package ama.media;

import ama.contract.MediaContracts;
import ama.contract.MediaContracts.MediaInfo;
import ama.contract.MediaContracts.NormalizedAudio;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Persists the ordered media snapshot and normalized source map inside the caller's transaction. */
public final class JdbcMediaSources {
    public record Source(String fileId, MediaInfo info, String sha256) {
        public Source { if (fileId == null || info == null || !fileId.equals(info.fileId()) || sha256 == null || !sha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid media source"); }
    }
    private final JdbcTemplate jdbc;
    public JdbcMediaSources(DataSource source) { jdbc = new JdbcTemplate(Objects.requireNonNull(source)); }
    public void replace(String batchId, List<Source> sources, NormalizedAudio audio) {
        if (batchId == null || batchId.isBlank() || sources == null || sources.isEmpty() || audio == null) throw new IllegalArgumentException("Invalid media source batch");
        if (sources.size() != audio.sourceMap().size()) throw new IllegalArgumentException("Source map does not match media sources");
        var ids = new HashSet<String>();
        for (int i = 0; i < sources.size(); i++) {
            var source = sources.get(i); var map = audio.sourceMap().get(i);
            if (!ids.add(source.fileId()) || !map.fileId().equals(source.fileId())) throw new IllegalArgumentException("Source order does not match normalized map");
        }
        jdbc.update("DELETE FROM media_source_map WHERE batch_id=?", batchId);
        jdbc.update("DELETE FROM media_source WHERE batch_id=?", batchId);
        for (int i = 0; i < sources.size(); i++) {
            var source = sources.get(i); var info = source.info();
            jdbc.update("INSERT INTO media_source(batch_id,ordinal,file_id,container,duration_ms,sample_rate,channels,bits_per_sample,has_audio,source_sha256) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    batchId, i + 1, source.fileId(), info.container(), info.durationMs(), info.sampleRate(), info.channels(), info.bitsPerSample(), info.hasAudio(), source.sha256());
            var map = audio.sourceMap().get(i);
            jdbc.update("INSERT INTO media_source_map(batch_id,file_id,normalized_start_sample,sample_count) VALUES (?,?,?,?)",
                    batchId, map.fileId(), map.normalizedStartSample(), map.sampleCount());
        }
    }
}
