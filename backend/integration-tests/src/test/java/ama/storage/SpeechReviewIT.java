package ama.storage;

import ama.contract.TranscriptContracts.*;
import ama.media.FfmpegNormalizer;
import ama.task.*;
import ama.transcript.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static ama.storage.ControlledSpeechImportIT.*;

/** Accepted original speech task, actual checkpoint suggestions, immutable human revisions. */
class SpeechReviewIT {
 @TempDir Path root;
 static final String ORIGINAL="本次核对原始文字没有增强标点",ENHANCED="本次核对，原始文字没有增强标点。";
 static final com.fasterxml.jackson.databind.ObjectMapper JSON=new com.fasterxml.jackson.databind.ObjectMapper();
 @BeforeAll static void schema(){ControlledSpeechImportIT.schema();assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version='057'",Integer.class));}
 void scenario(String module,String path,Map<String,Object> match,Object body)throws Exception {
  var value=Map.ofEntries(Map.entry("module",module),Map.entry("name","ama-owned-correction-"+id()),Map.entry("enabled",true),Map.entry("priority",1000),Map.entry("method","POST"),Map.entry("path",path),Map.entry("match",match),Map.entry("status_code",200),Map.entry("delay_ms",0),Map.entry("response_type","json"),Map.entry("response_body",body),Map.entry("response_headers",Map.of()));
  try(var client=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build()){
   var result=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:8792/admin/api/scenarios")).timeout(Duration.ofSeconds(3)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(value))).build(),HttpResponse.BodyHandlers.discarding());assertEquals(201,result.statusCode());
  }
 }
 ControlledSpeechImportIT.Fixture fixture()throws Exception {return fixture(1);}
 ControlledSpeechImportIT.Fixture fixture(int count)throws Exception {
  var harness=new ControlledSpeechImportIT();harness.root=root;var audios=new ArrayList<byte[]>();
  for(int n=0;n<count;n++){byte[] audio=wav(128000);byte[] marker=new byte[16];new java.security.SecureRandom().nextBytes(marker);System.arraycopy(marker,0,audio,1000,marker.length);audios.add(audio);
   byte[] normalized;try(var prepared=new FfmpegNormalizer(file->new ByteArrayInputStream(audio)).prepare(List.of(new ama.contract.MediaContracts.OrderedSource(id(),0)),()->false);var clip=prepared.openClip(prepared.sources().getFirst().fileId(),0,128000)){normalized=clip.readAllBytes();}
   scenario("asr","/inference/asr-nano/modelserving/asr",Map.of("binary_sha256",sha(normalized)),Map.of("text",ORIGINAL));
  }
  scenario("punctuation","/inference/asr-punc/modelserving/punc",Map.of("text_equals",ORIGINAL),Map.of("text",ENHANCED));
  var f=harness.new Fixture(audios);var lease=f.claim(f.queue);var carrier=TranscriptImportRuntime.prepare(f.queue,lease,f.settings,NEVER);assertTrue(f.queue.completeWithEffects(lease,carrier));return f;
 }
 JdbcSavedTranscripts service(ControlledSpeechImportIT.Fixture f){
  var installed=CorrectionInstallation.compose(new CorrectionInstallation.Declaration("CONTROLLED_SPEECH","1","speech-review-it"),List.of());
  var limits=new TranscriptAcquisitionBudget(16777216,16777216,16777216,134217728,67108864,33554432,128,256,256);
  return new JdbcSavedTranscripts(f.components.authority(),f.settings.archives(),limits,installed,f.fences,f.outbox,new ama.audit.JdbcAuditAdapter(source),new SavedTranscriptTransitions("AUTHORITY_CONTROLLED","AUTHORITY_CONTROLLED","2",List.of(),List.of()));
 }
 @SuppressWarnings("unchecked") Map<String,Object> selected(Map<String,Object> detail){return (Map<String,Object>)detail.get("selected");}
 @SuppressWarnings("unchecked") List<Map<String,Object>> segments(Map<String,Object> detail){return (List<Map<String,Object>>)selected(detail).get("segments");}
 long revision(ControlledSpeechImportIT.Fixture f){return j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,f.meeting);}
 String raw(ControlledSpeechImportIT.Fixture f){return j.queryForObject("SELECT output_id FROM durable_task WHERE id=?",String.class,f.task);}
 @SuppressWarnings("unchecked") String result(Map<String,Object> response,String field){return (String)((Map<String,Object>)response.get("result")).get(field);}
 String working(ControlledSpeechImportIT.Fixture f,JdbcSavedTranscripts service){var raw=raw(f);var detail=service.readOwner(f.actor,f.meeting,raw);return result(service.mutate(f.actor,f.meeting,id(),new Command(Operation.SAVE,revision(f),raw,segments(detail).stream().map(s->new Edit((String)s.get("segmentId"),(String)s.get("body"),List.of())).toList()),id()),"revisionId");}
 @Test void controlledSpeechReadinessUsesInstalledCapabilityAndActualPendingDecisions()throws Exception {
  try(var f=fixture()){
   var service=service(f);String working=working(f,service);
   var limits=new TranscriptAcquisitionBudget(16777216,16777216,16777216,134217728,67108864,33554432,128,256,256);
   String pin=f.settings.archives().get().indexSha256();
   var queries=new JdbcMinutesSavedQueries(f.components.authority(),f.settings.archives(),pin,limits,service);
   assertFalse(((List<?>)queries.readiness(f.actor,f.meeting).get("blockers")).contains("CORRECTION_UNAVAILABLE"));
   String pending=candidate(f,service,working);
   assertTrue(((List<?>)queries.readiness(f.actor,f.meeting).get("blockers")).contains("CORRECTIONS_PENDING"));
   service.mutate(f.actor,f.meeting,id(),new Command(Operation.CORRECTION,revision(f),working,List.of(),null,new Correction(pending,CorrectionDecision.REJECT)),id());
   var resolved=queries.readiness(f.actor,f.meeting);assertFalse(((List<?>)resolved.get("blockers")).contains("CORRECTIONS_PENDING"));
   assertFalse(((List<?>)resolved.get("blockers")).contains("CORRECTION_UNAVAILABLE"));
   var unavailable=new JdbcSavedTranscripts(f.components.authority(),f.settings.archives(),limits,CorrectionInstallation.compose(null,List.of()));
   var denied=new JdbcMinutesSavedQueries(f.components.authority(),f.settings.archives(),pin,limits,unavailable).readiness(f.actor,f.meeting);
   assertTrue(((List<?>)denied.get("blockers")).contains("CORRECTION_UNAVAILABLE"));assertFalse((Boolean)denied.get("businessReady"));
   var offline=new JdbcSavedTranscripts(f.components.authority(),f.settings.archives(),limits,CorrectionInstallation.compose(new CorrectionInstallation.Declaration("DISABLED_NOT_INSTALLED","1","offline-readiness-it"),List.of()));
   assertFalse(((List<?>)new JdbcMinutesSavedQueries(f.components.authority(),f.settings.archives(),pin,limits,offline).readiness(f.actor,f.meeting).get("blockers")).contains("CORRECTION_UNAVAILABLE"));
  }
 }
 @SuppressWarnings("unchecked") @Test void realProposalBlocksConfirmationUntilAnExplicitDecisionAndReplaysTheOriginal()throws Exception {
  try(var f=fixture()){
   var service=service(f);String working=working(f,service),key=id();long base=revision(f);
   var proposed=service.proposeCorrections(f.actor,f.meeting,key,base,working,id());var current=service.readCorrections(f.actor,f.meeting,working);var items=(List<Map<String,Object>>)current.get("items");assertEquals(1,items.size());var candidate=items.getFirst();assertEquals(ORIGINAL,candidate.get("source"));assertEquals(ENHANCED,candidate.get("replacement"));assertEquals("PENDING",candidate.get("status"));
   assertFalse((Boolean)((Map<String,Object>)service.readCurrent(f.actor,f.meeting).get("capabilities")).get("canConfirm"));
   assertEquals("CORRECTIONS_PENDING",assertThrows(Failure.class,()->service.mutate(f.actor,f.meeting,id(),new Command(Operation.CONFIRM,base,working,List.of()),id())).code());
   String decisionKey=id();var command=new Command(Operation.CORRECTION,base,working,List.of(),null,new Correction((String)candidate.get("id"),CorrectionDecision.ACCEPT));var accepted=service.mutate(f.actor,f.meeting,decisionKey,command,id());String edited=result(accepted,"revisionId");
   assertEquals(ENHANCED,segments(service.readCurrent(f.actor,f.meeting)).getFirst().get("body"));assertEquals(ORIGINAL,segments(service.readOwner(f.actor,f.meeting,raw(f))).getFirst().get("body"));assertEquals(0,((Number)service.readCorrections(f.actor,f.meeting,edited).get("pendingCount")).intValue());
   assertTrue((Boolean)service.mutate(f.actor,f.meeting,decisionKey,command,id()).get("replayed"));var original=service.proposeCorrections(f.actor,f.meeting,key,base,working,id());assertTrue((Boolean)original.get("replayed"));assertEquals(proposed.get("result"),original.get("result"));assertEquals(edited,selected(service.readCurrent(f.actor,f.meeting)).get("id"));
   service.mutate(f.actor,f.meeting,id(),new Command(Operation.CONFIRM,revision(f),edited,List.of()),id());assertThrows(Failure.class,()->service.proposeCorrections(f.actor,f.meeting,id(),revision(f),edited,id()));
  }
 }
 @SuppressWarnings("unchecked") String candidate(ControlledSpeechImportIT.Fixture f,JdbcSavedTranscripts service,String working){
  service.proposeCorrections(f.actor,f.meeting,id(),revision(f),working,id());return (String)((List<Map<String,Object>>)service.readCorrections(f.actor,f.meeting,working).get("items")).getFirst().get("id");
 }
 @Test void rejectKeepsTheHumanTextAndResolvesThePendingGate()throws Exception {
  try(var f=fixture()){
   var service=service(f);String working=working(f,service),candidate=candidate(f,service,working);var rejected=service.mutate(f.actor,f.meeting,id(),new Command(Operation.CORRECTION,revision(f),working,List.of(),null,new Correction(candidate,CorrectionDecision.REJECT)),id());String current=result(rejected,"revisionId");
   assertEquals(ORIGINAL,segments(service.readCurrent(f.actor,f.meeting)).getFirst().get("body"));assertEquals("REJECTED",j.queryForObject("SELECT state FROM speech_review_candidate WHERE id=?",String.class,candidate));assertEquals(0,((Number)service.readCorrections(f.actor,f.meeting,current).get("pendingCount")).intValue());
   assertDoesNotThrow(()->service.mutate(f.actor,f.meeting,id(),new Command(Operation.CONFIRM,revision(f),current,List.of()),id()));assertEquals(ORIGINAL,segments(service.readOwner(f.actor,f.meeting,raw(f))).getFirst().get("body"));
  }
 }
 @Test void immediateUndoCreatesANewVersionAndOriginalAcceptReplayCannotReattachIt()throws Exception {
  try(var f=fixture()){
   var service=service(f);String working=working(f,service),candidate=candidate(f,service,working),key=id();long base=revision(f);var command=new Command(Operation.CORRECTION,base,working,List.of(),null,new Correction(candidate,CorrectionDecision.ACCEPT));String accepted=result(service.mutate(f.actor,f.meeting,key,command,id()),"revisionId");long acceptedVersion=revision(f);
   String undone=result(service.mutate(f.actor,f.meeting,id(),new Command(Operation.UNDO,acceptedVersion,accepted,List.of()),id()),"revisionId");assertEquals("UNDONE",j.queryForObject("SELECT state FROM speech_review_candidate WHERE id=?",String.class,candidate));assertEquals(undone,j.queryForObject("SELECT undo_revision_id FROM speech_review_candidate WHERE id=?",String.class,candidate));assertEquals(ORIGINAL,segments(service.readCurrent(f.actor,f.meeting)).getFirst().get("body"));
   var replay=service.mutate(f.actor,f.meeting,key,command,id());assertTrue((Boolean)replay.get("replayed"));assertFalse((Boolean)replay.get("resultIsCurrent"));assertEquals(undone,selected(service.readCurrent(f.actor,f.meeting)).get("id"));
   assertEquals(409,assertThrows(Failure.class,()->service.mutate(f.actor,f.meeting,id(),new Command(Operation.UNDO,acceptedVersion,accepted,List.of()),id())).status());
  }
 }
 @Test void humanEditExpiresTheProposalAndAnOutsiderCannotReadOrCreateOne()throws Exception {
  try(var f=fixture()){
   var service=service(f);String working=working(f,service),candidate=candidate(f,service,working);var before=service.readCurrent(f.actor,f.meeting);String segment=(String)segments(before).getFirst().get("segmentId");String changed=result(service.mutate(f.actor,f.meeting,id(),new Command(Operation.SAVE,revision(f),working,List.of(new Edit(segment,"人工核对后的独立内容",List.of()))),id()),"revisionId");
   assertEquals("STALE",j.queryForObject("SELECT state FROM speech_review_candidate WHERE id=?",String.class,candidate));assertEquals(0,((Number)service.readCorrections(f.actor,f.meeting,changed).get("pendingCount")).intValue());assertEquals(409,assertThrows(Failure.class,()->service.mutate(f.actor,f.meeting,id(),new Command(Operation.CORRECTION,revision(f),changed,List.of(),null,new Correction(candidate,CorrectionDecision.ACCEPT)),id())).status());assertEquals("人工核对后的独立内容",segments(service.readCurrent(f.actor,f.meeting)).getFirst().get("body"));
   String outside="speech-outsider-"+id();j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",outside,outside);var actor=new ama.contract.IdentityContracts.Actor(outside,ama.contract.IdentityContracts.Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER));
   assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->service.readCorrections(actor,f.meeting,changed));assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->service.proposeCorrections(actor,f.meeting,id(),revision(f),changed,id()));
   assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE actor_id=? AND resource_id=?",Integer.class,outside,f.meeting));assertEquals(ORIGINAL,segments(service.readOwner(f.actor,f.meeting,raw(f))).getFirst().get("body"));
  }
 }
 @Test void meetingDeletionPurgesCandidateAndBatchContent()throws Exception {
  try(var f=fixture()){
   var service=service(f);String working=working(f,service);candidate(f,service,working);assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM speech_review_batch WHERE meeting_id=?",Integer.class,f.meeting));String segment=(String)segments(service.readCurrent(f.actor,f.meeting)).getFirst().get("segmentId");service.mutate(f.actor,f.meeting,id(),new Command(Operation.SPEAKERS,revision(f),working,List.of(),null,null,new SpeakerEdit(List.of(segment),"删除前人工标签",null)),id());assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM transcript_speaker_snapshot WHERE meeting_id=?",Integer.class,f.meeting));
   f.meetings.delete(f.actor,f.meeting,id(),new ama.contract.MeetingContracts.DeleteMeeting(revision(f)),"SPEECH_REVIEW_DELETE");
   try(var deletion=new ama.meeting.JdbcMeetingDeletion(source,f.tx,f.access,f.catalog)){
    String job=deletion.forMeeting(f.actor,f.meeting).deletionTaskId();for(int n=0;n<180&&!deletion.status(f.actor,job).state().equals("COMPLETE");n++)assertTrue(deletion.runOnce(job));assertEquals("COMPLETE",deletion.status(f.actor,job).state());
   }
   assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM speaker WHERE meeting_id=?",Integer.class,f.meeting));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM transcript_speaker_snapshot WHERE meeting_id=?",Integer.class,f.meeting));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM speech_review_candidate WHERE meeting_id=?",Integer.class,f.meeting));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM speech_review_batch WHERE meeting_id=?",Integer.class,f.meeting));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM speech_checkpoint WHERE input_id=?",Integer.class,f.input));assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->service.readCorrections(f.actor,f.meeting,working));
  }
 }
 @Test void acceptedProposalReceiptSurvivesUnavailableModelsButNewReadsRejectCorruptPayload()throws Exception {
  try(var f=fixture()){
   var service=service(f);String working=working(f,service),key=id();long base=revision(f);var accepted=service.proposeCorrections(f.actor,f.meeting,key,base,working,id());
   byte[] payload=j.queryForObject("SELECT payload FROM speech_checkpoint WHERE input_id=? AND stage='ENHANCEMENT'",byte[].class,f.input);
   try{
    j.update("UPDATE speech_checkpoint SET payload=? WHERE input_id=? AND stage='ENHANCEMENT'","{}".getBytes(java.nio.charset.StandardCharsets.UTF_8),f.input);
    var replay=service.proposeCorrections(f.actor,f.meeting,key,base,working,id());assertEquals(accepted.get("result"),replay.get("result"));assertEquals(true,replay.get("replayed"));
    assertThrows(RuntimeException.class,()->service.readCorrections(f.actor,f.meeting,working));
    assertThrows(RuntimeException.class,()->service.proposeCorrections(f.actor,f.meeting,id(),base,working,id()));
    assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM speech_review_batch WHERE meeting_id=?",Integer.class,f.meeting));
   }finally{j.update("UPDATE speech_checkpoint SET payload=? WHERE input_id=? AND stage='ENHANCEMENT'",payload,f.input);}
  }
 }
 @Test void aRejectedProposalCannotBeResurrectedByANewKeyAndGovernanceBlocksNewProposals()throws Exception {
  try(var f=fixture()){
   var service=service(f);String working=working(f,service),candidate=candidate(f,service,working);String current=result(service.mutate(f.actor,f.meeting,id(),new Command(Operation.CORRECTION,revision(f),working,List.of(),null,new Correction(candidate,CorrectionDecision.REJECT)),id()),"revisionId");
   var repeated=service.proposeCorrections(f.actor,f.meeting,id(),revision(f),current,id());assertEquals(0,((Number)((Map<?,?>)repeated.get("result")).get("createdCount")).intValue());assertEquals("REJECTED",j.queryForObject("SELECT state FROM speech_review_candidate WHERE id=?",String.class,candidate));
   j.update("UPDATE meeting SET governance_state='WITHDRAWN' WHERE id=?",f.meeting);
   assertThrows(RuntimeException.class,()->service.proposeCorrections(f.actor,f.meeting,id(),revision(f),current,id()));assertEquals(2,j.queryForObject("SELECT COUNT(*) FROM speech_review_batch WHERE meeting_id=?",Integer.class,f.meeting));
  }
 }
 @Test void actualControllerRequiresStrictProposalAndDecisionPathsAndReturnsOriginalReceipts()throws Exception {
  try(var f=fixture()){
   var service=service(f);String working=working(f,service),key=id();long base=revision(f);var mvc=ama.api.SpeechReviewHttpProbe.create(service,source,f.tx,f.access,f.components.registry());
   var session=new ama.contract.IdentityContracts.ResolvedSession(f.actor,"T".repeat(43),java.time.Instant.now().plusSeconds(60));String attribute=ama.api.SpeechReviewHttpProbe.sessionAttribute(),path="/api/v1/meetings/"+f.meeting+"/transcript";
   var request=Map.of("expectedRevisionToken",Long.toString(base),"sourceRevisionId",working);
   var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path+"/correction-proposals").requestAttr(attribute,session).header("Idempotency-Key",key).contentType("application/json").content(JSON.writeValueAsBytes(request))).andReturn().getResponse();assertEquals(200,response.getStatus(),response.getContentAsString());
   var read=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path+"/corrections").param("sourceRevisionId",working).requestAttr(attribute,session)).andReturn().getResponse();assertEquals(200,read.getStatus(),read.getContentAsString());String candidate=JSON.readTree(read.getContentAsByteArray()).path("items").get(0).path("id").asText();
   var body=Map.of("expectedRevisionToken",Long.toString(base),"sourceRevisionId",working,"candidateId",candidate,"decision","ACCEPT");String decisionKey=id();
   var mismatch=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path+"/corrections/"+id()+"/decisions").requestAttr(attribute,session).header("Idempotency-Key",decisionKey).contentType("application/json").content(JSON.writeValueAsBytes(body))).andReturn().getResponse();assertEquals(400,mismatch.getStatus());
   var accepted=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path+"/corrections/"+candidate+"/decisions").requestAttr(attribute,session).header("Idempotency-Key",decisionKey).contentType("application/json").content(JSON.writeValueAsBytes(body))).andReturn().getResponse();assertEquals(200,accepted.getStatus(),accepted.getContentAsString());assertEquals(ENHANCED,segments(service.readCurrent(f.actor,f.meeting)).getFirst().get("body"));
   var replay=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path+"/correction-proposals").requestAttr(attribute,session).header("Idempotency-Key",key).contentType("application/json").content(JSON.writeValueAsBytes(request))).andReturn().getResponse();assertEquals(200,replay.getStatus());assertTrue(JSON.readTree(replay.getContentAsByteArray()).path("replayed").asBoolean());
   var unavailable=ama.api.SpeechReviewHttpProbe.create(null,source,f.tx,f.access,f.components.registry());
   var recovered=unavailable.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path+"/correction-proposals").requestAttr(attribute,session).header("Idempotency-Key",key).contentType("application/json").content(JSON.writeValueAsBytes(request))).andReturn().getResponse();assertEquals(200,recovered.getStatus());assertTrue(JSON.readTree(recovered.getContentAsByteArray()).path("replayed").asBoolean());
   var decisionReplay=unavailable.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path+"/corrections/"+candidate+"/decisions").requestAttr(attribute,session).header("Idempotency-Key",decisionKey).contentType("application/json").content(JSON.writeValueAsBytes(body))).andReturn().getResponse();assertEquals(200,decisionReplay.getStatus());assertTrue(JSON.readTree(decisionReplay.getContentAsByteArray()).path("replayed").asBoolean());
  }
 }
 @SuppressWarnings("unchecked") @Test void humanSpeakerRevisionsPreserveRawHistoryPermissionsAndUndo()throws Exception {
  try(var f=fixture()){
   var service=service(f);String working=working(f,service);String segment=(String)segments(service.readCurrent(f.actor,f.meeting)).getFirst().get("segmentId");long base=revision(f);
   var originalRoot=j.queryForMap("SELECT owner_id,access_revision FROM meeting WHERE id=?",f.meeting);String key=id();var command=new Command(Operation.SPEAKERS,base,working,List.of(),null,null,new SpeakerEdit(List.of(segment),"人工核对的王工",null));
   String assigned=result(service.mutate(f.actor,f.meeting,key,command,id()),"revisionId");String speaker=(String)segments(service.readCurrent(f.actor,f.meeting)).getFirst().get("speakerId");assertNotNull(speaker);assertNull(segments(service.readOwner(f.actor,f.meeting,raw(f))).getFirst().get("speakerId"));assertEquals(ORIGINAL,segments(service.readCurrent(f.actor,f.meeting)).getFirst().get("body"));
   var old=(List<Map<String,Object>>)service.readSpeakers(f.actor,f.meeting,assigned).get("items");assertEquals("人工核对的王工",old.getFirst().get("label"));assertNull(old.getFirst().get("enterpriseId"));assertEquals("HUMAN",old.getFirst().get("source"));
   String renamed=result(service.mutate(f.actor,f.meeting,id(),new Command(Operation.SPEAKERS,revision(f),assigned,List.of(),null,null,new SpeakerEdit(List.of(segment),"人工核对的李工",null)),id()),"revisionId");String renamedSpeaker=(String)segments(service.readCurrent(f.actor,f.meeting)).getFirst().get("speakerId");assertNotEquals(speaker,renamedSpeaker);assertEquals("人工核对的王工",((List<Map<String,Object>>)service.readSpeakers(f.actor,f.meeting,assigned).get("items")).getFirst().get("label"));
   String undone=result(service.mutate(f.actor,f.meeting,id(),new Command(Operation.UNDO,revision(f),renamed,List.of()),id()),"revisionId");assertEquals(speaker,segments(service.readCurrent(f.actor,f.meeting)).getFirst().get("speakerId"));assertTrue((Boolean)service.mutate(f.actor,f.meeting,key,command,id()).get("replayed"));assertEquals(undone,selected(service.readCurrent(f.actor,f.meeting)).get("id"));
   assertEquals(originalRoot,j.queryForMap("SELECT owner_id,access_revision FROM meeting WHERE id=?",f.meeting));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM transcript_speaker_access WHERE meeting_id=?",Integer.class,f.meeting));
   service.mutate(f.actor,f.meeting,id(),new Command(Operation.CONFIRM,revision(f),undone,List.of()),id());assertNotNull(service.readCurrent(f.actor,f.meeting).get("confirmation"));
  }
 }
 @Test void speakerHttpCreatesAVersionedLabelAndRejectsAForgedIdField()throws Exception {
  try(var f=fixture()){
   var service=service(f);String working=working(f,service),segment=(String)segments(service.readCurrent(f.actor,f.meeting)).getFirst().get("segmentId"),path="/api/v1/meetings/"+f.meeting+"/transcript/speakers";
   var mvc=ama.api.SpeechReviewHttpProbe.create(service,source,f.tx,f.access,f.components.registry());var session=new ama.contract.IdentityContracts.ResolvedSession(f.actor,"T".repeat(43),java.time.Instant.now().plusSeconds(60));String attribute=ama.api.SpeechReviewHttpProbe.sessionAttribute();var body=new LinkedHashMap<String,Object>();body.put("expectedRevisionToken",Long.toString(revision(f)));body.put("sourceRevisionId",working);body.put("segmentIds",List.of(segment));body.put("label","人工张工");body.put("enterpriseId",null);
   body.put("speakerId",id());var forged=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).requestAttr(attribute,session).header("Idempotency-Key",id()).contentType("application/json").content(JSON.writeValueAsBytes(body))).andReturn().getResponse();assertEquals(400,forged.getStatus());body.remove("speakerId");
   var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).requestAttr(attribute,session).header("Idempotency-Key",id()).contentType("application/json").content(JSON.writeValueAsBytes(body))).andReturn().getResponse();assertEquals(200,response.getStatus(),response.getContentAsString());var receipt=JSON.readTree(response.getContentAsByteArray());assertEquals(receipt.path("result").path("revisionId").asText(),receipt.path("result").path("operationId").asText());String current=selected(service.readCurrent(f.actor,f.meeting)).get("id").toString();
   var read=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).requestAttr(attribute,session).param("sourceRevisionId",current)).andReturn().getResponse();assertEquals(200,read.getStatus());assertEquals("人工张工",JSON.readTree(read.getContentAsByteArray()).path("items").get(0).path("label").asText());
  }
 }
 @Test void corruptedOrMissingSpeakerProofRejectsReadAndConfirmationWithoutChangingPermissions()throws Exception {
  try(var f=fixture()){
   var service=service(f);String working=working(f,service),segment=(String)segments(service.readCurrent(f.actor,f.meeting)).getFirst().get("segmentId");String current=result(service.mutate(f.actor,f.meeting,id(),new Command(Operation.SPEAKERS,revision(f),working,List.of(),null,null,new SpeakerEdit(List.of(segment),"人工王工",null)),id()),"revisionId");String speaker=(String)segments(service.readCurrent(f.actor,f.meeting)).getFirst().get("speakerId");
   j.update("UPDATE speaker SET display_name='篡改旧标签' WHERE id=?",speaker);assertThrows(RuntimeException.class,()->service.readCurrent(f.actor,f.meeting));j.update("UPDATE speaker SET display_name='人工王工' WHERE id=?",speaker);
   j.update("DELETE FROM transcript_speaker_change WHERE revision_id=?",current);assertThrows(RuntimeException.class,()->service.readCurrent(f.actor,f.meeting));assertThrows(RuntimeException.class,()->service.mutate(f.actor,f.meeting,id(),new Command(Operation.CONFIRM,revision(f),current,List.of()),id()));assertNull(j.queryForObject("SELECT transcript_confirmation_id FROM meeting WHERE id=?",String.class,f.meeting));
  }
 }
 @SuppressWarnings("unchecked") @Test void acceptingOneOfTwoActualClipsKeepsTheOtherPendingUntilExplicitlyHandled()throws Exception {
  try(var f=fixture(2)){
   var service=service(f);String working=working(f,service);service.proposeCorrections(f.actor,f.meeting,id(),revision(f),working,id());var items=(List<Map<String,Object>>)service.readCorrections(f.actor,f.meeting,working).get("items");assertEquals(2,items.size());String first=(String)items.get(0).get("id"),second=(String)items.get(1).get("id");
   String accepted=result(service.mutate(f.actor,f.meeting,id(),new Command(Operation.CORRECTION,revision(f),working,List.of(),null,new Correction(first,CorrectionDecision.ACCEPT)),id()),"revisionId");assertEquals(1,((Number)service.readCorrections(f.actor,f.meeting,accepted).get("pendingCount")).intValue());assertFalse((Boolean)((Map<?,?>)service.readCurrent(f.actor,f.meeting).get("capabilities")).get("canConfirm"));
   String rejected=result(service.mutate(f.actor,f.meeting,id(),new Command(Operation.CORRECTION,revision(f),accepted,List.of(),null,new Correction(second,CorrectionDecision.REJECT)),id()),"revisionId");assertEquals(0,((Number)service.readCorrections(f.actor,f.meeting,rejected).get("pendingCount")).intValue());assertEquals(List.of(ORIGINAL,ORIGINAL),segments(service.readOwner(f.actor,f.meeting,raw(f))).stream().map(r->r.get("body")).toList());
  }
 }
 @Test void proposalAndConfirmationCannotCommitAnUnresolvedConfirmedVersion()throws Exception {
  try(var f=fixture();var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
   var service=service(f);String working=working(f,service);long base=revision(f);var barrier=new java.util.concurrent.CyclicBarrier(2);var outcomes=new ArrayList<java.util.concurrent.Future<Boolean>>();
   outcomes.add(pool.submit(()->{barrier.await();try{service.proposeCorrections(f.actor,f.meeting,id(),base,working,id());return true;}catch(RuntimeException denied){return false;}}));outcomes.add(pool.submit(()->{barrier.await();try{service.mutate(f.actor,f.meeting,id(),new Command(Operation.CONFIRM,base,working,List.of()),id());return true;}catch(RuntimeException denied){return false;}}));
   int succeeded=0;for(var outcome:outcomes)if(outcome.get(45,java.util.concurrent.TimeUnit.SECONDS))succeeded++;assertEquals(1,succeeded);long pending=j.queryForObject("SELECT COUNT(*) FROM speech_review_candidate WHERE meeting_id=? AND state='PENDING'",Long.class,f.meeting);String confirmed=j.queryForObject("SELECT transcript_confirmation_id FROM meeting WHERE id=?",String.class,f.meeting);assertFalse(pending>0&&confirmed!=null);assertTrue(pending==1||confirmed!=null);
  }
 }
 @Test void humanEnterpriseAssociationIsExplicitFreshAndNeverGrantsMeetingAccess()throws Exception {
  try(var f=fixture()){
   var service=service(f);String working=working(f,service),segment=(String)segments(service.readCurrent(f.actor,f.meeting)).getFirst().get("segmentId"),person="human-speaker-"+id();j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",person,"王工");long base=revision(f);String key=id();var command=new Command(Operation.SPEAKERS,base,working,List.of(),null,null,new SpeakerEdit(List.of(segment),"人工核对王工",person));String current=result(service.mutate(f.actor,f.meeting,key,command,id()),"revisionId");
   var other=new ama.contract.IdentityContracts.Actor(person,ama.contract.IdentityContracts.Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER));assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->service.readSpeakers(other,f.meeting,current));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM meeting_access_grant WHERE meeting_id=? AND enterprise_id=?",Integer.class,f.meeting,person));
   j.update("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id=?",person);assertDoesNotThrow(()->service.readSpeakers(f.actor,f.meeting,current));assertEquals(true,service.mutate(f.actor,f.meeting,key,command,id()).get("replayed"));assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->service.mutate(f.actor,f.meeting,id(),new Command(Operation.SPEAKERS,revision(f),current,List.of(),null,null,new SpeakerEdit(List.of(segment),"另一人工标签",person)),id()));
  }
 }
}
