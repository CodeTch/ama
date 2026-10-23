# B06.2 Controlled Archives and Batch Fingerprint Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: 激活后使用 superpowers:executing-plans，逐任务执行以下 checkbox；本准备阶段不实施、不派子代理。

**Goal:** 交付受控不可变配置档案来源、精确 batch v1 指纹及 processing-only 有界真实事实读取，并以独立 JVM 恢复证明持久性。
**Architecture:** 档案信任锚是部署显式固定的 index 原始 SHA；运行时只读其完整历史快照，业务校验仍交给已修订 S1。新增纯 batch helper 与窄 JDBC 读端，不启用 B06 生产策略、provider 或 HTTP API。
**Tech Stack:** Java 21、现有 Jackson streaming / typed canonical codec / Spring JDBC、MySQL 8.4.6、受保护本地文件目录；不增基础设施依赖。
**Spec:** `docs/acceptance/b/task-B-06-seams-proposal.md` §§4–7；`artifacts/phase-b/resume-2026-10-23/task-B-06-preflight.md` §4 第2项；原 `artifacts/checkpoints/2026-10-22-development-pause/task-B-06-runtime-prepared-brief.md`；`docs/superpowers/specs/2026-10-22-ama-config-wire-contract.md`。
**Mandatory adopted inputs:** `artifacts/phase-b/b07-pure-s1/consumer-handbook.md` + `artifacts/phase-b/minutes-pure-s1-fix1/consumer-handbook.md`；B03/B04/B05（含 fix1）采用手册；`artifacts/phase-b/b06-queue-seams-fix/coordination/task-B-06-seams-root-ack.md`。历史 pending 标题不覆盖采用记录。
**Status:** PREPARED / NOT ACTIVATED，2026-10-23；此次仅静态检查并写此 root ignored 文件。作者只读源为 `4dcdc6b5102c7d99b043385349b4a87c98b52e80`；root 已双 PASS 采用到 `0a85ef62007e2f8fd2d590002f3592e79f4767f1`。二者都不是本准备书自行授权的实施 BASE。

## Global Constraints

- 激活须由 root 指定精确 BASE、独有可写 worktree/branch、最终允许文件和新夹具；不 checkout/import/merge/rebase 当前受审作者树，不修改 B06.1 seal/report/HEAD。
- 全12正式 SQL through V013（含 V011.1、永久空号 V003/V004）字节不变；无新 SQL、猜号、repair、reset、旧 refusal/Recovery/wildcard/native/S3/PKI 重套。
- B06.1 两个证据目录及独立 literal 向量全部保留；新增证据独用 `artifacts/phase-b/b06-archives/`，每次执行先保全 raw log/exit/XML/source SHA，失败 setup 不叫 RED。
- 原始档案必须为完整 canonical JSON bytes；无 `{}` sentinel、内存清单代替持久来源、当前默认替代历史 Ref、Actor.status 授权或宽松未知策略。
- 档案磁盘 I/O、严格 JSON、指纹计算均在 DB 事务外；DB 短事务只做当前身份授权及有限完整读取，后续业务写入仍须重新核对真实 fence。
- 不修改旧 S1 profile/闭包/FullRow 语法、固定向量或旧测试 batch hash；新 helper 明确只为 `ama.input-batch.v1`。

## Review Focus

- 默认切换或新 JVM 启动后，旧 manifest 的 exact type/version/SHA 仍解析原始 bytes；缺历史不得回退。任务2、4。
- 文件大小、总量、目录链接/替换、取消在首次分配/owned copy 前受限，不能先 readAllBytes 再检查。任务2。
- 额外/矛盾 task、producer 或跨 meeting file 不能被 LIMIT 截断/DTO 常量隐藏；LONGTEXT 先有限检查。任务3。
- batch 的请求顺序、size、batch/meeting/base 身份变化必须改变 digest；raw JSON SHA 不能替代 typed hash。任务1。
- 部分发布失败、旧身份被重映射或历史删除必须失败且保留上个完整 release；不声称未验证的掉电/HA 保证。任务2、4。

## 实际接缝与限定决策

