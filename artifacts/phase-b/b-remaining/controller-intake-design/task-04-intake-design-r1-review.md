# Task 4 intake/API R1 independent bounded design re-review

Date: 2026-10-23. Design review only; no implementation adoption, runtime activation, schema approval, executed-test result or Phase B completion is asserted.

- SPEC verdict: **PASS within the bounded R1 scope**.
- Design-quality verdict: **PASS within the bounded R1 scope**.
- T4DR-01: **ADDRESSED**.
- T4DR-02: **ADDRESSED**.
- New findings: **0 total — P0: 0, P1: 0, P2: 0, P3: 0**.
- Remaining original findings in this scope: **0**.

## Exact inputs and review boundary

| Artifact | Independently verified raw-file SHA256 |
|---|---|
| task-04-intake-design-review.md | 37b9a2c422a2af686f12511e39414260dc1741a7a5f910f42a70dac72df52279 |
| task-04-intake-design-r1.md | fcbde3fa7885d0fe3553646c2fbec28e9ad6da78aba4678f8e29461c3f35666b |
| task-04-intake-design-r1-closure.md | 9adef8b54a8908948352db9c43c3358e6cff996a2ae16853f89d542b37716f5c |

Read the original full review, replacement R1 design, closure and supplied bounded diff. Assessment is restricted to T4DR-01/02, their introduced regressions, and the manifest profile kind/ref precision note. The original review's accepted areas are inherited rather than reopened. Relevant controlled-runtime Normative R1 and adopted Task 3 private-authority/complete-policy constraints remain binding. Concurrent idempotency and state-specific endpoint behavior warranted focused independent reasoning and frozen-source comparison.

All source checks used read-only `git show` at **3e393dbc9485b187b89bfc03abd88821a0aabddb**, never Task 3's changing implementation. Source paths and line numbers below refer to that commit. No tests, DB, Docker, private environment, source/index/HEAD mutation or subagents were used. The only artifact written by this re-review is this report. The invocation-local Git safe.directory override did not write configuration.

## T4DR-01 — ADDRESSED

R1 line 69 makes accepted-result lookup mandatory before current CAS/exhaustion/source/archive/readiness. Section 12 (lines 206–271) gives that rule an implementable transaction and construction boundary rather than leaving it as optional optimization.

1. **Independent replay construction and prerequisites.** Lines 219 and 223–229 retain safe protocol parsing, normalized semantics and bounded reads while excluding newly lowered operational limits, current expected-revision equality, source eligibility, archive selection, publication/new-write checks and intake readiness from historical acceptance. The explicit unavailable new-intake branch preserves controller/replay construction when current archives or new assembly are unavailable. It does not publish partially assembled authority, invent a policy, add a second issuer or use a public/lazy attachment mechanism. Common DB/B03/result-reader failures still deny or return unavailable. The section 6 available-component wiring is read subject to this explicit section 12 branch requirement.

2. **Fresh authorization and committed exact restoration.** Existing idempotency locking precedes normal B03 authorization; even a MISS authorizes before ending its transaction. Exact actor/resource/operation/key/request matching remains mandatory. Mismatch is conflict, incomplete/corrupt rows are unavailable, and only real persisted task/input/batch/full fixed-spec associations yield the original accepted revision and fingerprint. Lookup commit failure suppresses ACK. The new standalone read supplies no source/holder authority and cannot issue a new intake.

3. **Miss/concurrent-winner/preparation failure.** Lines 233–264 require the initial MISS transaction and any preparation transaction to end before one fresh recovery lookup. A committed exact winner is restored after renewed authorization; rollback/no row returns the original safely mapped prerequisite error; bounded lock timeout or unavailable/corrupt result returns safe503; mismatched body conflicts; revocation denies. A winner that commits only after the recovery MISS is explicitly left to a later same-key retry. The catch excludes malformed requests, prior denial, cancellation, corruption and arbitrary programming faults. Successful preparation still reaches final serialized claim, whose replay branch precedes new-intake checks. No cached/speculative202, new key, duplicate bind/enqueue, unbounded retry loop or holder reuse is authorized.

4. **Compatibility, CAS and maximum.** Lines 165–169 remain unchanged: tokens normalize into the existing semantic request before digest, preserve old digest version/order/representation, keep legacy/token/equal-dual equivalence and reject unequal forms. Supported max stays parse-valid; new increments at max fail after replay, while accepted max-minus-one commands restore their resulting max. Fresh actor checks remain required after every transaction boundary. Frozen `JdbcMeetingRepository.java:66–71,96–101` supports authorized replay-before-writable ordering; `MeetingDigests.java:13–17` confirms the old save semantic field order/version. B03's subject-before-meeting path and owner EDIT semantics remain consistent with frozen `JdbcMeetingAccess.java:71–83,92–101`; no subject/meeting inversion is introduced.

