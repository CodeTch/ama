# Task 6 exact Task5 candidate repin — 857102b

Preparation only. **Task5 candidate 857102b917bbef77c38fae3211e3a65207a98c6c is committed, independently under review, NOT adopted.** This is not a Task5 source review, acceptance verdict or runtime qualification. Task6 design remains already adopted. Only this W report is written; previous two preflights are unchanged. No product/test mutation, test execution, index/HEAD/DB/Docker/runtime/resource/private-env operation or subagent was used.

Worktree `/home/tanren/ama/.worktrees/phase-b-completion`; W=`.superpowers/sdd/2026-10-23-ama-phase-b-remaining`. Baseline B=`35b2325aee8e6c09aa1bdebc880c4f09bd37837b`; candidate C=`857102b917bbef77c38fae3211e3a65207a98c6c`. Source facts/signatures below come from read-only git show C:path, compared to B only where stated. Candidate commit does not grant Task6 runtime activation. Root pins actual adopted source after independent review; if review changes source, repin only affected seams/files plus relevant bounds/composition.

## Candidate manifest binding

Read `artifacts/phase-b/b-remaining/task-05/explicit-source-paths.json`: exactly 51 explicit source/POM paths. SHA256=`825cd23656de685b41b62bd47ab4d9a2ae5bfecd24cee084d2d7753198f7ceaa`. Read `artifacts/phase-b/b-remaining/task-05/final-source-seal.json`:481 total backend source/resource/POM seals, SHA256=`b9d4c9faf3c4ce566b8b2795d477c43f43ce2944ebeb1ae7f4c6d0c4d107eaaf`. Independently read all 51 explicit committed path bytes and matched each SHA against the final-source seal. This is byte binding only, not semantic/evidence review. Full 51-path table is appended below. The author report was used only as a source-manifest locator; its test/runtime/qualification claims are not adjudicated here.

## Exact candidate interfaces/file ranges/SHA

Paths are worktree-relative; every hash below is committed C bytes, never active working changes.

