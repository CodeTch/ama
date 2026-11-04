package ama.governance;
import ama.contract.GovernanceContracts.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class EvaluationQualificationTest {
 final QualificationRules rules=new QualificationRules(.2,.2,.8,.9,.9,.8,.1,.1,1);
 Sample s(String id,Kind kind){return new Sample(id,kind,"input",kind.name().startsWith("VOICE")?"second":null,kind==Kind.VOICE_SAME?"true":kind==Kind.VOICE_DIFFERENT?"false":"reference");}
 SampleResult result(String id,Map<String,Double> measures){return new SampleResult(id,true,1,null,"actual",measures,5,"m","p",false);}
 @Test void unrelatedExcellentScoresCannotHideVoiceFalseAccept(){
  var samples=List.of(s("same",Kind.VOICE_SAME),s("different",Kind.VOICE_DIFFERENT),s("asr",Kind.ASR));
  var outputs=List.of(result("same",Map.of("accepted",1d)),result("different",Map.of("accepted",1d)),result("asr",Map.of("cer",0d)));
  assertFalse(EvaluationQualification.assess(samples,outputs,rules,true,Map.of()).eligible());
 }
 @Test void validMixedDatasetCanQualifyIndependently(){
  var samples=List.of(s("same",Kind.VOICE_SAME),s("different",Kind.VOICE_DIFFERENT),s("asr",Kind.ASR));
  var outputs=List.of(result("same",Map.of("accepted",1d)),result("different",Map.of("accepted",0d)),result("asr",Map.of("cer",.1)));
  assertTrue(EvaluationQualification.assess(samples,outputs,rules,true,Map.of()).eligible());
  assertFalse(EvaluationQualification.assess(samples,outputs,null,true,Map.of()).eligible());
 }
 @Test void minutesRequireThreeHumanDimensionsAndFailuresStayBlocking(){
  var samples=List.of(s("minutes",Kind.MINUTES));var output=List.of(result("minutes",Map.of("textSimilarity",1d)));
  assertFalse(EvaluationQualification.assess(samples,output,rules,false,Map.of()).eligible());
  assertTrue(EvaluationQualification.assess(samples,output,rules,false,Map.of("minutes",Map.of("factuality",1d,"actions",1d,"evidence",1d))).eligible());
  assertFalse(EvaluationQualification.assess(samples,List.of(new SampleResult("minutes",false,0,"TIMEOUT")),rules,false,Map.of("minutes",Map.of("factuality",1d,"actions",1d,"evidence",1d))).eligible());
 }
}
