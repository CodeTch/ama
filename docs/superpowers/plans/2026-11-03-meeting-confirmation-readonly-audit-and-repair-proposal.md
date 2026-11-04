# 会议确认：本轮只读审计与修复方案

日期：2026-11-03。状态：分析完成，修复未实施。

本轮请求为“全面深度分析，严格按照原型网页和功能设计文档给出具体修复方案，先不修改代码”。在 `/home/tanren/ama` 的 `main` 分支、单 agent 下完成；未创建分支/worktree，未修改产品代码、测试、接口契约、原型或数据库，未提交、部署、执行真实最终确认或发布。只保存分析文档及本轮本地取证文件。

本报告独立使用 `.tools/local-app/meeting-confirmation-audit-20261103/` 的证据。项目中另一份同日 `meeting-confirmation-gap-analysis-and-repair-plan.md` 出现了不同附件、测试数量及证据目录；保留该文件，本报告不借用其中未由本轮验证的实验结论。

## 1. 分析依据和方法

### 1.1 产品依据

1. [原始原型](/home/tanren/ama/designDocs/智能会议助手1021版.html)，SHA-256 `ab21d0b73f4e48bbf701ee8bca5e958940b61f6e6f680d4ced02de0ce4bab867`。
2. [一期需求说明](/home/tanren/ama/designDocs/智能会议助手一期需求说明文档.md)：模块一会议信息、保存/名单授权与离开保护；模块三已保存纪要和来源；模块四最终确认、检查清单、确认失效与发布后只读。SHA-256 `e5b11f49373418e7b4ea9b74071fed035f320612a4783de075980c13223cf783`。
3. [项目既有产品裁决](/home/tanren/ama/docs/contracts/product-decisions.md:13)：D-03 日期可选、D-08 发布成功只读、D-21 结论/行动项人工核对；与原始需求差异分别记录。历史文件中的执行指令不作为本轮修改或发布授权。
4. 本轮附件：`/mnt/c/Users/LING/AppData/Local/Temp/codex-clipboard-3a3fa0a9-1241-4349-851b-d2e91f066ca0.png`。已成功读取。

### 1.2 方法与边界

- 阅读正式路由、组件、composable、API 客户端、OpenAPI、服务端 readiness、最终快照事务、会议信息/名单更新及相关测试。
- 原型通过浏览器可见交互进入确认页和内部/外部人员弹窗，采集 1440×900、1024×768；没有改写原型渲染函数。
- 当前应用使用本地既有账号/会议，在浏览器中只读获取数据并操作打开弹窗、步骤切换和未保存表单。拦截业务写请求；实际观测未触发业务写请求。
- 使用真实 `request()` 和 `useCommand()`，仅替换 fetch 返回受控错误响应，验证客户端错误分类；真实网络请求为 0。
- 已运行两个既有单测文件，共 5 项通过。未运行全量前后端测试，也未执行最终确认、名单授权、发布或真实并发写实验。
- “浏览器复现”“源码确认”“受控响应验证”“待专项验证”分别标注，不能互相替代。

## 2. 结论与附件现象

当前已经具备双栏布局、信息编辑、人员选择、正文预览、固定底栏以及真实最终确认接口。主要缺口是“看见问题 → 定位处理 → 回读复核 → 最终确认 → 未发布修改后重新确认”没有完整贯通；不能以主框架近似原型或少数正常态截图通过作为功能验收完成。

### 2.1 附件会议不是已有纪要却被随意锁住

附件会议 `cc866694-f95d-46f4-a757-2965ad334132` 的有效只读接口返回：

| 项目 | 结果 |
|---|---|
| 转写确认 | 已有确认记录 `baba0dde-80e5-4fbd-9e10-b3f59a396b25` |
| 已保存纪要 | `minutesRevisionId=null`、`elementSetId=null`、`savedGenerationInputId=null` |
| 任务 | 观察到成功的转写导入任务，未观察到纪要生成任务 |
| 最终确认 readiness | `ready=false` |
| 阻断 | `BODY_REQUIRED`、`ELEMENT_SET_REQUIRED`、`GENERATION_REQUIRED`、`SETTINGS_REQUIRED`、`MEETING_INFO_REQUIRED`、`ROSTER_CONFIRMATION_REQUIRED` |

