package ama.minutes;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;
public final class Task10Source {
 public static DataSource open(){String url=System.getenv("AMA_TEST_DATABASE_URL");assertEquals("jdbc:mysql://127.0.0.1:46151/ama_t10_1e48b41fc5c8_behavior",url);return ControlledMinutesDataSource.create(url,System.getenv("AMA_TEST_DATABASE_USERNAME"),System.getenv("AMA_TEST_DATABASE_PASSWORD"));}
}
