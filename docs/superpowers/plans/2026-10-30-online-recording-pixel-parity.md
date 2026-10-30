# 在线录音界面像素复刻实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将在线录音页面的 Vue 渲染结果与仓库原型的最终 `.acr-page` 结构和样式对齐，同时保留真实录音与接口边界。

**Architecture:** 先把原型在线录音区块完整重建到 `RecordingPage.vue`，演示阶段用测试夹具驱动伪录音状态和按钮交互；视觉验收后删除演示状态与临时入口，把同一模板接回 `useRecorder`、会议创建/上传/信息抽屉和现有真实接口。

**Tech Stack:** Vue 3、TypeScript、Vite SSR、Node test runner、Playwright Chromium。

**Spec:** `docs/superpowers/specs/2026-10-30-online-recording-pixel-parity-design.md`

## Global Constraints

- 所有工作在 `/home/tanren/ama` 的 `main` 分支进行，不建立或使用 worktree。
- 原型 `designDocs/智能会议助手1021版.html` 是在线录音页面的视觉权威；不新增原型中不存在的设计。
- 演示阶段伪数据只允许在测试/开发夹具中出现；视觉验收收口前必须移除固定演示会议、伪接口成功响应和绕过登录/权限的开关。
- 录音、实时 ASR、服务端保存等未接入能力必须使用诚实状态文案；现有真实录音状态机和接口逻辑保持可用。
- 保留工作树中与本任务无关的未提交改动，不重置、不覆盖、不提交它们。

## Review Focus

- 未开始、录音中、暂停、中断保存和保存失败状态是否与原型控件顺序、文案和禁用状态一致。
- 页面头部返回入口、卡片内两组工具、转写标题副文案和底部模拟说明是否存在多余或缺失内容。
- 已保存分段、长文件名、错误状态和无会议 ID 时是否仍保持布局不溢出。
- 1440×900 与 1024×768 下最终级联、字体、动画和滚动区域是否稳定。
- 测试夹具是否只在测试边界注入，生产页面是否仍从真实应用状态和接口读取数据。

---

### Task 1: 建立原型演示页与状态回归测试

**Files:**
- Create: `web/tests/unit/recording-page-view.test.ts`
- Modify: `web/tests/visual/shell.spec.ts` or create a recording-specific visual spec only if the existing fixture can reach `/recording/:id` without production changes.

**Interfaces:**
- Consumes: 原型 HTML 的在线录音模板和现有路由入口。
- Produces: 可由伪数据驱动的原型演示页，以及结构、文案、录音控制、转写空态和保存状态断言。

- [x] **Step 1: Write the failing SSR assertions**

  Render the new prototype-shaped recording view with a minimal injected application/router fixture. Assert the prototype return button, exact prototype copy (`未接入服务端保存`, `模拟识别`, and the full simulation note), no legacy arrow-only head, and the active/paused/interrupted control labels.

- [x] **Step 2: Run the focused test to verify it fails**

  Run: `npm --prefix web run test:unit -- tests/unit/recording-page-view.test.ts`

  Expected: FAIL on the current arrow header and legacy recording/transcript copy.

- [x] **Step 3: Add only test-local state helpers**

  Keep fake session/recorder values inside the test file or a test-only helper. During this task the demo page may use a clearly isolated fixture adapter; record its removal as a later cleanup step.

- [x] **Step 4: Run the focused test again**

  Run the same command and confirm the assertions still fail for the intended production mismatches rather than fixture errors.

### Task 2: Rebuild `RecordingPage.vue` as the prototype-shaped demo page

**Files:**
- Modify: `web/src/features/meetings/RecordingPage.vue`
- Modify: `web/src/shared/styles/prototype.css` only when the structure exposes a real cascade mismatch.

**Interfaces:**
- Consumes: 原型结构、测试夹具和现有路由/信息/材料入口。
- Produces: 原型形状的页面、伪数据驱动的录音状态交互，以及可替换回真实状态机的模板边界。

