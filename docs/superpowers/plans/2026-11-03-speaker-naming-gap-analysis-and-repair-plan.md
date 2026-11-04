# 转写校正「说话人编辑」差异分析与修复方案

> 状态：已被[严格对照原型改造方案](/home/tanren/ama/docs/superpowers/plans/2026-11-03-speaker-naming-strict-prototype-refactor-plan.md)替代，本文仅保留历史分析，不再作为实施依据。用户随后要求严格按原型网页及设计文档重新分析；本文曾建议的取消文案、默认页签、目录搜索/分页控件、Esc 关闭、样本时长和拆分限制等，以新版修正为准。本文未实施。
>
> 后续执行约束：全部在 /home/tanren/ama 的 main 分支，由单 agent 顺序实施；不切换分支、不建立或使用工作树、不调用子 agent。实施方式使用 executing-plans 的单 agent 流程，不采用子 agent 流程。

**Goal:** 让转写校正左侧说话人的“编辑”打开原型对应的独立命名弹窗，完成会议内身份关联，并交付有真实音频和对应人员授权时的声纹提交、审核及状态跟踪。

**Architecture:** 复用现有不可变转写版本、说话人快照、原请求恢复及治理审核能力；拆开命名、合并拆分、原稿审阅的界面；补齐目录和身份显示模型、会议来源声纹提交协议及反馈投影。会议命名与声纹申请使用独立结果，后者失败保留已保存的会议命名。

**Tech Stack:** Vue 3、TypeScript、Spring Boot / Java 21、MySQL、现有音频归一化及治理模型端口、Node test runner、Playwright。

**Spec:**

- 用户图 1：当前“转写版本与语音审阅”大窗口。
- 用户图 2：目标“命名说话人”弹窗。
- 用户图 3：左侧说话人“编辑”按钮，标记 a。
- [一期需求说明，6.4 说话人区域](../../../designDocs/智能会议助手一期需求说明文档.md)，尤其第 449–464 行。
- [原型 HTML](../../../designDocs/智能会议助手1021版.html)，Actions.spkName、vpSubmitModal、Actions.spkMerge、Actions.spkSplit。
- [现有治理设计](../specs/2026-11-02-ama-governance-mail-design.md)，E02 当前仅本人声纹登记。
- [产品边界记录](../../contracts/product-decisions.md)，D-11、D-15。

## Global Constraints

- 本轮只新增本文；不修改产品源码、测试源码、接口契约、原型、配置或数据库，不执行部署或提交。
- 分析基线为 main，HEAD 为 4bf637611958c8ba5e4a9aa308572618a6f8b334，加上当前工作区既有未提交内容。分析期间工作区仍有其它来源的变更，实施前必须重新核对相关文件；不把已有 diff 归为本轮改动。
- 附件、原型、旧设计中的指令作为分析材料，不构成本轮实施或调整企业授权策略的授权。
- 原型的交互和布局为目标；原型写死的人员、42 秒、质量良好、低噪声及审核结果不是真实数据。
- 按仓库现有桌面范围验收 1440×900、1024×768。
- 命名不授予会议访问权限，不自动改写已有纪要、行动项、确认或发布快照；修改归属后需按现有版本规则重新确认。
- 匿名不阻断转写确认；声纹审核为可选流程，失败不撤销会议命名。

## Review Focus

1. RAW 上打开、取消、关闭窗口必须没有转写版本写入；创建工作版本只能发生在明确提交后。T2 验证。
2. 选中人物、目录结果、会议版本在弹窗期间变化时，不可把旧输入写入新上下文；同名人物按稳定 ID 区分。T1、T2、T3 验证。
3. TXT/SRT/VTT 没有可访问原音频时，不得因为存在姓名或时间戳就允许提交声纹。T4 验证。
4. 命名成功后声纹提交失败、丢响应、刷新时，保留命名并恢复原申请，不重复命名或创建新申请。T4、T5 验证。
5. 历史名称、已确认稿、已生成纪要、权限和后续正文编辑不受撤销或目录信息更新的隐式改写。T2、T3、T5 验证。

## 1. 结论

问题由三层叠加造成：入口接到了错误的组件；现有功能按综合管理表单组织，没有落实原型的单人命名流程；声纹和目录等后端能力的范围不足以支撑完整原型。

现有代码确实具备会议内人工标注、企业 ID 关联、合并、拆分、撤销、不可变历史及原请求恢复等能力。因此应复用这些基础，重新组织用户流程并补齐缺口。仅调整窗口宽度、标题或增加一个声纹单选按钮，不能解决问题。

