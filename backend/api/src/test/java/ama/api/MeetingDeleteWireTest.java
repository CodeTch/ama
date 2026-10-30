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
        var controller = new MeetingController(new MeetingService(repository), new RosterService(repository), new MockEnvironment());
        var request = new MockHttpServletRequest();
        request.addHeader("Idempotency-Key", "delete-key");
        var actor = new Actor("owner", Status.ACTIVE, Set.of(Role.USER));
        request.setAttribute(SessionSecurityFilter.SESSION, new ResolvedSession(actor, "unused", Instant.now().plusSeconds(600)));
        String meeting = UUID.randomUUID().toString();

        assertDoesNotThrow(() -> controller.delete(meeting, "{\"expectedRevisionToken\":\"9007199254740993\"}".getBytes(), request));
        verify(repository).delete(eq(actor), eq(meeting), eq("delete-key"), eq(new DeleteMeeting(9007199254740993L)), anyString());
    }
}
