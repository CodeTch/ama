# B02 historical V007 migration target — independent SPEC and code-quality review

Date: 2026-10-22. Verdict: **SPEC PASS; code-quality APPROVE for this narrow test-maintenance range.** Critical: **0**. Important: **0**. Minor: **0**. No revision request or integration blocker found. Full B02 producer/ACL/API closure remains **OPEN**.

## Scope and independent method

Reviewed the complete owned range `f4e2c6fc0baf6c95c01a4b51cfb25ad8200e0ab4..3c425110f6f4c956427cd90b7327e5e7acf0d983` in `/home/tanren/ama/.worktrees/phase-b-identity-history`, after fully reading the coordinator brief and report and then the complete 8,613-byte review package. Independently compared the package diff against a fresh full-context Git diff: identical. Git confirms one commit, exactly two changed files, 45 insertions/3 deletions, expected HEAD, clean tracked tree and successful `git diff --check`.

Applied requesting-code-review and verification-before-completion principles as the fresh reviewer. All commands used Windows-local cwd `C:\Users\LING` and explicit WSL `--cd`/`--exec`. Review execution consisted only of file reads, read-only Git, in-memory parsing/hashing/comparisons, and this permitted report write. No tests, DB queries, container commands, network access, owner verifier, source/index/HEAD mutation, or further agents were used.

## SPEC assessment

- `backend/integration-tests/src/test/java/ama/identity/IdentityMigrationIT.java:13` and `:16`: both historical migrations use the private helper, including the original rerun-zero assertion. The original test body was independently extracted from BASE and compared to HEAD after replacing only the two old migration expressions. It is otherwise byte-for-byte identical, including all 12 assertion calls and intervening SQL/setup. Exact history, case/trailing-space NO PAD IDs, department IDs/FK, verification, generation, role, session expiry, long name and future-domain table absence checks remain intact.
- `IdentityMigrationIT.java:35` through `:39`: private test-only helper pins Flyway target007, classpath migration location, UTF8/UTC initialization and cleanDisabled=true, returning migrationsExecuted. It follows the existing explicit-target test pattern while retaining the production initialization SQL. The pinned target remains effective with V008 available for validation on the classpath.
- The complete Git path allowlist contains only that test and `docs/acceptance/b/identity-historical-migration-target.md`. Production SchemaMigrator, shared MysqlTestDatabase/helper, global runner, POM, SQL, API and security paths are unchanged. Production SchemaMigrator continues to migrate latest. Six formal SQL files were independently read from BASE and the worktree and compared by bytes and SHA256 against the frozen manifest and both fixture inventories: all identical. V007 remains `859970b83fc5623b18340c2de793ea35429cc3c0da5cbd1365b4763a5a64780d`; V008 remains `fa44424aa1ff32e049929d45865fe7133c2548fc9ea451f3350cd8984c118ce3`.
- Acceptance record `docs/acceptance/b/identity-historical-migration-target.md:3`, `:17`, `:18`, `:24`, `:26`, `:30`, `:32`, `:34` accurately describes the narrow fix, real assertion RED, independent GREEN, immutable SQL, startup exception, evidence loss, and remaining OPEN scope.

## Independent evidence checks

Evidence root: `/home/tanren/ama/.worktrees/phase-b-identity-history/artifacts/phase-b/b02-historical-target/`.

Independently recomputed all **35** hashes in `allowlist-sha256.json`: all match. Directory membership equals the 35 allowlisted files plus the manifest; no unlisted evidence file. BASE/HEAD source snapshots match actual Git/worktree bytes. `git-provenance.json` agrees with the reviewed range. XML counts and testcase identities were parsed independently, rather than accepting `junit-counts.json` totals.

|Archived run|IdentityMigrationIT XML|Unit XML|Initial / final tables|Successful history|
|---|---|---|---|---|
|RED, own fixture 92d85c5fdcb3, 127.0.0.1:44891|1 test, 1 assertion failure, 0 errors/skips|28 tests, 0 failures/errors/skips|0 / 21|001,002,005,006,007,008|
|GREEN, own fixture afb009479e40, 127.0.0.1:45577|1 test, 0 failures/errors/skips|28 tests, 0 failures/errors/skips|0 / 13|001,002,005,006,007|

