package ama.storage;
import java.nio.file.*;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class Task05Fix02SuccessRegressionIT extends Task05Fix02Fixture {
 void actualNativeHttpIntakeWorkerAndRawRegression()throws Exception {
  var first=upload("\ufeffAlpha😀\r\n \r\nBeta");var second=upload("Gamma\nDelta");
  var accepted=call("POST","/meetings/"+meeting+"/transcript-imports",json.writeValueAsString(Map.of("expectedRevisionToken","0","orderedFileIds",List.of(first,second))),"import");
  assertEquals(202,accepted.statusCode(),accepted.body());String task=body(accepted).get("taskId").asText();Files.writeString(receipt.resolve("accepted-"+task+".json"),json.writeValueAsString(Map.of("task",task,"owner",owner,"meeting",meeting,"httpStatus",accepted.statusCode())));
  assertEquals("READY",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task));
  var queue=app.getBean(ama.task.JdbcTaskQueue.class);
  try(var loop=ama.worker.WorkerLoop.contextual(queue,Map.of("TRANSCRIPT_IMPORT",1),Map.of("TRANSCRIPT_IMPORT_V1",(lease,cancel)->ama.task.TranscriptImportRuntime.prepare(queue,lease,settings(),cancel)),"TASK05_ACTUAL")){
   loop.start();long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
   while(System.nanoTime()<end&&List.of("READY","RUNNING").contains(j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task)))Thread.sleep(50);
  }
  var actual=j.queryForMap("SELECT state,output_kind,output_id,last_error,business_version FROM durable_task WHERE id=?",task);Files.writeString(receipt.resolve("task.json"),json.writeValueAsString(actual));
  assertEquals("SUCCEEDED",actual.get("state"),actual.toString());String raw=(String)actual.get("output_id");
  assertEquals("TRANSCRIPT_REVISION",actual.get("output_kind"));assertEquals(List.of("Alpha😀","Beta","Gamma","Delta"),j.queryForList("SELECT body FROM transcript_segment WHERE revision_id=? ORDER BY ordinal",String.class,raw));
  assertEquals(List.of(1L,3L,1L,2L),j.queryForList("SELECT source_paragraph FROM transcript_segment WHERE revision_id=? ORDER BY ordinal",Long.class,raw));
  assertEquals(((Number)actual.get("business_version")).longValue(),j.queryForObject("SELECT revision FROM transcript_revision WHERE id=?",Long.class,raw));
  assertNull(j.queryForObject("SELECT transcript_revision_id FROM meeting WHERE id=?",String.class,meeting));
 }
 @Test void actualS3HttpIntakeWorkerAndRawRegression()throws Exception {actualNativeHttpIntakeWorkerAndRawRegression();}
}
