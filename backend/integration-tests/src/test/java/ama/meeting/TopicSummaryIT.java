package ama.meeting;
import ama.contract.IdentityContracts.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Current owner-only counts include every persisted membership, independent of list pages. */
class TopicSummaryIT extends TopicMembershipIT {
 @Test void deniedMeetingDeletionPreservesItsCurrentPersonalTopic(){
  String id=(String)topics.create(actor,UUID.randomUUID().toString(),"拒绝删除保留归组",null).get("id");topics.assign(actor,meeting,id,UUID.randomUUID().toString(),0);
  assertThrows(ama.contract.MeetingContracts.Conflict.class,()->meetings.delete(actor,meeting,UUID.randomUUID().toString(),new ama.contract.MeetingContracts.DeleteMeeting(1L),UUID.randomUUID().toString()));
  assertEquals(1L,((Number)topics.summary(actor,id).get("meetingCount")).longValue());assertEquals(id,topics.memberships(actor,List.of(meeting)).getFirst().get("topicId"));assertEquals("ACTIVE",j.queryForObject("SELECT lifecycle FROM meeting WHERE id=?",String.class,meeting));
 }
 @Test void laterLifecycleEventFailureRollsBackMeetingAndPersonalMembershipTogether(){
  String id=(String)topics.create(actor,UUID.randomUUID().toString(),"事务回滚保留归组",null).get("id"),key=UUID.randomUUID().toString();topics.assign(actor,meeting,id,UUID.randomUUID().toString(),0);
  var validators=new HashMap<>(new MeetingEventReferences(source).validators());var actual=validators.get("MEETING_LIFECYCLE");var observed=new java.util.concurrent.atomic.AtomicBoolean();
  validators.put("MEETING_LIFECYCLE",event->{actual.validate(event);assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM meeting_topic WHERE meeting_id=?",Integer.class,meeting));observed.set(true);throw new IllegalStateException("Owned post-validation rollback fixture");});
  var tx=new ama.db.TransactionRunner(source);var fences=new ama.task.JdbcSubjectFences(source,Set.of("MEETING"));var access=new JdbcMeetingAccess(source,tx,fences);
  var rollback=new MeetingService(new JdbcMeetingRepository(source,tx,access,fences,new ama.task.JdbcOutbox(source,validators),new ama.audit.JdbcAuditAdapter(source),ignored->{}));
  assertThrows(IllegalStateException.class,()->rollback.delete(actor,meeting,key,new ama.contract.MeetingContracts.DeleteMeeting(0L),UUID.randomUUID().toString()));assertTrue(observed.get());
  assertEquals("ACTIVE",j.queryForObject("SELECT lifecycle FROM meeting WHERE id=?",String.class,meeting));assertEquals(0L,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,meeting));assertEquals(1L,((Number)topics.summary(actor,id).get("meetingCount")).longValue());
  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id=? AND action='MEETING_DELETED'",Integer.class,meeting));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM task_outbox WHERE subject_id=? AND event_type='MEETING_DELETED'",Integer.class,meeting));
  // Reusing the original key proves its claim rolled back with the failed transaction.
  meetings.delete(actor,meeting,key,new ama.contract.MeetingContracts.DeleteMeeting(0L),UUID.randomUUID().toString());assertEquals(0L,((Number)topics.summary(actor,id).get("meetingCount")).longValue());
 }
 @Test void deletingAnOwnedMeetingRetiresItsPersonalMembershipBeforeTopicDeletion(){
  String id=(String)topics.create(actor,UUID.randomUUID().toString(),"删除会议后的专题",null).get("id");topics.assign(actor,meeting,id,UUID.randomUUID().toString(),0);
  meetings.delete(actor,meeting,UUID.randomUUID().toString(),new ama.contract.MeetingContracts.DeleteMeeting(0L),UUID.randomUUID().toString());
  assertEquals(0L,((Number)topics.summary(actor,id).get("meetingCount")).longValue());assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM meeting_topic WHERE meeting_id=?",Integer.class,meeting));topics.delete(actor,id,UUID.randomUUID().toString(),0);
  assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->meetings.read(actor,meeting));
 }
 @Test void summaryReflectsAllOriginalMembershipsAndCurrentTopicRevision(){
  String id=(String)topics.create(actor,UUID.randomUUID().toString(),"完整计数",null).get("id"),second=meetings.create(actor,UUID.randomUUID().toString(),UUID.randomUUID().toString()).id();
  var empty=topics.summary(actor,id);assertEquals(id,empty.get("id"));assertEquals(0L,((Number)empty.get("meetingCount")).longValue());assertEquals("0",empty.get("revisionToken"));
  topics.assignMany(actor,UUID.randomUUID().toString(),id,List.of(new JdbcTopics.MembershipTarget(meeting,0),new JdbcTopics.MembershipTarget(second,0)));assertEquals(2L,((Number)topics.summary(actor,id).get("meetingCount")).longValue());
  topics.update(actor,id,UUID.randomUUID().toString(),0,"更名后的完整计数",null);assertEquals("1",topics.summary(actor,id).get("revisionToken"));
  topics.unassign(actor,meeting,id,UUID.randomUUID().toString(),0);assertEquals(1L,((Number)topics.summary(actor,id).get("meetingCount")).longValue());assertEquals(0L,j.queryForObject("SELECT SUM(revision) FROM meeting WHERE id IN (?,?)",Long.class,meeting,second));
 }
 @Test void foreignIdentityAndInactiveOriginalOwnerCannotReadPrivateCounts(){
  String id=(String)topics.create(actor,UUID.randomUUID().toString(),"私有计数",null).get("id");var other=new Actor("topic-summary-"+UUID.randomUUID(),Status.ACTIVE,Set.of(Role.USER,Role.PLATFORM_ADMIN));j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",other.enterpriseId(),other.enterpriseId());
  assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->topics.summary(other,id));j.update("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id=?",actor.enterpriseId());assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->topics.summary(actor,id));
 }
 @Test void invalidForeignLegacyEdgeDoesNotLeakARealMeetingCount(){
  String id=(String)topics.create(actor,UUID.randomUUID().toString(),"损坏关联",null).get("id");var other=new Actor("topic-corrupt-"+UUID.randomUUID(),Status.ACTIVE,Set.of(Role.USER));j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",other.enterpriseId(),other.enterpriseId());String foreign=meetings.create(other,UUID.randomUUID().toString(),UUID.randomUUID().toString()).id();
  j.update("INSERT INTO meeting_topic(topic_id,meeting_id,added_at) VALUES (?,?,UTC_TIMESTAMP(6))",id,foreign);
  assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->topics.summary(actor,id));assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM meeting_topic WHERE topic_id=?",Integer.class,id));
 }
}
