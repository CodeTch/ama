package ama.task;
import ama.contract.TaskContracts.*;
import ama.contract.ExternalContracts.*;
import java.util.*;import java.time.*;import java.nio.file.*;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.*;

/** Actual generic queue/transactions; explicitly synthetic framework policy/input, no remote HTTP or app graph. */
class Task13GenericAttemptIT {
 final com.fasterxml.jackson.databind.ObjectMapper json=new com.fasterxml.jackson.databind.ObjectMapper();
 @Test void genericTerminalObservationsCloseAttemptsAtomically()throws Exception {
  Path out=Path.of(System.getenv("AMA_TASK11_EVIDENCE_WINDOW"));assertTrue(Set.of("generic-attempt-red","generic-attempt-green").contains(out.getFileName().toString()));assertTrue(Files.isRegularFile(out.resolve("consumed.json")));
  var f=new Task13AttemptFixture();var baseline=f.j.queryForList("SELECT id FROM meeting ORDER BY id",String.class);var protectedRows=prior(f,baseline);var checks=new ArrayList<Executable>();
  for(String scenario:List.of("success","zero-effect","fenced-success","rollback-success","unknown-success"))checks.add(()->check(f,out,scenario));
  try{assertAll("Every generic terminal observation closes its own marked attempt",checks);}finally{assertEquals(protectedRows,prior(f,baseline),"Prior owner04 business histories must remain unchanged");Files.writeString(out.resolve("prior-business-preserved.json"),json.writeValueAsString(Map.of("priorMeetings",baseline,"groups",protectedRows.keySet(),"noOldRowRepair",true)),StandardOpenOption.CREATE_NEW);}
 }
 void check(Task13AttemptFixture f,Path out,String scenario)throws Exception {
  var q=f.queue();var operations=new JdbcExternalOperations(q);var spec=f.spec("T13_GENERIC",3);var lease=f.claimed(q,spec);var request=new ExternalRequest("TEST_CALL",spec.producerKey(),Task13AttemptFixture.hash("logical"+spec.producerKey()),Task13AttemptFixture.hash("execution"+spec.input().id()),"test-v1:"+spec.input().id());
  var token=operations.beginExternal(lease,request).orElseThrow();assertEquals("RECONCILING",f.j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,lease.taskId()));var open=f.j.queryForMap("SELECT * FROM task_attempt WHERE task_id=? AND attempt=?",lease.taskId(),lease.attempt());assertNull(open.get("ended_at"));
  var success=new ExternalObservation(ExternalOutcome.SUCCEEDED,"TEST_RESULT:"+spec.input().id(),spec.input(),null,null);
  if(scenario.equals("unknown-success")){assertEquals(ObservationResult.APPLIED,operations.observe(token,new ExternalObservation(ExternalOutcome.UNKNOWN,null,null,"TIMEOUT",null),()->fail("Unknown must not invoke effects")));assertNull(f.j.queryForMap("SELECT ended_at FROM task_attempt WHERE task_id=?",lease.taskId()).get("ended_at"));}
  if(scenario.equals("rollback-success")){
   var before=taskRows(f,lease.taskId(),token.operationId());assertThrows(IllegalStateException.class,()->operations.observe(token,success,()->{f.j.update("UPDATE meeting SET needs_generation=1 WHERE id=?",spec.subject().id());throw new IllegalStateException("Injected generic effect rollback");}));assertEquals(before,taskRows(f,lease.taskId(),token.operationId()));assertEquals(0,f.j.queryForObject("SELECT needs_generation FROM meeting WHERE id=?",Integer.class,spec.subject().id()));
  }
  boolean fenced=scenario.equals("fenced-success"),rejected=scenario.equals("zero-effect");if(fenced)f.advance(spec,1,0,false);
  var observation=rejected?new ExternalObservation(ExternalOutcome.FAILED_ZERO_EFFECT,null,null,"REJECTED",new NoEffectProof(NoEffectBasis.DEFINITIVE_REJECTION,"TEST_VERIFIED_ZERO",Instant.now())):success;
  assertEquals(fenced?ObservationResult.RECORDED_FENCED:ObservationResult.APPLIED,operations.observe(token,observation,()->{if(fenced)fail("Fenced observation cannot apply business effects");}));
  String expected=fenced?"STALE":rejected?"FAILED":"SUCCEEDED";assertEquals(expected,f.j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,lease.taskId()));
  var terminal=f.j.queryForMap("SELECT * FROM task_attempt WHERE task_id=? AND attempt=?",lease.taskId(),lease.attempt());var evidence=new TreeMap<String,Object>();evidence.put("scenario",scenario);evidence.put("task",lease.taskId());evidence.put("execution",token.executionId());evidence.put("operation",token.operationId());evidence.put("expectedAttemptOutcome",expected);evidence.put("attemptClosed",terminal.get("ended_at")!=null);evidence.put("attemptOutcome",terminal.get("outcome"));evidence.put("attemptError",terminal.get("error_code"));evidence.put("frameworkPolicySubstitute",true);evidence.put("remoteHttp",false);Files.writeString(out.resolve(scenario+"-terminal.json"),json.writeValueAsString(evidence),StandardOpenOption.CREATE_NEW);
  assertNotNull(terminal.get("ended_at"),scenario+": generic terminal attempt must be closed");assertEquals(expected,terminal.get("outcome"));assertEquals(fenced?"BUSINESS_FENCED":rejected?"REJECTED":null,terminal.get("error_code"));
  var beforeReplay=taskRows(f,lease.taskId(),token.operationId());assertEquals(ObservationResult.STALE_TOKEN,operations.observe(token,observation,()->fail("Old terminal token cannot reapply effects")));assertEquals(beforeReplay,taskRows(f,lease.taskId(),token.operationId()));
 }
 Map<String,List<Map<String,Object>>> taskRows(Task13AttemptFixture f,String task,String operation){var result=new TreeMap<String,List<Map<String,Object>>>();result.put("task",f.j.queryForList("SELECT * FROM durable_task WHERE id=?",task));result.put("attempt",f.j.queryForList("SELECT * FROM task_attempt WHERE task_id=? ORDER BY attempt",task));result.put("execution",f.j.queryForList("SELECT * FROM external_execution WHERE task_id=? ORDER BY id",task));result.put("operation",f.j.queryForList("SELECT * FROM external_operation WHERE id=?",operation));return result;}
 Map<String,List<Map<String,Object>>> prior(Task13AttemptFixture f,List<String> meetings){var result=new TreeMap<String,List<Map<String,Object>>>();for(String id:meetings){result.put(id+"/meeting",f.j.queryForList("SELECT * FROM meeting WHERE id=?",id));for(String table:List.of("minutes_revision","element_set","generation_candidate","final_confirmation","publication"))result.put(id+"/"+table,f.j.queryForList("SELECT * FROM "+table+" WHERE meeting_id=? ORDER BY id",id));result.put(id+"/tasks",f.j.queryForList("SELECT * FROM durable_task WHERE subject_id=? ORDER BY id",id));result.put(id+"/attempts",f.j.queryForList("SELECT * FROM task_attempt WHERE task_id IN (SELECT id FROM durable_task WHERE subject_id=?) ORDER BY task_id,attempt",id));result.put(id+"/executions",f.j.queryForList("SELECT * FROM external_execution WHERE task_id IN (SELECT id FROM durable_task WHERE subject_id=?) ORDER BY id",id));}return result;}
}
