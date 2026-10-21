package ama.storage;

import ama.support.MysqlTestDatabase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real MySQL V009 contract. Expectations are a subset of independently reviewed
 * B00-1.2 MySQL evidence, never generated from the migration being tested.
 * Breaks caught: missing tables/columns/indexes/FKs/CHECKs, permissive invalid
 * rows, changed historical data/DDL, or duplicate immutable physical locations.
 * No registry, permissions, restart protocol, or backend qualification is claimed.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileSchemaMigrationIT {
    private static final List<String> TABLES = List.of(
            "file_cleanup", "file_object", "file_reference", "storage_backend", "upload_part", "upload_session");
    private static final List<String> LOWER = List.of("001", "002", "005", "006", "007", "008");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String AT = "2026-10-22 01:02:03.123456";
    private static final String HASH = "a".repeat(64);
    private final String meeting = UUID.randomUUID().toString();
    private final String owner = "file-schema-test-" + UUID.randomUUID();
    private final String backend = "test-" + UUID.randomUUID();
    private final String file = UUID.randomUUID().toString();
    private final String upload = UUID.randomUUID().toString();
    private javax.sql.DataSource source;
    private Connection connection;
    private JdbcTemplate jdbc;
    private Map<String, List<Map<String, Object>>> lowerRows;
    private Map<String, String> lowerDdl;
    private List<Map<String, Object>> lowerHistory;

    @BeforeAll void upgradeOwnedFixtureWithoutReset() throws Exception {
        // The helper path proves this checkout owns the named fixture. Docker
        // inspection independently verifies the expected label and loopback bind.
        String container = System.getenv("AMA_TEST_CONTAINER");
        assertNotNull(container, "explicit helper-owned fixture required");
        assertTrue(container.matches("ama-test-b01-[0-9a-f]{12}"));
        assertTrue(Files.isRegularFile(Path.of(".tools/mysql-fixture", container, "env.sh")),
                "fixture must be created in THIS worktree");
        Process inspection = new ProcessBuilder("docker", "inspect", container).start();
        JsonNode metadata = JSON.readTree(inspection.getInputStream()).get(0);
        assertEquals(0, inspection.waitFor());
        assertEquals("b01", metadata.at("/Config/Labels/ama.test-fixture").asText());
        JsonNode bindings = metadata.at("/NetworkSettings/Ports/3306~1tcp");
        assertEquals(1, bindings.size(), "exactly one helper-owned loopback binding required");
        for (JsonNode binding : bindings) {
            assertEquals("127.0.0.1", binding.get("HostIp").asText());
            assertEquals("jdbc:mysql://127.0.0.1:" + binding.get("HostPort").asText() + "/ama_b01_fixture",
                    System.getenv("AMA_TEST_DATABASE_URL"), "URL must belong to the inspected fixture");
        }
        source = MysqlTestDatabase.dataSource();
        var setup = new JdbcTemplate(source);
        assertTrue(setup.queryForObject("SELECT VERSION()", String.class).startsWith("8.4."));
        int history = setup.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='flyway_schema_history'", Integer.class);
        if (history == 0) {
            assertEquals(0, setup.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class),
                    "first use must be confirmed empty; never reset an existing schema");
            migrate("008");
        } else {
            assertEquals(0, setup.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=0", Integer.class),
                    "preserve any failed migration; no reset/repair");
            List<String> versions = setup.queryForList("SELECT version FROM flyway_schema_history ORDER BY installed_rank", String.class);
            assertTrue(versions.equals(LOWER) || versions.equals(List.of("001","002","005","006","007","008","009")),
                    "only known successful owned lower/full history may be reused: " + versions);
        }
        // Seed only test data AFTER all lower migrations, before the upgrade.
        setup.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',?)", owner, "结构测试身份", AT);
        setup.update("INSERT INTO meeting(id,owner_id,stage,created_at,updated_at) VALUES (?,?,'ENTRY',?,?)", meeting, owner, AT, AT);
        setup.update("INSERT INTO audit_event(actor_id,action,resource_id,revision,result,trace_id,occurred_at) VALUES (?,'FILE_SCHEMA_TEST',?,0,'SUCCESS',?,?)",
                owner, meeting, UUID.randomUUID().toString(), AT);
        lowerRows = new TreeMap<>();
        lowerDdl = new TreeMap<>();
        lowerHistory = setup.queryForList("SELECT * FROM flyway_schema_history WHERE version<>'009' ORDER BY installed_rank");
        for (String table : setup.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name<>'flyway_schema_history' ORDER BY table_name", String.class)) {
            if (!TABLES.contains(table)) {
                lowerRows.put(table, setup.queryForList("SELECT * FROM `" + table + "`"));
                lowerDdl.put(table, setup.queryForMap("SHOW CREATE TABLE `" + table + "`").get("Create Table").toString());
            }
        }
        // RED is explicitly run with -Dama.file.schema.target=008 against the
        // reviewed lower set. Default009 is allowed only after root's SHA ACK.
        migrate(System.getProperty("ama.file.schema.target", "009"));
        connection = source.getConnection();
        connection.setAutoCommit(false);
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    }

    @AfterEach void rollbackTestRows() throws Exception {
        if (connection != null) connection.rollback();
    }

    @AfterAll void closeConnection() throws Exception {
        if (connection != null) connection.close();
    }

    private int migrate(String target) {
        return Flyway.configure().dataSource(source).locations("classpath:db/migration")
                .target(target).initSql("SET NAMES utf8mb4; SET time_zone='+00:00'")
                .cleanDisabled(true).load().migrate().migrationsExecuted;
    }

    @Test void sixTablesMatchIndependentAdoptedMysqlMetadata() throws Exception {
        assertEquals(TABLES, jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('storage_backend','file_object','file_reference','upload_session','upload_part','file_cleanup') ORDER BY table_name", String.class),
                "V009 must create all six file tables");
        JsonNode expected;
        try (var input = getClass().getResourceAsStream("/ama/storage/file-schema-b00-1.2-metadata.json")) {
            assertNotNull(input);
            expected = JSON.readTree(input).get("metadata");
        }
        Map<String, String> queries = new LinkedHashMap<>();
        queries.put("tables", "SELECT JSON_OBJECT('table',TABLE_NAME,'engine',ENGINE,'collation',TABLE_COLLATION) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME");
        queries.put("columns", "SELECT JSON_OBJECT('table',TABLE_NAME,'column',COLUMN_NAME,'ordinal',ORDINAL_POSITION,'type',COLUMN_TYPE,'nullable',IS_NULLABLE,'default',COLUMN_DEFAULT,'collation',COLLATION_NAME,'extra',EXTRA) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,ORDINAL_POSITION");
        queries.put("indexes", "SELECT JSON_OBJECT('table',TABLE_NAME,'name',INDEX_NAME,'unique',1-NON_UNIQUE,'seq',SEQ_IN_INDEX,'column',COLUMN_NAME,'direction',COLLATION,'prefix',SUB_PART,'type',INDEX_TYPE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX");
        queries.put("checks", "SELECT JSON_OBJECT('table',t.TABLE_NAME,'name',t.CONSTRAINT_NAME,'clause',c.CHECK_CLAUSE,'enforced',t.ENFORCED) FROM information_schema.TABLE_CONSTRAINTS t JOIN information_schema.CHECK_CONSTRAINTS c ON c.CONSTRAINT_SCHEMA=t.CONSTRAINT_SCHEMA AND c.CONSTRAINT_NAME=t.CONSTRAINT_NAME WHERE t.CONSTRAINT_SCHEMA=DATABASE() AND t.CONSTRAINT_TYPE='CHECK' ORDER BY t.TABLE_NAME,t.CONSTRAINT_NAME");
        queries.put("foreign_keys", "SELECT JSON_OBJECT('table',k.TABLE_NAME,'name',k.CONSTRAINT_NAME,'column',k.COLUMN_NAME,'ordinal',k.ORDINAL_POSITION,'target',k.REFERENCED_TABLE_NAME,'remote',k.REFERENCED_COLUMN_NAME,'delete',r.DELETE_RULE,'update',r.UPDATE_RULE) FROM information_schema.KEY_COLUMN_USAGE k JOIN information_schema.REFERENTIAL_CONSTRAINTS r ON r.CONSTRAINT_SCHEMA=k.CONSTRAINT_SCHEMA AND r.CONSTRAINT_NAME=k.CONSTRAINT_NAME WHERE k.CONSTRAINT_SCHEMA=DATABASE() AND k.REFERENCED_TABLE_NAME IS NOT NULL ORDER BY k.TABLE_NAME,k.CONSTRAINT_NAME,k.ORDINAL_POSITION");
        for (var query : queries.entrySet()) {
            List<JsonNode> actual = new ArrayList<>();
            for (String row : jdbc.queryForList(query.getValue(), String.class)) {
                JsonNode parsed = JSON.readTree(row);
                if (TABLES.contains(parsed.get("table").asText())) actual.add(parsed);
            }
            assertEquals(expected.get(query.getKey()), JSON.valueToTree(actual), query.getKey());
        }
    }

    @Test void orderedUpgradePreservesEveryLowerTableAndRowAndRerunsZero() throws Exception {
        assertEquals(List.of("001","002","005","006","007","008","009"),
                jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=0", Integer.class));
        assertEquals(lowerHistory, jdbc.queryForList("SELECT * FROM flyway_schema_history WHERE version<>'009' ORDER BY installed_rank"),
                "frozen lower checksums and every history field must remain unchanged");
        for (String table : lowerRows.keySet()) {
            assertEquals(lowerDdl.get(table), jdbc.queryForMap("SHOW CREATE TABLE `" + table + "`").get("Create Table"), table + " DDL");
            assertEquals(lowerRows.get(table), jdbc.queryForList("SELECT * FROM `" + table + "`"), table + " data");
        }
        assertEquals(0, migrate("009"));
        Map<String, String> hashes = Map.of(
                "V001__foundation.sql", "1726d9db3eb45d0fe86314dd41ad52d5f2332e3f16995cc246f3c3c532208335",
                "V002__identity_acl.sql", "68b0d478504a554344cb12dfa8ef79c62da5c6e3455317791762fca25e70f7d0",
                "V005__tasks_outbox.sql", "55cfdc4aabf169c82f30303ac4d03dce7f24b3c6af5b4cc3ebd0089ae7e7dda7",
                "V006__audit_metadata.sql", "f36b7ab2e8d32cf5de6eadcd86524ab269dce98f7906b808eff1a0e39325178f",
                "V007__identity_sessions.sql", "859970b83fc5623b18340c2de793ea35429cc3c0da5cbd1365b4763a5a64780d");
        for (var entry : hashes.entrySet()) {
            try (var sql = getClass().getResourceAsStream("/db/migration/" + entry.getKey())) {
                assertNotNull(sql, entry.getKey());
                assertEquals(entry.getValue(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sql.readAllBytes())), entry.getKey());
            }
        }
        // Reviewed V008 and frozen V009 hashes are added only after their gates.
    }

    @Test void enumChecksRejectInvalidBackendFileUploadAndCleanupStates() {
        seed();
        expectSqlCode(3819, () -> jdbc.update("UPDATE storage_backend SET storage_type='invalid' WHERE id=?", backend));
        expectSqlCode(3819, () -> jdbc.update("UPDATE storage_backend SET status='invalid' WHERE id=?", backend));
        expectSqlCode(3819, () -> jdbc.update("UPDATE file_object SET storage_type='invalid' WHERE id=?", file));
        expectSqlCode(3819, () -> jdbc.update("UPDATE file_object SET state='invalid' WHERE id=?", file));
        expectSqlCode(3819, () -> jdbc.update("UPDATE upload_session SET state='invalid' WHERE id=?", upload));
        String cleanup = cleanup(file, backend, "cleanup");
        expectSqlCode(3819, () -> jdbc.update("UPDATE file_cleanup SET state='invalid' WHERE id=?", cleanup));
    }

    @Test void foreignKeysRejectMissingMeetingBackendFileAndUpload() {
        seed();
        expectSqlCode(1452, () -> jdbc.update("UPDATE file_object SET meeting_id='missing' WHERE id=?", file));
        expectSqlCode(1452, () -> jdbc.update("UPDATE file_object SET backend_id='missing' WHERE id=?", file));
        expectSqlCode(1452, () -> jdbc.update("INSERT INTO file_reference VALUES ('missing','UPLOAD_SESSION',?,'TEST_INPUT')", upload));
        expectSqlCode(1452, () -> jdbc.update("UPDATE upload_session SET meeting_id='missing' WHERE id=?", upload));
        expectSqlCode(1452, () -> jdbc.update("UPDATE upload_session SET backend_id='missing' WHERE id=?", upload));
        expectSqlCode(1452, () -> jdbc.update("UPDATE upload_session SET completed_file_id='missing' WHERE id=?", upload));
        expectSqlCode(1452, () -> jdbc.update("INSERT INTO upload_part VALUES ('missing',1,1,?,NULL)", HASH));
        String cleanup = cleanup(file, backend, "cleanup");
        expectSqlCode(1452, () -> jdbc.update("UPDATE file_cleanup SET file_id='missing' WHERE id=?", cleanup));
        expectSqlCode(1452, () -> jdbc.update("UPDATE file_cleanup SET backend_id='missing' WHERE id=?", cleanup));
    }

    @Test void referencedParentsCannotBeDeletedOrRenamed() {
        seed();
        jdbc.update("INSERT INTO file_reference VALUES (?,'UPLOAD_SESSION',?,'TEST_INPUT')", file, upload);
        jdbc.update("INSERT INTO upload_part VALUES (?,1,1,?,NULL)", upload, HASH);
        jdbc.update("UPDATE upload_session SET completed_file_id=? WHERE id=?", file, upload);
        cleanup(file, backend, "cleanup");
        expectSqlCode(1451, () -> jdbc.update("DELETE FROM storage_backend WHERE id=?", backend));
        expectSqlCode(1451, () -> jdbc.update("UPDATE storage_backend SET id='renamed' WHERE id=?", backend));
        expectSqlCode(1451, () -> jdbc.update("DELETE FROM meeting WHERE id=?", meeting));
        expectSqlCode(1451, () -> jdbc.update("DELETE FROM file_object WHERE id=?", file));
        expectSqlCode(1451, () -> jdbc.update("DELETE FROM upload_session WHERE id=?", upload));
    }

    @Test void physicalLocationsAreUniqueAndCaseSensitiveInAllThreeLedgers() {
        seed();
        insertFile(UUID.randomUUID().toString(), "Object");
        insertFile(UUID.randomUUID().toString(), "object");
        expectSqlCode(1062, () -> insertFile(UUID.randomUUID().toString(), "Object"));
        insertUpload(UUID.randomUUID().toString(), "Target");
        insertUpload(UUID.randomUUID().toString(), "target");
        expectSqlCode(1062, () -> insertUpload(UUID.randomUUID().toString(), "Target"));
        cleanup(null, backend, "Cleanup");
        cleanup(null, backend, "cleanup");
        expectSqlCode(1062, () -> cleanup(null, backend, "Cleanup"));
    }

    @Test void partsAndReferenceKeysRejectDuplicatesAndInvalidPartNumbers() {
        seed();
        expectSqlCode(3819, () -> jdbc.update("INSERT INTO upload_part VALUES (?,0,1,?,NULL)", upload, HASH));
        jdbc.update("INSERT INTO upload_part VALUES (?,1,1,?,?)", upload, HASH, "server-receipt");
        expectSqlCode(1062, () -> jdbc.update("INSERT INTO upload_part VALUES (?,1,1,?,NULL)", upload, HASH));
        jdbc.update("INSERT INTO file_reference VALUES (?,'UPLOAD_SESSION',?,'TEST_INPUT')", file, upload);
        expectSqlCode(1062, () -> jdbc.update("INSERT INTO file_reference VALUES (?,'UPLOAD_SESSION',?,'TEST_INPUT')", file, upload));
        jdbc.update("INSERT INTO file_reference VALUES (?,'UPLOAD_SESSION',?,'TEST_OTHER')", file, upload);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM file_reference WHERE file_id=?", Integer.class, file));
    }

    @Test void namesPurposeAndOptionalProviderProvenanceRoundTripWithoutTruncation() {
        seed();
        String name = "中文引号'\"反斜杠\\换行\n".repeat(2000);
        jdbc.update("UPDATE upload_session SET display_name=?,purpose='TEST_INPUT',provider_upload_token='internal-session' WHERE id=?", name, upload);
        jdbc.update("UPDATE file_object SET display_name=?,provider_version_id=? WHERE id=?", name, "provider-版本", file);
        assertEquals(name, jdbc.queryForObject("SELECT display_name FROM upload_session WHERE id=?", String.class, upload));
        assertEquals("TEST_INPUT", jdbc.queryForObject("SELECT purpose FROM upload_session WHERE id=?", String.class, upload));
        assertEquals("internal-session", jdbc.queryForObject("SELECT provider_upload_token FROM upload_session WHERE id=?", String.class, upload));
        assertEquals(name, jdbc.queryForObject("SELECT display_name FROM file_object WHERE id=?", String.class, file));
        assertEquals("provider-版本", jdbc.queryForObject("SELECT provider_version_id FROM file_object WHERE id=?", String.class, file));
        jdbc.update("UPDATE file_object SET provider_version_id=NULL WHERE id=?", file);
        assertNull(jdbc.queryForObject("SELECT provider_version_id FROM file_object WHERE id=?", String.class, file));
        expectSqlCode(1048, () -> jdbc.update("UPDATE upload_session SET display_name=NULL WHERE id=?", upload));
        expectSqlCode(1048, () -> jdbc.update("UPDATE upload_session SET purpose=NULL WHERE id=?", upload));
    }

    private void seed() {
        jdbc.update("INSERT INTO storage_backend VALUES (?,'local','WRITABLE',1)", backend);
        insertFile(file, "file");
        insertUpload(upload, "upload");
    }

    private void insertFile(String id, String key) {
        jdbc.update("INSERT INTO file_object(id,meeting_id,backend_id,storage_type,namespace,object_key,display_name,size_bytes,sha256,state,created_at) VALUES (?,?,?,'local','test',?,'test-name',1,?,'AVAILABLE',?)",
                id, meeting, backend, key, HASH, AT);
    }

    private void insertUpload(String id, String key) {
        jdbc.update("INSERT INTO upload_session(id,meeting_id,backend_id,namespace,object_key,cancel_epoch,expected_size,state,created_at,updated_at,display_name,purpose) VALUES (?,?,?,'test',?,0,1,'OPEN',?,?,'test-name','TEST_INPUT')",
                id, meeting, backend, key, AT, AT);
    }

    private String cleanup(String fileId, String backendId, String key) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO file_cleanup(id,file_id,backend_id,namespace,object_key,state,not_before) VALUES (?,?,?,'test',?,'PENDING',?)", id, fileId, backendId, key, AT);
        return id;
    }

    private static void expectSqlCode(int code, Runnable operation) {
        RuntimeException failure = assertThrows(RuntimeException.class, operation::run);
        Throwable cause = failure;
        while (cause != null && !(cause instanceof SQLException)) cause = cause.getCause();
        assertInstanceOf(SQLException.class, cause, "must fail for the intended database constraint");
        assertEquals(code, ((SQLException) cause).getErrorCode());
    }
}
