package ama.storage;

import ama.access.MeetingAccess;
import ama.contract.IdentityContracts.Actor;
import ama.contract.StorageContracts.StorageKey;

import java.io.InputStream;

/**
 * Internal metadata/capabilities, never browser DTOs. All composition uses one caller transaction.
 */
public interface FileRegistry {
    interface LockedMeeting {}

    interface LockedReference {}
    interface LockedSources {}
    interface ReferenceContext {}
    interface ReadRequest { String referenceKind(); }
    record CurrentReadRequest(String referenceKind, String fileId) implements ReadRequest {}
    record CompletedSource(FileRecord file, String uploadId, String originalPurpose, long uploadCancelEpoch) {}

    /** Additive complete-source protocol; old registry implementations fail closed. */
    default LockedMeeting adopt(Actor actor, String meetingId, MeetingAccess.Locked access,
            MeetingAccess.Action action) { throw new ama.contract.FileContracts.Unavailable(); }
    default LockedReference prelock(LockedMeeting gate, Reference reference, String fileId,
            ReferenceContext context) { throw new ama.contract.FileContracts.Unavailable(); }
    default java.util.Optional<LockedSources> prelockSources(LockedMeeting gate,
            java.util.List<String> completeFileIds, boolean skipLocked) { throw new ama.contract.FileContracts.Unavailable(); }
    default java.util.List<CompletedSource> sources(LockedMeeting expectedGate, LockedSources held,
            java.util.List<String> exactFileIds) { throw new ama.contract.FileContracts.Unavailable(); }
    default void bindAll(LockedSources sources, java.util.List<Binding> bindings) { throw new ama.contract.FileContracts.Unavailable(); }
    default Download read(Actor actor, String meetingId, ReadRequest request, String range) { throw new ama.contract.FileContracts.Unavailable(); }

    record Reference(String kind, String ownerId, String purpose) {
        public Reference {
            ama.contract.FileContracts.code(kind);
            ama.contract.OutboxContracts.uuid(ownerId);
            ama.contract.FileContracts.code(purpose);
        }
    }

    record FileRecord(
            String fileId,
            String meetingId,
            StorageKey key,
            long size,
            String sha256,
            String state,
            long locationRevision,
            long referenceEpoch,
            String displayName) {}

    record Download(FileRecord file, InputStream body, long offset, long length)
            implements AutoCloseable {
        public void close() throws java.io.IOException {
            body.close();
        }
    }

    /** Initial identity/subject/root prelock. Consumers must not call MeetingAccess.lock again. */
    LockedMeeting lock(Actor actor, String meetingId, MeetingAccess.Action action);

    /** Reuse exactly the issued B03 capability for domain policy composition. */
    MeetingAccess.Locked access(LockedMeeting held);

    /** Domain policy prelocks real immutable rows before session/file/task locks. */
    LockedReference prelock(LockedMeeting held, Reference reference, String fileId);

    /** Requires the current transaction's initial registry capability; internal metadata only. */
    FileRecord resolve(String fileId);

    /** Atomic original-purpose-checked business binding plus optional protective-upload handoff. */
    void bind(LockedReference held, long expectedLocationRevision, String uploadId);

    record Binding(LockedReference reference, long expectedLocationRevision, String uploadId) {}

    void bindAll(java.util.List<Binding> bindings);

    /** Standalone fresh authorization and current business-reference proof, then storage open. */
    Download download(Actor actor, String fileId, String range);
}
