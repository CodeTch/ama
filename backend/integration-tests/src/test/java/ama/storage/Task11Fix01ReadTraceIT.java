package ama.storage;
import java.nio.file.*;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class Task11Fix01ReadTraceIT extends Task11Fix01HistoryValidationIT {
 final Task11Fix01SqlTrace trace=new Task11Fix01SqlTrace();
 @Override javax.sql.DataSource createSource(){return trace.wrap(super.createSource());}
 @Override @Test void genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate()throws Exception {
  var history=json.readTree(Files.readAllBytes(previousPipeline().resolve("fix01-isolated-history.json")));String pub=history.get("original").get("publicationId").asText(),old=history.get("original").get("taskId").asText();var service=ama.api.Task11RuntimeProbe.publication(app);
  assertEquals(ama.contract.ExternalContracts.ObservationResult.APPLIED,service.recoverOriginalReceipt(actor(),meeting,pub,old));
  assertEquals(Map.of("getWriteAttempts",1L,"postWriteAttempts",0L),service.traffic());Files.writeString(receipt.resolve("original-ack-bound-transaction-trace.json"),json.writeValueAsString(trace.verify()));
 }
}
