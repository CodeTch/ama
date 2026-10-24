package ama.api;
import ama.contract.IdentityContracts.*;
import ama.contract.MeetingContracts.*;
import ama.meeting.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class HttpRevisionTokensTest {
    @Test void tokenOnlyRosterCommandPreservesEveryLongBit() {
        var repository=mock(MeetingRepository.class);
        var controller=new MeetingController(new MeetingService(repository),new RosterService(repository),new MockEnvironment());
        var request=new MockHttpServletRequest();
        request.addHeader("Idempotency-Key","owned");
        var actor=new Actor("token-owner",Status.ACTIVE,Set.of(Role.USER));
        request.setAttribute(SessionSecurityFilter.SESSION,new ResolvedSession(actor,"unused",java.time.Instant.now().plusSeconds(600)));
        String meeting=UUID.randomUUID().toString();
        for(long value:new long[] {
            9007199254740991L,9007199254740992L,9007199254740993L,9223372036854775806L
        }) {
            assertDoesNotThrow(()->controller.confirm(meeting,("{\"expectedRevisionToken\":\""+value+"\"}").getBytes(StandardCharsets.UTF_8),request));
            verify(repository).confirm(eq(actor),eq(meeting),eq("owned"),eq(new ConfirmRoster(value)),anyString());
        }
    }
}
