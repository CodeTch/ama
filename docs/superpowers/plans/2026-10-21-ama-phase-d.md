> **2026-10-28 当前范围（AMA-DEMO-FIRST-20261028 R2）**：按[Demo 优先调整计划](2026-10-28-ama-demo-first-adjustment.md)执行；前期本地 MySQL＋本地文件＋单机，优先原型页面与核心业务联调。已完成/已采纳能力保留，不重做；S3、双 MySQL、双活全部尚未完成的专用实现、配置、测试和依赖归最后 F，前期不新增相关抽象或架构改造。本轮仅调整文档。 D-08 中 Demo 页面的复刻与对照验收拆入 B；D 的其余页面/业务仍按依赖交付，S3 读取/删除/恢复专属部分归 DEP-S3/DEP-MIGRATE。

---

# D：完整业务规则与原型页面 Implementation Plan

> **For agentic workers:** 沿用 AMA-WORKFLOW-20261027 与 Demo 优先 R2 的 executing-plans 内联流程、完整业务批次和集中审查；旧步骤模板不自动触发新代理、重复开发或提交。

**Goal:** 完整交付一期已定义的材料、纪要、来源、确认、发布、回看和个人管理，完成页面视觉与异常交互。

**Architecture:** 扩展 B 的领域服务与确认快照，保持有效稿、待应用设置和待采用候选分离。所有读写及媒体流共享对象级授权。

**Tech Stack:** 总计划技术栈；受限文档解析/导出进程；按最终文件格式基线选解析器并锁定版本。

**Spec:** [架构](../specs/2026-10-21-ama-architecture-design.md) 第 6、9—14 节；[功能矩阵](../specs/2026-10-21-ama-requirements-matrix.md)；[总计划](2026-10-21-ama-delivery-plan.md)。

## Global Constraints

- 继承总计划；材料集合可空；名称和分类在最终确认必填，不阻断前序处理。
- 七类主体为概述、讨论、结论、行动、风险、待确认、分歧；敏感/背景/关联材料独立目录。
- 个人模板主题 1—8 个；未明确行动项截止时不展示、不编造、不因此阻断最终确认。
- 发布后固定快照；撤回/废止普通回看、下载、检索、AI 引用失效；长流权限初始每5秒检查。

## Review Focus

1. 批量知识库引用只有一个失效时整批不新增：D-01 `invalidReferenceMakesBatchAtomicFailure`。
2. 名称含 `%`、`_`、中文长字串和未知日期：D-02 按字面包含匹配且不补造日期。
3. 敏感片段通过派生摘要重新出现：D-03 `excludedEvidenceCannotLeakThroughSummary`。
4. 候选生成完成但未采用就最终确认：D-04/D-05 必须拒绝。
5. 删除和权限失效发生于后台导出/下载：D-06/D-07 禁止迟到结果恢复访问。

## Task D-01：材料解析、知识库引用与应用快照

**Files:** Create `backend/contracts/src/main/java/ama/contract/MaterialContracts.java`、`backend/core/src/main/java/ama/material/MaterialService.java`、`backend/core/src/main/java/ama/knowledge/KnowledgeMaterialPort.java`、`backend/adapters/src/main/java/ama/material/DocumentParser.java`、`backend/worker/src/main/java/ama/worker/MaterialParseHandler.java`、`backend/adapters/src/main/resources/db/migration/V012__materials.sql`、`contracts/openapi/materials.yaml`、`backend/integration-tests/src/test/java/ama/material/MaterialSnapshotIT.java`、`web/src/features/materials/MaterialDialog.vue`、`web/src/features/materials/KnowledgeFilePicker.vue`。

**Interfaces:** `KnowledgeMaterialPort.search(Actor,String query,String cursor): MaterialPage`、`resolve(Actor,String externalId,String version): ExternalMaterial`；`POST /meetings/{id}/materials/references {expectedRevision,items:[{externalId,version}]}`；`POST /meetings/{id}/material-snapshots {expectedRevision}`→snapshotId。解析结果包含 fileVersion/page/paragraph/text，OCR 未配置的扫描件为明确不支持。

