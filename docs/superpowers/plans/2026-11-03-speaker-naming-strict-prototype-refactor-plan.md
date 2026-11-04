# 转写校正「说话人编辑」严格对照原型改造方案

> 状态：重新分析完成；本文是改造方案，尚未实施产品变更。
>
> 本文取代上一版《转写校正「说话人编辑」差异分析与修复方案》中的实施建议。原型和需求没有规定的界面、文案、交互，不作为自行增加的实施要求。
>
> 执行约束：在 /home/tanren/ama 根目录、main 分支，由单 agent 顺序实施；不创建分支或 worktree，不调用子 agent。本轮只编写方案文档，不修改产品代码、原型、接口契约或数据库。

**Goal：** 使转写校正侧栏“编辑”打开原型中的“命名说话人”弹窗，按原型完成组织人员选择、外部姓名、会议内命名和声纹审核二次确认，并落实需求规定的授权、样本、持久化、审核跟踪和版本边界。

**Architecture：** 将单人命名入口从综合审阅组件中分离；保留既有不可变转写版本和原请求恢复机制；补充版本化说话人名册、目录资料、会议来源声纹申请与反馈投影。界面结构和操作顺序由原型限定，真实数据校验和持久化由需求文档限定。

**Tech Stack：** Vue 3、TypeScript、Spring Boot / Java 21、MySQL、现有音频处理及声纹模型端口、Node test runner、Playwright。

**分析基线：** 2026-11-03，main，HEAD 为 4bf637611958c8ba5e4a9aa308572618a6f8b334，包含核查时工作区已有未提交内容。工作区存在其他任务的变更，实施前须重读相关代码，不覆盖或撤销这些变更。

## 1. 设计依据及使用边界

### 1.1 权威来源

| 编号 | 依据 | 本方案中的作用 |
| --- | --- | --- |
| S1 | [原型 HTML](/home/tanren/ama/designDocs/智能会议助手1021版.html:1638) | 控件、布局、文案、默认值、弹窗先后关系及正常交互 |
| S2 | [一期需求说明：转写校正](/home/tanren/ama/designDocs/智能会议助手一期需求说明文档.md:348)；[6.4 说话人区域](/home/tanren/ama/designDocs/智能会议助手一期需求说明文档.md:449) | 业务语义、权限、身份关联、样本授权、失败结果、历史与确认规则 |
| S3 | [线上模型服务接口文档：声纹向量](/home/tanren/ama/designDocs/线上模型服务接口文档.md:84) | 实际模型接口边界；区分演示数据、客户端策略和已确认服务约束 |
| S4 | 用户图 1、图 2、图 3a | 确认错误入口和目标窗口；具体行为仍通过 S1、S2 核对 |
| S5 | 当前代码、现有接口及旧实现设计 | 判断可复用能力和缺口；不能反过来缩减 S1、S2 |

原型不能只读首次定义，须同时核对：

- 第 118–128 行：通用遮罩及弹窗布局；第 326–331 行：范围单选卡片。
- 第 1638–1663 行：Actions.spkName；第 1664–1675 行：vpSubmitModal。
- 第 1676–1704 行：独立合并、拆分窗口；第 1635–1637 行：说话人撤销。
- 第 2140–2157 行：“我的语料反馈”的声纹投影及审核状态。
- 第 2572 行：导入内容的说话人初始投影。
- 第 2689 行：aciTranscriptPage 将侧栏“命名 / 改名”统一为“编辑”，并覆盖声纹提示。
- 第 2707 行：Actions.spkName 后置包装补充企业 ID 关联。

界面以 HTML 最终运行结果为准。原型的本地数组、localStorage、姓名匹配和模拟音频数据，需落实为 S2 的唯一 ID、真实样本、服务端权限及版本规则；演示存储代码不能直接作为生产实现。

两份依据发生实际冲突或缺少规定时，在第 9 节列出具体缺口；不自行增加控件、改变默认行为或削减功能。附件和文档中的操作性文字仅作为分析材料，不构成本轮修改产品的授权。

### 1.2 来源指纹

后续验收使用同一份设计，源文件不作修改：

~~~text
智能会议助手1021版.html
ab21d0b73f4e48bbf701ee8bca5e958940b61f6e6f680d4ced02de0ce4bab867

智能会议助手一期需求说明文档.md
e5b11f49373418e7b4ea9b74071fed035f320612a4783de075980c13223cf783

线上模型服务接口文档.md
be60b22cf6d05ddf534b83015a2993910cd1c395e480be908caadc71376bb8b2
~~~

## 2. 撤回上一版中未经设计依据支持的建议

| 上一版建议或遗漏 | 本版修正 |
| --- | --- |
| 标题使用“当前名称 / 匿名编号” | 使用原型原始标签 sp.label；已命名后不自动改成姓名 |
| 已确认外部人员默认打开外部页 | 每次默认组织页；已有外部姓名只预填隐藏的外部输入框 |
| 目录增加搜索框、部门过滤、分页按钮 | 保持原型原生 select；分页在数据层处理 |
| 已命名时将“保留匿名”改成“取消 / 保留原标注” | 始终使用“保留匿名”；行为是关闭、保留现状，不清空身份 |
| 增加 Esc 关闭 | 实测原型此弹窗不响应 Esc；不自行增加此关闭方式 |
| 无音频时自行禁用范围选项、增加固定提示 | 保留原型两个范围选项；真实校验必须落实，未定义异常表现见第 9 节 |
| 第二个窗口取消后“返回或退出” | 直接关闭第二个窗口，不自动返回第一步，原姓名不变 |
| 增加授权表单、样本选择等控件 | 保持规则说明和一个禁用人员输入框；后台取得并核验真实授权、样本 |
| 声纹样本统一裁成 3–30 秒 | 这是现有本人登记限制，不是 S1、S2、S3 确认的产品要求 |
| 拆分必须为非空真子集 | 至少一段即可；允许全选，保留源说话人的零片段记录 |
| 现有受限撤销等同完整原型撤销 | 单步版本回退不足以证明完整实现；业务撤销和 B12 边界单独核验 |
| 声纹状态只列待审核、通过、驳回 | 补齐“需要补充”及反馈展示，不自行新增补交页面 |

