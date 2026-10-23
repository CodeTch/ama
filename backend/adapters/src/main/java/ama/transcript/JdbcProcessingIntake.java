package ama.transcript;
import ama.access.MeetingAccess;
import ama.audit.AuditPort;
import ama.contract.AuditContracts.AuditEvent;
import ama.contract.IdentityContracts.Actor;
import ama.contract.OutboxContracts.*;
import ama.contract.ProcessingContracts.*;
import ama.db.TransactionRunner;
import ama.storage.*;
import ama.task.*;
import java.io.*;
import java.nio.charset.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static ama.contract.ProcessingContracts.*;
/** One transaction for actual input, handoff, CAS and enqueue. Historical replay needs no intake configuration. */
public final class JdbcProcessingIntake {
    @FunctionalInterface public interface NewIntake {
        JdbcTranscriptAuthority.PreparedIntake prepare(Actor actor,String meeting,ImportRequest request);
    }
    private record Replay(String scope,String task,int status,boolean matches) {
    }
    private record ReplayProof(ama.contract.TaskContracts.TaskSpec spec,String producer) {
    }
    private static final ama.contract.MinutesContracts.CanonicalBudget REPLAY_PRODUCER_BUDGET=new ama.contract.MinutesContracts.CanonicalBudget(65536,8,128,1024,4096);
    private final JdbcTemplate j;
    private final TransactionRunner tx;
    private final MeetingAccess access;
    private final JdbcTranscriptAuthority.Components components;
    private final SubjectFencePort fences;
    private final TaskQueue queue;
    private final OutboxPort outbox;
    private final AuditPort audit;
    private final NewIntake preparer;
    public JdbcProcessingIntake(DataSource source,TransactionRunner tx,MeetingAccess access,JdbcTranscriptAuthority.Components components, SubjectFencePort fences,TaskQueue queue,OutboxPort outbox,AuditPort audit,NewIntake preparer) {
        j=new JdbcTemplate(source);
        j.setQueryTimeout(5);
        this.tx=tx;
        this.access=access;
        this.components=components;
        this.fences=fences;
        this.queue=queue;
        this.outbox=outbox;
        this.audit=audit;
        this.preparer=Objects.requireNonNull(preparer);
    }
    public ImportAccepted accept(Actor actor,String meeting,String key,ImportRequest request,String trace) {
        independent();
        ama.contract.OutboxContracts.uuid(meeting);
        key(key);
        String scope=digest(actor.enterpriseId(),meeting,"PROCESSING_IMPORT",key),keyHash=digest(key);
        var fields=new ArrayList<String>();
        Collections.addAll(fields,"transcript-import-request-v1",Long.toString(request.expectedRevision()),Integer.toString(request.orderedFileIds().size()));
        fields.addAll(request.orderedFileIds());
        String body=digest(fields.toArray(String[]::new));
        var replay=lookup(actor,meeting,scope,keyHash,body);
        if(replay.isPresent())return replay.orElseThrow();
        JdbcTranscriptAuthority.PreparedIntake prepared;
        try {
            prepared=preparer.prepare(actor,meeting,request);
        }
        catch(ama.contract.MinutesContracts.Failure failure) {
            // Cancellation is not an ordinary preparation failure and must never become an acceptance.
            if(Thread.currentThread().isInterrupted()||!Set.of(ama.contract.MinutesContracts.FailureCode.PROFILE_UNAVAILABLE,ama.contract.MinutesContracts.FailureCode.CAPABILITY_UNAVAILABLE,ama.contract.MinutesContracts.FailureCode.BUDGET_EXCEEDED,ama.contract.MinutesContracts.FailureCode.READ_FAILED,ama.contract.MinutesContracts.FailureCode.PROVENANCE_UNAVAILABLE).contains(failure.code()))throw failure;
            var winner=lookup(actor,meeting,scope,keyHash,body);
            if(winner.isPresent())return winner.orElseThrow();
            throw unavailable();
        }
        catch(ama.contract.FileContracts.NotFound|ama.contract.FileContracts.Conflict failure) {
            var winner=lookup(actor,meeting,scope,keyHash,body);
            if(winner.isPresent())return winner.orElseThrow();
            throw conflict();
        }
        catch(ama.contract.FileContracts.Unavailable failure) {
            var winner=lookup(actor,meeting,scope,keyHash,body);
            if(winner.isPresent())return winner.orElseThrow();
            throw unavailable();
        }
        catch(Failure failure) {
            if(failure.status()!=409&&failure.status()!=503&&failure.status()!=413)throw failure;
            var winner=lookup(actor,meeting,scope,keyHash,body);
            if(winner.isPresent())return winner.orElseThrow();
            throw failure;
        }
        return tx.required(()-> {
            j.update("INSERT INTO idempotency_record(scope_hash,actor_id,resource_id,operation,key_hash,request_hash,state,http_status,created_at) VALUES (?,?,?,'PROCESSING_IMPORT',?,?,'SUCCEEDED',0,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE scope_hash=idempotency_record.scope_hash",scope,actor.enterpriseId(),meeting,keyHash,body);
            var claimed=record(actor,meeting,scope,keyHash,body).orElseThrow(ama.contract.ProcessingContracts::unavailable);
            if(claimed.task()!=null) {
                var proof=observe(claimed);
                access.require(access.lock(actor,meeting),MeetingAccess.Action.EDIT);
                match(claimed);
                return restore(meeting,claimed,proof);
            }
            var c=Objects.requireNonNull(components);
            var registry=c.registry();
            var gate=registry.lock(actor,meeting,MeetingAccess.Action.EDIT);
            match(claimed);
            long expected=request.expectedRevision();
            if(expected==Long.MAX_VALUE-1)throw new Failure(409,"REVISION_EXHAUSTED");
            if(prepared.spec().businessVersion()!=expected+1)throw conflict();
            var subject=fences.lock(prepared.spec().subject(),false).orElseThrow(ama.contract.ProcessingContracts::unavailable);
            if(subject.businessVersion()!=expected||subject.cancelEpoch()!=prepared.spec().cancelEpoch()||subject.deleted())throw conflict();
            var authority=c.authority();
            var reserved=authority.reserveIntake(gate,prepared);
            var bindings=new ArrayList<FileRegistry.Binding>();
            for(var original:authority.originals(prepared))bindings.add(new FileRegistry.Binding(registry.prelock(gate,new FileRegistry.Reference(JdbcTranscriptAuthority.REFERENCE,prepared.batchId(),JdbcTranscriptAuthority.PURPOSE),original.file().fileId(),reserved),original.file().locationRevision(),original.uploadId()));
            var receipt=registry.prelockSources(gate,request.orderedFileIds(),false).orElseThrow(ama.contract.ProcessingContracts::unavailable);
            var held=authority.insertAndReadNew(reserved,receipt);
            registry.bindAll(receipt,bindings);
            authority.requireHeld(held,gate,prepared.spec());
            if(j.update("UPDATE meeting SET revision=?,transcript_confirmation_id=NULL,final_confirmation_id=NULL,needs_generation=1,updated_at=UTC_TIMESTAMP(6) WHERE id=? AND revision=? AND cancel_epoch=? AND ever_published=0 AND stage<>'PUBLISHED' AND lifecycle='ACTIVE' AND governance_state='ACTIVE'",expected+1,meeting,expected,prepared.spec().cancelEpoch())!=1)throw conflict();
            if(j.update("UPDATE meeting_search_projection SET meeting_revision=? WHERE meeting_id=?",expected+1,meeting)!=1)throw unavailable();
            fences.advance(subject,expected+1,prepared.spec().cancelEpoch(),false);
            String task=queue.enqueue(prepared.spec(),authority.adoptCreated(gate,held,prepared.spec()));
            if(j.update("UPDATE idempotency_record SET result_id=?,http_status=202 WHERE scope_hash=? AND result_id IS NULL",task,scope)!=1)throw unavailable();
            var result=new ImportAccepted(meeting,prepared.batchId(),prepared.inputId(),task,expected+1,Long.toString(expected+1),prepared.spec().inputFingerprint());
            audit.append(new AuditEvent(actor.enterpriseId(),"TRANSCRIPT_IMPORT_ACCEPTED",meeting,expected+1,"SUCCEEDED",trace,Instant.now()));
            outbox.append(new EventDraft("TRANSCRIPT_IMPORT_ACCEPTED",task,prepared.spec().subject(),expected+1,new Reference("TRANSCRIPTION_INPUT",prepared.inputId())));
            outbox.flush();
            return result;
        });
    }
    private Optional<ImportAccepted> lookup(Actor actor,String meeting,String scope,String keyHash,String hash) {
        return tx.required(()-> {
            var existing=record(actor,meeting,scope,keyHash,hash);
            var proof=existing.isPresent()?observe(existing.orElseThrow()):null;
            access.require(access.lock(actor,meeting),MeetingAccess.Action.EDIT);
            if(existing.isEmpty())return Optional.empty();
            var replay=existing.orElseThrow();
            match(replay);
            if(replay.task()==null)throw unavailable();
            return Optional.of(restore(meeting,replay,proof));
        });
    }
    private Optional<Replay> record(Actor actor,String meeting,String scope,String keyHash,String hash) {
        return j.query("SELECT actor_id,resource_id,operation,key_hash,request_hash,result_id,http_status,state FROM idempotency_record WHERE scope_hash=? FOR UPDATE",(r,n)->new Replay(scope,r.getString(6),r.getInt(7),actor.enterpriseId().equals(r.getString(1))&&meeting.equals(r.getString(2))&&"PROCESSING_IMPORT".equals(r.getString(3))&&keyHash.equals(r.getString(4))&&hash.equals(r.getString(5))&&"SUCCEEDED".equals(r.getString(8))),scope).stream().findFirst();
    }
    private static void match(Replay replay) {
        if(!replay.matches())throw conflict();
    }
    private ReplayProof observe(Replay replay) {
        if(!replay.matches()||replay.task()==null)return null;
        try {
            var specs=taskSpec(replay.task());
            if(specs.size()!=1)return null;
            var spec=specs.getFirst();
            String producer=TranscriptImportProducer.compute(spec.subject().id(),spec.input().id(),spec.inputFingerprint(),spec.businessVersion(),spec.cancelEpoch(),REPLAY_PRODUCER_BUDGET,()->false);
            return new ReplayProof(spec,producer);
        }
        catch(ama.contract.MinutesContracts.Failure failure) {
            if(failure.code()==ama.contract.MinutesContracts.FailureCode.CANCELLED)throw failure;
            return null;
        }
        catch(IllegalArgumentException corrupt) {
            return null;
        }
    }
    private List<ama.contract.TaskContracts.TaskSpec> taskSpec(String task) {
        return j.query("SELECT task_type,pool,subject_kind,subject_id,producer_key,input_kind,input_id,input_fingerprint,business_version,priority,cancel_epoch,max_attempts FROM durable_task WHERE id=?",(r,n)->new ama.contract.TaskContracts.TaskSpec(r.getString(1),r.getString(2),new ama.contract.TaskContracts.SubjectRef(r.getString(3),r.getString(4)),r.getString(5),new ama.contract.TaskContracts.TypedRef(r.getString(6),r.getString(7)),r.getString(8),r.getLong(9),r.getInt(10),r.getLong(11),r.getInt(12)),task);
    }
    private ImportAccepted restore(String meeting,Replay replay,ReplayProof proof) {
        if(proof==null||!proof.producer().equals(proof.spec().producerKey())||!taskSpec(replay.task()).equals(List.of(proof.spec())))throw unavailable();
        if(replay.status()!=202)throw unavailable();
        var results=j.query("SELECT t.id,i.id,i.batch_id,i.base_revision,i.input_fingerprint FROM durable_task t JOIN transcription_input i ON i.id=t.input_id JOIN input_batch b ON b.id=i.batch_id JOIN input_batch_fingerprint_profile p ON p.batch_id=b.id WHERE t.id=? AND t.task_type='TRANSCRIPT_IMPORT_V1' AND t.pool='TRANSCRIPT_IMPORT' AND t.subject_kind='MEETING' AND t.subject_id=? AND t.input_kind='TRANSCRIPTION_INPUT' AND i.meeting_id=t.subject_id AND b.meeting_id=i.meeting_id AND b.kind='TRANSCRIPT' AND i.kind='IMPORT' AND i.material_mode='EMPTY' AND b.base_revision=i.base_revision AND t.business_version=i.base_revision AND t.cancel_epoch=i.cancel_epoch AND t.input_fingerprint=i.input_fingerprint AND t.priority=0 AND t.max_attempts=3 AND p.profile='ama.input-batch.v1'",(r,n)->new ImportAccepted(meeting,r.getString(3),r.getString(2),r.getString(1),r.getLong(4),r.getString(4),r.getString(5)),replay.task(),meeting);
        if(results.size()!=1)throw unavailable();
        var accepted=results.getFirst();
        if(j.queryForObject("SELECT COUNT(*) FROM durable_task WHERE input_kind='TRANSCRIPTION_INPUT' AND input_id=?",Long.class,accepted.inputId())!=1)throw unavailable();
        var rows=j.queryForList("SELECT ordinal FROM input_batch_file WHERE batch_id=? ORDER BY ordinal LIMIT 65",Long.class,accepted.batchId());
        if(rows.isEmpty()||rows.size()>64)throw unavailable();
        for(int n=0;n<rows.size();n++)if(rows.get(n)!=n+1L)throw unavailable();
        return accepted;
    }
    public static void key(String key) {
        if(key==null||key.isBlank()||encode(key).length>256)throw new IllegalArgumentException("Invalid idempotency key");
    }
    private static byte[] encode(String value) {
        try {
            var buffer=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(value));
            var bytes=new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        }
        catch(CharacterCodingException invalid) {
            throw new IllegalArgumentException("Invalid Unicode");
        }
    }
    static String digest(String... values) {
        try {
            var bytes=new ByteArrayOutputStream();
            var out=new DataOutputStream(bytes);
            for(String value:values) {
                var b=encode(value);
                out.writeInt(b.length);
                out.write(b);
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        }
        catch(IOException|NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
    private static void independent() {
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Standalone processing intake required");
    }
}
