# 会议确认：原型与功能设计差异分析及修复方案

> **状态：分析完成，实施未开始。** 本轮只新增本方案和 `.tools/meeting-confirmation-analysis-2026-11-03/` 下的分析证据；未修改产品源码、测试源码、契约、原型、配置或数据库，未提交、部署或执行真实最终确认/发布。
>
> **For agentic workers:** 后续仅使用 `executing-plans` 的单 agent 顺序执行方式。全部工作在 `/home/tanren/ama`、`main` 分支进行，不创建或切换分支，不创建或使用 worktree，不调用子 agent。以下复选框均是后续修复任务，不代表本轮已实施。

**Goal:** 依据原型最终渲染、一期功能设计和已采纳产品裁决，补齐会议确认的页面、信息编辑、成果核对、检查定位、确认失效和失败恢复闭环。

**Architecture:** 保留 `MeetingDetail → MinutesWorkspace → PublicationPanel` 的正式路由、现有会议信息编辑器、已保存纪要、名单授权及最终快照服务。在原组件链中补齐状态和事件；复用现有来源查看与纪要编辑能力。只在真实缺少字段/定位信息的接口处扩展契约，不另建一套确认页面、虚构成功状态或绕过后端检查。

**Tech Stack:** Vue 3、TypeScript、Vite、Java/Spring、现有数据库版本与幂等模型、Node test runner、Playwright。

**Spec:**

- 原型：`designDocs/智能会议助手1021版.html`。必须读取最终覆盖后的函数及运行时 DOM，不能只看早期同名函数。
- 功能设计：`designDocs/智能会议助手一期需求说明文档.md`，模块一 §7、§9，模块三保存/来源/确认规则，模块四 §3、§5，模块五状态流转。
- 既有裁决：`docs/contracts/product-decisions.md` 的 D-03、D-06、D-08、D-15、D-18、D-21；`docs/superpowers/specs/2026-10-21-ama-product-supplement.md`；`docs/superpowers/specs/2026-10-22-ama-minutes-ux-decisions.md`。
- 现行验收范围：`docs/development/2026-11-02-desktop-only-scope.md`，1440×900、1024×768 两个桌面视口。

## 1. 分析基线与证据边界

- 开始时已核实 `pwd=/home/tanren/ama`、分支 `main`；HEAD 为 `4bf637611958c8ba5e4a9aa308572618a6f8b334`，分析对象包含当前大量未提交改动，不能只按 HEAD 推断现状。
- 原型 SHA-256：`ab21d0b73f4e48bbf701ee8bca5e958940b61f6e6f680d4ced02de0ce4bab867`。
- 需求文档 SHA-256：`e5b11f49373418e7b4ea9b74071fed035f320612a4783de075980c13223cf783`。
- 附件已从 WSL 路径 `/mnt/c/Users/LING/AppData/Local/Temp/codex-clipboard-9068ce4b-267c-43a6-b124-d95971a5a7d5.png` 成功读取。图中实际是 AI 纪要页，红框是知识库发布步骤。本报告按用户文字要求以会议确认为主，检查相邻阶段衔接，不把红框视为单独发布页重构授权。
- 原型在隔离浏览器中通过可见按钮进入确认页、添加参会人弹窗、完成原型内确认后返回确认页。没有修改原型 HTML、CSS 或渲染函数。
- 当前应用使用本地已有测试账号，只读查询附件会议。未生成纪要、未新增会议、未保存名单、未执行最终确认或发布。
- 对当前 `PublicationPanel.vue` 做了隔离浏览器组件核对：运行原组件及其编辑器，注入明确标注的只读测试数据，阻止业务写请求。此证据证明前端行为，不证明真实数据库事务或完整发布链路通过。
- 本轮没有运行全量前后端验收、真实并发修改、权限撤销或最终确认丢回执实验，不对这些未运行场景作通过承诺。

### 附件会议的真实状态

会议 `cc866694-f95d-46f4-a757-2965ad334132` 在核对时的版本为 `10`：

| 事实 | 读取结果 | 含义 |
|---|---|---|
| 转写确认 | 有 `transcriptConfirmationId` | 已完成转写确认 |
| `/minutes` | `body=null`、`setId=null`，七类核心要素为空 | 尚无可供最终确认的已保存纪要/要素 |
| `/workspace` | `minutesRevisionId=null`、`elementSetId=null`、`finalConfirmationId=null` | 第 4、5 步的前置成果未满足 |
| `/final-confirmations/readiness` | `ready=false` | 当前不能最终确认 |
| 阻断码 | `BODY_REQUIRED`、`ELEMENT_SET_REQUIRED`、`GENERATION_REQUIRED`、`SETTINGS_REQUIRED`、`MEETING_INFO_REQUIRED`、`ROSTER_CONFIRMATION_REQUIRED` | 应完成生成、信息和名单流程，不应直接放开发布 |

