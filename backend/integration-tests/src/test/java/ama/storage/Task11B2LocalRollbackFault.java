package ama.storage;
import javax.sql.DataSource;import java.sql.*;import java.lang.reflect.*;import java.util.*;import org.springframework.jdbc.datasource.ConnectionHolder;import org.springframework.transaction.support.TransactionSynchronizationManager;import static org.junit.jupiter.api.Assertions.*;
/** Real delegated writes, one exact local-recovery TX, then natural rollback; never detached graph. */
final class Task11B2LocalRollbackFault {
 enum Point { RECEIPT_INSERT,TASK_TERMINAL,SUBMISSION_SEAL,PUBLICATION,MEETING,AUDIT,RECEIPT_RESULT,OUTBOX_INSERT,FLUSH,PRE_COMMIT_FAILURE }
 volatile Point armed;String task,publication,meeting;volatile boolean hit,rollbackObserved;final List<String> executed=new ArrayList<>();
 void arm(Point point,String meeting,String publication,String task){assertNull(armed);this.meeting=meeting;this.publication=publication;this.task=task;hit=false;rollbackObserved=false;executed.clear();armed=point;}
 void disarm(){armed=null;}
 static final class Injected extends RuntimeException {Injected(){super("Exact test-only local rollback fault");}}
 static Object invoke(Object target,Method method,Object[] args)throws Throwable {try{return method.invoke(target,args);}catch(InvocationTargetException e){throw e.getCause();}}
 ConnectionHolder bound(Connection connection)throws Exception {assertTrue(TransactionSynchronizationManager.isActualTransactionActive());assertFalse(connection.getAutoCommit());for(Object value:TransactionSynchronizationManager.getResourceMap().values())if(value instanceof ConnectionHolder holder&&holder.getConnection().unwrap(Connection.class)==connection.unwrap(Connection.class))return holder;throw new AssertionError("Exact physical transaction holder required");}
 DataSource wrap(DataSource actual){return (DataSource)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{DataSource.class},(p,m,a)->{
  Object value=invoke(actual,m,a);if(!(value instanceof Connection connection))return value;boolean[] recoveryWrite={false};
  return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(cp,cm,ca)->{
   if(cm.getName().equals("rollback")&&armed!=null)rollbackObserved=true;
   if(cm.getName().equals("commit")&&armed==Point.PRE_COMMIT_FAILURE&&recoveryWrite[0]&&!hit){bound(connection);assertTrue(executed.stream().anyMatch(sql->sql.startsWith("UPDATE task_outbox_sequence")),"Full local flush must precede PRE_COMMIT_FAILURE");hit=true;connection.rollback();rollbackObserved=true;throw new SQLException("Controlled PRE_COMMIT_FAILURE; actual rollback, physical commit never called");}
   Object result=invoke(connection,cm,ca);if(!(result instanceof PreparedStatement statement)||ca==null||ca.length==0||!(ca[0] instanceof String sql))return result;var parameters=new HashMap<Integer,Object>();
   return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(sp,sm,sa)->{
    if(sm.getName().startsWith("set")&&sa!=null&&sa.length>=2&&sa[0] instanceof Integer index)parameters.put(index,sa[1]);
    Object answer=invoke(statement,sm,sa);if(armed!=null&&sm.getName().startsWith("execute")){
     boolean receipt=sql.startsWith("INSERT INTO idempotency_record")&&sql.contains("'PUBLICATION_LOCAL_RECOVERY'");if(receipt){assertTrue(parameters.containsValue(meeting));recoveryWrite[0]=true;}
     if(recoveryWrite[0])executed.add(sql);
     boolean match=switch(armed){case RECEIPT_INSERT->receipt;case TASK_TERMINAL->sql.startsWith("UPDATE durable_task SET state=?,lease_owner")&&parameters.containsValue(task);case SUBMISSION_SEAL->sql.startsWith("UPDATE publication_submission SET local_state='NEVER_MARKED'")&&parameters.containsValue(task);case PUBLICATION->sql.startsWith("UPDATE publication SET state='FAILED',revision=revision+1")&&parameters.containsValue(publication);case MEETING->sql.startsWith("UPDATE meeting SET stage='PUBLISH_FAILED'")&&parameters.containsValue(meeting);case AUDIT->sql.startsWith("INSERT INTO audit_event")&&parameters.containsValue("PUBLICATION_LOCAL_NEVER_MARKED");case RECEIPT_RESULT->sql.startsWith("UPDATE idempotency_record SET result_id=?,http_status=200")&&parameters.containsValue(task);case OUTBOX_INSERT->sql.startsWith("INSERT INTO task_outbox")&&parameters.containsValue("PUBLICATION_LOCAL_RECOVERED");case FLUSH->sql.startsWith("UPDATE task_outbox_sequence");case PRE_COMMIT_FAILURE->false;};
     if(recoveryWrite[0]&&!hit&&match){bound(connection);assertTrue(answer instanceof Number&&((Number)answer).intValue()==1,"Named fault must follow one actual affected row");hit=true;throw new Injected();}
    }return answer;
   });
  });
 });}
}
