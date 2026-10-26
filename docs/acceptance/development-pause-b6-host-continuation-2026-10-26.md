# B 阶段暂停与精准接续：Task11 / B6 Host 转发缺口

暂停时间：2026-10-26T05:52:36.163919+08:00。用户明确要求“完成当前批次调度之后就停下”，已完成已派出的失败证据封存与独立诊断；不再启动修复、测试、资源探测或新运行窗口。以后只有用户明确“继续任务”才恢复。完整目标仍为 Task11→Task12→Task13，完成 Phase B 后停止，不进入 C/D/E/F。

## 唯一工作树与读取顺序

产品树 P：`/home/tanren/ama/.worktrees/phase-b-completion`；分支 `codex/phase-b-completion-20261023`；暂停时 HEAD `739193dff6dea6c28999260d4d332f99f1a58d12`。主树 `/home/tanren/ama` 为 main，同一 HEAD，只更新接续发现入口。本轮没有 commit、merge、push；未提交源码保留。

W：P 下 `.superpowers/sdd/2026-10-23-ama-phase-b-remaining`。按顺序读取：本文件 → W/`current-state.json` → W/`RESUME_LATEST.md` → 下列最新诊断、报告和源码检查点。旧暂停文档、旧 ACTIVE、旧 next_steps 和历史 PID 不覆盖本记录。

唯一产品作者 `/root/b11_continuation_author`，独立审查 `/root/b11_fix02_independent_review`；本批次两者均已完成并停止。根协调者负责记录、资源放行、证据核验；作者不写根记录，不派子代理。恢复时优先复用这些代理；不可恢复时带精确 brief 接替，不重做已完成任务。重型构建/测试串行，JVM512MiB/2CPU。

## 准确接续点：未修复 B6-HOST-I1 / I2

最新独立诊断：W/`task-11-b2-rejected-transport-manifest-diagnosis-20261026.md`，SHA256 `76bc92808562862f4bd07499ed2529ec63405adb3d4d37fd140c99e709825733`，根已全文读取。

- **B6-HOST-I1（新增测试转发器缺陷）**：客户端 Host 为新前置转发器47507；`tools/task11_rejected_transport.py` 原样转给原转发器45519；原转发器只替换45519→提供方45709，故47507继续抵达严格校验Host的提供方。源码确定这条组合不兼容；HTTP400只是源码预测，运行时没有记录上游状态，不可写成已观察400。
- **B6-HOST-I2（诊断不足）**：前置转发器只记录 ValueError 类，没有安全阶段、固定原因码或数值HTTP状态，无法认定这次具体在哪个上游分支失败。
- 建议的最小改动尚未实施：仅在新增前置转发器中验证唯一、大小写不敏感且等于当前监听地址的Host，再只将该字段改为下一跳45519；保留认证、委托、请求行/路径/空body及其他头字节。提供方和原转发器不改、不重启。加安全枚举阶段/数值状态，禁止原始头、凭据、响应体或任意异常文本日志。

**下次首个动作不是运行旧测试命令。** 先核验冻结源码与以上诊断，记录明确的最小依赖修正裁定，再给同作者源码/离线测试权限。Task11/B1 修复 cap 已5/5，不重置、不默认为新五轮；按已采用的 cap breaker 对阻塞后续工作的最小修正明确裁定。离线回归覆盖正确Host两跳转换、其他字节不变、缺失/重复/错误Host、GET/路径/body拒绝；无需接触原服务。原脚本import即启动资源，提取纯函数/主入口时必须保持生命周期边界并独立复审。

源码修正、离线测试和审查完成后，任何新GET之前都需要**单独、精确放行的只读前提核验**：核原进程PID/startTicks/配置/store身份，读取原转发器当前累计计数，比较保存的业务/provider前像，确认三个旧前置进程均已退出。最后一次已消耗1次转发尝试，原转发器事后GET累计数未采样；不得假定仍为0或已为1，不重置计数。新窗口必须采用观察到的累计基线和单独增量预算，并绑定新标签/入口/事实，不复用已消耗窗口。必要的重新打开保留服务必须重新明确放行，不因旧PID死亡自动初始化/换store/换密钥。

