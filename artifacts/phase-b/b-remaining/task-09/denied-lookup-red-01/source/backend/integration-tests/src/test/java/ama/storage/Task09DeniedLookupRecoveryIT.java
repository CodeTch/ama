package ama.storage;
import ama.transcript.*;import ama.model.*;import ama.task.*;import ama.worker.*;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class Task09DeniedLookupRecoveryIT extends Task09GenerationPhysicalCancelIT {
 @Override boolean candidateExpected(){return false;}
 @Override void beforeWorker(String task){assertDoesNotThrow(()->Class.forName("ama.transcript.MinutesRecordedLookup"));}
 @Override void recoverCancelled(String task,MinutesExternalPreparation preparation,ControlledMinutesClient client,JdbcExternalOperations operations,JdbcModelPermitPool permits,Map<String,Object> before,Map<String,String> files)throws Exception{
  assertEquals(1,j.update("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id=? AND status='ACTIVE'",owner));archiveUnavailable.set(true);
  var error=new java.util.concurrent.atomic.AtomicReference<Throwable>();var real=new MinutesReconciliationHandler(preparation,client,operations,permits);
  try(var loop=ReconciliationLoop.prepared(operations,permits,1,session->{try{real.reconcile(session);}catch(Throwable e){error.set(e);throw e;}},"TASK09_DENIED_GET")){loop.start();long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(40);while(System.nanoTime()<end&&error.get()==null&&!"STALE".equals(j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task)))Thread.sleep(100);assertNull(error.get(),error.get()==null?"":error.get().toString());assertEquals("STALE",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task));}
  permits.recoverSettlements(64);assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM model_reservation WHERE execution_id=? AND settled_at IS NULL",Integer.class,before.get("execution_id")));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM generation_candidate WHERE task_id=?",Integer.class,task));assertEquals("SUCCEEDED",j.queryForObject("SELECT state FROM external_execution WHERE task_id=?",String.class,task));assertEquals(1,relay.posts.get());assertEquals(files,seals());var denied=call("GET","/meetings/"+meeting+"/minutes/generation-tasks/"+task,"",null);assertTrue(Set.of(401,403).contains(denied.statusCode()));
  java.nio.file.Files.writeString(receipt.resolve("actual-denied-get-recovery.json"),json.writeValueAsString(Map.of("task",task,"execution",before.get("execution_id"),"sourceUnavailable",true,"remote","SUCCEEDED","local","STALE","candidateCount",0,"permitSettled",true,"providerPostCount",1)));
 }
}
