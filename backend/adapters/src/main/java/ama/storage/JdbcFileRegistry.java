package ama.storage;

import static ama.contract.OutboxContracts.uuid;

import ama.access.MeetingAccess;
import ama.contract.FileContracts.*;
import ama.contract.IdentityContracts.Actor;
import ama.contract.StorageContracts.StorageKey;
import ama.db.TransactionRunner;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;

import javax.sql.DataSource;

/**
 * Authoritative references share initial B03 identity/root locks and the actual transaction holder.
 */
public final class JdbcFileRegistry implements FileRegistry {
    private final ama.contract.ConfigArchiveContracts.ArchiveReadBudget sourceBudget;
    private final DataSource source;
    private final JdbcTemplate j;
    private final TransactionRunner tx;
    private final MeetingAccess access;
    private final FilePurposePolicy purposes;
    private final FileBackendCatalog backends;
    private final Map<String, FileReferencePolicy> policies;
    private final Map<Object, java.lang.ref.WeakReference<Gate>> gates =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final class Gate implements LockedMeeting {
        final java.lang.ref.WeakReference<Object> transaction;
        final Actor actor;
        final String meeting;
        final MeetingAccess.Locked access;
        boolean filesStarted;

        Gate(Actor actor, String meeting, MeetingAccess.Locked access) {
            this.transaction = new java.lang.ref.WeakReference<>(transaction());
            this.actor = actor;
            this.meeting = meeting;
            this.access = access;
        }

        JdbcFileRegistry issuer() {
            return JdbcFileRegistry.this;
        }
    }

    private final class Proof implements LockedReference {
        final Gate gate;
        final Reference reference;
        final String file;
        final FileReferencePolicy policy;
        final Object domain;

        Proof(
                Gate gate,
                Reference reference,
                String file,
                FileReferencePolicy policy,
                Object domain) {
            this.gate = gate;
            this.reference = reference;
            this.file = file;
            this.policy = policy;
            this.domain = domain;
        }

        JdbcFileRegistry issuer() {
            return JdbcFileRegistry.this;
        }
    }

    private record Original(
            String id,
            String meeting,
            String purpose,
            long epoch,
            StorageKey key,
            long size,
            String hash) {}

    public JdbcFileRegistry(
            DataSource source,
            TransactionRunner tx,
            MeetingAccess access,
            FilePurposePolicy purposes,
            FileBackendCatalog backends,
            Map<String, FileReferencePolicy> policies) {
        this(source,tx,access,purposes,backends,policies,null);
    }

    public JdbcFileRegistry(DataSource source, TransactionRunner tx, MeetingAccess access,
            FilePurposePolicy purposes, FileBackendCatalog backends,
            Map<String,FileReferencePolicy> policies,
            ama.contract.ConfigArchiveContracts.ArchiveReadBudget sourceBudget) {
        this.sourceBudget=sourceBudget;
        this.source = source;
        j = new JdbcTemplate(source);
        this.tx = tx;
        this.access = access;
        this.purposes = purposes;
        this.backends = backends;
        var copy = new HashMap<String, FileReferencePolicy>();
        policies.forEach(
                (kind, policy) -> {
                    ama.contract.FileContracts.code(kind);
                    if (kind.equals("UPLOAD_SESSION") || policy == null)
                        throw new IllegalArgumentException("Reserved or invalid reference policy");
                    copy.put(kind, policy);
                });
        this.policies = Map.copyOf(copy);
    }

    @Override
    public LockedMeeting lock(Actor actor, String meeting, MeetingAccess.Action action) {
        uuid(meeting);
        Object holder = transaction();
        if (gates.containsKey(holder))
            throw new IllegalStateException("Initial registry prelock already acquired");
        var gate = new Gate(actor, meeting, access.lock(actor, meeting));
        access.require(gate.access, action);
        gates.put(holder, new java.lang.ref.WeakReference<>(gate));
        return gate;
    }

    @Override
    public MeetingAccess.Locked access(LockedMeeting held) {
        return gate(held).access;
    }

    @Override
    public LockedReference prelock(LockedMeeting held, Reference reference, String file) {
        var gate = gate(held);
        uuid(file);
        if (gate.filesStarted)
            throw new IllegalStateException("Domain prelock cannot follow file locks");
        var policy = policies.get(reference.kind());
        if (policy == null) throw new NotFound();
        Object domain = policy.prelock(gate, gate.meeting, reference, file);
        if (domain == null) throw new NotFound();
        return new Proof(gate, reference, file, policy, domain);
    }

