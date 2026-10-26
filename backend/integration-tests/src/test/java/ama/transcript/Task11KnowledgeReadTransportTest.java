package ama.transcript;
import ama.task.PreparationCancellation;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class Task11KnowledgeReadTransportTest {
 @TempDir Path directory;
 static final String PAGE="{\"providerId\":\"kb\",\"storeInstanceId\":\"store\",\"items\":[],\"cursor\":null}";
 static final class Cancel implements PreparationCancellation {
  final AtomicBoolean stopped=new AtomicBoolean();final AtomicReference<Runnable> abort=new AtomicReference<>();
  public boolean cancelled(){return stopped.get();}
  public AutoCloseable register(Runnable value){abort.set(value);if(stopped.get())value.run();return ()->abort.compareAndSet(value,null);}
  void stop(){stopped.set(true);var action=abort.get();if(action!=null)action.run();}
 }
 @FunctionalInterface interface Serve{void respond(Socket socket,CountDownLatch release)throws Exception;}
 final class Harness implements AutoCloseable {
  final ServerSocket server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));
  final CountDownLatch accepted=new CountDownLatch(1),release=new CountDownLatch(1);final AtomicReference<String> request=new AtomicReference<>();final AtomicReference<Throwable> failure=new AtomicReference<>();
  final Thread responder;final ControlledKnowledgeClient client;final Task11DiscoveryServiceBoundaryTest.Fixture fixture;
  Harness(Serve serve)throws Exception {
   var pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();Path root=Files.createDirectory(directory.resolve(UUID.randomUUID().toString()));
   Path bearer=privateFile(root.resolve("bearer"),"A".repeat(43).getBytes(StandardCharsets.US_ASCII)),signing=privateFile(root.resolve("signing"),pair.getPrivate().getEncoded()),assertion=privateFile(root.resolve("assertion"),pair.getPublic().getEncoded());
   var configuration=new LinkedHashMap<String,Object>();configuration.put("role","application");configuration.put("providerId","kb");configuration.put("storeInstanceId","store");configuration.put("clientId","client");configuration.put("delegationKeyId","delegation");configuration.put("assertionKeyId","assertion");configuration.put("paths",Map.of("serviceBearer",bearer.toString(),"delegationPrivateKey",signing.toString(),"providerAssertionPublicKey",assertion.toString()));
   client=new ControlledKnowledgeClient(privateFile(root.resolve("application.json"),ControlledKnowledgeClient.canonical(configuration)),new URI("http://127.0.0.1:"+server.getLocalPort()),"ISOLATED_LOOPBACK_TEST");fixture=new Task11DiscoveryServiceBoundaryTest.Fixture(client);
   responder=new Thread(()->{try(var socket=server.accept()){socket.setSoTimeout(3000);var in=socket.getInputStream();var header=new ByteArrayOutputStream();int last=0;while(header.size()<16384){int b=in.read();if(b<0)throw new EOFException();header.write(b);last=(last<<8)|b;if(last==0x0d0a0d0a)break;}request.set(header.toString(StandardCharsets.US_ASCII));accepted.countDown();serve.respond(socket,release);}catch(Throwable error){if(!server.isClosed())failure.set(error);}},"b4-owned-responder");responder.setDaemon(true);responder.start();
  }
  Map<String,Object> call(Cancel cancel)throws Exception{return fixture.service.destinations(Task11DiscoveryServiceBoundaryTest.ACTOR,Task11DiscoveryServiceBoundaryTest.M,null,Map.of(),cancel);}
  public void close()throws Exception{release.countDown();server.close();client.close();responder.join(5000);assertFalse(responder.isAlive());if(failure.get()!=null)throw new AssertionError("Owned responder failed",failure.get());}
 }
 Path privateFile(Path path,byte[] bytes)throws IOException{Files.write(path,bytes,StandardOpenOption.CREATE_NEW);Files.setPosixFilePermissions(path,PosixFilePermissions.fromString("rw-------"));return path;}
 static void wire(Socket socket,String wire)throws IOException{socket.getOutputStream().write(wire.getBytes(StandardCharsets.UTF_8));socket.getOutputStream().flush();}
 static String ok(String body){return "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "+body.getBytes(StandardCharsets.UTF_8).length+"\r\n\r\n"+body;}
 @Test void actualServiceSignsViewerAndFixedGetThenParsesRealWire()throws Exception {
  try(var h=new Harness((socket,release)->wire(socket,ok(PAGE)))){assertEquals(List.of(),h.call(new Cancel()).get("items"));assertTrue(h.request.get().startsWith("GET /v1/knowledge/libraries?limit=20 HTTP/1.1"));String header=Arrays.stream(h.request.get().split("\r\n")).filter(line->line.startsWith("X-Controlled-Delegation:")).findFirst().orElseThrow();String token=header.substring(header.indexOf(':')+1).strip();var claims=ControlledKnowledgeClient.parse(ControlledKnowledgeClient.unbase64(token.split("\\.")[0]),3072);assertEquals("real-viewer",claims.get("actorId"));assertEquals("DESTINATION_READ",claims.get("purpose"));assertEquals("/v1/knowledge/libraries?limit=20",claims.get("resource"));assertEquals(1,h.client.traffic().get("getWriteAttempts"));assertEquals(0,h.client.traffic().get("postWriteAttempts"));assertEquals(2,h.fixture.roots.get());}
 }
 @Test void malformedFramingAndPayloadNeverReachFinalReadOrRetry()throws Exception {
  var cases=List.of(ok(PAGE).replace("Content-Length:","Content-Encoding: gzip\r\nContent-Length:"),ok(PAGE).replace("Content-Length:","Transfer-Encoding: chunked\r\nContent-Length:"),ok(PAGE).replace("Content-Length:","Content-Type: application/json\r\nContent-Length:"),"HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 262145\r\n\r\n", "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 100\r\n\r\n{}",ok(PAGE+"{}"),ok(PAGE.replace("\"items\":[]","\"items\":[],\"items\":[]")),ok(PAGE.replace("\"store\"","\"wrong\"")),ok(PAGE.replace("\"cursor\":null","\"cursor\":null,\"url\":\"https://evil\"")),"HTTP/1.1 302 Found\r\nContent-Type: application/json\r\nContent-Length: 2\r\nLocation: https://evil\r\n\r\n{}");
  for(String response:cases)try(var h=new Harness((socket,release)->wire(socket,response))){assertThrows(Exception.class,()->h.call(new Cancel()));assertEquals(1,h.fixture.roots.get());assertEquals(1,h.client.traffic().get("getWriteAttempts"));assertEquals(0,h.client.traffic().get("postWriteAttempts"));}
 }
 @Test void cancellationClosesHeaderBodyAndErrorBodyReads()throws Exception {
  for(String prefix:List.of("","HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 100\r\n\r\n{","HTTP/1.1 403 Forbidden\r\nContent-Type: application/json\r\nContent-Length: 100\r\n\r\n{"))try(var h=new Harness((socket,release)->{wire(socket,prefix);assertTrue(release.await(8,TimeUnit.SECONDS));})){var cancel=new Cancel();var thread=new Thread(()->{try{if(h.accepted.await(3,TimeUnit.SECONDS))cancel.stop();}catch(InterruptedException error){Thread.currentThread().interrupt();}},"b4-owned-cancel");thread.start();long start=System.nanoTime();try{assertThrows(Exception.class,()->h.call(cancel));}finally{thread.join(4000);}assertFalse(thread.isAlive());assertTrue(System.nanoTime()-start<TimeUnit.SECONDS.toNanos(4));assertNull(cancel.abort.get());assertEquals(1,h.fixture.roots.get());}
 }
 @Test void stalledHeadersBodyAndErrorBodyHonorExistingSocketTimeout()throws Exception {for(String prefix:List.of("","HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 100\r\n\r\n{","HTTP/1.1 403 Forbidden\r\nContent-Type: application/json\r\nContent-Length: 100\r\n\r\n{"))try(var h=new Harness((socket,release)->{wire(socket,prefix);assertTrue(release.await(9,TimeUnit.SECONDS));})){long start=System.nanoTime();assertThrows(IOException.class,()->h.call(new Cancel()));long duration=System.nanoTime()-start;assertTrue(duration>=TimeUnit.SECONDS.toNanos(4)&&duration<TimeUnit.SECONDS.toNanos(8));assertEquals(1,h.fixture.roots.get());}}
}
