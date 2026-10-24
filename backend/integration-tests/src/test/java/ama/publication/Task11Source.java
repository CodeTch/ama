package ama.publication;
import javax.sql.DataSource;import static org.junit.jupiter.api.Assertions.*;
public final class Task11Source {
 public static String schema(){String fixture=System.getenv("AMA_TASK11_FIXTURE");if("task11-fix01-isolated".equals(fixture))return "ama_t11fix1_49609b94161e_behavior";assertTrue(fixture==null||"task11-primary".equals(fixture));return "ama_t11_9714255c9e2c_behavior";}
 public static DataSource open(){String url=System.getenv("AMA_TEST_DATABASE_URL");assertEquals("jdbc:mysql://127.0.0.1:46151/"+schema(),url);return ama.minutes.ControlledMinutesDataSource.create(url,System.getenv("AMA_TEST_DATABASE_USERNAME"),System.getenv("AMA_TEST_DATABASE_PASSWORD"));}
}