上一版“入口错误、综合表单混用、目录资料缺失、会议来源声纹链路缺失”的诊断仍成立。界面和流程执行要求以本文为准。

## 3. 重新核查结果与根因

### 3.1 原型运行核验

本轮用独立浏览器上下文直接打开原型 HTML 的会议转写路由，执行原型自身按钮。未连接产品后端，模拟交互只影响临时浏览器中的原型数据。

| 核查场景 | 实际结果 |
| --- | --- |
| 最终侧栏按钮 | 五个说话人均为“编辑” |
| 已命名内部人员首次打开 | “命名说话人 · 说话人 1”；组织页选中；范围 once 选中 |
| 1440×900 内部人员弹窗 | 520×约 453.31 CSS px；16px 圆角；max-height:86vh |
| 遮罩 | rgba(17,24,73,.42)，blur(2px) |
| Esc / 空白遮罩 | Esc 不关闭；点击遮罩关闭 |
| 外部名称只有空格 | 保留弹窗，提示“请输入外部人员名称” |
| 选择声纹范围后确认命名 | 关闭第一步，打开“提交声纹库审核”；一个禁用人员输入框 |
| 第二步“取消” | 两个弹窗均关闭，原姓名未改变 |
| 已命名外部人员重开 | 默认组织页；外部姓名已预填，但输入区隐藏 |
| 1024×768 外部人员弹窗 | 宽 520、高约 500.11 CSS px；含原型黄色说明 |
| 拆分选中来源全部 5 段 | 成功；总片段仍为 14；源说话人保留且 0 段；新增匿名人承接 5 段 |

高度为当前字体、数据和浏览器条件下的观测值，不是固定 CSS 高度；不能强制所有场景同高。

### 3.2 当前实现责任定位

| 编号 | 当前问题及证据 | 改造方向 |
| --- | --- | --- |
| G01 | [侧栏事件](/home/tanren/ama/web/src/features/transcript/TranscriptSpeakerSummary.vue:7) → [会议页](/home/tanren/ama/web/src/features/meetings/MeetingDetail.vue:94) → [openSpeakers](/home/tanren/ama/web/src/features/transcript/TranscriptEditor.vue:146) → openReview | 独立命名窗口，不能滚动到综合表单代替 |
| G02 | 综合窗口混合版本恢复、纠错、人工标签、全文选段和合并，宽 920px | 命名、合并、拆分分别对应原型窗口 |
| G03 | [当前表单](/home/tanren/ama/web/src/features/transcript/TranscriptSpeakerPanel.vue:23) 目录只取第一页，显示搜索表单及列表 | 原型 select，完整目录数据，稳定 ID 选择 |
| G04 | [Person](/home/tanren/ama/backend/contracts/src/main/java/ama/contract/IdentityContracts.java:19) 缺职务；[正式目录适配器](/home/tanren/ama/backend/adapters/src/main/java/ama/identity/CompanyDirectoryAdapter.java:5) 尚不可用 | 补真实职务及提供方，不以 stableId 充当职务 |
| G05 | 非 WORKING 时 openSpeakers 先 begin，打开可能创建版本 | 创建工作稿延后至实际确认 |
| G06 | [读取](/home/tanren/ama/web/src/features/transcript/useSpeechCorrections.ts:17) 用 Promise.all 耦合纠错与说话人，且只读 WORKING | 独立读状态，允许从当前可展示源稿取得命名上下文 |
| G07 | 快照缺原始标签、类型和部门/职务；文件姓名为派生分组 | 增加明确身份、来源和资料快照 |
| G08 | [写入](/home/tanren/ama/backend/adapters/src/main/java/ama/transcript/JdbcSavedTranscripts.java:144) 每次产生新快照，列表只从有归属片段投影 | 逻辑说话人及版本名册，保留稳定标签、零片段项、目标身份 |
| G09 | 拆分禁止全选；[撤销](/home/tanren/ama/web/src/features/transcript/useSpeakerUndo.ts:6) 只回退当前纯说话人版本 | 修正限制；避免多次撤销成为撤销/重做来回切换 |
| G10 | 仅跳转“登记本人声纹”；[enroll](/home/tanren/ama/backend/adapters/src/main/java/ama/governance/JdbcGovernance.java:17) 将 actor 写为主体 | 区分提交人与被命名人，新增会议来源申请 |
| G11 | [GovernanceAudio](/home/tanren/ama/backend/adapters/src/main/java/ama/governance/GovernanceAudio.java:6) 限 3–30 秒；[数据库约束](/home/tanren/ama/backend/adapters/src/main/resources/db/migration/V062__operations_governance_mail.sql:41) 同样限制样本计数和状态 | 适配真实样本、授权、审核状态及数据库约束，不能只放开前端 |
| G12 | [反馈表](/home/tanren/ama/web/src/features/meetings/FeedbackTableView.vue) 未接会议声纹申请 | 接入原型字段、状态及计数 |
| G13 | 集成脚本把“编辑打开综合审阅”当正确断言 | 以原型指定入口和两步流程验收 |

此前产品只读复现确认 G01–G03：截图对应 TXT 会议有 10 段，点击“林晨”进入 920px 综合窗口，预选其 4 段，没有“生效范围”。本轮未向该会议提交操作；此前 17 项局部测试通过不能证明本方案已实现。

## 4. 必须保持一致的前端设计

### 4.1 第一层：命名说话人

