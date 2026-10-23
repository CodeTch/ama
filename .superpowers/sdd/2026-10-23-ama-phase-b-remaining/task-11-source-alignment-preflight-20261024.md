# Task11 source-alignment preflight — 2026-10-24

状态：只读准备，非设计复审，非实现派工、source adoption、测试通过、runtime/provider/Phase B qualification。唯一源码基线为 `/home/tanren/ama/.worktrees/phase-b-completion` 的固定提交 `11eb54086da95c675862fcf4225821104aa8e87d`；所有源码/schema/已采用设计均用 `git show 11eb540:<path>` 读取，不把活跃文件当 baseline。Task5 fix01 尚待限定复审/采用；本报告不改变顺序 `5fix/review/adopt → 6 → 8 → 7 → 9 → 10 → 11 → 12 → 13`，不重复已完成 Task1–4 或准备工作。

仅新增本报告及同名 `-source-manifest.json`。未修改产品、测试、POM、旧报告、HEAD/index；未运行测试/构建/DB/容器/网络/私有 env，无提交或子代理。`runtime-author-common.md` 仅作为本地派工约束上下文读取，其旧端口/HEAD/权限记载不作为事实或运行授权。

## 1. 设计身份和读取边界

已读 adopted final-publication wrapper、原设计316行、完整 R1 的全部语义（原设计已读相同区段通过 SequenceMatcher 精确行映射复用，所有替换/新增区段逐项读；输出截断部分 R1:224–322 单独完整补读）、93行 closure、原 review、R1 bounded review、raw manifest、plan extract、prepared brief、zero-effect root ruling、shared rank。原 review 结果是 ISSUES FOUND/NEEDS FIXES；**R1 scoped** SPEC PASS/design APPROVED，T11DR-01/02 ADDRESSED、零新 fix-delta finding；不能把两份 review 都写成原设计 PASS。

R1 raw SHA256 `f18ae4734603709284d91345b2909eca9687627c825444fd8cfa06dd49d36ffc`，102624 bytes/405行；原设计 `a8ec962bfee7ed1025a8d0dc113750447d9a8cc95b006b758cc544de900981e8`，69377 bytes/316行。manifest 以原始 git blob 验证其列出的所有 raw SHA；不把历史 author/source hash 冒充当前固定源码 hash。还读 controlled-runtime 全文含 Normative R1、authority-external、Task6/8/9/10 wrappers、Task7 root rulings/attempt clarification。具体 raw SHA、bytes、总行数、实际读区间及搜索范围见清单。

## 2. 固定源实际存在的能力及限制

