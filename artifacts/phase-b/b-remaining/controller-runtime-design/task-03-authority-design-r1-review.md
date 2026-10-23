# Task 3 / 4 authority design R1 — bounded independent re-review

Date: 2026-10-23. Design-only re-review of ADR-01..03 and concrete regressions introduced by their revisions. Task 3 implementation has not been reviewed or approved by this report.

## Verdicts

**SPEC verdict: PASS for this bounded design revision.**

**Design/quality verdict: PASS for this bounded design revision.**

ADR-01, ADR-02 and ADR-03 are CLOSED AT DESIGN LEVEL. There are no remaining finding IDs and no new blocking design findings. The original accepted architecture and prospective evidence requirements remain binding. Root may use this closure to freeze Task 3 ownership and dispatch implementation; this is not implementation acceptance, schema-application approval, runtime qualification, B06 completion or Phase B completion.

## Exact reviewed artifacts and method

Read both artifacts in full and independently verified their SHA256 values:

- `task-03-authority-design-r1.md`: `6fcb1f1d816cd96d389f83e67b0ceaba3298c4657cda3ed8bdcc27f3ab6537e9`.
- `task-03-authority-design-r1-closure.md`: `5dc50be0ffc693ef13f725a668ce1ccf69847c35621fb7356bf82d6be4d830c7`.

Compared these with the original review findings and accepted design. Checked the relevant unchanged MeetingAccess/FileReferencePolicy contracts, JdbcMeetingAccess identity/issuer/ACL paths, JdbcMeetingRepository name/status/coverage consumers, and the existing MeetingConfiguration/FileConfiguration shared-bean injection sites. The original binding controlled-runtime specification, Normative R1 and B04/B06 consumer-contract analysis carry forward. Concurrent Task 2 creator/schema implementation is excluded; repository inspection was limited to identifying established identity/name/status/coverage call sites, not assessing the changing creator implementation.

No tests, DB, Docker, Git, network, private-file or source-edit operations were performed. Only this report was written. Source HEAD remains coordinator-declared, not independently Git-verified.

## Finding closure

| Finding | R1 references | Closure determination |
|---|---|---|
| ADR-01, P2: bound initial identity capture | R1 lines 13–66, especially fixed caller routing at 51–60 | CLOSED. Public lock and tryLockIdentities use schema-bounded enterprise_id/status projections with no display_name transfer, including the initial registry gate. Both captures retain one issuer, actual holder, identity ordering/phase checks, root path and ACL implementation. Package-private repository capture retains actual complete Person names/statuses. Coverage changes to ids(), so projection does not alter identity coverage. |
| ADR-02, P2: legacy download versus mandatory finalizer | R1 lines 68–106 | CLOSED. FileReferencePolicy keeps its original two-method contract. CompleteReadPolicy has explicit abstract acquisition/task-prelock/finalizer methods. Dispatch derives from the registered policy/private proof; new planned reads cannot fall back to legacy authorization. Generic B06 current reads must finish the complete protocol; unchanged legacy providers retain their old path. |
| ADR-03, P2: expected-gate/full-set receipt authentication | R1 lines 108–148 | CLOSED. sources(expectedGate,receipt,exactFileIds) verifies private issuer/source/actual holder, exact gate identity, phase and complete bounded unique set before returning data. B06 separately checks ordered graph/observations. bindAll derives the expected gate/set from genuine proofs and consumes that same receipt; matching detached metadata cannot authenticate it. |

## Regression analysis

**Identity and existing consumers.** The public B03 signature and configured bean identity stay unchanged. Existing repository save reads Person.name and confirm reads Person.status from the retained full capture; neither is forced through a fabricated status-only Person. Existing requirePrepared coverage becomes projection-independent while the owner/member/grant/roster ACL remains shared. The standalone facts reader retains its conservative name measurement and ambient rejection, so this revision does not weaken its existing bounds or alter historical facts shapes. The initial registry and ordinary queue paths now avoid the previously unbounded name transfer. Full-name roster behavior is deliberately retained, with no new product name cap.

