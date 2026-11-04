package ama.governance;
import ama.contract.GovernanceContracts.*;
import java.util.*;
public final class ConfigurationReleaseService {
 public static void validate(ModelConfig candidate,Metrics metrics,boolean reviewed,Set<Kind> kinds,ModelConfig evaluated){
  if(!candidate.equals(evaluated)||metrics.sampleCount()<1||metrics.failed()!=0||metrics.succeeded()!=metrics.sampleCount()||!reviewed)throw new IllegalArgumentException("Complete reviewed evaluation of exact configuration required");
  // This legacy aggregate cannot prove a voice-specific result for a mixed dataset.
  // A later per-kind report may qualify it; unrelated scores must never authorize a threshold.
  if(candidate.voiceThreshold()!=null&&(!kinds.equals(Set.of(Kind.VOICE_SAME,Kind.VOICE_DIFFERENT))||metrics.score()<0.8))throw new IllegalArgumentException("Independent positive and negative voice calibration required");
 }
}
