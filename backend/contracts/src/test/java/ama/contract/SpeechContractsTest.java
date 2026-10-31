package ama.contract;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpeechContractsTest {
    @Test
    void optionalRecognitionTokensAndAnonymousSpeakersRemainAbsentOrAnonymous() {
        var result = new SpeechContracts.RecognitionResult("你好", null);
        var speaker = new SpeechContracts.SpeakerSpan(0, 1200, "anonymous-1");

        assertNull(result.tokens());
        assertEquals("anonymous-1", speaker.speakerId());
        assertNull(speaker.enterpriseId());
    }

    @Test
    void wavClipAndVoiceEmbeddingValidateBounds() {
        assertThrows(IllegalArgumentException.class, () -> new SpeechContracts.WavClip("file", -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new SpeechContracts.WavClip("file", 3, 0));
        assertThrows(IllegalArgumentException.class, () -> new SpeechContracts.VoiceEmbedding("model", 2, List.of(1d)));
    }
}
