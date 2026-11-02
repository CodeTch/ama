package ama.api;
import ama.recording.JdbcRecordingTranscriptSources;
record RecordingTranscriptRuntime(JdbcRecordingTranscriptSources service) implements AutoCloseable {
 @Override public void close(){if(service!=null)service.close();}
}
