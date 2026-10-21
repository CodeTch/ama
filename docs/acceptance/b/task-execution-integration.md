# B05 task / external execution / outbox integration contract

Status: implementation and local functional evidence submitted for independent review. This document does not enable production handlers, consumers, model calls, or company protocol adapters. The reviewed root for the entire owned diff is `0daecad85d348305c11982b5f0a7e2536a47434d`.

## Domain transactions and subject ownership

Use one actual Spring JDBC transaction and the **same DataSource instance** for TransactionRunner, identity/idempotency/domain/audit repositories, JdbcSubjectFences, JdbcTaskQueue and JdbcOutbox. A subject capability belongs to its issuing JdbcSubjectFences and actual transaction. Reusing it in another transaction or after completion is rejected.

The domain owner creates the subject in the same transaction, before inserting its root. Queue enqueue never creates a missing subject. Subject identity is `(kind, id)`; vendor identity strings retain case and whitespace. Immutable input/reference IDs are canonical UUIDs. No task rows with fabricated IDs are needed for domain events.

Actual public APIs:

```java
LockedSubject SubjectFencePort.create(SubjectRef subject, long version, long cancelEpoch);
Optional<LockedSubject> SubjectFencePort.lock(SubjectRef subject, boolean skipLocked);
LockedSubject SubjectFencePort.advance(LockedSubject held, long version, long cancelEpoch, boolean deleted);
void OutboxPort.append(EventDraft event);
void OutboxPort.flush();
List<OutboxEvent> OutboxPort.eventsAfter(long cursor, int limit);
```

Creation/edit transaction order is idempotency → sorted identities → subject(s) → meeting/roster/ACL/projection and relevant domain rows → task(s) → operation/execution if applicable → audit and staged events → **explicit `outbox.flush()` as the final operation** → return. For multiple subjects, acquire all subjects in canonical order before domain roots. Deletion advances cancellation epoch and installs a persistent tombstone; tombstones cannot be resurrected. Update the domain root and fence atomically.

`LockedSubject` is a capability for the subject row only. It does not claim that a domain root has been locked. The caller must acquire those locks. `TaskDomainPolicy.lockRoot(spec, skipLocked)` is the queue's explicit domain prelock extension: after subject, before task, it must lock all relevant roots/children in canonical order using the requested SKIP LOCKED behavior, and return false when the required root cannot be acquired. No locking JOIN is used by the queue.

A joined enqueue/completion transaction leaves final flush to the outer caller. A queue-owned transaction flushes itself. Do not perform another domain write or acquire another lock after explicit flush. The adapter rejects subsequent task/fence/outbox API use, missing flush, double flush and foreign transaction synchronizations (including synchronizations registered after flush). It cannot intercept arbitrary direct JDBC SQL issued by caller code; such SQL after flush and reverse-lock callbacks are outside the supported composition contract. No network call belongs inside a transaction or validation callback.

## Typed immutable references and reusable test pattern

Construct `JdbcSubjectFences(source, allowedSubjectKinds)`, `JdbcOutbox(source, Map<String, OutboxReferenceValidator>)`, and `JdbcTaskQueue(source, fences, outbox, Map<String, TaskDomainPolicy>)` explicitly. Unknown kinds/types have no generic fallback.

`EventDraft(eventType, nullableTaskId, subject, subjectRevision, Reference(kind, canonicalUuid))` contains no meeting body, credentials, or provider payload. A domain event uses a null task ID. `append` calls the registered reference validator immediately under the caller's already-held root; `flush` never discovers or locks a new domain root. The validator must verify that the immutable row exists and belongs to the exact subject/revision and registered event meaning. A foreign key alone is not provenance validation.

Task policy methods are `lockRoot`, `validateInput`, `validateOutput`, `validateExternalRequest`, and `validateExternalObservation`. Verify exact immutable input subject/version/fingerprint, output provenance, content-free versioned manifest and logical effect key. The default external-observation verifier throws, so merely registering ordinary work does not trust external proofs.

