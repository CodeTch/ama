package ama.api;

import ama.contract.IdentityContracts.Actor;
import ama.contract.IdentityContracts.Role;
import ama.contract.IdentityContracts.Status;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Resume confirmation after browser or process loss. Every write goes through the owner command gate. */
final class ConfirmationGenerationDispatcher implements AutoCloseable {
 private final JdbcTemplate jdbc;
 private final MinutesConfiguration.Runtime runtime;
 private final java.util.concurrent.ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor(r->{
  var worker=new Thread(r,"ama-confirmation-generation");worker.setDaemon(true);return worker;
 });
 ConfirmationGenerationDispatcher(DataSource source,MinutesConfiguration.Runtime runtime){
  jdbc=new JdbcTemplate(source);jdbc.setQueryTimeout(3);this.runtime=runtime;
 }
 public void start(){executor.scheduleWithFixedDelay(this::poll,1,2,TimeUnit.SECONDS);}
 private final java.util.concurrent.atomic.AtomicBoolean wakeQueued=new java.util.concurrent.atomic.AtomicBoolean();
 void wake(){
  if(executor.isShutdown()||!wakeQueued.compareAndSet(false,true))return;
  try{executor.execute(()->{wakeQueued.set(false);poll();});}
  catch(java.util.concurrent.RejectedExecutionException stopped){wakeQueued.set(false);}
 }
 public void close(){executor.shutdownNow();}
 private record Intent(String confirmation,String meeting,String actor,String current,String owner,long revision,String template,boolean active){}
 private void poll(){
  try{
   var waiting=jdbc.query("""
    SELECT i.confirmation_id,c.meeting_id,i.actor_id,m.transcript_confirmation_id,m.owner_id,m.revision,m.template_revision_id,
           m.lifecycle='ACTIVE' AND m.governance_state='ACTIVE' AND m.ever_published=0
    FROM transcript_generation_intent i JOIN transcript_confirmation c ON c.id=i.confirmation_id JOIN meeting m ON m.id=c.meeting_id
    WHERE i.state='WAITING' AND i.next_attempt_at<=UTC_TIMESTAMP(6) ORDER BY i.next_attempt_at,i.confirmation_id LIMIT 4
    """,(r,n)->new Intent(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getLong(6),r.getString(7),r.getBoolean(8)));
   for(var intent:waiting){
    if(!intent.confirmation.equals(intent.current)||!intent.actor.equals(intent.owner)){
     jdbc.update("UPDATE transcript_generation_intent SET state='SUPERSEDED' WHERE confirmation_id=? AND state='WAITING'",intent.confirmation);continue;
    }
    if(jdbc.update("UPDATE transcript_generation_intent SET next_attempt_at=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 15 SECOND) WHERE confirmation_id=? AND state='WAITING' AND next_attempt_at<=UTC_TIMESTAMP(6)",intent.confirmation)==0)continue;
    if(!intent.active)continue;
    try{dispatch(intent);}catch(RuntimeException failure){
     log(failure);
     jdbc.update("UPDATE transcript_generation_intent SET last_submit_failed=TRUE WHERE confirmation_id=? AND state='WAITING'",intent.confirmation);
    }
   }
  }catch(RuntimeException failure){log(failure);}
 }
 private static void log(RuntimeException failure){String reason=failure instanceof ama.contract.MinutesContracts.Failure f?f.code().name():failure instanceof ama.contract.TranscriptContracts.Failure f?f.code():failure.getClass().getSimpleName();
  String path=java.util.Arrays.stream(failure.getStackTrace()).filter(frame->frame.getClassName().startsWith("ama.")).limit(8).map(StackTraceElement::toString).collect(java.util.stream.Collectors.joining(" / "));
  System.getLogger(ConfirmationGenerationDispatcher.class.getName()).log(System.Logger.Level.WARNING,"Confirmation generation will retry: "+reason+" at "+path);}
 private void dispatch(Intent intent){
  runtime.requireCombined();
  var actor=new Actor(intent.actor,Status.ACTIVE,Set.of(Role.USER));String trace=UUID.randomUUID().toString();
  String key="confirm-"+intent.confirmation+"-"+intent.revision;
  if(intent.template==null){
   var templates=(List<?>)runtime.queries().templates(actor,intent.meeting,50,null).get("items");
   String template=templates.stream().map(Map.class::cast).filter(t->Boolean.TRUE.equals(t.get("default"))&&Boolean.TRUE.equals(t.get("selectable")))
    .map(t->(String)t.get("revisionId")).findFirst().orElseThrow();
   runtime.initial().initialize(actor,intent.meeting,key,intent.revision,template,trace);
   jdbc.update("UPDATE transcript_generation_intent SET next_attempt_at=UTC_TIMESTAMP(6),last_submit_failed=FALSE WHERE confirmation_id=? AND state='WAITING'",intent.confirmation);
   wake();
  }else runtime.generation().enqueueConfirmation(actor,intent.meeting,key,intent.revision,intent.confirmation,trace);
 }
}
