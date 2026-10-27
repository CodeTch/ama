package ama.storage;

import ama.transcript.*;
import java.nio.file.*;
import java.util.*;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Genuine new meetings on owner04; inherited model/native/HTTP pipeline, explicit limited route matrix. */
class Task11B5UpstreamMatrixIT extends Task11B5CombinedIT {
 static final List<String> TABLES=List.of("meeting","meeting_info_revision","roster_revision","roster_member","meeting_access_grant","meeting_search_projection","meeting_person_search","input_batch","input_batch_file","transcript_revision","transcript_segment","transcript_segment_mark","transcript_confirmation","file_object","file_reference","upload_session","upload_part","file_cleanup","minutes_template","template_revision","minutes_revision","element_set","minutes_element","action_item","action_suggestion","evidence_link","result_review","result_review_evidence","generation_candidate","meeting_minutes_editor_guard","exclusion_revision","sensitive_exclusion","final_confirmation","final_result_review","publication","publication_submission","publication_attempt","external_document_mapping","durable_task","task_attempt","external_operation","external_execution","model_reservation","idempotency_record","task_outbox","task_subject","audit_event");
 static final String CONTENT="Upstream matrix source 😀";
 boolean ordinary=true,installed;String partProbe,completeProbe,intakeFile;Map<String,List<Map<String,Object>>> previous;
 @Override javax.sql.DataSource createSource(){var ds=ama.publication.Task11B5Source04.open();var sql=new org.springframework.jdbc.core.JdbcTemplate(ds);sql.setQueryTimeout(5);previous=rows(sql);return ds;}
 @Override void install()throws Exception{if(!installed){super.install();installed=true;}}
 @Override Task11KnowledgeFixture knowledgeFixture()throws Exception {
  if(knowledge!=null)return knowledge;
  Path baseline=Path.of(System.getenv("AMA_TASK11_REPOSITORY_ROOT"),".superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-11-b5-runtime-evidence-20261027-04/saved-matrix-04/pipeline-5911bdf0-b557-4946-8ca4-05d4bf7f3a8b");
  var prior=json.readTree(Files.readAllBytes(baseline.resolve("knowledge-before-start.json")));
  knowledge=Task11KnowledgeFixture.reopenOnly(receipt,owner,ROOT.resolve("knowledge"),prior.path("port").asInt(),json.convertValue(prior.path("identities"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){}));
  knowledge.grant(owner);return knowledge;
 }
 @Override @Test void genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate()throws Exception {
  Path out=Path.of(System.getenv("AMA_TASK11_EVIDENCE_WINDOW"));assertEquals("upstream-matrix-01",out.getFileName().toString());assertTrue(Files.isRegularFile(out.resolve("consumed.json")));
  try {
   super.genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate();
   String first=meeting;var firstFacts=rows(j);if(relay!=null){relay.close();relay=null;}
   Path firstReceipt=receipt;receipt=Files.createDirectory(out.resolve("pipeline-"+UUID.randomUUID()));
   for(String name:List.of("resource.json","controlled-template-archive-installation.json"))Files.copy(firstReceipt.resolve(name),receipt.resolve(name));
   Files.writeString(receipt.resolve("shared-owned-resource-context.json"),json.writeValueAsString(Map.of("firstPipeline",firstReceipt.toString(),"sameNativeProcess",nativeFixture.process.pid(),"sameKnowledgeProcess",knowledge.pid(),"secondMeetingOnly",true)),StandardOpenOption.CREATE_NEW);
   ordinary=false;newMeeting();super.genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate();
   preserve(firstFacts);Files.writeString(receipt.resolve("upstream-matrix-summary.json"),json.writeValueAsString(Map.of("ordinaryMeeting",first,"publishedMeeting",meeting,"states",List.of("ORDINARY","READY","PUBLISHED"),"qualification","specified actual upstream HTTP route outcomes and selected table preservation; roster repeat may fail existing precondition, not all guard causal paths; no full B5/browser/remote protocol qualification")),StandardOpenOption.CREATE_NEW);
  } finally {preserve(previous);Files.writeString(out.resolve("prior-business-preserved.json"),json.writeValueAsString(Map.of("tables",TABLES,"oldRowsPreserved",true,"noOldRowRepair",true)),StandardOpenOption.CREATE_NEW);}
 }
 @Override void afterWorker(String task,MinutesExternalPreparation preparation,ama.model.ControlledMinutesClient client,ama.task.JdbcExternalOperations operations,ama.task.JdbcModelPermitPool permits,Throwable failure)throws Exception {
  assertNull(failure);assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM model_reservation WHERE execution_id IN (SELECT id FROM external_execution WHERE task_id=?) AND settled_at IS NOT NULL",Integer.class,task));
 }
 @Override void afterCandidate(Map<String,Object> candidate,JdbcMinutesFactsReader.Snapshot facts)throws Exception {
  partProbe=beginUpload("prepare-part");completeProbe=open(CONTENT);intakeFile=upload(CONTENT);
  if(!ordinary){super.afterCandidate(candidate,facts);return;}
  candidateId=(String)candidate.get("id");inputId=(String)candidate.get("input_id");
  postMinutes("/candidates/"+candidateId+"/adoptions",Map.of("expectedRevisionToken","4","inputId",inputId,"inputFingerprint",candidate.get("input_fingerprint")),"actual-adopt",200);
  matrix("ORDINARY",false);
 }
 @Override void runPublication(Map<String,Object> accepted)throws Exception {
  String task=(String)accepted.get("taskId");assertEquals("READY",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM external_execution WHERE task_id=?",Integer.class,task));matrix("READY",true);super.runPublication(accepted);
 }
 @Override void publishedReadbacks(Map<String,Object> candidate)throws Exception{super.publishedReadbacks(candidate);matrix("PUBLISHED",true);}
 String beginUpload(String key)throws Exception {var response=call("POST","/meetings/"+meeting+"/uploads",json.writeValueAsString(uploadRequest()),key+UUID.randomUUID());assertEquals(201,response.statusCode(),response.body());return body(response).path("uploadId").asText();}
 Map<String,Object> uploadRequest()throws Exception{return Map.of("purpose","TRANSCRIPT_INPUT","name","matrix.txt","size",CONTENT.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,"sha256",hash(CONTENT));}
 @FunctionalInterface interface Request {HttpResponse<String> send()throws Exception;}
 void check(String state,String name,boolean denied,int positive,int frozen,String code,Request request,List<Map<String,Object>> steps)throws Exception {
  var before=rows(j);long version=revision();var response=request.send();assertEquals(denied?frozen:positive,response.statusCode(),state+" "+name+" "+response.body());
  if(denied){assertEquals(code,body(response).at("/error/code").asText());assertTrue(before.equals(rows(j)),state+" "+name+" changed selected business tables");}
  steps.add(Map.of("operation",name,"status",response.statusCode(),"before",version,"after",revision()));
 }
 void matrix(String state,boolean denied)throws Exception {
  var steps=new ArrayList<Map<String,Object>>();
  check(state,"info",denied,200,409,"MEETING_CONFLICT",()->call("PUT","/meetings/"+meeting+"/info",json.writeValueAsString(Map.of("expectedRevisionToken",Long.toString(revision()),"name","Matrix info","rosterDraftIds",List.of(),"externalRosterNames",List.of("Matrix guest"))),state+"-info"),steps);
  check(state,"roster",denied,200,409,"MEETING_CONFLICT",()->call("POST","/meetings/"+meeting+"/roster-confirmations",json.writeValueAsString(Map.of("expectedRevisionToken",Long.toString(revision()))),state+"-roster"),steps);
  String original=j.queryForObject("SELECT transcript_revision_id FROM meeting WHERE id=?",String.class,meeting);
  for(String operation:List.of("save","marks","restore","confirm")){
   String current=j.queryForObject("SELECT transcript_revision_id FROM meeting WHERE id=?",String.class,meeting);var request=new LinkedHashMap<String,Object>();request.put("expectedRevisionToken",Long.toString(revision()));request.put(operation.equals("confirm")?"savedRevisionId":"sourceRevisionId",operation.equals("restore")?original:current);
   if(operation.equals("save")||operation.equals("marks")){var segments=new ArrayList<Map<String,Object>>();for(var row:j.queryForList("SELECT segment_id,body FROM transcript_segment WHERE revision_id=? ORDER BY ordinal",current)){var segment=new LinkedHashMap<String,Object>();segment.put("segmentId",row.get("segment_id"));segment.put("marks",j.queryForList("SELECT mark FROM transcript_segment_mark WHERE revision_id=? AND segment_id=? ORDER BY mark",String.class,current,row.get("segment_id")));if(operation.equals("save"))segment.put("body",row.get("body")+" edited");segments.add(segment);}request.put("segments",segments);}
   String route=switch(operation){case "save"->"";case "marks"->"/marks";case "restore"->"/restorations";default->"/confirmations";};
   check(state,"transcript-"+operation,denied,200,state.equals("PUBLISHED")?409:503,state.equals("PUBLISHED")?"MEETING_NOT_WRITABLE":"TRANSCRIPT_UNAVAILABLE",()->call(operation.equals("save")||operation.equals("marks")?"PUT":"POST","/meetings/"+meeting+"/transcript"+route,json.writeValueAsString(request),state+"-transcript-"+operation),steps);
  }
  check(state,"upload-begin",denied,201,409,"FILE_CONFLICT",()->call("POST","/meetings/"+meeting+"/uploads",json.writeValueAsString(uploadRequest()),state+"-begin"),steps);
  check(state,"upload-part",denied,204,409,"FILE_CONFLICT",()->part(partProbe,CONTENT),steps);
  check(state,"upload-complete",denied,200,409,"FILE_CONFLICT",()->complete(completeProbe,CONTENT),steps);
  check(state,"intake",denied,202,503,"PROCESSING_UNAVAILABLE",()->call("POST","/meetings/"+meeting+"/transcript-imports",json.writeValueAsString(Map.of("expectedRevisionToken",Long.toString(revision()),"orderedFileIds",List.of(intakeFile))),state+"-intake"),steps);
  if(!denied){String task=j.queryForObject("SELECT id FROM durable_task WHERE subject_id=? AND task_type='TRANSCRIPT_IMPORT_V1' AND state='READY'",String.class,meeting);var q=app.getBean(ama.task.JdbcTaskQueue.class);try(var loop=ama.worker.WorkerLoop.contextual(q,Map.of("TRANSCRIPT_IMPORT",1),Map.of("TRANSCRIPT_IMPORT_V1",(lease,cancel)->ama.task.TranscriptImportRuntime.prepare(q,lease,settings(),cancel)),"UPSTREAM_MATRIX")){loop.pollOnce("TRANSCRIPT_IMPORT");long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(20);while(System.nanoTime()<end&&List.of("READY","RUNNING").contains(j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task)))Thread.sleep(50);}assertEquals("SUCCEEDED",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task));}
  Files.writeString(receipt.resolve("upstream-matrix-"+state+".json"),json.writeValueAsString(Map.of("meeting",meeting,"state",state,"steps",steps,"deniedWritesPreservedSelectedTables",denied,"tables",TABLES)),StandardOpenOption.CREATE_NEW);
 }
 static Map<String,List<Map<String,Object>>> rows(org.springframework.jdbc.core.JdbcTemplate sql){var result=new TreeMap<String,List<Map<String,Object>>>();for(String table:TABLES){var rows=sql.queryForList("SELECT * FROM "+table);assertTrue(rows.size()<=8192);rows.sort(Comparator.comparing(Object::toString));result.put(table,rows);}return result;}
 void preserve(Map<String,List<Map<String,Object>>> baseline){var after=rows(j);baseline.forEach((table,rows)->assertTrue(after.get(table).containsAll(rows),"Previously committed rows changed: "+table));}
}