因此，这张图中的步骤禁用本身不能证明确认/发布按钮是假功能。另一个确实存在的问题是：直接访问不可用的 `/confirm`，地址仍为 `/confirm`，实际内容回退到转写页，缺少明确的前置条件说明或规范化路由。见 `MeetingDetail.vue:45–47`。

## 2. 总体结论

现有主页面已具备原型的双栏骨架、信息编辑、人员弹窗、纪要文本预览、固定底栏及真实最终确认接口。问题集中在以下四方面：

1. **需求能力缺失：** 检查项不能定位处理，确认页没有完整的成果/来源核对入口，分类 AI 建议没有端到端实现。
2. **状态行为不一致：** 最终确认后过早锁住本页信息；日期仍被前端强制必填；信息草稿与离开保护、顶部状态没有贯通。
3. **交互被另行设计或简化：** 检查区改成隐藏弹窗、底栏提示文本成了检查入口，已确认态展示 raw 分类 ID、参会人错误必填标记；名称/摘要建议的处理也与原型不同。
4. **验收覆盖不足：** 存在真实实现、硬编码演示页和原始原型三套路径；既有确认页测试主要断言字符串和 CSS，不能证明需求闭环。

后端已有大量正确基础，不应重做或删除：保存与授权分离、最终确认重新验证、不可变历史、版本冲突、幂等回执、发布后只读。修复应把这些能力接入正确的页面行为。

## 3. 需求、原型与既有裁决的适用关系

### 3.1 原型最终形态

以下函数共同决定最终确认页，单看 `acFinalPage` 会误判：

- `acFinalPage`：第 2623 行，定义信息、预览、检查区。
- `acInfoFields` 最后覆盖：第 2848 行，定义字段顺序、日期、建议和参会人区域。
- `acAddParticipants`：第 2857 行，定义内部/外部人员弹窗、选择暂存及取消行为。
- `aciHeader` 最后覆盖：第 2874 行，确认/发布阶段隐藏顶部“编辑会议信息”，保留工作台保存。
- `acfDescriptor`、`acfStripOriginal`：第 3137–3138 行，将主操作移入固定底栏；没有业务错误时移除检查卡，不应机械恢复早期重复底部按钮。

当前双栏比例 `.9fr/1.3fr`、16px 间距、主要卡片和 dock CSS 已与原型共享。不能将本次修复简单定性为“整页布局从零重写”。重点是条件渲染、交互和功能完整性。

### 3.2 日期已有明确裁决

原型第 2513、2848、3137 行要求会议日期必填，但需求模块四 §5.2 只要求名称、分类，不新增其他必填；D-03 及已采纳产品补充明确“可选实际会议日期，未知数据保持空”。后端 readiness 与真实集成测试也允许空日期。

方案按**既有已采纳裁决**处理：保留日期字段及位置，日期为空可确认；填写时验证合法性，不用创建时间代填。同步移除日期必填星标、ARIA 要求、错误文案和底栏必填说明。原型原件保持不变，在验收差异清单登记这一已裁决差异；不再重新询问已批准规则。

### 3.3 原型简化不能删除文档要求

- 原型正常态隐藏检查卡，不代表可以删除文档规定的失败原因和“去处理”能力；需要在原有异常检查区域补全功能。
- 原型中的模拟人员、示例类别、按姓名匹配身份、模拟成功不是正式授权或平台数据。内部授权必须使用经过核实的企业 ID；D-15 未明确的外部身份细则不能由实现者补造。
- 来源可定位不能代替语义核对，D-21 已明确结论/行动项需要负责人复核；不能为了主按钮可点而自动标记通过。
- 严格复刻适用的是产品布局与交互。技术错误码、回执、revision 等信息只有在用户处理失败/冲突需要时呈现，不应成为正常页面的主要操作。

## 4. 已确认差异清单

证据等级：**浏览器**为本轮运行时复现；**源码**为当前代码直接确认；**待专项验证**不等于已复现事故。优先级 P0 为确认闭环/数据一致性，P1 为功能和交互完整性，P2 为表现细节。

