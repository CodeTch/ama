# Phase B remaining implementation plan

> Execute continuously with subagents under the user's explicit activation. Do not stop at preparation or after an individual slice.

Spec: docs/superpowers/specs/2026-10-23-ama-phase-b-controlled-runtime.md. Original user-approved architecture and Phase B plan remain binding. Initial BASE 34cb226aefe99c67d3681af68d748b0aeea4e073. Preflights: docs/acceptance/b/remaining-preflight/. New isolated coordination branch codex/phase-b-completion-20261023.

## Global constraints

No old SQL/plans/artifacts/root .gitattributes/stash/main/worktree alteration. Only new owned loopback fixtures, no previous fixture/env/PKI/S3 operations; bounded resource creation, no secrets in logs. Root assigns migration numbers/freeze/ACK; authors cannot apply unacknowledged DDL. Explicit budgets are deployment capabilities, not invented product quotas. No company wire fabrication. Detached facts are not write authority. No network/file/archive/parse/hash work while DB roots locked. Full identity->subject->domain->sessions->files->tasks->external execution order; audit/outbox flush last. Read each actual source before edits. TDD for behavioral changes; relevant inherited regressions once before commit; failed setup is not business RED. Exact reports distinguish compile/unit/actual HTTP/MySQL/restart/browser evidence. No success without output.

## Tasks

### Task 1: Atomic task/worker prepared completion.

Add Supplier<TaskResult> overload to TaskQueue/JdbcTaskQueue (default fail closed for other implementations), preserve existing 3-arg API behavior. Validate genuine full lease/current/input before callback, actual output after DB-only callback, terminal mutation last, one transaction/flush. Null/throw/invalid output/audit/flush rolls back. Add explicit prepared-handler WorkerLoop factory/mode while existing constructors remain compatible; no double completion, lost lease suppresses callback, closure bounded. Owned core TaskQueue, adapters JdbcTaskQueue, worker WorkerLoop, focused unit/IT and NEW owned fixture harness. Exact tests: callback not invoked on late/revoked/stale lease; actual new output exists before validation; invalid/throw rolls rows/task/outbox back; successful output and task commit together; prepared Worker executes once/no early callback/no duplicate completion/heartbeat loss. External observation extension is Task 7, not implied complete here.
### Task 2: Reviewed forward runtime schema/profile/editor contract.

Design sidecar/editor guard exact lifecycle; independent design/DDL review before root number allocation/freeze and apply. Root verifies pinned Flyway ordering and old SQL/history hashes; new owned initial-zero fixture, physical metadata/negative/CHECK/FK/old data/history/rerun tests. Do not activate runtime merely from schema adoption.
### Task 3: Authentic file authority and bounded held processing facts.

Implement operation-specific first bind/current read/owner history/worker source read, genuine same-holder capability and original upload metadata/purpose/route proof. Preserve B04 APIs/old limits; never broaden generic download. Tests first binding without transcript, foreign/expired/late contexts, wrong session/purpose/route/epoch, member pending/history denial, mixed profiles, full lock order and rollback.
### Task 4: Processing intake/registered production policy/API.

Freeze exact batch/manifest/config outside locks, fresh recheck, insert batch/profile/input, one bindAll + authentic enqueue + CAS/audit/outbox atomic. Real post-intake spec/producer as spec. Idempotent same request one task; different payload conflicts; stale/denied/failure no rows or reference handoff. Add controller/OpenAPI/capabilities, no fake task output.
### Task 5: TXT Worker/import output.

Consume real registered task and immutable original archives/actual pinned bytes, strict parser ordered files/aggregate budgets. Prepared atomic RAW revision/segments/task result, rollback failure, no partial writes. Real queue/WorkerLoop/MySQL/local+S3 and fresh-JVM archive/input restart tests.
### Task 6: Saved transcript/marks/current confirmation.