Executable reusable examples are `backend/integration-tests/src/test/java/ama/task/TaskFixture.java` and `OutboxFenceIT.java`. They insert actual enterprise_identity → task_subject → reviewed meeting → meeting_info_revision rows in the same transaction; TEST_INFO resolves the real immutable revision, and TEST_TASK validates meeting/revision/fingerprint. TEST_CALL / test-v1 manifests and TEST_VERIFIED_ZERO proofs are **test-only registrations**, not new company formats. Domain owners must supply their real adapters rather than copying these test allowlists into production.

An existing test fixture can demonstrate a domain-only event without dummy tasks:

```java
var spec = fixture.spec(); // creates real identity, subject, meeting, immutable info row
fixture.tx.required(() -> {
    fixture.fences.lock(spec.subject(), false).orElseThrow();
    fixture.policy.lockRoot(spec, false);
    // Domain change and audit are performed here under their already-held locks.
    fixture.outbox.append(new EventDraft("MEETING_CHANGED", null, spec.subject(),
        spec.businessVersion(), new Reference(spec.input().kind(), spec.input().id())));
    fixture.outbox.flush();
    return null;
});
```

This is a test pattern with actual fixture variables, not a production composition root. B03 creation/edit must also enforce its idempotency, sorted identity, roster, ACL and projection rules before final flush.

## Durable tasks and workers

`TaskSpec(type,pool,subject,producerKey,input,inputFingerprint,businessVersion,priority,cancelEpoch,maxAttempts)` is immutable. Producer key/fingerprint are canonical SHA-256 hex; producer replay compares the entire spec. Priority is an infrastructure scheduling value [-100,100], not a company role/quota policy. maxAttempts is explicit and finite, including crashed claims.

`claim(pool, workerId)` requires an independent transaction, scans bounded nonlocking pages, then locks subject/root/task with SKIP LOCKED and revalidates DB time/state/version/epoch. Lease duration is 20 seconds. Renew/completion check complete spec, attempt, owner and current DB expiry. Cancel/version/tombstone fences reject old work. Safe RETRYABLE work backs off 5/10/20/40 seconds plus 0..1000 ms jitter; unknown failures quarantine rather than authorize a resend. An abandoned PREPARED external execution that reaches the attempt cap records local NOT_SENT proof and remains in history.

`completeWithEffects(lease,result,Runnable)` runs only against already-prelocked domain rows; effects, task/attempt state, audit supplied by caller and staged outbox commit or roll back together. The Runnable must not acquire lower-order locks or perform I/O. Business code must classify retry-safe results; WorkerLoop does not infer safety from an exception.

`backend/worker` is a separate module. `WorkerMain` uses ServiceLoader and requires exactly one `WorkerRuntimeProvider`, whose `create()` returns `WorkerRuntime`. No production provider is shipped and the API never autostarts a worker. Explicit configuration supplies per-pool capacities and a type→handler map. WorkerLoop has separate bounded pool executors/scanners and independent five-second heartbeats; blocked handlers do not occupy heartbeat or other pool slots. WorkerRuntime permits an application provider to compose task and reconciliation loops explicitly. Test-only provider registration resides exclusively under integration-test resources.

`ReconciliationLoop(operations, capacity, handler, owner)` separately scans due operations every five seconds and renews their independent leases every five seconds. Handlers run outside the claim transaction, query authoritative status, and submit observations through the port. Exceptions leave MARKED/UNKNOWN recoverable for reconciliation and never enqueue another external send. `ExternalOperationHeartbeat` also supports an explicitly bounded in-flight call owner. No reconciliation token grants model admission/send authority.

## External execution and quota

`beginExternal(taskLease, request)` rejects any ambient transaction. It commits the stable operation, appended execution, marker, RECONCILING task and outbox before returning a token. If the commit acknowledgment is unknown, it throws and returns no call authority. Process death after the marker leaves the task excluded from ordinary reclaim.

Only the token object freshly returned by the same JdbcExternalOperations instance has local initial-call authority. Reconstructing a record or obtaining a reconciliation token does not acquire that authority. Model admission is separate and one-shot per execution; a duplicated acquire returns empty. Callers must never interpret an observation/reconciliation token or an empty permit as permission to resend.

