package ama.storage;

import static ama.contract.FileContracts.hash;
import static ama.contract.OutboxContracts.uuid;

import ama.access.MeetingAccess;
import ama.audit.AuditPort;
import ama.contract.AuditContracts.AuditEvent;
import ama.contract.FileContracts.*;
import ama.contract.IdentityContracts.Actor;
import ama.contract.StorageContracts.*;
import ama.db.TransactionRunner;
import ama.task.OutboxPort;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

import javax.sql.DataSource;

/** V009 upload protocol. No storage call is made in a database transaction. */
public final class JdbcFileUploads implements FileUploads {
    private final JdbcTemplate j;
    private final TransactionRunner tx;
    private final MeetingAccess access;
    private final FilePurposePolicy purposes;
    private final FileBackendCatalog backends;
    private final AuditPort audit;
    private final OutboxPort outbox;
    private final long partSize;
    private final ama.transcript.PublicationWriteGuard publicationWrites;

    private record Root(long epoch,long revision,boolean published,MeetingAccess.Locked gate,
                        ama.transcript.PublicationWriteGuard.Held publication,RuntimeException guardFailure) {}
    private record PartStart(Session session,boolean replay) {}

    private record Session(
            String id,
            String meeting,
            StorageKey key,
            long epoch,
            long size,
            String hash,
            String token,
            String state,
            String file,
            String name,
            String purpose) {
        UploadToken uploadToken() {
            if (token == null || token.isBlank())
                throw new IllegalStateException("Incomplete accepted session");
            return new UploadToken(key.backendId(), token);
        }
    }

    private record Prepared(Root root, Session replay) {}

    private record Frozen(Session session, List<PartReceipt> receipts) {}

    public JdbcFileUploads(
            DataSource source,
            TransactionRunner tx,
            MeetingAccess access,
            FilePurposePolicy purposes,
            FileBackendCatalog backends,
            AuditPort audit,
            OutboxPort outbox,
            long partSize) {this(source,tx,access,purposes,backends,audit,outbox,partSize,null);}
    public JdbcFileUploads(DataSource source,TransactionRunner tx,MeetingAccess access,FilePurposePolicy purposes,
            FileBackendCatalog backends,AuditPort audit,OutboxPort outbox,long partSize,
            ama.transcript.PublicationWriteGuard publicationWrites) {
        if(publicationWrites!=null)publicationWrites.requireStorageSource(source,access);this.publicationWrites=publicationWrites;
        j = new JdbcTemplate(source);
        this.tx = tx;
        this.access = access;
        this.purposes = purposes;
        this.backends = backends;
        this.audit = audit;
        this.outbox = outbox;
        if (partSize < 1) throw new IllegalArgumentException("Positive part suggestion required");
        this.partSize = partSize;
    }

    @Override
    public Accepted begin(Actor actor, String meeting, String key, Begin request, String trace) {
        return beginSelected(actor,meeting,key,request,trace,null);
    }

