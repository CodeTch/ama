package ama.task;

import ama.access.MeetingAccess;
import ama.contract.TaskContracts.*;
import ama.contract.ExternalContracts.*;
import ama.contract.IdentityContracts.*;
import ama.db.*;
import ama.meeting.JdbcMeetingAccess;
import java.util.*;
import java.sql.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** Forward-only exact Task8 fixture. Real meeting access, typed inputs, queue leases and markers. */
final class Task08Fixture {
 final Task08ProbeSource source;
 final JdbcTemplate j;final TransactionRunner tx;final JdbcSubjectFences fences;final JdbcMeetingAccess access;
 final JdbcOutbox outbox;final Policy policy;
 final String family;
 final List<String> affected=new ArrayList<>();
 Task08Fixture(String target,String family){this(target,family,15);}
 Task08Fixture(String target,String family,int expectedHistory){this.family=family;source=new Task08ProbeSource(dataSource(target));j=new JdbcTemplate(source);tx=new TransactionRunner(source);fences=new JdbcSubjectFences(source,Set.of("MEETING"));access=new JdbcMeetingAccess(source,tx,fences);policy=new Policy();
  assertEquals(expectedHistory,j.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1",Integer.class),"Forward15 history");
  outbox=new JdbcOutbox(source,Map.of("T08_INFO",e->{var rows=j.queryForList("SELECT meeting_id,revision FROM meeting_info_revision WHERE id=?",e.reference().id());if(rows.size()!=1||!rows.getFirst().get("meeting_id").equals(e.subject().id())||((Number)rows.getFirst().get("revision")).longValue()!=e.subjectRevision())throw new IllegalArgumentException("Event provenance");}));
 }
 static DataSource dataSource(String target){
  String url=System.getenv("AMA_TASK08_"+target.toUpperCase()+"_DATABASE_URL");
  if(url==null||!url.equals("jdbc:mysql://127.0.0.1:44727/ama_t08_848e93aace8b_"+target)||!Set.of("behavior","fairheld","fairunregistered","overflow").contains(target))throw new IllegalArgumentException("Exact guarded Task8 target required");
  var s=new DriverManagerDataSource(url,System.getenv("AMA_TEST_DATABASE_USERNAME"),System.getenv("AMA_TEST_DATABASE_PASSWORD"));
  var props=new Properties();props.setProperty("connectionTimeZone","UTC");props.setProperty("forceConnectionTimeZoneToSession","true");props.setProperty("characterEncoding","UTF-8");props.setProperty("connectionCollation","utf8mb4_0900_ai_ci");props.setProperty("connectTimeout","3000");props.setProperty("socketTimeout","15000");props.setProperty("sessionVariables","lock_wait_timeout=5,innodb_lock_wait_timeout=5");s.setConnectionProperties(props);return s;
 }
 final class Policy implements TaskDomainPolicy {
  final JdbcTaskPreparations preparations=new JdbcTaskPreparations(source);
  volatile Runnable preparing=()->{};
  record Capture(MeetingAccess.LockedIdentities identities,Actor actor) {}
  public Optional<PreparedDomain> prepare(List<TaskSpec> specs,Acquisition purpose,boolean skip){
   preparing.run();if(specs.isEmpty()||specs.stream().anyMatch(s->!s.subject().equals(specs.getFirst().subject())))throw new IllegalArgumentException("Complete common subject");
   String owner=j.queryForObject("SELECT owner_id FROM meeting WHERE id=?",String.class,specs.getFirst().subject().id());Actor actor=new Actor(owner,Status.ACTIVE,Set.of(Role.USER));
   return access.tryLockIdentities(actor,affected,skip).map(ids->preparations.issue(specs,purpose,new Capture(ids,actor),true));
  }
  public void requirePrepared(List<TaskSpec> specs,Acquisition purpose,PreparedDomain held){preparations.require(specs,purpose,held);}
  public boolean permitsDomainEffects(PreparedDomain held){return preparations.permitsEffects(held);}
  public boolean lockRoot(TaskSpec s,boolean skip){throw new IllegalStateException("Authentic preparation required");}
  public boolean lockRoot(TaskSpec s,boolean skip,PreparedDomain held){
   var c=(Capture)preparations.value(held);var root=access.lockPrepared(c.identities(),s.subject().id(),skip);if(root.isEmpty())return false;
   access.requirePrepared(root.orElseThrow(),c.actor(),s.subject().id(),affected,MeetingAccess.Action.EDIT);
   return !j.queryForList("SELECT id FROM meeting_info_revision WHERE id=? FOR UPDATE"+(skip?" SKIP LOCKED":""),String.class,s.input().id()).isEmpty();
  }
  public void validateInput(TaskSpec s){
   var rows=j.queryForList("SELECT meeting_id,revision FROM meeting_info_revision WHERE id=?",s.input().id());
   if(!s.subject().kind().equals("MEETING")||!s.input().kind().equals("T08_INFO")||rows.size()!=1||!rows.getFirst().get("meeting_id").equals(s.subject().id())||((Number)rows.getFirst().get("revision")).longValue()!=s.businessVersion()||!s.inputFingerprint().equals(hash(s.input().id())))throw new IllegalArgumentException("Input provenance");
  }
  public void validateOutput(TaskSpec s,TaskResult r){if(r.output()!=null&&!r.output().equals(s.input()))throw new IllegalArgumentException("Output provenance");}
  public void validateExternalRequest(TaskSpec s,ExternalRequest r){if(!r.operationKind().equals("T08_CALL")||!r.requestManifest().equals("t08:"+s.input().id()))throw new IllegalArgumentException("Request provenance");}
  public void validateExternalObservation(TaskSpec s,ExternalObservation o){if(o.outcome()==ExternalOutcome.SUCCEEDED&&!o.externalResultId().equals("T08_RESULT:"+s.input().id()))throw new IllegalArgumentException("Result provenance");if(o.outcome()==ExternalOutcome.FAILED_ZERO_EFFECT&&!o.noEffectProof().safeEvidenceReference().equals("T08_ZERO"))throw new IllegalArgumentException("Proof provenance");}
 }
 JdbcTaskQueue queue(String...types){var map=new HashMap<String,TaskDomainPolicy>();for(String type:types)map.put(type,policy);return new JdbcTaskQueue(source,fences,outbox,map);}
 TaskSpec spec(String type){String meeting=UUID.randomUUID().toString(),input=UUID.randomUUID().toString(),owner="z-t08-"+UUID.randomUUID();var subject=new SubjectRef("MEETING",meeting);
  tx.required(()->{j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",owner,owner);fences.create(subject,0,0);j.update("INSERT INTO meeting(id,owner_id,stage,created_at,updated_at) VALUES (?,?,'ENTRY',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",meeting,owner);j.update("INSERT INTO meeting_info_revision(id,meeting_id,revision,name,created_by,created_at) VALUES (?,?,0,'Task8',?,UTC_TIMESTAMP(6))",input,meeting,owner);return null;});
  return new TaskSpec(type,"T08_"+UUID.randomUUID(),subject,hash(UUID.randomUUID().toString()),new TypedRef("T08_INFO",input),hash(input),0,0,0,3);
 }
 ExternalRequest request(TaskSpec s){return new ExternalRequest("T08_CALL",s.producerKey(),hash("logical"+s.input().id()),hash("execution"+s.input().id()),"t08:"+s.input().id());}
 OperationToken marker(TaskSpec s){var queue=queue(s.type());queue.enqueue(s);var lease=queue.claim(s.pool(),"T08_MARKER").orElseThrow();return new JdbcExternalOperations(queue).beginExternal(lease,request(s)).orElseThrow();}
 void due(String id){j.update("UPDATE external_operation SET next_check_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND,reconcile_expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",id);}
 String owner(String operation){return j.queryForObject("SELECT m.owner_id FROM meeting m JOIN external_operation o ON o.subject_id=m.id WHERE o.id=?",String.class,operation);}
 long seq(String id){return j.queryForObject("SELECT reconcile_scan_seq FROM external_operation WHERE id=?",Long.class,id);}
 List<String> ids(String type){return j.queryForList("SELECT o.id FROM external_operation o JOIN external_execution e ON e.operation_id=o.id AND e.execution_no=o.current_execution_no JOIN durable_task t ON t.id=e.task_id WHERE t.task_type=? ORDER BY o.reconcile_scan_seq",String.class,type);}
 static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 String skippedSnapshot(String excludedOperation)throws Exception{
  MessageDigest digest=MessageDigest.getInstance("SHA-256");try(var c=source.getConnection()){
   for(String table:List.of("external_operation","external_execution","durable_task","task_attempt","task_subject","meeting","meeting_info_revision","enterprise_identity","task_outbox","task_outbox_sequence")){
    String order=switch(table){case "task_outbox"->"sequence_id";case "task_attempt"->"task_id,attempt";case "task_subject"->"subject_kind,subject_id";case "enterprise_identity"->"enterprise_id";default->"id";};
    try(var st=c.prepareStatement("SELECT * FROM "+table+(table.equals("external_operation")?" WHERE id<>?":"")+" ORDER BY "+order)){if(table.equals("external_operation"))st.setString(1,excludedOperation);try(var r=st.executeQuery()){while(r.next()){digest.update((byte)2);for(int i=1;i<=r.getMetaData().getColumnCount();i++){byte[] b=r.getBytes(i);digest.update((byte)(b==null?0:1));if(b!=null){digest.update(ByteBuffer.allocate(8).putLong(b.length).array());digest.update(b);}}}}}
   }
  }return HexFormat.of().formatHex(digest.digest());
 }
}
