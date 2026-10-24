package ama.transcript;

import ama.access.MeetingAccess;
import ama.canonical.CanonicalValue;
import ama.canonical.CanonicalValueCodec;
import ama.config.*;
import ama.config.ConfigArchiveIndex.IndexBudget;
import ama.config.json.*;
import ama.contract.ConfigArchiveContracts.*;
import ama.contract.ConfigArchiveContracts.Ref;
import ama.contract.IdentityContracts.*;
import ama.contract.MinutesContracts.*;
import ama.contract.TaskContracts.*;
import ama.db.TransactionRunner;
import ama.meeting.JdbcMeetingAccess;
import ama.support.MysqlTestDatabase;
import ama.task.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static ama.canonical.CanonicalValue.*;

/** New matching-current metadata only. Never constructs the historical direct-insert Task3 fixture. */
final class ArchiveRestartFixture {
    static final ArchiveReadBudget B=new ArchiveReadBudget(65536,1048576,16,32,8,200000,32,255,65536,262144,1048576,65536,32,250000,500000);
    static final IndexBudget I=new IndexBudget(4194304,4096,67108864,128);
    static final CanonicalBudget C=new CanonicalBudget(16777216,32,1000000,2097152,250000);
    static final String TYPE="B06_RESTART_TEST",POOL="B06_RESTART_TEST",FILE_SHA=sha("A".getBytes(StandardCharsets.UTF_8));
    static final long VERSION=20,EPOCH=5;
    final DataSource source=MysqlTestDatabase.dataSource();
    final JdbcTemplate j=new JdbcTemplate(source);
    final TransactionRunner tx=new TransactionRunner(source);
    final JdbcSubjectFences fences=new JdbcSubjectFences(source,Set.of("MEETING"));
    final JdbcMeetingAccess access=new JdbcMeetingAccess(source,tx,fences);

