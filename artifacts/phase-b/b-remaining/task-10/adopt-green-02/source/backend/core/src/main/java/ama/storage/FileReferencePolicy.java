package ama.storage;

/** Registered domain authority, not user input. Unknown kinds have no fallback. */
public interface FileReferencePolicy {
    /**
     * Under the already held meeting: lock the exact immutable owner version and eligibility rows.
     * No identity/root discovery or network. Return a domain-owned transaction capability.
     */
    Object prelock(
            FileRegistry.LockedMeeting meeting,
            String meetingId,
            FileRegistry.Reference reference,
            String fileId);

    /**
     * Revalidate exact subject, immutable version, current pointer, file/hash and original purpose.
     * Called after file lock, so this method must not acquire any additional domain lock.
     */
    void validate(
            Object prelocked,
            String meetingId,
            FileRegistry.Reference reference,
            FileRegistry.FileRecord file,
            String originalPurpose);
}