- [ ] 写 `invalidReferenceMakesBatchAtomicFailure`、取消弹窗不变、应用新材料只产生候选；`assertEquals(0, addedReferences); assertEquals(oldSnapshotId, effectiveSnapshotId);`；PDF/PPT 页与 Word 段定位有原始样本依据。
- [ ] 运行 `python3 tools/checks/run.py it MaterialSnapshotIT`，确认原子性/版本/来源断言失败。
- [ ] 实现本地上传解析和知识库引用的独立元数据、批量全检、版本锁定、隔离解析、失败重试；按 A-02 冻结格式矩阵实现每种批准格式，不用空文本代表成功。
- [ ] 重跑 IT 与知识库真实契约；测试 ZIP 炸弹/畸形文档/源权限变化/文件重复引用，改材料后旧人工稿保留并有待应用/候选提示。
- [ ] 提交 `feat: add versioned materials and knowledge references`。

## Task D-02：完整会议信息、列表检索与状态路由

**Files:** Create `backend/core/src/main/java/ama/meeting/MeetingQueryService.java`、`backend/adapters/src/main/java/ama/meeting/MeetingSearchProjection.java`、`backend/integration-tests/src/test/java/ama/meeting/MeetingQueryIT.java`、`web/src/features/meeting/MeetingListPage.vue`、`web/tests/e2e/meeting-list.spec.ts`。Modify MeetingContracts、MeetingService、MeetingInfoDrawer、`contracts/openapi/meetings.yaml`。

**Interfaces:** `GET /meetings?scope=owned|participating&q=&stage=&from=&to=&page=&size=`→items/total/stageCounts；每项含 actualMeetingDate(可空)、materialCount、allowedActions、destination。`POST /meetings/{id}/info-suggestions/{suggestionId}/decision {expectedRevision,decision}`。

- [ ] 写字面包含、不区分英文大小写、权限一致分页统计、实际日期 null、同名部门；`assertEquals(expectedVisibleCount, total); assertNull(item.actualMeetingDate());`；未授权项不能因搜索计数泄露。
- [ ] 运行 `python3 tools/checks/run.py it MeetingQueryIT` 与 `python3 tools/checks/run.py e2e meeting-list.spec.ts`，确认语义失败。
- [ ] 实现权限优先查询/搜索投影、状态与动作映射、AI 信息建议采纳/拒绝，人工已改值不自动覆盖；实际日期入口按 A-02 已定规则，不用采集日期替代。
- [ ] 重跑用例，在10万会议/100万片段数据基准检查执行计划；筛选、分页、数量一致，空态和失败可恢复，陈旧页面动作由后端拒绝。
- [ ] 提交 `feat: complete meeting information and scoped search`。

## Task D-03：要素、行动项、敏感排除和证据

**Files:** Create `backend/core/src/main/java/ama/minutes/ElementService.java`、`backend/core/src/main/java/ama/minutes/EvidenceService.java`、`backend/core/src/main/java/ama/minutes/ExclusionPolicy.java`、`backend/integration-tests/src/test/java/ama/minutes/EvidenceExclusionIT.java`、`web/src/features/minutes/ElementsPanel.vue`、`web/src/features/minutes/EvidenceDrawer.vue`、`web/src/features/minutes/ActionItemDialog.vue`、`web/src/features/minutes/SensitivePanel.vue`。Modify MinutesContracts、MinutesJobHandler、minutes OpenAPI 与必要增量迁移。

**Interfaces:** `PUT /minutes-elements/{id} {expectedRevision,text,ownerEnterpriseId,dueOn}`；`GET /minutes-elements/{id}/evidence`→sourceType/sourceVersion/locator/supportState；`PUT /meetings/{id}/exclusions {expectedRevision,sourceIds}`→exclusionRevision/applied=false。所有设置版本加入 B-07 的 GenerationInput。

