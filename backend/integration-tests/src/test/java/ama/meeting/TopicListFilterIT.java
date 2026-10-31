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
class TopicListFilterIT {
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
 private JdbcWebMeetingProjectionReader reader(){var tx=new TransactionRunner(source);var access=new JdbcMeetingAccess(source,tx,new JdbcSubjectFences(source,Set.of("MEETING")));return new JdbcWebMeetingProjectionReader(source,tx,access,new WorkspaceCursor(),WorkspaceReadBudget.DEFAULT);}
 private MeetingListFilter filter(String topic){return new MeetingListFilter("",Set.of(),Set.of(),null,null,topic);}
 @Test void realOwnedMembershipFilterAdvancesEmptyPagesAndBindsCursorToTopic(){
  String topic=(String)topics.create(actor,UUID.randomUUID().toString(),"筛选专题",null).get("id");topics.assign(actor,meeting,topic,UUID.randomUUID().toString(),0);String second=meetings.create(actor,UUID.randomUUID().toString(),UUID.randomUUID().toString()).id();
  // Only the list-order timestamps of this test's new genuine meetings are controlled.
  j.update("UPDATE meeting SET updated_at='2026-10-31 08:00:00.000001' WHERE id=?",meeting);j.update("UPDATE meeting SET updated_at='2026-10-31 08:00:00.000002' WHERE id=?",second);
  var reader=reader();var first=reader.list(actor,"owned",1,null,filter(topic));assertTrue(first.items().isEmpty());assertTrue(first.hasMore());assertNotNull(first.nextCursor());var next=reader.list(actor,"owned",1,first.nextCursor(),filter(topic));assertEquals(List.of(meeting),next.items().stream().map(ama.contract.WorkspaceContracts.MeetingItem::id).toList());assertFalse(next.hasMore());
  assertThrows(IllegalArgumentException.class,()->reader.list(actor,"owned",1,first.nextCursor(),filter("__none")));assertEquals(List.of(second),reader.list(actor,"owned",10,null,filter("__none")).items().stream().map(ama.contract.WorkspaceContracts.MeetingItem::id).toList());assertThrows(IllegalArgumentException.class,()->reader.list(actor,"joined",10,null,filter(topic)));
 }
 @Test void personalTopicFilterCannotProbeAnotherOwnersTopic(){
  var other=new Actor("filter-other-"+UUID.randomUUID(),Status.ACTIVE,Set.of(Role.USER));j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",other.enterpriseId(),other.enterpriseId());String foreign=(String)topics.create(other,UUID.randomUUID().toString(),"私有专题",null).get("id");assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->reader().list(actor,"owned",10,null,filter(foreign)));
 }
 @Test void legacyCursorScopeIsUnchangedAndNewTopicScopesAreDistinct(){
  assertEquals("d651e8bf9b2fdf71e1c1ff7e3003a4578cf541357c4978e92c8438815cbf5a25",MeetingListFilter.NONE.cursorKey());assertEquals(MeetingListFilter.NONE,new MeetingListFilter("",Set.of(),Set.of(),null,null));assertNotEquals(filter("__none").cursorKey(),MeetingListFilter.NONE.cursorKey());assertNotEquals(filter("__none").cursorKey(),filter(meeting).cursorKey());assertThrows(IllegalArgumentException.class,()->filter("bad-topic"));
 }
}
