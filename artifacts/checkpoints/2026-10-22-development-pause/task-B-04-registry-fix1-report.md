# B04 registry fix round 1 report — B04-R1

Author correction complete; original independent reviewer re-review and Root adoption remain pending. This report supplements, and does not replace, task-B-04-registry-report.md. The sole actionable P2 finding was the actual pinned Tomcat response-header failure for an accepted long Unicode filename.

Worktree: `/home/tanren/ama/.worktrees/phase-b-file-registry`. Branch: `codex/phase-b-file-registry`.
Exact fix range: `d1e47ca69e68caa6bd51fee1706998b3e6cd2506..45dc8ed27449de191400c858b88413a518842f21` (one commit, `Bound download filename headers without truncating metadata`). Original implementation BASE remains `8da440d39e1b8e8dcdfc34722908d29b5aa3aa8a`; full eventual source range is BASE through this HEAD. Fix scope is exactly 52 paths: six existing source/test/contract/document/generated paths and 46 new fix1 evidence paths. Final worktree is clean.

## Correction and contract

`FileController` bounds the WHOLE Content-Disposition value to 1024 printable ASCII bytes. Existing fixed ASCII prefix is 50 bytes (`attachment; filename="download"; filename*=UTF-8` followed by two apostrophes); encoded filename budget is 974 bytes. Complete names fitting that budget retain the exact Unicode RFC5987 representation. On the first overflowing scalar, the encoder returns the longest already tracked complete-scalar prefix fitting the 971-byte marker budget, followed by literal `...`; ASCII fallback stays `download`.

Only one Unicode scalar is encoded at a time (at most four UTF-8 bytes or twelve percent-encoded ASCII bytes), with an early return before encoding the remaining suffix. No full unbounded UTF-8 copy is allocated. Percent triplets, UTF-8 sequences and surrogate pairs are not split. This is a transport budget, not a product name cap. Full stored/display names, upload receipt/idempotency fields, body/range/status/content-length semantics, authorization and stream ownership remain unchanged. Server/proxy header limits were not enlarged.

The handbook documents the transport prefix/marker and full durable name distinction. OpenAPI edits are descriptions only. Generated TypeScript edits are comments only; parsed OpenAPI with description keys removed and generated TypeScript with comments removed were compared to the start revision and are identical. `contract-scope-check.json` records that check. No DTO/type/schema change.

## Real RED → GREEN verification

All runs used the existing real authenticated `FileController`, pinned embedded Tomcat, actual MySQL identity/session/current-root/V011 data and controlled test-only INPUT_BATCH_FILE policy, with actual native local HTTP storage or Java S3/RustFS storage. No fake production grant or synthetic backend bytes were added. The optional test close observer wraps the actual read stream and signals only after its close returns; when unused, original streams are returned unchanged.

Exact selector: `FileRegistryUploadIT#downloadFilename*`, comprising:

- `downloadFilenameLongUnicodeFullPreservesMetadata`
- `downloadFilenameLongUnicodeRangePreservesMetadata`
- `downloadFilenameNormalUnicodeRemainsExact`
- `downloadFilenameScalarBoundaryIsSafe`

| Run | Backend | Methods | Failures | Errors/skips | Actual result |
|---|---|---:|---:|---:|---|
| red-local-01 | native local HTTP | 4 | 3 | 0/0 | long GET500 and Range500 with HeadersTooLargeException; 1025-byte boundary exceeds new assertion; normal Unicode passes |
| red-s3-01 | Java S3/RustFS | 4 | 3 | 0/0 | same genuine controller/header failures against unchanged production encoder |
| green-local-01 | native local HTTP | 4 | 0 | 0/0 | all focused cases pass |
| green-s3-01 | Java S3/RustFS | 4 | 0 | 0/0 | all focused cases pass |

