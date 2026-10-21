# B04 full registry/upload/API implementation report — independent review required

Status: implementation committed for fresh SPEC and quality review; not adopted. BASE `8da440d39e1b8e8dcdfc34722908d29b5aa3aa8a`; HEAD `d1e47ca69e68caa6bd51fee1706998b3e6cd2506`; branch `codex/phase-b-file-registry`; tree `/home/tanren/ama/.worktrees/phase-b-file-registry`. No push/main merge, children, SQL/POM changes, unreviewed V012/Minutes consumption or company integration. The only added infrastructure path is the explicitly authorized protected configuration guard in FileServiceMain; native storage algorithms and ports remain unchanged.

## Delivered behavior and consumer boundary

The public upload routes, durable V009 state machine, registry, typed domain policy ports, managed backend assembly/readiness, safe content streaming and OpenAPI/generated client are implemented. Initial begin, each receipt, freeze and registration use current B03 identity/subject/root authorization in short transactions. All real storage I/O is outside those transactions. Current purpose/epoch/published/lifecycle/governance are checked on both sides of I/O. ACKs follow actual commit. FINALIZING is irreversible; exact persisted receipts, original opaque token, Unicode name, purpose, whole hash and physical location recover the same decision after real API JVM death. AVAILABLE file, completed session and technical protective reference are atomic. Lost DB/storage ACK, concurrent completion/init, late part and rollback behavior are exercised against actual MySQL and HTTP adapters.

Production purpose policy is unavailable until a real provider is installed. Production business reference registrations are empty; UPLOAD_SESSION is never a business read grant. Controlled INPUT_BATCH_FILE tests bind actual V011 batch/file/hash rows and current transcript pointer and verify original upload_session.purpose, rather than inventing a purpose column or TEST grant. B06 integration is not claimed.

Registry composition acquires identity → subject → meeting exactly once via registry.lock, then exposes the exact B03 capability. Policies prelock real domain rows before sessions/files/task rows; bindAll prelocks the full sorted session set then the full sorted file set. Capabilities reject foreign issuers/transactions. Handoff changes only the exact technical reference and increments reference_epoch atomically. Caller audit/events and shared outbox flush remain last. New bindings reject unavailable/deleted files, cancel epoch and location revision mismatches. No cleanup worker/physical delete API is enabled because actual retention/absence providers are absent; D07 is not implemented here.

Managed backend ID is SHA-256 over the approved counted four-byte big-endian/strict UTF-8 v1 field list, excluding ID itself and credentials/runtime values. Original proposal bytes and SHA are archived before approval clarification; final field order/count and exact no-DNS canonical-origin grammar are in `docs/acceptance/b/file-registry-integration.md`. Both API and protected FileServiceMain derive the local logical volume/root/service/filesystem identity. Historical lookup revalidates its original definition and never follows a new default. Stable logical VIP/volume identity allows deployment switching without putting node device names in the ID; this is not HA/fencing proof. Missing/mismatching history rejects routing.

Production FileConfiguration enforces HTTPS S3 after ID matching, without an insecure flag. Controlled HTTP RustFS adapters are injected only in test composition. Company CA trust uses the JVM trust store as documented; no company endpoint/certificate qualification occurred. Credential/timeout rotation preserving identity is checked by managed-definition unit tests; no live company credential rotation is claimed. Empty backend configuration is DOWN and returns declared 503 even when a test purpose provider is present.

## Initial checkpoint verified execution counts (retained; correction below)

All counts below come from fresh XML, not Maven textual totals or infrastructure history. Evidence root: `artifacts/phase-b/b04-registry/`.

