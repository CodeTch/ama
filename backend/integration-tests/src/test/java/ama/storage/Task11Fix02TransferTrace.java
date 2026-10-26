package ama.storage;
import java.sql.*;import java.lang.reflect.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import javax.sql.DataSource;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
/** Actual SQL only; identities use reference equality of unwrapped physical connections/holders. */
final class Task11Fix02TransferTrace {
 final List<Map<String,Object>> rows=new CopyOnWriteArrayList<>();final ThreadLocal<String> phase=ThreadLocal.withInitial(()->"background");
 final IdentityHashMap<Object,Integer> identities=new IdentityHashMap<>();int serial;
 synchronized int identity(Object object){if(object==null)return 0;return identities.computeIfAbsent(object,k->++serial);}
 DataSource wrap(DataSource actual){return (DataSource)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{DataSource.class},(p,m,a)->{Object value=Task11ReplacementRollbackIT.invoke(actual,m,a);if(!(value instanceof Connection c))return value;return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(cp,cm,ca)->{Object result=Task11ReplacementRollbackIT.invoke(c,cm,ca);if(!(result instanceof PreparedStatement statement)||ca==null||ca.length==0||!(ca[0] instanceof String sql))return result;return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(sp,sm,sa)->{if(sm.getName().startsWith("execute")){Connection physical=c.unwrap(Connection.class);ConnectionHolder bound=null;for(Object resource:TransactionSynchronizationManager.getResourceMap().values())if(resource instanceof ConnectionHolder h&&h.getConnection().unwrap(Connection.class)==physical){bound=h;break;}rows.add(Map.of("sql",sql,"phase",phase.get(),"thread",Thread.currentThread().threadId(),"transaction",TransactionSynchronizationManager.isActualTransactionActive(),"holder",identity(bound),"physical",identity(physical)));}return Task11ReplacementRollbackIT.invoke(statement,sm,sa);});});});}
 synchronized Map<String,Object> releaseCompletedCall(){assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),"Never release identities during a live transaction");assertTrue(rows.isEmpty(),"Persist and clear rows before releasing identities");int retained=identities.size();identities.clear();return Map.of("releasedStrongIdentities",retained,"retainedAfter",identities.size(),"serial",serial);}
 long count(String prefix){return rows.stream().filter(r->((String)r.get("sql")).startsWith(prefix)).count();}
 long phaseCount(String label,String prefix){return rows.stream().filter(r->label.equals(r.get("phase"))&&((String)r.get("sql")).startsWith(prefix)).count();}
}
