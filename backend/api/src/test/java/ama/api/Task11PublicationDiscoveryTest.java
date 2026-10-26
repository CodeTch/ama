package ama.api;
import ama.contract.IdentityContracts.*;
import ama.transcript.*;
import ama.access.MeetingAccess;
import ama.db.TransactionRunner;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class Task11PublicationDiscoveryTest {
 static final String M="12345678-1234-4234-8234-123456789abc",BASE="/api/v1/meetings/"+M+"/publication-destinations/libraries";
 final ResolvedSession session=new ResolvedSession(new Actor("viewer",Status.ACTIVE,Set.of(Role.USER)),"T".repeat(43),Instant.now().plusSeconds(60));
 @Test void absentRuntimeStillChecksOwnerBeforeSafe503()throws Exception {
  var tx=mock(TransactionRunner.class);when(tx.required(any())).thenAnswer(i->((java.util.concurrent.Callable<?>)i.getArgument(0)).call());
  var access=mock(MeetingAccess.class);var runtime=new MinutesConfiguration.Runtime(null,null,null,null,null,null,null,null,65536);
  try(var reads=new PublicationReadRequests()) {
   var mvc=MockMvcBuilders.standaloneSetup(new PublicationDiscoveryController(runtime,tx,access,reads)).setControllerAdvice(new PublicationErrors()).build();
   for(String suffix:List.of("","/library/folders")) {
    var response=mvc.perform(get(BASE+suffix).requestAttr(SessionSecurityFilter.SESSION,session)).andReturn().getResponse();
    assertEquals(503,response.getStatus());assertEquals("private, no-store",response.getHeader("Cache-Control"));
   }
   verify(access,times(2)).lock(session.actor(),M);verify(access,times(2)).require(any(),eq(MeetingAccess.Action.EDIT));
   doThrow(new MeetingAccess.AccessDeniedException()).when(access).require(any(),eq(MeetingAccess.Action.EDIT));
   assertEquals(403,mvc.perform(get(BASE).requestAttr(SessionSecurityFilter.SESSION,session)).andReturn().getResponse().getStatus());
  }
 }
 @Test void exactRoutesDispatchActualViewerWithCancellableRequest()throws Exception {
  var service=mock(JdbcPublicationService.class);
  when(service.destinations(any(),anyString(),nullable(String.class),anyMap(),any())).thenAnswer(i->{assertEquals(session.actor(),i.getArgument(0));assertFalse(((ama.task.PreparationCancellation)i.getArgument(4)).cancelled());return Map.of("items",List.of(),"hasMore",false);});
  try(var reads=new PublicationReadRequests()) {
   var runtime=new MinutesConfiguration.Runtime(null,null,null,null,null,null,service,null,65536);
   var mvc=MockMvcBuilders.standaloneSetup(new PublicationDiscoveryController(runtime,null,null,reads)).setControllerAdvice(new PublicationErrors()).build();
   for(String suffix:List.of("?limit=1","/lib/folders?parentFolderId=p&limit=1")) {
    var pending=mvc.perform(get(BASE+suffix).requestAttr(SessionSecurityFilter.SESSION,session)).andReturn();assertTrue(pending.getRequest().isAsyncStarted());pending.getAsyncResult(5000);
    var response=mvc.perform(asyncDispatch(pending)).andReturn().getResponse();assertEquals(200,response.getStatus(),response.getContentAsString());assertEquals("private, no-store",response.getHeader("Cache-Control"));
   }
  }
 }
 @Test void malformedQueryNeverStartsAsyncOrCallsService()throws Exception {
  var service=mock(JdbcPublicationService.class);try(var reads=new PublicationReadRequests()) {
   var runtime=new MinutesConfiguration.Runtime(null,null,null,null,null,null,service,null,65536);
   var mvc=MockMvcBuilders.standaloneSetup(new PublicationDiscoveryController(runtime,null,null,reads)).setControllerAdvice(new PublicationErrors()).build();
   for(String query:List.of("limit=0","limit=51","limit=1&limit=2","actorId=victim","cursor=","limit=01","parentFolderId=p")) {
    var response=mvc.perform(get(BASE+"?"+query).requestAttr(SessionSecurityFilter.SESSION,session)).andReturn();assertEquals(400,response.getResponse().getStatus(),query);assertFalse(response.getRequest().isAsyncStarted());assertEquals("private, no-store",response.getResponse().getHeader("Cache-Control"));
   }
   verifyNoInteractions(service);
  }
 }
 @Test void executorAdmitsTwoReadsAndRejectsThirdWithoutProviderWork()throws Exception {
  var service=mock(JdbcPublicationService.class);var entered=new java.util.concurrent.CountDownLatch(2);var release=new java.util.concurrent.CountDownLatch(1);
  when(service.destinations(any(),anyString(),nullable(String.class),anyMap(),any())).thenAnswer(i->{entered.countDown();assertTrue(release.await(5,java.util.concurrent.TimeUnit.SECONDS));return Map.of("items",List.of(),"hasMore",false);});
  try(var reads=new PublicationReadRequests()) {var runtime=new MinutesConfiguration.Runtime(null,null,null,null,null,null,service,null,65536);var mvc=MockMvcBuilders.standaloneSetup(new PublicationDiscoveryController(runtime,null,null,reads)).setControllerAdvice(new PublicationErrors()).build();
   var first=mvc.perform(get(BASE).requestAttr(SessionSecurityFilter.SESSION,session)).andReturn();var second=mvc.perform(get(BASE).requestAttr(SessionSecurityFilter.SESSION,session)).andReturn();try{assertTrue(entered.await(2,java.util.concurrent.TimeUnit.SECONDS));var rejected=mvc.perform(get(BASE).requestAttr(SessionSecurityFilter.SESSION,session)).andReturn();if(rejected.getRequest().isAsyncStarted()){assertInstanceOf(org.springframework.core.task.TaskRejectedException.class,rejected.getAsyncResult(3000));rejected=mvc.perform(asyncDispatch(rejected)).andReturn();}assertEquals(503,rejected.getResponse().getStatus());assertEquals("private, no-store",rejected.getResponse().getHeader("Cache-Control"));verify(service,times(2)).destinations(any(),anyString(),nullable(String.class),anyMap(),any());}finally{release.countDown();}for(var request:List.of(first,second)){request.getAsyncResult(3000);assertEquals(200,mvc.perform(asyncDispatch(request)).andReturn().getResponse().getStatus());}
  }
 }
 @Test void servletTimeoutAndErrorAbortWorkBeforeSafeAsyncFailure()throws Exception {
  for(boolean timeout:List.of(true,false)){var service=mock(JdbcPublicationService.class);var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);var aborted=new java.util.concurrent.atomic.AtomicInteger();
   when(service.destinations(any(),anyString(),nullable(String.class),anyMap(),any())).thenAnswer(i->{var cancellation=(ama.task.PreparationCancellation)i.getArgument(4);cancellation.register(()->{aborted.incrementAndGet();release.countDown();});entered.countDown();assertTrue(release.await(5,java.util.concurrent.TimeUnit.SECONDS));return Map.of("items",List.of(),"hasMore",false);});
   try(var reads=new PublicationReadRequests()){var runtime=new MinutesConfiguration.Runtime(null,null,null,null,null,null,service,null,65536);var mvc=MockMvcBuilders.standaloneSetup(new PublicationDiscoveryController(runtime,null,null,reads)).setControllerAdvice(new PublicationErrors()).build();var pending=mvc.perform(get(BASE).requestAttr(SessionSecurityFilter.SESSION,session)).andReturn();assertTrue(entered.await(2,java.util.concurrent.TimeUnit.SECONDS));var context=(org.springframework.mock.web.MockAsyncContext)pending.getRequest().getAsyncContext();for(var listener:context.getListeners()){if(timeout)listener.onTimeout(new jakarta.servlet.AsyncEvent(context));else listener.onError(new jakarta.servlet.AsyncEvent(context,new java.io.IOException("Synthetic client disconnect")));}var manager=org.springframework.web.context.request.async.WebAsyncUtils.getAsyncManager(pending.getRequest());assertTrue(manager.hasConcurrentResult(),"Real WebAsyncManager must own the timeout/error result");var response=mvc.perform(sc->{var request=pending.getRequest();request.setDispatcherType(jakarta.servlet.DispatcherType.ASYNC);request.setAsyncStarted(false);return request;}).andReturn().getResponse();assertEquals(503,response.getStatus());assertEquals(1,aborted.get());assertEquals("private, no-store",response.getHeader("Cache-Control"));assertFalse(response.getContentAsString().contains("items"));}finally{release.countDown();}
  }
 }
}
