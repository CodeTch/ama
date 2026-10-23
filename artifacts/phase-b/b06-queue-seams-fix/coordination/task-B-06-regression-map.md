# B06.1 共享身份预锁：实际 MySQL 回归静态映射

日期：2026-10-23。只读基线：协调者主树 `/home/tanren/ama`，HEAD `8bd5e59`；生产源码仍是 `6b48b2f` / `05326e9` 后已采用的原 B05 实现。没有读取正在改动的 `.worktrees/phase-b-transcript-runtime` 生产文件，没有运行测试、连接数据库、调用 Docker、启动服务或读取私有 env/凭证。本文件是执行范围助手记录，不是代码审查或通过证明。

**建议：B06 新身份/能力/fact-mode 专项证明之外，继承回归使用下列原六类的 22 个精确方法，加3个 B03 旧调用方兼容方法，共25个；不整类跑 ReviewConcurrencyIT / ModelPermitIT / WorkerProcessIT，不把旧 schema / refusal / recovery 套混入已成功迁移到 V013 的独有库。** 这些是源码静态方法数，绝不是实际执行数。最终通过数、failures/errors/skips 和去重身份必须来自本次新 XML。

## 1. 先判定 fixture / target 安全性

所有相对路径均以 `/home/tanren/ama/` 为根。下列安全标签供后表逐项引用；它们只证明已读的稳定源码调用链，不替代执行者核对其正在修改的 helper 没有新增破坏动作。

