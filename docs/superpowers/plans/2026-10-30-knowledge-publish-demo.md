# Knowledge Publish Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增一个不调用产品 API 的 `/knowledge-publish-demo` 演示页，按原型像素还原知识库发布工作台并覆盖本地发布交互。

**Architecture:** 新建独立 Vue 单文件组件承载工作台壳层、五步流程、发布位置选择、纪要预览和本地状态机；复用 `tokens.css`、`prototype.css`、`parity.css` 提供的工作台样式和 `Icon` 组件。仅在 `main.ts` 注册公开演示路由并放过路由守卫，现有 `MeetingDetail`、`PublicationPanel`、API client 与真实发布流程不改动。

**Tech Stack:** Vue 3.5、Vue Router 4、TypeScript、现有 CSS token、Playwright。

**Spec:** `docs/superpowers/specs/2026-10-30-knowledge-publish-pixel-design.md`

## Global Constraints

- 所有计划、修改、测试和验证在 `/home/tanren/ama` 的 `main` 分支完成。
- 不创建或使用 `.worktrees`，不切换分支。
- 演示页不发起 `/api/v1/`、`/api/` 或 `/auth/` 请求。
- 不新增产品依赖，不修改真实发布接口和现有工作台。
- 只复刻原型已有设计，不添加额外导航或视觉元素。

## Review Focus

- 未选择知识库/文件夹时发布按钮必须保持禁用，并给出原型一致的提示；由页面组件测试覆盖。
- 搜索无匹配库时列表必须显示空态，清空后恢复；由页面组件测试覆盖。
- 进入子文件夹后返回根目录必须清除旧的子目录选择；由页面组件测试覆盖。
- 发布失败必须保留目标路径和标题并允许重试；由页面组件测试覆盖。
- 成功发布后页面必须进入只读结果状态，不能再次生成第二份文档；由页面组件测试覆盖。

### Task 1: Build the knowledge publish demo component

**Files:**
- Create: `web/src/features/minutes/KnowledgePublishDemoPage.vue`
- Test: `web/tests/e2e/knowledge-publish-demo.spec.ts`

**Interfaces:**
- Consumes: existing global CSS tokens, `Icon.vue`, `RouterLink`.
- Produces: a self-contained page with accessible headings, buttons, lists, dialogs, and local state transitions; no API calls.

- [x] **Step 1: Write the failing Playwright coverage**

  Add tests for the initial 1440×900 view: `知识库发布` is the active step, meeting title and preview are visible, the release button is disabled until a folder is selected, and no request URL contains `/api/` or `/auth/`. Add interaction assertions for library search, folder selection, preview dialog, publish progress, retry after simulated failure, and success read-only state.

- [x] **Step 2: Run the focused test to verify it fails**

  Run: `cd web && npx playwright test tests/e2e/knowledge-publish-demo.spec.ts --project=1440x900`

  Expected: FAIL because the demo route and component do not exist.

- [x] **Step 3: Implement the static page shell**

  Create `KnowledgePublishDemoPage.vue` with the exact demo meeting content from the prototype: top bar, back link, title/status row, five-step bar with step 5 active, two-column body, fixed footer, and a publish result region. Keep all copy in Chinese and use existing `btn`, `card`, `tag`, `input`, `aci-flow`, `aci-step`, `acf-dock` classes where they match the prototype.

- [x] **Step 4: Implement local publish state and interactions**

  Use local `ref`/`computed` state for libraries, folders, search text, selected destination, document title, preview dialog, publish phase (`idle`, `publishing`, `failed`, `succeeded`) and attempt count. Search filters the visible libraries; selecting a library exposes its folders; selecting a folder updates the orange publish path. The first publish attempt intentionally enters the prototype failure state and the next retry succeeds, with progress text and a success dialog/result card. Keep the selected destination and title across failure.

- [x] **Step 5: Add the route without touching real guards**

  Register `{path:'/knowledge-publish-demo',name:'knowledge-publish-demo',component:KnowledgePublishDemoPage}` in `web/src/main.ts` and allow this path through `router.beforeEach` alongside `/login` and `/transcript-correction-demo`. Do not change `/meeting/:id/:stage?` behavior.

- [x] **Step 6: Run the focused test to verify it passes**

  Run: `cd web && npx playwright test tests/e2e/knowledge-publish-demo.spec.ts --project=1440x900`

  Expected: PASS with no product API requests and no horizontal overflow.

- [x] **Step 7: Commit the self-contained demo**

  ```bash
  git add web/src/features/minutes/KnowledgePublishDemoPage.vue web/src/main.ts web/tests/e2e/knowledge-publish-demo.spec.ts
  git commit -m "feat: add knowledge publish prototype demo"
  ```

### Task 2: Verify responsive parity and repository health

**Files:**
- Modify: `web/tests/e2e/knowledge-publish-demo.spec.ts` (only if coverage needs viewport-specific assertions)

**Interfaces:**
- Consumes: the completed demo route from Task 1.
- Produces: verified screenshots and passing type/build checks.

- [x] **Step 1: Run both viewport projects**

  Run: `cd web && npx playwright test tests/e2e/knowledge-publish-demo.spec.ts --project=1440x900 --project=1024x768`

  Expected: PASS; `document.documentElement.scrollWidth` is at most the viewport width and the fixed footer remains visible.

- [ ] **Step 2: Run typecheck and production build**

  Run: `cd web && npm run typecheck && npm run build`

  Expected: both commands exit 0 with no TypeScript errors or build warnings that block output.

- [ ] **Step 3: Run the existing unit suite**

  Run: `cd web && npm run test:unit`

  Expected: all existing tests plus the new page coverage pass.

- [x] **Step 4: Review the diff and verify scope**

  Run: `git diff --check` and `git status --short`.

  Expected: no whitespace errors; only the new demo component, route, tests, and approved design/plan docs are changed.

Verification note: the demo-specific src-only typecheck and production build pass. The full workspace typecheck is blocked by the pre-existing `meetings-api.test.ts`/`createMeetingsApi().remove` mismatch, and the current full unit suite includes unrelated failures from concurrent work on `FuturePanels`, `MeetingList`, and `TranscriptForm`; those files are outside this plan.
