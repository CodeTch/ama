package ama.model;

import ama.contract.SpeechContracts;
import java.util.List;
import java.util.Optional;

/** Provider-neutral speech pipeline boundary. Implementations must preserve absent optional values. */
public interface SpeechModelPort {
    record SpeechSpan(long startSample, long endSample) {
        public SpeechSpan {
            if (startSample < 0 || endSample <= startSample) throw new IllegalArgumentException("Invalid speech span");
        }
    }

    List<SpeechSpan> vad(SpeechContracts.WavClip clip);
    List<SpeechContracts.SpeakerSpan> diarize(SpeechContracts.WavClip clip, List<SpeechSpan> speech);
    SpeechContracts.RecognitionResult recognize(SpeechContracts.WavClip clip);
    String punctuate(String text);
    SpeechContracts.VoiceEmbedding embed(SpeechContracts.WavClip clip);
    /** Absence is distinct from an empty successful correction response. */
    default Optional<String> correctionModel() { return Optional.empty(); }
    default SpeechContracts.CorrectionResult correct(String text) { throw new UnsupportedOperationException("Correction provider not installed"); }
    default SpeechContracts.CorrectionResult correct(String text,java.util.List<String> entities) { if(!entities.isEmpty())throw new UnsupportedOperationException("Entity context unavailable");return correct(text); }
}