| 编号 | 优先级 | 目标与当前差异 | 依据/定位 | 具体修复方向 |
|---|---|---|---|---|
| C01 | P0 | 检查清单只有阻断文字和 `全文来源/行动字段·待完成`；没有任何目标处理动作。浏览器中弹窗只有关闭、收起、完成。 | 需求模块四 §5.3；`PublicationPanel.vue:48–50` | 每个失败项对应真实字段、转写问题、纪要条目、候选、设置或来源；加入需求明确的“去处理”，处理后回读当前版本，不在本地置通过。 |
| C02 | P0 | 成果核对区只渲染 `title + body`。没有独立结论、行动项、风险、待确认事项、分歧、背景引用及来源核对入口；正文偶然提到这些内容不等于结构化核对完成。 | 需求模块四 §5.1；`PublicationPanel.vue:47`；`MinutesWorkspace.vue:125–129` | 复用原有核心要素与来源查看能力，在原型预览/检查区域内提供文档要求的核对入口；保留待确认/分歧性质，无音频不虚构定位。 |
| C03 | P0 | 只要有最终确认，本页信息编辑器即卸载，名称、日期、分类只读，顶部保存禁用。原型已确认未发布时仍可编辑；文档也要求可修改并使当前确认失效。浏览器已复现。 | 需求模块四 §3、§5.4；原型确认后回到第 4 步；`PublicationPanel.vue:38,47` | 根据负责人权限、发布任务/结果和恢复状态判断可编辑；不能用“有 final”替代“已发布只读”。保存实质修改后清除当前 final、保留旧快照并要求重新确认。 |
| C04 | P0 | 确认页强制日期必填，与已采纳 D-03、需求和后端冲突。 | `final-information.ts:3`、`MeetingInfoForm.vue:41`、`JdbcMinutesSavedQueries.java:24`、D-03 | 按 §3.2 统一前端校验、标识和文案；后端保留可空规则，合法性仍校验。 |
| C05 | P0 | 确认信息草稿没有接入全页离开保护。`MinutesWorkspace.beforeLeave()` 只看纪要和敏感选择；父页只检查抽屉 editor 的 pending，不检查嵌入确认页 editor。 | 需求模块一 §7.4；原型 `aciBackList`/`beforeunload`；`MinutesWorkspace.vue:32–35`、`MeetingDetail.vue:69` | 将嵌入 editor 的 dirty、pending、conflict 和 beforeLeave 向上贯通；覆盖返回列表、步骤跳转、浏览器后退/刷新；区分“草稿可恢复”和“已经保存成功”。本轮未执行真实未知提交离开实验。 |
| C06 | P1 | 必填失败仅返回错误字符串，未定位字段。浏览器验证空名称后焦点仍在“最终确认并继续发布”。 | 需求模块四 §5.2；`MeetingInfoEditor.vue:57` | 返回结构化字段错误，在原字段下提示并 focus/scroll；不清空其他输入；名称/分类检查一致。 |
| C07 | P1 | 分类 AI 建议全栈缺失。前端类型只有 NAME/SUMMARY；后端始终 `categoryStatus=SELECT_REQUIRED`；V053 CHECK 也只允许 NAME/SUMMARY。 | 原型 `acInfoSuggestion(category)`；需求模块一 §7.2；`info-suggestions.ts:2–3`、`MinutesInfoSuggestionValues.java:7–8`、V053 | 补真实建议来源、字典稳定 ID 映射、采纳/拒绝及不可匹配状态；不能仅添加静态建议行。涉及服务、协议/配置归档及前向迁移，不能限制为 CSS 任务。 |
| C08 | P1 | 名称/摘要建议已有真实接口，但任何草稿变脏都会禁用决定，提示先保存；决定后查询只返回 PENDING，原型“已采纳/已拒绝”显示缺失。 | 原型第 2846 行；`useInfoSuggestions.ts` 的 `canDecide`；`MeetingInfoEditor.vue:76–85`；`JdbcMinutesInfoSuggestions.java:16` | 保持单字段用户决定和迟到结果不覆盖人工值；在现有建议槽保留决定状态。局部采纳与草稿合并需要与契约一起修正，不通过隐式保存全部字段解决。 |
| C09 | P1 | “从发言人获取”只接受 `source=HUMAN`，文件标签和其他身份来源全部跳过；无企业 ID 的姓名直接变成外部字符串。与原型最终获取所有发言人作为可编辑条目的行为不一致。 | 原型第 2851 行；需求模块一 §7.2–7.3；`useSpeakerRosterSource.ts:20–44` | 区分已核实企业身份与仅展示候选；未实名/文件标签可供用户核对，不能按同名或声纹候选直接授权；复用既有人员选择/名单区，不新造自动授权流程。外部结构细则受 D-15 约束。 |
| C10 | P1 | 顶部用 `confirmedRosterId` 判定“会议信息已确认”，混淆名单授权和信息确认；嵌入表单变脏时顶部保存状态没有同步清除。 | 原型 `acInfoState/acWsDirty`；`MeetingDetail.vue:78`、`MeetingInfoEditor.vue:50,64–66` | 分别表达信息草稿、名单确认和当前 final 状态；后续编辑立即撤销旧“已保存”提示，保存成功后回读对应版本。 |
| C11 | P1 | 已确认态仍从外层当前 `meetingInfo/savedMinutes` 拼展示，而不是读已取得的 `p.final.information/body`。 | `PublicationPanel.vue:47`；`FrozenFinalProjection.java` | 已确认预览统一来自该 confirmation 快照；修改后切换到当前草稿并明确失效状态。源码可确认数据源混用，实际并发错配待专项测试，不宣称已发生串版。 |
| C12 | P1 | readiness 加载/读取失败时主按钮不能继续，但没有确认检查专用重试入口；后端 `PUBLICATION_HISTORY_UNAVAILABLE` 未翻译，会落到原始错误码。 | `usePublication.ts:12–17`；`PublicationPanel.vue:16,46,48–50`；`JdbcFinalConfirmations.java:40` | 提供正常的读取中/失败/重试反馈；失败项用业务原因和可执行动作，未知码也不能直接暴露为用户指令。 |
| C13 | P2 | 已确认态分类显示 raw ID，参会人被加 `*`；空名单缺少明确“已确认空名单”表达，未确认态空人员框也缺原型“暂未添加参会人”。 | `PublicationPanel.vue:47`、`MeetingInfoForm.vue:44`、原型第 2847 行 | 分类用当前字典标签，找不到时诚实标注；删除名单必填标记，区分未确认、已确认空名单、已选草稿。 |
| C14 | P1 | 分类“更多”用新页替换全部已有选项，旧选中值退化为 ID/已选；建议与已选分类标签可能不稳定。 | `MeetingInfoEditor.vue:35` | 合并并按稳定 ID 去重，保留已选标签和过期状态；分类失效由后端复核，不用缓存假装仍有效。 |
| C15 | P1 | 硬编码 demo ID 的确认页只有静态信息和通知按钮；其 e2e 特意断言没有 API 请求。与正式页面测试容易混淆。 | `MeetingDetail.vue:32,75`；`MeetingPrototypeStage.vue:63–67`；`ai-minutes-prototype.spec.ts:25–42` | demo 测试继续明确标为演示；正式验收必须覆盖非 demo ID、真实页面和真实接口。不能以 demo 能跳转证明最终确认完成。 |
| C16 | P1 | 不可用 `/confirm` URL 静默渲染转写内容；上一步处理状态与当前 URL 不一致。 | 本轮真实浏览器；`MeetingDetail.vue:45–47` | 统一步骤能力与路由守卫，在既有错误/状态位置说明前置条件，或规范化到实际阶段；不放开缺少纪要的确认。 |

