package ama.storage;
import ama.transcript.*;import ama.contract.MinutesContracts;import java.util.*;import java.util.concurrent.*;import java.util.function.*;import static org.junit.jupiter.api.Assertions.*;
class Task10Fix01ReplayIT extends Task10AdoptIT {
 Task10AtomicTraceSource probe;
 @Override javax.sql.DataSource createSource(){return probe=new Task10AtomicTraceSource(ama.minutes.Task10Source.open());}
 TranscriptAcquisitionBudget limits(){return new TranscriptAcquisitionBudget(16777216,16777216,16777216,268435456,134217728,8388608,128,256,256);}
 @Override void afterCandidate(Map<String,Object> candidate,JdbcMinutesFactsReader.Snapshot facts)throws Exception {
  super.afterCandidate(candidate,facts);var a=ama.api.Task09RuntimeProbe.components(app).authority();var assertions=new ArrayList<org.junit.jupiter.api.function.Executable>();
  for(String family:List.of("BODY","REVIEW","SETTINGS"))for(String kind:List.of("ORDINARY","PROGRAMMING","CORRUPTION"))assertions.add(()->{
   long r=j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,meeting);String body=j.queryForObject("SELECT minutes_revision_id FROM meeting WHERE id=?",String.class,meeting),exclusion=j.queryForObject("SELECT exclusion_revision_id FROM meeting WHERE id=?",String.class,meeting);String key="fix01-"+family+"-"+kind;
   var anchors=j.queryForList("SELECT id FROM evidence_link WHERE minutes_revision_id=? ORDER BY id",String.class,body).stream().map(id->new JdbcMinutesSavedCommands.Reanchor(id,0,5)).toList();
   Supplier<Map<String,Object>> call=()->switch(family){case "BODY"->new JdbcMinutesSavedCommands(a,this::snapshot,pin,limits()).saveBody(actor(),meeting,key,r,body,"Changed title","Changed body",anchors,UUID.randomUUID().toString());case "REVIEW"->new JdbcMinutesReviews(a,this::snapshot,pin,limits()).review(actor(),meeting,key,r,"BODY",body,"SOURCE_SUPPORT","WHOLE","SUPPORTED",null,null,UUID.randomUUID().toString());default->new JdbcMinutesSettingsCommands(a,this::snapshot,pin,prompt,model,limits()).replaceExclusions(actor(),meeting,key,r,exclusion,List.of(),UUID.randomUUID().toString());};
   RuntimeException injected=kind.equals("ORDINARY")?MinutesContracts.fail(MinutesContracts.FailureCode.PROFILE_UNAVAILABLE):kind.equals("CORRUPTION")?MinutesContracts.fail(MinutesContracts.FailureCode.PROVENANCE_UNAVAILABLE):new IllegalArgumentException("Injected programming failure");
   race(call,injected,kind.equals("ORDINARY"));
  });assertAll(assertions);
 }
 void race(Supplier<Map<String,Object>> call,RuntimeException injected,boolean ordinary)throws Exception {
  var winner=new java.util.concurrent.atomic.AtomicReference<Map<String,Object>>();var afterWinner=new java.util.concurrent.atomic.AtomicReference<Map<String,Object>>();var entered=new java.util.concurrent.atomic.AtomicBoolean();Thread loser=Thread.currentThread();
  probe.before=sql->{if(Thread.currentThread()==loser&&sql.startsWith("SELECT id,owner_id,revision,cancel_epoch")&&entered.compareAndSet(false,true)){probe.before=s->{};var executor=Executors.newSingleThreadExecutor();try{winner.set(executor.submit(call::get).get(30,TimeUnit.SECONDS));afterWinner.set(effectSnapshot());}catch(Exception e){throw new IllegalStateException(e);}finally{executor.shutdownNow();}throw injected;}};
  try{if(ordinary){var result=call.get();assertEquals(true,result.get("replayed"));assertEquals(winner.get().get("resultId"),result.get("resultId"));}else assertSame(injected,assertThrows(RuntimeException.class,call::get));assertTrue(entered.get(),"Actual initial receipt miss then preparation hook");assertEquals(afterWinner.get(),effectSnapshot());}finally{probe.before=s->{};probe.events.clear();}
 }
 Map<String,Object> effectSnapshot(){var m=new TreeMap<String,Object>();m.put("root",j.queryForMap("SELECT * FROM meeting WHERE id=?",meeting));for(String table:List.of("minutes_revision","element_set","result_review","exclusion_revision","generation_candidate"))m.put(table,j.queryForList("SELECT * FROM "+table+" WHERE meeting_id=? ORDER BY id",meeting));m.put("receipts",j.queryForList("SELECT * FROM idempotency_record WHERE resource_id=? ORDER BY scope_hash",meeting));m.put("audit",j.queryForList("SELECT * FROM audit_event WHERE resource_id=? ORDER BY id",meeting));m.put("outbox",j.queryForList("SELECT * FROM task_outbox WHERE subject_id=? ORDER BY sequence_id",meeting));return m;}
}
