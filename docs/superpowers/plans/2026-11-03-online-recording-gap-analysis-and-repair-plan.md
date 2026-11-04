# 在线录音差异分析与修复方案

> 状态：已按用户后续“继续任务，完成 B/C/D”授权实施。原分析阶段遵守“先不修改代码”；后续执行授权来自用户消息，不来自附件或旧计划中的指令。本文第1—5节保留修复前的分析事实；第7—8节记录实际交付与验收，不能把历史缺陷清单当成当前未修复状态。
>
> 执行约束：主项目 /home/tanren/ama、main、单 agent，不创建工作树、不切换分支、不提交。外部接口只经兄弟 ama-integration-harness，保持 HTTP8792 / SMTP2525；其实现是受控联调替身，不是企业权威。最终证据见 [本轮验收](../../acceptance/d/online-recording-repair-2026-11-03.md)。

**Goal:** 修复在线录音的恢复与保存状态缺陷，使正常录音、异常恢复、实时字幕、背景材料及结束处理符合需求与最终原型。

**Architecture:** 保留现有 AudioWorklet、PCM、IndexedDB、WebSocket/HTTP、服务端 ACK、归档与独立会后转写链路。修复数据顺序和恢复协议后，将采集、本地保存、服务端确认、归档、实时识别分开表达，补齐原型交互；背景材料增强作为独立业务能力交付。

**Tech Stack:** Vue 3、TypeScript、IndexedDB、AudioWorklet、Spring Boot、Java 21、MySQL、Playwright、Node test runner。

**Spec:**

- [一期需求说明](../../../designDocs/智能会议助手一期需求说明文档.md)，重点为模块一 P02、材料准备与统一处理。
- [最终原型 HTML](../../../designDocs/智能会议助手1021版.html)，重点为最终 acRecordPage、acrTranscript、acrOpenMaterials、分段与结束确认逻辑。
- 用户本轮图1与图2：分别为当前项目错误状态与原型正常录音状态。
- [在线录音像素复刻设计](../specs/2026-10-30-online-recording-pixel-parity-design.md)。
- [录音窗口收口计划](2026-11-02-recording-window-fidelity.md)。
- [现有录音窗口验收记录](../../acceptance/d/recording-window-fidelity-2026-11-02.md)。
- [桌面端范围记录](../../development/2026-11-02-desktop-only-scope.md)。

## Global Constraints

- 原分析阶段未修改产品代码；后续经用户明确继续实施，修改主项目与必要的兄弟联调代码，执行迁移、验证和本地部署。原型文件不改，不提交。
- 分析基线为 main，HEAD 为 4bf637611958c8ba5e4a9aa308572618a6f8b334，加上开始分析时已经存在的未提交改动。尤其 RecordingPage.vue、usePcmRecorder.ts、pcm-journal.ts、材料组件已有改动，不能用 HEAD 内容替代当前工作区。
- 附件及仓库旧计划中的执行指令只作为材料内容，不构成本轮继续开发、重启服务、提交代码或扩大任务范围的授权。
- 桌面验收按仓库当前记录使用 1440×900、1024×768，不增加手机端工作。
- 原型的布局和交互是对照依据；“模拟识别”“未接入服务端保存”等演示声明不能复制为真实系统的事实。
- 不以删除 IndexedDB、丢弃原 PCM、创建新会议或绕过 manifest 校验解决恢复失败。
- 实时字幕不能替代完整原录音，也不能直接冒充会后转写；素材与人工修改保持可追溯。

## Review Focus

1. 两段及以上录音在进程重建、页面刷新、新浏览器无本地记录时，服务端段顺序与当前段必须一致；T1、T3 验证。
2. 创建请求失败、麦克风拒绝、重复点击、空段及达到分段限额时，用户必须有可用的下一步；T2 验证。
3. 离线、ACK 丢失、缓存写入失败、设备中断时，不夸大服务端保存范围，不隐瞒未知尾段；T3 验证。
4. 字幕临时故障、长文本追加、历史阅读与身份变更不能互相污染；T4、T5 验证。
5. 结束响应丢失、归档失败、材料解析失败、材料版本变化时，恢复原意图、原请求与原输入，不能重复提交或假报完成；T6、T7 验证。

