package ama.storage;

import ama.task.*;
import ama.contract.TaskContracts.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Task05FixCarrierIT extends Task05FixFixture {
    @Test void twoGenuineDifferentPlansRejectCrossLeaseCarriersThenEachWritesItsOwnRows() throws Exception {
        String firstMeeting=meeting;String taskA=accept(List.of(upload("Alpha"),upload("Beta")));
        var queue=app.getBean(JdbcTaskQueue.class);var leaseA=claim(taskA);
        var carrierA=TranscriptImportRuntime.prepare(queue,leaseA,settings(),NEVER);
        newMeeting();String taskB=accept(List.of(upload("Beta"),upload("Alpha")));var leaseB=claim(taskB);
        var carrierB=TranscriptImportRuntime.prepare(queue,leaseB,settings(),NEVER);
        assertNotSame(carrierA,carrierB);
        long inserts=probe.statements.stream().filter(sql->sql.startsWith("INSERT INTO transcript_revision")).count();
        assertThrows(RuntimeException.class,()->queue.completeWithEffects(leaseA,carrierB));
        assertThrows(RuntimeException.class,()->queue.completeWithEffects(leaseB,carrierA));
        assertEquals(inserts,probe.statements.stream().filter(sql->sql.startsWith("INSERT INTO transcript_revision")).count());
        for(String task:List.of(taskA,taskB))assertNoOutput(task);
        // Inert public result suppliers cannot substitute either real private carrier.
        var foreignResult=new TaskResult(Outcome.SUCCEEDED,new TypedRef("TRANSCRIPT_REVISION",UUID.randomUUID().toString()),null);
        assertThrows(RuntimeException.class,()->queue.completeWithEffects(leaseA,()->foreignResult));assertNoOutput(taskA);
        assertTrue(queue.completeWithEffects(leaseB,carrierB));assertTrue(queue.completeWithEffects(leaseA,carrierA));
        assertEquals(List.of("Alpha","Beta"),bodies(taskA));assertEquals(List.of("Beta","Alpha"),bodies(taskB));
        assertEquals(firstMeeting,j.queryForObject("SELECT meeting_id FROM transcript_revision WHERE producer_task_id=?",String.class,taskA));
        Files.writeString(receipt.resolve("two-genuine-carriers.json"),json.writeValueAsString(Map.of("taskA",taskA,"taskB",taskB,"crossLeaseRejections",2,"arbitraryResultRejections",1,"planA",bodies(taskA),"planB",bodies(taskB))),StandardOpenOption.CREATE_NEW);
    }

    @Test void actualPrivateValidatorRejectsJustInsertedBodySourceAndOrderCorruptionThenRecovers() throws Exception {
        for(String corruption:List.of("BODY","SOURCE","ORDER")) {
            if(!corruption.equals("BODY"))newMeeting();
            String first=upload("Alpha"),second=upload("Beta");String task=accept(List.of(first,second));
            var queue=app.getBean(JdbcTaskQueue.class);var lease=claim(task);
            var carrier=TranscriptImportRuntime.prepare(queue,lease,settings(),NEVER);
            int outbox=j.queryForObject("SELECT COUNT(*) FROM task_outbox WHERE task_id=?",Integer.class,task);
            var applied=new AtomicBoolean();var actualValidatorQueries=new AtomicInteger();
            probe.before=sql->{
                if(sql.startsWith("SELECT segment_id,ordinal,OCTET_LENGTH")) {
                    actualValidatorQueries.incrementAndGet();
                    if(applied.compareAndSet(false,true)) {
                        String raw=j.queryForObject("SELECT id FROM transcript_revision WHERE producer_task_id=?",String.class,task);
                        assertEquals(2,j.queryForObject("SELECT COUNT(*) FROM transcript_segment WHERE revision_id=?",Integer.class,raw));
                        int rows=switch(corruption) {
                            case "BODY" -> j.update("UPDATE transcript_segment SET body='Omega' WHERE revision_id=? AND ordinal=1",raw);
                            case "SOURCE" -> j.update("UPDATE transcript_segment SET source_file_id=? WHERE revision_id=? AND ordinal=1",second,raw);
                            default -> j.update("UPDATE transcript_segment SET ordinal=ordinal+10 WHERE revision_id=?",raw);
                        };
                        assertEquals(corruption.equals("ORDER")?2:1,rows);
                    }
                }
            };
            try {assertThrows(RuntimeException.class,()->queue.completeWithEffects(lease,carrier),corruption);}
            finally {probe.before=sql->{};}
            assertTrue(applied.get());assertTrue(actualValidatorQueries.get()>0);
            assertNoOutput(task);
            assertNull(j.queryForObject("SELECT ended_at FROM task_attempt WHERE task_id=? AND attempt=?",Object.class,task,lease.attempt()));
            assertEquals(outbox,j.queryForObject("SELECT COUNT(*) FROM task_outbox WHERE task_id=?",Integer.class,task));
            assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id=? AND action='TRANSCRIPT_IMPORTED'",Integer.class,meeting));
            assertTrue(queue.completeWithEffects(lease,carrier));assertEquals(List.of("Alpha","Beta"),bodies(task));
            String raw=j.queryForObject("SELECT output_id FROM durable_task WHERE id=?",String.class,task);
            assertEquals(List.of(first,second),j.queryForList("SELECT source_file_id FROM transcript_segment WHERE revision_id=? ORDER BY ordinal",String.class,raw));
            assertEquals(List.of(1L,2L),j.queryForList("SELECT ordinal FROM transcript_segment WHERE revision_id=? ORDER BY ordinal",Long.class,raw));
            Files.writeString(receipt.resolve("actual-validator-"+corruption+".json"),json.writeValueAsString(Map.of("task",task,"corruption",corruption,"actualValidatorQueries",actualValidatorQueries.get(),"rolledBackBeforeRecovery",true,"genuineSameLeaseRecovery",true,"raw",raw)),StandardOpenOption.CREATE_NEW);
        }
    }
    void assertNoOutput(String task) {
        assertEquals("RUNNING",j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task));
        assertNull(j.queryForObject("SELECT output_id FROM durable_task WHERE id=?",String.class,task));
        assertEquals(0,j.queryForObject("SELECT COUNT(*) FROM transcript_revision WHERE producer_task_id=?",Integer.class,task));
    }
    List<String> bodies(String task) {
        return j.queryForList("SELECT s.body FROM transcript_segment s JOIN transcript_revision r ON r.id=s.revision_id WHERE r.producer_task_id=? ORDER BY s.ordinal",String.class,task);
    }
}
