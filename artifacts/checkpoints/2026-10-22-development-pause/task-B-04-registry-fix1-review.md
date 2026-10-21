# B04-R1 fix1 — independent scoped re-review

Date: 2026-10-22. Reviewer: `/root/b04_registry_review`, the original full reviewer.

**B04-R1: ADDRESSED. SPEC: PASS for the authorized fix1 scope. Technical quality: PASS for that scope. No newly introduced P0, P1, P2 or P3 findings.** This removes the demonstrated download-header blocker identified in the original full review. It does not resolve B04-Q401, qualify company/production deployments, or complete B06/D06/D07. Root adoption remains a separate action after reading this report.

## Exact authority and range

Reviewed the original finding and its evidence, the entire fix1 brief, the entire author fix1 report, all six changed source/test/contract/document/generated hunks, and every new evidence path. The exact single-commit correction is:

`d1e47ca69e68caa6bd51fee1706998b3e6cd2506..45dc8ed27449de191400c858b88413a518842f21`

Source tree: `/home/tanren/ama/.worktrees/phase-b-file-registry`. This is a scoped follow-up to the complete original `8da440d39e1b8e8dcdfc34722908d29b5aa3aa8a..d1e47ca69e68caa6bd51fee1706998b3e6cd2506` review, not a replacement for it. The original review remains byte-identical with SHA-256 `508a1aecf3f0205171b821ace2a837564b7ddba1a64bc972535607b317f70f0c`.

The fix1 brief authorizes a whole Content-Disposition value of at most 1024 printable ASCII bytes while preserving full durable/display names. It explicitly replaces the earlier unbounded full-header-name promise, without changing accepted input semantics. Thus shortening only the transport filename is consistent with current authority. No new product filename cap or connector-header enlargement is authorized or present.

Independently verified review input hashes:

| Input | SHA-256 |
| --- | --- |
| task-B-04-registry-fix1-brief.md | cd5055f67af55f3f30e8d2e863e008e0296dddbb08d6383ac50f3059f3edbb93 |
| task-B-04-registry-fix1-report.md | 55f0bcef30c5ce5502e3162369a42440a222226f73c2315bdf2f2e791f1f0e28 |
| task-B-04-registry-fix1-full.diff (903884 bytes) | 518e6df57e353e2201195d24f007b72e16fd48ea0da1e418f16bd268ee6e037d |
| task-B-04-registry-fix1-source.diff (35750 bytes) | 323b9fccc8a33fe341fd5b621237148599aa5206016e69d50fc5da4f50f4d074 |
| task-B-04-registry-fix1-delivery-paths.json | d7911a09ccc964b4e5036447a45d1b9ae109e14c77bca2927a061a60073fbfd6 |

## R1 correction and technical assessment

The original `FileController` emitted a 9050-byte Content-Disposition value for the accepted name `文` repeated 1000 times. Original actual controller/pinned Tomcat evidence shows full GET and Range both failing with HTTP500 and `HeadersTooLargeException`. The correction addresses that exact boundary, rather than merely making the old tests pass.

