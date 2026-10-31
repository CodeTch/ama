> **2026-10-31 当前总状态：NOT ACCEPTED。** C01 私有有界 PCM 资源、C04/C05 原生录音/文件/MySQL/归档已有实际局部验证；C02/C03/C06 业务接线、C07 实际100路两小时仍未完成。C07 的100路/300真实CAS用户/60秒尝试已暴露并保留失败证据，不能用单元或单路结果代替容量资格。最新逐步事实见项目根 `DEVELOPMENT_HANDOFF.md` 和 `.superpowers/sdd/2026-10-21-ama-phase-c/progress.md`；下方较早计数均为历史记录。

# C 阶段本地验收记录

日期：2026-10-31（Asia/Shanghai）

当前总状态仍为 **未签收**。下文早期组件与负载入口结果只作历史记录；最新实际完成度以本页后续核验及 `.superpowers/sdd/2026-10-21-ama-phase-c/progress.md` 为准。不得把模拟器协议、组件测试或准备产物记作真实模型质量、完整语音业务或两小时容量通过。

接续验证补充（2026-10-31）：B/C 接续期间固定端口 harness CAS/材料真实浏览器联调 10/10 通过；Web E2E 28/28、Web unit 337/337、Harness pytest 31 passed。C focused Java reactor（contracts/core/adapters/api/worker/realtime）保持全通过，`load realtime-100` 和生产构建通过。全量 Maven 仍只暴露既有 `StoredSourceGraphTest` 2 failures + 3 errors（PROFILE_UNAVAILABLE 测试夹具），未归因于 C 新增实现；真实模型、MySQL integration-tests 和两小时负载仍按环境门禁保留。

此外，固定端口本地 AMA 实例通过 CAS 会话完成真实录音 HTTP 边界回归：会议创建 `201`、录音 session `201`、二进制 PCM 分片 ACK `200`（连续序号 1、无缺口）、finish/archive `200`；请求携带 writer epoch、part、seq、sample 范围和 CSRF，未调用浏览器麦克风。

本轮在 `/home/tanren/ama` 的 `main` 分支单 agent 执行，未创建分支、worktree 或子 agent。范围按 Demo 优先 R2 执行：local 文件、local MySQL/单机协议优先，S3/HA 留到 DEP-S3/F。

已验证的能力：

- C-01：16kHz/单声道/16bit 契约、来源顺序映射、WAV 探测、受限 FFmpeg 转换、输入大小/进程时限、media capabilities 配置、媒体来源和来源映射迁移。
- C-02：可选 token、匿名说话人、WAV clip、embedding 契约；模型端口与阶段处理边界；来源时间线映射。
- C-03：revision 化纠错、显式接受/拒绝、过期撤销冲突、说话人合并 ACL 并集。
- C-04：单 writer epoch、分片哈希幂等/冲突拒绝、乱序 ACK、缺口阻断 finish、recording/archive 协议边界。
- C-05：48kHz 到 16kHz 跨帧重采样、有界缓存、ACK 删除、AudioWorklet 源码和会话状态机。
- C-06：实时字幕事件去重、版本检查、删除栅栏、realtime 模块 facade。
- C-07：`python3 tools/checks/run.py load realtime-100` 注册和 fail-closed 报告聚合；场景参数为 100 recorders/300 users/2 秒分片/7200 秒。

本次续作补充：

- C-01 handler 支持在规范化成功后调用 durable store；新增 JDBC media source snapshot writer。
- C-02 transcription handler 支持按 VAD、diarization、ASR、enhancement、embedding 阶段持久化成功快照。
- C-03 新增说话人编辑、纠错面板和音频播放器组件，并通过 SSR 组件测试。
- C-04 新增 pause/resume、persistence failure fail-closed、local recording HTTP boundary、JDBC recording manifest persistence 和稳定错误映射。
- C-05 新增 recording API client、断线缓存到 ACK 的 uploader 接线。
- C-06 新增 LiveTranscript 组件和 afterEventId 事件回放。