|标签|已核对的入口与证据|在已到 V013 的独有库上的结论|
|---|---|---|
|S1：TaskFixture|`backend/integration-tests/src/test/java/ama/task/TaskFixture.java:13` runId；`:14` 只调用 `MysqlTestDatabase.dataSource()`；`:18`–`:35` 是 TEST_TASK 的本地 meeting/input policy；`:40` 注册队列；`:43`–`:52` 为每次 UUID owner/subject/input 和带 runId 的 pool 插入新行；`:55`–`:59` 只操作本测试任务/subject 或读 outbox cursor。|没有 reset/drop/Flyway/迁移 target/历史拒绝入口。新增行依赖已经存在的 identity、meeting、meeting_info_revision、task/outbox 表，不要求数据库总行数为零。旧 policy 不验证 ACTIVE 身份，不能拿这些 GREEN 代替 B06 身份证明。|
|S2：DataSource|`backend/integration-tests/src/test/java/ama/support/MysqlTestDatabase.java:22`–`:29` 只校验显式 loopback fixture URL/容器名并创建 DataSource；危险的 `reset()` 是另一入口 `:11`–`:15`，包含 DROP/CREATE DATABASE。`backend/adapters/src/main/java/ama/db/MysqlDataSources.java:10`–`:26` 只构造 DriverManagerDataSource 和连接属性。|S1/S3/S4/S5/S6 均没有调用 reset；dataSource 不调用 reset/migrate。环境必须仍由协调者提供已授权的新独有库；不要在本记录中复制秘密值。|
|S3：OutboxFenceIT|`backend/integration-tests/src/test/java/ama/task/OutboxFenceIT.java:28`–`:42` 的 BeforeEach 只用 dataSource，检查成功 V010 **存在一次**，没有要求最高 target=010 或历史只有8条；`:45`–`:54` 新建 UUID owner/meeting。|已成功到 V013 的完整历史仍含成功 V010，因此 setup 的历史条件兼容；无 reset/drop/migrate/refusal。类注释中的 V010 是最早前提，不能误读为 final target 限制。|
|S4：ExternalExecutionIT|`backend/integration-tests/src/test/java/ama/task/ExternalExecutionIT.java:10` setup 仅 S1；`:23`–`:29` 的 ACK-loss 方法用代理包裹同一 source，`:27` 有第二个显式 test policy，`:28` 新建同源 queue；只故障注入 JDBC commit ACK。|本文选择的方法没有迁移或 schema reset。ACK-loss 不是历史 migration-refusal，也不调用 Docker。必须连同这处匿名 policy 做必要的新显式 preparation 适配，保持 server commit 后抛 ACK 异常的原语义。|
|S5：ReviewConcurrencyIT|`backend/integration-tests/src/test/java/ama/task/ReviewConcurrencyIT.java:81`–`:90`、`:93`–`:128`、`:131`–`:185` 都从 S1 开始；`:198`–`:222` 用同一个被包装 source 另建本地 test queue/policy；`:235`–`:263` ProbeSource 仅代理真实连接，`:239` 明确使用 RR，探针只卡 JDBC 边界。|没有 reset/drop/migrate/refusal 或子进程/网络调用。四个 observe 方法与 V013 历史无额外约束；必要的 policy API 适配不能把真实 RR 交错改为模拟返回。|
|S6：Worker / model|`backend/integration-tests/src/test/java/ama/task/WorkerProcessIT.java:7` 仅 S1；选中的 `:17` heartbeat 方法只新建 q/e 与 `ExternalOperationHeartbeat`。`ModelPermitIT.java:7` 仅 S1，model 使用 UUID；`:8`–`:9` token/pool 都用本地 TEST_CALL。|选中的 heartbeat 无子 JVM、Docker 或迁移，固定等待约6.1秒，覆盖有效 external renew。条件项 model 方法同样安全；不能因此扩大为整套 quota/model 回归。|
|S7：后续 SQL 的方向|`backend/adapters/src/main/resources/db/migration/V011__transcript_schema.sql:14` 起创建新 transcript 表，`:127` 起 FK 加在新表上；`V011_1__transcript_material_index.sql:1`–`:5` 只加 transcript index；`V012__minutes_evidence.sql:5` 起创建新表，`:249` 起 FK 加在新表上；`V013__publications.sql:5`–`:75` 新建5表，`:79`–`:113` FK 加在这5表上。|这些后续版本没有把 S1 的旧 fixture insert 变为必须先创建 transcript/minutes/publication 行的操作。V013 的 FK 指向旧 task/external 表是新表→旧表；本组不删除被引用的旧行。本文不执行或建议重新迁移。|
|S8：B03 MeetingConcurrencyIT|`backend/integration-tests/src/test/java/ama/meeting/MeetingConcurrencyIT.java:23` 仅 dataSource；`:25` owner/member UUID；`:27`–`:29` BeforeEach先调用 `new SchemaMigrator(actual).migrate()`，再插入identity。`backend/adapters/src/main/java/ama/db/SchemaMigrator.java:17` / `:32` 只在迁移外层获取/释放advisory lock；`:24`–`:29` 是当前classpath latest、cleanDisabled=true，无固定target/clean/repair/outOfOrder。`MeetingConcurrencyIT.java:32`–`:36` 仅本地构造repository/services，`:129`–`:149` ProbeSource代理真实RR连接。|**兼容V013，但它确实调用 latest迁移器，不能称为“无migrate入口”。** 前提是当前12个SQL保持不变、完整成功历史已到013、无pending；此时应验证/零迁移，不会走历史target或拒绝数据构造。无reset/drop、Docker、HTTP server或子JVM。若执行规则另行禁止任何migrator调用，原方法就不能原样跑，不能绕过BeforeEach声称已执行原方法；回报此约束即可。|

外部 reconciliation 扫描是全库候选：`JdbcExternalOperations.java:67` 每页64个；`ExternalExecutionIT.java:17` 已有最多100次扫描并断言本 operation 被取得。不能同时让别的任务共用本库跑相同全局扫描/全局 outbox-cursor 测试；该限制不构成使用旧库或新开服务的建议。

## 2. 默认精确继承回归（原六类22个，B03兼容3个）

### 2.1 普通 queue / domain acquisition（9 个方法）

共同安全链 S1+S2+S7。原生产受影响入口是 `backend/adapters/src/main/java/ama/task/JdbcTaskQueue.java:38` lockDomain，`:40`–`:48` enqueue，`:67`–`:85` claim/validLease，`:87`–`:97` renew/complete；原 `TaskDomainPolicy.java:9`–`:20` 的 policy 契约也在本次窄修正范围。

