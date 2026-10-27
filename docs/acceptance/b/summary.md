# Phase B acceptance evidence

This document distinguishes source verification from actual environment qualification. It does not declare Phase B accepted. The authoritative active task and review state is `.superpowers/sdd/2026-10-23-ama-phase-b-remaining/current-state.json`; this document records acceptance boundaries and supporting evidence.

## Scope

The approved workflow is [AMA-WORKFLOW-20261027](../../superpowers/plans/2026-10-27-ama-development-workflow.md). It combines related checks while preserving the required permissions, transactions, lock order, bounded acquisition, durable recovery, actual adapters and browser behavior. Work stops after B-L1 through B-L5; later phases are outside this authorization.

## Source evidence already adopted

| Feature boundary | Adopted source evidence | What this proves |
| --- | --- | --- |
| Meeting information and roster | B-L1 source adoption and independent final review | Separate save/confirm operations, actual category/people reads, immutable original retry, conflict drafts and identity-scoped recovery |
| Upload and intake | B-L2 source adoption and independent final review | Actual upload discovery/resume/reselection, complete ordered import manifest, bounded parallel uploads, frozen original requests and private recovery |
| Transcript, generation and workspace reads | B-L3 source adoption and combined independent review | Actual saved transcript APIs, per-instance intent/IME/draft guards, generation acceptance/recovery and bounded workspace/task composition |
| Minutes, review, final and publication | B-L4 source adoption and independent five-fix review | Actual saved text/elements/action tuples, evidence anchors/history/candidates/reviews/settings, final/publication contracts, read-only published owner and workspace-wide denial invalidation |

The exact file manifests, report hashes and command results are retained in the corresponding `bl1-source-adoption-20261027.md` through `bl4-source-adoption-20261027.md` records under the evidence work directory. The final B-L4 review closed all five reported defects. Relevant broad source checks include 241 Web tests, TypeScript checking, built frontend, 69 generated response-schema checks, and the prior 879-test offline Java run with its explicitly recorded historical-private-fixture exclusion. These counts are source/offline evidence; none substitutes for real MySQL, adapter, service restart, or browser evidence.

## Runtime evidence boundaries

Historical adopted evidence remains valid only for its recorded version, setup and assertions. The current gap inventory is `bl5-final-qualification-matrix-20261028.md`; `bl5-open-obligations-20261027.md` retains the original preflight inventory. In particular:

- Genuine local reconciliation closure and marker-response-loss UNKNOWN observations do not authorize reusing their historical resources or resending uncertain publication effects.
- The twelve representative write guards at genuine MARKED/UNKNOWN/SUCCESS-unapplied states are already adopted; they must not be relabeled as missing solely because an earlier inventory predates them.
- Actual application-to-controlled-provider TLS success and same-store/fresh-JVM readback do not prove every precommit crash, UNKNOWN or integrated restart scenario.
- Authentic legacy repair origin remains unavailable; synthetic business rows cannot replace that provenance.
- Real MySQL budget-before-transfer, lock ordering, withdrawal/races, generation atomicity, bounded reconciliation coexistence, and local/S3 HTTPS complete workflows require their own relevant evidence.

The fresh B-L5 plan is authorized for a maximum of **three hours from the first resource startup**. Human authorization is recorded with an exact plan hash in `bl5-human-authorization-20261027.json`. Other resource, request, memory, disk and identity restrictions remain unchanged. The independent source/harness gate must pass before activation. A runtime cap, an approval, a compiled harness, or a passing source review is not itself a successful runtime test.

## Retained qualifications and exclusions

B01 IMM remains PARTIAL; B04 Q401 remains UNKNOWN. Company SSO/directory/model/knowledge-service readiness, production identity authority, HA/capacity and native I/O failure qualification are not asserted by controlled fixtures. Visual pixel/viewport closure, host/storage failure qualification and full load/stability matrices retain the explicit later-phase dispositions in the approved workflow. Core Phase B permissions, transactions, application restart and critical crash/recovery requirements remain in Phase B.

No commit, merge or push is part of the current authorization. Final acceptance requires the applicable actual combined regression, resolution or explicit qualified disposition of required open assertions, and the sole independent final branch review.

## B-L5 local actual API result (2026-10-28)

The local API chain reached published `SUCCESS / APPLIED` through real HTTPS, native mTLS storage, Worker, controlled model HTTP and knowledge-provider TLS. It covered two uploaded files, eight ordered transcript segments, saved/confirmed transcript, generation, five required reviews, final confirmation and official document retrieval. Draft roster save versus confirmation, owner/member/outsider/role-only-admin access, stale generation CAS, member producer-ID omission, editor-intent admission and published-write denial were observed in the fresh batch. Exact scope and evidence are recorded in `bl5-local-runtime-results-20261028.md` under the work directory.

This result is **not browser qualification**. The temporary certificate warning still requires human handling. Enterprise action-owner selection returned `ENTERPRISE_SELECTION_NOT_INSTALLED`; crash/restart, concurrency, SQL rollback/budget and other open qualification items remain separate. Local services were stopped cleanly with all data retained before proceeding to S3. Phase B is still incomplete.

## B-L5 S3 actual API result (2026-10-28)

The fresh S3 API chain also reached SUCCEEDED / SUCCESS / APPLIED, using two actual uploaded files, saved/confirmed transcript, generation, reviews, final confirmation and official document retrieval. A single publication POST deliberately lost its client response; only status queries followed and recovered success. Owner document read succeeded; confirmed member was denied by the controlled provider ACL, while outsider/admin were denied by meeting ACL. Provider member-allow remains unqualified.

Additional actual evidence covers concurrent info saves, exact known-success idempotency replay without new external requests, member private-field omission, overlapping roster withdrawal with post-confirmation denial, concurrent two-file intake with a single durable accepted graph, stale generation with zero SQL mutation, and malformed second-file parsing with no partial RAW output. Concurrent generation yielded one success and one protected503 with no extra durable graph; its exact internal cause/phase is unproven. Test-client mistaken status assumptions and the bounded ingress503 remain retained, not hidden.

See bl5-s3-runtime-results-20261028.md and the final integration review package under the work directory. Browser certificate handoff, exact internal budget/lock/rollback and critical crash/full restart scenarios remain open. These API results do not declare Phase B complete.

## B-L5 batch closure (2026-10-28)

The one-time batch was settled at approximately 01:10 CST, before the original 02:42:33 CST hard deadline. Final monotonic elapsed check: 5272.683 seconds, below the authorized 10800 seconds. Both exact owned containers, all registered business processes and the watchdog were verified stopped; data and evidence were retained. Charged totals: 5 pipelines, 12 model, 4 publication and 1487 HTTP budget units. The browser handoff remained incomplete, and its temporary tab was closed. No commit, merge or push occurred.

The current batch is closed and must not be restarted using its consumed approval. The continuation entry is handoff-bl5-settled-20261028.md. Final independent review adjudicates the qualified results and remaining gaps; Phase B remains incomplete.

Final independent review completed: no new Critical/Important source defects, inherited SOURCE QUALITY PASS retained, actual API evidence adopted only within the report scope. Review SHA256: 10c1d9208b5aee4aa634a7d32d27a53357e8f9602db22648c63d2b77fbf84366. Controller adoption: bl5-final-limited-adoption-20261028.json. Phase B remains INCOMPLETE.
