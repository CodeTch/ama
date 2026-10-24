import java.nio.file.*;import java.util.*;import java.sql.*;import com.fasterxml.jackson.databind.*;import org.springframework.jdbc.core.*;import ama.db.*;
class Task05FixtureMaintenance {
 static Map<String,Object> safe(Map<String,Object> row){row.replaceAll((k,v)->v instanceof java.time.temporal.TemporalAccessor?v.toString():v);return row;}
 public static void main(String[] args)throws Exception {
  var json=new ObjectMapper();var root=Path.of(args[1]);var j=new JdbcTemplate(MysqlDataSources.fromEnvironment("AMA_TEST_DATABASE_"));var source=j.getDataSource();
  if(args[0].equals("diagnose")){
   var rows=j.queryForList("SELECT t.id,t.state,t.attempt,t.created_at,m.owner_id,f.backend_id FROM durable_task t JOIN meeting m ON m.id=t.subject_id JOIN transcription_input i ON i.id=t.input_id JOIN input_batch_file b ON b.batch_id=i.batch_id JOIN file_object f ON f.id=b.file_id WHERE t.task_type='TRANSCRIPT_IMPORT_V1' AND t.state IN ('READY','RUNNING') ORDER BY t.created_at,t.id LIMIT 100");rows.forEach(Task05FixtureMaintenance::safe);json.writeValue(root.resolve("diagnostic-active-tasks.json").toFile(),rows);System.out.println("diagnostic="+rows.size());return;
  }
  if(args[0].equals("plan")){
   var selectors=json.readTree(root.resolve("cleanup-owned-route-selectors.json").toFile());var found=new TreeMap<String,Map<String,Object>>();
   for(var selector:selectors)for(var row:j.queryForList("SELECT DISTINCT t.* FROM durable_task t JOIN meeting m ON m.id=t.subject_id JOIN transcription_input i ON i.id=t.input_id JOIN input_batch_file b ON b.batch_id=i.batch_id JOIN file_object f ON f.id=b.file_id WHERE f.backend_id=? AND t.task_type='TRANSCRIPT_IMPORT_V1' AND t.state IN ('READY','RUNNING') AND m.owner_id LIKE 'task05-%'",selector.asText()))found.put((String)row.get("id"),safe(row));
   json.writeValue(root.resolve("cleanup-enumerated-before.json").toFile(),found);System.out.println("enumerated="+found.size());return;
  }
  var expected=json.readTree(root.resolve("cleanup-enumerated-before.json").toFile());var receipts=new ArrayList<Object>();
  for(var it=expected.fields();it.hasNext();){var item=it.next();String id=item.getKey();
   new TransactionRunner(source).required(()->{
    String subject=item.getValue().get("subject_id").asText(),input=item.getValue().get("input_id").asText();
    j.queryForMap("SELECT * FROM task_subject WHERE subject_kind='MEETING' AND subject_id=? FOR UPDATE",subject);
    var meeting=j.queryForMap("SELECT * FROM meeting WHERE id=? FOR UPDATE",subject);var inputRow=j.queryForMap("SELECT * FROM transcription_input WHERE id=? FOR UPDATE",input);
    if(!meeting.get("owner_id").toString().startsWith("task05-")||!inputRow.get("meeting_id").equals(subject))throw new IllegalStateException("Foreign fixture");
    var before=j.queryForMap("SELECT * FROM durable_task WHERE id=? FOR UPDATE",id);safe(before);var actual=json.readTree(json.writeValueAsBytes(before));if(!actual.equals(item.getValue())){receipts.add(Map.of("id",id,"skipped","changed"));return null;}
    if(!List.of("READY","RUNNING").contains(before.get("state"))||before.get("output_id")!=null||before.get("output_kind")!=null)throw new IllegalStateException("Unexpected state");
    if(j.queryForObject("SELECT COUNT(*) FROM transcript_revision WHERE producer_task_id=?",Long.class,id)!=0||j.queryForObject("SELECT COUNT(*) FROM external_execution WHERE task_id=?",Long.class,id)!=0||j.queryForObject("SELECT COUNT(*) FROM external_operation WHERE subject_kind=? AND subject_id=?",Long.class,before.get("subject_kind"),before.get("subject_id"))!=0)throw new IllegalStateException("Unexpected effects");
    j.queryForMap("SELECT * FROM meeting WHERE id=? FOR UPDATE",before.get("subject_id"));j.queryForMap("SELECT * FROM transcription_input WHERE id=? FOR UPDATE",before.get("input_id"));
    long attempt=((Number)before.get("attempt")).longValue();int ended=0;
    if(attempt>0){var a=j.queryForMap("SELECT * FROM task_attempt WHERE task_id=? AND attempt=? FOR UPDATE",id,attempt);if(before.get("state").equals("RUNNING")){if(a.get("ended_at")!=null||!Objects.equals(a.get("owner"),before.get("lease_owner")))throw new IllegalStateException("Unexpected attempt");ended=j.update("UPDATE task_attempt SET ended_at=UTC_TIMESTAMP(6),outcome='CANCELLED',error_code='TASK05_FIXTURE_CLEANUP' WHERE task_id=? AND attempt=? AND owner=? AND ended_at IS NULL",id,attempt,before.get("lease_owner"));if(ended!=1)throw new IllegalStateException();}else if(a.get("ended_at")==null)throw new IllegalStateException("Unended READY history");}
    int changed=j.update("UPDATE durable_task SET state='CANCELLED',lease_owner=NULL,expires_at=NULL,output_kind=NULL,output_id=NULL,last_error='TASK05_FIXTURE_CLEANUP' WHERE id=? AND state=?",id,before.get("state"));if(changed!=1)throw new IllegalStateException();
    receipts.add(Map.of("id",id,"before",before,"after",safe(j.queryForMap("SELECT * FROM durable_task WHERE id=?",id)),"taskRows",changed,"attemptRows",ended));return null;
   });
  }
  json.writeValue(root.resolve("cleanup-after.json").toFile(),receipts);System.out.println("receipts="+receipts.size());
 }
}