- [ ] 写 `excludedEvidenceCannotLeakThroughSummary`、全文和局部排除、人工重新加入冲突、证据失配；`assertFalse(output.body().contains(seededExcludedSecret)); assertNull(action.dueOn());`；真实语义漏检保留人工核对，测试不得宣称全面零漏检。
- [ ] 运行 `python3 tools/checks/run.py it EvidenceExclusionIT`，确认过滤及证据约束失败。
- [ ] 实现七类要素、两类敏感摘录、独立背景/材料目录、来源抽屉和行动项编辑；排除不删除音文/要素，不改变原有 ACL；模型整理不能新增事实。
- [ ] 重跑 IT 与页面测试；无截止可确认，未选负责人不猜测真实人，改负责人不授权；无支持的新表述不得标为证据充分。
- [ ] 提交 `feat: add grounded elements actions and sensitive exclusions`。

## Task D-04：模板、编辑保存、候选和版本历史

**Files:** Create `backend/core/src/main/java/ama/minutes/TemplateService.java`、`backend/core/src/main/java/ama/minutes/CandidateService.java`、`backend/core/src/main/java/ama/minutes/RevisionHistoryService.java`、`backend/adapters/src/main/resources/db/migration/V013__minutes_templates_history.sql`、`backend/integration-tests/src/test/java/ama/minutes/MinutesRevisionIT.java`、`web/src/features/minutes/TemplateDialog.vue`、`web/src/features/minutes/CandidateCompare.vue`、`web/src/features/minutes/MinutesEditor.vue`、`web/tests/e2e/minutes-editing.spec.ts`。

**Interfaces:** `POST /templates {name,themes}`；`PUT /meetings/{id}/minutes {expectedRevision,title,body}`；`POST /meetings/{id}/minutes-generations {scope:full|element,elementId?,inputFingerprint,expectedRevision}`；`POST /meetings/{id}/minutes-restorations {sourceRevision,expectedRevision}`→newRevision。复用 B-07 candidate adoption，不新建第二套采用流程。

- [ ] 写主题0/9拒绝、1/8接受、个人模板越权、保存失败不丢、候选指纹冲突、历史恢复新版本；`assertEquals(409, staleAdoption.status()); assertTrue(restoredRevision > currentRevision);`；生成成功未采用仍有候选阻断。
- [ ] 运行 `python3 tools/checks/run.py it MinutesRevisionIT` 与 `python3 tools/checks/run.py e2e minutes-editing.spec.ts`，确认生命周期失败。
- [ ] 实现内置/个人模板、自动保存与离开提醒、复制当前编辑内容、局部/全文候选比较、历史恢复；选模板/排除仅待应用，修改正文不反写要素；人工标题联动按 A-02 决定。
- [ ] 重跑用例；并发编辑和迟到任务保留人工稿，切 Tab/刷新恢复草稿；最终确认/下载取已保存版本，复制取当前编辑值。
- [ ] 提交 `feat: complete minutes editing templates and candidate history`。

## Task D-05：完整最终检查、知识库治理与失效传播

**Files:** Create `backend/core/src/main/java/ama/publication/FinalChecklist.java`、`backend/core/src/main/java/ama/publication/PublicationGovernanceService.java`、`backend/core/src/main/java/ama/knowledge/KnowledgeAuthorityPort.java`、`backend/api/src/main/java/ama/api/KnowledgeEventController.java`、`backend/worker/src/main/java/ama/worker/PublicationReconcileHandler.java`、`backend/integration-tests/src/test/java/ama/publication/PublicationGovernanceIT.java`、`web/src/features/publication/FinalChecklistPanel.vue`、`web/src/features/publication/GovernanceHistory.vue`。Modify B-08 确认/发布服务与页面。

