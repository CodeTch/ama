# B 阶段后续逐页像素、非语音 CRUD 与 CAS 联调实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 B 阶段后续工作中完成原型全部可达页面的 1:1 像素复刻，实装所有非语音可见增删改查，并用固定端口的 `ama-integration-harness` 完成 CAS 与相关接口联调。

**Architecture:** 继续使用现有 Vue/Spring/MySQL、文件服务、Worker 和受控外部适配器。页面先按最终原型建立逐状态矩阵，再以真实 AMA API 驱动 UI；浏览器联调通过固定端口 harness 提供 CAS 和被证明缺失的外部兼容端点。harness 是可重复的模拟器，不定义 AMA 的业务语义。

**Tech Stack:** Vue 3、TypeScript、Playwright/Chromium、Pillow 或现有像素差工具、Java 21/Spring、MySQL、现有文件与任务服务、`ama-integration-harness` 的 FastAPI/SQLite 测试服务。

**Spec:** `docs/superpowers/specs/2026-10-30-ama-b-followup-pixel-crud-cas-design.md`

## Global Constraints

- 所有计划、修改、测试和验证在 `/home/tanren/ama` 的 `main` 分支完成；不创建或使用 `.worktrees`。
- 原型 `designDocs/智能会议助手1021版.html` 保持不变，基准 SHA256 为 `ab21d0b73f4e48bbf701ee8bca5e958940b61f6e6f680d4ced02de0ce4bab867`。
- 所有页面、弹窗、抽屉、滚动和固定底栏使用同一 Chromium、字体、语言/时区、DPR=1，并在 1440×900、1024×768 留存完整 reference/actual/diff。
- 非语音可见 CRUD 必须经过真实 AMA API 和持久化；不使用前端 mock、静态截图、直接 SQL 写业务结果或伪造成功。
- 语音相关业务（录音、音视频、ASR、播放、说话人识别/分离、依赖音频的自动纠错）本轮只做像素静态/延期状态，标记 `DEFERRED_VOICE`。
- harness 的 HTTP `8792`、SMTP `2525` 固定端口设计不修改；兄弟项目只在 AMA 真实请求证明兼容缺口后做最小同步修复。
- 旧计划和证据保留为历史；本计划是 2026-10-30 之后的当前执行入口。任何新证据必须绑定当前源码、schema、装配和测试版本。
- 密码、TGT、ST、Bearer、私钥和会话文件不得进入日志、仓库或公开验收证据。

## Review Focus

- 原型末尾 override、字体、图标、滚动高度或固定底栏造成的整页像素偏差；由页面矩阵和精确 diff 测试覆盖。
- 可见按钮只改前端状态、刷新后丢失或删除绕过领域规则；由每个资源的 CRUD 回读和权限测试覆盖。
- CAS callback/service 白名单、ticket 重放、退出撤销、harness 固定端口或跨项目状态不一致；由 AMA 与 harness 双侧测试和真实浏览器日志覆盖。
- 业务失败、403/404/409、过期版本、未知 ACK 或材料撤权被渲染为成功；由真实负例和恢复测试覆盖。
- 语音延期控件误申请设备、创建伪任务或被误记为已实现；由无设备访问断言和 `DEFERRED_VOICE` 矩阵覆盖。

### Task 1: 冻结当前范围与页面/CRUD 矩阵

**Files:**
- Create: `docs/superpowers/specs/2026-10-30-ama-b-followup-pixel-crud-cas-design.md`
- Create: `docs/acceptance/b/b-followup-page-crud-matrix-2026-10-30.md`
- Modify: `docs/acceptance/b/prototype-parity-validation-2026-10-29.md`
- Modify: `docs/acceptance/b/summary.md`

**Interfaces:**
- Consumes: 原型最终 HTML、现有 Vue 路由/组件、AMA OpenAPI/领域接口、现有 CAS 导入报告。
- Produces: 每个页面/状态的 `IN_SCOPE`、`DEFERRED_VOICE`、`OPEN`、`PASS` 状态；每个可见动作到 API、存储事实、权限和负例的映射。

