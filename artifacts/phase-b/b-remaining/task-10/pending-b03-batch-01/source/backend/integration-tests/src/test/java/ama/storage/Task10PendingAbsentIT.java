package ama.storage;
import java.util.*;import ama.contract.MeetingContracts.*;import ama.meeting.*;import ama.task.*;import static org.junit.jupiter.api.Assertions.*;import org.junit.jupiter.api.Test;
class Task10PendingAbsentIT extends Task09PipelineFixture {
 @Override javax.sql.DataSource createSource(){return ama.minutes.Task10Source.open();}
 @Test void missingContributionRejectsNewEffectsButKeepsAcceptedReceiptsAndRead()throws Exception {
  var old=new JdbcMeetingRepository(source,app.getBean(ama.db.TransactionRunner.class),app.getBean(JdbcMeetingAccess.class),app.getBean(JdbcSubjectFences.class),app.getBean(JdbcOutbox.class),new ama.audit.JdbcAuditAdapter(source),app.getBean(CategoryDictionary.class));
  var command=new SaveInfo(0L,"Owner name",null,null,null,List.of(),List.of());var before=j.queryForMap("SELECT * FROM meeting WHERE id=?",meeting);int receipts=j.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE resource_id=?",Integer.class,meeting);
  var denied=assertThrows(ama.contract.TranscriptContracts.Failure.class,()->old.save(actor(),meeting,"missing",command,UUID.randomUUID().toString()));assertEquals(503,denied.status());assertEquals("MINUTES_TRANSITION_UNAVAILABLE",denied.code());
  assertEquals(before,j.queryForMap("SELECT * FROM meeting WHERE id=?",meeting));assertEquals(receipts,j.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE resource_id=?",Integer.class,meeting));
  var real=app.getBean(JdbcMeetingRepository.class);assertEquals(1,real.save(actor(),meeting,"accepted",command,UUID.randomUUID().toString()).revision());
  assertEquals(1,old.save(actor(),meeting,"accepted",command,UUID.randomUUID().toString()).revision());assertEquals(1,old.read(actor(),meeting).revision());
  var confirm=new ConfirmRoster(1L);assertThrows(ama.contract.TranscriptContracts.Failure.class,()->old.confirm(actor(),meeting,"missing-confirm",confirm,UUID.randomUUID().toString()));assertEquals(2,real.confirm(actor(),meeting,"accepted-confirm",confirm,UUID.randomUUID().toString()).revision());assertEquals(2,old.confirm(actor(),meeting,"accepted-confirm",confirm,UUID.randomUUID().toString()).revision());
 }
}
