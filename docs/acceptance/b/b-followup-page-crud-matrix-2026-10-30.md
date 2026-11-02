<!-- BCD_LOCAL_CONTROLLED_COMPLETION_20261102 -->
# B后续页面与真实接口矩阵：当前完成

当前正常部署/工作源 `86a1c312d1b11c24b590c2aa8f6eef764b8c636ca82a0767142a29cd5a3f2ebd`。仅1440×900、1024×768，全部手机开发与验收移除且不归F；外部固定兄弟8792/2525、非公司权威。 当前20行均完成其本地功能/状态/权限义务；VOICE由C的实际录音/语音/播放验证关闭，未继续延期为占位。真实模型/平台生产资格另归F。视觉按真实状态和必要功能扩展逐项裁定，不把严格非零写成零。

| ID | 当前范围结论 | 实际证据领域 |
| --- | --- | --- |
| AUTH-01 | COMPLETE_LOCAL_CONTROLLED | AUTH |
| MEET-01 | COMPLETE_LOCAL_CONTROLLED | MEET |
| MEET-02 | COMPLETE_LOCAL_CONTROLLED | MEET |
| MEET-03 | COMPLETE_LOCAL_CONTROLLED | MEET |
| INFO-01 | COMPLETE_LOCAL_CONTROLLED | INFO |
| ROST-01 | COMPLETE_LOCAL_CONTROLLED | INFO |
| IMPORT-01 | COMPLETE_LOCAL_CONTROLLED | IMPORT |
| MATERIAL-01 | COMPLETE_LOCAL_CONTROLLED | MATERIAL |
| TRANS-01 | COMPLETE_LOCAL_CONTROLLED | TRANSCRIPT |
| MIN-01 | COMPLETE_LOCAL_CONTROLLED | MINUTES/TEMPLATE |
| REVIEW-01 | COMPLETE_LOCAL_CONTROLLED | MINUTES/INFO |
| FINAL-01 | COMPLETE_LOCAL_CONTROLLED | PUBLICATION |
| PUB-01 | COMPLETE_LOCAL_CONTROLLED | PUBLICATION |
| TOPIC-01 | COMPLETE_LOCAL_CONTROLLED | TOPIC |
| LIB-01 | COMPLETE_LOCAL_CONTROLLED | LIBRARY |
| FEEDBACK-01 | COMPLETE_LOCAL_CONTROLLED | FEEDBACK |
| VOICE-01 | COMPLETE_LOCAL_CONTROLLED | RECORDING/TRANSCRIPT |
| VOICE-02 | COMPLETE_LOCAL_CONTROLLED | RECORDING/TRANSCRIPT |
| CROSS-01 | COMPLETE_LOCAL_CONTROLLED | AUTH/IMPORT/RECORDING |
| CROSS-02 | COMPLETE_LOCAL_CONTROLLED | AUTH/IMPORT/RECORDING |

每个领域的精确报告路径、SHA及适用范围见`.tools/local-app/d08-page-matrix/controls-audit-11/evidence-domains.json`；每个生产控件的条件/禁用/handler/父form和对应证据见`bound-reviewed-controls.json`。这些是状态/源码与实际业务证据的汇合，不是20行或596节点均独立点击的计数。详[完整逐任务结果](../bcd-completion-2026-11-02.md)与[差异裁定](../d/desktop-visual-rulings-2026-11-02.md)。

<details>
<summary>历史执行记录：原始状态、失败与当时接续条件，均不覆盖上方当前结论</summary>

> 当前范围已按用户最新要求调整：仅桌面端1440×900、1024×768；所有手机端开发与验收项已移出，不属于F待办。历史多视口报告保留原文，不再作为手机端门槛。详见 [桌面端范围裁定](../../development/2026-11-02-desktop-only-scope.md)。

# B 阶段后续页面、CRUD 与联调矩阵（2026-10-30）

> 本表是 [B 阶段后续设计](../../superpowers/specs/2026-10-30-ama-b-followup-pixel-crud-cas-design.md) 的执行台账。它只记录 2026-10-30 之后的新范围；此前报告中的通过、失败和限定证据仍按原文保留。

## 状态定义