|精确 Class#method|静态源位置与覆盖机制|为何保留 / 重叠|
|---|---|---|
|`TaskLeaseIT#twoWorkersCompeteForOneTask`|`backend/integration-tests/src/test/java/ama/task/TaskLeaseIT.java:12`–`:18` 两个真实连接同时 claim，只能一个 lease。|新 preparation 进入 claim 每候选事务，不能让同 task 双取。不同于下面 held-row 的可前进性。|
|`TaskLeaseIT#expiredAttemptCannotCommit`|`:20` 旧租约过期、renew 失败、reclaim 后旧 complete 无 effects，新 complete 只执行一次。|覆盖 validLease 经过新 acquisition 后的租约/attempt/效果边界。|
|`TaskLeaseIT#pausedOldCompletionLosesToConcurrentCancellation`|`:21` 独立线程暂停旧 completion，取消 epoch 先提交，再放行 completion。|保留普通 mutation 与取消之间的真实线程场景。它的 latch 在进入 complete 之前，并非证明“已持有 SQL 锁时被取消”；不要扩大证据含义。|
|`TaskLeaseIT#versionEpochAndTombstoneEachFenceOldCompletion`|`:22` 一个测试方法内部循环 business version / cancel epoch / tombstone 三种 fence；每种 renew/complete 均失败。|与前项 epoch 场景部分重叠，但补齐 version 与删除语义；循环3次不是3个 JUnit 测试方法。|
|`TaskLeaseIT#sameProducerComparesEntireImmutableSpec`|`:23` enqueue 两次同 spec 得同 id，改 immutable spec 被拒绝。|新 PreparedDomain 绑定 complete spec，不能让 enqueue replay 绕过原不可变比较。|
|`TaskLeaseIT#missingSubjectAndUnknownOrCrossSubjectTypedInputFailClosed`|`:24` 实际断言为 missing subject 与跨 subject input 拒绝。|保留缺 root/fence 与 typed-input provenance 的失败分支；方法名虽含 Unknown，这个方法没有单独构造 unknown kind，勿声称已覆盖。|
|`TaskLeaseIT#heldRowDoesNotBlockOtherCandidate`|`:25` `ValueSource={subject,root,task}`，真实独立 blocker 分别持3类行，claim 必须取另一候选。|新 identity prelock 不能破坏既有 subject/root/task SKIP LOCKED。该方法静态展开3次；新增 identity-held skip 仍由 B06 自己的真实证明负责。|
|`TaskLeaseIT#queueDomainAndOutboxRollbackTogether`|`:28` caller tx 内 enqueue+audit+领域更新+最终 flush 后抛错，task/audit/sequence 全回滚。|新的 ambient 明确能力适配必须继续保证同一事务原子性；旧 identity-free test opt-in 应明确，不能靠通用默认放行。|
|`TaskLeaseIT#completionCallbackFailureRollsBackTaskAndOutbox`|`:29` completion effects 写 domain 后抛错，task 仍 RUNNING，domain/sequence 不变。|补齐 completeWithEffects callback 失败的回滚；与 enqueue rollback 是两个不同入口。|

### 2.2 external marker / facts / retry（7 个方法）

共同安全链 S1+S2+S4+S7。原生产受影响入口：`backend/adapters/src/main/java/ama/task/JdbcExternalOperations.java:30` beginExternal；`:56`–`:78` lock/claimReconciliation/renew；`:93`–`:133` observe；`:135`–`:157` retry。尤其 `:137` / `:141` / `:149` 原来在 fresh/replay 路径重复 lockDomain，不能变为 root 后重复 identity preparation。

