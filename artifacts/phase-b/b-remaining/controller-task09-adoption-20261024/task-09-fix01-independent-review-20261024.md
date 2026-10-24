# Task 09 fix01 independent scoped re-review — 2026-10-24

## Verdicts

- **F1 ADDRESSED. F2 ADDRESSED. F3 ADDRESSED. F4 ADDRESSED.**
- **SPEC compliance: PASS for the F1–F4 fix scope and the expressly adopted Task9 evidence boundary.** This does not assert unconditional whole-Task9 compliance: original F5 remains Minor OPEN for the explicit Task13 disposition.
- **Code quality: PASS for the reviewed fix scope.** No new blocking defect or other new finding was identified in this patch.
- **New findings: Critical 0 / Important 0 / Minor 0.** Remaining original findings: Critical 0 / Important 0 / Minor 1 (F5 only).

This is the same independent reviewer's scoped re-review, not a fresh whole-Task9 or whole-branch review. It neither adopts Task9 nor activates another task. Later workflow obligations below remain open.

## Scope and method

BASE `0e22b812000f2efa00c2ef8ed66e06d8254beab5`; HEAD `591545a4a1a4ce27edea54baa94e9d11666807b7`. Read the re-review prompt, original independent report, fix01 author brief, F2 source-first validation ruling, and the author report's fix appendix. Read all 2,166 lines of the fixed focused diff, recovering the small output-truncated custody section explicitly. Package: `task-09-fix01-review-package-20261024.diff`, 212143 bytes, supplied SHA-256 `5c1be4983227616011738c5d22a51ba8560068de2cce43990eb43e5ff1ef6027`. Its 55 paths contain all 22 changed current product/test/contract sources, new non-snapshot executable helpers, author reports and principal execution/custody evidence. The full 6,342-path package preserves historical source/XML/log snapshots; repeated snapshots were not reviewed as distinct current implementations.

No tests, builds, SQL, services, migrations, fixture mutations, secret reads, private scans, Git changes or product edits were performed. The sole new file is this report. No second reviewer or subagent was used. Independent mechanical fix01 custody was read and used within its declared limits rather than repeating its scans.

The only additional unchanged implementation context read was `backend/adapters/src/main/java/ama/transcript/JdbcMinutesGenerationCommands.java` and the `identity`/FULL-related lines of `JdbcMinutesGenerationReceipts.java`. Reason: F1 explicitly requires semantic idempotency consistency; the API fix must reach the existing exact FULL request digest. This was not a renewed review of F5 or other tasks.

## F1 — ADDRESSED: exact generation scope and receipt identity

`backend/api/src/main/java/ama/api/MinutesWire.java:6` now permits `scope` only for generation requests and requires a textual value exactly equal to `FULL`. Initial-settings still admits its own template field and rejects scope. Existing duplicate detection, unknown-field checks, revision normalization and transport bounds are retained. `contracts/openapi/minutes.yaml:181` onward now declares required scope with the single FULL enum. The unchanged receipt identity at `backend/adapters/src/main/java/ama/transcript/JdbcMinutesGenerationReceipts.java:18` already hashes the normalized revision and literal FULL, so equivalent token/legacy/equal-dual HTTP representations still address the same receipt.

`backend/api/src/test/java/ama/api/Task09Fix01WireTest.java:5–8` covers all revision representations, missing/null/numeric/non-FULL scope, duplicate/unknown fields and separation from initial-settings. Changed actual HTTP tests at `backend/integration-tests/src/test/java/ama/storage/Task09MinutesHttpIT.java:7–14` and `Task09MinutesHttpBoundaryIT.java:7–9` send conforming FULL bodies, assert the same accepted task on replay, preserve staged 503 for new work, reject invalid scope and exercise disabled-runtime replay and authorization boundaries. The focused HTTP and response-budget wrappers passed in the five passing cases of `integration-green-01`. These are accepted-receipt/boundary checks, not successful formal new-generation evidence.

## F2 — ADDRESSED: complete original root identities

`backend/adapters/src/main/java/ama/transcript/MinutesRecordedLookup.java:23–30` replaces one-version-per-type collection with a complete `Identity` set. Strict canonical type/version order and identity uniqueness reject duplicate identities, including conflicting hashes; presence of the selected generation input's prompt and model versions is checked by membership. The original complete roots sequence remains in the canonical envelope hash, and request hash, logical hash, business key and operation kind must still match. No root union is reduced to current defaults.

The production entry at `MinutesRecordedLookup.java:16` calls this same confined `reconstruct` method before the genuine authority transaction decides denied versus active fallback. Extraction did not substitute a test-only parser or create write authority. Existing actual capability/source/token/task/producer/requester and root authority checks remain outside or around the extracted function.