重新打开并等待后，“正在读取已保存纪要…”结束，出现“生成完整纪要”，会议确认仍不可进入。**本轮没有复现永久卡在加载中的现象。** 此样本尚无已保存纪要，不能通过强行启用第 4 步解决。若用户预期转写后自动生成，则需单独验证自动生成触发、模板/初始设置、任务创建与失败反馈；当前证据不足以断言某个自动生成函数就是根因。

### 2.2 用已有成果的真实样本核对确认页

另用既有会议 `f5824b8f-c499-43ff-8f4b-8526b7f6edea`：有已保存正文和要素，readiness 含 5 个必需审阅，1 个满足、4 个待处理，阻断为名单未确认及审阅未完成。正常进入真实确认页，确认了：

- 检查弹窗列出待完成条目，但没有“去处理”或条目定位动作。
- 空参会人区域没有原型的“暂未添加参会人”。
- 修改名称但不保存，点击“返回纪要”没有离开确认提示；再返回确认页，草稿仍保留。因此证实的是**缺少离开提示/保护流程**，不是“已经丢失草稿”。
- 本轮浏览器观测无页面脚本错误、无业务写请求。

### 2.3 不能夸大成整个布局全部错误

主栏比例 `.9fr / 1.3fr`、16px 间距和固定底栏已有原型基础。1440 视口实际双栏宽约 562.9/813.1px；1024 视口约 392.7/567.3px。本轮没有做同内容像素差验收，也不宣称零像素差。

原型末尾存在多层同名函数覆盖：第 2623 行 `acFinalPage` 不是最终全部行为；第 2848 行字段覆盖、第 2874 行头部覆盖、第 3137–3139 行固定底栏和原节点移除必须一起看。最终原型在无问题时移除检查卡，有问题时保留异常区；当前“点击底栏提示打开检查弹窗”不是该原型的原有实现。

当前共享顶部导航涉及其他已有改动，不应为本页修复随意整体回退。正文使用纯文本预览本身也不能直接判错：原型同样对正文作转义展示。缺口在成果/来源核对能力，不是擅自换一个 Markdown 阅读器。

## 3. 差异矩阵

P0：确认、恢复或状态边界优先修复；P1：需求功能及交互闭环；P2：显示和细节。优先级不表示所有项都已经通过真实写操作复现。

