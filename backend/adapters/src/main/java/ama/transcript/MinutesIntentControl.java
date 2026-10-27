package ama.transcript;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import ama.contract.TranscriptContracts.Failure;
/** Intent metadata shares the complete publication S/F acquisition budget; it grants no authority. */
final class MinutesIntentControl {
 private static final Map<String,String> QUERIES;
 static {var q=new LinkedHashMap<String,String>();
  q.put("publication","SELECT id,confirmation_id,state,revision FROM publication WHERE meeting_id=? ORDER BY id LIMIT 2");
  q.put("final_confirmation","SELECT id,meeting_revision FROM final_confirmation WHERE meeting_id=? ORDER BY id LIMIT 257");
  q.put("durable_task","SELECT id,task_type,input_kind,input_id,state FROM durable_task WHERE subject_kind='MEETING' AND subject_id=? ORDER BY id LIMIT 257");
  q.put("external_operation","SELECT id,operation_kind,state,current_execution_no FROM external_operation WHERE subject_kind='MEETING' AND subject_id=? ORDER BY id LIMIT 257");QUERIES=Collections.unmodifiableMap(q);
 }
 static Map<String,List<Map<String,Object>>> read(JdbcTemplate j,String meeting,boolean lock,TranscriptAcquisitionBudget.Ledger budget){
  var all=new LinkedHashMap<String,List<Map<String,Object>>>();
  for(var entry:QUERIES.entrySet()){
   budget.check();String sql=entry.getValue();int maximum=entry.getKey().equals("publication")?1:256;
   String columns=sql.substring(7,sql.indexOf(" FROM "));int fields=columns.split(",").length;
   long count=j.queryForObject("SELECT COUNT(*) FROM (SELECT 1"+sql.substring(sql.indexOf(" FROM "))+") intent_control_count",Long.class,meeting);
   if(count<0||count>maximum||count>budget.remainingRows())throw new Failure(503,"INTENT_CONTRIBUTION_UNAVAILABLE");
   // Schema-bounded metadata, including one concurrent extra row, admitted before JDBC transfer.
   long admitted=count+1;budget.admitCapture(admitted*fields*2048,admitted*fields*512,admitted*fields*1024,3);
   for(long n=0;n<admitted;n++)budget.row(fields);
   String bounded=sql.substring(0,sql.lastIndexOf(" LIMIT "))+" LIMIT "+admitted+(lock?" FOR UPDATE":"");
   var rows=j.queryForList(bounded,meeting);if(rows.size()!=count)throw new Failure(503,"INTENT_CONTRIBUTION_UNAVAILABLE");
   var saved=new ArrayList<Map<String,Object>>();
   for(var row:rows){var normalized=new LinkedHashMap<String,Object>();for(var cell:row.entrySet()){
    Object value=JdbcPublicationQueries.normalizeMetadata(cell.getValue());
    if(value instanceof String text){if(text.codePointCount(0,text.length())>512)throw new Failure(503,"INTENT_CONTRIBUTION_UNAVAILABLE");budget.capture(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,text.codePointCount(0,text.length()),text.length(),3);}
    normalized.put(cell.getKey(),value);
   }saved.add(Collections.unmodifiableMap(normalized));}
   all.put(entry.getKey(),List.copyOf(saved));
  }return Collections.unmodifiableMap(all);
 }
}
