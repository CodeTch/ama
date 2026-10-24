# Task5 fix02 independent scoped rereview — 2026-10-24

**SPEC: PASS within the assigned fix02 scope. Task quality: Approved.**

**T5SR01 residual: ADDRESSED. T5SR02 residual: ADDRESSED.** CV1 retains the prior **ADDRESSED** verdict and was not reopened or rerun. **0 Critical, 0 Important, 0 Minor findings** in the reviewed closure and fix02 changes. This closes the two residual source findings; whole-Task5 adoption and independent custody remain root's gate. This is not a whole-branch or deployment qualification.

## Scope and method

Reviewed fixed range `11eb54086da95c675862fcf4225821104aa8e87d..c943d15f4a2b9f294e4b7791fb51eb67d3acce5d`. Read HEAD independently and confirmed `c943d15f4a2b9f294e4b7791fb51eb67d3acce5d`. Read the fix02 brief/report and the complete previous review. The prior review is unchanged at 16201 bytes, SHA256 `e0b2b1cc0f57fd2dad1c3f5dfa616a604dad066a4af16820e979b258277b4d04`. Independently hashed the fix02 package: **90758 bytes**, SHA256 **`6759bef2dcf3b72226c10431cc64aefae619485e4a8962bb3f3cd5b241a28895`**.

Reviewed the complete owned 14-path backend delta. Read all three amended production files and the new/extended tests in full. For the seven forward-copied fixture/regression files, compared their complete contents against the corresponding fix01 files already read in full in this same review context and read every resulting difference. This recovered both the preserved fixture behavior and all new routing/instrumentation without treating an author's copy declaration as proof.

Reviewed paths:

- Product: `backend/adapters/src/main/java/ama/storage/FileBackendDefinitions.java`, `SourceReadLifetime.java`, and `ama/storage/s3/S3Objects.java` in the same main source root.
- Unit/lifecycle tests: `backend/adapters/src/test/java/ama/storage/FileBackendDefinitionsTest.java`, `SourceReadLifetimeTest.java`, and `backend/worker/src/test/java/ama/worker/WorkerShutdownTest.java`.
- Integration test/helper paths under `backend/integration-tests/src/test/java/`: `ama/storage/Task05Fix02AdmissionIT.java`, `Task05Fix02Fixture.java`, `Task05Fix02Maintenance.java`, `Task05Fix02NativeFixture.java`, `Task05Fix02S3OpenRegressionIT.java`, `Task05Fix02SuccessRegressionIT.java`, `ama/storage/s3/Task05Fix02Proxy.java`, and `Task05Fix02S3DeadlineRegressionIT.java`.

Outside that delta, reread only the actual `TranscriptWorkerRuntimeProvider.Runtime.close`, `WorkerLoop.close` and `ScopedS3HttpClient.OwnedRequest` context needed to verify the two composed fixes. The prior review's authorization and CV1 conclusions were not broadened into a new review of unchanged code.

No tests, DB calls, network/port probes, containers, private environment/PKI reads, resource operations, Git mutations, source/test edits, commits or delegation were performed. The only write by this review is this new report.

## T5SR01 — ADDRESSED

The previous gap was the real S3 semaphore wait before SDK request registration. `S3Objects.java:33–40` now routes its common acquisition helper through `SourceReadLifetime.tryAcquire`. Both ordinary request admission and the nested streaming admission use that helper. Their callers still start release ownership only after successful acquisition, so a rejected wait cannot enter an ordinary finally block that releases an unowned permit.

`SourceReadLifetime.java:48–65` preserves direct `Semaphore.tryAcquire(timeout, unit)` when there is no preparation scope. Within a scope it uses the smaller of configured admission time remaining and the original absolute batch time remaining. It checks the sticky aborted/closed state and waits at most 50ms before checking again. Thus real renewal loss can release the waiting preparation even when no HTTP request yet exists. A successful acquire is followed by a cancellation/deadline check; if cancellation wins there, exactly that newly acquired permit is returned and acquisition fails. If cancellation wins after the success check, the existing native-request registration still rejects/aborts the request and the established caller owns normal release. No new waiter thread, delayed interrupt of a reusable Worker thread, global transport-timeout reduction, source-selection capability, issuer, private carrier or completion grant was added.

