# 在线录音严格修复与复验记录

本轮按用户调整后的口径验收桌面端在线录音功能与性能。原型决定控件、操作及反馈，逐像素一致性已移出验收范围。

## 范围与依据

- 用户授权执行完整修复、重启应用系统、进行端到端操作并修复复验中的异常。实施遵循 `/home/tanren/ama`、`main`、单 agent；没有创建分支或工作树。
- 依据：[最终原型](../../../designDocs/智能会议助手1021版.html)、[一期需求](../../../designDocs/智能会议助手一期需求说明文档.md)、[本轮实施计划](../../superpowers/plans/2026-11-03-online-recording-strict-implementation.md)。原型文件 SHA-256 为 `ab21d0b73f4e48bbf701ee8bca5e958940b61f6e6f680d4ced02de0ce4bab867`，未修改。
- 共享顶栏遵循已经确认的[统一工作区顶栏方案](../../superpowers/plans/2026-11-03-persistent-workspace-header.md)。两种桌面视口为 1440×900、1024×768。
- 外部联调通过兄弟项目 `ama-integration-harness`。本轮按用户授权优化其中的规则匹配和请求处理。固定 HTTP 8792、SMTP 2525 不变。harness 是受控联调实现，其结果不能替代真实企业模型效果或生产接口资格。
- 前次回归时主项目 HEAD 为 `b09400999935a43a1a84676a8ebde34ceca8aa39`，harness HEAD 为 `87f51cefd1c9f0b5f041a0e98ed61a098b54bdce`。共享工作区中其他会话的改动保留，不计为本任务独占成果。

## 实现结果

| 功能 | 实现与核验目的 |
| --- | --- |
| 录音状态与授权 | 授权等待、重复拒绝、设备缺失与保存异常分开表达；重复授权失败复用空段，解除故障后一次点击开始采集。 |
| 暂停、分段与恢复 | 实际采样计时，暂停不增长；中断后独立 WAV 可回听；站内切换保留采集；刷新恢复明确未知尾段，不自动重新打开设备。 |
| 保存与原请求恢复 | 本地待确认和服务端确认范围分别显示；结束与开始处理回执丢失时保留原请求身份、原材料快照及修订；仅保存不发起转写。 |
| 字幕 | 保留成功游标及历史；同一录音分片更新不清空字幕、不取消在途读取；上翻停止跟随，返回最新恢复跟随；识别失败可通过真实重试接口恢复。 |
| 材料与正式处理 | 本地文档实际上传解析；失败材料阻断处理，可重试或移除；允许空材料；开始处理锁定材料快照，后续变更待应用。服务端完成归档、复制来源、独立原始转写及材料增强，离开页面仍继续。 |
| 原稿保护 | RAW 与增强 WORKING 分离；后续材料变更不改写当前任务输入；晚到的初次增强不能覆盖人工修订。 |
| 页面与窗口 | 复用最终原型的录音卡、分段列表、字幕卡、材料抽屉、信息抽屉、结束确认和处理页面结构；失败原因在现有提示区域显示。 |

## 前次回归记录

证据根目录为 `.tools/local-app/recording-strict-20261103/`。失败运行也保留，不能以较早失败结果覆盖后续通过结果，或反向冒用历史通过结果。

| 验证 | 本次结果 | 证据 |
| --- | --- | --- |
| 前端完整单元测试 | 714/714 通过，失败 0、跳过 0 | [完整日志](../../../.tools/local-app/recording-strict-20261103/web-full-resume-01.log) |
| 后端真实 MySQL 集成 | 22/22 通过，失败 0、错误 0、跳过 0；独立验证库已删除 | [集成报告](../../../.tools/local-app/recording-strict-20261103/it-resume-01/report.json) |
| harness 材料增强回归 | 4/4 通过 | [测试日志](../../../.tools/local-app/recording-strict-20261103/harness-resume-tests.log) |
| 固定端口模型契约 | 六类接口正常调用及受控错误场景通过 | [契约报告](../../../.tools/local-app/recording-strict-20261103/harness-model-contract-resume.json) |

22 项后端集成包括 RecordingProcessingIT 3 项、MaterialEnhancementIT 4 项、RecordingTranscriptSourceIT 3 项、DurableSubtitleIT 12 项。覆盖固定材料快照、原请求恢复、人工稿保护、来源复制、权限隔离、字幕游标、重启恢复、识别失败重试及已删除/失效身份边界。