| Requirement / source coverage | Independent assessment |
| --- | --- |
| Whole-header budget — `backend/api/src/main/java/ama/api/FileController.java:23`, `:120`, `:136` | The fixed prefix is exactly 50 ASCII bytes, leaving 974 for the encoded extended filename. Constants and concatenation account for the whole value, including the ASCII fallback and UTF-8 declaration. Every appended output character is printable ASCII. Therefore Java character length equals header byte length here. |
| Exact fitting names — `FileController.java:160–169` | The algorithm only truncates when a complete next scalar would exceed 974. It does not reserve marker bytes in the successful complete-name case. Names using exactly 974 encoded bytes remain exact. The safe alphabet is unchanged; other UTF-8 bytes become uppercase percent triplets. |
| Overflow / scalar safety — `FileController.java:140–167` | Iteration advances by `Character.charCount(codePoint)` and encodes one complete scalar. `prefixWithMarker` tracks only fully appended scalar boundaries within 971 bytes. On first overflow, resetting to this boundary and appending three literal dots cannot split a surrogate pair, UTF-8 sequence or percent triplet. The output is a deterministic prefix, not a pathname. Valid-name precondition is supported by unchanged `backend/contracts/src/main/java/ama/contract/FileContracts.java:9–15,66–75`, which rejects unpaired surrogates before acceptance. |
| Bounded work / memory — `FileController.java:138–162` | No whole-name `getBytes` remains. Each iteration constructs at most one scalar's UTF-8 bytes and at most twelve encoded bytes. Since each scalar contributes at least one output byte, at most 975 scalar iterations occur before return for an overlong name; the suffix is neither scanned nor encoded by this helper. Builder, temporary encoding and final header allocation are bounded independently of the accepted name's length. |
| Metadata / protocol — `FileController.java:114–133` | Full display name is read only to derive a separate response header. No metadata mutation, upload identity, receipt, storage key or DTO change exists. Current registry authorization and backend stream acquisition precede header construction as before; status, body transfer, content length, Range and try-with-resources remain unchanged. |
| Actual HTTP regressions — `backend/integration-tests/src/test/java/ama/storage/FileRegistryUploadIT.java:932–1071` | Four new methods cover long full and range responses, normal Unicode exact preservation and five meaningful encoding boundaries. Tests perform actual authenticated begin/part/completion and actual content GET, compare both database display-name columns before/after, verify byte bodies and Range headers, and strictly decode the safe extended filename. Assertions cannot pass merely because the header is absent. |
| Genuine access and bytes — `FileRegistryUploadIT.java:57–104,129–145,416–521,964–1007` | Test composition retains actual identity/session/current-root access and a controlled registered INPUT_BATCH_FILE policy against real V011 batch/file/revision data. The test inserts a real business-reference row, then downloads through the production registry/controller. `actualBatch` constructs the required current rows; policy validates current revision, file/hash/meeting/original purpose. This is appropriate setup for a download regression, not a claim to re-test reference handoff or implement B06. `ManagedRegistryFixture` selects actual local HTTP or S3 adapter bytes. |
| Stream-close observation — `backend/integration-tests/src/test/java/ama/storage/RegistryObservedStorage.java:16,35–45` | Optional observer is consumed once at actual read. It wraps the actual InputStream, invokes `super.close()` before signaling, and returns the original body when unused. It supplies no content, permissions or DB results. The new helper waits for closure before its status/body/header assertions. Thus retained GREEN results include actual close completion, not merely invocation of a synthetic callback. |
| Consumer contract — `docs/acceptance/b/file-registry-integration.md:38` | Documents the whole 1024-byte transport budget, exact-fitting names, scalar-safe prefix plus literal marker, preserved full metadata and bounded early termination. It removes the materially incorrect unbounded header promise. |
| OpenAPI / generated client — `contracts/openapi/files.yaml:331,350,419`; `web/src/shared/api/generated.ts:403,1049,1058` | Source descriptions explain the same policy for full and partial content and distinguish stored name from transport representation. All other OpenAPI structure is unchanged after recursively removing description fields. TypeScript changes are comments only; ignoring comments and their resulting empty lines gives identical non-comment lines. Independent check-only regeneration exits 0. No DTO/schema/error union drift is introduced. |

No server configuration, POM, SQL, identity, native storage implementation, backend definition, durable completion algorithm, lock order or reference capability implementation changed in this commit. Their original full-review conclusions remain applicable, subject to the same recorded limitations.

## Causal regression evidence

Archive root, abbreviated below as `A`: `/home/tanren/ama/.worktrees/phase-b-file-registry/artifacts/phase-b/b04-registry-fix1`.

Parsed all 17 retained XML reports, matched each suite/count to `execution-index.json`, and inspected testcase failures, system output and relevant classpath/selector properties. Read all archive metadata and machine-inspected every raw log/XML byte; repetitive reactor/JVM-property content was parsed rather than represented as manually rereading each duplicated property. Original probe/report copies were compared byte-for-byte to their already-reviewed root originals.

All four IT XMLs record selector `FileRegistryUploadIT#downloadFilename*`, JDK 21.0.12.1, Tomcat 11.0.24 and Spring Boot 4.1.1 classpath entries. These runs exercise the actual server version associated with the original finding.

