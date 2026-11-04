package ama.transcript;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
class TranscriptReadRetryTest {
 @Test void onlyAnObservedConcurrentRootChangeRestartsTheRead(){
  var root=new AtomicInteger(1);var calls=new AtomicInteger();
  assertEquals("fresh",TranscriptReadRetry.read(root::get,()->{if(calls.getAndIncrement()==0){root.incrementAndGet();throw fail(PROVENANCE_UNAVAILABLE);}return "fresh";}));
  assertEquals(2,calls.get());
 }
 @Test void unchangedCorruptProvenanceAndExhaustedBudgetsFailWithoutRetry(){
  for(var code:new FailureCode[]{PROVENANCE_UNAVAILABLE,BUDGET_EXCEEDED}){
   var calls=new AtomicInteger();assertEquals(code,assertThrows(Failure.class,()->TranscriptReadRetry.read(()->1,()->{calls.incrementAndGet();throw fail(code);})).code());assertEquals(1,calls.get());
  }
 }
 @Test void sustainedConcurrentWritersCannotCreateAnUnboundedRead(){
  var root=new AtomicInteger();var calls=new AtomicInteger();
  assertThrows(Failure.class,()->TranscriptReadRetry.read(root::get,()->{calls.incrementAndGet();root.incrementAndGet();throw fail(PROVENANCE_UNAVAILABLE);}));assertEquals(3,calls.get());
 }
}
