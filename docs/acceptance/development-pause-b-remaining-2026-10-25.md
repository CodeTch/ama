# 最新 Git 集成已完成（2026-10-25）

按用户明确指令，当前代码快照已合并main并推送GitHub。代码快照 `0df61e2e20f8`，main合并 `095b678bf09c`，已实际核对远端；详见 `docs/acceptance/integration-main-snapshot-2026-10-25.md`。本次为未验收WIP保存：Task11仍暂停于fix round1、5Important未复审，SEND DENY；原no2禁止claim/send，UNKNOWN不关闭。后续“继续任务”按最新暂停文档的具体未完节点推进11→12→13；旧HEADba48及不合并/不推送措辞为历史，实际HEAD以Git为准。

---

# B 阶段暂停与精准接续 — 2026-10-25 06:35:07 Asia/Shanghai

用户最新指令为“停止任务，将当前相关任务状态信息记录，编写接续文档”。**开发已暂停**；未来仅在用户明确“继续任务”后恢复。旧 ACTIVE/首final RED/迁移待跑等段落均为历史，以本文件和 W/current-state.json 最新 pause 节点为准。原目标仍是完整完成 B 后停止，不进入 C/D/E/F。

## 唯一工作树与现场

- 产品树 P：`/home/tanren/ama/.worktrees/phase-b-completion`；分支 `codex/phase-b-completion-20261023`；当前 HEAD `ba48f35e7ef97c1e162a2d373453bd1e9689529c`（Task10 采用）。Task11 大量未提交修改完整保留，未提交、未采用，不能 reset/clean/stash/drop 或恢复旧冻结版本覆盖现场。
- 主树：`/home/tanren/ama`，HEAD `c71a44adb679a78d4c28dde7a4223aad1a23b40f`；只更新精准发现/接续文档，不合并 main、不 push、不生产发布、不发外部消息。
- W：`/home/tanren/ama/.worktrees/phase-b-completion/.superpowers/sdd/2026-10-23-ama-phase-b-remaining`。暂停前状态/发现文档原字节、当前公开源码清单、tracked diff、index、测试结果索引及脱敏进程观察均保存在 W/`pause-20261025-063507`。公开源码清单仅限记录的代码前缀，不冒称扫描所有原始证据。
- 暂停时 collaboration.list_agents 只返回 root，无活动子代理。原协调者最后因 usage limit 失败；旧作者/审查者句柄目前也不在线。后续先尝试原身份，实际不可恢复才以完整精确brief接替，同一 fix round1 不重置、不从头实现。不得在受限时循环重派。
- 暂停快照未检测到本轮 Maven/Task11 测试进程，最后 bootstrap 已结束。保留现有数据库、接收端及其他服务；未杀无归属旧 Java/Node。脱敏快照是限定观察，不是所有数据库“无债务”证明。

## 精确首个接续节点（不能直接发起发布）

1. 先读本文件 → W/current-state.json → `task-11-replacement-fix01-author-brief-20261025.md` → `task-11-replacement-independent-source-review-r1-20261025.md`/coverage → 最新 fix01 的结果索引/源码快照。**Task11 replacement fix round1/5，五项 Important 尚未独立闭合，SEND DENY。**
2. 先诊断新发现的通用回归失败：`artifacts/phase-b/b-remaining/task-11/replacement-fix01-generic-external/result.json` 为 1FAIL/0ERROR，`Task11AffectedExternalIT.genericMarkerFailureRetryReplayAndCurrentSuccessStillUseOriginalProtocol`，断言 expected0/actual2（压缩源line5）。该失败窗测试源码SHA为6c241fe9…；当前源码已改为89ccd38d…，后续bootstrap未运行此selector，**尚无当前源码GREEN**。根因尚未定位，不能猜成夹具错误或产品错误、不能改期望掩盖；先读 sealed XML/log/source 与 fixture。这个未处理失败此前未进入协调 state，现显式加入。
3. 隔离新库 **正常16迁移/rerun0已经实际通过**：`replacement-fix01-isolated-bootstrap` exit0/1PASS，完整history文件在该窗。state 原“MIGRATIONS_PENDING”过时；复核现有证据与资源连接即可，**禁止重复 provisioning/bootstrap**。
4. 沿当前源码完成 fix01：真实 marker/跨 operation 矛盾阻断、历史同 key 失败ACK/矛盾、I3 SQL追踪、I4 全链同ledger、I5 完整前置证明、fresh currentSources，补仅欠缺的针对性验证；冻结新源码与修复报告后交同独立 reviewer 增量复审。预期 `task-11-replacement-fix01-report-20261025.md` **目前不存在**，不能引用为已完成报告。
5. 复审明确通过后，协调者再核原接收端进程/loaded SHA 与磁盘版本、同store/keys/port有界重启 ACK，才可考虑解除真实 replacement SEND 门禁。不得用旧R1 provider纯测试替代新源码实际运行资格。

