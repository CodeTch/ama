# 导入会议：当前批次暂停与接续（2026-10-29）

**状态：PAUSED BY USER / PARTIAL IMPLEMENTATION / NOT ACCEPTED。** 用户要求“完成当前批次调度之后，就停下，记录接续文档，方便后续通过‘继续任务’指令完成后续开发，实现不重复已完成工作，也不遗漏后续工作和待处理问题”。已经收集本批三个实施 agent 的检查点并关闭，未开启下一批。仅在用户明确“继续任务”后恢复。

本文件覆盖旧首页停止、旧 B 调度以及历史暂停条目的接续指向；历史通过仍只证明当时源码和其明确范围。**当前工作树不是功能已验收版本。**

## 1. 恢复时先定位现场

- 工作树 `/home/tanren/ama/.worktrees/phase-b-completion`，分支 `codex/phase-b-completion-20261023`，HEAD `4b68213dc6b59f9cc850b6acce1a70ed4102235a`。本批没有 commit/merge/push/reset/clean。环境默认 Windows UNC cwd 不可用，每个命令明确使用上述工作树。
- 源码检查点：`.tools/local-app/prototype-parity/20261029-import-pause/source-manifest.json`，**84 个文件的路径/字节数/SHA256**，旁边 `source/` 保留对应源码副本。39 个材料后端文件全部与作者检查点哈希一致。副本只供核对，不可直接覆盖工作树或误删已有改动。
- 当前计划 `docs/superpowers/plans/2026-10-29-import-functional-completion.md`；当前账本 `.superpowers/sdd/2026-10-29-import-pixel-parity/progress.md`；机器状态 `.superpowers/sdd/2026-10-23-ama-phase-b-remaining/current-state.json`。
- 三个子任务均已收束、关闭：Bacon（媒体）、Peirce（材料后端）、Halley（材料前端）。没有仍在后台开发的本批 agent，也没有自动继续任务或新线程。
- 当前自有 Web5174 / API8182 / management8183，DB 容器 `ama-local-dev-5c92c4c3a416`，库 `ama_local_dev`。只读 `python3 tools/dev/local_app.py status` 显示六个服务健康；这是**现存旧 Java 进程**，不证明新接口上线。Vue HMR 可读取本批前端，前后端尚未匹配，新导入流程可能请求尚未加载的接口，不能当作可用演示。
- 不动主目录另一组5173/8082/8083服务。Maven/后端构建/迁移/启动器必须由主会话串行；禁止并行 Maven 或顺手重启。不要输出私有 `.tools/local-app/state.json`、环境凭据、PKI、会话令牌。

## 2. 当前产品范围与规则

先按原型像素复刻“导入会议”；用户随后明确“功能也要实装，不能只完成前端的设计”。所以本轮应完成本地背景上传、真实知识库选择、主文件多格式处理、保存后恢复、放弃导入和原型相关处理功能，不能把禁用按钮/未接入面板算作完成。**范围仍限定导入会议，功能与视觉验收后停下，不自动推进下一页或整套 B。** 最新暂停要求先于继续开发，不能用此前“已授权”抵销暂停。

原型 `designDocs/智能会议助手1021版.html` 最终覆盖为唯一视觉/交互基准，SHA `ab21d0b73f4e48bbf701ee8bca5e958940b61f6e6f680d4ced02de0ce4bab867`，与 `/home/tanren/ama/designDocs/` 原件相同且未修改。使用原 DOM/CSS/SVG/文案，真实 API 驱动，不注入假成功。源码存在大量此前未提交改动，不将整份 diff 算作本批所有。

## 3. 已有证据，勿重做已通过部分

| 结果 | 范围和证据 | 当前适用限制 |
|---|---|---|
| 导入像素 18/18，差异0 | `.tools/local-app/prototype-parity/20261029-import-files-v3/report.json`；空态/单TXT/双TXT，1440×900、1536×791、1024×768，视口+全页，阈值0无mask | **功能扩展前源码**，不是暂停时当前树的新证明 |
| 真实文档 7/7 | `20261029-import-documents-native-v1/report.json`；DOC/DOCX/PDF/PPTX/TXT、五格式顺序、坏PDF无部分RAW | 原 v1 流程证据保留；不要重新开发这5种解析器 |
| 真实交互 4组通过 | `20261029-import-behaviour-v2/report.json`；排序/移除、关闭、延迟真实上传、丢失真实intake ACK的原key/body重试、一个任务、RAW来源顺序 | 新保存/放弃后语义已变，脚本的旧“空态复用CREATE”和“放弃禁用”断言须更新后再用于新验收 |
| 旧完整前端310/310及type/build/API通过 | `.tools/local-app/import-final-*.log` | 功能扩展前，不声称新84文件已通过完整回归 |
| 暂停时当前 TS检查通过 | `.tools/local-app/import-pause-typecheck.log`，exit0 | 只证明vue-tsc，不证明服务功能 |
| 暂停聚焦19项：18通过、1失败 | `.tools/local-app/import-pause-focused-tests.log` | 失败见第7节，尚未修复 |
| 媒体/subtitle 17项通过及真实ASR | `.tools/local-models/fixtures/import-formats-v2/` 的 unit-tests.log、real-asr-report.json、original-asr-report.json、real-subtitle-report.json | runner/解析层证据，尚未接全链路/Java验证 |

