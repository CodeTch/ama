# Task 8: FUP-B05-CANDIDATE-01 bounded reconciliation fairness design — R1

Status: DESIGN ONLY; independent review and root adoption required. No implementation, tests, database/Docker operation, private environment access, or Git mutation. This correction authors only the new R1 design and its closure note. Date: 2026-10-23.

Baseline: `66e70fdeef286a38592395ce94bdfbb1a93bce4a`. Observed HEAD during inspection: `efcbf78f1dcff6b708df4cd2d423885dcf23de91`, identified by root as docs-only design adoption. Read-only `git diff --name-only 66e70fdeef286a38592395ce94bdfbb1a93bce4a -- backend/adapters/src/main/java/ama/task backend/core/src/main/java/ama/task backend/worker/src/main/java/ama/worker/ReconciliationLoop.java backend/integration-tests/src/test/java/ama/task backend/adapters/src/main/resources/db/migration/V010__reliable_execution.sql` returned exit 0 with no paths. Task 2 review/Task 3 implementation remain outside scope.


Revision R1 addresses only T8DR-01 resource accounting/proof/tests from `task-08-fairness-design-review.md` SHA256 `7c78bf8608ff0019bf45c21174badb6e40d83a547ba1f5d16d223f2616a99120`. Original design SHA256 `309f7513f6c2b64db67b4469ebac7ba028c38dcc54c2c070d765bd7e02248d74` remains unchanged. This is a prospective design correction, not a passing implementation trace. Root has not adopted the ordinal design; independent scoped re-review and the existing allocation/freeze/ACK gates remain pending. The original report's inspection record below describes that original round; this correction round additionally reads TransactionRunner and verifies the source accounting without executing tests or Git commands.
## 1. Scope and actual source

Task 8/Global constraints require bounded cross-call progress, no artificial due-time changes, registered genuine task preparation and identity-first locking. `docs/acceptance/b/remaining-preflight/b07-b08-preflight.md:47` requires complete facts; line 49 specifies preparation/lock order, and its B05 integration section explicitly keeps first-64 fairness OPEN. Task 7 protocol design lines 176-178 describe separate permit-settlement recovery; lines 237-245 retain Task 8's separate activation gate. Root places implementation Task 8 after Tasks 3-6 and before Task 7 external Worker activation. This report does not implement permit recovery.

Repository-relative source anchors:

| Source | Relevant fact |
|---|---|
| `backend/adapters/src/main/java/ama/task/JdbcExternalOperations.java:66` | Repeats `ORDER BY next_check_at,id LIMIT 64` on every call. A held/unregistered prefix can permanently exclude the tail. |
| Same file, lines 21-29,55-64 | Actual operation/execution/task discovery, registered domain preparation, then task/operation/execution locks. Operation projection lacks next_check_at; fresh due-time recheck needs adding. |
| Same file, lines 30-53,77-79,134-144,147-173 | Marker/renewal/observation/explicit zero-effect retry own genuine state/time changes. Initial operation ID is UUID; retry reuses operation. |
| `backend/adapters/src/main/java/ama/task/JdbcTaskQueue.java:26` | Owned work transaction, authentic transaction context, outer flush last. |
| Same file, lines 45-67 | Complete TaskSpec family, issuer/source/holder/purpose and effects checks precede subject/domain locks. |
| `backend/core/src/main/java/ama/task/TaskDomainPolicy.java:9` | Claim uses TASK_MUTATION; EXTERNAL_FACT_OBSERVATION is a separate receipt purpose. |
| `backend/adapters/src/main/java/ama/task/JdbcTaskPreparations.java:12` | Opaque token binds exact issuer, DataSource object, actual transaction holder, complete specs, purpose. |
| `backend/adapters/src/main/java/ama/task/JdbcSubjectFences.java:34` | Subject supports FOR UPDATE SKIP LOCKED after preparation. |
| `backend/worker/src/main/java/ama/worker/ReconciliationLoop.java:38` | Repeated five-second poll after empty; operations instance persists; handler only after committed claim. |
| `backend/adapters/src/main/resources/db/migration/V010__reliable_execution.sql:113` | ID char(36) ascii/ascii_bin; PK id; due index `(state,next_check_at,id)`; marked_at/next_check_at datetime(6). No immutable monotonic operation ordinal. |
| Same migration, lines 187-209 | Execution has created_at, unique operation/no and task_id, no monotonic global ordinal. |
| `backend/adapters/src/main/java/ama/task/JdbcOutbox.java:35` | Commit-serialized outbox sequence exists, but transport events are not schema-guaranteed complete enumeration of legacy operations. |

