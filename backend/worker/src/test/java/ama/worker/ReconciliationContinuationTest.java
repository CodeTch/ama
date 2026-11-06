package ama.worker;

import ama.contract.ExternalContracts.*;
import ama.contract.TaskContracts.*;
import ama.task.ExternalOperationPort;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReconciliationContinuationTest {
 @Test void anEmptyBoundedPageDoesNotDelayAnAlreadyDueTailUntilTheNextIdlePoll()throws Exception {
  var pages=new CountDownLatch(2);
  var operations=new ExternalOperationPort(){
   public boolean hasDueReconciliation(){return pages.getCount()>0;}
   public boolean hasMoreReconciliationPages(){return pages.getCount()==1;}
   public Optional<OperationToken> claimReconciliation(String owner){pages.countDown();return Optional.empty();}
   public Optional<OperationToken> beginExternal(TaskLease lease,ExternalRequest request){throw new AssertionError();}
   public boolean renew(OperationToken token){throw new AssertionError();}
   public ObservationResult observe(OperationToken token,ExternalObservation observation,Runnable effects){throw new AssertionError();}
   public String retryAfterZeroEffect(String id,long execution,TaskSpec task,ExternalRequest request){throw new AssertionError();}
  };
  try(var loop=new ReconciliationLoop(operations,1,token->{throw new AssertionError();},"CONTINUATION_TEST")){
   loop.start();assertTrue(pages.await(3,TimeUnit.SECONDS));
  }
 }
}
