# B 阶段暂停与精准接续：fix02 最终复审待启动

保存时间：2026-10-25T20:02:25.860572+08:00。用户明确要求“完成当前调用之后就停下，记录并保存接续信息”。**当前为用户暂停；不再实现、测试、编译、派审或启动业务。仅后续明确“继续任务”解除本次暂停。** 本文及 W/current-state.json 最新暂停节点优先于旧文档中的 ACTIVE、fix01、旧 HEAD、旧 provider PID 和旧暂停时点。原目标仍是完成全部剩余 Phase B（Task11→12→13），完成后停止，不进入 C/D/E/F。

## 唯一入口与工作树

- P：`/home/tanren/ama/.worktrees/phase-b-completion`；分支 `codex/phase-b-completion-20261023`。
- W：`P/.superpowers/sdd/2026-10-23-ama-phase-b-remaining`。动态状态 `current-state.json`、完整过程 `progress.md`。
- 主树 `/home/tanren/ama` 仅作发现入口；本轮产品修改仍在 P。两树本次实际 HEAD 都为 `739193dff6dea6c28999260d4d332f99f1a58d12`。本轮未提交、未 merge、未 push，不将旧历史推送当作本轮已保存到远端。
- 源码、未跟踪新文件、证据、私有运行目录与数据库现场均保留，不 reset/clean/stash/drop，不用旧快照覆盖现有树。
- 作者 `/root/b11_fix01_continuation` 已停止，最后 session94470 exit0，无持有中的运行 session；原失败批次2458 exit1。独立审查者 `/root/b11_fix01_independent_review` 已完成 collector 窄审，未派发 fix02 最终复审。恢复时优先原作者/同 reviewer，若句柄不可用须携带完整材料接替，不从零重做或重置修复轮次。
- 当前修复轮次 **2/5**。Tasks1–10 已采用；fix02 作者工作与定向测试已封存，**独立结论尚未产生，Task11 整体未完成**。

## “继续任务”后的首个行动（不先跑测试）

1. 读取本文 → W/current-state.json → W/task-11-replacement-fix02-report-20261025.md → source/changed-paths/window-index；核当前 HEAD、源码/快照哈希及已保存证据。root 在用户暂停时正在执行的校验已完成：**1040 份当前源码及对应快照全部匹配**。不用为恢复而再跑已完成业务链。
2. 根据 immutable fix01 manifest（983 文件，SHA `65b03c0f95d9a9921fd146a7bf00c8395b8dec85fd18fede79857ee8aebf1240`）及其同名快照，与 fix02 的 1040 文件快照生成 **69 路径 scoped diff**，编写最终复审 brief。**这份 diff/brief 尚未生成，最终复审尚未派发。** 不用 HEAD~1 或 merge-base 代替 immutable FIX_BASE。
3. 派给同 reviewer `/root/b11_fix01_independent_review` 做完整 fix02 scoped assessment，明确检查 F1、I1/I2 组合证据和 I3/I4/I5 修复/剩余资格，保留每窗源码差异、失败及补充证据限制。初始独立报告、fix01 FAIL、staged source assessment、ordering/budget ordering/A2 custody/collector addenda 都在 W。独立 review 不是作者自审，不能用 root ACK 或工具 PASS 代替。
4. 如 reviewer 提出问题，原作者按同一 fix loop 定向修复和必要 RED/GREEN，再由同 reviewer 复审；不要泛化成无条件重跑历史状态性测试。若通过，继续 **完整 Task11** 的 remaining map，而非宣布 Task11/B 已完成。
5. Task11 整体审查/采用及最终契约稳定后推进 Task12 真 UI/浏览器，再 Task13 最终集成与 fresh 全分支审查。Task13 固定审查下界 `34cb226aefe99c67d3681af68d748b0aeea4e073`。

恢复时读资源实际状态，不能永久信任 PID/端口；只读 repin 不等于要求重建资源。任何重启/新发送/数据库变更须遵循既有精确资源与已审范围，旧窗口 ACK 已被消耗，不可重用。不要先启动 whole-pool worker。

## 已封存的最终复审包

以下全部位于 W；哈希为本次实际核验值：

