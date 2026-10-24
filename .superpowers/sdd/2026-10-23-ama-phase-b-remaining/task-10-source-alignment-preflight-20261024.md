# Task 10 source-alignment preflight — 2026-10-24

## Boundary and result

只读接续准备完成。此文件不是新的设计评审、源码采用、实施授权、运行时资格或 Phase B 验收。固定观察基线为 **857102b917bbef77c38fae3211e3a65207a98c6c**（feat(worker): implement confined TXT import and genuine RAW completion），该 Task5 候选不因本报告被采用；不是活动工作树/HEAD 的封印。未重复 Task1–4 实施或重新设计。

只从 git show 857102b:path 读取产品、契约、测试和 POM；git ls-tree/cat-file 仅用于固定提交路径确认。W 中仅直接读指定交接/调度/裁决/brief/extract。未运行测试、构建、容器、数据库、网络、私有环境读取或子代理；未改产品、测试、POM、旧报告、HEAD/index，也未提交。仅新建本报告和同名前缀的 **task-10-source-alignment-preflight-20261024-source-hashes.json**。

后续顺序保持 5→6→8→7→9→10→11→12→13。Task5 只有 root 安排的唯一产品作者；此报告不涉及其活动工作树变化。

## 已补齐的原件和采用边界

完整读取 Task10 采用 wrapper、归档 manifest、276 行原设计、独立 SPEC PASS / design APPROVED 审查和共享锁序裁决。Task10 归档没有独立 R1/closure，不虚构该链。原设计 SHA fb0ea71f985ed280156ad88ea981c6f70ca5678e79043fef32c4f263e3b3e96f；独立审查 SHA e9c32d58283d454b1e650163c3c110e395acb2b2400e4206c82bd089ebbd3b3e。

补读 Task9 wrapper、原设计、完整 R1、closure、原审查和 R1 独立审查及真实 delta。R1 SHA b1b3343a1216b9df621c3db560412fdde908499890823c9e75820e620969add2；closure 8192adacd6a1e333524502cf21e6283a35b0f34dd7b0d96038296da3844c4fe5；R1 审查 2c563fb7485c3af5905f1f23d78d5af2edbd40ba570154431815aa14364e3103。原审查是 CHANGES REQUESTED，不改写为 PASS；R1 关闭 T9DR-01 后 SPEC/design PASS。独立重算原件→R1 的四项精确文字替换，完整字节相等；修复产品路由/仓库根 OpenAPI 路径，没有额外机制改动。较大工具输出曾截断，已用分段补读恢复关键缺口；不将截断输出当作完整证据。

另完整读取 controlled-runtime（含全文 Normative R1）、authority-external、Task5/6/8 采用 wrapper、minutes UX，以及 Task7 root rulings/attempt clarification。详细原件路径、raw SHA256、字节数/行数、读取方式与真实源码检查范围均在 companion manifest；wrapper 采用只证明其设计地位，不将文内旧“未实现”时间描述当作 857 当前源码事实。Task3/6/7 等更大历史包未全部重新阅读全文，不声称新一轮上游设计审查。

Task10 原设计第60行的旧广义锁序由共享裁决覆盖；Task7 root ruling 的 PUBLISHING 示例不得变成不存在的 meeting stage。裁决 SHA **3b10ab12f46f7b4a2f70a91871f20d4f4052662b1cfee2b08700b02188b6bc75**。固定提交中 Task9/10 全部归档 manifest 成员逐项 raw SHA 验证匹配。

## 固定基线的真实接口映射

下表以模块/包名缩写定位；完整仓库路径、raw hash/linecount 和检查范围见 companion 的 committedInputs。冒号为 857 的真实行号，不能对照活动工作树行号。

