package ama.governance;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
/** Review is a decision; use is a separate, current fact. */
public final class GovernanceUsage {
 private GovernanceUsage(){}
 public static Map<String,Object> feedback(JdbcTemplate j,String id,String actor){
  var rows=j.queryForList("SELECT CASE WHEN b.feedback_id IS NULL OR f.feedback_type IS NULL THEN 'LEGACY' WHEN b.withdrawn THEN 'WITHDRAWN' WHEN f.state<>'ACCEPTED' THEN 'NOT_APPLIED' WHEN m.lifecycle<>'ACTIVE' OR m.governance_state<>'ACTIVE' OR m.revision<>b.source_revision OR i.status<>'ACTIVE' OR m.owner_id<>f.submitted_by THEN 'INVALID_SOURCE' WHEN b.adoption_state='ADOPTED' THEN 'EFFECTIVE' ELSE 'PENDING_ADOPTION' END usageState,b.adoption_version usageVersion,(b.feedback_id IS NOT NULL AND b.withdrawn=FALSE AND f.submitted_by=?) canWithdraw,b.withdraw_reason withdrawalReason,DATE_FORMAT(b.withdrawn_at,'%Y-%m-%dT%H:%i:%s.%fZ') withdrawnAt FROM feedback f JOIN meeting m ON m.id=f.meeting_id JOIN enterprise_identity i ON i.enterprise_id=f.submitted_by LEFT JOIN corpus_feedback_binding b ON b.feedback_id=f.id WHERE f.id=?",actor,id);if(rows.size()!=1)throw new IllegalArgumentException("Feedback unavailable");var r=rows.getFirst();r.put("canWithdraw",Boolean.TRUE.equals(r.get("canWithdraw"))||r.get("canWithdraw") instanceof Number n&&n.intValue()==1);return r;
 }
 public static Map<String,Object> voice(JdbcTemplate j,String id){
  var r=j.queryForMap("SELECT CASE WHEN v.state='REVOKED' THEN 'REVOKED' WHEN m.lifecycle<>'ACTIVE' OR m.governance_state<>'ACTIVE' THEN 'INVALID_SOURCE' WHEN a.expires_at<=UTC_TIMESTAMP(6) THEN 'EXPIRED' WHEN i.status<>'ACTIVE' THEN 'DISABLED' WHEN a.state<>'ACCEPTED' OR v.id IS NULL THEN 'NOT_APPLIED' WHEN a.model_version<>JSON_UNQUOTE((SELECT JSON_EXTRACT(config_json,'$.modelVersion') FROM governance_configuration ORDER BY revision DESC LIMIT 1)) THEN 'INCOMPATIBLE' WHEN JSON_TYPE((SELECT JSON_EXTRACT(config_json,'$.voiceThreshold') FROM governance_configuration ORDER BY revision DESC LIMIT 1))='NULL' THEN 'MATCHING_DISABLED' ELSE 'EFFECTIVE' END usageState,a.model_version usageVersion FROM meeting_voiceprint_application a JOIN meeting m ON m.id=a.meeting_id JOIN enterprise_identity i ON i.enterprise_id=a.subject_id LEFT JOIN governed_voiceprint v ON v.id=a.id WHERE a.id=?",id);r.put("canWithdraw",false);r.put("withdrawalReason",null);r.put("withdrawnAt",null);return r;
 }
}
