import java.nio.file.*;
import java.util.*;
import org.flywaydb.core.Flyway;
public class Task08Historical010Probe {
 public static void main(String[] args)throws Exception{
  if(!"jdbc:mysql://127.0.0.1:44727/ama_t08_848e93aace8b_historical010".equals(System.getenv("AMA_TEST_DATABASE_URL")))throw new IllegalArgumentException("Exact historical010 only");
  var s=Task08MigrationProbe.source();var result=new LinkedHashMap<String,Object>();try(var c=s.getConnection()){var before=Task08MigrationProbe.query(c,"SELECT COUNT(*) AS count FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()");Task08MigrationProbe.check("0".equals(before.getFirst().get("count")),"Fresh0 only");result.put("before",before);}
  var flyway=Flyway.configure().dataSource(s).locations("classpath:db/migration").initSql("SET NAMES utf8mb4; SET time_zone = '+00:00'").target("010").cleanDisabled(true).load();long start=System.nanoTime();int first=flyway.migrate().migrationsExecuted;Task08MigrationProbe.check(first==8,"Initial8");result.put("initialMigrations",first);result.put("initialMillis",(System.nanoTime()-start)/1000000);
  try(var c=s.getConnection()){result.put("historyBeforeRerun",Task08MigrationProbe.query(c,"SELECT * FROM flyway_schema_history ORDER BY installed_rank"));result.put("engine",Task08MigrationProbe.query(c,"SELECT VERSION() AS version,@@session.lock_wait_timeout AS metadataWaitSeconds,@@session.sql_mode AS sqlMode"));}
  int rerun=flyway.migrate().migrationsExecuted;Task08MigrationProbe.check(rerun==0,"Rerun0");result.put("rerunMigrations",rerun);
  try(var c=s.getConnection()){var history=Task08MigrationProbe.query(c,"SELECT * FROM flyway_schema_history ORDER BY installed_rank");Task08MigrationProbe.check(result.get("historyBeforeRerun").equals(history),"Full8 history preserved");result.put("historyAfterRerun",history);result.put("operationColumns",Task08MigrationProbe.query(c,"SELECT COLUMN_NAME,ORDINAL_POSITION,COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='external_operation' ORDER BY ORDINAL_POSITION"));}
  Task08MigrationProbe.JSON.writerWithDefaultPrettyPrinter().writeValue(Path.of(args[0]).toFile(),result);System.out.println("TASK08 historical010 normal new8/rerun0 full8 history preserved; forward13.3 excluded");
 }
}