## 最新实际结果与三个不可重跑窗口

|窗口|实际结果|不可扩大解释|
|---|---|---|
|b2-rejected-transport-01|1 ERROR，INVALID_ID，GET0|旧探针归档pin字面量无效，未完成构造；已修复源码，但失败保留。|
|b2-rejected-transport-continuation-01|1 FAIL，GET0|服务调用失败类为MinutesFailure，计数断言遮住原code；已确认并修正产品清单兼容缺口与诊断，但原运行具体throw点未知。|
|b2-rejected-transport-manifest-01|1 FAIL / 0 ERROR，NORMAL实际GET1、front forward1、POST0、发送字节0|已到真实回查网络；客户端IOException INVALID_PROVIDER_RECEIPT被完整保留。前置ValueError、无upstream200；NORMAL未验证，其余25项及7构造负例未到达。|

最后窗口运行前，两份保存的17表前像比较和提供方精确基线均通过。**最终17表afterimage和第二次提供方观察未执行，不能声称完整前后不变。** 原转发器事后GET数 UNSAMPLED。当前没有可通过的完整26项B6回查矩阵。

最后前置进程2662310/startTicks21980854/port47507，sink45361，已记录exit0/sinkJoined；更早前置2659369、2660337也已记录停止。没有新建后继任务，没有恢复PUBLISH、claim、SEND、retry、recovery/apply或发布POST。

## 已完成且不得重复

1. Tasks1–10已按各自限定采用；Task11 B1原四项审查问题已经5/5轮局部闭合并采用，不能从旧B1修复标题重来。
2. B2专属schema/用户/16正常迁移与0项重跑、PKI/身份/原生目录/档案/模型安装已完成，不重复provision/bootstrap/helper/初始化/授权。
3. 同一真实B03成员花名册→上传/导入→WORKING/确认→生成/候选采用/5人工review/final流水线已完成，generation及reservation已settled，不重建会议、不重新生成或final。
4. SUBMIT1原任务的实际本地NEVER_MARKED闭合、自然事务rollback、丢ACK/原回执重放/fresh JVM回执验证已局部通过并独立采用，原失败窗口保留。
5. 同final RESUBMIT2、唯一真正publicationPOST造成的签名永久拒绝execution1已实际通过并独立采用；task_attempt确已结束。不能再次执行拒绝流程。
6. 产品 `PublicationRecordedLookup` 的重复旧白名单错误已修复：真实路径使用既有严格PublicationManifest解析器，合法acceptanceBinding被接受，未知/坏字段仍拒绝，旧格式兼容。新测试实际RED4/1失败→GREEN4PASS；相关8+21+7=**40项最终合格纯测试**。Manifest01原7项虽原XML通过，但采集时间窗不合格，规范结果0项；原记录保留，唯一获准Manifest02为7PASS，不累计重复结果。
7. 失败诊断已保留安全typed enum、NORMAL原始cause和次级计数错误；这项源码与上述产品修正独立SPEC/QUALITY通过，未新增阻塞。真实运行随后到达GET，不能将其当完整回查通过。

冻结源码：W/`task-11-b2-rejected-transport-manifest-source-checkpoint-20261026.json` SHA `124a4e4d81c7c13b9b7f1ef3ec1a2a3cf132ae263343f4d0e53c2189e6056a0b`，1085当前/快照文件；该快照为接续基线，不能覆盖还原现场。源码复审 `task-11-b2-rejected-transport-manifest-source-independent-review-20261026.md` SHA `5e01553fe8d3cfcb4c3d977f956684dfa847b9fcd64e192a635cf1e9ac693949`。

## 最新证据索引

以下均在W，根已核验164实际文件、1018规范源码hash、1085宽范围源码当前及快照，全文读取实际报告和XMLcause。不同范围不可混称完整捕获。

