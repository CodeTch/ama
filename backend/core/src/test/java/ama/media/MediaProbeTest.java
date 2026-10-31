package ama.media;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MediaProbeTest {
    @Test
    void probesPcmWavMetadataWithoutInventingAudio() {
        var probe = new MediaProbe();
        var info = probe.probe("clip-a", new ByteArrayInputStream(wav(48_000, 2, 16, 48_000)));

        assertEquals("clip-a", info.fileId());
        assertEquals("WAV", info.container());
        assertTrue(info.hasAudio());
        assertEquals(48_000, info.sampleRate());
        assertEquals(2, info.channels());
        assertEquals(16, info.bitsPerSample());
        assertEquals(1_000, info.durationMs());
    }

    @Test
    void rejectsMalformedOrOversizedProbeInput() {
        var probe = new MediaProbe(44);
        assertThrows(IllegalArgumentException.class,
            () -> probe.probe("bad", new ByteArrayInputStream(new byte[] {1, 2, 3})));
        assertThrows(IllegalArgumentException.class,
            () -> probe.probe("bad", new ByteArrayInputStream(wav(48_000, 1, 16, 48_000))));
    }

    @Test
    void fileIdProbeUsesTheBoundedSourceReader() {
        var probe = new MediaProbe(id -> new ByteArrayInputStream(wav(16_000, 1, 16, 10)));
        assertEquals("WAV", probe.probe("reader-file").container());
    }

    private static byte[] wav(int rate, int channels, int bits, int frames) {
        int dataSize = frames * channels * bits / 8;
        var b = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + dataSize).put("WAVE".getBytes());
        b.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) channels)
            .putInt(rate).putInt(rate * channels * bits / 8).putShort((short) (channels * bits / 8))
            .putShort((short) bits).put("data".getBytes()).putInt(dataSize);
        b.put(new byte[dataSize]);
        return b.array();
    }
}
