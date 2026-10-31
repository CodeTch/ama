package ama.model;

import ama.contract.SpeechContracts;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpeechModelPortTest {
    @Test
    void modelPortKeepsOptionalTokensAndAnonymousIdentity() {
        var port = new SpeechModelPort() {
            public List<SpeechModelPort.SpeechSpan> vad(SpeechContracts.WavClip clip) { return List.of(new SpeechModelPort.SpeechSpan(0, 10)); }
            public List<SpeechContracts.SpeakerSpan> diarize(SpeechContracts.WavClip clip, List<SpeechModelPort.SpeechSpan> spans) { return List.of(new SpeechContracts.SpeakerSpan(0, 10, "anonymous-1")); }
            public SpeechContracts.RecognitionResult recognize(SpeechContracts.WavClip clip) { return new SpeechContracts.RecognitionResult("hello", null); }
            public String punctuate(String text) { return text + "。"; }
            public SpeechContracts.VoiceEmbedding embed(SpeechContracts.WavClip clip) { return new SpeechContracts.VoiceEmbedding("test", 2, List.of(0d, 1d)); }
        };
        assertNull(port.recognize(new SpeechContracts.WavClip("f", 0, 1)).tokens());
        assertNull(port.diarize(new SpeechContracts.WavClip("f", 0, 1), List.of()).getFirst().enterpriseId());
    }
}