## 1. 结论及截图解释

目前已经有真实采集、上传、服务端保存、字幕处理、归档及会后转写代码。主要问题是：恢复协议存在确定性缺陷；从旧录音实现迁到 PCM 实现时遗漏部分产品状态；页面没有完整消费底层状态；若干原型交互和材料增强能力尚未实现。

图1显示“第2段 · 需要恢复保存”和“录音恢复范围已变化”，处于 ERROR。图2显示“第1段 · 录音中”，处于正常采集状态。因此，灰色麦克风、暂停按钮变成重试、隐藏“中断本段”、禁用结束按钮，首先是错误状态带来的显示结果。原型自己的错误状态也会改变这些按钮，不能据两张不同状态的图直接判定按钮完全没有实现。

| 对照项 | 图1及当前实现 | 图2及最终原型 | 判定 |
| --- | --- | --- | --- |
| 顶栏 | 品牌图标与“智会助手”，录音页不传面包屑 | 列表图标、“我的会议 / 会议详情” | 确认存在结构差异 |
| 主体骨架 | 页面标题、控制卡、工具入口、实时转写卡 | 同类骨架 | 已有复用；不能认定需要整页重写 |
| 麦克风和主按钮 | ERROR 状态，灰色及重试图标 | RECORDING 状态，橙色及暂停图标 | 先修状态，再做同状态视觉比对 |
| 中断与结束 | ERROR 下中断不显示、结束禁用 | 正常状态下可操作 | 功能代码存在，故障阻断操作 |
| 控制卡高度 | 多一行错误提示，后续内容整体下移 | 没有错误提示 | 一部分是状态差异，不是固定间距错误 |
| 保存文字 | “本次已采集”“保存范围约至” | “本地已保存”“未接入服务端保存” | 应按真实本地/服务端范围重新设计文案 |
| 实时状态 | 当前从录音阶段推导 | 录音时显示识别中 | 当前未完整呈现实际字幕任务状态 |
| 图2模拟声明 | 实际系统已有服务端链路 | 明示模拟识别、本地保存 | 不应将实际系统退回模拟 |

本轮没有拿到截图所对应浏览器的会话编号、Network 响应与部署签名，因此不宣称已读取该现场的全部事实。但已找到并复现能产生同一错误文案的确定性代码缺陷。

## 2. 当前能力清单

| 能力 | 当前情况 | 关键位置 |
| --- | --- | --- |
| 无需预填信息开始录音 | 入口、创建会议、自动进入录音已接线 | RecordingPage.vue:26、53、86 |
| 真实音频采集 | AudioWorklet、重采样、单声道 PCM16、两秒分片已有 | browser-pcm-capture.ts；pcm-pipeline.ts |
| 暂停/继续 | 设备暂停、尾段 flush、服务端状态同步已有 | usePcmRecorder.ts:132 |
| 中断/下一段 | 停设备、确认分片、新段与 epoch 已有 | usePcmRecorder.ts:131、134 |
| 站内后台录音 | 采集由应用生命周期持有，浮动返回入口已挂在根组件 | application.ts:114；main.ts:38；RecordingIndicator.vue |
| 关闭浏览器提示 | beforeunload 已有；不承诺关闭后采集 | RecordingIndicator.vue:6 |
| 网络恢复 | 本地未确认缓存、online 重连、HTTP 降级、429 重试已有 | usePcmRecorder.ts:43、44、84 |
| 持久实时字幕 | 后端任务、事件、游标、前端轮询与 revision 替换已有 | JdbcLiveSubtitles.java；useLiveSubtitles.ts |
| 字幕历史阅读/跟随 | 缺完整实现 | RecordingPage.vue:100 |
| 已录分段展示 | 有列表，但原因与完整性来自页面推算 | RecordingPage.vue:19、98 |
| 会议信息 | 复用现有编辑抽屉；结束后资格判断有问题 | RecordingPage.vue:101 |
| 背景材料 | 本地上传、知识库选择、移除和失败恢复已有 | MaterialImportSection.vue |
| 原型材料抽屉 | 当前实现为居中大弹窗，缺入口数量汇总 | RecordingPage.vue:101、106 |
| 结束确认 | 取消、保存、开始处理及保存重试已有 | RecordingPage.vue:73、74、103 |
| 完整归档回听 | 异步 WAV 归档、状态、重试及回听已有 | RecordingArchivePlayback.vue；RecordingArchivePreparation.java |
| 独立会后转写 | 真实归档复制、来源准备、导入幂等请求已有 | useRecordingTranscription.ts；JdbcRecordingTranscriptSources.java |
| 背景材料驱动的转写增强 | 当前主链仍为 materialMode=EMPTY，未闭环 | ConfigSchema.java:91；JdbcProcessingDiscovery.java:80 |