| 编号 | 优先级 / 证据 | 明确差异及影响 | 修复方向 |
|---|---|---|---|
| MC01 | P0 / 真实浏览器、源码 | 检查项只有“全文来源/行动字段·待完成”，没有需求规定的“去处理”；用户不知道如何完成被要求的审阅。 | 使用已有目标 ID，定位对应要素、来源、转写问题、候选或设置；处理后回读服务器结果。 |
| MC02 | P1 / 源码、需求 | 聚合 blockers + required 列表没有完整对应需求十二类检查的逐项状态、原因及处理目标。 | 建立需求检查映射，区分失败、通过、读取失败和无需处理；缺少上下文时扩展契约，不能本地推测通过。 |
| MC03 | P1 / 源码、真实页面 | 成果预览仅 `title + body`，没有确认页所需的结论、行动、风险、待确认、分歧、背景引用与来源核对入口。 | 复用现有要素和来源查看能力，在原型既有预览/异常区内接通；不自行增加七个新卡片或新工作台。 |
| MC04 | P0 / 源码 | `p.final.confirmation` 一存在就卸载信息编辑器并禁用顶部保存；把“已最终确认”当成“已发布只读”。 | 未发布且权限允许时仍可修改；保存实质修改使当前 final 失效，保留历史。发布执行/成功等写限制仍按真实状态控制。 |
| MC05 | P0 / 真实浏览器、源码 | 嵌入确认页的信息 dirty 没有接入父级离开守卫；实测返回纪要没有提示。 | 向父级贯通 dirty、pending、conflict 和 beforeLeave，覆盖步骤切换、返回列表、后退/刷新。保留原型保存/放弃/继续编辑语义。 |
| MC06 | P0 / 受控响应验证、源码 | 最终确认明确的 409/422 被误归为 `UNCERTAIN`，不能进入既有 reset 路径；旧请求会持续待恢复。 | 增加最终确认/发布专属错误契约，保留确定拒绝与真正未知结果区别，不粗暴把所有 4xx 当安全重置。 |
| MC07 | P1 / 源码、产品依据 | 前端强制日期必填，后端不要求，需求只指定名称/分类必填，D-03 已记录日期可选。 | 按需求及已采纳 D-03 对齐可空日期；填写时仍校验合法日期，移除错误星标/文案，不用创建时间代填。 |
| MC08 | P1 / 源码 | 必填校验只返回字符串，没有字段级错误、滚动/聚焦目标。 | 将名称/分类校验结果绑定原字段，保留所有其他输入；不能只在顶部显示一段错误。 |
| MC09 | P1 / 前后端源码 | 分类 AI 建议未贯通：编辑器只有 NAME/SUMMARY 槽，类型/表约束也只有这两类；分类返回 SELECT_REQUIRED。 | 补真实分类建议来源、字典稳定 ID、采纳/拒绝和不可匹配状态；需要契约/服务/前向迁移，不能只画静态建议行。 |
| MC10 | P1 / 源码、原型 | 名称/摘要建议已有实现，但任何 dirty 都阻止采纳/拒绝；处理后原型的已采纳/已拒绝状态没有保留。 | 保留逐字段决定和决定状态，安全合并人工草稿，迟到建议不覆盖人工值；不隐式保存整份信息或名单授权。 |
| MC11 | P2 / 真实浏览器、源码 | 未确认空名单区域空白；已确认只读分支给参会人加必填星标，已确认空名单缺少清晰空态。 | 沿用原型空态，区分未确认、确认空名单、名单草稿；空名单本身合法。 |
| MC12 | P2 / 源码 | 只读分类显示内部 ID，可选审阅显示原始 ID，信息难以核对。 | 使用实际分类标签、条目标题及审阅类型；无法解析时明确不可用，不能造名称。 |
| MC13 | P1 / 源码，风险待并发验证 | 已确认页仍从外层当前 meetingInfo/savedMinutes 取值，而不是统一使用已读取的 final 冻结投影。 | 明确当前草稿与已确认快照的数据源；已确认内容使用同一 confirmation，修改失效后切回当前版本。本轮未复现串版。 |
| MC14 | P1 / 源码 | readiness 没有独立加载态和专用重试闭环；`PUBLICATION_HISTORY_UNAVAILABLE` 无业务文案映射，可能显示原始码。 | 复用原型状态/异常位置呈现读取中、失败及重试；拒绝把读取失败当未通过或通过。 |
| MC15 | P1 / 源码 | 步骤条、路由 availableStages 和最终按钮分别判断进入条件；步骤条检查候选，路由主要只看 body/set。 | 统一阶段能力定义，满足已保存成果且无待处置候选才能交接；信息未补齐仍允许进入确认页。不可用 URL 要说明或规范化。 |
| MC16 | P0 / 源码，需专项故障验证 | 按钮串行执行保存信息→确认名单→重读 readiness→final，各环节已有恢复，但整链没有单独的状态编排/离开保护。 | 给整链协调和明确阶段恢复；前序已成功不能伪装回滚，丢回执重放原键/原体，不能重新发一套授权或确认。不是断言已发生重复提交。 |
| MC17 | P1 / 现有测试 | 当前部分测试以源码/SSR/CSS断言或 demo 静态页为主，不能验证真实 API 的检查定位、确认失效、草稿离开与错误恢复。 | 按真实非 demo 路由建立行为和接口矩阵，视觉验收与功能验收分别记录。 |

关键定位：[PublicationPanel.vue](/home/tanren/ama/web/src/features/minutes/PublicationPanel.vue:36)、[MeetingInfoEditor.vue](/home/tanren/ama/web/src/features/meetings/MeetingInfoEditor.vue:57)、[final-information.ts](/home/tanren/ama/web/src/features/minutes/final-information.ts:3)、[usePublication.ts](/home/tanren/ama/web/src/features/minutes/usePublication.ts:11)、[客户端错误边界](/home/tanren/ama/web/src/shared/api/client.ts:5)。

## 4. 两项关键技术问题

### 4.1 确定拒绝被当成未知结果

实际服务端在校验不满足、版本冲突和可选审阅无效时分别返回 `FINAL_NOT_READY`、`REVISION_CONFLICT`、`INVALID_OPTIONAL_REVIEW`。publications API 没有传入该业务的 errorContract，通用 knownCodes 也未覆盖这些最终确认响应。

本轮运行实际 request/useCommand 并输入受控响应，结果如下（不是向真实服务端提交）：

