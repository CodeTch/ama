# AI 纪要与转写阶段原型复刻 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在现有会议详情入口中，为固定演示会议复刻附件的转写校正空态并补齐 AI 纪要工作台，两个阶段均使用本地伪数据交互且不触碰真实业务接口。

**Architecture:** `MeetingDetail` 继续作为真实工作台入口；当路由 ID 等于固定演示 ID 时，跳过登录/会议读取并渲染新的 `MeetingPrototypeStage` 组件。组件内部按 `transcript` 或 `minutes` 阶段渲染原型布局与本地交互，非演示 ID 保持现有 API 驱动分支不变。

**Tech Stack:** Vue 3、Vue Router 4、TypeScript、现有 `tokens.css` / `prototype.css` / `parity.css`、Playwright、Node test。

**Spec:** `docs/superpowers/specs/2026-10-30-ai-minutes-prototype-design.md`

## Global Constraints

- 所有开发计划、代码修改、测试和验证都在 `/home/tanren/ama` 的 `main` 分支进行。
- 不创建或使用 `.worktrees`，也不通过 Git worktree 建立或切换工作树。
- 不改动真实 API 客户端、接口契约、权限与发布逻辑。
- 固定演示会议 ID 为 `7b3707f9-ddf2-45cc-b116-7db26b87e4b5`；演示状态不写 API、缓存、会话或恢复记录。
- `/transcript-correction-demo` 保留；新增演示入口使用现有 `/meeting/:id/:stage?` 路径。

## Review Focus

- 访问演示 ID 时必须不发起 `/api/v1/` 请求；测试在 Task 4 中固定。
- 访问任意真实会议 ID 时仍使用原 `MeetingDetail` API 分支；测试在 Task 4 中固定。
- `transcript` 阶段必须复刻附件空态且不渲染红框标注；测试在 Task 3 中固定。
- `minutes` 阶段的 Tab、目录、重新生成、编辑提示和底部确认入口必须仅改变本地状态；测试在 Task 3 中固定。
- 桌面与窄屏不能产生横向溢出；测试在 Task 4 中固定。

### Task 1: Add failing route-level tests

**Files:**
- Create: `web/tests/e2e/ai-minutes-prototype.spec.ts`

**Interfaces:**
- Consumes: existing Vite dev server and `/meeting/:id/:stage?` router.
- Produces: failing browser assertions that define both demo stages and zero API traffic.

- [ ] **Step 1: Write the failing tests**

  Add tests that navigate to `/meeting/7b3707f9-ddf2-45cc-b116-7db26b87e4b5/transcript` and `/meeting/7b3707f9-ddf2-45cc-b116-7db26b87e4b5/minutes`, assert the fixed header/step labels, assert transcript copy or minutes tabs, click the local controls, and collect requests containing `/api/v1/` expecting an empty list. Assert document `scrollWidth` is at most viewport width.

- [ ] **Step 2: Run tests to verify they fail**

  Run: `npm --prefix web run test:e2e -- tests/e2e/ai-minutes-prototype.spec.ts`

  Expected: FAIL because the demo route is currently blocked by login and the new minutes-stage selectors do not exist.

### Task 2: Add isolated demo fixture and stage component

**Files:**
- Create: `web/src/features/meetings/meeting-prototype-demo.ts`
- Create: `web/src/features/meetings/MeetingPrototypeStage.vue`

**Interfaces:**
- Consumes: `stage: 'transcript' | 'minutes'`, `meetingId: string`, and `navigate(stage: string)` callback.
- Produces: isolated local rendering with no imports from API clients, session state, or journals.

- [ ] **Step 1: Implement fixture constants and local data**

  Export the fixed demo ID, stage type, header metadata, and representative AI minutes groups/paragraphs. Keep all strings scoped to this module and do not persist them.

- [ ] **Step 2: Implement transcript stage markup**

  Reproduce the attachment’s topbar, meeting header, five-step flow, left recording/speaker cards, right empty transcript card, history details, and local notice dialog. The red annotation rectangle is reference markup and must not be rendered.

- [ ] **Step 3: Implement minutes stage markup**

  Render the AI minutes workbench using the established prototype structure: `核心要素` / `会议纪要` tabs, left category directory, right content cards/body, local “重新生成”/edit feedback, and fixed “进入会议确认” action. Keep the existing shared button/card tokens and use a single local `ref` view state.

- [ ] **Step 4: Add local interaction behavior**

  Wire stage buttons to the supplied navigation callback, tabs and directory to local state, dialog actions to local notices, transcript reload/history to local status text, and minutes regeneration/edit actions to local status text. Do not call `fetch`, application APIs, or storage.

- [ ] **Step 5: Run component tests**

  Run: `npm --prefix web run typecheck`

  Expected: the new component compiles after its direct imports and props are complete.

### Task 3: Route the existing meeting detail page into the demo component

**Files:**
- Modify: `web/src/features/meetings/MeetingDetail.vue`
- Modify: `web/src/main.ts`

**Interfaces:**
- Consumes: `DEMO_MEETING_ID` and `MeetingPrototypeStage` from Task 2.
- Produces: `/meeting/:id/:stage?` demo entry that bypasses session/meeting reads only for the fixed ID; all other IDs preserve current behavior.

- [ ] **Step 1: Add demo route recognition**

  In `main.ts` allow the fixed demo ID through the route guard without calling `session.refresh()`. Keep login and transcript-correction-demo exceptions unchanged.

- [ ] **Step 2: Add demo branch to `MeetingDetail`**

  Compute `isDemoMeeting` and a validated demo `activeStage` (`transcript` or `minutes`, default `transcript`). Skip `refreshSession`, route-leave real work checks, and real data rendering for this ID. Render `MeetingPrototypeStage` within the existing detail route shell and hide the live actor status in the demo topbar.

- [ ] **Step 3: Preserve real meeting behavior**

  Keep `useSavedMeeting`, `useWorkspaceNavigation`, `MinutesWorkspace`, `TranscriptEditor`, and all existing conditional branches intact for non-demo IDs. Do not alter API request builders or permission checks.

- [ ] **Step 4: Run the failing e2e tests**

  Run: `npm --prefix web run test:e2e -- tests/e2e/ai-minutes-prototype.spec.ts`

  Expected: PASS for both stages, with zero `/api/v1/` requests and no horizontal overflow.

### Task 4: Visual and regression verification

**Files:**
- Modify: `web/tests/e2e/ai-minutes-prototype.spec.ts` if selectors need stabilization.

- [ ] **Step 1: Capture desktop screenshots**

  Run the e2e tests at the configured desktop viewport and review screenshots against the attachment: the page background, 56px topbar, 24px content padding, step geometry, white cards, navy/orange palette, and bottom dock must align; the red reference annotation must be absent.

- [ ] **Step 2: Run unit tests and typecheck**

  Run: `npm --prefix web run test:unit` and `npm --prefix web run typecheck`

  Expected: all existing unit tests pass and typecheck exits 0.

- [ ] **Step 3: Run production build**

  Run: `npm --prefix web run build`

  Expected: Vite build exits 0 with no TypeScript or Vue compiler errors.

- [ ] **Step 4: Inspect the final diff**

  Run: `git diff --check` and `git status --short`.

  Expected: no whitespace errors; only the intended design/plan, component, route, and test files are changed by this task.