## 已定位并修复的问题

1. 录音续租旧响应可能在新设备声明后关闭新采集。加入续租代次校验及暂停续租，回归先失败后通过，见 `renewal-race-red.log`、`renewal-race-green.log`。
2. 同一录音范围内 PCM 记录更新反复重置字幕控制器，导致字幕历史和在途读取丢失。相同身份、会议、录音范围的同步不再重置；见 `subtitle-scope-red.log`、`subtitle-scope-green.log`。
3. 重试材料解析尚未结束时“移除”仍可点击，造成相互冲突的材料操作。材料操作统一等待当前请求核对完成；见 `material-race-red.log`、`material-race-green-02.log`。
4. 材料增强受控故障已返回具体状态，但处理页只显示笼统失败。补齐归档、原始识别与材料增强的可读原因，并通过真实增强故障验证页面说明。
5. 上次验收中的音频字节核验失败源自测试观察器：Playwright WebSocket 路由代理隐藏了原生发送帧事件。调整为只屏蔽开发 HMR 客户端请求，保留业务 WebSocket；要求实际观察到音频帧、重传字节一致且归档逐片哈希与总长度一致。没有删除完整性断言或伪造成功响应。

## 本轮复验

浏览器运行 Chromium 的模拟麦克风和输出设备。录音数据经过 AudioWorklet、IndexedDB、WebSocket、后端、MySQL 和归档。权限拒绝、设备缺失、断网、丢回执及模型故障均为显式注入。模型侧使用项目固定端口的受控服务。真实麦克风兼容性、公司模型准确率及生产服务延迟需要在相应环境单独验证。

录音中站内切换采用最终原型的悬浮录音入口。浏览器刷新后回到原会议，继续采集需要用户操作。实时说话人标签来自当前音频窗口，临时文字及证据不足的范围显示占位名。处理成功的归档录音支持回听及按分段追溯，会议材料和音频各自维护保存状态。

| 本轮功能验证 | 实测结果 | 证据 |
| --- | --- | --- |
| 浏览器完整录音流程 | 14 项通过，覆盖暂停继续、跨页采集、材料增强失败恢复、结束与处理回执丢失，以及采集帧与归档逐字节比对 | [完整流程](../../../.tools/local-app/online-recording-parity/indexed-strict/report.json) |
| 设备与页面恢复 | 6 项通过，覆盖拒绝授权、原会话恢复、未知尾段提示、两个桌面尺寸与仅保存 | [设备恢复](../../../.tools/local-app/online-recording-parity/indexed-device_recovery/report.json) |
| 录音、材料和权限边界 | 12 项通过，覆盖离线补传后暂停、取消知识库选择、重复引用保护、损坏材料重试及移除、跨账号读取拒绝 | [异常边界](../../../.tools/local-app/online-recording-parity/indexed-edge/report.json) |
| 主项目前后端 | 730 项前端单测、56 项桌面浏览器用例和 22 项独立 MySQL 集成测试通过。类型、OpenAPI 和生产构建通过 | [单测](../../../.tools/local-app/online-recording-parity/unit-release.log) · [桌面用例](../../../.tools/local-app/online-recording-parity/e2e-release.log) · [MySQL](../../../.tools/local-app/recording-strict-20261103/recording-indexed-verified/report.json) · [契约与类型](../../../.tools/local-app/online-recording-parity/typecheck-release.log) · [构建](../../../.tools/local-app/online-recording-parity/build-release.log) |
| 共享模拟器 | 154 项测试通过。固定原始 4,242 条规则及相同 192,044 字节音频，50 次匹配的 P95 从 36.26 ms 降到 2.34 ms，命中的规则 ID 相同 | [测试结果](../../../.tools/local-app/online-recording-parity/harness-final.xml) · [优化前](../../../.tools/local-app/online-recording-parity/matcher-before.json) · [优化后](../../../.tools/local-app/online-recording-parity/matcher-after.json) |

