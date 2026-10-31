package ama.worker;
import ama.contract.TaskContracts.*;
import ama.task.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RecordingArchiveHandlerTest {
 @Test void uninstalledArchivePolicyCannotReadBytesOrClaimSuccess() {
  var source=new org.springframework.jdbc.datasource.AbstractDataSource(){public Connection getConnection(){throw new AssertionError("Uninstalled handler must not access the database");}public Connection getConnection(String user,String password){return getConnection();}};
  var queue=new JdbcTaskQueue(source,new JdbcSubjectFences(source,Set.of("MEETING")),new JdbcOutbox(source,Map.of()),Map.of());
  var spec=new TaskSpec("RECORDING_ARCHIVE_V1","RECORDING_ARCHIVE",new SubjectRef("MEETING",UUID.randomUUID().toString()),"a".repeat(64),new TypedRef("RECORDING_ARCHIVE_INPUT",UUID.randomUUID().toString()),"b".repeat(64),0,0,0,3);
  var lease=new TaskLease(UUID.randomUUID().toString(),1,"archive-test",Instant.now().plusSeconds(20),spec);
  var live=new PreparationCancellation(){public boolean cancelled(){return false;}public AutoCloseable register(Runnable abort){return ()->{};}};
  assertThrows(IllegalStateException.class,()->new RecordingArchiveHandler(queue).prepare(lease,live));
 }
}