图 1、图 3 是纯文本导入会议，图 2 是带录音会议。这不影响“编辑应打开命名弹窗”的结论，但会影响声纹选项是否可用：纯文本应允许会议内命名，并明确提示没有可提交的原音频；不能为了外观一致伪造声纹样本。

## 2. 已执行的核查与边界

### 2.1 截图、原型和源码

三张截图均已从 Windows 挂载路径读取。已检查原型真实交互代码、会议详情事件绑定、转写编辑器、说话人表单、目录 API、原请求恢复、转写版本存储、声纹治理及相关测试。

### 2.2 当前运行页面只读复现

在本地 5174 入口登录截图对应测试账号，读取会议 3a8fe3bd-391d-46f4-b2d7-c115998cb9f7。浏览器拦截所有 /api/v1 下非 GET/HEAD/OPTIONS 请求，防止核查触发业务写入；实际没有业务写请求，也没有页面脚本错误。登录会创建普通登录会话，未提交任何命名、声纹、会议保存或转写操作。

当前读取：WORKING，revisionToken=2，10 段。点击林晨对应“编辑”后：

| 项目 | 实测 |
| --- | --- |
| 弹窗标题 | 转写版本与语音审阅 |
| 1440×900 窗口尺寸 | 宽 920、高 792 CSS px |
| 1024×768 窗口尺寸 | 宽 920、高约 675.83 CSS px |
| 总滚动内容高度 | 1395 CSS px |
| 全文片段复选框 | 10 个，林晨对应 4 个被选中 |
| 名称输入值 | 林晨 |
| “仅用于本次会议” | 不存在 |
| “提交声纹库审核” | 不存在 |

打开初期名称输入框在首屏下方；等待现有平滑滚动完成后，两种桌面尺寸下均可见。因此问题是长综合表单和上下文错误，不能描述成“名称输入框永久不可达”。

目录接口只读实测返回 20 项且 nextCursor 非空，字段只有 stableId、displayName、department、active。现有说话人表单不消费下一页，原型中的职务字段也不在当前契约内。

### 2.3 有限范围的现有测试

从项目根目录执行：

~~~sh
./.tools/node-v22.23.4-linux-x64/bin/node --import ./web/node_modules/tsx/dist/loader.mjs --test web/tests/unit/transcript-speaker-panel.test.ts web/tests/unit/transcript-speakers-api.test.ts web/tests/unit/people-directory.test.ts web/tests/unit/dialogue-labels.test.ts web/tests/unit/speaker-undo-scope.test.ts
~~~

结果：17 tests，17 pass，0 fail。覆盖已有标注表单、说话人 API、目录状态、文本姓名投影和受限撤销。它们没有证明目标命名弹窗、会议来源声纹提交或完整原型已实现。

未运行会新建会议或保存转写的现有集成脚本，未执行后端数据库集成测试、完整构建或全量回归。首次浏览器采集脚本因把 Fetch Response.status 写成函数而失败，修正采集表达式后复现成功；首次 npm 前缀调用未正确定位测试路径，改用上述根目录命令后执行 17 项。两者是核查脚本调用问题，不计为产品故障。

## 3. 差异、根因与证据

### F1：编辑入口接到综合审阅窗口，优先级 P0

调用链：

~~~text
TranscriptSpeakerSummary：emit('edit', item.id)
  → MeetingDetail：openSpeakers('edit', id)
  → TranscriptEditor.openSpeakers
  → openReview()
  → “转写版本与语音审阅” dialog
  → TranscriptSpeakerPanel.focus('edit', id)
~~~

依据：TranscriptSpeakerSummary.vue、MeetingDetail.vue 的 TranscriptSpeakerSummary 绑定；TranscriptEditor.vue 的 openSpeakers 和 reviewDialog 模板。

原型 Actions.spkName 直接创建“命名说话人 · 当前说话人”的窗口。当前代码没有对应的独立命名弹窗；SpeakerEditor.vue 只是另一个说话人列表，也不能作为已经实现该弹窗的证据。

### F2：弹窗结构和视觉规格不一致，优先级 P0

原型为 520px 默认宽度、16px 圆角、居中模态，header/body/footer 分区，背景为 rgba(17,24,73,.42) 加 2px 模糊；正文滚动、底部动作保留可见。原型具体定义在 HTML 第 118–128 行。

当前为 920px、10px 圆角、88dvh，整个窗口滚动，背景透明度 .35 且无原型模糊效果。内容包含版本恢复、纠错增强、全部说话人、全部片段和合并；命名任务没有独立底栏。这属于界面组成差异，不能通过一组 CSS 修成原型。

### F3：命名、合并、拆分混用同一编辑器，优先级 P0

TranscriptSpeakerPanel.edit 会预选目标说话人的片段，这部分已实现，实测林晨为 4 段。但编辑模式依然列出全文 10 段并允许增减选择，还同时出现合并区域。

