package ama.storage;
import java.sql.*;import java.lang.reflect.*;import java.util.*;
/** Bounded test-only SQL shape and caller frames; never parameter values or exception messages. */
final class Task09TraceSource extends org.springframework.jdbc.datasource.AbstractDataSource {
 final javax.sql.DataSource actual;final List<Map<String,Object>> events=new java.util.concurrent.CopyOnWriteArrayList<>();
 Task09TraceSource(javax.sql.DataSource actual){this.actual=actual;}
 public Connection getConnection()throws SQLException {var connection=actual.getConnection();return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,a)->{Object value=invoke(connection,m,a);if(value instanceof Statement statement&&(m.getName().equals("prepareStatement")||m.getName().equals("createStatement"))){String sql=m.getName().equals("prepareStatement")?(String)a[0]:null;return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{sql==null?Statement.class:PreparedStatement.class},(p2,m2,a2)->{if(m2.getName().startsWith("execute")){if(events.size()>=10000)throw new IllegalStateException("Diagnostic bound");events.add(Map.of("sql",sql==null?(String)a2[0]:sql,"frames",Arrays.stream(Thread.currentThread().getStackTrace()).filter(f->f.getClassName().startsWith("ama.")).map(Object::toString).toList()));}return invoke(statement,m2,a2);});}return value;});}
 public Connection getConnection(String username,String password)throws SQLException{return getConnection();}
 static Object invoke(Object target,Method method,Object[] args)throws Throwable{try{return method.invoke(target,args);}catch(InvocationTargetException e){throw e.getCause();}}
}
