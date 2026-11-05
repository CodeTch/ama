package ama.governance;
import org.springframework.jdbc.core.JdbcTemplate;
final class GovernanceVoiceSamples {
 static void record(JdbcTemplate j,String id,long revision,String origin,String actor){String table=origin.equals("SELF")?"governed_voiceprint":"meeting_voiceprint_application";j.update("INSERT INTO governance_voice_sample_revision(voiceprint_id,revision,origin,sample_sha,sample_count,model_version,dimension,consent_reference,expires_at,actor_id) SELECT id,?,?,sample_sha,sample_count,model_version,dimension,consent_reference,expires_at,? FROM "+table+" WHERE id=?",revision,origin,actor,id);}
 private GovernanceVoiceSamples(){}
}