The added future race/HTTP/construction tests in lines 268–271 cover the formerly missing paths, including zero preflight/bind/enqueue calls for exact accepted replay and suppression of uncommitted or unreadable acceptance. They are requirements for future evidence, not executed results.

## T4DR-02 — ADDRESSED

R1 line 154 and section 13 (lines 273–304) now project the real operation-specific checks. `allowedActions` is reserved for nonterminal actions; terminal acknowledgment/cleanup is explicitly separated into `idempotentRetries`.

| State | R1 contract and frozen-source confirmation |
|---|---|
| OPEN | Parts and first completion require historical WRITABLE. Abort uses the historical recovery route, so valid READ_ONLY permits cleanup when normal mutation checks pass. `JdbcFileUploads.java:383–386` and the part path enforce mutable checks; `:259–267` selects new-write routing for OPEN completion; `:354–367` separates OPEN abort state change from cleanup. |
| FINALIZING | COMPLETE only, with no UPLOAD_PARTS or ABORT. A valid historical READ_ONLY route supports recovery, independently of default write readiness. `JdbcFileUploads.java:259–279` uses require(...,false) for FINALIZING; `:356–357` rejects FINALIZING abort. Publication/epoch/purpose/current owner checks still apply. |
| COMPLETED | No state-changing action. COMPLETE acknowledgment still requires fresh owner/purpose/epoch and exact part/hash manifest; it returns the old file before writable/backend checks. ABORT is a no-op acknowledgment before writable checks and skips storage entirely. `JdbcFileUploads.java:252–282,350–367` confirms that publication or disabled historical routing alone does not suppress these terminal retries. |
| ABORTED | No state-changing action; ABORT cleanup retry only. The terminal detection precedes writable/publication checks, but cleanup still calls the historical recovery route. Disabled/missing/mismatched routing therefore suppresses that retry. `JdbcFileUploads.java:350–367` confirms the distinction from COMPLETED acknowledgment. |
| FAILED | Empty actions/retries; no invented completion, repair or abort transition. The existing complete/abort state checks reject it. |

`FileBackendCatalog.java:128–145` confirms that READ_ONLY permits recovery while DISABLED, missing, managed-identity mismatch or DB definition/status mismatch is unavailable. Section 13 does not collapse these into a generic route-available or current-default flag. Its common checks are expressly qualified by the later terminal early-return rules, which preserve current owner/identity/lifecycle/governance/purpose/epoch checks without imposing new-write publication/backend predicates on COMPLETED acknowledgment.

Line 289 also correctly records the existing abort DB-then-storage boundary: OPEN may already be persisted ABORTED when cleanup fails. Discovery makes no promise of storage atomicity and only advertises successful cleanup when its historical route is valid. Terminal receipt visibility remains separate from byte eligibility and first-binding authority. The additional endpoint/discovery matrix tests cover these distinctions without widening B04 mutation semantics.

## Manifest precision and introduced-regression assessment

R1 lines 73 and 308 now require the ordered profiles entries `{kind:"CANONICAL_CODEC",ref:codecRef}` and `{kind:"CONFIRMATION_HASH",ref:confirmationRef}`, each ref containing artifactType/version/sha256. Frozen `ConfigSchema.java:74–76` requires exactly that kind/ref shape, ordering and matching artifact type. This closes the original precision note without creating a new protocol or third finding.

No new actionable design regression was identified in the bounded edits. The private Task 3 issuer/source/actual-holder checks, complete registered policy, no second gate in adopted enqueue, immutable producer identity and full-spec comparison, no archive/parse/hash under roots, complete lock ordering and full rollback remain inherited requirements. Historical replay's data-only result reader does not authorize intake or byte access. Both discovery arrays remain observations requiring endpoint rechecks; neither becomes a capability.

## Remaining gates

Root owns exact-hash adoption and later implementation dispatch. Actual post-Task-3 private API alignment, assembly startup/failure behavior, SQL/holder/lock instrumentation, rollback and real HTTP/MySQL race tests, endpoint/discovery agreement, manifest vectors and browser behavior remain future implementation evidence. Worker/runtime, storage qualification, Task 5/6/12 completion and schema activation are outside this re-review. This PASS closes only the two design findings and their bounded regression review.
