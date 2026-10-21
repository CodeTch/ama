# B04 S3 infrastructure implementation — independent SPEC and code-quality review

## Spec Compliance

**❌ Issues found; task quality: Needs fixes.** One Important defect remains in malformed range-response handling: nullable SDK Content-Length is unboxed after acquiring a live response without a failure-cleanup guard, so a missing header can escape as NullPointerException and retain request admission. See I1. The reviewed immutable protocol, selected-manifest semantics, v3 layout, recovery architecture and bounded ordinary I/O otherwise match this scoped delivery.

**Reviewed identity:** BASE `0daecad85d348305c11982b5f0a7e2536a47434d` → HEAD `9a8b79dfb4eb1ade5a18d7757699a40b3ed9f0fb`; 33 paths, 4,151 additions, 6 deletions. I read the complete supplied 4,550-line review diff in bounded chunks, with no changed file skipped. New-file hunks were complete; no changed source file needed a separate content read. Source hash reads and diff-header/blob identity checks were mechanical verification, not a second source crawl. No Git command, test, compilation, DB, Docker, service or provider operation was run. This report is the only write.

References below are relative to `/home/tanren/ama/.worktrees/phase-b-storage-s3`. `main/` abbreviates `backend/adapters/src/main/java/ama/storage/s3/`; `it/` abbreviates `backend/integration-tests/src/test/java/ama/storage/s3/`; `evidence/` abbreviates `artifacts/phase-b/b04-s3/`.

Binding inputs read: task implementation brief, original B04 brief, storage-preflight, full author implementation report, task-reviewer-prompt template, approved full infrastructure design and v3 layout addendum. Independently calculated design SHA-256 values match `3bb475ce826a739ae6c1f01246084e16fdec76dcf9038a387a79bcb7351904f5` and `065fd8b7f4852c8d395352d83083a67ad800523bd9ca5fcf1c1fb5db8f8c702b`. V3 supersedes physical layout only.

| Scoped requirement | Review result and source evidence |
| --- | --- |
| Nine public port methods unchanged; DB-free explicit adapter | Compliant: `main/S3FileStorage.java:22`, `:65`, `:130` implement the existing signatures; only additional adapter-specific resume helper at `:119`. No contract/schema/API/production bean change among all 33 paths. Dependency and native executable checks below corroborate isolation. |
| Actual caller bytes before publication; immutable control/data | Compliant: `main/Staging.java:51` consumes and checks bounded chunks and whole actual size/hash; `:113` independently GET-verifies chunks. `main/S3Objects.java:127` creates controls conditionally and reconciles exact fixed keys. `main/NativePublisher.java:47`, `:66`, `:126` verify source and use conditional final PUT/CompleteMPU. |
| Selected trusted receipts, original replay receipt, one terminal decision | Compliant: `main/SessionJournal.java:124` validates accepted winner and terminal postcheck; `:170` validates only supplied contiguous receipts and actual whole content before terminal creation; `:205` keeps wrong whole hash OPEN. `:326` abort competes on the same terminal and preserves existing COMPLETE. No unselected physical-part scan or rejection of empty logical parts. |
| Durable recovery, independent native IDs, no guessed abort | Compliant: `main/SessionJournal.java:235` reconstructs frozen request from durable pages; `main/NativePublisher.java:71` journals unique helper intent and `:92` known ID before UploadPart. Unknown Create has no list-diff attribution, automatic abort or TTL. Final result at `main/SessionJournal.java:270` binds terminal and actual result. |
| Actual final identity/hash/size; same-content other-session success | Compliant within supported immutable-writer assumptions: `main/NativePublisher.java:31` hashes actual final bytes and compares size/hash, independent of session provenance; publication reconciles full final content after normal and uncertain outcomes. |
| At most 10,000 legal native parts; bounded replay/memory | Compliant for configured profile: `main/NativePublisher.java:21` overflow-safe interval planning and `:94` native part numbering; `main/PublicationSource.java:31` retains at most native-interval positions, not all logical parts. `main/Staging.java:136` seeks directly to a chunk after full verification. Native extrema remain planner evidence, not huge-object executions. |
| V3 exact scope/key identity, canonical shape, representability | Compliant: `main/S3Layout.java:16`, `:58`, `:83`, `:99` implement exact code-unit escapes, deterministic 199-character segmentation, total/segment/depth checks and scope binding. `main/S3Objects.java:146`, `:157` compare full immutable bindings. `main/S3FileStorage.java:87` refuses unbound deletion. No hash overflow fallback or new product filename bound. |
| One scoped listing page, order and cursor validation | Compliant: `main/S3FileStorage.java:130` binds config/backend/namespace/H/prefix/cursor and constructs Prefix independently; `:164` sends one ListObjectsV2 request with bounded MaxKeys; returned entries are canonical/order checked and only that page is stat-hashed. Confirmed deleted entries may yield an advancing short/empty page. |
| HEAD/version/If-Match exact range, no full-hash drain | Core path compliant, exception cleanup defect I1: `main/RangeReader.java:20` checks numeric bounds, HEAD, exact status/range/length/version/ETag, and aborts on caller close. No stat/full-object fallback. Real recorded payload metrics support cancellation bounds for tested responses; missing Content-Length is uncovered. |
| Admission two with nested native staging I/O | Compliant ordinary path: `main/S3Objects.java:44` obtains native streaming gate before shared request permit; maximum native bodies admission−1 leaves a staging slot. Admission-one rejection and real two-request HTTP peak are evidenced. I1 separately leaks permits on malformed responses. |
| Explicit runtime; no production activation/fallback | Compliant scoped construction: `main/S3FileStorage.java:30` injects credentials/region/endpoint, explicitly disables system/environment proxies and SDK retries, and uses official URLConnection transport. HTTP fixture support exists; actual production TLS policy and provider credentials remain integration gates. Unknown test selector rejects at `backend/integration-tests/src/test/java/ama/storage/ContractFixture.java:14`. |

