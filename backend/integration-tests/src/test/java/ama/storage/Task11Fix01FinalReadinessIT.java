package ama.storage;
import java.nio.file.*;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class Task11Fix01FinalReadinessIT extends Task11Fix01HistoryValidationIT {
 @Override @Test void genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate()throws Exception {
  token=Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.SecureRandom.getSeed(32));j.update("INSERT INTO identity_session(token_hash,enterprise_id,session_generation,csrf_token,issued_at,expires_at) SELECT ?,enterprise_id,session_generation,?,UTC_TIMESTAMP(6)-INTERVAL 1 MINUTE,UTC_TIMESTAMP(6)+INTERVAL 1 HOUR FROM enterprise_identity WHERE enterprise_id=?",hash(token),csrf,owner);
  String previous=j.queryForObject("SELECT final_confirmation_id FROM meeting WHERE id=?",String.class,meeting);long before=revision();
  var readiness=call("GET","/meetings/"+meeting+"/final-confirmations/readiness","",null);
  var response=call("POST","/meetings/"+meeting+"/final-confirmations",json.writeValueAsString(Map.of("expectedRevisionToken",Long.toString(before),"optionalReviewIds",List.of())),"fix01-closed-history-final");
  Files.writeString(receipt.resolve("closed-history-final-http.json"),json.writeValueAsString(Map.of("readinessStatus",readiness.statusCode(),"readiness",readiness.body(),"status",response.statusCode(),"body",response.body(),"previousFinal",previous,"beforeRevision",before)));
  assertEquals(201,response.statusCode(),response.body());assertEquals(200,readiness.statusCode());assertTrue(body(readiness).get("ready").asBoolean());assertNotEquals(previous,body(response).get("resultId").asText());assertEquals(before+1,revision());
 }
}