    static String id(){return UUID.randomUUID().toString();}
    static void outside(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new AssertionError("Archive/draft work inside transaction");}
    static String sha(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new AssertionError(e);}}
    static ConfigBindings bindings(){outside();return new ConfigBindings(new StrictConfigArchiveReader(),new StrictProcessingManifestReader());}
    static Actor actor(String owner){return new Actor(owner,Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER));}
    static Ref ref(RawArchive a){return new Ref(a.claimedArtifactType(),a.claimedVersion(),sha(a.bytes()));}
    static byte[] literal(String name)throws IOException{
        outside();try(var in=Objects.requireNonNull(ArchiveRestartFixture.class.getResourceAsStream("/ama/transcript/restart/"+name))){
            byte[] bytes=in.readNBytes(65537);if(bytes.length>65536)throw new AssertionError("oversize test literal");return bytes;
        }
    }
    static RawArchive raw(String name)throws IOException{
        ArtifactType type=switch(name){case "codec"->ArtifactType.CANONICAL_CODEC;case "confirmation"->ArtifactType.CONFIRMATION_HASH;case "parser"->ArtifactType.PARSER_CONFIG;default->ArtifactType.PROCESSING_CONFIG;};
        String version=switch(name){case "codec"->"ama.canonical-value.v1";case "confirmation"->"ama.transcript-confirmation.v1";case "parser"->"ama.strict-utf8-lines.v1";default->"b06.controlled.import-txt."+name.substring(10);};
        return new RawArchive(type,version,literal(name+".json"));
    }
    static List<RawArchive> bundle()throws IOException{return List.of(raw("codec"),raw("confirmation"),raw("parser"),raw("processing1"));}
    static CanonicalValue obj(Object... pairs){var entries=new ArrayList<Entry>();for(int n=0;n<pairs.length;n+=2)entries.add(field((String)pairs[n],value(pairs[n+1])));return new ObjectValue(entries);}
    static CanonicalValue value(Object v){
        if(v==null)return nil();if(v instanceof CanonicalValue c)return c;if(v instanceof String s)return string(s);if(v instanceof Number n)return number(n.longValue());
        if(v instanceof List<?> list)return new Sequence(list.stream().map(ArchiveRestartFixture::value).toList());throw new AssertionError("fixture value");
    }
    static CanonicalValue wire(Ref r){return obj("artifactType",r.artifactType().name(),"version",r.version(),"sha256",r.sha256());}
    static byte[] manifest(Properties p,String input,Ref processing)throws IOException{
        outside();Ref parser=ref(raw("parser")),codec=ref(raw("codec")),confirmation=ref(raw("confirmation"));
        return CanonicalJson.encode(obj("schemaVersion",1,"profile","ama.transcription-input.v1","meetingId",p.getProperty("meeting"),"inputId",input,"kind","IMPORT",
            "batch",obj("id",p.getProperty("batch"),"kind","TRANSCRIPT","files",List.of(obj("ordinal",1,"fileId",p.getProperty("file"),"sha256",FILE_SHA,"sizeBytes",1))),
            "sourceRevisionId",null,"materials",obj("mode","EMPTY","items",List.of()),"configs",obj("processing",wire(processing),"parser",wire(parser),"model",null,"prompt",null,
                "profiles",List.of(obj("kind","CANONICAL_CODEC","ref",wire(codec)),obj("kind","CONFIRMATION_HASH","ref",wire(confirmation)))),
            "base",obj("meetingRevision",VERSION,"subjectBusinessVersion",VERSION,"cancelEpoch",EPOCH),"steps",List.of(obj("ordinal",1,"kind","IMPORT_TXT","parentInput",null,"priorOutput",null,
                "source",obj("kind","INPUT_BATCH","id",p.getProperty("batch")),"config",wire(processing),"parser",wire(parser),"model",null,"prompt",null))),65536,()->false);
    }
    Properties seed(){
        outside();var p=new Properties();for(String key:List.of("meeting","batch","input","file"))p.setProperty(key,id());p.setProperty("owner","b06-restart-"+id());p.setProperty("backend","B06_RESTART_"+id());
        String batchHash=new BatchFingerprint().compute(p.getProperty("meeting"),p.getProperty("batch"),"TRANSCRIPT",VERSION,List.of(new BatchFingerprint.Member(1,p.getProperty("file"),FILE_SHA,1)),C,()->false);
        p.setProperty("batchFingerprint",batchHash);
        tx.required(()->{
            j.update("INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",p.getProperty("owner"),p.getProperty("owner"));
            fences.create(new SubjectRef("MEETING",p.getProperty("meeting")),VERSION,EPOCH);
            j.update("INSERT INTO meeting(id,owner_id,revision,cancel_epoch,stage,created_at,updated_at) VALUES (?,?,?,?,'TRANSCRIPT_PENDING',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",p.getProperty("meeting"),p.getProperty("owner"),VERSION,EPOCH);
            j.update("INSERT INTO storage_backend(id,storage_type,status,config_revision) VALUES (?,'local','READ_ONLY',1)",p.getProperty("backend"));
            j.update("INSERT INTO file_object(id,meeting_id,backend_id,storage_type,namespace,object_key,display_name,size_bytes,sha256,state,created_at) VALUES (?,?,?,'local','CONTROLLED',?,'A.txt',1,?,'AVAILABLE',UTC_TIMESTAMP(6))",p.getProperty("file"),p.getProperty("meeting"),p.getProperty("backend"),p.getProperty("file"),FILE_SHA);
            j.update("INSERT INTO input_batch(id,meeting_id,kind,input_fingerprint,base_revision,created_at) VALUES (?,?,'TRANSCRIPT',?,?,UTC_TIMESTAMP(6))",p.getProperty("batch"),p.getProperty("meeting"),batchHash,VERSION);
            j.update("INSERT INTO input_batch_file(batch_id,ordinal,file_id,sha256) VALUES (?,1,?,?)",p.getProperty("batch"),p.getProperty("file"),FILE_SHA);return null;
        });return p;
    }
    static TaskSpec spec(Properties p,String fingerprint){
        outside();var data=obj("taskType",TYPE,"meetingId",p.getProperty("meeting"),"inputId",p.getProperty("input"),"inputFingerprint",fingerprint,"businessVersion",VERSION,"cancelEpoch",EPOCH);
        String producer=new CanonicalValueCodec().sha256(obj("profile","ama.b06-restart-test-producer.v1","kind","TASK_PRODUCER","data",data),C,()->false);
        return new TaskSpec(TYPE,POOL,new SubjectRef("MEETING",p.getProperty("meeting")),producer,new TypedRef("TRANSCRIPTION_INPUT",p.getProperty("input")),fingerprint,VERSION,0,EPOCH,3);
    }
    String accept(Properties p,byte[] manifest,String fingerprint){
        outside();String exact=new String(manifest,StandardCharsets.UTF_8);var spec=spec(p,fingerprint);var policy=new TestPolicy(p,spec);
        var outbox=new JdbcOutbox(source,Map.of("TRANSCRIPTION_INPUT",e->{if(!e.subject().equals(spec.subject())||!e.reference().id().equals(spec.input().id())||e.subjectRevision()!=VERSION)throw new AssertionError("outbox provenance");policy.validateInput(spec);}));
        var queue=new JdbcTaskQueue(source,fences,outbox,Map.of(TYPE,policy));
        return tx.required(()->{
            var held=access.lock(actor(p.getProperty("owner")),p.getProperty("meeting"));access.require(held,MeetingAccess.Action.EDIT);recheck(p);
            j.update("INSERT INTO transcription_input(id,meeting_id,batch_id,kind,material_mode,processing_config_version,parser_version,manifest_schema_version,config_manifest,input_fingerprint,base_revision,cancel_epoch,created_at) VALUES (?,?,?,'IMPORT','EMPTY','b06.controlled.import-txt.1','ama.strict-utf8-lines.v1',1,?,?,?,?,UTC_TIMESTAMP(6))",p.getProperty("input"),p.getProperty("meeting"),p.getProperty("batch"),exact,fingerprint,VERSION,EPOCH);
            String task=queue.enqueue(spec,policy.adopt(held));outbox.flush();return task;
        }); // This return is the actual outer commit ACK boundary, never an inner callback marker.
    }
    void recheck(Properties p){
        var root=j.queryForMap("SELECT owner_id,revision,cancel_epoch FROM meeting WHERE id=? FOR UPDATE",p.getProperty("meeting"));
        if(!root.get("owner_id").equals(p.getProperty("owner"))||((Number)root.get("revision")).longValue()!=VERSION||((Number)root.get("cancel_epoch")).longValue()!=EPOCH)throw new AssertionError("current fence");
        var batch=j.queryForMap("SELECT meeting_id,kind,input_fingerprint,base_revision FROM input_batch WHERE id=? FOR UPDATE",p.getProperty("batch"));
        if(!batch.get("meeting_id").equals(p.getProperty("meeting"))||!batch.get("kind").equals("TRANSCRIPT")||!batch.get("input_fingerprint").equals(p.getProperty("batchFingerprint"))||((Number)batch.get("base_revision")).longValue()!=VERSION)throw new AssertionError("batch changed");
        var members=j.queryForList("SELECT ordinal,file_id,sha256 FROM input_batch_file WHERE batch_id=? ORDER BY ordinal FOR UPDATE",p.getProperty("batch"));
        if(members.size()!=1||((Number)members.getFirst().get("ordinal")).longValue()!=1||!members.getFirst().get("file_id").equals(p.getProperty("file"))||!members.getFirst().get("sha256").equals(FILE_SHA))throw new AssertionError("members changed");
        var file=j.queryForMap("SELECT meeting_id,sha256,size_bytes FROM file_object WHERE id=? FOR UPDATE",p.getProperty("file"));
        if(!file.get("meeting_id").equals(p.getProperty("meeting"))||!file.get("sha256").equals(FILE_SHA)||((Number)file.get("size_bytes")).longValue()!=1)throw new AssertionError("file changed");
    }
    final class TestPolicy implements TaskDomainPolicy {
        final Properties p;final TaskSpec spec;final JdbcTaskPreparations preparations=new JdbcTaskPreparations(source);
        TestPolicy(Properties p,TaskSpec spec){this.p=p;this.spec=spec;}
        PreparedDomain adopt(MeetingAccess.Locked held){access.requirePrepared(held,actor(p.getProperty("owner")),p.getProperty("meeting"),List.of(p.getProperty("owner")),MeetingAccess.Action.EDIT);return preparations.issue(List.of(spec),Acquisition.TASK_MUTATION,held,true);}
        public void requirePrepared(List<TaskSpec> specs,Acquisition purpose,PreparedDomain held){preparations.require(specs,purpose,held);access.requirePrepared((MeetingAccess.Locked)preparations.value(held),actor(p.getProperty("owner")),p.getProperty("meeting"),List.of(p.getProperty("owner")),MeetingAccess.Action.EDIT);}
        public boolean permitsDomainEffects(PreparedDomain held){return preparations.permitsEffects(held);}
        public boolean lockRoot(TaskSpec s,boolean skip){throw new AssertionError("explicit authentic adoption required");}
        public boolean lockRoot(TaskSpec s,boolean skip,PreparedDomain held){requirePrepared(List.of(s),Acquisition.TASK_MUTATION,held);recheck(p);return true;}
        public void validateInput(TaskSpec s){
            if(!s.equals(spec))throw new AssertionError("full spec mismatch");
            var row=j.queryForMap("SELECT meeting_id,batch_id,kind,material_mode,processing_config_version,parser_version,manifest_schema_version,input_fingerprint,base_revision,cancel_epoch,source_revision_id,material_snapshot_id,model_config_version,prompt_version FROM transcription_input WHERE id=?",s.input().id());
            if(!row.get("meeting_id").equals(s.subject().id())||!row.get("batch_id").equals(p.getProperty("batch"))||!row.get("kind").equals("IMPORT")||!row.get("material_mode").equals("EMPTY")||!row.get("processing_config_version").equals("b06.controlled.import-txt.1")||!row.get("parser_version").equals("ama.strict-utf8-lines.v1")||((Number)row.get("manifest_schema_version")).longValue()!=1||!row.get("input_fingerprint").equals(s.inputFingerprint())||((Number)row.get("base_revision")).longValue()!=s.businessVersion()||((Number)row.get("cancel_epoch")).longValue()!=s.cancelEpoch())throw new AssertionError("stored input provenance");
            for(String nullable:List.of("source_revision_id","material_snapshot_id","model_config_version","prompt_version"))if(row.get(nullable)!=null)throw new AssertionError("unexpected nullable input");
        }
        public void validateOutput(TaskSpec s,TaskResult r){throw new AssertionError("No worker/output qualification");}
        public void validateExternalRequest(TaskSpec s,ama.contract.ExternalContracts.ExternalRequest r){throw new AssertionError("No external qualification");}
    }
    static FullRow input(StoredProvenanceFacts facts){return facts.rows().stream().filter(r->r.table()==Table.TRANSCRIPTION_INPUT).findFirst().orElseThrow();}
    static Properties read(Path path)throws IOException{var p=new Properties();try(var in=Files.newInputStream(path)){p.load(in);}return p;}
    static void write(Path path,Properties p)throws IOException{outside();try(var out=Files.newOutputStream(path,StandardOpenOption.CREATE_NEW)){p.store(out,"B06 controlled public metadata handshake");}}
}