| 状态 | 含义 |
| --- | --- |
| `UNTESTED` | 已登记但还没有绑定当前源码和新鲜证据 |
| `OPEN` | 已发现缺口，仍需实现或验证 |
| `PASS` | 页面像素、真实操作、持久化回读和适用负例均通过 |
| `DEFERRED_VOICE` | 语音业务按本轮边界延期；页面静态/延期状态仍必须完成像素验收 |
| `FAIL` | 有明确失败或回归，不得计为完成 |

所有新行初始为 `UNTESTED`，不得因为旧报告中的相近页面或旧受控流程而自动改成 `PASS`。

## 页面与功能矩阵

| ID | 页面/状态 | 本轮范围 | 可见操作和真实事实 | CAS/harness 联调 | 证据要求 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| AUTH-01 | 登录、匿名、错误、退出 | IN_SCOPE | 登录建立 AMA 会话；退出撤销会话并回到匿名 | 固定 `8792`，service 白名单、ticket 单次消费、错误和登出 | 浏览器请求日志、AMA/Harness 测试、Cookie/401 证据 | `PASS` |
| MEET-01 | 我的会议：负责/参与、空/非空、加载/失败 | IN_SCOPE | 真实列表读取、范围切换、分页、刷新回读 | 使用 CAS 登录身份和会议权限 | 两视口完整截图、列表 API、第二身份读取/拒绝 | `OPEN` |
| MEET-02 | 搜索、筛选、专题入口和删除确认 | IN_SCOPE | 筛选持久查询；专题/删除按领域软删或归档规则落库 | CAS 会话和权限负例 | 过滤 API、删除后回读、审计/权限结果、pixel diff | `OPEN` |
| MEET-03 | 新建会议、详情和路由状态 | IN_SCOPE | 创建、打开、刷新、失败恢复和域内删除/撤回 | owner/member/非成员 | POST/GET/状态回读、路由截图、409/403 | `OPEN` |
| INFO-01 | 会议信息抽屉 | IN_SCOPE | 日期、分类、标题、摘要、取消、关闭、保存 | owner scope、CSRF、revision | 抽屉全屏截图、保存后刷新、取消不写入、409 | `PASS` |
| ROST-01 | 企业/外部参会人名单 | IN_SCOPE | 草稿编辑、保存、确认、撤回；人工文本标签改名 | 名单授权、撤权、身份映射 | roster/grant 回读、成员/外部/非成员权限、pixel diff | `OPEN` |
| IMPORT-01 | 文本导入弹窗和队列 | IN_SCOPE | TXT/Office 文本选择、排序、上传、保存、重试、放弃 | 真实登录和导入受理；材料端点按实际请求联调 | 文件字节/hash、草稿刷新、坏文件无部分 RAW、截图 | `OPEN` |
| MATERIAL-01 | 背景材料上传/选择/移除/重试 | IN_SCOPE | 材料 CRUD、版本绑定和失败恢复；不得混入转写 RAW | 固定 harness 材料读取、403/缺版本/过期 token | provider 请求日志、快照版本、刷新回读、错误截图 | `OPEN` |
| TRANS-01 | 文本转写工作台 | IN_SCOPE | 文本段落编辑、替换、标记、保存、确认、恢复 | 会话、revision、冲突 | 全页 pixel diff、正文回读、409/IME/丢 ACK | `OPEN` |
| MIN-01 | 纪要、模板和候选 | IN_SCOPE | 模板读取/创建/编辑/删除（按领域规则）、生成受理、候选采用/保留、正文保存 | 受控模型/材料请求只作为被测外部依赖 | API/数据库回读、未保存稿保护、候选和失败状态截图 | `OPEN` |
| REVIEW-01 | 来源审阅、敏感信息、行动负责人 | IN_SCOPE（纯文本部分） | 审阅记录、负责人文本选择/清除、确认前门槛真实持久化 | 目录/身份请求使用 CAS 会话 | 审阅/负责人回读、越权/撤权、确认阻断/通过 | `OPEN` |
| FINAL-01 | 最终确认、冻结和只读 | IN_SCOPE | 确认、冻结、冲突恢复；冻结后写保护 | owner/member ACL | 事务结果、重复命令、刷新和只读回读 | `OPEN` |
| PUB-01 | 发布、未知 ACK、正式文档 | IN_SCOPE | 目的地选择、发布、状态查询、失败重试、只读文档 | provider ACL 和 CAS 身份 | 只查回不重发、成员/非成员/撤权、文档字节/截图 | `OPEN` |
| TOPIC-01 | 专题、归组、列表切换 | IN_SCOPE | 新建、编辑、搜索、单项/批量归组、移除/删除 | owner/member 权限 | CRUD API、回读、冲突/删除确认、pixel diff | `PASS` |
| LIB-01 | 会议知识库和文档导航 | IN_SCOPE（原型只读库） | 实际条目/位置/版本读取、分页、来源与会议导航；成果移除/失效按D-05治理规则执行 | 固定8792 harness，当前provider READ和会议ACL | 真实context/撤权403/恢复新版、来源导航、三视口12项零差异 | `PASS` |
| FEEDBACK-01 | 反馈列表、详情和处理状态 | IN_SCOPE | 创建、读取、补充、审核状态更新/撤销（按领域规则） | CAS 身份和权限 | 状态持久化、非授权拒绝、刷新和截图 | `PASS` |
| VOICE-01 | 录音页及录音控件 | DEFERRED_VOICE | 只实现原型静态/延期/错误状态；不访问设备、不建任务 | 不触发录音外部接口 | 1440/1024 pixel diff、`getUserMedia` 未调用、无写入 | `DEFERRED_VOICE` |
| VOICE-02 | 音视频、播放、ASR、说话人 | DEFERRED_VOICE | 只实现原型结构和延期状态；不上传媒体、不 ASR、不播放 | 不触发媒体/ASR接口 | 静态 pixel diff、无文件/任务/结果、副作用为零 | `DEFERRED_VOICE` |
| CROSS-01 | CAS + 导入完整浏览器链 | IN_SCOPE | 匿名→登录→会议→文本导入→材料读取→受理→退出 | 固定 `8792/2525`，真实 harness | 端到端报告、双方回归、页面无错误 | `OPEN` |
| CROSS-02 | 负例与兼容修复 | IN_SCOPE | 400/401/403/404/409/500、过期/重复 ticket、版本冲突诚实呈现 | 只修复被真实请求证明缺失的 harness 路由/响应 | 失败日志、最小 patch、harness 定向测试、AMA 回归 | `OPEN` |