| 能力 | 固定源证据 | Task11 接缝/限制 |
|---|---|---|
| 原 final/hash 图与 review scope | `ResultHashProfiles.java:106–156`；`ReviewCoverageEvaluator.java:1–32` | 当前 finalData/finalSources 必须找到真实 FINAL_CONFIRMATION 与实际 membership；没有 prospective FinalDraft API。须同算法 factoring、保留旧 stored 验证/金样，不能造 fake final/hash。coverage 仅在 exact kind/target/anchor/hash/profile 中取最高 review_no，不证明数据库闭包、human intent 或 ACTION 完整业务 readiness。 |
| Final/publication 物理模型 | V013全文 | final 确认、冻结 review membership、每会议唯一 publication root、attempt 必须同时有真实 operation/execution、唯一外部文档映射已存在。没有 publication_submission；publication 当前 final/key/destination 可变化，不能从它恢复旧 acceptance。 |
| 持久 marker/UNKNOWN/严格 retry | `JdbcExternalOperations.java:30–175`；`ExternalOperationPort.java:1–25` | beginExternal 在独立 marker 提交后才给实际 token 对象初次调用权；UNKNOWN claim/query 无新 execution。legacy retry 严格固定 input/r/e/fingerprint/logical key/hash，不支持改目的地或改 final。当前 API 仍是旧 Runnable observe；没有 Task7 prepared external session，也没有 replaceZeroEffectPublication/recoverNeverMarked。 |
| 普通 queue 真实 attempt 与私有 completion | `JdbcTaskQueue.java:106–218`；`ConfinedTaskCompletionPolicy.java` | validatedLease 校验真实 unfinished attempt；AfterTask/私有 issuer+holder+phase、write→actual validate→queue finish→最后 flush 可作集成基础，**不是** publication terminal hook。claim exhaustion:116、RETRYABLE exhaustion:191–195、PREPARED termination:208–216 均不更新 publication/submission。初始无 execution 直接返回。 |
| Worker 单次 ordinary dispatch/drain | `WorkerLoop.java:116–201` | ordinary renew JDBC 在 monitor 外；dispatch 禁止新 tick 并有界等待已选 tick；已 dispatch 后不二次 complete。尚无 Task7 MARKING/handoff/send ownership phase；不能直接称完整 external barrier 已实现。 |
| 原 reconciliation/permit | `ReconciliationLoop.java:1–85`；`JdbcExternalOperations.java:66–75`；`JdbcModelPermitPool.java:60–72` | 当前 reconciliation 只扫描最前 LIMIT64，Task8 fair ordinal/reserved sweep 尚非此固定源能力。settled 是 observation 后独立事务验证真实终态，不能 finally release；没有 Task11 本地 recovery maintenance。publication 不是 model call，禁止加假 permit。 |
| 真实权限及已有冻结片段 | `JdbcMeetingAccess.java:57–133`；meeting/file/import guard 下表 | B03 current ACTIVE owner EDIT、confirmed member READ、same issuer/actual holder 基础已存在；EDIT 本身不检查 publication-in-progress。已有 ever_published/PUBLISHED 拒绝，不是 complete-history PublicationWriteGuard。 |
| 既有 API token/契约 | `HttpRevisionTokens.java:1–32`；root `contracts/openapi/api.yaml:1–64` | canonical decimal/dual equality 已有；Task11 仍需 publication revision/execution 等精确 wire normalization、独立 replay-first 及 producer-owned publications.yaml refs。固定源没有 publications/minutes endpoints。 |

实际 tracked main source/根 OpenAPI 全文搜索未找到 KnowledgePublishPort、FinalConfirmationService、PublicationWriteGuard、publication_submission、MinutesPendingDisposition、MeetingMutationDomains、MinutesFactsReader、KNOWLEDGE_PUBLISH_V1、MINUTES_GENERATE_V1 的实现/注册。文件名 `storage/s3/PublicationSource.java` 是 S3 staging 分段字节校验，非知识库发布。搜索缺失只覆盖固定树所列 main Java/根 OpenAPI 和迁移目录，不宣称未知外部服务不存在。

## 3. Final/readiness 与 whole-PENDING/outstanding 集成输入

Task9/10 wrapper 已要求 one complete bounded shared union，当前源码尚不能假定其 helper 已安装。Task11 接续只能消费后来真正采用的 API：Task6 saved/confirmed WORKING 与 disabled-correction capability；Task9 实际 first-settings/template/exclusion/archived RENDER 原点、15类原始 profile/config/task/input closure；Task10 actual saved body/set/全目录/evidence/suggestions/nullable accepted tuples、exact reviews 与 shared PENDING declaration/prelock/disposition。

final/readiness/publication 必须额外声明**全部** generation_input 和 MINUTES_GENERATE_V1/GENERATION_INPUT task 历史、所有旧/current executions，即便 accepted work 尚无 candidate；不能仅看 needs_generation、current pointer 或 candidate_pending。V012:71–105 已有候选与 input 模型、candidate_pending；V010:69–111 已有 task_subject_history 与真实 attempt。按整个 meeting task history 有界计数后过滤，溢出关闭失败。PENDING、独立 generation debt、旧未决 execution 各自保留阻塞；STALE 不替代清债。

Final 在 expected r 的共享 root 下先检查 readiness，再一次性插 final+精确 required/optional review IDs 并 advance r/subject。每 CONCLUSION/ACTION 要 exact WHOLE SUPPORTED、非空 MEETING evidence；ACTION 另外 exact actual nullable ACTION_FIELDS CONFIRMED；BODY/OPEN_QUESTION 没有 blanket support gate。原算法不直接 hash 每个 final header/body字段，因此必须另做 exact frozen graph 与 rendered payload binding；draft/stored 字节等价尚待真实证据。

