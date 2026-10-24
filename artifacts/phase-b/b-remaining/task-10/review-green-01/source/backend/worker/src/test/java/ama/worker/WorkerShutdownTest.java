package ama.worker;

import ama.storage.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Lifecycle scheduling only; no synthetic queue is evidence of business authorization. */
class WorkerShutdownTest {
    @Test void failedAbortStillCancelsAllExecutionsAndClosesBackendOnce() throws Exception {
        failedAbortCleanup(false,true);
    }

    @Test void interruptedCloseStillCleansUpAndPreservesInterruption() throws Exception {
        failedAbortCleanup(true,true);
    }

    @Test void firstBackendFailureStillClosesSecondBackendOnce() throws Exception {failedAbortCleanup(false,false);}
    @Test void interruptedFirstBackendFailureStillClosesSecondBackendOnce() throws Exception {failedAbortCleanup(true,false);}

    private void failedAbortCleanup(boolean interrupt,boolean abortFailure) throws Exception {
        var queue = new WorkerLoopTest.LocalTasks(); queue.add("cpu"); queue.add("cpu");
        var ready = new CountDownLatch(2); var release = new CountDownLatch(1);
        var aborts = new AtomicInteger(); var preparations = new AtomicInteger();
        var writers = new AtomicInteger(); var backendCloses = new AtomicInteger();var secondBackendCloses=new AtomicInteger();
        var backendFailure=new IllegalStateException("FIRST_BACKEND_CLOSE_FAILED");
        var firstFailure = new IllegalStateException("OWNED_ABORT_FAILED");
        var loop = WorkerLoop.contextual(queue, Map.of("cpu", 2), Map.of("TEST", (lease, cancellation) -> {
            int index = preparations.incrementAndGet();
            try (var registration = cancellation.register(() -> {
                aborts.incrementAndGet();
                if (index == 1 && abortFailure) throw firstFailure;
            })) {
                ready.countDown();
                release.await();
                return () -> { writers.incrementAndGet(); return WorkerLoopTest.success(); };
            }
        }), "worker");
        var storage = (FileStoragePort) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {FileStoragePort.class, AutoCloseable.class}, (proxy, method, args) -> {
                    if (method.getName().equals("close")) { backendCloses.incrementAndGet(); throw backendFailure; }
                    throw new AssertionError("Unexpected backend operation " + method.getName());
                });
        var definition = new ManagedBackendId.Local("https://localhost:443", "owned-lifecycle", ".", "ext4");
        var secondStorage=(FileStoragePort)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{FileStoragePort.class,AutoCloseable.class},(proxy,method,args)->{
            if(method.getName().equals("close")){secondBackendCloses.incrementAndGet();return null;}
            throw new AssertionError("Unexpected second backend operation");
        });
        var secondDefinition=new ManagedBackendId.Local("https://localhost:443","owned-lifecycle-second",".","ext4");
        var definitions = new FileBackendDefinitions(List.of(new FileBackendCatalog.Backend(
                definition.id(), "local", 1, "READ_ONLY", storage, definition),new FileBackendCatalog.Backend(secondDefinition.id(),"local",1,"READ_ONLY",secondStorage,secondDefinition)), null);
        var constructor = TranscriptWorkerRuntimeProvider.Runtime.class
                .getDeclaredConstructor(WorkerLoop.class, ReconciliationLoop.class, ama.model.ControlledMinutesClient.class, FileBackendDefinitions.class);
        constructor.setAccessible(true);
        var runtime = constructor.newInstance(loop, null, null, definitions);
        var executors = executors(loop);
        try {
            loop.pollOnce("cpu"); assertTrue(ready.await(2, TimeUnit.SECONDS));
            if (interrupt) Thread.currentThread().interrupt();
            var thrown = assertThrows(IllegalStateException.class, runtime::close);
            boolean retainedInterrupt = Thread.interrupted();
            assertAll(
                    () -> assertSame(abortFailure?firstFailure:backendFailure, thrown, "Preserve the first owned cleanup failure"),
                    () -> assertEquals(2, aborts.get(), "A failed abort cannot skip another owned execution"),
                    () -> assertEquals(1, backendCloses.get(), "Provider must attempt the first backend once"),
                    () -> assertEquals(1,secondBackendCloses.get(),"Failed backend close cannot abandon later owned backend"),
                    () -> {if(abortFailure)assertTrue(Arrays.asList(thrown.getSuppressed()).contains(backendFailure));},
                    () -> assertTrue(executors.stream().allMatch(ExecutorService::isTerminated), "Every owned executor must terminate"),
                    () -> assertEquals(interrupt, retainedInterrupt),
                    () -> assertEquals(0, writers.get()),
                    () -> assertTrue(queue.outputs.isEmpty(), "No completion or quarantine after close"));
            runtime.close();
            assertEquals(1, backendCloses.get());assertEquals(1,secondBackendCloses.get()); assertEquals(2, aborts.get());
        } finally {
            // Rescue the known old implementation after RED so its leaked JVM threads cannot hang Surefire.
            Thread.interrupted(); release.countDown();
            for (var executor : executors) executor.shutdownNow();
            for (var executor : executors) assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    private static List<ExecutorService> executors(WorkerLoop loop) throws Exception {
        var result = new ArrayList<ExecutorService>();
        var heartbeats = WorkerLoop.class.getDeclaredField("heartbeats"); heartbeats.setAccessible(true);
        result.add((ExecutorService) heartbeats.get(loop));
        var pools = WorkerLoop.class.getDeclaredField("pools"); pools.setAccessible(true);
        for (var pool : ((Map<?, ?>) pools.get(loop)).values()) for (var name : List.of("scanner", "workers")) {
            var field = pool.getClass().getDeclaredField(name); field.setAccessible(true);
            result.add((ExecutorService) field.get(pool));
        }
        return result;
    }
}