|精确 Class#method|静态源位置与覆盖机制|为何保留 / 重叠|
|---|---|---|
|`ExternalExecutionIT#markerActuallyCommitsBeforeAuthorityAndAmbientIsRejected`|`backend/integration-tests/src/test/java/ama/task/ExternalExecutionIT.java:14` ambient begin 拒绝，成功 marker 可从新连接看见，task RECONCILING，不可再次 begin/普通 complete。|保护经新 validLease preparation 的独立 marker 边界。|
|`ExternalExecutionIT#unknownExecutionNeverReentersOrdinaryQueue`|`:15` UNKNOWN callback 不运行，普通 queue 不取，zero-effect retry 被拒，execution 仍一个。|fact purpose 分流不能把 UNKNOWN 错当无效果/重新发送许可。|
|`ExternalExecutionIT#oldTaskExpiryDoesNotDiscardAuthoritativeSuccess`|`:16` task lease 过期后有效 operation success 仍 APPLIED，domain callback 一次。|区分 task lease 与已提交 external authority；不能因新的统一 acquisition 把此事实丢弃。与 disabled-identity fact 专项是不同 fence。|
|`ExternalExecutionIT#reconcilerTakeoverFencesOldOwnerAndPreservesExternalFact`|`:17` claim reconciliation 接管，旧 renew/observe 拒绝，业务 version 更新后新 token 的成功为 RECORDED_FENCED，operation 仍 SUCCEEDED。|一次覆盖 reconciliation skip acquisition、external renew 的 stale 分支、fact 保留但 suppress domain callback。|
|`ExternalExecutionIT#commitAcknowledgementUnknownReturnsNoAuthority`|`:23`–`:29` 实际 commit 成功后代理抛 ACK lost，无返回 authority，task 不重新可取。|begin/prepare/work 事务组合改变后仍需保护不可重发边界；无外部服务或 native cohort。|
|`ExternalExecutionIT#concurrentSameProducerRetryAcceptsOnlyOneExecution`|`:31` 两个真实 retry 事务同时提交同 producer，返回同 task，总 execution=2。|同时触及 fresh retry 与同 producer replay；直接保护重复 lockDomain 复用局部 scope。默认不另重复运行纯顺序 replay 方法。|
|`ExternalExecutionIT#zeroEffectRetryAppendsExecutionWithoutResettingEpoch`|`:40` zero proof→retry task→claim→新 marker gen2，epoch 增长；旧 gen1 成功导致 QUARANTINED/MANUAL_REQUIRED，无旧 callback。|补齐现有 PREPARED→MARKED 路径与 old/current 两 spec 的事实处理；和并发 retry 的互斥断言不同。|

### 2.3 RR full rollback / rediscovery（4 个方法）

共同安全链 S1+S2+S5+S7。四个方法必须精确选，不带同类的 model configuration 方法。

|精确 Class#method|静态源位置与覆盖机制|为何保留 / 重叠|
|---|---|---|
|`ReviewConcurrencyIT#lateSuccessSeesZeroEffectCommittedWhileWaitingForSubject`|`backend/integration-tests/src/test/java/ama/task/ReviewConcurrencyIT.java:78`；共享 helper `:131`–`:180` 把 writer/observer 卡在真实 subject SQL 边界。|RR observer 等待时 zero-effect 已提交，必须看到并隔离矛盾成功。现有身份 prepare 之前增加发现/read 可能改变 ReadView 时点，这是直接风险。|
|`ReviewConcurrencyIT#lateSuccessRediscoversRetryCommittedWhileWaitingForSubject`|`:79`；helper `:163` fresh retry writer，`:175`–`:184` gen2隔离与不再授新 send authority。|比前项多了 current execution/task 变化，保护 current+old 完整 spec discovery 与整事务重试。|
|`ReviewConcurrencyIT#observationRejectsAmbientTransactionBeforeAnyEffects`|`:81`–`:90` caller tx 内 observe 抛错，effects=false、operation仍 MARKED。|新的 ambient enqueue 能力只影响明确入口，不能意外开放 observe ambient。与 beginExternal 的 ambient 拒绝是两个独立 API。|
|`ReviewConcurrencyIT#exhaustedRediscoveryRequiresResubmittingFactWithoutAnotherExternalSend`|`:93`–`:128` 每次真正 subject lock 前由另一真实事务推进 generation，4次后抛明确可重交事实异常；同 fact 再交 QUARANTINED，callback未运行。|直接保护4次完整 rollback/rediscovery 上限与同 fact resubmission；不得降级为静默 STALE_TOKEN。与前项单次 retry 交错不重复。|

