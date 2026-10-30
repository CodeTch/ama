# 生成准备的自身续租与累计预算（R2）

**最终采用：2026-10-29 R2正式本地M1-B已验收。** 原BASE→V2独立全分支APPROVE，无开放P2+；完整范围见[正式验收](formal-local-b-2026-10-29.md)。以下分批记录及其中旧“待审/未完成”作为时间线保留，以最终采用及范围限定为准。

当前是限定修复/验证记录，正式 B 尚未完成。对象仅为日常单机 MySQL、本地文件及受控合成模型；不恢复封存资源，不更改 marker/外部执行/未知结果协议，不用 SQL 业务夹具或重置租约。计划见 `docs/superpowers/plans/2026-10-29-minutes-preparation-renewal.md`。

## 实际缺陷与因果定位

`bqual-minutes-heartbeat01.log`：真实 `TranscriptWorkerRuntimeProvider` 组合，在 minutes S 的实际连接 commit 返回后暂停12秒；新物理连接依身份→root 的顺序 FOR UPDATE NOWAIT 成功，原 owner/attempt1 保持且真实心跳推进 expires_at。释放后出现 PROVENANCE_UNAVAILABLE，原 task attempt1 最终 EXPIRED、attempt2 成功；`passWithoutRetry=false`。最终成功不能掩盖正常续租造成的第一次准备丢失。

首版有界完整重捕获的 `heartbeat-green01` 仍失败。专用资格 JVM 的 JFR JavaExceptionThrow（16MiB/maxage2min）记录 `heartbeat-diagnostic/b5c22579-ff1f-425d-9cb0-14611f89d4e3/provenance-stacks.json`：真实F发出绑定原lease引用的 PreparationRenewed，紧接prepareStored catch报PROVENANCE。额外自行增加的15秒admission已到，尚未到原acquisition的30秒边界。删除这个过早门槛，仍同一个ledger的原始30秒deadline/max3，不重置累计预算；独立publication原有15秒策略不变。

`heartbeat-green02` 继续失败；第二JFR `heartbeat-diagnostic/b21e6ed1-cb37-4668-8d2d-f16655ed674e/provenance-stacks.json` 显示第一次F正确触发新捕获，随后新一轮discovery→PublicationWriteGuard.covers遇到自身心跳，尚未进入S即被严格拒绝。新增initial-generation专用路径：在D前建立完整original guard；coverage失败时再读完整guard，要求root/publication/final/原manifest/controls与完整四表集合均相同，仅原RUNNING任务same owner/attempt的expiry严格向前才发signal丢弃整个本轮，不接受替换声明。普通covers/guarded及recorded/nulllease路径不改成重试。

## 实施边界

生产改动在 `PublicationGraph`、`PublicationWriteGuard`、`MinutesGraphDiscovery`、`MinutesGraphCapture`、`JdbcMinutesGenerationPreparation`。只在已领取的initial generation preparation处理精确自身续租；其他字段、其他任务/attempt/execution/operation/集合变化仍拒绝。捕获失败不返回事实，不修改旧图；每次完整重新获取，最多3次，共享原累计ledger和原始30秒deadline。完整四表比较位于deferred正文transfer之前，普通读取/命令/lookup保持严格。

`bqual-minutes-heartbeat-green03.log` 与 `heartbeat-preparation/46541240-2b5a-45b5-a143-221a1e9b7639`：相同12秒S-commit停顿、物理身份/root NOWAIT、same owner/attempt且expiry前进，原 task `01dd6bdb-0155-4ce1-8712-81e33ae5a8f8` 在attempt1 SUCCEEDED，只有1 external execution和1 candidate。此证据先于下节charge顺序修正，当前源码仍需受影响复核。14 focused tests及 `bqual-minutes-renewal-broad01.log` 543/0/0/0属于当时版本，不冒称最新最终回归。

## 独立审查发现的累计预算 P2

`bqual-minutes-preparation-renewal-source-review-20261029.md` 指出：已完成D的charge位于可能发signal的coverage之后，失败轮D费用会被跳过。实际 `bqual-coverage-budget-red.log`，目录 `coverage-budget/068f338f-c7ac-4464-97d4-f598729f0a9c`，真实工厂/队列实际领取任务后只调用一次公开prepareStored，另线程调用真实queue.renew触发一次coverage失败，最后只观察到一次D charge，零外部执行/候选。该RED的资格观察器通过real-method调用观测，没有伪造返回；失败记录保留。

生产顺序现为 original guard → D discover → **立即 charge(D,同ledger)** → withPending → coverage。pending/guard自身收费不替代这笔domain/source metadata/membership/owned copies。

