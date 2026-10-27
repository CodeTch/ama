package ama.storage;

import ama.transcript.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Fresh real HTTP/DB/model/native pipeline. Never selected by the offline *Test gate. */
class Task11B5CombinedIT extends Task11FinalPublicationIT {
 String inputId,candidateId;
 @Override Task11KnowledgeFixture knowledgeFixture()throws Exception{return knowledge==null?super.knowledgeFixture():knowledge;}
 @Override javax.sql.DataSource createSource(){return ama.publication.Task11B5Source.open();}
 @Override boolean installedTranscript(){return true;}
 @Override void beforeApplication(){
  bounds.put("transcript.candidates.installation","AUTHORITY_CONTROLLED");
  bounds.put("transcript.publication.installation","AUTHORITY_CONTROLLED");
  bounds.put("transcript.transitions.installation-version","2");
  bounds.put("management.endpoint.health.group.ready.include","ping");
  bounds.put("processing.bounds.archive-total-bytes",2097152L);
 }
 @Override void configurePublicationRuntime(Map<String,Object> configured){
  configured.put("transcript.candidates.installation","AUTHORITY_CONTROLLED");configured.put("transcript.publication.installation","AUTHORITY_CONTROLLED");configured.put("transcript.transitions.installation-version","2");
 }
 @Override Map<String,Object> initialize(Object unused,String key,long expected)throws Exception {
  knowledge=knowledgeFixture();restartForFinal();
  var request=Map.<String,Object>of("expectedRevisionToken",Long.toString(expected),"templateRevisionId",revision);
  var response=postMinutes("/initial-settings",request,key,200);
  assertEquals(expected+1,revision());var before=businessState();postMinutes("/initial-settings",request,key,200);assertEquals(before,businessState());
  var current=getMinutes("/generation-inputs/current");assertTrue(current.path("eligible").asBoolean(),current.toString());assertFalse(current.toString().contains("MINUTES_COMBINED_NOT_INSTALLED"));
  assertEquals(before,businessState(),"Eligibility GET cannot create a command or mutate business state");
  return json.convertValue(response,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
 }
 @Override Map<String,Object> enqueue(JdbcMinutesGenerationCommands unused)throws Exception {
  var response=postMinutes("/generations",Map.of("expectedRevisionToken","4","scope","FULL"),"generate",202);
  inputId=response.path("inputId").asText();if(inputId.isEmpty())inputId=response.at("/result/inputId").asText();assertFalse(inputId.isEmpty());
  return json.convertValue(response,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
 }
 @Override void beforeWorker(String task) {
  try {
   var before=businessState();var request=Map.<String,Object>of("expectedRevisionToken","4");
   var first=postMinutes("/editor-intents",request,"sticky-editor",200);assertEquals("1",first.at("/current/intentEpochToken").asText());
   var after=businessState();assertEquals(before.get("meeting"),after.get("meeting"));assertEquals(before.get("tasks"),after.get("tasks"));
   var repeat=postMinutes("/editor-intents",request,"sticky-editor",200);assertEquals("1",repeat.at("/current/intentEpochToken").asText());assertEquals(after,businessState());
   var stored=getMinutes("/generation-inputs/"+inputId);assertTrue(stored.path("provenanceVerified").asBoolean());assertFalse(stored.toString().contains("MINUTES_COMBINED_NOT_INSTALLED"));assertEquals(after,businessState());
   Files.writeString(receipt.resolve("b5-initial-intent-generation-http.json"),json.writeValueAsString(Map.of("meeting",meeting,"task",task,"input",inputId,"initialHttp",true,"generationHttp",true,"intentHttp",true,"exactReplayNoSecondEpoch",true,"comparedBusinessTablesUnchanged",true)));
   super.beforeWorker(task); // Exact direct intent replay only; retains original pre-POST custody.
  } catch(Exception failure){throw new IllegalStateException(failure);}
 }
 @Override void afterCandidate(Map<String,Object> candidate,JdbcMinutesFactsReader.Snapshot facts)throws Exception {
  candidateId=(String)candidate.get("id");inputId=(String)candidate.get("input_id");
  var before=businessState();var stored=getMinutes("/generation-inputs/"+inputId);var projected=getMinutes("/candidates/"+candidateId);var current=getMinutes("/generation-inputs/current");
  assertTrue(projected.at("/eligibility/canAdopt").asBoolean(),projected.toString());assertEquals("PENDING",projected.at("/candidate/state").asText());assertTrue(stored.path("provenanceVerified").asBoolean());assertFalse(current.toString().contains("MINUTES_COMBINED_NOT_INSTALLED"));assertEquals(before,businessState());
  postMinutes("/candidates/"+candidateId+"/adoptions",Map.of("expectedRevisionToken","4","inputId",inputId,"inputFingerprint",candidate.get("input_fingerprint")),"actual-adopt",200);
  assertEquals(5,revision());assertFalse(getMinutes("/candidates/"+candidateId).at("/eligibility/canAdopt").asBoolean());
  // Inherited adoption is accepted replay; info/roster/reviews use actual services, final/publication use HTTP.
  super.afterCandidate(candidate,facts);
  publishedReadbacks(candidate);
 }
 com.fasterxml.jackson.databind.JsonNode getMinutes(String route)throws Exception {
  var response=call("GET","/meetings/"+meeting+"/minutes"+route,"",null);assertEquals(200,response.statusCode(),route+" "+response.body());assertEquals("private, no-store",response.headers().firstValue("Cache-Control").orElse(""));return body(response);
 }
 com.fasterxml.jackson.databind.JsonNode postMinutes(String route,Map<String,Object> request,String key,int status)throws Exception {
  var response=call("POST","/meetings/"+meeting+"/minutes"+route,json.writeValueAsString(request),key);assertEquals(status,response.statusCode(),route+" "+response.body());return body(response);
 }
 Map<String,Object> businessState(){
  var result=new TreeMap<String,Object>();result.put("meeting",j.queryForMap("SELECT * FROM meeting WHERE id=?",meeting));
  result.put("tasks",j.queryForList("SELECT * FROM durable_task WHERE subject_kind='MEETING' AND subject_id=? ORDER BY id",meeting));
  result.put("intent",j.queryForList("SELECT * FROM meeting_minutes_editor_guard WHERE meeting_id=?",meeting));
  result.put("commands",j.queryForList("SELECT * FROM idempotency_record WHERE resource_id=? ORDER BY scope_hash",meeting));
  for(String table:List.of("minutes_revision","element_set","generation_candidate","result_review","final_confirmation","publication"))result.put(table,j.queryForList("SELECT * FROM "+table+" WHERE meeting_id=? ORDER BY id",meeting));
  return result;
 }
 void publishedReadbacks(Map<String,Object> candidate)throws Exception {
  assertEquals(1,j.queryForObject("SELECT ever_published FROM meeting WHERE id=?",Integer.class,meeting));
  var before=businessState();
  var current=getMinutes("/generation-inputs/current");var input=getMinutes("/generation-inputs/"+inputId);var projected=getMinutes("/candidates/"+candidateId);
  assertFalse(current.path("eligible").asBoolean());assertFalse(input.at("/currentReadiness/eligible").asBoolean());assertFalse(projected.at("/eligibility/canAdopt").asBoolean());assertTrue(input.path("provenanceVerified").asBoolean());assertTrue(projected.path("provenanceVerified").asBoolean());assertEquals(before,businessState());
  postMinutes("/candidates/"+candidateId+"/adoptions",Map.of("expectedRevisionToken","4","inputId",inputId,"inputFingerprint",candidate.get("input_fingerprint")),"actual-adopt",200);assertEquals(before,businessState());
  String ownerToken=token;token=identity("b5-outsider-"+UUID.randomUUID());try {for(String route:List.of("/generation-inputs/current","/generation-inputs/"+inputId,"/candidates/"+candidateId)){var response=call("GET","/meetings/"+meeting+"/minutes"+route,"",null);assertEquals(403,response.statusCode(),response.body());}}finally{token=ownerToken;}
  Files.writeString(receipt.resolve("b5-published-readback-http.json"),json.writeValueAsString(Map.of("threeGetsComparedBusinessTablesUnchanged",true,"originalAdoptAcknowledgmentAvailable",true,"outsiderThreeGetsDenied",true,"qualification","newly owned positive pipeline and published readbacks only; no mutator-freeze, full-HTTP info/roster/review, race or closed-history qualification")));
 }
}