原型单人命名默认更新目标人物在当前稿中的全部关联片段；片段归属调整应由拆分或明确的高级标注承担。必须拆开入口和上下文，避免用户在改名过程中无意重分配其他人的发言。

### F4：组织架构选择只做了一部分，优先级 P1

- 当前入口文案为“自定义名称 / 企业人员”，原型为两个标签页“从组织架构选择 / 输入外部人员名称”。
- 当前默认 manual，组织搜索需要额外点击，不是原型的默认目录选择。
- TranscriptSpeakerPanel.searchPeople 每次只传 cursor=null，丢弃 nextCursor；真实接口已有下一页。
- 当前人员项显示姓名、部门、stableId，Person 契约没有 title；stableId 不应冒充原型职务。
- 项目已有 usePeopleDirectory，支持部门目录、分页、失败重试和作用域清理，应复用。
- 生产 CompanyDirectoryAdapter.search 仍抛 ProviderUnavailableException；当前本地目录由 ControlledDirectoryAdapter 接固定联调服务。测试账号可查目录不等于正式公司目录已经接入。

依据：TranscriptSpeakerPanel.vue 第 23–38、81–91 行；meeting-options.ts；usePeopleDirectory.ts；IdentityContracts.Person；CompanyDirectoryAdapter；tools/dev/java/ama/localdev/LocalApplication.java。

### F5：生效范围及会议来源声纹提交缺失，优先级 P1

现有表单没有生效范围、授权确认、会议样本选择、审核提交状态或“我的语料反馈”声纹跟踪；仅提供跳转 /operations 的“登记本人声纹”。

现有 /api/v1/operations/voiceprints 接受 consentReference、expiresAt、wavBase64。JdbcGovernance.enroll 直接用 actor.enterpriseId() 写 subject_id；数据库外键绑定 enterprise_identity。该接口属于“本人登记”，不能为选中的林晨或任意外部人员登记。

现有能力可复用：规范 WAV 校验、非静音检测、模型嵌入、加密存储、PENDING/APPROVED/REJECTED/REVOKED 状态、管理员审核与撤销。必须补充会议来源、提交人和被命名人区分、授权凭据校验、样本归属以及跟踪接口。

当前样本校验为 16kHz、单声道、16bit PCM WAV、3–30 秒、RMS 不低于现有阈值。原型“约 42 秒，模拟”不可直接传给现有接口，也不能显示为实际质量结论。

依据：OperationsController.enroll；JdbcGovernance.java 第 17–43 行；GovernanceAudio.java；V062__operations_governance_mail.sql。

### F6：说话人显示模型缺少类型和资料，优先级 P1

SpeechSpeakerSnapshot 只有 id、label、enterpriseId、source=HUMAN、segmentIds。侧栏根据 enterpriseId 是否为空及名称正则推断“匿名 / 自定义名称”，关联企业人员后也只显示“已关联企业人员”，无法回显真实部门和职务；声纹提示固定为无匹配信息。

文件姓名则由 dialogueLabels 从正文前缀提取，按来源文件与姓名组成展示键，属于未确认的显示投影。它不是持久化 speaker UUID，也不是企业身份。截图中“4 个说话人”和弹窗“0 个标签”分别统计文件分组与人工快照，口径不一致。

需要显式表达来源分组、已确认身份类型、目录资料和声纹状态，统一数量含义。文件文字中的姓名前缀属于原正文，改标签不应隐式全文替换；展示需要去重时只调整展示层，并保留可查看原文的能力。

### F7：RAW 上打开编辑入口可能先写入，优先级 P1

TranscriptEditor.openSpeakers 在当前不是 WORKING 时调用 begin；begin 会执行 editor.restore。该调用链可能在用户尚未确认命名前创建新的工作版本。

这是静态代码确认的条件性问题；截图对应会议已是 WORKING，本轮运行核查没有触发该分支。修复后打开、取消、保留匿名、关闭应只有读取；确需从 RAW 建立工作稿时，应在用户确认后执行，并在中断时恢复原请求。

### F8：说话人读取与纠错读取耦合，优先级 P1

useSpeechCorrections.load 使用 Promise.all 一起读取 corrections 和 speakers，并在整体完成后才写入两个状态；纠错查询失败会使可单独使用的说话人读取也无法完成。人工命名可用于文本转写，不能把语音增强可用性作为前提。

需要拆开读取状态、错误、取消控制和重试；命名操作仍参与统一的当前稿写入互斥和原请求恢复，避免两个模块同时写版本。

### F9：音频匿名分组的读取链路尚不完整，优先级 P1