## 必须保护的未发送执行与隔离

原主 Task11 schema `ama_t11_9714255c9e2c_behavior` 存在已接纳待审执行：publication `368767a5-c477-40fc-8e27-13705667c5a0`；task `1a8dc6bb-2076-44d2-8ff0-aa1b5f630e27` READY；operation `ccad27fc-5cf1-432b-83f8-a71341efcff5`；execution `427e9751-2bca-483b-8d91-cb1711ae127c` PREPARED/no2，markedAt/taskAttempt NULL。**禁止 claim/send**，也不能先认领再跳过、改状态假隔离。真实队列只能按pool claim，无合法task选择接口；不要为测试另造生产精确claim捷径。

负例使用已建隔离 schema `ama_t11fix1_49609b94161e_behavior`，owner `task11fix1-49609b94161e4c90b6438ba683bfa3b1`，fixture名 `task11-fix01-isolated`；ACK `task-11-fix01-root-fixture-application-ack-20261025.json` SHA `35e262369af28a4d4ac70604538da152df5372ef5c286c566ebc40694650c027`。helper仅1次CREATE/GRANT，之后mount列表顺序比较失败；排序等同与env-only后验完成已封存，追加DDL0，原失败保留，不能称原helper PASS。新env0600，当前已完成16迁移，勿再假设空库。provider仍原owner，不复制密钥/初始化存储。

## 已完成，不重复

- Tasks1–10均已限定采用（含7/8）；Task9 source591545a4/adoption97e788fe，Task10 source86777940/adoptionba48f35e。原测试/独立审查及资格限制原样保留。
- Task11设计R1、preflight、29项SQL probe已采用准备；V013_4正常16迁移/current model/原15前缀、主库bootstrap及新隔离bootstrap已执行。主库bootstrap旧collector缺beforehash只具后验限定，不重写历史。
- 真实最终确认/冻结读取、主发布 main03（单份远端文档＋本地mapping）已通过；成功原execution GET1/POST0通过；丢响应后真实fair reconciler查询恢复通过，仍同execution/count1。该重启是同JVM应用context，不是fresh-JVM。
- 真实永久签名拒绝及原GET读回通过；原zero-effect-01因日志日期序列化整窗ERROR保留。local-terminal02、local-recovery03、HTTP安全与精确异常映射、maintenance负向扫描通过；maintenance正向/大于64/actual-loop未通过资格。
- 替代 accept04仅事务接纳/no publication POST；privateboundary、真实最终outbox写后整事务rollback、gap纯10项已通过。独立source gate仍失败，不可等同发送资格。
- fix01纯manifest GREEN5，结构compile重复5非新增distinct，privateboundary02/旧failureACK02/read-trace02各1PASS；shared-ledger与transfer-boundary各1PASS。它们仅覆盖具体断言，不关闭整项I1–I5。详见暂停索引，所有RED、ERROR、0tests、失败旧窗保留，不能加总重复测试为单一全绿套件。
- Task12仅静态入口准备完成：`task-12-static-entry-index-prepared-20261025.md/.json`，44限定路径/现有meetings Phase A壳/批准prototype锚点。没有Task12实现、浏览器或端到端验收。

## 五项独立阻断与未完功能