### 需补专项验证的风险，不能当作已发生事故

- `confirmFinal → prepareFinal → 保存信息 → 确认名单 → 重读 readiness → 最终确认` 是多个独立请求。最终快照写本身有事务，但整个按钮操作不是一个事务。中间失败可能已保存信息、甚至已更新 ACL。应明确已完成阶段并恢复原操作，不能统一宣称全部未提交或全成功。
- `usePublication` 和 `useMeetingInfoEditor` 各自已有 single-flight 与原请求恢复；整个多阶段按钮链仍缺专门的编排与跨组件离开验证。需要验证快速连点、阶段间重新加载、名单已生效但最终检查失败、最终提交已成功但回执丢失。
- 已确认后的预览混用、readiness 与正文的版本一致性，需要真实并发测试。现有后端版本校验可阻止旧页面直接冻结，但不能替代前端清晰展示。

## 5. 十二项检查逐条补齐

下面是需求模块四 §5.3 的完整覆盖映射，不自行增加业务必填项。

| 需求检查 | 当前基础 | 修复后的处理目标 |
|---|---|---|
| 基础信息符合要求 | 聚合 `MEETING_INFO_REQUIRED`，前端字符串校验 | 定位名称/分类；可空日期仅校验格式；名单可空 |
| 当前使用确认转写 | 已有确认引用和 stale 检查 | 转写确认位置，保留回到确认页的上下文 |
| 实体纠错零待处理 | `CORRECTIONS_PENDING` | 打开实际待处理项，不能仅返回整个转写页让用户寻找 |
| 当前正文保存成功 | 已保存正文查询、纪要草稿保护 | 定位未保存正文；禁止把浏览器临时文本当确认版本 |
| 纪要与转写/材料匹配 | 来源及设置版本验证 | 明确哪一类版本变化，进入对应处理位置 |
| 重要结论有会议依据 | 必需 `SOURCE_SUPPORT` 审阅 | 精确打开结论及其来源；不足可回纪要转待确认事项 |
| 行动负责人/截止信息 | `ACTION_FIELDS` 审阅 | 精确打开该行动项；无截止日期不强制补填 |
| 背景未冒充会议结论 | 会议来源门槛、background 数据 | 展示相关背景/结论与来源；保留会议依据与背景区别 |
| 敏感设置已应用无冲突 | 排除及版本门槛 | 返回具体敏感设置/冲突正文；不得把敏感要素查看权限一起删掉 |
| 无待生成/待采用候选 | 候选 ID 和任务 ID | 打开该候选/任务，显示明确状态；操作后重新检查 |
| 来源关系保存完整 | 来源图核验和读取失败 | 展示可定位的失败关系或可重试的读取错误，不默认通过 |
| 当前用户有确认权限 | 前后端权限检查 | 无权限解释、移除操作入口；服务端仍拒绝直接调用 |