**Interfaces:** `KnowledgeAuthorityPort.getState(String externalDocumentId): AuthorityState`、`withdraw(String externalDocumentId,String businessKey,String reason): PublishOutcome`、`obsolete(String externalDocumentId,String businessKey,String reason): PublishOutcome`；AuthorityState 为 ACTIVE/WITHDRAWN/OBSOLETE/UNKNOWN。`POST /publications/{id}/withdrawal|obsolescence {reason,expectedRevision}`；回调按外部签名协议验签/去重/重放防护。

- [ ] 写全部未应用/未采用/未保存阻断，撤回先拒本地读，外部未知保持拒绝；`assertEquals(409, candidatePendingConfirmation.status()); assertEquals(403, withdrawnRead.status());`；回调重复和乱序不能复活旧状态。
- [ ] 运行 `python3 tools/checks/run.py it PublicationGovernanceIT`，确认规则失败。
- [ ] 扩展最终快照绑定转写/材料/信息/名单/模板/排除/正文/来源全部版本；发布预览和执行同快照。实现失效同步、定期对账、未知权威状态拒绝受保护读取，发布后无再发布/恢复入口。
- [ ] 重跑 IT、真实知识库契约及回调丢失后的对账测试；覆盖公司目录/权限在预览后改变、外部成功响应丢失、管理员无普通内容特权。
- [ ] 提交 `feat: enforce publication validity and governance propagation`。

## Task D-06：回看、来源读取与三格式导出

**Files:** Create `backend/core/src/main/java/ama/export/ExportService.java`、`backend/worker/src/main/java/ama/worker/ExportHandler.java`、`backend/api/src/main/java/ama/api/AuthorizedMediaController.java`、`backend/adapters/src/main/resources/db/migration/V014__exports.sql`、`backend/integration-tests/src/test/java/ama/export/ExportAccessIT.java`、`web/src/features/review/MeetingReviewPage.vue`、`web/tests/e2e/review-export.spec.ts`。

**Interfaces:** `POST /meetings/{id}/exports {format:md|pdf|docx,sourceRevision}`→exportJobId；`GET /exports/{id}/download`、`GET /files/{id}/content` 支持鉴权/Range；`GET /meetings/{id}/review` 返回当前用户有权查看的有效或固定发布快照。

- [ ] 写发布前最新已保存、发布后固定版本、未采用候选不可见、撤权中的流停止；`assertEquals(publishedRevision, exportedRevision); assertTrue(revocationDelayMs <= 5000);`；编辑页只开放原文要求的 Markdown 下载。
- [ ] 运行 `python3 tools/checks/run.py it ExportAccessIT` 与 `python3 tools/checks/run.py e2e review-export.spec.ts`，确认版本/授权失败。
- [ ] 实现受控代理、流式 Range、每5秒权限检查和失效终止；PDF/Word 转换在隔离进程，禁止任意网络/本地文件读取，沿用现有统一业务 fileId，本阶段验 local；未完 S3 读取/导出适配与差异验证归 DEP-S3/F-05。
- [ ] 重跑测试并渲染导出样本人工核对中文、表格、标题、换页与实际已保存内容；撤回/删除期间的迟到导出不得可下载，已经下载的数据无法远程收回明确记录。
- [ ] 提交 `feat: add authorized review and version-bound exports`。

## Task D-07：未发布会议删除与资源清理

**Files:** Create `backend/core/src/main/java/ama/meeting/MeetingDeletionService.java`、`backend/worker/src/main/java/ama/worker/FileCleanupHandler.java`、`backend/integration-tests/src/test/java/ama/meeting/MeetingDeletionIT.java`、`web/src/features/meeting/DeleteMeetingDialog.vue`。Modify MeetingContracts、TaskQueue 的取消调用与 FileRegistry。

**Interfaces:** `DELETE /meetings/{id}`→202 `{deletionTaskId}`；`GET /deletions/{id}`→pending|failed|complete，加不可访问生命周期/cancelEpoch。cleanup 仅根据受管文件记录及后端 ID 操作，不接受任意根目录路径。

