package ama.task;

import ama.contract.TaskContracts.*;
import ama.contract.ExternalContracts.*;
import java.sql.*;
import java.lang.ref.WeakReference;
import java.time.Instant;
import java.util.*;
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
 record Operation(String id,String kind,String key,String hash,String state,SubjectRef subject,long execution,long epoch,String owner,Instant expiry){}
 record Execution(String id,String operation,long number,String task,String state,String hash,String manifest,String basis,String evidence,Instant verified){}
 public JdbcExternalOperations(JdbcTaskQueue queue){q=queue;j=queue.jdbc;}
 private static Operation op(ResultSet r,int n)throws SQLException{return new Operation(r.getString("id"),r.getString("operation_kind"),r.getString("business_key"),r.getString("request_hash"),r.getString("state"),new SubjectRef(r.getString("subject_kind"),r.getString("subject_id")),r.getLong("current_execution_no"),r.getLong("reconcile_epoch"),r.getString("reconcile_owner"),instant(r,"reconcile_expires_at"));}
 private static Execution ex(ResultSet r,int n)throws SQLException{return new Execution(r.getString("id"),r.getString("operation_id"),r.getLong("execution_no"),r.getString("task_id"),r.getString("state"),r.getString("request_hash"),r.getString("request_manifest"),r.getString("no_effect_basis"),r.getString("no_effect_evidence"),instant(r,"no_effect_verified_at"));}
 Optional<Operation> operation(String id,boolean lock,boolean skip){return j.query("SELECT * FROM external_operation WHERE id=?"+(lock?" FOR UPDATE"+(skip?" SKIP LOCKED":""):""),JdbcExternalOperations::op,id).stream().findFirst();}
 Optional<Execution> execution(String operation,long n,boolean lock,boolean skip){return j.query("SELECT * FROM external_execution WHERE operation_id=? AND execution_no=?"+(lock?" FOR UPDATE"+(skip?" SKIP LOCKED":""):""),JdbcExternalOperations::ex,operation,n).stream().findFirst();}
 boolean valid(Operation o,Execution e,OperationToken t){return o.subject().equals(t.subject())&&o.id().equals(t.operationId())&&o.execution()==t.executionNo()&&e.id().equals(t.executionId())&&e.number()==t.executionNo()&&o.epoch()==t.reconcileEpoch()&&Objects.equals(o.owner(),t.owner())&&o.expiry()!=null&&o.expiry().isAfter(q.now())&&(o.state().equals("MARKED")||o.state().equals("UNKNOWN"))&&(e.state().equals("MARKED")||e.state().equals("UNKNOWN"));}
 OperationToken token(Operation o,Execution e){return new OperationToken(o.id(),e.id(),o.execution(),o.epoch(),o.owner(),o.expiry(),o.subject());}
 @Override public Optional<OperationToken> beginExternal(TaskLease lease,ExternalRequest request){
  independent();
  // Exceptions from commit (including lost ACK) escape: no token and no send authority is returned.
  Optional<OperationToken> committed=q.work(()->{
   var valid=q.validLease(lease);if(valid.isEmpty())return Optional.empty();var task=valid.orElseThrow();q.policy(task.spec()).validateExternalRequest(task.spec(),request);
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
   j.update("UPDATE durable_task SET state='RECONCILING' WHERE id=?",task.id());q.event("EXTERNAL_MARKED",task);
   return Optional.of(new OperationToken(oid,eid,number,epoch,lease.leaseOwner(),expiry,task.spec().subject()));
  });
  committed.ifPresent(this::grantCallAuthority);return committed;
 }
 private record Locked(Operation op,Execution execution,JdbcTaskQueue.Row task,SubjectFencePort.LockedSubject subject){}
 private Optional<Locked> lock(Operation seen,boolean skip){
  var current=execution(seen.id(),seen.execution(),false,false);if(current.isEmpty())return Optional.empty();
  var task=q.row(current.orElseThrow().task(),false,false);if(task.isEmpty())return Optional.empty();
  var domain=q.lockDomain(task.orElseThrow().spec(),skip);if(domain.isEmpty())return Optional.empty();
  var heldTask=q.row(task.orElseThrow().id(),true,skip);if(heldTask.isEmpty())return Optional.empty();
  var op=operation(seen.id(),true,skip);if(op.isEmpty()||op.orElseThrow().execution()!=seen.execution())return Optional.empty();
  var ex=execution(seen.id(),seen.execution(),true,skip);if(ex.isEmpty()||!ex.orElseThrow().task().equals(heldTask.orElseThrow().id()))return Optional.empty();
  return Optional.of(new Locked(op.orElseThrow(),ex.orElseThrow(),heldTask.orElseThrow(),domain.orElseThrow()));
 }
 @Override public Optional<OperationToken> claimReconciliation(String owner){
  independent();ama.contract.OutboxContracts.protocolCode(owner);
  var candidates=j.query("SELECT * FROM external_operation WHERE state IN ('MARKED','UNKNOWN') AND next_check_at<=UTC_TIMESTAMP(6) AND reconcile_expires_at<=UTC_TIMESTAMP(6) ORDER BY next_check_at,id LIMIT 64",JdbcExternalOperations::op);
  for(var seen:candidates){var answer=q.work(()->{
   var rows=lock(seen,true);if(rows.isEmpty())return Optional.<OperationToken>empty();var l=rows.orElseThrow();var o=l.op();
   if(!(o.state().equals("MARKED")||o.state().equals("UNKNOWN"))||o.expiry()==null||o.expiry().isAfter(q.now()))return Optional.<OperationToken>empty();
   Instant expiry=q.now().plusSeconds(20);j.update("UPDATE external_operation SET reconcile_epoch=reconcile_epoch+1,reconcile_owner=?,reconcile_expires_at=?,next_check_at=? WHERE id=?",owner,sqlTime(expiry),sqlTime(expiry),o.id());
   return Optional.of(new OperationToken(o.id(),l.execution().id(),o.execution(),o.epoch()+1,owner,expiry,o.subject()));
  });if(answer.isPresent())return answer;}
  return Optional.empty();
 }
 @Override public boolean renew(OperationToken token){independent();return q.work(()->{
  var seen=operation(token.operationId(),false,false);if(seen.isEmpty())return false;var rows=lock(seen.orElseThrow(),false);if(rows.isEmpty())return false;var l=rows.orElseThrow();if(!valid(l.op(),l.execution(),token))return false;
  j.update("UPDATE external_operation SET reconcile_expires_at=UTC_TIMESTAMP(6)+INTERVAL 20 SECOND,next_check_at=UTC_TIMESTAMP(6)+INTERVAL 20 SECOND WHERE id=?",token.operationId());return true;
 });}
 @Override public ObservationResult observe(OperationToken token,ExternalObservation observation,Runnable effects){revokeCallAuthority(token);return q.work(()->{
  var seen=operation(token.operationId(),false,false);if(seen.isEmpty())return ObservationResult.STALE_TOKEN;
  // Lock ALL involved tasks before operation when handling an old-generation contradiction.
  var current=seen.orElseThrow();var old=execution(current.id(),token.executionNo(),false,false);
  if(old.isEmpty()||!old.orElseThrow().id().equals(token.executionId())||!current.subject().equals(token.subject()))return ObservationResult.STALE_TOKEN;
  var currentEx=execution(current.id(),current.execution(),false,false).orElseThrow();
  var currentTask=q.row(currentEx.task(),false,false).orElseThrow();var oldTask=q.row(old.orElseThrow().task(),false,false).orElseThrow();
  var domain=q.lockDomain(currentTask.spec(),false).orElseThrow();
  for(String id:new TreeSet<>(List.of(currentTask.id(),oldTask.id())))q.row(id,true,false).orElseThrow();
  var o=operation(current.id(),true,false).orElseThrow();if(o.execution()!=current.execution())return ObservationResult.STALE_TOKEN;
  var e=execution(o.id(),o.execution(),true,false).orElseThrow();
  if(!valid(o,e,token)){
   if(observation.outcome()==ExternalOutcome.SUCCEEDED&&old.orElseThrow().state().equals("FAILED")&&old.orElseThrow().basis()!=null){
    q.policy(oldTask.spec()).validateExternalObservation(oldTask.spec(),observation);
    j.update("UPDATE external_operation SET state='MANUAL_REQUIRED',last_error='CONTRADICTORY_SUCCESS',external_result_id=?,reconcile_epoch=reconcile_epoch+1 WHERE id=?",observation.externalResultId(),o.id());
    q.event("EXTERNAL_CONTRADICTION",oldTask);return ObservationResult.QUARANTINED;
   }
   return ObservationResult.STALE_TOKEN;
  }
  q.policy(currentTask.spec()).validateExternalObservation(currentTask.spec(),observation);
  if(observation.outcome()==ExternalOutcome.UNKNOWN||observation.outcome()==ExternalOutcome.MANUAL_REQUIRED){
   j.update("UPDATE external_operation SET state=?,last_error=?,next_check_at=reconcile_expires_at WHERE id=?",observation.outcome()==ExternalOutcome.UNKNOWN?"UNKNOWN":"MANUAL_REQUIRED",observation.errorCode(),o.id());
   j.update("UPDATE external_execution SET state='UNKNOWN' WHERE id=?",e.id());q.event("EXTERNAL_UNKNOWN",currentTask);return ObservationResult.APPLIED;
  }
  boolean canApply=q.current(domain,currentTask.spec());
  if(canApply){q.policy(currentTask.spec()).validateOutput(currentTask.spec(),new TaskResult(observation.outcome()==ExternalOutcome.SUCCEEDED?Outcome.SUCCEEDED:Outcome.FAILED,observation.output(),observation.errorCode()));effects.run();}
  String state=observation.outcome()==ExternalOutcome.SUCCEEDED?"SUCCEEDED":"FAILED";
  var proof=observation.noEffectProof();
  j.update("UPDATE external_execution SET state=?,finished_at=UTC_TIMESTAMP(6),no_effect_basis=?,no_effect_evidence=?,no_effect_verified_at=? WHERE id=?",state,proof==null?null:proof.basis().name(),proof==null?null:proof.safeEvidenceReference(),proof==null?null:sqlTime(proof.verifiedAt()),e.id());
  j.update("UPDATE external_operation SET state=?,external_result_id=?,last_error=? WHERE id=?",state,observation.externalResultId(),observation.errorCode(),o.id());
  q.finish(currentTask,canApply?state:"STALE",canApply?observation.errorCode():"BUSINESS_FENCED",canApply?observation.output():null);
  return canApply?ObservationResult.APPLIED:ObservationResult.RECORDED_FENCED;
 });}
 @Override public String retryAfterZeroEffect(String id,long expected,TaskSpec next,ExternalRequest request){return q.work(()->{
  // Serialize the subject before creating a consistent-read snapshot or looking up the producer.
  q.lockDomain(next,false).orElseThrow();q.policy(next).validateInput(next);q.policy(next).validateExternalRequest(next,request);
  var replay=j.query("SELECT * FROM durable_task WHERE producer_key=?",JdbcTaskQueue::read,next.producerKey());
  if(!replay.isEmpty()){
   var task=replay.getFirst();if(!task.spec().equals(next))throw new IllegalArgumentException("Retry producer conflicts with immutable spec");
   q.lockDomain(next,false).orElseThrow();q.policy(next).validateInput(next);q.policy(next).validateExternalRequest(next,request);
   q.row(task.id(),true,false).orElseThrow();var o=operation(id,true,false).orElseThrow();
   var e=execution(id,expected+1,true,false).orElseThrow(()->new IllegalArgumentException("Retry producer belongs to another execution"));
   if(!e.task().equals(task.id())||!e.hash().equals(request.executionRequestHash())||!e.manifest().equals(request.requestManifest())||!o.subject().equals(next.subject())||!o.kind().equals(request.operationKind())||!o.key().equals(request.businessKey())||!o.hash().equals(request.logicalRequestHash()))throw new IllegalArgumentException("Retry replay conflicts with original acceptance");
   return task.id();
  }
  var seen=operation(id,false,false).orElseThrow();if(!seen.state().equals("FAILED"))throw new IllegalStateException("Only definitive zero-effect failure permits retry");var ex=execution(id,expected,false,false).orElseThrow();var old=q.row(ex.task(),false,false).orElseThrow();
  if(!old.spec().subject().equals(next.subject())||old.spec().businessVersion()!=next.businessVersion()||old.spec().cancelEpoch()!=next.cancelEpoch()||!old.spec().input().equals(next.input())||!old.spec().inputFingerprint().equals(next.inputFingerprint()))throw new IllegalArgumentException("Retry requires same fixed input");
  var subject=q.lockDomain(next,false).orElseThrow();if(!q.current(subject,next))throw new IllegalStateException("Retry is fenced");q.policy(next).validateInput(next);q.policy(next).validateExternalRequest(next,request);
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
