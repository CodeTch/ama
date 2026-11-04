# 导入弹窗关闭与请求恢复修复方案

> 状态：已实施，验证结果见文末。仅由当前单 agent 在 `/home/tanren/ama` 的 `main` 分支执行；不创建分支、worktree 或子 agent。执行时使用 `executing-plans`，逐项验证。

**Goal:** 解决保存或放弃导入结果未确认时无法退出弹窗的问题，同时保留原请求的可靠重试入口。

**Architecture:** 区分关闭界面、保存导入和放弃导入。保存/放弃请求失败或恢复等待时，允许持久化原请求后暂时关闭；恢复入口由应用层管理，不依赖弹窗实例。沿用后端的原请求幂等重放，并将上传初始化状态与恢复失败提示分开。

**Tech Stack:** Vue 3、TypeScript、现有 sessionStorage 日志、Node test/tsx、Playwright、现有 Java API。

**Spec:** 本文“需求与行为约定”；这些行为是本次建议，不代表已实现或用户已确认。

## 项目约束

- 所有开发计划、代码修改、测试和验证都在项目根目录 `/home/tanren/ama` 的 `main` 分支进行。
- 不创建或使用 `.worktrees`，也不通过 Git worktree 建立或切换工作树。
- 开始工作前确认当前目录是项目根目录且当前分支是 `main`。
- 除非用户明确要求，否则不自行切换到其他分支。
- 所有任务不可以开子代里/子agent进行处理，全程使用单agent。
- 保留工作区既有修改；本计划不授权清理用户数据、重置数据库或提交其他修改。

## 方案比较

1. **推荐：增加可恢复的关闭。** 保留正常情况下已有的保存/放弃流程，专门修复异常恢复状态。涉及关闭策略、持久化恢复入口、错误提示及测试。
2. **延迟创建会议，等用户选择文件后再创建。** 能减少空会议，但会改变上传授权、材料关联和会议创建流程；仍不能解决已发出的保存/放弃请求未确认，因此不作为本次修复。

单纯删除 `closeBlocked` 不构成完整修复：`App.vue` 关闭后会卸载面板，现有 scoped journal 也会在切换会议时清理旧范围，需要先保证恢复记录及入口独立存活。

## 需求与行为约定

| 场景 | 关闭行为 | 数据及请求行为 |
| --- | --- | --- |
| 新建空导入，尚无异常 | 保留现有放弃流程，明确显示“正在放弃导入…” | 请求有 15 秒等待上限；超时归入结果待确认 |
| 主文件上传完成，有尚未保存的选择 | 保留保存/放弃/继续导入确认 | 不把关闭直接当作已保存 |
| 保存或放弃请求处于 PREPARED/UNCERTAIN，当前没有其他传输或未解决操作 | 点击 × 或 Esc 可暂时关闭并回到原页面 | 原请求持久化到独立恢复记录，列表显示“有一项导入操作待确认 · 继续处理” |
| 保存或放弃请求正在发送 | 展示发送状态，避免重复提交；最多等待 15 秒 | 超时保留原 key/body，进入 UNCERTAIN 后可暂时关闭 |
| 放弃请求恢复中，上传队列尚未初始化 | 展示“上次放弃导入的结果待确认” | 不展示“上传恢复记录暂不可用”，也不强行读取可能已删除会议的上传状态 |
| 本地恢复存储不可用 | 明确显示“暂时无法保存恢复记录，请重试” | 保持当前实例和原请求；禁止用清空状态伪造成功退出 |
| 主文件/背景材料仍有实际上传或其他未确认操作 | 沿用各操作的关闭保护，显示具体原因 | 本次不取消上传保护，不把未知请求当作失败或已取消 |

普通关闭、暂时关闭、保存成功、放弃成功必须有不同事件含义。暂时关闭不发出 `saved` 或 `discarded`，不删除会议；仅收到有效成功回执后才刷新业务结果。

## Review Focus

