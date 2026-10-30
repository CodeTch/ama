> **2026-10-28 当前范围（AMA-DEMO-FIRST-20261028 R2）**：按[Demo 优先调整计划](2026-10-28-ama-demo-first-adjustment.md)的业务片段 DEMO-01～05 先交付 M1-Demo，再由 B-QUAL 完成 M1-B 正式本地验收。前期本地 MySQL＋本地文件＋单机，原型页面与功能一起交付；B-L1～4 已采纳，不按旧未勾选模板重做。S3、双 MySQL、双活未完专用实现/配置/测试/依赖最后 F。本轮仅文档。

---

# B：平台基础与首个业务闭环 Implementation Plan

> **For agentic workers:** 沿用 executing-plans 内联与完整业务批次、集中审查；当前状态以接续入口/采纳记录为准。下列历史任务接口与步骤保留需求追踪，不要求重新开发已采纳实现。

**Goal:** 建立经过评审的数据库详细设计基线，用真实持久化完成“转写文件导入→人工确认→纪要→测试知识库发布”，并建立后续功能共用的权限、文件、任务和版本边界。

**Architecture:** 业务状态由 MySQL 事务维护；local/S3 遵循统一端口；任务与外部发布使用幂等、租约和对账。首个闭环采用无音频的转写输入，不冒充已实现实时音频。

**Tech Stack:** 总计划技术栈；Spring Security/JDBC/Flyway、AWS Java SDK v2、JUnit、Playwright。

**Spec:** [架构](../specs/2026-10-21-ama-architecture-design.md) 第 4、6、8—10、12—13 节；[总计划](2026-10-21-ama-delivery-plan.md)。

## Global Constraints

- 继承总计划；未确认名单不授权，平台管理员不默认有正文权限。
- B 使用现有 local 实现覆盖受管文件；文件和目录持久化后再登记 DB、再返回成功。现有 S3 保留，未完适配/差异验证归 F；不能把文件与 DB 当跨资源事务。
- 任务租约初始 20 秒、每 5 秒续租、每 5 秒扫描；DB 可用时 30 秒内可重新领取。
- 业务版本和输入指纹决定能否写回；发布成功只读，未知外部结果先对账。
- B-00 是表结构落地与验收的设计前置；各领域任务消费其已评审数据字典、关系与约束，变更结构先更新相关设计并完成增量评审。

执行衔接：本次新增 B-00 时 B-01 及其他任务已开展，保留已有代码、提交和测试证据，由 B-00 对照执行工作树的实际基线补做设计评审。通用基础设施和不依赖未定结构的工作可继续收尾；依赖未评审结构的新建表/改表及相关任务最终验收须等待对应评审。执行者先同步总计划和本计划，记录工作树、提交及迁移版本，避免只修改主工作区而运行代理仍按旧计划推进。已应用或共享的迁移不得重写或重编号；差异修正使用统一登记的新迁移，并回归受影响任务。

## Review Focus

1. 单机会话和离职撤销在 B-02 验证；`sessionRevocationWorksOnBothNodes` 的未完跨节点部分归 DEP-HA/F-05。
2. 同幂等键不同请求及两窗口编辑：B-03 `sameKeyDifferentPayloadConflicts`。
3. 存储成功而事务失败：B-04 `orphanIsNotReadableAsBusinessFile`。
4. 旧 Worker 在租约到期后返回：B-05 `expiredAttemptCannotCommit`。
5. 生成或发布超时后迟到成功：B-07/B-08 分别检查候选和外部文档唯一性。

## Task B-00：数据库详细设计与评审

**Files:** Create `docs/superpowers/specs/2026-10-21-ama-database-design.md`、`docs/database/ama-data-dictionary.md`、`docs/database/ama-schema-draft.sql`、`docs/acceptance/b/database-design-review.md`。Read 架构第 6、8—10、12、14 节、需求对应清单、A-02 决定台账及执行工作树中已有的迁移/持久化实现；本任务产出设计与评审证据，正式迁移仍由各领域任务维护。

