# Task 1 independent review

**SPEC: PASS. Quality: APPROVED.** No Critical or Important findings. One optional test-determinism improvement is recorded below. This verdict covers ordinary prepared completion only; it does not approve Task 7, retention, external completion ownership, or overall Phase B.

## Reviewed scope and method

- Exact supplied range: `bd47aa0e1b24fa89fee5385860454f43dc0aecd7..cb92f1135bb304c2aec6a7f28e7530860c004710`, one commit, `feat(task): complete prepared database outputs atomically`.
- Verified review-package size 509828 bytes and SHA-256 `802dd11f898bee8446fafb87b58196f5a88428360543992a2c470ea1ab7b2b43`.
- Read the full task brief and author report, Task 1/global constraints, and applicable design decisions. Root-owned uncommitted R1 plan/spec changes are outside the source verdict; the dispatch explicitly leaves external ownership/retention to Task 7.
- Read commit/stat and all production/test/tool diff in passes: queue/API/integration tests; Worker/unit tests/fixture harness; preserved RED sources/evidence scripts and receipts. Log/source-hash inventories were queried directly. One tool output clipped the collector section, so that section was read again. No changed production/test file was recrawled: extra reads of JdbcTaskQueue and WorkerLoop were restricted to unchanged helpers or function context omitted from the diff.
- No Git command, suite rerun, source/index/branch edit, database operation, Docker operation, private-env read, or subagent was used. Only this requested review report was written.

## Specification checks and strengths

1. **Additive and fail-closed API.** `backend/core/src/main/java/ama/task/TaskQueue.java:17` documents DB-only, prelocked composition, no new authority, and no preparation/I/O/flush inside the callback. The default Supplier overload throws without invocation at line 24. Existing three-argument API remains intact; `PreparedWorkerLoopTest.java:90` verifies default rejection without effects.
2. **Fresh authority before callback; actual output afterward.** `backend/adapters/src/main/java/ama/task/JdbcTaskQueue.java:127` checks null lease/callback, invokes the existing `validLease`, obtains a nonnull actual result, validates its output, then calls the shared terminal helper. The authentic policy/identity/subject/root/task/current/input route remains in `prepareDomain` at line 45, `lockDomain` at line 56, and `validLease` at line 114. The Supplier cannot replace those checks. No post-callback current-version recheck rejects a legitimate domain advancement.
3. **Atomic terminal behavior without duplicated retry logic.** `JdbcTaskQueue.java:136` shares the original retry/finish behavior. Its old overload still validates before `Runnable.run()` at line 122. `PreparedCompletionIT.java:112` verifies a previously absent real output row, task output, attempt, audit and outbox commit together. Lines 124–140 and 165–173 exercise invalid output, callback throw, null result, audit/event/flush failure and rollback; rollback assertions start at line 103. The flush test checks sequence rollback too. Lines 191 and 201 cover legitimate domain advancement and caller-owned ambient rollback.
4. **Strict lease/input regressions.** `PreparedCompletionIT.java:143` onward verifies expiry, stale business version, revoked actual identity, changed actual input, cancellation epoch, forged owner/attempt and null arguments suppress effects. Line 161 explicitly preserves the old validation-before-effects contract. Existing strict B05 behavior is retained through the same helper and covered by the named inherited IT results.
5. **Explicit Worker mode and one completion owner.** `backend/worker/src/main/java/ama/worker/WorkerLoop.java:49` exposes an explicit prepared factory while retaining the original constructor. Preparation at line 111 happens before calling the queue; `completing` is set at line 118 before dispatch, so completion exceptions do not cause quarantine/second completion. The callback-entry guard at line 123 suppresses a failed heartbeat observed while completion waits for locks. Preparation exceptions/null callback retain the ordinary one-quarantine path. `PreparedWorkerLoopTest.java:18` onward verifies queue ownership, no early callback, prepared/ordinary completion failures, preparation failures and heartbeat suppression. `PreparedCompletionIT.java:211` additionally exercises the actual Worker plus JdbcTaskQueue/MySQL transaction boundary and single completion.
6. **Bounded lifecycle retained.** `WorkerLoop.java:156` shuts down scanners, heartbeats and workers, with a finite two-second worker wait per configured pool. New tests use try-with-resources and bounded latches. No new scheduler/provider or externally owned execution mode was introduced.
7. **Owned fixture and immutable migrations.** `tools/phase_b_remaining_owned_fixture.py:18` verifies the 12 existing SQL scripts against the authorized BASE before creation; line 23 refuses another fixture after owned metadata exists. The harness uses a new random name, the pinned image, loopback publication, bounded resources, private files and no reset/drop/repair path. The package contains no old SQL modification.

## Focused unchanged-code checks