- `ConfigBindings.computeProcessing/verifyProcessing(byte[]|FullRow, StoredProvenanceFacts, List<RawArchive>, ArchiveReadBudget, BooleanSupplier)` 已重验原始 SHA、schema/canonical bytes 和精确闭包；其成功 token 私有构造，继续复用，不改其权威边界。
- `RawArchive` 含未隔离 byte[]；`ArchiveBytes.capture(maximum, remaining, cancelled)` 才是先预算后 clone。文件读端先检查文件长度与累计容量，再分配有界 transport array；交给 ConfigBindings 做一次档案 owned capture，不把 ArchiveBytes 再转回数组。
- `StrictConfigJson` 为 package-private 且字段集合关闭；index 使用单独严格 reader/有限字段集，不放宽现有 archive/manifest 语法。`ConfigSchema.archive/dependencies`、`CanonicalJson`、`ArchiveReadState` 可直接用于启动验证。
- 首个受控 root=`PROCESSING_CONFIG:b06.controlled.import-txt.1`，完整四节点为该 processing、PARSER_CONFIG `ama.strict-utf8-lines.v1`、CANONICAL_CODEC `ama.canonical-value.v1`、CONFIRMATION_HASH `ama.transcript-confirmation.v1`；processing 三依赖、confirmation→codec，另两节点无依赖。
- 该 root 只绑定 IMPORT/TXT/EMPTY 单步 IMPORT_TXT；sourceRevision/parentInput/priorOutput/model/prompt 为 null，materials={mode:EMPTY,items:[]}，profiles 精确 codec+confirmation；不启用 ASR/增强/材料/模型。
- 固定受控预算：ParseBudget(65536,65536,2048)，最多32文件；CanonicalBudget(16777216,32,1000000,2097152,250000)；ArchiveReadBudget(65536,1048576,16,32,8,200000,32,255,65536,262144,1048576,65536,32,250000,500000)。不是产品配额或 production defaults。

## Task 1: 精确 batch v1 与独立向量

**Files:** Create `backend/core/src/main/java/ama/transcript/BatchFingerprint.java`；test `backend/core/src/test/java/ama/transcript/BatchFingerprintTest.java` 与同模块 `src/test/resources/ama/transcript/batch-v1.literal.*`；保留原 `tools/b06_batch_vector.py` 及 B06.1 goldens。
**Interfaces:** 嵌套 `Member(long ordinal,String fileId,String sha256,long sizeBytes)`；`String compute(String meetingId,String batchId,String kind,long baseRevision,List<Member> members,CanonicalBudget budget,BooleanSupplier cancelled)`；`String verify(FullRow batch,List<FullRow> members,List<ContentFile> files,CanonicalBudget budget,BooleanSupplier cancelled)`。verify 从完整真实行重建并比较 batch.input_fingerprint；纯 helper 不证明授权/数据库存在。
- [ ] 先冻结独立 JSON/hex/bytes/SHA 到新测试资源；对照旧 literal，734 bytes，SHA=`c4a558e4dc8aff8289b4ed60193efdfbc7f64f73ae642e2eec6398c473458053`；独立 Python 不导入应用，另以 OpenSSL 复核 bytes。
- [ ] 写 `literalVectorMatchesIndependentBytes`、`orderAndEveryIdentityFieldAffectDigest`、`invalidOrIncompleteMembersFailClosed`；先给不可成功的最小可编译入口，执行 `python3 tools/checks/run.py unit BatchFingerprintTest`，保存真正 assertion RED。
- [ ] 实现固定 envelope `{profile:ama.input-batch.v1,kind:INPUT_BATCH,data:{meetingId,batchId,kind:TRANSCRIPT,baseRevision,files:[{ordinal,fileId,sha256,sizeBytes}]}}`；复用现有 codec，不自行写第二套生产编码器。
- [ ] 精确 lowercase UUID/hash、UINT≤Long.MAX_VALUE、非空、ordinal=1..N、无重复 file、请求顺序；验证 files 四参 meeting 归属/完整匹配。created_at、taskId、定位/defaults 不入 hash；零字节可 hash 不代表 TXT 解析成功。
- [ ] 复跑该 selector，检查 literal、顺序、size/ID/base 变异、非法 UTF/UUID/hash/溢出、缺/额外 file；GREEN 后只提交本任务文件。

## Task 2: 受控 index、原始 bytes 与追加发布