**Interfaces:** Consumes 已确认业务规则、架构核心实体、B…E 任务契约及已有实现基线；Produces 带版本的数据库设计基线，供 B-01…B-08 和后续领域任务使用。基线包含“需求/规则→实体/表→实现任务→计划迁移→验证用例”的追踪关系；记录逻辑关联与实际外键策略，不能将实体名称清单视为完整表设计。公司接口及 E 阶段未定规则列为待补充项，注明责任方、影响范围和解锁条件，不阻塞无依赖的 B 阶段设计，但 B 所需字段和约束不得以待定项替代。

**交付物：**

| 交付物 | 必须包含的内容 |
| --- | --- |
| 数据库设计文档 | Mermaid ER 图（主键、关联键、基数、可选关系）；领域归属；内部/外部 ID、UTC 时间及可空实际会议日期；事务边界、版本/输入指纹、幂等、权限、删除与审计保留策略；迁移顺序和兼容/回滚边界 |
| 逐表数据字典 | 一期已确认范围内每张表的用途、所属任务、字段名/含义、MySQL 类型/长度或精度、可空性、默认值、主键/唯一约束、索引列顺序与查询依据；枚举、外键或应用完整性校验、更新/删除行为；待定范围显式隔离 |
| 审阅用 DDL | 与数据字典一致的 MySQL 8.4 / InnoDB / utf8mb4 定义，明确排序规则、约束与索引；按领域标注拟落入的迁移及依赖，不在 B-01 一次创建所有后续阶段的表，不直接替代 Flyway 迁移 |
| 评审与差异记录 | 设计版本、代码基线提交/工作树、已有迁移版本、覆盖矩阵、评审者/日期、发现/严重程度/责任方/结论；已有实现与设计的差异及修复任务、验证证据；区分设计通过与实现通过 |

- [ ] 盘点架构第 6.3 节全部实体组及各阶段新增实体（含会话撤销、模型额度、评测配置、邮件任务），建立需求/表/任务/迁移覆盖矩阵；记录现有 B-01 等实现，不因已有代码而反向放宽业务约束。
- [ ] 完成 ER 图和逐表数据字典：B 阶段所需表细化到可直接编写迁移；其余已确认范围给出一致的详细设计和分期归属，未定字段集中列出决定项。名称/摘要等遵循既有规格，不将技术存储长度冒充新增产品字数限制。
- [ ] 明确约束和事务方案，逐项推演名单草稿不授权、旧 revision 不覆盖、工作稿/确认快照/候选分离、同幂等键异请求冲突、失效租约不提交、发布未知不重复及删除后迟到结果被拒绝；将方案和关键断言映射到 B-02…B-08、C-04、D-07 等拥有任务。
- [ ] 为权限过滤后的会议列表/包含检索、任务领取与续租、版本历史、来源追踪和文件清理列出查询形态、联合索引顺序、容量假设及后续 EXPLAIN/基准验证任务；全文近似匹配不能替代包含匹配，设计估算不能记为性能验收。
- [ ] 编写审阅 DDL，与 ER 图/字典逐项核对，在独立可丢弃的 MySQL 8.4 测试库验证建表及结构元数据，记录实际命令、版本、结果和证据；不得连接生产或复用运行任务的测试库。无可用测试库时保留该项待验证，不能以静态检查标记通过。
- [ ] 进行独立评审，按下列标准记录逐项结论；修复阻塞设计问题，评审已有实现差异，确定修复任务及受影响回归。设计通过后记录基线版本，提交 `docs: define and review database schema baseline`；实现差异在对应任务最终验收前闭环，不提前将 B-01 或阶段 B 标记为完成。

**验收标准：**

