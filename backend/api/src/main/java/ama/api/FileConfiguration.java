package ama.api;

import ama.access.MeetingAccess;
import ama.audit.JdbcAuditAdapter;
import ama.db.TransactionRunner;
import ama.storage.*;
import ama.storage.internal.ServiceTls;
import ama.storage.s3.*;
import ama.task.JdbcOutbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
class FileConfiguration {
    @Bean
    @ConditionalOnMissingBean(FilePurposePolicy.class)
    FilePurposePolicy filePurposes(Environment env) {
        if(env.getProperty("processing.enabled",Boolean.class,false))return new ama.transcript.TranscriptFilePurposePolicy();
        return (actor, meeting, purpose) -> {
            throw new FilePurposePolicy.Unavailable();
        };
    }

    @Bean
    @ConditionalOnMissingBean(FileBackendDefinitions.class)
    FileBackendDefinitions fileDefinitions(Environment env) throws Exception {
        return ConfiguredBackends.read(env);
    }

    @Bean
    FileBackendCatalog fileBackends(DataSource source, FileBackendDefinitions definitions) {
        var catalog =
                new FileBackendCatalog(source, definitions.backends(), definitions.defaultId());
        catalog.configure();
        return catalog;
    }

    @Bean
    org.springframework.boot.health.contributor.HealthIndicator fileStorage(
            FileBackendCatalog catalog) {
        return () ->
                catalog.ready()
                        ? org.springframework.boot.health.contributor.Health.up().build()
                        : org.springframework.boot.health.contributor.Health.down().build();
    }

    @Bean
    FileUploads fileUploads(
            DataSource source,
            TransactionRunner tx,
            MeetingAccess access,
            FilePurposePolicy purposes,
            FileBackendCatalog backends,
            JdbcOutbox outbox,
            Environment env,ProcessingConfiguration.Runtime processing) {
        return new JdbcFileUploads(
                source,
                tx,
                access,
                purposes,
                backends,
                new JdbcAuditAdapter(source),
                outbox,
                env.getProperty("files.part-size", Long.class, 8L * 1024 * 1024),processing.components()==null?null:processing.components().authority().publicationWriteGuard());
    }

    @Bean
    FileRegistry fileRegistry(
            DataSource source,
            TransactionRunner tx,
            MeetingAccess access,
            FilePurposePolicy purposes,
            FileBackendCatalog backends,
            Map<String, FileReferencePolicy> references,ProcessingConfiguration.Runtime processing) {
        if(processing.components()!=null)return processing.components().registry();
        return new JdbcFileRegistry(source, tx, access, purposes, backends, references);
    }
}