内部可增加 typed view model 表达检查类型、结果和目标，但它是实现细节。页面继续使用原型既有信息、预览、异常检查和底部操作区域，不引入第二套工作台或自创导航。

## 6. 根因与修复策略

### 6.1 根因

1. 10 月 30 日旧计划把范围限定为 `PublicationPanel` 的 DOM/CSS，并明确不改业务/契约；该范围不足以覆盖分类建议、检查定位和确认后修改，不能继续当作本次完整修复边界。
2. 11 月 2 日记录重视特定已准备样本的视觉与恢复路径；这些证据是历史状态，不能覆盖当前 dirty tree，也不能证明全部文档能力。
3. 一个 publication 组件同时处理确认、发布、目录、结果和恢复；信息草稿在内部 editor，父级离开守卫只看纪要编辑，责任边界没有接通。
4. “已确认名单”“当前最终确认”“发布后只读”被部分 UI 条件合并，造成错误文案和过早禁用。
5. 原型多个同名函数后续覆盖，照搬某段早期 HTML 或只有 class/字符串的测试都容易验证错目标。

### 6.2 采用的修复路径

**推荐原位补全现有正式链路。** 保留组件接入和 API 主干，将检查项映射、处理目标、确认编排拆成职责明确的内部模块；必要时抽出确认区域子组件，不新增路由和独立数据状态。

整体重写一套确认页会重复已有权限、草稿和恢复能力；只继续修 CSS 又无法解决文档功能缺口。上述两种均不适合本次要求。此选择是本报告建议，未实施。

## 7. 分阶段修复任务

### T1：固定有效基线、路由和验收样本

**Files:** `web/tests/unit/meeting-confirmation-view.test.ts`、新建 `web/tests/e2e/meeting-confirmation.spec.ts`、新建 `web/tests/visual/meeting-confirmation.spec.ts`、`MeetingDetail.vue`、`MeetingProgress.vue`；原型只读。

**Interfaces:** 继续使用 `/meeting/:id/confirm`；场景区分未生成、待审阅、待补信息、可确认、已确认未发布、发布执行中、发布成功、失败/未知。

- [ ] 用非 demo ID 写失败测试：有效纪要进入确认；无正文访问 `/confirm` 不出现 URL/实际步骤不一致且无说明的页面；确认不要求提前补齐信息。
- [ ] 原型通过 UI 采集两个桌面视口的确认、名单、异常、确认后修改状态；保留原件哈希。
- [ ] 记录 D-03 日期等既有裁决差异，确保测试不把旧错误必填固定为目标。
- [ ] 修正正式路由能力与步骤条的一致性，保持生成/确认/发布真实门槛。
- [ ] 验证页面来源标记：原型、隔离组件、真实端到端分别报告，demo 测试不计入正式确认验收。

