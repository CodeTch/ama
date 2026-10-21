# B04 S3 implementation report — ready for independent full-owned review

Implementation and the described local test cohorts are complete. Root must still supply fresh independent full-owned SPEC and code-quality review; this is not B04 business/production acceptance. No tests are still running.

## Identity and ownership

- Worktree `/home/tanren/ama/.worktrees/phase-b-storage-s3`, branch `codex/phase-b-storage-s3`.
- Original dispatch BASE `cc1f1792a900686caee3c3b74ab354582b0315e3`.
- Reviewed root was mechanically fast-forwarded to `0daecad85d348305c11982b5f0a7e2536a47434d` after sealing all 32 then-owned source files. Every owned SHA remained identical across the update; no stash or conflict resolution was needed.
- **Full independent-review BASE is `0daecad85d348305c11982b5f0a7e2536a47434d`; final HEAD is `9a8b79dfb4eb1ade5a18d7757699a40b3ed9f0fb`.** Commit message: `Implement DB-free S3 storage with immutable recovery and bounded IO`. All33owned files/4151insertions/6deletions remain in that full diff. Root's later migration-register-only `2f621d65` was not merged or tested here. Worktree is clean; staged diff check passed before commit.
- Owned paths: `ama.storage.s3` main/test packages; integration S3 fixtures/proxy/process tests; `ContractFixture`/selector test; only fixture construction substitutions in shared `FileStorageContractIT`; parent/adapters dependency declarations; narrow file-service SDK exclusions; `tools/dev/s3_fixture.py`; `docs/acceptance/b/s3-file-infrastructure.md`. No owned SQL, business DB call, public contract, API, OpenAPI, native-file behavior or production bean change.
- Approved inputs: exact implementation brief, original/preflight brief, original adopted protocol SHA `3bb475ce826a739ae6c1f01246084e16fdec76dcf9038a387a79bcb7351904f5`, reviewed selected-manifest local behavior, and adopted tracked v3 addendum SHA `065fd8b7f4852c8d395352d83083a67ad800523bd9ca5fcf1c1fb5db8f8c702b`.

All raw evidence below is under `/home/tanren/ama/.worktrees/phase-b-storage-s3/artifacts/phase-b/b04-s3/`. It is ignored, retained locally and not committed as product source. Credentials/private fixture state remain under ignored `.tools/`. Source archives contain allowlisted source only, never env/data/credentials.

## Implementation delivered

The explicit `S3FileStorage(S3Config)` factory implements all nine unchanged FileStoragePort methods plus token-only `resume`. Separate classes implement canonical control records, v3 layout/full identity binding, bounded staging, selected manifest journal, bounded publication interval index, conditional native publication, transport admission and HEAD/range streams. No giant adapter class, mutable CAS, DB, process lock, external index or local recovery file is used.

Parts stage actual caller bytes through EOF before receipt publication; selected receipt descriptors and actual chunk/page/full SHA are verified. Completion uses exactly the supplied contiguous selected manifest. Unselected accepted parts are retained as orphans, including empty parts. Wrong whole hash leaves OPEN retryable. One immutable terminal object elects ABORTED or COMPLETE; matching complete helpers resume the original frozen manifest; abort after COMPLETE is a successful no-op, late writes cannot acknowledge, different manifests conflict. Every helper journals its own fresh native intent and known ID; unknown Create response retains intent/provider upload without guessing or blind abort. Final PUT and CompleteMultipartUpload use wire-level If-None-Match `*`; success reconciles full actual final bytes, not ETag or user metadata. Result records bind the terminal fingerprint and actual result.

`PublicationSource` fixes the initially measured replay-from-start quadratic byte amplification. One full actual verification pass builds at most 10000 interval positions. Each SDK reopen locates the staging chunk directly; fetched chunk bytes still receive SHA/length validation. No full logical manifest is retained in heap. The per-native streaming gate is acquired **before** the shared request permit, leaves one shared slot for nested staging reads, and releases in finally. Staging GETs take only the shared permit. Admission below two is rejected at configuration construction.

