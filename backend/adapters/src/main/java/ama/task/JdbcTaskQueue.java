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
import java.util.function.Function;
import java.util.concurrent.ThreadLocalRandom;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static ama.contract.OutboxContracts.protocolCode;

/** Short transactions: complete identities, then subject/root/task. Capabilities stay on the call stack. */
public final class JdbcTaskQueue implements TaskQueue {
 private boolean initialized;
 final DataSource source; final JdbcTemplate jdbc; final TransactionRunner tx;
 final JdbcSubjectFences fences; final JdbcOutbox outbox; final Map<String,TaskDomainPolicy> policies;
 record Row(String id,TaskSpec spec,String state,long attempt,String owner,Instant expiry,Instant available) {}
 public JdbcTaskQueue(DataSource source,JdbcSubjectFences fences,JdbcOutbox outbox,Map<String,TaskDomainPolicy> policies){
  this(source,fences,outbox,q->policies);
 }
 private JdbcTaskQueue(DataSource source,JdbcSubjectFences fences,JdbcOutbox outbox,Function<JdbcTaskQueue,Map<String,TaskDomainPolicy>> factory){
  this.source=Objects.requireNonNull(source);this.jdbc=new JdbcTemplate(source);this.jdbc.setQueryTimeout(2);this.tx=new TransactionRunner(source,10);this.fences=Objects.requireNonNull(fences);this.outbox=Objects.requireNonNull(outbox);
  this.policies=Map.copyOf(factory.apply(this));initialized=true;
 }
 static JdbcTaskQueue installConfined(DataSource source,JdbcSubjectFences fences,JdbcOutbox outbox,Function<JdbcTaskQueue,Map<String,TaskDomainPolicy>> factory){return new JdbcTaskQueue(source,fences,outbox,factory);}
 <T>T work(Callable<T> action){if(!initialized)throw new IllegalStateException("Queue not initialized");boolean outer=!TransactionSynchronizationManager.isActualTransactionActive();return tx.required(()->{TaskTransactionContext.current(source).requireBeforeFlush();T value=action.call();if(outer)outbox.flush();return value;});}
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
  private JdbcGenerationRetention.PublicationFacts publicationFacts;
  JdbcGenerationRetention.PublicationFacts publicationFacts(){return publicationFacts;}
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
  if(scope.purpose()==TaskDomainPolicy.Acquisition.EXTERNAL_FACT_OBSERVATION&&scope.policy() instanceof GenerationRetentionPolicy retention
    &&s.type().equals("MINUTES_GENERATE_V1")&&s.input().kind().equals("GENERATION_INPUT"))
  {var preparedPublication=retention.prelockedPublication(scope.prepared());scope.publicationFacts=preparedPublication.isPresent()?JdbcGenerationRetention.revisitPublication(source,scope.prepared(),s.subject().id(),preparedPublication.orElseThrow()):JdbcGenerationRetention.capturePublication(source,scope.prepared(),s.subject().id());}
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
  var preparation=prepareDomain(List.of(candidate.spec()),TaskDomainPolicy.Acquisition.TASK_MUTATION,true,null);if(preparation.isEmpty())return Optional.empty();var scope=preparation.orElseThrow();var domain=lockDomain(scope,candidate.spec(),true);if(domain.isEmpty())return Optional.empty();
  var locked=row(candidate.id(),true,true);if(locked.isEmpty())return Optional.empty();var r=locked.orElseThrow();Instant now=now();
  if(!r.spec().equals(candidate.spec()))return Optional.empty();
  if(!(r.state().equals("READY")&&!r.available().isAfter(now)||r.state().equals("RUNNING")&&!r.expiry().isAfter(now)))return Optional.empty();
  if(r.state().equals("RUNNING")&&unfinishedAttempt(r).isEmpty())return Optional.empty();
  if(!current(domain.orElseThrow(),r.spec())){finishLocal(scope,r,domain.orElseThrow().deleted()||domain.orElseThrow().cancelEpoch()!=r.spec().cancelEpoch()?"CANCELLED":"STALE","SUBJECT_CHANGED",null,true,false);return Optional.empty();}
  policy(r.spec()).validateInput(r.spec());
  if(r.state().equals("RUNNING"))endAttempt(r,"EXPIRED","LEASE_EXPIRED");
  if(r.attempt()>=r.spec().maxAttempts()){finishLocal(scope,r,"FAILED","ATTEMPT_LIMIT",null,false,true);return Optional.empty();}
  now=now();long attempt=r.attempt()+1;Instant expiry=now.plusSeconds(20);
  jdbc.update("UPDATE durable_task SET state='RUNNING',attempt=?,lease_owner=?,expires_at=? WHERE id=?",attempt,worker,sqlTime(expiry),r.id());
  jdbc.update("INSERT INTO task_attempt(task_id,attempt,owner,started_at) VALUES (?,?,?,?)",r.id(),attempt,worker,sqlTime(now));
  return Optional.of(new TaskLease(r.id(),attempt,worker,expiry,r.spec()));
 }
 record LockedAttempt(long attempt,String owner) {}
 record ValidatedLease(DomainScope domain,Row task,LockedAttempt attempt) {}
 private Optional<LockedAttempt> unfinishedAttempt(Row task){
  var rows=jdbc.query("SELECT attempt,owner,ended_at FROM task_attempt WHERE task_id=? AND attempt=? FOR UPDATE",(r,n)->r.getObject("ended_at")==null&&Objects.equals(task.owner(),r.getString("owner"))?new LockedAttempt(r.getLong("attempt"),r.getString("owner")):null,task.id(),task.attempt());
  return rows.size()==1?Optional.ofNullable(rows.getFirst()):Optional.empty();
 }
 Optional<ValidatedLease> validatedLease(TaskLease lease){return validatedLease(lease,null);}
 Optional<ValidatedLease> validatedLease(TaskLease lease,TaskDomainPolicy.PreparedDomain supplied){
  var prepared=prepareDomain(List.of(lease.spec()),TaskDomainPolicy.Acquisition.TASK_MUTATION,false,supplied);
  if(prepared.isEmpty())return Optional.empty();var scope=prepared.orElseThrow();
  var domain=lockDomain(scope,lease.spec(),false);if(domain.isEmpty())return Optional.empty();
  var locked=row(lease.taskId(),true,false);if(locked.isEmpty())return Optional.empty();var r=locked.orElseThrow();
  if(!r.spec().equals(lease.spec())||!current(domain.orElseThrow(),r.spec())||!r.state().equals("RUNNING")||r.attempt()!=lease.attempt()||!Objects.equals(r.owner(),lease.leaseOwner())||r.expiry()==null||!r.expiry().isAfter(now()))return Optional.empty();
  var attempt=unfinishedAttempt(r);if(attempt.isEmpty()||!r.expiry().isAfter(now()))return Optional.empty();
  policy(r.spec()).validateInput(r.spec());return Optional.of(new ValidatedLease(scope,r,attempt.orElseThrow()));
 }
 // Existing external-operation caller needs only the validated row, never completion authority.
 Optional<Row> validLease(TaskLease lease){return validatedLease(lease).map(ValidatedLease::task);}
 static final class AfterTask {
  private final JdbcTaskQueue queue;private final Object holder;private final ValidatedLease valid;private final TaskLease lease;
  private final ConfinedTaskCompletionPolicy policy;private final Supplier<TaskResult> carrier;private final PreparedCompletionEnvelope envelope;
  private CompletionPhase phase=CompletionPhase.BINDING;private CompletionInvocation invocation;private TaskResult result;
  private AfterTask(JdbcTaskQueue queue,ValidatedLease valid,TaskLease lease,ConfinedTaskCompletionPolicy policy,Supplier<TaskResult> carrier,PreparedCompletionEnvelope envelope){
   this.queue=queue;this.valid=valid;this.lease=lease;this.policy=policy;this.carrier=carrier;this.envelope=envelope;holder=TransactionSynchronizationManager.getResource(queue.source);
  }
  private void require(JdbcTaskQueue expected,ConfinedTaskCompletionPolicy registered){
   if(queue!=expected||policy!=registered||!queue.initialized||holder==null||!TransactionSynchronizationManager.isActualTransactionActive()||holder!=TransactionSynchronizationManager.getResource(queue.source)
     ||queue.policy(lease.spec())!=policy||valid.domain.policy()!=policy||valid.domain.purpose()!=TaskDomainPolicy.Acquisition.TASK_MUTATION||!valid.domain.specs().equals(List.of(lease.spec()))
     ||valid.domain.locked==null||!valid.task.spec().equals(lease.spec())||!valid.task.id().equals(lease.taskId())||valid.attempt.attempt()!=lease.attempt()||!valid.attempt.owner().equals(lease.leaseOwner())||phase==CompletionPhase.CLOSED)
    throw new IllegalArgumentException("Foreign or expired after-task scope");
   JdbcTaskPreparations.requireTransaction(queue.source,valid.domain.prepared());policy.requirePrepared(valid.domain.specs(),valid.domain.purpose(),valid.domain.prepared());
  }
  TaskDomainPolicy.PreparedDomain preparedFor(JdbcTaskQueue expected,ConfinedTaskCompletionPolicy registered,List<TaskSpec> specs){
   require(expected,registered);if(phase!=CompletionPhase.BINDING||!valid.domain.specs().equals(specs))throw new IllegalStateException("After-task binding phase required");return valid.domain.prepared();
  }
  void requireInvocation(JdbcTaskQueue expected,ConfinedTaskCompletionPolicy registered,CompletionInvocation exact,Supplier<TaskResult> payload,TaskLease wanted,CompletionPhase expectedPhase){
   require(expected,registered);if(invocation!=exact||carrier!=payload||!lease.equals(wanted)||phase!=expectedPhase)throw new IllegalStateException("After-task invocation mismatch");
  }
  void requireResult(TaskResult value){if(phase!=CompletionPhase.VALIDATING||result!=value)throw new IllegalStateException("After-task result mismatch");}
 }
 @Override public boolean renew(TaskLease lease){return work(()->{if(validLease(lease).isEmpty())return false;jdbc.update("UPDATE durable_task SET expires_at=UTC_TIMESTAMP(6)+INTERVAL 20 SECOND WHERE id=?",lease.taskId());return true;});}
 @Override public boolean complete(TaskLease lease,TaskResult result){return completeWithEffects(lease,result,()->{});}
 @Override public boolean completeWithEffects(TaskLease lease,TaskResult result,Runnable effects){return work(()->{
  var valid=validatedLease(lease);if(valid.isEmpty())return false;var validated=valid.orElseThrow();var r=validated.task();if(policy(r.spec()) instanceof ConfinedTaskCompletionPolicy)throw new IllegalStateException("Confined carrier required");policy(r.spec()).validateOutput(r.spec(),result);
  effects.run();
  completeValidated(validated,result);return true;
 });}
 @Override public boolean completeWithEffects(TaskLease lease,Supplier<TaskResult> effects){
  Objects.requireNonNull(lease,"lease");Objects.requireNonNull(effects,"effects");
  return work(()->{
   var valid=validatedLease(lease);if(valid.isEmpty())return false;var validated=valid.orElseThrow();var r=validated.task();
   if(policy(r.spec()) instanceof ConfinedTaskCompletionPolicy confined){
    var envelope=effects instanceof PreparedCompletionEnvelope e?e:null;
    var payload=envelope==null?effects:Objects.requireNonNull(envelope.payload(),"Completion payload");
    if(payload instanceof PreparedCompletionEnvelope||payload==envelope)throw new IllegalArgumentException("Recursive completion envelope");
    var scope=new AfterTask(this,validated,lease,confined,payload,envelope);
    try {
     scope.invocation=Objects.requireNonNull(confined.bindCompletion(scope,payload));scope.phase=CompletionPhase.BOUND;
     if(envelope!=null)envelope.admit();
     scope.phase=CompletionPhase.WRITING;scope.result=Objects.requireNonNull(scope.invocation.write(),"Prepared completion result");scope.phase=CompletionPhase.WRITTEN;
     scope.phase=CompletionPhase.VALIDATING;scope.invocation.validateActual(scope.result);scope.phase=CompletionPhase.VALIDATED;
     completeValidated(validated,scope.result);scope.phase=CompletionPhase.SETTLED;return true;
    } finally {scope.phase=CompletionPhase.CLOSED;}
   }
   if(effects instanceof PreparedCompletionEnvelope)throw new IllegalArgumentException("Confined completion not installed");
   var result=Objects.requireNonNull(effects.get(),"Prepared completion result");
   policy(r.spec()).validateOutput(r.spec(),result);
   completeValidated(validated,result);return true;
  });
 }
 private void completeValidated(ValidatedLease validated,TaskResult result){
  var r=validated.task();
  if(result.outcome()==Outcome.RETRYABLE && r.attempt()<r.spec().maxAttempts()){
   long delay=Math.min(40,5L << Math.min(3,r.attempt()-1));long millis=ThreadLocalRandom.current().nextLong(1001);
   jdbc.update("UPDATE durable_task SET state='READY',lease_owner=NULL,expires_at=NULL,available_at=?,last_error=? WHERE id=?",sqlTime(now().plusMillis(delay*1000+millis)),result.errorCode(),r.id());endAttempt(r,"RETRYABLE",result.errorCode());event("TASK_RETRY",r);
  }else finishLocal(validated.domain(),r,result.outcome()==Outcome.RETRYABLE?"FAILED":result.outcome().name(),result.errorCode(),result.output(),true,false);
 }
 private static final class LocalTerminal implements PublicationLocalTerminationPolicy.LocalTerminalContext {
  final JdbcTaskQueue queue;final DomainScope scope;final Row row;final Object holder;final String state,reason;final boolean ended;PublicationLocalTerminationPolicy.Phase phase=PublicationLocalTerminationPolicy.Phase.VALIDATING;boolean closed;
  private LocalTerminal(JdbcTaskQueue queue,DomainScope scope,Row row,String state,String reason,boolean ended){this.queue=queue;this.scope=scope;this.row=row;this.state=state;this.reason=reason;this.ended=ended;holder=TransactionSynchronizationManager.getResource(queue.source);}
  void require(DataSource source,TaskDomainPolicy.PreparedDomain prepared,PublicationLocalTerminationPolicy.Phase wanted){
   if(closed||phase!=wanted||queue.source!=source||holder==null||holder!=TransactionSynchronizationManager.getResource(source)||!TransactionSynchronizationManager.isActualTransactionActive()||scope.prepared()!=prepared||scope.locked==null||scope.purpose()!=TaskDomainPolicy.Acquisition.TASK_MUTATION||scope.policy()!=queue.policy(row.spec())||!(scope.policy() instanceof PublicationLocalTerminationPolicy)||!row.spec().type().equals("KNOWLEDGE_PUBLISH_V1")||!row.spec().input().kind().equals("PUBLICATION")||!scope.specs().contains(row.spec()))throw new IllegalArgumentException("Foreign or expired local termination context");
   JdbcTaskPreparations.requireTransaction(source,prepared);scope.policy().requirePrepared(scope.specs(),scope.purpose(),prepared);
  }
 }
 static JdbcTaskPreparations.LocalTerminalFacts requireLocalContext(DataSource source,TaskDomainPolicy.PreparedDomain prepared,PublicationLocalTerminationPolicy.LocalTerminalContext value,PublicationLocalTerminationPolicy.Phase phase){
  if(!(value instanceof LocalTerminal c))throw new IllegalArgumentException("Genuine queue local termination context required");c.require(source,prepared,phase);return new JdbcTaskPreparations.LocalTerminalFacts(c.row.id(),c.row.spec(),c.row.state(),c.row.attempt(),c.row.owner(),c.row.expiry(),c.state,c.reason,c.ended);
 }
 private void finishLocal(DomainScope scope,Row row,String state,String code,TypedRef output,boolean endCurrent,boolean terminatePrepared){finishLocal(scope,row,state,code,output,endCurrent,terminatePrepared,false,false);}
 private void finishLocal(DomainScope scope,Row row,String state,String code,TypedRef output,boolean endCurrent,boolean terminatePrepared,boolean requireEligible,boolean preserveTerminal){
  if(!(scope.policy() instanceof PublicationLocalTerminationPolicy policy)||!row.spec().type().equals("KNOWLEDGE_PUBLISH_V1")||!Set.of("FAILED","CANCELLED","STALE").contains(state)||output!=null){if(requireEligible)throw new IllegalArgumentException("Fixed publication recovery required");if(terminatePrepared)terminateUnmarkedExecution(row);finish(row,state,code,output,endCurrent);return;}
  var context=new LocalTerminal(this,scope,row,state,code,!endCurrent&&row.state().equals("RUNNING"));
  try{context.require(source,scope.prepared(),PublicationLocalTerminationPolicy.Phase.VALIDATING);boolean eligible=policy.validateLocalTerminal(scope.prepared(),context);if(requireEligible&&!eligible)throw new PublicationLocalRecoveryUnavailable("Publication local recovery ineligible");
   if(eligible)terminateLocalPrepared(context);else if(terminatePrepared)terminateUnmarkedExecution(row);
   if(!preserveTerminal){boolean end=endCurrent;if(eligible&&row.state().equals("RECONCILING")){endLocalReconciliationAttempt(context);end=false;}finish(row,state,code,output,end);}
   if(eligible){context.phase=PublicationLocalTerminationPolicy.Phase.SEALING;policy.sealLocalTerminal(scope.prepared(),context);context.require(source,scope.prepared(),PublicationLocalTerminationPolicy.Phase.SEALING);}
  }finally{context.closed=true;}
 }
 /** Private purpose carrier only; a lease, ordinary completion token or caller callback is insufficient. */
 public String recoverPublicationLocal(PublicationLocalTerminationPolicy.PreparedLocalRecovery recovery){Objects.requireNonNull(recovery);return work(()->{
  var spec=recovery.spec();if(!spec.type().equals("KNOWLEDGE_PUBLISH_V1")||!(policy(spec) instanceof PublicationLocalTerminationPolicy p))throw new IllegalArgumentException("Fixed publication recovery required");var binding=p.requireLocalRecovery(recovery);if(!binding.spec().equals(spec))throw new IllegalArgumentException("Recovery spec mismatch");var prepared=prepareDomain(List.of(spec),TaskDomainPolicy.Acquisition.TASK_MUTATION,false,binding.prepared()).orElseThrow();lockDomain(prepared,spec,false).orElseThrow();var row=row(binding.taskId(),true,false).orElseThrow();if(!row.spec().equals(spec))throw new IllegalArgumentException("Actual recovery task mismatch");policy(spec).validateInput(spec);
  boolean terminal=Set.of("FAILED","CANCELLED","STALE").contains(row.state());if(!terminal&&!Set.of("READY","RUNNING","RECONCILING").contains(row.state()))throw new PublicationLocalRecoveryUnavailable("Task cannot recover locally");if((row.state().equals("RUNNING")||row.state().equals("RECONCILING"))&&row.owner()!=null&&(row.expiry()==null||row.expiry().isAfter(now())))throw new PublicationLocalRecoveryUnavailable("Live ordinary owner excludes recovery");
  finishLocal(prepared,row,terminal?row.state():"FAILED",terminal?"TERMINAL_UNMARKED_FAILURE":"EXPIRED_UNMARKED_ABANDONED",null,true,false,true,terminal);return row.id();
 });}
 private void endLocalReconciliationAttempt(LocalTerminal context){context.require(source,context.scope.prepared(),PublicationLocalTerminationPolicy.Phase.VALIDATING);var r=context.row;if(!r.state().equals("RECONCILING"))throw new IllegalStateException("Actual reconciliation state required");var unfinished=jdbc.queryForList("SELECT attempt,owner FROM task_attempt WHERE task_id=? AND ended_at IS NULL ORDER BY attempt FOR UPDATE",r.id());if(unfinished.isEmpty())return;if(unfinished.size()!=1||((Number)unfinished.getFirst().get("attempt")).longValue()!=r.attempt()||!Objects.equals(unfinished.getFirst().get("owner"),r.owner()))throw new IllegalStateException("Exact actual unfinished attempt required");if(jdbc.update("UPDATE task_attempt SET ended_at=UTC_TIMESTAMP(6),outcome='FAILED',error_code='LOCAL_NEVER_MARKED' WHERE task_id=? AND attempt=? AND owner=? AND ended_at IS NULL",r.id(),r.attempt(),r.owner())!=1)throw new IllegalStateException("Local attempt must end exactly once");}
 private void terminateLocalPrepared(LocalTerminal context){
  context.require(source,context.scope.prepared(),PublicationLocalTerminationPolicy.Phase.VALIDATING);var task=context.row;
  var rows=jdbc.queryForList("SELECT id,operation_id,execution_no,state,marked_at,task_attempt,no_effect_basis,no_effect_verified_at FROM external_execution WHERE task_id=? ORDER BY operation_id,execution_no FOR UPDATE",task.id());if(rows.isEmpty())return;if(rows.size()!=1)throw new IllegalStateException("Local closure requires exact execution set");var e=rows.getFirst();if(e.get("marked_at")!=null||e.get("task_attempt")!=null)throw new IllegalStateException("Committed marker excludes local closure");
  if(e.get("state").equals("FAILED")&&"NOT_SENT".equals(e.get("no_effect_basis"))&&e.get("no_effect_verified_at")!=null)return;
  var op=jdbc.queryForMap("SELECT state,current_execution_no FROM external_operation WHERE id=? FOR UPDATE",e.get("operation_id"));if(!e.get("state").equals("PREPARED")||!op.get("state").equals("RETRY_QUEUED")||((Number)op.get("current_execution_no")).longValue()!=((Number)e.get("execution_no")).longValue())throw new IllegalStateException("Exact unmarked prepared execution required");
  if(jdbc.update("UPDATE external_execution SET state='FAILED',finished_at=UTC_TIMESTAMP(6),no_effect_basis='NOT_SENT',no_effect_evidence=?,no_effect_verified_at=UTC_TIMESTAMP(6) WHERE id=? AND state='PREPARED' AND marked_at IS NULL AND task_attempt IS NULL","publication-local:"+task.id(),e.get("id"))!=1)throw new IllegalStateException("Local prepared transition lost");
  if(jdbc.update("UPDATE external_operation SET state='FAILED',last_error='LOCAL_NEVER_MARKED',reconcile_epoch=reconcile_epoch+1,reconcile_owner=NULL,reconcile_expires_at=NULL WHERE id=? AND state='RETRY_QUEUED' AND current_execution_no=?",e.get("operation_id"),e.get("execution_no"))!=1)throw new IllegalStateException("Local operation transition lost");
 }
 void endAttempt(Row r,String outcome,String code){
  if(r.attempt()>0&&"RUNNING".equals(r.state())){
   int changed=jdbc.update("UPDATE task_attempt SET ended_at=UTC_TIMESTAMP(6),outcome=?,error_code=? WHERE task_id=? AND attempt=? AND owner=? AND ended_at IS NULL",outcome,code,r.id(),r.attempt(),r.owner());
   if(changed!=1)throw new IllegalStateException("Current attempt transition must affect exactly one row");
  }
 }
 void finish(Row r,String state,String code,TypedRef output){finish(r,state,code,output,true);}
 private void finish(Row r,String state,String code,TypedRef output,boolean endCurrent){
  jdbc.update("UPDATE durable_task SET state=?,lease_owner=NULL,expires_at=NULL,last_error=?,output_kind=?,output_id=? WHERE id=?",state,code,output==null?null:output.kind(),output==null?null:output.id(),r.id());
  if(endCurrent)endAttempt(r,state,code);event("TASK_"+state,r);
 }
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
