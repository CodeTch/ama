# 产品决定与平台缺口台账

日期：2026-10-21。来源：[需求对应与差异 §4](../superpowers/specs/2026-10-21-ama-requirements-matrix.md)、[总体计划 §5](../superpowers/plans/2026-10-21-ama-delivery-plan.md)。本表 `D-*` 指差异编号，任务列 `D-*` 指实施任务编号，两者不可混用。

三类：`adopted`（已采纳，不重新询问已批准范围）、`product-specification`（需产品补充规格）、`platform-evidence`（需平台/运维真实证据）。分类指出首要收口路径；产品规格仍可能需要平台配合，平台项目仍可能需要产品审阅降级行为。已采纳只表示规则已确认，**不表示代码、平台联调或部署已验证**。离线输入无法更改这些来源决定。

已采纳为 D-02/08/09/19。其余条目中的已明确部分继续约束实现；仅表内列出的缺口保持 pending。下列待定字段均为具体问题，不能以空白占位或实现者默认值替代批准的规格。责任方在受影响任务启动/接口冻结/验收前形成可审阅记录，附版本、批准人及受控证据引用。当前待定项均无新批准证据。

| 差异编号 | 分类/状态 | 责任方 | 原差异/缺口 | 已明确基线/执行边界 | 具体待定字段 | 受影响实施任务/最迟节点 |
| --- | --- | --- | --- | --- | --- | --- |
| D-01 | product-specification / pending | 产品 | 转写需求描述右侧纠错建议区，原型最终渲染包含行内纠错 | 保留全部纠错能力，优先复刻最终视觉；布局待 UI 规格确认，不从旧脚本推断生产行为 | `inlineOrSidebar`, `correctionActions`, `sourceNavigation` | C-03；产品 / 转写 UI 规格 |
| D-02 | adopted / 已采纳 | 产品 | 导入页明确不再内嵌信息区，文档仍留旧折叠信息章节 | 新导入入口使用统一信息抽屉，不恢复内嵌信息区 | 无新增产品决定；按已批准规则实现并另行验收 | B-09 C-01 D-02；产品 / 采集 UI 规格 |
| D-03 | product-specification / pending | 产品 | 实际会议日期用于列表，但基础信息枚举未一致纳入日期 | 业务日期未知不填造；确认日期编辑入口 | `optionalDateEntry`, `unknownDateDisplay`, `editPermissions` | D-02；产品 / 信息字段规格 |
| D-04 | product-specification / pending | 产品＋模型 | 多主文件、音视频混合、转写与音频混合的组合规则未定 | 保留多段排序；合法组合及格式/大小/时长逐项确认，不默认任意混合 | `multiplePrimaryFiles`, `audioVideoMix`, `transcriptAudioMix`, `ordering`, `formats`, `sizes`, `durations` | B-06 C-01 C-02；产品＋模型 / 文件接口冻结 |
| D-05 | product-specification / pending | 产品＋安全 | 原文不设部分名称上限，但工程必须有请求/解析保护 | 不新增产品名称字数上限；公开技术资源保护 | `requestBudget`, `parsingBudget`, `uploadBudget`, `capabilityDisplay`, `errorBehavior` | B-03 B-04 B-06 C-01 D-02；产品＋安全 / 上传与输入规格 |
| D-06 | product-specification / pending | 产品 | 自动保存、显式保存及保存指示在不同页面有差别 | 纪要自动保存、离开提醒；确认导出使用已保存版本 | `perEditorSaveTiming`, `saveIndicator`, `conflictRecovery`, `leaveReminder` | B-06 C-03 D-04 D-05 D-06；产品 / 编辑交互规格 |
| D-07 | product-specification / pending | 产品 | 纠错零待处理才能确认；失败后的原文降级确认仍是待补充项 | 默认纠错零待处理才确认 | `degradedConfirmationAllowed`, `explicitApproval`, `auditReason`, `eligibility` | B-06 C-03；产品 / 转写确认规格 |
| D-08 | adopted / 已采纳 | 产品 | 模块三泛称发布后修订，模块四明确发布后只读、禁止再发布/恢复 | 发布成功只读；保留历史；撤回/废止不得恢复或再次发布 | 无新增产品决定；按已批准规则实现并另行验收 | B-08 D-05；产品 / 发布规格 |
| D-09 | adopted / 已采纳 | 产品 | “七类要素”与敏感/背景/关联材料目录同时出现 | 七类主体为概述、讨论、结论、行动、风险、待确认、分歧；其他为辅助目录 | 无新增产品决定；按已批准规则实现并另行验收 | B-07 D-03；产品 / 要素 schema |
| D-10 | product-specification / pending | 产品＋邮件平台 | 邮件只有范围条目，没有收件人/正文/附件规则 | 邮件专项规格；受控链接仅为建议 | `recipientScope`, `externalRecipients`, `senderAuthorization`, `linkBodyAttachment`, `template`, `snapshotVersion`, `permissionRecheck`, `messageKey`, `unknownResultReconciliation`, `retryPolicy` | E-04；产品＋邮件平台 / 邮件规格 |
| D-11 | product-specification / pending | 产品＋安全 | 模块六仅有标题，运营治理缺完整细则 | 治理专项规格 | `roles`, `reviewStates`, `dataPurpose`, `evaluationDataset`, `consent`, `revocation`, `retention`, `configurationApproval`, `publishRollback` | E-01 E-02 E-03；产品＋安全 / 治理子项目 |
| D-12 | platform-evidence / pending | 模型平台 | 模型接口没有已确认的流式协议、字级时间戳、吞吐及在线实名 | 短窗口 HTTP 先验证；定位与身份能力不足显式降级 | `streamingProtocol`, `wordTimestamps`, `throughput`, `onlineIdentity`, `degradedDisplay` | A-03 C-02 C-06 F-04；模型平台 / 实时可行性验收 |
| D-13 | platform-evidence / pending | 产品＋桌面运维 | 后台录音未覆盖所有浏览器、休眠和关闭行为 | 站内跳转保持；休眠关闭不承诺录音 | `browserBaseline`, `backgroundRecording`, `sleepCloseBehavior`, `deviceLimits` | C-05 C-07 F-05；产品＋桌面运维 / 录音浏览器基线 |
| D-14 | platform-evidence / pending | 知识库平台 | 知识库幂等、撤回事件和权限同步接口尚未提供 | 稳定业务键查回、签名事件与对账；缺能力不得伪造成功 | `idempotentLookup`, `signedEvents`, `withdrawal`, `permissionSync`, `reconciliationWindow` | B-08 D-05 F-02 F-05；知识库平台 / 发布联调 |
| D-15 | product-specification / pending | 产品＋安全 | 外部人员、行动项负责人映射和声纹授权规则未完整 | 外部身份与企业账号分开；候选实名人工确认不产生授权 | `externalIdentity`, `responsiblePersonMapping`, `voiceConsent`, `humanConfirmation`, `accessGrantRules` | B-02 D-02 E-02；产品＋安全 / 人员和声纹规格 |
| D-16 | product-specification / pending | 产品＋运维 | 文档格式和 OCR 能力尚未冻结 | 支持矩阵；无 OCR 的扫描件明确不可解析，保留来源定位 | `formats`, `scannedDocuments`, `ocrAvailability`, `sourceLocator`, `failureDisplay` | B-06 D-01 D-06；产品＋运维 / 材料解析规格 |
| D-17 | product-specification / pending | 产品＋安全＋运维 | 历史不得自动清理，但临时数据、备份和删除保留规则不完整 | 历史不自动清理；仅校验无引用中间产物可清理 | `temporaryRetention`, `referenceValidation`, `backupDeletion`, `auditRetention`, `restoreDeletionProtection` | D-07 F-03 F-05；产品＋安全＋运维 / 删除与备份验收 |
| D-18 | product-specification / pending | 产品 | 标题与会议名称在人工修改后的联动规则不明确 | 标题与会议名分开版本化，使用已保存标题发布 | `manualTitleProtection`, `renamePropagation`, `titleVersion`, `conflictPolicy` | D-04；产品 / 纪要编辑规格 |
| D-19 | adopted / 已采纳 | 用户已确认；开发＋运维落实 | 用户已明确新增本地文件系统，与早期仅 S3 要求不同 | local / s3 与 MySQL 来源独立；local 不要求 S3；会话固定后端、历史按原记录路由 | 无新增产品决定；按已批准规则实现并另行验收 | B-04 F-01 F-02 F-03；支持范围已由用户确认；适配器及 HA 细节随本草案评审 |

