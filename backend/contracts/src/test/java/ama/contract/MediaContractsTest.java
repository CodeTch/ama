package ama.contract;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MediaContractsTest {
    @Test
    void orderedSourcesKeepSourceMappingAfterTheirOrderChanges() {
        var sources = List.of(
            new MediaContracts.SourceAudio("first", 16_000, 1, 16, 16_000),
            new MediaContracts.SourceAudio("second", 16_000, 1, 16, 8_000));

        var normalized = MediaContracts.NormalizedAudio.fromOrdered(sources.reversed());

        assertEquals(16_000, normalized.sampleRate());
        assertEquals(1, normalized.channels());
        assertEquals(16, normalized.bitsPerSample());
        assertEquals(24_000, normalized.sampleCount());
        assertEquals("second", normalized.sourceMap().getFirst().fileId());
        assertEquals(0, normalized.sourceMap().getFirst().normalizedStartSample());
        assertEquals(8_000, normalized.sourceMap().get(1).normalizedStartSample());
        assertEquals("first", normalized.sourceMap().get(1).fileId());
    }

    @Test
    void invalidOrOversizedMediaIsRejectedBeforeNormalization() {
        assertThrows(IllegalArgumentException.class,
            () -> new MediaContracts.SourceAudio("", 16_000, 1, 16, 1));
        assertThrows(IllegalArgumentException.class,
            () -> new MediaContracts.SourceAudio("clip", 48_000, 2, 24, 1));
        assertThrows(IllegalArgumentException.class,
            () -> MediaContracts.NormalizedAudio.fromOrdered(List.of(
                new MediaContracts.SourceAudio("same", 16_000, 1, 16, 2),
                new MediaContracts.SourceAudio("same", 16_000, 1, 16, 2))));
    }
}
