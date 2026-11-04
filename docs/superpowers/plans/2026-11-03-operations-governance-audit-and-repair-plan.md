# 运营治理深度分析与修复 Implementation Plan

> **For agentic workers:** 本文为待评审方案，本轮不实施。后续获准执行时使用 `executing-plans`，始终在 `/home/tanren/ama` 的 `main` 分支由同一个 agent 执行；不创建 worktree、不切分支、不启动子 agent、不自动提交或推送。

**Goal:** 补齐语料、声纹、评测、配置与会议主链路的真实闭环，并交付与现有产品一致、可用且精致的治理工作台。

**Architecture:** 保留现有 CAS、JDBC、加密、幂等、任务恢复及配置归档，扩展结构化资源与按权限的查询投影。前端拆分业务模块和详情交互，后端把批准数据与配置快照接入真实处理链路。

**Tech Stack:** Vue 3 / TypeScript / Spring Boot / Java 21 / MySQL / 现有文件服务、配置归档与语音适配器。

**Spec:** [运营治理补全与界面设计提案](../specs/2026-11-03-operations-governance-repair-design.md)。该提案源自现有系统需求和用户本次新增设计授权，尚未宣称实施或验收。

## Global Constraints

- 所有工作在项目根目录 `/home/tanren/ama`、`main` 分支，单 agent；不创建或使用 `.worktrees`，不通过 Git worktree 建立或切换工作树。
- 用户最初要求「先不修改代码」；随后确认不存在额外治理原型/文档，授权补全功能和界面设计。本轮交付分析、方案、设计预览，不执行产品开发。
- 保留已有未提交改动。本轮核查的是当前工作区，不仅是 HEAD；工作区可能继续变化，实施前重新确认相关文件和迁移最大版本。
- 管理员审核权不隐含源会议正文访问权；人员/声纹候选不改变会议 ACL；历史确认稿和发布稿不被全局配置或语料静默改写。
- 样本、模型、指标和页面统计必须有真实来源；原型模拟数据及本次设计样例不得进入运行数据或验收准确率。
- 暂不改变既有邮件入口和投递恢复协议；页面重构不得破坏共享 operations 请求的恢复。

## Review Focus

1. 不同业务入口创建了互不兼容的反馈：T2/T3 必须验证同一申请从提交到审核到生效的完整链路。
2. 负责人替另一人提交声纹但缺少该人的用途授权：T4 必须验证主体授权、最小音频范围及撤销即时生效。
3. 声纹正负样本都错误却被其他评测高分抵消：T5 必须以分类型门槛拒绝该配置。
4. 配置页面已经显示发布成功，实际新任务仍使用旧模型或提示词：T6 必须验证任务快照和真实请求。
5. 共享恢复记录、角色变化和迟到响应污染另一资源：T1/T7 必须验证当前账号、模块和资源绑定及原键恢复。

## 1. 核查结论

**当前实现具备本地受控后端基础，但不具备完整的运营治理产品闭环。** 问题包含已明确的业务断点、页面与数据结构不一致、评测语义不足，以及历史验收范围被过度概括。

不能把这次问题简单归为 CSS 不像，也不能说整个模块没有后端。已有能力应保留并补全，而不是重写。

### 1.1 原始依据事实

- 一期需求文档 1395 行，最后一行即「功能模块六：平台运营与治理」，没有正文。
- HTML 原型 SHA-256：`ab21d0b73f4e48bbf701ee8bca5e958940b61f6e6f680d4ced02de0ce4bab867`。
- 原型 `ownerRoute` 和 `render`（959～968 行）只开放负责人相关路由。浏览器实际访问治理候选路由全部重定向到会议首页。
- 原型残留 `DB.voiceprints`、`DB.voiceRegistered`、`DB.quality` 是数据，不是可操作的治理页面；不能从这些数据声称存在某个完整管理台。
- 原型可直接复用的部分：1638 行说话人命名、1664 行声纹申请、1739 行语料反馈、2140 行个人反馈统一列表。
- 用户本轮已确认没有更多资料并授权补设计，因此不再把缺失原型作为分析或方案交付的阻塞项；新增界面以本次方案评审结果建立基线。

### 1.2 截图中的账号不是管理员

