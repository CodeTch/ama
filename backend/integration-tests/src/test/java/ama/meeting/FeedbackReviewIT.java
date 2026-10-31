package ama.meeting;

import ama.contract.IdentityContracts.*;
import ama.access.MeetingAccess;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Fresh real JDBC, current role facts and the genuine meeting producer. */
class FeedbackReviewIT extends FeedbackPersistenceIT {
 Actor reviewer(boolean assigned){
  String id="feedback-reviewer-"+UUID.randomUUID();
  j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",id,id);
  if(assigned)j.update("INSERT INTO role_assignment(enterprise_id,role,assigned_at) VALUES (?,'PLATFORM_ADMIN',UTC_TIMESTAMP(6))",id);
  return new Actor(id,Status.ACTIVE,Set.of(Role.USER,Role.PLATFORM_ADMIN));
 }
 @SuppressWarnings("unchecked") Map<String,Object> current(Actor who,String id){return (Map<String,Object>)feedback.detail(who,id).get("feedback");}
 @SuppressWarnings("unchecked") List<Map<String,Object>> history(Actor who,String id){return (List<Map<String,Object>>)feedback.detail(who,id).get("history");}
 String submit(){return (String)feedback.create(actor,UUID.randomUUID().toString(),meeting,"WORDING","保留原始建议").get("id");}
 @Test void supplementAppendsHistoryWithoutChangingOriginalCreateReceipt(){
  String key=UUID.randomUUID().toString();var creation=feedback.create(actor,key,meeting,"WORDING","保留原始建议");String id=(String)creation.get("id"),supplementKey=UUID.randomUUID().toString();
  var receipt=feedback.supplement(actor,id,supplementKey,0,"进一步解释😀");
  assertEquals(receipt,feedback.supplement(actor,id,supplementKey,0,"进一步解释😀"));
  assertEquals("1",current(actor,id).get("revisionToken"));assertEquals("保留原始建议",current(actor,id).get("comment"));
  assertEquals(1,history(actor,id).size());assertEquals("进一步解释😀",history(actor,id).getFirst().get("text"));
  assertEquals(creation,feedback.create(actor,key,meeting,"WORDING","保留原始建议"));
  assertThrows(ama.contract.MeetingContracts.Conflict.class,()->feedback.supplement(actor,id,supplementKey,0,"修改后的补充"));
  assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id=? AND action='FEEDBACK_SUPPLEMENTED'",Integer.class,id));
 }
 @Test void onlyCurrentDatabaseAdminMayReviewAndCannotReadTheSourceMeeting(){
  String id=submit();var forged=reviewer(false);
  assertThrows(MeetingAccess.AccessDeniedException.class,()->feedback.review(forged,id,UUID.randomUUID().toString(),0,"ACCEPTED","伪角色"));
  assertThrows(MeetingAccess.AccessDeniedException.class,()->feedback.detail(forged,id));
  var admin=reviewer(true);feedback.review(admin,id,UUID.randomUUID().toString(),0,"NEEDS_MORE","请补充上下文");
  assertEquals("NEEDS_MORE",current(actor,id).get("state"));assertEquals("NEEDS_MORE",current(admin,id).get("state"));
  var tx=new ama.db.TransactionRunner(source);var fences=new ama.task.JdbcSubjectFences(source,Set.of("MEETING"));var access=new JdbcMeetingAccess(source,tx,fences);
  assertThrows(MeetingAccess.AccessDeniedException.class,()->tx.required(()->{access.require(access.lock(admin,meeting),MeetingAccess.Action.READ);return null;}));
  j.update("DELETE FROM role_assignment WHERE enterprise_id=?",admin.enterpriseId());
  assertThrows(MeetingAccess.AccessDeniedException.class,()->feedback.undoReview(admin,id,UUID.randomUUID().toString(),1));
 }
 @Test void reviewUndoAndHistoricalReplayNeverOverwriteLaterState(){
  String id=submit(),key=UUID.randomUUID().toString();var admin=reviewer(true);var reviewed=feedback.review(admin,id,key,0,"ACCEPTED","本地审核通过");
  var undo=feedback.undoReview(admin,id,UUID.randomUUID().toString(),1);assertEquals("PENDING",current(actor,id).get("state"));
  assertEquals(reviewed,feedback.review(admin,id,key,0,"ACCEPTED","本地审核通过"));assertEquals("PENDING",current(actor,id).get("state"));assertEquals("2",current(actor,id).get("revisionToken"));
  assertNotEquals(reviewed.get("historyId"),undo.get("historyId"));assertEquals(2,history(actor,id).size());
  assertThrows(ama.contract.MeetingContracts.Conflict.class,()->feedback.review(admin,id,UUID.randomUUID().toString(),1,"REJECTED","旧版本"));
  assertEquals(2,history(actor,id).size());
 }
 @Test void needsMoreSupplementResubmitsButOtherDecisionsRemainExplicit(){
  String id=submit();var admin=reviewer(true);feedback.review(admin,id,UUID.randomUUID().toString(),0,"NEEDS_MORE","需要说明");
  feedback.supplement(actor,id,UUID.randomUUID().toString(),1,"所需说明");assertEquals("PENDING",current(actor,id).get("state"));
  feedback.review(admin,id,UUID.randomUUID().toString(),2,"REJECTED","审核结论");feedback.supplement(actor,id,UUID.randomUUID().toString(),3,"追加说明");
  assertEquals("REJECTED",current(actor,id).get("state"));assertEquals("4",current(actor,id).get("revisionToken"));
 }
 @Test void deniedSupplementAndStaleRevisionLeaveNoPartialHistory(){
  String id=submit(),key=UUID.randomUUID().toString();feedback.supplement(actor,id,key,0,"第一次");
  assertThrows(ama.contract.MeetingContracts.Conflict.class,()->feedback.supplement(actor,id,UUID.randomUUID().toString(),0,"过期版本"));
  j.update("UPDATE meeting SET lifecycle='DELETING' WHERE id=?",meeting);
  assertThrows(MeetingAccess.AccessDeniedException.class,()->feedback.supplement(actor,id,key,0,"第一次"));
  assertThrows(MeetingAccess.AccessDeniedException.class,()->feedback.supplement(actor,id,UUID.randomUUID().toString(),1,"已撤权"));
  assertEquals(1,history(actor,id).size());assertEquals("1",current(actor,id).get("revisionToken"));
 }
 @Test void summaryReadsUseCurrentPrivateScopeCountsAndStateBoundCursors(){
  String id=submit();var admin=reviewer(true);feedback.review(admin,id,UUID.randomUUID().toString(),0,"NEEDS_MORE","需要补充");
  var own=feedback.summaries(actor,null,null,false);assertEquals(1,((List<?>)own.get("items")).size());
  var counts=(Map<?,?>)own.get("counts");assertEquals(1L,((Number)counts.get("ALL")).longValue());assertEquals(1L,((Number)counts.get("NEEDS_MORE")).longValue());
  assertEquals(0,((List<?>)feedback.summaries(actor,null,"PENDING",false).get("items")).size());
  assertThrows(MeetingAccess.AccessDeniedException.class,()->feedback.summaries(reviewer(false),null,null,true));
  assertEquals(0,((List<?>)feedback.summaries(reviewer(false),null,null,false).get("items")).size());
  assertTrue(((List<?>)feedback.summaries(admin,null,"NEEDS_MORE",true).get("items")).stream().anyMatch(row->id.equals(((Map<?,?>)row).get("id"))));
 }
 @Test void boundedInputsCannotReviewOrAppendAndForeignIdentitiesCannotRead(){
  String id=submit();var admin=reviewer(true);assertThrows(IllegalArgumentException.class,()->feedback.review(admin,id,UUID.randomUUID().toString(),0,"UNKNOWN","说明"));
  assertThrows(IllegalArgumentException.class,()->feedback.review(admin,id,UUID.randomUUID().toString(),0,"REJECTED","x".repeat(2049)));
  for(String text:List.of(""," ","x".repeat(4097)))assertThrows(IllegalArgumentException.class,()->feedback.supplement(actor,id,UUID.randomUUID().toString(),0,text));
  assertEquals(0,history(actor,id).size());assertThrows(MeetingAccess.AccessDeniedException.class,()->feedback.detail(reviewer(false),id));
 }
}