- **Risk: Supplier bypasses genuine preparation or lock order.** Checked only the unchanged queue `prepareDomain`/`lockDomain`/`validLease` chain (`JdbcTaskQueue.java:45`, `:56`, `:114`). It checks registered policy, transaction-bound preparation, effects permission, subject/domain roots, actual task identity/attempt/owner/state/expiry/current fence and saved input before callback entry.
- **Risk: output/audit/task writes commit separately or flush early.** Checked `JdbcTaskQueue.java:26` and `backend/adapters/src/main/java/ama/db/TransactionRunner.java:21`. REQUIRED joins the same transaction; top-level queue work flushes after the action, and thrown failures propagate for rollback. Actual MySQL tests cover outer and ambient cases.
- **Risk: heartbeat state or executor lifetime invalidates the new mode.** Checked the omitted unchanged `WorkerLoop.java:142` renewal and `:148` quarantine/`:156` shutdown helpers. Failed renewal monotonically sets `lost`; quarantine is one guarded completion; close remains finite.
- **Risk: regression totals include stale reports or ignore subprocess failure.** Checked `tools/checks/run.py:13`–49. It removes the supplied old reports before execution and fails on subprocess failure or invalid/failed/skipped accounting. The executed collector supplies all pre-existing reports plus required IT reports; newly generated unit reports were subsequently inventoried as documented.

## Verification receipts checked

- `artifacts/phase-b/b-remaining/task-01/queue-red.log:346`: 10 tests, 5 failures, no errors/skips. Failure details demonstrate actual output validation before callback writes; the reflective old-API bridge is retained in `queue-red-test.java.txt:108`.
- `worker-red.log:158`: 6 tests, 4 failures, including two ordinary completions and callback execution after failed heartbeat. The old-mode bridge is retained in `worker-red-test.java.txt:15`.
- `heartbeat-race-red.log:150`: 8 tests, 1 failure; `:152` records callback count 1 instead of 0 after completion dispatch. This is the exact new regression case fixed by the callback-entry guard.
- All three preserved RED test-file hashes match their corresponding raw `.sources.json` entries. `setup-mvn-unavailable.log:1` is correctly separated as a launch failure, not business RED.
- `prepared-green.log:333` records 14 passing IT, with BUILD SUCCESS at `:353`; Worker units at `:269` pass at that intermediate stage.
- Final `regression.command.json:2` records the Python collector invocation and exit 0. `run-regression-executed.py.txt:6`–10 preserves the exact executed ten-IT Maven verify command and initial report discovery. `run_regression.py:11`–12 adds post-run report discovery for future runs; this accounting correction was not presented as another test execution.
- Independently summed the retained `regression-counts.json`: 30 unit suites/221 tests plus 10 integration suites/84 tests, 305 total, zero failures/errors/skips. Compared all 40 entries against the existing JUnit XML: zero discrepancies. Independently summed the 40 per-suite lines in `regression.log`: also 305. `regression.log:408` contains 18 PreparedCompletionIT passes, `:264` contains 8 prepared Worker passes, `:430` records 84 IT, and `:446` BUILD SUCCESS.
- Verified all six final source/tool hashes against current bytes, all five changed Java source/test hashes against `regression.sources.json`, and all 40 evidence-inventory file hashes: zero mismatches.
- `fixture/fixture-initial-zero.log:1` records zero initial tables and actual MySQL 8.4.6/native AIO off. `fixture/fixture-selected.log:1` records owned `ama-test-b01-ad9f1af29eff`, pinned digest, memory/swap 805306368, 1 CPU, 256 PIDs and loopback; `fixture/fixture-published-port.log:1` records port 58062. `fixture/history-after.log:1` and `regression.log:407` agree on all 12 successful migrations through V013 including 011.1. Before/after SQL receipts are byte-identical, and all 12 receipt hashes match current SQL bytes.
- Injected Worker failure warnings (`regression.log:259`–263) and the unchanged deprecated-API note (`:294`) are explained by the tests/unchanged source. They are not evidence of a hidden failed test and are not blocking findings.

## Findings

### Critical

None.

### Important

None.

### Minor

**M1 — Replace the sleep-based heartbeat handoff in the tests when convenient.** `backend/worker/src/test/java/ama/worker/PreparedWorkerLoopTest.java:64` and `:76` sleep 100 ms after the fake queue's `renewed` latch fires. That latch is released inside `Queue.renew` at line 106, before `WorkerLoop.renew` sets its `lost` flag. A heavily descheduled heartbeat thread can therefore let the test release preparation/completion before failure propagation, producing a timing-dependent false failure. Use a deterministic scheduler/test synchronization point that observes the renewal task's completion before releasing the callback gate. This is a test reliability improvement, not a demonstrated production correctness defect or a requirement to rerun the suite now.

## Limits and assessment

The review validates the supplied frozen package and retained execution receipts rather than reproducing the fixture execution. It does not independently re-query the live container/database, inspect private credentials, or prove absence of all historical machine-side operations. Public fixture commands and reviewed source contain no credential values; exact comparison against generated secret values remains the author's recorded check. No HTTP/S3 runtime, company integration, native-AIO/HA/capacity, external retention/ownership, or overall Phase B qualification follows from this task.

**Task quality: APPROVED.** The implementation is small, preserves old ordering, reuses the existing genuine authority/transaction machinery, and has substantive actual-MySQL rollback and Worker coverage. The retained raw logs, source bindings and corrected accounting support the reported Task 1 result; the optional timing-test improvement does not block this slice.
