package ama.storage;
import org.springframework.jdbc.datasource.AbstractDataSource;
import java.lang.reflect.*;
import java.sql.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
/** Real JDBC boundary faults: every statement/commit still reaches the actual owned MySQL. */
final class Task06ProbeSource extends AbstractDataSource {
    final DataSource actual;
    volatile Integer isolation;
    final AtomicReference<String> rollbackOn = new AtomicReference<>(), lostAckOn = new AtomicReference<>();
    final AtomicReference<String> failStatement=new AtomicReference<>();
    volatile java.util.function.Consumer<String> before=sql-> {
    };
    volatile java.util.function.Consumer<String> after=sql-> {
    };
    final java.util.Set<Thread> rootsHeld=java.util.concurrent.ConcurrentHashMap.newKeySet();
    final java.util.List<String> lockTrace=new java.util.concurrent.CopyOnWriteArrayList<>();
    volatile java.util.function.BiConsumer<String,java.util.Map<Integer,Object>> afterBound=(sql,parameters)->{};
    final java.util.List<java.util.Map<String,Object>> events=new java.util.concurrent.CopyOnWriteArrayList<>();
    final java.util.concurrent.atomic.AtomicLong connections=new java.util.concurrent.atomic.AtomicLong();
    volatile String insertedFile;
    final java.util.List<String> statements = new java.util.concurrent.CopyOnWriteArrayList<>();
    Task06ProbeSource(DataSource actual) {
        this.actual = actual;
    }
    public Connection getConnection() throws SQLException {
        return wrap(actual.getConnection());
    }
    public Connection getConnection(String user, String password) throws SQLException {
        return wrap(actual.getConnection(user, password));
    }
    Connection wrap(Connection connection) throws SQLException {
        if(isolation!=null)connection.setTransactionIsolation(isolation);
        long connectionId=connections.incrementAndGet();
        var changed = new StringBuilder();
        return (Connection) Proxy.newProxyInstance( getClass().getClassLoader(), new Class[] {
            Connection.class
        }, (proxy, method, args) -> {
            if(method.getName().equals("close"))rootsHeld.remove(Thread.currentThread());
            if (method.getName().equals("prepareStatement") && args[0] instanceof String sql) {
                var statement = (PreparedStatement) invoke(connection, method, args);
                var parameters=new java.util.HashMap<Integer,Object>();
                return Proxy.newProxyInstance( getClass().getClassLoader(), new Class[] {
                    PreparedStatement.class
                }, (p, m, a) -> {
                    if(m.getName().startsWith("set")&&a!=null&&a.length>1&&a[0] instanceof Integer index)parameters.put(index,a[1]);
                    if(m.getName().startsWith("execute")&&sql.contains("FOR UPDATE")) {
                        if(sql.contains("FROM task_subject WHERE")||sql.contains("FROM meeting WHERE"))rootsHeld.add(Thread.currentThread());
                        for(String table:java.util.List.of("enterprise_identity","task_subject","meeting","transcription_input","input_batch","input_batch_fingerprint_profile","input_batch_file","upload_session","file_object","durable_task","task_attempt"))if(sql.contains("FROM "+table+" WHERE")) {
                            Object id=parameters.get(table.equals("task_subject")?2:1);
                            lockTrace.add(Thread.currentThread().getName()+" "+table+" "+id);
                        }
                    }
                    if (sql.startsWith("INSERT INTO file_object") && m.getName().equals("setString") && ((Integer) a[0]) == 1) insertedFile = (String) a[1];
                    if (m.getName().startsWith("execute") && (sql.startsWith("INSERT") || sql.startsWith("UPDATE"))) changed.append(sql).append('\n');
                    if (m.getName().startsWith("execute")){statements.add(sql);events.add(java.util.Map.of("connection",connectionId,"thread",Thread.currentThread().getName(),"sql",sql));}
                    if(m.getName().startsWith("execute")) {
                        before.accept(sql);
                        String failure=failStatement.get();
                        if(failure!=null&&sql.contains(failure)&&failStatement.compareAndSet(failure,null))throw new SQLException("Task6 controlled statement refusal");
                        Object result=invoke(statement,m,a);
                        after.accept(sql);afterBound.accept(sql,java.util.Collections.unmodifiableMap(new java.util.HashMap<>(parameters)));
                        return result;
                    }
                    return invoke(statement, m, a);
                });
            }
            if (method.getName().equals("commit")||method.getName().equals("rollback"))events.add(java.util.Map.of("connection",connectionId,"thread",Thread.currentThread().getName(),"sql",method.getName()));
            if (method.getName().equals("commit")) {
                String fail = rollbackOn.get();
                if (fail != null && changed.indexOf(fail) >= 0 && rollbackOn.compareAndSet(fail, null)) {
                    connection.rollback();
                    throw new SQLException("Controlled commit refusal");
                }
                Object result = invoke(connection, method, args);
                String lost = lostAckOn.get();
                if (lost != null && changed.indexOf(lost) >= 0 && lostAckOn.compareAndSet(lost, null)) throw new SQLException("Controlled lost commit acknowledgment");
                return result;
            }
            return invoke(connection, method, args);
        });
    }
    static Object invoke(Object object, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(object, args);
        }
        catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }
}