| 实际位置 | 可复用事实与 Task10 必须补的边界 |
|---|---|
| adapters migration V013_1__b_runtime_metadata.sql:19–34 | guard(meeting_id,intent_epoch) 及 0..9223372036854775806 CHECK/FK 已存在。没有 ACK、幂等 intent 服务，也没有“缺失即0”的权限。 |
| adapters/ama/meeting/JdbcMeetingRepository.java:34–59,61–124,139–159 | genuine create:51 同事务插0，replay 不补guard。save:85–87 和 roster confirm:116–120 已有 r/subject/search/final 更新，是必须安装统一候选 contribution 的真实写点；finish:152–155 最后 flush。writable 仅已发布/当前 r 检查，不是完整 Task11 publication guard。 |
| adapters/ama/meeting/JdbcMeetingAccess.java:27–44,57–104,106–134 | private issuer、真实 source transaction holder、身份先于 subject/meeting，unsigned UTF8 身份序；requirePrepared 校验身份覆盖/actor/meeting，require 新鲜读取已持根的权限；member READ 依 confirmed roster/grant，admin无旁路。不能从其它 package 构造 Held，不能用同 URL 或 ambient transaction 替代。 |
| core/ama/storage/CompleteReadPolicy.java:6–17；adapters/ama/transcript/JdbcTranscriptAuthority.java:252–370 | 实际 prepareRead→prelockRead→sources→prelockReadTasks→finishRead，私有 ReadScope 绑 issuer/holder/gate。lockRead:287–296 实际顺序是 transcript_revision 祖先 UUID→transcription_input→input_batch→sidecar→batch members。prelockReadTasks:325–334 合并全部 task 后，finishRead:339–349 才 attempts/producer。是源读取协议，尚非完整 minutes union/write facade。 |
| adapters/ama/transcript/JdbcProcessingFactsReader.java:27–30,55–124 | processing-only standalone detached read，明确拒绝 ambient transaction；公有构造器创建自己的 access，不能嵌入 Task10 命令当作 held writer。只能借鉴边界，不可重复调用以伪装全局一次加锁。 |
| adapters/ama/transcript/BoundedTranscriptMetadata.java:24–42 | 实际 remaining+1、元数据/CASE admitted 的 bounded projection；限定的是它列出的处理元数据。不是所有 minutes LONGTEXT / empty child set 的已测保护。 |
| contracts/ama/contract/MinutesContracts.java:188–266 | FullRow 固定物理投影，不是 ACL/完整查询证明；generation_input 无 cancel_epoch/config_manifest/acceptedRevision，minutes_element 无 origin_input；ACTION accepted 与 suggestion 分表。 |
| core/ama/minutes/contract/StoredSourceGraph.java:25–82,130–170 | body→原 generation_input；ACTION→原 suggestion.input_id；ordinary transcript element→该 revision 唯一 confirmation；目录各自 input_id；共享 locator 仍保留不同 occurrence。缺失/矛盾来源不能用最新 input 或文本相似性修复。 |
| core/ama/minutes/contract/ResultHashProfiles.java:23–66,79–93,141–158 | target 绑真实 body/set version/ID/完整内容/anchor；ACTION_FIELDS 绑 accepted nullable tuple；SOURCE_SUPPORT 精确全体相交 evidence，ACTION_FIELDS 全目标 evidence；sources 验原链。finalSources 仍需真实 final row，Task11 才增 prospective entry。 |
| core/ama/minutes/contract/ReviewCoverageEvaluator.java:10–30 | latest exact scope/hash/profile governs，WHOLE 按 scalar 完整范围；不合并 SPAN，不选择业务必需项，不生成 human credit。 |
| core/ama/minutes/contract/CreationSupport.java:6–20；OutputValidation.java:37–55；RowsetProjection.java:10–39 | 可复用 PARTIAL/UNSUPPORTED/null accepted tuple、scalar anchor/canonical anchorText、固定全set投影。原语不执行 DB 整图复制、人工确认或查询完整性。 |
| core/ama/config/ConfigBindings.java:172–183 | computeGeneration / verifyGeneration 已存在；ValidatedGenerationBinding 只有 fingerprint/manifest/profiles/outputBudget/canonicalBudget，没有 Task9 将补的 catalog/promptDefinition/modelDefinition accessor。原始 archives/parse/hash 仍须在根锁外完成。 |
| adapters/ama/task/JdbcTaskQueue.java:123–159,168–189；ConfinedTaskCompletionPolicy.java:4–11 | 真实包内 AfterTask 绑定 queue/policy/source holder/full spec/task/attempt/purpose/envelope/invocation；WRITING→VALIDATING→SETTLED→CLOSED，actual readback validator 在任务结束前。是普通完成能力，不能借作生成外部 retention token。 |
| adapters/ama/task/TranscriptImportRuntime.java:33–40,108–145 | 现有 confined import 安装和 inert carrier/同调用 writer-validator。RAW revision 使用 producer businessVersion，输出 typed TRANSCRIPT_REVISION；Task10 必须保留这条真实 producer ancestry，不能 SQL 假造成功 task。此候选尚待 Task5fix 最终采用重钉。 |
| core/ama/task/TaskDomainPolicy.java:9–42；ExternalOperationPort.java:8–24 | 现有 acquisition 仅 TASK_MUTATION/EXTERNAL_FACT_OBSERVATION；观察仍 observe(token,observation,Runnable)。尚无 Task7 生成专用私有一用 prepared/execution retention 接口；不得自行添 generic Outcome/flag 免验。 |
| api/ama/api/ProcessingConfiguration.java:42,87–94；worker/ama/worker/TranscriptWorkerRuntimeProvider.java:20–32 | 每进程单组 transcript components/registry/queue/WorkerLoop 的实际安装点。Task9扩展应汇入同一个不可变组装，不新增issuer/provider/第二调度器。 |
| worker/ama/worker/WorkerLoop.java:116–162,189 | 已有 DRAINING_RENEWAL，真实普通 tick/dispatch/admit 阶段；Task7 MARKING 必须在最终采用版 drain 后，不能恢复 monitor 持有期间做 JDBC 的旧 shorthand。 |
| api/ama/api/HttpRevisionTokens.java:8–26；MeetingController.java:30–44；contracts/openapi/api.yaml:22–56 | 实际 versioned routes 和 lossless legacy/token/equal-dual normalization 已实现；Task10复用包内 helper/ reviewed contract，无 double/2^53 DB cap。contracts/openapi/minutes.yaml 在857不存在。 |
| web/src/App.vue:1–34；web/src/shared/api/client.ts:5–19 | 当前只是会议列表未接入占位及通用fetch；没有真实 minutes editor/intent ACK 状态机。B03已实现的是后端会议info/roster，不把当前UI称为ACK-before-typing证据。 |