当前 speakerGroups 只合并已保存人工标签与正文姓名前缀。SpeechMediaPipeline 明确保留匿名模型簇于 typed checkpoints，RAW 段 speakerId=null；当前 speakers 读模型投影的也是人工快照。

由此可推导：没有姓名前缀且未人工标注的普通音频，左侧可能没有可直接命名的说话人分组。不能把已经执行 diarization 当成已经交付“说话人 1”的可编辑入口。

补齐时应单独投影匿名来源分组，保留来源文件、处理窗口、模型簇和片段映射。若识别正文只有整窗口时间、无法精确对应多个说话人，应明确“归属待核对”，允许人工选段；不能把整窗口自动认定为同一人，也不能仅按不同窗口同名簇合并身份。此项是代码推导，未在本轮创建音频会议实测。

### F10：测试验收对象偏离了原型，优先级 P1

tools/dev/transcript_dialogue_integration.mjs 明确断言侧栏编辑后出现“转写版本与语音审阅”，随后在其中填写“说话人名称”。transcript_workbench_integration.mjs 验证的也是综合表单。

现有 transcript-speaker-panel.test.ts 只验证人工标签、历史说明和合并按钮；现有 visual 测试没有目标命名弹窗基线。旧验收记录还将“通过说话人侧卡打开综合审阅窗口”描述为结果。它们可以说明既有能力可用，不能说明本次原型入口已正确交付。

修复必须修改验收目标，并保留原稿审阅等独立功能的回归；不能只让原来的错误入口测试继续通过。

## 4. 方案选择

| 方案 | 可解决的问题 | 代价及局限 |
| --- | --- | --- |
| A：保留综合表单，仅调整标题、宽度和顺序 | 部分外观 | 继续混淆片段标注与单人命名，声纹链路仍缺失，不满足请求 |
| B：独立命名弹窗，复用版本命令，补齐目录和会议声纹工作流 | 界面、会议命名、审核与反馈完整覆盖 | 需要前后端、契约及数据库增量，推荐 |
| C：重写转写及声纹全部领域模型 | 可以实现 | 已有不可变历史、恢复、权限、音频处理需要重新验证，范围和回归成本过大 |

推荐 B。该建议属于本轮修复设计，尚未执行，也不将它表述成用户已经批准的实施方案。

## 5. 目标界面及交互

1. 左侧“编辑”只打开 SpeakerNamingDialog，标题为“命名说话人 · 当前名称/匿名编号”。原稿、历史、增强和恢复入口保留在独立审阅窗口。
2. 采用原型 520px、16px 圆角及 header/body/footer 结构。正文超高时内部滚动；关闭、取消及主按钮可达，支持 Escape、焦点约束和关闭后回到触发按钮。
3. 命名方式使用原型两标签页。新匿名人物默认目录页；已有企业关联回填人员；已确认外部人物回填外部名称。文件前缀姓名只作为待确认初值，不自动选择同名企业员工。后两项为防止错误身份覆盖的建议行为。
4. 目录选择器展示姓名、部门、真实可用的职务，支持搜索和下一页；同名者按稳定 ID 选择。数据缺失不能填写原型的示例职务；查询中、无结果、服务失败、停用人员分别处理。
5. 外部名称去除首尾空白、拒绝空值，遵守现有 256 UTF-16 长度约束，处理中文输入法组合状态；服务端也验证。切换标签页不能遗留另一个人的 enterpriseId。
6. 生效范围默认“仅用于本次会议”。“提交声纹库审核”显示真实可用性及不可用原因。对当前 TXT 示例显示“本次会议没有可用原录音，无法提交声纹样本”，仍可正常保存命名。
7. 单人改名锁定打开时的目标分组及全部关联片段，不显示全文勾选列表。合并、拆分分别使用独立窗口；拆分才需要选段和回听。
8. 匿名人物底部按原型显示“保留匿名 / 确认命名”。已经命名或文件有原标签的情况，建议取消按钮显示“取消 / 保留原标注”，其语义是零写入退出，不能把“保留匿名”误实现成删除现有身份。
9. 成功后回读权威当前稿和说话人，更新侧栏、片段标签和搜索；已有纪要或确认被影响时给出重新确认/生成提示，保留历史内容。成功提示区分“本次会议命名已保存”和“声纹申请已进入审核”。
10. 请求未知或已受理但回读失败时，允许遵循当前项目恢复设计关闭展示层，但必须保留原 key/body、提供恢复入口、阻止重复的新写入；请求发送中禁用重复提交。不能把关闭当作取消了服务端操作。

## 6. 数据与保存设计

### 6.1 独立目标上下文