`backend/integration-tests/src/test/java/ama/storage/Task09Fix01RootsIT.java:5–6` derives a real candidate's reusable sources, appends genuine immutable prompt/model archives, constructs a prospective PINNED draft with `GenerationArchiveRefs` and `ConfigBindings`, and renders that binding without altering meeting pointers. `backend/integration-tests/src/test/java/ama/transcript/Task09Fix01LookupProbe.java:7–18` asserts at least two prompt and model versions, passes that source-derived manifest to the production parser, and rejects duplicate identities, conflicting SHA, missing selected prompt and altered roots against the original envelope. This source-first case passed after the recorded business RED.

Separately, `backend/integration-tests/src/test/java/ama/storage/Task09GenerationPhysicalCancelIT.java:13` proves a genuine active claim passes the same recorded validator and returns empty before the existing GET-only recovery applies/settles the original execution. The genuine denied-claim chain is exercised by `Task09Fix01DeniedLookupIT` and the fresh `denied-green-02` window. This is a composed proof: real dual-version source preparation plus the identical production parser, and real single-version active/denied durable chains. It is **not** a durable DB marker containing both versions, and not a completed Task10 workflow. Within the adopted Task9 boundary this directly closes the named parser defect; the later multi-version marker obligation remains explicit below.

## F3 — ADDRESSED: immutable original parent on terminal reconstruction

`backend/adapters/src/main/java/ama/transcript/MinutesCandidateGraphPlan.java:40–48` now distinguishes first receipt from an existing task candidate. For an existing candidate it checks unique selection, meeting, input, fingerprint and base revision; loads the original persisted minutes body; checks meeting, generation input, creator and AI origin; and selects that body's parent. A nonnull parent must exist in the captured graph, belong to the same meeting and precede the body's revision. The graph is then reconstructed and restored through the existing complete equality mechanism. The current meeting pointer is used only when no original candidate exists. `MinutesExternalPreparation.java:50` routes actual successful receipt processing through this method.

`backend/integration-tests/src/test/java/ama/storage/Task09Fix01ParentReplayIT.java:4` supplies a genuine terminal candidate and provider output. `backend/integration-tests/src/test/java/ama/transcript/Task09Fix01GraphProbe.java:5–8` changes only the detached current-body observation, proves identical candidate ID and complete reconstructed rows, and rejects a self-parent and a missing original parent. No detached graph is submitted as write authority and no DB pointer is manufactured. The original current-pointer dependency is therefore removed in production source and tested at the actual reconstruction boundary. The passing case does not claim a real later save/adopt lifecycle or exhaust every nonnull-parent workflow.

## F4 — ADDRESSED: admission before durable file creation

`backend/adapters/src/main/java/ama/model/ControlledMinutesProviderStore.java:25–29` performs journal admission and request/envelope directory-slot checks before any immutable request/envelope write. The synchronized provider operation and exclusive store ownership span this preflight and the subsequent journal reservation. `backend/adapters/src/main/java/ama/model/ControlledMinutesStore.java:43–57` validates request identity, existing exact mapping, logical-key conflict, execution cap and remaining journal capacity before payload creation; reserve reuses that admission. Accepted entries still follow durable exact request/envelope/payload creation, retaining restart validation rather than accepting an incomplete mapping.

`ControlledMinutesStore.java:134–135` counts every directory member, including unknown and crash-temporary files, with a finite ceiling. Startup checks the bounded payload/request/envelope directories, and new admission checks available slots. Rejected conflicts or full capacity no longer create fresh request/envelope files. Residuals consume capacity and are not silently deleted. The package-private reduced cap supports a small test; the public deployment cap remains 4096.

`backend/adapters/src/test/java/ama/config/json/Task09Fix01ProviderAdmissionTest.java:7–9` compares complete durable file membership/hashes across three fresh-UUID logical conflicts, rejects capacity overflow without change, reopens and verifies the accepted duplicate mapping, and verifies that an unjournaled crash-temporary member consumes the remaining slot without cleanup. The three new tests and four inherited envelope tests passed in the 11-test unit window alongside four wire tests; the separate eight lower-journal regression tests also passed. The fix does not weaken immutable verification to obtain admission success.

## Evidence accounting and retained failure

All paths in this section are under `artifacts/phase-b/b-remaining/task-09/fix01/` unless stated otherwise. Commands and actual outputs in the package support these claims; no success is inferred solely from author prose.

