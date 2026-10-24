package ama.task;

import ama.contract.TaskContracts.*;
import ama.contract.ExternalContracts.*;
import java.sql.*;
import java.lang.ref.WeakReference;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.jdbc.core.JdbcTemplate;
import static ama.task.JdbcTaskQueue.*;

/** A token is returned only after the outer marker transaction has committed successfully. */
public final class JdbcExternalOperations implements ExternalOperationPort {
 final JdbcTaskQueue q; final JdbcTemplate j;
 // Weak identity references avoid retaining every abandoned pre-call token for the life of a worker.
 // Value-equal reconstructed records must never inherit initial-send authority.
 private final List<WeakReference<OperationToken>> callAuthorities=new ArrayList<>();
 private synchronized void grantCallAuthority(OperationToken token){callAuthorities.removeIf(ref->ref.get()==null);callAuthorities.add(new WeakReference<>(token));}
 boolean hasCallAuthority(OperationToken token){synchronized(this){callAuthorities.removeIf(ref->ref.get()==null);return callAuthorities.stream().anyMatch(ref->ref.get()==token);}}
 private synchronized void revokeCallAuthority(OperationToken token){callAuthorities.removeIf(ref->ref.get()==null||ref.get()==token);}
 private final List<WeakReference<OperationToken>> lookupAuthorities=new ArrayList<>();
 private synchronized void grantLookup(OperationToken token){lookupAuthorities.removeIf(r->r.get()==null);lookupAuthorities.add(new WeakReference<>(token));}
 /** Private-constructor identity capability. It carries no send or domain-effect authority. */
 public static final class RecordedLookup {
  private final JdbcExternalOperations issuer;private final OperationToken token;
  private RecordedLookup(JdbcExternalOperations issuer,OperationToken token){this.issuer=issuer;this.token=token;}
  public void require(javax.sql.DataSource source,OperationToken actual){if(issuer.q.source!=source||token!=actual)throw new IllegalArgumentException("Foreign recorded lookup");issuer.requireLiveLookup(token);}
 }
 private synchronized void requireLookup(OperationToken token){lookupAuthorities.removeIf(r->r.get()==null);if(lookupAuthorities.stream().noneMatch(r->r.get()==token))throw new IllegalArgumentException("Genuine committed reconciliation claim required");}
 private void requireLiveLookup(OperationToken token){independent();requireLookup(token);var o=operation(token.operationId(),false,false).orElseThrow();var e=execution(o.id(),token.executionNo(),false,false).orElseThrow();if(!valid(o,e,token))throw new IllegalArgumentException("Stale recorded lookup claim");}
 public RecordedLookup recordedLookup(OperationToken token){requireLiveLookup(token);return new RecordedLookup(this,token);}
 @Override public synchronized void releaseRecordedLookup(OperationToken token){lookupAuthorities.removeIf(r->r.get()==null||r.get()==token);}
 record Operation(String id,String kind,String key,String hash,String state,SubjectRef subject,long execution,long epoch,String owner,Instant expiry){}
 record Execution(String id,String operation,long number,String task,String state,String hash,String manifest,String basis,String evidence,Instant verified){}
 private final ReconciliationScanBudget scanBudget;
 private final ReentrantLock scanLock=new ReentrantLock();
 // Enumeration only: no durable claim, cached policy, or task/send capability lives here.
 private final ScanCursor scanCursor=new ScanCursor();
 private static final class ScanCursor {boolean active;long after;long upper;}
 private record ScanCandidate(long sequence,String operationId) {}
 private record ScanOperation(Operation operation,long sequence,Instant nextCheck,boolean due) {}
 public JdbcExternalOperations(JdbcTaskQueue queue){this(queue,new ReconciliationScanBudget(64,64));}
 public JdbcExternalOperations(JdbcTaskQueue queue,ReconciliationScanBudget budget){q=Objects.requireNonNull(queue);j=queue.jdbc;scanBudget=Objects.requireNonNull(budget);}
 private static Operation op(ResultSet r,int n)throws SQLException{return new Operation(r.getString("id"),r.getString("operation_kind"),r.getString("business_key"),r.getString("request_hash"),r.getString("state"),new SubjectRef(r.getString("subject_kind"),r.getString("subject_id")),r.getLong("current_execution_no"),r.getLong("reconcile_epoch"),r.getString("reconcile_owner"),instant(r,"reconcile_expires_at"));}
 private static Execution ex(ResultSet r,int n)throws SQLException{return new Execution(r.getString("id"),r.getString("operation_id"),r.getLong("execution_no"),r.getString("task_id"),r.getString("state"),r.getString("request_hash"),r.getString("request_manifest"),r.getString("no_effect_basis"),r.getString("no_effect_evidence"),instant(r,"no_effect_verified_at"));}
 Optional<Operation> operation(String id,boolean lock,boolean skip){return j.query("SELECT * FROM external_operation WHERE id=?"+(lock?" FOR UPDATE"+(skip?" SKIP LOCKED":""):""),JdbcExternalOperations::op,id).stream().findFirst();}
 Optional<Execution> execution(String operation,long n,boolean lock,boolean skip){
  // Metadata only before any Task9 manifest transfer, including pre-domain discovery.
  var sizes=j.queryForList("SELECT e.id,e.task_id,t.task_type,OCTET_LENGTH(e.request_manifest) AS manifest_bytes FROM external_execution e JOIN durable_task t ON t.id=e.task_id WHERE e.operation_id=? AND e.execution_no=?"+(lock?" FOR UPDATE"+(skip?" SKIP LOCKED":""):""),operation,n);
  if(sizes.isEmpty())return Optional.empty();if(sizes.size()!=1)throw new DomainPreparationUnavailableException();var size=sizes.getFirst();
  if("MINUTES_GENERATE_V1".equals(size.get("task_type"))){
   if(!(size.get("manifest_bytes") instanceof Number bytes)||bytes.longValue()<1||bytes.longValue()>65536)throw new DomainPreparationUnavailableException();
   // Repeat the size and identity predicate in the actual content query to close growth/rebinding races.
   return j.query("SELECT * FROM external_execution WHERE operation_id=? AND execution_no=? AND id=? AND task_id=? AND OCTET_LENGTH(request_manifest) BETWEEN 1 AND 65536"+(lock?" FOR UPDATE"+(skip?" SKIP LOCKED":""):""),JdbcExternalOperations::ex,operation,n,size.get("id"),size.get("task_id")).stream().findFirst();
  }
  return j.query("SELECT * FROM external_execution WHERE operation_id=? AND execution_no=?"+(lock?" FOR UPDATE"+(skip?" SKIP LOCKED":""):""),JdbcExternalOperations::ex,operation,n).stream().findFirst();
 }
 boolean valid(Operation o,Execution e,OperationToken t){return o.subject().equals(t.subject())&&o.id().equals(t.operationId())&&o.execution()==t.executionNo()&&e.id().equals(t.executionId())&&e.number()==t.executionNo()&&o.epoch()==t.reconcileEpoch()&&Objects.equals(o.owner(),t.owner())&&o.expiry()!=null&&o.expiry().isAfter(q.now())&&(o.state().equals("MARKED")||o.state().equals("UNKNOWN"))&&(e.state().equals("MARKED")||e.state().equals("UNKNOWN"));}
 OperationToken token(Operation o,Execution e){return new OperationToken(o.id(),e.id(),o.execution(),o.epoch(),o.owner(),o.expiry(),o.subject());}
 private final class Context implements ExecutionContext {
  final Object holder;final JdbcTaskQueue.DomainScope scope;final Row task;final Operation operation;final Execution execution;
  boolean open=true,retaining;
  private Context(JdbcTaskQueue.DomainScope scope,Row task,Operation operation,Execution execution){
   this.scope=scope;this.task=task;this.operation=operation;this.execution=execution;
   holder=org.springframework.transaction.support.TransactionSynchronizationManager.getResource(q.source);
  }
  JdbcExternalOperations issuer(){return JdbcExternalOperations.this;}
  void require(){
   if(!open||holder==null||holder!=org.springframework.transaction.support.TransactionSynchronizationManager.getResource(q.source)
     ||scope.policy()!=q.policy(task.spec())||!scope.specs().contains(task.spec())||!execution.task().equals(task.id())
     ||!execution.operation().equals(operation.id())||!operation.subject().equals(task.spec().subject()))throw new IllegalArgumentException("Foreign or expired external context");
   JdbcTaskPreparations.requireTransaction(q.source,scope.prepared());scope.policy().requirePrepared(scope.specs(),scope.purpose(),scope.prepared());
  }
 }
 static TaskDomainPolicy.PreparedDomain contextPreparation(javax.sql.DataSource source,ExecutionContext execution){
  if(!(execution instanceof JdbcExternalOperations.Context c)||c.issuer().q.source!=source)throw new IllegalArgumentException("Foreign external context");c.require();return c.scope.prepared();
 }
 static JdbcTaskPreparations.ExternalFacts requireContext(javax.sql.DataSource source,TaskDomainPolicy.PreparedDomain held,ExecutionContext execution){
  if(!(execution instanceof JdbcExternalOperations.Context c)||c.issuer().q.source!=source||c.scope.prepared()!=held)throw new IllegalArgumentException("Foreign external context");
  c.require();return new JdbcTaskPreparations.ExternalFacts(c.task.id(),c.task.spec(),c.operation.id(),c.execution.id(),c.execution.number(),c.execution.hash(),c.execution.manifest());
 }
 static void requireRetentionContext(javax.sql.DataSource source,TaskDomainPolicy.PreparedDomain held,ExecutionContext execution){
  requireContext(source,held,execution);var c=(JdbcExternalOperations.Context)execution;
  if(!c.retaining||c.scope.purpose()!=TaskDomainPolicy.Acquisition.EXTERNAL_FACT_OBSERVATION||!c.scope.policy().permitsDomainEffects(held))throw new IllegalArgumentException("Dedicated retention phase required");
 }
 static boolean publicationFrozen(javax.sql.DataSource source,TaskDomainPolicy.PreparedDomain held,ExecutionContext execution,String meeting){
  requireRetentionContext(source,held,execution);var c=(JdbcExternalOperations.Context)execution;
  var facts=c.scope.publicationFacts();if(facts==null)throw new IllegalArgumentException("Publication facts not prepared");
  return facts.frozen(source,held,meeting);
 }
 private PreparedExternalPolicy preparedPolicy(TaskSpec spec){
  if(!(q.policy(spec) instanceof PreparedExternalPolicy p))throw new IllegalArgumentException("Prepared external policy not installed");return p;
 }
 @Override public Optional<OperationToken> beginPrepared(TaskLease lease,PreparedRequest request){return begin(lease,null,Objects.requireNonNull(request));}
 @Override public Optional<OperationToken> beginExternal(TaskLease lease,ExternalRequest request){return begin(lease,Objects.requireNonNull(request),null);}
 private Optional<OperationToken> begin(TaskLease lease,ExternalRequest legacy,PreparedRequest prepared){
  independent();
  // Exceptions from commit (including lost ACK) escape: no token and no send authority is returned.
  Optional<OperationToken> committed=q.work(()->{
   var valid=q.validatedLease(lease);if(valid.isEmpty())return Optional.empty();var validated=valid.orElseThrow();var task=validated.task();
   if(prepared==null&&q.policy(task.spec()) instanceof PreparedExternalPolicy)throw new IllegalArgumentException("Prepared request required");
   var request=prepared==null?legacy:Objects.requireNonNull(preparedPolicy(task.spec()).requireRequest(task.spec(),prepared));
   q.policy(task.spec()).validateExternalRequest(task.spec(),request);
   var found=j.query("SELECT * FROM external_operation WHERE operation_kind=? AND business_key=? FOR UPDATE",JdbcExternalOperations::op,request.operationKind(),request.businessKey());
   String oid,eid;long number,epoch;Instant now=q.now(),expiry=now.plusSeconds(20);
   if(found.isEmpty()){
    oid=UUID.randomUUID().toString();eid=UUID.randomUUID().toString();number=1;epoch=1;
    j.update("INSERT INTO external_operation(id,operation_kind,business_key,request_hash,state,marked_at,next_check_at,reconcile_epoch,reconcile_owner,reconcile_expires_at,subject_kind,subject_id,current_execution_no) VALUES (?,?,?,?,'MARKED',?,?,?,?,?,?,?,1)",oid,request.operationKind(),request.businessKey(),request.logicalRequestHash(),sqlTime(now),sqlTime(expiry),epoch,lease.leaseOwner(),sqlTime(expiry),task.spec().subject().kind(),task.spec().subject().id());
    j.update("INSERT INTO external_execution(id,operation_id,execution_no,task_id,task_attempt,state,request_hash,request_manifest,created_at,marked_at) VALUES (?,?,1,?,?,'MARKED',?,?,?,?)",eid,oid,task.id(),lease.attempt(),request.executionRequestHash(),request.requestManifest(),sqlTime(now),sqlTime(now));
   }else{
    var o=found.getFirst();if(!o.subject().equals(task.spec().subject())||!o.hash().equals(request.logicalRequestHash()))throw new IllegalArgumentException("External logical key conflict");
    var e=execution(o.id(),o.execution(),true,false).orElseThrow();
    if(!o.state().equals("RETRY_QUEUED")||!e.state().equals("PREPARED")||!e.task().equals(task.id()))return Optional.empty();
    if(!e.hash().equals(request.executionRequestHash())||!e.manifest().equals(request.requestManifest()))throw new IllegalArgumentException("Execution request changed");
    oid=o.id();eid=e.id();number=o.execution();epoch=o.epoch()+1;
    j.update("UPDATE external_execution SET state='MARKED',task_attempt=?,marked_at=? WHERE id=?",lease.attempt(),sqlTime(now),eid);
    j.update("UPDATE external_operation SET state='MARKED',reconcile_epoch=?,reconcile_owner=?,reconcile_expires_at=?,next_check_at=? WHERE id=?",epoch,lease.leaseOwner(),sqlTime(expiry),sqlTime(expiry),oid);
   }
   j.update("UPDATE durable_task SET state='RECONCILING' WHERE id=?",task.id());
   if(prepared!=null){var c=new Context(validated.domain(),task,operation(oid,true,false).orElseThrow(),execution(oid,number,true,false).orElseThrow());
    try{c.require();preparedPolicy(task.spec()).writeMarked(validated.domain().prepared(),c,prepared);c.require();}finally{c.open=false;}}
   q.event("EXTERNAL_MARKED",task);
   return Optional.of(new OperationToken(oid,eid,number,epoch,lease.leaseOwner(),expiry,task.spec().subject()));
  });
  committed.ifPresent(this::grantCallAuthority);return committed;
 }
 private record Locked(Operation op,Execution execution,JdbcTaskQueue.Row task,SubjectFencePort.LockedSubject subject){}
 private Optional<Locked> lock(Operation seen,boolean skip){
  var current=execution(seen.id(),seen.execution(),false,false);if(current.isEmpty())return Optional.empty();
  var task=q.row(current.orElseThrow().task(),false,false);if(task.isEmpty())return Optional.empty();
  if(!q.policies.containsKey(task.orElseThrow().spec().type()))return Optional.empty();
  var domain=q.lockDomain(task.orElseThrow().spec(),skip);if(domain.isEmpty())return Optional.empty();
  var heldTask=q.row(task.orElseThrow().id(),true,skip);if(heldTask.isEmpty())return Optional.empty();
  var op=operation(seen.id(),true,skip);if(op.isEmpty()||op.orElseThrow().execution()!=seen.execution())return Optional.empty();
  var ex=execution(seen.id(),seen.execution(),true,skip);if(ex.isEmpty()||!ex.orElseThrow().task().equals(heldTask.orElseThrow().id()))return Optional.empty();
  return Optional.of(new Locked(op.orElseThrow(),ex.orElseThrow(),heldTask.orElseThrow(),domain.orElseThrow()));
 }
 /** Empty can mean the finite scan budget was consumed; retain this instance across polls. */
 @Override public Optional<OperationToken> claimReconciliation(String owner){
  independent();ama.contract.OutboxContracts.protocolCode(owner);
  if(!scanLock.tryLock())return Optional.empty();
  try {
   if(!scanCursor.active){
    var maximum=j.query("SELECT reconcile_scan_seq FROM external_operation FORCE INDEX (external_reconcile_scan) ORDER BY reconcile_scan_seq DESC LIMIT 1",(r,n)->r.getLong(1));
    if(maximum.isEmpty())return Optional.empty();
    long upper=maximum.getFirst();if(upper<=0)throw new IllegalStateException("Invalid reconciliation scan ordinal");
    scanCursor.after=0;scanCursor.upper=upper;scanCursor.active=true;
   }
   int consumed=0;
   while(consumed<scanBudget.maxRowsPerCall()){
    int limit=Math.min(scanBudget.pageSize(),scanBudget.maxRowsPerCall()-consumed);
    var page=j.query("SELECT reconcile_scan_seq,id FROM external_operation FORCE INDEX (external_reconcile_scan) WHERE reconcile_scan_seq > ? AND reconcile_scan_seq <= ? ORDER BY reconcile_scan_seq LIMIT ?",
      (r,n)->new ScanCandidate(r.getLong(1),r.getString(2)),scanCursor.after,scanCursor.upper,limit);
    if(page.isEmpty()){scanCursor.active=false;return Optional.empty();}
    for(int position=0;position<page.size();position++){
     var candidate=page.get(position);
     if(candidate.sequence()<=scanCursor.after||candidate.sequence()>scanCursor.upper)throw new IllegalStateException("Invalid reconciliation scan order");
     // Consume before the independent transaction: held/unknown/error rows cannot pin the head.
     // A failed commit returns no authority, and this position is revisited on the next pass.
     scanCursor.after=candidate.sequence();consumed++;
     var answer=claimCandidate(candidate,owner);
     boolean finished=scanCursor.after==scanCursor.upper||(page.size()<limit&&position==page.size()-1);
     if(finished)scanCursor.active=false;
     if(answer.isPresent())grantLookup(answer.orElseThrow());
     if(answer.isPresent()||finished)return answer;
     // Unconsumed prefetched rows are discarded on success and refetched next call.
    }
   }
   return Optional.empty();
  } finally {scanLock.unlock();}
 }
 private Optional<ScanOperation> scanOperation(String id,boolean lock){
  return j.query("SELECT *, (next_check_at<=UTC_TIMESTAMP(6) AND reconcile_expires_at<=UTC_TIMESTAMP(6)) AS scan_due FROM external_operation WHERE id=?"+(lock?" FOR UPDATE SKIP LOCKED":""),
    (r,n)->new ScanOperation(op(r,n),r.getLong("reconcile_scan_seq"),instant(r,"next_check_at"),r.getBoolean("scan_due")),id).stream().findFirst();
 }
 private static boolean observable(String state){return state.equals("MARKED")||state.equals("UNKNOWN");}
 private Optional<OperationToken> claimCandidate(ScanCandidate candidate,String owner){return q.work(()->{
  var initial=scanOperation(candidate.operationId(),false);if(initial.isEmpty())return Optional.empty();var discovered=initial.orElseThrow();var seen=discovered.operation();
  if(discovered.sequence()!=candidate.sequence())throw new IllegalStateException("Reconciliation scan ordinal changed");
  if(!observable(seen.state())||!discovered.due())return Optional.empty();
  var current=execution(seen.id(),seen.execution(),false,false);if(current.isEmpty())return Optional.empty();var seenExecution=current.orElseThrow();
  var task=q.row(seenExecution.task(),false,false);if(task.isEmpty())return Optional.empty();var seenTask=task.orElseThrow();
  if(!q.policies.containsKey(seenTask.spec().type()))return Optional.empty();
  var claimPurpose=q.policy(seenTask.spec()) instanceof RecordedFactRecoveryPolicy?TaskDomainPolicy.Acquisition.EXTERNAL_FACT_OBSERVATION:TaskDomainPolicy.Acquisition.TASK_MUTATION;
   var scope=q.prepareDomain(List.of(seenTask.spec()),claimPurpose,true,null);if(scope.isEmpty())return Optional.empty();
  if(q.lockDomain(scope.orElseThrow(),seenTask.spec(),true).isEmpty())return Optional.empty();
  var heldTask=q.row(seenTask.id(),true,true);if(heldTask.isEmpty()||!heldTask.orElseThrow().spec().equals(seenTask.spec()))return Optional.empty();
  q.policy(heldTask.orElseThrow().spec()).validateInput(heldTask.orElseThrow().spec());
  var locked=scanOperation(seen.id(),true);if(locked.isEmpty())return Optional.empty();var latest=locked.orElseThrow();var operation=latest.operation();
  if(latest.sequence()!=candidate.sequence())throw new IllegalStateException("Reconciliation scan ordinal changed");
  if(operation.execution()!=seen.execution()||!operation.subject().equals(seen.subject())||!operation.subject().equals(heldTask.orElseThrow().spec().subject()))return Optional.empty();
  var heldExecution=execution(operation.id(),operation.execution(),true,true);if(heldExecution.isEmpty())return Optional.empty();var execution=heldExecution.orElseThrow();
  if(!execution.id().equals(seenExecution.id())||!execution.operation().equals(operation.id())||execution.number()!=operation.execution()||!execution.task().equals(heldTask.orElseThrow().id()))return Optional.empty();
  Instant now=q.now();
  if(!observable(operation.state())||!observable(execution.state())||operation.expiry()==null||latest.nextCheck()==null||operation.expiry().isAfter(now)||latest.nextCheck().isAfter(now))return Optional.empty();
  Instant expiry=now.plusSeconds(20);
  j.update("UPDATE external_operation SET reconcile_epoch=reconcile_epoch+1,reconcile_owner=?,reconcile_expires_at=?,next_check_at=? WHERE id=?",owner,sqlTime(expiry),sqlTime(expiry),operation.id());
  return Optional.of(new OperationToken(operation.id(),execution.id(),operation.execution(),operation.epoch()+1,owner,expiry,operation.subject()));
 });}
 @Override public boolean renew(OperationToken token){independent();return q.work(()->{
  var seen=operation(token.operationId(),false,false);if(seen.isEmpty())return false;var rows=lock(seen.orElseThrow(),false);if(rows.isEmpty())return false;var l=rows.orElseThrow();if(!valid(l.op(),l.execution(),token))return false;
  j.update("UPDATE external_operation SET reconcile_expires_at=UTC_TIMESTAMP(6)+INTERVAL 20 SECOND,next_check_at=UTC_TIMESTAMP(6)+INTERVAL 20 SECOND WHERE id=?",token.operationId());return true;
 });}
 private static final int OBSERVATION_DISCOVERY_ATTEMPTS=4;
 private static final class RediscoverObservation extends RuntimeException {}
 @Override public ObservationResult observe(OperationToken token,ExternalObservation observation,Runnable effects){
  independent();revokeCallAuthority(token);
  for(int attempt=0;attempt<OBSERVATION_DISCOVERY_ATTEMPTS;attempt++){
   try{return q.work(()->observeCurrent(token,observation,effects));}
   catch(RediscoverObservation changed){
    // q.work has rolled back and released every subject/task/operation lock and the RR ReadView.
    // Never discover and lock a newly current task while retaining the previous operation lock.
   }
  }
  throw new ObservationRetryRequiredException();
 }
 @Override public ObservationResult observePrepared(OperationToken token,PreparedObservation observation){return observePrepared(token,observation,false,false);}
 @Override public ObservationResult observePreparedFact(OperationToken token,PreparedObservation observation){return observePrepared(token,observation,true,false);}
 @Override public ObservationResult recoverPreparedFact(OperationToken token,PreparedObservation observation){return observePrepared(token,observation,true,true);}
 private ObservationResult observePrepared(OperationToken token,PreparedObservation prepared,boolean factOnly,boolean recovery){
  independent();Objects.requireNonNull(prepared);revokeCallAuthority(token);releaseRecordedLookup(token);
  for(int attempt=0;attempt<OBSERVATION_DISCOVERY_ATTEMPTS;attempt++)try{return q.work(()->observeCurrent(token,null,null,prepared,factOnly,recovery));}catch(RediscoverObservation changed){}
  throw new ObservationRetryRequiredException();
 }
 private ObservationResult observeCurrent(OperationToken token,ExternalObservation observation,Runnable effects){return observeCurrent(token,observation,effects,null,false,false);}
 private ObservationResult observeCurrent(OperationToken token,ExternalObservation observation,Runnable effects,PreparedObservation prepared,boolean factOnly,boolean recovery){
  var seen=operation(token.operationId(),false,false);if(seen.isEmpty())return ObservationResult.STALE_TOKEN;
  // Lock ALL involved tasks before operation when handling an old-generation contradiction.
  var current=seen.orElseThrow();var old=execution(current.id(),token.executionNo(),false,false);
  if(old.isEmpty()||!old.orElseThrow().id().equals(token.executionId())||!current.subject().equals(token.subject()))return ObservationResult.STALE_TOKEN;
  var currentEx=execution(current.id(),current.execution(),false,false).orElseThrow();
  var currentTask=q.row(currentEx.task(),false,false).orElseThrow();var oldTask=q.row(old.orElseThrow().task(),false,false).orElseThrow();
  var scope=q.prepareDomain(List.of(currentTask.spec(),oldTask.spec()),TaskDomainPolicy.Acquisition.EXTERNAL_FACT_OBSERVATION,false,null)
    .orElseThrow(DomainPreparationUnavailableException::new);
  var domain=q.lockDomain(scope,currentTask.spec(),false).orElseThrow(DomainPreparationUnavailableException::new);
  var heldTasks=new HashMap<String,JdbcTaskQueue.Row>();
  for(String id:new TreeSet<>(List.of(currentTask.id(),oldTask.id())))heldTasks.put(id,q.row(id,true,false).orElseThrow());
  var lockedCurrent=heldTasks.get(currentTask.id());var lockedOld=heldTasks.get(oldTask.id());
  if(!lockedCurrent.spec().equals(currentTask.spec())||!lockedOld.spec().equals(oldTask.spec()))throw new RediscoverObservation();
  currentTask=lockedCurrent;oldTask=lockedOld;
  var o=operation(current.id(),true,false).orElseThrow();
  if(o.execution()!=current.execution())throw new RediscoverObservation();
  var heldExecutions=new HashMap<Long,Execution>();
  for(long number:new TreeSet<>(List.of(o.execution(),token.executionNo())))
   heldExecutions.put(number,execution(o.id(),number,true,false).orElseThrow());
  var e=heldExecutions.get(o.execution());var tokenExecution=heldExecutions.get(token.executionNo());
  if(!e.task().equals(currentTask.id())||!tokenExecution.task().equals(oldTask.id()))throw new RediscoverObservation();
  if(!tokenExecution.id().equals(token.executionId())||!o.subject().equals(token.subject()))return ObservationResult.STALE_TOKEN;
  if(prepared==null&&scope.policy() instanceof PreparedExternalPolicy)throw new IllegalArgumentException("Prepared observation required");
  Context context=prepared==null?null:new Context(scope,oldTask,o,tokenExecution);
  try {
  if(context!=null){context.require();q.policy(oldTask.spec()).validateInput(oldTask.spec());observation=Objects.requireNonNull(preparedPolicy(oldTask.spec()).verifyRemoteFact(oldTask.spec(),context,prepared));context.require();}
  boolean effectsAllowed=scope.policy().permitsDomainEffects(scope.prepared());
  // A current authoritative fact can outlive business authority. Identity fencing cannot make
  // that fact permanently unrecordable merely because the disabled principal cannot renew.
  // Exact current execution and live observation states are still required; no lease is extended.
  boolean terminalFact=observation.outcome()==ExternalOutcome.SUCCEEDED||observation.outcome()==ExternalOutcome.FAILED_ZERO_EFFECT;
  boolean factOnlyCurrent=terminalFact&&!effectsAllowed&&o.execution()==token.executionNo()&&e.id().equals(token.executionId())
    &&(o.state().equals("MARKED")||o.state().equals("UNKNOWN"))&&(e.state().equals("MARKED")||e.state().equals("UNKNOWN"));
  // Verified proof contradiction precedes replay/conflict rejection for both current and older executions.
  if(observation.outcome()==ExternalOutcome.SUCCEEDED&&tokenExecution.state().equals("FAILED")
    &&tokenExecution.basis()!=null&&tokenExecution.evidence()!=null&&tokenExecution.verified()!=null){
   if(prepared==null)q.policy(oldTask.spec()).validateExternalObservation(oldTask.spec(),observation);
   j.update("UPDATE external_operation SET state='MANUAL_REQUIRED',last_error='CONTRADICTORY_SUCCESS',external_result_id=?,reconcile_epoch=reconcile_epoch+1 WHERE id=?",observation.externalResultId(),o.id());
   q.event("EXTERNAL_CONTRADICTION",oldTask);return ObservationResult.QUARANTINED;
  }
  // An explicit retry may advance the mutable operation summary before an old commit ACK arrives.
  // Recovery can acknowledge that immutable failure, but cannot borrow any successor authority.
  if(recovery&&prepared!=null&&observation.outcome()==ExternalOutcome.FAILED_ZERO_EFFECT
    &&o.execution()>token.executionNo()&&tokenExecution.state().equals("FAILED"))
   return acknowledgeOldFailure(scope,context,oldTask,tokenExecution,prepared,observation);
  if(prepared!=null&&terminalFact&&o.execution()==token.executionNo()&&(recovery||o.epoch()==token.reconcileEpoch()&&Objects.equals(o.owner(),token.owner()))
    &&e.id().equals(token.executionId())&&(o.state().equals("SUCCEEDED")||o.state().equals("FAILED"))&&o.state().equals(e.state())){
   var proof=observation.noEffectProof();
   boolean same=observation.outcome()==ExternalOutcome.SUCCEEDED?o.state().equals("SUCCEEDED")&&Objects.equals(observation.externalResultId(),j.queryForObject("SELECT external_result_id FROM external_operation WHERE id=?",String.class,o.id())):
      o.state().equals("FAILED")&&proof!=null&&Objects.equals(e.basis(),proof.basis().name())&&Objects.equals(e.evidence(),proof.safeEvidenceReference())&&Objects.equals(e.verified(),proof.verifiedAt());
   if(!same)return ObservationResult.STALE_TOKEN;
   var stored=j.queryForMap("SELECT state,last_error,output_kind,output_id FROM durable_task WHERE id=?",currentTask.id());
   var kind=(String)stored.get("output_kind");var result=new PreparedExternalPolicy.StoredResult((String)stored.get("state"),(String)stored.get("last_error"),kind==null?null:new TypedRef(kind,(String)stored.get("output_id")));
   if(!preparedPolicy(currentTask.spec()).verifyReplay(scope.prepared(),context,prepared,result))return ObservationResult.STALE_TOKEN;
   context.require();return result.state().equals("STALE")?ObservationResult.RECORDED_FENCED:ObservationResult.APPLIED;
  }
  if(recovery){
   // An old context never regains business authority. Only the same verified terminal receipt
   // can take over an expired exact execution; a live newer owner is left untouched.
   if(!terminalFact||o.execution()!=token.executionNo()||!e.id().equals(token.executionId())
     ||!(o.state().equals("MARKED")||o.state().equals("UNKNOWN"))||!(e.state().equals("MARKED")||e.state().equals("UNKNOWN"))
     ||o.expiry()==null||o.expiry().isAfter(q.now()))return ObservationResult.STALE_TOKEN;
   var now=q.now();
   j.update("UPDATE external_operation SET reconcile_epoch=reconcile_epoch+1,reconcile_owner=?,reconcile_expires_at=? WHERE id=?","RECEIPT_"+UUID.randomUUID(),sqlTime(now.plusSeconds(20)),o.id());
   o=operation(o.id(),true,false).orElseThrow();token=token(o,e);
  }
  if(!valid(o,e,token)&&!factOnlyCurrent)return ObservationResult.STALE_TOKEN;
  if(prepared==null)q.policy(currentTask.spec()).validateExternalObservation(currentTask.spec(),observation);
  if(observation.outcome()==ExternalOutcome.UNKNOWN||observation.outcome()==ExternalOutcome.MANUAL_REQUIRED){
   j.update("UPDATE external_operation SET state=?,last_error=?,next_check_at=reconcile_expires_at WHERE id=?",observation.outcome()==ExternalOutcome.UNKNOWN?"UNKNOWN":"MANUAL_REQUIRED",observation.errorCode(),o.id());
   j.update("UPDATE external_execution SET state='UNKNOWN' WHERE id=?",e.id());q.event("EXTERNAL_UNKNOWN",currentTask);return ObservationResult.APPLIED;
  }
  boolean canApply=!factOnly&&effectsAllowed&&valid(o,e,token)&&q.current(domain,currentTask.spec());
  TaskResult local=null;
  if(canApply){
   if(prepared==null){q.policy(currentTask.spec()).validateOutput(currentTask.spec(),new TaskResult(observation.outcome()==ExternalOutcome.SUCCEEDED?Outcome.SUCCEEDED:Outcome.FAILED,observation.output(),observation.errorCode()));effects.run();}
   else {
    context.require();local=Objects.requireNonNull(preparedPolicy(currentTask.spec()).writeCurrent(scope.prepared(),context,prepared));context.require();
    if(local.outcome()!=Outcome.SUCCEEDED&&local.outcome()!=Outcome.FAILED||local.outcome()==Outcome.SUCCEEDED&&observation.outcome()!=ExternalOutcome.SUCCEEDED
      ||local.outcome()==Outcome.FAILED&&observation.outcome()==ExternalOutcome.SUCCEEDED&&(!"INVALID_MODEL_OUTPUT".equals(local.errorCode())||local.output()!=null))throw new IllegalArgumentException("Invalid remote/local outcome mapping");
    q.policy(currentTask.spec()).validateOutput(currentTask.spec(),local);preparedPolicy(currentTask.spec()).validateStoredResult(scope.prepared(),context,prepared,local);context.require();
   }
  }
  if(!canApply&&!factOnly&&prepared!=null&&effectsAllowed&&valid(o,e,token)&&observation.outcome()==ExternalOutcome.SUCCEEDED
    &&!domain.deleted()&&domain.cancelEpoch()==currentTask.spec().cancelEpoch()&&domain.businessVersion()>currentTask.spec().businessVersion()
    &&scope.policy() instanceof GenerationRetentionPolicy retention&&currentTask.spec().type().equals("MINUTES_GENERATE_V1")&&currentTask.spec().input().kind().equals("GENERATION_INPUT")){
   context.retaining=true;
   try{var capability=retention.prepareRetention(scope.prepared(),context,prepared);
    if(capability.isPresent()){var held=capability.orElseThrow();JdbcGenerationRetention.consume(q.source,scope.prepared(),context,held);
     var candidate=retention.retainStale(held,prepared);retention.validateRetained(held,prepared,candidate);JdbcGenerationRetention.validate(q.source,scope.prepared(),context,held,candidate);}
   }finally{context.retaining=false;}
  }
  String state=observation.outcome()==ExternalOutcome.SUCCEEDED?"SUCCEEDED":"FAILED";
  var proof=observation.noEffectProof();
  j.update("UPDATE external_execution SET state=?,finished_at=UTC_TIMESTAMP(6),no_effect_basis=?,no_effect_evidence=?,no_effect_verified_at=? WHERE id=?",state,proof==null?null:proof.basis().name(),proof==null?null:proof.safeEvidenceReference(),proof==null?null:sqlTime(proof.verifiedAt()),e.id());
  j.update("UPDATE external_operation SET state=?,external_result_id=?,last_error=? WHERE id=?",state,observation.externalResultId(),observation.errorCode(),o.id());
  String localState=canApply?(local==null?state:local.outcome().name()):"STALE";
  String localError=canApply?(local==null?observation.errorCode():local.errorCode()):"BUSINESS_FENCED";
  var localOutput=canApply?(local==null?observation.output():local.output()):null;
  if(prepared!=null){
   if(!currentTask.state().equals("RECONCILING")||j.update("UPDATE task_attempt SET ended_at=UTC_TIMESTAMP(6),outcome=?,error_code=? WHERE task_id=? AND attempt=? AND owner=? AND ended_at IS NULL",localState,localError,currentTask.id(),currentTask.attempt(),currentTask.owner())!=1)throw new IllegalStateException("Current external attempt must end exactly once");
  }
  q.finish(currentTask,localState,localError,localOutput);
  return canApply?ObservationResult.APPLIED:ObservationResult.RECORDED_FENCED;
  } finally {if(context!=null)context.open=false;}
 }
 /** Fact-only lookup: both task/execution rows are already locked in the complete observation scope. */
 private ObservationResult acknowledgeOldFailure(JdbcTaskQueue.DomainScope scope,Context context,Row oldTask,
     Execution oldExecution,PreparedObservation prepared,ExternalObservation observation){
  var proof=observation.noEffectProof();
  if(proof==null||oldExecution.basis()==null||oldExecution.evidence()==null||oldExecution.verified()==null
    ||!oldExecution.basis().equals(proof.basis().name())||!oldExecution.evidence().equals(proof.safeEvidenceReference())
    ||!oldExecution.verified().equals(proof.verifiedAt()))return ObservationResult.STALE_TOKEN;
  // Locking reads remain current under RR and select the OLD task, never the successor's result.
  var stored=j.queryForMap("SELECT state,last_error,output_kind,output_id FROM durable_task WHERE id=? FOR UPDATE",oldTask.id());
  var kind=(String)stored.get("output_kind");
  var result=new PreparedExternalPolicy.StoredResult((String)stored.get("state"),(String)stored.get("last_error"),kind==null?null:new TypedRef(kind,(String)stored.get("output_id")));
  if(!result.state().equals("FAILED")&&!result.state().equals("STALE"))return ObservationResult.STALE_TOKEN;
  // The execution's exact marked attempt must be the old terminal task's attempt and already
  // ended with that same local result. Nothing here ends an attempt or rewrites a durable fact.
  var attempts=j.query("SELECT a.owner,a.ended_at,a.outcome,a.error_code,e.finished_at FROM external_execution e JOIN task_attempt a ON a.task_id=e.task_id AND a.attempt=e.task_attempt WHERE e.id=? AND a.task_id=? AND a.attempt=? FOR UPDATE",
      (r,n)->r.getString("owner")!=null&&r.getObject("ended_at")!=null&&r.getObject("finished_at")!=null
        &&result.state().equals(r.getString("outcome"))&&Objects.equals(result.errorCode(),r.getString("error_code")),
      oldExecution.id(),oldTask.id(),oldTask.attempt());
  if(attempts.size()!=1||!attempts.getFirst())return ObservationResult.STALE_TOKEN;
  if(!preparedPolicy(oldTask.spec()).verifyReplay(scope.prepared(),context,prepared,result))return ObservationResult.STALE_TOKEN;
  context.require();return result.state().equals("STALE")?ObservationResult.RECORDED_FENCED:ObservationResult.APPLIED;
 }
 @Override public String retryAfterZeroEffect(String id,long expected,TaskSpec next,ExternalRequest request){return q.work(()->{
  // Discover the COMPLETE immutable old/new spec set before identities, subject, roots or tasks.
  var seen=operation(id,false,false).orElseThrow();
  var ex=execution(id,expected,false,false).orElseThrow();var old=q.row(ex.task(),false,false).orElseThrow();
  var scope=q.prepareDomain(List.of(next,old.spec()),TaskDomainPolicy.Acquisition.TASK_MUTATION,false,null)
    .orElseThrow(DomainPreparationUnavailableException::new);
  q.lockDomain(scope,next,false).orElseThrow();q.policy(next).validateInput(next);q.policy(next).validateExternalRequest(next,request);
  // A locking read is current even when the preliminary discovery established an RR snapshot.
  var replay=j.query("SELECT * FROM durable_task WHERE producer_key=? FOR UPDATE",JdbcTaskQueue::read,next.producerKey());
  if(!replay.isEmpty()){
   var task=replay.getFirst();if(!task.spec().equals(next))throw new IllegalArgumentException("Retry producer conflicts with immutable spec");
   q.lockDomain(scope,next,false).orElseThrow();q.policy(next).validateInput(next);q.policy(next).validateExternalRequest(next,request);
   q.row(task.id(),true,false).orElseThrow();var o=operation(id,true,false).orElseThrow();
   var e=execution(id,expected+1,true,false).orElseThrow(()->new IllegalArgumentException("Retry producer belongs to another execution"));
   if(!e.task().equals(task.id())||!e.hash().equals(request.executionRequestHash())||!e.manifest().equals(request.requestManifest())||!o.subject().equals(next.subject())||!o.kind().equals(request.operationKind())||!o.key().equals(request.businessKey())||!o.hash().equals(request.logicalRequestHash()))throw new IllegalArgumentException("Retry replay conflicts with original acceptance");
   return task.id();
  }
  if(!seen.state().equals("FAILED"))throw new IllegalStateException("Only definitive zero-effect failure permits retry");
  if(!old.spec().subject().equals(next.subject())||old.spec().businessVersion()!=next.businessVersion()||old.spec().cancelEpoch()!=next.cancelEpoch()||!old.spec().input().equals(next.input())||!old.spec().inputFingerprint().equals(next.inputFingerprint()))throw new IllegalArgumentException("Retry requires same fixed input");
  var subject=q.lockDomain(scope,next,false).orElseThrow();if(!q.current(subject,next))throw new IllegalStateException("Retry is fenced");q.policy(next).validateInput(next);q.policy(next).validateExternalRequest(next,request);
  if(!request.operationKind().equals(seen.kind())||!request.businessKey().equals(seen.key())||!request.logicalRequestHash().equals(seen.hash()))throw new IllegalArgumentException("Logical effect cannot change");
  String task=UUID.randomUUID().toString();
  if(task.compareTo(old.id())<0){q.insertNew(next,task);q.row(old.id(),true,false).orElseThrow();}else{q.row(old.id(),true,false).orElseThrow();q.insertNew(next,task);}
  var o=operation(id,true,false).orElseThrow();var e=execution(id,expected,true,false).orElseThrow();
  if(o.execution()!=expected||!o.state().equals("FAILED")||!e.state().equals("FAILED")||e.basis()==null||e.evidence()==null||e.verified()==null)throw new IllegalStateException("Retry lacks definitive zero-effect evidence");
  j.update("INSERT INTO external_execution(id,operation_id,execution_no,task_id,state,request_hash,request_manifest,created_at) VALUES (?,?,?,?,'PREPARED',?,?,UTC_TIMESTAMP(6))",UUID.randomUUID().toString(),id,expected+1,task,request.executionRequestHash(),request.requestManifest());
  j.update("UPDATE external_operation SET state='RETRY_QUEUED',current_execution_no=?,reconcile_epoch=reconcile_epoch+1,reconcile_owner=NULL,reconcile_expires_at=NULL,last_error=NULL WHERE id=?",expected+1,id);
  return task;
 });}
}