| 设计项 | 必须实现的原型定义 |
| --- | --- |
| 入口 | 侧栏对应说话人的“编辑”；一人一个目标上下文 |
| 标题 | “命名说话人 · {原始标签}”，对应 sp.label，不用后来选择的姓名替换 |
| 第一个字段标题 | “命名方式” |
| 标签页顺序 | “从组织架构选择” → “输入外部人员名称” |
| 初始页签 | 每次均为组织页，包括已命名外部人员 |
| 组织控件 | 单个原生 select；“姓名 · 部门 · 职务”；仅有效人员 |
| 组织选择 | 已关联人员按唯一 ID 回填；无有效匹配时为首个有效选项；不新增“请选择”改变默认值 |
| 外部控件 | 单文本框；placeholder“例如：陈分析师（某券商）” |
| 外部预填 | 目标明确 external 且有姓名时回填；切页签不清空输入 |
| 第二个字段标题 | “生效范围” |
| 范围 1 | “仅用于本次会议”；“不进入声纹库，其他会议不受影响”；默认选中 |
| 范围 2 | “提交声纹库审核”；“进入平台管理员审核队列，审核通过前不会成为正式声纹” |
| 外部目标说明 | “外部人员或声纹无匹配结果时，可继续保留匿名说话人，不强制实名。” |
| 说明条件 | 打开时目标为 external；不能仅因切到外部页就新增该说明 |
| 底部 | 始终“保留匿名 / 确认命名”；ghost / primary 及顺序保持 |
| 关闭 | X、空白遮罩、“保留匿名”；未提交时不改名、不清空身份、不建版本 |
| Esc | 原型该窗口没有此关闭行为，本次不自行增加 |
| 内容范围 | 无历史、恢复、增强、全文复选框、合并表单、本人声纹登记入口 |

原型导入处理对有效目录作精确姓名匹配，唯一匹配时带入企业 ID。承接该行为须使用真实目录和唯一 ID；不能据姓名生成账号、推断授权或授予会议权限，同名多项不能任选一项。实际选择和提交以 select 对应的唯一 ID 为准。

### 4.2 第二层：提交声纹库审核

| 设计项 | 必须实现的原型定义 |
| --- | --- |
| 进入 | 第一步选声纹范围并点“确认命名” |
| 窗口关系 | 第一层关闭后开第二层，不叠放、不在第一层展开长表单 |
| 标题 | “提交声纹库审核” |
| 规则区 | 原型蓝色 info banner、shield 图标、“请确认以下规则：”及四条规则 |
| 四条规则 | 对应人员授权；合格声音片段；进入管理员审核队列且审核前不是正式声纹；负责人不能下载声纹特征 |
| 人员字段 | “被命名人员”；单个 disabled 输入框；姓名及有值时的“ · 部门” |
| 底部 | “取消 / 确认提交审核” |
| 取消 / X / 遮罩 | 退出，不自动回第一步；提交前不改名、不创建申请 |
| 不增加的控件 | 授权勾选、授权编号、有效天数、上传 WAV、选片段、下载特征 |

规则区原文如下；“对应人员授权”“平台管理员审核队列”“不能下载声纹特征”保留原型加粗：

~~~text
请确认以下规则：
· 提交声纹需要对应人员授权；
· 提交的是符合质量要求的声音片段（本会议中约 42 秒，模拟）；
· 提交后进入平台管理员审核队列，审核通过前不会成为正式声纹；
· 会议负责人不能下载声纹特征。
~~~

“本会议中约 42 秒，模拟”是样例数据槽位，不是真实样本结论。规则行的位置和结构不变，时长、质量接真实结果；不固定 42 秒，不将旧 3–30 秒限制写成产品规则。无真实结果时的占位/失败表现见 D02，不先显示样本已合格。

### 4.3 视觉规格

以下来自通用类及命名窗口内联样式，不能套用“添加参会人”等其他窗口的专用样式。

| 对象 | 原型规格 |
| --- | --- |
| 遮罩 | fixed、inset:0、居中 flex、rgba(17,24,73,.42)、blur(2px)、z-index:900 |
| 容器 | width:520px；max-width:92vw；max-height:86vh；白底；16px 圆角；纵向 flex |
| 阴影 | 0 8px 30px rgba(17,24,73,.14) |
| 头部 | padding:16px 22px；底边 1px #E7E7E2；标题 16px、700、#111849 |
| 正文 | padding:20px 22px；overflow-y:auto，不将整个窗口变成长页面 |
| 底部 | padding:14px 22px；顶边 1px #E7E7E2；右对齐；gap:10px |
| 字段 | margin-bottom:14px；标签 12.5px、500、#6F7482，底距 6px |
| 标签页 | gap:4px；本窗口底距 10px；padding:9px 16px、13.5px |
| 输入/select | 1px #DEDED8；9px 圆角；padding:8px 12px；13px；保留 select 箭头 |
| 范围卡片 | padding:9px 12px；gap:8px；1px 边框；10px 圆角；底距 8px |
| 选中范围 | 边框 #FF6B1A；背景 #FFF0E8；15px 圆点及原型内部选中点 |
| 按钮 | padding:8px 16px；13px；9px 圆角；主按钮 #FF6B1A，hover #E85A0E |
| 字体 | 原型 PingFang SC / Microsoft YaHei / Helvetica Neue / Arial / sans-serif；正文 14px、line-height:1.55 |
| 动画 | 遮罩 fadeIn .18s、弹窗 popIn .2s；截图等待稳定 |

验证最终 computed style 和截图，而非只验证存在同名 CSS 类。原型标签页等声明本身存在后置覆盖，须以实际浏览器结果为准。

## 5. 业务流程和保存边界

### 5.1 操作时序

~~~mermaid
flowchart TD
    A[侧栏编辑：读取目标和目录] --> B[命名说话人]
    B -->|保留匿名 / X / 遮罩| C[关闭，保留原状态]
    B -->|仅本次会议：确认命名| D[保存当前会议命名]
    B -->|提交声纹审核：确认命名| E[第二层声纹确认]
    E -->|取消 / X / 遮罩| C
    E -->|确认提交审核| F[保存当前会议命名]
    F -->|命名成功| G[核验授权及样本并提交申请]
    F -->|命名失败| H[保留输入，不创建声纹申请]
    G -->|真实进入审核队列| I[原型成功提示及我的语料反馈]
    G -->|失败| J[保留已保存命名，返回真实失败原因]
~~~

