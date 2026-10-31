> **2026-10-28 当前范围（AMA-DEMO-FIRST-20261028 R2）**：按[Demo 优先调整计划](2026-10-28-ama-demo-first-adjustment.md)执行；前期本地 MySQL＋本地文件＋单机，优先原型页面与核心业务联调。已完成/已采纳能力保留，不重做；S3、双 MySQL、双活全部尚未完成的专用实现、配置、测试和依赖归最后 F，前期不新增相关抽象或架构改造。本轮仅调整文档。 C-04/C-07 的 S3 适配与专属验证归 DEP-S3；本地音频/录音业务、进程恢复与协议资格保留。

---

# C：音视频处理与实时录音 Implementation Plan

> **For agentic workers:** 沿用 AMA-WORKFLOW-20261027 与 Demo 优先 R2 的 executing-plans 内联流程、完整业务批次和集中审查；旧步骤模板不自动触发新代理、重复开发或提交。

**Goal:** 交付正式音视频转写工作台和可恢复的在线录音，验证 100 路实时接入及真实模型延迟。

**Architecture:** 浏览器采集与服务器持久化协议分离；字幕不作为保存确认。离线音频经受限子进程和任务链处理，实时调用独立配额。

**Tech Stack:** Java 21、Spring WebSocket、AudioWorklet/IndexedDB、FFmpeg、MySQL、local/S3、JUnit/Vitest/Playwright。

**Spec:** [架构](../specs/2026-10-21-ama-architecture-design.md) 第 5—8、11 节；[总计划](2026-10-21-ama-delivery-plan.md)。

## Global Constraints

- 继承总计划；每场一个采集端，100 场录音用户计入总计 300 在线。
- 输入转换到 16kHz/单声道/16bit PCM，不能假定浏览器实际输入已是 16kHz。
- 每 2 秒分片；浏览器未 ACK 缓存建议 10 分钟约 19.2MB；达到限额明确阻断/提示，不能无限缓存。
- 临时字 p95≤3 秒、稳定字 p95≤5 秒均从语音片段结束计；没有 tokens 时只提供可靠片段定位。

## Review Focus

1. 多段排序改变后引用时间仍定位原录音：C-01/C-02 `orderedClipsKeepSourceMapping`。
2. 模型无 tokens/跨段说话人不稳定：C-02 `optionalFieldsAreNotInvented`。
3. 同序号不同音频以及旧连接同时写入：C-04 `staleWriterAndHashConflictRejected`。
4. 48kHz 设备、浏览器休眠和缓存满：C-05 的重采样/恢复/限额测试。
5. 稳定识别结果迟到而会话已结束或删除：C-06 `lateTranscriptCannotReviveDeletedMeeting`。

## Task C-01：多文件采集与受限音频标准化

**Files:** Create `backend/contracts/src/main/java/ama/contract/MediaContracts.java`、`backend/core/src/main/java/ama/media/MediaProbe.java`、`backend/adapters/src/main/java/ama/media/FfmpegNormalizer.java`、`backend/worker/src/main/java/ama/worker/MediaPreparationHandler.java`、`backend/adapters/src/main/resources/db/migration/V009__media_sources.sql`、`backend/integration-tests/src/test/java/ama/media/MediaPreparationIT.java`、`config/media-capabilities.json`。Modify `web/src/features/capture/ImportMeetingDialog.vue`。

**Interfaces:** Consumes B-04/B-05；Produces `MediaProbe.probe(String fileId): MediaInfo`、`normalize(List<OrderedSource>): NormalizedAudio`；OrderedSource 为 fileId/order，NormalizedAudio 含 fileId/sampleRate/channels/bitsPerSample/sampleCount/sourceMap。合法输入组合、格式、大小/时长由 A-02 的能力表冻结，不默许任意音频/视频/转写混合。

- [ ] 写 `orderedClipsKeepSourceMapping`、`invalidOrOversizedMediaIsRejected`，两段交换顺序后仍可回到原 fileId/time；输出 `assertEquals(16000, audio.sampleRate()); assertEquals(1, audio.channels()); assertEquals(16, audio.bitsPerSample());`。
- [ ] 运行 `python3 tools/checks/run.py it MediaPreparationIT`，确认标准化或来源映射失败。
- [ ] 实现探测、排序快照、视频音轨抽取、流式转换、子进程时限/CPU/磁盘上限；每台重任务初始最多2个，不能通过 shell 拼接文件名。
- [ ] 重跑 IT，覆盖损坏容器、无音轨、中文长名、任务取消/进程退出和无有效主文件阻断；验证不同输入类型的可用/不支持原因。
- [ ] 提交 `feat: prepare ordered media with bounded normalization`。

