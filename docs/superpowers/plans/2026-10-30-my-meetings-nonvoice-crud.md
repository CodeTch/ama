# 我的会议非语音 CRUD 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `/meetings` 及其可达工作台的全部非语音增删改查、确认、归组、移除、重试、审核和发布操作接到真实 AMA 服务，并用 CAS、持久化回读和浏览器端到端证据验证。

**Architecture:** 保留现有 Vue 3 `shared/api` facade、Spring controller/service/repository、MySQL 迁移、CAS 会话、ACL、revision/幂等、文件服务和受控 provider。按领域逐步替换 `FuturePanels` 占位并复用现有领域接口；每个领域先用失败测试固定缺口，再接通真实请求、刷新回读和权限/冲突状态。

**Tech Stack:** Vue 3、TypeScript、Vite、Playwright 1.58.2、Spring Boot 4.1.1、Java 21、MySQL、Flyway、现有 `ama-integration-harness` 固定 HTTP 8792/SMTP 2525。

**Spec:** `docs/superpowers/specs/2026-10-30-my-meetings-crud-design.md`

## Global Constraints

- 所有计划、修改、测试和验证在 `/home/tanren/ama` 的 `main` 分支完成；不创建或使用 `.worktrees`。
- 原型 `designDocs/智能会议助手1021版.html` 保持不变；页面验证使用同一 Chromium、字体、语言/时区、DPR=1，视口 1440×900 与 1024×768。
- 非语音可见 CRUD 必须经过真实 AMA API 和持久化；不使用前端 mock、静态截图、直接 SQL 写业务结果或伪造成功。
- 所有写操作按领域携带当前 ACTIVE CAS 身份、ACL、CSRF/Origin、幂等键和 revision/access-revision token；未知 ACK 只重试原键或查回原结果。
- 录音、音视频、ASR、播放、说话人识别/分离及依赖音频纠错保持 `DEFERRED_VOICE`，不得访问设备、创建媒体任务或伪造结果。
- harness 固定端口不改变；仅在 AMA 真实请求证明兼容缺口后对兄弟项目做最小 patch，并跑双方定向回归。
- 默认使用仓库已有 `account1` 可重复 fixture；外部 provider 缺失时呈现真实不可用并登记证据，不写成成功。

## Review Focus

- 占位按钮在刷新、重登、第二身份读取后仍然只改前端：每个领域任务必须有 API 调用和持久化回读测试。
- revision/幂等/未知 ACK/撤权竞争导致重复写入或错误假成功：由命令 facade、controller wire 和领域事务测试覆盖。
- provider/文件/目录不可用被页面当成成功：由受控 provider 成功、403、404、过期 token、503 和文档下载验证覆盖。
- 列表筛选、专题归组和删除后分页使用不一致快照：由共同筛选、列表重载、游标和删除后第一页回退测试覆盖。
- 语音延期控件申请设备或产生业务副作用：由浏览器 `getUserMedia`/网络/数据库无写入断言覆盖。

---

### Task 1: 固定 CAS、fixture 与测试基线

**Files:**
- Modify: `tools/dev/import_cas_integration.mjs`
- Modify: `tools/dev/local_app.py`
- Modify: `tools/dev/seed_account1_scenarios.py`
- Test: `tools/dev/tests/test_seed_account1_scenarios.py`
- Test: `backend/api/src/test/java/ama/api/CasControllerTest.java`
- Test: `backend/api/src/test/java/ama/api/CasClientTest.java`
- Create: `docs/acceptance/b/my-meetings-nonvoice-baseline-2026-10-30.md`

**Interfaces:**
- Consumes: CAS callback/service configuration, account1 fixture reset design, harness ports 8792/2525.
- Produces: repeatable owner/member/non-member/revoked/admin sessions and non-sensitive browser request logs for every later task.

