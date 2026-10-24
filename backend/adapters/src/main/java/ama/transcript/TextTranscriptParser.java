package ama.transcript;

import static ama.transcript.TranscriptDocumentParser.ErrorCode.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Unwired internal strict UTF-8 profile. The caller owns the source and its blocking timeout. */
public final class TextTranscriptParser implements MeasuredTranscriptDocumentParser {
    public static final String PROFILE_VERSION = "ama.strict-utf8-lines.v1";

    @Override public Result parse(InputStream source, UUID fileId, String expectedSourceSha256,
                                  ParseBudget budget, BooleanSupplier cancelled) throws ParseException {
        return parseMeasured(source,fileId,expectedSourceSha256,budget,cancelled).result();
    }

    @Override public MeasuredResult parseMeasured(InputStream source, UUID fileId, String expectedSourceSha256,
                                  ParseBudget budget, BooleanSupplier cancelled) throws ParseException {
        if (source == null || fileId == null || expectedSourceSha256 == null
                || !expectedSourceSha256.matches("[0-9a-f]{64}") || budget == null
                || budget.maxSourceBytes() <= 0 || budget.maxUtf16CodeUnits() <= 0
                || budget.maxSegments() <= 0 || cancelled == null) {
            throw new ParseException(INVALID_ARGUMENT);
        }
        checkCancellation(cancelled);
        var digest = sha256();
        var boundedSource = new BoundedSource(source, budget.maxSourceBytes(), digest, cancelled);
        var decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        // Never close this reader: closing it would close the caller-owned source.
        var reader = new InputStreamReader(boundedSource, decoder);
        var lines = new Lines(fileId, expectedSourceSha256, budget, cancelled);
        char[] buffer = new char[1024];
        try {
            int count;
            while (true) {
                checkCancellation(cancelled);
                count = reader.read(buffer);
                checkCancellation(cancelled);
                if (count < 0) break;
                for (int i = 0; i < count; i++) lines.accept(buffer[i]);
            }
        } catch (LimitOrCancellation e) {
            throw new ParseException(e.code);
        } catch (CharacterCodingException e) {
            throw new ParseException(INVALID_UTF8);
        } catch (InterruptedIOException e) {
            Thread.currentThread().interrupt();
            throw new ParseException(CANCELLED);
        } catch (IOException e) {
            checkCancellation(cancelled);
            throw new ParseException(SOURCE_READ_FAILED);
        }
        checkCancellation(cancelled);
        String actualSha256 = HexFormat.of().formatHex(digest.digest());
        if (!actualSha256.equals(expectedSourceSha256)) throw new ParseException(SOURCE_HASH_MISMATCH);
        lines.finishLine();
        checkCancellation(cancelled);
        if (lines.segments.isEmpty()) throw new ParseException(NO_TEXT);
        return new MeasuredResult(new Result(lines.segments, actualSha256, PROFILE_VERSION),
            new Usage(boundedSource.used, lines.units, lines.segments.size()));
    }

    private static void checkCancellation(BooleanSupplier cancelled) throws ParseException {
        // isInterrupted deliberately preserves the interrupt flag.
        if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) throw new ParseException(CANCELLED);
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }

    private static UUID stableId(UUID fileId, String sourceHash, long paragraphNo) {
        var digest = sha256();
        for (String field : new String[]{PROFILE_VERSION, fileId.toString(), sourceHash, Long.toString(paragraphNo)}) {
            byte[] value = field.getBytes(StandardCharsets.UTF_8);
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value.length).array());
            digest.update(value);
        }
        byte[] hash = digest.digest();
        hash[6] = (byte) ((hash[6] & 0x0f) | 0x80); // Custom UUID version 8.
        hash[8] = (byte) ((hash[8] & 0x3f) | 0x80); // IETF variant 10.
        var uuid = ByteBuffer.wrap(hash);
        return new UUID(uuid.getLong(), uuid.getLong());
    }

    private static final class Lines {
        private final UUID fileId;
        private final String sourceHash;
        private final ParseBudget budget;
        private final BooleanSupplier cancelled;
        private final ArrayList<Segment> segments = new ArrayList<>();
        private final StringBuilder line = new StringBuilder();
        private long units, paragraphNo = 1;
        private boolean first = true, previousCr;

        private Lines(UUID fileId, String sourceHash, ParseBudget budget, BooleanSupplier cancelled) {
            this.fileId = fileId; this.sourceHash = sourceHash; this.budget = budget; this.cancelled = cancelled;
        }

        private void accept(char value) throws ParseException {
            checkCancellation(cancelled);
            if (units == budget.maxUtf16CodeUnits()) throw new ParseException(UTF16_CODE_UNITS_EXCEEDED);
            units++;
            if (first) {
                first = false;
                if (value == '\ufeff') return;
            }
            if (value == 0) throw new ParseException(INVALID_TEXT);
            if (previousCr && value == '\n') { previousCr = false; return; }
            previousCr = value == '\r';
            if (value == '\r' || value == '\n') {
                finishLine();
                paragraphNo++;
            } else line.append(value);
        }

        private void finishLine() throws ParseException {
            String text = line.toString();
            if (!text.isBlank()) {
                if (segments.size() == budget.maxSegments()) throw new ParseException(SEGMENTS_EXCEEDED);
                segments.add(new Segment(stableId(fileId, sourceHash, paragraphNo), fileId,
                    paragraphNo, text, null, null, null));
            }
            line.setLength(0);
        }
    }

    private static final class LimitOrCancellation extends IOException {
        private final ErrorCode code;
        private LimitOrCancellation(ErrorCode code) { this.code = code; }
    }

    /** Clamps each read to remaining bytes plus one sentinel, with no whole-file buffering. */
    private static final class BoundedSource extends InputStream {
        private final InputStream source;
        private final long limit;
        private final MessageDigest digest;
        private final BooleanSupplier cancelled;
        private long used;

        private BoundedSource(InputStream source, long limit, MessageDigest digest, BooleanSupplier cancelled) {
            this.source = source; this.limit = limit; this.digest = digest; this.cancelled = cancelled;
        }

        private void check() throws LimitOrCancellation {
            if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) throw new LimitOrCancellation(CANCELLED);
        }

        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            check();
            long remaining = limit - used;
            int bounded = remaining >= length ? length : (int) remaining + 1;
            int count = source.read(buffer, offset, bounded);
            check();
            if (count > 0) {
                if (count > remaining) throw new LimitOrCancellation(SOURCE_BYTES_EXCEEDED);
                used += count;
                digest.update(buffer, offset, count);
            }
            return count;
        }

        @Override public int read() throws IOException {
            byte[] one = new byte[1];
            int count = read(one, 0, 1);
            return count < 0 ? -1 : Byte.toUnsignedInt(one[0]);
        }
    }
}