- [ ] 写录音/暂停/发布中/未知发布/曾成功发布拒删，清理中立即不可访问，迟到上传/模型/导出不复活；`assertEquals(409, forbiddenDelete.status()); assertFalse(lateResultCommitted);`；知识库原文件仍存在。
- [ ] 运行 `python3 tools/checks/run.py it MeetingDeletionIT`，确认边界失败。
- [ ] 实现取消→清单枚举→幂等删除→全部在线业务副本清理确认；本阶段处理 local 目录同步，S3 版本/保留策略与跨后端清理未完部分归 DEP-MIGRATE/F-03，任何路径均不得虚报物理清理完成；备份保留/删除按 A-02 已定规则交给 F-03 恢复抑制清单。
- [ ] 对 local 定向运行中断恢复测试；S3 专属及双后端回归归 DEP-S3/DEP-MIGRATE/F：事务失败、删除一半重启、存储临时错误、历史多版本和无业务引用临时产物；保留最小审计不保留要求删除的正文。
- [ ] 提交 `feat: delete unpublished meetings with recoverable cleanup`。

## Task D-08：个人专题与全页面视觉/交互收口

**拆分顺序（R2）：** 当前 Demo 页面按[逐页矩阵](2026-10-28-ama-demo-first-adjustment.md)分入 DEMO-01～04，随对应功能一起复刻和联调，05 汇合：1440×900 原型对照、1024×768 关键布局可用；已有 shell 零像素断言保留。原型尚未索引的纪要/审阅/发布状态须先从原件实际补录参考；信息抽屉等关键形态不能以“功能可点”代替。复用已采纳页面及协议，允许必要的模板/CSS/交互呈现调整。个人专题、非关键差异及全产品三视口/像素收口仍在 D-08，前置部分不重新开发。

**Files:** Create `backend/core/src/main/java/ama/topic/TopicService.java`、`backend/adapters/src/main/resources/db/migration/V015__topics.sql`、`backend/integration-tests/src/test/java/ama/topic/TopicAccessIT.java`、`web/src/features/topic/TopicPanel.vue`、`web/tests/e2e/topics.spec.ts`、`web/tests/visual/business-pages.spec.ts`、`docs/acceptance/d/page-matrix.md`。

**Interfaces:** `POST /topics {name}`、`POST /topics/{id}/meetings {meetingIds}`、`DELETE /topics/{id}`；单层个人专题，仅本人负责的已发布会议可归集，空专题可删。页面矩阵引用 A-05 原型入口索引，不引入新产品导航。

- [ ] 写别人专题直接访问403、未发布会议归集422、非空专题删除409；`assertEquals(403, outsider.status());`；视觉覆盖导入/录音/转写/纪要/确认/发布/回看/专题及各弹窗。
- [ ] 运行 `python3 tools/checks/run.py it TopicAccessIT`、`python3 tools/checks/run.py e2e topics.spec.ts`、`python3 tools/checks/run.py visual business-pages.spec.ts`，确认行为/布局失败。
- [ ] 实现专题与名称去重；按原型补齐空态、加载、错误、权限失效、长名称、窄屏和焦点保持；专题不改变正文版本或授权。
- [ ] 重跑测试；审阅原型对比而非直接更新截图掩盖差异；页面矩阵每个按钮均关联真实接口及成功/失败路径，E 阶段治理页面单列后续验收。
- [ ] 提交 `feat: complete personal topics and business page fidelity`。

## 阶段验收与移交

- [ ] 一期已定义主业务所有页面和规则已覆盖，D-01…D-18 中尚缺规则明确落实到决定台账或 E 阶段任务。
- [ ] local 的上传、回听、来源、导出、删除全链路通过；无隐藏 S3/双 MySQL/双活必需依赖。S3 适配与全链路差异验证归最后 F，原始义务保留。
- [ ] `docs/acceptance/d/summary.md` 包含交互/视觉/权限/异常证据；不能只因截图一致就宣布功能完成。
