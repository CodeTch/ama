# Task3 committed handoff

Exact author commit: `aa0b5f08a19649091b0bcd60959bd741fb5c7850`. Original BASE: `3e393dbc9485b187b89bfc03abd88821a0aabddb`.

Evidence manifest SHA256: `56767a16ef4ecd1266bcde2f383bc5747a06cc94803abe811ebe4169b4f4e4d6`. Final working tree clean. Committed source blobs match owned-source-manifest.json. Public report and full raw evidence are committed under artifacts/phase-b/b-remaining/task-03.

# Task 3 implementation and self-review

Task3 implementation is ready for independent review; no overall Phase B completion is claimed.

## Review range and custody

- Original BASE: `3e393dbc9485b187b89bfc03abd88821a0aabddb`.
- Branch: `codex/phase-b-completion-20261023`.
- Precommit HEAD: `27143b30ea8aa15b75df65d6b44e65920cc65351`.
- Intervening root commits contain only adopted designs/spec wrappers/remaining-plan. Exact paths are in `self-review-checks.json`.
- Exact 19 owned source/test paths and SHA256 are in `owned-paths.txt` and `owned-source-manifest.json`. Only these paths and this new task-03 evidence directory are committed by the implementer.
- Applicable original/R1 authority designs and independent reviews were fullread before implementation. The independently reviewed R2 pre-gate addendum was adopted by root at `40a50f7432b252e52fab7ee33d8234dc674a2986`; its original/R1/R2 and reviews remain preserved in sibling controller-pre-gate-design evidence.
- Author is the sole Task3 source writer; no helper/reviewer subagent was spawned. Root supplies independent review after this report.

## Implemented contracts

`JdbcMeetingAccess` now captures only enterprise ID/status for public authorization lock/try-lock identity paths. Existing private repository capture still transfers full names for create/save/roster; `JdbcMeetingRepository` only changes the identity coverage accessor to `ids()`. Its Task2 `meeting_minutes_editor_guard` INSERT is preserved. Actual huge supplementary names survive real create/save tests. The existing ACL and genuine DataSource-bound issuer remain the only authorization implementation.

`FileRegistry` gains additive authentic adoption, contextual domain prelock, complete source receipts, receipt-aware bind, and operation-specific read APIs. `JdbcFileRegistry` privately checks actual issuer, registered provider, DataSource transaction resource holder, exact genuine gate, full unique file set, phase, and one-shot consumption. A caught bind failure dooms the real enclosing transaction. A caller DTO, marker implementation, detached metadata or operation enum cannot mint a source or business capability. Legacy two-method `FileReferencePolicy` and old constructor behavior remain unchanged; old constructors cannot activate complete-source paths without explicit limits.

`CompleteReadPolicy` declares every complete protocol hook as abstract, including `prepareRead`, `authorizeRead`, `prelockCurrent`, four-argument `prelockRead`, whole-member enumeration, whole-scope task prelock, and finalization. The configured actual provider and private preparation lineage must match at final use. The registry rejects multiple distinct complete providers before downstream locks and invokes whole-scope task prelock before any finalization.

`JdbcTranscriptAuthority.create(DataSource, TransactionRunner, MeetingAccess, FilePurposePolicy, FileBackendCatalog, Map<String,FileReferencePolicy>, ArchiveReadBudget, CanonicalBudget)` constructs one authority, its private complete policy, registry, intake reader and source reader, attaches the registry exactly once before anything escapes, then returns `Components(registry, authority, intake, sources)`. Constructors for the issuer and adapters are not public assembly alternatives. `INPUT_BATCH_FILE` legacy attachment is rejected. Missing explicit budgets are rejected. Task4 owns production Spring registration; no HTTP/config wiring was added here.

The explicit `ArchiveReadBudget` already contains the required graph depth, fact row/edge, aggregate byte/scalar, individual UTF8/UTF16, batch-file and cancellation dimensions. The new metadata/source selectors use these fields for their stated dimensions; they do not infer graph limits from archive byte size. `CanonicalBudget` independently bounds pre-gate canonical encoding/hashing. SQL measures requested text before JDBC materialization; cumulative selections reject oversize before strings cross JDBC. Source metadata omits display names and provider tokens. Legacy consumers retain their original limits. No new universal implicit cap was introduced.

## First bind API for Task4

