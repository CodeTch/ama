package ama.storage;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.util.concurrent.TimeUnit;

class ContractFixtureTest {
    @Test
    void unknownBackendSelectorCannotSilentlyRunLocalInstead() throws Exception {
        var builder =
                new ProcessBuilder(
                                Path.of(System.getProperty("java.home"), "bin/java").toString(),
                                "-cp",
                                System.getProperty("java.class.path"),
                                ContractFixtureTest.class.getName())
                        .redirectErrorStream(true);
        builder.environment().put("AMA_STORAGE_CONTRACT", "misspelled-s3");
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS));
            assertEquals(
                    0,
                    process.exitValue(),
                    new String(
                            process.getInputStream().readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8));
        } finally {
            process.destroyForcibly();
        }
    }

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("ama-contract-selector-");
        try (var ignored = new ContractFixture(temp)) {
            throw new AssertionError("unknown selector silently selected a backend");
        } catch (IllegalArgumentException expected) {
            /* Fail closed before constructing either backend. */
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (DirectoryNotEmptyException retainedUnexpectedBackendEvidence) {
                /* Do not mask the selector assertion. */
            }
        }
    }
}
