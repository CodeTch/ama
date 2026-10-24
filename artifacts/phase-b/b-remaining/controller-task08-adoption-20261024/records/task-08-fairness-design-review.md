# Task 8 fairness: independent bounded design and proposed DDL review

Date: 2026-10-23. Scope: remaining FUP-B05-CANDIDATE-01 only. This is an independent source-grounded review of the complete design whose SHA256 is `309f7513f6c2b64db67b4469ebac7ba028c38dcc54c2c070d765bd7e02248d74`.

## Verdict

- **SPEC: FAIL**, solely for the inaccurate declared resource accounting in T8DR-01.
- **Design quality: FAIL pending T8DR-01 correction.** The finite cursor, authority and lock-order architecture is otherwise suitable for prospective implementation.
- **Proposed DDL shape quality: PASS**, as an additive design proposal subject to the explicit gates below. No migration file/version/freeze/apply approval is supplied by this review.
- Findings: **1 total: P0 0, P1 0, P2 1, P3 0**.
- FUP-B05-CANDIDATE-01 remains **OPEN**. No implementation, integration, runtime, company, HA or capacity qualification follows.

No tests, database/Docker operations, private environment access, Git commands/mutations, source edits or delegation were performed. Only this assigned report is written. Task 3/4 implementation is outside scope. Baseline `66e70fdeef286a38592395ce94bdfbb1a93bce4a` and unchanged Task 8 source status were supplied by root; this reviewer verifies the exact read-file hashes below, rather than claiming an independent Git comparison. Full design, remaining plan, b07-b08 preflight and adopted authority refinement were read; controlled-runtime Normative R1 and relevant existing source were inspected.

## Finding

### T8DR-01 — P2 — Account for transaction session SQL and the high-water result row

**Location:** `.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-08-fairness-design.md:129` (and the prospective budget assertion at line 156).

The stated SQL ceiling `1+ceil(B/P)+B*(9+Q_policy)` is not a ceiling for the actual candidate transaction path. Every `q.work` calls `TransactionRunner.required` (`JdbcTaskQueue.java:26`), whose callback executes `SET NAMES utf8mb4` and `SET time_zone = '+00:00'` before invoking candidate work (`TransactionRunner.java:23-27`). Those are two real JDBC SQL statements per attempted candidate. They are session configuration, not BEGIN/COMMIT/ROLLBACK, and are not included in the declared policy cost. Merely saying transaction-control statements are counted separately leaves this cost unaccounted.

The result/mutation-row formula also omits the one row returned by the pass-opening maximum query. The allowed configuration `P=B=1`, opening a nonempty pass and successfully claiming its single row, returns one maximum row, one discovery row, eight fixed candidate query rows and one updated row: `11+R_policy`, exceeding the claimed `10+R_policy`. Its explicit SQL count is likewise `13+Q_policy`, exceeding the claimed `11+Q_policy`, before driver transaction controls.

**Impact:** the proposed JDBC budget evidence can either fail on a valid implementation or undercount observable database work while reporting a passed bound. Exact finite deployment bounds are an express Task 8/global requirement. This does not invalidate the cursor's eventual progress proof, but the design should not be adopted with a false quantitative contract.

**Required correction:** explicitly distinguish business SQL, the `2*C` session-setup statements, driver/transaction controls and result/mutation rows. With the currently enumerated nine candidate business statements, safe combined envelopes are `1+ceil(B/P)+B*(11+Q_policy)` SQL statements, plus separately declared driver/transaction controls, and `1+B+B*(9+R_policy)` result/mutation rows. If the early due gate uses an additional clock query instead of a timestamp expression/projection within an already counted read, count that too; do not silently increase the fixed helper cost. No change to TransactionRunner is requested.

**Prospective evidence:** add a pass-opening successful `(P,B)=(1,1)` trace, a multi-candidate all-skipped call, and a final empty-page call to the existing budget scenarios. The trace must include both session SET statements, the maximum query and the policy closure; record which driver operations are outside the SQL count. Re-review the corrected design arithmetic before freezing implementation expectations.

## Independent assessment of the remaining design

### Finite progress, arrivals and recovery

