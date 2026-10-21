# B-05 report — preparation checkpoint, NOT VALIDATED / awaiting ordered dependencies

2026-10-22. **PREPARATION ONLY, WAITING_FOR_REVIEWED_V007_V008_V009_AND_CENTRAL_V010_FREEZE.** Root requested return/release of the agent slot after preserving this checkpoint. Original B05 remains active work to resume later; no claim of task completion, migration acceptance, whole-stage completion, performance/NFR qualification, company model integration, or production activation.

## Exact checkout, commands and preservation

- Workspace `/home/tanren/ama/.worktrees/phase-b-tasks`, branch `codex/phase-b-tasks`.
- Original SQL-only checkpoint `daa1c3d12eced720a8679d340b3e029a49ac81f0` remains in history.
- Read the entire exact `task-B-05-resume-brief.md`, its actual-resumption override, B00 protocol/target-table definitions, register, independent original/fix/upload review records and DIFF-04—08/12—13. Programmatically loaded the complete design declaration/dictionary for target-column/constraint preparation; large bulk dictionary tool output truncated, so relevant B05/execution/outbox/quota definitions were reread in focused sections. Do not present bulk output as a second independent design audit.
- Merged exact reviewed root `9572b5a349a556d1f6092f8ed78337cfde76f27d` with `git merge --no-edit`; resulting own HEAD is **`7a5d5844e2047c2befa1acc8184d8d32ff64f5d8`**.
- No preparation-source checkpoint commit was created. `pom.xml` is modified; all new B05 source/test/module files are untracked and preserved. Nothing was pushed, cherry-picked into main, or independently reviewed. Later root `62a8ec5` adoption notice was received but not merged; own formal location currently contains only V001/V002/V005/V006.
- Before merge, archived all original Java/IT drafts as `.tools/b05-resume-preserved/pre-9572b5a-drafts.tar`, SHA256 **`a6eddc74b09a0de7012029910ead3139270d76f0de1943c384bcafaca0e9f8da`**. The old JdbcTaskQueue/JdbcModelPermitPool/TaskLeaseIT were later moved (not deleted) into `.tools/b05-resume-preserved/legacy/`, alongside before-refactor TaskContracts/TaskQueue/ModelPermitPool copies. The tar is the exact original source snapshot.
- Moving old noncompliant implementations out of active source prevents their implicit subject creation/locking JOIN/task-token external completion/unproven deadline release from being mistaken for the adopted implementation. Their full replacement is still required. There is currently **no active JdbcTaskQueue/JdbcModelPermitPool implementation and no canonical TaskLeaseIT**; the old draft test source and old RED evidence remain preserved for honest continuation.
- V005 bytes unchanged, SHA256 **`55cfdc4aabf169c82f30303ac4d03dce7f24b3c6af5b4cc3ebd0089ae7e7dda7`**.
- Original `.tools/b05-red.log` unchanged, SHA256 **`d9b5ad8e8afe75fa3a18f9fc5f689b50b6e95e03512c44fa55f712c72232cc75`**.
- Old owned fixture `ama-test-b01-8083a18e30db`, loopback46037, was not connected to, reset, migrated, stopped, or otherwise changed this preparation turn. Credentials remain ignored and were not printed. No new fixture was created, and no SQL was executed against any database.

## Stable consumer API proposals (not independent acceptance)

These are actual compilable source signatures reported to root for B03/B06/B08 preparation. They are not permission to enable a worker or consume an unapproved production integration.

### Subject fence

`SubjectFencePort`:

```java
LockedSubject create(SubjectRef subject, long businessVersion, long cancelEpoch);
Optional<LockedSubject> lock(SubjectRef subject, boolean skipLocked);
LockedSubject advance(LockedSubject locked, long businessVersion, long cancelEpoch, boolean deleted);
```