## 2. Decision and alternatives

Add an immutable database-generated scan ordinal to external_operation. Keep one bounded in-memory cursor per JdbcExternalOperations instance. Fix an upper ordinal at each pass start; discover raw ordinal pages INCLUDING terminal/non-due rows and test eligibility separately. Consume/advance every row, including held/unregistered/rejected rows; return at most one token per call.

Enumeration becomes cyclic immutable insertion order. next_check_at stays an eligibility gate; no API promises oldest-due priority. This trades scanning terminal history for a finite membership proof, an index range bound and no durable cursor mutex. Do not add a priority lane that can consume the entire budget and starve this sweep.

- OFFSET or a larger fixed number of pages still shifts under removals/claims/arrivals. Restarting repeats the same blocked prefix; persistent offsets alone are also unsafe.
- `(next_check_at,id)` keyset has reproducible SQL comparison (datetime(6), ascii_bin) but mutable next_check_at. Renewals move keys ahead; earlier due changes move behind. Its watermark does not freeze membership.
- Pure UUID/id keyset is stable but constant arrivals can interpose before a target. The finite UUID universe is not a useful operational bound.
- Existing operation has marked_at, not created_at. Retry leaves marked_at unchanged; execution/task created_at could be joined, but execution changes and extra joins do not solve wall-clock membership. datetime(6) is not allocation/commit order. Equal timestamps, clock rollback, backdating and long uncommitted inserts require assumptions absent from the schema. A timestamp scheme is insufficient without new monotonic-clock/producer guarantees.
- Outbox EXTERNAL_MARKED is atomic for normal beginExternal (line 50), but legacy coverage/indefinite retention is not guaranteed by the transport contract. Filling missing events would fabricate audit facts. A transport-fed index still needs complete legacy enumeration.
- Sidecar `(scan_seq AUTO_INCREMENT, operation_id UNIQUE FK external_operation.id)` is viable, but every initial marker needs same-transaction child insertion after operation and before outbox. Old writers would otherwise produce invisible rows. Legacy coverage backfill must enumerate ALL operations, not just registered/due ones, before activation. FK/unique locks and one-to-one lifecycle add complexity. A column adds no child/FK lock and old INSERTs receive metadata automatically. Neither confers authority.

## 3. Forward schema, legacy rows and migration gate

No migration number is assigned; no SQL file is created in this design task. Future candidate belongs in `W/UNASSIGNED-task08-reconciliation-scan.sql`, outside formal resources, until independent review, root allocation/freeze and apply ACK. Proposed exact additive shape:

```sql
ALTER TABLE external_operation
    ADD COLUMN reconcile_scan_seq BIGINT NOT NULL AUTO_INCREMENT,
    ADD UNIQUE INDEX external_reconcile_scan (reconcile_scan_seq);
```

Retain id PK and every prior field, collation, index, constraint, row and history checksum. No existing AUTO_INCREMENT column conflicts. Signed BIGINT matches Java long; generated positive allocation fails at exhaustion, never wraps. No CHECK referencing AUTO_INCREMENT is proposed. Application INSERTs omit the column; UPDATEs never assign it. Reject nonpositive/overflow metadata as integrity failure. Restore/import preserves values and resumes allocation above the maximum; this is metadata integrity, not a product quota.

