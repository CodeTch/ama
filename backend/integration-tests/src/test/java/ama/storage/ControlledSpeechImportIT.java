package ama.storage;

import ama.config.*;
import ama.config.file.*;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.IdentityContracts.*;
import ama.contract.MeetingContracts.DeleteMeeting;
import ama.contract.TaskContracts.*;
import ama.db.*;
import ama.meeting.*;
import ama.task.*;
import ama.transcript.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Real accepted task + MySQL + native files + fixed sibling speech HTTP. No quality claims. */
class ControlledSpeechImportIT {
 @TempDir Path root;
 static javax.sql.DataSource source;static JdbcTemplate j;
 static final PreparationCancellation NEVER=new PreparationCancellation(){public boolean cancelled(){return false;}public AutoCloseable register(Runnable abort){return ()->{};}};
 @BeforeAll static void schema(){
  String url=System.getenv("AMA_C_RECORDING_DATABASE_URL");if(url==null||!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/ama_c_recording_[0-9a-f]{12}"))throw new IllegalArgumentException("Owned C schema required");
  assertEquals("http://127.0.0.1:8792",System.getenv("AMA_SPEECH_HARNESS_ORIGIN"));source=MysqlDataSources.fromEnvironment("AMA_C_RECORDING_DATABASE_");j=new JdbcTemplate(source);
  assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version='056'",Integer.class));
 }
 static String id(){return UUID.randomUUID().toString();}
 static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
 final class Fixture implements AutoCloseable {
  final ManagedLocalFixture nativeFiles;final FileStoragePort storage;final FileBackendCatalog catalog;final TransactionRunner tx;
  final JdbcSubjectFences fences;final JdbcOutbox outbox;final JdbcMeetingAccess access;final MeetingService meetings;
  final JdbcTranscriptAuthority.Components components;final JdbcTaskQueue queue;final Actor actor;final String meeting,file,input,task;final byte[] wav;
  final TranscriptImportRuntime.Settings settings;
  Fixture()throws Exception {this(wav(128000));}
  Fixture(byte[] originalWav)throws Exception {
   nativeFiles=new ManagedLocalFixture(root.resolve(id()));storage=nativeFiles.startServer();catalog=new FileBackendCatalog(source,List.of(new FileBackendCatalog.Backend(nativeFiles.binding.id(),"local",1,"WRITABLE",storage,nativeFiles.binding)),nativeFiles.binding.id());catalog.configure();
   tx=new TransactionRunner(source);fences=new JdbcSubjectFences(source,Set.of("MEETING"));access=new JdbcMeetingAccess(source,tx,fences);var validators=new HashMap<String,OutboxReferenceValidator>(new MeetingEventReferences(source).validators());validators.putAll(new TranscriptEventReferences(source).validators());outbox=new JdbcOutbox(source,validators);
   String owner="speech-import-"+id();j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",owner,owner);actor=new Actor(owner,Status.ACTIVE,Set.of(Role.USER));
   meetings=new MeetingService(new JdbcMeetingRepository(source,tx,access,fences,outbox,new ama.audit.JdbcAuditAdapter(source),m->{}));meeting=meetings.create(actor,id(),"speech-resume").id();
   FilePurposePolicy purposes=(a,m,p)->{if(!p.equals("TRANSCRIPT_INPUT"))throw new FilePurposePolicy.Unavailable();};
   components=JdbcTranscriptAuthority.create(source,tx,access,purposes,catalog,Map.of(),ama.minutes.ControlledMinutesAdmin.READ,new ama.contract.MinutesContracts.CanonicalBudget(16777216,32,1000000,4194304,250000));queue=TranscriptImportRuntime.install(source,fences,outbox,components);
   var uploads=new JdbcFileUploads(source,tx,access,purposes,catalog,new ama.audit.JdbcAuditAdapter(source),outbox,1048576,components.authority().publicationWriteGuard());
   wav=originalWav.clone();String hash=sha(wav);var accepted=uploads.begin(actor,meeting,id(),new ama.contract.FileContracts.Begin("TRANSCRIPT_INPUT","受控语音.wav",wav.length,hash),"SPEECH_IMPORT");uploads.part(actor,accepted.uploadId(),1,new ByteArrayInputStream(wav),wav.length,hash,"SPEECH_IMPORT");file=uploads.complete(actor,accepted.uploadId(),new ama.contract.FileContracts.Completion(List.of(new ama.contract.FileContracts.ClientPart(1,hash)),hash),"SPEECH_IMPORT").fileId();
   var raw=new ArrayList<RawArchive>();for(String[] r:new String[][]{{"restart/codec","CANONICAL_CODEC","ama.canonical-value.v1"},{"restart/confirmation","CONFIRMATION_HASH","ama.transcript-confirmation.v1"},{"media-parser-v3","PARSER_CONFIG",ControlledSpeechProfile.PARSER},{"media-processing-v3","PROCESSING_CONFIG","ama.import-media-documents.v3"}})try(var in=getClass().getResourceAsStream("/ama/transcript/"+r[0]+".json")){assertNotNull(in);raw.add(new RawArchive(ArtifactType.valueOf(r[1]),r[2],in.readAllBytes()));}
   Path archive=Files.createDirectory(root.resolve("archives-"+id()));String pin=new ControlledArchiveReleaseWriter().publish(archive,null,raw,ama.minutes.ControlledMinutesAdmin.INDEX,ama.minutes.ControlledMinutesAdmin.READ,()->false);var snapshot=new ControlledConfigArchiveSource().open(archive,pin,ama.minutes.ControlledMinutesAdmin.INDEX,ama.minutes.ControlledMinutesAdmin.READ,()->false);Ref processing=snapshot.index().entries().stream().map(ConfigArchiveIndex.Entry::ref).filter(r->r.artifactType()==ArtifactType.PROCESSING_CONFIG).findFirst().orElseThrow();
   settings=new TranscriptImportRuntime.Settings(()->snapshot,ama.minutes.ControlledMinutesAdmin.READ,new TranscriptDocumentParser.ParseBudget(1048576,1048576,4096),Duration.ofSeconds(60));
   var intake=new JdbcProcessingIntake(source,tx,access,components,fences,queue,outbox,new ama.audit.JdbcAuditAdapter(source),(a,m,r)->components.authority().prepare(components.intake().readDraft(a,m,r.orderedFileIds(),()->false),id(),id(),snapshot,processing,()->false));var imported=intake.accept(actor,meeting,id(),new ama.contract.ProcessingContracts.ImportRequest(0,List.of(file)),"SPEECH_IMPORT");input=imported.inputId();task=imported.taskId();
  }
  TaskLease claim(JdbcTaskQueue queue){var lease=queue.claim("TRANSCRIPT_IMPORT","SPEECH_IMPORT_IT").orElseThrow();assertEquals(task,lease.taskId());return lease;}
  int checkpoints(){return j.queryForObject("SELECT COUNT(*) FROM speech_checkpoint WHERE input_id=?",Integer.class,input);}
  void expire(){j.update("UPDATE durable_task SET expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",task);}
  @Override public void close()throws Exception{nativeFiles.close();}
 }
 @Test void actualFiveStageTaskKeepsRawSeparateAndClosesEveryExecution()throws Exception {
  try(var f=new Fixture()){
   var lease=f.claim(f.queue);var carrier=TranscriptImportRuntime.prepare(f.queue,lease,f.settings,NEVER);assertEquals(5,f.checkpoints());assertThrows(IllegalStateException.class,carrier::get);assertTrue(f.queue.completeWithEffects(lease,carrier));
   assertEquals("SUCCEEDED",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,f.task));
   String raw=j.queryForObject("SELECT output_id FROM durable_task WHERE id=?",String.class,f.task);String original=new ObjectMapper().readTree(j.queryForObject("SELECT payload FROM speech_checkpoint WHERE input_id=? AND stage='ASR'",byte[].class,f.input)).path("value").path("result").path("text").asText();
   assertFalse(original.isBlank());assertEquals(List.of(original),j.queryForList("SELECT body FROM transcript_segment WHERE revision_id=? ORDER BY ordinal",String.class,raw));
   assertEquals(List.of(0L),j.queryForList("SELECT start_ms FROM transcript_segment WHERE revision_id=?",Long.class,raw));assertEquals(List.of(8000L),j.queryForList("SELECT end_ms FROM transcript_segment WHERE revision_id=?",Long.class,raw));
   assertNull(j.queryForObject("SELECT transcript_revision_id FROM meeting WHERE id=?",String.class,f.meeting));
   assertEquals(5,j.queryForObject("SELECT COUNT(*) FROM speech_execution WHERE lease_task_id=? AND state='COMPLETED' AND settled_at IS NOT NULL",Integer.class,f.task));
   var blob=j.queryForMap("SELECT backend_id,namespace,object_key,wav_sha256,byte_count FROM speech_pcm_artifact WHERE input_id=? AND state='READY'",f.input);var key=new ama.contract.StorageContracts.StorageKey((String)blob.get("backend_id"),(String)blob.get("namespace"),(String)blob.get("object_key"));try(var in=f.storage.read(key,0,((Number)blob.get("byte_count")).longValue())){assertEquals(blob.get("wav_sha256"),sha(in.readAllBytes()));}
  }
 }
 @Test void preparationCapabilityCannotBePromotedToRawCompletion()throws Exception {
  try(var f=new Fixture()){
   var lease=f.claim(f.queue);var specs=List.of(lease.spec());var policy=f.components.authority().taskPolicy();
   f.tx.required(()->{
    var held=policy.prepare(specs,TaskDomainPolicy.Acquisition.TASK_PREPARATION,false).orElseThrow();
    assertThrows(IllegalArgumentException.class,()->policy.requirePrepared(specs,TaskDomainPolicy.Acquisition.TASK_MUTATION,held));
    assertTrue(f.fences.lock(lease.spec().subject(),false).isPresent());assertTrue(policy.lockRoots(specs,false,held));
    assertDoesNotThrow(()->f.components.authority().requireImportPreparation(specs,held));
    assertThrows(IllegalArgumentException.class,()->f.components.authority().requireImportCompletion(specs,held));
    return null;
   });
   var carrier=TranscriptImportRuntime.prepare(f.queue,lease,f.settings,NEVER);assertTrue(f.queue.completeWithEffects(lease,carrier));
  }
 }
 @Test void newQueueAttemptReusesAllSuccessfulStagesAndTheOriginalPcm()throws Exception {
  try(var f=new Fixture()){
   var lease=f.claim(f.queue);var stop=new AtomicBoolean();
   class Probe implements org.springframework.transaction.support.TransactionSynchronization {
    @Override public void afterCommit(){if(j.queryForObject("SELECT COUNT(*) FROM speech_checkpoint WHERE input_id=? AND stage='ASR'",Integer.class,f.input)>0)stop.set(true);}
   }
   PreparationCancellation afterAsr=new PreparationCancellation(){public boolean cancelled(){
    if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()&&org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations().stream().noneMatch(s->s instanceof Probe))org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new Probe());
    return stop.get();
   }public AutoCloseable register(Runnable abort){return ()->{};}};
   assertThrows(CancellationException.class,()->TranscriptImportRuntime.prepare(f.queue,lease,f.settings,afterAsr));assertEquals(3,f.checkpoints());
   byte[] vad=j.queryForObject("SELECT payload FROM speech_checkpoint WHERE input_id=? AND stage='VAD'",byte[].class,f.input);String artifact=j.queryForObject("SELECT id FROM speech_pcm_artifact WHERE input_id=?",String.class,f.input);f.expire();
   var restarted=TranscriptImportRuntime.install(source,f.fences,f.outbox,f.components);var next=f.claim(restarted);assertEquals(2,next.attempt());var carrier=TranscriptImportRuntime.prepare(restarted,next,f.settings,NEVER);assertTrue(restarted.completeWithEffects(next,carrier));
   assertEquals(5,f.checkpoints());assertArrayEquals(vad,j.queryForObject("SELECT payload FROM speech_checkpoint WHERE input_id=? AND stage='VAD'",byte[].class,f.input));assertEquals(artifact,j.queryForObject("SELECT id FROM speech_pcm_artifact WHERE input_id=?",String.class,f.input));
   assertEquals(5,j.queryForObject("SELECT COUNT(*) FROM speech_execution WHERE lease_task_id=?",Integer.class,f.task));assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM transcript_revision WHERE producer_task_id=?",Integer.class,f.task));
  }
 }
 @Test void deletingPreparedTaskErasesNormalizedBytesAndRejectsLateRaw()throws Exception {
  try(var f=new Fixture()){
   var lease=f.claim(f.queue);var carrier=TranscriptImportRuntime.prepare(f.queue,lease,f.settings,NEVER);var blob=j.queryForMap("SELECT backend_id,namespace,object_key,byte_count,wav_sha256 FROM speech_pcm_artifact WHERE input_id=?",f.input);var key=new ama.contract.StorageContracts.StorageKey((String)blob.get("backend_id"),(String)blob.get("namespace"),(String)blob.get("object_key"));byte[] original;try(var in=f.storage.read(key,0,((Number)blob.get("byte_count")).longValue())){original=in.readAllBytes();}
   long revision=j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,f.meeting);f.meetings.delete(f.actor,f.meeting,id(),new DeleteMeeting(revision),"SPEECH_DELETE");assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->f.queue.completeWithEffects(lease,carrier));
   try(var deletion=new JdbcMeetingDeletion(source,f.tx,f.access,f.catalog)){
    String job=deletion.forMeeting(f.actor,f.meeting).deletionTaskId();for(int n=0;n<180&&!deletion.status(f.actor,job).state().equals("COMPLETE");n++){assertTrue(deletion.runOnce(job));}
    assertEquals("COMPLETE",deletion.status(f.actor,job).state());
   }
   assertEquals(0,f.checkpoints());assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM speech_pcm_artifact WHERE input_id=?",Integer.class,f.input));
   assertThrows(ama.contract.StorageContracts.StorageException.class,()->f.storage.stat(key));assertThrows(ama.contract.StorageContracts.StorageException.class,()->f.storage.put(key,new ByteArrayInputStream(original),original.length,(String)blob.get("wav_sha256")));
   assertEquals(5,j.queryForObject("SELECT COUNT(*) FROM speech_execution WHERE lease_task_id=? AND settled_at IS NOT NULL",Integer.class,f.task));
  }
 }
 static byte[] wav(int samples){var b=ByteBuffer.allocate(44+samples*2).order(ByteOrder.LITTLE_ENDIAN);b.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(36+samples*2).put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(16).putShort((short)1).putShort((short)1).putInt(16000).putInt(32000).putShort((short)2).putShort((short)16).put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(samples*2);for(int n=0;n<samples;n++)b.putShort((short)(Math.sin(n*.1)*3000));return b.array();}
}
