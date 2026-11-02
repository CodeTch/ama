package ama.media;

import ama.contract.MediaContracts.OrderedSource;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FfmpegNormalizerTest {
    @Test
    void normalizesOrderedSourcesToSixteenKhzMonoPcmAndKeepsMapping() {
        var normalizer = new FfmpegNormalizer(id -> new ByteArrayInputStream(wav(id.equals("a") ? 48_000 : 8_000, 2, 16, 4_000)));

        var audio = normalizer.normalize(List.of(new OrderedSource("b", 1), new OrderedSource("a", 0)));

        assertEquals(16_000, audio.sampleRate());
        assertEquals(1, audio.channels());
        assertEquals(16, audio.bitsPerSample());
        assertTrue(audio.sampleCount() > 0);
        assertEquals("a", audio.sourceMap().getFirst().fileId());
        assertEquals("b", audio.sourceMap().get(1).fileId());
    }

    @Test
    void rejectsDuplicateOrMissingSourcesBeforeLaunchingFfmpeg() {
        var launches = new int[1];
        var normalizer = new FfmpegNormalizer(id -> { launches[0]++; return new ByteArrayInputStream(new byte[0]); });
        assertThrows(IllegalArgumentException.class, () -> normalizer.normalize(List.of(
            new OrderedSource("same", 0), new OrderedSource("same", 1))));
        assertEquals(0, launches[0]);
    }

    @Test
    void rejectsCorruptOrOversizedInputBeforeReturningAUsableAudio() {
        var corrupt = new FfmpegNormalizer(id -> new ByteArrayInputStream("not-media".getBytes()));
        assertThrows(IllegalArgumentException.class, () -> corrupt.normalize(List.of(new OrderedSource("中文-长文件名-" + "x".repeat(80), 0))));
        var oversized = new FfmpegNormalizer(id -> new ByteArrayInputStream(new byte[100]), 32, java.time.Duration.ofSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> oversized.normalize(List.of(new OrderedSource("big", 0))));
    }

    @Test void longRecordingCapacityIsExplicitAndOrdinaryInputKeepsItsOriginalCeiling()throws Exception {
        assertThrows(IllegalArgumentException.class,()->new FfmpegNormalizer.Limits(33554433,115200000,java.time.Duration.ofMinutes(2)));
        byte[] input=wav(16000,1,16,32000);
        try(var prepared=new FfmpegNormalizer(id->new ByteArrayInputStream(input),FfmpegNormalizer.Limits.recording(115200000,java.time.Duration.ofMinutes(2))).prepare(List.of(new OrderedSource("recording",0)),()->false)) {
            assertEquals(32000,prepared.sources().getFirst().sampleCount());
        }
    }

    private static byte[] wav(int rate, int channels, int bits, int frames) {
        int dataSize = frames * channels * bits / 8;
        var b = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + dataSize).put("WAVE".getBytes());
        b.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) channels)
            .putInt(rate).putInt(rate * channels * bits / 8).putShort((short) (channels * bits / 8))
            .putShort((short) bits).put("data".getBytes()).putInt(dataSize).put(new byte[dataSize]);
        return b.array();
    }
}
