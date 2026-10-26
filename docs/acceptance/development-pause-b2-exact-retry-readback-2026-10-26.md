# B 阶段暂停与精准接续：Task11 精确重试回读，源码审查两项待修

暂停时间：2026-10-26T10:32:35.299564+08:00。按用户要求，已完成当前已调度源码/离线验证/独立审查，现已停止。没有启动新修复批次，也没有创建或执行回读运行窗口。只有用户下次明确“继续任务”才恢复。完整目标仍为Task11→12→13，完成B后止，不进C/D/E/F。

## 下一次从哪里开始（必须先读）

**先修复两项源码审查阻塞，再做窄范围离线验证与复审；不能直接运行readback。** 最终审查为SPEC/QUALITY CHANGES REQUIRED，C0/I2/M0。数值oracle原问题EXACT-RETRY-NUMERIC-I1已在源码/离线范围解决，实际业务后像验证仍未执行。

1. **EXACT-RETRY-READBACK-I1**：`Task11B2ExactRetryReadbackIT.java:93`误拦截真实`TransactionRunner.java:27–28`所需的精确`SET NAMES utf8mb4`与`SET time_zone = '+00:00'`，会令首次合法回执事务失败。只允许这两条连接初始化语句，保持任意SET、业务DML/DDL、callable/batch及错误会话许可拒绝。先以fake connection/statement复现，再验证精确初始化与会话hash/actor/CSRF绑定；无需真实SQL。
2. **EXACT-RETRY-READBACK-I2**：`tools/task11_window.py:176`没有调用第61行定义的`validate_exact_readback_release`。Java校验仍存在，但runner可提前创建/消耗窗口。接入准确readback分支，在环境/目录/进程副作用前校验；用隔离或AST测试证明缺失/过期/错误release提前失败，保留旧阶段行为。

这两项已由根协调者核对源码确认，**本次没有派修复**。下次保持Task11 cap5/5，记录最小依赖修正裁定，复用同一作者/审查员；原冻结包不可覆盖。当前作者`/root/b11_host_author`、独立审查`/root/b11_host_reviewer`均已完成并停止。

## 当前批次封存结果

- 六路径：3修改+3新增；当前/快照1094文件，15份离线证据均经根与独立审查核验。
- 数值及完整oracle测试：因果RED6项/3失败→GREEN6项/0失败/0错误/0跳过，含完整合成图与21个不变量破坏负例；不等于实际数据库通过。
- 最终离线test-compile退出0，460测试源码，512MiB/2CPU串行。未执行任何新SQL、服务、回读或grant。
- 源码checkpoint：`task-11-b2-exact-retry-readback-source-checkpoint-20261026.json`，SHA256 `2be86f4d6c95630adf78ae5ef588ede533697905342963c3d11de3b8e35bd463`。
- 源码报告：`task-11-b2-exact-retry-readback-source-report-20261026.md`，SHA256 `64991592c539ef47fabaa04416c93dd0752d065312dc4f8c329c1c5b9858ee78`。
- 审查：`task-11-b2-exact-retry-readback-source-independent-review-20261026.md`，SHA256 `3a4e12162eeea61dfbe875616e0c6ecc4f9fa9ca116ca5d03b792bdf3c1ca4fa`，根已全文读取。
- 根核验：`task-11-b2-exact-retry-readback-root-source-verification-20261026.json`。

唯一产品树 P=/home/tanren/ama/.worktrees/phase-b-completion，分支codex/phase-b-completion-20261023；主树/home/tanren/ama仅接续入口。两树HEAD均739193dff6dea6c28999260d4d332f99f1a58d12。本次没有commit/merge/push，不丢弃未提交工作。W=P/.superpowers/sdd/2026-10-23-ama-phase-b-remaining。

用户2026-10-26要求完成当前批次调度后停止，保存接续记录。已完成批次仅六路径数值断言/独立回读入口源码、离线检查及独立审查。停止后不发起实际回读或下一批修复；新审查问题作为接续首项。下次“继续任务”解除暂停，但不代表可以重跑旧窗口。

## 不得重复的已完成工作

Tasks1–10及Task11B1局部采用、B2专属schema/16迁移/身份/PKI/原生/档案/真实B03流水线、SUBMIT1本地NEVER_MARKED闭合与ACK/freshJVM回执、RESUBMIT2真实远端拒绝均保留既有采用结论。不得重建会议、重新导入/模型生成/final、provision/bootstrap/provider初始化或重复远端拒绝。