| 响应 | 客户端 kind/code | 命令状态 |
|---|---|---|
| 409 FINAL_NOT_READY | response / 无 code | UNCERTAIN |
| 409 REVISION_CONFLICT | response / 无 code | UNCERTAIN |
| 422 INVALID_OPTIONAL_REVIEW | response / 无 code | UNCERTAIN |

`useCommand` 只有已验证的 HTTP 拒绝进入 CONFLICT/REJECTED；`usePublication.reset()` 只接受这两种状态。因此问题不只是错误文案，而是阻断纠正后重新确认。422 还显示通用“当前输入格式暂不支持”，与实际含义不符。

修复应校验匹配路由、HTTP 状态、声明的错误码及响应结构。确定拒绝后允许重读、纠正并创建新的确认意图；网络中断、未知服务异常、响应不可验证、提交成功丢回执等仍保留原请求恢复策略。不能为修 UI 削弱幂等边界。

### 4.2 当前状态与历史快照边界

服务端已经在信息、建议或名单的实质变更时失效当前 final，并保留历史；最终确认会重新读取并验证来源、版本、审阅等，事务写入冻结对象与回执。前端却把存在 final 当成禁用编辑条件，这不是需要重新设计后端快照机制的问题。

正确状态应区分：未完成检查、可最终确认、已确认未发布、修改导致确认失效、发布执行中、发布成功只读、失败/未知待恢复。状态用于驱动原有页面和按钮，不新增一套用户流程。

## 5. 十二项检查的需求映射

| 需求检查 | 已有基础 | 必须接通的处理目标/验收 |
|---|---|---|
| 基础信息 | MEETING_INFO_REQUIRED、名单确认、表单 | 名称/分类定位；日期可空；摘要和名单可空 |
| 当前确认转写 | 确认 ID、版本校验 | 对应转写确认位置，保留返回上下文 |
| 实体纠错零待处理 | CORRECTIONS_PENDING | 具体待处理纠错项 |
| 正文已保存 | body/set、工作区 dirty | 保存或定位未保存正文，不确认浏览器临时文本 |
| 转写/材料版本匹配 | savedGenerationInput、stale 条件 | 指明哪类来源变化及所需重新生成/采用动作 |
| 重要结论有会议依据 | SOURCE_SUPPORT 审阅 | 具体结论和来源；依据不足转待确认，不自动假定通过 |
| 行动负责人/截止信息 | ACTION_FIELDS 审阅 | 具体行动项；需求允许没有截止日期，不新增必填 |
| 背景不冒充会议结论 | 会议证据门槛、background 数据 | 核对背景性质和结论会议依据 |
| 敏感设置已应用无冲突 | 排除/设置版本、冲突检测 | 具体设置或冲突正文，处理后回读 |
| 无待生成/待采用 | task/candidate ID | 具体任务和候选，不直接跳过 |
| 来源关系完整保存 | 来源图/边校验 | 可定位来源或明确读取失败重试 |
| 当前有确认权限 | authorize、服务端校验 | 无权限说明和操作限制，接口仍拒绝越权 |

纯文本转写无音频可确认；待确认事项和分歧不必为发布而强行消除，需保留性质；名单保存不授予权限，确认才应用；这些要求不能在修复中被“统一必填”覆盖。

## 6. 原型、需求与既有裁决的差异处理

1. **日期：** 原型存在日期必填星标，需求只强制名称/分类，D-03 已明确可选。建议按需求和既有裁决对齐，验收清单登记该差异；不自行恢复强制日期。
2. **检查展示：** 原型最终覆盖在全通过时隐藏检查卡，需求描述完整清单；至少必须在原有异常区补齐原因和“去处理”。全通过时是否始终展示全部十二项，在实施前的映射表中明确，不擅自增加常驻大面板或新弹窗。
3. **成果核对：** 原型可视预览偏正文，需求要求全部成果及来源。先映射既有预览/来源查看/要素定位能承载的能力；未有原型结构的部分单列规格落差，不能由开发者自由设计新页面。

本轮没有要求立即批准上述设计，也没有依赖审批继续写代码。它们是后续实施必须锁定的验收基线；已经有明确裁决的事项不重复制造决策。

## 7. 具体修复顺序与涉及文件

### T0：冻结映射与场景

文件：需求/原型只读；更新本方案对应验收映射，扩展正式确认页测试。