- [ ] 覆盖矩阵包含全部一期实体组和 B…E 迁移任务；B-01…B-08 所需表、字段、约束、索引无遗漏/待定项，后续待定项均有责任方、影响任务和补充评审条件。
- [ ] ER 图、数据字典和审阅 DDL 中的表/字段/关联一致；每个唯一约束有业务依据，每个逻辑关系有明确完整性与删除策略，MySQL 建表及元数据核对有实际通过证据。
- [ ] 上述并发、授权、快照、幂等、租约、发布和删除场景均有事务/约束方案和拥有任务的验证用例；外部副作用不能仅凭本地唯一键宣称幂等成功。
- [ ] 迁移分工与已有版本不冲突；记录扩展/迁移/收缩和回滚边界；已应用或共享脚本保持不可变；现有实现差异有明确修复与回归归属。
- [ ] 独立评审无未关闭的阻塞设计问题，评审记录能定位设计版本、证据和结论。B-00 通过代表设计基线可采用，现有实现差异闭环及真实集成/性能/部署资格仍由对应任务提供证据。

## Task B-01：基础迁移、事务和审计边界

**Files:** Create `backend/adapters/src/main/resources/db/migration/V001__foundation.sql`、`backend/adapters/src/main/java/ama/db/TransactionRunner.java`、`backend/core/src/main/java/ama/audit/AuditPort.java`、`backend/adapters/src/main/java/ama/audit/JdbcAuditAdapter.java`、`backend/integration-tests/src/test/java/ama/db/FoundationIT.java`。

**Interfaces:** Consumes B-00 已评审的基础表/审计设计、迁移规范及现有实现差异清单；Produces `TransactionRunner.required(Callable<T>): T`，`AuditPort.append(AuditEvent): void`；`AuditEvent` 定义于 `backend/contracts/src/main/java/ama/contract/AuditContracts.java`，含 actorId/action/resourceId/revision/result/traceId/time，不存正文与令牌。

- [ ] 核对 B-00 基线与本任务迁移、审计字段和事务实现；已有代码可复用，差异按评审结论修复。已应用或共享的 V001 保持不可变，修正迁移登记新版本并验证升级路径；本任务完成记录引用设计评审版本与差异闭环证据。
- [ ] 写 `rollbackDoesNotLeaveSuccessAudit`、`migrationAppliesOnce`；事务中写记录后抛错，断言 `assertEquals(0, committedRows); assertEquals(0, successfulAuditRows);`；两次迁移版本一致。
- [ ] 运行 `python3 tools/checks/run.py it FoundationIT`，确认目标事务行为失败。
- [ ] 实现 migration lock、UTF-8/UTC、事务执行和最小审计；使用真实 MySQL 而非 H2 替代锁/排序行为；迁移独立执行，不让两个应用启动时争改表。
- [ ] 重跑 IT；连接断开后无半提交，已存在数据库再次部署不重复建数据。
- [ ] 提交 `feat: establish transactional persistence and audit foundation`。

## Task B-02：统一身份、人员目录和对象级访问控制

**Files:** Create `backend/contracts/src/main/java/ama/contract/IdentityContracts.java`、`backend/core/src/main/java/ama/identity/IdentityPort.java`、`backend/core/src/main/java/ama/identity/DirectoryPort.java`、`backend/core/src/main/java/ama/access/MeetingAccess.java`、`backend/adapters/src/main/java/ama/identity/CompanyIdentityAdapter.java`、`backend/api/src/main/java/ama/api/SecurityConfiguration.java`、`backend/adapters/src/main/resources/db/migration/V002__identity_acl.sql`、`backend/integration-tests/src/test/java/ama/identity/IdentityAccessIT.java`。

**Interfaces:** Consumes A-02 的已确定协议；Produces `IdentityPort.resolveSession(String): Optional<Actor>`、`DirectoryPort.search(String query,String cursor,int limit): PeoplePage`、`MeetingAccess.require(Actor actor,String meetingId,Action action): void`。Actor 含稳定 enterpriseId、状态、角色；目录人员含 stableId/displayName/department/active。`GET /api/v1/session`、`GET /api/v1/directory/people`。