**⚠️ Cannot verify / not accepted by this scoped review:** full business B04 registry/ACL/purpose/MySQL registration and rollback behavior, default-backend governance, actual production key-generator grammar, production TLS/writer permissions/durable ACK, power loss/RHEL/XFS/HA/fencing, sustained capacity and company integration. They are intentionally outside this adapter delivery and remain OPEN. Actual process-kill proof covers terminal, known-upload and result boundaries, not every instruction/durable boundary in the broader design. Other recorded boundaries use after-commit response loss. The available new suites do not establish a separate wrong-credential/AccessDenied or missing-bucket qualification; the mapping is inspected, not promoted to executed evidence. The no-arbitrary-external-overwrite and no-permanent-delete-fence lifetime assumptions remain material.

## Strengths

- The protocol has one clear terminal authority, immutable accepted slots, selected-request fingerprinting and exact-record reconciliation. `main/SessionJournal.java:78`, `:124`, `:170`, `:235`, `:326` preserve complete/abort and late-writer semantics without DB locks, process mutexes or mutable CAS. Tests use actual provider operations and independent JVMs (`it/S3ReliabilityIT.java:219`, `it/S3NativeRaceIT.java:17`, `it/S3RecoveryIT.java:65`).
- The decomposition is useful rather than ceremonial: ten production classes divide encoding, physical identity, transport/admission, staging, selected manifests, source indexing, publication and range ownership. No new giant facade; the largest production unit is the 341-line journal. `main/S3FileStorage.java:22`, `main/PublicationSource.java:15`, `main/RangeReader.java:11` expose the responsibilities clearly.
- V3 implements the approved physical representation precisely, including escaped characters crossing a physical segment and exact UTF-16/surrogate identity. Full equality checks protect against scope/key hash aliasing. Pure and HTTP boundary tests address the demonstrated provider limitation instead of weakening logical key semantics (`backend/adapters/src/test/java/ama/storage/s3/S3LayoutV3Test.java:18`, `it/S3LayoutV3IT.java:43`, `:99`, `:169`).
- Native body replay and admission changes respond to preserved genuine failures, with recorded byte amplification and concurrent real HTTP measurements. The interval index removes replay-from-start growth; the native streaming gate fixes the self-starvation case without increasing total request admission (`main/PublicationSource.java:31`, `main/S3Objects.java:44`, `it/S3ReplayBudgetIT.java:15`, `it/S3AdmissionIT.java:21`).
- Evidence custody is unusually explicit: failed attempts, historical/final source identities, actual runtime classpaths, native packaging, raw wire metrics and frozen expensive runs are retained separately. There is no false claim that v2 or frozen 10001 classes are the final classes, or that local RustFS results qualify production (`docs/acceptance/b/s3-file-infrastructure.md:43`, `:55`).

## Issues

### Critical (Must Fix)

None found within the reviewed scope and supported-writer/lifetime assumptions.

