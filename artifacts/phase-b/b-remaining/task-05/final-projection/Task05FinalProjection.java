import java.nio.file.*;import java.util.*;import com.fasterxml.jackson.databind.*;import org.springframework.jdbc.core.*;import ama.db.*;
class Task05FinalProjection {
 static Map<String,Object> safe(Map<String,Object> row){row.replaceAll((k,v)->v instanceof java.time.temporal.TemporalAccessor?v.toString():v);return row;}
 static List<Map<String,Object>> safeList(List<Map<String,Object>> rows){rows.forEach(Task05FinalProjection::safe);return rows;}
 public static void main(String[]args)throws Exception {
  var root=Path.of(args[0]);var json=new ObjectMapper();var j=new JdbcTemplate(MysqlDataSources.fromEnvironment("AMA_TEST_DATABASE_"));var output=new ArrayList<Object>();
  for(var x:json.readTree(root.resolve("task-ids.json").toFile())){
   String id=x.asText();var task=safe(j.queryForMap("SELECT * FROM durable_task WHERE id=?",id));var raw=safeList(j.queryForList("SELECT * FROM transcript_revision WHERE producer_task_id=? LIMIT 2",id));var segments=new ArrayList<Object>();
   for(var rev:raw)segments.addAll(safeList(j.queryForList("SELECT * FROM transcript_segment WHERE revision_id=? ORDER BY ordinal LIMIT 65",rev.get("id"))));
   output.add(Map.of("task",task,"attempts",safeList(j.queryForList("SELECT * FROM task_attempt WHERE task_id=? ORDER BY attempt",id)),"raw",raw,"segments",segments,"input",safe(j.queryForMap("SELECT id,meeting_id,batch_id,input_fingerprint,base_revision,cancel_epoch,parser_version,processing_config_version FROM transcription_input WHERE id=?",task.get("input_id"))),"meeting",safe(j.queryForMap("SELECT id,owner_id,revision,cancel_epoch,transcript_revision_id FROM meeting WHERE id=?",task.get("subject_id"))),"outbox",safeList(j.queryForList("SELECT sequence_id,event_type,task_id FROM task_outbox WHERE task_id=? ORDER BY sequence_id",id)),"audit",safeList(j.queryForList("SELECT action,resource_id,result FROM audit_event WHERE resource_id=? AND action='TRANSCRIPT_IMPORTED'",task.get("subject_id")))));
  }
  json.writeValue(root.resolve("actual-task-projections.json").toFile(),output);
  json.writeValue(root.resolve("bounded-active-import-diagnostic.json").toFile(),safeList(j.queryForList("SELECT t.id,t.state,t.attempt,t.lease_owner,t.subject_id,t.input_id,m.owner_id FROM durable_task t JOIN meeting m ON m.id=t.subject_id WHERE t.pool='TRANSCRIPT_IMPORT' AND t.state IN ('READY','RUNNING') ORDER BY t.id LIMIT 65")));
  json.writeValue(root.resolve("history.json").toFile(),safeList(j.queryForList("SELECT installed_rank,version,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank")));
  System.out.println("exact retained tasks="+output.size());
 }
}
