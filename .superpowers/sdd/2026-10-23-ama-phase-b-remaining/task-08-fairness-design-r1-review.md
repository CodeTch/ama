# Task 8 fairness design R1: scoped independent re-review

Date: 2026-10-23. Scope: T8DR-01 arithmetic/proof/prospective-test correction and defects introduced by that revision only. Reviewed R1 design SHA256 `4eefdd7da3abf58f13d0fe7e15fbe07c1740fa035f08bb5e60b0a708b9a08284`.

## Verdict

- **T8DR-01: ADDRESSED.**
- **SPEC: PASS for this bounded design revision.**
- **Design quality: PASS for this bounded design revision.**
- **Introduced findings: 0 (P0 0, P1 0, P2 0, P3 0).**
- Unrelated observations: none reported.

These verdicts remove the original review's sole arithmetic correction blocker. They do not independently reopen or expand its unchanged ordinal, DDL, authority or lock-order assessment. FUP-B05-CANDIDATE-01 remains OPEN. No ordinal adoption, migration allocation/freeze/application permission, implementation completion, executed test evidence, fairness closure, runtime or capacity qualification follows.

## Assessment

R1 section 7 accounts for the two explicit session SET statements on each attempted top-level candidate q.work. Direct source inspection confirms JdbcTaskQueue.work delegates to TransactionRunner.required, whose callback executes SET NAMES and SET time_zone before candidate work. They correctly contribute up to 2*C explicit SQL statements and zero result/domain-mutation rows. Setup failures can execute fewer statements; no additional candidate retry is introduced.

With M maximum queries, D page queries, C consumed/attempted candidates and L returned discovery rows, the revised explicit SQL ceiling M+D+C*(11+Q_policy) and row ceiling M_rows+L+C*(9+R_policy) are consistent with the declared helper. Their conservative envelopes are respectively `1+ceil(B/P)+B*(11+Q_policy)` and `1+B+B*(9+R_policy)`. The maximum-query result row is included. Counting returned discovery rows separately from consumed rows correctly covers unconsumed prefetched rows on early success. Repeated reads count once per returned row per statement, rather than once per identity.

The early due check is explicitly constrained to a timestamp expression/projection within the already-counted initial read. Choosing an additional clock query requires adding its statement and row cost, up to B in each envelope. The fresh post-lock clock read remains in the nine fixed business statements. Q_policy/R_policy must cover the complete bounded registered preparation/root/input closure, including explicit SQL from policy wrappers and nested REQUIRED session setup. Top-level SETs are excluded from Q_policy only because they are separately counted. Driver-generated transaction/control work is separately traced and declared; the formula makes no claim to include it.

The worked prospective traces are arithmetically correct:

| Scenario | Explicit SQL | Result/mutation rows |
|---|---:|---:|
| Opening successful P=B=1 | 1 maximum + 1 page + 9 fixed + 2 SET = 13+Q | 1 maximum + 1 page + 9 fixed = 11+R |
| Opening four unregistered skipped rows, P=2, B=4 | 1 maximum + 2 pages + 12 candidate reads + 8 SET = 23 | 1 maximum + 4 page rows + 12 candidate reads = 17 |
| Four-candidate conservative envelope with Q_policy=R_policy=0 | 1+2+4*11 = 47 | 1+4+4*9 = 41 |
| Active-pass final empty page, C=0 | 1 page = 1 | 0 |

The successful-policy Q/R remain symbolic actual closure costs; they are not replaced with zero. The final-empty scenario is compatible with a consumed last position whose candidate exception leaves the pass active, and does not require an additional maximum query or same-call wrap. Section 8 explicitly requires all three trace scenarios and the corrected ceiling assertions in later evidence. This addresses the original prospective-evidence request without misrepresenting arithmetic review as JDBC execution.

The newly explicit backlog example also checks: ceil(100001/64)=1563 calls, with 1562 intervening five-second empty-poll intervals totaling 7810 seconds (about 2.17 hours), before query/control time. It preserves the existing tradeoff and root adoption gate and introduces no algorithm change.

## Exact read identities and limits

All paths below are relative to `/home/tanren/ama/.worktrees/phase-b-completion`. Raw SHA256 values were independently read with Get-FileHash.

| Read artifact/source | SHA256 |
|---|---|
| .superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-08-fairness-design-review.md | 7c78bf8608ff0019bf45c21174badb6e40d83a547ba1f5d16d223f2616a99120 |
| .superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-08-fairness-design-r1.md | 4eefdd7da3abf58f13d0fe7e15fbe07c1740fa035f08bb5e60b0a708b9a08284 |
| .superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-08-fairness-design-r1-closure.md | d5438fd0eb8b6fffc21997580a1ba4af723f17e479afa9968daae59f53a0a768 |
| .superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-08-r1-review-diff.txt | 10e2d759dd92907c7c3f508499c51cff61efe11928eca5bf5ce9af854455294b |
| backend/adapters/src/main/java/ama/db/TransactionRunner.java | c68bab5254f79bf0927e8dcf537a97aa6afca4397791e892b1ac140bae4a14ce |
| backend/adapters/src/main/java/ama/task/JdbcTaskQueue.java | 83c2ba70b1a80449e83e49b0e5bf6efbd4574c7597bc687d2c41721768794b14 |

Read the full original review, full R1 design, entire bounded diff and closure note. Source inspection was limited to TransactionRunner and the opening JdbcTaskQueue range containing q.work/q.now; whole-file hashes identify their bytes, not whole-file implementation review. The historical original design identity remains the original review's recorded SHA256 `309f7513f6c2b64db67b4469ebac7ba028c38dcc54c2c070d765bd7e02248d74`; no fresh full original architecture review is claimed.

No implementation/source edits, tests, DB/Docker operations, private-environment access, Git commands/mutations or subagent dispatch were performed. Only this assigned review report was written. All later implementation and operational gates remain in force. This report's own SHA256 is returned separately.