### 2.4 outbox final boundary 与有效 external renewal（各1个方法）

|精确 Class#method|安全/兼容证据与覆盖机制|为何保留 / 重叠|
|---|---|---|
|`OutboxFenceIT#domainLocksAndNewEventsAreRejectedAfterFinalFlush`|S2+S3+S7；`backend/integration-tests/src/test/java/ama/task/OutboxFenceIT.java:165`–`:173` flush 后 domain lock/new append 都拒绝、sequence 回滚。原 `JdbcSubjectFences.java:51`–`:55` / `TaskTransactionContext.java:35`–`:41` 是边界。|把准备/能力接入同一 tx 后必须继续遵守最终 flush；该旧方法只直接验证 fence/outbox 入口，不证明新 identity API 的 after-flush 分支。|
|`WorkerProcessIT#independentOperationHeartbeatContinuesWhileHandlerBlocks`|S1+S2+S6+S7；`backend/integration-tests/src/test/java/ama/task/WorkerProcessIT.java:17`，scheduler 在 `backend/worker/src/main/java/ama/worker/ExternalOperationHeartbeat.java:12` 经过5秒调用真实 operations.renew；expiry 需增长。|默认集合中唯一明确验证**有效** external renew 的旧方法，前面的 takeover 只验证 stale renew=false。此方法不启动子 JVM；不需整类跑 worker kill/reclaim。若本次 B06 新专项已在真实 MySQL 上明确验证同一 ACTIVE-token renew 成功，可去重省掉它并在报告映射到那条实际 XML。|

### 2.5 B03 旧 lock/require 调用方兼容（3 个方法，窄增补）

本修正新增 `JdbcMeetingAccess` identity-phase API，也会影响旧能力共享实现。原 `backend/adapters/src/main/java/ama/meeting/JdbcMeetingAccess.java:41` 的 public lock 组合 `lockIdentities`→`lockMeeting`，`:42`–`:64` 负责binary排序/当前identity/subject/root，`:66`–`:82` require负责重读root与grant provenance；旧 `JdbcMeetingRepository.java:64`–`:65`、`:94`–`:95` 直接使用包内路径，`:124` 则使用旧 public lock/require。只测B06新ambient authgate不能证明这些旧service调用仍保持行为。因此这3个为本修正的窄继承回归，不扩展HTTP/B04/S3套。

共同安全链 S2+S7+S8；尤其事先确认它的helper是latest迁移器而非历史target。执行报告必须诚实记录这个BeforeEach迁移器调用及实际零迁移结果。

|精确 Class#method|静态源位置与覆盖机制|为何保留 / 重叠|
|---|---|---|
|`MeetingConcurrencyIT#identityDisabledWhileConfirmationWaitsCannotReceiveGrant`|`backend/integration-tests/src/test/java/ama/meeting/MeetingConcurrencyIT.java:39`–`:54` 独立writer持member identity的DISABLED更新；真实confirm等待identity，提交后Conflict，revision不前进且grant=0。|保护旧roster confirm的全身份预锁/当前DB状态；与B06禁用owner后的task completion/fact控制是不同旧调用方。|
|`MeetingConcurrencyIT#actualServiceLocksSortedIdentitiesBeforeRootAndFlushesLast`|`:86`–`:104` 直接调用原service，验证ASCII/U+E000/U+10000的实际MySQL二进制顺序，最后identity < subject < root < audit < outbox，最后SQL是sequence更新。末尾 `meetings.read` 还经过旧public lock/require。|新identity-phase共用代码的排序和无后补锁/flush-last最直接旧调用方证据；B06单owner或新ambient测试不能替代此完整service路径。|
|`MeetingConcurrencyIT#readWaitingForRevocationSeesCommittedRosterNotOldSnapshot`|`:56`–`:72` revoke writer在commit前被卡住，member reader等待identity后必须读到已提交撤销并AccessDenied；runtime用真正RR source。|直接保护旧public lock/require授权读与撤销grant；新增fact-mode不能影响它的普通ACTIVE身份、root和grant语义。与前两项写侧排序/disabled-member确认不同。|

