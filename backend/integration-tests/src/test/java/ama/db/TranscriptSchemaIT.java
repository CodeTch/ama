package ama.db;

import ama.support.MysqlTestDatabase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/**
 * PREPARATION ONLY: actual metadata assertions, compiled but never database-run.
 * Formal resumption must add the owned-fixture/upgrade/preservation harness and
 * physical constraint cases before this can qualify V011. No fixture is reset.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TranscriptSchemaIT {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> TABLES = List.of("input_batch", "input_batch_file", "speaker",
            "transcript_confirmation", "transcript_revision", "transcript_segment", "transcript_segment_mark",
            "transcription_input");
    private static final List<String> LOWER = List.of("001", "002", "005", "006", "007", "008", "009", "010");
    private static final List<String> FULL = List.of("001", "002", "005", "006", "007", "008", "009", "010", "011");
    private JdbcTemplate jdbc;

    @BeforeAll
    void attachOnlyAfterExplicitFormalResumption() {
        // This intentional failure is a hold guard, NOT feature RED. No JDBC
        // connection is attempted during preparation, including accidental runs.
        assertTrue(Boolean.getBoolean("ama.transcript.schema.formal-resumption"),
                "B06 preparation hold: require root formal resumption before any database execution");
        var source = MysqlTestDatabase.dataSource();
        jdbc = new JdbcTemplate(source);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=0", Integer.class),
                "preserve failed migration history; no reset or repair");
        var versions = jdbc.queryForList("SELECT version FROM flyway_schema_history ORDER BY installed_rank", String.class);
        assertTrue(versions.equals(LOWER) || versions.equals(FULL),
                "only exact known successful lower/full history is eligible: " + versions);
        // Private historical target; production migrator remains latest.
        Flyway.configure().dataSource(source).locations("classpath:db/migration")
                .target("011").cleanDisabled(true).load().validate();
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