本轮Host两跳修正及安全诊断完成，10纯测试通过。新DBpreflight和Host矩阵实际通过并独立采用：26场景、7构造负例，GET1→25，POST增量0，17表及provider前后不变；新front4141477已exit0/sinkJoined。精确记录为W/task-11-b6-host-runtime-root-adoption-20261026.json SHAba5c5b4d34c6380c258697aa01882285bf972e1a2f78f2f72d4b2afd83f7f94f。旧三次失败窗口依然失败且不可重跑。

精确重试原始源码401455及修正R1ad4cdad9已封存；中断清理缺陷EXACT-RETRY-I1的6项模拟进程回归RED6/5失败→GREEN6/0，源码复审9a4052ad通过。R1实际窗口b2-exact-retry-01已消耗且FAIL1，不能再次运行。

## 最重要的运行事实与未知项

b2-exact-retry-01已完成22前网络负例、12实际物理事务回滚、2并发202；共36 servlet，1次授权恢复，2次只读SQLite观察。12回滚后17表前像均相同。两个202指向同一task3588aaba-ecfa-4889-8954-46c1454447d4、execution65fa6b21-22da-4a2e-a886-341b16978106；submission3/execution2，op896456c1-cc73-4a8c-803d-8b5bb058d4c7，原publication/final不变。两次实际commit返回、相反replayed标志已经观察。尚未claim/send。

失败发生在提交后verifyAccepted第108行：expected Java6L与JDBC数字生成的Jackson节点比较不等，打印JSON却相同。离线探针复现LongNode/BigIntegerNode及IntNode差异，Connector/J9.7.0将BIGINT UNSIGNED映射BigInteger；现场具体Java类未采样。保存JSON再读入的整数类型也会影响旧行比较。当前源码批次修正完整测试oracle；不是已证明的业务重试缺陷。

最后relayGET39/destinationPOST17/publicationPOST1，failures0。READ/PUBLISH/version由1/0/2变为1/1/3；该授权已经恢复，不能再次grant。provider最后完整观察为授权后、接受前，documents0；最终第三次观察未执行。全部接受后17表/manifest/tasklease/noattempt oracle、7独立回执请求和最终provider观察尚缺，不能由202推定已通过。

并发诊断executedWrites/hit/rollbackObserved继承前一PRE_COMMIT累计内容，不可解释为重复创建或并发回滚。以独立commit事件和响应ID为准。nullable owned-provider cleanup false不代表原provider死亡；helperphase不代表主测试进度。

cleanup记录四个临时会话均删除、app关闭返回、native1156860退出143、无cleanupfailure；原provider2655441/relay2655444分别仍活着。暂停未重新探测，不将历史存活当作恢复时当前存活。

## 下次最小接续

先完成上方明确OPEN的I1/I2源码修正及复审，保持Task11cap5/5；本冻结包尚未通过，禁止直接运行。只有修正源码独立通过后，才核验新的冻结source/current hashes和原资源身份，准备新鲜、单独、限时一次readback release。现只有惰性配置ACK48ebb70d5249915a093b622e06d902332782e8790f8b2a8d49c646c719439a51，不等于运行许可。

新tuple: b2-exact-retry-readback-01 / it / Task11B2ExactRetryReadbackIT / task11-b2-exact-isolated。未来release文件task-11-b2-exact-retry-readback-runtime-release-20261026.json，状态EXACT_RETRY_READBACK_RUNTIME_RELEASED_ONCE，必须绑定最终源码/ACK、maxInvocations1、有效时间，receiptOnlyCallsAuthorized7、oneGrantAuthorized false。暂停时不得创建release或目标窗口。

先读取、立即保存完整17表当前后像和关键数字类，完整strict integral oracle通过后才启动一个receipt-only context/四个精确临时会话。使用原胜出key task11-b2-exact-retry-winner-01，仅3owner数字/token/equaldual202回放、1changedbody409、member/outsider/admin各403，共7。业务写入0，受限会话insert/delete单列。最终17表及relay不变，剩余1次mode=ro SQLite观察完成原2+1总额；任一失败封存，不重复消耗。

不得再次grant/init/revoke/native启动、完整publication装配、rollbackmatrix/concurrency/acceptance、providerGET/destinationPOST/publicationPOST、claim/SEND；不得继承运行原JUnit用例。成功后仍须原失败+新窗口联合事实/独立证据审查及根采用，才可进入B3。原FAIL记录不改写为PASS。

## 保留资源与义务

