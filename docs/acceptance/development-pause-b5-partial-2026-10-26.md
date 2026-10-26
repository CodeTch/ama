# B5 当前批次暂停与精确接续记录 — 2026-10-26

**状态：用户明确要求暂停；B5 部分源码保存，未完成、未整体审查、未发布运行许可。**

最新指令：“完成当前批次调用之后就停下，记录相关开发进度信息以及接续文档……”。本记录覆盖此前所有“持续执行全 B / ACTIVE / 继续运行”条目。完成收尾验证与本文落盘后停止，只有用户再次明确“继续任务”才恢复。记录时间 UTC：2026-10-26T13:59:45.966891+00:00。

## 1. 唯一工作区与协作

- 产品工作区：`/home/tanren/ama/.worktrees/phase-b-completion`；Windows：`\\wsl.localhost\Ubuntu-24.04\home\tanren\ama\.worktrees\phase-b-completion`。
- 分支 `codex/phase-b-completion-20261023`；实际 HEAD `739193dff6dea6c28999260d4d332f99f1a58d12`。保留全部未提交修改；本批没有 commit/reset/merge/push、没有新 worktree。
- 主 Agent `/root` 是唯一实现者。只有同一审查者 `/root/readback_subfunction_reviewer`，实际先前接受配置 `gpt-6-astra / xhigh / fork_turns=none`。没有创建其他 Agent；审查者不得委派、改实现或操作保留运行资源。
- 用户请求主 `GPT-6 Astra/high`；不能将该请求当作已验证切换。模型以实际会话配置为准。
- 同一审查者最后完成的是 B4，当前空闲；本轮未向它提交不完整 B5 审查。后续完成整个 B5 后一次完整审查，修复和运行证据复核均复用它。

## 2. 已保存的精确源码与证据

- 当前部分源码 checkpoint：`.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-11-b5-partial-pause-source-checkpoint-20261026.json`。
- checkpoint SHA256：`f6e1fc7aceb837f2e26d88af210801f7f4c2a76c940bf30d6bc9770e5184113f`；1189 份当前源码与快照逐份一致。相比已采用 B4 checkpoint，46 路径有变化。
- 快照目录：`.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-11-b5-partial-pause-source-checkpoint-20261026`；本快照仅用于暂停恢复，禁止直接拿它创建运行 release。
- B4 基准 checkpoint SHA256：`9fa4010fbcd77817ea75c430b16cfe36e0151a5ff23cc68d11597279f3e6b753`。旧 B4 的 1171 份快照重新核验不变。
- 相对 B4 patch SHA256：`49369e5c73fba5aaa4b427cf8c6a281f60e1fcaeab4fd0873cfd7ef3ae8b1e7e`；文件 `.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-11-b5-partial-pause-source-20261026.patch`。
- 证据索引 `.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-11-b5-partial-pause-evidence-index-20261026.json`，SHA256 `d73a9224a5df5f95df219c43e3342e9a72767335dfe6c0fc0c61ad1d5ad91c01`，277 个文件，包括旧失败及本批 RED/GREEN，未覆盖旧包。
- 可变接续文档更新前副本：`.superpowers/sdd/2026-10-23-ama-phase-b-remaining/pause-20261026-b5-partial/before/`，索引 `preimage-index.json`。不要把其中旧 ACTIVE/旧 next_steps 作为最新指令。

最终收尾证据在 `task-11-b5-evidence-20261026/`：

| 阶段 | 结果 | 资格 |
|---|---|---|
| `pause-focused-final.json` / `.log` / XML | 53 tests, 0 failures, 0 errors, 0 skipped；退出 0 | 14 个相关测试类，实际服务/事务/SQL路径与明确替身边界；不是完整项目回归或真实 MySQL/HTTP验收 |
| `pause-final-compile.json` / `.log` | 退出 0 | 离线增量 `test-compile`，不是 clean build |

两阶段 `sourceBefore == sourceAfter == 当前1189文件`。执行命令、cwd、开始/结束时间、退出码、512MiB/2CPU限制和 runner 锁信息已写入元数据。没有重跑 Host 矩阵或历史 runtime。暂停封存脚本首轮把旧 B4 `files` 列表误当成映射，退出 1；未产生 checkpoint/release。R1 仅验证并复用已复制的1189文件，转换旧列表格式后成功封存，原失败脚本保留；这不是产品测试失败。