- [ ] **Step 1: Add failing baseline assertions** for anonymous 401, exact service allowlist, ticket single-use, session actor, logout revocation, fixture identities and reset idempotence.
- [ ] **Step 2: Run focused suites to observe RED** with `python3 -m pytest tools/dev/tests/test_seed_account1_scenarios.py` and `./mvnw -pl backend/api -Dtest=CasControllerTest,CasClientTest test`.
- [ ] **Step 3: Implement only the missing harness/fixture wiring**; preserve fixed ports and never log tickets, passwords, cookies or keys.
- [ ] **Step 4: Run the same suites plus `node tools/dev/import_cas_integration.mjs --help`/baseline command** and record fixture ids, source hash, harness revision and first real failure.
- [ ] **Step 5: Commit** with `git add tools/dev backend/api/src/test docs/acceptance/b/my-meetings-nonvoice-baseline-2026-10-30.md && git commit -m "test: fix CAS and nonvoice fixture baseline"`.

### Task 2: 会议列表、详情、信息/名单与会议删除

**Files:**
- Modify: `contracts/openapi/meetings.yaml`
- Modify: `backend/contracts/src/main/java/ama/contract/MeetingContracts.java`
- Modify: `backend/core/src/main/java/ama/meeting/MeetingRepository.java`
- Modify: `backend/core/src/main/java/ama/meeting/MeetingService.java`
- Modify: `backend/api/src/main/java/ama/api/MeetingController.java`
- Modify: `backend/api/src/main/java/ama/api/MeetingErrors.java`
- Modify: `backend/adapters/src/main/java/ama/meeting/JdbcMeetingRepository.java`
- Modify: `backend/adapters/src/main/java/ama/meeting/MeetingEventReferences.java`
- Modify: `web/src/shared/api/meetings.ts`
- Modify: `web/src/features/meetings/FuturePanels.vue`
- Modify: `web/src/features/meetings/MeetingList.vue`
- Modify: `web/src/App.vue`
- Test: `backend/api/src/test/java/ama/api/MeetingDeleteWireTest.java`
- Test: `backend/adapters/src/test/java/ama/meeting/MeetingDeleteRepositoryTest.java`
- Test: `web/tests/unit/meetings-api.test.ts`
- Test: `web/tests/unit/meeting-delete-view.test.ts`
- Test: `web/tests/e2e/meeting-crud.spec.ts`

**Interfaces:**
- Consumes: `MeetingPage`, `Saved`, `Mutation`, current ACL/revision/idempotency and the Task 1 sessions.
- Produces: `DELETE /api/v1/meetings/{id}` with owner-only lifecycle soft delete, `createMeetingsApi.remove`, and a real delete confirmation that reloads the current list.

- [ ] **Step 1: Write RED tests** for delete body/key validation, owner success, member/outsider 403, stale revision/different-key 409, published/in-flight refusal, idempotent replay, deleted hidden from list/detail, and UI cancel/error/success behavior.
- [ ] **Step 2: Run focused Java/TS tests and confirm expected missing-route or missing-method failures.**
- [ ] **Step 3: Add the OpenAPI delete operation, contract record/method, controller parsing and error mapping; regenerate `web/src/shared/api/generated.ts` and validate the exact response shape.
- [ ] **Step 4: Implement repository transaction** with identity→subject→meeting locks, owner EDIT, lifecycle/cancel epoch/revision update, audit, `MEETING_DELETED` outbox validation and exact idempotency replay.
- [ ] **Step 5: Replace the delete `FuturePanels` placeholder** with loading/error/cancel states; refresh or return to page one after success and retain the row after failure.
- [ ] **Step 6: Run focused tests, `npm --prefix web run typecheck`, and Playwright `meeting-crud.spec.ts` against a real local app/fixture; save API, SQL readback, 403/409 and screenshot evidence.
- [ ] **Step 7: Commit** with `git add contracts/openapi/meetings.yaml backend web/src/shared/api web/tests && git commit -m "feat: connect meeting CRUD and deletion"`.

### Task 3: 文本导入、上传草稿与背景材料

**Files:**
- Modify: `web/src/features/import/ImportDialogView.vue`
- Modify: `web/src/features/import/UploadImportView.vue`
- Modify: `web/src/features/import/UploadImportPanel.vue`
- Modify: `web/src/features/import/KnowledgeMaterialPicker.vue`
- Modify: `web/src/features/import/MaterialImportSection.vue`
- Modify: `web/src/features/import/useImportDraft.ts`
- Modify: `web/src/features/import/useMaterials.ts`
- Modify: `web/src/shared/api/import-drafts.ts`
- Modify: `web/src/shared/api/materials.ts`
- Modify: backend import/material controllers only where a real request proves a contract gap
- Test: `web/tests/unit/import-dialog-view.test.ts`
- Test: `web/tests/unit/upload-import-view.test.ts`
- Test: `web/tests/unit/use-materials.test.ts`
- Test: relevant Java API/adapter tests and `tools/dev/import_cas_integration.mjs`