原像素差异曾是遮罩/子弹窗层级及两侧 viewport 操作历史不同导致，已因果解决。**保留原生 dialog 外层 mask + 子div.modal 的结构**。截图两侧执行同一打开/选文件/resize顺序，不重建一侧同时保留另一侧；不可放宽容差/改原型/遮罩。首页补充回归 `20261029-import-home-regression-v1` 严格结果25/32零差异，另7组仅8–24边缘像素色值差异，仍如实记FAIL；不冒称当前32/32，不覆盖上一轮首页报告。

## 4. 主会话已经写入的草稿与保护修复

主要文件：`web/src/{App.vue,main.ts,application.ts}`、`shared/navigation-scope.ts`、`shared/api/import-drafts.ts`、`features/import/{ImportDialogView.vue,UploadImportView.vue,UploadImportPanel.vue,useImportDraft.ts,useUploadQueue.ts}`、`features/meetings/MeetingDetail.vue`，以及 `JdbcImportDrafts.java`、`ImportDraftController.java`、`V028__import_draft.sql`。

- 审查P1：afterEach 在导航失败时不再按尝试目标改scope。`navigation-scope.test.ts` 已先RED再GREEN，暂停聚焦复测也通过。
- 审查P2：同一个 MeetingDetail 改 params.id 复用组件时也执行导入关闭守卫。只改query去掉 `?import=1` 不会误拦；尚缺专门真实浏览器历史切换证明。
- 审查P2：保存不能仅关闭弹窗。新增服务端主文件有序草稿与命令回执；前端恢复完成文件时走真实 receipt，`queue.restoreSelection` 只允许已确认状态，禁止覆盖未知上传。对应新队列单测RED→GREEN且暂停复测通过。**服务端持久化/导航/刷新组合仍未联调验证。**
- 首页新导入使用 `/meetings?import=<id>&fresh=1` 作为刷新后可恢复的会议scope，保持首页覆盖弹窗；原 CREATE 原回执仍是新建入口。已有会议复用组件；新导入空态关闭拟走真实discard，有文件拟显示原保存/放弃/继续提示。新增API尚未加载，不能宣称真实关闭已通过。
- `useImportDraft` 保存/放弃采用原key/body持久日志，未知结果阻止离开；保存先ACK再关闭，处理先保存再发原intake。已有会议文件变更开始接自动保存；这些属于尚需专门测试的源码。
- 上传期间点击关闭的临时提示原来完成后不消失，已用真实延迟回执先复现失败再修复，原交互v2证明通过。功能扩展不要丢掉此修复。

草稿API（源码，尚未上线）：

- GET `/api/v1/meetings/{id}/import-draft` → `{meetingId,revisionToken,meetingRevisionToken,orderedFileIds,files:[{fileId,uploadId}]}`。
- PUT 同路径 `{expectedRevisionToken,orderedFileIds}` + Idempotency-Key → 同结构。selection revision独立于meeting revision，原件须属于同会议、完成且可用。当前写保护只接受ENTRY。
- POST `/api/v1/meetings/{id}/import-discard` `{expectedRevisionToken}` + Idempotency-Key → `{meetingId,state:DISCARDED,revisionToken}`；源码验证owner/active/ENTRY/无任务/无OPEN或FINALIZING上传，再推进root+subject fence，保留幂等审计回执。当前是逻辑删除，物理原件清理/保留策略尚需核对，不能说已物理清除。

这些Java类及V028**未编译、未测试、未应用**。前端 `act` 回调类型收尾已修正，暂停时typecheck exit0。

## 5. 媒体批次：代码已有，完成注册后再跑全链路

Bacon实际写入11个文件，已包含在84文件manifest：`MediaTranscriptParser.java`、`local_asr.py`、media-parser-v2.json、media-processing-v2.json、`LocalMediaSetup.java`、`create_media_import_fixtures.py`、Java媒体/档案测试、3个Python测试文件。

