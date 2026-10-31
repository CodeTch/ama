package ama.api;
import ama.contract.IdentityContracts.*;
import ama.transcript.*;
import ama.access.MeetingAccess;
import ama.db.TransactionRunner;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class Task11PublicationDocumentTest {
 static final String M="12345678-1234-4234-8234-123456789abc",P="12345678-1234-4234-8234-123456789abd",PATH="/api/v1/meetings/"+M+"/publications/"+P+"/document";
 final ResolvedSession session=new ResolvedSession(new Actor("member-viewer",Status.ACTIVE,Set.of(Role.USER)),"T".repeat(43),Instant.now().plusSeconds(60));
 @Test void libraryContextIsASeparatePrivateFixedViewerReadAndRejectsQuerySelection()throws Exception {
  var service=mock(JdbcPublicationService.class);when(service.libraryContext(eq(session.actor()),eq(M),eq(P),any())).thenReturn(Map.of("meetingId",M,"publicationId",P,"libraryName","Actual library","versionToken","a".repeat(64)));
  String path=PATH.replace("/document","/library-context");try(var reads=new PublicationReadRequests()) {var runtime=new MinutesConfiguration.Runtime(null,null,null,null,null,null,service,null,65536);var mvc=MockMvcBuilders.standaloneSetup(new PublicationDocumentController(runtime,null,null,reads)).setControllerAdvice(new PublicationErrors()).build();var pending=mvc.perform(get(path).requestAttr(SessionSecurityFilter.SESSION,session)).andReturn();assertTrue(pending.getRequest().isAsyncStarted(),"Actual library route must be mapped");pending.getAsyncResult(5000);var r=mvc.perform(asyncDispatch(pending)).andReturn().getResponse();assertEquals(200,r.getStatus());assertEquals("private, no-store",r.getHeader("Cache-Control"));assertTrue(r.getContentAsString().contains("Actual library"));assertEquals(400,mvc.perform(get(path+"?libraryId=arbitrary").requestAttr(SessionSecurityFilter.SESSION,session)).andReturn().getResponse().getStatus());verify(service).libraryContext(eq(session.actor()),eq(M),eq(P),any());verify(service,never()).officialDocument(any(),anyString(),anyString(),any());}
 }
 @Test void realRouteUsesViewerAndEmitsOnlyBufferedPrivateStaticHtml()throws Exception {
  var service=mock(JdbcPublicationService.class);byte[] html="<!doctype html><html>safe</html>".getBytes(java.nio.charset.StandardCharsets.UTF_8);when(service.officialDocument(eq(session.actor()),eq(M),eq(P),any())).thenReturn(html);
  try(var reads=new PublicationReadRequests()) {var runtime=new MinutesConfiguration.Runtime(null,null,null,null,null,null,service,null,65536);var mvc=MockMvcBuilders.standaloneSetup(new PublicationDocumentController(runtime,null,null,reads)).setControllerAdvice(new PublicationErrors()).build();var pending=mvc.perform(get(PATH).requestAttr(SessionSecurityFilter.SESSION,session)).andReturn();pending.getAsyncResult(5000);var r=mvc.perform(asyncDispatch(pending)).andReturn().getResponse();assertEquals(200,r.getStatus());assertArrayEquals(html,r.getContentAsByteArray());assertEquals("text/html;charset=UTF-8",r.getContentType());assertEquals("private, no-store",r.getHeader("Cache-Control"));assertEquals("default-src 'none'; frame-ancestors 'self'",r.getHeader("Content-Security-Policy"));assertEquals("nosniff",r.getHeader("X-Content-Type-Options"));assertNull(r.getHeader("Location"));verify(service).officialDocument(eq(session.actor()),eq(M),eq(P),any());}
 }
 @Test void absenceChecksCurrentReadBeforeSafe503AndRejectsCallerDocumentSelection()throws Exception {
  var tx=mock(TransactionRunner.class);when(tx.required(any())).thenAnswer(i->((java.util.concurrent.Callable<?>)i.getArgument(0)).call());var access=mock(MeetingAccess.class);
  try(var reads=new PublicationReadRequests()){var runtime=new MinutesConfiguration.Runtime(null,null,null,null,null,null,null,null,65536);var mvc=MockMvcBuilders.standaloneSetup(new PublicationDocumentController(runtime,tx,access,reads)).setControllerAdvice(new PublicationErrors()).build();var r=mvc.perform(get(PATH).requestAttr(SessionSecurityFilter.SESSION,session)).andReturn().getResponse();assertEquals(503,r.getStatus());verify(access).lock(session.actor(),M);verify(access).require(any(),eq(MeetingAccess.Action.READ));assertEquals(400,mvc.perform(get(PATH+"?documentId=arbitrary").requestAttr(SessionSecurityFilter.SESSION,session)).andReturn().getResponse().getStatus());}
 }
 @Test void finalDenialReturnsNoDocumentBytes()throws Exception {var service=mock(JdbcPublicationService.class);when(service.officialDocument(any(),anyString(),anyString(),any())).thenThrow(new MeetingAccess.AccessDeniedException());try(var reads=new PublicationReadRequests()){var runtime=new MinutesConfiguration.Runtime(null,null,null,null,null,null,service,null,65536);var mvc=MockMvcBuilders.standaloneSetup(new PublicationDocumentController(runtime,null,null,reads)).setControllerAdvice(new PublicationErrors()).build();var pending=mvc.perform(get(PATH).requestAttr(SessionSecurityFilter.SESSION,session)).andReturn();pending.getAsyncResult(5000);var r=mvc.perform(asyncDispatch(pending)).andReturn().getResponse();assertEquals(403,r.getStatus());assertFalse(r.getContentAsString().contains("<!doctype"));assertEquals("private, no-store",r.getHeader("Cache-Control"));}}
}
