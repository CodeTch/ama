# 转写校正界面 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增一个独立的“转写校正”演示路由，像素复刻用户附件中的空转写状态，并用隔离伪数据验证页面交互，同时保持真实工作台接口逻辑不变。

**Architecture:** 新建 `TranscriptCorrectionDemoPage.vue` 作为只读演示页面，复用现有全局 token 和图标组件；在 `main.ts` 注册 `/transcript-correction-demo`。真实 `MeetingDetail.vue` 及 transcript API 保持不变，伪数据只存在演示页面和 Playwright 测试中。

**Tech Stack:** Vue 3、Vue Router、TypeScript、Vite、Playwright、现有 CSS token/prototype/parity 样式。

**Spec:** `docs/superpowers/specs/2026-10-30-transcript-correction-design.md`

## Global Constraints

- 所有开发、测试和验证在 `/home/tanren/ama` 的 `main` 分支进行。
- 不创建或使用 `.worktrees`，不切换分支。
- 视觉基准是用户附件和 `docs/acceptance/a/prototype/{1440x900,1024x768}/original-transcript-page.png`。
- 真实工作台接口逻辑保持默认；演示页面不调用 API，伪数据不进入 API client。
- 不增加转写校正之外的新页面设计或未请求的业务能力。

## Review Focus

- 空转写响应：右侧必须显示原型文案和“重新读取转写”，不能伪造段落、播放器或时长；由 `transcript-correction.spec.ts` 验证。
- 工作区阶段：第一步完成、第二步高亮、后三步禁用时，流程条颜色、连接线和可访问状态需一致；由视觉断言验证。
- 视口变化：1440×900 与 1024×768 均不产生横向溢出，左栏宽度、右栏卡片和标题动作保持原型比例；由双视口截图验证。
- 历史折叠：默认只显示摘要，展开后显示“转写历史”内容并保持卡片间距；由交互断言验证。
- 接口边界：演示路由始终不发产品 API 请求；真实 `/meeting/:id/transcript` 工作台继续由现有 API client 驱动，不共享演示状态；由 Playwright 请求监听和生产代码审阅验证。

### Task 1: 新增独立转写校正演示路由

**Files:**
- Create: `web/src/features/transcript/TranscriptCorrectionDemoPage.vue`
- Modify: `web/src/main.ts`
- Test: `web/tests/e2e/transcript-correction-demo.spec.ts`

**Interfaces:**
- Consumes: 现有全局样式 token、`Icon.vue`、`RouterLink`。
- Produces: `/transcript-correction-demo` 独立演示页；真实工作台及 API 方法签名不变。

- [ ] **Step 1: 写空态结构测试**

  在 `web/tests/e2e/transcript-correction-demo.spec.ts` 访问新路由，断言标题、状态、五步条、左右空态、重新读取按钮和历史摘要存在，并确认页面没有发出 `/api/v1/` 请求。

- [ ] **Step 2: 运行测试确认当前差异**

  运行 `cd web && npx playwright test tests/e2e/transcript-correction-demo.spec.ts --project=1440x900`，确认路由不存在或目标页面缺失导致失败。

- [ ] **Step 3: 最小修正组件结构与样式**

  实现 `TranscriptCorrectionDemoPage.vue` 的静态空态布局和本地交互；注册路由；保持编辑、保存和更多按钮只展示未接入提示，不写入真实数据。

- [ ] **Step 4: 运行结构测试确认通过**

  重新运行上述 Playwright 测试，期望所有语义断言通过且页面无横向滚动、无 API 请求。

- [ ] **Step 5: 提交本任务**

  ```bash
  git add web/src/features/transcript/TranscriptCorrectionDemoPage.vue web/src/main.ts web/tests/e2e/transcript-correction-demo.spec.ts
  git commit -m "feat: add transcript correction demo route"
  ```

### Task 2: 双视口像素与交互回归

**Files:**
- Modify: `web/tests/e2e/transcript-correction-demo.spec.ts`
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

