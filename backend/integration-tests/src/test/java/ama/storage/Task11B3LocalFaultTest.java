package ama.storage;
import ama.db.TransactionRunner;import javax.sql.DataSource;import java.sql.*;import java.lang.reflect.*;import java.util.*;import java.util.stream.*;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.jdbc.datasource.AbstractDataSource;import org.springframework.dao.DataAccessException;import org.junit.jupiter.api.*;import static org.junit.jupiter.api.Assertions.*;
/** Pure real-transaction/fake-physical-JDBC proof that seams fail after delegated writes and rollback. */
class Task11B3LocalFaultTest {
 static final String MEETING="synthetic-meeting",PUBLICATION="synthetic-publication",TASK="synthetic-task",EXECUTION="synthetic-execution",OPERATION="synthetic-operation";
 static final String RECEIPT="INSERT INTO idempotency_record(operation,resource_id) VALUES ('PUBLICATION_LOCAL_RECOVERY',?)";
 record Write(String sql,Object parameter){Object[] arguments(){return sql.startsWith("UPDATE durable_task")?new Object[]{"FAILED",parameter}:new Object[]{parameter};}}
 static Write write(Task11B3LocalFault.Point p){return switch(p){
  case RECEIPT_INSERT->new Write(RECEIPT,MEETING);
  case TASK_TERMINAL->new Write("UPDATE durable_task SET state=?,lease_owner=NULL WHERE id=?",TASK);
  case EXECUTION_CLOSE->new Write("UPDATE external_execution SET state='FAILED',finished_at=UTC_TIMESTAMP(6) WHERE id=?",EXECUTION);
  case OPERATION_CLOSE->new Write("UPDATE external_operation SET state='FAILED',last_error='LOCAL_NEVER_MARKED' WHERE id=?",OPERATION);
  case SUBMISSION_SEAL->new Write("UPDATE publication_submission SET local_state='NEVER_MARKED' WHERE task_id=?",TASK);
  case PUBLICATION->new Write("UPDATE publication SET state='FAILED',revision=revision+1 WHERE id=?",PUBLICATION);
  case MEETING->new Write("UPDATE meeting SET stage='PUBLISH_FAILED' WHERE id=?",MEETING);
  case AUDIT->new Write("INSERT INTO audit_event(event_type) VALUES (?)","PUBLICATION_LOCAL_NEVER_MARKED");
  case RECEIPT_RESULT->new Write("UPDATE idempotency_record SET result_id=?,http_status=200 WHERE resource_id='synthetic-meeting'",TASK);
  case OUTBOX_INSERT->new Write("INSERT INTO task_outbox(event_type) VALUES (?)","PUBLICATION_LOCAL_RECOVERED");
  case FLUSH,PRE_COMMIT_FAILURE->new Write("UPDATE task_outbox_sequence SET next_sequence=2 WHERE id=?",MEETING);
 };}
 @TestFactory Stream<DynamicTest> everyNamedSeamDelegatesThenRollsBack(){return Arrays.stream(Task11B3LocalFault.Point.values()).map(point->DynamicTest.dynamicTest(point.name(),()->{
  var fake=new Fake();var fault=new Task11B3LocalFault();DataSource source=fault.wrap(fake);var jdbc=new JdbcTemplate(source);var tx=new TransactionRunner(source);
  fault.arm(point,MEETING,PUBLICATION,TASK,EXECUTION,OPERATION);Write target=write(point);
  assertThrows(RuntimeException.class,()->tx.required(()->{if(point!=Task11B3LocalFault.Point.RECEIPT_INSERT)jdbc.update(RECEIPT,MEETING);jdbc.update(target.sql(),target.arguments());return null;}));
  assertTrue(fault.hit);assertTrue(fault.rollbackObserved);assertTrue(fake.sql.contains(target.sql()));assertEquals(0,fake.commits);assertEquals(1,fake.rollbacks);assertEquals(1,fake.closes);
 }));}
 @Test void disarmedWrapperPreservesDataSourceAndBoundConnectionIdentity(){
  var fake=new Fake();var fault=new Task11B3LocalFault();DataSource source=fault.wrap(fake);
  assertEquals(source,source);assertEquals(source.hashCode(),source.hashCode());
  assertEquals("done",new TransactionRunner(source).required(()->{var c=org.springframework.jdbc.datasource.DataSourceUtils.getConnection(source);assertEquals(c,c);assertSame(c,c.unwrap(Connection.class));try(var s=c.prepareStatement("SELECT 1")){assertSame(c,s.getConnection());}org.springframework.jdbc.datasource.DataSourceUtils.releaseConnection(c,source);return "done";}));
  assertEquals(1,fake.commits);assertEquals(0,fake.rollbacks);assertEquals(1,fake.closes);
 }
 @Test void failedRollbackCannotBeReportedAsReturnedRollback(){
  var fake=new Fake();fake.rollbackThrows=true;var fault=new Task11B3LocalFault();DataSource source=fault.wrap(fake);var jdbc=new JdbcTemplate(source);fault.arm(Task11B3LocalFault.Point.EXECUTION_CLOSE,MEETING,PUBLICATION,TASK,EXECUTION,OPERATION);
  assertThrows(RuntimeException.class,()->new TransactionRunner(source).required(()->{jdbc.update(RECEIPT,MEETING);jdbc.update(write(Task11B3LocalFault.Point.EXECUTION_CLOSE).sql(),EXECUTION);return null;}));
  assertTrue(fault.hit);assertFalse(fault.rollbackObserved,"A failed physical rollback is not a returned rollback");assertEquals(1,fake.rollbacks);assertEquals(0,fake.commits);assertEquals(1,fake.closes);
 }
 static final class Fake extends AbstractDataSource {
  final List<String> sql=new ArrayList<>();int commits,rollbacks,closes;boolean rollbackThrows;
  public Connection getConnection(){boolean[] auto={true};return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,a)->switch(m.getName()){
   case "equals"->p==a[0];case "hashCode"->System.identityHashCode(p);case "toString"->"InertB3Connection";
   case "unwrap"->p;case "isWrapperFor"->a[0]==Connection.class;case "getAutoCommit"->auto[0];case "setAutoCommit"->{auto[0]=(Boolean)a[0];yield null;}
   case "isReadOnly","isClosed"->false;case "clearWarnings"->null;case "commit"->{commits++;yield null;}case "rollback"->{rollbacks++;if(rollbackThrows)throw new SQLException("Inert rollback failed");yield null;}case "close"->{closes++;yield null;}
   case "createStatement"->statement((Connection)p,null);case "prepareStatement"->statement((Connection)p,(String)a[0]);default->throw new AssertionError("Unexpected inert connection method "+m.getName());});}
  public Connection getConnection(String u,String p){throw new AssertionError("No credentials");}
  Object statement(Connection connection,String prepared){return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{prepared==null?Statement.class:PreparedStatement.class},(p,m,a)->{
   if(m.getName().startsWith("set"))return null;
   return switch(m.getName()) {case "getConnection"->connection;case "close","getWarnings"->null;case "execute"->{sql.add((String)a[0]);yield false;}case "executeUpdate"->{sql.add(prepared);yield 1;}default->throw new AssertionError("Unexpected inert statement method "+m.getName());};});}
 }
}