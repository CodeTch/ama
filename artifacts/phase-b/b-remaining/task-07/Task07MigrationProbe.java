import java.sql.*;
import java.nio.file.*;
import java.util.*;
import ama.db.SchemaMigrator;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
public class Task07MigrationProbe {
 static List<Map<String,String>> query(Connection c,String sql)throws Exception{try(var st=c.createStatement();var r=st.executeQuery(sql)){var rows=new ArrayList<Map<String,String>>();while(r.next()){var row=new LinkedHashMap<String,String>();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getString(i));rows.add(row);}return rows;}}
 public static void main(String[] args)throws Exception{
 String url=System.getenv("AMA_TEST_DATABASE_URL");if(!"jdbc:mysql://127.0.0.1:44727/ama_t07_0cea85a0e472_behavior".equals(url))throw new IllegalArgumentException("Exact Task7 fixture required");
 var ds=new DriverManagerDataSource(url,System.getenv("AMA_TEST_DATABASE_USERNAME"),System.getenv("AMA_TEST_DATABASE_PASSWORD"));var props=new Properties();props.setProperty("connectionTimeZone","UTC");props.setProperty("forceConnectionTimeZoneToSession","true");props.setProperty("characterEncoding","UTF-8");props.setProperty("connectionCollation","utf8mb4_0900_ai_ci");props.setProperty("connectTimeout","3000");props.setProperty("socketTimeout","15000");props.setProperty("sessionVariables","lock_wait_timeout=5,innodb_lock_wait_timeout=5");ds.setConnectionProperties(props);
 try(var c=ds.getConnection()){if(!query(c,"SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()").isEmpty())throw new AssertionError("Fresh schema required");}
 int first=new SchemaMigrator(ds).migrate();if(first!=15)throw new AssertionError("Expected15");List<Map<String,String>> before;try(var c=ds.getConnection()){before=query(c,"SELECT * FROM flyway_schema_history ORDER BY installed_rank");}
 int rerun=new SchemaMigrator(ds).migrate();if(rerun!=0)throw new AssertionError("Expected0");try(var c=ds.getConnection()){var after=query(c,"SELECT * FROM flyway_schema_history ORDER BY installed_rank");if(!before.equals(after)||after.size()!=15)throw new AssertionError("History changed");new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(Path.of(args[0]).toFile(),Map.of("first",first,"rerun",rerun,"before",before,"after",after,"engine",query(c,"SELECT VERSION() AS version,@@server_uuid AS serverUuid,@@session.time_zone AS timeZone,@@character_set_connection AS charset,@@session.lock_wait_timeout AS metadataWait,@@session.innodb_lock_wait_timeout AS rowWait")));}
 }
}
