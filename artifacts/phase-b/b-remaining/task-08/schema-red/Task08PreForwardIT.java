package ama.task;
import ama.contract.TaskContracts.*;
import ama.contract.ExternalContracts.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Task08PreForwardIT {
 @Test @Order(1) void normalOld14MarkersCoverAllStates(){
  var f=new Task08Fixture("behavior","T08_LEGACY",14);
  for(String state:List.of("MARKED","UNKNOWN","SUCCEEDED","FAILED","MANUAL_REQUIRED","RETRY_QUEUED")){
   var spec=f.spec("T08_LEGACY");var q=f.queue(spec.type());q.enqueue(spec);var lease=q.claim(spec.pool(),"T08_LEGACY").orElseThrow();var ext=new JdbcExternalOperations(q);var token=ext.beginExternal(lease,f.request(spec)).orElseThrow();
   if(state.equals("MARKED"))continue;
   ExternalObservation observation=switch(state){case "UNKNOWN"->new ExternalObservation(ExternalOutcome.UNKNOWN,null,null,"T08_UNKNOWN",null);case "MANUAL_REQUIRED"->new ExternalObservation(ExternalOutcome.MANUAL_REQUIRED,null,null,"T08_MANUAL",null);case "SUCCEEDED"->new ExternalObservation(ExternalOutcome.SUCCEEDED,"T08_RESULT:"+spec.input().id(),spec.input(),null,null);default->new ExternalObservation(ExternalOutcome.FAILED_ZERO_EFFECT,null,null,"T08_REJECT",new NoEffectProof(NoEffectBasis.DEFINITIVE_REJECTION,"T08_ZERO",Instant.now()));};
   assertEquals(ObservationResult.APPLIED,ext.observe(token,observation,()->{}));
   if(state.equals("RETRY_QUEUED")){var next=new TaskSpec(spec.type(),spec.pool(),spec.subject(),Task08Fixture.hash(UUID.randomUUID().toString()),spec.input(),spec.inputFingerprint(),0,0,0,3);ext.retryAfterZeroEffect(token.operationId(),1,next,f.request(spec));}
   assertEquals(state,f.j.queryForObject("SELECT state FROM external_operation WHERE id=?",String.class,token.operationId()));
  }
  assertEquals(6,f.j.queryForObject("SELECT COUNT(DISTINCT state) FROM external_operation",Integer.class));
 }
 @Test @Order(2) void ordinalMustExistBeforeNewScannerActivation(){
  var f=new Task08Fixture("behavior","T08_LEGACY",14);
  assertEquals(1,f.j.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='external_operation' AND COLUMN_NAME='reconcile_scan_seq'",Integer.class),"New ordinal missing on actual old14 schema (intentional schema RED)");
 }
}
