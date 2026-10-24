# Task 09 independent task review — 2026-10-24

## Verdicts

- **SPEC compliance: FAIL — changes required.** F1, F2 and F3 contradict Task9-owned request/recovery contracts. F4 defeats the controlled provider's finite admission boundary. F5 is a smaller but explicit S/A/F design deviation.
- **Code quality: FAIL — changes required.** The recovery and durable-store defects are concrete correctness/resource failures. Passing single-version and unchanged-current-pointer tests do not cover them.
- **Unique findings: Critical 0 / Important 4 / Minor 1.** Findings are counted once across the two verdicts. No finding is based merely on the absence of Task10/11 workflows.

This is the single independent Task9 review, not the final whole-branch review. It does not adopt Task9 or activate Task10.

## Scope and method

Reviewed BASE `cff858e163ca7dea774dcf05c707ef7551956156` to HEAD `0e22b812000f2efa00c2ef8ed66e06d8254beab5`. Read the full fixed `task-09-independent-review-20261024-package.diff` in bounded segments, including all 154 current product/test/contract paths, six non-snapshot executable helpers and two reports. Package identity supplied by the manifest: 953608 bytes, SHA-256 `7d40ebbb5d012a9fed68e7b66d8da3fff9664bad1171825b068b05d681d4fae3`. Its full-path index contains 96298 entries. Historical duplicate source snapshots were treated as evidence versions, not separate current implementations.

Read the activation brief first and followed the task-reviewer prompt. Normative basis is `docs/superpowers/specs/2026-10-23-ama-phase-b-minutes-generation.md`, its adopted R1 and shared-rank ruling, and the adopted controlled-envelope supplement/wrapper. Also read the prepared review-context index and the concrete admission/cancellation, source-first composition, manifest-schema, denied-fact-lookup, initial-settings and PINNED/exclusion rulings. The controlled-envelope wrapper's coordinated adoption is not misrepresented as part of the author's commit.

Read the author W/public reports, final validation/source-closure/private-scan receipts and formal runtime result; used the independent initial/final mechanical custody results for custody rather than repeating their hash scans. No tests, builds, database queries, migrations, services, fixture operations, secret reads, Git mutations or source edits were performed. The only created artifact is this report.

Focused unchanged-context inspection was limited to `backend/adapters/src/main/java/ama/task/JdbcExternalOperations.java:48–58,129–138,230–248,359–382`. Reason: the changed execution-manifest metadata query joins/locks `durable_task`, so its actual callers had to be checked for task-before-operation/execution locking and current-read semantics. These callers already hold the corresponding task rows before the locking execution read. The final `lock=true` metadata read now uses `FOR UPDATE`, addressing the evidenced RR snapshot regression without assuming a nonlocking read was current. No broad re-review of Tasks1–8 was performed.

## Important findings

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

## Minor finding

### F5 — Enqueue performs a throwaway F capture before the serialized command F

**Source:** `backend/adapters/src/main/java/ama/transcript/JdbcMinutesGenerationCommands.java:15–18`; `JdbcMinutesGenerationPreparation.java:38–42,74–80`.

R1 §3.2 explicitly requires command F to reuse its serialized idempotency/auth gate and prohibits a throwaway third gate. Enqueue invokes public `preparation.prepare`, which captures S, computes A, then opens and commits its own full F at line42. The command subsequently claims idempotency, opens another fresh gate and repeats full `captureCurrent` in `insert`. The extra F contributes no write authority to the final command and charges/copies/locks the graph again on the same ledger. This makes the command's resource cost larger than the adopted two-capture protocol. Final revalidation remains present, so I do not characterize this as an authorization bypass or lost-write bug.

**Required correction:** expose a confined command preparation result after S/A and perform its sole successful F inside the command's serialized gate. Preserve standalone readers' S/A/F and final authorization precedence for deferred preparation failures, plus the bounded concurrent-winner replay rule.

**Precise validation:** a focused trace should show one S capture and one successful command F capture, including same-key concurrent replay and deferred named-failure precedence. Reuse already qualified primitive behavior where source-identical.

## Positive findings and reviewed boundaries

- The final source separates detached facts from genuine holder-bound write/retention authority. Private observation/client issuance, original full-spec comparison, genuine ExternalExecutionSession checks, one-shot marker/permit send and fact-only denied recovery are present. No permissive DTO or public boolean was accepted as domain-write authority in the reviewed paths.
- Controlled transport uses finite real socket deadlines and cancellation registration; cancellation runs outside the session monitor. UNKNOWN retains permit debt. Remote SUCCEEDED is separated from invalid/local-budget output rejection, and the original output limit is carried to send and lookup admission.
- The graph implementation performs bounded metadata discovery, identity-union acquisition, source/session/file/task closure, outside-root archive/parse/hash preparation and protected complete readback. Reviews and final selections use original/frozen memberships. The table-rank extension is explicit. This review's focused shared-call inspection found the final execution metadata current-lock fix consistent with its inspected task-lock callers.
- Candidate persistence compares complete graph rows/sets, gives action suggestions no accepted identity/date, classifies structural support conservatively, and exposes no automatic adoption. Disposition-aware replay no longer requires every terminal candidate to remain PENDING; F3 describes the separate remaining current-parent dependency.
- Task9 budget errors are narrowly mapped to 413 while unavailable/provenance failures remain 503. Accepted initial/generation receipt construction is independent of new-branch availability. Formal new initial/settings and generation remain closed pending the actual specified contributions.

