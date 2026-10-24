# B 阶段暂停与准确接续 — 2026-10-24 09:30（Asia/Shanghai）

**状态：用户明确要求现在暂停。B 阶段尚未完成；Task 7 尚未采用。没有启动 Task 9，没有进入 C/D/E/F。**

恢复只需说“继续任务”。恢复者必须先读本文、同一工作树下 `.superpowers/sdd/2026-10-23-ama-phase-b-remaining/current-state.json`、`resume-20261024-active-context.md`、`progress.md` 与 `task-13-pending-issue-register-20261024.md`。本文是本次暂停的最新入口；较早记录中“进行中/等待审查/SQL-PROBE OPEN”等文字仅保留当时历史，以下状态优先。

## 精确位置与工作区

- 唯一产品工作树：`/home/tanren/ama/.worktrees/phase-b-completion`；Windows 路径：`\\wsl.localhost\Ubuntu-24.04\home\tanren\ama\.worktrees\phase-b-completion`。
- 分支：`codex/phase-b-completion-20261023`。源码 HEAD：`6cce4794dba473825d83fb9bccafc043cd611b5c`（Task7 fix01）；父源码交付 `84b4b0bbf8212ec5606c3f8583a0989f10af681e`；Task7 原始 BASE/Task8 采用提交 `de1611a67ba483afe21f3b29bb69c2d32028efef`。
- 上述 HEAD、索引和产品代码保持不变。暂停文档、根协调状态以及新证据文件保留在磁盘，**本次未创建 Task7 采用提交，也未把暂停记录另做中途提交**。恢复时保留这些根协调者文件，不清理、重置或混入实现者的精确修复提交。
- 主工作区 `/home/tanren/ama` 仍为 `main`，HEAD `c71a44adb679a78d4c28dde7a4223aad1a23b40f`；只更新两个发现入口文档，未改主分支代码、索引、提交、旧 stash 或其他工作树。无 merge/push/PR/生产发布/邮件。
- 全量最终审查下界固定为 `34cb226aefe99c67d3681af68d748b0aeea4e073`，不能改成当前 main merge-base，以免漏掉已独立集成的 Task1–4。

## 已完成与禁止重做

|任务|当前结论|恢复时处理|
|---|---|---|
|1–6|已实现、独立审查并采用；原失败/组合来源限制保留|不重新实现，不为恢复重跑旧门禁|
|8|已采用 DE；公平扫描与当前模型同步已闭环|不重做；仅保留 Task13 的精确预取删除测试 Minor|
|7|初始实现+fix01 已提交，复审仍有 1 个 Important；未采用|从下节 fix02 接续|
|9|设计、源对齐、post6 map、夹具 helper 静态审查完成；源实现未开始|沿用准备，仅在最终 Task7 采用后按实际源码窄范围重定位并激活|
|10|设计/源对齐完成；实现未开始|Task9 采用后按实际新源码接续|
|11|设计、235文件前检、R1 DDL 静态修订、helper 审查以及真实隔离 SQL 证据核验完成；产品实现未开始|不重复上述准备或未变 SQL 探针；完整激活门禁仍待执行|
|12|设计、边界 map、请求类型跟进与门禁输出转发准备完成；实现未开始|Task11 之后实施，带入所有已登记 API/类型问题|
|13|最终门禁/来源前检、Task8 预取删除测试方案已准备；最终验证/全分支审查/交付未执行|最后执行，不把准备当作完成|

稳定执行顺序保持 `1,2,3,4,5,6,8,7,9,10,11,12,13`。恢复后完成所有 B 阶段计划任务与最终审查，再按此前授权停下，写好下一阶段接续文档；不要自动进入 C/D/E/F。

## 恢复后的第一项：Task7 fix round 2/5（尚未派发）

先完整阅读 `W/task-07-fix01-independent-review-20261024.md`，SHA `1d4eee2f878e5dcde8fea04c03b07b4a579483c3e01390ddf24677739a55a18e`。`W` 指本工作树 `.superpowers/sdd/2026-10-23-ama-phase-b-remaining`。

复审：**SPEC ISSUES REMAIN / quality NEEDS FIXES；0 Critical、1 Important、0 Minor**。T7IR-02 发布 RR 快照、T7IR-03 矛盾隔离，以及 T7CV-01/02/03 框架测试缺口均 ADDRESSED。唯一剩余项仍计为 T7IR-01，不新增重复编号：

