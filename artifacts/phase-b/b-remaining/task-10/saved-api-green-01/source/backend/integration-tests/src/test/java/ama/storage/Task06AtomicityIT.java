package ama.storage;
import ama.transcript.*;import ama.contract.TranscriptContracts.*;import org.junit.jupiter.api.Test;
import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class Task06AtomicityIT extends Task06Fixture {
 Command command(String raw){return new Command(Operation.SAVE,1,raw,j.queryForList("SELECT segment_id FROM transcript_segment WHERE revision_id=? ORDER BY ordinal",String.class,raw).stream().map(id->new Edit(id,"Atomic😀",List.of())).toList());}
 @Test void failedTransactionRollsBackAllAndLostAcknowledgmentReplaysActualResult()throws Exception {
  String raw=raw();var service=isolated();var command=command(raw);long outbox=j.queryForObject("SELECT COUNT(*) FROM task_outbox",Long.class),audit=j.queryForObject("SELECT COUNT(*) FROM audit_event",Long.class);
  probe.rollbackOn.set("INSERT INTO transcript_revision");assertThrows(RuntimeException.class,()->service.mutate(actor(),meeting,"rollback",command,UUID.randomUUID().toString()));
  assertEquals(1,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Integer.class,meeting));assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM transcript_revision WHERE meeting_id=?",Integer.class,meeting));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE resource_id=? AND operation='TRANSCRIPT_SAVE'",Integer.class,meeting));assertEquals(outbox,j.queryForObject("SELECT COUNT(*) FROM task_outbox",Long.class));assertEquals(audit,j.queryForObject("SELECT COUNT(*) FROM audit_event",Long.class));
  probe.lostAckOn.set("INSERT INTO transcript_revision");assertThrows(RuntimeException.class,()->service.mutate(actor(),meeting,"lost",command,UUID.randomUUID().toString()));
  var replay=service.mutate(actor(),meeting,"lost",command,UUID.randomUUID().toString());assertEquals(true,replay.get("replayed"));assertEquals(2,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Integer.class,meeting));assertEquals(2,j.queryForObject("SELECT COUNT(*) FROM transcript_revision WHERE meeting_id=?",Integer.class,meeting));assertEquals(outbox+1,j.queryForObject("SELECT COUNT(*) FROM task_outbox",Long.class));assertEquals(audit+1,j.queryForObject("SELECT COUNT(*) FROM audit_event",Long.class));
 }
 @Test void ordinarySnapshotBudgetFailureAfterConcurrentAcceptanceReturnsWinner()throws Exception {
  String raw=raw();var command=command(raw);var composed=ama.api.Task06RuntimeProbe.compose(app);var normal=composed.service();
  var bounded=new JdbcSavedTranscripts(ama.api.Task05RuntimeProbe.components(app).authority(),settings().archives(),new TranscriptAcquisitionBudget(16777216,16777216,16777216,67108864,67108864,1,128,256,64),composed.correction());
  var once=new AtomicBoolean();var winner=new AtomicReference<Map<String,Object>>();probe.before=sql->{if(sql.startsWith("SELECT id,parent_id,processing_input_id")&&once.compareAndSet(false,true))winner.set(normal.mutate(actor(),meeting,"winner",command,UUID.randomUUID().toString()));};
  var replay=bounded.mutate(actor(),meeting,"winner",command,UUID.randomUUID().toString());probe.before=sql->{};assertEquals(winner.get().get("result"),replay.get("result"));assertEquals(true,replay.get("replayed"));assertEquals(2,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Integer.class,meeting));
 }
 @Test void twoConcurrentDifferentKeysCanAdvanceExpectedRevisionOnlyOnce()throws Exception {
  String raw=raw();var command=command(raw);var service=isolated();var barrier=new CyclicBarrier(2);probe.before=sql->{if(sql.startsWith("SELECT id,parent_id,processing_input_id"))try{barrier.await(8,TimeUnit.SECONDS);}catch(Exception e){throw new RuntimeException(e);}};
  try(var pool=Executors.newFixedThreadPool(2)){var results=new ArrayList<Future<Boolean>>();for(int i=0;i<2;i++){String key="parallel-"+i;results.add(pool.submit(()->{try{service.mutate(actor(),meeting,key,command,UUID.randomUUID().toString());return true;}catch(Failure conflict){assertEquals(409,conflict.status());return false;}}));}int accepted=0;for(var result:results)if(result.get(20,TimeUnit.SECONDS))accepted++;assertEquals(1,accepted);}
  probe.before=sql->{};assertEquals(2,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Integer.class,meeting));assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE resource_id=? AND operation='TRANSCRIPT_SAVE'",Integer.class,meeting));
 }
}
