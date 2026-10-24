package ama.storage.s3;

import static org.junit.jupiter.api.Assertions.*;

import ama.contract.StorageContracts.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class S3LayoutV3Test {
    final S3Layout layout = new S3Layout(S3ProtocolTest.config());

    StorageKey key(String s) {
        return new StorageKey("s3", "scope", s);
    }

    @Test
    void ordinary512AndExactBudgetHaveBoundedPhysicalSegments() {
        for (int n : new int[] {198, 199, 200, 397, 398, 399, 512, 942}) {
            String path = layout.data(key("a".repeat(n)));
            assertTrue(path.startsWith("data/v3/"));
            assertEquals(72 + n + 2 * ((n + 198) / 199), path.length());
            assertTrue(path.split("/").length <= 8);
            for (String s : path.split("/"))
                assertTrue(s.length() <= 200 && !s.isEmpty() && !s.equals(".") && !s.equals(".."));
        }
        assertEquals(
                ErrorCode.INVALID_ARGUMENT,
                assertThrows(StorageException.class, () -> layout.data(key("a".repeat(943))))
                        .code());
    }

    @Test
    void escapesAndShallowSegmentationPreserveExactOrderAndPrefix() {
        List<String> strings =
                new ArrayList<>(
                        List.of(
                                "a",
                                "a.b",
                                "a/b",
                                "a?",
                                "a~",
                                "é",
                                "\ud800",
                                "\ud83d\ude00",
                                "\uffff"));
        for (int n : new int[] {195, 196, 197, 198, 199, 200, 397, 398, 399})
            for (String suffix : List.of("", ".", "/b", "~", "é"))
                strings.add("a".repeat(n) + suffix);
        for (String a : strings)
            for (String b : strings) {
                assertEquals(
                        Integer.signum(a.compareTo(b)),
                        Integer.signum(layout.data(key(a)).compareTo(layout.data(key(b)))));
                assertEquals(a.startsWith(b), layout.data(key(a)).startsWith(layout.data(key(b))));
            }
        assertEquals("a.0b.1c~007e", S3Layout.encode("a.b/c~"));
        assertEquals(
                846, layout.data(key(String.join("/", Collections.nCopies(256, "a")))).length());
    }

    @Test
    void canonicalDecodingRejectsAlternateChunkingsAndEscapeSpellings() {
        for (int n = 195; n <= 200; n++)
            for (String suffix : List.of(".", "/b", "~", "é", "\ud800")) {
                String logical = "a".repeat(n) + suffix;
                assertEquals(
                        logical, layout.fromData("scope", layout.data(key(logical))).objectKey());
            }
        for (String bad :
                List.of(
                        "abc",
                        "x",
                        "xabc/",
                        "xabc/xx",
                        "xabc//xdef",
                        "x" + "a".repeat(200),
                        "x~0061",
                        "x~00E9",
                        "x.",
                        "x.2",
                        "x~",
                        "xraw/raw"))
            assertThrows(
                    StorageException.class,
                    () -> layout.fromData("scope", layout.scope("scope") + bad));
        assertThrows(
                StorageException.class, () -> layout.fromData("other", layout.data(key("abc"))));
    }

    @Test
    void everyControlTemplateIsShallowAndInvalidPhysicalNamesFailBeforeTransport() {
        String id = "00000000-0000-0000-0000-000000000000",
                hash = "a".repeat(64),
                index = Long.toString(Long.MAX_VALUE);
        for (String path :
                List.of(
                        "scopes/v3/" + hash + "/spec",
                        "keys/v3/" + hash + "/spec",
                        "sessions/v1/" + id + "/spec",
                        "sessions/v1/" + id + "/parts/2147483647",
                        "sessions/v1/" + id + "/terminal",
                        "sessions/v1/" + id + "/result",
                        "sessions/v1/" + id + "/completions/" + hash + "/page/2147483647",
                        "sessions/v1/" + id + "/completions/" + hash + "/root",
                        "sessions/v1/" + id + "/attempts/" + id + "/known-upload",
                        "sessions/v1/" + id + "/attempts/" + id + "/intent",
                        "staging/v1/" + id + "/" + id + "/chunk/" + index,
                        "staging/v1/" + id + "/" + id + "/manifest-page/" + index,
                        "staging/v1/" + id + "/" + id + "/root"))
            assertEquals(path, S3Layout.physical(path, 1024));
        var io = new S3Objects(null, S3ProtocolTest.config());
        for (String path :
                List.of("a//b", "a/../b", "a/./b", "a/", "a".repeat(201), "a/b/c/d/e/f/g/h/i"))
            assertEquals(
                    ErrorCode.INVALID_ARGUMENT,
                    assertThrows(StorageException.class, () -> io.create(path, new byte[0]))
                            .code());
    }

    @Test
    void credentialAndTimeoutRotationRetainsFullPhysicalScopeIdentity() {
        S3Config c = S3ProtocolTest.config();
        S3Config rotated =
                new S3Config(
                        c.backendId(),
                        c.endpoint(),
                        c.region(),
                        c.dataBucket(),
                        c.controlBucket(),
                        software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                                software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(
                                        "rotated", "rotated-secret")),
                        c.pathStyle(),
                        c.keyBudget(),
                        c.chunkBytes(),
                        c.nativeMinimum(),
                        c.nativeMaximum(),
                        c.nativeMaxParts(),
                        c.maximumObject(),
                        16,
                        java.time.Duration.ofSeconds(42));
        assertEquals(c.identity(), rotated.identity());
        assertArrayEquals(
                layout.scopeBinding("é\ud800"), new S3Layout(rotated).scopeBinding("é\ud800"));
        assertNotEquals(layout.scopeHash("\ud800"), layout.scopeHash("\ud801"));
        assertNotEquals(layout.scopeHash("a/b"), layout.scopeHash("a"));
    }
}
