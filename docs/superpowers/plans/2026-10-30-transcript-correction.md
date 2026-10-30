# 转写校正界面 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有会议工作台的“转写校正”阶段对齐用户附件中的空转写状态，保持原型结构、几何和接口逻辑，并用测试伪响应验证页面交互。

**Architecture:** 继续使用 `MeetingDetail.vue` 作为页面壳，复用 `MeetingProgress`、`TranscriptPlayer`、`TranscriptEditor` 和 `TranscriptForm`。不在生产组件中写死业务假数据；伪数据只作为 Playwright 请求拦截 fixture，覆盖会话、会议、工作区、转写空态和选项读取，使视觉与交互可以离线回放。

**Tech Stack:** Vue 3、Vue Router、TypeScript、Vite、Playwright、现有 CSS token/prototype/parity 样式。

**Spec:** `docs/superpowers/specs/2026-10-30-transcript-correction-design.md`

## Global Constraints

- 所有开发、测试和验证在 `/home/tanren/ama` 的 `main` 分支进行。
- 不创建或使用 `.worktrees`，不切换分支。
- 视觉基准是用户附件和 `docs/acceptance/a/prototype/{1440x900,1024x768}/original-transcript-page.png`。
- 真实接口逻辑保持默认；伪响应只在测试 fixture 中出现，不写入生产 API client。
- 不增加转写校正之外的新页面设计或未请求的业务能力。

## Review Focus

- 空转写响应：右侧必须显示原型文案和“重新读取转写”，不能伪造段落、播放器或时长；由 `transcript-correction.spec.ts` 验证。
- 工作区阶段：第一步完成、第二步高亮、后三步禁用时，流程条颜色、连接线和可访问状态需一致；由视觉断言验证。
- 视口变化：1440×900 与 1024×768 均不产生横向溢出，左栏宽度、右栏卡片和标题动作保持原型比例；由双视口截图验证。
- 历史折叠：默认只显示摘要，展开后显示“转写历史”内容并保持卡片间距；由交互断言验证。
- 接口回退：fixture 关闭或 API 返回空态时，页面不渲染任何伪成功文案；由现有单元测试与生产代码审阅验证。

### Task 1: 对齐转写空态工作台结构

**Files:**
- Modify: `web/src/features/meetings/MeetingDetail.vue`
- Modify: `web/src/features/transcript/TranscriptPlayer.vue`
- Modify: `web/src/features/transcript/TranscriptEditor.vue`
- Modify: `web/src/shared/styles/parity.css`

**Interfaces:**
- Consumes: 现有 `WorkspaceNavigation`、`SavedMeeting`、`TranscriptCurrent` 组件输入。
- Produces: 在 `/meeting/:id/transcript` 下可渲染附件对应的标题、流程条、左右空态和历史入口；不改变 API 方法签名。

- [ ] **Step 1: 写空态结构测试**

  在 `web/tests/e2e/transcript-correction.spec.ts` 建立 API route fixture，返回 ACTIVE 会话、未命名会议、`TRANSCRIPT_PENDING` 工作区、空 `transcript.current` 和空媒体源；断言标题、状态、五步条、左右空态、重新读取按钮和历史摘要存在。

- [ ] **Step 2: 运行测试确认当前差异**

  运行 `cd web && npx playwright test tests/e2e/transcript-correction.spec.ts --project=chromium`，记录当前截图/结构差异，不更新 baseline。

- [ ] **Step 3: 最小修正组件结构与样式**

  在现有组件边界内修正空态卡片 padding、标题字号、列间距、流程条上下间距、工作区内容高度和历史折叠间距；保持空媒体/空转写的原有真实文案和 API 重读动作。删除会让空态变成假播放/假转写的任何演示文案。

- [ ] **Step 4: 运行结构测试确认通过**

  重新运行上述 Playwright 测试，期望所有语义断言通过且页面无横向滚动。

- [ ] **Step 5: 提交本任务**

  ```bash
  git add web/src/features/meetings/MeetingDetail.vue web/src/features/transcript/TranscriptPlayer.vue web/src/features/transcript/TranscriptEditor.vue web/src/shared/styles/parity.css web/tests/e2e/transcript-correction.spec.ts
  git commit -m "fix: align transcript correction empty workspace"
  ```

### Task 2: 双视口像素与交互回归

**Files:**
- Modify: `web/tests/e2e/transcript-correction.spec.ts`
- Create: `web/tests/visual/baselines/{1024x768,1440x900}/transcript-correction-empty.png` only after verifying against the user-provided reference

**Interfaces:**
- Consumes: Task 1 页面和同一组 API fixture。
- Produces: 双视口结构断言、截图证据、历史展开回归和无溢出验证。

- [ ] **Step 1: 写双视口截图与交互断言**

  在 1440×900 与 1024×768 下等待字体和网络空闲后截图；断言流程条第二步可见且高亮，前三个核心空态文本一致，展开“转写历史与原操作恢复”后出现历史卡片，回收时仍无横向滚动。

- [ ] **Step 2: 运行双视口回归**

  运行 `cd web && npx playwright test tests/e2e/transcript-correction.spec.ts --project=chromium`，对照附件和仓库原型图检查完整视口；若出现差异，优先修正源 CSS/结构，不放宽截图阈值。

- [ ] **Step 3: 运行完整前端验证**

  依次运行 `cd web && npm run typecheck`、`npm run build`、`npm run test:unit`，确认现有转写编辑、恢复和会议详情测试不回归。

- [ ] **Step 4: 提交验证结果**

  仅在截图与完整验证通过后提交测试变更及证据路径；不更新原型 reference 图，不把 fixture 代码带入生产组件。

