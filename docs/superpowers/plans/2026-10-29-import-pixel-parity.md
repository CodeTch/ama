# 导入会议像素复刻实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. 主会话实施，完成后只进行一次有界只读审查。

**Goal:** 按原型还原“导入会议”弹窗、首页覆盖背景、文件行与底部操作，验证后停下。

**Architecture:** 保留既有 Vue、真实 CREATE 原回执和 uploadQueue/Worker 协议；抽出纯弹窗视图供首页与现有会议入口共用，不复制原型整页进产品。主文件区原生组件沿用原型最终 class、SVG、固定文案；恢复和未知结果仍按真实状态呈现。

**Tech Stack:** Vue 3 / TypeScript / 既有 Playwright 与 Pillow 精确像素比较。

**Spec:** 用户本轮两张对照图；designDocs/智能会议助手1021版.html 最终 aciInputBody/aciInputFooter/aciShowImport/aciMainFiles 与最终 CSS，SHA ab21d0b73f4e48bbf701ee8bca5e958940b61f6e6f680d4ced02de0ce4bab867。

## Global Constraints
- 范围只限导入弹窗和它必需的入口/背景；完成即停，不推进其他页面。
- 沿用现有隔离工作树；不改原型、不提交/合并/推送，不清理脏树或既有证据。
- 真实上传顺序、SHA/字节、幂等、ACK/未知结果恢复、owner 与只读权限不得回退。
- TXT/DOC/DOCX/PDF/PPTX 与既有媒体支持保持；原型列出的其他格式不冒充已实装解析。背景材料/知识库能力缺口不制造成功或把背景文件错当主文件。
- 相同浏览器、字体、业务 fixture、DPR 和视口，保留完整截图/差异图；精确阈值0，不用遮罩或容差隐藏差异。
- 本树 Web5174/API8182/management8183；不动主树服务。Maven 与启动器串行。

## Review Focus
- 首页打开弹窗后的原创建回执是否仍由同一 actor/命令持有，是否错误清理未知请求。
- 持久上传未完成时的 X/Escape/背景关闭与身份失效。
- 底部开始处理必须等真实 ACK，不能把受理写成完成，重试必须复用原请求。
- 单文件/多文件顺序、恢复/失败、重复文件选择、移除限制。
- 1000px 弹窗在小视口独立滚动，页脚始终可见，背景磨砂与标题同步。

## Task 1：基线与固定空态
- [ ] 保存原型真实 DOM/截图、用户原图，添加固定文案和结构 RED。
- [ ] 创建纯 ImportDialogView 与主文件区，按原型尺寸/层级/图标/文案恢复；保留恢复控制在需要时展示。

## Task 2：真实入口与命令绑定
- [ ] 首页仍通过原 CREATE ACK 建立导入 scope，弹窗覆盖首页；现有会议入口复用同一弹窗。
- [ ] 原上传/恢复/排序/导入协议沿用；底部保存、开始处理、关闭走实际状态；新增重要行为先 RED 后 GREEN。

## Task 3：像素与真实交互
- [ ] 对齐业务内容，在1440×900、1536×791、1024×768验证空态与文件状态，保存 reference/actual/diff。
- [ ] 真实上传/顺序/移除/保存与处理、原请求恢复、关闭和键盘交互验证；不伪造业务成功。

## Task 4：审查和停止
- [ ] 一次只读审查，必要修复因果验证，前端完整单测/type/build/API检查与首页关键回归。
- [ ] 固定源码与验收证据，更新当前停止边界；导入弹窗结束即停。