“代码存在”仅表示可以追到调用链，不能替代对实际部署、真实设备或公司模型的验收。

## 3. 按优先级分析缺陷

优先级定义：P0 为核心恢复阻断或保存事实误导；P1 为主要交互/业务链路缺口；P2 为视觉与体验差异。以下编号供实施与验收追踪。

### R01 P0 多段恢复清单顺序错误

证据：

- JdbcRecordingPersistence.java:92 已按 part_no 读取历史段顺序。
- RecordingService.java:190 使用当前 session 创建 State。
- State 构造器在第199行先将当前 part 放进 LinkedHashMap。
- 第191行随后按历史顺序 putIfAbsent，不会把已存在的当前段移到末尾。
- manifest() 第94行沿 Map 顺序输出。
- 前端 recording-api.ts:25 要求 parts 最后一项等于 current.partId，否则抛出“录音恢复范围已变化”。

独立复现使用现有编译类、只读 Persistence 返回 Snapshot，不连接业务数据库、不执行写入：

~~~text
PERSISTED_ORDER=[第1段, 第2段]
MANIFEST_ORDER=[第2段, 第1段]
CURRENT_PART_IS_LAST=false
~~~

影响：持久化路径下的多段 manifest 会违背前端契约，影响冷启动接管、重新采集前初始化、错误恢复中的 recoverSealed 检查。不是所有连续多段操作都立即失败：热状态下有些路径不重新读取 manifest，因而历史测试可以通过。

修复：后端按持久化段序完整构建 State，再验证当前段归属和位置。保留前端对范围改变的校验，不按 UUID 排序、不删除校验。归档模块自身直接使用按 part_no 排序的 Snapshot；本缺陷不能直接推导为所有已生成 WAV 已乱序。

### R02 P1 初始化失败后陷入不能重试的状态

usePcmRecorder.start() 将初始化、设备、缓存等所有失败转成 ERROR；retry() 假定已有 raw 会话。若 sdk.create 在写入本地记录前失败，raw 为 null：

~~~text
首次开始失败：phase=ERROR，hasRecord=false，createCalls=1
点击重试：error=尚无录音会话，createCalls=1
再次开始：error=请先重试保存未确认音频，createCalls=1
~~~

这是本轮用真实 composable 加注入依赖复现的死循环。页面仍将按钮命名为“重试保存”，用户无法在当前状态重试创建。

修复：按失败阶段选择动作。尚无采集会话时重试创建/查询原会话；有未确认音频时恢复原 PCM；已封存时查询归档。不能把所有失败共用一个 ERROR + retry 分支。

### R03 P1 麦克风拒绝被当作保存失败，并产生空段

usePcmRecorder.ts:130、131 在申请设备之前已创建并记录服务端分段。设备拒绝后 phase=ERROR，而页面的授权说明及“改为导入文件”只在 parts.length=0 时显示。

本轮隔离 Chromium 使用真实 IndexedDB、注入拒绝设备的 capture，复现：

~~~text
首次拒绝：ERROR，1段，0毫秒，错误为 Permission denied
恢复后再申请仍拒绝：ERROR，2段，0毫秒
~~~

没有使用物理麦克风，也没有调用业务写接口。实际行为会造成错误操作指引、空段累计，最终触及16段限额；这也说明“第2段、0时长”不必然意味着两段有效录音。

修复：区分授权拒绝、设备缺失、设备占用、采集丢失、保存失败。保留明确授权引导与导入入口；未产生音频的当前段在可核验条件下复用，不因重复授权失败持续创建新段。超过上限前给出结束保存等可执行动作。