Existing rows receive metadata in unspecified order: explicit enumeration backfill ONLY. It does not promote rows, mark them due, register policy, create task/execution/result or infer authority. Verify every old row has a unique positive ordinal and all previous columns are byte-identical. Root must inspect actual MySQL ALTER/rebuild/lock behavior and migration ordering before applying. No online-availability claim. New owned fixture first. Preserve V010 and exact B05SchemaIT historical oracle; run that target-010 oracle on a separate NEW target-010 fixture, never on the forward database.

New code requires the migration; missing schema fails visibly, with no first-64 fallback. Old marker INSERTs automatically allocate metadata after adoption. Immutability is a repository write invariant, covered for begin/retry/renew/observe, not a new general anti-admin trigger.

## 4. Concrete interfaces/state

Keep ExternalOperationPort.claimReconciliation(String owner) and OperationToken unchanged. Add adapter configuration:

```java
public record ReconciliationScanBudget(int pageSize, int maxRowsPerCall) {
    // Both positive; pageSize <= maxRowsPerCall; reject invalid config.
}
public JdbcExternalOperations(JdbcTaskQueue queue, ReconciliationScanBudget budget);
public JdbcExternalOperations(JdbcTaskQueue queue); // delegates to (64,64)
private record ScanCandidate(long sequence, String operationId) {}
private final ReentrantLock scanLock = new ReentrantLock();
private final ScanCursor scanCursor;
private static final class ScanCursor {
    boolean active;
    long after; // zero is before positive sequence domain
    long upper; // fixed for the pass
}
private List<ScanCandidate> discoverPage(long after, long upper, int limit);
private Optional<OperationToken> claimCandidate(ScanCandidate c, String owner);
```

(64,64) preserves constructor compatibility, not a product quota. Deployment uses explicit budgets. Validate arithmetic/positive config once; bounded page memory, no per-owner cursor map, growing rejected set, cached capabilities, or long-lived transaction/snapshot.

Reject ambient transactions and validate owner before scanLock.tryLock(). If contended, return empty with no DB/cursor work; do not queue indefinitely. Keep scanLock separate from `this`, which currently protects initial-send token bookkeeping. Renewal/observation/heartbeat must not take scanLock. ReconciliationLoop is already non-overlapping; separate instances/processes have independent cursors and DB fencing.

Cursor survives calls on its instance; never construct a new operations instance per poll. Restart begins at zero safely. Repeated crashes before pass completion limit liveness; no promise of starvation freedom under infinitely frequent restarts. No durable cursor is needed for this cross-call closure.

## 5. Discovery SQL and cursor lifecycle

Open an inactive pass with ONE nonlocking autocommit query:

```sql
SELECT reconcile_scan_seq
FROM external_operation FORCE INDEX (external_reconcile_scan)
ORDER BY reconcile_scan_seq DESC LIMIT 1;
```

No row: return empty, remain inactive. Otherwise `(active=true, after=0, upper=returnedValue)`. Never raise upper during a pass. Pages:

```sql
SELECT reconcile_scan_seq, id
FROM external_operation FORCE INDEX (external_reconcile_scan)
WHERE reconcile_scan_seq > ? AND reconcile_scan_seq <= ?
ORDER BY reconcile_scan_seq LIMIT ?;
```

SQL numeric ordering/comparison and exact signed-long binding avoid collation/precision disagreement. Unique ordinal needs no tie-breaker. ascii_bin UUID remains lookup identity, not cursor order. No state/due/policy JOIN/WHERE filters: a filtered scan over terminal history could examine arbitrarily many rows to fill a limited result. Raw discovery is ordinal/id only and grants no authority.

Per call consume at most B=maxRowsPerCall rows, pages <=P=pageSize, last page limited to remaining budget. Validate positive monotonically increasing sequence <=upper. Advance after BEFORE candidate transaction, count the row, then call claimCandidate. On success immediately return its COMMITTED token. Discard unprocessed prefetched rows; next call refetches after the last CONSUMED row. Never advance to the final fetched row unless all fetched rows were consumed.