AAC/FLAC/OGG/M4V/AVI已真实FFmpeg/PyAV解码并经过离线faster-whisper；SRT/VTT从实际cue抽取文本和时间。原7种媒体也实测ASR通过。SRT/VTT严格UTF8/时间/顺序/结构，预算包含整个原字幕的BOM/CRLF/时间与元数据，保留字面payload，不猜说话人或文本。v1档案未重写：

- `ama.media-documents.v2` parser SHA `31698d653296570e762a5752a57c0c90c25eda71fe14b0709eb3790d735344d0`。
- `ama.import-media-documents.v2` processing SHA `b77fd4ac034d9d43163425d93e28800bd11532ba6de1bfb1701e1b7c37983e5d`。

**接续不要再写解析器，应完成尚未做的注册：** `ConfigSchema`、`ConfigBindings` 的v2 schema/格式/engine，`StrictConfigJson` 接受decoder/subtitles；`TranscriptImportRuntime`接受v1/v2并显式用绑定版本构造parser；`JdbcTranscriptAuthority` step kind、`ImportTaskDomainPolicy` timed-source SQL、`TranscriptSegmentProvenance` 双版本；`ProcessingConfiguration`按绑定版本取formats；前端 `document-formats.ts`、显示错误和accept、OpenAPI/生成类型/严格API格式校验更新19种并保留v1兼容。材料agent也改了ConfigSchema/Bindings/Authority，必须在现有局部改动上合并，不能整文件替换。

一参 `MediaTranscriptParser` 默认现为v2，旧运行时仍未显式传档案版本；**这是优先集成风险，不能直接重建后启动旧配置。** Java新增 `ExtendedMediaImportConfigTest` 等未执行。运行时模型manifest仍是v1 schema，不需要重下模型；LocalMediaSetup默认v2、第四参数v1保留旧安装。先完成注册和编译，再由主会话串行按原安装途径发布新档案并切换，勿改旧档案/hash/index。

## 6. 材料后端与前端：保留半成品，接着集成

后端详细接续：`docs/superpowers/plans/2026-10-29-materials-backend-checkpoint.md`，含39路径逐项SHA、API与风险；作者最终回复另存 `.superpowers/sdd/2026-10-29-import-pixel-parity/backend-materials-batch-checkpoint.md`。

已有但全未编译/测试：独立MaterialParser/DocumentParser、JdbcMaterials、MATERIAL_SOURCE及文件引用策略、知识库真实授权文档/精确SHA版本读取、V017候选、material catalog/snapshot/source context/generation fingerprint、图捕获、renderer、wire、candidate/saved copy等接线，以及3个parser测试。

**“应用材料并重新增强”未实现。** 未有ENHANCE provider/producer/task；原RAW IMPORT仍按原件抽取且manifest EMPTY。材料纪要generation接线也未证明全链路。不能仅把capabilities改成PINNED或关联一个材料就宣称转写增强完成。材料mutation会改meeting business revision；活跃READY/RUNNING/RECONCILING任务时草稿返回409 MATERIAL_PROCESSING_ACTIVE，前端须重新读取状态。

材料前端仅新增4文件：`shared/api/materials.ts`、`features/import/useMaterials.ts`、`tests/unit/{materials-api,use-materials}.test.ts`。最终接口/风险见 `.superpowers/sdd/2026-10-29-import-pixel-parity/frontend-materials-batch-checkpoint.md`。

- useMaterials 已有本地上传/关联/重试/移除原命令和恢复状态，state为 `{busy,blocked,failed,hasFiles}`；知识库模型保留跨库选择和分页cursor。
- **MaterialImportSection.vue、KnowledgeMaterialPicker.vue 两个组件都不存在。** 不要误以为已挂到页面。当前shell已预留materials named slot，实际仍显示旧section/FuturePanels占位。
- 接续用原型DOM创建两个组件，MaterialImportSection props meetingId，emit state，expose reload/ensureReady；在Panel接slot、真正的close/start guard、preview和全局session/journal清理。知识库不可造文档或授权；确认时只从选择器移除真实成功项。

源码两侧目前约定：GET/POST meeting materials；POST materials/{id}/retry；DELETE materials/{id}；GET `/api/v1/material-libraries?meetingId=...&search/cursor/limit`；GET `/api/v1/material-libraries/{libraryId}/documents` 同查询；item字段为 `id,materialRevisionId,parsedDocumentId,fileId,name,source,sourceVersion,sizeBytesToken,sha256,state,error`，列表另有 `materialSnapshotId,materialsPending,meetingRevision`，知识库version是不可变payload SHA。背景purpose为MATERIAL_SOURCE，复用分块/完成/receipt，不能当主文件；预览用既有文件content端点并核对SHA。**本批OpenAPI及generated类型尚未更新。**