Each GREEN run performs eight actual downloads: full and range for `文` repeated 1000 times, one ordinary Unicode case, and five boundary cases. Thus eight passing IT executions represent four distinct methods across two backends, and 16 actual GREEN downloads. Each RED run reached six actual downloads before assertions ended the failing methods. Logs preserve original failures and XML without replacement. `red-local-source-sha256.txt` and `red-s3-source-sha256.txt` identify the respective RED source bytes; production FileController was unchanged in both. Final source hashes are in `owned-source-sha256.txt`.

Each download checks exact original full display name in BOTH upload_session and file_object before and after HTTP, real backend stream closure, printable ASCII header <=1024, complete safe percent triplets, strict UTF-8 decoding, and expected scalar output. Full responses are 200 with exact `abcdef` and Content-Length 6. Range is 206 with exact `bcd`, Content-Length 3 and Content-Range `bytes 1-3/6`. Long-name headers are 1016 bytes. The ordinary name includes Chinese, emoji, quotes, slash, percent, spaces and CR/LF; its full decoded name is unchanged and its encoded header is 105 bytes. Boundary cases verify 974 ASCII exact (header1024), 962 ASCII+emoji exact (1024), 963 ASCII+emoji truncated before emoji (1016), 974 ASCII+emoji reduced to 971 ASCII+marker (1024), and 108 Chinese+emoji reduced to 107 Chinese+marker (1016).

Each canonical invocation also executes the SAME 67 unit cases. There were four overlapping unit executions, not 268 distinct cases. The final 13 unit-suite XMLs preserve 67 tests / 0 failures / 0 errors / 0 skips once. No full 35-case/backend, JVM death or unchanged native/S3 10001-part cohorts were rerun. `execution-index.json` records all saved suite counts.

## Actual commands

Run from the worktree, first sourcing `/home/tanren/ama/.worktrees/phase-a/.tools/env.sh`. Local RED and GREEN each source `.tools/mysql-fixture/ama-test-b01-999352219d61/env.sh`, then execute:

```sh
python3 tools/checks/run.py it FileRegistryUploadIT#downloadFilename*
```

S3 RED and GREEN each source `.tools/mysql-fixture/ama-test-b01-ca05078d2c9e/env.sh` and `.tools/s3-registry/env.sh`, set `AMA_STORAGE_CONTRACT=s3`, then execute the same selector. Exact per-run setup/command/log mapping is in `commands.json`; no private environment values are archived.

Affected contract checks passed, captured in `openapi-01.log` and `generated-check.log`:

```sh
npm run check:openapi
npm --prefix web run generate:api
npm --prefix web run typecheck
web/node_modules/.bin/openapi-typescript contracts/openapi/api.yaml --check -o web/src/shared/api/generated.ts
git diff --cached --check -- backend contracts docs web
```

The canonical checks used the pinned private tool environment; temporary read-only node_modules symlinks to phase-a were removed afterwards, without deleting their targets. No npm install or shared POM edits occurred.

## Preservation and evidence verification

New archive: `/home/tanren/ama/.worktrees/phase-b-file-registry/artifacts/phase-b/b04-registry-fix1`. Its `.gitattributes` is `** -text`; allowlist specifies exact retained paths/sizes, SHA256SUMS hashes all 45 other archive files. SHA256SUMS SHA-256: `68dafc71a09625efda4bd8b87d00052bf57691363378c283933bc406b69aded8`. The 46 new archive files include original raw review/probe copies plus actual RED/GREEN logs/XML and the bounded source/check metadata. All review source paths, sizes and digests are recorded in `review-inputs-sha256.json`.

The original 181-path checkpoint is indexed in `start-181-paths.json`; every indexed byte form was checked against start Git objects. Of those paths, 175 are unchanged in final working files; six source paths were intentionally edited, and their complete original bytes remain in start Git. All original 144 artifacts and both original manifests remain byte identical. Original full root report SHA-256 remains `20e0f2ed68670e4605026917fbe0c82424642d7a8d77ca637a3e54176d8147bb`. Fresh review SHA-256 remains `508a1aecf3f0205171b821ace2a837564b7ddba1a64bc972535607b317f70f0c`. Original archive manifest digests remain `9a13b6a6691ae100452c3f731e9629cad1ea8fa62b9bc84165cd943c7c21b317` and `4bd99a72a4dab5ca97ee7b62565ebdfc5336a5beb653dff7a29b6cf8c7ae76bb`.

