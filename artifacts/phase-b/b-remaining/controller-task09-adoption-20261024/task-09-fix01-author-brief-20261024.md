# Task9 fix round 1/5 — same author, 2026-10-24

BASE: 0e22b812000f2efa00c2ef8ed66e06d8254beab5. Original Task9 brief and all safety/custody/private/runtime/resource constraints remain binding. This is one minimal fix wave for F1–F4, not a new design or full Task9 implementation pass. You remain the sole product writer and must not spawn agents. Coordinator owns central state/progress/rulings. Preserve every existing committed artifact and failed window.

Read the complete independent review `task-09-independent-review-20261024.md` and the original activation brief/report. Use receiving-code-review/systematic-debugging/TDD for the four actual defects. F1–F4 verbatim follow below.

## Binding coordinator rulings and scope

- F1: adopted R1 exact FULL scope is authoritative. FULL is required; no implicit legacy missing-scope compatibility. `expectedRevision` remains legacy revision representation, not permission to omit scope. Align wire/OpenAPI/idempotency normalization and genuine replay/staged-new/400 boundaries; initial-settings retains separate schema.
- F2: complete root identity and current exact membership are required. Preserve original root union/complete provenance and source-first issuance. Use supported genuine prepared cross-version roots for active and denied reconstruction; do not fabricate current pointers or call structural evidence Task10 business closure.
- F3: immutable original candidate parent must come from verified original persisted candidate graph for replay. New candidate declaration/current retention authority remains strict. Focused genuine graph/restore test may compare a later current observation, but do not SQL-invent Task10 adoption. Actual later save/adopt integration remains mandatory Task10 obligation.
- F4: finite store admission must precede unaccounted durable writes with crash-consistent accepted mappings. Bounded store test is sufficient; do not make thousands of fsyncs merely to reach cap. Keep exact restart/duplicate mapping validation.
- F5 is Minor and explicitly deferred to the final whole-branch review under SDD fix-loop; do not mix into this Important fix wave. It is a real extra-F design deviation, not declared compliant or a two-capture implementation. Final review must decide disposition.

## Verification and delivery

Add meaningful focused RED tests first for each defect, then minimal production corrections and fresh GREEN on amended source. Covering test targets should be existing `Task09MinutesHttpIT`/`Task09MinutesHttpBoundaryIT` (F1), confined recorded lookup/real source-first preparation tests (F2), candidate graph restore and corruption tests (F3), and controlled provider store admission/restart tests (F4); add a narrowly named test where existing file cannot exercise the actual defect. Choose affected inherited selectors based on amended paths. Batch compatible selectors on frozen source; do not repeat Task9 full matrix, all units, migration, formal runtime/admin installation, or protected old custody scans without a concrete new dependency.

All heavy windows serial, test JVM512Mi/2CPU; native/child64Mi/2CPU. Use the repaired collector with new XML and before/after hashes. No source changes during running windows. Preserve all failed/compile windows and exact source differences. Keep existing schema/resource/private owner; no reset/bootstrap or private value exposure. Scan new raw/staged delta against original six known values plus all actual provider.secret files, receipt in memory; retain prior complete scan qualification unchanged.

Append fix report to SAME `task-09-report.md` with four IDs, root causes, changes, covering test file names, exact command, actual output/result/source windows, inherited qualifications and remaining risks. Update own public report only as appropriate without rewriting old raw evidence. Commit only own exact fix source/tests/new evidence/report; no coordinator ledger/discovery files or private `.tools`. Return STATUS, commit, addressed IDs, actual focused test outcomes, evidence paths, limitations. Report must have tests+command+output before scoped reviewer can start. No claim adoption.

## Open findings verbatim


### F1 — The generation endpoint rejects the exact adopted request body

**Source:** `backend/api/src/main/java/ama/api/MinutesWire.java:6`; corroborating contract `contracts/openapi/minutes.yaml` (`MinutesGenerationCommand`); tests `backend/integration-tests/src/test/java/ama/storage/Task09MinutesHttpIT.java:7–10` and `Task09MinutesHttpBoundaryIT.java:7–9`.

R1 §5 defines the generation object exactly as `{expectedRevisionToken?,expectedRevision?,scope:"FULL"}`. `MinutesWire.command(..., false)` permits only the two revision fields. Any `scope`, including the required string `"FULL"`, throws `Unknown field` and produces HTTP400. Conversely, the parser and OpenAPI accept an object without scope. This occurs before accepted-receipt lookup, so a conforming client cannot even replay an already accepted generation while new work is deliberately unavailable. The final HTTP tests consistently send bodies without scope; their success demonstrates the divergent contract rather than compliance.

**Required correction:** accept and require the exact FULL scope on generation requests, retain strict duplicate/unknown-field rejection, align OpenAPI and semantic idempotency normalization. Initial-settings must retain its separate exact schema.

**Precise validation:** a conforming FULL body replays the same accepted task with 202; the same body on a genuinely unaccepted key reaches the staged 503; missing/nonstring/non-FULL scope and unknown fields reject with 400. Test token/legacy/equal-dual forms with FULL. No new Task10 installation is needed to demonstrate these boundaries.

### F2 — Recorded lookup rejects legitimate multiple original prompt/model versions

**Source:** `backend/adapters/src/main/java/ama/transcript/MinutesRecordedLookup.java:18–20`; upstream root derivation `backend/core/src/main/java/ama/minutes/contract/GenerationArchiveRefs.java:17–24`; installed call site `backend/worker/src/main/java/ama/worker/MinutesReconciliationHandler.java:7`.

