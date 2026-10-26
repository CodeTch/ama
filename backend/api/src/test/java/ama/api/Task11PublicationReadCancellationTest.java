package ama.api;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class Task11PublicationReadCancellationTest {
 @Test void cancellationAbortsOwnedResourcesExactlyOnceAndLateRegistrationImmediately()throws Exception {var c=new PublicationReadRequests.Cancellation();var aborted=new AtomicInteger();c.register(aborted::incrementAndGet);c.cancel();c.cancel();assertEquals(1,aborted.get());c.register(aborted::incrementAndGet);assertEquals(2,aborted.get());assertTrue(c.cancelled());assertThrows(ama.contract.TranscriptContracts.Failure.class,c::check);}
 @Test void detachedResourcesAreNeverAborted()throws Exception {var c=new PublicationReadRequests.Cancellation();var aborted=new AtomicInteger();var registration=c.register(aborted::incrementAndGet);registration.close();registration.close();c.cancel();assertEquals(0,aborted.get());}
 @Test void shutdownCancelsAdmittedRequestBeforeJoiningExecutor()throws Exception {var reads=new PublicationReadRequests();var aborted=new AtomicInteger();var task=reads.start(c->{c.register(aborted::incrementAndGet);return 1;});assertEquals(1,task.getCallable().call());reads.close();assertEquals(1,aborted.get());}
 @Test void shutdownAbortsInFlightReadAndRejectsFutureAdmission()throws Exception {
  var reads=new PublicationReadRequests();var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);var aborted=new AtomicInteger();var finished=new java.util.concurrent.atomic.AtomicReference<Throwable>();
  var task=reads.start(c->{c.register(()->{aborted.incrementAndGet();release.countDown();});entered.countDown();if(!release.await(3,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Cancellation did not unblock read");return 1;});
  var thread=new Thread(()->{try{task.getCallable().call();}catch(Throwable error){finished.set(error);}},"b4-cancellation-component");thread.setDaemon(true);thread.start();try{assertTrue(entered.await(2,java.util.concurrent.TimeUnit.SECONDS));reads.close();thread.join(3000);assertFalse(thread.isAlive());assertEquals(1,aborted.get());assertInstanceOf(ama.contract.TranscriptContracts.Failure.class,finished.get());assertThrows(ama.contract.TranscriptContracts.Failure.class,()->reads.start(c->1));}finally{release.countDown();reads.close();thread.join(3000);}
 }
}
