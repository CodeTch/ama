package ama.minutes;
import ama.db.TransactionRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** One-time, coordinator-authorized retirement of exact Task9 test fixtures; never a runtime API. */
class Task09FixtureIsolationIT {
 @Test void retireCapturedReadyFixturesWithoutChangingAnyImmutableSpec()throws Exception {
  var source=Task09Source.open();var j=new JdbcTemplate(source);j.setQueryTimeout(2);var json=new ObjectMapper();Path out=Path.of(System.getenv("AMA_TASK09_EVIDENCE_WINDOW"));
  String predicate="t.state='READY' AND t.task_type='TRANSCRIPT_IMPORT_V1' AND t.pool='TRANSCRIPT_IMPORT' AND m.owner_id LIKE 'task09-graph-%' AND t.producer_key=CONCAT(REPLACE(t.id,'-',''),REPLACE(t.id,'-','')) AND t.input_fingerprint=? AND i.config_manifest='{}'";
  String from=" FROM durable_task t JOIN meeting m ON m.id=t.subject_id AND t.subject_kind='MEETING' JOIN transcription_input i ON i.id=t.input_id AND i.meeting_id=m.id ";
  var synthetic=j.queryForList("SELECT t.id"+from+"WHERE "+predicate+" ORDER BY t.id LIMIT 65","a".repeat(64));assertTrue(synthetic.size()>0&&synthetic.size()<=64);
  List<String> ids=new ArrayList<>(synthetic.stream().map(r->(String)r.get("id")).toList());String real="01f28056-233c-478f-9b2d-14741ddf16e8";assertFalse(ids.contains(real));ids.add(real);Collections.sort(ids);
  var realIdentity=j.queryForMap("SELECT t.*,m.owner_id FROM durable_task t JOIN meeting m ON m.id=t.subject_id WHERE t.id=?",real);assertEquals("task09-d892ff5f-04e7-4032-9320-9bab983243f1",realIdentity.get("owner_id"));assertEquals("8fa500d5-ff36-40ac-a778-ee2b5a435fb8",realIdentity.get("subject_id"));assertEquals("READY",realIdentity.get("state"));assertEquals("TRANSCRIPT_IMPORT_V1",realIdentity.get("task_type"));assertEquals("TRANSCRIPT_IMPORT",realIdentity.get("pool"));
  for(long pid:List.of(1346089L,1346114L))assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false),"prior case process must be stopped");
  var before=new TreeMap<String,Map<String,Object>>();var input=new TreeMap<String,Map<String,Object>>();var meetings=new TreeMap<String,Map<String,Object>>();var external=new TreeMap<String,List<Map<String,Object>>>();
  for(String id:ids){var row=j.queryForMap("SELECT * FROM durable_task WHERE id=?",id);assertEquals("READY",row.get("state"));before.put(id,row);input.put((String)row.get("input_id"),j.queryForMap("SELECT * FROM transcription_input WHERE id=?",row.get("input_id")));meetings.put((String)row.get("subject_id"),j.queryForMap("SELECT * FROM meeting WHERE id=?",row.get("subject_id")));assertQuiescent(j,id);external.put(id,j.queryForList("SELECT id,operation_id,execution_no,task_id,task_attempt,state,marked_at,request_hash FROM external_execution WHERE task_id=?",id));}
  Files.writeString(out.resolve("retirement-before.json"),json.writeValueAsString(plain(Map.of("syntheticPredicate",predicate,"syntheticIds",synthetic,"realId",real,"priorStoppedPids",List.of(1346089L,1346114L),"tasks",before,"inputs",input,"meetings",meetings,"externalExecutions",external))),StandardOpenOption.CREATE_NEW);
  new TransactionRunner(source,10).required(()->{
   for(String meeting:meetings.keySet()){j.queryForMap("SELECT * FROM task_subject WHERE subject_kind='MEETING' AND subject_id=? FOR UPDATE",meeting);assertEquals(meetings.get(meeting),j.queryForMap("SELECT * FROM meeting WHERE id=? FOR UPDATE",meeting));}
   assertEquals(synthetic,j.queryForList("SELECT t.id"+from+"WHERE "+predicate+" ORDER BY t.id LIMIT 65","a".repeat(64)));
   for(String id:ids){assertEquals(before.get(id),j.queryForMap("SELECT * FROM durable_task WHERE id=? FOR UPDATE",id));assertEquals(input.get(before.get(id).get("input_id")),j.queryForMap("SELECT * FROM transcription_input WHERE id=?",before.get(id).get("input_id")));assertQuiescent(j,id);}
   for(String id:ids)assertEquals(1,j.update("UPDATE durable_task SET state='CANCELLED' WHERE id=? AND state='READY' AND attempt=? AND producer_key=? AND input_id=?",id,before.get(id).get("attempt"),before.get(id).get("producer_key"),before.get(id).get("input_id")));
   return null;
  });
  var after=new TreeMap<String,Map<String,Object>>();for(String id:ids){var row=j.queryForMap("SELECT * FROM durable_task WHERE id=?",id);var expected=new LinkedHashMap<>(before.get(id));expected.put("state","CANCELLED");assertEquals(expected,row);after.put(id,row);}
  Files.writeString(out.resolve("retirement-after.json"),json.writeValueAsString(plain(Map.of("changedRows",ids.size(),"onlyChangedField","state","transition","READY_TO_CANCELLED","tasks",after))),StandardOpenOption.CREATE_NEW);
 }
 static Object plain(Object value){if(value instanceof java.time.temporal.TemporalAccessor)return value.toString();if(value instanceof Map<?,?> map){var out=new LinkedHashMap<String,Object>();map.forEach((k,v)->out.put(k.toString(),plain(v)));return out;}if(value instanceof List<?> list)return list.stream().map(Task09FixtureIsolationIT::plain).toList();return value;}
 static void assertQuiescent(JdbcTemplate j,String id){
  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM task_attempt WHERE task_id=? AND ended_at IS NULL",Integer.class,id));
  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM external_execution WHERE task_id=? AND (marked_at IS NOT NULL OR state IN ('MARKED','UNKNOWN','SUCCEEDED'))",Integer.class,id));
  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM model_reservation r JOIN external_execution e ON e.id=r.execution_id WHERE e.task_id=?",Integer.class,id));
 }
}
