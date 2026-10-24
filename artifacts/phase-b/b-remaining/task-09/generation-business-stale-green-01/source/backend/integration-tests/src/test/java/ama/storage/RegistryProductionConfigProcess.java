package ama.storage;

import ama.api.ApiApplication;

import org.springframework.boot.builder.SpringApplicationBuilder;

import java.util.*;

/** Fresh JVM running production backend assembly. Arguments contain no credentials. */
public final class RegistryProductionConfigProcess {
    public static void main(String[] args) {
        var options =
                new ArrayList<String>(
                        List.of(
                                "--server.address=127.0.0.1",
                                "--server.port=0",
                                "--management.server.port=0",
                                "--logging.level.root=ERROR",
                                "--spring.datasource.url=" + System.getenv("AMA_TEST_DATABASE_URL"),
                                "--spring.datasource.username="
                                        + System.getenv("AMA_TEST_DATABASE_USERNAME"),
                                "--spring.datasource.password="
                                        + System.getenv("AMA_TEST_DATABASE_PASSWORD")));
        options.addAll(List.of(args));
        try (var app =
                new SpringApplicationBuilder(ApiApplication.class)
                        .run(options.toArray(String[]::new))) {
            System.out.println("UNEXPECTED_CONFIG_ACCEPTED");
        }
    }
}
