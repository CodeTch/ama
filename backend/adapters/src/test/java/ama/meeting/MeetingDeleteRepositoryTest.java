package ama.meeting;

import ama.contract.IdentityContracts.Actor;
import ama.contract.IdentityContracts.Role;
import ama.contract.IdentityContracts.Status;
import ama.contract.MeetingContracts.DeleteMeeting;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MeetingDeleteRepositoryTest {
    @Test void serviceForwardsDeleteCommandWithRevisionAndTrace() {
        var repository=new RecordingRepository();
        var service=new MeetingService(repository);
        var actor=new Actor("owner",Status.ACTIVE,Set.of(Role.USER));
        var meeting="00000000-0000-4000-8000-000000000001";
        var result=service.delete(actor,meeting,"delete-key",new DeleteMeeting(1L),"trace");
        assertEquals(2,result.revision());
        assertEquals(actor,repository.actor);assertEquals(meeting,repository.meeting);assertEquals("delete-key",repository.key);assertEquals(new DeleteMeeting(1L),repository.request);assertEquals("trace",repository.trace);
    }

    @Test void serviceRejectsInvalidDeleteTargetBeforeRepository() {
        var repository=new RecordingRepository();
        var service=new MeetingService(repository);
        var actor=new Actor("owner",Status.ACTIVE,Set.of(Role.USER));
        assertThrows(IllegalArgumentException.class,()->service.delete(actor,"not-a-uuid","key",new DeleteMeeting(1L),"trace"));
        assertNull(repository.request);
    }

    private static final class RecordingRepository implements MeetingRepository {
        Actor actor;String meeting,key,trace;DeleteMeeting request;
        public ama.contract.MeetingContracts.Created create(Actor a,String k,String t){throw new UnsupportedOperationException();}
        public ama.contract.MeetingContracts.Saved read(Actor a,String id){throw new UnsupportedOperationException();}
        public ama.contract.MeetingContracts.Mutation save(Actor a,String id,String k,ama.contract.MeetingContracts.SaveInfo r,String t){throw new UnsupportedOperationException();}
        public ama.contract.MeetingContracts.Mutation confirm(Actor a,String id,String k,ama.contract.MeetingContracts.ConfirmRoster r,String t){throw new UnsupportedOperationException();}
        public ama.contract.MeetingContracts.Mutation delete(Actor a,String id,String k,DeleteMeeting r,String t){actor=a;meeting=id;key=k;request=r;trace=t;return new ama.contract.MeetingContracts.Mutation(id,2);}
    }
}
