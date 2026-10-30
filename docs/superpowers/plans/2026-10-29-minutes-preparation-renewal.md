# R2 生成准备跨正常心跳的完整重捕获

B-R05/10/12 实际发现，先于继续共存。`bqual-minutes-heartbeat01.log` 及 `.tools/local-app/heartbeat-preparation/45aafcc9-0e47-41bc-a5a0-1f6c783711ae`：真实同 production provider runtime 在 minutes S commit 后暂停12秒；新物理连接按身份→root NOWAIT取得锁，证明暂停处不持有这些物理锁。真实心跳保留owner/attempt1只推进expiry；释放后准备Failure/PROVENANCE_UNAVAILABLE，原任务最后attempt1 EXPIRED、attempt2 SUCCEEDED。`passWithoutRetry=false`，不以最终成功隐藏正常续租导致的一次准备丢弃。

原因边界：`JdbcMinutesGenerationPreparation.prepareStored/prepareExisting` 保存S快照，在F重捕获前 `PublicationWriteGuard.tasks` 及 `MinutesGraphDiscovery.lockAndCompare`严格比较完整 task metadata（含expires_at），正常自身心跳也使其不同。不能全局忽略expires、替换某个字段使旧图相等、重发外部请求或改变marker；命令/读取/lookup仍严格。

修正设计：

1. 单个 genuine stored-initial TaskLease 路径内部允许有界的完整新获取，最多3次，原ledger累计，仅在原 ledger 的原始30秒 deadline 内接纳下一次；每次重新 discovery/identity/root/source/图/归属/容量/guard/绑定，失败的capture不返回事实。prepareRecorded/lookup/nulllease、命令和普通读取不进入该分支。
2. 复用已独立审查的 `PublicationGraph.onlyOwnExpiryChanged` full4table union比较。仅exact task ID、RUNNING、same owner/attempt、expires严格向前且其余所有字段/其他task/attempt/external_operation/execution及集合完全相同，发私有lease引用绑定的重捕获signal；全不匹配仍PROVENANCE_UNAVAILABLE。
3. PublicationWriteGuard新增包内generation-preparation tasks路径，在原scope/holder/阶段检查和原锁序下调用完整union检查；不放松普通tasks/claim。
4. MinutesGraphCapture只在包内generation-preparation调用新选项：按现有顺序读取并锁完整四表；延后四表比较至四表均完成、所有deferred正文transfer之前，统一判定equal/ownrenewalsignal/strict拒绝。非四表及其他路径保持原严格比较；不接受修改后的旧graph。MinutesGraphDiscovery仅把现有lock+read提取为before/after比较记录，严格wrapper保留原语义。
5. 真正stored preparation loop catch该私有signal后完整重来；不catch一般Failure，不在测试包装重试。max/ledger/原始30秒deadline/取消保持有界。

验证：现有真实12秒Scommit/心跳RED→GREEN需原task attempt1/1execution成功；补焦点严格owner/attempt/keyset/其他字段/其他table/无变化/向后expiry负例、recorded/nulllease与次数/累计预算边界；必要实际discovery→S及S→F阶段和耗尽场景。运行适用Java回归，再同一reviewer独立审查。完成前正式B仍INCOMPLETE；随后回到任务池共存/饱和、全锁集合、B00/历史范围判断与全分支审查。

首版 fix 的真实 green01 仍失败，未计通过。专用新qual JVM的JFR（16MiB/2min，仅JavaExceptionThrow）确证只出现一次 issuer-bound PreparationRenewed，从实际F的PublicationWriteGuard.tasksForGenerationPreparation发出，随即在prepareStored catch转为PROVENANCE（heartbeat-diagnostic/b5c22579-ff1f-425d-9cb0-14611f89d4e3/provenance-stacks.json）。此时并非foreign lease/max3，额外15秒admission已到。该额外门槛原为本修复方案自行借用，非原30秒acquisition契约；现删除这个过早门槛，继续严格使用同一个 ledger 的原始30秒deadline和预算/max3。不会重新计时、延长原deadline或绕过累计预算；Publication专属已采纳15秒策略不改。需真正同一12秒S停顿首次attempt成功后才能闭环。

第二次JFR `heartbeat-diagnostic/b21e6ed1-cb37-4668-8d2d-f16655ed674e/provenance-stacks.json` 证明第一次F已正确发signal并重捕获，但新一轮 discovery 后 PublicationWriteGuard.covers 在建立guard前后遇到正常heartbeat而报PROVENANCE。该轮尚未进入S；不能把新一轮guard中新expiry替换进旧discovery。新增包内initial-generation路径：先取得完整guard声明，再作discovery；coverage失败时在同累计ledger内重新读取完整guard，要求root/publication/final/原manifest/controls和全四表集合均严格相同，仅本任务expiry前进才发signal丢弃整轮。普通guarded/covers保持原严格实现；若完整guards相同仍coverage失败就原样拒绝，绝不接受replacement。错误仍先经既有fresh身份/root授权再向外返回。此路径也必须实际验证，最新green尚未形成，不以源码改动当通过。