|文件|SHA256|
|---|---|
|task-11-b2-rejected-transport-manifest-window-index-20261026.json|0f35902e099bc65e4724db676ebb70e71fa83eecfefecf51cef40f4278cedd8e|
|task-11-b2-rejected-transport-manifest-attained-facts-20261026.json|9588a52656f0a6a4df6a28a8c38fbcf2c67ba149e2715e3af267bf3f0afb416c|
|task-11-b2-rejected-transport-manifest-report-20261026.md|eb025a702d38c01ff4b632be17a7e5e7189e9a44ffe7884c4b2cc25082f89b87|
|task-11-b2-rejected-transport-manifest-result-freeze-20261026.json|2316268c120c80526f43be49932c59764b09a32531b0c1369c5287183d1d01ac|
|task-11-b2-rejected-transport-manifest-root-verification-20261026.json|3e61016a1f94840289bb325cd0a67d4747c74dffd65f555b5587e56ca1d226ad|
|task-11-b2-rejected-transport-manifest-pure-evidence-index-20261026.json|2e4d86af0b8f2cce2179c6c28fbb8e8153b48c54ba3ef8a03b9e0cb365a636c5|
|task-11-b2-rejected-transport-manifest-root-source-readback-20261026.json|1e3940b34a63945d5e026c7568591e4fe7f373606e328ec5276e5f9f504c75df|

## 保留的业务与资源

同meeting `36db643e-5fbd-4f9a-9a85-a7e6b7d76e38`，r13；final `1431080e-9b05-4913-9355-db3fa5b0c39a`；publication `142a236c-bcd6-4608-8e93-2dd5446c5fa9`，最后观察FAILED/revision5。原submission1 task `0b9fa9a6-8883-40a8-947a-471558e0e63a` 为FAILED0/NEVER_MARKED；submission2 task `f0f17320-130e-46b9-908c-c143a02e10f8` FAILED1；operation `896456c1-cc73-4a8c-803d-8b5bb058d4c7` FAILED/currentexecution1/reconcileepoch1；execution `0ef15c54-cc24-49f5-b5f8-6b01933118db` markedFAILED/DEFINITIVE_REJECTION。这些是最后保存的观察，暂停没有重新查询数据库。

B2 owner `task11b2-d9e2fdcc611e4e21bb360e539ca46258`，schema `ama_t11b2_d9e2fdcc611e_behavior`。MySQL CID `11009a6b34fef0416e651100fbde05a07067dbcbd973f1ba731c08b2281140ad`，loopback46151，768MiB/1CPU/pids256；不得重建/重置。B2 KB根 `.tools/`owner下`knowledge-bb9bb5c9-7277-4911-8d55-7038140e62b1`，storeInstanceId `bf7f17d6-4ad9-402e-9bd8-7e03182afa30`。

最后窗口cleanup记录提供方PID2655441/startTicks21557905/45709、原转发器PID2655444/startTicks21557924/45519仍活着；暂停未重新探测或停止它们。provider config `provider-777c6ed3-4b93-4403-b6ec-cdae4b8f2a7d.json`；原relay config/state suffix `5df8b970-d7cd-4f86-b48c-e1a97da76dfe`。原生进程2655411已停，保留原root/config/managedID/port45833。所有原存储、keys、logs保留，不输出secret或secret hash。PUBLISH仍最后观察为0，READ1/grantversion2，documents0/slotgeneration1；不恢复授权。

旧主/A/fix01/fix02/B1保留场景与三个UNKNOWN不得触碰或由新成功覆盖。主held task `1a8dc6bb-2076-44d2-8ff0-aa1b5f630e27`/execution `427e9751-2bca-483b-8d91-cb1711ae127c`；B1已接受未claim task `246e645e-7894-490d-8233-e692b15e966e`。三个原UNKNOWN：`07327d2f-3b2f-4bc1-ba6d-f9f03246d08a`、`0469c111-f07f-4428-acb8-3447ec0894ad`、`fd323464-54c7-4d80-ae93-ac0cd7925663`。更多精确限制继续读取state、原fact文档及Task13登记，不因本记录未逐项复述而解除。