- 服务器已经完成放弃，但响应丢失：恢复必须直接重放原 POST，不先读取可能已删除的会议草稿。
- 关闭后切换会议或刷新列表：原命令及恢复入口都还在，不被旧 owner 清理机制删除。
- 登录失效及账号切换：新账号看不到旧账号记录；不得通过“重试”绕过 DENIED 状态。
- 请求超时及晚到回执：不重复创建请求，不改变原 body/key，不让旧回执关闭新弹窗。
- 存储写入失败、清理失败和损坏：可见地报告问题；迁移记录必须先写入成功再清理旧记录。

## Task 1：保留可跨弹窗恢复的原请求

**Files:**
- Create: `web/src/features/import/pending-import-journal.ts`
- Modify: `web/src/features/import/useImportDraft.ts`
- Test: `web/tests/unit/pending-import-journal.test.ts`
- Test: `web/tests/unit/use-import-draft.test.ts`

**Interfaces:**
- `PendingImportRecord = { schema: 1; actor: string; meetingId: string; newImport: boolean; command: CommandSnapshot }`。
- `createPendingImportJournal(context)` 提供 `put(record)`、`get(actor, meetingId)`、`list(actor)`、`remove(actor, meetingId, commandKey)`、`clearActor(actor)`；失败必须返回明确结果，不能静默丢弃。
- 新增 `draft.defer(newImport: boolean): boolean`：只在没有在途 draft 请求且原命令待确认时，将原命令安全交给恢复存储；成功后允许关闭。

- [ ] 先写失败测试：保存和放弃的原 key/path/body 原样恢复；会议 A 的记录不被会议 B 覆盖；账号 B 无法读取 A；写入失败保留旧记录；旧命令回执不能清除同会议的新 key。
- [ ] 实现专用于导入的有界索引与记录：沿用当前标签页 namespace、校验器和私有存储；建议每账号每标签页最多 16 条，每条命令 body 延续 65536 字节上限；到限时明确要求先处理记录，不自动淘汰。
- [ ] 保留旧 journal 的读取兼容；恢复入口优先读取独立记录，旧记录仅在成功迁移后清理。相同会议存在不同未确认命令时报告冲突，不覆盖。
- [ ] `useImportDraft.open()` 恢复 DISCARD 时继续跳过普通草稿和上传读取，但显式返回“等待放弃确认”显示状态。
- [ ] 同一账号刷新可恢复；显式登出或切换账号按项目私有数据规则清理原账号记录，清理失败有可见反馈。登录过期期间隐藏记录且禁止发送，不能未经会话复核直接重试。
- [ ] 运行上述两个测试文件，确认通过。

## Task 2：关闭策略与应用层恢复入口

**Files:**
- Modify: `web/src/features/import/ImportDialogView.vue`
- Modify: `web/src/features/import/UploadImportPanel.vue`
- Modify: `web/src/App.vue`
- Modify: `web/src/application.ts`
- Modify: `web/src/features/meetings/MeetingDetail.vue`（核对内嵌导入的路由离开与恢复入口）
- Test: `web/tests/e2e/import-dialog-close-recovery.spec.ts`

**Interfaces:**
- 面板增加 `deferred: [meetingId: string]` 事件；仅在 `draft.defer()` 成功后发出。
- 将 draft 待确认状态与实际上传/材料关闭保护分开；关闭决策先检查可暂存请求，之后才进入已有的正常保存/放弃确认。
- 应用层恢复入口按当前账号读取记录；点击“继续处理”绑定原 meetingId，并打开原导入会话。

- [ ] 先写浏览器失败测试：注入已保存的 DISCARD 请求，点击 × 后弹窗关闭，列表显示恢复提示；点击继续处理后仍是原请求且没有额外创建会议。
- [ ] 暂时关闭完成后移除 `import`/`fresh` 查询参数，防止路由监听立即重开；保留独立恢复记录。普通 `closed` 与业务成功事件不能重复导航。
- [ ] 恢复提示不能依赖会议仍出现在列表中；即使服务端已删除会议，也能按原请求重放读取放弃回执。
- [ ] 会议 A 关闭后进入 B，再返回列表刷新，仍可恢复 A；主文件/背景材料的实际传输保护保持有效。
- [ ] 验证保存/放弃成功后仅移除对应 commandKey 的记录，刷新列表；晚到结果不关闭另一个会议的弹窗。
- [ ] 运行新增关闭恢复浏览器测试。

## Task 3：准确提示、有限等待和可执行重试

