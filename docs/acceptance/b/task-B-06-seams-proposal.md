# B06.1 技术接缝提案与最小真实证明

状态：**NEEDS_CONTEXT — 等待 root 对本提案的 ACK**。日期 2026-10-23。唯一实现树为 `/home/tanren/ama/.worktrees/phase-b-transcript-runtime`，分支 `codex/phase-b-transcript-runtime`，BASE/当前 HEAD 均为 `6b48b2f855bbf890c3ccad68325e7fd74d434489`。本阶段只有新测试、取证工具和说明；未修改共享生产接口、fingerprint/runtime 或正式 SQL。完整 B06、生产策略、生产 provider 和配置均未交付。

## 1. 已读取的约束与真实现状

采用 B03 `meeting-domain-integration.md` / adoption，B05 `task-execution-integration.md` + 强制 `task-B-05-fix1-integration.md`，B04 `file-registry-integration.md` / adoption，S1 原 `consumer-handbook.md` + fix1 addendum，以及原 B06 prepared brief、接续 brief/preflight、config-wire 采用说明、V008/V011/V011.1 和实际 core/adapters/worker 源码。历史手册 pending 标题不覆盖采用记录。

实际 `JdbcTaskQueue.lockDomain` 为 subject → policy.lockRoot；`JdbcMeetingAccess.lock` 为 identity → subject → meeting，并拒绝 root 之后再次 identity prelock。claim 拒绝 ambient。生产没有 meeting task policy，故下列结果是**受控组合缺口证明**，不是已部署 B05 生产故障。B04 测试的 current-transcript 前提和 `{}` manifest 永远不作为 B06 模板。

## 2. 实际首个 RED 与控制例

精确命令（先 source 本次新夹具的私有 env，再配置公开 Java21 路径）：

```text
python3 tools/checks/run.py it B06QueueSeamsIT
```

原始 `red-run.log` / `red-run.exit`、`run-1/.../TEST-ama.task.B06QueueSeamsIT.xml` 和当次源码均在 `artifacts/phase-b/b06-queue-seams/`。

- 3 个 IT，**2 failures / 0 errors / 0 skips**，命令 exit 1；同一 Maven verify 顺带实际执行179个 Java unit，0 failures/errors/skips。两类计数分列，不把重复执行累加为独立覆盖。
- `standaloneClaimMustLockIdentityBeforeSubject`：真实任务已成功 enqueue/claim，真实锁 SQL 索引 identity=1、subject=0、meeting=3、task=5，违反断言 identity < subject < meeting < task。没有虚构数据库返回，也不是编译/setup RED。
- `ambientEnqueueMustReuseAuthenticMeetingCapability`：同一 DataSource/真实事务已有 access.lock + require(EDIT)；enqueue 的朴素 policy 重入真实 access.lock，得到 `Identity prelock cannot follow a meeting root lock`。期望“安全复用已有能力”失败；实际 task/outbox 都0，事务回滚。
- `disabledIdentityActuallyBlocksCompletionAndPreventsEffects` 控制例 **GREEN**：A 的真实 UPDATE identity=DISABLED 持有未提交 identity；B 的 complete 到真实 identity SELECT FOR UPDATE 的 before-execute latch；A 提交后 B 拒绝，effects=0，meeting stage=ENTRY，task=RUNNING/output=NULL。Actor.status 仍 ACTIVE，未参与授权。门控是实际 JDBC 边界和10秒上限；本例未查询 lock-wait 表、未声称度量实际等待时长或复现死锁。B 的 trace 仍先 subject 后 identity。

另有独立精确 selector `B06QueueSeamsIT#currentThreeArgumentCompletionValidatesOutputBeforeEffects` 观察现有三参完成顺序，实际1 test / 0 failures/errors/skips、exit0，结果见 proof report；它是行为控制例，不称新生产故障。

## 3. 请求 ACK 的最小身份/能力协议

建议显式传递、绑定实际事务的 opaque preparation；不引入 ThreadLocal、第二个 outbox synchronization、外包 claim 或晚取 identity。以下是待 ACK 的签名草案，不是已改 API：

```java
interface TaskDomainPolicy {
    interface PreparedDomain {}
    enum Acquisition { TASK_MUTATION, EXTERNAL_FACT_OBSERVATION }
    Optional<PreparedDomain> prepare(List<TaskSpec> completeDiscoveredSpecs, Acquisition purpose, boolean skipLocked);
    void requirePrepared(List<TaskSpec> specs, Acquisition purpose, PreparedDomain prepared);
    boolean lockRoot(TaskSpec spec, boolean skipLocked, PreparedDomain prepared);
    // validateInput/validateOutput/external validators retain their responsibilities.
}
String TaskQueue.enqueue(TaskSpec spec, TaskDomainPolicy.PreparedDomain alreadyHeld);
```