本轮使用独立浏览器上下文登录截图中的测试账号 `1`，服务端返回角色 `USER`、`reviewer=false`，页面有「我的声纹」「语料反馈」。管理员读取数据集、评测、配置和批准语料的接口对该账号均返回 403。

另以 `dev-admin` 登录，角色为 `PLATFORM_ADMIN` + `USER`，四个标签均可见；六类治理 GET 均返回 200，查询到 7 条声纹记录、7 个数据集版本、6 个评测任务，批准语料列表当时为 0。普通账号隐藏管理员功能属于权限行为，不能用「给测试账号加管理员」代替产品修复。

当前截图的「运营治理」标题与本人登记工作区也存在职责表达混淆。新方案将个人入口与管理员工作台明确分开，保留按权限的直接链接处理。

### 1.3 核查期间的工作区变化

浏览器取证后，工作区继续出现其他修改。2026-11-03 19:58（Asia/Shanghai）的复核已发现 `TranscriptFeedbackDialog.vue` 新增六类反馈、原值、建议值与说明，`JdbcFeedback` / `JdbcCorpusFeedback` 新增结构化提交，及未跟踪的 `V069__structured_meeting_feedback.sql`。这些不是本轮分析所作的修改，本轮也未验证该迁移已应用或新链路已验收。

因此 G03 已从「提交表单完全缺失」修订为「提交端已补充，查询与展示尚未接通」；实施时复用现有补充，不重复创建同义字段或迁移。本轮保存了 21 个相关文件的只读快照及 SHA-256，见证据目录中的 `source-snapshot-manifest.json`；早先浏览器截图与 SSR 证据保留其原始观测含义，不能当作后来修改的运行验收。

**20:03 的增量复核又发现以下进展。以下状态优先于差异表中的早期观测，不应再把相关功能统称为「没有代码」。** 增量只读快照共 30 个文件，见 `source-update-manifest.json`；本轮未替这些工作区修改执行迁移、重新部署或写入业务验收。

| 关联项 | 已出现的补充代码 | 方案调整与剩余验收 |
| --- | --- | --- |
| G03/G04/G05 | `FeedbackTableView.vue` 已读取 originalValue/suggestedValue；`JdbcMyFeedbackOverview.java` 已联合语料和会议声纹申请，输出结构化字段；`JdbcFeedback.summaries` 已委托新查询 | 不重写已出现的结构化提交、七列表格和联合查询；验证迁移、API 契约、完整字段往返、计数/分页、详情及历史兼容 |
| G07 | 新增 `JdbcCorpusAdoption.java`，维护采纳/撤销原请求；`OperationsConfiguration` 调用维护任务；适配器调用 `/v1/feedback/corpus`，要求 LOCAL_SIMULATION 回执 | 从「缺少采纳出口」调整为「已有本地受控采纳实现，主链路使用仍需证明」；验证新纠错请求中的实际语料、快照/撤回和旧数据规则，不能用 ADOPTED 回执代替处理效果验收 |
| G08/G09 | `TranscriptEditor.vue` 已调用会议 voiceprint-applications；新增 `MeetingVoiceprintController`、`JdbcMeetingVoiceprintApplications`、`MeetingVoiceSample`，包括授权查询、样本提取、补充/审核历史和批准入库 | 复用这些资源与 V069；核验真实入队、响应恢复、个人列表和审核联调；继续补登记与新会议候选匹配、撤销/过期的完整闭环。LOCAL_SIMULATION 授权不能宣称企业授权服务已验收 |

G15 发布校验、G14 指标语义、G16 运行配置接入等问题在本次增量复核范围内仍未看到对应修复。修复优先级与业务闭环验收目标保持不变。

## 2. 差异与缺陷清单

下表保留本轮初查和 19:58 快照的 22 项发现，便于追溯原问题；20:03 已出现的补充以 1.3 节增量状态为准，不能将全部条目视为仍未动工。P0/P1/P2 为本次修复顺序，不改变原一期需求总表中的功能优先级。行号对应取证时的文件，实时工作区继续变化时查阅快照。

