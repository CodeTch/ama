# B-06 parser prerequisite implementation report

Status: IMPLEMENTED, locally committed; independent coordinator review pending.
This closes only the bounded strict UTF-8 pure-parser prerequisite. Full B-06 remains OPEN.

## Identity and scope

- BASE: `c370aa21181afd8d6255e2e29ec7da496eb42b97` (exact task baseline).
- HEAD/local commit: `9ae628058d53ddaff64e14adeeecb64ded653c1c`.
- Branch: `codex/phase-b-transcript-parser`.
- Own tree: `/home/tanren/ama/.worktrees/phase-b-transcript-parser`.
- Commit: `feat: add bounded strict UTF-8 transcript parser prerequisite`.
- Changed tracked files only:
- `backend/adapters/src/main/java/ama/transcript/TextTranscriptParser.java`
- `backend/adapters/src/test/java/ama/transcript/TextTranscriptParserTest.java`
- `backend/core/src/main/java/ama/transcript/TranscriptDocumentParser.java`
- `docs/acceptance/b/transcript-parser-prerequisite.md`

Worktree is clean after commit and `git diff BASE HEAD --check` passes. No merge or push.
No POM, contracts, SQL/migration, API/OpenAPI, worker, registry or shared runner changes.
Only this requested report was written outside the owned tree. The required source
brief/product supplement/B00 workdraft, processing-input and R5/R6/original B06 brief
were read. No credentials, environment files or `.tools` state were copied or modified.

## Exact internal API and data

`ama.transcript.TranscriptDocumentParser`:

```java
Result parse(InputStream source, UUID fileId, String expectedSourceSha256,
             ParseBudget budget, BooleanSupplier cancelled) throws ParseException;
record ParseBudget(long maxSourceBytes, long maxUtf16CodeUnits, int maxSegments) {}
record Result(List<Segment> segments, String sourceSha256, String parserProfileVersion) {}
record Segment(UUID stableId, UUID sourceFileId, long paragraphNo, String text,
               UUID speakerId, Long startMs, Long endMs) {}
```

Result defensively copies the immutable segment list. Nullable speaker/times are
always null. `paragraphNo` is the original 1-based physical line, retaining gaps
from skipped Java `String.isBlank()` lines. Text is never trimmed/normalized.
Caller stream stays open on every observed success/failure; no file location or
source bytes are mutated. Required argument nulls, nonpositive bounds and hashes
other than exact lowercase 64-hex fail `INVALID_ARGUMENT` before source I/O.

Safe typed errors: `INVALID_ARGUMENT`, `INVALID_UTF8`, `INVALID_TEXT`, `NO_TEXT`,
`SOURCE_HASH_MISMATCH`, `SOURCE_BYTES_EXCEEDED`, `UTF16_CODE_UNITS_EXCEEDED`,
`SEGMENTS_EXCEEDED`, `CANCELLED`, `SOURCE_READ_FAILED`. Exceptions contain the code
name only and no source IOException cause, document content or partial result.

## Profile and ID encoding

Only unwired `TextTranscriptParser`, profile `ama.strict-utf8-lines.v1`.
Strict UTF-8 `REPORT`; consume at most one initial BOM, preserve subsequent U+FEFF.
CRLF/CR/LF each count one physical delimiter. NUL is `INVALID_TEXT`; empty or
Java-whitespace-only text is `NO_TEXT`; NBSP is retained per `String.isBlank()`.
Speaker labels, timestamps, tags and markup remain literal text.

ID encoding: ordered UTF-8 strings profile version, canonical lowercase UUID string,
exact lowercase source SHA256 hex, unpadded decimal physical-line ordinal. Prefix
each field by four-byte unsigned big-endian byte length. SHA256 the concatenation,
take first 16 bytes, set byte6 high nibble to 8 and byte8 high two bits to binary10;
encode canonical UUID. This is a documented custom SHA256 version8 layout, not
RFC5 or MD5. Profile changes require new version; source/file/line changes alter
the derivation input. The normal 122-bit retained hash collision limit applies.

