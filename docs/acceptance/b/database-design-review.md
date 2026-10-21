# B-00 数据库设计审阅与差异记录

日期：2026-10-21；当前设计版本 **B00-1.1-review**。首版独立B-00 reviewer已完整评审c73a765，结论6 Important、0 Critical、0 Minor，**基线暂不可采用**。作者按已确认六项修正，本版**待scoped re-review，仍不得标设计通过**。本记录只支持设计结构核对和评审，不代表B-01/B-02/B-05或阶段B验收、性能、真实接口、部署批准。

## 输入及精确基线

设计工作树 `/home/tanren/ama/.worktrees/phase-b-database-design`，branch `codex/phase-b-database-design`；BASE `e8c88d2aaed1a19b0d0202ddce9cb51ee01d84ee`。B-02/B-05暂停输入的HEAD均 `344597250accf16f8a1e56713e9aec6045ba28e5`；其未提交SQL/Java是输入，不是已评审实现。B-04输入HEAD `b8752705e5a03da97c1877868fbc58ecc90251bc`，仅DB无关路径继续，不以此设计收尾其资格。

读取架构6.3全部实体组、6/8—10/12/14约束、需求对应矩阵、A-02产品决定台账和integration-catalog、测试manifest、最新批准product-supplement、B..E task-contracts摘录、database-existing-inputs及B02/B05暂停报告、V001/V002/V005、B01 TransactionRunner/JdbcAuditAdapter、B05 JdbcTaskQueue/JdbcModelPermitPool草案。冻结版本与原始SHA详见 [`migration-register.md`](../../database/migration-register.md)。原计划占位V003/V004留空，修复从V006开始，不改原SQL。

## 覆盖矩阵：要求→表→任务/迁移→拥有测试

