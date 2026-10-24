package ama.worker;

import ama.contract.TaskContracts.*;
import ama.task.TaskQueue;
import ama.task.PreparedCompletionEnvelope;
import ama.task.PreparationCancellation;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static ama.contract.OutboxContracts.protocolCode;

/** Separate module, explicitly started by a process owner. No API bean/autostart or built-in company handlers. */
public final class WorkerLoop implements WorkerRuntime {
    @FunctionalInterface public interface ExternalHandler { void handle(ExternalTaskExecution execution) throws Exception; }
    public static WorkerLoop external(TaskQueue queue,ama.task.ExternalOperationPort operations,ama.task.ModelPermitPool permits,
            Map<String,Integer> capacities,Map<String,ExternalHandler> handlers,Map<String,String> models,String workerId){
        throw new IllegalStateException("External Worker not installed");
    }
    @FunctionalInterface public interface Handler { TaskResult handle(TaskLease lease) throws Exception; }
    /** Bounded preparation outside completion; the returned callback performs only prelocked DB writes. */
    @FunctionalInterface public interface PreparedHandler { Supplier<TaskResult> prepare(TaskLease lease) throws Exception; }
    @FunctionalInterface public interface ContextualPreparedHandler {
        Supplier<TaskResult> prepare(TaskLease lease,PreparationCancellation cancellation) throws Exception;
    }
    public static WorkerLoop contextual(TaskQueue queue,Map<String,Integer> capacities,
            Map<String,ContextualPreparedHandler> handlers,String workerId) {
        return new WorkerLoop(queue,capacities,Map.of(),Map.of(),handlers,workerId);
    }
    private static final System.Logger LOG = System.getLogger(WorkerLoop.class.getName());
    private static final long INTERVAL_SECONDS = 5;
    private final TaskQueue queue;
    private final String workerId;
    private final Map<String,Handler> handlers;
    private final Map<String,PreparedHandler> preparedHandlers;
    private final Map<String,Pool> pools;
    private final Map<String,ContextualPreparedHandler> contextualHandlers;
    private final java.util.Set<Execution> executions=ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService heartbeats;
    private volatile long lastProgressNanos;
    public record Observation(String state,long progressAgeMillis,int active) {}
    public Observation observation(){long value=lastProgressNanos;return new Observation(closed.get()?"STOPPING":!started.get()?"STARTING":value==0?"STARTING":"RUNNING",value==0?Long.MAX_VALUE:TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-value),executions.size());}
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    private static final class Pool {
        final Semaphore slots;
        final ThreadPoolExecutor workers;
        final ScheduledExecutorService scanner;
        final AtomicBoolean scanning = new AtomicBoolean();

        Pool(String name, int capacity) {
            slots = new Semaphore(capacity);
            workers = new ThreadPoolExecutor(capacity, capacity, 0, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(capacity), threads("ama-work-" + name));
            scanner = Executors.newSingleThreadScheduledExecutor(threads("ama-poll-" + name));
        }
    }

    public WorkerLoop(TaskQueue queue, Map<String,Integer> capacities, Map<String,Handler> handlers, String workerId) {
        this(queue, capacities, handlers, Map.of(), Map.of(), workerId);
    }

    /** Explicit queue-owned atomic completion mode; the handler itself never completes the task. */
    public static WorkerLoop prepared(TaskQueue queue, Map<String,Integer> capacities,
                                      Map<String,PreparedHandler> handlers, String workerId) {
        return new WorkerLoop(queue, capacities, Map.of(), handlers, Map.of(), workerId);
    }

    private WorkerLoop(TaskQueue queue, Map<String,Integer> capacities, Map<String,Handler> handlers,
                       Map<String,PreparedHandler> preparedHandlers, Map<String,ContextualPreparedHandler> contextualHandlers, String workerId) {
        this.queue = Objects.requireNonNull(queue); this.handlers = Map.copyOf(handlers);
        this.preparedHandlers = Map.copyOf(preparedHandlers);
        this.contextualHandlers=Map.copyOf(contextualHandlers);
        protocolCode(workerId); this.workerId = workerId;
        if (capacities.isEmpty()) throw new IllegalArgumentException("Explicit pool capacities required");
        int total = 0;
        for (var entry : capacities.entrySet()) {
            protocolCode(entry.getKey());
            if (entry.getValue() == null || entry.getValue() < 1) throw new IllegalArgumentException("Positive pool capacity required");
            total = Math.addExact(total, entry.getValue());
        }
        var configured = new LinkedHashMap<String,Pool>();
        capacities.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                configured.put(entry.getKey(), new Pool(entry.getKey(), entry.getValue())));
        pools = Map.copyOf(configured);
        heartbeats = Executors.newScheduledThreadPool(total, threads("ama-heartbeat"));
    }

    public void start() {
        if (closed.get()) throw new IllegalStateException("Worker is closed");
        if (!started.compareAndSet(false, true)) return;
        pools.forEach((name,pool) -> pool.scanner.scheduleWithFixedDelay(() -> pollOnce(name), 0, INTERVAL_SECONDS, TimeUnit.SECONDS));
    }

    /** Bounded claim batch. A blocked pool scanner/handler cannot consume another pool's capacity or heartbeat. */
    public void pollOnce(String name) {
        var pool = pools.get(name);
        if (pool == null) throw new IllegalArgumentException("Unconfigured worker pool");
        if (closed.get() || !pool.scanning.compareAndSet(false, true)) return;
        try {
            while (!closed.get() && pool.slots.tryAcquire()) {
                boolean submitted = false;
                try {
                    var claimed = queue.claim(name, workerId);lastProgressNanos=System.nanoTime();
                    if (claimed.isEmpty()) return;
                    var lease = claimed.orElseThrow();
                    pool.workers.execute(() -> execute(name, pool, lease));
                    submitted = true;
                } finally { if (!submitted) pool.slots.release(); }
            }
        } catch (RuntimeException failure) {
            // Fixed five-second poll bounds retries; no DB/network error text containing credentials is logged.
            LOG.log(System.Logger.Level.WARNING, "Worker poll unavailable: " + failure.getClass().getSimpleName());
        } finally { pool.scanning.set(false); }
    }

    private enum Phase { ORDINARY,DRAINING_RENEWAL,DISPATCHED,WRITER_ADMITTED,FINISHED }
    private final class Execution implements PreparationCancellation {
        final TaskLease lease;
        Phase phase=Phase.ORDINARY;
        boolean inFlight,lost,cancelled,admitted;
        long generation;
        Runnable abort;
        Execution(TaskLease lease){this.lease=lease;}
        public synchronized boolean cancelled(){return cancelled||lost||closed.get()||Thread.currentThread().isInterrupted();}
        public AutoCloseable register(Runnable action){
            Objects.requireNonNull(action);boolean reject;
            synchronized(this){reject=cancelled();if(!reject){if(abort!=null)throw new IllegalStateException("Source already registered");abort=action;}}
            if(reject){action.run();throw new IllegalStateException("Preparation cancelled");}
            var released=new AtomicBoolean();
            return ()->{if(released.compareAndSet(false,true))synchronized(this){if(abort==action)abort=null;}};
        }
        void cancel(){Runnable action;synchronized(this){cancelled=true;action=abort;abort=null;notifyAll();}if(action!=null)action.run();}
        void renew(){
            long tick;
            synchronized(this){if(phase!=Phase.ORDINARY||cancelled()||inFlight)return;inFlight=true;tick=++generation;}
            boolean ok=false;try{ok=queue.renew(lease);if(ok)lastProgressNanos=System.nanoTime();}catch(RuntimeException unavailable){/* sticky loss */}
            Runnable action=null;
            synchronized(this){
                if(tick!=generation||!inFlight)return;
                inFlight=false;
                if(!ok&&(phase==Phase.ORDINARY||phase==Phase.DRAINING_RENEWAL)){lost=true;action=abort;abort=null;}
                notifyAll();
            }
            if(action!=null)action.run();
        }
        synchronized boolean dispatch() throws InterruptedException {
            if(phase!=Phase.ORDINARY||cancelled())return false;
            phase=Phase.DRAINING_RENEWAL;
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(inFlight&&!cancelled()){
                long left=end-System.nanoTime();if(left<=0){lost=true;return false;}
                TimeUnit.NANOSECONDS.timedWait(this,left);
            }
            if(cancelled())return false;
            phase=Phase.DISPATCHED;return true;
        }
        synchronized void admit(){
            if(phase!=Phase.DISPATCHED||cancelled()||admitted)throw new IllegalStateException("Worker completion admission denied");
            admitted=true;phase=Phase.WRITER_ADMITTED;
        }
        synchronized boolean dispatched(){return phase==Phase.DISPATCHED||phase==Phase.WRITER_ADMITTED||phase==Phase.FINISHED;}
        synchronized void finish(){phase=Phase.FINISHED;notifyAll();}
    }
    private static final class Envelope implements PreparedCompletionEnvelope {
        private final Supplier<TaskResult> payload;private final Runnable admission;
        Envelope(Supplier<TaskResult> payload,Runnable admission){this.payload=Objects.requireNonNull(payload);this.admission=admission;}
        public Supplier<TaskResult> payload(){return payload;}
        public void admit(){admission.run();}
        public TaskResult get(){throw new IllegalStateException("QUEUE_PATH_REQUIRED");}
    }
    private void execute(String name, Pool pool, TaskLease lease) {
        var execution=new Execution(lease);executions.add(execution);
        ScheduledFuture<?> heartbeat=null;
        try {
            if(closed.get()){execution.cancel();return;}
            heartbeat=heartbeats.scheduleWithFixedDelay(execution::renew,INTERVAL_SECONDS,INTERVAL_SECONDS,TimeUnit.SECONDS);
            var contextual=contextualHandlers.get(lease.spec().type());
            var prepared=preparedHandlers.get(lease.spec().type());
            Supplier<TaskResult> effects=null;TaskResult result=null;
            if(contextual!=null)effects=Objects.requireNonNull(contextual.prepare(lease,execution),"Prepared carrier");
            else if(prepared!=null)effects=Objects.requireNonNull(prepared.prepare(lease),"Prepared completion callback");
            else {
                var handler=handlers.get(lease.spec().type());
                result=handler==null?new TaskResult(Outcome.FAILED,null,"UNREGISTERED_HANDLER"):Objects.requireNonNull(handler.handle(lease),"Handler result");
            }
            if(execution.dispatch()) {
                if(effects!=null) {
                    var payload=effects;
                    queue.completeWithEffects(lease,contextual!=null?new Envelope(payload,execution::admit):()->{execution.admit();return payload.get();});
                } else queue.complete(lease,result);
            }
        } catch(Exception failure) {
            // Selecting dispatch makes commit acknowledgement potentially unknown: never complete twice.
            if(!execution.dispatched()&&!execution.cancelled()&&!contextualHandlers.containsKey(lease.spec().type())) {
                try {if(execution.dispatch())queue.complete(lease,new TaskResult(Outcome.RECONCILING,null,"UNKNOWN_HANDLER_FAILURE"));}
                catch(Exception unavailable){LOG.log(System.Logger.Level.WARNING,"Worker quarantine unavailable: "+unavailable.getClass().getSimpleName());}
            } else LOG.log(System.Logger.Level.WARNING,"Worker completion unavailable: "+failure.getClass().getSimpleName());
        } finally {
            execution.finish();if(heartbeat!=null)heartbeat.cancel(false);executions.remove(execution);pool.slots.release();
            if(!closed.get())try{pool.scanner.execute(()->pollOnce(name));}catch(RejectedExecutionException shuttingDown){/* close won */}
        }
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        Throwable failure=null;
        var owned=new java.util.ArrayList<ExecutorService>();
        for(var pool:pools.values()) {
            owned.add(pool.scanner);
            try{pool.scanner.shutdownNow();}catch(RuntimeException|Error error){failure=cleanupFailure(failure,error);}
        }
        // cancel detaches each callback under its monitor, then invokes it outside that monitor.
        for(var execution:executions)try{execution.cancel();}catch(RuntimeException|Error error){failure=cleanupFailure(failure,error);}
        owned.add(heartbeats);
        for(var pool:pools.values())owned.add(pool.workers);
        for(var executor:owned)try{executor.shutdownNow();}catch(RuntimeException|Error error){failure=cleanupFailure(failure,error);}
        boolean interrupted=Thread.interrupted();
        try {
            for(var executor:owned) {
                boolean done=false;
                while(!done)try {
                    done=executor.awaitTermination(Math.max(0,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);
                    if(!done){failure=cleanupFailure(failure,new IllegalStateException("Worker shutdown deadline exceeded"));break;}
                }catch(InterruptedException error){interrupted=true;}
            }
        } finally {if(interrupted)Thread.currentThread().interrupt();}
        if(failure instanceof RuntimeException error)throw error;
        if(failure instanceof Error error)throw error;
    }

    private static Throwable cleanupFailure(Throwable first,Throwable next) {
        if(first==null)return next;
        if(first!=next&&first.getSuppressed().length<8)first.addSuppressed(next);
        return first;
    }

    private static ThreadFactory threads(String prefix) {
        var sequence = new AtomicInteger();
        return task -> new Thread(task, prefix + "-" + sequence.incrementAndGet());
    }
}