| Candidate actual file:line | Existing candidate signature/seam | Candidate SHA256 |
|---|---|---|
| `backend/adapters/src/main/java/ama/transcript/ConfiguredProcessing.java:11-22` | record(read,canonical,index,preparation); static read(PropertyResolver) | `d35e12e2fcb30e830254844601003a9cc497ff0418482927a2b768b3f1afb475` |
| `backend/adapters/src/main/java/ama/task/TranscriptImportRuntime.java:26-40,45-61,63-100,108-145` | Settings; install; prepare; private ImportPolicy/Carrier/Invocation; RAW writer/validator | `4056e73593bbc7769ea947083cfd82bb1c8581f109a605cc13330e7683094a4e` |
| `backend/adapters/src/main/java/ama/transcript/JdbcTranscriptAuthority.java:59-68,173-188,252-300,325-353` | Components/create; requireImportSource/Completion; readWorkerFacts; existing byte protocol shifted | `1d7b50ffe57a4116ad267f76f7b8c2e213d5b83622652a1d8cb14fa8a728765d` |
| `backend/adapters/src/main/java/ama/transcript/JdbcProcessingFactsReader.java:42-69,111-125` | legacy ctor; package-private authority ctor; standalone readWorker/current lease facts | `410331e9d9957440c459a1b3ac6558bbad510c82416fa6b57be75c08393e183a` |
| `backend/adapters/src/main/java/ama/transcript/ImportTaskDomainPolicy.java:110-146,151-172` | completionGate retained; requireCompletion; validateOutput/full provenance/zero marks | `f1f75c031c0129ee0f3b9d9e5d737d4dca5c7cf0d72e359befa409ab20bab342` |
| `backend/core/src/main/java/ama/config/ConfigBindings.java:18-29,82-86,118,123-127,152-170` | validated original parseBudget accessor; original canonical/confirmation/four-archive verification | `79adef52e2fc6886b0a279d7272ab5e855ee5d6586b66e0dcd629b81035a3c0a` |
| `backend/core/src/main/java/ama/transcript/MeasuredTranscriptDocumentParser.java:5-10` | Usage(sourceBytes,decodedUtf16CodeUnits,segments); MeasuredResult; parseMeasured | `5331ac6c791c0c1040b6fa07d8b93f1ff738e53bf0fe6a6731b10c4f44145d7d` |
| `backend/adapters/src/main/java/ama/transcript/TextTranscriptParser.java:21-35,66-73,114-123,163-173` | additive measured implementation; old parse delegates; actual usage | `f0d72108f191eb973077b0fedcb5f1e8ed6302b782b4f637c39c5c5ba832a1cd` |
| `backend/api/src/main/java/ama/api/ProcessingConfiguration.java:18-42,74-111` | existing API Runtime; confined queue install; worker-health observer; positive n/i | `ed895ba2e6e514e28bb687dc62497fa166f86c5961516afa1a9ec892754d9001` |
| `backend/api/src/main/java/ama/api/FileConfiguration.java:35-46,78-87` | ConfiguredBackends.read and same actual processing registry | `d2042b9a6fad67c0933d2fc9eb6b269fc924da8e65a44e25be6cd398fb7e6c69` |
| `backend/worker/src/main/java/ama/worker/TranscriptWorkerMain.java:13-25` | actual independent TXT entry; ConfiguredProcessing/ConfiguredBackends/provider/health/run | `eeb7ba5eb2dbb57606b42ee72a21f7395d53d2355d35a2ad6d0b1a710e086e2e` |
| `backend/worker/src/main/java/ama/worker/TranscriptWorkerRuntimeProvider.java:16-40` | bounded capacity; create one process assembly; queue/contextual loop | `5f1c9a5a9b5a3268dcb99a93f24cede2c1686e653a8db67e4220423896d6f677` |
| `backend/worker/src/main/java/ama/worker/WorkerMain.java:7-15` | legacy ServiceLoader and public run(WorkerRuntime) | `9c457478eb07efa7b51b11a316a4f0d18afcc8f8b225690a812061847a12fef2` |
| `backend/worker/src/main/java/ama/worker/WorkerLoop.java:18-40,119-160,165-201` | ContextualPreparedHandler/contextual; observation; cancellation/drain/dispatch | `8a06a569ba19b109d3ecbb0217bcacb7c6c1c04c6136bb610441e2a23d19431e` |
| `backend/adapters/src/main/java/ama/task/JdbcTaskQueue.java:25-32,139-159,163-188` | queue-private registration/AfterTask; private invocation phases; confined completion | `05d9965f0d448d37295680288aeb46c79d3908fc943edb6955c10d0fec94bc64` |
| `backend/adapters/src/main/java/ama/db/TransactionRunner.java:14-19` | additive (DataSource,int transactionSeconds), legacy default -1 | `63c0a3600d1529fdafca64d7413dc511b8b3ea9a2311d98ecce68a386def846b` |
| `backend/adapters/src/main/java/ama/transcript/JdbcProcessingDiscovery.java:35-74,256-264` | execution supplier; actual worker-installed/businessReady; real RAW output locator | `b91a4c63af9d12ff4c3b5363d588fc2c729129f10439941ed11fefda87f40f36` |
| `backend/adapters/src/main/java/ama/transcript/WorkerHealthProbe.java:15-28` | bounded authenticated actual-loop installation observation | `2756105e6092f81e775da51cff4f944932ad37261e89811fb98b048836f25e98` |

## Concrete committed signatures and ownership

`ConfiguredProcessing` now exists as public record `(ArchiveReadBudget read,CanonicalBudget canonical,ConfigArchiveIndex.IndexBudget index,TranscriptImportRuntime.Settings preparation)` with `static ConfiguredProcessing read(PropertyResolver env)`. It packages configuration for the independent Worker; it is not the API Runtime or an authority issuer.

`TranscriptImportRuntime.Settings(Supplier<ConfigArchiveSource.Snapshot> archives,ArchiveReadBudget archiveBudget,TranscriptDocumentParser.ParseBudget aggregate,Duration batchDeadline)` validates nonnull collaborators, positive aggregate bytes/UTF16/segments and deadline>0/≤1h. `install(DataSource,JdbcSubjectFences,JdbcOutbox,JdbcTranscriptAuthority.Components)` first requires exact source identity, then installs queue-private confined registration. `prepare(JdbcTaskQueue,TaskLease,Settings,PreparationCancellation)` rejects ambient transaction and requires that exact queue's private ImportPolicy; returns inert Supplier only. Actual Carrier is private/get throws; no public RAW writer or mint is exposed.

Authority `Components(registry,authority,intake,sources)` and static create signature remain the same as B. Added exact seams are `public void requireImportSource(DataSource expected)` (identity check), `public void requireImportCompletion(List<TaskSpec>,TaskDomainPolicy.PreparedDomain)` (deny-only delegation) and `public StoredProvenanceFacts readWorkerFacts(TaskLease,BooleanSupplier)` (standalone detached acquisition). Package-private dataSource()/transactionRunner()/meetingAccess() derive collaborators from this genuine private-created authority; no public source/runner/access tuple was introduced.

