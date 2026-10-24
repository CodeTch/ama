package ama.db;

import ama.support.MysqlTestDatabase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** V008 structure only: no meeting API, authorization switch or business-write acceptance. */
class MeetingSchemaIT {
    private static final List<String> TABLES = List.of("idempotency_record", "meeting", "meeting_access_grant",
            "meeting_info_revision", "meeting_person_search", "meeting_search_projection", "roster_member", "roster_revision");
    private static final String MEETING = "00000000-0000-0000-0000-000000000001";
    private static final String ROSTER = "00000000-0000-0000-0000-000000000002";
    private static final String MEMBER = "00000000-0000-0000-0000-000000000003";
    private static final ObjectMapper JSON = new ObjectMapper();
    private DataSource source;
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetOnlyOwnedDisposableFixture() {
        source = MysqlTestDatabase.reset();
        jdbc = new JdbcTemplate(source);
    }

    @Test
    void frozenInputsRetainRegisteredBytes() throws Exception {
        var hashes = Map.of(
                "V001__foundation.sql", "1726d9db3eb45d0fe86314dd41ad52d5f2332e3f16995cc246f3c3c532208335",
                "V002__identity_acl.sql", "68b0d478504a554344cb12dfa8ef79c62da5c6e3455317791762fca25e70f7d0",
                "V005__tasks_outbox.sql", "55cfdc4aabf169c82f30303ac4d03dce7f24b3c6af5b4cc3ebd0089ae7e7dda7",
                "V006__audit_metadata.sql", "f36b7ab2e8d32cf5de6eadcd86524ab269dce98f7906b808eff1a0e39325178f",
                "V007__identity_sessions.sql", "859970b83fc5623b18340c2de793ea35429cc3c0da5cbd1365b4763a5a64780d",
                "V008__meeting_schema.sql", "fa44424aa1ff32e049929d45865fe7133c2548fc9ea451f3350cd8984c118ce3");
        for (var entry : hashes.entrySet()) {
            try (var stream = getClass().getResourceAsStream("/db/migration/" + entry.getKey())) {
                assertNotNull(stream, entry.getKey());
                assertEquals(entry.getValue(), java.util.HexFormat.of().formatHex(
                        java.security.MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes())), entry.getKey());
            }
        }
    }

    @Test
    void lowerSetUpgradeAddsExactlyEightAdoptedTablesAndPreservesLegacyRowsAndHistory() throws Exception {
        lowerSet();
        jdbc.update("INSERT INTO audit_event VALUES (19,'actor','CONFIRM','legacy-resource',7,'SUCCESS','trace','2026-10-20 19:45:12.123456')");
        identity("existing-owner");
        jdbc.update("INSERT INTO identity_session (token_hash,enterprise_id,session_generation,csrf_token,issued_at,expires_at) VALUES (?,'existing-owner',0,?,'2026-10-21','2026-10-22')",
                "d".repeat(64), "e".repeat(43));
        var history = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        var audit = jdbc.queryForList("SELECT * FROM audit_event");
        var oldColumns = jdbc.queryForList("SELECT * FROM information_schema.columns WHERE table_schema=DATABASE() ORDER BY table_name,ordinal_position");
        var oldTables = jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()", String.class);
        var identityRows = jdbc.queryForList("SELECT * FROM enterprise_identity");
        var sessionRows = jdbc.queryForList("SELECT * FROM identity_session");

        assertEquals(1, migrate("008"), "only the assigned V008 remains after the canonical lower set");

        var added = new ArrayList<>(jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY BINARY table_name", String.class));
        added.removeAll(oldTables);
        assertEquals(TABLES, added, "V008 must add exactly the eight approved tables");
        assertEquals(history, jdbc.queryForList("SELECT * FROM flyway_schema_history WHERE version <> '008' ORDER BY installed_rank"));
        assertEquals(audit, jdbc.queryForList("SELECT * FROM audit_event"));
        assertEquals(identityRows, jdbc.queryForList("SELECT * FROM enterprise_identity"));
        assertEquals(sessionRows, jdbc.queryForList("SELECT * FROM identity_session"));
        assertEquals(oldColumns, jdbc.queryForList("SELECT * FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name NOT IN (" + placeholders() + ") ORDER BY table_name,ordinal_position", TABLES.toArray()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='008' AND success=1", Integer.class));
        assertEquals(List.of("001", "002", "005", "006", "007", "008"), jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank", String.class));
        assertAdoptedMetadata();
        var finalHistory = jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank");
        assertEquals(0, migrate("008"));
        assertEquals(finalHistory, jdbc.queryForList("SELECT * FROM flyway_schema_history ORDER BY installed_rank"));
    }

    @Test
    void draftRosterPersistsWithoutCreatingEffectiveGrantsOrSearchRows() {
        lowerSet();
        migrate("008");
        ownerAndMeeting();
        jdbc.update("INSERT INTO roster_revision (id,meeting_id,revision,state) VALUES (?,?,0,'DRAFT')", ROSTER, MEETING);
        jdbc.update("INSERT INTO roster_member VALUES (?,?,?,'Long human name 😀','ENTERPRISE')", ROSTER, MEMBER, "owner");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM roster_member", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM meeting_access_grant", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM meeting_person_search", Integer.class));
        assertNull(jdbc.queryForObject("SELECT actual_meeting_date FROM meeting", java.sql.Date.class));
    }

    @Test
    void revisionUniquenessMemberKindsAndConfirmationMetadataAreEnforcedByMysql() {
        lowerSet();
        migrate("008");
        ownerAndMeeting();
        jdbc.update("INSERT INTO roster_revision (id,meeting_id,revision,state) VALUES (?,?,0,'DRAFT')", ROSTER, MEETING);
        sqlError(3819, "INSERT INTO roster_revision VALUES ('bad',?,1,'CONFIRMED',NULL,NULL)", MEETING);
        sqlError(3819, "INSERT INTO roster_revision VALUES ('bad',?,1,'DRAFT','2026-10-21','owner')", MEETING);
        sqlError(3819, "INSERT INTO roster_revision VALUES ('bad',?,1,'draft',NULL,NULL)", MEETING);
        sqlError(1062, "INSERT INTO roster_revision (id,meeting_id,revision,state) VALUES ('duplicate',?,0,'DRAFT')", MEETING);
        sqlError(3819, "INSERT INTO roster_member VALUES (?,?,'owner','Name','EXTERNAL')", ROSTER, MEMBER);
        sqlError(3819, "INSERT INTO roster_member VALUES (?,?,NULL,'Name','ENTERPRISE')", ROSTER, MEMBER);
        jdbc.update("INSERT INTO roster_member VALUES (?,?,'owner','Name','ENTERPRISE')", ROSTER, MEMBER);
        sqlError(1062, "INSERT INTO roster_member VALUES (?,'other','owner','Name','ENTERPRISE')", ROSTER);
        jdbc.update("INSERT INTO roster_member VALUES (?,'external-1',NULL,'External','EXTERNAL')", ROSTER);
        jdbc.update("INSERT INTO roster_member VALUES (?,'external-2',NULL,'External','EXTERNAL')", ROSTER);
        String longName = "完整名称😀".repeat(2000);
        jdbc.update("INSERT INTO meeting_info_revision (id,meeting_id,revision,name,created_by,created_at) VALUES ('info',?,0,?,'owner','2026-10-21 01:02:03.123456')", MEETING, longName);
        assertEquals(longName, jdbc.queryForObject("SELECT name FROM meeting_info_revision", String.class));
        sqlError(1062, "INSERT INTO meeting_info_revision (id,meeting_id,revision,created_by,created_at) VALUES ('other',?,0,'owner','2026-10-21')", MEETING);
        assertNull(jdbc.queryForObject("SELECT actual_meeting_date FROM meeting_info_revision", java.sql.Date.class));
    }

    @Test
    void foreignKeysRejectOrphansCompositeMemberMismatchAndParentDeletion() {
        lowerSet();
        migrate("008");
        sqlError(1452, "INSERT INTO meeting (id,owner_id,stage,created_at,updated_at) VALUES (?,'missing','ENTRY','2026-10-21','2026-10-21')", MEETING);
        ownerAndMeeting();
        sqlError(1452, "INSERT INTO roster_revision (id,meeting_id,revision,state) VALUES (?,'missing',0,'DRAFT')", ROSTER);
        jdbc.update("INSERT INTO roster_revision VALUES (?,?,0,'CONFIRMED','2026-10-21','owner')", ROSTER, MEETING);
        jdbc.update("INSERT INTO roster_member VALUES (?,?,'owner','Name','ENTERPRISE')", ROSTER, MEMBER);
        jdbc.update("INSERT INTO roster_revision (id,meeting_id,revision,state) VALUES ('other-roster',?,1,'DRAFT')", MEETING);
        sqlError(1452, "INSERT INTO meeting_person_search VALUES (?,'other-roster',?,'Name')", MEETING, MEMBER);
        sqlError(1452, "INSERT INTO meeting_access_grant VALUES (?,'missing','READ',?,'2026-10-21')", MEETING, ROSTER);
        sqlError(3819, "INSERT INTO meeting_access_grant VALUES (?,'owner','EDIT',?,'2026-10-21')", MEETING, ROSTER);
        jdbc.update("INSERT INTO meeting_access_grant VALUES (?,'owner','READ',?,'2026-10-21')", MEETING, ROSTER);
        jdbc.update("INSERT INTO meeting_person_search VALUES (?,?,?,'Name')", MEETING, ROSTER, MEMBER);
        sqlError(1451, "DELETE FROM roster_member WHERE roster_id=?", ROSTER);
        sqlError(1451, "DELETE FROM meeting WHERE id=?", MEETING);
        sqlError(1451, "DELETE FROM enterprise_identity WHERE enterprise_id='owner'");
    }

    @Test
    void stateVocabulariesAndBinaryIdentifiersStayDistinctFromSearchMatching() {
        lowerSet();
        migrate("008");
        ownerAndMeeting();
        for (String assignment : List.of("stage='entry'", "lifecycle='RESTORED'", "governance_state='RESTORED'", "ever_published=2", "needs_generation=2", "materials_pending=2")) {
            sqlError(3819, "UPDATE meeting SET " + assignment);
        }
        jdbc.update("INSERT INTO meeting_search_projection (meeting_id,name_search,number_search,category_id,meeting_revision) VALUES (?,'Résumé Planning',?,'Category',0)", MEETING, MEETING);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM meeting_search_projection WHERE name_search LIKE '%planning%'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM meeting_search_projection WHERE name_search LIKE '%Resume%'", Integer.class));
        for (String category : List.of("category", "Category ")) {
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM meeting_search_projection WHERE category_id=?", Integer.class, category));
        }
        String scope = "a".repeat(64);
        jdbc.update("INSERT INTO idempotency_record VALUES (?,'Actor ','Resource ','CREATE',?,?,'SUCCEEDED',NULL,201,'2026-10-21')", scope, "b".repeat(64), "c".repeat(64));
        sqlError(1062, "INSERT INTO idempotency_record VALUES (?,'other','other','CREATE',?,?,'SUCCEEDED',NULL,201,'2026-10-21')", scope, "d".repeat(64), "e".repeat(64));
        sqlError(3819, "UPDATE idempotency_record SET state='PENDING'");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM idempotency_record WHERE actor_id='Actor' OR actor_id='actor '", Integer.class));
    }

    private void lowerSet() {
        migrate("007");
        assertEquals(List.of("001", "002", "005", "006", "007"), jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank", String.class));
    }

    private int migrate(String target) {
        return Flyway.configure().dataSource(source).locations("classpath:db/migration")
                .target(target).cleanDisabled(true).load().migrate().migrationsExecuted;
    }

    private void ownerAndMeeting() {
        identity("owner");
        jdbc.update("INSERT INTO meeting (id,owner_id,stage,created_at,updated_at) VALUES (?,'owner','ENTRY','2026-10-21 01:02:03.123456','2026-10-21 01:02:03.123456')", MEETING);
    }

    private void identity(String id) {
        jdbc.update("INSERT INTO enterprise_identity (enterprise_id,display_name,department,status,session_generation,verified_at) VALUES (?,?,'legacy display only','ACTIVE',0,'2026-10-21 01:02:03.123456')", id, id);
    }

    private void sqlError(int expected, String sql, Object... args) {
        var failure = assertThrows(DataAccessException.class, () -> jdbc.update(sql, args));
        Throwable cause = failure;
        while (cause != null && !(cause instanceof java.sql.SQLException)) cause = cause.getCause();
        assertNotNull(cause, failure.toString());
        assertEquals(expected, ((java.sql.SQLException) cause).getErrorCode(), failure.toString());
    }

    private String placeholders() {
        return String.join(",", java.util.Collections.nCopies(TABLES.size(), "?"));
    }

    private void assertAdoptedMetadata() throws Exception {
        // Independently captured, adopted B00-1.2 information_schema subset; never generated from V008.
        JsonNode expected = JSON.readTree(getClass().getResourceAsStream("/ama/db/meeting-schema-b00-1.2.json"));
        for (var entry : METADATA_QUERIES.entrySet()) {
            var actual = new ArrayList<JsonNode>();
            for (String row : jdbc.queryForList(entry.getValue(), String.class)) {
                JsonNode value = JSON.readTree(row);
                if (TABLES.contains(value.path("table").asText())) actual.add(value);
            }
            var wanted = new ArrayList<JsonNode>();
            expected.path(entry.getKey()).forEach(wanted::add);
            assertEquals(wanted.size(), actual.size(), entry.getKey() + " row count");
            assertEquals(new java.util.HashSet<>(wanted), new java.util.HashSet<>(actual),
                    entry.getKey() + " must equal adopted real metadata");
        }
    }

    private static final Map<String, String> METADATA_QUERIES = Map.of(
            "tables", "SELECT JSON_OBJECT('table',TABLE_NAME,'engine',ENGINE,'collation',TABLE_COLLATION) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()",
            "columns", "SELECT JSON_OBJECT('table',TABLE_NAME,'column',COLUMN_NAME,'ordinal',ORDINAL_POSITION,'type',COLUMN_TYPE,'nullable',IS_NULLABLE,'default',COLUMN_DEFAULT,'collation',COLLATION_NAME,'extra',EXTRA) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()",
            "indexes", "SELECT JSON_OBJECT('table',TABLE_NAME,'name',INDEX_NAME,'unique',1-NON_UNIQUE,'seq',SEQ_IN_INDEX,'column',COLUMN_NAME,'direction',COLLATION,'prefix',SUB_PART,'type',INDEX_TYPE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE()",
            "checks", "SELECT JSON_OBJECT('table',t.TABLE_NAME,'name',t.CONSTRAINT_NAME,'clause',c.CHECK_CLAUSE,'enforced',t.ENFORCED) FROM information_schema.TABLE_CONSTRAINTS t JOIN information_schema.CHECK_CONSTRAINTS c ON c.CONSTRAINT_SCHEMA=t.CONSTRAINT_SCHEMA AND c.CONSTRAINT_NAME=t.CONSTRAINT_NAME WHERE t.CONSTRAINT_SCHEMA=DATABASE() AND t.CONSTRAINT_TYPE='CHECK'",
            "foreign_keys", "SELECT JSON_OBJECT('table',k.TABLE_NAME,'name',k.CONSTRAINT_NAME,'column',k.COLUMN_NAME,'ordinal',k.ORDINAL_POSITION,'target',k.REFERENCED_TABLE_NAME,'remote',k.REFERENCED_COLUMN_NAME,'delete',r.DELETE_RULE,'update',r.UPDATE_RULE) FROM information_schema.KEY_COLUMN_USAGE k JOIN information_schema.REFERENTIAL_CONSTRAINTS r ON r.CONSTRAINT_SCHEMA=k.CONSTRAINT_SCHEMA AND r.CONSTRAINT_NAME=k.CONSTRAINT_NAME WHERE k.CONSTRAINT_SCHEMA=DATABASE() AND k.REFERENCED_TABLE_NAME IS NOT NULL");

}
