package ama.storage;

import ama.transcript.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Two genuine meetings: valid ordinary edits, then the same families at READY and permanent SUCCESS. */
class Task11B5SavedMatrix04IT extends Task11B5CombinedIT {
 boolean ordinary=true,installed;String ordinaryMeeting;Map<String,List<Map<String,Object>>> ordinaryFacts;
 @Override javax.sql.DataSource createSource(){return ama.publication.Task11B5Source04.open();}
 @Override void install()throws Exception{if(!installed){super.install();installed=true;}}
 @Override @Test void genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate()throws Exception {
  Path out=Path.of(System.getenv("AMA_TASK11_EVIDENCE_WINDOW"));assertEquals("saved-matrix-04",out.getFileName().toString());assertTrue(Files.isRegularFile(out.resolve("consumed.json")));
  super.genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate();
  ordinaryMeeting=meeting;ordinaryFacts=meetingFacts(ordinaryMeeting);assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM publication",Integer.class));
  if(relay!=null){relay.close();relay=null;}Path firstReceipt=receipt;receipt=Files.createDirectory(out.resolve("pipeline-"+UUID.randomUUID()));
  for(String name:List.of("resource.json","controlled-template-archive-installation.json"))Files.copy(firstReceipt.resolve(name),receipt.resolve(name));
  Files.writeString(receipt.resolve("shared-owned-resource-context.json"),json.writeValueAsString(Map.of("firstPipeline",firstReceipt.toString(),"sameNativeProcess",nativeFixture.process.pid(),"sameArchive",archive.toString(),"sameKnowledgeProcess",knowledge.pid(),"secondMeetingOnly",true)),StandardOpenOption.CREATE_NEW);
  ordinary=false;newMeeting();
  super.genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate();
  assertEquals(ordinaryFacts,meetingFacts(ordinaryMeeting));
  Files.writeString(receipt.resolve("b5-saved-matrix-summary.json"),json.writeValueAsString(Map.of("ordinaryMeeting",ordinaryMeeting,"publishedMeeting",meeting,"ordinaryMeetingPreserved",true,"familiesPerState",10,"states",List.of("ORDINARY","READY","PUBLISHED"),"qualification","actual HTTP saved-write and intent matrix only; not all mutators, RUNNING/RECONCILING, RAW/file/races/rollback or complete B5")),StandardOpenOption.CREATE_NEW);
 }
 @Override void afterWorker(String task,MinutesExternalPreparation preparation,ama.model.ControlledMinutesClient client,ama.task.JdbcExternalOperations operations,ama.task.JdbcModelPermitPool permits,Throwable failure)throws Exception {
  assertNull(failure);var reservation=j.queryForList("SELECT id,execution_id,settled_at FROM model_reservation WHERE execution_id IN (SELECT id FROM external_execution WHERE task_id=?) ORDER BY id",task);assertEquals(1,reservation.size());assertNotNull(reservation.getFirst().get("settled_at"));Files.writeString(receipt.resolve("generation-reservation-after-worker-close.json"),json.writeValueAsString(Map.of("task",task,"settled",true,"count",1)),StandardOpenOption.CREATE_NEW);
 }
 @Override void afterCandidate(Map<String,Object> candidate,JdbcMinutesFactsReader.Snapshot facts)throws Exception {
  if(!ordinary){super.afterCandidate(candidate,facts);return;}
  candidateId=(String)candidate.get("id");inputId=(String)candidate.get("input_id");assertEquals("PENDING",candidate.get("state"));
  postMinutes("/candidates/"+candidateId+"/adoptions",Map.of("expectedRevisionToken","4","inputId",inputId,"inputFingerprint",candidate.get("input_fingerprint")),"actual-adopt",200);
  assertEquals(5L,revision());matrix("ORDINARY",false);
 }
 @Override void runPublication(Map<String,Object> accepted)throws Exception {
  String task=(String)accepted.get("taskId");assertEquals("READY",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM external_execution WHERE task_id=?",Integer.class,task));
  matrix("READY",true);super.runPublication(accepted);
 }
 @Override void publishedReadbacks(Map<String,Object> candidate)throws Exception{super.publishedReadbacks(candidate);assertEquals(1,j.queryForObject("SELECT ever_published FROM meeting WHERE id=?",Integer.class,meeting));matrix("PUBLISHED",true);}
 void matrix(String state,boolean denied)throws Exception {
  var steps=new ArrayList<Map<String,Object>>();String originalBody=current("minutes_revision_id"),originalSet=current("element_set_id");long initial=revision();
  var before=business();var intent=call("POST","/meetings/"+meeting+"/minutes/editor-intents",json.writeValueAsString(request(Map.of())),state+"-intent");assertEquals(denied?503:200,intent.statusCode(),intent.body());if(denied){assertEquals("MINUTES_UNAVAILABLE",body(intent).at("/error/code").asText());assertEquals(before,business());}else assertEquals(initial,revision());steps.add(Map.of("operation","intent","status",intent.statusCode(),"before",initial,"after",revision()));
  command(state,"body","PUT","/body",Map.of("sourceRevisionId",originalBody,"title","Matrix title","body","Changed body","evidenceAnchors",anchors("minutes_revision_id",originalBody)),denied,steps);
  String set=current("element_set_id"),element=action(set);command(state,"element","PUT","/elements/"+element,Map.of("sourceSetId",set,"body","Changed action body","evidenceAnchors",anchors("element_id",element)),denied,steps);
  set=current("element_set_id");element=action(set);var fields=new LinkedHashMap<String,Object>();fields.put("sourceSetId",set);fields.put("ownerEnterpriseId",null);fields.put("ownerDisplay","Explicit owner 😀");fields.put("dueOn","2027-01-31");command(state,"action","PUT","/elements/"+element+"/action-fields",fields,denied,steps);
  set=current("element_set_id");element=action(set);command(state,"question","POST","/elements/"+element+"/open-question",Map.of("sourceSetId",set,"body","Changed action body","evidenceAnchors",anchors("element_id",element)),denied,steps);
  command(state,"set-restore","POST","/element-set-restorations",Map.of("sourceSetId",originalSet),denied,steps);
  command(state,"body-restore","POST","/body-restorations",Map.of("sourceRevisionId",originalBody),denied,steps);
  element=action(current("element_set_id"));command(state,"review","POST","/reviews",Map.of("target",Map.of("kind","ELEMENT","id",element),"reviewKind","SOURCE_SUPPORT","scope","WHOLE","outcome","SUPPORTED"),denied,steps);
  command(state,"template","PUT","/settings/template",Map.of("templateRevisionId",revision),denied,steps);
  command(state,"exclusions","PUT","/settings/exclusions",Map.of("sourceExclusionRevisionId",current("exclusion_revision_id"),"items",List.of()),denied,steps);
  assertEquals(initial+(denied?0:9),revision());
  Files.writeString(receipt.resolve("b5-saved-matrix-"+state+".json"),json.writeValueAsString(Map.of("meeting",meeting,"state",state,"steps",steps,"businessUnchangedForEveryDeniedRequest",denied,"before",initial,"after",revision(),"positiveCounterpart","same source-aware request families on separate ordinary genuine meeting")),StandardOpenOption.CREATE_NEW);
 }
 void command(String state,String name,String method,String route,Map<String,Object> fields,boolean denied,List<Map<String,Object>> steps)throws Exception {
  var payload=request(fields);var before=business();long version=revision();var response=call(method,"/meetings/"+meeting+"/minutes"+route,json.writeValueAsString(payload),state+"-"+name);assertEquals(denied?503:200,response.statusCode(),route+" "+response.body());
  if(denied){assertEquals("MINUTES_UNAVAILABLE",body(response).at("/error/code").asText());assertEquals(before,business(),state+" "+route);}else assertEquals(version+1,revision());
  steps.add(Map.of("operation",name,"status",response.statusCode(),"before",version,"after",revision(),"request",payload,"response",body(response)));
 }
 Map<String,Object> request(Map<String,?> fields){var result=new LinkedHashMap<String,Object>();result.put("expectedRevisionToken",Long.toString(revision()));result.putAll(fields);return result;}
 String current(String column){assertTrue(Set.of("minutes_revision_id","element_set_id","exclusion_revision_id").contains(column));return j.queryForObject("SELECT "+column+" FROM meeting WHERE id=?",String.class,meeting);}
 String action(String set){return j.queryForObject("SELECT id FROM minutes_element WHERE set_id=? AND category='ACTION' ORDER BY ordinal LIMIT 1",String.class,set);}
 List<Map<String,Object>> anchors(String column,String id){assertTrue(Set.of("minutes_revision_id","element_id").contains(column));return j.queryForList("SELECT id FROM evidence_link WHERE "+column+"=? ORDER BY id",String.class,id).stream().map(e->Map.<String,Object>of("evidenceId",e,"startToken","0","endToken","7")).toList();}
 Map<String,List<Map<String,Object>>> business(){var result=new TreeMap<String,List<Map<String,Object>>>();for(String table:List.of("meeting","minutes_revision","element_set","minutes_element","action_item","action_suggestion","evidence_link","result_review","result_review_evidence","generation_candidate","meeting_minutes_editor_guard","exclusion_revision","sensitive_exclusion","final_confirmation","final_result_review","publication","publication_submission","publication_attempt","external_document_mapping","durable_task","task_attempt","external_operation","external_execution","model_reservation","idempotency_record","task_outbox","task_outbox_sequence","task_subject","meeting_search_projection","audit_event")){var rows=j.queryForList("SELECT * FROM "+table);assertTrue(rows.size()<=2048);rows.sort(Comparator.comparing(Object::toString));result.put(table,rows);}return result;}
 Map<String,List<Map<String,Object>>> meetingFacts(String id){var result=new TreeMap<String,List<Map<String,Object>>>();result.put("meeting",j.queryForList("SELECT * FROM meeting WHERE id=?",id));for(String table:List.of("minutes_revision","element_set","generation_candidate","result_review","exclusion_revision"))result.put(table,j.queryForList("SELECT * FROM "+table+" WHERE meeting_id=? ORDER BY id",id));return result;}
}