`JdbcProcessingFactsReader(JdbcTranscriptAuthority)` is package-private and derives the exact source/tx/access (:50-53), alongside unchanged public DataSource constructor. Package-private `readWorker(Actor,TaskLease,ArchiveReadBudget,BooleanSupplier)` calls the same standalone reader with lease context; read(:66-69) rejects ambient transaction. :112-118 checks exact one StoredTask/full spec, actual RUNNING owner/attempt/expiry, unfinished attempt, rechecks expiry after attempt lock and owner/current meeting fence. Facts remain detached, not byte or saved-write authority; legacy readStored still does not become a selected complete saved transcript reader.

`ImportTaskDomainPolicy.requireCompletion(List<TaskSpec>,PreparedDomain)` is package-private (:139-145). It uses original JdbcTaskPreparations issuer/fullspec/purpose, private Prepared (not enqueue-only Adopted), permitsEffects and retained completionGate. Gate is set only after all prepared graphs are current and complete source acquisition succeeds (:110-136). Verification then calls registry.access on the retained gate rather than fresh access.requirePrepared/JDBC refresh. This is a deny-only check of prior authentic acquisition, not Task6 authorization or a grant from DTO/enum/bool.

## Original measured parsing and RAW result contract

Candidate ConfigBindings.ValidatedProcessingBinding now stores and exposes `public TranscriptDocumentParser.ParseBudget parseBudget()` (:20-29), passes the parsed original processing-definition budget through complete(:25) and creates binding with original parse at :118. Original processing→parser/codec/confirmation references and canonical/fingerprint/task checks remain in that same original binding. Baseline budget-preflight statement “no public parseBudget accessor” is superseded **for this candidate**; no current-default parse budget is needed or permitted as a replacement. Existing processingBudget/confirmation methods shift to :123/:127 and finish to :152.

`MeasuredTranscriptDocumentParser` is additive: Usage(sourceBytes,decodedUtf16CodeUnits,segments), MeasuredResult(result,usage), `parseMeasured(InputStream,UUID,String,ParseBudget,BooleanSupplier)`. TextTranscriptParser keeps existing profile ama.strict-utf8-lines.v1 and old parse signature, now delegating to parseMeasured (:24-26); successful return measures actual boundedSource.used/all lines.units/emitted segment count (:72-73). Decoded BOM, line separators/blank lines and supplementary units stay included in actual decoded-unit count; human save must not reparse/reassign stable IDs.

Runtime prepareRaw:68-97 reads genuine detached Worker facts, original stored manifest, selects original processing ref, loads closure with manifest reservation, verifies original processing binding and parser profile, orders members, checks declared aggregate source bytes, and computes each next file budget as componentwise min(original parse budget,remaining aggregate bytes/UTF16/segments). Actual measured usage is checked/accumulated with checked arithmetic. Actual source open is components.sources().open, with cancellation abort registration and remaining batch DeadlineInputStream. This is the concrete candidate seam replacing the baseline's pending measured-budget path. Task6 preserves original profile/provenance and consumes produced RAW; it does not enqueue another parse for edits.

Private Invocation.write:118-129 creates exactly one RAW revision with `revision=spec.businessVersion`, batch/input/fingerprint, parent NULL, created owner/time and actual producer task UUID. Segments use parser stable IDs, global ordinal1..n, exact body/file/paragraph, NULL start/end/speaker; it returns `TaskResult(SUCCEEDED,TypedRef("TRANSCRIPT_REVISION",raw.id),null)` and TRANSCRIPT_IMPORTED audit. This writer contains no meeting root advance or saved-pointer assignment. Task6 WORKING remains r+1/fullcopy/null human producer, not RAW renumbering or MAX+1.

Private Invocation.validateActual:131-144 runs in the same queue invocation after write: exact result identity/outcome/output, exact RAW revision/provenance row, bounded complete segment metadata/UTF16 lengths, each exact body/source/paragraph/null annotation, then domain.validateOutput. ImportTaskDomainPolicy.validateOutput:151-166 requires actual TRANSCRIPT_REVISION, fixed input/producer spec, complete ordinal/source metadata and zero marks. The task is RUNNING during this same-call output validation; historical consumers instead require actual committed SUCCEEDED+same output ID through authority.finishRead:349. Do not apply live Worker lease predicates to historical producer success.

These locations identify source behavior only. Acceptance of confinement, actual MySQL atomicity/transfer and retained source evidence belongs to the independent Task5 review, not this report. The private just-written RAW validator is not the Task6 general stored/history reader; Task6 must still implement the adopted complete ancestry/child/empty-mark/pretransfer UTF8/scalar/UTF16/copy/response and S/A/F protocol.

## Actual API and independent Worker per-process assembly

