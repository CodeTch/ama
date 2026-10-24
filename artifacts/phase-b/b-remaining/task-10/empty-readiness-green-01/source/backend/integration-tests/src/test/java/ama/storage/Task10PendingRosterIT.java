package ama.storage;
import java.util.*;import ama.transcript.*;import static org.junit.jupiter.api.Assertions.*;
class Task10PendingRosterIT extends Task10GenerationFixture {
 @Override void beforeWorker(String task){new JdbcMinutesEditor(ama.api.Task09RuntimeProbe.components(app).authority()).intent(actor(),meeting,"sticky-editor",4);}
 @Override void afterCandidate(Map<String,Object> candidate,JdbcMinutesFactsReader.Snapshot facts)throws Exception {
  var result=call("POST","/meetings/"+meeting+"/roster-confirmations","{\"expectedRevisionToken\":\"4\"}","confirm-after-candidate");assertEquals(200,result.statusCode(),result.body());
  assertEquals("STALE",j.queryForObject("SELECT state FROM generation_candidate WHERE id=?",String.class,candidate.get("id")));
  assertEquals(5L,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,meeting));assertEquals(1L,j.queryForObject("SELECT access_revision FROM meeting WHERE id=?",Long.class,meeting));
 }
}