The approved v3 layout uses exact UTF-16 identity, `.0`/`.1` escapes, `~hhhh`, 199 encoded characters per `x`-prefixed physical segment, exact canonical reverse decoding, full immutable scope/key bindings, and static physical configuration identity excluding rotatable credentials/timeouts. Ordinary 512-character generated-key envelope and 64-character backend/namespace fit. Encoded length 942 is exactly 1024 physical bytes; 943 rejects before I/O. All data/control templates receive pre-I/O total/segment/depth checks. No empty/dot/dotdot physical segment, hash fallback, caller segmentation or generic 255-character product limit was added. Registry-generated grammar remains a future unimplemented integration obligation.

List performs one independently scoped provider page with MaxKeys<=limit, validates canonical physical order/cursor scope, hashes only returned-page objects, and permits short/empty advancing pages after concurrent deletion. Range read uses HEAD then concrete VersionId or opaque If-Match, never full stat; zero/invalid reads make no final GET. It requires exact 206/Content-Range/content length/version/validator and aborts promptly on malformed/ignored/truncated streams or caller close.

## Dependencies and controlled provider custody

AWS Java SDK BOM 2.56.0, `s3` plus `url-connection-client`; explicit credentials/SigV4/region/endpoint/path-style/timeouts, no default credential chain, ambient/system proxy disabled, RetryPolicy.none. Apache, Apache5 and Netty clients are excluded. The native file-service adapter edge excludes both new SDK entry modules and preserves its existing JDBC exclusions.

`final-runtime-graphs.log` is a successful dependency-plugin 3.10.0 runtime graph for adapters and native file-service. `final-canonical-classpath.json` records the actual Maven test classpath and proves no Apache/Apache5/Netty transport. `final-native-package-libs.json` checks the `-exec.jar` executable: **8 actual library jars**, no AWS/JDBC/H2/MySQL/Apache/Netty. Historical `native-package-libs.json` has nine entries because one is the directory itself. A new inspection script first selected the ordinary non-exec jar, got an empty BOOT-INF assertion, then was corrected to the configured exec classifier; this was an inspection-script error, not a product failure. Graph was not blindly rerun for that correction.

Historical isolated focus runs deliberately reused `.tools/test-classpath` from the earlier v2 Surefire XML. That classpath still contains unused Apache5. They explicitly instantiate URLConnection and do not establish dependency exclusion; only the final canonical graph/classpath does. Do not conflate these runtime identities.

Fixture helper pins RustFS 1.0.3 linux/amd64 `rustfs/rustfs@sha256:128220dc9382bac81940a1a9616ee8c274a3f3b8f647eedbeb50df5bb8b49922`, 2 CPU, 2 GiB with no additional swap, 256 PID, UID10001, loopback-only fixed port, console disabled, bounded logs. Before own restart/stop it checks exact container ID, owner label, image, mounts, limits and port. No other agent/container/DB fixture was restarted or modified.

- Retained failed v2 fixture: `.tools/s3-java`, fixed 45955. Raw segment length 255 PUT succeeded, 256/512/1024 failed. A deep raw key diagnostic was followed by exit139/OOMKilled=false; subsequent owned restart produced stack-overflow/139. This temporal observation does not prove the exact causal provider code path. Container/data remain untouched; no further deep probe/restart.
- Solely owned healthy shallow fixture: `.tools/s3-java-shallow`, fixed45453. V2 and V3 use separate fresh buckets/static physical configurations, never token redirection or v2 reinterpretation.
- Solely owned V3 restart/final canonical fixture: `.tools/s3-java-v3restart`, fixed44789, independently owned/configured. Five acknowledged keys include 512-character/real >9MiB MPU, exact1024 physical key, slash-rich511 and199/200 boundaries. Exact owned restart preserved endpoint; fresh JVM checked full bytes, range, bounded list, original-token resume and delete. `v3-restart-{write,read}.log` and `v3-restart-lifecycle.json` record the actual two-process probe and ownership. This is local restart evidence, not power-loss/HA qualification.

