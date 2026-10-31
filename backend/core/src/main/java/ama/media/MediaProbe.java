package ama.media;

import ama.contract.MediaContracts.MediaInfo;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Bounded probe for the small, deterministic WAV subset used by local preparation. */
public final class MediaProbe {
    @FunctionalInterface public interface SourceReader { InputStream open(String fileId) throws IOException; }
    private final long maxBytes;
    private final SourceReader reader;

    public MediaProbe() { this(32L * 1024 * 1024, null); }
    public MediaProbe(long maxBytes) { this(maxBytes, null); }
    public MediaProbe(SourceReader reader) { this(64L * 1024 * 1024, reader); }
    public MediaProbe(long maxBytes, SourceReader reader) {
        if (maxBytes < 44) throw new IllegalArgumentException("Probe limit too small");
        this.maxBytes = maxBytes;
        this.reader = reader;
    }

    public MediaInfo probe(String fileId) {
        if (reader == null) throw new IllegalStateException("No media source reader configured");
        try (var input = reader.open(fileId)) { return probe(fileId, input); }
        catch (IOException e) { throw new IllegalArgumentException("Media probe failed", e); }
    }

    public MediaInfo probe(String fileId, InputStream input) {
        Objects.requireNonNull(input, "input");
        byte[] bytes = readBounded(input);
        if (bytes.length < 44 || !ascii(bytes, 0, "RIFF") || !ascii(bytes, 8, "WAVE")) {
            throw new IllegalArgumentException("Unsupported or malformed WAV");
        }
        var view = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int channels = 0, rate = 0, bits = 0, dataBytes = -1;
        int offset = 12;
        while (offset + 8 <= bytes.length) {
            String chunk = new String(bytes, offset, 4, StandardCharsets.US_ASCII);
            long rawSize = Integer.toUnsignedLong(view.getInt(offset + 4));
            if (rawSize > Integer.MAX_VALUE || rawSize > bytes.length - offset - 8) {
                throw new IllegalArgumentException("Malformed WAV chunk");
            }
            int size = (int) rawSize;
            int body = offset + 8;
            if (chunk.equals("fmt ")) {
                if (size < 16) throw new IllegalArgumentException("Malformed WAV format");
                int format = Short.toUnsignedInt(view.getShort(body));
                channels = Short.toUnsignedInt(view.getShort(body + 2));
                rate = view.getInt(body + 4);
                bits = Short.toUnsignedInt(view.getShort(body + 14));
                if (format != 1 || channels <= 0 || rate <= 0 || bits <= 0) {
                    throw new IllegalArgumentException("Unsupported WAV format");
                }
            } else if (chunk.equals("data")) {
                dataBytes = size;
                break;
            }
            offset = body + size + (size & 1);
        }
        if (dataBytes < 0 || channels == 0 || rate == 0 || bits == 0) {
            throw new IllegalArgumentException("WAV has no usable audio");
        }
        long bytesPerFrame = Math.multiplyExact(channels, bits / 8L);
        if (bits % 8 != 0 || bytesPerFrame == 0 || dataBytes % bytesPerFrame != 0) {
            throw new IllegalArgumentException("WAV data is not frame aligned");
        }
        long frames = dataBytes / bytesPerFrame;
        long durationMs = Math.multiplyExact(frames, 1000L) / rate;
        return new MediaInfo(fileId, "WAV", bytes.length, durationMs, rate, channels, bits, true);
    }

    private byte[] readBounded(InputStream input) {
        try {
            var out = new ByteArrayOutputStream((int) Math.min(maxBytes, 64 * 1024));
            byte[] buffer = new byte[8192];
            long total = 0;
            for (int n; (n = input.read(buffer)) >= 0;) {
                total += n;
                if (total > maxBytes) throw new IllegalArgumentException("Media exceeds probe limit");
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("Media probe failed", e);
        }
    }

    private static boolean ascii(byte[] bytes, int offset, String value) {
        if (offset + value.length() > bytes.length) return false;
        for (int i = 0; i < value.length(); i++) if (bytes[offset + i] != (byte) value.charAt(i)) return false;
        return true;
    }
}