### T2：信息编辑、确认后修改和离开保护（P0）

**Files:** `MeetingInfoEditor.vue`、`MeetingInfoForm.vue`、`useMeetingInfoEditor.ts`、`final-information.ts`、`PublicationPanel.vue`、`MinutesWorkspace.vue`、`MeetingDetail.vue`；测试扩展 `final-information.test.ts`、`meeting-info-persistence.test.ts` 和 T1 e2e。

**Interfaces:** 嵌入 editor 向上暴露 `dirty`、`pendingRecovery`、`conflict`、`beforeLeave(): Promise<boolean>`；父级统一委托，保存仍为现有 `SAVE_INFO`，不附带确认名单/最终确认。

- [ ] 先写失败测试：日期可空、非法已填日期被拒绝、名称/分类字段获得焦点、空名单允许；最终确认后未发布仍可编辑信息。
- [ ] 将必填校验返回值改成明确的字段及文案，在原输入控件显示错误、滚动和聚焦，保留其他草稿。
- [ ] 可编辑性依据真正的发布/权限/恢复状态；正常未发布修改使用现有后端失效逻辑，不删除历史 final。
- [ ] 将嵌入草稿/未知请求接入返回列表、步骤跳转、路由离开和刷新保护；提示及按钮沿用原型/需求已有的保存、放弃、继续编辑语义。
- [ ] 区分信息保存、名单授权和最终确认状态，修改后同步顶部保存提示；确认快照失效后禁用继续发布直到重新确认。
- [ ] 验证：纯顶部保存不授予或撤销权限；确认后改名不覆盖人工纪要标题；发布执行中/成功后写入被拒绝。

### T3：检查清单与成果核对（P0）

**Files:** `PublicationPanel.vue`、`MinutesWorkspace.vue`、`MeetingDetail.vue`；新建 `web/src/features/minutes/final-confirmation-checks.ts`；必要扩展 `JdbcMinutesSavedQueries.java`、`contracts/openapi/publications.yaml`、`web/src/shared/api/publications.ts` 及生成守卫。

**Interfaces:** 定义局部处理目标 `FinalCheckTarget`，包含 `information(field)`、`transcript(problemId?)`、`minutes(targetId, reviewKind?)`、`sensitive(locatorId?)`、`candidate(candidateId)`、`task(taskId)`、`retry`。目标 ID 只来自当前已授权结果；不接受任意 URL。检查只读，明确点击后的编辑仍通过既有版本/权限接口。

- [ ] 为 §5 十二项逐项建立失败测试，断言状态、原因、目标、点击后聚焦位置和处理后回读。
- [ ] 以现有 readiness 和已保存成果生成检查模型；缺失可定位信息时扩展结构化响应，后端仍执行原完整检查，前端不能推测语义通过。
- [ ] 在原型异常检查区呈现错误和“去处理”；去除以隐藏弹窗、可点击提示文本代替问题处理的路径。全通过状态遵循原型最终呈现及已采纳规则，不重复增加操作栏。
- [ ] 打通结论/行动项来源、背景、待确认/分歧核对；复用既有来源弹窗和当前工作台定位能力，不复制一个新的审阅编辑器。
- [ ] 加载中和失败提供真实状态及重试；补 `PUBLICATION_HISTORY_UNAVAILABLE` 等业务错误映射。分页切换时保持目标稳定，列表缩减时校正页码。
- [ ] 验证纯转写输入、无截止日期行动项、无材料、长正文、多页审阅、排除冲突、候选待处置；检查成功不新增任何确认/授权写入。

### T4：最终确认编排、恢复与快照一致性（P0）

**Files:** `PublicationPanel.vue`、`usePublication.ts`、`MeetingInfoEditor.vue`；新建 `web/src/features/minutes/useFinalConfirmation.ts`；`JdbcFinalConfirmations.java`、`FrozenFinalProjection.java` 作为复用/验证对象。

**Interfaces:** `confirm(): Promise<void>` 按保存信息、确认名单、重读条件、最终确认、读取快照推进；`phase` 仅用于协调和正确恢复，不作为新增页面步骤展示。原有每个请求继续使用其不可变 key/body。