## Commands, test cohorts and results

Commands run inside Ubuntu24.04 via WSL, using the approved `.tools/env.sh` Java/Maven toolchain. IT commands additionally source the approved MySQL fixture's env **unprinted, for existing runner admission only**, then the selected owned S3 fixture env. No S3/local selector connects to that MySQL fixture. Existing reactor units themselves use their own H2 TCP outage and deliberately unreachable loopback MySQL probe; those are not business DB operations or a controlled MySQL-fixture test.

Canonical runner is unchanged `python3 tools/checks/run.py`. To protect the running v2 `target`, a complete source allowlist was copied to `.tools/v3-canonical`, with per-file SHA in `final-canonical-source-manifest.json`. `MAVEN_CONFIG=-DargLine=-Xmx384m` bounds the forked heap. The source copy is reviewed-root plus owned files, not an alternate implementation.

| Cohort / command | Actual result | Evidence |
|---|---:|---|
| Final canonical `run.py unit` after admission fix | 38/38, zero failure/error/skip | `final-canonical-unit-admission.log`, matching XML folder |
| Canonical shared local `run.py it FileStorageContractIT`, `AMA_STORAGE_CONTRACT=local` | 7/7 | `v3-canonical-local-contract.log`/XML |
| Final canonical shared S3 same selector, env=s3, after final admission change | 7/7 | `v3-canonical-final-s3-contract.log`/XML; earlier pre-admission7/7 retained separately |
| Canonical post-seek reliability | 10/10 | `v3-canonical-reliability.log`/XML |
| Canonical multipart boundaries excluding expensive10001 and separately measured mixed-payload test | 5/5 | `v3-canonical-multipart-boundaries.log`/XML |
| Final canonical related regression `run.py it 'S3[AINR]*IT'` | 23/23,zero failure/error/skip | `v3-canonical-final-s3-regression.log`/XML |
| Final canonical range test with added overlong Content-Length fault | 1/1 | `v3-canonical-range-length.log`/XML |
| Final V3 shared contract + all layout HTTP cases after missing-binding delete fix | 11/11 | `v3-final-common-layout.log`/JUnit XML |
| V3 native-process races plus complete/put/head/range/error classification, all3 version modes | 12/12 | `v3-native-races.log`/XML |
| Indexed replay + recovery4 + mixed native boundaries | 6/6 | `v3-replay-green.log`/XML |
| V3 integrity cases | 4/4 | `v3-integrity.log`/XML |
| V3 selector invalid env actual subprocess | 1/1 | `v3-selector-green.log`/XML |
| Post-admission barrier + native races3 + replay | 5/5 | `v3-admission-green.log`/XML |
| Frozen historical v2 full multipart, including actual10001 tiny logical parts | 7/7,3641s | `multipart-bounded.log`/XML |
| Frozen independent v3 actual10001 tiny logical parts | 1/1,2358.096s,384MiB heap,one final conditional PUT/no native UploadPart | `v3-10001-final.log`/XML directory |
| V3 fixed-provider restart, five acknowledged objects, fresh JVM | explicit probe exit0 write/read, ownership lifecycle verified | restart files above |

The shared contract's historical display labels still contain `local`; actual selector/fixture and commands establish which backend ran. All assertions were preserved. No backend was skipped. Final `test-evidence-inventory.json` inventories every retained JUnit XML, including failures, rather than adding repeat cohorts into a misleading unique-test total.

Recovery/race boundaries actually exercised: independent JVM conditional put and complete/complete/put-complete same/different-content races in never-versioned, Enabled and Suspended modes; real native publication races; separate helper native IDs; kill after durable terminal, known-upload ID and result followed by token-only fresh JVM resume; response loss after actual upstream spec/chunk/root/slot/terminal/result/abort commits; unknown Create; lost native part; actual native complete success followed by dropped response or embedded200/409/412/NoSuchUpload; two late-slot barriers and abort/complete terminal races. Corrupt actual chunk, spec, frozen manifest, result, false metadata, copied foreign-config token, malformed physical list entries and forced fullscope collision fail closed. Only those three explicit process-kill boundaries are claimed; other listed boundaries use actual-commit lost-response faults, not a claim of every possible instruction-level kill point.