真实 `FAILED_ZERO_EFFECT` 回执已经提交，但确认丢失；授权用户随后通过 `retryAfterZeroEffect` 创建 PREPARED 后继执行。旧会话再试同一回执时，终态重放与新恢复分支都要求它等于 `current_execution_no`，因此永久 STALE_TOKEN，旧 pending 不清理，本地容量一的 Worker/对账器不能继续。后继甚至不必启动；全局许可回收不能释放这个本地槽位。

复审给出的路径是源码推导，**尚未为这个新残余执行 RED 测试或实施修复**。恢复后：

1. 重新读取 superpowers `subagent-driven-development/references/fix-loop.md`，再恢复原作者 `/root/b07_prepared_external_runtime`，派发 **round2/5**；不另起作者，不让根协调者改产品代码。原审查者 `/root/b07_independent_review` 可用于限定复审。
2. FIX_BASE 使用审查所见的 `6cce4794...`。保持专用 Task7 夹具与原单独应用 ACK；先封存当前报告/清单/ledger 等可变元数据，并为当前全部原始证据追加保护清单，覆盖 fix01 新原始文件，不能只保护最初 8669 项。
3. 按完整复审要求，用真实 DB、注册策略的旧回执/旧存储结果/完整不可变 spec/精确已结束 attempt 验证来有界确认旧结果、释放本地容量。保持 ACTIVE 旧令牌严格性；不得授予发送/业务写权限、重复 writer/终止 attempt、修改后继 epoch/owner/state/output 或结算后继许可。
4. 补确定性场景：旧零效果提交后丢 ACK→合法后继仍 PREPARED；容量一的实际 Worker/共享对账路径恢复并处理后续任务；后继已开始且实际占用许可时旧结果恢复不影响后继；拒绝外部/不匹配回执与 proof、错误旧结果、未结束/不匹配 attempt、UNKNOWN，保留矛盾隔离和 live-owner 拒绝。
5. 原作者跑与修改有关的验证、写报告并精确提交后，再做限定独立 SPEC+quality 复审及增量证据核验。未变 Task8 算法和已完成设计不重做。所有 Important 关闭之前不得采用 Task7/安装消费者/启动 Task9。

## 已完成验证与证据，不得伪装成未来源码资格

- fix01 源提交 `6cce4794...`：最终同源 **141 个去重 IT +254 个单元 =395 PASS**，原120 IT+新增21；17 IT XML/41单元 XML。原始30窗口和 fix01 的13窗口、真实 RED、设置/诊断错误全部保留。
- fix01 作者详细报告 `W/task-07-fix01-report-20261024.md` SHA `4878d259...`；公共报告 `artifacts/phase-b/b-remaining/task-07/report.md` SHA `bc510879...`；manifest `964c6661...`；ledger `3037e925...`。
- 独立机械核验 `W/task-07-fix01-final-delivery-custody-20261024.json` SHA `b6c55f52...`：21项 PASS，9143累计路径/450修复路径、9777采用保护项、8669初始原始路径、当前所有14生产文件及最终源码窗口、15条完整历史均核对。它不等于代码审查通过。
- 根核验：最终 IT `dd40e60d...`，单元 `19a7c292...`；机械报告8个输入/包封印实际核验 `a689bff7...`。最初误用键名的根核验草稿已原样归档，不作为成功证明。
- 根新鲜3个容器完整选择字段/名称/mounts和4个准确子进程 PID 检查 `5794fe40...`；PID1277931/1296943/1299071/1300741均已消失。仅说明这些受控进程，非全局无进程。
- 根已知6个私有值/PEM扫描 `3ba2f58c...`：9516文件/317531891字节零匹配，含Task7当前交付、4审查包、两份前像diff及全部369探针记录；不输出私有值或其摘要，不称通用秘密审计。
- fix02 修改相关生产代码后，上述通过结果保留为历史；按实际变化补验证，不能把395项回溯宣称覆盖新源码。

## Task11 新完成的真实 SQL 窄门禁

原精确 R1 SQL `195bec3b...` 未分配正式版本。helper修订 `ba481d2e...`、限定审查 `10618cb4...`；先前 V002 排序规则误判在执行前通过最终 V007 定义修正，DDL提案未因此改动，也没有实际失败的伪记录。

只执行一次新隔离库 `ama_t11_fc2e1fdaf6ad_probe`，owner `task11-probe-fc2e1fdaf6ad4f88b40a4baf410340d1`，沿用既有8.4.6容器。独立实际核验 `W/task-11-isolated-probe-actual-custody-20261024.json` SHA `f92d9440310a1c44858ab6bc7093d56909a2b28663099a7803919ebbb425d28a`，29项 PASS；根完整阅读并核验公共拷贝封印。精确 CREATE、19字段/6FK/4CHECK/6次级索引、严格词汇/尾空格/NULL元组/LOB字节界、6缺父键与12父键更新删除拒绝，已取得实际错误、rollback和残留零行证据。231MySQL命令/232总命令，17正例+55反例+55合法对照，不能称127个独立功能。