| Execution | Result | Evidence |
| --- | --- | --- |
| Actual API HTTP + MySQL + local native HTTP/filesystem business cohort | 35 / 35 | `managed-local-03.log`, `managed-local-03/TEST-ama.storage.FileRegistryUploadIT.xml` |
| Actual API HTTP + separate MySQL + Java S3/RustFS business cohort | 35 / 35 | `managed-s3-01.log`, `managed-s3-01/TEST-ama.storage.FileRegistryUploadIT.xml` |
| Actual API JVM termination/restart + separate FileServiceMain JVM | 1 / 1 | `managed-local-restart-03.log` and matching XML |
| Actual API JVM termination/restart + real Java S3/RustFS | 1 / 1 | `managed-s3-restart-01.log` and matching XML |
| Fresh production API changed-bucket rejection + protected native same-ID root mismatch startup rejection | 2 / 2 | `managed-binding-final.log` and matching XML |
| Unconfigured purpose / empty readiness / configured purpose without backend | 3 / 3 | `config-http-final.log` and matching XML |
| Full Java unit gate, including four codec/identity cases and production HTTPS boundary | 67 / 67 | `unit-final.log`, `unit-final/*.xml` |
| OpenAPI lint/bundle, generated TypeScript regeneration, vue-tsc | PASS | `openapi-final.log`; before/after generated SHA files match exactly |
| Independent Python codec bytes, 10 immutable migration files | PASS | `independent-vectors-check.txt`, `immutable-sql-check.txt` |

The primary final IT accounting is 77 backend/config executions across 41 distinct test methods: 36 exercised separately on each backend, plus five configuration/HTTP cases. The 35-case local cohort hosts the real native service in the test JVM and traverses HTTP; the real restart case separately runs both API and FileServiceMain as child JVMs. The application-context restart case is not counted as real JVM death proof. An additional single-method diagnostic execution is recorded below and is not silently folded into that matrix. The previously accepted native 39/S3/10001-part infrastructure runs are not counted as new B04 proof and were not rerun.

## Meaningful RED, intermediate failure and setup record

Every listed log and XML is retained unchanged. `execution-index.json` inventories raw suites but is not a sum of unique tests; repeated historical runs are not additive acceptance counts.

- `red-http-01`: initial fixture token had the wrong length and returned 401; setup error, not valid feature RED. Corrected token setup led to `red-http-02`, authenticated missing upload route (403 instead of expected 503), then green-purpose-01.
- `red-workflow-01`: 11 workflow failures before registry implementation. `workflow-02` retained a Java method-reference compilation failure; workflow-03 passed 11.
- `red-reference-01`: real registered-reference GET expected 200 but returned 404, then reference-02 passed 12.
- `red-faults-01`: 20 tests with four failures: three commit failures fell through to 403 instead of declared 503, and begin replay omitted current cancellation epoch. Corrected handlers/revalidation; faults-02 passed 20.
- `red-bindings-01`: test composition initially used a different DataSource holder during domain validation, causing unsupported synchronization; corrected the controlled policy to use the same actual probe DataSource. `red-bindings-02` then exposed the real multi-file session/file lock-order bug. Batch prelocking fixed that behavior.
- `red-openapi-01`: actual HTTP 409 was absent from the declared schema union. Updated source contract, regenerated client and checked actual 409/503/404/416 response bodies through the declared source schema.
- `red-binding-codec-01`: executable independent golden vector mismatch before the codec implementation. No S1 implementation was consumed.
- `red-readiness-01`: empty backend configuration returned ready 200 instead of 503. `managed-local-02` retained a compile failure from the obsolete Spring Boot health package; actual Boot 4 package fixed. Final HTTP and actual-backend readiness tests pass.
- `managed-local-restart-02`: invalid runner invocation with multiple positional selectors, so no tests executed. Its copied FileRegistryUploadIT XML is explicitly a stale copy from the prior 35-case run, preserved as evidence only and excluded from counts. Subsequent invocations use one valid selector.
- `red-production-tls-01`: valid managed HTTP S3 definition was incorrectly accepted by actual FileConfiguration; one test failed because no exception was thrown. Production-only HTTPS guard fixed this. The final changed-bucket test uses HTTPS so its failure specifically proves identity mismatch, not TLS rejection.
- `red-empty-backend-01`: configured test purpose plus no backend caused NullPointerException and generic 403. Explicit absent-default handling now returns declared 503; final HTTP suite passes 3.
- S3 fixture startup initially failed because noninteractive sudo could not create the owned directories (`s3-start.log`). Root-authorized creation of only the new exact fixture directories allowed the reviewed helper to start (`s3-start-02.log`). No existing fixtures were repaired/reset.

