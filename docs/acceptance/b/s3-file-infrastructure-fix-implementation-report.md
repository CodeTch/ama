# B04 S3 fix round 1 — ready for scoped independent re-review

FIX_BASE **9a8b79dfb4eb1ade5a18d7757699a40b3ed9f0fb** → HEAD **ff7b6efbd3c8d47ca018e57f2e7b8eb633894335**, branch `codex/phase-b-storage-s3`, worktree `/home/tanren/ama/.worktrees/phase-b-storage-s3`. Commit: `Fix S3 response ownership and tighten test fixture safeguards`. The exact delta is20paths,536insertions,120deletions; worktree is clean. Original full-owned review BASE remains0daecad85, but this is the requested scoped fix delta, not a replacement for the original review.

I read the complete independent review and verified I1 against both code and actual SDK/HTTP behavior before changing production code. All four findings are addressed by author verification; root's fresh scoped review is still required. No SQL, public API/contract, business DB operation, dependency declaration, provider switch, production activation, main merge, push or cleanup change. No subagents. Original implementation report and its raw seal were not rewritten.

## Finding dispositions and change boundaries

**I1 — nullable structural length and response ownership.** `S3Objects.contentLength(Long)` rejects absent/negative lengths as sanitized STORAGE_UNAVAILABLE. HEAD, control GET, actual stat and staged-chunk GET use it; stat rejects invalid structural metadata before consuming body bytes. `RangeReader` keeps cleanup ownership after `raw` while validating every response header and constructing the wrapper. Its finally aborts/releases unless ownership has transferred to the returned wrapper. The rejection branch no longer separately releases, so there is one release authority before transfer; the existing wrapper's idempotent close owns release afterward. Runtime header-validation failure is sanitized. No size inference, full stat, full-object drain or alternate read fallback was introduced.

`S3MissingLengthIT` exercises missing HEAD length, a valid close-delimited206 and a correctly chunked206. The final test first establishes with the raw pinned SDK that each framing produces a genuinely null length, and that both GET framings decode the expected three bytes successfully. This distinguishes missing metadata from malformed chunk syntax. For each mode, the same adapter then receives four malformed exchanges with admission2, requires typed STORAGE_UNAVAILABLE, and subsequently performs a healthy one-byte read. Separate4MiB requested ranges verify prompt bounded abort for both GET framings. Control/stat/staging cases require typed errors and a healthy subsequent GET on the same transport. No production fault-injection hook was added.

**M1 — append-heavy capture.** `WireProxy` now holds a private synchronized ArrayList, providing amortizedO(1) append. Accepted-request accounting, completed-event insertion and reset/snapshot decisions share the same lock. Snapshot/serialization use an immutable completed-event list copied only at a settled boundary; all callers now use `snapshot()`/`reset()` instead of iterating or clearing a concurrently mutated collection. A real-provider concurrency test records four concurrent stat calls as12exchanges, retains an unchanged12-event snapshot after the next call grows the log to15, rejects list mutation, then verifies reset and three subsequent fresh exchanges. Existing fixture tests compile against the new access boundary. Historical COW cost/time/GC evidence remains unchanged; no new10001test or performance extrapolation was made.

**M2 — supported retry API.** Production `S3FileStorage`, fixture admin clients and the admission-barrier test client consistently use `StandardRetryStrategy.builder().maxAttempts(1).build()` through `retryStrategy`. No suppression is needed. Primary local pinnedSDK2.56.0 evidence is `sdk-2.56.0-retry-api.txt`: ClientOverrideConfiguration.Builder exposes retryStrategy, StandardRetryStrategy implements RetryStrategy, whose builder exposes maxAttempts(int) and whose instance exposes maxAttempts(). Exact inspected JAR SHA values are in `sdk-retry-artifact-identities.json`. Credentials, endpoints, region/addressing, proxy disabling and timeouts are unchanged.

`S3RetryIT` checks actual after-commit response loss for adapter and fixture writes. Each faulted mutation returned upstream200 and appears exactly once at its exact wire path. The adapter reconciles the actual object; the raw fixture client throws after one attempt while a direct read proves the mutation committed. Existing unknown-Create/lost-part/lost-control/native-completion behavior is also covered below. The final sealed native-complete drop and embedded200error captures each contain exactly one CompleteMultipartUpload POST (query contains uploadId), independently checked from raw JSON. Final canonical compile logs contain no deprecated-API notice, warning or error. This is a configuration migration retaining prior semantics, not a claim that old behavior was a failing retry test.

