# B02 identity prerequisite — implementation delivery for independent review

Current status: **PREREQUISITE IMPLEMENTED AND SELF-VERIFIED; INDEPENDENT REVIEW PENDING**. Full B02 remains **OPEN** for B03/V008 owner/effective confirmed READ grants, roster ACL tests and final authorization review. Company integration remains unavailable; this report does not claim real SSO/directory qualification or production approval.

## Commits, baseline and owned scope

- Worktree `/home/tanren/ama/.worktrees/phase-b-identity`, branch `codex/phase-b-identity`.
- Scoped implementation commit: **`39b447d43a91cc0692a5d43ee109fcc82d37d6db`**, `feat: add enterprise identity prerequisite and shared session security` (29 files; 1422 insertions, 2 deletions).
- Original BASE: `344597250accf16f8a1e56713e9aec6045ba28e5`. Frozen V002 checkpoint `e9990c8ee1584a7d8e7d1a259f71908624eb6b25` retained.
- Mechanical reviewed-baseline merges: `01a3d3c7f9332f9f93a7029d5317a3a310efc003` includes root45ce136; `a4ed859bb7d678d7c821646ab75ee959163bb767` includes reviewed rootdf420cf/V006/B00-1.2. These upstream changes are independently reviewed dependency content, not authored B02 changes. Root can review BASE3445972..39b447d with these baseline differences identified, and integrate the scoped commit separately.
- Tracked/untracked Git worktree was clean after commit. Original pre-B00 Java/test draft copies remain ignored in `.tools/b02-pre-b00/`; pause snapshot `task-B-02-b00-input.md` and old logs/fixture are preserved.

Changed paths in scoped commit (relative to the assigned worktree):

1. `backend/contracts/src/main/java/ama/contract/IdentityContracts.java`
2. `backend/core/src/main/java/ama/access/MeetingAccess.java`
3. `backend/core/src/main/java/ama/identity/IdentityPort.java`
4. `backend/core/src/main/java/ama/identity/DirectoryPort.java`
5. `backend/core/src/main/java/ama/identity/SessionPort.java`
6. `backend/core/src/main/java/ama/identity/SessionIssuer.java`
7. `backend/core/src/main/java/ama/identity/IdentityLifecyclePort.java`
8. `backend/adapters/src/main/java/ama/identity/CompanyIdentityAdapter.java`
9. `backend/adapters/src/main/java/ama/identity/CompanyDirectoryAdapter.java`
10. `backend/adapters/src/main/java/ama/identity/JdbcIdentitySessions.java`
11. `backend/adapters/src/main/java/ama/identity/JdbcDirectoryProjection.java`
12. `backend/adapters/src/main/resources/db/migration/V007__identity_sessions.sql`
13. `backend/api/src/main/java/ama/api/BusinessErrors.java`
14. `backend/api/src/main/java/ama/api/IdentityConfiguration.java`
15. `backend/api/src/main/java/ama/api/IdentityController.java`
16. `backend/api/src/main/java/ama/api/IdentityErrorHandler.java`
17. `backend/api/src/main/java/ama/api/SecurityConfiguration.java`
18. `backend/api/src/main/java/ama/api/SessionCookies.java`
19. `backend/api/src/main/java/ama/api/SessionSecurityFilter.java`
20. `backend/api/src/test/java/ama/api/IdentityContractsTest.java`
21. `backend/api/src/test/java/ama/api/IdentitySecurityTest.java`
22. `backend/integration-tests/src/test/java/ama/identity/IdentityAccessIT.java`
23. `backend/integration-tests/src/test/java/ama/identity/IdentityMigrationIT.java`
24. `backend/integration-tests/src/test/java/ama/identity/IdentityMigrationRefusalIT.java`
25. `backend/integration-tests/src/test/java/ama/support/ExpectedIdentityMigrationFailure.java`
26. `contracts/openapi/api.yaml`
27. `contracts/openapi/identity.yaml`
28. `web/src/shared/api/generated.ts` (generator output, not manually authored DTOs)
29. `docs/acceptance/b/identity-prerequisite.md`

