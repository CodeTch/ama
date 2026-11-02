package ama.governance;
import ama.contract.GovernanceContracts.*;
import ama.contract.SpeechContracts.VoiceEmbedding;
public interface GovernanceModelPort {
 VoiceEmbedding embed(byte[] wav,String modelVersion);
 SampleResult evaluate(Sample sample,ModelConfig config);
}
