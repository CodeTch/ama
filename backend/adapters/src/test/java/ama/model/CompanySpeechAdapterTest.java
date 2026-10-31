package ama.model;

import ama.contract.SpeechContracts;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompanySpeechAdapterTest {
    @Test
    void retriesRateLimitOnceWithoutInventingOptionalFields() {
        var calls = new AtomicInteger();
        SpeechModelPort delegate = new SpeechModelPort() {
            public List<SpeechSpan> vad(SpeechContracts.WavClip c) { return List.of(); }
            public List<SpeechContracts.SpeakerSpan> diarize(SpeechContracts.WavClip c, List<SpeechSpan> s) { return List.of(); }
            public SpeechContracts.RecognitionResult recognize(SpeechContracts.WavClip c) { if (calls.getAndIncrement()==0) throw new CompanySpeechAdapter.RateLimited(); return new SpeechContracts.RecognitionResult("text", null); }
            public String punctuate(String s) { return s; }
            public SpeechContracts.VoiceEmbedding embed(SpeechContracts.WavClip c) { return new SpeechContracts.VoiceEmbedding("m", 1, List.of(1d)); }
        };
        var result = new CompanySpeechAdapter(delegate, 2, Duration.ofSeconds(1)).recognize(new SpeechContracts.WavClip("f", 0, 1));
        assertEquals("text", result.text()); assertNull(result.tokens()); assertEquals(2, calls.get());
    }
}