|规则/来源实体组|目标表（全字段见字典）|拥有任务/计划迁移|必须由领域任务验证的断言|
|---|---|---|---|
|架构6.3身份、部门、role、跨节点撤销；CAP-12|enterprise_identity、department、role_assignment、identity_session|B-02/V007|sessionRevocationWorksOnBothNodes；停用再启用旧session仍拒绝；同名不同ID、大小写/尾空格精确|
|CAP-11/12/13/14、会议/信息/名单|meeting、meeting_info_revision、roster_revision、roster_member、meeting_access_grant、info_suggestion|B-03/V008、D-02/V018|draftRosterNeverGrants；confirmedRosterRevokesAtomically；adminCannotReadBody；dateNullNotCreatedDate|
|双窗口版本/请求幂等|idempotency_record、meeting.revision/task_subject|B-03/V008、B-05/V010|sameKeyDifferentPayloadConflicts；twoWindowsCannotOverwrite；rollbackLeavesNoIdempotentSuccess|
|CAP-01/02/03/06、固定local/S3及孤儿|storage_backend、file_object、file_reference、upload_session、upload_part、file_cleanup|B-04/V009|orphanIsNotReadableAsBusinessFile；completeAfterDeletionRejected；backendChangeKeepsHistory；concurrentReferencePreventsCleanup|
|架构8 task/task_attempt/model额度|task_subject、durable_task、task_attempt、external_operation、model_capacity、model_reservation|B-05/V010|expiredAttemptCannotCommit；cancelOnlyAndVersionOnlyFence；crashAttemptCap；independentHeartbeat；quotaNotReleasedAtTimeout；deadlineMicrosecondNormalization|
|架构8 outbox提交游标|task_outbox_sequence、task_outbox、outbox_consumer|B-05/V010、C-06/V016|delayedLowerCommitNeverSkipped；rollbackConsumesNoSequence；consumerReplayIdempotent；lockedCandidateDoesNotBlockOthers|
|CAP-02/06，原始/工作/确认转写实体|input_batch、input_batch_file、transcript_revision、transcript_segment、transcript_confirmation、speaker基础|B-06/V011|unconfirmedTranscriptCannotGenerate；pureTextHasNoInventedTime；mixedBatchRejected；confirmationAndEnqueueAtomic|
|MIN-01/02/05/06/07/09/10/11|generation_input、minutes_revision、element_set、minutes_element、action_item、generation_candidate|B-07/V012、D-03/D-04|lateGenerationCannotReplaceManualEdit；candidatePendingBlocksFinal；elementAdoptionDoesNotOverwriteBody；restorationCreatesRevision|
|MIN-03/04/08/12|template_revision、exclusion_revision、sensitive_exclusion、source_locator、evidence_link|B-07/V012、D-03/D-04|exclusionNeverDeletesSourceOrChangesAcl；templateOwnerOnly；unknownLocatorRejected；unsupportedEvidenceNotSufficient|
|MIN-13/14、PUB-01..08|final_confirmation、publication、publication_attempt、external_document_mapping|B-08/V013|staleConfirmationRejected；successReadOnlyForever；timeoutLateSuccessOneExternalDocument；beforeExternalMarkerActuallyCommitted；notFoundWhileOriginalInFlightNeverResends|
|CAP-02、模型源时间轴|media_source、media_source_span|C-01/C-02/V014|orderedMultiSourceSampleMapping；noSilentClipLoss；knownCapabilitiesOnly|
|转写纠错/说话人/edit_operation|speaker、correction、edit_operation|C-03/V015（speaker V011）|zeroPendingRequired；undoCreatesRevision；speakerNameNeverGrants；fullVersionSearch|
|CAP-08/09/10，录音/分片/字幕|recording_session、recording_part、recording_chunk、subtitle_revision|C-04/C-06/V016|durableAckAfterFileAndDb；sameSeqDifferentHashRejected；oldWriterEpochRejected；finishWithGapNotAvailable；subtitleReconnectCursor|
|CAP-04/05/07、材料实体组|material_reference、material_revision、material_snapshot、material_snapshot_item、parsed_document、parsed_unit|D-01/V017|oneInvalidReferenceRollsBackBatch；pendingMaterialNotApplied；fixedExternalVersion；ocrUnavailableExplicit|
|PUB-06/07、外部治理事件|publication_governance、external_event_receipt|D-05/V020|localBlockBeforeExternalWithdraw；duplicateAndOutOfOrderEventsSafe；authorityUnknownFailsClosed|
|MIN-15、回看/导出|export_job、file_reference|D-06/V021|downloadRechecksCurrentAclAndState；publishedSnapshotFixed；mdPdfDocxSavedVersion|
|删除与历史保留|meeting_deletion、file_cleanup、meeting.cancel_epoch|D-07/V022|publishedOrUnknownCannotDelete；lateCompletionCannotRevive；storageDeleteFailureNotComplete；historyNeverAutoPurged|
|个人topic/meeting_topic|topic、meeting_topic|D-08/V023|onlyOwnPublishedMeetings；topicDoesNotGrant；onlyEmptyTopicDeletable|
|反馈候选/审核/语料|corpus_feedback、feedback_segment、corpus_revision|E-01/V024|reviewDoesNotMutateConfirmation；reviewRoleNotBodyRead；unapprovedCorpusNotReusable|
|声纹授权/审核/撤销|voiceprint_consent、voiceprint_sample|E-02/V025|noConsentNoRegistration；revokeDisablesMatching；encryptedFeatures；candidateNeverGrants|
|评测与配置|evaluation_dataset、evaluation_run、configuration_revision、configuration_release|E-03/V026|fixedVersionsAndRealMetrics；independentReviewer；whitelistedConfigOnly；rollbackNewReleaseRevision|
|邮件受控链接|mail_job、mail_recipient、external_operation|E-04/V027|onlyConfirmedStillAuthorizedEnterpriseRecipients；savedVersionLinkOnly；unknownQueriesBeforeRetry|
|事务/审计最小保留|audit_event|B-01/V006及所有领域|sameDataSourceRollbackIncludesAudit；noBodiesTokensInAudit；deleteRetainsMinimalEventOnly|

B-09/C-05/C-07消费上述结构不新增独立实体；C-02复用C-01/B-06；D-03/D-04消费B-07完整基础，V019预留仅必要扩展。架构全部十组实体与额外会话撤销、额度、评测配置、邮件均有表、字段、约束、索引和 owning task；没有把名称清单当数据字典。

