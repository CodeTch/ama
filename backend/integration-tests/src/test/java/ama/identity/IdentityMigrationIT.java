package ama.identity;

import ama.db.SchemaMigrator;
import ama.support.MysqlTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdentityMigrationIT {
    @Test void emptyLegacySetUpgradesInOrderAndUsesNoPadIds() {
        var source = MysqlTestDatabase.reset();
        new SchemaMigrator(source).migrate();
        var jdbc = new JdbcTemplate(source);
        assertEquals(java.util.List.of("001","002","005","006","007"), jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank", String.class));
        assertEquals(0, new SchemaMigrator(source).migrate());
        for (String id : java.util.List.of("Case", "case", "Case ")) {
            jdbc.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,'姓名','ACTIVE','2026-10-21')", id);
        }
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM enterprise_identity", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM enterprise_identity WHERE enterprise_id='Case '", Integer.class));
        jdbc.update("INSERT INTO department VALUES ('Dept','同名',NULL,'2026-10-21'),('dept ','同名',NULL,'2026-10-21')");
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM department", Integer.class));
        assertThrows(RuntimeException.class, () -> jdbc.update("UPDATE enterprise_identity SET department_id='missing' WHERE enterprise_id='Case'"));
        assertThrows(RuntimeException.class, () -> jdbc.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status) VALUES ('unverified','姓名','ACTIVE')"));
        String longName = "姓名".repeat(500);
        jdbc.update("UPDATE enterprise_identity SET display_name=? WHERE enterprise_id='Case'", longName);
        assertEquals(longName, jdbc.queryForObject("SELECT display_name FROM enterprise_identity WHERE enterprise_id='Case'", String.class));
        assertThrows(RuntimeException.class, () -> jdbc.update("UPDATE enterprise_identity SET session_generation=-1 WHERE enterprise_id='Case'"));
        assertThrows(RuntimeException.class, () -> jdbc.update("INSERT INTO role_assignment VALUES ('Case','INVALID','2026-10-21')"));
        assertThrows(RuntimeException.class, () -> jdbc.update("INSERT INTO identity_session(token_hash,enterprise_id,session_generation,csrf_token,issued_at,expires_at,revoked_at) VALUES (?, 'Case',0,?,'2026-10-22','2026-10-21',NULL)", "a".repeat(64), "b".repeat(43)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('meeting','roster_revision','meeting_access_grant')", Integer.class));
    }
}
