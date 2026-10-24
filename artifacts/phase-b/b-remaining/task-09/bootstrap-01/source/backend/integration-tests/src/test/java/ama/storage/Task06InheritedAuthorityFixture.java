package ama.storage;

import ama.access.MeetingAccess;
import ama.audit.JdbcAuditAdapter;
import ama.contract.FileContracts.*;
import ama.contract.IdentityContracts.*;
import ama.db.TransactionRunner;
import ama.meeting.JdbcMeetingAccess;
import ama.support.MysqlTestDatabase;
import ama.task.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static ama.transcript.ArchiveProcessingFixture.*;
import static org.junit.jupiter.api.Assertions.*;

/** Owns only new UUID DB rows and new native/TLS paths; actual B04 completion, no fabricated receipt. */
final class Task06InheritedAuthorityFixture implements AutoCloseable {
 final Task03TraceSource trace=new Task03TraceSource(MysqlTestDatabase.dataSource());
 final RegistryProbeSource source=new RegistryProbeSource(trace);
 final JdbcTemplate j=new JdbcTemplate(source);
 final TransactionRunner tx=new TransactionRunner(source);
 final JdbcSubjectFences fences=new JdbcSubjectFences(source,Set.of("MEETING"));
 final JdbcMeetingAccess access=new JdbcMeetingAccess(source,tx,fences);
 final String owner="task06-fix-inherited-authority-"+id(),meeting=id();
 final Actor actor=new Actor(owner,Status.ACTIVE,Set.of(Role.USER));
 final Task06InheritedRegistryFixture storage;
 final FileBackendCatalog catalog;
 final java.util.concurrent.atomic.AtomicInteger reads=new java.util.concurrent.atomic.AtomicInteger();
 final FilePurposePolicy purposes=(a,m,p)->{if(!p.equals("TRANSCRIPT_INPUT"))throw new FilePurposePolicy.Unavailable();};
 final JdbcFileUploads uploads;
 final List<String> ownedTasks=new ArrayList<>();
 Task06InheritedAuthorityFixture() throws Exception {
  storage=new Task06InheritedRegistryFixture(Task06InheritedResources.nativePath());
    FileStoragePort observed=(FileStoragePort)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{FileStoragePort.class},(p,m,a)->{
   if(m.getName().equals("read")){if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new AssertionError("storage under transaction");reads.incrementAndGet();}
   try{return m.invoke(storage.storage,a);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
  });
  catalog=new FileBackendCatalog(source,List.of(storage.backend(observed)),storage.binding.id());catalog.configure();
  j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",owner,owner);
  j.update("INSERT INTO task_subject(subject_kind,subject_id,business_version,cancel_epoch,deleted) VALUES ('MEETING',?,0,0,0)",meeting);
  j.update("INSERT INTO meeting(id,owner_id,stage,created_at,updated_at) VALUES (?,?,'ENTRY',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",meeting,owner);
  uploads=new JdbcFileUploads(source,tx,access,purposes,catalog,new JdbcAuditAdapter(source),new JdbcOutbox(source,Map.of()),1024);
 }
 record Uploaded(String file,String upload,String hash) {}
 Uploaded upload(String value) {
  byte[] bytes=value.getBytes(java.nio.charset.StandardCharsets.UTF_8);String digest=hash(value);
  var accepted=uploads.begin(actor,meeting,id(),new Begin("TRANSCRIPT_INPUT","original😀.txt",bytes.length,digest),id());
  uploads.part(actor,accepted.uploadId(),1,new ByteArrayInputStream(bytes),bytes.length,digest,id());
  var completed=uploads.complete(actor,accepted.uploadId(),new Completion(List.of(new ClientPart(1,digest)),digest),id());
  return new Uploaded(completed.fileId(),accepted.uploadId(),digest);
 }
 JdbcFileRegistry registry(Map<String,FileReferencePolicy> policies){return new JdbcFileRegistry(source,tx,access,purposes,catalog,policies,B);}
 ama.transcript.JdbcTranscriptAuthority.Components components(){return ama.transcript.JdbcTranscriptAuthority.create(source,tx,access,purposes,catalog,Map.of(),B,new ama.contract.MinutesContracts.CanonicalBudget(16777216,32,1000000,2097152,250000));}
 ama.transcript.JdbcTranscriptAuthority.PreparedIntake prepare(ama.transcript.JdbcTranscriptAuthority.Components c,List<Uploaded> uploaded)throws Exception {
  var draft=c.intake().readDraft(actor,meeting,uploaded.stream().map(Uploaded::file).toList(),()->false);
   var raw=new java.util.ArrayList<ama.contract.ConfigArchiveContracts.RawArchive>();
   for(String name:List.of("codec","confirmation","parser","processing1")){
    var type=switch(name){case "codec"->ama.contract.ConfigArchiveContracts.ArtifactType.CANONICAL_CODEC;case "confirmation"->ama.contract.ConfigArchiveContracts.ArtifactType.CONFIRMATION_HASH;case "parser"->ama.contract.ConfigArchiveContracts.ArtifactType.PARSER_CONFIG;default->ama.contract.ConfigArchiveContracts.ArtifactType.PROCESSING_CONFIG;};
    String version=switch(name){case "codec"->"ama.canonical-value.v1";case "confirmation"->"ama.transcript-confirmation.v1";case "parser"->"ama.strict-utf8-lines.v1";default->"b06.controlled.import-txt.1";};
    try(var in=getClass().getResourceAsStream("/ama/transcript/restart/"+name+".json")){raw.add(new ama.contract.ConfigArchiveContracts.RawArchive(type,version,in.readAllBytes()));}
   }
   var root=Task06InheritedResources.archivePath();
   var ib=new ama.config.ConfigArchiveIndex.IndexBudget(4194304,4096,67108864,128);
   String pin=assertDoesNotThrow(()->new ama.config.file.ControlledArchiveReleaseWriter().publish(root,null,raw,ib,ama.transcript.ArchiveProcessingFixture.B,()->false),"archive publication");
   var snapshot=assertDoesNotThrow(()->new ama.config.file.ControlledConfigArchiveSource().open(root,pin,ib,ama.transcript.ArchiveProcessingFixture.B,()->false),"archive open");
   var processing=snapshot.index().entries().stream().map(ama.config.ConfigArchiveIndex.Entry::ref).filter(r->r.artifactType()==ama.contract.ConfigArchiveContracts.ArtifactType.PROCESSING_CONFIG).findFirst().orElseThrow();
   String batch=UUID.randomUUID().toString(),input=UUID.randomUUID().toString();
   var prepared=assertDoesNotThrow(()->c.authority().prepare(draft,batch,input,snapshot,processing,()->false),"intake preparation");
  return prepared;
 }
 void bind(ama.transcript.JdbcTranscriptAuthority.Components c,ama.transcript.JdbcTranscriptAuthority.PreparedIntake p,List<Uploaded> uploaded){
  tx.required(()->{
   var gate=c.registry().lock(actor,meeting,MeetingAccess.Action.EDIT);var reservation=c.authority().reserveIntake(gate,p);
   var bindings=new ArrayList<FileRegistry.Binding>();
   for(var u:uploaded)bindings.add(new FileRegistry.Binding(c.registry().prelock(gate,new FileRegistry.Reference("INPUT_BATCH_FILE",p.batchId(),"TRANSCRIPT_INPUT"),u.file(),reservation),0,u.upload()));
   var receipt=c.registry().prelockSources(gate,uploaded.stream().map(Uploaded::file).toList(),false).orElseThrow();
   var held=c.authority().insertAndReadNew(reservation,receipt);c.registry().bindAll(receipt,bindings);c.authority().requireHeld(held,gate,p.spec());return null;
  });
 }
 String task(ama.transcript.JdbcTranscriptAuthority.PreparedIntake p,String state){
  var spec=p.spec();String task=id();ownedTasks.add(task);
  j.update("INSERT INTO durable_task(id,task_type,pool,subject_kind,subject_id,producer_key,input_kind,input_id,input_fingerprint,business_version,cancel_epoch,priority,state,attempt,max_attempts,lease_owner,expires_at,available_at,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,0,?,1,3,'TASK03_WORKER',UTC_TIMESTAMP(6)+INTERVAL 1 HOUR,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",task,spec.type(),spec.pool(),spec.subject().kind(),spec.subject().id(),spec.producerKey(),spec.input().kind(),spec.input().id(),spec.inputFingerprint(),spec.businessVersion(),spec.cancelEpoch(),state);
  j.update("INSERT INTO task_attempt(task_id,attempt,owner,started_at) VALUES (?,1,'TASK03_WORKER',UTC_TIMESTAMP(6))",task);return task;
 }
 String revision(ama.transcript.JdbcTranscriptAuthority.PreparedIntake p,String task,String kind,String parent,long revision){
  String id=id();j.update("INSERT INTO transcript_revision(id,meeting_id,revision,kind,batch_id,parent_id,input_fingerprint,created_by,created_at,processing_input_id,producer_task_id) VALUES (?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6),?,?)",id,meeting,revision,kind,p.batchId(),parent,p.spec().inputFingerprint(),owner,p.inputId(),task);return id;
 }
 void current(String revision,long version){j.update("UPDATE meeting SET transcript_revision_id=?,revision=? WHERE id=?",revision,version,meeting);j.update("UPDATE task_subject SET business_version=? WHERE subject_kind='MEETING' AND subject_id=?",version,meeting);}
 Actor member(){
  String person="authority-member-"+id(),roster=id();
  j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",person,"member😀");
  j.update("INSERT INTO roster_revision(id,meeting_id,revision,state,confirmed_at,confirmed_by) VALUES (?,?,1,'CONFIRMED',UTC_TIMESTAMP(6),?)",roster,meeting,owner);
  j.update("INSERT INTO roster_member(roster_id,member_id,enterprise_id,display_name,member_kind) VALUES (?,?,?,?,'ENTERPRISE')",roster,id(),person,"member😀");
  j.update("INSERT INTO meeting_access_grant VALUES (?,?,'READ',?,UTC_TIMESTAMP(6))",meeting,person,roster);
  j.update("UPDATE meeting SET roster_revision_id=? WHERE id=?",roster,meeting);
  return new Actor(person,Status.ACTIVE,Set.of(Role.USER));
 }
 Actor outsider(){String person="authority-outsider-"+id();j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",person,person);return new Actor(person,Status.ACTIVE,Set.of(Role.PLATFORM_ADMIN));}
 public void close() throws Exception {
  try {
   for(String task:ownedTasks){
    tx.required(()->{
     var state=j.queryForObject("SELECT state FROM durable_task WHERE id=? FOR UPDATE",String.class,task);
     if(!Set.of("SUCCEEDED","FAILED").contains(state)){
      j.update("UPDATE task_attempt SET ended_at=UTC_TIMESTAMP(6),outcome='FAILED',error_code='TASK03_FIXTURE_CLOSED' WHERE task_id=? AND ended_at IS NULL",task);
      j.update("UPDATE durable_task SET state='FAILED',lease_owner=NULL,expires_at=NULL WHERE id=?",task);
     }
     return null;
    });
   }
   Path receipt=Task06InheritedResources.evidencePath();Files.createDirectories(receipt);
   var rows=new ArrayList<String>();rows.add("owner="+owner+" meeting="+meeting);
   for(String task:ownedTasks)rows.add("synthetic-read-fixture task="+task+" state="+j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task));
   Files.write(receipt.resolve(meeting+".txt"),rows);
  } finally {storage.close();}
 }
}