The focused integration proof exercises the actual previously missing boundary. `Task05Fix02AdmissionIT.java:19–50` creates three genuinely accepted HTTP upload/intake tasks for distinct meetings and runs the registered queue with three contextual Worker preparations against one real S3 backend. The fixture explicitly configures admission 2. Two holder preparations stall in real control reads. The target then enters actual `TranscriptImportRuntime.prepare`.

At lines 64–79 the test observes the exact target thread inside `Semaphore.tryAcquire` and `S3Objects.acquire`, verifies actual selected source read entry outside the transaction, no retained DB root, zero available permits and both holder barriers unreleased. This is observation of the preparation's real stack, not merely a future timeout. The loss case expires the exact target's persisted running lease at line 81, leaving the actual heartbeat to produce known loss and invoke its registered abort.

Lines 82–89 assert actual preparation exit before either holder release, unchanged proxy request count, zero unowned permit release and no RAW/typed output for any of the three tasks. The loss case requires exactly one target Worker abort. Lines 92–104 close/join the Worker, assert the exact target thread is no longer alive, require precisely two available permits, acquire both and reject a third, and only then perform guarded fixture maintenance before proxy route shutdown.

Retained evidence proves the distinction from the old implementation:

| Admission observation | Old-source RED | Final-product GREEN |
| --- | --- | --- |
| One-second batch, 20000ms transport | Target still waiting after 3000ms | Preparation exits 668ms after admission-wait observation |
| Actual persisted renewal loss | Target still waiting after 8000ms despite one Worker abort | Preparation exits after 4295ms, with one Worker abort |
| Holder release counts at observation | 1 and 1 | 1 and 1 |
| Proxy request count | 150 to 150 | 150 to 150 |

The GREEN wait stacks include the new lifetime helper between the real semaphore and S3 admission; the RED stacks do not. RED's affected product hashes equal the exact fix02 base blobs. The passing traces and assertions establish no target transport while the permits remain occupied, actual prompt exit after known loss/the remaining deadline, and correct permit recovery after owner cancellation.

The target's test-only bounded gate after `prepare` exit is accurately disclosed: the exit latch proves source preparation ended; it does not claim the Worker thread had already terminated. The subsequent actual close/join and exact thread assertion prove termination. This gate prevents fixture re-claim during observation and does not create completion authority.

`SourceReadLifetimeTest.java:43–80` adds focused semaphore coverage: deadline rejection with no unowned release, known-loss wakeup with a clean reusable executor thread and cleared thread-local state, and cancellation immediately after a genuine successful acquire returning exactly that permit. All seven lifetime cases, including the final acquisition-race addition, are present and passing in final-unit XML. The inherited four lifecycle cases remain in that suite. Combined source and evidence close this residual without weakening the previously fixed admitted transport path.

## T5SR02 — ADDRESSED

`FileBackendDefinitions.java:14–28` now isolates each closeable backend's failure and continues through the complete list for checked exceptions, runtime exceptions and Errors. It preserves the first failure, excludes self-suppression, retains at most eight subsequent suppressed failures, and restores a preexisting interruption or a caught `InterruptedException` after the cleanup pass. The outer Runtime still attempts this pass after loop failure and its existing one-use close guard prevents repeating backend attempts on a second Runtime close.

`WorkerShutdownTest.java:24–76` uses two active actual contextual Worker executions and the actual provider Runtime, assembled through its private constructor solely for lifecycle testing. It now owns two closeable backend definitions: the first throws, the second records its close. Four cases cover a preceding execution-abort failure versus a backend-only first failure, each with normal and pre-interrupted close. Assertions require both cancellation actions, both backend close attempts once, exact first-error identity, backend suppression after an abort failure, termination of every owned executor, preserved interruption, no writer/quarantine, and no duplicate attempts on repeated Runtime close.

The retained old-source `red-multi-backend` XML has **4 failures**, each specifically reporting that the second backend close count was 0 instead of 1. The focused GREEN has **4 passing cases**; all four also pass in the final 240-unit run. This is the original multi-backend skip reproduced and repaired, rather than a replacement success-only cleanup fixture.