- 每项 MC01–MC17 对应需求条目、最终原型节点、代码位置、验收结果。
- 固定 1440×900、1024×768 的正常、阻断、错误、已确认未发布、人员弹窗样本；区分原型、真实页面和受控测试。
- 明确 §6 的展示差异，保留原型原件哈希；不修改原型来配合当前实现。

### T1：先修错误协议和恢复（P0）

文件：`contracts/openapi/publications.yaml`、`web/src/shared/api/client.ts`、`web/src/shared/api/publications.ts`、`web/src/features/minutes/usePublication.ts`；必要的专属错误契约及测试。

- 先测试本报告三类受控失败，修正 HTTP/code/command 状态映射。
- 明确哪些响应代表本次未写入，哪些需要查回原请求；各业务码给出可执行原因。
- 验收：确定冲突可刷新纠正；无效选择可重选；网络未知和丢回执仍重试同 key/body；错误响应不能触发进入发布页。

### T2：统一交接、入口和加载反馈

文件：`MeetingDetail.vue`、`MeetingProgress.vue`、`MinutesWorkspace.vue`、`useMinutesEditor.ts`、`MinutesGenerationPanel.vue`、`usePublication.ts`。

- 分别定义“可进入确认”和“可提交最终确认”，不能提前要求信息全部完整。
- 统一已保存正文/要素、待采用候选、生成任务、当前版本等交接条件。
- 验证截图会议的加载→无纪要→生成入口，以及自动生成触发/设置失败；只有重现后才修具体根因。
- 加载失败提供明确重试；请求过期/路由切换不可把旧状态回填当前页。

### T3：补信息、建议和人员细节

文件：`MeetingInfoEditor.vue`、`MeetingInfoForm.vue`、`final-information.ts`、`useInfoSuggestions.ts`、`web/src/shared/api/info-suggestions.ts`、`contracts/openapi/info-suggestions.yaml`、`MinutesInfoSuggestionValues.java`、`JdbcMinutesInfoSuggestions.java`；必要前向迁移。

- 日期可空，名称/分类字段级校验并定位；保留空摘要/空名单。
- 补分类建议真实链路，分类字典失效或无法匹配时回到手动选择；不使用假建议或硬编码平台字典。
- 逐字段采纳/拒绝保留人工草稿和处理状态，不让异步结果覆盖新输入。
- 空名单/只读标签按原型与需求纠正，实际人员 ID、目录查询和授权规则沿用现有实现。
- 前向迁移编号在实施时按项目登记确定，不改已执行迁移或覆盖其他未提交文件。

### T4：接通检查和成果核对

文件：`PublicationPanel.vue`、`MinutesWorkspace.vue`、相关转写/要素/来源定位组件；必要扩展 `JdbcMinutesSavedQueries.java`、OpenAPI 与生成类型。

- 把 §5 十二项映射到来源明确的检查结果；条目状态、理由、目标可测试。
- 按原型既有异常区域提供需求规定的“去处理”，复用已存在的来源弹窗和编辑入口。
- 目标只能来自当前授权数据；完成后重新读取，不将本地点击等同于服务器审阅成功。
- 长正文、多页审阅、名称超长、无材料、纯转写和无截止日期均能核对，底栏不能遮挡关键错误。

### T5：接通离开保护、确认失效与多阶段提交

文件：`PublicationPanel.vue`、`MeetingInfoEditor.vue`、`useMeetingInfoEditor.ts`、`MinutesWorkspace.vue`、`MeetingDetail.vue`、`usePublication.ts`；必要抽出内部协调函数。

- 嵌入 editor 暴露 dirty/pending/conflict/beforeLeave，父级统一使用；不另造一份草稿。
- 保存信息与名单授权分离；最终按钮按现有顺序协调，给整个过程加一致的操作保护和阶段恢复。
- 已确认未发布可编辑，实质保存后失效当前 final；已发布仍只读。
- 当前信息和冻结预览各用正确数据源，不能用同一个“有 final”条件处理全部状态。
- 故障场景：保存失败、名单失败、final 检查失败、最终提交成功但回执丢失、快速双击、刷新恢复、另一会话更新。验证前序真实成功状态和历史快照不被误改。

### T6：验收与回归

文件：正式非 demo 路由的浏览器测试；现有 `meeting-confirmation-view.test.ts`、`final-information.test.ts`、`publication-state.test.ts`、信息/名单/建议测试；后端 final/readiness/权限集成测试。

