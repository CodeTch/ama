package ama.api;

import ama.contract.IdentityContracts.*;
import ama.contract.MeetingContracts.DeleteMeeting;
import ama.meeting.*;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MeetingDeleteWireTest {
    @Test void deletePreservesOpaqueRevisionTokenAndIdempotencyKey() {
        var repository = mock(MeetingRepository.class);
        var cleanup=mock(JdbcMeetingDeletion.class);
        var controller = new MeetingDeletionController(new MeetingService(repository),cleanup);
        var request = new MockHttpServletRequest();
        request.addHeader("Idempotency-Key", "delete-key");
        var actor = new Actor("owner", Status.ACTIVE, Set.of(Role.USER));
        request.setAttribute(SessionSecurityFilter.SESSION, new ResolvedSession(actor, "unused", Instant.now().plusSeconds(600)));
        String meeting = UUID.randomUUID().toString();

        request.setContent("{\"expectedRevisionToken\":\"9007199254740993\"}".getBytes());
        var expected=new JdbcMeetingDeletion.Status(UUID.randomUUID().toString(),meeting,"PENDING","INVENTORY","9007199254740994",0,null);
        when(cleanup.forMeeting(actor,meeting)).thenReturn(expected);
        var result=assertDoesNotThrow(() -> controller.delete(meeting,request));assertEquals(202,result.getStatusCode().value());assertEquals(expected,result.getBody());assertEquals("private, no-store",result.getHeaders().getCacheControl());
        verify(repository).delete(eq(actor), eq(meeting), eq("delete-key"), eq(new DeleteMeeting(9007199254740993L)), anyString());
    }
    @Test void unknownBodyFieldsAndRepeatedKeysCannotAcceptADeletion(){
        var repository=mock(MeetingRepository.class);var cleanup=mock(JdbcMeetingDeletion.class);var controller=new MeetingDeletionController(new MeetingService(repository),cleanup);var request=new MockHttpServletRequest();request.setContent("{\"expectedRevisionToken\":\"0\",\"root\":\"/tmp/other\"}".getBytes());
        assertThrows(IllegalArgumentException.class,()->controller.delete(UUID.randomUUID().toString(),request));verifyNoInteractions(repository,cleanup);
    }
}