- [x] **Step 1: 建立页面清单**：从原型最终渲染和 Vue 路由登记登录、列表、详情、抽屉、弹窗、纪要、发布、专题、模板、反馈、知识库及语音页的入口和覆盖层。
- [x] **Step 2: 建立 CRUD 映射**：对每个非语音按钮/菜单/行操作记录 HTTP 方法、路径、请求字段、revision/幂等要求、成功持久化事实、删除语义和最小负例。
- [x] **Step 3: 标记语音延期**：将录音、音视频、ASR、播放、说话人识别和依赖音频的纠错标成 `DEFERRED_VOICE`，同时保留其静态像素状态用例。
- [x] **Step 4: 运行矩阵自检**：拒绝无页面入口、无 API 映射、只有前端状态或把延期功能标为 PASS 的条目；输出未覆盖清单。

### Task 2: 固定端口 CAS harness 联调基线

**Files:**
- Modify: `backend/api/src/main/java/ama/api/CasClient.java`
- Modify: `backend/api/src/main/java/ama/api/CasAuthenticationController.java`
- Modify: `tools/dev/import_cas_integration.mjs`
- Modify: `/home/tanren/ama-integration-harness/backend/...` only when an observed AMA request proves a compatibility gap
- Test: `backend/api/src/test/java/ama/api/CasClientTest.java`
- Test: `backend/api/src/test/java/ama/api/CasControllerTest.java`
- Test: `/home/tanren/ama-integration-harness/backend/tests/test_provider_api.py`

**Interfaces:**
- Consumes: `AMA_CAS_ORIGIN`, exact `CAS_SERVICE_URL`, current session/CSRF contracts, all B page API calls made under that session, and harness fixed HTTP `8792`/SMTP `2525`.
- Produces: real browser CAS login/logout, one-time ticket validation, identity synchronization, service allowlist behavior, and recorded material/import compatibility outcomes.

- [ ] **Step 1: Run baseline against fixed harness** with `FIXED_PORTS=1`, exact callback allowlist and a fresh report; record the first actual failure rather than assuming harness behavior.
- [ ] **Step 2: Add or update failing AMA/harness regression** for the observed request, status, payload shape or callback mismatch. Do not add a broad success stub.
- [ ] **Step 3: Implement the smallest fix** on the owning side; if the request proves the simulator is missing a compatible route or response, update the sibling harness in place without resetting its parallel work or changing ports.
- [ ] **Step 4: Run both focused suites** and the real browser flow. Verify anonymous 401, exact CAS redirect/service, ticket single-use, session actor, logout 401, permission failures and no page errors.
- [ ] **Step 5: Save a report** containing non-sensitive request/status evidence, harness revision, AMA source hash and the fixed-port configuration.

### Task 3: 会议列表、详情和信息/名单 CRUD 与像素复刻

**Files:**
- Modify: `web/src/App.vue`
- Modify: `web/src/features/meetings/MeetingList.vue`
- Modify: `web/src/features/meetings/MeetingDetail.vue`
- Modify: `web/src/features/meetings/MeetingInfoDrawer.vue`
- Modify: `web/src/features/meetings/MeetingInfoForm.vue`
- Modify: `web/src/features/meetings/MeetingCreatePanel.vue`
- Modify: `web/src/features/meetings/useMeetingList.ts`
- Modify: `web/src/shared/styles/prototype.css`
- Test: `web/tests/unit/meeting-list-view.test.ts`
- Test: `web/tests/unit/meeting-info-persistence.test.ts`
- Test: `web/tests/unit/meeting-create-flow.test.ts`
- Test: existing `tools/dev/prototype_home_pixel.mjs`, `tools/dev/prototype_home_behaviour.mjs`, `tools/dev/prototype_parity_info_drawer.mjs`

**Interfaces:**
- Consumes: Task 1 matrix and Task 2 authenticated browser session.
- Produces: pixel-aligned list/detail/info states and real create/read/update/delete or domain-equivalent archive/withdraw behavior with refresh and permission evidence.

