package ama.storage;
import com.sun.net.httpserver.*;import java.net.*;import java.net.http.*;import java.nio.file.*;import java.io.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
/** Executes the actual forward method with inert HTTP objects; no listeners or sockets. */
class Bl5MeterForwardTest {
 @TempDir Path root;
 static void field(Object object,String name,Object value)throws Exception {var f=Bl5HttpMeter.class.getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
 @Test @SuppressWarnings("unchecked") void strictProviderHostUsesTargetWhileS3KeepsSignedHost()throws Exception {
  assertEquals("host",System.getProperty("jdk.httpclient.allowRestrictedHeaders"));
  for(String kind:List.of("model","knowledge","s3","native")){
   Path owned=Files.createDirectory(root.resolve(kind));long now=System.currentTimeMillis();Files.writeString(owned.resolve("monitor.json"),new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("owner",kind,"status","ADMIT","observedUtcMillis",now,"expiresUtcMillis",now+10000)));
   Bl5HttpMeter meter=mock(Bl5HttpMeter.class,CALLS_REAL_METHODS);HttpClient client=mock(HttpClient.class);HttpExchange exchange=mock(HttpExchange.class);ScheduledExecutorService deadlines=Executors.newSingleThreadScheduledExecutor();AtomicReference<HttpRequest> captured=new AtomicReference<>();
   field(meter,"root",owned);field(meter,"kind",kind);field(meter,"target",URI.create("http://127.0.0.1:23456"));field(meter,"permits",new Semaphore(2));field(meter,"deadlines",deadlines);field(meter,"client",client);
   Headers headers=new Headers();headers.set("Host","127.0.0.1:12345");headers.set("Authorization","Bearer synthetic-fixture");when(exchange.getRequestHeaders()).thenReturn(headers);when(exchange.getRequestMethod()).thenReturn("GET");when(exchange.getRequestURI()).thenReturn(URI.create("/v1/controlled/result"));when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(new byte[0]));when(exchange.getResponseHeaders()).thenReturn(new Headers());ByteArrayOutputStream output=new ByteArrayOutputStream();when(exchange.getResponseBody()).thenReturn(output);
   HttpResponse<InputStream> response=mock(HttpResponse.class);when(response.statusCode()).thenReturn(200);when(response.body()).thenReturn(new ByteArrayInputStream("{}".getBytes()));when(response.headers()).thenReturn(HttpHeaders.of(Map.of("content-length",List.of("2")),(a,b)->true));when(client.sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenAnswer(call->{captured.set(call.getArgument(0));return CompletableFuture.completedFuture(response);});
   try{meter.forward(exchange);assertNotNull(captured.get());assertEquals(kind.equals("s3")?"127.0.0.1:12345":"127.0.0.1:23456",captured.get().headers().firstValue("Host").orElseThrow());assertEquals("Bearer synthetic-fixture",captured.get().headers().firstValue("Authorization").orElseThrow());assertEquals("{}",output.toString());verify(exchange).sendResponseHeaders(200,2L);}finally{deadlines.shutdownNow();}
  }
 }
}
