# B06.2 受控档案与 processing metadata 恢复采用记录

2026-10-23。用户指定的 B06.2 四任务已完成实现、真实测试、逐任务独立复审及整项独立 SPEC/quality 双 PASS，P0–P3、deferred/parked 全零。完整 B06 与阶段 B 仍未验收。本记录承接原[准备书](task-B-06-archives-prepared-brief.md)、[激活及组件采用快照](task-B-06-archives-activation.md)和[整项候选快照](task-B-06-archives-review-ready.md)，历史原件字节不改。

## 精确范围与采用

激活 BASE `ad9960595ab5de6eef7760e2c9094d23666074cb`；运行时源码最终采用并新鲜全量单测的终点 `821daaa05a4c4faffb924ae4c00b72cabdd62347`。整项独立审查覆盖 `ad9960595ab5de6eef7760e2c9094d23666074cb..adb2461bb285283663db66434d3ae64c4b4cd980` 的 1956 个唯一路径，包含完整实现、初版失败与修复、组件复审、精确采用及组合测试证据。该范围全部为新增路径，其中只有 19 个实际新增 Java 编译源路径，不能把档案中的 Java 副本计成应用类。

|任务|完整作者终点|协调采用终点|精确路径数|
|---|---|---|---:|
|batch v1 / 独立 734-byte literal|`0b65a68f100beed8172c3c3f270a19c5e1cf8523`|`f6304fa12a3aabc33243a6169c2736d41aceb015`|104|
|index/source/publisher，含 fix1|`38a321a5bd11d56b8071dc03965fe413c71bd78e`|`ba8af64a09aa4ae7ec5f48db033ce6b3d7b891c8`|459|
|processing facts reader，含 fix1|`76a50c72006835a567ff200d231bf78420d67f3a`|`5563f4cebd6f3f8d5c44d67c4e9b54f0a1d83d00`|436|
|跨 JVM 真实入队 metadata 恢复|`41a41e1d7d345fd3b6d46e0a7bc01757298abb3c`|`821daaa05a4c4faffb924ae4c00b72cabdd62347`|512|

每项完整作者历史的最终字节/模式与协调采用精确相等。原树、初版提交及所有失败/成功证据保留。Task2 首次三项 P2 与一项 P3（链预算、复制前 admission、FIFO lock、deadline 文档），Task3 首次一项 P2（scalar 与 UTF16 单元）由原作者版本化修复、原独立审查者限定双 PASS，整项审查再次确认关闭。原 f58/2986957 封存仅绑定历史 Git blobs，当前修正源码使用各 fix1 current seal，不改写旧报告或哈希。

[整项独立审查原件](../../../artifacts/phase-b/b06-archives/coordination/final/whole-b062-independent-review.md) SHA256 `707538771e1b71bf9da90d79f42110edcca16904d715741d9f5371e54004f56d`。审查者独立核对全部路径/模式、六份 current seal 的精确覆盖、386 份保存 XML 和源码/日志关联，直接审查组合源码；未重跑 Java、访问 DB/Docker/私有环境。本审查后仅追加采用、报告、最终核对及接续文字，不扩大上述源码审查范围。

## 新鲜验证与计数