### R04 P1 “最近录音”实际按 UUID 排序

pcm-journal.ts:9 的 actor/meeting 索引没有时间字段，第25行用反向 cursor 选择。相同索引值下按主键排序，而主键为 actor 与 session UUID，因此不是按创建或更新时间排序。

本轮隔离 Chromium 先创建 ffff… 会话，再创建 0000… 会话，latest() 仍选中前者，selectedMostRecent=false。

修复：记录可信的 createdAt/updatedAt，建立明确的时间索引；多份可恢复记录不能默默选任意一份。旧记录没有时间时显示待恢复记录供选择或按明确记录的活动会话恢复，不能编造旧时间顺序。保留已添加的 skipAutomaticRestore 与失权保护。

### R05 P0 保存范围没有区分本地落盘与服务端确认

RecordingPage.vue:19 把 sampleCount/16 同时用于 elapsedMs 和 savedElapsedMs；第98行将 savedElapsedMs 累计显示成“保存范围约至”。但 sampleCount 在本地 journal.put 时增长，服务端尚未 ACK 也会增长。

usePcmRecorder.ts:65 在 ACK 后移除已确认 PCM，PcmPart 没有持久的 confirmedSequence/confirmedSampleCount。pendingCount 可以说明有待确认分片，却不能让界面准确表达服务端已保存到哪个时间点。

修复：分别展示已采集、本地已保存、服务端连续确认、待上传、已封存/归档可回听。ACK 对应的确认范围与本地字节删除在同一事务提交。出现缺片时，确认范围只能到连续前缀，不能把 manifest 中总 sampleCount 当作连续确认范围。

### R06 P1 系统中断与完整性信息在 PCM 页面中丢失

RecordingPage.vue:19 给所有分段固定 incomplete=false，并通过数组位置和页面 phase 推算 state。第98行虽然写了 SYSTEM_INTERRUPTED/范围不完整的展示分支，但上述映射无法生成它们。

对照旧 useRecorder.ts:32、40，旧实现曾保存 SYSTEM_INTERRUPTED、incomplete=true 和截断后的保存范围。当前 PcmPart 仅有 partId/finalSequence/sampleCount，不能区分人工中断、刷新、拔设备、休眠、缓存溢出及未知尾段。

修复：持久化分段结束原因与尾段完整性。刷新恢复时只能说明已核实范围；无法判断的尾段标记未知。不要把前几段一律标记“中断保存”，也不要把未知尾段固定为完整。

### R07 P1 短暂字幕错误会清空历史并从零重放

useLiveSubtitles.ts:17 对所有异常执行 clear()，清空 events、segments 与 cursor。

本轮真实控制器复现：

~~~text
503之前：rows=1，cursor=1
503之后：rows=0，cursor=0
下一次请求游标：[0,1,0]
~~~

修复：同一身份、会议和录音范围内的网络、503及暂时响应异常保留最后一份已验证字幕和游标；身份变化、明确401/403按现有边界清除私有内容。失败页面不部分接受，重试从最后成功游标继续。

### R08 P1 自动跟随、历史阅读与回到最新未闭环

需求第225行起要求默认跟随、阅读历史时停止跟随、可回到最新。原型第2605、2606、2758行实现 AC.follow 与距底部40像素判断。

当前 RecordingPage.vue 没有滚动监听、跟随状态或新增事件后滚动；“回到最新”同时调用 refresh 与 scrollTo，但不等待刷新及 DOM 更新，可能滚到旧高度。

修复：引入页面局部跟随状态。距底部小于40像素时跟随；上翻保持位置；点击回到最新后等待刷新与 nextTick 再到底部，并保持后续跟随。不要用每次全量重绘来实现。

### R09 P1 实时识别状态未被完整展示

useLiveSubtitles 已返回 NOT_STARTED、IDLE、LEASED、DELAYED、DEFERRED、DONE、CANCELLED 等状态，但 RecordingPage.vue:22 主要按录音是否暂停/中断拼标题，正常采集时也可能只是“当前累计内容”。