## Unresolved earlier 401 — not claimed fixed

Exact occurrence: `FileRegistryUploadIT.orphanIsNotReadableAsBusinessFile`, first `begin("abc")` call, initial POST `/api/v1/meetings/{id}/uploads`. Its @BeforeEach identity/session creation and POST meeting creation had already succeeded. The failure occurred before that test installed the file-registration rollback hook or sent bytes. Expected 201; actual 401 with `{error:{code:UNAUTHENTICATED,message:Authentication required,traceId:2b235500-5f04-43b7-a742-8fb19a63f7ac}}`. Raw evidence: `managed-local-01.log` lines 278 onward and `managed-local-01/TEST-ama.storage.FileRegistryUploadIT.xml`. This was a 25-case run with one failure, not an expected RED.

Available evidence does not include the token, cookie, actor ID, session row snapshot or request headers, deliberately avoiding secret capture. The original log shows valid schema 011.1, successful preceding test setup implied by the failed stack at begin, and the response/status/trace. It cannot distinguish a fixture/session transient from application behavior.

Causal investigation read the actual SessionSecurityFilter cookie/session-resolution branches, JdbcIdentitySessions' fresh ACTIVE/generation/revocation/issued-at/expiry query and the fixture's 32-byte token construction, beforeEach probe/hook resets and joined concurrent work. The reviewed V007 issued_at is DATETIME(6); no rounding mismatch was established. No causal explanation was proven and no identity code was changed. Test-only diagnostics now print only status, session/identity generation, revocation boolean, issued-age and expiry remaining on a 401; they never print token/hash/credentials. No retry was added. Later full local/S3 green runs do not establish a fix.

Narrow diagnostic reproduction: `python3 tools/checks/run.py it FileRegistryUploadIT#orphanIsNotReadableAsBusinessFile` once on the owned local fixture. Result: PASS, 1 executed / 0 failures / 0 skipped; no 401 occurred and therefore the diagnostic branch emitted no session snapshot. Evidence: `diagnostic-orphan-01.log` and matching XML. If it succeeds, that means only non-reproduction under the instrumented current code, not resolution of the original uncertainty. Independent review must assess this retained limitation.

## Owned fixtures, preservation and reproducibility

New local-business MySQL: `ama-test-b01-999352219d61`, loopback 45333. New S3-business MySQL: `ama-test-b01-ca05078d2c9e`, loopback 46515. Both pinned reviewed MySQL image, 2 CPU/1 GiB/256 PID, process-only native AIO disabled; original empty schema migration to exactly ten reviewed migrations through 011.1 is logged. Private wrapper source is archived, no sysctl/helper modification or reset/repair/outOfOrder. New RustFS: `ama-b04-java-44ef702b9e`, loopback 47779, pinned reviewed digest, 2 CPU/2 GiB/256 PID. Selected IDs/image/resource values/actual ports and process arguments are in owned-fixture allowlists; no full Docker Config.Env.

All databases, buckets, local roots, native journals, private fixture environments/PKI and failures remain in the owned tree/fixtures. Test teardown closes only owned connections/servers/processes; it does not delete database/blob history. Only selected child `.log` files are copied into the archive. No private configuration, key material, environment file, DB dump or blob data is archived. Selected private credential values were scanned in memory against all archived bytes with no match. The two temporary read-only node_modules symlinks were removed after verification, not their shared targets.

`artifact-allowlist.json` gives exact archive paths/sizes; `SHA256SUMS` hashes every archived file except itself; archive `.gitattributes` is `** -text`. Required source hashes, exact owned-source paths/hashes, original proposal SHA and unchanged SQL hashes are included. Pin runtime by sourcing private phase-a `.tools/env.sh`; source only the corresponding owned MySQL and S3 fixture env files when reproducing. No company service, HA/fencing/capacity/mail/model/KB operations or heavy unchanged infrastructure tests occurred.

