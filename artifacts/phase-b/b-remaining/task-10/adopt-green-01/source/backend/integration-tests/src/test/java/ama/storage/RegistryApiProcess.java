package ama.storage;

import ama.api.ApiApplication;
import ama.storage.internal.ServiceTls;
import ama.storage.s3.*;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.support.GenericApplicationContext;

import java.net.URI;
import java.nio.file.Path;

/** Test-only application process. Production never registers these controlled purposes/adapters. */
public final class RegistryApiProcess {
    public static void main(String[] args) throws Exception {
        boolean s3 = "s3".equals(System.getenv("AMA_STORAGE_CONTRACT"));
        FileStoragePort actual;
        ManagedBackendId.Definition binding;
        if (s3) {
            binding =
                    new ManagedBackendId.S3(
                            System.getenv("AMA_S3_ENDPOINT"),
                            "us-east-1",
                            System.getenv("AMA_REGISTRY_DATA_BUCKET"),
                            System.getenv("AMA_REGISTRY_CONTROL_BUCKET"),
                            true,
                            1024);
            actual =
                    new S3FileStorage(
                            S3Fixture.config(
                                    binding.id(),
                                    URI.create(System.getenv("AMA_S3_ENDPOINT")),
                                    System.getenv("AMA_REGISTRY_DATA_BUCKET"),
                                    System.getenv("AMA_REGISTRY_CONTROL_BUCKET")));
        } else {
            binding =
                    new ManagedBackendId.Local(
                            System.getenv("AMA_REGISTRY_LOCAL_URI"),
                            System.getenv("AMA_REGISTRY_VOLUME"),
                            System.getenv("AMA_REGISTRY_RELATIVE_ROOT"),
                            System.getenv("AMA_REGISTRY_FILESYSTEM"));
            Path pki = Path.of(System.getenv("AMA_REGISTRY_PKI"));
            char[] password = LocalFixture.PASSWORD;
            actual =
                    new LocalFileClient(
                            URI.create(System.getenv("AMA_REGISTRY_LOCAL_URI")),
                            ServiceTls.context(
                                    pki.resolve("writer.p12"),
                                    password,
                                    pki.resolve("trust.p12"),
                                    password));
        }
        var storage = new RegistryObservedStorage(actual);
        if (args.length > 0 && args[0].equals("crash-after-complete"))
            storage.afterComplete.set(() -> Runtime.getRuntime().halt(73));
        boolean disabled = args.length > 0 && args[0].equals("purpose-disabled");
        var app =
                new SpringApplicationBuilder(ApiApplication.class)
                        .initializers(
                                context -> {
                                    var c = (GenericApplicationContext) context;
                                    c.registerBean(
                                            "controlledBackends",
                                            FileBackendDefinitions.class,
                                            () ->
                                                    new FileBackendDefinitions(
                                                            java.util.List.of(
                                                                    new FileBackendCatalog.Backend(
                                                                            binding.id(),
                                                                            binding.type(),
                                                                            1,
                                                                            "WRITABLE",
                                                                            storage,
                                                                            binding)),
                                                            binding.id()));
                                    c.registerBean(
                                            "controlledPurpose",
                                            FilePurposePolicy.class,
                                            () ->
                                                    (actor, meeting, purpose) -> {
                                                        if (disabled
                                                                || !purpose.equals(
                                                                        "CONTROLLED_INPUT"))
                                                            throw new FilePurposePolicy
                                                                    .Unavailable();
                                                    });
                                })
                        .run(
                                "--server.address=127.0.0.1",
                                "--server.port=0",
                                "--management.server.port=0",
                                "--logging.level.root=ERROR",
                                "--spring.datasource.url=" + System.getenv("AMA_TEST_DATABASE_URL"),
                                "--spring.datasource.username="
                                        + System.getenv("AMA_TEST_DATABASE_USERNAME"),
                                "--spring.datasource.password="
                                        + System.getenv("AMA_TEST_DATABASE_PASSWORD"),
                                "--identity.allowed-origin=https://registry-restart.example.invalid",
                                "--files.backend.id=local",
                                "--files.backend.type=" + (s3 ? "s3" : "local"));
        System.out.println(
                "REGISTRY_READY " + app.getEnvironment().getProperty("local.server.port"));
        System.out.flush();
    }
}
