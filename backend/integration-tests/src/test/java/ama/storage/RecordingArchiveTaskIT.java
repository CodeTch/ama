package ama.storage;

import ama.contract.IdentityContracts.*;
import ama.contract.RecordingContracts;
import ama.contract.TaskContracts.*;
import ama.db.*;
import ama.meeting.*;
import ama.recording.*;
import ama.task.*;
import ama.transcript.*;
import java.nio.file.Path;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

class RecordingArchiveTaskIT {
 @TempDir Path root;
 static javax.sql.DataSource source;static JdbcTemplate j;
 @BeforeAll static void schema(){String url=System.getenv("AMA_C_RECORDING_DATABASE_URL");if(url==null||!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/ama_c_recording_[0-9a-f]{12}"))throw new IllegalArgumentException("Fresh owned schema required");source=MysqlDataSources.fromEnvironment("AMA_C_RECORDING_DATABASE_");j=new JdbcTemplate(source);Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();}
 final class Fixture implements AutoCloseable {
  final FileUploads uploads;final ManagedLocalFixture files;final FileBackendCatalog catalog;final TransactionRunner tx;final JdbcSubjectFences fences;final JdbcMeetingAccess access;final JdbcOutbox outbox;final Actor actor;final String meeting;final RecordingService recordings;final JdbcTaskQueue queue;final JdbcTranscriptAuthority.Components components;
  final java.util.concurrent.atomic.AtomicBoolean storageUnavailable=new java.util.concurrent.atomic.AtomicBoolean(),rejectSuccess=new java.util.concurrent.atomic.AtomicBoolean();
  final java.util.concurrent.ConcurrentHashMap<String,java.util.concurrent.atomic.AtomicInteger> uploadCalls=new java.util.concurrent.ConcurrentHashMap<>();
  final java.util.concurrent.atomic.AtomicReference<Runnable> afterBegin=new java.util.concurrent.atomic.AtomicReference<>(),afterPart=new java.util.concurrent.atomic.AtomicReference<>(),afterComplete=new java.util.concurrent.atomic.AtomicReference<>();
  final java.util.concurrent.atomic.AtomicBoolean blockRead=new java.util.concurrent.atomic.AtomicBoolean();
  final java.util.concurrent.CountDownLatch readStarted=new java.util.concurrent.CountDownLatch(1),readAborted=new java.util.concurrent.CountDownLatch(1);
  Fixture()throws Exception {
   files=new ManagedLocalFixture(root.resolve(UUID.randomUUID().toString()));var real=files.startServer();
   var storage=(FileStoragePort)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{FileStoragePort.class},(proxy,method,args)->{
    if(Set.of("read","readSequence").contains(method.getName())&&storageUnavailable.get())throw new ama.contract.StorageContracts.StorageException(ama.contract.StorageContracts.ErrorCode.STORAGE_UNAVAILABLE);
    try{
     Object result=method.invoke(real,args);
     if(Set.of("beginUpload","writePart","complete").contains(method.getName()))uploadCalls.computeIfAbsent(method.getName(),key->new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
     var hook=switch(method.getName()){case "beginUpload"->afterBegin;case "writePart"->afterPart;case "complete"->afterComplete;default->null;};
     if(hook!=null){var action=hook.getAndSet(null);if(action!=null)action.run();}
     if(Set.of("read","readSequence").contains(method.getName())&&blockRead.get()){
      var input=(java.io.InputStream)result;
      var transport=SourceReadLifetime.track(()->{try{input.close();}catch(java.io.IOException ignored){}readAborted.countDown();});readStarted.countDown();
      return new java.io.FilterInputStream(input){
       @Override public int read(byte[] bytes,int off,int count)throws java.io.IOException{try{readAborted.await(10,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException stopped){Thread.currentThread().interrupt();}throw new java.io.IOException("Controlled blocked source closed");}
       @Override public void close()throws java.io.IOException{readAborted.countDown();transport.close();super.close();}
      };
     }
     return result;
    }catch(java.lang.reflect.InvocationTargetException failure){throw failure.getCause();}
   });
   catalog=new FileBackendCatalog(source,List.of(new FileBackendCatalog.Backend(files.binding.id(),"local",1,"WRITABLE",storage,files.binding)),files.binding.id());catalog.configure();
   tx=new TransactionRunner(source);fences=new JdbcSubjectFences(source,Set.of("MEETING"));access=new JdbcMeetingAccess(source,tx,fences);var refs=new HashMap<String,OutboxReferenceValidator>(new MeetingEventReferences(source).validators());refs.putAll(RecordingArchiveRuntime.eventReferences(source));var actual=refs.get(RecordingArchiveRuntime.KIND);refs.put(RecordingArchiveRuntime.KIND,event->{actual.validate(event);if(event.eventType().equals("TASK_SUCCEEDED")&&rejectSuccess.get())throw new IllegalStateException("Controlled completion rollback");});outbox=new JdbcOutbox(source,refs);
   String id="recording-task-"+UUID.randomUUID();j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",id,id);actor=new Actor(id,Status.ACTIVE,Set.of(Role.USER));
   var meetings=new MeetingService(new JdbcMeetingRepository(source,tx,access,fences,outbox,new ama.audit.JdbcAuditAdapter(source),m->{}));meeting=meetings.create(actor,UUID.randomUUID().toString(),"recording-archive-task").id();
   FilePurposePolicy purposes=(a,m,p)->{if(!p.equals("RECORDING_ARCHIVE"))throw new FilePurposePolicy.Unavailable();};
   components=JdbcTranscriptAuthority.create(source,tx,access,purposes,catalog,Map.of("RECORDING_ARCHIVE",new RecordingArchiveReferencePolicy(source)),ama.minutes.ControlledMinutesAdmin.READ,new ama.contract.MinutesContracts.CanonicalBudget(16777216,32,1000000,4194304,250000));
   uploads=new JdbcFileUploads(source,tx,access,purposes,catalog,new ama.audit.JdbcAuditAdapter(source),outbox,1024,components.authority().publicationWriteGuard());
   var persistence=new JdbcRecordingPersistence(source,catalog);recordings=new RecordingService(persistence);
   queue=TranscriptImportRuntime.install(source,fences,outbox,components,new RecordingArchiveRuntime.Dependencies(access,components.registry(),uploads,catalog,persistence,components.authority().publicationWriteGuard()));
  }
  RecordingContracts.Session recorded(){var s=recordings.open(meeting,catalog.defaultWrite().id());recordings.accept(s.sessionId(),1,new RecordingContracts.Chunk(s.partId(),1,1,0,2,new byte[]{1,2,3,4}));recordings.finish(s.sessionId(),1,Map.of(s.partId(),1L));return s;}
  public void close()throws Exception {try{if(!"DELETED".equals(j.queryForObject("SELECT lifecycle FROM meeting WHERE id=?",String.class,meeting)))txDelete(this);}finally{files.close();}}
 }
 static final PreparationCancellation LIVE=new PreparationCancellation(){public boolean cancelled(){return false;}public AutoCloseable register(Runnable abort){return ()->{};}};
 static final class Cancel implements PreparationCancellation {
  final java.util.concurrent.atomic.AtomicBoolean stopped=new java.util.concurrent.atomic.AtomicBoolean();final List<Runnable> callbacks=new java.util.concurrent.CopyOnWriteArrayList<>();
  public boolean cancelled(){return stopped.get();}
  public AutoCloseable register(Runnable abort){callbacks.add(abort);if(stopped.get())abort.run();return ()->callbacks.remove(abort);}
  void cancel(){stopped.set(true);callbacks.forEach(Runnable::run);}
 }
 @Test void preparationHonorsTheWorkersSingleCancellationRegistration()throws Exception {
  try(var f=new Fixture()){
   var current=new java.util.concurrent.atomic.AtomicReference<Runnable>();
   var signal=new PreparationCancellation(){public boolean cancelled(){return false;}public AutoCloseable register(Runnable action){if(!current.compareAndSet(null,action))throw new IllegalStateException("Source already registered");return ()->current.compareAndSet(action,null);}};
   var s=f.recorded();var accepted=RecordingArchiveRuntime.accept(f.queue,f.actor,s.sessionId());var lease=f.queue.claim("RECORDING_ARCHIVE","archive-single-source").orElseThrow();assertEquals(accepted.taskId(),lease.taskId());
   var prepared=RecordingArchiveRuntime.prepare(f.queue,lease,signal);assertNull(current.get());assertTrue(f.queue.completeWithEffects(lease,prepared));assertEquals("SUCCEEDED",RecordingArchiveRuntime.status(f.queue,f.actor,s.sessionId()).state());
  }
 }
 @Test void taskCancellationAbortsAnAlreadyOpenedBlockedSourceAndPreservesAck()throws Exception {
  try(var f=new Fixture()){
   var s=f.recorded();var accepted=RecordingArchiveRuntime.accept(f.queue,f.actor,s.sessionId());var lease=f.queue.claim("RECORDING_ARCHIVE","archive-cancel").orElseThrow();assertEquals(accepted.taskId(),lease.taskId());var signal=new Cancel();f.blockRead.set(true);var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
   try{
    var future=executor.submit(()->RecordingArchiveRuntime.prepare(f.queue,lease,signal));assertTrue(f.readStarted.await(5,java.util.concurrent.TimeUnit.SECONDS));signal.cancel();
    var failure=assertThrows(java.util.concurrent.ExecutionException.class,()->future.get(3,java.util.concurrent.TimeUnit.SECONDS));assertInstanceOf(java.util.concurrent.CancellationException.class,failure.getCause());assertEquals(0,f.readAborted.getCount());
    assertEquals(1,f.recordings.ack(s.sessionId(),s.partId()).highestContiguousSeq());assertFalse(RecordingArchiveRuntime.status(f.queue,f.actor,s.sessionId()).available());
   }finally{signal.cancel();f.readAborted.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(5,java.util.concurrent.TimeUnit.SECONDS));}
  }
 }
 @Test void onlyConfinedCompletionRegistersPlayableArchiveAndFinishReplayKeepsTask()throws Exception {
  try(var f=new Fixture()) {
   var s=f.recorded();var accepted=RecordingArchiveRuntime.accept(f.queue,f.actor,s.sessionId());assertEquals("READY",accepted.state());
   var tasks=new JdbcWorkspaceTasks(f.components.authority(),f.queue,new WorkspaceCursor(),WorkspaceReadBudget.DEFAULT).read(f.actor,f.meeting,"ALL",20,null);
   assertEquals(accepted.taskId(),tasks.items().getFirst().taskId());assertEquals("/api/v1/meetings/"+f.meeting+"/recording-archives/"+leaseInput(f,accepted.taskId()),tasks.items().getFirst().detailPath());
   assertEquals(accepted.taskId(),RecordingArchiveRuntime.accept(f.queue,f.actor,s.sessionId()).taskId());
   assertThrows(ama.contract.FileContracts.NotFound.class,()->RecordingArchiveRuntime.download(f.queue,f.actor,s.sessionId(),null));
   var lease=f.queue.claim("RECORDING_ARCHIVE","recording-test").orElseThrow();assertEquals(accepted.taskId(),lease.taskId());var carrier=new ama.worker.RecordingArchiveHandler(f.queue).prepare(lease,LIVE);
   var candidate=j.queryForMap("SELECT id,file_id,upload_id FROM recording_archive WHERE input_id=?",lease.spec().input().id());
   var write=f.components.registry().prepareWrite(f.actor,f.meeting);
   assertThrows(ama.contract.FileContracts.NotFound.class,()->f.tx.required(()->{
    var gate=f.components.registry().lockWrite(f.actor,f.meeting,write);
    var reference=f.components.registry().prelock(gate,new FileRegistry.Reference("RECORDING_ARCHIVE",(String)candidate.get("id"),"RECORDING_ARCHIVE"),(String)candidate.get("file_id"));
    var file=f.components.registry().resolve((String)candidate.get("file_id"));f.components.registry().bind(reference,file.locationRevision(),(String)candidate.get("upload_id"));return null;
   }));
   assertThrows(IllegalStateException.class,carrier::get);
   assertThrows(IllegalArgumentException.class,()->f.queue.completeWithEffects(lease,()->new TaskResult(Outcome.SUCCEEDED,new TypedRef("RECORDING_ARCHIVE",UUID.randomUUID().toString()),null)));
   assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM file_reference r JOIN recording_archive a ON a.file_id=r.file_id WHERE a.session_id=? AND r.owner_kind='RECORDING_ARCHIVE'",Integer.class,s.sessionId()));
   assertTrue(f.queue.completeWithEffects(lease,carrier));assertFalse(f.queue.completeWithEffects(lease,carrier));
   var status=RecordingArchiveRuntime.status(f.queue,f.actor,s.sessionId());assertEquals("SUCCEEDED",status.state());assertTrue(status.available());assertEquals(2,status.sampleCount());
   try(var full=RecordingArchiveRuntime.download(f.queue,f.actor,s.sessionId(),null)){assertEquals(48,full.length());byte[] wav=full.body().readAllBytes();assertEquals("RIFF",new String(wav,0,4,java.nio.charset.StandardCharsets.US_ASCII));assertArrayEquals(new byte[]{1,2,3,4},Arrays.copyOfRange(wav,44,48));}
   try(var range=RecordingArchiveRuntime.download(f.queue,f.actor,s.sessionId(),"bytes=44-47")){assertEquals(44,range.offset());assertArrayEquals(new byte[]{1,2,3,4},range.body().readAllBytes());}
   assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM file_reference r JOIN recording_archive a ON a.file_id=r.file_id WHERE a.session_id=? AND r.owner_kind='UPLOAD_SESSION'",Integer.class,s.sessionId()));
   txDelete(f);assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->RecordingArchiveRuntime.download(f.queue,f.actor,s.sessionId(),null));
  }
 }
 static String leaseInput(Fixture f,String task){return j.queryForObject("SELECT input_id FROM durable_task WHERE id=? AND subject_id=?",String.class,task,f.meeting);}
 @Test void completionFailureRollsBackFileHandoffAndReadyStateTogether()throws Exception {
  try(var f=new Fixture()) {
   var s=f.recorded();var accepted=RecordingArchiveRuntime.accept(f.queue,f.actor,s.sessionId());var lease=f.queue.claim("RECORDING_ARCHIVE","archive-rollback").orElseThrow();assertEquals(accepted.taskId(),lease.taskId());var carrier=RecordingArchiveRuntime.prepare(f.queue,lease,LIVE);
   f.rejectSuccess.set(true);assertThrows(IllegalStateException.class,()->f.queue.completeWithEffects(lease,carrier));
   assertEquals("RUNNING",RecordingArchiveRuntime.status(f.queue,f.actor,s.sessionId()).state());assertEquals("PREPARED",j.queryForObject("SELECT state FROM recording_archive WHERE input_id=?",String.class,lease.spec().input().id()));
   assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM file_reference r JOIN recording_archive a ON a.file_id=r.file_id WHERE a.input_id=? AND r.owner_kind='RECORDING_ARCHIVE'",Integer.class,lease.spec().input().id()));
   assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM file_reference r JOIN recording_archive a ON a.file_id=r.file_id WHERE a.input_id=? AND r.owner_kind='UPLOAD_SESSION'",Integer.class,lease.spec().input().id()));
   f.rejectSuccess.set(false);assertTrue(f.queue.completeWithEffects(lease,carrier));assertTrue(RecordingArchiveRuntime.status(f.queue,f.actor,s.sessionId()).available());
  }
 }
 @Test void failedArchiveCanBeExplicitlyRetriedWithoutLosingAcceptedAudio()throws Exception {
  try(var f=new Fixture()) {
   var s=f.recorded();var first=RecordingArchiveRuntime.accept(f.queue,f.actor,s.sessionId());f.storageUnavailable.set(true);
   for(int attempt=1;attempt<=3;attempt++){
    if(attempt>1){var time=j.queryForObject("SELECT available_at FROM durable_task WHERE id=?",java.time.LocalDateTime.class,first.taskId());long delay=Math.max(0,java.time.Duration.between(java.time.Instant.now(),time.toInstant(java.time.ZoneOffset.UTC)).toMillis()+30);assertTrue(delay<12000);Thread.sleep(delay);}
    var lease=f.queue.claim("RECORDING_ARCHIVE","archive-retry").orElseThrow();assertEquals(first.taskId(),lease.taskId());assertTrue(f.queue.completeWithEffects(lease,RecordingArchiveRuntime.prepare(f.queue,lease,LIVE)));
   }
   assertEquals("FAILED",RecordingArchiveRuntime.status(f.queue,f.actor,s.sessionId()).state());assertEquals(1,f.recordings.ack(s.sessionId(),s.partId()).highestContiguousSeq());f.storageUnavailable.set(false);
   var retried=RecordingArchiveRuntime.accept(f.queue,f.actor,s.sessionId());assertNotEquals(first.taskId(),retried.taskId());assertEquals("READY",retried.state());assertEquals(retried.taskId(),RecordingArchiveRuntime.accept(f.queue,f.actor,s.sessionId()).taskId());
   var lease=f.queue.claim("RECORDING_ARCHIVE","archive-retry").orElseThrow();assertEquals(retried.taskId(),lease.taskId());assertTrue(f.queue.completeWithEffects(lease,RecordingArchiveRuntime.prepare(f.queue,lease,LIVE)));assertTrue(RecordingArchiveRuntime.status(f.queue,f.actor,s.sessionId()).available());
  }
 }
 @Test void httpPlaybackRequiresAuthenticationAndFreshAclAndSupportsRanges()throws Exception {
  try(var f=new Fixture()) {
   var s=f.recorded();var accepted=RecordingArchiveRuntime.accept(f.queue,f.actor,s.sessionId());var lease=f.queue.claim("RECORDING_ARCHIVE","recording-http-test").orElseThrow();assertEquals(accepted.taskId(),lease.taskId());assertTrue(f.queue.completeWithEffects(lease,RecordingArchiveRuntime.prepare(f.queue,lease,LIVE)));
   var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new ama.api.RecordingArchiveController(f.queue)).build();
   String path="/api/v1/recordings/"+s.sessionId()+"/audio",attribute="ama.api.SessionSecurityFilter.session";
   mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
   var session=new ResolvedSession(f.actor,"csrf",java.time.Instant.now().plusSeconds(600));
   mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/meetings/"+f.meeting+"/recording-archives/"+lease.spec().input().id()).requestAttr(attribute,session)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.taskId").value(accepted.taskId())).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.available").value(true));
   mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).requestAttr(attribute,session).header("Range","bytes=44-47")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isPartialContent()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Range","bytes 44-47/48")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(new byte[]{1,2,3,4}));
   mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).requestAttr(attribute,session).header("Range","bytes=99-")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isRequestedRangeNotSatisfiable());
   txDelete(f);mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).requestAttr(attribute,session)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
  }
 }
 static void txDelete(Fixture f){f.tx.required(()->{f.access.require(f.access.lock(f.actor,f.meeting),ama.access.MeetingAccess.Action.EDIT);var held=f.fences.lock(new SubjectRef("MEETING",f.meeting),false).orElseThrow();f.fences.advance(held,held.businessVersion()+1,held.cancelEpoch()+1,true);j.update("UPDATE meeting SET revision=revision+1,cancel_epoch=cancel_epoch+1,lifecycle='DELETED' WHERE id=?",f.meeting);f.outbox.flush();return null;});}
 @Test void deletionAfterPreparationFencesCompletionAndPreservesAckedAudio()throws Exception {
  try(var f=new Fixture()) {
   var s=f.recorded();var accepted=RecordingArchiveRuntime.accept(f.queue,f.actor,s.sessionId());var lease=f.queue.claim("RECORDING_ARCHIVE","recording-test").orElseThrow();assertEquals(accepted.taskId(),lease.taskId());var carrier=RecordingArchiveRuntime.prepare(f.queue,lease,LIVE);txDelete(f);
   assertFalse(f.queue.completeWithEffects(lease,carrier));assertEquals(1,f.recordings.ack(s.sessionId(),s.partId()).highestContiguousSeq());
   assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM file_reference r JOIN recording_archive a ON a.file_id=r.file_id WHERE a.session_id=? AND r.owner_kind='RECORDING_ARCHIVE'",Integer.class,s.sessionId()));
  }
 }
}