2026-10-31 23:28 用户要求完成当前批次后停止，等待“继续任务”。本矩阵仍5项PASS、13项OPEN、2项DEFERRED_VOICE；MIN-01没有关闭。个人模板CRUD/协议/页面已实现，初始化回执与事件的真实MySQL定向21项通过，最新事件修复未部署；完整生成/候选/最终发布、原生模板UI恢复及像素验收未完成。停止位置、源码/部署差异与证据见[任务接续记录](../../development/task-continuation-2026-10-31-2328.md)。

## 关闭规则

- `IN_SCOPE` 行必须同时有页面 pixel diff、真实请求/响应、持久化回读和适用负例；只有单测或静态构建不能关闭。
- `DEFERRED_VOICE` 行只关闭页面视觉和无副作用边界，不写成语音功能通过。
- 任何 `UNTESTED`、`OPEN` 或 `FAIL` 行都会使本轮总状态保持 `NOT ACCEPTED`。
- 每行证据必须写出 AMA 源码 hash、harness revision、fixture 标识、视口、命令和报告路径。

## 2026-10-31 新鲜业务证据（像素未关闭的行保留 OPEN）

| 行 | 实际已验证 | 报告/剩余项 |
| --- | --- | --- |
| ROST-01 | dev-member 授予→读 200/写 403→撤权后原会话读 403→重新授予读 200；外部人员保持文本标签 | `.tools/local-app/material-minutes/roster-review-final/report.json`；逐页像素尚缺 |
| IMPORT-01 / MATERIAL-01 / CROSS-01 | 真实 CAS、TXT 字节、草稿刷新、harness 版本材料、403、导入受理、退出 10/10 | `.tools/local-app/integration-import-cas/material-release/report.json`；Office 与完整材料 CRUD/像素尚缺 |
| TRANS-01 | 原生转写保存、恢复、确认与材料上下文生成链实际使用同一确认 | `.tools/local-app/material-minutes/native-final-ready/report.json`；完整交互负例/像素尚缺 |
| MIN-01 | 正确材料快照进入 generation_input；同键受理/重放、worker 成功、原生编辑保存/刷新、模板实际读取 | `.tools/local-app/material-minutes/native-workspace-fixed/report.json`、`native-final-ready/report.json`；模板全 CRUD、候选与像素尚缺 |
| REVIEW-01 / FINAL-01 | 审阅未齐时阻断；合成决定补齐后最终确认 201、重放 200、负责人/成员冻结稿一致 | `.tools/local-app/material-minutes/roster-review-final/report.json`；合成审阅只证明门禁，语义质量与逐页像素未签收 |
| PUB-01 | 经固定 harness 8792 发现目的地、发布成功、同键重放不重发；负责人/成员正式文档字节一致 | `.tools/local-app/material-publication/first/report.json`；包含两视口截图，逐原型 diff 与剩余负例未关闭 |
| TOPIC-01 / FEEDBACK-01 | 真实专题创建/列表/归组/移除/删除；反馈创建/列表回读与 UI 提交 | `.tools/local-app/b-followup/topic-feedback-final/report.json`、`ui-report.json`；剩余状态变更/像素未关闭 |