可预先读取授权和样本能力，但不能第一次点“确认命名”就写入命名或创建申请。该分支第二次确认才是提交动作。

### 5.2 结果规则

| 编号 | 场景 | 规定 |
| --- | --- | --- |
| B01 | 外部名称空白 | trim 后空则不提交；原型 toast“请输入外部人员名称”；窗口保留 |
| B02 | 仅本次会议 | 更新当前稿关联，不入声纹库；toast“说话人已命名 / {姓名}（仅本次会议）” |
| B03 | 选择范围/切页签 | 只改本地草稿，不改会议、身份或申请 |
| B04 | 匿名或已命名目标取消 | 均保持原状，“保留匿名”不等于主动匿名化 |
| B05 | 声纹二次确认前取消 | 0 次命名/申请写入；原型包装函数的本地 save 不转化为产品业务写入 |
| B06 | 命名成功、声纹失败 | S2 明确保留命名；独立保存两个结果，不整体回滚 |
| B07 | 声纹成功提示 | 真正入审核队列才提示“已提交声纹审核 / 可在「我的语料反馈」中跟踪审核状态” |
| B08 | 准备中/结果未知 | 不冒充待审核；恢复同一请求，不重复命名或申请 |
| B09 | 无音频/可靠范围 | 命名仍可用；不虚构样本、声音、42 秒、质量或审核成功 |
| B10 | 已确认稿修改归属 | 新工作稿须重新确认；保留旧确认、纪要、行动项及发布快照 |
| B11 | 权限 | 负责人可修改，参会人只读；命名不授权；治理管理员不自动获得整场会议访问 |
| B12 | 失败/冲突 | 保留输入，不将旧目标改绑新版本；服务端权限、版本和幂等校验 |

真实网络异常在原型中未完整建模，数据行为由 S2 明确；异常 UI 复用已有明确规范，缺项列在第 9 节，不借此新增常驻控件。

### 5.3 相邻操作

| 操作 | 原型界面与功能 |
| --- | --- |
| 合并 | 独立“合并说话人”；说明“将「来源说话人」的全部发言合并到「目标说话人」，可撤销。”；来源 select 含段数、目标 select；底部“取消 / 合并” |
| 合并结果 | 来源目标不能相同；全部归入目标、保留目标身份、移除源名册项；toast“合并完成 / 可点击「撤销」回退” |
| 拆分 | 独立“拆分说话人”；说明“选择要拆分出去的说话片段，将生成新的匿名说话人。”；“源说话人”“选择片段”；列表 max-height:240px；“取消 / 拆分为新说话人” |
| 拆分选择 | 只列来源片段，初始不选，至少一段；切来源重置；允许全选 |
| 拆分结果 | 新匿名人承接选中段，源项保留（可 0 段）；toast“拆分完成 / 新说话人已生成，请命名”；不自动弹出命名窗口 |
| 拆分回听 | S2 要求支持；S1 时间文本没有回听绑定，差异见 D04，不删需求或自行增播放器 |
| 撤销 | 保留侧栏入口，恢复已定义范围的身份和归属；不借综合审阅窗口完成；历史范围见 D03 |

## 6. 数据、接口和后端改造

以下为支撑既定界面的工程方案，不是新增用户功能；拟新增的名称不代表当前已有接口或文件。

### 6.1 版本化说话人上下文

现有每次人工标注都会产生新的 speaker snapshot UUID。该 UUID 不能同时承担“整个会议中同一说话人的稳定编号”和“某次不可变身份快照”两个职责。

新增逻辑说话人及版本名册关系，至少表达：

| 数据 | 作用 |
| --- | --- |
| logicalSpeakerId / groupKey | 定位同一说话人，服务端分配或验证；姓名不是主体唯一键 |
| originalLabel | 原型 sp.label，供标题、匿名回显；改名后保留 |
| sourceKind / sourceRef | 人工、导入文件、模型匿名分组的来源；文件展示键不是 UUID |
| identityKind | ANONYMOUS / INTERNAL / EXTERNAL；未确认文件姓名保留来源事实，不新增可见“第四种身份”标签 |
| identitySnapshotId | 当前版本采用的不可变资料；改名产生新记录，不改旧记录 |
| name / enterpriseId / department / title | 显示与关联；内部按稳定 ID，部门/职务来自真实目录 |
| revision roster | 该版本说话人名册、顺序及关系，包含零片段项 |
| segmentIds | 当前归属片段；每段最多一个归属，不丢失、不重复 |
| voice display | 真实可证实的匹配和提示；审核中不等于匹配成功 |

扩展现有说话人读接口：保留 items 原语义，增加 groups 视图，支持当前 RAW 或 WORKING 来源，并独立于纠错读取。写入仍针对经版本检查的工作稿。同步 OpenAPI、严格响应解析、生成类型及读模型。

侧栏仍使用原型“匿名 / 外部”、部门、段数和最终“声纹匹配度”文本，不向用户暴露技术来源枚举或 UUID。已关联的真实目录资料应能回显，不能用“已关联企业人员”替代原型部门。

历史兼容使用增量名册/身份资料，不修改旧 snapshot 摘要算法或重算旧摘要。新增事实须进入完整读取、证据校验、读预算和纪要来源校验；旧记录缺资料时保持未知，不能回填成今天的目录信息。

匿名音频分组从真实 diarization checkpoint 的来源、窗口及片段映射投影；无姓名前缀也应可编辑。不得把不同窗口相同模型编号直接认定为同一真实主体，或把混合窗口作为单人声纹样本。

### 6.2 明确的说话人业务命令

复用现有 POST /api/v1/meetings/{meetingId}/transcript/speakers 的版本写入、幂等回执和审计。在该服务上补充明确操作含义的契约，兼容旧命令；不能只用“任意 segmentIds + label”覆盖全部原型语义。