**Interfaces:**
- Consumes: saved owner meeting from Task 2, upload/material APIs, file bytes and provider fixtures.
- Produces: real text import queue, draft save/recovery/discard, local/background material add/remove/retry and version-bound readback.

- [ ] **Step 1: Add RED tests** for file ordering, accepted text formats, draft reload/discard, upload retry/abort, material selection/removal, duplicate version rejection and no-partial-output failures.
- [ ] **Step 2: Run focused Web/import/Java tests and capture the current placeholder failures.
- [ ] **Step 3: Wire existing facades into every visible dialog action**, preserving command journals, actor scoping and exact response validation.
- [ ] **Step 4: Exercise controlled provider success, 403, missing version, stale token and upload hash mismatch; add only proven compatibility fixes.
- [ ] **Step 5: Run browser upload/save/refresh/remove/retry flows and persist file/hash/draft/material evidence.
- [ ] **Step 6: Commit** with `git add web/src/features/import web/src/shared/api/{import-drafts,materials}.ts web/tests backend && git commit -m "feat: connect import and material CRUD"`.

### Task 4: 文本转写编辑、来源与确认

**Files:**
- Modify: `web/src/features/transcript/TranscriptEditor.vue`
- Modify: `web/src/features/transcript/TranscriptForm.vue`
- Modify: `web/src/features/transcript/TranscriptPlayer.vue` (deferred media-safe state only)
- Modify: `web/src/shared/api/transcripts.ts`
- Modify: `web/src/shared/api/transcript-import.ts`
- Test: `web/tests/unit/transcript-editor.test.ts`
- Test: `web/tests/unit/transcripts-api.test.ts`
- Test: `web/tests/e2e/transcript-crud.spec.ts`
- Test: relevant backend transcript controller/adapter suites

**Interfaces:**
- Consumes: completed text import/material snapshot from Task 3 and workspace navigation.
- Produces: text-only segment edit/replace/flag/save/confirm/history/source UI with revision conflict recovery and no audio side effects.

- [ ] **Step 1: Add RED tests** for edit/replace/flag command bodies, IME-safe input, save conflict, unknown ACK retry, history/source read authorization and confirmation gate.
- [ ] **Step 2: Run the focused suite and verify failures are caused by missing interaction or contract wiring.
- [ ] **Step 3: Connect editor actions to existing transcript command facade and render real API errors/recovery states.
- [ ] **Step 4: Add browser assertions that text edits survive refresh and audio/player APIs are never called for TXT-only fixtures.
- [ ] **Step 5: Run Java/Web/E2E checks and save transcript/revision/readback evidence.
- [ ] **Step 6: Commit** with `git add web/src/features/transcript web/src/shared/api/{transcripts,transcript-import}.ts web/tests backend && git commit -m "feat: connect text transcript CRUD"`.

### Task 5: 纪要、模板、候选、敏感信息、审阅与行动负责人

**Files:**
- Modify: `web/src/features/minutes/MinutesWorkspace.vue`
- Modify: `web/src/features/minutes/MinutesGenerationPanel.vue`
- Modify: `web/src/features/minutes/MinutesTemplateToolbar.vue`
- Modify: `web/src/features/minutes/MinutesSettings.vue`
- Modify: `web/src/features/minutes/MinutesSensitive.vue`
- Modify: `web/src/features/minutes/ActionOwnerSelection.vue`
- Modify: `web/src/shared/api/minutes-generation.ts`
- Modify: `web/src/shared/api/minutes-saved.ts`
- Modify: `web/src/shared/api/minutes-inspection.ts`
- Modify: `web/src/shared/api/minutes-wire.ts`
- Test: `web/tests/unit/minutes-components.test.ts`
- Test: `web/tests/unit/minutes-generation.test.ts`
- Test: `web/tests/unit/minutes-editor.test.ts`
- Test: `web/tests/unit/action-owner-selection.test.ts`
- Test: `web/tests/unit/minutes-review-fixes.test.ts`
- Test: corresponding Java API/adapter/integration suites

