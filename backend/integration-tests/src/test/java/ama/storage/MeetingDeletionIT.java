package ama.storage;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.*;
import ama.contract.MeetingContracts.*;
import ama.contract.StorageContracts.*;
import ama.db.*;
import ama.meeting.*;
import ama.task.*;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Owned MySQL rows and private actual mTLS/local volumes. Explicit row fixtures are not model proofs. */
class MeetingDeletionIT {
 @TempDir Path root;
 static DataSource source;static JdbcTemplate j;
 static String id(){return UUID.randomUUID().toString();}
 static final byte[] DATA="abc".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
 static final String HASH="ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
 @BeforeAll static void schema()throws Exception{String url=System.getenv("AMA_C_RECORDING_DATABASE_URL");if(url==null||!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/ama_c_recording_[0-9a-f]{12}"))throw new IllegalArgumentException("Owned schema required");source=MysqlDataSources.fromEnvironment("AMA_C_RECORDING_DATABASE_");j=new JdbcTemplate(source);Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();LocalFixture.ensurePki();}
 final class Fixture implements AutoCloseable {
  final ManagedLocalFixture files;final FileStoragePort storage;final FileBackendCatalog catalog;final TransactionRunner tx;final JdbcMeetingAccess access;final MeetingService meetings;final Actor actor;final String meeting;final AtomicBoolean fail=new AtomicBoolean();
  Fixture()throws Exception{
   files=new ManagedLocalFixture(root.resolve(id()));storage=files.startServer();
   FileStoragePort faulty=(FileStoragePort)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{FileStoragePort.class},(o,m,a)->{if(m.getName().equals("retireKey")&&fail.getAndSet(false))throw new StorageException(ErrorCode.STORAGE_UNAVAILABLE);try{return m.invoke(storage,a);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}});
   catalog=new FileBackendCatalog(source,List.of(new FileBackendCatalog.Backend(files.binding.id(),"local",1,"WRITABLE",faulty,files.binding)),files.binding.id());catalog.configure();tx=new TransactionRunner(source);var fences=new JdbcSubjectFences(source,Set.of("MEETING"));access=new JdbcMeetingAccess(source,tx,fences);actor=new Actor("deletion-"+id(),Status.ACTIVE,Set.of(Role.USER));j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",actor.enterpriseId(),actor.enterpriseId());meetings=new MeetingService(new JdbcMeetingRepository(source,tx,access,fences,new JdbcOutbox(source,new MeetingEventReferences(source).validators()),new ama.audit.JdbcAuditAdapter(source),x->{}));meeting=meetings.create(actor,id(),id()).id();
  }
  JdbcMeetingDeletion worker(){return new JdbcMeetingDeletion(source,tx,access,catalog);}
  StorageKey key(String key){return new StorageKey(files.binding.id(),"files",key);}
  void put(StorageKey key){storage.put(key,new ByteArrayInputStream(DATA),3,HASH);}
  String accept(){meetings.delete(actor,meeting,"original",new DeleteMeeting(0L),id());return worker().forMeeting(actor,meeting).deletionTaskId();}
  void drain(String job){try(var w=worker()){for(int n=0;n<400&&!w.status(actor,job).state().equals("COMPLETE");n++){j.update("UPDATE meeting_deletion SET available_at=UTC_TIMESTAMP(6) WHERE id=?",job);assertTrue(w.runOnce(job));}assertEquals("COMPLETE",w.status(actor,job).state());}}
  public void close()throws Exception{files.close();}
 }
 @Test void acceptedDeletionCancelsActiveWorkRetiresUnregisteredFilesAndErasesOwnedBodyHistory()throws Exception{
  try(var f=new Fixture()){
   String foreign=f.meetings.create(f.actor,id(),id()).id(),upload=id(),file=id(),session=id(),part=id(),task=id(),body=id(),input=id(),artifact=id();
   var own=f.key("uploads/"+upload);var token=f.storage.beginUpload(new UploadSpec(own.backendId(),own.namespace(),own.objectKey(),3));var receipt=f.storage.writePart(token,1,new ByteArrayInputStream(DATA),3,HASH);
   j.update("INSERT INTO upload_session(id,meeting_id,backend_id,namespace,object_key,cancel_epoch,expected_size,provider_upload_token,state,created_at,updated_at,display_name,purpose) VALUES (?,?,?,'files',?,0,3,?,'OPEN',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),'private.txt','MATERIAL')",upload,f.meeting,own.backendId(),own.objectKey(),token.sessionId());
   var registered=f.key("stored/"+file);f.put(registered);j.update("INSERT INTO file_object(id,meeting_id,backend_id,storage_type,namespace,object_key,display_name,size_bytes,sha256,state,created_at) VALUES (?,?,?,'local','files',?,'private',3,?,'AVAILABLE',UTC_TIMESTAMP(6))",file,f.meeting,registered.backendId(),registered.objectKey(),HASH);
   j.update("INSERT INTO recording_session(session_id,meeting_id,part_id,writer_epoch,backend_id,state,created_at) VALUES (?,?,?,1,?,'FINISHED',UTC_TIMESTAMP(6))",session,f.meeting,part,f.files.binding.id());var orphan=new StorageKey(f.files.binding.id(),"recordings",session+"/"+part+"/999");f.put(orphan);
   j.update("INSERT INTO minutes_revision(id,meeting_id,revision,title,title_origin,body,created_by,created_at) VALUES (?,?,0,'private title','HUMAN','private body',?,UTC_TIMESTAMP(6))",body,f.meeting,f.actor.enterpriseId());
   j.update("INSERT INTO durable_task(id,task_type,pool,subject_kind,subject_id,producer_key,input_kind,input_id,input_fingerprint,business_version,cancel_epoch,priority,max_attempts,state,available_at,created_at) VALUES (?,'MINUTES_EXPORT_V1','MINUTES_EXPORT','MEETING',?,?,'MINUTES_EXPORT_INPUT',?,?,0,0,0,3,'READY',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",task,f.meeting,task.replace("-","").repeat(2),input,HASH);
   j.update("INSERT INTO minutes_export_input(id,meeting_id,requested_by,request_key_hash,request_hash,source_revision_id,source_hash,format,base_revision,access_revision,cancel_epoch,backend_id,input_fingerprint,producer_key,created_at) VALUES (?,?,?,?,?,?,?,'md',0,0,0,?,?,?,UTC_TIMESTAMP(6))",input,f.meeting,f.actor.enterpriseId(),HASH,HASH,body,HASH,f.files.binding.id(),HASH,HASH);
   var unregistered=f.key("exports/"+artifact);f.put(unregistered);j.update("INSERT INTO minutes_export_artifact(id,input_id,task_id,attempt,file_id,backend_id,namespace,object_key,state,size_bytes,sha256,created_at) VALUES (?,?,?,1,?,?,'files',?,'INTENT',3,?,UTC_TIMESTAMP(6))",artifact,input,task,id(),unregistered.backendId(),unregistered.objectKey(),HASH);
   String oldCleanup=id();j.update("INSERT INTO file_cleanup(id,backend_id,namespace,object_key,state,not_before) VALUES (?,?,'files',?,'PENDING',UTC_TIMESTAMP(6))",oldCleanup,unregistered.backendId(),unregistered.objectKey());
   var remote=f.key("remote-original/"+id());f.put(remote); // No meeting ownership: never enters the cleanup inventory.
   String job=f.accept();assertThrows(MeetingAccess.AccessDeniedException.class,()->f.access.require(f.actor,f.meeting,MeetingAccess.Action.READ));assertEquals("CANCELLED",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task));
   f.fail.set(true);try(var w=f.worker()){for(int n=0;n<8&&!w.status(f.actor,job).state().equals("FAILED");n++)w.runOnce(job);assertEquals("FAILED",w.status(f.actor,job).state());assertTrue(j.queryForObject("SELECT COUNT(*) FROM meeting_info_revision WHERE meeting_id=?",Long.class,f.meeting)>0);}
   f.drain(job);for(var k:List.of(registered,orphan,unregistered)){assertThrows(StorageException.class,()->f.storage.stat(k));assertThrows(StorageException.class,()->f.put(k));}
   assertThrows(StorageException.class,()->f.storage.complete(token,List.of(receipt),HASH));assertThrows(StorageException.class,()->f.put(new StorageKey(orphan.backendId(),"recordings",session+"/"+part+"/1000")));assertEquals(3,f.storage.stat(remote).size());assertNotNull(f.meetings.read(f.actor,foreign));
   for(String table:List.of("minutes_revision","meeting_info_revision","file_object","upload_session","recording_session","minutes_export_input"))assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE meeting_id=?",Integer.class,f.meeting));
   assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM file_cleanup WHERE id=?",Integer.class,oldCleanup));assertEquals(1,f.meetings.delete(f.actor,f.meeting,"original",new DeleteMeeting(0L),id()).revision());assertEquals(job,f.worker().forMeeting(f.actor,f.meeting).deletionTaskId());assertThrows(Conflict.class,()->f.meetings.delete(f.actor,f.meeting,"original",new DeleteMeeting(1L),id()));
  }
 }
 @Test void newMaterialReceiptsProofCacheAndLocationAreErasedOnlyForDeletedMeeting()throws Exception{
  try(var f=new Fixture()){
   String foreign=f.meetings.create(f.actor,id(),id()).id();var references=new LinkedHashMap<String,String>();
   for(String meeting:List.of(f.meeting,foreign)){
    String reference=id();references.put(meeting,reference);
    j.update("INSERT INTO material_reference(id,meeting_id,kind,provider_id,external_id,state) VALUES (?,?,'KNOWLEDGE','CONTROLLED_HTTP_V1','shared-external-original','ACTIVE')",reference,meeting);
    j.update("INSERT INTO material_revision(id,reference_id,revision,external_version,content_hash,parse_state,created_at) VALUES (?,?,1,?,?,'READY',UTC_TIMESTAMP(6))",id(),reference,HASH,HASH);
    j.update("INSERT INTO material_knowledge_location(reference_id,library_id) VALUES (?,'test-private-library')",reference);
    j.update("INSERT INTO material_authority_cache(meeting_id,actor_id,dependency_hash,pins_hash,state,demand_until,next_check_at) VALUES (?,?,?,?,'UNKNOWN',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",meeting,f.actor.enterpriseId(),HASH,HASH);
    j.update("INSERT INTO material_batch_receipt(meeting_id,scope_hash,request_hash,response_body,created_at) VALUES (?,?,?,'private receipt with source metadata',UTC_TIMESTAMP(6))",meeting,HASH,HASH);
   }
   String job=f.accept();f.drain(job);
   for(String table:List.of("material_batch_receipt","material_authority_cache","material_reference")){
    assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE meeting_id=?",Integer.class,f.meeting));
    assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE meeting_id=?",Integer.class,foreign));
   }
   for(String table:List.of("material_knowledge_location","material_revision")){
    assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE reference_id=?",Integer.class,references.get(f.meeting)));
    assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE reference_id=?",Integer.class,references.get(foreign)));
   }
  }
 }
 @Test void disabledOwnerCannotInspectButCannotStrandAlreadyAuthorizedCleanup()throws Exception{
  try(var f=new Fixture()){String job=f.accept();j.update("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id=?",f.actor.enterpriseId());try(var w=f.worker()){assertThrows(MeetingAccess.AccessDeniedException.class,()->w.status(f.actor,job));for(int n=0;n<150;n++){if(j.queryForObject("SELECT state FROM meeting_deletion WHERE id=?",String.class,job).equals("COMPLETE"))break;assertTrue(w.runOnce(job));}assertEquals("COMPLETE",j.queryForObject("SELECT state FROM meeting_deletion WHERE id=?",String.class,job));}}
 }
 @Test void failedAcceptanceRollsBackTombstoneAndInventoryTogether()throws Exception{
  try(var f=new Fixture()){String sid=id();j.update("INSERT INTO recording_session(session_id,meeting_id,part_id,writer_epoch,backend_id,state,created_at) VALUES (?,?,?,1,?,'PAUSED',UTC_TIMESTAMP(6))",sid,f.meeting,id(),f.files.binding.id());assertThrows(Conflict.class,f::accept);assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM meeting_deletion WHERE meeting_id=?",Integer.class,f.meeting));assertEquals("ACTIVE",j.queryForObject("SELECT lifecycle FROM meeting WHERE id=?",String.class,f.meeting));}
 }
 @Test void restartedCleanupWaitsForTheOldLeaseThenContinuesFromDurableInventory()throws Exception{
  try(var f=new Fixture()){
   String file=id();var key=f.key("crash/"+file);f.put(key);j.update("INSERT INTO file_object(id,meeting_id,backend_id,storage_type,namespace,object_key,display_name,size_bytes,sha256,state,created_at) VALUES (?,?,?,'local','files',?,'private',3,?,'AVAILABLE',UTC_TIMESTAMP(6))",file,f.meeting,key.backendId(),key.objectKey(),HASH);
   String job=f.accept();try(var first=f.worker()){assertTrue(first.runOnce(job));assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM meeting_deletion_item WHERE deletion_id=?",Integer.class,job));}
   j.update("UPDATE meeting_deletion SET state='RUNNING',lease_token=?,lease_until=TIMESTAMPADD(MINUTE,10,UTC_TIMESTAMP(6)) WHERE id=?",id(),job);
   try(var restarted=f.worker()){assertFalse(restarted.runOnce(job));assertEquals(3,f.storage.stat(key).size());}
   j.update("UPDATE meeting_deletion SET lease_until=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(6)) WHERE id=?",job);f.drain(job);assertThrows(StorageException.class,()->f.storage.stat(key));
  }
 }
}