**Read compatibility and authority.** The extension is additive. Existing two-method providers need no invented finalizer implementation. B06 cannot register a legacy-only INPUT_BATCH_FILE policy, and caller selectors cannot opt into a legacy bypass. Genuine NotFound alternatives retain their narrow fallback behavior; arbitrary finalization failures abort even when another legacy reference appears valid. Scope deduplication uses policy identity and authentic scope identity, avoiding caller equality as authority. Current generic reads remain current-effective only; history/worker selectors use the explicit planned path.

**Task ordering.** R1's prelockReadTasks acquires the entire sorted task union before any finishRead acquires an attempt, with authentic phase transitions. This resolves the risk of per-scope task/attempt interleaving. The stated global attempt order remains binding. Supporting one complete-provider instance alongside legacy providers is a bounded capability choice appropriate to the current B06 registration; distinct complete providers fail unavailable before source acquisition. It introduces no authority fallback and requires no generic cross-policy coordinator in this slice.

**Source receipt lifecycle.** Source metadata remains data-only and may be inspected before handoff. BINDING/CONSUMED receipts cannot be reused, reset or expanded. The same-holder exact-set check precedes B06's actual new-row insertion, and binding reuses the validated snapshots instead of trying to access a consumed receipt. This retains the first-binding/no-existing-transcript sequence and prevents a detached list from replacing B04 receipt authentication. Failure must abort the caller transaction and leave the local receipt unusable; implementation review must verify that behavior rather than infer it from a thrown exception that a caller might catch.

**Worker versus history.** The additional explicit distinction is consistent with the original scope: current/history reads require the actual successful typed RAW producer relation and immutable original full spec/provenance; they do not require a historical RUNNING lease or today's business version. Worker bytes still require the actual live RUNNING task/unfinished attempt, DB expiry, current fences and owner authority. Both preserve original upload epoch/route checks. No stale-retention or external-send authority is added.

**Intake debt correction.** R1 line 190 and the closure note now agree with the binding specification: clear transcript/final confirmations, set needs_generation=1, preserve the saved transcript pointer and advance meeting/subject exactly once to r+1/e. The preserved earlier R1 is historical only. The reviewed current hash contains the correction; no contradictory clear-debt instruction remains in the reviewed sequence.

## Implementation evidence retained as gates

These are required future proofs, not tests run or findings remaining open against this design:

- Instrument initial registry and queue authorization to reject any display_name selection/materialization; test huge and supplementary-character names, unchanged complete roster names, owner changes, foreign issuer/holder, and skip-locked identity contention before roots.
- Exercise an unchanged legacy provider, reject new requests to it, reject B06 legacy registration, and prove generic B06 finishRead runs once per authentic scope. Test duplicate scopes, genuine NotFound alternatives, arbitrary finalizer failure and no storage open after failure/failed commit.
- Trace deliberately conflicting task/input/file/parent UUID orders: all domain roots before sessions/files, all task UUIDs before any attempts, and sorted attempt acquisition. Test the unsupported multiple-complete-provider case before sources and the supported one-provider union.
- Reject fake/foreign/ended/suspended/wrong-gate/subset/extra/duplicate/BINDING/CONSUMED receipts before effects. Verify exact receipt inspection plus one handoff; same source set with wrong B06 ordinal fails. Verify failure cannot be caught and followed by a partial successful transaction/receipt reuse.
- Retain original first-bind actual-new-row seal, original-source/purpose/route/epoch, full live lease, current/history/member eligibility, full rollback, standalone reader compatibility, bounded acquisition, hash-outside-roots and post-commit private-pin gates.
- Retain the independent review's inherited regression matrix and subsequent actual local/S3 worker/restart qualification. Task 3's narrow B03 capture/coverage edits must start only from root's frozen base after Task 2 creator edits finish.

No new product approval or additional architecture decision is required by this re-review.
