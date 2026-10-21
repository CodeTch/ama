# B07 generation contract preparation (read-only design prerequisite)

Read this first; it is the requirements. Prepare the concrete internal model-output/provenance/fingerprint contract needed by B07 and later source/confirmation work. This is a bounded design artifact, not implementation completion. No code/SQL/schema changes, no services/tests/company requests, no child agents. Use current reviewed root d71a05b and original sources; output only new SDD proposal and short report. Do not repeat generic C–F preflight, already in later-stage-preflight.md.

Read original designDocs/智能会议助手一期需求说明文档.md sections on transcript/minutes/source/final confirmation, relevant 线上模型服务接口文档.md contents (state what LLM is actually specified), docs/superpowers/specs/2026-10-21-ama-database-design.md §§6 onward and relevant B00-1.2 dictionary/DDL, product-supplement.md, architecture design and original B07 below. Original prototype can clarify seven names/grouping, but business rules override placeholder JavaScript. No modification of originals.

Produce implementation-ready proposal <=220 lines at task-B-07-generation-contract-proposal.md: (1) exact seven semantic element kinds and separate sensitive/background directories, no discarded kind from simplified visual grouping; (2) proposed versioned strict JSON shape and validation algorithm with duplicate/unknown keys, invalid scalar/Unicode/number/ID, size-budget, source ID/locator union, cross-input/version/bounds checks and sanitised failures; distinguish deterministic structural consistency from unsupported semantic/quality claims; (3) authoritative immutable input manifest and unambiguous fingerprint encoding including complete confirmation marks/hash, source file versions/SHA, explicit EMPTY materials/template/exclusion, config versions, scope/base revisions; no latest/config secret/content copies; (4) IDs and evidence links compatible with B00 tables without extra columns, source provenance and evidence sufficiency classifications, background must not independently support meeting decisions/actions; no invented confidence; (5) candidate vs first effective saved result/adoption and manual title/body protection, stale version/epoch, final constraints; (6) exact feasible independent implementation split with proposed public/internal interfaces/owned paths, meaningful tests and unresolved production platform prerequisites. No broad implementation of task/ACL/file/SQL, and do not hardcode test categories or call JSON schema proof of true source support. Clearly label proposed technical choices, conflicts, and owner root needs to rule on before implementation; do not transmute pending company protocols/formats into adopted facts.

Pure TXT B06 parser profile exists unwired; V011 preparation is not applied/adopted. B05 observe fix is under review; do not consume its implementation. B02 MeetingAccess runtime/B03 domain/B04 registry pending. This design may describe those dependencies without treating them complete. Preserve original approved product supplement: title manually protected with explicit relink only; minutes auto+explicit save; controlled mail separately; governance conservative workflow. Unknown action owner external mapping stays prerequisite, no ACL from owner/speaker selection.

Report path task-B-07-generation-contract-report.md; exact inputs/version/SHA, decisions needing review, no tests executed. Return status+paths+concerns only.

## Original task
## Task B-07：基于确认稿生成首版纪要

**Files:** Create `backend/contracts/src/main/java/ama/contract/MinutesContracts.java`、`backend/core/src/main/java/ama/minutes/MinutesService.java`、`backend/core/src/main/java/ama/model/MinutesModelPort.java`、`backend/adapters/src/main/java/ama/model/CompanyLlmAdapter.java`、`backend/worker/src/main/java/ama/worker/MinutesJobHandler.java`、`backend/adapters/src/main/resources/db/migration/V007__minutes_evidence.sql`、`contracts/openapi/minutes.yaml`、`backend/integration-tests/src/test/java/ama/minutes/MinutesGenerationIT.java`。

**Interfaces:** `MinutesModelPort.generate(GenerationInput): GenerationOutput`；Input 含确认稿/材料/模板/排除/模型配置版本和 sourceIds，Output 含七类 elements、body、title、sourceRefs。`POST /meetings/{id}/minutes-generations {transcriptConfirmationId,expectedRevision}`→taskId；结果为 candidateId/inputFingerprint；`POST /minutes-candidates/{id}/adoption {expectedRevision}` 才更新正文。

- [ ] 写未确认稿拒绝、坏 schema/越界引用拒绝、编辑后旧候选不覆盖；`assertEquals(409, adoption.status()); assertEquals("人工稿", currentBody);`；模拟含数字的源文，生成不能引用不存在 sourceId。
- [ ] 运行 `python3 tools/checks/run.py it MinutesGenerationIT`，确认依据/版本约束失败。
- [ ] 实现受控 LLM 适配、schema/引用校验、输入指纹、候选与有效稿分离、错误分类；基本生成可用但完整敏感/局部编辑在 D-03/04 补齐，不提前标全功能。
- [ ] 重跑 IT；真实测试模型到位后执行 `python3 tools/checks/run.py contract MinutesGenerationIT`，保留输入版本与脱敏结果评审，模拟质量不等于真实质量。
- [ ] 提交 `feat: generate traceable minutes candidates from confirmed transcripts`。