**Interfaces:**
- Consumes: confirmed text transcript from Task 4, current minutes/generation/review schemas and owner/member scope.
- Produces: real template CRUD, generation acceptance, candidate decision, body/element editing, exclusions, review history and action-owner save/readback.

- [ ] **Step 1: Add RED tests** for each currently disabled/placeholder action, unsaved-draft protection, candidate adoption/rejection, template name conflict, exclusion CAS, review authorization and owner lookup failure.
- [ ] **Step 2: Run focused suites and record each missing path before implementation.
- [ ] **Step 3: Wire existing command journals/facades and implement only missing backend contracts proven by the tests; preserve immutable histories and no-provider failures.
- [ ] **Step 4: Verify reload, conflict reapply/discard, member read-only behavior, revoked identity refusal and no fabricated model output.
- [ ] **Step 5: Run browser minutes/review flows and persist body, element, template, exclusion, review and owner readback evidence.
- [ ] **Step 6: Commit** with `git add web/src/features/minutes web/src/shared/api/minutes-* web/tests backend && git commit -m "feat: connect minutes and review CRUD"`.

### Task 6: 最终确认、冻结、发布、会议库与正式文档

**Files:**
- Modify: `web/src/features/minutes/PublicationPanel.vue`
- Modify: `web/src/features/minutes/MinutesWorkspace.vue`
- Modify: `web/src/shared/api/publications.ts`
- Modify: `web/src/shared/api/minutes-saved.ts`
- Modify: relevant `backend/api`, `backend/core`, `backend/adapters`, `backend/file-service` publication/final controllers and adapters only for proven gaps
- Test: `web/tests/unit/publication-state.test.ts`
- Test: `web/tests/e2e/publication-crud.spec.ts`
- Test: relevant `backend/api/src/test`, `backend/adapters/src/test`, `backend/integration-tests/src/test`

**Interfaces:**
- Consumes: reviewed minutes and action-owner state from Task 5; controlled destination/provider and file read APIs.
- Produces: final confirmation/freeze, destination library/folder selection, publish/retry/status lookup, official document read-only/download when provider supports it.

- [ ] **Step 1: Add RED tests** for final readiness gates, frozen write refusal, destination pagination, 202/unknown ACK lookup, failed retry, member/撤权 access and document byte/hash validation.
- [ ] **Step 2: Run focused Java/Web tests and confirm missing behavior.
- [ ] **Step 3: Connect publication UI to existing facade; implement minimum backend/provider compatibility only for observed requests.
- [ ] **Step 4: Verify status and document readback; if provider is unavailable, render truthful unavailable state and record the environment block.
- [ ] **Step 5: Run browser confirm→publish→refresh→document read flow with owner/member/revoked sessions and save provider/request/file evidence.
- [ ] **Step 6: Commit** with `git add web/src/features/minutes/PublicationPanel.vue web/src/shared/api/publications.ts web/tests backend && git commit -m "feat: connect final confirmation and publication"`.

### Task 7: 专题、归组、会议库导航与反馈

**Files:**
- Modify: `web/src/features/meetings/FuturePanels.vue`
- Modify: `web/src/features/meetings/MeetingList.vue`
- Modify: `web/src/App.vue`
- Modify: `web/src/features/meetings/FuturePage.vue`
- Create or modify: `web/src/shared/api/topics.ts`, `web/src/shared/api/feedback.ts`, `web/src/shared/api/library.ts` as required by current contracts
- Create or modify: corresponding `contracts/openapi`, `backend/api`, `backend/core`, `backend/adapters` files only where no existing contract exists
- Test: `web/tests/unit/meeting-list-view.test.ts`
- Test: new `web/tests/unit/topic-crud.test.ts`, `feedback-crud.test.ts`, `library-navigation.test.ts`
- Test: new or existing Java wire/adapter tests for topic/group/feedback writes

