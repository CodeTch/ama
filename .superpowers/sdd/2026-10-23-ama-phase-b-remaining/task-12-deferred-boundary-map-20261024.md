# Task12 deferred boundary ownership map — 2026-10-24

Read-only preparation for T4SR-04/05/06 against fixed committed BASE `80c878f52c7b748efdcdb64e707d50cf410843b4`. All product, contract, test and command inputs came from `git show BASE:path`, never concurrent Task6 live files. W is `.superpowers/sdd/2026-10-23-ama-phase-b-remaining`. The pending register was read first from W; its BASE copy is pinned below. This is neither implementation nor independent review nor repeated Task12 web/design preflight. No issue is closed here.

Authority: `W/task-13-pending-issue-register-20261024.md:16–18,26` assigns04/05 to Task12 producer/read-projection and contract ownership,06 to contract tooling/Task13 triage. Exact original review is `artifacts/phase-b/b-remaining/task-04-review/task-04-review.md:48,50,52`. Its later `task-04-fix-01-review.md:6,40–43` leaves these minors deferred and retains composed-suite/environment limitations. Adopted Task4 completion is not reopened.

## T4SR-04 — limit overflow

Future owner: Task12 API producer/read-projection author; close only with scoped source review and fresh actual HTTP evidence. `backend/api/src/main/java/ama/api/ProcessingDiscoveryController.java:58–70` parses canonical nonnegative decimal into long then narrows via Math.toIntExact at59. Both2147483648 and9223372036854775807 parse as long and throw ArithmeticException on narrowing. `ProcessingErrors.java:36–41` maps IllegalArgumentException to400 INVALID_ARGUMENT but this arithmetic failure reaches catch-all503 PROCESSING_UNAVAILABLE through11–13. This is BASE source analysis, not a new executed HTTP result.

All call sites are GET under `/api/v1/meetings/{id}`:

| Suffix | Controller lines | JdbcProcessingDiscovery bounds/call lines | Contract parameter and400 response lines |
|---|---|---|---|
|`/uploads`|18–20|77–83, max50|`processing.yaml:933–942,1280–1317`; `files.yaml:76–78` delegates GET|
|`/uploads/{upload}/parts`|26–28|147–154, max500|`processing.yaml:952–961,1535–1575`|
|`/completed-files`|30–32|157–168, max50|`processing.yaml:933–942,1677–1704`|
|`/processing-inputs`|34–36|171–176, max50|`processing.yaml:933–942,1806–1833`|
|`/processing-tasks`|42–44|228–234, max50|`processing.yaml:933–942,2064–2091`|

The adapter `page(int,int)` at387–388 already rejects below1/above endpoint maximum. Minimal ownership is validation before narrowing or equally narrow invalid-argument translation, preserving50/500 maxima and20/100 defaults. Do not globally translate unrelated arithmetic faults into400.

Proposed future acceptance, with a valid owner/meeting and valid upload for parts: omitted uses20/100;1 and endpoint maximum50/500 return200;0,51/501,2147483647,2147483648,9223372036854775807 return400 INVALID_ARGUMENT on all five routes. Controls: empty, `-1`, `01`, nondecimal,9223372036854775808 and duplicate limit return400 (query multiplicity check53–56). The essential missing regression is2147483648 reaching400 instead of503. Capture actual HTTP response/code, not only parser exceptions. These are expected future cases, not claimed BASE passes.

Relevant existing lane: `backend/integration-tests/src/test/java/ama/storage/ProcessingIntakeHttpIT.java:191–216`, `discoveryRecoversActualReceiptsInputsAndTasksWithoutClientMemory`, exercises these routes and only checks uploads limit51 at214. HTTP helper126–134 uses real HttpClient against ephemeral loopback HTTP; setup47–62,85–108 uses MySQL, owned native/archive fixture and actual application/authentication; teardown825–853 records custody. It is actual HTTP evidence, not browser HTTPS qualification. Add the missing focused boundaries here or an equivalent real HTTP fixture; existing success does not prove overflow coverage.

## T4SR-05 — positive part size token

Future owner: Task12 contract author, with exact schema/generated consistency and real full-long response evidence. Receipt `contracts/openapi/processing.yaml:454–476` defines accepted.partSize int64 minimum0 at467–470 and token with zero-accepting pattern474/revision ceiling description475. Reference `contracts/openapi/files.yaml:441–465` has numeric minimum1 and token pattern `^[1-9][0-9]{0,18}$`, description1..9223372036854775807. Align receipt with that positive-size definition. Explicitly scope the numeric minimum discrepancy with this consistency correction; do not lower runtime part sizes to revision ceiling or alter revision tokens elsewhere.

Runtime chain: `FileConfiguration.java:58–76` and `ProcessingConfiguration.java:96–98` read `files.part-size` as Long, default8388608. `JdbcFileUploads.java:64–82` rejects only size<1; `JdbcProcessingDiscovery.java:35–50` rejects<=0. `FileContracts.java:33` emits begin token using Long.toString; discovery receipt86–94 uses Long.toString on the same configured suggestion. `processing.yaml:382` says this is current suggestion, not persisted original chunk size. No algorithm change is indicated for this minor.

