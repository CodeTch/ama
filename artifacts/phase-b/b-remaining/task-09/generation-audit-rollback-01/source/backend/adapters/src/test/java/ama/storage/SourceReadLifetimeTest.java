package ama.storage;

import ama.task.PreparationCancellation;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Transport lifetime only; cancellation never manufactures a source/completion grant. */
class SourceReadLifetimeTest {
    @Test void completedTransportDetachesAndClosingScopeDoesNotCancelOriginalCarrierSignal() throws Exception {
        var signal=new Signal();var closes=new AtomicInteger();
        try(var scope=new SourceReadLifetime(signal,System.nanoTime()+TimeUnit.SECONDS.toNanos(3))){
            var request=SourceReadLifetime.track(closes::incrementAndGet);request.close();request.close();
        }
        assertEquals(0,closes.get());assertFalse(signal.cancelled());assertNull(signal.action.get());
    }
    @Test void alreadyLostScopeImmediatelyAbortsRacingNewRequestExactlyOnce() throws Exception {
        var signal=new Signal();var closes=new AtomicInteger();
        try(var scope=new SourceReadLifetime(signal,System.nanoTime()+TimeUnit.SECONDS.toNanos(3))){
            signal.cancel();
            assertThrows(IllegalStateException.class,()->SourceReadLifetime.track(closes::incrementAndGet));
        }
        assertEquals(1,closes.get());
    }
    @Test void deadlineAbortsRealRegisteredWorkWithoutCancellingTheOriginalSignal() throws Exception {
        var signal=new Signal();var aborted=new CountDownLatch(1);var closes=new AtomicInteger();
        try(var scope=new SourceReadLifetime(signal,System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(100))){
            var request=SourceReadLifetime.track(()->{closes.incrementAndGet();aborted.countDown();});
            assertTrue(aborted.await(2,TimeUnit.SECONDS));request.close();
        }
        assertEquals(1,closes.get());assertFalse(signal.cancelled());
    }
    @Test void failedAbortStillReleasesOtherOwnedRequestsAndRepeatedCloseIsSafe() throws Exception {
        var signal=new Signal();var closes=new AtomicInteger();var failure=new IllegalStateException("OWNED_CLOSE_FAILED");
        var scope=new SourceReadLifetime(signal,System.nanoTime()+TimeUnit.SECONDS.toNanos(3));
        SourceReadLifetime.track(()->{closes.incrementAndGet();throw failure;});SourceReadLifetime.track(closes::incrementAndGet);
        assertSame(failure,assertThrows(IllegalStateException.class,scope::close));scope.close();
        assertEquals(2,closes.get());assertNull(signal.action.get());
        // Failure cannot strand thread-scoped admission or prevent the next independent preparation.
        try(var next=new SourceReadLifetime(signal,System.nanoTime()+TimeUnit.SECONDS.toNanos(3))){assertFalse(signal.cancelled());}
    }
    @Test void saturatedAdmissionDeadlineCannotReleaseAnUnownedPermit() throws Exception {
        var permits=new Semaphore(0);var pool=Executors.newSingleThreadExecutor();
        try {
            var waiting=pool.submit(()->{
                try(var scope=new SourceReadLifetime(new Signal(),System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(100))){return SourceReadLifetime.tryAcquire(permits,30,TimeUnit.SECONDS);}
            });
            assertFalse(waiting.get(2,TimeUnit.SECONDS));assertEquals(0,permits.availablePermits());
        }finally{pool.shutdownNow();assertTrue(pool.awaitTermination(2,TimeUnit.SECONDS));}
    }
    @Test void knownLossWakesActualAdmissionWithoutInterruptingReusableThread() throws Exception {
        var permits=new Semaphore(0);var signal=new Signal();var pool=Executors.newSingleThreadExecutor();var thread=new AtomicReference<Thread>();
        try {
            var waiting=pool.submit(()->{
                thread.set(Thread.currentThread());
                try(var scope=new SourceReadLifetime(signal,System.nanoTime()+TimeUnit.SECONDS.toNanos(30))){
                    boolean acquired=SourceReadLifetime.tryAcquire(permits,30,TimeUnit.SECONDS);
                    assertFalse(Thread.currentThread().isInterrupted());return acquired;
                }
            });
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);boolean blocked=false;
            while(System.nanoTime()<deadline){var current=thread.get();if(current!=null&&java.util.Arrays.stream(current.getStackTrace()).anyMatch(f->f.getClassName().equals("java.util.concurrent.Semaphore")&&f.getMethodName().equals("tryAcquire"))){blocked=true;break;}Thread.sleep(5);}
            assertTrue(blocked);signal.cancel();assertFalse(waiting.get(1,TimeUnit.SECONDS));assertEquals(0,permits.availablePermits());
            // The same executor can be reused without a stray interrupt or thread-scoped lifetime.
            permits.release();assertTrue(pool.submit(()->SourceReadLifetime.tryAcquire(permits,1,TimeUnit.SECONDS)).get(2,TimeUnit.SECONDS));assertEquals(0,permits.availablePermits());
        }finally{pool.shutdownNow();assertTrue(pool.awaitTermination(2,TimeUnit.SECONDS));}
    }
    @Test void cancellationWinningImmediatelyAfterActualAcquireReturnsExactlyThatPermit() throws Exception {
        var signal=new Signal();var acquired=new AtomicInteger();
        var permits=new Semaphore(1){
            @Override public boolean tryAcquire(long timeout,TimeUnit unit) throws InterruptedException {
                boolean value=super.tryAcquire(timeout,unit);
                if(value){acquired.incrementAndGet();signal.cancel();}return value;
            }
        };
        try(var scope=new SourceReadLifetime(signal,System.nanoTime()+TimeUnit.SECONDS.toNanos(3))){
            assertFalse(SourceReadLifetime.tryAcquire(permits,2,TimeUnit.SECONDS));
        }
        assertEquals(1,acquired.get());assertEquals(1,permits.availablePermits());
    }
    private static final class Signal implements PreparationCancellation {
        final AtomicReference<Runnable> action=new AtomicReference<>();private volatile boolean lost;
        public boolean cancelled(){return lost;}
        public AutoCloseable register(Runnable abort){assertTrue(action.compareAndSet(null,abort));return ()->action.compareAndSet(abort,null);}
        void cancel(){lost=true;var abort=action.get();if(abort!=null)abort.run();}
    }
}
