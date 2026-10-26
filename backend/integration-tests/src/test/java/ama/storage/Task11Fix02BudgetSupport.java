package ama.storage;
import java.nio.file.*;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
/** One dimension per independent JVM/window; never accept or create a final. */
abstract class Task11Fix02BudgetSupport extends Task11Fix02CompositionC3AcceptIT {
 abstract int dimension();long fixedLimit(){return 0;}
 @Override @Test void genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate()throws Exception{
  String pub="d629b199-2b36-4ca8-aa4d-7fee86ecdf95";assertEquals("FAILED",j.queryForObject("SELECT state FROM publication WHERE id=?",String.class,pub));assertEquals(2,j.queryForObject("SELECT COUNT(*) FROM publication_submission WHERE publication_id=?",Integer.class,pub));assertTrue(j.queryForList("SELECT id FROM durable_task WHERE pool='KNOWLEDGE_PUBLISH' AND state IN ('READY','RUNNING')").isEmpty());assertEquals(2,j.queryForObject("SELECT COUNT(*) FROM external_execution e JOIN publication_submission s ON s.task_id=e.task_id WHERE s.publication_id=? AND e.state='FAILED' AND e.no_effect_basis='DEFINITIVE_REJECTION'",Integer.class,pub));trace.phase.set("full-resubmit-dimension-"+dimension());var result=ama.transcript.Task11Fix02ReplacementBudgetProbe.verifyDimension(ama.api.Task11RuntimeProbe.publication(app),actor(),meeting,pub,app.getBean(FileBackendCatalog.class),finalGuard,this::preimages,this::telemetry,this::budgetProgress,dimension(),fixedLimit());assertEquals(0,relay.read().get("postPublication").asInt());assertEquals(0,relay.read().get("blockedPublicationAttempts").asInt());Files.writeString(receipt.resolve("budget-dimension-result.json"),json.writeValueAsString(result));relay.settled=true;
 }
}