## Schema/copy/review 的具体插入边界

V012__minutes_evidence.sql:39–46/121–135 分别有 element_set_revision 与 minutes_history 唯一索引；按各自 meeting/revision 最大值分配+1，不等同 meeting r，不推导历史 body↔set 配对。V008:37–48 current root、V012:71–87 candidate、V013:17–42 frozen final 才是真实配对出处。candidate_task 唯一，candidate_pending 索引可发现完整 PENDING；不能把 task SUCCEEDED 当 adopted。

按 V012:5–37,108–135,176–243 的真实 owner/FK：
- 新正文ID、setID、每个element/evidence/directory ownerID都需服务器UUID；每一 evidence 的唯一 target FK 改为新ID。
- locator 仅同meeting完整 typed/null 字段相等才可复用，否则新UUID；每个目录join和evidence经过总映射，保留共享物理locator的多个occurrence。
- accepted enterprise/display/date 三个独立nullable值与原 suggestion.input_id/owner_mention/due_on 分开；目录原input、body原input、原transcript/file/confirmation不变。
- ACTION→OPEN_QUESTION 在删除新图的 ACTION-only children前，验证ordinary intrinsic confirmation仍是原固定来源；不支持/歧义原子拒绝。新图不复制 result_review/result_review_evidence/final credit。
- 选择当前保存目标、全体相关review/evidence和原profiles。Task10以实际 CONCLUSION/ACTION WHOLE SUPPORTED+非空MEETING证据及ACTION_FIELDS CONFIRMED决定必需项；body/OPEN_QUESTION无 blanket gate。review_no 用真实独立 sequence，review写 r+1/clear final，不改creation support_state、不清独立generation debt。

