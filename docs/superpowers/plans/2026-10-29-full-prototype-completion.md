# 原型全页面与全功能补齐实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. 使用现有隔离工作树，由主会话实施；只读功能清点可并行，产品保持唯一写入者。

**Goal:** 完整复刻最终原型所有可达页面、组件、布局、样式、固定文案和功能，取消旧阶段/未来页面豁免；不以像素级比对签收。

**Architecture:** 继续采用现有 Vue/Java/独立 Worker/文件服务/知识库装配；真实权限、CAS、原请求日志、事务、预算与不可变来源保持。通过原型操作驱动真实接口与持久状态；新增能力使用明确版本的解析/处理配置和领域接口，不在旧TXT profile下偷换算法或复制模拟业务状态。

**Tech Stack:** Vue 3、TypeScript、Java 21、Spring、MySQL、现有本地服务。文档解析优先固定版本 Apache PDFBox / Apache POI 及 JDK 流式 XML；依赖版本与API按官方资料核对。录音采用浏览器MediaRecorder，媒体预处理使用已安装FFmpeg，ASR/校正/生成使用真实可配置本地或外部能力；未知结果不制造成功。

**Spec:** `docs/acceptance/b/prototype-parity-2026-10-29.md`；原件 `designDocs/智能会议助手1021版.html`，SHA256 ab21d0b73f4e48bbf701ee8bca5e958940b61f6e6f680d4ced02de0ce4bab867。逐控件目录由 `.superpowers/sdd/2026-10-29-prototype-parity/full-scope-functional-inventory.md` 补充。

## Global Constraints

- 用户最后两条范围修正优先：所有可达功能均补全；TXT只是例子；不擅自设计；不做像素级比对。
- 本地假成功、占位状态、单个格式成功、旧B通过都不能使全功能验收通过。
- 原型原件不修改。沿用脏隔离worktree，不提交/合并/推送/重置/删除历史证据。
- 旧严格TXT profile、不可变存档及历史任务继续可验证；新解析profile并存，禁止覆盖旧档。
- 一个真实请求内保持共享预算；来源原字节hash、顺序、版本和权限可核对；失败不留下部分成功结果。
- 普通用户页面不暴露后台装配步骤；必要错误/撤权/冲突与原操作恢复应可理解。

## Review Focus

- 末尾override及实际可达状态决定要求，不把早期残留死函数算成新设计。
- DOC/DOCX/PDF/PPTX真文件、混合顺序、损坏/加密/压缩膨胀/无文字/取消/限额失败，不只改accept属性。
- 录音设备拒绝、中断/重试、多段与保存；浏览器只在用户操作时申请设备。
- 材料/模板/专题/说话人/纠错/反馈的持久结果、权限与已发布只读，不用disabled页面冒充完成。
- 所有固定文案、布局与组件状态由原型逐项对应；诊断尺寸测试及截图只辅助，功能覆盖必须100%。

## Task 0：收尾并保存已有修复（旧范围，不提升整体结论）

- [x] 重现并修复review F1–F6；保留RED和GREEN日志。
- [x] 完成旧范围严格要求的表头、分页、readiness文案与工具栏样式修正。
- [x] Web265/0、type/build/API；56项布局诊断通过；新真实TXT主链通过。
- [ ] 原候选保留分支真实证据独立落盘；完整适用Java回归及角色复测绑定源码。
- [ ] 敏感分支区分实际组件因果回归与native真实检测场景；不能把CURRENT_DETECTION_SOURCE_REQUIRED/422当成功。

## Task 1：全功能目录与缺口基线

- [ ] 从最终可达页面提取每个入口/字段/按钮精确文字、状态、成功/取消/失败结果与行号。
- [ ] 对应产品组件/API/存储/Provider及测试，所有未完成项显式OPEN；撤销FUTURE-UI通过豁免。
- [ ] 每类功能补RED基线，再实施；不只比截图。

## Task 2：真实多格式文档导入

**Files:** 新建 `backend/adapters/src/main/java/ama/transcript/OfficeTranscriptParser.java` 及解析单测；修改 `ConfigSchema`、`ConfigBindings`、`JdbcTranscriptAuthority`、`TranscriptImportRuntime`、`ProcessingConfiguration`/capabilities；新增文档profile/processing不可变存档，保留旧档；修改 `upload-bytes.ts`、`useUploadQueue.ts`、`UploadImportView.vue` 与OpenAPI/生成facade。

- [ ] RED：真实TXT/DOC/DOCX/PDF/PPTX解析、原hash/稳定段落、混合顺序；当前TXT-only路径必须实际失败。
- [ ] 实现受限流式/有界提取，原字节hash校验，单共享aggregate预算，取消与异常不暴露部分结果。
- [x] 新profile与旧TXT并存；配置/manifest严格相互绑定；同输入重复稳定。
- [x] UI多选、拖拽、排序、原文件重选/恢复遵从原型；类型能力来自真实装配，不仅扩展名白名单。
- [ ] GREEN：解析测试、配置与任务来源回归、实际浏览器各格式上传→Worker→RAW→工作稿/确认/纪要，异常与重试。

## Task 3：背景材料与知识库材料选择

- [ ] 从原型字段与状态补全上传、列表、移除/重试/选择行为，加入真实材料解析任务、来源定位与生成输入快照。
- [ ] 测试本地PDF/PPTX/DOCX与真实知识库对象/版本，失败/撤权不加入可用快照，发布绑定材料可回读。

## Task 4：录音与音视频处理

- [ ] 按原型实现真实录制、暂停/继续/结束、分段保存/失败重试、累计时长、刷新恢复和回听。
- [ ] 实现媒体校验/预处理/ASR任务、真实说话人和时间定位，复用文件来源与任务恢复；配置缺失真实报未完成。
- [ ] 设备拒绝、取消、中断/重登、未知上传结果与已发布只读验证；自动化使用明确隔离设备fixture，不申请用户真实麦克风。

## Task 5：转写增强、说话人和反馈

- [ ] 逐项完成原型行内候选、接受/拒绝/批量、替换、说话人重命名/绑定、上下文反馈与持久状态。
- [ ] 保留人工稿、确认版本、IME/CAS/dirty与原请求恢复；纠错结果须由实际配置能力产生。

## Task 6：专题、归组、删除与会议知识库

- [ ] 原型专题新建/编辑/搜索/列表分组/单项批量归组、删除确认有真实持久接口与权限。
- [ ] 会议知识库各标签/条目/文档导航使用真实可授权对象，空/失败/只读状态遵从原型。

## Task 7：个人模板与局部生成、反馈页面

- [ ] 个人模板名字和1–8段内容规则、保存并使用、本人可见、重生成要求真实实现。
- [ ] 局部重新生成、选择、采用/保留与当前body/set/来源契约一致；不覆盖未保存人工稿。
- [ ] 反馈全部/待审核/通过/驳回/补充与详情/上下文/后续操作有真实状态和权限；不能伪造审核通过。

## Task 8：全功能逐项验收与最终绑定

- [ ] 对照截图与DOM/计算样式核对所有页面、弹窗、抽屉、滚动和固定动作，固定文案逐字核对；不运行整页像素差作为签收门。
- [ ] 每个必要控件的正常、取消、失败、重试、只读均有证据；未知请求只查原操作；成员/非成员/已发布状态回归。
- [ ] 最终Web/Java/契约/构建及新增能力相关native测试，源文件hash与证据对应。
- [ ] 全范围独立复核（这是新范围的审阅，不把旧范围冻结review当新通过）；未关闭任何OPEN不得写整体通过。