Bounded complete facts reader and same-algorithm draft hash entry; immutable WORKING save/restore inherited provenance, all marks including empty, current/owner history/member effective reads. Actual disabled correction-domain capability. HTTP idempotency/CAS/ACL; unsaved/stale/blank fails; mark-only invalidation; repeated confirmation one ID; archive damage/old config fail correctly.
### Task 7: External prepared-output and narrow stale candidate retention protocol.

Add independently reviewed purpose/capability/transaction protocol to existing external operations, retaining all generic strict fences. Complete marker/operation/task/result/permit/outbox atomically. Explicit externally-owned Worker completion prevents second completion. Candidate-only retention after business revision change under fresh authority/same epoch/lifecycle, no current pointer writes; deletion/revocation/cancel fail. Adversarial old tokens/authority/rollback/receipt tests.
### Task 8: B05 reconciliation fairness.

Fix exact FUP-B05-CANDIDATE-01 with bounded scan/cursor/progress across calls, no fake next_check_at mutations, preserve SKIP LOCKED/full identity ordering and unknown-policy no authority. New owned fixture >64 held/unregistered candidates, later eligible eventual claim; inherited reconciliation/identity tests. Close follow-up only after independent evidence/review.
### Task 9: Minutes facts/input/model/candidates.

Complete bounded actual generation/review/final graph reader, fifteen-profile archived closure, prospective ConfigBindings computation/fresh recheck, registered input+task enqueue. Controlled HTTP model normalized port/strict output; production absent contracts unavailable. Token-bound global permits and committed external marker. Atomic full candidate graph, support PARTIAL/UNSUPPORTED and unaccepted action suggestions. Malformed/unknown/budget/source/lease/crash and restart tests.
### Task 10: Guarded first save/adoption/edit/review.

Durable acknowledged editor-intent handshake before typing, atomic first-save guards, immutable retained candidate on changed business revision; regenerate explicit adoption/reject/disposition. Owner saved edits preserve provenance/current body; exact source/action reviews bind saved targets/scopes/whole evidence. Member saved-only/admin no bypass; changed content/source invalidates review; concurrency preserves draft; HUMAN title remains.
### Task 11: Final confirmation/publication/reconciliation.

Prospective final hash, exact frozen review membership and fresh current readiness. Actual controlled durable HTTP KB protocol, destination proof, stable business key, committed marker and attempts. Lost response/restart/query yields one document; UNKNOWN never resends; zero-effect explicit retry uses existing root/new execution; success makes all writes read-only. Production unavailable without company contracts. Concurrent final/review/edits and denied late facts tested.
### Task 12: Web local controlled loop and actual browser acceptance.

Server projections/task read/controller/API errors; real file upload/import/save/confirm/generate/candidate/review/final/publish pages per approved prototype anchors. Draft preservation/409/retry/idempotency/read-only flows; owner/member/outsider/admin. Explicit test identity/harness only. Typecheck/build plus local and S3 actual end-to-end and refresh/restart/lost-response paths. No frontend-only permission checks.
### Task 13: Final integrated verification, fresh whole independent review, records/delivery.

Run relevant affected full gates once, preserve failures and exact source/evidence hashes. Review all new commits with most capable fresh agent; bounded fixes/re-review. Write docs/acceptance/b/summary.md, demo/test qualification/pending integration records, update DEVELOPMENT_HANDOFF and development-resume. Adopt reviewed work to the active development branch if safe under existing activation; preserve main/old stash/trees. Record complete local B separately from company/HA/capacity and later C/D/E/F.

## Shared interface and self-consistency scan

