package ama.task;
import javax.sql.DataSource;
import java.util.Properties;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
final class Task08Historical010Source {
 static DataSource dataSource(){String url=System.getenv("AMA_TASK08_HISTORICAL010_DATABASE_URL");if(!"jdbc:mysql://127.0.0.1:44727/ama_t08_848e93aace8b_historical010".equals(url))throw new IllegalArgumentException("Exact historical010 ACK target required");var source=new DriverManagerDataSource(url,System.getenv("AMA_TEST_DATABASE_USERNAME"),System.getenv("AMA_TEST_DATABASE_PASSWORD"));var props=new Properties();props.setProperty("sessionVariables","lock_wait_timeout=5,innodb_lock_wait_timeout=5");props.setProperty("connectTimeout","3000");props.setProperty("socketTimeout","15000");source.setConnectionProperties(props);return source;}
}