rank：idempotency prelude → identities → subject/meeting → guard/info/roster/publication/final/**submission（将来）**/mapping/publication_attempt → 已定 Task3/6 transcript 内序 → shared Task9 minutes 内序（candidate 最后）→ sessions/files → **全部 tasks 再全部 attempts** → operations/executions → audit/outbox flush最后。不得重复 source gate、先锁 candidate 再补较低 domain、task 后发现 hook 行。所有 S/F/replay/readback 的 LONGTEXT 先 X-lock 元数据/完整子集保护、length/UTF16/copy预留，再取内容；当前 external helpers :26–27/:35/:68 的 SELECT * 不满足这一未来 Task11 保护协议，需在后来实际 facade composition 中闭合，不能靠现有 query timeout 代替。

## 4. 全 mutator freeze 的实际接入清单（准备，非穷尽 source qualification）

| 固定源实际入口/片段 | 已有检查 | 后续必须接入同一 complete-history guard |
|---|---|---|
| `JdbcMeetingRepository.writable:157–160`（info/roster） | revision + everpublished/PUBLISHED | 所有版本推进前 merged PENDING contribution 与 publication history；replay/read 与新 effect 分开。 |
| `JdbcFileRegistry.writable:682–686`；`JdbcFileUploads.authorize/mutable/writable:370–390` | everpublished/PUBLISHED、epoch/purpose | upload-init、part登记/completion、bind/reference/current file业务提交；在途 transport完成不授予冻结后 attach permission，orphans走普通cleanup。 |
| `JdbcProcessingIntake:95–129` | genuine B04 gate，post-intake r+1、invalidate transcript/final、debt，everpublished/lifecycle/governance | 在现有 domain/task前纳入同一 PENDING/完整 publication declaration，不另开 gate 或晚加行。 |
| `ImportTaskDomainPolicy:81–135`、`JdbcTranscriptAuthority:192–225`、`JdbcProcessingFactsReader:118` | genuine issuer/holder/source/input provenance，current/everpublished片段 | Task5 RAW应用、原始来源引用及所有输出都受 in-progress/UNKNOWN/remote success guard；stale任务退役不恢复 byte authority。 |
| `JdbcProcessingDiscovery:376–379` | 当前 writable projection仅 everpublished/stage/lifecycle/governance | 它是展示观察，后续 safe action必须来自新 guard，不给写权限。 |
| Task6/9/10/11 尚未在基线出现的服务 | 不可当已实现 | save/marks/restore/confirm，settings/init/enqueue/current/retained output，intent/body/set/action/review/adopt/reject/dispose，final/submit/retry/recovery 全部安装。所有将来 direct file/reference/cancel/delete/governance business application 也需真实调用点核对。 |

READY/RUNNING/RECONCILING、PREPARED/RETRY_QUEUED/MARKED/UNKNOWN、任何未决旧轮/未应用远端成功冻结 content/intent/reference/current和retained输出；verified success/ever_published 永久冻结，withdrawn/obsolete不能重开。身份 revoke/disable 和其已有独立 lifecycle fence 可继续，但不解锁。共享 guard 缺失/历史不完整/超预算 fail closed。上表是固定源有针对性的入口映射；Task6/8/7/9/10实现后 root 必须做完整 direct-writer inventory，不能把本表当最终全部调用点证明。

## 5. UNKNOWN、零效果与 durable marker/attempt 风险

