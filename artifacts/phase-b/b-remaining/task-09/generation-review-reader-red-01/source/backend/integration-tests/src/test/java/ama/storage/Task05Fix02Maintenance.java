package ama.storage;

import java.nio.file.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Finite prelinked owned-case fixture maintenance; never business cancellation/success evidence. */
final class Task05Fix02Maintenance {
    static Object safe(Object value){
        if(value instanceof java.time.temporal.TemporalAccessor)return value.toString();
        if(value instanceof Map<?,?> map){var converted=new LinkedHashMap<String,Object>();map.forEach((k,v)->converted.put(k.toString(),safe(v)));return converted;}
        if(value instanceof Collection<?> list)return list.stream().map(Task05Fix02Maintenance::safe).toList();
        return value;
    }
    static void apply(DataSource source,String task,Path accepted,Path output) throws Exception {
        var json=new ObjectMapper().findAndRegisterModules(); var j=new JdbcTemplate(source);
        var receipt=json.readTree(Files.readString(accepted));
        assertEquals(task,receipt.get("task").asText());
        String owner=receipt.get("owner").asText(), meeting=receipt.get("meeting").asText();
        var before=j.queryForMap("SELECT * FROM durable_task WHERE id=?",task);
        var link=j.queryForMap("SELECT m.owner_id,i.id input_id,i.meeting_id,i.batch_id FROM meeting m JOIN transcription_input i ON i.meeting_id=m.id WHERE m.id=? AND i.id=?",meeting,before.get("input_id"));
        assertEquals(owner,link.get("owner_id")); assertEquals(meeting,before.get("subject_id"));
        var files=j.queryForList("SELECT f.id,f.meeting_id,f.backend_id,f.namespace,f.object_key,u.id upload_id,u.state upload_state FROM input_batch_file b JOIN file_object f ON f.id=b.file_id JOIN upload_session u ON u.backend_id=f.backend_id AND u.namespace=f.namespace AND u.object_key=f.object_key WHERE b.batch_id=? ORDER BY b.ordinal",link.get("batch_id"));
        assertFalse(files.isEmpty());
        var acceptedRow=j.queryForMap("SELECT actor_id,resource_id,operation,result_id,http_status,state FROM idempotency_record WHERE result_id=? AND operation='PROCESSING_IMPORT'",task);
        assertEquals(owner,acceptedRow.get("actor_id"));assertEquals(meeting,acceptedRow.get("resource_id"));assertEquals(202,((Number)acceptedRow.get("http_status")).intValue());assertEquals("SUCCEEDED",acceptedRow.get("state"));
        for(var file:files){assertEquals(meeting,file.get("meeting_id"));assertEquals("COMPLETED",file.get("upload_state"));}
        Files.writeString(output.resolve("maintenance-prelinked-"+task+".json"),json.writeValueAsString(safe(Map.of("accepted",accepted.toString(),"link",link,"files",files,"actualAcceptance",acceptedRow,"before",before))),StandardOpenOption.CREATE_NEW);
        var template=new TransactionTemplate(new DataSourceTransactionManager(source));
        var counts=template.execute(status->{
            var row=j.queryForMap("SELECT * FROM durable_task WHERE id=? FOR UPDATE",task); assertEquals(before,row);
            assertEquals("TRANSCRIPT_IMPORT_V1",row.get("task_type"));assertEquals("TRANSCRIPT_IMPORT",row.get("pool"));
            assertEquals("MEETING",row.get("subject_kind"));assertEquals(meeting,row.get("subject_id"));
            assertNull(row.get("output_id"));assertNull(row.get("output_kind"));
            assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM transcript_revision WHERE producer_task_id=?",Integer.class,task));
            assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM external_operation WHERE subject_kind='MEETING' AND subject_id=?",Integer.class,meeting));
            assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM external_execution WHERE task_id=?",Integer.class,task));
            assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM model_reservation r JOIN external_execution e ON e.id=r.execution_id WHERE e.task_id=?",Integer.class,task));
            long attempt=((Number)row.get("attempt")).longValue();int ended=0;
            if(row.get("state").equals("RUNNING")) {
                assertTrue(attempt>0);var a=j.queryForMap("SELECT * FROM task_attempt WHERE task_id=? AND attempt=? FOR UPDATE",task,attempt);
                assertEquals(row.get("lease_owner"),a.get("owner"));assertNull(a.get("ended_at"));
                ended=j.update("UPDATE task_attempt SET ended_at=UTC_TIMESTAMP(6),outcome='CANCELLED',error_code='TASK05_FIXTURE_CLEANUP' WHERE task_id=? AND attempt=? AND owner=? AND ended_at IS NULL",task,attempt,row.get("lease_owner"));assertEquals(1,ended);
            }else{assertEquals("READY",row.get("state"));assertEquals(0,attempt);assertNull(row.get("lease_owner"));}
            int changed=j.update("UPDATE durable_task SET state='CANCELLED',lease_owner=NULL,expires_at=NULL,last_error='TASK05_FIXTURE_CLEANUP' WHERE id=? AND state=? AND attempt=? AND lease_owner <=> ? AND output_id IS NULL AND output_kind IS NULL",task,row.get("state"),attempt,row.get("lease_owner"));
            assertEquals(1,changed);return Map.of("taskRows",changed,"attemptRows",ended);
        });
        Files.writeString(output.resolve("maintenance-after-"+task+".json"),json.writeValueAsString(safe(Map.of("counts",counts,"task",j.queryForMap("SELECT * FROM durable_task WHERE id=?",task),"attempts",j.queryForList("SELECT * FROM task_attempt WHERE task_id=? ORDER BY attempt",task)))),StandardOpenOption.CREATE_NEW);
    }
}
