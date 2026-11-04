package ama.meeting;
import ama.access.MeetingAccess;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StructuredFeedbackIT extends FeedbackReviewIT {
 @Test void sixOriginalTypesKeepIndependentValuesAndOptionalNoteAcrossReviewReplay(){
  for(String type:JdbcFeedback.TYPES){String key=UUID.randomUUID().toString();var created=feedback.createStructured(actor,key,meeting,type," 原始😀 ","建议\n第二行","");String id=(String)created.get("id");
   assertEquals(created,feedback.createStructured(actor,key,meeting,type," 原始😀 ","建议\n第二行",""));
   assertThrows(ama.contract.MeetingContracts.Conflict.class,()->feedback.createStructured(actor,key,meeting,type," 原始😀 ","改变建议",""));
   feedback.review(reviewer(true),id,UUID.randomUUID().toString(),0,"NEEDS_MORE","请补充");feedback.supplement(actor,id,UUID.randomUUID().toString(),1,"依据");
   var stored=j.queryForMap("SELECT original_value,suggested_value,note FROM feedback WHERE id=?",id);assertEquals(Map.of("original_value"," 原始😀 ","suggested_value","建议\n第二行","note",""),stored);
   assertEquals("PENDING",current(actor,id).get("state"));assertEquals(created,feedback.createStructured(actor,key,meeting,type," 原始😀 ","建议\n第二行",""));
  }
  var overview=feedback.summaries(actor,null,null,false);assertEquals(6,((List<?>)overview.get("items")).size());for(Object value:(List<?>)overview.get("items")){var row=(Map<?,?>)value;assertEquals("CORPUS",row.get("kind"));assertEquals(" 原始😀 ",row.get("originalValue"));assertEquals("建议\n第二行",row.get("suggestedValue"));}
 }
 @Test void structuredBindingFailureRollsBackCreationWithoutReacquiringIdentityAfterRoot(){
  var tx=new ama.db.TransactionRunner(source);var access=new JdbcMeetingAccess(source,tx,new ama.task.JdbcSubjectFences(source,Set.of("MEETING")));var corpus=new ama.governance.JdbcCorpusFeedback(source,tx,access,new JdbcFeedback(source,tx,access));
  assertThrows(IllegalArgumentException.class,()->corpus.submitStructured(actor,UUID.randomUUID().toString(),meeting,0,List.of(UUID.randomUUID().toString()),"PERSON","原文","建议",""));
  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM feedback WHERE meeting_id=?",Integer.class,meeting));
 }
 @Test void deletionKeepsAdoptionReceiptUntilExternalRevocationCompletes(){
  var row=feedback.createStructured(actor,UUID.randomUUID().toString(),meeting,"PERSON","原值","建议","");String id=(String)row.get("id");
  j.update("INSERT INTO corpus_feedback_binding(feedback_id,source_revision,segment_ids,adoption_state,adoption_version) VALUES (?,0,'[]','ADOPTED','harness-corpus-test')",id);
  assertFalse(MeetingDeletionRows.feedbackEffectsSettled(j,meeting));j.update("UPDATE corpus_feedback_binding SET adoption_state='REVOKED' WHERE feedback_id=?",id);assertTrue(MeetingDeletionRows.feedbackEffectsSettled(j,meeting));
  j.update("UPDATE corpus_feedback_binding SET adoption_pending='{}' WHERE feedback_id=?",id);assertFalse(MeetingDeletionRows.feedbackEffectsSettled(j,meeting));
 }
 @Test void summaryCursorIsBoundToIdentityAndFilterAndReturnsAllTiedRows(){
  for(int n=0;n<24;n++)feedback.createStructured(actor,UUID.randomUUID().toString(),meeting,"PRODUCT_ALIAS","原文"+n,"建议"+n,"说明");
  j.update("UPDATE feedback SET created_at='2026-11-03 00:00:00' WHERE meeting_id=?",meeting);
  var one=feedback.summaries(actor,null,null,false);String cursor=(String)one.get("nextCursor");assertTrue(cursor.startsWith("f1."));var two=feedback.summaries(actor,cursor,null,false);var ids=new HashSet<Object>();for(var page:List.of(one,two))for(Object row:(List<?>)page.get("items"))assertTrue(ids.add(((Map<?,?>)row).get("id")));assertEquals(24,ids.size());
  assertThrows(IllegalArgumentException.class,()->feedback.summaries(actor,cursor,"PENDING",false));assertThrows(IllegalArgumentException.class,()->feedback.summaries(reviewer(false),cursor,null,false));
 }
 @Test void mixedCorpusAndVoiceTiesArePagedTogetherByApplicantRatherThanSubject(){
  var subject=reviewer(false);var states=List.of("PENDING","ACCEPTED","REJECTED","NEEDS_MORE");
  for(int n=0;n<12;n++){
   var corpus=feedback.createStructured(actor,UUID.randomUUID().toString(),meeting,"PERSON","原值"+n,"建议"+n,"");j.update("UPDATE feedback SET state=?,created_at='2026-11-03 00:00:00' WHERE id=?",states.get(n%4),corpus.get("id"));
   j.update("INSERT INTO meeting_voiceprint_application(id,meeting_id,applicant_id,subject_id,subject_name,transcript_revision_id,source_revision,segment_ids,consent_reference,expires_at,policy_version,model_version,dimension,sample_sha,sample_count,state,created_at) VALUES (?,?,?,?,?,?,0,'[]','query-fixture',TIMESTAMPADD(DAY,1,UTC_TIMESTAMP()),'query-policy','query-model',256,?,48000,?,'2026-11-03 00:00:00')",UUID.randomUUID().toString(),meeting,actor.enterpriseId(),subject.enterpriseId(),"主体"+n,UUID.randomUUID().toString(),"a".repeat(64),states.get(n%4));
  }
  var one=feedback.summaries(actor,null,null,false);var two=feedback.summaries(actor,(String)one.get("nextCursor"),null,false);var ids=new HashSet<Object>();for(var page:List.of(one,two))for(Object row:(List<?>)page.get("items"))assertTrue(ids.add(((Map<?,?>)row).get("id")));assertEquals(24,ids.size());
  var counts=(Map<?,?>)one.get("counts");for(String state:states){assertEquals(6L,((Number)counts.get(state)).longValue());assertEquals(6,((List<?>)feedback.summaries(actor,null,state,false).get("items")).size());}
  assertEquals(0L,((Number)((Map<?,?>)feedback.summaries(subject,null,null,false).get("counts")).get("ALL")).longValue());
 }
 @Test void cachedVoiceCandidatesDisappearAsSoonAsTheirSourceMeetingIsDeleting() throws Exception{
  ama.governance.GovernanceVoiceConsent.configure((subject,meetingId)->new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("reference","query-fixture").put("expiresAt",java.time.Instant.now().plusSeconds(3600).toString()).set("samplePolicy",new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("version","query-policy")));
  var runtime=new ama.governance.JdbcGovernanceRuntime(source);String input=UUID.randomUUID().toString(),voice=UUID.randomUUID().toString(),file=UUID.randomUUID().toString();var snapshot=runtime.freeze(input);String model=snapshot.configuration().modelVersion();
  var consent=Map.of("subjectId",actor.enterpriseId(),"meetingId",meeting,"reference","query-fixture","expiresAt",java.time.Instant.now().plusSeconds(86400).toString(),"active",true);
  var grant=java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:8792/admin/api/feedback/consents")).header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(consent))).build(),java.net.http.HttpResponse.BodyHandlers.ofString());assertEquals(200,grant.statusCode());
  var provider=new ama.governance.GovernanceHttpProvider(java.net.URI.create("http://127.0.0.1:8792"),model,256);ama.governance.GovernanceVoiceConsent.configure(provider::consent);
  j.update("INSERT INTO meeting_voiceprint_application(id,meeting_id,applicant_id,subject_id,subject_name,transcript_revision_id,source_revision,segment_ids,consent_reference,expires_at,policy_version,model_version,dimension,sample_sha,sample_count,state) VALUES (?,?,?,?,?,?,0,'[]','query-fixture',TIMESTAMPADD(DAY,1,UTC_TIMESTAMP()),'meeting-pcm-3-50s.v1',?,256,?,48000,'ACCEPTED')",voice,meeting,actor.enterpriseId(),actor.enterpriseId(),"主体",UUID.randomUUID().toString(),model,"a".repeat(64));
  j.update("INSERT INTO governed_voiceprint(id,subject_id,consent_reference,purpose,model_version,dimension,sample_sha,sample_count,expires_at,state,encrypted_embedding) VALUES (?,?,'query-fixture','SPEAKER_CANDIDATE',?,256,?,48000,TIMESTAMPADD(DAY,1,UTC_TIMESTAMP()),'APPROVED',?)",voice,actor.enterpriseId(),model,"a".repeat(64),new byte[]{1,2,3});
  j.update("INSERT INTO governance_speaker_candidate(input_id,file_id,start_sample,speaker_id,candidates_json,configuration_revision) VALUES (?,?,0,'speaker',JSON_ARRAY(JSON_OBJECT('voiceprintId',?,'subjectId',?,'modelVersion',?)),?)",input,file,voice,actor.enterpriseId(),model,snapshot.revision());
  assertEquals(1,((List<?>)runtime.candidateRows(input).getFirst().get("candidates")).size());
  j.update("UPDATE meeting SET lifecycle='DELETING' WHERE id=?",meeting);
  assertEquals(0,((List<?>)runtime.candidateRows(input).getFirst().get("candidates")).size());
  assertEquals("INVALID_SOURCE",ama.governance.GovernanceUsage.voice(j,voice).get("usageState"));
 }
 @Test void readOnlyParticipantCannotUseEitherSubmissionService(){
  var member=reviewer(false);String roster=UUID.randomUUID().toString();
  j.update("INSERT INTO roster_revision(id,meeting_id,revision,state,confirmed_at,confirmed_by) VALUES (?,?,1,'CONFIRMED',UTC_TIMESTAMP(6),?)",roster,meeting,actor.enterpriseId());
  j.update("INSERT INTO roster_member(roster_id,member_id,member_kind,enterprise_id,display_name) VALUES (?,?,'ENTERPRISE',?,?)",roster,UUID.randomUUID().toString(),member.enterpriseId(),"只读人员");
  j.update("INSERT INTO meeting_access_grant(meeting_id,enterprise_id,action,roster_id,confirmed_at) VALUES (?,?,'READ',?,UTC_TIMESTAMP(6))",meeting,member.enterpriseId(),roster);j.update("UPDATE meeting SET roster_revision_id=? WHERE id=?",roster,meeting);
  var tx=new ama.db.TransactionRunner(source);var access=new JdbcMeetingAccess(source,tx,new ama.task.JdbcSubjectFences(source,Set.of("MEETING")));access.require(member,meeting,MeetingAccess.Action.READ);
  assertThrows(MeetingAccess.AccessDeniedException.class,()->feedback.create(member,UUID.randomUUID().toString(),meeting,"WORDING","绕过页面"));
  var corpus=new ama.governance.JdbcCorpusFeedback(source,tx,access,feedback);assertThrows(MeetingAccess.AccessDeniedException.class,()->corpus.submit(member,UUID.randomUUID().toString(),meeting,0,List.of(),"WORDING","绕过绑定入口"));
  assertThrows(MeetingAccess.AccessDeniedException.class,()->corpus.submitStructured(member,UUID.randomUUID().toString(),meeting,0,List.of(UUID.randomUUID().toString()),"PERSON","原文","建议",""));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM feedback WHERE meeting_id=?",Integer.class,meeting));
 }
}