拟在 speaker-naming.ts 定义 SpeakerNamingTarget，至少包括：actorId、meetingId、sourceRevisionId、expectedRevisionToken、groupKey、origin、speakerSnapshotId（可空）、segmentIds、currentLabel、currentEnterpriseId。

- origin 区分 HUMAN、FILE、MODEL_CLUSTER、UNASSIGNED。
- groupKey 只定位来源分组；只有 speakerSnapshotId 可以指向已保存的人工作业快照。
- segmentIds 在打开时冻结；提交前核对仍属于同一版本、同一目标。正文筛选不改变命名范围。
- FILE 当前为“文件 ID + 名称”的合成展示键，不能提交到需要 UUID 的 speaker 字段。
- actor、meeting、权限、版本变化使上下文失效；不自动把旧内容套到新版本。输入可以保留给用户比对，但必须重新明确选择后才创建新请求。

### 6.2 复用当前会议内标注命令

现有 POST /api/v1/meetings/{meetingId}/transcript/speakers 已支持：

~~~json
{
  "expectedRevisionToken": "当前已捕获版本",
  "sourceRevisionId": "当前工作稿 UUID",
  "segmentIds": ["目标人物关联片段 UUID"],
  "label": "人工确认名称",
  "enterpriseId": null
}
~~~

选择企业人员时 enterpriseId 使用稳定企业 ID，外部命名为 null。第一阶段沿用此命令及 Idempotency-Key，不另建绕开版本、权限、原请求日志的保存接口。

RAW 场景在确认动作中先恢复为工作版本，得到并回读新的版本 token 后再提交命名。两个动作分别保存原请求；刷新后从实际完成步骤恢复，不重复建版本。最终命名的片段集合必须与用户确认范围一致。

重构 useSpeechCorrections 时分别暴露 loadSpeakers 和 loadCorrections，使用独立读取状态；写命令继续受统一互斥约束。useSpeakerNaming 提供 open(target)、submit(draft)、retryOriginal()、close()，负责上下文、命名草稿和与已有命令日志的衔接。

### 6.3 扩展显示资料时保留历史

需要明确的身份类型 ANONYMOUS / INTERNAL / EXTERNAL / UNCONFIRMED，而不是由名称正则推断。来源类型与身份类型分开：FILE 来源的姓名不等于已经确认的 EXTERNAL 人员。

目录 Person 增加可空 title 时，必须同步上游适配器、IdentityContracts、OpenAPI、前端严格响应校验、generated.ts 和测试；缺少真实提供方字段时保留 null。已有关联通过稳定 ID 回填，不能要求用户搜索到第一页才认出当前人员。

建议新增与 speaker snapshot 关联的不可变身份资料记录，保存确认时的类型及真实部门/职务快照；历史缺资料维持空值。若扩展现有快照字段，须引入兼容的快照/摘要版本，同步 TranscriptSpeakerProof、受限读取预算、canonical capture、MinutesContracts 表定义和纪要读模型，不能只改 SQL 后重算旧快照摘要。

命名继续产生新的工作版本和新的说话人快照，不原地修改旧快照。相同姓名不自动合并，改名也不替换正文中的同名文本。

## 7. 会议来源声纹审核设计

### 7.1 必须区分的身份及事实

提交人是会议负责人；被命名人是被选中的企业人员或独立外部主体；授权人及授权范围来自可验证授权凭据。这三者不能使用一个 actor 字段代替。

样本必须绑定会议、转写版本、说话人快照、片段、原文件摘要、真实采样范围及模型版本。服务端验证归属、来源可访问和授权，不接受前端传来的任意人物 ID、任意音频 URL 或“已授权=true”作为充分依据。

纯文本、无原音频的字幕、来源文件失效、没有可靠说话人范围、静音或时长不足分别返回明确原因；不隐藏整个命名能力。

### 7.2 拟议接口

以下为待新增契约，而非当前已有接口：

| 接口 | 责任 |
| --- | --- |
| GET /api/v1/meetings/{meetingId}/transcript/speaker-voiceprint-capabilities?sourceRevisionId=… | 按当前来源分组返回可提交性、真实可用音频范围、不可用原因；读取不创建申请 |
| POST /api/v1/meetings/{meetingId}/speaker-voiceprint-submissions | 根据已保存命名操作、主体授权和来源范围建立幂等申请，返回申请 ID 及真实状态 |
| GET /api/v1/meetings/{meetingId}/speaker-voiceprint-submissions/{submissionId} | 查询准备、审核、失败状态及可展示原因，支持原申请恢复 |
| 既有治理审核端点的扩展/适配 | 将合格申请交给现有声纹审核机制；待审核不能参与正式匹配 |
| 我的反馈列表及详情的声纹投影 | 按提交人查看申请和审核状态，与正式声纹主体分开授权 |

