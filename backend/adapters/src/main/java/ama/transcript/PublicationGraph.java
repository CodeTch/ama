package ama.transcript;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import ama.contract.TaskContracts.TaskLease;
import java.time.LocalDateTime;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;

/** Bounded publication controls and complete shared task union, declared before root acquisition. */
final class PublicationGraph {
 record Plan(String meeting,List<MinutesPendingDiscovery.Query> controls,List<MinutesPendingDiscovery.Query> tasks,Map<String,List<Map<String,Object>>> rows,TranscriptAcquisitionBudget.Ledger budget){}
 static Plan discover(DataSource source,String meeting,TranscriptAcquisitionBudget.Ledger budget){var j=new JdbcTemplate(source);j.setQueryTimeout(2);var controls=new ArrayList<MinutesPendingDiscovery.Query>();var tasks=new ArrayList<MinutesPendingDiscovery.Query>();var rows=new LinkedHashMap<String,List<Map<String,Object>>>();
  controls.add(q("publication_submission","task_id publication_id submission_no confirmation_id provider_id business_key library_id folder_id requester_id logical_request_hash execution_request_hash created_at local_state local_decided_at local_reason local_decided_by local_attempt_no local_execution_id","task_id","publication_id IN (SELECT id FROM publication WHERE meeting_id=?)",meeting,List.of("OCTET_LENGTH(request_manifest) AS request_manifest_bytes","CHAR_LENGTH(request_manifest) AS request_manifest_scalars","OCTET_LENGTH(CONVERT(request_manifest USING utf16))/2 AS request_manifest_utf16")));
  controls.add(q("external_document_mapping","publication_id provider_id external_document_id authority_state verified_at","publication_id","publication_id IN (SELECT id FROM publication WHERE meeting_id=?)",meeting,List.of("OCTET_LENGTH(document_url) AS document_url_bytes","CHAR_LENGTH(document_url) AS document_url_scalars","OCTET_LENGTH(CONVERT(document_url USING utf16))/2 AS document_url_utf16")));
  controls.add(q("publication_attempt","id publication_id confirmation_id operation_id kind outcome created_at finished_at execution_id","id","publication_id IN (SELECT id FROM publication WHERE meeting_id=?)",meeting,List.of()));
  for(String table:List.of("durable_task","task_attempt","external_operation","external_execution")){
   var shape=MinutesSourceRows.SHAPES.get(table);var cols=new ArrayList<>(shape.columns().stream().filter(c->!MinutesSourceRows.CONTENT.contains(c)).toList());String where=switch(table){case "durable_task"->"subject_kind='MEETING' AND subject_id=?";case "task_attempt"->"task_id IN (SELECT id FROM durable_task WHERE subject_kind='MEETING' AND subject_id=?)";case "external_operation"->"subject_kind='MEETING' AND subject_id=?";default->"operation_id IN (SELECT id FROM external_operation WHERE subject_kind='MEETING' AND subject_id=?)";};
   if(table.equals("external_execution")){cols.add("OCTET_LENGTH(request_manifest) AS request_manifest_bytes");cols.add("SUBSTRING(no_effect_evidence,11,36) AS proof_store");cols.add("SUBSTRING(no_effect_evidence,48,40) AS proof_receipt");cols.add("SUBSTRING(no_effect_evidence,89,64) AS proof_digest");cols.add("CASE WHEN no_effect_evidence IS NOT NULL AND OCTET_LENGTH(no_effect_evidence)>0 THEN 1 ELSE 0 END AS no_effect_evidence_present");cols.add("CASE WHEN no_effect_evidence REGEXP '^knowledge:[0-9a-f-]{36}:receipt-[0-9a-f]{32}:[0-9a-f]{64}$' THEN 1 ELSE 0 END AS provider_proof_reference_shape");}if(table.equals("external_operation"))cols.add("CASE WHEN external_result_id IS NOT NULL THEN 1 ELSE 0 END AS external_result_present");tasks.add(new MinutesPendingDiscovery.Query(table,cols,table.equals("external_execution")?List.of("operation_id","execution_no"):shape.keys(),where,List.of(meeting)));
  }
  for(var q:controls)rows.put(q.table(),read(j,q,false,budget));for(var q:tasks)rows.put(q.table(),read(j,q,false,budget));var plan=new Plan(meeting,List.copyOf(controls),List.copyOf(tasks),Map.copyOf(rows),budget);reserve(plan,false);return plan;
 }
 static Plan fresh(DataSource source,Plan plan){var j=new JdbcTemplate(source);var rows=new LinkedHashMap<String,List<Map<String,Object>>>();for(var q:plan.controls())rows.put(q.table(),read(j,q,true,plan.budget()));for(var q:plan.tasks())rows.put(q.table(),read(j,q,true,plan.budget()));return new Plan(plan.meeting(),plan.controls(),plan.tasks(),Map.copyOf(rows),plan.budget());}
 private static List<Map<String,Object>> read(JdbcTemplate j,MinutesPendingDiscovery.Query q,boolean lock,TranscriptAcquisitionBudget.Ledger budget){
  var all=new ArrayList<Map<String,Object>>();List<String> order=!lock&&q.table().equals("durable_task")?List.of("created_at","id"):q.keys();List<Object> last=null;int cap=Math.min(256,budget.taskLimit());
  for(;;){budget.check();int limit=(int)Math.min(16,Math.min(cap-all.size()+1,budget.remainingRows()+1));require(limit>0,BUDGET_EXCEEDED);var args=new ArrayList<>(q.args());String after="";if(last!=null){after=" AND ("+String.join(",",order)+") > ("+String.join(",",Collections.nCopies(order.size(),"?"))+")";args.addAll(last);}String from=" FROM "+q.table()+" WHERE ("+q.where()+")"+after;String tail=" ORDER BY "+String.join(",",order)+" LIMIT "+limit;
   long count=j.queryForObject("SELECT COUNT(*) FROM (SELECT 1"+from+tail+") publication_count",Long.class,args.toArray());if(count==0)break;require(count<=cap-all.size()&&count<=budget.remainingRows(),BUDGET_EXCEEDED);
   // All projected strings are bounded metadata (schema maximum VARCHAR(512)); the
   // conservative transient reservation includes a concurrent extra row before transfer.
   long admitted=Math.min(limit,count+1);long cells=Math.multiplyExact(admitted,q.columns().size());budget.admitCapture(cells*2048,cells*512,cells*1024,2);
   for(long n=0;n<admitted;n++)budget.row(q.columns().size());
   var page=j.queryForList("SELECT "+String.join(",",q.columns())+from+" ORDER BY "+String.join(",",order)+" LIMIT "+admitted+(lock?" FOR UPDATE":""),args.toArray());require(page.size()==count,PROVENANCE_UNAVAILABLE);
   for(var raw:page){var normalized=new LinkedHashMap<String,Object>();for(var cell:raw.entrySet()){Object v=cell.getValue();if(v instanceof Number number)v=new java.math.BigDecimal(number.toString()).longValueExact();else if(v instanceof java.sql.Timestamp timestamp)v=timestamp.toLocalDateTime().toString();else if(v instanceof java.sql.Date date)v=date.toLocalDate().toString();if(v instanceof String text){require(text.codePointCount(0,text.length())<=512,BUDGET_EXCEEDED);budget.capture(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,text.codePointCount(0,text.length()),text.length(),3);}normalized.put(cell.getKey(),v);}all.add(Collections.unmodifiableMap(normalized));}
   if(page.size()<limit)break;var row=page.getLast();last=order.stream().map(row::get).toList();
  }all.sort((a,b)->{for(String key:q.keys()){Object x=a.get(key),y=b.get(key);int comparison=x instanceof Number&&y instanceof Number?Long.compare(((Number)x).longValue(),((Number)y).longValue()):((String)x).compareTo((String)y);if(comparison!=0)return comparison;}return 0;});return List.copyOf(all);
 }
 private static MinutesPendingDiscovery.Query q(String table,String columns,String key,String where,String meeting,List<String> extra){var cols=new ArrayList<>(List.of(columns.split(" ")));cols.addAll(extra);return new MinutesPendingDiscovery.Query(table,List.copyOf(cols),List.of(key),where,List.of(meeting));}
 /** Internal signal only after every declared task-union row was freshly compared. No capture is accepted. */
 static final class PreparationRenewed extends RuntimeException {
  private final TaskLease lease;
  private PreparationRenewed(TaskLease lease){super("Same-lease renewal requires fresh publication preparation",null,false,false);this.lease=lease;}
  boolean issuedFor(TaskLease expected){return lease==expected;}
 }
 static boolean onlyOwnExpiryChanged(TaskLease lease,Map<String,List<Map<String,Object>>> before,Map<String,List<Map<String,Object>>> after){
  var tables=Set.of("durable_task","task_attempt","external_operation","external_execution");
  if(lease==null||!before.keySet().equals(tables)||!after.keySet().equals(tables))return false;
  for(String table:tables)if(!table.equals("durable_task")&&!Objects.equals(before.get(table),after.get(table)))return false;
  var oldRows=before.get("durable_task");var newRows=after.get("durable_task");if(oldRows.size()!=newRows.size())return false;
  boolean changed=false;
  for(int n=0;n<oldRows.size();n++){
   var old=oldRows.get(n);var fresh=newRows.get(n);if(old.equals(fresh))continue;
   if(changed||!lease.taskId().equals(old.get("id"))||!"RUNNING".equals(old.get("state"))||!lease.leaseOwner().equals(old.get("lease_owner"))||!Long.valueOf(lease.attempt()).equals(old.get("attempt")))return false;
   var prior=new LinkedHashMap<>(old);var current=new LinkedHashMap<>(fresh);Object first=prior.remove("expires_at"),next=current.remove("expires_at");
   if(!prior.equals(current))return false;
   try{var previous=expiry(first);var following=expiry(next);if(previous==null||following==null||!following.isAfter(previous))return false;}
   catch(java.time.DateTimeException invalid){return false;}
   changed=true;
  }
  return changed;
 }
 private static LocalDateTime expiry(Object value){
  // Connector/J returns DATETIME as LocalDateTime; the metadata reader also supports ISO-normalized Timestamp values.
  return value instanceof LocalDateTime time?time:value instanceof String text?LocalDateTime.parse(text):null;
 }
 static void requirePreparationUnion(TaskLease lease,Map<String,List<Map<String,Object>>> before,Map<String,List<Map<String,Object>>> after){
  var tables=Set.of("durable_task","task_attempt","external_operation","external_execution");
  require(lease!=null&&before.keySet().equals(tables)&&after.keySet().equals(tables),PROVENANCE_UNAVAILABLE);
  if(before.equals(after))return;
  if(onlyOwnExpiryChanged(lease,before,after))throw new PreparationRenewed(lease);
  throw fail(PROVENANCE_UNAVAILABLE);
 }
 static void lockForPreparation(DataSource source,Plan plan,boolean controls,TaskLease lease){
  if(controls||lease==null){lock(source,plan,controls);return;}
  var j=new JdbcTemplate(source);j.setQueryTimeout(2);var before=new LinkedHashMap<String,List<Map<String,Object>>>();var after=new LinkedHashMap<String,List<Map<String,Object>>>();
  // Preserve declaration and lock order, and compare the complete union before issuing a renewal signal.
  for(var query:plan.tasks()){before.put(query.table(),plan.rows().get(query.table()));after.put(query.table(),read(j,query,true,plan.budget()));}
  requirePreparationUnion(lease,before,after);
 }
 static void lock(DataSource source,Plan plan,boolean controls){var j=new JdbcTemplate(source);j.setQueryTimeout(2);for(var q:controls?plan.controls():plan.tasks())require(read(j,q,true,plan.budget()).equals(plan.rows().get(q.table())),PROVENANCE_UNAVAILABLE);}
 static void lockReplacement(DataSource source,Plan plan,String insertedTask){var j=new JdbcTemplate(source);j.setQueryTimeout(2);for(var query:plan.tasks()){var q=query;if(q.table().equals("durable_task")){var args=new ArrayList<>(q.args());args.add(insertedTask);q=new MinutesPendingDiscovery.Query(q.table(),q.columns(),q.keys(),"("+q.where()+") AND id<>?",args);}require(read(j,q,true,plan.budget()).equals(plan.rows().get(q.table())),PROVENANCE_UNAVAILABLE);}}
 static void reserve(Plan plan,boolean prospective){var rows=plan.rows();long cap=Math.min(256,plan.budget().taskLimit());long tasks=rows.get("durable_task").size()+(prospective?1:0),attempts=rows.get("task_attempt").size()+(prospective?3:0),operations=rows.get("external_operation").size()+(prospective?1:0),executions=rows.get("external_execution").size()+(prospective?1:0),pubAttempts=rows.get("publication_attempt").size()+(prospective?1:0);for(var task:rows.get("durable_task")){String type=(String)task.get("task_type");require(Set.of("TRANSCRIPT_IMPORT_V1","MINUTES_GENERATE_V1","KNOWLEDGE_PUBLISH_V1","RECORDING_ARCHIVE_V1").contains(type),CAPABILITY_UNAVAILABLE);if(Set.of("READY","RUNNING").contains(task.get("state")))attempts+=((Number)task.get("max_attempts")).longValue()-((Number)task.get("attempt")).longValue();if(Set.of("MINUTES_GENERATE_V1","KNOWLEDGE_PUBLISH_V1").contains(type)&&Set.of("READY","RUNNING","RECONCILING").contains(task.get("state"))&&rows.get("external_execution").stream().noneMatch(e->task.get("id").equals(e.get("task_id")))){operations++;executions++;if("KNOWLEDGE_PUBLISH_V1".equals(type))pubAttempts++;}}require(tasks<=cap&&attempts<=cap&&operations<=cap&&executions<=cap&&pubAttempts<=cap&&rows.get("publication_submission").size()+(prospective?1:0)<=cap,BUDGET_EXCEEDED);}
}
