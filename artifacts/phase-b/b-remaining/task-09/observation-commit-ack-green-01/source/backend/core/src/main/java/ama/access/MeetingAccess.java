package ama.access;
import ama.contract.IdentityContracts.Actor;
/** V008 implements this against shared ACTIVE identity + meeting owner/effective grants in the
 * protected transaction. Actor alone is never authorization evidence. Legacy meeting_acl is forbidden. */
public interface MeetingAccess {
    enum Action { READ, EDIT, MANAGE }
    /** Opaque transaction capability: acquire at the start of the caller transaction, before roots. */
    interface Locked {}
    Locked lock(Actor actor, String meetingId);
    /** Complete canonical identity phase for queue composition, before any subject/root. */
    interface LockedIdentities {}
    default java.util.Optional<LockedIdentities> tryLockIdentities(Actor actor, java.util.Collection<String> affected, boolean skipLocked) {
        throw new IllegalStateException("Identity preparation unavailable");
    }
    /** Consumes authentic same-transaction identities, never discovers additional identities. */
    default java.util.Optional<Locked> lockPrepared(LockedIdentities identities, String meetingId, boolean skipLocked) {
        throw new IllegalStateException("Identity preparation unavailable");
    }
    /** Adoption checks exact actor, meeting and complete already-held identity coverage without new locks. */
    default void requirePrepared(Locked held, Actor actor, String meetingId, java.util.Collection<String> affected, Action action) {
        throw new IllegalStateException("Capability adoption unavailable");
    }
    /** Rechecks against already-held identity/subject/root, without acquiring an identity lock. */
    void require(Locked locked, Action action);
    /** Standalone only; an ambient caller transaction must use lock + require(Locked,...). */
    void require(Actor actor, String meetingId, Action action);
    final class AccessDeniedException extends RuntimeException {
        public AccessDeniedException() { super("Meeting access denied"); }
    }
}
