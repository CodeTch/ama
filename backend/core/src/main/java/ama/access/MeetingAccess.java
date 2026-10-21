package ama.access;
import ama.contract.IdentityContracts.Actor;
/** V008 implements this against shared ACTIVE identity + meeting owner/effective grants in the
 * protected transaction. Actor alone is never authorization evidence. Legacy meeting_acl is forbidden. */
public interface MeetingAccess {
    enum Action { READ, EDIT, MANAGE }
    void require(Actor actor, String meetingId, Action action);
    final class AccessDeniedException extends RuntimeException {
        public AccessDeniedException() { super("Meeting access denied"); }
    }
}
