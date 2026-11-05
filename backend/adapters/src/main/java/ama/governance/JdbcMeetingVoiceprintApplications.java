package ama.governance;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.Actor;
import ama.contract.MeetingContracts.Conflict;
import ama.db.TransactionRunner;
import ama.meeting.JdbcMeetingAccess;
import ama.storage.FileBackendCatalog;
import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** One authoritative application state; formal matching entries exist only after approval. */
public final class JdbcMeetingVoiceprintApplications extends JdbcOperationsSupport {
 private String checkedAfter="";
 private final GovernanceHttpProvider provider;private final EncryptedEmbeddingStore crypto;private final MeetingVoiceSample samples;
 public JdbcMeetingVoiceprintApplications(DataSource source,TransactionRunner tx,JdbcMeetingAccess access,GovernanceHttpProvider provider,EncryptedEmbeddingStore crypto,FileBackendCatalog backends){super(source,tx,access);this.provider=provider;this.crypto=crypto;this.samples=new MeetingVoiceSample(j,backends);}
 public boolean contains(String id){id(id);return j.queryForObject("SELECT COUNT(*) FROM meeting_voiceprint_application WHERE id=?",Integer.class,id)==1;}
 public Map<String,Object> submit(Actor actor,String key,String meeting,long revision,List<String> segments,String subject,String name){
  id(meeting);expected(revision);reason(subject);reason(name);segments=List.copyOf(segments);if(segments.isEmpty()||segments.size()>500||segments.stream().distinct().count()!=segments.size())throw new IllegalArgumentException("Bounded unique speaker segments required");segments.forEach(JdbcOperationsSupport::id);
  var request=List.of(meeting,revision,segments,subject,name);var prior=original(actor,key,"MEETING_VOICE_SUBMIT",request);if(prior!=null)return prior;
  final var ids=segments;access.prepareExternalRead(actor,meeting);
  var source=tx.required(()->source(actor,meeting,revision,ids,subject,name));
  var consent=provider.consent(subject,meeting);String reference=consent.path("reference").asText(),policy=consent.path("samplePolicy").path("version").asText();reason(reference);reason(policy);
  Instant expires=Instant.parse(consent.path("expiresAt").asText());if(!expires.isAfter(Instant.now()))throw new IllegalArgumentException("对应人员授权已失效，未提交声纹审核。");
  long min=consent.path("samplePolicy").path("minSamples").asLong(),max=consent.path("samplePolicy").path("maxSamples").asLong(),target=consent.path("samplePolicy").path("targetSamples").asLong();
  if(min<48000||max>800000||target<min||target>max)throw new IllegalStateException("Sample policy unavailable");
  byte[] wav=samples.collect(meeting,(String)source.get("transcript"),rows(source),target);long count=GovernanceAudio.require(wav,min,max);var embedding=provider.embedMeeting(wav);
  // Recheck the provider after inference and all local authorities at commit.
  if(!provider.consent(subject,meeting).equals(consent))throw new IllegalArgumentException("对应人员授权已变化，未提交声纹审核。");
  return command(actor,key,"MEETING_VOICE_SUBMIT",request,false,()->{
   var fresh=source(actor,meeting,revision,ids,subject,name);if(!fresh.equals(source)||!expires.isAfter(Instant.now()))throw new Conflict();
   String id=UUID.randomUUID().toString();byte[] encrypted=crypto.encrypt(aad(id,subject,embedding.modelVersion(),embedding.dimension()),embedding);
   j.update("INSERT INTO meeting_voiceprint_application(id,meeting_id,applicant_id,subject_id,subject_name,transcript_revision_id,source_revision,segment_ids,consent_reference,expires_at,policy_version,model_version,dimension,sample_sha,sample_count,encrypted_embedding) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,meeting,actor.enterpriseId(),subject,name,source.get("transcript"),revision,write(ids),reference,Timestamp.from(expires),policy,embedding.modelVersion(),embedding.dimension(),MeetingVoiceSample.sha(wav),count,encrypted);
   GovernanceVoiceSamples.record(j,id,0,"MEETING",actor.enterpriseId());audit(actor,"MEETING_VOICE_SUBMITTED",id,0);return view(locked(id));
  });
 }
 public Map<String,Object> resample(Actor actor,String id,String key,long expected,String reason){
  id(id);expected(expected);reason(reason);var request=List.of(id,expected,reason);var prior=original(actor,key,"MEETING_VOICE_RESAMPLE",request);if(prior!=null)return prior;
  var old=tx.required(()->{var row=locked(id);authorize(actor,row,"SUPPLEMENT");if(((Number)row.get("revision")).longValue()!=expected||!Set.of("NEEDS_MORE","REJECTED").contains(row.get("state")))throw new Conflict();return row;});
  String meeting=(String)old.get("meeting_id"),subject=(String)old.get("subject_id"),name=(String)old.get("subject_name");var ids=List.of(read((String)old.get("segment_ids"),String[].class));access.prepareExternalRead(actor,meeting);
  long revision=j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,meeting);var source=tx.required(()->source(actor,meeting,revision,ids,subject,name));
  var consent=provider.consent(subject,meeting);String reference=consent.path("reference").asText(),policy=consent.path("samplePolicy").path("version").asText();reason(reference);reason(policy);Instant expires=Instant.parse(consent.path("expiresAt").asText());
  long min=consent.path("samplePolicy").path("minSamples").asLong(),max=consent.path("samplePolicy").path("maxSamples").asLong(),target=consent.path("samplePolicy").path("targetSamples").asLong();if(!expires.isAfter(Instant.now())||min<48000||max>800000||target<min||target>max)throw new IllegalArgumentException("当前有效授权与采样策略必需。");
  byte[] wav=samples.collect(meeting,(String)source.get("transcript"),rows(source),target);long count=GovernanceAudio.require(wav,min,max);var embedding=provider.embedMeeting(wav);if(!provider.consent(subject,meeting).equals(consent))throw new Conflict();
  return command(actor,key,"MEETING_VOICE_RESAMPLE",request,false,()->{var row=locked(id);authorize(actor,row,"SUPPLEMENT");if(((Number)row.get("revision")).longValue()!=expected||expected>=50||!Set.of("NEEDS_MORE","REJECTED").contains(row.get("state"))||!expires.isAfter(Instant.now())||!source(actor,meeting,revision,ids,subject,name).equals(source))throw new Conflict();
   j.update("UPDATE meeting_voiceprint_application SET transcript_revision_id=?,source_revision=?,consent_reference=?,expires_at=?,policy_version=?,model_version=?,dimension=?,sample_sha=?,sample_count=?,encrypted_embedding=?,state='PENDING',revision=revision+1,review_reason=NULL,reviewed_by=NULL WHERE id=?",source.get("transcript"),revision,reference,Timestamp.from(expires),policy,embedding.modelVersion(),embedding.dimension(),MeetingVoiceSample.sha(wav),count,crypto.encrypt(aad(id,subject,embedding.modelVersion(),embedding.dimension()),embedding),id);
   j.update("UPDATE governed_voiceprint SET state='REVOKED',encrypted_embedding=NULL,revision=revision+1 WHERE id=? AND state<>'REVOKED'",id);
   j.update("INSERT INTO meeting_voiceprint_history(id,application_id,actor_id,action,revision_after,state_after,text) VALUES (?,?,?,'RESAMPLE',?,'PENDING',?)",UUID.randomUUID().toString(),id,actor.enterpriseId(),expected+1,reason);GovernanceVoiceSamples.record(j,id,expected+1,"MEETING",actor.enterpriseId());audit(actor,"MEETING_VOICE_RESAMPLED",id,expected+1);return view(locked(id));
  });
 }
 private Map<String,Object> source(Actor actor,String meeting,long revision,List<String> segments,String subject,String name){
  var gate=access.lock(actor,meeting);access.require(gate,MeetingAccess.Action.EDIT);var root=j.queryForMap("SELECT revision,transcript_revision_id FROM meeting WHERE id=?",meeting);
  if(((Number)root.get("revision")).longValue()!=revision||root.get("transcript_revision_id")==null)throw new Conflict();
  var all=j.queryForList("SELECT s.segment_id,s.source_file_id,s.start_ms,s.end_ms,s.speaker_id,p.enterprise_id,p.label FROM transcript_segment s JOIN transcript_speaker_snapshot p ON p.id=s.speaker_id AND p.meeting_id=? WHERE s.revision_id=? ORDER BY s.ordinal",meeting,root.get("transcript_revision_id"));
  var selected=all.stream().filter(s->segments.contains(s.get("segment_id"))).toList();
  if(selected.size()!=segments.size()||selected.stream().anyMatch(s->!subject.equals(s.get("enterprise_id"))||!name.equals(s.get("label")))||selected.stream().map(s->s.get("speaker_id")).distinct().count()!=1)throw new IllegalArgumentException("当前同一人员的会议片段必需。");
  if(j.queryForObject("SELECT COUNT(*) FROM enterprise_identity WHERE enterprise_id=? AND status='ACTIVE'",Integer.class,subject)!=1)throw new IllegalArgumentException("当前有效人员身份必需。");
  return Map.of("transcript",root.get("transcript_revision_id"),"segments",selected);
 }
 @SuppressWarnings("unchecked") private static List<Map<String,Object>> rows(Map<String,Object> source){return (List<Map<String,Object>>)source.get("segments");}
 public Map<String,Object> detail(Actor actor,String id){id(id);return tx.required(()->{active(actor);var row=locked(id);if(!actor.enterpriseId().equals(row.get("applicant_id"))&&!admin(actor))throw new MeetingAccess.AccessDeniedException();var result=new LinkedHashMap<String,Object>();result.put("feedback",view(row));result.put("submittedAt",row.get("submittedAt"));result.put("reviewReason",row.get("review_reason"));result.put("reviewedBy",row.get("reviewed_by"));result.put("usage",GovernanceUsage.voice(j,id));result.put("history",j.queryForList("SELECT id,action,actor_id actorId,CAST(revision_after AS CHAR) revisionToken,state_after state,text,DATE_FORMAT(created_at,'%Y-%m-%dT%H:%i:%s.%fZ') createdAt FROM meeting_voiceprint_history WHERE application_id=? ORDER BY revision_after LIMIT 50",id));return result;});}
 public Map<String,Object> action(Actor actor,String id,String key,long expected,String action,String decision,String text){
  id(id);expected(expected);if(!Set.of("SUPPLEMENT","REVIEW","UNDO_REVIEW").contains(action))throw new IllegalArgumentException("Application action required");
  if(!action.equals("UNDO_REVIEW")){if(text==null||text.isBlank()||text.length()>(action.equals("SUPPLEMENT")?4096:2048)||text.indexOf(0)>=0)throw new IllegalArgumentException("Bounded application reason required");}
  if(action.equals("REVIEW")&&!Set.of("ACCEPTED","REJECTED","NEEDS_MORE").contains(Objects.requireNonNullElse(decision,"")))throw new IllegalArgumentException("Application review decision required");
  var request=List.of(id,expected,action,Objects.requireNonNullElse(decision,""),text);String operation="MEETING_VOICE_"+action;
  var prior=original(actor,key,operation,request);if(prior!=null){tx.required(()->{authorize(actor,locked(id),action);return null;});return prior;}
  // Approval requires current subject authorization as well as the retained qualified sample.
  if(action.equals("REVIEW")&&"ACCEPTED".equals(decision)){var row=tx.required(()->{var r=locked(id);authorize(actor,r,action);return r;});var consent=provider.consent((String)row.get("subject_id"),(String)row.get("meeting_id"));if(!consent.path("reference").asText().equals(row.get("consent_reference")))throw new Conflict();}
  return command(actor,key,operation,request,!action.equals("SUPPLEMENT"),()->{
   var row=locked(id);authorize(actor,row,action);long current=((Number)row.get("revision")).longValue();String state=(String)row.get("state");if(current!=expected||current>=50)throw new Conflict();
   if(action.equals("REVIEW")&&!state.equals("PENDING")||action.equals("UNDO_REVIEW")&&state.equals("PENDING"))throw new Conflict();
   String next=action.equals("REVIEW")?decision:action.equals("UNDO_REVIEW")?"PENDING":state;
   if("ACCEPTED".equals(next)&&action.equals("REVIEW")){
    if(row.get("encrypted_embedding")==null||!instant(row.get("expires_at")).isAfter(Instant.now()))throw new Conflict();
    j.update("INSERT INTO governed_voiceprint(id,subject_id,consent_reference,purpose,model_version,dimension,sample_sha,sample_count,expires_at,state,encrypted_embedding,reviewer_id,review_reason) VALUES (?,?,?,'SPEAKER_CANDIDATE',?,?,?,?,?,'APPROVED',?,?,?) ON DUPLICATE KEY UPDATE state='APPROVED',encrypted_embedding=VALUES(encrypted_embedding),consent_reference=VALUES(consent_reference),model_version=VALUES(model_version),dimension=VALUES(dimension),sample_sha=VALUES(sample_sha),sample_count=VALUES(sample_count),expires_at=VALUES(expires_at),revision=revision+1,reviewer_id=VALUES(reviewer_id),review_reason=VALUES(review_reason)",id,row.get("subject_id"),row.get("consent_reference"),row.get("model_version"),row.get("dimension"),row.get("sample_sha"),row.get("sample_count"),row.get("expires_at"),row.get("encrypted_embedding"),actor.enterpriseId(),text);
   }else if(!"ACCEPTED".equals(next))j.update("UPDATE governed_voiceprint SET state='REVOKED',encrypted_embedding=NULL,revision=revision+1 WHERE id=? AND state<>'REVOKED'",id);
   boolean reset=!next.equals(state)||action.equals("REVIEW")||action.equals("UNDO_REVIEW");
   j.update("UPDATE meeting_voiceprint_application SET state=?,revision=revision+1,review_reason=?,reviewed_by=? WHERE id=?",next,action.equals("REVIEW")?text:reset?null:row.get("review_reason"),action.equals("REVIEW")?actor.enterpriseId():reset?null:row.get("reviewed_by"),id);
   String history=UUID.randomUUID().toString();j.update("INSERT INTO meeting_voiceprint_history(id,application_id,actor_id,action,revision_after,state_after,text) VALUES (?,?,?,?,?,?,?)",history,id,actor.enterpriseId(),action,current+1,next,text);audit(actor,operation,id,current+1);
   return Map.of("feedbackId",id,"historyId",history,"revisionToken",Long.toString(current+1),"state",next,"operation",action);
  });
 }
 private void authorize(Actor actor,Map<String,Object> row,String action){active(actor);if(action.equals("SUPPLEMENT")){if(!actor.enterpriseId().equals(row.get("applicant_id")))throw new MeetingAccess.AccessDeniedException();access.require(access.lock(actor,(String)row.get("meeting_id")),MeetingAccess.Action.EDIT);}else{requireAdmin(actor);if(actor.enterpriseId().equals(row.get("applicant_id"))||actor.enterpriseId().equals(row.get("subject_id")))throw new MeetingAccess.AccessDeniedException();}}
 private Map<String,Object> locked(String id){var rows=j.queryForList("SELECT *,DATE_FORMAT(created_at,'%Y-%m-%dT%H:%i:%s.%fZ') submittedAt FROM meeting_voiceprint_application WHERE id=? FOR UPDATE",id);if(rows.size()!=1)throw new MeetingAccess.AccessDeniedException();return rows.getFirst();}
 private static Map<String,Object> view(Map<String,Object> row){return Map.of("id",row.get("id"),"meetingId",row.get("meeting_id"),"category","VOICEPRINT","comment",row.get("subject_name"),"state",row.get("state"),"revisionToken",row.get("revision").toString());}
 private static Instant instant(Object value){return value instanceof Timestamp t?t.toInstant():((java.time.LocalDateTime)value).toInstant(java.time.ZoneOffset.UTC);}
 private static String aad(String id,String subject,String model,int dimension){return id+":"+subject+":"+model+":"+dimension;}
 public void cleanExpired(){
  j.update("UPDATE meeting_voiceprint_application SET encrypted_embedding=NULL WHERE expires_at<=UTC_TIMESTAMP(6) AND encrypted_embedding IS NOT NULL");
  var candidates=j.queryForList("SELECT a.id,a.subject_id,a.meeting_id,a.consent_reference FROM meeting_voiceprint_application a JOIN governed_voiceprint v ON v.id=a.id WHERE v.state='APPROVED' AND a.id>? ORDER BY a.id LIMIT 5",checkedAfter);
  if(candidates.isEmpty()){checkedAfter="";return;}
  for(var candidate:candidates){checkedAfter=(String)candidate.get("id");boolean revoked=false;
   try{var consent=provider.consent((String)candidate.get("subject_id"),(String)candidate.get("meeting_id"));revoked=!candidate.get("consent_reference").equals(consent.path("reference").asText());}
   catch(IllegalArgumentException denied){revoked=true;}catch(IllegalStateException unavailable){continue;}
   if(revoked)tx.required(()->{j.update("UPDATE governed_voiceprint SET state='REVOKED',encrypted_embedding=NULL,revision=revision+1,review_reason='对应人员授权已撤销或失效',updated_at=UTC_TIMESTAMP(6) WHERE id=? AND state='APPROVED'",candidate.get("id"));j.update("UPDATE meeting_voiceprint_application SET encrypted_embedding=NULL WHERE id=?",candidate.get("id"));return null;});
  }
 }
}
