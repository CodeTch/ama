package ama.task;
import javax.sql.DataSource;
import java.util.Properties;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
final class Task07DataSource {
 static DataSource create(){
 String url=System.getenv("AMA_TEST_DATABASE_URL");if(!"jdbc:mysql://127.0.0.1:44727/ama_t07_0cea85a0e472_behavior".equals(url))throw new IllegalArgumentException("Exact Task7 fixture required");
 var ds=new DriverManagerDataSource(url,System.getenv("AMA_TEST_DATABASE_USERNAME"),System.getenv("AMA_TEST_DATABASE_PASSWORD"));var p=new Properties();p.setProperty("connectionTimeZone","UTC");p.setProperty("forceConnectionTimeZoneToSession","true");p.setProperty("characterEncoding","UTF-8");p.setProperty("connectionCollation","utf8mb4_0900_ai_ci");p.setProperty("connectTimeout","3000");p.setProperty("socketTimeout","15000");p.setProperty("sessionVariables","lock_wait_timeout=5,innodb_lock_wait_timeout=5");ds.setConnectionProperties(p);return ds;
 }
}
