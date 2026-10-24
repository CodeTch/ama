package ama.storage;
import ama.transcript.*;import ama.contract.TaskContracts.*;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
/** Real persisted historical reviewer in the shared prospective/stored S/F gate; no current pointer fixture. */
class Task09GenerationPreparationIdentitiesIT extends Task09GenerationReviewIdentitiesIT {
 @Override void afterReview(Map<String,Object> candidate,JdbcMinutesFactsReader.Snapshot restored,String review)throws Exception {
  var authority=ama.api.Task09RuntimeProbe.components(app).authority();var task=j.queryForMap("SELECT * FROM durable_task WHERE id=?",candidate.get("task_id"));
  var spec=new TaskSpec((String)task.get("task_type"),(String)task.get("pool"),new SubjectRef((String)task.get("subject_kind"),(String)task.get("subject_id")),(String)task.get("producer_key"),new TypedRef((String)task.get("input_kind"),(String)task.get("input_id")),(String)task.get("input_fingerprint"),((Number)task.get("business_version")).longValue(),((Number)task.get("priority")).intValue(),((Number)task.get("cancel_epoch")).longValue(),((Number)task.get("max_attempts")).intValue());
  var trace=(Task09TraceSource)source;trace.events.clear();var prep=new JdbcMinutesGenerationPreparation(authority,this::snapshot,pin,prompt,model,new TranscriptAcquisitionBudget(16777216,16777216,16777216,268435456,134217728,8388608,128,256,256));
  var result=assertDoesNotThrow(()->prep.prepareRecorded(actor(),(String)candidate.get("task_id"),spec));assertEquals(spec,result.spec());long locks=trace.events.stream().filter(e->e.toString().contains("SELECT enterprise_id,status FROM enterprise_identity WHERE enterprise_id=? FOR UPDATE")).count();assertTrue(locks>=4,"S/F must lock owner and historical reviewer before roots");assertNull(j.queryForObject("SELECT element_set_id FROM meeting WHERE id=?",String.class,meeting));
  java.nio.file.Files.writeString(receipt.resolve("actual-preparation-identity-union.json"),json.writeValueAsString(Map.of("historicalReviewerStatus","DISABLED","identityLocks",locks,"requestReconstructed",true,"currentPointersUnchanged",true,"task",candidate.get("task_id"))));
 }
}
