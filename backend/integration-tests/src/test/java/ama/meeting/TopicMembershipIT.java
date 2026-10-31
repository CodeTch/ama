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
class TopicMembershipIT {
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
 @Test void assignmentIsOnePersonalTopicAndOriginalKeyDoesNotReapplyAnOldChoice(){
  String a=(String)topics.create(actor,UUID.randomUUID().toString(),"专题甲",null).get("id"),b=(String)topics.create(actor,UUID.randomUUID().toString(),"专题乙",null).get("id"),key=UUID.randomUUID().toString();
  assertThrows(ama.contract.MeetingContracts.Conflict.class,()->topics.assign(actor,meeting,a,UUID.randomUUID().toString(),1));assertNull(topics.memberships(actor,List.of(meeting)).getFirst().get("topicId"));
  topics.assign(actor,meeting,a,key,0);topics.assign(actor,meeting,a,key,0);assertEquals(a,topics.memberships(actor,List.of(meeting)).getFirst().get("topicId"));assertThrows(ama.contract.MeetingContracts.Conflict.class,()->topics.assign(actor,meeting,b,key,0));
  topics.assign(actor,meeting,b,UUID.randomUUID().toString(),0);topics.assign(actor,meeting,a,key,0);assertEquals(b,topics.memberships(actor,List.of(meeting)).getFirst().get("topicId"));assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM meeting_topic WHERE meeting_id=?",Integer.class,meeting));
  assertThrows(ama.contract.MeetingContracts.Conflict.class,()->topics.unassign(actor,meeting,a,UUID.randomUUID().toString(),0));String remove=UUID.randomUUID().toString();topics.unassign(actor,meeting,b,remove,0);topics.unassign(actor,meeting,b,remove,0);assertNull(topics.memberships(actor,List.of(meeting)).getFirst().get("topicId"));
  assertEquals(2,j.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id=? AND action='TOPIC_ASSIGNED'",Integer.class,meeting));assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id=? AND action='TOPIC_UNASSIGNED'",Integer.class,meeting));assertEquals(0,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,meeting));
 }
 @Test void batchChecksAllRevisionsBeforeWritingAndLeavesAclAndSourceVersionsUntouched(){
  String second=meetings.create(actor,UUID.randomUUID().toString(),UUID.randomUUID().toString()).id(),topic=(String)topics.create(actor,UUID.randomUUID().toString(),"批次专题",null).get("id");
  var grants=j.queryForList("SELECT * FROM meeting_access_grant WHERE meeting_id IN (?,?) ORDER BY meeting_id,enterprise_id",meeting,second);var bad=List.of(new JdbcTopics.MembershipTarget(meeting,0),new JdbcTopics.MembershipTarget(second,1));assertThrows(ama.contract.MeetingContracts.Conflict.class,()->topics.assignMany(actor,UUID.randomUUID().toString(),topic,bad));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM meeting_topic WHERE meeting_id IN (?,?)",Integer.class,meeting,second));
  var both=List.of(new JdbcTopics.MembershipTarget(second,0),new JdbcTopics.MembershipTarget(meeting,0));String key=UUID.randomUUID().toString();topics.assignMany(actor,key,topic,both);topics.assignMany(actor,key,topic,both);var rows=topics.memberships(actor,List.of(second,meeting));assertEquals(List.of(second,meeting),rows.stream().map(row->row.get("meetingId")).toList());assertTrue(rows.stream().allMatch(row->topic.equals(row.get("topicId"))));assertEquals(grants,j.queryForList("SELECT * FROM meeting_access_grant WHERE meeting_id IN (?,?) ORDER BY meeting_id,enterprise_id",meeting,second));
  assertEquals(0,j.queryForObject("SELECT SUM(revision) FROM meeting WHERE id IN (?,?)",Long.class,meeting,second));topics.assignMany(actor,UUID.randomUUID().toString(),null,both);assertTrue(topics.memberships(actor,List.of(second,meeting)).stream().allMatch(row->row.get("topicId")==null));
 }
 @Test void foreignMeetingsAndTopicsDenyWholeBatchAndMembershipReadIsBounded(){
  var other=new Actor("topic-other-"+UUID.randomUUID(),Status.ACTIVE,Set.of(Role.USER));j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",other.enterpriseId(),other.enterpriseId());String foreign=meetings.create(other,UUID.randomUUID().toString(),UUID.randomUUID().toString()).id(),topic=(String)topics.create(actor,UUID.randomUUID().toString(),"本人专题",null).get("id"),otherTopic=(String)topics.create(other,UUID.randomUUID().toString(),"他人专题",null).get("id");
  assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->topics.assign(actor,meeting,otherTopic,UUID.randomUUID().toString(),0));assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->topics.assignMany(actor,UUID.randomUUID().toString(),topic,List.of(new JdbcTopics.MembershipTarget(meeting,0),new JdbcTopics.MembershipTarget(foreign,0))));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM meeting_topic WHERE meeting_id=?",Integer.class,meeting));assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->topics.memberships(other,List.of(meeting)));
  assertThrows(IllegalArgumentException.class,()->topics.memberships(actor,List.of(meeting,meeting)));assertThrows(IllegalArgumentException.class,()->topics.memberships(actor,List.of()));assertThrows(IllegalArgumentException.class,()->topics.memberships(actor,java.util.stream.IntStream.range(0,51).mapToObj(i->UUID.randomUUID().toString()).toList()));
 }
 @Test void replayStillRequiresCurrentMeetingAuthority(){
  String topic=(String)topics.create(actor,UUID.randomUUID().toString(),"权限专题",null).get("id"),key=UUID.randomUUID().toString();topics.assign(actor,meeting,topic,key,0);j.update("UPDATE meeting SET lifecycle='DELETING' WHERE id=?",meeting);assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->topics.assign(actor,meeting,topic,key,0));
 }
}
