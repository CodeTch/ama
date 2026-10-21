package ama.db;

import ama.support.MysqlTestDatabase;
import ama.audit.JdbcAuditAdapter;
import ama.contract.AuditContracts.AuditEvent;
import java.time.Instant;
import org.springframework.jdbc.datasource.DataSourceUtils;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

class FoundationIT {
    private DataSource source;
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetOwnedTables() {
        source = MysqlTestDatabase.dataSource();
        jdbc = new JdbcTemplate(source);
        jdbc.execute("DROP TABLE IF EXISTS b01_probe");
        jdbc.execute("DROP TABLE IF EXISTS audit_event");
        jdbc.execute("DROP TABLE IF EXISTS flyway_schema_history");
    }

    @Test
    void migrationAppliesOnce() {
        var migrator = new SchemaMigrator(source);
        assertEquals(1, migrator.migrate(), "fresh MySQL must apply the foundation migration");
        assertEquals(0, migrator.migrate(), "redeploy must leave the existing migration unchanged");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version='001' AND success=1", Integer.class));
        assertEquals("utf8mb4", jdbc.queryForObject(
                "SELECT CCSA.character_set_name FROM information_schema.tables T JOIN information_schema.collation_character_set_applicability CCSA ON T.table_collation=CCSA.collation_name WHERE T.table_schema=DATABASE() AND T.table_name='audit_event'", String.class));
    }

    @Test
    void independentMigratorsWaitForDatabaseLockThenApplyOnce() throws Exception {
        try (var lock = source.getConnection(); var statement = lock.createStatement()) {
            statement.execute("SELECT GET_LOCK('ama.schema.migration', 5)");
            Process first = migratorProcess();
            Process second = migratorProcess();
            try {
                assertFalse(first.waitFor(1500, TimeUnit.MILLISECONDS), "first independent migrator must wait for the database lock");
                assertFalse(second.waitFor(1500, TimeUnit.MILLISECONDS), "second independent migrator must wait for the database lock");
                statement.execute("SELECT RELEASE_LOCK('ama.schema.migration')");
                assertTrue(first.waitFor(30, TimeUnit.SECONDS));
                assertTrue(second.waitFor(30, TimeUnit.SECONDS));
                assertEquals(0, first.exitValue());
                assertEquals(0, second.exitValue());
                assertEquals(1, jdbc.queryForObject(
                        "SELECT COUNT(*) FROM flyway_schema_history WHERE version='001' AND success=1", Integer.class));
            } finally {
                first.destroyForcibly();
                second.destroyForcibly();
            }
        }
    }

    @Test
    void rollbackDoesNotLeaveSuccessAudit() {
        new SchemaMigrator(source).migrate();
        jdbc.execute("CREATE TABLE b01_probe (id INT PRIMARY KEY) ENGINE=InnoDB");
        var runner = new TransactionRunner(source);
        var audit = new JdbcAuditAdapter(source);
        assertThrows(IllegalStateException.class, () -> runner.required(() -> {
            jdbc.update("INSERT INTO b01_probe VALUES (1)");
            audit.append(event());
            throw new IllegalStateException("business failure");
        }));
        int committedRows = jdbc.queryForObject("SELECT COUNT(*) FROM b01_probe", Integer.class);
        int successfulAuditRows = jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE result='SUCCESS'", Integer.class);
        assertAll(() -> assertEquals(0, committedRows), () -> assertEquals(0, successfulAuditRows));
    }