Short/empty page after all returned rows are consumed, or after==upper, ends the pass. Return; NEXT call opens a new pass. Never wrap in the same call. If success consumes the final row (known final via upper or last row of short page), reset active before returning. Success earlier in a short page retains active and remaining rows. If a page is exactly full but upper points to a rolled-back/deleted gap, the later empty page safely ends the pass.

Discovery exception leaves after unchanged for that query and propagates. Candidate exception rolls back via q.work and propagates AFTER consumed-position advancement: next call reaches later work, failed row returns next pass. Do not swallow DB/policy errors into stale/empty or retry unboundedly. Commit-ACK ambiguity returns no token, keeps progress, and allows genuine later expiry takeover; never resend. No extra in-call rediscovery retry loop.

## 6. Claim authority and global lock order

Each claimCandidate owns ONE independent q.work transaction. Prefer a claim-specific helper, leaving shared renew/observe/zero-effect retry logic unchanged.

1. Discover actual operation by ID, current execution, and that execution's actual durable_task. Check ordinal equality for metadata consistency; skip terminal or not-currently-due rows cheaply. Read complete immutable TaskSpec from actual task. Absent chain returns empty. Unregistered policy returns empty BEFORE identity/subject/root/task locks; no generic fallback. Detached cursor/order/policy labels confer nothing.
2. `q.prepareDomain(List.of(actualSpec), TASK_MUTATION, true, null)`: existing policy acquires complete sorted identity union before ANY subject/root. Preserve issuer, exact DataSource object, actual holder, full-spec and purpose checks, and genuine registered domain/input rules. Explicit preparation-empty skips; thrown failure propagates under cursor rule. No EXTERNAL_FACT_OBSERVATION substitution.
3. Lock subject then complete domain graph, sessions then files as required by that policy, all in existing canonical order using SKIP LOCKED. No new identity after roots. End each candidate transaction before attempting another, releasing partial identity unions/roots.
4. Lock actual task SKIP LOCKED and compare full locked TaskSpec to discovery. Invoke the registered policy validateInput on that exact locked spec under the already-prelocked complete domain graph; this is a DB-only provenance check, not another identity/root discovery phase. Its failures propagate, and its statement/row cost belongs to Q_policy/R_policy below. Then lock operation and execution SKIP LOCKED. Verify operation/subject, unchanged current execution number, execution ID/no and task relation; task subject equals operation subject. Changed discovery skips until a later pass; never rediscover/lock a different task while holding old operation lock.
5. Read one fresh DB UTC timestamp after locks. Require operation AND execution states MARKED/UNKNOWN, non-null reconciliation expiry, BOTH next_check_at<=now AND reconcile_expires_at<=now. Extend private Operation or a claim-only locked projection with nextCheckAt and scan sequence; never trust detached due fields. Do not add blanket q.current business-version equality: reconciliation can query an old execution whose business effects are fenced during observe. Authentic registered TASK_MUTATION preparation remains necessary; receipt-only capability cannot claim.
6. Only then increment epoch and set owner/expiry/next_check_at to existing genuine 20-second lease deadline. No task/execution creation, initial-call-authority grant, permit admission, fake output or side-effect callback. Return token only after outer flush/commit. Audit/outbox last; no subsequent locks, network/file/archive/parse/hash work.

Skipped rows keep next_check_at/marked_at/epoch/owner/expiry/task/execution/outbox byte-identical. Unregistered task policy differs from UNKNOWN external outcome: registered UNKNOWN can be claimed for lookup, but never creates another send/execution. Existing initial-send identity capability and explicit verified zero-effect retry remain unchanged.

## 7. Bounds and concurrency/recovery argument

Let P=pageSize, B=maxRowsPerCall, C consumed rows (C<=B). A call opens at most one pass, returns <=1 token, issues <=ceil(B/P) page queries plus <=1 maximum query only at pass opening. Discovery returns <=B rows total, each list <=P. A final empty page after a full page uses the remaining page budget; short pages terminate. <=B candidate transactions, each attempted once without rediscovery retry.