    @Override
    public FileRecord resolve(String file) {
        uuid(file);
        var reference = gates.get(transaction());
        Gate gate = reference == null ? null : reference.get();
        if (gate == null)
            throw new IllegalStateException("Initial registry authorization required");
        gate(gate);
        FileRecord record = file(file, false);
        if (!gate.meeting.equals(record.meetingId())) throw new NotFound();
        return record;
    }

    @Override
    public void bind(LockedReference held, long expectedLocationRevision, String uploadId) {
        bindAll(List.of(new Binding(held, expectedLocationRevision, uploadId)));
    }

    @Override
    public void bindAll(List<Binding> bindings) {
        if (bindings == null || bindings.isEmpty())
            throw new IllegalArgumentException("Bindings required");
        var proofs = bindings.stream().map(b -> proof(b.reference())).toList();
        Gate gate = proofs.getFirst().gate;
        if (gate.filesStarted || proofs.stream().anyMatch(p -> p.gate != gate))
            throw new IllegalStateException("One batch per initial capability required");
        access.require(gate.access, MeetingAccess.Action.EDIT);
        writable(gate);
        var originals = new TreeMap<String, Original>();
        for (var proof : proofs) {
            var original = original(proof.file, false);
            if (!gate.meeting.equals(original.meeting())
                    || !gate.meeting.equals(file(proof.file, false).meetingId()))
                throw new NotFound();
            originals.put(proof.file, original);
        }
        // Complete sorted session set, then complete sorted file set, before any ledger change.
        for (var entry :
                originals.entrySet().stream()
                        .sorted(Comparator.comparing(e -> e.getValue().id()))
                        .toList()) {
            Original locked = original(entry.getKey(), true);
            if (!entry.getValue().equals(locked)) throw new Conflict();
        }
        gate.filesStarted = true;
        var files = new TreeMap<String, FileRecord>();
        for (String id : originals.keySet()) files.put(id, file(id, true));
        for (int i = 0; i < bindings.size(); i++)
            bind(
                    proofs.get(i),
                    bindings.get(i),
                    originals.get(proofs.get(i).file),
                    files.get(proofs.get(i).file));
    }

    private void bind(Proof proof, Binding binding, Original original, FileRecord file) {
        Gate gate = proof.gate;
        checkOriginal(gate, original, file);
        if (file.locationRevision() != binding.expectedLocationRevision()) throw new Conflict();
        String uploadId = binding.uploadId();
        if (uploadId != null && !original.id().equals(uploadId)) throw new Conflict();
        purposes.require(gate.actor, gate.meeting, original.purpose());
        proof.policy.validate(
                proof.domain, gate.meeting, proof.reference, file, original.purpose());
        if (!proof.reference.purpose().equals(original.purpose())) throw new Conflict();
        int changed = 0;
        if (j.queryForObject(
                        "SELECT COUNT(*) FROM file_reference WHERE file_id=? AND owner_kind=? AND"
                            + " owner_id=? AND purpose=?",
                        Integer.class,
                        file.fileId(),
                        proof.reference.kind(),
                        proof.reference.ownerId(),
                        proof.reference.purpose())
                == 0) {
            changed +=
                    j.update(
                            "INSERT INTO file_reference(file_id,owner_kind,owner_id,purpose) VALUES"
                                + " (?,?,?,?)",
                            file.fileId(),
                            proof.reference.kind(),
                            proof.reference.ownerId(),
                            proof.reference.purpose());
        }
        if (uploadId != null)
            changed +=
                    j.update(
                            "DELETE FROM file_reference WHERE file_id=? AND"
                                + " owner_kind='UPLOAD_SESSION' AND owner_id=? AND purpose=?",
                            file.fileId(),
                            uploadId,
                            original.purpose());
        if (changed > 0)
            j.update(
                    "UPDATE file_object SET reference_epoch=reference_epoch+1 WHERE id=?",
                    file.fileId());
    }

    private Download legacyDownload(Actor actor, String id, String range) {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Download owns its authorization transaction");
        uuid(id);
        var discovered =
                j.queryForList(
                        "SELECT meeting_id FROM file_object WHERE id=? AND state='AVAILABLE'",
                        String.class,
                        id);
        if (discovered.size() != 1 || discovered.getFirst() == null) throw new NotFound();
        FileRecord record =
                tx.required(
                        () -> {
                            Gate gate =
                                    (Gate)
                                            lock(
                                                    actor,
                                                    discovered.getFirst(),
                                                    MeetingAccess.Action.READ);
                            var references =
                                    j.query(
                                            "SELECT owner_kind,owner_id,purpose FROM file_reference"
                                                + " WHERE file_id=? ORDER BY"
                                                + " owner_kind,owner_id,purpose",
                                            (r, n) ->
                                                    new Reference(
                                                            r.getString(1),
                                                            r.getString(2),
                                                            r.getString(3)),
                                            id);
                            return legacyRead(gate,references,id);
                        });
        long[] interval = range(range, record.size());
        return new Download(
                record,
                backends.require(record.key().backendId(), false)
                        .storage()
                        .read(record.key(), interval[0], interval[1]),
                interval[0],
                interval[1]);
    }

