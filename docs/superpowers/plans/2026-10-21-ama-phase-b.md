# B：平台基础与首个业务闭环 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用真实持久化完成“转写文件导入→人工确认→纪要→测试知识库发布”，并建立后续功能共用的权限、文件、任务和版本边界。

**Architecture:** 业务状态由 MySQL 事务维护；local/S3 遵循统一端口；任务与外部发布使用幂等、租约和对账。首个闭环采用无音频的转写输入，不冒充已实现实时音频。

**Tech Stack:** 总计划技术栈；Spring Security/JDBC/Flyway、AWS Java SDK v2、JUnit、Playwright。

**Spec:** [架构](../specs/2026-10-21-ama-architecture-design.md) 第 4、6、8—10、12—13 节；[总计划](2026-10-21-ama-delivery-plan.md)。

## Global Constraints

- 继承总计划；未确认名单不授权，平台管理员不默认有正文权限。
- local/S3 覆盖所有受管文件；文件和目录持久化后再登记 DB、再返回成功；不能当跨资源事务。
- 任务租约初始 20 秒、每 5 秒续租、每 5 秒扫描；DB 可用时 30 秒内可重新领取。
- 业务版本和输入指纹决定能否写回；发布成功只读，未知外部结果先对账。

## Review Focus

1. 跨节点会话和离职撤销：B-02 `sessionRevocationWorksOnBothNodes`。
2. 同幂等键不同请求及两窗口编辑：B-03 `sameKeyDifferentPayloadConflicts`。
3. 存储成功而事务失败：B-04 `orphanIsNotReadableAsBusinessFile`。
4. 旧 Worker 在租约到期后返回：B-05 `expiredAttemptCannotCommit`。
5. 生成或发布超时后迟到成功：B-07/B-08 分别检查候选和外部文档唯一性。

## Task B-01：基础迁移、事务和审计边界

**Files:** Create `backend/adapters/src/main/resources/db/migration/V001__foundation.sql`、`backend/adapters/src/main/java/ama/db/TransactionRunner.java`、`backend/core/src/main/java/ama/audit/AuditPort.java`、`backend/adapters/src/main/java/ama/audit/JdbcAuditAdapter.java`、`backend/integration-tests/src/test/java/ama/db/FoundationIT.java`。

**Interfaces:** Produces `TransactionRunner.required(Callable<T>): T`，`AuditPort.append(AuditEvent): void`；`AuditEvent` 定义于 `backend/contracts/src/main/java/ama/contract/AuditContracts.java`，含 actorId/action/resourceId/revision/result/traceId/time，不存正文与令牌。

- [ ] 写 `rollbackDoesNotLeaveSuccessAudit`、`migrationAppliesOnce`；事务中写记录后抛错，断言 `assertEquals(0, committedRows); assertEquals(0, successfulAuditRows);`；两次迁移版本一致。
- [ ] 运行 `python3 tools/checks/run.py it FoundationIT`，确认目标事务行为失败。
- [ ] 实现 migration lock、UTF-8/UTC、事务执行和最小审计；使用真实 MySQL 而非 H2 替代锁/排序行为；迁移独立执行，不让两个应用启动时争改表。
- [ ] 重跑 IT；连接断开后无半提交，已存在数据库再次部署不重复建数据。
- [ ] 提交 `feat: establish transactional persistence and audit foundation`。

## Task B-02：统一身份、人员目录和对象级访问控制

**Files:** Create `backend/contracts/src/main/java/ama/contract/IdentityContracts.java`、`backend/core/src/main/java/ama/identity/IdentityPort.java`、`backend/core/src/main/java/ama/identity/DirectoryPort.java`、`backend/core/src/main/java/ama/access/MeetingAccess.java`、`backend/adapters/src/main/java/ama/identity/CompanyIdentityAdapter.java`、`backend/api/src/main/java/ama/api/SecurityConfiguration.java`、`backend/adapters/src/main/resources/db/migration/V002__identity_acl.sql`、`backend/integration-tests/src/test/java/ama/identity/IdentityAccessIT.java`。

**Interfaces:** Consumes A-02 的已确定协议；Produces `IdentityPort.resolveSession(String): Optional<Actor>`、`DirectoryPort.search(String query,String cursor,int limit): PeoplePage`、`MeetingAccess.require(Actor actor,String meetingId,Action action): void`。Actor 含稳定 enterpriseId、状态、角色；目录人员含 stableId/displayName/department/active。`GET /api/v1/session`、`GET /api/v1/directory/people`。

- [ ] 写 `sessionRevocationWorksOnBothNodes` 及 CSRF/Origin、停用、同名人员测试；普通管理员读取别人的会议 `assertEquals(403, reply.status());`；草稿名单加入的人仍 403；确认后才可读。
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

本任务分三个可独立评审的小交付；最后共同契约通过才算 B-04 完成。

**Files:** Create `backend/contracts/src/main/java/ama/contract/StorageContracts.java`、`backend/core/src/main/java/ama/storage/FileStoragePort.java`、`backend/core/src/main/java/ama/storage/FileRegistry.java`、`backend/adapters/src/main/java/ama/storage/StorageRouter.java`、`backend/adapters/src/main/java/ama/storage/S3FileStorage.java`、`backend/adapters/src/main/java/ama/storage/LocalFileClient.java`、`backend/file-service/pom.xml`、`backend/file-service/src/main/java/ama/file/LocalFileStore.java`、`backend/file-service/src/main/java/ama/file/InternalFileController.java`、`backend/api/src/main/java/ama/api/FileController.java`、`backend/adapters/src/main/resources/db/migration/V004__files_uploads.sql`、`backend/integration-tests/src/test/java/ama/storage/FileStorageContractIT.java`、`backend/integration-tests/src/test/java/ama/storage/LocalDurabilityIT.java`、`contracts/openapi/files.yaml`。