- root 在 `821daaa05a4c4faffb924ae4c00b72cabdd62347` 未加载私有夹具环境，执行 `python3 tools/checks/run.py unit`：29 套、213 项，0 failures/errors/skips，8 模块构建 SUCCESS。[原始日志/退出码/fresh XML](../../../artifacts/phase-b/b06-archives/final-units/summary.json)保留原 DeclaredErrorResponse.java 的 deprecated API 提示。
- 事实读端最终 12 个真实 MySQL IT 方法通过，包括授权、额外 task/producer、历史 fence、LONGTEXT 前置边界、取消与溢出。原 10 项及 UTF16 新 2 项均执行。
- 重启最终 1 个 JUnit 方法通过，包含 5 个真实子 JVM：A/B 成功及缺失历史、篡改历史、错误 pin 三负例。A 经真实 enqueue/full TaskSpec/acceptedTask、一次外层 outbox flush 和实际提交后才 ACK；全新 B 按原 manifest 恢复 root.1/旧摘要，独立新 draft 选择 root.2。三副本 unavailable，无默认回退/成功 ACK/已核对 DB 变更，原正例和行保留。
- 真 RED 为 A 提交后 fresh B 错选新默认、父断言失败；GREEN 只修 B 的历史 Ref 选择，测试/helper/调用不变。编译/设置错误单列 setup；五个子 JVM 不是五项测试。旧 179 加 batch6、archive28 构成当前 213，各轮专项/自动 units 不累加。详细 source/SQL/manifest/PID 哈希见[作者报告](task-B-06-archives-report.md)。

## 消费者和保护边界

[消费者契约](task-B-06-archives-integration.md)为实现入口：exact index SHA 与受保护部署目录是显式信任锚；追加保留旧 identities/history，缺历史须 unavailable。运行时相对句柄/NOFOLLOW，离线 publisher 依赖受保护管理根及已验证本地 SDS/fsync/atomic move。文件入口的 30 秒为 cooperative 检查，纯 reader/ref-discovery 仅用调用者 predicate；不提供 OS 抢占、恶意特权替换、断电、HA 或真实容量资格。

唯一新 MySQL `ama-test-b01-a5e7d68612a4`（ID `363e6bef449436390d3e21041408b53002a48311668c86864befe2535404e45b`，owner `archives-20261023`）由 Task3 initial-zero、固定镜像/资源/loopback 后按原历史应用 12 SQL through V013；完成后唯一使用权串行转交 Task4。Task4 保留全部 62 份移交 manifest SHA，最终 64 包含 RED/GREEN 新行，history/SQL 不变。fixture/运行目录保留，无旧库/容器/S3/PKI 操作，无 reset/repair/outOfOrder、秘密值公开、push 或生产激活。

最终再核对 2840 个受保护 artifact/计划/SQL 的字节及 Git mode/blob、两份用户计划的四个当前/检查点路径、root attributes、main `0676a21311f2b2193f73a5e096f4a5605ee9b41f` 和原 stash `bc12e825246c60c12752129c4d512ef9c545758c`。[最终核对与接续文件封存](../../../artifacts/phase-b/b06-archives/coordination/final/SHA256SUMS)列明本次收尾，既有六封存载荷不变。

## B06.3 接续与执行取舍

下一任务从协调分支最终 clean HEAD 另行指定精确 BASE，先准备并审查 B06.3：首次真实 INPUT_BATCH_FILE 的原 upload UUID/purpose、完成 session、AVAILABLE/route/epoch 与 current/history 读取资格；未提交批次/真实 held capability 的事实组合需要窄接口，standalone detached facts 不签发写入授权。本轮已证明的是已提交 metadata + detached draft + 新鲜授权/固定事实复核的受控组合。

input_batch 无 fingerprint profile/version，v1 helper 不自动重解释旧 hash；混合历史/升级须中央 forward schema 设计后另审，不猜迁移。导入/Worker 原子输出、保存/编辑/marks/确认、完整 B07 facts、Supplier/provider/API/HTTP 和生产闭环仍待实施。FUP-B05-CANDIDATE-01 OPEN、B04-Q401 UNKNOWN、B01-IMM-01 永久 PARTIAL、D01 仅设计采用及企业/HA/fencing/容量门禁不变。

三项执行取舍及代价详见激活快照：按用户要求采用子代理与独立分项复审，增加上下文/集成成本；原生 WSL ownership 失败后使用 WSL Git，保留手工 worktree 生命周期；JDK 目录 API 的限制留在受保护管理根假设内，若平台不成立须返工适配。Task4 选择已提交 metadata 短写组合，未提交批次/held-facts reader 的后续实施及复审成本保留。全部 worktree 保留，main 按阶段门禁不动。
