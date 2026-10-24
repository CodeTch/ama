package ama.storage;
import java.util.*;import ama.transcript.*;import static org.junit.jupiter.api.Assertions.*;
class Task10FirstSaveIT extends Task10GenerationFixture {
 @Override String instruction(){return "controlled:rich-output";}
 @Override String expectedCandidateState(){return "ADOPTED";}
 @Override Map<String,Object> expectedRoot(Map<String,Object> before){
  var after=j.queryForMap("SELECT * FROM meeting WHERE id=?",meeting);assertEquals(5L,((Number)after.get("revision")).longValue());assertNotNull(after.get("minutes_revision_id"));assertNotNull(after.get("element_set_id"));assertEquals(0,((Number)after.get("needs_generation")).intValue());assertNotNull(after.get("generation_fingerprint"));assertEquals(5L,j.queryForObject("SELECT business_version FROM task_subject WHERE subject_kind='MEETING' AND subject_id=?",Long.class,meeting));assertEquals(5L,j.queryForObject("SELECT meeting_revision FROM meeting_search_projection WHERE meeting_id=?",Long.class,meeting));
  for(String key:before.keySet())if(!Set.of("revision","minutes_revision_id","element_set_id","needs_generation","generation_fingerprint","updated_at").contains(key))assertEquals(before.get(key),after.get(key),key);return after;
 }
 @Override void afterCandidate(Map<String,Object> candidate,JdbcMinutesFactsReader.Snapshot facts)throws Exception {
  var root=j.queryForMap("SELECT minutes_revision_id,element_set_id FROM meeting WHERE id=?",meeting);assertNotEquals(candidate.get("minutes_revision_id"),root.get("minutes_revision_id"));assertNotEquals(candidate.get("element_set_id"),root.get("element_set_id"));assertNotNull(candidate.get("decided_at"));
  var original=j.queryForMap("SELECT * FROM minutes_revision WHERE id=?",candidate.get("minutes_revision_id"));var saved=j.queryForMap("SELECT * FROM minutes_revision WHERE id=?",root.get("minutes_revision_id"));assertEquals(original.get("body"),saved.get("body"));assertEquals(original.get("title"),saved.get("title"));assertEquals(original.get("generation_input_id"),saved.get("generation_input_id"));assertEquals(1L,((Number)original.get("revision")).longValue());assertEquals(2L,((Number)saved.get("revision")).longValue());
  var originals=j.queryForList("SELECT id FROM minutes_element WHERE set_id=?",String.class,candidate.get("element_set_id"));var copies=j.queryForList("SELECT id FROM minutes_element WHERE set_id=?",String.class,root.get("element_set_id"));assertEquals(8,originals.size());assertEquals(8,copies.size());assertTrue(Collections.disjoint(originals,copies));assertEquals(7,j.queryForObject("SELECT COUNT(DISTINCT category) FROM minutes_element WHERE set_id=?",Integer.class,root.get("element_set_id")));
  assertEquals(2,j.queryForObject("SELECT COUNT(*) FROM action_item a JOIN minutes_element e ON e.id=a.element_id WHERE e.set_id=? AND a.owner_enterprise_id IS NULL AND a.owner_display IS NULL AND a.due_on IS NULL",Integer.class,root.get("element_set_id")));
  assertEquals(2,j.queryForObject("SELECT COUNT(*) FROM action_suggestion a JOIN minutes_element e ON e.id=a.element_id WHERE e.set_id=? AND a.input_id=?",Integer.class,root.get("element_set_id"),candidate.get("input_id")));
  assertEquals(2,j.queryForObject("SELECT COUNT(*) FROM sensitive_detection WHERE set_id=?",Integer.class,root.get("element_set_id")));assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM background_note WHERE set_id=?",Integer.class,root.get("element_set_id")));
  assertEquals(9,j.queryForObject("SELECT COUNT(*) FROM evidence_link WHERE minutes_revision_id=? OR element_id IN (SELECT id FROM minutes_element WHERE set_id=?)",Integer.class,root.get("minutes_revision_id"),root.get("element_set_id")));
  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM result_review WHERE meeting_id=?",Integer.class,meeting));assertEquals(0L,j.queryForObject("SELECT intent_epoch FROM meeting_minutes_editor_guard WHERE meeting_id=?",Long.class,meeting));
 }
}