- [ ] **Step 1: Capture RED screenshots and CRUD failures** for empty, non-empty, search, filter, pagination, create, save, cancel, conflict, roster draft/confirm/revoke and delete/withdraw states.
- [ ] **Step 2: Align DOM/CSS/text/icon structure** to the final prototype without changing the original HTML or replacing domain components with fixture-only markup.
- [ ] **Step 3: Implement missing non-voice CRUD paths** through existing AMA command/facade contracts; persist, audit and authorize writes according to the domain rules.
- [ ] **Step 4: Run focused Web tests and real browser checks** against fixed harness/CAS and current MySQL; verify reload and second-user read/deny outcomes.
- [ ] **Step 5: Store exact pixel diffs and update the matrix** only after the reference and actual are from the same fixture and source hash.

### Task 4: 文本导入、材料和转写稿 CRUD 与像素复刻

**Files:**
- Modify: `web/src/features/import/ImportDialogView.vue`
- Modify: `web/src/features/import/UploadImportView.vue`
- Modify: `web/src/features/import/UploadImportPanel.vue`
- Modify: `web/src/features/import/KnowledgeMaterialPicker.vue`
- Modify: `web/src/features/import/MaterialImportSection.vue`
- Modify: `web/src/features/import/useImportDraft.ts`
- Modify: `web/src/features/import/useMaterials.ts`
- Modify: `web/src/features/transcript/TranscriptEditor.vue`
- Modify: `web/src/features/transcript/TranscriptForm.vue`
- Modify: `web/src/shared/api/import-drafts.ts`
- Modify: `web/src/shared/api/materials.ts`
- Test: existing import/material/transcript Web suites and `tools/dev/import_cas_integration.mjs`
- Test: relevant Java API/adapter and harness material contract suites

**Interfaces:**
- Consumes: saved meeting/roster from Task 3, fixed CAS session, import draft/material APIs and actual file bytes.
- Produces: pixel-aligned text import/material/transcript pages; real save/reload/remove/retry/reorder/confirm behavior and honest provider/version failures.

- [ ] **Step 1: Capture RED for dialog, queue, material picker, draft recovery and transcript states** at both required viewports.
- [ ] **Step 2: Implement non-voice CRUD** for text files, import drafts, background materials, text transcript edits/replacement/confirmation and domain-approved removal/retry; never mix background material into transcript RAW.
- [ ] **Step 3: Exercise fixed harness material reads** with success, 403, missing version and stale token; if a real request exposes a harness mismatch, add the smallest sibling regression/fix and retain logs.
- [ ] **Step 4: Run browser flows** for upload, save, refresh, remove/retry, import acknowledgement and transcript save/confirm, including malformed input and no-partial-output cases.
- [ ] **Step 5: Update pixel and CRUD evidence** only after API response, persistent readback and page diff all pass.

### Task 5: 纪要、审阅、模板、专题、反馈和发布 CRUD 与像素复刻

**Files:**
- Modify: `web/src/features/minutes/MinutesWorkspace.vue`
- Modify: `web/src/features/minutes/MinutesGenerationPanel.vue`
- Modify: `web/src/features/minutes/MinutesTemplateToolbar.vue`
- Modify: `web/src/features/minutes/MinutesSettings.vue`
- Modify: `web/src/features/minutes/MinutesSensitive.vue`
- Modify: `web/src/features/minutes/PublicationPanel.vue`
- Modify: `web/src/features/meetings/FuturePage.vue`
- Modify: `web/src/features/meetings/FuturePanels.vue`
- Modify: corresponding `web/src/shared/api/*` and backend API/core/adapters contracts where the matrix proves a missing non-voice operation
- Test: existing minutes/publication/meeting Web suites plus focused API/adapter/integration tests for each newly wired operation

**Interfaces:**
- Consumes: confirmed text transcript/material snapshots and Task 2 session/ACL evidence.
- Produces: pixel-aligned minutes/review/final/publication/topic/template/feedback/meeting-library states and real CRUD or domain-equivalent state transitions.