After commit, read-only verification used `git cat-file --batch` for all 52 changed paths, compared exact blob bytes to final files, and passed. It verified all three SHA256SUMS manifests (117, 25 and 45 entries respectively), all six final source hashes, all 181 original checkpoint hashes/sizes, and the unchanged original root report. `git status --porcelain` was empty. These checks do not normalize raw line endings or remove failure text. Source whitespace checks exclude intentionally preserved raw historical logs.

Owned fixtures reused only: MySQL `ama-test-b01-999352219d61` (loopback45333) and `ama-test-b01-ca05078d2c9e` (46515), with existing authorized native AIO off; RustFS `ama-b04-java-44ef702b9e` (47779). Each test used new owned DB entities/local roots/buckets. No fixture reset, repair, removal or history cleanup. Existing private environments/PKI, roots/buckets and failures remain retained; teardown closes owned connections/server processes only. No credentials, private PKI, full Docker Config.Env, environment files, database dumps or stored blob content were copied. The exact owned three fixture environment PASSWORD/SECRET/ACCESS_KEY values were compared in memory against archive bytes without printing them; no match (`secret-scan.txt`).

## Remaining Q401 and platform limits

Q401 remains unresolved. Exact prior occurrence: `FileRegistryUploadIT.orphanIsNotReadableAsBusinessFile`, first begin call at POST `/api/v1/meetings/{id}/uploads`, expected201 actual401 UNAUTHENTICATED, trace `2b235500-5f04-43b7-a742-8fb19a63f7ac`. Identity/session setup and meeting creation had succeeded; failure preceded the rollback hook and storage bytes. Raw original evidence is `artifacts/phase-b/b04-registry/managed-local-01.log` lines278 onward and matching XML. It was an unexpected failure in a 25-case run. Retained evidence lacks the specific cookie/token, actor/session row snapshot and request headers; it cannot distinguish fixture/session transient from application behavior.

Earlier causal investigation inspected SessionSecurityFilter cookie/session resolution, JdbcIdentitySessions ACTIVE/generation/revocation/issued-at/expiry query, 32-byte fixture token construction, beforeEach resets and joined concurrent work. V007 issued_at is DATETIME(6), and no rounding mismatch or other cause was proven. No identity changes or retries were made. Earlier narrow diagnostic `python3 tools/checks/run.py it FileRegistryUploadIT#orphanIsNotReadableAsBusinessFile` passed once (1/0/0; `diagnostic-orphan-01.log` and XML) and emitted no 401 snapshot. That result, later green runs, and this fix1's successful authenticated requests establish only non-reproduction; none fixes or closes Q401. No new Q401 reproduction run was performed in this bounded correction.

Company-provider/end-to-end platform qualification, HA/fencing/capacity, B06 integration and D06/D07 completion remain outside this delivery exactly as in the unchanged full report. There is no company endpoint qualification claim. No SQL, POM, native storage algorithm, identity/SSO, company deployment, model/mail/publish, push, main merge or child-agent work. Original reviewer must inspect this complete fix range and evidence before R1 is closed and Root adopts it.

## Complete changed path list (52)

