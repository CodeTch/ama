package ama.task;
import ama.contract.ExternalContracts.*;
import ama.contract.TaskContracts.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Task07RecoveryBoundsIT {
 @Test void keysetRecoveryHasFiniteQueriesRowsAndAdvancesPastUnknownDebt(){
  var f=new Task07PreparedExternalIT();f.setup();var operations=new JdbcExternalOperations(f.queue);var token=operations.beginPrepared(f.lease,f.preparedRequest).orElseThrow();String model="T07_BOUND_"+UUID.randomUUID();
  var p=new JdbcModelPermitPool(operations,Map.of(model,new ModelConfiguration(model,1,1,true,ModelConfiguration.CutoffPolicy.EXPLICIT_SETTLEMENT)),(a,b,c)->{throw new AssertionError();});p.configure();var unknown=p.acquire(token,model,null).orElseThrow();
  assertThrows(IllegalArgumentException.class,()->p.recoverSettlements(0));assertThrows(IllegalArgumentException.class,()->p.recoverSettlements(65));
  String after="";String upper=null;int maximumQueries=0;long maximumRows=0;boolean visited=false;
  int reservations=f.jdbc.queryForObject("SELECT COUNT(*) FROM model_reservation",Integer.class);
  for(int i=0;i<=reservations;i++){
   f.source.clear();p.recoverSettlements(1);maximumQueries=Math.max(maximumQueries,f.source.sql.size());maximumRows=Math.max(maximumRows,f.source.rows);
   assertTrue(f.source.sql.size()<=14);assertTrue(f.source.rows<=12);
   var pages=f.source.sql.stream().filter(x->x.text().startsWith("SELECT id,model,execution_id")).toList();assertEquals(1,pages.size());var page=pages.getFirst();assertEquals(1,page.parameters().get(3));
   String a=page.parameters().get(1).toString(),u=page.parameters().get(2).toString();if(upper==null)upper=u;else assertEquals(upper,u);
   assertTrue(a.compareTo(after)>=0);after=a;
   if(f.source.sql.stream().anyMatch(x->x.text().startsWith("SELECT COUNT(*) FROM model_reservation r")&&unknown.id().equals(x.parameters().get(1)))){visited=true;break;}
  }
  assertTrue(visited);assertNull(f.jdbc.queryForObject("SELECT settled_at FROM model_reservation WHERE id=?",java.sql.Timestamp.class,unknown.id()));
  System.out.println("TASK07 recovery B=1 Qmax="+maximumQueries+" Rmax="+maximumRows+" envelope Q<=2+12B/R<=2+10B");
 }
 @Test void concurrentMaintenanceUsesOneCursorAndDoesNoSqlOnContendedInstance()throws Exception{
  var f=new Task07PreparedExternalIT();f.setup();var operations=new JdbcExternalOperations(f.queue);String model="T07_BOUND_"+UUID.randomUUID();var p=new JdbcModelPermitPool(operations,Map.of(model,new ModelConfiguration(model,1,1,true,ModelConfiguration.CutoffPolicy.EXPLICIT_SETTLEMENT)),(a,b,c)->{});p.configure();p.acquire(operations.beginPrepared(f.lease,f.preparedRequest).orElseThrow(),model,null).orElseThrow();
  var entered=new CountDownLatch(1);var release=new CountDownLatch(1);f.source.before=sql->{if(sql.text().startsWith("SELECT id,model,execution_id")){entered.countDown();Task07ExternalWorkerIT.await(release);}};
  var executor=Executors.newSingleThreadExecutor();try{var first=executor.submit(()->p.recoverSettlements(1));Task07ExternalWorkerIT.await(entered);int count=f.source.sql.size();assertEquals(0,p.recoverSettlements(1));assertEquals(count,f.source.sql.size());release.countDown();first.get(5,TimeUnit.SECONDS);}finally{release.countDown();executor.shutdownNow();}
 }
 @Test void recoverySettlesOnlyOldVerifiedExecutionWhenNewGenerationIsUnknown(){
  var f=new Task07InheritedFixture();var q=f.queue();var e=new JdbcExternalOperations(q);var s=f.spec();var lease=f.claimed(q,s);
  var request=new ExternalRequest("TEST_CALL",s.producerKey(),Task07InheritedFixture.hash("logical"+s.producerKey()),Task07InheritedFixture.hash(s.input().id()),"test-v1:"+s.input().id());
  var old=e.beginExternal(lease,request).orElseThrow();String model="T07_BOUND_"+UUID.randomUUID();var p=new JdbcModelPermitPool(e,Map.of(model,new ModelConfiguration(model,2,1,true,ModelConfiguration.CutoffPolicy.EXPLICIT_SETTLEMENT)),(a,b,c)->{});p.configure();var oldPermit=p.acquire(old,model,null).orElseThrow();
  e.observe(old,new ExternalObservation(ExternalOutcome.FAILED_ZERO_EFFECT,null,null,"REJECTED",new NoEffectProof(NoEffectBasis.DEFINITIVE_REJECTION,"TEST_VERIFIED_ZERO",java.time.Instant.now())),()->{});
  var next=new TaskSpec(s.type(),s.pool(),s.subject(),Task07InheritedFixture.hash("retry"+s.producerKey()),s.input(),s.inputFingerprint(),0,0,0,3);e.retryAfterZeroEffect(old.operationId(),1,next,request);var current=e.beginExternal(q.claim(s.pool(),"NEXT_GENERATION").orElseThrow(),request).orElseThrow();var currentPermit=p.acquire(current,model,null).orElseThrow();
  int bound=2*f.j.queryForObject("SELECT COUNT(*) FROM model_reservation",Integer.class)+2;for(int i=0;i<bound&&!f.j.queryForObject("SELECT settled_at IS NOT NULL FROM model_reservation WHERE id=?",Boolean.class,oldPermit.id());i++)p.recoverSettlements(1);
  assertTrue(f.j.queryForObject("SELECT settled_at IS NOT NULL FROM model_reservation WHERE id=?",Boolean.class,oldPermit.id()));assertNull(f.j.queryForObject("SELECT settled_at FROM model_reservation WHERE id=?",java.sql.Timestamp.class,currentPermit.id()));assertEquals("MARKED",f.j.queryForObject("SELECT state FROM external_operation WHERE id=?",String.class,current.operationId()));
 }
 @Test void transientSettlementFailureConsumesPositionThenRecoversOnTheNextPass(){
  var f=new Task07PreparedExternalIT();f.setup();var e=new JdbcExternalOperations(f.queue);var t=e.beginPrepared(f.lease,f.preparedRequest).orElseThrow();String model="T07_BOUND_"+UUID.randomUUID();var p=new JdbcModelPermitPool(e,Map.of(model,new ModelConfiguration(model,1,1,true,ModelConfiguration.CutoffPolicy.EXPLICIT_SETTLEMENT)),(a,b,c)->{});p.configure();var permit=p.acquire(t,model,null).orElseThrow();e.observePrepared(t,f.preparedObservation);
  var failed=new java.util.concurrent.atomic.AtomicBoolean();f.source.before=sql->{if(sql.text().startsWith("SELECT * FROM external_operation WHERE id=? FOR UPDATE")&&t.operationId().equals(sql.parameters().get(1))&&failed.compareAndSet(false,true))throw new org.springframework.dao.TransientDataAccessResourceException("T07_RETRYABLE_SETTLEMENT_FAULT");};
  int bound=2*f.jdbc.queryForObject("SELECT COUNT(*) FROM model_reservation",Integer.class)+2;boolean consumed=false;String previous=null;
  for(int i=0;i<bound&&!f.jdbc.queryForObject("SELECT settled_at IS NOT NULL FROM model_reservation WHERE id=?",Boolean.class,permit.id());i++){
   f.source.clear();p.recoverSettlements(1);assertTrue(f.source.sql.size()<=14);assertTrue(f.source.rows<=12);
   for(var sql:f.source.sql)if(sql.text().startsWith("SELECT id,model,execution_id")){String after=sql.parameters().get(1).toString();if(failed.get()&&permit.id().equals(after))consumed=true;previous=after;}
  }
  assertTrue(failed.get());assertTrue(consumed,"Failed candidate must be consumed before the next call");assertTrue(f.jdbc.queryForObject("SELECT settled_at IS NOT NULL FROM model_reservation WHERE id=?",Boolean.class,permit.id()));
 }

}
