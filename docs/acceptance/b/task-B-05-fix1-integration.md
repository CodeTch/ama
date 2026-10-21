# B05 fix round 1 consumer contract addendum

This addendum supersedes only the observation transaction boundary described in the initial sealed `task-execution-integration.md`. The original report, handbook, evidence and manifests remain historical immutable delivery artifacts. Fix BASE: `682a7bfaef45f6ed3fec38df99e85a7b78d450d7`. Independent scoped review/adoption remains required before enabling consumers.

## Observations own an independent short transaction

Call `ExternalOperationPort.observe(token, authoritativeObservation, prelockedEffects)` **outside every caller transaction**, after the external call/status query has returned. Ambient transactions are rejected before authority revocation, database changes or the callback. Do not wrap this method in a domain REQUIRED transaction.

The adapter itself owns the atomic local transaction: registered domain policy prelocks subject/root(s), then all discovered relevant tasks, operation and executions. The controlled callback, domain facts, task state and outbox commit together. Domain callbacks must continue to operate only on prelocked rows, never perform network I/O or acquire lower-order locks. This change does not restore old task or operation token business authority.

A token execution's zero-effect proof is read again with current execution locks. A verified old success contradicting a proof is recorded as `MANUAL_REQUIRED` / `QUARANTINED`; it never runs stale domain effects or grants another external send. If current execution changed since discovery, the entire attempt rolls back before fresh discovery. The next attempt has no previous locks, task set or REPEATABLE READ snapshot. New task locks are never acquired after retaining an old operation lock.

There are at most four discovery attempts per call. Exhaustion throws public `ObservationRetryRequiredException`; **this is not an acknowledgment and not STALE_TOKEN**. The caller must retain and resubmit the same already-authoritative observation outside a transaction, with its existing operation/execution identity. It must not call a remote create/send operation again, allocate a new external execution, synthesize zero-effect evidence or treat this exception as ordinary task retry. Handle this exception within the domain adapter/reconciliation handler so generic worker exception logging is not interpreted as acknowledgment. Existing framework workers do not invent a company observation persistence or resend protocol.

A subsequent submission of the same fact can quarantine the operation after generation churn stops. Existing stale-token/no-effect/quota rules remain: a stale token never regains domain writes, initial call authority or model admission; UNKNOWN never authorizes resend; NULL cutoff stays conservatively reserved.

## Multi-model configuration

`JdbcModelPermitPool.configure()` remains an independent administration transaction with no domain or operation locks. It materializes and locks the entire configured model set in sorted order before the first consistent reservation count. This prevents the count for A from establishing a REPEATABLE READ snapshot that misses B's admission committed before B's lock. Any rejected shrink rolls back the whole configuration update, including earlier models. Constructors still perform no database writes; disabled/local-unconfigured models and unproven cutoff semantics remain fail closed.

## Executable regression patterns

`ReviewConcurrencyIT` uses real MySQL connections explicitly set to REPEATABLE READ. Its delegating JDBC probe only coordinates latches at actual SQL/commit boundaries; it does not replace database results or sleep to guess ordering.

- A real B admission inserts its reservation and retains B's lock before commit. Configuration reaches B while that admission is uncommitted. After admission commits, the corrected complete-model prelock precedes both counts; two live reservations prevent capacity1 and both prior revisions remain intact.
- A success observer waits for a subject held by a zero-effect writer. The current locked proof causes quarantine after the writer commits.
- A success observer waits while a safe retry appends a new execution. The first observation transaction rolls back, fresh discovery locks both tasks before operation/executions, and the original fact is quarantined without a domain callback or new-send authority.
- Ambient observation rejects without effects. Four forced generation advances exhaust the bounded loop with the explicit retry-required error; resubmitting the same fact then quarantines it, without any external network call.

No SQL/migration/worker process implementation changed. Frozen V010 and all initial delivery artifacts remain unchanged. No database restart, fixture reset/repair/removal, company call or production enablement occurs in this fix round.