| 操作 | 命令事实 | 服务端行为 |
| --- | --- | --- |
| RENAME | 目标逻辑 ID、源版本、身份方式、企业 ID 或外部姓名 | 取得目标全部片段，只更新该目标身份；0 段也可改名册 |
| MERGE | 来源、目标逻辑 ID、源版本 | 两者不同；全部来源归入目标；保留目标逻辑身份/资料，移除源名册项 |
| SPLIT | 来源 ID、非空片段集合、源版本 | 片段均属于来源；创建新匿名人；保留源项；允许全选 |
| UNDO_SPEAKER | 被撤销操作 ID、当前版本 | 新建工作版本恢复支持范围内的身份/归属，不以覆盖旧全文的方式撤销 |

公共字段为 expectedRevisionToken、sourceRevisionId、Idempotency-Key。目标范围由服务端复核，正文筛选不能缩小命名范围。

打开时冻结 actor、meeting、source、revision token、目标 ID 及归属范围；切账号、切会议、失权或版本变化时，旧请求不能写入新上下文。

RAW 上实际确认后复用建立工作稿操作，回读新版本再命名；两步分别保存原 key/body 及回执，响应未知时恢复原步骤。取消不建稿。未保存正文不能被隐式丢弃，也不能将命名提交到落后版本；冲突和恢复 UI 缺口见 D05。

### 6.3 目录数据补齐

1. Person 增加真实可空 title，打通提供方、Java 契约、投影、OpenAPI、前端严格解析及生成类型。
2. 保留单个 select，通过游标接口后台完整加载，按 stableId 去重并保持服务端顺序；不能只展示前 20 条。
3. 已选 ID 独立于页面保存，迟到请求不能改变选择；已有有效关联按 ID 回填。
4. 目录容量超过当前有界读取预算时，通过提供方快照/缓存及后端分页解决，不擅自加搜索表单或截断为第一页。
5. 不伪造职务，不以 ID 替代；空值显示、目录失败、空目录的具体交互见 D02。
6. S2 要求企业搜索接口，不等于要求在此窗口增加搜索框。正式 CompanyDirectoryAdapter 接入是交付依赖。

### 6.4 会议来源声纹申请

当前本人登记接口不能替代此流程。新增会议申请，复用模型提取、特征加密、治理审核基础，单独表达提交人、主体、授权和来源。

| 拟议接口 | 责任 |
| --- | --- |
| GET /api/v1/meetings/{meetingId}/transcript/speaker-voiceprint-capabilities | 读取指定源版本/目标的授权及样本能力，不创建申请或命名 |
| POST /api/v1/meetings/{meetingId}/speaker-voiceprint-submissions | 命名保存后，按原请求幂等受理该命名对应的申请 |
| GET /api/v1/meetings/{meetingId}/speaker-voiceprint-submissions/{id} | 查询同一申请的准备、审核及失败结果，不返回特征向量 |
| 既有治理审核接口的会议申请适配 | 覆盖“待审核 / 已通过 / 已驳回 / 需要补充” |
| 既有“我的语料反馈”读取扩展 | 按提交人读取申请，与文字反馈共用原型列表 |

申请至少绑定 namingOperationId、meetingId、sourceRevisionId、logicalSpeakerId、identitySnapshotId、subjectRef、consentGrantId、sampleDescriptor、Idempotency-Key。sampleDescriptor 包含来源文件、摘要、片段及起止范围，由服务端生成或核验，不能用任意音频 URL 替代。

subjectRef 区分 ENTERPRISE / EXTERNAL。提交人为负责人，主体可为另一企业人员或外部人员。外部姓名不是全局唯一身份；不能创建虚假企业账号或将声纹登记到负责人名下。现有企业外键、加密 AAD、匹配过滤和主体有效性判断均须兼容扩展。

服务端校验主体有效授权、用途、来源可访问性、单人范围、格式和实际质量；“选择了范围”“点击确认”都不能代替对应人员授权。授权取得渠道见 D01，不在命名窗口自行增加采集表单。

复用真实会议音频及规范化能力。S3 的 50 秒是客户端目标策略，原型 42 秒为模拟，GovernanceAudio 的 3–30 秒是旧入口限制，三者不能互相冒充产品硬约束。会议申请需采用正式确认并实际验证的样本策略，不擅自确定新的秒数门槛。

数据库也必须同步核查：V062 中 ck_voice_vector 将 sample_count 限制为 48000–480000，ck_voice_state 只允许 PENDING / APPROVED / REJECTED / REVOKED。会议样本及“需要补充”不能直接写入而不调整模型。通过新增申请记录承载准备/审核流程，并为正式特征记录设计兼容的主体和样本计数约束；使用新迁移处理，不编辑已经执行的 V062。不能只改 Java 校验或前端枚举后宣称链路完成。

### 6.5 双结果、状态与恢复

- 会议命名回执、声纹申请回执独立；申请失败不删除命名。
- 内部区分 PREPARING、PREPARATION_FAILED、PENDING_REVIEW、APPROVED、REJECTED、NEEDS_MORE；前两个是工程准备结果，不新增原型反馈标签。
- PENDING_REVIEW 才显示“待审核”；后三者对应“已通过 / 已驳回 / 需要补充”。
- 已有治理的授权过期、撤销、特征清除须保留其真实语义，不伪映射为“已驳回”，也不借此自行扩展反馈 UI。
- 丢响应、刷新、重试恢复同一 key/body；命名后来变化不能悄悄改变旧申请主体及来源。
- 审核前不进入正式匹配；普通会议接口不得提供向量下载。
- “需要补充”必须能由真实审核产生、查询、展示；原型未定义补交入口，不新增补交页面。

### 6.6 我的语料反馈

沿用原型“全部 / 待审核 / 已通过 / 已驳回 / 需要补充”标签及计数。七列表格保持：

~~~text
类型 | 原始值 | 建议值 / 人员 | 来源会议 | 提交时间 | 状态 | 审核意见
~~~

声纹项类型“声纹样本”，原始值“会议匿名说话人”，建议值为人员；审核意见为空时按原型显示“等待平台管理员审核”。按 applicantId 过滤本人提交，不按姓名或 subjectId 过滤；负责人为他人提交也应出现在负责人的列表。