| Retained execution | XML results (tests/failures/errors/skips) | Evidence and interpretation |
| --- | --- | --- |
| red-local-01 | 4 / 3 / 0 / 0 | `A/red-local-01.log:266–611` records header-buffer exceptions, long Range500 and full500, normal-name200 and the 1025-byte boundary. Failure assertions match the intended statuses/budget, not compile/setup failure. |
| red-s3-01 | 4 / 3 / 0 / 0 | `A/red-s3-01.log:266–611` records the same failure modes with actual S3 bytes. Final test/observer source hashes match this RED snapshot; controller hash matches the original baseline. |
| green-local-01 | 4 / 0 / 0 / 0 | `A/green-local-01.log:268–280` contains eight actual successful download records and the clean selected-method result; BUILD SUCCESS at line 296. |
| green-s3-01 | 4 / 0 / 0 / 0 | `A/green-s3-01.log:262–274` contains eight actual successful download records and the clean selected-method result; BUILD SUCCESS at line 290. |
| green-unit-reports (13 suites) | 67 / 0 / 0 / 0 | Same unit set runs before each of the four canonical invocations. There are 67 distinct retained unit cases, not 268 distinct cases. These units do not replace the actual HTTP regression. |

Four distinct IT methods run on each backend: eight GREEN method executions and **16 actual GREEN downloads**. Each RED run reaches six download responses before the failing boundary method stops. No retries or status suppression appear in the new tests.

For each backend, the two long-name requests now return 200/206 with 1016-byte header values; the full body is exact `abcdef` (6 bytes) and Range is exact `bcd` (3 bytes), Content-Range `bytes 1-3/6`. The normal name includes Chinese, emoji, quotes, slash, percent, space and CR/LF; its 105-byte ASCII header strictly decodes to the original name. Both upload_session and file_object retain exact full names before and after each download. The logged metadata/close claims are backed by assertions preceding those log statements, and subsequent exact-byte/header assertions also pass in the GREEN XMLs.

Boundary cases are substantive: 974 ASCII characters fit exactly (1024 total); 962 ASCII plus emoji fit exactly (1024); 963 ASCII plus emoji truncates before the emoji (1016); 974 ASCII plus emoji trims the earlier prefix to 971 ASCII plus marker (1024); 108 Chinese plus emoji trims to 107 Chinese plus marker (1016). Strict decoding plus expected scalar-string equality detects broken Unicode boundaries independently of the encoding implementation.

One bounded provenance limitation is retained: the RED-local test-file hash `dd339c7fc51f60b0a08e3dcea218d6080073bdf5b69a5e42493fe87dbfac13d0` differs from the final test source. The archive provides its hash, not that source preimage, so I do not assert that RED-local used byte-identical final tests or speculate about the difference. Both RED controller hashes independently match the baseline (`4a63a06b216ea57f37032f129627a54a2366d0f2b9728ba6cd0ba12ab564f924`); RED-S3's test and observer hashes exactly match final source, and both GREEN records match the final assertions. This limitation does not invalidate the exact final-test causal RED on S3, the original independent controller/Tomcat reproduction, or the local GREEN acceptance. It is not an additional blocking finding.

`A/openapi-01.log` retains successful validation/bundling/generation and the typecheck invocation. Its two bundler warnings rename colliding Conflict/Unavailable component names; these unchanged schema portions are outside this correction. I independently established description-only structure and generated check success instead of treating a terse archived log as the sole source of truth. No broad additional suite is needed for these documentation-only changes.

## Custody and actual fresh reviewer checks

Performed read-only Git/Python/XML/source checks under WSL Ubuntu-24.04, invoked from the Windows host. Applicable review/verification discipline was retained: inspect source and actual evidence before making a completion claim; no child reviewer was spawned.

