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

/** Exact V013 schema prerequisite; new owned fixture only, no reset/repair.
 * Missing-table RED precedes V013 creation. Actual 1.3 oracle is independent.
 * Transactional DML proves physical constraints only; application semantics remain open.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PublicationSchemaIT {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> TABLES = List.of("external_document_mapping","final_confirmation","final_result_review","publication","publication_attempt");
    private static final List<String> LOWER = List.of("001","002","005","006","007","008","009","010","011","011.1","012");
    private static final List<String> FULL = List.of("001","002","005","006","007","008","009","010","011","011.1","012","013");
    private static final String AT = "2026-10-22 01:02:03.123456";
    private static final String HASH = "a".repeat(64);
    private static final String OWNER = "b08-schema-owner";
    private static final String MEETING = "b08-schema-meeting";
    private static final String FILE = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String BATCH = "b08-batch";
    private static final String INPUT = "b08-input";
    private static final String REVISION = "b08-revision";
    private static final String SPEAKER = "b08-speaker";
    private static final Map<String, String> LOWER_HASHES = Map.ofEntries(
            Map.entry("V001__foundation.sql", "1726d9db3eb45d0fe86314dd41ad52d5f2332e3f16995cc246f3c3c532208335"),
            Map.entry("V002__identity_acl.sql", "68b0d478504a554344cb12dfa8ef79c62da5c6e3455317791762fca25e70f7d0"),
            Map.entry("V005__tasks_outbox.sql", "55cfdc4aabf169c82f30303ac4d03dce7f24b3c6af5b4cc3ebd0089ae7e7dda7"),
            Map.entry("V006__audit_metadata.sql", "f36b7ab2e8d32cf5de6eadcd86524ab269dce98f7906b808eff1a0e39325178f"),
            Map.entry("V007__identity_sessions.sql", "859970b83fc5623b18340c2de793ea35429cc3c0da5cbd1365b4763a5a64780d"),
            Map.entry("V008__meeting_schema.sql", "fa44424aa1ff32e049929d45865fe7133c2548fc9ea451f3350cd8984c118ce3"),
            Map.entry("V009__file_schema.sql", "79f048987282c82fd7a56d697a02e88c93fa45b3349bd4d43e840dca23ba437d"),
            Map.entry("V010__reliable_execution.sql", "03e89cc8bd58d5a6aa0d4ab031b8fc05a4f7d2450623df147cd84d05b68977db"),
            Map.entry("V011__transcript_schema.sql", "a926c8b5d9806082d248f38bc7c7dd2bdbc46dd319d34b787e02f86132096ccb"),
            Map.entry("V011_1__transcript_material_index.sql", "9efc688e49ce4afc52f383929b91d70970905f04b4e7836bdb0533eac50a9713"),
            Map.entry("V012__minutes_evidence.sql", "10742ca2075fa3430dedf692853e1c106bd4464eaed46d8c8c1aa07859878fb7"));
    private static final Map<String, Integer> LOWER_CHECKSUMS = Map.ofEntries(
            Map.entry("001",1201435595),Map.entry("002",1757300424),Map.entry("005",-1999355348),Map.entry("006",-1744360556),
            Map.entry("007",157746768),Map.entry("008",-1747387036),Map.entry("009",-287948759),Map.entry("010",-1187397378),Map.entry("011",-286330771),Map.entry("011.1",-570494170),Map.entry("012",2101765957));
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
        evidence=root.resolve(".tools/publication-schema-formal");
        Files.createDirectories(evidence);
        verifyOwnedFixture();
        verifyLowerHashes();
        source=MysqlTestDatabase.dataSource();
        setup=new JdbcTemplate(source);
        assertTrue(setup.queryForObject("SELECT VERSION()",String.class).equals("8.4.6"));
        assertEquals(0,setup.queryForObject("SELECT @@GLOBAL.innodb_use_native_aio",Integer.class));
        boolean empty=setup.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='flyway_schema_history'",Integer.class)==0;
        if(empty) {
            assertEquals(0,setup.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()",Integer.class),"initially empty only; never reset an existing fixture");
            writeEvidence("initial-empty.json",Map.of("table_count",0,"container",System.getenv("AMA_TEST_CONTAINER"),"native_aio",0));
            assertEquals(11,migrate("012"));
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
        if(Boolean.getBoolean("ama.publication.schema.red")) {
            assertEquals(LOWER,versions,"RED cannot downgrade a newer fixture");
        } else {
            Path sql=root.resolve("backend/adapters/src/main/resources/db/migration/V013__publications.sql");
            assertTrue(Files.isRegularFile(sql));
            String hash=sha(Files.readAllBytes(sql));
            assertEquals(hash,System.getProperty("ama.publication.schema.frozen-sha"),"central complete-byte register commit and explicit ACK required before first application");
            JsonNode ack=JSON.readTree(Files.readAllBytes(root.resolve(".tools/publication-schema-review/root-ack.json")));
            assertEquals(hash,ack.path("frozen_sha256").asText());
            assertTrue(ack.path("register_commit").asText().matches("[0-9a-f]{40}"));
            assertFalse(ack.path("ack_utc").asText().isBlank());
            JsonNode identity=JSON.readTree(Files.readAllBytes(root.resolve(".tools/publication-schema-review/fixture-identity.json")));
            assertEquals(identity.path("name").asText(),ack.path("authorized_fixture_name").asText());
            assertEquals(identity.path("inspection").path("id").asText(),ack.path("authorized_fixture_id").asText());
            assertEquals(JSON.valueToTree(LOWER),ack.path("authorized_lower_versions"));
            try(var in=getClass().getResourceAsStream("/db/migration/V013__publications.sql")) {
                assertNotNull(in); assertEquals(hash,sha(in.readAllBytes()),"actual Flyway resource is frozen bytes");
            }
            writeEvidence("root-ack.json",ack);
            assertEquals(versions.equals(LOWER)?1:0,migrate("013"));
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
        if(setup!=null) writeEvidence(!completed?"after-setup-failure.json":Boolean.getBoolean("ama.publication.schema.red")?"after-red.json":"after-tests.json",fullSnapshot());
    }

    @Test void allFiveAssignedTablesExist() {
        assertEquals(TABLES,jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ("+TABLES.stream().map(t->"'"+t+"'").collect(java.util.stream.Collectors.joining(","))+") ORDER BY BINARY table_name",String.class),"V013 must add all five adopted publication tables; exact lower012 must fail here");
    }


    @Test void allFiveActualMetadataGroupsEqualIndependentAdoptedOracle() throws Exception {
        JsonNode expected=oracle().path("metadata");
        for(var query:metadataQueries().entrySet()) {
            var actual=new ArrayList<JsonNode>();
            for(String row:jdbc.queryForList(query.getValue(),String.class)) {
                JsonNode parsed=JSON.readTree(row);
                if(TABLES.contains(parsed.path("table").asText())) actual.add(parsed);
            }
            assertEquals(expected.path(query.getKey()),JSON.valueToTree(actual),query.getKey()+": exact independently filtered B00-1.3 actual metadata");
        }
    }

    @Test void upgradePreservesLowerRowsDdlMetadataHistoryAndBothRerunsExecuteZero() throws Exception {
        assertEquals(FULL,guardKnownHistory());
        assertEquals(lowerBefore,lowerSnapshot());
        var all=new TreeSet<String>(); lowerBefore.path("ddl").fieldNames().forEachRemaining(all::add); all.addAll(TABLES);
        assertEquals(all,new TreeSet<>(setup.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()",String.class)));
        JsonNode before=fullSnapshot();
        assertEquals(0,migrate("013")); historicalFlyway().validate();
        assertEquals(before,fullSnapshot()); writeEvidence("private-target-rerun-zero.json",before);
        assertEquals(0,new SchemaMigrator(source).migrate());
        assertEquals(before,fullSnapshot()); writeEvidence("production-latest-rerun-zero.json",before);
        verifyLowerHashes();
    }

    @Test void finalConfirmationRequiresAllFrozenParentsAndUniqueMeetingRevision() {
        seedPublication();
        error(1062,"INSERT INTO final_confirmation SELECT 'duplicate',meeting_id,meeting_revision,info_revision_id,roster_revision_id,transcript_confirmation_id,minutes_revision_id,material_snapshot_id,element_set_id,template_revision_id,exclusion_revision_id,source_manifest_hash,input_fingerprint,confirmed_by,confirmed_at FROM final_confirmation");
        error(1264,"UPDATE final_confirmation SET meeting_revision=-1");
        for(String column:List.of("info_revision_id","roster_revision_id","transcript_confirmation_id","minutes_revision_id","element_set_id","template_revision_id","exclusion_revision_id","source_manifest_hash","input_fingerprint","confirmed_by","confirmed_at")) error(1048,"UPDATE final_confirmation SET `"+column+"`=NULL");
        assertNull(jdbc.queryForObject("SELECT material_snapshot_id FROM final_confirmation",String.class));
        // Deferred material FK preserves the physical column/index; app must forbid use until V017.
        jdbc.update("UPDATE final_confirmation SET material_snapshot_id='future-material'");
        assertEquals("future-material",jdbc.queryForObject("SELECT material_snapshot_id FROM final_confirmation",String.class));
    }

    @Test void publicationStatesAndOneRootPerMeetingAndBusinessKeyArePhysical() {
        seedPublication();
        for(String state:List.of("READY","RUNNING","RECONCILING","FAILED","SUCCEEDED")) jdbc.update("UPDATE publication SET state=?",state);
        for(String state:List.of("ready","UNKNOWN","WITHDRAWN")) error(3819,"UPDATE publication SET state=?",state);
        error(1264,"UPDATE publication SET revision=-1");
        error(1062,"INSERT INTO publication SELECT 'duplicate',meeting_id,confirmation_id,'other-key',library_id,folder_id,state,revision,created_at,published_at FROM publication");
        secondPublication();
        error(1062,"UPDATE publication SET business_key='business-key' WHERE id='publication-two'");
        jdbc.update("UPDATE publication SET business_key='BUSINESS-KEY' WHERE id='publication-two'");
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM publication",Integer.class));
        for(String column:List.of("confirmation_id","business_key","library_id","folder_id","state","created_at")) error(1048,"UPDATE publication SET `"+column+"`=NULL WHERE id='publication'");
    }

    @Test void attemptsKeepExactKindsOutcomesAndConcreteExecutionParents() {
        seedPublication();
        for(String kind:List.of("PUBLISH","RECONCILE","WITHDRAW","OBSOLETE")) jdbc.update("UPDATE publication_attempt SET kind=?",kind);
        for(String state:List.of("PENDING","SUCCESS","FAILED","UNKNOWN")) jdbc.update("UPDATE publication_attempt SET outcome=?",state);
        error(3819,"UPDATE publication_attempt SET kind='publish'");
        error(3819,"UPDATE publication_attempt SET kind='RETRY'");
        error(3819,"UPDATE publication_attempt SET outcome='SUCCEEDED'");
        error(3819,"UPDATE publication_attempt SET outcome='unknown'");
        for(String column:List.of("publication_id","confirmation_id","operation_id","execution_id","kind","outcome","created_at")) error(1048,"UPDATE publication_attempt SET `"+column+"`=NULL");
        assertEquals("execution",jdbc.queryForObject("SELECT a.execution_id FROM publication_attempt a JOIN external_execution e ON e.id=a.execution_id JOIN external_operation o ON o.id=a.operation_id WHERE e.operation_id=o.id",String.class));
    }

    @Test void externalMappingIsUniqueWithExactProviderAndDocumentIdsAndUnicodeUrl() {
        seedPublication(); secondPublication();
        error(1062,"INSERT INTO external_document_mapping SELECT publication_id,'other-provider','other-document',document_url,authority_state,verified_at FROM external_document_mapping");
        error(1062,"INSERT INTO external_document_mapping SELECT 'publication-two',provider_id,external_document_id,document_url,authority_state,verified_at FROM external_document_mapping");
        jdbc.update("INSERT INTO external_document_mapping VALUES ('publication-two','PROVIDER','document','https://example.invalid/second','ACTIVE',?)",AT);
        jdbc.update("DELETE FROM external_document_mapping WHERE publication_id='publication-two'");
        jdbc.update("INSERT INTO external_document_mapping VALUES ('publication-two','provider','DOCUMENT','https://example.invalid/second','ACTIVE',?)",AT);
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM external_document_mapping",Integer.class));
        for(String state:List.of("ACTIVE","WITHDRAWN","OBSOLETE","UNKNOWN")) jdbc.update("UPDATE external_document_mapping SET authority_state=?",state);
        error(3819,"UPDATE external_document_mapping SET authority_state='active'");
        error(3819,"UPDATE external_document_mapping SET authority_state='SUCCEEDED'");
        String url="https://example.invalid/会议😀𠮷?x=é&y=值".repeat(3000);
        jdbc.update("UPDATE external_document_mapping SET document_url=? WHERE publication_id='publication'",url);
        assertEquals(url,jdbc.queryForObject("SELECT document_url FROM external_document_mapping WHERE publication_id='publication'",String.class));
        for(String column:List.of("provider_id","external_document_id","document_url","authority_state","verified_at")) error(1048,"UPDATE external_document_mapping SET `"+column+"`=NULL WHERE publication_id='publication'");
    }

    @Test void frozenReviewMembershipHasBothPhysicalParentsUniqueJoinAndRestrict() {
        seedPublication();
        error(1452,"INSERT INTO final_result_review VALUES ('missing-confirmation','review')");
        error(1452,"INSERT INTO final_result_review VALUES ('final','missing-review')");
        error(1062,"INSERT INTO final_result_review VALUES ('final','review')");
        error(1451,"DELETE FROM result_review WHERE id='review'");
        error(1451,"DELETE FROM final_confirmation WHERE id='final'");
        assertEquals(List.of("review"),jdbc.queryForList("SELECT review_id FROM final_result_review WHERE confirmation_id='final'",String.class));
        jdbc.update("INSERT INTO result_review VALUES ('new-review',?,2,'SOURCE_SUPPORT','element',NULL,'anchor',?,?,'test-profile','SUPPORTED',?,?)",MEETING,HASH,HASH,OWNER,AT);
        assertEquals(List.of("review"),jdbc.queryForList("SELECT review_id FROM final_result_review WHERE confirmation_id='final'",String.class),"new assertions do not automatically become frozen membership");
        jdbc.update("DELETE FROM final_result_review WHERE confirmation_id='final'");
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM result_review WHERE id='review'",Integer.class));
    }

    @Test void everyDeployedFkRejectsMissingParentsAndReferencedRowsRestrictDelete() throws Exception {
        seedPublication();
        var keys=new LinkedHashMap<String,List<String>>();
        for(JsonNode row:oracle().path("metadata").path("foreign_keys")) keys.computeIfAbsent(row.path("table").asText()+"/"+row.path("name").asText(),unused->new ArrayList<>()).add(row.path("column").asText());
        assertEquals(18,keys.size());
        for(var key:keys.entrySet()) {
            String table=key.getKey().split("/")[0];
            String assignments=key.getValue().stream().map(column->"`"+column+"`='missing-parent'").collect(java.util.stream.Collectors.joining(","));
            error(1452,"UPDATE `"+table+"` SET "+assignments);
        }
        for(String table:List.of("meeting_info_revision","roster_revision","transcript_confirmation","minutes_revision","element_set","template_revision","exclusion_revision","enterprise_identity","publication","final_confirmation","external_operation","external_execution","result_review")) error(1451,"DELETE FROM `"+table+"`");
    }

    @Test void rawSqlBoundariesLeaveAuthoritySameMeetingHashesAndExecutionConsistencyToApplication() throws Exception {
        seedPublication(); secondPublication();
        jdbc.update("UPDATE final_confirmation SET meeting_id='meeting-two',confirmed_by=?,source_manifest_hash='unverified',input_fingerprint='unchecked' WHERE id='final'",OWNER);
        jdbc.update("UPDATE result_review SET meeting_id='meeting-two',reviewed_by='unknown-reviewer' WHERE id='review'");
        jdbc.update("UPDATE publication_attempt SET confirmation_id='final-two'");
        jdbc.update("INSERT INTO external_operation(id,operation_kind,business_key,request_hash,state,marked_at,next_check_at,subject_kind,subject_id,current_execution_no) VALUES ('other-operation','TEST_SCHEMA','different',?,'MARKED',?,?,'MEETING',?,1)",HASH,AT,AT,MEETING);
        jdbc.update("UPDATE publication_attempt SET operation_id='other-operation'");
        jdbc.update("UPDATE external_document_mapping SET document_url='javascript:unchecked',authority_state='ACTIVE'");
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM publication p JOIN final_confirmation f ON f.id=p.confirmation_id WHERE p.id='publication' AND p.meeting_id=f.meeting_id",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM publication_attempt a JOIN external_execution e ON e.id=a.execution_id WHERE a.operation_id=e.operation_id",Integer.class));
        assertEquals("unknown-reviewer",jdbc.queryForObject("SELECT reviewed_by FROM result_review WHERE id='review'",String.class));
        assertEquals("unverified",jdbc.queryForObject("SELECT source_manifest_hash FROM final_confirmation WHERE id='final'",String.class));
        writeEvidence("application-boundaries.json",Map.of("application_rejection_test",false,"raw_sql_accepted",List.of("cross-meeting frozen parent and publication reference","existing confirmer without proof of owner authority","unknown review identity","unchecked source manifest and input hash","attempt confirmation differs from publication confirmation","operation differs from execution parent operation","unvalidated URL and unverified provider authority"),"material_nonnull", "physically accepted with deferred fk_final_confirmation_9; Application B must forbid until V017"));
    }

    private JsonNode oracle() throws Exception {
        try(var in=getClass().getResourceAsStream("/ama/db/publication-schema-b00-1.3.json")) { assertNotNull(in); return JSON.readTree(in); }
    }

    private void seedPublication() {
        jdbc.update("INSERT INTO roster_revision VALUES ('roster',?,0,'CONFIRMED',?,?)",MEETING,AT,OWNER);
        jdbc.update("INSERT INTO external_operation(id,operation_kind,business_key,request_hash,state,marked_at,next_check_at,subject_kind,subject_id,current_execution_no) VALUES ('operation','TEST_SCHEMA','operation-key',?,'MARKED',?,?,'MEETING',?,1)",HASH,AT,AT,MEETING);
        jdbc.update("INSERT INTO external_execution(id,operation_id,execution_no,task_id,state,request_hash,request_manifest,created_at) VALUES ('execution','operation',1,'b08-lower-task','PREPARED',?,'synthetic manifest',?)",HASH,AT);
        finalConfirmation("final",MEETING,0);
        jdbc.update("INSERT INTO publication VALUES ('publication',?,'final','business-key','library','folder','READY',0,?,NULL)",MEETING,AT);
        jdbc.update("INSERT INTO publication_attempt VALUES ('attempt','publication','final','operation','PUBLISH','PENDING',?,NULL,'execution')",AT);
        jdbc.update("INSERT INTO external_document_mapping VALUES ('publication','provider','document','https://example.invalid/document','ACTIVE',?)",AT);
        jdbc.update("INSERT INTO final_result_review VALUES ('final','review')");
    }

    private void finalConfirmation(String id,String meeting,long revision) {
        jdbc.update("INSERT INTO final_confirmation VALUES (?,?,?,'b08-lower-info','roster','b08-confirmation','minutes',NULL,'set','template-revision','exclusion',?,?,?,?)",id,meeting,revision,HASH,HASH,OWNER,AT);
    }

    private void secondPublication() {
        jdbc.update("INSERT INTO meeting(id,owner_id,stage,created_at,updated_at) VALUES ('meeting-two',?,'ENTRY',?,?)",OWNER,AT,AT);
        finalConfirmation("final-two","meeting-two",1);
        jdbc.update("INSERT INTO publication VALUES ('publication-two','meeting-two','final-two','other-key','library','folder','READY',0,?,NULL)",AT);
    }

    private void seedGeneration() {
        setup.update("INSERT INTO minutes_template VALUES ('template',?,'name','name',?,'template-revision',0,?)",OWNER,HASH,AT);
        setup.update("INSERT INTO template_revision VALUES ('template-revision','template',0,?,'name','theme',1,?)",OWNER,AT);
        setup.update("INSERT INTO element_set VALUES ('set',?,0,?)",MEETING,AT);
        setup.update("INSERT INTO element_set VALUES ('other-set',?,1,?)",MEETING,AT);
        setup.update("INSERT INTO exclusion_revision VALUES ('exclusion',?,0,?)",MEETING,AT);
        setup.update("INSERT INTO generation_input VALUES ('generation',?,'b08-confirmation',NULL,'template-revision','exclusion','set','prompt','model',0,?,'FULL',NULL)",MEETING,HASH);
        setup.update("INSERT INTO minutes_revision VALUES ('minutes',?,0,NULL,'title','AI','body','generation',?,?)",MEETING,OWNER,AT);
        setup.update("INSERT INTO minutes_element VALUES ('element','set','ACTION',1,'action','SUPPORTED')");
        setup.update("INSERT INTO action_item VALUES ('element',NULL,NULL,NULL)");
        setup.update("INSERT INTO source_locator (id,meeting_id,kind,transcript_revision_id,segment_id) VALUES ('locator',?,'TRANSCRIPT',?,'segment')",MEETING,REVISION);
        setup.update("INSERT INTO sensitive_exclusion VALUES ('exclusion','locator','INTERNAL_BUSINESS_DATA',NULL)");
        setup.update("INSERT INTO generation_candidate VALUES ('candidate',?,'generation','b08-lower-task','minutes','set',0,?,'PENDING',?,NULL)",MEETING,HASH,AT);
        setup.update("INSERT INTO evidence_link VALUES ('evidence','locator','element',NULL,'SUPPORTED','anchor')");
        setup.update("INSERT INTO sensitive_detection VALUES ('detection','set','generation','INTERNAL_BUSINESS_DATA',1)");
        setup.update("INSERT INTO sensitive_detection_source VALUES ('detection','locator')");
        setup.update("INSERT INTO background_note VALUES ('background','set','generation',1,'background')");
        setup.update("INSERT INTO background_note_source VALUES ('background','locator')");
        setup.update("INSERT INTO action_suggestion VALUES ('element','generation','unverified owner',NULL)");
        setup.update("INSERT INTO result_review VALUES ('review',?,1,'SOURCE_SUPPORT','element',NULL,'anchor',?,?,'test-profile','SUPPORTED',?,?)",MEETING,HASH,HASH,OWNER,AT);
        setup.update("INSERT INTO result_review_evidence VALUES ('review','evidence')");
    }
    private void seedLowerRows() {
        identity(setup,OWNER);
        setup.update("INSERT INTO identity_session(token_hash,enterprise_id,session_generation,csrf_token,issued_at,expires_at) VALUES (?,?,0,?,?,'2026-10-23')",HASH,OWNER,"c".repeat(43),AT);
        setup.update("INSERT INTO meeting (id,owner_id,stage,created_at,updated_at) VALUES (?,?,'ENTRY',?,?)",MEETING,OWNER,AT,AT);
        setup.update("INSERT INTO meeting_info_revision(id,meeting_id,revision,name,created_by,created_at) VALUES ('b08-lower-info',?,0,'lower-name',?,?)",MEETING,OWNER,AT);
        setup.update("INSERT INTO storage_backend VALUES ('b08-schema-backend','local','WRITABLE',0)");
        file(setup,FILE,"lower-file");
        setup.update("INSERT INTO audit_event(actor_id,action,resource_id,revision,result,trace_id,occurred_at) VALUES (?,'SCHEMA_PROBE',?,0,'SUCCESS','b08-lower-trace',?)",OWNER,MEETING,AT);
        setup.update("INSERT INTO task_subject VALUES ('MEETING',?,0,0,0)",MEETING);
        setup.update("INSERT INTO durable_task(id,task_type,pool,subject_kind,subject_id,producer_key,input_kind,input_id,input_fingerprint,business_version,cancel_epoch,priority,max_attempts,available_at,created_at) VALUES ('b08-lower-task','TEST_SCHEMA','TEST_SCHEMA','MEETING',?,?,'TEST_INFO','b08-lower-info',?,0,0,0,1,?,?)",MEETING,HASH,HASH,AT,AT);
        setup.update("INSERT INTO input_batch VALUES (?,?,'TRANSCRIPT',?,0,?)",BATCH,MEETING,HASH,AT);
        setup.update("INSERT INTO input_batch_file VALUES (?,1,?,?)",BATCH,FILE,HASH);
        setup.update("INSERT INTO transcription_input (id,meeting_id,batch_id,kind,material_mode,processing_config_version,parser_version,manifest_schema_version,config_manifest,input_fingerprint,base_revision,cancel_epoch,created_at) VALUES (?,?,?,'IMPORT','EMPTY','test-config','test-parser',1,'test-manifest',?,0,0,?)",INPUT,MEETING,BATCH,HASH,AT);
        setup.update("INSERT INTO transcript_revision (id,meeting_id,revision,kind,batch_id,input_fingerprint,created_by,created_at,processing_input_id) VALUES (?,?,0,'RAW',?,?,?,?,?)",REVISION,MEETING,BATCH,HASH,OWNER,AT,INPUT);
        setup.update("INSERT INTO speaker VALUES (?,?,?,NULL,'ANONYMOUS',NULL,0)",SPEAKER,MEETING,"Speaker");
        setup.update("INSERT INTO transcript_segment (revision_id,segment_id,ordinal,body,source_file_id) VALUES (?,'segment',1,'Plaintext',?)",REVISION,FILE);
        setup.update("INSERT INTO transcript_confirmation VALUES ('b08-confirmation',?,?,?,?,?)",MEETING,REVISION,HASH,OWNER,AT);
        seedGeneration();
    }

    private void identity(JdbcTemplate target,String id) {
        target.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',?)",id,id,AT);
    }

    private void file(JdbcTemplate target,String id,String key) {
        target.update("INSERT INTO file_object(id,meeting_id,backend_id,storage_type,namespace,object_key,display_name,size_bytes,sha256,state,created_at) VALUES (?,?,'b08-schema-backend','local','TEST_SCHEMA',?,'source',12,?,'AVAILABLE',?)",id,MEETING,key,HASH,AT);
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
        Path record=root.resolve(".tools/publication-schema-review/fixture-identity.json");
        assertTrue(Files.isRegularFile(record),"new fixture's initial-empty capture required");
        JsonNode original=JSON.readTree(Files.readAllBytes(record));
        assertEquals(name,original.path("name").asText());
        assertEquals(0,original.path("initial_table_count").asInt(-1));
        String format="{\"id\":{{json .Id}},\"name\":{{json .Name}},\"image\":{{json .Config.Image}},\"image_id\":{{json .Image}},\"label\":{{json (index .Config.Labels \"ama.test-fixture\")}},\"args\":{{json .Args}},\"ports\":{{json .NetworkSettings.Ports}},\"mounts\":{{json .Mounts}},\"auto_remove\":{{json .HostConfig.AutoRemove}},\"nano_cpus\":{{json .HostConfig.NanoCpus}},\"memory\":{{json .HostConfig.Memory}},\"memory_swap\":{{json .HostConfig.MemorySwap}},\"pids_limit\":{{json .HostConfig.PidsLimit}}}";
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
        return Flyway.configure().dataSource(source).locations("classpath:db/migration").target("013")
                .initSql("SET NAMES utf8mb4; SET time_zone='+00:00'").cleanDisabled(true).load();
    }

    private int migrate(String target) {
        return Flyway.configure().dataSource(source).locations("classpath:db/migration").target(target)
                .initSql("SET NAMES utf8mb4; SET time_zone='+00:00'").cleanDisabled(true).load().migrate().migrationsExecuted;
    }

    private JsonNode lowerSnapshot() throws Exception { return snapshot(false); }
    private JsonNode fullSnapshot() throws Exception { return snapshot(true); }

    private JsonNode snapshot(boolean includePublication) throws Exception {
        var ddl=new TreeMap<String,String>();
        var rows=new TreeMap<String,Object>();
        for(String table:setup.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY BINARY table_name",String.class)) {
            if(!includePublication && TABLES.contains(table)) continue;
            ddl.put(table,setup.queryForMap("SHOW CREATE TABLE `"+table+"`").get("Create Table").toString());
            String filter=table.equals("flyway_schema_history")&&!includePublication?" WHERE version <> '013'":"";
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
                if(includePublication || !TABLES.contains(row.path("table").asText())) group.add(row);
            }
            metadata.put(query.getKey(),group);
        }
        var hashes=new TreeMap<String,String>();
        for(String name:LOWER_HASHES.keySet()) try(var input=getClass().getResourceAsStream("/db/migration/"+name)) {
            assertNotNull(input,name);
            hashes.put(name,sha(input.readAllBytes()));
        }
        var snapshot=new TreeMap<String,Object>(Map.of("ddl",ddl,"rows",rows,"metadata",metadata,"lower_sql_sha256",new TreeMap<>(hashes)));
        if(includePublication) {
            try(var input=getClass().getResourceAsStream("/db/migration/V013__publications.sql")) {
                if(input!=null) hashes.put("V013__publications.sql",sha(input.readAllBytes()));
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
