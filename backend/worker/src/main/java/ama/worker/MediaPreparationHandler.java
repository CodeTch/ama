package ama.worker;

import ama.contract.MediaContracts.NormalizedAudio;
import ama.contract.MediaContracts.OrderedSource;
import ama.media.FfmpegNormalizer;
import java.util.List;
import java.util.Objects;

/** C-01 preparation boundary; task persistence owns the surrounding transaction. */
public final class MediaPreparationHandler {
    public record Prepared(NormalizedAudio audio, String fingerprint) {
        public Prepared {
            Objects.requireNonNull(audio, "audio");
            if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid fingerprint");
        }
    }

    private final FfmpegNormalizer normalizer;
    public MediaPreparationHandler(FfmpegNormalizer normalizer) { this.normalizer = Objects.requireNonNull(normalizer, "normalizer"); }

    public Prepared prepare(List<OrderedSource> sources) {
        return prepare(sources, ignored -> {});
    }

    /** Persist only after normalization and fingerprinting have completed successfully. */
    public Prepared prepare(List<OrderedSource> sources, java.util.function.Consumer<Prepared> durableStore) {
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(durableStore, "durableStore");
        try (var audio = normalizer.prepare(sources, () -> false)) {
            var prepared = new Prepared(audio.audio(), audio.fingerprint());
            durableStore.accept(prepared);
            return prepared;
        }
    }

    public FfmpegNormalizer.PreparedAudio prepareAudio(List<OrderedSource> sources, java.util.function.BooleanSupplier cancelled) {
        return normalizer.prepare(sources, cancelled);
    }
}
