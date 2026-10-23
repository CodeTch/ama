# Task 6 final Task5 seam repin — 2026-10-24

Preparation only. **Frozen candidate c943d15f4a2b9f294e4b7791fb51eb67d3acce5d is NOT adopted.** Fix02 is independently under review. This bounded supplement compares original candidate 857102b917bbef77c38fae3211e3a65207a98c6c (C) to frozen candidate c943d15 (F), including fix01 11eb540 and fix02 c943d15. It neither adopts Task5 nor activates Task6, re-reviews Task5, redesigns Task6, reruns tests, or qualifies runtime resources. Existing source/budget/preflight/candidate-repin reports remain byte-for-byte unchanged.

Worktree `/home/tanren/ama/.worktrees/phase-b-completion`; W `.superpowers/sdd/2026-10-23-ama-phase-b-remaining`. All product observations and source seals below use exact committed `git show F:path` bytes, compared directly with C bytes. No working product bytes are substituted. Only this report and its independent source-manifest JSON are newly written.

## Binding and exact carry-forward

Manifest `task-06-final-task5-seam-repin-20261024-source-manifest.json`, SHA256 `b2b219a8c49e17d9d793b9cd5fe5d10abcab5ab08a865257ab67d0f6251d8e0d`, supplies exact old/current Git blob IDs, SHA256, byte lengths and line counts for every original 51 path, 18 focused file pin, 12 carry-forward path, 21 budget-table path and all backend paths changed by either fix. For each original focused range it also seals exact raw newline-preserving old/current same-numbered slices; these range hashes do not replace full-file seals. The original 51 C path bytes independently match the previous report's complete 51-row byte/SHA table.

- Original 51 paths: **45 exact byte matches; 6 changed**: LocalFileClient, S3FileStorage, S3Objects, TranscriptImportRuntime, TranscriptWorkerRuntimeProvider, WorkerLoop. All original tests/POMs are unchanged. No original path disappeared.
- Original 18 focused file pins: **15 entire files exact; 3 changed**: TranscriptImportRuntime, TranscriptWorkerRuntimeProvider, WorkerLoop. **16 of 18 original range groups exact**: WorkerLoop's old functional ranges are unchanged although its close method outside those ranges changed. The table below distinguishes these facts.
- All **12** previously carried source/hash/OpenAPI paths remain exact C→F, including TranscriptConfirmationHash, StoredSourceGraph, ArchivedProfileResolver, StoredFactsIndex, MinutesContracts, JdbcTranscriptRows, TranscriptReadGraph, BoundedTranscriptMetadata, ConfigArchiveIndex, ControlledConfigArchiveSource, FileRegistry and contracts/openapi/api.yaml. Earlier B→C equality therefore carries transitively through F. No saved/restore/draft-hash/confirmation/OpenAPI producer implementation was added by these fixes.
- All **21** prior budget-table source paths are exact C→F. Their earlier B→C corrections remain the existing candidate supplement's responsibility, especially the already-added original parseBudget accessor and measured parser. This report does not restore obsolete B wording.
- Two-fix backend name-status lists are preserved separately in the manifest. Product changes are exactly the 9 rows below: fix01 changes LocalFileClient, S3FileStorage, TranscriptImportRuntime, provider, WorkerLoop and adds SourceReadLifetime/ScopedS3HttpClient; fix02 changes FileBackendDefinitions, SourceReadLifetime and S3Objects. Added/changed test source seals are locator/custody facts only; no pass claims are inferred from their existence.

## Original 18 focused pins

The ranges are the original report's line numbers, deliberately compared at those exact numeric slices. `same` means exact raw bytes, not an approximate semantic match. Current full SHA/line counts are in the manifest; changed product seals follow.

