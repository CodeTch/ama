package ama.transcript;

import ama.config.*;
import ama.config.file.ControlledConfigArchiveSource;
import ama.config.json.StrictProcessingManifestReader;
import ama.contract.MinutesContracts.Failure;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static ama.transcript.ArchiveRestartFixture.*;

/** A real separate JVM entry point. Inputs are public paths/pins/IDs; fixture credentials stay inherited. */
public final class ArchiveRestartProbe {
    public static void main(String[] args)throws Exception {
        String mode=args[0];Path root=Path.of(args[1]),handoff=Path.of(args[3]),receipt=Path.of(args[4]);
        var result=new Properties();result.setProperty("pid",Long.toString(ProcessHandle.current().pid()));result.setProperty("mode",mode);
        System.out.println("B06_PROCESS_START mode="+mode+" pid="+ProcessHandle.current().pid());
        try {
            if(mode.equals("A"))produce(root,args[2],handoff,result);else recover(root,args[2],handoff,result);
            write(receipt,result);System.out.println("B06_PROCESS_SUCCESS mode="+mode+" pid="+ProcessHandle.current().pid());
        }catch(Failure failure){
            result.setProperty("status","UNAVAILABLE");result.setProperty("code",failure.code().name());write(receipt,result);
            System.out.println("B06_UNAVAILABLE code="+failure.code().name());System.exit(23);
        }
    }
    static void produce(Path root,String pin,Path handoff,Properties result)throws Exception {
        outside();var snapshot=new ControlledConfigArchiveSource().open(root,pin,I,B,()->false);
        var f=new ArchiveRestartFixture();var p=f.seed();var reader=new JdbcProcessingFactsReader(f.source);
        var batch=reader.readBatch(actor(p.getProperty("owner")),p.getProperty("meeting"),p.getProperty("batch"),B,()->false);
        var batchRow=batch.rows().stream().filter(r->r.table()==ama.contract.MinutesContracts.Table.INPUT_BATCH).findFirst().orElseThrow();
        if(!new BatchFingerprint().verify(batchRow,batch.rows().stream().filter(r->r.table()==ama.contract.MinutesContracts.Table.INPUT_BATCH_FILE).toList(),batch.files(),C,()->false).equals(p.getProperty("batchFingerprint")))throw new AssertionError("real batch v1");
        byte[] manifest=manifest(p,p.getProperty("input"),ref(raw("processing1")));
        var archives=snapshot.loadClosure(List.of(ref(raw("processing1"))),B,manifest.length,()->false);
        String digest=bindings().computeProcessing(manifest,batch,archives,B,()->false).fingerprint();
        String task=f.accept(p,manifest,digest); // Actual outer commit returned before any ACK.
        var stored=reader.readStored(actor(p.getProperty("owner")),p.getProperty("meeting"),p.getProperty("input"),B,()->false);
        if(stored.taskFacts().size()!=1||!stored.taskFacts().getFirst().taskId().equals(task)||!stored.taskFacts().getFirst().spec().equals(spec(p,digest)))throw new AssertionError("queue accepted fullspec not durable");
        if(!digest.equals(bindings().verifyProcessing(input(stored),stored,archives,B,()->false).fingerprint()))throw new AssertionError("postcommit stored verification");
        if(!"READY".equals(f.j.queryForObject("SELECT state FROM durable_task WHERE id=?",String.class,task))||f.j.queryForObject("SELECT COUNT(*) FROM task_outbox WHERE task_id=? AND event_type='TASK_ENQUEUED'",Integer.class,task)!=1)throw new AssertionError("queue/outbox commit absent");
        p.setProperty("task",task);p.setProperty("digest",digest);p.setProperty("producerKey",spec(p,digest).producerKey());p.setProperty("manifestSha256",sha(manifest));p.setProperty("root1Sha256",ref(raw("processing1")).sha256());p.setProperty("index1Sha256",pin);
        p.setProperty("pidA",Long.toString(ProcessHandle.current().pid()));p.setProperty("status","COMMIT_ACK");
        Files.write(handoff.resolveSibling("persisted-manifest.json"),manifest,StandardOpenOption.CREATE_NEW);write(handoff,p);
        result.putAll(p);System.out.println("B06_COMMIT_ACK task="+task+" input="+p.getProperty("input")+" digest="+digest);
    }
    static void recover(Path root,String pin,Path handoff,Properties result)throws Exception {
        outside();var p=read(handoff);var f=new ArchiveRestartFixture();var reader=new JdbcProcessingFactsReader(f.source);
        var snapshot=new ControlledConfigArchiveSource().open(root,pin,I,B,()->false);
        var facts=reader.readStored(actor(p.getProperty("owner")),p.getProperty("meeting"),p.getProperty("input"),B,()->false);
        byte[] original=input(facts).string("config_manifest").getBytes(StandardCharsets.UTF_8);
        boolean sameManifest=Arrays.equals(original,Files.readAllBytes(handoff.resolveSibling("persisted-manifest.json")));
        if(!sha(original).equals(p.getProperty("manifestSha256"))||!sameManifest)throw new AssertionError("old manifest changed");
        // Historical acquisition follows the unchanged persisted manifest, independently of defaults.
        var roots=new ProcessingManifestRefs(new StrictProcessingManifestReader()).roots(original,B,()->false);
        var archives=snapshot.loadClosure(roots,B,original.length,()->false);
        var verified=bindings().verifyProcessing(input(facts),facts,archives,B,()->false);
        if(!verified.fingerprint().equals(p.getProperty("digest"))||!verified.processing().equals(ref(raw("processing1"))))throw new AssertionError("old digest/root not recovered");
        if(facts.taskFacts().size()!=1||!facts.taskFacts().getFirst().taskId().equals(p.getProperty("task"))||!facts.taskFacts().getFirst().spec().equals(spec(p,p.getProperty("digest"))))throw new AssertionError("actual queued fullspec changed");
        var batch=reader.readBatch(actor(p.getProperty("owner")),p.getProperty("meeting"),p.getProperty("batch"),B,()->false);
        byte[] fresh=manifest(p,id(),ref(raw("processing2")));
        var newArchives=snapshot.loadClosure(List.of(ref(raw("processing2"))),B,fresh.length,()->false);
        var draft=bindings().computeProcessing(fresh,batch,newArchives,B,()->false);
        if(!draft.processing().equals(ref(raw("processing2")))||draft.fingerprint().equals(verified.fingerprint()))throw new AssertionError("new independent default draft");
        result.setProperty("status","RECOVERED");result.setProperty("oldDigest",verified.fingerprint());result.setProperty("oldManifestSha256",sha(original));result.setProperty("oldRootSha256",verified.processing().sha256());
        result.setProperty("task",p.getProperty("task"));result.setProperty("producerKey",facts.taskFacts().getFirst().spec().producerKey());result.setProperty("defaultRootVersion",draft.processing().version());result.setProperty("newDraftDigest",draft.fingerprint());result.setProperty("index2Sha256",snapshot.indexSha256());
        System.out.println("B06_RECOVERED task="+p.getProperty("task")+" oldDigest="+verified.fingerprint()+" default="+draft.processing().version());
    }
}
