package ama.task;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.Actor;
import ama.contract.TaskContracts.TypedRef;
import ama.meeting.JdbcMeetingAccess;
import ama.task.ExternalOperationPort.ExecutionContext;
import ama.task.TaskDomainPolicy.PreparedDomain;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Narrow generation-only authority. Construction grants nothing; issuance needs the authentic held graph. */
public final class JdbcGenerationRetention {
 private final DataSource source;private final JdbcTaskPreparations preparations;private final JdbcMeetingAccess access;private final JdbcTemplate j;
 public JdbcGenerationRetention(DataSource source,JdbcTaskPreparations preparations,JdbcMeetingAccess access){this.source=Objects.requireNonNull(source);this.preparations=Objects.requireNonNull(preparations);this.access=Objects.requireNonNull(access);j=new JdbcTemplate(source);j.setQueryTimeout(2);}
 private final class Held implements GenerationRetentionPolicy.Retention {
  final PreparedDomain domain;final ExecutionContext context;final JdbcTaskPreparations.ExternalFacts facts;
  final Map<String,Object> root,subject;boolean used;
  private Held(PreparedDomain domain,ExecutionContext context,JdbcTaskPreparations.ExternalFacts facts,Map<String,Object> root,Map<String,Object> subject){this.domain=domain;this.context=context;this.facts=facts;this.root=root;this.subject=subject;}
  JdbcGenerationRetention issuer(){return JdbcGenerationRetention.this;}
 }
 public Optional<GenerationRetentionPolicy.Retention> prepare(PreparedDomain held,ExecutionContext context,MeetingAccess.Locked root,Actor originalOwner){
  JdbcExternalOperations.requireRetentionContext(source,held,context);
  var facts=preparations.requireExternal(held,context);var spec=facts.spec();
  if(!spec.type().equals("MINUTES_GENERATE_V1")||!spec.input().kind().equals("GENERATION_INPUT")||!spec.subject().kind().equals("MEETING"))throw new IllegalArgumentException("Generation family required");
  access.requirePrepared(root,originalOwner,spec.subject().id(),List.of(),MeetingAccess.Action.EDIT);
  var meeting=j.queryForMap("SELECT * FROM meeting WHERE id=? FOR UPDATE",spec.subject().id());
  var subject=j.queryForMap("SELECT * FROM task_subject WHERE subject_kind=? AND subject_id=? FOR UPDATE",spec.subject().kind(),spec.subject().id());
  long revision=((Number)meeting.get("revision")).longValue(),epoch=((Number)meeting.get("cancel_epoch")).longValue();
  if(!originalOwner.enterpriseId().equals(meeting.get("owner_id"))||!"ACTIVE".equals(meeting.get("lifecycle"))||!"ACTIVE".equals(meeting.get("governance_state"))
    ||((Number)meeting.get("ever_published")).intValue()!=0||"PUBLISHING".equals(meeting.get("stage"))||"PUBLISHED".equals(meeting.get("stage"))
    ||revision<=spec.businessVersion()||epoch!=spec.cancelEpoch()||((Number)subject.get("business_version")).longValue()!=revision||((Number)subject.get("cancel_epoch")).longValue()!=epoch||((Number)subject.get("deleted")).intValue()!=0)return Optional.empty();
  if(!j.queryForList("SELECT id FROM publication WHERE meeting_id=? AND state IN ('READY','RUNNING','RECONCILING') LIMIT 1",spec.subject().id()).isEmpty())return Optional.empty();
  var inputs=j.queryForList("SELECT meeting_id,base_revision,input_fingerprint FROM generation_input WHERE id=?",spec.input().id());
  if(inputs.size()!=1||!spec.subject().id().equals(inputs.getFirst().get("meeting_id"))||((Number)inputs.getFirst().get("base_revision")).longValue()!=spec.businessVersion()||!spec.inputFingerprint().equals(inputs.getFirst().get("input_fingerprint")))throw new IllegalArgumentException("Original generation input mismatch");
  return Optional.of(new Held(held,context,facts,meeting,subject));
 }
 static void consume(DataSource source,PreparedDomain domain,ExecutionContext context,GenerationRetentionPolicy.Retention retention){
  var held=require(source,domain,context,retention);if(held.used)throw new IllegalArgumentException("Retention already consumed");held.used=true;
 }
 private static Held require(DataSource source,PreparedDomain domain,ExecutionContext context,GenerationRetentionPolicy.Retention retention){
  if(!(retention instanceof JdbcGenerationRetention.Held h)||h.issuer().source!=source||h.domain!=domain||h.context!=context)throw new IllegalArgumentException("Foreign retention capability");
  JdbcExternalOperations.requireRetentionContext(source,domain,context);h.issuer().preparations.requireExternal(domain,context);return h;
 }
 static void validate(DataSource source,PreparedDomain domain,ExecutionContext context,GenerationRetentionPolicy.Retention retention,TypedRef candidate){
  var h=require(source,domain,context,retention);if(!h.used||candidate==null||!candidate.kind().equals("GENERATION_CANDIDATE"))throw new IllegalArgumentException("Retained candidate required");
  var j=h.issuer().j;var s=h.facts.spec();
  if(!h.root.equals(j.queryForMap("SELECT * FROM meeting WHERE id=? FOR UPDATE",s.subject().id()))||!h.subject.equals(j.queryForMap("SELECT * FROM task_subject WHERE subject_kind=? AND subject_id=? FOR UPDATE",s.subject().kind(),s.subject().id())))throw new IllegalArgumentException("Retention changed current root");
  var rows=j.queryForList("SELECT id FROM generation_candidate WHERE id=? AND meeting_id=? AND task_id=? AND input_id=? AND base_revision=? AND input_fingerprint=? AND state='STALE' AND minutes_revision_id IS NOT NULL",candidate.id(),s.subject().id(),h.facts.taskId(),s.input().id(),s.businessVersion(),s.inputFingerprint());
  if(rows.size()!=1)throw new IllegalArgumentException("Retained candidate provenance mismatch");
 }
}