**Interfaces:** `FileStoragePort.put(StorageKey key,InputStream body,long size,String sha256): StoredBlob`、`stat(StorageKey): BlobStat`、`read(StorageKey,long offset,long length): InputStream`、`delete(StorageKey): void`、`beginUpload(UploadSpec): UploadToken`、`writePart(UploadToken,int part,InputStream,long size,String sha256): PartReceipt`、`complete(UploadToken,List<PartReceipt>,String sha256): StoredBlob`、`abort(UploadToken): void`、`list(String backendId,String namespace,String prefix,String cursor,int limit): BlobPage`。StorageKey 含 backendId/namespace/objectKey；StoredBlob 含 key/size/sha256/providerVersionId；BlobStat 含 size/sha256/providerVersionId；BlobPage 含 items/nextCursor；UploadSpec 含 backendId/namespace/objectKey/expectedSize；PartReceipt 含 part/size/sha256/providerToken；UploadToken 固定 backendId/sessionId。`FileRegistry.resolve(String fileId): FileRecord` 返回 fileId/key/size/sha256/state/locationRevision；调用前业务层鉴权，位置不回传浏览器。

**HTTP contract:** `POST /api/v1/meetings/{id}/uploads {purpose,name,size,sha256}`→uploadId/partSize；purpose 仅接受当前用户有权使用的已启用文件用途。`PUT /uploads/{id}/parts/{part}` 传二进制及 `X-Checksum-SHA256`；`POST /uploads/{id}/completion {parts:[{part,sha256}],sha256}`→fileId；`DELETE /uploads/{id}` 中止。providerToken 只保存在服务端，不信任客户端提交的位置或凭证；`GET /files/{id}/content` 经 MeetingAccess 校验，D-06 增加长流撤权检查。

- [ ] 写共同契约：同键同哈希重放、不同内容拒绝、字节范围正确、完成/中止幂等、默认后端改变不影响旧文件。`orphanIsNotReadableAsBusinessFile` 模拟文件写成功而 DB 提交失败，业务 GET 应 404；核心 `assertArrayEquals(expectedRange, bytes); assertEquals("original-backend", token.backendId());`。
- [ ] 运行 `python3 tools/checks/run.py it FileStorageContractIT`，确认两个实现缺失/行为失败。
- [ ] 实现文件登记、后端路由和上传状态，先评审提交 `feat: define file storage contract and registry`；不开放未实现后端为可用。
- [ ] 实现 local：同卷临时文件→哈希→文件同步→不覆盖原子发布→目录同步；内部认证、根目录/符号链接防护、挂载身份检查、受保护暂存。写 `LocalDurabilityIT` 注入同步失败和挂载丢失，确认不能返回成功；通过后提交 `feat: implement durable local file service`。
- [ ] 实现 S3：配置校验、流式读取、multipart 合法 part、校验及重复完成查询；ETag 不冒充 SHA-256；同一共同契约运行 local/s3 两个参数组，通过后提交 `feat: implement S3 file storage adapter`。
- [ ] 重跑两个 IT，报告各后端实际执行数；断电/DRBD 资格留 F-02，普通文件测试不能代替断电持久化证据。

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

**Files:** Create `web/src/features/capture/ImportMeetingDialog.vue`、`web/src/features/meeting/MeetingInfoDrawer.vue`、`web/src/features/transcript/TranscriptPage.vue`、`web/src/features/minutes/MinutesPage.vue`、`web/src/features/publication/PublicationPage.vue`、`web/tests/e2e/first-flow.spec.ts`、`web/tests/e2e/fixtures.ts`。Modify `web/src/App.vue`、`web/src/shared/api/generated.ts`。

**Interfaces:** Consumes B-02…B-08 OpenAPI；测试 fixtures 提供 owner/member/outsider/admin 四种独立会话及脱敏转写文件，不把测试登录入口编入生产。

- [ ] 写 `importConfirmGeneratePublish`，按真实页面点击与等待后断言 `await expect(page.getByTestId('publication-state')).toHaveText('已发布');`；刷新保持结果，参与者没有编辑按钮且直接写 API 为403。
- [ ] 运行 `python3 tools/checks/run.py e2e first-flow.spec.ts`，确认未实现步骤失败。
- [ ] 实现各页最小完整动作及保存/失败/冲突状态，使用原型视觉；实际发生日期未知显示空；异步任务经 taskId 查询/事件恢复。
- [ ] local/s3 分别跑 E2E，保留当前真实/模拟平台标识；加入同键重试、失败保留输入和刷新后只读断言，执行 typecheck/build。
- [ ] 提交 `feat: deliver first end-to-end meeting minutes flow`，提交 B 阶段演示与缺口报告。

## 阶段验收与移交

- [ ] 双文件后端用同一套测试，local 无 S3 服务/凭证仍能完成闭环；数据跨进程重启保留。
- [ ] 未确认/越权/旧版本不能生成或发布；结果未知查回不会重复文档；任务可恢复。
- [ ] `docs/acceptance/b/summary.md` 区分可演示闭环与公司真实联调；缺 SSO/模型/知识库任何一项则相应真联调未通过，不能以 mock 交付替代。