**Files:**
- Modify: `web/src/features/import/UploadImportView.vue`
- Modify: `web/src/features/import/UploadImportPanel.vue`
- Modify: `web/src/features/import/useImportDraft.ts`
- Modify: `web/src/shared/api/import-drafts.ts`
- Test: `web/tests/unit/upload-import-view.test.ts`
- Test: `web/tests/unit/use-import-draft.test.ts`
- Test: `web/tests/unit/import-drafts-api.test.ts`

**Interfaces:**
- `draft.recoveryMode` 区分正常初始化、保存待确认、放弃待确认、恢复记录故障。
- `UploadImportView` 的上传恢复错误由“真实队列恢复故障”驱动，不能仅依据 `queue.blocked` 默认值。
- `createImportDraftsApi` 的 read/send 使用局部 15000ms 截止时间，并将取消信号传递至实际 HTTP 请求和响应体读取；测试可注入短截止时间。

- [ ] 先写失败测试：恢复 DISCARD 时不出现上传错误；真实队列恢复失败仍显示错误及重试入口；初始化期间不显示失败。
- [ ] 放弃待确认文案为“上次放弃导入的结果待确认，可重试或暂时关闭”；保存待确认使用对应保存文案。
- [ ] 真实上传恢复失败时提供“重新检查”，调用已有恢复路径；若 DISCARD 待确认则只提供原请求重试，不混入上传恢复。
- [ ] 超时请求终止本地等待，保留原命令并进入 UNCERTAIN；不用单独 Promise.race 放任旧网络请求无限悬挂，不把超时当作后端未执行。
- [ ] 重试限定于允许发送的原命令状态；DENIED 显示登录/权限处理，不提供点击后必然抛出 `Command cannot be sent` 的按钮；CONFLICT/REJECTED 走现有重新核对流程。
- [ ] 成功但本地清理失败时只重试清理；不能误触发第二次业务操作。
- [ ] 运行上述单元测试，确认通过。

## Task 4：验收与后端边界核对

后端 `JdbcImportDrafts.discard()` 已实现原拥有者对已删除草稿的精确回执重放；本次优先复用该行为。此前未确认请求的具体 HTTP 故障仍需用运行时证据定位，不预先假定为网络错误或修改后端协议。

- [ ] 浏览器覆盖断网、503、无效成功响应、恢复原请求、Esc、重复点击、刷新、切换会议、存储不可用和晚到结果。
- [ ] 保留并执行原有 `import-dialog-stability.spec.ts`：正常关闭仍等待放弃完成，加载与关闭时布局不跳动。
- [ ] 验证每次重试的 method/path/body/Idempotency-Key 完全一致；关闭和恢复不创建新会议。
- [ ] 在专用测试会议上验证“服务端放弃成功、客户端丢失响应、再次原样重试”得到相同回执；不操作用户实际会议。
- [ ] 从项目根目录运行 `npm --prefix web run typecheck`。
- [ ] 从项目根目录运行 `npm --prefix web run test:unit`。
- [ ] 从项目根目录运行 `npm --prefix web run test:e2e -- tests/e2e/import-dialog-stability.spec.ts tests/e2e/import-dialog-close-recovery.spec.ts tests/e2e/material-dialog-stability.spec.ts`。
- [ ] 从项目根目录运行 `npm --prefix web run build`。

验收以“错误状态可退出、退出后可恢复同一个请求、有效成功回执才改变业务状态”为准。

## 实施记录

- 关闭恢复的浏览器回归加入现有 `import-dialog-stability.spec.ts`。导入和材料弹窗的 18 项浏览器测试均通过。
- 真实联调发现通用会议读取预检在放弃回执重放前返回 403。仅将放弃导入 POST 交给该操作原有的授权和原请求校验。相关 Java 测试 6 项通过。
- 本地服务更新后，在专用新建会议上丢弃首次成功响应。关闭弹窗并刷新后，重试使用相同正文和幂等键，服务端再次返回相同的 HTTP 200 回执。
- 前端类型检查、构建以及导入超时、恢复存储和命令状态的 11 项单测通过。全量前端单测运行有 1 项纪要候选窗口测试报 `Application context missing`，该项仍需单独处理。
