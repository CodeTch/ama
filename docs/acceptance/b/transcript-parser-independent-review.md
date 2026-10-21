# B-06 parser prerequisite independent review

## Spec Compliance

- ✅ Spec compliant for the pure strict-UTF-8 parser prerequisite at BASE `c370aa21181afd8d6255e2e29ec7da496eb42b97` → HEAD `9ae628058d53ddaff64e14adeeecb64ded653c1c`. This approves the scoped parser; full B-06 and production activation remain open.
- Read the complete brief, complete implementation report and full four-path/598-insertion diff. No truncated hunk required a separate source read. Later patch access checked byte equality and extracted line anchors; current sources were read as bytes only for SHA256 validation. No broad source crawl.
- Interface and immutability match: `backend/core/src/main/java/ama/transcript/TranscriptDocumentParser.java:16` accepts caller-owned stream, UUID, exact expected SHA, explicit budget and cancellation supplier; `:23`–`:30` provides immutable records and defensive List.copyOf. Adapter `backend/adapters/src/main/java/ama/transcript/TextTranscriptParser.java:37` deliberately leaves its reader/source open.
- Strict decoding and provenance match: adapter `:35`–`:36` configures UTF-8 REPORT; `:108`–`:122` counts units before retention, consumes only the initial BOM, rejects NUL, handles CRLF/CR/LF and advances physical line ordinals through blank lines. `:125`–`:132` uses String.isBlank, preserves untrimmed text and emits null speaker/time fields. `TextTranscriptParserTest.java:33`, `:75`, `:166`, `:187`, `:221` cover Unicode/emoji, whitespace, NBSP, later BOM, blank ordinal gaps and buffer boundaries. Labels and time tags stay literal.
- Full digest matches: adapter `:42`–`:48` reaches EOF before success; `:62`–`:67` verifies actual streamed digest; `:166` hashes original bytes including BOM/separators. Expected SHA is used in private provisional IDs, but segments cannot escape before verification. Test `:58`, `:68`, `:156`, `:195` covers malformed/truncated suffix, changed BOM/separators, observed EOF and IOException instead of EOF. Adapter `:66` gives explicit NO_TEXT (test `:75`); `:116` gives explicit INVALID_TEXT for NUL (test `:83`).
- Stable IDs match: adapter `:80`–`:92` hashes length-prefixed UTF-8 profile/file/hash/physical-ordinal fields and sets custom UUIDv8/IETF variant bits. `docs/acceptance/b/transcript-parser-prerequisite.md:61`–`:82` documents field order, unsigned big-endian lengths, decimal ordinal, bit layout, collision limits and versioning. Test `:46` pins the independent literal vector, repetition and file/source differences; test `:187` distinguishes identical text at different physical ordinals. Independent artifact audit reconstructed the documented encoding and UUID and matched saved id-vector.txt: `3a5df925-ae7a-8b89-95c0-3a85c7d96a17`.
- Budgets match: adapter `:26`–`:31` validates positive limits/nulls/exact lowercase64hex before I/O. `:158`–`:167` caps byte reads to remaining plus one sentinel. The cast/addition occurs only when remaining is smaller than the positive int request length, avoiding overflow for decoder calls. `:110` checks UTF16 units before incrementing; `:128` checks segment count before append. It avoids maxSourceBytes+1 arithmetic even for Long.MAX_VALUE; emitted physical ordinals remain bounded by source units. Tests `:87`, `:100` cover minimal/all-max-positive, exact/one-over byte/unit/segment bounds, emoji units, BOM/newlines and observable bulk/single-byte overflow reads. No truncation success.
- Cancellation/safe errors match: adapter `:32`, `:44`–`:46`, `:61`, `:65`, `:70`–`:73`, `:109`, `:153`–`:162` check before/around I/O, per unit and before success. `:50`–`:59` translates failures without source causes and restores interruption for InterruptedIOException. Port `:37`–`:46` exposes only code/name diagnostics. Tests `:111`, `:118`, `:130`, `:140`, `:208` and failure helper `:24` cover timing, interruption, safe details and caller stream ownership.
- Scope matches all four owned files: core port, adapter, focused test and scoped acceptance document. The complete patch adds no POM/runtime dependency, SQL/table/migration, API/OpenAPI, worker, bean/registry, shared runner or B05 consumer changes. Acceptance document `:3`–`:5`, `:46`–`:52`, `:114`–`:119` retains production format/size and remaining B06 integration gates.
- ⚠️ Future dispatcher rejection of unsupported format requests, production budgets, service/immutable processing input/ACL/revision/confirmation/candidate/hash integration and B05 handoff remain outside this diff and unverified/open. The profile deliberately has no format/filename argument. UTF-8 markup parsed literally is not DOCX/SRT/VTT/media support or format approval.