    @Test
    void connectionLossRollsBackAndFreshJdbcRecovers() throws Exception {
        new SchemaMigrator(source).migrate();
        jdbc.execute("CREATE TABLE b01_probe (id INT PRIMARY KEY) ENGINE=InnoDB");
        var runner = new TransactionRunner(source);
        var audit = new JdbcAuditAdapter(source);
        assertThrows(RuntimeException.class, () -> runner.required(() -> {
            jdbc.update("INSERT INTO b01_probe VALUES (1)");
            audit.append(event());
            // Use a separate JDBC session to kill the exact caller connection.
            var bound = DataSourceUtils.getConnection(source);
            long id;
            try (var statement = bound.createStatement(); var result = statement.executeQuery("SELECT CONNECTION_ID()")) {
                result.next();
                id = result.getLong(1);
            }
            try (var killer = source.getConnection(); var statement = killer.createStatement()) {
                statement.execute("KILL CONNECTION " + id);
            }
            try (var statement = bound.createStatement()) {
                statement.execute("SELECT 1");
            }
            return null;
        }));
        assertAll(
                () -> assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM b01_probe", Integer.class)),
                () -> assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)));
        runner.required(() -> {
            jdbc.update("INSERT INTO b01_probe VALUES (2)");
            audit.append(event());
            return null;
        });
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM b01_probe", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class));
    }

    @Test
    void checkedExceptionRollsBackBusinessAndAudit() {
        new SchemaMigrator(source).migrate();
        jdbc.execute("CREATE TABLE b01_probe (id INT PRIMARY KEY) ENGINE=InnoDB");
        assertThrows(RuntimeException.class, () -> new TransactionRunner(source).required(() -> {
            jdbc.update("INSERT INTO b01_probe VALUES (1)");
            new JdbcAuditAdapter(source).append(event());
            throw new java.io.IOException("checked business failure");
        }));
        assertAll(
                () -> assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM b01_probe", Integer.class)),
                () -> assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)));
    }

    @Test
    void auditCannotAutocommitWithoutCallerTransaction() {
        new SchemaMigrator(source).migrate();
        assertThrows(IllegalStateException.class, () -> new JdbcAuditAdapter(source).append(event()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class));
    }

    @Test
    void nestedRequiredCannotCommitAfterInnerFailureIsCaught() {
        new SchemaMigrator(source).migrate();
        jdbc.execute("CREATE TABLE b01_probe (id INT PRIMARY KEY) ENGINE=InnoDB");
        var runner = new TransactionRunner(source);
        assertThrows(RuntimeException.class, () -> runner.required(() -> {
            jdbc.update("INSERT INTO b01_probe VALUES (1)");
            try {
                runner.required(() -> {
                    new JdbcAuditAdapter(source).append(event());
                    throw new IllegalStateException("inner failure");
                });
            } catch (IllegalStateException expected) {
                // Catching it cannot make a rollback-only transaction successful.
            }
            return null;
        }));
        assertAll(
                () -> assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM b01_probe", Integer.class)),
                () -> assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)));
    }

    @Test
    void successfulTransactionStoresUtf8mb4AndUtcMetadata() {
        new SchemaMigrator(source).migrate();
        var time = Instant.parse("2026-10-20T19:45:12.123456Z");
        var runner = new TransactionRunner(source);
        assertEquals("committed", runner.required(() -> {
            assertEquals("+00:00", jdbc.queryForObject("SELECT @@session.time_zone", String.class));
            new JdbcAuditAdapter(source).append(new AuditEvent("张三😀", "CONFIRM", "meeting-1",
                    3, "SUCCESS", "trace-1", time));
            return "committed";
        }));
        var row = jdbc.queryForMap("SELECT actor_id, action, resource_id, revision, result, trace_id, CAST(occurred_at AS CHAR) AS occurred_at FROM audit_event");
        assertEquals("张三😀", row.get("actor_id"));
        assertEquals("CONFIRM", row.get("action"));
        assertEquals("meeting-1", row.get("resource_id"));
        assertEquals(3L, ((Number) row.get("revision")).longValue());
        assertEquals("SUCCESS", row.get("result"));
        assertEquals("trace-1", row.get("trace_id"));
        assertEquals("2026-10-20 19:45:12.123456", row.get("occurred_at"));
        assertEquals(0, new SchemaMigrator(source).migrate());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class),
                "redeployment must preserve existing data");
    }

    private AuditEvent event() {
        return new AuditEvent("actor-1", "CONFIRM", "meeting-1", 3, "SUCCESS", "trace-1",
                Instant.parse("2026-10-20T19:45:12.123456Z"));
    }

    private Process migratorProcess() throws Exception {
        var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), MigrationMain.class.getName());
        for (String suffix : new String[]{"URL", "USERNAME", "PASSWORD"}) {
            builder.environment().put("DATABASE_" + suffix, System.getenv("AMA_TEST_DATABASE_" + suffix));
        }
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        return builder.start();
    }
}
