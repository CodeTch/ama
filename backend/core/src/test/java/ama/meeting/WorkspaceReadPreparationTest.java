package ama.meeting;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WorkspaceReadPreparationTest {
 @Test void independentPreparationsCanMakeProgressTogetherAndAllMustFinish(){
  var entered=new CountDownLatch(4);var completed=new AtomicInteger();
  WorkspaceReadPreparation.run(List.of("a","b","c","d"),new WorkspaceReadBudget(),id->{entered.countDown();try{assertTrue(entered.await(2,TimeUnit.SECONDS));}catch(InterruptedException e){throw new IllegalStateException(e);}completed.incrementAndGet();});
  assertEquals(4,completed.get());
 }
 @Test void failureIsPropagatedInsteadOfReturningPartialAuthorization(){
  var failure=new IllegalStateException("proof unavailable");assertSame(failure,assertThrows(IllegalStateException.class,()->WorkspaceReadPreparation.run(List.of("a"),new WorkspaceReadBudget(),id->{throw failure;})));
 }
 @Test void stalledPreparationCannotResetOrExceedTheRequestDeadline(){
  var d=WorkspaceReadBudget.DEFAULT;var budget=new WorkspaceReadBudget(new WorkspaceReadBudget.Limits(d.bytes(),d.responseBytes(),d.scalars(),d.units(),d.rows(),d.edges(),Duration.ofMillis(100)),()->false);var released=new CountDownLatch(1);
  assertThrows(WorkspaceReadBudget.Unavailable.class,()->WorkspaceReadPreparation.run(List.of("a"),budget,id->{try{new CountDownLatch(1).await();}catch(InterruptedException expected){released.countDown();Thread.currentThread().interrupt();}}));
  assertThrows(WorkspaceReadBudget.Unavailable.class,budget::check);
  try{assertTrue(released.await(2,TimeUnit.SECONDS));}catch(InterruptedException e){throw new IllegalStateException(e);}
 }
 @Test void tooManyPreparationsNeverStart(){
  var called=new AtomicInteger();assertThrows(WorkspaceReadBudget.Unavailable.class,()->WorkspaceReadPreparation.run(Collections.nCopies(129,"a"),new WorkspaceReadBudget(),id->called.incrementAndGet()));assertEquals(0,called.get());
 }
}
