package ama.transcript;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static ama.contract.MinutesContracts.*;
class PublicationGovernanceRefreshTest {
 @Test void metadataDriftRestartsOnlyTheAcquisitionAndIsBounded(){
  var attempts=new AtomicInteger();assertEquals("complete",PublicationGraph.retryObservation(()->{if(attempts.incrementAndGet()<3)throw new PublicationGraph.ObservationChanged();return "complete";}));assertEquals(3,attempts.get());
  attempts.set(0);var error=assertThrows(Failure.class,()->PublicationGraph.retryObservation(()->{attempts.incrementAndGet();throw new PublicationGraph.ObservationChanged();}));assertEquals(FailureCode.PROVENANCE_UNAVAILABLE,error.code());assertEquals(3,attempts.get());
 }
 @Test void provenanceBudgetAndPermissionFailuresAreNeverRetried(){
  for(RuntimeException expected:new RuntimeException[]{fail(FailureCode.PROVENANCE_UNAVAILABLE),fail(FailureCode.BUDGET_EXCEEDED),new ama.access.MeetingAccess.AccessDeniedException()}){
   var count=new AtomicInteger();assertSame(expected,assertThrows(RuntimeException.class,()->PublicationGraph.retryObservation(()->{count.incrementAndGet();throw expected;})));assertEquals(1,count.get());
  }
 }
 @Test void callerWorkNeverReplaysEvenIfItThrowsTheInternalDriftSignal(){
  var effects=new AtomicInteger();assertThrows(Failure.class,()->PublicationGraph.retryObservation(()->PublicationGovernanceBinding.invokeWork(()->{effects.incrementAndGet();throw new PublicationGraph.ObservationChanged();})));assertEquals(1,effects.get());
 }
 @Test void allAttemptsShareTheOriginalCumulativeLedger(){
  var budget=new Task11AcceptanceBindingTest().budget(false);long before=budget.usage()[0];var attempts=new AtomicInteger();
  PublicationGraph.retryObservation(()->{budget.capture(100,100,100,2);if(attempts.incrementAndGet()<3)throw new PublicationGraph.ObservationChanged();return true;});
  assertEquals(before+300,budget.usage()[0]);
 }
 @Test void rolledBackMetadataDeadlockReacquiresTheCompleteObservation(){
  var attempts=new AtomicInteger();assertEquals("complete",PublicationGraph.retryObservation(()->PublicationGovernanceBinding.captureMetadata(()->{if(attempts.incrementAndGet()<3)throw new org.springframework.dao.CannotAcquireLockException("deadlock");return "complete";})));assertEquals(3,attempts.get());
  attempts.set(0);var error=assertThrows(Failure.class,()->PublicationGraph.retryObservation(()->PublicationGovernanceBinding.captureMetadata(()->{attempts.incrementAndGet();throw new org.springframework.dao.CannotAcquireLockException("deadlock");})));assertEquals(FailureCode.PROVENANCE_UNAVAILABLE,error.code());assertEquals(3,attempts.get());
 }
 @Test void lockFailuresFromCallerEffectsAreNeverReplayed(){
  var effects=new AtomicInteger();var expected=new org.springframework.dao.CannotAcquireLockException("caller effect");assertSame(expected,assertThrows(RuntimeException.class,()->PublicationGraph.retryObservation(()->PublicationGovernanceBinding.invokeWork(()->{effects.incrementAndGet();throw expected;}))));assertEquals(1,effects.get());
 }
 @Test void metadataPermissionAndBudgetFailuresKeepTheirOriginalMeaning(){
  for(RuntimeException expected:new RuntimeException[]{fail(FailureCode.BUDGET_EXCEEDED),new ama.access.MeetingAccess.AccessDeniedException()})assertSame(expected,assertThrows(RuntimeException.class,()->PublicationGovernanceBinding.captureMetadata(()->{throw expected;})));
 }
}
