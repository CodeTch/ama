package ama.task;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
final class Task08InheritedResources {
 static DataSource source(){var s=Task08Fixture.dataSource("behavior");requireForward(s);return s;}
 static void requireForward(DataSource source){var j=new JdbcTemplate(source);assertEquals(java.util.List.of("001","002","005","006","007","008","009","010","011","011.1","012","013","013.1","013.2","013.3"),j.queryForList("SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank",String.class));assertEquals(15,j.queryForObject("SELECT COUNT(*) FROM flyway_schema_history",Integer.class));assertEquals(-385117638,j.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='013.3'",Integer.class));}
}
