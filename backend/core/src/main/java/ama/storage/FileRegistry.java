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
