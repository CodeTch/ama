package ama.task;

import ama.contract.TaskContracts.*;
import ama.contract.OutboxContracts.*;
import ama.db.TransactionRunner;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.concurrent.ThreadLocalRandom;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static ama.contract.OutboxContracts.protocolCode;

/** Short transactions: complete identities, then subject/root/task. Capabilities stay on the call stack. */
public final class JdbcTaskQueue implements TaskQueue {
 final DataSource source; final JdbcTemplate jdbc; final TransactionRunner tx;
 final JdbcSubjectFences fences; final JdbcOutbox outbox; final Map<String,TaskDomainPolicy> policies;
 record Row(String id,TaskSpec spec,String state,long attempt,String owner,Instant expiry,Instant available) {}
 public JdbcTaskQueue(DataSource source,JdbcSubjectFences fences,JdbcOutbox outbox,Map<String,TaskDomainPolicy> policies){
  this.source=source;this.jdbc=new JdbcTemplate(source);this.tx=new TransactionRunner(source);this.fences=fences;this.outbox=outbox;this.policies=Map.copyOf(policies);
 }
 <T>T work(Callable<T> action){boolean outer=!TransactionSynchronizationManager.isActualTransactionActive();return tx.required(()->{TaskTransactionContext.current(source).requireBeforeFlush();T value=action.call();if(outer)outbox.flush();return value;});}
 static void independent(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Independent transaction required");}
 TaskDomainPolicy policy(TaskSpec s){var p=policies.get(s.type());if(p==null)throw new IllegalArgumentException("Unregistered task type");return p;}
 Instant now(){return jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)",(r,n)->instant(r,1));}
 static Instant instant(ResultSet r,String column)throws SQLException{var v=r.getObject(column,LocalDateTime.class);return v==null?null:v.toInstant(ZoneOffset.UTC);}
 static Instant instant(ResultSet r,int column)throws SQLException{var v=r.getObject(column,LocalDateTime.class);return v==null?null:v.toInstant(ZoneOffset.UTC);}
 static LocalDateTime sqlTime(Instant v){return v==null?null:LocalDateTime.ofInstant(v,ZoneOffset.UTC);}
 static Row read(ResultSet r,int index)throws SQLException{
  var s=new TaskSpec(r.getString("task_type"),r.getString("pool"),new SubjectRef(r.getString("subject_kind"),r.getString("subject_id")),r.getString("producer_key"),new TypedRef(r.getString("input_kind"),r.getString("input_id")),r.getString("input_fingerprint"),r.getLong("business_version"),r.getInt("priority"),r.getLong("cancel_epoch"),r.getInt("max_attempts"));
  return new Row(r.getString("id"),s,r.getString("state"),r.getLong("attempt"),r.getString("lease_owner"),instant(r,"expires_at"),instant(r,"available_at"));
 }
 Optional<Row> row(String id,boolean lock,boolean skip){return jdbc.query("SELECT * FROM durable_task WHERE id=?"+(lock?" FOR UPDATE"+(skip?" SKIP LOCKED":""):""),JdbcTaskQueue::read,id).stream().findFirst();}
 boolean current(SubjectFencePort.LockedSubject h,TaskSpec s){return !h.deleted()&&h.businessVersion()==s.businessVersion()&&h.cancelEpoch()==s.cancelEpoch();}
 static final class DomainScope {
  private final TaskDomainPolicy policy;private final List<TaskSpec> specs;private final TaskDomainPolicy.Acquisition purpose;private final TaskDomainPolicy.PreparedDomain prepared;
  private SubjectFencePort.LockedSubject locked;
  DomainScope(TaskDomainPolicy policy,List<TaskSpec> specs,TaskDomainPolicy.Acquisition purpose,TaskDomainPolicy.PreparedDomain prepared){this.policy=policy;this.specs=specs;this.purpose=purpose;this.prepared=prepared;}
  TaskDomainPolicy policy(){return policy;}List<TaskSpec> specs(){return specs;}TaskDomainPolicy.Acquisition purpose(){return purpose;}TaskDomainPolicy.PreparedDomain prepared(){return prepared;}
 }
 Optional<DomainScope> prepareDomain(List<TaskSpec> discovered,TaskDomainPolicy.Acquisition purpose,boolean skip,TaskDomainPolicy.PreparedDomain supplied){
  var specs=new ArrayList<>(new LinkedHashSet<>(discovered));if(specs.isEmpty())throw new DomainPreparationUnavailableException();
  specs.sort(Comparator.comparing((TaskSpec s)->s.input().kind()).thenComparing(s->s.input().id()).thenComparing(TaskSpec::type).thenComparing(TaskSpec::producerKey));
  var p=policies.get(specs.getFirst().type());if(p==null)throw new DomainPreparationUnavailableException();var subject=specs.getFirst().subject();
  // No root/identity phase is started when policy families cannot establish one common issuer.
  if(specs.stream().anyMatch(s->policies.get(s.type())!=p||!s.subject().equals(subject)))throw new DomainPreparationUnavailableException();
  var prepared=supplied==null?p.prepare(specs,purpose,skip):Optional.of(supplied);
  if(prepared.isEmpty())return Optional.empty();JdbcTaskPreparations.requireTransaction(source,prepared.orElseThrow());p.requirePrepared(specs,purpose,prepared.orElseThrow());
  if(purpose==TaskDomainPolicy.Acquisition.TASK_MUTATION&&!p.permitsDomainEffects(prepared.orElseThrow()))return Optional.empty();
  return Optional.of(new DomainScope(p,List.copyOf(specs),purpose,prepared.orElseThrow()));
 }
 Optional<SubjectFencePort.LockedSubject> lockDomain(DomainScope scope,TaskSpec s,boolean skip){
  if(!scope.specs().contains(s)||scope.policy()!=policy(s))throw new IllegalArgumentException("Task not covered by preparation");
  JdbcTaskPreparations.requireTransaction(source,scope.prepared());
  scope.policy().requirePrepared(scope.specs(),scope.purpose(),scope.prepared());
  if(scope.locked!=null)return Optional.of(scope.locked);
  var held=fences.lock(s.subject(),skip);
  if(held.isEmpty()||!scope.policy().lockRoots(scope.specs(),skip,scope.prepared()))return Optional.empty();
  scope.locked=held.orElseThrow();return held;
 }
 Optional<SubjectFencePort.LockedSubject> lockDomain(TaskSpec s,boolean skip){
  var scope=prepareDomain(List.of(s),TaskDomainPolicy.Acquisition.TASK_MUTATION,skip,null);
  return scope.isEmpty()?Optional.empty():lockDomain(scope.orElseThrow(),s,skip);
 }
 void event(String type,Row row){outbox.append(new EventDraft(type,row.id(),row.spec().subject(),row.spec().businessVersion(),new Reference(row.spec().input().kind(),row.spec().input().id())));}
 @Override public String enqueue(TaskSpec s){return enqueue(s,null);}
 @Override public String enqueue(TaskSpec s,TaskDomainPolicy.PreparedDomain alreadyHeld){return work(()->enqueueLocked(s,UUID.randomUUID().toString(),alreadyHeld));}
 String enqueueLocked(TaskSpec s,String id,TaskDomainPolicy.PreparedDomain alreadyHeld){
  if(s.priority() < -100 || s.priority()>100)throw new IllegalArgumentException("Task priority must be between -100 and 100");
  var scope=prepareDomain(List.of(s),TaskDomainPolicy.Acquisition.TASK_MUTATION,false,alreadyHeld).orElseThrow(()->new IllegalArgumentException("Missing domain preparation"));
  var held=lockDomain(scope,s,false).orElseThrow(()->new IllegalArgumentException("Missing subject/root"));
  policy(s).validateInput(s);
  var old=jdbc.query("SELECT * FROM durable_task WHERE producer_key=? FOR UPDATE",JdbcTaskQueue::read,s.producerKey());
  if(!old.isEmpty()){if(!old.getFirst().spec().equals(s))throw new IllegalArgumentException("Producer key conflicts with immutable spec");return old.getFirst().id();}
  if(!current(held,s))throw new IllegalArgumentException("Stale task input fence");
  return insertNew(s,id);
 }
 String insertNew(TaskSpec s,String id){
  jdbc.update("INSERT INTO durable_task(id,task_type,pool,subject_kind,subject_id,producer_key,input_kind,input_id,input_fingerprint,business_version,cancel_epoch,priority,max_attempts,available_at,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",id,s.type(),s.pool(),s.subject().kind(),s.subject().id(),s.producerKey(),s.input().kind(),s.input().id(),s.inputFingerprint(),s.businessVersion(),s.cancelEpoch(),s.priority(),s.maxAttempts());
  event("TASK_ENQUEUED",row(id,true,false).orElseThrow());return id;
 }
 @Override public Optional<TaskLease> claim(String pool,String worker){
  independent();protocolCode(pool);protocolCode(worker);
  // Four bounded pages prevent one locked head batch from blocking all later candidates.
  for(int page=0;page<4;page++){
   var candidates=new ArrayList<Row>();
   candidates.addAll(jdbc.query("SELECT * FROM durable_task WHERE pool=? AND state='READY' AND available_at<=UTC_TIMESTAMP(6) ORDER BY available_at,id LIMIT 64 OFFSET ?",JdbcTaskQueue::read,pool,page*64));
   candidates.addAll(jdbc.query("SELECT * FROM durable_task WHERE pool=? AND state='RUNNING' AND expires_at<=UTC_TIMESTAMP(6) ORDER BY expires_at,id LIMIT 64 OFFSET ?",JdbcTaskQueue::read,pool,page*64));
   if(candidates.isEmpty())break;
   Instant now=now();candidates.sort(Comparator.<Row>comparingLong(r->-(r.spec().priority()+Math.max(0,Duration.between(r.available(),now).getSeconds())/5)).thenComparing(Row::id));
   for(var candidate:candidates){var lease=work(()->claimOne(candidate,worker));if(lease.isPresent())return lease;}
  }
  return Optional.empty();
 }
 private Optional<TaskLease> claimOne(Row candidate,String worker){
  if(!policies.containsKey(candidate.spec().type()))return Optional.empty();
  var domain=lockDomain(candidate.spec(),true);if(domain.isEmpty())return Optional.empty();
  var locked=row(candidate.id(),true,true);if(locked.isEmpty())return Optional.empty();var r=locked.orElseThrow();Instant now=now();
  if(!r.spec().equals(candidate.spec()))return Optional.empty();
  if(!(r.state().equals("READY")&&!r.available().isAfter(now)||r.state().equals("RUNNING")&&!r.expiry().isAfter(now)))return Optional.empty();
  if(!current(domain.orElseThrow(),r.spec())){finish(r,domain.orElseThrow().deleted()||domain.orElseThrow().cancelEpoch()!=r.spec().cancelEpoch()?"CANCELLED":"STALE","SUBJECT_CHANGED",null);return Optional.empty();}
  policy(r.spec()).validateInput(r.spec());
  if(r.state().equals("RUNNING"))endAttempt(r,"EXPIRED","LEASE_EXPIRED");
  if(r.attempt()>=r.spec().maxAttempts()){terminateUnmarkedExecution(r);finish(r,"FAILED","ATTEMPT_LIMIT",null);return Optional.empty();}
  long attempt=r.attempt()+1;Instant expiry=now.plusSeconds(20);
  jdbc.update("UPDATE durable_task SET state='RUNNING',attempt=?,lease_owner=?,expires_at=? WHERE id=?",attempt,worker,sqlTime(expiry),r.id());
  jdbc.update("INSERT INTO task_attempt(task_id,attempt,owner,started_at) VALUES (?,?,?,?)",r.id(),attempt,worker,sqlTime(now));
  return Optional.of(new TaskLease(r.id(),attempt,worker,expiry,r.spec()));
 }
 Optional<Row> validLease(TaskLease lease){
  var domain=lockDomain(lease.spec(),false);if(domain.isEmpty())return Optional.empty();
  var locked=row(lease.taskId(),true,false);if(locked.isEmpty())return Optional.empty();var r=locked.orElseThrow();
  if(!r.spec().equals(lease.spec())||!current(domain.orElseThrow(),r.spec())||!r.state().equals("RUNNING")||r.attempt()!=lease.attempt()||!Objects.equals(r.owner(),lease.leaseOwner())||r.expiry()==null||!r.expiry().isAfter(now()))return Optional.empty();
  policy(r.spec()).validateInput(r.spec());return Optional.of(r);
 }
 @Override public boolean renew(TaskLease lease){return work(()->{if(validLease(lease).isEmpty())return false;jdbc.update("UPDATE durable_task SET expires_at=UTC_TIMESTAMP(6)+INTERVAL 20 SECOND WHERE id=?",lease.taskId());return true;});}
 @Override public boolean complete(TaskLease lease,TaskResult result){return completeWithEffects(lease,result,()->{});}
 @Override public boolean completeWithEffects(TaskLease lease,TaskResult result,Runnable effects){return work(()->{
  var valid=validLease(lease);if(valid.isEmpty())return false;var r=valid.orElseThrow();policy(r.spec()).validateOutput(r.spec(),result);
  effects.run();
  completeValidated(r,result);return true;
 });}
 @Override public boolean completeWithEffects(TaskLease lease,Supplier<TaskResult> effects){
  Objects.requireNonNull(lease,"lease");Objects.requireNonNull(effects,"effects");
  return work(()->{
   var valid=validLease(lease);if(valid.isEmpty())return false;var r=valid.orElseThrow();
   var result=Objects.requireNonNull(effects.get(),"Prepared completion result");
   policy(r.spec()).validateOutput(r.spec(),result);
   completeValidated(r,result);return true;
  });
 }
 private void completeValidated(Row r,TaskResult result){
  if(result.outcome()==Outcome.RETRYABLE && r.attempt()<r.spec().maxAttempts()){
   long delay=Math.min(40,5L << Math.min(3,r.attempt()-1));long millis=ThreadLocalRandom.current().nextLong(1001);
   jdbc.update("UPDATE durable_task SET state='READY',lease_owner=NULL,expires_at=NULL,available_at=?,last_error=? WHERE id=?",sqlTime(now().plusMillis(delay*1000+millis)),result.errorCode(),r.id());endAttempt(r,"RETRYABLE",result.errorCode());event("TASK_RETRY",r);
  }else finish(r,result.outcome()==Outcome.RETRYABLE?"FAILED":result.outcome().name(),result.errorCode(),result.output());
 }
 void endAttempt(Row r,String outcome,String code){if(r.attempt()>0)jdbc.update("UPDATE task_attempt SET ended_at=UTC_TIMESTAMP(6),outcome=?,error_code=? WHERE task_id=? AND attempt=? AND ended_at IS NULL",outcome,code,r.id(),r.attempt());}
 void finish(Row r,String state,String code,TypedRef output){jdbc.update("UPDATE durable_task SET state=?,lease_owner=NULL,expires_at=NULL,last_error=?,output_kind=?,output_id=? WHERE id=?",state,code,output==null?null:output.kind(),output==null?null:output.id(),r.id());endAttempt(r,state,code);event("TASK_"+state,r);}
 /** A PREPARED execution has never crossed the committed marker boundary. Task lock is already held. */
 private void terminateUnmarkedExecution(Row task){
  var candidates=jdbc.queryForList("SELECT operation_id,execution_no FROM external_execution WHERE task_id=? AND state='PREPARED'",task.id());
  if(candidates.isEmpty())return;var candidate=candidates.getFirst();String operation=candidate.get("operation_id").toString();long number=((Number)candidate.get("execution_no")).longValue();
  var parents=jdbc.queryForList("SELECT state,current_execution_no FROM external_operation WHERE id=? FOR UPDATE",operation);
  var executions=jdbc.queryForList("SELECT id,state FROM external_execution WHERE operation_id=? AND execution_no=? FOR UPDATE",operation,number);
  if(parents.size()!=1||executions.size()!=1||!parents.getFirst().get("state").equals("RETRY_QUEUED")||((Number)parents.getFirst().get("current_execution_no")).longValue()!=number||!executions.getFirst().get("state").equals("PREPARED"))return;
  jdbc.update("UPDATE external_execution SET state='FAILED',finished_at=UTC_TIMESTAMP(6),no_effect_basis='NOT_SENT',no_effect_evidence='LOCAL_PREPARED_ATTEMPT_LIMIT',no_effect_verified_at=UTC_TIMESTAMP(6) WHERE id=?",executions.getFirst().get("id"));
  jdbc.update("UPDATE external_operation SET state='FAILED',last_error='ATTEMPT_LIMIT',reconcile_epoch=reconcile_epoch+1 WHERE id=?",operation);
 }
}