提交命令最少包含 expectedRevisionToken、sourceRevisionId、speakerSnapshotId、namingOperationId、subjectRef、consentGrantId、segmentIds、sampleRanges。sampleRanges 明确 sourceFileId、startMs、endMs；服务端核对它们来自目标人物且属于已授权来源，不依赖客户端计算的最终 WAV。

新增独立申请记录，至少存 applicantId、subjectRef、meetingId、命名结果/源版本、样本摘要与范围、授权 ID、申请状态、审核结果、失败原因、幂等键和时间。准备中的任务不是“已进入审核”。状态建议为 PREPARING → PENDING_REVIEW → APPROVED / REJECTED，另有 PREPARATION_FAILED、REVOKED、EXPIRED；状态与现有正式声纹记录关联，而非用一个成功布尔值替代。

### 7.3 两个业务结果分别恢复

~~~text
命名弹窗
  ├─ 仅本次会议 → 保存命名 → 回读当前稿 → 完成
  └─ 提交声纹审核 → 授权/样本确认窗口
       ├─ 取消 → 返回或退出，不提交命名/申请
       └─ 确认 → 保存命名 → 回读命名结果 → 提交声纹申请
                                      ├─ 进入审核 → 显示申请 ID/状态
                                      ├─ 样本或授权失败 → 保留命名，显示原因
                                      └─ 响应未知 → 恢复同一申请，不再次命名
~~~

这一选择依据需求 6.4 的“失败不撤销会议命名”。需要一个可恢复的前端操作状态将命名回执和申请回执关联，但不把两者伪装为跨模型调用的原子事务。命名失败时不提交声纹，提交声纹失败时不回滚命名。

命名提交后若用户又改了转写、合并了目标或撤销了命名，申请准备/重试必须重新核对来源；旧的已受理申请可查询原结果，不能悄悄改绑新版本。

### 7.4 外部人员与现有治理边界

当前 governed_voiceprint.subject_id 外键只能指向企业账号。完整支持外部人员审核，需要独立、稳定的外部主体与授权模型，不能为一个外部姓名创建虚假企业账号，也不能按姓名合并不同外部人员。

建议引入 voice subject 引用层，明确 ENTERPRISE 与 EXTERNAL，迁移现有本人声纹为企业主体引用，保留原主体、授权、密文 AAD 兼容和审计。服务端审核、匹配、撤销及主体有效性检查需同步适配；外部授权提供方未就绪时，本次会议外部命名照常可用，声纹选项显示确切限制，不能标记为完整的外部声纹支持。

D-11/D-15 中真实企业角色、允许用途、外部身份和保留策略仍有前置依赖。方案可以完成受控协议和合成数据验证；正式公司目录、真实他人/外部声纹启用需要落实这些已有产品依赖。本轮不修改这些政策，也不声称当前本人登记已经满足会议代提交。

### 7.5 复用媒体能力与治理能力

用现有 FfmpegNormalizer/受保护来源读取处理真实会议音频。按照当前治理校验准备 3–30 秒规范样本，时长、来源和质量检查结果来自实际数据；无足够可核实的单人音频时返回不能提交，不合成声音。已有 ASR 窗口/embedding 不能直接认定为某人的授权样本。

复用模型端口、加密特征及审核状态；普通用户接口不返回向量。审核者只能读取授权审核所需的信息，不能借审核权获得整场会议正文或录音访问。正式匹配仅使用已通过、未过期、未撤销且主体仍有效的记录，并继续返回待人工确认的身份候选。

## 8. 分阶段实施任务

每个任务都先建立能暴露当前差异的测试，再实现并运行相应验证。下列任务未开始；不要求本轮创建分支、提交或运行有业务写入的集成流程。

### T1：统一说话人目标与目录读模型

**Files:** 新增 web/src/features/transcript/speaker-naming.ts；修改 dialogue-labels.ts、TranscriptEditor.vue、TranscriptSpeakerSummary.vue；复用 features/meetings/usePeopleDirectory.ts、shared/api/meeting-options.ts。需要新的目录字段时同步 IdentityContracts.java、ControlledDirectoryAdapter.java、CompanyDirectoryAdapter.java、contracts/openapi/identity.yaml 及 generated.ts。

**Interfaces:** 产生 SpeakerNamingTarget 和显式来源/身份类型；消费当前保存版本、人工快照及文件来源。后续命名窗口只接收该目标，不重新从列表索引猜测对象。

