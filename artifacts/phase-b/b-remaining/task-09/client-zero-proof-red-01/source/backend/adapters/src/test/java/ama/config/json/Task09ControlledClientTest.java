package ama.config.json;
import ama.model.*;
import ama.task.PreparationCancellation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;import java.net.*;import java.time.*;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class Task09ControlledClientTest {
 @TempDir Path dir;
 static final PreparationCancellation OPEN=new PreparationCancellation(){public boolean cancelled(){return false;}public AutoCloseable register(Runnable r){return ()->{};}};
 Object client(int port)throws Exception {return Class.forName("ama.model.ControlledMinutesClient").getConstructor(ControlledMinutesHttpTransport.class).newInstance(new ControlledMinutesHttpTransport(URI.create("http://127.0.0.1:"+port),Task09ControlledHttpTest.AUTH,"CONTROLLED_INSTALLATION",Duration.ofSeconds(2),Duration.ofSeconds(30),Duration.ofSeconds(5)));}
 Object request(Object client,Task09ControlledProtocolTest.Wire wire)throws Exception{return client.getClass().getMethod("prepare",byte[].class,String.class).invoke(client,wire.body(),wire.header());}
 Object exchange(Object client,String method,Object request)throws Exception{return client.getClass().getMethod(method,request.getClass(),PreparationCancellation.class).invoke(client,request,OPEN);}
 @Test void realAuthenticatedSuccessBindsMetadataAndExactOutputToPrivateRequest()throws Exception {
  try(var provider=new ControlledMinutesProvider(dir,new InetSocketAddress("127.0.0.1",0),Task09ControlledHttpTest.AUTH,"CONTROLLED_INSTALLATION")){provider.start();var wire=Task09ControlledProtocolTest.wire();Object client=assertDoesNotThrow(()->client(provider.port()));try(var close=(AutoCloseable)client){Object request=request(client,wire),receipt=exchange(client,"execute",request),again=exchange(client,"lookup",request);assertEquals("SUCCEEDED",receipt.getClass().getMethod("state").invoke(receipt));assertArrayEquals((byte[])receipt.getClass().getMethod("output").invoke(receipt),(byte[])again.getClass().getMethod("output").invoke(again));assertEquals(receipt.getClass().getMethod("resultId").invoke(receipt),again.getClass().getMethod("resultId").invoke(again));assertEquals(0,receipt.getClass().getConstructors().length);Object other=request(client,Task09ControlledProtocolTest.wire());assertThrows(Exception.class,()->client.getClass().getMethod("require",receipt.getClass(),request.getClass()).invoke(client,receipt,other));}}
 }
 @Test void lookupOfNeverSentExecutionIsUnknownAndDoesNotCreateProviderReservation()throws Exception {
  try(var provider=new ControlledMinutesProvider(dir,new InetSocketAddress("127.0.0.1",0),Task09ControlledHttpTest.AUTH,"CONTROLLED_INSTALLATION")){provider.start();Object client=assertDoesNotThrow(()->client(provider.port()));try(var close=(AutoCloseable)client){Object request=request(client,Task09ControlledProtocolTest.wire()),receipt=exchange(client,"lookup",request);assertEquals("UNKNOWN",receipt.getClass().getMethod("state").invoke(receipt));assertNull(receipt.getClass().getMethod("output").invoke(receipt));assertEquals(0,Files.size(dir.resolve("journal.bin")));}}
 }
 @Test void postRejectionWithoutPersistedLookupProofCannotIssueZeroEffectReceipt()throws Exception {
  var wire=Task09ControlledProtocolTest.wire();var json=new com.fasterxml.jackson.databind.ObjectMapper();var body=json.readTree(wire.body());var gets=new java.util.concurrent.atomic.AtomicInteger();var posts=new java.util.concurrent.atomic.AtomicInteger();var server=com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1",0),1);
  server.createContext("/v1/minutes/executions",exchange->{try{exchange.getRequestBody().readNBytes(16777217);var out=json.createObjectNode();for(String key:List.of("protocolVersion","executionId","executionNo","logicalBusinessKey","logicalRequestHash","executionRequestHash","payloadSha256"))out.set(key,body.get(key));if(exchange.getRequestMethod().equals("POST")){posts.incrementAndGet();out.put("state","FAILED_ZERO_EFFECT");out.put("receiptId","cmz-00000000-0000-4000-8000-000000000001");out.put("basis","DEFINITIVE_REJECTION");out.put("code","CONTROLLED_REJECTION");}else{gets.incrementAndGet();out.put("state","UNKNOWN");}byte[] bytes=json.writeValueAsBytes(out);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);}finally{exchange.close();}});server.start();
  try(var c=(ControlledMinutesClient)client(server.getAddress().getPort())){var request=c.prepare(wire.body(),wire.header());var result=c.execute(request,OPEN);assertEquals("UNKNOWN",result.state());assertEquals(1,posts.get());assertEquals(1,gets.get());assertNull(result.receiptId());}finally{server.stop(0);}
 }

}