## 独立评审首轮修正（R1—R6）

FIX_BASE `c73a7656fc1c5c2cb07d8b94ead68122059b549f`。评审原报告由协调者保存为`task-B-00-review.md`。六项对照原需求逐段核实成立；本次只改直接相关设计/声明/字典/DDL/ER/registry/证据，无产品代码或正式Flyway SQL。以下“已修正”是作者修复结论，独立复审尚未签署。

|独立发现/级别|1.1修正及精确实体|分期/所属验收用例|作者/独立状态|
|---|---|---|---|
|B00-R1 Important：列表字段/共同过滤错误|新增meeting_search_projection、meeting_person_search；编号明确完整持久meeting.id；q=名称OR编号OR当前CONFIRMED名单姓名，as_ci英文大小写不敏感且字面转义；共同F包含权限、治理、scope、q、stage、category、topic/UNASSIGNED、上海日期，默认updated_at/id倒序；list/total/stageCounts/topicGroups共用|B-03/V008、D-02、D-08；listNameNumberConfirmedPersonContains、listEnglishCaseInsensitive、draftAndOldRosterNotSearchable、allViewsUseSameFilters、unassignedAndCategoryNullRules|作者已修正；待复审|
|B00-R2 Important：个人模板/专题并发重名缺协议|新增minutes_template当前根；topic/模板完整NFC＋首尾空白规范名LONGTEXT binary及非唯一SHA桶；先锁owner identity，再候选完整碰撞复核＋CAS；历史template_revision名不占当前空间|B-07/V012、D-04、D-08/V023；concurrentSameNameCreateOneWins、renameConflictRollsBack、differentOwnersSameNameAllowed、longNameNoTruncation、forcedHashCollisionComparedInFull、historicalTemplateNameDoesNotReserve|作者已修正；待复审|
|B00-R3 Important：专题说明漏存储|topic.description LONGTEXT NULL，未填写/空串归NULL；创建/修改name＋description同事务/expectedRevision，说明单独改亦CAS|D-08/V023；topicDescriptionRoundTrips、descriptionConflictKeepsOriginal、nullDescriptionIsEmptyState|作者已修正；待复审|
|B00-R4 Important：同确认发布失败重试与单task操作唯一性冲突|external_operation是稳定逻辑键/subject，追加external_execution唯一(op,no)/task；当前执行CAS＋跨执行永不清零reconcile_epoch；FAILED且明确零效果证据才原确认新task/PREPARED→真commit marker；publication_attempt/model_reservation追踪具体执行；旧执行保留、UNKNOWN不得重发；纯目录更正使用publication CAS及新执行完整hash，逻辑hash/业务键/确认不变|B-05/V010、B-08/V013；zeroEffectFailureRetriesSameConfirmationSuccessfully、correctRejectedFolderWithSameConfirmation、concurrentRetryOneExecution、oldExecutionTokenCannotCommit、unknownNeverCreatesRetryExecution、lateContradictorySuccessRequiresReconcile；平台稳定键失败重试/同键变参能力另实证|作者已修正；待复审|
|B00-R5 Important：转写处理材料/配置输入快照链缺失|新增transcription_input(batch/sourceRevision/materialMode/snapshot/parser/model/prompt/configManifest/schema/version/epoch/fingerprint)；task typed输入和transcript_revision.processing_input_id/producer_task_id双向溯源；工作稿/恢复继承原输入|B-06/V011、C-02/C-03、D-01/V017补材料FK；reenhancementPinsAppliedMaterialAndConfig、lateOldInputNeverOverwrites、historyAndRestoreKeepOriginalInput、emptyMaterialHasCanonicalFingerprint|作者已修正；待复审|
|B00-R6 Important：片段多选标记缺存储|新增transcript_segment_mark复合PK(revision,segment,mark)，四值CHECK与片段FK；改标记产生新稿，排序集合入confirmation.content_hash及generation/transcription输入fingerprint；清当前转写/最终确认重新确认|B-06/V011、C-03；segmentMarksMultiSelectPersistAndRoundTrip、invalidOrDuplicateMarkRejected、changedMarksInvalidateConfirmation、marksIncludedInCanonicalHashes、publishedMarksReadOnly|作者已修正；待复审|

