package ama.minutes;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
public final class Task09Source {
 public static DataSource open(){
  String url=System.getenv("AMA_TEST_DATABASE_URL");assertEquals("jdbc:mysql://127.0.0.1:44727/ama_t09_2d0e28ea7755_behavior",url);
  var source=new DriverManagerDataSource(url,System.getenv("AMA_TEST_DATABASE_USERNAME"),System.getenv("AMA_TEST_DATABASE_PASSWORD")){
   @Override public java.sql.Connection getConnection()throws java.sql.SQLException{var c=super.getConnection();try(var st=c.createStatement()){st.execute("SET NAMES utf8mb4");st.execute("SET time_zone='+00:00'");st.execute("SET SESSION lock_wait_timeout=5");st.execute("SET SESSION innodb_lock_wait_timeout=5");}catch(java.sql.SQLException e){c.close();throw e;}return c;}
  };
  var j=new JdbcTemplate(source);assertEquals(15,j.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1",Integer.class));assertEquals(-385117638,j.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='013.3'",Integer.class));return source;
 }
}