1. `components.intake().readDraft(actor, meetingId, orderedFileIds, cancelled)` uses a short independent authenticated transaction to return detached original metadata. It confers no write or byte authority.
2. `authority.prepare(draft, batchId, inputId, pinnedSnapshot, exactProcessingRef, cancelled)` independently reads the original archive closure, binds profiles, computes the batch/input fingerprints and canonical producer key, and returns private-constructible `PreparedIntake` (`batchId()`, `inputId()`, `spec()`). Fixed semantics are IMPORT/EMPTY/TXT, `TRANSCRIPT_IMPORT_V1` / `TRANSCRIPT_IMPORT`, priority 0, max attempts 3, target revision r+1 and original cancel epoch.
3. In the eventual producer transaction, adopt or lock the genuine B03 EDIT gate once. Call `reserveIntake(gate, prepared)`, then registry contextual `prelock` for every binding. Reservation verifies the registry's pre-source phase without granting a public phase bypass.
4. Acquire `prelockSources(gate, completeFileIds, false)` once: all original session UUIDs precede all file UUIDs. Pass the same actual receipt to `insertAndReadNew(reservation, receipt)` and `bindAll(receipt, bindings)`.
5. `insertAndReadNew` writes batch/profile/members/input after the deliberate existing source locks, reads back the actual new complete rows, and proves empty own producer/output sets. `requireHeld(held, gate, prepared.spec())` checks actual handoff references, epochs, full spec and same holder. The new batch needs no dummy transcript or confirmation.
6. Task4 must perform its business CAS/audit/enqueue in that same transaction using the returned exact spec and held facts. This slice does not advance meeting state or enqueue production work itself.

## Read distinctions and lock order

Generic download grants only current saved WORKING provenance through its complete original RAW producer chain. Owner history uses the requested RAW/WORKING revision and its fixed original input, including a successful producer from an older business version. It does not require a live RUNNING lease and can remain readable after publication when ACL permits. Worker source reads require the actual RUNNING task, complete immutable spec, owner/attempt, database expiry, matching unfinished attempt, and current business/cancel/writable state. A stale DTO expiry cannot deny a genuinely renewed lease; a future DTO expiry cannot revive an expired DB lease.

Pre-gate resolution, bounded discovery and preparation are recorded as registry-owned Prepared/Ineligible/Fatal outcomes. Recognized closed selector types independently determine the READ/EDIT floor even when the complete provider is missing or legacy-only. For known meetings the single fresh READ gate (plus existing same-gate EDIT for history/worker) precedes any observable stored failure. Authorization denial wins. Cancellation/interruption is prompt. Only genuine current ineligibility may use an independently valid legacy reference after authorization; fatal corruption/budget/profile/composition cannot fall back. A non-null dangling current pointer is now Fatal, covered by an explicit failing-before-fix test with a valid legacy reference.

The preparation graph is detached data. Final locking verifies exact observations and exact preparation actor/selector/issuer under the authentic gate. There is no hashing, archive parsing or late root discovery under that gate. The complete bounded producer-task selection is discovered before the gate and compared under the root; extra/missing tasks cannot silently disappear behind a single-ID lookup.

Order: identity IDs in existing unsigned UTF8 order; subject/meeting; all transcript parents by UUID; input; batch; profile; ordered members; all original upload sessions by UUID; all files by UUID; all declared durable tasks by UUID; all attempts by task/attempt. Multi-scope task union is locked before attempt/finalizer phases. Historical finalization verifies the actual successful typed output; worker finalization verifies locked DB lease/attempt truth. The registry's private pin opens exact historical storage only after transaction commit. Commit failure and cancellation at commit open nothing.

Original metadata checks include exactly one COMPLETED upload (bounded LIMIT 2), exact meeting/purpose/key/hash/size/cancel epoch, file AVAILABLE and exact counters, managed backend type/identity/revision. READ_ONLY routes work; disabled/redefined/unavailable routes and stale epochs fail without default fallback.

## Verification and preserved failures

Fresh command/source bindings, full logs and copied JUnit XML are retained by label. `verification-summary.json` lists exact commands and all suite counts. `verification-source-deltas.json` explains the small source differences between full regression, upload retry and final Task3 runs. Final authority-final-normalized source seal exactly matches `owned-source-manifest.json`.