Fixed candidate BUSINESS SQL is <=9 statements: initial operation, execution and task reads; subject/task/operation/execution lock reads; one fresh DB-time read after locks; successful update. The eight fixed query statements each return <=1 row, and the update affects <=1 row. The early due gate must use a UTC timestamp expression/projection in the already-counted initial operation read; it MUST NOT invoke a second q.now() without updating the bound. This is a prospective helper requirement, not a claim that the helper exists. If an extra clock query is chosen, add its statement and result row per invoking candidate explicitly (worst case +B in both envelopes); no silent helper-cost increase.

Actual source verification: `JdbcTaskQueue.java:26` delegates every q.work to TransactionRunner.required. `backend/adapters/src/main/java/ama/db/TransactionRunner.java:23-27` executes `SET NAMES utf8mb4` and `SET time_zone = '+00:00'` before the candidate callback. These are TWO explicit session-setup SQL statements per attempted candidate transaction, not BEGIN/COMMIT/ROLLBACK. In this design C consumed rows each attempts one q.work, so setup cost is 2*C (at most that number executes if setup itself fails); each SET returns zero result rows and mutates zero domain rows. TransactionRunner source SHA256: `c68bab5254f79bf0927e8dcf537a97aa6afca4397791e892b1ac140bae4a14ce`; JdbcTaskQueue source SHA256: `83c2ba70b1a80449e83e49b0e5bf6efbd4574c7597bc687d2c41721768794b14`. No TransactionRunner change is proposed.

Let M be pass-opening maximum queries (0 or 1), M_rows their returned rows (0 or 1), D page queries (<=ceil(B/P)), and L raw page rows returned (<=B, possibly greater than C when success leaves prefetched rows unconsumed). Let Q_policy/R_policy bound all additional registered preparation/root/input SQL and result/mutation rows per candidate. Q_policy includes any explicit SQL added by policy-owned wrappers, including nested REQUIRED session SETs if such wrappers exist; the two top-level SETs counted separately are excluded from Q_policy to avoid double counting. The registered policy closure must declare these costs, not hide extra framework work behind a callback.

- Business/policy SQL <= M+D+C*(9+Q_policy).
- Top-level session SQL <=2*C; combined explicit JDBC SQL <=M+D+C*(11+Q_policy), conservatively `1+ceil(B/P)+B*(11+Q_policy)`.
- Result/mutation rows <=M_rows+L+C*(9+R_policy), conservatively `1+B+B*(9+R_policy)`. Count each SELECT result row and each affected domain row once per statement, including repeated reads of the same row. Include the maximum query's one returned row; SETs return no row and do not add to this domain-row count.
- Driver/transaction controls are a separate measured category: connection acquisition/release, setAutoCommit, commit/rollback and any actual isolation/read-only/savepoint control calls or driver-generated control SQL. Trace and declare their counts separately; they are not claimed bounded by the explicit-JDBC-SQL formula. Do not classify the two explicit session SETs as controls to exclude them.

Existing empty outbox.flush performs no SQL because claim stages no event. Any future event-producing helper change must revise its explicit cost, not assume it remains empty. Arbitrary unbounded TaskDomainPolicy implementations cannot satisfy this claim; production registrations need finite DB-only closure. Ordinary connection/statement deadlines remain deployment safeguards. SKIP LOCKED does not bound network/metadata waits or arbitrary callbacks.

Worked design-count verification (prospective traces; arithmetic/source review only, NO JDBC/DB execution):