**Files:** Create core `ama/config/ConfigArchiveSource.java`、`ConfigArchiveIndex.java`、`ProcessingManifestRefs.java`；adapters `ama/config/json/StrictConfigIndexReader.java`、`ama/config/file/ControlledConfigArchiveSource.java`、`ControlledArchiveReleaseWriter.java`；test `backend/adapters/src/test/java/ama/config/file/ControlledConfigArchiveSourceTest.java`；独立工具 `tools/b06_archives_vectors.py` 与新 evidence 下 literal 原始档案/index。
**Interfaces:** `ConfigArchiveSource.open(Path root,String exactIndexSha,IndexBudget indexBudget,ArchiveReadBudget validationBudget,BooleanSupplier cancel) -> Snapshot`；`Snapshot.loadClosure(List<Ref> roots,ArchiveReadBudget budget,long reservedManifestBytes,BooleanSupplier cancel) -> List<RawArchive>`；`ProcessingManifestRefs(ProcessingManifestReader).roots(byte[] originalManifest,ArchiveReadBudget budget,BooleanSupplier cancel) -> List<Ref>` 只做有界严格 Ref discovery，不签发 processing 成功。
**Publication interface:** `ControlledArchiveReleaseWriter.publish(Path root,String previousIndexSha,List<RawArchive> appended,IndexBudget indexBudget,ArchiveReadBudget validationBudget,BooleanSupplier cancel) -> String newIndexSha`；首次 previous=null。仅显式离线管理工具/库调用，不注册在线服务、API 或运行时自动 publisher。
- [ ] 写并运行 `ControlledConfigArchiveSourceTest`，真实临时目录上的缺失历史/默认回退、追加重映射、预算前置与路径链接拒绝产生 assertion RED；JSON syntax/编译失败另计。
- [ ] 实现独立 index grammar：`{indexSchema:ama.config-index.v1,generation:UINT>0,previousIndexSha256:null|HASH,entries:[{artifactType,version,sha256,sizeBytes}]}`；字段关闭、canonical JSON、strict UTF8/重复字段/BOM/未知 schema 拒绝；entries 按 artifactType ASCII 后 version unsigned UTF8 排序，identity 唯一。
- [ ] 固定受控 IndexBudget(maxIndexBytes=4194304,maxEntries=4096,maxReleaseBytes=67108864,maxGenerations=128)；每对象仍≤65536；启动校验的 tokens/scalars/dependencies/depth 用单个显式 validationBudget 累计，不能每节点重置或拿其假冒 processing operation budget。
- [ ] 布局为 `releases/<indexSha>/index.json` 和其下 `objects/<rawSha>.json`；绝对受保护 root+exact index SHA 是外部信任锚，默认 root Ref 单独显式配置。无 latest/CURRENT、版本自动探测、任意客户端路径或 secrets。
- [ ] open 严格检查选中 index/前代 index 链的 generation 连续、旧 entries 原样包含、选中 release 全对象 SHA/header/schema/完整依赖图；Snapshot 只保留不可变索引/依赖元数据，load 每次重验文件长度、EOF、raw SHA，返回本次独有 transport arrays。
- [ ] 用受保护目录的相对句柄/NOFOLLOW_LINKS（所需 SecureDirectoryStream 不支持则 unavailable），拒绝链接/非普通文件/逃逸。先 size/累计预算（含 manifest reservation）再分配；有限 FileChannel 读取，取消/中断关闭，30秒整体截止；记录本地 regular-file 中断边界，不称网络 read timeout。
- [ ] manifest Ref discovery 单独受同一操作上限约束，临时 capture 不导出 raw array；后续业务验证仍从原始 manifest bytes 开始。transport + S1 档案/manifest capture 各≤1MiB，discovery临时capture另≤65536 bytes；原DB字符串也受manifest上限，禁止 unbounded readAllBytes/重复 ArchiveBytes→array 克隆。
- [ ] publisher 验证前代和全新 closure 后，在本次独有 staging 写完整新 release、fsync 文件/目录并 atomic rename；旧 identities 不变、旧 objects/releases 不删、不修复、不覆盖，不使用可变共享 hardlink；平台不支持原子发布就失败。
- [ ] 测 strict 字段/UTF8、错 SHA/size、缺/额外/循环 closure、累计超限/取消、链接与替换、追加 remap/drop、发布失败旧 release 仍可读；GREEN 后提交。独立 raw fixture root.1→root.2 仅新增一个 processing identity，index4→5节点，公共三节点原样复用。

## Task 3: processing-only 有界真实 DB 事实读端