| ID / 顺序 | 已核实事实 | 用户影响 / 修复方向 | 直接依据 |
| --- | --- | --- | --- |
| G01 / P1 | 治理页面由单个 `OperationsPage.vue` 的混合长表单构成，普通/管理员复用，标签仅为本地 ref | 登记、审核、数据集编辑、评测报告混杂；刷新丢当前标签。拆模块、路由持久化、列表 + 详情 | `web/src/features/governance/OperationsPage.vue:5,39,42,48-55` |
| G02 / P1 | 语料页要求手输源会议编号、版本、段 ID、撤回反馈编号 | 业务用户无法自然完成流程，容易版本错误。由会议上下文带入，撤回从记录操作 | 同文件第 30、31、48 行 |
| G03 / P1 | 初查为单 comment；19:58 复核时提交端已新增六类及结构化字段，但 `JdbcFeedback.FIELDS`、summaries、detail 的投影仍为旧字段 | 提交、回执、详情和列表尚未形成完整字段往返；复用已有提交与 V069，补查询/显示/兼容并验证迁移 | `TranscriptFeedbackDialog.vue:8-15,32-34`、`JdbcFeedback.java:12,20-25,104,158`、`V069__structured_meeting_feedback.sql:5-15` |
| G04 / P1 | 个人反馈表「原始值」固定为 `—`，建议列显示整个 comment | 表格外观保留了七列，但数据表达不满足原型 | `FeedbackTableView.vue:15-16`；本轮 SSR 读回第二格为 `—` |
| G05 / P1 | `/feedback/summaries` 只查 feedback，没有声纹申请联合查询 | 原型中的「语料 + 声纹」统一跟踪和计数不成立 | `JdbcFeedback.java:92-105` 对照原型 2142～2145 行 |
| G06 / P1 | 两套反馈提交并存：`/api/v1/feedback` 无源绑定，`/meetings/{id}/feedback` 有绑定，后者又兼容旧/新两种 payload | 同样显示通过的历史/通用反馈可能永远不进入批准消费。统一新提交语义，历史明确标识 | `useFeedbackSubmission.ts:28`、`JdbcCorpusFeedback.java:13-32` |
| G07 / P1 | 已批准列表只在治理 HTTP 控制器中被调用，未找到处理链路消费者；该列表也仍只选旧 category/comment | 审核不会实际提升后续企业实体纠错。增加有效语料版本、结构化投影、上下文适配和任务来源证明 | `JdbcCorpusFeedback.java:32`、`OperationsController.java:42`、`SpeechPipeline.java:39` |
| G08 / P1 | 声纹勾选提交后仍只执行命名，随后固定提示授权服务不可用/未提交 | 原型按钮存在但没有声纹审核申请入队，这是明确功能断点 | `TranscriptEditor.vue:159-194`，尤其 184、191～193 行；需求 462～464 行 |
| G09 / P1 | 后端登记主体固定为 actor，无会议/片段/提交人资源；没有 needs-more 状态和样本补充历史 | 本人登记不能替代负责人经授权提交说话人样本；审核与跟踪信息不足 | `JdbcGovernance.java:17-35`、`V062__operations_governance_mail.sql:23-45` |
| G10 / P1 | 匹配只有管理员手工上传接口；实际转写生成并保存向量，却不查询有效声纹库 | 登记后不能在新会议形成实名候选。增加逐说话人片段匹配，保持人工确认 | `JdbcGovernance.java:41-43`、`SpeechMediaPipeline.java:52-58`、`SpeechPipeline.java:33-43` |
| G11 / P1 | 声纹页不显示审核意见、历史、真实姓名/部门；过期清密文不改变显示 state | 管理员难以判断授权与审核状态，过期条目可能仍展示 APPROVED | `OperationsPage.vue:47`、`JdbcGovernance.java:15,75` |
| G12 / P1 | 治理与主链路模型标识不同：harness-embed-v1 与 harness-fixture-embedding-v1 | 不能把两个字符串相近的模型视为同一向量空间；需模型目录/指纹和版本兼容验证 | `OperationsPage.vue:23`、`ControlledSpeechProfile.java:31-33` |
| G13 / P1 | 质量模型/提示词被前后端固定；纪要调用实际使用 deepseek-v4-flash，却报告通用 embedding 配置名 | 缺少可选择版本、实际分类型模型来源和可复现对比 | `OperationsPage.vue:23,50`、`JdbcGovernance.java:51`、`GovernanceHttpProvider.java:31-37` |
| G14 / P1 | 说话人评测只比较人数文本；纪要用编辑距离分数；没有实体/证据类型 | 不能把这些分数当 DER、纪要可用率或企业实体改进。扩展参考标注和分类型指标 | `GovernanceHttpProvider.java:34-37`、`EvaluationService.java:6`、`GovernanceContracts.java:6` |
| G15 / P0 | 发布校验用所有样本总均分 ≥0.8，并只检查声纹正负类型出现过 | 两个声纹配对全错，8 个其他样本满分，仍可发布阈值。本轮无副作用纯 Java 调用已复现 | `ConfigurationReleaseService.java:5-7`；本轮 policy probe |
| G16 / P1 | 配置页只有当前三字段与回滚；模型发布只写 governance_configuration；主语音/纪要链路未读取该表 | 不是完整的系统配置中心。需要与实际配置归档、任务快照接通 | `OperationsPage.vue:52`、`JdbcGovernance.java:67-71`、`ControlledSpeechProfile.java:26-36` |
| G17 / P1 | 评测发起、人工复核、发布均可由同一管理员完成，没有独立审批资源 | 不满足既有 D-11 的独立审批方向。新增候选、审批和证据绑定 | `JdbcGovernance.java:50,66-68`；`docs/contracts/product-decisions.md:21,38` |
| G18 / P1 | 七类写入响应接受任意不超过 12 个键的对象，`{}` 也通过 | 空响应可能被当成「已保存/已发布」。全部写回执闭合校验、核对资源和原请求，再回读 | `web/src/shared/api/operations-wire.ts:12-18`；本轮 7 路径探针均为 true |
| G19 / P2 | voiceprints/datasets/evaluations/corpus/history 静默 LIMIT 100，控制器拒绝任何查询参数 | 无完整分页、筛选、总数；更多记录不可达。扩展有界分页查询 | `JdbcGovernance.java:40,49,53,70`、`JdbcCorpusFeedback.java:32`、`OperationsController.java:52` |
| G20 / P2 | 全页共用一个 reason；加载串联 voiceprints 后再 Promise.all 读取全部管理员资源 | 审核原因跨模块残留；一个读接口失败影响其他资源。独立 action 状态与按模块加载 | `OperationsPage.vue:7,15,20,27-29` |
| G21 / P2 | 同一 operations 未知请求跨治理、反馈、邮件共享；403 会置 available=false，重新检查登录未显式重建同账号 scope | 存在同账号权限恢复后页面不能恢复的路径风险，需要专项用例，不将它声称为本轮已完成线上复现 | `useOperations.ts:21-26`、`OperationsRecovery.vue:4`、`OperationsPage.vue:14`、`application.ts:85-86` |
| G22 / P2 | 评测逐条暂存只显示条数、无法查看/移除单条；批量 Base64 与浏览器恢复存储容量不一致 | 大样本集提交和纠错体验欠缺，需样本资源引用、体积预检与未发送草稿可编辑恢复 | `OperationsPage.vue:22,49`、`useOperations.ts:18-23`、`scoped-private-journal.ts:18,41-46` |