### Important (Should Fix)

**I1 — Missing Content-Length can leak a range response and exhaust request admission.**

- Location: `main/RangeReader.java:50`–`:59`, specifically nullable `response.contentLength()` at `:53`; related unchecked HEAD unboxing at `:33`. `main/S3Objects.java:74` acquires the shared permit and returns a live SDK stream; `:93` is the explicit abort/release path.
- The SDK 2.56.0 `GetObjectResponse.contentLength()` and `HeadObjectResponse.contentLength()` return `java.lang.Long`, directly from nullable fields. With a 206 response whose Content-Length is absent, the comparison unboxes null before entering the rejection block. The wrapper owning close is never returned, and neither `io.release(in)` nor a finally runs. This yields an unsanitized NullPointerException and loses a permit/live response; repeated malformed responses can render that adapter instance unavailable even after the provider recovers. Missing HEAD length also escapes the typed error boundary, though `io.call` has already released its request permit.
- The same nullable length assumption appears at `main/S3Objects.java:104`, `:182` and `main/Staging.java:122`. Those methods have finally-based release, so they do not share this permit leak, but can still emit an untyped NPE. Treat this as one metadata-validation/error-ownership defect, not several unrelated findings.
- Fix: explicitly reject missing/invalid structural lengths as sanitized STORAGE_UNAVAILABLE. Protect all post-raw header validation with guaranteed abort/release until stream ownership transfers to the returned wrapper. Preserve bounded abort and exactly-once permit release. Do not substitute full stat, inferred metadata size or a whole-object read.
- Focused validation recommended for the author: a real proxy response removing Content-Length (close-delimited or correctly chunked HTTP framing) on otherwise valid range 206, plus missing HEAD length; require STORAGE_UNAVAILABLE, prompt bounded abort, and a successful healthy request after more malformed exchanges than admission. Existing `it/S3ReliabilityIT.java:106` covers wrong range, ignored 200, overlong declared length and truncation, but not a missing length. No new provider container, expensive 10001 run or broad suite rerun is required to establish this fix.
- Verification basis: static control flow plus one narrowly scoped outside-diff SDK type check using the pinned local 2.56.0 JAR and pinned javap. No runtime reproduction was performed in this review; the current evidence has no matching missing-header case.

### Minor (Nice to Have)

**M1 — Test capture has quadratic append cost.** `it/WireProxy.java:37` uses CopyOnWriteArrayList for an append-heavy event log. The v3 10001 capture contains 330,053 events, so each append copies the accumulated array. This is already disclosed and does not invalidate functional byte accounting, but materially inflates heap/GC and suite time. Use a synchronized append list with snapshot-at-settle, or another append-oriented collection, for future runs. Do not reinterpret its cost as product performance or rerun the frozen group merely for this instrumentation improvement.

**M2 — Deprecated retry API noise remains in final compilation output.** `main/S3FileStorage.java:53` and `it/S3Fixture.java:80` use `RetryPolicy.none()` through the deprecated retry-policy API. Final canonical unit and selected IT build logs explicitly report deprecated API usage. Preserve one-attempt semantics while adopting the SDK's supported configuration API, or document a narrowly justified suppression; the current runtime behavior is not itself incorrect. This is the reported build-noise finding, not a failing-test claim.

**M3 — Fixture ownership validator checks fewer limits than the report claims.** `tools/dev/s3_fixture.py:11`–`:18` verifies owner, image, memory, port and mounts, but not configured CPU quota, memory-swap bound or PID limit before restart/stop. Those are set at `:38`; the report describes checking all limits. Extend the read-only assertions to those fields (and keep exact ID/owner checks) or narrow the prose. The sealed lifecycle evidence shows the intended owned fixture was used; this is a future drift-detection/documentation gap, not evidence of a foreign-container operation.

## Evidence verification

All checks below were read-only parsing/hashing/inspection of the allowlisted artifacts. No env, credential or provider-data file was opened or copied. No large raw log was dumped. An initial javap lookup found no executable on PATH; the pinned java.home from an existing XML supplied the correct executable. Two mechanical evidence-path assumptions were corrected from the existing manifests (mixed production/test class directories; canonical wire subdirectory), without regenerating evidence.

### Source and sealed artifact custody