No pom or existing RuntimeConfiguration/health code change was needed. V001/V002/V005/V006 bytes remain unchanged. No central registry ownership edit, other domain table, worker framework, main merge or push occurred.

## Exact delivered behavior and ports

Every opaque session lookup performs a single fresh shared-MySQL query, checking hash, issued time, expiry, revoked_at, ACTIVE identity and equal generation. Roles come only from canonical role_assignment, never browser headers/query claims or old platform_admin. Stable enterprise IDs preserve case and trailing spaces; Actor defensively copies roles. Session timestamp values truncate to UTC microseconds and diagnostic strings redact token/CSRF secrets.

Single logout locks identity before session update and audit. All logout atomically increments generation; lifecycle disable and re-enable each advance generation on status change, so old sessions cannot revive. B01 TransactionRunner/JdbcAuditAdapter use the same bound connection. Failed audit rolls back session/generation/status changes. Two concurrent all-logouts serialize without losing increments. Lifecycle verification is attributed to the controlled system actor SYSTEM_IDENTITY_SYNC, not falsely to the affected employee. No public lifecycle/admin route exists.

Production CompanyIdentityAdapter/SessionIssuer and CompanyDirectoryAdapter always fail unavailable. DB resolution is live, but there is no production issuance, callback, local password, test-login, test header or profile/config bypass. No actual company provider fields/protocol were invented. The JDBC directory projection is real canonical DB mapping/paging but is activated only through a test-source @TestConfiguration requiring the test profile; it is not proof of live company directory synchronization. Production test-mode configuration fails startup. Same-name people remain distinct by exact stable ID and department; LIKE metacharacters are literal and paging is bounded.

Public API: GET `/api/v1/session`, GET `/api/v1/directory/people`, POST `/api/v1/session/logout`, POST `/api/v1/session/logout-all`. Production directory is safe503 until qualification. Responses use safe code/message/traceId errors and no-store for authenticated state. Cookie helper/clearing use __Host-AMA_SESSION, Secure, HttpOnly, Path=/, SameSite=Lax, explicit expiry; GET session never returns raw token. Writes require exactly one configured allowed Origin plus exactly one independent session CSRF header, constant-time token comparison; absent, wrong, duplicate or ambiguous values fail403. Invalid opaque/path/userinfo/query/fragment Origin configuration fails startup. Empty allowed origin denies writes. API and health listener/source policies stay independent; existing health/liveness/readiness regressions pass.

Stable consumer contracts:

- Actor(enterpriseId, Status ACTIVE|DISABLED, Set<Role> USER|PLATFORM_ADMIN).
- Person(stableId,displayName,department,active); PeoplePage(items,nextCursor).
- IdentityPort.resolveSession(String): Optional<Actor>.
- DirectoryPort.search(String query,String cursor,int limit): PeoplePage.
- SessionPort extends IdentityPort: resolve(String): Optional<ResolvedSession>; revoke(String token,String traceId); revokeAll(String enterpriseId,String traceId).
- SessionIssuer.issueForVerifiedIdentity(String enterpriseId,Duration lifetime): IssuedSession, production unavailable.
- IdentityLifecyclePort.setStatus(String enterpriseId,Status status,Instant verifiedAt,String traceId): void, server-only authoritative input.
- MeetingAccess.require(Actor,String meetingId,Action READ|EDIT|MANAGE): void and AccessDeniedException, **contract only**.

B03/V008 must wire shared ACTIVE-identity + current owner/effective confirmed READ grants in the protected transaction, map denial to403, and run final roster/admin/revocation tests. Arbitrary/stale Actor is not proof of access. There is no implementation or read of legacy meeting_acl in runtime code; no synthetic meeting/roster/grant tables were created for qualification.

## Migration freeze, execution and fixture ownership

V007 exact SHA256 **`859970b83fc5623b18340c2de793ea35429cc3c0da5cbd1365b4763a5a64780d`**, Flyway checksum **`157746768`**. The exact SHA was sent before first Flyway execution; root independently verified and acknowledged central freeze. No SQL byte was changed after formal application/shared migration status. Corrections, if discovered by review, require a centrally registered forward version.