## Review handoff

Review the complete BASE..HEAD owned source and evidence, especially frozen decision/replay, final authorization, capability/lock ordering, original-purpose reference handoff, config identity and HTTPS boundary, and the unresolved 401 limitation. Consumer handbook is `docs/acceptance/b/file-registry-integration.md`. Root must obtain fresh independent SPEC and quality review before adoption or allowing B06 to consume it. This report does not claim review approval, B06/D06/D07 completion, company-provider qualification or deployment.

### Initial checkpoint owned source/doc paths (additional correction test below)

- `backend/adapters/src/main/java/ama/storage/FileBackendCatalog.java`
- `backend/adapters/src/main/java/ama/storage/FileBackendDefinitions.java`
- `backend/adapters/src/main/java/ama/storage/JdbcFileRegistry.java`
- `backend/adapters/src/main/java/ama/storage/JdbcFileUploads.java`
- `backend/adapters/src/main/java/ama/storage/ManagedBackendAdapters.java`
- `backend/adapters/src/test/java/ama/storage/ManagedBackendIdentityTest.java`
- `backend/api/src/main/java/ama/api/FileConfiguration.java`
- `backend/api/src/main/java/ama/api/FileController.java`
- `backend/api/src/main/java/ama/api/FileErrors.java`
- `backend/api/src/main/resources/application.yml`
- `backend/api/src/test/java/ama/api/DeploymentConfigTest.java`
- `backend/api/src/test/java/ama/api/FileConfigurationTest.java`
- `backend/contracts/src/main/java/ama/contract/FileContracts.java`
- `backend/core/src/main/java/ama/storage/FilePurposePolicy.java`
- `backend/core/src/main/java/ama/storage/FileReferencePolicy.java`
- `backend/core/src/main/java/ama/storage/FileRegistry.java`
- `backend/core/src/main/java/ama/storage/FileUploads.java`
- `backend/core/src/main/java/ama/storage/ManagedBackendId.java`
- `backend/file-service/src/main/java/ama/file/FileServiceMain.java`
- `backend/integration-tests/src/test/java/ama/storage/FileBackendBindingIT.java`
- `backend/integration-tests/src/test/java/ama/storage/FileRegistryHttpIT.java`
- `backend/integration-tests/src/test/java/ama/storage/FileRegistryRestartIT.java`
- `backend/integration-tests/src/test/java/ama/storage/FileRegistryUploadIT.java`
- `backend/integration-tests/src/test/java/ama/storage/ManagedLocalFixture.java`
- `backend/integration-tests/src/test/java/ama/storage/ManagedRegistryFixture.java`
- `backend/integration-tests/src/test/java/ama/storage/RegistryApiProcess.java`
- `backend/integration-tests/src/test/java/ama/storage/RegistryDeclaredError.java`
- `backend/integration-tests/src/test/java/ama/storage/RegistryObservedStorage.java`
- `backend/integration-tests/src/test/java/ama/storage/RegistryProbeSource.java`
- `backend/integration-tests/src/test/java/ama/storage/RegistryProductionConfigProcess.java`
- `contracts/openapi/api.yaml`
- `contracts/openapi/files.yaml`
- `contracts/openapi/identity.yaml`
- `docs/acceptance/b/file-backend-binding-proposal.md`
- `docs/acceptance/b/file-registry-integration.md`
- `web/src/shared/api/generated.ts`


Committed archive verification: all 118 archive files exactly match committed Git blob bytes, including raw failed logs/XML. SHA256SUMS digest: `9a13b6a6691ae100452c3f731e9629cad1ea8fa62b9bc84165cd943c7c21b317`. Working tree was clean after commit; no push or merge performed.

## Final bounded correction and final HEAD

