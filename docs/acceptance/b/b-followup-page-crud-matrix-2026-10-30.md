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
| TOPIC-01 | 专题、归组、列表切换 | IN_SCOPE | 新建、编辑、搜索、单项/批量归组、移除/删除 | owner/member 权限 | CRUD API、回读、冲突/删除确认、pixel diff | `OPEN` |
| LIB-01 | 会议知识库和文档导航 | IN_SCOPE | 标签/条目读取、选择、移除、只读导航 | provider 版本和 ACL | 真实版本 token、403/404、页面状态和回读 | `OPEN` |
| FEEDBACK-01 | 反馈列表、详情和处理状态 | IN_SCOPE | 创建、读取、补充、审核状态更新/撤销（按领域规则） | CAS 身份和权限 | 状态持久化、非授权拒绝、刷新和截图 | `PASS` |
| VOICE-01 | 录音页及录音控件 | DEFERRED_VOICE | 只实现原型静态/延期/错误状态；不访问设备、不建任务 | 不触发录音外部接口 | 1440/1024 pixel diff、`getUserMedia` 未调用、无写入 | `DEFERRED_VOICE` |
| VOICE-02 | 音视频、播放、ASR、说话人 | DEFERRED_VOICE | 只实现原型结构和延期状态；不上传媒体、不 ASR、不播放 | 不触发媒体/ASR接口 | 静态 pixel diff、无文件/任务/结果、副作用为零 | `DEFERRED_VOICE` |
| CROSS-01 | CAS + 导入完整浏览器链 | IN_SCOPE | 匿名→登录→会议→文本导入→材料读取→受理→退出 | 固定 `8792/2525`，真实 harness | 端到端报告、双方回归、页面无错误 | `OPEN` |
| CROSS-02 | 负例与兼容修复 | IN_SCOPE | 400/401/403/404/409/500、过期/重复 ticket、版本冲突诚实呈现 | 只修复被真实请求证明缺失的 harness 路由/响应 | 失败日志、最小 patch、harness 定向测试、AMA 回归 | `OPEN` |

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