V007 preflight refuses nonempty legacy identity/session/ACL before permanent DDL. Authoritative verification/issuance dates, department IDs and confirmed-grant origin are unavailable; they are not guessed, coerced, truncated, silently revoked or deleted. Existing deployments require a separate versioned authoritative mapping/backfill/recovery plan. Empty controlled upgrade installs canonical department/roles, generation/verification, session generation/issued_at, NO PAD identity IDs and constraints. Legacy display department/platform_admin remain expand-window metadata with safe defaults, never authority. Meeting/grant/roster structure belongs to V008.

Fixture ownership and preservation:

- **Positive formal:** `ama-test-b01-c8517df9ef69`, loopback45905. Read-only container label/loopback/table-count inventory confirmed it owned and empty before first adoption of reviewed V006 and frozen V007. Only this successful positive fixture was reused/reset for positive test runs. No failed/unknown history was reset.
- **Negative formal:** `ama-test-b01-768977c381e0`, loopback46617. Separately created/read-confirmed empty. Migrate-to006, seed controlled legacy rows, attempt exact V007 once, expected preflight refusal. Left intact with007 success0; never reset, repaired or rerun against that failed history.
- **Clearly separate design-only sandbox:** `ama-test-b01-5e2d9b66e6c2`, loopback44735. Explicit review-only raw SQL/candidate validation before formal freeze, no Flyway/history. It was never retroactively relabeled after a formal migration.
- **Preserved old B02:** `ama-test-b01-c5950c373972`, loopback45635, untouched during resumption. B01 failed45443 and root45173 never used/reset. No other agent fixture was stopped.

All formal DB tests use the pinned helper MySQL8.4.6 image and the new task-owned disposable ama_b01_fixture schema. No H2 is used to qualify identity persistence. Unit health tests retain their existing unrelated H2 fixture. Credentials stay ignored; no password value is printed.

Observed positive Flyway history (all success1):

|Version|Checksum|
|---|---:|
|001|1201435595|
|002|1757300424|
|005|-1999355348|
|006|-1744360556|
|007|157746768|

Negative history has the same001/002/005/006 success1 followed by007/checksum157746768/success0. Assertions and preserved snapshots prove all three legacy source tables' rows and SHOW CREATE TABLE definitions remain unchanged after refusal; department/role tables do not exist there. Positive metadata shows utf8mb4_0900_bin stable IDs, LONGTEXT display names, unsigned generation, DATETIME(6), independent role table, session constraints and no future domain tables.

## RED/GREEN chronology and actual verification

All commands run in the assigned worktree with pinned phase-a/.tools/env.sh. Real IT commands explicitly source the correct owned fixture environment. Raw full logs and current XML/metadata/DDL/frozen SQL copies are preserved under **`/home/tanren/ama/.worktrees/phase-b-identity/artifacts/phase-b/b02-prerequisite/`**, with sha256.json. These are ignored local evidence, not production credentials.

Behavioral RED evidence:

- Original anonymous boundary: expected401, actual403 (1 assertion failure); original schema expected1, actual0. Those pre-B00 logs/fixture were preserved; their provisional ACL tests were superseded, never represented as final ACL acceptance.
- `/tmp/b02-resume-red-contracts.log`: 3 executed/3 assertion failures (mutable privileges, invalid Actor, timestamp precision) → `/tmp/b02-resume-green-contracts.log`, 3/3 GREEN.
- Some provider/cookie/source setup was prepared behind the V006 gate and initially had **compile-only RED**, `/tmp/b02-resume-red-compile.log`. This is not represented as a behavioral assertion failure. First formal10-test identity run passed after dependency arrival; SQL positive/refusal checks passed first formal execution after explicit design-only validation.
- `/tmp/b02-red-generation-mutation.log`: removed generation predicate intentionally; focused disable/re-enable test expected401, actual200. Restored the predicate unchanged and subsequent full18-test run GREEN. This is mutation validation, not a claim the original predicate was missing in production preparation.
- `/tmp/b02-red-ambiguous-headers.log`: prerequisite unit stage caught diagnostic secret output and invalid Origin startup (2 assertion failures); fixes redact diagnostics/truncate issued expiry and validate Origin configuration.
- `/tmp/b02-red-ambiguous-headers-actual.log`: 2 executed/2 failures; duplicate Origin and duplicate CSRF writes returned204 instead of403. Added exact single-header checks → full18 GREEN.
- `/tmp/b02-red-lifecycle-audit-actor.log`: focused test failed because server verification impersonated the affected employee as actor. Fixed to SYSTEM_IDENTITY_SYNC → final full18 GREEN.

