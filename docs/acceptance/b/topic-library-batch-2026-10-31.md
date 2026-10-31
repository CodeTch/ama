# 专题与知识库当前批次接续（2026-10-31 18:58）

用户已要求停止。仅记录当前结果；**B/C 总体仍未完成、未验收**。所有工作位于 `/home/tanren/ama` 的 `main`，单 agent，无新分支、worktree、提交或清理。本批没有修改兄弟 harness 源码，固定端口8792/2525未变。

## 环境事故与停止状态

用户明确确认误删了容器、镜像及持久卷。只读现场观察为45907拒绝连接、Docker无法inspect原自有MySQL容器 `ama-local-dev-692731c0919e`。原主库 `ama_local_dev`、C测试库 `ama_c_recording_a21835cf4f93` 此前迁移至V044；**当前数据库不存在可访问实例，不能把旧库状态当作现存事实**。未拉镜像、创建卷、创建数据库或正常重启/部署服务。数据库恢复或新环境重建必须在用户后续恢复任务后单独执行；不能承诺误删卷中的业务数据可恢复。

本批Maven与前端验证已结束。录音驱动PID2091026在全部100录音任务失败后，以SIGINT有序退出，finally已写入实际observations。未停止其余服务进程：前端/API/Worker/文件/受控模型/知识库OS进程仍存活，但依赖数据库的业务不可用，不能称服务健康。没有新增调度、子agent或独立测试API，后续不会自动推进开发。

## 专题本批完成的实现和测试

- 全量私有成员数摘要与当前专题revision；真实当前owner/身份门、拒绝foreign和损坏外部关联，独立于会议列表分页。
- 新删除会议在原事务/授权/版本/生命周期门内退役个人专题归组，避免新产生无法删除的专题；不做历史全库清理，不改会议内容版本或ACL。
- 专题冷恢复用原请求meetingIds恢复真实批量目标数，不编造旧会议名称；非空专题使用原型“无法删除”状态，读取未知时不提供新删除命令，当前成员数和revision由真实摘要核实。
- `TopicSummaryIT`原8项通过后增加两个真实事务负例：版本冲突保留归组；实际生命周期事件校验后故障回滚会议/归组/审计/outbox/原请求键，并能用同键真实重试。最终 **10/10 PASS**：`.tools/local-app/topic-summary-atomic-green.log`。
- 摘要HTTPwire **1 PASS**；SDK/SSR此前10项通过。OpenAPI与生成类型已更新。`tools/dev/topic_crud_cas_integration.mjs`已补批量丢ACK刷新、非空阻断和删除会议后空专题删除案例，**新版本尚未部署运行**。
- 新筛选/分组/非空专题像素和当前代码CAS资格仍待完成；`TOPIC-01`保持OPEN。

## 知识库本批完成的实现和测试

固定8792真实签名协议探测，dev-member对既有正式文档GET返回200，`?view=location`返回404；只读探测没有修改既有发布夹具。证据 `.tools/local-app/library-location-protocol-red.json`。

受控provider新增精确DOCUMENT_READ位置查询：当前文档READ授权、真实library和完整目录链、当前grant版本及canonical元数据SHA；严格拒绝损坏目录和未知查询。4项临时真实Ed25519/SQLite/publish/read授权测试RED→GREEN（`library-location-provider-{red,green}.log`），**运行中的旧provider未重启，不能说8792新查询已GREEN**。无需修改harness固定端口或桥接认证规则；该能力只代表明确的受控模拟协议。

AMA新增 `GET /api/v1/meetings/{meetingId}/publications/{publicationId}/library-context`：当前成员READ→完整PublicationDocumentRead图和不可变发布事实→当前viewer的provider DOCUMENT_READ→严格provider/store/viewer/document/result/publication/payload/library/folder/version绑定→重新完整读取并验证当前READ和事实一致。不会借用owner目的地发现、原requester回执、写权限或源文件权限。版本token是受控元数据的canonical SHA，不冒充公司知识库版本。

