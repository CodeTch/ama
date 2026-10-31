package ama.worker;

import ama.contract.SpeechContracts;
import ama.model.SpeechModelPort;
import java.util.List;
import java.util.Objects;

/** Executes speech stages once and keeps raw recognition separate from enhancement. */
public final class TranscriptionHandler {
    @FunctionalInterface public interface StageStore { void save(String stage, Object value); }
    public record Result(List<SpeechModelPort.SpeechSpan> speech, List<SpeechContracts.SpeakerSpan> speakers,
                         SpeechContracts.RecognitionResult raw, String enhancedText, SpeechContracts.VoiceEmbedding embedding) {
        public Result {
            speech = List.copyOf(speech); speakers = List.copyOf(speakers); Objects.requireNonNull(raw); Objects.requireNonNull(enhancedText); Objects.requireNonNull(embedding);
        }
    }
    private final SpeechModelPort model;
    public TranscriptionHandler(SpeechModelPort model) { this.model = Objects.requireNonNull(model); }
    public Result transcribe(SpeechContracts.WavClip clip) {
        return transcribe(clip, (stage, value) -> {});
    }
    public Result transcribe(SpeechContracts.WavClip clip, StageStore store) {
        Objects.requireNonNull(clip);
        Objects.requireNonNull(store);
        var speech = List.copyOf(model.vad(clip));
        store.save("VAD", speech);
        var speakers = List.copyOf(model.diarize(clip, speech));
        store.save("DIARIZATION", speakers);
        var raw = Objects.requireNonNull(model.recognize(clip));
        store.save("ASR", raw);
        var enhanced = Objects.requireNonNull(model.punctuate(raw.text()));
        store.save("ENHANCEMENT", enhanced);
        var embedding = Objects.requireNonNull(model.embed(clip));
        store.save("EMBEDDING", embedding);
        return new Result(speech, speakers, raw, enhanced, embedding);
    }
}