不默认带入同类 `concurrentConfirmationsHaveOneVersionAndOneEffect`（`:75`，CAS竞争）和 `guessedFutureCasCannotBypassCompleteRemovedIdentityPrelock`（`:106`，discovery/CAS race）：当前窄修改若没有改变旧repository的discovery/`requireDiscoveredIdentities`或CAS判断，前述3项足以聚焦实际共享风险；发现这些具体旧路径也被修改时，再单列相应精确selector及原因，而不整类加跑。两方法同S8兼容规则，均不含reset或历史target。

## 3. 可执行 selector 形态（仅提供，未执行）

在作者自己的 tree 与协调者已经授权、已成功到 V013 的同一独有库上下文执行。这里没有加载 env、创建 fixture 或执行任何命令。以下方法组合是上表25个静态方法（原六类22+B03兼容3）的精确并集，无 wildcard：

```bash
B06_INHERITED_SELECTORS='TaskLeaseIT#twoWorkersCompeteForOneTask+expiredAttemptCannotCommit+pausedOldCompletionLosesToConcurrentCancellation+versionEpochAndTombstoneEachFenceOldCompletion+sameProducerComparesEntireImmutableSpec+missingSubjectAndUnknownOrCrossSubjectTypedInputFailClosed+heldRowDoesNotBlockOtherCandidate+queueDomainAndOutboxRollbackTogether+completionCallbackFailureRollsBackTaskAndOutbox,ExternalExecutionIT#markerActuallyCommitsBeforeAuthorityAndAmbientIsRejected+unknownExecutionNeverReentersOrdinaryQueue+oldTaskExpiryDoesNotDiscardAuthoritativeSuccess+reconcilerTakeoverFencesOldOwnerAndPreservesExternalFact+commitAcknowledgementUnknownReturnsNoAuthority+concurrentSameProducerRetryAcceptsOnlyOneExecution+zeroEffectRetryAppendsExecutionWithoutResettingEpoch,ReviewConcurrencyIT#lateSuccessSeesZeroEffectCommittedWhileWaitingForSubject+lateSuccessRediscoversRetryCommittedWhileWaitingForSubject+observationRejectsAmbientTransactionBeforeAnyEffects+exhaustedRediscoveryRequiresResubmittingFactWithoutAnotherExternalSend,OutboxFenceIT#domainLocksAndNewEventsAreRejectedAfterFinalFlush,WorkerProcessIT#independentOperationHeartbeatContinuesWhileHandlerBlocks,MeetingConcurrencyIT#identityDisabledWhileConfirmationWaitsCannotReceiveGrant+actualServiceLocksSortedIdentitiesBeforeRootAndFlushesLast+readWaitingForRevocationSeesCommittedRosterNotOldSnapshot'

./mvnw -B -ntp -pl backend/integration-tests -am \
  '-Dtest=NoUnitTestsForB06FocusedIntegrationSelection' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  "-Dit.test=$B06_INHERITED_SELECTORS" \
  -Dfailsafe.failIfNoSpecifiedTests=false verify
```

这里用明确不存在的 unit selector 抑制每次 integration `verify` 附带重复跑所有 Java unit；不会把0个unit当成 unit PASS，最后当前 Java unit suite 仍按 ACK 单独实际执行一次。此风格已有 `docs/acceptance/b/meeting-domain-fix-report.md:40` 的已采用命令先例。不要用 `-DskipTests` 执行 IT，它通常连 Failsafe 一并跳过；`-DskipTests test-compile` 只能叫编译验证。

`tools/checks/run.py:55`–`:59` 当前只把 `#` 前整段当一个 class pattern，不能接收上面的逗号多类选择串；`:70`–`:75` 在 integration 模式也会 `verify` 并顺带重跑 units。故此处给直接 Maven 形态，不修改 helper。执行者应独立保存已有 XML，再采集新 raw log / XML / source SHA，并确认选中每个方法确实出现；`failsafe.failIfNoSpecifiedTests=false` 是 reactor依赖模块的兼容选项，不能成为“0 IT 也通过”的证据。

