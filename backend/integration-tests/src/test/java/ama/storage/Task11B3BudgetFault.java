package ama.storage;
import java.sql.*;import java.lang.reflect.*;import java.util.*;import javax.sql.DataSource;import org.springframework.jdbc.datasource.*;import org.springframework.transaction.support.TransactionSynchronizationManager;import static org.junit.jupiter.api.Assertions.*;
/** Exact diagnostic FINAL transaction only. All real writes are rollback-only; never commits a successor. */
final class Task11B3BudgetFault {
 static final String OPERATION_CAS="UPDATE external_operation SET request_hash=?,state='RETRY_QUEUED',current_execution_no=?,reconcile_epoch=reconcile_epoch+1,reconcile_owner=NULL,reconcile_expires_at=NULL,last_error=NULL WHERE id=? AND operation_kind='KNOWLEDGE_PUBLISH' AND business_key=? AND subject_kind='MEETING' AND subject_id=? AND request_hash=? AND current_execution_no=? AND state='FAILED' AND reconcile_epoch<9223372036854775806";
 static final class FinalGateReached extends RuntimeException {}
 boolean rollbackOnlyBeforeFirstWrite,physicalRollbackReturned,finalReached;int delegatedWrites;final List<String> sqlWrites=new ArrayList<>(),sqlTrace=new ArrayList<>();String proposedTask;
 private String scope,actor,meeting,publication,operation;private long thread;private DataSource guarded;private Object selectedHolder;
 void arm(String scope,String actor,String meeting,String publication,String operation){assertNull(this.scope);this.scope=Objects.requireNonNull(scope);this.actor=Objects.requireNonNull(actor);this.meeting=Objects.requireNonNull(meeting);this.publication=Objects.requireNonNull(publication);this.operation=Objects.requireNonNull(operation);thread=Thread.currentThread().threadId();rollbackOnlyBeforeFirstWrite=physicalRollbackReturned=finalReached=false;delegatedWrites=0;proposedTask=null;selectedHolder=null;sqlWrites.clear();sqlTrace.clear();}
 void disarm(){assertFalse(TransactionSynchronizationManager.isActualTransactionActive());scope=null;}
 static Object invoke(Object target,Method method,Object[] args)throws Throwable {try{return method.invoke(target,args);}catch(InvocationTargetException error){throw error.getCause();}}
 ConnectionHolder bound(Connection connection)throws SQLException {assertTrue(TransactionSynchronizationManager.isActualTransactionActive());assertFalse(connection.getAutoCommit());var holder=assertInstanceOf(ConnectionHolder.class,TransactionSynchronizationManager.getResource(guarded));assertSame(connection,holder.getConnection());return holder;}
 DataSource wrap(DataSource source){assertNull(guarded);guarded=new AbstractDataSource(){public Connection getConnection()throws SQLException{return connection(source.getConnection());}public Connection getConnection(String u,String p)throws SQLException{throw new SQLException("Alternate diagnostic credentials forbidden");}};return guarded;}
 Connection connection(Connection actual){return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},new InvocationHandler(){
  boolean selected;
  public Object invoke(Object cp,Method cm,Object[] ca)throws Throwable {
   String name=cm.getName();if(cm.getDeclaringClass()==Object.class)return switch(name){case "equals"->cp==ca[0];case "hashCode"->System.identityHashCode(cp);default->"ExactBudgetDiagnosticConnection";};
   if(name.equals("unwrap")){assertEquals(Connection.class,ca[0]);return cp;}if(name.equals("isWrapperFor"))return ca[0]==Connection.class;if(name.equals("prepareCall"))throw new AssertionError("Callable diagnostic SQL forbidden");
   if(name.equals("commit")&&selected){actual.rollback();physicalRollbackReturned=true;throw new SQLException("Selected diagnostic transaction must never commit");}
   if(name.equals("setAutoCommit")&&selected&&Boolean.TRUE.equals(ca[0])&&!physicalRollbackReturned){actual.rollback();physicalRollbackReturned=true;}
   Object value=Task11B3BudgetFault.invoke(actual,cm,ca);if(name.equals("rollback")&&selected)physicalRollbackReturned=true;if(!(value instanceof Statement statement))return value;
   String prepared=ca!=null&&ca.length>0&&ca[0] instanceof String text?text:null;var parameters=new HashMap<Integer,Object>();
   return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{statement instanceof PreparedStatement?PreparedStatement.class:Statement.class},(sp,sm,sa)->{
    String n=sm.getName();if(n.equals("getConnection"))return cp;if(n.equals("unwrap")||n.contains("Batch"))throw new AssertionError("Diagnostic statement escape/batch forbidden");if(n.startsWith("set")&&sa!=null&&sa.length>=2&&sa[0] instanceof Integer index)parameters.put(index,sa[1]);if(n.equals("clearParameters"))parameters.clear();
    String sql=prepared!=null?prepared:sa!=null&&sa.length>0&&sa[0] instanceof String text?text:null;
    if(n.startsWith("execute")&&sql!=null){
     if(sql.indexOf(';')>=0)throw new AssertionError("Diagnostic multi-statement SQL forbidden");if(sqlTrace.size()==32)sqlTrace.removeFirst();sqlTrace.add(sql);boolean write=sql.startsWith("INSERT ")||sql.startsWith("UPDATE ")||sql.startsWith("DELETE ")||sql.startsWith("REPLACE ");
     if(write){
      if(scope==null||Thread.currentThread().threadId()!=thread||prepared==null)throw new AssertionError("Unselected diagnostic business write");
      var holder=bound((Connection)cp);
      if(!selected){if(!sql.startsWith("INSERT INTO idempotency_record(")||!scope.equals(parameters.get(1))||!actor.equals(parameters.get(2))||!meeting.equals(parameters.get(3))||!"PUBLICATION_RESUBMIT".equals(parameters.get(4)))throw new AssertionError("Unselected diagnostic business write");selected=true;selectedHolder=holder;holder.setRollbackOnly();assertTrue(holder.isRollbackOnly());rollbackOnlyBeforeFirstWrite=true;}
      assertSame(selectedHolder,holder);assertTrue(holder.isRollbackOnly());assertTrue(delegatedWrites<8);
      if(sql.startsWith("INSERT INTO external_operation")||sql.startsWith("UPDATE external_execution SET state='MARKED'"))throw new AssertionError("Diagnostic marker/new operation forbidden");
      if(sql.startsWith("INSERT INTO publication_submission(")){assertEquals(publication,parameters.get(2),"Diagnostic publication binding");assertNotNull(proposedTask,"Actual queue task INSERT must precede final submission");assertEquals(proposedTask,parameters.get(1),"Diagnostic task binding");finalReached=true;throw new FinalGateReached();}
      if(sql.startsWith("INSERT INTO idempotency_record(")){assertEquals(scope,parameters.get(1));assertEquals(actor,parameters.get(2));assertEquals(meeting,parameters.get(3));assertEquals("PUBLICATION_RESUBMIT",parameters.get(4));}
      else if(sql.startsWith("INSERT INTO durable_task(")){assertNull(proposedTask);proposedTask=(String)parameters.get(1);UUID.fromString(proposedTask);assertEquals("KNOWLEDGE_PUBLISH_V1",parameters.get(2));assertEquals("KNOWLEDGE_PUBLISH",parameters.get(3));assertEquals("MEETING",parameters.get(4));assertEquals(meeting,parameters.get(5));assertEquals("PUBLICATION",parameters.get(7));assertEquals(publication,parameters.get(8));}
      else if(sql.startsWith("UPDATE external_operation SET request_hash=")){assertEquals(OPERATION_CAS,sql,"Only the exact production replacement CAS is released");assertEquals(7,parameters.size());assertEquals(operation,parameters.get(3));assertEquals(meeting,parameters.get(5));assertEquals(7L,((Number)parameters.get(2)).longValue());assertEquals(6L,((Number)parameters.get(7)).longValue());assertTrue(((String)parameters.get(1)).matches("[0-9a-f]{64}"));assertTrue(((String)parameters.get(6)).matches("[0-9a-f]{64}"));assertTrue(parameters.get(4) instanceof String key&&!key.isBlank()&&key.length()<=255);}
      else if(sql.startsWith("INSERT INTO external_execution(")){assertEquals(operation,parameters.get(2));assertEquals(proposedTask,parameters.get(4));assertTrue(sql.contains("'PREPARED'"));}
      else throw new AssertionError("Unreleased diagnostic write SQL");
      Object answer=Task11B3BudgetFault.invoke(statement,sm,sa);assertEquals(1,((Number)answer).intValue());delegatedWrites++;sqlWrites.add(sql);return answer;
     }
     if(!(sql.startsWith("SELECT ")||sql.equals("SET NAMES utf8mb4")||sql.equals("SET time_zone = '+00:00'")))throw new AssertionError("Unreleased diagnostic SQL");
    }
    return Task11B3BudgetFault.invoke(statement,sm,sa);
   });
  }
 });}
 Map<String,Object> evidence(){return Map.of("rollbackOnlyBeforeFirstWrite",rollbackOnlyBeforeFirstWrite,"physicalRollbackReturned",physicalRollbackReturned,"finalReachedBeforeDelegation",finalReached,"delegatedWrites",delegatedWrites,"sqlWrites",List.copyOf(sqlWrites),"lastSqlTrace",List.copyOf(sqlTrace),"proposedTask",proposedTask==null?"none":proposedTask);}
}