- [ ] 编排失败测试：信息失败不继续名单；名单失败不继续 final；final 失败不跳发布；丢回执只恢复原请求；快速连点和阶段间路由变化不会产生第二套提交。
- [ ] 对整个点击链加协调锁，联动返回/保存/编辑；已经确定成功的前序阶段不伪装回滚，后续失败说明准确。
- [ ] final 回读成功后以 `p.final.information/body/elements/confirmation` 展示确认版本；信息/纪要/检查版本不一致时重新读取，不拼接成“已确认”。
- [ ] 继续用现有服务端完整来源验证、权限、乐观版本及原子 final 写；不因 UI 对齐移除审阅、候选和源版本门槛。
- [ ] 用独立测试会议验证三阶段失败、真实最终确认 201/原键重放 200、刷新恢复、另一会话修改、旧快照不变、成员只读和无权用户拒绝。不要复用用户截图会议做写测试。

### T5：会议信息 AI 建议闭环（P1，包含后端）

**Files:** `MeetingInfoEditor.vue`、`MeetingInfoForm.vue`、`useInfoSuggestions.ts`、`web/src/shared/api/info-suggestions.ts`、`contracts/openapi/info-suggestions.yaml`、`MinutesInfoSuggestionValues.java`、`JdbcMinutesInfoSuggestions.java`、`MeetingInfoSuggestionController.java`、`JdbcMeetingRepository.java`、新增前向迁移；模型侧涉及 `ControlledMinutesProtocol.java` 等现有受控生成协议及配置归档。

**Interfaces:** 在已有 `NAME | SUMMARY` 基础上补 `CATEGORY` 的真实建议和决定状态；分类建议值与稳定分类 ID 映射分开，未匹配/失效不能采纳。任何新增模型协议必须保留已归档旧版本的可读取性。

- [ ] 写失败测试：可匹配分类采纳、不可匹配提示待选择、拒绝、已决定状态、迟到建议、人工草稿合并、重试不覆盖其他字段。
- [ ] 核实并接通真实模型/结构化建议来源；当前链路只从标题和 OVERVIEW 派生名称/摘要，不能凭关键词或示例文本伪造分类建议。
- [ ] 扩展建议表约束、响应和服务，使用新的前向迁移；执行时按 migration register 分配编号，不改已存在 V053、不覆盖当前未提交 V064。
- [ ] 在原型已有三个建议槽显示建议及采纳/拒绝结果；人工正在编辑时保留草稿，只处理选中字段，不自动保存整份信息或更新名单授权。
- [ ] 更新 OpenAPI、生成类型和响应校验；验证建议失败不阻断用户手填名称/分类及正常确认。

### T6：名单、分类字典与页面细节（P1/P2）

**Files:** `MeetingInfoForm.vue`、`MeetingInfoEditor.vue`、`useSpeakerRosterSource.ts`、`usePeopleDirectory.ts`、`PublicationPanel.vue`；相关 roster/directory/view 测试。

**Interfaces:** 延续企业稳定 ID、内部/外部名单区分和“保存不授权、确认才更新”；不改变行动负责人或历史发言归属。

- [ ] 覆盖匿名、文件标签、已人工核实身份、同名不同部门、失效人员、取消人员弹窗、空名单和目录分页。
- [ ] 从发言人获取保留可核对候选，明确哪些只是标签；无企业 ID 者不能授予访问。对 D-15 尚未定义的外部身份持久结构单列规格缺口，不自行引入身份规则。
- [ ] 分类页追加并去重，保留已选项；分类失效有真实原因，不能用 prototype 演示字典替代平台字典。
- [ ] 已确认视图使用分类标签，名单不加必填标记，空态/按钮/间距沿用原型；正常状态不显示内部 ID。
- [ ] 人员弹窗保持先独立选择、取消不改变外层草稿、“添加所选人员”才合并；验证最终授权增删与企业 ID 一致。

### T7：完整验收与回归

**Files:** T1 浏览器/视觉测试、相关 unit tests、现有后端 final/roster/readiness integration tests；新建本轮 acceptance 报告。原型和历史失败证据不改。

- [ ] 每个差异 C01–C16 对应测试和结果，不以 `class` 存在或按钮文字存在代替行为。
- [ ] 两个桌面视口分别覆盖完整确认页、人员两种页签、校验失败、检查定位、长正文/长名单、加载失败、确认后修改；尺寸、滚动、焦点、dock 遮挡一起核对。
- [ ] 真实非 demo ID 完整走通：转写确认 → 已保存纪要 → 来源/行动审阅 → 信息草稿 → 最终确认 → 发布配置。后续若执行真实发布，另验证发布后只读；本轮不执行。
- [ ] 使用独立测试数据验证重复点击、401/403、409、503、超时、成功丢回执、目录失效、另一会话更新、空名单及无日期；保留实际失败原件。
- [ ] 原型截图必须来自未修改原型；测试数据、权限/平台限制或已裁决差异逐项解释，不修改原型 renderer，不将当前实现截图更新为“原型基准”。
- [ ] 单 agent 自查对照需求、代码、契约和测试；不调用独立 reviewer agent。

