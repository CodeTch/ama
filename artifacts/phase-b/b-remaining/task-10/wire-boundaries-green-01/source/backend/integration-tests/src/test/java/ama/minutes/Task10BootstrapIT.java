package ama.minutes;
import java.nio.file.*;import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class Task10BootstrapIT {
 @Test void approvedEmptySchemaNormalFifteenThenZeroRetainsCompleteHistory()throws Exception{
  var source=Task10Source.open();var j=new org.springframework.jdbc.core.JdbcTemplate(source);j.setQueryTimeout(5);
  assertEquals("ama_t10_1e48b41fc5c8_behavior",j.queryForObject("SELECT DATABASE()",String.class));
  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()",Integer.class));
  var session=j.queryForMap("SELECT @@session.time_zone AS zone,@@character_set_connection AS charset,@@session.lock_wait_timeout AS metadata_wait,@@session.innodb_lock_wait_timeout AS row_wait");
  assertEquals("+00:00",session.get("zone"));assertEquals("utf8mb4",session.get("charset"));assertEquals(5,((Number)session.get("metadata_wait")).intValue());assertEquals(5,((Number)session.get("row_wait")).intValue());
  assertEquals(15,new ama.db.SchemaMigrator(source).migrate());var baseline=Task09FinalMigrationIT.history(j);assertEquals(15,baseline.size());assertEquals("013.3",baseline.getLast().get("version"));assertEquals("-385117638",baseline.getLast().get("checksum"));
  assertEquals(0,new ama.db.SchemaMigrator(source).migrate());var after=Task09FinalMigrationIT.history(j);assertEquals(baseline,after);
  Files.writeString(Path.of(System.getenv("AMA_TASK10_EVIDENCE_WINDOW"),"full-fifteen-history.json"),new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("initialTables",0,"initialMigrations",15,"rerunMigrations",0,"session",session,"baseline",baseline,"after",after)));
 }
}
