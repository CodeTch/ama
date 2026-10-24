package ama.storage;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.*;
import ama.db.TransactionRunner;
import ama.meeting.JdbcMeetingAccess;
import ama.support.MysqlTestDatabase;
import ama.task.JdbcSubjectFences;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class Task05LegacyAuthorityIT {
 @Test void initialAuthorizationNeverTransfersUnboundedIdentityNames() {
  var probe=new RegistryProbeSource(MysqlTestDatabase.dataSource());var j=new JdbcTemplate(probe);
  String owner="authority-"+UUID.randomUUID(),meeting=UUID.randomUUID().toString();
  j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",owner,"name😀".repeat(50000));
  j.update("INSERT INTO task_subject(subject_kind,subject_id,business_version,cancel_epoch,deleted) VALUES ('MEETING',?,0,0,0)",meeting);
  j.update("INSERT INTO meeting(id,owner_id,stage,created_at,updated_at) VALUES (?,?,'ENTRY',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",meeting,owner);
  var tx=new TransactionRunner(probe);var access=new JdbcMeetingAccess(probe,tx,new JdbcSubjectFences(probe,Set.of("MEETING")));
  var actor=new Actor(owner,Status.ACTIVE,Set.of(Role.USER));probe.statements.clear();
  tx.required(()->{access.require(access.lock(actor,meeting),MeetingAccess.Action.EDIT);return null;});
  assertFalse(probe.statements.stream().anyMatch(s->s.contains("display_name")),"Authorization may not transfer a name before source budgets apply");
  probe.statements.clear();
  tx.required(()->{var identities=access.tryLockIdentities(actor,List.of(),false).orElseThrow();access.lockPrepared(identities,meeting,false).orElseThrow();return null;});
  assertFalse(probe.statements.stream().anyMatch(s->s.contains("display_name")));
 }
 @Test void authenticCompletedSourceReceiptIsBoundToGateHolderAndCompleteSet() throws Exception {
  try(var f=new Task05AuthorityFixture()) {
   var a=f.upload("first");var b=f.upload("second");var registry=f.registry(Map.of());
   FileRegistry.LockedSources[] saved=new FileRegistry.LockedSources[1];
   FileRegistry.LockedMeeting[] gateSaved=new FileRegistry.LockedMeeting[1];
   f.tx.required(()->{
    var gate=registry.lock(f.actor,f.meeting,MeetingAccess.Action.EDIT);gateSaved[0]=gate;
    var held=assertDoesNotThrow(()->registry.prelockSources(gate,List.of(b.file(),a.file()),false).orElseThrow());saved[0]=held;
    var actual=registry.sources(gate,held,List.of(a.file(),b.file()));assertEquals(2,actual.size());
    assertEquals(Set.of(a.upload(),b.upload()),new HashSet<>(actual.stream().map(FileRegistry.CompletedSource::uploadId).toList()));
    assertThrows(IllegalArgumentException.class,()->registry.sources(gate,held,List.of(a.file())));
    assertThrows(IllegalArgumentException.class,()->registry.sources(gate,held,List.of(a.file(),a.file())));
    assertThrows(IllegalArgumentException.class,()->registry.sources(gate,new FileRegistry.LockedSources(){},List.of(a.file(),b.file())));
    assertThrows(IllegalStateException.class,()->registry.prelockSources(gate,List.of(a.file()),false));
    return null;
   });
   assertThrows(IllegalStateException.class,()->registry.sources(gateSaved[0],saved[0],List.of(a.file(),b.file())));
   f.tx.required(()->{var gate=registry.lock(f.actor,f.meeting,MeetingAccess.Action.EDIT);assertThrows(IllegalArgumentException.class,()->registry.sources(gate,saved[0],List.of(a.file(),b.file())));return null;});
  }
 }
 @Test void sameSourceReceiptSupportsOneBindingAndCaughtFailureDoomsTransaction() throws Exception {
  try(var f=new Task05AuthorityFixture()) {
   var a=f.upload("bind-a");var b=f.upload("bind-b");String owner=UUID.randomUUID().toString();
   FileReferencePolicy policy=new FileReferencePolicy(){
    public Object prelock(FileRegistry.LockedMeeting g,String m,FileRegistry.Reference r,String file){return r;}
    public void validate(Object p,String m,FileRegistry.Reference r,FileRegistry.FileRecord file,String purpose){if(!r.equals(p))throw new AssertionError();}
   };
   var registry=f.registry(Map.of("TASK03_SOURCE",policy));var reference=new FileRegistry.Reference("TASK03_SOURCE",owner,"TRANSCRIPT_INPUT");
   f.tx.required(()->{
    var gate=registry.lock(f.actor,f.meeting,MeetingAccess.Action.EDIT);
    var proof=registry.prelock(gate,reference,a.file());var receipt=registry.prelockSources(gate,List.of(a.file()),false).orElseThrow();
    long revision=registry.sources(gate,receipt,List.of(a.file())).getFirst().file().locationRevision();
    assertDoesNotThrow(()->registry.bindAll(receipt,List.of(new FileRegistry.Binding(proof,revision,a.upload()))));
    assertThrows(IllegalArgumentException.class,()->registry.sources(gate,receipt,List.of(a.file())));return null;
   });
   assertEquals(1,f.j.queryForObject("SELECT COUNT(*) FROM file_reference WHERE file_id=? AND owner_kind='TASK03_SOURCE'",Integer.class,a.file()));
   assertEquals(0,f.j.queryForObject("SELECT COUNT(*) FROM file_reference WHERE file_id=? AND owner_kind='UPLOAD_SESSION'",Integer.class,a.file()));
   long before=f.j.queryForObject("SELECT reference_epoch FROM file_object WHERE id=?",Long.class,b.file());
   assertThrows(org.springframework.transaction.UnexpectedRollbackException.class,()->f.tx.required(()->{
    var gate=registry.lock(f.actor,f.meeting,MeetingAccess.Action.EDIT);var proof=registry.prelock(gate,reference,b.file());var receipt=registry.prelockSources(gate,List.of(b.file()),false).orElseThrow();
    assertThrows(RuntimeException.class,()->registry.bindAll(receipt,List.of(new FileRegistry.Binding(proof,999,b.upload()))));
    assertThrows(RuntimeException.class,()->registry.sources(gate,receipt,List.of(b.file())));
    f.j.update("UPDATE file_object SET reference_epoch=reference_epoch+10 WHERE id=?",b.file());return null;
   }));
   assertEquals(before,f.j.queryForObject("SELECT reference_epoch FROM file_object WHERE id=?",Long.class,b.file()));
  }
 }
 @Test void adoptionUsesExistingGenuineIdentityAndRootWithoutSecondAcquisition() throws Exception {
  try(var f=new Task05AuthorityFixture()) {
   var registry=f.registry(Map.of());
   f.tx.required(()->{
    var access=f.access.lock(f.actor,f.meeting);f.source.statements.clear();
    var gate=assertDoesNotThrow(()->registry.adopt(f.actor,f.meeting,access,MeetingAccess.Action.EDIT));
    assertSame(access,registry.access(gate));
    assertFalse(f.source.statements.stream().anyMatch(s->s.startsWith("SELECT enterprise_id")));
    assertThrows(IllegalStateException.class,()->registry.adopt(f.actor,f.meeting,access,MeetingAccess.Action.EDIT));return null;
   });
  }
 }
 @Test void originalMetadataAndHistoricalRouteCannotBeSubstituted() throws Exception {
  try(var f=new Task05AuthorityFixture()) {
   var a=f.upload("original");var registry=f.registry(Map.of());
   Runnable acquire=()->f.tx.required(()->{var gate=registry.lock(f.actor,f.meeting,MeetingAccess.Action.READ);registry.prelockSources(gate,List.of(a.file()),false).orElseThrow();return null;});
   f.j.update("UPDATE upload_session SET expected_size=99 WHERE id=?",a.upload());assertThrows(RuntimeException.class,acquire::run);
   f.j.update("UPDATE upload_session SET expected_size=8,cancel_epoch=1 WHERE id=?",a.upload());assertThrows(RuntimeException.class,acquire::run);
   f.j.update("UPDATE upload_session SET cancel_epoch=0 WHERE id=?",a.upload());
   f.j.update("UPDATE storage_backend SET config_revision=config_revision+1 WHERE id=?",f.storage.binding.id());assertThrows(RuntimeException.class,acquire::run);
   f.j.update("UPDATE storage_backend SET config_revision=config_revision-1 WHERE id=?",f.storage.binding.id());
   f.source.statements.clear();assertDoesNotThrow(acquire::run);
   assertFalse(f.source.statements.stream().anyMatch(s->s.contains("provider_upload_token")||s.contains("provider_version_id")||s.contains("display_name")),"authorization projections omit unneeded LONGTEXT");
   int firstFile=-1,lastSession=-1;for(int i=0;i<f.source.statements.size();i++){String q=f.source.statements.get(i);if(q.contains("FOR UPDATE")&&q.contains("FROM upload_session"))lastSession=i;if(firstFile<0&&q.contains("FOR UPDATE")&&q.contains("FROM file_object"))firstFile=i;}
   assertTrue(lastSession>=0&&firstFile>lastSession);
  }
 }
 @Test void completeFinalizerRunsOnceForPlannedAndGenericReadsBeforeAnyOpen() throws Exception {
  try(var f=new Task05AuthorityFixture()) {
   var a=f.upload("readable");String owner=UUID.randomUUID().toString();
   var ref=new FileRegistry.Reference("TASK03_COMPLETE",owner,"TRANSCRIPT_INPUT");
   f.j.update("INSERT INTO file_reference(file_id,owner_kind,owner_id,purpose) VALUES (?,?,?,?)",a.file(),ref.kind(),owner,ref.purpose());
   class Scope implements FileRegistry.ReferenceContext {boolean tasks;}
   var finishes=new java.util.concurrent.atomic.AtomicInteger();var fail=new java.util.concurrent.atomic.AtomicBoolean();
   CompleteReadPolicy policy=new CompleteReadPolicy(){
    public Object prelock(FileRegistry.LockedMeeting g,String m,FileRegistry.Reference r,String id){return new Scope();}
    public Object prelock(FileRegistry.LockedMeeting g,String m,FileRegistry.Reference r,String id,FileRegistry.ReferenceContext c){return c;}
    public ReadPreparation prepareRead(Actor a,String m,FileRegistry.ReadRequest r){return new ReadPreparation(){};}
    public void authorizeRead(FileRegistry.LockedMeeting g,String m,FileRegistry.ReadRequest r){}
    public Object prelockCurrent(FileRegistry.LockedMeeting g,String m,FileRegistry.Reference r,String id,ReadPreparation p){return new Scope();}
    public ReadPlan prelockRead(FileRegistry.LockedMeeting g,String m,FileRegistry.ReadRequest r,ReadPreparation p){return new ReadPlan(new Scope(),List.of(new ReadMember(ref,a.file())),a.file());}
    public List<ReadMember> readMembers(Object d,FileRegistry.Reference r,String id){return List.of(new ReadMember(ref,a.file()));}
    public void validate(Object d,String m,FileRegistry.Reference r,FileRegistry.FileRecord file,String p){assertEquals(a.file(),file.fileId());}
    public void prelockReadTasks(List<Object> scopes){assertEquals(1,scopes.size());((Scope)scopes.getFirst()).tasks=true;}
    public void finishRead(Object d,List<FileRegistry.CompletedSource> actual){assertTrue(((Scope)d).tasks);assertEquals(1,actual.size());finishes.incrementAndGet();if(fail.get())throw new IllegalStateException("finalizer refused");}
   };
   var registry=f.registry(Map.of(ref.kind(),policy));FileRegistry.ReadRequest request=new FileRegistry.CurrentReadRequest(ref.kind(),a.file());
   try(var download=assertDoesNotThrow(()->registry.read(f.actor,f.meeting,request,null))){assertEquals("readable",new String(download.body().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
   assertEquals(1,finishes.get());assertEquals(1,f.reads.get());
   try(var download=registry.download(f.actor,a.file(),null)){assertEquals(8,download.length());}
   assertEquals(2,finishes.get());assertEquals(2,f.reads.get());
   fail.set(true);assertThrows(IllegalStateException.class,()->registry.read(f.actor,f.meeting,request,null));assertEquals(2,f.reads.get());
   assertThrows(IllegalStateException.class,()->registry.download(f.actor,a.file(),null));assertEquals(2,f.reads.get());
   FileReferencePolicy legacy=new FileReferencePolicy(){public Object prelock(FileRegistry.LockedMeeting g,String m,FileRegistry.Reference r,String id){return r;}public void validate(Object d,String m,FileRegistry.Reference r,FileRegistry.FileRecord file,String p){}};
   assertThrows(ama.contract.FileContracts.Unavailable.class,()->f.registry(Map.of(ref.kind(),legacy)).read(f.actor,f.meeting,request,null));
  }
 }
 @Test void firstBindSealsActualNewRowsWithoutTranscriptOrConfirmation() throws Exception {
  try(var f=new Task05AuthorityFixture()) {
   var a=f.upload("first real input");
   var c=assertDoesNotThrow(()->ama.transcript.JdbcTranscriptAuthority.create(f.source,f.tx,f.access,f.purposes,f.catalog,Map.of(),ama.transcript.ArchiveProcessingFixture.B,new ama.contract.MinutesContracts.CanonicalBudget(16777216,32,1000000,2097152,250000)));
   var draft=c.intake().readDraft(f.actor,f.meeting,List.of(a.file()),()->false);
   assertEquals(0,draft.revision());assertEquals(a.upload(),draft.orderedSources().getFirst().uploadId());
   assertEquals(0,f.j.queryForObject("SELECT COUNT(*) FROM transcript_revision WHERE meeting_id=?",Integer.class,f.meeting));
   var prepared=f.prepare(c,List.of(a));String batch=prepared.batchId(),input=prepared.inputId();
   f.tx.required(()->{
    var gate=c.registry().lock(f.actor,f.meeting,MeetingAccess.Action.EDIT);
    var reservation=c.authority().reserveIntake(gate,prepared);
    var proof=c.registry().prelock(gate,new FileRegistry.Reference("INPUT_BATCH_FILE",batch,"TRANSCRIPT_INPUT"),a.file(),reservation);
    var sources=c.registry().prelockSources(gate,List.of(a.file()),false).orElseThrow();
    var held=c.authority().insertAndReadNew(reservation,sources);
    var facts=c.authority().detached(held);assertEquals(3,facts.rows().size());assertTrue(facts.taskFacts().isEmpty());
    assertThrows(RuntimeException.class,()->c.authority().requireHeld(held,gate,prepared.spec()));
    c.registry().bindAll(sources,List.of(new FileRegistry.Binding(proof,draft.orderedSources().getFirst().file().locationRevision(),a.upload())));
    c.authority().requireHeld(held,gate,prepared.spec());return null;
   });
   assertEquals(1,f.j.queryForObject("SELECT COUNT(*) FROM input_batch_fingerprint_profile WHERE batch_id=?",Integer.class,batch));
   assertEquals(1,f.j.queryForObject("SELECT COUNT(*) FROM transcription_input WHERE id=? AND batch_id=?",Integer.class,input,batch));
   assertEquals(0,f.j.queryForObject("SELECT COUNT(*) FROM transcript_revision WHERE meeting_id=?",Integer.class,f.meeting));
   assertEquals(0,f.j.queryForObject("SELECT COUNT(*) FROM transcript_confirmation WHERE meeting_id=?",Integer.class,f.meeting));
   assertEquals(0,f.j.queryForObject("SELECT COUNT(*) FROM durable_task WHERE input_id=?",Integer.class,input));
  }
 }
 @Test void actualSuccessfulRawHistoryIsDistinctFromCurrentWorkingAndLiveWorker() throws Exception {
  try(var f=new Task05AuthorityFixture()) {
   var a=f.upload("original history");var c=f.components();var p=f.prepare(c,List.of(a));f.bind(c,p,List.of(a));
   assertThrows(RuntimeException.class,()->c.registry().download(f.actor,a.file(),null),"pending input has no generic byte authority");
   String task=f.task(p,"SUCCEEDED"),raw=f.revision(p,task,"RAW",null,1),working=f.revision(p,null,"WORKING",raw,2);
   f.j.update("UPDATE durable_task SET output_kind='TRANSCRIPT_REVISION',output_id=? WHERE id=?",raw,task);
   f.j.update("UPDATE task_attempt SET ended_at=UTC_TIMESTAMP(6),outcome='SUCCEEDED' WHERE task_id=?",task);
   f.current(working,9);
   try(var download=assertDoesNotThrow(()->c.sources().history(f.actor,f.meeting,raw,a.file(),null))){assertEquals("original history",new String(download.body().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
   try(var download=c.registry().download(f.actor,a.file(),null)){assertEquals(16,download.length());}
   var lease=new ama.contract.TaskContracts.TaskLease(task,1,"TASK03_WORKER",java.time.Instant.now().plusSeconds(3600),p.spec());
   int opened=f.reads.get();assertThrows(RuntimeException.class,()->c.sources().open(lease,p.inputId(),a.file()));assertEquals(opened,f.reads.get());
   f.j.update("UPDATE durable_task SET output_id=? WHERE id=?",working,task);assertThrows(RuntimeException.class,()->c.registry().download(f.actor,a.file(),null));assertEquals(opened,f.reads.get());
   f.j.update("UPDATE durable_task SET output_id=? WHERE id=?",raw,task);f.current(raw,9);assertThrows(RuntimeException.class,()->c.registry().download(f.actor,a.file(),null));
  }
 }
 @Test void workerUsesActualRenewedExpiryAndMatchingUnfinishedAttempt() throws Exception {
  try(var f=new Task05AuthorityFixture()) {
   var a=f.upload("worker source");var c=f.components();var p=f.prepare(c,List.of(a));f.bind(c,p,List.of(a));String task=f.task(p,"RUNNING");f.current(null,1);
   var oldLease=new ama.contract.TaskContracts.TaskLease(task,1,"TASK03_WORKER",java.time.Instant.now().minusSeconds(60),p.spec());
   try(var download=assertDoesNotThrow(()->c.sources().open(oldLease,p.inputId(),a.file()))){assertEquals(13,download.length());}
   int opened=f.reads.get();f.j.update("UPDATE durable_task SET expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",task);
   var future=new ama.contract.TaskContracts.TaskLease(task,1,"TASK03_WORKER",java.time.Instant.now().plusSeconds(7200),p.spec());
   assertThrows(RuntimeException.class,()->c.sources().open(future,p.inputId(),a.file()));assertEquals(opened,f.reads.get());
   f.j.update("UPDATE durable_task SET expires_at=UTC_TIMESTAMP(6)+INTERVAL 1 HOUR WHERE id=?",task);f.j.update("UPDATE task_attempt SET ended_at=UTC_TIMESTAMP(6) WHERE task_id=?",task);
   assertThrows(RuntimeException.class,()->c.sources().open(oldLease,p.inputId(),a.file()));assertEquals(opened,f.reads.get());
  }
 }
 @Test void lateReservationAndForeignSourceCannotMintHeldFacts() throws Exception {
  try(var f=new Task05AuthorityFixture()) {
   var a=f.upload("private receipt");var c=f.components();var p=f.prepare(c,List.of(a));
   f.tx.required(()->{var gate=c.registry().lock(f.actor,f.meeting,MeetingAccess.Action.EDIT);c.registry().prelockSources(gate,List.of(a.file()),false).orElseThrow();assertThrows(IllegalStateException.class,()->c.authority().reserveIntake(gate,p));return null;});
   assertThrows(org.springframework.transaction.UnexpectedRollbackException.class,()->f.tx.required(()->{
    var gate=c.registry().lock(f.actor,f.meeting,MeetingAccess.Action.EDIT);var r=c.authority().reserveIntake(gate,p);
    assertThrows(IllegalArgumentException.class,()->c.authority().insertAndReadNew(r,new FileRegistry.LockedSources(){}));return null;
   }));
   assertEquals(0,f.j.queryForObject("SELECT COUNT(*) FROM input_batch WHERE id=?",Integer.class,p.batchId()));
   assertEquals(0,f.j.queryForObject("SELECT COUNT(*) FROM transcription_input WHERE id=?",Integer.class,p.inputId()));
  }
 }
 @Test void failedLaterBindingAndCommitRollBackCompleteNewGraphAndReferences() throws Exception {
  try(var f=new Task05AuthorityFixture()) {
   var a=f.upload("rollback first");var b=f.upload("rollback second");var c=f.components();var p=f.prepare(c,List.of(a,b));
   List<Map<String,Object>> before=f.j.queryForList("SELECT id,reference_epoch FROM file_object WHERE id IN (?,?) ORDER BY id",a.file(),b.file());
   assertThrows(org.springframework.transaction.UnexpectedRollbackException.class,()->f.tx.required(()->{
    var gate=c.registry().lock(f.actor,f.meeting,MeetingAccess.Action.EDIT);var r=c.authority().reserveIntake(gate,p);
    var ref=new FileRegistry.Reference("INPUT_BATCH_FILE",p.batchId(),"TRANSCRIPT_INPUT");
    var pa=c.registry().prelock(gate,ref,a.file(),r);var pb=c.registry().prelock(gate,ref,b.file(),r);
    var receipt=c.registry().prelockSources(gate,List.of(a.file(),b.file()),false).orElseThrow();c.authority().insertAndReadNew(r,receipt);
    assertThrows(RuntimeException.class,()->c.registry().bindAll(receipt,List.of(new FileRegistry.Binding(pa,0,a.upload()),new FileRegistry.Binding(pb,999,b.upload()))));return null;
   }));
   for(String table:List.of("input_batch","transcription_input"))assertEquals(0,f.j.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE id=?",Integer.class,table.equals("input_batch")?p.batchId():p.inputId()));
   assertEquals(0,f.j.queryForObject("SELECT COUNT(*) FROM input_batch_fingerprint_profile WHERE batch_id=?",Integer.class,p.batchId()));
   assertEquals(0,f.j.queryForObject("SELECT COUNT(*) FROM input_batch_file WHERE batch_id=?",Integer.class,p.batchId()));
   assertEquals(before,f.j.queryForList("SELECT id,reference_epoch FROM file_object WHERE id IN (?,?) ORDER BY id",a.file(),b.file()));
   assertEquals(2,f.j.queryForObject("SELECT COUNT(*) FROM file_reference WHERE file_id IN (?,?) AND owner_kind='UPLOAD_SESSION'",Integer.class,a.file(),b.file()));
   f.source.rollbackOn.set("INSERT INTO input_batch_fingerprint_profile");assertThrows(RuntimeException.class,()->f.bind(c,p,List.of(a,b)));
   assertEquals(0,f.j.queryForObject("SELECT COUNT(*) FROM input_batch WHERE id=?",Integer.class,p.batchId()));
   assertEquals(before,f.j.queryForList("SELECT id,reference_epoch FROM file_object WHERE id IN (?,?) ORDER BY id",a.file(),b.file()));
  }
 }
 @Test void completeAssemblyRejectsLegacyTranscriptOverrideAndMissingBudget() throws Exception {
  try(var f=new Task05AuthorityFixture()) {
   FileReferencePolicy legacy=new FileReferencePolicy(){public Object prelock(FileRegistry.LockedMeeting g,String m,FileRegistry.Reference r,String id){return r;}public void validate(Object d,String m,FileRegistry.Reference r,FileRegistry.FileRecord file,String p){}};
   var canonical=new ama.contract.MinutesContracts.CanonicalBudget(100000,16,10000,10000,1000);
   assertThrows(IllegalArgumentException.class,()->ama.transcript.JdbcTranscriptAuthority.create(f.source,f.tx,f.access,f.purposes,f.catalog,Map.of("INPUT_BATCH_FILE",legacy),ama.transcript.ArchiveProcessingFixture.B,canonical));
   assertThrows(NullPointerException.class,()->ama.transcript.JdbcTranscriptAuthority.create(f.source,f.tx,f.access,f.purposes,f.catalog,Map.of(),null,canonical));
  }
 }
 private static class RejectingComplete implements CompleteReadPolicy {
  RuntimeException preparationFailure=new IllegalStateException("private graph detail");
  public ReadPreparation prepareRead(Actor actor,String meeting,FileRegistry.ReadRequest request){throw preparationFailure;}
  public void authorizeRead(FileRegistry.LockedMeeting g,String m,FileRegistry.ReadRequest r){}
  public Object prelock(FileRegistry.LockedMeeting g,String m,FileRegistry.Reference r,String f){throw new AssertionError("failed preparation acquired domain");}
  public Object prelock(FileRegistry.LockedMeeting g,String m,FileRegistry.Reference r,String f,FileRegistry.ReferenceContext c){throw new AssertionError("failed preparation acquired domain");}
  public Object prelockCurrent(FileRegistry.LockedMeeting g,String m,FileRegistry.Reference r,String f,ReadPreparation p){throw new AssertionError("failed preparation acquired domain");}
  public ReadPlan prelockRead(FileRegistry.LockedMeeting g,String m,FileRegistry.ReadRequest r,ReadPreparation p){throw new AssertionError("failed preparation acquired domain");}
  public List<ReadMember> readMembers(Object d,FileRegistry.Reference r,String f){throw new AssertionError();}
  public void validate(Object d,String m,FileRegistry.Reference r,FileRegistry.FileRecord f,String p){throw new AssertionError();}
  public void prelockReadTasks(List<Object> scopes){throw new AssertionError();}
  public void finishRead(Object d,List<FileRegistry.CompletedSource> sources){throw new AssertionError();}
 }
 private static FileReferencePolicy legacy(){return new FileReferencePolicy(){public Object prelock(FileRegistry.LockedMeeting g,String m,FileRegistry.Reference r,String f){return r;}public void validate(Object d,String m,FileRegistry.Reference r,FileRegistry.FileRecord f,String p){assertEquals(d,r);}};}
 @Test void recognizedMissingLegacyAndFailedProvidersAlwaysAuthorizeBeforeSafeFailure() throws Exception {
  try(var f=new Task05AuthorityFixture()){
   var member=f.member();var outsider=f.outsider();String file=UUID.randomUUID().toString(),input=UUID.randomUUID().toString();
   var spec=new ama.contract.TaskContracts.TaskSpec("TRANSCRIPT_IMPORT_V1","TRANSCRIPT_IMPORT",new ama.contract.TaskContracts.SubjectRef("MEETING",f.meeting),"a".repeat(64),new ama.contract.TaskContracts.TypedRef("TRANSCRIPTION_INPUT",input),"b".repeat(64),1,0,0,3);
   var lease=new ama.contract.TaskContracts.TaskLease(UUID.randomUUID().toString(),1,"TASK03_WORKER",java.time.Instant.now(),spec);
   var requests=List.<FileRegistry.ReadRequest>of(new FileRegistry.CurrentReadRequest("INPUT_BATCH_FILE",file),new ama.transcript.ProcessingSourceReader.HistoryRequest(UUID.randomUUID().toString(),file),new ama.transcript.ProcessingSourceReader.WorkerRequest(lease,input,file));
   var registries=List.of(new JdbcFileRegistry(f.source,f.tx,f.access,f.purposes,f.catalog,Map.of()),new JdbcFileRegistry(f.source,f.tx,f.access,f.purposes,f.catalog,Map.of("INPUT_BATCH_FILE",legacy())),f.registry(Map.of("INPUT_BATCH_FILE",new RejectingComplete())));
   for(var registry:registries)for(var request:requests){
    for(var actor:List.of(outsider,member,f.actor)){
     f.source.statements.clear();
     if(actor==outsider||actor==member&&!(request instanceof FileRegistry.CurrentReadRequest))assertThrows(MeetingAccess.AccessDeniedException.class,()->registry.read(actor,f.meeting,request,null));
     else assertThrows(ama.contract.FileContracts.Unavailable.class,()->registry.read(actor,f.meeting,request,null));
     assertEquals(1,f.source.statements.stream().filter(q->q.startsWith("SELECT enterprise_id,status")).count());assertNoCompleteLocks(f);assertEquals(0,f.reads.get());
    }
    f.j.update("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id=?",member.enterpriseId());f.source.statements.clear();
    assertThrows(MeetingAccess.AccessDeniedException.class,()->registry.read(member,f.meeting,request,null));assertNoCompleteLocks(f);
    f.j.update("UPDATE enterprise_identity SET status='ACTIVE' WHERE enterprise_id=?",member.enterpriseId());
   }
   f.source.statements.clear();assertThrows(IllegalArgumentException.class,()->registries.getFirst().read(f.actor,f.meeting,()->"INPUT_BATCH_FILE",null));assertTrue(f.source.statements.isEmpty());
  }
 }
 private static void assertNoCompleteLocks(Task05AuthorityFixture f){assertFalse(f.source.statements.stream().anyMatch(q->q.contains("FOR UPDATE")&&(q.contains("FROM transcript_revision")||q.contains("FROM transcription_input")||q.contains("FROM input_batch")||q.contains("FROM upload_session")||q.contains("FROM file_object")||q.contains("FROM durable_task")||q.contains("FROM task_attempt"))));}
 @Test void onlyGenuineCurrentIneligibilityCanUseIndependentLegacyAfterAuthorization() throws Exception {
  try(var f=new Task05AuthorityFixture()){
   var uploaded=f.upload("legacy fallback");String owner=UUID.randomUUID().toString();
   for(String kind:List.of("TASK03_COMPLETE","TASK03_LEGACY"))f.j.update("INSERT INTO file_reference VALUES (?,?,?,'TRANSCRIPT_INPUT')",uploaded.file(),kind,owner);
   var complete=new RejectingComplete();complete.preparationFailure=new ama.contract.FileContracts.NotFound();
   var registry=f.registry(Map.of("TASK03_COMPLETE",complete,"TASK03_LEGACY",legacy()));
   var outsider=f.outsider();assertThrows(MeetingAccess.AccessDeniedException.class,()->registry.download(outsider,uploaded.file(),null));assertEquals(0,f.reads.get());
   try(var d=registry.download(f.actor,uploaded.file(),null)){assertEquals(15,d.length());}assertEquals(1,f.reads.get());
   complete.preparationFailure=new IllegalStateException("private graph corruption");f.source.statements.clear();
   assertThrows(ama.contract.FileContracts.Unavailable.class,()->registry.download(f.actor,uploaded.file(),null));assertNoCompleteLocks(f);assertEquals(1,f.reads.get());
   f.j.update("INSERT INTO file_reference VALUES (?,'TASK03_OTHER',?,'TRANSCRIPT_INPUT')",uploaded.file(),owner);
   var ambiguous=f.registry(Map.of("TASK03_COMPLETE",complete,"TASK03_OTHER",new RejectingComplete(),"TASK03_LEGACY",legacy()));
   assertThrows(MeetingAccess.AccessDeniedException.class,()->ambiguous.download(outsider,uploaded.file(),null));
   f.source.statements.clear();assertThrows(ama.contract.FileContracts.Unavailable.class,()->ambiguous.download(f.actor,uploaded.file(),null));assertNoCompleteLocks(f);assertEquals(1,f.reads.get());
   var old=new JdbcFileRegistry(f.source,f.tx,f.access,f.purposes,f.catalog,Map.of("TASK03_LEGACY",legacy()));try(var d=old.download(f.actor,uploaded.file(),null)){assertEquals(15,d.length());}
   complete.preparationFailure=ama.contract.MinutesContracts.fail(ama.contract.MinutesContracts.FailureCode.CANCELLED);f.source.statements.clear();
   assertThrows(ama.contract.MinutesContracts.Failure.class,()->registry.download(f.actor,uploaded.file(),null));assertFalse(f.source.statements.stream().anyMatch(q->q.contains("enterprise_identity")));assertEquals(2,f.reads.get());
  }
 }
 @Test void danglingCurrentPointerIsFatalAfterAuthorizationEvenWithValidLegacyReference() throws Exception {
  try(var f=new Task05AuthorityFixture()){
   var uploaded=f.upload("dangling");var canonical=new ama.contract.MinutesContracts.CanonicalBudget(16777216,32,1000000,2097152,250000);
   var c=ama.transcript.JdbcTranscriptAuthority.create(f.source,f.tx,f.access,f.purposes,f.catalog,Map.of("TASK03_LEGACY",legacy()),ama.transcript.ArchiveProcessingFixture.B,canonical);
   var p=f.prepare(c,List.of(uploaded));f.bind(c,p,List.of(uploaded));
   f.j.update("INSERT INTO file_reference VALUES (?,'TASK03_LEGACY',?,'TRANSCRIPT_INPUT')",uploaded.file(),UUID.randomUUID().toString());
   f.current(UUID.randomUUID().toString(),3);var outsider=f.outsider();
   assertThrows(MeetingAccess.AccessDeniedException.class,()->c.registry().download(outsider,uploaded.file(),null));
   f.source.statements.clear();assertThrows(ama.contract.FileContracts.Unavailable.class,()->c.registry().download(f.actor,uploaded.file(),null));
   assertNoCompleteLocks(f);assertEquals(0,f.reads.get());assertEquals(1,f.source.statements.stream().filter(q->q.startsWith("SELECT enterprise_id,status")).count());
  }
 }
 @Test void realCurrentMemberHistoryOwnerAndMalformedProducerProfilesRemainDistinct() throws Exception {
  try(var f=new Task05AuthorityFixture()){
   var uploaded=f.upload("producer metadata");var c=f.components();var p=f.prepare(c,List.of(uploaded));f.bind(c,p,List.of(uploaded));String task=f.task(p,"SUCCEEDED"),raw=f.revision(p,task,"RAW",null,1),working=f.revision(p,null,"WORKING",raw,2);
   f.j.update("UPDATE durable_task SET output_kind='TRANSCRIPT_REVISION',output_id=? WHERE id=?",raw,task);f.j.update("UPDATE task_attempt SET ended_at=UTC_TIMESTAMP(6),outcome='SUCCEEDED' WHERE task_id=?",task);f.current(working,7);
   var member=f.member();try(var d=c.registry().download(member,uploaded.file(),null)){assertEquals(17,d.length());}
   assertThrows(MeetingAccess.AccessDeniedException.class,()->c.sources().history(member,f.meeting,raw,uploaded.file(),null));
   assertThrows(org.springframework.dao.DataAccessException.class,()->f.j.update("UPDATE input_batch_fingerprint_profile SET profile='UNKNOWN' WHERE batch_id=?",p.batchId()));f.j.update("DELETE FROM input_batch_fingerprint_profile WHERE batch_id=?",p.batchId());int reads=f.reads.get();
   assertThrows(MeetingAccess.AccessDeniedException.class,()->c.sources().history(member,f.meeting,raw,uploaded.file(),null));assertThrows(ama.contract.FileContracts.Unavailable.class,()->c.registry().download(f.actor,uploaded.file(),null));assertEquals(reads,f.reads.get());
   f.j.update("INSERT INTO input_batch_fingerprint_profile(batch_id,profile) VALUES (?,?)",p.batchId(),ama.transcript.BatchFingerprint.PROFILE);
   for(String column:List.of("producer_key","input_fingerprint")){
    String original=f.j.queryForObject("SELECT "+column+" FROM durable_task WHERE id=?",String.class,task);f.j.update("UPDATE durable_task SET "+column+"=? WHERE id=?","0".repeat(64),task);
    assertThrows(ama.contract.FileContracts.Unavailable.class,()->c.sources().history(f.actor,f.meeting,raw,uploaded.file(),null));assertEquals(reads,f.reads.get());f.j.update("UPDATE durable_task SET "+column+"=? WHERE id=?",original,task);
   }
   f.j.update("UPDATE transcript_revision SET parent_id=? WHERE id=?",working,working);assertThrows(ama.contract.FileContracts.Unavailable.class,()->c.registry().download(member,uploaded.file(),null));assertEquals(reads,f.reads.get());
   f.j.update("UPDATE transcript_revision SET parent_id=? WHERE id=?",raw,working);f.j.update("UPDATE meeting SET ever_published=1,stage='PUBLISHED' WHERE id=?",f.meeting);
   try(var d=c.sources().history(f.actor,f.meeting,raw,uploaded.file(),null)){assertEquals(17,d.length());}
  }
 }
 @Test void completeReadLocksAllParentsThenSessionsFilesAndTasksAndCommitBeforeOpen() throws Exception {
  try(var f=new Task05AuthorityFixture()){
   var uploaded=new ArrayList<Task05AuthorityFixture.Uploaded>();for(String text:List.of("first ordered","second ordered","third ordered"))uploaded.add(f.upload(text));
   uploaded.sort(Comparator.comparing(Task05AuthorityFixture.Uploaded::file).reversed());var c=f.components();var p=f.prepare(c,uploaded);f.bind(c,p,uploaded);String task=f.task(p,"SUCCEEDED"),raw=f.revision(p,task,"RAW",null,1),working=f.revision(p,null,"WORKING",raw,2);
   f.j.update("UPDATE durable_task SET output_kind='TRANSCRIPT_REVISION',output_id=? WHERE id=?",raw,task);f.j.update("UPDATE task_attempt SET ended_at=UTC_TIMESTAMP(6),outcome='SUCCEEDED' WHERE task_id=?",task);f.current(working,9);f.trace.locks.clear();f.source.statements.clear();
   try(var d=c.registry().download(f.actor,uploaded.getFirst().file(),null)){assertEquals(3,f.j.queryForObject("SELECT COUNT(*) FROM input_batch_file WHERE batch_id=?",Integer.class,p.batchId()));}
   var events=List.copyOf(f.trace.locks);assertEquals(List.of(raw,working).stream().sorted().toList(),firstIds(events,"transcript_revision"));
   assertEquals(uploaded.stream().map(Task05AuthorityFixture.Uploaded::upload).sorted().toList(),firstIds(events,"upload_session"));
   assertEquals(uploaded.stream().map(Task05AuthorityFixture.Uploaded::file).sorted().toList(),firstIds(events,"file_object"));
   assertEquals(List.of(task),firstIds(events,"durable_task"));
   int parent=firstLock(events,"transcript_revision"),input=firstLock(events,"transcription_input"),batch=firstLock(events,"input_batch WHERE"),profile=firstLock(events,"input_batch_fingerprint_profile"),members=firstLock(events,"input_batch_file"),sessions=firstLock(events,"upload_session"),files=firstLock(events,"file_object"),tasks=firstLock(events,"durable_task");
   assertTrue(parent<input&&input<batch&&batch<profile&&profile<members&&members<sessions&&sessions<files&&files<tasks);
   assertFalse(events.stream().anyMatch(e->e.sql().contains("FROM task_attempt")));assertFalse(f.source.statements.stream().anyMatch(q->q.contains("config_manifest")||q.contains("transcript_segment")||q.contains("provider_upload_token")||q.contains("provider_version_id")));
   assertEquals(1,f.source.statements.stream().filter(q->q.startsWith("SELECT enterprise_id,status")).count());
   java.nio.file.Path trace=Task05RegressionResources.evidencePath().resolve(f.meeting+"-lock-order.txt");java.nio.file.Files.write(trace,events.stream().map(e->e.sql()+" | "+e.parameters()).toList());
   int opened=f.reads.get();f.trace.refuseCommit.set(true);assertThrows(RuntimeException.class,()->c.registry().download(f.actor,uploaded.getFirst().file(),null));assertEquals(opened,f.reads.get());
   f.trace.afterCommit.set(()->Thread.currentThread().interrupt());try{assertEquals(ama.contract.MinutesContracts.FailureCode.CANCELLED,assertThrows(ama.contract.MinutesContracts.Failure.class,()->c.registry().download(f.actor,uploaded.getFirst().file(),null)).code());assertEquals(opened,f.reads.get());}finally{Thread.interrupted();}
  }
 }
 private static List<String> firstIds(List<Task03TraceSource.Event> events,String table){var ids=new LinkedHashSet<String>();for(var event:events)if(!event.sql().startsWith("SELECT (")&&event.sql().contains("FROM "+table+" WHERE id=?")&&event.parameters().getFirst() instanceof String id)ids.add(id);return List.copyOf(ids);}
 private static int firstLock(List<Task03TraceSource.Event> events,String table){for(int i=0;i<events.size();i++)if(events.get(i).sql().contains("FROM "+table))return i;return -1;}
 @Test void preGateGraphAndTaskChangesNeverTriggerLateDiscoveryOrOpen() throws Exception {
  try(var f=new Task05AuthorityFixture()){
   var uploaded=f.upload("race guarded");var c=f.components();var p=f.prepare(c,List.of(uploaded));f.bind(c,p,List.of(uploaded));String task=f.task(p,"SUCCEEDED"),raw=f.revision(p,task,"RAW",null,1),working=f.revision(p,null,"WORKING",raw,2);
   f.j.update("UPDATE durable_task SET output_kind='TRANSCRIPT_REVISION',output_id=? WHERE id=?",raw,task);f.j.update("UPDATE task_attempt SET ended_at=UTC_TIMESTAMP(6),outcome='SUCCEEDED' WHERE task_id=?",task);f.current(working,2);
   var outside=new JdbcTemplate(f.trace.actual);f.trace.beforeIdentity.set(()->outside.update("UPDATE transcript_revision SET parent_id=? WHERE id=?",working,working));f.trace.locks.clear();
   assertThrows(RuntimeException.class,()->c.registry().download(f.actor,uploaded.file(),null));assertEquals(0,f.reads.get());assertEquals(List.of(raw,working).stream().sorted().toList(),firstIds(f.trace.locks,"transcript_revision"));assertEquals(-1,firstLock(f.trace.locks,"upload_session"));
   outside.update("UPDATE transcript_revision SET parent_id=? WHERE id=?",raw,working);
   f.trace.beforeTask.set(()->outside.update("UPDATE durable_task SET priority=1 WHERE id=?",task));f.trace.locks.clear();assertThrows(RuntimeException.class,()->c.registry().download(f.actor,uploaded.file(),null));assertEquals(0,f.reads.get());assertTrue(firstLock(f.trace.locks,"durable_task")>firstLock(f.trace.locks,"file_object"));
   outside.update("UPDATE durable_task SET priority=0 WHERE id=?",task);try(var d=c.registry().download(f.actor,uploaded.file(),null)){assertEquals(12,d.length());}
  }
 }
 @Test void actualPreparedReadCannotCrossIssuerSelectorGateOrHolder() throws Exception {
  try(var f=new Task05AuthorityFixture()){
   var uploaded=f.upload("private preparation");var c=f.components();var p=f.prepare(c,List.of(uploaded));f.bind(c,p,List.of(uploaded));String task=f.task(p,"SUCCEEDED"),raw=f.revision(p,task,"RAW",null,1),working=f.revision(p,null,"WORKING",raw,2);
   f.j.update("UPDATE durable_task SET output_kind='TRANSCRIPT_REVISION',output_id=? WHERE id=?",raw,task);f.j.update("UPDATE task_attempt SET ended_at=UTC_TIMESTAMP(6),outcome='SUCCEEDED' WHERE task_id=?",task);f.current(working,2);
   var field=JdbcFileRegistry.class.getDeclaredField("policies");field.setAccessible(true);var policy=(CompleteReadPolicy)((Map<?,?>)field.get(c.registry())).get("INPUT_BATCH_FILE");
   var request=new FileRegistry.CurrentReadRequest("INPUT_BATCH_FILE",uploaded.file());var prepared=policy.prepareRead(f.actor,f.meeting,request);Object[] scope=new Object[1];
   f.tx.required(()->{
    var gate=c.registry().lock(f.actor,f.meeting,MeetingAccess.Action.READ);
    assertThrows(IllegalArgumentException.class,()->policy.prelockRead(gate,f.meeting,request,new CompleteReadPolicy.ReadPreparation(){}));
    assertThrows(IllegalArgumentException.class,()->policy.prelockRead(gate,f.meeting,new ama.transcript.ProcessingSourceReader.HistoryRequest(raw,uploaded.file()),prepared));
    scope[0]=policy.prelockRead(gate,f.meeting,request,prepared).context();assertThrows(RuntimeException.class,()->policy.prelockReadTasks(List.of(scope[0])));return null;
   });
   assertThrows(IllegalStateException.class,()->policy.readMembers(scope[0],new FileRegistry.Reference("INPUT_BATCH_FILE",p.batchId(),"TRANSCRIPT_INPUT"),uploaded.file()));
   f.tx.required(()->{var gate=c.registry().lock(f.actor,f.meeting,MeetingAccess.Action.READ);assertThrows(RuntimeException.class,()->policy.prelockRead(gate,f.meeting,request,prepared));return null;});
   var foreign=f.components();var second=(CompleteReadPolicy)((Map<?,?>)field.get(foreign.registry())).get("INPUT_BATCH_FILE");var fresh=policy.prepareRead(f.actor,f.meeting,request);
   f.tx.required(()->{var gate=foreign.registry().lock(f.actor,f.meeting,MeetingAccess.Action.READ);assertThrows(IllegalArgumentException.class,()->second.prelockRead(gate,f.meeting,request,fresh));assertThrows(IllegalArgumentException.class,()->policy.prelockRead(gate,f.meeting,request,fresh));return null;});assertEquals(0,f.reads.get());
  }
 }
 @Test void configuredGraphSourceAndDisplayBoundsAreEnforcedWithoutClipping() throws Exception {
  try(var f=new Task05AuthorityFixture()){
   var a=f.upload("bounded first");var b=f.upload("bounded second");var c=f.components();var p=f.prepare(c,List.of(a,b));f.bind(c,p,List.of(a,b));String task=f.task(p,"SUCCEEDED"),raw=f.revision(p,task,"RAW",null,1),working=f.revision(p,null,"WORKING",raw,2);
   f.j.update("UPDATE durable_task SET output_kind='TRANSCRIPT_REVISION',output_id=? WHERE id=?",raw,task);f.j.update("UPDATE task_attempt SET ended_at=UTC_TIMESTAMP(6),outcome='SUCCEEDED' WHERE task_id=?",task);f.current(working,2);
   var B=ama.transcript.ArchiveProcessingFixture.B;var canonical=new ama.contract.MinutesContracts.CanonicalBudget(16777216,32,1000000,2097152,250000);
   for(var limited:List.of(readBudget(1,B.maxFactRows(),B.maxFactEdges(),B.maxStringUtf16(),B.maxAggregateScalars()),readBudget(B.maxGraphDepth(),2,B.maxFactEdges(),B.maxStringUtf16(),B.maxAggregateScalars()),readBudget(B.maxGraphDepth(),B.maxFactRows(),1,B.maxStringUtf16(),B.maxAggregateScalars()),readBudget(B.maxGraphDepth(),B.maxFactRows(),B.maxFactEdges(),32,B.maxAggregateScalars()),readBudget(B.maxGraphDepth(),B.maxFactRows(),B.maxFactEdges(),B.maxStringUtf16(),64))){
    var limitedComponents=ama.transcript.JdbcTranscriptAuthority.create(f.source,f.tx,f.access,f.purposes,f.catalog,Map.of(),limited,canonical);f.source.statements.clear();
    assertThrows(ama.contract.FileContracts.Unavailable.class,()->limitedComponents.registry().download(f.actor,a.file(),null));assertNoCompleteLocks(f);assertEquals(0,f.reads.get());
   }
   var sourceLimited=new JdbcFileRegistry(f.source,f.tx,f.access,f.purposes,f.catalog,Map.of(),readBudget(B.maxGraphDepth(),2,B.maxFactEdges(),B.maxStringUtf16(),B.maxAggregateScalars()));
   assertThrows(RuntimeException.class,()->f.tx.required(()->{var gate=sourceLimited.lock(f.actor,f.meeting,MeetingAccess.Action.READ);sourceLimited.prelockSources(gate,List.of(a.file(),b.file()),false);return null;}));assertEquals(0,f.reads.get());
   f.j.update("UPDATE file_object SET display_name=? WHERE id=?","😀".repeat(B.maxStringUtf16()/2+1),a.file());
   f.tx.required(()->{var gate=c.registry().lock(f.actor,f.meeting,MeetingAccess.Action.READ);assertEquals(2,c.registry().sources(gate,c.registry().prelockSources(gate,List.of(a.file(),b.file()),false).orElseThrow(),List.of(a.file(),b.file())).size());return null;});
   assertThrows(RuntimeException.class,()->c.registry().download(f.actor,a.file(),null));assertEquals(0,f.reads.get());
  }
 }
 private static ama.contract.ConfigArchiveContracts.ArchiveReadBudget readBudget(int depth,long rows,long edges,int utf16,long scalars){var b=ama.transcript.ArchiveProcessingFixture.B;return new ama.contract.ConfigArchiveContracts.ArchiveReadBudget(b.maxArtifactBytes(),b.maxTotalBytes(),b.maxArtifacts(),b.maxDependencies(),depth,b.maxTokens(),b.maxJsonDepth(),b.maxNameUtf16(),utf16,b.maxStringUtf8Bytes(),scalars,b.maxManifestBytes(),b.maxBatchFiles(),rows,edges);}
 @Test void duplicateOriginalPurposeRouteAndEpochCannotAuthorizeAnotherCompletedFile() throws Exception {
  try(var f=new Task05AuthorityFixture()){
   var a=f.upload("original one");var b=f.upload("original two");var registry=f.registry(Map.of());
   Runnable acquire=()->f.tx.required(()->{var gate=registry.lock(f.actor,f.meeting,MeetingAccess.Action.READ);registry.prelockSources(gate,List.of(a.file()),false).orElseThrow();return null;});
   f.j.update("UPDATE upload_session SET completed_file_id=? WHERE id=?",a.file(),b.upload());assertThrows(ama.contract.FileContracts.NotFound.class,acquire::run);f.j.update("UPDATE upload_session SET completed_file_id=? WHERE id=?",b.file(),b.upload());
   String key=f.j.queryForObject("SELECT object_key FROM upload_session WHERE id=?",String.class,a.upload());f.j.update("UPDATE upload_session SET object_key='substituted' WHERE id=?",a.upload());assertThrows(RuntimeException.class,acquire::run);f.j.update("UPDATE upload_session SET object_key=? WHERE id=?",key,a.upload());
   f.j.update("UPDATE file_object SET reference_epoch=18446744073709551615 WHERE id=?",a.file());assertThrows(RuntimeException.class,acquire::run);f.j.update("UPDATE file_object SET reference_epoch=0 WHERE id=?",a.file());
   f.j.update("UPDATE upload_session SET purpose='OTHER_INPUT' WHERE id=?",a.upload());var c=f.components();assertThrows(RuntimeException.class,()->c.intake().readDraft(f.actor,f.meeting,List.of(a.file()),()->false));f.j.update("UPDATE upload_session SET purpose='TRANSCRIPT_INPUT' WHERE id=?",a.upload());
   var readonly=new FileBackendCatalog(f.source,List.of(new FileBackendCatalog.Backend(f.storage.binding.id(),f.storage.binding.type(),2,"READ_ONLY",f.storage.storage,f.storage.binding)),f.storage.binding.id());readonly.configure();
   var roRegistry=new JdbcFileRegistry(f.source,f.tx,f.access,f.purposes,readonly,Map.of(),ama.transcript.ArchiveProcessingFixture.B);
   assertDoesNotThrow(()->f.tx.required(()->{var gate=roRegistry.lock(f.actor,f.meeting,MeetingAccess.Action.READ);roRegistry.prelockSources(gate,List.of(a.file()),false).orElseThrow();return null;}));
   var disabled=new FileBackendCatalog(f.source,List.of(new FileBackendCatalog.Backend(f.storage.binding.id(),f.storage.binding.type(),3,"DISABLED",f.storage.storage,f.storage.binding)),f.storage.binding.id());disabled.configure();
   var offRegistry=new JdbcFileRegistry(f.source,f.tx,f.access,f.purposes,disabled,Map.of(),ama.transcript.ArchiveProcessingFixture.B);
   assertThrows(RuntimeException.class,()->f.tx.required(()->{var gate=offRegistry.lock(f.actor,f.meeting,MeetingAccess.Action.READ);offRegistry.prelockSources(gate,List.of(a.file()),false);return null;}));assertEquals(0,f.reads.get());

  }
 }
 @Test void identitySkipLockedStopsBeforeRootAndActualRosterSaveKeepsFullName() throws Exception {
  try(var f=new Task05AuthorityFixture()){
   var acquired=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
   try(var executor=java.util.concurrent.Executors.newSingleThreadExecutor()){
    var blocker=executor.submit(()->new TransactionRunner(f.trace.actual).required(()->{new JdbcTemplate(f.trace.actual).queryForObject("SELECT enterprise_id FROM enterprise_identity WHERE enterprise_id=? FOR UPDATE",String.class,f.owner);acquired.countDown();assertTrue(release.await(5,java.util.concurrent.TimeUnit.SECONDS));return null;}));
    try{
     assertTrue(acquired.await(5,java.util.concurrent.TimeUnit.SECONDS));f.source.statements.clear();
     f.tx.required(()->{assertTrue(f.access.tryLockIdentities(f.actor,List.of(),true).isEmpty());return null;});
     assertFalse(f.source.statements.stream().anyMatch(q->q.contains("FROM meeting")||q.contains("FROM task_subject")));assertTrue(f.source.statements.stream().anyMatch(q->q.contains("SKIP LOCKED")));
    }finally{release.countDown();}blocker.get(5,java.util.concurrent.TimeUnit.SECONDS);
   }
   var member=f.member();String name="完整😀姓名".repeat(20000);f.j.update("UPDATE enterprise_identity SET display_name=? WHERE enterprise_id=?",name,member.enterpriseId());
   var outbox=new ama.task.JdbcOutbox(f.source,new ama.meeting.MeetingEventReferences(f.source).validators());
   var repository=new ama.meeting.JdbcMeetingRepository(f.source,f.tx,f.access,f.fences,outbox,new ama.audit.JdbcAuditAdapter(f.source),id->{throw new ama.contract.MeetingContracts.CategoryUnavailable();});
   var created=repository.create(f.actor,"task03-create-"+UUID.randomUUID(),"task03-name");
   repository.save(f.actor,created.id(),"task03-save-"+UUID.randomUUID(),new ama.contract.MeetingContracts.SaveInfo(0L,"full name",null,null,null,List.of(member.enterpriseId()),List.of()),"task03-name");
   assertEquals(name,f.j.queryForObject("SELECT rm.display_name FROM roster_member rm JOIN roster_revision rr ON rr.id=rm.roster_id WHERE rr.meeting_id=? AND rm.enterprise_id=?",String.class,created.id(),member.enterpriseId()));
   assertEquals(1,f.j.queryForObject("SELECT COUNT(*) FROM meeting_minutes_editor_guard WHERE meeting_id=?",Integer.class,created.id()));
  }
 }
}
