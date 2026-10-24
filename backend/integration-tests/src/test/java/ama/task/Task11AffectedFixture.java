package ama.task;
import ama.contract.TaskContracts.*;
import ama.contract.ExternalContracts.*;
import ama.contract.OutboxContracts.*;
import ama.db.TransactionRunner;
import ama.support.MysqlTestDatabase;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
/** Test-only policy against actual reviewed meeting and immutable info revision tables. */
class Task11AffectedFixture {
 final String runId=UUID.randomUUID().toString();
 final DataSource source=ama.publication.Task11Source.open();
 final JdbcTemplate j=new JdbcTemplate(source);
 final TransactionRunner tx=new TransactionRunner(source);
 final JdbcSubjectFences fences=new JdbcSubjectFences(source,Set.of("MEETING"));
 final TaskDomainPolicy policy=new TaskDomainPolicy(){
  final JdbcTaskPreparations preparations=new JdbcTaskPreparations(source);
  public java.util.Optional<PreparedDomain> prepare(java.util.List<TaskSpec> specs,Acquisition purpose,boolean skip){return java.util.Optional.of(preparations.issue(specs,purpose,null,true));}
  public void requirePrepared(java.util.List<TaskSpec> specs,Acquisition purpose,PreparedDomain held){preparations.require(specs,purpose,held);}
  public boolean permitsDomainEffects(PreparedDomain held){return preparations.permitsEffects(held);}

  public boolean lockRoot(TaskSpec s,boolean skip){
   return !j.queryForList("SELECT id FROM meeting WHERE id=? FOR UPDATE"+(skip?" SKIP LOCKED":""),String.class,s.subject().id()).isEmpty();
  }
  public void validateInput(TaskSpec s){
   if(!s.subject().kind().equals("MEETING") || !s.input().kind().equals("TEST_INFO"))throw new IllegalArgumentException("Unregistered test input");
   var rows=j.queryForList("SELECT meeting_id,revision FROM meeting_info_revision WHERE id=?",s.input().id());
   if(rows.size()!=1 || !rows.getFirst().get("meeting_id").equals(s.subject().id()) || ((Number)rows.getFirst().get("revision")).longValue()!=s.businessVersion() || !s.inputFingerprint().equals(hash(s.input().id())))throw new IllegalArgumentException("Input provenance mismatch");
  }
  public void validateOutput(TaskSpec s,TaskResult result){if(result.output()!=null && !result.output().equals(s.input()))throw new IllegalArgumentException("Wrong output provenance");}
  public void validateExternalObservation(TaskSpec s,ExternalObservation o){
   if(o.outcome()==ExternalOutcome.FAILED_ZERO_EFFECT && !o.noEffectProof().safeEvidenceReference().equals("TEST_VERIFIED_ZERO"))throw new IllegalArgumentException("Unverified proof");
   if(o.outcome()==ExternalOutcome.SUCCEEDED && !o.externalResultId().startsWith("TEST_RESULT:"))throw new IllegalArgumentException("Unverified external fact");
  }
  public void validateExternalRequest(TaskSpec s,ExternalRequest request){
   if(!request.operationKind().equals("TEST_CALL") || !request.requestManifest().equals("test-v1:"+s.input().id()))throw new IllegalArgumentException("Unknown manifest");
  }
 };
 final JdbcOutbox outbox=new JdbcOutbox(source,Map.of("TEST_INFO",e->{
  var rows=j.queryForList("SELECT meeting_id,revision FROM meeting_info_revision WHERE id=?",e.reference().id());
  if(rows.size()!=1 || !rows.getFirst().get("meeting_id").equals(e.subject().id()) || ((Number)rows.getFirst().get("revision")).longValue()!=e.subjectRevision())throw new IllegalArgumentException("Wrong event input");
 }));
 JdbcTaskQueue queue(){return new JdbcTaskQueue(source,fences,outbox,Map.of("T07_INHERITED_TASK",policy));}
 TaskSpec spec(){return spec("TEST_POOL",3);}
 TaskSpec spec(String pool,int attempts){
  String id=UUID.randomUUID().toString(),input=UUID.randomUUID().toString(),owner="t07-inherited-"+UUID.randomUUID();
  var subject=new SubjectRef("MEETING",id);
  tx.required(()->{
   j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",owner,owner);
   fences.create(subject,0,0);
   j.update("INSERT INTO meeting(id,owner_id,stage,created_at,updated_at) VALUES (?,?,'ENTRY',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",id,owner);
   j.update("INSERT INTO meeting_info_revision(id,meeting_id,revision,name,created_by,created_at) VALUES (?,?,0,'test',?,UTC_TIMESTAMP(6))",input,id,owner);
   return null;
  });
  return new TaskSpec("T07_INHERITED_TASK",pool+"_"+runId,subject,hash(UUID.randomUUID().toString()),new TypedRef("TEST_INFO",input),hash(input),0,0,0,attempts);
 }
 static String hash(String s){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new RuntimeException(e);}}
 TaskLease claimed(JdbcTaskQueue q,TaskSpec s){q.enqueue(s);var lease=q.claim(s.pool(),"worker-a");assertTrue(lease.isPresent(),"enqueued task must be claimed");return lease.orElseThrow();}
 TaskResult success(TaskSpec s){return new TaskResult(Outcome.SUCCEEDED,s.input(),null);}
 void expire(TaskLease l){j.update("UPDATE durable_task SET expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",l.taskId());}
 void advance(TaskSpec s,long version,long epoch,boolean deleted){tx.required(()->{var held=fences.lock(s.subject(),false).orElseThrow();policy.lockRoot(s,false);j.update("UPDATE meeting SET revision=?,cancel_epoch=?,lifecycle=? WHERE id=?",version,epoch,deleted?"DELETED":"ACTIVE",s.subject().id());fences.advance(held,version,epoch,deleted);return null;});}
 long cursor(){return j.queryForObject("SELECT next_id-1 FROM task_outbox_sequence WHERE id=1",Long.class);}
}