| Producer/composition | Exact candidate construction | Task6 alignment consequence |
|---|---|---|
| API | ProcessingConfiguration.Runtime remains package-private with same Components/NewIntake/budgets/availability fields. configured(:42) creates one authority Components. processingQueue(:87-88) now uses TranscriptImportRuntime.install for configured runtime; unavailable branch has an empty-policy JdbcTaskQueue. FileConfiguration.fileRegistry(:85-87) returns that same Components.registry. File definitions now delegate ConfiguredBackends.read(:35-38). | Baseline wording “registered queue directly uses authority.taskPolicy” must be refined: actual installed queue policy is private confined ImportPolicy delegating authority.taskPolicy, not a second issuer. Do not cast queue policy to ImportTaskDomainPolicy or rebuild another queue/issuer for Task6. Saved graph extensions belong on the existing actual authority. |
| Independent TXT Worker | TranscriptWorkerMain:13-25 reads one bounded private config file (code inspected only, file never opened here), builds DataSource, ConfiguredProcessing/ConfiguredBackends, provider and authenticated health server, then WorkerMain.run. Provider.create:23-31 validates archive supplier before polling; creates tx/fences/access/catalog/outbox, one authority Components, one TranscriptImportRuntime.install queue and WorkerLoop.contextual with real TRANSCRIPT_IMPORT_V1 prepare. | Actual main/provider facade now exists in candidate; no longer merely an unspecified future facade. Each process has its own one genuine assembly, not identity shared across JVMs. Root must adopt exact final facade after review before Task6 pins to it. |
| Worker lifecycle/health | WorkerLoop.ContextualPreparedHandler/contextual adds PreparationCancellation; public envelope is data/lifecycle only. WorkerMain.run(WorkerRuntime) preserves legacy ServiceLoader main and explicit shutdown/start. Discovery capability uses fresh WorkerHealthProbe execution supplier (:68-70); API no URL→NOT_INSTALLED, invalid→UNAVAILABLE. | Worker installed/businessReady is observed from actual loop health, not inferred from API bean/source installation. It is not disabled-correction qualification and not a new requirement that a running Worker exist to inspect valid retained text or verify an already-successful historical producer. |

Queue-private completion remains distinct from Task6 saved commands. JdbcTaskQueue.AfterTask:139-159 authenticates exact queue/registration/holder/spec/lease/purpose and invocation identity/phase. Confined completeWithEffects:168-183 binds, optional deny-only lifecycle admission, writes, validates actual result, settles task then closes scope. Generic Runnable overload:163-164 rejects confined policies. Task6 must not obtain/create an AfterTask, call Supplier.get on the carrier, or repurpose requireImportCompletion as a saved command issuer.

## Required positive limits and correction insertion points

ConfiguredProcessing.read:13-18 reads the existing archive/canonical/index tuple plus **new explicit Worker aggregate** processing.bounds.total-source-bytes,total-utf16,total-segments,batch-deadline-ms. n/i(:21-22) require positive long/int-exact values. Worker timing profile requires exact worker.lease-seconds=20, heartbeat-seconds=5,poll-seconds=5. Settings(:26-30) caps batch deadline at1h; provider constructor(:16-18) requires capacity1..64. Original parse per-file limits are the new binding accessor; runtime intersects remaining aggregate on each file. These are source validation constraints, not observed private runtime values or proven capacity.

API ProcessingConfiguration still constructs its own archive/canonical/index/intake tuple via existing properties (:37-41), rejects files>64/manifest>65536 and checks total-source-bytes for intake. It does **not** call ConfiguredProcessing.read, and does not thereby acquire Worker total-utf16,total-segments,deadline,timing settings. Task6 must pin actual API saved-acquisition/request/response/copy/revision/task and original archive/canonical effective minima separately; do not impose independent Worker private config/credentials merely to construct historical replay/read services. API import request64KiB remains unchanged. `canonical-scalars` still maps to CanonicalBudget.maxContainerEntries in both API and ConfiguredProcessing, not aggregate scalars.

TransactionRunner now has additive `(DataSource,int transactionSeconds)` (:14-19); positive sets transaction timeout,0/<-1 rejects, legacy constructor still uses-1. The provider currently calls legacy constructor. This is an existing candidate timeout seam, not proof every Task6 command has a timeout; no special isolation was added. Existing archive fixed64KiB/artifact,1MiB controlled loader and original catalog caps remain unchanged.

No correction capability/installation qualification is supplied by these candidate import assemblies. Task6's already-adopted qualified DISABLED_NOT_INSTALLED must be composed at actual Task6 service/capability startup using the API's same authentic Components plus explicitly selected domain-installation facts, before claiming new confirmation readiness. Do not derive it from processing.enabled, Worker health NOT_INSTALLED, no correction property, an empty PENDING reader or caught missing-table error. No pendingCount. Unknown/enabled-without-authoritative-reader/contradictory installation remains unavailable; save independence and true historical replay remain as adopted. Process-private component identity/qualification must bind preparation and final check; restart/replacement expires preparations. Candidate code is not evidence that this component exists yet, and no new API/interface name is prescribed here.

