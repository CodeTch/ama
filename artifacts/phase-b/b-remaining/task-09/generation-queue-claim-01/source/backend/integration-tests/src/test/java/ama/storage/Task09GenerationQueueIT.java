package ama.storage;
import ama.transcript.*;
import ama.config.*;
import ama.contract.ConfigArchiveContracts.Ref;
import ama.contract.IdentityContracts.Actor;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
class Task09GenerationQueueIT extends Task09MinutesFixture {
 @Test void acceptedQueueInputIsAtomicReplayFirstAndPreservesCurrentRoot()throws Exception {
  confirmed();install();initialize(service(),"initial",3);var before=j.queryForMap("SELECT * FROM meeting WHERE id=?",meeting);var authority=ama.api.Task09RuntimeProbe.components(app).authority();var prep=new JdbcMinutesGenerationPreparation(authority,this::snapshot,pin,prompt,model,new TranscriptAcquisitionBudget(16777216,16777216,16777216,268435456,134217728,8388608,128,256,256));
  Class<?> type=assertDoesNotThrow(()->Class.forName("ama.transcript.JdbcMinutesGenerationCommands"));var fences=new ama.task.JdbcSubjectFences(source,Set.of("MEETING"));var outbox=new ama.task.JdbcOutbox(source,new MinutesEventReferences(source).validators());var domain=(ama.task.TaskDomainPolicy)JdbcTranscriptAuthority.class.getMethod("minutesTaskPolicy").invoke(authority);var queue=new ama.task.JdbcTaskQueue(source,fences,outbox,Map.of("MINUTES_GENERATE_V1",domain));Object commands=type.getConstructor(JdbcTranscriptAuthority.class,JdbcMinutesGenerationPreparation.class,ama.task.TaskQueue.class,ama.task.OutboxPort.class,ama.audit.AuditPort.class).newInstance(authority,prep,queue,outbox,new ama.audit.JdbcAuditAdapter(source));
  var accepted=invoke(commands,"same",4);var result=(Map<?,?>)accepted.get("result");assertEquals("GENERATION_TASK",result.get("kind"));assertEquals(before,j.queryForMap("SELECT * FROM meeting WHERE id=?",meeting));assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM generation_input WHERE meeting_id=?",Integer.class,meeting));assertEquals("READY",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,result.get("taskId")));archiveUnavailable.set(true);var replay=invoke(commands,"same",4);assertEquals(result,replay.get("result"));assertEquals(true,replay.get("replayed"));assertThrows(RuntimeException.class,()->invoke(commands,"same",3));
  var lease=queue.claim("MINUTES_GENERATE","TASK09_CASE").orElseThrow();assertEquals(result.get("taskId"),lease.taskId());assertTrue(queue.renew(lease));assertTrue(queue.complete(lease,new ama.contract.TaskContracts.TaskResult(ama.contract.TaskContracts.Outcome.FAILED,null,"CONTROLLED_TEST_STOP")));assertEquals("FAILED",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,lease.taskId()));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM external_execution WHERE task_id=?",Integer.class,lease.taskId()));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM generation_candidate WHERE task_id=?",Integer.class,lease.taskId()));
  java.nio.file.Files.writeString(receipt.resolve("real-generation-queue.json"),json.writeValueAsString(Map.of("result",result,"meetingRevision",4,"sameKeyReplay",true,"actualClaimAttempt",lease.attempt(),"actualOrdinaryRenew",true,"controlledTestStop","FAILED","externalExecutions",0,"candidates",0)));

 }
 @SuppressWarnings("unchecked") Map<String,Object> invoke(Object service,String key,long expected)throws Exception {try{return (Map<String,Object>)service.getClass().getMethod("enqueue",Actor.class,String.class,String.class,long.class,String.class).invoke(service,actor(),meeting,key,expected,UUID.randomUUID().toString());}catch(java.lang.reflect.InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;throw e;}}
}