## 4. 数量与去重口径

|稳定源码范围|静态测试方法数|本建议默认选中|
|---|---:|---:|
|TaskLeaseIT|11（含1个参数化方法）|9|
|OutboxFenceIT|7|1|
|ExternalExecutionIT|10|7|
|ReviewConcurrencyIT|5|4|
|ModelPermitIT|7|0|
|WorkerProcessIT|3|1|
|原六类小计|43|22|
|MeetingConcurrencyIT（B03窄增补）|5|3|
|合计|48|25|

默认25个方法中，heldRow的一个参数化方法有3个输入，所以静态展开可列27个潜在 JUnit invocation；这仍然不是执行数。version/epoch/tombstone 的内部3次循环不会再多算方法。若作者同一批命令还选了整类或 B06 专项重复入口，只能按新XML里的真实 `(classname,name)` 合并去重，不能把每轮小计相加称独有测试数。不得把既有179-unit源码基线乘以 verify 次数；新增unit和既有unit重跑须分别说明。

本集合是**继承行为回归**。opaque issuer/actual holder/跨tx/跨DS/完整spec/purpose、unsigned UTF8身份并集、held-identity skip、root后不重取identity、authentic ambient adoption、DISABLED身份事实保留且 effects=0/无renew或新send等，旧 identity-free TEST_TASK 都不能独立证明；应指向本次已授权 B06 新专项的实际证据。上表不能替代 ACK `task-B-06-seams-root-ack.md:17`–`:20` 要求的这些新增证明。

## 5. 仅在具体共享风险出现时追加（不默认要求重跑 model / worker 套）

|精确 selector|具体触发条件 / helper安全证据|与默认集合的关系|
|---|---|---|
|`ModelPermitIT#copiedAndReconciliationTokensCannotAdmitAndDuplicateCannotResend`|仅当本次改动触及 `JdbcExternalOperations.java:18`–`:20` authority identity集合、`:28` valid predicate、`:53` grant边界，或 `JdbcModelPermitPool.java:42`–`:57` acquire 的 authority/state 检查时追加。方法在 `ModelPermitIT.java:16`–`:22`，S1+S2+S6+S7，无 reset/migration。|专门检查复制token、重复admission、reconciliation token不能变新send许可。仅修改 domain preparation/scope 并保留这些 authority路径时，不要求作者重复 quota/model。不要顺手带 capacity/deadline/configuration 方法。|
|`WorkerProcessIT#separateProcessConsumesProducedTask`|仅当 `TaskWorkerProvider` / ServiceLoader / WorkerMain/WorkerLoop wiring 本次实际改动，或新的 fixture显式 opt-in 没有任何真实新JVM消费证据时追加。`WorkerProcessIT.java:8` / `:10` 仅启动独立Java child，`TaskWorkerProvider.java:5` 用 S1、明确测试pool，source同S2；没有迁移/Docker。|验证测试 provider与已编译接口跨JVM组合；通常不需要为未改动的worker wiring额外跑。不能由它声称B06生产Worker/provider已激活。|

条件 selector 直接追加到同一个 `-Dit.test` 列表，保留精确 `Class#method`，并重新计算**实际**XML去重；不要再重复默认方法。默认 retry并发方法已覆盖同producer replay：`ExternalExecutionIT#safeRetryReplayReturnsSameAcceptedTask`（`:32`，安全链S1/S2/S4/S7）可作为失败定位的顺序替代诊断，正常通过后不必再叠加。

## 6. 本轮应避免的 classes / cohorts