- `review-candidate-evidence-manifest.json`: independently verified **all 353 allowlisted file SHA-256 values and sizes**, zero mismatch; seal SHA `59536dde036a46a1d3df3d4866697fa7d4d776b2d292e73cb978cffa66571454`.
- All **33 owned source files** match their sealed manifest, source archive members and canonical source manifest. Independently calculated Git blob hashes match all 33 new blob IDs in the supplied committed diff, without invoking Git. `commit-source-attestation.json` separately names the exact HEAD/BASE and records clean worktree; cleanliness is its attestation, not a fresh Git status claim by this reviewer.
- Source archive SHA `40e1ea84723f3fad9e6f55c23e8fa70303246d8feaf1c0d28d5e8dcfb879d593`; per-file source manifest SHA `1d4c4ab3f39e8acc83ac231d0ae95dfec4909078ffbd0a81a6430b604d9ba5e2`.
- All **241 canonical source-copy files** match `final-canonical-source-manifest.json`, SHA `0232a09fd8ee1419336fb5c85884901d0fcadcf0d1f03b8addee503a6caab673`.
- `compiled-cohort-identities.json`, SHA `2d2d3c29d3bf9cd507de79a138b19e973b66c3e7c0492587214d92ca4958c17b`: all recorded final canonical class hashes and cohort class hashes match actual existing class files. Historical cohorts contain 16 v2 frozen classes, 33 v3-10001 classes, 22 restart classes and 37 admission-green classes (including test/helper classes as recorded).
- Reviewed the sealed normalized comparison and all three exact javap delta records: 17/20 production classes have matching normalized instructions; NativePublisher changes only the UploadPart call to streaming admission, S3Objects adds the gate, and S3FileStorage changes missing-binding delete. These changes do not enter the frozen 10001 test's one-small-PUT/no-delete path. The final native admission paths have their separate final regressions. This is an impact judgment supported by actual class hashes and exact deltas, not a claim that final bytecode is identical or that the expensive run used final classes. I did not regenerate the full normalized comparison.
- Inspected current `.tools/v3_focus.py` command construction: isolated directory prepended to historical classpath, Java `-Xmx384m`, and JUnit console 6.0.3. Current helper SHA `d273dda33cc6a1484eb72d1c5a640ad029e64fbe34b6f146b4fdb225b8610669`. This helper is outside the immutable source archive; frozen class hashes and retained cohort records, not its current text alone, establish the recorded run identity.
- The sealed redaction attestation records 379 scanned source/evidence files, six actual fixture credential values checked, zero matching files. I verified the sealed attestation, not independently reloaded secrets to repeat that scan. Its 379-file scan population is distinct from the 353-file evidence seal.

### Runtime and packaging

- Final runtime graph SHA `7557f7741a081c48cd5fdcd8657d2240a2abdfb2cd01dc63b549105b83de8751` records successful dependency-plugin 3.10.0 resolution and explicit S3/URLConnection 2.56.0 dependencies.
- Independently recomputed the final canonical **137-entry classpath** string SHA `ff767d6c2187eb1919e93d1eee4b696dd60d751bd5aa53579123c86a64b0d82f`; classpath JSON SHA `956fef119241b770366e3357ae8cd24889e190f57705310e6bc9b6647b8e2cc1`. No Apache/Apache5/Netty transport entry.
- Historical focus classpath SHA matches `02ec3c0ee5889f82f4424ac82e89fa7fc89e64210344d5a0bd219f2a144e8515` and does contain unused Apache5. Historical focus runs therefore do **not** prove runtime dependency exclusion; the final canonical graph/classpath do.
- Opened the final native executable as ZIP, verified its whole-JAR SHA `594f3834f086ec333f4a52ceffe6c0cd7cc7cda57a30b7a9256cbaab5d9a4517`, and independently matched exactly **8 BOOT-INF library JARs** to the sealed manifest, SHA `058204d82ab1e1c58839147d29f6806e2416cf8675d18e7df6f243a9b2a117d0`. No AWS/JDBC/H2/MySQL/Apache/Netty library. Directory entries are not counted as JARs.

### Test cohorts and retained failures

Parsed every suite in **39 inventory groups**, independently checked XML SHA values and tests/failures/errors/skips against the inventory, with zero mismatch. Selected groups below overlap; these counts must not be summed as a unique test total.

