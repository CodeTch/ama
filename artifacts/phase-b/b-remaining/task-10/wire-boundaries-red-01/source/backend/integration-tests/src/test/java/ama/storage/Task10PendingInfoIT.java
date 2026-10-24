package ama.storage;
import java.util.*;
import ama.transcript.*;
import static org.junit.jupiter.api.Assertions.*;
class Task10PendingInfoIT extends Task10GenerationFixture {
 @Override void beforeWorker(String task){new JdbcMinutesEditor(ama.api.Task09RuntimeProbe.components(app).authority()).intent(actor(),meeting,"sticky-editor",4);}
 @Override void afterCandidate(Map<String,Object> candidate,JdbcMinutesFactsReader.Snapshot facts)throws Exception {
  var original=new LinkedHashMap<>(candidate);
  var saved=call("PUT","/meetings/"+meeting+"/info",json.writeValueAsString(Map.of("expectedRevisionToken","4","name","Actual owner saved name","rosterDraftIds",List.of())),"save-after-candidate");
  assertEquals(200,saved.statusCode(),saved.body());
  var after=j.queryForMap("SELECT * FROM generation_candidate WHERE id=?",candidate.get("id"));assertEquals("STALE",after.get("state"));assertNotNull(after.get("decided_at"));
  for(String field:List.of("input_id","task_id","minutes_revision_id","element_set_id","base_revision","input_fingerprint","created_at"))assertEquals(original.get(field),after.get(field),field);
  assertEquals(5L,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,meeting));
  var replay=call("PUT","/meetings/"+meeting+"/info",json.writeValueAsString(Map.of("expectedRevisionToken","4","name","Actual owner saved name","rosterDraftIds",List.of())),"save-after-candidate");assertEquals(200,replay.statusCode(),replay.body());assertEquals(after,j.queryForMap("SELECT * FROM generation_candidate WHERE id=?",candidate.get("id")));
 }
}
