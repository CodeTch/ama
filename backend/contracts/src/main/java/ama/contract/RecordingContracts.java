package ama.contract;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/** Durable recording stream protocol values. */
public final class RecordingContracts {
    public static final int MAX_PCM_BYTES = 64000;
    public static final int MAX_SESSION_CHUNKS = 4096;
    public static final long MAX_SESSION_SAMPLES = 115200000L;
    private RecordingContracts() {}
    public record Session(String sessionId, String meetingId, String partId, long writerEpoch, String backendId) {
        public Session {
            if (sessionId == null || sessionId.isBlank() || meetingId == null || meetingId.isBlank() || partId == null || partId.isBlank() || writerEpoch < 1 || backendId == null || backendId.isBlank()) throw new IllegalArgumentException("Invalid recording session");
        }
    }
    public record Chunk(String partId, long writerEpoch, long seq, long sampleStart, long sampleCount, String sha256, byte[] pcm) {
        public Chunk {
            if (partId == null || partId.isBlank() || writerEpoch < 0 || seq < 1 || sampleStart < 0 || sampleCount < 1
                    || sampleCount > MAX_PCM_BYTES / 2 || sampleStart > Long.MAX_VALUE - sampleCount
                    || pcm == null || pcm.length != sampleCount * 2) {
                throw new IllegalArgumentException("Invalid recording chunk");
            }
            pcm = pcm.clone();
            var actual = digest(pcm);
            if (sha256 == null) sha256 = actual;
            if (!sha256.equals(actual)) throw new IllegalArgumentException("Recording chunk hash mismatch");
        }
        public Chunk(String partId, long seq, long sampleStart, long sampleCount, byte[] pcm) { this(partId, 0, seq, sampleStart, sampleCount, null, pcm); }
        public Chunk(String partId, long writerEpoch, long seq, long sampleStart, long sampleCount, byte[] pcm) { this(partId, writerEpoch, seq, sampleStart, sampleCount, null, pcm); }
        public byte[] pcm() { return pcm.clone(); }
        private static String digest(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (Exception e) { throw new AssertionError(e); } }
    }
    public record Ack(long highestContiguousSeq, List<Long> missingSeqs) {
        public Ack { if (highestContiguousSeq < 0 || missingSeqs == null) throw new IllegalArgumentException("Invalid ACK"); missingSeqs = List.copyOf(missingSeqs); }
        public List<Long> missingRanges() { return missingSeqs; }
    }
}
