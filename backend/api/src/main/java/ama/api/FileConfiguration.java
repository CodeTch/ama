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
        var definitions = new ArrayList<FileBackendCatalog.Backend>();
        var aliases = new HashMap<String, String>();
        for (String alias : env.getProperty("files.backends", "").split(",")) {
            if (alias.isBlank()) continue;
            ama.contract.FileContracts.code(alias);
            String prefix = "files." + alias + ".";
            String type = env.getRequiredProperty(prefix + "type"),
                    id = env.getRequiredProperty(prefix + "id");
            FileBackendCatalog.Backend backend;
            long revision = env.getRequiredProperty(prefix + "revision", Long.class);
            String status = env.getProperty(prefix + "status", "WRITABLE");
            if (type.equals("local")) {
                var local =
                        new ManagedBackendId.Local(
                                env.getRequiredProperty(prefix + "service-origin"),
                                env.getRequiredProperty(prefix + "volume-id"),
                                env.getRequiredProperty(prefix + "relative-root"),
                                env.getRequiredProperty(prefix + "filesystem"));
                ManagedBackendId.require(id, local);
                backend =
                        ManagedBackendAdapters.local(
                                id,
                                local,
                                ServiceTls.context(
                                        Path.of(env.getRequiredProperty(prefix + "key-store")),
                                        env.getRequiredProperty(prefix + "key-store-password")
                                                .toCharArray(),
                                        Path.of(env.getRequiredProperty(prefix + "trust-store")),
                                        env.getRequiredProperty(prefix + "trust-store-password")
                                                .toCharArray()),
                                revision,
                                status);
            } else if (type.equals("s3")) {
                var s3 =
                        new ManagedBackendId.S3(
                                env.getRequiredProperty(prefix + "endpoint"),
                                env.getRequiredProperty(prefix + "region"),
                                env.getRequiredProperty(prefix + "data-bucket"),
                                env.getRequiredProperty(prefix + "control-bucket"),
                                env.getProperty(prefix + "path-style", Boolean.class, true),
                                env.getProperty(prefix + "key-budget", Integer.class, 1024));
                ManagedBackendId.require(id, s3);
                if (!URI.create(s3.endpoint()).getScheme().equals("https"))
                    throw new IllegalArgumentException("Production S3 requires HTTPS");
                var config =
                        new S3Config(
                                id,
                                URI.create(s3.endpoint()),
                                s3.region(),
                                s3.dataBucket(),
                                s3.controlBucket(),
                                () ->
                                        AwsBasicCredentials.create(
                                                env.getRequiredProperty(prefix + "access-key"),
                                                env.getRequiredProperty(prefix + "secret-key")),
                                s3.pathStyle(),
                                s3.keyBudget(),
                                env.getProperty(
                                        prefix + "chunk-bytes", Integer.class, 8 * 1024 * 1024),
                                5L * 1024 * 1024,
                                5L * 1024 * 1024 * 1024,
                                10000,
                                env.getRequiredProperty(
                                        prefix + "maximum-object-bytes", Long.class),
                                env.getProperty(prefix + "admission", Integer.class, 8),
                                Duration.ofMillis(
                                        env.getProperty(
                                                prefix + "timeout-ms", Long.class, 20000L)));
                backend = ManagedBackendAdapters.s3(config, revision, status);
            } else throw new IllegalArgumentException("Unknown configured file backend");
            if (aliases.put(alias, id) != null)
                throw new IllegalArgumentException("Duplicate backend alias");
            definitions.add(backend);
        }
        String selected = env.getProperty("files.default");
        if (selected != null && !aliases.containsKey(selected))
            throw new IllegalArgumentException("Unknown configured default");
        return new FileBackendDefinitions(
                definitions, selected == null ? null : aliases.get(selected));
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
            Environment env) {
        return new JdbcFileUploads(
                source,
                tx,
                access,
                purposes,
                backends,
                new JdbcAuditAdapter(source),
                outbox,
                env.getProperty("files.part-size", Long.class, 8L * 1024 * 1024));
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
