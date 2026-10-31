package ama.transcript;

import ama.contract.MediaContracts;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceTimelineTest {
    @Test
    void mapsNormalizedSamplesBackToTheOrderedOriginalFile() {
        var audio = MediaContracts.NormalizedAudio.fromOrdered(List.of(
            new MediaContracts.SourceAudio("first", 16_000, 1, 16, 16_000),
            new MediaContracts.SourceAudio("second", 16_000, 1, 16, 8_000)));
        var timeline = new SourceTimeline(audio);

        assertEquals(new SourceTimeline.Locator("first", 15_999), timeline.locate(15_999));
        assertEquals(new SourceTimeline.Locator("second", 0), timeline.locate(16_000));
        assertThrows(IllegalArgumentException.class, () -> timeline.locate(24_000));
    }
}
