package ama.model;

import ama.contract.SpeechContracts;
import java.util.List;

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
}