- **原正确 reopening 语义保留。** 首次成功前，每个 accepted submission 均有 admitted authoritative provider-zero-effect failure，或真实 sealed LOCAL_NEVER_MARKED decision，且无 mapping/success/UNKNOWN/live work，fresh owner/lifecycle/CAS可显式 edit/refinal/reconfigure/resubmit；内容改变才需新 final，目的地/配置修正且内容不变无需无意义 refinal。同一个 publication UUID、旧 accepted bindings/task/attempt/execution/final/request/事实永久保留。FAILED字符串、timeout/404/absent execution 都不是这一证明。
- **UNKNOWN lookup-only。** marker commit ACK不明、possible send、provider NOT_OBSERVED、provider store mismatch和任何旧未决轮都不重发、不自动新execution、不释放permit。fresh primary complete locking read在原marker TX完成后才可能消除本地commit不确定；没有其结果继续冻结。
- **durable acceptance缺口真实存在。** V008 idempotency仅result_id/hash/status；V013 publication current destination/final可变；attempt又必须有真实operation/execution。初始失败无execution时，旧header不可重建。R1 submission logical proposal是必需后续接缝，当前不能用 hash/current/browser重建或backfill；legacy missing binding UNKNOWN/unavailable。
- **queue终结组合。** 初始 claim exhaustion、exhausted RETRYABLE、ordinary failure/quarantine不能永留READY，也不能靠generic PREPARED termination字符串解锁。需要 constructor-bound KNOWLEDGE_PUBLISH_V1 terminal policy，在任务锁前声明完整额外union、消费实际私有domain/task/attempt phase一次；queue/attempt/local外部state/submission/publication/events同事务rollback。现有 ConfinedTaskCompletionPolicy仅普通准备输出，不能直接当该hook；合法live-owner普通完成与禁止abandon另一个live RUNNING owner必须区分。maintenance不能自动abandon READY/live work。
- **真实本地闭包。** A无execution不造operation/execution/pubattempt；B真实unmarked PREPARED；C真实已本地终结且marker fields仍空的execution。必须排除任何pubattempt/possible-send/success及未决旧轮；恢复赢则延迟marker失败，marker赢则恢复拒绝。标记事实/超时/名字都不能转换成never-marked。实际attempt状态/结束次数及marker fields不清空要专门验证。
- **不可变旧事实/新替换。** legacy retryAfterZeroEffect:147–174完全保留固定输入语义；改logical/destination/r/final需要新的publication-only confined extension。same-key现有operation current hash可作为明确新协议projection，旧execution manifest永不改，old token/replay必须验证其原task/spec/manifest/attempt/final，不能用新current hash。new-key接受阶段只task/submission，V010:119 marked_at NOT NULL禁止假operation；真实marker后才建operation/execution。mixed local/provider历史只向provider传实际accepted predecessor，不伪造本地未发的远端reject。
- **fact-only不是domain writer。** 当前外部 observe保留terminal fact并可能queue STALE/BUSINESS_FENCED；未来Task7负责actual task_attempt原子终态与prepared output，Task11单独fresh-authority local failure。fenced remoteSUCCESS/rawpublication_attempt PENDING须呈现remote成功+application FENCED_MANUAL/manualLocked、lookupPending=false，无mapping假成功/无重发/无“terminal仍可claim”承诺。未提供新的recorded-fact application API。

## 6. Controlled provider authentication/delegation 与 official-document 接缝

固定源码中无 Task11 KB adapter/provider fixture/renderer/delegation/document controller；不能把既有session或Task9未来model bearer视为KB认证。R1:198–216所选机制必须实际实现：每条provider route先服务256-bit bearer，再Ed25519短时actor/purpose/method/resource/requestDigest delegation；session或immutable submission.requester为原actor，私有registered bridge签发；固定client/provider/storeInstance/key/TLS、restart稳定restricted credential custody（秘密不进repo/log/manifest/browser）。PUBLISH还需current genuine owner==原requester及Task7真实send admission；assertion持有或actor JSON不能授权。

RECEIPT_LOOKUP允许已revoked原actor的严格同client/accepted submission/execution事实恢复，不给document/new publish/任意lookup权；无记录只UNKNOWN。DESTINATION与DOCUMENT_READ要求current provider grants。Provider必须持久publication-wide sole-document slot、immutable per-execution request/payload/result/proof journal、proof-CAS，app/provider restart不换空store伪装同identity。后来的旧POST仍返旧不可变receipt，不可生成第二document。

