package ama.storage;

import ama.contract.TranscriptContracts.*;
import ama.model.OfflineCorrectionFixture;
import java.nio.file.Path;
import java.util.*;
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

 @Test void llmReplacementIsPendingUntilAcceptedAndUndoRestoresTheOriginal()throws Exception {
  support.root=root;String original="联调"+id()+"因该复核",punctuated=original+"。",corrected=punctuated.replace("因该","应该");
  support.scenario("llm","/v1/chat/completions",OfflineCorrectionFixture.match(punctuated),Map.of("choices",List.of(Map.of("message",Map.of("content","[{\"seg\":0,\"old\":\"因该\",\"new\":\"应该\",\"why\":\"同音错字\",\"entityType\":\"术语\",\"confidence\":0.875}]"),"finish_reason","stop"))));
  try(var f=support.fixture(1,original,punctuated)){
   assertEquals(6,f.checkpoints());var service=support.service(f);String working=support.working(f,service),candidate=support.candidate(f,service,working);
   var items=(List<?>)service.readCorrections(f.actor,f.meeting,working).get("items");
   var item=(Map<?,?>)items.getFirst();assertEquals(punctuated,item.get("punctuationAlternative"));
   var entity=(Map<?,?>)((List<?>)item.get("entities")).getFirst();assertEquals("术语",entity.get("type"));assertEquals(0.875,entity.get("confidence"));
   assertEquals("PENDING",j.queryForObject("SELECT state FROM speech_review_candidate WHERE id=?",String.class,candidate));
   assertEquals(corrected,j.queryForObject("SELECT after_text FROM speech_review_candidate WHERE id=?",String.class,candidate));
   assertEquals(original,support.segments(service.readCurrent(f.actor,f.meeting)).getFirst().get("body"));
   var accepted=service.mutate(f.actor,f.meeting,id(),new Command(Operation.CORRECTION,support.revision(f),working,List.of(),null,new Correction(candidate,CorrectionDecision.ACCEPT)),id());String edited=support.result(accepted,"revisionId");
   assertEquals(corrected,support.segments(service.readCurrent(f.actor,f.meeting)).getFirst().get("body"));assertEquals(original,support.segments(service.readOwner(f.actor,f.meeting,support.raw(f))).getFirst().get("body"));
   var undone=service.mutate(f.actor,f.meeting,id(),new Command(Operation.UNDO,support.revision(f),edited,List.of()),id());
   assertEquals(original,support.segments(service.readCurrent(f.actor,f.meeting)).getFirst().get("body"));assertEquals("UNDONE",j.queryForObject("SELECT state FROM speech_review_candidate WHERE id=?",String.class,candidate));
   String head=support.result(undone,"revisionId");
   for(var decision:List.of(CorrectionDecision.REOPEN,CorrectionDecision.ACCEPT_PUNCTUATION,CorrectionDecision.REOPEN,CorrectionDecision.REJECT,CorrectionDecision.REOPEN)){
    var receipt=service.mutate(f.actor,f.meeting,id(),new Command(Operation.CORRECTION,support.revision(f),head,List.of(),null,new Correction(candidate,decision)),id());
    head=support.result(receipt,"revisionId");
    assertEquals(decision==CorrectionDecision.ACCEPT_PUNCTUATION?punctuated:original,support.segments(service.readCurrent(f.actor,f.meeting)).getFirst().get("body"));
    if(decision==CorrectionDecision.REOPEN)assertEquals(1,service.readCorrections(f.actor,f.meeting,head).get("pendingCount"));
   }
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