## Measured bounded I/O and real corrections

`final-canonical-range-metrics.json` extracts exact final-group wire values. At16MiB, three one-byte GETs transfer exactly3 final body bytes; zero/invalid issue no final GET. Early close reads67357upstream/64285delivered bytes. Ignored200 and malformed Content-Range each read15517/14069; truncated100-byte range delivers50and throws. At64MiB the middle one-byte read transfers1; early close reads15516/14068. The added overlong declared Content-Length case reads15517/14069 before rejection. All are below the declared fixed1MiB transport-window allowance and do not scale as full-object drain. Different successive runs have different bounded socket-window counts; their raw values remain separate. Proxy status in raw events is the real upstream206, while ignore-range changes the client-facing status to200. HEAD/binding/control traffic is separately recorded, not omitted from total request evidence. The proxy counts actual upstream body bytes separately from delivered bytes, without hidden upstream BufferedInputStream.

Real replay RED:16MiB/4 nativeparts stagingGET75,497,472B=4.5x;64MiB/13 nativeparts587,202,560B=8.75x, failing declared8x ceiling. Seek-index GREEN:64MiB293,601,280B=4.375x;16MiB unchanged4.5x. These include actual verification/SDK reopen costs and separately account final-object full-stat reads. They are pinned-profile functional amplification evidence, not tested50GiB throughput.

Real native admission RED: two simultaneous native bodies with admission2 both exhausted their own staging request permits and returned STORAGE_UNAVAILABLE. Minimal streaming-before-shared gate correction passed the real barrier plus native races/replay. Final canonical proxy measured `configuredAdmission=2 observedPeakUpstreamHTTP=2`; config admission1 rejects immediately. No hidden HTTP limit expansion.

Other preserved failures/corrections:

1. Initial real shared contract against deliberately incomplete pre-implementation adapter:7 tests,2 failures/5 errors. Real v2 implementation then7/7 (`red-contract.*`, `contract-attempt1.*`). No stub remains.
2. Actual SDK2.56 dependency graph unexpectedly included Apache5; excluded in POM, final graph/canonical runtime verifies the correction (`red-runtime-transports.txt`). An initial graph attempt stalled on uncached plugin/DNS; only its exact owned Maven PID was diagnosed/killed, then build-only explicit proxy used. This was not S3 proxy configuration.
3. V2 key/provider errors and both connection-refused multipart attempts each7errors are preserved. ConnectionRefused after provider exit is not feature RED. An intermediate shallow run used ignored MAVEN_ARGS and revealed actual6GiB default heap; it was stopped and preserved, then correctMAVEN_CONFIG384MiB frozen run succeeded. Original failure/heap/jstack logs remain.
4. V3 required layout behavior initially2/2RED. First update left one old v2 slash-encoding oracle in pure test (`a/` versus approved `a.1`):5tests/1failure, corrected to adopted spec, then7/7 and later8/8. Final canonical includes9pure S3 tests within38units.
5. First V3 list HTTP assertion used URLEncoder's `%7E` spelling although SDK correctly emits literal `~`. Original10tests/1failure and focused diagnostic1failure remain. Query decoding comparison verifies the exact independently built logical prefix; no production prefix behavior was weakened.
6. First V3 protocol35tests had2failures from privileged test mutation still addressing v2 physical paths in HEAD/GET races. The test was corrected to the v3 mapped path; later complete native/concurrency group12/12 and final related regression validate actual v3 paths.
7. Missing-key-binding delete actualRED: an unbound physical object was erroneously removed, then test GET raised NoSuchKey. Minimum production fix makes missing binding a no-op while mismatching existing binding still fails closed. Final common/layout11/11. No schema/API change.
8. Selector invalid-env first test harness cleanup failed on retained directory; preserved separately. Corrected harness then showed true behavioralRED; fail-closed selector correction passed1/1 and final canonical unit.
9. Replay and admission actualRED/GREEN above remain distinct artifacts, not rewritten as a single successful run.

