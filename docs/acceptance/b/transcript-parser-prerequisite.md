# B-06 strict UTF-8 parser prerequisite

This internal pure-code prerequisite does not complete B-06 or authorize a production
format/size matrix. It has no Spring bean, registry, controller, upload capability,
DB/schema/migration, worker, B-05 consumer, model call or external integration.

## Internal port

`ama.transcript.TranscriptDocumentParser` declares:

```java
Result parse(InputStream source, UUID fileId, String expectedSourceSha256,
             ParseBudget budget, BooleanSupplier cancelled) throws ParseException;
```

`ParseBudget(long maxSourceBytes, long maxUtf16CodeUnits, int maxSegments)` is
explicit caller policy. Every bound must be positive; null required arguments and
anything other than exactly 64 lowercase hexadecimal SHA-256 characters fail
`INVALID_ARGUMENT` before source I/O. The UUID value is immutable; canonical
lowercase hyphenated `UUID.toString()` supplies the identity encoding. The parser
never creates or changes file location or source bytes. Its digest is a source
identity check, not a business input fingerprint or confirmation content hash.

`Result(List<Segment> segments, String sourceSha256, String parserProfileVersion)`
defensively copies its segment list. Segments are immutable records with
`UUID stableId`, `UUID sourceFileId`, `long paragraphNo`, `String text`, and nullable
`UUID speakerId`, `Long startMs`, `Long endMs`. The last three are always null for
this profile. All failures throw only a typed `ParseException.code()` and safe
code-name message with no nested source exception, body, path or provider detail.
No exception contains partially parsed segments.

## Explicit profile semantics

The only implementation is `TextTranscriptParser`, profile
`ama.strict-utf8-lines.v1`. It accepts only strict UTF-8 bytes and uses decoder
`REPORT` for malformed/unmappable data. It does not guess legacy encodings or
accept a filename as proof of format. A single initial UTF-8 BOM is an encoding
marker; any later U+FEFF remains exact text. NUL is explicitly `INVALID_TEXT`.
Other controls are preserved. CRLF, CR and LF each delimit one physical line;
nonblank lines retain their complete leading, trailing and interior text. Blank
means exactly Java `String.isBlank()`, so NBSP is retained. Blank lines are skipped
without renumbering later physical line positions. A document with no retained
text fails `NO_TEXT`; no placeholder, people or timestamps are invented. Labels,
`[00:01]`, SRT-looking markup and other strings have no special semantics here.

This is a bytes-to-plain-text profile, not a format detector. The port has no
filename, MIME or arbitrary format argument. DOCX/SRT/VTT/OCR/media profiles and
legacy encoding conversions are unsupported; a future dispatcher must reject
requests for those profiles explicitly rather than route them here based on an
extension. A valid UTF-8 string that resembles markup is still literal text; its
syntax is not a format capability claim. Invalid UTF-8 fails `INVALID_UTF8`.
Production format selection and supported sizes remain a separate approval gate.

## Source integrity and stable IDs

Success requires observing EOF and verifying SHA-256 over **every exact source
byte**, including optional BOM and original line separators. A stream error even
after all expected bytes, a mismatching SHA or malformed suffix cannot return a
partial successful transcript. Digest mismatch is `SOURCE_HASH_MISMATCH`.

The versioned ID encoding is four ordered UTF-8 fields:

1. profile version string `ama.strict-utf8-lines.v1`;
2. canonical lowercase source file UUID string;
3. exact lowercase source SHA-256 hex string;
4. 1-based physical line ordinal in unpadded ASCII decimal.

Each field is prefixed by its **four-byte unsigned big-endian byte length**. The
concatenation is hashed with SHA-256. Take the first 16 digest bytes; replace the
high nibble of byte 6 with `1000` (version 8) and the top two bits of byte 8 with
`10` (IETF variant), preserving other bits. Encode as canonical UUID. This is a
custom SHA-256 UUID v8 layout, not UUID v5 or an MD5-derived identifier. Changing
file, source bytes, ordinal or profile version changes the derivation input;
collision resistance has the normal truncated hash limit (122 retained bits).
Profile semantics/encoding changes must introduce a new version.

