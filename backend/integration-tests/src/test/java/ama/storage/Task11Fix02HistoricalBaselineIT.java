package ama.storage;
import java.nio.file.*;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
/** Initial-only real provider commit/response loss; leaves exact UNKNOWN for separate RED/GREEN. */
class Task11Fix02HistoricalBaselineIT extends Task11FinalPublicationIT {
 Task11ResponseLossFixture loss;
 @org.junit.jupiter.api.AfterEach void closeLoss()throws Exception{if(loss!=null)loss.close();}
 @Override Task11KnowledgeFixture knowledgeFixture()throws Exception {
  assertEquals("task11-fix02-isolated",System.getenv("AMA_TASK11_FIXTURE"));
  Path previous=Path.of(System.getenv("AMA_TASK11_REPOSITORY_ROOT"),"artifacts/phase-b/b-remaining/task-11/publication-zero-effect-01");Path retained;try(var paths=Files.list(previous)){retained=paths.filter(x->x.getFileName().toString().startsWith("pipeline-")).findFirst().orElseThrow().resolve("knowledge-retained-unresolved.json");}
  var original=new Task11KnowledgeFixture(receipt,owner,retained);original.grant(owner);loss=new Task11ResponseLossFixture(original,receipt);preserveKnowledge=true;return original;
 }
 @Override int knowledgeClientPort(){return loss.port;}
 @Override Task11PublicationWaiter.Decision expectedPublicationDecision(){return Task11PublicationWaiter.Decision.UNRESOLVED;}
 @Override void runPublication(Map<String,Object> accepted)throws Exception {
  String task=(String)accepted.get("taskId");assertEquals(List.of(task),j.queryForList("SELECT id FROM durable_task WHERE pool='KNOWLEDGE_PUBLISH' AND state IN ('READY','RUNNING') ORDER BY id",String.class));super.runPublication(accepted);
  var remote=loss.read();assertTrue(remote.get("withheld").asBoolean());assertEquals(1,remote.get("postPublication").asInt());assertEquals(0,remote.get("failures").asInt());assertEquals("SUCCESS",remote.get("receipt").get("state").asText());
  var execution=j.queryForMap("SELECT id,operation_id,execution_no,state FROM external_execution WHERE task_id=?",task);assertEquals("UNKNOWN",execution.get("state"));assertEquals(execution.get("id"),remote.get("receipt").get("executionId").asText());assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM external_document_mapping WHERE publication_id=?",Integer.class,accepted.get("publicationId")));
  Files.writeString(receipt.resolve("fix02-historical-baseline.json"),json.writeValueAsString(Map.of("accepted",accepted,"execution",execution,"remote",remote,"qualification","one actual initial provider SUCCESS deliberately withheld; exact local UNKNOWN retained for noPOST handler source-loss RED/GREEN; no replacement")));
  loss.settled=true;preserveKnowledge=true;
 }
}
