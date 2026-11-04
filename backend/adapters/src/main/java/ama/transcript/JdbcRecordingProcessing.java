package ama.transcript;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.*;
import ama.contract.ProcessingContracts.*;
import ama.contract.TranscriptContracts.*;
import ama.recording.JdbcRecordingTranscriptSources;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.util.concurrent.*;

/** Resumable processing of a sealed recording. Every external command retains its original identity. */
public final class JdbcRecordingProcessing implements AutoCloseable {
    private final JdbcTranscriptAuthority authority;
    private final JdbcRecordingTranscriptSources sources;
    private final JdbcProcessingIntake intake;
    private final JdbcMaterialEnhancements enhancements;
    private final JdbcTemplate jdbc;
    private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"recording-processing");t.setDaemon(true);return t;});
    private final Set<String> dispatched=ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),r->{var t=new Thread(r,"recording-processing-stage");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());

    public JdbcRecordingProcessing(JdbcTranscriptAuthority authority,JdbcRecordingTranscriptSources sources,JdbcProcessingIntake intake,JdbcSavedTranscripts saved,JdbcMaterialEnhancements enhancements){
        this.authority=authority;this.sources=sources;this.intake=intake;this.enhancements=enhancements;
        jdbc=new JdbcTemplate(authority.dataSource());jdbc.setQueryTimeout(5);
    }
    private void prepare(Actor actor,String meeting){authority.savedAccess().prepareExternalRead(actor,meeting);}
    private void gate(Actor actor,String meeting){authority.fileRegistry().lock(actor,meeting,MeetingAccess.Action.EDIT);}
    private Map<String,Object> row(String session){var rows=jdbc.queryForList("SELECT * FROM recording_processing WHERE session_id=?",session);return rows.isEmpty()?null:rows.getFirst();}
    private Map<String,Object> root(String meeting){return jdbc.queryForMap("SELECT revision,material_snapshot_id,materials_pending,transcript_revision_id FROM meeting WHERE id=?",meeting);}
    private static long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    private static ama.contract.TranscriptContracts.Failure conflict(String code){return new ama.contract.TranscriptContracts.Failure(409,code);}

    public Map<String,Object> accept(Actor actor,String session,String key,long expected,String snapshot){
        ama.contract.MinutesContracts.uuid(session);ama.contract.MinutesContracts.uuid(key);if(snapshot!=null)ama.contract.MinutesContracts.uuid(snapshot);
        var recording=jdbc.queryForMap("SELECT meeting_id,state FROM recording_session WHERE session_id=?",session);String meeting=(String)recording.get("meeting_id");prepare(actor,meeting);
        authority.transactionRunner().required(()->{
            gate(actor,meeting);var old=row(session);
            if(old!=null){if(!actor.enterpriseId().equals(old.get("actor_id"))||!key.equals(old.get("request_key"))||expected!=number(old,"expected_revision")||!Objects.equals(snapshot,old.get("material_snapshot_id")))throw conflict("IDEMPOTENCY_CONFLICT");return null;}
            var current=root(meeting);
            if(!"FINISHED".equals(jdbc.queryForObject("SELECT state FROM recording_session WHERE session_id=? FOR UPDATE",String.class,session)))throw conflict("RECORDING_NOT_SEALED");
            if(number(current,"revision")!=expected||number(current,"materials_pending")!=0||!Objects.equals(snapshot,current.get("material_snapshot_id")))throw conflict("RECORDING_MATERIALS_CHANGED");
            jdbc.update("INSERT INTO recording_processing(session_id,meeting_id,actor_id,request_key,expected_revision,material_snapshot_id,state,decision_key,import_request_key,import_revision,updated_at) VALUES(?,?,?,?,?,?,'WAITING_ARCHIVE',?,?,?,UTC_TIMESTAMP(6))",session,meeting,actor.enterpriseId(),key,expected,snapshot,UUID.randomUUID().toString(),key,expected);return null;
        });return read(actor,session);
    }
    public Map<String,Object> read(Actor actor,String session){
        ama.contract.MinutesContracts.uuid(session);String meeting=jdbc.queryForObject("SELECT meeting_id FROM recording_session WHERE session_id=?",String.class,session);prepare(actor,meeting);
        return authority.transactionRunner().required(()->{gate(actor,meeting);var value=row(session);var result=new LinkedHashMap<String,Object>();result.put("sessionId",session);result.put("meetingId",meeting);result.put("state",value==null?"NOT_REQUESTED":value.get("state"));if(value!=null){result.put("materialSnapshotId",value.get("material_snapshot_id"));result.put("taskId",value.get("task_id"));result.put("errorCode",value.get("error_code"));
                result.put("materialsChanged",Boolean.TRUE.equals(value.get("pending_materials_changed"))||value.get("pending_materials_changed") instanceof Number changed&&changed.intValue()==1||!Objects.equals(value.get("material_snapshot_id"),root(meeting).get("material_snapshot_id")));
                result.put("materialFiles",jdbc.queryForList("SELECT r.id materialRevisionId,u.display_name name,r.parse_state state FROM material_snapshot_item s JOIN material_revision r ON r.id=s.material_revision_id JOIN material_reference m ON m.id=r.reference_id JOIN upload_session u ON u.completed_file_id=r.content_file_id AND u.state='COMPLETED' WHERE s.snapshot_id=? AND m.meeting_id=? ORDER BY s.ordinal LIMIT 64",value.get("material_snapshot_id"),meeting));
            }return result;});
    }
    public Map<String,Object> retry(Actor actor,String session,String expectedTask){
        if(expectedTask!=null)ama.contract.MinutesContracts.uuid(expectedTask);
        var current=read(actor,session);String meeting=(String)current.get("meetingId");
        if("FAILED".equals(current.get("state"))&&"RECORDING_ARCHIVE_FAILED".equals(current.get("errorCode")))sources.retryArchive(actor,session);
        authority.transactionRunner().required(()->{
            gate(actor,meeting);var value=row(session);if(value==null)throw conflict("RECORDING_PROCESS_NOT_REQUESTED");
            if(!"FAILED".equals(value.get("state"))||!Objects.equals(expectedTask,value.get("task_id")))return null;
            if(value.get("task_id")!=null&&value.get("source_revision_id")==null){
                String taskState=jdbc.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,value.get("task_id"));
                if(Set.of("FAILED","CANCELLED","STALE").contains(taskState)){
                    var now=root(meeting);
                    if(number(now,"revision")!=number(value,"import_revision")+1||!Objects.equals(now.get("material_snapshot_id"),value.get("material_snapshot_id"))||number(now,"materials_pending")!=0)throw conflict("ENHANCEMENT_SOURCE_CHANGED");
                    jdbc.update("UPDATE recording_processing SET task_id=NULL,import_request_key=?,import_revision=? WHERE session_id=?",UUID.randomUUID().toString(),number(now,"revision"),session);
                    value=row(session);
                }
            }
            jdbc.update("UPDATE recording_processing SET state=?,error_code=NULL,lease_until=NULL,lease_owner=NULL WHERE session_id=? AND state='FAILED'",value.get("source_revision_id")!=null?"ENHANCING":value.get("task_id")!=null?"TRANSCRIBING":"WAITING_ARCHIVE",session);return null;
        });return read(actor,session);
    }
    public void start(){scheduler.scheduleWithFixedDelay(()->{try{for(var session:jdbc.queryForList("SELECT session_id FROM recording_processing WHERE state IN ('WAITING_ARCHIVE','COPYING','TRANSCRIBING','ENHANCING') AND (lease_until IS NULL OR lease_until<UTC_TIMESTAMP(6)) ORDER BY updated_at LIMIT 8",String.class))if(dispatched.add(session))try{workers.execute(()->{try{runOnce(session);}finally{dispatched.remove(session);}});}catch(RejectedExecutionException full){dispatched.remove(session);}}catch(RuntimeException failure){System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,"Recording processing poll unavailable");}},0,1,TimeUnit.SECONDS);}
    public void runOnce(String session){
        String lease=UUID.randomUUID().toString();
        if(jdbc.update("UPDATE recording_processing SET lease_owner=?,lease_until=UTC_TIMESTAMP(6)+INTERVAL 5 MINUTE WHERE session_id=? AND state IN ('WAITING_ARCHIVE','COPYING','TRANSCRIBING','ENHANCING') AND (lease_until IS NULL OR lease_until<UTC_TIMESTAMP(6))",lease,session)!=1)return;
        final var job=row(session);String meeting=(String)job.get("meeting_id");var actor=new Actor((String)job.get("actor_id"),Status.ACTIVE,Set.of(Role.USER));
        try{
            prepare(actor,meeting);
            var source=sources.read(actor,session);
            if(!Boolean.TRUE.equals(source.get("archiveAvailable"))){
                if(Set.of("FAILED","CANCELLED","STALE").contains(sources.archiveStatus(actor,session).state()))throw conflict("RECORDING_ARCHIVE_FAILED");
                return;
            }
            if(!"READY".equals(source.get("state"))){sources.accept(actor,session,(String)job.get("request_key"));state(session,lease,"COPYING");return;}
            if(job.get("task_id")==null&&source.get("transcriptionTaskId")!=null&&Objects.equals(job.get("import_request_key"),job.get("request_key"))){
                long importedRevision=jdbc.queryForObject("SELECT business_version FROM durable_task WHERE id=?",Long.class,source.get("transcriptionTaskId"));
                if(importedRevision<1)throw conflict("RECORDING_IMPORT_REVISION_INVALID");
                jdbc.update("UPDATE recording_processing SET task_id=?,import_revision=?,state='TRANSCRIBING' WHERE session_id=? AND lease_owner=?",source.get("transcriptionTaskId"),importedRevision-1,session,lease);return;
            }
            if(job.get("task_id")==null){
                var accepted=intake.accept(actor,meeting,(String)job.get("import_request_key"),new ImportRequest(number(job,"import_revision"),List.of((String)source.get("fileId"))),UUID.randomUUID().toString());
                jdbc.update("UPDATE recording_processing SET task_id=?,state='TRANSCRIBING' WHERE session_id=? AND lease_owner=?",accepted.taskId(),session,lease);return;
            }
            var task=jdbc.queryForMap("SELECT state,output_id,business_version FROM durable_task WHERE id=?",job.get("task_id"));
            if(Set.of("FAILED","CANCELLED","STALE").contains(task.get("state")))throw conflict("RECORDING_TRANSCRIPTION_FAILED");
            if(!"SUCCEEDED".equals(task.get("state")))return;
            authority.transactionRunner().required(()->{gate(actor,meeting);var current=root(meeting);var original=row(session);if(original.get("source_revision_id")==null){if(current.get("transcript_revision_id")!=null&&!Objects.equals(current.get("transcript_revision_id"),task.get("output_id"))||number(current,"revision")!=number(task,"business_version")||!Objects.equals(current.get("material_snapshot_id"),job.get("material_snapshot_id"))||number(current,"materials_pending")!=0)throw conflict("ENHANCEMENT_SOURCE_CHANGED");jdbc.update("UPDATE recording_processing SET source_revision_id=?,source_meeting_revision=?,state='ENHANCING' WHERE session_id=? AND lease_owner=?",task.get("output_id"),number(current,"revision"),session,lease);}return null;});
            var enhanced=row(session);String raw=(String)enhanced.get("source_revision_id"),key=(String)job.get("request_key"),decision=(String)job.get("decision_key");long revision=number(enhanced,"source_meeting_revision");
            var candidate=enhancements.generateInitial(actor,meeting,key,revision,raw,(String)job.get("material_snapshot_id"));
            if(!Set.of("READY","APPLYING","APPLIED").contains(candidate.get("state")))throw conflict("ENHANCEMENT_SOURCE_CHANGED");
            enhancements.adoptInitial(actor,meeting,(String)candidate.get("candidateId"),decision);
            complete(actor,meeting,session,lease,"SUCCEEDED",null);
        }catch(RuntimeException failure){String code=failure instanceof ama.contract.TranscriptContracts.Failure f?f.code():failure instanceof ama.contract.ProcessingContracts.Failure f?f.code():failure.getClass().getSimpleName();if(!code.matches("[A-Za-z0-9_]{1,128}"))code="RECORDING_PROCESSING_UNAVAILABLE";if(code.contains("CHANGED"))complete(actor,meeting,session,lease,"STALE",code);else jdbc.update("UPDATE recording_processing SET state='FAILED',error_code=? WHERE session_id=? AND lease_owner=?",code,session,lease);}
        finally{jdbc.update("UPDATE recording_processing SET lease_owner=NULL,lease_until=NULL,updated_at=UTC_TIMESTAMP(6) WHERE session_id=? AND lease_owner=?",session,lease);}
    }
    private void state(String session,String lease,String state){jdbc.update("UPDATE recording_processing SET state=?,error_code=NULL WHERE session_id=? AND lease_owner=?",state,session,lease);}
    private void complete(Actor actor,String meeting,String session,String lease,String state,String error){
        authority.transactionRunner().required(()->{
            gate(actor,meeting);
            if(jdbc.update("UPDATE recording_processing SET state=?,error_code=? WHERE session_id=? AND lease_owner=?",state,error,session,lease)!=1)return null;
            var finished=row(session);
            if(Boolean.TRUE.equals(finished.get("pending_materials_changed"))||finished.get("pending_materials_changed") instanceof Number flag&&flag.intValue()==1){
                boolean failed=Boolean.TRUE.equals(finished.get("pending_materials_failed"))||finished.get("pending_materials_failed") instanceof Number flag&&flag.intValue()==1;
                JdbcMaterials.advanceSnapshot(jdbc,new ama.task.JdbcSubjectFences(authority.dataSource(),Set.of("MEETING")),meeting,(String)finished.get("pending_material_snapshot_id"),failed);
                jdbc.update("UPDATE recording_processing SET pending_materials_changed=0 WHERE session_id=?",session);
            }
            return null;
        });
    }
    @Override public void close(){scheduler.shutdownNow();workers.shutdownNow();try{scheduler.awaitTermination(5,TimeUnit.SECONDS);workers.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}