性能复测沿用项目的 10 场录音、50 个真实登录账号、每 2 秒一片口径。字幕延迟从对应音频结束算到客户端收到事件。最初在 60 秒场景中反复对临时字幕做分离，出现积压。将说话人分离放到定稿窗口并与识别并行后，早期长测仍出现超时。排查确认共享模拟器会对同一音频按候选规则重复计算 SHA。修复保留了旧规则及其优先级。共享服务复测的 180 秒预检通过，900 片全部读回核验，临时 / 稳定字幕 P95 为 2.65 / 3.81 秒。[初次短测](../../../.tools/local-app/online-recording-parity/capacity-preflight/report.json) · [提前停止的长测](../../../.tools/local-app/online-recording-parity/capacity-30m/stopped.json) · [共享服务预检](../../../.tools/local-app/online-recording-parity/shared-preflight/report.json)

### 30 分钟并发验收

使用仓库已有的 [10 路录音场景](../../../tools/load/scenarios/realtime-10-30m.json) 和[通过条件](../../../tools/load/report.py)。10 个账号同时录音，另 40 个账号持续读取会议列表和详情。每路发送 900 个两秒音频分片，客户端按对应音频结束时间计算字幕到达延迟。结束后读回全部 WAV，核对每个分片，再选一场从完整归档独立生成原始转写。临时字幕 P95 要求 ≤3 秒，稳定字幕 P95 要求 ≤5 秒。

第一次完整复测收到全部 6,000 条临时和 3,000 条稳定字幕，10 份归档全部核对一致。临时字幕 P95 为 3.394 秒，稳定字幕 P95 为 4.713 秒，按临时字幕指标判为失败。长录音后半段，查询待处理字幕反复扫描历史分片，定稿后的下一片等待较久。改为按当前录音段的最新序号查找待处理分片，并允许工作线程连续处理最多三片已经到达的音频。共享模拟器的规则读取、执行凭证与请求日志改在线程中处理，相应写入完成后再返回。[首次完整复测](../../../.tools/local-app/online-recording-parity/shared-qualification/report.json)

后续一次复测与本机其他项目的大量磁盘读写重叠。490 秒时已有 782 条临时和 353 条稳定字幕超过门槛，即使剩余样本全部达标也无法通过，已提前停止。[停止时的原始计数](../../../.tools/local-app/online-recording-parity/latency-qualification/stopped.json)

在包含 900 个分片的同一录音段上核对 MySQL 实际执行计划，发现仅按序号倒序仍会读取并排序整段。改为按完整主键倒序后，调度查询只读取 1 行。字幕轮询用录音、分段及游标三项直接定位待检查范围，追平时扫描的分片数为 0。[排序执行计划](../../../.tools/local-app/online-recording-parity/long-part-schedulerSort.txt) · [索引倒序执行计划](../../../.tools/local-app/online-recording-parity/long-part-schedulerSeek.txt) · [追平时的查询](../../../.tools/local-app/online-recording-parity/long-part-readerSeek.txt)

**最终复测通过。** 10 场会议同时持续录音 1,800 秒，另外 40 个已登录账号发起了 9,584 次会议列表与详情读取。主项目运行源代码签名与部署签名一致，结束时再次核对一致。共享模拟器前后使用同一代码和配置，启用了相同的 4,242 条规则。[完整报告](../../../.tools/local-app/online-recording-parity/indexed-capacity/report.json) · [逐项汇总](../../../.tools/local-app/online-recording-parity/indexed-capacity/summary.json) · [模拟器运行前](../../../.tools/local-app/online-recording-parity/indexed-harness-before.json) · [运行后](../../../.tools/local-app/online-recording-parity/indexed-harness-after.json)

| 项目 | 通过条件 | 最终实测 |
| --- | --- | --- |
| 临时字幕 | P95 ≤3 秒，事件完整 | 2.608 秒，6,000/6,000 条 |
| 稳定字幕 | P95 ≤5 秒，事件完整 | 4.423 秒，3,000/3,000 条 |
| 音频保存 | 全部分片确认且归档回读一致 | 9,000/9,000 片，10/10 份 WAV 核验一致，分片确认 P95 为 545 毫秒 |
| 会后原始转写 | 从完整归档独立完成 1 场 30 分钟转写 | 来源 28,800,000 个采样点，原始转写末尾为 30:00 |
| 错误 | 录音和后台访问均完成 | 业务错误 0，瞬时错误 0 |

受控模型返回的 3,000 条稳定字幕中，2,986 条带有经核验的说话人分段，其余 14 条使用待识别占位。浏览器功能回归使用模拟麦克风，容量测试使用登录后的协议客户端。这里测量的是本地受控模型链路的保存与到达延迟，企业生产环境的模型效果、网络和物理设备另行实测。
