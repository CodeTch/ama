package ama.storage;

import ama.db.TransactionRunner;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import static org.junit.jupiter.api.Assertions.*;

/** Offline contract tests: real guard, JdbcTemplate and TransactionRunner over inert JDBC handles. */
class Task11B2ExactRetryReadbackGuardTest {
    static final String HASH="a".repeat(64), ACTOR="synthetic-owner", CSRF="T".repeat(43);
    final Task11B2ExactRetryReadbackIT readback=new Task11B2ExactRetryReadbackIT();
    final FakeJdbc fake=new FakeJdbc();
    final DataSource source=readback.guard(fake);

    @Test void realRequiredTransactionInitializesBothSettingsAndKeepsTheBoundConnection() {
        String result=new TransactionRunner(source,10).required(()->{
            Connection first=DataSourceUtils.getConnection(source), second=DataSourceUtils.getConnection(source);
            assertSame(first,second);
            assertSame(first,first.unwrap(Connection.class));
            try(Statement statement=first.createStatement()) { assertSame(first,statement.getConnection()); }
            DataSourceUtils.releaseConnection(second,source);DataSourceUtils.releaseConnection(first,source);
            new JdbcTemplate(source).execute("SELECT 1");
            return "receipt-lookup-reached";
        });
        assertEquals("receipt-lookup-reached",result);
        assertEquals(List.of("SET NAMES utf8mb4","SET time_zone = '+00:00'","SELECT 1"),fake.sql);
        assertEquals(1,fake.connections);assertEquals(1,fake.commits);assertEquals(0,fake.rollbacks);assertEquals(1,fake.closes);
    }

    @Test void exactSessionInsertAndDeleteBindHashActorAndCsrfThroughJdbcTemplate() {
        var jdbc=new JdbcTemplate(source);
        readback.sessionPermit.set(new Task11B2ExactRetryReadbackIT.Permit(Task11B2ExactRetryReadbackIT.INSERT_SESSION,HASH,ACTOR));
        try { assertEquals(1,jdbc.update(Task11B2ExactRetryReadbackIT.INSERT_SESSION,HASH,CSRF,ACTOR)); }
        finally { readback.sessionPermit.remove(); }
        readback.sessionPermit.set(new Task11B2ExactRetryReadbackIT.Permit(Task11B2ExactRetryReadbackIT.DELETE_SESSION,HASH,ACTOR));
        try { assertEquals(1,jdbc.update(Task11B2ExactRetryReadbackIT.DELETE_SESSION,HASH)); }
        finally { readback.sessionPermit.remove(); }
        assertEquals(List.of(Map.of(1,HASH,2,CSRF,3,ACTOR),Map.of(1,HASH)),fake.bindings);
    }

    @Test void unrelatedSetAndBusinessDmlDdlRemainDeniedEvenWithSessionPermit()throws Exception {
        try(Connection c=source.getConnection();Statement s=c.createStatement()) {
            for(String sql:List.of("SET sql_safe_updates = 0","SET NAMES utf8mb4; SELECT 1"," SET NAMES utf8mb4","SET NAMES utf8mb4;","set names utf8mb4","SET time_zone = '+01:00'","UPDATE publication SET revision=0","DELETE FROM identity_session","CREATE TABLE forbidden(id INT)","DROP TABLE publication","CALL forbidden()")) {
                assertThrows(AssertionError.class,()->s.execute(sql),sql);
                readback.sessionPermit.set(new Task11B2ExactRetryReadbackIT.Permit(Task11B2ExactRetryReadbackIT.INSERT_SESSION,HASH,ACTOR));
                try { assertThrows(AssertionError.class,()->s.execute(sql),sql); }
                finally { readback.sessionPermit.remove(); }
            }
        }
        assertTrue(fake.sql.isEmpty());
    }

    @Test void missingAndMismatchedSessionPermissionsNeverReachTheDelegate()throws Exception {
        try(Connection c=source.getConnection();PreparedStatement s=c.prepareStatement(Task11B2ExactRetryReadbackIT.INSERT_SESSION)) {
            bind(s,HASH,CSRF,ACTOR);assertThrows(AssertionError.class,s::executeUpdate);
            for(var permit:List.of(new Task11B2ExactRetryReadbackIT.Permit(Task11B2ExactRetryReadbackIT.DELETE_SESSION,HASH,ACTOR),new Task11B2ExactRetryReadbackIT.Permit(Task11B2ExactRetryReadbackIT.INSERT_SESSION,"wrong-hash",ACTOR),new Task11B2ExactRetryReadbackIT.Permit(Task11B2ExactRetryReadbackIT.INSERT_SESSION,HASH,"wrong-actor"))) {
                readback.sessionPermit.set(permit);
                try { assertThrows(AssertionError.class,s::executeUpdate); }
                finally { readback.sessionPermit.remove(); }
            }
        }
        assertTrue(fake.sql.isEmpty());
    }

