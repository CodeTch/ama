package ama.storage;

import ama.contract.IdentityContracts.*;
import ama.contract.RecordingContracts;
import ama.db.TransactionRunner;
import ama.meeting.*;
import ama.recording.*;
import ama.task.*;
import ama.transcript.*;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static ama.storage.ControlledSpeechImportIT.*;

/** Actual recording archive -> real managed TRANSCRIPT_INPUT copy, never a forged upload. */
class RecordingTranscriptSourceIT {
 @TempDir(cleanup=org.junit.jupiter.api.io.CleanupMode.NEVER) Path root;
 @BeforeAll static void schema(){ControlledSpeechImportIT.schema();assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version='060'",Integer.class));}
 final class Fixture implements AutoCloseable {
  final ManagedLocalFixture nativeFiles;final FileStoragePort storage;final FileBackendCatalog catalog;final TransactionRunner tx;final JdbcSubjectFences fences;final JdbcOutbox outbox;final JdbcMeetingAccess access;final MeetingService meetings;final Actor actor;final String meeting;final JdbcRecordingPersistence persistence;final RecordingService recordings;final JdbcTranscriptAuthority.Components components;final FileUploads uploads;final JdbcTaskQueue queue;final RecordingContracts.Session session;final JdbcRecordingTranscriptSources copies;
  Fixture()throws Exception {this(java.util.function.UnaryOperator.identity());}
  Fixture(java.util.function.UnaryOperator<FileUploads> wrapper)throws Exception {
   nativeFiles=new ManagedLocalFixture(root.resolve(id()));storage=nativeFiles.startServer();catalog=new FileBackendCatalog(source,List.of(new FileBackendCatalog.Backend(nativeFiles.binding.id(),"local",1,"WRITABLE",storage,nativeFiles.binding)),nativeFiles.binding.id());catalog.configure();tx=new TransactionRunner(source);fences=new JdbcSubjectFences(source,Set.of("MEETING"));access=new JdbcMeetingAccess(source,tx,fences);
   var validators=new HashMap<String,OutboxReferenceValidator>(new MeetingEventReferences(source).validators());validators.putAll(new TranscriptEventReferences(source).validators());validators.putAll(RecordingArchiveRuntime.eventReferences(source));outbox=new JdbcOutbox(source,validators);
   String owner="recording-transcript-"+id();j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",owner,owner);actor=new Actor(owner,Status.ACTIVE,Set.of(Role.USER));meetings=new MeetingService(new JdbcMeetingRepository(source,tx,access,fences,outbox,new ama.audit.JdbcAuditAdapter(source),m->{}));meeting=meetings.create(actor,id(),"independent-recording-transcript-source").id();
   var purposes=new TranscriptFilePurposePolicy();components=JdbcTranscriptAuthority.create(source,tx,access,purposes,catalog,Map.of("RECORDING_ARCHIVE",new RecordingArchiveReferencePolicy(source)),ama.minutes.ControlledMinutesAdmin.READ,new ama.contract.MinutesContracts.CanonicalBudget(16777216,32,1000000,4194304,250000));uploads=new JdbcFileUploads(source,tx,access,purposes,catalog,new ama.audit.JdbcAuditAdapter(source),outbox,8388608,components.authority().publicationWriteGuard());persistence=new JdbcRecordingPersistence(source,catalog);recordings=new RecordingService(persistence);queue=TranscriptImportRuntime.install(source,fences,outbox,components,new RecordingArchiveRuntime.Dependencies(access,components.registry(),uploads,catalog,persistence,components.authority().publicationWriteGuard()));
   session=tx.required(()->{access.require(access.lock(actor,meeting),ama.access.MeetingAccess.Action.EDIT);return recordings.open(meeting,nativeFiles.binding.id());});copies=new JdbcRecordingTranscriptSources(source,access,persistence,queue,wrapper.apply(uploads));
  }
  void archive()throws Exception {archive(2);}
  void recorded(int samples)throws Exception {
   int seq=0;for(int offset=0;offset<samples;offset+=32000){int count=Math.min(32000,samples-offset),number=++seq,begin=offset;byte[] bytes=new byte[count*2];var buffer=java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);for(int n=0;n<count;n++)buffer.putShort((short)(Math.sin((begin+n)*.1)*3000));tx.required(()->{access.require(access.lock(actor,meeting),ama.access.MeetingAccess.Action.EDIT);return recordings.accept(session.sessionId(),1,new RecordingContracts.Chunk(session.partId(),1,number,begin,count,bytes));});if(samples>=28800000&&seq%300==0)System.out.println("LONG_SOURCE_COMMITTED_CHUNKS="+seq+" totalSamples="+samples);}long last=seq;
   tx.required(()->{access.require(access.lock(actor,meeting),ama.access.MeetingAccess.Action.EDIT);return recordings.finish(session.sessionId(),1,Map.of(session.partId(),last));});
  }
  void archive(int samples)throws Exception {recorded(samples);var accepted=RecordingArchiveRuntime.accept(queue,actor,session.sessionId());var lease=queue.claim("RECORDING_ARCHIVE","RECORDING_SOURCE_IT").orElseThrow();assertEquals(accepted.taskId(),lease.taskId());var heartbeats=java.util.concurrent.Executors.newSingleThreadScheduledExecutor();try{heartbeats.scheduleWithFixedDelay(()->queue.renew(lease),1,1,java.util.concurrent.TimeUnit.SECONDS);var carrier=RecordingArchiveRuntime.prepare(queue,lease,NEVER);heartbeats.shutdownNow();assertTrue(heartbeats.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS));assertTrue(queue.renew(lease));assertTrue(queue.completeWithEffects(lease,carrier));}finally{heartbeats.shutdownNow();assertTrue(heartbeats.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS));}assertTrue(RecordingArchiveRuntime.status(queue,actor,session.sessionId()).available());
  }
  @Override public void close()throws Exception{copies.close();nativeFiles.close();}
 }
 @Test void onlyReadyActualArchiveBecomesASeparateCompletedUploadWithTheExactOriginalSha()throws Exception {
  try(var f=new Fixture()){
   assertEquals("NOT_REQUESTED",f.copies.read(f.actor,f.session.sessionId()).get("state"));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM recording_transcript_source WHERE meeting_id=?",Integer.class,f.meeting));assertThrows(RuntimeException.class,()->f.copies.accept(f.actor,f.session.sessionId(),id()));f.archive();String key=id();var accepted=f.copies.accept(f.actor,f.session.sessionId(),key);assertEquals("PENDING",accepted.get("state"));assertTrue(f.copies.runOnce(f.session.sessionId()));var ready=f.copies.read(f.actor,f.session.sessionId());assertEquals("READY",ready.get("state"));String copied=(String)ready.get("fileId");var archive=j.queryForMap("SELECT file_id,sha256,size_bytes FROM recording_archive WHERE session_id=? AND state='READY'",f.session.sessionId());assertNotEquals(archive.get("file_id"),copied);var actual=j.queryForMap("SELECT f.sha256,f.size_bytes,u.purpose,u.state FROM file_object f JOIN upload_session u ON u.completed_file_id=f.id WHERE f.id=?",copied);assertEquals(archive.get("sha256"),actual.get("sha256"));assertEquals(((Number)archive.get("size_bytes")).longValue(),((Number)actual.get("size_bytes")).longValue());assertEquals("TRANSCRIPT_INPUT",actual.get("purpose"));assertEquals("COMPLETED",actual.get("state"));assertEquals(copied,f.copies.accept(f.actor,f.session.sessionId(),key).get("fileId"));assertFalse(f.copies.runOnce(f.session.sessionId()));
   try(var recovered=new JdbcRecordingTranscriptSources(source,f.access,new JdbcRecordingPersistence(source,f.catalog),f.queue,f.uploads)){assertEquals(copied,recovered.read(f.actor,f.session.sessionId()).get("fileId"));}assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM transcript_revision WHERE meeting_id=?",Integer.class,f.meeting));
  }
 }
 @Test void secondIdentityCannotStartOrReadAndLateCopyCannotReviveADeletedMeeting()throws Exception {
  try(var f=new Fixture()){
   f.archive();String outsider="recording-copy-outsider-"+id();j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",outsider,outsider);var foreign=new Actor(outsider,Status.ACTIVE,Set.of(Role.USER));assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->f.copies.read(foreign,f.session.sessionId()));assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->f.copies.accept(foreign,f.session.sessionId(),id()));f.copies.accept(f.actor,f.session.sessionId(),id());long revision=j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,f.meeting);f.meetings.delete(f.actor,f.meeting,id(),new ama.contract.MeetingContracts.DeleteMeeting(revision),"RECORDING_SOURCE_DELETE");assertFalse(f.copies.runOnce(f.session.sessionId()));assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->f.copies.read(f.actor,f.session.sessionId()));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM recording_transcript_source WHERE meeting_id=? AND state='READY'",Integer.class,f.meeting));
   try(var deletion=new JdbcMeetingDeletion(source,f.tx,f.access,f.catalog)){String job=deletion.forMeeting(f.actor,f.meeting).deletionTaskId();for(int n=0;n<180&&!deletion.status(f.actor,job).state().equals("COMPLETE");n++)assertTrue(deletion.runOnce(job));assertEquals("COMPLETE",deletion.status(f.actor,job).state());}assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM recording_transcript_source WHERE meeting_id=?",Integer.class,f.meeting));
  }
 }

 static FileUploads intercept(FileUploads delegate,Runnable afterPart){return new FileUploads(){
  public ama.contract.FileContracts.Accepted begin(Actor a,String m,String k,ama.contract.FileContracts.Begin r,String t){return delegate.begin(a,m,k,r,t);}
  public ama.contract.FileContracts.Accepted beginPinned(Actor a,String m,String k,ama.contract.FileContracts.Begin r,String t,String b){return delegate.beginPinned(a,m,k,r,t,b);}
  public void part(Actor a,String u,int p,InputStream body,long size,String hash,String t){delegate.part(a,u,p,body,size,hash,t);afterPart.run();}
  public ama.contract.FileContracts.Completed complete(Actor a,String u,ama.contract.FileContracts.Completion r,String t){return delegate.complete(a,u,r,t);}
  public void abort(Actor a,String u,String t){delegate.abort(a,u,t);}
 };}
 @Test void lostPartReplyReusesTheOriginalRealUploadAndPartOnRetry()throws Exception {
  var first=new java.util.concurrent.atomic.AtomicBoolean(true);try(var f=new Fixture(delegate->intercept(delegate,()->{if(first.compareAndSet(true,false))throw new IllegalStateException("Controlled lost part ACK");}))){
   f.archive();String key=id();f.copies.accept(f.actor,f.session.sessionId(),key);assertFalse(f.copies.runOnce(f.session.sessionId()));String upload=j.queryForObject("SELECT upload_id FROM recording_transcript_source WHERE session_id=?",String.class,f.session.sessionId());assertNotNull(upload);assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM upload_part WHERE upload_id=?",Integer.class,upload));assertEquals("DELAYED",f.copies.read(f.actor,f.session.sessionId()).get("state"));assertTrue(f.copies.runOnce(f.session.sessionId()));assertEquals("READY",f.copies.read(f.actor,f.session.sessionId()).get("state"));assertEquals(upload,j.queryForObject("SELECT upload_id FROM recording_transcript_source WHERE session_id=?",String.class,f.session.sessionId()));assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM upload_part WHERE upload_id=?",Integer.class,upload));
  }
 }
 @Test void anOrdinaryCompletedUploadWithTheSameBytesCannotClaimRecordingOrigin()throws Exception {
  try(var f=new Fixture()){
   f.archive();f.copies.accept(f.actor,f.session.sessionId(),id());assertTrue(f.copies.runOnce(f.session.sessionId()));String copy=(String)f.copies.read(f.actor,f.session.sessionId()).get("fileId");byte[] bytes;try(var original=RecordingArchiveRuntime.download(f.queue,f.actor,f.session.sessionId(),null)){bytes=original.body().readAllBytes();}String hash=sha(bytes);var ordinary=f.uploads.begin(f.actor,f.meeting,id(),new ama.contract.FileContracts.Begin("TRANSCRIPT_INPUT","ordinary.wav",bytes.length,hash),"ORDINARY_SOURCE");f.uploads.part(f.actor,ordinary.uploadId(),1,new ByteArrayInputStream(bytes),bytes.length,hash,"ORDINARY_SOURCE");String file=f.uploads.complete(f.actor,ordinary.uploadId(),new ama.contract.FileContracts.Completion(List.of(new ama.contract.FileContracts.ClientPart(1,hash)),hash),"ORDINARY_SOURCE").fileId();
   f.tx.required(()->{f.access.require(f.access.lock(f.actor,f.meeting),ama.access.MeetingAccess.Action.EDIT);assertTrue(JdbcRecordingTranscriptSources.readyCopy(j,f.meeting,f.actor.enterpriseId(),0,copy,hash));assertFalse(JdbcRecordingTranscriptSources.readyCopy(j,f.meeting,f.actor.enterpriseId(),0,file,hash));assertFalse(JdbcRecordingTranscriptSources.readyCopy(j,f.meeting,f.actor.enterpriseId(),1,copy,hash));assertFalse(JdbcRecordingTranscriptSources.readyCopy(j,f.meeting,f.actor.enterpriseId(),0,copy,"0".repeat(64)));return null;});
  }
 }

 @Test void actualReadyCopyEntersTheConfinedImportQueueAndProducesIndependentRawFromFullArchive()throws Exception {
  try(var f=new Fixture()){
   f.archive(128000);f.copies.accept(f.actor,f.session.sessionId(),id());assertTrue(f.copies.runOnce(f.session.sessionId()));String file=(String)f.copies.read(f.actor,f.session.sessionId()).get("fileId");
   var pipeline=pipeline(f);var accepted=pipeline.accepted();var settings=pipeline.settings();var lease=f.queue.claim("TRANSCRIPT_IMPORT","RECORDING_ARCHIVE_ASR").orElseThrow();assertEquals(accepted.taskId(),lease.taskId());var carrier=TranscriptImportRuntime.prepare(f.queue,lease,settings,NEVER);assertTrue(f.queue.completeWithEffects(lease,carrier));assertEquals("SUCCEEDED",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,lease.taskId()));assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM transcript_revision WHERE meeting_id=? AND kind='RAW' AND producer_task_id=?",Integer.class,f.meeting,lease.taskId()));assertEquals(5,j.queryForObject("SELECT COUNT(*) FROM speech_checkpoint WHERE input_id=?",Integer.class,accepted.inputId()));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM recording_subtitle_event WHERE meeting_id=?",Integer.class,f.meeting));assertEquals("ama.recording-wav.v1",j.queryForObject("SELECT parser_version FROM transcription_input WHERE id=?",String.class,accepted.inputId()));
  }
 }

 record Pipeline(ama.contract.ProcessingContracts.ImportAccepted accepted,TranscriptImportRuntime.Settings settings) {}
 Pipeline pipeline(Fixture f)throws Exception {
   var raw=new ArrayList<ama.contract.ConfigArchiveContracts.RawArchive>();for(String[] r:new String[][]{{"restart/codec","CANONICAL_CODEC","ama.canonical-value.v1"},{"restart/confirmation","CONFIRMATION_HASH","ama.transcript-confirmation.v1"},{"recording-parser-v1","PARSER_CONFIG","ama.recording-wav.v1"},{"recording-processing-v1","PROCESSING_CONFIG","ama.import-recording-wav.v1"}})try(var input=getClass().getResourceAsStream("/ama/transcript/"+r[0]+".json")){assertNotNull(input);raw.add(new ama.contract.ConfigArchiveContracts.RawArchive(ama.contract.ConfigArchiveContracts.ArtifactType.valueOf(r[1]),r[2],input.readAllBytes()));}
   Path archive=java.nio.file.Files.createDirectory(root.resolve("recording-archives-"+id()));String pin=new ama.config.file.ControlledArchiveReleaseWriter().publish(archive,null,raw,ama.minutes.ControlledMinutesAdmin.INDEX,ama.minutes.ControlledMinutesAdmin.READ,()->false);var snapshot=new ama.config.file.ControlledConfigArchiveSource().open(archive,pin,ama.minutes.ControlledMinutesAdmin.INDEX,ama.minutes.ControlledMinutesAdmin.READ,()->false);var processing=snapshot.index().entries().stream().map(ama.config.ConfigArchiveIndex.Entry::ref).filter(r->r.artifactType()==ama.contract.ConfigArchiveContracts.ArtifactType.PROCESSING_CONFIG).findFirst().orElseThrow();var settings=new TranscriptImportRuntime.Settings(()->snapshot,ama.minutes.ControlledMinutesAdmin.READ,new TranscriptDocumentParser.ParseBudget(33554432,1048576,8192),java.time.Duration.ofSeconds(60),230400044L);
   var intake=new JdbcProcessingIntake(source,f.tx,f.access,f.components,f.fences,f.queue,f.outbox,new ama.audit.JdbcAuditAdapter(source),(a,m,r)->f.components.authority().prepare(f.components.intake().readDraft(a,m,r.orderedFileIds(),()->false),id(),id(),snapshot,processing,()->false));var accepted=intake.accept(f.actor,f.meeting,id(),new ama.contract.ProcessingContracts.ImportRequest(0,List.of((String)f.copies.read(f.actor,f.session.sessionId()).get("fileId"))),"RECORDING_ARCHIVE_ASR");return new Pipeline(accepted,settings);
 }
 @Test void anUnconfiguredHistoricalBackendCannotStarveAnAvailableRecordingImport()throws Exception {
  try(var old=new Fixture()){
   old.archive(128000);old.copies.accept(old.actor,old.session.sessionId(),id());assertTrue(old.copies.runOnce(old.session.sessionId()));var waiting=pipeline(old);
   try(var current=new Fixture()){
    current.archive(128000);current.copies.accept(current.actor,current.session.sessionId(),id());assertTrue(current.copies.runOnce(current.session.sessionId()));var available=pipeline(current);var lease=current.queue.claim("TRANSCRIPT_IMPORT","AVAILABLE_RECORDING_SOURCE").orElseThrow();assertEquals(available.accepted().taskId(),lease.taskId());assertEquals("READY",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,waiting.accepted().taskId()));var carrier=TranscriptImportRuntime.prepare(current.queue,lease,available.settings(),NEVER);assertTrue(current.queue.completeWithEffects(lease,carrier));assertEquals("SUCCEEDED",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,lease.taskId()));
   }
  }
 }

 @Test void thirtyMinuteArchiveStreamsToIndependentRawWithoutAnUploadLimitBypass()throws Exception {
  verifyLongSource(28800000,900,15,"THIRTY_MINUTE_SOURCE");
 }
 @Test @Tag("phase-f") @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="AMA_RUN_PHASE_F_TWO_HOUR_SOURCE",matches="1")
 void twoHourArchiveStreamsToIndependentRawWithoutAnUploadLimitBypass()throws Exception {
  verifyLongSource(115200000,3600,60,"TWO_HOUR_SOURCE");
 }
 private void verifyLongSource(int samples,int chunks,int windows,String label)throws Exception {
  long bytes=44L+samples*2L;
  long started=System.nanoTime();try(var f=new Fixture()){
   f.archive(samples);assertEquals(chunks,j.queryForObject("SELECT COUNT(*) FROM recording_chunk WHERE session_id=?",Integer.class,f.session.sessionId()));assertEquals(samples,RecordingArchiveRuntime.status(f.queue,f.actor,f.session.sessionId()).sampleCount());System.out.println(label+"_ARCHIVE_READY bytes="+bytes);f.copies.accept(f.actor,f.session.sessionId(),id());assertTrue(f.copies.runOnce(f.session.sessionId()));var sourceCopy=f.copies.read(f.actor,f.session.sessionId());assertEquals("READY",sourceCopy.get("state"));assertEquals(bytes,((Number)sourceCopy.get("sourceBytes")).longValue());var configured=pipeline(f);var lease=f.queue.claim("TRANSCRIPT_IMPORT",label).orElseThrow();assertEquals(configured.accepted().taskId(),lease.taskId());var heartbeats=java.util.concurrent.Executors.newSingleThreadScheduledExecutor();var renewed=new java.util.concurrent.atomic.AtomicInteger();
   try{
    heartbeats.scheduleWithFixedDelay(()->{if(f.queue.renew(lease))renewed.incrementAndGet();},1,1,java.util.concurrent.TimeUnit.SECONDS);var base=configured.settings();var longSettings=new TranscriptImportRuntime.Settings(base.archives(),base.archiveBudget(),base.aggregate(),java.time.Duration.ofSeconds(60),230400044L,java.time.Duration.ofMinutes(10));var carrier=TranscriptImportRuntime.prepare(f.queue,lease,longSettings,NEVER);System.out.println(label+"_PREPARED checkpoints="+j.queryForObject("SELECT COUNT(*) FROM speech_checkpoint WHERE input_id=?",Integer.class,configured.accepted().inputId()));heartbeats.shutdownNow();assertTrue(heartbeats.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS));assertTrue(f.queue.renew(lease));assertTrue(f.queue.completeWithEffects(lease,carrier));
   }finally{heartbeats.shutdownNow();assertTrue(heartbeats.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS));}
   assertEquals("SUCCEEDED",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,lease.taskId()));assertEquals(windows*5,j.queryForObject("SELECT COUNT(*) FROM speech_checkpoint WHERE input_id=?",Integer.class,configured.accepted().inputId()));assertEquals(windows,j.queryForObject("SELECT COUNT(*) FROM speech_pcm_artifact WHERE input_id=? AND state='READY'",Integer.class,configured.accepted().inputId()));assertEquals(windows,j.queryForObject("SELECT COUNT(*) FROM transcript_segment s JOIN transcript_revision r ON r.id=s.revision_id WHERE r.producer_task_id=? AND r.kind='RAW'",Integer.class,lease.taskId()));assertEquals(samples/16L,j.queryForObject("SELECT MAX(s.end_ms) FROM transcript_segment s JOIN transcript_revision r ON r.id=s.revision_id WHERE r.producer_task_id=?",Long.class,lease.taskId()));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM recording_subtitle_event WHERE meeting_id=?",Integer.class,f.meeting));System.out.println(label+"_PASS chunks="+chunks+" bytes="+bytes+" windows="+windows+" checkpoints="+(windows*5)+" elapsedSeconds="+java.util.concurrent.TimeUnit.NANOSECONDS.toSeconds(System.nanoTime()-started)+" renewals="+renewed.get()+" scope=BOUNDED_SOURCE_STREAM_NOT_REALTIME_CAPACITY_OR_COMPANY_QUALITY");
  }
 }

 @Test void httpSourcePreparationIsExplicitAndReadsNeverCreateACopyOrAnImport()throws Exception {
  try(var f=new Fixture()){
   var mvc=ama.api.RecordingTranscriptHttpProbe.create(f.copies,f.recordings,f.access,f.tx);String attribute=ama.api.RecordingTranscriptHttpProbe.sessionAttribute(),path="/api/v1/recordings/"+f.session.sessionId()+"/transcription-source",current="/api/v1/meetings/"+f.meeting+"/recording-transcription-source";var identity=new ResolvedSession(f.actor,"T".repeat(43),java.time.Instant.now().plusSeconds(60));var json=new com.fasterxml.jackson.databind.ObjectMapper();
   var untouched=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(current).requestAttr(attribute,identity)).andReturn().getResponse();assertEquals(200,untouched.getStatus());assertEquals("NOT_REQUESTED",json.readTree(untouched.getContentAsByteArray()).path("state").asText());assertFalse(json.readTree(untouched.getContentAsByteArray()).path("archiveAvailable").asBoolean());assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM recording_transcript_source WHERE meeting_id=?",Integer.class,f.meeting));
   f.archive();var malformed=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).header("Idempotency-Key",id()).contentType("application/json").content("{\"fileId\":\""+id()+"\"}").requestAttr(attribute,identity)).andReturn().getResponse();assertEquals(400,malformed.getStatus());var prepared=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).header("Idempotency-Key",id()).contentType("application/json").content("{}").requestAttr(attribute,identity)).andReturn().getResponse();assertEquals(202,prepared.getStatus(),prepared.getContentAsString());assertTrue(f.copies.runOnce(f.session.sessionId()));var replay=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).requestAttr(attribute,identity)).andReturn().getResponse();assertEquals(200,replay.getStatus());assertEquals("private, no-store",replay.getHeader("Cache-Control"));assertEquals("READY",json.readTree(replay.getContentAsByteArray()).path("state").asText());assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM transcript_revision WHERE meeting_id=?",Integer.class,f.meeting));
   String other="recording-http-other-"+id();j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",other,other);var foreign=new ResolvedSession(new Actor(other,Status.ACTIVE,Set.of(Role.USER)),"T".repeat(43),java.time.Instant.now().plusSeconds(60));assertEquals(403,mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(current).requestAttr(attribute,foreign)).andReturn().getResponse().getStatus());assertEquals(403,mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).header("Idempotency-Key",id()).contentType("application/json").content("{}").requestAttr(attribute,foreign)).andReturn().getResponse().getStatus());
  }
 }
 @Test void publicTranscriptUploadCannotConsumeTheInternalRecordingByteBudget()throws Exception {
  try(var f=new Fixture()){
   var mvc=ama.api.RecordingTranscriptHttpProbe.uploads(f.uploads,f.components.registry());var identity=new ResolvedSession(f.actor,"T".repeat(43),java.time.Instant.now().plusSeconds(60));String body=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("purpose","TRANSCRIPT_INPUT","name","ordinary-too-large.wav","size",33554433,"sha256","a".repeat(64)));var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/meetings/"+f.meeting+"/uploads").header("Idempotency-Key",id()).contentType("application/json").content(body).requestAttr(ama.api.RecordingTranscriptHttpProbe.sessionAttribute(),identity)).andReturn().getResponse();assertEquals(413,response.getStatus(),response.getContentAsString());assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM upload_session WHERE meeting_id=?",Integer.class,f.meeting));
  }
 }

 @Test void aSecondArchiveWindowKeepsExactSourceIdentityAtRawCompletion()throws Exception {
  try(var f=new Fixture()){
   f.archive(1936000);f.copies.accept(f.actor,f.session.sessionId(),id());assertTrue(f.copies.runOnce(f.session.sessionId()));var p=pipeline(f);var lease=f.queue.claim("TRANSCRIPT_IMPORT","TWO_RECORDING_WINDOWS").orElseThrow();assertEquals(p.accepted().taskId(),lease.taskId());var heartbeats=java.util.concurrent.Executors.newSingleThreadScheduledExecutor();try{heartbeats.scheduleWithFixedDelay(()->f.queue.renew(lease),1,1,java.util.concurrent.TimeUnit.SECONDS);var carrier=TranscriptImportRuntime.prepare(f.queue,lease,p.settings(),NEVER);System.out.println("TWO_WINDOW_PREPARED checkpoints="+j.queryForObject("SELECT COUNT(*) FROM speech_checkpoint WHERE input_id=?",Integer.class,p.accepted().inputId()));heartbeats.shutdownNow();assertTrue(heartbeats.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS));assertTrue(f.queue.renew(lease));assertTrue(f.queue.completeWithEffects(lease,carrier));}finally{heartbeats.shutdownNow();assertTrue(heartbeats.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS));}assertEquals("SUCCEEDED",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,lease.taskId()));assertEquals(2,j.queryForObject("SELECT COUNT(*) FROM transcript_segment s JOIN transcript_revision r ON r.id=s.revision_id WHERE r.producer_task_id=?",Integer.class,lease.taskId()));
  }
 }

 @Test void anUnconfiguredPendingArchiveCannotStarveTheCurrentAvailableBackend()throws Exception {
  try(var old=new Fixture()){
   old.recorded(32000);var waiting=RecordingArchiveRuntime.accept(old.queue,old.actor,old.session.sessionId());
   try(var current=new Fixture()){
    current.recorded(32000);var accepted=RecordingArchiveRuntime.accept(current.queue,current.actor,current.session.sessionId());var lease=current.queue.claim("RECORDING_ARCHIVE","AVAILABLE_ARCHIVE_ROUTE").orElseThrow();assertEquals(accepted.taskId(),lease.taskId());assertEquals("READY",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,waiting.taskId()));assertTrue(current.queue.completeWithEffects(lease,RecordingArchiveRuntime.prepare(current.queue,lease,NEVER)));assertTrue(RecordingArchiveRuntime.status(current.queue,current.actor,current.session.sessionId()).available());
   }
  }
 }
}