Final successful checks:

1. **`python3 tools/checks/run.py it IdentityAccessIT`**, `/tmp/b02-final-identity-it-after-review.log`: **18 executed, 0 failures/errors/skips**, two actual loopback API nodes/shared real MySQL. Includes anonymous boundary; both-node revoke and disable/re-enable; logout-all scope; expiry/forgery; exact IDs and DB roles; Origin/CSRF attacks including duplicates; same-name department/paging; public/management separation; production unavailable issuer/directory; audit rollback; concurrent revocations; node restart; forged browser headers/Basic credentials; correct system audit actor. This final Maven verify also reran all delivered Java unit classes successfully after the last narrow Java fix.
2. **`python3 tools/checks/run.py it IdentityMigrationIT`**, `/tmp/b02-final-migration-it.log`: **1 executed, 0 failures/errors/skips**. Ordered history/repeated migrate no-op, NO PAD case/trailing space IDs, department FK, verification/generation/role/session constraints, long display name, absence of future domain tables.
3. **`python3 tools/checks/run.py it IdentityMigrationRefusalIT`**, `/tmp/b02-v007-refusal.log`: **1 executed, 0 failures/errors/skips** on the separate new negative fixture, preserved failed history. Exact expected Flyway refusal log was narrowly captured on its test thread; unrelated errors stay visible. No broad log muting.
4. **`python3 tools/checks/run.py all`**, `/tmp/b02-final-current-suite.log`: **Python74 +web8 +Java28**, all passing, zero skips; OpenAPI validation/bundle **2 checks PASS**. Java includes StorageRouter8, DeploymentConfig9, IdentityContracts3, IdentitySecurity6, ContractClient2. Existing health/liveness/readiness/source-network regressions pass. This full current suite was run once for the final prerequisite feature; after the later audit-actor-only Java fix, final canonical IT verify reran all Java units. Unchanged Python/web/OpenAPI checks were not needlessly rerun.
5. Web `npm run generate:api`, `npm run check:api`, `npm run typecheck`: exit0; generator byte-for-byte match and vue-tsc success, `/tmp/b02-resume-generate.log`. Generated schema has remained unchanged afterward; final current suite OpenAPI lint/bundle again passed without warnings.
6. `git diff --cached --check`, `git diff --check`: exit0 before commit. Exact V007 SHA rechecked after commit; unchanged. Source diff self-reviewed with baseline changes separated.

The runner deliberately clears stale selected-group JUnit reports. Older successful migration/refusal runs retain raw full command logs and the runner's PASS after reading their then-fresh XML; the final copied XML set is not falsely described as containing every earlier selector's XML. Latest18-test XML and latest Java unit XML are preserved in the evidence bundle. No skipped/pending company contract test is counted as passing.

## Self-review, remaining gates and exclusions

Self-review covered every owned production boundary and SQL bytes, generated types and test/evidence ownership. Findings fixed with real RED/GREEN: duplicate security headers, diagnostic secret output/precision, invalid Origin config and server lifecycle audit attribution. Verified SQL never reads legacy ACL for authorization; role flags/claims never become body permission; API/health chains remain separate; all revocation/audit mutations use bound transactions. No shared-source write or helper/subagent dispatch occurred.

Full B02 stays open: B03/V008 must implement owner/effective confirmed READ grants, confirmed-roster source checks, admin/body denial, draft-never-grants and revoke/race/file rechecks with final independent review. Platform roles never override those object permissions. MeetingAccess is intentionally not instantiated yet; no meeting-body test route or fake meeting/grant fixture was added.

