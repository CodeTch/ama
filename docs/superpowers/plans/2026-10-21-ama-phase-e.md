# E：运营治理与纪要邮件 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完整交付一期保留的反馈审核、声纹治理、质量评测、系统配置和邮件能力。

**Architecture:** 治理是明确授权的独立流程，不授予管理员全部会议内容权限。邮件通过持久化任务调用企业接口，有效版本及收件权限在执行时重新检查。

**Tech Stack:** 总计划技术栈、现有 embed/LLM 服务、企业邮件协议；不新增生产口令系统或独立向量数据库。

**Spec:** [架构](../specs/2026-10-21-ama-architecture-design.md) 第 10、12、15 节；[需求差异 D-10/D-11/D-15/D-17](../specs/2026-10-21-ama-requirements-matrix.md)；[总计划](2026-10-21-ama-delivery-plan.md)。

## Global Constraints

- 继承总计划；原文模块六只有标题，邮件仅有总表条目，因此本阶段先交付具体补充规格，再按确认内容实现，不把猜测当原需求。
- 未审核反馈不投入全局使用；声纹识别只是候选，不授予会议访问权限。
- 声纹登记授权、用途、撤销、保留及密钥归属由企业规则明确；不得默认永久保存。
- 邮件接入真实测试环境前需 INT-07；本计划编写阶段不发送邮件。

## Review Focus

1. 审核人员没有源会议权限却拿到全文：E-01 `reviewerCannotReadUnrelatedSource`。
2. 员工撤销声纹授权但旧向量继续匹配：E-02 `revokedEmbeddingIsNotSearchable`。
3. 旧模型向量维度与新模型混用：E-02 `modelVersionMismatchIsRejected`。
4. 有失败/超时样本的质量报告只统计成功样本：E-03 `failedSamplesRemainInDenominator`。
5. 邮件发送成功后响应丢失导致重发：E-04 `unknownDeliveryDoesNotTriggerBlindRetry`。

## Task E-01：反馈提交与审核闭环

**Files:** Create `docs/superpowers/specs/2026-10-21-ama-governance-design.md`、`backend/contracts/src/main/java/ama/contract/GovernanceContracts.java`、`backend/core/src/main/java/ama/governance/CorpusFeedbackService.java`、`backend/adapters/src/main/resources/db/migration/V016__corpus_feedback.sql`、`backend/integration-tests/src/test/java/ama/governance/CorpusFeedbackIT.java`、`web/src/features/governance/FeedbackDialog.vue`、`web/src/features/governance/CorpusReviewPage.vue`、`contracts/openapi/governance.yaml`。

**Interfaces:** `POST /meetings/{id}/feedback {sourceRevision,segmentIds,category,comment}`；`POST /feedback/{id}/review {expectedRevision,decision:accept|reject,reason}`。反馈修订关联源版本，审核结果产生新的批准版本；不得直接改原确认稿。

- [ ] 形成治理补充规格：明确可提交/审核范围、审核状态、源内容最小披露、数据用途、撤销/保留、声纹及评测配置角色；由产品/安全确认后继续 E-01…03 的相关实现。未定规则不影响 D/F 的独立任务。
- [ ] 写 `reviewerCannotReadUnrelatedSource`、未审核不能生效、重复审核冲突；`assertEquals(403, sourceRead.status()); assertEquals(0, activeUnreviewedItems);`。
- [ ] 运行 `python3 tools/checks/run.py it CorpusFeedbackIT`，确认权限/版本断言失败。
- [ ] 实现提交、脱敏展示、受限审核、审核意见、撤销及审计；全局使用仅消费有效批准版，不把会议访问权隐含授予治理角色。
- [ ] 重跑 IT 与反馈/审核页面流程，核查缺失源、源被删及重复提交行为，按已确认保留规则处理。
- [ ] 提交 `feat: add authorized corpus feedback and review`。

## Task E-02：声纹登记、审核、匹配和撤销

**Files:** Create `backend/contracts/src/main/java/ama/contract/VoiceprintContracts.java`、`backend/core/src/main/java/ama/governance/VoiceprintService.java`、`backend/adapters/src/main/java/ama/governance/EncryptedEmbeddingStore.java`、`backend/adapters/src/main/resources/db/migration/V017__voiceprints.sql`、`backend/integration-tests/src/test/java/ama/governance/VoiceprintLifecycleIT.java`、`web/src/features/governance/VoiceprintPage.vue`。

**Interfaces:** Consumes E-01 已确认规则、C-02 `embed(WavClip)`；`POST /voiceprints {subjectEnterpriseId,consentReference,sampleFileId}`；`POST /voiceprints/{id}/review`；`POST /voiceprints/{id}/revocation {reason,expectedRevision}`；`VoiceprintService.match(Actor actor,VoiceEmbedding input): List<SpeakerCandidate>`，候选含 subjectId/modelVersion/score，不写 ACL。

