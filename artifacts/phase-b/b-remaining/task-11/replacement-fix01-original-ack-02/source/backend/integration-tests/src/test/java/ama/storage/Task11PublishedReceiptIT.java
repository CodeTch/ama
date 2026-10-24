package ama.storage;
import java.nio.file.*;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
/** Exact terminal GET qualification; intentionally not lost-response recovery. */
class Task11PublishedReceiptIT extends Task11PublicationResumeIT {
 @Override String previousWindow(){return "publication-main-03";}
 @Override Task11KnowledgeFixture attachKnowledge()throws Exception{return new Task11KnowledgeFixture(receipt,owner,previousPipeline().resolve("knowledge-retained-unresolved.json"));}
 @Override @Test void genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate()throws Exception {
  String publication="7255d632-1a01-4e55-9080-7dc173bcb499",execution="3b2920bc-0f3e-42fd-9719-a2ee311bae28";var service=ama.api.Task11RuntimeProbe.publication(app);var before=state(publication);var traffic=service.traffic();
  var actual=service.readPublishedReceipt(actor(),meeting,publication);assertEquals(execution,actual.get("executionId"));assertEquals("doc-b2b635fc3fcaaf513f82637092e92638",actual.get("documentId"));assertEquals("result-5a7910348168b4e456b36a824644e666",actual.get("resultId"));assertEquals("SUCCESS",actual.get("state"));
  assertEquals(before,state(publication));var after=service.traffic();assertEquals(traffic.get("postWriteAttempts"),after.get("postWriteAttempts"));assertEquals(traffic.get("getWriteAttempts")+1,after.get("getWriteAttempts"));
  Files.writeString(receipt.resolve("terminal-get-readback.json"),json.writeValueAsString(Map.of("publication",publication,"execution",execution,"receipt",actual,"before",before,"after",state(publication),"trafficBefore",traffic,"trafficAfter",after,"qualification","owner-authorized terminal GET; no claim/observe/POST; not lost-response recovery")));
 }
 List<Map<String,Object>> state(String publication){return j.queryForList("SELECT p.id,p.state publication_state,m.stage,m.revision,m.ever_published,t.state task_state,o.state operation_state,e.id execution_id,e.execution_no,e.state execution_state,a.outcome FROM publication p JOIN meeting m ON m.id=p.meeting_id JOIN publication_submission s ON s.publication_id=p.id JOIN durable_task t ON t.id=s.task_id JOIN external_execution e ON e.task_id=t.id JOIN external_operation o ON o.id=e.operation_id JOIN publication_attempt a ON a.execution_id=e.id WHERE p.id=? ORDER BY e.execution_no",publication);}
}