## 不遗漏的后续顺序与资格边界

- **先完成B6只读回查**：Host修正/安全诊断/离线测试/独立源码审查 → 单独当前状态核验 → 新精确运行窗口 → 真实结果封存/根核验/独立证据审查。26项矩阵、24次增量GET的预算仅为旧提案，必须根据实际新前提重新绑定；不得直接使用旧release。
- **再完成B2 exact retry**：恢复PUBLISH一次需单独放行，真实零副作用retry HTTP/ACL/rollback/replay/并发证明；同operation产生submission3/PREPAREDexecution2后保持未claim。W/`task-11-b2-exact-retry-next-stage-preparation-20261026.md` SHA `fb2d4d3618efc2e1a2569477800ab8ea4464e95c487260ef95713778882dfa6b` 只是未激活准备，不含执行权限。
- **B3**：`task-11-b3-local-maintenance-preflight-20261026.md`、`task-11-b3-sequence-and-maintenance-root-ruling-20261026.md`；真实本地无marker序列已完成部分，不能重做。合法legacy terminalUNDECIDED正向来源仍缺，不能混旧queue/新policy或SQL造状态；64扫描/16尝试/5秒游标与Task8 >64公平性区别保留，自动Worker quarantine与直接queue证据分开。
- **B4**：`task-11-b4-discovery-document-preflight-20261026.md`；两个发现GET、fresh app READ＋provider DOCUMENT_READ双ACL、同源安全文档relay及预算/取消待实现。需后续真实SUCCESS＋ACTIVE mapping，当前失败或将来PREPARED不是文档fixture。
- **B5**：`task-11-b5-guards-combined-runtime-preflight-20261026.md`；完整publication历史guard须覆盖全部普通变更/上传/registry/RAW/current与retained generation，再启用combined initial/generation/freshintent；Task10 M1三个静态eligibility仍需真实装配。不得先放开入口再补guard。
- **B6其余**：`task-11-b6-transport-restart-gap-preflight-20261026.md`及对应root-ruling；KB HTTPS/TLS、独立provider协议CAS/重复/延迟旧POST、新JVM崩溃切点和真实native+S3/browser仍未闭合。当前loopbackHTTP不替代TLS；不完整Unicode/name负例不独立证明该解析边界；clientreceivedbytes不可观察；控制探针不等于完整服务验证；interrupt包含cancel，admission为确定性顺序非压力竞态。
- **Task12**：`task-12-current-activation-gap-map-20261026.md`（4b29b1f2…）仅准备。先等完整Task11/combined/M1/B4，后做受保护B03读取/预算/索引、9份rootOpenAPI与6份生成unknownunion、业务UI、四独立身份/3viewport/真实HTTPS LOCAL+S3浏览器，不重做静态入口盘点。
- **Task13**：`task-13-pending-issue-register-20261024.md`逐项裁定。Generic unprepared attempt finalization仍OPEN；B2 prepared task_attempt结束不能关闭它。旧T11-FINAL-ERROR-MAPPING先核已有B1 servlet409证据，避免重复工作。原UNKNOWN、legacymaintenance、T10M1、旧F5/公平性/完整性/公司能力等资格边界保留。完成相关总gate与固定下界`34cb226aefe99c67d3681af68d748b0aeea4e073`全分支独立审查、交付记录后停止，不进CDEF。

暂停记录原字节、Git状态及tracked diff位于W/`pause-20261026-b6-host-continuation/`；最终记录索引为W/`pause-20261026-b6-host-record-index.json`。这是本地保存，不是已提交、推送或Task11/PhaseB验收。用户下次“继续任务”撤销本次暂停后，严格从本记录首个未完成Host修正节点接续。
