package ama.api;

import ama.recording.RecordingService;
import ama.recording.JdbcRecordingPersistence;
import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.ObjectProvider;

@Configuration(proxyBeanMethods=false)
class RecordingConfiguration {
    @Bean RecordingIngressBudget recordingIngress(org.springframework.core.env.Environment env) {
        return new RecordingIngressBudget(new RecordingIngressBudget.Limits(
            env.getProperty("recording.ingress.max-keys",Integer.class,512),env.getProperty("recording.ingress.bytes-per-second",Long.class,128L*1024),
            env.getProperty("recording.ingress.burst-bytes",Long.class,5L*ama.realtime.RecordingBinaryCodec.MAX_FRAME_BYTES),
            env.getProperty("recording.ingress.global-bytes-per-second",Long.class,32L*1024*1024),env.getProperty("recording.ingress.global-burst-bytes",Long.class,8L*1024*1024),
            java.time.Duration.ofSeconds(env.getProperty("recording.ingress.idle-seconds",Long.class,120L))),System::nanoTime);
    }
    @Bean ama.recording.RecordingMutationGuard recordingMutations(DataSource source,ama.access.MeetingAccess access) {
        return new ama.recording.RecordingMutationGuard(source,access);
    }
    @Bean ama.recording.JdbcRecordingWriterLeases recordingWriterLeases(DataSource source) {
        return new ama.recording.JdbcRecordingWriterLeases(source);
    }
    @Bean("RECORDING_ARCHIVE") ama.storage.FileReferencePolicy recordingArchiveReferences(DataSource source) {
        return new ama.recording.RecordingArchiveReferencePolicy(source);
    }
    @Bean ama.recording.RecordingArchiveAdmission recordingArchiveAdmission(ama.task.JdbcTaskQueue queue) {
        return (actor,session)->ama.task.RecordingArchiveRuntime.accept(queue,actor,session).taskId();
    }
    @Bean JdbcRecordingPersistence recordingPersistence(DataSource source,ama.storage.FileBackendCatalog backends) {
        return new JdbcRecordingPersistence(source,backends);
    }
    @Bean(destroyMethod="close") RecordingSubtitleRuntime recordingSubtitleRuntime(DataSource source,ama.access.MeetingAccess access,JdbcRecordingPersistence persistence) {
        String installation=System.getenv("AMA_LOCAL_SPEECH_RUNTIME");
        if(installation==null||installation.isBlank())return new RecordingSubtitleRuntime(null);
        try {
            var profile=ama.task.ControlledSpeechProfile.fromRuntime(java.nio.file.Path.of(installation));
            var subtitles=new ama.recording.JdbcLiveSubtitles(source,access,persistence,profile);
            subtitles.startScheduler();return new RecordingSubtitleRuntime(subtitles);
        } catch(Exception unavailable) {
            org.slf4j.LoggerFactory.getLogger(RecordingConfiguration.class).warn("Live subtitles unavailable: type={}",unavailable.getClass().getSimpleName());
            return new RecordingSubtitleRuntime(null);
        }
    }
    @Bean(destroyMethod="close") RecordingTranscriptRuntime recordingTranscriptRuntime(DataSource source,ama.meeting.JdbcMeetingAccess access,JdbcRecordingPersistence persistence,ama.task.JdbcTaskQueue queue,ama.storage.FileUploads uploads,org.springframework.core.env.Environment env) {
        if(!ama.transcript.RecordingTranscriptProfile.PROCESSING.equals(env.getProperty("processing.recording-config-version")))return new RecordingTranscriptRuntime(null);
        try {var copies=new ama.recording.JdbcRecordingTranscriptSources(source,access,persistence,queue,uploads);copies.startScheduler();return new RecordingTranscriptRuntime(copies);}
        catch(Exception unavailable){org.slf4j.LoggerFactory.getLogger(RecordingConfiguration.class).warn("Recording transcription unavailable: type={}",unavailable.getClass().getSimpleName());return new RecordingTranscriptRuntime(null);}
    }
    @Bean RecordingService recordingService(JdbcRecordingPersistence persistence) {
        return new RecordingService(persistence);
    }
}
