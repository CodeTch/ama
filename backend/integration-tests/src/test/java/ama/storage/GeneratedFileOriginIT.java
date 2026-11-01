package ama.storage;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.*;
import ama.contract.StorageContracts.StorageKey;
import ama.db.*;
import ama.meeting.*;
import ama.task.*;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Actual MySQL/local storage origin checks. The reference fixture is not an export-task authority. */
class GeneratedFileOriginIT {
 @TempDir Path root;
 static javax.sql.DataSource source;static JdbcTemplate j;
 @BeforeAll static void schema(){String url=System.getenv("AMA_C_RECORDING_DATABASE_URL");if(url==null||!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/ama_c_recording_[0-9a-f]{12}"))throw new IllegalArgumentException("Owned component schema required");source=MysqlDataSources.fromEnvironment("AMA_C_RECORDING_DATABASE_");j=new JdbcTemplate(source);Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();}
 final class Fixture implements AutoCloseable {
  final ManagedLocalFixture files;final FileRegistry registry;final TransactionRunner tx;final JdbcMeetingAccess access;final Actor actor,foreign;final String meeting,file=UUID.randomUUID().toString(),origin=UUID.randomUUID().toString(),task=UUID.randomUUID().toString();final byte[] bytes="saved generated artifact 中文".getBytes(java.nio.charset.StandardCharsets.UTF_8);final String hash;
  Fixture()throws Exception{this(false);}
  Fixture(boolean completeRead)throws Exception{
   files=new ManagedLocalFixture(root.resolve(UUID.randomUUID().toString()));var storage=files.startServer();var catalog=new FileBackendCatalog(source,List.of(new FileBackendCatalog.Backend(files.binding.id(),"local",1,"WRITABLE",storage,files.binding)),files.binding.id());catalog.configure();
   tx=new TransactionRunner(source);var fences=new JdbcSubjectFences(source,Set.of("MEETING"));access=new JdbcMeetingAccess(source,tx,fences);var outbox=new JdbcOutbox(source,new MeetingEventReferences(source).validators());
   actor=identity();foreign=identity();var service=new MeetingService(new JdbcMeetingRepository(source,tx,access,fences,outbox,new ama.audit.JdbcAuditAdapter(source),m->{}));meeting=service.create(actor,UUID.randomUUID().toString(),"generated-file-component").id();
   hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));var key=new StorageKey(files.binding.id(),"files","generated/"+file);storage.put(key,new ByteArrayInputStream(bytes),bytes.length,hash);
   var row=j.queryForMap("SELECT revision,cancel_epoch FROM meeting WHERE id=?",meeting);
   j.update("INSERT INTO durable_task(id,task_type,pool,subject_kind,subject_id,producer_key,input_kind,input_id,input_fingerprint,business_version,cancel_epoch,priority,max_attempts,state,available_at,created_at) VALUES (?,'MINUTES_EXPORT_V1','MINUTES_EXPORT','MEETING',?,?,'MINUTES_EXPORT_INPUT',?,?,?, ?,0,3,'SUCCEEDED',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",task,meeting,HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(task.getBytes())),origin,hash,row.get("revision"),row.get("cancel_epoch"));
   j.update("INSERT INTO file_object(id,meeting_id,backend_id,storage_type,namespace,object_key,display_name,size_bytes,sha256,state,created_at) VALUES (?,?,?,'local','files',?,'纪要.md',?,?,'AVAILABLE',UTC_TIMESTAMP(6))",file,meeting,key.backendId(),key.objectKey(),bytes.length,hash);
   j.update("INSERT INTO generated_file_origin(file_id,meeting_id,origin_kind,origin_id,task_id,purpose,cancel_epoch,backend_id,namespace,object_key,size_bytes,sha256,created_at) VALUES (?,?,'MINUTES_EXPORT',?,?,'MINUTES_EXPORT',?,?,'files',?,?,?,UTC_TIMESTAMP(6))",file,meeting,origin,task,row.get("cancel_epoch"),key.backendId(),key.objectKey(),bytes.length,hash);
   j.update("INSERT INTO file_reference(file_id,owner_kind,owner_id,purpose) VALUES (?,'MINUTES_EXPORT',?,'MINUTES_EXPORT')",file,origin);
   var reference=new FileReferencePolicy(){public Object prelock(FileRegistry.LockedMeeting gate,String m,FileRegistry.Reference ref,String id){if(!meeting.equals(m)||!origin.equals(ref.ownerId())||!file.equals(id))throw new ama.contract.FileContracts.NotFound();return ref;}public void validate(Object proof,String m,FileRegistry.Reference ref,FileRegistry.FileRecord value,String purpose){if(!ref.equals(proof)||!"MINUTES_EXPORT".equals(purpose)||!hash.equals(value.sha256()))throw new ama.contract.FileContracts.NotFound();}};
   var complete=new CompleteReadPolicy(){
    record Preparation(String file) implements ReadPreparation{}
    record Scope(FileRegistry.Reference reference,String file) implements FileRegistry.ReferenceContext{}
    public Object prelock(FileRegistry.LockedMeeting gate,String m,FileRegistry.Reference r,String f){return new Scope((FileRegistry.Reference)reference.prelock(gate,m,r,f),f);}
    public Object prelock(FileRegistry.LockedMeeting gate,String m,FileRegistry.Reference r,String f,FileRegistry.ReferenceContext context){if(!(context instanceof Scope s)||!s.file().equals(f)||!s.reference().equals(r))throw new ama.contract.FileContracts.NotFound();return s;}
    public ReadPreparation prepareRead(Actor a,String m,FileRegistry.ReadRequest r){return new Preparation(((FileRegistry.CurrentReadRequest)r).fileId());}
    public void authorizeRead(FileRegistry.LockedMeeting gate,String m,FileRegistry.ReadRequest r){}
    public Object prelockCurrent(FileRegistry.LockedMeeting gate,String m,FileRegistry.Reference r,String f,ReadPreparation prepared){if(!(prepared instanceof Preparation p)||!p.file().equals(f))throw new ama.contract.FileContracts.NotFound();return prelock(gate,m,r,f);}
    public ReadPlan prelockRead(FileRegistry.LockedMeeting gate,String m,FileRegistry.ReadRequest r,ReadPreparation prepared){var ref=new FileRegistry.Reference("MINUTES_EXPORT",origin,"MINUTES_EXPORT");var scope=(Scope)prelockCurrent(gate,m,ref,file,prepared);return new ReadPlan(scope,List.of(new ReadMember(ref,file)),file);}
    public List<ReadMember> readMembers(Object value,FileRegistry.Reference r,String f){return List.of(new ReadMember(r,f));}
    public void prelockReadTasks(List<Object> scopes){assertEquals(1,scopes.size());assertEquals(task,j.queryForObject("SELECT id FROM durable_task WHERE id=? AND state='SUCCEEDED' FOR UPDATE",String.class,task));}
    public void finishRead(Object value,List<FileRegistry.CompletedSource> files){assertEquals(1,files.size());var s=(Scope)value;validate(s,meeting,s.reference(),files.getFirst().file(),files.getFirst().originalPurpose());}
    public void validate(Object value,String m,FileRegistry.Reference r,FileRegistry.FileRecord f,String p){reference.validate(((Scope)value).reference(),m,r,f,p);}
   };
   registry=new JdbcFileRegistry(source,tx,access,(a,m,p)->{},catalog,Map.of("MINUTES_EXPORT",completeRead?complete:reference),new ama.contract.ConfigArchiveContracts.ArchiveReadBudget(65536,1048576,64,128,16,200000,32,255,65536,262144,1048576,65536,1,250000,500000));
  }
  private Actor identity(){String id="export-origin-"+UUID.randomUUID();j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",id,id);return new Actor(id,Status.ACTIVE,Set.of(Role.USER));}
  public void close()throws Exception{files.close();}
 }
 @Test void generatedFileUsesNormalAuthorizedRangeWithoutAnUploadSession()throws Exception{
  try(var fixture=new Fixture()){
   assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM upload_session WHERE completed_file_id=?",Integer.class,fixture.file));
   try(var full=fixture.registry.download(fixture.actor,fixture.file,null)){assertArrayEquals(fixture.bytes,full.body().readAllBytes());}
   try(var range=fixture.registry.download(fixture.actor,fixture.file,"bytes=2-7")){assertEquals(2,range.offset());assertEquals(6,range.length());assertArrayEquals(Arrays.copyOfRange(fixture.bytes,2,8),range.body().readAllBytes());}
   assertThrows(MeetingAccess.AccessDeniedException.class,()->fixture.registry.download(fixture.foreign,fixture.file,null));
  }
 }
 @Test void generatedFileAlsoUsesTheCompleteOriginAndProducerReadProtocol()throws Exception{
  try(var fixture=new Fixture(true)){
   try(var full=fixture.registry.download(fixture.actor,fixture.file,null)){assertArrayEquals(fixture.bytes,full.body().readAllBytes());}
   try(var range=fixture.registry.read(fixture.actor,fixture.meeting,new FileRegistry.CurrentReadRequest("MINUTES_EXPORT",fixture.file),"bytes=2-7")){assertArrayEquals(Arrays.copyOfRange(fixture.bytes,2,8),range.body().readAllBytes());}
   j.update("UPDATE generated_file_origin SET sha256=? WHERE file_id=?","b".repeat(64),fixture.file);assertThrows(ama.contract.FileContracts.NotFound.class,()->fixture.registry.download(fixture.actor,fixture.file,null));
  }
 }
 @Test void artifactUnionDoesNotReduceTheOriginalSourceBudgetOrRelaxOrdinaryBatches()throws Exception{
  try(var f=new Fixture()){
   String uploaded=UUID.randomUUID().toString(),upload=UUID.randomUUID().toString(),key="uploaded/"+uploaded;
   f.files.client().put(new StorageKey(f.files.binding.id(),"files",key),new ByteArrayInputStream(f.bytes),f.bytes.length,f.hash);
   j.update("INSERT INTO file_object(id,meeting_id,backend_id,storage_type,namespace,object_key,display_name,size_bytes,sha256,state,created_at) VALUES (?,?,?,'local','files',?,'source.txt',?,?,'AVAILABLE',UTC_TIMESTAMP(6))",uploaded,f.meeting,f.files.binding.id(),key,f.bytes.length,f.hash);
   j.update("INSERT INTO upload_session(id,meeting_id,backend_id,namespace,object_key,cancel_epoch,expected_size,expected_sha256,state,completed_file_id,created_at,updated_at,display_name,purpose) VALUES (?,?,?,'files',?,0,?,?,'COMPLETED',?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),'source.txt','TRANSCRIPT_INPUT')",upload,f.meeting,f.files.binding.id(),key,f.bytes.length,f.hash,uploaded);
   f.tx.required(()->{var gate=f.registry.lock(f.actor,f.meeting,MeetingAccess.Action.READ);assertThrows(IllegalArgumentException.class,()->f.registry.prelockSources(gate,List.of(uploaded,f.file),false));return null;});
   f.tx.required(()->{var gate=f.registry.lock(f.actor,f.meeting,MeetingAccess.Action.READ);var union=f.registry.prelockDerivedSources(gate,List.of(uploaded),f.file,false).orElseThrow();var rows=f.registry.sources(gate,union,List.of(uploaded,f.file));assertEquals(2,rows.size());assertEquals(Set.of("TRANSCRIPT_INPUT","MINUTES_EXPORT"),rows.stream().map(FileRegistry.CompletedSource::originalPurpose).collect(java.util.stream.Collectors.toSet()));return null;});
   f.tx.required(()->{var gate=f.registry.lock(f.actor,f.meeting,MeetingAccess.Action.READ);assertThrows(ama.contract.FileContracts.NotFound.class,()->f.registry.prelockDerivedSources(gate,List.of(f.file),uploaded,false));return null;});
  }
 }
 @Test void changedOriginHashAndEpochCannotAuthorizeExistingPhysicalBytes()throws Exception{
  try(var fixture=new Fixture()){
   j.update("UPDATE generated_file_origin SET sha256=? WHERE file_id=?","b".repeat(64),fixture.file);assertThrows(ama.contract.FileContracts.NotFound.class,()->fixture.registry.download(fixture.actor,fixture.file,null));
   j.update("UPDATE generated_file_origin SET sha256=?,cancel_epoch=cancel_epoch+1 WHERE file_id=?",fixture.hash,fixture.file);assertThrows(ama.contract.FileContracts.Conflict.class,()->fixture.registry.download(fixture.actor,fixture.file,null));
  }
 }
 @Test void contradictoryUploadedAndGeneratedOriginsFailClosed()throws Exception{
  try(var fixture=new Fixture()){
   var row=j.queryForMap("SELECT * FROM generated_file_origin WHERE file_id=?",fixture.file);
   j.update("INSERT INTO upload_session(id,meeting_id,backend_id,namespace,object_key,cancel_epoch,expected_size,expected_sha256,state,completed_file_id,created_at,updated_at,display_name,purpose) VALUES (?,?,?,'files',?,?,?,?, 'COMPLETED',?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),'fixture.md','MINUTES_EXPORT')",UUID.randomUUID().toString(),fixture.meeting,row.get("backend_id"),row.get("object_key"),row.get("cancel_epoch"),row.get("size_bytes"),row.get("sha256"),fixture.file);
   assertThrows(ama.contract.FileContracts.NotFound.class,()->fixture.registry.download(fixture.actor,fixture.file,null));
  }
 }
 @Test void publishedReadFactsDoNotGrantPublishedBusinessWrites()throws Exception{
  try(var fixture=new Fixture()){
   j.update("UPDATE meeting SET ever_published=1,stage='PUBLISHED' WHERE id=?",fixture.meeting);
   assertTrue(readFacts(fixture,fixture.actor,false));assertFalse(readFacts(fixture,fixture.foreign,false));
  }
 }
 @Test void deniedIdentityGovernanceAndEpochCannotBecomeReadCapabilities()throws Exception{
  try(var fixture=new Fixture()){
   assertFalse(readFacts(fixture,fixture.actor,true));
   j.update("UPDATE meeting SET governance_state='WITHDRAW_PENDING' WHERE id=?",fixture.meeting);assertFalse(readFacts(fixture,fixture.actor,false));
   j.update("UPDATE meeting SET governance_state='ACTIVE' WHERE id=?",fixture.meeting);j.update("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id=?",fixture.actor.enterpriseId());assertFalse(readFacts(fixture,fixture.actor,false));
  }
 }
 private boolean readFacts(Fixture fixture,Actor actor,boolean wrongEpoch){return fixture.tx.required(()->{
  var row=j.queryForMap("SELECT revision,cancel_epoch,ever_published FROM meeting WHERE id=?",fixture.meeting);long epoch=((Number)row.get("cancel_epoch")).longValue(),revision=((Number)row.get("revision")).longValue();
  var identities=fixture.access.tryLockFactIdentities(actor,List.of(actor.enterpriseId()),false).orElseThrow();var facts=fixture.access.lockFactRoot(identities,fixture.meeting,false).orElseThrow();
  if(((Number)row.get("ever_published")).longValue()!=0){assertFalse(fixture.access.factAllowsBusiness(facts,epoch,revision));assertThrows(MeetingAccess.AccessDeniedException.class,()->fixture.access.authorizeFactRoot(facts,epoch,revision));}
  var allowed=fixture.access.authorizeReadFactRoot(facts,epoch+(wrongEpoch?1:0),revision);allowed.ifPresent(gate->fixture.access.require(gate,MeetingAccess.Action.READ));return allowed.isPresent();
 });}
}
