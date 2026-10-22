# B06 运行时静态预检（未激活）

日期：2026-10-23。检查基线：main `0676a21311f2b2193f73a5e096f4a5605ee9b41f`。完整读取封存任务书 `artifacts/checkpoints/2026-10-22-development-pause/task-B-06-runtime-prepared-brief.md`；本次仅静态读取并新增本文件，没有执行测试、连接数据库、调用 Docker、启动服务、导入同伴代码或修改源码。

**此文件只是准备。B07 F1/F2/F3 修复经独立复审采用后，由协调者明确激活新的隔离 B06 任务，才能实施。** B04 已归并并有原独立通过证据；旧手册的 pending 标题和 S1 手册的 UX pending 属历史快照，不覆盖当前接续入口、独立审查或已确认 UX 补充。

## 1. 实际接缝与位置

以下路径均相对仓库根，行号针对上述基线。

|接缝|实际签名与位置|B06 含义|
|---|---|---|
|队列领域策略|`backend/core/src/main/java/ama/task/TaskDomainPolicy.java:9`：`boolean lockRoot(TaskSpec, boolean skipLocked)`，另有 validateInput/validateOutput/validateExternalRequest/validateExternalObservation|唯一锁扩展发生在 subject 之后，尚无 identity 前置接缝；未知任务无 fallback。|
|队列|`backend/adapters/src/main/java/ama/task/JdbcTaskQueue.java:25` work；:38 lockDomain；:54 claim；:81 validLease；:89 `completeWithEffects(TaskLease,TaskResult,Runnable)`|lockDomain 当前顺序 subject→policy root；enqueue 可加入外部事务，claim 明确拒绝 ambient；renew/complete 都经 validLease。effects 必须只写已预锁领域行，整体回滚。|
|会议授权|`backend/core/src/main/java/ama/access/MeetingAccess.java:9`：`Locked lock(Actor,String)`、`require(Locked,Action)`；实现 `backend/adapters/src/main/java/ama/meeting/JdbcMeetingAccess.java:41`|真实顺序 identity→subject→meeting；:42 的分离 identity 能力仅包内，:44 禁止同事务 root 后重新 identity 预锁；:89 绑定同 DataSource 实际事务 holder。公开 lock 不接收 skipLocked。|
|文件登记|`backend/core/src/main/java/ama/storage/FileRegistry.java:44`：lock(actor,meeting,action)、access(held)、prelock(held,Reference,fileId)、resolve(fileId)、bindAll(List<Binding>)、download(actor,fileId,range)|API 事务最先 registry.lock，一次复用 registry.access，不另调用 MeetingAccess.lock。所有领域预锁后一次 bindAll；resolve 只是事务内元数据，不是读取授权。|
|真实引用策略|`backend/core/src/main/java/ama/storage/FileReferencePolicy.java:9` prelock / :19 validate；`backend/adapters/src/main/java/ama/storage/JdbcFileRegistry.java:127`、:158、:238|prelock 获取完整领域能力，validate 在 file 锁后不得发现新领域锁。download 自有授权事务；文件 I/O 位于 DB 事务外。生产无现成 INPUT_BATCH_FILE bean；用途默认 unavailable（`backend/api/src/main/java/ama/api/FileConfiguration.java:26`）。|
|Worker|`backend/worker/src/main/java/ama/worker/WorkerMain.java:8`；WorkerRuntimeProvider.create()；`WorkerLoop.java:15` Handler.handle(TaskLease)→TaskResult、:40 显式 capacities/handlers、:85 execute|WorkerMain 严格恰好一个 ServiceLoader provider；仓库仅 IT 资源注册 TaskWorkerProvider。API 不启动 Worker。默认 execute 最后调用 queue.complete，未传领域 effects。|
|原始配置校验|`backend/core/src/main/java/ama/config/ConfigBindings.java:168` computeProcessing(byte[],facts,List<RawArchive>,ArchiveReadBudget,cancelled)，:169 verifyProcessing；`backend/core/src/main/java/ama/transcript/ProcessingFingerprint.java:10` 同样封装|纯验证器验证调用方原始 bytes/closed facts；不提供档案持久存储、权威索引、部署恢复或 DB 完整读取。draft 不要求自身 TaskSpec/输出行；stored 需真实 input/task/producer 全链。|
|解析与确认|`backend/core/src/main/java/ama/transcript/TranscriptDocumentParser.java:16` parse(InputStream,UUID,SHA,ParseBudget,cancelled)；TextTranscriptParser.PROFILE_VERSION=`ama.strict-utf8-lines.v1`；`backend/core/src/main/java/ama/minutes/contract/TranscriptConfirmationHash.java:45` hash(ConfirmationFacts,Artifact,GraphBudget,cancelled)|TXT 解析器可复用，严格 EOF/SHA/有限预算，调用方负责阻塞超时与关流。确认必须配置真实 raw archive closure；无参 helper 故意不可用。待 B07 修复后重新核对任何 API 变化。|