- [ ] 加入“文件同名跨文件不误合并、已确认同名员工按 ID 区分、没有姓名前缀的音频不被伪造实名”的读模型测试。
- [ ] 复用目录状态实现 20 项后的下一页、部门过滤、同名选择、已关联人员回填；验证 503 与零结果可区分、晚到请求不可覆盖新对象。
- [ ] 明确匿名模型簇的来源映射；对暂不可精确归属的内容提供可核对的未归属状态，不把整窗口自动标成同一人。
- [ ] 对新增快照资料使用兼容迁移和历史读取测试，旧 RAW/WORKING/确认快照可读且内容不变。

**验收:** 初始列表数量口径一致，任何显示项都能解释其来源；目录翻页不会漏人或丢失选中身份。

### T2：交付独立命名弹窗和当前会议保存

**Files:** 新增 web/src/features/transcript/SpeakerNamingDialog.vue、useSpeakerNaming.ts；修改 TranscriptEditor.vue、TranscriptSpeakerPanel.vue、useSpeechCorrections.ts、MeetingDetail.vue；现有 shared/api/transcript-corrections.ts 仅在需要明确捕获版本接口时调整。

**Interfaces:** open(target: SpeakerNamingTarget)、submit(draft)、retryOriginal()、close()；draft 明确 namingMethod、label、enterpriseId、effectScope。会议内保存仍落到既有 speakers 命令。

- [ ] 建立“侧栏编辑打开命名弹窗且不打开综合审阅”的组件/浏览器失败测试。
- [ ] 建立 RAW/WORKING 两场景打开、保留匿名/取消、Escape 都为 0 次业务写入的测试。
- [ ] 实现原型标题、标签页、选择器、范围卡片和底栏；文件示例确认命名只提交对应 4 段，正文筛选不影响范围。
- [ ] 在确认阶段恢复 RAW 工作稿并保存命名；针对每一步丢响应、刷新、重复点击验证原 key/body 恢复及不重复版本。
- [ ] 拆开纠错和说话人读取；模拟 corrections=503、speakers=200，命名仍可用，错误只影响相应模块。
- [ ] 验证 409 保留输入并要求重新核对、403 清除私有内容、切会议/账号不串数据、中文输入法未完成不提交。

**验收:** 新建匿名、文件姓名、企业关联、外部姓名均能从正确入口完成当前会议命名；保存后刷新、重新进入仍正确；未受影响片段、正文和标记不变。

### T3：拆分相邻操作并完成身份回显

**Files:** 新增 web/src/features/transcript/SpeakerMergeDialog.vue、SpeakerSplitDialog.vue；修改 TranscriptSpeakerSummary.vue、TranscriptEditor.vue、useSpeakerUndo.ts；后端按需要修改 JdbcSavedTranscripts.java、TranscriptSpeakerRows.java、TranscriptSpeakerProof.java 和关联读模型。

**Interfaces:** 合并明确 source、target、影响片段集合、保留身份；拆分明确源分组、非空真子集和新的匿名身份；撤销继续调用既有受限制的版本撤销。

- [ ] 验证命名不会显示全文片段复选框或合并表单；合并和拆分各自打开原型对应窗口。
- [ ] 验证合并保留目标身份、不重复或丢失片段；拆分支持实际可播放片段定位，无音频时说明原因。
- [ ] 验证目录关联回显真实部门/职务，外部与未确认文件标签可区分，声纹审核状态与匹配状态分别展示。
- [ ] 验证“最近操作是正文编辑”时侧栏撤销不能回滚该正文；原确认、纪要、发布快照及 ACL 均不被命名隐式改写。

**验收:** 四个入口“编辑、合并、拆分、撤销”各自语义明确，历史保留且不越过已有撤销边界。

### T4：实现会议来源声纹申请

**Files:** 拟新增 backend/api/src/main/java/ama/api/MeetingSpeakerVoiceprintController.java、backend/core/src/main/java/ama/governance/MeetingSpeakerVoiceprintService.java、backend/adapters/src/main/java/ama/governance/JdbcMeetingSpeakerVoiceprints.java；扩展 GovernanceContracts.java、operations.yaml、transcripts.yaml、api.yaml、治理迁移；前端新增 SpeakerVoiceprintDialog.vue、speaker-voiceprints.ts 并接入 useSpeakerNaming.ts。

**Interfaces:** 第 7.2 节拟议端点和请求字段；内部主体引用区分申请人、被命名人、授权人；正式审核继续复用已有治理状态。数据库迁移编号在实施时按 main 最新编号分配，不能覆盖现有 V062/V064 等迁移。

- [ ] 先验证“负责人为另一企业人员提交”不会把声纹登记给负责人；外部人员不写入企业账号表。
- [ ] 实现无写入的可用性读取、服务端来源和授权验证、实际音频范围提取、样本质量校验及申请幂等受理。
- [ ] 验证 TXT、字幕无音频、过期来源、混合说话人范围、低质量/不足时长、授权缺失/过期均有明确结果。
- [ ] 验证命名成功后声纹失败仍保留命名；丢响应或页面刷新只恢复原申请，不再命名、不双重登记。
- [ ] 验证申请只有达到 PENDING_REVIEW 才显示“已进入审核”；准备失败不能显示成功；审核前不能参与正式匹配。

