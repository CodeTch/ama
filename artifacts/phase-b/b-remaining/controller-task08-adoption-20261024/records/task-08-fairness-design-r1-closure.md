# Task 8 design R1 closure record

Date: 2026-10-23. Scope: T8DR-01 (P2), resource accounting/proof and prospective tests only. Status: correction supplied for root's scoped independent re-review; no independent PASS, implementation, fairness closure or ordinal adoption is claimed. FUP-B05-CANDIDATE-01 remains OPEN.

## Inputs and exact output identity

| Artifact/source | SHA256 |
|---|---|
| Original task-08-fairness-design.md, preserved byte-identical | 309f7513f6c2b64db67b4469ebac7ba028c38dcc54c2c070d765bd7e02248d74 |
| Full independent task-08-fairness-design-review.md | 7c78bf8608ff0019bf45c21174badb6e40d83a547ba1f5d16d223f2616a99120 |
| New task-08-fairness-design-r1.md | 4eefdd7da3abf58f13d0fe7e15fbe07c1740fa035f08bb5e60b0a708b9a08284 |
| backend/adapters/src/main/java/ama/db/TransactionRunner.java | c68bab5254f79bf0927e8dcf537a97aa6afca4397791e892b1ac140bae4a14ce |
| backend/adapters/src/main/java/ama/task/JdbcTaskQueue.java | 83c2ba70b1a80449e83e49b0e5bf6efbd4574c7597bc687d2c41721768794b14 |

Report paths are in `.superpowers/sdd/2026-10-23-ama-phase-b-remaining` under `/home/tanren/ama/.worktrees/phase-b-completion`. This closure file's own SHA256 is returned separately, avoiding self-referential hashing.

## T8DR-01 correction mapping

- Source verified q.work delegates to TransactionRunner.required (`JdbcTaskQueue.java:26`); actual callback executes `SET NAMES utf8mb4` and `SET time_zone = '+00:00'` (`TransactionRunner.java:23-27`). No source edit requested or made.
- R1 section 7 distinguishes business/policy SQL, the additional 2*C top-level session SQL, separately measured driver/transaction controls, and result/mutation rows. Explicit SQL envelope is `1+ceil(B/P)+B*(11+Q_policy)`; row envelope is `1+B+B*(9+R_policy)`, including maximum-query result. Policy closure must also declare any nested wrapper SQL rather than hide it.
- Early due check uses a timestamp projection/expression within the already-counted operation read; a separate early clock query requires explicit added statement and row costs (worst case +B to each envelope). The fresh locked clock query is already one of the nine business statements.
- R1 section 7 includes worked opening-success (1,1), multi-skipped (2,4) and active-pass final-empty counts. Section 8's statementAndRowBudgets selector now requires these actual future trace scenarios with SET/max/policy/driver distinctions.
- Root has not adopted the ordinal. Existing terminal-history latency limitation is explicit: 100,000 prefix rows plus tail, B=64, takes 1,563 calls and about 7,810 seconds of five-second empty-poll spacing before DB time. No latency SLA is invented. No algorithm, DDL shape, authority/lock protocol or migration gate was changed.

## Design verification actually performed

Read full independent review and verified its hash; read TransactionRunner and q.work source, verified their hashes; reread revised budget section; calculated small arithmetic locally, without JDBC/DB/test execution:

| Scenario | Explicit SQL | Result/mutation rows |
|---|---|---|
| Opening successful (1,1) | 1 maximum + 1 page + 9 business + 2 SET =13, plus actual Q_policy | 1 maximum + 1 page + 9 fixed =11, plus actual R_policy |
| Four unregistered skipped candidates (2,4) | 1 maximum + 2 pages + 12 discovery + 8 SET =23 | 1 maximum + 4 page rows + 12 candidate read rows =17 |
| Conservative four-candidate envelope, Q_policy=R_policy=0 | 1+2+4*11=47 | 1+4+4*9=41 |
| Active-pass final empty page | 1 page; no q.work/SET/max | 0 |

These are source-grounded prospective counts, not passing integration traces. A registered successful policy must supply real Q_policy/R_policy; zero is not substituted. Driver/control work is separately recorded in future trace evidence and excluded from the displayed explicit-SQL arithmetic. Original design hash was rechecked after writing R1 and remains unchanged.

No implementation source edits, DDL file, DB/Docker/private-environment access, tests, Git commands/mutations or subagent dispatch occurred in this correction round. Only the R1 design and this closure note were created. Remaining gates: scoped independent re-review, root ordinal/backlog decision, deployment budgets, root migration allocation/freeze/apply ACK, owned-fixture verification and later independent implementation review.