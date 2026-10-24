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
    private static final class Signal implements PreparationCancellation {
        final AtomicReference<Runnable> action=new AtomicReference<>();private boolean lost;
        public boolean cancelled(){return lost;}
        public AutoCloseable register(Runnable abort){assertTrue(action.compareAndSet(null,abort));return ()->action.compareAndSet(abort,null);}
        void cancel(){lost=true;var abort=action.get();if(abort!=null)abort.run();}
    }
}