## 3. 当前已实现但尚未整体采用的 B5 内容

完整历史普通写 guard 已接到共享 authority、图捕获与写 claim，验证接受记录、原始 manifest、final/task/operation/execution/本地或远端关闭证据；UNKNOWN、未应用成功、未关闭或矛盾历史保持禁止。真实事务 holder、同 issuer、同 gate、阶段与一次 claim 约束保留；只读资格不会消耗 claim。真实 `JdbcMeetingAccess` 根锁跟踪和最终原文等长变更拒绝已有离线测试。

RAW 任务入口、完整发布事实的 Task7 retention carrier、生成的 current/stale/fact-only 分支已接入。最近修复了已知发布冻结仍获取当前文件来源的问题，以及无业务图时普通外部观察重放的 NPE。只有确切 `STALE / BUSINESS_FENCED / 无输出 / 无候选稿` 可走该重放确认；不会恢复业务写权限。对应 `frozen-fact-current-source-red/green` 与 `frozen-fact-replay-red-02/green` 保留；测试明确替换了外部 context 校验，不宣称真实 engine 通过。

上传采用 ACK 优先：begin/part/complete 的合法既有结果先返回，仍做新鲜授权、参数/epoch核对；新写执行准备/存储/最终 guard。已覆盖存储后冻结禁止登记、竞态 ACK、授权 abort 清理。真实文件 registry 两种 bindAll 路径已有 ordinary/READY拒绝/多文件回滚测试；首个重载原先缺少捕获异常后的整体 rollback-only，已因果复现并补齐。

SavedTranscriptTransitions 保留旧声明原义，单独核验真实 guard 安装；不会把 DISABLED 声明改称已安装。真实组合装配核对同 authority、共享配置、真实 import/minutes/publication 队列策略，拒绝空队列、手工策略拼装、外部 authority 和配置错配。fresh intent/initial/generation 已接入源码，旧回执保持优先；fresh intent typed stale409已覆盖。初始设置使用选定 bounds 和 lockGraph 身份集合。

三个 T10 GET 已移除固定 TASK10/TASK11_NOT_INSTALLED：组合缺失返回 MINUTES_COMBINED_NOT_INSTALLED；组合可用时走生成准备或候选采用的只读 S/A/F 条件，保留不可变详情。相关投影测试的事实读取及 readiness seams 是替身，真正组合 HTTP 正反例仍未验收。

以上是源码进展，**不是 B5 完成、审查通过或功能运行验收通过**。B4 审查报告 `3e691b304151e2174be6cf128140ab7d13baed1715a7503acec9af0f36e34085` 只覆盖其旧冻结包；不能扩展到这些 B5 修改。

## 4. 精确剩余事项与下一个开发节点

1. B5 whole-source implementation remains incomplete and unreviewed. Finish the all-mutator call-site ledger and ordinary/closed/frozen actual-entry matrix: B03 info/roster, intake, saved transcript variants, initial settings, generation enqueue/current/retained output, intent, saved minutes edits/settings/reviews/adopt/reject/dispose/restore; preserve immutable history and rollback/fence behavior.

2. Investigate operation-wide acquisition accounting: RAW TranscriptReadGraph metadata still uses its prior reader budget separately from the publication ledger; intent still reads MinutesIntentControl four-query metadata outside the shared ledger and retains an unused legacy requireControlled method. These are identified static review concerns, not proven/fixed findings. Do not remove checks without equivalent coverage.

3. Complete genuine S/A/F eligibility and assembly validation. Current three projection tests substitute provenance and readiness seams; assembly test substitutes archive admission/JDBC. Need actual combined HTTP permitted/blocked cases for exactly generation-inputs/current, generation-inputs/{inputId}, candidates/{candidateId}, including owner/source/final/publication/current candidate fences. Query must never claim, enqueue or adopt.

4. Initial settings now uses selected discovery limits and lockGraph identity union, but full positive/negative S/A/F runtime evidence remains. Generation fact-only preparation and normal fenced replay now have causal offline tests; external-context bridge is a substitute, not actual external-engine/runtime proof. Verify current/retained writers and ordinary generic Task7 fixed-input fallback.

