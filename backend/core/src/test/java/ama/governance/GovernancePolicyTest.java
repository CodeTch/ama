package ama.governance;
import ama.contract.GovernanceContracts.*;
import ama.contract.SpeechContracts.VoiceEmbedding;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GovernancePolicyTest {
 VoiceEmbedding vector(String version){return new VoiceEmbedding(version,2,List.of(1d,0d));}
 @Test void revokedEmbeddingIsNotSearchable(){
  var service=new VoiceprintService();var now=Instant.now();
  var candidates=List.of(new VoiceprintService.Entry("a","person","APPROVED",true,now.plusSeconds(20),vector("v1")),new VoiceprintService.Entry("b","revoked","REVOKED",true,now.plusSeconds(20),vector("v1")),new VoiceprintService.Entry("c","disabled","APPROVED",false,now.plusSeconds(20),vector("v1")),new VoiceprintService.Entry("d","expired","APPROVED",true,now,vector("v1")));
  assertEquals(List.of("person"),service.match(vector("v1"),candidates,0.9,now).stream().map(VoiceprintService.Candidate::subjectId).toList());
 }
 @Test void modelVersionMismatchIsRejected(){assertThrows(IllegalArgumentException.class,()->VoiceprintService.similarity(vector("v1"),vector("v2")));assertThrows(IllegalArgumentException.class,()->VoiceprintService.similarity(vector("v1"),new VoiceEmbedding("v1",1,List.of(1d))));}
 @Test void missingEvaluatedThresholdDoesNotMatch(){assertThrows(IllegalArgumentException.class,()->new VoiceprintService().match(vector("v1"),List.of(),null,Instant.now()));}
 @Test void failedSamplesRemainInDenominator(){
  var report=EvaluationService.metrics(List.of(new SampleResult("ok",true,1d,null),new SampleResult("bad",false,0d,"TIMEOUT")));
  assertEquals(2,report.sampleCount());assertEquals(1,report.failed());assertEquals(0.5,report.score());
 }
 @Test void unicodeTranscriptQualityMeasuresActualEdits(){assertEquals(1d,EvaluationService.textScore("会议结束","会议结束"));assertEquals(0.75,EvaluationService.textScore("会议结束","会议结数"));assertEquals(0d,EvaluationService.textScore("会议结束",""));}
 @Test void releaseRequiresFullReviewedEvidenceAndVoicePairs(){
  var config=new ModelConfig("embed-v1","prompt-v1",0.8);
  assertThrows(IllegalArgumentException.class,()->ConfigurationReleaseService.validate(config,new Metrics(3,2,1,0.5),true,Set.of(Kind.ASR),config));
  assertThrows(IllegalArgumentException.class,()->ConfigurationReleaseService.validate(config,new Metrics(3,3,0,1),false,Set.of(Kind.VOICE_SAME,Kind.VOICE_DIFFERENT),config));
  assertThrows(IllegalArgumentException.class,()->ConfigurationReleaseService.validate(config,new Metrics(3,3,0,1),true,Set.of(Kind.ASR),config));
  assertDoesNotThrow(()->ConfigurationReleaseService.validate(config,new Metrics(2,2,0,1),true,Set.of(Kind.VOICE_SAME,Kind.VOICE_DIFFERENT),config));
 }
}
