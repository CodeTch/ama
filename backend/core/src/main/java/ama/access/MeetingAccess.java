package ama.access;
import ama.contract.IdentityContracts.Actor;
/** V008 implements this against shared ACTIVE identity + meeting owner/effective grants in the
 * protected transaction. Actor alone is never authorization evidence. Legacy meeting_acl is forbidden. */
public interface MeetingAccess {
    /** Independent preparation only; implementations must finish local ACL checks before external I/O. */
    default void prepareExternalRead(Actor actor,String meetingId) {}
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
    /** Additional absolute expiry of an already authorized READ (e.g. signed publication state).
     * Empty means no external proof deadline; stream/session renewal still has its own maximum. */
    default java.util.Optional<java.time.Instant> readAuthorityDeadline(Locked locked){require(locked,Action.READ);return java.util.Optional.empty();}
    /** Standalone only; an ambient caller transaction must use lock + require(Locked,...). */
    void require(Actor actor, String meetingId, Action action);
    final class AccessDeniedException extends RuntimeException {
        public AccessDeniedException() { super("Meeting access denied"); }
    }
    /** No fresh external proof: still denies content, but must not turn a transient outage
     * into a permanent access revocation or a terminal background-task failure. */
    final class AuthorityUnavailableException extends RuntimeException {
        public AuthorityUnavailableException() { super("Current external authority unavailable"); }
        public AuthorityUnavailableException(Throwable cause) { super("Current external authority unavailable",cause); }
    }
}