## Strengths

- Small framework-free port and separate byte/digest bound wrapper versus line assembly: adapter `:138`–`:176`, `:95`–`:134`; port `:9`–`:30`. The implementation is understandable within four files and adds no runtime dependency.
- Real-stream tests assert data, reads, EOF, errors and ownership rather than mock parser internals: test `:24`–`:31`, `:100`–`:108`, `:195`–`:205`. The independent vector at `:52` avoids testing ID derivation only against itself.
- Resource limits and caller responsibilities are candid: acceptance document `:86`–`:110` explains fixed decoder read-ahead, constant-factor builder/list overhead, suitable caller heap budgets and blocking-read timeout/lifetime ownership.

## Issues

### Critical (Must Fix)

None found in the scoped implementation or evidence.

### Important (Should Fix)

None found in the scoped implementation or evidence.

### Minor (Nice to Have)

None requiring action at this task gate. No unrelated coverage expansion requested.

## Evidence audit and exact checks

Evidence paths below are relative to `/home/tanren/ama/.worktrees/phase-b-transcript-parser/artifacts/phase-b/b06-parser/`.

- Recomputed SHA256 for all eleven allowlisted files; every value matches both author report and `allowlist-sha256.txt:1`–`:11`: red.log, red.xml, green.log, green.xml, final-tests.log, final-tests.xml, id-vector.txt, source.diff, source-sha256.txt, runtime.txt, verification.txt. Manifest hash matches report: `59f61876e08e70328b2c9975b5c041bb307a478040508a34b27d82880eb8981a`. Evidence directory contains exactly those eleven files plus manifest.
- Recomputed the four current source SHA256s: all match `source-sha256.txt:1`–`:4` and author report. Saved source.diff is byte-for-byte equal to the full patch after the review package's `## Diff` header. This binds source/evidence to the reviewed patch without repeating git operations.
- `red.xml:2`: 15 tests, 10 failures, 5 errors, 0 skipped. All ten assertion failures contain cause `UnsupportedOperationException: Parser not implemented`; all five errors are the same unimplemented parser. `red.log:24`–`:85` shows successful compile/setup through JUnit execution; `:86`, `:274`, `:284` record expected RED. These are genuine missing-behavior RED failures/errors, not setup errors or fake-green counts.
- `green.xml:2`: 15/0/0/0, matching `green.log:84`, `:98`. Final `final-tests.xml:2`: 19/0/0/0, matching `final-tests.log:83`, `:97`. These are successive cohorts: final scope is 19 tests, not 49 summed across runs. Four extra final tests characterize delimiter boundaries, missing-EOF read failure, interrupted I/O and long Unicode lines.
- XML `red.xml:17`, `green.xml:17`, `final-tests.xml:17` records selector TextTranscriptParserTest; `:40` records surefire.failIfNoSpecifiedTests=false; `:46` records Java21.0.12.1, consistent with `runtime.txt:1`–`:3`. Logs confirm contracts/core/adapters reactor compilation and only parser test execution (`final-tests.log:5`–`:8`, `:33`–`:55`, `:82`). No API/IT/H2/StorageRouter/runtime DB/Docker execution appears in this selector; aggregate validation is not claimed. The reported Maven command is consistent with these saved properties/reactor logs; logs are Maven output rather than a separately recorded shell command transcript.
- Saved GREEN/final logs contain no unexpected warnings/failures; XML has no unexpected stdout/stderr. Initial RED errors are explained above. The report separately discloses unrelated initial permission-denied filename discovery, not represented as build/test evidence.
- Review ran no tests, Maven builds, DB, Docker, runtime network/server or company calls. No git commands/mutations, source/index/HEAD/branch changes, source plans/designDocs edits, child agents or broad crawl. This review report is the sole successful write. A failed first report-write attempt used an invalid Windows UNC path and created no file; the report was then saved via its exact WSL path. Historical command execution/cleanliness claims beyond saved evidence were not independently replayed.

## Assessment

**Task quality: Approved.**

The parser meets scoped strict-decoding, provenance, deterministic-ID, bounded-retention, full-digest, cancellation and ownership contracts, supported by meaningful saved real-stream evidence. Production format/size approval and the remaining B06 service/integration remain open.