| Recorded group | Tests / failures / errors / skipped |
| --- | --- |
| Final canonical units after admission change | 38 / 0 / 0 / 0 |
| Canonical shared local contract | 7 / 0 / 0 / 0 |
| Final canonical shared S3 contract | 7 / 0 / 0 / 0 |
| Final canonical related S3 regression | 23 / 0 / 0 / 0 |
| Final canonical added overlong range case | 1 / 0 / 0 / 0 |
| Canonical reliability / multipart boundaries | 10 / 0 / 0 / 0; 5 / 0 / 0 / 0 |
| Final shared common + layout after delete fix | 11 / 0 / 0 / 0 |
| V3 native races / integrity / selector | 12 / 0 / 0 / 0; 4 / 0 / 0 / 0; 1 / 0 / 0 / 0 |
| V3 replay-green / admission-green | 6 / 0 / 0 / 0; 5 / 0 / 0 / 0 |
| Frozen v3 10001 logical parts | 1 / 0 / 0 / 0 |
| Frozen historical v2 multipart | 7 / 0 / 0 / 0 |

The v3 10001 log states **2,358,096 ms = 2,358.096 seconds** for the full console run; its Jupiter suite XML reports a narrower 2,347.901-second suite duration. These are distinct measurements. The preserved v3 wire JSON contains 330,053 exchanges, exactly one conditional final PUT and zero native UploadPart calls. This supports logical/native separation, not 10,001 native parts or production throughput. No repeat of that roughly 40-minute run is warranted for the unrelated I1 fix.

Preserved nonpassing cohorts were counted as failures, not silently replaced: initial shared RED 7 tests/2 failures/5 errors; v3 replay RED 1/1 failure; admission RED 1/1 failure; layout RED 2/2 failures; delete-binding RED 1/error; behavioral selector RED 1/failure. Inventory also retains the two 7-error multipart connection-refused attempts, key-budget diagnostic, old list/reliability diagnostics, initial v3 layout/list oracle failures, pre-correction protocol 35/2 failures and selector harness failure. These establish recorded history, with the author's distinctions between implementation RED, test-oracle/harness error and provider outage preserved. Deprecated-API notices are reported in M2 rather than claimed pristine output.

### Actual I/O and restart evidence

Independently recomputed all **13 final canonical range metric records** from raw allowlisted JSON: hashes, all-request counts, control-body byte totals and final request method/range/status/upstream/downstream counts all match the metric summary.

- Final regression: 16 MiB object, three fully consumed one-byte GETs transfer exactly 3 final payload bytes; zero/invalid ranges issue no final GET. HEAD has no payload.
- 16 MiB early close: 67,357 upstream / 64,285 delivered bytes. Ignored 200 and wrong Content-Range: 15,517 / 14,069 each. Truncated 100-byte interval: 50 / 50, with test requiring failure.
- 64 MiB: middle one-byte read transfers 1; early close 15,516 / 14,068. Added overlong Content-Length case: 15,517 / 14,069. Recorded status is upstream status; proxy client-facing mutation is separately evident in test code. These are observed fixed-window results below the tested 1 MiB allowance, not a proof for all transports or arbitrary load.
- Preserved replay RED: 16 MiB 75,497,472 staging bytes (4.5x); 64 MiB 587,202,560 (8.75x). GREEN: 64 MiB 293,601,280 (4.375x); 16 MiB remains 4.5x. Final object hashing is accounted separately. Actual native UploadPart wire lengths include SDK framing and are not misreported as decoded object-part lengths.
- Final canonical admission log explicitly records configured admission 2 / observed peak upstream HTTP 2. Raw admission capture contains four successful UploadParts across two publications. This supports the gate fix; it does not cover the independent missing-header leak.
- Restart lifecycle record preserves identical exact owned container ID/image/owner and fixed loopback port 44789 across orderly restart, with distinct provider PIDs/start times. Write/read probe logs acknowledge five v3 objects and fresh-JVM full-byte/hash/range/list/token-only resume/delete checks. The source probe itself is reviewed at `it/S3RestartProbe.java:30`. Lifecycle metadata's unsigned readiness false does not negate the subsequent successful signed SDK read probe. No power-loss claim is inferred.
- Original v2 deep-key/provider exit139 and OOMKilled=false failure evidence remains sealed. Its cause is unresolved; v3 shallow success neither rewrites that history nor qualifies production RustFS.

## Assessment

**Task quality: Needs fixes.** The architecture and substantial real test evidence support the scoped immutable S3 implementation, but the explicit malformed-response/cancellation contract requires I1 to be fixed before this gate passes. The author should add the focused missing-header fault and cleanup regression, then preserve refreshed source/evidence identities; broader production and business B04 gates remain independent and OPEN.