共享顺序还受 `backend/adapters/src/main/java/ama/task/TaskTransactionContext.java:19` 与 B05 fix1 手册限制：同 DataSource、唯一已有 outbox 同步，flush 必须最后，不能新增隐藏同步。外部接缝同样使用 lockDomain：`JdbcExternalOperations.java:30/59/76/82/135`（begin/claim-reconcile/renew/observe/retry）；改共享接口时不能只覆盖普通 claim。observe 按 `docs/acceptance/b/task-B-05-fix1-integration.md:7` 必须独立事务。

## 2. 必须先明确的五个缺口

1. **锁序尚未实际复现。** 在 policy.lockRoot 内直接 MeetingAccess.lock 会先持有 subject 再拿 identity；若 API 已 registry.lock 后 enqueue，又会触发 root 后重复 identity 拒绝。claim 外包 REQUIRED 则被独立事务检查拒绝。TaskSpec 没有 actor 字段，须定义从真实不可变输入/会议所有权发现执行身份并在锁下复核的协议；不能信任旧 Actor.status 或解锁快照。只加 pre-hook 仍需说明 skipLocked、完整身份集、同事务能力复用和多次 lockDomain 的一致行为。
2. **首次导入引用尚未实现。** `FileRegistryUploadIT.java:416` ControlledBatchReference 要求已存在 current transcript_revision；:484 的测试构造还写入 `{}` manifest。它只验证 B04，不可复制成首次导入业务。真实策略须支持合法新批次/已固定输入/任务阶段，再明确当前及历史保存稿/确认的读取资格；精确检查原 upload UUID、原 purpose、完整 batch/file 顺序/哈希/大小、当前或历史归属，不能靠 hash 或假 current 指针授予权限。
3. **档案存储和批次 fingerprint 尚未实现。** S1 的 RawArchive/ArchiveBytes/ArchiveReadState 是内存载体与有限复制工具，没有持久 (artifactType,exact version)→SHA index 或发布/恢复协议。需先写技术说明：受控只读配置来源、不可变映射/原始 bytes/完整依赖闭包、发布与保留/重启恢复/default 切换、边界预算和失败行为。`docs/superpowers/specs/2026-10-22-ama-config-wire-contract.md:17` 明确保留 batch 指纹待 B06 定义，不能等同 manifest SHA 或 processing fingerprint，也不能重新解释旧 profile。
4. **Worker 完成原子性需设计。** Handler 返回 TaskResult 后 WorkerLoop 仅 queue.complete；领域结果不能先在 handler 独立提交，再期待队列补检查。须给出一个清晰的预计算结果→短事务 completeWithEffects 协议或窄生命周期扩展，保持解析/I/O 无锁、所有待写行先预锁、lease/version/epoch/ACTIVE 身份检查与 revision/指针/audit/outbox 同事务；避免隐式双次完成或靠 ThreadLocal 传结果。
5. **完整运行时与 API 尚未存在。** 当前主源码没有 B06 transcript API/repository/导入 handler/生产 INPUT_BATCH_FILE 策略。需新做真实 batch→input→TaskSpec→worker→保存/编辑/四类 marks→确认，并提供有界完整事实查询给 S1/B07。完整 source/file/task facts 不能被 DTO 常量替代；旧配置及所有 producer 身份必须可恢复。

## 3. 最小真实 MySQL 锁序反例建议（尚未执行）

在新独有空库应用已采用 SQL 后，只建一个 ACTIVE owner、meeting/task_subject、真实固定输入及一个可认领任务。可复用真实 JDBC latch probe 样式；新增专用小用例，不调用旧大 cohort。

- **原 API 组合例**：同 DataSource、同真实事务，先 registry.lock 或 MeetingAccess.lock 得 authentic capability，再 enqueue 试用需要 active 身份的 meeting policy。期望安全复用且入队；当前朴素 policy 若重新 MeetingAccess.lock 会发生 root 后 identity 拒绝。用真实断言记录失败，不能把编译/配置错误叫 RED；这只是最小组合缺口，不冒充生产行为故障。
- **并发顺序例**：以 JDBC 实际 SQL 边界 latch 安排 A 先通过 MeetingAccess.lock 取得 owner identity 并停在 subject 查询之前，B 从 complete/claim 的 lockDomain 取得 subject 并停在 policy identity 查询之前。旧朴素组合形成等待环；保留 SQL/事务结果，断言“可正常有序完成、无领域错误/死锁且未授权效果为零”。更稳定的首个回归可直接断言真实 trace 的 identity FOR UPDATE 必须先于 subject/root/task，辅以有限超时死锁例，避免依赖睡眠/谁成为 deadlock victim。
- **停用输出例**：独立事务先将 owner DISABLED 并占住 identity；完成线程须等待 identity，释放停用事务后拒绝领域 effects/指针写入。同时覆盖普通 validLease/renew、ambient enqueue 和独立 claim；改共享接缝后按受影响范围复核 observe 等调用。
- 一致采用同一个包装 DataSource 构建 tx/access/fences/queue/outbox；probe 只等待/观察真实 SQL，不替换 DB 返回值。所有 latch 有 finally release 与短截止，不关闭或调整共享服务。