## 专项收口内容

- 输入组合 D-04/05：分别判断多主文件、音视频混合、转写与音频混合是否允许，排序/合并/来源定位规则，以及格式、大小、时长能力矩阵与技术资源预算。没有批准前不宣称任意输入均可混合，不增加产品名称字数上限。
- 邮件 D-10：明确可选收件人及外部收件范围、发件授权、发送时重新检查权限、受控链接/正文/附件和模板、已保存成果版本、业务消息键、结果未知查回与重试策略。受控链接是建议，尚未批准；INT-07 提供平台能力证据。本任务不发送邮件。
- 治理 D-11/15/17：逐角色定义反馈/声纹审核状态与可执行动作、模型/评测数据用途、声纹知情授权及撤销/保留、评测集与指标、配置审批/发布/回滚、临时文件/备份删除与审计保留。未审核数据不得生效；身份候选不授予访问；历史成果不自动清理。
- 日期 D-03：UTC 存储与 Asia/Shanghai 会议日期过滤已确定；可选日期的编辑入口、未知值展示与权限仍待产品规格。
- 降级确认 D-07：纠错零待处理是当前前置。是否开放失败后原文降级、明确批准及审计原因尚待产品决定，不能静默绕过。
- OCR D-16：格式/扫描件/现有 OCR 服务可用性、来源位置和失败展示需能力矩阵；没有 OCR 时明确不可解析，不假造文字。
- 人工标题 D-18：标题与会议名分开版本化、按已保存标题发布已确定；人工修改后不再自动覆盖只是建议，联动与冲突策略需确认。

每次收口保留原差异编号、具体字段答案、责任方、批准版本/受控引用、受影响任务、实现验证/平台联调/部署验收三类证据。后续专项规格不能把未取得接口改成 verified，也不能重开已采纳范围或修改原始需求/原型。
