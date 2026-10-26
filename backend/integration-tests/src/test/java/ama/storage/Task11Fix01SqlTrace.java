package ama.storage;
import java.sql.*;import java.lang.reflect.*;import java.util.*;import javax.sql.DataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
final class Task11Fix01SqlTrace {
 final List<Map<String,Object>> rows=new ArrayList<>();
 DataSource wrap(DataSource actual){final DataSource[] wrapped=new DataSource[1];wrapped[0]=(DataSource)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{DataSource.class},(proxy,method,args)->{Object value=Task11ReplacementRollbackIT.invoke(actual,method,args);if(!(value instanceof Connection connection))return value;return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(cp,cm,ca)->{Object result=Task11ReplacementRollbackIT.invoke(connection,cm,ca);if(!(result instanceof PreparedStatement statement)||ca==null||ca.length==0||!(ca[0] instanceof String sql))return result;return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(sp,sm,sa)->{if(sm.getName().startsWith("execute")){Object holder=null;for(Object resource:TransactionSynchronizationManager.getResourceMap().values())if(resource instanceof org.springframework.jdbc.datasource.ConnectionHolder h&&h.getConnection().unwrap(Connection.class)==connection.unwrap(Connection.class)){holder=h;break;}rows.add(Map.of("sql",sql,"transaction",TransactionSynchronizationManager.isActualTransactionActive(),"holder",holder==null?0:System.identityHashCode(holder),"thread",Thread.currentThread().threadId()));}return Task11ReplacementRollbackIT.invoke(statement,sm,sa);});});});return wrapped[0];}
 Map<String,Object> verify(){
  assertFalse(rows.stream().anyMatch(r->((String)r.get("sql")).startsWith("SELECT * FROM external_")));
  int transfers=0;for(int i=0;i<rows.size();i++){var r=rows.get(i);String sql=(String)r.get("sql");if(!(sql.startsWith("SELECT e.request_manifest execution_manifest")||sql.startsWith("SELECT request_manifest,no_effect_evidence")||sql.startsWith("SELECT request_manifest FROM publication_submission")))continue;transfers++;assertEquals(true,r.get("transaction"));assertNotEquals(0,r.get("holder"));
   var earlier=rows.subList(0,i).stream().filter(x->x.get("holder").equals(r.get("holder"))&&x.get("thread").equals(r.get("thread"))).toList();
   for(String table:List.of("meeting","final_confirmation","durable_task","external_execution"))assertTrue(earlier.stream().anyMatch(x->{String s=(String)x.get("sql");return s.contains("FROM "+table+" ")&&s.contains("FOR UPDATE");}),"transfer must retain its own transaction's "+table+" locks: "+sql);
  }
  assertTrue(transfers>0);return Map.of("transfers",transfers,"sql",List.copyOf(rows),"qualification","actual executed JDBC SQL, transaction holder and thread; predecessor locks checked within same transaction, no bind values or credentials");
 }
}