    private FileRecord legacyRead(Gate gate,List<Reference> references,String id){
                            var proofs = new ArrayList<Proof>();
                            for (var reference : references)
                                if (policies.containsKey(reference.kind())) {
                                    try {
                                        proofs.add((Proof) prelock(gate, reference, id));
                                    } catch (NotFound invalid) {
                                        /* Another valid current reference may authorize this same file. */
                                    }
                                }
                            if (proofs.isEmpty()) throw new NotFound();
                            Original discoveredOriginal = original(id, false);
                            if (!gate.meeting.equals(discoveredOriginal.meeting()))
                                throw new NotFound();
                            Original original = original(id, true);
                            gate.filesStarted = true;
                            FileRecord file = file(id, true);
                            checkOriginal(gate, original, file);
                            boolean readable = false;
                            for (var proof : proofs) {
                                if (j.queryForObject(
                                                "SELECT COUNT(*) FROM file_reference WHERE"
                                                    + " file_id=? AND owner_kind=? AND owner_id=?"
                                                    + " AND purpose=?",
                                                Integer.class,
                                                id,
                                                proof.reference.kind(),
                                                proof.reference.ownerId(),
                                                proof.reference.purpose())
                                        != 1) continue;
                                if (!proof.reference.purpose().equals(original.purpose())) continue;
                                try {
                                    proof.policy.validate(
                                            proof.domain,
                                            gate.meeting,
                                            proof.reference,
                                            file,
                                            original.purpose());
                                    readable = true;
                                } catch (NotFound invalid) {
                                    /* fail this reference closed */
                                }
                            }
                            if (!readable) throw new NotFound();
                            return file;
    }

    private void checkOriginal(Gate gate, Original original, FileRecord file) {
        if (!file.state().equals("AVAILABLE")
                || !gate.meeting.equals(file.meetingId())
                || !original.meeting().equals(gate.meeting)
                || !original.key().equals(file.key())
                || original.size() != file.size()
                || !original.hash().equals(file.sha256())) throw new NotFound();
        long epoch =
                j.queryForObject(
                        "SELECT cancel_epoch FROM meeting WHERE id=?", Long.class, gate.meeting);
        if (epoch != original.epoch()) throw new Conflict();
    }

    private Original original(String file, boolean lock) {
        var rows =
                j.query(
                        "SELECT * FROM upload_session WHERE completed_file_id=? AND"
                            + " state='COMPLETED' ORDER BY id"
                                + (lock ? " FOR UPDATE" : ""),
                        (r, n) ->
                                new Original(
                                        r.getString("id"),
                                        r.getString("meeting_id"),
                                        r.getString("purpose"),
                                        r.getLong("cancel_epoch"),
                                        new StorageKey(
                                                r.getString("backend_id"),
                                                r.getString("namespace"),
                                                r.getString("object_key")),
                                        r.getLong("expected_size"),
                                        r.getString("expected_sha256")),
                        file);
        if (rows.size() != 1) throw new NotFound();
        return rows.getFirst();
    }

    private FileRecord file(String id, boolean lock) {
        return j
                .query(
                        "SELECT * FROM file_object WHERE id=?" + (lock ? " FOR UPDATE" : ""),
                        (r, n) ->
                                new FileRecord(
                                        r.getString("id"),
                                        r.getString("meeting_id"),
                                        new StorageKey(
                                                r.getString("backend_id"),
                                                r.getString("namespace"),
                                                r.getString("object_key")),
                                        r.getLong("size_bytes"),
                                        r.getString("sha256"),
                                        r.getString("state"),
                                        r.getLong("location_revision"),
                                        r.getLong("reference_epoch"),
                                        r.getString("display_name")),
                        id)
                .stream()
                .findFirst()
                .orElseThrow(NotFound::new);
    }