Actual enterprise identity/directory versioned protocols, stable-ID budget and mappings, role/consent authority, lifetime/disable propagation and real login/logout/disable evidence remain pending. Production token issuance/entropy and cookie callback integration are unqualified because issuance stays unavailable; controlled opaque token fixtures are not real SSO evidence. No company calls, credentials, mail, publication, fencing, third production node, main merge or push. No capacity/HA/RHEL/production certification is inferred from local tests.

---

## Archived preparation checkpoint (retained chronology)

Historical status at preparation checkpoint: **AWAITING_REVIEWED_V006**. The following section describes that earlier paused state, not the current delivery status above. At that time no V007 execution, real-DB qualification or final ACL acceptance was claimed.

## Preserved work and baseline

- Assigned worktree `/home/tanren/ama/.worktrees/phase-b-identity`, branch `codex/phase-b-identity`.
- Frozen V002-only checkpoint `e9990c8ee1584a7d8e7d1a259f71908624eb6b25` retained; exact SHA256 `68b0d478504a554344cb12dfa8ef79c62da5c6e3455317791762fca25e70f7d0` unchanged.
- Reviewed root `45ce136f4340a50a518c1dc7484c70d8caf26ca0` merged mechanically; merge HEAD `01a3d3c7f9332f9f93a7029d5317a3a310efc003`. No owned implementation commit yet because real DB validation is dependency-gated.
- Original draft sources copied before refinement to ignored `.tools/b02-pre-b00/`. Original pause evidence/report preserved in `task-B-02-b00-input.md` and its referenced logs.
- Old fixture `ama-test-b01-c5950c373972`, port45635, preserved unchanged during resumption. New owned fixture `ama-test-b01-c8517df9ef69`, port45905, schema `ama_b01_fixture`, is reserved for resumed positive/refusal tests. Credentials remain ignored and unpublished. Root45173 and other fixtures untouched.

## Prepared scope

V007 only: `backend/adapters/src/main/resources/db/migration/V007__identity_sessions.sql`, current draft SHA256 `859970b83fc5623b18340c2de793ea35429cc3c0da5cbd1365b4763a5a64780d`. **Not executed.** No invented V006 placeholder, outOfOrder, checksum repair, meeting/roster/grant tables, or legacy ACL authorization.

Approved strict legacy preflight runs before permanent DDL and refuses any legacy identity/session/ACL rows because authoritative provenance is absent. It does not fabricate verification/issuance dates, department IDs, role mapping or confirmed origins; it does not delete/revoke/coerce legacy rows. Real existing installations require a separately reviewed authoritative mapping/backfill/recovery plan before V007. The expand window retains old department display text/platform_admin columns with safe defaults, never as authority. Empty fixture upgrades create canonical department/roles and exact NO PAD identity IDs, generation and session issuance fields.

Prepared runtime implementation: DB resolver with one fresh shared query of ACTIVE/generation/expiry/revocation, DB lifecycle generation advance and audited revoke/all-revoke/status updates using B01's bound TransactionRunner/JdbcAuditAdapter. No production issuance/callback/test-login route; CompanyIdentityAdapter always fails unavailable. CompanyDirectoryAdapter also remains unavailable; canonical JDBC directory projection is activated only by test-source/profile configuration. No local password/provider protocol is invented.

Prepared API: public and management listener isolation; opaque __Host-AMA_SESSION cookie helper with Secure/HttpOnly/Path=/SameSite=Lax; session-bound CSRF plus exact configured Origin for writes; GET session/directory and POST current/all logout; safe code/message/traceId errors. OpenAPI identity slice/root refs and generated TS are prepared. Existing RuntimeConfiguration health chain was not changed.

MeetingAccess is a contract only. Its V008 implementation must revalidate shared ACTIVE identity and current meeting owner/effective grants in the protected transaction; arbitrary/stale Actor alone is insufficient. No legacy meeting_acl read exists in new runtime code. Final owner/grant/roster tests remain deferred explicitly to B03/V008.

## Stable interface draft sent to coordinator

