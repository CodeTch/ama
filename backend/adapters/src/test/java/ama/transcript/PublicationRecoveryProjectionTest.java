package ama.transcript;
import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class PublicationRecoveryProjectionTest {
 final Task11PublicationExactRetryTest fixture=new Task11PublicationExactRetryTest();
 Map<String,Object> root(){return new HashMap<>(Map.of("id",Task11PublicationExactRetryTest.MEETING,"ever_published",0L,"lifecycle","ACTIVE","governance_state","ACTIVE","final_confirmation_id",Task11PublicationExactRetryTest.FINAL,"revision",13L));}
 List<?> actions(Map<String,Object> root,Map<String,List<Map<String,Object>>> rows,boolean owner){return (List<?>)PublicationReadProjection.status(root,fixture.header(),rows,owner).get("allowedActions");}
 @Test void authenticatedClosedFailureOffersExactRetryAndReconfiguration(){var actions=actions(root(),fixture.rows(),true);assertTrue(actions.contains("RETRY_ZERO_EFFECT"));assertTrue(actions.contains("RESUBMIT"));}
 @Test void jdbcUnsignedRootRevisionAndNormalizedTaskRevisionCompareByExactValue(){for(Number revision:List.<Number>of(13,13L,new java.math.BigInteger("13"),new java.math.BigDecimal("13.0"))){var root=root();root.put("revision",revision);assertTrue(actions(root,fixture.rows(),true).contains("RETRY_ZERO_EFFECT"),revision.getClass().getName());}for(Number revision:List.<Number>of(14L,new java.math.BigDecimal("13.5"),new java.math.BigInteger("18446744073709551629"))){var root=root();root.put("revision",revision);assertFalse(actions(root,fixture.rows(),true).contains("RETRY_ZERO_EFFECT"));}}
 @Test void unknownSuccessManualAndNonOwnerNeverReceiveNewSendActions(){
  for(String state:List.of("UNKNOWN","SUCCEEDED","MANUAL_REQUIRED")){var rows=fixture.rows();rows.get("external_operation").getFirst().put("state",state);var actions=actions(root(),rows,true);assertFalse(actions.contains("RETRY_ZERO_EFFECT"));assertFalse(actions.contains("RESUBMIT"));}
  assertFalse(actions(root(),fixture.rows(),false).contains("RETRY_ZERO_EFFECT"));
 }
 @Test void changedContentCannotUseExactRetry(){var root=root();root.put("final_confirmation_id",null);root.put("revision",14L);var actions=actions(root,fixture.rows(),true);assertFalse(actions.contains("RETRY_ZERO_EFFECT"));assertTrue(actions.contains("RESUBMIT"));}
 @Test void localTerminalNeverSentTaskOffersLocalClosureBeforeResubmission(){
  var rows=fixture.rows();for(String key:List.of("external_execution","external_operation","publication_attempt","task_attempt"))rows.put(key,List.of());rows.get("durable_task").getFirst().put("attempt",0L);
  var actions=actions(root(),rows,true);assertTrue(actions.contains("RECOVER_LOCAL"));assertFalse(actions.contains("RESUBMIT"));assertFalse(actions.contains("RETRY_ZERO_EFFECT"));
 }
}