B2schema ama_t11b2_d9e2fdcc611e_behavior，owner task11b2-d9e2fdcc611e4e21bb360e539ca46258，MySQL CID11009a6b34fef0416e651100fbde05a07067dbcbd973f1ba731c08b2281140ad /127.0.0.1:46151/768MiB1CPU256pids。provider2655441/startTicks21557905/45709；relay2655444/startTicks21557924/45519；storebf7f17d6-4ad9-402e-9bd8-7e03182afa30；原config/PKI/keys/root均保留。新native1156860和旧2655411已记录停，原生root及45833不可自动重建。私有路径见原retainedfacts，不输出凭据。

meeting36db643e-5fbd-4f9a-9a85-a7e6b7d76e38/r13；final1431080e-9b05-4913-9355-db3fa5b0c39a；publication142a236c-bcd6-4608-8e93-2dd5446c5fa9最新响应观察READY/rev6但完整后像待验证。submission1 task0b9fa9a6-8883-40a8-947a-471558e0e63a FAILED0/NEVER_MARKED；submission2f0f17320-130e-46b9-908c-c143a02e10f8 FAILED1；execution10ef15c54-cc24-49f5-b5f8-6b01933118db为真实DEFINITIVE_REJECTION。所有旧行和历史不可补写。

原held1a8dc6bb-2076-44d2-8ff0-aa1b5f630e27/execution427e9751-2bca-483b-8d91-cb1711ae127c；B1held246e645e-7894-490d-8233-e692b15e966e；UNKNOWN07327d2f-3b2f-4bc1-ba6d-f9f03246d08a、0469c111-f07f-4428-acb8-3447ec0894ad、fd323464-54c7-4d80-ae93-ac0cd7925663，继续保护，不能由新成功覆盖。

## 后续不得遗漏

B3：新task-11-b3-prepared-next-stage-preparation-20261026.md(ac4e0345)只读draft，等待本readback采用。真实PREPARED/租约/回滚/ACK/新JVM/耗尽/marker双顺序、ReconciliationLoop游标/节流/restart与Task8公平性待补。初始无execution本地闭合已完成不重做；真实legacy terminalUNDECIDED来源仍缺，禁止SQL造历史/禁hook后commit，正向maintenance资格保持未证实。

B4：task-11-b4-discovery-document-preflight-20261026.md。两个发现GET、fresh appREAD+providerDOCUMENT_READ双ACL、同源安全文档与预算/取消/typederror需实现；必须先有合法真实SUCCESS+ACTIVE mapping，当前PREPARED不是文档fixture。

B5：task-11-b5-guards-combined-runtime-preflight-20261026.md。完整历史guard覆盖全部普通变更、上传/registry/RAW/current与retainedgeneration后才能启用combinedinitial/generation/freshintent；T10M1三处eligibility待真装配；不可先开入口。

B6余项：task-11-b6-transport-restart-gap-preflight-20261026.md及ruling；HTTPS/TLS、独立providerCAS/重复/旧延迟POST、新JVM崩溃切点、native+S3/browser。现HTTP不等于TLS，Unicode/name/receivedbytes/interruptcancel/确定性admission的覆盖限定均保留。

Task12：task-12-current-activation-gap-map-20261026.md；等完整Task11，后做B03受保护读取/预算/索引、9份rootOpenAPI与6生成unknownunion、业务UI、四身份三viewport及真实HTTPSLOCAL+S3browser。

Task13：task-13-pending-issue-register-20261024.md。Genericunpreparedattemptfinalization仍OPEN；新增T11-KB-IO-ERROR-MAPPING为ProviderFailure/IOException未映射且未实际测HTTP；旧T11-FINAL-ERROR-MAPPING已有B1servlet409不要重做。旧UNKNOWN/legacymaintenance/T10M1/F5/公平性/完整性/公司能力等逐项处理。最终相关总gate和固定下界34cb226aefe99c67d3681af68d748b0aeea4e073全分支独立审查、交付后止于B，不进CDEF。

## 接续入口与保存位置

读取顺序：本记录 → W/current-state.json → W/RESUME_LATEST.md → 最终源码审查全文 → 当前源码报告/checkpoint → 数值诊断及原失败事实。主树和产品树DEVELOPMENT_HANDOFF.md顶部均指向本记录，下方旧暂停/ACTIVE文字仅历史。旧Host暂停记录仅用于历史，不再从Host缺陷开始。

W/pause-20261026-b2-exact-retry-readback/保存暂停时记录副本、两树Git状态、产品树tracked diff、源检查点与审查/诊断/关键事实副本；未跟踪源码由1094文件冻结快照保留，不能仅靠tracked diff恢复。记录哈希索引为W/pause-20261026-b2-exact-retry-readback-record-index.json。没有commit/merge/push，没有清理或重置保留资源。