The sole RED failure is `org.opentest4j.AssertionFailedError` at original IdentityMigrationIT.java:14: expected history001/002/005/006/007, actual additionally008. It is the requested behavior failure, not a missing dependency, compilation error or failed migration. RED log independently reports six migrations applied and BUILD FAILURE. GREEN log reports five migrations applied to007, all six classpath migrations validated, second migration invocation up to date with no migration necessary, and BUILD SUCCESS. The GREEN XML identifies the actual unchanged testcase with no failure/skip; its preserved rerun assertion checks returned zero.

Each run has five unit suites: DeploymentConfig9, IdentityContracts3, IdentitySecurity6, StorageRouter8, ContractClient2. Parsed testcase counts equal suite totals and no failure/error/skip element exists in either run's unit XML. These totals match the archived summary.

Parsed initial/post metadata independently confirms distinct fixture identities, ownership label, exact loopback ports, zero initial tables and nativeAIO0 throughout. RED/GREEN histories have only success=1 rows and expected scripts/checksums:001=1201435595,002=1757300424,005=-1999355348,006=-1744360556,007=157746768; RED additionally008=-1747387036. Archived DDL contains exactly one CREATE TABLE for every inventoried table, 21 RED/13 GREEN. GREEN inventory contains none of meeting, roster_revision or meeting_access_grant. Preserved RED history and full DDL remain available for retrospective review; no failed Flyway history is being treated as acceptable.

## Startup exception and evidence hygiene

Read the complete archived wrapper and unchanged shared helper. Static inspection confirms the wrapper imports the reviewed helper, checks its pinned image and exact 14-element run tuple, validates owned generated name/label, loopback binding, task-local generated credential path and normal mysqld options, and appends exactly one `--innodb-use-native-aio=OFF` option. Non-run helper commands pass through unchanged; each invocation calls start once. The two archived redacted argument arrays exactly match that expected structure and the two qualification names. Actual archived metadata reports nativeAIO0 for both initial and final states. No shared helper/source change is present.

The first startup's unavailable Docker log is explicitly disclosed in startup-observations.json, the report and acceptance record; it was not reconstructed or counted as RED. The second actual state/log shows exited1, OOMKilled=false, io_setup EAGAIN after five attempts and AIO initialization abort. Host snapshot records aio-nr61455/max65536. The coordinator explicitly approved this process-local profile for the two new qualification fixtures. This is adequate for the scoped SQL structure/identity/FK regression evidence and makes no production I/O or capacity claim. It does not create a reason to require broad reruns for this unchanged production range.

Allowlist names exclude env/credential files; startup credential arguments are redacted. A static scan of all 35 allowlisted files found no sensitive XML property names, literal fixture-password assignments, password URL values, private-key markers or full Config.Env content markers. Source references to environment variable names are explanatory code, not archived credential values.

## Code-quality assessment and limits

The helper is small, private, locally documented, and confined to the historical test. Both migration calls share one fixed configuration, preventing the initial run and rerun from drifting. Returning migrationsExecuted preserves the original observable contract. No unrelated abstraction, production target switch or weakened assertion was introduced. No Critical, Important or Minor finding is warranted.

This review freshly verified archived evidence consistency and source requirements; it did **not** rerun the archived tests or inspect live fixture state. Hash consistency is not independent runtime witnessing or proof of chronology. Current continued fixture availability, historical absence of later resets, and absence of operations on unrelated fixtures are not independently re-observed here; the retained successful history/DDL and candid provenance support the reported qualification within the authorized retrospective review boundary. The first startup's lost original log remains unavailable. The approved native-AIO-off qualification covers SQL schema behavior only. Full B02 ACL/API/producer work, broader production qualification and excluded all/web/OpenAPI checks are outside this approval and remain OPEN.