| Evidence | Tests | Failures | Errors | Skips | Qualification |
|---|---:|---:|---:|---:|---|
| authority-regressions, unit suites | 221 | 0 | 0 | 0 | All default reactor unit tests |
| authority-regressions, Task3 | 21 | 0 | 0 | 0 | Before dangling-pointer regression |
| authority-regressions, other passing IT | 67 | 0 | 0 | 0 | MeetingRevision30, Concurrency5, Queue18, Facts12, actual LOCAL JVM restart1, config child-JVM restart1 |
| authority-regressions, upload IT | 39 | 0 | 29 | 0 | Initial application restart handshake timeout; retained failed run |
| upload-regression-retry | 39 | 0 | 0 | 0 | Independent upload-only invocation, unchanged timeout |
| dangling-red | 1 | 1 | 0 | 0 | Expected Unavailable was not thrown before fix |
| authority-final | 22 | 0 | 0 | 0 | Semantic final sources before Git formatting normalization |
| authority-final-normalized | 22 | 0 | 0 | 0 | Final exact committed Task3 source bytes |
| exact-authorized-teardown | 1 | 0 | 0 | 0 | Fixture custody only, separate from product qualification |

The resulting distinct verified set is 221 unit tests plus 128 selected integration tests (349), all passing in the applicable final runs. This is a composed evidence set, not a claim that the earlier 348-test invocation passed. Before final formatting normalization, from the broad run to authority-final there were exactly four changed source hashes: JdbcTranscriptAuthority has the narrow dangling-row-to-Fatal catch, TranscriptAuthorityIT adds its regression, and JdbcFileRegistry/FileRegistry only lose extra blank lines at EOF. The upload retry already includes the semantic fix and new test source; its only final differences are those two EOF whitespace changes. The initial staged whitespace check additionally found EOF blank lines in new files and Git CRLF normalization. All 19 owned source files were normalized only for CRLF and trailing EOF whitespace, recorded in source-normalization.json, and all 22 Task3 cases were then rerun in authority-final-normalized on exact final committed bytes. No further semantic changes were made.

The original upload failure is preserved in `authority-regressions.log` and its XML: MySQL JDBC handshake exceeded the configured 3-second connect timeout at `FileRegistryUploadIT.fullNameAndOpaqueTokenSurviveApplicationRestart`; its failed app start caused 28 subsequent connection errors. `upload-preflight.log` records the same guarded container before retry: running, restart count 0, not OOM-killed, maximum used connections 8 of 151, no max-connection errors, current successful read-only connection, and resource snapshot. The retry passed 39/39 without changing timeout or test source. There was no simultaneous resource snapshot at the first failure, so its environmental root cause is unresolved; a passing retry does not prove that cause. No global timeout or runtime workaround was introduced.

All earlier TDD/compile/setup/trace failures remain in raw evidence; none are silently counted as green. Important sequence: identity, source receipts/adoption/binding, complete-read dispatch, real first bind, real producer/history/worker metadata, adversarial rollback, denial precedence, full lock trace, budget/route checks, then dangling-pointer red/green. Early fixture setup failures (absolute archive root, schema-invalid profile/status, and trace boolean SELECT classification) are recorded and corrected in test setup. The final-audit initial assertion used an incorrect table-name spelling; final-audit-2 uses the real existing Task2 table and passes; final-audit-normalized rebinds the normalized source and final custody receipts. These are not implementation runtime qualification results.

## Focused proof coverage

The 22 Task3 methods exercise actual B04 completed uploads and actual saved DB rows: first bind without transcript/confirmation/task; issuer/gate/holder/set/phase/consumption rejection; caught binding and injected commit rollback of whole graph/references/epochs; old registry behavior; registered finalizer once and no open on failure; current/member vs owner history vs live worker; actual renewal/expiry/ended attempt; exact successful producer/full immutable spec; stale graph without late discovery; schema/profile/cycle/corruption failures; source route/revision/READ_ONLY/disabled/counter/original-session defects; bounded graph/rows/edges/scalars/UTF16; sorted parent/session/file/task order with reversed input ordinal; pre-gate denial precedence for missing/legacy/failed/ambiguous providers; genuine legacy fallback vs fatal; cancellation at preparation/commit; skip-locked identity before roots; full supplementary roster-name persistence.

Private preparation misuse tests use test-only reflection to obtain genuine internal state and supply foreign/expired/wrong-selector/wrong-phase instances. No production policy getter, public opener, public pin or privileged test API was introduced. Controlled protocol callback counters test registry orchestration; real authority/provenance tests use the private production issuer and genuine transaction holder.

