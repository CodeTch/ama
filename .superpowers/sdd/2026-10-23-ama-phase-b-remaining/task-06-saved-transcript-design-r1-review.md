# Task 6 saved-transcript design R1 — bounded independent re-review

Date: 2026-10-23. DESIGN REVIEW ONLY.

**SPEC verdict: PASS.**

**Design/quality verdict: PASS.**

**T6DR-01 (P1): ADDRESSED. T6DR-02 (P2): ADDRESSED.**

New findings introduced by R1: none. No deferred minor observations were raised. These verdicts cover the two original findings and regressions introduced by the supplied R1 diff; they are not a new broad review or implementation/runtime acceptance.

## Scope and verified inputs

Read task-06-design-brief.md first, then the original review, complete bounded diff, revised design and closure. Re-read numbered slices where initial output was truncated. All W-relative references below use `.superpowers/sdd/2026-10-23-ama-phase-b-remaining` in the coordination checkout. `R1` means `task-06-saved-transcript-design-r1.md`.

Independently calculated SHA256 values:

| Artifact | SHA256 |
|---|---|
| Preserved original task-06-saved-transcript-design.md | 1734217a8d6954a8d408b30a3f5912c1e93f1c9592b2313375d80af2f6389a3e |
| Original task-06-saved-transcript-design-review.md | 79b757cd0074086378e5744d7a3ff938474b6e5a3c9769c5e0e0096c2cda462d |
| task-06-design-r1-review-diff.txt | 753ef37d2e34d62778f6d7a38ccab6415cf292ea873a8962a758712196cad542 |
| R1 | b2c023a2ee8639c668a078cfccce83f712c3dc99d65f9a64fab5a32a98687253 |
| task-06-saved-transcript-design-r1-closure.md | b1485d695509a99d8361483c3bc41d1f542f72a13ed2964dc625d40eee6e6e01 |
| docs/superpowers/specs/2026-10-23-ama-phase-b-pre-gate-read.md | 5d99cc891056b310513674592a67c97165fbcce0967d277d32cb9a323c4468d4 |
| artifacts/phase-b/b-remaining/controller-pre-gate-design/task-03-pre-gate-read-addendum-r2.md | bba6bc94672bf7cd94d428468a458f6585a4b7f52e3ce1ba88ecb249ae4de476 |

Focused source checks covered V011 relationships/indexes, TransactionRunner, the adopted R2 wrapper/full addendum, and the B06 stored-history distinction. JdbcProcessingFactsReader was inspected only through read-only `git show` at frozen commit `3e393dbc9485b187b89bfc03abd88821a0aabddb`; active Task 3 implementation was not reviewed or treated as adopted authority. Command-local safe.directory did not change persistent Git configuration. No tests, DB/Docker operations, private environment reads, source edits, Git/index/HEAD mutations or subagents were used. This report is the only authored file.

## T6DR-01 — ADDRESSED

The original review required an operation-specific split between stored inspection, original bytes and new business authority (`task-06-saved-transcript-design-review.md:23-29`). R1 replaces the universal current-byte prerequisite in its complete closure at `R1:88` and makes the split executable in the operation matrix at `R1:98-111`.

- `R1:102` retains fresh B03 owner EDIT, exact same-meeting RAW/WORKING identity, complete bounded stored closure, original file/session/member metadata, required profiles/archives and actual successful producer integrity. Original input/base/cancel remain historical. Current upload epoch and route eligibility no longer deny retained DB text alone; published immutable history remains readable only with fresh B03 permission.
- `R1:109-111` explicitly forbids using registry.read/open or a byte-eligible source receipt to obtain the inspection proof. It retains metadata acquisition in the existing gate/rank, separates sourceBytes observations, treats unknown byte support as unavailable, and fails corrupt/missing provenance or required archives. An inspection proof cannot become source or mutation authority.
- `R1:103-107` retains current-member projection predicates, full B04 source proofs for both explicit byte routes, new-business owner/CAS/lifecycle/source/readiness predicates, and historical replay without pointer authority. `R1:231-237` applies the split to the actual owner detail/marks/confirmation and source routes. No member history, CANDIDATE, admin or UUID-possession bypass is introduced.

This matches the distinction at `docs/acceptance/b/remaining-preflight/b06-preflight.md:85`. The frozen `backend/adapters/src/main/java/ama/transcript/JdbcProcessingFactsReader.java:172-186` also corroborates that the retained ContentFile facts are id/meeting/hash/size rather than a current byte-opening grant; it does not establish that the new saved reader already exists. Closure `task-06-saved-transcript-design-r1-closure.md:17-21` accurately summarizes the actual revision.

`R1:308` specifies the paired owner-inspection success / explicit-source failure with zero opens, including immutable published history, corrupt provenance negatives and unchanged new-business predicates. These remain prospective tests.

## T6DR-02 — ADDRESSED

The original review required explicit independent transaction phases and a stable pre-transfer bound, including final comparison and empty child sets (`task-06-saved-transcript-design-review.md:35-45`). R1 now supplies both.

