package ama.storage;
import java.util.*;import ama.transcript.*;import ama.contract.TranscriptContracts.*;import static org.junit.jupiter.api.Assertions.*;
class Task10PendingTranscriptIT extends Task10GenerationFixture {
 @Override void beforeWorker(String task){new JdbcMinutesEditor(ama.api.Task09RuntimeProbe.components(app).authority()).intent(actor(),meeting,"sticky-editor",4);}
 @Override void afterCandidate(Map<String,Object> candidate,JdbcMinutesFactsReader.Snapshot facts)throws Exception {
  var saved=isolated();String working=j.queryForObject("SELECT transcript_revision_id FROM meeting WHERE id=?",String.class,meeting);saved.mutate(actor(),meeting,"same-confirmation",new Command(Operation.CONFIRM,4,working,List.of()),UUID.randomUUID().toString());assertEquals(4L,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,meeting));assertEquals("PENDING",j.queryForObject("SELECT state FROM generation_candidate WHERE id=?",String.class,candidate.get("id")));
  var ids=j.queryForList("SELECT segment_id FROM transcript_segment WHERE revision_id=? ORDER BY ordinal",String.class,working);saved.mutate(actor(),meeting,"updated-transcript",new Command(Operation.SAVE,4,working,ids.stream().map(id->new Edit(id,"Actual changed transcript",List.of())).toList()),UUID.randomUUID().toString());assertEquals(5L,j.queryForObject("SELECT revision FROM meeting WHERE id=?",Long.class,meeting));assertEquals("STALE",j.queryForObject("SELECT state FROM generation_candidate WHERE id=?",String.class,candidate.get("id")));
 }
}