Independent Python `hashlib`/`struct` vector: source UTF-8 `hello`, file
`00000000-0000-4000-8000-000000000001`, source SHA
`2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824`,
line `1` yields `3a5df925-ae7a-8b89-95c0-3a85c7d96a17`. A focused Java test pins
that independently calculated result, repeated invocations, source/file changes,
and distinct identical-text line ordinals.

## Resource and cancellation contract

Byte reads are clamped to the remaining source budget plus **at most one** overflow
sentinel, never an unbounded read-ahead. Overflow fails `SOURCE_BYTES_EXCEEDED`.
The JDK reader has a fixed decoding byte buffer; the adapter has a 1024-unit char
buffer. The adapter counts every decoded UTF-16 unit, including a BOM and all
newlines, before adding it to the current line. The supplementary character 😀
therefore counts as two units. Overflow fails `UTF16_CODE_UNITS_EXCEEDED`.
Retained segment count is checked before appending to the list and fails
`SEGMENTS_EXCEEDED`. None of these failures truncate to a successful result.

No whole raw-file buffer/readAllBytes/readAllLines is used. Retention is
`O(maxUtf16CodeUnits + maxSegments)` plus fixed decoder/char buffers: retained
text and current line share the global decoded-unit bound; StringBuilder spare
capacity, the current line's transient String copy and immutable-list defensive
copy are constant-factor overhead. Caller budgets must be appropriate for its
heap; they are not global product quotas/defaults. Source read-ahead may reach the
bounded fixed decoder buffer before a decoded-unit/segment error, but never
exceeds maxSourceBytes plus one byte.

Cancellation is checked before source I/O, around each bounded read, around each
character-buffer read, per decoded unit, and before success. Thread interruption
also returns `CANCELLED` while preserving its flag. `InterruptedIOException`
restores the interrupt flag and returns `CANCELLED`; other read IOExceptions
return `SOURCE_READ_FAILED` unless cancellation/interruption is active. No stream
is closed on success or failure. The caller owns blocking-stream timeout and
lifetime; a signal cannot by itself interrupt an arbitrary already-blocked read.

## Provenance boundaries and verification

This slice preserves raw-file UUID, exact hash, physical-line provenance and parser
version for the later immutable processing input. It does not create WORKING or
RAW revisions, `transcription_input`, business fingerprints, marks, confirmations,
ACLs or candidates. Product supplement and B00 R5/R6 remain authoritative; the full
B-06 service, immutable input, V011, confirmation/marks hash rules and B-05 handoff
remain open.

Focused verification, pinned JDK via the coordinator's `.tools/env.sh`:

```bash
./mvnw -B -pl backend/adapters -am -Dtest=TextTranscriptParserTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

The selector builds contracts/core/adapters and executes only the parser test
class. It does not execute API, IT, database, H2, Docker, server, web or Python
aggregate suites. Ignored local evidence lives under
`artifacts/phase-b/b06-parser`; the coordinator report records exact counts,
source/commit identities and an explicit raw-artifact allowlist and SHA-256s.


## Coordinator adoption

Fullownedc370aa2..9ae6280 independently SPECcompliant/qualityApproved0findings; coordinator read full implementation/review reports. Integratedf3f5d8c, all4owned paths byte-identical before this appendix. Final focused19 tests passed; previous15RED/15GREEN overlap and are not added. All11rawcaptures+manifest independently SHA-verified/copied to `artifacts/phase-b/b06-parser/`; both raw reports preserved separately with SHA before public-copy newline normalization. See [implementation](transcript-parser-implementation-report.md), [independent review](transcript-parser-independent-review.md). No extra tests/DB/network actions for mechanical adoption. StrictUTF8 profile remains unwired: production format/size activation, fullB06/V011/input/ACL/confirmation/marks/fingerprints and company qualification remain OPEN; no SQL or existing plan changed.
