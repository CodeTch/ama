# 2026-10-23 开发恢复记录

用户本次明确要求使用子代理按接续说明继续开发，已恢复功能开发。协调分支 `codex/phase-b-resume-20261023` 从 main `0676a21311f2b2193f73a5e096f4a5605ee9b41f` 建立；main 阶段验收门禁保留。本记录覆盖本次新增工作，不改写 2026-10-22 暂停与归并证据。

## 已完成的接续优先项

|项目|结果|准确采用/证据|
|---|---|---|
|B04 协调记录|已补齐并经独立审查，SPEC PASS/quality Approve；Q401 仍 UNKNOWN|`ba6306c`；[采用记录](b/file-registry-adoption.md)与[文档复审](b/file-registry-adoption-independent-review.md)|
|D01 设计 R1|所有纳入要素 basis 不论证据数量都是根；域规则统一，限定复审双 PASS，0 新问题|作者 `212c794b497631b13b395c4d1436cbe306208fd4` → 接续集成 `01ab5380f308233e4b7932096c7df2bae07e0d83`；[设计采用](b/material-provenance-design-adoption.md)与[复审](b/material-provenance-design-fix1-independent-review.md)|
|B07 纯 S1 F1/F2/F3|递归确认全验、共享 DAG 最长路径预算、复制前有界不可变字节载体；限定复审双 PASS，0 新问题|作者 `13b8f1d16f85787c12626faa41c7e511403f16f9` → 接续集成 `93b2270fb98482cb0d5470d5f80a2275100abd02`；[组件采用](b/minutes-pure-s1-adoption.md)与[复审](b/minutes-pure-s1-fix1-independent-review.md)|
|B06.1 共享身份准备|完整身份前置、同事务真实能力采用、全部 queue/external 路径及停用身份终局事实保留；独立双 PASS，0 问题|完整作者 `6b48b2f..4dcdc6b` 三提交 → 协调采用 `0a85ef62007e2f8fd2d590002f3592e79f4767f1`；[采用记录](b/task-B-06-seams-adoption.md)、[复审](b/task-B-06-seams-independent-review.md)与[消费者契约](b/task-B-06-seams-integration.md)|

D01 的技术设计已采用，新增三表/两列尚无正式迁移编号或应用；B00-1.3 历史源和全部已应用 SQL 保持原件。纯 S1 的采用不代表完整 B07 生成、材料格式、模型、ACL、UI 或阶段 B 验收。

## 新鲜验证与计数

B07 原代码回归出现 5 个真实 assertion failures，0 errors/skips；最终原 13 套共 112 项通过，即原 97 项完整子集加 15 新项。规范入口 25 项是其中子集。F3 是静态数据流修正和小型行为验证，没有声称分配观察 runtime RED；设置/编译/审计错误均保留。

D01 缩减离线向量 82 项、纯渲染/保全 184 项通过，旧集合规则断言失败仅证明静态成员矛盾，不代表坏 final、哈希碰撞或 SQL 丢失。没有重跑不变 DDL/真实 MySQL cohort。

接续优先项集成源码 `93b2270fb98482cb0d5470d5f80a2275100abd02` 上执行 `python3 tools/checks/run.py unit`，使用公开锁定 Java21 路径、未加载私有夹具环境：26 套 179 项，0 failures/errors/skips，8 模块 SUCCESS。179 包含上述 112，不能累加为独立新用例。输出保留未改动 DeclaredErrorResponse.java 既有 deprecated API 编译提示。该轮没有执行 IT、DB/S3 或真实服务。原始日志、26 份 fresh XML、源码哈希、审查审计和集成字节凭据在[优先项公开归档](../../artifacts/phase-b/resume-2026-10-23/SHA256SUMS)。

[优先项整分支独立审查](development-resume-independent-review-2026-10-23.md)覆盖 `0676a21311f2b2193f73a5e096f4a5605ee9b41f..05326e918be139973944bfad46e59c4fdc252e7b` 的172路径全部组合变更，SPEC/quality双PASS，0新问题，无deferred/parked finding。该历史范围不包含后续 B06；B06 的精确独立范围和采用见下节。

## B06.1 已采用与后续任务

B04/S1 门禁通过后，子代理 `b06_queue_seams` 在新隔离树 `/home/tanren/ama/.worktrees/phase-b-transcript-runtime`、分支 `codex/phase-b-transcript-runtime`，从 BASE `6b48b2f855bbf890c3ccad68325e7fd74d434489` 完成 B06.1。先交技术提案与实际 MySQL RED，协调者 ACK 后实施共享协议，最终作者 HEAD `4dcdc6b5102c7d99b043385349b4a87c98b52e80`；新独立审查与机械审计双 PASS 后，三个提交全部采用至 `0a85ef62007e2f8fd2d590002f3592e79f4767f1`。

修正提供完整身份集合前置、实际 DataSource/事务/发行者/完整 spec/用途绑定、真实已持有能力显式采用和一次完整领域预锁；所有 queue/external 路径接入。停用身份无法取得领域效果、续租或发送权限，经过原权威验证的当前终局事实仍以既有 fenced 路径保留。原三参数完成语义保留，没有 Supplier、WorkerLoop/provider、新 SQL 或生产 B06 策略。

