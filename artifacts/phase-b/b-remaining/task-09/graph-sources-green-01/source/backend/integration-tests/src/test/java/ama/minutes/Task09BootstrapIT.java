package ama.minutes;
import ama.db.SchemaMigrator;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class Task09BootstrapIT {
 @Test void bootstrapOnlyAcknowledgedEmptySchemaThenNormalRerunZero(){
  String url=System.getenv("AMA_TEST_DATABASE_URL");assertEquals("jdbc:mysql://127.0.0.1:44727/ama_t09_2d0e28ea7755_behavior",url);
  var source=new DriverManagerDataSource(url,System.getenv("AMA_TEST_DATABASE_USERNAME"),System.getenv("AMA_TEST_DATABASE_PASSWORD"));var j=new JdbcTemplate(source);
  assertEquals("ama_t09_2d0e28ea7755_behavior",j.queryForObject("SELECT DATABASE()",String.class));
  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()",Integer.class));
  var migrator=new SchemaMigrator(source);assertEquals(15,migrator.migrate());assertEquals(0,migrator.migrate());
  assertEquals(java.util.List.of("001","002","005","006","007","008","009","010","011","011.1","012","013","013.1","013.2","013.3"),j.queryForList("SELECT version FROM flyway_schema_history ORDER BY installed_rank",String.class));
  assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=0",Integer.class));
  System.out.println("TASK09 COMPLETE INITIAL HISTORY "+j.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank"));
 }
}