R1检索投影不是授权源，R2摘要不是唯一键也不替代完整名称，R4逻辑操作不是单一执行，R5处理快照不是纪要generation_input，R6片段标记不是minutes_element.category。SQL结构成功不能替代上述拥有任务的事务/并发/产品行为测试。原实现差异DIFF-01…13仍由所属任务闭环。

## 作者自审发现与实施差异（首版保留，1.1协议优先）

严重度：P0安全/数据不可逆；P1设计/核心实现阻塞；P2可后续工程优化。此处“设计已处理”仅表示本文给出可审阅方案，独立评审仍待进行；“实施待修复”必须在拥有任务最终验收前闭环。

|编号/严重度|输入发现|设计处理/结论|实施责任、迁移与回归|
|---|---|---|---|
|DIFF-01 P1|V001 actor/resource等用ai_ci比较，trace索引缺稳定tie-break；未明确时间微秒归一|目标binary标识，审计仍同事务、不作事件游标；设计已处理|B-01/V006，冲突数据预检、FoundationIT精确比较/微秒回归|
|DIFF-02 P0|V002 meeting_acl nullable confirmed_at同表可能混草稿/有效授权且无meeting FK|新meeting_access_grant仅有效READ、独立roster；旧ACL停读，确认来源不能猜测；设计已处理|B-02+B-03/V007/008，draftRosterNeverGrants、撤权、admin拒绝；旧SQL冻结|
|DIFF-03 P1|V002无session generation，停用再启用可能复活旧session；旧utf8mb4_bin有PAD语义|共享实时resolver＋generation单调增长＋0900_bin；设计已处理|B-02/V007，双节点退出/停用/重启与ID尾空格测试|
|DIFF-04 P0|V005无调用前持久external marker；旧进程发出后崩溃可能重复效果|beginExternal实际commit后网络，RECONCILING独立owner/epoch；NOT_FOUND不证明可重发；设计已处理|B-05/V010＋B-08/V013，ambient REQUIRED嵌套拒绝、发出前后kill、迟到成功/查回竞态|
|DIFF-05 P1|V005 subject→task与领域先锁meeting路径可能死锁；claim JOIN可能反序|统一idempotency/identity→subject→domain→session/file→task/op→audit→sequence末尾；permit独立事务；设计已处理|B-05及B03/B04/B08/C04/D07，真实持锁跳过、编辑/删除/完成并发、死锁只重试纯DB事务|
|DIFF-06 P1|V005缺producer dedupe、task_attempt、max_attempts、typed refs，崩溃回收可无限|业务入队键＋全输入比较、显式重试上限含crash、typed不可变快照；设计已处理|B-05/V010，sameProducerDifferentInput、expiredAttemptCannotCommit、crash cap|
|DIFF-07 P1|V005单例序列方向合理但无consumer checkpoint/lease；域锁后序列规则未全覆盖|所有producer最后sequence，独立consumer fence，连续ACK前缀；设计已处理|B-05/V010、C06；延迟低号commit、rollback、重复投递、全局争用负载|
|DIFF-08 P1|V005 safe_deadline NOT NULL，无平台保证可能虚构释放；Instant纳秒不一致|NULL可保守长期占额；实际平台cutoff才回收；config/enable/settled/operation关联；设计已处理|B-05/V010，未知deadline不释放、微秒settlement、禁用/缩容并发；INT03实证另门槛|
|DIFF-09 P1|未有文件DB实现；跨文件/DB成功易错认为原子|durable文件/目录先，DB登记后，file_reference/epoch保护清理；设计已处理|B-04/V009，orphan不可读、并发引用/迁移/删除、local及S3两后端共同契约|
|DIFF-10 P1|生成/候选/确认指针需独立且删除迟到结果不可覆盖|不可变输入/快照/工作稿/候选表、根version/epoch、needs_generation独立于PENDING；设计已处理|B-06/07/08、D07，所有覆盖矩阵关键用例；真实平台结果不由本地唯一键保证|
|DIFF-11 P1|原计划V003/V004低号与已冻结V005冲突|留空低号，统一连续V006—027预留，原脚本不改；设计已处理|所有拥有任务更新新brief，不改原计划；集成前checksum/重复版本/迁移历史回归|
|DIFF-12 P1|实际JdbcTaskQueue.enqueue用INSERT IGNORE懒建subject；claim锁JOIN；completeWithEffects先锁task后调用域callback；RECONCILING仍由旧task token完成|subject只能领域创建；领取候选后按显式顺序锁；域根先锁；外部结果改独立operation token；设计已处理|B-05/V010及领域入口对齐，禁止直接复用草案GREEN；subjectMissingFailsClosed、callbackCannotReverseDomainLocks、oldOriginCannotCompleteAfterReconcileTakeover|
|DIFF-13 P1|实际JdbcModelPermitPool构造INSERT IGNORE并遍历无序Map锁多个model；acquire可使用本地未配置的旧DB model，按客户端deadline删除额度|部署配置显式enabled/version，初始化按model排序；operation→model→reservation准入，deadline证据或NULL占额；设计已处理|B-05/V010，unknownOrDisabledModelDenied、concurrentConfigOrder、noUnprovenDeadlineRelease；构造器不得隐式改变生产策略|

