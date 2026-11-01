package ama.task;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.*;
import ama.contract.TaskContracts.*;
import ama.contract.StorageContracts.*;
import ama.contract.TranscriptContracts.Failure;
import ama.export.*;
import ama.meeting.JdbcMeetingAccess;
import ama.storage.*;
import ama.transcript.SavedMinutesExportSource;
import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Read-only durable exports. Only the genuine queue completion can register immutable artifacts. */
public final class MinutesExportRuntime {
 public static final String TYPE="MINUTES_EXPORT_V1",POOL="MINUTES_EXPORT",KIND="MINUTES_EXPORT_INPUT";
 private MinutesExportRuntime(){}
 public record Dependencies(JdbcMeetingAccess access,FileRegistry registry,FileBackendCatalog backends,SavedMinutesExportSource saved){
  public Dependencies{Objects.requireNonNull(access);Objects.requireNonNull(registry);Objects.requireNonNull(backends);Objects.requireNonNull(saved);}
 }
 public record Status(String exportJobId,String state,String sourceRevision,String format,String fileId,String error){}
 private record Input(String id,String meeting,String actor,String keyHash,String requestHash,String revisionId,String sourceHash,String format,long revision,long access,long epoch,String finalId,String backend,String fingerprint,String producer){
  Actor identity(){return new Actor(actor,ama.contract.IdentityContracts.Status.ACTIVE,Set.of(Role.USER));}
  TaskSpec spec(){return new TaskSpec(TYPE,POOL,new SubjectRef("MEETING",meeting),producer,new TypedRef(KIND,id),fingerprint,revision,0,epoch,3);}
 }
 private record Artifact(String id,String input,String task,long attempt,String file,String backend,String namespace,String key,String state,long size,String hash){StorageKey storageKey(){return new StorageKey(backend,namespace,key);}}
 public static JdbcTaskQueue install(DataSource source,JdbcSubjectFences fences,JdbcOutbox outbox,Dependencies dependencies){return JdbcTaskQueue.installConfined(source,fences,outbox,q->Map.of(TYPE,policy(q,dependencies)));}
 static TaskDomainPolicy policy(JdbcTaskQueue queue,Dependencies d){d.access().requireProjectionSource(queue.source);d.saved().requireSource(queue.source);return new Policy(queue,d);}
 private static Policy installed(JdbcTaskQueue queue){if(!(queue.policies.get(TYPE) instanceof Policy p)||p.queue!=queue)throw new IllegalStateException("Export runtime unavailable");return p;}
 public static Status accept(JdbcTaskQueue queue,Actor actor,String meeting,String key,String sourceRevision,String format){return installed(queue).accept(actor,meeting,key,sourceRevision,format);}
 public static Status status(JdbcTaskQueue queue,Actor actor,String task){return installed(queue).status(actor,task);}
 public static Supplier<TaskResult> prepare(JdbcTaskQueue queue,TaskLease lease,MinutesExportProcess converter,PreparationCancellation cancellation){JdbcTaskQueue.independent();return installed(queue).prepare(lease,converter,cancellation);}
 public static Map<String,OutboxReferenceValidator> eventReferences(DataSource source){
  var j=new JdbcTemplate(source);j.setQueryTimeout(2);return Map.of(KIND,event->{
   if(event.taskId()==null||!event.subject().kind().equals("MEETING")||!Set.of("TASK_ENQUEUED","TASK_RETRY","TASK_SUCCEEDED","TASK_FAILED","TASK_CANCELLED","TASK_STALE").contains(event.eventType()))throw new IllegalArgumentException("Export task event required");
   if(j.queryForObject("SELECT COUNT(*) FROM durable_task t JOIN minutes_export_input i ON i.id=t.input_id WHERE t.id=? AND i.id=? AND i.meeting_id=? AND t.subject_id=i.meeting_id AND t.subject_kind='MEETING' AND t.task_type=? AND t.pool=? AND t.input_kind=? AND t.business_version=? AND t.business_version=i.base_revision AND t.cancel_epoch=i.cancel_epoch AND t.input_fingerprint=i.input_fingerprint AND t.producer_key=i.producer_key",Integer.class,event.taskId(),event.reference().id(),event.subject().id(),TYPE,POOL,KIND,event.subjectRevision())!=1)throw new IllegalArgumentException("Export event provenance");
  });
 }
 private static String digest(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private static String hash(String... fields){try{var digest=MessageDigest.getInstance("SHA-256");for(String field:fields){if(field==null){digest.update(java.nio.ByteBuffer.allocate(4).putInt(-1).array());continue;}byte[] bytes=field.getBytes(StandardCharsets.UTF_8);digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes);}return HexFormat.of().formatHex(digest.digest());}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private static String fingerprint(Input in){return hash("ama.minutes-export.input.v1",in.id(),in.meeting(),in.actor(),in.keyHash(),in.requestHash(),in.revisionId(),in.sourceHash(),in.format(),Long.toString(in.revision()),Long.toString(in.access()),Long.toString(in.epoch()),in.finalId(),in.backend());}
 private static void check(PreparationCancellation c){if(Thread.currentThread().isInterrupted()||c.cancelled())throw new CancellationException("Export cancelled");}
 private static Failure conflict(){return new Failure(409,"EXPORT_REQUEST_CONFLICT");}
 private static final class Domain {
  final Policy owner;final Input input;final JdbcMeetingAccess.FactIdentities identities;final SavedMinutesExportSource.Metadata metadata;
  FileRegistry.LockedMeeting gate;SavedMinutesExportSource.Captured captured;boolean admitted;String denial;
  List<Artifact> artifacts=List.of();
  Domain(Policy owner,Input input,JdbcMeetingAccess.FactIdentities identities,SavedMinutesExportSource.Metadata metadata){this.owner=owner;this.input=input;this.identities=identities;this.metadata=metadata;}
 }
 private static final class Policy implements ConfinedTaskCompletionPolicy {
  final JdbcTaskQueue queue;final Dependencies d;final JdbcTaskPreparations issuer;
  Policy(JdbcTaskQueue queue,Dependencies d){this.queue=queue;this.d=d;issuer=new JdbcTaskPreparations(queue.source);}
  Input input(String id,boolean lock){
   var rows=queue.jdbc.query("SELECT id,meeting_id,requested_by,request_key_hash,request_hash,source_revision_id,source_hash,format,base_revision,access_revision,cancel_epoch,final_confirmation_id,backend_id,input_fingerprint,producer_key FROM minutes_export_input WHERE id=?"+(lock?" FOR UPDATE":""),(r,n)->new Input(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getLong(9),r.getLong(10),r.getLong(11),r.getString(12),r.getString(13),r.getString(14),r.getString(15)),id);
   if(rows.size()!=1)throw new Failure(404,"EXPORT_NOT_FOUND");return rows.getFirst();
  }
  List<Artifact> artifacts(String input,boolean lock){return queue.jdbc.query("SELECT id,input_id,task_id,attempt,file_id,backend_id,namespace,object_key,state,size_bytes,sha256 FROM minutes_export_artifact WHERE input_id=? ORDER BY task_id,attempt LIMIT 4"+(lock?" FOR UPDATE":""),(r,n)->new Artifact(r.getString(1),r.getString(2),r.getString(3),r.getLong(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getString(9),r.getLong(10),r.getString(11)),input);}
  void controls(Domain domain){if(!input(domain.input.id(),true).equals(domain.input))throw conflict();domain.artifacts=artifacts(domain.input.id(),true);if(domain.artifacts.size()>3)throw conflict();}
  void shape(TaskSpec spec){if(!TYPE.equals(spec.type())||!POOL.equals(spec.pool())||!KIND.equals(spec.input().kind())||!"MEETING".equals(spec.subject().kind()))throw new IllegalArgumentException("Export task shape");}
  Status projection(Input in){
   var rows=queue.jdbc.queryForList("SELECT id,state,last_error,output_kind,output_id FROM durable_task WHERE producer_key=? AND input_kind=? AND input_id=?",in.producer(),KIND,in.id());if(rows.size()!=1)throw new Failure(503,"EXPORT_UNAVAILABLE");var row=rows.getFirst();String file=null;
   if("SUCCEEDED".equals(row.get("state"))&&"MINUTES_EXPORT".equals(row.get("output_kind"))){var files=queue.jdbc.queryForList("SELECT a.file_id FROM minutes_export_artifact a JOIN generated_file_origin g ON g.file_id=a.file_id AND g.origin_id=a.id AND g.task_id=a.task_id JOIN meeting m ON m.id=? WHERE a.id=? AND a.input_id=? AND a.task_id=? AND a.state='REGISTERED' AND m.revision=? AND m.access_revision=? AND m.cancel_epoch=? AND m.minutes_revision_id=? AND m.governance_state='ACTIVE' AND m.lifecycle='ACTIVE'",String.class,in.meeting(),row.get("output_id"),in.id(),row.get("id"),in.revision(),in.access(),in.epoch(),in.revisionId());if(files.size()==1)file=files.getFirst();}
   return new Status((String)row.get("id"),(String)row.get("state"),in.revisionId(),in.format(),file,(String)row.get("last_error"));
  }
  Status status(Actor actor,String task){
   ama.contract.MinutesContracts.uuid(task);var ids=queue.jdbc.queryForList("SELECT input_id FROM durable_task WHERE id=? AND task_type=? AND input_kind=?",String.class,task,TYPE,KIND);if(ids.size()!=1)throw new Failure(404,"EXPORT_NOT_FOUND");var in=input(ids.getFirst(),false);
   return queue.tx.required(()->{d.access().require(d.access().lock(actor,in.meeting()),MeetingAccess.Action.READ);if(!in.actor().equals(actor.enterpriseId()))throw new MeetingAccess.AccessDeniedException();return projection(in);});
  }
  Status replay(Actor actor,String meeting,String key,String request){
   var ids=queue.jdbc.queryForList("SELECT id FROM minutes_export_input WHERE meeting_id=? AND requested_by=? AND request_key_hash=?",String.class,meeting,actor.enterpriseId(),key);if(ids.isEmpty())return null;if(ids.size()!=1)throw conflict();var in=input(ids.getFirst(),false);if(!in.requestHash().equals(request))throw conflict();return projection(in);
  }
  Status accept(Actor actor,String meeting,String key,String revision,String format){
   JdbcTaskQueue.independent();ama.contract.MinutesContracts.uuid(meeting);ama.contract.MinutesContracts.uuid(revision);
   if(key==null||!key.matches("[\\x21-\\x7e]{1,128}")||!Set.of("md","pdf","docx").contains(format==null?"":format))throw new Failure(400,"INVALID_EXPORT_REQUEST");
   String keyHash=hash("ama.minutes-export.key.v1",key),request=hash("ama.minutes-export.request.v1",revision,format);
   var old=queue.tx.required(()->{d.access().require(d.access().lock(actor,meeting),MeetingAccess.Action.READ);return replay(actor,meeting,keyHash,request);});if(old!=null)return old;
   var prepared=d.saved().prepare(actor,meeting,revision);var backend=d.backends().defaultWrite();
   return queue.tx.required(()->{
    var gate=d.saved().lockRoot(prepared,actor,meeting);var again=replay(actor,meeting,keyHash,request);if(again!=null)return again;
    String id=UUID.randomUUID().toString();var draft=new Input(id,meeting,actor.enterpriseId(),keyHash,request,revision,prepared.sourceHash(),format,prepared.meetingRevision(),prepared.accessRevision(),prepared.cancelEpoch(),prepared.finalConfirmation(),backend.id(),null,null);
    String fingerprint=fingerprint(draft),producer=hash("ama.minutes-export.producer.v1",id,fingerprint);var in=new Input(id,meeting,actor.enterpriseId(),keyHash,request,revision,prepared.sourceHash(),format,prepared.meetingRevision(),prepared.accessRevision(),prepared.cancelEpoch(),prepared.finalConfirmation(),backend.id(),fingerprint,producer);
    var domain=new Domain(this,in,null,null);domain.gate=gate;domain.admitted=true;
    d.saved().recheck(prepared,gate,()->{queue.jdbc.update("INSERT INTO minutes_export_input(id,meeting_id,requested_by,request_key_hash,request_hash,source_revision_id,source_hash,format,base_revision,access_revision,cancel_epoch,final_confirmation_id,backend_id,input_fingerprint,producer_key,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6))",in.id(),in.meeting(),in.actor(),in.keyHash(),in.requestHash(),in.revisionId(),in.sourceHash(),in.format(),in.revision(),in.access(),in.epoch(),in.finalId(),in.backend(),in.fingerprint(),in.producer());controls(domain);});
    queue.enqueue(in.spec(),issuer.issue(List.of(in.spec()),Acquisition.TASK_MUTATION,domain,true));queue.outbox.flush();return projection(in);
   });
  }
  public Optional<PreparedDomain> prepare(List<TaskSpec> specs,Acquisition purpose,boolean skip){
   if(specs.size()!=1||purpose!=Acquisition.TASK_MUTATION)throw new DomainPreparationUnavailableException();var spec=specs.getFirst();shape(spec);var in=input(spec.input().id(),false);if(!in.spec().equals(spec))throw new IllegalArgumentException("Export input mismatch");
   SavedMinutesExportSource.Metadata metadata=null;String denial=null;
   try{metadata=d.saved().discover(in.meeting());}catch(ama.contract.MinutesContracts.Failure|Failure|MeetingAccess.AccessDeniedException invalid){denial="EXPORT_SOURCE_UNAVAILABLE";}
   final var plan=metadata;final var reason=denial;var identities=d.access().tryLockFactIdentities(in.identity(),plan==null?List.of(in.actor()):plan.identities(),skip);
   return identities.map(ids->{var domain=new Domain(this,in,ids,plan);domain.denial=reason;return issuer.issue(specs,purpose,domain,true);});
  }
  public void requirePrepared(List<TaskSpec> specs,Acquisition purpose,PreparedDomain held){issuer.require(specs,purpose,held);}
  public boolean permitsDomainEffects(PreparedDomain held){return issuer.permitsEffects(held);}
  public boolean lockRoot(TaskSpec spec,boolean skip){throw new DomainPreparationUnavailableException();}
  public boolean lockRoots(List<TaskSpec> specs,boolean skip,PreparedDomain held){
   issuer.require(specs,Acquisition.TASK_MUTATION,held);var domain=(Domain)issuer.value(held);if(domain.admitted)return true;var in=domain.input;
   var facts=d.access().lockFactRoot(domain.identities,in.meeting(),skip);if(facts.isEmpty())return false;
   var allowed=d.access().authorizeReadFactRoot(facts.orElseThrow(),in.epoch(),in.revision());
   if(allowed.isPresent()&&domain.metadata!=null&&domain.denial==null){
    domain.gate=d.registry().adopt(in.identity(),in.meeting(),allowed.orElseThrow(),MeetingAccess.Action.READ);
    try{domain.captured=d.saved().capture(in.identity(),domain.gate,domain.metadata,()->controls(domain));}
    catch(ama.contract.MinutesContracts.Failure|Failure|MeetingAccess.AccessDeniedException invalid){domain.gate=null;domain.denial="EXPORT_SOURCE_UNAVAILABLE";}
   }else domain.denial="EXPORT_ACCESS_REVOKED";
   if(domain.gate==null)controls(domain);return true;
  }
  public void validateInput(TaskSpec spec){shape(spec);var in=input(spec.input().id(),false);if(!in.spec().equals(spec)||!fingerprint(in).equals(in.fingerprint())||!hash("ama.minutes-export.producer.v1",in.id(),in.fingerprint()).equals(in.producer()))throw new IllegalArgumentException("Export input provenance");}
  public void validateOutput(TaskSpec spec,TaskResult result){if(result.outcome()==Outcome.SUCCEEDED)throw new IllegalStateException("QUEUE_PATH_REQUIRED");}
  public void validateExternalRequest(TaskSpec spec,ama.contract.ExternalContracts.ExternalRequest request){throw new IllegalStateException("Exports have no external operation");}
  final class Carrier implements Supplier<TaskResult>{
   final Policy owner=Policy.this;final TaskLease lease;final SavedMinutesExportSource.Prepared source;final Artifact artifact;final PreparationCancellation cancellation;final TaskResult failure;
   Carrier(TaskLease lease,SavedMinutesExportSource.Prepared source,Artifact artifact,PreparationCancellation cancellation,TaskResult failure){this.lease=lease;this.source=source;this.artifact=artifact;this.cancellation=cancellation;this.failure=failure;}
   public TaskResult get(){throw new IllegalStateException("QUEUE_PATH_REQUIRED");}
  }
  Domain domain(TaskLease lease){var valid=queue.validatedLease(lease).orElseThrow(()->new CancellationException("Export lease changed"));return (Domain)issuer.value(valid.domain().prepared());}
  void requireSource(Input in,SavedMinutesExportSource.Prepared source){if(!in.meeting().equals(source.meetingId())||!in.revisionId().equals(source.sourceRevision())||!in.sourceHash().equals(source.sourceHash())||in.revision()!=source.meetingRevision()||in.access()!=source.accessRevision()||in.epoch()!=source.cancelEpoch()||!Objects.equals(in.finalId(),source.finalConfirmation()))throw new Failure(409,"EXPORT_SOURCE_CHANGED");}
  void usable(Domain domain,SavedMinutesExportSource.Prepared source){if(domain.gate==null||domain.captured==null||domain.denial!=null)throw new MeetingAccess.AccessDeniedException();requireSource(domain.input,source);d.saved().requireCaptured(source,domain.captured);}
  Supplier<TaskResult> prepare(TaskLease lease,MinutesExportProcess converter,PreparationCancellation c){
   check(c);shape(lease.spec());
   try{
    var in=queue.work(()->{var domain=domain(lease);if(domain.gate==null)throw new MeetingAccess.AccessDeniedException();return domain.input;});
    var source=d.saved().prepare(in.identity(),in.meeting(),in.revisionId());requireSource(in,source);check(c);
    byte[] bytes=converter.render(source.document(),MinutesExportRenderer.Format.valueOf(in.format().toUpperCase(Locale.ROOT)),c::cancelled);check(c);String hash=digest(bytes);
    d.access().prepareExternalRead(in.identity(),in.meeting());
    var artifact=queue.work(()->{
     var domain=domain(lease);usable(domain,source);var existing=domain.artifacts.stream().filter(a->a.task().equals(lease.taskId())&&a.attempt()==lease.attempt()).toList();
     if(!existing.isEmpty()){var a=existing.getFirst();if(existing.size()!=1||a.size()!=bytes.length||!a.hash().equals(hash)||!Set.of("INTENT","PREPARED").contains(a.state()))throw conflict();return a;}
     String id=UUID.randomUUID().toString(),file=UUID.randomUUID().toString(),key="exports/"+in.meeting()+"/"+id;
     queue.jdbc.update("INSERT INTO minutes_export_artifact(id,input_id,task_id,attempt,file_id,backend_id,namespace,object_key,state,size_bytes,sha256,created_at) VALUES (?,?,?,?,?,?,'files',?,'INTENT',?,?,UTC_TIMESTAMP(6))",id,in.id(),lease.taskId(),lease.attempt(),file,in.backend(),key,bytes.length,hash);
     return new Artifact(id,in.id(),lease.taskId(),lease.attempt(),file,in.backend(),"files",key,"INTENT",bytes.length,hash);
    });
    check(c);var blob=d.backends().require(in.backend(),true).storage().put(artifact.storageKey(),new ByteArrayInputStream(bytes),bytes.length,hash);
    if(!blob.key().equals(artifact.storageKey())||blob.size()!=artifact.size()||!blob.sha256().equals(artifact.hash()))throw new IllegalStateException("Export storage receipt mismatch");check(c);
    d.access().prepareExternalRead(in.identity(),in.meeting());
    return queue.work(()->{var domain=domain(lease);usable(domain,source);var found=domain.artifacts.stream().filter(a->a.id().equals(artifact.id())).toList();if(found.size()!=1||!Set.of("INTENT","PREPARED").contains(found.getFirst().state()))throw conflict();queue.jdbc.update("UPDATE minutes_export_artifact SET state='PREPARED' WHERE id=? AND state IN ('INTENT','PREPARED')",artifact.id());return new Carrier(lease,source,new Artifact(artifact.id(),artifact.input(),artifact.task(),artifact.attempt(),artifact.file(),artifact.backend(),artifact.namespace(),artifact.key(),"PREPARED",artifact.size(),artifact.hash()),c,null);});
   }catch(CancellationException cancelled){throw cancelled;}
   catch(Exception failure){check(c);boolean denied=failure instanceof MeetingAccess.AccessDeniedException||failure instanceof Failure f&&f.status()==409;String code=denied?"EXPORT_SOURCE_CHANGED":"EXPORT_PREPARATION_FAILED";java.util.logging.Logger.getLogger(MinutesExportRuntime.class.getName()).warning("Export preparation unavailable: task="+lease.taskId()+" attempt="+lease.attempt()+" type="+failure.getClass().getName());return new Carrier(lease,null,null,c,new TaskResult(denied?Outcome.FAILED:Outcome.RETRYABLE,null,code));}
  }
  public CompletionInvocation bindCompletion(JdbcTaskQueue.AfterTask scope,Supplier<TaskResult> payload){
   if(!(payload instanceof Policy.Carrier c)||c.owner!=this)throw new IllegalArgumentException("Foreign export carrier");var held=scope.preparedFor(queue,this,List.of(c.lease.spec()));var domain=(Domain)issuer.value(held);
   return new CompletionInvocation(){
    boolean written,validated;TaskResult expected;
    void guard(CompletionPhase phase){scope.requireInvocation(queue,Policy.this,this,c,c.lease,phase);issuer.require(List.of(c.lease.spec()),Acquisition.TASK_MUTATION,held);check(c.cancellation);}
    public TaskResult write(){
     guard(CompletionPhase.WRITING);if(written)throw new IllegalStateException("Export completion reused");written=true;
     if(c.failure!=null)return expected=c.failure;
     if(domain.gate==null||domain.captured==null||domain.denial!=null)return expected=new TaskResult(Outcome.FAILED,null,"EXPORT_ACCESS_REVOKED");
     try{usable(domain,c.source);}catch(Failure|ama.contract.MinutesContracts.Failure|MeetingAccess.AccessDeniedException invalid){return expected=new TaskResult(Outcome.FAILED,null,"EXPORT_SOURCE_CHANGED");}
     var a=c.artifact;if(domain.artifacts.stream().filter(a::equals).count()!=1)throw conflict();var in=domain.input;
     String storageType=d.backends().historicalType(a.backend());
     queue.jdbc.update("INSERT INTO file_object(id,meeting_id,backend_id,storage_type,namespace,object_key,display_name,size_bytes,sha256,state,reference_epoch,created_at) VALUES (?,?,?,?,?,?,?, ?,?,'AVAILABLE',1,UTC_TIMESTAMP(6))",a.file(),in.meeting(),a.backend(),storageType,a.namespace(),a.key(),"会议纪要."+in.format(),a.size(),a.hash());
     queue.jdbc.update("INSERT INTO generated_file_origin(file_id,meeting_id,origin_kind,origin_id,task_id,purpose,cancel_epoch,backend_id,namespace,object_key,size_bytes,sha256,created_at) VALUES (?,?,'MINUTES_EXPORT',?,?,'MINUTES_EXPORT',?,?,?,?,?,?,UTC_TIMESTAMP(6))",a.file(),in.meeting(),a.id(),a.task(),in.epoch(),a.backend(),a.namespace(),a.key(),a.size(),a.hash());
     queue.jdbc.update("INSERT INTO file_reference(file_id,owner_kind,owner_id,purpose) VALUES (?,'MINUTES_EXPORT',?,'MINUTES_EXPORT')",a.file(),a.id());
     if(queue.jdbc.update("UPDATE minutes_export_artifact SET state='REGISTERED' WHERE id=? AND state='PREPARED'",a.id())!=1)throw conflict();
     return expected=new TaskResult(Outcome.SUCCEEDED,new TypedRef("MINUTES_EXPORT",a.id()),null);
    }
    public void validateActual(TaskResult result){
     guard(CompletionPhase.VALIDATING);scope.requireResult(result);if(!written||validated||result!=expected)throw new IllegalStateException("Export validation phase");validated=true;if(result.outcome()!=Outcome.SUCCEEDED)return;
     var a=c.artifact;if(queue.jdbc.queryForObject("SELECT COUNT(*) FROM minutes_export_artifact a JOIN file_object f ON f.id=a.file_id AND f.backend_id=a.backend_id AND f.namespace=a.namespace AND f.object_key=a.object_key AND f.size_bytes=a.size_bytes AND f.sha256=a.sha256 JOIN generated_file_origin g ON g.file_id=f.id AND g.origin_id=a.id AND g.task_id=a.task_id JOIN file_reference r ON r.file_id=f.id AND r.owner_kind='MINUTES_EXPORT' AND r.owner_id=a.id AND r.purpose='MINUTES_EXPORT' WHERE a.id=? AND a.task_id=? AND a.attempt=? AND a.state='REGISTERED' AND f.state='AVAILABLE' AND f.meeting_id=?",Integer.class,a.id(),c.lease.taskId(),c.lease.attempt(),domain.input.meeting())!=1)throw new IllegalStateException("Export output provenance");
    }
   };
  }
 }
}
