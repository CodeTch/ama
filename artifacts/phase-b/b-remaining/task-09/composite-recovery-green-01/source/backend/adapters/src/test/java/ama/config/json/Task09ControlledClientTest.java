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
}