这三类各证明不同事实；没有实际结果前，仍称“待验证组合假设”，不先改框架。

## 4. 可复用证据与推荐切分

可复用现有源码和测试结构，不重做已完成的 parser/schema/基础设施：

- `MeetingConcurrencyIT.java:39/86/129`：真实 identity disable、二进制身份排序、flush-last 与 JDBC probe；`ReviewConcurrencyIT.java:235`：真实 RR 数据源、before/after SQL/commit/rollback 门控。
- `TaskFixture.java:12`、`TaskLeaseIT.java:20/25/28`、OutboxFenceIT、ReviewConcurrencyIT：真实任务/租约/fence/回滚/skip-locked/最终 flush。TEST_TASK/TEST_INFO/TEST_CALL 仅示例，不能作为生产允许名单。TaskFixture 不检查 ACTIVE 身份，原 GREEN 不覆盖 B06 身份缺口。
- `WorkerProcessIT.java:8` 和 `FileRegistryRestartIT.java:46`：独立 JVM/持久 DB/杀进程恢复样式；已有进程 fixture 不证明新配置索引跨重启恢复。
- `ManagedRegistryFixture.java:13`、FileRegistryUploadIT 的真实 HTTP 与 FileRegistryRestartIT：小负载 local/mTLS 或 S3 实际组合。用新 B06 受控组合，不跑 10001-part/native 容量旧套，不挪用既有私有 fixture。
- ConfigBindings/RawHashChain 的 canonical raw bytes 与独立向量结构可复用；合成预算不是生产默认，旧 `{}` 测试材料永久保持历史，不能转为有效档案。

推荐四个可独立复审的小交付，仍共同构成 B06，不提前声称闭环完成：

1. 技术说明与最小真实锁序 RED；若证实再做窄共享能力修正及受影响 B05 回归，不改 SQL。
2. 不可变配置索引/恢复、准确 batch 指纹和独立 literal 向量；完整受控 raw 档案与输入事实查询，实际跨 JVM 重启验证。
3. 原上传→INPUT_BATCH_FILE→固定 IMPORT/EMPTY input+真实 TaskSpec→显式 Worker provider/handler→原子输出，local/S3 各跑小型真实闭环及失败/迟到/全事务回滚。
4. 保存稿/编辑/marks/确认和历史读取；真实 HTTP 成功及闭合错误/OpenAPI/generated TS 一致；B07 消费手册与完整限定证据，独立 SPEC/quality 审查。

每个交付精确 BASE..HEAD、源文件/原始日志/XML/SHA 允许清单与限制独立封存。用户确认的 guarded 首版及负责人原文核对是 B07 UX，不擅加 B06 行为。

## 5. 迁移和夹具保护

正式 12 个 SQL 文件当前通过 V013（含 V011_1 表示 Flyway 11.1，V003/V004 永久空号），B06 不预期新 SQL。应用新独有已验证初始零库时按已成功采用的完整历史顺序到 V013；不能回头跑只接受旧 target 的历史结构套。原迁移、历史 B00-1.3/提案/封存证据、原工作树和所有既有库/S3/PKI 完全保留；无 reset/repair/outOfOrder/猜号。发现结构缺口先独立增量设计再由协调者登记新版本和 ACK，尚未采用 D01 不授予迁移/运行时许可。

`tools/dev/mysql_fixture.py:102` 可作为独有随机命名、loopback、锁定镜像与私有 env 的创建结构；不使用其 stop 对任何旧容器，不读取/输出 env。若主机 AIO 条件确需 native OFF，仅在新拥有进程显式配置并记录资格边界，不修改共享 helper 或主机设置。`MysqlTestDatabase.dataSource():22` 校验 loopback/name/schema，可复用；**reset():11 执行 DROP DATABASE，禁止调用**。不得执行 DatabaseRecoveryIT、历史 reset/refusal/迁移 wildcard 套。

只采集选定 ID/镜像/挂载/loopback/资源/native UTC/epoch 与真实 SQL history/checksum/SHA，不导出完整 docker inspect/Config.Env、口令、私钥或数据库内容。后续测试通过 `python3 tools/checks/run.py it <ExactClass[#method]>` 精确选择；runner 会处理旧 XML，先归档本次 raw/XML 再跑下一批。零匹配/跳过/缺环境不是成功。公司接口、真实模型/知识库/邮件、HA/fencing、生产与 main 合并/push 均不在 B06 准备授权内。