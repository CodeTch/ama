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

    @Override
    public Download download(Actor actor, String id, String range) {
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
