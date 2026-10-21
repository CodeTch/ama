Final fix-round-1 HEAD: `b126db27e592a563392292fef8551015251dbe50`. Exact FIX_BASE: `682a7bfaef45f6ed3fec38df99e85a7b78d450d7`. Scope: FIX_BASE→HEAD,37 paths. Worktree clean; independent scoped re-review/adoption pending. Tracked full report SHA-256: `db35d200702f23d598956f98d2d3e4a3f00ad806c6c98d6458154fc332fdf5e6`. Initial task-B-05-report.md and its sealed artifacts were not overwritten.

# B05 fix round 1 report

Owner: `/root/b05_concurrency_implement`; date: 2026-10-22 Asia/Shanghai. Worktree `/home/tanren/ama/.worktrees/phase-b-tasks`, branch `codex/phase-b-tasks`.

**FIX_BASE: `682a7bfaef45f6ed3fec38df99e85a7b78d450d7`.** This tracked report is committed with the complete fix; the coordinator copy `task-B-05-fix1-report.md` records exact final HEAD after commit. Review only FIX_BASE→final HEAD plus the named binding context. Initial full-range review was `0daecad85d348305c11982b5f0a7e2536a47434d..682a7bf` and found Important I1/I2; those findings, all original source variants, and sealed evidence remain preserved.

Status: I1 and I2 implemented with deterministic real-MySQL RED→GREEN and focused regression; awaiting fresh independent scoped review/adoption. No consumer activation, company calls, SQL modification, production merge/push, child agent or database restart.

## I1: complete model prelock before RR reservation snapshot

Verified the review against actual TransactionRunner/default isolation and reproduced it. A real valid B admission inserted its second unsettled NULL-deadline reservation while retaining B's model lock before commit. The old configure transaction counted A first, then waited for B. After B committed, its stale RR ReadView still counted only the earlier B reservation. RED captured in-flight2, capacity1 and both configuration revisions2; this was a functional assertion failure, not setup failure.

`JdbcModelPermitPool.configure()` now uses two phases in its existing independent transaction: materialize/lock the complete configured model set in sorted order; only then select/validate revisions and count reservations. The existing upsert avoids the missing-key gap-lock/insert deadlock. Configuration does not lock any operation or domain row. Ambient rejection, no constructor writes, explicit local config/enable/revision checks and conservative cutoff rules are unchanged.

The same test schedule works with both implementations: B admission is uncommitted before configure attempts B. Under the fix, configuration has not created a reservation ReadView yet; after B commits, counts see both reservations and reject the shrink. The whole transaction rolls back, retaining both A and B revision1 and B capacity2. No test incorrectly demands B admission progress after the corrected admin transaction already owns B.

Disposition: I1 corrected; exact GREEN trace is `B_reservation_inserted_uncommitted → admin_attempt_B_lock → B_admission_committed → admin_count_A → admin_count_B`, actual in-flight2, A/B revisions1, B capacity2.

## I2: locked proof, safe generation rediscovery and independent observe

Both reported windows reproduced on BASE. A stale success observer read its old execution before waiting for the subject. If the writer committed zero-effect FAILED proof during the wait, the old object still read MARKED and the observer returned STALE_TOKEN. If the writer appended generation2 during the wait, the early current-execution comparison returned STALE_TOKEN before considering the contradiction. Neither result saved the authoritative success or quarantined the retryable operation.

The fix retains the discovery phase but treats discovered objects only as lock candidates. After subject/root and the complete sorted discovered task set, it locks operation, then relevant execution rows in generation order. Task rows and the token execution/proof are current locked reads. Contradiction requires current FAILED state and complete basis/evidence/verified-at proof, then the existing registered local observation verifier; it records the safe external result and MANUAL_REQUIRED without executing the stale domain callback or restoring any send/admission authority.

A changed current generation throws an internal rediscovery signal. The entire owned transaction rolls back, releasing subject/task/operation locks and RR ReadView before discovering again. It never discovers a new task and locks it while retaining an old operation lock. The generation-change GREEN trace explicitly records rollback, fresh subject/root, both task locks, operation, both execution locks and commit of quarantine.

Per the coordinator's explicit architecture ruling, `observe` is now an **independent short transaction entry point**. It rejects ambient transactions before any authority revocation, database effects or callback. Its own transaction still commits registered prelocked domain facts/effects and outbox atomically. Consumers are not yet enabled, so the unreleased ambient composition contract was not retained at the expense of correctness. `ExternalOperationPort` JavaDoc and new consumer addendum state the boundary.

There are four discovery attempts. If all race with a newer generation, public `ObservationRetryRequiredException` is thrown, never STALE_TOKEN or a false acknowledgment. The caller must retain and resubmit the **same already-authoritative observation**, outside a transaction; this never authorizes a remote create/send, a new external execution, ordinary task resend or fabricated zero-effect proof. Domain/reconciliation providers must handle that explicit retry requirement before generic handler exception logging can discard their local fact. No company observation durability protocol is invented here.

The bounded test forces four actual safe-retry generations with independent committed transactions, receives that explicit exception, then stops generation churn and resubmits the same original fact; it quarantines successfully. All generated marker/proof transitions are local test fixtures; no external network call is made. Stale callbacks remain unexecuted, old renewal fails, and the retry-generation case proves beginExternal cannot grant a new-send token after quarantine.

Disposition: I2 corrected for zero-effect-proof and generation-advance windows, with explicit ambient and bounded-retry boundary coverage.

