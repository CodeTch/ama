# Task5 fix01 independent scoped rereview — 2026-10-24

**SPEC: ISSUES. Task quality: Needs fixes.**

**T5SR01: NOT ADDRESSED in full (HTTP paths fixed; S3 admission cancellation remains). T5SR02: NOT ADDRESSED in full (execution/executor cleanup fixed; multi-backend cleanup remains). CV1: ADDRESSED.**

Remaining findings: **2 Important, 0 Critical, 0 Minor**. They are residual composition paths of T5SR01 and T5SR02, not two additional unrelated findings. No separate fix-introduced Critical/Important/Minor regression was established in the reviewed diff. This verdict does not adopt Task5 or reopen Task1–4 or other unchanged implementation.

## Review boundary and independent evidence

Reviewed FIX_BASE `857102b917bbef77c38fae3211e3a65207a98c6c` through HEAD `11eb54086da95c675862fcf4225821104aa8e87d`; `f98980543415ba17f0102a9c85f49452c5611527` is the intervening record-only checkpoint. Independently read the fix brief, original source review, fix report and the 20 owned backend source/test/helper changes with final-source context. The seven product paths and thirteen test/helper paths are those in `artifacts/phase-b/b-remaining/task-05-fix-01/explicit-source-paths.json`. The original review hashes to `7da8043023ceb21053bafa5ae0157c80b93c9fc687c8bb6549f8b1cb1df319a9`. The review package is 117347 bytes and hashes to `56270809110665178ce8c4eacabba0c01de0832e04ac2b070d8d28b242ef7622`. HEAD was read and matches the reviewed commit. Truncated tool output was recovered through smaller source reads and compact structured extraction.

This review read retained public commands, XML, source seals, observations, source windows and the evidence manifest. It did not execute tests, connect to a DB/network/service, inspect private environment/PKI, create or change resources, change source/tests/HEAD/index, commit, or delegate. Its only write is this new report. Root performs the independent live resource, historical and private-exposure custody gate; those responsibilities are not converted into source claims here.

All 20 owned current source hashes match the final source seal. Independently recomputed all 340 entries of the public evidence manifest: zero byte/hash mismatches. These checks establish consistency of retained evidence, not a fresh execution of its tests.

## T5SR01 — NOT ADDRESSED in full: S3 admission waits still precede cancellation ownership

**Important residual. Primary changed location:** `backend/adapters/src/main/java/ama/storage/s3/ScopedS3HttpClient.java:26` (registration only when the SDK creates an `OwnedRequest`). **Necessary unchanged call-chain location:** `backend/adapters/src/main/java/ama/storage/s3/S3Objects.java:35` (`permits.tryAcquire(config.timeout().toMillis(), TimeUnit.MILLISECONDS)`), reached by `raw` at line 78 before `client.getObject` at line 81; `call` also acquires at line 54 before its action.

The actual open chain is `S3FileStorage.read:90–91` → `RangeReader.read:24` → `S3Objects.binding:162–168` → `scope/optional/get` → `raw/acquire`. Only after that semaphore admission succeeds does execution reach the new SDK request wrapper and register its native abort. Consequently a preparation waiting for an occupied S3 request permit has a `SourceReadLifetime`, but that lifetime has no active request to cancel. `SourceReadLifetime.abort:46–50` marks the scope aborted and runs the current registered requests; it neither wakes this semaphore waiter nor interrupts the preparation thread. `WorkerLoop.Execution.renew:137–144` likewise records loss and invokes the registered abort without interrupting that thread.

A concrete reachable case is a shared backend with admission 2 and three active genuine import preparations. Two valid preparations hold the permits in stalled control/data reads under their longer transport/batch bounds. The third waits in `tryAcquire`; its actual queue renewal then returns false. Its scope abort runs with an empty request set, but that third preparation remains blocked until another permit is released or the independent S3 transport/admission timeout expires. Thus known lease loss still cannot promptly release *this pre-open preparation*. Subsequent scope rejection prevents the HTTP request and final completion; the defect concerns resource lifetime, not unauthorized RAW writes. For scopes with a shorter remaining deadline than the permit holders, the same missing integration also leaves the deadline unable to wake admission.

This is a remaining boundary of the original T5SR01 whole-preparation cancellation requirement. The new HTTP wrapper correctly handles an admitted request, but the original source-open composition is not closed before the first blocking transport admission. The fact that `S3Objects.java` itself was unchanged does not make the amended composition satisfy that requirement.

**What is fixed and supported:** `TranscriptImportRuntime.prepareRaw:64–66` creates the deny-only lifetime before facts/archive/source preparation; it carries no source or completion authority. LOCAL registers before `sendAsync` and safely attaches its future and body, including the assignment/cancellation race. S3 registers before `ExecutableHttpRequest.call`, retaining registration through response-body lifetime. Authentic source selection and private completion gates were not weakened. No dedicated blocking body-reader thread was introduced.