## Explicit corrections to the two 35b2325 preflights

The two historical documents remain unchanged and accurately describe B. This candidate-only supplement changes readiness locations, not product rules.

| Earlier B preparation statement | Exact C correction / remaining Task6 work |
|---|---|
| Source preflight PinA: real RAW writer/output allocation pending | Concrete candidate private writer/validator now at TranscriptImportRuntime:118-144 and domain output:151-166; RAW revision=original businessVersion, typed TRANSCRIPT_REVISION, zero marks and actual producer lineage source identified. Still NOT adopted or independently accepted by this preparation. Task6 must validate actual complete stored history/r≤root before human r+1 allocation; no invented RAW/current pointer. |
| PinB: main Worker facade pending | Candidate TranscriptWorkerMain/provider/ConfiguredProcessing/WorkerMain.run now exact committed facade. Independent Task5 review still pending, so adoption/final source pin remains pending. API queue now confined wrapper delegates actual authority policy. |
| PinC: shared authority ctor/readWorkerFacts/deny-only bridge provisional working APIs | They now have exact committed signatures/hashes above, including retained completionGate verification without new acquisition. Candidate-only status replaces unsealed working observation; standalone detached semantics remain, and no bridge creates Task6 saved authority. |
| Budget preflight original parseBudget accessor absent | Candidate ConfigBindings.ValidatedProcessingBinding.parseBudget exists with retained original value, used by runtime min/actual measured aggregate. Canonical/fingerprint/archive behavior is still original binding. |
| Budget preflight no selected numerical runtime qualification | Still true. New source property validation/settings constrain allowed tuples but are not observed effective private deployment values. R1 defaults and prior finite candidate tuple remain unqualified proposals. Root still pins actual Task6 bounds/correction component. |
| Source G1/G2/G3/G4/G5: saved reader/complete children/pretransfer/draft hash/commands/HTTP/correction absent | Remain Task6 work. No new saved current/history/member body/marks/restore/confirmation methods, genuine stored-inspection proof, hashDraft/ConfirmationDraft, or transcripts OpenAPI producer surface is supplied by this import candidate. |
| Source G6: source metadata discovery input-OR-id query shape | TranscriptReadGraph and BoundedTranscriptMetadata are byte-identical B→C. New Task6 saved reader still aligns declared producer PK/unique-key and complete child sets per adopted design. This is not a new Task5 review finding. |
| Source pretransfer helper + same-hash extraction seams | JdbcTranscriptRows/StoredFactsIndex/StoredSourceGraph/TranscriptConfirmationHash/ArchivedProfileResolver/MinutesContracts unchanged B→C. Reuse baseline actual source-seal entries and approved extraction plan; candidate RAW-specific validator does not implement general Task6 stored acquisition. |
| Archive/catalog/minima/Task3 source request contracts | ConfigArchiveIndex,ControlledConfigArchiveSource,FileRegistry unchanged B→C. Earlier fixed caps/dimensional minima/source-byte vs stored-inspection split remain. Worker health is an execution observation, not a source or correction authority. |

The following 12 exact paths were compared B→C and are byte-identical. Their B preflight line ranges/hashes remain valid at C; future review changes still require repinning if affected:

- `backend/core/src/main/java/ama/minutes/contract/TranscriptConfirmationHash.java` — `bd4d709f659c29b8651e7ee82f7c5ade387dab3b4230302ad09780000dac274d`.
- `backend/core/src/main/java/ama/minutes/contract/StoredSourceGraph.java` — `32f04f856dc98080e73fa8346c46e757df3db7b60c7b809c43be6174e7ba7812`.
- `backend/core/src/main/java/ama/minutes/contract/ArchivedProfileResolver.java` — `9fe80570bd49c7211dc290ae78e759db6a265ca1b07af2cb59fe2c2ec4950b72`.
- `backend/core/src/main/java/ama/minutes/contract/StoredFactsIndex.java` — `d3a1feaa336e7abef93de75ff708e3e0d0aa6de1fdaa60f03ac797db7a9a1f7d`.
- `backend/contracts/src/main/java/ama/contract/MinutesContracts.java` — `a37aeb05d3ae8aa95434b34c324887891262b2e63515ca4a29771e8ed3ff2db9`.
- `backend/adapters/src/main/java/ama/transcript/JdbcTranscriptRows.java` — `58115736a3794ce07f97ae0cac57e240c2dde335308a14d2bace725a13a430f6`.
- `backend/adapters/src/main/java/ama/transcript/TranscriptReadGraph.java` — `1aad12427b0a296247e841c52d35ff0d8cffedbc4ea87b9efb277ffca33b1481`.
- `backend/adapters/src/main/java/ama/transcript/BoundedTranscriptMetadata.java` — `508c12c3a358d58560f9bf5d2216cbadb63c93bbcb42d23307d3d996130e663e`.
- `backend/core/src/main/java/ama/config/ConfigArchiveIndex.java` — `82d7cec3fee2ae2302f00c8e7475d1fca6c532e766830e9ea9971e866ec2de25`.
- `backend/adapters/src/main/java/ama/config/file/ControlledConfigArchiveSource.java` — `7aff267b9ff90b3f68b28a2cd93d8b20b7eb1a6f6e54fdf26a616e1584799e49`.
- `backend/core/src/main/java/ama/storage/FileRegistry.java` — `af70e18d8b3cf4fecd3f8d9337c343e3d3310c6ee7a9202105c7dc327c96dc41`.
- `contracts/openapi/api.yaml` — `0855b5d4a130a77aa88a341c1ab203a5843300d04f388d9a4bb1a5eb6ce38ad8`.

