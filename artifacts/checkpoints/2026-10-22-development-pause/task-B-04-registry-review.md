# B04 registry/upload/API — fresh independent full review

Date: 2026-10-22. Reviewer: `/root/b04_registry_review`. **SPEC: changes required. Technical quality: changes required. One actionable P2 finding; no P0, P1 or P3 findings.** The original unexplained 401 remains an explicitly tracked qualification uncertainty, not a demonstrated source defect or a resolved incident.

## Scope and authority

Reviewed the complete `8da440d39e1b8e8dcdfc34722908d29b5aa3aa8a..d1e47ca69e68caa6bd51fee1706998b3e6cd2506` range in `/home/tanren/ama/.worktrees/phase-b-file-registry`, including BOTH `5e7022c20c9389ad319157320408901f3df089ac` and `d1e47ca69e68caa6bd51fee1706998b3e6cd2506`. This is not a HEAD~1 review. HEAD remained unchanged and the source worktree remained clean after review.

First read the complete root review brief, then the activation/original/prepared briefs and final author report. Compared the synchronized delivery and B plans to both primary main and root: exact matches. Read the product supplement from the owner/root trees (exact match; it is not present in primary main). Applied the adopted B00-1.3/V009 upload, lock, reference and lifecycle requirements; current six-table dictionary and V009 SQL; B03 adoption/MeetingAccess/runtime/consumer handbook; B05 adoption, original handbook and mandatory fix addendum; accepted FileStoragePort/StorageContracts/StorageRouter and local/S3/selected-manifest integration boundaries. No pending heading in historical documentation was treated as overriding the adopted authority.

Independently checked all **181 changed paths, 2,741,493 final bytes** against exact final Git blobs, sizes and SHA-256 in the root path map: all match. Fully read every changed handwritten runtime, contract, helper, test and consumer document listed in the source coverage appendix. The generated TypeScript change was read against the source contract and independently regenerated in check-only mode. All 144 evidence paths were byte-audited and machine-parsed; substantive failure/provenance/contract claims were checked against source and raw results. Routine repeated Maven/JVM property output was machine-inspected rather than represented as a manual line-by-line reading of every duplicated XML property.

The supplied full diff is 2,780,763 bytes, SHA `7aac6a4adfbd9f8ded019598e10058c67f860bc4c2441f2065ffb279fb66aa2b`; source supplement is 300,477 bytes, SHA `3e8e2b52d01a03c290feb5dbbb049a506dd0b6d3c9ec97fe8a2b236acfcdd29c`. Both were independently hashed. Source was reviewed from the final complete files and the complete changed-source set, not inferred from these hashes.

## Actionable finding

### B04-R1 — P2: bound the download filename header independently of the stored display name

Location: `backend/api/src/main/java/ama/api/FileController.java:116–119` (unbounded Content-Disposition construction), with encoding at lines 133–148.

A valid accepted name such as `文` repeated 1,000 times is preserved correctly in the upload/file metadata, but the controller expands its full UTF-8 value into the response header. That creates a **9,050-byte Content-Disposition value alone**, before the remaining response headers. The pinned Tomcat 11.0.24 and Spring Boot 4.1.1 default response-header budget is **8,192 bytes**. Thus an otherwise authorized file becomes undownloadable, including through Range, because response commit throws `HeadersTooLargeException`. This is not speculative proxy behavior: it occurs in the shipped embedded server.

Reproduction and evidence:

- A memory-only Java probe called the actual compiled `FileController.encoded`, verified that `FileContracts.Begin` accepts the name, and exercised the pinned Tomcat output buffer: ordinary name succeeded; the Unicode name threw `HeadersTooLargeException`.
- Under the coordinator's subsequent explicit narrow exception to report-only writes, a new isolated loopback Tomcat probe invoked the actual `FileController.content` method, using an in-memory test registry solely to isolate the transport boundary. No database, storage service, existing fixture or company service was used. Its owned ephemeral server was stopped and destroyed.
- Actual HTTP results at loopback port 44751: normal name/full GET **200, 3 body bytes**; normal name/Range **206, 1 body byte**; long Unicode name/full GET **500, 0 body bytes**; long Unicode name/Range **500, 0 body bytes**. The log records the exception and `FileController.content(FileController.java:129)` at response commit.
- Existing `FileRegistryUploadIT.fullNameAndOpaqueTokenSurviveApplicationRestart` (line 385) and the real restart test check database name preservation, not a download with that name. `businessDownloadRangesAndDispositionAreExact` (line 893) uses the short default name and does not assert the disposition itself. Their green results therefore do not cover this failure.