共同基线：AMA `main`（当前工作区修改尚未提交），CAS actor `dev-owner`/`dev-member`，harness revision `816c04e64f1321b35eb0e159223a31640a2b7f62` 加当前兼容修复。材料/纪要/最终/发布 fixture 为会议 `1c4162bb-292f-46ad-8e1c-5d129ff6c2e3`，截图视口 1440×900 与 1024×768；具体 sourceSignature 见材料纪要脚本报告。未绑定完整源码/像素证据的行继续 OPEN。

### 首页精确像素的新鲜结果

2026-10-31 原始 HTML SHA 仍为 `ab21d0b73f4e48bbf701ee8bca5e958940b61f6e6f680d4ced02de0ce4bab867`，未修改原型 DOM/render/CSS。通过真实 CAS/API 幂等创建 dev-viewer 的三条会议完整夹具（hasMore=false），仅向参考原型投影真实业务数据；32 个 owned/group/calendar/preset/status/empty 视口和全页比较均为零差异，覆盖 1440×900、1536×791、1024×768。双方 Chromium 统一 `--force-color-profile=srgb --disable-gpu --disable-partial-raster`，精确比较阈值仍为 0，maskedRegions=[]。前两次诊断的 8–60 像素通道1差异保留在旧报告中，未用容差删除；禁用动画和产品 CSS 的试验无效，产品 CSS 试验已撤回。

正式报告 `.tools/local-app/prototype-parity/home-finite-final-20261031/report.json` 包含部署/工作区签名、harness revision、实际会议 IDs 和测量环境；幂等夹具报告 `home-controlled-fixture/report.json`。这关闭相应首页静态状态的像素缺口，其他页面/状态与业务负例仍按行保持 OPEN。

### 导入弹窗精确像素（2026-10-31）

实际 CAS dev-viewer、新会议 f0b0dc03-6c62-4090-8786-f98edb43f7c9，部署/工作区签名均382f5594ddfc81d4d36b07b02cd0aeaa1bf5343b397d60fcd30142a26dda2c52；原型 SHA 保持原值。删除产品多出的音视频技术说明 DOM 后，空/单文件/两文件 ×1440×900、1536×791、1024×768 ×视口/全页共18项像素差异均为0，无掩码/容差、无页面错误。正式报告 `.tools/local-app/prototype-parity/import-panel-fixed-20261031/report.json`；前次14项差异保留。IMPORT-01仍OPEN，Office、排序/保存/重试/放弃及异常持久化负例尚需补齐，不能仅以弹窗像素签收整行。

### 文本与 Office 导入真实联调（2026-10-31 14:33）

