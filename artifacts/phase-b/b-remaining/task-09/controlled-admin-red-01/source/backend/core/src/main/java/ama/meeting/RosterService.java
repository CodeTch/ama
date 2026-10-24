package ama.meeting;

import ama.contract.IdentityContracts.Actor;
import ama.contract.MeetingContracts.*;
import static ama.contract.OutboxContracts.uuid;

/** Explicit roster confirmation is independent from information saving. */
public final class RosterService {
    private final MeetingRepository repository;
    public RosterService(MeetingRepository repository) { this.repository=repository; }
    public Mutation confirm(Actor actor,String id,String key,ConfirmRoster request,String traceId) {
        uuid(id); MeetingService.key(key); return repository.confirm(actor,id,key,request,traceId);
    }
}
