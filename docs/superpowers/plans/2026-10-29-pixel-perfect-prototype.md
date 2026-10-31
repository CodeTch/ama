> **2026-10-30 计划覆盖说明：** 本文保留首页像素批次的历史任务；当前 B 后续入口为 [逐页像素、非语音 CRUD 与 CAS 联调计划](2026-10-30-ama-b-followup-pixel-crud-cas.md)。新入口补充所有非语音 CRUD、固定端口 CAS harness 联调和语音延期边界。

# 原型逐页1:1像素复刻实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. 主会话实施现有隔离工作树；并行只读助手只写页面清单，产品保持唯一写入者。

**Goal:** 按原型最终渲染逐页、逐状态1:1像素复刻，并保留全部真实功能/权限/预算要求。

**Architecture:** 沿用Vue原生组件和真实Java/Worker/文件/知识库后端，逐页使用原型DOM结构、最终CSS与SVG图标；原型原件保持不变。通过同浏览器/字体/视口/DPR/可对齐fixture生成reference/actual/diff，不掩盖主体，不伪造业务成功。

**Tech Stack:** 既有Vue3/TypeScript、Chromium/Playwright；Pillow或既有PNG工具作精确图像比较；既有Java21系统不变。

**Spec:** docs/acceptance/b/prototype-parity-2026-10-29.md，原型designDocs/智能会议助手1021版.html（SHA ab21d0b73f4e48bbf701ee8bca5e958940b61f6e6f680d4ced02de0ce4bab867）。用户最新像素要求覆盖历史no-pixel规则，无需重问授权或另行设计。

## Global Constraints

- 2026-10-30 后续工作以新总计划为准：所有非语音可见 CRUD 必须真实落库；录音、音视频、ASR、播放、说话人识别和依赖音频纠错标记 `DEFERRED_VOICE`，只做页面静态/延期状态。
- CAS、会话、材料/导入相关联调使用固定端口 `ama-integration-harness`（HTTP `8792`、SMTP `2525`）；harness 是模拟器，不定义 AMA 业务语义，只有真实请求证明缺口时才同步修复。
- 全页及每个必要覆盖层/状态；1440×900与1024×768，DPR1，同环境字体。
- 默认精确像素差0；不通过mask、裁剪、容差放宽或修改原型消除差异。
- 业务内容通过测试fixture对齐，不修改原型固定设计，不在产品响应中伪造业务结果。
- 原功能补齐范围继续，未实装/禁用/静态未来页面仍OPEN。
- 不提交/合并/推送，不重置脏树，不删除历史证据；保留原权限、CAS、IME、恢复和预算。
- 本树5174/8182/8183；不停止主树5173服务；Maven与启动器串行。

## Review Focus

- 不同字体/图标/日期控件默认样式使页面近似但像素不一致。
- 旧CSS覆盖改变继承/滚动/固定底栏；必须按最终cascade诊断。
- 非空/空/筛选/分组/成员等状态遗漏，弹窗含子弹窗。
- 假数据掩盖真实权限或能力缺口；视觉与功能分别记账。
- 截图稳定性：字体就绪、光标/hover/动画、视口滚动和内容尺寸必须一致。

## Task 1：新标准与完整case矩阵
- [x] 更新权威验收标准与接续状态，撤销no-pixel。
- [ ] 保存最终可达页面/覆盖层case矩阵，所有新像素项初始UNTESTED。

## Task 2：像素基线与会议列表
**Files:** tools/dev/prototype_pixel_parity.mjs，tools/dev/prototype_pixel_diff.py；web/src/App.vue、features/meetings/MeetingList.vue、shared/Icon.vue、shared/styles。
- [ ] 相同环境创建对齐的列表/空/筛选/成员fixture和完整截图，先保存实际RED差异。
- [ ] 由原型最终DOM/CSS定位差异来源，修正布局、文字、图标和控件。
- [ ] 重新比较精确像素并执行相关功能测试；未达到0继续OPEN。

## Task 3：会议信息及导入/处理页面
**Files:** MeetingInfoDrawer.vue、MeetingInfoForm.vue、UploadImportView.vue、SavedFilesPanel.vue。
- [ ] 抽屉/字段/内外人员选择、导入主材料/背景材料、处理状态逐个RED→修正→像素/功能验收。

## Task 4：录音、转写、说话人和纠错
**Files:** RecordingPage.vue、TranscriptForm.vue、TranscriptPlayer.vue、FuturePanels.vue及对应真实能力。
- [ ] 录音各状态、转写/编辑/替换/说话人/候选/反馈覆盖层按矩阵逐项复刻并验证。

## Task 5：纪要、确认、发布与剩余页面
**Files:** MinutesWorkspace.vue、PublicationPanel.vue、模板/局部生成、专题/反馈/会议库对应组件。
- [ ] 两纪要视图、模板、局部生成、候选、确认、选址、成功、已发布、专题/反馈/库页面逐项复刻并验证。

## Task 6：最终整体验收
- [ ] 所有case及视口完整像素比较通过；每个必要功能正常/取消/失败/恢复有真实证据。
- [ ] 相应Web/Java/type/build/API回归、最终源码hash、全范围独立复核；任一OPEN不得宣称整体完成。
