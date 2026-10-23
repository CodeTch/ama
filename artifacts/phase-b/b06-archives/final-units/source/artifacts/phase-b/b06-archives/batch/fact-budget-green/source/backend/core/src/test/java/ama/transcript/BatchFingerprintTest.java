package ama.transcript;

import ama.canonical.CanonicalValue;
import ama.canonical.CanonicalValueCodec;
import ama.contract.MinutesContracts.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static ama.canonical.CanonicalValue.*;
import static ama.contract.MinutesContracts.*;
import static ama.contract.MinutesContracts.FailureCode.*;
import static org.junit.jupiter.api.Assertions.*;

class BatchFingerprintTest {
    static final String MEETING = "11111111-1111-4111-8111-111111111111";
    static final String OTHER = "22222222-2222-4222-8222-222222222222";
    static final String BATCH = "33333333-3333-4333-8333-333333333333";
    static final String FIRST = "44444444-4444-4444-8444-444444444444";
    static final String SECOND = "55555555-5555-4555-8555-555555555555";
    static final String EMPTY = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    static final String A = "559aead08264d5795d3909718cdd05abd49572e84fe55590eef31a88a08fdffd";
    static final String HASH = "c4a558e4dc8aff8289b4ed60193efdfbc7f64f73ae642e2eec6398c473458053";
    static final CanonicalBudget B = new CanonicalBudget(10000, 16, 1000, 1000, 100);
    final BatchFingerprint helper = new BatchFingerprint();