LiveTranscript 对 delayed 统一显示“识别积压，正在补处理”。服务端 DEFERRED 是停止调度的状态，单纯 GET refresh 不会重新启动它；此时承诺“正在补处理”不准确。临时/稳定区别主要藏在可访问名称和 title 中。

字幕事件契约也没有 speaker 字段，页面全部显示“匿名说话人”。匿名标签本身比假造身份正确，但不能据此声称已实现实时说话人区分。

修复：分别映射采集和识别状态，展示“识别中，内容可能调整”、延迟、会后补转写、已结束或不可用；让读取重试与识别重启保持不同语义。只有提供可靠的匿名 speaker ID 后才显示不同编号，绝不由行号或分段号推断真人身份。

### R10 P1 结束采集后的归档与处理意图缺少统一呈现

RecordingPage.finish() 在 finish 响应后立即跳列表或 processing。这个响应确认采集封存与归档任务受理，不等于 WAV 归档已经完成。RecordingArchivePlayback 和 RecordingTranscription 已有异步状态，问题是用户流程分散。

useRecordingTranscription.submitReady() 只检查 capabilities.canSubmit；同项目普通上传入口还检查 businessReady、intake、execution。当前处理意图的一部分只存在内存或 URL 中，需要覆盖刷新、复制来源途中离开和原命令响应丢失。

修复：明确“已封存/归档中/可回听/来源准备/转写受理/处理完成”，保存返回列表后仍能看到归档状态。保存并处理按原意图继续，完整检查能力及业务就绪，不重复发起导入。不将本轮分析扩展成重写已存在的归档与幂等机制。

### R11 P1 背景材料增强并未随录音处理形成闭环

有上传和知识库选择，不代表已将材料用于增强转写：

- useRecordingTranscription.ts:40 仅提交 orderedFileIds=[source.fileId]。
- JdbcProcessingDiscovery.java:80 固定 materialMode=EMPTY。
- ConfigSchema.java:91、92 仅允许 IMPORT 和空材料清单。
- SavedFilesPanel.vue:42 明说背景材料单独保存、生成纪要时再核对。
- 当前 SpeechPipeline 中 ENHANCEMENT 主要为文本标点处理，CORRECTION 为独立纠错；不能等同于材料快照驱动的重增强。

因此“录音期间添加背景材料 → 开始处理时锁定材料 → 正式增强 → 后续材料变更生成新候选并保留人工稿”仍需要业务实现。原型已有该交互，需求第187、202、231行附近亦有材料准备要求。

修复：保留原始转写不可变，再以固定材料快照和来源版本产生增强候选；补就绪条件、失败原因、原请求恢复及应用动作。线上模型接口文档只说明 LLM 对话调用结构，不能据此宣称公司模型已满足材料增强效果；需定义并验证受控提示词、输出契约及真实提供方能力。

### R12 P1 分段文件语义未完全兑现

原型“中断本段”明确生成独立文件，后续各段绑定来源；当前 interrupt() 主要停止设备、补传并暂停服务端，没有提交独立分段归档。页面也改为“已记录录音段”，不再声称已有独立文件。

当前 RecordingArchivePreparation 会正确计算 PartRange，并生成整场 WAV；但公开归档状态和会后转写来源主要是整场文件，尚未将这些分段范围作为完整的前端来源模型提供。

修复方案建议：保留整场 WAV，同时提供可验证的每段文件记录及范围映射；按原型要求生成分段受管文件或可独立读取的受管派生资源。不能把一个 PCM 段仅改名成 .webm，就宣称完成独立文件保存。该项影响存储和转写来源，需要与 T7 分开交付。

### R13 P2 背景材料窗口与入口摘要偏离原型

原型 acrOpenMaterials 在第2737行创建侧边抽屉；当前为1040像素上限的居中 dialog。已有材料数量、自动保存说明、准备状态摘要也没有回填录音工具入口。

修复：在录音场景恢复侧边抽屉及原型标题、底部完成区和数量汇总，复用 MaterialImportSection 的真实操作。不能把“完成”按钮当作已完成文件解析的证明。

### R14 P2 顶栏与主体视觉验收未全面落地