## 7. 明确保留的失败与待处理问题

1. 当前聚焦测试唯一失败：`material listing rejects contradictory readiness and preserves opaque numeric tokens`。测试给 `meetingRevision='9223372036854775807'`，既有revisionToken上界为`9223372036854775806`。先核对契约再修测试/材料专用校验，不为过测放宽全局修订token上界。
2. `MaterialCatalog.manifest` 长构造括号、Table枚举新增后的exhaustive switch、材料批量替换类型/方法签名未编译核对。
3. **V017疑似FK ALTER在CREATE之前、重复或命名冲突**；未检查，不可直接冻结/执行。V028也未运行DDL与并发/审计测试。中央register仅登记候选/保留号，未采用。
4. 材料非ACTION/零证据element context root、历史parser/candidate restore/copy-remap/readback/graph closure和精确绑定仍未完成证明；材料列表/知识聚合/cumulative budget也未端到端证明。
5. 新材料archives helper未接本地安装；必须新minutes release，保留原档案及历史index，不覆盖旧版充当已审核。
6. 材料前端reload在首次await前日志读取失败时的reloadFlight清理、确定性拒绝恢复、loading/过期响应、坏日志/存储拒绝、actor/meeting切换、completion回包丢失、remove/retry失败与批次部分成功需覆盖。
7. 新useImportDraft保存/放弃日志在logout/actor改变、ACK后本地清理失败、页面重载的原请求恢复，以及首页query/原CREATE交接/空态真实放弃/已有会议自动保存要真实验证；其journal目前由组件管理，尚需核对全局身份清理接线。
8. 原三项审查发现虽有源码修正/部分单测，路由Back被阻止、同组件切会议ID、保存后离开/刷新恢复真实文件顺序仍未整体验证；不能提前勾掉验收。
9. 19格式的Java注册/档案版本兼容/真实浏览器处理、材料ENHANCE与纪要上下文、API schemas、材料Vue和新正常态/失败态像素都未完成；原18/18不能自动继承。

## 8. “继续任务”后的执行顺序

1. 先读本文件、两个材料检查点、functional plan和84文件manifest；对照当前tree SHA/dirty状态。保留已完成解析器、原上传协议、原像素DOM和历史证据，不重做B、首页或v1文档解析。
2. 修正上面已知TS测试token问题；完成媒体v2注册；收尾既有材料Java/SQL/source context接线。先编译/聚焦测试定位半成品问题，不上来启动会自动迁移的local_app。
3. 审查V017/V028候选与现有库，按中央register记录首次应用SHA并冻结；只在本工作树拥有的数据库顺序验证。任何已应用脚本永不改写，禁止repair/outOfOrder/重编号/清空库。新发现结构修正另登记前向迁移。
4. 完成两个材料Vue、Panel/Application生命周期、OpenAPI/generated、保存/放弃与真实处理；实现未完ENHANCE，不能降格为按钮占位或静默忽略材料。
5. 主会话串行后端build/新档案安装/必要服务启动，再跑原件五格式和新增七格式、材料本地/知识库/版本/预览/失败重试移除、draft save/reload/reenter/discard及原请求/权限/预算/并发/丢回执组合验证。已有测试可复用，只更新被真实功能改变的断言。
6. 当前源码再执行完整前端unit/type/build/check:api、相关Java/Python/真实浏览器；对空态/文件/材料/知识库/关闭提示/小屏等按原型做新的完整像素证据，0阈值无遮罩。
7. 做一次限定于新增功能的独立审查，修正重要问题并重测，更新真正验收与停止边界。只在导入会议功能+像素均满足后停下，不能把本次暂停误记为完成。

工具链由 `tools.dev.local_app.toolchain()` 提供 Java21/Node22.23.4/npm。当前可复现检查：`npm run typecheck`；或在web中以该node执行 `--import tsx --test tests/unit/navigation-scope.test.ts tests/unit/upload-queue.test.ts tests/unit/materials-api.test.ts tests/unit/use-materials.test.ts`，预期目前18/19。不要因已知失败直接写全绿。

## 9. 历史验收定位

`docs/acceptance/b/import-pixel-parity-2026-10-29.md` 是扩展真实功能之前的有限像素阶段记录；`homepage-pixel-parity-2026-10-29.md` 是再前一轮首页证据。两者保留，不覆盖、不重复开发，也不拿来宣布当前功能验收完成。整体原型/B阶段保持NOT ACCEPTED。

