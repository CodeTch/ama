package ama.governance;

import ama.contract.GovernanceContracts.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GovernanceProviderEvidenceTest {
 @Test void languageEvaluationsReportTheRequestedModelAndTheirOwnPrompt() throws Exception {
  var json=new ObjectMapper();var requests=new ArrayList<String>();
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/v1/chat/completions",exchange->{try{
   var request=json.readTree(exchange.getRequestBody().readAllBytes());requests.add(request.toString());
   String input=request.path("messages").get(1).path("content").asText();
   String output=switch(input){case "entity input"->"[\"PERSON:张三\"]";case "evidence input"->"{\"c1\":[\"s1\"]}";default->"实际纪要";};
   byte[] body=json.writeValueAsBytes(Map.of("choices",List.of(Map.of("message",Map.of("content",output)))));
   exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);
  }finally{exchange.close();}});server.start();
  try {
   var provider=new GovernanceHttpProvider(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),"harness-fixture-embedding-v1",256);
   var config=new ModelConfig("harness-fixture-embedding-v1","summary-v2",null);
   for(var sample:List.of(new Sample("m",Kind.MINUTES,"minutes input",null,"秘密参考"),new Sample("e",Kind.ENTITY,"entity input",null,"[\"PERSON:张三\"]"),new Sample("l",Kind.EVIDENCE,"evidence input",null,"{\"c1\":[\"s1\"]}"))){
    var result=provider.evaluate(sample,config);assertEquals("deepseek-v4-flash",result.modelVersion());
    assertEquals(switch(sample.kind()){case MINUTES->"summary-v2";case ENTITY->"entity-extraction-v1";default->"evidence-links-v1";},result.promptVersion());
   }
   assertEquals(3,requests.size());assertTrue(requests.stream().allMatch(r->r.contains("deepseek-v4-flash")));assertFalse(requests.stream().anyMatch(r->r.contains("秘密参考")));
  }finally{server.stop(0);}
 }
}
