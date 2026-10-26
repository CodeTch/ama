package ama.storage;
import com.fasterxml.jackson.core.type.TypeReference;import com.fasterxml.jackson.databind.*;import java.nio.file.*;import java.util.*;import java.util.function.Consumer;import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;
/** Synthetic complete graph derived from frozen public rows; gives no SQL, actor or mutation authority. */
class Task11B3LocalOracleTest {
 static final String M="36db643e-5fbd-4f9a-9a85-a7e6b7d76e38",P="142a236c-bcd6-4608-8e93-2dd5446c5fa9",T="3588aaba-ecfa-4889-8954-46c1454447d4",E="65fa6b21-22da-4a2e-a886-341b16978106",O="896456c1-cc73-4a8c-803d-8b5bb058d4c7",A="task11b2-owner-43879ba2-aec0-47b2-90c6-9c79337baf88",K="task11-b3-prepared-local-decision-01",TIME="2026-10-26 04:40:00.000001";
 final ObjectMapper json=new ObjectMapper();Map<String,List<Map<String,Object>>> before,after;Map<String,List<String>> keys;
 @BeforeEach void graph()throws Exception {
  Path root=Path.of(System.getenv("AMA_TASK11_REPOSITORY_ROOT"));var raw=json.readTree(Files.readAllBytes(root.resolve("artifacts/phase-b/b-remaining/task-11/b2-exact-retry-readback-01/accepted-rows-after.json")));before=json.convertValue(raw.path("rows"),new TypeReference<>(){});before.put("model_capacity",new ArrayList<>(List.of(new HashMap<>(Map.of("model","synthetic","capacity",1)))));before.put("model_reservation",new ArrayList<>());after=copy(before);keys=new HashMap<>();
  for(var entry:before.entrySet()){String table=entry.getKey();keys.put(table,switch(table){case "meeting","publication","durable_task","external_operation","external_execution","audit_event","task_outbox_sequence"->List.of("id");case "publication_submission"->List.of("task_id");case "idempotency_record"->List.of("scope_hash");case "task_outbox"->List.of("sequence_id");default->entry.getValue().isEmpty()?List.of("id"):new ArrayList<>(entry.getValue().getFirst().keySet());});}
  row(after,"durable_task","id",T).putAll(Map.of("state","FAILED","last_error","EXPIRED_UNMARKED_ABANDONED"));
  row(after,"meeting","id",M).putAll(Map.of("stage","PUBLISH_FAILED","updated_at",TIME));row(after,"publication","id",P).putAll(Map.of("state","FAILED","revision",7));
  var op=row(after,"external_operation","id",O);op.putAll(Map.of("state","FAILED","last_error","LOCAL_NEVER_MARKED","reconcile_epoch",Task11B2ExactRetryOracle.integer(op.get("reconcile_epoch")).add(java.math.BigInteger.ONE)));op.put("reconcile_owner",null);op.put("reconcile_expires_at",null);
  row(after,"external_execution","id",E).putAll(Map.of("state","FAILED","finished_at",TIME,"no_effect_basis","NOT_SENT","no_effect_evidence","publication-local:"+T,"no_effect_verified_at",TIME));
  row(after,"publication_submission","task_id",T).putAll(Map.of("local_state","NEVER_MARKED","local_decided_at",TIME,"local_reason","EXPIRED_UNMARKED_ABANDONED","local_decided_by",A,"local_attempt_no",0,"local_execution_id",E));
  var audit=new HashMap<>(before.get("audit_event").getFirst());audit.putAll(Map.of("id",7001,"action","PUBLICATION_LOCAL_NEVER_MARKED","actor_id",A,"resource_id",M,"revision",13,"result","SUCCEEDED","trace_id","00000000-0000-4000-8000-000000007002","occurred_at",TIME));after.get("audit_event").add(audit);
  var receipt=new HashMap<>(before.get("idempotency_record").getFirst());receipt.putAll(Map.of("scope_hash","019f656a951acd0c795229e4fbdea81d4816b589c59175d9ef85ab617f21e29d","request_hash","27dc5c0ffda8bd65938ac17590a7d290301119368bee6b77bd9cb6c94eebc1a4","key_hash","69364aa652019acf5f27ef7b18b20d987868aca8411e0b9710aa2aef8527b97a","operation","PUBLICATION_LOCAL_RECOVERY","actor_id",A,"resource_id",M,"result_id",T,"http_status",200,"state","SUCCEEDED","created_at",TIME));after.get("idempotency_record").add(receipt);
  var next=Task11B2ExactRetryOracle.integer(before.get("task_outbox_sequence").getFirst().get("next_id"));after.get("task_outbox_sequence").getFirst().put("next_id",next.add(java.math.BigInteger.TWO));
  for(String type:List.of("TASK_FAILED","PUBLICATION_LOCAL_RECOVERED")){var event=new HashMap<>(before.get("task_outbox").getFirst());event.putAll(Map.of("sequence_id",next,"event_type",type,"task_id",T,"subject_kind","MEETING","subject_id",M,"subject_revision",13,"reference_kind","PUBLICATION","reference_id",P,"occurred_at",TIME));after.get("task_outbox").add(event);next=next.add(java.math.BigInteger.ONE);}
 }
 Map<String,List<Map<String,Object>>> copy(Map<String,List<Map<String,Object>>> rows){return json.convertValue(rows,new TypeReference<>(){});}
 static Map<String,Object> row(Map<String,List<Map<String,Object>>> rows,String table,String field,String id){return rows.get(table).stream().filter(r->id.equals(r.get(field))).findFirst().orElseThrow();}
 void verify(Map<String,List<Map<String,Object>>> value)throws Exception {Task11B3LocalOracle.verify(before,value,keys,M,P,T,E,O,A,K);}
 @Test void acceptsOnlyTheCompleteExpectedLocalDecisionDelta(){assertDoesNotThrow(()->verify(after));}
 @Test void acceptsRealUnsignedAuditIdsWithoutAssumingContiguousAllocation()throws Exception {
  for(Object id:List.of(7001,7001L,new java.math.BigInteger("7001"),new java.math.BigInteger("9223372036854775808"),new java.math.BigInteger("18446744073709551615"))){var graph=copy(after);graph.get("audit_event").getLast().put("id",id);assertDoesNotThrow(()->verify(graph));}
 }
 @Test void rejectsInvalidAuditIdTypesRangesAndReusedHistoryIdentity()throws Exception {
  for(Object id:List.of("00000000-0000-4000-8000-000000007001","7001",0,-1,new java.math.BigInteger("18446744073709551616"),7001.0,new java.math.BigDecimal("7001.0"),before.get("audit_event").getFirst().get("id"))){var graph=copy(after);graph.get("audit_event").getLast().put("id",id);assertThrows(AssertionError.class,()->verify(graph),"invalid audit id "+id);}
  var graph=copy(after);graph.get("audit_event").getFirst().put("action","HISTORY_CHANGED");assertThrows(AssertionError.class,()->verify(graph));
 }
 @Test void rejectsIndependentBusinessAndHistoryCorruptions()throws Exception {
  List<Consumer<Map<String,List<Map<String,Object>>>>> corruptions=List.of(
   r->row(r,"durable_task","id",T).put("producer_key","wrong"),r->row(r,"durable_task","id",T).put("attempt",1),
   r->row(r,"external_execution","id",E).put("marked_at",TIME),r->row(r,"external_execution","id",E).put("no_effect_basis","DEFINITIVE_REJECTION"),
   r->row(r,"external_operation","id",O).put("state","UNKNOWN"),r->row(r,"publication_submission","task_id",T).put("local_execution_id",null),
   r->row(r,"meeting","id",M).put("revision",14),r->row(r,"meeting","id",M).put("ever_published",1),r->row(r,"publication","id",P).put("revision",8),
   r->r.get("external_document_mapping").add(new HashMap<>(Map.of("publication_id",P))),
   r->r.get("idempotency_record").getLast().put("actor_id","wrong"),r->r.get("idempotency_record").getLast().put("request_hash","0".repeat(64)),
   r->r.get("task_outbox").add(new HashMap<>(r.get("task_outbox").getLast())),r->r.get("task_outbox_sequence").getFirst().put("next_id",9999),
   r->r.get("model_capacity").getFirst().put("capacity",2),r->r.get("audit_event").getLast().put("action","WRONG"),r->r.get("task_outbox").getLast().put("reference_id","wrong"));
  int i=0;for(var corrupt:corruptions){var value=copy(after);corrupt.accept(value);assertThrows(AssertionError.class,()->verify(value),"corruption "+i++);}
 }
}