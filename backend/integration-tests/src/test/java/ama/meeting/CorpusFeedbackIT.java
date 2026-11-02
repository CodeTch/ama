package ama.meeting;
import ama.governance.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
class CorpusFeedbackIT extends FeedbackReviewIT {
 JdbcCorpusFeedback corpus;
 @BeforeEach void corpus(){var tx=new ama.db.TransactionRunner(source);var access=new JdbcMeetingAccess(source,tx,new ama.task.JdbcSubjectFences(source,Set.of("MEETING")));feedback=new JdbcFeedback(source,tx,access);corpus=new JdbcCorpusFeedback(source,tx,access,feedback);}
 @Test void onlyBoundApprovedCurrentSourcesAreConsumableAndWithdrawalIsImmediate(){
  var admin=reviewer(true);String id=(String)corpus.submit(actor,UUID.randomUUID().toString(),meeting,0,List.of(),"WORDING","仅显式提交建议").get("id");
  assertTrue(corpus.approved(admin).stream().noneMatch(row->row.get("id").equals(id)));feedback.review(admin,id,UUID.randomUUID().toString(),0,"ACCEPTED","批准用于受控语料");
  assertTrue(corpus.approved(admin).stream().anyMatch(row->row.get("id").equals(id)));corpus.withdraw(actor,id,UUID.randomUUID().toString());assertTrue(corpus.approved(admin).stream().noneMatch(row->row.get("id").equals(id)));
  assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->corpus.approved(actor));
 }
 @Test void reviewerCannotReadUnrelatedSourceAndSourceChangeStopsUse(){
  var admin=reviewer(true);String id=(String)corpus.submit(actor,UUID.randomUUID().toString(),meeting,0,List.of(),"WORDING","最小披露").get("id");feedback.review(admin,id,UUID.randomUUID().toString(),0,"ACCEPTED","审核");
  var tx=new ama.db.TransactionRunner(source);var access=new JdbcMeetingAccess(source,tx,new ama.task.JdbcSubjectFences(source,Set.of("MEETING")));assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->access.require(admin,meeting,ama.access.MeetingAccess.Action.READ));
  j.update("UPDATE meeting SET governance_state='WITHDRAWN' WHERE id=?",meeting);assertTrue(corpus.approved(admin).stream().noneMatch(row->row.get("id").equals(id)));
 }
 @Test void disablingSubmitterStopsGlobalConsumptionWithoutGivingReviewerSourceAccess(){
  var admin=reviewer(true);String id=(String)corpus.submit(actor,UUID.randomUUID().toString(),meeting,0,List.of(),"WORDING","授权来源").get("id");feedback.review(admin,id,UUID.randomUUID().toString(),0,"ACCEPTED","审核通过");
  assertTrue(corpus.approved(admin).stream().anyMatch(row->row.get("id").equals(id)));j.update("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id=?",actor.enterpriseId());assertTrue(corpus.approved(admin).stream().noneMatch(row->row.get("id").equals(id)));
 }
 @Test void legacyApprovedFeedbackCannotBeSilentlyBoundToANewSourceVersion(){
  String key=UUID.randomUUID().toString();String id=(String)feedback.create(actor,key,meeting,"WORDING","原未绑定反馈").get("id");feedback.review(reviewer(true),id,UUID.randomUUID().toString(),0,"ACCEPTED","旧审核");
  assertThrows(ama.contract.MeetingContracts.Conflict.class,()->corpus.submit(actor,key,meeting,0,List.of(),"WORDING","原未绑定反馈"));assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM corpus_feedback_binding WHERE feedback_id=?",Integer.class,id));
 }
 @Test void reviewerSeesRedactedExplicitFeedbackWhileSubmitterKeepsOriginal(){
  var admin=reviewer(true);String comment="联系13800138000，邮箱alice@example.invalid，证件110101199001011234";String id=(String)corpus.submit(actor,UUID.randomUUID().toString(),meeting,0,List.of(),"WORDING",comment).get("id");
  assertEquals(comment,current(actor,id).get("comment"));String visible=(String)current(admin,id).get("comment");assertFalse(visible.contains("13800138000"));assertFalse(visible.contains("alice@example.invalid"));assertFalse(visible.contains("110101199001011234"));feedback.review(admin,id,UUID.randomUUID().toString(),0,"ACCEPTED","审核通过");var approved=corpus.approved(admin).stream().filter(row->row.get("id").equals(id)).findFirst().orElseThrow();assertEquals(visible,approved.get("comment"));
 }
}
