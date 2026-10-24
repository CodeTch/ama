package ama.worker;

import ama.task.ExternalOperationPort;
import ama.contract.ExternalContracts.OperationToken;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static ama.contract.OutboxContracts.protocolCode;

/** Independent bounded reconciliation of known operations. No send/admission authority is minted here. */
public final class ReconciliationLoop implements WorkerRuntime {
    @FunctionalInterface public interface Handler { void reconcile(OperationToken token) throws Exception; }
    private static final System.Logger LOG = System.getLogger(ReconciliationLoop.class.getName());
    private final ExternalOperationPort operations;
    private final Handler handler;
    private final String owner;
    private final Semaphore slots;
    private final ExecutorService workers;
    private final ScheduledExecutorService scanner;
    private final ExternalOperationHeartbeat heartbeats;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean scanning = new AtomicBoolean();

    public ReconciliationLoop(ExternalOperationPort operations, int capacity, Handler handler, String owner) {
        if (capacity < 1) throw new IllegalArgumentException("Explicit positive reconciliation capacity required");
        this.operations = Objects.requireNonNull(operations);
        this.handler = Objects.requireNonNull(handler);
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

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        scanner.shutdownNow();
        heartbeats.close();
        workers.shutdownNow();
        try { workers.awaitTermination(2, TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    }
}