## Source/evidence identity and remaining gates

Historical source archives:

- v2 initial22files SHA `68674b9dfa709302ac2cb1b5c6dd31ed1cccd73a767b04181eb2c8ccfd1952be`.
- v2 final-before-v3 23files SHA `46bddc1c68a9fc872fd31f44ad7bc5a37362b4b3ad958486f4045815a0fb3119`.
- v3 before indexed seek26files SHA `1200bf1d99d5e4d715461276b7532ee3ad4cc39f69e5b2d85c27bd5257b888c8`.
- v3 before root merge32files SHA `4332fb07d2345a2d93dc217623362d0a63c82aff7ba4f7cf2edc755673a6cc6a`.

Each has a per-file JSON SHA manifest. Final33-file source archive `v3-source-review-candidate.tar.gz` SHA **40e1ea84723f3fad9e6f55c23e8fa70303246d8feaf1c0d28d5e8dcfb879d593**, per-file manifest `v3-source-review-candidate.json` SHA `1d4c4ab3f39e8acc83ac231d0ae95dfec4909078ffbd0a81a6430b604d9ba5e2`. Canonical241-file source manifest SHA `0232a09fd8ee1419336fb5c85884901d0fcadcf0d1f03b8addee503a6caab673` captures reviewed-root plus these exact owned contents. All described commands ran before commit; commit packages those validated contents, with only documentation addition after the final test.

`compiled-cohort-identities.json` SHA `2d2d3c29d3bf9cd507de79a138b19e973b66c3e7c0492587214d92ca4958c17b` records frozen actual class hashes and final canonical hashes. Javac debug/parameter options mean raw class inequality is not itself a semantic delta. A normalized `javap -p -c` comparison of20production classes (`10001-final-bytecode-comparison.json` plus exact diffs) shows17unchanged; only NativePublisher's native UploadPart call→streaming gate, S3Objects gate implementation and S3FileStorage missing-binding delete differ. The10001case publishes through the unchanged smallPUT path and does not delete. Actual layout/selected manifest/indexed source/coalescing instructions are identical. Final independent review can assess this exact impact, without pretending a second expensive run occurred. V2 target and V3 isolated10001/restart classes were never recompiled in place.

Historical focus actual classpath file `.tools/test-classpath` SHA `02ec3c0ee5889f82f4424ac82e89fa7fc89e64210344d5a0bd219f2a144e8515` still contains unusedApache5. Final canonical classpath string SHA `ff767d6c2187eb1919e93d1eee4b696dd60d751bd5aa53579123c86a64b0d82f`, JSON with complete entry list SHA `956fef119241b770366e3357ae8cd24889e190f57705310e6bc9b6647b8e2cc1`, excludesApache/Apache5/Netty. Final graph SHA `7557f7741a081c48cd5fdcd8657d2240a2abdfb2cd01dc63b549105b83de8751`; final native-package manifest SHA `058204d82ab1e1c58839147d29f6806e2416cf8675d18e7df6f243a9b2a117d0`. These new files never overwrite historical runtime/package evidence.

`review-candidate-evidence-manifest.json` SHA `59536dde036a46a1d3df3d4866697fa7d4d776b2d292e73cb978cffa66571454` seals the retained source/raw evidence after all runs finished. `final-redaction-scan.json` scanned379source/evidence files against all6actual credential values from the three owned fixtures, yielding zero matching files; no credential values were emitted. Final commit contains only33allowlisted paths, no raw env, fixture data or ignored evidence.

The retained proxy event list uses CopyOnWriteArrayList, so long evidence capture has quadratic append copying and high old-generation occupancy. This is a test instrumentation cost, not product replay behavior or a measured production throughput limit. Old/v3 frozen cohorts were allowed to progress; no heap widening, provider reset, fixture reinterpretation or mid-run class mutation was used to manufacture success.

