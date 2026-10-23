# Task 1 implementer report

Status: implementation and bounded verification complete; awaiting root independent review. No overall Phase B completion claim.

## Source and scope

BASE: `bd47aa0e1b24fa89fee5385860454f43dc0aecd7`.
Branch: `codex/phase-b-completion-20261023`.
Worktree: `/home/tanren/ama/.worktrees/phase-b-completion`.
Commit: `cb92f1135bb304c2aec6a7f28e7530860c004710`; final source hashes are in `artifacts/phase-b/b-remaining/task-01/final-source-sha256.json`.

Owned changes only:
- `backend/core/src/main/java/ama/task/TaskQueue.java`: additive default-rejecting Supplier completion overload; documents DB-only/prelocked callback and absence of independent authority.
- `backend/adapters/src/main/java/ama/task/JdbcTaskQueue.java`: validLease (authentic preparation, identities/root/current fence, actual task owner/attempt/expiry and input) precedes Supplier; actual returned output validation follows effects; shared completeValidated helper retains retry/terminal/outbox behavior. Null callback/lease/result rejected. Existing Runnable validates before invoking effects.
- `backend/worker/src/main/java/ama/worker/WorkerLoop.java`: explicit PreparedHandler + prepared factory; preparation runs outside completion; queue owns Supplier. Heartbeat loss suppresses dispatch and is rechecked at callback entry if queue waited for locks. Completion exceptions are logged without a second completion attempt; preparation/ordinary handler exceptions retain one quarantine completion. Existing constructor stays compatible.
- Focused tests `PreparedCompletionIT.java`, `PreparedWorkerLoopTest.java`.
- New `tools/phase_b_remaining_owned_fixture.py` and new evidence directory only.

No external observation/retention implementation, new DDL, production task policy, or other domain/source changes. Root-owned plan/spec edits observed in status were left untouched and excluded from staging. No subagents spawned. Existing task fixture types reused only as test utilities; production classes carry no test helpers.

## Behavioral evidence

Queue actual MySQL tests prove previously absent real meeting_info_revision output is written then read/validated before task terminal state; output/task/attempt/audit/outbox commit together. Invalid output, thrown callback, null result, audit insert failure, event validation failure, and final flush failure roll output/task/attempt/audit/outbox back. Ambient transaction performs the final flush; later caller failure still rolls everything back. Null callback/lease rejected. Expiry, stale version, cancellation epoch, revoked real owner identity, forged owner/attempt and changed actual input suppress effects. Callback may advance its prelocked meeting/subject version after initial validation. Existing Runnable still rejects absent output before calling effects.

Actual WorkerLoop plus JdbcTaskQueue/MySQL verifies preparation without an active transaction, callback with an active transaction, actual output persistence, exactly one prepared completion and zero ordinary completions. Scheduler unit tests cover one queue-owned callback, preparation/null-callback failure quarantine, default API rejection for legacy queue implementations, heartbeat loss during preparation and after completion dispatch, and no second completion when ordinary or prepared completion throws. Existing bounded pool/independent heartbeat tests pass. All worker instances/executors are closed; existing process-recovery tests use their bounded subprocess cleanup.

## RED / GREEN and exact commands

All captured commands execute from the worktree using the new private fixture env only and public read-only JDK `/home/tanren/ama/.worktrees/phase-a/.tools/zulu21.52.203-ca-jdk21.0.12.1-linux_x64`. No old environment was sourced. Repository `mvnw` pins Maven 3.10.0. Secret-free argv/cwd/JDK/private-env-path receipts and source hashes accompany each log. `evidence-runner.py.txt` preserves the runner source. `.tools/phase-b-remaining/run.py` launches the following commands:

1. Setup failure before tests: attempted `mvn -B -pl backend/integration-tests -am -DskipTests=false -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=PreparedCompletionIT -Dfailsafe.failIfNoSpecifiedTests=false verify`; mvn absent on PATH. No Maven process/test ran. Preserved `setup-mvn-unavailable.log`; corrected to canonical pinned wrapper. This is not business RED.
2. `python3 tools/checks/run.py it PreparedCompletionIT`: queue-red.log, 10 tests / 5 expected failures / 0 errors / 0 skipped. Test-first bridge used the old 3-argument API while Supplier overload was absent, explicitly reproducing output validation before writes; the direct Supplier call replaced this bridge after implementation. Exact RED test preserved in queue-red-test.java.txt.
3. `python3 tools/checks/run.py unit PreparedWorkerLoopTest`: worker-red.log, 6 tests / 4 expected failures. Test-first bridge selected existing ordinary Worker when prepared factory absent. Observed early callback, callback despite lost heartbeat, missing queue-owned mode, and 2 ordinary completion attempts on throw. Exact RED test preserved in worker-red-test.java.txt.
4. `python3 tools/checks/run.py it PreparedCompletionIT`: prepared-green.log, 14 IT pass; reactor also ran all units, including 9 Worker tests. No failures/skips. First actual Worker/MySQL proof.
5. `python3 tools/checks/run.py unit PreparedWorkerLoopTest`: heartbeat-race-red.log, 8 tests / 1 expected failure: heartbeat loss after completion dispatch still invoked effects. Exact RED source preserved in heartbeat-race-red-test.java.txt. Added callback-entry guard.
6. Final `python3 artifacts/phase-b/b-remaining/task-01/run_regression.py`: regression.log, exit 0. Full underlying command:
   `./mvnw -B -ntp -pl backend/integration-tests -am -Dit.test=PreparedCompletionIT,TaskLeaseIT,B06QueueSeamsIT,ExternalExecutionIT,ModelPermitIT,OutboxConsumerIT,OutboxFenceIT,ReconciliationWorkerIT,ReviewConcurrencyIT,WorkerProcessIT -Dfailsafe.failIfNoSpecifiedTests=false verify`
   Fresh JUnit XML: **221 unit + 84 integration = 305 tests**, 40 suites, 0 failures, 0 errors, 0 skipped. PreparedCompletionIT 18; PreparedWorkerLoopTest 8; inherited WorkerLoopTest 3. Per-suite counts in regression-counts.json.

The final regression invokes canonical tools/checks/run.execute for fresh-report deletion and fail-closed execution. Its initial expected-report set (after prior targeted unit run) reported 92 = 84 IT + 8 prepared Worker tests, while Maven verify actually executed every unit suite. Post-run inventory of all fresh XML established the 221/84 totals without rerunning tests. Collector was corrected to discover all generated reports for future runs; exact executed collector preserved as run-regression-executed.py.txt. verification-summary.json records this accounting distinction. No test result or original failure log was rewritten.

Expected Worker warning logs on injected completion failures and inherited compiler deprecation notes remain preserved; they do not represent unreported test failures. `git diff --check` passed. Self-review examined all owned production diff and tests; retained shared terminal helper, old semantics, no post-callback version fence and no source changes outside ownership.

## Fixture custody and immutable migration receipt

New fixture only: `ama-test-b01-ad9f1af29eff`, `127.0.0.1:58062`, database `ama_b01_fixture`, owner label `ama.remaining.owner=remaining-20261023`.
Pinned image: `mysql@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9`.
Actual server 8.4.6, native AIO OFF; memory and swap each 805306368 bytes, CPU 1000000000 NanoCPUs, PID limit 256. Exact container ID/image ID/mounts/loopback binding are in fixture/fixture-selected.log. Initial 0 tables confirmed before migrations.

Private env PATH ONLY: `/home/tanren/ama/.worktrees/phase-b-completion/.tools/phase-b-remaining/env.sh`; directory 0700, credential/env files 0600. New owned metadata path `.tools/phase-b-remaining/owned.json`. Credentials never printed or committed; evidence checked against both generated passwords. Public harness never reads an old fixture/env and refuses recreation after owned.json exists. Root received identity/custody message for later reuse. Container and all data retained; no reset/drop/repair/outOfOrder/restart or old fixture operation performed.

Only 12 unchanged formal scripts applied in existing Flyway order: 001,002,005,006,007,008,009,010,011,011.1,012,013. Before SQL hashes verified byte-for-byte against BASE; pre-test full applied history printed before first test; before/after hashes identical; after history and actual version/AIO recorded. All repeated SchemaMigrator validations reported no pending migrations. No new formal schema created.

## Limits / review notes

This proves Task 1 only under controlled local MySQL, including actual Worker behavior and inherited process crash/recovery regression. No HTTP/S3/production policy/runtime consumer/HA/native-AIO/capacity qualification is claimed. B05SchemaIT was excluded because it intentionally targets/asserts the historical V010 exact schema. DatabaseRecoveryIT was excluded because it restarts a container and reads raw docker inspect; no fixture restart qualification is claimed. Repository-wide web/contract/all pipeline was outside this task and would write historical artifact paths. Affected full Java unit reactor and the named safe IT regression ran once after the final functional change.

Supplier remains trusted bounded composition under registered authentic policy; it is not a sandbox for arbitrary callbacks. External completion ownership and stale-candidate retention remain Task 7. Root independent specification/quality review remains required before acceptance.
