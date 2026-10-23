# B06.2 整项独立复审候选快照

2026-10-23。四项实现均已完成各自独立 SPEC/quality 复审并精确采用；本页是整项独立复审前的候选快照，尚不宣布完整 B06.2 采用。最终结论须见随后完整采用记录。原准备书、激活快照及旧证据均保持历史原件。

前三组件的完整采用与修复审查见[激活快照](task-B-06-archives-activation.md)。重启作者完整范围 `5563f4cebd6f3f8d5c44d67c4e9b54f0a1d83d00..41a41e1d7d345fd3b6d46e0a7bc01757298abb3c` 经新独立双 PASS、P0–P3/deferred/parked 全零，精确采用到 `821daaa05a4c4faffb924ae4c00b72cabdd62347`；512 个路径/模式/载荷精确一致。[独立报告及采用凭据](../../../artifacts/phase-b/b06-archives/coordination/runtime/SHA256SUMS)原件公开。首个审查代理因模型容量未启动，替代代理独立检查全范围；没有跳过审查或沿用未经执行的结果。

在该最终组合源码上，root 未加载任何私有夹具环境，显式设置公开 Java21 工具路径，执行 `python3 tools/checks/run.py unit`：29 套、213 项，0 failures/errors/skips，8 模块 SUCCESS。原始 stdout/exit、29 份 fresh XML 与 source-before-run 哈希保存在[全量 units 证据](../../../artifacts/phase-b/b06-archives/final-units/summary.json)。输出保留原未改 DeclaredErrorResponse.java 的 deprecated API 提示，没有把无测试模块计成测试。179 是旧全集，6 个新 batch 加 28 个新 archive 方法构成当前 213；所有 incidental unit 重跑和专项执行均不重复累加。

root 快照工具按所有新增 `.java` 路径取源，因此原始 summary 的 newSourceFiles=350 包含 19 个实际编译源路径和 331 个历史证据内 Java 副本；它不是新增应用类数。[分类清单](../../../artifacts/phase-b/b06-archives/final-units/source-inventory-classification.json)明确区分，原始 summary/执行脚本均保留。全部路径都与其 Git/运行前 bytes 关联，编译源在运行后未变化；该归档计数说明不需要再执行测试。

processing facts 最终为 12 IT 方法；restart 为 1 JUnit 方法、5 个 GREEN 子 JVM（A/B 及三负例）。实际 RED 为 A 真提交后 B 错用新默认，父断言失败；GREEN 只修 B 的历史 Ref 选择，测试/helper/调用不变。三负例均明确 unavailable、无回退/成功 ACK/已核对 DB 变更，原正例 release/行保留。详见[消费者契约](task-B-06-archives-integration.md)与[Task4 原始报告](task-B-06-archives-report.md)，不把五个子进程计为五个测试。

组合封存重新核对 batch103、index current458、facts current435、restart511 及 component49 项哈希；原 index/facts 源封存仍仅绑定历史 f58/2986957 Git blobs。2840 个受保护旧 artifact/计划/SQL、root attributes、main 和旧 stash 保持原件。IT 只用本次受控 MySQL；root 本轮 units 不访问 DB，未重复运行不变旧 cohort。

本候选只覆盖受控不可变档案、batch v1、processing-only 事实读取和真实入队 metadata 恢复。input_batch profile/version、原 upload/session/purpose/文件引用、未提交批次/held-facts reader、Worker/provider/Supplier/API、完整 B07、HA/断电/容量和企业门禁仍按原计划待办。FUP-B05-CANDIDATE-01 OPEN，B04-Q401 UNKNOWN，B01-IMM-01 永久 PARTIAL，D01 仅设计采用。main 未移动，无 push 或生产激活。