| Window / command | Actual result and qualification |
| --- | --- |
| `unit-red-01`: `python3 tools/checks/run.py unit Task09Fix01*Test` | Exit 1; 7 tests, 3 failures. Provider admission business RED; API module not reached. |
| `wire-red-01`: `unit Task09Fix01WireTest` | Exit 1; 4 tests, 2 failures. Scope business RED. |
| `provenance-red-01`: `it Task09Fix01*IT` | Exit 1; no fresh tests, accessor compilation failure. Not business RED. |
| `provenance-red-02`: `it Task09Fix01*IT` | Exit 1; 2 tests, 2 failures for roots and parent reconstruction. |
| `unit-green-01`: `unit Task09Fix01*Test` | Exit 0; 11 tests, 0 failures/errors. |
| `integration-green-01`: `it Task09Fix01*IT` | Exit 1; 6 tests: 5 PASS, 1 FAIL. The denied case's remote terminal fact was recorded, but final permit settlement assertion failed. This window remains failed. |
| `denied-green-02`: `it Task09Fix01DeniedLookupIT` | Fresh exit 0; 1 PASS after test-only bounded settlement wait and per-call evidence. Not relabeled as a single all-green six-case run. |
| `store-regression-01`: `unit Task09ControlledStoreTest` | Exit 0; 8 tests, 0 failures/errors. |
| `openapi-green-01`: `contract OpenApi` | Exit 0; two canonical contract checks in output, zero fresh XML tests. Stale XML excluded; existing three collision warnings retained. |

The original failed denied case remains the same task `fa172992-cbf0-4527-a638-ab6e4853b819`, execution `3ea4befa-a586-4170-aa2c-5923fd678ae4`, operation `da316d18-5827-4ca3-95e8-387d6935f2d9`, reservation `120e2368-6b9b-46eb-9e5f-f53df037580c`: task STALE, execution/operation SUCCEEDED, zero candidates, reservation initially unsettled. Its scan rank 7/102 excludes a simple eight-page insufficiency explanation. The later standalone call to the existing product settlement method returned true and zero unsettled for that original permit without GET, POST or business-row rewriting. The original-state, command, output and receipt custody is retained in seven explicitly hashed files. It demonstrates eventual settlement of that fact, not a retroactive PASS of the failed test.

The test change at `backend/integration-tests/src/test/java/ama/storage/Task09DeniedLookupProcess.java:26` adds a bounded ten-second wait with 25 ms pauses and per-call recovery/target-state records; it does not remove the final zero-unsettled assertion. The fresh run records initial one unsettled, recovery returns 0/0/0/1, final zero and child exit 0, using GET-only recovery. No product settlement algorithm changed. The precise cause of the earlier immediate-attempt failure remains unproven; a lock/interruption timing explanation is not promoted to a diagnosis.

Independent `task-09-fix01-mechanical-custody-20261024.md/.json` reports 9 windows, 14 fresh XML, 39 observations/25 distinct case keys, zero mechanical discrepancies, all 22 own sources matching, and five closure windows with the same 274 main paths. Only the denied-child test differs between the early green windows and final source. These are custody and source facts, not additional business cases. It confirms exact 6342 committed delta paths = 6341 pre-receipt staged paths + receipt, and seven original-fact hashes. Raw 6316 manifest entries were independently checked for path/size, not rehashed in full. New windows, owned sources and original facts were hashed. The scanner receipt reports eight unique known values, zero exact hits and zero PEM hits; this reviewer neither read those values nor repeated the scan.

## F5 and CannotVerify / future obligations

- **F5 remains Minor OPEN**, under the explicit Task13 disposition boundary. The extra standalone F before command F is unchanged. This report neither calls it fixed nor silently reconciles the original normative deviation.
- A real Task10 current PINNED/active-exclusion generation whose original durable request marker contains multiple prompt/model versions must later recover the same task/operation/execution/permit via GET-only active/denied reconciliation and settle without an extra POST. The present source-first/composed proof cannot substitute for it.
- A real later save/adopt must replay the original terminal candidate after the current body changes, without candidate/graph/task rewriting or resend. The detached current-observation regression closes the algorithmic defect, not that end-to-end obligation.
- Successful formal new HTTP mutations, Task11 publication/freeze and full actual LOCAL+S3 workflows remain assigned later. Runtime RUNNING or accepted-receipt replay does not close them.
- Original historical UNKNOWN cause, three excluded windows, old collector's limited suite custody, 11 missing before-images and the composed original 289+4 unit qualification remain unchanged. The fix windows are not a post-fix rerun of all 293 tests; overlapping inherited scenarios are not summed as new cases.

No remaining F1–F4 source defect or new fix-scope breakage was found. Adoption can use these scoped verdicts together with the explicit retained F5 and later workflow obligations; this report does not replace their owner decisions.
