# 会议确认修复与实操验收

状态：验证进行中，尚未宣告完成。

本轮由一个 agent 在 `/home/tanren/ama` 的 `main` 分支执行，不使用工作树、不切分支、不提交或推送。保留其他并行聊天已有修改。已按用户选择暂停另外两个重复的会议确认实施聊天。本报告只对本轮实际记录的检查作结论。

## 设计依据和落地边界

- 原型：`designDocs/智能会议助手1021版.html`，SHA256 `ab21d0b73f4e48bbf701ee8bca5e958940b61f6e6f680d4ced02de0ce4bab867`。
- 需求：`designDocs/智能会议助手一期需求说明文档.md`，SHA256 `e5b11f49373418e7b4ea9b74071fed035f320612a4783de075980c13223cf783`。
- 已批准实施范围：`docs/superpowers/plans/2026-11-03-meeting-confirmation-readonly-audit-and-repair-proposal.md`，MC01–MC17、T0–T6。
- 既有产品裁决：D-03 日期可选、D-08 发布成功只读、D-21 重要结论和行动的人工核对。
- 沿用原型双栏、字段、人员弹窗、预览、检查区域和固定底栏。检查失败展开十二项，全通过时采用原生折叠展示；成果和来源从现有预览区域进入既有来源与审阅能力。没有另建确认工作台。
- 日期可空是按需求及 D-03 执行的明确差异，不以创建日期替代会议日期。长文本、待确认事项和分歧保持原有性质。

## 修复对应关系

| 编号 | 落地结果 | 主要实现 |
|---|---|---|
| MC01 | 失败项可定位具体来源审阅、行动字段、转写、候选、设置和任务；处理后返回确认重读 | `FinalConfirmationChecks.vue`、`MinutesWorkspace.vue` |
| MC02 | 十二类检查逐项呈现服务器结果、原因和动作；读取失败不能推断为通过 | `final-confirmation-checks.ts` |
| MC03 | 预览内可核对七类成果、背景引用和来源 | `PublicationPanel.vue` |
| MC04 | 已最终确认但未发布仍可编辑信息；保存实质变更使当前 final 失效 | `MeetingInfoEditor.vue`、`PublicationPanel.vue` |
| MC05 | 草稿离开时提供继续编辑、放弃、保存后继续；待恢复请求阻止误离开 | `MeetingDetail.vue`、`MinutesWorkspace.vue`、`MeetingInfoEditor.vue` |
| MC06 | 已知且状态匹配的 409/422 与未知结果区分；未知结果仍重放原键和原体 | `publication-errors.ts`、`client.ts`、`usePublication.ts` |
| MC07 | 日期可空，填写时仍校验合法日期 | `final-information.ts`、`MeetingInfoForm.vue` |
| MC08 | 名称和分类必填错误绑定字段并聚焦，保留其他输入 | `MeetingInfoEditor.vue`、`MeetingInfoForm.vue` |
| MC09 | 分类建议经过候选来源核验、独立模型协议、当前字典及显式采纳/拒绝 | `HttpMeetingCategorySuggestion.java`、`JdbcMinutesInfoSuggestions.java`、V066、兄弟项目 `meeting_category.py` |
| MC10 | 名称/分类/摘要建议保留决定状态；采纳单字段安全合并，不覆盖其他人工草稿 | `useInfoSuggestions.ts`、`useMeetingInfoEditor.ts` |
| MC11 | 空名单明确显示；保存名单与确认授权分开；确认空名单合法 | `MeetingInfoForm.vue`、最终确认协调器 |
| MC12 | 分类显示字典标签；可选审阅显示目标和审阅类型 | `PublicationPanel.vue`、readiness 扩展 |
| MC13 | 最终确认预览统一读取冻结信息、正文、要素和背景，不混入最新草稿 | `FrozenFinalProjection.java`、`PublicationPanel.vue` |
| MC14 | 确认读取中、失败和重试状态明确；相关 GET 有读取上限 | `usePublication.ts`、`client.ts` |
| MC15 | 步骤条、路由和交接按钮使用同一能力计算；有待生成/候选时禁止交接 | `workspace-stages.ts` |
| MC16 | 信息保存→名单确认→重新检查→final→导航统一防重复；前序成功保留真实状态 | `useFinalConfirmation.ts` |
| MC17 | 独立会议通过真实非 demo 页面、真实 API、实际工作线程验证；视觉和功能分别留证 | `tools/dev/meeting_confirmation_*.mjs` |

## 外部接口和启动修复

所有外部集成入口保持兄弟项目固定 HTTP 8792、SMTP 2525。兄弟项目是共同开发的模拟器，不代表真实平台或模型权威实现；分类建议测试只证明协议绑定、字典约束、人工决定和持久化，不能证明真实模型分类准确率。

分类接口使用独立 `ama-meeting-category.v1` profile，核对精确输入 SHA256、候选输入指纹和字典值。请求仅生成建议元数据，不能直接写人工信息或授予名单权限。新增 V066 仅前向扩展建议存储。

实操发现确认初始化时多个组件重复读取完整工作区，造成同账号数据库锁排队。增加经相同权限门禁和外部来源校验的轻量访问续读；并合并同会话同会议正在执行的导航读取，不缓存完成结果。每个订阅者可以独立取消，身份变化不能复用旧响应。4.5 秒内容展示授权时限保持不变，失败仍收回内容。

共享工作区的治理迁移 V072 已执行后被追加 SQL，导致 Flyway 校验失败。通过本地迁移历史校验值 `-1766875882` 确认原文件前 96 行；保留已执行部分，追加的历史运行快照补齐移入 V073。反馈撤回理由的同号草稿已在执行前改为 V074。没有清库、修改 Flyway 历史或执行 repair。

为避免其他聊天重启打断实操，`meeting_confirmation_validate.py` 调用原 `local_app.start` 并一直持有原有生命周期锁直到浏览器测试结束。浏览器仅屏蔽开发热更新通知，业务请求不替换为假成功响应。丢回执与超时属于明确记录的故障注入。

## 测试与证据

工作日志、失败记录和回归输出：`.superpowers/sdd/2026-11-03-meeting-confirmation-readonly-audit-and-repair-proposal/`。

- 独立完整样本：`74012745-4b22-4d77-b156-5fdbc684b205`。实际 UI 导入 TXT、工作线程转写、人工工作版本和确认、真实纪要生成，具有一个结论、两个行动项、待确认事项和分歧。
- 异常恢复样本：`07ead9ac-7bb7-4d40-9a2d-f6851d18234d`。本轮独立创建；不使用用户原有会议作写测试。
- 历史会议仅执行只读离开保护探测，不提交业务写请求。
- 原型截图：`.tools/local-app/meeting-confirmation-audit-20261103/`。
- 实操主报告及截图：`.tools/local-app/meeting-confirmation-e2e-20261103-v7/`。
- 异常恢复报告：`.tools/local-app/meeting-confirmation-recovery/`。

当前已取得的阶段证据：独立样本准备 6 项通过；分类适配、冻结投影、建议 wire、工作区访问门禁后端专项 15 项通过、0 跳过；兄弟项目 Python 146 项通过；OpenAPI 与 123 个响应结构逐字节一致。全量前端阶段运行 700/703，通过后续单独重跑确认当时三个失败文件共 11 项全部通过，最终全量仍需重跑。类型检查阶段已通过。

最终汇总需在主业务矩阵、恢复矩阵、重启后回读和最终构建全部完成后更新。阶段通过不等于最终验收通过；受控模拟器结果不等于真实外部系统上线验证。
