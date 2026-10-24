# Task 4 fix 01 scoped independent re-review

## Spec Compliance

- **✅ SPEC: PASS within the fix scope.** T4SR-01, T4SR-02 and T4SR-03 are **ADDRESSED**.
- **Task quality: Approved within the fix scope.** No new Critical or Important issue identified in the four-file fix diff. Original Minor T4SR-04/05/06 remain explicitly deferred; this approval does not close them or replace the later whole-branch review.
- Review bounds: previous reviewed source `59c8b0801398123b1c0190ecbfeba8513f645abd` to fix HEAD `1ce4a2e6405fe380a5a7f7a18873d903c71528b7`. The controller's proof records that all four pre-fix source blobs at author base `f176382618924d9a00fd5fbabf93bbc30c78dd71` equal the previous reviewed blobs. Intervening separately owned documentation/API integration is retained in the full audit and excluded from this source fix review.
- Independently hashed bounded package: `task-04-fix-01-review-package.diff`, 50111 bytes, SHA256 `17b9221303b98b96bbf30d55268e9d5558bb4c20a8ca182c08042de92efe8100`.
- Independently hashed complete audit: `task-04-fix-01-review-package-audit.diff`, SHA256 `8e5b504a0f59b014ad75a31711e7fb7735d1a99b0a7e1ac020ed4fb6d321bdb9`. The bounded four-source package was read once; no broad review of unrelated intervening work was performed.

## Finding closure

### T4SR-01 — ADDRESSED

`backend/adapters/src/main/java/ama/transcript/JdbcProcessingIntake.java:66–105,133–144` passes the original normalized ImportRequest through the initial lookup, each permitted single recovery lookup, and final serialized replay. `:151–173` checks expectedRevision+1 and exact production type/pool/subject/input/priority/max-attempt shape, the immutable IMPORT/EMPTY/null/config/parser/schema columns, same-meeting batch/base/profile and member count.

`:176–202` reads the bounded saved canonical manifest and verifies exact input/batch/base/config associations, the complete original ordinal/file-ID/hash sequence, recomputed batch fingerprint, input fingerprint and canonical producer. This uses original immutable manifest metadata rather than current file/source tables or archive availability. `:212–220,225–241` then rechecks the complete input/manifest, batch, profile, members and task with current locked reads after fresh owner EDIT authorization; failures remain safe503. Hashing/strict parsing is confined to observe before the genuine meeting roots, and restore only compares the observed immutable graph. No current source eligibility, route, epoch, publication, readiness or archive prerequisite was added to accepted replay.

The new real HTTP regressions cover redirected result_id (`backend/integration-tests/src/test/java/ama/storage/ProcessingIntakeHttpIT.java:407–424`), member substitution/order/profile/batch fingerprint (`:425–455`), fixed input/manifest corruption (`:456–470`), and corrupt committed winner in the final claim branch (`:471–495`). They assert safe503, stable persisted counts, exact finally restoration and successful replay once the fixture corruption is removed. Existing historical/unavailable/lossless/lost-ACK/root-hash tests remain part of the final composed coverage.

### T4SR-02 — ADDRESSED

`contracts/openapi/processing.yaml:362–368` now includes JSON null in the nullable reason enum without removing any of the six allowed strings. The successful ELIGIBLE/null response therefore satisfies the published reason schema. `backend/integration-tests/src/test/java/ama/storage/ProcessingIntakeHttpIT.java:792–799` retains actual completed-file and upload-receipt responses for schema validation. The exact public fix report and root verification record seven representative objects, two invalid reasons, four actual HTTP response objects, final lint/bundle and repository generate/check API gates. Regeneration was byte-identical; no generated-source delta is missing from the fix package on that evidence.

### T4SR-03 — ADDRESSED

`backend/adapters/src/main/java/ama/transcript/JdbcProcessingDiscovery.java:59–61` derives exhausted from the fresh revision, excludes it from canSubmit and returns REVISION_EXHAUSTED for an otherwise writable exhausted owner meeting. The fix leaves execution NOT_INSTALLED/businessReady false and does not put this predicate before accepted replay. `backend/integration-tests/src/test/java/ama/storage/ProcessingIntakeHttpIT.java:227–250` checks capability at max-minus-one, max, and after an accepted increment reaches max; subsequent inherited legacy/token/equal-dual replay assertions preserve historical acknowledgment.

