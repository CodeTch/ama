package ama.transcript;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import ama.contract.TranscriptContracts.Failure;
/** Bounded metadata only. Controlled admission is deliberately stricter than Task11 publication policy. */
final class MinutesIntentControl {
 private static final Map<String,String> QUERIES;
 static {var q=new LinkedHashMap<String,String>();
  q.put("publication","SELECT id,confirmation_id,state,revision FROM publication WHERE meeting_id=? ORDER BY id LIMIT 2");
  q.put("final_confirmation","SELECT id,meeting_revision FROM final_confirmation WHERE meeting_id=? ORDER BY id LIMIT 257");
  q.put("durable_task","SELECT id,task_type,input_kind,input_id,state FROM durable_task WHERE subject_kind='MEETING' AND subject_id=? ORDER BY id LIMIT 257");
  q.put("external_operation","SELECT id,operation_kind,state,current_execution_no FROM external_operation WHERE subject_kind='MEETING' AND subject_id=? ORDER BY id LIMIT 257");QUERIES=Collections.unmodifiableMap(q);
 }
 static Map<String,List<Map<String,Object>>> read(JdbcTemplate j,String meeting,boolean lock){var all=new LinkedHashMap<String,List<Map<String,Object>>>();for(var entry:QUERIES.entrySet()){
  var rows=j.queryForList(entry.getValue()+(lock?" FOR UPDATE":""),meeting);if(rows.size()>(entry.getKey().equals("publication")?1:256))throw new Failure(503,"INTENT_CONTRIBUTION_UNAVAILABLE");
  all.put(entry.getKey(),rows.stream().map(row->Collections.unmodifiableMap(new LinkedHashMap<>(row))).toList());
 }return Collections.unmodifiableMap(all);}
 static void requireControlled(Map<String,List<Map<String,Object>>> rows,Map<String,Object> root){
  if(!rows.get("publication").isEmpty()||((Number)root.get("ever_published")).intValue()!=0||"PUBLISHED".equals(root.get("stage")))throw new Failure(409,"MEETING_NOT_WRITABLE");
  // No installed publication family can slip through by merely omitting its publication row.
  for(var task:rows.get("durable_task"))if(!Set.of("TRANSCRIPT_IMPORT_V1","MINUTES_GENERATE_V1").contains(task.get("task_type"))||!Set.of("TRANSCRIPTION_INPUT","GENERATION_INPUT").contains(task.get("input_kind")))throw new Failure(503,"INTENT_CONTRIBUTION_UNAVAILABLE");
  for(var operation:rows.get("external_operation"))if(!"MINUTES_GENERATE".equals(operation.get("operation_kind")))throw new Failure(503,"INTENT_CONTRIBUTION_UNAVAILABLE");
  Object current=root.get("final_confirmation_id");if(current!=null&&rows.get("final_confirmation").stream().filter(row->current.equals(row.get("id"))).count()!=1)throw new Failure(503,"PROVENANCE_UNAVAILABLE");
 }
}