The proposed ordinal is a scheduling coordinate, never an authority token. Fixing H once, querying a raw indexed range and advancing only through each consumed row creates a finite monotone pass. Held identities, missing policies, terminal rows and rejected candidates all consume a position. Successful claims also consume their position; repeatedly becoming due cannot keep an early successful row at the front forever.

This closes the original `JdbcExternalOperations.java:68-74` first-64 counterexample: 129 held or unregistered rows followed by one available registered row are reached in at most five normal calls at `(16,32)`. The stated fixture must prove both blocker classes independently. A short page ends only after its returned rows are consumed; a successful early row cannot discard the prefetched tail. The implementation must define “short” against that query's requested LIMIT, including a reduced final page.

New allocations above H cannot extend the current pass. Earlier reserved values that commit after their gap has been traversed are revisited after reset; no long snapshot or exclusion set hides them permanently. Finite gaps and deletions do not obstruct the range seek. A database crash that changes allocation history still leaves surviving unique positive rows enumerable on a later pass; the proof needs stable persisted identity, not contiguous sequence numbers or commit ordering.

The in-memory cursor is adequate for the requested cross-call closure. The existing loop retains one operations instance and schedules again after empty (`ReconciliationLoop.java:14,25-28,38-57`). A new instance starts from zero; eventual service therefore assumes a sufficiently long interval of repeated calls without restart, as explicitly acknowledged. Infinitely frequent restarts or persistent/adversarial lock denial are not covered. No durable shared cursor is necessary to fix the named follow-up.

Candidate exception advancement is deliberate and appropriate: failed candidates do not pin the head, and their owned transaction releases partial locks before the next call. A discovery exception cannot consume undiscovered rows. An ambiguous commit returns no token or send authority and is recovered through later lease expiry; do not make an automatic initial send or fabricate an external outcome. An exception on the final consumed position may require an empty closing call, still within a finite pass.

### Fresh authority, locks and UNKNOWN

The proposed claim-specific helper is preferable to altering the shared renew/observe/retry helper. The real task comes from the actual operation/current execution chain. Registry absence is checked before acquiring authority or locks; authentic preparation remains TASK_MUTATION, with exact issuer/DataSource/transaction-holder/full-spec verification. The inspected `prepareDomain` and `JdbcTaskPreparations` support this boundary.

Preparation precedes subject/domain/session/file/task/operation/execution locks. Every applicable lock phase must honor SKIP LOCKED, and a failed candidate transaction must release its partially acquired union before moving on. Locked full TaskSpec equality, locked current-execution/ID/task/subject checks, both live states and both fresh due/expiry predicates are necessary. The current adapter projection lacks next_check_at and the current claim does not recheck it; adding a locked projection is justified, not an incidental queue rewrite.

validateInput after the task lock must be a bounded DB-only check of the already-prelocked immutable graph. Existing TaskDomainPolicy does not itself enforce boundedness or prohibit arbitrary callback work, so each production registration needs an explicit closure and cost declaration before activation. The test SeamPolicy prelocks its input and performs a nonlocking immutable provenance read (`B06QueueSeamsIT.java:72-95`); it is supporting evidence of the intended composition, not production qualification.

Avoiding a blanket new current-business-version check preserves reconciliation of old committed external effects. It does not authorize initial send, candidate adoption, stale retention or arbitrary callbacks. Receipt-only preparation still cannot claim; unknown policy is distinct from registered external UNKNOWN. The unchanged initial-send capability is granted only after beginExternal's commit (`JdbcExternalOperations.java:53`), and claims never call that grant. Permit recovery remains Task 7's independent protocol.

### Ordinal necessity and alternatives

A new ordinal is a reasonable engineering choice, not a unique mathematical necessity. The finite UUID domain itself gives a formal bound, but random arrivals can interpose at a scale unrelated to the captured workload; that is a poor useful operational fairness argument. Mutable due-time keys do not stabilize membership, and the current timestamp columns provide no enforced monotonic insertion clock. OFFSET and a larger bounded window preserve the original vulnerability. An outbox-fed enumeration would need a complete retained legacy mapping that the schema/transport contract does not guarantee; manufacturing events is unacceptable.

