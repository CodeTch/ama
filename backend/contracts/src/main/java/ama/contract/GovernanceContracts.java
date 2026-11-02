package ama.contract;
import java.util.*;
/** Versioned, bounded governance data; no endpoint, command or credential is a user setting. */
public final class GovernanceContracts {
 private GovernanceContracts(){}
 public enum Kind { ASR, DIARIZATION, MINUTES, VOICE_SAME, VOICE_DIFFERENT }
 public record ModelConfig(String modelVersion,String promptVersion,Double voiceThreshold){
  public ModelConfig {token(modelVersion);token(promptVersion);if(voiceThreshold!=null&&(!Double.isFinite(voiceThreshold)||voiceThreshold<=0||voiceThreshold>1))throw new IllegalArgumentException("Evaluated threshold required");}
 }
 public record Sample(String id,Kind kind,String input,String input2,String expected){
  public Sample {token(id);Objects.requireNonNull(kind);bounded(input,1_400_000);if(input2!=null)bounded(input2,1_400_000);bounded(expected,8192);if(kind==Kind.MINUTES&&input.length()>16384)throw new IllegalArgumentException("Bounded minutes input required");if((kind==Kind.VOICE_SAME||kind==Kind.VOICE_DIFFERENT)&&(input2==null||!Set.of("true","false").contains(expected)||!Boolean.toString(kind==Kind.VOICE_SAME).equals(expected)))throw new IllegalArgumentException("Explicit voice pair reference required");}
 }
 public record SampleResult(String id,boolean succeeded,double score,String error){public SampleResult{token(id);if(!Double.isFinite(score)||score<0||score>1||!succeeded&&score!=0)throw new IllegalArgumentException("Bounded score required");}}
 public record Metrics(int sampleCount,int succeeded,int failed,double score){}
 public static void token(String value){if(value==null||!value.matches("[A-Za-z0-9_.:/-]{1,128}"))throw new IllegalArgumentException("Bounded version identifier required");}
 public static void bounded(String value,int max){if(value==null||value.isBlank()||value.length()>max||value.indexOf(0)>=0)throw new IllegalArgumentException("Bounded text required");MinutesContracts.scalars(value);}
}