The retained clean LOCAL RED cases fail specifically on preparation non-exit before fixture release. The S3 control RED fails on the analogous assertion. Final LOCAL3 and S3 control/error/header4 demonstrate the repaired admitted HTTP paths. `Task05FixOpenIT:65–80` and `Task05FixS3OpenIT:35–42` wait for the fixture's request/body barrier before the cancellation assertion; every S3 case uses one import preparation and therefore does not occupy all permits ahead of the target. The admission-reuse regression verifies permits after a completed abort, not a target waiting to acquire one during loss.

**Minimum closure:** make the existing deny-only cancellation/deadline govern S3 admission before waiting, preserving permit accounting, the genuine authorization/commit-before-I/O route and existing transport configuration. Add a focused meaningful RED/GREEN case that holds the backend permits with owned work, observes a genuine target preparation waiting before HTTP execution, causes real target renewal loss, and verifies target exit before releasing the permit holders. Also test the remaining batch bound at that boundary where it is shorter than the holders' lifetime. Assert no new target transport/output, no abandoned target thread, and no permit leak/over-release. Do not substitute a lower global timeout or a new source grant.

## T5SR02 — NOT ADDRESSED in full: a failed backend close still skips later owned backends

**Important residual. Primary changed location:** `backend/worker/src/main/java/ama/worker/TranscriptWorkerRuntimeProvider.java:44`. **Necessary unchanged cleanup dependency:** `backend/adapters/src/main/java/ama/storage/FileBackendDefinitions.java:14–16`.

The new runtime correctly attempts `definitions.close()` after a failed `loop.close()`. However, `FileBackendDefinitions.close` is a direct `for` loop over all retained historical backend definitions, invoking each `AutoCloseable.close()` without per-backend exception isolation. With two closeable owned backend definitions, a failure from the first exits this method before the second is attempted. The runtime catches the aggregate call's failure only after that skip, while `closed` was already set at line 41. Repeating runtime close returns without attempting the skipped backend. Multiple retained backend definitions are explicitly supported by the list-based production configuration; S3's backend close delegates to its real client at `S3FileStorage.java:224–225` and the close contract permits failure.

This retains the original failure mode one cleanup level below the newly fixed provider call: one owned close failure abandons other owned resources and repeat close cannot recover them. It is within the assigned best-effort backend cleanup requirement, not a general review of backend configuration or a newly introduced independent backend defect.

**What is fixed and supported:** `WorkerLoop.close:204–229` isolates each execution abort, shuts down all scanners/heartbeats/workers, uses one shared two-second join deadline, bounds suppressed errors and restores interruption. `Execution.cancel:132` detaches the abort under its monitor and calls it outside that monitor. The provider preserves the loop's first failure and attempts the definitions cleanup after it.

`WorkerShutdownTest:21–65` genuinely runs two active contextual preparations in the actual WorkerLoop and invokes the actual private provider Runtime through reflection. Its scheduling queue is synthetic and is correctly not presented as business authorization. The retained RED reports missing second cancellation/backend cleanup/executor termination; GREEN and the final unit XML pass both normal and interrupted cases. Assertions cover first-error identity, both aborts, no writer/quarantine, every executor terminated, repeated close and interruption. But this fixture has exactly one backend and that backend always closes successfully (`WorkerShutdownTest:38–45`); it cannot establish cleanup after a backend failure.

**Minimum closure:** attempt every owned backend's close even if an earlier one fails, preserving the original/first failure with bounded additional errors and safe repeated runtime close. Extend the focused actual-provider lifecycle test with at least two closeable backends: make the first throw and the second record closure; retain the throwing execution abort as appropriate. Assert both backend attempts, first-error preservation/suppression, terminated executors, no completion/quarantine, interruption behavior and no duplicate attempts on repeat close. No DB/network suite is needed for this lifecycle proof.

## CV1 — ADDRESSED

`Task05FixCarrierIT:12–30` prepares two genuine production private carriers through real HTTP accepted tasks and the actual registered `JdbcTaskQueue`/`TranscriptImportRuntime`. Their prepared source plans differ (`[Alpha, Beta]` and `[Beta, Alpha]`). Both cross-lease substitutions are rejected without any added RAW INSERT, while both original tasks remain RUNNING without output/RAW. An arbitrary public result supplier is also rejected. B then A completes using their original carriers; actual ordered bodies and the original meeting are checked. The fixture does not install a permissive policy or mint a private carrier.

