package ama.storage;
import java.nio.file.*;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class Task11Fix01LocalGapIT extends Task11Fix01HistoryValidationIT {
 @Override @Test void genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate()throws Exception {
  var history=json.readTree(Files.readAllBytes(previousPipeline().resolve("fix01-isolated-history.json")));String task=history.get("replacement").get("taskId").asText();
  assertEquals(List.of(task),j.queryForList("SELECT id FROM durable_task WHERE pool='KNOWLEDGE_PUBLISH' AND state IN ('READY','RUNNING') ORDER BY id",String.class));
  var queue=app.getBean(ama.task.JdbcTaskQueue.class);var lease=queue.claim("KNOWLEDGE_PUBLISH","FIX01_LOCAL_GAP").orElseThrow();assertEquals(task,lease.taskId());
  var service=ama.api.Task11RuntimeProbe.publication(app);service.prepare(lease);assertTrue(queue.renew(lease));
  assertTrue(queue.complete(lease,new ama.contract.TaskContracts.TaskResult(ama.contract.TaskContracts.Outcome.FAILED,null,"FIX01_PREPARATION_STOP")));
  var row=j.queryForMap("SELECT s.local_state,s.local_reason,s.local_attempt_no,s.local_execution_id,e.id,e.state,e.marked_at,e.task_attempt,e.no_effect_basis FROM publication_submission s JOIN external_execution e ON e.task_id=s.task_id WHERE s.task_id=?",task);
  assertEquals("NEVER_MARKED",row.get("local_state"));assertEquals("FAILED",row.get("state"));assertEquals("NOT_SENT",row.get("no_effect_basis"));assertNull(row.get("marked_at"));assertNull(row.get("task_attempt"));assertEquals(row.get("id"),row.get("local_execution_id"));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM task_attempt WHERE task_id=? AND ended_at IS NULL",Integer.class,task));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM publication_attempt WHERE execution_id=?",Integer.class,row.get("id")));
  assertEquals(Map.of("getWriteAttempts",0L,"postWriteAttempts",1L),service.traffic());
  Files.writeString(receipt.resolve("actual-local-prepared-gap.json"),json.writeValueAsString(Map.of("task",task,"closed",row,"traffic",service.traffic(),"qualification","genuine sole-pool claim, worker preparation and renewal, ordinary queue local completion; destination POST only, no marker/publication POST")));
 }
}