- [ ] 保留单机会话撤销及 CSRF/Origin、停用、同名人员验证；未完 `sessionRevocationWorksOnBothNodes` 归 DEP-HA，不在前期新增跨节点测试；普通管理员读取别人的会议 `assertEquals(403, reply.status());`；草稿名单加入的人仍 403；确认后才可读。
- [ ] 运行 `python3 tools/checks/run.py it IdentityAccessIT`，确认访问规则失败。
- [ ] 按 A-02 的公司协议实现 adapter、后端令牌与安全 Cookie、撤销和目录映射；测试身份仅在 test profile 注入，生产配置加载它必须失败。
- [ ] 重跑 IT；公司测试环境到位后另运行 `python3 tools/checks/run.py contract IdentityAccessIT`，保留真实登录、退出和停用证据。
- [ ] 提交 `feat: integrate enterprise identity and meeting authorization`；未提供协议时只交付端口及受控测试，实现/联调状态分别记录。

## Task B-03：会议草稿、版本锁和幂等请求

**Files:** Create `backend/contracts/src/main/java/ama/contract/MeetingContracts.java`、`backend/core/src/main/java/ama/meeting/MeetingService.java`、`backend/core/src/main/java/ama/meeting/RosterService.java`、`backend/adapters/src/main/java/ama/meeting/JdbcMeetingRepository.java`、`backend/api/src/main/java/ama/api/MeetingController.java`、`backend/adapters/src/main/resources/db/migration/V003__meeting_revisions.sql`、`contracts/openapi/meetings.yaml`、`backend/integration-tests/src/test/java/ama/meeting/MeetingRevisionIT.java`。

**Interfaces:** Consumes B-01/B-02；Produces `POST /api/v1/meetings {}`→`{id,revision,stage}`，`PUT /meetings/{id}/info {expectedRevision,name,category,summary,occurredOn,rosterDraftIds}`，`POST /meetings/{id}/roster-confirmations {expectedRevision}`。副作用请求携带 `Idempotency-Key`，保存成功返回新 revision；未知日期 null。

- [ ] 写 `sameKeyDifferentPayloadConflicts`、`staleRevisionPreservesCurrentValue`、`draftRosterDoesNotGrantAccess`；同键异体和旧 revision 均 `assertEquals(409, reply.status());`；空名称可建草稿且不自动补实际日期。
- [ ] 运行 `python3 tools/checks/run.py it MeetingRevisionIT`，确认预期冲突和授权未实现。
- [ ] 实现会议/信息/名单修订与有效 ACL 分离、唯一幂等记录和请求摘要；不得用 AI 候选替换人工字段，不新增名称产品最大长度。
- [ ] 重跑 IT，覆盖并发两请求、DB 回滚后重试、名单确认增删及停用人员拒绝授权。
- [ ] 提交 `feat: add revisioned meeting drafts and confirmed rosters`。

## Task B-04：统一文件契约与两种后端

本任务原共同契约、local、S3 三部分及已采纳成果全部保留。当前 B-04-Demo 只收口 local 与业务登记/API 所需缺口；B-04 的未完 S3 适配和验证单列 DEP-S3/F，不能把该后移部分标为完成，也不阻塞 B Demo。

**Files:** Create `backend/contracts/src/main/java/ama/contract/StorageContracts.java`、`backend/core/src/main/java/ama/storage/FileStoragePort.java`、`backend/core/src/main/java/ama/storage/FileRegistry.java`、`backend/adapters/src/main/java/ama/storage/StorageRouter.java`、`backend/adapters/src/main/java/ama/storage/S3FileStorage.java`、`backend/adapters/src/main/java/ama/storage/LocalFileClient.java`、`backend/file-service/pom.xml`、`backend/file-service/src/main/java/ama/file/LocalFileStore.java`、`backend/file-service/src/main/java/ama/file/InternalFileController.java`、`backend/api/src/main/java/ama/api/FileController.java`、`backend/adapters/src/main/resources/db/migration/V004__files_uploads.sql`、`backend/integration-tests/src/test/java/ama/storage/FileStorageContractIT.java`、`backend/integration-tests/src/test/java/ama/storage/LocalDurabilityIT.java`、`contracts/openapi/files.yaml`。

