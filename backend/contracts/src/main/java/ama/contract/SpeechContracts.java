package ama.contract;

import java.util.*;

/** Stable values exchanged by speech recognition, diarization, and review. */
public final class SpeechContracts {
    private SpeechContracts() {}

    public record RecognitionResult(String text, List<Token> tokens) {
        public RecognitionResult {
            boundedText(text, 65536, true);
            tokens = tokens == null ? null : List.copyOf(tokens);
            if (text.isBlank() && tokens != null && !tokens.isEmpty()) throw new IllegalArgumentException("Empty recognition cannot contain tokens");
        }
    }

    /** Offline llm_correct.py item; the current request contains exactly segment 0. */
    public record TextCorrection(int segment, String original, String replacement, String reason,String entityType,Double confidence) {
        public TextCorrection(int segment,String original,String replacement,String reason){this(segment,original,replacement,reason,null,null);}
        public TextCorrection {
            if(entityType!=null)boundedText(entityType,64,false);
            if(confidence!=null&&(!Double.isFinite(confidence)||confidence<0||confidence>1))throw new IllegalArgumentException("Invalid correction confidence");
            if (segment != 0) throw new IllegalArgumentException("Correction segment outside request");
            boundedText(original, 128, false); boundedText(replacement, 128, true); boundedText(reason, 512, false);
            if (original.equals(replacement)) throw new IllegalArgumentException("Correction must change text");
        }
    }

    /** Provider proposals only. Rendering this value never authorizes a transcript write. */
    public record CorrectionResult(String modelVersion, String sourceText, List<TextCorrection> corrections) {
        public CorrectionResult {
            boundedText(modelVersion, 128, false); boundedText(sourceText, 65536, true);
            corrections = List.copyOf(corrections);
            if (corrections.size() > 128) throw new IllegalArgumentException("Correction budget exceeded");
            String rendered = render(sourceText, corrections);
            if (!sourceText.isBlank() && rendered.isBlank()) throw new IllegalArgumentException("Correction cannot erase all speech");
        }
        public String correctedText() { return render(sourceText, corrections); }
    }

    private record Replacement(int start, int end, String text) {}
    private static String render(String source, List<TextCorrection> corrections) {
        var replacements = new ArrayList<Replacement>(); var occupied = new BitSet(source.length());
        for (var correction : corrections) {
            int start = source.indexOf(correction.original());
            if (start < 0) throw new IllegalArgumentException("Correction original is absent");
            while (start >= 0) {
                int end = start + correction.original().length();
                int overlap = occupied.nextSetBit(start);
                if (overlap >= 0 && overlap < end) throw new IllegalArgumentException("Overlapping corrections");
                occupied.set(start, end);
                replacements.add(new Replacement(start, end, correction.replacement()));
                start = source.indexOf(correction.original(), end);
            }
        }
        replacements.sort(Comparator.comparingInt(Replacement::start));
        var result = new StringBuilder(); int position = 0;
        for (var replacement : replacements) {
            if (replacement.start() < position) throw new IllegalArgumentException("Overlapping corrections");
            if ((long)result.length() + replacement.start() - position + replacement.text().length() > 65536) throw new IllegalArgumentException("Correction text budget exceeded");
            result.append(source, position, replacement.start()).append(replacement.text()); position = replacement.end();
        }
        if ((long)result.length() + source.length() - position > 65536) throw new IllegalArgumentException("Correction text budget exceeded");
        return result.append(source, position, source.length()).toString();
    }
    private static void boundedText(String text, int limit, boolean emptyAllowed) {
        if (text == null || text.length() > limit || !emptyAllowed && text.isBlank()) throw new IllegalArgumentException("Bounded speech text required");
        MinutesContracts.scalars(text);
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
