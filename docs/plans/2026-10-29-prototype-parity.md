# B 原型还原执行计划

Spec: docs/acceptance/b/prototype-parity-2026-10-29.md

Global constraints: 同一隔离 worktree，保留已有脏改动；不提交、不合并、不推送、不重置；主会话唯一产品写入者；原型文件不修改。用户已授权持续执行。原型已确定设计，直接实现，无需再次设计审批。

Task 1: 严格标准及 RED 基线。保留历史验收，新增列表/主导航/转写可读模式/抽屉/录音页面断言，运行观察失败。
Task 2: 列表及待接入核心页面。还原列表完整结构与筛选入口；真实数据过滤范围明确；录音和媒体导入页可达，明确未接入。
Task 3: 工作流与信息/导入。五环节导航、头部与固定底栏、560抽屉及1000导入弹窗；保留真实保存/名单确认/TXT上传。
Task 4: 转写阅读/编辑。两行工具栏、300左栏、段落局部编辑/标记/搜索，原请求和草稿恢复继续可达。
Task 5: AI纪要与确认/发布/已发布。各自页面结构和操作对齐，真实候选/来源/审阅/冻结/目录/发布保持。
Task 6: 全视口截图与真实交互回归、契约/类型/构建、独立审阅，修复阻塞项并更新验收结论。

Interfaces: Task2 list APIs must retain actor-bound bounded pagination; Task3 active stage is consumed by Task4/5; mounted editors survive stage navigation to preserve drafts; footer actions call existing command state machines rather than duplicate writes.
Review focus: late prototype overrides; missing controls hidden by old 204px test; real permission/CAS/journal behavior after template migration; no fake future media functionality; published route read-only; no client current-page filtering portrayed as global.

2026-10-29 用户范围变更：上列future-UI/仅TXT范围已被取代；后续执行 `docs/superpowers/plans/2026-10-29-full-prototype-completion.md`，原型全部可达功能均须实装，布局/样式/组件/固定文字严格对应，不要求像素级比对。原计划保留为历史执行记录，不能按旧豁免验收。
