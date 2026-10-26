package ama.storage;
import java.nio.file.*;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class Task11Fix01LedgerIT extends Task11Fix01HistoryValidationIT {
 @Override @Test void genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate()throws Exception {var history=json.readTree(Files.readAllBytes(previousPipeline().resolve("fix01-isolated-history.json")));String pub=history.get("original").get("publicationId").asText();String selected=j.queryForObject("SELECT task_id FROM publication_submission WHERE publication_id=? AND submission_no=4",String.class,pub);var service=ama.api.Task11RuntimeProbe.publication(app);var results=ama.transcript.Task11Fix01LedgerProbe.verify(service,meeting,selected);assertEquals(0L,service.traffic().get("postWriteAttempts"));Files.writeString(receipt.resolve("effective-cumulative-ledger.json"),json.writeValueAsString(results));}
}
