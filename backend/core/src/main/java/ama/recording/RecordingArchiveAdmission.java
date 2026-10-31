package ama.recording;
import ama.contract.IdentityContracts.Actor;
/** Admission follows the committed capture seal; success returns a durable task identity. */
@FunctionalInterface public interface RecordingArchiveAdmission {
 String accept(Actor actor,String sessionId);
}