验证命令与结果：

- C 相关 Java focused tests：contracts 5、core 12、adapters 4、api 1、worker 5、realtime 3，全部通过。
- `source .tools/env.sh && ./mvnw -DskipTests package`：通过，所有 9 个模块编译打包通过。
- `python3 -m unittest discover -s tools/load/tests`：2 个测试通过。
- `python3 tools/checks/run.py load realtime-100`：2 个测试通过。
- web unit 集：337 个测试通过；recording/transcript 续作组件、`npm run typecheck`、生产构建和 OpenAPI generated-type check 均通过。

待环境证据：真实模型协议、MySQL integration-tests、100 路两小时端到端负载和真实 p95 延迟尚未在本机执行；这几项需要固定的 integration harness、模型服务和数据库环境，不能用模拟结果代替。

全量 Maven 测试还会触发既有 `StoredSourceGraphTest` 的 5 个 `PROFILE_UNAVAILABLE` 失败；该失败发生在现有 minutes profile fixture，与 C 本轮新增代码无依赖关系。C focused tests 和全量编译均不受影响。

## 2026-10-31 核验修正与后续工作

上文是较早的定向验证记录，不能据此签收 C 阶段。最新生产 Java 模块单元测试共 655 个通过，Web 单元 338 个通过、E2E 28 个通过。`StoredSourceGraphTest` 的材料 v2 夹具已修正且独立 golden vector 校验通过；根 Maven 运行继续进入 integration-tests，暴露历史证据文件、仓库根和受限夹具环境缺失（4 failures/66 errors，其中两个新投影夹具错误已定向修复）。根 Maven 尚未全绿。

发现原 load 实现没有执行协议传输，却构造了 100 路零错误样本；已移除，未接传输时输出 `NOT_EXECUTED/PROTOCOL_TRANSPORT_REQUIRED` 并判定失败。聚合器现在要求实际用户、录音路数、错误与延迟观测，5 个回归测试通过。这些测试只验证报告判定逻辑，不是 100 路两小时负载证据。

C-04 实际 JDBC 仅写 hash/长度，未保存 PCM，进程重启丢失会话；录音控制器未使用会议对象 ACL。当前正在修复这两个实现缺口并添加新建独立本地 MySQL schema 的恢复/回滚协议验证。完成这些修复前，录音可靠持久化与 C 阶段整体验收保持未完成。

### 录音持久化与缓存修复的实际验证

新 V034 与 JDBC 实现已通过独立本地 MySQL 集成：RecordingProtocolIT 5/5（字节、重建服务、事务回滚、旧 NULL PCM、并发重复），RecordingAccessIT 1/1（真实 HTTP/session/当前会议 ACL，外来身份与删除中会议拒绝）。日志 `.tools/local-app/recording-http-mysql-final.log`；schema 为本轮独占 `ama_c_recording_eb96eed04c36`，未使用历史 `ama_b01_fixture`。所有录音 HTTP 操作在当前会议授权锁内执行；二进制读取最多 64001 字节，错误稳定映射为 403/409/413。

缓存 7 个回归通过，重采样 4 个回归通过；新增 wrapper 将此前遗漏的 feature 目录测试纳入默认 Web 集，当前 Web 351 通过，生产 Java 662 通过。类型检查和构建通过。此结果证明本轮修复，不代表归档/回听、多 part、租约或真实实时识别已经交付。

### 多段恢复和固定 harness 语音协议

V035 保存真实签发 part 的顺序；暂停后重开设备签发新 part 并递增 writerEpoch，旧写入者被拒绝，finish 必须声明全部段。最新独立 MySQL/真实 HTTP 定向验证 7/7 通过（`.tools/local-app/recording-parts-mysql-fixed.log`）。实际 CAS 录音 prepare 5 项和整套 AMA 进程/数据库重启后 recover 4 项通过，3 个 64000 字节 PCM 的实际 SHA2 与持久化清单一致；报告在 `.tools/local-app/recording-cas/`。