    @Override
    public Accepted beginPinned(Actor actor,String meeting,String key,Begin request,String trace,String backendId) {
        if(backendId==null||!backendId.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Managed pinned backend required");
        return beginSelected(actor,meeting,key,request,trace,backendId);
    }

    private Accepted beginSelected(Actor actor,String meeting,String key,Begin request,String trace,String pinnedBackend) {
        independent();
        uuid(meeting);
        if (key == null || key.isBlank())
            throw new IllegalArgumentException("Idempotency key required");
        ama.contract.FileContracts.text(key);
        String scope = digest(actor.enterpriseId(), meeting, "UPLOAD_BEGIN", key),
                requestHash =
                        pinnedBackend==null?digest(
                                "file-init-v1",
                                request.purpose(),
                                request.name(),
                                Long.toString(request.size()),
                                request.sha256()):digest("file-init-pinned-v1",pinnedBackend,request.purpose(),request.name(),Long.toString(request.size()),request.sha256());
        Accepted acknowledged=tx.required(()->{
            var old=replay(scope,false);Root root=authorize(actor,meeting);purposes.require(actor,meeting,request.purpose());
            if(old==null)return null;var session=acceptedReplay(old,actor,meeting,request,requestHash,pinnedBackend);if(root.epoch()!=session.epoch())throw new Conflict();return new Accepted(session.id(),partSize);
        });
        if(acknowledged!=null)return acknowledged;
        var publication=preparePublication(actor,meeting);
        Prepared prepared =
                tx.required(
                        () -> {
                            var replay = replay(scope, false);
                            Root root = authorize(actor, meeting,publication);
                            purposes.require(actor, meeting, request.purpose());
                            if (replay != null) {
                                Session old =
                                        acceptedReplay(
                                                replay, actor, meeting, request, requestHash,pinnedBackend);
                                if (root.epoch() != old.epoch()) throw new Conflict();
                                return new Prepared(root, old);
                            }
                            writable(root);
                            selectedBackend(pinnedBackend);
                            return new Prepared(root, null);
                        });
        if (prepared.replay() != null) return new Accepted(prepared.replay().id(), partSize);
        var backend = selectedBackend(pinnedBackend);
        String id = UUID.randomUUID().toString();
        // Short bounded ASCII object keys never contain a display name or browser path.
        StorageKey target = new StorageKey(backend.id(), "files", id.substring(0, 2) + "/" + id);
        UploadToken token =
                backend.storage()
                        .beginUpload(
                                new UploadSpec(
                                        target.backendId(),
                                        target.namespace(),
                                        target.objectKey(),
                                        request.size()));
        if (!token.backendId().equals(target.backendId())) throw new Unavailable();
        // Physical I/O can outlive a worker renewal. Re-capture the entire current graph
        // before committing its receipt; the original epoch and upload binding stay fenced.
        var committingPublication=preparePublication(actor,meeting);
        return tx.required(
                () -> {
                    j.update(
                            "INSERT INTO"
                                + " idempotency_record(scope_hash,actor_id,resource_id,operation,key_hash,request_hash,state,http_status,created_at)"
                                + " VALUES"
                                + " (?,?,?,'UPLOAD_BEGIN',?,?,'SUCCEEDED',0,UTC_TIMESTAMP(6)) ON"
                                + " DUPLICATE KEY UPDATE scope_hash=idempotency_record.scope_hash",
                            scope,
                            actor.enterpriseId(),
                            meeting,
                            digest(key),
                            requestHash);
                    var replay = replay(scope, true);
                    Root root = authorize(actor, meeting,committingPublication);
                    purposes.require(actor, meeting, request.purpose());
                    if (replay.get("result_id") != null) {
                        var old = acceptedReplay(replay, actor, meeting, request, requestHash,pinnedBackend);
                        if (root.epoch() != old.epoch()) throw new Conflict();
                        return new Accepted(old.id(), partSize);
                    }
                    checkRequest(replay, actor, meeting, requestHash);
                    writable(root);
                    if (root.epoch() != prepared.root().epoch()) throw new Conflict();
                    backends.require(backend.id(), true);
                    j.update(
                            "INSERT INTO"
                                + " upload_session(id,meeting_id,backend_id,namespace,object_key,cancel_epoch,expected_size,expected_sha256,provider_upload_token,state,created_at,updated_at,display_name,purpose)"
                                + " VALUES"
                                + " (?,?,?,?,?,?,?,?,?,'OPEN',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),?,?)",
                            id,
                            meeting,
                            target.backendId(),
                            target.namespace(),
                            target.objectKey(),
                            root.epoch(),
                            request.size(),
                            request.sha256(),
                            token.sessionId(),
                            request.name(),
                            request.purpose());
                    j.update(
                            "UPDATE idempotency_record SET result_id=?,http_status=201 WHERE"
                                + " scope_hash=?",
                            id,
                            scope);
                    finish(actor, meeting, root.revision(), "FILE_UPLOAD_STARTED", trace);
                    return new Accepted(id, partSize);
                });
    }

    @Override
    public void part(
            Actor actor,
            String id,
            int number,
            InputStream body,
            long size,
            String checksum,
            String trace) {
        independent();
        uuid(id);
        if (number < 1 || size < 0 || body == null)
            throw new IllegalArgumentException("Invalid part");
        String sum = hash(checksum);
        String meeting = discover(id);
        boolean acknowledged=tx.required(()->{
            Root root=authorize(actor,meeting);Session session=session(id);if(root.epoch()!=session.epoch())throw new Conflict();purposes.require(actor,meeting,session.purpose());
            var old=part(id,number);if(old==null)return false;if(old.size()!=size||!old.sha256().equals(sum))throw new Conflict();return true;
        });
        if(acknowledged)return;
        var publication=preparePublication(actor,meeting);
        PartStart started=tx.required(()->{
            Root root=authorize(actor,meeting,publication);Session s=session(id);
            if(root.epoch()!=s.epoch())throw new Conflict();purposes.require(actor,meeting,s.purpose());
            var existing=part(id,number);if(existing!=null){if(existing.size()!=size||!existing.sha256().equals(sum))throw new Conflict();return new PartStart(s,true);}
            writable(root);requireState(s,"OPEN");backends.require(s.key().backendId(),true);return new PartStart(s,false);
        });
        if(started.replay())return;Session before=started.session();
        PartReceipt receipt =
                backends.require(before.key().backendId(), true)
                        .storage()
                        .writePart(before.uploadToken(), number, body, size, sum);
        if (receipt.part() != number || receipt.size() != size || !receipt.sha256().equals(sum))
            throw new Unavailable();
        var committingPublication=preparePublication(actor,meeting);
        tx.required(
                () -> {
                    Root root = authorize(actor, meeting,committingPublication);
                    Session s = session(id);
                    if(root.epoch()!=s.epoch())throw new Conflict();purposes.require(actor,meeting,s.purpose());
                    PartReceipt old = part(id, number);
                    if (old != null) {
                        if (old.size() != receipt.size() || !old.sha256().equals(receipt.sha256()))
                            throw new Conflict();
                        return null;
                    }
                    writable(root);requireState(s,"OPEN");sameSession(before,s);backends.require(s.key().backendId(),true);
                    j.update(
                            "INSERT INTO"
                                + " upload_part(upload_id,part_no,size_bytes,sha256,provider_token)"
                                + " VALUES (?,?,?,?,?)",
                            id,
                            receipt.part(),
                            receipt.size(),
                            receipt.sha256(),
                            receipt.providerToken());
                    finish(actor, meeting, root.revision(), "FILE_PART_ACCEPTED", trace);
                    return null;
                });
    }

    @Override
    public Completed complete(Actor actor, String id, Completion request, String trace) {
        independent();
        uuid(id);
        String meeting = discover(id);
        Completed acknowledged=tx.required(()->{
            Root root=authorize(actor,meeting);Session session=session(id);if(root.epoch()!=session.epoch())throw new Conflict();purposes.require(actor,meeting,session.purpose());
            var stored=receipts(id);manifest(session,stored,request);return session.state().equals("COMPLETED")?new Completed(session.file()):null;
        });
        if(acknowledged!=null)return acknowledged;
        var publication=preparePublication(actor,meeting);
        Frozen frozen =
                tx.required(
                        () -> {
                            Root root = authorize(actor, meeting,publication);
                            Session s = session(id);
                            purposes.require(actor, meeting, s.purpose());
                            if (root.epoch() != s.epoch()) throw new Conflict();
                            var receipts = receipts(id);
                            manifest(s, receipts, request);
                            if (s.state().equals("COMPLETED")) return new Frozen(s, receipts);
                            writable(root);
                            if (!Set.of("OPEN", "FINALIZING").contains(s.state()))
                                throw new Conflict();
                            backends.require(s.key().backendId(), s.state().equals("OPEN"));
                            if (s.state().equals("OPEN")) {
                                j.update(
                                        "UPDATE upload_session SET"
                                            + " state='FINALIZING',expected_sha256=?,updated_at=UTC_TIMESTAMP(6)"
                                            + " WHERE id=?",
                                        request.sha256(),
                                        id);
                                Session decided = session(id);
                                finish(
                                        actor,
                                        meeting,
                                        root.revision(),
                                        "FILE_UPLOAD_FROZEN",
                                        trace);
                                return new Frozen(decided, receipts);
                            }
                            return new Frozen(s, receipts);
                        });
        if (frozen.session().state().equals("COMPLETED"))
            return new Completed(frozen.session().file());
        // Repeated native complete re-establishes durable publication; stat/read is never
        // substituted.
        Session selected = frozen.session();
        StoredBlob blob =
                backends.require(selected.key().backendId(), false)
                        .storage()
                        .complete(selected.uploadToken(), frozen.receipts(), selected.hash());
        if (!blob.key().equals(selected.key())
                || blob.size() != selected.size()
                || !blob.sha256().equals(selected.hash())) throw new Unavailable();
        var committingPublication=preparePublication(actor,meeting);
        return tx.required(
                () -> {
                    Root root = authorize(actor, meeting,committingPublication);
                    Session s = session(id);
                    purposes.require(actor, meeting, s.purpose());
                    if (root.epoch() != s.epoch()) throw new Conflict();
                    sameSession(selected, s);
                    var receipts = receipts(id);
                    manifest(s, receipts, request);
                    if (!receipts.equals(frozen.receipts())) throw new Conflict();
                    if (s.state().equals("COMPLETED")) return new Completed(s.file());
                    writable(root);
                    requireState(s, "FINALIZING");
                    var backend = backends.require(s.key().backendId(), false);
                    String file = UUID.randomUUID().toString();
                    j.update(
                            "INSERT INTO"
                                + " file_object(id,meeting_id,backend_id,storage_type,namespace,object_key,provider_version_id,display_name,size_bytes,sha256,state,created_at)"
                                + " VALUES (?,?,?,?,?,?,?,?,?,?,'AVAILABLE',UTC_TIMESTAMP(6))",
                            file,
                            meeting,
                            s.key().backendId(),
                            backend.type(),
                            s.key().namespace(),
                            s.key().objectKey(),
                            blob.providerVersionId(),
                            s.name(),
                            s.size(),
                            s.hash());
                    j.update(
                            "UPDATE upload_session SET"
                                + " state='COMPLETED',completed_file_id=?,updated_at=UTC_TIMESTAMP(6)"
                                + " WHERE id=?",
                            file,
                            id);
                    j.update(
                            "INSERT INTO file_reference(file_id,owner_kind,owner_id,purpose) VALUES"
                                + " (?,'UPLOAD_SESSION',?,?)",
                            file,
                            id,
                            s.purpose());
                    j.update(
                            "UPDATE file_object SET reference_epoch=reference_epoch+1 WHERE id=?",
                            file);
                    finish(actor, meeting, root.revision(), "FILE_UPLOAD_COMPLETED", trace);
                    return new Completed(file);
                });
    }

    @Override
    public void abort(Actor actor, String id, String trace) {
        independent();
        uuid(id);
        String meeting = discover(id);
        Session s =
                tx.required(
                        () -> {
                            Root root = authorize(actor, meeting);
                            Session found = session(id);
                            purposes.require(actor, meeting, found.purpose());
                            if (root.epoch() != found.epoch()) throw new Conflict();
                            if (found.state().equals("COMPLETED")) return found;
                            if (found.file()!=null) throw new Conflict();
                            if (found.state().equals("ABORTED")) return found;
                            if (!Set.of("OPEN","FINALIZING").contains(found.state())) throw new Conflict();
                            j.update(
                                    "UPDATE upload_session SET"
                                        + " state='ABORTED',updated_at=UTC_TIMESTAMP(6) WHERE id=?",
                                    id);
                            Session aborted = session(id);
                            finish(actor, meeting, root.revision(), "FILE_UPLOAD_ABORTED", trace);
                            return aborted;
                        });
        if (!s.state().equals("COMPLETED"))
            backends.require(s.key().backendId(), false).storage().discardUpload(s.uploadToken(),s.key());
    }

    private ama.transcript.PublicationWriteGuard.Prepared preparePublication(Actor actor,String meeting){return publicationWrites==null?null:publicationWrites.prepare(actor,meeting);}
    private Root authorize(Actor actor,String meeting){return authorize(actor,meeting,null);}
    private Root authorize(Actor actor,String meeting,ama.transcript.PublicationWriteGuard.Prepared prepared){
        var gate=access.lock(actor,meeting);access.require(gate,MeetingAccess.Action.EDIT);
        ama.transcript.PublicationWriteGuard.Held contribution=null;RuntimeException failure=null;
        try{if(publicationWrites==null||prepared==null)throw new Unavailable();contribution=publicationWrites.controls(prepared,actor,meeting,gate);}
        catch(MeetingAccess.AccessDeniedException denied){throw denied;}
        catch(RuntimeException unavailable){failure=unavailable;}
        final var held=contribution;final var rejected=failure;
        return j.queryForObject("SELECT cancel_epoch,revision,ever_published,stage FROM meeting WHERE id=? FOR UPDATE",(r,n)->new Root(r.getLong(1),r.getLong(2),r.getBoolean(3)||r.getString(4).equals("PUBLISHED"),gate,held,rejected),meeting);
    }
    private void writable(Root root){
        if(root.published())throw new Conflict();if(root.guardFailure()!=null)throw root.guardFailure();
        if(publicationWrites==null||root.publication()==null)throw new Unavailable();publicationWrites.tasks(root.publication(),root.gate());publicationWrites.claim(root.publication(),root.gate());
    }

    private String discover(String id) {
        return j
                .queryForList("SELECT meeting_id FROM upload_session WHERE id=?", String.class, id)
                .stream()
                .findFirst()
                .orElseThrow(NotFound::new);
    }

    private Session session(String id) {
        return j
                .query(
                        "SELECT * FROM upload_session WHERE id=? FOR UPDATE",
                        (r, n) -> session(r, n),
                        id)
                .stream()
                .findFirst()
                .orElseThrow(NotFound::new);
    }

    private Session session(ResultSet r, int n) throws SQLException {
        return new Session(
                r.getString("id"),
                r.getString("meeting_id"),
                new StorageKey(
                        r.getString("backend_id"),
                        r.getString("namespace"),
                        r.getString("object_key")),
                r.getLong("cancel_epoch"),
                r.getLong("expected_size"),
                r.getString("expected_sha256"),
                r.getString("provider_upload_token"),
                r.getString("state"),
                r.getString("completed_file_id"),
                r.getString("display_name"),
                r.getString("purpose"));
    }

    private PartReceipt part(String id, int number) {
        return j
                .query(
                        "SELECT part_no,size_bytes,sha256,provider_token FROM upload_part WHERE"
                            + " upload_id=? AND part_no=?",
                        (r, n) ->
                                new PartReceipt(
                                        r.getInt(1), r.getLong(2), r.getString(3), r.getString(4)),
                        id,
                        number)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private List<PartReceipt> receipts(String id) {
        return j.query(
                "SELECT part_no,size_bytes,sha256,provider_token FROM upload_part WHERE upload_id=?"
                    + " ORDER BY part_no",
                (r, n) ->
                        new PartReceipt(
                                Math.toIntExact(r.getLong(1)),
                                r.getLong(2),
                                r.getString(3),
                                r.getString(4)),
                id);
    }

    private void manifest(Session s, List<PartReceipt> rows, Completion request) {
        if (rows.size() != request.parts().size()
                || rows.isEmpty()
                || (s.hash() != null && !s.hash().equals(request.sha256()))) throw new Conflict();
        long total = 0;
        for (int i = 0; i < rows.size(); i++) {
            var stored = rows.get(i);
            var client = request.parts().get(i);
            if (stored.part() != i + 1
                    || client.part() != stored.part()
                    || !client.sha256().equals(stored.sha256())) throw new Conflict();
            try {
                total = Math.addExact(total, stored.size());
            } catch (ArithmeticException e) {
                throw new Conflict();
            }
        }
        if (total != s.size()) throw new Conflict();
    }

    private void sameSession(Session a, Session b) {
        if (!a.id().equals(b.id())
                || !a.meeting().equals(b.meeting())
                || !a.key().equals(b.key())
                || a.epoch() != b.epoch()
                || a.size() != b.size()
                || !Objects.equals(a.hash(), b.hash())
                || !a.uploadToken().equals(b.uploadToken())
                || !a.name().equals(b.name())
                || !a.purpose().equals(b.purpose())) throw new Conflict();
    }

    private static void requireState(Session s, String state) {
        if (!s.state().equals(state)) throw new Conflict();
    }

    private Map<String, Object> replay(String scope, boolean lock) {
        var rows =
                j.queryForList(
                        "SELECT * FROM idempotency_record WHERE scope_hash=?"
                                + (lock ? " FOR UPDATE" : ""),
                        scope);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private void checkRequest(Map<String, Object> row, Actor actor, String meeting, String hash) {
        if (!actor.enterpriseId().equals(row.get("actor_id"))
                || !meeting.equals(row.get("resource_id"))
                || !"UPLOAD_BEGIN".equals(row.get("operation"))
                || !hash.equals(row.get("request_hash"))) throw new Conflict();
    }

    private Session acceptedReplay(
            Map<String, Object> row, Actor actor, String meeting, Begin request, String hash,String pinnedBackend) {
        checkRequest(row, actor, meeting, hash);
        if (row.get("result_id") == null) throw new Conflict();
        Session s = session((String) row.get("result_id"));
        if (!s.meeting().equals(meeting)
                || pinnedBackend!=null&&!s.key().backendId().equals(pinnedBackend)
                || !s.name().equals(request.name())
                || !s.purpose().equals(request.purpose())
                || s.size() != request.size()
                || (request.sha256() != null && !request.sha256().equals(s.hash())))
            throw new Conflict();
        return s;
    }

    private FileBackendCatalog.Backend selectedBackend(String pinnedBackend){return pinnedBackend==null?backends.defaultWrite():backends.require(pinnedBackend,true);}

    private void finish(Actor actor, String meeting, long revision, String action, String trace) {
        audit.append(
                new AuditEvent(
                        actor.enterpriseId(),
                        action,
                        meeting,
                        revision,
                        "SUCCEEDED",
                        trace,
                        Instant.now()));
        outbox.flush();
    }

    private static String digest(String... values) {
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            for (String value : values) {
                if (value == null) {
                    out.writeInt(-1);
                } else {
                    byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
                    out.writeInt(encoded.length);
                    out.write(encoded);
                }
            }
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void independent() {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Upload commands require an independent transaction");
    }
}
