# Task 3 pre-gate read addendum R2 — bounded independent design re-review

Date: 2026-10-23. Scope: only the PRD-01 unresolved-provider authorization residual and regressions introduced by its R2 closure. This is a design review, not review of active Task 3 implementation or implementation/runtime qualification.

**PRD-01: ADDRESSED.**

**SPEC verdict: PASS within the bounded design scope.**

**Design/quality verdict: PASS within the bounded design scope.**

**Introduced findings: 0 (P0: 0; P1: 0; P2: 0; P3: 0).** No residual PRD-01 finding remains in this design revision.

## Reviewed evidence

Read the complete prior R1 review, R2 addendum, R2 closure and bounded R1-to-R2 diff. Independently verified their raw-file SHA256 values:

| Artifact | SHA256 |
| --- | --- |
| task-03-pre-gate-read-addendum-r1-review.md | 2b829d12b48ac59ca45695fb384722bdf098ecdfcc725c962cab814fb761e0d9 |
| task-03-pre-gate-read-addendum-r2.md | bba6bc94672bf7cd94d428468a458f6585a4b7f52e3ce1ba88ecb249ae4de476 |
| task-03-pre-gate-read-addendum-r2-closure.md | 2c0cb26f9a8415b23e0cdb1781e6c0be3448c1dead3d3f175827cf2deda896ce |
| task-03-pre-gate-r2-review-diff.txt | da57f8c4454e677f6988337ae5c402914aeaa5f87485e412f1b7367bf921a363 |

Also read the complete original pre-gate addendum, R1 addendum and adopted task-03-authority-design-r1.md to check inherited constraints. The residual is a textual contract gap; source inspection was not needed to decide its closure. No tests, DB, Docker, private environment, source/index/HEAD writes or subagents were used. This report is the sole new artifact.

## PRD-01 closure reasoning

The prior residual required an executable authorization participant when a valid known-meeting history/worker request has no usable complete preparation provider. R2 now places the minimum permission decision in the registry, which already has the genuine B03 access collaborator and gate. Its closed mapping of exact final record types selects only the existing permission requirement: CurrentReadRequest requires READ; HistoryRequest and WorkerRequest additionally require EDIT on that exact held handle. The minimum therefore remains available when registration is absent, legacy-only, unsupported or ambiguous.

The entire optional dispatch stage is now inside the private outcome boundary: configuration/provider selection, support checks, bounded discovery and preparation. A valid recognized unsupported request is Fatal, not malformed syntax. The final sequence first acquires the single fresh READ gate, then applies the selector minimum with existing access.require. Only after these checks and any available actual-provider authorizeRead may the stored outcome be observed. Consequently outsiders/revoked members encounter READ denial first, and ordinary members encounter EDIT denial first for history/worker; an appropriately authorized caller receives only safe Fatal. There is no dependency on constructing or substituting an authorization provider.

The prospective matrix covers all three selectors, missing/legacy-only/ambiguous or failed resolution, and outsider/revoked/member/owner roles. Its one-gate and zero-complete-downstream-acquisition/open assertions directly cover the prior residual. These are requirements for later implementation evidence, not executed results.

## Introduced-regression assessment

- **Selector trust and operation confusion:** public records remain forgeable data claims. The exact final type mapping cannot be weakened by an overridden method, caller action/boolean or provider result. History/worker reference kinds are fixed. A CurrentReadRequest is constrained to current-WORKING semantics even when its IDs originate from another graph. It cannot mint history/worker scope. Exact selector, actor, meeting, holder, preparation, actual configured provider/authority lineage and complete locked graph remain mandatory for success.
- **Authority dispatch:** the registry mapping supplies a permission floor, not complete-protocol participation or source authority. This preserves the adopted distinction between caller claims and privately authenticated configured-policy dispatch. A present abstract authorizeRead can restrict further, cannot weaken the floor, and retains R1's prohibition on discovery, domain/source/task acquisition, hashing or parsing. An absent provider is never replaced. Existing B03 performs both checks; no duplicate ACL or second gate is introduced.
- **Failure sequencing and locks:** Fatal/Ineligible do not enter the complete prelock, source receipt, task/attempt, pin or open path. Prepared alone continues the inherited exact graph, full source/task union, finalization, commit and private-open protocol. No late provider selection, root discovery, hash/parse under roots, retry or transferable preparation authority is added.
- **Generic and legacy behavior:** minimal AVAILABLE-file-to-meeting lookup/NotFound remains. Generic complete discovery is current-only and defers provider/composition failures until READ. Fatal cannot fall back to a legacy reference; genuine current Ineligible may use only an independently valid unchanged legacy reference through its original checks and commit. The standalone legacy constructor/download acquires no new complete budget, selector, preparation or authorization-hook prerequisite. New planned reads against legacy-only support remain unsupported after their required authorization. Explicit complete assembly still rejects legacy INPUT_BATCH_FILE overrides and invalid/missing complete budgets.
- **Inherited exits and bounds:** cancellation/interruption still terminates promptly rather than entering deferred authorization, retry or reuse. Immediate syntax/ambient-transaction rejection and closed infrastructure failure remain inherited. The original bounded observations, exact full reference comparison, private outcomes, safe error categories, no raw graph disclosure, no source grant from failed preparation and distinct historical-producer/live-worker proofs remain binding.

## Disposition

Accept the R2 design as closure of PRD-01. No new finding IDs or follow-up design changes are required in this bounded scope. This verdict does not establish implementation correctness, test success, runtime acceptance or Phase B completion.
