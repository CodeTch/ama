package ama.transcript;
import ama.contract.IdentityContracts.Actor;
import java.util.*;
/** Controlled composition only until the combined publication installation is available. */
public final class JdbcMinutesEditor {
 private final JdbcTranscriptAuthority authority;private final JdbcMinutesIntentReceipts receipts;
 public JdbcMinutesEditor(JdbcTranscriptAuthority authority){this.authority=Objects.requireNonNull(authority);receipts=new JdbcMinutesIntentReceipts(authority.dataSource(),authority.transactionRunner(),authority.savedAccess());}
 public Map<String,Object> intent(Actor actor,String meeting,String key,long expected){var replay=receipts.lookup(actor,meeting,key,expected);if(replay.isPresent())return replay.orElseThrow();var id=JdbcMinutesIntentReceipts.identity(actor,meeting,key,expected);var budget=authority.publicationBudget();
  var jdbc=new org.springframework.jdbc.core.JdbcTemplate(authority.dataSource());jdbc.setQueryTimeout(2);
  // The worker can claim or advance the first generation between preparation and F.
  // A retry rolls back the command claim and repeats every check with the original key and budget.
  return TranscriptReadRetry.read(()->MinutesReadVersion.capture(jdbc,meeting,"MEETING_TASKS",null,(rows,bytes)->{budget.row(rows);budget.capture(bytes,bytes,bytes,2);}),()->{
   var plan=authority.prepareMinutesIntent(actor,meeting,expected,budget);
   return authority.transactionRunner().required(()->{
    var stored=receipts.claim(actor,meeting,id);var gate=authority.fileRegistry().lock(actor,meeting,ama.access.MeetingAccess.Action.EDIT);
    JdbcMinutesIntentReceipts.match(stored);if(stored.result()!=null)return receipts.restore(meeting,expected,stored,true);
    var scope=authority.captureMinutesIntent(plan,gate,actor,meeting,expected);
    authority.writeMinutesIntent(scope,plan,gate);receipts.accepted(id,meeting);return receipts.restore(meeting,expected,new JdbcMinutesIntentReceipts.Stored(meeting,200,true),false);
   });
  },cause->cause instanceof ama.contract.MinutesContracts.Failure f&&f.code()==ama.contract.MinutesContracts.FailureCode.PROVENANCE_UNAVAILABLE
    ||cause instanceof ama.contract.TranscriptContracts.Failure t&&t.code().equals("INTENT_CONTRIBUTION_UNAVAILABLE"));
 }
}