Independent Python hashlib/struct vector: UTF-8 `hello`, file
`00000000-0000-4000-8000-000000000001`, SHA
`2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824`, line1,
yields `3a5df925-ae7a-8b89-95c0-3a85c7d96a17`; Java asserts this literal.
The exact encoded bytes are saved in the allowlisted `id-vector.txt`.

## Digest, budget, cancellation and ownership evidence

Source SHA256 is calculated from original streamed bytes including BOM and original
line separators. Success is withheld until EOF and exact expected SHA match.
Wrong BOM/separators, truncated/malformed suffix and an IOException instead of EOF
all fail; no partial transcript is returned. A verified source digest is distinct
from the later business input fingerprint and confirmation hash.

All positive budgets are explicit technical caller policy, with no product quota,
name limit, global content cap or default. Source reads are clamped to remaining
budget plus one overflow sentinel. Observable single-byte and bulk streams both
read exactly2 bytes for a byte budget1 before typed failure, rather than retaining
or consuming an unbounded suffix. Decoded UTF-16 budget counts every source unit
including BOM/newlines (emoji counts2); segment count is checked before list append.
Exact/one-over tests use `中😀\nA`: 9 bytes, 5 UTF-16 units, 2 segments. BOM/CRLF
and minimal/all-max-positive budget cases are covered. No truncation success.

Memory is O(maxUtf16CodeUnits + maxSegments), plus fixed JDK decoder byte buffering
and a 1024-unit adapter char buffer. Builder capacity/transient line String and
immutable-list copy are constant-factor overhead; no whole raw source buffer,
readAllBytes or readAllLines. Callers must choose budgets suitable for their heap.
The fixed decoder may read ahead before a char/segment bound is discovered; the
byte budget plus one sentinel remains an absolute source-read bound.

Cancellation checks precede I/O, surround each actual source/buffer read, occur
per decoded unit and before success. Pre-read cancellation reads0 bytes; mid-read
signal stops before another source read. Preexisting thread interruption is
preserved; InterruptedIOException restores the flag and fails CANCELLED. Other
IOExceptions become safe SOURCE_READ_FAILED. Arbitrary already-blocked stream
reads require caller timeout/lifetime management; the signal alone cannot unblock
an arbitrary InputStream. Tests cover open stream ownership across success, hash,
decode, NUL, budget, no-text, cancel and read-error paths.

## TDD and actual verification scope

Skills used: test-driven-development (including writing-good-tests reference),
verification-before-completion. The task's explicit focused-unit boundary takes
precedence over the skill's generic aggregate-suite advice. No child agents.

Meaningful first RED: declared port plus explicitly unimplemented adapter skeleton,
15 real-stream tests. Maven compilation/setup succeeded; tests showed **15 tests,
10 assertion failures, 5 errors, 0 skipped**, all attributable to
UnsupportedOperationException from the missing parser. This was expected RED,
not an unexplained setup failure. The UTF-16 fixture count was corrected from6 to5
before implementing behavior; the independent vector was also added at that point.
The final code has no skeleton/stub. First GREEN: 15/0/0/0. Four further tests
characterize existing implementation across buffer boundaries, exact-EOF error,
interrupted I/O and long Unicode lines; byte read-ahead assertion was strengthened
for bulk reads. No production behavior was added after that initial GREEN.

Final selected result: **Tests run: 19, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS**.
Final run completed 2026-10-22T03:10:16+08:00 under pinned Java21.

Command from own tree via WSL --cd/--exec, sourced shared pinned runtime read-only:

```bash
source /home/tanren/ama/.worktrees/phase-a/.tools/env.sh
./mvnw -B -pl backend/adapters -am -Dtest=TextTranscriptParserTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Selected reactor: contracts/core/adapters compiled; only
`ama.transcript.TextTranscriptParserTest` executed. No existing StorageRouter
unit, API, H2/DB, IT, Docker, runtime network/server, web/Python aggregate or company
calls were executed by this selector. There were no unexpected test/setup failures.
Initial broad parent-directory AGENTS filename discovery emitted unrelated permission
denied entries; this was read-only discovery, not a build/test failure, and was not
repeated or used as evidence. No external documents/company integration were used.

## Production gates and remaining concerns

No production bean, capability activation, public upload/API surface, DB/schema,
B05 queue consumption, model/SSO/company call or runtime dependency was introduced.
DOCX/SRT/VTT/OCR/media and legacy encoding conversions remain unsupported profiles.
This parser has no filename/MIME/format argument and is not a detector. A future
profile dispatcher must explicitly reject unsupported format requests; valid UTF-8
markup may be parsed literally only when the caller deliberately selects plain
text. No extension is accepted as proof of a supported format. Production format/
size matrix and qualification remain pending. This is a deliberate profile boundary,
not implicit SRT/VTT/DOCX support.

Full B06 service, immutable `transcription_input`, V011, RAW/WORKING revisions,
confirmation, ACL, candidate handling, marks/content hash and business fingerprints
remain **OPEN**, including independent review and integration with reviewed B05.
No known unresolved defect in the scoped parser; known limits are caller-controlled
blocking read timeout, caller heap budget selection, hash collision limits and
unsupported format dispatch responsibility. Coordinator independent review pending.

## Exact raw evidence allowlist and SHA256

All raw artifacts are synthetic/parser/build evidence in the owned tree at
`artifacts/phase-b/b06-parser/`; this directory is Git-ignored. No environment dump,
credential, uploaded user document, provider data or secret state was retained.
`source-sha256.txt` lists final tracked-source hashes; `source.diff` is the exact
staged patch committed as HEAD. The manifest hashes the eleven evidence files;
its own hash is listed below rather than recursively inside itself.

| File | SHA256 |
|---|---|
| `red.log` | `c84134ecafc6eb28d2a1be2f84d80cb943c7cf51d210e37ae4ebf1d8600171a4` |
| `red.xml` | `4c3fc928d712547db10b7325a129f5b556ddae7321ec8371037aff7a1a6ae69a` |
| `green.log` | `e5cc26ab80d8dccdfb5d6325ccc49bb341ba087d7b95b368037bfa18f947ca11` |
| `green.xml` | `0290337a9da1f34543204eb2e257424abb69c50039ebe16c66d3664294922c8b` |
| `final-tests.log` | `6fa6a13c012e4e1c0c146c8c519b16e4df23529e7cb3377f1f822344ec8426ce` |
| `final-tests.xml` | `9a6a753047396c3ff089af16e7447c1bf5f1cda1570868b8026839d7b440d21e` |
| `id-vector.txt` | `861df745cafea063becdd91aca9c974cd276a4b98d7b936d9c1838cc0dea7fd1` |
| `source.diff` | `eba6a58a08c91c3686a3123ee70c91939e8c152c5e8a7fe5cd03030ff18d82ef` |
| `source-sha256.txt` | `4d04ee42ca9b4c06c013e6567e16a13ae883b4d419de8e17dd93b28f1ed90b3e` |
| `runtime.txt` | `30aac07b24f5d0def7d7e11d787c55948d4898c4c73585a99c98777d3361d2d2` |
| `verification.txt` | `56b3332adebd116aac401d8211564214dc6991113d597c3fd1059ac9bbd2a422` |
| `allowlist-sha256.txt` | `59f61876e08e70328b2c9975b5c041bb307a478040508a34b27d82880eb8981a` |

Final tracked-source SHA256s:

```text
cfb4c1d8e31a86367f82c7e02148321ff8588576654937fe12aaf27a587b10f3  backend/core/src/main/java/ama/transcript/TranscriptDocumentParser.java
497c1bfbbab55554b3fc8dc26c4b4f0031cba0ecae35ecd9979a5bb7394ac04a  backend/adapters/src/main/java/ama/transcript/TextTranscriptParser.java
7f1dfe861b7773fa3a98c9d13e9f71a79415604c0cb116dd23c9c2f9ded93f98  backend/adapters/src/test/java/ama/transcript/TextTranscriptParserTest.java
01a9015ab6965b0df7eb7c01dfa165594bb2b32e5cc151cb700cbcc811e2c4bd  docs/acceptance/b/transcript-parser-prerequisite.md
```
