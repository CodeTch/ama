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
    @Bean RecordingService recordingService(JdbcRecordingPersistence persistence) {
        return new RecordingService(persistence);
    }
}
