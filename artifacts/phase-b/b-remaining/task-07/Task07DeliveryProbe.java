import java.sql.*;
import java.nio.file.*;
import java.util.*;
import ama.db.SchemaMigrator;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
public class Task07DeliveryProbe {
 public static void main(String[] args)throws Exception{
  String url=System.getenv("AMA_TEST_DATABASE_URL");if(!"jdbc:mysql://127.0.0.1:44727/ama_t07_0cea85a0e472_behavior".equals(url))throw new IllegalArgumentException("Exact owned fixture required");
  var ds=new DriverManagerDataSource(url,System.getenv("AMA_TEST_DATABASE_USERNAME"),System.getenv("AMA_TEST_DATABASE_PASSWORD"));var props=new Properties();props.setProperty("connectionTimeZone","UTC");props.setProperty("forceConnectionTimeZoneToSession","true");props.setProperty("characterEncoding","UTF-8");props.setProperty("connectionCollation","utf8mb4_0900_ai_ci");props.setProperty("connectTimeout","3000");props.setProperty("socketTimeout","15000");props.setProperty("sessionVariables","lock_wait_timeout=5,innodb_lock_wait_timeout=5");ds.setConnectionProperties(props);
  int rerun=new SchemaMigrator(ds).migrate();if(rerun!=0)throw new AssertionError("Post-test rerun must be zero");
  try(var c=ds.getConnection()){var result=new LinkedHashMap<String,Object>();result.put("normalRerun",rerun);result.put("history",Task07MigrationProbe.query(c,"SELECT * FROM flyway_schema_history ORDER BY installed_rank"));result.put("engine",Task07MigrationProbe.query(c,"SELECT VERSION() AS version,@@server_uuid AS serverUuid,@@session.time_zone AS timeZone,@@character_set_connection AS charset,@@session.lock_wait_timeout AS metadataWait,@@session.innodb_lock_wait_timeout AS rowWait"));
   for(String table:List.of("durable_task","external_operation","external_execution"))result.put(table,Task07MigrationProbe.query(c,"SELECT state,COUNT(*) AS rows_count FROM "+table+" GROUP BY state ORDER BY state"));
   result.put("reservations",Task07MigrationProbe.query(c,"SELECT settled_at IS NOT NULL AS settled,COUNT(*) AS rows_count FROM model_reservation GROUP BY settled_at IS NOT NULL"));result.put("scope","Only Task7 owned schema. Deliberately retained failure/UNKNOWN/marker rows are not reset or claimed as globally quiescent.");new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(Path.of(args[0]).toFile(),result);
  }
 }
}