- [ ] 写 `revokedEmbeddingIsNotSearchable`、`modelVersionMismatchIsRejected`、无授权登记拒绝及普通用户不能下载向量；`assertEquals(0, revokedMatches.size()); assertEquals(previousAcl, currentAcl);`。
- [ ] 运行 `python3 tools/checks/run.py it VoiceprintLifecycleIT`，确认用途/撤销边界失败。
- [ ] 实现授权记录、采样质量/时长策略、审核、加密向量、可配置有效阈值和撤销清理；阈值必须来自企业样本评测，不能编造准确率或默认把分数当真人结论。
- [ ] 重跑 IT、真实 embed 契约和人员停用场景；清理备份处理按已确认规则登记到 F-03，不向普通接口返回特征向量。
- [ ] 提交 `feat: govern voiceprint consent and candidate matching`。

## Task E-03：质量评测与受控配置发布

**Files:** Create `backend/contracts/src/main/java/ama/contract/EvaluationContracts.java`、`backend/core/src/main/java/ama/governance/EvaluationService.java`、`backend/core/src/main/java/ama/governance/ConfigurationReleaseService.java`、`backend/worker/src/main/java/ama/worker/EvaluationHandler.java`、`backend/adapters/src/main/resources/db/migration/V018__evaluation_config.sql`、`backend/integration-tests/src/test/java/ama/governance/EvaluationReleaseIT.java`、`web/src/features/governance/EvaluationPage.vue`、`web/src/features/governance/SystemConfigurationPage.vue`。

**Interfaces:** `POST /evaluations {datasetVersion,modelConfigVersion,promptVersion}`→runId；`GET /evaluations/{id}`→metrics/errors/sampleCount/versions；`POST /configuration-releases {expectedRevision,candidateVersion,evaluationRunId}`；回滚创建新发布修订，不改写历史配置。允许的配置键白名单由 E-01 规格确定。

- [ ] 写 `failedSamplesRemainInDenominator`、评测绑定版本、未授权配置发布拒绝、回滚不改变在途任务输入；`assertEquals(totalSamples, report.sampleCount()); assertEquals(originalConfigVersion, runningTask.configVersion());`。
- [ ] 运行 `python3 tools/checks/run.py it EvaluationReleaseIT`，确认统计/发布边界失败。
- [ ] 实现可复现数据集版本、识别/说话人/纪要质量与人工评审记录、配置审查及新修订回滚；不允许页面输入任意模型 URL 或执行命令，受控地址与凭证引用由运维管理。
- [ ] 重跑 IT；真实质量指标按已批准数据集/标准验收，结果含失败样本和模型/提示词版本；评测负载不得挤占实时保留配额。
- [ ] 提交 `feat: evaluate quality and version configuration releases`。

## Task E-04：纪要邮件与发送结果管理

**Files:** Create `docs/superpowers/specs/2026-10-21-ama-mail-design.md`、`backend/contracts/src/main/java/ama/contract/MailContracts.java`、`backend/core/src/main/java/ama/mail/MailService.java`、`backend/core/src/main/java/ama/mail/MailPort.java`、`backend/adapters/src/main/java/ama/mail/CompanyMailAdapter.java`、`backend/worker/src/main/java/ama/worker/MailHandler.java`、`backend/adapters/src/main/resources/db/migration/V019__mail_jobs.sql`、`backend/integration-tests/src/test/java/ama/mail/MailDeliveryIT.java`、`web/src/features/mail/SendMinutesDialog.vue`。

**Interfaces:** `POST /meetings/{id}/mail {sourceRevision,recipientEnterpriseIds}`→mailJobId；`MailPort.send(MailRequest): MailOutcome`、`findByBusinessKey(String): MailOutcome`，结果 SENT/FAILED/UNKNOWN/UNSUPPORTED_QUERY。实际正文/附件策略、收件范围、授权与通知文案先写入补充规格；优先受控链接为待确认建议。

- [ ] 用 INT-07 和产品规则形成具体邮件规格并确认；不把任意外部邮箱、附件发送和自动重发作为默认能力。
- [ ] 写 `unknownDeliveryDoesNotTriggerBlindRetry`、收件授权、撤回后发送拒绝和稳定幂等键；`assertEquals(1, sendAttempts); assertEquals("UNKNOWN", unresolvedState);`；无可靠查询/去重能力时未知态不得盲目自动重发。
- [ ] 运行 `python3 tools/checks/run.py it MailDeliveryIT`，确认副作用保护失败，使用本地邮件接收器而非真实员工地址。
- [ ] 实现版本绑定、执行前权限/有效性重验、结果查询/对账、失败提示与审计；链接仍经过正式访问鉴权，邮件不包含 S3 长期直链。
- [ ] 重跑 IT；公司授权测试名单/环境到位后另做契约验收，原文保留的邮件功能必须有真实接口证据后才标联调通过。
- [ ] 提交 `feat: send authorized minutes mail with delivery tracking`。

## 阶段验收与移交

- [ ] 治理/邮件补充规格的决定有记录，审批范围与具体公司接口匹配，不把总架构批准当作缺失业务规则的批准。
- [ ] 反馈审核、声纹撤销、评测/配置回滚、邮件未知态均有后端负向和前端流程测试。
- [ ] `docs/acceptance/e/summary.md` 及治理页面原型对比完成；所有一期 P1 项目都在实现/联调/验收台账中有明确状态。