G07/G10/G16 是通过全仓引用搜索和实际主链路代码确定的接入缺口，不声称本轮运行过新会议模型任务。G21 是静态可达路径风险。G15/G18/G04 有本轮隔离纯逻辑探针佐证。

## 3. 可保留的真实实现

- 当前数据库身份和管理员角色校验、CSRF/幂等请求、未知响应恢复和换账号隔离。
- 反馈的通过/驳回/需要补充、补充历史、撤销审核及最小披露。问题是结构和跨模块闭环不足，并非完全无审核功能。
- 精确源修订/片段绑定、批准有效性过滤、主动撤回。不能以它们存在就认定已有企业语料处理消费。
- 本人声纹同意与期限、规范 WAV/非静音校验、AES-GCM 加密、独立管理员审核、模型/维度过滤、撤销与过期清密文。
- 不可变数据集、持久评测租约和逐样本结果、失败计入分母、人工意见、追加配置修订与回滚。
- 实际 CAS 和本地受控接口已能正常读取。不要为了界面美化削弱上述能力或替换为前端模拟成功。

## 4. 根因

1. **管理端产品规格一直缺失。** 原始模块六无正文，现实现采用后补的本地技术规则，没有形成完整的角色、页面、字段与跨模块流程设计。
2. **按接口集合搭页面。** 技术编号、原始状态码和内部模型名直接进入业务表单，导致页面虽然能调用接口，却不符合使用者的操作路径。
3. **主链路和治理各自完成了局部。** 会议中有申请按钮，治理中有登记表；审批能记录结论，处理任务却没消费该结论；配置有发布表，真实运行仍用另一套配置。
4. **测试覆盖的是现有实现契约。** 例如 feedback-table-view 测试验证七列存在，未验证原值真实显示、声纹申请合并；治理测试主要证明幂等和权限，未覆盖两个声纹样本全错的混合评测。
5. **验收结论口径过宽。** `docs/acceptance/e/summary.md` 的 COMPLETE_LOCAL_CONTROLLED 有真实局部证据，但不能等价于治理全功能完成、全产品原型一致或真实模型质量合格。视觉文档本身已说明没有原型管理页和像素一致结论。