`.tools/local-app/integration-import-cas/office-order-corrupt-zip-20261031/report.json` 的6项实际CAS/UI/API/Worker检查全部通过：五格式DOC、DOCX、PDF、PPTX、TXT按冻结顺序生成RAW，授权下载原文件SHA逐一相同；保存已提交但浏览器丢失响应后原键/原正文重试；刷新顺序一致且无重传；dev-member非成员403；真正截断PK ZIP为第二文件时整批FAILED、历史RAW为零；新导入关闭提示允许继续，明确放弃后workspace403。主成功会议9f565408-74df-4261-b4c6-8a29e8a5b61a、task5e9b8a80-70f9-4d3e-937c-7805db0a94bd、RAW e4cff13e-be93-4cfc-9a5d-c4f7cc0b34dc；失败会议5a9fa410-ac8b-4cae-acb3-10b3a869acf3、放弃会议8cebfb55-11dd-4420-896d-5d305db6fe5e。实际部署efa19d12cf7b0aadf6382a67a3067d4bd10417868f752559a7f0ead2cb92cd5a，报告另记工作区正在开发的反馈差异，不混充一致签名。视口1440×900；本报告证明业务行为，18项精确像素报告单列，剩余材料/恢复/错误状态像素仍OPEN。未改动既有业务夹具或原型。

### 反馈详情、补充与审核完整联调（2026-10-31）

新鲜报告 `.tools/local-app/b-followup/feedback-review-explicit-labels-20261031/report.json`：真实CAS业务8组通过，1440×900、1536×791、1024×768 ×全部/四种审核状态/空列表 ×视口/全页共36项精确像素差异均为0。部署与工作区签名均 `582495f1bbf2c5cb6ce035155c9bdb3d3dcf028fb34978f61508c79e7c34ec62`，harness revision `816c04e64f1321b35eb0e159223a31640a2b7f62`（固定8792/2525，模拟CAS不代表企业权威）；owner `feedback-ui-bedcf219`，foreign `feedback-ui-382deaae`，会议 `03dbc464-aebe-49ec-affb-912c3603fbad`、主反馈 `31a6421f-d165-4d9b-9e03-ed87c66653d3`。

创建、补充与管理员审核均在真实提交后丢失ACK，刷新保持只读原请求，相同key/body重试且无重复历史。需要补充后再次提交恢复待审；通过/撤销追加历史，旧审核回执重放不重新应用旧结论；真实并发审核导致过期补充409，显式放弃且无部分历史；foreign CAS的读取/补充/审核均403且页面不泄露原文。管理员可审核主动提交的反馈，读取来源会议仍403。服务器状态筛选、完整计数和私有分页真实回读；原始comment不可变，V044历史上限50，当前角色及权限在每次事务内验证。审核通过仅记录本地处理结论，D阶段全局语料更新不在本行资格内，也未伪装为已更新。原型SHA未改，无掩码/容差。

后台真实10项、录音跨I/O兼容13项、生产Java691项、Web419项和type/build/API字节校验通过；标签修正后8项及type/build通过。首次管理员表单显式标签缺陷失败报告 `feedback-review-first-20261031` 原样保留，未计为通过。FEEDBACK-01关闭，其余OPEN行与整体NOT ACCEPTED保持真实状态。

### 2026-10-31 18:58 用户停止时的专题/知识库与环境状态

详 [当前批次接续](topic-library-batch-2026-10-31.md)。TOPIC-01新摘要/删除/恢复及真实事务10项通过，LIB-01后端/受控provider/前端读取实现与定向测试完成，均缺新部署CAS/像素，保留OPEN。原已通过条目作为带旧sourceSignature的历史证据保留，不代表误删后环境当前可用。用户确认容器、镜像和持久卷误删，所有开发/验证已停止，未重建数据或部署；总体NOTACCEPTED。

### 用户恢复后的新环境接续（2026-10-31）

原数据库卷事故后已重建的主库迁移V044，端口46443；不将旧业务数据写作已恢复。源码957cda0的全新专题CAS9项通过（`b-followup/resume-topic-20261031-01/report.json`），手工替换/撤销真实CAS6项通过（`transcript-replacements/resume-cas-20261031-01/report.json`）。新专题基础三视口30项、单/批量归组三视口30项、扩展非空分组三视口42项均为精确零差异（基础状态与扩展报告有重叠，不相加声称独立102项）。扩展最初失败由参考夹具未读取归组后的真实排序/updatedAt造成，修正仅参考数据投影，保留原型及失败报告。