仅补齐此链路需要的数据和审核能力，不重设计整个平台运营页。缺少的具体管理端动作设计见 D06。

## 7. 可顺序执行的改造任务

全部尚未执行。每项先建立可揭示行为差异的验证，再实现；验证围绕用户动作、回执、持久化及历史，不只检查模板里有没有某个字符串。

### T01：建立原型基线与失败用例

**依赖：** 无。

**文件：** 原型只读；拟新增 /home/tanren/ama/web/tests/e2e/speaker-naming.spec.ts、/home/tanren/ama/web/tests/visual/speaker-naming.spec.ts；调整[对话集成脚本](/home/tanren/ama/tools/dev/transcript_dialogue_integration.mjs)。

- [ ] 固定 S1 指纹、相同显示数据、字体、视口，采集两个窗口及外部分支基线。
- [ ] 从真实“编辑”入口验证标题、控件、默认页/范围、底栏及窗口关系。
- [ ] 增加关闭无写入、第二步取消、外部空白、全选拆分的失败用例。

**完成标准：** 能区分综合表单和原型；仅缩小旧表单宽度不能通过。

### T02：补齐目录与读取上下文

**依赖：** T01。

**文件：** [IdentityContracts](/home/tanren/ama/backend/contracts/src/main/java/ama/contract/IdentityContracts.java)、[ControlledDirectoryAdapter](/home/tanren/ama/backend/adapters/src/main/java/ama/identity/ControlledDirectoryAdapter.java)、[CompanyDirectoryAdapter](/home/tanren/ama/backend/adapters/src/main/java/ama/identity/CompanyDirectoryAdapter.java)、[目录前端 API](/home/tanren/ama/web/src/shared/api/meeting-options.ts)、[来源标签投影](/home/tanren/ama/web/src/features/transcript/dialogue-labels.ts)、[useSpeechCorrections](/home/tanren/ama/web/src/features/transcript/useSpeechCorrections.ts)、[身份契约](/home/tanren/ama/contracts/openapi/identity.yaml)。

- [ ] 接入 title 真实来源，完整消费目录游标。
- [ ] 分离说话人/纠错读状态及失效控制，纠错失败不阻断命名。
- [ ] 明确 RAW、WORKING、文件、模型匿名目标；展示键不能传作持久化 UUID。
- [ ] 验证默认选中、ID 回填、同名、20 人以后选项、迟到请求、停用人员。

**完成标准：** 命名得到完整可信上下文；有后台分页，无新增可见搜索/分页控件。

### T03：版本名册与说话人业务命令

**依赖：** T02。

**文件：** [JdbcSavedTranscripts](/home/tanren/ama/backend/adapters/src/main/java/ama/transcript/JdbcSavedTranscripts.java)、[TranscriptSpeakerRows](/home/tanren/ama/backend/adapters/src/main/java/ama/transcript/TranscriptSpeakerRows.java)、[TranscriptSpeakerProof](/home/tanren/ama/backend/core/src/main/java/ama/transcript/TranscriptSpeakerProof.java)、[TranscriptContracts](/home/tanren/ama/backend/contracts/src/main/java/ama/contract/TranscriptContracts.java)、[MinutesContracts](/home/tanren/ama/backend/contracts/src/main/java/ama/contract/MinutesContracts.java)、[转写契约](/home/tanren/ama/contracts/openapi/transcripts.yaml)；在 /home/tanren/ama/backend/adapters/src/main/resources/db/migration/ 新增增量迁移。

- [ ] 逻辑身份、原始标签、版本名册、不可变资料支持零片段项。
- [ ] RENAME / MERGE / SPLIT 契约及归属校验复用版本 CAS、幂等、审计。
- [ ] 实现 0 段改名、全选拆分、合并保留目标身份。
- [ ] 接入完整读取、预算及证据校验；旧摘要不变。
- [ ] 验证历史、确认失效、旧纪要及 ACL 不变。

**完成标准：** 刷新可重建原型名册，零片段对象不会丢失。迁移编号按执行时 main 最新编号分配，不覆盖已有迁移。

### T04：独立命名弹窗

**依赖：** T02、T03。

**文件：** 拟新增 /home/tanren/ama/web/src/features/transcript/SpeakerNamingDialog.vue、/home/tanren/ama/web/src/features/transcript/useSpeakerNaming.ts；修改 [TranscriptEditor](/home/tanren/ama/web/src/features/transcript/TranscriptEditor.vue)、[MeetingDetail](/home/tanren/ama/web/src/features/meetings/MeetingDetail.vue)、[侧栏摘要](/home/tanren/ama/web/src/features/transcript/TranscriptSpeakerSummary.vue)。

- [ ] 逐项实现第 4.1、4.3 节模板、样式、文案和默认状态。
- [ ] 编辑指向独立控制器，不调用 openReview 或旧面板 focus。
- [ ] RAW 建稿延后至确认，使用冻结目标和原请求恢复。
- [ ] 会议内命名保存、权威回读、侧栏/正文关联同步。
- [ ] 页签草稿、空白 toast、取消及成功文案保持原型。

**完成标准：** 图 3a 打开图 2 对应窗口，真实保存并刷新；已命名外部重开等边界一致。

### T05：分离合并、拆分，落实撤销

**依赖：** T03、T04；撤销范围受 D03 约束。

**文件：** 拟新增 /home/tanren/ama/web/src/features/transcript/SpeakerMergeDialog.vue、/home/tanren/ama/web/src/features/transcript/SpeakerSplitDialog.vue；修改 [TranscriptSpeakerPanel](/home/tanren/ama/web/src/features/transcript/TranscriptSpeakerPanel.vue)、[useSpeakerUndo](/home/tanren/ama/web/src/features/transcript/useSpeakerUndo.ts) 及 T03 服务。

- [ ] 两个独立原型窗口，不混入命名。
- [ ] 合并保留目标，拆分允许全选，移除现有真子集限制。
- [ ] 操作记录及逆向归属避免回滚后续正文/标记。
- [ ] 连续撤销按已定义范围执行，不把撤销产生的新版本误当下一次原操作。
- [ ] D04 明确后绑定回听，复用真实来源及播放权限。