`Task05FixCarrierIT:33–67` intercepts the actual private validator's first segment metadata SELECT. At that point the RAW and both new segments exist. The injected mutations change the first body to equal-length `Omega`, change the first source to the other genuine file, or shift the actual persisted ordinals by 10. `Task04ProbeSource:51–58` invokes this hook at the real JDBC statement boundary; its SQL still executes against the same source and active transaction. The actual `TranscriptImportRuntime.Invocation.validateActual:129–142` performs the rejecting checks. The test asserts mutation occurred, actual validator entry, absence of committed RAW/output, unchanged attempt ending/outbox and no imported audit, then successfully completes with the same original lease/carrier and checks original source IDs/ordinals. No new production mutation/testing grant was added.

The retained `cv1-genuine-carriers-and-validator` XML contains both passing cases on final product source. Three actual-validator receipts record BODY/SOURCE/ORDER rejection and same-lease recovery; the two-carrier receipt records both substitutions and the distinct plans. This closes the specific CV1 missing negative proof. It does not convert the test fixture into broader deployment evidence.

## Verification assessment and limits

Independently parsed retained XML, checked command exit records and terminal build summaries:

| Retained run | Tests | Failures / errors / skips | Meaning |
| --- | ---: | --- | --- |
| `red-shutdown` | 2 | 2 / 0 / 0 | Actual cleanup assertion failures |
| `red-local-batch-clean-fixture` | 1 | 1 / 0 / 0 | Genuine pre-open preparation did not exit |
| `red-local-renewal-clean-fixture` | 1 | 1 / 0 / 0 | Genuine pre-open loss did not release preparation |
| `red-s3-control-open` | 1 | 1 / 0 / 0 | Genuine control-open preparation did not exit |
| `green-shutdown-compile-corrected` | 2 | 0 / 0 / 0 | Repaired execution/executor/provider-call behavior |
| `final-all-units` | 233 | 0 / 0 / 0 | Final source; includes shutdown2 and lifetime4 |
| `green-s3-open` | 3 | 0 / 0 / 0 | Admitted control/error paths |
| `s3-before-headers` | 1 | 0 / 0 / 0 | Admitted request awaiting headers |
| `cv1-genuine-carriers-and-validator` | 2 | 0 / 0 / 0 | Genuine private carrier/validator negative matrix |
| `final-affected-regressions` | 7 | 0 / 0 / 0 | Relevant complete-flow/transport/lifecycle regressions |
| `final-local-lifetime-custody` | 3 | 0 / 0 / 0 | Final LOCAL body/header deadline and actual renewal loss |

The five selected integration runs contain **16 distinct class/method identities**, without overlapping reruns. All selected integration product hashes equal final product hashes. Their complete differences match `source-windows.json`; earlier S3/CV1/header evidence has later test-only changes. Final affected7 and final LOCAL3 have the final full backend seal. The final unit run's complete seal equals `final-source-seal.json`. Focused shutdown2 is already counted inside 233; finite fixture-maintenance1 is excluded from business counts.

Final LOCAL observation receipts show 1212ms and 1140ms for one-second bounds against 300000ms transport, and 5226ms for actual loss with one registered Worker abort. S3 observations show 1166ms/1197ms/1226ms for control/error/header one-second cases, and 5237ms for actual renewal loss with one registered Worker abort, against 20000ms transport. Every cited successful observation records release count 1 before fixture release. These are retained observed timings, not newly measured timings in this review.

**Cannot verify from supplied execution evidence:** the saturated S3 admission cancellation/deadline case and the multi-backend first-close-fails case were not executed by the supplied tests. Their failure paths above are established by the actual source composition; no new runtime measurement is claimed. The source and existing tests support one-shot registration/release and normal permit reuse, but the integration receipts do not directly instrument an exactly-once physical socket-release counter across every race. No conclusion of universal race coverage is drawn.

Preserve all historical qualifications: original CV2 full unformatted inspect before selected extraction; three read-only javap inspections without explicit JVM resource flags; failed compile/accessor/fixture-maintenance setup attempts; earlier route-before-maintenance chronology; post-run RED source reconstruction; first glob-expanded final-unit launcher and first dynamic-port comparison failure. None is relabelled a business RED or a clean historical pass. Root's separate custody audit is the authority for resource/history/private-exposure disposition. The review does not rerun those mechanisms.

## Named checks outside the owned source list

Only the following dependencies were followed to resolve the assigned findings or specific proof:

1. `S3Objects.acquire/raw/call/binding/scope`, `RangeReader.read` and `DeadlineInputStream` for pre-open admission, actual stream ownership and abort/release composition.
2. `FileBackendDefinitions.close` and the S3 backend close delegate for the provider's owned backend cleanup boundary.
3. `Task04ProbeSource` for the genuine JDBC interception point and actual validator-corruption test semantics.

These reads do not constitute a review of unchanged implementation generally. The two residual findings block closure of this fix round; CV1 requires no additional broad rerun.