## Evidence accounting and limitations

The author report is substantially candid about evidence limits. I do not promote compile/setup failures, structural fixtures or historical partial custody into business success.

| Evidence | Qualification retained in this review |
| --- | --- |
| `canonical-units-final-01` | 293 executed, 289 PASS and 4 ERROR; the four errors are the old private Runtime reflection signature. |
| `worker-shutdown-final-01` | Exact four adapted tests PASS; together with unchanged production and the other 289, **293 composed distinct**, not one all-green run. |
| `inherited-migration-final-01` / `-02` | First12 had2FAIL; final12PASS. Final current-read fix is in final main source. Final normal migration0 and all15×10 history fields match the original baseline via explicit UTC representation; no retroactive bootstrap claim. |
| `minutes-http-final-01` | 4PASS; accepted replay/history, denial/CSRF and 413 coverage. F1 explains its schema gap. |
| `terminal-replay-final-01` | 5PASS; overlaps earlier scenario identities, not five additional unique business capabilities. |
| `disposition-replay-final-01` | 3PASS on exact root+candidate-PK structural CAS; no current-pointer/root changes and no Task10 adoption claim. |
| `candidate-pages-final-02` | 1PASS after retained prior failing window; keyset/highwater and state metadata evidence. |
| `final-source-closure.json` | Final qualification windows report274 matching main-source paths; final mechanical pass independently confirms current274 and own154. Some earlier windows have explicitly listed source differences and are not silently final-source proofs. |
| `formal-runtime-start-01` | Actual import/global reconciliation start-close, RUNNING observation, 64MiB/2CPU, newGenerationDispatch=false; guarded18 tasks/10 operations unchanged. This is not a new generation or full workflow run. |
| `private-scan.json` | Six original known values plus two actual provider-secret files/eight unique values, zero exact hits and zero private-PEM hits. Scope96142 raw files/96297 staged paths/3099 unique staged blobs; receipt scanned in memory. I inspected the receipt/source, not private values, and did not repeat the scan. |

Mechanical custody is supporting evidence, not either semantic verdict. The initial report's165-window/20557-protected-path checks are reused; the final pass covers10 new result windows plus formal-runtime evidence. It reports zero new window/source mismatches, exact commit scope and raw path/size coverage, but explicitly does not rehash every old raw payload. I preserve that limitation. The sole retained blank-line whitespace finding at `Task09NativeFixture.java:33` remains `git diff --check` exit2, not a clean check; it is not escalated into a sixth behavioral finding.

The historical qualifications remain intact: original first external failure cause **UNKNOWN**; later UTC/readback diagnosis is not retroactive proof of that cause. Exclude stale-copied-XML `graph-controls-green-01` (actual0tests), resource-unqualified512MiB `application-provider-restart-02`, and wrong503-expectation response-budget window from claimed qualified success. Preserve the old collector's suite-level-only custody, absent historical per-case generation/continuous identity proof, and the11 missing before-images. Preserve failed original denied/child recovery windows and their explicitly composed settlement qualification. Tests with overlapping identities are not summed into new cases.

### CannotVerify / deliberately open cross-task obligations

No new test was run by this reviewer. F1–F5 are static source contradictions with named triggers; their suggested regressions are not claimed executed. The precise later full-path impact of genuine Task10 current PINNED/active exclusions and saved/adopted body changes still needs actual integration evidence. That future execution obligation is separate from the presently demonstrated parser/reconstruction defects.

Actual Task10 first-save/adoption/settings → current PINNED/active exclusions → generation, Task11 publication/freeze, successful formal new HTTP mutation, and full real LOCAL+S3 business workflows remain open to their assigned later tasks. Their absence is permitted at this Task9 stage and is not itself a finding. The source-only/structural tests cannot close them, and formal runtime RUNNING cannot substitute for them. Historical missing stack/before-images remain evidence limitations and are not repaired by this review.

## Required disposition

Return F1–F4 for Task9 source correction with the precise affected validation above; address F5 or obtain an explicit documented design reconciliation rather than silently calling the current command two-phase. Retain all existing failed windows and current qualification boundaries. Re-review the resulting focused diff and fresh affected evidence before Task9 adoption; no broad identical-source test rerun is requested by this report.