A sidecar with its own ordinal and unique operation relation could implement the same proof. It requires complete metadata-only backfill, atomic insertion for every producer, and an old-writer rollout strategy. The proposed column avoids missing old-writer sidecars: the existing explicit-column INSERT at `JdbcExternalOperations.java:39` omits the new field and receives an allocation automatically. The operation PK and request identity remain unchanged. The column is therefore a defensible simpler choice if root accepts the rebuild/latency tradeoffs; this review does not treat root as having preapproved it.

### Concrete operational regressions and DDL compatibility

Scanning all terminal history can materially delay a newly due operation compared with the existing due-index query. For example, 100,000 terminal prefix rows and one due tail at B=64 require 1,563 calls; with empty calls separated by the existing five-second poll, traversal is on the order of 2.2 hours before query time. This is an explicit design tradeoff, not an extra finding against an unstated latency SLA. Repeated restart can repeat that prefix. Root must acknowledge backlog-dependent latency when choosing deployment budgets; do not silently replace the finite sweep with a priority lane that consumes its whole budget.

The exact proposed ALTER adds only a signed NOT NULL AUTO_INCREMENT column and unique index. The inspected original external_operation has no AUTO_INCREMENT and retains id as its PK; the new index can satisfy the separate allocation-index requirement. MySQL documents that the auto column can be first/only in a secondary index, without becoming the PK. [MySQL AUTO_INCREMENT handling](https://dev.mysql.com/doc/refman/8.4/en/innodb-auto-increment-handling.html)

Adding the column is not an instant, uninterrupted rollout: MySQL documents substantial reorganization and no concurrent DML for this case. The design correctly leaves rebuild/locking behavior as a gate. This review consulted the 8.4 documentation as a compatibility reference, not as proof of the repository's actual pinned fixture version. Verify that exact engine/version before freeze. [MySQL online DDL operations](https://dev.mysql.com/doc/refman/8.4/en/innodb-online-ddl-operations.html)

Adding the column to existing rows supplies enumeration metadata only. No old state/due/owner/epoch/current execution/request hash/task/output authority may be changed or inferred. Unspecified old-row ordinal order is acceptable. An all-state/all-policy legacy enumeration is essential; limiting backfill to presently due or registered operations would defeat later recovery. The design's old-column byte-preservation and positive/unique coverage gate is appropriate.

Signed long avoids a JDBC unsigned overflow mismatch. Positive-domain validation, immutable repository writes, restore/import values and allocation-above-maximum checks remain required. Exhaustion must be exercised at the actual engine boundary with no wrapping, duplicate live assignment or silent fallback; it is not a product quota. The original migration and historical metadata oracle must remain byte-identical. The exact target-010 oracle belongs on its separately created target-010 fixture, not a forward-schema fixture.

Missing-schema failures must propagate visibly, never fall back to the first 64. Old binaries may continue to insert markers after the additive migration, but their reconciliation scanners retain their old fairness limitation; an old scanner's presence is not runtime completion evidence. New scanner instances must be wired once with explicit deployment budgets before external consumers are activated.

## Prospective gates

1. Correct T8DR-01 and independently re-review its exact updated design hash. Preserve this original finding. Root makes the ordinal-versus-sidecar adoption decision.
2. Stage only the reviewed forward candidate outside formal migration resources; root alone assigns the migration number, verifies pinned Flyway ordering and original SQL/history hashes, freezes the final candidate and separately ACKs application. This report assigns none and performs none.
3. On a new root-owned pre-forward fixture containing genuine legacy markers, prove all prior columns/relations/history hashes unchanged, positive unique sequence coverage for every state/policy, expected additive metadata/index, and new omitted-column allocation. Verify physical ALTER/rebuild/lock behavior, actual engine-version compatibility, restore/import and exhaustion behavior, and zero additional migrations on normal Flyway rerun. No reset, repair, outOfOrder or old-fixture reuse.
4. Implement meaningful RED/GREEN for the separate >64 held/unregistered cases; then the design's arrivals, late commit, gaps, success-prefetch, fresh due/spec, lock order, candidate/discovery exception, ambiguous ACK, same-instance and concurrent-instance scenarios. Add the corrected budget traces above. Check actual EXPLAIN/index use; logical row bounds do not prove an MVCC physical-I/O ceiling.
5. Record the registered production policy's complete identity/domain/input closure and finite SQL/row budgets; fresh locked due/spec authority must not depend on detached discovery data. No archive/file/network/parse/hash work under DB root locks, no generic policy fallback or receipt-purpose substitution.
6. Use the same new fixture for the explicit instance and fresh-JVM restart scenarios without resetting durable external state; verify one execution/no resend for UNKNOWN and no duplicate live lease. Keep relevant inherited task/reconciliation/identity/permit/outbox regressions and old golden assertions intact.
7. Obtain independent implementation SPEC/quality review of exact source/evidence hashes. Only then may root close FUP-B05-CANDIDATE-01 and allow dependent external Worker activation in the adopted Task 8-before-Task 7 order. Company protocol absence, generic fencing, UNKNOWN/manual behavior and separate permit settlement remain unchanged.

## Exact reviewed file hashes

Paths are relative to `/home/tanren/ama/.worktrees/phase-b-completion`. Hashes describe the read bytes; a hash does not imply full-file implementation review where only relevant ranges were inspected.

| File | SHA256 |
|---|---|
| .superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-08-fairness-design.md | 309f7513f6c2b64db67b4469ebac7ba028c38dcc54c2c070d765bd7e02248d74 |
| docs/superpowers/plans/2026-10-23-ama-phase-b-remaining.md | bb829ac0476bc7861ea98e985ec29f69a86e0817bbca37963be050e74fb57614 |
| docs/acceptance/b/remaining-preflight/b07-b08-preflight.md | 28c6308fecde3785512e5cedc9223e7fff0f39aa8d5115b2dab0a54ec75bd07e |
| docs/acceptance/b/task-B-05-reconciliation-candidate-followup.md | b5eefdab4016fe62452ad7669b0d6710faaad185c09a11c692374cc8e12cee3d |
| docs/superpowers/specs/2026-10-23-ama-phase-b-controlled-runtime.md | 8bec16ce2935cef50aee5771c4be3ea899db11fed28e4255b2feeb2f7a016b54 |
| docs/superpowers/specs/2026-10-23-ama-phase-b-authority-external-protocols.md | 1a40905870a7d4d1fcdb44515cca2c82deffdf734368c12e397854439d99ac95 |
| backend/adapters/src/main/java/ama/task/JdbcExternalOperations.java | 63e84274395c74c8d5e5375fcfc898c5cc39f19447318a2093eebaff1496ad19 |
| backend/adapters/src/main/java/ama/task/JdbcTaskQueue.java | 83c2ba70b1a80449e83e49b0e5bf6efbd4574c7597bc687d2c41721768794b14 |
| backend/adapters/src/main/java/ama/task/JdbcTaskPreparations.java | 43e93c567664bb916c03a52381ee4892aa153a58dff0cfa9537c11f4e68d6905 |
| backend/adapters/src/main/java/ama/task/JdbcSubjectFences.java | 4622efcf0e6d76bf69e385fc442b4098fb6fe6f222b017349b92267008bb40dc |
| backend/adapters/src/main/java/ama/task/JdbcOutbox.java | a97cf78ab2585e33159027716f1c2e8078af276a97cfacfa3603ee59af272909 |
| backend/adapters/src/main/java/ama/db/TransactionRunner.java | c68bab5254f79bf0927e8dcf537a97aa6afca4397791e892b1ac140bae4a14ce |
| backend/core/src/main/java/ama/task/TaskDomainPolicy.java | acea5e5690597cb03163ba82217ae3a390b7b279ed511d316b0890500f08e620 |
| backend/worker/src/main/java/ama/worker/ReconciliationLoop.java | d2dc10a818c870e250fdfa265682fa897019668376e450ebf5b8abed50914149 |
| backend/adapters/src/main/resources/db/migration/V010__reliable_execution.sql | 03e89cc8bd58d5a6aa0d4ab031b8fc05a4f7d2450623df147cd84d05b68977db |
| backend/integration-tests/src/test/java/ama/task/B06QueueSeamsIT.java | f2fdf2f64ca33d112a8cb618748249b8454e631cc24a8090927f2963b5312a74 |

The DDL reviewed is exactly the three-line ALTER embedded in the design at lines 46-48; it has no separate candidate-file hash or allocated migration identity. Public reference documentation was read only. All runtime evidence listed above is prospective and unexecuted.
