package ama.transcript;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.util.concurrent.*;

/** Durable continuation of the RAW import. A lease loss replays the original generation/decision keys. */
public final class OfflineMaterialProcessing implements AutoCloseable {
 private final JdbcTranscriptAuthority authority;
 private final JdbcMaterialEnhancements enhancement;
 private final JdbcProcessingIntake intake;
 private final JdbcRecordingProcessing recording;
 private final JdbcTemplate jdbc;
 private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"offline-material-processing");t.setDaemon(true);return t;});
 public OfflineMaterialProcessing(JdbcTranscriptAuthority authority,JdbcMaterialEnhancements enhancement,JdbcProcessingIntake intake,JdbcRecordingProcessing recording){
  this.authority=authority;this.enhancement=enhancement;this.intake=intake;this.recording=recording;this.jdbc=new JdbcTemplate(authority.dataSource());jdbc.setQueryTimeout(5);
 }
 public void start(){executor.scheduleWithFixedDelay(()->{try{pollAdjustments();poll();}catch(RuntimeException ex){System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,"Offline processing poll failed",ex);}},0,1,TimeUnit.SECONDS);}
 public Map<String,Object> adjustment(Actor actor,String meeting){
  ama.contract.OutboxContracts.uuid(meeting);authority.savedAccess().prepareExternalRead(actor,meeting);
  return authority.transactionRunner().required(()->{authority.fileRegistry().lock(actor,meeting,MeetingAccess.Action.EDIT);
   var rows=jdbc.queryForList("SELECT id,stage,candidate_id AS candidateId,error_code AS errorCode FROM material_adjustment WHERE meeting_id=? AND actor_id=? ORDER BY meeting_revision DESC LIMIT 1",meeting,actor.enterpriseId());
   return rows.isEmpty()?Map.of("stage","NONE"):rows.getFirst();});
 }
 public void retryAdjustment(Actor actor,String meeting,String id){
  ama.contract.OutboxContracts.uuid(id);authority.savedAccess().prepareExternalRead(actor,meeting);
  authority.transactionRunner().required(()->{authority.fileRegistry().lock(actor,meeting,MeetingAccess.Action.EDIT);
   jdbc.update("UPDATE material_adjustment SET stage='PENDING',error_code=NULL WHERE id=? AND meeting_id=? AND actor_id=? AND stage='FAILED'",id,meeting,actor.enterpriseId());return null;});
 }
 private void pollAdjustments(){
  var rows=jdbc.queryForList("SELECT * FROM material_adjustment WHERE stage='PENDING' AND (lease_until IS NULL OR lease_until<UTC_TIMESTAMP(6)) ORDER BY meeting_revision LIMIT 4");
  for(var row:rows){
   String lease=UUID.randomUUID().toString(),id=(String)row.get("id");
   if(jdbc.update("UPDATE material_adjustment SET lease_id=?,lease_until=UTC_TIMESTAMP(6)+INTERVAL 5 MINUTE WHERE id=? AND stage='PENDING' AND (lease_until IS NULL OR lease_until<UTC_TIMESTAMP(6))",lease,id)!=1)continue;
   try{
    var actor=new Actor((String)row.get("actor_id"),Status.ACTIVE,Set.of(Role.USER));
    if(row.get("source_id")==null){
     restartInitial(actor,row);
     jdbc.update("UPDATE material_adjustment SET stage='SUBMITTED',lease_id=NULL,lease_until=NULL WHERE id=? AND lease_id=?",id,lease);
     continue;
    }
    var result=enhancement.generate(actor,(String)row.get("meeting_id"),id,((Number)row.get("meeting_revision")).longValue(),(String)row.get("source_id"),(String)row.get("snapshot_id"));
    jdbc.update("UPDATE material_adjustment SET stage='READY',candidate_id=?,lease_id=NULL,lease_until=NULL WHERE id=? AND lease_id=?",result.get("candidateId"),id,lease);
   }catch(RuntimeException failure){String code=failure instanceof ama.contract.TranscriptContracts.Failure f?f.code():"ENHANCEMENT_UNAVAILABLE";jdbc.update("UPDATE material_adjustment SET stage=?,error_code=?,lease_id=NULL,lease_until=NULL WHERE id=? AND lease_id=?",code.contains("CHANGED")?"STALE":"FAILED",code,id,lease);}
  }
 }
 private void restartInitial(Actor actor,Map<String,Object> row){
  String meeting=(String)row.get("meeting_id"),key=(String)row.get("id");
  long revision=((Number)row.get("meeting_revision")).longValue();
  if(row.get("recording_session_id")!=null){
   recording.changeRun(actor,(String)row.get("recording_session_id"),(String)row.get("original_run_id"),"restart",key,revision);
  }else{
   var files=jdbc.queryForList("""
    SELECT f.file_id FROM durable_task t JOIN transcription_input i ON i.id=t.input_id
    JOIN input_batch_file f ON f.batch_id=i.batch_id
    WHERE t.id=? AND t.subject_id=? AND i.meeting_id=? AND t.task_type='TRANSCRIPT_IMPORT_V1'
    ORDER BY f.ordinal
    """,String.class,row.get("original_task_id"),meeting,meeting);
   intake.accept(actor,meeting,key,new ama.contract.ProcessingContracts.ImportRequest(revision,files),UUID.randomUUID().toString(),true);
  }
 }
 private void poll(){
  var rows=jdbc.queryForList("""
    SELECT p.*,t.state raw_state,t.output_id,t.business_version FROM offline_material_processing p
    JOIN durable_task t ON t.id=p.task_id WHERE p.stage IN ('TRANSCRIBING','ENHANCING','ADOPTING')
    AND t.state IN ('SUCCEEDED','FAILED','CANCELLED','STALE') AND (p.lease_until IS NULL OR p.lease_until<UTC_TIMESTAMP(6))
    ORDER BY t.created_at LIMIT 8
    """);
  for(var r:rows){
   String task=(String)r.get("task_id"),meeting=(String)r.get("meeting_id"),lease=UUID.randomUUID().toString();
   if(jdbc.update("UPDATE offline_material_processing SET lease_id=?,lease_until=UTC_TIMESTAMP(6)+INTERVAL 5 MINUTE WHERE task_id=? AND stage=? AND (lease_until IS NULL OR lease_until<UTC_TIMESTAMP(6))",lease,task,r.get("stage"))!=1)continue;
   try{
    String rawState=(String)r.get("raw_state");
    if(!"SUCCEEDED".equals(rawState)){finish(task,lease,rawState,"RAW_"+rawState);continue;}
    if(!"ADOPTING".equals(r.get("stage")))jdbc.update("UPDATE offline_material_processing SET stage='ENHANCING' WHERE task_id=? AND lease_id=?",task,lease);
    var actor=new Actor((String)r.get("actor_id"),Status.ACTIVE,Set.of(Role.USER));
    var result=enhancement.generateInitial(actor,meeting,(String)r.get("generation_key"),((Number)r.get("business_version")).longValue(),(String)r.get("output_id"),(String)r.get("snapshot_id"));
    if(jdbc.update("UPDATE offline_material_processing SET stage='ADOPTING' WHERE task_id=? AND lease_id=?",task,lease)!=1)continue;
    enhancement.adoptInitial(actor,meeting,(String)result.get("candidateId"),(String)r.get("decision_key"));
    finish(task,lease,"SUCCEEDED",null);
   }catch(RuntimeException failure){String code=failure instanceof ama.contract.TranscriptContracts.Failure f?f.code():"ENHANCEMENT_UNAVAILABLE";finish(task,lease,code.contains("CHANGED")?"STALE":"FAILED",code);}
  }
 }
 private void finish(String task,String lease,String stage,String error){
  authority.transactionRunner().required(()->{
   var rows=jdbc.queryForList("SELECT meeting_id,actor_id FROM offline_material_processing WHERE task_id=? AND lease_id=?",task,lease);if(rows.isEmpty())return null;
   String meeting=(String)rows.getFirst().get("meeting_id");var actor=new Actor((String)rows.getFirst().get("actor_id"),Status.ACTIVE,Set.of(Role.USER));
   authority.fileRegistry().lock(actor,meeting,MeetingAccess.Action.EDIT);
   if(jdbc.update("UPDATE offline_material_processing SET stage=?,error_code=?,lease_id=NULL,lease_until=NULL WHERE task_id=? AND lease_id=?",stage,error,task,lease)==1)applyPending(meeting,task);
   return null;});
 }
 private void applyPending(String meeting,String task){
  var pending=jdbc.queryForList("SELECT pending_snapshot_id,pending_failed FROM offline_material_processing WHERE task_id=? AND pending_changed=1 FOR UPDATE",task);
  if(pending.isEmpty())return;var row=pending.getFirst();JdbcMaterials.advanceSnapshot(jdbc,new ama.task.JdbcSubjectFences(authority.dataSource(),Set.of("MEETING")),meeting,(String)row.get("pending_snapshot_id"),((Number)row.get("pending_failed")).intValue()!=0);
  jdbc.update("UPDATE offline_material_processing SET pending_changed=0 WHERE task_id=?",task);
 }
 public void command(Actor actor,String meeting,String task,boolean cancel){
  ama.contract.OutboxContracts.uuid(meeting);ama.contract.OutboxContracts.uuid(task);authority.savedAccess().prepareExternalRead(actor,meeting);
  authority.transactionRunner().required(()->{authority.fileRegistry().lock(actor,meeting,MeetingAccess.Action.EDIT);
   var rows=jdbc.queryForList("SELECT p.stage,t.state raw_state FROM offline_material_processing p JOIN durable_task t ON p.task_id=t.id WHERE p.meeting_id=? AND p.task_id=? AND p.actor_id=? FOR UPDATE",meeting,task,actor.enterpriseId());
   if(rows.isEmpty())throw new ama.contract.TranscriptContracts.Failure(404,"PROCESSING_NOT_FOUND");
   var row=rows.getFirst();String state=(String)row.get("stage");
   if(cancel&&Set.of("TRANSCRIBING","ENHANCING","FAILED").contains(state))jdbc.update("UPDATE offline_material_processing SET stage='CANCELLED',error_code=NULL,lease_id=NULL,lease_until=NULL WHERE task_id=?",task);
   else if(!cancel&&"FAILED".equals(state)&&"SUCCEEDED".equals(row.get("raw_state")))jdbc.update("UPDATE offline_material_processing SET stage='ENHANCING',error_code=NULL WHERE task_id=?",task);
   else if(!(cancel&&state.equals("CANCELLED")||!cancel&&Set.of("ENHANCING","ADOPTING","SUCCEEDED").contains(state)))throw new ama.contract.TranscriptContracts.Failure(409,"PROCESSING_STAGE_CHANGED");
   if(cancel)applyPending(meeting,task);
   return null;});
 }
 public void close(){executor.shutdownNow();}
}