## Root activation refresh after independent Task5 review

This report saves lookup time only. Root first resolves actual reviewed/adopted Task5 commit. Compare final reviewed bytes against this C candidate's focused seams/manifest and repin changed files; unchanged 12-path seals can carry forward after byte comparison. Recheck actual queue registration/authority per-process composition, original parseBudget/aggregate/source lifetime, RAW allocation+same-call validation/terminal output, and actual API effective tuple/correction installation component. If reviewer changes only a bounded seam, do not restart a whole Task6 design review; update exact affected signatures/line ranges/hash/coverage.

Prior prospective Task6 test mapping remains: complete stored graph/zero marks/retained LONGTEXT locks+pretransfer dimensions through S/F/readback; same actual saved revision hashDraft/confirmed golden equality with full original archives; immutable fullcopy saves/marks/restore/CAS/replay/current confirmation identity; current/member/owner stored inspection vs original bytes; API lossless tokens/strict bounded parsing/no-store; correction component identity; real adopted LOCAL/S3 Worker RAW→Task6 loop/restart. This report does not execute tests, parse runtime receipts into acceptance, re-review Task5, or certify earlier author test totals.

## Full committed 51-source manifest binding

Exactly the candidate explicit-source-paths.json list, in original order. Every row was loaded from C and its digest matched candidate final-source-seal.json. Source/test/POM presence is not semantic review; no listed code/test was modified/executed here.

