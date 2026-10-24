package ama.storage;
import ama.transcript.*;import java.util.*;import java.util.concurrent.atomic.*;import static org.junit.jupiter.api.Assertions.*;
/** Historical review identities are locked as status metadata; inactive reviewers grant no current access. */
class Task09GenerationReviewIdentitiesIT extends Task09GenerationReviewReaderIT {
 String reviewer;final AtomicInteger observedLocks=new AtomicInteger();
 @Override javax.sql.DataSource createSource(){return new Task09TraceSource(ama.minutes.Task09Source.open());}
 @Override String reviewAuthor()throws Exception {reviewer="task09-reviewer-"+UUID.randomUUID();identity(reviewer);return reviewer;}
 @Override void beforeReviewRead(){j.update("UPDATE enterprise_identity SET status='DISABLED' WHERE enterprise_id=? AND status='ACTIVE'",reviewer);((Task09TraceSource)source).events.clear();}
 @Override void afterReview(Map<String,Object> candidate,JdbcMinutesFactsReader.Snapshot restored,String review)throws Exception {((Task09TraceSource)source).events.stream().filter(e->e.toString().contains("SELECT enterprise_id,status FROM enterprise_identity WHERE enterprise_id=? FOR UPDATE")).forEach(e->observedLocks.incrementAndGet());assertTrue(observedLocks.get()>=4,"S/F must each lock both actor and historical reviewer before roots");assertEquals("DISABLED",j.queryForObject("SELECT status FROM enterprise_identity WHERE enterprise_id=?",String.class,reviewer));super.afterReview(candidate,restored,review);}
}