- 视觉测试与行为测试分开。不能只断言 class、文案、SSR 输出，也不能让原型 renderer 接受当前实现的结构修改。
- 真实独立测试会议走通：保存转写 → 生成/保存纪要 → 必需审阅 → 补信息/名单 → 最终确认 → 发布配置；禁止拿用户截图会议做写测试。
- 验证权限撤销、409/422/503、未知结果恢复、候选待处置、来源变化、空名单、无日期、已确认后修改、发布后只读。
- 按改动范围执行相关 unit、typecheck、契约生成检查、build、Playwright 和后端集成测试，全部记录实际命令与结果。不能用本轮 5 项旧测试通过代替修复验收。
- 全程单 agent，在项目根目录 `main` 实施；不得创建 worktree、切分支或用子 agent。

## 8. 可复用能力与根因

应保留：信息草稿与版本冲突处理、实际人员目录、保存/授权分离、空名单确认、来源/行动项审阅、服务端提交时再次检查、不可变最终快照、幂等回执、原操作恢复、发布后只读。它们已有实现，不能笼统说“后端全部没有做”。

根因是原有页面保真任务主要限定 DOM/CSS、正常态样本与当前业务状态没有完整对应；信息 editor、工作台离开守卫和 publication 命令各自有状态却没有接通；final 与 published 边界混用；最终确认错误协议没有接入客户端语义；测试没有覆盖被阻断时如何继续处理。仅再调一轮间距和按钮不能完成本次修复。

## 9. 本轮证据及验证结果

证据目录：`/home/tanren/ama/.tools/local-app/meeting-confirmation-audit-20261103/`。

| 证据 | 内容 |
|---|---|
| [read-only-api.json](/home/tanren/ama/.tools/local-app/meeting-confirmation-audit-20261103/read-only-api.json) | 附件会议有效只读接口结果 |
| [current-minutes-observation.json](/home/tanren/ama/.tools/local-app/meeting-confirmation-audit-20261103/current-minutes-observation.json) | 加载结束后的页面、按钮、无写请求/脚本错误 |
| [confirmation-sample-api.json](/home/tanren/ama/.tools/local-app/meeting-confirmation-audit-20261103/confirmation-sample-api.json) | 有纪要样本与真实阻断项 |
| [actual-confirm-observation.json](/home/tanren/ama/.tools/local-app/meeting-confirmation-audit-20261103/actual-confirm-observation.json) | 两视口、检查弹窗、未保存离开、草稿返回仍在 |
| [实际确认页 1440](/home/tanren/ama/.tools/local-app/meeting-confirmation-audit-20261103/actual-confirm-1440.png) / [1024](/home/tanren/ama/.tools/local-app/meeting-confirmation-audit-20261103/actual-confirm-1024.png) | 真实页面截图，非静态 demo |
| [实际检查弹窗](/home/tanren/ama/.tools/local-app/meeting-confirmation-audit-20261103/actual-checklist-1440.png) | 待完成列表没有处理入口 |
| [原型确认页 1440](/home/tanren/ama/.tools/local-app/meeting-confirmation-audit-20261103/prototype-confirm-1440.png) / [1024](/home/tanren/ama/.tools/local-app/meeting-confirmation-audit-20261103/prototype-confirm-1024.png) | 原始原型运行时截图，内容与真实样本不同，不作直接像素比例结论 |
| [prototype-observation.json](/home/tanren/ama/.tools/local-app/meeting-confirmation-audit-20261103/prototype-observation.json) | 原型正常点击后的 DOM/交互记录 |
| [final-error-classification.json](/home/tanren/ama/.tools/local-app/meeting-confirmation-audit-20261103/final-error-classification.json) | 3 类确定失败被归为 UNCERTAIN；受控 fetch，真实网络为 0 |

已有测试实际运行：从根目录加载 `.tools/env.sh`，执行 `./web/node_modules/.bin/tsx --test web/tests/unit/final-information.test.ts web/tests/unit/publication-state.test.ts`，共 **5 项通过、0 失败**。测试包含旧规则，不能据此证明产品要求已满足。

未实施修复，未宣称真实最终确认/发布事务通过、全部角色验收通过或截图永久加载问题已解决。后续应以上述顺序补齐并按需求/原型验收，不直接重绘页面。
