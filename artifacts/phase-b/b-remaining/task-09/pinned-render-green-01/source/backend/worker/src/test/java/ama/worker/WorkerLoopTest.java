package ama.worker;

import ama.contract.TaskContracts.*;
import ama.task.TaskQueue;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Scheduling unit tests only. The test-only queue does not qualify any DB lease/recovery/NFR claim. */
class WorkerLoopTest {
    @Test void boundedPoolStartsSecondHandlerOnlyAfterFirstReturns() throws Exception {
        var queue = new LocalTasks();
        queue.add("cpu"); queue.add("cpu");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var secondEntered = new CountDownLatch(1);
        var executions = new AtomicInteger();
        WorkerLoop.Handler handler = lease -> {
            if (executions.incrementAndGet() == 1) {
                entered.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test release timed out");
            } else secondEntered.countDown();
            return success();
        };
        try (var worker = new WorkerLoop(queue, Map.of("cpu", 1), Map.of("TEST", handler), "worker")) {
            try {
                worker.pollOnce("cpu");
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                worker.pollOnce("cpu");
                assertFalse(secondEntered.await(150, TimeUnit.MILLISECONDS));
            } finally { release.countDown(); }
            assertTrue(queue.completed.tryAcquire(2, TimeUnit.SECONDS));
            worker.pollOnce("cpu");
            assertTrue(secondEntered.await(2, TimeUnit.SECONDS));
            assertTrue(queue.completed.tryAcquire(2, TimeUnit.SECONDS));
        }
        assertEquals(2, executions.get());
        assertEquals(2, queue.outputs.size());
    }

    @Test void blockedCpuHandlerDoesNotConsumeIoCapacity() throws Exception {
        var queue = new LocalTasks();
        queue.add("cpu"); queue.add("io");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        WorkerLoop.Handler handler = lease -> {
            if (lease.spec().pool().equals("cpu")) {
                entered.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test release timed out");
            }
            return success();
        };
        try (var worker = new WorkerLoop(queue, Map.of("cpu", 1, "io", 1), Map.of("TEST", handler), "worker")) {
            try {
                worker.pollOnce("cpu"); assertTrue(entered.await(2, TimeUnit.SECONDS));
                worker.pollOnce("io");
                assertTrue(queue.completed.tryAcquire(2, TimeUnit.SECONDS));
                assertEquals(1, queue.outputs.size());
                assertEquals("io", queue.outputs.values().iterator().next());
            } finally { release.countDown(); }
        }
    }

    @Test void independentHeartbeatRunsDuringBlockedHandler() throws Exception {
        var queue = new LocalTasks(); queue.add("cpu");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        WorkerLoop.Handler handler = lease -> {
            entered.countDown();
            if (!release.await(9, TimeUnit.SECONDS)) throw new IllegalStateException("Test release timed out");
            return success();
        };
        try (var worker = new WorkerLoop(queue, Map.of("cpu", 1), Map.of("TEST", handler), "worker")) {
            try {
                worker.start();
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                assertTrue(queue.renewed.await(7, TimeUnit.SECONDS), "The real five-second scheduler must run independently of the blocked handler");
                assertTrue(queue.outputs.isEmpty());
            } finally { release.countDown(); }
            assertTrue(queue.completed.tryAcquire(2, TimeUnit.SECONDS));
        }
    }

    static TaskResult success() { return new TaskResult(Outcome.SUCCEEDED, new TypedRef("TEST_OUTPUT", UUID.randomUUID().toString()), null); }

    static final class LocalTasks implements TaskQueue {
        final ConcurrentLinkedQueue<TaskSpec> ready = new ConcurrentLinkedQueue<>();
        final ConcurrentMap<String,String> outputs = new ConcurrentHashMap<>();
        final Semaphore completed = new Semaphore(0);
        final CountDownLatch renewed = new CountDownLatch(1);
        void add(String pool) {
            ready.add(new TaskSpec("TEST", pool, new SubjectRef("TEST_ROOT", UUID.randomUUID().toString()),
                    "1".repeat(64), new TypedRef("TEST_INPUT", UUID.randomUUID().toString()), "2".repeat(64), 0, 1, 0, 5));
        }
        public String enqueue(TaskSpec task) { ready.add(task); return UUID.randomUUID().toString(); }
        public Optional<TaskLease> claim(String pool, String owner) {
            for (var spec : ready) if (spec.pool().equals(pool) && ready.remove(spec)) {
                return Optional.of(new TaskLease(UUID.randomUUID().toString(), 1, owner, Instant.now().plusSeconds(20), spec));
            }
            return Optional.empty();
        }
        public boolean renew(TaskLease lease) { renewed.countDown(); return true; }
        public boolean complete(TaskLease lease, TaskResult result) {
            outputs.put(lease.taskId(), lease.spec().pool()); completed.release(); return true;
        }
        public boolean completeWithEffects(TaskLease lease, TaskResult result, Runnable effects) {
            effects.run(); return complete(lease, result);
        }
    }
}