- 元数据校验 **5 PASS** +服务边界 **4 PASS** +原文档边界 **5 PASS**，合计 **14 PASS**（`library-context-green.log`）。服务测试是明确的严格合成只读JDBC图，不冒充真实库/CAS端到端证据。
- HTTP真实路由404/未启动async的RED→映射修复；当前文档/新路由 **4 PASS**，实际异步会话过滤器 **6 PASS**，合计 **10 PASS**（`library-wire-green.log`）。
- 新SDK、真实分页loader和页面已接到应用与 `/meeting-library/:id?`；每次显式加载一页、最多两路context读取、空过滤页保留真实cursor、暂态失败重试同页、重复cursor拒绝、身份/取消隐藏旧事实。401时取消同批其他读取经过真实前端RED→GREEN。查询来源会议用真实GET信息和新context，保留最新读到的位置版本。
- 最终定向前端 **14 PASS**（含知识库9项、专题摘要/恢复/原型布局5项）：`library-final-web-green.log`；**typecheck、build、OpenAPI生成字节校验全部PASS**（`library-final-typecheck.log`、`library-final-build.log`、`library-final-api-bytes.log`）。没有在长测期间跑全套或重建部署API。
- **当前代码未部署，新知识库真实CAS/provider路径、权限负例/分页/刷新/三视口精确像素尚未验收，LIB-01保持OPEN。** 原型原文件不变。全局目录按真实可访问已发布会议读取，不报告已加载页为完整总数。

## 两小时长测真实结果

`.tools/local-app/recording-load/renewal-100-7200s/interrupted-qualification.json` 为独立中断资格报告，**FAIL_INTERRUPTED**。原驱动在SIGINT后未写正常 `report.json`，因此保留原事件/observations并使用该案例冻结的原聚合器计算失败结果；不伪造正常结束。

实际100录音/300CAS用户；原计划7200秒；停止时观测进度2490.904秒（约41.5分钟），最后ACK约采集2278.073秒；累计 **113152 ACK，0完整归档验证，100录音任务失败**。所有录音最终错误均503/IDENTITY_UNAVAILABLE。报告missingAckedChunks113152表示未完成归档读回覆盖，**不是已经证明原始持久化PCM全部丢失**；用户确认的数据库卷误删应独立看待，未尝试数据恢复。模型仍DISABLED_NOT_INSTALLED，字幕/真实模型性能未签收。

保留 `events.ndjson`、`observations.json`、`execution.json`、冻结driver/worker/files、development-activity、只读诊断、`stop-observation.json`与`user-confirmed-environment-incident.json`。此次失败不能替代完整7200秒或模型资格。

## 代码/部署签名与后续顺序

当前工作源码：`957cda0d1c61c1ac2d0eac63d58f96c4894956d2293ee2a4806a0bb73ed93d34`。主API旧冻结artifact签名：`582495f1bbf2c5cb6ce035155c9bdb3d3dcf028fb34978f61508c79e7c34ec62`。二者不同是事实，未手工修改state绑定。旧5824的反馈8组业务/36零像素、专题7组/60零像素、native真SIGKILL10项等历史证据保留，不能套用到新专题/知识库代码。

用户恢复工作后优先：

1. 核实误删环境/卷的恢复可能性；若需新建环境，明确全新fixture和数据库来源，不沿用已消失库的状态或旧CAS业务数据。
2. 恢复固定8792兄弟服务与本项目自有数据库/文件/模型/知识库配置，正常迁移至V044并进行基础健康与原始字节验证；保护留存发布/录音证据，缺失数据不能补写成原记录。
3. 顺序跑适当后端/前端回归，正常部署绑定新sourceSignature；再运行专题更新CAS脚本、新增专题像素和知识库真实联调/像素。不得用HMR或新单测充作新API已部署的证明。
4. B剩余首页/名单/材料/转写/纪要模板候选/审阅/最终/发布等矩阵继续；C02生产speech阶段/全局配额/未知回查、C03 AI建议和speaker、C06持久字幕/会后处理仍未安装。C07在环境恢复后需要重新执行完整两小时；不缩短时长、不覆盖本次失败。

本文件为停止时的状态记录，不表示恢复工作已获新的执行指令。
