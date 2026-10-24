# Task 5 R1 — independent bounded design re-review

2026-10-23. Design-only re-review of T5DR-01, T5DR-02 and introduced breakage. R1 is an additive repair superseding affected paragraphs only; the original accepted requirements remain binding.

- **SPEC verdict: PASS at design level.**
- **Design-quality verdict: PASS at design level.**
- **T5DR-01: ADDRESSED.**
- **T5DR-02: ADDRESSED.**
- **New actionable findings: none.** No new P0/P1/P2/P3 finding IDs.

This closes the requested design findings. It does not establish implemented correctness, executed-test success, transport qualification, runtime readiness or Phase B completion.

## Inputs and bounded method

Read task-05-design-brief.md first, original findings, affected original design contracts, all 220 lines of R1, its closure and exact additive diff. Independently verified raw SHA256:

| Artifact | SHA256 |
|---|---|
| task-05-import-worker-design.md | 26a5b94befb57b5802b37b7dcd5e0679fc42add7f6d0e9a69602da871556d06f |
| task-05-import-worker-design-review.md | 90f4d86d43a0d0d87889d7a33c1e2e80696a389763853f298ffcdd5679feb6a7 |
| task-05-design-r1-review-diff.txt | 552f86823e3a66e0e7b6a4ccdd63e36c0928ae1acf1da4786ff6c525dd586d89 |
| task-05-import-worker-design-r1.md | 7abe66e4fb2ae304df29ad62d374455c86a928d41fa4ba03f2d78fd268a284f4 |
| task-05-import-worker-design-r1-closure.md | b80abbcda376ded13cd5b4b9776e259722387190dad6df25d206f408623cd2e7 |

Reconstructed both added diff bodies and compared with the complete R1 and closure: both match. Consulted Task5 plan/preflight, Task1 compatibility report/review portions, Task3 adopted authority R1 lock/holder/worker constraints and pre-gate R2 operation authorization. Read Task7 section 9 and root rulings only for scoped ownership compatibility.

Named source-risk checks used read-only git show at **3e393dbc9485b187b89bfc03abd88821a0aabddb**: TaskQueue, WorkerLoop, and relevant JdbcTaskQueue construction/domain/lease/completion/attempt code. Invocation-local safe.directory did not change Git configuration. No changing Task3 source was adopted. No tests, DB, Docker, private environment, fixture operations, source/Git/index/HEAD mutation or subagents. Only this requested report was written. An initial report write failed because PowerShell's provider-qualified current directory produced an invalid path; it wrote no file and was corrected to explicit UNC.

## T5DR-01 — ADDRESSED

**R1 locations:** sections 1.1–1.5; lines 19–59, 63–74, 95–116 and 120–131.

The repair specifies an executable queue-to-writer path. Private ValidatedLease preserves the genuine DomainScope. Only the queue creates AfterTask, after authentic registered acquisition, complete task locks and the exact unfinished-attempt check. Its guards bind the initialized queue and registered policy, exact configured DataSource, actual active transaction holder, authentic preparation issuer, purpose/full immutable specs, current task/owner/attempt and complete prelocked graph. Registered verification consumes the retained preparation; it neither reconstructs authority from PreparedRaw nor reacquires roots.

The package-private installConfined factory resolves queue/policy identity before exposure. Registrations are copied/validated before initialization permits operations; failure publishes nothing. Existing public construction remains available for old registrations. The private production policy retains queue identity only for comparison. There is no public authority setter/mint, replacement issuer, ThreadLocal/global cache or recursive acquisition.

RawCarrier carries data and its get always rejects. bindCompletion requires its private class/exact policy owner and creates one same-call RawInvocation retaining the actual context and exact plan. Queue alone changes phases. write and validateActual require exact invocation/carrier/holder/spec/task/attempt and corresponding phase, plus separate one-use guards. CLOSED contexts cannot be reused. This closes the previous gap between authentic queue acquisition and the private RAW writer; arbitrary same-source transactions cannot directly invoke a successful carrier.

Actual output validation runs after writes in that same invocation: complete new RAW/segment readback, exact prepared comparison and registered provenance checks precede task/attempt/audit/outbox and final flush. The new-row exception grants no insert authority. Production SUCCEEDED through ordinary complete, old Runnable or arbitrary Supplier is rejected; no-write failure/retry carriers have a bound private invocation. Existing unrelated Task1 callbacks retain their order and behavior.

**Core envelope:** frozen WorkerLoop wraps preparation in a lambda, which would erase RawCarrier and invoke its inert get. The additive core PreparedCompletionEnvelope solves that concrete compatibility problem: the private contextual wrapper preserves the exact payload and performs lifecycle admission after DB checks, before WRITING. It has no DB-authority methods. A public fake envelope/no-op admit cannot mint a private carrier, AfterTask or genuine held graph; retained carrier use still requires fresh queue acquisition. Exact wrapper/payload capture, one unwrap and recursive/null rejection prevent substitution. Old PreparedHandler/factory remain on their original branch; unsupported default queues fail before effects. This is a narrow cross-module data/lifecycle protocol, not a public permission API.

