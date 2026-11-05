# AI纪要原型与功能修复验收

状态：已完成。本轮重启后的连续九阶段验收通过 70 项，双尺寸视觉验收通过 4 项。在已执行的场景中，未解决的非预期异常为 0。

## 范围与依据

- 主项目 `/home/tanren/ama`，`main`，全程单 agent，不使用分支切换或 worktree，不提交推送。
- 依据：`designDocs/智能会议助手1021版.html`、一期需求功能模块三、D-06/09/18/20/21，以及 `docs/superpowers/specs/2026-11-03-ai-minutes-repair-baseline.md` 的入口映射和冲突裁决。
- 参考原型 SHA256：`ab21d0b73f4e48bbf701ee8bca5e958940b61f6e6f680d4ced02de0ce4bab867`，未修改参考原型。
- 用户截图对应会议 `cc866694-f95d-46f4-a757-2965ad334132` 不作为写入测试对象。所有生成、编辑、确认、发布和撤回均针对专门建立的验收会议。
- 保留两项目原有未提交工作；本专项不将治理、录音等其他对话的既有改动归为自身成果。
- 外部身份、人员、模型、知识库及其他联调服务通过兄弟项目 `ama-integration-harness`；HTTP 8792、SMTP 2525 固定端口不变。harness 是共同开发的测试实现，不是生产接口的权威定义。

## 功能到验证的映射

各条款链接到同一构建的浏览器记录。每份记录保留实际测试会议、检查结果以及请求或截图，视觉对照另用已导入同样内容的会议。

| 条款 | 实现行为与验证要求 | 最终证据 |
| --- | --- | --- |
| P01 / MIN-01 | 无结果、执行中、失败互斥显示；确认转写衔接首次生成；刷新/切页恢复原任务，不重复提交 | [首次生成](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-full/smoke/report.json)、[首版编辑保护](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-full/guard/report.json)、[首次失败与重试](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-full/initial_failure/report.json) |
| D-20 | 首版仅在无人工编辑且版本未变时自动保存；已有稿产生候选，采用与拒绝有真实持久结果 | [编辑、采用与版本恢复](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-full/workflow/report.json)、[故障恢复](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-full/recovery/report.json) |
| MIN-02 / D-09 | 七类数据独立；原型目录保留；待确认与分歧分型；背景材料不变成会议结论 | [编辑、采用与版本恢复](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-full/workflow/report.json)、[来源与权限](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-full/sources/report.json)、[双尺寸原型对照](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-visual/report.json) |
| MIN-03/04 | 两类敏感摘录、真实来源、逐项/全部排除；重新加入排除内容会阻断确认 | [编辑、采用与版本恢复](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-full/workflow/report.json)、[来源与权限](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-full/sources/report.json) |
| MIN-05/06 | 条目编辑、Unicode 范围、负责人企业身份、无日期不强制；来源支持和行动字段分别核对 | [编辑、采用与版本恢复](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-full/workflow/report.json)、[浏览器编辑与回执恢复](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-full/editing/report.json) |
| MIN-07 | 局部生成绑定具体分类和当前输入；保留未选择部分与正文；旧候选不能覆盖新版本 | [编辑、采用与版本恢复](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-full/workflow/report.json) |
| MIN-08 | 三种内置模板；个人模板 1–8 主题顺序、创建/编辑/删除；选中与已应用区分 | [模板](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-full/templates/report.json)、[故障恢复](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-full/recovery/report.json) |
| MIN-09 / D-18 | 人工标题保护；显式/自动保存；中文输入法；丢失 ACK 恢复原请求；复制与下载内容准确 | [浏览器编辑与回执恢复](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-full/editing/report.json)、[故障恢复](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-full/recovery/report.json)、[冻结发布与下载](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-full/publication/report.json) |
| MIN-10/11 | 全文候选比较与采用；历史双版本比较；整体关联恢复创建新修订并使原确认失效 | [编辑、采用与版本恢复](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-full/workflow/report.json) |
| MIN-12 | 转写/录音、PDF/PPTX/DOCX、知识库来源定位和真实版本；来源与会议权限分别验证 | [来源与权限](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-full/sources/report.json) |
| MIN-13/14 / D-21 | 结论和行动的实际核对条件；信息在确认页补齐；冻结快照与发布输入一致 | [首次生成](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-full/smoke/report.json)、[编辑、采用与版本恢复](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-full/workflow/report.json)、[冻结发布与下载](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-full/publication/report.json) |
| MIN-15 / P06 | 负责人/参会人只读冻结成果；MD/PDF/DOCX 真下载；独立库权限；撤回后正文、历史及旧下载链接拒绝 | [冻结发布与下载](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-full/publication/report.json) |
| 原型视觉 | 1440×900、1024×768，同内容双视图；字体、颜色、横向几何和相对间距；敏感与来源布局 | [双尺寸原型对照](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-visual/report.json) |

