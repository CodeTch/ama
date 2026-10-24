package ama.transcript;

import ama.config.file.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static ama.transcript.ArchiveRestartFixture.*;
import static org.junit.jupiter.api.Assertions.*;

/** Retains every unique run's positive releases, copied negatives and real process receipts. */
class Task06InheritedArchiveRestartIT {
    final List<Properties> owned=new ArrayList<>();
    record Run(Process process,int exit,Properties receipt) {}
    Run launch(Path run,String label,String mode,Path root,String pin,Path handoff)throws Exception {
        Path receipt=run.resolve(label+".properties"),out=run.resolve(label+".stdout.log"),err=run.resolve(label+".stderr.log");
        var argv=List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Xmx512m","-XX:ActiveProcessorCount=2","-cp",System.getProperty("java.class.path"),ArchiveRestartProbe.class.getName(),mode,root.toString(),pin,handoff.toString(),receipt.toString());
        Files.write(run.resolve(label+".argv.txt"),argv,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        var process=new ProcessBuilder(argv).redirectOutput(out.toFile()).redirectError(err.toFile()).start();
        Files.writeString(run.resolve(label+".parent-pid.txt"),Long.toString(process.pid()),StandardOpenOption.CREATE_NEW);
        try {assertTrue(process.waitFor(30,TimeUnit.SECONDS),"own child exceeded30s: "+label);}
        finally {if(process.isAlive()){process.destroy();if(!process.waitFor(2,TimeUnit.SECONDS)){process.destroyForcibly();assertTrue(process.waitFor(2,TimeUnit.SECONDS));}}}
        Files.writeString(run.resolve(label+".exit.txt"),Integer.toString(process.exitValue()),StandardOpenOption.CREATE_NEW);
        assertTrue(Files.exists(receipt),"child receipt missing; retained logs "+run);
        Properties p=read(receipt);assertEquals(Long.toString(process.pid()),p.getProperty("pid"),"actual Process.pid and child identity must agree");
        return new Run(process,process.exitValue(),p);
    }
    @Test void freshJvmRecoversAcceptedOldInputAndRejectsDamagedCopies()throws Exception {
        String evidence=Path.of(System.getenv("AMA_TASK05_ROOT")).getParent().getParent().resolve("artifacts/phase-b/b-remaining/task-06/fix-01/inherited-archive").toString();Files.createDirectories(Path.of(evidence));
        Path run=Path.of(evidence).resolve("runtime-"+UUID.randomUUID());Files.createDirectory(run);Path root=run.resolve("positive");Files.createDirectory(root);
        Files.writeString(run.resolve("parent-jvm-pid.txt"),Long.toString(ProcessHandle.current().pid()),StandardOpenOption.CREATE_NEW);
        var publisher=new ControlledArchiveReleaseWriter();String one=publisher.publish(root,null,bundle(),I,B,()->false);
        assertEquals(new String(literal("index1.sha256"),StandardCharsets.UTF_8),one);
        Path handoff=run.resolve("commit-handoff.properties");Run a=launch(run,"A","A",root,one,handoff);
        assertEquals(0,a.exit(),"A must commit genuine accepted queue task");assertEquals("COMMIT_ACK",a.receipt().getProperty("status"));
        var p=read(handoff);owned.add(p);assertEquals(Long.toString(a.process().pid()),p.getProperty("pidA"));
        var f=new ArchiveRestartFixture();var actual=new JdbcProcessingFactsReader(f.source).readStored(actor(p.getProperty("owner")),p.getProperty("meeting"),p.getProperty("input"),B,()->false);
        assertEquals(1,actual.taskFacts().size());assertEquals(p.getProperty("task"),actual.taskFacts().getFirst().taskId());assertEquals(spec(p,p.getProperty("digest")),actual.taskFacts().getFirst().spec());
        String before=snapshot(f,p);Files.writeString(run.resolve("db-before.sha256"),before,StandardOpenOption.CREATE_NEW);
        String two=publisher.publish(root,one,List.of(raw("processing2")),I,B,()->false);
        assertEquals(new String(literal("index2.sha256"),StandardCharsets.UTF_8),two);
        Run b=launch(run,"B","B",root,two,handoff);
        assertEquals(0,b.exit(),"fresh B must recover old persisted manifest under appended index/defaultroot2");
        assertEquals("RECOVERED",b.receipt().getProperty("status"));assertEquals(p.getProperty("digest"),b.receipt().getProperty("oldDigest"));assertEquals(p.getProperty("manifestSha256"),b.receipt().getProperty("oldManifestSha256"));assertEquals(p.getProperty("root1Sha256"),b.receipt().getProperty("oldRootSha256"));
        assertEquals("b06.controlled.import-txt.2",b.receipt().getProperty("defaultRootVersion"));assertNotEquals(p.getProperty("digest"),b.receipt().getProperty("newDraftDigest"));assertNotEquals(a.process().pid(),b.process().pid());assertNotEquals(ProcessHandle.current().pid(),b.process().pid());
        var pids=new HashSet<Long>();pids.add(a.process().pid());pids.add(b.process().pid());
        for(String damage:List.of("missing-history","mutated-history","wrong-index-pin")) {
            Path copy=run.resolve(damage);copyTree(root,copy);String pin=two;
            Path oldObject=copy.resolve("releases").resolve(two).resolve("objects").resolve(p.getProperty("root1Sha256")+".json");
            if(damage.equals("missing-history"))Files.delete(oldObject);
            if(damage.equals("mutated-history")){byte[] bytes=Files.readAllBytes(oldObject);bytes[0]='[';Files.write(oldObject,bytes);}
            if(damage.equals("wrong-index-pin"))pin="0".repeat(64);
            Run negative=launch(run,damage,"B",copy,pin,handoff);
            assertEquals(23,negative.exit(),"typed unavailable required: "+damage);assertEquals("UNAVAILABLE",negative.receipt().getProperty("status"));assertEquals("PROFILE_UNAVAILABLE",negative.receipt().getProperty("code"));assertNull(negative.receipt().getProperty("oldDigest"));assertNull(negative.receipt().getProperty("newDraftDigest"));assertTrue(pids.add(negative.process().pid()),"fresh process required");
            assertEquals(before,snapshot(f,p),"failed recovery must not mutate DB: "+damage);
        }
        assertEquals(before,snapshot(f,p));Files.writeString(run.resolve("db-after.sha256"),snapshot(f,p),StandardOpenOption.CREATE_NEW);
        // Reopening the untouched positive root still verifies all original bytes after every negative.
        assertEquals(5,new ControlledConfigArchiveSource().open(root,two,I,B,()->false).index().entries().size());
    }
    static void copyTree(Path source,Path dest)throws Exception {
        try(var paths=Files.walk(source)){for(var path:paths.toList()){Path out=dest.resolve(source.relativize(path));if(Files.isDirectory(path))Files.createDirectory(out);else Files.copy(path,out);}}
    }
    static String snapshot(ArchiveRestartFixture f,Properties p){
        outside();var text=new StringBuilder();
        for(String table:List.of("enterprise_identity","task_subject","meeting","storage_backend","file_object","input_batch","input_batch_file","transcription_input","durable_task","task_outbox","transcript_revision"))text.append(table).append(':').append(f.j.queryForObject("SELECT COUNT(*) FROM "+table,Long.class)).append('\n');
        text.append(f.j.queryForList("SELECT * FROM transcription_input WHERE id=?",p.getProperty("input")));
        text.append(f.j.queryForList("SELECT * FROM durable_task WHERE id=?",p.getProperty("task")));
        text.append(f.j.queryForList("SELECT * FROM task_outbox WHERE task_id=? ORDER BY sequence_id",p.getProperty("task")));
        text.append(f.j.queryForList("SELECT * FROM meeting WHERE id=?",p.getProperty("meeting")));
        text.append(f.j.queryForList("SELECT * FROM task_subject WHERE subject_kind='MEETING' AND subject_id=?",p.getProperty("meeting")));
        return sha(text.toString().getBytes(StandardCharsets.UTF_8));
    }
 @org.junit.jupiter.api.AfterEach void closeOwnMetadataAcceptance()throws Exception {var f=new ArchiveRestartFixture();for(var p:owned){f.tx.required(()->{f.j.queryForObject("SELECT id FROM durable_task WHERE id=? AND subject_id=? FOR UPDATE",String.class,p.getProperty("task"),p.getProperty("meeting"));assertEquals(0,f.j.queryForObject("SELECT COUNT(*) FROM task_attempt WHERE task_id=?",Integer.class,p.getProperty("task")));f.j.update("UPDATE durable_task SET state='FAILED',lease_owner=NULL,expires_at=NULL WHERE id=? AND state='READY'",p.getProperty("task"));return null;});var root=Path.of(System.getenv("AMA_TASK05_ROOT")).getParent().getParent().resolve("artifacts/phase-b/b-remaining/task-06/fix-01/inherited-archive");Files.writeString(root.resolve(p.getProperty("task")+"-fixture-closed.json"),new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("task",p.getProperty("task"),"meeting",p.getProperty("meeting"),"owner",p.getProperty("owner"),"scope","controlled metadata acceptance; after all immutable-recovery assertions, precise own READY fixture terminal maintenance", "state",f.j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,p.getProperty("task")))));}}
}