369份追加原始状态（159570091字节）保留在该 `.tools/owner/`；完整最终记录756260字节及首份intent、ACK、receipt、全部封印公开副本位于 `artifacts/phase-b/b-remaining/controller-task11-sql-probe-20261024/`，manifest `8f280464...`。ACK `3bf88ca5...`。根采用说明 `W/task-11-isolated-probe-actual-acceptance-20261024.md`。

**只关闭精确 R1 的隔离引擎/CHECK/FK 机制门禁。** 正式Task11仍待最终7/9/10源与完整前缀重定位、版本分配/冻结/正式独立应用 ACK、完整迁移/历史/模型同步及私有 writer/时序/发布运行时。不能把最小父键桩验证当作完整前缀或产品完成。SQL/引擎/父键条件未变时不重复该探针。报告四/五Git输入的字样 Minor `T11PROBE-M1` 仍登记，实际封印五份正确。

## 后续任务激活与待处理问题

Task9新夹具 helper 已静态审查通过，SHA `a868bc63...`，**从未执行**，无Task9 schema/env/新的凭据。只能在最终Task7真正采用后，由根创建固定公共 `W/task-07-consumer-activation-root-ack-20261024.json`，绑定真实源/采用提交、PASS/Approved/0重要问题及 `docs/acceptance/b/task-B-remaining-07-prepared-external-adoption-20261024.md` 精确摘要；当前这些采用文档/ACK不存在。之后实际源/前缀重定位、单次新Task9 schema创建/ready、再单独应用ACK，不能复用Task7全库扫描会写到的测试行。

完整待处理登记是 `W/task-13-pending-issue-register-20261024.md`，恢复必须逐项保留：

- Task7残余IR01；T7CV04未来真实消费者的 durable provider/归档producer-input完整闭包、三种终态replay、完整候选图、编辑/采纳/所有版本写入PENDING处置、最终readiness和发布投影。
- Task9真实首次settings/template/空排除源与owner/idempotentCAS/严格首次逻辑，不B03回填；enqueue不推进r/e或清debt；完整outstanding-generation闭包；共同领域锁顺序沿用既有裁定。
- Task11正式激活及零效果后同publication UUID新execution重试、UNKNOWN只查询、成功后永久只读/全部写入口冻结。
- Task12：T4SR04超大limit应400；T4SR05 partSizeToken正确正数定义；T6SR03四个生成请求别名不能unknown；T4SR06工具命名/外部banner区分；真实lossless HTTP/browser、所有投影与门禁新输出转发。
- Task13：T8SR-M1真实“已预取候选后删除”的确定性测试，已有新方案 `task-13-prefetched-deletion-case-prepared-20261024.md`，尚未实现/运行；原空洞覆盖保留。最终全分支审查、所有Minor triage、summary/demo/接续文档尚未完成。
- 保留heartbeat100ms、既有WARNING/deprecation/ByteBuddy、T03可读性、最初29握手错误原因未证、旧marker Optional空原因UNKNOWN等历史，不改写为洁净日志或已知根因。
- B01 IMM永久PARTIAL、B04 Q401 UNKNOWN、缺公司SSO/目录/模型/KB实际接口以及HA/容量/native物理I/O均未资格化；不伪造企业验收。

## 保留的资源与恢复纪律

既有共享MySQL46653、Task8所有者MySQL44727与S3 45501三个容器原样保留；CID/owner/镜像/完整mounts见 `W/task-07-fix01-root-selected-resources-pids-20261024.json`。Task7只用 `ama_t07_0cea85a0e472_behavior`，原单独应用ACK `ee73ff0b...`，正常15已完成、重跑0；不再初始导入。Task8的MAX overflow库有意耗尽，绝不能复用/重置。私有env/PKI/旧stash只留原位，不输出其值或摘要，不建新凭据/新容器。

没有本轮仍在运行的测试或探针；全部子代理已保存报告并停止。保留原始失败、source windows、SQL与15条历史、当前模型、旧golden。禁止 reset/drop/clean/repair/outOfOrder/旧SQL改写/历史去噪。Worker和测试JVM512MiB/2CPU，独立native/child/probe64MiB/2CPU；PowerShell通过字面here-string送WSL Python，避免shell拼接。恢复时使用子代理，按原计划自主裁决并登记，不等常规澄清。

**先修Task7残余，复审采用后才继续9→10→11→12→13；完成全部B再停。现在保持暂停。**