| Scenario | Maximum/page queries | Fixed candidate business SQL | Policy SQL | Session SET SQL | Combined explicit SQL | Result/mutation rows | Driver/control category |
|---|---|---|---|---|---|---|---|
| Opening successful (P,B)=(1,1), one row, C=1 | 1+1 | 8 reads+1 update=9 | Q for actual declared policy path | 2 | 13+Q | maximum 1+page 1+fixed 9+R =11+R | One transaction's actual acquisition/begin-or-autocommit/commit/restore/release trace, separately recorded |
| Opening all-skipped (P,B)=(2,4), four due rows with unregistered task types, C=4 | 1+2 | 3 reads per row (operation/execution/task), no mutation:12 | 0 (registry rejection before policy) | 8 | 23 | maximum 1+page 4+candidate reads 12=17 | Four actual transaction-control traces, separately recorded |
| Final empty page in an already-active pass, C=0 | 0+1 | 0 | 0 | 0 | 1 | 0 | Autocommit discovery connection/driver operations only; no candidate q.work controls |

For the second trace, the conservative Q_policy=R_policy=0 envelopes are 47 SQL and 41 rows; exact 23 SQL/17 rows fit because early policy rejection executes only three of the nine possible candidate business statements. The final-empty trace may follow a last-position candidate exception that left a pass active; it must not open a new pass in the same call. For the opening success, envelopes are exactly 13+Q_policy SQL and 11+R_policy rows; the former erroneous 11+Q_policy and 10+R_policy ceilings are explicitly superseded. Policy Q/R must be shown from its actual bounded closure in later execution evidence, never replaced by zero for a registered successful policy. Setup-failure and candidate rollback traces count statements actually executed and separately record controls, with no token on error.

The covering numeric range avoids unbounded due-filter scanning. LIMIT does NOT prove a hard InnoDB physical page/version-inspection count under MVCC. Evidence must report logical rows/attempts and actual EXPLAIN/index plan, not claim a fabricated physical I/O SLA.

At pass start fix H=upper. Default auto allocation is monotonic: new allocations >H cannot lengthen the pass despite constant arrivals, time changes or UUID order. Transactions with previously reserved <=H ordinals may commit later; finitely many positive unique slots <=H exist. Gaps/rollbacks do not obstruct progress. Each consumed value strictly increases after, so the pass is finite and never chases a moving upper bound. Success also consumes its position, preventing repeated early successes from monopolizing the cursor. Worst-case invocation bound is one per possible pass row plus final empty invocation (early success returns); for a stable all-skipped N-row pass, <=ceil(N/B)+1 calls.

After a target commits and stays eligible with required locks available at some visit, it is reached this pass if ahead, or a later finite pass if behind/new/late-committing. New allocations cannot interpose ahead inside captured membership. Persistent lock denial can prevent service, so fairness means eventual examination and service under an available-lock opportunity, not defeating perpetual adversarial locking. Competing workers may serve the row first; that is not job starvation. DB task/domain/operation SKIP LOCKED and fresh epoch/expiry enforce one live takeover; independent in-memory cursors need no shared authority.

Due advanced after discovery denies premature claim under lock. Earlier due time, retry-marked state, newly available policy, released held identity, or low ordinal committed behind after all return on next pass; no exclusion set can lose them. Deletion of a discovered row/gaps/empty pages/restart are safe because discovery owns no business state. Repeated restarts before completing passes are an explicit liveness limitation, not weakened fencing.


Backlog-latency tradeoff remains explicit and is NOT adopted by root: scanning 100,000 terminal prefix rows plus one eligible tail at B=64 needs ceil(100001/64)=1,563 calls under stable all-skipped prefix conditions. At the existing five-second interval between empty polls this is about 7,810 seconds (approximately 2.17 hours) before query/control time. This is not a latency SLA or evidence of capacity qualification. Root must acknowledge the tradeoff and deployment budgets before ordinal adoption; repeated restart can rescan the prefix. The R1 accounting correction changes neither enumeration nor this limitation.

## 8. Future tests: exact scenarios, not executed

