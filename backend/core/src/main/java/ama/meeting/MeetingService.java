package ama.meeting;

import ama.contract.IdentityContracts.Actor;
import ama.contract.MeetingContracts.*;
import static ama.contract.OutboxContracts.uuid;

public final class MeetingService {
    private final MeetingRepository repository;
    public MeetingService(MeetingRepository repository) { this.repository=repository; }
    public Created create(Actor actor,String key,String traceId) { key(key); return repository.create(actor,key,traceId); }
    public Saved read(Actor actor,String id) { uuid(id); return repository.read(actor,id); }
    public Mutation save(Actor actor,String id,String key,SaveInfo request,String traceId) {
        uuid(id); key(key); return repository.save(actor,id,key,request,traceId);
    }
    public Mutation delete(Actor actor,String id,String key,DeleteMeeting request,String traceId) {
        uuid(id); key(key); if(request==null)throw new IllegalArgumentException("Delete request required"); return repository.delete(actor,id,key,request,traceId);
    }
    static void key(String key) { if(key==null || key.isBlank())throw new IllegalArgumentException("Idempotency key required"); }
}
