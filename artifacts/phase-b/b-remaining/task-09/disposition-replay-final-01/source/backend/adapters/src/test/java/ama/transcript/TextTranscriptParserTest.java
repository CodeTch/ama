package ama.transcript;

import static ama.transcript.TranscriptDocumentParser.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class TextTranscriptParserTest {
    private final TranscriptDocumentParser parser = new TextTranscriptParser();
    private static final UUID FILE = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final ParseBudget ROOM = new ParseBudget(100_000, 100_000, 100);

    private static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
    private static String sha(byte[] source) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
    }
    private Result parse(String source) throws Exception { return parse(bytes(source), ROOM); }
    private Result parse(byte[] source, ParseBudget budget) throws Exception {
        return parser.parse(new ByteArrayInputStream(source), FILE, sha(source), budget, () -> false);
    }
    private void fails(ErrorCode expected, byte[] source, ParseBudget budget) throws Exception {
        var tracked = new TrackingStream(source, 3);
        assertEquals(expected, assertThrows(ParseException.class,
            () -> parser.parse(tracked, FILE, sha(source), budget, () -> false)).code());
        assertFalse(tracked.closed);
    }

    @Test void preservesUnicodeSpacesAndPhysicalLinesWithoutInferringPeopleOrTimes() throws Exception {
        var result = parse("\ufeff  中文😀  \r\n\t \r张三：[00:01] hi\n\n末\ufeff尾");
        assertEquals(List.of("  中文😀  ", "张三：[00:01] hi", "末\ufeff尾"),
            result.segments().stream().map(Segment::text).toList());
        assertEquals(List.of(1L, 3L, 5L), result.segments().stream().map(Segment::paragraphNo).toList());
        for (var segment : result.segments()) {
            assertEquals(FILE, segment.sourceFileId());
            assertNull(segment.speakerId()); assertNull(segment.startMs()); assertNull(segment.endMs());
        }
        assertEquals(sha(bytes("\ufeff  中文😀  \r\n\t \r张三：[00:01] hi\n\n末\ufeff尾")), result.sourceSha256());
        assertThrows(UnsupportedOperationException.class, () -> result.segments().clear());
    }

    @Test void stableIdsRepeatButChangeWithFileOrExactSourceBytes() throws Exception {
        var a = parse("hello");
        assertEquals(a, parse("hello"));
        assertNotEquals(a.segments().getFirst().stableId(), parse("hello\n").segments().getFirst().stableId());
        var other = parser.parse(new ByteArrayInputStream(bytes("hello")), UUID.randomUUID(), sha(bytes("hello")), ROOM, () -> false);
        assertNotEquals(a.segments().getFirst().stableId(), other.segments().getFirst().stableId());
        assertEquals(UUID.fromString("3a5df925-ae7a-8b89-95c0-3a85c7d96a17"), a.segments().getFirst().stableId());
        assertEquals("ama.strict-utf8-lines.v1", a.parserProfileVersion());
        assertEquals(8, a.segments().getFirst().stableId().version());
        assertEquals(2, a.segments().getFirst().stableId().variant());
    }

    @Test void rejectsMalformedTruncatedAndLegacyUtf8WithoutPartialSuccess() throws Exception {
        for (byte[] source : List.of(new byte[]{'o', 'k', 10, (byte)0xc3, 0x28},
                new byte[]{'o', 'k', 10, (byte)0xf0, (byte)0x9f},
                new byte[]{(byte)0xff, (byte)0xfe, 'a', 0},
                new byte[]{(byte)0xc0, (byte)0xaf},
                new byte[]{(byte)0xed, (byte)0xa0, (byte)0x80})) {
            fails(ErrorCode.INVALID_UTF8, source, ROOM);
        }
    }

    @Test void rejectsWrongHashIncludingChangedBomOrLineSeparators() throws Exception {
        for (String source : List.of("hello", "\ufeffhello\n", "hello\r\n")) {
            assertEquals(ErrorCode.SOURCE_HASH_MISMATCH, assertThrows(ParseException.class,
                () -> parser.parse(new ByteArrayInputStream(bytes(source)), FILE, sha(bytes("hello\n")), ROOM, () -> false)).code());
        }
    }

    @Test void emptyAndJavaWhitespaceOnlyAreNoText() throws Exception {
        for (String source : List.of("", "\ufeff", " \t\r\n\u2003\n")) {
            fails(ErrorCode.NO_TEXT, bytes(source), ROOM);
        }
        assertEquals("\u00a0", parse("\u00a0").segments().getFirst().text());
        assertEquals("\ufeff", parse("\ufeff\ufeff").segments().getFirst().text());
    }

    @Test void nulFailsExplicitlyWithoutDroppingIt() throws Exception {
        fails(ErrorCode.INVALID_TEXT, bytes("ok\nsecret\u0000tail"), ROOM);
    }

    @Test void explicitBudgetsAcceptExactBoundaryAndFailOneOverWithoutTruncation() throws Exception {
        assertEquals("x", parse(bytes("x"), new ParseBudget(1, 1, 1)).segments().getFirst().text());
        assertEquals("x", parse(bytes("x"), new ParseBudget(Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE)).segments().getFirst().text());
        var source = bytes("中😀\nA"); // 9 bytes, 5 UTF-16 units, 2 nonblank lines
        assertEquals(2, parse(source, new ParseBudget(9, 5, 2)).segments().size());
        fails(ErrorCode.SOURCE_BYTES_EXCEEDED, source, new ParseBudget(8, 5, 2));
        fails(ErrorCode.UTF16_CODE_UNITS_EXCEEDED, source, new ParseBudget(9, 4, 2));
        fails(ErrorCode.SEGMENTS_EXCEEDED, source, new ParseBudget(9, 5, 1));
        // BOM and delimiters count toward the technical decoded-unit bound.
        assertEquals(1, parse(bytes("\ufeffx\r\n"), new ParseBudget(6, 4, 1)).segments().size());
        fails(ErrorCode.UTF16_CODE_UNITS_EXCEEDED, bytes("\ufeffx\r\n"), new ParseBudget(6, 3, 1));
    }

    @Test void byteBudgetReadsAtMostOneOverflowSentinel() throws Exception {
        for (int chunk : new int[]{1, 4096}) {
            var raw = bytes("x".repeat(1000));
            var source = new TrackingStream(raw, chunk);
            assertEquals(ErrorCode.SOURCE_BYTES_EXCEEDED, assertThrows(ParseException.class,
                () -> parser.parse(source, FILE, sha(raw), new ParseBudget(1, 100, 100), () -> false)).code());
            assertEquals(2, source.delivered);
            assertFalse(source.closed);
        }
    }

    @Test void cancellationBeforeReadingDoesNotTouchCallerStream() throws Exception {
        var source = new TrackingStream(bytes("abc"), 1);
        assertEquals(ErrorCode.CANCELLED, assertThrows(ParseException.class,
            () -> parser.parse(source, FILE, sha(bytes("abc")), ROOM, () -> true)).code());
        assertEquals(0, source.readCalls); assertFalse(source.closed);
    }

    @Test void cancellationAfterReadStopsBeforeAnotherReadAndNeverReturnsPartial() throws Exception {
        var cancel = new AtomicBoolean();
        var source = new TrackingStream(bytes("one\ntwo\nthree"), 4) {
            @Override public int read(byte[] buffer, int offset, int length) {
                int count = super.read(buffer, offset, length); cancel.set(true); return count;
            }
        };
        assertEquals(ErrorCode.CANCELLED, assertThrows(ParseException.class,
            () -> parser.parse(source, FILE, sha(bytes("one\ntwo\nthree")), ROOM, cancel::get)).code());
        assertEquals(1, source.readCalls); assertFalse(source.closed);
    }

    @Test void threadInterruptionIsPreservedAndPreventsRead() throws Exception {
        var source = new TrackingStream(bytes("abc"), 1);
        Thread.currentThread().interrupt();
        try {
            assertEquals(ErrorCode.CANCELLED, assertThrows(ParseException.class,
                () -> parser.parse(source, FILE, sha(bytes("abc")), ROOM, () -> false)).code());
            assertTrue(Thread.currentThread().isInterrupted()); assertEquals(0, source.readCalls);
        } finally { Thread.interrupted(); }
    }

    @Test void readFailuresAreTypedSafeAndDoNotCloseCallerStream() throws Exception {
        var closed = new AtomicBoolean();
        InputStream source = new InputStream() {
            private int reads;
            @Override public int read() throws IOException {
                if (reads++ < 3) return 'a';
                throw new IOException("private /secret/provider credential=example");
            }
            @Override public void close() { closed.set(true); }
        };
        var error = assertThrows(ParseException.class,
            () -> parser.parse(source, FILE, "a".repeat(64), ROOM, () -> false));
        assertEquals(ErrorCode.SOURCE_READ_FAILED, error.code());
        assertFalse(error.toString().contains("private")); assertNull(error.getCause()); assertFalse(closed.get());
    }

    @Test void streamRemainsCallerOwnedAfterSuccessAndHashFailure() throws Exception {
        for (boolean mismatch : List.of(false, true)) {
            var source = new TrackingStream(bytes("hello"), 2);
            if (mismatch) assertThrows(ParseException.class,
                () -> parser.parse(source, FILE, "a".repeat(64), ROOM, () -> false));
            else assertEquals(sha(bytes("hello")), parser.parse(source, FILE, sha(bytes("hello")), ROOM, () -> false).sourceSha256());
            assertFalse(source.closed); assertTrue(source.eofSeen);
        }
    }

    @Test void splitMultibyteCharactersAndMixedDelimitersWorkWithSingleByteReads() throws Exception {
        var raw = bytes("\ufeff😀\r\n中\rZ\nlast");
        var source = new TrackingStream(raw, 1);
        var result = parser.parse(source, FILE, sha(raw), ROOM, () -> false);
        assertEquals(List.of("😀", "中", "Z", "last"), result.segments().stream().map(Segment::text).toList());
        assertEquals(List.of(1L, 2L, 3L, 4L), result.segments().stream().map(Segment::paragraphNo).toList());
    }

    @Test void invalidArgumentsFailBeforeAnyIo() throws Exception {
        var source = new TrackingStream(bytes("abc"), 1);
        for (String hash : Arrays.asList(null, "A".repeat(64), "a".repeat(63), "g".repeat(64))) {
            assertEquals(ErrorCode.INVALID_ARGUMENT, assertThrows(ParseException.class,
                () -> parser.parse(source, FILE, hash, ROOM, () -> false)).code());
        }
        for (ParseBudget budget : List.of(new ParseBudget(0, 1, 1), new ParseBudget(1, -1, 1), new ParseBudget(1, 1, 0))) {
            assertEquals(ErrorCode.INVALID_ARGUMENT, assertThrows(ParseException.class,
                () -> parser.parse(source, FILE, "a".repeat(64), budget, () -> false)).code());
        }
        assertEquals(0, source.readCalls);
    }

    @Test void delimitersAcrossBufferBoundariesDoNotRenumberBlankLines() throws Exception {
        String prefix = "a".repeat(1023);
        var result = parse(prefix + "\r\n\r\n\rB\nB\n");
        assertEquals(List.of(prefix, "B", "B"), result.segments().stream().map(Segment::text).toList());
        assertEquals(List.of(1L, 4L, 5L), result.segments().stream().map(Segment::paragraphNo).toList());
        assertNotEquals(result.segments().get(1).stableId(), result.segments().get(2).stableId());
    }

    @Test void matchingBytesWithoutConfirmedEofStillFailOnReadError() throws Exception {
        var source = new InputStream() {
            private boolean delivered;
            @Override public int read(byte[] buffer, int offset, int length) throws IOException {
                if (delivered) throw new IOException("sensitive EOF failure");
                delivered = true; buffer[offset] = 'x'; return 1;
            }
            @Override public int read() { throw new AssertionError("bulk stream required"); }
        };
        assertEquals(ErrorCode.SOURCE_READ_FAILED, assertThrows(ParseException.class,
            () -> parser.parse(source, FILE, sha(bytes("x")), ROOM, () -> false)).code());
    }

    @Test void interruptedReadRestoresInterruptFlagWithoutExposingSourceDetails() throws Exception {
        var source = new InputStream() {
            @Override public int read() throws IOException { throw new InterruptedIOException("secret"); }
        };
        try {
            var error = assertThrows(ParseException.class,
                () -> parser.parse(source, FILE, sha(bytes("x")), ROOM, () -> false));
            assertEquals(ErrorCode.CANCELLED, error.code());
            assertTrue(Thread.currentThread().isInterrupted());
            assertNull(error.getCause()); assertFalse(error.toString().contains("secret"));
        } finally { Thread.interrupted(); }
    }

    @Test void linesBeyondOneReadBufferRetainExactUnicodeWithoutSourceBuffering() throws Exception {
        String first = "中😀 ".repeat(2200);
        var raw = bytes(first + "\r\n\n终");
        var source = new TrackingStream(raw, 71);
        var result = parser.parse(source, FILE, sha(raw), ROOM, () -> false);
        assertEquals(List.of(first, "终"), result.segments().stream().map(Segment::text).toList());
        assertEquals(List.of(1L, 3L), result.segments().stream().map(Segment::paragraphNo).toList());
        assertTrue(source.eofSeen); assertFalse(source.closed);
    }

    private static class TrackingStream extends ByteArrayInputStream {
        int delivered, readCalls; boolean closed, eofSeen; final int chunk;
        TrackingStream(byte[] source, int chunk) { super(source); this.chunk = chunk; }
        @Override public synchronized int read(byte[] buffer, int offset, int length) {
            readCalls++;
            int count = super.read(buffer, offset, Math.min(length, chunk));
            if (count < 0) eofSeen = true; else delivered += count;
            return count;
        }
        @Override public void close() { closed = true; }
    }
}
