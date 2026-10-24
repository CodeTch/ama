import java.nio.file.*;import java.util.*;import com.fasterxml.jackson.databind.*;import org.springframework.jdbc.core.*;import ama.db.*;
class Task05CustodyLink {
 static Map<String,Object> safe(Map<String,Object> row){row.replaceAll((k,v)->v instanceof java.time.temporal.TemporalAccessor?v.toString():v);return row;}
 public static void main(String[] args)throws Exception {
  var json=new ObjectMapper();var root=Path.of(args[0]);var j=new JdbcTemplate(MysqlDataSources.fromEnvironment("AMA_TEST_DATABASE_"));var output=new ArrayList<Object>();
  for(var item:json.readTree(root.resolve("custody-link-task-ids.json").toFile())){
   String id=item.asText();var task=safe(j.queryForMap("SELECT * FROM durable_task WHERE id=?",id));var meeting=safe(j.queryForMap("SELECT id,owner_id,created_at,revision,cancel_epoch FROM meeting WHERE id=?",task.get("subject_id")));var input=safe(j.queryForMap("SELECT id,meeting_id,batch_id,base_revision,cancel_epoch,input_fingerprint,created_at FROM transcription_input WHERE id=?",task.get("input_id")));
   var accept=j.queryForList("SELECT actor_id,resource_id,operation,result_id,http_status,state,created_at FROM idempotency_record WHERE result_id=? AND operation='PROCESSING_IMPORT'",id);accept.forEach(Task05CustodyLink::safe);
   var files=j.queryForList("SELECT f.id,f.meeting_id,f.backend_id,f.created_at,u.id AS upload_id,u.state,u.created_at AS upload_created_at,u.completed_file_id FROM input_batch_file b JOIN file_object f ON f.id=b.file_id JOIN upload_session u ON u.completed_file_id=f.id WHERE b.batch_id=? ORDER BY b.ordinal",input.get("batch_id"));files.forEach(Task05CustodyLink::safe);
   if(accept.size()!=1||!accept.getFirst().get("actor_id").equals(meeting.get("owner_id"))||!accept.getFirst().get("resource_id").equals(meeting.get("id"))||!input.get("meeting_id").equals(meeting.get("id"))||files.isEmpty())throw new IllegalStateException("Unresolved original acceptance");
   output.add(Map.of("task",task,"meeting",meeting,"input",input,"persistedHttpAcceptance",accept,"actualCompletedSources",files));
  }
  json.writeValue(root.resolve("cleanup-additive-http-provenance.json").toFile(),output);System.out.println("linked="+output.size());
 }
}