| Committed explicit path | Bytes | Committed SHA256 |
|---|---:|---|
| `backend/adapters/pom.xml` | 1823 | `efd4b63169be1a7e6b91f14485dfd931c5dd7d295983be38b292c1add2821712` |
| `backend/adapters/src/main/java/ama/db/TransactionRunner.java` | 1620 | `63c0a3600d1529fdafca64d7413dc511b8b3ea9a2311d98ecce68a386def846b` |
| `backend/adapters/src/main/java/ama/storage/ConfiguredBackends.java` | 5476 | `a6a2f929b539a6c9e790eed1c50c5d36e6ef0133e21b8dfab40078179c963128` |
| `backend/adapters/src/main/java/ama/storage/DeadlineInputStream.java` | 2736 | `b362d6de89b36ca99e65e1bbc665e1939023ba568508e4f3997dc1cd20f17be6` |
| `backend/adapters/src/main/java/ama/storage/LocalFileClient.java` | 7504 | `3b9bebf391f138bf2642a7c6b51d865a045bb31bd804006af1c935004f6c4f4d` |
| `backend/adapters/src/main/java/ama/storage/s3/RangeReader.java` | 4899 | `bdd1c99499565572c5f3b2436ec6392f737ff331363e8a2897c1f39aad69066f` |
| `backend/adapters/src/main/java/ama/storage/s3/S3FileStorage.java` | 9650 | `ae165322770d55a048571454085171c63a50aacb821b16ba581f7c8249a9ddc4` |
| `backend/adapters/src/main/java/ama/storage/s3/S3Objects.java` | 7458 | `e4f09f1481dba0af3ce8e123e58cfb58e8c1cb5a3f37ae00331e53325d47bd06` |
| `backend/adapters/src/main/java/ama/task/ConfinedTaskCompletionPolicy.java` | 456 | `80676bf76d4bc895a529b0d197fd5887eadf32594f3e34080ab581c68e66bc9e` |
| `backend/adapters/src/main/java/ama/task/JdbcTaskQueue.java` | 21398 | `05d9965f0d448d37295680288aeb46c79d3908fc943edb6955c10d0fec94bc64` |
| `backend/adapters/src/main/java/ama/task/TranscriptImportRuntime.java` | 14304 | `4056e73593bbc7769ea947083cfd82bb1c8581f109a605cc13330e7683094a4e` |
| `backend/adapters/src/main/java/ama/transcript/ConfiguredProcessing.java` | 2362 | `d35e12e2fcb30e830254844601003a9cc497ff0418482927a2b768b3f1afb475` |
| `backend/adapters/src/main/java/ama/transcript/ImportTaskDomainPolicy.java` | 14273 | `f1f75c031c0129ee0f3b9d9e5d737d4dca5c7cf0d72e359befa409ab20bab342` |
| `backend/adapters/src/main/java/ama/transcript/JdbcProcessingDiscovery.java` | 27820 | `b91a4c63af9d12ff4c3b5363d588fc2c729129f10439941ed11fefda87f40f36` |
| `backend/adapters/src/main/java/ama/transcript/JdbcProcessingFactsReader.java` | 17372 | `410331e9d9957440c459a1b3ac6558bbad510c82416fa6b57be75c08393e183a` |
| `backend/adapters/src/main/java/ama/transcript/JdbcTranscriptAuthority.java` | 38321 | `1d7b50ffe57a4116ad267f76f7b8c2e213d5b83622652a1d8cb14fa8a728765d` |
| `backend/adapters/src/main/java/ama/transcript/TextTranscriptParser.java` | 8514 | `f0d72108f191eb973077b0fedcb5f1e8ed6302b782b4f637c39c5c5ba832a1cd` |
| `backend/adapters/src/main/java/ama/transcript/WorkerHealthProbe.java` | 2917 | `2756105e6092f81e775da51cff4f944932ad37261e89811fb98b048836f25e98` |
| `backend/adapters/src/test/java/ama/config/json/ConfigBindingsTest.java` | 8480 | `a7742d7571a060e101592bd0ee316273f1050dc5406e2999828b545afdf123cd` |
| `backend/adapters/src/test/java/ama/storage/DeadlineInputStreamTest.java` | 1482 | `beafd52af8034b30f93f6009405d1d51295a700540160f0058d5a2008b127e26` |
| `backend/adapters/src/test/java/ama/transcript/MeasuredTextTranscriptParserTest.java` | 1759 | `d6249bb3fcc824de1cea692adea06fd81730503ada15481750d1c707f08a436c` |
| `backend/api/src/main/java/ama/api/FileConfiguration.java` | 2998 | `d2042b9a6fad67c0933d2fc9eb6b269fc924da8e65a44e25be6cd398fb7e6c69` |
| `backend/api/src/main/java/ama/api/ProcessingConfiguration.java` | 8384 | `ed895ba2e6e514e28bb687dc62497fa166f86c5961516afa1a9ec892754d9001` |
| `backend/core/src/main/java/ama/config/ConfigBindings.java` | 21843 | `79adef52e2fc6886b0a279d7272ab5e855ee5d6586b66e0dcd629b81035a3c0a` |
| `backend/core/src/main/java/ama/task/PreparationCancellation.java` | 308 | `a713a76cb5d005795b0e002f5ff758f8dfd6dc1127218c3db9694454ad81cd6f` |
| `backend/core/src/main/java/ama/task/PreparedCompletionEnvelope.java` | 318 | `ab7fb979030b39d6c48a1cab18773c3d75e733d33fc1590adaf1f34fd5c781db` |
| `backend/core/src/main/java/ama/transcript/MeasuredTranscriptDocumentParser.java` | 583 | `5331ac6c791c0c1040b6fa07d8b93f1ff738e53bf0fe6a6731b10c4f44145d7d` |
| `backend/integration-tests/src/test/java/ama/api/Task05RuntimeProbe.java` | 390 | `7ea9341fa94b2c4d7f26a977ad85a628aafd788a54d7b117adc2a967b9ffa568` |
| `backend/integration-tests/src/test/java/ama/storage/Task05AuthorityFixture.java` | 10553 | `603e49f60466dc5d58170f80c2e538b6c5029f2b6d343a5b6fc5c446d227ae45` |
| `backend/integration-tests/src/test/java/ama/storage/Task05LegacyAuthorityIT.java` | 48285 | `0ece458b2feadde0bc2b2b15ef9014baaa03d4c71cbe3a933cf721923e54dfa0` |
| `backend/integration-tests/src/test/java/ama/storage/Task05LegacyCatalogIT.java` | 8577 | `ae200cab57c9d2bb8a48bbd6f7b56f7890be601f4c6bd987bb241f40d082030c` |
| `backend/integration-tests/src/test/java/ama/storage/Task05LegacyUploadIT.java` | 65983 | `a71e149d3c501a31947cbbded13144906ad4bc4693a1ab16b8d71778d496b784` |
| `backend/integration-tests/src/test/java/ama/storage/Task05NativeFixture.java` | 9291 | `cd3fb88b42526f7768cae50e28e80bd513e421033a29d18a271d0aa9e05fb5f6` |
| `backend/integration-tests/src/test/java/ama/storage/Task05PipelineIT.java` | 39846 | `94dc632d4d478704c25fbde84ebf3aab4098a44f73f83a86464e06c71db88ea5` |
| `backend/integration-tests/src/test/java/ama/storage/Task05RegistryFixture.java` | 1650 | `4ff6f0313681f50d37493d8747bad927fd58661de349104125f4fa418743c655` |
| `backend/integration-tests/src/test/java/ama/storage/Task05RegressionResources.java` | 1163 | `5c2b4809731e10b8ed7b9e17012138e8f3f5ae290034554425b12f4e909d8fb8` |
| `backend/integration-tests/src/test/java/ama/storage/Task05TransportIT.java` | 4268 | `c390836928c5834079b6825c77788b1ec8497af1d42d7da0a483ab6c1724805e` |
| `backend/integration-tests/src/test/java/ama/storage/Task05WorkerProcess.java` | 2024 | `3fcddca6065996f589c0b3c69a3bfe8b57384e157e4d4c86d2e87c1bb9dfbde0` |
| `backend/integration-tests/src/test/java/ama/storage/s3/Task05S3DeadlineIT.java` | 3529 | `6926b4136f1a329f9d1776bb00412daca15dd65bf5ef2d920c19b3839385843c` |
| `backend/integration-tests/src/test/java/ama/storage/s3/WireProxy.java` | 12268 | `aabfd4954bb67eb4a2e60102595ad1d9076ef2d264222e734f6585a3446da88d` |
| `backend/integration-tests/src/test/java/ama/task/Task05AfterTaskIT.java` | 4795 | `dd1919ea092faa5eeaf7be61b74588442a8035e87b22a04cc4daabc69b986109` |
| `backend/integration-tests/src/test/java/ama/task/Task05AttemptIT.java` | 3290 | `8572246ea5be2eeb9b5faa05fe511755d5d14d643bdf81ce967884090c2e6a6a` |
| `backend/integration-tests/src/test/java/ama/task/Task05CancellationIT.java` | 3787 | `7e66376d9e8de9386444fafe8bbfb7bbde7bbe4d8980a44a36d6baa1cf8d9012` |
| `backend/integration-tests/src/test/java/ama/task/Task05LegacyPreparedCompletionIT.java` | 16897 | `c29dde8d718a4690fde6806e250c845c393c4d6c0bb949d78c86f46ac939a1df` |
| `backend/worker/pom.xml` | 574 | `b8d46fef3bc3ffbf0cb546d2d27bc01bb22ff14937488474ab6d1a427b4be24d` |
| `backend/worker/src/main/java/ama/worker/TranscriptWorkerMain.java` | 2319 | `eeb7ba5eb2dbb57606b42ee72a21f7395d53d2355d35a2ad6d0b1a710e086e2e` |
| `backend/worker/src/main/java/ama/worker/TranscriptWorkerRuntimeProvider.java` | 3303 | `5f1c9a5a9b5a3268dcb99a93f24cede2c1686e653a8db67e4220423896d6f677` |
| `backend/worker/src/main/java/ama/worker/WorkerHealthServer.java` | 2921 | `15f6900b98bf197a1a50be1b3ac578410073db654c822efa9a713c1c0576b0ec` |
| `backend/worker/src/main/java/ama/worker/WorkerLoop.java` | 13176 | `8a06a569ba19b109d3ecbb0217bcacb7c6c1c04c6136bb610441e2a23d19431e` |
| `backend/worker/src/main/java/ama/worker/WorkerMain.java` | 788 | `9c457478eb07efa7b51b11a316a4f0d18afcc8f8b225690a812061847a12fef2` |
| `backend/worker/src/test/java/ama/worker/PreparedWorkerLoopTest.java` | 8871 | `873e45dc4322ef385a2cc56979a9252303db15ed6f3d47280b9d22924208ce96` |

## Verification/custody seal

Independently recomputed all 51 explicit committed path hashes against the candidate final-source seal, all 18 focused file hashes/range lengths, and all 12 unchanged B→C comparisons. The two prior preflights retain their exact delivered SHA256 values (source alignment 5b94c4a0c6a71bb51b26e093e0260c88c2d79e19b74dd211b4124e9163be21e4; budget activation 1e1f6a325fe1d7321cc17edde963f92072d7edaed94e9d1db14a05a0be97dd85). Only this new report was written. No tests/runtime/private configuration were opened or executed. Candidate source remains under independent review and NOT adopted. Final report digest is delivered separately.
