package ama.api;
import ama.identity.SessionPort;
import ama.contract.IdentityContracts.*;
import ama.transcript.JdbcPublicationService;
import java.time.Instant;
import java.util.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/** Real security chain and async document dispatch. The domain service/session store are unit boundaries. */
class Task11AsyncSessionSecurityTest {
 static final String TOKEN="A".repeat(43),PATH=Task11PublicationDocumentTest.PATH;
 static final byte[] HTML="<!doctype html><html>protected document</html>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
 @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false) @EnableWebMvc @EnableWebSecurity
 @Import({SecurityConfiguration.class,PublicationDocumentController.class,PublicationReadRequests.class,PublicationErrors.class,SyncControl.class})
 static class Wiring {
  @Bean SessionPort sessions(){return mock(SessionPort.class);}
  @Bean JdbcPublicationService publications(){return mock(JdbcPublicationService.class);}
  @Bean MinutesConfiguration.Runtime runtime(JdbcPublicationService service){return new MinutesConfiguration.Runtime(null,null,null,null,null,null,service,null,65536);}
  @Bean ama.db.TransactionRunner tx(){return mock(ama.db.TransactionRunner.class);}
  @Bean ama.access.MeetingAccess access(){return mock(ama.access.MeetingAccess.class);}
 }
 @org.springframework.web.bind.annotation.RestController @Profile("task11-async-security-test")
 static class SyncControl { @org.springframework.web.bind.annotation.GetMapping("/api/sync") String read(){return "sync";} }
 @Test void synchronousRequestStillResolvesOnce()throws Exception {
  try(var c=context()){var sessions=c.getBean(SessionPort.class);when(sessions.resolve(TOKEN)).thenReturn(Optional.of(new ResolvedSession(new Actor("owner",Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER)),"T".repeat(43),Instant.now().plusSeconds(60))));var mvc=MockMvcBuilders.webAppContextSetup(c).addFilters(c.getBean(FilterChainProxy.class)).build();var result=mvc.perform(get("/api/sync").cookie(new Cookie(SessionCookies.NAME,TOKEN)).with(r->{r.setLocalPort(8443);return r;})).andReturn();assertEquals(200,result.getResponse().getStatus());assertEquals("sync",result.getResponse().getContentAsString());verify(sessions).resolve(TOKEN);}
 }
 @Test void asynchronousDomainDenialKeeps403()throws Exception{failure(new ama.access.MeetingAccess.AccessDeniedException(),403,"MEETING_FORBIDDEN");}
 @Test void asynchronousUnavailableKeeps503()throws Exception{failure(new ama.contract.TranscriptContracts.Failure(503,"PUBLICATION_READ_UNAVAILABLE"),503,"PUBLICATION_READ_UNAVAILABLE");}
 void failure(RuntimeException failure,int status,String code)throws Exception{
  try(var c=context()){var sessions=c.getBean(SessionPort.class);when(sessions.resolve(TOKEN)).thenReturn(Optional.of(new ResolvedSession(new Actor("owner",Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER)),"T".repeat(43),Instant.now().plusSeconds(60))));when(c.getBean(JdbcPublicationService.class).officialDocument(any(),anyString(),anyString(),any())).thenThrow(failure);var mvc=MockMvcBuilders.webAppContextSetup(c).addFilters(c.getBean(FilterChainProxy.class)).build();var pending=mvc.perform(get(PATH).cookie(new Cookie(SessionCookies.NAME,TOKEN)).with(r->{r.setLocalPort(8443);return r;})).andReturn();assertTrue(pending.getRequest().isAsyncStarted());pending.getAsyncResult(5000);var response=mvc.perform(asyncDispatch(pending)).andReturn().getResponse();assertEquals(status,response.getStatus());assertTrue(response.getContentAsString().contains(code));assertFalse(response.getContentAsString().contains("protected document"));verify(sessions,times(2)).resolve(TOKEN);}
 }
 AnnotationConfigWebApplicationContext context(){var c=new AnnotationConfigWebApplicationContext();c.setServletContext(new MockServletContext());c.getEnvironment().setActiveProfiles("task11-async-security-test");c.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("test",Map.of("local.server.port",8443)));c.register(Wiring.class);c.refresh();return c;}
 @Test void activeSessionSurvivesAsyncRedispatch()throws Exception{exercise(false);}
 @Test void revokedSessionBeforeRedispatchCannotReceiveBufferedHtml()throws Exception{exercise(true);}
 void exercise(boolean revoke)throws Exception {
  try(var c=context()){
   var sessions=c.getBean(SessionPort.class);var service=c.getBean(JdbcPublicationService.class);var session=new ResolvedSession(new Actor("owner",Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER)),"T".repeat(43),Instant.now().plusSeconds(60));
   when(sessions.resolve(TOKEN)).thenReturn(Optional.of(session));when(service.officialDocument(any(),anyString(),anyString(),any())).thenReturn(HTML);
   var mvc=MockMvcBuilders.webAppContextSetup(c).addFilters(c.getBean(FilterChainProxy.class)).build();var pending=mvc.perform(get(PATH).cookie(new Cookie(SessionCookies.NAME,TOKEN)).with(r->{r.setLocalPort(8443);return r;})).andReturn();assertTrue(pending.getRequest().isAsyncStarted());pending.getAsyncResult(5000);
   if(revoke)when(sessions.resolve(TOKEN)).thenReturn(Optional.empty());
   var response=mvc.perform(asyncDispatch(pending)).andReturn().getResponse();assertEquals(revoke?401:200,response.getStatus());if(revoke)assertFalse(response.getContentAsString().contains("protected document"));else assertArrayEquals(HTML,response.getContentAsByteArray());verify(sessions,times(2)).resolve(TOKEN);verify(service,times(1)).officialDocument(eq(session.actor()),anyString(),anyString(),any());
  }
 }
 @Test void missingSessionDoesNotStartAsyncWork()throws Exception {
  try(var c=context()){var sessions=c.getBean(SessionPort.class);when(sessions.resolve(null)).thenReturn(Optional.empty());var mvc=MockMvcBuilders.webAppContextSetup(c).addFilters(c.getBean(FilterChainProxy.class)).build();var result=mvc.perform(get(PATH).with(r->{r.setLocalPort(8443);return r;})).andReturn();assertEquals(401,result.getResponse().getStatus());assertFalse(result.getRequest().isAsyncStarted());verifyNoInteractions(c.getBean(JdbcPublicationService.class));}
 }
}
