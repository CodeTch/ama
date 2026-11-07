package ama.governance;
import ama.db.TransactionRunner;
import ama.meeting.JdbcMeetingAccess;
import javax.sql.DataSource;
import java.util.*;
/** Durable original external commands: a lost reply never changes the retried body. */
public final class JdbcCorpusAdoption extends JdbcOperationsSupport {
 private final GovernanceHttpProvider provider;
 public JdbcCorpusAdoption(DataSource source,TransactionRunner tx,JdbcMeetingAccess access,GovernanceHttpProvider provider){super(source,tx,access);this.provider=provider;}
 public void process(){
  // The queue scan is only a scheduling hint. Do not take join/range locks before
  // the submitter identity and meeting root used by foreground feedback commands.
  var due=j.queryForList("SELECT f.id,f.meeting_id,f.submitted_by FROM corpus_feedback_binding b JOIN feedback f ON f.id=b.feedback_id WHERE f.feedback_type IS NOT NULL ORDER BY COALESCE(b.adoption_checked_at,'1970-01-01'),f.id LIMIT 1");if(due.isEmpty())return;var hint=due.getFirst();
  var body=tx.required(()->{
   var actor=new ama.contract.IdentityContracts.Actor((String)hint.get("submitted_by"),ama.contract.IdentityContracts.Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER));
   // Fact capabilities also capture disabled identities, so existing adoption can
   // still be revoked. They do not authorize source content or business writes.
   var identities=access.tryLockFactIdentities(actor,List.of(),true);if(identities.isEmpty()||access.lockFactRoot(identities.orElseThrow(),(String)hint.get("meeting_id"),true).isEmpty())return null;
   var rows=j.queryForList("SELECT f.id,f.feedback_type,f.original_value,f.suggested_value,f.note,f.state,f.revision,b.*,m.revision current_source_revision,m.lifecycle,m.governance_state,m.owner_id,f.submitted_by,i.status actor_status FROM corpus_feedback_binding b JOIN feedback f ON f.id=b.feedback_id JOIN meeting m ON m.id=f.meeting_id JOIN enterprise_identity i ON i.enterprise_id=f.submitted_by WHERE f.id=? AND f.meeting_id=? AND f.submitted_by=? AND f.feedback_type IS NOT NULL LIMIT 1 FOR UPDATE SKIP LOCKED",hint.get("id"),hint.get("meeting_id"),hint.get("submitted_by"));if(rows.isEmpty())return null;var r=rows.getFirst();j.update("UPDATE corpus_feedback_binding SET adoption_checked_at=UTC_TIMESTAMP(6) WHERE feedback_id=?",r.get("id"));
   if(r.get("adoption_pending") instanceof String pending)return map(pending);
   boolean active="ACCEPTED".equals(r.get("state"))&&!Boolean.TRUE.equals(r.get("withdrawn"))&&"ACTIVE".equals(r.get("lifecycle"))&&"ACTIVE".equals(r.get("governance_state"))&&"ACTIVE".equals(r.get("actor_status"))&&r.get("owner_id").equals(r.get("submitted_by"))&&((Number)r.get("source_revision")).longValue()==((Number)r.get("current_source_revision")).longValue();
   String fingerprint=digest(r.get("revision").toString(),Boolean.toString(active));if(fingerprint.equals(r.get("adoption_fingerprint"))||!active&&"NOT_APPLIED".equals(r.get("adoption_state")))return null;
   var command=new LinkedHashMap<String,Object>();command.put("id",r.get("id"));command.put("revision",((Number)r.get("adoption_sequence")).longValue()+1);command.put("active",active);command.put("feedbackType",r.get("feedback_type"));command.put("originalValue",CorpusFeedbackService.redact((String)r.get("original_value")));command.put("suggestedValue",CorpusFeedbackService.redact((String)r.get("suggested_value")));command.put("note",CorpusFeedbackService.redact((String)r.get("note")));
   var envelope=Map.of("command",command,"fingerprint",fingerprint);j.update("UPDATE corpus_feedback_binding SET adoption_pending=? WHERE feedback_id=?",write(envelope),r.get("id"));return new LinkedHashMap<String,Object>(envelope);
  });if(body==null)return;
  @SuppressWarnings("unchecked") var command=(Map<String,Object>)body.get("command");var receipt=provider.applyCorpus(command);
  tx.required(()->{var current=j.queryForObject("SELECT CAST(adoption_pending AS CHAR) FROM corpus_feedback_binding WHERE feedback_id=? FOR UPDATE",String.class,command.get("id"));if(current==null||!write(map(current)).equals(write(body)))return null;j.update("UPDATE corpus_feedback_binding SET adoption_state=?,adoption_version=?,adoption_fingerprint=?,adoption_sequence=?,adoption_pending=NULL WHERE feedback_id=?",receipt.path("state").asText(),receipt.path("version").asText(),body.get("fingerprint"),command.get("revision"),command.get("id"));return null;});
 }
}