**M3 — read-only ownership limits.** The helper now additionally checks full inspected ID equality, NanoCpus=2000000000, MemorySwap=2147483648 and PidsLimit=256; existing owner/image/memory/loopbackport/mount checks remain. Six deterministic offline unittest cases execute the real guard against controlled inspect responses, including13new drift subcases and existing guard regressions. The dependency mock substitutes Docker inspect output only; no test issues Docker mutations. One actual read-only inspect of the same owned44789fixture passed all guards. `owned-fixture-limits.json` records exact ID `54dc7cc4db251d2942c70f40834716d76b83d4c85ee778ef8a5fda1074273c9a`, original pinned image/owner,2CPU,2GiB memory/2GiB memory+swap,256PIDs and unchanged mounts/loopback endpoint. No additional restart or stop occurred.

Production changes are limited to RangeReader, S3Objects, Staging and S3FileStorage. S3Fixture/S3AdmissionIT change retry construction. WireProxy and mechanical snapshot callers change test capture. Three new IT classes are S3MissingLengthIT, S3RetryIT and WireProxyCaptureIT; `tools/dev/tests/test_s3_fixture.py` is the new offline guard suite. The acceptance document now explicitly labels the prior bytecode/cohort comparison as historical9a8b79d and describes this fix without claiming production acceptance. `source-manifest.json` lists all20exact changed paths and SHA values.

## RED, correction and fresh GREEN evidence

All new evidence is under `/home/tanren/ama/.worktrees/phase-b-storage-s3/artifacts/phase-b/b04-s3/fix-round-1/`. New focus raw captures initially written to uniquely named sibling `wire/fix-round-1-*` directories are copied byte-for-byte into this fix seal's `wire/`; no original wire file was overwritten. Tooling copies preserve exact command construction. New classes compile only into `.tools/fix-round-1/<cohort>` or its new `canonical` tree, never any old frozen target/classpath directory.

| Cohort | Tests/failures/errors/skipped | Interpretation |
|---|---|---|
| length-red |6/6/0/0|Four readers expose actual NPE; the two large-range cases leave proxy exchanges active because the leaked response never aborts. The settle failure is a secondary symptom, not treated as a complete permit-exhaustion proof.|
| length-red-repeat |6/4/2/0|Three-byte valid responses let the proxy finish; after repeated malformed range reads, the same adapter's healthy request actually fails while acquiring exhausted admission. HEAD/control/stat/staging still expose NPE. This is the precise behavior RED.|
| length-green |6/0/0/0|Minimal I1 production correction only; repeated faults and subsequent healthy read pass.|
| all-fixes-green |9/0/0/0|Missing length6 + actual one-attempt retry2 + concurrent capture/snapshot1.|
| framing-native-green |8/0/0/0|Final missing-length6, now including raw-SDK positive framing controls, plus native complete committed-response drop/embedded200error2.|
| canonical-related |24/0/0/0|Admission1 + missinglength6 + recovery4 + reliability10 + replaybudget1 + retry2.|
| canonical-shared |7/0/0/0|Unchanged shared contract against real S3; selector env is explicitlys3.|
| canonical reactor units |38/0/0/0 in each canonical build|Reported as two executions of the same38-unit cohort, not76unique tests.|
| fixture-guard-red |6tests,13failing subcases|CPU/swap/PID drift and full ID mismatch were previously accepted.|
| fixture-guard-green |6tests,0failures|New drift checks plus prior guard behavior pass offline.|

These overlapping cohorts are not summed as unique tests. `test-counts.json` records exact XML counts. No backend skipped. The original shared-local7, native bundle graph, restart and both10001cohorts remain historical evidence; they were neither rerun nor presented as executions of this fix binary.

The canonical-related test source preceded only the later raw-SDK positive-control extension inside S3MissingLengthIT. Its original245-file source manifest and class hashes are retained separately. Final focus8 ran that extension. Before canonical-shared, only that test file was refreshed in the new canonical tree; `canonical-final-source-manifest.json` matches all delivered changed files. Production source is identical across both final canonical groups and final focus8.

Commands used the approved toolchain env. Canonical ITs source the existing approved MySQL fixture env unprinted **only for runner admission**, then the unchanged owned44789S3 env. No controlled MySQL fixture operation occurred. Existing unit tests continue their previously disclosed private H2 TCP outage and unreachable loopback MySQL probe.

