package ama.task;

import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.AbstractDataSource;

/** Actual JDBC observation and deterministic fault gates; no fake database answers. */
final class Task08ProbeSource extends AbstractDataSource {
 record Sql(String text,Map<Integer,Object> parameters) {}
 final DataSource delegate;
 final List<Sql> sql=new CopyOnWriteArrayList<>();
 final List<String> controls=new CopyOnWriteArrayList<>();
 volatile long rows;
 volatile Consumer<Sql> before=x->{},after=x->{};
 volatile Runnable committed=()->{};
 Task08ProbeSource(DataSource delegate){this.delegate=delegate;}
 void clear(){sql.clear();controls.clear();rows=0;}
 public Connection getConnection()throws SQLException{
  Connection real=delegate.getConnection();controls.add("acquire");
  return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
   String name=m.getName();
   if(Set.of("setAutoCommit","commit","rollback","close","setReadOnly","setTransactionIsolation","setSavepoint","releaseSavepoint").contains(name))controls.add(name+(a==null?"":Arrays.toString(a)));
   Object value=invoke(real,m,a);
   if(name.equals("commit"))committed.run();
   if(value instanceof Statement statement && (name.equals("prepareStatement")||name.equals("createStatement"))){
    String prepared=name.equals("prepareStatement")?(String)a[0]:null;Map<Integer,Object> params=new TreeMap<>();
    return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{prepared==null?Statement.class:PreparedStatement.class},(p2,m2,a2)->{
     String n=m2.getName();
     if(prepared!=null&&n.startsWith("set")&&a2!=null&&a2.length>=2&&a2[0] instanceof Integer k)params.put(k,a2[1]);
     Sql event=null;if(n.startsWith("execute")){event=new Sql(prepared==null?(String)a2[0]:prepared,Collections.unmodifiableMap(new TreeMap<>(params)));sql.add(event);before.accept(event);}
     Object v=invoke(statement,m2,a2);
     if(event!=null){if(v instanceof Integer count&&count>0)rows+=count;if(v instanceof Long count&&count>0)rows+=count;after.accept(event);}
     if(v instanceof ResultSet result)return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ResultSet.class},(p3,m3,a3)->{Object x=invoke(result,m3,a3);if(m3.getName().equals("next")&&Boolean.TRUE.equals(x))rows++;return x;});
     return v;
    });
   }
   return value;
  });
 }
 public Connection getConnection(String u,String p)throws SQLException{return getConnection();}
 static Object invoke(Object target,Method m,Object[] a)throws Throwable{try{return m.invoke(target,a);}catch(InvocationTargetException e){throw e.getCause();}}
}
