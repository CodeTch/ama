package ama.worker;

import ama.contract.MediaContracts.OrderedSource;
import ama.media.FfmpegNormalizer;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MediaPreparationHandlerTest {
    @Test void equalDurationDifferentAudioCannotReuseThePreparationFingerprint(){
        var source=new java.util.concurrent.atomic.AtomicReference<>(wav(16000,1,16,160));
        var handler=new MediaPreparationHandler(new FfmpegNormalizer(id->new ByteArrayInputStream(source.get())));var sources=List.of(new OrderedSource("same-original",0));
        var first=handler.prepare(sources);var changed=source.get().clone();changed[44]=64;changed[45]=31;source.set(changed);
        assertNotEquals(first.fingerprint(),handler.prepare(sources).fingerprint());
    }
    @Test
    void preparationReturnsStableFingerprintForTheOrderedSourceSnapshot() {
        var normalizer = new FfmpegNormalizer(id -> new ByteArrayInputStream(wav(16_000, 1, 16, 1_600)));
        var handler = new MediaPreparationHandler(normalizer);
        var sources = List.of(new OrderedSource("a", 0), new OrderedSource("b", 1));

        var first = handler.prepare(sources);
        var second = handler.prepare(sources);

        assertEquals(first.fingerprint(), second.fingerprint());
        assertEquals(3_200, first.audio().sampleCount());
        assertEquals(2, first.audio().sourceMap().size());
    }

    @Test
    void preparationPersistsSourceMapOnlyAfterNormalizationSucceeds() {
        var normalizer = new FfmpegNormalizer(id -> new ByteArrayInputStream(wav(16_000, 1, 16, 10)));
        var handler = new MediaPreparationHandler(normalizer);
        var saved = new java.util.concurrent.atomic.AtomicReference<MediaPreparationHandler.Prepared>();
        var prepared = handler.prepare(List.of(new OrderedSource("a", 0)), saved::set);
        assertSame(prepared, saved.get());
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