## 首版结构验证历史（提交c73a765，不能作为1.1通过证据）

最终新建专属一次性fixture：`ama-test-b01-b901c0503cf5`，loopback `127.0.0.1:45717`，schema `ama_b01_fixture`；helper虽命名B01，所有权目录位于本B00工作树。没有连接保留的B01/B02/B05库、根45173端口、公司平台或生产。凭证仅在被忽略的本地env和容器内使用，不输出。

执行命令（WSL Ubuntu-24.04；按实际工作树执行）：

```sh
cd /home/tanren/ama/.worktrees/phase-b-database-design
source /home/tanren/ama/.worktrees/phase-a/.tools/env.sh
python3 tools/dev/mysql_fixture.py start
python3 tools/checks/database_design.py generate
python3 -m py_compile tools/checks/database_design.py
python3 tools/checks/database_design.py verify --container ama-test-b01-b901c0503cf5
```

验证器只接受本工作树helper新建、指定name/label、loopback端口且空schema的容器，拒绝远程/其他任务/非空库DDL，不DROP/RESET。显式`--metadata-only`仅重查同一拥有库，不运行DDL。执行review SQL后读取information_schema全量TABLES/COLUMNS/STATISTICS/CHECK_CONSTRAINTS/KEY_COLUMN_USAGE/REFERENTIAL_CONSTRAINTS，精确比对表/引擎/排序、每列类型/顺序/NULL/default/extra/排序、所有显式及MySQL自动FK索引的列顺序/唯一性/前缀、全部CHECK表达式与ENFORCED、全部FK列/目标/RESTRICT。CHECK用受限AST消除MySQL冗余括号/元数据引号转义，保留AND/OR分组和字面值大小写，未知语法fail-closed。字典和ER与声明源逐字节同步也检查；真实metadata才是结构证据。

首版最终命令 **exit 0**：MySQL **8.4.6**，**79表、612列、247索引定义（363索引列）、85个ENFORCED CHECK、162外键约束（166关联列）全部一致**。DDL SHA256 `a2bdcdaf65abc29f3a2c6e498c95220aeb3fe9b09698c529b831484712e999df`；镜像 `mysql@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9`。首版完整元数据保存在git提交c73a765的同名JSON历史中；当前[`database-metadata-evidence.json`](database-metadata-evidence.json)仅保留1.1结果。另执行非法storage枚举/不存在identity的session，分别CHECK3819/FK1452拒绝；AST检查器验证逻辑分组/字面值大小写不会误判。以上不代表产品行为测试。

