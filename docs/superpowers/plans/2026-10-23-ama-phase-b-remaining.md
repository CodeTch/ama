> **2026-10-28 当前调度（AMA-DEMO-FIRST-20261028 R2）**：执行[Demo 优先调整计划](2026-10-28-ama-demo-first-adjustment.md)。Tasks 1–10 和 B-L1～4 已有采纳，不重做；DEMO-01～05 按业务片段交付 M1-Demo，Task11/12 深度核心证明与 Task13/B-L5 由 B-QUAL 汇合，达到 M1-B 才正式验收。S3、双 MySQL、双活未完实现/配置/验证及专属依赖最后 F；本轮仅文档。

## 当前任务归属（覆盖下文旧双后端门槛）

| 范围 | 当前处理 |
| --- | --- |
| 已采纳 Tasks 1–10、B-L1～4 | 保留原完成/采纳结论及限定证据；旧步骤未勾选不表示重新开发 |
| Task5 local/S3 Worker 与恢复 | 已有实现保留；DEMO-02/05 接通任务与常规重开，本地深入恢复最晚 B-QUAL/M1-B；未完 S3 专属恢复归 DEP-S3/F |
| Task11/12/13、B-L5 | DEMO-01 会议/信息→02 上传/转写→03 生成/审阅→04 发布/文档→05 完整演示；深度预算/锁序/事务/恢复按 B 摘要归 B-QUAL，未完 S3 浏览器/差异/部署验证归 F |
| 旧 B-L5 批次与历史权限 | 已关闭且不得重用；日常本地环境按实际授权和归属持续复用，不强制每次新建封存；超出既有授权的资源另行具体处理，本次不给运行授权 |

下方 10-27 及更早状态段是历史原件；其“下一步 B-L1”“ACTIVE/PAUSED”和代理调度不覆盖本段与最新 current-state 的规划字段。最新事实见 B 摘要和 Demo 接续文档。

---

# PAUSED_BY_USER — 2026-10-27 本批已完成，等待下次“继续”

最新用户指令要求完成当前批次后停止。信息编辑状态层R3已由同一GPT-6 Astra xHigh独立reviewer限定采纳，166项Web单元测试通过、类型检查通过；本批无待修复发现。B阶段仍未全部完成，未开启后续批次。主Agent唯一实现、同一独立reviewer，不新增其他Agent。

详细接续文档：`.superpowers/sdd/2026-10-23-ama-phase-b-remaining/handoff-paused-20261027.md`；权威入口 `RESUME_LATEST.md` 与 `current-state.json`。下次用户“继续”即从已批准计划的未完成项恢复，先信息持久化/真实编辑与名单确认。保留当前工作树/未提交代码/证据；旧runtime授权不自动续期。以下状态也按历史保留；当前执行以 2026-10-28 入口为准。

---

# ACTIVE — 按用户“继续任务”完成全部 B 阶段

2026-10-27 用户已明确恢复并要求完成 B 阶段。主 Agent 端到端实现；唯一复审 Agent 为 `/root/phase_b_independent_review`（GPT-6 Astra xHigh），该替代审查者已由用户授权。当前实现、复审与未完成项以 `.superpowers/sdd/2026-10-23-ama-phase-b-remaining/current-state.json` 和 `RESUME_LATEST.md` 为准。下方暂停记录仅保留历史，不再作为当前停止指令。B 阶段尚未整体完成。

## 历史暂停记录（已恢复）

当时用户要求完成当前批次后停止；该历史暂停已被后续明确“继续任务”解除。权威暂停记录：`docs/acceptance/development-pause-b5-partial-2026-10-26.md`，SHA256 `17a054f578cfdc6bf82c6c0e3dc2b44bbfb6449422d1e181ff2c075be7210951`。只有明确“继续任务”后恢复。

部分checkpoint `task-11-b5-partial-pause-source-checkpoint-20261026.json` SHA256 `f6e1fc7aceb837f2e26d88af210801f7f4c2a76c940bf30d6bc9770e5184113f`；1189源码/46路径相对B4变化/277证据。最终53Java全通过、增量compile0且同源码映射。B5未完成、未整体审查，无release/window。下一步按暂停记录第4节：全mutator矩阵/共享预算核对/真实组合三GET/特殊publication路径/相关回归后完整B5同一审查者；不是重做B2/B3。保留O6 UNKNOWN、SQLite3/3与全部旧consumed窗口。主唯一writer，SAME reviewer空闲，不新增Agent。

# Phase B remaining implementation plan

> Historical execution template; current scheduling follows AMA-DEMO-FIRST-20261028 and AMA-WORKFLOW-20261027 (inline feature batches). This documentation change does not start implementation.

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

