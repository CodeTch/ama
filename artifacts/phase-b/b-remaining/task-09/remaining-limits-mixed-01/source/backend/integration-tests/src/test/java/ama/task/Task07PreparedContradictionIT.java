package ama.task;
import ama.contract.ExternalContracts.*;
import ama.contract.TaskContracts.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class Task07PreparedContradictionIT {
 void contradiction(boolean successor){
  var f=new Task07PreparedExternalIT();f.setup();var e=new JdbcExternalOperations(f.queue);var old=e.beginPrepared(f.lease,f.preparedRequest).orElseThrow();String model="T07_FIX_"+UUID.randomUUID();var p=new JdbcModelPermitPool(e,Map.of(model,new ModelConfiguration(model,2,1,true,ModelConfiguration.CutoffPolicy.EXPLICIT_SETTLEMENT)),(a,b,c)->{});p.configure();var oldPermit=p.acquire(old,model,null).orElseThrow();
  var zero=f.receipt(new ExternalObservation(ExternalOutcome.FAILED_ZERO_EFFECT,null,null,"REJECTED",new NoEffectProof(NoEffectBasis.DEFINITIVE_REJECTION,"T07_VERIFIED_ZERO",java.time.Instant.now())));assertEquals(ObservationResult.APPLIED,e.observePrepared(old,zero));
  var s=f.spec;var next=new TaskSpec(s.type(),s.pool(),s.subject(),Task07InheritedFixture.hash("retry"+s.producerKey()),s.input(),s.inputFingerprint(),0,0,0,3);f.coveredSpecs.add(next);Permit newer=null;
  if(successor){e.retryAfterZeroEffect(old.operationId(),1,next,f.wire);var token=e.beginPrepared(f.queue.claim(s.pool(),"T07_SUCCESSOR").orElseThrow(),f.preparedRequest).orElseThrow();newer=p.acquire(token,model,null).orElseThrow();}
  assertEquals(ObservationResult.QUARANTINED,e.observePrepared(old,f.preparedObservation));assertEquals("MANUAL_REQUIRED",f.jdbc.queryForObject("SELECT state FROM external_operation WHERE id=?",String.class,old.operationId()));assertEquals("CONTRADICTORY_SUCCESS",f.jdbc.queryForObject("SELECT last_error FROM external_operation WHERE id=?",String.class,old.operationId()));
  assertEquals(1,f.jdbc.queryForObject("SELECT COUNT(*) FROM task_outbox WHERE task_id=? AND event_type='EXTERNAL_CONTRADICTION'",Integer.class,f.lease.taskId()));assertEquals(0,f.writes.get());assertFalse(e.hasCallAuthority(old));
  var denied=new TaskSpec(s.type(),s.pool(),s.subject(),Task07InheritedFixture.hash("denied"+s.producerKey()),s.input(),s.inputFingerprint(),0,0,0,3);f.coveredSpecs.add(denied);assertThrows(IllegalStateException.class,()->e.retryAfterZeroEffect(old.operationId(),successor?2:1,denied,f.wire));
  if(newer!=null){assertFalse(p.settled(newer));assertNull(f.jdbc.queryForObject("SELECT settled_at FROM model_reservation WHERE id=?",java.sql.Timestamp.class,newer.id()));}
 }
 @Test void verifiedSuccessQuarantinesCurrentPreparedZeroEffectFailure(){contradiction(false);}
 @Test void verifiedOldSuccessQuarantinesSuccessorWithoutSettlingItsReservation(){contradiction(true);}
 @Test void unequalSuccessResultIsRejectedWhileEqualTerminalReplayIsIdempotent(){var f=new Task07PreparedExternalIT();f.setup();var e=new JdbcExternalOperations(f.queue);var t=e.beginPrepared(f.lease,f.preparedRequest).orElseThrow();assertEquals(ObservationResult.APPLIED,e.observePrepared(t,f.preparedObservation));assertEquals(ObservationResult.APPLIED,e.observePrepared(t,f.preparedObservation));var unequal=f.receipt(new ExternalObservation(ExternalOutcome.SUCCEEDED,"OTHER_RESULT",null,null,null));assertEquals(ObservationResult.STALE_TOKEN,e.observePrepared(t,unequal));assertEquals(1,f.writes.get());}
}
