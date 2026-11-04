package ama.storage;

import ama.transcript.*;
import ama.task.*;
import ama.model.HttpMaterialEnhancement;
import ama.contract.TranscriptContracts.*;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static ama.storage.ControlledSpeechImportIT.*;

/** Real recorded bytes, archive, speech task and sibling enhancement, including restart replay. */
class RecordingProcessingIT {
 @TempDir Path root;
 @BeforeAll static void schema(){ControlledSpeechImportIT.schema();}
 JdbcSavedTranscripts saved(RecordingTranscriptSourceIT.Fixture f,RecordingTranscriptSourceIT.Pipeline pipeline){
  var limits=new TranscriptAcquisitionBudget(16777216,16777216,16777216,134217728,67108864,33554432,128,256,256);
  var corrections=CorrectionInstallation.compose(new CorrectionInstallation.Declaration("CONTROLLED_SPEECH","1","recording-processing-it"),List.of());
  return new JdbcSavedTranscripts(f.components.authority(),pipeline.settings().archives(),limits,corrections,f.fences,f.outbox,new ama.audit.JdbcAuditAdapter(source),new SavedTranscriptTransitions("AUTHORITY_CONTROLLED","AUTHORITY_CONTROLLED","2",List.of(),List.of()));
 }
 void completeImport(RecordingTranscriptSourceIT.Fixture f,RecordingTranscriptSourceIT.Pipeline pipeline,ama.contract.TaskContracts.TaskLease lease)throws Exception{
  var heartbeats=java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
  try{heartbeats.scheduleWithFixedDelay(()->f.queue.renew(lease),1,1,java.util.concurrent.TimeUnit.SECONDS);var carrier=TranscriptImportRuntime.prepare(f.queue,lease,pipeline.settings(),NEVER);heartbeats.shutdownNow();assertTrue(heartbeats.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS));assertTrue(f.queue.renew(lease));assertTrue(f.queue.completeWithEffects(lease,carrier));}
  finally{heartbeats.shutdownNow();assertTrue(heartbeats.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS));}
 }
 @Test void firstRawWithoutWorkingPointerBecomesWorkingAndRestartReplaysWithoutAnotherVersion()throws Exception{
  var support=new RecordingTranscriptSourceIT();support.root=root;
  try(var f=support.new Fixture();var model=new HttpMaterialEnhancement(URI.create("http://127.0.0.1:8792"))){
   f.archive(128000);f.copies.accept(f.actor,f.session.sessionId(),id());assertTrue(f.copies.runOnce(f.session.sessionId()));
   var pipeline=support.pipeline(f);var lease=claimAvailable(f.queue,"TRANSCRIPT_IMPORT","RECORDING_PROCESSING_IT");completeImport(f,pipeline,lease);
   String raw=j.queryForObject("SELECT output_id FROM durable_task WHERE id=?",String.class,lease.taskId());assertNotNull(raw);
   assertNull(j.queryForObject("SELECT transcript_revision_id FROM meeting WHERE id=?",String.class,f.meeting));
   var saved=saved(f,pipeline);var enhancements=new JdbcMaterialEnhancements(f.components.authority(),saved,model);String key=id();
   try(var process=new JdbcRecordingProcessing(f.components.authority(),f.copies,null,saved,enhancements)){
    assertEquals("WAITING_ARCHIVE",process.accept(f.actor,f.session.sessionId(),key,1,null).get("state"));
    assertThrows(ama.contract.TranscriptContracts.Failure.class,()->process.accept(f.actor,f.session.sessionId(),id(),1,null));
    process.runOnce(f.session.sessionId());
    assertEquals(0,j.queryForObject("SELECT import_revision FROM recording_processing WHERE session_id=?",Long.class,f.session.sessionId()),"A recovered pre-existing import retains its real original revision for retries");
    process.runOnce(f.session.sessionId());
    assertEquals("SUCCEEDED",process.read(f.actor,f.session.sessionId()).get("state"),process.read(f.actor,f.session.sessionId()).toString());
   }
   var before=j.queryForMap("SELECT revision,transcript_revision_id FROM meeting WHERE id=?",f.meeting);
   assertNotEquals(raw,before.get("transcript_revision_id"));assertEquals(2,j.queryForObject("SELECT COUNT(*) FROM transcript_revision WHERE meeting_id=?",Integer.class,f.meeting));
   try(var recovered=new JdbcRecordingProcessing(f.components.authority(),f.copies,null,saved,enhancements)){
    assertEquals("SUCCEEDED",recovered.accept(f.actor,f.session.sessionId(),key,1,null).get("state"));recovered.runOnce(f.session.sessionId());
    assertEquals(before,j.queryForMap("SELECT revision,transcript_revision_id FROM meeting WHERE id=?",f.meeting));
   }
  }
 }
 @Test void manualWorkingDraftCannotBeOverwrittenByLateInitialEnhancement()throws Exception{
  var support=new RecordingTranscriptSourceIT();support.root=root;
  try(var f=support.new Fixture();var model=new HttpMaterialEnhancement(URI.create("http://127.0.0.1:8792"))){
   f.archive(128000);f.copies.accept(f.actor,f.session.sessionId(),id());assertTrue(f.copies.runOnce(f.session.sessionId()));var pipeline=support.pipeline(f);
   var lease=claimAvailable(f.queue,"TRANSCRIPT_IMPORT","RECORDING_PROCESSING_IT");completeImport(f,pipeline,lease);
   var saved=saved(f,pipeline);var enhancements=new JdbcMaterialEnhancements(f.components.authority(),saved,model);
   try(var process=new JdbcRecordingProcessing(f.components.authority(),f.copies,null,saved,enhancements)){
    process.accept(f.actor,f.session.sessionId(),id(),1,null);process.runOnce(f.session.sessionId());
    String raw=j.queryForObject("SELECT output_id FROM durable_task WHERE id=?",String.class,lease.taskId());
    @SuppressWarnings("unchecked") var selected=(Map<String,Object>)saved.readOwner(f.actor,f.meeting,raw).get("selected");
    @SuppressWarnings("unchecked") var segments=(List<Map<String,Object>>)selected.get("segments");
    saved.mutate(f.actor,f.meeting,id(),new Command(Operation.SAVE,1,raw,segments.stream().map(s->new Edit((String)s.get("segmentId"),"人工修订保留。",List.of())).toList()),id());
    var before=j.queryForMap("SELECT revision,transcript_revision_id FROM meeting WHERE id=?",f.meeting);process.runOnce(f.session.sessionId());
    assertEquals("STALE",process.read(f.actor,f.session.sessionId()).get("state"));assertEquals(before,j.queryForMap("SELECT revision,transcript_revision_id FROM meeting WHERE id=?",f.meeting));
   }
  }
 }
 @Test void materialEditsDuringProcessingWaitWithoutChangingTheRunningTaskOrItsSourceSnapshot()throws Exception{
  var support=new RecordingTranscriptSourceIT();support.root=root;
  try(var f=support.new Fixture();var model=new HttpMaterialEnhancement(URI.create("http://127.0.0.1:8792"))){
   f.archive(128000);f.copies.accept(f.actor,f.session.sessionId(),id());assertTrue(f.copies.runOnce(f.session.sessionId()));var pipeline=support.pipeline(f);
   var saved=saved(f,pipeline);var enhancements=new JdbcMaterialEnhancements(f.components.authority(),saved,model);
   try(var process=new JdbcRecordingProcessing(f.components.authority(),f.copies,null,saved,enhancements)){
    process.accept(f.actor,f.session.sessionId(),id(),1,null);process.runOnce(f.session.sessionId());
    byte[] body="术语对照：我们下次会议=我们下次会议（追加材料）".getBytes(java.nio.charset.StandardCharsets.UTF_8);String hash=sha(body);
    var upload=f.uploads.begin(f.actor,f.meeting,id(),new ama.contract.FileContracts.Begin("MATERIAL_SOURCE","追加材料.txt",body.length,hash),id());
    f.uploads.part(f.actor,upload.uploadId(),1,new java.io.ByteArrayInputStream(body),body.length,hash,id());String file=f.uploads.complete(f.actor,upload.uploadId(),new ama.contract.FileContracts.Completion(List.of(new ama.contract.FileContracts.ClientPart(1,hash)),hash),id()).fileId();
    var materials=new JdbcMaterials(f.components.authority(),f.uploads,null,new ama.material.MaterialParser.Budget(1048576,1048576,100));materials.add(f.actor,f.meeting,id(),new JdbcMaterials.Selection(file,null,null,null),id());
    assertEquals(1,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,f.meeting));assertNull(j.queryForObject("SELECT material_snapshot_id FROM meeting WHERE id=?",String.class,f.meeting));
    String later=materials.list(f.actor,f.meeting).materialSnapshotId();assertNotNull(later);
    var lease=claimAvailable(f.queue,"TRANSCRIPT_IMPORT","RECORDING_PENDING_MATERIAL_IT");completeImport(f,pipeline,lease);
    process.runOnce(f.session.sessionId());assertEquals("SUCCEEDED",process.read(f.actor,f.session.sessionId()).get("state"),process.read(f.actor,f.session.sessionId()).toString());
    assertEquals(later,j.queryForObject("SELECT material_snapshot_id FROM meeting WHERE id=?",String.class,f.meeting));assertNull(process.read(f.actor,f.session.sessionId()).get("materialSnapshotId"));
    assertEquals(2,j.queryForObject("SELECT COUNT(*) FROM transcript_revision WHERE meeting_id=?",Integer.class,f.meeting));
    assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM transcript_segment s JOIN transcript_revision r ON r.id=s.revision_id WHERE r.meeting_id=? AND s.body LIKE '%追加材料%'",Integer.class,f.meeting));
   }
  }
 }
}
