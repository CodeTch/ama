package ama.publication;
import javax.sql.DataSource;import static org.junit.jupiter.api.Assertions.*;
public final class Task11Source {
 public static DataSource open(){String url=System.getenv("AMA_TEST_DATABASE_URL");assertEquals("jdbc:mysql://127.0.0.1:46151/ama_t11_9714255c9e2c_behavior",url);return ama.minutes.ControlledMinutesDataSource.create(url,System.getenv("AMA_TEST_DATABASE_USERNAME"),System.getenv("AMA_TEST_DATABASE_PASSWORD"));}
}
