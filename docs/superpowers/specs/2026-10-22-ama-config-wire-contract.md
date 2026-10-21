# B06/B07 不可变配置格式契约（技术设计采用）

2026-10-22。root 已完整阅读109行提案、95行作者报告和fresh独立评审。限定 SPEC/技术质量 PASS，0 findings，评审SHA39f99b7a…。本记录只采用纯数据格式、校验和恢复链，不声明代码/数据库/模型/运行时/两个待定交互通过。

规范原件：[完整提案](../../../artifacts/phase-b/config-wire-contract/task-B-06-config-contract-proposal.md) SHA427451ba18bf1a72ce77da7eea87c72a6e4dcf885cb77617ab660b68a94e9832；[作者报告与完整五向量](../../../artifacts/phase-b/config-wire-contract/task-B-06-config-contract-report.md) SHA8ad71c05…；[独立评审](../../acceptance/b/config-wire-contract-independent-review.md)。原始字节保留在artifacts/phase-b/config-wire-contract，包含原待审标题作为历史证据。此采用说明优先决定当前门禁状态。

本补充只对 [纪要契约](2026-10-22-ama-minutes-contract.md) 的配置/档案绑定与新增 TRANSCRIPTION_INPUT 域生效。原 confirmation/generation-v2/review-v2/final-v2 内容投影、使用项来源限制和工作流门禁不变；B00-1.3/旧SQL不变，未新增DB列/表/业务附件或profile kind。profile总数仍15。

关键选择为严格 UTF8 canonical JSON 的完整原始 archive SHA，与既定 typed binary 业务哈希分开。精确 IMPORT/EMPTY manifest、PROCESSING_CONFIG/PARSER_CONFIG、PROMPT_CONFIG/MODEL_CONFIG、固定schema/profile定义和完整闭包依赖必须从原始 bytes 验证；'{}'、test-manifest、caller boolean或自行声明已验证profile不能得到成功binding。旧fixture原件不迁移成有效配置。归档身份为不可变(type,version)，未知schema/定义/缺失历史/矛盾SHA失败，不猜latest。

创建和恢复明确分开：draft compute 不接收也不需要自身尚不存在的stored fingerprint/TaskSpec/output revision；它验证完整raw manifest/archive/draft字段和真实先决batch/file/history事实后生成新digest。持久化后stored verify必须严格比较真实row.input_fingerprint、所有相关任务固定fence及生产者链；不能以null/空串/wildcard/valid=true跳过。共享校验阶段可复用，但成功binding只能由验证器私有构造并保留不可变原件/refs，不能公开伪造。

实施必须在检查每个数组长度和累计预算后复制一次，后续SHA/解析/canonical对比使用同一隔离副本；公共fact构造/API也不能先做不受预算的大复制。token/byte/rows/edges/依赖/深度预算和取消对整个历史图累计，不在节点重置。只允许官方Jackson streaming读取和有限typed writer，不以宽松ObjectMapper树或手写parser取代。

S1单一作者可接入提案列出的纯contracts/readers/ConfigBindings/ProcessingFingerprint，仍不承担存储索引真实性、实际归档发布、DB权限完整性或网络resolver。模板、renderer/model参数只是安全语义配置的明确绑定，不自动授权执行。CATALOG本版本只含确认转写、EMPTY材料；parsed_unit的PAGE/PARAGRAPH不能自动推断PDF/PPT/WORD/TXT格式，D01必须补齐映射及来源设计后才可启用完整材料目录。ASR/增强/多步/非空处理材料必须另审版本化语法，不默认为v1成功变体。

独立评审核对五个raw JSON长度/SHA和引用一致性，只是设计向量验证。S1仍须独立完整processing/generation/review/final域字节向量、实际reader/graph/预算/取消/失败测试及fresh代码审查，不能把原始JSON SHA叫业务fingerprint。batch指纹算法仍须B06在真实写入前定义/测试；实际模型协议质量、renderer、CAS/fence/归档持久性及所有部署前置另验。
