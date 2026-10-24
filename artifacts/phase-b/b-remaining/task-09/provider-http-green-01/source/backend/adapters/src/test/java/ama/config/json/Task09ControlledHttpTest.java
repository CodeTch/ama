package ama.config.json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
class Task09ControlledHttpTest {
 @TempDir Path dir;
 static final String AUTH="test-only-task09-controlled-authorization-value";
 final ObjectMapper json=new ObjectMapper();
 Class<?> provider(){return assertDoesNotThrow(()->Class.forName("ama.model.ControlledMinutesProvider"),"real bounded HTTP controlled provider must exist");}
 AutoCloseable server()throws Exception {Object s=provider().getConstructor(Path.class,InetSocketAddress.class,String.class,String.class).newInstance(dir,new InetSocketAddress("127.0.0.1",0),AUTH,"CONTROLLED_INSTALLATION");s.getClass().getMethod("start").invoke(s);return (AutoCloseable)s;}
 int port(Object s)throws Exception{return (int)s.getClass().getMethod("port").invoke(s);}
 HttpResponse<byte[]> post(int port,Task09ControlledProtocolTest.Wire w)throws Exception {
  try(var c=HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build()){
   return c.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/v1/minutes/executions")).timeout(Duration.ofSeconds(5)).header("Authorization","Bearer "+AUTH).header("Content-Type","application/json").header("X-Ama-Minutes-Request-Envelope",w.header()).POST(HttpRequest.BodyPublishers.ofByteArray(w.body())).build(),HttpResponse.BodyHandlers.ofByteArray());
  }
 }
 String raw(int port,String headers)throws Exception {
  try(var s=new Socket()){s.connect(new InetSocketAddress("127.0.0.1",port),2000);s.setSoTimeout(3000);s.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));s.getOutputStream().flush();return new String(s.getInputStream().readNBytes(2048),StandardCharsets.US_ASCII);}
 }
 String prefix(int port){return "POST /v1/minutes/executions HTTP/1.1\r\nHost: 127.0.0.1:"+port+"\r\nAuthorization: Bearer "+AUTH+"\r\nContent-Type: application/json\r\nContent-Length: 0\r\n";}
 @Test void actualHttpDuplicateRetainsOneDurableResult()throws Exception {
  var w=Task09ControlledProtocolTest.wire();try(var s=server()){var first=post(port(s),w);assertEquals(200,first.statusCode(),new String(first.body()));var second=post(port(s),w);assertArrayEquals(first.body(),second.body());var metadata=json.readTree(first.body());assertEquals("SUCCEEDED",metadata.get("state").asText());assertTrue(metadata.get("resultId").asText().startsWith("cmr-"));
   try(var c=HttpClient.newHttpClient()){var result=c.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port(s)+"/v1/minutes/results/"+metadata.get("resultId").asText())).header("Authorization","Bearer "+AUTH).GET().build(),HttpResponse.BodyHandlers.ofByteArray());assertEquals(200,result.statusCode());assertEquals(metadata.get("outputSha256").asText(),Task09ControlledProtocolTest.sha(result.body()));}
  }
 }
 @Test void rawRepeatedCaseDuplicateCombinedAndFoldedEnvelopeRejectedBeforeReservation()throws Exception {
  var w=Task09ControlledProtocolTest.wire();try(var s=server()){int p=port(s);String h="X-Ama-Minutes-Request-Envelope: "+w.header()+"\r\n";
   for(String bad:List.of(h+h,h+"x-ama-minutes-request-envelope: "+w.header()+"\r\n","X-Ama-Minutes-Request-Envelope: "+w.header()+","+w.header()+"\r\n",h+" continuation\r\n"))assertTrue(raw(p,prefix(p)+bad+"\r\n").startsWith("HTTP/1.1 400"));
   assertEquals(0,Files.size(dir.resolve("journal.bin")));
  }
 }
 @Test void aggregateAndPerFieldLimitsRejectBeforeUnboundedParse()throws Exception {
  try(var s=server()){int p=port(s);assertTrue(raw(p,prefix(p)+"X-Large: "+"a".repeat(8193)+"\r\n\r\n").startsWith("HTTP/1.1 431"));
   assertTrue(raw(p,prefix(p)+"X-One: "+"a".repeat(8000)+"\r\nX-Two: "+"b".repeat(8000)+"\r\nX-Three: "+"c".repeat(1000)+"\r\n\r\n").startsWith("HTTP/1.1 431"));assertEquals(0,Files.size(dir.resolve("journal.bin")));
  }
 }
 @Test void unauthenticatedRequestCannotReserve()throws Exception {try(var s=server()){assertTrue(raw(port(s),"POST /v1/minutes/executions HTTP/1.1\r\nHost: 127.0.0.1:"+port(s)+"\r\nContent-Length: 0\r\n\r\n").startsWith("HTTP/1.1 401"));assertEquals(0,Files.size(dir.resolve("journal.bin")));}}
 @Test void freshProviderProcessPreservesExactEnvelopeAndResultIdentity()throws Exception {
  provider();var w=Task09ControlledProtocolTest.wire();Path secret=dir.resolve("secret");Files.writeString(secret,AUTH);Files.setPosixFilePermissions(secret,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));Path config=dir.resolve("provider.properties"),ready=dir.resolve("ready");
  Files.writeString(config,"mode=CONTROLLED_INSTALLATION\nbind=127.0.0.1\nport=0\nstore="+dir.resolve("store")+"\nsecretFile="+secret+"\nreadyFile="+ready+"\n");
  byte[] first;Process child=child(config,"first.log");try{first=post(awaitPort(child,ready),w).body();assertEquals("SUCCEEDED",json.readTree(first).get("state").asText());}finally{child.destroyForcibly();assertTrue(child.waitFor(5,java.util.concurrent.TimeUnit.SECONDS));}
  Files.delete(ready);child=child(config,"second.log");try{assertArrayEquals(first,post(awaitPort(child,ready),w).body());}finally{child.destroy();if(!child.waitFor(5,java.util.concurrent.TimeUnit.SECONDS))child.destroyForcibly();}
 }
 Process child(Path config,String log)throws Exception {return new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-Xmx64m","-XX:ActiveProcessorCount=2","-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),"ama.model.ControlledMinutesProvider",config.toString()).redirectErrorStream(true).redirectOutput(dir.resolve(log).toFile()).start();}
 int awaitPort(Process child,Path ready)throws Exception {long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);while(System.nanoTime()<end){if(Files.exists(ready))return Integer.parseInt(Files.readString(ready).trim());if(!child.isAlive())fail("provider process exited before readiness");Thread.sleep(20);}fail("provider readiness deadline");return 0;}
}
