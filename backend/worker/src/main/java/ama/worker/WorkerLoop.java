package ama.worker;

import ama.contract.TaskContracts.*;
import ama.task.TaskQueue;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static ama.contract.OutboxContracts.protocolCode;

/** Separate module, explicitly started by a process owner. No API bean/autostart or built-in company handlers. */
public final class WorkerLoop implements WorkerRuntime {
    @FunctionalInterface public interface Handler { TaskResult handle(TaskLease lease) throws Exception; }
    private static final System.Logger LOG = System.getLogger(WorkerLoop.class.getName());
    private static final long INTERVAL_SECONDS = 5;
    private final TaskQueue queue;
    private final String workerId;
    private final Map<String,Handler> handlers;
    private final Map<String,Pool> pools;
    private final ScheduledExecutorService heartbeats;
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
        this.queue = Objects.requireNonNull(queue); this.handlers = Map.copyOf(handlers);
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
                    var claimed = queue.claim(name, workerId);
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

    private void execute(String name, Pool pool, TaskLease lease) {
        var lost = new AtomicBoolean();
        ScheduledFuture<?> heartbeat = null;
        try {
            heartbeat = heartbeats.scheduleWithFixedDelay(() -> renew(lease, lost), INTERVAL_SECONDS, INTERVAL_SECONDS, TimeUnit.SECONDS);
            var handler = handlers.get(lease.spec().type());
            TaskResult result = handler == null
                    ? new TaskResult(Outcome.FAILED, null, "UNREGISTERED_HANDLER") : handler.handle(lease);
            if (!lost.get()) queue.complete(lease, result);
        } catch (Exception failure) {
            quarantine(lease, lost);
        } finally {
            if (heartbeat != null) heartbeat.cancel(false);
            pool.slots.release();
            if (!closed.get()) {
                try { pool.scanner.execute(() -> pollOnce(name)); }
                catch (RejectedExecutionException shuttingDown) { /* close won the race; no new claims */ }
            }
        }
    }

    private void renew(TaskLease lease, AtomicBoolean lost) {
        if (lost.get() || closed.get()) return;
        try { if (!queue.renew(lease)) lost.set(true); }
        catch (RuntimeException unavailable) { lost.set(true); }
    }

    private void quarantine(TaskLease lease, AtomicBoolean lost) {
        if (lost.get()) return;
        try { queue.complete(lease, new TaskResult(Outcome.RECONCILING, null, "UNKNOWN_HANDLER_FAILURE")); }
        catch (RuntimeException unavailable) {
            LOG.log(System.Logger.Level.WARNING, "Worker quarantine unavailable: " + unavailable.getClass().getSimpleName());
        }
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        pools.values().forEach(pool -> pool.scanner.shutdownNow());
        heartbeats.shutdownNow();
        pools.values().forEach(pool -> pool.workers.shutdownNow());
        for (var pool : pools.values()) {
            try { pool.workers.awaitTermination(2, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
        }
    }

    private static ThreadFactory threads(String prefix) {
        var sequence = new AtomicInteger();
        return task -> new Thread(task, prefix + "-" + sequence.incrementAndGet());
    }
}