Consume real registered task and immutable original archives/actual pinned bytes, strict parser ordered files/aggregate budgets. Prepared atomic RAW revision/segments/task result, rollback failure, no partial writes. Retain adopted real queue/WorkerLoop/MySQL/local+S3 and fresh-JVM evidence. Demo requires basic persistence/task recovery; remaining deep local recovery belongs to B-QUAL by M1-B. Unfinished S3-specific implementation/verification belongs to DEP-S3/F.
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

Server projections/task read/controller/API errors; real file upload/import/save/confirm/generate/candidate/review/final/publish pages per approved prototype anchors. Draft preservation/409/retry/idempotency/read-only flows; owner/member/outsider/admin. Explicit test identity/harness only. Follow R2 DEMO-01..05 vertical slices, page matrix, local MySQL/files/single-host browser loop and representative recovery for M1-Demo; deep assertions remain B-QUAL/M1-B. Controlled model output is not real AI quality; actual platform tests remain B-02/07/08 when available. Unfinished S3-specific end-to-end and recovery moves to DEP-S3/F. No frontend-only permission checks.
### Task 13: Final integrated verification, fresh whole independent review, records/delivery.

DEMO-05 delivers the first demonstrable workflow without claiming Phase B complete. B-QUAL then closes the applicable local core obligations, runs affected final gates and whole-branch review using the existing consolidated policy; preserve failures and exact source/evidence hashes. Bounded fixes/re-review, without a new reviewer solely for replanning. Maintain docs/acceptance/b/summary.md as the current disposition table and update DEVELOPMENT_HANDOFF and development-resume. Adopt reviewed work to the active development branch only under actual authorization; preserve old stash/trees. Record M1-Demo, formal local M1-B, actual platform capability and deployment qualification separately. Stop after B; no automatic C/D/E/F.

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

Read the spec's full Normative refinement R1 before each Task2..13 brief. It supersedes ambiguous epoch/retention/API shorthand above. DR-01: sticky real0-only guard; explicitadopt nooriginalepoch equality. DR-02: registered authentic stale-only immutable retention; STALE nonblocking/nonadoptable, current editor-only PENDING; finalrace serialized. DR-03: explicit task->operation heartbeat/complete ownershiphandoff. DR-04: supported v2 graph-copy/edit/conversion matrix, full remapping/no reviewcredit, deterministic required SOURCE_SUPPORT/ACTION_FIELDS reviewset. DR-05: allbrowsermutations exact token/legacy normalization before idempotencydigest. DR-06: producer-owned authoritative read/discovery table plus actual TLS E2E; local browser is required for Demo, unfinished S3 browser qualification is retained under DEP-S3/F. All six original findings preserved; bounded independent rereview required before dependent runtime activation.

## Adopted authority/external refinement

The normative additive design is `docs/superpowers/specs/2026-10-23-ama-phase-b-authority-external-protocols.md`, backed by exact independently reviewed artifacts and hashes. It supersedes compact Task 7 permit-atomic wording: observation output/task/attempt/operation/execution/audit/outbox is atomic; verified permit settlement follows commit in a separate bounded-recoverable transaction. Execute stable task IDs in order **1,2,3,4,5,6,8,7,9,10,11,12,13**, because fairness must close before external Worker activation. Task 3 implementation starts only after Task 2 implementation adoption. No runtime qualification follows from design adoption.

## Task8 adopted finite-scan design

Read docs/superpowers/specs/2026-10-23-ama-phase-b-reconciliation-fairness.md and its exact approved R1/reviews. Root selects additive immutable ordinal; no migration number/freeze/apply yet. Include current model generator/dictionary/DDL/ER update for the exact reviewed column/index, preserving all other definitions. Bounded explicit SQL accounting includes two session SETs per candidate plus highwater result row; driver controls separate. Backlog-dependent sweep latency and rebuild cost are accepted only within local controlled qualification; no capacity/SLA inference. FUP stays OPEN until actual implementation and independent review.

## Task4 adopted intake/API refinement

Read docs/superpowers/specs/2026-10-23-ama-phase-b-processing-intake.md and exact Task4 R1/reviews. Mandatory accepted-result lookup precedes rejecting current intake prerequisites; one bounded fresh concurrent-winner recovery lookup after named ordinary preparation failure; preserve reauthorization/exact semantic match. Project B04 upload actions/terminal retries using actual operation/state/historical-route matrix. Java names align with Task3 final authority/preparer; no duplicate issuance. Task4 owns real legacy info/roster lossless-token extensions and server discovery/OpenAPI; Task12 actual browser. No implementation activation from this design adoption.

