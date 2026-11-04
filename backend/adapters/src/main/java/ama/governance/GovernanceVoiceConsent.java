package ama.governance;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.function.BiFunction;
/** Current external authorization is checked before using a meeting-derived feature. */
public final class GovernanceVoiceConsent {
 private static volatile BiFunction<String,String,JsonNode> authority;
 public static void configure(BiFunction<String,String,JsonNode> value){authority=java.util.Objects.requireNonNull(value);}
 public static boolean current(JdbcTemplate j,String voice){
  var rows=j.queryForList("SELECT subject_id,meeting_id,consent_reference,policy_version FROM meeting_voiceprint_application WHERE id=?",voice);if(rows.isEmpty())return true;
  var provider=authority;if(provider==null)return false;var row=rows.getFirst();boolean valid;
  try{var consent=provider.apply((String)row.get("subject_id"),(String)row.get("meeting_id"));valid=row.get("consent_reference").equals(consent.path("reference").asText())&&row.get("policy_version").equals(consent.path("samplePolicy").path("version").asText())&&Instant.parse(consent.path("expiresAt").asText()).isAfter(Instant.now());}
  catch(IllegalArgumentException denied){valid=false;}catch(RuntimeException unavailable){return false;}
  if(!valid){j.update("UPDATE governed_voiceprint SET state='REVOKED',encrypted_embedding=NULL,revision=revision+1,review_reason='对应人员授权已撤销或失效',updated_at=UTC_TIMESTAMP(6) WHERE id=? AND state<>'REVOKED'",voice);j.update("UPDATE meeting_voiceprint_application SET encrypted_embedding=NULL WHERE id=?",voice);}return valid;
 }
 private GovernanceVoiceConsent(){}
}