|Tasks|Producer -> consumer|Check/ruling|
|1,5|Supplier completion + prepared Worker -> import writer|DB-only callback after fresh full lease, before actual output validation; no parse under locks|
|1,7,9,11|ordinary completion vs external owner|1 does not claim external support; 7 supplies explicit single owner path before consumers|
|2,3,4,10|profile/editor schema -> qualified rows and guard|No old row inference/backfill; strict root version allocation and apply gate|
|3,4,5,6|authentic file/held proof -> intake/worker/saved facts|same DS/holder, original session, distinct current/history/worker purposes|
|4,5,6|post-intake spec -> RAW -> WORKING confirmation|RAW not human saved; manifest original fences retained, current save fence independent|
|6,9,10,11|complete saved source/archive graph -> generation/review/final|No fabricated rows; bounded completeness, immutable original profile dispatch|
|7,10|stale retention -> first-save protection|Generic B05 fence strict, retention cannot adopt or authorize send|
|8,9,11|fair candidate selection -> external worker activation|Must close follow-up before consumer external loops enabled|
|9,10,11|candidate graph -> body/reviews -> frozen final|support never inferred; exact reviews frozen, changes invalidate|
|4,6,9,10,11,12|real contracts/projections -> browser|Idempotency/CAS/authorization remain server authority; UI draft preserved|
|1..13 self consistency|Every task's output precedes its named consumer and tests inspect actual behavior|No fake placeholder completion; no source changes in final docs-only task unless routed to fix review|

Detailed per-pair collisions and task status live in the plan ledger. Implementation authors run sequentially with exact owned source base; independent read-only design/reviews may run in parallel.

## R1 design-review resolutions (normative)

Read the spec's full Normative refinement R1 before each Task2..13 brief. It supersedes ambiguous epoch/retention/API shorthand above. DR-01: sticky real0-only guard; explicitadopt nooriginalepoch equality. DR-02: registered authentic stale-only immutable retention; STALE nonblocking/nonadoptable, current editor-only PENDING; finalrace serialized. DR-03: explicit task->operation heartbeat/complete ownershiphandoff. DR-04: supported v2 graph-copy/edit/conversion matrix, full remapping/no reviewcredit, deterministic required SOURCE_SUPPORT/ACTION_FIELDS reviewset. DR-05: allbrowsermutations exact token/legacy normalization before idempotencydigest. DR-06: producer-owned authoritative read/discovery table plus actualTLSdualstorageE2E. All six original findings preserved; bounded independent rereview required before dependent runtime activation.

## Adopted authority/external refinement

The normative additive design is `docs/superpowers/specs/2026-10-23-ama-phase-b-authority-external-protocols.md`, backed by exact independently reviewed artifacts and hashes. It supersedes compact Task 7 permit-atomic wording: observation output/task/attempt/operation/execution/audit/outbox is atomic; verified permit settlement follows commit in a separate bounded-recoverable transaction. Execute stable task IDs in order **1,2,3,4,5,6,8,7,9,10,11,12,13**, because fairness must close before external Worker activation. Task 3 implementation starts only after Task 2 implementation adoption. No runtime qualification follows from design adoption.

## Task8 adopted finite-scan design

Read docs/superpowers/specs/2026-10-23-ama-phase-b-reconciliation-fairness.md and its exact approved R1/reviews. Root selects additive immutable ordinal; no migration number/freeze/apply yet. Include current model generator/dictionary/DDL/ER update for the exact reviewed column/index, preserving all other definitions. Bounded explicit SQL accounting includes two session SETs per candidate plus highwater result row; driver controls separate. Backlog-dependent sweep latency and rebuild cost are accepted only within local controlled qualification; no capacity/SLA inference. FUP stays OPEN until actual implementation and independent review.

## Task4 adopted intake/API refinement

Read docs/superpowers/specs/2026-10-23-ama-phase-b-processing-intake.md and exact Task4 R1/reviews. Mandatory accepted-result lookup precedes rejecting current intake prerequisites; one bounded fresh concurrent-winner recovery lookup after named ordinary preparation failure; preserve reauthorization/exact semantic match. Project B04 upload actions/terminal retries using actual operation/state/historical-route matrix. Java names align with Task3 final authority/preparer; no duplicate issuance. Task4 owns real legacy info/roster lossless-token extensions and server discovery/OpenAPI; Task12 actual browser. No implementation activation from this design adoption.