## Files and consumer contract

Changed initial production source paths only:

- `backend/adapters/src/main/java/ama/task/JdbcModelPermitPool.java`
- `backend/adapters/src/main/java/ama/task/JdbcExternalOperations.java`
- `backend/core/src/main/java/ama/task/ExternalOperationPort.java`

New active source/test paths:

- `backend/core/src/main/java/ama/task/ObservationRetryRequiredException.java`
- `backend/integration-tests/src/test/java/ama/task/ReviewConcurrencyIT.java`

New consumer addendum: `docs/acceptance/b/task-B-05-fix1-integration.md`. It supersedes only the observe transaction boundary in the initial sealed handbook. The initial handbook/report were not rewritten. New fix report is this file; all new evidence is exclusively `artifacts/phase-b/b05-fix-round-1/`.

## Exact test execution and source variants

| Invocation | Selected DB cases / failures / errors / skips | Meaning |
|---|---|---|
|`red.log`, `red.xml`|4 / 4 / 0 / 0|I1 and both I2 deterministic interleavings plus ambient rejection against unchanged BASE adapters|
|`bounded-red.log`, `bounded-red.xml`|1 / 1 / 0 / 0|new bounded retry case against same BASE adapters; expected explicit retry error, but old method returned normally|
|`green.log`, `green.xml`|5 / 0 / 0 / 0|all five new cases against corrected source|
|`focused-regression.log`, three selected XMLs|22 / 0 / 0 / 0|ReviewConcurrencyIT5 + ExternalExecutionIT10 + ModelPermitIT7 against final runtime source|

Each invocation also executed the normal33 reactor Java unit tests, all passing with no skips; these incidental repeats are counted separately in `execution-manifest.json`. Final unit XMLs are under `focused-unit-xml`. No unchanged worker-process/recovery IT, DB restart, broad full-B suite, Python or Web suite was ritualistically rerun. Earlier sealed 47-case delivery evidence is historical context, not claimed to be52 fresh cases in this fix round.

`red-source/` and `red-source-sha256.json` retain exact BASE adapters and four-case RED test. `bounded-red-test-source.java` retains the full five-case test used for bounded RED and final GREEN; its SHA equals the final active test source. Every probe delegates to actual MySQL. Connections explicitly set and verify REPEATABLE READ; latches gate actual JDBC query/commit boundaries rather than sleeping or mocking query results. No failed launch or setup error is counted as RED.

Reproduction uses pinned `/home/tanren/ama/.worktrees/phase-a/.tools/env.sh`, then the original positive fixture's derived current-port env. Canonical new-test runs use `python3 tools/checks/run.py it ReviewConcurrencyIT`; bounded RED selected its exact method. Focused regression uses Maven integration module + dependencies with `-Dit.test=ReviewConcurrencyIT,ExternalExecutionIT,ModelPermitIT -Dfailsafe.failIfNoSpecifiedTests=false verify`. Exact selectors and XML identities are preserved.

## Preservation, database scope and manifests

All201 original delivered paths remain in exact FIX_BASE Git objects and were hash-verified. In the working tree198 are byte-identical; only the3 named initial source paths changed. All144 sealed initial evidence hash entries, initial reports/manifests, raw sandbox candidates, source archive and exact retained stash `bc12e825246c60c12752129c4d512ef9c545758c` remain untouched. No reset/repair/remove/pop/drop was issued.

Only the existing own positive fixture was used: `ama-test-b01-3ee46f689f9a`, exact ID `59f3de25efa7156e89329880ccfbd4d51bd2f52d735ead9fca64745c28eb3dcb`, current loopback45951. Read-only audit confirms AutoRemove=false, retained mount/args, MySQL8.4.6, REPEATABLE-READ, UTC, nativeAIO0 and unchanged8 successful migration history entries. No additional restart, helper change, global AIO setting, failed46509 access or other fixture mutation occurred. The other three owned fixtures and original46037 remain preserved/untouched. Intentional RED data—including the unique test model with demonstrated capacity-floor violation—is retained; successful tests use distinct test identities rather than hiding failures through cleanup.

V010 remains35,136 bytes, SHA `03e89cc8bd58d5a6aa0d4ab031b8fc05a4f7d2450623df147cd84d05b68977db`, checksum `-1187397378`. No SQL file or migration register changed. Root remains registry owner. This round qualifies local functional SQL behavior only, with the already-authorized nativeAIO-off fixture mode; no real company capacity/cutoff, native I/O, HA/VIP, load, whole-B or production qualification.

Source/contract/consumer allowlist: `artifacts/phase-b/b05-fix-round-1/source-allowlist.json`, SHA **562a3472f04326012cd5be6cd2390aa24675fbe347a50d20d50ba3baf7c06aba** (6 final paths). Evidence allowlist: `artifacts/phase-b/b05-fix-round-1/evidence-allowlist.json`, SHA **a06897e0636b55c74fa8a9087d435c80349a61dc288a41cff5846500a1b46c26**. The latter hashes all evidence files except itself; this report is outside those manifests to avoid self-reference. The coordinator copy records final HEAD and report SHA after commit. Credential-bearing env files are excluded; literal local fixture-password scan found no values in the source/evidence allowlists.

Fresh scoped independent review/adoption remains the next gate. No current test failure remains, but this owner does not self-approve the fixes or enable downstream consumers. The observation entry-point change is intentional and binding for future domain adapters; retriable observation errors require retaining/re-submitting the same fact, never repeating an external effect.
