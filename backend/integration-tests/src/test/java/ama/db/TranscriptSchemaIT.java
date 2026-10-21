package ama.db;

import ama.support.MysqlTestDatabase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.sql.Connection;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.dao.DataAccessException;
import static org.junit.jupiter.api.Assertions.*;

/**
 * B06 corrected schema group, historical target 011.1. Frozen V011 is preserved;
 * registered V011.1 supplies its missing adopted material backing index only.
 * Independently adopted metadata is never generated from either migration.
 * Own new fixture only, known histories, no reset/repair/downgrade. Physical row
 * tests roll back. Same-meeting/snapshot membership, immutability and B's
 * EMPTY/NULL restriction remain application obligations, not schema acceptance.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TranscriptSchemaIT {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> TABLES = List.of("input_batch", "input_batch_file", "speaker",
            "transcript_confirmation", "transcript_revision", "transcript_segment", "transcript_segment_mark",
            "transcription_input");
    private static final List<String> LOWER = List.of("001", "002", "005", "006", "007", "008", "009", "010");
    private static final List<String> ORIGINAL = List.of("001", "002", "005", "006", "007", "008", "009", "010", "011");
    private static final List<String> FULL = List.of("001", "002", "005", "006", "007", "008", "009", "010", "011", "011.1");
    private static final String AT = "2026-10-22 01:02:03.123456";
    private static final String HASH = "a".repeat(64);
    private static final String OWNER = "b06-schema-owner";
    private static final String MEETING = "b06-schema-meeting";
    private static final String FILE = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String BATCH = "b06-batch";
    private static final String INPUT = "b06-input";
    private static final String REVISION = "b06-revision";
    private static final String SPEAKER = "b06-speaker";
    private static final Map<String, String> LOWER_HASHES = Map.of(
            "V001__foundation.sql", "1726d9db3eb45d0fe86314dd41ad52d5f2332e3f16995cc246f3c3c532208335",
            "V002__identity_acl.sql", "68b0d478504a554344cb12dfa8ef79c62da5c6e3455317791762fca25e70f7d0",
            "V005__tasks_outbox.sql", "55cfdc4aabf169c82f30303ac4d03dce7f24b3c6af5b4cc3ebd0089ae7e7dda7",
            "V006__audit_metadata.sql", "f36b7ab2e8d32cf5de6eadcd86524ab269dce98f7906b808eff1a0e39325178f",
            "V007__identity_sessions.sql", "859970b83fc5623b18340c2de793ea35429cc3c0da5cbd1365b4763a5a64780d",
            "V008__meeting_schema.sql", "fa44424aa1ff32e049929d45865fe7133c2548fc9ea451f3350cd8984c118ce3",
            "V009__file_schema.sql", "79f048987282c82fd7a56d697a02e88c93fa45b3349bd4d43e840dca23ba437d",
            "V010__reliable_execution.sql", "03e89cc8bd58d5a6aa0d4ab031b8fc05a4f7d2450623df147cd84d05b68977db");
    private static final Map<String, Integer> LOWER_CHECKSUMS = Map.of(
            "001",1201435595,"002",1757300424,"005",-1999355348,"006",-1744360556,
            "007",157746768,"008",-1747387036,"009",-287948759,"010",-1187397378);
    private DataSource source;
    private Connection connection;
    private JdbcTemplate setup;
    private JdbcTemplate jdbc;
    private Path root;
    private Path evidence;
    private JsonNode lowerBefore;

    @BeforeAll
    void upgradeOnlyTheOwnedFixtureWithoutReset() throws Exception {
        root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("tools/dev/mysql_fixture.py"))) root = root.getParent();
        assertNotNull(root);
        evidence = root.resolve(".tools/transcript-schema-formal");
        Files.createDirectories(evidence);
        verifyOwnedFixture();
        verifyLowerHashes();
        source = MysqlTestDatabase.dataSource();
        setup = new JdbcTemplate(source);
        assertTrue(setup.queryForObject("SELECT VERSION()", String.class).startsWith("8.4."));
        assertEquals(0, setup.queryForObject("SELECT @@GLOBAL.innodb_use_native_aio", Integer.class));
        boolean empty = setup.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='flyway_schema_history'", Integer.class) == 0;
        if (empty) {
            assertEquals(0, setup.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Integer.class),
                    "only confirmed empty first use; never reset an existing fixture");
            writeEvidence("initial-empty.json", Map.of("table_count",0,"container",System.getenv("AMA_TEST_CONTAINER"),"native_aio",0));
            assertEquals(8, migrate("010"));
        }
        var versions = guardKnownHistory();
        // Validate the actual known history while V011 is still intentionally
        // absent during RED; this validates only, never migrates/downgrades.
        Flyway.configure().dataSource(source).locations("classpath:db/migration")
                .target(versions.getLast()).cleanDisabled(true).load().validate();
        if (versions.equals(LOWER) && setup.queryForObject("SELECT COUNT(*) FROM enterprise_identity WHERE enterprise_id=?", Integer.class, OWNER) == 0) seedLowerRows();
        JsonNode currentLower = lowerSnapshot();
        Path before = evidence.resolve("lower-before.json");
        if (Files.exists(before)) {
            lowerBefore = JSON.readTree(Files.readAllBytes(before));
            assertEquals(lowerBefore, currentLower, "preserved lower snapshot must match before any new DML");
        } else {
            assertEquals(LOWER, versions, "never invent a before-snapshot after V011 already exists");
            lowerBefore = currentLower;
            writeEvidence("lower-before.json", lowerBefore);
        }
        if (Boolean.getBoolean("ama.transcript.schema.red")) {
            assertEquals(LOWER, versions, "RED cannot downgrade an already-newer fixture");
        } else {
            var sql = root.resolve("backend/adapters/src/main/resources/db/migration/V011__transcript_schema.sql");
            assertTrue(Files.isRegularFile(sql));
            String hash = sha(Files.readAllBytes(sql));
            assertEquals("a926c8b5d9806082d248f38bc7c7dd2bdbc46dd319d34b787e02f86132096ccb",hash,"original frozen V011 is immutable");
            assertEquals(hash, System.getProperty("ama.transcript.schema.frozen-sha"),
                    "explicit root centrally acknowledged frozen SHA required before first formal apply");
            try (var input=getClass().getResourceAsStream("/db/migration/V011__transcript_schema.sql")) {
                assertNotNull(input,"frozen migration must be the actual classpath SQL");
                assertEquals(hash,sha(input.readAllBytes()),"source and actual Flyway resource bytes must match");
            }
            Path indexSql=root.resolve("backend/adapters/src/main/resources/db/migration/V011_1__transcript_material_index.sql");
            assertTrue(Files.isRegularFile(indexSql));
            String indexHash=sha(Files.readAllBytes(indexSql));
            assertEquals(indexHash,System.getProperty("ama.transcript.schema.index-frozen-sha"),"root central V011.1 SHA ACK required before apply");
            try(var input=getClass().getResourceAsStream("/db/migration/V011_1__transcript_material_index.sql")) {
                assertNotNull(input); assertEquals(indexHash,sha(input.readAllBytes()));
            }
            JsonNode indexBefore=null;
            if(versions.equals(ORIGINAL)) {
                indexBefore=fullSnapshot();
                Path retained=evidence.resolve("index-before.json");
                assertFalse(Files.exists(retained),"preserve original correction-before snapshot");
                writeEvidence("index-before.json",indexBefore);
            } else if(versions.equals(FULL)) {
                indexBefore=JSON.readTree(Files.readAllBytes(evidence.resolve("index-before.json")));
            }
            assertEquals(versions.equals(LOWER) ? 2 : versions.equals(ORIGINAL) ? 1 : 0,migrate("011.1"));
            assertEquals(FULL, guardKnownHistory());
            assertEquals(lowerBefore, lowerSnapshot(), "upgrade must preserve all lower DDL, rows, metadata, SQL bytes and history");
            writeEvidence("after-upgrade.json", fullSnapshot());
            if(indexBefore!=null) {
                assertOnlyAdoptedIndexWasAdded(indexBefore,fullSnapshot());
                writeEvidence("index-after.json",fullSnapshot());
            }
        }
        connection = source.getConnection();
        connection.setAutoCommit(false);
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    }

    @AfterEach void rollbackRows() throws Exception { if (connection != null) connection.rollback(); }
    @AfterAll void preserveFinalEvidenceAndClose() throws Exception {
        boolean setupCompleted=connection!=null;
        if (connection != null) {
            try { connection.rollback(); } finally { connection.close(); }
        }
        if(setup!=null) writeEvidence(!setupCompleted ? "after-setup-failure.json" :
                Boolean.getBoolean("ama.transcript.schema.red") ? "after-red.json" : "after-tests.json",fullSnapshot());
    }

    @Test
    void allEightAssignedTablesExist() {
        assertEquals(TABLES, jdbc.queryForList("SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() AND table_name IN ('input_batch','input_batch_file','speaker',"
                + "'transcript_confirmation','transcript_revision','transcript_segment','transcript_segment_mark',"
                + "'transcription_input') ORDER BY BINARY table_name", String.class),
                "V011 must add all eight adopted transcript tables; lower010 must fail this assertion");
    }

    @Test
    void allFiveActualMetadataGroupsEqualTheIndependentAdoptedOracle() throws Exception {
        JsonNode expected;
        try (var input = getClass().getResourceAsStream("/ama/db/transcript-schema-b00-1.2.json")) {
            assertNotNull(input, "independent B00-1.2 expected metadata required");
            expected = JSON.readTree(input).path("metadata");
        }
        for (var query : metadataQueries().entrySet()) {
            var actual = new ArrayList<JsonNode>();
            for (String row : jdbc.queryForList(query.getValue(), String.class)) {
                JsonNode parsed = JSON.readTree(row);
                if (TABLES.contains(parsed.path("table").asText())) actual.add(parsed);
            }
            assertEquals(expected.path(query.getKey()), JSON.valueToTree(actual),
                    query.getKey() + " exact independent source subset; only material FK deferred to V017");
        }
    }

    @Test
    void orderedUpgradePreservesAllLowerRowsDdlMetadataHistoryAndFrozenBytesAndRerunsZero() throws Exception {
        assertEquals(FULL, guardKnownHistory());
        assertEquals(lowerBefore, lowerSnapshot());
        var allTables = new TreeSet<String>();
        lowerBefore.path("ddl").fieldNames().forEachRemaining(allTables::add);
        allTables.addAll(TABLES);
        assertEquals(allTables, new TreeSet<>(setup.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()", String.class)));
        JsonNode before = fullSnapshot();
        assertEquals(0, migrate("011.1"));
        historicalFlyway().validate();
        assertEquals(before, fullSnapshot(), "rerun zero must retain every DDL, row, metadata and history field");
        verifyLowerHashes();
        writeEvidence("rerun-zero.json", before);
    }

    @Test
    void productionLatestMigratorKeepsTheQualifiedSchemaAndRowsUnchanged() throws Exception {
        JsonNode before=fullSnapshot();
        assertEquals(0,new SchemaMigrator(source).migrate(),"normal Foundation migrator must remain latest and rerun zero");
        assertEquals(FULL,guardKnownHistory());
        assertEquals(before,fullSnapshot());
        writeEvidence("production-latest-rerun-zero.json",before);
    }

    @Test
    void checksEnforceEveryVocabularyAndAcceptTheirAdoptedValues() {
        seedTranscript();
        for (String assignment : List.of("kind='transcript'", "kind='UNKNOWN'")) error(3819,"UPDATE input_batch SET " + assignment);
        for (String value : List.of("TRANSCRIPT","MEDIA")) jdbc.update("UPDATE input_batch SET kind=?", value);
        for (String value : List.of("RAW","ENHANCED","WORKING","CANDIDATE")) jdbc.update("UPDATE transcript_revision SET kind=?", value);
        error(3819,"UPDATE transcript_revision SET kind='raw'");
        error(3819,"UPDATE transcription_input SET kind='import'");
        error(3819,"UPDATE transcription_input SET material_mode='empty'");
        for (String value : List.of("ANONYMOUS","HUMAN","VOICE_CANDIDATE")) jdbc.update("UPDATE speaker SET identity_source=?", value);
        error(3819,"UPDATE speaker SET identity_source='anonymous'");
        error(3819,"INSERT INTO transcript_segment_mark VALUES (?, 'segment', 'key')", REVISION);
        error(3819,"INSERT INTO transcript_segment_mark VALUES (?, 'segment', 'UNKNOWN')", REVISION);
    }

    @Test
    void processingInputChecksPreserveRequiredAndNullableVersionSemanticsAndMaterialDeferral() {
        seedTranscript();
        assertNull(jdbc.queryForObject("SELECT model_config_version FROM transcription_input", String.class));
        assertNull(jdbc.queryForObject("SELECT prompt_version FROM transcription_input", String.class));
        error(3819,"UPDATE transcription_input SET material_snapshot_id='future-material'");
        error(3819,"UPDATE transcription_input SET material_mode='PINNED'");
        jdbc.update("UPDATE transcription_input SET material_mode='PINNED',material_snapshot_id='future-material'");
        assertEquals("future-material",jdbc.queryForObject("SELECT material_snapshot_id FROM transcription_input",String.class),
                "only material FK is deferred: schema permits PINNED, so application B must enforce EMPTY/NULL");
        jdbc.update("UPDATE transcription_input SET material_mode='EMPTY',material_snapshot_id=NULL");
        error(3819,"UPDATE transcription_input SET parser_version=NULL");
        error(3819,"UPDATE transcription_input SET kind='ASR',parser_version=NULL,model_config_version=NULL");
        jdbc.update("UPDATE transcription_input SET kind='ASR',parser_version=NULL,model_config_version='test-model'");
        assertNull(jdbc.queryForObject("SELECT parser_version FROM transcription_input",String.class));
        error(3819,"UPDATE transcription_input SET kind='ENHANCE',source_revision_id=NULL");
        jdbc.update("UPDATE transcription_input SET kind='ENHANCE',source_revision_id=?,parser_version=NULL,prompt_version=NULL",REVISION);
        error(3819,"UPDATE transcription_input SET model_config_version=NULL");
        error(3819,"UPDATE transcription_input SET manifest_schema_version=0");
        assertEquals(1,jdbc.queryForObject("SELECT manifest_schema_version FROM transcription_input",Integer.class));
    }

    @Test
    void plaintextRetainsNullableTimesParagraphAndSpeakerWhilePartialOrReversedTimesFail() {
        seedTranscript();
        var row=jdbc.queryForMap("SELECT source_paragraph,start_ms,end_ms,speaker_id FROM transcript_segment");
        for(Object value:row.values()) assertNull(value);
        error(3819,"UPDATE transcript_segment SET start_ms=0,end_ms=NULL");
        error(3819,"UPDATE transcript_segment SET start_ms=NULL,end_ms=0");
        error(3819,"UPDATE transcript_segment SET start_ms=2,end_ms=1");
        jdbc.update("UPDATE transcript_segment SET start_ms=0,end_ms=0,source_paragraph=1,speaker_id=?",SPEAKER);
        jdbc.update("UPDATE transcript_segment SET start_ms=1,end_ms=300");
        error(1048,"UPDATE transcript_segment SET source_file_id=NULL");
        error(1264,"UPDATE transcript_segment SET start_ms=-1");
    }

    @Test
    void everyDeployedForeignKeyRejectsOrphansIncludingTheCircularProvenanceLinks() {
        seedTranscript();
        var assignments = new LinkedHashMap<String,List<String>>();
        assignments.put("input_batch",List.of("meeting_id"));
        assignments.put("input_batch_file",List.of("batch_id","file_id"));
        assignments.put("transcription_input",List.of("meeting_id","batch_id","source_revision_id"));
        assignments.put("transcript_revision",List.of("meeting_id","batch_id","parent_id","processing_input_id","producer_task_id"));
        assignments.put("transcript_segment",List.of("revision_id","source_file_id","speaker_id"));
        assignments.put("transcript_confirmation",List.of("meeting_id","revision_id","confirmed_by"));
        assignments.put("speaker",List.of("meeting_id","enterprise_id","merged_into_id"));
        for(var entry:assignments.entrySet()) for(String column:entry.getValue())
            error(1452,"UPDATE " + entry.getKey() + " SET " + column + "='missing-parent'");
        error(1452,"INSERT INTO transcript_segment_mark VALUES (?, 'missing-segment', 'KEY')",REVISION);
        error(1452,"INSERT INTO transcript_segment_mark VALUES ('missing-revision', 'segment', 'KEY')");
        revision("second-revision",1);
        error(1452,"INSERT INTO transcript_segment_mark VALUES ('second-revision', 'segment', 'KEY')");
        jdbc.update("UPDATE transcription_input SET source_revision_id=?",REVISION);
        jdbc.update("UPDATE transcript_revision SET producer_task_id='b06-lower-task'");
        jdbc.update("UPDATE transcript_segment SET speaker_id=?",SPEAKER);
        for(String sql:List.of("DELETE FROM input_batch WHERE id='"+BATCH+"'",
                "DELETE FROM transcription_input WHERE id='"+INPUT+"'",
                "DELETE FROM transcript_revision WHERE id='"+REVISION+"'",
                "DELETE FROM speaker WHERE id='"+SPEAKER+"'",
                "DELETE FROM file_object WHERE id='"+FILE+"'",
                "DELETE FROM meeting WHERE id='"+MEETING+"'")) error(1451,sql);
    }

    @Test
    void historyBatchFileOrderSegmentOrderAndConfirmationUniquenessAreEnforced() {
        seedTranscript();
        error(1062,"INSERT INTO input_batch_file VALUES (?,1,?,?)",BATCH,FILE,HASH);
        error(1062,"INSERT INTO input_batch_file VALUES (?,2,?,?)",BATCH,FILE,HASH);
        error(3819,"UPDATE input_batch_file SET ordinal=0");
        error(1062,"INSERT INTO transcript_revision (id,meeting_id,revision,kind,batch_id,input_fingerprint,created_by,created_at,processing_input_id) VALUES ('duplicate',?,0,'RAW',?,?,?, ?,?)",MEETING,BATCH,HASH,OWNER,AT,INPUT);
        error(1062,"INSERT INTO transcript_segment (revision_id,segment_id,ordinal,body,source_file_id) VALUES (?,'other-segment',1,'body',?)",REVISION,FILE);
        error(1062,"INSERT INTO transcript_segment (revision_id,segment_id,ordinal,body,source_file_id) VALUES (?,'segment',2,'body',?)",REVISION,FILE);
        error(1062,"INSERT INTO transcript_confirmation VALUES ('duplicate',?,?,?, ?,?)",MEETING,REVISION,HASH,OWNER,AT);
        revision("second-revision",1);
        jdbc.update("INSERT INTO transcript_segment (revision_id,segment_id,ordinal,body,source_file_id) VALUES ('second-revision','segment',1,'stable ID in another revision',?)",FILE);
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM transcript_segment WHERE segment_id='segment'",Integer.class));
    }

    @Test
    void allFourMarksCoexistAndDuplicatesArePreventedPerCompositeSegment() {
        seedTranscript();
        for(String mark:List.of("KEY","CONCLUSION","DISPUTE","GENERATION_FOCUS")) {
            jdbc.update("INSERT INTO transcript_segment_mark VALUES (?,'segment',?)",REVISION,mark);
            error(1062,"INSERT INTO transcript_segment_mark VALUES (?,'segment',?)",REVISION,mark);
        }
        assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM transcript_segment_mark",Integer.class));
        error(1451,"DELETE FROM transcript_segment WHERE revision_id=? AND segment_id='segment'",REVISION);
        revision("second-revision",1);
        jdbc.update("INSERT INTO transcript_segment (revision_id,segment_id,ordinal,body,source_file_id) VALUES ('second-revision','segment',1,'body',?)",FILE);
        jdbc.update("INSERT INTO transcript_segment_mark VALUES ('second-revision','segment','KEY')");
        assertEquals(5,jdbc.queryForObject("SELECT COUNT(*) FROM transcript_segment_mark",Integer.class));
    }

    @Test
    void binarySourceReferencesAndNoPadHumanIdentityIdsRemainExactAndLongUnicodeSurvives() {
        seedTranscript();
        String upper=FILE.toUpperCase(java.util.Locale.ROOT);
        error(1452,"UPDATE transcript_segment SET source_file_id=?",upper);
        error(1452,"UPDATE input_batch_file SET file_id=?",upper);
        file(jdbc,upper,"uppercase-file");
        jdbc.update("INSERT INTO transcript_segment (revision_id,segment_id,ordinal,body,source_file_id) VALUES (?,'uppercase-segment',2,'body',?)",REVISION,upper);
        assertEquals(FILE,jdbc.queryForObject("SELECT source_file_id FROM transcript_segment WHERE segment_id='segment'",String.class));
        assertEquals(upper,jdbc.queryForObject("SELECT source_file_id FROM transcript_segment WHERE segment_id='uppercase-segment'",String.class));
        for(String identity:List.of("Case","case","Case ")) {
            identity(jdbc,identity);
            jdbc.update("UPDATE speaker SET enterprise_id=?",identity);
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM speaker WHERE enterprise_id=?",Integer.class,identity));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM speaker WHERE enterprise_id=?",Integer.class,identity+" "));
        }
        String text="转写内容😀𠮷 é\n".repeat(5000);
        jdbc.update("UPDATE transcript_segment SET body=? WHERE segment_id='segment'",text);
        jdbc.update("UPDATE speaker SET display_name=?",text);
        jdbc.update("UPDATE transcription_input SET config_manifest=?,processing_config_version=?",text,"处理配置😀".repeat(20));
        assertEquals(text,jdbc.queryForObject("SELECT body FROM transcript_segment WHERE segment_id='segment'",String.class));
        assertEquals(text,jdbc.queryForObject("SELECT display_name FROM speaker",String.class));
        assertEquals(text,jdbc.queryForObject("SELECT config_manifest FROM transcription_input",String.class));
    }

    private void seedTranscript() {
        jdbc.update("INSERT INTO input_batch VALUES (?,?,'TRANSCRIPT',?,0,?)",BATCH,MEETING,HASH,AT);
        jdbc.update("INSERT INTO input_batch_file VALUES (?,1,?,?)",BATCH,FILE,HASH);
        jdbc.update("INSERT INTO transcription_input (id,meeting_id,batch_id,kind,material_mode,processing_config_version,parser_version,manifest_schema_version,config_manifest,input_fingerprint,base_revision,cancel_epoch,created_at) VALUES (?,?,?,'IMPORT','EMPTY','test-config','test-parser',1,'test-manifest',?,0,0,?)",INPUT,MEETING,BATCH,HASH,AT);
        revision(REVISION,0);
        jdbc.update("INSERT INTO speaker VALUES (?,?,?,NULL,'ANONYMOUS',NULL,0)",SPEAKER,MEETING,"Speaker");
        jdbc.update("INSERT INTO transcript_segment (revision_id,segment_id,ordinal,body,source_file_id) VALUES (?,'segment',1,'Plaintext',?)",REVISION,FILE);
        jdbc.update("INSERT INTO transcript_confirmation VALUES ('b06-confirmation',?,?,?,?,?)",MEETING,REVISION,HASH,OWNER,AT);
    }

    private void revision(String id,int revision) {
        jdbc.update("INSERT INTO transcript_revision (id,meeting_id,revision,kind,batch_id,input_fingerprint,created_by,created_at,processing_input_id) VALUES (?,?,?,'RAW',?,?,?,?,?)",id,MEETING,revision,BATCH,HASH,OWNER,AT,INPUT);
    }

    private void seedLowerRows() {
        identity(setup,OWNER);
        setup.update("INSERT INTO identity_session(token_hash,enterprise_id,session_generation,csrf_token,issued_at,expires_at) VALUES (?,?,0,?,?,'2026-10-23')",HASH,OWNER,"c".repeat(43),AT);
        setup.update("INSERT INTO meeting (id,owner_id,stage,created_at,updated_at) VALUES (?,?,'ENTRY',?,?)",MEETING,OWNER,AT,AT);
        setup.update("INSERT INTO meeting_info_revision(id,meeting_id,revision,name,created_by,created_at) VALUES ('b06-lower-info',?,0,'lower-name',?,?)",MEETING,OWNER,AT);
        setup.update("INSERT INTO storage_backend VALUES ('b06-schema-backend','local','WRITABLE',0)");
        file(setup,FILE,"lower-file");
        setup.update("INSERT INTO audit_event(actor_id,action,resource_id,revision,result,trace_id,occurred_at) VALUES (?,'SCHEMA_PROBE',?,0,'SUCCESS','b06-lower-trace',?)",OWNER,MEETING,AT);
        setup.update("INSERT INTO task_subject VALUES ('MEETING',?,0,0,0)",MEETING);
        setup.update("INSERT INTO durable_task(id,task_type,pool,subject_kind,subject_id,producer_key,input_kind,input_id,input_fingerprint,business_version,cancel_epoch,priority,max_attempts,available_at,created_at) VALUES ('b06-lower-task','TEST_SCHEMA','TEST_SCHEMA','MEETING',?,?,'TEST_INFO','b06-lower-info',?,0,0,0,1,?,?)",MEETING,HASH,HASH,AT,AT);
    }

    private void identity(JdbcTemplate target,String id) {
        target.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',?)",id,id,AT);
    }

    private void file(JdbcTemplate target,String id,String key) {
        target.update("INSERT INTO file_object(id,meeting_id,backend_id,storage_type,namespace,object_key,display_name,size_bytes,sha256,state,created_at) VALUES (?,?,'b06-schema-backend','local','TEST_SCHEMA',?,'source',12,?,'AVAILABLE',?)",id,MEETING,key,HASH,AT);
    }

    private void error(int code,String sql,Object... args) {
        var failure=assertThrows(DataAccessException.class,()->jdbc.update(sql,args),sql);
        Throwable cause=failure;
        while(cause!=null && !(cause instanceof java.sql.SQLException)) cause=cause.getCause();
        assertNotNull(cause,failure.toString());
        assertEquals(code,((java.sql.SQLException)cause).getErrorCode(),sql+": "+failure);
    }

    private void verifyOwnedFixture() throws Exception {
        String name=System.getenv("AMA_TEST_CONTAINER");
        assertNotNull(name);
        assertTrue(name.matches("ama-test-b01-[0-9a-f]{12}"));
        assertTrue(Files.isRegularFile(root.resolve(".tools/mysql-fixture").resolve(name).resolve("env.sh")),"fixture must be created in this worktree");
        Path record=root.resolve(".tools/transcript-schema-review/fixture-identity.json");
        assertTrue(Files.isRegularFile(record),"new fixture's initial-empty capture required");
        JsonNode original=JSON.readTree(Files.readAllBytes(record));
        assertEquals(name,original.path("name").asText());
        assertEquals(0,original.path("initial_table_count").asInt(-1));
        String format="{\"id\":{{json .Id}},\"name\":{{json .Name}},\"image\":{{json .Config.Image}},\"label\":{{json (index .Config.Labels \"ama.test-fixture\")}},\"args\":{{json .Args}},\"ports\":{{json .NetworkSettings.Ports}}}";
        Process process=new ProcessBuilder("docker","inspect","--format",format,name).start();
        JsonNode current=JSON.readTree(process.getInputStream());
        assertEquals(0,process.waitFor());
        assertEquals(original.path("inspection"),current,"exact original fixture ID/arguments/ports must remain unchanged");
        assertEquals("b01",current.path("label").asText());
        assertEquals("mysql@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9",current.path("image").asText());
        JsonNode bindings=current.at("/ports/3306~1tcp");
        assertEquals(1,bindings.size());
        assertEquals("127.0.0.1",bindings.get(0).path("HostIp").asText());
        assertEquals("jdbc:mysql://127.0.0.1:"+bindings.get(0).path("HostPort").asText()+"/ama_b01_fixture",System.getenv("AMA_TEST_DATABASE_URL"));
        writeEvidence("owned-fixture.json",original);
    }

    private void verifyLowerHashes() throws Exception {
        for(var entry:LOWER_HASHES.entrySet()) {
            try(var input=getClass().getResourceAsStream("/db/migration/"+entry.getKey())) {
                assertNotNull(input,entry.getKey());
                assertEquals(entry.getValue(),sha(input.readAllBytes()),entry.getKey());
            }
        }
    }

    private List<String> guardKnownHistory() {
        assertEquals(0,setup.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success<>1 OR version IS NULL",Integer.class),"failed/unknown history: stop and preserve");
        var versions=setup.queryForList("SELECT version FROM flyway_schema_history ORDER BY installed_rank",String.class);
        assertTrue(versions.equals(LOWER)||versions.equals(ORIGINAL)||versions.equals(FULL),"unknown or incomplete/newer history: "+versions);
        for(var entry:LOWER_CHECKSUMS.entrySet()) assertEquals(entry.getValue(),setup.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version=?",Integer.class,entry.getKey()));
        if(!versions.equals(LOWER)) assertEquals(-286330771,setup.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version='011'",Integer.class));
        return versions;
    }

    private Flyway historicalFlyway() {
        return Flyway.configure().dataSource(source).locations("classpath:db/migration").target("011.1")
                .initSql("SET NAMES utf8mb4; SET time_zone='+00:00'").cleanDisabled(true).load();
    }

    private int migrate(String target) {
        return Flyway.configure().dataSource(source).locations("classpath:db/migration").target(target)
                .initSql("SET NAMES utf8mb4; SET time_zone='+00:00'").cleanDisabled(true).load().migrate().migrationsExecuted;
    }

    private JsonNode lowerSnapshot() throws Exception { return snapshot(false); }
    private JsonNode fullSnapshot() throws Exception { return snapshot(true); }

    private JsonNode snapshot(boolean includeTranscript) throws Exception {
        var ddl=new TreeMap<String,String>();
        var rows=new TreeMap<String,Object>();
        for(String table:setup.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY BINARY table_name",String.class)) {
            if(!includeTranscript && TABLES.contains(table)) continue;
            ddl.put(table,setup.queryForMap("SHOW CREATE TABLE `"+table+"`").get("Create Table").toString());
            String filter=table.equals("flyway_schema_history")&&!includeTranscript?" WHERE version NOT IN ('011','011.1')":"";
            List<String> keys=setup.queryForList("SELECT column_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=? AND index_name='PRIMARY' ORDER BY seq_in_index",String.class,table);
            String order=keys.isEmpty()?"":" ORDER BY "+keys.stream().map(k->"`"+k+"`").collect(java.util.stream.Collectors.joining(","));
            rows.put(table,setup.query("SELECT * FROM `"+table+"`"+filter+order,(rs,n)->{
                var values=new TreeMap<String,Object>();
                for(int col=1;col<=rs.getMetaData().getColumnCount();col++) {
                    Object value=rs.getObject(col);
                    if(value instanceof java.util.Date || value instanceof java.time.temporal.TemporalAccessor) value=value.toString();
                    if(value instanceof byte[] bytes) value=HexFormat.of().formatHex(bytes);
                    values.put(rs.getMetaData().getColumnLabel(col),value);
                }
                return values;
            }));
        }
        var metadata=new LinkedHashMap<String,Object>();
        for(var query:metadataQueries().entrySet()) {
            var group=new ArrayList<JsonNode>();
            for(String text:setup.queryForList(query.getValue(),String.class)) {
                JsonNode row=JSON.readTree(text);
                if(includeTranscript || !TABLES.contains(row.path("table").asText())) group.add(row);
            }
            metadata.put(query.getKey(),group);
        }
        var hashes=new TreeMap<String,String>();
        for(String name:LOWER_HASHES.keySet()) try(var input=getClass().getResourceAsStream("/db/migration/"+name)) {
            assertNotNull(input,name);
            hashes.put(name,sha(input.readAllBytes()));
        }
        var snapshot=new TreeMap<String,Object>(Map.of("ddl",ddl,"rows",rows,"metadata",metadata,"lower_sql_sha256",new TreeMap<>(hashes)));
        if(includeTranscript) {
            try(var input=getClass().getResourceAsStream("/db/migration/V011__transcript_schema.sql")) {
                if(input!=null) hashes.put("V011__transcript_schema.sql",sha(input.readAllBytes()));
            }
            try(var input=getClass().getResourceAsStream("/db/migration/V011_1__transcript_material_index.sql")) {
                if(input!=null) hashes.put("V011_1__transcript_material_index.sql",sha(input.readAllBytes()));
            }
            snapshot.put("migration_sql_sha256",hashes);
        }
        return JSON.readTree(JSON.writeValueAsBytes(snapshot));
    }

    private void writeEvidence(String name,Object value) throws Exception {
        Files.writeString(evidence.resolve(name),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value)+"\n");
    }

    private String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private void assertOnlyAdoptedIndexWasAdded(JsonNode before,JsonNode after) throws Exception {
        assertEquals(before.path("ddl").size(),after.path("ddl").size(),"correction creates no tables");
        var names=before.path("ddl").fieldNames();
        while(names.hasNext()) {
            String table=names.next();
            String actual=after.path("ddl").path(table).asText();
            if(table.equals("transcription_input")) actual=actual.replace("\n  KEY `fk_transcription_input_3` (`material_snapshot_id`),","");
            assertEquals(before.path("ddl").path(table).asText(),actual,table+" existing DDL must remain unchanged except the one named index");
            if(!table.equals("flyway_schema_history")) assertEquals(before.path("rows").path(table),after.path("rows").path(table),table+" rows");
        }
        JsonNode oldHistory=before.path("rows").path("flyway_schema_history");
        JsonNode history=after.path("rows").path("flyway_schema_history");
        assertEquals(oldHistory.size()+1,history.size());
        for(int n=0;n<oldHistory.size();n++) assertEquals(oldHistory.get(n),history.get(n),"full history prefix field preservation");
        assertEquals("011.1",history.get(history.size()-1).path("version").asText());
        for(String group:List.of("tables","columns","checks","foreign_keys"))
            assertEquals(before.path("metadata").path(group),after.path("metadata").path(group),group);
        var stripped=new ArrayList<JsonNode>();
        int added=0;
        JsonNode wanted;
        try(var input=getClass().getResourceAsStream("/ama/db/transcript-schema-b00-1.2.json")) {
            wanted=JSON.readTree(input).path("metadata").path("indexes");
        }
        for(JsonNode row:after.path("metadata").path("indexes")) {
            if(row.path("table").asText().equals("transcription_input") && row.path("name").asText().equals("fk_transcription_input_3")) {
                assertTrue(wanted.findValues("name").stream().anyMatch(n->n.asText().equals("fk_transcription_input_3")));
                boolean exact=false; for(JsonNode adopted:wanted) if(adopted.equals(row)) exact=true;
                assertTrue(exact,"added index row must exactly match independent adopted evidence");
                added++;
            } else stripped.add(row);
        }
        assertEquals(1,added);
        assertEquals(before.path("metadata").path("indexes"),JSON.valueToTree(stripped),"only the one missing adopted index row may be added");
    }

    private Map<String, String> metadataQueries() {
        var queries = new LinkedHashMap<String, String>();
        queries.put("tables", "SELECT JSON_OBJECT('table',TABLE_NAME,'engine',ENGINE,'collation',TABLE_COLLATION) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME");
        queries.put("columns", "SELECT JSON_OBJECT('table',TABLE_NAME,'column',COLUMN_NAME,'ordinal',ORDINAL_POSITION,'type',COLUMN_TYPE,'nullable',IS_NULLABLE,'default',COLUMN_DEFAULT,'collation',COLLATION_NAME,'extra',EXTRA) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,ORDINAL_POSITION");
        queries.put("indexes", "SELECT JSON_OBJECT('table',TABLE_NAME,'name',INDEX_NAME,'unique',1-NON_UNIQUE,'seq',SEQ_IN_INDEX,'column',COLUMN_NAME,'direction',COLLATION,'prefix',SUB_PART,'type',INDEX_TYPE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX");
        queries.put("checks", "SELECT JSON_OBJECT('table',t.TABLE_NAME,'name',t.CONSTRAINT_NAME,'clause',c.CHECK_CLAUSE,'enforced',t.ENFORCED) FROM information_schema.TABLE_CONSTRAINTS t JOIN information_schema.CHECK_CONSTRAINTS c ON c.CONSTRAINT_SCHEMA=t.CONSTRAINT_SCHEMA AND c.CONSTRAINT_NAME=t.CONSTRAINT_NAME WHERE t.CONSTRAINT_SCHEMA=DATABASE() AND t.CONSTRAINT_TYPE='CHECK' ORDER BY t.TABLE_NAME,t.CONSTRAINT_NAME");
        queries.put("foreign_keys", "SELECT JSON_OBJECT('table',k.TABLE_NAME,'name',k.CONSTRAINT_NAME,'column',k.COLUMN_NAME,'ordinal',k.ORDINAL_POSITION,'target',k.REFERENCED_TABLE_NAME,'remote',k.REFERENCED_COLUMN_NAME,'delete',r.DELETE_RULE,'update',r.UPDATE_RULE) FROM information_schema.KEY_COLUMN_USAGE k JOIN information_schema.REFERENTIAL_CONSTRAINTS r ON r.CONSTRAINT_SCHEMA=k.CONSTRAINT_SCHEMA AND r.CONSTRAINT_NAME=k.CONSTRAINT_NAME WHERE k.CONSTRAINT_SCHEMA=DATABASE() AND k.REFERENCED_TABLE_NAME IS NOT NULL ORDER BY k.TABLE_NAME,k.CONSTRAINT_NAME,k.ORDINAL_POSITION");
        return queries;
    }
}