**Interfaces:** `FileStoragePort.put(StorageKey key,InputStream body,long size,String sha256): StoredBlob`、`stat(StorageKey): BlobStat`、`read(StorageKey,long offset,long length): InputStream`、`delete(StorageKey): void`、`beginUpload(UploadSpec): UploadToken`、`writePart(UploadToken,int part,InputStream,long size,String sha256): PartReceipt`、`complete(UploadToken,List<PartReceipt>,String sha256): StoredBlob`、`abort(UploadToken): void`、`list(String backendId,String namespace,String prefix,String cursor,int limit): BlobPage`。StorageKey 含 backendId/namespace/objectKey；StoredBlob 含 key/size/sha256/providerVersionId；BlobStat 含 size/sha256/providerVersionId；BlobPage 含 items/nextCursor；UploadSpec 含 backendId/namespace/objectKey/expectedSize；PartReceipt 含 part/size/sha256/providerToken；UploadToken 固定 backendId/sessionId。`FileRegistry.resolve(String fileId): FileRecord` 返回 fileId/key/size/sha256/state/locationRevision；调用前业务层鉴权，位置不回传浏览器。

**HTTP contract:** `POST /api/v1/meetings/{id}/uploads {purpose,name,size,sha256}`→uploadId/partSize；purpose 仅接受当前用户有权使用的已启用文件用途。`PUT /uploads/{id}/parts/{part}` 传二进制及 `X-Checksum-SHA256`；`POST /uploads/{id}/completion {parts:[{part,sha256}],sha256}`→fileId；`DELETE /uploads/{id}` 中止。providerToken 只保存在服务端，不信任客户端提交的位置或凭证；`GET /files/{id}/content` 经 MeetingAccess 校验，D-06 增加长流撤权检查。

- [ ] 写共同契约：同键同哈希重放、不同内容拒绝、字节范围正确、完成/中止幂等、默认后端改变不影响旧文件。`orphanIsNotReadableAsBusinessFile` 模拟文件写成功而 DB 提交失败，业务 GET 应 404；核心 `assertArrayEquals(expectedRange, bytes); assertEquals("original-backend", token.backendId());`。
- [ ] 运行 `python3 tools/checks/run.py it FileStorageContractIT`，只针对当前 local 路径的真实缺口确认行为失败；已有两个实现均保留，不人为重造失败。
- [ ] 实现文件登记、后端路由和上传状态，先评审提交 `feat: define file storage contract and registry`；不开放未实现后端为可用。
- [ ] 实现 local：同卷临时文件→哈希→文件同步→不覆盖原子发布→目录同步；内部认证、根目录/符号链接防护、挂载身份检查、受保护暂存。写 `LocalDurabilityIT` 注入同步失败和挂载丢失，确认不能返回成功；通过后提交 `feat: implement durable local file service`。
- [ ] **后移 DEP-S3/F（仅未完部分）**：复用已采纳 S3 adapter，补目标环境配置校验、流式读取、multipart 合法 part、校验及重复完成查询的适配/验证；ETag 不冒充 SHA-256；共同契约 S3 参数组在 F 验证。已有实现与通过记录不重做。
- [ ] 本地有变更时定向运行 FileStorageContractIT/LocalDurabilityIT 并报告实际数量；S3 差异和双后端回归归 F，断电/DRBD 资格仍在 F-02。既有证据按原范围复用。

## Task B-05：可靠任务、outbox 和模型调用额度

**Files:** Create `backend/contracts/src/main/java/ama/contract/TaskContracts.java`、`backend/core/src/main/java/ama/task/TaskQueue.java`、`backend/adapters/src/main/java/ama/task/JdbcTaskQueue.java`、`backend/adapters/src/main/java/ama/task/JdbcModelPermitPool.java`、`backend/worker/pom.xml`、`backend/worker/src/main/java/ama/worker/WorkerLoop.java`、`backend/adapters/src/main/resources/db/migration/V005__tasks_outbox.sql`、`backend/integration-tests/src/test/java/ama/task/TaskLeaseIT.java`。

