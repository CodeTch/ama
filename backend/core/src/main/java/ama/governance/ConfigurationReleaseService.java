package ama.governance;
import ama.contract.GovernanceContracts.*;
import java.util.*;
public final class ConfigurationReleaseService {
 public static void validate(ModelConfig candidate,Metrics metrics,boolean reviewed,Set<Kind> kinds,ModelConfig evaluated){
  if(!candidate.equals(evaluated)||metrics.sampleCount()<1||metrics.failed()!=0||metrics.succeeded()!=metrics.sampleCount()||!reviewed)throw new IllegalArgumentException("Complete reviewed evaluation of exact configuration required");
  if(candidate.voiceThreshold()!=null&&(!kinds.contains(Kind.VOICE_SAME)||!kinds.contains(Kind.VOICE_DIFFERENT)||metrics.score()<0.8))throw new IllegalArgumentException("Positive and negative voice calibration required");
 }
}