Final HEAD `d1e47ca69e68caa6bd51fee1706998b3e6cd2506` includes initial `5e7022c20c9389ad319157320408901f3df089ac` plus the catalog startup race correction. Full review range remains BASE `8da440d39e1b8e8dcdfc34722908d29b5aa3aa8a` through this final HEAD. The original report/checkpoint bytes are preserved at `artifacts/phase-b/b04-registry-config-race/initial-report-5e7022c.md`; initial raw archive/manifest were not modified.

# B04 catalog startup race correction — fresh review required

Initial source checkpoint: `5e7022c20c9389ad319157320408901f3df089ac`, with full B04 BASE `8da440d39e1b8e8dcdfc34722908d29b5aa3aa8a`. Initial report copied byte-for-byte to `initial-report-5e7022c.md`; digest and initial archive manifest digest are in `initial-checkpoint.txt`. The entire initial committed evidence archive remains unchanged.

Root identified unlocked first-registration SELECT then INSERT. `FileBackendCatalogIT` uses two real catalog instances, actual owned MySQL, real native local storage HTTP probes, and a JDBC proxy that delays actual SELECT results after execution; it never substitutes rows or errors. New local roots are independently owned under target/registry-catalog-race and preserved. Existing owned local-business MySQL remains unchanged except new test rows; no reset, migration mutation or other fixture access.

RED `red-01.log` / XML: 3 executed, 2 failures. `identicalConcurrentFirstRegistrationsBothSucceed` gates both genuinely empty reads before either insert and gets an actual DuplicateKeyException. `racedNewerRevisionIsNotOverwrittenOrAcceptedAsOlderConfiguration` pauses an older revision-2 process after reading revision 1, lets a second real catalog register revision 3 READ_ONLY, then releases revision 2; the old implementation ignored the zero-row CAS and incorrectly reported successful configuration. `conflictingSameRevisionFirstRegistrationsFailClosed` passed with one accepted configuration and one rejected.

The narrow correction catches only DuplicateKeyException from a first insert, reloads the actual row and applies the existing type/revision/status validation. Losing a revision compare-and-set now requires the actual row to match the desired type, revision and status exactly; otherwise startup fails closed. It does not retry or overwrite newer state, remap physical ID, alter storage algorithms or add a schema migration. Storage probes remain outside database transactions. No broad cohort was rerun.

GREEN `green-01.log` / XML: all 3 cases pass, zero failures/errors/skips. The same reactor also executes the existing 67 Java unit tests, archived separately under green-unit-reports. Initial local/S3 35-case cohorts, real JVM recovery, managed binding/HTTPS/readiness, OpenAPI and original unexplained 401 remain exactly as reported in the initial checkpoint. This correction does not explain or claim to fix that 401; the earlier narrow diagnostic's non-reproduction is unchanged.

Consumer handbook documents concurrent registration and compare-and-set semantics. Final review must cover full BASE through this correction, not only the delta. No push/main merge, cleanup, company/HA request, SQL/shared POM/native algorithm change or child agent occurred.

Final primary IT accounting: initial 77 executions plus 3 focused catalog race cases = 80 executions across 44 distinct methods. The additional one-method 401 diagnostic remains separately counted and does not establish a fix. The correction reactor executes 67 unit tests with no failures/errors/skips; do not add repeated unit runs as distinct tests.

Additional source: `backend/integration-tests/src/test/java/ama/storage/FileBackendCatalogIT.java`. Correction also modifies the previously listed `FileBackendCatalog.java` and consumer handbook. Exact correction source hashes and raw RED/GREEN/67-unit XML are under `artifacts/phase-b/b04-registry-config-race/`.

Correction manifest SHA256: `4bd99a72a4dab5ca97ee7b62565ebdfc5336a5beb653dff7a29b6cf8c7ae76bb`. All 144 initial/correction artifact files exactly match final committed Git blob bytes and both manifests validate. Final tree is clean; no push/merge performed. Fresh independent SPEC and quality review remains required.