No production qualification is claimed: power-loss/HA durability, sustained capacity/throughput, production provider selection/credentials/registry governance, real application key-generation grammar and controlled business B04/MySQL integration remain separate. Technical50GiB/native5GiB/max10000 profiles were checked by planner/representability tests, not multi-TiB transfers. Conservative retained staging/native attempts require future authorized quiescent cleanup; no automatic cleanup or permanent external-overwrite/delete fence is promised. Full independent review remains required before root adopts this implementation; all locally required cohorts described above have now completed.

## Exact command ledger for principal final and RED/GREEN cohorts

These are records of completed invocations, not an instruction to overwrite their evidence names. All use `source /home/tanren/ama/.worktrees/phase-a/.tools/env.sh`; canonical IT admission also sources `/home/tanren/ama/.worktrees/phase-a/.tools/mysql-fixture/ama-test-b01-f86e8451e91e/env.sh` without printing it. Final canonical/focus admission uses `.tools/s3-java-v3restart/env.sh`; the frozen10001groups use `.tools/s3-java-shallow/env.sh`. No environment contents are copied into evidence.

```text
# In .tools/v3-canonical (complete source copy, original check runner)
MAVEN_CONFIG=-DargLine=-Xmx384m python3 tools/checks/run.py unit
AMA_STORAGE_CONTRACT=local MAVEN_CONFIG=-DargLine=-Xmx384m python3 tools/checks/run.py it FileStorageContractIT
AMA_STORAGE_CONTRACT=s3 MAVEN_CONFIG=-DargLine=-Xmx384m python3 tools/checks/run.py it FileStorageContractIT
MAVEN_CONFIG=-DargLine=-Xmx384m python3 tools/checks/run.py it 'S3[AINR]*IT'
MAVEN_CONFIG=-DargLine=-Xmx384m python3 tools/checks/run.py it 'S3ReliabilityIT#rangesTransferOnlyRequestedFinalPayloadAndAbortWithoutDrain'

# Worktree helper invocations that compile independent classes and capture exact logs/XML
python3 .tools/v3_unit.py red
python3 .tools/v3_unit.py green
python3 .tools/v3_unit.py green2
python3 .tools/v3_unit.py final
python3 .tools/v3_focus.py replay-red ama.storage.s3.S3ReplayBudgetIT
python3 .tools/v3_focus.py replay-green ama.storage.s3.S3ReplayBudgetIT ama.storage.s3.S3RecoveryIT 'ama.storage.s3.S3MultipartIT#emptyLogicalPartsAndMixedBoundariesUseLegalNativeIntervals'
python3 .tools/v3_focus.py admission-red ama.storage.s3.S3AdmissionIT
python3 .tools/v3_focus.py admission-green ama.storage.s3.S3AdmissionIT ama.storage.s3.S3NativeRaceIT ama.storage.s3.S3ReplayBudgetIT
python3 .tools/v3_focus.py 10001-final 'ama.storage.s3.S3MultipartIT#moreThanNativePartCeilingTinyLogicalPartsAreCoalesced'
python3 .tools/restart_runner.py write
python3 .tools/restart_lifecycle.py
python3 .tools/restart_runner.py read

# Dependency proof: canonical cwd, build-only explicit proxy as recorded in .tools/final_graph.py
./mvnw -B -ntp -pl backend/file-service -am org.apache.maven.plugins:maven-dependency-plugin:3.10.0:tree -Dscope=runtime
```

The helper's full command construction, source paths, Java384MiB argument and selectors are retained in the ignored `.tools` scripts and their compiled-cohort/classpath records. The old full v2 multipart used `MAVEN_CONFIG=-DargLine=-Xmx384m python3 tools/checks/run.py it S3MultipartIT` in the original worktree before its target was frozen. `commit-source-attestation.json` additionally verifies every one of33committed file contents equals both sealed source SHA and final canonical source-copy SHA, and verifies clean worktree. The attestation was created after the evidence seal, so it is separate from that immutable manifest.