上述都是待实施的写边界；基线没有 MinutesFactsReader/JdbcMinutesFactsReader/MinutesEditorService/MinutesPendingDisposition，路径存在性已从固定提交确认。不能把设计里的 PreparedMinutesGraph/HeldMinutesMutation 等名字标成现有API。

## 一个共享声明、一次全局顺序获取

绑定顺序为 identities→task_subject→meeting→guard/info/roster/publication/final 控制根→**已采用Task3/6 processing domain实际内部序**→以下minutes extension→所有sessions→所有files→所有tasks→所有attempts→external operation/executions→audit/outbox，flush最后：

minutes_template → template_revision → exclusion_revision → sensitive_exclusion → generation_input → minutes_revision → element_set → minutes_element → action_item → action_suggestion → sensitive_detection → background_note → source_locator → sensitive_detection_source → background_note_source → evidence_link → result_review → result_review_evidence → final_result_review → generation_candidate；同表完整PK升序。

候选可早发现，generation_candidate header 的真实锁只能在上列最后域位；不能先锁candidate后回头锁transcript/minutes。metadata-only writer可跳过未用表，full graph operation必须先声明合并完整依赖，一次按序获取。已经持有的精确行需认证，不换顺序重获取。publication header不等于external operation锁。变化/多出父节点/集合溢出中止并到锁外重准备。

必装位置：JdbcMeetingRepository save/confirm；JdbcProcessingIntake.java:106–124（必须在prelockSources/tasks前声明并获取candidate contribution，成功r advance同事务dispose）；Task6 save/marks/restore/新或重新挂接confirmation；Task9 first settings如有候选共存、first-save completion；全部Task10 edits/settings/review/adopt/reject/dispose；Task11 final。纯intent、真replay、未advance acknowledgment、PENDING-only completion/enqueue不dispose。Task11先在旧r检查所有PENDING及完整outstanding generation，再advance；不能先advance清掉阻塞。

S阶段每个父行/实际子行加锁并确认包括空集的完整PK集合，预留UTF8/scalar/UTF16/rows/edges/copies/escaped response/原archive预算后才转移LONGTEXT；A阶段原archives/parse/hash；F阶段新鲜同源gate，完整有界字段/集合比较、DB-only写和实际readback。不得SELECT完大字段才测量，也不得仅靠driver streaming/gap locks/ supplied facts自证。所有生产者和消费者共用同一个合格effective complete-domain budget，不选Task9/10草案里较大的数字单独放行；实际正数/SQL成本/完整集保护仍未测。

## Task6/8/7/9 采用后必须重钉

| 边界 | 此857仅能记录的起点 | 激活前重新钉住的真实内容 |
|---|---|---|
| Task5fix | AfterTask/ImportRuntime/WorkerLoop/单assembly候选 | root最终采用commit+审查SHA；private phase/actual readback、renew drain、RAW实际输出与原成功producer ancestry；此报告不评定修复。 |
| Task6 | processing-only reader/Task3私有scope | saved WORKING/完整marks/唯一confirmation、hashDraft、S/A/F/同gate capture+exact recheck、correction DISABLED_NOT_INSTALLED、原archive/byte权限分离；真实新增version writer全部接PENDING。 |
| Task8 | JdbcExternalOperations 当前有限64候选扫描；V010旧schema | adopted forward reconcile_scan_seq/unique index及真实enumerator/SQL成本/分页状态；>64 held/unregistered/arrival恢复资格；实际新migration/model输出SHA与root资源门。Task10不分配或改DDL。 |
| Task7 | observe(...,Runnable)和普通AfterTask不够 | 私有同issuer/source/holder/purpose/fullspec/gate/execution的一用current/retention writer-validator；真正marker/drain/handoff、receipt-only vs domain、原execution事实、postcommit verified permit recovery。业务r变化唯一放宽，cancel/ACL/lifecycle/publication不放宽；STALE永不adopt。 |
| Task9 | 纯generation计算/不存在的完整minutes reader和candidate writer | 最终adopted graph declaration/capture/complete equality、15profile kinds加所有reached roots、真实settings installer/init、catalog accessor、独立history allocator、完整task/input/candidate关系、candidate readback、共用budget与first-save/copy hook；生成路由/OpenAPI为/api/v1/meetings。 |
| Task11（后续） | V013 publication READY/RUNNING/RECONCILING；B03仅ever_published | 实际authoritative publication/outstanding-work guard接每个mutator/intent/current completion/retention，未知/未安装deny；final prospective hash/frozen review IDs。不得提前用总为false/可选no-op冒充。 |
| Task12 | 占位UI | exact tab原key ACK前禁用typing/IME/paste/drop/a11y/restoration/hydration/autosave；正GET/别tab broadcast不替代ACK；loss ACK原key重试，stale draft保留。 |