## Task C-02：VAD、说话人、ASR 与增强流水线

**Files:** Create `backend/contracts/src/main/java/ama/contract/SpeechContracts.java`、`backend/core/src/main/java/ama/model/SpeechModelPort.java`、`backend/adapters/src/main/java/ama/model/CompanySpeechAdapter.java`、`backend/worker/src/main/java/ama/worker/TranscriptionHandler.java`、`backend/core/src/main/java/ama/transcript/SourceTimeline.java`、`backend/integration-tests/src/test/java/ama/transcript/SpeechPipelineIT.java`。

**Interfaces:** `SpeechModelPort.vad(WavClip): List<SpeechSpan>`、`diarize(WavClip,List<SpeechSpan>): List<SpeakerSpan>`、`recognize(WavClip): RecognitionResult`、`punctuate(String): String`、`embed(WavClip): VoiceEmbedding`。WavClip 为 fileId/startSample/sampleCount；时间结果为毫秒；RecognitionResult 为 text/tokens(可空)，VoiceEmbedding 为 modelVersion/dimension/vector。C-01 定义的来源映射不可丢失。

- [ ] 写 `optionalFieldsAreNotInvented`、WAV/Base64/Host 契约、超时/429、跨段映射；`assertNull(result.tokens()); assertEquals(originalFileId, locator.fileId());`；匿名聚类不得生成真实企业 ID。
- [ ] 运行 `python3 tools/checks/run.py it SpeechPipelineIT`，确认协议和定位行为失败。
- [ ] 实现分阶段任务、全局模型额度、标准化音频流、有限重试、原始/增强结果分开；长音频分段方案须按 A-03 证据选择，不能任意切断说话人聚类上下文。
- [ ] 重跑 IT，再运行 `python3 tools/checks/run.py contract SpeechPipelineIT` 验证真实端点；各阶段失败可恢复且成功阶段不重复覆盖；无质量基准不声称识别准确率达标。
- [ ] 提交 `feat: integrate speech models with traceable transcription stages`。

## Task C-03：转写核对、纠错和说话人工作台

**Files:** Create `backend/core/src/main/java/ama/transcript/CorrectionService.java`、`backend/core/src/main/java/ama/transcript/SpeakerService.java`、`backend/adapters/src/main/resources/db/migration/V010__corrections_speakers.sql`、`backend/integration-tests/src/test/java/ama/transcript/CorrectionRevisionIT.java`、`web/src/features/transcript/TranscriptEditor.vue`、`web/src/features/transcript/SpeakerEditor.vue`、`web/src/features/transcript/CorrectionPanel.vue`、`web/src/shared/media/AudioPlayer.vue`、`web/tests/e2e/transcript-workbench.spec.ts`。Modify B-06 契约/控制器及 `TranscriptPage.vue`。

**Interfaces:** `PATCH /meetings/{id}/transcript/segments/{segmentId} {expectedRevision,text,speakerId}`；`POST /corrections/{id}/decision {expectedRevision,decision:accept|reject}`；`POST /meetings/{id}/transcript/replacements {expectedRevision,occurrenceIds,replacement}`；`POST /speakers/{id}/merge {targetSpeakerId,expectedRevision}`。纠错/编辑形成可撤销 operationId；全文搜索返回完整稿的片段 ID，不仅当前 DOM。

- [ ] 写同形词批量替换精确范围、拒绝后无待处理、撤销遇新版本冲突；核心 `assertEquals(409, staleUndo.status()); assertEquals(0, pendingCorrections);`；说话人实名/合并后 ACL 不变。
- [ ] 运行 `python3 tools/checks/run.py it CorrectionRevisionIT` 和 `python3 tools/checks/run.py e2e transcript-workbench.spec.ts`，确认规则或交互失败。
- [ ] 实现音文跟随、播放定位、行内/面板纠错的已批准布局、说话人修正、保存冲突和长文虚拟化；AI 增强新结果先成候选，明确采用才更新稿。
- [ ] 重跑测试；编辑期间播放器/后台刷新不抢焦点；纯文本输入关闭不可用音频能力；确认按钮与后端同样要求所有纠错处理完成。
- [ ] 提交 `feat: deliver transcript correction and speaker workbench`。

## Task C-04：录音服务端协议与可靠归档