- [x] **Step 1: Implement the prototype-shaped template and demo interactions**

  Replace the current template with the prototype’s recording page structure and demo interactions: `返回会议列表`, the three recording controls, segment interruption flow, saved-part rows, live transcript empty state and materials/info entries. Keep the existing visual class names and make the demo state explicit and removable.

- [x] **Step 2: Run the focused test to verify it passes**

  Run: `npm --prefix web run test:unit -- tests/unit/recording-page-view.test.ts`

  Expected: PASS with all prototype structure/copy assertions green.

- [x] **Step 3: Refactor only after green**

  Remove duplicated computed text or selectors if needed while keeping the demo state isolated from business APIs.

- [x] **Step 4: Run the focused test again**

  Confirm the page test remains green after cleanup.

### Task 3: Remove demo data and reconnect the clean interface logic

**Files:**
- Modify: `web/src/features/meetings/RecordingPage.vue`
- Modify: `web/src/features/recording/` only if the rebuilt template needs an existing state mapping.
- Test: `web/tests/unit/recording-page-view.test.ts`

**Interfaces:**
- Consumes: Task 2 prototype-shaped template and interaction assertions.
- Produces: a production page that renders only real recorder/session/upload/info state, with no fixed demo data or bypass.

- [x] **Step 1: Write the failing cleanup assertions**

  Assert that the production source has no demo seed, fake service success, hard-coded meeting, or development-only recording bypass, while the real recorder state still controls the rendered status and buttons.

- [x] **Step 2: Run the cleanup assertions to verify the intended demo path is still present**

  Run: `npm --prefix web run test:unit -- tests/unit/recording-page-view.test.ts`

  Expected: FAIL on the temporary demo adapter or marker.

- [x] **Step 3: Remove the demo adapter and reconnect real state**

  Delete the temporary demo data/entry path, map the existing `useRecorder` state into the prototype template, and keep all interface errors truthful.

- [x] **Step 4: Run the focused test to verify cleanup passes**

  Run: `npm --prefix web run test:unit -- tests/unit/recording-page-view.test.ts`

  Expected: PASS with no demo path and with the prototype structure still present.

### Task 4: Verify visual and project regressions

**Files:**
- Modify: `web/tests/visual/` only if a recording visual test is required to exercise an existing reachable fixture.
- No production files unless Task 2 exposes a measured CSS mismatch.

**Interfaces:**
- Consumes: completed Tasks 1–3 and the existing prototype reference capture tooling.
- Produces: fresh unit, type, build and visual/e2e evidence for the changed page.

- [x] **Step 1: Run focused and related unit tests**

  Run: `npm --prefix web run test:unit -- tests/unit/recording-page-view.test.ts tests/unit/recording-store.test.ts tests/unit/application.test.ts`

- [x] **Step 2: Run typecheck and build**

  Run: `npm --prefix web run typecheck && npm --prefix web run build`

- [ ] **Step 3: Run visual/e2e checks at both approved viewports**

  Run: `npm --prefix web run test:e2e -- tests/visual/shell.spec.ts tests/e2e/shell.spec.ts`

  If a recording-specific route fixture is available without production bypasses, capture and compare 1440×900 and 1024×768 with reduced motion and zero diff tolerance.

- [ ] **Step 4: Run the complete frontend unit suite**

  Run: `npm --prefix web run test:unit`

  Latest run: 324/326 passed; `upload-import-view.test.ts` cases 260–261 fail because the concurrently modified `UploadImportView.vue` now omits `DOC`/`PDF`/`PPTX` from its visible format guide. These failures are outside the recording-page diff and remain open in the shared worktree.

- [x] **Step 5: Review the diff and verify no fake production path remains**

  Check `git diff -- web/src/features/meetings/RecordingPage.vue web/src/shared/styles/prototype.css web/tests/unit/recording-page-view.test.ts` and confirm all pseudo-data helpers are test-local.