| 文件 | SHA256 / 内容 |
|---|---|
| task-11-replacement-fix02-report-20261025.md | `e7879df6dd06b55ad703d8b8de8e859935c6ed781f34342a69ff3d81257245a9` |
| task-11-replacement-fix02-source-20261025.json | `4808d1f30f2eea7c932507d9d6ac9160228d41e4b71a8334b4b0a682cef74feb`；1040 文件，旁边同名目录保存实际字节 |
| task-11-replacement-fix02-changed-paths-20261025.json | `d396525dbe2dd211c9eb16bc8136bc37ddd7f253b6c6c42a379d1df495ef5137`；69 变更路径 |
| task-11-replacement-fix02-window-index-20261025.json | `246ee0f88cd2c5e61e42a442b73624ec2c5e043575b41d4956cbd02a7c1e75a7`；46 原始窗口，包含 FAIL/ERROR/0tests 与逐窗来源差异，**不是 46 窗全绿** |
| task-11-replacement-fix01-report-sealed-before-fix02-20261025.md | `0a47515617d67b79a1a92634705be579a1d4fd4e042506753334825031c86746`；fix01 报告原字节归档。原路径随后仅追加 fix02 摘要，新的 SHA `aeb4a74f25ba677c1217c1b48f1137adaef64738ba993c002f9f2a403fad283a`，不可再将原路径当旧哈希原件 |
| task-11-fix02-final-four-root-check-20261025.json | `83f59731cb8bf2380088909307d04f0ace86e921bd8099b5228f5e30f61bc74a`；最后 4 窗实际 XML/结果/来源和 URL/result 恢复断言核对 |

最后三份纯测试/URL02 窗口 source-manifest SHA 均为 `102d2ca47fc223bc87466953ad7d01747ad1eccc33c56cea0a3f5575db66e67c`。权威哈希优先实际 result/index/root-check，报告表格是人工说明。公开源码快照不包含私有 env、数据库、运行 store、target 与全部证据；必须保留整棵 P 及 W，不可只留下清单。

## 本轮已完成，勿重复

- fix02 产品修复集中 9 个 transcript adapter 文件；321 个 staged 产品/provider 路径在状态性验证中一直保持冻结。历史 GET 不依赖当前 source 资格、worker 按每字段较小上限先量度后正文、count+1 SQL 上界、原 wire canonical/语义验证均已实现，最终独立闭合待审。
- B：真实历史 GET 后 fact SUCCEEDED / domain 未应用；来源故障 RED/GREEN、unapplied readiness 已完成。
- C：两个真实拒绝前序、8 个预算维度正式窗口（54 次实际调用，14 表前像一致）、新 final/new key 接纳、真实 replacement SUCCESS LOSS、跨 operation manual 后当前真实 SUCCESS 仅记事实而不应用 domain 已完成。C3b 四个 2729 限制对真实 2730 manifest，metadata prefix681/body0，实际一次 expiry-only retry 有证据；C2 无该实测不能混用。
- M：真实 UNKNOWN→正常 reconciliation→唯一 mapping。UNKNOWN readiness 断言在后来 enum 错误导致的整窗 FAIL 中，仅部分到达证据；mapped continuation 为正式 1PASS，不把两者混成 UNKNOWN 全窗 PASS。
- A：真实初始拒绝、same-key no2、final S/F 竞争、实际 session.begin source failure 后 sticky 不重试、旧 receipt contradiction 后同 op manual。A2 业务通过但旧 collector 的时间边界失败，详见下一节，禁止重做 A2。
- collector 工具修复：preclear→seal 持独占 flock、child-exit 与 observed collection-close 分开、有界稳定 stat/hash/XML/log、monotonic；6 类离线测试通过，独立窄审 PASS SHA `d44e072db3fea8225ce842a957ea7065d2ac1efd2ba476f3a19a474bb3d91d49`。该锁仅约束合作 runner，不宣称全机排他。无需重跑六项来恢复会话。
- 最后窗口：manual01 **1PASS**；URL/result01 **1FAIL 保留**；修订 URL/result02 **1PASS**；transfer-final01 **3PASS**、manifest-final01 **7PASS**、errors-final01 **1PASS**。两次 skipTests compile 都0。新 collector 已实际用于这些窗口，最后批次全部结束。
- manual01 实际 lease_valid=0（expires 11:27:28 UTC，查询 11:47:25 UTC），只声称 manual + stored RUNNING/PREPARED 组合阻断，不声称 live lease 或 isolated manual-only。
- URL02 的真实 URL 长度变化为 PROVENANCE consistency 拒绝，不是 URL budget；真实来源 MINUTES_GENERATE operation `6ffdda35-75fd-49b6-90a0-cba6bcf133d6` 的 external_result_id 为 BUDGET/body0；发布 op 的 result 仅存在性投影、未修改。正常 exactPK SQL 可达先验证，两个 fault 都同 physical bound rollbackOnly，12 表前像/原值恢复，provider 流量不变。

