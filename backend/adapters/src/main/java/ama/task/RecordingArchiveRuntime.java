package ama.task;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.*;
import ama.contract.TaskContracts.*;
import ama.contract.FileContracts.*;
import ama.db.TransactionRunner;
import ama.meeting.JdbcMeetingAccess;
import ama.recording.*;
import ama.storage.*;
import ama.transcript.PublicationWriteGuard;
import javax.sql.DataSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Recording archive effects are confined to the queue's freshly validated completion transaction. */
public final class RecordingArchiveRuntime {
 public static final String TYPE="RECORDING_ARCHIVE_V1",POOL="RECORDING_ARCHIVE",KIND="RECORDING_ARCHIVE_INPUT";
 private RecordingArchiveRuntime(){}
 public record Dependencies(JdbcMeetingAccess access,FileRegistry registry,FileUploads uploads,FileBackendCatalog backends,JdbcRecordingPersistence recordings,PublicationWriteGuard publication) {
  public Dependencies {Objects.requireNonNull(access);Objects.requireNonNull(registry);Objects.requireNonNull(uploads);Objects.requireNonNull(backends);Objects.requireNonNull(recordings);Objects.requireNonNull(publication);}
 }
 public record Status(String taskId,String state,boolean available,long sampleCount){}
 private record Input(String id,String session,String meeting,String owner,long revision,long epoch,String fingerprint,String producer) {
  TaskSpec spec(){return new TaskSpec(TYPE,POOL,new SubjectRef("MEETING",meeting),producer,new TypedRef(KIND,id),fingerprint,revision,0,epoch,3);}
  Actor actor(){return new Actor(owner,ama.contract.IdentityContracts.Status.ACTIVE,Set.of(Role.USER));}
 }
 public static JdbcTaskQueue install(DataSource source,JdbcSubjectFences fences,JdbcOutbox outbox,Dependencies dependencies){return JdbcTaskQueue.installConfined(source,fences,outbox,q->Map.of(TYPE,policy(q,dependencies)));}
 static TaskDomainPolicy policy(JdbcTaskQueue queue,Dependencies dependencies){dependencies.access().requireProjectionSource(queue.source);return new Policy(queue,dependencies);}
 private static Policy installed(JdbcTaskQueue queue){if(!(queue.policies.get(TYPE) instanceof Policy p)||p.queue!=queue)throw new IllegalStateException("Recording archive not installed");return p;}
 public static Map<String,OutboxReferenceValidator> eventReferences(DataSource source) {
  var j=new JdbcTemplate(source);j.setQueryTimeout(2);
  return Map.of(KIND,event->{
   if(event.taskId()==null||!event.subject().kind().equals("MEETING")||!Set.of("TASK_ENQUEUED","TASK_RETRY","TASK_SUCCEEDED","TASK_FAILED","TASK_CANCELLED","TASK_STALE").contains(event.eventType()))throw new IllegalArgumentException("Recording task event required");
   if(j.queryForObject("SELECT COUNT(*) FROM durable_task t JOIN recording_archive_input i ON i.id=t.input_id WHERE t.id=? AND i.id=? AND i.meeting_id=? AND t.subject_id=i.meeting_id AND t.subject_kind='MEETING' AND t.task_type=? AND t.pool=? AND t.input_kind=? AND t.business_version=? AND t.business_version=i.base_revision AND t.cancel_epoch=i.cancel_epoch AND t.input_fingerprint=i.input_fingerprint AND t.producer_key=i.producer_key",Integer.class,event.taskId(),event.reference().id(),event.subject().id(),TYPE,POOL,KIND,event.subjectRevision())!=1)throw new IllegalArgumentException("Recording task event provenance");
  });
 }
 /** Independent admission after capture sealing. A crash before admission can safely replay finish. */
 public static Status accept(JdbcTaskQueue queue,Actor actor,String session){return installed(queue).accept(actor,session);}
 public static Status status(JdbcTaskQueue queue,Actor actor,String session){return installed(queue).status(actor,session);}
 public static Status statusInput(JdbcTaskQueue queue,Actor actor,String meeting,String input){
  var p=installed(queue);return queue.tx.required(()->{
   p.d.access().require(p.d.access().lock(actor,meeting),MeetingAccess.Action.READ);
   var rows=queue.jdbc.query("SELECT t.id,t.state,a.state,COALESCE(a.sample_count,0),EXISTS(SELECT 1 FROM recording_archive_current c WHERE c.input_id=i.id AND c.session_id=i.session_id) FROM recording_archive_input i JOIN durable_task t ON t.input_id=i.id AND t.input_kind=? AND t.producer_key=i.producer_key JOIN recording_archive a ON a.input_id=i.id WHERE i.id=? AND i.meeting_id=?",(r,n)->new Status(r.getString(1),r.getString(2),r.getString(2).equals("SUCCEEDED")&&r.getString(3).equals("READY")&&r.getBoolean(5),r.getLong(4)),KIND,input,meeting);
   if(rows.size()!=1)throw new NotFound();return rows.getFirst();
  });
 }
 public static FileRegistry.Download download(JdbcTaskQueue queue,Actor actor,String session,String range){
  var p=installed(queue);String file=p.queue.tx.required(()->{p.authorize(actor,session,MeetingAccess.Action.READ);return p.file(session);});
  return p.d.registry().download(actor,file,range);
 }
 public static Supplier<TaskResult> prepare(JdbcTaskQueue queue,TaskLease lease,PreparationCancellation cancellation){
  if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Archive I/O requires independent preparation");return installed(queue).prepare(lease,cancellation);
 }
 /** The policy accepts only this process's actual declaration in its original transaction. */
 public static void requireReferenceDeclaration(FileRegistry.ReferenceContext context,DataSource source,String meeting,String archive,String file){
  if(!(context instanceof Domain domain)||domain.owner.queue.source!=source||domain.holder!=TransactionSynchronizationManager.getResource(source)||!TransactionSynchronizationManager.isActualTransactionActive()||domain.gate==null||!domain.spec.subject().id().equals(meeting)||!Objects.equals(domain.archive,archive)||!Objects.equals(domain.fileId,file))throw new IllegalArgumentException("Foreign recording completion declaration");
 }
 /** Registration additionally requires the actual queue-owned WRITING invocation, never a caller flag. */
 public static void requireReferenceCompletion(FileRegistry.ReferenceContext context,DataSource source,String meeting,String archive,String file){
  requireReferenceDeclaration(context,source,meeting,archive,file);var domain=(Domain)context;
  if(domain.scope==null||domain.invocation==null||domain.carrier==null)throw new IllegalArgumentException("Recording queue completion required");
  domain.scope.requireInvocation(domain.owner.queue,domain.owner,domain.invocation,domain.carrier,domain.carrier.lease,CompletionPhase.WRITING);
 }
 private static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
 private static void check(PreparationCancellation c){if(Thread.currentThread().isInterrupted()||c.cancelled())throw new CancellationException("Archive cancelled");}
 private static final class Domain implements FileRegistry.ReferenceContext {
  final Policy owner;final Object holder;final TaskSpec spec;
  final Actor actor;final JdbcMeetingAccess.FactIdentities identities;final PublicationWriteGuard.Prepared plan;
  MeetingAccess.Locked access;FileRegistry.LockedMeeting gate;PublicationWriteGuard.Held publication;
  FileRegistry.LockedReference reference;FileRegistry.LockedSources sources;FileRegistry.CompletedSource file;
  String archive,fileId;JdbcTaskQueue.AfterTask scope;CompletionInvocation invocation;Policy.Carrier carrier;
  Domain(Policy owner,TaskSpec spec,Actor actor,JdbcMeetingAccess.FactIdentities identities,PublicationWriteGuard.Prepared plan){this.owner=owner;this.spec=spec;holder=TransactionSynchronizationManager.getResource(owner.queue.source);this.actor=actor;this.identities=identities;this.plan=plan;}
 }
 private static final class Policy implements ConfinedTaskCompletionPolicy {
  final JdbcTaskQueue queue;final Dependencies d;final JdbcTaskPreparations issuer;final RecordingArchivePreparation archives;
  Policy(JdbcTaskQueue queue,Dependencies d){this.queue=queue;this.d=d;issuer=new JdbcTaskPreparations(queue.source);archives=new RecordingArchivePreparation(d.recordings(),d.backends());}
  Input input(String id,boolean lock){
   var rows=queue.jdbc.query("SELECT id,session_id,meeting_id,requested_by,base_revision,cancel_epoch,input_fingerprint,producer_key FROM recording_archive_input WHERE id=?"+(lock?" FOR UPDATE":""),(r,n)->new Input(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getLong(5),r.getLong(6),r.getString(7),r.getString(8)),id);
   if(rows.size()!=1)throw new IllegalArgumentException("Recording input unavailable");return rows.getFirst();
  }
  void shape(TaskSpec s){if(!s.type().equals(TYPE)||!s.pool().equals(POOL)||!s.input().kind().equals(KIND)||!s.subject().kind().equals("MEETING"))throw new IllegalArgumentException("Recording task shape");}
  String authorize(Actor actor,String session,MeetingAccess.Action action){String meeting=d.recordings().sessionMetadata(session).session().meetingId();d.access().require(d.access().lock(actor,meeting),action);return meeting;}
  String file(String session){var files=queue.jdbc.queryForList("SELECT a.file_id FROM recording_archive a JOIN recording_archive_current c ON c.input_id=a.input_id AND c.session_id=a.session_id WHERE a.session_id=? AND a.state='READY'",String.class,session);if(files.size()!=1)throw new NotFound();return files.getFirst();}
  Status status(Actor actor,String session){return queue.tx.required(()->{authorize(actor,session,MeetingAccess.Action.READ);return projection(session);});}
  Status projection(String session){
   var rows=queue.jdbc.query("SELECT t.id,t.state,a.state,COALESCE(a.sample_count,0) FROM recording_archive_current c JOIN recording_archive_input i ON i.id=c.input_id JOIN durable_task t ON t.input_id=i.id AND t.input_kind=? AND t.producer_key=i.producer_key JOIN recording_archive a ON a.input_id=i.id WHERE c.session_id=?",(r,n)->new Status(r.getString(1),r.getString(2),r.getString(2).equals("SUCCEEDED")&&r.getString(3).equals("READY"),r.getLong(4)),KIND,session);
   if(rows.size()!=1)throw new NotFound();return rows.getFirst();
  }
  Status accept(Actor actor,String session){
   JdbcTaskQueue.independent();var detached=d.recordings().load(session);String meeting=detached.session().meetingId();
   d.access().require(actor,meeting,MeetingAccess.Action.EDIT);String fingerprint=archives.inputFingerprint(session);
   var plan=d.publication().prepare(actor,meeting);
   return queue.tx.required(()->{
    var gate=d.access().lock(actor,meeting);d.access().require(gate,MeetingAccess.Action.EDIT);
    var registry=d.registry().adopt(actor,meeting,gate,MeetingAccess.Action.EDIT);var held=d.publication().controls(plan,actor,meeting,gate);
    return d.recordings().locked(session,()->{
     if(!fingerprint.equals(archives.inputFingerprint(session)))throw new Conflict();
     var root=queue.jdbc.queryForMap("SELECT revision,cancel_epoch FROM meeting WHERE id=?",meeting);long revision=((Number)root.get("revision")).longValue(),epoch=((Number)root.get("cancel_epoch")).longValue();
     var current=queue.jdbc.queryForList("SELECT t.state,i.input_fingerprint,i.generation FROM recording_archive_current c JOIN recording_archive_input i ON i.id=c.input_id JOIN durable_task t ON t.input_id=i.id AND t.input_kind=? AND t.producer_key=i.producer_key WHERE c.session_id=?",KIND,session);
     long generation=0;
     if(!current.isEmpty()){
      if(current.size()!=1||!fingerprint.equals(current.getFirst().get("input_fingerprint")))throw new Conflict();
      if(Set.of("READY","RUNNING","RECONCILING","SUCCEEDED").contains(current.getFirst().get("state"))){var result=projection(session);queue.outbox.flush();return result;}
      if(!Set.of("FAILED","CANCELLED","STALE").contains(current.getFirst().get("state")))throw new Conflict();
      generation=Math.addExact(((Number)current.getFirst().get("generation")).longValue(),1);
     }
     d.publication().reserveLocalArchive(plan);
     String id=UUID.randomUUID().toString(),producer=hash(("ama.recording-archive-task.v2|"+session+"|"+revision+"|"+epoch+"|"+generation+"|"+fingerprint).getBytes(StandardCharsets.UTF_8));
     var in=new Input(id,session,meeting,actor.enterpriseId(),revision,epoch,fingerprint,producer);
     queue.jdbc.update("INSERT INTO recording_archive_input(id,session_id,meeting_id,requested_by,base_revision,cancel_epoch,input_fingerprint,producer_key,generation) VALUES (?,?,?,?,?,?,?,?,?)",id,session,meeting,actor.enterpriseId(),revision,epoch,fingerprint,producer,generation);
     queue.jdbc.update("INSERT INTO recording_archive_current(session_id,input_id) VALUES (?,?) ON DUPLICATE KEY UPDATE input_id=VALUES(input_id)",session,id);
     queue.jdbc.update("INSERT INTO recording_archive(id,input_id,session_id,meeting_id,state,backend_id) VALUES (?,?,?,?,'PENDING',?)",UUID.randomUUID().toString(),id,session,meeting,detached.session().backendId());
     var domain=new Domain(this,in.spec(),actor,null,plan);domain.access=gate;domain.gate=registry;domain.publication=held;
     queue.enqueue(in.spec(),issuer.issue(List.of(in.spec()),Acquisition.TASK_MUTATION,domain,true));
     var result=projection(session);queue.outbox.flush();return result;
    });
   });
  }
  public Optional<PreparedDomain> prepare(List<TaskSpec> specs,Acquisition purpose,boolean skip){
   if(specs.size()!=1||purpose!=Acquisition.TASK_MUTATION)throw new DomainPreparationUnavailableException();var spec=specs.getFirst();shape(spec);var in=input(spec.input().id(),false);if(!in.spec().equals(spec))throw new IllegalArgumentException("Recording input mismatch");
   var actor=in.actor();var plan=d.publication().prepare(actor,in.meeting());var identities=d.access().tryLockFactIdentities(actor,List.of(actor.enterpriseId()),skip);
   return identities.map(ids->issuer.issue(specs,purpose,new Domain(this,spec,actor,ids,plan),true));
  }
  public void requirePrepared(List<TaskSpec> specs,Acquisition purpose,PreparedDomain held){issuer.require(specs,purpose,held);}
  public boolean permitsDomainEffects(PreparedDomain held){return issuer.permitsEffects(held);}
  public boolean lockRoot(TaskSpec spec,boolean skip){throw new DomainPreparationUnavailableException();}
  public boolean lockRoots(List<TaskSpec> specs,boolean skip,PreparedDomain held){
   issuer.require(specs,Acquisition.TASK_MUTATION,held);var domain=(Domain)issuer.value(held);var spec=specs.getFirst();var in=input(spec.input().id(),false);
   if(domain.access==null){
    var facts=d.access().lockFactRoot(domain.identities,in.meeting(),skip);if(facts.isEmpty())return false;
    if(d.access().factAllowsBusiness(facts.orElseThrow(),in.epoch(),in.revision())){
     domain.access=d.access().authorizeFactRoot(facts.orElseThrow(),in.epoch(),in.revision());domain.gate=d.registry().adopt(domain.actor,in.meeting(),domain.access,MeetingAccess.Action.EDIT);domain.publication=d.publication().controls(domain.plan,domain.actor,in.meeting(),domain.access);
    }
   }
   var sessions=queue.jdbc.queryForList("SELECT session_id FROM recording_session WHERE session_id=? AND meeting_id=? AND state='FINISHED' FOR UPDATE"+(skip?" SKIP LOCKED":""),String.class,in.session(),in.meeting());if(sessions.isEmpty())return false;
   if(!input(in.id(),true).equals(in))throw new IllegalArgumentException("Recording input changed");
   var current=queue.jdbc.queryForList("SELECT input_id FROM recording_archive_current WHERE session_id=? FOR UPDATE",String.class,in.session());if(!current.equals(List.of(in.id())))throw new Conflict();
   var candidates=queue.jdbc.queryForList("SELECT id,file_id,state FROM recording_archive WHERE input_id=? FOR UPDATE",in.id());if(candidates.size()!=1)throw new IllegalArgumentException("Recording archive missing");var row=candidates.getFirst();
   if(domain.gate!=null&&"PREPARED".equals(row.get("state"))&&row.get("file_id") instanceof String file){
    domain.archive=(String)row.get("id");domain.fileId=file;
    domain.reference=d.registry().prelock(domain.gate,new FileRegistry.Reference("RECORDING_ARCHIVE",domain.archive,"RECORDING_ARCHIVE"),file,domain);
    var sources=d.registry().prelockSources(domain.gate,List.of(file),skip);if(sources.isEmpty())return false;domain.sources=sources.orElseThrow();domain.file=d.registry().sources(domain.gate,domain.sources,List.of(file)).getFirst();
   }
   if(domain.gate!=null)d.publication().tasks(domain.publication,domain.access);
   return true;
  }
  public void validateInput(TaskSpec spec){shape(spec);if(!input(spec.input().id(),false).spec().equals(spec))throw new IllegalArgumentException("Recording input provenance");}
  public void validateOutput(TaskSpec spec,TaskResult result){if(result.outcome()==Outcome.SUCCEEDED)throw new IllegalStateException("QUEUE_PATH_REQUIRED");}
  public void validateExternalRequest(TaskSpec spec,ama.contract.ExternalContracts.ExternalRequest request){throw new IllegalStateException("Archive has no external operation");}
  final class Carrier implements Supplier<TaskResult> {
   final Policy owner=Policy.this;final TaskLease lease;final String archive,file,upload,hash;final long size,samples;final PreparationCancellation cancellation;final TaskResult failure;
   Carrier(TaskLease lease,String archive,String file,String upload,String hash,long size,long samples,PreparationCancellation cancellation,TaskResult failure){this.lease=lease;this.archive=archive;this.file=file;this.upload=upload;this.hash=hash;this.size=size;this.samples=samples;this.cancellation=cancellation;this.failure=failure;}
   public TaskResult get(){throw new IllegalStateException("QUEUE_PATH_REQUIRED");}
  }
  Carrier failed(TaskLease lease,PreparationCancellation c,String code){return new Carrier(lease,null,null,null,null,0,0,c,new TaskResult(Outcome.RETRYABLE,null,code));}
  Supplier<TaskResult> prepare(TaskLease lease,PreparationCancellation c){
   check(c);shape(lease.spec());String stage="LEASE_FACTS";
   try(var lifetime=new SourceReadLifetime(c,System.nanoTime()+java.time.Duration.ofMinutes(10).toNanos())){
    var in=queue.work(()->{var valid=queue.validatedLease(lease);if(valid.isEmpty())throw new CancellationException("Archive lease expired");var domain=(Domain)issuer.value(valid.orElseThrow().domain().prepared());if(domain.gate==null)throw new MeetingAccess.AccessDeniedException();return input(lease.spec().input().id(),false);});
    stage="PREPARE_AUDIO";var artifact=archives.prepare(in.session(),c::cancelled);check(c);if(!artifact.inputFingerprint().equals(in.fingerprint()))throw new IllegalStateException("Frozen recording changed");
    stage="UPLOAD_BEGIN";var accepted=d.uploads().beginPinned(in.actor(),in.meeting(),in.id(),new Begin("RECORDING_ARCHIVE","录音.wav",artifact.blob().size(),artifact.blob().sha256()),"recording-archive",artifact.blob().key().backendId());
    long partSize=Math.min(accepted.partSize(),8L*1024*1024);if(partSize<1)throw new IllegalStateException("Upload bounds unavailable");var parts=new ArrayList<ClientPart>();
    stage="SOURCE_READ";try(var stream=d.backends().require(artifact.blob().key().backendId(),false).storage().read(artifact.blob().key(),0,artifact.blob().size());var registered=SourceReadLifetime.track(()->{try{stream.close();}catch(IOException ignored){}})){
     long remaining=artifact.blob().size();int number=1;
     while(remaining>0){check(c);int count=(int)Math.min(partSize,remaining);byte[] bytes=stream.readNBytes(count);if(bytes.length!=count)throw new IOException("Archive truncated");String hash=hash(bytes);stage="UPLOAD_PART";d.uploads().part(in.actor(),accepted.uploadId(),number,new ByteArrayInputStream(bytes),count,hash,"recording-archive");parts.add(new ClientPart(number++,hash));remaining-=count;}
     if(stream.read()!=-1)throw new IOException("Archive oversized");
    }
    check(c);stage="UPLOAD_COMPLETE";var completed=d.uploads().complete(in.actor(),accepted.uploadId(),new Completion(parts,artifact.blob().sha256()),"recording-archive");check(c);
    stage="PREPARED_FACTS";return queue.work(()->{
     if(queue.validatedLease(lease).isEmpty())throw new CancellationException("Archive lease changed");
     var row=queue.jdbc.queryForMap("SELECT id,state,file_id,upload_id,sha256,size_bytes,sample_count FROM recording_archive WHERE input_id=? FOR UPDATE",in.id());String id=(String)row.get("id");
     if(row.get("file_id")!=null&&(!completed.fileId().equals(row.get("file_id"))||!accepted.uploadId().equals(row.get("upload_id"))||!artifact.blob().sha256().equals(row.get("sha256"))))throw new Conflict();
     queue.jdbc.update("UPDATE recording_archive SET state='PREPARED',file_id=?,upload_id=?,sha256=?,size_bytes=?,sample_count=? WHERE id=? AND state IN ('PENDING','PREPARED')",completed.fileId(),accepted.uploadId(),artifact.blob().sha256(),artifact.blob().size(),artifact.sampleCount(),id);
     return new Carrier(lease,id,completed.fileId(),accepted.uploadId(),artifact.blob().sha256(),artifact.blob().size(),artifact.sampleCount(),c,null);
    });
   }catch(CancellationException cancelled){throw cancelled;}
   catch(Exception failure){check(c);StringBuilder facts=new StringBuilder();Throwable cause=failure;for(int depth=0;cause!=null&&depth<8;depth++,cause=cause.getCause()){facts.append(" type=").append(cause.getClass().getName());if(cause instanceof ama.contract.StorageContracts.StorageException storage)facts.append(" storageCode=").append(storage.code());if(cause instanceof ama.contract.MinutesContracts.Failure invalid)facts.append(" domainCode=").append(invalid.code());if(cause instanceof java.sql.SQLException sql)facts.append(" sqlState=").append(sql.getSQLState()!=null&&sql.getSQLState().matches("[A-Za-z0-9]{5}")?sql.getSQLState():"UNKNOWN").append(" vendorCode=").append(sql.getErrorCode());}
    java.util.logging.Logger.getLogger(RecordingArchiveRuntime.class.getName()).warning("Recording archive preparation unavailable: stage="+stage+" task="+lease.taskId()+" attempt="+lease.attempt()+facts);return failed(lease,c,"RECORDING_ARCHIVE_PREPARATION_FAILED");}
  }
  public CompletionInvocation bindCompletion(JdbcTaskQueue.AfterTask scope,Supplier<TaskResult> carrier){
   if(!(carrier instanceof Policy.Carrier c)||c.owner!=this)throw new IllegalArgumentException("Foreign archive carrier");var held=scope.preparedFor(queue,this,List.of(c.lease.spec()));var domain=(Domain)issuer.value(held);
   var invocation=new CompletionInvocation(){
    boolean written,validated;
    void guard(CompletionPhase phase){scope.requireInvocation(queue,Policy.this,this,c,c.lease,phase);issuer.require(List.of(c.lease.spec()),Acquisition.TASK_MUTATION,held);check(c.cancellation);}
    public TaskResult write(){
     guard(CompletionPhase.WRITING);if(written)throw new IllegalStateException("Archive writer reused");written=true;if(c.failure!=null)return c.failure;
     if(domain.gate==null||domain.reference==null||domain.sources==null||domain.file==null)throw new IllegalStateException("Archive completion not authorized");
     var file=domain.file.file();if(!file.fileId().equals(c.file)||!domain.file.uploadId().equals(c.upload)||!file.sha256().equals(c.hash)||file.size()!=c.size||!domain.file.originalPurpose().equals("RECORDING_ARCHIVE"))throw new Conflict();
     d.registry().bindAll(domain.sources,List.of(new FileRegistry.Binding(domain.reference,file.locationRevision(),c.upload)));
     if(queue.jdbc.update("UPDATE recording_archive SET state='READY' WHERE id=? AND input_id=? AND file_id=? AND upload_id=? AND sha256=? AND size_bytes=? AND sample_count=? AND state='PREPARED'",c.archive,c.lease.spec().input().id(),c.file,c.upload,c.hash,c.size,c.samples)!=1)throw new Conflict();
     return new TaskResult(Outcome.SUCCEEDED,new TypedRef("RECORDING_ARCHIVE",c.archive),null);
    }
    public void validateActual(TaskResult result){
     guard(CompletionPhase.VALIDATING);scope.requireResult(result);if(!written||validated)throw new IllegalStateException("Archive validation phase");validated=true;
     if(c.failure!=null){if(result!=c.failure)throw new IllegalStateException("Archive failure changed");return;}
     if(result.outcome()!=Outcome.SUCCEEDED||!result.output().equals(new TypedRef("RECORDING_ARCHIVE",c.archive))||queue.jdbc.queryForObject("SELECT COUNT(*) FROM recording_archive a JOIN file_reference r ON r.file_id=a.file_id AND r.owner_kind='RECORDING_ARCHIVE' AND r.owner_id=a.id AND r.purpose='RECORDING_ARCHIVE' WHERE a.id=? AND a.input_id=? AND a.state='READY' AND a.file_id=? AND a.upload_id=? AND a.size_bytes=? AND a.sha256=? AND a.sample_count=?",Integer.class,c.archive,c.lease.spec().input().id(),c.file,c.upload,c.size,c.hash,c.samples)!=1)throw new IllegalStateException("Archive output provenance");
    }
   };
   domain.scope=scope;domain.invocation=invocation;domain.carrier=c;return invocation;
  }
 }
}
