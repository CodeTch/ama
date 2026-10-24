package ama.db;

import ama.support.MysqlTestDatabase;
import ama.support.ExpectedRollbackError;
import ama.support.ExpectedAuditMigrationFailure;
import ama.audit.JdbcAuditAdapter;
import ama.contract.AuditContracts.AuditEvent;
import java.time.Instant;
import org.springframework.jdbc.datasource.DataSourceUtils;
import java.nio.file.Path;
import java.nio.file.Files;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.io.TempDir;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.flywaydb.core.api.FlywayException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.TimeZone;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

class FoundationIT {
    private DataSource source;
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetOwnedTables() {
        source = MysqlTestDatabase.reset();
        jdbc = new JdbcTemplate(source);
    }

    @Test
    void migrationAppliesOnce() {
        var migrator = new SchemaMigrator(source);
        assertTrue(migrator.migrate() >= 1, "fresh MySQL must apply the project migrations");
        var history = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        assertEquals(0, migrator.migrate(), "redeploy must leave the existing migration unchanged");
        assertEquals(history, jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank"));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version='001' AND success=1", Integer.class));
        assertEquals("utf8mb4", jdbc.queryForObject(
                "SELECT CCSA.character_set_name FROM information_schema.tables T JOIN information_schema.collation_character_set_applicability CCSA ON T.table_collation=CCSA.collation_name WHERE T.table_schema=DATABASE() AND T.table_name='audit_event'", String.class));
    }

    @Test
    void consecutiveResetsRemoveTablesFromLaterMigrations(@TempDir Path migrations) throws Exception {
        new SchemaMigrator(source).migrate();
        var production = Flyway.configure().dataSource(source).locations("classpath:db/migration").load();
        // Local-only migration follows whatever versions the project currently ships.
        // It is never placed on the shared test/production classpath.
        String version = production.info().current().getVersion() + ".1";
        Files.writeString(migrations.resolve("V" + version + "__b01_reset_probe.sql"),
                "CREATE TABLE b01_future_fixture (id INT PRIMARY KEY) ENGINE=InnoDB");
        for (int cycle = 0; cycle < 2; cycle++) {
            var extra = Flyway.configure().dataSource(source)
                    .locations("classpath:db/migration", "filesystem:" + migrations)
                    .cleanDisabled(true).load();
            assertEquals(1, extra.migrate().migrationsExecuted);
            jdbc.update("INSERT INTO b01_future_fixture VALUES (1)");
            resetOwnedTables();
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class),
                    "fixture reset must remove objects from later migrations too");
            new SchemaMigrator(source).migrate();
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version='001' AND success=1", Integer.class));
        }
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
        try (var expectedLog = new ExpectedRollbackError()) {
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
            assertEquals(1, expectedLog.failures().size(), "exactly the expected killed-connection rollback error is captured");
            assertInstanceOf(java.sql.SQLException.class, expectedLog.failures().getFirst().getCause());
        }
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

    @Test
    void frozenBaselineUpgradesWithoutChangingRowsOrHistory() {
        migrateFrozenBaseline();
        jdbc.update("INSERT INTO audit_event VALUES (41, ?, ?, ?, ?, ?, ?, ?)",
                "操作者😀 ", "A".repeat(64), "R".repeat(254) + " ", Long.MAX_VALUE,
                "S".repeat(64), "trace ", "2026-10-20 19:45:12.123456");
        var originalRows = auditRows();
        var originalHistory = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        assertEquals(List.of("001", "002", "005"), jdbc.queryForList(
                "SELECT version FROM flyway_schema_history ORDER BY installed_rank", String.class));

        assertEquals(1, Flyway.configure().dataSource(source).locations("classpath:db/migration")
                .target("006").cleanDisabled(true).load().migrate().migrationsExecuted,
                "assigned V006 upgrades the frozen low-version set");
        assertEquals(originalRows, auditRows(), "IDs, full identifiers, codes, revision and time survive conversion");
        assertEquals(originalHistory, jdbc.queryForList(
                "SELECT * FROM flyway_schema_history WHERE version IN ('001','002','005') ORDER BY installed_rank"));
        assertEquals(List.of("001", "002", "005", "006"), jdbc.queryForList(
                "SELECT version FROM flyway_schema_history ORDER BY installed_rank", String.class));
        assertEquals(List.of("PRIMARY:id", "audit_resource_time:resource_id,occurred_at,id", "audit_trace:trace_id"),
                jdbc.queryForList("SELECT CONCAT(index_name, ':', GROUP_CONCAT(column_name ORDER BY seq_in_index)) "
                        + "FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='audit_event' "
                        + "GROUP BY index_name ORDER BY BINARY index_name", String.class));
        assertEquals("bigint unsigned", jdbc.queryForObject("SELECT column_type FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND table_name='audit_event' AND column_name='revision'", String.class));
        for (String column : List.of("action", "result")) {
            assertEquals("varchar(64):ascii:ascii_bin", jdbc.queryForObject(
                    "SELECT CONCAT(column_type, ':', character_set_name, ':', collation_name) FROM information_schema.columns "
                    + "WHERE table_schema=DATABASE() AND table_name='audit_event' AND column_name=?", String.class, column));
        }
        // Future registered migrations can coexist without hard-coding a global high water mark.
        int pending = Flyway.configure().dataSource(source).locations("classpath:db/migration").load().info().pending().length;
        var migrator = new SchemaMigrator(source);
        assertEquals(pending, migrator.migrate());
        new TransactionRunner(source).required(() -> { new JdbcAuditAdapter(source).append(event()); return null; });
        assertEquals(42L, jdbc.queryForObject("SELECT MAX(id) FROM audit_event", Long.class));
        var finalHistory = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        assertEquals(0, migrator.migrate());
        assertEquals(finalHistory, jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank"));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class));
    }

    @Test
    void auditIdentifiersMatchCaseAndTrailingSpacesExactly() {
        new SchemaMigrator(source).migrate();
        var runner = new TransactionRunner(source);
        for (String identifier : List.of("Case", "case", "Case ")) {
            runner.required(() -> {
                new JdbcAuditAdapter(source).append(new AuditEvent(identifier, "CONFIRM", identifier,
                        1, "SUCCESS", identifier, Instant.parse("2026-10-20T19:45:12Z")));
                return null;
            });
        }
        for (String column : List.of("actor_id", "resource_id", "trace_id")) {
            for (String identifier : List.of("Case", "case", "Case ")) {
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE " + column + "=?",
                        Integer.class, identifier), column + " must retain exact identifier equality");
            }
            assertEquals("utf8mb4_0900_bin:NO PAD", jdbc.queryForObject(
                    "SELECT CONCAT(c.collation_name, ':', co.pad_attribute) FROM information_schema.columns c "
                    + "JOIN information_schema.collations co ON c.collation_name=co.collation_name "
                    + "WHERE c.table_schema=DATABASE() AND c.table_name='audit_event' AND c.column_name=?",
                    String.class, column));
        }
        assertEquals(List.of(1L, 3L), jdbc.queryForList("SELECT id FROM audit_event WHERE resource_id IN (?, ?) "
                + "ORDER BY occurred_at, id", Long.class, "Case", "Case "));
    }

    @Test
    void auditNanosecondsTruncateToUtcMicrosecondsRegardlessOfJvmTimezone() {
        new SchemaMigrator(source).migrate();
        var previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            for (String time : List.of("2026-10-20T19:45:12.123456789Z", "2026-10-20T19:45:12.999999999Z",
                    "1969-12-31T23:59:59.999999999Z")) {
                new TransactionRunner(source).required(() -> {
                    new JdbcAuditAdapter(source).append(new AuditEvent("actor", "CONFIRM", "resource",
                            0, "SUCCESS", "trace", Instant.parse(time)));
                    return null;
                });
            }
            assertEquals(List.of("2026-10-20 19:45:12.123456", "2026-10-20 19:45:12.999999",
                    "1969-12-31 23:59:59.999999"), jdbc.queryForList(
                    "SELECT CAST(occurred_at AS CHAR) FROM audit_event ORDER BY id", String.class));
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"long-action", "non-ascii-action", "non-ascii-result"})
    void incompatibleLegacyMetadataFailsBeforeConversionEvenWithoutStrictMode(String invalid) {
        migrateFrozenBaseline();
        jdbc.update("INSERT INTO audit_event VALUES (77, 'actor', ?, 'resource', 0, ?, 'trace', '2026-10-20 19:45:12.123456')",
                invalid.equals("long-action") ? "A".repeat(65) : invalid.equals("non-ascii-action") ? "确认" : "CONFIRM",
                invalid.equals("non-ascii-result") ? "成功" : "SUCCESS");
        var originalRows = auditRows();
        var originalColumns = jdbc.queryForList("SHOW FULL COLUMNS FROM audit_event");
        var originalHistory = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        var migrator = Flyway.configure().dataSource(source).locations("classpath:db/migration").target("006")
                .initSql("SET sql_mode=''; SET NAMES utf8mb4; SET time_zone='+00:00'").cleanDisabled(true).load();
        try (var expectedLog = new ExpectedAuditMigrationFailure()) {
            var failure = assertThrows(FlywayException.class, migrator::migrate);
            assertTrue(failure.getMessage().contains("chk_audit_v006_metadata_compatible"), failure.getMessage());
            assertEquals(List.of("Migration of schema `ama_b01_fixture` to version \"006 - audit metadata\" "
                    + "failed! Please restore backups and roll back database and code!"), expectedLog.messages(),
                    "exactly the expected Flyway V006 rejection event is captured");
        }
        assertEquals(originalRows, auditRows(), "failure must preserve the incompatible source values");
        assertEquals(originalColumns, jdbc.queryForList("SHOW FULL COLUMNS FROM audit_event"),
                "preflight rejects values before any audit ALTER; multi-statement DDL is not claimed transactional");
        assertEquals(originalHistory, jdbc.queryForList("SELECT * FROM flyway_schema_history "
                + "WHERE version IN ('001','002','005') ORDER BY installed_rank"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='006' AND success=0", Integer.class));
        assertThrows(FlywayException.class, () -> new SchemaMigrator(source).migrate(),
                "a failed nontransactional migration needs explicit operator assessment, never automatic repair");
    }

    @Test
    void auditJoinsExternalSpringTransactionAndRollsBackWithIt() {
        new SchemaMigrator(source).migrate();
        new TransactionTemplate(new DataSourceTransactionManager(source)).execute(status -> {
            new JdbcAuditAdapter(source).append(event());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class));
            status.setRollbackOnly();
            return null;
        });
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class));
    }

    private void migrateFrozenBaseline() {
        assertEquals(3, Flyway.configure().dataSource(source).locations("classpath:db/migration")
                .target("005").cleanDisabled(true).load().migrate().migrationsExecuted);
    }

    private List<java.util.Map<String, Object>> auditRows() {
        return jdbc.queryForList("SELECT id, actor_id, action, resource_id, CAST(revision AS CHAR) AS revision, "
                + "result, trace_id, CAST(occurred_at AS CHAR) AS occurred_at FROM audit_event ORDER BY id");
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