## 连续实操与交付证据

- [连续九阶段运行报告](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-full/suite-report.json) 与 [最终核验摘要](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-full/acceptance-summary.json) 记录同一运行。主测试会议为 `665cd679-b76e-410f-bdaa-f6046af58958`。分阶段通过项数为 首次生成 9、首版编辑保护 11、首次失败与重试 3、编辑、采用与版本恢复 15、浏览器编辑与回执恢复 4、模板 4、故障恢复 6、来源与权限 11、冻结发布与下载 7，共 70 项。负责人和参会人使用各自的浏览器会话。
- 三种下载均来自冻结修订 `509e6418-3d82-4b9e-ac26-3862ac8d223f`，逐一读取文件并核对标题、段落顺序和正文。[MD，506 字节](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-full/publication/published-member.md)、[PDF，21288 字节](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-full/publication/published-member.pdf)、[DOCX，2339 字节](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-full/publication/published-member.docx)。知识库发布任务执行 1 次后成功。撤回后，原下载链接返回 403。
- 两种视口分别采集真实工作台与原型的核心要素、正文及敏感区域，记录 26 项样式或布局测量行。[视觉测量](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-visual/report.json)、[1440 实际核心要素](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-visual/actual-core-1440.png)、[1440 原型核心要素](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-visual/prototype-core-1440.png)、[1024 实际正文](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-visual/actual-body-1024.png)、[1024 原型正文](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-67-visual/prototype-body-1024.png)。
- 当前运行源码签名为 `751c5f52c4f8d728bdfbe299859bc15486a748df4f1439a5f761b482d39788b5`。页面、API、worker、文件服务及模型/知识库桥接健康，HTTP 8792 与 SMTP 2525 均可用，验收故障注入规则已清理。登录前会话、撤权、旧证据、版本冲突和非法输入按预期拒绝。最终摘要列出所有观测到的非成功 HTTP 业务路径。

## 已完成的回归证据

日志目录：`.superpowers/sdd/2026-11-03-ai-minutes-gap-analysis-and-repair-plan/`。

