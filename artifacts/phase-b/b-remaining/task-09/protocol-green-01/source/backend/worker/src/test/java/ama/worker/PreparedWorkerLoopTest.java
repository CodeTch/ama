package ama.worker;

import ama.contract.TaskContracts.*;
import ama.task.TaskQueue;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Scheduler behavior only; real transaction/lease proof lives in PreparedCompletionIT. */
class PreparedWorkerLoopTest {
    WorkerLoop prepared(Queue queue, WorkerLoop.PreparedHandler handler) {
        return WorkerLoop.prepared(queue, Map.of("cpu", 1), Map.of("TEST", handler), "worker");
    }
    @Test void preparationFinishesBeforeSingleQueueOwnedCallback() throws Exception {
        var queue = new Queue(); var prepared = new AtomicBoolean(); var callbackUnderQueue = new AtomicBoolean();
        var callbacks = new AtomicInteger();
        try (var worker = prepared(queue, lease -> {
            assertFalse(queue.insideCompletion.get()); assertEquals(0, callbacks.get());
            prepared.set(true);
            return () -> { callbacks.incrementAndGet(); callbackUnderQueue.set(queue.insideCompletion.get()); return WorkerLoopTest.success(); };
        })) {
            worker.pollOnce("cpu"); assertTrue(queue.done.await(2, TimeUnit.SECONDS));
        }
        assertTrue(prepared.get()); assertTrue(callbackUnderQueue.get()); assertEquals(1, callbacks.get());
        assertEquals(1, queue.preparedCalls.get()); assertEquals(0, queue.ordinaryCalls.get());
    }
    @Test void preparedCompletionFailureNeverStartsSecondCompletion() throws Exception {
        var queue = new Queue(); queue.failCompletion = true;
        try (var worker = prepared(queue, lease -> WorkerLoopTest::success)) {
            worker.pollOnce("cpu"); assertTrue(queue.done.await(2, TimeUnit.SECONDS));
        }
        assertEquals(1, queue.preparedCalls.get()); assertEquals(0, queue.ordinaryCalls.get());
    }
    @Test void ordinaryCompletionFailureNeverStartsSecondCompletion() throws Exception {
        var queue = new Queue(); queue.failCompletion = true;
        try (var worker = new WorkerLoop(queue, Map.of("cpu", 1), Map.of("TEST", lease -> WorkerLoopTest.success()), "worker")) {
            worker.pollOnce("cpu"); assertTrue(queue.done.await(2, TimeUnit.SECONDS));
        }
        assertEquals(1, queue.ordinaryCalls.get()); assertEquals(0, queue.preparedCalls.get());
    }
    @Test void preparationFailureHasOneQuarantineCompletion() throws Exception {
        var queue = new Queue();
        try (var worker = prepared(queue, lease -> { throw new IllegalStateException("PREPARE_FAILED"); })) {
            worker.pollOnce("cpu"); assertTrue(queue.done.await(2, TimeUnit.SECONDS));
        }
        assertEquals(1, queue.ordinaryCalls.get()); assertEquals(0, queue.preparedCalls.get());
        assertEquals(Outcome.RECONCILING, queue.result.outcome());
    }
    @Test void lostHeartbeatSuppressesPreparedCallback() throws Exception {
        var queue = new Queue(); queue.renewAllowed = false;
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var callbacks = new AtomicInteger();
        try (var worker = prepared(queue, lease -> {
            entered.countDown();
            if (!release.await(9, TimeUnit.SECONDS)) throw new IllegalStateException("PREPARATION_TIMEOUT");
            return () -> { callbacks.incrementAndGet(); return WorkerLoopTest.success(); };
        })) {
            try {
                worker.pollOnce("cpu"); assertTrue(entered.await(2, TimeUnit.SECONDS));
                assertTrue(queue.renewed.await(7, TimeUnit.SECONDS));
                // Allow the independently scheduled failed renewal to return to WorkerLoop.
                Thread.sleep(100);
            } finally { release.countDown(); }
        }
        assertEquals(0, callbacks.get()); assertEquals(0, queue.preparedCalls.get()); assertEquals(0, queue.ordinaryCalls.get());
    }
    @Test void closeWhileCompletionWaitsSuppressesCallback() throws Exception {
        var queue=new Queue();queue.pauseCompletion=true;var callbacks=new AtomicInteger();
        try(var worker=prepared(queue,lease->()->{callbacks.incrementAndGet();return WorkerLoopTest.success();})) {
            worker.pollOnce("cpu");assertTrue(queue.atCompletion.await(2,TimeUnit.SECONDS));
            worker.close();queue.releaseCompletion.countDown();assertTrue(queue.done.await(2,TimeUnit.SECONDS));
        }
        assertEquals(0,callbacks.get());assertEquals(1,queue.preparedCalls.get());assertEquals(0,queue.ordinaryCalls.get());
        assertEquals(1,queue.renewed.getCount(),"No tick after dispatch");
    }
    @Test void nullPreparedCallbackHasOneQuarantineCompletion() throws Exception {
        var queue = new Queue();
        try (var worker = prepared(queue, lease -> null)) {
            worker.pollOnce("cpu"); assertTrue(queue.done.await(2, TimeUnit.SECONDS));
        }
        assertEquals(1, queue.ordinaryCalls.get()); assertEquals(0, queue.preparedCalls.get());
        assertEquals(Outcome.RECONCILING, queue.result.outcome());
    }
    @Test void otherQueueImplementationsRejectAtomicEffectsWithoutRunningThem() {
        TaskQueue unsupported = new WorkerLoopTest.LocalTasks(); var callbacks = new AtomicInteger();
        assertThrows(UnsupportedOperationException.class, () -> unsupported.completeWithEffects(null, () -> { callbacks.incrementAndGet(); return WorkerLoopTest.success(); }));
        assertEquals(0, callbacks.get());
    }
    @Test void selectedRenewalDrainsBeforeCompletionEntry() throws Exception {
        var queue=new Queue();queue.pauseRenew=true;
        var prepared=new CountDownLatch(1);
        try(var worker=prepared(queue,lease->{
            assertTrue(queue.renewEntered.await(7,TimeUnit.SECONDS));
            prepared.countDown();return WorkerLoopTest::success;
        })) {
            worker.pollOnce("cpu");assertTrue(prepared.await(8,TimeUnit.SECONDS));
            try {assertFalse(queue.atCompletion.await(300,TimeUnit.MILLISECONDS),"Completion must not acquire DB roots until selected renew exits");}
            finally {queue.releaseRenew.countDown();}
            assertTrue(queue.done.await(2,TimeUnit.SECONDS));
        }
        assertEquals(1,queue.preparedCalls.get());assertEquals(0,queue.ordinaryCalls.get());
    }
    static final class Queue implements TaskQueue {
        final WorkerLoopTest.LocalTasks tasks = new WorkerLoopTest.LocalTasks();
        final AtomicInteger ordinaryCalls = new AtomicInteger(), preparedCalls = new AtomicInteger();
        final ThreadLocal<Boolean> insideCompletion = ThreadLocal.withInitial(() -> false);
        final CountDownLatch done = new CountDownLatch(1), renewed = new CountDownLatch(1);
        final CountDownLatch atCompletion = new CountDownLatch(1), releaseCompletion = new CountDownLatch(1);
        volatile boolean pauseCompletion, failCompletion, renewAllowed = true, pauseRenew;
        final CountDownLatch renewEntered=new CountDownLatch(1),releaseRenew=new CountDownLatch(1);
        volatile TaskResult result;
        Queue() { tasks.add("cpu"); }
        public String enqueue(TaskSpec spec) { return tasks.enqueue(spec); }
        public Optional<TaskLease> claim(String pool, String owner) { return tasks.claim(pool, owner); }
        public boolean renew(TaskLease lease) {
            renewEntered.countDown();
            if(pauseRenew)try {if(!releaseRenew.await(8,TimeUnit.SECONDS))throw new IllegalStateException("RENEW_GATE_TIMEOUT");}
            catch(InterruptedException e){Thread.currentThread().interrupt();return false;}
            renewed.countDown(); return renewAllowed;
        }
        public boolean complete(TaskLease lease, TaskResult value) {
            ordinaryCalls.incrementAndGet(); result = value; done.countDown();
            if (failCompletion) throw new IllegalStateException("COMPLETION_FAILED"); return true;
        }
        public boolean completeWithEffects(TaskLease lease, TaskResult value, Runnable effects) { throw new AssertionError("Wrong completion overload"); }
        public boolean completeWithEffects(TaskLease lease, Supplier<TaskResult> effects) {
            preparedCalls.incrementAndGet(); insideCompletion.set(true);
            try {
                atCompletion.countDown();
                if (pauseCompletion) {
                    try { if (!releaseCompletion.await(9, TimeUnit.SECONDS)) throw new IllegalStateException("COMPLETION_GATE_TIMEOUT"); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
                }
                result = effects.get(); if (failCompletion) throw new IllegalStateException("COMPLETION_FAILED"); return true; }
            finally { insideCompletion.remove(); done.countDown(); }
        }
    }
}