## 2026-10-29 接续进展（本次继续任务）

- 已修复材料 API 测试中的越界 revision fixture；材料 API 3/3 通过。
- 已完成媒体 v2 配置 schema/engine 注册并修正运行时按绑定 profile 显式构造媒体解析器，保留 `ama.media-documents.v1` 与 v2 兼容；后端模块构建成功，媒体/材料解析与配置聚焦测试 16/16 通过。
- 已将 `MaterialImportSection` 与 `KnowledgeMaterialPicker` 接入 `ImportDialogView` 的 named slot。材料本地上传走真实哈希分块与 `MATERIAL_SOURCE`，知识库走真实授权分页/版本选择；拖放读取 `DataTransfer.files`。材料组件类型检查通过，导入/材料前端聚焦测试 24/24 通过（后续格式文案更新后的子集 22/22 通过）。
- 主文件前端能力声明已扩展到后端 v2 的 19 种格式：`TXT/DOC/DOCX/PDF/PPTX`、原有音视频及 `AAC/FLAC/OGG/M4V/AVI/SRT/VTT`，并更新选择器、恢复选择器、严格格式类型。
- 复核发现候选 `V017__materials.sql` 中一批外键 ALTER 位于被引用表创建之前，已在工作树候选脚本中调整为所有 CREATE 后再执行 ALTER；该脚本仍未应用、未冻结，待独立 DDL/数据库验证与登记。

当前仍未达到导入会议最终验收：V017/V028 已在自有本地库成功应用，但材料后端尚未完成真实写入联调，保存/放弃草稿的跨刷新浏览器证据、材料失败/重试/移除、ENHANCE 处理上下文及当前源码像素回归仍待完成。不要用本节聚焦通过替代完整验收。


## 2026-10-29 导入弹窗重叠/无法关闭修复

用户反馈点击导入会议后出现重叠弹窗且无法关闭。复核确认两个独立时序问题：

- `UploadImportPanel.closeBlocked` 错把草稿首次读取中的 `draft.loading` 当成不可关闭的未决写操作；接口加载慢时关闭按钮只显示阻断提示。现已只对真实写操作、上传运行、未确认请求和材料操作阻断关闭，初次读取允许关闭。
- 首页创建流程在导入弹窗挂载前仍短暂保留 `MeetingCreatePanel`，造成创建面板与导入弹窗叠放。现已在 `importMeeting` 被设置后立即卸载创建面板，再显示导入弹窗；创建请求状态仍保留用于失败恢复。

聚焦回归 14/14、`vue-tsc` 通过。


### 第二次复现结论

第一次修复后问题仍可复现。使用真实浏览器点击“导入会议”时，Playwright 报告关闭按钮被嵌套的 `material-picker-mask` 拦截；该知识库 `<dialog>` 的 `open` 属性为 false，但自定义全屏样式没有显式隐藏关闭状态。补充 `.material-picker-mask:not([open]) { display:none }` 后，点击关闭按钮使导入弹窗从 1 个打开状态变为 0 个，首页创建面板与提示弹窗也均为 0。

## 2026-10-29 受控 integration harness 联调

按用户要求使用独立 `/home/tanren/ama-integration-harness`。8787 属于其他容器，因此在 harness 自有目录启动隔离实例：HTTP 8792、SMTP 2526、数据目录 `data/live-8792`。live HTTP/SMTP e2e 通过 60/60。

为兼容当前 AMA 材料客户端，在 harness 增加了：

- `GET /v1/knowledge/material-libraries`
- `GET /v1/knowledge/material-libraries/{libraryId}/documents`
- `GET /v1/knowledge/material-documents/{documentId}?libraryId=...&version=...`

接口返回固定 `providerId/storeInstanceId`、分页 cursor、64 位版本 SHA 和 `ama.controlled-knowledge-document.v1` payload。独立材料兼容测试 5/5 通过。

工作树启动器支持 `AMA_INTEGRATION_KNOWLEDGE_ORIGIN=http://127.0.0.1:8792`，当前 AMA API 已实际通过该实例完成材料库和文档读取；浏览器知识库选择器能够显示 harness 的五个知识库及文档。继续关联材料时，应用随后进入真实 `MATERIAL_SOURCE` 上传阶段，但当前本地文件服务返回 `FILE_STORAGE_UNAVAILABLE`，因此材料写入、快照和重试移除仍未完成验收。下一步先排查当前工作树文件服务端点与 API 的 mTLS/配置匹配，再重跑同一浏览器流程，保留 harness 数据和请求日志。