**Interfaces:** `TaskQueue.enqueue(TaskSpec): String`、`claim(String pool,String workerId): Optional<TaskLease>`、`renew(TaskLease): boolean`、`complete(TaskLease,TaskResult): boolean`；TaskSpec 含 type/inputFingerprint/priority/cancelEpoch；TaskLease 含 taskId/attempt/leaseOwner/expiresAt。`ModelPermitPool.acquire(String model,Instant safeDeadline): Optional<Permit>`，定义在 `backend/core/src/main/java/ama/task/ModelPermitPool.java`。

- [ ] 写 `expiredAttemptCannotCommit`、两个 Worker 竞争、租约20秒/续租5秒、DB恢复后30秒回收和 cancelEpoch 拒绝提交；`assertFalse(staleCommit); assertEquals(1, successfulEffects);`；请求超时未到 safeDeadline 不释放全部额度。
- [ ] 运行 `python3 tools/checks/run.py it TaskLeaseIT`，验证竞争/重放测试确实失败。
- [ ] 实现 SKIP LOCKED 短事务、DB 时钟、独立续租、有限退避、池隔离、outbox 游标；外部未知进入 reconciling，不因自动重试重复副作用。
- [ ] 重跑真实 MySQL 并发 IT；任务/事件与业务事务原子提交；测试线程暂停、连接失效和恢复后的迟到完成。
- [ ] 提交 `feat: add durable tasks leases and model admission control`。

## Task B-06：转写文件导入与确认快照

**Files:** Create `backend/contracts/src/main/java/ama/contract/TranscriptContracts.java`、`backend/core/src/main/java/ama/transcript/TranscriptService.java`、`backend/adapters/src/main/java/ama/transcript/TextTranscriptParser.java`、`backend/api/src/main/java/ama/api/TranscriptController.java`、`backend/adapters/src/main/resources/db/migration/V006__transcripts.sql`、`contracts/openapi/transcripts.yaml`、`backend/integration-tests/src/test/java/ama/transcript/TranscriptConfirmationIT.java`。

**Interfaces:** Consumes FileRegistry/TaskQueue/MeetingAccess；`POST /meetings/{id}/processing {fileIds,materialSnapshotId}`→taskId；材料为空时 materialSnapshotId 为 null，输入指纹按规范化空集合计算，D-01 提供非空快照。`PUT /meetings/{id}/transcript {expectedRevision,segments}`→revision；`POST /meetings/{id}/transcript-confirmations {expectedRevision}`→confirmationId。Segment 含 stableId/text/speakerId(可空)/sourceFileId/startMs/endMs(可空)。

- [ ] 写无音频纯文本、零待处理纠错、未保存/旧版确认；`assertTrue(segment.startMs() == null); assertEquals(409, unconfirmedGeneration.status());`；不能补造时戳/说话人。
- [ ] 运行 `python3 tools/checks/run.py it TranscriptConfirmationIT`，确认业务规则失败。
- [ ] 实现批准的转写格式解析、稳定片段、工作稿/确认稿分离；材料允许空、名称/分类未补不阻断处理；能力清单明确未支持的格式，其他音视频在 C-01 实现。
- [ ] 重跑 IT：修改已确认稿使确认失效，未确认版本不能被任务读取；原稿保留，重复确认不重复发任务。
- [ ] 提交 `feat: import and confirm revisioned transcripts`。

## Task B-07：基于确认稿生成首版纪要

