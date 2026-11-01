# 阶段 D 页面与交互验收矩阵

状态：用户已要求继续，D03完整材料UI与新发布专题/回看入口已通过，阶段 D 仍未验收。`通过`要求真实身份、当前受管业务版本回读、适用失败路径及原型对照都有证据；单测、静态按钮清单、合成模型输出各自只证明其边界。所有外部联调走兄弟项目固定 HTTP 8792 / SMTP 2525，兄弟实现不作为公司权威契约。

原型：`designDocs/智能会议助手1021版.html`，SHA-256 `ab21d0b73f4e48bbf701ee8bca5e958940b61f6e6f680d4ced02de0ce4bab867`。视口为1440×900、1024×768和390×844。保留原型原图、实现图和差异；禁止用当前实现更新参考图来消除差异。

| 页面/窗口 | 操作与接口 | 已有实际证据 | 仍待核验 |
| --- | --- | --- | --- |
| 登录/退出 | CAS登录、ticket回调、session、logout | B AUTH-01；所有D CAS脚本实际登录 | D最终三视口复核 |
| 我的会议 | 查询、筛选、分页、分组、日期范围、打开 | `d02-query/cas-03/report.json` 五组通过；容量另见meeting-query报告 | 最终原型与失败恢复 |
| 会议信息/名单 | info、人员查询、roster confirmations、AI建议采纳/拒绝 | `info-suggestions/cas-05/report.json` 七组通过；roster-review-final授权与撤权 | 最终原型对照 |
| 导入主文件 | 上传、文件重选、取消、批次受理、任务进度 | import/material-ui真实CAS；TXT→RAW用于各D流程 | 三视口统一复核 |
| 材料/知识库选择 | catalogue、batch、retry、remove、受管文件下载 | `material-authority/ui-cas-08/report.json` 五组通过，七个原始定位核验 | 已由material-change-deletion/cas-01验证；剩最终原型对照 |
| 录音/转写 | 录音开始/暂停/继续/结束、上传、RAW/人工版本、保存/确认/纠错 | B/C专用证据；D实际TXT恢复确认 | 语音依赖仍按C未通过处理；D页面/失败边界待复核 |
| 核心要素 | 七类、定位、来源、审阅、行动字段、待确认转换 | 完整生成/候选历史实际CAS | 材料/copy/noDate已由region-generation/material-cas02-02四组通过；REGION实际采用/空区域/过期等已有证据，仍待最终视觉收口 |
| 敏感信息 | 两类摘录、全文/局部排除、保存/取消、来源 | 原有契约/IT和前端组件证据 | 转写cas-01六组、材料v4-api-01六组API和fresh-material-v4-ui-02完整UI八组通过；旧失败保留，剩最终原型差异收口 |
| 模板 | 内置选择、个人新增/改名/主题/删除、越权 | `personal-templates/material-template-cas-03/report.json` 十四组通过 | 最终原型对照 |
| 正文编辑 | 开始编辑、自动/手动保存、恢复原保存、复制、离开提醒 | candidate-history-cas-04及原生编辑证据 | 未保存复制已由material-cas02-02实际验证；剩最终原型对照 |
| 重新生成 | FULL/REGION、输入要求、任务恢复 | FULL实际v2；REGION源码及回归 | REGION已有v3实际任务/采用/空区域/过期/外人/原键证据；v4材料排除API通过，仍待最新UI汇合 |
| 候选/版本 | 读取/比较/采用/拒绝/保留当前稿/处置、历史恢复 | `personal-templates/candidate-history-cas-04/report.json` 五组通过 | REGION实际采用与两份SQL source-equivalence已核验；整例失败报告保持，剩最终UI/视觉汇合 |
| 最终确认 | readiness、逐项核对、确认、返回修改 | personal material/template完整最终确认；候选阻断 | REGION及材料变更候选/未应用设置均已验证阻断；剩最新页面对照 |
| 发布 | 目的地库/目录、预览、受理、原请求恢复、任务状态 | personal material/template及governance实际CAS | 最终原型/按钮复核 |
| 治理/回看 | 撤回、废止、状态恢复、正式文档/成员读取 | `governance/cas-05/report.json` 八组；resume-cas-03五组 | 最终原型对照 |
| 导出 | 编辑页Markdown，回看MD/PDF/Word，任务恢复、full/Range | `exports/cas-deployed-06/report.json` 三格式；genuine-graph-07；治理流中断 | 最终按钮/原型对照 |
| 删除 | DELETE→202、全局进度、原请求恢复、清理终态 | `deletions/cas-01/report.json` 五组；新材料表IT五项 | 已由material-change-deletion/cas-01五组实际验证；剩最终原型/按钮收口 |
| 专题/反馈 | 专题CRUD、归组/移除、非空删除拒绝、外人拒绝；反馈提交 | `b-followup/topic-feedback-final/report.json`、ui-report | Topic*IT22及topic-cas-01实际CAS八组通过，含新鲜真实发布、正向/422/403/409/重放/三视口；剩全页面按钮映射 |

上表短路径都相对 `.tools/local-app/`。源码按钮清单为 `.tools/local-app/d08-page-matrix/source-button-inventory.json`：初次静态扫描51个Vue文件、437个按钮出现位置，包含条件分支/组件重复/演示页面，不能当作437个已验业务操作。后续按可达生产页面逐项归并并绑定真实接口或纯UI作用、成功/失败/禁用原因和截图；尚未绑定的项保持待审。


2026-11-01 本批暂停补记：生产 Java 854 项、完整 Web 481 项、Topic MySQL 22 项、本地运行工具 30 项、兄弟桥接 5 项通过；build/type/API字节一致检查通过。v4 材料排除真实 CAS/API 六组通过，不能覆盖 `material-v4-01` / `material-v4-02` 的浏览器失败。所有未完成的逐页/逐按钮和原型差异项继续开放，不按静态按钮数量签收，不把语音延期或 B/C 失败改成通过。当前准确接续见 `docs/development/phase-d-pause-2026-11-01.md`。


接续验收：`fresh-material-v4-ui-02`完整材料UI八组PASS；`topic-cas-01`专题八组PASS。`published-access-green-01`与`published-member-green-01`对实际已发布会议，分别以负责人和有会议读取权限的参会人验证三视口core/body/媒体区域/三格式导出窗口，均PASS，0业务写请求。见[回看入口修复](published-readback-2026-11-01.md)及[新发布专题](published-topics-2026-11-01.md)。新源码前端486/type/build/API一致通过。

首次published三张截图拍到了加载态，保留为加载证据，不作为内容完成或视觉通过。published-readback-01补已加载截图，随后实际原型对照发现隐藏切换栏/媒体/导出，已RED→GREEN修复；最终以两角色green报告为准。

旧437清单仅扫描features。新的Vue AST审计 `controls-audit-02/controls.json` 覆盖web/src全部57组件、464按钮，其中402生产候选、62演示/未来页面待路线审查；新增App.vue的25处等。**所有未绑定实际证据的行仍为REVIEW_REQUIRED**。这些数字不等于可达按钮数，更不等于成功业务操作数。需继续按生产路由/条件分支合并、绑定成功/失败/禁用原因和原型差异，才能关闭D08。