验证过程保留失败事实：第一专属库 `ama-test-b01-30bc2ebafd07:45159` 建表/列比对成功，但索引比较 exit 1，因为SQL元数据按不区分大小写排序（audit_resource_time在PRIMARY前）、Python排序不同；修正为双方使用相同键排序，未忽略任何索引。第二新库 `ama-test-b01-42ff604f7074:45269` 全量DDL成功，索引/FK通过，但CHECK比较 exit 1，因为MySQL返回的CHECK_CLAUSE包含转义引号；诊断实际字符串后实现保留语义的AST比较，该库`--metadata-only` exit 0（79表606列的一版）。自审补声纹撤销记录/逐收件人独立消息后，**没有改写/复用上述结构库来冒充全新运行**，最终第三新空库完整CREATE＋全元数据核对一遍exit 0，结果见上。第一/第二库已由本任务helper移除；最终库保留供本次评审只读复核，根协调者可在评审后移除。

未运行Maven全套：本任务不改应用/正式Flyway代码，领域回归属于对应任务；无EXPLAIN性能报告、锁竞态/故障时间/NFR、生产HA或真实平台集成通过声明。所有性能估算、外部未知接口、安全用途/保留门槛在设计文档明确列出责任方和解锁条件。

## 当前1.1结构验证与复审门

本轮新建独有空fixture `ama-test-b01-01baa6c89b08`，loopback `127.0.0.1:45575`，schema `ama_b01_fixture`；未连接或改动首版及其他任务fixture。执行完整85表CREATE和全部FK，再读取全部实际information_schema，不只抽查新增表。命令（WSL）：

```sh
cd /home/tanren/ama/.worktrees/phase-b-database-design
source /home/tanren/ama/.worktrees/phase-a/.tools/env.sh
python3 tools/dev/mysql_fixture.py start
python3 tools/checks/database_design.py generate
python3 -m py_compile tools/checks/database_design.py
python3 tools/checks/database_design.py verify --container ama-test-b01-01baa6c89b08
```

实际exit 0：MySQL **8.4.6**，**85表、672列、270索引定义/404索引列、99个ENFORCED CHECK、179 FK/186 FK列**逐项一致。类型/NULL/default/顺序/排序、索引唯一性及无前缀、CHECK AST及FK目标/RESTRICT全部通过；字典/DDL/ER同步一致。DDL SHA256 **`16d69102d5b775130efac720a31794fcedf0ff27f4f0002ef3fb9756a605d183`**；镜像摘要同首版。非法storage enum与未知identity的session再次分别被3819/1452拒绝，AST语义控制通过。本轮无失败DDL/元数据运行，完整实际结果在[`database-metadata-evidence.json`](database-metadata-evidence.json)。本fixture保留供复审只读复核，凭证不入仓库或报告。

六项作者修正有完整结构证据，但名称并发、列表一致性、同确认及目标更正重试、增强输入链和标记失效仍须所属任务做行为测试；本轮未跑应用/Maven、性能或真实平台测试，不以结构成功代签独立采用结论。

## 独立评审清单与结论边界

|检查|作者自审状态|独立评审|
|---|---|---|
|十组实体＋会话/额度/评测配置/邮件覆盖且逐字段|1.1补齐六项，85表672字段|待scoped复审|
|ER/字典/DDL一致、真实8.4元数据精确一致|以本节1.1新空库结果为准|待scoped复审|
|名单隔离、CAS、不可变快照/候选、幂等/租约、删除迟到|事务/约束/拥有回归已映射；未声称实现通过|待审|
|外部marker真提交、对账接管、NOT_FOUND/迟到唯一性|规则已明确；平台协议仍前置|待审|
|全局锁序/consumer/permit/清理参考关系|明确路径、逻辑关系拥有者及删除顺序；并发实测待领域|待审|
|冻结迁移与新增连续注册|原SQL不改、V003/V004不回填、任务映射已记录|待审|
|未知规则分离且无B必需结构TBD|只外部协议/字典内容/企业用途/保留/运维证据待补|待审|

评审者需针对提交 `BASE..HEAD` 与本证据给出 findings、严重度、位置、责任方和结论；作者根据独立发现修正基线并重新验证受影响结构。不得由作者代签“独立评审无阻塞”。实现差异必须由拥有任务闭环并给回归证据后才可验收，设计采用不自动清除 DIFF-*。
