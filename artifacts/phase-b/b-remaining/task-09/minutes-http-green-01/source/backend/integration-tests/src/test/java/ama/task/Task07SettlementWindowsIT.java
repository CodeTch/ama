package ama.task;
import ama.contract.TaskContracts.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static ama.task.Task07ExternalWorkerIT.*;

class Task07SettlementWindowsIT {
 final Task07PreparedExternalIT f=new Task07PreparedExternalIT();final JdbcExternalOperations external=new JdbcExternalOperations(f.queue);
 JdbcModelPermitPool pool(String model){return new JdbcModelPermitPool(external,Map.of(model,new ModelConfiguration(model,1,1,true,ModelConfiguration.CutoffPolicy.EXPLICIT_SETTLEMENT)),(t,m,d)->{throw new IllegalStateException();});}
 @Test void childKilledAfterTerminalCommitBeforeSettlementRecoversExactDurableDebt()throws Exception{
  Path window=Path.of(System.getenv("TASK07_EVIDENCE_WINDOW")),ready=window.resolve("terminal-crash-ready.txt"),log=window.resolve("terminal-crash-child.log");
  var child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Xmx64m","-XX:ActiveProcessorCount=2","-cp",System.getProperty("java.class.path"),"ama.task.Task07TerminalCrashProbe",ready.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
  List<String> ids;
  try{eventually(()->Files.exists(ready));ids=Files.readAllLines(ready);assertEquals(4,ids.size());assertEquals("SUCCEEDED",f.jdbc.queryForObject("SELECT state FROM external_execution WHERE id=?",String.class,ids.get(1)));assertEquals("SUCCEEDED",f.jdbc.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,ids.get(3)));assertNull(f.jdbc.queryForObject("SELECT settled_at FROM model_reservation WHERE id=?",java.sql.Timestamp.class,ids.getFirst()));}
  finally{child.destroyForcibly();assertTrue(child.waitFor(5,TimeUnit.SECONDS));assertFalse(child.isAlive());Files.writeString(window.resolve("terminal-crash-child-status.txt"),"pid="+child.pid()+"\ngone=true\nexit="+child.exitValue()+"\n");}
  var pool=pool(ids.get(2));int bound=2*((f.jdbc.queryForObject("SELECT COUNT(*) FROM model_reservation",Integer.class)+7)/8)+2;
  for(int i=0;i<bound&&!f.jdbc.queryForObject("SELECT settled_at IS NOT NULL FROM model_reservation WHERE id=?",Boolean.class,ids.getFirst());i++)pool.recoverSettlements(8);
  assertTrue(f.jdbc.queryForObject("SELECT settled_at IS NOT NULL FROM model_reservation WHERE id=?",Boolean.class,ids.getFirst()));assertEquals(1,f.jdbc.queryForObject("SELECT COUNT(*) FROM external_execution WHERE task_id=?",Integer.class,ids.get(3)));
 }
 @Test void settlementCommitBeforeLostAcknowledgementIsIdempotent(){
  f.setup();var token=external.beginPrepared(f.lease,f.preparedRequest).orElseThrow();String model="T07_"+UUID.randomUUID().toString().replace("-","");var pool=pool(model);pool.configure();var permit=pool.acquire(token,model,null).orElseThrow();external.observePrepared(token,f.preparedObservation);
  var once=new AtomicBoolean();f.source.committed=()->{if(once.compareAndSet(false,true))throw new IllegalStateException("T07_SETTLEMENT_ACK_LOST");};assertThrows(IllegalStateException.class,()->pool.settled(permit));f.source.committed=()->{};
  var committed=f.jdbc.queryForObject("SELECT settled_at FROM model_reservation WHERE id=?",java.sql.Timestamp.class,permit.id());assertNotNull(committed);assertTrue(pool.settled(permit));assertEquals(committed,f.jdbc.queryForObject("SELECT settled_at FROM model_reservation WHERE id=?",java.sql.Timestamp.class,permit.id()));
 }
 @Test void concurrentExactSettlementMutatesReservationOnce()throws Exception{
  f.setup();var token=external.beginPrepared(f.lease,f.preparedRequest).orElseThrow();String model="T07_"+UUID.randomUUID().toString().replace("-","");var a=pool(model);a.configure();var permit=a.acquire(token,model,null).orElseThrow();external.observePrepared(token,f.preparedObservation);var b=pool(model);var go=new CountDownLatch(1);var updates=new AtomicInteger();f.source.after=e->{if(e.text().startsWith("UPDATE model_reservation SET settled_at="))updates.incrementAndGet();};
  var threads=Executors.newFixedThreadPool(2);try{var left=threads.submit(()->{await(go);return a.settled(permit);});var right=threads.submit(()->{await(go);return b.settled(permit);});go.countDown();assertTrue(left.get(10,TimeUnit.SECONDS));assertTrue(right.get(10,TimeUnit.SECONDS));}finally{threads.shutdownNow();f.source.after=e->{};}
  assertEquals(1,updates.get());assertNotNull(f.jdbc.queryForObject("SELECT settled_at FROM model_reservation WHERE id=?",java.sql.Timestamp.class,permit.id()));assertEquals(1,f.jdbc.queryForObject("SELECT COUNT(*) FROM model_reservation WHERE execution_id=?",Integer.class,token.executionId()));
 }
}
