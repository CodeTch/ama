package ama.api;

import ama.identity.SessionPort;
import ama.contract.IdentityContracts.*;
import ama.contract.StorageContracts.StorageKey;
import ama.storage.*;
import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real session filter, controller and blocked transport; only the identity/registry stores are unit boundaries. */
class DownloadStreamRevocationTest {
 @Test void logoutStopsAnAlreadyOpenedProtectedDownload()throws Exception{exercise(true);}
 @Test void meetingGrantRevocationStopsAnAlreadyOpenedProtectedDownload()throws Exception{exercise(false);}
 private void exercise(boolean logout)throws Exception{
  var denied=new AtomicBoolean();var sessions=mock(SessionPort.class);var registry=mock(FileRegistry.class);String token="A".repeat(43),id=UUID.randomUUID().toString();
  var actor=new Actor("viewer",Status.ACTIVE,Set.of(Role.USER));var session=new ResolvedSession(actor,"B".repeat(43),Instant.now().plusSeconds(60));
  when(sessions.resolve(token)).thenAnswer(invocation->logout&&denied.get()?Optional.empty():Optional.of(session));
  var file=new FileRegistry.FileRecord(id,UUID.randomUUID().toString(),new StorageKey("test-local","files","saved/input"),100,"a".repeat(64),"AVAILABLE",0,1,"saved.md");
  var started=new CountDownLatch(1);var closed=new CountDownLatch(1);
  var source=new InputStream(){public int read()throws IOException{started.countDown();try{if(!closed.await(10,TimeUnit.SECONDS))throw new IOException("Test timeout");}catch(InterruptedException interruption){Thread.currentThread().interrupt();throw new IOException(interruption);}return 'X';}public int read(byte[] bytes,int offset,int length)throws IOException{if(length==0)return 0;bytes[offset]=(byte)read();return 1;}public void close(){closed.countDown();}};
  when(registry.download(eq(actor),eq(id),isNull())).thenReturn(new FileRegistry.Download(file,source,0,100));
  when(registry.download(eq(actor),eq(id),eq("bytes=0-0"))).thenAnswer(invocation->{if(!logout&&denied.get())throw new ama.access.MeetingAccess.AccessDeniedException();return new FileRegistry.Download(file,new ByteArrayInputStream(new byte[]{1}),0,1);});
  var environment=new MockEnvironment().withProperty("local.server.port","8443");var controller=new FileController(mock(FileUploads.class),registry,environment);var filter=new SessionSecurityFilter(sessions,environment);
  var request=new MockHttpServletRequest("GET","/api/v1/files/"+id+"/content");request.setLocalPort(8443);request.setCookies(new Cookie(SessionCookies.NAME,token));var response=new MockHttpServletResponse();var executor=Executors.newSingleThreadExecutor();
  try{
   var download=executor.submit(()->{filter.doFilter(request,response,(req,res)->controller.content(id,request,response));return null;});
   assertTrue(started.await(5,TimeUnit.SECONDS));long began=System.nanoTime();denied.set(true);assertTrue(closed.await(5,TimeUnit.SECONDS));
   assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-began)<=5000);var failure=assertThrows(ExecutionException.class,()->download.get(1,TimeUnit.SECONDS));assertInstanceOf(IOException.class,failure.getCause());assertEquals(0,response.getContentAsByteArray().length);
  }finally{source.close();executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));}
 }
}
