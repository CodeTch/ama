package ama.transcript;

import ama.contract.IdentityContracts.*;
import ama.contract.TranscriptContracts.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SavedSpeechCorrectionIdentityTest {
 final Actor actor=new Actor("correction-owner",Status.ACTIVE,Set.of(Role.USER));
 final String meeting="00000000-0000-4000-8000-000000000001",source="00000000-0000-4000-8000-000000000002",first="00000000-0000-4000-8000-000000000003",second="00000000-0000-4000-8000-000000000004";
 Command decision(String id,CorrectionDecision decision){return new Command(Operation.CORRECTION,7,source,List.of(),null,new Correction(id,decision));}
 @Test void batchBindsAllCandidatesAndRejectsDuplicates(){
  var batch=new Correction(first,CorrectionDecision.ACCEPT,List.of(second));
  assertEquals(List.of(first,second),batch.candidateIds());
  var command=new Command(Operation.CORRECTION,7,source,List.of(),null,batch);
  assertNotEquals(JdbcSavedTranscriptReceipts.identity(actor,meeting,"batch-key",decision(first,CorrectionDecision.ACCEPT)).request(),JdbcSavedTranscriptReceipts.identity(actor,meeting,"batch-key",command).request());
  assertThrows(IllegalArgumentException.class,()->new Correction(first,CorrectionDecision.ACCEPT,List.of(first)));
  assertThrows(IllegalArgumentException.class,()->new Correction(first,CorrectionDecision.ACCEPT,List.of(second,second)));
 }
 @Test void candidateAndDecisionArePartOfTheImmutableOriginalRequest(){
  var accept=JdbcSavedTranscriptReceipts.identity(actor,meeting,"same-original-key",decision(first,CorrectionDecision.ACCEPT));
  assertEquals(accept,JdbcSavedTranscriptReceipts.identity(actor,meeting,"same-original-key",decision(first,CorrectionDecision.ACCEPT)));
  assertNotEquals(accept.request(),JdbcSavedTranscriptReceipts.identity(actor,meeting,"same-original-key",decision(second,CorrectionDecision.ACCEPT)).request());
  assertNotEquals(accept.request(),JdbcSavedTranscriptReceipts.identity(actor,meeting,"same-original-key",decision(first,CorrectionDecision.REJECT)).request());
  assertEquals("TRANSCRIPT_CORRECTION",accept.operation());
 }
 @Test void clientCannotAttachArbitraryTextOrSmuggleADecisionThroughSave(){
  assertThrows(IllegalArgumentException.class,()->new Command(Operation.SAVE,7,source,List.of(new Edit(first,"client text",List.of())),null,new Correction(first,CorrectionDecision.ACCEPT)));
  assertThrows(RuntimeException.class,()->new Command(Operation.CORRECTION,7,source,List.of(new Edit(first,"injected",List.of())),null,new Correction(first,CorrectionDecision.ACCEPT)));
  assertThrows(IllegalArgumentException.class,()->new Command(Operation.CORRECTION,7,source,List.of()));
  assertEquals(ama.contract.MinutesContracts.FailureCode.INVALID_ID,assertThrows(ama.contract.MinutesContracts.Failure.class,()->new Correction("not-a-server-candidate",CorrectionDecision.ACCEPT)).code());
 }
}