**完成标准：** 名册、段数、身份变化可验证；未定义的历史深度不擅自承诺。

### T06：会议声纹申请与二次确认

**依赖：** T03、T04；授权及样本策略依赖 D01、D02。

**文件：** 拟新增 /home/tanren/ama/web/src/features/transcript/SpeakerVoiceprintDialog.vue、/home/tanren/ama/web/src/features/transcript/speaker-voiceprints.ts、/home/tanren/ama/backend/api/src/main/java/ama/api/MeetingSpeakerVoiceprintController.java、/home/tanren/ama/backend/core/src/main/java/ama/governance/MeetingSpeakerVoiceprintService.java、/home/tanren/ama/backend/adapters/src/main/java/ama/governance/JdbcMeetingSpeakerVoiceprints.java；扩展 [GovernanceContracts](/home/tanren/ama/backend/contracts/src/main/java/ama/contract/GovernanceContracts.java)、[治理契约](/home/tanren/ama/contracts/openapi/operations.yaml) 及增量迁移。

- [ ] 第 4.2 节第二个窗口，不新增授权输入、上传或片段选择控件。
- [ ] 无业务写入的能力核查、授权适配、真实取样及质量校验。
- [ ] 内部他人和外部主体引用，企业外键/加密上下文兼容。
- [ ] 命名先保存、申请后受理，分别保留回执和原请求。
- [ ] 无音频、无授权、混合样本、模型失败、丢响应及刷新恢复均无假成功。

**完成标准：** 授权和样本合格时可查询真实申请；失败保留命名，不能以本人登记替代。

### T07：审核与我的语料反馈

**依赖：** T06；管理端动作缺口见 D06。

**文件：** [JdbcGovernance](/home/tanren/ama/backend/adapters/src/main/java/ama/governance/JdbcGovernance.java)、[JdbcFeedback](/home/tanren/ama/backend/adapters/src/main/java/ama/meeting/JdbcFeedback.java)、[FeedbackPage](/home/tanren/ama/web/src/features/meetings/FeedbackPage.vue)、[FeedbackTableView](/home/tanren/ama/web/src/features/meetings/FeedbackTableView.vue)、[OperationsPage](/home/tanren/ama/web/src/features/governance/OperationsPage.vue) 及反馈契约。

- [ ] 审核真实申请，含“需要补充”及理由。
- [ ] 原型标签、计数、七列展示同一申请。
- [ ] applicantId 权限过滤、管理员审核权限，不开放整场会议。
- [ ] 通过前不正式匹配；审核结果不覆盖历史命名。

**完成标准：** 命名申请、后台审核、我的反馈共用同一记录，不用演示行代替。

### T08：端到端及视觉验收

**依赖：** T01–T07 对应功能。

**文件：** T01 浏览器/视觉用例、相关 unit 和后端集成测试；调整[工作台集成脚本](/home/tanren/ama/tools/dev/transcript_workbench_integration.mjs) 和相关验收记录。

- [ ] 完成第 8 节矩阵，从侧栏到持久化、刷新、审核、反馈。
- [ ] 同数据、浏览器、字体、视口比较，不修改原型、遮掉控件或扩大容差换取通过。
- [ ] 完成类型、契约、构建及相关前后端检查，再按变更/失败/未解问题扩大回归。
- [ ] 正式目录/授权联调与受控模拟结果分开报告。

**完成标准：** 视觉和功能分别有证据；只完成命名窗口不等于整个原型完成。

## 8. 验收矩阵和命令

### 8.1 验收场景

| 编号 | 场景 | 应观察到的结果 | 依据 |
| --- | --- | --- | --- |
| A01 | 侧栏编辑 | 独立命名窗口，无综合审阅内容 | S1 |
| A02 | 两种桌面视口 | 520px、16px 圆角及原型遮罩、内边距、底栏 | S1 |
| A03 | 已命名内部人 | 标题仍为原始标签，组织页、once 默认 | S1 |
| A04 | 已命名外部重开 | 默认组织页，外部预填且初始隐藏 | S1 实测 |
| A05 | 外部说明/切页签 | 说明按目标 external 显示，切换不清草稿 | S1 |
| A06 | 目录控件 | 姓名·部门·职务，无新增搜索/分页，无 ID 冒充职务 | S1、S2 |
| A07 | 超过 20 人/同名 | 可选后续人员，按唯一 ID 保存，晚到请求不改选择 | S2 |
| A08 | 空白外部姓名 | 原型 toast，窗口保留，0 次写入 | S1 |
| A09 | X/遮罩/保留匿名 | 保留原状态；RAW 也不建工作版本 | S1、S2 |
| A10 | Esc | 不关闭命名窗口 | S1 实测 |
| A11 | 正文筛选后命名 | 仍更新目标全部关联片段 | S1、S2 |
| A12 | 命名后刷新 | 名称、类型、资料、原始标签及关联可恢复 | S1、S2 |
| A13 | vp 第一次确认 | 第一层关、第二层开，无命名/申请写入 | S1 |
| A14 | 第二层及取消 | 一个禁用输入框，退出且不改名、不自动返回 | S1 |
| A15 | 二次确认成功 | 命名保存，真实待审核，原型成功提示 | S1、S2 |
| A16 | 声纹失败 | 命名保留，无假成功；错误 UI 按 D02 | S2 |
| A17 | TXT/无音频字幕 | 命名可用，不虚构播放、样本或声纹结果 | S2 |
| A18 | 被命名人非提交人 | 归属被命名人，申请在提交人反馈内 | S2 |
| A19 | 外部主体/授权不明 | 真实主体及有效授权才受理，不按姓名或 actor 冒名登记 | S2 |
| A20 | 真实 42 秒样本 | 按正式策略核验，不直接套旧本人登记 30 秒上限 | S1、S3 |
| A21 | 合并 | 源目标不同，全部归目标，保留目标，移除源 | S1、S2 |
| A22 | 拆分部分/全部 | 至少一段，新匿名人，全选后源项 0 段保留 | S1 实测、S2 |
| A23 | 零片段改名 | 名册可保存，不伪造片段 | S1 |
| A24 | 拆分回听 | 可靠时间及真实音频，D04 确定的触发方式 | S2 |
| A25 | 连续撤销 | 按已定义范围恢复，不反复撤销“撤销本身” | S1、S2、D03 |
| A26 | 后续正文编辑 | 归属撤销不能丢掉后续正文/标记 | S2 |
| A27 | 确认后改名 | 新稿重新确认，旧确认/纪要保留 | S2 |
| A28 | 403/切账号/会议 | 清理私有显示，旧请求不作用于新上下文 | S2 |
| A29 | 409/重复点击/丢响应 | 保留输入，同 key/body 恢复，无重复版本/申请 | S2 |
| A30 | 纠错读取失败 | 说话人可独立读取和命名 | S2、G06 |
| A31 | 无姓名前缀音频 | 可定位匿名目标，未强认真实身份 | S1、S2 |
| A32 | 我的语料反馈 | 原型标签、计数、七列和“需要补充” | S1 |
| A33 | 审核前后 | 审核前不正式匹配，无普通用户特征下载 | S2 |
| A34 | 历史/权限 | 旧快照可读且摘要不变，命名不授权 | S2 |