RecordingPage 直接渲染没有 breadcrumb 的 WorkspaceTopbar，后者固定品牌首页入口。与图2导航结构不同。主体大量沿用 prototype.css，不应推断整套尺寸都错。

修复：共享顶栏增加受控的会议上下文变体，录音页使用“我的会议 / 会议详情”。同一状态、同一内容、同一视口对比控制卡、分段、字幕和三个窗口。实际保存/模型语义与原型演示文字的差异单独列明，不能通过遮罩或修改原型消掉。

### R15 P1 结束后会议信息入口可点但内容不加载

RecordingPage.vue:101 给 MeetingInfoEditor 的 eligible 固定传入 !finished。结束后重新打开录音归档页，入口仍可点击，但编辑器不读取草稿，可能出现空抽屉。是否允许编辑会议信息，应由会议权限与发布/治理状态决定，不能由是否停止采集决定。

修复：使用会议编辑资格；加载、只读、失败状态均有明确内容。保留已有会议信息组件及名单确认逻辑，不在录音页复制第二套字段。

### R16 P2 计时显示依赖分片落盘

usePcmRecorder.ts:146 从 journal 中 sampleCount 计算时钟；pcm-pipeline.ts 满32000个样本才写入，正常连续采集以两秒粒度更新。第一片之前显示00:00:00，界面容易让用户怀疑没有录上。

修复：采集时钟从实际已采样数量更新，暂停不计时；本地与服务端保存进度继续独立计算。不可把平滑的显示时钟当作保存确认。

## 4. 为什么已有验收没有拦住这些问题

1. RecordingServiceTest.recoveryManifestKeepsOrderedPartsAndDistinguishesGapsFromPersistedSamples 使用 new RecordingService() 内存实现。它验证热状态的插入顺序，没有执行 recoverable=true 的 State 重建路径。
2. recording-api.test.ts 的 manifest 正例只有一段；usePcmRecorder 的 fixture 已自行按正确顺序构造数组，无法揭露真实后端返回顺序。
3. 字幕控制器测试覆盖 revision 更新、身份变化、403清空，没有覆盖成功显示后出现503应保留历史。
4. recording-page-view.test.ts 以 SSR 文案和 class 断言为主。它能确认控件存在，但不能证明滚动跟随、真实设备授权、样式像素或完整流程。
5. web/tests/visual 当前只有 shell.spec.ts 的首页顶部截图基线。仓库有录音专用工具与历史实测，但没有变成录音多状态、完整交互的常规视觉门禁。
6. 历史验收文件自身明确记录录音18项整图 pass=false，没有宣称零差异。不能把“已做过截图”和“全部符合原型”混成一个结论。
7. 当前还有旧 MediaRecorder 与新 PCM 两套实现/类型。旧实现的 interruption、incomplete、逐段文件及平滑时钟语义，没有全部迁入新页面。

## 5. 本轮验证及其边界

已完成五个缺陷复现：

| 编号 | 验证方式 | 实际结果 |
| --- | --- | --- |
| R01 | JShell 调用现有 RecordingService 编译类，注入只读持久化 Snapshot | 两段顺序由1、2变2、1 |
| R02 | Node 运行真实 usePcmRecorder，注入创建失败 | 创建仅调用1次，retry无法重新创建 |
| R03 | 隔离 Chromium、真实 IndexedDB、注入设备拒绝 | 两次拒绝后有2个零时长段 |
| R04 | 隔离 Chromium、真实 IndexedDB、先后写两份受控记录 | latest选中较早但UUID较大的记录 |
| R07 | Node 运行真实 useLiveSubtitles，注入503 | 字幕清空，游标从1回0 |

最终在正确的运行上下文统一复跑8个现有测试文件，35项全部通过，0失败，进程退出码0。早先直接从根目录运行时有5项 SSR 因 Vite 根目录错误未正确加载；这属于探针执行方式问题，已更正并复跑，不能记成产品回归。35项通过也没有覆盖本轮五个新增复现场景。

可复核的根目录命令：