- [ ] **Step 1: Capture RED for each page, tab, modal, confirmation, empty/error/readonly state and each visible CRUD control.**
- [ ] **Step 2: Add the smallest missing AMA command/read contracts** for template, topic/group, feedback, material/library or publication actions; include authorization, revision, idempotency, audit and deletion semantics.
- [ ] **Step 3: Implement Vue state recovery** so refresh, route re-entry, conflict and provider failure reflect server state and never overwrite unsaved text.
- [ ] **Step 4: Run owner/member/non-member/platform-admin browser cases** against real CAS sessions and controlled provider responses; verify published pages remain read-only and ACL changes take effect.
- [ ] **Step 5: Store exact pixel diffs and CRUD evidence** for every closed matrix row; leave missing enterprise/production capability explicitly OPEN.

### Task 6: 语音页面的像素复刻与延期边界

**Files:**
- Modify: `web/src/features/meetings/RecordingPage.vue`
- Modify: `web/src/features/recording/RecordingIndicator.vue`
- Modify: `web/src/features/transcript/TranscriptPlayer.vue`
- Modify: `web/src/features/transcript/useSourceMedia.ts`
- Modify: `web/src/features/meetings/FuturePanels.vue`
- Test: `tools/dev/prototype_parity_recording.mjs`, `tools/dev/prototype_parity_media_player.mjs` or their replacement static-state checks

**Interfaces:**
- Consumes: Task 1 `DEFERRED_VOICE` rows and the original prototype visual states.
- Produces: static/disabled/error/延期 voice page renderings that match the prototype geometry and copy without opening devices or creating fake media results.

- [ ] **Step 1: Capture reference states** without calling `getUserMedia`, recording, ASR or media providers.
- [ ] **Step 2: Align all static structure, controls, copy, overlays and responsive states**; show a stable, explainable deferred state for unavailable business actions.
- [ ] **Step 3: Add a no-device-access assertion** and verify no voice task, file, transcript result or success receipt is created by loading or navigating the page.
- [ ] **Step 4: Mark the feature rows `DEFERRED_VOICE`**, never `PASS`, and ensure the overall report still distinguishes page visual completion from voice business completion.

### Task 7: Final B follow-up gate and independent review

**Files:**
- Modify: `docs/acceptance/b/b-followup-page-crud-matrix-2026-10-30.md`
- Modify: `docs/acceptance/b/prototype-parity-2026-10-29.md`
- Modify: `docs/acceptance/b/prototype-parity-validation-2026-10-29.md`
- Modify: `docs/acceptance/b/summary.md`
- Create: `docs/acceptance/b/b-followup-final-2026-10-30.md`

**Interfaces:**
- Consumes: all task-level screenshot, browser, API, DB/file, harness and regression evidence.
- Produces: one source-bound final report with `PASS`, `OPEN`, `FAIL` and `DEFERRED_VOICE` rows and an overall `ACCEPTED`/`NOT ACCEPTED` decision.

- [ ] **Step 1: Run the complete relevant Web/type/build/API/contract suites** serially and retain fresh command output; zero tests, skips or stale artifacts are not PASS.
- [ ] **Step 2: Run the fixed-port CAS/import browser suite** and both-side harness tests; verify callback allowlist, one-time ticket, logout, ACL/permission failures and material/version error honesty.
- [ ] **Step 3: Run the full pixel matrix** at 1440×900 and 1024×768 for every page/state, preserving unmasked reference/actual/diff images and exact mismatch counts.
- [ ] **Step 4: Bind every closed row** to the current source hash, harness revision, fixture identity, API response and persistent readback; retain all unresolved rows.
- [ ] **Step 5: Request independent review** of the final report and update the overall decision. Any `OPEN`, `FAIL` or unexplained `UNTESTED` keeps the B follow-up `NOT ACCEPTED`; `DEFERRED_VOICE` is reported separately and does not become a false feature pass.