### 8.2 实施阶段命令

从 /home/tanren/ama 根目录执行。以下为后续实施检查，本轮未声称运行或通过这些产品测试。

~~~sh
pwd
git branch --show-current
PATH=/home/tanren/ama/.tools/node-v22.23.4-linux-x64/bin:$PATH npm --prefix web run typecheck
PATH=/home/tanren/ama/.tools/node-v22.23.4-linux-x64/bin:$PATH npm --prefix web run check:api
PATH=/home/tanren/ama/.tools/node-v22.23.4-linux-x64/bin:$PATH npm --prefix web run test:unit
PATH=/home/tanren/ama/.tools/node-v22.23.4-linux-x64/bin:$PATH npm --prefix web run build
PATH=/home/tanren/ama/.tools/node-v22.23.4-linux-x64/bin:$PATH npm --prefix web run test:e2e -- tests/e2e/speaker-naming.spec.ts tests/visual/speaker-naming.spec.ts
~~~

后端按目录、说话人版本、会议申请、审核投影运行对应模块单元及受控数据库集成测试。测试类名随实现确定，不将尚不存在的类或命令写成已验证成功。集成测试不向用户现有真实会议写入。

1440×900、1024×768 为当前项目桌面验证场景；字体及系统影响字形和内容高度，比较时固定环境，不改变原型规格补偿环境差异。

## 9. 原型或需求尚未完整定义的项目

这些是后续实施的具体设计/外部依赖，不影响本轮交付方案。已有设计的界面、文案及流程按本文执行；缺项不能由开发人员自行补成新的用户功能。

| 编号 | 已确定的要求 | 尚缺什么 | 可以推进的工作 |
| --- | --- | --- | --- |
| D01 | 对应人员授权、可选声纹申请 | 授权凭据取得渠道、外部主体核验、用途/有效期的正式提供方；原型无授权采集控件 | 定义适配端口和主体引用、受控集成；不以本人同意或确认点击代替他人授权，不自加表单 |
| D02 | 无授权/样本不得假成功，失败保留命名，资料真实 | 无音频、准备中/失败、无授权、空/失败目录、缺职务的占位、禁用、错误文案及恢复表现；质量/时长门槛 | 完成错误码、状态、数据保留及接口；使用已有明确公共规范；不自行规定新增提示、禁用范围或按钮 |
| D03 | 说话人可撤销；原型演示栈 10 条；S2 B12 待确认 | 正式范围、数量、保留期限及与其他编辑穿插的规则 | 操作记录和不破坏正文的逆向归属；不将“10 条”或“仅最新版本”擅定为产品范围 |
| D04 | 拆分支持回听 | 原型行只绑定勾选，时间未绑定回听；需明确点击时间或其他既定触发 | 完成播放/定位适配，保留列表设计，不自行增播放器/播放按钮 |
| D05 | 保留输入、原请求恢复、不重复写入 | 同步模拟未覆盖发送中关闭、未知结果、未保存正文、冲突在此弹窗的表现 | 完成 key/body、回执及上下文隔离，不自增恢复表单或确认步骤 |
| D06 | 管理员审核，反馈有“需要补充” | 需求模块六仅标题，缺会议声纹管理动作及补交交互设计 | 完成接口、权限、状态及原型反馈表，不自改运营/补交页面，不删除“需要补充” |

模拟 42 秒改为真实样本、姓名选择以唯一 ID 持久化、本地保存改为后端权限/版本校验，均是将已明确需求接到真实系统，不是重设计前端。其他未定义交互不能据此推定。

## 10. 交付顺序、范围及完成判定

T01 → T02 → T03 → T04 → T05 → T06 → T07 → T08，单 agent 顺序执行。已明确的命名窗口及会议保存可先完成；D01–D06 对应能力未完成前，不能标记整个声纹/撤销/回听流程完整交付。

完整改造需同时满足：

1. 两层弹窗的结构、默认状态、文字、关闭方式及先后顺序与原型一致。
2. 命名真实保存、内部唯一 ID 关联、外部姓名/匿名/零片段名册行为正确。
3. 合并、拆分及已定义撤销独立工作，不借综合审阅表单完成。
4. 声纹有对应主体授权、真实样本、真实审核记录和反馈跟踪；失败保留命名。
5. 当前稿、历史、纪要、权限及恢复满足 S2，不以演示数据代替后端能力。
6. 视觉、行为、契约、持久化及回归都有证据；正式提供方未接入据实记录。

本轮完成原型复核、差异重审及本文编写。旧方案保留为历史记录并标注替代关系；未修改产品源码，未运行写入真实会议的业务验证，未提交或部署。