**Interfaces:**
- Consumes: meeting owner and published/read scopes from Tasks 2/6.
- Produces: real topic create/edit/delete, meeting single/batch grouping/remove, library navigation and feedback create/read/review/revoke with owner/role boundaries.

- [ ] **Step 1: Inventory current migrations/OpenAPI/controllers and write RED tests for every visible placeholder action; use the existing domain rule if present, otherwise define the smallest new schema/service in the test.
- [ ] **Step 2: Run focused tests and classify each failure as existing facade wiring, missing backend contract, or unavailable provider.
- [ ] **Step 3: Implement real commands with owner/role ACL, CAS/idempotency and persistence; replace `FuturePanels` placeholders with loading/cancel/error/retry states.
- [ ] **Step 4: Verify group/list consistency, empty-topic delete rule, feedback status persistence, second-user denial and refresh readback.
- [ ] **Step 5: Run browser flows and save API/DB/pixel evidence.
- [ ] **Step 6: Commit** with `git add web/src/features/meetings web/src/shared/api contracts/openapi backend web/tests && git commit -m "feat: connect topics library and feedback CRUD"`.

### Task 8: 语音延期边界、像素状态与全链路 E2E

**Files:**
- Modify: `web/src/features/recording/RecordingPage.vue`
- Modify: `web/src/features/recording/useRecorder.ts`
- Modify: `web/src/features/transcript/TranscriptPlayer.vue`
- Modify: `web/src/features/meetings/MeetingList.vue`
- Modify: `web/src/features/meetings/FuturePanels.vue`
- Modify: `web/tests/e2e/shell.spec.ts`
- Modify: `web/tests/visual/shell.spec.ts`
- Create: `web/tests/e2e/my-meetings-nonvoice-flow.spec.ts`
- Create: `docs/acceptance/b/my-meetings-nonvoice-e2e-2026-10-30.md`
- Modify: `docs/acceptance/b/b-followup-page-crud-matrix-2026-10-30.md`

**Interfaces:**
- Consumes: completed domain slices from Tasks 1–7 and the unchanged prototype reference.
- Produces: one browser chain from anonymous CAS login through meeting/import/material/transcript/minutes/final/publication/topic/feedback and logout, plus verified deferred voice states and pixel evidence.

- [ ] **Step 1: Add RED E2E/visual assertions** for each required viewport/state, no-device/no-media side effects, login/logout, and full non-voice CRUD flow.
- [ ] **Step 2: Run the focused browser suites to capture the current failures and baseline diffs.
- [ ] **Step 3: Align only the DOM/CSS/text/state transitions revealed by the reference and wire any remaining parent-child refresh or route behavior.
- [ ] **Step 4: Run fixed-harness browser flow with owner/member/non-member/revoked/admin sessions, API log capture, DB readback and provider/file hashes.
- [ ] **Step 5: Run pixel capture/diff at 1440×900 and 1024×768 for empty, non-empty, dialog, drawer, error, read-only and deferred voice states; update the matrix only with fresh source/harness evidence.
- [ ] **Step 6: Run the full verification commands: `python3 tools/checks/run.py all`, `npm --prefix web run typecheck`, `npm --prefix web run build`, `npm --prefix web run check:api`, `./mvnw -B -ntp test`, focused Playwright E2E/visual commands and the fixed-port integration report.
- [ ] **Step 7: Commit** with `git add web tests tools docs contracts backend && git commit -m "test: verify my meetings nonvoice end to end"`.

## Self-review

- **Spec coverage:** identity/home (Task 1–2), meeting CRUD (Task 2), import/material (Task 3), transcript (Task 4), minutes/review (Task 5), final/publication (Task 6), topic/library/feedback (Task 7), deferred voice and pixel/E2E (Task 8).
- **Type consistency:** all later tasks consume the prior task's persisted meeting, workspace, transcript, minutes and publication states; new facades must export exact command and response types before UI tasks use them.
- **Review focus coverage:** API/persistence truth (Tasks 2–7), provider boundary (Tasks 3/6/7), ACL/fixture (Task 1 plus every domain), pagination (Tasks 2/7), voice no-side-effect (Task 8).
- **Proportion:** tasks state file boundaries, test names/behaviors, exact commands and handoff interfaces without prescribing implementation bodies beyond domain lock/response invariants.
