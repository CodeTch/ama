package ama.db;

import ama.support.MysqlTestDatabase;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Run once per NEW owned fixture with explicit mode; failed history is intentionally preserved. */
class MeetingSchemaLegacyAclIT {
    @Test
    void legacyAclStopsBeforePermanentDdlWithoutDestroyingFailureEvidence() {
        String mode = System.getenv("AMA_TEST_LEGACY_ACL_MODE");
        assertTrue(("draft".equals(mode) || "timestamped".equals(mode)), "explicit draft/timestamped mode requires a NEW separate fixture");
        boolean timestampPresent = mode.equals("timestamped");
        var source = MysqlTestDatabase.dataSource();
        var jdbc = new JdbcTemplate(source);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class),
                "negative fixture must be empty; preserve any existing failure evidence without reset/repair");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("007").cleanDisabled(true).load().migrate();
        // Deliberately no confirmation provenance; timestamp alone must never invent grants.
        jdbc.update("INSERT INTO enterprise_identity (enterprise_id,display_name,department,status,session_generation,verified_at) VALUES ('legacy','Legacy','legacy display only','ACTIVE',0,'2026-10-21 01:02:03.123456')");
        jdbc.update("INSERT INTO meeting_acl VALUES ('legacy-meeting','legacy','READ',?)", timestampPresent ? "2026-10-21 00:00:00" : null);
        var oldRows = jdbc.queryForList("SELECT * FROM meeting_acl");
        var oldTables = jdbc.queryForList("SHOW TABLES");
        var oldDefinitions = definitions(jdbc);
        var oldHistory = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        try (var expectedLog = new ExpectedLegacyAclRejection()) {
            var failure = assertThrows(FlywayException.class, () -> Flyway.configure().dataSource(source)
                    .locations("classpath:db/migration").target("008").cleanDisabled(true)
                    .initSql("SET sql_mode=''; SET NAMES utf8mb4; SET time_zone='+00:00'").load().migrate());
            assertTrue(failure.getMessage().contains("chk_meeting_v008_legacy_acl_empty"), failure.getMessage());
            assertEquals(1, expectedLog.count, "capture exactly the expected Flyway rejection, no unrelated errors");
        }
        assertEquals(oldRows, jdbc.queryForList("SELECT * FROM meeting_acl"));
        assertEquals(oldTables, jdbc.queryForList("SHOW TABLES"));
        assertEquals(oldDefinitions, definitions(jdbc), "refusal must precede all permanent DDL, including legacy ALTERs");
        assertEquals(oldHistory, jdbc.queryForList("SELECT * FROM flyway_schema_history WHERE version <> '008' ORDER BY installed_rank"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='008' AND success=0", Integer.class));
    }

    private java.util.Map<String, String> definitions(JdbcTemplate jdbc) {
        var result = new java.util.TreeMap<String, String>();
        for (String table : jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()", String.class)) {
            result.put(table, jdbc.queryForMap("SHOW CREATE TABLE `" + table.replace("`", "``") + "`").get("Create Table").toString());
        }
        return result;
    }

    private static final class ExpectedLegacyAclRejection extends ch.qos.logback.classic.turbo.TurboFilter implements AutoCloseable {
        private final ch.qos.logback.classic.LoggerContext context =
                (ch.qos.logback.classic.LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        private final Thread owner = Thread.currentThread();
        private int count;

        private ExpectedLegacyAclRejection() {
            start();
            context.addTurboFilter(this);
        }

        @Override
        public ch.qos.logback.core.spi.FilterReply decide(org.slf4j.Marker marker,
                ch.qos.logback.classic.Logger logger, ch.qos.logback.classic.Level level,
                String message, Object[] parameters, Throwable failure) {
            if (Thread.currentThread() == owner
                    && logger.getName().equals("org.flywaydb.core.internal.command.DbMigrate")
                    && level == ch.qos.logback.classic.Level.ERROR
                    && ("Migration of schema `ama_b01_fixture` to version \"008 - meeting schema\" "
                    + "failed! Please restore backups and roll back database and code!").equals(message)
                    && (parameters == null || parameters.length == 0) && failure == null) {
                count++;
                return ch.qos.logback.core.spi.FilterReply.DENY;
            }
            return ch.qos.logback.core.spi.FilterReply.NEUTRAL;
        }

        @Override
        public void close() {
            context.getTurboFilterList().remove(this);
            stop();
        }
    }
}