审查 `task-11-replacement-independent-source-review-r1-20261025.md`（SHA d8eea4beeb90ea9470d2f114a425e28d80b6440cdedfb42c87a361eecbf2f595），SPEC Fail/Quality Needs fixes，0Critical/5Important/0Minor。初审74文件manifest b8012cc81f0e8de43f74e3cf6c4107da8f01029f8863c3cda52e02180af47109是旧candidate，不能覆盖当前修复源码。

|项|要求与暂停状态|
|---|---|
|I1|完整同publication old/current TaskSpec union；旧失败ack已有真实GET通过，contradictory success隔离仍欠|
|I2|跨旧operation矛盾/manual必须冻结新marker/current effect，保留事实记录；已编辑，真实race/marker仍欠|
|I3|metadata-only pre-discovery，持根/父/行锁量度并读取LONGTEXT，锁外parse；read-trace局部通过，全路径仍需证据|
|I4|一次有效累计ledger覆盖发现、S/F/lookup、proof响应、parse/render/transport copies；部分通过，policy.prepare freshledger遗漏刚修，整链未复审|
|I5|三个reader共用严格manifest/transition原绑定＋真实完整predecessors/generation；纯5通过，完整历史语义与运行验证欠|

另 currentSources边界#4必须在真实SEND前闭合：current-effect用fresh source；historical/fenced receipt读取不能因此依赖当前EDIT/source。不擅自更改评审分级。矛盾SUCCESS测试只能按现R1固定endpoint/transport＋精确receipt binding认证；只有永久FAILED要求Ed25519 proof。test-only矛盾声明不等于真实远端文档effect，不能新增未审SUCCESS签名协议、改旧FAILED收据或旧UNKNOWN。

Task11后续仍需：实际replacement消费/改destination同key/new-final new-key/真实local-only PREPARED序号缺口/provider proof-CAS竞态；exact retry HTTP；真实revoked/fenced fairclaim与RecordedFactRecoveryPolicy；maintenance正向旧兼容崩溃/>64游标/actual-loop；owner/member/final/history/views/destinations及same-origin安全HTML relay双ACL；全部mutator冻结/容量/预算/rollback/race/strict parser/auth；fresh-JVM、combined actual runtime/native+S3；关闭Task10 M1三旧资格投影。完整矩阵看 `task-11-implementation-report-20261025.md` Latest checkpoint及R1§11–12，报告旧pending/最新通过冲突以sealed窗口及此暂停补充为准。Task11独立总覆盖/提交/采用尚未完成。

Task11采用后接Task12真实UI/四session/TLS native+S3浏览器/typecheck/build/草稿409幂等只读与重启；之后Task13相关最终gates、最强fresh独立全分支审查（固定下界34cb226aefe99c67d3681af68d748b0aeea4e073）、限定修复、准确验收/交付文档，完整B后停止。

## 永久保留的问题与资源

- 三个原UNKNOWN分别保留：Task10 execution `fd323464-54c7-4d80-ae93-ac0cd7925663` 原provider归属缺失；Task10 `0469c111-f07f-4428-acb8-3447ec0894ad` 原GET UNKNOWN；Task11 `07327d2f-3b2f-4bc1-ba6d-f9f03246d08a` 原GET UNKNOWN/APPLIED、taskRECONCILING/noeffectNULL。**禁止推断NOT_SENT/FAILED_ZERO_EFFECT、重发或由新成功关闭**。精确custody/disposition见pending register与各原fact文档。
- Task9 F5 extra preparation-F Minor、B-FINAL-REPLAY-INTEGRITY-01旧4caller mixedPROFILE、T8SR-M1、Task6/Task4 frontend遗留、Task7 Q01/Q02原未知失败与所有旧限定，均需Task13明确裁定；先读 `task-13-pending-issue-register-20261024.md` 和 cross-task closure map，不以旧采用豁免。
- 已有B01 IMM PARTIAL/B04 Q401 UNKNOWN、企业SSO/目录/model/KB合同缺失、HA/capacity/native physical I/O不合格边界保留；不推进C/D/E/F。
- 原owned MySQL CID11009a6b…，实际loopback46151（旧44727仅历史）；同容器768Mi/1CPU，先前OOM原因细分UNKNOWN，禁止重建/reset/repair/outOfOrder。新测试库均在同资源。原shared MySQL46653/S3资源保持，实际liveness后续read-only核。
- 受控KB当前最后已知PID=PGID231675/46287，原storeInstanceId98863bbd-a519-4553-9a09-1246ab221ec9，原`.tools/task11-9714255c9e2c4861a2795cfaaec8e97f/knowledge-fixture-20261025/runtime/knowledge.sqlite`。本次进程快照仍见同PID；loaded SHA cd80e61d…，磁盘已有后续修改，**活进程不等于新源码已加载**。保留数据库/WAL/SHM/凭据，未来只能精确检查后原store reopen initialize=false，缺库拒绝、不换store/keys。暂停未重启/清理服务。
- 私有env/bearer/key只保留忽略目录0700/0600，不输出值或secret hash。JVM测试512MiB/2CPU，native/provider子进程64MiB/2CPU；重测试串行、单产品写者、审查只读。