**Files:** Create `backend/contracts/src/main/java/ama/contract/MinutesContracts.java`、`backend/core/src/main/java/ama/minutes/MinutesService.java`、`backend/core/src/main/java/ama/model/MinutesModelPort.java`、`backend/adapters/src/main/java/ama/model/CompanyLlmAdapter.java`、`backend/worker/src/main/java/ama/worker/MinutesJobHandler.java`、`backend/adapters/src/main/resources/db/migration/V007__minutes_evidence.sql`、`contracts/openapi/minutes.yaml`、`backend/integration-tests/src/test/java/ama/minutes/MinutesGenerationIT.java`。

**Interfaces:** `MinutesModelPort.generate(GenerationInput): GenerationOutput`；Input 含确认稿/材料/模板/排除/模型配置版本和 sourceIds，Output 含七类 elements、body、title、sourceRefs。`POST /meetings/{id}/minutes-generations {transcriptConfirmationId,expectedRevision}`→taskId；结果为 candidateId/inputFingerprint；`POST /minutes-candidates/{id}/adoption {expectedRevision}` 才更新正文。

- [ ] 写未确认稿拒绝、坏 schema/越界引用拒绝、编辑后旧候选不覆盖；`assertEquals(409, adoption.status()); assertEquals("人工稿", currentBody);`；模拟含数字的源文，生成不能引用不存在 sourceId。
- [ ] 运行 `python3 tools/checks/run.py it MinutesGenerationIT`，确认依据/版本约束失败。
- [ ] 实现受控 LLM 适配、schema/引用校验、输入指纹、候选与有效稿分离、错误分类；基本生成可用但完整敏感/局部编辑在 D-03/04 补齐，不提前标全功能。
- [ ] 重跑 IT；真实测试模型到位后执行 `python3 tools/checks/run.py contract MinutesGenerationIT`，保留输入版本与脱敏结果评审，模拟质量不等于真实质量。
- [ ] 提交 `feat: generate traceable minutes candidates from confirmed transcripts`。

## Task B-08：最终快照、幂等发布与结果查回

**Files:** Create `backend/contracts/src/main/java/ama/contract/PublicationContracts.java`、`backend/core/src/main/java/ama/publication/FinalConfirmationService.java`、`backend/core/src/main/java/ama/publication/PublicationService.java`、`backend/core/src/main/java/ama/knowledge/KnowledgePublishPort.java`、`backend/adapters/src/main/java/ama/knowledge/CompanyKnowledgePublishAdapter.java`、`backend/adapters/src/main/resources/db/migration/V008__publications.sql`、`contracts/openapi/publications.yaml`、`backend/integration-tests/src/test/java/ama/publication/PublicationReconcileIT.java`。

**Interfaces:** `KnowledgePublishPort.publish(PublishRequest): PublishOutcome`、`findByBusinessKey(String): PublishOutcome`；Outcome.status 限 SUCCESS/FAILED/UNKNOWN，含 externalDocumentId/url。`POST /meetings/{id}/final-confirmations {expectedRevision}`→confirmationId；`POST /meetings/{id}/publications {confirmationId,libraryId,folderId}`→publicationId；`GET /publications/{id}`→state。外部业务键固定 publicationId＋confirmationId。

- [ ] 写缺名称/分类、待采用候选和陈旧确认拒绝；外部成功后丢响应，双并发重试的关键断言 `assertEquals(1, externalCreateCount); assertEquals(firstDocumentId, reconciledDocumentId);`。
- [ ] 运行 `python3 tools/checks/run.py it PublicationReconcileIT`，确认未知态和重复保护失败。
- [ ] 实现一致版本冻结、发布前目录/权限重验、外部幂等/查回、未知态锁定、成功后只读；公司接口无可靠幂等/查回时阻断真发布，不伪造其能力。
- [ ] 重跑 IT；知识库测试接口具备契约后运行 `python3 tools/checks/run.py contract PublicationReconcileIT`，记录真实文档 ID 和查回证据，不向生产发布测试内容。
- [ ] 提交 `feat: publish confirmed snapshots with reconciliation`。

## Task B-09：首个可操作的前端闭环