## 5. 实施任务与交付顺序

### T0：先阻止错误的声纹阈值发布

**修改：** `backend/core/src/main/java/ama/governance/ConfigurationReleaseService.java`、`backend/adapters/src/main/java/ama/governance/JdbcGovernance.java` 及对应发布测试。

- [ ] 将 G15 的纯逻辑复现固定为回归用例：声纹两条全错、其他八条满分，发布必须被拒绝。
- [ ] 不再接受混合总均分作为声纹阈值证据；现有报告不能证明声纹分组独立达标时，返回「缺少合格声纹评测证据」。T5 补齐分类型报告和事先冻结的发布规则后，接通正常发布资格计算。
- [ ] 验证普通无阈值配置、既有只读查询、原请求幂等和历史配置读取不受此修复破坏。不得仅隐藏前端发布按钮。

### T1：统一权限能力、回执和模块入口

**修改：** `web/src/main.ts`、`web/src/shared/WorkspaceTopbar.vue`、`web/src/features/governance/OperationsPage.vue`、`useOperations.ts`、`OperationsRecovery.vue`、`web/src/shared/api/operations-wire.ts`、`backend/api/src/main/java/ama/api/OperationsController.java`、`contracts/openapi/operations.yaml`。

**新增：** `web/src/features/governance/OperationsShell.vue`、`operations-api.ts`、`operations-state.ts`。提供四个子路由；旧 `/operations` 按 capability 定向，保留历史原请求。

**接口：** capabilities 输出按动作的权限和受控能力；每个 POST 返回具名闭合回执，绑定目标 ID、修订和原操作；列表返回 `{items,counts,nextCursor}`，limit 20/50、最大 100。

- [ ] 测试 `emptyReceiptNeverAcknowledgesGovernanceMutation`：七类空回执一律不能触发成功提示或清除原请求。
- [ ] 测试 `sameActorPermissionRecoveryRebuildsScope`、`lateReadCannotReplaceCurrentTab`、`pendingMailSurvivesGovernanceNavigation`。
- [ ] 补齐响应 schema / 资源绑定，按动作隔离表单，按模块加载，角色恢复时显式同步作用域。
- [ ] 验证 USER 直访管理员接口 403；管理员访问无权会议仍 403；路由/筛选刷新可恢复；已有邮件丢 ACK 测试保持通过。

### T2：结构化反馈、统一个人跟踪与审核

**修改：** `TranscriptFeedbackDialog.vue`、`FeedbackPage.vue`、`FeedbackTableView.vue`、`useFeedbackSubmission.ts`、`web/src/shared/api/feedback.ts`、`backend/adapters/src/main/java/ama/meeting/JdbcFeedback.java`、`backend/adapters/src/main/java/ama/governance/JdbcCorpusFeedback.java`、`contracts/openapi/feedback.yaml`。

**新增：** `web/src/features/governance/corpus/CorpusGovernancePage.vue`、`CorpusReviewDrawer.vue`，后端结构化反馈投影。先核对并复用工作区已有 V069；只有仍缺少的治理资源才追加迁移。