Required correction: preserve the full original name in durable metadata and display semantics, while defining a bounded, safely encoded transport filename/fallback that fits the response-header budget. Do not impose a new product name cap or truncate the stored name. A larger fixed connector limit alone does not solve accepted unbounded names. Add focused real authorized GET and Range regressions with a long Unicode name, exact bytes/status, safe bounded headers and unchanged full metadata; retain the original failure evidence. Update the consumer wording that currently promises the full UTF-8 name in the header. This finding must be closed before B04 adoption.

Probe custody under root SDD `b04-header-review-probe/`:

| File | SHA-256 |
| --- | --- |
| HeaderReviewProbe.java | efc11e2849134afeb9c921cc9e1e4fe98571710a9acc7e3abbd0b1d670d16a34 |
| invocation.json | 5c1ad38a14a6894e490ded9f677137a22dc5a1412c8a376316a20a6c8c63a886 |
| probe.log | a5329df26c56d9ddc1d6b4a1d477ae89dd379eb01556baa6b9d892253f055878 |
| evidence-manifest.json | a1e029b32e46eee062f74904ebcffd0b3c66ca37be0411125dd9b4269350a1dc |

The invocation preserves the exact Java command/classpath; the manifest also records the actual controller/Begin class and pinned Tomcat/Boot JAR hashes. This is a focused transport regression, not new end-to-end authentication/storage acceptance.

## SPEC and technical assessment of the remaining implementation

| Area | Assessment and evidence |
| --- | --- |
| Durable init | `JdbcFileUploads.begin` checks the full idempotency request, current identity/root/purpose/epoch, performs actual backend begin outside the DB transaction, and commits the accepted opaque token, name, purpose and pinned location with the idempotency result before ACK. The final insert-or-lock idempotency operation precedes identity/subject/root/session locks. Concurrent losers retain only technical storage state, not business availability. |
| Parts | Both authorization boundaries require OPEN/current purpose/epoch and never-published state. Only actual adapter receipts are inserted; existing same-size/hash receipts are retained, different content conflicts. Post-I/O state check prevents late receipt ACK after freeze/abort. The fault probe executes real SQL and either rolls back before commit or drops ACK after actual commit; it does not fabricate DB results. |
| Freeze/recovery/completion | Exact all-receipt count, ordered contiguous numbering, each hash, overflow-safe size sum and whole hash are checked. OPEN→FINALIZING is committed before storage complete; no reopen/FAILED fallback exists. Persisted token/name/purpose/location/receipts reconstruct the same decision. Actual adapter complete, not stat/read, re-establishes durable storage. Registration reauthorizes and atomically writes AVAILABLE + COMPLETED/result + UPLOAD_SESSION reference/epoch. Concurrent completion returns one result; lost storage/DB ACK stays recoverable. |
| Abort | OPEN→ABORTED wins in the DB before storage abort. FINALIZING conflicts; completed replay preserves the file. Repeated abort retries provider abort. No cleanup of unknown or recoverable history is enabled. |
| Current authorization | Actual B03 access locks the current identity, subject and root and checks ACTIVE lifecycle/governance. Upload mutations additionally enforce publication and epoch. Current purpose is rechecked around storage boundaries and on terminal replay. Actor roles/status alone are not the authority. Published terminal replay cannot add new references or bytes. No original-uploader-only restriction was invented. |
| Capability and lock composition | Registry handles are issuer/actual same-DataSource transaction-holder bound. The registry acquires initial B03 access once and returns that exact capability. All policy prelocks must precede file phase. `bindAll` gathers originals, locks the complete sorted upload-session set, then complete sorted files, then mutates ledgers. Caller audit/outbox flush remains last. Foreign/expired handles and late policy prelocks are rejected. Arbitrary direct JDBC outside this supported contract is not claimed to be intercepted. |
| Genuine references | Production registrations are empty and reserved UPLOAD_SESSION cannot become a readable policy. Controlled tests use actual reviewed V011 input_batch/file/transcript rows and current pointer, not client booleans, a fictional purpose column or a hash-only grant. Bind verifies original session purpose/location/size/hash/epoch, AVAILABLE state and location revision. Exact technical-reference removal and business-reference insertion occur atomically with reference_epoch. |
| Download | Current meeting READ and a registered current business-reference proof precede the pinned backend read. Technical upload protection and possession of fileId are insufficient. Range arithmetic and 416 handling are sound; try-with-resources closes the body. Names are safely percent-encoded against header injection, but **R1 makes the transport sizing incomplete**. Periodic stream revocation is intentionally D06. |
| Managed identity | Four-byte big-endian field count plus four-byte strict UTF-8 lengths/bytes, correct six/eight ordered fields, no ID self-input, full lowercase SHA-256. Strict noncanonical origin/surrogate rejection matches the final grammar. Definitions exclude credentials/timeouts and per-node mount facts. Historical reads do not fall back to a new default. |
| Native guard | For managed 64-hex IDs, FileServiceMain independently derives identity from protected serviceOrigin/volume/filesystem and the normalized actual root relative to mountpoint before store/server construction. Existing mount/device/source/marker/role guard remains. Arbitrary infrastructure IDs remain outside managed catalog admission. Forged full protected deployment identity/volume provenance remains an operational qualification. |
| Production configuration | Production FileConfiguration validates identity then rejects non-HTTPS S3; no insecure production flag. Local assembly does not require S3 values. The low-level HTTP fixture remains compatible. Empty catalog/default fails unavailable/readiness DOWN. READ_ONLY admits the explicitly accepted frozen-recovery/read contract; DISABLED fails I/O. Historical definitions and revisions must exactly match DB state. |
| Corrected catalog races | Inspected all three real two-catalog/MySQL cases and their actual-query gates. Duplicate INSERT catches only DuplicateKeyException, reloads and uses exact validation. Lost CAS accepts only the identical desired resulting row; newer/conflicting rows are preserved and rejected. RED 3/2 failures and GREEN 3/0 match the source defect/correction; no blanket overwrite/retry introduced. |
| HTTP/OpenAPI | Strict fields/duplicate JSON/trailing tokens, bounded request reads, server-owned receipts and safe error DTOs. Actual 409/503/404/416 tests resolve the declared closed schema, including enum and UUID trace. Source and generated client agree; independent check-only regeneration passes. Storage keys/tokens/provider paths are not browser DTOs. The R1 transport 500 is outside the otherwise exercised declared error paths and needs correction. |
| Scope/deferred work | No SQL/POM/native algorithm changes, no imported V012 or S1 implementation. No deletion worker, full B06 consumer, purpose provider or company reference policy is falsely enabled. No complete B06/D06/D07 or production readiness is inferred. |