上述是实施/激活前必须重新对齐的已有门，不是要求重新做已完成设计或再建立审批链。此857映射不能预定后续最终API签名或声称已关闭facade gate。

## 后续有意义的验证入口（本次未运行）

先在root单独激活的新guarded resources做focused RED/GREEN；以下为实现者应补证据而非预检测试：
1. 意图/完成双序竞争、legacy UNKNOWN、上限、两tab不同key、同key重放/失ACK/stale expected、refresh/restored drafts/放弃编辑；intent不改r/e/debt/final，正guard服务器saved edit才准入。
2. 真实Task5→Task6→Task9 pipeline自动first save与PENDING手工adopt；business变化只由Task7留完整STALE，cancel/撤权/publication无domain effect；HUMAN title保留。
3. 完整copy/remap包含七类及空数组、所有locator occurrence、两种sensitive label/joins、background、accepted每个nullable字段与suggestion、独立body/set历史；改变任何新target不得继承review，unsupported conversion/rebind原子失败。
4. SOURCE_SUPPORT全体相交evidence/WHOLE和ACTION_FIELDS完整accepted tuple；空/漏/extra/foreign/partial/stale/profile缺失、后PARTIAL覆盖同范围SUPPORTED、SPAN不拼WHOLE；最终冻结实际IDs后新review/default不改历史。
5. 所有真实version writers在完整PENDING集上同事务disposition；候选/任务尾页、overflow、变集合、empty-child并发、元数据-before-text SQL trace；不能删除多余候选来“修复”预算。
6. 每个graph row/readback/review membership/receipt/audit/outbox/flush/commit故障回滚；wrong issuer/source/holder/phase/spec/task/attempt/execution、suspend/closed/reuse均拒绝；no double completion。
7. 真HTTP owner/member/outsider/admin四身份、Origin/CSRF/no-store、9007199254740991/2/3与9223372036854775806/token-invalid/equal-dual/unequal-dual；accepted replay在当前config不可用时可恢复且新鲜鉴权。
8. 实际两窗口浏览器与fresh JVM恢复，同DB/old archives/provider state保留；原sources/config/default更换后review/final仍按原链，不用route.fulfill或SQL-seeded happy path替代。

