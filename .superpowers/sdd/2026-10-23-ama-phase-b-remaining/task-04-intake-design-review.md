# Task 4 intake/API independent design review

Date: 2026-10-23. Design review only. This report does not approve implementation, schema activation, runtime installation, tests, or Phase B completion.

- SPEC verdict: **CHANGES REQUIRED**
- Design-quality verdict: **CHANGES REQUIRED**
- Open findings: **2 total — P0: 0, P1: 0, P2: 2, P3: 0**
- Exact reviewed artifact: `.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-04-intake-design.md`
- Exact review SHA256: `3626aad723401de14d633d28a200138aa2cdeb233fc44ef961c348164296a574`
- Frozen source used for targeted source checks: `3e393dbc9485b187b89bfc03abd88821a0aabddb`. All source references below mean that commit, not the concurrent Task 3 working tree.

## Scope and authority

Read the complete Task 4 design, complete controlled-runtime specification including Normative R1, adopted authority/external wrapper, approved Task 3 original and R1 authority designs and R1 review. Remaining-plan review was restricted to Task 4 and applicable global/shared constraints. Targeted frozen-source reads checked B03/registry ordering, B04 upload operations/backend restrictions, preparation/queue completion, existing meeting CAS/idempotency and processing manifest grammar.

The controlled-runtime spec lines 11–18, 57–73 and the adopted Task 3 R1 source-receipt/complete-policy/rank rules remain binding. Proposed finite budgets are explicit deployment capabilities, not new product quotas. No company integration, installed worker, or actual tests are inferred.

No DB, Docker, network, private environment, tests, source/index/HEAD edits, or subagents were used. Read-only `git show` used an invocation-local safe.directory override after Windows Git rejected repository ownership; no Git configuration was written. Only this new review report was written.

## Findings

### T4DR-01 — P2: Make preflight-independent accepted replay mandatory

**Design references:** Task 4 lines 69–72, 82–85, 106, 169 and 182.

Line 69 calls the early replay transaction “Optional,” but the promised behavior at line 106 requires it (or an equivalent mandatory replay-first mechanism). Without this optional step, a valid retry first executes readDraft, which requires the original expected revision at line 70, and loads the current configured archive at line 72. A previous successful intake already advanced r to r+1. Thus an implementation that legitimately omits the optional optimization rejects even an immediate lost-ACK retry before reaching the final transaction's committed-replay branch. Changed/unavailable current archive selection creates another premature rejection. The max-revision replay promise at line 169 is affected for the same reason.

**Source support:** `backend/adapters/src/main/java/ama/meeting/JdbcMeetingRepository.java:66–71` and `:96–101` restore a reauthorized accepted result before writable/CAS validation. Controlled-runtime lines 21 and 59 require semantic reauthorized replay. The design itself promises historical acceptance replay without archive reselection.

**Required change:** Make an existing-acceptance lookup/replay path mandatory before any current-CAS, exhaustion, source, current-archive or intake-readiness prerequisite that can reject an already accepted request. Preserve fresh actor authorization and exact scope/body matching; do not bypass those. A miss ends that transaction and proceeds to preparation/final serialized claim. Define the miss/concurrent-winner case explicitly if preparation fails after another same-key acceptance commits, so the error path cannot accidentally return speculative acceptance or choose a new key.

**Future proof:** Commit acceptance, advance/change the meeting or make current archive selection unavailable, retry the identical key using legacy/token/equal-dual normalized forms, and obtain the original task/input/batch/revision with no new bind/enqueue. Revoked actor and changed semantic request must still fail. This is a design wording/flow correction, not a claim that the eventual implementation is defective.

### T4DR-02 — P2: Do not advertise ABORT for a FINALIZING upload

**Design references:** Task 4 line 154, together with compatibility statements at lines 175 and 203.