Every method requires the caller's actual transaction on the same DataSource. Domain creation inserts the fence before the root in that transaction; duplicate identity is rejected, never reinitialized. Caller locks subject before domain root. `LockedSubject` exposes subject/version/epoch/deleted; JdbcSubjectFences issues a private transaction/issuer-bound handle and rejects stale/foreign transaction handles. Advance is monotonic, deletion advances epoch, and tombstone revival is rejected. Constructor takes an explicit allowed-kind set; no implicit root or generic fallback exists. Domain-specific root/UUID authorization/identity rules remain the domain owner's responsibility.

### Outbox writer and final flush

`OutboxPort`:

```java
void append(OutboxContracts.EventDraft event);
void flush();
List<OutboxContracts.OutboxEvent> eventsAfter(long cursor, int limit);
```

`EventDraft(eventType, nullable taskId, SubjectRef subject, long subjectRevision, Reference reference)` and `Reference(kind, canonicalUuid)` carry no body/secrets. `OutboxEvent(sequence,event,occurredAt)` carries a DB-generated UTC microsecond timestamp. `JdbcOutbox(DataSource, Map<String,OutboxReferenceValidator>)` requires an explicitly registered domain validator per reference kind. Unknown references fail closed; validator must verify exact subject/version/immutable reference under the prelocked domain root. There is no dummy task UUID for domain events.

**Supported caller transaction pattern:** identity/idempotency→subject→domain/files/tasks/operations→audit→append event(s)→**flush last**→return. append only stages events. flush acquires singleton sequence, inserts the complete batch, updates next_id, and seals B05 context; B05 subject/task/outbox entry after flush rejects. Caller must not do further direct JDBC/domain writes or network I/O after flush. Parent root concern about assumed Spring ordering is explicitly addressed: this is an **explicit final flush**, not automatic beforeCommit allocation. Transactions producing outbox events may not register any other transaction synchronization. flush checks that only the internal guard exists; the internal highest-priority beforeCommit guard rechecks, rejecting a foreign callback registered after flush before that callback can execute. beforeCommit does not acquire DB/domain locks or run network callbacks. Missing flush fails commit. afterCompletion clears pending references and unbinds transaction-scoped state for rollback and success. Transactions requiring other framework synchronizations must use a separately reviewed supported composition, not silently assume registration order is safe.

`eventsAfter` is an internal ordered transport read, not a user-facing authorized resolution API. Browser/file/domain readers must reauthorize each subject/reference. It does not implement consumer ACK/lease yet.

### Revised task and external contracts

`TaskSpec(type,pool,subject,producerKey,TypedRef input,inputFingerprint,businessVersion,priority,cancelEpoch,maxAttempts)` now represents the adopted target. producerKey/fingerprint require canonical lowercase SHA256; TypedRef requires protocol kind and canonical UUID. It carries no body. TaskLease retains exact planned taskId/attempt/leaseOwner/expiresAt and immutable spec, truncating expiresAt to microseconds. TaskResult uses typed output plus safe error code; SUCCEEDED needs an output, RETRYABLE needs a diagnostic code. No producer/typed-input SQL is implemented yet.

`TaskQueue` retains enqueue/claim/renew/complete and completeWithEffects. Fence advancement and events moved to their dedicated ports, avoiding a second outbox/fence framework. Registered `TaskDomainPolicy` is the intended whitelist boundary:

```java
boolean lockRoot(TaskSpec spec, boolean skipLocked); // after subject, before task
void validateInput(TaskSpec spec);                  // immutable exact subject/version/fingerprint
void validateOutput(TaskSpec spec, TaskResult result);
void validateExternalRequest(TaskSpec spec, ExternalRequest request);
```

No B03/B04 business implementation was invented/copied. Actual approved domain adapters must implement and register these checks; unknown type/kind cannot authorize queue work.