`claimReconciliation(owner)` and `renew(token)` use independent short transactions. The current operation/execution number, reconciliation epoch/owner and DB expiry fence observations independently of the old task lease. `observe(token, observation, prelockedEffects)` returns:

- STALE_TOKEN: no current authority; ordinary business callback does not run.
- APPLIED: current external observation recorded; for terminal results, current domain effects may commit. UNKNOWN is recorded with no business callback and remains reconciling.
- RECORDED_FENCED: authoritative terminal fact retained, but version/epoch/tombstone prevents domain effects.
- QUARANTINED: a verified late success contradicts stored zero-effect proof; operation becomes MANUAL_REQUIRED and no further send is authorized.

`retryAfterZeroEffect(operationId, expectedExecutionNo, newTaskSpec, request)` only accepts verified definitive zero-effect failure. It creates a new task and PREPARED execution under the original stable logical business key, increments execution/reconciliation generations, and preserves history. Producer replay returns the previously accepted task only when full spec/request match. Timeout, NOT_FOUND, UNKNOWN, task expiry or disconnect are not zero-effect proof. Manual resolution requires a separately reviewed authoritative domain action; the framework invents no company reconciliation rules.

`JdbcModelPermitPool(external, explicitConfigurationMap, deadlineVerifier)` performs no constructor writes. `configure()` is an explicit administration action with sorted model locks, immutable revision semantics, and no shrink below live in-flight use. Unknown local configuration, disabled models, stale revision and unknown current execution fail closed even if another process populated capacity rows.

Admission/settlement are independent transactions with operation → exact execution → model → reservation lock order. Never invoke them while holding domain locks. NULL safe_deadline holds capacity indefinitely until proven terminal settlement. A finite deadline requires PROVEN_DEADLINE policy and a real DeadlineVerifier for server-enforced cutoff/fencing; all times normalize to UTC microseconds. Client timeout/cancel does not release quota. Terminal result business commit and exact historical execution settlement are separate short transactions. No actual company capacity, cutoff, credentials or handlers are configured here.

## Durable outbox consumers

Construct `JdbcOutboxConsumer(source, outbox, registeredPolicies)` and explicitly `register(consumerId)` without resetting its cursor. Claim/renew uses a 20-second lease and monotonically increasing epoch. `read(lease, limit)` returns the committed prefix. `apply(lease, events)` accepts a bounded continuous prefix only, validates actual stored events, prelocks domain rows through `OutboxConsumerPolicy.prelockDomains`, then locks consumer state and rechecks cursor/epoch before applying local effects and advancing cursor in the same transaction. Duplicate/stale/out-of-order acknowledgments cannot skip events. Flush remains last if effects append more events.

`applyToPrelockedDomains` must not perform network delivery or discover additional domain locks. Remote consumers must separately deduplicate external effects and use their reviewed delivery protocol; local cursor atomicity does not make a remote service transactional. `eventsAfter` is an internal transport API: a user-facing resolver still checks subject authorization before dereferencing content. No automatic history/outbox cleanup is enabled.

## Migration and local evidence boundary

V010 SHA-256 is `03e89cc8bd58d5a6aa0d4ab031b8fc05a4f7d2450623df147cd84d05b68977db`, Flyway checksum `-1187397378`. It requires reviewed lower 001,002,005,006,007,008,009 in order, stopped writers, exact lower metadata and conservative empty-legacy preconditions. Applied/shared bytes are frozen; corrections require a newly registered version, never repair/reset. B05SchemaIT is a historical oracle with a test-private target010 helper on an owned target-010 fixture; it neither resets nor downgrades an already newer database. Production SchemaMigrator remains latest and behavior tests use the actually deployed schema.

Evidence is under `artifacts/phase-b/b05-functional/` and the full B05 report. Functional containers used a process-local native-AIO-off override authorized for this host's exhausted AIO quota; shared helper/defaults were unchanged. Fixed-loopback restart and controlled 20-second lease tests qualify local SQL behavior, not production VIP/failover, 100-recording load or real provider fencing. Company adapters, quotas/cutoff and independent review remain release gates.
