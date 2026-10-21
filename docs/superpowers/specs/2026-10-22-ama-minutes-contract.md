# 纪要纯 S1 技术契约（独立修订评审采用）

2026-10-22。采用范围仅为不可变类型、规范编码/哈希、严格 JSON 结构与来源位置校验。不是 B07 完整业务验收、真实模型质量或生产能力启用。

完整规范由两个逐字节归档输入共同组成，**fix1 优先覆盖旧文档所有冲突内容**：

- [完整初稿](../../../artifacts/phase-b/b07-contract-design/task-B-07-contract-final-proposal.md)，SHA eaae0ee74d2330cb4bcd32525e445e3809c238e139574477306c761742b96f05。其原“待审”标题作为历史原件保留；未修正的 v1 来源投影不可实现为已采用契约。
- [fix1 规范替换](../../../artifacts/phase-b/b07-contract-design/task-B-07-contract-fix1-proposal.md)，SHA e9a5a7cccb49e20640fee790fbcf866257e74befaa997bf89260f384dba08c00。

[初审](../../acceptance/b/minutes-contract-independent-review.md)发现两项 Important；[独立增量复审](../../acceptance/b/minutes-contract-fix-independent-review.md)逐项 ADDRESSED、限定 SPEC/QUALITY PASS，0新 findings。root 已完整阅读初稿、修订、两个报告及两个独立评审，并核对真实列/FK；本记录采用修订后的技术选择，不将历史原件改写成当时已通过。

编码/解析的固定选择：ama.canonical-value.v1 typed binary/domain envelope、严格 Unicode scalar/UTF8 与无归一化、unsigned UTF8 object key 顺序、明确 null/EMPTY/集合/有序数组语义；独立字节/哈希向量及预算/取消检查必须实测。解析使用现有受管 Jackson2 streaming，仅 adapters 可声明 versionless jackson-core；contracts/core 不引入框架。七类主体和敏感/背景目录保留，结构成立只得 PARTIAL/UNSUPPORTED，不推断语义支持或企业人员/行动日期。

来源投影按 fix1 分离唯一 locatorRows 与每条 EVIDENCE/DETECTION/BACKGROUND/EXCLUSION usage，完全双射及按规定键排序。共享位置的不同 input/parser 版本均入哈希。GENERATION_INPUT 使用 ama.generation-input.v2；REVIEW_TARGET/REVIEW_SOURCES 使用 ama.result-review.v2；FINAL_SOURCES 使用 ama.final-sources.v2；codec/ROWSET 保持 v1。不得解释旧 v1 digest 为 v2。

仅接受 fix1 支持矩阵内真实字段链；输入事实完整性和权限真实性由后续 S2 负责，纯 helper 不发明丢失的来源边。普通要素本地材料来源、ACTION 重新绑定到旧 suggestion 输入以外的来源、任一无法恢复上下文的使用项均明确失败，不丢行算部分哈希。复杂材料及一般来源重绑定仍为必须实现的后续功能，[D01 前置设计](../../acceptance/b/material-provenance-design-prerequisite.md)须先补齐持久化来源设计并独立评审。此限制不能作为删除需求或宣称任意 TXT 工作流可最终确认的依据。

转写确认 profile 从 revision.processing_input_id→transcription_input.processing_config_version/config_manifest/manifest_schema_version 恢复；B06 手工/标记/还原继承原处理输入，另行转换需要新不可变输入。最终来源 profile 从 final→minutes_revision.generation_input_id→固定配置依赖恢复；手工纪要/还原继承原输入，缺链不猜 latest。评审按精确目标/完整证据集合/最新 exact scope，部分范围不得自动授予全文核对信用。

实施门：S1 独立作者只实现上述纯类型/codec/catalog/hash/JSON 与有意义测试，不接 DB、文件解析器、模型、网络、Spring bean、Worker、ACL、采用/最终确认流程。所有 supplied-fact 图检查必须有预算、取消和环检测。不得复制不存在的 evidence.input_id、添加 manifest/profile 列或 JSON 附件类型。正式 SQL 单独按照 B00-1.3/V012 审查，不由本记录启用。

两个用户交互选择（首结果自动保存或显式采用、逐项或合并核对）仍 pending，任何推荐项不作默认启用。真实模型协议/质量、配置档案、来源权限、CAS/fence/事务/归档恢复、B08/公司/运维资格仍待后续证明。纯 S1 实现还须 TDD、实际回归及 fresh 独立 SPEC/quality 后才采用代码。