**验收:** 有授权且合格样本的会议申请能形成可查询的治理记录；无音频会议仍能完成本次会议命名。

### T5：补齐反馈、审核及撤销状态跟踪

**Files:** 修改 web/src/features/meetings/FeedbackPage.vue、FeedbackTableView.vue、useFeedbackList.ts；修改 features/governance/OperationsPage.vue、operations-types.ts；扩展反馈列表/详情契约及后端投影，接入新增会议声纹申请记录。

**Interfaces:** 声纹反馈项带类型、submissionId、meetingId、subject display、申请人与状态；既有文字反馈保持原语义。状态转换由服务端回执驱动。

- [ ] 验证提交人能在“我的语料反馈”找到申请，即使提交人不是声纹主体；其他普通用户不能读取。
- [ ] 验证管理员审核通过/驳回、主体撤销、授权过期可回显，且不会授予原会议访问权限。
- [ ] 验证撤销清理规则和现有加密上下文兼容；历史命名、会议正文及已生成纪要不被反馈状态回写。

**验收:** 从命名弹窗提交的申请、治理审核与我的反馈指向同一申请记录，没有互相独立的假成功状态。

### T6：以真实入口建立原型验收

**Files:** 新增 web/tests/e2e/speaker-naming.spec.ts、web/tests/visual/speaker-naming.spec.ts；新增相应 unit 测试；调整 tools/dev/transcript_dialogue_integration.mjs 和 transcript_workbench_integration.mjs 的入口断言，保留原稿审阅独立回归。

- [ ] 1440×900、1024×768 下，对目录页、外部页、无音频不可用状态、二次授权窗口进行截图与几何核对。
- [ ] 原型与产品使用相同的显示数据和字体条件，断言 520px 布局、标题、标签、范围卡片、底栏及遮罩；不能用改原型、隐藏缺失控件或扩大容差通过。
- [ ] 使用真实界面点击到真实命名、回读、刷新、审核跟踪；受控联调结果与正式企业能力验收分开说明。
- [ ] 运行相关 unit、typecheck、OpenAPI 生成一致性、build，再执行专用前后端集成测试和回归。只有改动、失败或未解风险需要时才扩大测试范围。

建议前端基础验证命令（均从项目根目录调用）：

~~~sh
PATH=/home/tanren/ama/.tools/node-v22.23.4-linux-x64/bin:$PATH npm --prefix web run test:unit
PATH=/home/tanren/ama/.tools/node-v22.23.4-linux-x64/bin:$PATH npm --prefix web run typecheck
PATH=/home/tanren/ama/.tools/node-v22.23.4-linux-x64/bin:$PATH npm --prefix web run check:api
PATH=/home/tanren/ama/.tools/node-v22.23.4-linux-x64/bin:$PATH npm --prefix web run build
PATH=/home/tanren/ama/.tools/node-v22.23.4-linux-x64/bin:$PATH npm --prefix web run test:e2e -- tests/e2e/speaker-naming.spec.ts tests/visual/speaker-naming.spec.ts
~~~

后端按新增测试类精确运行对应模块单元/集成测试，并沿用项目受控数据库及来源测试装配；不能在当前真实会议上用测试写入替代验收环境。

## 9. 交付顺序与完成标准

第一阶段完成 T1 的必要目标/目录基础及 T2，先消除错误入口并交付真正可保存的会议命名；第二阶段完成 T1 的完整来源投影及 T3，收口身份回显和相邻操作；第三阶段完成 T4、T5，交付声纹流程。T6 的对应测试贯穿各阶段，最终统一验收。

不能把第一阶段标记为整个原型已完成。完整交付需同时满足：

- 点击图 3a 打开图 2 对应命名窗口，全部控件及底栏可达。
- 企业/外部命名、取消、匿名、重开、刷新、并发、失败恢复符合定义。
- 只影响选中的人物及其当前稿关联范围，不改其它片段、正文、历史和权限。
- 声纹有真实音频、可核实主体授权、实际审核记录及跟踪；没有条件时显示真实限制。
- 本次会议命名与声纹申请结果分别记录，失败不会互相伪装或重复提交。
- 原稿、历史、增强、合并、拆分和撤销仍可独立使用。
- 目录职务、授权提供方及外部主体等尚未落实的正式环境依赖如实列出，不能用联调数据宣称已接入。

本轮输出为诊断与方案，未实施上述产品变更。