1. standalone queue 操作在任何 subject/root/task 锁之前调用 prepare；领域准备锁**完整**身份集，按 unsigned UTF8 排序，当前 shared identity 必须 ACTIVE。B06 执行身份从真实 meeting.owner_id 发现，根锁下验证 owner 未变化；不是从旧 Actor.status 或客户端 actor 字段赋权。导入只修改 owner 的转写域，不变更 roster/grants，因此此操作的完整集合是实际 owner；如未来操作影响其他身份，必须先发现完整并集。
2. B03 增加 opaque identity-phase capability 和从它进入 subject/root 的窄入口：`tryLockIdentities(actor, completeAffectedIds, skipLocked)` 与 `lockPrepared(identityCapability, meetingId, skipLocked)`。保留现有 lock(actor,id)/require 契约与 rootsStarted 防逆序。新入口只允许 authentic issuer + 同 DataSource + 同实际 Spring transaction holder；不能由公开接口实现伪造成功。
3. API 先 `registry.lock`，拿 `registry.access(held)`。B06 policy 提供 `adopt(spec, authenticMeetingCapability, alreadyLockedInputFacts)`，验证同 holder/issuer、subject、身份覆盖、当前 action/fence，返回 PreparedDomain；enqueue 显式传入，队列不重新 identity prelock。无 capability 的 ambient 重入不会猜测已有锁或变成默认授权。
4. PreparedDomain 绑定 issuing policy、实际 holder、完整 subject/spec集合、身份能力和已持有领域行。每次使用先 requirePrepared；伪造、跨事务/DS/策略、缺身份/缺领域覆盖均失败。结束后 token 不再有效；仅调用栈传递，不新建全局事务缓存/synchronization。
5. skipLocked=true：身份逐个真实 FOR UPDATE SKIP LOCKED，任何身份缺失/被锁/非 ACTIVE，整个候选失败；不得拿不完整身份进入 subject/root。已取得的身份仅在该候选短事务结束前保留。false 使用普通锁。subject/root/task 同样遵守 skip，claim 不退化为阻塞式 access.lock。授权读/roster 的既有非 skip 契约不改变。
6. 队列内部返回局部 DomainScope(subject, preparation)，多次 lockDomain **复用该局部 scope**，不再重复 prepare。尤其 retry 的首次、producer replay、再次 fence 检查共用同一能力；不得在已有 root/operation 后补取 identity。旧无身份的明确 test 注册策略可显式实现 identity-free preparation；未知策略仍拒绝，无 unrestricted default。
7. external observe 的初始 discovery 已有 current/old TaskSpec：在 subject 之前将完整集合一次交给领域 prepare；不同 policy 的联合能力必须由同一明确领域 owner 签发，否则 fail closed，不能依次预锁策略身份造成跨策略逆序。B06 本次只启用本地 TXT，external domain 没有生产注册。

| 调用方 | 初始能力/复用 | 其他约束 |
|---|---|---|
| enqueue | standalone prepare；ambient 显式 adopt 真实 gate | 同事务 batch/ref/input/task/audit/outbox |
| claim | 独立事务、skip=true prepare | 每候选独立结束；不可包 ambient |
| renew / complete | 当前 lease spec 的 fresh prepare，skip=false | 先身份再 subject/root/task，DB当前租约 |
| beginExternal | 经 validLease 走 fresh prepare | 保留独立 marker 事务/commit 后 authority |
| claimReconciliation / external renew | lock(seen) 的全发现 specs fresh prepare | 保留独立事务、skip语义 |
| observe | current+old specs 完整集合先 prepare | 保留 fix1 独立事务、4次 rollback/rediscover、旧成功保留/隔离；不因身份停用丢失权威外部事实，领域 effects 仍禁用 |
| retryAfterZeroEffect | 首次 prepare 的 scope 在全部 lockDomain 重用 | 保留完整 proof、任务排序、operation/execution顺序；不新增外部发送授权 |

external 的“记录已发生事实”和“允许领域写入”仍有区别：能力可明确记录身份不再ACTIVE，使 observation保留权威事实而 effects fenced；ordinary claim/renew/complete/enqueue fail closed。上面显式 Acquisition 区分任务变更与外部事实登记；观察能力保留identity当前状态、canApplyDomain=false且只为其事实路径取得锁，不签发可通过MeetingAccess.require的禁用身份授权gate，不能把禁用身份升格成 ACTIVE 或 blanket throw 导致事实永远不可登记。此边界须纳入受影响 B05 regression。

