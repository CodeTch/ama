package ama.worker;

import ama.contract.SpeechContracts;
import ama.model.SpeechModelPort;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TranscriptionHandlerTest {
    @Test
    void successfulStagesAreKeptAndEnhancementIsSeparate() {
        SpeechModelPort model = new SpeechModelPort() {
            public List<SpeechSpan> vad(SpeechContracts.WavClip c) { return List.of(new SpeechSpan(0, 2)); }
            public List<SpeechContracts.SpeakerSpan> diarize(SpeechContracts.WavClip c, List<SpeechSpan> s) { return List.of(new SpeechContracts.SpeakerSpan(0, 2, "anonymous-1")); }
            public SpeechContracts.RecognitionResult recognize(SpeechContracts.WavClip c) { return new SpeechContracts.RecognitionResult("原始", null); }
            public String punctuate(String s) { return s + "。"; }
            public SpeechContracts.VoiceEmbedding embed(SpeechContracts.WavClip c) { return new SpeechContracts.VoiceEmbedding("m", 1, List.of(1d)); }
        };
        var result = new TranscriptionHandler(model).transcribe(new SpeechContracts.WavClip("f", 0, 2));
        assertEquals("原始", result.raw().text());
        assertEquals("原始。", result.enhancedText());
        assertNull(result.raw().tokens());
    }

    @Test
    void successfulStageSnapshotsArePersistedWithoutOverwritingRawRecognition() {
        SpeechModelPort model = new SpeechModelPort() {
            public List<SpeechSpan> vad(SpeechContracts.WavClip c) { return List.of(new SpeechSpan(0, 2)); }
            public List<SpeechContracts.SpeakerSpan> diarize(SpeechContracts.WavClip c, List<SpeechSpan> s) { return List.of(new SpeechContracts.SpeakerSpan(0, 2, "anonymous-1")); }
            public SpeechContracts.RecognitionResult recognize(SpeechContracts.WavClip c) { return new SpeechContracts.RecognitionResult("raw", null); }
            public String punctuate(String s) { return s + "!"; }
            public SpeechContracts.VoiceEmbedding embed(SpeechContracts.WavClip c) { return new SpeechContracts.VoiceEmbedding("m", 1, List.of(1d)); }
        };
        var stages = new java.util.ArrayList<String>();
        new TranscriptionHandler(model).transcribe(new SpeechContracts.WavClip("f", 0, 2), (stage, value) -> stages.add(stage));
        assertEquals(List.of("VAD", "DIARIZATION", "ASR", "ENHANCEMENT", "EMBEDDING"), stages);
    }
}
