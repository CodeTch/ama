package ama.transcript;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Internal parsing port; does not authorize or activate a production import format. */
public interface TranscriptDocumentParser {
    /**
     * Parses a caller-selected profile with a canonical UUID and exact lowercase SHA-256.
     * Success requires EOF and digest verification. Failures never expose partial segments.
     * Never closes source; caller supplies blocking I/O timeout and stream lifetime.
     * Cancellation includes thread interruption, whose flag is preserved.
     */
    Result parse(InputStream source, UUID fileId, String expectedSourceSha256,
                 ParseBudget budget, BooleanSupplier cancelled) throws ParseException;

    /**
     * Positive technical bounds, validated before I/O by parse; never product content quotas.
     * UTF-16 units count all decoded input including an initial BOM and line separators.
     */
    record ParseBudget(long maxSourceBytes, long maxUtf16CodeUnits, int maxSegments) {}

    record Result(List<Segment> segments, String sourceSha256, String parserProfileVersion) {
        public Result { segments = List.copyOf(segments); }
    }

    record Segment(UUID stableId, UUID sourceFileId, long paragraphNo, String text,
                   UUID speakerId, Long startMs, Long endMs) {}

    enum ErrorCode {
        INVALID_ARGUMENT, INVALID_UTF8, INVALID_TEXT, NO_TEXT, SOURCE_HASH_MISMATCH,
        SOURCE_BYTES_EXCEEDED, UTF16_CODE_UNITS_EXCEEDED, SEGMENTS_EXCEEDED,
        CANCELLED, SOURCE_READ_FAILED, ENGINE_UNAVAILABLE, INVALID_MEDIA, AUDIO_REQUIRED, AUDIO_DURATION_EXCEEDED
    }

    /** Safe diagnostic only: no source content, path, provider details or partial result. */
    final class ParseException extends Exception {
        private final ErrorCode code;
        public ParseException(ErrorCode code) {
            super(code.name());
            this.code = code;
        }
        public ErrorCode code() { return code; }
    }
}