## Retained 401: independent causal assessment

Tracked item **B04-Q401 (unresolved qualification incident, not a code finding assigned a speculative severity)**.

The raw `managed-local-01.log:278–289` and XML contain exactly one failure in the 25-case run: `orphanIsNotReadableAsBusinessFile` failed at its first `begin("abc")`, expected 201, actual 401, trace `2b235500-5f04-43b7-a742-8fb19a63f7ac`. BeforeEach meeting creation had succeeded. The storage/registration rollback hook is installed only after that begin, so this event neither proves nor disproves the intended orphan protection scenario; it is not an expected RED for orphan handling.

I independently followed the actual SessionSecurityFilter duplicate-cookie/missing-or-invalid-session branches and JdbcIdentitySessions query conditions: token shape/hash match, ACTIVE identity, matching generation, unrevoked session, issued_at and expiry against DB UTC time. DataAccessException already returns 503, so a generic database-unavailable-as-401 explanation is contradicted by current code. The V007 timestamp precision is DATETIME(6). Fixture tokens are actual 32-byte SecureRandom URL-base64 values; beforeEach creates a fresh actor/session and resets hooks. The concurrency tests join their futures/executors; the current 401 diagnostics return the original response without retry or assertion suppression. No parallel-test override or deterministic stale-token carryover was found in the inspected setup/configuration. This rules out several proposed explanations as unsupported; it does **not** prove a cause.

The failed evidence lacks the matching request cookie/header/session snapshot needed to distinguish the resolver's alternatives. The later 35/35 cohorts and one targeted diagnostic PASS are non-reproductions only. The diagnostic emitted no snapshot because it did not fail. There is no identifiable production source defect or test-masking change established by this evidence, and an arbitrary authentication patch or blind repeated cohort is not justified.

Keep the original incident and trace visible under the identity/fixture qualification owner. If it recurs, retain bounded non-secret request/session correlation and resolver reason evidence before changing behavior; then require an actual causal RED and focused regression. Do not close Q401 on the strength of subsequent green runs. Q401 is separate from the demonstrated R1 adoption blocker and remains open after any header fix.

## Evidence audit and actual checks

Fresh reviewer checks, performed without running database/storage suites:

1. Git full-range path set, each final worktree file vs exact HEAD blob, byte count and SHA: 181/181.
2. Both SHA256SUMS manifests: 117 + 25 listed entries validate; including the manifests themselves this is 118 + 26 = 144 artifact paths. Initial 118 files also match original commit 5e7022c exactly.
3. All archive bytes decoded; all **55 XML** and **7 JSON** documents parsed. Each execution-index entry agrees with actual XML totals and testcase count; no XML skipped elements. Both size allowlists match their files, excluding only their own allowlist and SHA256SUMS as designed.
4. Reviewed failure/testcase details and scanned all **58 raw logs**, including successful/failed build markers, setup/compile/selector errors, child process readiness/identity rejection, migrations, contract response bodies and the retained 401. Inspected the wrapper/source manifests and selected runtime/provenance metadata. Hash records for 36 initial source paths, three correction source paths, 12 read-source inputs and ten SQL files match their relevant checkpoint/current inputs.
5. All **ten migration files** match BASE and root byte-for-byte; no SQL was added/changed. The final source set contains no POM or storage-algorithm rewrite.
6. Independent Python counted UTF-8 golden-vector program: **2 PASS**.
7. Pinned Node 22.23.4 / openapi-typescript 7.13.0 CLI `contracts/openapi/api.yaml --check -o web/src/shared/api/generated.ts`: independently captured exit **0**, no write. Generated SHA `a71071c73bbb89550fb24d07763f4acadb9db510fb40e7454ea0e29234998eb7` matches both archived before/after records.
8. Fresh narrow transport probe described under R1: exit 0 after four requests and owned server teardown; two controls succeeded and two long-name requests failed 500. No existing fixture was involved.
9. Final HEAD/status check: exact reviewed HEAD and clean owner tree.

Validated recorded primary IT accounting:

| Retained final run | Executions | Failures/errors/skips |
| --- | ---: | --- |
| managed-local-03 / upload | 35 | 0/0/0 |
| managed-s3-01 / upload | 35 | 0/0/0 |
| managed-local-restart-03 | 1 | 0/0/0 |
| managed-s3-restart-01 | 1 | 0/0/0 |
| managed-binding-final | 2 | 0/0/0 |
| config-http-final | 3 | 0/0/0 |
| config-race green-01 | 3 | 0/0/0 |
| **Primary total** | **80** | **0/0/0** |

The XML set has **44 distinct methods**, with 36 repeated per backend plus five original configuration methods and three race methods. The separate diagnostic is 1/1 and not folded in. Both final-unit archives have 13 suites/67 cases, not 134 distinct cases. These are verified **recorded author executions**, not reviewer reruns.

The invalid multi-selector runner log has only its argument error; its XML is byte-identical to the prior 35-case upload XML and execution-index correctly marks it non-executed. The source/HTTP/openapi REDs, intermediary compile errors, wrong initial token setup, same-DataSource binding fixture mistake, TLS RED and race RED remain preserved. Original initial report SHA `784439d049801e7ed9cb0dea224e0b749ad213459c4f6e6e0ce2186021cc0176` and initial manifest are retained in the separate correction archive. No failed evidence was normalized away.

Original manifest SHA: `9a13b6a6691ae100452c3f731e9629cad1ea8fa62b9bc84165cd943c7c21b317`. Correction manifest SHA: `4bd99a72a4dab5ca97ee7b62565ebdfc5336a5beb653dff7a29b6cf8c7ae76bb`.

## Limits and custody

The selected evidence consistently identifies newly owned MySQL fixtures `ama-test-b01-999352219d61:45333` and `ama-test-b01-ca05078d2c9e:46515` (2 CPU/1 GiB/256 PIDs, nativeAIO0, ten reviewed migrations through 011.1) and RustFS `ama-b04-java-44ef702b9e:47779` (2 CPU/2 GiB/256 PIDs). Configured port requests and actual dynamic ports are separately preserved. These are recorded observations; this reviewer did not query Docker/DB or re-attest live fixture state. No contradicting destructive/reset/repair/company-call path was found in the changed source or inspected evidence. This does not independently prove absence of every unrecorded historical action.

The reviewer did not read private environments/PKI/blob/DB contents and does not claim an independent scan against undisclosed credential values. The author's private-value scan remains an attributed historical claim. Delivered allowlists/source/log/XML contain no full Config.Env/private PKI/data dump.

No DB/Docker/fixture mutation/reset/restart/removal/repair, company/model/knowledge/mail/publish/fencing calls, source fix/commit, child reviewer, or native/S3 10,001-part rerun occurred. The only new writes are this report and the coordinator-authorized isolated header-probe directory. Initial failed `java --source /dev/stdin` execution stopped at illegal-seek before running a probe; the memory-only retry used a Linux memfd and succeeded. No environment or fixture modification was used to work around it.

Production purpose/reference owners and real B06 consumers remain future gates; D06 periodic revocation and D07 retention/deletion remain unimplemented here. Native RHEL/XFS/DRBD, power loss, two-node HA/fencing, company S3/TLS/certificates, actual credential rotation, production capacity and company identity integration remain separate qualifications. Existing infrastructure evidence was not re-counted as new business acceptance.

## Source coverage appendix

All 37 owned source/docs/generated paths were covered. Handwritten Java/config/contracts/tests/docs were fully read; generated.ts was checked against the source contract, its complete changed portion and independent exact regeneration.

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
- `backend/integration-tests/src/test/java/ama/storage/FileBackendCatalogIT.java`
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