Only NEW root-owned loopback MySQL fixtures, unique namespaces and explicitly bounded fixture sizes. Use genuine owners/affected identities, meeting/input rows, registered test policy, real queue tasks and committed beginExternal markers. Fixture-owned due/expiry updates arrange scheduling, never fabricate successful state. Use real MeetingAccess preparation as in B06 SeamPolicy, not TaskFixture's intentionally identity-free policy for identity evidence. Latches/SQL tracing control actual DB lock races; sleeps are not lock-order evidence. No old fixture/env/PKI/S3 operations.

| Proposed selector | Exact assertions |
|---|---|
| ReconciliationFairnessIT.moreThan64HeldIdentitiesProgressAcrossCalls | 130 genuine markers; hold required identities for first 129, last free. (P,B)=(16,32): tail claimed within 5 calls despite empty intermediate calls. Complete identity union before subject; failed union acquires no root/task. Release locks; next pass visits old prefix. |
| ReconciliationFairnessIT.moreThan64UnregisteredPoliciesDoNotStarveRegisteredTail | 129 genuinely produced markers with type absent from scanner registry, plus registered tail. <=5 calls with (16,32); absent type gets no preparation/root/task lock/token; all skipped fields/outbox unchanged. |
| ReconciliationFairnessIT.mixedHeldRootsTasksOperationsAndUnknownPolicies | >64 mixed blockers at identity/subject/domain/task/operation/execution phases, real independent transactions. SKIP LOCKED each phase, partial-lock release, tail progress, no identities after roots. |
| ReconciliationFairnessIT.constantArrivalsCannotExtendCapturedPass | Capture H on first call; append genuine markers before every later call. H remains fixed, original tail served within original finite pass, arrivals >H visited next pass. Fixture timestamps deliberately earlier to prove independence from wall clock. |
| ReconciliationFairnessIT.successAdvancesOnlyConsumedRows | Several claimable rows in one page; each call consumes only through returned success, never skips prefetched tail. Make prior early rows due again and still reach all original IDs. |
| ReconciliationFairnessIT.shortEmptyExactBoundaryAndGapWrap | Empty, N<P, N=P, N=B, terminal-only history, rolled-back sequence gaps, last-row success. <=1 pass opening per call; proper next-call reset; no spin; later insert served. |
| ReconciliationFairnessIT.futureDueAndDueTimeChanges | Barrier moves due time forward/renews before lock: no premature token. Behind-cursor row becomes due, locks released: next pass visits. Microseconds and UUID lexical prefixes do not alter ordinal order. |
| ReconciliationFairnessIT.lateCommitBehindCursorIsRevisited | Uncommitted initial marker reserves low seq, higher marker commits/captures H; scanner passes gap; commit low row. Next pass finds it with no artificial time mutation. |
| ReconciliationFairnessIT.concurrentInstancesAndSameInstance | Two scanner instances race same row: one live token/epoch, no initial-send authority. Same-instance tryLock contention returns promptly; scanner Java lock does not block renew/observe. |
| ReconciliationFairnessIT.errorAndCommitAmbiguityAdvanceWithoutAuthority | Preparation/SQL/commit-ACK failure after consumption propagates, no token, rollback or inspected durable ACK-ambiguous state. Next call reaches tail; failed candidate revisited next pass. Discovery error retains cursor. No resend. |
| ReconciliationFairnessIT.discoveryChangesRequireFreshActualSpec | Change current execution/task relation at deterministic barrier using genuine retry transitions where possible. No new task lock after operation lock, old capability never authorizes new spec; forged/expired/foreign-DS/incomplete preparation rejected. |
| ReconciliationFairnessIT.statementAndRowBudgets | Keep (8,16) limits and add opening-success (1,1), opening four-unregistered all-skipped (2,4), and active-pass final-empty traces from section 7. Count business/policy SQL, both session SETs per q.work, maximum query/result row, page/result rows, mutation rows and separate driver/control operations. Assert combined ceiling 1+ceil(B/P)+B*(11+Q_policy) and row ceiling 1+B+B*(9+R_policy), including any explicitly added early-clock-query cost; successful policy Q/R come from actual closure. <=16 raw/consumed rows, <=2 page queries plus optional max and <=16 candidate transactions for (8,16); no OFFSET/unbounded list, bounded LIMIT, byte-identical skipped rows. EXPLAIN uses external_reconcile_scan range without filesort. |
| ReconciliationFairnessRestartIT.newInstanceAndFreshJvmRecover | Partial pass then instance and separately fresh-JVM restart against same new owned fixture, no reset. Tail eventually claimed, live lease not duplicated, UNKNOWN still one execution and zero resend count. |
| ReconciliationScanSchemaIT.forwardMetadataLegacyPreservationAndRerun | New pre-forward owned fixture with varied genuine markers. Root-ACKed migration: positive unique/non-null/auto_increment/index metadata; prior columns/IDs/times/history checksums unchanged; omitted insert allocates higher; retry/renew/observe preserve seq; rerun zero; missing schema fails visibly. |