Proposed evidence: positive1,8388608,9223372036854775806 and9223372036854775807 retain exact decimal strings in actual begin and receipt responses. The final value must neither inherit revision ceiling nor pass through a lossy JS number. Configuration0/negative is invalid; tokens `0`, negative, plus sign, leading zero, fraction, whitespace and empty are not canonical positive size tokens. Match files schema definition exactly. Its regex limits lexical shape, not signed-long numeric ceiling:9223372036854775808 and other19-digit values also match. Do not claim schema alone enforces maximum; distinguish described/runtime long domain unless an explicitly reviewed additional constraint is authored.

`ProcessingIntakeHttpIT.java:506–511`, `originalUploadBeginEmitsOpaquePartSizeToken`, proves ordinary begin numeric/token equality only;191–216 reads ordinary receipts without max assertions. Use a fixture-selected part-size before application startup to capture max on both endpoints; a technical suggestion does not require uploading Long.MAX_VALUE bytes. Historical `artifacts/phase-b/b-remaining/task-04-fix-01/http-schema.py:1–12` validates four retained actual HTTP objects against full receipt/completed-file schemas. `eligibility-schema.py:1–14` tests a different enum issue. These are historical patterns, not a current maximum-size lane; do not overwrite or relabel old evidence.

Generated consumer `web/src/shared/api/generated.ts:610–619` has correct begin comment;721–727 retains the receipt revision comment. `web/package.json:17–18` defines `npm --prefix web run generate:api`, then `npm --prefix web run check:api`; `web/scripts/check-api.mjs:5–13` regenerates to temp and compares bytes. Regenerate from final schema; do not hand-edit generated.ts. Generation/check does not itself prove runtime numeric or schema boundaries.

## T4SR-06 — duplicate response names and independent CLI banner

Future owner: Task12 contract/tooling window for naming triage; Task13 final report for retained warnings/banner. Historical `artifacts/phase-b/b-remaining/task-04/openapi-size-final-bundle.log:11–44` records files begin409/503 warnings renaming Conflict to Conflict-2 and Unavailable to Unavailable-2. These are response-object local names (CLI calls them schemas), with differing descriptions: files525–534 vs meetings287–292 for Conflict; files535–544 vs identity219–224 for Unavailable. Shared BusinessError shape does not make descriptions interchangeable.

If correcting names in the future owned window, update unambiguous response names and every reference, preserving HTTP/error semantics. File Conflict refs:51,147,220,285,386. File Unavailable refs:57,153,226,291,392. Root `api.yaml:30–39` includes file routes;40–57 includes discovery. Require no unresolved refs, fresh bundle and generated/check evidence; do not manually rename generated/bundled components. This map does not prescribe a new naming design or inventory all possible future warnings.

Separately historical log2–8 is CLI update notice. Root `package.json:9` pins Redocly2.66.1; notice advertises2.70.0 at that historical execution. It is an external tooling notice, not product/API defect or a reason to upgrade tooling in this preparation. Retain raw historical warnings/banner; no suppression, rewriting or pristine-output claim. A naming correction cannot promise banner disappearance.

## Current command lanes, future execution only

Relevant Java routing: `python3 tools/checks/run.py it 'ProcessingIntakeHttpIT#discoveryRecoversActualReceiptsInputsAndTasksWithoutClientMemory'`, plus the newly authored boundary method once it exists; token baseline selector `ProcessingIntakeHttpIT#originalUploadBeginEmitsOpaquePartSizeToken`. Runner52–75,147–153 discovers *IT.java, requires explicit jdbc:mysql environment, runs Maven `-Dit.test=... verify`; module `backend/integration-tests/pom.xml:9` binds Failsafe. `unit` discovers *Test.java and cannot prove this HTTP boundary. Runner execute13–16 deletes discovered reports: these are lane identifiers, not instructions to run over retained evidence. Use Task12 reviewed fresh-output custody and current resource/fixture guards first.

Contract `python3 tools/checks/run.py contract OpenApi` routes156–157 to77–84 and invokes `npm run check:openapi`. Root `package.json:7` chains `redocly lint contracts/openapi/api.yaml --config redocly.yaml` then `redocly bundle ... --output artifacts/openapi/api.yaml`. `redocly.yaml:1–4` enables struct/unresolved-ref/unused-component errors. The default bundle overwrites accumulated output: use Task12 already-owned reviewed fresh-output override/direct equivalent. No such command was executed here. Focused HTTP, exact schema, generated diff/check and fresh lint/bundle complement each other; no web/design preflight is repeated.

Limitations: no tests, generators, network/browser, DB, resources or private environment were accessed/executed. No product/test/HEAD/index writes. Only this new map and uniquely named manifest are written. No fix, test pass, review approval, activation or runtime qualification is claimed. Actual Tasks6/8/7/9/10/11 API/source repin remains mandatory at Task12 activation; this BASE-only map cannot substitute. Current Task6 live source and prospective schemas were not read. Original review line50 cites older receipt lines471–474; current BASE lines472–475 above govern this map.

## Fixed BASE evidence inventory