~~~bash
. .tools/env.sh
node --import ./web/node_modules/tsx/dist/loader.mjs --input-type=module <<'EOF'
process.chdir('/home/tanren/ama/web');
for (const file of [
  'src/features/recording/usePcmRecorder.test.ts',
  'src/features/recording/recording-api.test.ts',
  'src/features/recording/pcm-pipeline.test.ts',
  'tests/unit/live-subtitles-controller.test.ts',
  'tests/unit/live-transcript.test.ts',
  'tests/unit/recording-page-view.test.ts',
  'tests/unit/recording-transcription-controller.test.ts',
  'tests/unit/recording-transcription-panel.test.ts'
]) await import('./web/' + file);
EOF
~~~

浏览器探针使用独立上下文，所有 /api/ 请求均被拦截，没有写业务会议、使用物理麦克风、重启现有服务或执行真实模型调用。本轮未重跑完整 Java/MySQL 集成、真实 CAS、30分钟容量、公司语音效果或像素差异验收；这些属于后续修复的对应验证，不是本轮已通过项。

## 6. 方案取舍与推荐结构

| 方案 | 能解决的范围 | 代价及限制 | 结论 |
| --- | --- | --- | --- |
| 仅调样式与按钮显隐 | 顶栏、间距、窗口外观 | 清单顺序、恢复死锁、字幕历史与材料增强仍存在 | 不足以完成本任务 |
| 保留真实链路，按协议、状态、业务、呈现分层修复 | 同时修核心可靠性和原型差异 | 需要少量契约/本地存储演进及有针对性的全链验证 | 推荐 |
| 整页及录音链路重写 | 可以重新组织页面与实现 | 已验证的 PCM、租约、ACK、归档、幂等都要重新承担风险 | 当前证据不支持此投入 |

后续执行采用第二种结构，依据用户继续实施的明确授权。具体设计调整与实际验证如下。

建议状态拆分：

- 采集：未开始、请求授权、录音中、暂停、设备中断、已结束。
- 保存：本地写入中、待服务端确认、已确认连续范围、恢复中、保存失败。
- 归档：未请求、已受理、生成中、可回听、失败。
- 识别：未启动、识别中、暂时延迟、会后补转写、已结束、权限不可用。
- 操作错误：创建、授权、设备、缓存、网络、租约冲突、清单冲突、封存、归档分别携带可执行恢复动作。

## 7. 实际交付与要求映射

以下记录取代原待执行检查表。实现始终由单 agent 顺序推进，验证工具可并发；没有创建子 agent、分支或工作树。完整用例结果和原始失败均在验收记录中索引。

| 任务 | 对应问题 | 已实现内容 | 主要验证 |
| --- | --- | --- | --- |
| T1 | R01 | 按持久化 part_no 重建有序 manifest，当前段保持末位；保留客户端严格范围校验 | 2/3/16段核心回归、真实 JDBC/HTTP、原两段 WAV 与分段逐字节核验 |
| T2 | R02/R03 | 按初始化/设备/传输等阶段恢复；失败创建先查原会话；重复拒绝复用空段；设备成功后再开新段；16段上限释放设备；页面保留中文错误 | 录音控制器18项、真实浏览器拒绝注入→解除→原生采集；原空段不增加 |
| T3 | R04/R05/R06 | schema1兼容可选元数据；ACK确认范围与原PCM删除同事务；按可信时间恢复；旧多记录明确选择；原因/尾段未知保留至封存；无本地记录只读查原会话 | IndexedDB事务回滚、缺口、重复ACK、旧缓存、两桌面12项；浏览器刷新与仅保存 |
| T4 | R07/R09 | 同范围暂时字幕错误保留历史与游标；失权清除；DEFERRED/DONE/CANCELLED准确文案，临时/稳定状态可见 | 字幕控制器、渲染单测；真实浏览器持久字幕回读 |
| T5 | R08/R16 | 实采样时钟包括不足2秒尾段，暂停不增长；40px跟随阈值，上翻不抢位置，回到最新等待刷新和DOM更新 | 控制器短采样测试；真实采集/暂停截图；两桌面滚动交互 |
| T6 | R10/R15 | 封存前持久保存并处理意图；原来源复制及导入请求恢复；完整业务就绪检查；仅保存撤销尚未提交处理意图；结束后信息抽屉可读 | 丢finish/来源/受理回执测试、真实原归档单一转写任务、结束后两桌面信息抽屉 |
| T7 | R11 | 独立材料增强profile及候选表V064，固定原稿/会议修订/材料快照；读取获准真实材料；审阅差异后显式采用新WORKING；原键恢复，不改RAW/历史 | 主项目真实MySQL+固定harness、材料权限测试、实际DOCX解析、两次丢ACK及原采用恢复、真实HTTP回执回归 |
| T8 | R12/R13/R14 | 原PCM派生独立WAV读取与Range；440px右侧材料抽屉、数量、关闭保护；共享顶栏遵循用户后续统一导航选择 | 分段字节/Range/权限/腐坏源503；原生audio元数据；两桌面实际窗口；严格整图差异保留 |
| T9 | 全部 | 补缺陷回归、真实浏览器链路、独立原字节/RAW/工作稿读回、原型差异裁定、数据库与阶段验收记录 | Web全量642、后端批次898、补充定向17、集成23及最终流7、harness137；最终结果以验收记录为准 |

