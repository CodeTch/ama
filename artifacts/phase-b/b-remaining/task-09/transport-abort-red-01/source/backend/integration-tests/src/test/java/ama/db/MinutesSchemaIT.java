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

/** Exact V012 schema prerequisite; new owned fixture only, no reset/repair.
 * Missing-table RED precedes V012 creation. Actual 1.3 oracle is independent.
 * Transactional DML proves physical constraints only; application semantics remain open.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MinutesSchemaIT {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> TABLES = List.of("action_item","action_suggestion","background_note","background_note_source","element_set","evidence_link","exclusion_revision","generation_candidate","generation_input","minutes_element","minutes_revision","minutes_template","result_review","result_review_evidence","sensitive_detection","sensitive_detection_source","sensitive_exclusion","source_locator","template_revision");
    private static final List<String> LOWER = List.of("001","002","005","006","007","008","009","010","011","011.1");
    private static final List<String> FULL = List.of("001","002","005","006","007","008","009","010","011","011.1","012");
    private static final String AT = "2026-10-22 01:02:03.123456";
    private static final String HASH = "a".repeat(64);
    private static final String OWNER = "b07-schema-owner";
    private static final String MEETING = "b07-schema-meeting";
    private static final String FILE = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String BATCH = "b07-batch";
    private static final String INPUT = "b07-input";
    private static final String REVISION = "b07-revision";
    private static final String SPEAKER = "b07-speaker";
    private static final Map<String, String> LOWER_HASHES = Map.of(
            "V001__foundation.sql", "1726d9db3eb45d0fe86314dd41ad52d5f2332e3f16995cc246f3c3c532208335",
            "V002__identity_acl.sql", "68b0d478504a554344cb12dfa8ef79c62da5c6e3455317791762fca25e70f7d0",
            "V005__tasks_outbox.sql", "55cfdc4aabf169c82f30303ac4d03dce7f24b3c6af5b4cc3ebd0089ae7e7dda7",
            "V006__audit_metadata.sql", "f36b7ab2e8d32cf5de6eadcd86524ab269dce98f7906b808eff1a0e39325178f",
            "V007__identity_sessions.sql", "859970b83fc5623b18340c2de793ea35429cc3c0da5cbd1365b4763a5a64780d",
            "V008__meeting_schema.sql", "fa44424aa1ff32e049929d45865fe7133c2548fc9ea451f3350cd8984c118ce3",
            "V009__file_schema.sql", "79f048987282c82fd7a56d697a02e88c93fa45b3349bd4d43e840dca23ba437d",
            "V010__reliable_execution.sql", "03e89cc8bd58d5a6aa0d4ab031b8fc05a4f7d2450623df147cd84d05b68977db",
            "V011__transcript_schema.sql", "a926c8b5d9806082d248f38bc7c7dd2bdbc46dd319d34b787e02f86132096ccb",
            "V011_1__transcript_material_index.sql", "9efc688e49ce4afc52f383929b91d70970905f04b4e7836bdb0533eac50a9713");
    private static final Map<String, Integer> LOWER_CHECKSUMS = Map.of(
            "001",1201435595,"002",1757300424,"005",-1999355348,"006",-1744360556,
            "007",157746768,"008",-1747387036,"009",-287948759,"010",-1187397378,"011",-286330771,"011.1",-570494170);
    private DataSource source;
    private Connection connection;
    private JdbcTemplate setup;
    private JdbcTemplate jdbc;
    private Path root;
    private Path evidence;
    private JsonNode lowerBefore;

    @BeforeAll void upgradeOnlyTheOwnedFixtureWithoutReset() throws Exception {
        root=Path.of("").toAbsolutePath();
        while(root!=null && !Files.isRegularFile(root.resolve("tools/dev/mysql_fixture.py"))) root=root.getParent();
        assertNotNull(root);
        evidence=root.resolve(".tools/generation-schema-formal");
        Files.createDirectories(evidence);
        verifyOwnedFixture();
        verifyLowerHashes();
        source=MysqlTestDatabase.dataSource();
        setup=new JdbcTemplate(source);
        assertTrue(setup.queryForObject("SELECT VERSION()",String.class).startsWith("8.4."));
        assertEquals(0,setup.queryForObject("SELECT @@GLOBAL.innodb_use_native_aio",Integer.class));
        boolean empty=setup.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='flyway_schema_history'",Integer.class)==0;
        if(empty) {
            assertEquals(0,setup.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()",Integer.class),"initially empty only; never reset an existing fixture");
            writeEvidence("initial-empty.json",Map.of("table_count",0,"container",System.getenv("AMA_TEST_CONTAINER"),"native_aio",0));
            assertEquals(10,migrate("011.1"));
        }
        var versions=guardKnownHistory();
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target(versions.getLast()).cleanDisabled(true).load().validate();
        if(versions.equals(LOWER) && setup.queryForObject("SELECT COUNT(*) FROM enterprise_identity WHERE enterprise_id=?",Integer.class,OWNER)==0) seedLowerRows();
        JsonNode currentLower=lowerSnapshot();
        Path before=evidence.resolve("lower-before.json");
        if(Files.exists(before)) {
            lowerBefore=JSON.readTree(Files.readAllBytes(before));
            assertEquals(lowerBefore,currentLower,"all lower rows/DDL/metadata/history must match before mutation");
        } else {
            assertEquals(LOWER,versions,"never invent a lower snapshot after application");
            lowerBefore=currentLower;
            writeEvidence("lower-before.json",lowerBefore);
        }
        if(Boolean.getBoolean("ama.minutes.schema.red")) {
            assertEquals(LOWER,versions,"RED cannot downgrade a newer fixture");
        } else {
            Path sql=root.resolve("backend/adapters/src/main/resources/db/migration/V012__minutes_evidence.sql");
            assertTrue(Files.isRegularFile(sql));
            String hash=sha(Files.readAllBytes(sql));
            assertEquals(hash,System.getProperty("ama.minutes.schema.frozen-sha"),"central complete-byte register commit and explicit ACK required before first application");
            JsonNode ack=JSON.readTree(Files.readAllBytes(root.resolve(".tools/generation-schema-review/root-ack.json")));
            assertEquals(hash,ack.path("frozen_sha256").asText());
            assertTrue(ack.path("register_commit").asText().matches("[0-9a-f]{40}"));
            assertFalse(ack.path("ack_utc").asText().isBlank());
            try(var in=getClass().getResourceAsStream("/db/migration/V012__minutes_evidence.sql")) {
                assertNotNull(in); assertEquals(hash,sha(in.readAllBytes()),"actual Flyway resource is frozen bytes");
            }
            writeEvidence("root-ack.json",ack);
            assertEquals(versions.equals(LOWER)?1:0,migrate("012"));
            assertEquals(FULL,guardKnownHistory());
            assertEquals(lowerBefore,lowerSnapshot(),"upgrade preserves every lower object, row, metadata field, checksum/history and source SHA");
            writeEvidence("after-upgrade.json",fullSnapshot());
        }
        connection=source.getConnection();
        connection.setAutoCommit(false);
        jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
    }

    @AfterEach void rollbackRows() throws Exception { if(connection!=null) connection.rollback(); }
    @AfterAll void preserveFinalEvidenceAndClose() throws Exception {
        boolean completed=connection!=null;
        if(connection!=null) try { connection.rollback(); } finally { connection.close(); }
        if(setup!=null) writeEvidence(!completed?"after-setup-failure.json":Boolean.getBoolean("ama.minutes.schema.red")?"after-red.json":"after-tests.json",fullSnapshot());
    }

    @Test void allNineteenAssignedTablesExist() {
        assertEquals(TABLES,jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ("+TABLES.stream().map(t->"'"+t+"'").collect(java.util.stream.Collectors.joining(","))+") ORDER BY BINARY table_name",String.class),"V012 must add all nineteen adopted generation tables; exact lower011.1 must fail here");
    }

    @Test void allFiveActualMetadataGroupsEqualTheIndependentAdoptedOracle() throws Exception {
        JsonNode expected;
        try(var in=getClass().getResourceAsStream("/ama/db/minutes-schema-b00-1.3.json")) {
            assertNotNull(in); expected=JSON.readTree(in).path("metadata");
        }
        for(var query:metadataQueries().entrySet()) {
            var actual=new ArrayList<JsonNode>();
            for(String row:jdbc.queryForList(query.getValue(),String.class)) {
                JsonNode parsed=JSON.readTree(row);
                if(TABLES.contains(parsed.path("table").asText())) actual.add(parsed);
            }
            assertEquals(expected.path(query.getKey()),JSON.valueToTree(actual),query.getKey()+" exact independent source; only two material FK constraints excluded, all indexes retained");
        }
    }

    @Test void upgradePreservesLowerStateAndPrivateAndProductionRerunsExecuteZero() throws Exception {
        assertEquals(FULL,guardKnownHistory());
        assertEquals(lowerBefore,lowerSnapshot());
        var all=new TreeSet<String>(); lowerBefore.path("ddl").fieldNames().forEachRemaining(all::add); all.addAll(TABLES);
        assertEquals(all,new TreeSet<>(setup.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()",String.class)));
        JsonNode before=fullSnapshot();
        assertEquals(0,migrate("012")); historicalFlyway().validate();
        assertEquals(before,fullSnapshot()); writeEvidence("private-target-rerun-zero.json",before);
        assertEquals(0,new SchemaMigrator(source).migrate());
        assertEquals(before,fullSnapshot()); writeEvidence("production-latest-rerun-zero.json",before);
        verifyLowerHashes();
    }

    @Test void sevenSubjectCategoriesAndAdoptedVocabulariesRemainExact() {
        seedGeneration();
        for(String category:List.of("OVERVIEW","DISCUSSION","CONCLUSION","ACTION","RISK","OPEN_QUESTION","DISAGREEMENT")) {
            jdbc.update("INSERT INTO minutes_element VALUES (?,?,?,2,'body','SUPPORTED')",category,"set",category);
        }
        assertEquals(8,jdbc.queryForObject("SELECT COUNT(*) FROM minutes_element",Integer.class));
        error(3819,"UPDATE minutes_element SET category='BACKGROUND' WHERE id='element'");
        error(3819,"UPDATE minutes_element SET category='overview' WHERE id='element'");
        for(String state:List.of("SUPPORTED","PARTIAL","UNSUPPORTED","NOT_EXTRACTED")) jdbc.update("UPDATE minutes_element SET support_state=? WHERE id='element'",state);
        error(3819,"UPDATE minutes_element SET support_state='supported'");
        for(String origin:List.of("HUMAN","LINKED","AI")) jdbc.update("UPDATE minutes_revision SET title_origin=?",origin);
        error(3819,"UPDATE minutes_revision SET title_origin='human'");
        for(String state:List.of("PENDING","ADOPTED","REJECTED","STALE")) jdbc.update("UPDATE generation_candidate SET state=?",state);
        error(3819,"UPDATE generation_candidate SET state='pending'");
        error(1062,"INSERT INTO minutes_element VALUES ('duplicate','set','ACTION',1,'body','SUPPORTED')");
        error(1062,"UPDATE element_set SET revision=0 WHERE id='other-set'");
    }

    @Test void templateBoundsAndGenerationScopeEnforceTheirAdoptedShapes() {
        seedGeneration();
        for(int count:List.of(1,8)) jdbc.update("UPDATE template_revision SET theme_count=?",count);
        error(3819,"UPDATE template_revision SET theme_count=0");
        error(3819,"UPDATE template_revision SET theme_count=9");
        error(3819,"UPDATE generation_input SET scope='full'");
        error(3819,"UPDATE generation_input SET scope='ELEMENT'");
        error(3819,"UPDATE generation_input SET target_element_id='element'");
        jdbc.update("UPDATE generation_input SET scope='ELEMENT',target_element_id='element'");
        jdbc.update("UPDATE generation_input SET scope='FULL',target_element_id=NULL");
        error(1264,"UPDATE generation_input SET base_revision=-1");
        // Material parent is intentionally unavailable until V017; accepted SQL is a boundary witness.
        jdbc.update("UPDATE generation_input SET material_snapshot_id='future-material'");
        assertEquals("future-material",jdbc.queryForObject("SELECT material_snapshot_id FROM generation_input",String.class));
    }

    @Test void allSevenLocatorKindsAcceptRequiredShapesAndRejectMissingOrInvalidBounds() {
        seedGeneration();
        jdbc.update("INSERT INTO source_locator (id,meeting_id,kind,transcript_revision_id,segment_id) VALUES ('transcript',?,'TRANSCRIPT',?,'segment')",MEETING,REVISION);
        jdbc.update("INSERT INTO source_locator (id,meeting_id,kind,file_id,start_ms,end_ms) VALUES ('audio',?,'AUDIO',?,0,0)",MEETING,FILE);
        for(String kind:List.of("PDF_PAGE","PPT_PAGE")) jdbc.update("INSERT INTO source_locator (id,meeting_id,kind,file_id,page_no) VALUES (?,?,?, ?,1)",kind,MEETING,kind,FILE);
        for(String kind:List.of("WORD_PARAGRAPH","TEXT_PARAGRAPH")) jdbc.update("INSERT INTO source_locator (id,meeting_id,kind,file_id,paragraph_no) VALUES (?,?,?, ?,1)",kind,MEETING,kind,FILE);
        jdbc.update("INSERT INTO source_locator (id,meeting_id,kind,material_revision_id) VALUES ('knowledge',?,'KNOWLEDGE_VERSION','future-version')",MEETING);
        error(3819,"UPDATE source_locator SET kind='transcript' WHERE id='transcript'");
        error(3819,"UPDATE source_locator SET segment_id=NULL WHERE id='transcript'");
        error(3819,"UPDATE source_locator SET start_ms=2,end_ms=1 WHERE id='audio'");
        error(3819,"UPDATE source_locator SET end_ms=NULL WHERE id='audio'");
        error(3819,"UPDATE source_locator SET page_no=0 WHERE id='PDF_PAGE'");
        error(3819,"UPDATE source_locator SET paragraph_no=0 WHERE id='WORD_PARAGRAPH'");
        error(3819,"UPDATE source_locator SET material_revision_id=NULL WHERE id='knowledge'");
        error(1264,"UPDATE source_locator SET start_ms=-1 WHERE id='audio'");
    }

    @Test void twoSensitivityLabelsCoexistAtOneLocatorAndMembershipsAreUnique() {
        seedGeneration();
        jdbc.update("INSERT INTO sensitive_exclusion VALUES ('exclusion','locator','POLITICAL_SENSITIVE',NULL)");
        jdbc.update("INSERT INTO sensitive_detection VALUES ('detection-two','set','generation','POLITICAL_SENSITIVE',1)");
        jdbc.update("INSERT INTO sensitive_detection_source VALUES ('detection-two','locator')");
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM sensitive_exclusion WHERE locator_id='locator'",Integer.class));
        error(1062,"INSERT INTO sensitive_exclusion VALUES ('exclusion','locator','INTERNAL_BUSINESS_DATA','duplicate')");
        error(1062,"INSERT INTO sensitive_detection_source VALUES ('detection','locator')");
        error(1062,"INSERT INTO sensitive_detection VALUES ('dup','set','generation','INTERNAL_BUSINESS_DATA',1)");
        error(3819,"UPDATE sensitive_exclusion SET category='OTHER'");
        error(3819,"UPDATE sensitive_detection SET category='internal_business_data'");
        error(3819,"UPDATE sensitive_detection SET ordinal=0");
        jdbc.update("DELETE FROM sensitive_exclusion WHERE category='INTERNAL_BUSINESS_DATA'");
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sensitive_exclusion",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM sensitive_detection",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM sensitive_detection_source",Integer.class));
    }

    @Test void backgroundAndRawActionSuggestionsRetainUnicodeAndRemainSeparateFromAcceptedFields() {
        seedGeneration();
        String body="会议背景😀𠮷 é\n".repeat(5000);
        jdbc.update("UPDATE background_note SET body=?",body);
        jdbc.update("UPDATE action_suggestion SET owner_mention=?,due_on='2026-12-31'",body);
        assertEquals(body,jdbc.queryForObject("SELECT body FROM background_note",String.class));
        assertEquals(body,jdbc.queryForObject("SELECT owner_mention FROM action_suggestion",String.class));
        assertNull(jdbc.queryForObject("SELECT owner_enterprise_id FROM action_item",String.class));
        assertNull(jdbc.queryForObject("SELECT due_on FROM action_item",String.class));
        error(1062,"INSERT INTO background_note_source VALUES ('background','locator')");
        error(1062,"INSERT INTO background_note VALUES ('dup','set','generation',1,'body')");
        error(3819,"UPDATE background_note SET ordinal=0");
        jdbc.update("UPDATE action_suggestion SET owner_mention=NULL,due_on=NULL");
        assertNull(jdbc.queryForObject("SELECT owner_mention FROM action_suggestion",String.class));
    }

    @Test void reviewTargetKindOutcomeAndHashChecksProtectExactPhysicalShapes() {
        seedGeneration();
        for(String outcome:List.of("SUPPORTED","PARTIAL","UNSUPPORTED")) jdbc.update("UPDATE result_review SET outcome=?",outcome);
        error(3819,"UPDATE result_review SET element_id=NULL");
        error(3819,"UPDATE result_review SET minutes_revision_id='minutes'");
        error(3819,"UPDATE result_review SET review_kind='source_support'");
        error(3819,"UPDATE result_review SET target_anchor=NULL");
        error(3819,"UPDATE result_review SET outcome='CONFIRMED'");
        error(3819,"UPDATE result_review SET review_no=0");
        error(3819,"UPDATE result_review SET target_hash=?","A".repeat(64));
        error(3819,"UPDATE result_review SET source_set_hash=?","g".repeat(64));
        error(3819,"UPDATE result_review SET source_set_hash='abc'");
        error(1048,"UPDATE result_review SET reviewed_by=NULL");
        error(1048,"UPDATE result_review SET target_hash=NULL");
        error(1048,"UPDATE result_review SET id=NULL");
        jdbc.update("UPDATE result_review SET review_kind='ACTION_FIELDS',target_anchor=NULL,outcome='CONFIRMED'");
        error(3819,"UPDATE result_review SET outcome='SUPPORTED'");
        error(3819,"UPDATE result_review SET target_anchor='anchor'");
        error(3819,"UPDATE result_review SET element_id=NULL,minutes_revision_id='minutes'");
        jdbc.update("UPDATE result_review SET review_kind='SOURCE_SUPPORT',element_id=NULL,minutes_revision_id='minutes',target_anchor='anchor',outcome='SUPPORTED'");
        error(1062,"INSERT INTO result_review_evidence VALUES ('review','evidence')");
    }

    @Test void everyDeployedForeignKeyRejectsMissingParentsAndReferencesRestrictDeletion() throws Exception {
        seedGeneration();
        JsonNode expected;
        try(var in=getClass().getResourceAsStream("/ama/db/minutes-schema-b00-1.3.json")) { expected=JSON.readTree(in); }
        var keys=new LinkedHashMap<String,List<String>>();
        for(JsonNode row:expected.path("metadata").path("foreign_keys")) {
            keys.computeIfAbsent(row.path("table").asText()+"/"+row.path("name").asText(),unused->new ArrayList<>()).add(row.path("column").asText());
        }
        for(var key:keys.entrySet()) {
            String table=key.getKey().split("/")[0];
            String assignments=key.getValue().stream().map(column->"`"+column+"`='missing-parent'").collect(java.util.stream.Collectors.joining(","));
            if(List.of("evidence_link","result_review").contains(table) && key.getValue().contains("minutes_revision_id")) assignments+=",element_id=NULL";
            error(1452,"UPDATE `"+table+"` SET "+assignments);
        }
        for(String table:List.of("evidence_link","result_review","source_locator","generation_input","minutes_template","element_set","transcript_revision","enterprise_identity")) error(1451,"DELETE FROM `"+table+"`");
    }

    @Test void individualReferencesDoNotProveApplicationAuthoritySameMeetingOrHashTruth() {
        seedGeneration();
        jdbc.update("INSERT INTO meeting(id,owner_id,stage,created_at,updated_at) VALUES ('other-meeting',?,'ENTRY',?,?)",OWNER,AT,AT);
        jdbc.update("UPDATE result_review SET meeting_id='other-meeting',reviewed_by='unknown-identity',target_hash=?","b".repeat(64));
        // Valid FKs and hash syntax allow this; these are accepted-boundary witnesses.
        assertEquals("unknown-identity",jdbc.queryForObject("SELECT reviewed_by FROM result_review",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM result_review r JOIN minutes_element e ON e.id=r.element_id JOIN element_set s ON s.id=e.set_id WHERE r.meeting_id=s.meeting_id",Integer.class));
        assertEquals("b".repeat(64),jdbc.queryForObject("SELECT target_hash FROM result_review",String.class));
        jdbc.update("INSERT INTO minutes_element VALUES ('other-element','other-set','ACTION',1,'other content','SUPPORTED')");
        jdbc.update("UPDATE evidence_link SET element_id='other-element'");
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM result_review r JOIN result_review_evidence re ON re.review_id=r.id JOIN evidence_link e ON e.id=re.evidence_id WHERE e.element_id=r.element_id",Integer.class));
        writeBoundaryWitness();
    }

    private void writeBoundaryWitness() {
        try { writeEvidence("application-boundaries.json",Map.of("raw_sql_accepted",List.of("unknown reviewer identity","cross-meeting target","different evidence target","syntactically valid but unchecked content hash"),"application_rejection_test",false)); }
        catch(Exception failure) { throw new IllegalStateException(failure); }
    }

    private void seedGeneration() {
        jdbc.update("INSERT INTO minutes_template VALUES ('template',?,'name','name',?,'template-revision',0,?)",OWNER,HASH,AT);
        jdbc.update("INSERT INTO template_revision VALUES ('template-revision','template',0,?,'name','theme',1,?)",OWNER,AT);
        jdbc.update("INSERT INTO element_set VALUES ('set',?,0,?)",MEETING,AT);
        jdbc.update("INSERT INTO element_set VALUES ('other-set',?,1,?)",MEETING,AT);
        jdbc.update("INSERT INTO exclusion_revision VALUES ('exclusion',?,0,?)",MEETING,AT);
        jdbc.update("INSERT INTO generation_input VALUES ('generation',?,'b07-confirmation',NULL,'template-revision','exclusion','set','prompt','model',0,?,'FULL',NULL)",MEETING,HASH);
        jdbc.update("INSERT INTO minutes_revision VALUES ('minutes',?,0,NULL,'title','AI','body','generation',?,?)",MEETING,OWNER,AT);
        jdbc.update("INSERT INTO minutes_element VALUES ('element','set','ACTION',1,'action','SUPPORTED')");
        jdbc.update("INSERT INTO action_item VALUES ('element',NULL,NULL,NULL)");
        jdbc.update("INSERT INTO source_locator (id,meeting_id,kind,transcript_revision_id,segment_id) VALUES ('locator',?,'TRANSCRIPT',?,'segment')",MEETING,REVISION);
        jdbc.update("INSERT INTO sensitive_exclusion VALUES ('exclusion','locator','INTERNAL_BUSINESS_DATA',NULL)");
        jdbc.update("INSERT INTO generation_candidate VALUES ('candidate',?,'generation','b07-lower-task','minutes','set',0,?,'PENDING',?,NULL)",MEETING,HASH,AT);
        jdbc.update("INSERT INTO evidence_link VALUES ('evidence','locator','element',NULL,'SUPPORTED','anchor')");
        jdbc.update("INSERT INTO sensitive_detection VALUES ('detection','set','generation','INTERNAL_BUSINESS_DATA',1)");
        jdbc.update("INSERT INTO sensitive_detection_source VALUES ('detection','locator')");
        jdbc.update("INSERT INTO background_note VALUES ('background','set','generation',1,'background')");
        jdbc.update("INSERT INTO background_note_source VALUES ('background','locator')");
        jdbc.update("INSERT INTO action_suggestion VALUES ('element','generation','unverified owner',NULL)");
        jdbc.update("INSERT INTO result_review VALUES ('review',?,1,'SOURCE_SUPPORT','element',NULL,'anchor',?,?,'test-profile','SUPPORTED',?,?)",MEETING,HASH,HASH,OWNER,AT);
        jdbc.update("INSERT INTO result_review_evidence VALUES ('review','evidence')");
    }
    private void seedLowerRows() {
        identity(setup,OWNER);
        setup.update("INSERT INTO identity_session(token_hash,enterprise_id,session_generation,csrf_token,issued_at,expires_at) VALUES (?,?,0,?,?,'2026-10-23')",HASH,OWNER,"c".repeat(43),AT);
        setup.update("INSERT INTO meeting (id,owner_id,stage,created_at,updated_at) VALUES (?,?,'ENTRY',?,?)",MEETING,OWNER,AT,AT);
        setup.update("INSERT INTO meeting_info_revision(id,meeting_id,revision,name,created_by,created_at) VALUES ('b07-lower-info',?,0,'lower-name',?,?)",MEETING,OWNER,AT);
        setup.update("INSERT INTO storage_backend VALUES ('b07-schema-backend','local','WRITABLE',0)");
        file(setup,FILE,"lower-file");
        setup.update("INSERT INTO audit_event(actor_id,action,resource_id,revision,result,trace_id,occurred_at) VALUES (?,'SCHEMA_PROBE',?,0,'SUCCESS','b07-lower-trace',?)",OWNER,MEETING,AT);
        setup.update("INSERT INTO task_subject VALUES ('MEETING',?,0,0,0)",MEETING);
        setup.update("INSERT INTO durable_task(id,task_type,pool,subject_kind,subject_id,producer_key,input_kind,input_id,input_fingerprint,business_version,cancel_epoch,priority,max_attempts,available_at,created_at) VALUES ('b07-lower-task','TEST_SCHEMA','TEST_SCHEMA','MEETING',?,?,'TEST_INFO','b07-lower-info',?,0,0,0,1,?,?)",MEETING,HASH,HASH,AT,AT);
        setup.update("INSERT INTO input_batch VALUES (?,?,'TRANSCRIPT',?,0,?)",BATCH,MEETING,HASH,AT);
        setup.update("INSERT INTO input_batch_file VALUES (?,1,?,?)",BATCH,FILE,HASH);
        setup.update("INSERT INTO transcription_input (id,meeting_id,batch_id,kind,material_mode,processing_config_version,parser_version,manifest_schema_version,config_manifest,input_fingerprint,base_revision,cancel_epoch,created_at) VALUES (?,?,?,'IMPORT','EMPTY','test-config','test-parser',1,'test-manifest',?,0,0,?)",INPUT,MEETING,BATCH,HASH,AT);
        setup.update("INSERT INTO transcript_revision (id,meeting_id,revision,kind,batch_id,input_fingerprint,created_by,created_at,processing_input_id) VALUES (?,?,0,'RAW',?,?,?,?,?)",REVISION,MEETING,BATCH,HASH,OWNER,AT,INPUT);
        setup.update("INSERT INTO speaker VALUES (?,?,?,NULL,'ANONYMOUS',NULL,0)",SPEAKER,MEETING,"Speaker");
        setup.update("INSERT INTO transcript_segment (revision_id,segment_id,ordinal,body,source_file_id) VALUES (?,'segment',1,'Plaintext',?)",REVISION,FILE);
        setup.update("INSERT INTO transcript_confirmation VALUES ('b07-confirmation',?,?,?,?,?)",MEETING,REVISION,HASH,OWNER,AT);
    }

    private void identity(JdbcTemplate target,String id) {
        target.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',?)",id,id,AT);
    }

    private void file(JdbcTemplate target,String id,String key) {
        target.update("INSERT INTO file_object(id,meeting_id,backend_id,storage_type,namespace,object_key,display_name,size_bytes,sha256,state,created_at) VALUES (?,?,'b07-schema-backend','local','TEST_SCHEMA',?,'source',12,?,'AVAILABLE',?)",id,MEETING,key,HASH,AT);
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
        Path record=root.resolve(".tools/generation-schema-review/fixture-identity.json");
        assertTrue(Files.isRegularFile(record),"new fixture's initial-empty capture required");
        JsonNode original=JSON.readTree(Files.readAllBytes(record));
        assertEquals(name,original.path("name").asText());
        assertEquals(0,original.path("initial_table_count").asInt(-1));
        String format="{\"id\":{{json .Id}},\"name\":{{json .Name}},\"image\":{{json .Config.Image}},\"image_id\":{{json .Image}},\"label\":{{json (index .Config.Labels \"ama.test-fixture\")}},\"args\":{{json .Args}},\"ports\":{{json .NetworkSettings.Ports}},\"mounts\":{{json .Mounts}},\"auto_remove\":{{json .HostConfig.AutoRemove}}}";
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
        assertTrue(versions.equals(LOWER)||versions.equals(FULL),"unknown or incomplete/newer history: "+versions);
        for(var entry:LOWER_CHECKSUMS.entrySet()) assertEquals(entry.getValue(),setup.queryForObject("SELECT checksum FROM flyway_schema_history WHERE version=?",Integer.class,entry.getKey()));
        return versions;
    }

    private Flyway historicalFlyway() {
        return Flyway.configure().dataSource(source).locations("classpath:db/migration").target("012")
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
            String filter=table.equals("flyway_schema_history")&&!includeTranscript?" WHERE version <> '012'":"";
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
            try(var input=getClass().getResourceAsStream("/db/migration/V012__minutes_evidence.sql")) {
                if(input!=null) hashes.put("V012__minutes_evidence.sql",sha(input.readAllBytes()));
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
