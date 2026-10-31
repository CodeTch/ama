package ama.contract;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Stable values exchanged by speech recognition, diarization, and review. */
public final class SpeechContracts {
    private SpeechContracts() {}

    public record RecognitionResult(String text, List<Token> tokens) {
        public RecognitionResult {
            if (text == null || text.isBlank()) throw new IllegalArgumentException("Recognition text required");
            tokens = tokens == null ? null : List.copyOf(tokens);
        }
    }

    public record Token(String text, long startSample, long endSample, Double confidence) {
        public Token {
            if (text == null || text.isBlank() || startSample < 0 || endSample <= startSample
                    || confidence != null && (!Double.isFinite(confidence) || confidence < 0 || confidence > 1)) {
                throw new IllegalArgumentException("Invalid recognition token");
            }
        }
    }

    public record SpeakerSpan(long startSample, long endSample, String speakerId, String enterpriseId) {
        public SpeakerSpan {
            if (startSample < 0 || endSample <= startSample || speakerId == null || speakerId.isBlank()) {
                throw new IllegalArgumentException("Invalid speaker span");
            }
            enterpriseId = enterpriseId == null || enterpriseId.isBlank() ? null : enterpriseId;
        }
        public SpeakerSpan(long startSample, long endSample, String speakerId) {
            this(startSample, endSample, speakerId, null);
        }
    }

    public record WavClip(String fileId, long startSample, long sampleCount) {
        public WavClip {
            if (fileId == null || fileId.isBlank() || startSample < 0 || sampleCount <= 0) {
                throw new IllegalArgumentException("Invalid WAV clip");
            }
        }
        public long endSampleExclusive() { return Math.addExact(startSample, sampleCount); }
    }

    public record VoiceEmbedding(String modelVersion, int dimension, List<Double> values) {
        public VoiceEmbedding {
            if (modelVersion == null || modelVersion.isBlank() || dimension <= 0 || values == null
                    || values.size() != dimension || values.stream().anyMatch(v -> v == null || !Double.isFinite(v))) {
                throw new IllegalArgumentException("Invalid voice embedding");
            }
            values = List.copyOf(values);
        }
        public String modelId() { return modelVersion; }
    }
}