最终验证为 18 个 seam 方法、26 个既有精选方法/28 次调用，共 44 个不同 IT 方法/46 次调用，另有 26 套 179 项 Java units 全通过。三轮最终源码哈希一致；之前 RED/GREEN 和 unit 重复执行不累加。[作者报告](b/task-B-06-seams-report.md)、[独立审查](b/task-B-06-seams-independent-review.md)、[机械审计](b/task-B-06-seams-evidence-audit.md)及[采用凭据](../../artifacts/phase-b/b06-seams-adoption-2026-10-23/SHA256SUMS)公开保留。采用后 1104 路径/模式匹配，1103 个非属性载荷精确一致，完整作者封存再次通过；没有重复执行相同 runtime cohort。

只创建并保留一个独有、有限资源、loopback、initial-zero MySQL 夹具，实际按原历史应用 12 个 SQL through V013。精选 B03 helper 确实调用最新 migrator 并执行零迁移。旧库/容器/S3/PKI 均未操作，无 reset/repair/outOfOrder 或私密 env/Config.Env 导出。原 SQL、计划、1740 个旧 artifact 文件与最初 RED 证据保持不变。

下一交付按[原静态切分](../../artifacts/phase-b/resume-2026-10-23/task-B-06-preflight.md)和[已 ACK 技术提案](b/task-B-06-seams-proposal.md)落实：不可变配置档案索引/恢复、准确 batch 指纹及独立 literal 向量、有界完整事实读取与跨 JVM 恢复。其后才是首次真实 INPUT_BATCH_FILE 引用、导入/Worker 原子输出、保存/编辑/marks/确认和完整 API。任何结构缺口先做中央增量设计登记，不猜版本，不把提案向量当正式实现。

[B06.2 准备书](b/task-B-06-archives-prepared-brief.md)由原实现子代理只读编写，协调者全文核对并修正规范入口名称后公开，状态 PREPARED / 未激活。没有执行测试、访问夹具或实现代码；建议签名、SecureDirectoryStream/目录 fsync/原子发布及有界 JDBC 行为均待实际验证。input_batch 缺 profile/version、原上传绑定及完整 B07 facts 等边界明确留待后续设计，不能用该计划授予业务引用或生产运行资格。下一次须从当前已采用基线另建独有写树，明确新夹具后激活。

只读集成复核另登记 [FUP-B05-CANDIDATE-01](b/task-B-05-reconciliation-candidate-followup.md)：外部对账原 LIMIT 64 单页查询在连续不可获取候选下存在后续候选不可见的公平性缺口。B06.1 没有改动该查询，当前只承诺窗口内 unknown-domain skip，不声称全局公平性。该既有问题无本轮运行时复现，留作独立精准反例/修正及后续外部 worker 激活资格；不改写过去 B05 报告或本轮封存。

## 接续处理决定与代价

- 原会话作者/审查者无法恢复，采用明确标记的接续角色；作者沿用原工作树与精确 BASE，新独立审查保持原问题编号和范围。若上下文存在遗漏，代价是额外复审/返工，旧历史与证据仍完整。
- B07 代码和 D01 设计在分离工作树并行修复，审查及采用分别执行。两者只在集成属性追加处冲突，已保留有序规则并核对所有其他载荷精确一致；若并行假设有误，代价是集成返工。
- 使用从当前 main 建立的接续分支，阶段 B 验收后再处理 main。代价是保留一次后续本地集成步骤，不提前宣告阶段完成。
- 原生 worktree 工具对 WSL UNC 路径报 dubious ownership，未改变 global safe.directory；确认 `.worktrees` 已忽略且目标不存在后，用 WSL Git 建立 B06 新树。代价是使用手工 Git 工作树生命周期，应用没有该新树的 managed attachment。
- 将 EXTERNAL_FACT_OBSERVATION 与普通任务变更分开：停用身份无领域权限，但保留经过验证的可信事实；不支持的策略组合显式非 ACK，claim-only 扫描可跳过未注册域。代价是无法自动处理的组合仍需配置修正/人工结算，不能以再发送请求解决。
- 对停用身份的当前终局事实给予窄范围 owner/epoch/expiry 例外，仍须精确执行绑定及原权威校验；过期 UNKNOWN/MANUAL、ACTIVE 业务授权和旧代矛盾规则不放宽。代价是增加事实与业务权限的协议边界和回归维护；既有 fenced 状态与证据可供人工结算，不新增发送或生产权限。
- 未改的 LIMIT 64 候选公平性缺口独立登记，不扩成本轮未授权的全局调度承诺。代价是后续须额外队列修正/复审；修复前极端候选分布仍可能延迟或饿死后续对账，因此相应运行资格保持未通过。

B04-Q401 继续 UNKNOWN；B01-IMM-01 永久 PARTIAL。企业 SSO/目录/分类、模型配额/协议、知识库/邮件、真实运维隔离/fencing、断电及容量资格仍为前置条件。本次没有真实公司联调、外部发布/邮件、生产或 push。
