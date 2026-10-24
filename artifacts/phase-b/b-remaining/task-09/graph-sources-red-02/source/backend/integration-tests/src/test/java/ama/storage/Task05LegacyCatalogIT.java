package ama.storage;

import static org.junit.jupiter.api.Assertions.*;

import ama.db.SchemaMigrator;
import ama.support.MysqlTestDatabase;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

import javax.sql.DataSource;

/**
 * Real first-registration and revision races; gates delay actual query results, never replace them.
 */
class Task05LegacyCatalogIT {
    DataSource source;
    Task05NativeFixture fixture;
    FileStoragePort storage;
    JdbcTemplate j;

    @BeforeEach
    void start() throws Exception {
        source = MysqlTestDatabase.dataSource();
        assertEquals(13,new JdbcTemplate(source).queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1",Integer.class));
        j = new JdbcTemplate(source);
        fixture = new Task05NativeFixture(Task05RegressionResources.nativePath());
        storage = fixture.startServer();
    }

    @AfterEach
    void close() throws Exception {
        if (fixture != null) fixture.close();
    }

    FileBackendCatalog catalog(DataSource data, long revision, String status) {
        var backend =
                new FileBackendCatalog.Backend(
                        fixture.binding.id(), "local", revision, status, storage, fixture.binding);
        return new FileBackendCatalog(data, List.of(backend), backend.id());
    }

    @Test
    void identicalConcurrentFirstRegistrationsBothSucceed() throws Exception {
        var gate = new CyclicBarrier(2);
        var data =
                gated(
                        () -> {
                            try {
                                gate.await(10, TimeUnit.SECONDS);
                            } catch (Exception e) {
                                throw new AssertionError(e);
                            }
                        });
        var one = catalog(data, 1, "WRITABLE");
        var two = catalog(data, 1, "WRITABLE");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(one::configure);
            var second = pool.submit(two::configure);
            assertDoesNotThrow(() -> first.get(20, TimeUnit.SECONDS));
            assertDoesNotThrow(() -> second.get(20, TimeUnit.SECONDS));
        }
        assertEquals(
                1,
                j.queryForObject(
                        "SELECT COUNT(*) FROM storage_backend WHERE id=?",
                        Integer.class,
                        fixture.binding.id()));
        assertNotNull(one.defaultWrite());
        assertNotNull(two.defaultWrite());
    }

    @Test
    void conflictingSameRevisionFirstRegistrationsFailClosed() throws Exception {
        var gate = new CyclicBarrier(2);
        var data =
                gated(
                        () -> {
                            try {
                                gate.await(10, TimeUnit.SECONDS);
                            } catch (Exception e) {
                                throw new AssertionError(e);
                            }
                        });
        var one = catalog(data, 1, "WRITABLE");
        var two = catalog(data, 1, "READ_ONLY");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var results = List.of(pool.submit(() -> attempt(one)), pool.submit(() -> attempt(two)));
            int success = 0;
            for (var result : results) if (result.get(20, TimeUnit.SECONDS)) success++;
            assertEquals(1, success);
        }
        assertEquals(
                1,
                j.queryForObject(
                        "SELECT COUNT(*) FROM storage_backend WHERE id=?",
                        Integer.class,
                        fixture.binding.id()));
        assertEquals(
                1L,
                j.queryForObject(
                        "SELECT config_revision FROM storage_backend WHERE id=?",
                        Long.class,
                        fixture.binding.id()));
    }

    @Test
    void racedNewerRevisionIsNotOverwrittenOrAcceptedAsOlderConfiguration() throws Exception {
        catalog(source, 1, "WRITABLE").configure();
        var read = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var data =
                gated(
                        () -> {
                            read.countDown();
                            try {
                                assertTrue(release.await(10, TimeUnit.SECONDS));
                            } catch (InterruptedException e) {
                                throw new AssertionError(e);
                            }
                        });
        var older = catalog(data, 2, "WRITABLE");
        try (var pool = Executors.newSingleThreadExecutor()) {
            var result = pool.submit(() -> attempt(older));
            assertTrue(read.await(10, TimeUnit.SECONDS));
            try {
                catalog(source, 3, "READ_ONLY").configure();
            } finally {
                release.countDown();
            }
            assertFalse(
                    result.get(20, TimeUnit.SECONDS), "superseded configuration must fail startup");
        }
        assertEquals(
                3L,
                j.queryForObject(
                        "SELECT config_revision FROM storage_backend WHERE id=?",
                        Long.class,
                        fixture.binding.id()));
        assertEquals(
                "READ_ONLY",
                j.queryForObject(
                        "SELECT status FROM storage_backend WHERE id=?",
                        String.class,
                        fixture.binding.id()));
        assertThrows(IllegalStateException.class, () -> catalog(source, 2, "WRITABLE").configure());
    }

    static boolean attempt(FileBackendCatalog catalog) {
        try {
            catalog.configure();
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    DataSource gated(Runnable gate) {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        return (DataSource)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {DataSource.class},
                        (proxy, method, args) -> {
                            Object result = invoke(method, source, args);
                            if (!method.getName().equals("getConnection")) return result;
                            Connection connection = (Connection) result;
                            return Proxy.newProxyInstance(
                                    getClass().getClassLoader(),
                                    new Class<?>[] {Connection.class},
                                    (cp, cm, ca) -> {
                                        Object statement = invoke(cm, connection, ca);
                                        if (!cm.getName().equals("prepareStatement")
                                                || !(ca[0] instanceof String sql)
                                                || !sql.startsWith(
                                                        "SELECT storage_type,config_revision,status"
                                                            + " FROM storage_backend"))
                                            return statement;
                                        return Proxy.newProxyInstance(
                                                getClass().getClassLoader(),
                                                new Class<?>[] {PreparedStatement.class},
                                                (sp, sm, sa) -> {
                                                    Object actual = invoke(sm, statement, sa);
                                                    if (sm.getName().equals("executeQuery")
                                                            && calls.incrementAndGet() <= 2)
                                                        gate.run();
                                                    return actual;
                                                });
                                    });
                        });
    }

    static Object invoke(Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }
}
