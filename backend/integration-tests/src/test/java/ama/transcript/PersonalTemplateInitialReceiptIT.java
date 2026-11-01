package ama.transcript;

import ama.db.TransactionRunner;
import ama.meeting.JdbcMeetingAccess;
import ama.task.JdbcSubjectFences;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Receipt-component fixtures only; full initial command acceptance is separately tested via CAS. */
class PersonalTemplateInitialReceiptIT extends PersonalTemplateDiscoveryIT {
 @Test void acceptedPersonalInitialRevisionReplaysAfterLaterEditAndDeletionWithoutAnyArchiveSupplier(){
  var first=templates.create(actor,UUID.randomUUID().toString(),"个人初始模板",List.of("决定"));
  var chosen=templates.update(actor,first.id(),UUID.randomUUID().toString(),1,"个人第二版",List.of("决定","行动"));
  var j=new JdbcTemplate(source);var tx=new TransactionRunner(source);var access=new JdbcMeetingAccess(source,tx,new JdbcSubjectFences(source,Set.of("MEETING")));
  var receipts=new JdbcMinutesSettingsReceipts(source,tx,access);String key=UUID.randomUUID().toString(),exclusion=UUID.randomUUID().toString();
  var identity=JdbcMinutesSettingsReceipts.identity(actor,meeting,key,0,chosen.revisionId());
  tx.required(()->{
   var claimed=receipts.claim(actor,meeting,identity);assertNull(claimed.result());
   j.update("INSERT INTO exclusion_revision(id,meeting_id,revision,created_at) VALUES (?,?,1,UTC_TIMESTAMP(6))",exclusion,meeting);
   receipts.accepted(identity,exclusion);return null;
  });
  var original=receipts.lookup(actor,meeting,key,0,chosen.revisionId()).orElseThrow();
  assertEquals(true,original.get("replayed"));assertEquals(chosen.revisionId(),((Map<?,?>)original.get("result")).get("templateRevisionId"));
  var later=templates.update(actor,first.id(),UUID.randomUUID().toString(),2,"后续第三版",List.of("其他主题"));
  templates.delete(actor,first.id(),UUID.randomUUID().toString(),3);
  assertEquals(original.get("result"),receipts.lookup(actor,meeting,key,0,chosen.revisionId()).orElseThrow().get("result"));
  assertTrue(templates.list(actor,20,null).items().isEmpty());assertNotEquals(chosen.revisionId(),later.revisionId());
  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM sensitive_exclusion WHERE revision_id=?",Integer.class,exclusion));
 }
}