Official view走真正same-origin `/api/v1/meetings/{meetingId}/publications/{publicationId}/document` relay：S fresh app READ+精确mapping/final/lifecycle，outside-root固定provider document route用actual viewer DOCUMENT_READ，F fresh local recheck后输出bounded escaped immutable payload，private/no-store、CSP/nosniff、无credential redirect/arbitrary URL fetch。owner/member真实双grant阳性、分别撤销任一ACL阴性、wrongclient/delegation/store/key/purpose/actor negatives和主动abort transport仍都是HTTP/browser证据门槛。

## 7. root 后续 DDL/model 输入与采用后重钉

**只交逻辑输入，不交正式SQL。** publication_submission当前仍logical proposal；Task8实际schema完成后，root另行独立审查精确columns/collations/nullability/FK RESTRICT/index/CHECK，submission_no溢出、immutable binding及one-way UNDECIDED→NEVER_MARKED affected-count1/exact replay，再独立版本分配/完整字节冻结/root ACK/应用/当前model同步。本报告没有分配、freeze、ACK或apply任一DDL，也没有生成publication_submission SQL。

需要root准备的输入是：真实task PK与publication/final/requester/nullable execution FK parents；每publication正数supported submission_no唯一；原provider/key/destination/hash/content-free ≤64KiB manifest/created_at不可变；真实local decision条件字段与reason vocabulary，null execution只表示真实无execution；bounded undecided scan索引；same-meeting/spec/request/manifest crosscheck由领域证明，FK不够。现有V013无需重写。新binding插入在实际task及父行就绪后是受控new-row例外；旧submission控制rank在final后mapping/pubattempt前。完整immutable/oneway enforcement不能只声称DDL CHECK就已完成。

当前model拥有者路径：`tools/checks/database_design.py`；generated `docs/database/ama-schema-draft.sql`、`docs/database/ama-data-dictionary.md`，以及 `docs/superpowers/specs/2026-10-21-ama-database-design.md` 的ER appendix（generator:503–507/668–673）。实际Task8 ordinal/index会改变基底，之后才能安排Task11 model同步；保留所有历史SQL/design-history及前份原输出，不能把历史导出改成新schema。此处仅读generator，未运行其generate/DB功能。

后续固定依赖表：

1. Task5 fix01限定复审/采用：真实ordinary renewal drain、state-aware attempt、private raw writer/queue facade exact SHA，不以11eb540被本预检读取等价于采用。
2. Task6采用：真实完整saved/confirm/S-A-F/byte-authority split、correctness capability、query/lock/pretransfer budget，及其与PENDING/freeze贡献的实际接口。
3. Task8采用：实际ordinal migration/history及current model、fair sweep独立预算；Task11 local maintenance scan≤64/attempt≤16不占reserved sweep，无新scheduler。
4. Task7采用：actual prepared marker/observation、single external ownership/renew drain handoff、actual queue task_attempt phase、fact-only与business writer分支、postcommit permit durable recovery；不沿用当前旧Runnable路径作为最终权限。
5. Task9/10采用：完整MinutesFactsReader/graph/archive closure、first-settings/render origin、saved/candidate拷贝、exact review、one whole-PENDING helper所有version writer实际callsite、outstanding generation inventory、有效统一budget/共有rank/issuer/DS/holder/gate。
6. 然后root为Task11重钉上述源码/DDL/model raw SHA、merged完整union和all-mutator清单；审查publication-only replacement/local hook的实际可组合性后再派实现。读准备报告或设计PASS不替代该步骤。

尚待真正执行且本次未执行：旧hash vectors及draft↔stored等价、S/A/F row/byte/rank/overflow、全写路径冻结、accepted-generation无candidate、两序marker/local-recovery和ACK uncertainty、全部atomic rollback、immutable旧ack+后来edit/destination变化、authenticated provider proof-CAS/双ACL official view、retained app/provider fresh-process同一document恢复、Task5 drain/Task8公平/Task7 permit恢复、producer-root OpenAPI、native+S3四独立session真实browser链。Company SSO/directory/model/KB、production publish/mail、HA/capacity/native-I/O、B01 IMM PARTIAL与B04 Q401 UNKNOWN没有被此准备关闭。
