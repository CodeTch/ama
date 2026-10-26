package ama.storage;
import java.nio.file.*;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class Task11Fix01ChangedDestinationIT extends Task11Fix01HistoryValidationIT {
 final Task11Fix01SqlTrace trace=new Task11Fix01SqlTrace();
 @Override javax.sql.DataSource createSource(){return trace.wrap(super.createSource());}
 @Override @Test void genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate()throws Exception {
  var history=json.readTree(Files.readAllBytes(previousPipeline().resolve("fix01-isolated-history.json")));String publication=history.get("original").get("publicationId").asText(),old=history.get("original").get("taskId").asText(),confirmation=history.get("original").get("confirmationId").asText();
  knowledge.createFolder("controlled-fix01-folder");knowledge.grant(owner,"controlled-fix01-folder");var service=ama.api.Task11RuntimeProbe.publication(app);
  var accepted=service.resubmit(actor(),meeting,publication,"fix01-changed-destination",revision(),j.queryForObject("SELECT revision FROM publication WHERE id=?",Long.class,publication),confirmation,"CONTROLLED_KNOWLEDGE_V1","controlled-library","controlled-fix01-folder",UUID.randomUUID().toString());
  String task=(String)accepted.get("taskId");assertEquals("3",accepted.get("submissionNoToken"));
  assertNotEquals(j.queryForObject("SELECT logical_request_hash FROM publication_submission WHERE task_id=?",String.class,old),j.queryForObject("SELECT logical_request_hash FROM publication_submission WHERE task_id=?",String.class,task));
  assertEquals(ama.contract.ExternalContracts.ObservationResult.APPLIED,service.recoverOriginalReceipt(actor(),meeting,publication,old));
  var validation=ama.transcript.Task11Fix01HistoryProbe.verify(service,meeting,task);
  assertEquals(List.of(task),j.queryForList("SELECT id FROM durable_task WHERE pool='KNOWLEDGE_PUBLISH' AND state IN ('READY','RUNNING') ORDER BY id",String.class));
  var queue=app.getBean(ama.task.JdbcTaskQueue.class);var lease=queue.claim("KNOWLEDGE_PUBLISH","FIX01_CHANGED_GAP").orElseThrow();assertEquals(task,lease.taskId());service.prepare(lease);assertTrue(queue.renew(lease));assertTrue(queue.complete(lease,new ama.contract.TaskContracts.TaskResult(ama.contract.TaskContracts.Outcome.FAILED,null,"FIX01_PREPARATION_STOP")));
  assertEquals("NEVER_MARKED",j.queryForObject("SELECT local_state FROM publication_submission WHERE task_id=?",String.class,task));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM external_execution WHERE task_id=? AND marked_at IS NOT NULL",Integer.class,task));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM task_attempt WHERE task_id=? AND ended_at IS NULL",Integer.class,task));
  Files.writeString(receipt.resolve("changed-destination-and-real-gaps.json"),json.writeValueAsString(Map.of("accepted",accepted,"oldAck","APPLIED","gapValidation",validation,"traffic",service.traffic(),"qualification","changed logical destination same key no3; original old1 ACK, real local no2 and no3 gaps; destination authorization only; no replacement marker/send")));
  Files.writeString(receipt.resolve("protected-source-trace.json"),json.writeValueAsString(trace.verify()));
 }
}
