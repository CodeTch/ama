package ama.task;
import ama.contract.ExternalContracts.*;
import ama.contract.TaskContracts.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static ama.task.Task07ExternalWorkerIT.*;

class Task07PreparedMatrixIT {
 final Task07PreparedExternalIT f=new Task07PreparedExternalIT();
 final JdbcExternalOperations operations=new JdbcExternalOperations(f.queue);
 @BeforeEach void setup(){f.setup();}
 @Test void unavailablePreparationRetainsSameReceiptForLaterAtomicCompletion(){
  var token=operations.beginPrepared(f.lease,f.preparedRequest).orElseThrow();f.preparationUnavailable.set(true);
  assertThrows(DomainPreparationUnavailableException.class,()->operations.observePrepared(token,f.preparedObservation));assertEquals("RECONCILING",f.state());assertEquals(0,f.writes.get());
  f.preparationUnavailable.set(false);assertEquals(ObservationResult.APPLIED,operations.observePrepared(token,f.preparedObservation));assertEquals(1,f.writes.get());
 }
 @ParameterizedTest @ValueSource(strings={"DISABLED","DELETED","GOVERNED"})
 void deniedPreparedAuthorityRecordsOnlyExactTerminalFactAndExcludesUnknown(String denial){
  var token=operations.beginPrepared(f.lease,f.preparedRequest).orElseThrow();
  f.tx.required(()->{f.access.lock(f.actor,f.meeting);if(denial.equals("DISABLED"))f.jdbc.update("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id=?",f.owner);else if(denial.equals("DELETED")){f.jdbc.update("UPDATE meeting SET lifecycle='DELETED' WHERE id=?",f.meeting);f.jdbc.update("UPDATE task_subject SET deleted=1 WHERE subject_kind='MEETING' AND subject_id=?",f.meeting);}else f.jdbc.update("UPDATE meeting SET governance_state='WITHDRAW_PENDING' WHERE id=?",f.meeting);return null;});f.deniedFacts.set(true);
  f.jdbc.update("UPDATE external_operation SET reconcile_epoch=reconcile_epoch+1,reconcile_expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",token.operationId());
  var unknown=f.receipt(new ExternalObservation(ExternalOutcome.UNKNOWN,null,null,"T07_UNKNOWN",null));
  assertEquals(ObservationResult.STALE_TOKEN,operations.observePrepared(token,unknown));assertEquals("RECONCILING",f.state());
  var forged=new OperationToken(token.operationId(),UUID.randomUUID().toString(),token.executionNo(),token.reconcileEpoch(),token.owner(),token.expiresAt(),token.subject());
  assertEquals(ObservationResult.STALE_TOKEN,operations.observePrepared(forged,f.preparedObservation));
  assertEquals(ObservationResult.RECORDED_FENCED,operations.observePrepared(token,f.preparedObservation));assertEquals("STALE",f.state());assertEquals(0,f.writes.get());assertEquals(0,f.jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id=?",Integer.class,f.meeting));assertEquals("SUCCEEDED",f.jdbc.queryForObject("SELECT state FROM external_execution WHERE id=?",String.class,token.executionId()));
 }
 @Test void fourActualPreparedDiscoveryRestartsPreserveReceiptThenQuarantineOriginalExecution()throws Exception{
  var token=operations.beginPrepared(f.lease,f.preparedRequest).orElseThrow();
  var zero=f.receipt(new ExternalObservation(ExternalOutcome.FAILED_ZERO_EFFECT,null,null,"T07_ZERO",new NoEffectProof(NoEffectBasis.DEFINITIVE_REJECTION,"T07_PROOF",Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS))));
  assertEquals(ObservationResult.APPLIED,operations.observePrepared(token,zero));
  var specs=new ArrayList<TaskSpec>();for(int i=0;i<4;i++){var s=new TaskSpec(f.spec.type(),f.spec.pool(),f.spec.subject(),Task08InheritedFixture.hash(f.spec.producerKey()+i),f.spec.input(),f.spec.inputFingerprint(),0,0,0,3);specs.add(s);f.coveredSpecs.add(s);}
  var discovered=new CountDownLatch[4];var advanced=new CountDownLatch[4];for(int i=0;i<4;i++){discovered[i]=new CountDownLatch(1);advanced[i]=new CountDownLatch(1);}
  var attempts=new AtomicInteger();var holders=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
  f.source.after=e->{if(Thread.currentThread().getName().equals("T07_DISCOVERY_OBSERVER")&&e.text().equals("SELECT * FROM external_operation WHERE id=?")){int index=attempts.getAndIncrement();if(index<4){holders.add(TransactionSynchronizationManager.getResource(f.source));discovered[index].countDown();await(advanced[index]);}}};
  int rollbacks=Collections.frequency(f.source.controls,"rollback");var executor=Executors.newSingleThreadExecutor(r->new Thread(r,"T07_DISCOVERY_OBSERVER"));
  try{var observed=executor.submit(()->assertThrows(ObservationRetryRequiredException.class,()->operations.observePrepared(token,f.preparedObservation)));
   for(int i=0;i<4;i++){await(discovered[i]);operations.retryAfterZeroEffect(token.operationId(),i+1,specs.get(i),f.wire);var lease=f.queue.claim(f.spec.pool(),"T07_GENERATION_ADVANCE").orElseThrow();var current=operations.beginPrepared(lease,f.preparedRequest).orElseThrow();assertEquals(ObservationResult.APPLIED,operations.observePrepared(current,zero));advanced[i].countDown();}
   observed.get(10,TimeUnit.SECONDS);assertEquals(4,attempts.get());assertEquals(4,holders.size());assertTrue(Collections.frequency(f.source.controls,"rollback")-rollbacks>=4);assertEquals(0,f.writes.get());
  }finally{for(var latch:advanced)latch.countDown();executor.shutdownNow();f.source.after=e->{};}
  assertEquals(ObservationResult.QUARANTINED,operations.observePrepared(token,f.preparedObservation));assertEquals(0,f.writes.get());assertEquals(1,f.jdbc.queryForObject("SELECT COUNT(*) FROM task_outbox WHERE task_id=? AND event_type='EXTERNAL_CONTRADICTION'",Integer.class,f.lease.taskId()));
 }
}