- `artifacts/phase-b/b04-registry-fix1/.gitattributes`
- `artifacts/phase-b/b04-registry-fix1/SHA256SUMS`
- `artifacts/phase-b/b04-registry-fix1/artifact-allowlist.json`
- `artifacts/phase-b/b04-registry-fix1/commands.json`
- `artifacts/phase-b/b04-registry-fix1/contract-scope-check.json`
- `artifacts/phase-b/b04-registry-fix1/execution-index.json`
- `artifacts/phase-b/b04-registry-fix1/generated-check.log`
- `artifacts/phase-b/b04-registry-fix1/generated.sha256`
- `artifacts/phase-b/b04-registry-fix1/green-local-01.log`
- `artifacts/phase-b/b04-registry-fix1/green-local-01/TEST-ama.storage.FileRegistryUploadIT.xml`
- `artifacts/phase-b/b04-registry-fix1/green-s3-01.log`
- `artifacts/phase-b/b04-registry-fix1/green-s3-01/TEST-ama.storage.FileRegistryUploadIT.xml`
- `artifacts/phase-b/b04-registry-fix1/green-unit-reports/TEST-ama.api.DeploymentConfigTest.xml`
- `artifacts/phase-b/b04-registry-fix1/green-unit-reports/TEST-ama.api.FileConfigurationTest.xml`
- `artifacts/phase-b/b04-registry-fix1/green-unit-reports/TEST-ama.api.IdentityContractsTest.xml`
- `artifacts/phase-b/b04-registry-fix1/green-unit-reports/TEST-ama.api.IdentitySecurityTest.xml`
- `artifacts/phase-b/b04-registry-fix1/green-unit-reports/TEST-ama.storage.ContractFixtureTest.xml`
- `artifacts/phase-b/b04-registry-fix1/green-unit-reports/TEST-ama.storage.ManagedBackendIdentityTest.xml`
- `artifacts/phase-b/b04-registry-fix1/green-unit-reports/TEST-ama.storage.StorageRouterTest.xml`
- `artifacts/phase-b/b04-registry-fix1/green-unit-reports/TEST-ama.storage.s3.S3LayoutV3Test.xml`
- `artifacts/phase-b/b04-registry-fix1/green-unit-reports/TEST-ama.storage.s3.S3ProtocolTest.xml`
- `artifacts/phase-b/b04-registry-fix1/green-unit-reports/TEST-ama.support.ContractClientTest.xml`
- `artifacts/phase-b/b04-registry-fix1/green-unit-reports/TEST-ama.task.TaskMetadataTest.xml`
- `artifacts/phase-b/b04-registry-fix1/green-unit-reports/TEST-ama.transcript.TextTranscriptParserTest.xml`
- `artifacts/phase-b/b04-registry-fix1/green-unit-reports/TEST-ama.worker.WorkerLoopTest.xml`
- `artifacts/phase-b/b04-registry-fix1/header-policy.md`
- `artifacts/phase-b/b04-registry-fix1/openapi-01.log`
- `artifacts/phase-b/b04-registry-fix1/owned-source-sha256.txt`
- `artifacts/phase-b/b04-registry-fix1/preservation-check.json`
- `artifacts/phase-b/b04-registry-fix1/red-local-01.log`
- `artifacts/phase-b/b04-registry-fix1/red-local-01/TEST-ama.storage.FileRegistryUploadIT.xml`
- `artifacts/phase-b/b04-registry-fix1/red-local-source-sha256.txt`
- `artifacts/phase-b/b04-registry-fix1/red-s3-01.log`
- `artifacts/phase-b/b04-registry-fix1/red-s3-01/TEST-ama.storage.FileRegistryUploadIT.xml`
- `artifacts/phase-b/b04-registry-fix1/red-s3-source-sha256.txt`
- `artifacts/phase-b/b04-registry-fix1/review-inputs-sha256.json`
- `artifacts/phase-b/b04-registry-fix1/review-inputs/b04-header-review-probe/HeaderReviewProbe.java`
- `artifacts/phase-b/b04-registry-fix1/review-inputs/b04-header-review-probe/evidence-manifest.json`
- `artifacts/phase-b/b04-registry-fix1/review-inputs/b04-header-review-probe/invocation.json`
- `artifacts/phase-b/b04-registry-fix1/review-inputs/b04-header-review-probe/probe.log`
- `artifacts/phase-b/b04-registry-fix1/review-inputs/task-B-04-registry-fix1-brief.md`
- `artifacts/phase-b/b04-registry-fix1/review-inputs/task-B-04-registry-report.md`
- `artifacts/phase-b/b04-registry-fix1/review-inputs/task-B-04-registry-review.md`
- `artifacts/phase-b/b04-registry-fix1/secret-scan.txt`
- `artifacts/phase-b/b04-registry-fix1/source-diff-check.txt`
- `artifacts/phase-b/b04-registry-fix1/start-181-paths.json`
- `backend/api/src/main/java/ama/api/FileController.java`
- `backend/integration-tests/src/test/java/ama/storage/FileRegistryUploadIT.java`
- `backend/integration-tests/src/test/java/ama/storage/RegistryObservedStorage.java`
- `contracts/openapi/files.yaml`
- `docs/acceptance/b/file-registry-integration.md`
- `web/src/shared/api/generated.ts`