**接口：** 保留工作区新提交的 `feedbackType,originalValue,suggestedValue,note`，旧 `category/comment` 只作兼容；来源继续由 meetingId、sourceRevisionToken、segmentIds 绑定，服务端固定 transcriptRevisionId。查询投影统一输出 sourceRef、`kind=CORPUS|VOICEPRINT`、reviewState、usageState 和源摘要。不得无必要再次改名或要求用户手填版本。

- [ ] 测试六类反馈字段往返、原值/建议分别显示、来源不手填、语料与声纹统一分页/计数。
- [ ] 测试旧非结构化记录保留原 comment，不能无依据拆解或成为全局有效条目。
- [ ] 实现提交、详情、需要补充、审核和撤回；审核状态与使用状态分别呈现。
- [ ] 验证普通本人跟踪、管理员最小披露和两个管理员同修订冲突；来源失效不隐藏本人处理历史。

### T3：批准语料接入实际纠错链路

**修改：** `JdbcCorpusFeedback.java`、已新增的 `JdbcCorpusAdoption.java`、`backend/core/src/main/java/ama/model/SpeechModelPort.java`、`backend/core/src/main/java/ama/transcript/SpeechPipeline.java`、`backend/adapters/src/main/java/ama/model/HttpSpeechModelPort.java`、`backend/adapters/src/main/java/ama/task/SpeechMediaPipeline.java`、相关处理输入/检查点契约。

**新增：** `backend/core/src/main/java/ama/governance/ApprovedCorpusPort.java`、`backend/adapters/src/main/java/ama/governance/JdbcApprovedCorpus.java`，用于任务消费已有批准/采纳事实；语料批准修订、有效版本和任务快照只补仍缺少的字段。不得另建与 `JdbcCorpusAdoption` 冲突的第二套采纳权威。

**接口：** `CorpusSnapshot {id,revision,entries}`，每条包含明确类型、原值、建议值、批准修订和来源绑定；处理任务消费此快照，并在外部调用与采用候选前校验当前授权有效性。

- [ ] 测试 `approvedEntityAppearsInNextCorrectionContext` 与 `withdrawnEntityNeverAppearsInNewModelRequest`。
- [ ] 实现审核通过产生不可变有效版本，并把有效条目传入现有 entities 纠错上下文；不修改 ASR 裸 WAV 接口。
- [ ] 测试源删除/撤回、提交人停用、版本变化和缓存场景；历史确认稿/发布稿哈希保持不变。
- [ ] 真实受控外部请求证明有语料/无语料输入差异；不能仅断言 GET approved 返回了一行。

### T4：会议声纹申请、主体授权与候选匹配

**修改：** `TranscriptEditor.vue`、`SpeakerNamingDialog.vue`、`JdbcGovernance.java`、`VoiceprintService.java`、`GovernanceAudio.java`，以及工作区已新增的 `JdbcMeetingVoiceprintApplications.java`、`MeetingVoiceSample.java`、`MeetingVoiceprintController.java`、语音主链路及相关契约。

**新增：** `web/src/features/governance/voiceprints/VoiceprintGovernancePage.vue`、`VoiceprintReviewDrawer.vue`、`MyVoiceprintsPage.vue`。后端复用已出现的 Application/History 和样本提取，补足主体授权生命周期与消费能力；先核对 V069，不重复建表。

**接口：** 本人授权资源与申请分开。会议申请提交 `subjectEnterpriseId,consentId,sourceRef,sampleRanges,expectedRevisionToken`；服务端提取可授权片段，不接受用户自行声称拥有另一主体的授权。候选返回 subjectId、score、modelFingerprint、thresholdRevision 和需人工确认标志，不包含向量。

- [ ] 测试命名成功且申请入队、申请失败仍保留命名、同请求重放不重复申请。
- [ ] 测试无授权/无录音/外部未核验主体/过短片段拒绝；需要补充形成新版本；申请进入个人统一列表。
- [ ] 接通逐说话人样本提取、授权核验、审核历史和有效声纹查询；统一模型指纹，不伪造模型版本兼容。
- [ ] 测试新会议出现候选；撤销/过期/主体停用后不再出现；不同模型维度隔离；会议 ACL 不变。

