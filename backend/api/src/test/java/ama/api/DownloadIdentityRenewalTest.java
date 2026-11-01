package ama.api;

import ama.identity.SessionPort;
import ama.contract.IdentityContracts.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DownloadIdentityRenewalTest {
 @Test void ongoingDownloadRechecksTheExactOriginalSessionAndNeverChangesActor()throws Exception{
  var sessions=mock(SessionPort.class);String token="A".repeat(43);var actor=new Actor("viewer",Status.ACTIVE,Set.of(Role.USER));
  when(sessions.resolve(token)).thenReturn(Optional.of(new ResolvedSession(actor,"B".repeat(43),Instant.now().plusSeconds(60))));
  var request=new MockHttpServletRequest("GET","/api/v1/files/123/content");request.setLocalPort(8443);request.setCookies(new Cookie(SessionCookies.NAME,token));var response=new MockHttpServletResponse();var renewal=new AtomicReference<Supplier<Actor>>();
  new SessionSecurityFilter(sessions,new MockEnvironment().withProperty("local.server.port","8443")).doFilter(request,response,(req,res)->renewal.set(SessionSecurityFilter.readIdentity(request)));
  assertNotNull(renewal.get());assertEquals(actor,renewal.get().get());
  when(sessions.resolve(token)).thenReturn(Optional.empty());assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->renewal.get().get());
  when(sessions.resolve(token)).thenReturn(Optional.of(new ResolvedSession(new Actor("other",Status.ACTIVE,Set.of(Role.USER)),"B".repeat(43),Instant.now().plusSeconds(60))));assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->renewal.get().get());
 }
 @Test void requestAttributesCannotSubstituteForAnIssuedSessionRenewal(){
  var request=new MockHttpServletRequest();request.setAttribute(SessionSecurityFilter.SESSION,new ResolvedSession(new Actor("viewer",Status.ACTIVE,Set.of(Role.USER)),"B".repeat(43),Instant.now().plusSeconds(60)));
  assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->SessionSecurityFilter.readIdentity(request));
 }
}