## 4. 新 input_batch fingerprint 的精确定义与独立字面向量

仅新增 `ama.input-batch.v1`，kind=`INPUT_BATCH`。使用现有 `ama.canonical-value.v1` typed binary codec 对以下 envelope 完整编码，再 SHA256 lowercase64：

```text
{profile:"ama.input-batch.v1",kind:"INPUT_BATCH",data:{
 meetingId:UUID,batchId:UUID,kind:"TRANSCRIPT",baseRevision:UINT,
 files:[{ordinal:UINT,fileId:UUID,sha256:HASH,sizeBytes:UINT}, ...]}}
```

字段集合精确关闭；UUID canonical lowercase；hash lowercase64；UINT 0..Long.MAX_VALUE；ordinal连续从1；文件非空、无重复，保留请求顺序。无 trim/换行/Unicode/UUID自动修复。每个 size 来自真实不可变 file metadata；原 purpose/uploadId/AVAILABLE/meeting/epoch/授权另由 B04/B06 验证，不以 hash 替代。batchId/meetingId/baseRevision 是该不可变批次的身份，纳入 digest；created_at、存储定位/当前默认、taskId不纳入。全0字节文件可计算batch digest，但 TXT parser 仍可 NO_TEXT 拒绝，不能将指纹向量当解析成功。

codec tags=00 null/01 false/02 true/03 uint/04 scalar/05 array/06 object；数量/长度/uint均8字节BE；key按unsigned UTF8排序，string为strict UTF8，数组保留顺序。业务 digest不是 raw JSON SHA，也不是 TRANSCRIPTION_INPUT digest。

独立 Python `tools/b06_batch_vector.py` 不导入应用代码；固定 meeting=11111111-1111-4111-8111-111111111111，batch=33333333-3333-4333-8333-333333333333，baseRevision=9，files为(ordinal1,file444...,empty SHA e3b0c442…b855,size0)、(ordinal2,file555...,SHA(A)559aead0…ffd,size1)。全部字面字段、完整734字节 hex及SHA分别在 `batch-v1.literal.json/.hex/.sha256`；独立 openssl交叉核对同一bytes：

```text
734 bytes
c4a558e4dc8aff8289b4ed60193efdfbc7f64f73ae642e2eec6398c473458053
```

这是设计向量；ACK前没有新生产fingerprint实现。现有15类archive/profile不新增/重解释；B06只对本生产路径创建的新TRANSCRIPT批次使用本v1，历史测试hash不迁移。将来切换batch算法必须另做可恢复的版本判别设计，不能就地改v1。

## 5. 真正不可变档案来源与重启/保留

建议 B00 允许的**受控只读文件目录**，不虚构公司平台或未来V026：显式配置绝对 archive-root、index-generation、index原始SHA、defaults根Ref，API/worker启动读取同一已验证快照。目录为部署拥有、运行用户只读；对象路径只能为 `objects/<sha256>.json`，拒绝符号链接/非普通文件/目录逃逸；对size先界定，再有限读取并验证raw SHA/header/canonicalJSON和完整闭包。缺配置/缺历史/mutation/未知schema/profile明确 unavailable，绝不 fallback current defaults。

index是一份有schema/version、generation、前代index SHA、按(type,exact version)有序唯一entries的canonicalJSON；每entry有type/version/sha256/sizeBytes，路径由SHA派生，不由客户端指定。部署的受保护index SHA是信任锚，SHA自身不证明发布者。新generation只能追加新身份，旧映射/对象字节保持；离线发布验证前代全量映射仍存在、所有closure合法，再写临时release目录，fsync文件/目录，原子rename，保留旧release。运行进程不实现在线发布/回滚服务，E03另审。

defaults只选择新输入的精确root Ref，独立于历史读取；历史永远按saved manifest中的type/version/SHA查index。每次重启从磁盘重新读取全历史index和原始bytes，重建严格reader；没有内存DTO清单作为持久性证明。新release/index必须保留所有已落库manifest引用、旧defaults和旧processing/parser/codec/confirmation闭包；本阶段无GC/TTL。脱离DB的归档缺失是部署错误，不能通过重算新defaults修复。后续交付必须实际跨JVM验证：写旧input→停进程→增加新root/default→新JVM恢复旧SHA→缺失/变异拒绝。当前仅提案，未声称这项已通过。

## 6. TXT IMPORT / EMPTY 的精确闭包和预算

