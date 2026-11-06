package ama.api;
import java.time.Instant;import java.util.*;import org.junit.jupiter.api.Test;import org.springframework.test.web.servlet.setup.MockMvcBuilders;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;import static org.junit.jupiter.api.Assertions.*;
class Task11PublicationReadsTest {
 @Test void aRacingPublicationRenewsProofOnceAndStillRequiresAnAuthorizedStatusRead()throws Exception{
  var access=org.mockito.Mockito.mock(ama.access.MeetingAccess.class);
  var reader=org.mockito.Mockito.mock(ama.transcript.JdbcPublicationQueries.class);
  var actor=new ama.contract.IdentityContracts.Actor("owner",ama.contract.IdentityContracts.Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER));
  var request=new org.springframework.mock.web.MockHttpServletRequest();
  request.setAttribute(SessionSecurityFilter.SESSION,new ama.contract.IdentityContracts.ResolvedSession(actor,"T".repeat(43),Instant.now().plusSeconds(60)));
  String meeting=UUID.randomUUID().toString();
  var runtime=new MinutesConfiguration.Runtime(null,null,null,null,null,null,null,null,65536);
  var controller=new PublicationController(runtime,null,access,reader);
  org.mockito.Mockito.when(reader.current(actor,meeting)).thenThrow(new ama.access.MeetingAccess.AuthorityUnavailableException()).thenReturn(Map.of("fresh",true));
  assertEquals(200,controller.current(meeting,request).getStatusCode().value());
  org.mockito.Mockito.verify(access).prepareExternalRead(actor,meeting);
  org.mockito.Mockito.when(reader.current(actor,meeting)).thenThrow(new ama.access.MeetingAccess.AccessDeniedException());
  assertThrows(ama.access.MeetingAccess.AccessDeniedException.class,()->controller.current(meeting,request));
  org.mockito.Mockito.verifyNoMoreInteractions(access);
 }
 @Test void statusRoutesRemainAvailableAsExplicit503WithoutCommandInstallation()throws Exception {
  var runtime=new MinutesConfiguration.Runtime(null,null,null,null,null,null,null,null,65536);
  var mvc=MockMvcBuilders.standaloneSetup(new PublicationController(runtime,null,null)).setControllerAdvice(new PublicationErrors()).build();
  var actor=new ama.contract.IdentityContracts.Actor("owner",ama.contract.IdentityContracts.Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER));
  var session=new ama.contract.IdentityContracts.ResolvedSession(actor,"T".repeat(43),Instant.now().plusSeconds(60));
  for(String suffix:List.of("current","12345678-1234-4234-8234-123456789abd","12345678-1234-4234-8234-123456789abd/submissions","12345678-1234-4234-8234-123456789abd/attempts")){
   var response=mvc.perform(get("/api/v1/meetings/12345678-1234-4234-8234-123456789abc/publications/"+suffix).requestAttr(SessionSecurityFilter.SESSION,session)).andReturn().getResponse();
   assertEquals(503,response.getStatus(),suffix);assertTrue(response.getContentAsString().contains("PUBLICATION_READ_UNAVAILABLE"));assertEquals("private, no-store",response.getHeader("Cache-Control"));
  }
 }
 @Test void finalHistoryHasItsOwnReadRoute()throws Exception {
  var runtime=new MinutesConfiguration.Runtime(null,null,null,null,null,null,null,null,65536);
  var mvc=MockMvcBuilders.standaloneSetup(new FinalConfirmationController(runtime,null,org.mockito.Mockito.mock(ama.db.TransactionRunner.class),org.mockito.Mockito.mock(ama.access.MeetingAccess.class))).setControllerAdvice(new PublicationErrors()).build();
  var response=mvc.perform(get("/api/v1/meetings/12345678-1234-4234-8234-123456789abc/final-confirmations")).andReturn().getResponse();assertEquals(503,response.getStatus());assertTrue(response.getContentAsString().contains("PUBLICATION_READ_UNAVAILABLE"));
 }

}
