package ama.recording;
import java.sql.DriverManager;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Closing the embedded application must restore the caller's JVM-wide JDBC login budget. */
class RecordingConnectionIsolationIT {
 @Test void closingHttpFixtureRestoresTheIndependentJdbcLoginBudget()throws Exception {
  int original=DriverManager.getLoginTimeout();var fixture=new RecordingAccessIT();
  try{
   DriverManager.setLoginTimeout(7);fixture.startWith();
   try(var connection=fixture.app.getBean(DataSource.class).getConnection()){assertTrue(connection.isValid(2));}
   fixture.stop();assertEquals(7,DriverManager.getLoginTimeout());
  }finally{fixture.stop();DriverManager.setLoginTimeout(original);}
 }
}
