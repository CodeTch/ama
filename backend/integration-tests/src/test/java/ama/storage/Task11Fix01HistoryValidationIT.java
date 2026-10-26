package ama.storage;
import java.nio.file.*;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class Task11Fix01HistoryValidationIT extends Task11PublicationResumeIT {
 @Override String previousWindow(){return "replacement-fix01-isolated-history-01";}
 @Override Task11KnowledgeFixture attachKnowledge()throws Exception {assertEquals("task11-fix01-isolated",System.getenv("AMA_TASK11_FIXTURE"));return new Task11KnowledgeFixture(receipt,owner,previousPipeline().resolve("knowledge-retained-provider-attached.json"));}
 @Override @Test void genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate()throws Exception {
  var history=json.readTree(Files.readAllBytes(previousPipeline().resolve("fix01-isolated-history.json")));String next=history.get("replacement").get("taskId").asText();
  var service=ama.api.Task11RuntimeProbe.publication(app);var result=ama.transcript.Task11Fix01HistoryProbe.verify(service,meeting,next);
  assertEquals("READY",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,next));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM task_attempt WHERE task_id=?",Integer.class,next));assertEquals(Map.of("getWriteAttempts",0L,"postWriteAttempts",0L),service.traffic());Files.writeString(receipt.resolve("history-validation.json"),json.writeValueAsString(result));
 }
}