Companion `task-12-deferred-boundary-map-20261024-manifest.json` records full SHA256 in addition to whole BASE Git blob SHA1/bytes and 1-based inclusive excerpt ranges. Ranges identify cited excerpts; omitted lines are not claimed reviewed. Data came from git show with no checkout.

| BASE path | Git blob SHA1 | Bytes | Evidence lines |
|---|---|---:|---|
| `.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-13-pending-issue-register-20261024.md` | `2d962f6aba9d91c91c19a7a6aff7bdfe721753cd` | 4432 | 1–28 |
| `artifacts/phase-b/b-remaining/task-04-review/task-04-review.md` | `a010f8299cf17e6a0dc58962137aac44820db818` | 12990 | 46–52 |
| `artifacts/phase-b/b-remaining/task-04-review/task-04-fix-01-review.md` | `9a08286cad23e968ae1f4e2659fcb0fbadb96b1b` | 9279 | 3–7, 37–43 |
| `backend/api/src/main/java/ama/api/ProcessingDiscoveryController.java` | `e1accc7f3195a7058145131d0716215672b4fbc8` | 4195 | 9–48, 53–74 |
| `backend/api/src/main/java/ama/api/ProcessingErrors.java` | `95a136bc8fe1aed75a3e228fe8cc0b1e973235bd` | 2865 | 6–17, 36–41 |
| `backend/adapters/src/main/java/ama/transcript/JdbcProcessingDiscovery.java` | `989597cada2046607af9761a66021e4379c79d86` | 27820 | 35–50, 77–94, 146–176, 228–234, 384–391 |
| `backend/api/src/main/java/ama/api/ProcessingConfiguration.java` | `5abd7937a9f9a64990e09605b4506919c1854f6d` | 8384 | 96–98 |
| `backend/api/src/main/java/ama/api/FileConfiguration.java` | `3751a21da2205d5dfed8ffe9eed79cea7b618717` | 2998 | 58–76 |
| `backend/adapters/src/main/java/ama/storage/JdbcFileUploads.java` | `14b7b45f5eddb6ebd9ee244436c501940574f5f8` | 25015 | 64–82 |
| `backend/contracts/src/main/java/ama/contract/FileContracts.java` | `346645af88e0ea0761da07406cf000ede9c248d6` | 2534 | 25–35 |
| `backend/integration-tests/src/test/java/ama/storage/ProcessingIntakeHttpIT.java` | `3caf121fc821c2c7d2e5bc13e788e981b3a78e6b` | 66307 | 47–62, 85–108, 126–134, 191–216, 506–511, 825–853 |
| `backend/integration-tests/pom.xml` | `bf8be4908bdc08d556e28d88b6758725ae1686bd` | 1565 | 1–9 |
| `contracts/openapi/api.yaml` | `ceea478c6bd5518b6a3eeebe052ea85902522402` | 2390 | 30–57 |
| `contracts/openapi/processing.yaml` | `7a560d4611f60fe2ad10ccbf8a46af2b25d47c9b` | 59297 | 382, 454–476, 933–961, 1280–1317, 1535–1575, 1677–1704, 1806–1833, 2064–2091 |
| `contracts/openapi/files.yaml` | `a75b56f0a7949265ca347b7b06c5b6c7d492621c` | 13140 | 38–58, 76–78, 134–154, 207–227, 272–292, 373–393, 441–465, 525–544 |
| `contracts/openapi/meetings.yaml` | `6d0a422dc90e5d454e1391fda3c70cb22645fe42` | 10136 | 287–292 |
| `contracts/openapi/identity.yaml` | `c5da190152c7a1f0b2896a13750c151e47051c1d` | 6133 | 219–224 |
| `web/package.json` | `2d746c212806e2c7dd1d26d1679e3a1245d8cb43` | 975 | 11–19 |
| `web/scripts/check-api.mjs` | `8bf9a57af5a7635bee3dc320d595846206930b96` | 873 | 1–13 |
| `web/src/shared/api/generated.ts` | `f81228685650ed0bb4865049d44bd6d7c7b27b6d` | 100250 | 610–619, 721–727 |
| `package.json` | `2053504daf902fd38822e70bcde375393d92ff41` | 385 | 1–10 |
| `redocly.yaml` | `1bad279ba62cc6844cccb729d9bf8acac7bfddb2` | 81 | 1–4 |
| `tools/checks/run.py` | `16a750cee47604dcff7452b225a3e255352b63f4` | 9743 | 13–24, 46–84, 147–159 |
| `artifacts/phase-b/b-remaining/task-04/openapi-size-final-bundle.log` | `257d9e01468aed225e23e5c2599764a606e53d3a` | 1683 | 2–8, 10–47 |
| `artifacts/phase-b/b-remaining/task-04-fix-01/http-schema.py` | `4032e6c6eb0a6743ecb0f2b1db27dbffc11e9c16` | 958 | 1–12 |
| `artifacts/phase-b/b-remaining/task-04-fix-01/eligibility-schema.py` | `cd99a6e92e34b8d19f7ca8635889719eb566ae44` | 925 | 1–14 |