    @Override public LockedMeeting adopt(Actor actor,String meeting,MeetingAccess.Locked held,MeetingAccess.Action action){
        uuid(meeting);Object holder=transaction();
        if(gates.containsKey(holder))throw new IllegalStateException("Initial registry prelock already acquired");
        access.requirePrepared(held,actor,meeting,List.of(actor.enterpriseId()),action);
        var gate=new Gate(actor,meeting,held);gates.put(holder,new java.lang.ref.WeakReference<>(gate));return gate;
    }
    @Override public void bindAll(LockedSources held,List<Binding> bindings){
        // REQUIRED marks the real caller transaction rollback-only even if it catches the failure.
        tx.required(()->{
            Sources acquired=null;
            try {
                if(bindings==null||bindings.isEmpty()||sourceBudget==null||bindings.size()>sourceBudget.maxBatchFiles())throw new IllegalArgumentException("Bounded bindings required");
                var proofs=bindings.stream().map(b->proof(b.reference())).toList();var gate=proofs.getFirst().gate;
                if(proofs.stream().anyMatch(p->p.gate!=gate))throw new IllegalArgumentException("Different source gate");
                acquired=checkedSources(gate,held,proofs.stream().map(p->p.file).toList());
                acquired.phase=SourcePhase.BINDING;
                access.require(gate.access,MeetingAccess.Action.EDIT);writable(gate);
                for(int i=0;i<proofs.size();i++){
                    var p=proofs.get(i);bind(p,bindings.get(i),acquired.originals.get(p.file),acquired.files.get(p.file));
                }
                acquired.phase=SourcePhase.CONSUMED;return null;
            } catch(RuntimeException failure){
                if(held instanceof JdbcFileRegistry.Sources s&&s.issuer()==this)s.phase=SourcePhase.CONSUMED;
                throw failure;
            }
        });
    }
    @Override public LockedReference prelock(LockedMeeting held,Reference reference,String file,ReferenceContext context){
        var gate=gate(held);uuid(file);if(gate.filesStarted)throw new IllegalStateException("Domain prelock cannot follow sources");
        if(!(policies.get(reference.kind()) instanceof CompleteReadPolicy policy))throw new Unavailable();
        Object domain=policy.prelock(gate,gate.meeting,reference,file,context);
        if(domain==null)throw new NotFound();return new Proof(gate,reference,file,policy,domain);
    }
    private enum PreparedState { PREPARED, INELIGIBLE, FATAL }
    private record PreparedRead(CompleteReadPolicy policy,CompleteReadPolicy.ReadPreparation data,PreparedState state,RuntimeException failure) {}
    private PreparedRead prepare(Actor actor,String meeting,ReadRequest request){
        CompleteReadPolicy policy=null;
        try {
            if(sourceBudget==null||!(policies.get(request.referenceKind()) instanceof CompleteReadPolicy))throw new Unavailable();
            policy=(CompleteReadPolicy)policies.get(request.referenceKind());
            var data=policy.prepareRead(actor,meeting,request);if(data==null)throw new Unavailable();
            return new PreparedRead(policy,data,PreparedState.PREPARED,null);
        }catch(RuntimeException failure){
            cancellation(failure);
            return new PreparedRead(policy,null,failure instanceof NotFound&&request instanceof CurrentReadRequest?PreparedState.INELIGIBLE:PreparedState.FATAL,failure);
        }
    }
    private static void cancellation(RuntimeException failure){
        if(Thread.currentThread().isInterrupted())throw ama.contract.MinutesContracts.fail(ama.contract.MinutesContracts.FailureCode.CANCELLED);
        if(failure instanceof ama.contract.MinutesContracts.Failure f&&f.code()==ama.contract.MinutesContracts.FailureCode.CANCELLED)throw f;
    }
    private static String validateSelector(ReadRequest request){
        String file;
        if(request instanceof CurrentReadRequest current){ama.contract.FileContracts.code(current.referenceKind());file=current.fileId();}
        else if(request instanceof ama.transcript.ProcessingSourceReader.HistoryRequest history){uuid(history.revisionId());file=history.fileId();}
        else if(request instanceof ama.transcript.ProcessingSourceReader.WorkerRequest worker){Objects.requireNonNull(worker.lease());uuid(worker.inputId());file=worker.fileId();}
        else throw new IllegalArgumentException("Unknown complete-read selector");
        uuid(file);return file;
    }
    private void authorizeOperation(Gate gate,ReadRequest request,CompleteReadPolicy policy){
        // Fixed concrete final record types establish only a permission floor, never byte authority.
        if(request instanceof ama.transcript.ProcessingSourceReader.HistoryRequest||request instanceof ama.transcript.ProcessingSourceReader.WorkerRequest)
            access.require(gate.access,MeetingAccess.Action.EDIT);
        if(policy!=null)policy.authorizeRead(gate,gate.meeting,request);
    }
    private static void requirePrepared(PreparedRead prepared){
        if(prepared.state==PreparedState.FATAL)throw new Unavailable();
        if(prepared.state==PreparedState.INELIGIBLE)throw new NotFound();
    }
    @Override public Download read(Actor actor,String meeting,ReadRequest request,String range){
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Read owns its authorization transaction");
        Objects.requireNonNull(actor);uuid(meeting);String requested=validateSelector(request);ama.contract.MinutesContracts.check(()->false);
        var prepared=prepare(actor,meeting,request);
        FileRecord pinned=tx.required(()->{
            var gate=(Gate)lock(actor,meeting,MeetingAccess.Action.READ);authorizeOperation(gate,request,prepared.policy);requirePrepared(prepared);
            var policy=prepared.policy;var plan=policy.prelockRead(gate,meeting,request,prepared.data);
            if(plan==null||plan.context()==null||!requested.equals(plan.requestedFileId())||plan.completeMembers().isEmpty()||plan.completeMembers().size()>sourceBudget.maxBatchFiles())throw new Unavailable();
            var proofs=new ArrayList<Proof>();var unique=new HashSet<CompleteReadPolicy.ReadMember>();
            for(var member:plan.completeMembers()){
                if(!member.reference().kind().equals(request.referenceKind())||!unique.add(member))throw new Unavailable();
                var proof=(Proof)prelock(gate,member.reference(),member.fileId(),plan.context());
                if(proof.policy!=policy||proof.domain!=plan.context())throw new Unavailable();proofs.add(proof);
            }
            return completeRead(gate,proofs,requested);
        });
        return open(pinned,range);
    }
    @Override public Download download(Actor actor,String id,String range){
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Download owns its authorization transaction");
        if(policies.values().stream().noneMatch(p->p instanceof CompleteReadPolicy))return legacyDownload(actor,id,range);
        uuid(id);ama.contract.MinutesContracts.check(()->false);
        var meetings=j.queryForList("SELECT meeting_id FROM file_object WHERE id=? AND state='AVAILABLE'",String.class,id);
        if(meetings.size()!=1||meetings.getFirst()==null)throw new NotFound();String meeting=meetings.getFirst();
        List<Reference> discovered=List.of();PreparedRead prepared=null;CurrentReadRequest request=null;
        try {
            discovered=BoundedFileReferences.read(j,sourceBudget,id);CompleteReadPolicy provider=null;
            for(var reference:discovered)if(policies.get(reference.kind()) instanceof CompleteReadPolicy candidate){
                if(provider!=null&&provider!=candidate)throw new Unavailable();provider=candidate;
                if(request==null)request=new CurrentReadRequest(reference.kind(),id);
            }
            if(request!=null)prepared=prepare(actor,meeting,request);
        }catch(RuntimeException failure){cancellation(failure);prepared=new PreparedRead(null,null,PreparedState.FATAL,failure);}
        final var references=discovered;final var outcome=prepared;final var selector=request;
        FileRecord pinned=tx.required(()->{
            var gate=(Gate)lock(actor,meeting,MeetingAccess.Action.READ);
            if(selector!=null)authorizeOperation(gate,selector,outcome==null?null:outcome.policy);
            if(outcome!=null&&outcome.state==PreparedState.FATAL)throw new Unavailable();
            if(!BoundedFileReferences.read(j,sourceBudget,id).equals(references))throw new Unavailable();
            if(outcome==null||outcome.state==PreparedState.INELIGIBLE){
                var legacy=references.stream().filter(r->!(policies.get(r.kind()) instanceof CompleteReadPolicy)).toList();
                return legacyRead(gate,legacy,id);
            }
            return genericComplete(gate,references,id,outcome);
        });return open(pinned,range);
    }
    private Download open(FileRecord pinned,String range){
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Storage open must follow commit");
        ama.contract.MinutesContracts.check(()->false);
        long[] interval=range(range,pinned.size());
        return new Download(pinned,backends.require(pinned.key().backendId(),false).storage().read(pinned.key(),interval[0],interval[1]),interval[0],interval[1]);
    }
    private record ProofKey(Reference reference,String file) {}
    private FileRecord genericComplete(Gate gate,List<Reference> references,String requested,PreparedRead prepared){
        var proofs=new LinkedHashMap<ProofKey,Proof>();CompleteReadPolicy provider=null;
        for(var reference:references){
            var policy=policies.get(reference.kind());if(policy==null||proofs.containsKey(new ProofKey(reference,requested)))continue;
            try {
                Proof proof;
                if(policy instanceof CompleteReadPolicy complete){
                    if(complete!=prepared.policy)throw new Unavailable();
                    Object domain=complete.prelockCurrent(gate,gate.meeting,reference,requested,prepared.data);
                    if(domain==null)throw new NotFound();proof=new Proof(gate,reference,requested,complete,domain);
                }else proof=(Proof)prelock(gate,reference,requested);
                if(proofs.size()>=sourceBudget.maxFactRows())throw new Unavailable();
                proofs.put(new ProofKey(reference,requested),proof);
                if(policy instanceof CompleteReadPolicy complete){
                    if(provider!=null&&provider!=complete)throw new Unavailable();provider=complete;
                    if(!(proof.domain instanceof ReferenceContext context))throw new Unavailable();
                    var members=complete.readMembers(proof.domain,reference,requested);
                    if(members==null||members.isEmpty()||members.size()>sourceBudget.maxBatchFiles())throw new Unavailable();
                    for(var member:members){
                        if(policies.get(member.reference().kind())!=complete)throw new Unavailable();
                        var key=new ProofKey(member.reference(),member.fileId());if(proofs.containsKey(key))continue;
                        var added=(Proof)prelock(gate,member.reference(),member.fileId(),context);
                        if(added.domain!=proof.domain||proofs.size()>=sourceBudget.maxFactRows())throw new Unavailable();proofs.put(key,added);
                    }
                }
            } catch(NotFound denied){/* independently valid current references can still qualify */}
            if(proofs.size()>sourceBudget.maxFactRows())throw new Unavailable();
        }
        return completeRead(gate,new ArrayList<>(proofs.values()),requested);
    }
    private FileRecord completeRead(Gate gate,List<Proof> proofs,String requested){
        if(proofs.isEmpty())throw new NotFound();uuid(requested);
        CompleteReadPolicy provider=null;var domains=new IdentityHashMap<Object,List<Proof>>();
        var fileIds=new TreeSet<String>();
        for(var p:proofs){
            proof(p);fileIds.add(p.file);
            if(p.policy instanceof CompleteReadPolicy complete){
                if(provider!=null&&provider!=complete)throw new Unavailable();provider=complete;
                domains.computeIfAbsent(p.domain,k->new ArrayList<>()).add(p);
            }
        }
        var receipt=prelockSources(gate,List.copyOf(fileIds),false).orElseThrow(NotFound::new);
        var actual=sources(gate,receipt,List.copyOf(fileIds));var files=new HashMap<String,CompletedSource>();for(var a:actual)files.put(a.file().fileId(),a);
        boolean readable=false;var valid=new ArrayList<Object>();
        for(var p:proofs)if(!(p.policy instanceof CompleteReadPolicy)){
            try{validateReadReference(p,files.get(p.file));if(p.file.equals(requested))readable=true;}catch(NotFound denied){/* legacy alternative */}
        }
        for(var e:domains.entrySet()){
            try{for(var p:e.getValue())validateReadReference(p,files.get(p.file));valid.add(e.getKey());}catch(NotFound denied){/* this complete scope does not authorize */}
        }
        if(!valid.isEmpty()){
            provider.prelockReadTasks(List.copyOf(valid));
            // The provider has acquired its entire task union. No task discovery may follow an attempt.
            for(var scope:valid){
                var members=domains.get(scope);var scopedIds=new HashSet<String>();for(var p:members)scopedIds.add(p.file);
                try{
                    provider.finishRead(scope,actual.stream().filter(a->scopedIds.contains(a.file().fileId())).toList());
                    if(scopedIds.contains(requested))readable=true;
                }catch(NotFound denied){/* independently finalized alternative may still qualify */}
            }
        }
        if(!readable||!files.containsKey(requested))throw new NotFound();return displayName(files.get(requested).file());
    }
    private void validateReadReference(Proof p,CompletedSource actual){
        if(actual==null||!p.reference.purpose().equals(actual.originalPurpose())
                ||j.queryForObject("SELECT COUNT(*) FROM file_reference WHERE file_id=? AND owner_kind=? AND owner_id=? AND purpose=?",Integer.class,p.file,p.reference.kind(),p.reference.ownerId(),p.reference.purpose())!=1)throw new NotFound();
        p.policy.validate(p.domain,p.gate.meeting,p.reference,actual.file(),actual.originalPurpose());
    }
    private FileRecord displayName(FileRecord file){
        // Only an authorized browser/worker result needs a display name; source authentication never does.
        var state=new SourceAcquisition();measure("file_object",file.fileId(),List.of("display_name"),false,state,0);
        String name=j.query("SELECT display_name FROM file_object WHERE id=?",rs->{
            if(!rs.next())throw new NotFound();
            try(var reader=rs.getCharacterStream(1)){
                var text=new StringBuilder();char[] chunk=new char[Math.min(1024,sourceBudget.maxStringUtf16())];
                for(int n;(n=reader.read(chunk))!=-1;){state.check();if(n>sourceBudget.maxStringUtf16()-text.length())throw new Unavailable();text.append(chunk,0,n);}return text.toString();
            }catch(java.io.IOException failure){throw new Unavailable();}
        },file.fileId());
        return new FileRecord(file.fileId(),file.meetingId(),file.key(),file.size(),file.sha256(),file.state(),file.locationRevision(),file.referenceEpoch(),name);
    }