1. `git rev-parse HEAD`, `git rev-list --count BASE..HEAD`, `git status --porcelain`: exact HEAD above, one commit, clean checkout. Changed-path set exactly matches the 52-entry delivery map.
2. For every changed path, read working bytes and `git show HEAD:path`; exact equality, declared size and SHA-256 all pass. Total **1,007,100 bytes**: six existing source paths plus 46 new archive paths, all covered in the appendix.
3. Verified every one of the 45 SHA256SUMS entries; the seal itself hashes to `68dafc71a09625efda4bd8b87d00052bf57691363378c283933bc406b69aded8`. The 44-entry allowlist matches every archive file except itself and the seal, with exact sizes. `.gitattributes` is `** -text`.
4. Independently compared every one of the 181 `start-181-paths.json` entries to exact baseline Git bytes/sizes/digests. Exactly the six reviewed source paths differ at final HEAD; the other 175 are unchanged, including all **144 original artifact files** and their original manifests. Original source byte forms remain in the baseline commit.
5. All seven `review-inputs-sha256.json` inputs match root originals and their archive copies byte-for-byte, including original full report, original full review, fix1 brief and all four original probe files. Original full report remains `20e0f2ed68670e4605026917fbe0c82424642d7a8d77ca637a3e54176d8147bb`; original review remains `508a1aecf3f0205171b821ace2a837564b7ddba1a64bc972535607b317f70f0c`.
6. Verified six final source seals and generated.sha256 against actual bytes. Compared RED controller hashes to exact baseline; compared RED-S3 test/observer and RED-local observer to final bytes. The RED-local test preimage limitation is expressly described above.
7. Parsed every JSON manifest/check/index, all 17 XML reports and all raw log text; matched actual method names/failures/counts/transport records to source assertions. Four IT XMLs have 6,6,8,8 transport records respectively and no observed 401 in those selected runs. This latter observation does not resolve the separate incident.
8. Independently parsed OpenAPI JSON before/after with descriptions removed: equal. Compared generated TypeScript with comments and empty comment-only lines removed: equal. Initial literal post-comment byte comparison noticed the added empty line left by the new header comment; the line-aware comparison and actual diff show no code change.
9. Ran `/home/tanren/ama/.worktrees/phase-a/.tools/node-v22.23.4-linux-x64/bin/node /home/tanren/ama/.worktrees/phase-a-web/web/node_modules/openapi-typescript/bin/cli.js contracts/openapi/api.yaml --check -o web/src/shared/api/generated.ts` from the author tree. Actual exit **0**, output `openapi-typescript 7.13.0`; no regeneration write.
10. Ran `git diff --check d1e47ca69e68caa6bd51fee1706998b3e6cd2506 45dc8ed27449de191400c858b88413a518842f21 -- backend contracts docs web`: exit **0**, no output. Raw archived whitespace remains preserved.

I did not run DB/storage/HTTP services, repeat the selected ITs, reset/restart/remove fixtures, read private environment/PKI, run company/model/mail/fencing/publish calls, or change repository source/branches. The author's credential-value comparison in `secret-scan.txt` is retained as an author check; I did not independently repeat it against private secrets. No fresh native 10001-part, full 35-case cohort, JVM-death, platform or production qualification is claimed. Existing causal source/evidence is sufficient for this bounded fix.

## Q401 and remaining scope

**B04-Q401 stays UNKNOWN / unresolved qualification uncertainty.** The original preserved `artifacts/phase-b/b04-registry/managed-local-01.log:278–289` incident remains: `orphanIsNotReadableAsBusinessFile`, first upload begin, expected201/actual401, trace `2b235500-5f04-43b7-a742-8fb19a63f7ac`, after successful setup/meeting creation and before the rollback hook/storage operation. The original review's causal assessment is unchanged: retained evidence cannot identify the cookie/session/resolver condition, and subsequent successful authentication does not establish a fix. No identity changes occurred here; no authentication patch or ceremonial repeat cohort is demanded on speculation.

If it recurs, retain bounded non-secret request/session correlation and resolver reason evidence before changing behavior. The tracked uncertainty must remain visible to the qualification owner after R1 closure. Company/provider acceptance, HA/fencing/capacity, B06 production policy integration, D06 in-stream revocation and D07 deletion remain outside this review and delivery. No broader adoption or production-readiness claim follows from this scoped PASS.

## Complete delivery coverage / SHA-256

Every row below was independently compared with its final Git blob and delivery-map size/hash. Source rows were reviewed by complete changed hunks and surrounding relevant flow; evidence rows were audited as described above. Paths are relative to the author tree.