### T5：数据集管理与可信评测

**修改：** `GovernanceContracts.java`、`EvaluationService.java`、`ConfigurationReleaseService.java`、`GovernanceHttpProvider.java`、`JdbcGovernance.java`、`OperationsConfiguration.java`、OpenAPI 数据集/评测模型。

**新增：** `web/src/features/governance/evaluations/EvaluationPage.vue`、`DatasetDialog.vue`、`EvaluationReport.vue`、`EvaluationCompare.vue`；分类型指标计算器与样本文件引用。

**接口：** 报告输出每类型 metrics、总/成功/失败/未处理数、实际模型/提示词身份、参考答案和机器结果。`releaseEligibility` 由服务端根据评测前冻结的门槛逐项计算，不根据通用平均分计算。

- [ ] 首先保留 G15 的失败用例：声纹 0/2 正确、其他 8/8 正确，阈值发布必须被拒绝。
- [ ] 测试相同说话人数但时间/身份分配错误不会得到完美 DER；纪要文本相似不等于事实一致。
- [ ] 实现 ASR、分离、声纹、实体、纪要、证据各类型可解释指标；未提供足够参考标注的报告标为观察，禁止用于发布。
- [ ] 补数据集查看/逐条移除/版本复制，采用受控样本引用，精确展示任务进度、失败与重试历史。
- [ ] 验证真实版本绑定、同数据集版本对比、超时保留、逐样本配额与重启恢复。

### T6：候选配置、独立审批及运行生效

**修改：** `ConfigurationReleaseService.java`、`JdbcGovernance.java`、`ControlledSpeechProfile.java`、`backend/adapters/src/main/java/ama/transcript/JdbcMinutesGenerationPreparation.java`、现有配置归档读取/写入适配器。

**新增：** `web/src/features/governance/configuration/SystemConfigurationPage.vue`、`ConfigurationDiff.vue`、`ConfigurationReleaseDialog.vue`；配置候选/审批/审计投影与运行快照桥接。

**接口：** 候选包含已安装配置引用、完整 contentHash、作用范围、验收规则；审批绑定候选 hash 和 evaluationId；发布绑定 expectedRevisionToken；回滚返回新的修订。

- [ ] 测试作者自审拒绝、候选修改使旧审批失效、失败/不匹配评测拒绝发布。
- [ ] 接通受控模型目录、差异预览、独立审批、发布和回滚；保留地址/凭证在运维配置。
- [ ] 测试发布后新语音/纪要任务实际使用新配置；在途任务的原输入指纹不变；回滚后新任务使用目标配置的新发布修订。
- [ ] 对归档写入/指针更新中断做恢复测试，避免数据库显示新版本而归档不可读；未就绪不能显示已生效。

### T7：完成视觉重构、体验和验收

**修改：** 上述四模块组件、`web/src/shared/styles/tokens.css` 的必要扩展、治理局部样式；全局已有会议页面样式谨慎复用，避免视觉回归。

**新增：** `web/tests/e2e/operations-governance.spec.ts`、`operations-corpus-flow.spec.ts`、`operations-voiceprint-flow.spec.ts`、`operations-configuration-flow.spec.ts` 及评审后冻结的治理视觉基线。

- [ ] 按设计提案实现顶栏、页面层级、查询列表、紧凑状态、右侧详情/弹窗、整页评测报告；数据样例仅用于隔离视觉夹具。
- [ ] 覆盖四模块的加载/空/正常/错误/失权/冲突/未知结果，以及 1440×900、1024×768；检查全部字段和关键操作，不只比较顶栏或无横向溢出。
- [ ] 执行三个完整闭环：语料生效与撤回、声纹登记与新会议候选、评测审批发布与回滚。
- [ ] 适用 Java、MySQL 集成、Web 单元、类型、OpenAPI 和构建通过后，按真实范围更新验收文档；不覆盖旧失败报告，不把受控验收改称企业质量达标。

### 数据库迁移安排