## Fixture custody and resource limits

Only container `ama-test-b01-ad9f1af29eff`, ID `eefdd9a984ee00fe6ca886168b29a6ce4f3b405467b8d3204240d4e1a8dc7502`, loopback port 58062 was used after exact identity/running/port guard. Only the new `.tools/phase-b-remaining/env.sh` was loaded. No reset, old 12-migration fixture creator, old database/storage fixture, old TLS private asset, mount, new container or S3 runtime was used. Existing 13 migration SQL files and formal schema were unchanged.

New native/TLS roots use fresh temp UUID directories. `*.fixture-paths.json`, `*.children.json`, command receipts and fresh custody receipts retain nonsecret path/owner/expanded process arguments. Maven and test JVM limits are 512 MiB / 2 active processors; new native service children are 64 MiB; selected real registry API child is explicitly 192 MiB / 2 processors. Config restart child inherits only nonsecret 64 MiB / 2-processor JAVA_TOOL_OPTIONS. Generated passwords/key bytes are never evidence content. Final exact credential scan reports zero hits.

`TranscriptAuthorityFixture.close` terminalizes only its tracked synthetic nonterminal task IDs and unfinished attempts in a controlled deny-only teardown transaction, retaining successful history. The earlier exact RUNNING task `7d4db414-118c-432b-aaa6-32b654e96a69` was separately proven absent from Task2's sealed snapshot and changed only under root's exact-ID ACK: task CANCELLED and its unfinished attempt ended CANCELLED, affected counts one each. Before/after receipts and ACK are preserved. It is not a business cancellation/runtime proof. Final read-only custody receipt contains 51 retained synthetic task/attempt rows, all tasks terminal and all attempts ended, so none can be claimed by later Worker tests. Prior fixture namespaces and successful historical rows were not globally cleaned or reset.

## Self-review result and limits

Final checks pass: 19 owned source/test paths, empty index before explicit staging, root-only intervening committed docs, original BASE ancestry, whitespace check, unchanged FileReferencePolicy, unchanged formal SQL, Task2 creator guard preserved, final source seal matches the normalized 22-test run, no credential hits, and retained synthetic task custody closed. `self-review-checks.json` and the evidence manifest make these checks reviewable.

This slice supplies authority/source provenance and first-bind composition. The successful RAW/producer rows in read-policy tests are controlled stored fixtures, not evidence of an actual importer or Worker output. Production Spring registration, HTTP/token intake, task enqueue, actual parser Worker, saved transcript workflow, external material runtime, fairness and S3 runtime qualification remain later tasks. No claim is made for those flows or for all Phase B.

Independent review should assess the complete source range from original BASE, with special attention to the private capability/holder checks, bounded projection arithmetic, producer set completeness, same-gate failure precedence, and whole-source/task/attempt phase order. The implementer found and fixed the dangling-pointer fallback bug during self-review; the remaining known concern is the single unreproduced inherited JDBC handshake timeout documented above.




---

# Task3 fix round1 committed handoff

Exact fix commit: `912945df2ad09d7ff265f6ba97e304a2b98a4b79`. FIX_BASE: `aa0b5f08a19649091b0bcd60959bd741fb5c7850`.

New evidence manifest SHA256: `5e65debdaa678c4b738faf034a81f63aa0b496eb484cb25ecd439a6bafc416a4`. Clean worktree; committed source blobs match exact covering-final source hashes. 35 covering tests passed (4 unit +31 integration). 16 new exact synthetic tasks/attempts terminal/ended. Original417 Task3 evidence files unchanged.

# Task3 fix round 1 — independent review closure

Ready for scoped independent rereview of T03-IR-01 and T03-IR-02. This report does not grant the implementation gate or claim Phase B completion.

- FIX_BASE: `aa0b5f08a19649091b0bcd60959bd741fb5c7850`.
- Precommit HEAD: `5560381927d51001bd484642269b9432481e8c70` (root-only Task5 design adoption after FIX_BASE).
- Reviewed report: `.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-03-review.md`, exact SHA256 `60f3e807d0d6e79fbdeafce015b063ce0acefbf910c0ec252dab25484952149c`.
- Scope: `FileBackendCatalog.java`, one call in `JdbcFileRegistry.java`, and new `TranscriptAuthorityReviewIT.java`. Exact paths/hashes are in `owned-source-manifest.json`.
- No Task4, Worker/parser, queue, schema, creator or public capability changes. No helpers or reviewer agents were spawned.