当前执行拆为 R2 的 DEMO-01～05：按用户操作片段同步装配、原型与 API，优先复用已存在的 `web/src/features/meetings/`、`import/`、`transcript/`、`minutes/` 组件。下列拟建文件名属于原始任务设计，不是另建重复页面的指令。表现层可作原型复刻必需的最小调整；既有领域/权限/版本/恢复协议与可用功能保持。

**Files:** Create `web/src/features/capture/ImportMeetingDialog.vue`、`web/src/features/meeting/MeetingInfoDrawer.vue`、`web/src/features/transcript/TranscriptPage.vue`、`web/src/features/minutes/MinutesPage.vue`、`web/src/features/publication/PublicationPage.vue`、`web/tests/e2e/first-flow.spec.ts`、`web/tests/e2e/fixtures.ts`。Modify `web/src/App.vue`、`web/src/shared/api/generated.ts`。

**Interfaces:** Consumes B-02…B-08 OpenAPI；测试 fixtures 提供 owner/member/outsider/admin 四种独立会话及脱敏转写文件，不把测试登录入口编入生产。

- [ ] 写 `importConfirmGeneratePublish`，按真实页面点击与等待后断言 `await expect(page.getByTestId('publication-state')).toHaveText('已发布');`；刷新保持结果，参与者没有编辑按钮且直接写 API 为403。
- [ ] 运行 `python3 tools/checks/run.py e2e first-flow.spec.ts`，确认未实现步骤失败。
- [ ] 实现各页最小完整动作及保存/失败/冲突状态，使用原型视觉；实际发生日期未知显示空；异步任务经 taskId 查询/事件恢复。
- [ ] 在本地 MySQL/local/单机跑完整浏览器 E2E，保留真实/受控平台标识，验证同键重试、失败输入、刷新只读及原型页面截图对照，执行 typecheck/build。S3 E2E 未完部分归 DEP-S3/F。
- [ ] 提交 `feat: deliver first end-to-end meeting minutes flow`，提交 B 阶段演示与缺口报告。

## 阶段验收与移交

**M1-Demo 首次演示**按调整计划第 4.2/4.3 节验收：真实页面闭环、逐页原型、基本权限/版本/幂等/未知/持久化与常规重开、受影响验证；结果真实、无重要缺陷、受控提供方清楚标识。精确事务阶段/预算/锁序/完整重启/crash 等剩余证明归 B-QUAL，不统一压在首次演示之前；相关高风险路径发生修改或发现实际缺陷时提前处理。Demo 通过时 **B 仍 INCOMPLETE**。

以下为 **M1-B 正式本地业务验收**。所有适用义务及最晚关卡见 [B 摘要](../../acceptance/b/summary.md#current-open-obligations-r2)，不能仅靠受控演示关闭正式验收：

- [ ] B-00 交付物齐全并通过独立评审；B 阶段迁移/实现与评审基线一致，已开展任务的差异及受影响回归全部闭环；`docs/acceptance/b/summary.md` 引用设计版本和评审证据。
- [ ] 本地 MySQL/local/单机无 S3 服务/凭证、第二数据库或双活依赖即可完成真实页面闭环，数据跨进程重启保留；Demo 页面复刻有对照证据。双后端专属验证未完部分归 F，单列未完成。
- [ ] 未确认/越权/旧版本不能生成或发布；结果未知查回不会重复文档；任务可恢复。
- [ ] B-QUAL 关闭全部适用本地核心断言，包括精确并发/写后回滚、读取前累计预算/锁序/同 holder、维护共存/饱和、关键 crash 与各组件重启；最终适用回归与原下界上的全分支审查完成。已采纳证据按有效范围复用。
- [ ] `docs/acceptance/b/summary.md` 区分可演示闭环与公司真实联调；缺 SSO/模型/知识库任何一项则相应真联调未通过，不能以 mock 交付替代。
- [ ] 本地 B 达标后停止交接，不自动启动 C/D/E/F；真实模型/企业能力按 B-02/07/08 独立联调，最晚相应生产功能交付前通过，不能把三类部署后移扩大为全部真实能力后移。
