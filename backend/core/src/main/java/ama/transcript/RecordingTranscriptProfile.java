package ama.transcript;

/** Separate archive identity; permission to consume it still requires a verified recording source. */
public final class RecordingTranscriptProfile {
 private RecordingTranscriptProfile() {}
 public static final String PARSER="ama.recording-wav.v1";
 public static final String PROCESSING="ama.import-recording-wav.v1";
 public static final long MAX_BYTES=230400044L;
 public static final long MAX_SAMPLES=115200000L;
}
