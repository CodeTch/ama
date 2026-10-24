package ama.storage;
import java.nio.file.*;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
/** Real existing fixture scan: marked/unknown and already sealed facts cannot be locally rewritten. */
class Task11LocalMaintenanceIT extends Task11LocalRecoveryResumeIT {
 @Override @Test void genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate()throws Exception {
  var local=ama.api.Task11RuntimeProbe.localPublication(app);assertNotNull(local);var before=inventory();var outcome=local.maintainLocalFailures();assertTrue(outcome.scanned()>0&&outcome.scanned()<=64);assertTrue(outcome.attempted()>0&&outcome.attempted()<=16);assertEquals(0,outcome.recovered());assertEquals(before,inventory());var throttled=local.maintainLocalFailures();assertEquals(0,throttled.scanned());assertEquals(0,throttled.attempted());assertEquals(before,inventory());
  Files.writeString(receipt.resolve("actual-local-maintenance.json"),json.writeValueAsString(Map.of("first",outcome,"sameCadence",throttled,"before",before,"after",inventory(),"qualification","fresh DB-only scan of existing Task11 fixture; marked remote failure denied local closure, UNKNOWN skipped, sealed excluded; no automatic abandonment/provider traffic; positive crash-repair and >64 fairness matrix pending")));
 }
 List<Map<String,Object>> inventory(){return j.queryForList("SELECT t.id,t.state,t.attempt,t.lease_owner,CAST(t.expires_at AS CHAR) expires_at,s.local_state,s.local_reason,CAST(s.local_decided_at AS CHAR) local_decided_at,p.state publication_state,p.revision publication_revision,m.stage,m.revision,m.cancel_epoch,(SELECT COUNT(*) FROM task_attempt a WHERE a.task_id=t.id AND a.ended_at IS NOT NULL) ended_attempts FROM publication_submission s JOIN durable_task t ON t.id=s.task_id JOIN publication p ON p.id=s.publication_id JOIN meeting m ON m.id=p.meeting_id ORDER BY s.task_id");}
}