**Files:** Create `backend/core/src/main/java/ama/transcript/ProcessingFactsReader.java`、`backend/adapters/src/main/java/ama/transcript/JdbcProcessingFactsReader.java`；test `backend/integration-tests/src/test/java/ama/transcript/ProcessingFactsReaderIT.java`、`ArchiveProcessingFixture.java`。
**Interfaces:** `readBatch(Actor actor,String meetingId,String batchId,ArchiveReadBudget budget,BooleanSupplier cancel) -> StoredProvenanceFacts`；`readStored(Actor actor,String meetingId,String inputId,ArchiveReadBudget budget,BooleanSupplier cancel) -> StoredProvenanceFacts`。首版 standalone-only，拒绝 ambient；JDBC 实现由一个 DS 内部构造 TransactionRunner/JdbcSubjectFences/JdbcMeetingAccess，先 lock+require(EDIT)，不接收外部 capability、不扩展共享协议。
- [ ] root 明确新夹具后，先记录 initial-zero/镜像/资源/loopback/正式12 SQL lowerSHA来源再 migrate through V013；新增 IT 不用 reset。写 `readsCompleteStoredProcessingFromActualRows`、`extraTaskAndWrongProducerCannotDisappear`、`boundedMetadataBeforeMaterialization`，保存 assertion RED。
- [ ] readBatch 读完整 INPUT_BATCH、全部 INPUT_BATCH_FILE ORDER BY ordinal、每个 file_object 不可变 id/meeting/sha/size 的四参 ContentFile；readStored 再读完整 TRANSCRIPTION_INPUT、全部相关 durable_task 的实际 taskId+全 TaskSpec、全部 owning TRANSCRIPT_REVISION 及其 producer_task_id 指向的真实 task 并集。
- [ ] 按 Table.columns 固定投影/nullable/UTC微秒/UINT 语法构造 FullRow；taskFacts 使用 StoredTask，taskSpecs 可空，不以当前 meeting revision 填历史 fence。所有跨 meeting/来源边交给纯 validator 逐一验证；无输出即完整空集。
- [ ] 在已锁 root 下用有序 current reads 保持同一次真实事实视图：领域行→files→tasks；每类 LIMIT remaining+1，超限失败而非截断成功，合计 reserve 后才建 DTO/list/copy。config_manifest 先锁行读取 OCTET_LENGTH/CHAR_LENGTH 并检查预算，再有限字符流物化，不能先 getString(LONGTEXT)。
- [ ] 短事务返回 detached facts 后再读 archive/调用原 computeProcessing/verifyProcessing；readStored 不签发写入授权，也不插入/更新任何业务行。需要写事务内未提交新批次/真实 held capability 的组合，留 B06.3 窄接口复审，禁止把 standalone reader 嵌入 enqueue 写链。
- [ ] IT 验证额外矛盾任务、missing/wrong producer、ordinal gap/多余成员、foreign meeting file、DISABLED 实时身份、非 owner、ambient 拒绝、超限/取消，以及历史旧 fence不被当前版本替代；SELECT/断言须用真实 DB，不替换返回值。
- [ ] 串行执行 `python3 tools/checks/run.py it ProcessingFactsReaderIT`，actual XML 有执行且0 failure/error/skip方为 GREEN；归档后提交本任务。fixture SQL 数据仅为受控 metadata/真实队列任务事实，不声称合格上传、生产 producer 或已完成 Worker。

## Task 4: 跨 JVM 恢复、消费者说明与独立审查

**Files:** Create `backend/integration-tests/src/test/java/ama/transcript/ConfigArchiveRestartIT.java`、`ArchiveRestartProbe.java`；`docs/acceptance/b/task-B-06-archives-integration.md`、`task-B-06-archives-report.md`；`artifacts/phase-b/b06-archives/` 允许清单、source/SQL/manifest SHA 与原始证据。
- [ ] 写真正进程级 RED：JVM A 用 release1/root.1 与实际 DB batch/input/真实 queued TaskSpec 保存旧 processing digest，commit ACK 后正常退出本次子进程；JVM B 必须全新启动，不能复用静态 Map/DTO。
- [ ] 发布 release2/default root.2，同时保留旧4节点；B 从 exact indexSHA2 和真实旧 manifest refs 找回 root.1 原 bytes，readStored+verifyProcessing 得原 digest；另独立 draft 选择 root.2。禁止把旧 manifest 改成新默认。
- [ ] 在独有负例副本分别缺失/变异历史档案或错误 index SHA，fresh JVM 明确 unavailable、无 fallback/DB变化；不破坏保留的正例 releases/旧行。保留每进程 stdout/exit/commit handshake/source hashes。
- [ ] 精确串行运行 `python3 tools/checks/run.py it ConfigArchiveRestartIT`；按变更实际影响跑 Task1/2 精选 unit，然后当前全 Java unit `python3 tools/checks/run.py unit` 一次；每批先存 XML，报告区分独立方法/参数化 invocation/重复执行。
- [ ] 手册说明显式配置/发布/保留/预算/异常和新 reader 的权限与完整性边界；报告精确 BASE..HEAD、fixture初始零库、迁移 history/checksum、全部失败/RED→GREEN、完整 source/manifest hashes。无 HTTP/Worker/上传闭环验收声明。
- [ ] 清洁本地提交；root 安排独立 SPEC+quality review，发现问题另有版本化修复证据；双 PASS/采用之前不归并、不 push、不启用 production。root 给最终基线后再封存，不改旧 B06.1 文件。