建议执行命令从项目根目录运行，加载已有 `.tools/env.sh` 后按实际改动范围选择：

```sh
npm --prefix web run test:unit
npm --prefix web run typecheck
npm --prefix web run check:api
npm --prefix web run build
npm --prefix web run test:e2e -- tests/e2e/meeting-confirmation.spec.ts tests/visual/meeting-confirmation.spec.ts
./mvnw -B -ntp verify
```

其中 `verify` 为最终后端回归命令；开发各任务先运行对应模块/用例，未改后端前不为纯样式调整运行全量。不得沿用旧计划的 `npm test -- --runInBand`：当前 web 实际脚本为 `test:unit`/`test:e2e`，不是 Jest。

## 8. Review Focus

1. **无日期、空摘要、空名单：** 不增加未批准必填项；T2/T6，服务端一致验证。
2. **其他会话同时修改：** 当前阅读、readiness、提交和 final 必须版本一致；T3/T4，旧确认历史保持不变。
3. **多阶段提交成功一部分后中断：** 原 key/body 恢复、不重复授权或冻结、不误报整体成功；T4。
4. **发言人候选/同名人员/失效身份：** 仅展示候选不会变成会议授权，确认后才按稳定 ID 变更；T6。
5. **原型没有覆盖的错误态与长内容：** 在原有结构内保持可理解、可定位、可恢复，不能为视觉一致删除真实门槛；T1/T3/T7。

## 9. 本轮验证结果与交付证据

证据目录：`/home/tanren/ama/.tools/meeting-confirmation-analysis-2026-11-03/`。

| 文件 | 内容/结论 |
|---|---|
| `corrected-live-readback.json` | 正式同源浏览器 GET：会议列表 200、附件会议 `/minutes` 200，正文及要素为空 |
| `current-meeting-browser-readback.json` | 正式会议、workspace、readiness、current final；前述表格依据其成功返回项 |
| `current-requested-confirm.png` | 不满足前置时请求 `/confirm` 实际渲染转写内容 |
| `prototype-confirm-1440.png`、`prototype-confirm-1024.png` | 未改原型的两个桌面完整确认页 |
| `prototype-people.png`、`prototype-people.txt` | 原型人员选择弹窗 |
| `prototype-confirmed.png`、`prototype-confirmed-behavior.json` | 原型确认后返回本页，名称/日期/分类仍可编辑，正常最终确认 dock 消失 |
| `component-behavior-report.json` | 原组件隔离测试：无“去处理”、错误未聚焦、已 final 三个只读输入、没有信息编辑按钮；0 业务写、0 page error |
| `component-checklist.png`、`component-confirm-blocked.png`、`component-confirmed.png` | 上述原组件隔离场景截图，不是生产端到端截图 |
| `focused-tests.log` | 6 个既有测试文件，36 项通过、0 失败；不代表缺口已修好 |

本轮实际执行的既有测试：`final-information.test.ts`、`publication-state.test.ts`、`meeting-info-editor.test.ts`、`meeting-info-persistence.test.ts`、`info-suggestions.test.ts`、`speaker-roster-source.test.ts`。这些测试当前还包含“日期必填”等旧预期，修复时必须按照已采纳规则修改预期，不能把现有通过作为产品正确性的证明。

原始排查中 `APIRequestContext` 直接调用返回过 401；另外请求了不存在的 `/minutes/current`，并观察到列表 `limit=50` 返回 503。这些探索记录保留，但不作为本报告的确认页缺陷证明。最终会议数据结论使用已登录浏览器同源 fetch 的有效路由及 200 响应；`limit=10` 正常返回。首轮组件注入因 Vite 模块身份不同未成功，随后使用运行时实际模块路径重试，以上报告来自成功的第二轮。

本轮**未**宣称零像素一致、最终确认/发布事务验收通过或修复完成。下一步如获实施指令，应从 T1 开始，先锁定验收与真实前置状态，再依次处理 T2–T7。
