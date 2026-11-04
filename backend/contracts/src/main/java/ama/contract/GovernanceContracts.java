package ama.contract;
import java.util.*;
/** Versioned, bounded governance data; no endpoint, command or credential is a user setting. */
public final class GovernanceContracts {
 private GovernanceContracts(){}
 public enum Kind { ASR, DIARIZATION, MINUTES, VOICE_SAME, VOICE_DIFFERENT, ENTITY, EVIDENCE }
 public record ModelConfig(String modelVersion,String promptVersion,Double voiceThreshold){
  public ModelConfig {token(modelVersion);token(promptVersion);if(voiceThreshold!=null&&(!Double.isFinite(voiceThreshold)||voiceThreshold<=0||voiceThreshold>1))throw new IllegalArgumentException("Evaluated threshold required");}
 }
 public record Sample(String id,Kind kind,String input,String input2,String expected){
  public Sample {token(id);Objects.requireNonNull(kind);bounded(input,5_120_060);if(input2!=null)bounded(input2,5_120_060);bounded(expected,8192);if(Set.of(Kind.MINUTES,Kind.ENTITY,Kind.EVIDENCE).contains(kind)&&input.length()>16384)throw new IllegalArgumentException("Bounded minutes input required");if((kind==Kind.VOICE_SAME||kind==Kind.VOICE_DIFFERENT)&&(input2==null||!Set.of("true","false").contains(expected)||!Boolean.toString(kind==Kind.VOICE_SAME).equals(expected)))throw new IllegalArgumentException("Explicit voice pair reference required");}
 }
 public record SampleResult(String id,boolean succeeded,double score,String error,String actual,Map<String,Double> measures,long durationMillis,String modelVersion,String promptVersion,boolean requiresHumanReview){
  public SampleResult{token(id);if(!Double.isFinite(score)||score<0||score>1||!succeeded&&score!=0||durationMillis<0)throw new IllegalArgumentException("Bounded score required");measures=measures==null?Map.of():Map.copyOf(measures);if(measures.values().stream().anyMatch(v->v==null||!Double.isFinite(v)||v< -1))throw new IllegalArgumentException("Finite metrics required");if(actual!=null&&actual.length()>65536)throw new IllegalArgumentException("Bounded actual output required");}
  public SampleResult(String id,boolean succeeded,double score,String error){this(id,succeeded,score,error,null,Map.of(),0,null,null,true);}
 }
 public record QualificationRules(double maxCer,double maxDer,double minEntityF1,double minEvidenceCoverage,double minEvidenceCorrectness,double minMinutesRubric,double maxFar,double maxFrr,int minimumPerKind){
  public QualificationRules{for(double v:new double[]{maxCer,maxDer,minEntityF1,minEvidenceCoverage,minEvidenceCorrectness,minMinutesRubric,maxFar,maxFrr})if(!Double.isFinite(v)||v<0||v>1)throw new IllegalArgumentException("Qualification bounds must be in [0,1]");if(minimumPerKind<1||minimumPerKind>50)throw new IllegalArgumentException("1 to 50 samples per kind required");}
 }
 public record Metrics(int sampleCount,int succeeded,int failed,double score){}
 public static void token(String value){if(value==null||!value.matches("[A-Za-z0-9_.:/-]{1,128}"))throw new IllegalArgumentException("Bounded version identifier required");}
 public static void bounded(String value,int max){if(value==null||value.isBlank()||value.length()>max||value.indexOf(0)>=0)throw new IllegalArgumentException("Bounded text required");MinutesContracts.scalars(value);}
}