**Files:** Create `backend/contracts/src/main/java/ama/contract/RecordingContracts.java`、`backend/realtime/pom.xml`、`backend/realtime/src/main/java/ama/realtime/RecordingSocketHandler.java`、`backend/core/src/main/java/ama/recording/RecordingService.java`、`backend/worker/src/main/java/ama/worker/RecordingArchiveHandler.java`、`backend/adapters/src/main/resources/db/migration/V011__recordings.sql`、`contracts/recording-protocol.md`、`backend/integration-tests/src/test/java/ama/recording/RecordingProtocolIT.java`。

**Interfaces:** `POST /meetings/{id}/recordings`→sessionId/partId/writerEpoch/backendId；WSS `/api/v1/recordings/{sessionId}/stream`，分片 header 为 partId/writerEpoch/seq/sampleStart/sampleCount/sha256，seq 从1、sampleStart从0；ACK 含 highestContiguousSeq/missingRanges。`POST /recordings/{id}/pause|resume|finish` 携带 expectedEpoch；finish 携带每段 finalSeq，返回 archiveTaskId，尚有缺口不标可用。

- [ ] 写 `staleWriterAndHashConflictRejected`、乱序2先到/重复同哈希/DB失败/存储失败；`assertEquals(0, ack.highestContiguousSeq()); assertFalse(ackClaimsUnpersistedData);`；补齐1后连续到2，完成缺口不得被归档掩盖。
- [ ] 运行 `python3 tools/checks/run.py it RecordingProtocolIT`，确认故障协议失败。
- [ ] 实现单采集者租约、二进制限流、固定后端、存储与清单成功后 ACK、删除栅栏；暂停不计时，重开设备新 part；归档流式合并并校验，不提前清理技术分片。
- [ ] 本阶段在 local 定向运行 IT；S3 适配/差异验证后移 DEP-S3/F；杀实时进程、续租失败、归档重试均不丢已 ACK 数据；旧响应不能覆盖新会话；单元故障注入不替代 F-02 断电演练。
- [ ] 提交 `feat: persist resumable recordings with durable acknowledgements`。

## Task C-05：浏览器采集、缓存与恢复体验

**Files:** Create `web/src/features/recording/pcm-worklet.ts`、`web/src/features/recording/resample.ts`、`web/src/features/recording/chunk-store.ts`、`web/src/features/recording/useRecordingSession.ts`、`web/src/features/recording/RecordingPage.vue`、`web/src/features/recording/resample.test.ts`、`web/tests/e2e/recording-recovery.spec.ts`。

**Interfaces:** `createResampler(inputRate:number): { process(input:Float32Array):Int16Array; flush():Int16Array }` 保持跨帧滤波/采样状态；`ChunkStore.put(chunk:Chunk):Promise<void>`、`removeThrough(partId:string,seq:number):Promise<void>`、`pending(sessionId:string):AsyncIterable<Chunk>`，Chunk 结构与 C-04 一致。会话 composable 提供 start/pause/resume/finish/reconnect，不因路由卸载而自动销毁录音。

- [ ] 写 48kHz 输入1秒输出16000样本、跨帧连续性、10分钟预算及 ACK 删除；`expect(samples.length).toBe(16000);`；E2E 断网后显示未保存范围，重连补传后归零。
- [ ] 运行 `python3 tools/checks/run.py web resample.test.ts` 和 `python3 tools/checks/run.py e2e recording-recovery.spec.ts`，确认采集/恢复行为失败。
- [ ] 实现 HTTPS 麦克风、2秒分片、IndexedDB 有界缓存、站内后台入口、设备中断新段、结束确认和进度；关闭/休眠不承诺继续采集，缓存失败明确提示而不丢弃后继续报成功。
- [ ] 重跑测试，覆盖拒绝麦克风、设备切换、重开标签恢复提示、缓存配额不足和令牌过期；按企业支持浏览器逐一记录。
- [ ] 提交 `feat: add browser recording and bounded offline recovery`。

## Task C-06：短窗口实时字幕与会后补处理

**Files:** Create `backend/realtime/src/main/java/ama/realtime/RealtimeTranscriber.java`、`backend/realtime/src/main/java/ama/realtime/SubtitleEventPublisher.java`、`backend/core/src/main/java/ama/transcript/SubtitleReconciler.java`、`backend/integration-tests/src/test/java/ama/recording/RealtimeSubtitleIT.java`、`web/src/features/recording/LiveTranscript.vue`。Modify SpeechContracts、RecordingContracts。