`ExternalContracts` separates ExternalRequest(logical and execution hashes, fixed safe manifest) and OperationToken(operationId,executionId,executionNo,reconcileEpoch,owner,expiresAt,subject). It defines authoritative success/unknown/manual/zero-effect observations and proof categories NOT_SENT/DEFINITIVE_REJECTION/PROVEN_CUTOFF_FENCED. UNKNOWN/NOT_FOUND/timeout is not a zero-effect proof. Manifest content validation remains domain-owned; no generic arbitrary JSON acceptance path is registered.

`ExternalOperationPort` proposal (interface only): beginExternal(lease,request), claimReconciliation(owner), renew(token), observe(token,observation,prelockedDomainEffects), retryAfterZeroEffect(operationId,expectedExecutionNo,newTask,request). beginExternal explicitly requires no ambient transaction and a committed marker before call authority. Retry remains explicit user action and requires current durable zero-effect proof, with tasks locked before operation. Exact observation return semantics and authoritative proof validation still require implementation review; **do not freeze/enable external consumers from interface existence alone**. Old task lease is not an external result token.

`ModelPermitPool` retains planned unbound acquire(model,deadline) but it explicitly throws fail-closed because no execution token can authorize an actual call. The prepared overload is acquire(OperationToken,model,nullable safeDeadline). Permit carries exact executionId/configRevision and normalizes known deadline to microseconds. settled(permit) must verify exact execution terminal in committed DB, not trust a caller's declaration. No constructor automatically creates capacity; no implementation/real quota/cutoff verifier exists yet. Unknown cutoff must remain NULL/indefinite retention; no invented production capacity is present.

## Prepared source and tests

- New `JdbcSubjectFences`, `JdbcOutbox`, internal `TaskTransactionContext`; compiled only, **not behavior-validated against target MySQL**. Their DB-facing code was prepared under the explicit no-schema-execution gate before a runnable schema RED/GREEN cycle; this is candid preparation, not claimed strict TDD completion for those methods.
- `TaskMetadataTest` validates unsafe protocol-code rejection and preservation of vendor ID case/trailing-space identity.
- New separate `backend/worker` Maven module and root module wiring. WorkerLoop is an explicitly constructed lifecycle class, with bounded per-pool executors/slots, per-pool scanner threads, separate five-second heartbeat executor, conservative quarantine on handler exception and no built-in company handlers. It is not an API bean and has no autostart. This is scheduling preparation only: process bootstrap/production policy, operation-token heartbeat, DB reclaim/fairness/aging/backoff integration remain missing.
- WorkerLoopTest uses actual Java executors/latches and a test-only in-memory queue to test worker scheduling boundaries. It does **not** prove database lease semantics, model quotas, application restart recovery, or a production NFR.
- New `OutboxFenceIT`, **compiled but never executed**, expects the centrally frozen V010 already applied to a new explicitly owned fixture. It does not call migrator/reset/repair. It prepares real distinct transactions/threads for delayed lower-commit cursor inversion, sequence/domain rollback, missing final flush, foreign callback registration before/after flush, tombstone reinitialization/revival rejection, missing/unknown subject/reference rejection, and B05 API use after flush. It seeds only test-only actual adopted meeting/identity rows; production validators still need real domain implementations.
- Outbox consumer cursor/lease/epoch/continuous-prefix acknowledgement implementation is not yet present; do not claim DIFF-07 closed.

## V010 review-only preparation, NOT a formal migration

Local generator `.tools/b05-resume-preserved/build_v010_candidate.py` reads the adopted design declaration and frozen V005 shape; it writes only `.tools/b05-resume-preserved/V010-review-only.sql`. It never executes SQL or writes Flyway locations. Candidate is 210 lines/10 owned target tables. Current **review-only/nonfinal** candidate SHA256: **`db5d10fddb023333ff97583a1e259d4c706b2e430904891e3a8e1fc39899fa61`**. This is **not** a central final freeze request or acknowledgment, and has never been applied or passed to formal migrator.

Root's explicit compatibility ruling is incorporated into the candidate:

1. All writers/workers stopped during upgrade; accepted ordered history exactly001→002→005→006→007→008→009, no failed/unknown/higher history.
2. durable_task/task_outbox/model_reservation/model_capacity all provably empty before any permanent DDL. No fabricated producer key, typed input, external marker, deadline or quota. Nonempty/unknown stops with source intact for separately reviewed mapping/recovery.
3. task_outbox_sequence must contain exactly id=1,next_id=1. Higher cursor/extra rows are unaccounted history; never reset/rewind it.
4. Existing task_subject only exact binary MEETING rows whose actual approved V008 meeting exists, exact same revision/cancel_epoch and ACTIVE lifecycle. Unknown kind/missing root/stale/tombstone cases require separate explicit backfill; no inferred resurrection.
5. Temporary CHECK preflight guards occur before permanent DDL. Then minimal ALTER/drop of **empty obsolete columns**, exact target charset/collation/types/constraints/indexes; new task_attempt/external_operation/external_execution/outbox_consumer are owned V010 tables. No entire table drop, source-row deletion, frozen SQL modification, outOfOrder/repair/placeholder lower migration. Legacy FK auto-names/indexes are currently derived from frozen V005 and need actual metadata validation before finalization.

No V010 file exists in `backend/adapters/src/main/resources/db/migration/`. This avoids accidental formal application while dependencies are absent. A separate raw design sandbox remains possible but was not created/executed this turn. Before any first formal application: receive root's exact independently approved V007/V008/V009; confirm new own empty fixture; finalize source/metadata checks; send the **final** V010 SHA and **wait for central freeze acknowledgment**. Once formally shared/applied, no byte changes; any necessary forward delta needs a new centrally allocated version. The review-only candidate is not retroactive formal evidence.

## Actual commands / RED and GREEN counts

All Java commands ran from the own worktree with pinned runtime:

```sh
cd /home/tanren/ama/.worktrees/phase-b-tasks
source /home/tanren/ama/.worktrees/phase-a/.tools/env.sh
```

No DB environment script was sourced in this preparation turn.

|Exact command|Evidence log|Actual result|
|---|---|---|
|`python3 tools/checks/run.py unit TaskMetadataTest`|`.tools/b05-resume-metadata-red.log`|2 executed, 1 failure, 0 errors, 0 skipped; unsafe kind accepted by old SubjectRef|
|same command after protocol validation fix|`.tools/b05-resume-metadata-green.log`|2 executed, 0 failures/errors/skips; exit0. This was before the later DTO expansion/delegation; it is not a fresh rerun of the final DTO source. Later final compilation passed but metadata cases were not rerun after root requested stopping extra checks.|
|`python3 tools/checks/run.py unit WorkerLoopTest` with two-test empty worker stub|`.tools/b05-resume-worker-red.log`|2 executed, 2 failures, 0 errors/skips; handler never entered|
|same command after adding independent heartbeat test, still empty stub|`.tools/b05-resume-worker-red-3.log`|3 executed, 3 failures, 0 errors/skips; all fail expected entry assertions|
|same command after bounded WorkerLoop implementation|`.tools/b05-resume-worker-green.log`|**3 executed, 0 failures/errors/skips, exit0**; test elapsed5.281s, Maven12.226s. Final module production source and integration test compilation also succeeded. Worker run was already started before root requested returning checkpoint; only its completion/output was collected afterward.|
|`python3 .tools/b05-resume-preserved/build_v010_candidate.py`|console/local candidate|generation only, exit0; no SQL execution or fixture; not a schema qualification test|

Fresh worker XML: `backend/worker/target/surefire-reports/TEST-ama.worker.WorkerLoopTest.xml`. Metadata XML was removed by the later shared check runner's fresh-report cleanup; its exact RED/GREEN logs remain. Do not count earlier/stale XML as fresh execution. No Java tests were silently skipped in the selected groups; unselected tests were not executed.

