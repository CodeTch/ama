package ama.transcript;
import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class Task11PublicationProjectionTest {
 @SuppressWarnings("unchecked") Map<String,Object> view(String operation,String execution,String mapping,boolean owner)throws Exception {
  var type=assertDoesNotThrow(()->Class.forName("ama.transcript.PublicationReadProjection"),"Missing bounded public publication projection");
  var rows=new HashMap<String,List<Map<String,Object>>>();for(String name:List.of("publication_submission","durable_task","external_operation","external_execution","publication_attempt","external_document_mapping"))rows.put(name,List.of());
  rows.put("publication_submission",List.of(Map.of("task_id","task","submission_no",1L)));rows.put("external_operation",List.of(Map.of("id","operation","state",operation)));rows.put("external_execution",List.of(Map.of("id","execution","operation_id","operation","state",execution,"execution_no",1L,"task_id","task")));
  if(mapping!=null)rows.put("external_document_mapping",List.of(Map.of("authority_state",mapping)));
  var method=type.getDeclaredMethod("status",Map.class,Map.class,Map.class,boolean.class);method.setAccessible(true);
  return (Map<String,Object>)method.invoke(null,Map.of("ever_published",0L,"final_confirmation_id","final","revision",13L),Map.of("id","publication","state",mapping==null?"RUNNING":"SUCCEEDED","revision",2L,"confirmation_id","final"),rows,owner);
 }
 @Test void factualSuccessWithoutMappingIsTerminalManualAndNeverLookupPending()throws Exception {
  var result=view("SUCCEEDED","SUCCEEDED",null,true);assertEquals("SUCCESS",result.get("remoteOutcome"));assertEquals("FENCED_MANUAL",result.get("applicationOutcome"));assertEquals(true,result.get("manualLocked"));assertEquals(false,result.get("lookupPending"));assertEquals("RUNNING",result.get("rawState"));assertFalse(result.containsKey("documentUrl"));
 }
 @Test void unknownIsQueryOnlyAndMemberGetsNoPrivateExecutionState()throws Exception {
  var result=view("UNKNOWN","UNKNOWN",null,false);assertEquals("UNKNOWN",result.get("remoteOutcome"));assertEquals(true,result.get("lookupPending"));assertFalse(result.containsKey("executionId"));assertFalse(((List<?>)result.get("allowedActions")).contains("RETRY_ZERO_EFFECT"));
 }
 @Test void manualOperationNeverPromisesFurtherLookup()throws Exception {var result=view("MANUAL_REQUIRED","PREPARED",null,true);assertEquals(true,result.get("manualLocked"));assertEquals(false,result.get("lookupPending"));}
 @Test void memberFinalRequiresEveryEffectiveAssociation()throws Exception {
  var method=assertDoesNotThrow(()->JdbcMinutesFactsReader.class.getDeclaredMethod("requireEffectiveFinal",Map.class,Map.class));method.setAccessible(true);
  var root=new HashMap<String,Object>();var frozen=new HashMap<String,Object>();root.put("final_confirmation_id","final");frozen.put("id","final");
  for(String k:List.of("info_revision_id","roster_revision_id","transcript_confirmation_id","minutes_revision_id","material_snapshot_id","element_set_id","template_revision_id","exclusion_revision_id")){root.put(k,k);frozen.put(k,k);}
  assertDoesNotThrow(()->method.invoke(null,root,frozen));for(String k:List.of("final_confirmation_id","minutes_revision_id","element_set_id","roster_revision_id")){var wrong=new HashMap<String,Object>(root);wrong.put(k,"other");assertThrows(java.lang.reflect.InvocationTargetException.class,()->method.invoke(null,wrong,frozen));}
 }

 @Test void jdbcTemporalMetadataUsesStableStringsBeforeResponseEncoding()throws Exception {
  var method=assertDoesNotThrow(()->JdbcPublicationQueries.class.getDeclaredMethod("normalizeMetadata",Object.class));method.setAccessible(true);var time=java.time.LocalDateTime.of(2026,10,25,12,34,56,123456000);assertEquals(time.toString(),method.invoke(null,time));assertEquals(time.toString(),method.invoke(null,java.sql.Timestamp.valueOf(time)));assertEquals("2026-10-25",method.invoke(null,time.toLocalDate()));assertEquals(13L,method.invoke(null,new java.math.BigDecimal("13")));assertNull(method.invoke(null,new Object[]{null}));
 }

 @Test void ownerGetsOnlyActualCurrentExecutionAssociation()throws Exception {var result=view("MARKED","MARKED",null,true);assertEquals("execution",result.get("executionId"));assertEquals("1",result.get("executionNoToken"));assertEquals("operation",result.get("operationId"));}
 @Test void withdrawnMappingRetainsAppliedHistoryWithoutInventingManualFailure()throws Exception {var result=view("SUCCEEDED","SUCCEEDED","WITHDRAWN",true);assertEquals("APPLIED",result.get("applicationOutcome"));assertEquals(false,result.get("manualLocked"));assertNull(result.get("officialDocumentUrl"));}
 @SuppressWarnings("unchecked") @Test void attemptSeparatesRawOutcomeFromTerminalRemoteAndApplication()throws Exception {var method=JdbcPublicationQueries.class.getDeclaredMethod("attemptView",Map.class,Map.class);method.setAccessible(true);var attempt=new HashMap<String,Object>();for(String key:List.of("id","confirmation_id","operation_id","execution_id"))attempt.put(key,key);attempt.put("kind","PUBLISH");attempt.put("outcome","PENDING");attempt.put("created_at","2026-10-25T12:00");attempt.put("finished_at",null);var rows=Map.of("external_execution",List.of(Map.<String,Object>of("id","execution_id","operation_id","operation_id","state","SUCCEEDED")),"external_operation",List.of(Map.<String,Object>of("id","operation_id","state","SUCCEEDED")));var result=(Map<String,Object>)method.invoke(null,attempt,rows);assertEquals("PENDING",result.get("rawOutcome"));assertEquals("SUCCESS",result.get("remoteOutcome"));assertEquals("FENCED_MANUAL",result.get("applicationOutcome"));assertEquals(false,result.get("lookupPending"));}

}