## 真实缺口与下一交付门禁

- 当前没有 durable config index/source/publisher，任务2实现；没有 bounded processing JDBC reader，任务3实现。原 S1 只有内存验证与私有成功 token，不能宣称已有存储权威性/恢复。
- input_batch 无 fingerprint profile/version 列：本交付只提供显式新 v1 helper；不得自动重解释旧 hash。将来算法升级/混合历史判别须 root 另审 forward schema 设计，不猜迁移。
- input_batch_file 无原 upload UUID/purpose：本读端不能据此授予文件引用/下载。首次 INPUT_BATCH_FILE、原完成 session/purpose、AVAILABLE/路由/epoch 与 current/history 读取资格留 B06.3/4 按 B04真实协议实现。
- 不存在 current processing-input 指针；任务3按真实 input/task 身份读取，业务当前性以后按 saved base/fence 设计，不新增假列。完整 segment/marks/confirmation/parent/source graph/B07 facts 读取不在本次 processing-only 合同中。
- Supplier 完成重载/WorkerLoop handler、生产 TaskDomainPolicy/provider、API/幂等/保存/编辑/确认/OpenAPI/TS、local&S3 业务闭环均留后续交付；本次跨 JVM 证明的是档案与真实 metadata 恢复。
- 现有 queue 候选查询 LIMIT64 的公平性边界另立 B05/B06 后续任务：前64连续 held/不可获取/未注册时后候选不可见且 skip不推进；B06.1只承诺窗口内 unknown skip。本次不修扫描，外部 Worker 激活前须登记/满足该既有资格门禁。

## 新夹具与证据保护

- Java21 使用公开路径 `/home/tanren/ama/.worktrees/phase-a/.tools/zulu21.52.203-ca-jdk21.0.12.1-linux_x64`，只设置工具路径，不加载旧私有配置。纯 unit/临时目录先行，不连接 DB；真实 IT 仅使用 root 激活时分配的新独有 MySQL：随机唯一 name、loopback、initial-zero，建议768MiB/1CPU/256pids、锁定8.4.6镜像，必要 nativeAIO OFF只限新进程并记资格边界。
- 不接触 B06.1 容器 `ama-test-b01-ff63b53162a3` 或任何旧库/容器/S3/PKI，不 source 旧私有 env，不 stop/reset/repair；只可用 MysqlTestDatabase.dataSource，禁止 reset。环境仅私有加载、不输出 env/Config.Env/口令。
- 本次新 root/release/staging/temp 和失败副本均独有；不清理旧 evidence。保留失败命令原文/退出码；selector未匹配、缺环境、skip、setup/编译错误不计业务 RED/PASS。

## 自检与接续条件

- Spec覆盖：proposal§4→任务1；§5/6→任务2/4；§7中本次 processing facts→任务3；余下首次业务引用/完整API/Worker明确延期。四任务接口同名同参，五项 Review Focus 均有所属真实测试。
- 本准备阶段没有测试执行、DB/Docker访问、发布或源码修改；没有新增通过声明。全文约110行，采用 writing-plans 的任务/接口/RED→GREEN/独立review结构，路径遵从 root 指定位置。
- **待核实：** 新根目录所用 filesystem 的 SecureDirectoryStream/目录fsync/atomic rename 行为、新 JDBC LONGTEXT 预界定实现与跨 JVM fixture 的最小排程尚未运行；任务中的新签名为待激活实现建议，非已存在 API，遇不支持须 fail closed并返 root，不扩大本次准备探索。
- **下一步条件：** root 审阅/收敛此 prepared brief，指定最终已采用精确 BASE、独有写树和新夹具边界，再显式激活 B06.2；当前保持 PREPARED，不主动启动实施。