## 失败证据与限制绝不覆盖

- A2 原 `replacement-fix02-same-operation-marker-01`：collector exit1/process0/tests0/qualifiedfalse；raw JUnit 1PASS SHA `9b8a6884605854ae415a19939c509a1c002347b4fd5043bff022985597a0b869`。mtime 超旧 child-end **53,488,479ns**，根因未推定。独立 supplemental custody addendum SHA `4f5eb2f0c58af8ce6666eeef46e969a93ef884987c0755b2b97c1ae913630c04` 接受有限补充业务证据；原 FAIL 不变，无历史 OS flock/ps 证明，root 串行记录仅辅助。
- URL/result01 共用 loop line10，实际没有预期异常，但未在 finally 保存目标/注入轨迹，不能倒推哪一个 kind 失败。root 曾写“first URL case”已在 `task-11-fix02-url01-root-firststop-erratum-20261025.json` 更正。URL02 成功不能重标 URL01。
- 其他 RED/worker harness FAIL/JDBC codec ERROR/OOM tests0/telemetry 原因 UNKNOWN/M enum FAIL/I5 首10例在失败窗的部分证据全部由 final report/index 保留，不累加重复测试数、不转成全绿。
- public FINAL_NOT_READY 当前通过 /error 返回403的问题仍 OPEN；本轮验证 typed failure +真实 readinessGET，不声称最终 POST409 已正确。
- generic `T11-GENERIC-ATTEMPT-FINALIZATION` 两个未结束 attempt 缺陷仍 Task13 OPEN；删断言后的当前 PASS 不是修复。

## 不能遗漏的后续任务

完整逐项权威：W/task-11-post-fix01-remaining-map-20261025.md SHA `d770e0d22e1d2d0912409f059f2c20a82553279443a05b4f32e5eda825bb0488`。它起草于 fix01 时点，节点0/1/11中本轮已做部分必须用 final fix02 report/index 增量核销，**不能机械重跑整表**。R1 权威为 W/task-11-final-publication-design-r1.md（SHA `f18ae4734603709284d91345b2909eca9687627c825444fd8cfa06dd49d36ffc`），不是无 -r1 旧版。

Task11 尚欠：exact unchanged zero-effect retry HTTP；local failure/recovery 剩余 lifecycle/race/rollback；maintenance 正向/>64/fairness/actual-loop/restart；owner/member final/publication/history 与 bounded pagination；destination discovery、same-origin document relay 的 fresh 双 ACL；全部 mutator freeze 与合法 closed-history reopen；MinutesConfiguration initial/generation 真正 activation、T10-M1 三条旧 eligibility 投影及 HTTP；final HTTP error mapping、producer OpenAPI/root integration、严格 parser/replay；strict provider transport/dedicated HTTP-body admission、fresh app JVM/original-store restart/native+S3 combined 两条真实链；final/readiness/marker/observe 剩余安全、预算、事务竞争矩阵；whole Task11 SPEC/quality 独立审查及合法采用。

Task12 尚未实现：已有 static-entry-index/approved web-loop R1/deferred-boundary map 可复用。等待服务契约稳定后机械生成客户端；真实 UI/native+S3/TLS/四独立身份 session/浏览器验收/typecheck/build/409草稿恢复/只读回放/重启；接收 T4SR-04/05/06、T6SR-03、README/generated 等遗留，不 route.fulfill 或 SQL seeds 冒充真实链。

