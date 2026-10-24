package ama.worker;

import ama.task.ExternalOperationPort;
import ama.contract.ExternalContracts.OperationToken;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static ama.contract.OutboxContracts.protocolCode;

/** Independent bounded reconciliation of known operations. No send/admission authority is minted here. */
public final class ReconciliationLoop implements WorkerRuntime {
    @FunctionalInterface public interface PreparedHandler { void reconcile(ExternalTaskExecution session) throws Exception; }
    public static ReconciliationLoop prepared(ExternalOperationPort operations,ama.task.ModelPermitPool permits,int capacity,PreparedHandler handler,String owner){
        return new ReconciliationLoop(operations,capacity,null,Objects.requireNonNull(handler),Objects.requireNonNull(permits),owner,null);
    }
    public static ReconciliationLoop preparedWithPublicationMaintenance(ExternalOperationPort operations,ama.task.ModelPermitPool permits,int capacity,PreparedHandler handler,String owner,ama.transcript.JdbcPublicationLocalRecovery local){
        return new ReconciliationLoop(operations,capacity,null,Objects.requireNonNull(handler),Objects.requireNonNull(permits),owner,Objects.requireNonNull(local));
    }
    @FunctionalInterface public interface Handler { void reconcile(OperationToken token) throws Exception; }
    private static final System.Logger LOG = System.getLogger(ReconciliationLoop.class.getName());
    private final ExternalOperationPort operations;
    private final ama.transcript.JdbcPublicationLocalRecovery localPublications;
    private final Handler handler;private final PreparedHandler preparedHandler;private final ama.task.ModelPermitPool permits;
    private final java.util.Set<ExternalExecutionSession> sessions=ConcurrentHashMap.newKeySet();
    private final java.util.Set<ExternalExecutionSession> pending=ConcurrentHashMap.newKeySet();
    private final String owner;
    private final Semaphore slots;
    private final ExecutorService workers;
    private final ScheduledExecutorService scanner;
    private final ExternalOperationHeartbeat heartbeats;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean scanning = new AtomicBoolean();

    public ReconciliationLoop(ExternalOperationPort operations, int capacity, Handler handler, String owner) {
        this(operations,capacity,Objects.requireNonNull(handler),null,null,owner,null);
    }
    private ReconciliationLoop(ExternalOperationPort operations,int capacity,Handler handler,PreparedHandler preparedHandler,ama.task.ModelPermitPool permits,String owner,ama.transcript.JdbcPublicationLocalRecovery localPublications){
        if (capacity < 1) throw new IllegalArgumentException("Explicit positive reconciliation capacity required");
        this.operations = Objects.requireNonNull(operations);this.localPublications=localPublications;
        this.handler = handler;this.preparedHandler=preparedHandler;this.permits=permits;
        protocolCode(owner);
        this.owner = owner;
        slots = new Semaphore(capacity);
        workers = new ThreadPoolExecutor(capacity, capacity, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(capacity), task -> new Thread(task, "ama-reconciliation"));
        scanner = Executors.newSingleThreadScheduledExecutor(task -> new Thread(task, "ama-reconciliation-poll"));
        heartbeats = new ExternalOperationHeartbeat(operations, capacity);
    }

    @Override public void start() {
        if (closed.get()) throw new IllegalStateException("Reconciler is closed");
        if (started.compareAndSet(false, true)) scanner.scheduleWithFixedDelay(this::poll, 0, 5, TimeUnit.SECONDS);
    }

    private void poll() {
        if (closed.get() || !scanning.compareAndSet(false, true)) return;
        try {
            // Maintenance precedes capacity/claim checks, including an entirely saturated worker.
            if(permits!=null)permits.recoverSettlements(8);
            if(localPublications!=null)try{localPublications.maintainLocalFailures();}catch(RuntimeException unavailable){LOG.log(System.Logger.Level.WARNING,"Publication local maintenance unavailable: "+unavailable.getClass().getSimpleName());}
            for(var session:pending){session.retry();if(!session.pending()&&pending.remove(session)){session.close();sessions.remove(session);slots.release();}}
            while (!closed.get() && slots.tryAcquire()) {
                boolean submitted = false;
                try {
                    var claimed = operations.claimReconciliation(owner);
                    if (claimed.isEmpty()) return;
                    workers.execute(() -> reconcile(claimed.orElseThrow()));
                    submitted = true;
                } finally { if (!submitted) slots.release(); }
            }
        } catch (RuntimeException unavailable) {
            LOG.log(System.Logger.Level.WARNING, "Reconciliation poll unavailable: " + unavailable.getClass().getSimpleName());
        } finally { scanning.set(false); }
    }

    private void reconcile(OperationToken token) {
        if(preparedHandler!=null){reconcilePrepared(token);return;}
        try (var tracked = heartbeats.track(token)) {
            // claim committed before the handler. The handler queries authoritative status outside DB locks.
            // Any eventual observation revalidates the independent epoch in its own short transaction.
            handler.reconcile(token);
        } catch (Exception unavailable) {
            // Leave MARKED/UNKNOWN intact for later reconciliation; never enqueue a resend on this path.
            LOG.log(System.Logger.Level.WARNING, "Reconciliation handler unavailable: " + unavailable.getClass().getSimpleName());
        } finally {
            slots.release();
            if (!closed.get()) {
                try { scanner.execute(this::poll); }
                catch (RejectedExecutionException shuttingDown) { /* no new claims after close */ }
            }
        }
    }

    private void reconcilePrepared(OperationToken token){
        ExternalExecutionSession session=null;boolean retained=false;
        try{session=ExternalExecutionSession.reconciliation(token,operations,permits,heartbeats,()->!closed.get());sessions.add(session);preparedHandler.reconcile(session);}
        catch(Exception unavailable){LOG.log(System.Logger.Level.WARNING,"Prepared reconciliation unavailable: "+unavailable.getClass().getSimpleName());}
        finally{
            if(session!=null&&session.pending()&&!closed.get()){pending.add(session);retained=true;}
            else if(session!=null){session.close();sessions.remove(session);}
            if(!retained)slots.release();
            if(!closed.get())try{scanner.execute(this::poll);}catch(RejectedExecutionException shutdown){}
        }
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        scanner.shutdownNow();
        sessions.forEach(ExternalExecutionSession::close);
        heartbeats.close();
        workers.shutdownNow();
        try { workers.awaitTermination(2, TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
}