## 暂停记录核验

暂停未新增开发/测试/SQL、未提交/merge/push。最后迁移与各结果只读采集。根的第一次只读ps命令因PowerShell转义产生awk语法错误，改为脱敏/proc观察，无业务影响。所有旧文档保留历史，在主树及产品树发现入口顶部指向本文件。后续“继续任务”撤销此用户暂停后，按首个接续节点推进，不能从旧Task9或首final RED重来，也不能跳过fix01、独立审查和剩余11→12→13。


## 文档校验补充（2026-10-25；仅整理，继续暂停）

**先做只读重新定位，再恢复任何测试。** 对照 W/pause-20261025-063507/current-public-source-manifest.json 核当前968份源码、HEAD/index及两个fixture ACK；源码有差异先判断是否是之后合法修改，不回滚覆盖。核当前CID/端口/原store/process-startticks/loaded source（均是暂停时快照，PID/port不能当永久事实）。不得先开全pool worker；原主库no2仍禁claim。资源不可用时保留UNKNOWN/原库，不能重建；旧agent不可恢复或指定模型额度不可用时保留精确brief，不循环重派、不擅自降低评审门禁。

### 原失败与当前源码必须分开

`replacement-fix01-generic-external`失败窗测试原SHA `6c241fe9da6b72788e5ac11d8d17dfbf2e8e4f2456a0b1e758aa557ca94c4fe9`（2249字节），当前为 `89ccd38df03a3e18f114c52143c39d63ec4670a6070cb9945ad5dfcac1f4a61b`（2322字节）。到后续bootstrap共8路径改变，包括两份产品源、测试和launcher，详见 W/pause-document-audit-20261025/generic-failure-source-window-comparison.json。不能把bootstrap成功当generic回归通过，不能把旧失败直接归因于当前源码。后续先读原sealed log/XML/manifest与当前fixture，确认已有改动的目的和失败原因，再只跑必要selector。原窗命令为 `/usr/bin/python3 tools/checks/run.py it Task11AffectedExternalIT`，但**不能脱离固定fixture/ACK/私有env直接执行这条原始命令**；调度入口是 `tools/task11_window.py`，读取其当前接口与对应command.json/ACK，明确schema后再运行，避免默认连错库。此处只登记证据与入口，未修产品或测试。

### 显式遗留分工，避免大类摘要漏项

|负责人窗口|必须接收的具体事项|
|---|---|
|Task11|T7CV04剩余final readiness-before-advance、publication read/application projection；T10-M1三条旧eligibility投影及实际HTTP；独立source I1–I5/fresh currentSources、generic当前源码未GREEN；全R1真实安全/恢复/运行时矩阵|
|Task12|T4SR-04列表limit溢出2147483648应400；T4SR-05 partSize正long定义/原token不丢精度；T4SR-06 Redocly命名告警与外部banner区分；T6SR-03四mutation aliases含unknown；generated.ts机械再生成与校验；web README仍称health-only的过时描述|
|Task13|Task9-F5、B-FINAL-REPLAY-INTEGRITY-01、T8SR-M1实际prefetched-candidate删除竞态；FUP-B-REMAINING-TEST-01/02；T03-IR-M01/M02；T8-HIST-OPTIONAL；T11PROBE-M1 prepared report四/五blob措辞；Task7 Q01/Q02及Task9原首次观察失败原因UNKNOWN；所有旧来源窗口/资源/collector限定与三个原UNKNOWN|

