package ama.task;
import ama.contract.ExternalContracts.*;
import ama.contract.TaskContracts.*;
import ama.task.ExternalOperationPort.*;
import ama.worker.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static ama.task.Task07ExternalWorkerIT.*;

/** Actual expired authority and one local slot; every durable operation delegates to owned MySQL. */
class Task07ReceiptRecoveryIT {
 final Task07PreparedExternalIT f=new Task07PreparedExternalIT(),g=new Task07PreparedExternalIT();
 final JdbcExternalOperations first=new JdbcExternalOperations(f.queue),second=new JdbcExternalOperations(g.queue);
 final Map<String,JdbcExternalOperations> issuers=new ConcurrentHashMap<>();
 final AtomicInteger sends=new AtomicInteger();
 ExternalOperationPort route=new ExternalOperationPort(){
  JdbcExternalOperations by(OperationToken t){return issuers.get(t.operationId());}
  public Optional<OperationToken> beginExternal(TaskLease l,ExternalRequest r){throw new AssertionError();}
  public Optional<OperationToken> beginPrepared(TaskLease l,PreparedRequest r){var a=l.spec().equals(f.spec)?first:second;var t=a.beginPrepared(l,r);t.ifPresent(x->issuers.put(x.operationId(),a));return t;}
  public Optional<OperationToken> claimReconciliation(String owner){var t=first.claimReconciliation(owner);if(t.isPresent()){issuers.put(t.get().operationId(),first);return t;}t=second.claimReconciliation(owner);t.ifPresent(x->issuers.put(x.operationId(),second));return t;}
  public boolean renew(OperationToken t){return by(t).renew(t);}
  public ObservationResult observe(OperationToken t,ExternalObservation o,Runnable e){throw new AssertionError();}
  public ObservationResult observePrepared(OperationToken t,PreparedObservation o){return by(t).observePrepared(t,o);}
  public ObservationResult observePreparedFact(OperationToken t,PreparedObservation o){return by(t).observePreparedFact(t,o);}
  public ObservationResult recoverPreparedFact(OperationToken t,PreparedObservation o){return by(t).recoverPreparedFact(t,o);}
  public String retryAfterZeroEffect(String id,long n,TaskSpec s,ExternalRequest r){throw new AssertionError();}
 };
 final ModelPermitPool noPermits=new ModelPermitPool(){public Optional<Permit> acquire(OperationToken t,String m,java.time.Instant d){throw new AssertionError();}public boolean settled(Permit p){throw new AssertionError();}public int recoverSettlements(int n){return 0;}};
 void expire(Task07PreparedExternalIT x,OperationToken t){x.jdbc.update("UPDATE external_operation SET reconcile_expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND,next_check_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",t.operationId());}
 int bound(){return 2*((f.jdbc.queryForObject("SELECT COUNT(*) FROM external_operation",Integer.class)+63)/64)+4;}
 void pollUntil(ReconciliationLoop loop,java.util.function.BooleanSupplier done)throws Exception{for(int i=0;i<bound()&&!done.getAsBoolean();i++){invoke(loop,"poll");Thread.sleep(20);}assertTrue(done.getAsBoolean(),()->"Bounded polls exhausted: first="+f.state()+", second="+(g.lease==null?"not created":g.state()));}
 @Test void capacityOneReconcilerRecoversExpiredPendingReceiptThenProcessesUnrelatedWork()throws Exception{
  f.setup();var old=route.beginPrepared(f.lease,f.preparedRequest).orElseThrow();expire(f,old);
  var held=new AtomicReference<ExternalTaskExecution>();var firstDone=new CountDownLatch(1);var otherDone=new CountDownLatch(1);var error=new AtomicReference<Throwable>();
  try(var loop=ReconciliationLoop.prepared(route,noPermits,1,s->{try{if(s.token().operationId().equals(old.operationId())){held.set(s);expire(f,s.token());invoke(s,"renew");assertEquals(ObservationResult.STALE_TOKEN,s.observe(f.preparedObservation));firstDone.countDown();}else{assertEquals(ObservationResult.APPLIED,s.observe(g.preparedObservation));otherDone.countDown();}}catch(Throwable e){error.set(e);firstDone.countDown();otherDone.countDown();}},"T07_PENDING_RECOVERY")){
   pollUntil(loop,()->firstDone.getCount()==0);assertNull(error.get());assertEquals("RECONCILING",f.state());
   g.setup();var other=route.beginPrepared(g.lease,g.preparedRequest).orElseThrow();expire(g,other);
   pollUntil(loop,()->otherDone.getCount()==0);assertNull(error.get());assertEquals("STALE",f.state());assertEquals("SUCCEEDED",g.state());assertEquals(0,f.writes.get());assertEquals(1,g.writes.get());
   assertEquals("SUCCEEDED",f.jdbc.queryForObject("SELECT state FROM external_execution WHERE id=?",String.class,old.executionId()));assertEquals(0,sends.get());
  }
 }
 @Test void oldWorkerSlotRetiresAfterFreshReconcilerCommitsSameExecution()throws Exception{
  f.setup();f.jdbc.update("UPDATE durable_task SET expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",f.lease.taskId());
  var inTransport=new CountDownLatch(1);var release=new CountDownLatch(1);var done=new CountDownLatch(1);var otherDone=new CountDownLatch(1);var session=new AtomicReference<ExternalTaskExecution>();var error=new AtomicReference<Throwable>();
  TaskQueue queue=new TaskQueue(){public String enqueue(TaskSpec s){throw new AssertionError();}public Optional<TaskLease> claim(String p,String o){var l=f.queue.claim(f.spec.pool(),o);return l.isPresent()?l:g.queue.claim(g.spec.pool(),o);}public boolean renew(TaskLease l){return l.spec().equals(f.spec)?f.queue.renew(l):g.queue.renew(l);}public boolean complete(TaskLease l,TaskResult r){throw new AssertionError("Ordinary completion forbidden");}public boolean completeWithEffects(TaskLease l,TaskResult r,Runnable e){throw new AssertionError();}};
  WorkerLoop.ExternalHandler handler=s->{try{if(s.lease().spec().equals(f.spec)){session.set(s);assertTrue(s.begin(f.preparedRequest));s.send(()->{sends.incrementAndGet();inTransport.countDown();await(release);return f.preparedObservation;});done.countDown();}else{assertTrue(s.begin(g.preparedRequest));s.send(()->{sends.incrementAndGet();return g.preparedObservation;});otherDone.countDown();}}catch(Throwable t){error.set(t);done.countDown();otherDone.countDown();}};
  try(var worker=WorkerLoop.external(queue,route,noPermits,Map.of(f.spec.pool(),1),Map.of(f.spec.type(),handler,g.spec.type(),handler),Map.of(),"T07_OLD_WORKER")){
   worker.pollOnce(f.spec.pool());await(inTransport);var old=session.get().token();expire(f,old);invoke(session.get(),"renew");
   OperationToken fresh=null;for(int i=0;i<bound()&&fresh==null;i++)fresh=first.claimReconciliation("T07_FRESH_RECONCILER").orElse(null);assertNotNull(fresh);assertEquals(old.executionId(),fresh.executionId());assertTrue(fresh.reconcileEpoch()>old.reconcileEpoch());
   assertEquals(ObservationResult.APPLIED,first.observePrepared(fresh,f.preparedObservation));release.countDown();await(done);
   for(int i=0;i<3&&worker.observation().active()!=0;i++)worker.pollOnce(f.spec.pool());eventually(()->worker.observation().active()==0);assertNull(error.get());
   g.setup();g.jdbc.update("UPDATE durable_task SET expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",g.lease.taskId());worker.pollOnce(f.spec.pool());await(otherDone);assertNull(error.get());assertEquals(2,sends.get());assertEquals(1,f.writes.get());assertEquals("SUCCEEDED",g.state());
  }finally{release.countDown();}
 }
}
