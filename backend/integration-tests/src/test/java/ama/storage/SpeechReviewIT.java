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
 ControlledSpeechImportIT.Fixture fixture()throws Exception {
  var harness=new ControlledSpeechImportIT();harness.root=root;byte[] audio=wav(128000);var random=new java.security.SecureRandom();byte[] marker=new byte[16];random.nextBytes(marker);System.arraycopy(marker,0,audio,1000,marker.length);
  byte[] normalized;try(var prepared=new FfmpegNormalizer(file->new ByteArrayInputStream(audio)).prepare(List.of(new ama.contract.MediaContracts.OrderedSource(id(),0)),()->false);var clip=prepared.openClip(prepared.sources().getFirst().fileId(),0,128000)){normalized=clip.readAllBytes();}
  scenario("asr","/inference/asr-nano/modelserving/asr",Map.of("binary_sha256",sha(normalized)),Map.of("text",ORIGINAL));
  scenario("punctuation","/inference/asr-punc/modelserving/punc",Map.of("text_equals",ORIGINAL),Map.of("text",ENHANCED));
  var f=harness.new Fixture(audio);var lease=f.claim(f.queue);var carrier=TranscriptImportRuntime.prepare(f.queue,lease,f.settings,NEVER);assertTrue(f.queue.completeWithEffects(lease,carrier));return f;
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
   var service=service(f);String working=working(f,service);candidate(f,service,working);assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM speech_review_batch WHERE meeting_id=?",Integer.class,f.meeting));
   f.meetings.delete(f.actor,f.meeting,id(),new ama.contract.MeetingContracts.DeleteMeeting(revision(f)),"SPEECH_REVIEW_DELETE");
   try(var deletion=new ama.meeting.JdbcMeetingDeletion(source,f.tx,f.access,f.catalog)){
    String job=deletion.forMeeting(f.actor,f.meeting).deletionTaskId();for(int n=0;n<180&&!deletion.status(f.actor,job).state().equals("COMPLETE");n++)assertTrue(deletion.runOnce(job));assertEquals("COMPLETE",deletion.status(f.actor,job).state());
   }
   assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM speech_review_candidate WHERE meeting_id=?",Integer.class,f.meeting));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM speech_review_batch WHERE meeting_id=?",Integer.class,f.meeting));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM speech_checkpoint WHERE input_id=?",Integer.class,f.input));assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->service.readCorrections(f.actor,f.meeting,working));
  }
 }
}
