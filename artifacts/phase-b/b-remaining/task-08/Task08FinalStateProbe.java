import java.nio.file.*;
import java.util.*;
public class Task08FinalStateProbe {
 public static void main(String[] args)throws Exception{
  String target=args[0],url=System.getenv("AMA_TEST_DATABASE_URL");String expected=target.equals("shared")?"jdbc:mysql://127.0.0.1:46653/ama_b01_fixture":"jdbc:mysql://127.0.0.1:44727/ama_t08_848e93aace8b_"+target;
  if(!expected.equals(url)||!Set.of("shared","behavior","fairheld","fairunregistered","overflow","historical010").contains(target))throw new IllegalArgumentException("Exact target only");
  var result=new LinkedHashMap<String,Object>();result.put("target",target);result.put("scope","Read-only retained control fixture state; no company pipeline/HA/capacity claim");
  try(var c=Task08MigrationProbe.source().getConnection()){
   c.setReadOnly(true);c.setAutoCommit(false);result.put("history",Task08MigrationProbe.query(c,"SELECT * FROM flyway_schema_history ORDER BY installed_rank"));result.put("engine",Task08MigrationProbe.query(c,"SELECT VERSION() AS version,@@session.lock_wait_timeout AS metadataWaitSeconds,@@session.sql_mode AS sqlMode"));
   for(String table:List.of("external_operation","external_execution","durable_task","task_attempt","outbox_consumer","model_reservation")){String field=table.equals("task_attempt")?"outcome":(table.equals("outbox_consumer")||table.equals("model_reservation"))?null:"state";if(field==null)result.put(table,Task08MigrationProbe.query(c,"SELECT COUNT(*) AS count FROM "+table));else result.put(table,Task08MigrationProbe.query(c,"SELECT "+field+",COUNT(*) AS count FROM "+table+" GROUP BY "+field+" ORDER BY "+field));}
   if(!target.equals("historical010"))result.put("ordinal",Task08MigrationProbe.query(c,"SELECT COUNT(*) AS rowsTotal,COUNT(DISTINCT reconcile_scan_seq) AS distinctOrdinals,MIN(reconcile_scan_seq) AS minimum,MAX(reconcile_scan_seq) AS maximum,SUM(reconcile_scan_seq<=0 OR reconcile_scan_seq IS NULL) AS invalid FROM external_operation"));
   result.put("controlCounts",Task08MigrationProbe.query(c,"SELECT (SELECT COUNT(*) FROM task_outbox) AS outboxRows,(SELECT COUNT(*) FROM meeting) AS meetings,(SELECT COUNT(*) FROM enterprise_identity) AS identities"));c.rollback();
  }
  Task08MigrationProbe.JSON.writerWithDefaultPrettyPrinter().writeValue(Path.of(args[1]).toFile(),result);
 }
}
