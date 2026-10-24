package ama.task;
import ama.worker.*;import ama.contract.TaskContracts.*;import ama.contract.ExternalContracts.*;
import org.junit.jupiter.api.*;import java.util.*;import java.time.*;import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class WorkerProcessIT {
 TaskFixture f;
 @BeforeEach void setup(){f=new TaskFixture();}
 Process process(TaskSpec s,boolean block,Path log)throws Exception{var b=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",System.getProperty("java.class.path"),"ama.worker.WorkerMain");b.environment().put("AMA_TEST_WORKER_POOL",s.pool());b.environment().put("AMA_TEST_WORKER_BLOCK",Boolean.toString(block));return b.redirectErrorStream(true).redirectOutput(log.toFile()).start();}
 static void await(java.util.function.BooleanSupplier check,int seconds)throws Exception{long until=System.nanoTime()+seconds*1000000000L;while(System.nanoTime()<until){if(check.getAsBoolean())return;Thread.sleep(100);}assertTrue(check.getAsBoolean(),"condition did not become true within "+seconds+" seconds");}
 @Test void separateProcessConsumesProducedTask()throws Exception{var s=f.spec();String id=f.queue().enqueue(s);Path log=Files.createTempFile("b05-worker-",".log");Process p=process(s,false,log);try{await(()->"SUCCEEDED".equals(f.j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,id)),10);assertEquals(1,f.j.queryForObject("SELECT COUNT(*) FROM task_attempt WHERE task_id=?",Integer.class,id));}finally{p.destroy();p.waitFor(5,java.util.concurrent.TimeUnit.SECONDS);if(p.isAlive())p.destroyForcibly();}}
 @Test void killedWorkerIsReclaimedWithinThirtySecondsAndCannotCommit()throws Exception{
  var s=f.spec();var q=f.queue();String id=q.enqueue(s);Path log=Files.createTempFile("b05-blocked-worker-",".log");Process old=process(s,true,log);
  TaskLease lease;
  try{await(()->"RUNNING".equals(f.j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,id)),10);var row=q.row(id,false,false).orElseThrow();lease=new TaskLease(id,row.attempt(),row.owner(),row.expiry(),row.spec());Thread.sleep(6100);assertTrue(q.row(id,false,false).orElseThrow().expiry().isAfter(lease.expiresAt().plusSeconds(4)));}finally{old.destroyForcibly();old.waitFor(5,java.util.concurrent.TimeUnit.SECONDS);}
  long started=System.nanoTime();Process fresh=process(s,false,Files.createTempFile("b05-recovered-worker-",".log"));try{await(()->"SUCCEEDED".equals(f.j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,id)),30);assertTrue(Duration.ofNanos(System.nanoTime()-started).toSeconds()<30);assertFalse(q.complete(lease,f.success(s)));assertEquals(2,f.j.queryForObject("SELECT COUNT(*) FROM task_attempt WHERE task_id=?",Integer.class,id));}finally{fresh.destroy();fresh.waitFor(5,java.util.concurrent.TimeUnit.SECONDS);if(fresh.isAlive())fresh.destroyForcibly();}
 }
 @Test void independentOperationHeartbeatContinuesWhileHandlerBlocks()throws Exception{var q=f.queue();var e=new JdbcExternalOperations(q);var s=f.spec();var l=f.claimed(q,s);var t=e.beginExternal(l,new ExternalRequest("TEST_CALL",s.producerKey(),TaskFixture.hash(s.producerKey()),TaskFixture.hash(s.input().id()),"test-v1:"+s.input().id())).orElseThrow();try(var heartbeats=new ExternalOperationHeartbeat(e,1);var tracked=heartbeats.track(t)){Thread.sleep(6100);var expiry=f.j.queryForObject("SELECT reconcile_expires_at FROM external_operation WHERE id=?",java.time.LocalDateTime.class,t.operationId()).toInstant(ZoneOffset.UTC);assertTrue(expiry.isAfter(t.expiresAt().plusSeconds(4)));}}
}
