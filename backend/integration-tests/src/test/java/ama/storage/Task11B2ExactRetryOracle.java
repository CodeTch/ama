package ama.storage;

import com.fasterxml.jackson.databind.*;
import java.math.BigInteger;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Complete, stateless accepted-row oracle. No storage, fixture, process or HTTP ownership. */
final class Task11B2ExactRetryOracle {
 static final String PUBLICATION="142a236c-bcd6-4608-8e93-2dd5446c5fa9",OPERATION="896456c1-cc73-4a8c-803d-8b5bb058d4c7",OLD_EXECUTION="0ef15c54-cc24-49f5-b5f8-6b01933118db",OLD_TASK="f0f17320-130e-46b9-908c-c143a02e10f8",KEY="task11-b2-exact-retry-winner-01";
 static final Set<String> TABLES=Collections.unmodifiableSet(new TreeSet<>(List.of("meeting","final_confirmation","final_result_review","publication","publication_submission","external_document_mapping","publication_attempt","durable_task","task_attempt","external_operation","external_execution","idempotency_record","audit_event","task_outbox","task_outbox_sequence","task_subject","meeting_search_projection")));
 static final ObjectMapper json=Task11B2RuntimeFixture.JSON;
 private Task11B2ExactRetryOracle() {}
 static BigInteger integer(Object value) {
  if(value instanceof JsonNode n){assertTrue(n.isIntegralNumber(),"Integral JSON number required");return n.bigIntegerValue();}
  assertTrue(value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long||value instanceof BigInteger,"Integral Java number required");
  return new BigInteger(value.toString());
 }
 static Object canonical(Object value) {
  if(value instanceof JsonNode n){
   if(n.isObject()){var m=new TreeMap<String,Object>();n.fields().forEachRemaining(e->m.put(e.getKey(),canonical(e.getValue())));return m;}
   if(n.isArray()){var l=new ArrayList<Object>();n.forEach(v->l.add(canonical(v)));return l;}
   if(n.isNumber())return canonical(n.numberValue());if(n.isTextual())return n.textValue();if(n.isBoolean())return n.booleanValue();if(n.isNull())return null;throw new AssertionError("Unsupported JSON value");
  }
  if(value instanceof Number)return integer(value);
  if(value instanceof Map<?,?> m){var result=new TreeMap<String,Object>();m.forEach((k,v)->{assertInstanceOf(String.class,k);result.put((String)k,canonical(v));});return result;}
  if(value instanceof List<?> l)return l.stream().map(Task11B2ExactRetryOracle::canonical).toList();
  if(value instanceof java.time.temporal.TemporalAccessor||value instanceof java.util.Date)return value.toString();
  if(value instanceof byte[] bytes)return Base64.getEncoder().encodeToString(bytes);
  assertTrue(value==null||value instanceof String||value instanceof Boolean||value instanceof Set<?>,"Unsupported row value");return value;
 }
 static boolean same(Object expected,Object actual){return Objects.equals(canonical(expected),canonical(actual));}
 static void equal(Object expected,Object actual){assertEquals(canonical(expected),canonical(actual));}
 static BigInteger number(Map<String,Object> row,String key){return integer(row.get(key));}
 static Map<String,Object> byId(List<Map<String,Object>> rows,String field,String id){var found=rows.stream().filter(row->id.equals(row.get(field))).toList();assertEquals(1,found.size(),"Exactly one selected row");return found.getFirst();}
 static String commandKeyHash(String key)throws Exception{byte[] value=key.getBytes(java.nio.charset.StandardCharsets.UTF_8);var bytes=new java.io.ByteArrayOutputStream();var out=new java.io.DataOutputStream(bytes);out.writeInt(value.length);out.write(value);return Task11B2RuntimeFixture.digest(bytes.toByteArray());}
 static Map<String,Object> verify(Map<String,List<Map<String,Object>>> originalRows, Map<String,List<Map<String,Object>>> after, String newTask, String newExecution, JsonNode origin)throws Exception {
  equal(TABLES, originalRows.keySet()); equal(TABLES, after.keySet());
 var delta=Map.of("durable_task",1,"external_execution",1,"publication_submission",1,"idempotency_record",1,"audit_event",1,"task_outbox",2);
  for(String table:TABLES){
   assertEquals(originalRows.get(table).size()+delta.getOrDefault(table,0),after.get(table).size(),table+" exact row delta");
   if(!Set.of("publication","external_operation","task_outbox_sequence").contains(table))for(var old:originalRows.get(table))assertTrue(after.get(table).stream().anyMatch(row->same(old,row)),table+" immutable predecessor");
  }
  var pub=byId(after.get("publication"),"id",PUBLICATION);var expectedPub=new TreeMap<>(byId(originalRows.get("publication"),"id",PUBLICATION));expectedPub.put("state","READY");expectedPub.put("revision",6L);equal(expectedPub,pub);
  var op=byId(after.get("external_operation"),"id",OPERATION);var expectedOp=new TreeMap<>(byId(originalRows.get("external_operation"),"id",OPERATION));expectedOp.put("state","RETRY_QUEUED");expectedOp.put("current_execution_no",2L);expectedOp.put("reconcile_epoch",2L);expectedOp.put("reconcile_owner",null);expectedOp.put("reconcile_expires_at",null);expectedOp.put("last_error",null);equal(expectedOp,op);
  for(String table:List.of("publication","external_operation"))for(var old:originalRows.get(table))if(!Set.of(PUBLICATION,OPERATION).contains(old.get("id")))assertTrue(after.get(table).stream().anyMatch(row->same(old,row)));
  assertEquals(number(originalRows.get("task_outbox_sequence").getFirst(),"next_id").add(BigInteger.TWO),number(after.get("task_outbox_sequence").getFirst(),"next_id"));
  var task=byId(after.get("durable_task"),"id",newTask);assertEquals("READY",task.get("state"));assertEquals(BigInteger.valueOf(0),number(task,"attempt"));assertNull(task.get("lease_owner"));assertNull(task.get("lease_token"));assertNull(task.get("expires_at"));
  var previousTask=byId(originalRows.get("durable_task"),"id",OLD_TASK);for(String field:List.of("task_type","pool","subject_kind","subject_id","input_kind","input_id","input_fingerprint","business_version","cancel_epoch","priority","max_attempts"))equal(previousTask.get(field),task.get(field));assertNotEquals(previousTask.get("producer_key"),task.get("producer_key"));
  var e=byId(after.get("external_execution"),"id",newExecution);assertEquals(OPERATION,e.get("operation_id"));assertEquals(BigInteger.valueOf(2),number(e,"execution_no"));assertEquals(newTask,e.get("task_id"));assertEquals("PREPARED",e.get("state"));for(String field:List.of("marked_at","task_attempt","finished_at","no_effect_basis","no_effect_evidence","no_effect_verified_at"))assertNull(e.get(field));
  var sub=byId(after.get("publication_submission"),"task_id",newTask);assertEquals(BigInteger.valueOf(3),number(sub,"submission_no"));assertEquals("UNDECIDED",sub.get("local_state"));assertEquals(PUBLICATION,sub.get("publication_id"));assertEquals(sub.get("request_manifest"),e.get("request_manifest"));assertEquals(sub.get("execution_request_hash"),e.get("request_hash"));
  var oldManifest=json.readTree((String)byId(originalRows.get("publication_submission"),"task_id",OLD_TASK).get("request_manifest"));var manifest=json.readTree((String)sub.get("request_manifest"));
  Set<String> changing=Set.of("executionNo","submissionNo","executionRequestHash","priorProofs","bindingTransition","acceptanceBinding");assertEquals(oldManifest.size(),manifest.size());for(var fields=oldManifest.fieldNames();fields.hasNext();){String field=fields.next();if(!changing.contains(field))equal(oldManifest.get(field),manifest.get(field));}
  assertEquals(BigInteger.TWO,integer(manifest.get("executionNo")));assertEquals(BigInteger.valueOf(3),integer(manifest.get("submissionNo")));assertNotEquals(oldManifest.get("executionRequestHash"),manifest.get("executionRequestHash"));assertEquals(1,manifest.path("priorProofs").size());var proof=manifest.path("priorProofs").get(0);assertEquals(OLD_EXECUTION,proof.path("executionId").asText());assertEquals(oldManifest.get("executionRequestHash"),proof.get("executionRequestHash"));assertEquals(origin.at("/actual/providerAfter/executions/0/receipt/receiptId"),proof.get("receiptId"));String signed=origin.at("/actual/providerAfter/executions/0/receipt/permanentRejection").asText();assertEquals(Task11B2RuntimeFixture.digest(signed.getBytes(java.nio.charset.StandardCharsets.US_ASCII)),proof.path("proofDigest").asText());assertEquals(manifest.get("priorProofs"),manifest.at("/bindingTransition/priorProofs"));assertEquals(BigInteger.ONE,integer(manifest.at("/bindingTransition/expectedGeneration")));assertEquals(manifest.get("businessKey"),manifest.at("/bindingTransition/newBusinessKey"));assertEquals(manifest.get("logicalRequestHash"),manifest.at("/bindingTransition/newLogicalRequestHash"));
  var receipts=after.get("idempotency_record").stream().filter(row->"PUBLICATION_ZERO_EFFECT_RETRY".equals(row.get("operation"))).toList();assertEquals(1,receipts.size());var receipt=receipts.getFirst();assertEquals(newTask,receipt.get("result_id"));assertEquals(BigInteger.valueOf(202),number(receipt,"http_status"));assertEquals("SUCCEEDED",receipt.get("state"));assertEquals(commandKeyHash(KEY),receipt.get("key_hash"));assertEquals(receipt.get("scope_hash"),manifest.at("/acceptanceBinding/receiptScopeHash").asText());assertEquals(receipt.get("request_hash"),manifest.at("/acceptanceBinding/requestHash").asText());assertEquals("PUBLICATION_ZERO_EFFECT_RETRY",manifest.at("/acceptanceBinding/operation").asText());
  assertTrue(after.get("task_attempt").stream().noneMatch(row->newTask.equals(row.get("task_id"))));assertTrue(after.get("publication_attempt").stream().noneMatch(row->newExecution.equals(row.get("execution_id"))));equal(originalRows.get("external_document_mapping"),after.get("external_document_mapping"));
  return Map.of("task",canonical(task),"submission",canonical(sub),"execution",canonical(e),"operation",canonical(op),"publication",canonical(pub),"receipt",canonical(receipt));
 }
}