    private enum SourcePhase { SOURCES_HELD, BINDING, CONSUMED }
    private final class Sources implements LockedSources {
        final Gate gate;
        final SortedMap<String,Original> originals;
        final SortedMap<String,FileRecord> files;
        SourcePhase phase=SourcePhase.SOURCES_HELD;
        private Sources(Gate gate,SortedMap<String,Original> originals,SortedMap<String,FileRecord> files){
            this.gate=gate;this.originals=Collections.unmodifiableSortedMap(new TreeMap<>(originals));
            this.files=Collections.unmodifiableSortedMap(new TreeMap<>(files));
        }
        JdbcFileRegistry issuer(){return JdbcFileRegistry.this;}
    }
    private final class SourceAcquisition {
        long rows,bytes,scalars,edges;
        void check(){ama.contract.MinutesContracts.check(()->false);}
        void row(long edgeCount){check();if(++rows>sourceBudget.maxFactRows()||edgeCount>sourceBudget.maxFactEdges()-edges)throw new Unavailable();edges+=edgeCount;}
        void text(long octets,long points,long units){
            check();if(octets<0||points<0||units<0||octets>sourceBudget.maxStringUtf8Bytes()
                    ||units>sourceBudget.maxStringUtf16()||octets>sourceBudget.maxTotalBytes()-bytes
                    ||points>sourceBudget.maxAggregateScalars()-scalars)throw new Unavailable();
            bytes+=octets;scalars+=points;
        }
    }
    private SortedSet<String> completeIds(List<String> ids){
        if(sourceBudget==null)throw new Unavailable();
        if(ids==null||ids.isEmpty()||ids.size()>sourceBudget.maxBatchFiles())throw new IllegalArgumentException("Complete bounded source set required");
        var result=new TreeSet<String>();for(String id:ids){uuid(id);if(!result.add(id))throw new IllegalArgumentException("Duplicate source");}return result;
    }
    @Override public Optional<LockedSources> prelockSources(LockedMeeting held,List<String> ids,boolean skip){
        var gate=gate(held);var complete=completeIds(ids);
        if(gate.filesStarted)throw new IllegalStateException("Source acquisition already started");
        gate.filesStarted=true;
        var state=new SourceAcquisition();var associations=new TreeMap<String,String>();
        for(String id:complete){state.check();var found=originalIds(id);if(found.size()!=1)throw new NotFound();associations.put(id,found.getFirst());}
        var originals=new TreeMap<String,Original>();
        for(String upload:new TreeSet<>(associations.values())){
            if(!measure("upload_session",upload,List.of("id","meeting_id","purpose","backend_id","namespace","object_key","expected_sha256","state","completed_file_id"),skip,state,3))return Optional.empty();
            var rows=j.query("SELECT id,meeting_id,purpose,cancel_epoch,backend_id,namespace,object_key,expected_size,expected_sha256,state,completed_file_id FROM upload_session WHERE id=? FOR UPDATE",
                (r,n)->new Object[]{new Original(r.getString(1),r.getString(2),r.getString(3),exact(r,"cancel_epoch"),new StorageKey(r.getString(5),r.getString(6),r.getString(7)),exact(r,"expected_size"),r.getString(9)),r.getString(10),r.getString(11)},upload);
            if(rows.size()!=1)throw new NotFound();var row=rows.getFirst();var original=(Original)row[0];
            String file=(String)row[2];
            if(!"COMPLETED".equals(row[1])||!upload.equals(associations.get(file))||!gate.meeting.equals(original.meeting())||original.hash()==null)throw new NotFound();
            originals.put(file,original);
        }
        // Recheck complete associations after all original primary-key locks, never LIMIT 1.
        for(String id:complete)if(!originalIds(id).equals(List.of(associations.get(id))))throw new Conflict();
        var files=new TreeMap<String,FileRecord>();
        for(String id:complete){
            if(!measure("file_object",id,List.of("id","meeting_id","backend_id","storage_type","namespace","object_key","sha256","state"),skip,state,2))return Optional.empty();
            var rows=j.query("SELECT id,meeting_id,backend_id,namespace,object_key,size_bytes,sha256,state,location_revision,reference_epoch,storage_type FROM file_object WHERE id=? FOR UPDATE",
                (r,n)->{
                    var backend=backends.require(r.getString(3),false);
                    if(!backend.type().equals(r.getString(11)))throw new Unavailable();
                    return new FileRecord(r.getString(1),r.getString(2),new StorageKey(r.getString(3),r.getString(4),r.getString(5)),exact(r,"size_bytes"),r.getString(7),r.getString(8),exact(r,"location_revision"),exact(r,"reference_epoch"),null);
                },id);
            if(rows.size()!=1)throw new NotFound();var file=rows.getFirst();checkOriginal(gate,originals.get(id),file);
            if(file.referenceEpoch()>=Long.MAX_VALUE-1||file.locationRevision()>=Long.MAX_VALUE-1)throw new Conflict();files.put(id,file);
        }
        return Optional.of(new Sources(gate,originals,files));
    }
    private List<String> originalIds(String file){
        return j.queryForList("SELECT id FROM upload_session WHERE completed_file_id=? AND state='COMPLETED' ORDER BY id LIMIT 2",String.class,file);
    }
    private boolean measure(String table,String id,List<String> columns,boolean skip,SourceAcquisition state,long edges){
        String projection=String.join(",",columns.stream().map(c->"COALESCE(OCTET_LENGTH("+c+"),0),COALESCE(CHAR_LENGTH("+c+"),0),COALESCE(OCTET_LENGTH(CONVERT("+c+" USING utf16)) DIV 2,0)").toList());
        return j.query("SELECT "+projection+" FROM "+table+" WHERE id=? FOR UPDATE"+(skip?" SKIP LOCKED":""),rs->{
            state.check();if(!rs.next()){if(skip)return false;throw new NotFound();}state.row(edges);
            for(int i=0;i<columns.size();i++)state.text(rs.getLong(i*3+1),rs.getLong(i*3+2),rs.getLong(i*3+3));return true;
        },id);
    }
    private static long exact(java.sql.ResultSet rs,String column)throws java.sql.SQLException{
        try{return rs.getBigDecimal(column).longValueExact();}catch(ArithmeticException|NullPointerException failure){throw new Conflict();}
    }
    private Sources checkedSources(LockedMeeting expected,LockedSources held,List<String> ids){
        var gate=gate(expected);var complete=completeIds(ids);
        if(!(held instanceof JdbcFileRegistry.Sources sources)||sources.issuer()!=this||sources.gate!=gate
                ||sources.phase!=SourcePhase.SOURCES_HELD||!sources.files.keySet().equals(complete))throw new IllegalArgumentException("Foreign, consumed or incomplete sources");
        gate(sources.gate);return sources;
    }
    @Override public List<CompletedSource> sources(LockedMeeting expected,LockedSources held,List<String> ids){
        var sources=checkedSources(expected,held,ids);
        return sources.files.entrySet().stream().map(e->{var o=sources.originals.get(e.getKey());return new CompletedSource(e.getValue(),o.id(),o.purpose(),o.epoch());}).toList();
    }

