package ama.storage;

import ama.publication.Task11Source;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Dedicated DB SELECTs and allocated private-input reads only. No app, migration, child or business fixture. */
class Task11B2RuntimePreflightIT {
    @Test void retainedBootstrapAndAllocatedInputsAreReadyWithoutRuntimeEffects()throws Exception {
        var inputs=Task11B2RuntimeFixture.inspectPublicInputs();var result=new LinkedHashMap<String,Object>();String phase="PUBLIC_INPUTS";
        result.put("configurationAckSha256",Task11B2RuntimeFixture.ACK_SHA);result.put("runtimeEffects",false);
        try{
            phase="ABSENT_RUNTIME_ORIGINS";var absentBefore=Task11B2RuntimeFixture.absentOrigins(inputs);result.put("absentOriginsBefore",absentBefore);
            phase="ALLOCATED_PRIVATE_INPUTS";result.put("privateValidation",Task11B2RuntimeFixture.validatePrivateInputs(inputs));
            phase="DEDICATED_DB";var j=new JdbcTemplate(Task11Source.open());j.setQueryTimeout(5);
            var session=j.queryForMap("SELECT DATABASE() AS selected_schema,CURRENT_USER() AS principal,@@server_uuid AS server_uuid,@@session.time_zone AS zone,@@character_set_connection AS charset,@@session.lock_wait_timeout AS metadata_wait,@@session.innodb_lock_wait_timeout AS row_wait");
            assertEquals("ama_t11b2_d9e2fdcc611e_behavior",session.get("selected_schema"));assertEquals("t11b2_d9e2fdcc611e@%",session.get("principal"));assertEquals("1bdea6eb-cf32-11f1-8fe3-fef4aa4b3a25",session.get("server_uuid"));assertEquals("+00:00",session.get("zone"));assertEquals("utf8mb4",session.get("charset"));assertEquals(5,((Number)session.get("metadata_wait")).intValue());assertEquals(5,((Number)session.get("row_wait")).intValue());result.put("session",session);
            phase="RETAINED_FULL_HISTORY";var before=history(j);Path saved=inputs.root().resolve("artifacts/phase-b/b-remaining/task-11/b2-bootstrap-01/full-sixteen-history-metadata.json");
            // The retained bootstrap evidence is already pinned by the successful root verification/index.
            Path w=inputs.root().resolve(".superpowers/sdd/2026-10-23-ama-phase-b-remaining");
            var verification=Task11B2RuntimeFixture.publicJson(w.resolve("task-11-b2-bootstrap-root-verification-20261026.json"),inputs.configuration().at("/publicInputPins/task-11-b2-bootstrap-root-verification-20261026.json").asText());
            byte[] indexBytes=Task11B2RuntimeFixture.publicBytes(w.resolve("task-11-b2-bootstrap-window-index-20261026.json"));assertTrue(Task11B2RuntimeFixture.digest(indexBytes).equals(verification.get("indexSha256").asText()),"Retained bootstrap index pin mismatch");var bootstrapIndex=Task11B2RuntimeFixture.JSON.readTree(indexBytes);
            assertTrue(Task11B2RuntimeFixture.digest(Task11B2RuntimeFixture.publicBytes(saved)).equals(bootstrapIndex.get("fullHistoryMetadataSha256").asText()),"Retained bootstrap metadata bytes changed");var expected=Task11B2RuntimeFixture.JSON.readTree(Task11B2RuntimeFixture.publicBytes(saved));
            assertEquals(16,before.size());assertEquals("013.4",before.getLast().get("version"));assertTrue(Task11B2RuntimeFixture.JSON.valueToTree(before).equals(expected.get("after")),"All ten retained UTC history fields must match");result.put("historyBefore",before);
            phase="NO_NEW_BUSINESS";var counts=counts(j);for(var count:counts.values())assertEquals(0L,count.longValue(),"No business/runtime fixture may precede preflight");result.put("businessCountsBefore",counts);
            assertEquals(19,j.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='publication_submission'",Integer.class));
            phase="READ_ONLY_FINAL_COMPARE";assertEquals(before,history(j));assertEquals(counts,counts(j));assertEquals(absentBefore,Task11B2RuntimeFixture.absentOrigins(inputs));result.put("historyAfter",history(j));result.put("businessCountsAfter",counts(j));result.put("absentOriginsAfter",absentBefore);result.put("status","PASS");result.put("noMigratorAppClientChildOrIdentityInstallation",true);result.put("actualProviderRequests",0);result.put("actualNativeRequests",0);phase="COMPLETE";
        }catch(Throwable failure){result.put("status","FAIL_OR_SETUP_ERROR");result.put("safeFailureType",failure.getClass().getName());throw failure;}
        finally{result.put("lastPhase",phase);Files.writeString(inputs.evidence().resolve("b2-runtime-preflight.json"),Task11B2RuntimeFixture.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result)+"\n",StandardOpenOption.CREATE_NEW);}
    }
    static List<Map<String,String>> history(JdbcTemplate j){return j.queryForList("SELECT installed_rank,version,description,type,script,checksum,installed_by,DATE_FORMAT(installed_on,'%Y-%m-%dT%H:%i:%s') AS installed_on,execution_time,success FROM flyway_schema_history ORDER BY installed_rank").stream().map(row->{var result=new LinkedHashMap<String,String>();row.forEach((key,value)->result.put(key,value==null?null:value.toString()));return (Map<String,String>)result;}).toList();}
    static Map<String,Long> counts(JdbcTemplate j){var result=new TreeMap<String,Long>();for(String table:List.of("enterprise_identity","identity_session","role_assignment","meeting","publication","publication_submission","final_confirmation","durable_task","external_operation","external_execution","model_capacity","model_reservation"))result.put(table,j.queryForObject("SELECT COUNT(*) FROM "+table,Long.class));return result;}
}