已确认可继承的**入口**：
- 纯单元：HashProfilesTest#reviewSourcesRejectsSupportiveSubsetAndFullContentChangesTargetHash；FinalAndCoverageTest#latestContraryExactScopeWinsAndPartialReviewsNeverGiveWholeCredit；StoredSourceGraphTest#ordinaryCopiedLocalElementNeverAcceptsAttractiveHistoricalInput；SourceAndSupportTest#backgroundOnlyActionsStayUnsupportedAndSuggestionsNeverAcceptIdentity；ConfigBindingsTest#matchingArchiveIdentityCannotSubstituteDifferentRawDefinitionOrExtraNodes；StrictMinutesJsonReaderTest。
- 非reset DB入口：MeetingCreationGuardIT#newCreateHasExactlyOneZeroGuardAndReplayPreservesPositive / #realLegacyCreationReplayAndReadNeverInitializeMissingGuard / #creationReplayReauthorizesWithoutGuardMutation；MeetingConcurrencyIT#actualServiceLocksSortedIdentitiesBeforeRootAndFlushesLast；TranscriptAuthorityReviewIT#genuineWorkerScopesLockReverseOrderedTaskUnionBeforeAnyAttempt；Task05AfterTaskIT#actualQueueScopeRejectsWrongIdentityPhaseSuspendedAndClosedHolder。各fixture/方法仍须在最终采用版重钉，TEST_*不能充当生产pipeline证明。
- TaskFixture.java:14用dataSource而非reset，随机新identity/meeting并保留；不是空数据库隔离保证。ModelPermitIT修改共享capacity，ExternalExecutionIT做全局reconciliation并启动crash probe；不得未经root规划并行塞入整类/整套回归。
- MysqlTestDatabase.reset():11–18会DROP/CREATE整个ama_b01_fixture，禁止本接续/旧fixture上调用。仅dataSource也读取环境并接DB，本次未调用。
- root pom聚合全部模块；integration-tests/pom.xml:9将integration-test+verify绑failsafe。后续精确 -Dtest=Class#method 或 -Dit.test=Class#method，并在对应最终reactor依赖已备妥的root受控命令运行；不推荐裸mvn verify/全量schema IT。不要用clean/覆盖旧target或重定向旧report取代证据；新run目录、命令、实际selected test counts/结果、exit、报告hash由root封存。此处不给“已安全执行”的命令或测试PASS。

## 当前裁定与剩余资格

- **Ruling: 固定857映射，最终采用后重钉 — 理由：Task5唯一作者正在改活跃源码，6/8/7/9尚会改变接口 — 错误代价：把候选签名/旧行号当成最终能力导致错误授权与重复工作。**
- **Ruling: 共享rank优先于Task10旧第60行，候选header在最后minutes域 — 理由：绑定跨任务裁决且Task3/6序不可颠倒 — 错误代价：逆序锁/晚扩集合，破坏并发与完整性。**
- **Ruling: 原设计已有表支持，不新增Task10 DDL或历史配对/acceptedRevision/origin_input列 — 理由：真实V012/V013可表达选定机制 — 错误代价：擅改已用schema/造不可验证历史。**
- **Ruling: 单一不可变factory合并贡献，缺能力fail unavailable — 理由：真实私有issuer/holder不是DTO — 错误代价：public mint/ambient transaction/no-op绕过完整gate。具体签名由最终采用源码决定，不能在本报告虚构。**
- **Ruling: admission/prelock/disposition和outstanding work用同一有效预算 — 理由：防生产者生成消费者无法完整锁定的合法集 — 错误代价：永久PENDING、隐式截断或final漏项；实际minima尚待root资格证据。**
- **Ruling: Task11实际freeze缺失时不声称可激活；不提前造false guard — 理由：V008没有PUBLISHING且existing ever_published检查不覆盖unresolved操作 — 错误代价：外部发布过程中内容改变或迟到candidate覆盖冻结版本。**
- **Ruling: action非null enterprise选人必须消费真实受限identity selector，否则诚实只支持display/null-enterprise — 理由：FK/mention不是选择权限 — 错误代价：错误身份赋值或隐含ACL扩张。**
- **Ruling: 只称准备完成 — 理由：本次无运行时执行，常量/设计PASS/路径存在不是实测 — 错误代价：误报源/事务/浏览器/容量/公司资格。**

没有发现需要本次擅改设计/源码才能继续只读准备的矛盾；真实shared union/private writer、完整minutes reader/producer、共同budget、全部writer集成、Task11 guard和真实行为证据仍为未关闭门。B01 IMM PARTIAL、B04 Q401 UNKNOWN、FUP-B05-CANDIDATE-01及公司/HA/capacity/native-I/O资格不得由本报告推进。

