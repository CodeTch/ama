package ama.api;

import ama.recording.JdbcLiveSubtitles;

/** Explicit installation state; recording transport remains available without ASR. */
record RecordingSubtitleRuntime(JdbcLiveSubtitles service) implements AutoCloseable {
    @Override public void close() { if(service!=null)service.close(); }
}
