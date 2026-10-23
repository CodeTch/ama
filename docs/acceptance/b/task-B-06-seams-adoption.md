# B06.1 共享身份准备协议采用记录

2026-10-23，采用已独立审查的共享队列/授权接缝修正。范围是 B06.1，不代表完整 B06 或阶段 B 验收；协调分支为 `codex/phase-b-resume-20261023`，main 仍为 `0676a21311f2b2193f73a5e096f4a5605ee9b41f`。

## 精确范围与审查

作者完整范围为 `6b48b2f855bbf890c3ccad68325e7fd74d434489..4dcdc6b5102c7d99b043385349b4a87c98b52e80`。三个提交全部采用，保留原技术提案、真实 RED、修正和封存历史：

|作者提交|协调分支采用提交|内容|
|---|---|---|
|`26bf77d7d514475de874bdcee4962833a4bf8a2a`|`a1f6cbf3de4597ab50f410483f8f0f541ebbae61`|技术提案与最初真实 MySQL RED/控制|
|`553196cacbf27f2fd4dfc3b65d70c535f10f9958`|`7dced13c4642e30fa8596ca2888ce302a483b474`|最终经过验证的共享协议修正|
|`4dcdc6b5102c7d99b043385349b4a87c98b52e80`|`0a85ef62007e2f8fd2d590002f3592e79f4767f1`|最终报告、证据与封存|

[独立审查](task-B-06-seams-independent-review.md)为 SPEC PASS、quality PASS，P0–P3 全零，无 deferred/parked finding。[独立机械审计](task-B-06-seams-evidence-audit.md)核对完整 1104 路径、1103 个非自指封存哈希、364 份历史 TEST XML、最终源码关联及 1759 个旧保护文件；没有重跑测试或访问数据库、私密环境。

审查包为 10,692,954 字节，SHA256 `b283e5639f222a66a850b8b3e81c20f057b6ccf2394de459bb281391c5c8b0fd`。审查报告 SHA256 `46f1b2638e8f181c2aaa6a2dcdffa13d76499939211b57904620877002ffb496`，机械审计 SHA256 `ddab0b9759281a1aae9b0bfc5052afa292338a2f94ebe1017d19fcdd93ccbafc`。

## 采用后的行为与消费契约

完整身份集合先于 subject/root/task/operation 锁。每次调用的 opaque capability 绑定发行者、精确 DataSource、实际事务 holder、完整不可变 TaskSpec 集合和用途；队列消费端独立核对 DataSource/holder。领域行在一次完整 batch 中预锁，重试及事实观察复用本地 DomainScope。已有真实 MeetingAccess/file capability 可显式采用，不能在根锁后重新获取身份。

普通任务变更与外部事实观察分开。停用身份不能获得领域回调、续租或再次发送权限；精确绑定当前执行、通过原权威校验的终局事实仍可用既有 RECORDED_FENCED 保留。仅此无领域效果的事实路径可忽略旧 owner/epoch/expiry；ACTIVE 授权、过期 UNKNOWN/MANUAL、错误执行、未验证事实及矛盾隔离规则保留。全局 reconciliation claim 可跳过未注册域，实际 observe 对不支持组合仍显式非 ACK。

上述 skip 只针对原 LIMIT 64 查询已选中的窗口，不代表全局公平性。连续前 64 个不可获取候选可能使后续候选不可见的既有限制，已单独登记为 [FUP-B05-CANDIDATE-01](task-B-05-reconciliation-candidate-followup.md)；原查询未改，本轮没有相应运行时复现或生产资格结论。

接入细节以[消费者契约](task-B-06-seams-integration.md)、[作者完整报告](task-B-06-seams-report.md)及公开 ACK 为准。未注册域没有宽松默认；旧 identity-free 测试策略均显式接入。原三参数 completeWithEffects 的“先校验已存在输出，再运行 effects”语义保留。

## 验证与采用凭据

最终源码执行结果如下，全部零 failures/errors/skips：

|验证|准确数量|
|---|---|
|B06QueueSeamsIT|18 个方法、18 次调用|
|受影响 B05/B03/授权精选回归|26 个方法、28 次调用；held-row 方法展开为 3 次|
|完整 Java units|26 套、179 项|

最终 IT 共 44 个不同方法、46 次调用。早期 RED/GREEN、179 项重复执行及先前 S1 的 112 项均有重叠，不累加为独立新增覆盖。最终三轮的全部 12 个 Java 文件哈希与 tested commit 一致。原始身份锁序/ambient RED，以及 skip、过期事实、未注册域、跨数据源的真实断言 RED 均保留；设置错误不作为产品 RED。

采用后新鲜核对 1104 个路径及全部文件模式，1103 个非属性载荷与作者精确一致；属性文件也通过作者封存哈希。整份作者 SHA256SUMS 在协调树再次验证通过。记录见[采用凭据与封存](../../../artifacts/phase-b/b06-seams-adoption-2026-10-23/SHA256SUMS)。采用没有改动经过验证的源码，未重复运行相同 runtime cohort。

唯一新建 MySQL 夹具保留，限定 loopback/768MiB/1CPU/pids256。初始零表后实际应用 12 个原 SQL through V013；精选 B03 helper 确实调用最新 migrator，验证 12 项并执行零迁移，不能称未调用迁移器。旧库、容器、S3、PKI、已应用 SQL、计划与历史证据均未改动；没有 reset/repair/outOfOrder。

## 后续范围

不可变配置索引/档案恢复、正式 batch/producer 指纹、生产 INPUT_BATCH_FILE 策略、完整导入/API/local/S3/Worker 原子输出及保存/编辑/marks/确认仍未交付。Supplier、WorkerLoop 和 provider 未修改，没有新增 SQL 或生产域注册。B04-Q401 仍 UNKNOWN，B01-IMM-01 永久 PARTIAL，D01 仍仅设计采用；没有 main 合并、push、生产或公司平台操作。
