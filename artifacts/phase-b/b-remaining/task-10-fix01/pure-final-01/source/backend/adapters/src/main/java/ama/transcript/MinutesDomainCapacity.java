package ama.transcript;
import java.util.*;
import ama.contract.TaskContracts.TaskSpec;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
/** Existing bounded selectors' capacity, not a business quota. Called on root-protected exact
 * declarations before producer effects; subsequent claims consume, rather than enlarge, reservations.
 * Terminal history is retained. Only READY/RUNNING can acquire another queue attempt. */
final class MinutesDomainCapacity {
 static final int SELECTOR_LIMIT=256;
 static final List<String> FAMILIES=List.of("generation_input","durable_task","task_attempt","external_operation","external_execution","generation_candidate");
 private static final Set<String> TERMINAL=Set.of("SUCCEEDED","FAILED","STALE","CANCELLED");
 private MinutesDomainCapacity(){}
 static Map<String,List<Map<String,Object>>> merge(Map<String,List<Map<String,Object>>> first,Map<String,List<Map<String,Object>>> second){
  var result=new LinkedHashMap<String,List<Map<String,Object>>>();for(String family:FAMILIES){var rows=new LinkedHashMap<List<Object>,Map<String,Object>>();for(var part:List.of(first,second))for(var row:part.getOrDefault(family,List.of())){var key=family.equals("task_attempt")?List.of(row.get("task_id"),row.get("attempt")):List.of(row.get("id"));rows.putIfAbsent(key,row);}result.put(family,List.copyOf(rows.values()));}return result;
 }
 static Map<String,Long> reserve(Map<String,List<Map<String,Object>>> rows,int taskLimit,int inputLimit,TaskSpec prospective){
  require(taskLimit>0&&inputLimit>0,INVALID_ARGUMENT);var counts=new LinkedHashMap<String,Long>();for(String table:FAMILIES)counts.put(table,(long)rows.getOrDefault(table,List.of()).size());
  var attempts=rows.getOrDefault("task_attempt",List.of());var operations=rows.getOrDefault("external_operation",List.of());var executions=rows.getOrDefault("external_execution",List.of());var candidates=rows.getOrDefault("generation_candidate",List.of());
  var tasks=rows.getOrDefault("durable_task",List.of());var producers=new HashSet<String>();
  for(var task:tasks){String id=(String)task.get("id"),type=(String)task.get("task_type"),state=(String)task.get("state");require(producers.add((String)task.get("producer_key")),PROVENANCE_UNAVAILABLE);boolean minutes="MINUTES_GENERATE_V1".equals(type);require(minutes||"TRANSCRIPT_IMPORT_V1".equals(type),CAPABILITY_UNAVAILABLE);
   long attempt=number(task,"attempt"),maximum=number(task,"max_attempts");require(attempt>=0&&maximum==3&&attempt<=maximum,PROVENANCE_UNAVAILABLE);var owned=attempts.stream().filter(a->id.equals(a.get("task_id"))).toList();require(owned.size()==attempt,PROVENANCE_UNAVAILABLE);var seen=new HashSet<Long>();for(var row:owned){long n=number(row,"attempt");require(n>0&&n<=attempt&&seen.add(n),PROVENANCE_UNAVAILABLE);}
   require(TERMINAL.contains(state)||Set.of("READY","RUNNING","RECONCILING").contains(state),PROVENANCE_UNAVAILABLE);
   if(Set.of("READY","RUNNING").contains(state))add(counts,"task_attempt",maximum-attempt);
   if(minutes&&!TERMINAL.contains(state)){
    // This producer fixes one immutable input/business key and executionNo=1. Generic Task7
    // retry cannot create a different producer for that input; its two-spec gate rejects it.
    String key="minutes:"+task.get("input_id");if(operations.stream().noneMatch(o->key.equals(o.get("business_key"))))add(counts,"external_operation",1);
    if(executions.stream().noneMatch(e->id.equals(e.get("task_id"))))add(counts,"external_execution",1);
    if(candidates.stream().noneMatch(c->id.equals(c.get("task_id"))))add(counts,"generation_candidate",1);
   }
  }
  if(prospective!=null&&!producers.contains(prospective.producerKey())){
   boolean minutes="MINUTES_GENERATE_V1".equals(prospective.type());require(minutes||"TRANSCRIPT_IMPORT_V1".equals(prospective.type()),CAPABILITY_UNAVAILABLE);require(prospective.maxAttempts()==3,PROVENANCE_UNAVAILABLE);add(counts,"durable_task",1);add(counts,"task_attempt",prospective.maxAttempts());
   if(minutes){if(rows.getOrDefault("generation_input",List.of()).stream().noneMatch(r->prospective.input().id().equals(r.get("id"))))add(counts,"generation_input",1);for(String table:List.of("external_operation","external_execution","generation_candidate"))add(counts,table,1);}
  }
  for(var count:counts.entrySet()){long bound=count.getKey().equals("durable_task")?Math.min(SELECTOR_LIMIT,taskLimit):count.getKey().equals("generation_input")?Math.min(SELECTOR_LIMIT,inputLimit):SELECTOR_LIMIT;require(count.getValue()<=bound,BUDGET_EXCEEDED);}return Collections.unmodifiableMap(counts);
 }
 private static long number(Map<String,Object> row,String field){return ((Number)row.get(field)).longValue();}
 private static void add(Map<String,Long> counts,String table,long n){counts.put(table,Math.addExact(counts.get(table),n));}
}
