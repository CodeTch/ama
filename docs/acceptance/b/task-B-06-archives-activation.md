# B06.2 激活及前三组件采用快照

2026-10-23，用户明确要求“使用子代理，按 B06.2 准备书继续开发，完成实现、测试和独立复审后更新接续说明”。本页记录激活与组件采用当时的事实；最终状态须以另行发布的 B06.2 完整采用记录为准。本快照时 Task4 刚激活，尚未完成 B06.2。

原[准备书](task-B-06-archives-prepared-brief.md)与其 PREPARED 标记是历史原件，字节不改。本次明确激活 BASE `ad9960595ab5de6eef7760e2c9094d23666074cb`，协调分支 `codex/phase-b-resume-20261023`；main `0676a21311f2b2193f73a5e096f4a5605ee9b41f` 保留。作者分别使用独有新树，完成后独立审查，再逐项精确采用。原始失败、初版与修复提交均保留。

|组件|完整作者历史|协调采用终点|复审结果|
|---|---|---|---|
|正式 batch v1 helper / 734-byte literal|`ad99605..0b65a68f100beed8172c3c3f270a19c5e1cf8523`|`f6304fa12a3aabc33243a6169c2736d41aceb015`|SPEC/quality PASS；6 新方法，15 项受影响方法包含旧回归|
|不可变 index/source/offline publisher|`ad99605..f58bff56c0e823b1d542d623e8db9b21e011635f..38a321a5bd11d56b8071dc03965fe413c71bd78e`|`ba8af64a09aa4ae7ec5f48db033ce6b3d7b891c8`|首次三项 P2、一项 P3；fix1 全关闭，双 PASS；最终 28 方法|
|独立有界 processing facts reader|`ad99605..298695761706419ee20c1b6b98f3ad73eff65cdd..76a50c72006835a567ff200d231bf78420d67f3a`|`5563f4cebd6f3f8d5c44d67c4e9b54f0a1d83d00`|首次一项 P2；fix1 关闭，双 PASS；最终 12 IT 方法|

采用凭据逐文件比较作者完整历史终点和协调树，合计 999 个新增路径/模式/载荷全部精确一致；每次采用后验证 2840 个受保护 artifact/计划/SQL 路径不变。源代码、原始日志、XML 和每轮封存均在各组件目录。此处保留[独立审查原件与采用凭据](../../../artifacts/phase-b/b06-archives/coordination/components/SHA256SUMS)，既有审查未重跑 Java 或访问夹具，结论来自源码和保存的真实运行证据。Task2 初始 329 项源封存应对 f58 Git blobs；Task3 初始 306 项应对 2986957 Git blobs。当前源使用各 fix1 current seal，不能把历史封存改写为修复后源码声明。

唯一新 MySQL 夹具名 `ama-test-b01-a5e7d68612a4`，ID `363e6bef449436390d3e21041408b53002a48311668c86864befe2535404e45b`，owner `archives-20261023`。Task3 证明 initial-zero、受限资源、loopback、固定 8.4.6 镜像及 12 份不变 SQL 按序 through V013；Task3 修复后保存 history/旧 41 manifests 不变审计。私有 env 仅在已授权进程内部加载，未导出值。Task3 完成且停止访问后，root 将唯一使用权串行转交给 Task4。

Task4 的独有新树为 `.worktrees/b06-archive-restart`，分支 `codex/b06-archive-restart-20261023`，精确组合 BASE `5563f4cebd6f3f8d5c44d67c4e9b54f0a1d83d00`。必须另建 matching-current-fence 元数据，用真实 enqueue/acceptedTask/commit ACK，然后独立 JVM 恢复；此前 Task3 的历史任务直接插行只证明事实读取，不替代入队资格。最终组合 units、Task4 IT 和整项独立复审仍待执行。

处理决定及代价：用户明确选择子代理，采用逐任务作者/独立复审，取代准备书所建议 inline 执行；代价是额外上下文、审查及集成步骤。原生 worktree 工具对 WSL UNC 报 ownership，使用已验证的 WSL Git 建独有树而不改 global safe.directory；代价是手工生命周期，无 app managed attachment。JDK 缺句柄相对 mkdir/目录 fsync：离线 publisher 仅在原计划受保护、部署自有的管理根下，使用 sole lock、NOFOLLOW/目录身份复核、独有 staging 及 held-SDS sibling atomic move；运行时读取始终相对句柄。若该平台假设不成立，代价是 publisher 平台适配返工，不能扩大成不可信可写根、恶意特权替换、断电或 HA 资格。

B06 及阶段 B 尚未完成，未新增 SQL、生产 policy/provider/HTTP/WorkerLoop。B01-IMM-01 永久 PARTIAL，B04-Q401 UNKNOWN，D01 仅设计采用，FUP-B05-CANDIDATE-01 OPEN，企业与生产门禁不变。
