package ama.storage;

import org.springframework.jdbc.datasource.AbstractDataSource;

import java.lang.reflect.*;
import java.sql.*;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

/** Real JDBC boundary faults: every statement/commit still reaches the actual owned MySQL. */
final class RegistryProbeSource extends AbstractDataSource {
    final DataSource actual;
    final AtomicReference<String> rollbackOn = new AtomicReference<>(),
            lostAckOn = new AtomicReference<>();
    volatile String insertedFile;
    final java.util.List<String> statements = new java.util.concurrent.CopyOnWriteArrayList<>();

    RegistryProbeSource(DataSource actual) {
        this.actual = actual;
    }

    public Connection getConnection() throws SQLException {
        return wrap(actual.getConnection());
    }

    public Connection getConnection(String user, String password) throws SQLException {
        return wrap(actual.getConnection(user, password));
    }

    Connection wrap(Connection connection) {
        var changed = new StringBuilder();
        return (Connection)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class[] {Connection.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("prepareStatement")
                                    && args[0] instanceof String sql) {
                                var statement =
                                        (PreparedStatement) invoke(connection, method, args);
                                return Proxy.newProxyInstance(
                                        getClass().getClassLoader(),
                                        new Class[] {PreparedStatement.class},
                                        (p, m, a) -> {
                                            if (sql.startsWith("INSERT INTO file_object")
                                                    && m.getName().equals("setString")
                                                    && ((Integer) a[0]) == 1)
                                                insertedFile = (String) a[1];
                                            if (m.getName().startsWith("execute")
                                                    && (sql.startsWith("INSERT")
                                                            || sql.startsWith("UPDATE")))
                                                changed.append(sql).append('\n');
                                            if (m.getName().startsWith("execute"))
                                                statements.add(sql);
                                            return invoke(statement, m, a);
                                        });
                            }
                            if (method.getName().equals("commit")) {
                                String fail = rollbackOn.get();
                                if (fail != null
                                        && changed.indexOf(fail) >= 0
                                        && rollbackOn.compareAndSet(fail, null)) {
                                    connection.rollback();
                                    throw new SQLException("Controlled commit refusal");
                                }
                                Object result = invoke(connection, method, args);
                                String lost = lostAckOn.get();
                                if (lost != null
                                        && changed.indexOf(lost) >= 0
                                        && lostAckOn.compareAndSet(lost, null))
                                    throw new SQLException("Controlled lost commit acknowledgment");
                                return result;
                            }
                            return invoke(connection, method, args);
                        });
    }

    static Object invoke(Object object, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(object, args);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }
}
