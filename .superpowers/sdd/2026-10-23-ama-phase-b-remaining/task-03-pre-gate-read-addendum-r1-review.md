# Task 3 pre-gate read addendum R1 — bounded independent design re-review

Date: 2026-10-23. Scope: PRD-01 failure sequencing and introduced regressions. This reviews the design only, not live Task 3 source or implementation/runtime qualification.

**PRD-01: NOT ADDRESSED in full (substantially addressed; one P2 residual).**

**SPEC verdict: REQUEST CHANGES.**

**Design/quality verdict: REQUEST CHANGES.**

**Introduced findings: none.** The residual remains PRD-01, not a new architecture finding.

## Exact reviewed artifacts

Complete original addendum, original independent review, R1, closure and bounded diff were read. Independently checked raw-file SHA256 values:

| Artifact | SHA256 |
| --- | --- |
| task-03-pre-gate-read-addendum.md | a45b8390d5ea27216de6407fb35ebc17eb0cfa365bbd6edc12c4ec16792daa57 |
| task-03-pre-gate-read-addendum-review.md | 44fd8346289d8b3bb1f5a85f6012cb6a5a7a5dcf779d452ac97b737b8733eb3c |
| task-03-pre-gate-read-addendum-r1.md | 58e085bc64a85f89e9ff3c9bdabf8dce31d5feb2205ebed46b56dfa12101387a |
| task-03-pre-gate-read-addendum-r1-closure.md | ed8f20a6b1fd93f6aae25857acb4359e09c058e16adb1a4056a6ec4fcd681274 |
| task-03-pre-gate-r1-review-diff.txt | 3029d9af32acf4daccf3e7c0ac9388398ac00dd4f244a0e67839629d68d5a90d |

The adopted task-03-authority-design-r1.md was also read for inherited legacy/complete dispatch and same-gate requirements. Frozen-source inspection was unnecessary for this textual contract gap. No tests, DB, Docker, private environment, source/index/HEAD mutations or subagents were used. This report is the sole new artifact.

## PRD-01 residual — P2 — Define authorization when configured-provider resolution fails

**References:** R1 lines 17, 21, 27 and 37; closure paragraph 1. Original PRD-01 covers known-meeting requests, unsupported/missing protocol support and operation-specific authorization before observable stored failure.

R1 handles a provider's failed preparation correctly once that provider exists: retain failure privately, acquire one genuine READ gate, call mandatory authorizeRead on the same handle, then disclose the safe outcome. But line 27 first resolves the actual configured complete provider, then captures preparation failure. It does not define the outcome or authorization participant when resolution itself fails. Line 21 explicitly includes missing preparation support and unsupported composition as Fatal, while line 17 places the new operation classifier/EDIT check on the registered CompleteReadPolicy.

A syntactically valid recognized history/worker request for a known meeting can encounter an absent or legacy-only registration for its kind. There is no configured CompleteReadPolicy on which to invoke authorizeRead. Returning unavailable at resolution bypasses the promised fresh READ denial. Even interpreting resolution as inside the Fatal capture, acquiring READ and then throwing Fatal lets an ordinary READ-authorized member receive the stored failure before required owner EDIT denial. Missing support is not invalid selector syntax, and a working authorization subsystem is not system-wide inability to authorize. Those exceptions do not settle this case.

This is an incomplete failure-order contract, not a claim about live implementation behavior. The prospective requirement that EDIT precedes every stored preparation failure does not explain how it can be fulfilled without the provider. Generic discovery/composition errors are explicitly captured at line 31 and need only the registry READ gate; that branch does not have this unresolved EDIT dependency.

**Required bounded closure:** explicitly include configuration/provider resolution and discovery in known-meeting failure routing, and specify how trusted operation authorization remains available on the same genuine gate when preparation-provider selection/support fails. A construction/dispatch invariant may eliminate a reachable unresolved-provider state, but it must be stated and enforced for accepted recognized requests. Assuming successful resolution is insufficient. If unsupported recognized requests remain reachable, define READ and required EDIT precedence without a caller-controlled mode, substituted provider, second ACL implementation, second gate, late graph discovery, or reclassification as malformed input. Keep legacy-only construction/download independent of complete-provider budgets and preparation requirements.

Add prospective cases for valid known-meeting current/history/worker requests with missing registration, legacy-only registration/missing preparation support, and resolution failure, across outsiders, revoked members, ordinary members and owners. Either establish an explicit assembly invariant preventing the relevant request path, or show fresh READ and required same-handle EDIT denial precede its safe Fatal outcome. Assert zero domain/source/task acquisition and zero opens from the failed complete path. No broad redesign is requested.

## Accepted closure portions and regression assessment

- Private Prepared/Ineligible/Fatal outcomes are data only. R1 supersedes only failure routing and affected signatures; the original prohibition on caller-supplied preparations remains binding, with private issuer/configured-policy/request/actor/meeting checks.
- For a resolved complete provider, mandatory authorizeRead supplies fresh same-handle owner EDIT before history/worker graph failures; current requires READ. It is independent of preparation and cannot acquire domain/source/task rows, hash or parse.
- Fatal and Ineligible do not enter prelockRead or acquire complete-path domain/source/task authority, pins or bytes. Prepared retains complete locked-graph equality and original acquisition/finalization/commit/open requirements.
- Cancellation/interruption is excluded from deferred outcomes and terminates promptly without authorization continuation, retry or reuse. Syntax and ambient-transaction rejection stay immediate; infrastructure failure remains closed.
- Generic minimal AVAILABLE-file-to-meeting NotFound is preserved. Complete discovery/composition failures become Fatal and wait for READ. Finite bounds are explicit for complete paths; legacy-only construction/download adds no B06 preparation or budget prerequisite.
- Genuine current-ineligibility permits only an independently valid unchanged legacy reference after authorization and its original checks/commit. Fatal aborts even alongside valid legacy. No blanket NotFound/503 conversion or arbitrary-error fallback is introduced. The independent legacy path derives no authority from failed complete preparation.
- Original exact reference/graph comparison, bounded allocation/text projection, no hash/parse beneath roots, one gate, no retry, full source/task ordering, worker versus historical-producer distinctions and postcommit private opening remain binding. No introduced regression was identified in these preserved contracts.

## Disposition

Open: **PRD-01, one P2 residual; no new finding IDs and no P0/P1/P3 findings.** The closure's universal denial-precedence claim remains premature until provider-resolution failure has an executable same-gate authorization contract. Submit only that bounded revision and closure evidence for re-review.