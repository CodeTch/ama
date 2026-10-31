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
class FeedbackPersistenceIT {
 DataSource source;JdbcTemplate j;JdbcFeedback feedback;MeetingService meetings;Actor actor;String meeting;
 @BeforeEach void start(){
  String url=System.getenv("AMA_C_RECORDING_DATABASE_URL");
  if(url==null||!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/ama_c_recording_[0-9a-f]{12}"))throw new IllegalArgumentException("Owned integration schema required");
  source=MysqlDataSources.fromEnvironment("AMA_C_RECORDING_DATABASE_");j=new JdbcTemplate(source);
  Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
  actor=new Actor("feedback-"+UUID.randomUUID(),Status.ACTIVE,Set.of(Role.USER));
  j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",actor.enterpriseId(),actor.enterpriseId());
  var tx=new TransactionRunner(source);var fences=new JdbcSubjectFences(source,Set.of("MEETING"));var access=new JdbcMeetingAccess(source,tx,fences);
  feedback=new JdbcFeedback(source,tx,access);meetings=new MeetingService(new JdbcMeetingRepository(source,tx,access,fences,new JdbcOutbox(source,new MeetingEventReferences(source).validators()),new JdbcAuditAdapter(source),id->{}));
  meeting=meetings.create(actor,UUID.randomUUID().toString(),UUID.randomUUID().toString()).id();
 }
 @Test void lostCreateResponseReplaysOnePersistedFeedbackAndRejectsChangedBody(){
  String key=UUID.randomUUID().toString();var first=feedback.create(actor,key,meeting,"WORDING","真实文本建议😀");
  assertEquals(first,feedback.create(actor,key,meeting,"WORDING","真实文本建议😀"));
  assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM feedback WHERE meeting_id=?",Integer.class,meeting));
  assertThrows(ama.contract.MeetingContracts.Conflict.class,()->feedback.create(actor,key,meeting,"WORDING","不同内容"));
  assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM feedback WHERE meeting_id=?",Integer.class,meeting));
  assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id=? AND action='FEEDBACK_SUBMITTED'",Integer.class,first.get("id")));
  j.update("UPDATE meeting SET lifecycle='DELETING' WHERE id=?",meeting);
  assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->feedback.create(actor,key,meeting,"WORDING","真实文本建议😀"));
 }
 @Test void paginationReturnsEveryOwnItemExactlyOnceAndRejectsForeignCursor(){
  for(int n=0;n<23;n++)feedback.create(actor,UUID.randomUUID().toString(),meeting,"WORDING","建议"+n);
  var one=feedback.list(actor,null);assertEquals(20,((List<?>)one.get("items")).size());assertNotNull(one.get("nextCursor"));
  var two=feedback.list(actor,(String)one.get("nextCursor"));assertEquals(3,((List<?>)two.get("items")).size());assertNull(two.get("nextCursor"));
  var ids=new HashSet<Object>();for(var page:List.of(one,two))for(var row:(List<?>)page.get("items"))assertTrue(ids.add(((Map<?,?>)row).get("id")));
  assertEquals(23,ids.size());assertThrows(IllegalArgumentException.class,()->feedback.list(actor,UUID.randomUUID().toString()));
 }
 @Test void invalidOrUnboundedFeedbackCannotCreateRows(){
  for(var value:List.of("", "   ", "x".repeat(4097)))assertThrows(IllegalArgumentException.class,()->feedback.create(actor,UUID.randomUUID().toString(),meeting,"WORDING",value));
  assertThrows(IllegalArgumentException.class,()->feedback.create(actor,UUID.randomUUID().toString(),meeting,"UNKNOWN","建议"));
  assertThrows(IllegalArgumentException.class,()->feedback.create(actor,"",meeting,"WORDING","建议"));
  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM feedback WHERE meeting_id=?",Integer.class,meeting));
 }
}