本次检查期间工作区先后出现 V065～V069，最新复核包含 `V069__structured_meeting_feedback.sql`。本方案不预占迁移号，执行时读取最新最大版本并核实应用状态；复用已有结构化反馈字段，再为仍缺少的语料版本、声纹申请/授权历史、评测样本/指标、配置审批等分别追加迁移。不得改写已应用的迁移，也不得照旧数据库草案重建同义表。

迁移必须兼容现有 feedback、governed_voiceprint、governance_dataset/evaluation/configuration 及历史命令回执。历史数据缺字段时标记不可证明的状态，不编造姓名、原值、授权或模型来源。

## 6. 本轮验证、证据和限制

证据目录：`.tools/local-app/operations-readonly-audit-20261103/`。

| 核查 | 本轮结果 |
| --- | --- |
| 项目位置和分支 | `/home/tanren/ama` / `main` |
| 附件 | 从 WSL 路径读取用户截图，确认其为当前普通账号的运营治理页面 |
| 原型浏览器核查 | 4 个治理候选路由全部回会议首页；保存个人反馈原型截图 |
| 当前页面与 API | 账号 1、管理员独立真实登录；12 次治理 GET；14 张实际页面截图 + 1 张原型截图；JS 页面错误 0，业务写请求 0 |
| 两种桌面 | 四模块在授权范围内均读取；全部已捕获页面 documentWidth 等于视口宽度。仅证明未出现全页横向溢出，不证明布局优质或功能完整 |
| 既有单元测试 | operations 和 feedback 六个测试文件共 18 项通过，日志 `unit-tests.log` |
| SSR 首次运行 | 从根目录直接运行包含 FeedbackTableView 的测试时 18 通过、1 个因 Vite 默认根路径无法定位组件失败；不当作产品失败或隐瞒 |
| SSR 只读重放 | 显式 web root、独立缓存和禁用 HMR 后渲染成功；确认七列和原值固定 `—`。见 `feedback-ssr-confirmed.json`。中间一次探针有 HMR 端口/依赖扫描关闭警告，已通过独立只读配置排除 |
| 写回执探针 | 7 类写入响应的 `{}` 全部被当前校验器接受，见 `frontend-probes.json` 的 schemaGaps；其中初次字符串匹配的原值检测为 false，已被上述正确解析 td 的 SSR 结果更正 |
| 阈值纯逻辑复现 | 从 19:58 源快照在证据目录独立编译两个纯逻辑类，再调用 `ConfigurationReleaseService.validate`：voice 0/2、其他 8/8、总均分 0.8 的配置被接受。见 `threshold-policy-probe.log`；未调用发布 API，也未写库 |
| 工作区后续变化 | 19:58 固定 21 个源文件快照，20:03 又固定 30 个文件增量快照；结构化反馈/统一查询、会议声纹申请和本地语料采纳已有新增，详见 1.3。本轮没有实施这些产品修改，也未声称其通过运行验证 |
| 独立设计预览 | 最终版本共 41 项检查通过：四模块/身份切换、七列个人反馈、筛选、详情、审核原因、本地状态演示及多宽度布局；1440/1024/736/320 无整页横向溢出、JS 错误 0、业务 API 请求 0。见 `design-preview-validation-v2.json`；不是产品功能验收 |

未执行：真实登记、审核、撤回、评测创建、配置发布/回滚、邮件发送、业务数据库写入、迁移、产品构建/重新部署和全量回归。现有记录仅通过 GET 查看。本轮保存的是提案、核查产物和独立界面预览，不是产品改动。

## 7. 自查与完成标准

- 四类一期治理能力均有任务映射；会议内语料/声纹入口与个人跟踪没有被遗漏。
- 原型明确部分按原语义修复；无原型的管理端依据本次新增设计提案，不声称原型存在。
- 已有后端、未接入链路、已复现缺陷、静态风险、拟新增设计分别列明。
- T0→T1→T2→T3→T4→T5→T6→T7 为建议单 agent 顺序；先修错误发布门禁，再建立共用类型与权限，随后每个闭环单独验收。
- 单元测试通过、控制器存在、页面按钮存在、截图无溢出，都不能替代业务链路完成标准。
- 本轮结束条件是用户拿到可审阅的完整方案；只有获准开始实施后，才将上述未完成复选框作为开发任务执行。
