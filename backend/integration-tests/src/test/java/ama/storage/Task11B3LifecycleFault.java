package ama.storage;
import javax.sql.DataSource;import java.sql.*;import java.lang.reflect.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicReference;import org.springframework.jdbc.datasource.*;import org.springframework.transaction.support.TransactionSynchronizationManager;import static org.junit.jupiter.api.Assertions.*;
/** Delegates real selected writes/physicalcommit; only test-owned transaction failure/observation. */
final class Task11B3LifecycleFault {
 enum Marker {ROLLBACK_BEFORE_COMMIT,COMMIT_THEN_LOSE_ACK}
 volatile CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
 volatile boolean attemptHit,attemptRollbackReturned,markerRollbackReturned,markerCommitReturned;volatile String attemptTask,scope,markerTask,execution;volatile Marker marker;
 DataSource guarded;final List<Map<String,Object>> events=new CopyOnWriteArrayList<>();
 record Flight(String sql,long openedNanos,String thread){}
 final AtomicReference<Flight> recoveryFlight=new AtomicReference<>();
 void armAttempt(String task,String scope){assertNull(attemptTask);this.attemptTask=task;this.scope=scope;attemptHit=false;attemptRollbackReturned=false;}
 void disarmAttempt(){attemptTask=null;}
 void armMarker(Marker mode,String task,String execution){assertNull(marker);this.marker=mode;this.markerTask=task;this.execution=execution;markerRollbackReturned=false;markerCommitReturned=false;entered=new CountDownLatch(1);release=new CountDownLatch(1);}
 void disarmMarker(){marker=null;}
 static Object invoke(Object actual,Method method,Object[] args)throws Throwable {try{return method.invoke(actual,args);}catch(InvocationTargetException error){throw error.getCause();}}
 void bound(Connection connection)throws SQLException {assertTrue(TransactionSynchronizationManager.isActualTransactionActive());assertFalse(connection.getAutoCommit());var holder=assertInstanceOf(ConnectionHolder.class,TransactionSynchronizationManager.getResource(guarded));assertSame(connection,holder.getConnection());}
 void event(String phase){assertTrue(events.size()<256);events.add(Map.of("phase",phase,"monotonicNanos",System.nanoTime(),"thread",Thread.currentThread().getName()));}
 DataSource wrap(DataSource actual){assertNull(guarded);guarded=new AbstractDataSource(){public Connection getConnection()throws SQLException{return connection(actual.getConnection());}public Connection getConnection(String u,String p)throws SQLException{throw new SQLException("Alternate credentials forbidden");}};return guarded;}
 Connection connection(Connection actual){return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},new InvocationHandler(){
  boolean selectedMarker,recovery,attemptFailed,flushed;
  public Object invoke(Object proxy,Method method,Object[] args)throws Throwable {
   String name=method.getName();if(method.getDeclaringClass()==Object.class)return switch(name){case "equals"->proxy==args[0];case "hashCode"->System.identityHashCode(proxy);default->"B3LifecycleConnection";};
   if(name.equals("unwrap")){assertEquals(Connection.class,args[0]);return proxy;}if(name.equals("isWrapperFor"))return args[0]==Connection.class;if(name.equals("prepareCall"))throw new AssertionError("Callable SQL forbidden");
   if(name.equals("commit")&&selectedMarker&&marker!=null){
    bound((Connection)proxy);assertTrue(flushed,"Selected real marker must reach complete outbox flush");event("MARKER_BEFORE_PHYSICAL_COMMIT");entered.countDown();Marker mode=marker;
    if(mode==Marker.ROLLBACK_BEFORE_COMMIT){assertTrue(release.await(5,TimeUnit.SECONDS),"Owned marker barrier deadline");actual.rollback();markerRollbackReturned=true;event("MARKER_PHYSICAL_ROLLBACK_RETURNED");throw new SQLException("B3 selected marker rolled back before physicalcommit");}
    Object answer=Task11B3LifecycleFault.invoke(actual,method,args);markerCommitReturned=true;event("MARKER_PHYSICAL_COMMIT_RETURNED_ACK_LOST");throw new SQLException("B3 marker commit acknowledgement deliberately unavailable");
   }
   Object value=Task11B3LifecycleFault.invoke(actual,method,args);if(name.equals("rollback")&&attemptFailed){attemptRollbackReturned=true;event("ATTEMPT_END_PHYSICAL_ROLLBACK_RETURNED");}if(!(value instanceof Statement statement))return value;
   String prepared=args!=null&&args.length>0&&args[0] instanceof String text?text:null;var parameters=new HashMap<Integer,Object>();
   return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{statement instanceof PreparedStatement?PreparedStatement.class:Statement.class},(sp,sm,sa)->{
    String n=sm.getName();if(n.equals("getConnection"))return proxy;if(n.equals("unwrap")||n.contains("Batch"))throw new AssertionError("Statement escape/batch forbidden");if(n.startsWith("set")&&sa!=null&&sa.length>=2&&sa[0] instanceof Integer index)parameters.put(index,sa[1]);if(n.equals("clearParameters"))parameters.clear();
    String sql=prepared!=null?prepared:sa!=null&&sa.length>0&&sa[0] instanceof String text?text:null;boolean executes=n.startsWith("execute");Flight flight=null;
    if(executes&&sql!=null){if(sql.startsWith("INSERT INTO external_operation"))throw new AssertionError("New operation roots forbidden in B3 lifecycle");if(sql.startsWith("UPDATE external_execution SET state='MARKED',task_attempt=")){if(marker==null||!Objects.equals(execution,parameters.get(3)))throw new AssertionError("Unreleased publication marker write");}if(sql.startsWith("INSERT INTO external_execution")&&sql.contains("'MARKED'"))throw new AssertionError("Unreleased publication marker write");}
    if(executes&&sql!=null&&sql.startsWith("SELECT ")&&sql.contains("FROM idempotency_record WHERE scope_hash=?")&&scope!=null&&scope.equals(parameters.get(1))){bound((Connection)proxy);recovery=true;}
    if(executes&&recovery&&sql!=null&&sql.startsWith("SELECT ")&&sql.contains(" FOR ")){bound((Connection)proxy);flight=new Flight(sql,System.nanoTime(),Thread.currentThread().getName());assertTrue(recoveryFlight.compareAndSet(null,flight));}
    try{
     Object answer=Task11B3LifecycleFault.invoke(statement,sm,sa);
     if(executes&&sql!=null){
      if(sql.startsWith("INSERT INTO idempotency_record")&&sql.contains("'PUBLICATION_LOCAL_RECOVERY'")&&scope!=null&&scope.equals(parameters.get(1)))recovery=true;
      if(marker!=null&&sql.startsWith("UPDATE external_execution SET state='MARKED',task_attempt=")&&Objects.equals(execution,parameters.get(3))){bound((Connection)proxy);assertEquals(1,((Number)answer).intValue());selectedMarker=true;event("SELECTED_MARKER_WRITE_RETURNED");}
      if(selectedMarker&&sql.startsWith("UPDATE task_outbox_sequence")){assertEquals(1,((Number)answer).intValue());flushed=true;event("SELECTED_MARKER_FLUSH_RETURNED");}
      if(recovery&&attemptTask!=null&&sql.startsWith("UPDATE task_attempt SET ended_at=UTC_TIMESTAMP(6),outcome=?")&&Objects.equals(attemptTask,parameters.get(3))&&!attemptHit){bound((Connection)proxy);assertEquals(1,((Number)answer).intValue());attemptHit=true;attemptFailed=true;event("SELECTED_REAL_ATTEMPT_END_WRITE_RETURNED");throw new SQLException("B3 selected actual attempt-ending write failure");}
     }
     return answer;
    }finally{if(flight!=null)assertTrue(recoveryFlight.compareAndSet(flight,null));}
   });
  }
 });}
}