Relevant inherited regressions once after actual behavioral RED/GREEN, only on new owned fixtures: ExternalExecutionIT (unknownExecutionNeverReentersOrdinaryQueue; reconcilerTakeoverFencesOldOwnerAndPreservesExternalFact), ReconciliationWorkerIT, B06QueueSeamsIT (completeIdentityUnionUsesBinaryOrderBeforeAnySubject; heldIdentitySkipsBeforeSubjectAndReleasesPartialUnion; preparationsRejectForgeryIssuerSpecPurposeAndExpiredTransactions; unregisteredReconciliationDomainIsSkippedWithoutGrantingAuthority; factOnlyReceiptSeparatesExpiredOwnerEpochFromActiveAuthorityAndUnknown; queueIndependentlyRejectsPreparedTokenFromAnotherBoundDataSource), ModelPermitIT, TaskLeaseIT, OutboxFenceIT, OutboxConsumerIT, PreparedCompletionIT. WorkerLoopTest/PreparedWorkerLoopTest if shared wiring changes. B05SchemaIT's exact V010 metadata/history oracle runs separately on a new target-010 fixture. Preserve inherited assertions and old golden metadata. Author derives exact Maven commands from then-current build files; none were executed here.

## 9. Owned files, sequencing and remaining gates

Proposed implementation ownership:

- `backend/adapters/src/main/java/ama/task/JdbcExternalOperations.java`: explicit budget, bounded cursor, fresh claim helper. Other external protocols retain their semantics.
- New `backend/adapters/src/main/java/ama/task/ReconciliationScanBudget.java` (public nested record also acceptable after review): positive deployment bounds only.
- New root-numbered migration, first staged at `W/UNASSIGNED-task08-reconciliation-scan.sql`; root allocates/freezes/ACKs. No old SQL edits.
- New `backend/integration-tests/src/test/java/ama/task/ReconciliationFairnessIT.java`, `ReconciliationFairnessRestartIT.java`, `ReconciliationScanSchemaIT.java`, narrowly owned fixture/policy/probe helpers and root-assigned owned acceptance harness/evidence.
- No required change to ExternalOperationPort signature, TaskDomainPolicy, JdbcTaskPreparations, generic queue, WorkerLoop or ReconciliationLoop behavior. Optional Javadoc should explain empty can mean exhausted per-call scan budget. Deployment composition selects explicit budget when implemented; no uninspected production config path is invented here.

Task 7 consumes unchanged claim signature/token. Its permit-recovery cursor/budget is separate; never reuse operation scan progress for reservations. Task 8 scan metadata does not contradict Task 7's no-new-observation-DDL decision, and neither task claims the other's completion.

Unresolved decisions/gates: independent review of ordinal versus sidecar tradeoff, migration shape/rollout cost, policy-specific finite bounds and final claim predicates; root migration number/freeze/apply ACK and new owned fixture allocation; deployment page/row budgets. No unresolved product question. These are review/implementation gates, not another user permission request. FUP-B05-CANDIDATE-01 remains OPEN pending real owned-fixture evidence and independent implementation review. No author was dispatched.