    static List<BatchFingerprint.Member> members() {
        return List.of(new BatchFingerprint.Member(1, FIRST, EMPTY, 0),
                       new BatchFingerprint.Member(2, SECOND, A, 1));
    }
    String compute(List<BatchFingerprint.Member> values) {
        return helper.compute(MEETING, BATCH, "TRANSCRIPT", 9, values, B, () -> false);
    }
    void fails(FailureCode code, Runnable action) {
        var failure = assertThrows(Failure.class, action::run);
        assertEquals(code, failure.code());
        assertEquals(code.name(), failure.getMessage());
        assertNull(failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
    }
    static byte[] resource(String extension) throws IOException {
        try (var stream = BatchFingerprintTest.class.getResourceAsStream("/ama/transcript/batch-v1.literal." + extension)) {
            assertNotNull(stream);
            return stream.readAllBytes();
        }
    }
    static FullRow batch(String fingerprint) {
        return new FullRow(Table.INPUT_BATCH, List.of(cell(BATCH), cell(MEETING), cell("TRANSCRIPT"),
                cell(fingerprint), cell(9), cell("2026-10-23T00:00:00.000000Z")));
    }
    static FullRow member(String batchId, long ordinal, String fileId, String hash) {
        return new FullRow(Table.INPUT_BATCH_FILE, List.of(cell(batchId), cell(ordinal), cell(fileId), cell(hash)));
    }
    static List<FullRow> rows() {
        return List.of(member(BATCH, 1, FIRST, EMPTY), member(BATCH, 2, SECOND, A));
    }
    static List<ContentFile> files() {
        return List.of(new ContentFile(FIRST, EMPTY, 0, MEETING), new ContentFile(SECOND, A, 1, MEETING));
    }
    static FullRow replace(FullRow row, String column, Cell value) {
        var cells = new ArrayList<>(row.cells());
        cells.set(row.table().index(column), value);
        return new FullRow(row.table(), cells);
    }

    // A raw JSON hash or a codec replacement must break this independently frozen byte assertion.
    @Test void literalVectorMatchesIndependentBytes() throws Exception {
        var expected = resource("bytes");
        assertEquals(734, expected.length);
        assertArrayEquals(expected, HexFormat.of().parseHex(new String(resource("hex"), StandardCharsets.US_ASCII).strip()));
        assertEquals(HASH, new String(resource("sha256"), StandardCharsets.US_ASCII).strip());
        var projected = new ArrayList<CanonicalValue>();
        for (var item : members()) projected.add(object(field("ordinal", number(item.ordinal())),
                field("fileId", string(item.fileId())), field("sha256", string(item.sha256())),
                field("sizeBytes", number(item.sizeBytes()))));
        var envelope = object(field("profile", string("ama.input-batch.v1")), field("kind", string("INPUT_BATCH")),
                field("data", object(field("meetingId", string(MEETING)), field("batchId", string(BATCH)),
                        field("kind", string("TRANSCRIPT")), field("baseRevision", number(9)),
                        field("files", new Sequence(projected)))));
        assertArrayEquals(expected, new CanonicalValueCodec().encode(envelope, B, () -> false));
        assertEquals(HASH, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(expected)));
        assertNotEquals(HASH, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(resource("json"))));
        assertEquals(HASH, compute(members()));
    }

    @Test void orderAndEveryIdentityFieldAffectDigest() {
        var baseline = compute(members());
        assertEquals(HASH, baseline);
        assertNotEquals(baseline, helper.compute(OTHER, BATCH, "TRANSCRIPT", 9, members(), B, () -> false));
        assertNotEquals(baseline, helper.compute(MEETING, OTHER, "TRANSCRIPT", 9, members(), B, () -> false));
        assertNotEquals(baseline, helper.compute(MEETING, BATCH, "TRANSCRIPT", 10, members(), B, () -> false));
        assertNotEquals(baseline, compute(List.of(new BatchFingerprint.Member(1, SECOND, A, 1),
                                                 new BatchFingerprint.Member(2, FIRST, EMPTY, 0))));
        assertNotEquals(baseline, compute(List.of(new BatchFingerprint.Member(1, OTHER, EMPTY, 0), members().get(1))));
        assertNotEquals(baseline, compute(List.of(new BatchFingerprint.Member(1, FIRST, A, 0), members().get(1))));
        assertNotEquals(baseline, compute(List.of(new BatchFingerprint.Member(1, FIRST, EMPTY, 1), members().get(1))));
        assertNotEquals(baseline, compute(List.of(members().get(0))));
        assertNotEquals(baseline, helper.compute(MEETING, BATCH, "TRANSCRIPT", Long.MAX_VALUE,
                List.of(new BatchFingerprint.Member(1, FIRST, EMPTY, Long.MAX_VALUE)), B, () -> false));
    }

    @Test void invalidOrIncompleteMembersFailClosed() {
        fails(INPUT_INVALID, () -> compute(List.of()));
        fails(INVALID_ARGUMENT, () -> compute(null));
        fails(INPUT_INVALID, () -> compute(Arrays.asList(members().get(0), null)));
        fails(INPUT_INVALID, () -> compute(List.of(new BatchFingerprint.Member(0, FIRST, EMPTY, 0))));
        fails(INPUT_INVALID, () -> compute(List.of(new BatchFingerprint.Member(2, FIRST, EMPTY, 0))));
        fails(INPUT_INVALID, () -> compute(List.of(members().get(1), members().get(0))));
        fails(INPUT_INVALID, () -> compute(List.of(members().get(0), new BatchFingerprint.Member(3, SECOND, A, 1))));
        fails(INPUT_INVALID, () -> compute(List.of(members().get(0), new BatchFingerprint.Member(2, FIRST, EMPTY, 0))));
        fails(INVALID_NUMBER, () -> compute(List.of(new BatchFingerprint.Member(-1, FIRST, EMPTY, 0))));
        fails(INVALID_NUMBER, () -> compute(List.of(new BatchFingerprint.Member(1, FIRST, EMPTY, -1))));
        fails(INVALID_NUMBER, () -> helper.compute(MEETING, BATCH, "TRANSCRIPT", -1, members(), B, () -> false));
        for (String invalid : List.of("", FIRST.toUpperCase(Locale.ROOT).replace("44444444", "AAAAAAAA"),
                FIRST + " ", "00000000-0000-0000-0000-000000000000", "\ud800")) {
            fails(INVALID_ID, () -> compute(List.of(new BatchFingerprint.Member(1, invalid, EMPTY, 0))));
            fails(INVALID_ID, () -> helper.compute(invalid, BATCH, "TRANSCRIPT", 9, members(), B, () -> false));
            fails(INVALID_ID, () -> helper.compute(MEETING, invalid, "TRANSCRIPT", 9, members(), B, () -> false));
        }
        for (String invalid : List.of("", A.toUpperCase(Locale.ROOT), EMPTY + " ", "g".repeat(64), "\udfff"))
            fails(INVALID_ID, () -> compute(List.of(new BatchFingerprint.Member(1, FIRST, invalid, 0))));
        fails(INVALID_ID, () -> compute(List.of(new BatchFingerprint.Member(1, null, EMPTY, 0))));
        fails(INVALID_ID, () -> compute(List.of(new BatchFingerprint.Member(1, FIRST, null, 0))));
        for (String kind : Arrays.asList(null, "", "MEDIA", "transcript", "TRANSCRIPT ", "\ud800"))
            fails(INPUT_INVALID, () -> helper.compute(MEETING, BATCH, kind, 9, members(), B, () -> false));
    }

    @Test void verifyComparesActualBatchFingerprintAndReturnsComputedValue() {
        assertEquals(HASH, helper.verify(batch(HASH), rows(), files(), B, () -> false));
        assertEquals(HASH, helper.verify(batch(HASH), rows(), List.of(files().get(1), files().get(0)), B, () -> false));
        assertEquals(HASH, helper.verify(replace(batch(HASH), "created_at", cell("2026-10-24T00:00:00.000000Z")),
                rows(), files(), B, () -> false));
        fails(FINGERPRINT_MISMATCH, () -> helper.verify(batch(EMPTY), rows(), files(), B, () -> false));
        fails(FINGERPRINT_MISMATCH, () -> helper.verify(replace(batch(HASH), "base_revision", cell(10)), rows(), files(), B, () -> false));
        fails(FINGERPRINT_MISMATCH, () -> helper.verify(batch(HASH), rows(),
                List.of(new ContentFile(FIRST, EMPTY, 1, MEETING), files().get(1)), B, () -> false));
        fails(INPUT_INVALID, () -> helper.verify(replace(batch(HASH), "kind", cell("MEDIA")), rows(), files(), B, () -> false));
    }

    @Test void verifyRejectsMissingExtraDuplicateForeignAndContradictoryMetadata() {
        for (List<ContentFile> invalid : List.of(List.<ContentFile>of(), List.of(files().get(0)),
                List.of(files().get(0), files().get(1), new ContentFile(OTHER, A, 1, MEETING)),
                List.of(files().get(0), files().get(0)),
                List.of(new ContentFile(FIRST, A, 0, MEETING), files().get(1)),
                List.of(new ContentFile(FIRST, EMPTY, 0, OTHER), files().get(1)),
                List.of(new ContentFile(FIRST, EMPTY, 0), files().get(1)),
                List.of(new ContentFile(OTHER, EMPTY, 0, MEETING), files().get(1))))
            fails(INPUT_INVALID, () -> helper.verify(batch(HASH), rows(), invalid, B, () -> false));
        fails(INVALID_ARGUMENT, () -> helper.verify(null, rows(), files(), B, () -> false));
        fails(INVALID_ARGUMENT, () -> helper.verify(batch(HASH), null, files(), B, () -> false));
        fails(INVALID_ARGUMENT, () -> helper.verify(batch(HASH), rows(), null, B, () -> false));
        fails(INPUT_INVALID, () -> helper.verify(rows().get(0), rows(), files(), B, () -> false));
        fails(INPUT_INVALID, () -> helper.verify(batch(HASH), List.of(batch(HASH)), files(), B, () -> false));
        fails(INPUT_INVALID, () -> helper.verify(batch(HASH), List.of(), files(), B, () -> false));
        fails(INPUT_INVALID, () -> helper.verify(batch(HASH), Arrays.asList(rows().get(0), null), files(), B, () -> false));
        fails(INPUT_INVALID, () -> helper.verify(batch(HASH), rows(), Arrays.asList(files().get(0), null), B, () -> false));
        fails(INPUT_INVALID, () -> helper.verify(batch(HASH), List.of(member(OTHER, 1, FIRST, EMPTY), rows().get(1)), files(), B, () -> false));
        fails(INPUT_INVALID, () -> helper.verify(batch(HASH), List.of(rows().get(1), rows().get(0)), files(), B, () -> false));
        fails(INPUT_INVALID, () -> helper.verify(batch(HASH), List.of(rows().get(0), member(BATCH, 3, SECOND, A)), files(), B, () -> false));
        fails(INPUT_INVALID, () -> helper.verify(batch(HASH), List.of(rows().get(0), member(BATCH, 2, FIRST, EMPTY)), files(), B, () -> false));
        fails(INPUT_INVALID, () -> helper.verify(batch(HASH), List.of(rows().get(0), rows().get(1), member(BATCH, 3, OTHER, A)), files(), B, () -> false));
    }

    @Test void canonicalBudgetsAndCancellationNeverReturnSuccess() {
        assertEquals(HASH, helper.compute(MEETING, BATCH, "TRANSCRIPT", 9, members(),
                new CanonicalBudget(734, 8, 1000, 64, 5), () -> false));
        for (var budget : List.of(new CanonicalBudget(733, 16, 1000, 1000, 100),
                new CanonicalBudget(10000, 2, 1000, 1000, 100), new CanonicalBudget(10000, 16, 1, 1000, 100),
                new CanonicalBudget(10000, 16, 1000, 63, 100), new CanonicalBudget(10000, 16, 1000, 1000, 1))) {
            fails(BUDGET_EXCEEDED, () -> helper.compute(MEETING, BATCH, "TRANSCRIPT", 9, members(), budget, () -> false));
            fails(BUDGET_EXCEEDED, () -> helper.verify(batch(HASH), rows(), files(), budget, () -> false));
        }
        fails(INVALID_ARGUMENT, () -> helper.compute(MEETING, BATCH, "TRANSCRIPT", 9, members(), null, () -> false));
        fails(INVALID_ARGUMENT, () -> helper.compute(MEETING, BATCH, "TRANSCRIPT", 9, members(), B, null));
        fails(CANCELLED, () -> helper.compute(MEETING, BATCH, "TRANSCRIPT", 9, members(), B, () -> true));
        fails(CANCELLED, () -> helper.verify(batch(HASH), rows(), files(), B, () -> true));
        var checks = new AtomicInteger();
        fails(CANCELLED, () -> helper.compute(MEETING, BATCH, "TRANSCRIPT", 9, members(), B, () -> checks.incrementAndGet() > 12));
        checks.set(0);
        fails(CANCELLED, () -> helper.verify(batch(HASH), rows(), files(), B, () -> checks.incrementAndGet() > 12));
        Thread.currentThread().interrupt();
        try {
            fails(CANCELLED, () -> compute(members()));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }
}
