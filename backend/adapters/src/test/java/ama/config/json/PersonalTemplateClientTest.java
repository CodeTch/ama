package ama.config.json;

import ama.model.*;
import java.net.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual bounded socket transport tests; fixed-harness business qualification follows deployment. */
class PersonalTemplateClientTest extends Task09ControlledClientTest {
 @Test void v2ProviderMetadataAndOriginalLookupUseTheVersionOfTheDurableRequest()throws Exception{
  var wire=PersonalTemplateProtocolTest.personal(PersonalTemplateProtocolTest.PERSONAL,List.of("会议决定"));
  try(var provider=new ControlledMinutesProvider(dir,new InetSocketAddress("127.0.0.1",0),Task09ControlledHttpTest.AUTH,"CONTROLLED_INSTALLATION")){
   provider.start();var http=new Task09ControlledHttpTest();var result=http.post(provider.port(),wire);assertEquals(200,result.statusCode());assertEquals(PersonalTemplateProtocolTest.PERSONAL,http.json.readTree(result.body()).path("protocolVersion").asText());
   try(var client=(ControlledMinutesClient)client(provider.port())){var request=client.prepare(wire.body(),wire.header());var original=client.lookup(request,OPEN);assertEquals("SUCCEEDED",original.state());assertTrue(http.json.readTree(original.output()).path("body").asText().contains("## 会议决定"));var again=client.execute(request,OPEN);assertEquals(original.resultId(),again.resultId());assertArrayEquals(original.output(),again.output());}
  }
 }
 @Test void aV1ResponseCannotAcknowledgeAnOriginalV2Request()throws Exception{
  var wire=PersonalTemplateProtocolTest.personal(PersonalTemplateProtocolTest.PERSONAL,List.of("会议决定"));var json=new com.fasterxml.jackson.databind.ObjectMapper();var input=json.readTree(wire.body());
  var server=com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1",0),1);
  server.createContext("/v1/minutes/executions",exchange->{try{exchange.getRequestBody().readNBytes(16777217);var result=json.createObjectNode();for(String field:List.of("executionId","executionNo","logicalBusinessKey","logicalRequestHash","executionRequestHash","payloadSha256"))result.set(field,input.get(field));result.put("protocolVersion","ama.controlled-minutes-http.v1");result.put("state","UNKNOWN");byte[] bytes=json.writeValueAsBytes(result);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);}finally{exchange.close();}});server.start();
  try(var client=(ControlledMinutesClient)client(server.getAddress().getPort())){var request=client.prepare(wire.body(),wire.header());assertThrows(RuntimeException.class,()->client.execute(request,OPEN));}finally{server.stop(0);}
 }
 @Test void neverSentV2LookupRemainsUnknownWithoutCreatingAnyReservation()throws Exception{
  var wire=PersonalTemplateProtocolTest.personal(PersonalTemplateProtocolTest.PERSONAL,List.of("会议决定"));
  try(var provider=new ControlledMinutesProvider(dir,new InetSocketAddress("127.0.0.1",0),Task09ControlledHttpTest.AUTH,"CONTROLLED_INSTALLATION")){
   provider.start();try(var client=(ControlledMinutesClient)client(provider.port())){var request=client.prepare(wire.body(),wire.header());assertEquals("UNKNOWN",client.lookup(request,OPEN).state());assertEquals(0,java.nio.file.Files.size(dir.resolve("journal.bin")));}
  }
 }
}
