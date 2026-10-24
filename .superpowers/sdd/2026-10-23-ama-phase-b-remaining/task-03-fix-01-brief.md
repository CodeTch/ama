# Task3 fix round1/5 — source boundary and multi-scope proof

Read original task-03-brief.md and current task-03-report.md, then complete task-03-review.md (rawSHA60f3e807d0d6e79fbdeafce015b063ce0acefbf910c0ec252dab25484952149c). Exact previous reviewed FIX_BASE aa0b5f08a19649091b0bcd60959bd741fb5c7850. Current HEAD5560381927d51001bd484642269b9432481e8c70 contains only root Task5 design-adoption docs after author commit. Do not stage root docs; same current branch/fixture/custody. Sole source author, no helpers/reviewers.

Root ruling for plan-mandated T03-IR-01: higher spec forbids hashing under held roots; original design incorrectly called backends.require metadata-only. Preserve constructor-validated immutable managed backend identity and actual database type/status/revision/historical-route checks; perform derivation before roots, never waive it. Root authorizes minimal FileBackendCatalog ownership expansion plus necessary focused tests to expose/use a real metadata-only locked validation path, preserving old B04 behavior. No public caller-attested boolean/callback grant/duplicateissuer/late I/O or broad backend refactor. Source chain was independently checked by root. This is a correction to technical implementation choice, not a product approval question.

Required review findings verbatim:

## Important findings

### T03-IR-01 — SHA-256 executes while source roots are locked (constraint conflict; plan-mandated helper choice)

**Location:** `backend/adapters/src/main/java/ama/storage/JdbcFileRegistry.java:649`.

The new `prelockSources` path calls `backends.require(...)` inside the file-row mapper after the genuine identity/meeting gate, all original-session locks, and the file measurement `FOR UPDATE` have been acquired. The unchanged helper is not metadata-only:

1. `backend/adapters/src/main/java/ama/storage/FileBackendCatalog.java:135` calls `ManagedBackendId.require(id, b.definition())`.
2. `backend/core/src/main/java/ama/storage/ManagedBackendId.java:119` evaluates `definition.id()`.
3. `ManagedBackendId.java:17` calls `derive(fields())`.
4. `ManagedBackendId.java:109` allocates/encodes the definition bytes and executes `MessageDigest.getInstance("SHA-256").digest(...)`.

Consequently every actual source acquisition hashes under the locked database roots, including draft, first-bind and complete read paths. This is a demonstrated call-chain violation of the binding global rule, not a speculative timing issue. The original adopted design at `artifacts/phase-b/b-remaining/controller-runtime-design/task-03-authority-design.md:70` explicitly chose this helper and called it metadata-only; that factual assumption is incorrect and cannot override the no-hash-under-roots constraint. The implementation report's no-hashing-under-gate claim is therefore too broad.

**Required fix:** preserve genuine managed identity checking, but perform derivation/validation before the locked phase (immutable catalog construction already checks the definition), and expose/use a narrowly scoped metadata-only historical-route validation path under locks. Continue checking actual backend type/status/revision and rechecking the exact route before post-commit open; do not remove those checks or add an arbitrary callback. Root should approve any needed FileBackendCatalog ownership expansion. Add a focused regression that detects hashing on the locked path and verifies that redefined/disabled routes still fail closed. No new database experiment was run by this reviewer.

### T03-IR-02 — Required whole-task-union-before-any-attempt proof is missing

**Location:** `backend/integration-tests/src/test/java/ama/storage/TranscriptAuthorityIT.java:119`, `:313`, `:322`.

The complete-provider orchestration test explicitly asserts `scopes.size() == 1`. The actual lock-order test creates one producer task, asserts only `List.of(task)`, and asserts that no task-attempt lock occurs. The worker test also uses one task. Thus none of the final 22 tests can detect the essential cross-scope regression where scope A's attempt is acquired before scope B's durable task, nor prove task UUID ordering different from scope/input order.

This is a missing explicit acceptance test, not a claim that the inspected union implementation is presently wrong. `JdbcFileRegistry.java:568` and `JdbcTranscriptAuthority.java:286`/`:297` appear structured to acquire the task union first, but the adopted R1 explicitly requires multiple authentic scopes, task-union-before-any-attempt traces, shared-task deduplication/conflict rejection, and task UUID order different from other ordering (`task-03-authority-design-r1.md:104`, `:106`, `:168`, `:223`). The supplied archived evidence does not establish those requirements. Passing inherited queue tests cannot establish the new complete-read protocol's multi-scope behavior.

**Required fix:** add focused tests for at least two scopes/tasks with reverse scope versus task UUID order and at least one unfinished attempt. Assert that all distinct durable-task locks precede the first attempt, finalization occurs once per distinct scope, and shared IDs are deduplicated while conflicting expectations reject before opening storage. Exercise the actual private production union where applicable through existing test-only reflection/real rows; a focused configured-provider orchestration test may supplement that but should not be represented as production authority proof. Preserve the exact final source/run binding. No suite rerun is requested merely to reproduce the existing singleton evidence.


# Repair contract

Implement only these blockers and regression consequences. Cover actual hashing boundary plus disabled/redefined route denial and production multi-scope union ordering; use meaningful TDD and real bounded SQL/trace/holder evidence. Existing test-only reflection may exercise genuine issuer-minted scopes without broadening public single-request product support; configured-provider counters supplement but cannot masquerade as production authority proof. Include reverse scope/task UUID order, >=2 distinct tasks, unfinished attempt, shared task dedup and conflicting expectation/no-open failure. Explain reachability constraints if genuine scopes cannot be produced, do not synthesize a public capability workaround.

Covering tests: TranscriptAuthorityIT/new focused same-area tests; ManagedBackendIdentityTest and focused FileBackendCatalogIT selectors only after checking helper custody/noreset. Relevant affected existing local route/upload tests if actual behavioral changes warrant; no repeat full reactor or all old restart suites merely to regenerate evidence. Final exact modified-source seal and fresh covering XML/commands/output required. Preserve source-window limits and original unresolved uploadhandshake failure; no timeout workaround. Optional M01 normalformat touched security-sensitive methods if useful, avoid unrelated churn; M01/M02 initially deferred, root finalreview sees them.

Append full fix report to SAME W/task-03-report.md with exact FIX_BASE/newcommit/paths/ruling/tests commands+output/reviewfindingclosure and concerns. Preserve all prior committed task03 artifacts byte-for-byte. New public artifacts only artifacts/phase-b/b-remaining/task-03-fix-01/ (new rawreport/manifest/command/source/XML/custody, nested * -text -whitespace); original manifest/report stay exact. Use owned fixture .tools/phase-b-remaining/env.sh only after identityguard, noDDL/oldPKI/storage/reset; exactnew synthetic rows tracked/terminalized same approved deny-only teardown. Selfreview and explicit ownedpaths commit; return shortstatus/commit/tests/concerns. Root generates bounded fix diff from previous reviewed FIX_BASE, scoped rereview only two findings/newbreakage. No Task4 source/overallBqualification.
