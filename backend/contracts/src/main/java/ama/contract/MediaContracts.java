package ama.contract;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Versioned media values shared by the preparation and speech stages.
 * Physical paths and provider locators never cross this boundary.
 */
public final class MediaContracts {
    private MediaContracts() {}

    public static final int NORMALIZED_SAMPLE_RATE = 16_000;
    public static final int NORMALIZED_CHANNELS = 1;
    public static final int NORMALIZED_BITS_PER_SAMPLE = 16;

    public record MediaInfo(
            String fileId,
            String container,
            long sizeBytes,
            long durationMs,
            int sampleRate,
            int channels,
            int bitsPerSample,
            boolean hasAudio) {
        public MediaInfo {
            requireId(fileId);
            if (container == null || container.isBlank() || sizeBytes < 0 || durationMs < 0
                    || sampleRate < 0 || channels < 0 || bitsPerSample < 0) {
                throw new IllegalArgumentException("Invalid media metadata");
            }
            if (!hasAudio && (sampleRate != 0 || channels != 0 || bitsPerSample != 0 || durationMs != 0)) {
                throw new IllegalArgumentException("Silent media has audio metadata");
            }
        }
    }

    public record OrderedSource(String fileId, int order) {
        public OrderedSource {
            requireId(fileId);
            if (order < 0) throw new IllegalArgumentException("Invalid source order");
        }
    }

    /** A source after it has been decoded to the canonical PCM shape. */
    public record SourceAudio(
            String fileId,
            int sampleRate,
            int channels,
            int bitsPerSample,
            long sampleCount) {
        public SourceAudio {
            requireId(fileId);
            if (sampleRate != NORMALIZED_SAMPLE_RATE
                    || channels != NORMALIZED_CHANNELS
                    || bitsPerSample != NORMALIZED_BITS_PER_SAMPLE
                    || sampleCount < 0) {
                throw new IllegalArgumentException("Media is not normalized PCM");
            }
        }
    }

    /** A source range in the concatenated canonical audio timeline. */
    public record SourceMap(
            String fileId,
            long normalizedStartSample,
            long sampleCount) {
        public SourceMap {
            requireId(fileId);
            if (normalizedStartSample < 0 || sampleCount <= 0) {
                throw new IllegalArgumentException("Invalid source range");
            }
        }
    }

    /** Canonical audio and the immutable mapping back to the ordered inputs. */
    public record NormalizedAudio(
            int sampleRate,
            int channels,
            int bitsPerSample,
            long sampleCount,
            List<SourceMap> sourceMap) {
        public NormalizedAudio {
            if (sampleRate != NORMALIZED_SAMPLE_RATE
                    || channels != NORMALIZED_CHANNELS
                    || bitsPerSample != NORMALIZED_BITS_PER_SAMPLE
                    || sampleCount < 0
                    || sourceMap == null) {
                throw new IllegalArgumentException("Invalid normalized audio");
            }
            sourceMap = List.copyOf(sourceMap);
            long end = 0;
            var ids = new HashSet<String>();
            for (var range : sourceMap) {
                if (!ids.add(range.fileId()) || range.normalizedStartSample() != end) {
                    throw new IllegalArgumentException("Source mapping is not contiguous");
                }
                end = Math.addExact(end, range.sampleCount());
            }
            if (end != sampleCount) throw new IllegalArgumentException("Source mapping size mismatch");
        }

        public static NormalizedAudio fromOrdered(List<SourceAudio> ordered) {
            Objects.requireNonNull(ordered, "ordered");
            long cursor = 0;
            var mapping = new java.util.ArrayList<SourceMap>(ordered.size());
            for (var source : ordered) {
                Objects.requireNonNull(source, "source");
                mapping.add(new SourceMap(source.fileId(), cursor, source.sampleCount()));
                cursor = Math.addExact(cursor, source.sampleCount());
            }
            return new NormalizedAudio(
                    NORMALIZED_SAMPLE_RATE,
                    NORMALIZED_CHANNELS,
                    NORMALIZED_BITS_PER_SAMPLE,
                    cursor,
                    mapping);
        }
    }

    private static void requireId(String id) {
        if (id == null || id.isBlank() || id.length() > 255 || id.indexOf('\n') >= 0 || id.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Invalid media file id");
        }
    }
}
