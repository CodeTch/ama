# DEMO-05 浏览器与原型对照记录

本记录依 R2 第 4.3 节逐项评估当前 Demo 的结构、操作和可见状态。共享 shell 的既有零像素断言继续保留；本记录不宣称全页零像素或全产品三视口验收。正式 B 的深层事务、预算、锁、竞态及崩溃资格仍按 summary.md 唯一开放义务表推进。

## 原件与取证

原件 `designDocs/智能会议助手1021版.html` 的 SHA-256 为 `ab21d0b73f4e48bbf701ee8bca5e958940b61f6e6f680d4ced02de0ce4bab867`，未修改。列表/信息/导入/转写参考沿用 A 记录。纪要及后续状态由 `tools/dev/capture_demo_prototype.mjs` 从原件可见控件实际进入，等待所有 override 生效，记录八个状态、1440×900 与 1024×768 两个视口。其 metadata 明确标识 referenceOnly，不作为业务成功证据。

原件操作链：长转写会议 → 接受纠错并保存 → 确认转写并生成 → 核心要素/会议纪要 → 重新生成/核对候选/保留当前稿 → 进入会议确认 → 填写必需信息/最终确认 → 选择目的地 → 发布/只读页。实际应用样本来自日常本地入口的正常导入与命令，使用不同真实 ID，不注入原型文本或 SQL 结果。

## 逐页判定

| R2 页面/状态 | 原件参考及实际证据 | 对照结果与差异 |
| --- | --- | --- |
| 列表、创建 | A `original-meetings.png`；actual-demo `list-{1440,1024}.png`；shell E2E 8 例/零像素 2 例 | 顶栏、标题、负责/参与范围、主要入口和表格层级保留；新建与列表导入均走真实创建命令。数量和会议内容来自数据库。尚未实现的产品范围不伪装可用。 |
| 信息与名单 | A `original-info-drawer.png`；actual-demo `info-drawer-*`；roster-boundary/report.json | 右侧抽屉、背景遮罩、标题/字段/名单/操作区及关闭行为成立；可滚动访问全部字段，自动读取真实类别。后端要求将保存与确认名单分开表达，明确解释草稿不授予权限。两页真实 409 保留本地稿。 |
| 导入弹窗/队列 | A `original-import-modal.png`；actual-demo `import-*`；transcript-smoke/report.json | 居中原生弹窗、文件区、顺序/队列及开始处理入口成立；双 TXT 顺序贯穿 RAW，坏第二文件没有部分 RAW。材料/音视频入口以文字明确后续范围，无假成功。 |
| 转写工作台 | A `original-transcript-page.png`；actual-demo `transcript-*`；TXT/browser-safety 日志 | 左侧输入/录音/说话人上下文与右侧段落、标记、保存/确认/历史分区成立。TXT 无音频和说话人时明确缺省，不制造时戳。确认版本与刷新持久化来自真实 API。 |
| 生成、候选、纪要 | prototype-demo `minutes-core-*`、`minutes-body-*`、`candidate-dialog-*`；minutes-smoke `candidate-comparison-*`、`adopted-1440`；actual-demo `minutes-*` | 核心要素左侧分类/右侧内容、正文视图和两列候选对比成立。候选仅呈现所选正文/集合，决定区保持在弹窗视口；保存稿/本地未保存稿的含义分开提示。现有设置/历史与协议恢复控件收在候选与版本视图。合成模型身份明确。 |
| 审阅与最终确认 | prototype-demo `confirm-*`；actual-demo `source-review-*`；publication-smoke `ready-final-*`；minutes-smoke/review-report.json | 实际原始段落与原文件入口、整篇/局部审阅、行动字段确认及未满足原因可访问。确认前信息/名单与纪要两列预览，信息编辑回到同一抽屉。必需审阅属于后端额外保护；PARTIAL 阻止冻结，受控脚本 SUPPORTED 只证明门禁，不代表语义质量。 |
| 发布/只读/正式文档 | prototype-demo `publish-*`、`publish-selected-*`、`published-*`；actual-demo `published-owner-*`、`published-member-*`；publication-smoke/report.json | 目的地选择、冻结版本/发布状态两列、等待/原请求恢复、成功只读和正式文档入口成立。成功观察立即移除信息/导入/生成入口。保留来源、转写和工作区导航以提供既有受权查看；不复制原型缺失的音频播放。owner/member 双 ACL 正反例实际执行。外部文档样式不作为本系统原型比较。 |

以上对照要求操作和主要区块可见、可理解、可访问。实际应用多保留一层转写/纪要/任务导航，以及纪要内候选/审阅/恢复控件；它们承载已采纳的权限和原请求保护，不是新领域实现。原型中的虚构文本/数量、音频、人员头像和模拟消息不复制到日常样本。

尚待 D-08 的外观差异：字体渲染、卡片阴影与细间距、表单行高/密度、长内容带来的滚动长度、全产品第三视口。两视口弹窗边界和决定区可达性仍属本轮门槛，不以后移外观为由豁免。

## 浏览器安全与持久化

`demo05-browser-safety03.log` 记录两页面保存 A=200/B=409、保留 B 稿、读取新版本再应用成功；compositionstart 后无中途 PUT、切换视图保留稿、compositionend 后一次保存并刷新保留中文/Unicode；陈旧生成请求 409 且没有额外 task 或人工正文变化。未确认生成返回 503/MINUTES_UNAVAILABLE 且无任务，不能记成某个特定业务错误已返回。

`demo05-ack-publication01.log` 刻意在实际 202 后丢弃浏览器 ACK，只发生一个 POST，查回入口只 GET，原任务完成。较早失误的脚本仍保留，不算通过。有效 CSRF 的 member/admin 写请求仍被会议权限拒绝；平台管理员仅有 PLATFORM_ADMIN，未因角色而获得会议内容。名单草稿/确认/撤回实际序列为 403→403→200→200→403，刷新后清除成员工作区。

普通六服务及 MySQL 重开保留原会话、业务 JSON 和相同正式文档字节；它不替代在途 crash。发布 preparation 续租缺陷有独立生产修复和真实 Worker 证据，历史首次任务的 56.85 秒领取延迟仍开放。

## 审查与当前门槛

独立 UI/runtime 审查提出进度未更新 P2。新增真实父组件回归先 RED 后 GREEN，证明保存/最终确认读取刷新进度、等待读取时保留同一编辑器及草稿、发布完成第五步、会话失效清除观察。刷新不清空同一 scope 的旧观察或设置页面 loading；拒绝/失效仍清空。进度修复与候选过滤/弹窗决定区的独立复核已关闭重要发现；最终实际截图、246条该切片Web回归/类型/构建/契约、10条共享shell检查及重新执行的真实浏览器安全验证均通过。随后B-R13又以249条Web/535条Java和完整浏览器、真实MySQL边界验证收口，其姓名LONGTEXT P2已独立关闭。维护者依R2逐页矩阵采纳本地受控 **M1-Demo ACCEPTED（2026-10-29）**；该里程碑不代表真实模型质量或正式B。

原型/实际截图与完整日志位于本工作树 `.tools/local-app/{prototype-demo,actual-demo,minutes-smoke,publication-smoke}`；对应源脚本可复现，实际业务脚本会创建/修改日常演示会议，参考脚本不操作业务环境。
