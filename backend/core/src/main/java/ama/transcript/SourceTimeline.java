package ama.transcript;

import ama.contract.MediaContracts;
import java.util.List;
import java.util.Objects;

/** Maps canonical sample offsets back to the ordered source file and local offset. */
public final class SourceTimeline {
    private final List<Range> ranges;

    public SourceTimeline(MediaContracts.NormalizedAudio audio) {
        Objects.requireNonNull(audio, "audio");
        this.ranges = audio.sourceMap().stream()
                .map(m -> new Range(m.fileId(), m.normalizedStartSample(), m.sampleCount()))
                .toList();
    }

    public Locator locate(long normalizedSample) {
        if (normalizedSample < 0) throw new IllegalArgumentException("Sample offset must be non-negative");
        for (Range range : ranges) {
            if (normalizedSample >= range.startSample && normalizedSample < range.endSampleExclusive()) {
                return new Locator(range.fileId, normalizedSample - range.startSample);
            }
        }
        throw new IllegalArgumentException("Sample offset outside source timeline");
    }

    public record Locator(String fileId, long localSample) {
        public Locator {
            if (fileId == null || fileId.isBlank() || localSample < 0) throw new IllegalArgumentException("Invalid locator");
        }
    }

    private record Range(String fileId, long startSample, long sampleCount) {
        private long endSampleExclusive() { return Math.addExact(startSample, sampleCount); }
    }
}