R1 §§3.1/4/6.1 require all reached original generation roots, including multiple historical versions. `GenerationArchiveRefs` correctly walks PINNED source inputs and adds identities by `(artifactType, version)`. However, recorded lookup stores PROMPT_CONFIG and MODEL_CONFIG in `Map<ArtifactType,String>` and requires every `put` to return null. A valid root list containing current prompt P2 and historical prompt P1 therefore throws PROVENANCE_UNAVAILABLE, even though its ordering, identities and complete request hash are correct. The subsequent current-input check also assumes a single version per type.

This is not limited to a disabled actor: the genuine reconciliation handler unconditionally calls `prepareDeniedLookup` before choosing the authorized full-provenance path. The error occurs before the function can return Optional.empty. Consequently a supported cross-version PINNED request cannot recover through that handler after an uncertain send/restart, for either active or revoked ownership. A remote terminal result can remain locally unresolved and its permit unsettled. Appending defaults and replaying an older single-version input does not exercise a request whose original root union contains both versions.

**Required correction:** validate uniqueness and canonical ordering by complete root identity, retain all original refs, and require membership of the selected input's exact prompt/model versions rather than uniqueness by type. Preserve full original manifest/hash/requester/token/source checks and GET-only authority; do not reduce the root union to make this parser pass.

**Precise validation:** feed a genuine/supported prepared manifest with at least two prompt/model generations into the confined lookup reconstruction, with exact current-input membership. Cover active-owner fallback and fresh genuine denied-owner claim, plus duplicate same-identity/conflicting SHA, missing current version and altered-root hash rejection. Current-pointer workflow evidence remains staged to Task10/11; the root-list parser capability belongs to Task9 now.

### F3 — Terminal replay reconstructs an immutable candidate parent from the current meeting pointer

**Source:** `backend/adapters/src/main/java/ama/transcript/MinutesExternalPreparation.java:50`; `MinutesCandidateGraphPlan.java:17–20,40–49`; comparison `MinutesCandidateWriter.java:33–36`.

Every successful receipt, including terminal replay, calls `MinutesCandidateGraphPlan.prepare` with `graph.discovery().root().get("minutes_revision_id")`. For an already persisted candidate, `restore` restores its original IDs, timestamps and revision numbers, but does not restore its original `minutes_revision.parent_id`. Thus a candidate originally generated with parent null (or P0) is rebuilt with current saved parent P1 after a later saved/adopted graph becomes current. If P1 is not part of the original source closure, `prepare` fails immediately at line17; if it is present, the final full-row equality rejects the changed parent. Both outcomes prevent replay of an otherwise unchanged original result.

R1 §7 requires reuse and verification of the existing complete graph and the original predeclared parent. Later current state is separate from that immutable result. The three disposition fixtures explicitly preserve all meeting pointers and root revisions (`Task09RemainingDispositionFixture.java:8–9`), so their PASS supports disposition-enum handling but cannot establish parent stability after a genuine current-body change. This is a Task9 replay reconstruction defect, not a demand to fabricate Task10 adoption now.

**Required correction:** when an existing task/candidate is identified, derive the expected immutable parent from the original persisted candidate graph and validate that relation through the existing complete provenance proof. Do not make terminal graph equivalence depend on the latest current pointer. Keep new-output parent predeclaration and fresh current/retention authority checks distinct.

**Precise validation:** a focused graph/restore regression should reconstruct the same original parent despite a different current observation and still reject an actually corrupted original parent. The later real Task10 save/adopt integration must replay the same terminal result after current body changes, asserting no candidate/graph/task rewrite or resend. This review did not SQL-invent that future workflow.

### F4 — Rejected provider reservations leave unbounded durable request files

**Source:** `backend/adapters/src/main/java/ama/model/ControlledMinutesProviderStore.java:17–24`, particularly writes at21–22 before `journal.reserve` at23; admission checks `ControlledMinutesStore.java:46–49`.

The provider-facing store writes/fsyncs `requests/<newExecution>.json` and `envelopes/<newExecution>.json` before the journal checks logical-key uniqueness and MAX_EXECUTIONS. A correctly authenticated, structurally valid request with a fresh execution UUID and an already accepted logical key therefore creates two immutable files and then rejects. Repeating with fresh UUIDs adds arbitrarily many files while the journal execution count remains unchanged. The same leak happens to every otherwise valid new request after the journal reaches its 4096-execution cap. Startup validates journal entries only; these rejected files are not counted by that cap.

This is an authenticated controlled-protocol resource bug, not a claim of unauthenticated access or company deployment exposure. Per-request byte bounds and finite journal size do not bound persistent resources created before admission. It contradicts the finite controlled provider/resource boundary and can exhaust the owned store despite every rejected call returning an error.

**Required correction:** perform logical-key/capacity admission under the same store ownership before durable request/envelope creation, or use a bounded crash-consistent reservation protocol accounting for every staged file. Preserve the requirement that accepted journal entries always have independently verifiable exact request/envelope bytes; do not solve this by committing incomplete accepted entries.

**Precise validation:** after one accepted request, submit multiple valid conflicting requests with new execution UUIDs and assert rejection leaves request/envelope membership and bytes unchanged. Verify the capacity boundary likewise, and retain crash/restart/duplicate immutable-mapping checks. A small bounded store test is sufficient; no large disk-filling run is warranted.