## Task3 adopted pre-gate complete-read refinement

Read docs/superpowers/specs/2026-10-23-ama-phase-b-pre-gate-read.md and exact original/R1/R2/reviews. This closes the producer-hash-before-identities API gap and PRD-01 authorization-precedence residual. Fixed final selector TYPE permission floor is independent of optional provider resolution; entire dispatch failure is private until fresh READ/owner EDIT on same gate. Data-only preparation never grants source/byte authority; legacy-only behavior preserved. Actual implementation/tests independent gate remains pending.

## Task6 adopted saved-content refinement

Read docs/superpowers/specs/2026-10-23-ama-phase-b-saved-transcript.md and exact R1/reviews. Owner stored-history text/marks inspection is distinct from current original-byte eligibility; new-business/current-member/source predicates remain. Explicit snapshot/outside-archive/final phases and retained metadata/parent/child locks bound LONGTEXT before transfer, including final comparison and empty marks. Actual shared issuer/RAW allocation/qualified correction/budgets and later candidate/publication integration remain source activation gates; design approval is not implementation qualification.

## Task5 adopted confined completion/renew-drain refinement

Read docs/superpowers/specs/2026-10-23-ama-phase-b-import-worker.md and exact original+R1/reviews. New production success uses queue-private genuine after-task scope/inert carrier/same-call writer+validator; old unrelated callbacks remain. Drain selected ordinary renewal without DB locks before completion/Task7 MARKING, JDBC outside monitor; state-aware exactly-once attempt transitions. Under R2, the genuine local pipeline and basic persistence/protection are Demo obligations; deep transport/authority/concurrency/restart proofs remain B-QUAL obligations by M1-B, brought forward for actual defects or changed high-risk paths. Unfinished S3-specific transport/recovery qualification moves to DEP-S3/F. Already adopted evidence is retained.

## Task9 adopted minutes-generation refinement

Read docs/superpowers/specs/2026-10-23-ama-phase-b-minutes-generation.md and exact original/R1/reviews/shared-rank ruling. Enqueue freezes current r/e without content advance or debt clearing; final/publication complete outstanding-generation closure is mandatory. Task9 owns real first-settings/template origin, Task10 later settings and shared PENDING disposal. Actual independent history allocation and original fifteen-profile graphs, private external authority/durable provider/invalid-output/permit-UNKNOWN contracts remain. Product API version/root OpenAPI is corrected; shared minutes rank follows established Task3/6 before sources/tasks. Design adoption supplies no runtime qualification.

## Task10 adopted saved-minutes/editor/review refinement

Read docs/superpowers/specs/2026-10-23-ama-phase-b-minutes-editor.md and exact independently reviewed original/shared-rank ruling. Actual tab editor-intent ACK precedes edits; first-save/PENDING/STALE differ by real fences, explicit adoption preserves provenance with full graph remapping and no review credit. Shared complete PENDING contribution applies every version writer using declaration then one globally ordered union; exact whole-source/action reviews and independent history pairing remain. Upstream confined writer/facade reconciliation and Task11 publication/outstanding-work integration are mandatory source/activation gates, not qualified by this design pass.

## Task11 adopted final/publication refinement

Read docs/superpowers/specs/2026-10-23-ama-phase-b-final-publication.md and exact independently reviewed original/R1/closure/reviews. Both design findings are closed. Publication submission remains logical proposal only: actual post-Task8 exact DDL review/allocation/freeze/separate ACK/application and model synchronization still required. Genuine Task7 private queue-hook/source composition, all-mutator freeze, immutable acceptance/old acknowledgment/local-never-marked marker barriers, authenticated caller/delegation and dual-ACL official document relay remain source/runtime evidence gates. No source, schema or runtime qualification follows from this design adoption.

## Task12 adopted web-loop refinement

Read docs/superpowers/specs/2026-10-23-ama-phase-b-web-loop.md and exact original/R1/closure/reviews. T12DR01 pretransfer protection is closed at design level. Actual Task12/B03 protected projection ownership, same-holder producer/rank/full-set integration, cumulative metadata/content/copy/response budgets and new-path MySQL no-transfer/race instrumentation remain technical requirements; adopted implementation is not reopened solely by these historical source-gate descriptions. R2 requires the actual local browser loop, page matrix, basic four-identity/protection/recovery evidence and applicable change review for M1-Demo, then deeper MySQL/fresh-process/crash evidence by M1-B. Use the verified local origin without weakening transport/authentication protections. Unfinished S3 browser/recovery and deployment-specific qualification remains DEP-S3/F. Design adoption alone supplies no runtime qualification.