    private void writable(Gate gate) {
        var row =
                j.queryForMap("SELECT ever_published,stage FROM meeting WHERE id=?", gate.meeting);
        if (((Number) row.get("ever_published")).intValue() != 0
                || row.get("stage").equals("PUBLISHED")) throw new Conflict();
    }

    private Gate gate(LockedMeeting held) {
        if (!(held instanceof JdbcFileRegistry.Gate gate)
                || gate.issuer() != this
                || gate.transaction.get() != transaction())
            throw new IllegalArgumentException("Foreign or expired registry capability");
        return gate;
    }

    private Proof proof(LockedReference held) {
        if (!(held instanceof JdbcFileRegistry.Proof proof) || proof.issuer() != this)
            throw new IllegalArgumentException("Foreign reference capability");
        gate(proof.gate);
        return proof;
    }

    private Object transaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(source))
            throw new IllegalStateException("Registry requires the same DataSource transaction");
        return TransactionSynchronizationManager.getResource(source);
    }

    private static long[] range(String value, long size) {
        if (value == null) return new long[] {0, size};
        try {
            if (!value.matches("bytes=[0-9]*-[0-9]*") || size == 0)
                throw new IllegalArgumentException();
            String[] ends = value.substring(6).split("-", -1);
            long first, last;
            if (ends[0].isEmpty()) {
                long suffix = Long.parseLong(ends[1]);
                if (suffix < 1) throw new IllegalArgumentException();
                first = Math.max(0, size - suffix);
                last = size - 1;
            } else {
                first = Long.parseLong(ends[0]);
                last = ends[1].isEmpty() ? size - 1 : Math.min(Long.parseLong(ends[1]), size - 1);
                if (first >= size || last < first) throw new IllegalArgumentException();
            }
            return new long[] {first, last - first + 1};
        } catch (IllegalArgumentException invalid) {
            throw new RangeFailure(size);
        }
    }

    public static final class RangeFailure extends RuntimeException {
        private final long size;

        public RangeFailure(long size) {
            super("Requested range unavailable");
            this.size = size;
        }

        public long size() {
            return size;
        }
    }
}