| Focused file | Original ranges | Entire file C→F | Range group C→F |
|---|---|---|---|
| `ConfiguredProcessing.java` | `11-22` | same | same |
| `TranscriptImportRuntime.java` | `26-40,45-61,63-100,108-145` | CHANGED | CHANGED |
| `JdbcTranscriptAuthority.java` | `59-68,173-188,252-300,325-353` | same | same |
| `JdbcProcessingFactsReader.java` | `42-69,111-125` | same | same |
| `ImportTaskDomainPolicy.java` | `110-146,151-172` | same | same |
| `ConfigBindings.java` | `18-29,82-86,118,123-127,152-170` | same | same |
| `MeasuredTranscriptDocumentParser.java` | `5-10` | same | same |
| `TextTranscriptParser.java` | `21-35,66-73,114-123,163-173` | same | same |
| `ProcessingConfiguration.java` | `18-42,74-111` | same | same |
| `FileConfiguration.java` | `35-46,78-87` | same | same |
| `TranscriptWorkerMain.java` | `13-25` | same | same |
| `TranscriptWorkerRuntimeProvider.java` | `16-40` | CHANGED | CHANGED |
| `WorkerMain.java` | `7-15` | same | same |
| `WorkerLoop.java` | `18-40,119-160,165-201` | CHANGED | same |
| `JdbcTaskQueue.java` | `25-32,139-159,163-188` | same | same |
| `TransactionRunner.java` | `14-19` | same | same |
| `JdbcProcessingDiscovery.java` | `35-74,256-264` | same | same |
| `WorkerHealthProbe.java` | `15-28` | same | same |

For changed runtime lines, use **current** Settings/install/prepare 26–40, private policy/carrier 45–61, prepareRaw 63–104, bind/guard 105–115, writer 116–128 and validator 129–143. The writer/validator bodies are exactly the old bodies shifted by two lines; no output/provenance allocation change follows. Provider constructor/create/assembly 16–39 is exact; close is now 40–48. WorkerLoop old 18–40,119–160,165–201 remains exact; new shutdown 204–235 must be carried too.

## Exact affected product bytes at F

SHA256 uses full committed file bytes, not pretty-printed text. Line count is raw bytes split into lines, not code-only lines. Exact C/F comparison metadata and full paths for unchanged source are in the independent manifest.

| Product path | Bytes | Lines | Current SHA256 |
|---|---:|---:|---|
| `backend/adapters/src/main/java/ama/storage/FileBackendDefinitions.java` | 1359 | 30 | `e138d4192cfc49998ec3203689fd0535fbdca9913d58f3d2872c1615dcbad987` |
| `backend/adapters/src/main/java/ama/storage/LocalFileClient.java` | 9057 | 214 | `0b3a791e3aa93cc63ef04e117b5f65e3b186c75f4b6a7f6b6f9b8aaab1d2a214` |
| `backend/adapters/src/main/java/ama/storage/SourceReadLifetime.java` | 4805 | 89 | `1082731ac484632f243f5ae6e9b03e7e5f00846de0b38a887653a713af0d8ea8` |
| `backend/adapters/src/main/java/ama/storage/s3/S3FileStorage.java` | 9682 | 227 | `20197e0ae0771f84d7253e6c5e91cb7de3a73367f9a556054b9c09e9d1ad7683` |
| `backend/adapters/src/main/java/ama/storage/s3/S3Objects.java` | 7488 | 203 | `3d4df6d7eff8d8cb7c8b37df73dec23eb7508c3f427c808401dcd2ba1a70ce99` |
| `backend/adapters/src/main/java/ama/storage/s3/ScopedS3HttpClient.java` | 2614 | 45 | `b26bdf44ac6538377d0ea29b5c53de1623b4c39e3b0856b71026521b6603b31b` |
| `backend/adapters/src/main/java/ama/task/TranscriptImportRuntime.java` | 14078 | 146 | `5b9ffc7b767c01cdd51c1434318b8eb375fbf2e37a81b96107e8569eb714d39b` |
| `backend/worker/src/main/java/ama/worker/TranscriptWorkerRuntimeProvider.java` | 3628 | 50 | `c9d331c12432222ec0bc93732317cc6f1a87fcde5a65dcd22c87c2a983d99f48` |
| `backend/worker/src/main/java/ama/worker/WorkerLoop.java` | 14374 | 242 | `2bf351bc9625a0ba4491bdaf752da7c1499193cd9f96cabe0747e9d8b88529cf` |

## Actual seam delta the Task6 author must carry

**Import lifetime now begins before source acquisition.** TranscriptImportRuntime:64–66 computes the existing positive bounded batch deadline and enters `new SourceReadLifetime(PreparationCancellation,long deadline)` before Worker facts/archive/source work. At :90–93 it opens the genuine existing `components.sources().open(...)` download and passes `download.body()` directly to the measured parser. The old per-download cancellation registration and outer remaining-batch DeadlineInputStream were removed. The process lifetime covers pre-open, headers, error/control reads and data bodies via actual storage clients; body-level transport DeadlineInputStream wrappers still exist. Do not copy the old candidate :87–97 pattern back or double-register a second PreparationCancellation callback.

