package ama.meeting;

import ama.audit.JdbcAuditAdapter;
import ama.contract.IdentityContracts.*;
import ama.db.*;
import ama.task.*;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Fresh append-only fixtures in the owned C development schema; genuine meeting creator. */
class TopicPersistenceIT {
 DataSource source;JdbcTemplate j;JdbcTopics topics;MeetingService meetings;Actor actor;String meeting;
 @BeforeEach void start(){
  String url=System.getenv("AMA_C_RECORDING_DATABASE_URL");
  if(url==null||!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/ama_c_recording_[0-9a-f]{12}"))throw new IllegalArgumentException("Owned integration schema required");
  source=MysqlDataSources.fromEnvironment("AMA_C_RECORDING_DATABASE_");j=new JdbcTemplate(source);
  Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
  actor=new Actor("topics-"+UUID.randomUUID(),Status.ACTIVE,Set.of(Role.USER));
  j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",actor.enterpriseId(),actor.enterpriseId());
  var tx=new TransactionRunner(source);var fences=new JdbcSubjectFences(source,Set.of("MEETING"));var access=new JdbcMeetingAccess(source,tx,fences);
  topics=new JdbcTopics(source,tx,access);meetings=new MeetingService(new JdbcMeetingRepository(source,tx,access,fences,new JdbcOutbox(source,new MeetingEventReferences(source).validators()),new JdbcAuditAdapter(source),id->{}));
  meeting=meetings.create(actor,UUID.randomUUID().toString(),UUID.randomUUID().toString()).id();
 }
 @Test void originalCreateAndUpdateKeysReturnImmutableAcceptedReceipts(){
  String key=UUID.randomUUID().toString();var first=topics.create(actor,key,"原专题","说明");String id=(String)first.get("id");
  assertEquals(first,topics.create(actor,key,"原专题","说明"));assertThrows(ama.contract.MeetingContracts.Conflict.class,()->topics.create(actor,key,"其他专题","说明"));
  String update=UUID.randomUUID().toString();var changed=topics.update(actor,id,update,0,"修改名称","说明二");
  assertEquals(changed,topics.update(actor,id,update,0,"修改名称","说明二"));assertEquals(first,topics.create(actor,key,"原专题","说明"));
  assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM topic WHERE owner_id=?",Integer.class,actor.enterpriseId()));assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id=? AND action='TOPIC_CREATED'",Integer.class,id));
  assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id=? AND action='TOPIC_UPDATED'",Integer.class,id));
 }
 @Test void deletionReplaysAfterRowIsGoneAndForeignOwnersCannotReadOrMutate(){
  var created=topics.create(actor,UUID.randomUUID().toString(),"空专题",null);String id=(String)created.get("id"),key=UUID.randomUUID().toString();var deleted=topics.delete(actor,id,key,0);assertEquals(deleted,topics.delete(actor,id,key,0));
  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM topic WHERE id=?",Integer.class,id));
  String remaining=(String)topics.create(actor,UUID.randomUUID().toString(),"个人专题",null).get("id");var other=new Actor("topic-other-"+UUID.randomUUID(),Status.ACTIVE,Set.of(Role.USER));j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",other.enterpriseId(),other.enterpriseId());assertTrue(topics.list(other).isEmpty());assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->topics.update(other,remaining,UUID.randomUUID().toString(),0,"越权",null));
 }
 @Test void unsupportedOrOversizedInputsCreateNoRows(){
  for(String name:List.of("", "   ","x".repeat(256)))assertThrows(IllegalArgumentException.class,()->topics.create(actor,UUID.randomUUID().toString(),name,null));
  assertThrows(IllegalArgumentException.class,()->topics.create(actor,UUID.randomUUID().toString(),"有效名称","x".repeat(4097)));assertThrows(IllegalArgumentException.class,()->topics.create(actor,"","有效名称",null));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM topic WHERE owner_id=?",Integer.class,actor.enterpriseId()));
 }
}