- Actor(enterpriseId, Status ACTIVE|DISABLED, Set<Role> USER|PLATFORM_ADMIN); stable ID preserved exactly, roles copied defensively.
- Person(stableId, displayName, department, active); PeoplePage(items,nextCursor).
- IdentityPort.resolveSession(String): Optional<Actor>.
- DirectoryPort.search(String query,String cursor,int limit): PeoplePage.
- SessionPort extends IdentityPort: resolve(String): Optional<ResolvedSession>, revoke(String token,String traceId), revokeAll(String enterpriseId,String traceId).
- SessionIssuer.issueForVerifiedIdentity(String enterpriseId,Duration lifetime): IssuedSession, **production unavailable**.
- IdentityLifecyclePort.setStatus(String enterpriseId,Status status,Instant verifiedAt,String traceId): void, server-only input with no public endpoint.
- MeetingAccess.require(Actor,String meetingId,Action READ|EDIT|MANAGE): void, safe AccessDeniedException.

## Actual preparation verification

Pinned runtime sourced from phase-a/.tools/env.sh. All following runs are pure/compile/API checks, with no migration execution:

1. `python3 tools/checks/run.py unit IdentityContractsTest`: RED `/tmp/b02-resume-red-contracts.log`, **3 executed, 3 assertion failures, zero errors/skips**. Caught mutable privilege set, invalid actor allowed, nanoseconds not truncated. GREEN `/tmp/b02-resume-green-contracts.log`, **3 executed, zero failures/errors/skips** after implementation.
2. `./mvnw -B -ntp -pl backend/integration-tests -am -DskipTests test-compile`: RED compile `/tmp/b02-resume-red-compile.log`, missing CompanyIdentityAdapter and SessionCookies consumers. This is compilation evidence only, not behavioral RED. GREEN `/tmp/b02-resume-compile.log`, BUILD SUCCESS; all integration sources compiled. Last small test-fixture corrections happened afterward and will be recompiled on resume.
3. `python3 tools/checks/run.py unit IdentitySecurityTest`: `/tmp/b02-resume-green-security.log`, **3 executed, zero failures/errors/skips**. Production issuer unavailable, secure cookie properties, production test-mode configuration rejected. No database used. These lack prior assertion RED (only compilation RED) and require behavioral mutation/RED verification on resume.
4. `python3 tools/checks/run.py contract OpenApi`, web `npm run generate:api`, `npm run check:api`, `npm run typecheck`: logs `/tmp/b02-resume-openapi.log`, `/tmp/b02-resume-generate.log`; completed exit0. OpenAPI validation/bundle 2 checks PASS, generated types match byte-for-byte, vue-tsc exit0. Initial bundle warning for duplicate Forbidden schema name was removed by naming the identity error BusinessForbidden; latest bundle has no warning.
5. `git diff --check`: exit0 for tracked changes at this checkpoint (untracked sources still require final self-review).

Prepared but **not run**: IdentityAccessIT (two actual loopback nodes/shared real MySQL; anonymous boundary, both-node revoke, generation disable-reenable, all-logout scope, expiration/forgery, exact IDs/roles, Origin/CSRF, same-name directory/paging, listener separation, production unavailable provider). IdentityMigrationIT (strict refusal with source rows/schema retained, ordered 001→002→005→006→007, NO PAD IDs, role/session constraints, no future domain tables). These tests must run after reviewed V006 arrives; they do not yet establish implementation correctness.

## Required continuation before prerequisite delivery

Receive and merge independently reviewed V006 from root; do not execute V007 earlier. On new owned fixture, establish meaningful behavior RED/GREEN, execute canonical IdentityAccessIT and migration/refusal tests, add DB rollback/concurrency/restart/failure-path evidence as needed, and fix actual defects. Confirm exact target metadata and pre/post upgrade state/Flyway history. Complete affected full regression and OpenAPI/generated-type checks, self-review all owned artifacts including preserved draft changes, scoped commit and updated final report. Root independently reviews originalBASE3445972..final with reviewed baseline differences identifiable.

Full B02 remains open until V008 owner/effective confirmed READ grants, roster integration, final permission tests and independent review. Actual company SSO/directory contract, stable-ID budget, role mapping, lifetime/disable propagation evidence, real login/logout/disable qualification and enterprise security approval remain pending. No company call, real credentials/email/publication, fencing, third node, main merge or push occurred.