**Interfaces:** `RealtimeTranscriber.submit(String sessionId,String partId,long seq,WavClip): void`；事件 `{eventId,sessionId,partId,segmentId,revision,status:temporary|stable,text,startMs,endMs}`。SSE/WSS 重连通过 afterEventId 恢复，持久版本是事实来源；真实稳定语义由 A-03 验证。

- [ ] 写 `lateTranscriptCannotReviveDeletedMeeting`、重复词/数字/跨窗口重叠、乱序识别和模型限流；`assertEquals("非常非常重要", text); assertEquals(0, writesAfterDeletion);`；模型失败时已 ACK 音频继续完整保存。
- [ ] 运行 `python3 tools/checks/run.py it RealtimeSubtitleIT`，确认去重/版本/事件恢复失败。
- [ ] 实现实时保留配额、窗口队列和字幕修订，依据 A-03 选窗口/重叠；无在线实名能力则明确匿名；识别积压标出延迟并记录会后补转写任务，不伪造实时字。
- [ ] 重跑 IT 和真实模型测试；计算语音片段结束到显示的全链路 p95，分别报告 temporary/stable；断线后按事件游标恢复不重复显示。
- [ ] 提交 `feat: stream revisioned live subtitles with recovery`。

## Task C-07：100 路协议负载与阶段报告

**Files:** Create `tools/load/recording_load.py`、`tools/load/report.py`、`tools/load/requirements.txt`、`tools/load/tests/test_recording_load.py`、`tools/load/scenarios/realtime-100.json`、`docs/acceptance/c/summary.md`。Modify `tools/checks/run.py` 注册 load selector；负载客户端的异步 WebSocket 依赖仅用于测试并锁定版本，不加入生产镜像。

**Interfaces:** CLI `python3 tools/checks/run.py load realtime-100`；场景含 recorders=100/totalUsers=300/chunkMs=2000/durationSeconds=7200/backend/modelMode，报告提供 missingAckedChunks/duplicateChunks/queueAge/latency/errorCounts/resourcePeaks；模型模拟与真实分开归档。

- [ ] 写报告聚合测试，丢一个已 ACK 分片或少创建一路时不得 PASS；`assert report['passed'] is False`，不能丢弃失败样本计算分位数。
- [ ] 运行 `python3 -m unittest discover -s tools/load/tests`，确认违规负载不能被识别时失败。
- [ ] 实现可校验 hash/seq 的模拟采集端、200其他用户场景和端到端打点；不把100个空闲连接当100场实时录音。
- [ ] C-07 在 local 进行2小时协议负载（不作为首个 M1-Demo 前置）；S3 参数组及适配/负载后移 DEP-S3/F-04；加入断连/单进程退出；真实模型可用时追加真实链路，未达标记录瓶颈并回到责任任务修复。8小时及主机故障满载资格在 F。
- [ ] 提交 `test: verify concurrent recording durability and live latency`；报告各验收项通过/失败/待环境，不把全部统称通过。

## 阶段验收与移交

- [ ] 离线音视频、纯转写输入的来源和确认规则一致；原始/增强/人工/确认稿可区分。
- [ ] 暂停、续录、断线、设备中断、缺口、结束、归档、回听具备可操作状态和错误反馈。
- [ ] C-07 应用容量证据完成；真实实时目标依赖模型通过，不能用模拟模型签收生产实时能力。

## 2026-10-31 执行核验与 C-04 续作顺序

当前源代码中的 C 辅助类不是整阶段交付证明。新增独立账本 `.superpowers/sdd/2026-10-21-ama-phase-c/progress.md` 持续登记实际实现、失败回归和待完成项；上方未勾选的整任务不因单个辅助类通过自动关闭。

C-04 后续按既定 local 存储契约推进：以现有 `FileBackendCatalog/FileStoragePort` 保存不可变分片，保持 session 固定 backend，文件持久化成功后才提交清单并 ACK；数据库提交失败留下可幂等恢复的同键孤儿，不主动清除仍可能需要的字节。为现有有效数据库 PCM 保留显式历史读路径，新文件分片读取失败不得切换后端。接着补归档流式校验/任务/正式文件注册与回听，再连接浏览器 PCM、租约/重连及字幕。该顺序由 C-05/06/07 对已 ACK 字节的依赖决定；不新增 S3/HA 工作。

新增验证使用本轮新建 `ama_c_recording_<nonce>` schema 与新临时目录/mTLS 文件服务，不恢复任何旧受限证据窗口，不删除已有业务或证据。真实公司模型质量与 p95 仍按依赖边界单列；harness 模拟协议通过不能替代这些资格。