这些事项有的要求修复、有的仅要求最终独立审查裁定/披露，不能全部宣称产品缺陷，也不能自动关闭。精确规则以 pending register、原review、既有adoption及当前R1为准。

### 权威输入与明确文件

- 总计划：`docs/superpowers/plans/2026-10-23-ama-phase-b-remaining.md`；Task11：W/`task-11-final-publication-design.md`，重点§8及§11–12；fixbrief、独立报告和coverage分别为 W/`task-11-replacement-fix01-author-brief-20261025.md`、`task-11-replacement-independent-source-review-r1-20261025.md`、`task-11-replacement-independent-source-coverage-r1-20261025.json`。
- Task12批准设计：`docs/superpowers/specs/2026-10-23-ama-phase-b-web-loop.md` 及 W/`task-12-web-business-loop-design-r1.md`；复用 `task-12-deferred-boundary-map-20261024.md`、`task-12-transcript-request-type-followup-20261024.md`、`task-12-static-entry-index-prepared-20261025.md/.json`，最终Task11接口采用后只作增量repin，不重复设计或旧完整preflight。
- 原UNKNOWN精确处置：W/`task-10-f2-original-unknown-custody-ruling-20261024.md`、`task-10-closing02-original-facts-disposition-20261024.md`、`task-11-original-publication-unknown-disposition-20261025.md`。合法fair claim可能只变lease/owner/time，需准确记录；不声称旧行逐字节未变。
- 资源完整CID/路径：MySQL `11009a6b34fef0416e651100fbde05a07067dbcbd973f1ba731c08b2281140ad`→46151；shared MySQL `eefdd9a984ee00fe6ca886168b29a6ce4f3b405467b8d3204240d4e1a8dc7502`→46653；S3 `a91f5946d5f619335d5cfb6bf2295b1df46abbb4a75b2db8feccaf637bcb7d51`→45501。完整公开ACK分别在W/`task-11-root-fixture-application-ack-20261025.json`、`task-11-fix01-root-fixture-application-ack-20261025.json`；env分别在`.tools/task11-9714255c9e2c4861a2795cfaaec8e97f/env.sh`、`.tools/task11fix1-49609b94161e4c90b6438ba683bfa3b1/env.sh`。只读路径不输出私有内容。最新已知KB loaded SHA完整值在state/原restart ACK；当前磁盘不是该loaded版本。
- 当前review model为B00-1.7-publication-submission（SQL99张表），`docs/database/ama-schema-draft.sql`及其他当前model/权威文档另记在 W/pause-document-audit-20261025/additional-model-and-authority-manifest.json。**它是review SQL，不能送入Flyway或声称99表全部已部署。** 旧state B00-1.6/Task11 DDL pending与44727已归档更正。
- Task13交付明确包括 `docs/acceptance/b/summary.md`、demo/test qualification、pending integration和DEVELOPMENT_HANDOFF/接续入口；按已固定下界34cb226a做新全分支审查，原W/证据持续保留。角色沿既有授权：核心作者/协调及任务独立审查Astra high，机械Sol xhigh，最终整分支Astra xhigh；实际可用性先核，审查不能由作者自审代替。
- 原Superpowers7.0.1路径在本次文档校验时已不存在，也不在当前available skills列表。此事实不恢复开发；下次继续先核工作流资源，保留已批准的单写者、串行测试、独立审查、DDL/source/send门禁，不因工具/旧handle缺失跳过，也不为文档校验安装或改模型。

### 保存与校验范围

暂停的968份公开源码清单是**哈希定位清单，不是完整文件备份**；tracked diff不包含untracked新源正文，必须保留现有整棵工作树。原64窗口索引也是结果/来源索引，不是最终semantic或全源复审，不可汇总成64窗全绿。原pause-record-verification.json是06:35时点的历史验证，文档更新后其旧hash理应变化，不能覆盖旧验证伪装从未修改；本次另存审计preimages与新verification。所有校验只读源码/既有证据，不运行测试、不查DB、不启动worker、不提交。开发继续暂停。
