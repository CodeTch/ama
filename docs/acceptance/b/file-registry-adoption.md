# B04 文件登记与上传：协调采用记录

日期：2026-10-23。恢复开发后补齐暂停时尚未写入的公开协调记录。本次采用已完成独立审查及限定复审的文件登记、上传状态机和鉴权读取实现，开放其作为 B06 的受审查依赖。此记录没有新增运行时代码，也不代表阶段 B、D06/D07 或生产资格验收通过。

## 采用范围与审查依据

原作者范围为 `8da440d39e1b8e8dcdfc34722908d29b5aa3aa8a..45dc8ed27449de191400c858b88413a518842f21`，包括初始交付、配置竞争修正和下载响应头修正。相关提交已归并至当前 main，无需重新实现或再次导入原功能。

| 作者提交 | 原集成提交 |
| --- | --- |
| `5e7022c20c9389ad319157320408901f3df089ac` | `b6d5930eb1f74e3311cd34f8bf730d5c99063ef4` |
| `d1e47ca69e68caa6bd51fee1706998b3e6cd2506` | `a1545a26ddcbaabb20d24d9e839b762d6544dc86` |
| `45dc8ed27449de191400c858b88413a518842f21` | `b938cb14275955cc860c806a7fb9de95565f5b65` |

[完整初审](../../../artifacts/checkpoints/2026-10-22-development-pause/task-B-04-registry-review.md)唯一阻塞项 B04-R1（P2）是合法长 Unicode 名称产生超大 Content-Disposition，导致实际 Tomcat GET/Range 返回 500。[修复报告](../../../artifacts/checkpoints/2026-10-22-development-pause/task-B-04-registry-fix1-report.md)及[原审查者限定复审](../../../artifacts/checkpoints/2026-10-22-development-pause/task-B-04-registry-fix1-review.md)确认 R1 ADDRESSED，限定 SPEC/quality PASS，未发现新问题。采用以完整初审和此次复审的组合结论为依据。

完整名称继续保存在持久元数据和展示语义中；整个下载响应头值最多 1024 个可打印 ASCII 字节。能够放入的名称精确保留，超长名称按完整 Unicode 标量保留前缀并追加 `...`，处理在预算边界停止。未增加产品名称上限或扩大服务器响应头配置。

## 证据与本次检查

[暂停时集成字节凭据](../../../artifacts/checkpoints/2026-10-22-development-pause/task-B-04-registry-integration-byte-receipt.json)已证明最终 227 路径、3,608,418 字节与作者 Git 对象一致。恢复后执行只读[保全检查脚本](../../../artifacts/phase-b/b04-adoption-2026-10-23/audit.py)，[结果](../../../artifacts/phase-b/b04-adoption-2026-10-23/receipt-audit.json)再次确认当前主树全部 227 路径与该凭据的大小/SHA256 相符；四份报告/评审哈希、两份用户计划和 12 份正式 SQL 均保持原字节。脚本只读取公开文件和 Git 对象。

本次没有重跑基础设施或业务测试，没有操作数据库/S3夹具。以下为已经由原独立审查核实的历史执行证据，不能计作新执行：

- 完整交付最终主要 IT 共 80 次执行、0 failures/errors/skips，含两后端重复方法；44 个不同方法，另有一次诊断运行。原审查详细列出范围及重叠。
- 响应头修复：local/S3 各 4 个方法，RED 各 3 个断言失败，GREEN 各 4 个通过；合计 16 次实际 GREEN 下载。重复的同一组 67 单测不累加为独立测试数。
- RED-local 测试源码只有旧哈希、没有旧源码前像，不能声称与最终测试字节相同。RED-S3 的最终测试源码和旧控制器保全，可证明准确因果回归；local GREEN 与原独立 HTTP 失败证据继续有效。

原始失败/成功日志、XML、报告和 manifest 保留在 `artifacts/phase-b/b04-registry*` 与暂停 checkpoint 中，本次没有覆盖旧档案。

## B06 消费边界

准确接口与组合规则见[作者消费者手册](file-registry-integration.md)。`FileRegistry.lock(actor, meetingId, action)` 必须首先取得 identity→subject→meeting 锁，`registry.access(held)` 返回该次真实 B03 capability。不得在组合中另外调用 MeetingAccess.lock；所有策略 prelock 在 session/file 阶段之前完成；整个输入批次使用一次 `bindAll`，按完整 session 集合再完整 file 集合排序锁定。`resolve` 只是授权事务内部元数据，不是独立读取许可，业务变更的 audit/outbox 最后 flush。

本次采用不注册尚未实现的 INPUT_BATCH_FILE 生产策略，不激活导入 Worker，也不以技术上传保护引用冒充业务可读引用。B06 仍需真实领域行、完整所有者锁、原 upload UUID、当前和历史引用规则，以及任务/文件/业务原子提交的独立验证。

## 保留问题与限制

**B04-Q401 继续 UNKNOWN。** 原 `orphanIsNotReadableAsBusinessFile` 在首次 upload begin 预期 201、实际 401，trace `2b235500-5f04-43b7-a742-8fb19a63f7ac`。该事件发生在回滚 hook 和存储操作之前；旧证据缺少精确 cookie/session/resolver 关联快照。后续通过仅是未复现，不是已定位或已修复。若再现，先保留有限非秘密关联证据，再做因果分析，不能盲目修改身份模块或加重试。

D06 长流撤权、D07 清理/保留、真实平台、断电/HA/fencing、容量和公司接口资格仍开放。B01-IMM-01 的永久 PARTIAL 和既有迁移保护规则不受本次采用影响。