Original pre-B00 real MySQL RED remains unchanged: canonical TaskLeaseIT11 executed, failure1/errors9/skips0 against minimal stub; contention expected1/actual0 was the assertion failure, nine tests had missing-feature `orElseThrow` errors; rollback test passed stub and is **not valid feature RED**. Original Maven verify also executed two existing unit tests. This preparation turn did not rerun it against the now-rejected old V005 design.

Per root's latest request, no extra all-suite, schema/IT, migration, OpenAPI or browser run was performed for this checkpoint. No source was committed as validated. This report does not claim the entire project unit suite passes.

## Self-review and exact remaining work

1. Receive reviewed full lower migration set before any formal V010. Root reports V007 independent Approved0 findings at62a8ec5; V008/V009 still forthcoming. Preserve all prior fixtures/history. Build and test refusal cases on new owned fixture, inspecting actual legacy FK/index names/types before freeze. Candidate guards do not yet constitute tested complete drift detection.
2. Implement new JdbcTaskQueue using bounded nonlocking READY/expired scans, subject→registered root→task locks with SKIP LOCKED/revalidation, producer full immutable-spec comparison, task_attempt history, explicit max attempts including crash, tombstone/version/epoch/fingerprint gates, finite safe retries and priority aging. No old implementation should be resurrected unchanged.
3. Implement stable logical operation/append-only execution marker, actual transaction commit/commit-ack-unknown semantics, independent token takeover/heartbeat/expiry, authoritative observation including old task expiry, stale sender contradictions/manual quarantine, safe zero-effect new-task/new-execution retry with all old/new task locks before operation and monotonic reconcile epoch. Never resend UNKNOWN. Define an observation return type that distinguishes rejected token from recorded external fact with blocked domain write; the current interface boolean is provisional and inadequate for consumers that need that distinction.
4. Implement quota configuration (explicit local authority + persisted revision/enabled; sorted model lock order), execution-token admission transaction operation→execution→model→reservation, NULL cutoff retention, verified microsecond cutoff, shrink floor at inflight, exact execution terminal settlement after business commit. No real platform/capacity/cutoff proof has been supplied, so company invocation remains disabled.
5. Implement durable outbox consumer lease/epoch and contiguous-prefix ACK with domain locks before consumer and effects/cursor atomicity. Validate strict final-flush supported pattern with actual MySQL competing threads/callback tests before declaring outbox safe. Direct JDBC after flush cannot be intercepted by this port and is expressly unsupported; domain usage/review must enforce it.
6. Supply actual B03/B04/B06/B07/B08 domain validators/adapters from their approved APIs, without invented formats or accepting arbitrary snapshots. Root creation and all mutations must use the same subject/root transaction and lock order; domain callbacks can modify only already-prelocked roots, no external I/O.
7. Complete separate worker process/bootstrap, current operation-token heartbeat, fairness/aging DB policy, bounded retry integration and reliable shutdown/recovery; no production handler registration/autostart now. Current unit worker behavior alone does not prove the20s lease/5s renewal/5s scan/30s healthy-DB recovery target.
8. Rewrite canonical TaskLeaseIT against approved ordered target, preserving the original RED limitations. Run meaningful real DB concurrency/locked-row progress/old attempt/version/epoch/tombstone, kill/paused-worker recovery, ambient marker rejection, marker commit/processkill, old origin vs reconciler, producer replay/conflict/crash cap, outbox inversion/rollback/cursor, unknown/cutoff precision/config capacity tests. No schema-method GREEN exists yet; OutboxFenceIT was only compiled. Run focused RED/GREEN plus affected current suite once after fixes, then root independent review before integration.

No child agents, company calls, credentials in source/output, main code writes, push, email/publication, real fencing, third project node, mandatory Redis/MQ, or global runtime changes occurred. Only this explicitly requested scoped coordination report was written outside the own worktree.
