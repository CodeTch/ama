package ama.storage;

import ama.contract.IdentityContracts.*;
import ama.contract.TaskContracts.*;
import ama.db.*;
import ama.export.*;
import ama.meeting.*;
import ama.task.*;
import ama.transcript.SavedMinutesExportSource;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real MySQL queue and local immutable storage; saved-source proof is an explicit mock boundary.
 * This tests queue confinement, replay and revocation, not genuine minutes graph authorization. */
class MinutesExportTaskIT {
 @TempDir Path root;
 static javax.sql.DataSource source;static JdbcTemplate j;
 @BeforeAll static void schema(){
  String base=Objects.requireNonNull(System.getenv("AMA_C_RECORDING_DATABASE_URL"));
  if(!base.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/ama_c_recording_[0-9a-f]{12}"))throw new IllegalArgumentException("Owned component schema required");
  String schema="ama_c_recording_"+UUID.randomUUID().toString().replace("-","").substring(0,12),prefix=base.substring(0,base.lastIndexOf('/')+1);
  String user=System.getenv("AMA_C_RECORDING_DATABASE_USERNAME"),password=System.getenv("AMA_C_RECORDING_DATABASE_PASSWORD");
  new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(prefix+"mysql",user,password)).execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
  source=new org.springframework.jdbc.datasource.DriverManagerDataSource(prefix+schema,user,password);j=new JdbcTemplate(source);
  org.flywaydb.core.Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
 }
 final PreparationCancellation cancellation=new PreparationCancellation(){public boolean cancelled(){return false;}public AutoCloseable register(Runnable abort){return ()->{};}};
 final class Fixture implements AutoCloseable {
  final ManagedLocalFixture files;final FileBackendCatalog backends;final FileRegistry registry;final JdbcTaskQueue queue;final JdbcMeetingAccess access;final Actor actor;final String meeting,revision=UUID.randomUUID().toString();
  final SavedMinutesExportSource saved=mock(SavedMinutesExportSource.class);final SavedMinutesExportSource.Prepared prepared=mock(SavedMinutesExportSource.Prepared.class);
  final MinutesExportProcess converter;
  Fixture()throws Exception{
   files=new ManagedLocalFixture(root.resolve(UUID.randomUUID().toString()));var storage=files.startServer();backends=new FileBackendCatalog(source,List.of(new FileBackendCatalog.Backend(files.binding.id(),"local",1,"WRITABLE",storage,files.binding)),files.binding.id());backends.configure();
   var tx=new TransactionRunner(source);var fences=new JdbcSubjectFences(source,Set.of("MEETING"));access=new JdbcMeetingAccess(source,tx,fences);var validators=new HashMap<>(new MeetingEventReferences(source).validators());validators.putAll(MinutesExportRuntime.eventReferences(source));var outbox=new JdbcOutbox(source,validators);
   String id="export-task-"+UUID.randomUUID();j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",id,id);actor=new Actor(id,Status.ACTIVE,Set.of(Role.USER));
   meeting=new MeetingService(new JdbcMeetingRepository(source,tx,access,fences,outbox,new ama.audit.JdbcAuditAdapter(source),m->{})).create(actor,UUID.randomUUID().toString(),"export-task-protocol").id();
   j.update("INSERT INTO minutes_revision(id,meeting_id,revision,title,title_origin,body,created_by,created_at) VALUES (?,?,0,'Saved title','HUMAN','Saved body',?,UTC_TIMESTAMP(6))",revision,meeting,id);
   j.update("UPDATE meeting SET minutes_revision_id=? WHERE id=?",revision,meeting);
   registry=new JdbcFileRegistry(source,tx,access,(a,m,p)->{},backends,Map.of("MINUTES_EXPORT",new ama.export.MinutesExportReferencePolicy(source,()->saved)),new ama.contract.ConfigArchiveContracts.ArchiveReadBudget(65536,1048576,64,128,16,200000,32,255,65536,262144,1048576,65536,32,250000,500000));
   when(prepared.meetingId()).thenReturn(meeting);when(prepared.sourceRevision()).thenReturn(revision);when(prepared.sourceHash()).thenReturn("c".repeat(64));when(prepared.document()).thenReturn(new MinutesExportDocument("Saved title","Saved body"));
   when(prepared.meetingRevision()).thenReturn(0L);when(prepared.accessRevision()).thenReturn(0L);when(prepared.cancelEpoch()).thenReturn(0L);
   when(saved.prepare(any(),eq(meeting),eq(revision))).thenReturn(prepared);
   when(saved.lockRoot(eq(prepared),any(),eq(meeting))).thenAnswer(call->registry.lock(call.getArgument(1),meeting,ama.access.MeetingAccess.Action.READ));
   var metadata=mock(SavedMinutesExportSource.Metadata.class);when(metadata.identities()).thenReturn(List.of(id));when(saved.discover(meeting)).thenReturn(metadata);
   when(saved.capture(any(),any(),eq(metadata),any())).thenAnswer(call->{((Runnable)call.getArgument(3)).run();return mock(SavedMinutesExportSource.Captured.class);});
   doAnswer(call->{((Runnable)call.getArgument(2)).run();return null;}).when(saved).recheck(eq(prepared),any(),any(Runnable.class));
   when(saved.recheckRead(eq(prepared),any(),anyList(),any(Runnable.class),any(Runnable.class))).thenAnswer(call->{var gate=(FileRegistry.LockedMeeting)call.getArgument(1);List<String> ids=call.getArgument(2);((Runnable)call.getArgument(3)).run();var receipt=registry.prelockSources(gate,ids,false).orElseThrow();((Runnable)call.getArgument(4)).run();return new FileRegistry.AcquiredSources(receipt,ids);});
   queue=MinutesExportRuntime.install(source,fences,outbox,new MinutesExportRuntime.Dependencies(access,registry,backends,saved));
   converter=new MinutesExportProcess(root.resolve("conversion"),new MinutesExportRenderer.Fonts(null,null),Duration.ofSeconds(10));
  }
  MinutesExportRuntime.Status accept(String key){return MinutesExportRuntime.accept(queue,actor,meeting,key,revision,"md");}
  TaskLease claim(){return queue.claim(MinutesExportRuntime.POOL,"EXPORT_TEST").orElseThrow();}
  public void close()throws Exception{
   // Only this fixture's queued test tasks: a failed assertion must not let another
   // fixture claim them from the shared pool. Keep the owned schema and source evidence.
   try{j.update("UPDATE durable_task SET state='CANCELLED',lease_owner=NULL,expires_at=NULL WHERE subject_kind='MEETING' AND subject_id=? AND state IN ('READY','RUNNING')",meeting);}finally{files.close();}
  }
 }
 @Test void originalKeyReplaysAndOnlyActualQueueCompletionRegistersTheFile()throws Exception{
  try(var f=new Fixture()){
   var first=f.accept("original");assertEquals(first.exportJobId(),f.accept("original").exportJobId());
   assertThrows(ama.contract.TranscriptContracts.Failure.class,()->MinutesExportRuntime.accept(f.queue,f.actor,f.meeting,"original",f.revision,"pdf"));
   var lease=f.claim();var carrier=MinutesExportRuntime.prepare(f.queue,lease,f.converter,cancellation);
   assertThrows(IllegalStateException.class,carrier::get);
   assertThrows(IllegalArgumentException.class,()->f.queue.completeWithEffects(lease,()->new TaskResult(Outcome.SUCCEEDED,new TypedRef("MINUTES_EXPORT",UUID.randomUUID().toString()),null)));
   assertTrue(f.queue.completeWithEffects(lease,carrier));var done=MinutesExportRuntime.status(f.queue,f.actor,first.exportJobId());assertEquals("SUCCEEDED",done.state());assertNotNull(done.fileId());
   assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM generated_file_origin WHERE file_id=? AND task_id=?",Integer.class,done.fileId(),lease.taskId()));
   assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM upload_session WHERE completed_file_id=?",Integer.class,done.fileId()));
   var row=j.queryForMap("SELECT backend_id,namespace,object_key,size_bytes FROM file_object WHERE id=?",done.fileId());var key=new ama.contract.StorageContracts.StorageKey((String)row.get("backend_id"),(String)row.get("namespace"),(String)row.get("object_key"));
   try(var bytes=f.backends.require(key.backendId(),false).storage().read(key,0,((Number)row.get("size_bytes")).longValue())){assertEquals("# Saved title\n\nSaved body\n",new String(bytes.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
   try(var full=f.registry.download(f.actor,done.fileId(),null)){assertEquals("# Saved title\n\nSaved body\n",new String(full.body().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
   try(var range=f.registry.read(f.actor,f.meeting,new FileRegistry.CurrentReadRequest("MINUTES_EXPORT",done.fileId()),"bytes=2-6")){assertEquals("Saved",new String(range.body().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
   assertFalse(f.queue.completeWithEffects(lease,carrier));assertEquals(first.exportJobId(),f.accept("original").exportJobId());
   j.update("UPDATE meeting SET governance_state='WITHDRAW_PENDING' WHERE id=?",f.meeting);assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->f.registry.download(f.actor,done.fileId(),null));
  }
 }
 @Test void missingPublicationAuthorityDeniesReadWithoutConsumingQueuedWorkAsRevoked()throws Exception{
  try(var f=new Fixture()){
   var accepted=f.accept("authority-outage");
   j.update("UPDATE meeting SET ever_published=1 WHERE id=?",f.meeting);
   f.access.installPublicationValidity();
   assertThrows(ama.access.MeetingAccess.AuthorityUnavailableException.class,()->f.access.require(f.actor,f.meeting,ama.access.MeetingAccess.Action.READ));
   assertThrows(ama.access.MeetingAccess.AuthorityUnavailableException.class,f::claim);
   assertEquals("READY",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,accepted.exportJobId()));
   assertEquals(0L,j.queryForObject("SELECT attempt FROM durable_task WHERE id=?",Long.class,accepted.exportJobId()));
   j.update("UPDATE meeting SET governance_state='WITHDRAW_PENDING' WHERE id=?",f.meeting);
   assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->f.access.require(f.actor,f.meeting,ama.access.MeetingAccess.Action.READ));
  }
 }
 @Test void revocationBetweenConversionAndCompletionCannotPublishPhysicalBytes()throws Exception{
  try(var f=new Fixture()){
   var accepted=f.accept("revoked");var lease=f.claim();var carrier=MinutesExportRuntime.prepare(f.queue,lease,f.converter,cancellation);
   j.update("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id=?",f.actor.enterpriseId());
   assertTrue(f.queue.completeWithEffects(lease,carrier));assertEquals("FAILED",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,accepted.exportJobId()));
   assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM generated_file_origin WHERE task_id=?",Integer.class,lease.taskId()));
   assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM minutes_export_artifact WHERE task_id=? AND state='PREPARED'",Integer.class,lease.taskId()));
  }
 }
 @Test void expiredAttemptCannotRegisterAnArtifactAfterItsReplacementHasRun()throws Exception{
  try(var f=new Fixture()){
   var accepted=f.accept("lease");var old=f.claim();var abandoned=MinutesExportRuntime.prepare(f.queue,old,f.converter,cancellation);
   j.update("UPDATE durable_task SET expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",old.taskId());
   var fresh=f.claim();assertEquals(old.attempt()+1,fresh.attempt());assertFalse(f.queue.completeWithEffects(old,abandoned));
   var actual=MinutesExportRuntime.prepare(f.queue,fresh,f.converter,cancellation);assertTrue(f.queue.completeWithEffects(fresh,actual));
   assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM generated_file_origin WHERE task_id=?",Integer.class,accepted.exportJobId()));
   assertEquals(2,j.queryForObject("SELECT COUNT(*) FROM minutes_export_artifact WHERE task_id=?",Integer.class,accepted.exportJobId()));
  }
 }
}