    @Test void wrongOrClearedHashActorCsrfBindingsAreRejected()throws Exception {
        readback.sessionPermit.set(new Task11B2ExactRetryReadbackIT.Permit(Task11B2ExactRetryReadbackIT.INSERT_SESSION,HASH,ACTOR));
        try(Connection c=source.getConnection();PreparedStatement s=c.prepareStatement(Task11B2ExactRetryReadbackIT.INSERT_SESSION)) {
            for(String[] values:List.of(new String[]{"wrong",CSRF,ACTOR},new String[]{HASH,"wrong",ACTOR},new String[]{HASH,CSRF,"wrong"})) {
                bind(s,values[0],values[1],values[2]);assertThrows(AssertionError.class,s::executeUpdate);
            }
            bind(s,HASH,CSRF,ACTOR);s.clearParameters();assertThrows(AssertionError.class,s::executeUpdate);
            s.setString(1,HASH);s.setString(2,CSRF);assertThrows(AssertionError.class,s::executeUpdate);
        } finally { readback.sessionPermit.remove(); }
        readback.sessionPermit.set(new Task11B2ExactRetryReadbackIT.Permit(Task11B2ExactRetryReadbackIT.DELETE_SESSION,HASH,ACTOR));
        try(Connection c=source.getConnection();PreparedStatement s=c.prepareStatement(Task11B2ExactRetryReadbackIT.DELETE_SESSION)) {
            assertThrows(AssertionError.class,s::executeUpdate);s.setString(1,"wrong");assertThrows(AssertionError.class,s::executeUpdate);
        } finally { readback.sessionPermit.remove(); }
        assertTrue(fake.sql.isEmpty());
    }

    @Test void permitsStayOnTheIssuingThread()throws Exception {
        readback.sessionPermit.set(new Task11B2ExactRetryReadbackIT.Permit(Task11B2ExactRetryReadbackIT.DELETE_SESSION,HASH,ACTOR));
        var failure=new AtomicReference<Throwable>();
        Thread child=new Thread(()->{try { assertThrows(AssertionError.class,()->new JdbcTemplate(source).update(Task11B2ExactRetryReadbackIT.DELETE_SESSION,HASH)); } catch(Throwable t){failure.set(t);}});
        try { child.start();child.join(1000);assertFalse(child.isAlive());if(failure.get()!=null)throw new AssertionError(failure.get()); }
        finally { readback.sessionPermit.remove(); }
        assertTrue(fake.sql.isEmpty());
    }

    @Test void callableBatchAndStatementUnwrapStayForbidden()throws Exception {
        try(Connection c=source.getConnection();Statement s=c.createStatement();PreparedStatement p=c.prepareStatement("SELECT 1")) {
            assertEquals("Callable SQL forbidden",assertThrows(AssertionError.class,()->c.prepareCall("SELECT 1")).getMessage());
            assertEquals("Batch SQL forbidden",assertThrows(AssertionError.class,()->s.addBatch("SELECT 1")).getMessage());
            assertEquals("Batch SQL forbidden",assertThrows(AssertionError.class,s::executeBatch).getMessage());
            assertEquals("Batch SQL forbidden",assertThrows(AssertionError.class,p::addBatch).getMessage());
            assertEquals("Batch SQL forbidden",assertThrows(AssertionError.class,p::executeLargeBatch).getMessage());
            assertEquals("Statement unwrap forbidden",assertThrows(AssertionError.class,()->s.unwrap(Statement.class)).getMessage());
            assertEquals("Statement unwrap forbidden",assertThrows(AssertionError.class,()->p.unwrap(PreparedStatement.class)).getMessage());
        }
        assertTrue(fake.sql.isEmpty());
    }

    static void bind(PreparedStatement s,String hash,String csrf,String actor)throws SQLException {s.setString(1,hash);s.setString(2,csrf);s.setString(3,actor);}
    static final class FakeJdbc extends AbstractDataSource {
        final List<String> sql=new ArrayList<>();final List<Map<Integer,Object>> bindings=new ArrayList<>();
        int connections,commits,rollbacks,closes;
        @Override public Connection getConnection() {
            connections++;
            return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,a)->switch(m.getName()) {
                case "createStatement" -> statement(null);
                case "prepareStatement" -> statement((String)a[0]);
                case "getAutoCommit" -> true;
                case "setAutoCommit","clearWarnings" -> null;
                case "isClosed","isReadOnly" -> false;
                case "commit" -> {commits++;yield null;}
                case "rollback" -> {rollbacks++;yield null;}
                case "close" -> {closes++;yield null;}
                case "toString" -> "InertJdbcConnection";
                default -> throw new AssertionError("Unexpected fake connection method: "+m.getName());
            });
        }
        @Override public Connection getConnection(String u,String p){throw new AssertionError("No alternate credentials");}
        Object statement(String prepared) {
            var parameters=new HashMap<Integer,Object>();
            return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{prepared==null?Statement.class:PreparedStatement.class},(p,m,a)->{
                if(m.getName().startsWith("set")&&a!=null&&a.length>=2&&a[0] instanceof Integer index){parameters.put(index,a[1]);return null;}
                return switch(m.getName()) {
                    case "execute","executeUpdate" -> {sql.add(prepared==null?(String)a[0]:prepared);bindings.add(Map.copyOf(parameters));yield m.getName().equals("execute")?Boolean.FALSE:Integer.valueOf(1);}
                    case "clearParameters" -> {parameters.clear();yield null;}
                    case "getWarnings","close","setQueryTimeout" -> null;
                    case "isClosed" -> false;
                    default -> throw new AssertionError("Unexpected fake statement method: "+m.getName());
                };
            });
        }
    }
}