C-02 增加真正 HTTP 语音适配：VAD/SPK 完整 WAV Base64，ASR/embed 二进制 WAV，显式 Host/auth/地址，毫秒映射回 clip 样本偏移，未返回的 tokens/confidence/企业身份保持缺省。固定 8792 的 SpeechPipelineIT 首先真实失败于 harness 默认 VAD 返回超过两秒音频的片段。同步修复仅默认种子时间范围，显式自定义故障保持原样；harness 全量 34 通过；Java 五阶段实际 HTTP 链通过（`.tools/local-app/speech-harness-green.log`）。这只验证模拟器协议兼容，真实模型质量/延迟及完整业务流水线尚未签收。

### 本地不可变分片接线（继续中）

V036 为新分片引入不可变文件对象键。生产配置改为使用现有 FileBackendCatalog/FileStoragePort，session 签发时固定实际 backend，新分片先经 mTLS 文件服务持久化再提交清单，不再写数据库 PCM；历史有效数据库 PCM 有显式读路径，新文件读失败不会切换后端。当前已通过真实本地卷/mTLS 的分片读回、文件写入失败无 ACK、数据库回滚保留同键文件并精确重试三项（`.tools/local-app/recording-file-rollback.log`）。独立文件进程退出恢复、孤儿文件不同内容冲突和生产 CAS 重启尚在本轮续测；应用进程尚需部署该批改动。

本批接线前最新生产 Java 单元 667 通过，Web 351 通过。文件分片通过不等于归档任务/正式文件/回听已完成，后续继续按 C 账本推进。

### 文件分片实际部署和整套进程恢复

`.tools/local-app/recording-file-native-green.log` 的 4 个 MySQL＋真实本地卷/mTLS/独立文件进程测试全部通过，包括文件进程被强制结束后重启恢复。孤儿对象同序号不同音频的类型回归先失败后修复，当前稳定返回录音冲突；原请求仍可精确重试。

生产配置已部署 V036。新 CAS 会议 `a97f00b6-2375-4534-9a36-1a69c8276c52`、session `7a45f61a-7893-4c9c-aa30-59dd92761f6b`，实际 backend 固定为 `2665aaaae7a1ce354d04f7143a66725bd1aceea7b9653dca340ee0e1c059b74a`。prepare 5 项、整套 AMA/数据库重启后 recover 5 项通过；两段四个 64000 字节分片均通过配置的 mTLS 文件接口读回并逐一校验 SHA256（`.tools/local-app/recording-cas-files/actual-file-readback.json`）。该结果证明已 ACK 文件分片和多段恢复；归档任务、正式文件注册、回听、租约和生产 PCM/字幕仍需完成。

### 持久归档登记与回听接线

V037/V038 和当前 API/worker 代码把本地录音归档安装到同一真实任务队列。普通 owner registry 调用不能绑定 PREPARED 文件；必须持有队列同一实际事务的 WRITING invocation。归档 READY、保护引用移交与任务成功同时提交，成功事件失败时全部回滚。工作台有限类型与持久输入关系校验支持录音归档，前端依然精确校验会议范围的详情地址。

`.tools/local-app/recording-archive-retry-green.log` 五项真实 MySQL＋本地卷/mTLS 集成测试通过，包含归档重放、非法完成回调/旁路登记拒绝、提交失败回滚、三个真实失败尝试/退避后显式新代次重试、删除栅栏、完整 WAV/Range/416。新专用 schema `ama_c_recording_a21835cf4f93` 保留所有失败历史与音频，未清空旧夹具。当前广泛验证生产 Java669、Web352 全绿，生成契约字节一致、类型/构建通过；部署与固定 harness CAS 回听链路正在执行，后续记录结果。单采集者租约/WebSocket、生产采集与字幕、自动语音业务和实际容量仍未签收。