## T03-IR-01: locked source metadata must not derive backend hashes

**Addressed.** The reported call chain was reproduced by a real failing integration test. The prior implementation and adopted original design incorrectly treated `FileBackendCatalog.require` as metadata-only. The prior public Task3 report's no-hashing-under-gate statement was too broad; it is corrected by this appended finding closure, while the original evidence remains immutable.

Root explicitly authorized the minimum FileBackendCatalog extension and resolved the conflicting original helper choice in favor of the higher-level no-hash-under-roots rule. Backend construction already calls `ManagedBackendId.require`; `Backend` is an immutable record whose definition is a sealed Local/S3 record of immutable fields, and the catalog freezes its map. This construction proof remains intact.

The new **package-private** `requireHistoricalMetadata(id)` selects the actual configured backend and performs the same actual DB type/status/configuration-revision check. It does not derive an ID, invoke storage, accept a caller-attested flag or callback, or expose a public authority. `JdbcFileRegistry.prelockSources` calls this method after source roots are held. Shared private helpers avoid a second metadata policy. The existing public `require(id,newWrite)` still validates managed identity, then runs the same metadata check, preserving legacy B04 behavior. Post-commit open still uses that original `require` before the actual storage read.

The regression uses a temporary test-only JCA SHA-256 provider, always removed in `finally`. It delegates byte-for-byte digest computation to the JDK SUN implementation and observes the actual ManagedBackendId call stack plus Spring's actual transaction state; it does not stub production validation or assert source strings. The red trace shows `ManagedBackendId.derive → FileBackendCatalog.require → JdbcFileRegistry.prelockSources → JdbcTranscriptAuthority.readDraft` under an actual transaction. The final green run exercises genuine draft acquisition, receipt-aware first bind, worker source read, constructor identity rejection, and actual route corruption. It records **managed SHA-256 outside=5, locked=0**.

Real new fixture route rows are independently changed to mismatched revision, DISABLED status, and wrong type; each fails without opening storage. A separate exact-route disable occurs only after physical read-transaction commit, proving the retained post-commit check denies before open. Only the test's exact new backend ID is altered and restored. Existing READ_ONLY recovery and original-backend pinning are covered by the two inherited selected upload tests.

## T03-IR-02: whole task union precedes every attempt

**Addressed by added acceptance evidence; no production union logic change was needed.** The earlier singleton tests could not establish the required cross-scope property. New tests retrieve the actual registered private production policy through the established test-only reflection pattern and obtain genuine issuer-minted preparations/scopes from actual saved rows, on one genuine EDIT gate and actual transaction holder.

1. Two real distinct input batches/tasks use different completed B04 source files. Both tasks are actually RUNNING with unfinished attempts. Request/scope order is explicitly sorted in descending task UUID order, while expected lock order is ascending. After genuine source receipt acquisition and actual policy validation, the production `prelockReadTasks` must lock both distinct task UUIDs exactly once in ascending order. No attempt lock is present at that boundary. Production finalization then locks the whole attempt union. The trace asserts the last distinct durable-task acquisition precedes the first attempt acquisition. Each genuine scope finalizes once; a second attempt to finalize it fails by its private phase guard.
2. Two independently prepared/minted scopes for the same actual task prove shared task and attempt deduplication: each primary acquisition occurs exactly once, while both scopes independently finalize exactly once.
3. Normal immutable graphs cannot naturally carry contradictory snapshots through the final observation rechecks. To test the union's defensive conflict branch, a clearly labeled **test-only fault injection** replaces one private task-expectation map only after two genuine scopes and sources have been obtained. Conflicting priority expectations fail before any task/attempt acquisition or storage open. This is not a normal business-reachable graph, public API, fabricated public capability, or runtime-success proof.
4. A separate configured-provider orchestration test gives the real registry two distinct scopes over the same file. It proves one whole-scope prelock call, exactly one finalization for each scope, and zero storage opens until both have finalized. This is deliberately labeled protocol orchestration evidence, not production authority proof.

