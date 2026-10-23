# Task 3 independent implementation review

## Spec compliance

**Verdict: Issues found (T03-IR-01, T03-IR-02).** The private capability, source receipt, first-bind and operation-specific read architecture substantially matches the adopted requirements, but the new locked source path performs cryptographic hashing and the explicitly required multi-scope/task-union ordering proof is absent from the supplied final tests.

**Code quality: Needs fixes.** No Critical finding. Two Important findings below block this implementation gate. This is a Task 3 gate, not runtime or Phase B completion approval.

Review range is exactly `3e393dbc9485b187b89bfc03abd88821a0aabddb..aa0b5f08a19649091b0bcd60959bd741fb5c7850`. Later root documentation commits are excluded. The 382836-byte bounded package hashes to `ddfd6828dff31f5710115a164e5da7a2d71dddea219657af854f916b5bdd1103`; the retained 6289323-byte raw audit hashes to `79c659ecb215f1f85dd376949434b00861be52ba856809b6eb3547c4162ad698`. Both hashes were independently checked with Get-FileHash.

## Strengths

- `backend/adapters/src/main/java/ama/meeting/JdbcMeetingAccess.java:50` and `:54` route authorization through status-only capture while retaining the same issuer/holder and ACL. The repository change is restricted to the coverage accessor. Actual large supplementary-name authorization and roster preservation are exercised at `backend/integration-tests/src/test/java/ama/storage/TranscriptAuthorityIT.java:15` and `:396`.
- `backend/adapters/src/main/java/ama/storage/JdbcFileRegistry.java:401` and `:671` authenticate the receipt against the expected private gate and exact unique set, consume it once, and join REQUIRED so a caught bind failure still rolls back. `TranscriptAuthorityIT.java:49` tests that failure with persisted reference-epoch observations.
- `backend/adapters/src/main/java/ama/transcript/JdbcTranscriptAuthority.java:131` inserts actual new batch/profile/member/input rows after source locks, rereads their complete values, checks empty producer/output sets and issues the private held object. `TranscriptAuthorityIT.java:133` demonstrates first binding without a dummy transcript or confirmation; `:199` checks graph/reference rollback.
- `backend/adapters/src/main/java/ama/storage/JdbcFileRegistry.java:446` establishes the selector permission floor independently of optional provider resolution. `TranscriptAuthorityIT.java:242` tests missing/legacy/failing providers against real owner/member/outsider identity state, with no downstream locks or storage opens after denial.
- `backend/adapters/src/main/java/ama/transcript/JdbcTranscriptAuthority.java:297` separates historical successful typed output from live RUNNING lease/unfinished-attempt checks. `TranscriptAuthorityIT.java:159` and `:175` demonstrate historical business-version independence and actual renewed/expired database lease behavior. `JdbcFileRegistry.java:510` opens only after successful transaction return; the test at `TranscriptAuthorityIT.java:313` also exercises commit refusal and post-commit interruption.
- `backend/adapters/src/main/java/ama/transcript/BoundedTranscriptMetadata.java:23` and `JdbcTranscriptRows.java:23` measure text before transfer and reject rather than clip. The sidecar profile remains separate from existing INPUT_BATCH/hash shapes. The complete protocol hooks at `backend/core/src/main/java/ama/storage/CompleteReadPolicy.java:6` are abstract, while legacy FileReferencePolicy remains unchanged.

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

## Minor findings

- **T03-IR-M01 — Readability of new security-sensitive code.** `backend/adapters/src/main/java/ama/transcript/JdbcTranscriptAuthority.java:80`, `:242`, `:286` and `BoundedTranscriptMetadata.java:23` compress multiple state checks, SQL expressions and transitions into very long lines. Expand the new methods/conditions with normal formatting and named intermediate values when addressing the blockers; preserve the private single-issuer design. This is maintainability feedback, not a separate correctness failure.
- **T03-IR-M02 — Initial upload regression remains an unexplained test failure.** `artifacts/phase-b/b-remaining/task-03/authority-regressions.log:189` and `:390` retain application startup failure and 29 errors; the later upload-only XML passes 39/39. The source and report correctly retain both and do not claim that the original invocation passed. Keep the handshake cause explicitly unresolved; the later healthy snapshot/retry does not diagnose the earlier failure. No runtime workaround or timeout change is warranted from this evidence alone.

## Evidence and cannot-verify limits

- Independently parsed the archived XML: `authority-final-normalized-xml` is 22 tests / 0 failures / 0 errors / 0 skips; `authority-regressions-xml` is 348 / 0 / 29 / 0; `upload-regression-retry-xml` is 39 / 0 / 0 / 0; `dangling-red-xml` is 1 / 1 / 0 / 0. Final authority and upload-retry logs contain no matches for WARNING/WARN/ERROR. The missing multi-scope proof above is distinct from these real passing results.
- Read the command receipts, source-normalization/delta records and rootproof. The 221-unit plus 128-integration total is composed evidence across qualified source windows. Only the 22 authority tests are independently asserted here as run on the exact final normalized source set. The full regression gate was not rerun on those final bytes, and this review did not run tests.
- The raw audit is retained and hash-verified, not treated as a second source diff to reread. All 19 owned source/test changes were inspected through bounded diff passes. The first registry output and a later evidence-script output were truncated; the affected bounded ranges were retrieved. Unchanged registry Gate/Proof/bind/checkOriginal/transaction context was inspected because diff hunks cut those relied-on methods. Focused outside-diff checks were limited to (a) FileBackendCatalog/ManagedBackendId for the named route/hash boundary risk, and (b) TransactionRunner for caught-failure rollback semantics. No broader repository crawl or Git command was used.
- Rootproof's 416 manifest-entry/committed-blob verification is controller-provided evidence; this reviewer did not independently repeat all 416 checks or inspect private environment values. No Docker, database, network, fixture or historical asset operation was performed. Only this review file was written.
- Actual production Spring wiring, HTTP/token intake, production enqueue/importer, parser bytes/output, saved transcript flow, S3 runtime and complete Phase B behavior remain outside this slice. Synthetic successful RAW/producer fixture rows demonstrate read-policy behavior only. No claim is made that those later workflows are complete or verified.

## Assessment

**Spec compliance: Issues found. Task quality: Needs fixes.** Close T03-IR-01 with a real hash-free locked route check and T03-IR-02 with the mandated multi-scope ordering evidence, then review the bounded correction against this exact baseline. The observed strengths and archived passing tests remain valid within their stated scope.