Task13：最终相关检查、fresh 独立整分支 review（固定下界见上）、具体修复与交付记录。逐项接收 pending-issue-register、cross-task closure map：Task9-F5、B-FINAL-REPLAY-INTEGRITY-01、T8SR-M1、generic attempt defect、T03-IR-M01/M02、FUP-B-REMAINING-TEST-01/02、T8-HIST-OPTIONAL、T11PROBE-M1、Task7 Q01/Q02/Task9旧UNKNOWN原因等。准确披露 B01 IMM PARTIAL/B04 Q401 UNKNOWN、公司接口/HA/capacity/native physical I/O 资格边界，不因局部 green 自动关闭。

## 保留资源与禁止消费的现场

- MySQL CID `11009a6b34fef0416e651100fbde05a07067dbcbd973f1ba731c08b2281140ad`，46151，768Mi/1CPU/pids256。shared MySQL46653/S345501保留；所有已建库正常16 migrations/rerun0均完成，禁止重复 provision/bootstrap/reset/repair/outOfOrder。
- provider 最后只读核实 PID=PGID1555480/startTicks17005861/46287，loaded SHA `6141572ab7f9376760261894f233f8bf3a24c23e366ca8d4091fd300c8c6a658`。同原 storeInstanceId `98863bbd-a519-4553-9a09-1246ab221ec9`；原 store `P/.tools/task11-9714255c9e2c4861a2795cfaaec8e97f/knowledge-fixture-20261025/runtime/knowledge.sqlite`。已按一次精确 ACK 原库 initialize=false 重启过，**不要重跑 restart helper**；旧 PID231675不是当前。暂停不关闭或重启它。
- fix02 schema `ama_t11fix2_075249f8161d_behavior`，owner `task11fix2-075249f8161d4ad68373955ff0333384`，fixture `task11-fix02-isolated`；私有 env在该owner目录，0700/0600，不输出秘密或其hash。JVM512Mi/2CPU，native64Mi/2CPU，重测试串行。
- 主库 held no2 永不自动 claim/send：pub368767a5-c477-40fc-8e27-13705667c5a0 / task1a8dc6bb-2076-44d2-8ff0-aa1b5f630e27 READY0 / exec427e9751-2bca-483b-8d91-cb1711ae127c PREPARED / opccad27fc-5cf1-432b-83f8-a71341efcff5。
- A held no2：pubadaed37b-2733-422a-af76-47853fadc771 / taskf91d8573-7f2b-4d37-963d-cee87b48c923 storedRUNNINGattempt1 / exec1afb5f06-2130-483a-9f7a-fb7d57596895 PREPARED/unmarked / ope130b98a-c2a9-4f94-8308-6ba20afa2c68 MANUAL_REQUIRED/current2；自然 lease 过期不授权消费或修状态。
- oldfix01 schema ama_t11fix1_49609b94161e_behavior：op6c2d66a1-48d9-43cb-8d3f-a471e8711976 manual；no4task6ea8661c-fe81-4bf1-b1a9-3d2a75bf412d storedRUNNINGattempt2/noexec，禁止 whole-pool worker。
- 原3个业务UNKNOWN永久独立保留：T11 exec07327d2f-3b2f-4bc1-ba6d-f9f03246d08a；T10 exec0469c111-f07f-4428-acb8-3447ec0894ad；T10 execfd323464-54c7-4d80-ae93-ac0cd7925663（SQL MARKED、provider归属缺失，业务UNKNOWN）。不由新成功推断零effect/重发/关闭。
- C 当前真实成功doc-d65ee9e35c4961e7b358d341049b9171仍 unmapped（旧op manual、当前factSUCCEEDED/taskSTALE）；B success未应用；M真实唯一mapping保留；都不能变更为便利新测试的状态。

## 本次暂停保存范围

W/pause-20261025-fix02-review-pending 保存本次 state/两树handoff修改前原字节、Git status/HEAD、脱敏限定进程观察及保存核验。观察仅见所匹配 provider，未见匹配的 Maven/测试进程；不是全机不存在任意进程的证明。作者确认无运行 session、测试 native按teardown关闭，无新relay。没有为暂停查SQL、重启/终止服务、提交或推送。最后在途报告写入完成后，所有作者实现与测试均停止。

发现入口为两树 DEVELOPMENT_HANDOFF.md 顶部及旧暂停文档顶部链接。后续只需“继续任务”即可按本文首节点恢复，不能从旧 Task9/fix01/首final RED重来，也不能跳过本次未派的最终复审。