Section 4's direct-call, foreign-holder/issuer/spec, expired-context, repeated-invocation, interleaved-plan and validation/flush-fault probes directly target this finding. They remain prospective implementation evidence.

## T5DR-02 — ADDRESSED

**R1 locations:** sections 2.1–2.4, lines 135–167; proofs at lines 191–201.

The chosen phase-and-drain ordering removes the inversion. A tick selects under the monitor, executes JDBC outside it, then publishes the exact in-flight result under the monitor. Completion enters DRAINING_RENEWAL, excludes new ticks and waits without DB transaction/locks, releasing the monitor during condition wait. Only after in-flight=false and a fresh loss/close check may DISPATCHED enter the queue. Completion therefore cannot hold DB roots while waiting for an ordinary renewal that needs those roots and owns its admission monitor.

A running renewal failure becomes sticky loss before dispatch. Queued/late ticks do no JDBC after drain/handoff and cannot turn terminal success into loss. Close can select while renewal is blocked because JDBC holds no monitor. Close/loss/cancellation before admission produces zero RAW INSERT; admission first allows only atomic authorized completion or rollback, not retraction of committed effects. Stream abort/join remain outside the monitor.

DISPATCHED sets completing before the sole queue call. False return, exception and unknown ACK cannot trigger second ordinary completion/quarantine. The final heartbeat pause does not extend authority: actual DB expiry must be checked freshly after lock waits; expired/revoked authority denies callback entry. Lease20s/heartbeat5s/poll5s and finite DB/drain/shutdown limits remain; no extra scheduler or root-held parse/archive/hash/I/O is introduced.

**Task7 ownership:** R1 supersedes only Task7 section 9's ordinary-renew-over-monitor shorthand. Future begin uses this same completed drain before MARKING. Selected ordinary renewal finishes before marker selection; queued renewal cannot run afterward. MARKING still precludes ordinary completion, including unknown marker ACK. Independent marker commit, one operation tracker, OPERATION/receipt-only handling, no resend and durable permit settlement remain intact. The refinement preserves the adopted ownership guarantees without activating Task7 behavior or releasing permits on Task5 close.

The barrier matrix covers the original root/heartbeat/callback cycle, running and queued ticks, genuine loss, expiry during lock waiting, close before admission, late terminal tick and unknown ACK. Progress must be proved without DB timeout breaking a cycle.

## Introduced-breakage checks and retained gates

No new actionable design regression was found in package ownership, factory initialization, core envelope, scope phases, lease freshness, single completion or the Task7 handoff refinement.

The stricter attempt update rule is qualified by ending an authenticated current unfinished attempt. Frozen JdbcTaskQueue lines 105, 107–108 and 139–143 show why it must remain state-aware: retry leaves READY with a positive already-ended attempt, and expired-at-limit currently reaches a second endAttempt. R1 expressly distinguishes already-ended reclaim and preserves old behavior. Its qualified rule is not a blanket requirement to end every historical positive attempt again. Preserve READY-after-retry stale/cancel cleanup without rewriting that ended attempt, alongside attempt0 and expired-at-limit handling. This is the compatibility reading of the accepted transition rule, not an additional finding or permission for zero rows when a genuine unfinished attempt must end.

All original accepted requirements remain: measured bytes/all decoded UTF16/segments with old parser compatibility; exact original manifest/config/archive/source after default changes; Task3 pre-gate R2 authorization; producer-version RAW semantics; actual-output validation after writer; atomic rollback and flush-last; truthful installed health; finite settings and fixture custody. Untouched observations are deferred rather than reopened in this bounded review.

Mandatory prospective gates remain: concrete cancellation before local success/error/metadata bodies can block; real local and S3 stalled-body abort/unblock and exactly-once permit release; genuine local/S3 upload -> intake -> actual queue/WorkerLoop/parser/MySQL RAW proof; and fresh-JVM recovery of the same READY task with original archives/input after defaults change. Header timeout, interruption, mocked success, SQL-seeded RAW or this review cannot substitute. Unknown commit ACK stays unknown until durable rediscovery; it is neither proof of rollback nor permission to repeat completion/send.

## Disposition

Adopt R1 as the bounded design repair for **T5DR-01** and **T5DR-02**, retaining all unaffected accepted requirements and future qualification gates. No implemented/tested/runtime PASS. B01 IMM PARTIAL, B04 Q401 UNKNOWN and company/HA/native-I/O/capacity boundaries are unchanged.