The proposed UploadReceipt explicitly says “FINALIZING permits completion retry/abort per actual B04 behavior.” Frozen B04 does not permit that abort. `JdbcFileUploads.abort` authorizes the owner and checks original purpose/epoch; COMPLETED and ABORTED are idempotent early returns, then it requires OPEN. FINALIZING therefore throws Conflict before changing state or calling storage. Publishing ABORT as an allowed action creates an authoritative server/UI contract that the existing mutation will reject. Altering B04 to make this true would broaden the design's declared scope and frozen-upload semantics.

**Specific source:** `backend/adapters/src/main/java/ama/storage/JdbcFileUploads.java:343–367`, particularly `:354–357`. Completion supports OPEN/FINALIZING at `:251–279`; OPEN requires a writable backend, while FINALIZING recovery uses the historical non-new-write route. `backend/adapters/src/main/java/ama/storage/FileBackendCatalog.java:128–145` permits READ_ONLY recovery and rejects DISABLED/mismatched routes.

**Required change:** FINALIZING advertises completion retry only, subject to fresh owner/purpose/epoch/lifecycle/publication and historical-route checks. No UPLOAD_PARTS or ABORT action for FINALIZING. Document any terminal idempotent retry exposure separately from a state-changing permission. Preserve OPEN versus FINALIZING backend distinctions: a generic “route available” flag must not authorize OPEN writes on READ_ONLY, and a writable-default requirement must not hide FINALIZING recovery on a valid historical READ_ONLY route.

**Future proof:** Discovery and the real B04 endpoints agree for OPEN, FINALIZING, COMPLETED and ABORTED across WRITABLE/READ_ONLY/DISABLED historical routes, epoch changes, publication, and revoked owner authority. No new upload mutation is needed to close this finding.

## Source-resolved risks and accepted design areas

1. **No subject/meeting inversion in the proposed transaction.** Task 4 lines 82–84 are safe against the established path: `JdbcFileRegistry.java:109–118` calls `access.lock`; `JdbcMeetingAccess.java:41,68–83` reaches `lockPrepared`, which locks the MEETING subject at line 76 before the meeting root at line 78. The later explicit fences.lock retains an already-held subject. This exactly matches Task 3 original line 200 and R1 lines 181–193. Do not file a deadlock finding based only on the abbreviated diagram. Actual post-Task-3 SQL tracing remains a gate.

2. **Registered source/holder authority and adoption are appropriately explicit.** Task 4 lines 88–114 require exact gate/full file-set source authentication, private Task 3 issuance, actual new rows/read/seal/BOUND state, exact DataSource and transaction holder, and the registered policy's own preparation issuer. The adopted enqueue path avoids a second gate/identity/source pass. Frozen `JdbcTaskPreparations.java:14–41` and `JdbcTaskQueue.java:45–63,72–80` support this composition and full-spec collision checking. Public DTOs and effectsAllowed do not substitute for private issuance.

3. **Revision/spec/producer and immutable graph match the adopted contracts.** Task 4 lines 58–74 and 93–106 use TRANSCRIPT_IMPORT_V1/TRANSCRIPT_IMPORT, TRANSCRIPTION_INPUT, r+1/e, exact processing fingerprint, priority 0/maxAttempts 3, original ordinal order, explicit batch profile and the adopted producer envelope. Both batch and input base use r+1. Existing transcript/saved pointers remain while confirmations clear and needs_generation becomes 1. IMPORT_TXT, TRANSCRIPT and IMPORT retain their distinct manifest/batch/input roles.

4. **Canonical manifest compatibility needs ordinary implementation precision, not a new product choice.** Frozen `ConfigSchema.java:67–81` matches the stated IMPORT/TXT/EMPTY structure. Expand line 73's abbreviated profiles list into the actual ordered objects `{kind:"CANONICAL_CODEC",ref:...}`, `{kind:"CONFIRMATION_HASH",ref:...}`; each ref uses artifactType/version/sha256. Do not serialize bare strings or bare refs. The document already binds the existing readers/schema and requires independent vectors, so this is recorded as a precision gate rather than a third finding.

