package ama.transcript;
import ama.contract.IdentityContracts.Actor;
import java.util.*;
/** Controlled composition only until the combined publication installation is available. */
public final class JdbcMinutesEditor {
 private final JdbcTranscriptAuthority authority;private final JdbcMinutesIntentReceipts receipts;
 public JdbcMinutesEditor(JdbcTranscriptAuthority authority){this.authority=Objects.requireNonNull(authority);receipts=new JdbcMinutesIntentReceipts(authority.dataSource(),authority.transactionRunner(),authority.savedAccess());}
 public Map<String,Object> intent(Actor actor,String meeting,String key,long expected){var replay=receipts.lookup(actor,meeting,key,expected);if(replay.isPresent())return replay.orElseThrow();throw new ama.contract.TranscriptContracts.Failure(503,"INTENT_CONTRIBUTION_UNAVAILABLE");}
}
