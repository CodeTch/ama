package ama.transcript;
import ama.contract.IdentityContracts.Actor;
import java.util.*;
/** Controlled composition only until the combined publication installation is available. */
public final class JdbcMinutesEditor {
 private final JdbcTranscriptAuthority authority;private final JdbcMinutesIntentReceipts receipts;
 public JdbcMinutesEditor(JdbcTranscriptAuthority authority){this.authority=Objects.requireNonNull(authority);receipts=new JdbcMinutesIntentReceipts(authority.dataSource(),authority.transactionRunner(),authority.savedAccess());}
 public Map<String,Object> intent(Actor actor,String meeting,String key,long expected){var replay=receipts.lookup(actor,meeting,key,expected);if(replay.isPresent())return replay.orElseThrow();var id=JdbcMinutesIntentReceipts.identity(actor,meeting,key,expected);var plan=authority.prepareMinutesIntent(actor,meeting,expected);
  return authority.transactionRunner().required(()->{var stored=receipts.claim(actor,meeting,id);var gate=authority.fileRegistry().lock(actor,meeting,ama.access.MeetingAccess.Action.EDIT);JdbcMinutesIntentReceipts.match(stored);if(stored.result()!=null)return receipts.restore(meeting,expected,stored,true);
   var scope=authority.captureMinutesIntent(plan,gate,actor,meeting,expected);authority.writeMinutesIntent(scope,plan,gate);receipts.accepted(id,meeting);return receipts.restore(meeting,expected,new JdbcMinutesIntentReceipts.Stored(meeting,200,true),false);
  });}
}
