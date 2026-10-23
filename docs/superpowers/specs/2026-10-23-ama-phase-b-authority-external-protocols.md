# Phase B authority and external protocol adoption

2026-10-23. Technical refinements under the existing authorization to complete the local controlled B loop. These are design gates only; implementation, runtime, company, HA and capacity qualification remain distinct. The original architecture, Phase B plan and controlled-runtime Normative R1 remain binding.

## Task 3: genuine held authority

The exact immutable design/review sources are in `artifacts/phase-b/b-remaining/controller-runtime-design/`. Adopt `task-03-authority-design.md` with `task-03-authority-design-r1.md` superseding its affected signatures and sequences. R1 SHA256 is `6fcb1f1d816cd96d389f83e67b0ceaba3298c4657cda3ed8bdcc27f3ab6537e9`. Original review ADR-01..03 and the preserved earlier R1 remain historical; the bounded R1 re-review is SPEC PASS / design PASS, all three closed.

R1 requires status-only public identity capture in the same existing B03 issuer/ACL, with complete roster-name capture retained for repository use; an additive mandatory CompleteReadPolicy over unchanged legacy FileReferencePolicy; all task locks before any attempts; and exact issuer/source/holder/gate/phase/full-set authenticated source receipts consumed once. Actual first bind uses newly inserted production rows after every source FK parent is held. Intake clears transcript/final confirmations, sets needs_generation=1, preserves the saved transcript pointer and advances meeting/subject once. Historical successful RAW producer provenance is distinct from a live RUNNING worker lease.

No detached data object grants authority. Registered complete finalization and successful transaction commit precede the registry private storage pin/open. Task 3 does not deliver HTTP intake, an importer Worker or saved-transcript workflow; those remain Tasks 4 through 6.

## Task 7: external completion ownership

Adopt the exact `task-07-protocol-design.md` (SHA256 `35c1ef41e8764d973814b3cb54b3f25e4b205c19bcebdb420a3196182fdd664d`), `task-07-root-review-rulings.md` and `task-07-attempt-receipt-clarification.md`, with independent SPEC PASS / design APPROVED. Mandatory T7G1 through T7G8 in that review are future implementation/activation gates.

The observation transaction atomically owns actual domain output, durable_task, task_attempt, operation, execution, audit and outbox. Permit settlement remains a separate existing verified-terminal transaction AFTER observation commits, with bounded durable recovery; a crash retains capacity conservatively. This explicitly supersedes any compact plan wording suggesting a single permit/observation transaction. UNKNOWN never releases or blindly resends. Remote success with invalid local model output terminally fails local processing without a candidate; it never becomes zero-effect retry proof.

Prepared external policies and capabilities are registered/private and bind the real task/spec/holder/execution. Only generation has a narrowly authenticated stale-retention path. Generic fences remain strict; active stale/expired operation tokens stay STALE_TOKEN. Immutable stale output changes no current root, revision, confirmation or debt. Worker marking and ownership/heartbeat handoff share one monitor; after handoff no ordinary completion path runs.

Final confirmation computes current readiness before its one revision advance. Task 10 supplies the complete PENDING-candidate prelock/disposition helper to every affected business-version write. Publication in progress freezes content and candidate retention. Controlled providers must durably prove immutable result-ID/payload binding across restart before same-fact replay is eligible. No speculative new SQL or company protocol is authorized by this design.

For publication, task_attempt means only the queue table. A fact-only fenced observation invokes no domain writer, including publication_attempt. Task 11 must expose the separate authoritative remote outcome and unapplied business outcome and prohibit resend; later application of a recorded fact requires its own reviewed fresh gate.

## Execution order

Stable task IDs remain unchanged. Execute 1, 2, 3, 4, 5, 6, **8, 7**, 9, 10, 11, 12, 13: Task 8 closes reconciliation fairness before any external Worker activation. The plan ledger records actual completion and exact reviewed bases.
