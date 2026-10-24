package ama.storage;
import org.springframework.jdbc.datasource.AbstractDataSource;
import javax.sql.DataSource;
import java.sql.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Test-only actual JDBC observations and one-shot interleaving at the initial identity boundary. */
final class Task03TraceSource extends AbstractDataSource {
 final java.util.concurrent.atomic.AtomicBoolean refuseCommit=new java.util.concurrent.atomic.AtomicBoolean();
 final DataSource actual;final List<Event> locks=new java.util.concurrent.CopyOnWriteArrayList<>();final AtomicReference<Runnable> beforeIdentity=new AtomicReference<>(),beforeTask=new AtomicReference<>(),afterCommit=new AtomicReference<>();
 record Event(String sql,List<Object> parameters) {}
 Task03TraceSource(DataSource actual){this.actual=actual;}
 public Connection getConnection()throws SQLException{return wrap(actual.getConnection());}
 public Connection getConnection(String user,String password)throws SQLException{return wrap(actual.getConnection(user,password));}
 private Connection wrap(Connection connection){return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,args)->{
  if(m.getName().equals("commit")&&refuseCommit.compareAndSet(true,false)){connection.rollback();throw new SQLException("Task03 controlled read commit refusal");}
  Object result=invoke(connection,m,args);
  if(m.getName().equals("commit")){Runnable action=afterCommit.getAndSet(null);if(action!=null)action.run();}
  if(m.getName().equals("prepareStatement")&&args[0] instanceof String sql){
   var statement=(PreparedStatement)result;var values=new TreeMap<Integer,Object>();
   return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(s,method,a)->{
    if(method.getName().startsWith("set")&&a!=null&&a.length>=2&&a[0] instanceof Integer index)values.put(index,a[1]);
    if(method.getName().startsWith("execute")){
     if(sql.startsWith("SELECT id FROM durable_task WHERE")){Runnable action=beforeTask.getAndSet(null);if(action!=null)action.run();}
     if(sql.startsWith("SELECT enterprise_id,status")){Runnable action=beforeIdentity.getAndSet(null);if(action!=null)action.run();}
     if(sql.startsWith("SELECT")&&sql.contains("FOR UPDATE"))locks.add(new Event(sql,List.copyOf(values.values())));
    }
    return invoke(statement,method,a);
   });
  }return result;
 });}
 private static Object invoke(Object object,Method method,Object[] args)throws Throwable{try{return method.invoke(object,args);}catch(InvocationTargetException failure){throw failure.getCause();}}
}