## Strengths and introduced-regression checks

- The replay correction uses one immutable proof shared by both replay branches instead of adding a separate current-source preparation path (`JdbcProcessingIntake.java:133–144,151–241`). It restores request binding while preserving the historical zero-current-source-query contract.
- The new corruption tests exercise persisted production acceptance and check business-row counts, rather than replacing intake or authorization with successful mocks (`ProcessingIntakeHttpIT.java:407–505`).
- Fixture time adjustment is confined by explicit method names to the four new positive integrity cases (`ProcessingIntakeHttpIT.java:107,112–120`). Production SessionPort/authentication is unchanged; inherited and negative authorization fixtures keep their original issuance. The diagnostic records boolean token/cookie checks rather than token values (`:134–139`). This does not qualify the environment clock cause.
- Named outside-diff risk: the new fixed replay parser/hash budgets might reject a supported 64-file historical input. Checked `backend/contracts/src/main/java/ama/contract/MinutesContracts.java:63–64`, `backend/core/src/main/java/ama/canonical/CanonicalValueCodec.java:45–68` and `backend/core/src/main/java/ama/transcript/BatchFingerprint.java:34–59,101–107`. CanonicalBudget's fifth parameter is maxContainerEntries, not aggregate text length; the fixed 4096 container/8192 node/262144 encoded-byte limits do not impose the suspected 4096-character restriction. REPLAY_READ preserves the 64-file/65536-byte manifest envelope (`JdbcProcessingIntake.java:33–35`). No budget regression finding follows from this check.
- Named outside-diff risk: observe relies on StrictProcessingManifestReader for some fixed shape/ordinal checks. Checked `backend/adapters/src/main/java/ama/config/json/StrictProcessingManifestReader.java:8–10` and `backend/core/src/main/java/ama/config/ConfigSchema.java:67–81`. They enforce IMPORT/EMPTY, null source/model/prompt, unique contiguous members, TRANSCRIPT batch, exact config/ref kinds, ordered profile slots and the single IMPORT_TXT step before the new fingerprint comparison. No permissive shape gap was identified in the fix.

## Verification evidence and limits

- Read the fix brief, prior review context, exact public fix report, `task-04-fix-01-rootproof.json`, and `artifacts/phase-b/b-remaining/task-04-fix-01/covering-exact-seal.json`. The controller independently verifies four source paths, 494 manifest entries, 499 filesystem/Git blobs, 451 source snapshot paths, preserved original report prefix and 590 unchanged original raw artifacts. This re-review relies on that custody proof rather than rerunning Git/source inventory checks.
- The final full run `covering-final-3` contains 24 HTTP + 2 unit cases with one authentication assertion failure and one native/TLS startup error. `covering-missing-2` reran exactly those two unchanged HTTP methods plus the same two units and passed. The exact-source union is **26 distinct passing cases**, not 30 and not one clean full HTTP invocation. No source bytes changed between those final labels according to the inspected seal/controller verification.
- **⚠️ Cannot independently establish environment root cause:** retained diagnostics show session issued_at approximately three seconds ahead of an immediate database_now observation. They do not establish a host-clock-step cause. The native/TLS startup exposed typed STORAGE_UNAVAILABLE; later certificate validity and the passing focused rerun do not determine that original cause. These retained observations do not demonstrate a new product regression in the four-file fix; they also do not grant production clock/TLS reliability qualification.
- **⚠️ Verification noise remains:** inherited Redocly Conflict/Unavailable rename warnings and update banners are retained and disclosed. T4SR-06 remains deferred. No pristine-log or single-clean-suite claim is made.
- No tests were run during this re-review merely to reconfirm the supplied exact-code evidence. No product, Git/index/HEAD, DB, resource, private environment or old evidence mutation; no subagents. The only review write is this report.

## Assessment

**SPEC PASS; task quality Approved for fix 01.** All three original Important findings are closed by the reviewed source and focused behavior evidence, with no new blocking regression identified. Root may adopt this scoped correction while retaining the original Minor ledger and the explicit composed-test/environment limitations. Task 5/6/7, actual Worker/RAW execution, browser/S3 main pipeline and full Phase B qualification remain outside this review.
