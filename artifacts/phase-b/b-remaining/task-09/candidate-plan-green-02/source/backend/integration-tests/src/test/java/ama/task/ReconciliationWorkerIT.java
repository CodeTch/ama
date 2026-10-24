package ama.task;
import ama.worker.*;import ama.contract.ExternalContracts.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import java.time.*;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class ReconciliationWorkerIT {
 @Test void independentReconcilerOwnsHeartbeatAndRecordsResultAfterTaskExpiry()throws Exception{
  var f=new TaskFixture();var q=f.queue();var e=new JdbcExternalOperations(q);var s=f.spec();var task=f.claimed(q,s);var old=e.beginExternal(task,new ExternalRequest("TEST_CALL",s.producerKey(),TaskFixture.hash(s.producerKey()),TaskFixture.hash(s.input().id()),"test-v1:"+s.input().id())).orElseThrow();
  f.expire(task);f.j.update("UPDATE external_operation SET next_check_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND,reconcile_expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",old.operationId());
  var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var token=new AtomicReference<OperationToken>();var failure=new AtomicReference<Throwable>();
  try(var loop=new ReconciliationLoop(e,1,current->{if(!current.operationId().equals(old.operationId()))return;try{token.set(current);entered.countDown();assertTrue(release.await(15,TimeUnit.SECONDS));assertEquals(ObservationResult.APPLIED,e.observe(current,new ExternalObservation(ExternalOutcome.SUCCEEDED,"TEST_RESULT:"+s.input().id(),s.input(),null,null),()->{}));}catch(Throwable t){failure.set(t);}},"reconciliation-process")){
   loop.start();assertTrue(entered.await(10,TimeUnit.SECONDS),"due external operation must be reconciled");assertEquals(old.operationId(),token.get().operationId());assertFalse(e.renew(old));Thread.sleep(6100);
   assertTrue(f.j.queryForObject("SELECT reconcile_expires_at FROM external_operation WHERE id=?",java.time.LocalDateTime.class,old.operationId()).toInstant(ZoneOffset.UTC).isAfter(token.get().expiresAt().plusSeconds(4)));
   release.countDown();WorkerProcessIT.await(()->"SUCCEEDED".equals(f.j.queryForObject("SELECT state FROM external_operation WHERE id=?",String.class,old.operationId())),5);assertNull(failure.get());
  }finally{release.countDown();}
 }
}
