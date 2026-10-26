package ama.storage;

import com.fasterxml.jackson.databind.*;
import java.math.*;import java.util.*;import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static ama.storage.Task11B2ExactRetryOracle.*;

/** Pure synthetic row oracle regressions. No environment, storage, process or runtime fixture. */
class Task11B2ExactRetryOracleTest {
 static final String TASK="new-task",EXEC="new-execution";
 @Test void equalIntegralClassesAndSavedJsonRows()throws Exception {
  var jdbc=Map.of("revision",BigInteger.valueOf(6),"nested",List.of(Map.of("attempt",0L)));
  var saved=json.readValue(json.writeValueAsString(jdbc),Map.class);
  equal(jdbc,saved);equal(Map.of("revision",6L),Map.of("revision",BigInteger.valueOf(6)));
  assertTrue(same(List.of(jdbc),List.of(saved)));
 }
 @Test void unsignedMaximumAndExactArithmetic(){var max=new BigInteger("18446744073709551615");equal(max,json.valueToTree(max));assertEquals(max,integer(max));assertEquals(max.add(BigInteger.TWO),integer(max).add(BigInteger.TWO));assertFalse(same(max,-1L));}
 @Test void rejectsDecimalsFloatsAndStringsAsNumbers(){for(Object v:List.of(new BigDecimal("6.0"),new BigDecimal("6.5"),6.0d,6.5f,"6",json.valueToTree(6.0d)))assertThrows(AssertionError.class,()->integer(v));for(Object v:List.of(new BigDecimal("6.0"),6.5d))assertThrows(AssertionError.class,()->canonical(Map.of("x",v)));assertFalse(same("6",6));}
 @Test void preservesStructureNullStringsAndOrder(){assertFalse(same(Map.of("x",1),Map.of("y",1)));assertFalse(same(List.of(1,2),List.of(2,1)));assertFalse(same("READY","FAILED"));var n=new HashMap<String,Object>();n.put("x",null);assertFalse(same(n,Map.of()));equal(java.sql.Timestamp.valueOf("2026-10-26 01:00:00.123456"),"2026-10-26 01:00:00.123456");equal(new byte[]{1,2},"AQI=");}
 @Test void completeAcceptedGraphSupportsSavedPreimageAndMixedJdbcNumbers()throws Exception {var f=new Graph();verify(f.before,f.after,TASK,EXEC,f.origin);}
 @Test void completeOracleRejectsChangedInvariants()throws Exception {
  List<Consumer<Graph>> mutations=List.of(
   f->f.after.get("meeting").getFirst().put("revision",14),
   f->f.after.get("durable_task").removeLast(),
   f->f.row("publication",PUBLICATION).put("revision",7),
   f->f.row("external_operation",OPERATION).put("state","FAILED"),
   f->f.after.get("task_outbox_sequence").getFirst().put("next_id",15),
   f->f.row("durable_task",TASK).put("attempt",1),
   f->f.row("durable_task",TASK).put("lease_token","claimed"),
   f->f.row("durable_task",TASK).put("input_fingerprint","changed"),
   f->f.row("external_execution",EXEC).put("state","SENDING"),
   f->f.row("external_execution",EXEC).put("marked_at","now"),
   f->f.after.get("publication_submission").getLast().put("local_state","FAILED"),
   f->f.manifest("fixed",json.valueToTree("changed")),
   f->f.manifest("executionNo",json.valueToTree(2.5)),
   f->f.manifest("submissionNo",json.valueToTree("3")),
   f->f.manifest("priorProofs",json.createArrayNode()),
   f->f.manifest("acceptanceBinding",json.createObjectNode()),
   f->f.after.get("idempotency_record").getFirst().put("state","RUNNING"),
   f->f.after.get("idempotency_record").getFirst().put("key_hash","wrong"),
   f->f.after.get("task_attempt").add(map("task_id",TASK)),
   f->f.after.get("publication_attempt").add(map("execution_id",EXEC)),
   f->f.after.get("external_document_mapping").add(map("id","unexpected"))
  );
  for(int i=0;i<mutations.size();i++){var f=new Graph();verify(f.before,f.after,TASK,EXEC,f.origin);mutations.get(i).accept(f);assertThrows(AssertionError.class,()->verify(f.before,f.after,TASK,EXEC,f.origin),"mutation "+i);}
 }
 static Map<String,Object> map(Object... pairs){var m=new TreeMap<String,Object>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;}
 static class Graph {
  Map<String,List<Map<String,Object>>> before=new TreeMap<>(),after=new TreeMap<>();JsonNode origin;
  Graph()throws Exception {
   for(String table:TABLES)before.put(table,new ArrayList<>());
   before.get("meeting").add(map("id","meeting","revision",13));
   before.get("publication").add(map("id",PUBLICATION,"state","FAILED","revision",5));
   before.get("external_operation").add(map("id",OPERATION,"state","FAILED","current_execution_no",1,"reconcile_epoch",1,"reconcile_owner",null,"reconcile_expires_at",null,"last_error","denied"));
   before.get("task_outbox_sequence").add(map("next_id",10));
   var task=map("id",OLD_TASK,"state","FAILED","attempt",1,"lease_owner",null,"lease_token",null,"expires_at",null,"producer_key","old");
   for(String field:List.of("task_type","pool","subject_kind","subject_id","input_kind","input_id","input_fingerprint"))task.put(field,field+"-fixed");
   for(String field:List.of("business_version","cancel_epoch","priority","max_attempts"))task.put(field,3);
   before.get("durable_task").add(task);
   var oldManifest=map("executionNo",1,"submissionNo",2,"executionRequestHash","old-hash","priorProofs",List.of(),"bindingTransition",Map.of(),"acceptanceBinding",Map.of(),"businessKey","business","logicalRequestHash","logical","fixed",17);
   before.get("publication_submission").add(map("task_id",OLD_TASK,"request_manifest",json.writeValueAsString(oldManifest)));
   before.get("external_execution").add(map("id",OLD_EXECUTION,"state","FAILED"));
   for(var entry:before.entrySet()){var list=new ArrayList<Map<String,Object>>();for(var row:entry.getValue()){var copy=new TreeMap<String,Object>();row.forEach((k,v)->copy.put(k,v instanceof Number?new BigInteger(v.toString()):v));list.add(copy);}after.put(entry.getKey(),list);}
   row("publication",PUBLICATION).put("state","READY");row("publication",PUBLICATION).put("revision",BigInteger.valueOf(6));
   var op=row("external_operation",OPERATION);op.put("state","RETRY_QUEUED");op.put("current_execution_no",BigInteger.TWO);op.put("reconcile_epoch",BigInteger.TWO);op.put("last_error",null);
   after.get("task_outbox_sequence").getFirst().put("next_id",BigInteger.valueOf(12));
   var nt=new TreeMap<>(after.get("durable_task").getFirst());nt.put("id",TASK);nt.put("state","READY");nt.put("attempt",BigInteger.ZERO);nt.put("producer_key","new");after.get("durable_task").add(nt);
   String signed="synthetic-signed-proof";origin=json.valueToTree(Map.of("actual",Map.of("providerAfter",Map.of("executions",List.of(Map.of("receipt",Map.of("receiptId","receipt","permanentRejection",signed)))))));
   var proof=Map.of("executionId",OLD_EXECUTION,"executionRequestHash","old-hash","receiptId","receipt","proofDigest",Task11B2RuntimeFixture.digest(signed.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
   var manifest=new TreeMap<>(oldManifest);manifest.put("executionNo",2);manifest.put("submissionNo",3);manifest.put("executionRequestHash","new-hash");manifest.put("priorProofs",List.of(proof));manifest.put("bindingTransition",Map.of("priorProofs",List.of(proof),"expectedGeneration",1,"newBusinessKey","business","newLogicalRequestHash","logical"));manifest.put("acceptanceBinding",Map.of("receiptScopeHash","scope","requestHash","request","operation","PUBLICATION_ZERO_EFFECT_RETRY"));String encoded=json.writeValueAsString(manifest);
   after.get("external_execution").add(map("id",EXEC,"operation_id",OPERATION,"execution_no",BigInteger.TWO,"task_id",TASK,"state","PREPARED","marked_at",null,"task_attempt",null,"finished_at",null,"no_effect_basis",null,"no_effect_evidence",null,"no_effect_verified_at",null,"request_manifest",encoded,"request_hash","new-hash"));
   after.get("publication_submission").add(map("task_id",TASK,"submission_no",BigInteger.valueOf(3),"local_state","UNDECIDED","publication_id",PUBLICATION,"request_manifest",encoded,"execution_request_hash","new-hash"));
   after.get("idempotency_record").add(map("operation","PUBLICATION_ZERO_EFFECT_RETRY","result_id",TASK,"http_status",BigInteger.valueOf(202),"state","SUCCEEDED","key_hash",commandKeyHash(KEY),"scope_hash","scope","request_hash","request"));
   after.get("audit_event").add(map("id","audit"));after.get("task_outbox").add(map("id",11));after.get("task_outbox").add(map("id",12));
  }
  Map<String,Object> row(String table,String id){return byId(after.get(table),"id",id);}
  void manifest(String field,JsonNode value){try{var sub=after.get("publication_submission").getLast();var m=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree((String)sub.get("request_manifest"));m.set(field,value);String text=json.writeValueAsString(m);sub.put("request_manifest",text);row("external_execution",EXEC).put("request_manifest",text);}catch(Exception e){throw new AssertionError(e);}}
 }
}