初始显式受控配置建议 `b06.controlled.import-txt.1`（不是生产默认）。raw archive exactly4：PROCESSING_CONFIG该version、PARSER_CONFIG=`ama.strict-utf8-lines.v1`、CANONICAL_CODEC=`ama.canonical-value.v1`、CONFIRMATION_HASH=`ama.transcript-confirmation.v1`。processing deps按slot字典序为CANONICAL_CODEC/CONFIRMATION_HASH/PARSER_CONFIG；confirmation唯一dep codec；parser/codec空deps。引用必须type/version/raw SHA完全匹配，无额外node。

raw manifest继续采用已采用ama.transcription-input.v1：kind IMPORT，batch TRANSCRIPT和完整ordinal/file/sha/size；sourceRevisionId=null；materials={mode:EMPTY,items:[]}；model/prompt=null；profiles准确两项codec、confirmation；base完整meetingRevision/subjectBusinessVersion/cancelEpoch；恰好一步IMPORT_TXT、source INPUT_BATCH和精确batchId、config/parser原Ref、parentInput/priorOutput/model/prompt均null。绝无 `{}` sentinel。非EMPTY材料、ASR、增强、多步/格式自动探测均拒绝。

明确受控技术预算（不是产品大小/数量规则，也不隐式启用production）：

- parser每文件 ParseBudget(maxSourceBytes=65536,maxUtf16CodeUnits=65536,maxSegments=2048)；最多32文件，因此来源总量上界2097152 bytes/UTF16 units、65536 segments，累加用减法/overflow检查。全原文保留，预算超限失败，不截断。
- archived CanonicalBudget(maxEncodedBytes=16777216,maxDepth=32,maxNodes=1000000,maxStringBytes=2097152,maxContainerEntries=250000)。
- acquisition ArchiveReadBudget按真实构造顺序为 (65536,1048576,16,32,8,200000,32,255,65536,262144,1048576,65536,32,250000,500000)。
- 前述source/row数先LIMIT budget+1/计数检查后建有界carrier；archive每个和累计先检查再首次owned copy，共享ArchiveReadState，不因历史节点重置。对象I/O显式connect/read deadline=5s、单任务wall deadline=30s，取消/中断关流；此受控组合pool capacity=1，不宣称容量/生产SLA。
- 部署将来可显式选择新配置version/技术预算；已有input仍引用原始档案bytes。未配置任何production root则503/unavailable。

## 7. 真实事实解析、首批引用与 TaskSpec

API独立入口保留session/Origin/CSRF/expectedRevision/Idempotency-Key。锁序是idempotency→registry.lock身份/subject/meeting→所有input/batch/当前历史领域行→一次bindAll的全部session排序→全部file排序→task→audit/outbox。raw档案加载和后端I/O在事务外；当前事务只用已验证不可变snapshot。草稿姓名/分类/日期/roster不阻断。

request expectedRevision=r且根/subject一致，导入命令的固定新版本为r+1；batch.base_revision、input.base_revision、manifest base两个revision与TaskSpec.businessVersion均r+1，cancelEpoch原样固定。同事务advance root/fence、stage TRANSCRIPT_PENDING，保留旧transcript内容/指针直到新worker成功；没有current processing-input列，用真实input.base_revision + task fence精确判定，禁止猜新列。后来的任何编辑/导入advance会使旧任务失效。name/date未知保持null。

batch和每行来自当前已授权AVAILABLE managed uploads：完整UUID有序请求、同meeting、原upload UUID/purpose和已完成session、不可变hash/size、locationRevision/backend-route、epoch全部核对。先插入真实input_batch/file，再从实际行完整查询给draft compute；不伪造DTO存在。INPUT_BATCH_FILE policy允许**首次真实batch尚无current transcript**：持有同meeting gate+新batch+全部成员行，原session目的准确TRANSCRIPT_IMPORT；固定input建立后验证manifest/batch/fence。下载资格通过本meeting完整的真实输入→batch→file以及当前或保留历史saved revision/confirmation链，不要求任意fake current指针，technical UPLOAD_SESSION本身不授读权。解绑只针对bindAll中原始uploadUUID，历史源文件不删。

完整查询至少包含：input_batch整行；该batch全部input_batch_file ORDER BY ordinal（连续、无缺/额外/重复）；每个file_object不可变meeting/sha/size（ContentFile四参）及原upload facts；transcription_input整行/raw manifest；所有durable_task WHERE input_kind=TRANSCRIPTION_INPUT AND input_id=...的全TaskSpec和真实taskId；所有该input的transcript_revision以及其producer_task_id真实StoredTask；确认读取再完整segment/marks/parent/confirmation链。PK和外键不证明同meeting/来源，逐边验证；无输出也显式空集。B07以后沿真实闭包查询所有reached input/tasks/config，不用当前root版本补历史fence。查询在当前授权短事务中，后续final重新校验；历史版本只用自己的raw refs。