```text
python3 .tools/fix1_focus.py length-red ama.storage.s3.S3MissingLengthIT
python3 .tools/fix1_focus.py length-red-repeat ama.storage.s3.S3MissingLengthIT
python3 .tools/fix1_focus.py length-green ama.storage.s3.S3MissingLengthIT
python3 .tools/fix1_focus.py all-fixes-green ama.storage.s3.S3MissingLengthIT ama.storage.s3.S3RetryIT ama.storage.s3.WireProxyCaptureIT
python3 .tools/fix1_focus.py framing-native-green ama.storage.s3.S3MissingLengthIT 'ama.storage.s3.S3MultipartIT#nativeCompleteCommittedResponseLossAndEmbeddedErrorReconcile(java.lang.String)'
python3 -m unittest discover -s tools/dev/tests -p test_s3_fixture.py -v

# Original unchanged canonical runner, in new .tools/fix-round-1/canonical
MAVEN_CONFIG=-DargLine=-Xmx384m python3 tools/checks/run.py it 'S3[AMR][dei]*IT'
AMA_STORAGE_CONTRACT=s3 MAVEN_CONFIG=-DargLine=-Xmx384m python3 tools/checks/run.py it FileStorageContractIT
```

No historical artifact/cohort name was reused. The focus helper prepends its independent classes to the previously verified canonical137-entry classpath, which has no Apache/Apache5/Netty transport; unlike initial implementation focus runs it does not use the old Apache5-containing classpath. New actual final canonical classpath string SHA is `6f35fccaa3a2f8e76b61ea4e5bef35c8800eac580c11daf21bb999be07dc79d1`; its complete list JSON SHA is `0180bca7f26b0f9d92b08a63a80f9c9eeb51591e4ad06d8218afa9b04ae842e1`. Dependency declarations did not change and no old graph/package record was regenerated.

## Actual malformed-response body measurements

`missing-length-metrics.json` includes raw capture SHA and separate upstream/delivered body counters for every new missing-length capture. Final framing-native-green observations:

- Four missing HEAD lengths: zero body bytes and zero finalGETs during the malformed sequence; subsequent healthy read succeeds.
- Four close-delimited206 and four chunked206 responses: three upstream/delivered payload bytes each; four typed rejections on the same admission2adapter, then successful healthy read. Chunk framing overhead is not mislabeled as payload bytes.
- 4MiB requested close-delimited range:15517upstream/14069delivered body bytes before abort.
- 4MiB requested chunked range:18413upstream/15517delivered body bytes before abort.
- Missing length in control/stat/staging: each real upstream body contains3bytes; each rejects with sanitized storage error and then serves a healthy controlGET.

Both large-response counts are below the declared1MiB transport-window allowance, not whole-object drains. Raw counters describe proxy socket body reads, not a claim of universal network/kernel buffering behavior. The final related admission test again observes configured2/actual upstream peak2. Existing16/64MiB replay body totals remain75497472/293601280respectively; no change to logical coalescing or recovery indexing was made.

## Seals, commit correspondence and limits

- Original seal SHA remains `59536dde036a46a1d3df3d4866697fa7d4d776b2d292e73cb978cffa66571454`: all353sealed file hashes/sizes checked with zero mismatch. Original147compiled class files, including frozen cohorts and old final canonical classes, also match. See `original-seal-unchanged.json`; a postcommit check again validates all353original sealed files. Original reports/archives/runtime classpaths/raw graphs/wire/time records are intact.
- New20-path source archive `source.tar.gz` SHA **7c694b6374a174b334aae694653212356542ac986bdc49156cbfef2120f26824**; source manifest SHA `6e79fb2cc5b1b40d2806568af021cb0c2dbce094e5f5327b76190b24bb36a366`.
- New final canonical245-file source manifest SHA `a1305b2924fe7056db9bb331764de48f9013b58ae10a5ff48099d93afa9b95c9`; final and related-before-extension compiled manifests remain separate.
- New141-file evidence seal SHA **635040bd8e2626007917bed2d154335411d0e3c6e84fe2f0c6303ae795c4b29a**. It includes allowlisted raw logs/XML/JSON, source archive and tooling copies. No credential/data/env file is included.
- Redaction scan checks158changed-source/new-evidence files against all6actual credentials from the three owned fixtures, with zero hits; values were never emitted. Scan SHA `79dce975918ff99f00cb6b4e871c0f0e38ad0accfbdacc75d32bd61e25f6e5e3`.
- Separate postcommit attestation SHA `3a3dcbaa87d008fa0483bba43d861ff2f6f19ac48fc3d65c45b95acb8bd1d805` verifies all20committed blobs equal the source seal, all141new sealed files and353original sealed files still match, and worktree is clean. It was created after the new seal and is explicitly separate from that immutable141-file manifest. `git diff --cached --check` passed before commit.

No known finding remains unaddressed by this author fix. This is not independent review approval or production qualification. Original business B04/MySQL/registry, provider durability/HA/capacity/TLS and external-writer lifetime gates remain open. Missing metadata is intentionally rejected; general chunked storage support is not inferred from the raw-SDK framing controls. No unchanged expensive cohort was rerun to manufacture stronger claims.
