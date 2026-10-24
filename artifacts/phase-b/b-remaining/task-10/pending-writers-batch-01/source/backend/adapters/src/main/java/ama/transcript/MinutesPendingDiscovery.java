package ama.transcript;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import ama.contract.TranscriptContracts.Failure;
/** Complete bounded detached headers; no text/source I/O and no authority in these values. */
final class MinutesPendingDiscovery {
 static final List<String> DOMAIN=List.of("generation_input","generation_candidate");
 static final List<String> TASKS=List.of("durable_task","task_attempt","external_operation","external_execution");
 record Query(String table,List<String> columns,List<String> keys,String where,List<Object> args){}
 record Plan(String meeting,Map<String,List<Map<String,Object>>> rows,List<Query> queries){}
 static Plan discover(DataSource source,String meeting){return discover(source,meeting,List.of());}
 static Plan discover(DataSource source,String meeting,Collection<String> additionalTasks){var extra=new TreeSet<>(additionalTasks);if(extra.size()>256)throw unavailable();var j=new JdbcTemplate(source);j.setQueryTimeout(2);var queries=new ArrayList<Query>();var rows=new LinkedHashMap<String,List<Map<String,Object>>>();
  var candidates=query("generation_candidate",MinutesGraphCapture.CANDIDATE.columns(),List.of("id"),"meeting_id=? AND state='PENDING'",List.of(meeting));
  var taskArgs=new ArrayList<Object>();taskArgs.add(meeting);taskArgs.addAll(extra);
  var tasks=query("durable_task",columns("durable_task"),List.of("id"),"subject_kind='MEETING' AND subject_id=? AND (task_type='MINUTES_GENERATE_V1' OR input_kind='GENERATION_INPUT' OR "+in("id",extra.size())+")",taskArgs);
  var c=read(j,candidates,false);var t=read(j,tasks,false);var inputs=new TreeSet<String>();for(var row:c)inputs.add((String)row.get("input_id"));for(var row:t){if("MINUTES_GENERATE_V1".equals(row.get("task_type"))||"GENERATION_INPUT".equals(row.get("input_kind"))){if(!"MINUTES_GENERATE_V1".equals(row.get("task_type"))||!"GENERATION_INPUT".equals(row.get("input_kind")))throw unavailable();inputs.add((String)row.get("input_id"));}}
  if(!t.stream().map(row->(String)row.get("id")).toList().containsAll(extra))throw unavailable();
  var input=query("generation_input",List.of("id","meeting_id","base_revision","input_fingerprint"),List.of("id"),in("id",inputs.size()),new ArrayList<>(inputs));
  var publications=query("publication",List.of("id","meeting_id","confirmation_id","state","revision"),List.of("id"),"meeting_id=?",List.of(meeting));
  var finals=query("final_confirmation",List.of("id","meeting_id","meeting_revision"),List.of("id"),"meeting_id=?",List.of(meeting));
  var taskIds=t.stream().map(row->(String)row.get("id")).sorted().toList();
  var attempts=query("task_attempt",columns("task_attempt"),List.of("task_id","attempt"),in("task_id",taskIds.size()),new ArrayList<>(taskIds));
  var operations=query("external_operation",columns("external_operation"),List.of("id"),"subject_kind='MEETING' AND subject_id=? AND operation_kind='MINUTES_GENERATE'",List.of(meeting));var o=read(j,operations,false);var operationIds=o.stream().map(row->(String)row.get("id")).sorted().toList();
  var executionArgs=new ArrayList<Object>(taskIds);executionArgs.addAll(operationIds);var executions=query("external_execution",columns("external_execution"),List.of("operation_id","execution_no"),"("+in("task_id",taskIds.size())+") OR ("+in("operation_id",operationIds.size())+")",executionArgs);
  for(var q:List.of(publications,finals,input,candidates,tasks,attempts,operations,executions)){queries.add(q);rows.put(q.table(),q==candidates?c:q==tasks?t:q==operations?o:read(j,q,false));}
  return new Plan(meeting,Collections.unmodifiableMap(rows),List.copyOf(queries));
 }
 static void lock(DataSource source,Plan plan,List<String> tables){var j=new JdbcTemplate(source);j.setQueryTimeout(2);for(String table:tables){var q=plan.queries().stream().filter(x->x.table().equals(table)).findFirst().orElseThrow();if(!read(j,q,true).equals(plan.rows().get(table)))throw unavailable();}}
 static void validate(Plan plan,long revision){
  var inputs=index(plan.rows().get("generation_input"));var tasks=index(plan.rows().get("durable_task"));
  for(var input:inputs.values())if(!plan.meeting().equals(input.get("meeting_id")))throw unavailable();
  for(var task:tasks.values()){if(!"GENERATION_INPUT".equals(task.get("input_kind")))continue;var input=inputs.get(task.get("input_id"));if(input==null||!same(task,"input_fingerprint",input,"input_fingerprint")||number(task,"business_version")!=number(input,"base_revision"))throw unavailable();}
  for(var candidate:plan.rows().get("generation_candidate")){
   var input=inputs.get(candidate.get("input_id"));var task=tasks.get(candidate.get("task_id"));long base=number(candidate,"base_revision");
   if(base>revision||input==null||task==null||!"MINUTES_GENERATE_V1".equals(task.get("task_type"))||!same(candidate,"input_id",task,"input_id")||base!=number(input,"base_revision")||!same(candidate,"input_fingerprint",input,"input_fingerprint")||!"SUCCEEDED".equals(task.get("state"))||!"GENERATION_CANDIDATE".equals(task.get("output_kind"))||!same(candidate,"id",task,"output_id")||candidate.get("minutes_revision_id")==null||candidate.get("element_set_id")==null||candidate.get("decided_at")!=null)throw unavailable();
  }
 }
 static Map<String,Map<String,Object>> index(List<Map<String,Object>> rows){var m=new HashMap<String,Map<String,Object>>();for(var row:rows)if(m.put((String)row.get("id"),row)!=null)throw unavailable();return m;}
 private static List<String> columns(String table){return MinutesSourceRows.SHAPES.get(table).columns().stream().filter(c->!MinutesSourceRows.CONTENT.contains(c)).toList();}
 private static Query query(String table,List<String> cols,List<String> keys,String where,List<Object> args){return new Query(table,cols,keys,where,List.copyOf(args));}
 private static String in(String column,int count){return count==0?"1=0":column+" IN ("+String.join(",",Collections.nCopies(count,"?"))+")";}
 static List<Map<String,Object>> read(JdbcTemplate j,Query q,boolean lock){var rows=j.queryForList("SELECT "+String.join(",",q.columns())+" FROM "+q.table()+" WHERE "+q.where()+" ORDER BY "+String.join(",",q.keys())+" LIMIT 257"+(lock?" FOR UPDATE":""),q.args().toArray());if(rows.size()>256)throw unavailable();return rows.stream().map(row->Collections.unmodifiableMap(new LinkedHashMap<>(row))).toList();}
 static long number(Map<String,Object> row,String column){return ((Number)row.get(column)).longValue();}
 private static boolean same(Map<String,Object>a,String x,Map<String,Object>b,String y){return Objects.equals(a.get(x),b.get(y));}
 static Failure unavailable(){return new Failure(503,"MINUTES_TRANSITION_UNAVAILABLE");}
}
