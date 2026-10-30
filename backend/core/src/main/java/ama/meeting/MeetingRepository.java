package ama.meeting;

import ama.contract.IdentityContracts.Actor;
import ama.contract.MeetingContracts.*;

/** Atomic aggregate boundary, including fresh identity, CAS, immutable history, audit and final outbox flush. */
public interface MeetingRepository {
    Created create(Actor actor, String key, String traceId);
    Saved read(Actor actor, String id);
    Mutation save(Actor actor, String id, String key, SaveInfo request, String traceId);
    Mutation confirm(Actor actor, String id, String key, ConfirmRoster request, String traceId);
    Mutation delete(Actor actor, String id, String key, DeleteMeeting request, String traceId);
}
