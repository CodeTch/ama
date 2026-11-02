package ama.storage;

import ama.contract.TranscriptContracts.*;
import ama.model.HttpSpeechModelPort;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static ama.storage.ControlledSpeechImportIT.*;

/** Actual task -> LLM checkpoint -> pending proposal -> explicit immutable human decision. */
class OfflineSpeechReviewIT {
 @TempDir Path root;
 final SpeechReviewIT support=new SpeechReviewIT();
 @AfterEach void cleanup()throws Exception{support.clearScenarios();}
 @BeforeAll static void schema(){ControlledSpeechImportIT.schema();assertEquals(1,j.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version='061'",Integer.class));}

 private Map<String,Object> correctionMatch(String text)throws Exception {
  var request=new AtomicReference<com.fasterxml.jackson.databind.JsonNode>();
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/v1/chat/completions",exchange->{
   request.set(SpeechReviewIT.JSON.readTree(exchange.getRequestBody().readAllBytes()));
   byte[] response=SpeechReviewIT.JSON.writeValueAsBytes(Map.of("choices",List.of(Map.of("message",Map.of("content","[]")))));
   exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
  });server.start();
  try(var client=new HttpSpeechModelPort(HttpSpeechModelPort.Config.at(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),"localhost",null,"fixture",256,Duration.ofSeconds(2)),c->{throw new AssertionError("Text request must not read audio");})){client.correct(text);}
  finally{server.stop(0);}
  return Map.of("field_equals",Map.of("messages",request.get().path("messages")));
 }
 @Test void llmReplacementIsPendingUntilAcceptedAndUndoRestoresTheOriginal()throws Exception {
  support.root=root;String original="联调"+id()+"因该复核",punctuated=original+"。",corrected=punctuated.replace("因该","应该");
  support.scenario("llm","/v1/chat/completions",correctionMatch(punctuated),Map.of("choices",List.of(Map.of("message",Map.of("content","[{\"seg\":0,\"old\":\"因该\",\"new\":\"应该\",\"why\":\"同音错字\"}]"),"finish_reason","stop"))));
  try(var f=support.fixture(1,original,punctuated)){
   assertEquals(6,f.checkpoints());var service=support.service(f);String working=support.working(f,service),candidate=support.candidate(f,service,working);
   assertEquals("PENDING",j.queryForObject("SELECT state FROM speech_review_candidate WHERE id=?",String.class,candidate));
   assertEquals(corrected,j.queryForObject("SELECT after_text FROM speech_review_candidate WHERE id=?",String.class,candidate));
   assertEquals(original,support.segments(service.readCurrent(f.actor,f.meeting)).getFirst().get("body"));
   var accepted=service.mutate(f.actor,f.meeting,id(),new Command(Operation.CORRECTION,support.revision(f),working,List.of(),null,new Correction(candidate,CorrectionDecision.ACCEPT)),id());String edited=support.result(accepted,"revisionId");
   assertEquals(corrected,support.segments(service.readCurrent(f.actor,f.meeting)).getFirst().get("body"));assertEquals(original,support.segments(service.readOwner(f.actor,f.meeting,support.raw(f))).getFirst().get("body"));
   service.mutate(f.actor,f.meeting,id(),new Command(Operation.UNDO,support.revision(f),edited,List.of()),id());
   assertEquals(original,support.segments(service.readCurrent(f.actor,f.meeting)).getFirst().get("body"));assertEquals("UNDONE",j.queryForObject("SELECT state FROM speech_review_candidate WHERE id=?",String.class,candidate));
  }
 }
 @Test void historicalFiveStageTaskStillOffersItsSavedPunctuation()throws Exception {
  support.root=root;
  try(var f=support.fixture()){
   assertEquals(1,j.update("DELETE FROM speech_checkpoint WHERE input_id=? AND stage='CORRECTION'",f.input));
   var service=support.service(f);String working=support.working(f,service),candidate=support.candidate(f,service,working);
   assertEquals(SpeechReviewIT.ENHANCED,j.queryForObject("SELECT after_text FROM speech_review_candidate WHERE id=?",String.class,candidate));
  }
 }
}