SourceReadLifetime:24–35 rejects nested scopes and exhaustion of its static 1024-scope capacity, registers one cancellation callback, schedules on a two-thread daemon timer, and binds the scope to the calling thread. `track(Runnable)` (:39–46) registers actual abort hooks, or returns an unscoped registration when no scope exists. `tryAcquire(Semaphore,long,TimeUnit)` (:49–63) keeps actual S3 admission within remaining deadline and checks sticky cancellation at at most 50ms polling slices; it releases a just-acquired permit if cancellation/deadline wins. Outside a scope it preserves normal semaphore timeout behavior. Abort (:67–74) attempts every captured callback despite RuntimeException/Error, caps accumulated suppressed failures at eight, then throws the first; close (:76–80) releases callback/timer/capacity/ThreadLocal in finally. These are deny-only lifecycle operations, with no source/lease/holder/write authority. Scope must be entered/closed on its preparation thread; no automatic cross-thread propagation or global Task6 deadline is supplied.

**Actual native/S3 transport registrations moved earlier.** LocalFileClient:57–117 registers before `sendAsync`/`future.get`, owns future/body assignment races and aborts the future plus assigned body; the existing finite body lifetime remains. S3FileStorage:30–60 installs the package-private ScopedS3HttpClient around the actual Apache5 client while preserving configured acquisition/connect/socket/API/attempt timeouts and one-attempt policy. ScopedS3HttpClient:18–42 registers the real ExecutableHttpRequest before delegate.call(), owns abort/body-close cleanup, and covers SDK headers/error/control reads as well as returned body. S3Objects:33–40 now calls SourceReadLifetime.tryAcquire for its existing admission semaphores. The public FileStoragePort/FileRegistry/ProcessingSourceReader selectors and source authority protocol are unchanged. Task6 original-byte routes continue to use the existing genuine Task3 provider/finalizer/postcommit open, without an extra S/F wrapper or a forged byte grant.

**Shutdown cleanup is now complete-attempt cleanup.** WorkerLoop:204–235 attempts scanner shutdown, every execution cancellation, heartbeat/worker shutdown and termination waits even when a callback fails; it retains a shared two-second termination deadline, restores interruption and propagates aggregated failure. Synchronous callbacks themselves are not thereby proven bounded. Provider.Runtime.close:40–48 always attempts definitions.close after loop.close and propagates failures; FileBackendDefinitions.close:14–28 attempts every AutoCloseable backend, retains first failure with at most eight suppressed later ones and preserves interruption. This affects lifecycle/error handling and transport resource release; it does not grant domain capability or certify memory/concurrency safety.

**RAW completion, saved reader and command authority are unchanged.** Current runtime :120 still allocates one RAW revision equal to immutable `TaskSpec.businessVersion`, retains exact input/batch/fingerprint/owner/producer-task, parent NULL, original stable IDs and global ordinal; :127 returns real SUCCEEDED TRANSCRIPT_REVISION. :129–143 performs existing same-invocation private validation. JdbcTaskQueue, ImportTaskDomainPolicy, JdbcTranscriptAuthority and JdbcProcessingFactsReader are exact C bytes: queue-private Carrier/AfterTask restrictions, genuine issuer/DataSource/held-gate/full-spec checks and historical actual SUCCEEDED+same output remain binding. Worker RUNNING lease facts are not historical producer proof. Human WORKING save/restore still needs the adopted fullcopy/null human producer/original ancestry/complete zero or nonzero marks/r+1/CAS/replay/current-pointer implementation; Task5's private just-written RAW validator is not that reader.

**Same-algorithm draft/confirmation factoring is unchanged work.** The twelve carried files prove no hash/profile/confirmed projection/golden or source-graph extraction change in either fix. Task6 must implement the adopted confirmation-independent saved validation/savedData/hashSaved/hashDraft while preserving old public confirmed paths, original canonical field order/null/[] semantics and complete four-archive closure. Stable actual confirmation ID/hash/saved-revision receipt remains distinct from fresh observation; repeated unchanged confirm and historical replay rules are unchanged. No imaginary confirmation, hash, producer or accepted-revision column is supplied by the lifecycle fixes.

