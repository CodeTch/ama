package ama.transcript;
import ama.meeting.WorkspaceReadBudget;import org.springframework.jdbc.core.JdbcTemplate;import java.util.*;
/** Bounded persisted parent relations, never canonical producer/content/readiness proof.
 * Internal declaration and lock phases must be composed under the caller's authentic owner gate.
 */
final class WorkspaceTaskRelations {
 private static final List<String> RANK=List.of("publication","final_confirmation","publication_submission","transcript_revision","transcript_confirmation","transcription_input","input_batch","input_batch_fingerprint_profile","template_revision","exclusion_revision","generation_input");
 private static final Map<String,String> COLUMNS=Map.ofEntries(
  Map.entry("publication","id,meeting_id"),Map.entry("final_confirmation","id,meeting_id"),Map.entry("publication_submission","task_id,publication_id,confirmation_id,submission_no,logical_request_hash,business_key"),
  Map.entry("transcript_revision","id,meeting_id,kind"),Map.entry("transcript_confirmation","id,meeting_id,revision_id"),
  Map.entry("transcription_input","id,meeting_id,batch_id,kind,material_mode,material_snapshot_id,source_revision_id,model_config_version,prompt_version,parser_version,manifest_schema_version,input_fingerprint,base_revision,cancel_epoch"),
  Map.entry("input_batch","id,meeting_id,kind,base_revision"),Map.entry("input_batch_fingerprint_profile","batch_id,profile"),Map.entry("template_revision","id"),Map.entry("exclusion_revision","id,meeting_id"),
  Map.entry("generation_input","id,meeting_id,transcript_confirmation_id,template_revision_id,exclusion_revision_id,base_revision,input_fingerprint,scope,target_element_id,material_snapshot_id"));
 private record Key(String table,String id){}
 private final JdbcTemplate jdbc;private final WorkspaceReadBudget budget;private final Map<Key,Map<String,Object>> parents=new HashMap<>();private boolean captured,locked;
 WorkspaceTaskRelations(JdbcTemplate jdbc,WorkspaceReadBudget budget){this.jdbc=Objects.requireNonNull(jdbc);this.budget=Objects.requireNonNull(budget);}
 void capture(List<WorkspaceTaskScan.Metadata> tasks){if(captured)throw unavailable();captured=true;for(var task:tasks){budget.check();var spec=task.spec();String meeting=spec.subject().id();
  switch(spec.type()){
   case "TRANSCRIPT_IMPORT_V1" -> {
    var input=parent("transcription_input",spec.input().id());same(input,"meeting_id",meeting);same(input,"kind","IMPORT");same(input,"material_mode","EMPTY");empty(input,"material_snapshot_id","source_revision_id","model_config_version","prompt_version");if(input.get("parser_version")==null)throw unavailable();number(input,"manifest_schema_version",1);same(input,"input_fingerprint",spec.inputFingerprint());number(input,"base_revision",spec.businessVersion());number(input,"cancel_epoch",spec.cancelEpoch());
    String batchId=id(input,"batch_id");var batch=parent("input_batch",batchId);same(batch,"meeting_id",meeting);same(batch,"kind","TRANSCRIPT");number(batch,"base_revision",spec.businessVersion());same(parent("input_batch_fingerprint_profile",batchId),"profile","ama.input-batch.v1");
   }
   case "MINUTES_GENERATE_V1" -> {
    var input=parent("generation_input",spec.input().id());same(input,"meeting_id",meeting);same(input,"scope","FULL");empty(input,"target_element_id","material_snapshot_id");same(input,"input_fingerprint",spec.inputFingerprint());number(input,"base_revision",spec.businessVersion());
    var confirmation=parent("transcript_confirmation",id(input,"transcript_confirmation_id"));same(confirmation,"meeting_id",meeting);var revision=parent("transcript_revision",id(confirmation,"revision_id"));same(revision,"meeting_id",meeting);same(revision,"kind","WORKING");parent("template_revision",id(input,"template_revision_id"));same(parent("exclusion_revision",id(input,"exclusion_revision_id")),"meeting_id",meeting);
   }
   case "KNOWLEDGE_PUBLISH_V1" -> {
    var submission=parent("publication_submission",task.id());same(submission,"publication_id",spec.input().id());same(submission,"logical_request_hash",spec.inputFingerprint());long sequence=number(submission,"submission_no");if(sequence<1)throw unavailable();String confirmation=id(submission,"confirmation_id");same(submission,"business_key",spec.input().id()+":"+confirmation);
    same(parent("publication",spec.input().id()),"meeting_id",meeting);var original=parent("final_confirmation",confirmation);same(original,"meeting_id",meeting);
   }
   default -> throw unavailable();
  }
 }}
 void lockAndCompare(){if(!captured||locked)throw unavailable();locked=true;
  for(String table:RANK)for(var entry:parents.entrySet().stream().filter(e->e.getKey().table().equals(table)).sorted(Comparator.comparing(e->e.getKey().id())).toList())if(!entry.getValue().equals(read(entry.getKey(),true)))throw unavailable();budget.check();
 }
 private Map<String,Object> parent(String table,String id){try{ama.contract.OutboxContracts.uuid(id);}catch(RuntimeException invalid){throw unavailable();}var key=new Key(table,id);return parents.computeIfAbsent(key,k->read(k,false));}
 private Map<String,Object> read(Key key,boolean lock){
  budget.check();String columns=COLUMNS.get(key.table());if(columns==null)throw unavailable();String pk=key.table().equals("publication_submission")?"task_id":key.table().equals("input_batch_fingerprint_profile")?"batch_id":"id";
  long bound=key.table().equals("transcription_input")?4096:key.table().equals("publication_submission")?2048:512;budget.reserve(1,16,bound,bound,bound,4,false);
  var value=jdbc.query("SELECT "+columns+" FROM "+key.table()+" WHERE "+pk+"=? LIMIT 2"+(lock?" FOR UPDATE":""),rs->{if(!rs.next())throw unavailable();var row=new LinkedHashMap<String,Object>();for(String column:columns.split(","))row.put(column,rs.getObject(column));if(rs.next())throw unavailable();return Collections.unmodifiableMap(row);},key.id());
  budget.check();same(value,pk,key.id());return value;
 }
 private static String id(Map<String,Object> row,String key){Object value=row.get(key);if(!(value instanceof String id))throw unavailable();try{ama.contract.OutboxContracts.uuid(id);return id;}catch(RuntimeException invalid){throw unavailable();}}
 private static void same(Map<String,Object> row,String key,Object value){if(!Objects.equals(value,row.get(key)))throw unavailable();}
 private static void empty(Map<String,Object> row,String...keys){for(String key:keys)if(row.get(key)!=null)throw unavailable();}
 private static long number(Map<String,Object> row,String key){Object value=row.get(key);if(!(value instanceof Number n))throw unavailable();long result=n.longValue();if(result<0||result==Long.MAX_VALUE||!n.toString().equals(Long.toString(result)))throw unavailable();return result;}
 private static void number(Map<String,Object> row,String key,long expected){if(number(row,key)!=expected)throw unavailable();}
 private static WorkspaceReadBudget.Unavailable unavailable(){return new WorkspaceReadBudget.Unavailable();}
}