| 测试组 | 结果 | 日志/说明 |
| --- | --- | --- |
| 当前可执行后端单元回归 | 1,352 通过，0 失败、0 错误、0 跳过 | `readiness-final-backend-inventory.json` 与 `readiness-final-backend-units.log`，同一次 Maven 回归 |
| 完整前端单元测试，串行执行 | 728 通过，0 失败、0 取消、0 跳过 | `ack-final-frontend-units.log`，含 3 项生成回执与卸载竞态回归 |
| 传输、会话、续验、发布传输专项 | 29 通过，0 取消 | `transport-admission-final.log`；覆盖业务客户端绝对 URL |
| 前端类型检查 | 通过 | `generation-ack-typecheck-green.log` |
| 旧 JDBC 夹具与当前真实边界 | 37 通过 | `updated-integration-fixtures-green.log`；包含原篡改/撤权拒绝 |
| 正式文档规范化配置和发布关联 | 13 通过 | `document-configuration-green.log` |
| 显示续验与真实权限、完整内容比对、读版本 | 23 通过 | `published-display-renewal-fixed.log` |
| 生成回执、租约、冻结与完整内容比对 | 54 通过（含 11 项真实 MySQL） | `generation-receipt-comparison-tests.log` |
| 长历史元数据成员与逐字段比较 | 23 通过，其中 18 项真实 MySQL | `grouped-membership-index-tests.log`，覆盖限定条件不同的同列选择器、同数量替换和二进制不同的文本 |
| 联调服务的纪要生成契约 | 5 通过 | `personal-heading-partition-green.log`，覆盖按主题归类、来源绑定与原请求重放 |
| 最终确认就绪读取与发布并发 | 43 通过，其中 11 项真实 MySQL | `readiness-observation-tests.log`，含在最终比对时改变任务行、整体重新取得同一预算下的前后快照，以及损坏历史阻止就绪、读取不产生写入的检查 |
| 冻结编辑 HTTP 语义与现有 API 分派 | 24 通过 | `minutes-freeze-wire-green.log` |
| 完整内容比较、元数据分页、生成准备续期 | 40 通过 | `publication-content-reuse-green.log` |
| 发布准备声明范围、续期和写入边界 | 33 通过 | `publication-continuation-green.log` |
| 当前稿完整读比较、复核、预算与范围 | 34 通过 | `read-final-comparison-green.log` |

这些测试组部分重叠，不将表中数量相加称为独立测试总数。728 项完整前端回归包含实际绝对 URL 的权限续验、网络队列和生成回执作用域测试。首次直接从仓库根目录调用全部前端测试时，部分旧测试按 cwd 查找 Vue 文件失败；改用 npm 前端包入口后完整通过，错误启动不计入通过结果。

## 真实运行中修复的主要异常

- 长历史会议反复捕获完整内容及元数据造成 413：使用自适应有界分页、完整字段 SQL 比较和私有原始声明延续，保留原累计预算、全量成员比较、真实权限与提交时复验。不提高限额、不重置账本，不以哈希或长度代替完整字段比较。
- 在有 482 条证据的同一会议上对 8 行元数据进行逐字段锁定比对。按同列和相同限定条件合并查询，并为主键增加可索引的等值条件后，12 组交替实测的单页查询中位数由 18.761 毫秒降至 0.940 毫秒，两组查询每次均返回相同的 8 行。两次尝试超时才完成的原发布记录保留；重启后在原发布上实际完成三格式下载与撤回。见 `long-history-metadata-timing.json` 和 `exclusive-62-publication`。
- 发布后台任务更新时，就绪状态的只读请求曾在初读与锁定比较之间返回 503。现在只在确认发生元数据变化时，回滚并重新完成有身份检查的整次读取，最多三次，共用原预算和截止时间。最终比对逐字段检查已捕获的内容，原有确认写入继续执行自己的完整校验。原测试发布也已完成三格式下载、独立撤权及撤回后的访问验证，见[原发布复验](../../../artifacts/ai-minutes-repair-2026-11-05/exclusive-66-original-publication/publication/report.json)。
- 发布准备期间凭据过期：在获取实际读取门之前重新准备外部证明；只在尚未取得内容权限时有限重试，已获取内容后不吞掉拒绝或漂移。
- 已发布页面续验被耗时内容读取阻塞：无知识库材料且发布证明有效时使用独立提交快照的只读 ACL 查询；前端普通 JSON 请求最多并发 4 个，并为精确的权限续验路由保留请求容量。浏览器失败时序本身不足以证明连接排队；已确认的服务端问题是导出任务并发变化引起治理证明元数据漂移，现以同一预算、同一原始发布关联进行有限完整重读，且刷新后重新检查当前 ACL。4.5 秒显示截止时间与原拒绝规则不变。
- 正式文档的合法发布配置被误拒绝：有界提供方解析器使用 Long，存档解析器对小整数使用 Integer，Map.equals 错判相同标签规则；改为验证完整结构后按既有规范化值比较，仍校验完整内容哈希、精确版本和发布关联。
- 页面在收到生成回执时立即离开，可能在清理前卸载命令控制器，刷新后误以为原请求仍待确认。现按当前会议、身份和操作范围在发布有效回执前清理记录，已离开的旧请求不能清除新会议的记录。
- 撤回后的正式文档读取曾返回 503。现在在首次读取及发送结果前，分别检查发布生命周期，撤回后返回 403。
- 一场经过多次模板变更和版本恢复的会议中，外部模型已成功返回，本地重复读取完整历史，构建候选时耗尽累计预算并误报模型输出无效。现在在原有权限、租约和锁下逐字段比较第二次捕获的完整内容，区分外部输出不合约与本地准备失败。原任务已成功，原候选可读取、拒绝，当前稿和后续生成资格已核对。证据为 `exclusive-59-history`。
- 查询任务详情时，长历史可能因为把单页容量当作整次查询上限而返回 413。现在按完整主键分页，重试只在相同选择范围内复用逐字段比对成功的历史行。分配上限、累计计数、并发变更检查和所有来源权限继续生效。
- 联调模型曾将全部内容复制到个人模板的每个主题，一条行动会在八个主题中出现八次。兄弟项目现按主题分配原始摘录，保留来源及正文定位，已保存的联调执行仍可精确重放。五项契约测试通过，日志为 `personal-heading-partition-green.log`。
- 对冻结纪要申请编辑曾返回 503：修改操作现在返回既有 409 MEETING_CONFLICT；读取不可用仍保持既有 503，不扩大编辑权限。

