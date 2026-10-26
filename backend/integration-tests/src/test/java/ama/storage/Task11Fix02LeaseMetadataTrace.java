package ama.storage;
import java.sql.*;import java.lang.reflect.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import javax.sql.DataSource;import static org.junit.jupiter.api.Assertions.*;
/** Exact safe PublicationGraph task metadata, never request bodies or credentials. */
final class Task11Fix02LeaseMetadataTrace {
 record Read(long start,long end,long callId,long thread,String phase,String sql,List<Map<String,Object>> rows){}
 record Renewal(long completed,int affected){}
 record Statement(long sequence,long callId,long thread,String phase,String sql){}
 final AtomicLong callIds=new AtomicLong();final ThreadLocal<Long> calls=ThreadLocal.withInitial(()->0L);final ThreadLocal<String> rejection=ThreadLocal.withInitial(()->"NONE");final List<Statement> statements=new CopyOnWriteArrayList<>();
 long beginCall(){calls.set(callIds.incrementAndGet());return sequence.get();}void endCall(){calls.remove();}
 Map<String,Object> reject(String reason){rejection.set(reason);return null;}
 final AtomicLong sequence=new AtomicLong();final List<Read> reads=new CopyOnWriteArrayList<>();final List<Renewal> renewals=new CopyOnWriteArrayList<>();final Task11Fix02TransferTrace trace;
 Task11Fix02LeaseMetadataTrace(Task11Fix02TransferTrace trace){this.trace=trace;}
 long checkpoint(){return sequence.get();}
 DataSource wrap(DataSource actual){return (DataSource)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{DataSource.class},(p,m,a)->{Object value=Task11ReplacementRollbackIT.invoke(actual,m,a);if(!(value instanceof Connection c))return value;return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(cp,cm,ca)->{Object result=Task11ReplacementRollbackIT.invoke(c,cm,ca);if(!(result instanceof PreparedStatement statement)||ca==null||ca.length==0||!(ca[0] instanceof String sql))return result;return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(sp,sm,sa)->{
  long start=sequence.incrementAndGet(),call=calls.get(),thread=Thread.currentThread().threadId();String phase=trace.phase.get();if(sm.getName().startsWith("execute"))statements.add(new Statement(start,call,thread,phase,sql));Object answer=Task11ReplacementRollbackIT.invoke(statement,sm,sa);
  if(sm.getName().equals("executeUpdate")&&sql.startsWith("UPDATE durable_task SET expires_at="))renewals.add(new Renewal(sequence.incrementAndGet(),((Number)answer).intValue()));
  if(!sm.getName().equals("executeQuery")||!(answer instanceof ResultSet rs)||!sql.startsWith("SELECT id,task_type,pool,")||!sql.contains("FROM durable_task WHERE (subject_kind='MEETING' AND subject_id=?)"))return answer;
  var values=new ArrayList<Map<String,Object>>();return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ResultSet.class},(rp,rm,ra)->{
   Object field=Task11ReplacementRollbackIT.invoke(rs,rm,ra);
   if(rm.getName().equals("next")&&Boolean.TRUE.equals(field)){var row=new TreeMap<String,Object>();var metadata=rs.getMetaData();for(int i=1;i<=metadata.getColumnCount();i++){String key=metadata.getColumnLabel(i);assertFalse(key.contains("manifest")||key.contains("evidence")||key.contains("secret"));Object cell=rs.getObject(i);row.put(key,cell==null?null:cell.toString());}values.add(Collections.unmodifiableMap(row));}
   if(rm.getName().equals("close")){values.sort(Comparator.comparing(r->(String)r.get("id")));reads.add(new Read(start,sequence.incrementAndGet(),call,thread,phase,sql,List.copyOf(values)));}return field;
  });
 });});});}
 Map<String,Object> onlyCurrentExpiry(long checkpoint,String phase,String task,long attempt,String owner){
  long call=calls.get(),thread=Thread.currentThread().threadId(),end=sequence.get();rejection.set("NONE");if(call==0)return reject("NO_CURRENT_CALL");
  var selected=reads.stream().filter(r->r.start()>checkpoint&&r.end()<=end&&r.callId()==call&&r.thread()==thread&&r.phase().equals(phase)).sorted(Comparator.comparingLong(Read::start)).toList();if(selected.size()<2)return reject("INSUFFICIENT_SAME_CALL_THREAD_READS");var after=selected.getLast();if(!after.sql().endsWith("FOR UPDATE"))return reject("LAST_READ_NOT_LOCKED");var before=selected.get(selected.size()-2);
  var actualSql=statements.stream().filter(r->r.sequence()>checkpoint&&r.sequence()<=end&&r.callId()==call&&r.thread()==thread&&r.phase().equals(phase)).toList();if(actualSql.isEmpty()||!actualSql.getLast().sql().equals(after.sql())||actualSql.getLast().sequence()!=after.start())return reject("FAILURE_AFTER_DIFFERENT_SQL");
  if(before.rows().size()!=after.rows().size())return reject("INVENTORY_CHANGED");String oldExpiry=null,newExpiry=null;int changed=0;
  for(int i=0;i<before.rows().size();i++){var left=new TreeMap<>(before.rows().get(i));var right=new TreeMap<>(after.rows().get(i));if(!Objects.equals(left.get("id"),right.get("id")))return reject("ROW_IDS_CHANGED");if(task.equals(left.get("id"))){if(!"RUNNING".equals(left.get("state"))||!Long.toString(attempt).equals(left.get("attempt"))||!owner.equals(left.get("lease_owner")))return reject("LEASE_BINDING_CHANGED");oldExpiry=(String)left.remove("expires_at");newExpiry=(String)right.remove("expires_at");if(!Objects.equals(oldExpiry,newExpiry))changed++;}if(!left.equals(right))return reject("NON_EXPIRY_METADATA_CHANGED");}
  if(changed!=1||oldExpiry==null||newExpiry==null||!Timestamp.valueOf(newExpiry.replace('T',' ')).after(Timestamp.valueOf(oldExpiry.replace('T',' '))))return reject("EXPIRY_NOT_INCREASED");
  var renewed=renewals.stream().filter(r->r.completed()>before.end()&&r.completed()<after.end()&&r.affected()==1).toList();if(renewed.isEmpty())return reject("NO_SUCCESSFUL_INTERVENING_RENEWAL");
  return Map.of("callId",call,"thread",thread,"start",checkpoint,"end",end,"before",before,"after",after,"actualSuccessfulRenewals",renewed,"onlyChangedField","current durable_task.expires_at");
 }
}