5. **No-hash-under-roots and new-row exceptions are coherent.** Preparation freezes archive/manifest/fingerprint/producer work before roots; locked work compares bounded values. Complete existing domain acquisition precedes sessions/files/tasks. Intake-created rows and completion-created RAW/segments are the only post-source new-domain exception. Task 4 line 116 correctly validates actual typed output after the writer, consistent with frozen `JdbcTaskQueue.java:127–140`; it does not require the producer task to be SUCCEEDED before the queue finishes it.

6. **Assembly is cycle-free as designed, conditional on the actual Task 3 seam.** Lines 122–126 specify private one-time pre-exposure attachment, exact complete registration, immutable publication and DB-only independent event validators. No public attachment, lazy bean lookup, queue-dependent authority or second B03 issuer is authorized. Uninstalled import has no intake capability; no fake health follows from task count.

7. **Discovery separates receipts, source eligibility and bytes.** Lines 134–161 cover all required owner collections/details and lost accepted IDs, bounded keyset pagination, explicit safe projections, UTF8/UTF16 pre-transfer checks, complete graphs, fresh B03 and no-store responses. Published owner metadata is compatible with frozen `JdbcMeetingAccess.java:93–104`: owner EDIT is distinct from writer never-published checks. Obsolete epochs can leave metadata visible while bytes remain denied. READ_ONLY completed immutable sources remain eligible under Task 3; DISABLED routes do not. T4DR-02 is the specific incorrect upload action.

8. **Lossless HTTP ownership and compatibility are adequate.** Lines 165–169 cover the actual existing info/roster controllers now, normalized semantic digests, additive numeric-compatible output tokens, canonical ASCII grammar through 9223372036854775806, max-minus-one success and max exhaustion after replay. Frozen `MeetingController.java:34–53`, `MeetingContracts.java:12–32` and `MeetingDigests.java:13–17` substantiate the narrow extension. New browser-used long fields have string tokens; Task 12 owns actual token-only browser generation, not the server extension. Existing numeric upload writes remain compatible within explicitly qualified practical limits.

## Remaining implementation/adoption gates

These are future evidence requirements, not successful tests:

- Close T4DR-01 and T4DR-02 in a revised exact-hash design and obtain bounded re-review/root adoption.
- Begin source work only on root's frozen adopted post-Task-3 base. Verify genuine assembly/BOUND/adoption APIs rather than invent public authority seams.
- Trace actual identity -> subject/meeting -> complete R1 domain -> all sessions -> all files -> all tasks -> attempts; prove same subject re-lock, no second gate, no late existing roots, no archive/parse/hash under roots, and no SQL after final flush.
- Prove full transaction rollback at insertion/bind/enqueue/audit/flush faults and that consumed/failed receipts cannot be reused.
- Prove accepted replay independently of changed current CAS/archive/readiness, semantic legacy/token/dual equivalence, max handling, revoked authorization and concurrent same-key acceptance.
- Freeze complete OpenAPI shapes and exact profile-ref grammar; use independent producer/manifest vectors against the existing readers and ConfigBindings.
- Prove authorized, bounded, no-store discovery including huge/supplementary historical names, complete graphs, missing/ambiguous provenance, historical READ_ONLY behavior, real B04 allowed actions, and lost-ACK/restart recovery.
- Prove private assembly failure/foreign-source/holder/provider negatives and actual Spring cycle-free startup; keep generic legacy compatibility and mandatory new complete-policy dispatch distinct.
- Prove real HTTP integers/tokens at 2^53-1, 2^53, 2^53+1 and supported max, plus exact exhaustion/replay ordering. Task 12 still must prove actual browser string request generation and preserved drafts.
- Keep Task 5 worker/RAW/output/restart evidence, Task 6 saved/member/history bytes, and Task 12 actual browser local/S3 E2E separate. Intake acceptance does not establish an installed execution runtime.

Root owns adoption and activation decisions. This review grants neither.