本次扩大 Maven 运行还触发了依赖旧 B3 一次性历史归档的测试。对应 configuration ACK 和业务快照不在当前工作区，这 10 个测试类共 58 项不能记为通过；单独记录缺失输入，不伪造历史归档替代。

逐次失败、修复、重启与测试会议状态见 `progress.md`。保留失败证据，不通过删除历史、重建发布编号或直接修改业务行伪造成功。

## 重启与真实操作方式

从项目根目录调用 `tools/dev/ai_minutes_validate.py`，在同一生命周期锁内重建、重启并执行浏览器脚本，避免测试中途被另一个重启覆盖。完整运行脚本为 `tools/dev/ai_minutes_suite_e2e.mjs`；视觉脚本为 `tools/dev/ai_minutes_visual_matched_e2e.mjs`。

本专项验收以现有 Vue 源码构建本地前端产物，由同一端口的 Vite preview 代理业务 API 和 CAS 登录。这样刷新使用带内容指纹的 JS/CSS，避免开发服务器分发数百个源码模块时发生的启动中断。原开发启动支持热更新。构建输出到专用运行目录，Vite 提示主包大于 500 kB；这条体积提示已保留。启动器测试 21 项通过。

浏览器使用真实登录、按钮、弹窗、文件上传/下载；必要的业务准备和状态读取使用已登录身份的正式 API。故障注入通过固定 harness 的精确测试资源匹配规则，结束后清理。没有修改参考 HTML、以接口返回替代页面可见性检查，或用 SQL 强行推进业务状态。

## 完成边界

上述连续九阶段运行、同构建视觉对照及运行签名和健康核验均已通过。两个视口的标注差异逐项保留在视觉报告和实施基线中，包括实际来源、条目编辑入口、真实复核及业务规定的两类敏感内容。

harness 的可重复输出验证流程、数据绑定、错误处理和联调契约，不证明生产模型总结质量。敏感排除的文字冲突检查不等同于任意语义改写的识别保证。原型与需求有明确冲突的项目按基线逐项记录，不以自行改造界面消除冲突。
