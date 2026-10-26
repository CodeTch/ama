package ama.storage;
import java.sql.*;import java.lang.reflect.*;import java.util.*;import javax.sql.DataSource;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
/** Test-only one-shot fault on the exact bound transaction; holder is rollback-only before SQL. */
final class Task11Fix01RollbackFault {
 enum Kind { MANIFEST, EVIDENCE, SOURCE }
 Kind kind;String id;boolean injected;final List<String> after=new ArrayList<>();
 void arm(Kind kind,String id){this.kind=kind;this.id=id;injected=false;after.clear();}
 void disarm(){kind=null;}
 DataSource wrap(DataSource actual){return (DataSource)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{DataSource.class},(proxy,method,args)->{Object value=Task11ReplacementRollbackIT.invoke(actual,method,args);if(!(value instanceof Connection connection))return value;return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(cp,cm,ca)->{Object result=Task11ReplacementRollbackIT.invoke(connection,cm,ca);if(!(result instanceof PreparedStatement statement)||ca==null||ca.length==0||!(ca[0] instanceof String sql))return result;return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(sp,sm,sa)->{if(sm.getName().startsWith("execute")){
   boolean match=kind==Kind.MANIFEST&&sql.startsWith("SELECT e.task_id,OCTET_LENGTH(e.request_manifest)")||kind==Kind.EVIDENCE&&sql.startsWith("SELECT OCTET_LENGTH(request_manifest) mb")||kind==Kind.SOURCE&&sql.startsWith("SELECT id FROM upload_session WHERE completed_file_id=");
   if(kind!=null&&!injected&&match){assertTrue(TransactionSynchronizationManager.isActualTransactionActive());assertFalse(connection.getAutoCommit());ConnectionHolder bound=null;for(Object resource:TransactionSynchronizationManager.getResourceMap().values())if(resource instanceof ConnectionHolder h&&h.getConnection().unwrap(Connection.class)==connection.unwrap(Connection.class)){bound=h;break;}assertNotNull(bound,"same physical bound connection required");bound.setRollbackOnly();assertTrue(bound.isRollbackOnly());injected=true;
    String table=kind==Kind.SOURCE?"upload_session":"external_execution",column=kind==Kind.MANIFEST?"request_manifest":kind==Kind.EVIDENCE?"no_effect_evidence":"purpose";
    try(var fault=connection.prepareStatement("UPDATE "+table+" SET "+column+"=? WHERE id=?")){fault.setString(1,kind==Kind.SOURCE?"FIX01_UNAPPROVED_SOURCE":"x".repeat(65537));fault.setString(2,id);assertEquals(1,fault.executeUpdate());}
   }
   if(injected)after.add(sql);
  }return Task11ReplacementRollbackIT.invoke(statement,sm,sa);});});});}
}