1. **Transaction phases.** `R1:115-117` defines one genuine identity/subject/meeting acquisition per transaction and the complete rank. `R1:123-125` gives TX-S detached acquisition, authorization before deferred discovery failure, protected bounded transfer and commit/expiry. `R1:129-134` moves all original archive I/O, parsing and canonical/producer/body hashing into A with no retained roots, then gives TX-F one fresh gate and complete protected remeasurement/retransfer/exact comparison. Mutations consume their existing idempotency/auth gate through private held methods (`R1:194`). Read/command responses follow successful commit. `R1:138-140` distinguishes the already-held DB-only transfer helper and genuine one-transaction metadata/receipt operations, without creating a third rename-only gate or bypassing full-route archives. Original replay transactions remain distinct and mandatory (`R1:134`, `R1:186-188`).
2. **Stable LONGTEXT acquisition.** `R1:150-158` selects retained actual X row/parent locks plus mandatory root writer discipline and FK protection, without relying on unspecified gap locking. Revision parents precede bounded segment metadata enumeration; every segment row is locked before mark enumeration. Empty marks are protected by the segment parent lock. Body/manifest lengths and aggregate budgets are reserved before content-bearing SELECT, and transfer is restricted to the exact already-locked PK set. Existing rows cannot grow between measurement and transfer. The same discipline explicitly governs TX-F comparison and new-row readback. getCharacterStream and post-transfer equality are secondary checks, not the pre-transfer proof.
3. **Source-opening protocol.** `R1:144-146` preserves Task 3 R2's exact final core selector type floor: CurrentReadRequest READ, HistoryRequest/WorkerRequest additional EDIT on the same handle. The entire optional dispatch outcome precedes one final gate; the actual configured hook may restrict further, while missing/legacy/ambiguous support produces deferred Fatal without a substitute provider. Only Prepared enters complete acquisition/finalization, successful commit and private postcommit open. No S/F wrapper is added to Task 3 opening. This matches the adopted wrapper `docs/superpowers/specs/2026-10-23-ama-phase-b-pre-gate-read.md:7-13` and full R2 `artifacts/phase-b/b-remaining/controller-pre-gate-design/task-03-pre-gate-read-addendum-r2.md:15-29`.

The concrete schema matches the proposed parent protection: `backend/adapters/src/main/resources/db/migration/V011__transcript_schema.sql:75-95` defines indexed composite segment/mark keys; `:143` supplies segment-to-revision FK; `:167` supplies mark-to-segment composite FK. `:149-151` supplies confirmation parent relationships, while root serialization remains mandatory for application-level completeness. `backend/adapters/src/main/java/ama/db/TransactionRunner.java:14-25` creates the transaction template and connection settings without selecting a special isolation level. Frozen JdbcProcessingFactsReader `:116-122` measures manifest lengths with FOR UPDATE and `:155-168` transfers/validates under retained row protection. These checks substantiate the design's chosen mechanism; they do not prove installed FK enforcement or executed concurrency behavior.

Closure `task-06-saved-transcript-design-r1-closure.md:23-29` matches the actual changes. `R1:312-316` contains the required phase, JDBC transfer, concurrent body growth/child insertion/empty-mark, final-comparison and R2 denial/zero-open traces, all explicitly prospective.

## Introduced-regression check and remaining gates

No new blocking regression was found within the bounded diff. Shared private issuer/DataSource/actual holder/gate checks remain binding (`R1:65-82`); detached data cannot authorize. Complete source/task union and all tasks before attempts remain ranked (`R1:117`). Original archive/profile/fingerprint closure and byte-compatible confirmed/draft hash requirements remain (`R1:163-180`). Original immutable copy/mark invalidation, replay-first/current-confirmation identity, correction qualification, current-member projection, no-store and Task 10/11 activation constraints remain (`R1:41-61`, `R1:184-219`, `R1:243-257`). Approval is bounded to design closure; the original review's preserved constraints remain binding.

Before source dispatch, retain `R1:298-302` and the original review `task-06-saved-transcript-design-review.md:62`: adopted Task 3 issuer/rank/held-helper alignment; Task 5 actual successful RAW output kind and RAW.revision = immutable producer businessVersion allocation; qualified positive effective budgets and correction installation state. The R1 storage inspection purpose must be implemented in that shared issuer, not as an alternate DTO-based authority.

Implementation acceptance still requires actual MySQL JDBC SQL/transfer instrumentation demonstrating no oversized LONGTEXT reaches the driver, retained row/parent protection through both acquisitions, installed FK/root-writer discipline including Task 5, complete empty/nonempty mark sets, cancellation/rollback, one gate per transaction and no archive/parse/hash beneath roots. Exercise paired historical-text/byte-route outcomes and the R2 permission/error-precedence matrix. Preserve native+S3 actual pipeline/fresh-JVM evidence, pure hash/graph/config golden regressions, full HTTP ACL/token/no-store, CAS/idempotency/confirmation and relevant meeting/source regressions. Task 10 disposition and Task 11 publication freeze must integrate before those runtimes activate. None of these tests or runtime qualifications was executed by this review; no new product approval or SQL allocation follows.