### 实施时固定的设计取舍

1. **旧录音缓存兼容**：保留 IndexedDB schema1，增加可选时间、确认范围及完整性字段。旧记录不知道的事实保持未知；不为升级重写或丢弃原音频。未额外建立活动指针或强制数据库升版。
2. **冷重进**：新增 `GET /api/v1/meetings/{meetingId}/recordings` 查询原会话。只读打开不创建新录音、不申请写租约、不打开设备；服务端有缺口时要求回原设备恢复。
3. **材料增强为显式审阅流程**：保留冻结 IMPORT/EMPTY 语音导入语义，完整录音先产生独立 RAW；保存人工工作版本后，通过“根据背景材料增强转写”锁定材料快照并生成候选，核对后采用。此实现不宣称“开始处理”已自动生成或自动采用材料增强稿。后续材料变更仍须明确生成候选，不静默覆盖人工稿或历史纪要。
4. **分段来源**：使用真实持久PCM派生、逐块验证SHA的授权WAV资源，支持独立读取和Range；不额外伪造fileId，也不把PCM改名为webm。完整会后转写继续使用原整场归档的受管副本。
5. **导航变更**：R14最初建议恢复“我的会议 / 会议详情”，已被用户后来选择的共享“智会助手 / 页面名”取代，详 [共享顶栏方案](2026-11-03-persistent-workspace-header.md)。保留返回列表动作，不恢复重复顶栏。
6. **测试组织**：复用现有控制器测试及 `recording-material-flow.spec.ts`、`recording-journal-facts.spec.ts`；真实链路在 `tools/dev/recording_*integration.mjs` 与只读readback工具中验证，不按原拟议名称再建重复测试文件。
7. **视觉结论**：两个桌面视口均有同暂停状态/内容/采样时间对照；新增回听区、真实保存/识别语义及共享顶栏产生非零整图差异，不使用遮罩、改原型或提高阈值消除。新材料抽屉关闭按钮与重复标题问题已修复。没有“所有录音状态严格像素全零”的结论。

## 8. 交付与验证边界

本轮是既有 B00—B09、C01—C07、D01—D08 完成基线上的在线录音补充修复，不重新受理已成功的历史任务。最终状态、源码位置、真实请求与结果标识、失败保留、截图及复跑方式见 [在线录音修复验收](../../acceptance/d/online-recording-repair-2026-11-03.md)。

外部联调只使用兄弟项目固定接口；同时修复其增强协议、幂等持久化、有界输入输出和测试。其术语替换规则证明真实材料被传入并影响候选，不能证明企业ASR/LLM效果。浏览器使用显式模拟输入设备；设备拒绝为getUserMedia的NotAllowedError故障注入，解除后由真实AudioWorklet采集。没有物理麦克风或真实用户切换浏览器权限的资格结论。

原C阶段10并发录音/50在线/30分钟容量结果仍是原冻结运行源的历史证据，本轮未重跑该容量资格；本轮新增的有界材料增强也不冒用其容量结论。手机端仍不在范围，生产平台、模型质量及部署专属验证沿用既有F边界。并行会话的共享导航、导入恢复、说话人和读取预算改动保持原样，不归入本轮独占成果；本轮未提交或推送代码。