新会议e8824826-5e77-4029-b94d-28fdaa42e4c8实际完成确认/受控合成纪要/显式审阅/冻结/发布/当前位置与成员权限，7项真实检查PASS（`b-followup/resume-library-20261031-04/report.json`）。提供方来自当前自有local受控进程，CAS仍为8792模拟器；不冒充企业知识库或真实模型质量。首次任务完成期间有一次503来源图变化，后续同task读取200，未新建第二生成；测试请求字段与query错误也保留原失败。

知识库页头精确对照实测3198像素差异，修正面包屑/按钮文字和链接行高后HMR4项零差异。新正式脚本`tools/dev/meeting_library_cas_integration.mjs`涵盖实际READ撤回/恢复、版本变化、成员/非成员、三视口和导航；尚待新部署执行，LIB-01仍OPEN。专题新增真实服务端筛选检查也待新部署重跑，TOPIC-01仍OPEN。全Web432、生产Java698、type/build/APIbytes已通过，但这些计数在存储probe修复之前；当前新验证见后续记录。

正常新部署受历史113k录音对象的存储全枚举阻断，120秒未就绪，证据`resume-library-topic-deploy-20261031-01.log`；已停止仅该失败部署的自有应用进程，数据库/文件/失败证据保留。新增每backend可用性探测避免枚举，仍验证backendID/mTLS/实际卷/角色/inode；真正文件列表不改，其他适配器默认仍list。真实LocalFileProbeIT2+顺序读取3+并发2共7PASS（`resume-file-probe-green.log`）；专题10/录音13/生产全套顺序复验中，再正常部署32pool/32file并完成CAS/像素/native/100路及完整7200秒。不得把旧中断长测标完成。

### 当前源码的专题与只读知识库签收（2026-10-31）

TOPIC-01：当前冻结源码的真实CAS10项和三视口72项精确零像素通过。汇总 `.tools/local-app/resume-topic-final-qualification.json`，业务 `b-followup/resume-topic-final-20261031-01/report.json`；像素 `prototype-parity/resume-topic-final-{expanded,assignment}-{1440,1536,1024}-20261031-01/report.json`，全部部署/工作区签名一致，无mask/容差。包含非空专题全量摘要/删除阻断、真实服务端筛选、单/批量归组/移出、默认不写入、丢ACK刷新原请求、版本冲突原子拒绝、非owner拒绝及会议删除后归组退役。真实MySQL专题事务10项、录音续租13项和生产698项也通过。当前TOPIC-01签收不覆盖D其他规则或全产品视觉收口。

LIB-01：`b-followup/resume-library-final-20261031-01/report.json`真实6项+三视口12项零像素，部署/工作区签名一致。所有产品外部请求经8792，报告含harness当前源码SHA；管理日志确认location 200/403实际穿过harness。当前成员READ、非成员拒绝、提供方撤READ后刷新隐藏、恢复新grant版本、真实分页/刷新/按会议ID导航均通过。并发provider回包乱序已确定性RED→GREEN，Web全433/type/build/APIbytes通过。原型`PAGES['/meeting-library']`为只读浏览，没有删除已发布成果按钮；Ruling：本行的“移除”明确归原有D-05治理/失效传播，不能新增旁路删除已发布快照或将治理任务标完成。原型只读路径验收通过，D-05仍未完成。旧失败/局部验证不被重标。

新TXT会议db0dbb33-962f-469a-bb86-074f9f28bd35的实际纪要执行与结果读取已经8792；提供方模型/知识库仍是可重复受控模拟器，不是企业权威。生成/发布脚本最后因同名会议定位歧义失败的报告保留，新的库脚本用真实meetingId定位并对全部真实已发布条目投影对比。新原生SIGKILL恢复10项PASS，11.8秒恢复、17原帧957416字节WAV逐帧SHA；报告`recording-native-cas/resume-harness-probe-hard-crash-20261031-01/report.json`。100路60秒短测当前运行，尚不证明两小时或字幕性能。

</details>