The public product still accepts its existing single operation selector. These tests exercise private production union behavior without adding a multi-request API. The direct production-policy tests use its real holder checks and DB reads; they do not independently claim a public multi-scope end-to-end workflow. Trace receipts distinguish first primary task/attempt acquisition from later locking predicate rechecks.

IR01 has a real RED→GREEN implementation sequence. IR02 was a missing test requirement against code already appearing structurally correct; its new tests passed without an artificial production defect or unrelated refactor. The existing singleton evidence was not rerun merely to reproduce its insufficiency.

## Exact verification window

All fresh commands, full output, source hashes and fresh JUnit XML are retained in this new directory. `verification-summary.json` lists every command and suite. `covering-final.sources.json` exactly matches all final modified/new source bytes in `owned-source-manifest.json`.

| Run | Tests | Failures | Errors | Skips | Meaning |
|---|---:|---:|---:|---:|---|
| hash-red | 1 | 1 | 0 | 0 | Actual locked ManagedBackendId SHA-256 call, before fix |
| hash-green | 5 | 0 | 0 | 0 | ManagedBackendIdentity4 plus genuine boundary1 |
| multiscope-proof | 4 | 0 | 0 | 0 | All new review tests before added post-commit denial assertion |
| covering-final | 35 | 0 | 0 | 0 | Exact final source window |

The final 35 are **4 ManagedBackendIdentity unit tests + 22 existing TranscriptAuthority + 4 new TranscriptAuthorityReview + 3 FileBackendCatalog + 2 selected FileRegistryUpload integration tests**. The upload selectors are `defaultSwitchPinsExistingUploadAndBusinessDownloadToOriginalBackend` and `readOnlyBackendRecoversFrozenResultButRejectsNewPartWrites`. No full reactor unit/IT collection, old restart suite, or S3 runtime was rerun. The previous 349 composed-evidence total remains confined to its originally documented source windows; it is not relabeled as run on this fix.

The previous upload handshake timeout remains unresolved. Its failed run and later healthy retry remain byte-for-byte preserved. This fix neither diagnoses that earlier timeout nor changes connection timeouts or adds a runtime workaround.

## Fixture and evidence custody

The runner guards the exact approved MySQL ID `eefdd9a984ee00fe6ca886168b29a6ce4f3b405467b8d3204240d4e1a8dc7502`, container name `ama-test-b01-ad9f1af29eff`, running state and loopback port58062 before loading only `.tools/phase-b-remaining/env.sh`. Same public JDK21 and Maven/test JVM512MiB/ActiveProcessorCount2 limits apply. Selected helpers were source-inspected: they create fresh target UUID storage/PKI paths; no old private assets, mounts, new containers, reset/repair/DDL or S3 fixture is used. Flyway's existing validation reports 13 current migrations and no migration necessary.

Only exact new IDs tracked by the existing approved fixture teardown are terminalized. The final read-only custody query selects only the **16 exact task UUIDs extracted from this fix's fresh receipts**, not a mutable prefix namespace. All16 tasks are terminal and all16 attempts ended; rows remain retained. `new-exact-task-custody.tsv` and the command/output seal record them. Existing successful historical fixtures and old task rows are untouched.

All **417 files** in the original `task-03/` evidence directory were rechecked against its original manifest/hash and remain unchanged. New evidence lives only in `task-03-fix-01/`. The formal SQL and Task2 creator guard are unchanged. Credential scanning reports zero hits. No generated keys/password bytes are included in evidence. Fresh nonsecret path/owner receipts and lock traces are retained.

## Self-review and handoff

Production change is narrowly limited to20 additional catalog lines/two extracted private checks and one registry call substitution. Constructors retain genuine immutable managed identity validation; metadata type/status/revision comparison is shared, not weakened. Existing public `require`, defaultWrite, readiness, and post-commit open retain their behavior. No authority issuer/holder/receipt/phase or task-union production code changed.

The new security-sensitive methods and tests use normal formatting. Minor M01 broader formatting is not expanded into unrelated churn. M02 remains explicitly unresolved as above. Final audit verifies empty index before owned-path staging, root-only intervening docs, three owned source/test paths, exact final run/source binding, unchanged417 prior evidence files, no schema/creator changes, no credentials, and closed exact new task custody.

Independent rereview should focus on the metadata-only locked path, construction proof, actual post-commit route denial, two-scope ordering/dedup/conflict trace assertions, and absence of new public bypasses. Root provides that review; no gate or overall Phase B approval is claimed here.
