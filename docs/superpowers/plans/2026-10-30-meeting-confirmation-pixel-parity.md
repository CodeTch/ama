# 会议确认界面像素复刻 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `/meeting/:id/confirm` 重构为与原型最终确认页一致的双栏确认界面，并保持现有最终确认与发布状态逻辑。

**Architecture:** 保留 `MeetingDetail -> MinutesWorkspace -> PublicationPanel` 现有组件链和 `usePublication` 状态机，只在 `PublicationPanel` 的 `confirm` 分支调整 DOM 结构、文案和 scoped CSS。通过组件级 SSR 测试锁定关键结构和交互可见性；开发态交互继续使用现有应用状态，伪数据仅作为测试输入。

**Tech Stack:** Vue 3 `<script setup>`, TypeScript, Vite, node:test/Vitest-compatible SSR harness, existing prototype CSS tokens.

**Spec:** `docs/superpowers/specs/2026-10-30-meeting-confirmation-pixel-parity-design.md`

## Global Constraints

- 所有修改在 `/home/tanren/ama` 的 `main` 分支完成，不创建或使用 worktree。
- 视觉基准为 `designDocs/智能会议助手1021版.html` 的 `acFinalPage` 与现有 `web/src/shared/styles/prototype.css`。
- 不改变 `usePublication`、后端契约或发布页面行为。
- 伪数据只用于开发态测试输入，完成验证后不进入生产运行时。

## Review Focus

- 纪要为空时仍显示原型的预览空态，不能渲染 `undefined` 标题或正文；由确认页 SSR 测试覆盖。
- readiness 阻断项必须使用业务文案并保持主按钮禁用；由阻断 readiness 测试覆盖。
- 已有最终确认快照时信息区变只读且底部操作可继续发布；由已确认状态测试覆盖。
- 确认页在窄屏下必须从双栏降为单栏，且固定操作栏可换行；由 CSS 断言和构建验证覆盖。
- 确认页不能引入独立伪数据分支绕过 `usePublication`；由源码测试检查确认动作仍调用 `confirmFinal`。

---

### Task 1: 锁定确认页视觉结构与状态文案

**Files:**
- Modify: `web/src/features/minutes/PublicationPanel.vue`
- Test: `web/tests/unit/meeting-confirmation-view.test.ts`

**Interfaces:**
- Consumes: `PublicationPanel` props (`stage`, `meetingInfo`, `savedMinutes`, `owner`, `locked`) and `usePublication` computed state.
- Produces: `stage === 'confirm'` markup with `.ac-confirm-grid`, `.ac-confirm-info`, `.ac-confirm-preview`, `.ac-confirm-check`, and `.acf-dock`.

- [ ] **Step 1: Write the failing SSR tests**

  Add tests that render `PublicationPanel` with a minimal injected application and assert:
  - the confirm page contains `会议信息`, `纪要预览`, `确认检查`, `返回纪要编辑`, and `最终确认并继续发布`;
  - empty `savedMinutes` renders `尚无已保存纪要` without `undefined`;
  - readiness blockers render their mapped Chinese text and disable the final-confirm button.

- [ ] **Step 2: Run the focused test and verify the expected failure**

  Run: `npm test -- --runInBand web/tests/unit/meeting-confirmation-view.test.ts` from `web/`.
  Expected: FAIL because the current confirm markup uses `会议纪要`, lacks the new check section contract, or does not expose the expected empty-state markup.

- [ ] **Step 3: Update the confirm template**

  In `PublicationPanel.vue`, keep the existing `confirmFinal` handler and replace only the confirm branch markup with the prototype structure:
  - left card title `会议信息`, embedded editor or read-only snapshot;
  - right card title `纪要预览`, title/body or empty state;
  - separate `ac-confirm-check` card with readiness errors/success state and counts;
  - fixed `acf-dock` with the two prototype actions.

- [ ] **Step 4: Run the focused tests and verify they pass**

  Run the same focused test command. Expected: PASS with no SSR errors.

- [ ] **Step 5: Commit the focused change**

  ```bash
  git add web/src/features/minutes/PublicationPanel.vue web/tests/unit/meeting-confirmation-view.test.ts
  git commit -m "feat: align meeting confirmation view with prototype"
  ```

### Task 2: Match prototype geometry and responsive behavior

**Files:**
- Modify: `web/src/features/minutes/PublicationPanel.vue`
- Modify: `web/src/shared/styles/prototype.css` only if shared rules are missing
- Test: `web/tests/unit/meeting-confirmation-view.test.ts`

**Interfaces:**
- Consumes: Task 1 markup and existing tokens (`--navy`, `--border`, `--orange`, spacing utilities).
- Produces: stable desktop two-column layout and narrow-screen single-column layout matching the reference.

- [ ] **Step 1: Extend the focused test with source-level geometry assertions**

  Assert that the component stylesheet contains the required `.ac-confirm-grid`, `.ac-confirm-body`, `.ac-confirm-check`, and responsive `@media` rules, and that no extra confirmation card is rendered.

- [ ] **Step 2: Run the focused test to verify the new assertions fail**

  Run: `npm test -- --runInBand web/tests/unit/meeting-confirmation-view.test.ts`.
  Expected: FAIL for any missing geometry rule or stale card label.

- [ ] **Step 3: Implement the minimum scoped CSS**

  Tune the confirm-only styles to the prototype: 0.9/1.3 column ratio, 16px gap, card header/body padding, 1.85 line height, bounded preview scroll, and 760px single-column breakpoint. Keep existing shared tokens and avoid new decoration.

- [ ] **Step 4: Run the focused test and production build**

  Run: `npm test -- --runInBand web/tests/unit/meeting-confirmation-view.test.ts` and `npm run build` from `web/`.
  Expected: both commands exit 0.

- [ ] **Step 5: Commit the geometry change**

  ```bash
  git add web/src/features/minutes/PublicationPanel.vue web/src/shared/styles/prototype.css web/tests/unit/meeting-confirmation-view.test.ts
  git commit -m "style: match meeting confirmation geometry"
  ```

### Task 3: Verify the full web surface and remove demo-only seams

**Files:**
- Modify: `web/tests/unit/meeting-confirmation-view.test.ts` if coverage needs final assertions
- Inspect: `web/src/features/minutes/PublicationPanel.vue`, `web/src/features/minutes/usePublication.ts`

**Interfaces:**
- Consumes: Tasks 1–2 implementation.
- Produces: evidence that confirmation uses the real publication state machine and no runtime pseudo-data branch remains.

- [ ] **Step 1: Add a regression assertion for the real confirm handler**

  Assert that the confirm button is wired to the existing `confirmFinal` path and that no `VITE_LOCAL_DEV_LOGIN` conditional appears in the `confirm` template branch.

- [ ] **Step 2: Run the focused regression test and inspect the diff**

  Run: `npm test -- --runInBand web/tests/unit/meeting-confirmation-view.test.ts` and `git diff --check`.
  Expected: PASS and no whitespace errors.

- [ ] **Step 3: Run the complete web verification**

  Run from `web/`: `npm test` and `npm run build`.
  Expected: all tests pass and build exits 0.

- [ ] **Step 4: Review the final diff against the spec**

  Confirm that only confirmation-page presentation and its tests changed, with no backend or interface modifications and no retained demo-only runtime code.

- [ ] **Step 5: Commit the verification updates if needed**

  ```bash
  git add web/tests/unit/meeting-confirmation-view.test.ts
  git commit -m "test: cover meeting confirmation prototype parity"
  ```
