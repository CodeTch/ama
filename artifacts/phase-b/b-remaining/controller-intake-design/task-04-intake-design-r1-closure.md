# Task 4 intake/API R1 — design finding closure

2026-10-23. Design-only correction report; independent bounded re-review/root adoption still required. No implementation, tests, Git/DB/Docker/private environment mutation, subagents or new DDL.

## Exact artifacts

| Artifact | SHA256 |
|---|---|
| Preserved original task-04-intake-design.md | 3626aad723401de14d633d28a200138aa2cdeb233fc44ef961c348164296a574 |
| Preserved complete independent task-04-intake-design-review.md | 37b9a2c422a2af686f12511e39414260dc1741a7a5f910f42a70dac72df52279 |
| New full replacement task-04-intake-design-r1.md | fcbde3fa7885d0fe3553646c2fbec28e9ad6da78aba4678f8e29461c3f35666b |

Original and review were read and independently hashed. Review has exactly two P2 findings, both accepted. Targeted unchanged JdbcFileUploads.complete/abort and FileBackendCatalog.require were read to preserve operation-specific behavior. Active Task3 facade/authority implementation was not inspected; source names remain provisional. No subject/meeting inversion is asserted: accepted B03 subject-before-meeting ordering and genuine already-held subject re-lock remain unchanged.

## Closure matrix

| Finding | R1 replacement | Design disposition |
|---|---|---|
| T4DR-01 (P2), optional early replay can reject an accepted command | Section3 step3 mandates lookup before current-CAS/exhaustion/source/archive/intake readiness. Section12 specifies fresh actor authorization/exact scope-body matching, committed persisted-result reconstruction, MISS closing transaction, independent replay construction, one bounded recovery lookup after preflight failure and exact concurrent-winner outcomes. Final serialized claim replay still precedes new-intake predicates. | ADDRESSED, pending independent re-review. |
| T4DR-02 (P2), incorrect FINALIZING abort advertisement | Section7 UploadReceipt removes FINALIZING abort/parts and adds separate terminal idempotentRetries. Section13 freezes state/operation matrix: FINALIZING COMPLETE only, including valid historical READ_ONLY recovery; OPEN parts/new completion requires historical WRITABLE; abort cleanup separately uses recovery route; terminal acknowledgment/cleanup follows actual early-return checks. | ADDRESSED, pending independent re-review. |
| Manifest profile-list precision (review note, not third finding) | Section3 step7 and section14 explicitly require ordered kind/ref objects with exact archive Ref fields; no bare strings or bare refs. | CLARIFIED without new schema/protocol. |

## Added future evidence

T4DR-01 tests include immediate accepted replay, later revision/publication/source epoch/config/readiness changes, semantic integer/token/equal-dual and max handling; missing-versus-committed-versus-corrupt result distinctions; A-miss/B-winner/A-preflight-failure barriers; B uncommitted/rollback/timeout/late commit/different body; revoked actor; fresh lookup commit failure; final-claim concurrent replay; no new bind/enqueue/rekey/speculative202. Current intake unavailable must not remove replay route construction. These are tests to implement later, not passing evidence.

T4DR-02 tests pair real discovery with B04 endpoints across OPEN/FINALIZING/COMPLETED/ABORTED and WRITABLE/READ_ONLY/DISABLED/missing/mismatched historical routes, changed/default absence, epoch/publication/identity changes. Distinguish FINALIZING recovery, OPEN new-write checks, terminal same-manifest acknowledgment and ABORTED cleanup; verify no advertised FINALIZING abort, no restored byte permission and private/no-store results. Existing abort DB-then-storage cleanup behavior is preserved rather than incorrectly described as atomic.

All original accepted authority/full-spec/producer/rank/bounds/rollback/token/assembly/discovery contracts and inherited evidence gates remain binding. Task5/6/12 future worker/source/browser duties remain separate. Root should perform scoped R1 re-review; this closure does not grant implementation/runtime/schema/Phase-B completion approval.