## New archive raw SHA-256 manifest

```text
48747480eddba024928d03be9bf83dc579e5515768647507a767e8187a85c9ab  .gitattributes
b8e17c519be4ad66467263fabb9f35df4158d6dbecd781789159827a86c51ef6  artifact-allowlist.json
0f4ffa966b7ade1a4f8b33fd1c5c9834a003ac8e52e7797a2d83c03a62a17bd9  commands.json
24610861ee5fb1694417cc287c90938a15e83e5a1d8916efef41d1e769d01218  contract-scope-check.json
f435734d1d849cf2c6247b090dd94309f519621464849ee1822ea921f03b9fcd  execution-index.json
2fc64bac02ac4cf39207db106f663d18a74456f7927f87f437c2d041aa095a98  generated-check.log
22a3998c418670984c3c46649f847cde662cc79ac95d0ffb9e27df6e1ac92b8d  generated.sha256
28abf3915e2d2e9ca90b517140f2c21d26c0bb2c8d95b550975683be229dacc2  green-local-01/TEST-ama.storage.FileRegistryUploadIT.xml
f1d9d6820573614fb9299aaed6dc17fa0f42fcdf0644d9047c4c391e28b9b91d  green-local-01.log
b3bb67a928b89f325b37f235ea6e2f720c8123e6b3b0b52f1fa1d156e1c71d91  green-s3-01/TEST-ama.storage.FileRegistryUploadIT.xml
84153dd1f594f4eed77572ca7b9f1ae1e8d0e0ca24387f4b4af2aa004f0f93c2  green-s3-01.log
39b0b74d9a4cbe64e8f8edf295a68754717b09aac534aa1bff5b0c6800b5bb31  green-unit-reports/TEST-ama.api.DeploymentConfigTest.xml
f44c51b4cdbe499af5e7aa6d35963b61aa2d177ca32d3d3669c26355055b419c  green-unit-reports/TEST-ama.api.FileConfigurationTest.xml
d7790cb7bb7ac3cb00b402074703ab64206bd836e844edb4dff471c41c53da5d  green-unit-reports/TEST-ama.api.IdentityContractsTest.xml
fd83ccc133ce7e8ed5e3d1bb89da27f253f04f52f95d735a21c7cc72a95f6d13  green-unit-reports/TEST-ama.api.IdentitySecurityTest.xml
36ed9e4b8b6d929840190ecabbb0f4af063cf47a5de3f2b9cd3bcaf5041e5484  green-unit-reports/TEST-ama.storage.ContractFixtureTest.xml
0c0c0c1eea392ba66b63663806db914e5ca9881dde8a6bd9158105dc8ef3d5f3  green-unit-reports/TEST-ama.storage.ManagedBackendIdentityTest.xml
c7671810da1e57375c710b62cb5071e1493c2e2a4b431cb2f834714adc332770  green-unit-reports/TEST-ama.storage.StorageRouterTest.xml
1b4c4e2b35049da072f969a6ea6bf150620906656c77109171de500aec807dca  green-unit-reports/TEST-ama.storage.s3.S3LayoutV3Test.xml
bc9edaedc6bd60f08918df858f0549c6874679c5bff7ed039394465d6af65d19  green-unit-reports/TEST-ama.storage.s3.S3ProtocolTest.xml
f309343ada1aa082dacfb4ed6eda5fb4a5d7144849c8fd078b37279da588e921  green-unit-reports/TEST-ama.support.ContractClientTest.xml
c12d18479494e892cf8290a7c312e3e532b2a212397124de5bf43473c9383866  green-unit-reports/TEST-ama.task.TaskMetadataTest.xml
1e72227b5bc3aa970d6b2e05f335fd86eef97db84dfe0b002604e556f056725e  green-unit-reports/TEST-ama.transcript.TextTranscriptParserTest.xml
deecc8b415b2ebbc5b230f5673ccf340f0f3aecf71387ea51bc7a4de5b600962  green-unit-reports/TEST-ama.worker.WorkerLoopTest.xml
51bb078213b65db13201f7ad6e7c8ac1fc401397edfef2e7ee4f0634568c833d  header-policy.md
22377d6d3bbd7c0c30587ac57eace1256b7f5b85ed16ca4160fbdcb7e686dd84  openapi-01.log
e939b0be595bf456f29a22fd9a9f061eede8069fe3a9d1ae8a6cb207c8409a29  owned-source-sha256.txt
5b05dc881267c9b6315b2e2fedbeccd9c1f54fefee3b676831ecb706eae93a5d  preservation-check.json
6b4ae61eec18ef0dbebafe8b22bb5193b10330561c7de6829615c80f6c8c4dc2  red-local-01/TEST-ama.storage.FileRegistryUploadIT.xml
b9672990c4ac8c3df900ef2a2a2b3f13d65a4ad0c47ff4eeaa93ad3dda3241c5  red-local-01.log
02730d4cac0220a94fe70b1b1ca1fc1bf933f3353b1bb33b772e939b6f45fe08  red-local-source-sha256.txt
b2b0c55c7c9ebfc227690f88567cea89f52d7cbbe4854d82a741ab96b1b47b8a  red-s3-01/TEST-ama.storage.FileRegistryUploadIT.xml
f1b2a6a4e94a41b082635f384ec21bb6db66913fada9176b0dc06b9cba02fe5a  red-s3-01.log
9d213f5d09aa332c083b57666b5ad089789cc17ee94013f634f6bb71845a1d30  red-s3-source-sha256.txt
efc11e2849134afeb9c921cc9e1e4fe98571710a9acc7e3abbd0b1d670d16a34  review-inputs/b04-header-review-probe/HeaderReviewProbe.java
a1e029b32e46eee062f74904ebcffd0b3c66ca37be0411125dd9b4269350a1dc  review-inputs/b04-header-review-probe/evidence-manifest.json
5c1ad38a14a6894e490ded9f677137a22dc5a1412c8a376316a20a6c8c63a886  review-inputs/b04-header-review-probe/invocation.json
a5329df26c56d9ddc1d6b4a1d477ae89dd379eb01556baa6b9d892253f055878  review-inputs/b04-header-review-probe/probe.log
cd5055f67af55f3f30e8d2e863e008e0296dddbb08d6383ac50f3059f3edbb93  review-inputs/task-B-04-registry-fix1-brief.md
20e0f2ed68670e4605026917fbe0c82424642d7a8d77ca637a3e54176d8147bb  review-inputs/task-B-04-registry-report.md
508a1aecf3f0205171b821ace2a837564b7ddba1a64bc972535607b317f70f0c  review-inputs/task-B-04-registry-review.md
f9dda427555b9861d9950d00a1fdbce97d316432b5f9c4e8b7b267cca6912aa0  review-inputs-sha256.json
1cb3d8764807628cc15f288aa4fbe700241cc6fcb93814c090efb4a0c5de9c65  secret-scan.txt
375212dc871cae7c073b2f9af41de4164083a4cfbbf7e06dd6c9ce3c0132b417  source-diff-check.txt
0e420bdf96e5500bee68657b42cb58ae4b8cb90c43990272dc8c6ebab142f83e  start-181-paths.json
```
