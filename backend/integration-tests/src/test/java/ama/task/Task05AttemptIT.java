package ama.task;
import ama.contract.TaskContracts.*;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
/** Forward-schema tests; no migrator, reset, or historical history-count assertion. */
class Task05AttemptIT {
 @Test void expiryAfterActualAttemptLockWaitDeniesRenewal()throws Exception {
  var f=new TaskFixture();var q=f.queue();var l=f.claimed(q,f.spec());
  f.j.update("UPDATE durable_task SET expires_at=UTC_TIMESTAMP(6)+INTERVAL 1 SECOND WHERE id=?",l.taskId());
  var pool=java.util.concurrent.Executors.newSingleThreadExecutor();
  try(var blocker=f.source.getConnection()){
   blocker.setAutoCommit(false);try(var lock=blocker.prepareStatement("SELECT owner FROM task_attempt WHERE task_id=? AND attempt=? FOR UPDATE")){
    lock.setString(1,l.taskId());lock.setLong(2,l.attempt());try(var rows=lock.executeQuery()){assertTrue(rows.next());}
   }
   var future=pool.submit(()->q.renew(l));Thread.sleep(1350);assertFalse(future.isDone(),"Renewal must actually wait for attempt lock");blocker.rollback();
   assertFalse(future.get(3,java.util.concurrent.TimeUnit.SECONDS),"Fresh DB expiry after the final lock wait must deny renewal");
  }finally{pool.shutdownNow();assertTrue(pool.awaitTermination(3,java.util.concurrent.TimeUnit.SECONDS));}
 }
 @Test void endedOrForeignAttemptDeniesEveryLeaseUse() {
  for(String fault:new String[]{"ended","foreign","missing"}) {
   var f=new TaskFixture();var q=f.queue();var s=f.spec();var l=f.claimed(q,s);var effects=new AtomicInteger();
   if(fault.equals("ended"))f.j.update("UPDATE task_attempt SET ended_at=UTC_TIMESTAMP(6),outcome='FAILED' WHERE task_id=? AND attempt=?",l.taskId(),l.attempt());
   else if(fault.equals("foreign"))f.j.update("UPDATE task_attempt SET owner='OTHER' WHERE task_id=? AND attempt=?",l.taskId(),l.attempt());
   else f.j.update("DELETE FROM task_attempt WHERE task_id=? AND attempt=?",l.taskId(),l.attempt());
   assertFalse(q.renew(l),fault);
   assertFalse(q.completeWithEffects(l,f.success(s),effects::incrementAndGet),fault);
   assertFalse(q.completeWithEffects(l,()->{effects.incrementAndGet();return f.success(s);}),fault);
   assertEquals(0,effects.get());
  }
 }
 @Test void retryReadyHistoryAndExpiredAtLimitEndOnlyOnce() {
  var f=new TaskFixture();var q=f.queue();var s=f.spec();var l=f.claimed(q,s);
  assertTrue(q.complete(l,new TaskResult(Outcome.RETRYABLE,null,"RETRY")));
  Object ended=f.j.queryForObject("SELECT ended_at FROM task_attempt WHERE task_id=? AND attempt=1",Object.class,l.taskId());
  f.advance(s,1,0,false);f.j.update("UPDATE durable_task SET available_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",l.taskId());
  assertTrue(q.claim(s.pool(),"next").isEmpty());
  assertEquals(ended,f.j.queryForObject("SELECT ended_at FROM task_attempt WHERE task_id=? AND attempt=1",Object.class,l.taskId()));
  var limited=f.spec("LIMITED",1);var ll=f.claimed(q,limited);f.expire(ll);assertTrue(q.claim(limited.pool(),"next").isEmpty());
  assertEquals("FAILED",f.j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,ll.taskId()));
  assertEquals("EXPIRED",f.j.queryForObject("SELECT outcome FROM task_attempt WHERE task_id=? AND attempt=1",String.class,ll.taskId()));
 }
}
