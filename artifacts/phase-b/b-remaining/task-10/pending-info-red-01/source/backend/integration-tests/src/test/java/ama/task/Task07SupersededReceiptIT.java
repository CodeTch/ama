package ama.task;

import ama.contract.ExternalContracts.*;
import ama.contract.TaskContracts.*;
import ama.task.ExternalOperationPort.*;
import ama.worker.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static ama.task.Task07ExternalWorkerIT.*;

/** Old terminal receipts survive a legitimate retry; every state transition uses the owned DB. */
class Task07SupersededReceiptIT {
 final Task07ReceiptRecoveryIT h=new Task07ReceiptRecoveryIT();
 final Task07PreparedExternalIT f=h.f;
 final JdbcExternalOperations operations=h.first;
 PreparedObservation zero(OperationToken token,TaskLease lease){
  var receipt=f.receipt(new ExternalObservation(ExternalOutcome.FAILED_ZERO_EFFECT,null,null,"T07_REJECTED",
      new NoEffectProof(NoEffectBasis.DEFINITIVE_REJECTION,"T07_ZERO:"+token.executionId(),Instant.now().truncatedTo(ChronoUnit.MICROS))));
  f.replayBindings.put(receipt,new JdbcTaskPreparations.ExternalFacts(lease.taskId(),lease.spec(),token.operationId(),token.executionId(),token.executionNo(),f.wire.executionRequestHash(),f.wire.requestManifest()));
  return receipt;
 }
 String successor(OperationToken old){
  var s=f.spec;var next=new TaskSpec(s.type(),s.pool(),s.subject(),Task07InheritedFixture.hash("retry:"+s.producerKey()),s.input(),s.inputFingerprint(),s.businessVersion(),s.priority(),s.cancelEpoch(),s.maxAttempts());
  f.coveredSpecs.add(next);return operations.retryAfterZeroEffect(old.operationId(),old.executionNo(),next,f.wire);
 }
 Map<String,Object> oldRows(OperationToken old){
  var rows=new LinkedHashMap<String,Object>();
  rows.put("execution",f.jdbc.queryForMap("SELECT * FROM external_execution WHERE id=?",old.executionId()));
  rows.put("task",f.jdbc.queryForMap("SELECT * FROM durable_task WHERE id=?",f.lease.taskId()));
  rows.put("attempts",f.jdbc.queryForList("SELECT * FROM task_attempt WHERE task_id=? ORDER BY attempt",f.lease.taskId()));
  rows.put("events",f.events());rows.put("outputs",f.jdbc.queryForObject("SELECT COUNT(*) FROM meeting_info_revision WHERE id=?",Integer.class,f.output));
  rows.put("audit",f.jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id=?",Integer.class,f.meeting));return rows;
 }
 Map<String,Object> nextRows(String task,OperationToken old){
  var rows=new LinkedHashMap<String,Object>();rows.put("operation",f.jdbc.queryForMap("SELECT * FROM external_operation WHERE id=?",old.operationId()));
  rows.put("execution",f.jdbc.queryForMap("SELECT * FROM external_execution WHERE operation_id=? AND execution_no=?",old.operationId(),old.executionNo()+1));
  rows.put("task",f.jdbc.queryForMap("SELECT * FROM durable_task WHERE id=?",task));
  rows.put("attempts",f.jdbc.queryForList("SELECT * FROM task_attempt WHERE task_id=? ORDER BY attempt",task));
  rows.put("permits",f.jdbc.queryForList("SELECT r.* FROM model_reservation r JOIN external_execution e ON e.id=r.execution_id WHERE e.task_id=?",task));return rows;
 }
 void assertEnded(OperationToken old){
  var e=f.jdbc.queryForMap("SELECT * FROM external_execution WHERE id=?",old.executionId());
  assertEquals("FAILED",e.get("state"));assertNotNull(e.get("finished_at"));assertEquals("DEFINITIVE_REJECTION",e.get("no_effect_basis"));assertEquals("T07_ZERO:"+old.executionId(),e.get("no_effect_evidence"));assertNotNull(e.get("no_effect_verified_at"));
  var a=f.jdbc.queryForMap("SELECT * FROM task_attempt WHERE task_id=? AND attempt=?",f.lease.taskId(),e.get("task_attempt"));
  assertNotNull(a.get("ended_at"));assertEquals("FAILED",a.get("outcome"));assertEquals("T07_REJECTED",a.get("error_code"));assertNotNull(a.get("owner"));
  assertEquals("FAILED",f.state());assertEquals("T07_REJECTED",f.jdbc.queryForObject("SELECT last_error FROM durable_task WHERE id=?",String.class,f.lease.taskId()));
  assertNull(f.jdbc.queryForObject("SELECT output_id FROM durable_task WHERE id=?",String.class,f.lease.taskId()));
 }
 void lostAck(Runnable action){
  var once=new AtomicBoolean();f.source.committed=()->{if(once.compareAndSet(false,true))throw new IllegalStateException("T07_ZERO_ACK_LOST");};
  try{var failure=assertThrows(IllegalStateException.class,action::run);assertEquals("T07_ZERO_ACK_LOST",failure.getMessage());}finally{f.source.committed=()->{};}
 }
 Object jsonValue(Object value){
  if(value instanceof Map<?,?> map){var result=new LinkedHashMap<String,Object>();map.forEach((k,v)->result.put(k.toString(),jsonValue(v)));return result;}
  if(value instanceof List<?> list)return list.stream().map(this::jsonValue).toList();
  if(value instanceof java.time.temporal.TemporalAccessor||value instanceof java.util.Date)return value.toString();return value;
 }
 void evidence(String name,Map<String,Object> values)throws Exception{
  var path=System.getenv("TASK07_EVIDENCE_WINDOW");if(path!=null)new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(java.nio.file.Path.of(path,name+".json").toFile(),jsonValue(values));
 }
 void immutable(Map<String,Object> before,OperationToken old){assertEquals(before,oldRows(old));assertEquals(1,f.terminalWrites.get());assertEquals(0,f.writes.get());}

 @Test void oldCommitAckLossThenPreparedSuccessorRetiresActualWorkerSlotOnce()throws Exception{
  f.setup();f.jdbc.update("UPDATE durable_task SET expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",f.lease.taskId());
  var committed=new CountDownLatch(1);var release=new CountDownLatch(1);var otherDone=new CountDownLatch(1);var error=new AtomicReference<Throwable>();var session=new AtomicReference<ExternalTaskExecution>();var receipt=new AtomicReference<PreparedObservation>();var claimedFirst=new AtomicBoolean();var sends=new AtomicInteger();
  TaskQueue queue=new TaskQueue(){public String enqueue(TaskSpec s){throw new AssertionError();}public Optional<TaskLease> claim(String p,String owner){if(!claimedFirst.get()){var l=f.queue.claim(f.spec.pool(),owner);if(l.isPresent())claimedFirst.set(true);return l;}return h.g.queue.claim(h.g.spec.pool(),owner);}public boolean renew(TaskLease l){return l.spec().equals(f.spec)?f.queue.renew(l):h.g.queue.renew(l);}public boolean complete(TaskLease l,TaskResult r){throw new AssertionError("No ordinary completion");}public boolean completeWithEffects(TaskLease l,TaskResult r,Runnable e){throw new AssertionError();}};
  WorkerLoop.ExternalHandler handler=s->{try{
   if(s.lease().spec().equals(f.spec)){
    session.set(s);assertTrue(s.begin(f.preparedRequest));receipt.set(zero(s.token(),s.lease()));
    // Set the actual JDBC after-commit fault only after real permit acquisition in send().
    try{s.send(()->{sends.incrementAndGet();var once=new AtomicBoolean();f.source.committed=()->{if(once.compareAndSet(false,true))throw new IllegalStateException("T07_ZERO_ACK_LOST");};return receipt.get();});fail("Commit ACK must be lost");}
    catch(IllegalStateException lost){assertEquals("T07_ZERO_ACK_LOST",lost.getMessage());}finally{f.source.committed=()->{};}
    committed.countDown();await(release);
   }else{assertTrue(s.begin(h.g.preparedRequest));assertTrue(s.send(()->{sends.incrementAndGet();return h.g.preparedObservation;}));otherDone.countDown();}
  }catch(Throwable t){error.set(t);committed.countDown();otherDone.countDown();}};
  var pool=h.permits();try(var worker=WorkerLoop.external(queue,h.route,pool,Map.of(f.spec.pool(),1),Map.of(f.spec.type(),handler,h.g.spec.type(),handler),Map.of(f.spec.type(),h.model),"T07_SUPERSEDED_WORKER")){
   worker.pollOnce(f.spec.pool());await(committed);assertNull(error.get());var old=session.get().token();assertEnded(old);var before=oldRows(old);
   String next=successor(old);var successorBefore=nextRows(next,old);assertEquals("RETRY_QUEUED",f.jdbc.queryForObject("SELECT state FROM external_operation WHERE id=?",String.class,old.operationId()));assertEquals("PREPARED",f.jdbc.queryForObject("SELECT state FROM external_execution WHERE task_id=?",String.class,next));
   h.g.setup();h.g.jdbc.update("UPDATE durable_task SET expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",h.g.lease.taskId());release.countDown();
   eventually(()->{worker.pollOnce(f.spec.pool());return otherDone.getCount()==0&&worker.observation().active()==0;});assertNull(error.get());
   immutable(before,old);assertFalse(operations.hasCallAuthority(old));assertEquals(successorBefore,nextRows(next,old));assertEquals(2,sends.get());assertEquals("SUCCEEDED",h.g.state());assertEquals(1,f.zeroReplays.get());
   assertEquals(ObservationResult.STALE_TOKEN,session.get().observe(receipt.get()));assertFalse(session.get().send(()->{throw new AssertionError("Never resend");}));
   assertEquals(ObservationResult.STALE_TOKEN,operations.observePrepared(old,receipt.get()));assertEquals(ObservationResult.STALE_TOKEN,operations.observePreparedFact(old,receipt.get()));
   h.settleBounded(pool,old.executionId());evidence("worker-old-receipt",Map.of("before",before,"after",oldRows(old),"successorBefore",successorBefore,"successorAfter",nextRows(next,old),"sends",sends.get(),"localActive",worker.observation().active()));
  }finally{release.countDown();}
 }

 @Test void preparedReconciliationRetiresOldZeroReceiptAndRunsUnrelatedWorkOnSameSlot()throws Exception{
  f.setup();var old=h.route.beginPrepared(f.lease,f.preparedRequest).orElseThrow();var pool=h.permits();pool.acquire(old,h.model,null).orElseThrow();h.expire(f,old);
  var committed=new CountDownLatch(1);var release=new CountDownLatch(1);var otherDone=new CountDownLatch(1);var error=new AtomicReference<Throwable>();var session=new AtomicReference<ExternalTaskExecution>();
  try(var loop=ReconciliationLoop.prepared(h.route,pool,1,s->{try{if(s.token().operationId().equals(old.operationId())){session.set(s);var receipt=zero(s.token(),f.lease);lostAck(()->{try{s.observe(receipt);}catch(InterruptedException x){throw new AssertionError(x);}});committed.countDown();await(release);}else{assertEquals(ObservationResult.APPLIED,s.observe(h.g.preparedObservation));otherDone.countDown();}}catch(Throwable t){error.set(t);committed.countDown();otherDone.countDown();}},"T07_SUPERSEDED_RECONCILER")){
   h.pollUntil(loop,()->committed.getCount()==0);assertNull(error.get());assertEnded(old);var before=oldRows(old);String next=successor(old);var nextBefore=nextRows(next,old);
   h.g.setup();var other=h.route.beginPrepared(h.g.lease,h.g.preparedRequest).orElseThrow();h.expire(h.g,other);release.countDown();h.pollUntil(loop,()->otherDone.getCount()==0);
   assertNull(error.get());immutable(before,old);assertFalse(operations.hasCallAuthority(session.get().token()));assertTrue(pool.acquire(old,h.model,null).isEmpty());assertEquals(nextBefore,nextRows(next,old));assertEquals("SUCCEEDED",h.g.state());assertEquals(1,f.zeroReplays.get());h.settleBounded(pool,old.executionId());
   assertFalse(session.get().send(()->{throw new AssertionError("Reconciliation never sends");}));evidence("reconciliation-old-receipt",Map.of("before",before,"after",oldRows(old),"successorBefore",nextBefore,"successorAfter",nextRows(next,old)));
  }finally{release.countDown();}
 }

 @Test void oldFactRecoveryLeavesRunningSuccessorAndRealPermitUntouched()throws Exception{
  f.setup();var old=operations.beginPrepared(f.lease,f.preparedRequest).orElseThrow();var zero=zero(old,f.lease);
  var pool=new JdbcModelPermitPool(operations,Map.of(h.model,new ModelConfiguration(h.model,2,1,true,ModelConfiguration.CutoffPolicy.EXPLICIT_SETTLEMENT)),(t,m,d)->{});pool.configure();var oldPermit=pool.acquire(old,h.model,null).orElseThrow();
  lostAck(()->operations.observePrepared(old,zero));assertEnded(old);var before=oldRows(old);String task=successor(old);var nextLease=f.queue.claim(f.spec.pool(),"T07_RUNNING_SUCCESSOR").orElseThrow();var next=operations.beginPrepared(nextLease,f.preparedRequest).orElseThrow();var permit=pool.acquire(next,h.model,null).orElseThrow();var nextBefore=nextRows(task,old);
  assertTrue(operations.hasCallAuthority(next));f.source.clear();assertEquals(ObservationResult.APPLIED,operations.recoverPreparedFact(old,zero));
  int recoveryQueries=f.source.sql.size();long recoveryRows=f.source.rows;
  assertTrue(recoveryQueries<=48);assertTrue(recoveryRows<=48);
  assertTrue(f.source.sql.stream().noneMatch(sql->sql.text().matches("(?is)^(UPDATE|INSERT|DELETE).*")),"Retirement must only read durable facts");
  assertEquals(ObservationResult.APPLIED,operations.recoverPreparedFact(old,zero));
  immutable(before,old);assertFalse(operations.hasCallAuthority(old));assertEquals(nextBefore,nextRows(task,old));assertTrue(operations.hasCallAuthority(next));assertFalse(pool.settled(permit));assertTrue(pool.settled(oldPermit));assertEquals(nextBefore,nextRows(task,old));
  assertNull(f.jdbc.queryForObject("SELECT ended_at FROM task_attempt WHERE task_id=? AND attempt=?",java.sql.Timestamp.class,task,nextLease.attempt()));
  evidence("running-successor-permit",Map.of("before",before,"after",oldRows(old),"successorBefore",nextBefore,"successorAfter",nextRows(task,old),"oldPermitSettled",true,"successorPermitSettled",false,"recoveryQueries",recoveryQueries,"recoveryRows",recoveryRows));
 }

 @ParameterizedTest @ValueSource(strings={"proof","result","unended","attemptNumber","attemptOutcome","attemptError","unknown","foreignExecution","foreignReceipt"})
 void rejectsMismatchedOldFactOrUnendedAttempt(String mismatch){
  f.setup();var old=operations.beginPrepared(f.lease,f.preparedRequest).orElseThrow();var receipt=zero(old,f.lease);operations.observePrepared(old,receipt);assertEnded(old);String task=successor(old);var nextBefore=nextRows(task,old);OperationToken supplied=old;PreparedObservation suppliedReceipt=receipt;
  switch(mismatch){
   case "proof" -> {var remote=f.remote(receipt);suppliedReceipt=f.receipt(new ExternalObservation(ExternalOutcome.FAILED_ZERO_EFFECT,null,null,"T07_REJECTED",new NoEffectProof(NoEffectBasis.DEFINITIVE_REJECTION,"DIFFERENT",remote.noEffectProof().verifiedAt())));f.replayBindings.put(suppliedReceipt,f.replayBindings.get(receipt));}
   case "result" -> f.jdbc.update("UPDATE durable_task SET last_error='WRONG_RESULT' WHERE id=?",f.lease.taskId());
   case "unended" -> f.jdbc.update("UPDATE task_attempt SET ended_at=NULL WHERE task_id=? AND attempt=?",f.lease.taskId(),f.lease.attempt());
   case "attemptNumber" -> f.jdbc.update("UPDATE external_execution SET task_attempt=task_attempt+1 WHERE id=?",old.executionId());
   case "attemptOutcome" -> f.jdbc.update("UPDATE task_attempt SET outcome='SUCCEEDED' WHERE task_id=? AND attempt=?",f.lease.taskId(),f.lease.attempt());
   case "attemptError" -> f.jdbc.update("UPDATE task_attempt SET error_code='OTHER_ERROR' WHERE task_id=? AND attempt=?",f.lease.taskId(),f.lease.attempt());
   case "unknown" -> suppliedReceipt=f.receipt(new ExternalObservation(ExternalOutcome.UNKNOWN,null,null,"T07_UNKNOWN",null));
   case "foreignExecution" -> supplied=new OperationToken(old.operationId(),UUID.randomUUID().toString(),old.executionNo(),old.reconcileEpoch(),old.owner(),old.expiresAt(),old.subject());
   case "foreignReceipt" -> suppliedReceipt=new PreparedObservation(){};
   default -> throw new AssertionError();
  }
  var damaged=oldRows(old);final var token=supplied;final var observation=suppliedReceipt;
  if(mismatch.equals("foreignReceipt"))assertThrows(IllegalArgumentException.class,()->operations.recoverPreparedFact(token,observation));else assertEquals(ObservationResult.STALE_TOKEN,operations.recoverPreparedFact(token,observation));
  assertEquals(damaged,oldRows(old));assertEquals(nextBefore,nextRows(task,old));assertEquals(1,f.terminalWrites.get());assertEquals(0,f.writes.get());assertFalse(operations.hasCallAuthority(old));
 }
}