`FileBackendDefinitionsTest.java:10–22` additionally verifies all twelve callbacks are attempted, the first checked IOException is retained, an InterruptedException preserves the final interrupt flag, suppression stops at eight, and an initial Error cannot skip the second backend. Both cases pass in final-unit XML. Scheduling/storage doubles in these tests instrument lifecycle only; they are not claimed as business authority or real external-backend failure evidence.

## Evidence verification and source windows

Evidence root: `artifacts/phase-b/b-remaining/task-05-fix-02`.

Independently recomputed all **270 evidence-manifest entries**, including expected byte lengths and SHA256: **zero mismatches**. All 14 owned current source hashes match `final-source-seal.json`. Parsed retained XML independently and compared counts against actual command records, the command index and build summaries. The following are retained author executions, not reviewer test reruns:

| Run | Tests | Failures / errors / skips | Evidence meaning |
| --- | ---: | --- | --- |
| `red-multi-backend` | 4 | 4 / 0 / 0 | Exact second-backend skip assertion |
| `green-multi-backend` | 4 | 0 / 0 / 0 | Actual provider cleanup closure |
| `red-s3-admission` | 0 | 0 / 0 / 0 | Compile/setup failure; not business RED |
| `red-s3-admission-map-corrected` | 2 | 2 / 0 / 0 | Exact genuine admission non-exit assertions |
| `green-s3-admission` | 2 | 0 / 0 / 0 | Genuine saturated deadline and real renewal loss |
| `focused-admission-lifetime` | 6 | 0 / 0 / 0 | Earlier lifetime window before final race addition |
| `focused-backend-aggregate` | 2 | 0 / 0 / 0 | Checked/Error/bounded-suppression coverage |
| `final-affected-s3-regressions` | 4 | 0 / 0 / 0 | Admitted control-open, actual complete S3 pipeline, unscoped body/error/reuse |
| `final-all-units` | 240 | 0 / 0 / 0 | Final full backend source window |

The two selected integration runs contain **6 distinct class/method identities**, with no overlapping rerun sum. Both use the exact final product source. The full source-map differences for all nine runs match `source-windows.json`. In particular, `green-s3-admission` has only later test/helper additions, while `final-affected-s3-regressions` differs from final only in the final lifetime unit-test addition. Final all-unit source seal equals the final seal exactly. Focused lifecycle4/backend2/lifetime cases are already inside 240 and are not added again. No CV1, LOCAL/restart or unrelated cohort is claimed newly executed.

Inspected the actual admission wait/result/lifecycle receipts for both GREEN cases. They record preparation exit before Worker close, exact Worker close return and permit recovery, holder fixture release, guarded per-task maintenance, then proxy route close/thread join. This is consistent with the executed test ordering. Maintenance is kept separate from business cancellation/success. The genuine positive regression checks actual S3 HTTP-intake READY to Worker SUCCEEDED/RAW ordered content, original paragraph provenance, producer revision and unchanged saved pointer.

## Limits and historical qualifications

No unresolved Cannot-verify item blocks closure of the two assigned residual source findings. Verification is limited to source inspection and the retained executed evidence; this reviewer did not freshly execute tests or establish live resource state. The six passing integration cases and lifecycle tests are focused correctness evidence, not throughput, deployment capacity or exhaustive scheduling/race qualification. In particular, the late-acquire race is a deterministic real-semaphore unit probe; it is not represented as repeated adversarial real-network stress.

The evidence has no independent historical proxy-port field. Preserve the report's authorized fixture-config URI/managed-ID derivation chain and root's separate port/resource audit. No port was guessed, backfilled, contacted or rerun for reporting format. Private credentials, PKI and live custody remain outside this reviewer scope.

Preserve the setup and historical qualifications: the initial eleven-entry `Map.of` compile failure is not meaningful RED; the first lifecycle RED's standalone source reconstruction is explicitly post-run and hash-matched, not backdated capture; expected negative Worker logs and compiler warnings remain. Earlier fix01/original CV2 broader-inspect history, unbounded read-only javap inspections, earlier route-before-maintenance ordering and other disclosed failed setup windows remain unchanged. Root's independent custody verdict governs history/resource/private-exposure acceptance.

Both residual requirements are now supported by the actual final source and appropriately targeted negative/positive evidence. No further broad rerun or CV1 reopening is needed for this scoped source gate.