建议production type=`TRANSCRIPT_IMPORT_V1`，pool=`TRANSCRIPT_IMPORT`，input.kind=`TRANSCRIPTION_INPUT`、id真实input UUID、subject=(MEETING,meetingUUID)、inputFingerprint为ConfigBindings.computeProcessing结果，businessVersion=r+1、priority=0、cancelEpoch=e、maxAttempts=3。producerKey另有明确v1域的typed canonical SHA(profile=`ama.transcript-import-producer.v1`,kind=`TASK_PRODUCER`,data={taskType,meetingId,inputId,inputFingerprint,businessVersion,cancelEpoch})；不将batch/rawJSON SHA当producer key。首次enqueue的全TaskSpec和实际task id落库后，用stored verifier验证全历史匹配。相同key必须全spec相同才回放，冲突整事务回滚。当前seam测试TEST_INFO仅用于真实队列锁证明，不是该生产者。

## 8. Worker原子结果：现有缺口与所需准确时序

当前WorkerLoop.Handler返回TaskResult后loop调用queue.complete；当前三参completeWithEffects先validLease→validateOutput→effects→terminal。因此“parse handler先独立写revision再返回”会提前提交失效输出；“effects插入新revision、validateOutput检查实际新行”又在effects之前失败。真实小控制例用新meeting_info_revision作输出，验证查询缺行、effects=0、task仍RUNNING，展示此先后，不将测试域冒充转写运行时。

建议后续窄增重载，**保留原三参语义**：

```java
boolean completeWithEffects(TaskLease lease, Supplier<TaskResult> prelockedWrites);
```

执行顺序：短事务fresh identity/subject/root/全部既有input/batch/source/domain rows→task→完整lease/spec/DB时间和ACTIVE owner/lifecycle/governance/everPublished/revision/epoch/config/source复核→callback插入真实immutable revision/segments并更新current pointer、清confirmation/final与needs_generation、写audit/stage事件；成功写入令meeting revision/fence从固定输入r+1推进r+2，saved transcript revision同为r+2，task仍保留原输入固定r+1（这是自身结果事务的advance，不重写历史TaskSpec）→取得非null且合法TaskResult→validateOutput查询刚插入的实际行与producer/task/input来源→terminal task/attempt/outbox→最后flush→commit。回调只用已经预锁的现有行，允许按规范插入新immutable行；不I/O/parse、不发现新的身份/旧领域行锁。source metadata和所有FK目标先持有，避免task之后下探file/domain。

lease无效则返回false且不调callback；回调、TaskResult构造/null检查、实际输出校验、audit或flush任何异常均回滚**全部结果/指针/task/outbox**。供应者内存计数不具有DB事务语义，不用于生产effect判定。返回值只是committed acceptance；提交ACK不明不重跑独立写入，按真实task/output恢复。没有提前独立result commit。

WorkerLoop新增显式prepared-handler适配入口（保留旧Handler构造）：外部parse返回仅持有不可变数据的Supplier<TaskResult>；loop在heartbeat未lost时调用上述重载一次。supplier不自己开事务、不再次complete。异常保留当前generic隔离行为，业务明确区分安全本地重试/不可重试；文件SHA/格式错保留原稿。一个显式ServiceLoader provider只注册已实现TRANSCRIPT_IMPORT域/pool，缺配置fail closed；不启用B07/B08/model/company/reconcile handlers，不为纯本地parse造model permit或remote marker。

## 9. ACK 后的限定工作与风险

请 ACK 第3节身份准备/显式复用协议的方向，以及第8节保留原三参的新supplier顺序；准确签名可在同样能力/失败边界内收敛。首个实施只修共享锁协议并跑受影响B05 concurrency/fix1外部回归，再由root安排独立审查。新archive/fingerprint/runtime仍按B06小阶段逐项交付，不能以本note替代真实跨JVM/HTTP/local&S3/失败迟到/编辑确认验证。

最重要实现风险为：external observe停用身份时仍须保存外部权威事实；多spec/cross-policy完整身份并集；skip部分能力不越过身份阶段；相同事务反复domain调用复用；supplier写入后验证失败的全事务回滚。正式12 SQL throughV013字节已与BASE逐项验证不变；如后来发现schema缺口先返root，不猜迁移。新夹具保留、无reset/repair/stop/删除/旧环境/S3/PKI/company/native重套。独立review与完整runtime验收均未完成。