5. Converge special final/replacement/private-publication capability checks with ordinary guard declarations; do not make the ordinary freeze block a genuine authorized publication marker/writer. Run related inherited regressions/OpenAPI lint+bundle and final compile after remaining changes. Current pause gate is 53 focused Java tests plus incremental compile only; not whole suite.

6. After complete B5 implementation and necessary validation, create a NEW complete source checkpoint/report and submit ONE full review to SAME /root/readback_subfunction_reviewer. Main verifies/fixes findings, SAME reviewer targeted rereview. Current partial checkpoint is never a B5 adoption or runtime release.

7. Only after appropriate source review and fresh separately reviewed bounded resource plan may genuine combined HTTP/worker/MySQL and lawful independent SUCCESS/B4 dual-ACL/browser acceptance run. Never reuse O6 UNKNOWN, old consumed windows/releases, or add SQLite observations (3/3 exhausted). No retained-runtime qualification was refreshed during this pause.

8. Remaining B3 qualifications: authentic legacy positive, actual saturated Task8 coexistence and exact O4 historical cause remain unqualified. O5/O6 actual evidence and limited scanner component are adopted; do not repeat them. B4 source/offline adopted but actual dual ACL/browser not qualified.

9. Continue remaining B6 transport/CAS/restart native+S3, whole Task11 adoption, Task12 web/browser, Task13 final integration including inherited WorkerShutdownTest four constructor-reflection errors and generic unfinished-attempt behavior, then final whole-branch review from lower bound 34cb226aefe99c67d3681af68d748b0aeea4e073. Stop before C/D/E/F.

## 5. 运行资源与不可重复执行的边界

本暂停批次没有读取私有运行配置、访问保留 MySQL/provider/relay/native/SQLite、重启资源或创建 release/window。因此以下是最后已采用的历史事实，不是暂停时的新鲜探测：

- 保留 owner `task11b2-d9e2fdcc611e4e21bb360e539ca46258`；MySQL 容器 `11009a6b34fef0416e651100fbde05a07067dbcbd973f1ba731c08b2281140ad`，schema `ama_t11b2_d9e2fdcc611e_behavior`；原资源限制和身份核验门禁不变。
- O6 `UNKNOWN` 继续受保护：publication revision19/RUNNING，operation current_execution8/epoch16/UNKNOWN，meeting r13/PUBLISH_FAILED/ever_published0，final未变。不得重发、关闭、改写成成功或通过重试制造 SUCCESS。
- 最后 relay 全计数49/28/1；provider2655441/start21557905，relay2655444/start21557924 是历史 retained身份。旧 native2655411/1156860 已死；不得由暂停或恢复动作擅自重启。
- SQLite 只读观察额度3/3已经耗尽。旧 Host、readback、grant、pipeline、预算诊断、O5/O6 continuation 及所有 consumed release/window 不得重跑。
- B2 I1/I2 已修复采用，O5/O6及扫描组件已有限采用，B4源码已采用；恢复时不重复这些已经采用的执行。真正新的运行需新的源码审查、fresh gate和唯一窗口。

## 6. 下一次“继续任务”的读取与执行顺序

1. 先读本记录，再读 W/RESUME_LATEST.md、current-state.json、本次 partial checkpoint 和第4节必要源码。只在矛盾时定向读旧历史。
2. 检查真实 Git 状态；逐项核对当前源码/1189快照和277证据索引。保留 WIP。若与暂停哈希不同，先定向辨别用户修改，禁止 reset 或覆盖。
3. 把本次用户暂停改为已明确恢复并保存更新前文档。沿第4节继续未完成 B5；不要默认重跑已通过的53测试或旧封存包，后续实际改动再做针对性回归。
4. 主 Agent 完整实现/验证后，才提交同一 reviewer 完整 B5 审查。后续源码审查、真实运行、Task12/13分别有其门禁，不得跳过，也不得把本 partial checkpoint 当成批准。
5. 完成剩余 B 目标，止于 C/D/E/F 之前；如果用户再要求暂停，以当时最新明确指令为准。