| Kind | Path | Bytes | SHA-256 |
| --- | --- | ---: | --- |
| artifact | `artifacts/phase-b/b04-registry-fix1/.gitattributes` | 9 | 48747480eddba024928d03be9bf83dc579e5515768647507a767e8187a85c9ab |
| artifact | `artifacts/phase-b/b04-registry-fix1/SHA256SUMS` | 4732 | 68dafc71a09625efda4bd8b87d00052bf57691363378c283933bc406b69aded8 |
| artifact | `artifacts/phase-b/b04-registry-fix1/artifact-allowlist.json` | 3600 | b8e17c519be4ad66467263fabb9f35df4158d6dbecd781789159827a86c51ef6 |
| artifact | `artifacts/phase-b/b04-registry-fix1/commands.json` | 2361 | 0f4ffa966b7ade1a4f8b33fd1c5c9834a003ac8e52e7797a2d83c03a62a17bd9 |
| artifact | `artifacts/phase-b/b04-registry-fix1/contract-scope-check.json` | 279 | 24610861ee5fb1694417cc287c90938a15e83e5a1d8916efef41d1e769d01218 |
| artifact | `artifacts/phase-b/b04-registry-fix1/execution-index.json` | 3375 | f435734d1d849cf2c6247b090dd94309f519621464849ee1822ea921f03b9fcd |
| artifact | `artifacts/phase-b/b04-registry-fix1/generated-check.log` | 30 | 2fc64bac02ac4cf39207db106f663d18a74456f7927f87f437c2d041aa095a98 |
| artifact | `artifacts/phase-b/b04-registry-fix1/generated.sha256` | 98 | 22a3998c418670984c3c46649f847cde662cc79ac95d0ffb9e27df6e1ac92b8d |
| artifact | `artifacts/phase-b/b04-registry-fix1/green-local-01.log` | 18401 | f1d9d6820573614fb9299aaed6dc17fa0f42fcdf0644d9047c4c391e28b9b91d |
| artifact | `artifacts/phase-b/b04-registry-fix1/green-local-01/TEST-ama.storage.FileRegistryUploadIT.xml` | 35198 | 28abf3915e2d2e9ca90b517140f2c21d26c0bb2c8d95b550975683be229dacc2 |
| artifact | `artifacts/phase-b/b04-registry-fix1/green-s3-01.log` | 17586 | 84153dd1f594f4eed77572ca7b9f1ae1e8d0e0ca24387f4b4af2aa004f0f93c2 |
| artifact | `artifacts/phase-b/b04-registry-fix1/green-s3-01/TEST-ama.storage.FileRegistryUploadIT.xml` | 34830 | b3bb67a928b89f325b37f235ea6e2f720c8123e6b3b0b52f1fa1d156e1c71d91 |
| artifact | `artifacts/phase-b/b04-registry-fix1/green-unit-reports/TEST-ama.api.DeploymentConfigTest.xml` | 32719 | 39b0b74d9a4cbe64e8f8edf295a68754717b09aac534aa1bff5b0c6800b5bb31 |
| artifact | `artifacts/phase-b/b04-registry-fix1/green-unit-reports/TEST-ama.api.FileConfigurationTest.xml` | 31791 | f44c51b4cdbe499af5e7aa6d35963b61aa2d177ca32d3d3669c26355055b419c |
| artifact | `artifacts/phase-b/b04-registry-fix1/green-unit-reports/TEST-ama.api.IdentityContractsTest.xml` | 32005 | d7790cb7bb7ac3cb00b402074703ab64206bd836e844edb4dff471c41c53da5d |
| artifact | `artifacts/phase-b/b04-registry-fix1/green-unit-reports/TEST-ama.api.IdentitySecurityTest.xml` | 32401 | fd83ccc133ce7e8ed5e3d1bb89da27f253f04f52f95d735a21c7cc72a95f6d13 |
| artifact | `artifacts/phase-b/b04-registry-fix1/green-unit-reports/TEST-ama.storage.ContractFixtureTest.xml` | 31881 | 36ed9e4b8b6d929840190ecabbb0f4af063cf47a5de3f2b9cd3bcaf5041e5484 |
| artifact | `artifacts/phase-b/b04-registry-fix1/green-unit-reports/TEST-ama.storage.ManagedBackendIdentityTest.xml` | 15815 | 0c0c0c1eea392ba66b63663806db914e5ca9881dde8a6bd9158105dc8ef3d5f3 |
| artifact | `artifacts/phase-b/b04-registry-fix1/green-unit-reports/TEST-ama.storage.StorageRouterTest.xml` | 16269 | c7671810da1e57375c710b62cb5071e1493c2e2a4b431cb2f834714adc332770 |
| artifact | `artifacts/phase-b/b04-registry-fix1/green-unit-reports/TEST-ama.storage.s3.S3LayoutV3Test.xml` | 15962 | 1b4c4e2b35049da072f969a6ea6bf150620906656c77109171de500aec807dca |
| artifact | `artifacts/phase-b/b04-registry-fix1/green-unit-reports/TEST-ama.storage.s3.S3ProtocolTest.xml` | 15831 | bc9edaedc6bd60f08918df858f0549c6874679c5bff7ed039394465d6af65d19 |
| artifact | `artifacts/phase-b/b04-registry-fix1/green-unit-reports/TEST-ama.support.ContractClientTest.xml` | 32051 | f309343ada1aa082dacfb4ed6eda5fb4a5d7144849c8fd078b37279da588e921 |
| artifact | `artifacts/phase-b/b04-registry-fix1/green-unit-reports/TEST-ama.task.TaskMetadataTest.xml` | 15506 | c12d18479494e892cf8290a7c312e3e532b2a212397124de5bf43473c9383866 |
| artifact | `artifacts/phase-b/b04-registry-fix1/green-unit-reports/TEST-ama.transcript.TextTranscriptParserTest.xml` | 17931 | 1e72227b5bc3aa970d6b2e05f335fd86eef97db84dfe0b002604e556f056725e |
| artifact | `artifacts/phase-b/b04-registry-fix1/green-unit-reports/TEST-ama.worker.WorkerLoopTest.xml` | 7238 | deecc8b415b2ebbc5b230f5673ccf340f0f3aecf71387ea51bc7a4de5b600962 |
| artifact | `artifacts/phase-b/b04-registry-fix1/header-policy.md` | 3189 | 51bb078213b65db13201f7ad6e7c8ac1fc401397edfef2e7ee4f0634568c833d |
| artifact | `artifacts/phase-b/b04-registry-fix1/openapi-01.log` | 1547 | 22377d6d3bbd7c0c30587ac57eace1256b7f5b85ed16ca4160fbdcb7e686dd84 |
| artifact | `artifacts/phase-b/b04-registry-fix1/owned-source-sha256.txt` | 717 | e939b0be595bf456f29a22fd9a9f061eede8069fe3a9d1ae8a6cb207c8409a29 |
| artifact | `artifacts/phase-b/b04-registry-fix1/preservation-check.json` | 724 | 5b05dc881267c9b6315b2e2fedbeccd9c1f54fefee3b676831ecb706eae93a5d |
| artifact | `artifacts/phase-b/b04-registry-fix1/red-local-01.log` | 70276 | b9672990c4ac8c3df900ef2a2a2b3f13d65a4ad0c47ff4eeaa93ad3dda3241c5 |
| artifact | `artifacts/phase-b/b04-registry-fix1/red-local-01/TEST-ama.storage.FileRegistryUploadIT.xml` | 86077 | 6b4ae61eec18ef0dbebafe8b22bb5193b10330561c7de6829615c80f6c8c4dc2 |
| artifact | `artifacts/phase-b/b04-registry-fix1/red-local-source-sha256.txt` | 411 | 02730d4cac0220a94fe70b1b1ca1fc1bf933f3353b1bb33b772e939b6f45fe08 |
| artifact | `artifacts/phase-b/b04-registry-fix1/red-s3-01.log` | 70258 | f1b2a6a4e94a41b082635f384ec21bb6db66913fada9176b0dc06b9cba02fe5a |
| artifact | `artifacts/phase-b/b04-registry-fix1/red-s3-01/TEST-ama.storage.FileRegistryUploadIT.xml` | 85717 | b2b0c55c7c9ebfc227690f88567cea89f52d7cbbe4854d82a741ab96b1b47b8a |
| artifact | `artifacts/phase-b/b04-registry-fix1/red-s3-source-sha256.txt` | 411 | 9d213f5d09aa332c083b57666b5ad089789cc17ee94013f634f6bb71845a1d30 |
| artifact | `artifacts/phase-b/b04-registry-fix1/review-inputs-sha256.json` | 1666 | f9dda427555b9861d9950d00a1fdbce97d316432b5f9c4e8b7b267cca6912aa0 |
| artifact | `artifacts/phase-b/b04-registry-fix1/review-inputs/b04-header-review-probe/HeaderReviewProbe.java` | 3862 | efc11e2849134afeb9c921cc9e1e4fe98571710a9acc7e3abbd0b1d670d16a34 |
| artifact | `artifacts/phase-b/b04-registry-fix1/review-inputs/b04-header-review-probe/evidence-manifest.json` | 1371 | a1e029b32e46eee062f74904ebcffd0b3c66ca37be0411125dd9b4269350a1dc |
| artifact | `artifacts/phase-b/b04-registry-fix1/review-inputs/b04-header-review-probe/invocation.json` | 13746 | 5c1ad38a14a6894e490ded9f677137a22dc5a1412c8a376316a20a6c8c63a886 |
| artifact | `artifacts/phase-b/b04-registry-fix1/review-inputs/b04-header-review-probe/probe.log` | 14154 | a5329df26c56d9ddc1d6b4a1d477ae89dd379eb01556baa6b9d892253f055878 |
| artifact | `artifacts/phase-b/b04-registry-fix1/review-inputs/task-B-04-registry-fix1-brief.md` | 4485 | cd5055f67af55f3f30e8d2e863e008e0296dddbb08d6383ac50f3059f3edbb93 |
| artifact | `artifacts/phase-b/b04-registry-fix1/review-inputs/task-B-04-registry-report.md` | 20920 | 20e0f2ed68670e4605026917fbe0c82424642d7a8d77ca637a3e54176d8147bb |
| artifact | `artifacts/phase-b/b04-registry-fix1/review-inputs/task-B-04-registry-review.md` | 23038 | 508a1aecf3f0205171b821ace2a837564b7ddba1a64bc972535607b317f70f0c |
| artifact | `artifacts/phase-b/b04-registry-fix1/secret-scan.txt` | 216 | 1cb3d8764807628cc15f288aa4fbe700241cc6fcb93814c090efb4a0c5de9c65 |
| artifact | `artifacts/phase-b/b04-registry-fix1/source-diff-check.txt` | 153 | 375212dc871cae7c073b2f9af41de4164083a4cfbbf7e06dd6c9ce3c0132b417 |
| artifact | `artifacts/phase-b/b04-registry-fix1/start-181-paths.json` | 36191 | 0e420bdf96e5500bee68657b42cb58ae4b8cb90c43990272dc8c6ebab142f83e |
| source | `backend/api/src/main/java/ama/api/FileController.java` | 10438 | 6070a0669c98d6cb40bb57923caaf847730bf51e63d051436aba88e9b1f99cd9 |
| source | `backend/integration-tests/src/test/java/ama/storage/FileRegistryUploadIT.java` | 66208 | 61a2352852299f22c30aac28d9e26180b653190114de14a25fa54d3000094761 |
| source | `backend/integration-tests/src/test/java/ama/storage/RegistryObservedStorage.java` | 2364 | cf20ebca75d2878c654357871ce04c5e184a1b8a383fc05181ced5f1ee9d5472 |
| source | `contracts/openapi/files.yaml` | 12788 | c7990e9b865562d916c63b21b34ebb4ac616e4b67473b638e94075d3d2ec07b5 |
| source | `docs/acceptance/b/file-registry-integration.md` | 16272 | 00553b228d7e5cfe6d6cbcfb8fd6acadec1a43b0de55a0d020c032b914f06c58 |
| source | `web/src/shared/api/generated.ts` | 42002 | 9fbc1f6b829333be0095f86f14b4c6bf5489c8d06be86a756547e6eb699df983 |