`bqual-coverage-budget-green03.log`，目录 `coverage-budget/64908e5b-7d65-4a75-a0cc-6b1b78642981`，task `0b0673bc-2964-4f56-94cd-2508dd04793d`：

- 原始方法体完整执行；资格JVM只用ByteBuddy Advice观察charge入口/出口和Ledger.admitCapture入口，不替换方法体、返回、异常、SQL或授权。该依赖没有进入普通application/Worker制品。
- 两次D charge都完成，每次233行；两次使用同一ledger，分别真实增加UTF-8/scalar/UTF16、owned copies/rows/links，失败轮费用保留。
- 两轮实际保留copy bytes为2,939,007；包括JDBC瞬时buffer admission的真实峰值为4,028,766。
- 使用实际配置copy-byte cap=4,028,765：无discard的单轮真实prepare通过；含原coverage失败轮费用的prepare具体BUDGET_EXCEEDED，在外部执行/候选之前拒绝。
- 资格结束时该task无external_execution/candidate，所观察业务图/receipt/audit/outbox快照不变。所有lease变化来自实际queue.claim/renew，未用SQL更改；随后恢复普通Worker，由正常过期领取完成原任务（只读API settlement记录）。

这是真实MySQL/工厂/公开preparer/队列控制流加只观测的JVM instrumentation。它不等同于完整WorkerLoop调用计数或所有budget维度的重新资格；既有native行边界证明仍按原范围。`sameLedger`数值标识也应结合生产单ledger变量传递及观察器实际参数评估。

保留失败：coverage-green01只用retained usage计算限额，未正确标定transient峰值，抛BUDGET而未产完整PASS；green02在普通启动的文件liveness检查失败，随后检查原文件进程live，未判定根因；手动普通start恢复六服务，green03才完整通过。父编排现有finally同时恢复partial-start服务，恢复Worker后查回原owned task终态再结束。启动检查失败不当成预算修复失败或通过。

## 当前剩余验证

预算P2 closure独立审查进行中；修正后实际S/DISCOVERY/EXHAUST、recorded路径、原deadline/取消、最终Java回归及必要跨路径检查仍按最新日志收口。DISCOVERY observer01误命中后续事务查询，已仅允许第一次精确边界，旧轮未计通过。共存/饱和、全部适用锁集合/原子性/历史范围判断、B00差异和最终全分支审查仍开放。不能将本页单一绿色或predicate测试扩为正式B完成。


## 当前补证：coverage / 三轮 / 原始 deadline / cancellation

D acquisition charge 前移的 P2 已经由 W/bqual-coverage-budget-closure-20261029.md 独立关闭。DISCOVERY05 (`heartbeat-preparation/1954d1b6-c301-4db0-8b8d-274f3db6fd10`) 在首个实际 heartbeat 前进后及时释放，原 task626fca3c… attempt1成功/单execution+candidate。EXHAUST01 (`7bf2a37e-f1fa-4406-adb8-6fc41ce81e42`) 三次实际 S commit暂停/三次F私有signal后命中max3，首次attempt终止时0execution/candidate、无BUDGET；原task74360380…随后普通attempt2成功，最终各一。原历史大图的BUDGET和过长observer的max3失败保留，不改写为成功。

`coverage-budget/cde05937-8e2b-43f3-924e-c0199383ce0e` 单次 public prepareStored 中，first D实际续租导致coverage丢弃；第二fresh D在同一ledger原30s deadline以后首次charge返回具体BUDGET_EXCEEDED，实际总33,705ms，两个局部D暂停12,869/11,639ms均低于各自30s。只读Advice观察到两个charge使用唯一实际ledger引用和相同原deadline；原方法体、返回、异常、时钟和授权不被替换。11次生产queue周期续租使原lease有效。拒绝时业务快照不变、0execution/0candidate。parent首次恢复读API503中止，原FAIL记录保留；后续 bqual-coverage-deadline-settlement02.log 实际200/SUCCEEDED，0 transient reads，不将单个503原因写为已知。

`coverage-budget/aa415a31-4158-4deb-9454-dbeaa37842df` 单次public prepareStored：first D实际续租/丢弃后，第二D收到真实线程interrupt，返回具体CANCELLED，未重试为第三轮；唯一已收费ledger引用，0业务快照变化/0execution/0candidate。finally清除资格调用线程interrupt、恢复普通Worker，原任务实际API成功，parent完整PASS。

上述deadline/cancel已提交sole reviewer增量复核。最终源码适用Java回归和全B其他义务尚未因此完成。