|class / cohort|静态理由与位置|
|---|---|
|`B05SchemaIT`|`backend/integration-tests/src/test/java/ama/task/B05SchemaIT.java:8` 每方法先执行历史 target010 helper，`:13` 固定target010，`:32` 断言历史恰为8个版本。即便 `:15` 的表数测试看似无害，BeforeEach仍会进入历史target；不得放入已到V013库。|
|`FoundationIT` / `IdentityMigrationIT` / `MeetingSchemaIT`|`backend/integration-tests/src/test/java/ama/db/FoundationIT.java:34`、`backend/integration-tests/src/test/java/ama/identity/IdentityMigrationIT.java:12`、`backend/integration-tests/src/test/java/ama/db/MeetingSchemaIT.java:30` 直接 reset；S2说明会DROP/CREATE。|
|`IdentityAccessIT`|`backend/integration-tests/src/test/java/ama/identity/IdentityAccessIT.java:30` BeforeAll直接reset，`:41` / `:43` BeforeEach还批量DELETE identity_session/enterprise_identity。不能因“identity兼容性”关键词把此HTTP套混入新V013回归。|
|`IdentityMigrationRefusalIT` / `MeetingSchemaLegacyAclIT`|`backend/integration-tests/src/test/java/ama/identity/IdentityMigrationRefusalIT.java:11`–`:20` 要独立空库、迁移到006并写不安全旧identity；`:29` 保留失败V007。`backend/integration-tests/src/test/java/ama/db/MeetingSchemaLegacyAclIT.java:11`–`:33` 要独立空库和显式mode，制造旧ACL后预期V008失败。不是普通runtime失败分支。|
|`FileSchemaMigrationIT` / `TranscriptSchemaIT` / `MinutesSchemaIT`|`backend/integration-tests/src/test/java/ama/storage/FileSchemaMigrationIT.java:82`–`:84` 仅允许到009的历史；`backend/integration-tests/src/test/java/ama/db/TranscriptSchemaIT.java:43`–`:45` / `:425`–`:428` 仅允许到011.1；`backend/integration-tests/src/test/java/ama/db/MinutesSchemaIT.java:38` / `:399`–`:402` 仅允许到012。全都不接受已到013的历史，且含schema安装/结构证据动作。|
|`PublicationSchemaIT`|它的FULL确实含013（`backend/integration-tests/src/test/java/ama/db/PublicationSchemaIT.java:38`），不是“target不兼容”。仍是无关schema qualification，BeforeAll `:71`–`:90` 会验证fixture/Docker/SQL hash、准备结构证据，并有migrate helper；本次没有SQL变化，不调用它重新资格化。|
|`DatabaseRecoveryIT`|`backend/integration-tests/src/test/java/ama/task/DatabaseRecoveryIT.java:8` inspect，`:12` capture，`:14` docker restart，`:20` 硬断言8条成功历史；既超出本次窄scope，也不兼容V013历史。|
|整类 `WorkerProcessIT` / 整类 `ExternalExecutionIT`|前者会带入 `WorkerProcessIT.java:11`–`:15` 的进程kill+等待回收30秒场景；后者会带入 `ExternalExecutionIT.java:18`–`:21` 的MarkerCrashProbe进程kill，及`:33`–`:38` 的attempt-cap路径。默认保留marker/fact/retry/有效renew即可；本次没有WorkerLoop/attempt-cap状态机修改，不重复恢复/kill cohorts。|
|`ReviewConcurrencyIT#allModelLocksPrecedeReservationReadView`、其余 `ModelPermitIT` 方法|`ReviewConcurrencyIT.java:18`–`:75` 是model配置先锁全集再count的已采用FIX1容量问题；`JdbcModelPermitPool.java:19`–`:41` configure/inflight不经queue.lockDomain。没有具体model/authority共享改动时，不重跑quota/config/deadline cohorts。|
|native / S3 / multipart / wire-proxy / local durability / file-service HTTP cohorts|本次不改存储后端、S3 wire、native I/O或schema；不从上述task/fact selector传递调用这些类。不要用整package、全部IT、wildcard来“保险”，也不重复旧10001-part/native/S3资格化。|

最终执行报告要把本次B06新专项、这里继承方法、可选追加、失败重跑和最后unit分开，并保留明确重叠关系；本静态映射不授予任何数据库/服务操作新许可。
