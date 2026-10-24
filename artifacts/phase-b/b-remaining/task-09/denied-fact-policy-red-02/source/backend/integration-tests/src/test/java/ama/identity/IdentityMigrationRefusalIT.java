package ama.identity;

import ama.db.SchemaMigrator;
import ama.support.MysqlTestDatabase;
import ama.support.ExpectedIdentityMigrationFailure;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Run once in its own confirmed-empty fixture and preserve the expected failed V007 history. */
class IdentityMigrationRefusalIT {
    @Test void unsafeLegacyIdentityRefusedBeforePermanentDdl() {
        var source = MysqlTestDatabase.dataSource();
        var jdbc = new JdbcTemplate(source);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class), "Refusal qualification requires a separate new empty owned fixture; never reset failed evidence");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("006").load().migrate();
        jdbc.update("INSERT INTO enterprise_identity VALUES ('Legacy ','同名','部门显示文本','ACTIVE',TRUE)");
        jdbc.update("INSERT INTO identity_session VALUES (?, 'Legacy ', ?, '2026-01-01', NULL)", "a".repeat(64), "b".repeat(43));
        jdbc.update("INSERT INTO meeting_acl VALUES ('unproven-meeting','Legacy ','READ','2025-01-01')");
        var identity = jdbc.queryForList("SELECT * FROM enterprise_identity");
        var sessions = jdbc.queryForList("SELECT * FROM identity_session");
        var acl = jdbc.queryForList("SELECT * FROM meeting_acl");
        var schemas = java.util.Map.of(
                "identity", jdbc.queryForObject("SHOW CREATE TABLE enterprise_identity", (r,n)->r.getString(2)),
                "session", jdbc.queryForObject("SHOW CREATE TABLE identity_session", (r,n)->r.getString(2)),
                "acl", jdbc.queryForObject("SHOW CREATE TABLE meeting_acl", (r,n)->r.getString(2)));
        try (var expected = new ExpectedIdentityMigrationFailure()) {
            var failure = assertThrows(RuntimeException.class, () -> new SchemaMigrator(source).migrate());
            assertTrue(failure.toString().contains("identity_upgrade_requires_authoritative_mapping"));
            assertEquals(1, expected.messages().size());
        }
        assertEquals(identity, jdbc.queryForList("SELECT * FROM enterprise_identity"));
        assertEquals(sessions, jdbc.queryForList("SELECT * FROM identity_session"));
        assertEquals(acl, jdbc.queryForList("SELECT * FROM meeting_acl"));
        assertEquals(schemas.get("identity"), jdbc.queryForObject("SHOW CREATE TABLE enterprise_identity", (r,n)->r.getString(2)));
        assertEquals(schemas.get("session"), jdbc.queryForObject("SHOW CREATE TABLE identity_session", (r,n)->r.getString(2)));
        assertEquals(schemas.get("acl"), jdbc.queryForObject("SHOW CREATE TABLE meeting_acl", (r,n)->r.getString(2)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('department','role_assignment')", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='007' AND success=0", Integer.class));
    }
}
