package ama.storage;

import ama.transcript.*;
import ama.model.*;
import ama.task.*;
import ama.contract.ExternalContracts.*;
import ama.contract.TaskContracts.*;
import java.net.URI;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** New owner04 pipelines only: ordinary rejection, genuinely fenced retention and disposal. */
class Task11B5RetainedDecisionsIT extends Task11B5CombinedIT {
 static final List<String> GRAPH=List.of("minutes_revision","element_set","minutes_element","action_item","action_suggestion","evidence_link","source_locator","sensitive_detection","sensitive_detection_source","background_note","background_note_source","result_review","result_review_evidence","generation_input");
 boolean retained,installed;String activeTask;volatile Map<String,Object> advancedRoot;
 final AtomicBoolean advanced=new AtomicBoolean();Map<String,List<Map<String,Object>>> previous;
 @Override javax.sql.DataSource createSource(){var ds=ama.publication.Task11B5Source04.open();var sql=new org.springframework.jdbc.core.JdbcTemplate(ds);sql.setQueryTimeout(5);previous=rows(sql);return ds;}
 @Override void install()throws Exception{if(!installed){super.install();installed=true;}}
 @Override Task11KnowledgeFixture knowledgeFixture()throws Exception {
  if(knowledge!=null)return knowledge;
  Path baseline=Path.of(System.getenv("AMA_TASK11_REPOSITORY_ROOT"),".superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-11-b5-retained-runtime-20261027/retained-decisions-01/pipeline-f7cfabb4-f2c2-4def-b107-9237b5a6ba58");
  var prior=json.readTree(Files.readAllBytes(baseline.resolve("knowledge-before-start.json")));
  assertFalse(json.readTree(Files.readAllBytes(baseline.resolve("knowledge-closed.json"))).path("alive").asBoolean());
  knowledge=Task11KnowledgeFixture.reopenOnly(receipt,owner,ROOT.resolve("knowledge"),prior.path("port").asInt(),json.convertValue(prior.path("identities"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){}));
  return knowledge; // No publication and no new KB actor grant are required.
 }
 @Override @Test void genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate()throws Exception {
  Path out=Path.of(System.getenv("AMA_TASK11_EVIDENCE_WINDOW"));assertEquals("retained-decisions-02",out.getFileName().toString());assertTrue(Files.isRegularFile(out.resolve("consumed.json")));
  try {
   super.genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate();
   String first=meeting;var firstFacts=rows(j);if(relay!=null){relay.close();relay=null;}
   Path firstReceipt=receipt;receipt=Files.createDirectory(out.resolve("pipeline-"+UUID.randomUUID()));
   for(String name:List.of("resource.json","controlled-template-archive-installation.json"))Files.copy(firstReceipt.resolve(name),receipt.resolve(name));
   Files.writeString(receipt.resolve("shared-owned-resource-context.json"),json.writeValueAsString(Map.of("firstPipeline",firstReceipt.toString(),"sameNativeProcess",nativeFixture.process.pid(),"sameKnowledgeProcess",knowledge.pid(),"secondMeetingOnly",true)),StandardOpenOption.CREATE_NEW);
   retained=true;newMeeting();super.genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate();
   preserve(firstFacts);Files.writeString(receipt.resolve("retained-decisions-summary.json"),json.writeValueAsString(Map.of("ordinaryMeeting",first,"retainedMeeting",meeting,"ordinaryDecision","MINUTES_REJECT","retainedDecision","MINUTES_DISPOSE","qualification","two genuine private candidates and exact terminal receipt recovery; selected table and private graph preservation; no publication frozen RAW or full B5 qualification")),StandardOpenOption.CREATE_NEW);
  } finally {preserve(previous);Files.writeString(out.resolve("prior-business-preserved.json"),json.writeValueAsString(Map.of("tables",previous.keySet(),"oldRowsPreserved",true,"noOldRowRepair",true)),StandardOpenOption.CREATE_NEW);}
 }
 @Override String expectedTaskState(){return retained?"STALE":"SUCCEEDED";}
 @Override String expectedCandidateState(){return retained?"STALE":"PENDING";}
 @Override Map<String,Object> expectedRoot(Map<String,Object> before){if(!retained)return before;assertTrue(advanced.get());return Objects.requireNonNull(advancedRoot);}
 @Override void beforeWorker(String task){activeTask=task;super.beforeWorker(task);}
 @Override ControlledMinutesClient controlledClient(int port){
  try {relay=new Task09ControlledRelay(port,this::advanceAfterRemoteReceipt);return new ControlledMinutesClient(new ControlledMinutesHttpTransport(URI.create("http://127.0.0.1:"+relay.port()),"task09-owned-provider-test-authorization-value","CONTROLLED_INSTALLATION",Duration.ofSeconds(2),Duration.ofSeconds(30),Duration.ofSeconds(5)));}
  catch(Exception failure){throw new IllegalStateException(failure);}
 }
 void advanceAfterRemoteReceipt(){
  if(!retained)return;assertTrue(advanced.compareAndSet(false,true),"Exactly one generation POST is authorized");
  try {
   var marker=j.queryForMap("SELECT t.state task_state,e.state execution_state,o.state operation_state,e.id execution_id FROM durable_task t JOIN external_execution e ON e.task_id=t.id JOIN external_operation o ON o.id=e.operation_id WHERE t.id=?",activeTask);
   assertEquals("RECONCILING",marker.get("task_state"));assertEquals("MARKED",marker.get("execution_state"));assertEquals("MARKED",marker.get("operation_state"));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM generation_candidate WHERE task_id=?",Integer.class,activeTask));assertEquals(4,revision());
   String working=j.queryForObject("SELECT transcript_revision_id FROM meeting WHERE id=?",String.class,meeting);
   var segments=j.queryForList("SELECT segment_id FROM transcript_segment WHERE revision_id=? ORDER BY ordinal",String.class,working).stream().map(id->Map.of("segmentId",id,"body","Actual later saved source","marks",List.of())).toList();
   var response=call("PUT","/meetings/"+meeting+"/transcript",json.writeValueAsString(Map.of("expectedRevisionToken","4","sourceRevisionId",working,"segments",segments)),"retained-source-save");
   assertEquals(200,response.statusCode(),"Actual post-marker source SAVE");assertEquals(5,revision());advancedRoot=j.queryForMap("SELECT * FROM meeting WHERE id=?",meeting);
   Files.writeString(receipt.resolve("actual-business-advance.json"),json.writeValueAsString(Map.of("meeting",meeting,"task",activeTask,"execution",marker.get("execution_id"),"operation","HTTP TRANSCRIPT_SAVE","httpStatus",200,"beforeRevision",4,"afterRevision",5,"markerStates",marker,"afterProviderResponseBeforeWorkerDelivery",true)),StandardOpenOption.CREATE_NEW);
  }catch(Exception failure){throw new IllegalStateException("Post-marker actual source advance failed",failure);}
 }
 @Override void afterWorker(String task,MinutesExternalPreparation preparation,ControlledMinutesClient client,JdbcExternalOperations operations,JdbcModelPermitPool permits,Throwable failure)throws Exception {
  assertNull(failure);assertNull(relay.failure.get());assertEquals(1,relay.posts.get());assertEquals(1,relay.gets.get());
  assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM model_reservation WHERE execution_id IN (SELECT id FROM external_execution WHERE task_id=?) AND settled_at IS NOT NULL",Integer.class,task));
  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM model_reservation WHERE execution_id IN (SELECT id FROM external_execution WHERE task_id=?) AND settled_at IS NULL",Integer.class,task));
 }
 @Override void afterCandidate(Map<String,Object> candidate,JdbcMinutesFactsReader.Snapshot facts)throws Exception {
  candidateId=(String)candidate.get("id");inputId=(String)candidate.get("input_id");long expected=retained?5:4;
  var graph=graph();var task=j.queryForMap("SELECT * FROM durable_task WHERE id=?",candidate.get("task_id"));
  var root=j.queryForMap("SELECT minutes_revision_id,element_set_id,needs_generation,generation_fingerprint,cancel_epoch FROM meeting WHERE id=?",meeting);
  if(retained)assertNull(task.get("output_id"));
  var beforeReads=rows(j);var view=getMinutes("/candidates/"+candidateId);assertEquals(expectedCandidateState(),view.at("/candidate/state").asText());assertEquals(!retained,view.at("/eligibility/canAdopt").asBoolean());assertTrue(view.path("provenanceVerified").asBoolean());assertTrue(getMinutes("/generation-inputs/"+inputId).path("provenanceVerified").asBoolean());assertTrue(beforeReads.equals(rows(j)),"Historical GET changed selected tables");
  String route="/candidates/"+candidateId+(retained?"/disposals":"/rejections"),key=retained?"actual-dispose":"actual-reject";
  var request=Map.<String,Object>of("expectedRevisionToken",Long.toString(expected));var ack=postMinutes(route,request,key,200);assertEquals(candidateId,ack.path("resultId").asText());assertFalse(ack.path("replayed").asBoolean());assertEquals(expected+1,revision());
  var decided=j.queryForMap("SELECT * FROM generation_candidate WHERE id=?",candidateId);assertEquals("REJECTED",decided.get("state"));assertNotNull(decided.get("decided_at"));
  var immutable=new LinkedHashMap<>(decided);immutable.put("state",candidate.get("state"));immutable.put("decided_at",candidate.get("decided_at"));assertTrue(candidate.equals(immutable),"Decision changed immutable candidate fields");
  assertTrue(graph.equals(graph()),"Decision changed private content graph");assertTrue(task.equals(j.queryForMap("SELECT * FROM durable_task WHERE id=?",candidate.get("task_id"))),"Decision changed original task");assertTrue(root.equals(j.queryForMap("SELECT minutes_revision_id,element_set_id,needs_generation,generation_fingerprint,cancel_epoch FROM meeting WHERE id=?",meeting)),"Decision changed current content pointers or generation fence");
  var decidedRows=rows(j);var replay=postMinutes(route,request,key,200);assertTrue(replay.path("replayed").asBoolean());assertEquals(candidateId,replay.path("resultId").asText());assertTrue(decidedRows.equals(rows(j)),"Decision ACK replay changed selected tables");
  postMinutes(route,Map.of("expectedRevisionToken",Long.toString(revision())),key+"-new",409);assertTrue(decidedRows.equals(rows(j)),"New decision on closed candidate changed selected tables");
  var closed=getMinutes("/candidates/"+candidateId);assertEquals("REJECTED",closed.at("/candidate/state").asText());assertFalse(closed.at("/eligibility/canAdopt").asBoolean());assertTrue(closed.path("provenanceVerified").asBoolean());assertTrue(decidedRows.equals(rows(j)),"Closed candidate GET changed selected tables");
  Files.writeString(receipt.resolve("actual-candidate-decision.json"),json.writeValueAsString(Map.of("meeting",meeting,"task",candidate.get("task_id"),"candidate",candidateId,"priorState",candidate.get("state"),"decision",retained?"MINUTES_DISPOSE":"MINUTES_REJECT","revision",revision(),"http",true,"sameKeyReplay",true,"closedNewKeyDenied",true,"completePrivateGraphPreserved",GRAPH)),StandardOpenOption.CREATE_NEW);
 }
 @Override void afterExternal(Map<String,Object> candidate,MinutesExternalPreparation preparation,ControlledMinutesClient client,JdbcExternalOperations operations){try{verifyTerminalRecovery(candidate,preparation,client,operations);}catch(Exception failure){throw new IllegalStateException("Actual terminal recovery failed",failure);}}
 void verifyTerminalRecovery(Map<String,Object> candidate,MinutesExternalPreparation preparation,ControlledMinutesClient client,JdbcExternalOperations operations)throws Exception {
  String task=(String)candidate.get("task_id");var row=j.queryForMap("SELECT o.*,e.id execution_id FROM external_operation o JOIN external_execution e ON e.operation_id=o.id AND e.execution_no=o.current_execution_no WHERE e.task_id=?",task);
  var token=new OperationToken((String)row.get("id"),(String)row.get("execution_id"),((Number)row.get("current_execution_no")).longValue(),((Number)row.get("reconcile_epoch")).longValue(),(String)row.get("reconcile_owner"),((java.time.LocalDateTime)row.get("reconcile_expires_at")).toInstant(java.time.ZoneOffset.UTC),new SubjectRef("MEETING",meeting));
  assertNull(j.queryForObject("SELECT lease_owner FROM durable_task WHERE id=?",String.class,task));var before=rows(j);int posts=relay.posts.get(),gets=relay.gets.get();
  var request=preparation.prepareLookup(token);var wire=client.prepare(request.body(token.executionId()),request.envelope(),request.outputLimit());var remote=client.lookup(wire,NEVER);assertEquals(row.get("external_result_id"),remote.resultId());
  var observation=preparation.accept(request,token,wire,remote,NEVER);var observed=operations.recoverPreparedFact(token,observation);assertEquals(retained?ObservationResult.RECORDED_FENCED:ObservationResult.APPLIED,observed);
  assertEquals(posts,relay.posts.get());assertEquals(gets+2,relay.gets.get());assertNull(relay.failure.get());assertTrue(before.equals(rows(j)),"Original terminal receipt recovery changed selected tables or private graph");
  assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM generation_candidate WHERE task_id=?",Integer.class,task));assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM external_execution WHERE task_id=?",Integer.class,task));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM publication WHERE meeting_id=?",Integer.class,meeting));
  assertEquals(Map.of("getWriteAttempts",0L,"postWriteAttempts",0L),ama.api.Task11RuntimeProbe.publication(app).traffic());
  Files.writeString(receipt.resolve("actual-terminal-receipt-recovery.json"),json.writeValueAsString(Map.ofEntries(Map.entry("task",task),Map.entry("candidate",candidateId),Map.entry("operation",token.operationId()),Map.entry("execution",token.executionId()),Map.entry("result",remote.resultId()),Map.entry("outcome",observed.name()),Map.entry("postsBefore",posts),Map.entry("postsAfter",relay.posts.get()),Map.entry("getsBefore",gets),Map.entry("getsAfter",relay.gets.get()),Map.entry("unchangedTables",before.keySet()),Map.entry("noSecondGraphOrEffect",true),Map.entry("reservationSettledBeforeRecovery",true))),StandardOpenOption.CREATE_NEW);
 }
 static Map<String,List<Map<String,Object>>> rows(org.springframework.jdbc.core.JdbcTemplate sql){var result=Task11B5UpstreamMatrixIT.rows(sql);for(String table:GRAPH)if(!result.containsKey(table)){var values=sql.queryForList("SELECT * FROM "+table);assertTrue(values.size()<=8192);values.sort(Comparator.comparing(Object::toString));result.put(table,values);}return result;}
 Map<String,List<Map<String,Object>>> graph(){var result=rows(j);result.keySet().retainAll(GRAPH);return result;}
 void preserve(Map<String,List<Map<String,Object>>> baseline){var after=rows(j);baseline.forEach((table,values)->assertTrue(after.get(table).containsAll(values),"Previously committed rows changed: "+table));}
}
