package ama.storage;

import javax.sql.DataSource;
import java.sql.*;import java.lang.reflect.*;import java.util.*;import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;

/** Delegates real writes, then fails one exact local-recovery transaction; no partial commit or fake row. */
final class Task11B3LocalFault {
 enum Point {RECEIPT_INSERT,TASK_TERMINAL,EXECUTION_CLOSE,OPERATION_CLOSE,SUBMISSION_SEAL,PUBLICATION,MEETING,AUDIT,RECEIPT_RESULT,OUTBOX_INSERT,FLUSH,PRE_COMMIT_FAILURE}
 volatile Point armed;volatile boolean hit,rollbackObserved;
 final List<String> executed=Collections.synchronizedList(new ArrayList<>());
 final List<Map<String,Object>> maintenanceQueries=new CopyOnWriteArrayList<>();
 String meeting,publication,task,execution,operation;DataSource guardedSource;
 void arm(Point point,String meeting,String publication,String task,String execution,String operation){assertNull(armed);this.meeting=meeting;this.publication=publication;this.task=task;this.execution=execution;this.operation=operation;hit=false;rollbackObserved=false;executed.clear();armed=point;}
 void disarm(){armed=null;}
 static Object invoke(Object target,Method method,Object[] args)throws Throwable {try{return method.invoke(target,args);}catch(InvocationTargetException e){throw e.getCause();}}
 void bound(Connection connection)throws SQLException {
  assertTrue(TransactionSynchronizationManager.isActualTransactionActive());assertFalse(connection.getAutoCommit());
  var holder=assertInstanceOf(ConnectionHolder.class,TransactionSynchronizationManager.getResource(guardedSource));assertSame(connection,holder.getConnection(),"Same DataSource transaction holder must own the guarded connection");
 }
 DataSource wrap(DataSource actual){assertNull(guardedSource);guardedSource=new AbstractDataSource(){
  public Connection getConnection()throws SQLException {return connection(actual.getConnection());}
  public Connection getConnection(String user,String password)throws SQLException {throw new SQLException("Alternate credentials forbidden");}
 };return guardedSource;}
 Connection connection(Connection actual){return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},new InvocationHandler(){
  boolean recoveryWrite;
  public Object invoke(Object proxy,Method method,Object[] args)throws Throwable {
   String name=method.getName();
   if(method.getDeclaringClass()==Object.class)return switch(name){case "equals"->proxy==args[0];case "hashCode"->System.identityHashCode(proxy);default->"B3LocalGuardConnection";};
   if(name.equals("unwrap")){assertEquals(Connection.class,args[0]);return proxy;}
   if(name.equals("isWrapperFor"))return args[0]==Connection.class;
   if(name.equals("prepareCall"))throw new AssertionError("Callable SQL forbidden");
   if(name.equals("commit")&&armed==Point.PRE_COMMIT_FAILURE&&recoveryWrite&&!hit){
    bound((Connection)proxy);assertTrue(executed.stream().anyMatch(sql->sql.startsWith("UPDATE task_outbox_sequence")),"Full real flush precedes precommit failure");hit=true;actual.rollback();rollbackObserved=true;
    throw new SQLException("B3 controlled failure before physical commit; actual rollback completed");
   }
   Object value=Task11B3LocalFault.invoke(actual,method,args);if(name.equals("rollback")&&armed!=null&&recoveryWrite)rollbackObserved=true;if(!(value instanceof Statement statement))return value;
   String prepared=args!=null&&args.length>0&&args[0] instanceof String text?text:null;var parameters=new HashMap<Integer,Object>();
   return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{statement instanceof PreparedStatement?PreparedStatement.class:Statement.class},(sp,sm,sa)->{
    String n=sm.getName();if(n.equals("getConnection"))return proxy;if(n.equals("unwrap"))throw new AssertionError("Statement unwrap forbidden");if(n.contains("Batch"))throw new AssertionError("Batch SQL forbidden");
    if(n.startsWith("set")&&sa!=null&&sa.length>=2&&sa[0] instanceof Integer index)parameters.put(index,sa[1]);if(n.equals("clearParameters"))parameters.clear();
    Object answer=Task11B3LocalFault.invoke(statement,sm,sa);
    if(n.startsWith("execute")){
     String sql=prepared!=null?prepared:sa!=null&&sa.length>0&&sa[0] instanceof String text?text:null;
     if(armed!=null&&sql!=null){
      boolean receipt=sql.startsWith("INSERT INTO idempotency_record")&&sql.contains("'PUBLICATION_LOCAL_RECOVERY'");
      if(receipt){assertTrue(parameters.containsValue(meeting));recoveryWrite=true;}
      if(recoveryWrite){assertTrue(executed.size()<1024);executed.add(sql);boolean matches=switch(armed){
       case RECEIPT_INSERT->receipt;
       case TASK_TERMINAL->sql.startsWith("UPDATE durable_task SET state=?,lease_owner")&&parameters.containsValue(task);
       case EXECUTION_CLOSE->sql.startsWith("UPDATE external_execution SET state='FAILED',finished_at")&&parameters.containsValue(execution);
       case OPERATION_CLOSE->sql.startsWith("UPDATE external_operation SET state='FAILED',last_error='LOCAL_NEVER_MARKED'")&&parameters.containsValue(operation);
       case SUBMISSION_SEAL->sql.startsWith("UPDATE publication_submission SET local_state='NEVER_MARKED'")&&parameters.containsValue(task);
       case PUBLICATION->sql.startsWith("UPDATE publication SET state='FAILED',revision=revision+1")&&parameters.containsValue(publication);
       case MEETING->sql.startsWith("UPDATE meeting SET stage='PUBLISH_FAILED'")&&parameters.containsValue(meeting);
       case AUDIT->sql.startsWith("INSERT INTO audit_event")&&parameters.containsValue("PUBLICATION_LOCAL_NEVER_MARKED");
       case RECEIPT_RESULT->sql.startsWith("UPDATE idempotency_record SET result_id=?,http_status=200")&&parameters.containsValue(task);
       case OUTBOX_INSERT->sql.startsWith("INSERT INTO task_outbox")&&parameters.containsValue("PUBLICATION_LOCAL_RECOVERED");
       case FLUSH->sql.startsWith("UPDATE task_outbox_sequence");case PRE_COMMIT_FAILURE->false;
      };
      if(matches&&!hit){bound((Connection)proxy);assertTrue(answer instanceof Number&&((Number)answer).intValue()==1,"Fault must follow a real affected row");hit=true;throw new SQLException("B3 controlled failure after delegated local write: "+armed.name());}
      }
     }
     if(sql!=null&&sql.startsWith("SELECT s.task_id,s.publication_id,CAST(s.created_at AS CHAR)")&&answer instanceof ResultSet result){
      String thread=Thread.currentThread().getName();long opened=System.nanoTime();var arguments=new TreeMap<>(parameters);int[] rows={0};boolean[] closed={false};
      return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ResultSet.class},(rp,rm,ra)->{
       Object returned=Task11B3LocalFault.invoke(result,rm,ra);if(rm.getName().equals("next")&&Boolean.TRUE.equals(returned))rows[0]++;
       if(rm.getName().equals("close")&&!closed[0]){closed[0]=true;assertTrue(maintenanceQueries.size()<16);maintenanceQueries.add(Map.of("thread",thread,"sql",sql,"parameters",arguments,"rows",rows[0],"openedNanos",opened,"closedNanos",System.nanoTime()));}
       return returned;
      });
     }
    }
    return answer;
   });
  }
 });}
}