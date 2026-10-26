package ama.storage;

import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.*;
import static org.junit.jupiter.api.Assertions.*;

/** Test-only same-length wire fault. No committed business mutation: savepoint rollback
 * completes when the real JDBC ResultSet closes, before detached validation runs. */
final class Task11Fix02WireFault {
 record Cell(String table,String key,String id,String original){
  Cell{assertTrue(table.equals("publication_submission")&&key.equals("task_id")||table.equals("external_execution")&&key.equals("id"));}
 }
 long armedThread;String prefix,wire;List<Cell> cells=List.of();boolean injected,delivered,restored;Connection active;Savepoint savepoint;ConnectionHolder holder;
 void arm(String prefix,String wire,List<Cell> cells){
  assertNull(this.prefix);assertFalse(cells.isEmpty());
  for(var cell:cells){assertEquals(cell.original().getBytes(StandardCharsets.UTF_8).length,wire.getBytes(StandardCharsets.UTF_8).length);assertEquals(cell.original().length(),wire.length());assertEquals(cell.original().codePointCount(0,cell.original().length()),wire.codePointCount(0,wire.length()));assertNotEquals(cell.original(),wire);}
  this.prefix=prefix;this.wire=wire;armedThread=Thread.currentThread().threadId();this.cells=List.copyOf(cells);injected=delivered=restored=false;
 }
 void disarm(){assertTrue(!injected||restored,"Fault may not leave a temporary SQL mutation");prefix=null;}
 private void inject(Connection connection)throws Exception{
  assertTrue(TransactionSynchronizationManager.isActualTransactionActive());assertFalse(connection.getAutoCommit());
  holder=null;for(Object resource:TransactionSynchronizationManager.getResourceMap().values())if(resource instanceof ConnectionHolder h&&h.getConnection().unwrap(Connection.class)==connection.unwrap(Connection.class)){holder=h;break;}
  assertNotNull(holder,"Exact bound physical connection required");active=connection;savepoint=connection.setSavepoint();injected=true;
  TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){public void beforeCommit(boolean readOnly){assertTrue(restored,"Restore before any enclosing commit");}public void afterCompletion(int status){if(!restored)assertEquals(STATUS_ROLLED_BACK,status,"Unrestored fault must roll back whole transaction");}});
  try{for(var cell:cells){assertEquals(cell.original(),read(cell));try(var update=active.prepareStatement("UPDATE "+cell.table()+" SET request_manifest=? WHERE "+cell.key()+"=?")){update.setString(1,wire);update.setString(2,cell.id());assertEquals(1,update.executeUpdate());}}}catch(Throwable failed){holder.setRollbackOnly();try{restore();}catch(Throwable cleanup){failed.addSuppressed(cleanup);}throw failed;}
 }
 private String read(Cell cell)throws SQLException{try(var query=active.prepareStatement("SELECT request_manifest FROM "+cell.table()+" WHERE "+cell.key()+"=?")){query.setString(1,cell.id());try(var rows=query.executeQuery()){assertTrue(rows.next());String result=rows.getString(1);assertFalse(rows.next());return result;}}}
 private void restore()throws Exception{if(restored)return;try{active.rollback(savepoint);for(var cell:cells)assertEquals(cell.original(),read(cell));active.releaseSavepoint(savepoint);restored=true;}catch(Throwable failed){holder.setRollbackOnly();throw failed;}}
 DataSource wrap(DataSource actual){return (DataSource)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{DataSource.class},(proxy,method,args)->{
  Object value=Task11ReplacementRollbackIT.invoke(actual,method,args);if(!(value instanceof Connection connection))return value;
  return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(cp,cm,ca)->{
   Object result=Task11ReplacementRollbackIT.invoke(connection,cm,ca);if(!(result instanceof PreparedStatement statement)||ca==null||ca.length==0||!(ca[0] instanceof String sql))return result;
   return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(sp,sm,sa)->{
    boolean target=prefix!=null&&Thread.currentThread().threadId()==armedThread&&!injected&&sm.getName().equals("executeQuery")&&sql.startsWith(prefix);
    if(target)inject(connection);
    Object answer;try{answer=Task11ReplacementRollbackIT.invoke(statement,sm,sa);}catch(Throwable failed){if(target){holder.setRollbackOnly();try{restore();}catch(Throwable cleanup){failed.addSuppressed(cleanup);}}throw failed;}
    if(!target||!(answer instanceof ResultSet rows))return answer;
    return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ResultSet.class},(rp,rm,ra)->{
     if(rm.getName().equals("close")){try{return Task11ReplacementRollbackIT.invoke(rows,rm,ra);}finally{restore();}}
     try{Object field=Task11ReplacementRollbackIT.invoke(rows,rm,ra);if(field instanceof String text&&text.equals(wire))delivered=true;return field;}catch(Throwable failed){holder.setRollbackOnly();try{restore();}catch(Throwable cleanup){failed.addSuppressed(cleanup);}throw failed;}
    });
   });
  });
 });}
}