## API/Worker assembly and resource implications

API ProcessingConfiguration, FileConfiguration, ConfiguredProcessing, TranscriptWorkerMain, WorkerMain, ConfiguredBackends and health/discovery sources are exact C bytes. API still creates one genuine Components and installs the confined wrapper on its queue; its file registry reuses that Components.registry. Independent Worker provider.create:23–31 still validates archives then builds one per-process authority/queue/contextual handler assembly. Only cleanup behavior changed; do not build a second authority/queue, cast confined policy to ImportTaskDomainPolicy, or require live Worker availability/private Worker credentials merely to inspect valid retained DB text or replay accepted results. No correction installation component was added.

Existing Settings positive aggregate bytes/UTF16/segments, batch deadline >0/≤1h, ConfiguredProcessing property/timing validation and provider capacity1..64 are unchanged. Existing original `ValidatedProcessingBinding.parseBudget()` and measured Usage(sourceBytes,decodedUtf16CodeUnits,segments) are exact; current runtime :81–95 still intersects original per-file parse limits with remaining aggregate bytes/UTF16/segments and sums actual measurements. Human edits never reparse or reassign stable IDs. The changes extend denial/release to pre-open and admission phases; they do not increase byte/string/fact/edge/copy/request/response/archive/canonical limits or supply a new numerical effective tuple.

The previous finite candidate budgets remain unqualified. Task6 still needs explicit operation-scoped aggregate UTF8/scalar/UTF16/copy/response/revision/task accounting before LONGTEXT transfer and all S/F/readback, plus real request/escaped-response admission. Full original catalog/closure caps (64KiB artifact,1MiB controlled closure), canonical minima and the canonical-scalars key's actual maxContainerEntries meaning remain unchanged. 1024 lifetimes/two timer threads/50ms polling/two-second shutdown wait are source constants, not observed deployment capacity, guaranteed cancellation latency or replacement Task6 limits. Backend callbacks, JDBC acquisition, archive reads and root-held work are not all made interruptible by this transport helper; carry the exact existing mechanisms and qualify relevant Task6 deadlines/cancellation rather than claiming universal coverage.

The adopted S→A→F boundary still requires one genuine gate per acquisition, no archive/parse/hash under roots, complete retained metadata/parent/child/empty-mark locks and pretransfer reservations. The adopted owner stored-text versus sourceBytes/new-business split is unchanged. R1:215–219/300 still requires actual immutable qualified DISABLED_NOT_INSTALLED composition (safe qualificationId, NOT_APPLICABLE, no pendingCount); unknown/contradictory/enabled without authoritative reader remains unavailable. The lifecycle wrapper and Worker health cannot stand in for that component. Task10 disposition and Task11 publication freeze integration gates remain unchanged.

## Root final adoption refresh and custody

This is the minimal actual seam refresh to frozen F, **not an adoption verdict**. Root resolves independent fix02 review first. If accepted source is F and root advances HEAD only with adoption/central documentation, Task6 activation needs only a read-only `git diff --exit-code F ADOPTION -- backend contracts/openapi/api.yaml` (and any additional product path declared in those adoption commits), plus confirmation that the pinned manifest paths still have these committed blobs. That verifies product bytes stayed equal; it does not require another source/budget/preflight/design pass or rerunning unchanged tests. Root supplies actual clean adopted BASE and concrete qualified effective-budget/correction composition pin to the Task6 author. If product bytes change during review, refresh only those changed seams and their actual budget/composition effects. Do not relabel F as adopted before that decision.

Task6 implementation still has the existing prospective real-MySQL complete-set/pretransfer/concurrency tests, native+S3 production RAW→save/marks/restore/confirm/history/member pipeline, original-archive fresh JVM, hash golden equality, replay/CAS/ACL/no-store/token and correction qualification obligations. This source-only supplement executes none and neither certifies nor substitutes Task5 author's test receipts. No test/build/network/DB/Docker/private env/PKI operation, product/tracked-file modification, staging, index mutation, commit or subagent was used. Existing unrelated untracked root resume document was observed and left untouched. Prior report SHA seals and adopted wrapper/R1/closure/review exact committed seals are in the manifest; the three required prior reports remain unchanged. Report SHA is delivered separately, avoiding a self-reference.
