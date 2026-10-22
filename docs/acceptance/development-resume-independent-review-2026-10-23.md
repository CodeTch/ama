# 2026-10-23 接续优先项整分支独立审查

审查者：`/root/resume_checkpoint_review`，独立于本次两项修复作者及先前限定复审者。只读主目录 `/home/tanren/ama`；未派子代理。日期：2026-10-23（Asia/Shanghai）。

## 双结论与 actionable findings

**SPEC：PASS。Technical quality：PASS。** 结论覆盖下列固定 BASE..HEAD 的全部组合变更、接续优先项与记录真实性；不是只审 HEAD~1，也没有将 scoped PASS 当成无需独立检查的前提。

**Actionable findings：无 P0/P1/P2/P3。没有 deferred、parked 或仍阻止本次采用的 finding，因此无新增问题的 file:line 项。** B07 F1/P1、F2/P2、F3/P2 与 D01 R1/P2 在当前组合中均已解决。B04-Q401 继续 UNKNOWN，B01-IMM-01 永久 PARTIAL；它们没有被静默关闭，也不因本次 PASS 得到新的运行时资格。

本结论支持已完成接续优先项的组合采用。它不宣告完整 B07、D01 运行时、B06、阶段 B、生产部署或 main 合并通过。

## 固定范围与完整包核对

- 分支：`codex/phase-b-resume-20261023`。
- BASE：`0676a21311f2b2193f73a5e096f4a5605ee9b41f`。
- 完整 HEAD：`05326e918be139973944bfad46e59c4fdc252e7b`。
- main 在审查前后仍为上述 BASE；工作区 tracked/untracked status 均 clean，审查报告位于已忽略的 SDD 目录。
- 固定包：`.superpowers/sdd/2026-10-23-resume/resume-checkpoint-review-package.diff`，**1,920,169 bytes**，SHA256 **`a52ec053eee03d601a914f56f409e1e8a07ba3fe479934efd0b2ca8000777e50`**。

完整六提交均在范围内：

|提交|内容|
|---|---|
|`ba6306c66f43402253f9073b8c605cbaf16edbd3`|B04 公开协调采用与保全|
|`01ab5380f308233e4b7932096c7df2bae07e0d83`|D01 R1 精确采用|
|`fba12b695e01788b2307f713d9a6301a9941f4d6`|D01 设计采用记录|
|`93b2270fb98482cb0d5470d5f80a2275100abd02`|B07 F1/F2/F3 精确采用|
|`6b48b2f855bbf890c3ccad68325e7fd74d434489`|S1 采用与接续验证公开归档|
|`05326e918be139973944bfad46e59c4fdc252e7b`|当前接续入口与状态|

本轮独立 Python/Git 只读审计逐字节比较完整包的 patch 部分与 `git diff --no-ext-diff --full-index --binary -U10 BASE HEAD`；相等。包的全部 **172 路径、2,261,519 最终文件字节**，逐项 size/SHA 与 exact HEAD Git blob 及当前工作文件均相等。`git diff --check BASE HEAD` 实际 exit 0，stdout/stderr 为空。

172 路径的完整归属为：根属性 1、根 handoff 1、生产 Java 10、测试 Java 5、当前 docs Markdown 13、checker-renderer 1、D01 fix1 archive 36、B04 adoption archive 4、B07 fix1 archive 62、resume archive 39。此计数涵盖整包，不把大 XML/重复历史复制材料假称为逐行手工代码阅读。

## 需求、手工阅读与必要旁路检查

先完整读取当前 `DEVELOPMENT_HANDOFF.md`、`docs/acceptance/development-resume-2026-10-23.md`，再核对 2026-10-22 暂停接续说明及两份原 fix1 brief。接续顺序要求修 B07 F1/F2/F3、D01 R1，独立复审后采用，补 B04 记录，并保留计划/SQL/档案及 main 阶段门禁；当前组合满足该范围。原作者/审查会话无法恢复后的接续角色与代价已明确披露。

手工阅读覆盖：全部 15 个 Java 变更，Artifact 声明与 API/equality/hash 变化；完整最终 `StoredSourceGraph`、`StoredFactsIndex`、`OriginChainVerifier`、`GenerationFingerprint`、`TranscriptConfirmationHash`、`ResultHashProfiles`、`DomainHash`、`ConfigBindings`、`ConfigArchiveContracts`；新反例 fixture/helper、各新测试断言、消费者补充；D01 全部当前规范/字典/ER/checker 变更与精确 §5a/§6/§9；作者报告、原 full review 的发现/边界、两项 scoped review、三份 adoption 及 B04 文档复审；新 evidence 的 capture/audit/seal/receipt 脚本。

必要 outside-diff 阅读限于实际风险：`ArchivedProfileResolver` 的 operation 所有权、`ArchiveReadState` 累计预检、`RowsetProjection` 的 hashBudget 使用、`StrictConfigArchiveReader`/`CanonicalJson` 的同 holder SHA/parse/equality，以及全部生产/测试 Artifact 构造和 bytes 调用点。还核对 `tools/checks/run.py:13`、`:52` 的旧 XML 清理及明确 unit selector，确认 root fresh XML 声明有运行器机制支撑。没有扩展为旧 B04/B05 业务全面重审或无关功能爬查。

初始若干合并输出过长处已用有界的单文件/行号阅读补齐关键遗漏；没有将截断输出当成已读证据。完整 raw/XML/JSON/重复 custody 副本按程序全字节及结构审计，和上述手工阅读区分。

## B07 组合代码判断

**F1：ADDRESSED。** `StoredFactsIndex.java:12`、`:16` 创建一份操作索引及 `sources`；`StoredSourceGraph.java:22`、`:155` 保留每个到达历史 input 的完整 pinned-set graph。`OriginChainVerifier.java:9`–`:29` 同时收集根和全部 nested graph 的 intrinsic confirmations，并验证每个到达 input 的主确认、精确 raw 配置、processing 链、历史 TaskSpec/fence 和 stored fingerprint。usage 的完整多重性仍进入投影；去重的是验证目标，不是业务 occurrence。

`GenerationFingerprint.java:88`、`:115`、`:124` 的 nested/直接/草稿路径与 `TranscriptConfirmationHash.java:48` 共用同一个 index/resolver；nested recompute 不再重入公开验证并重开预算。`ResultHashProfiles.java:85`、`:144` 的 review/final 也复用该状态，选中 final reviews 调用 bound helpers。所有 transitive input graph 在 verifier 快照 IDs 前已由迭代 walk 完成闭包；不可变事实上的后续 recompute 不会偷偷引入未验证新后代。`GenerationFingerprint.java:94`–`:98` 用 finally 恢复 nested rowset hashBudget，避免旧 input 的预算泄漏到调用者。最终 digest 返回前仍完成 raw exact closure。

**F2：ADDRESSED。** `StoredSourceGraph.java:163` 先查 active-cycle；`:164`–`:166` 对缓存节点以 `height <= maxOriginDepth - depth + 1` 检查完整剩余子树高度。完成节点向父传播完整 height；`:175`–`:176` 先使用 long 加一再比较/转换。正深度前置检查使减法在 Integer.MAX_VALUE 上界也安全。iterative stack、每轮/每边取消检查及 session 累计 unique visited-node 预算保留。缓存叶和非叶、浅后深和深后浅均不能隐藏最长路径；重复使用不会重复消耗 unique-node 额度。失败后没有成功 digest 返回路径。

**F3：ADDRESSED。** `MinutesContracts.java:270` 移除 `Artifact(..., byte[])` 与复制数组 accessor，统一为 immutable/size-inspectable `ArchiveBytes`。`ConfigArchiveContracts.java:17`–`:23` 的唯一 raw capture 在私有 clone 前检查实际 maximum/remaining 与取消；无公开 unchecked constructor。`StoredFactsIndex.java:23`–`:30` 和 `DomainHash.java:10` 先读 size，再对同 holder 做 SHA；`ConfigBindings.java:119` 直接复用已隔离原件，取消原 readAllBytes+clone 旁路。strict parsing 与 canonical-byte comparison 继续使用同一份隔离 bytes。

全量调用点搜索没有遗留 Artifact 裸数组构造/复制 accessor。detached facts 的累积唯一 `(version,SHA,content)` 预算与 raw Context 的 archive/manifest 累计预算分开明确；原始 acquisition 的实际预算必须在首次 capture 传入（正常用同一 ArchiveReadState），API 不会推断后来更严预算。这一边界在消费者补充/adoption 中如实说明，不是默许旧旁路。

现有 IMPORT/TXT/EMPTY/FULL、未知/不支持历史显式失败、profile/domain 身份和 usage multiplicity 保持不变。D01 设计采用没有将当前 v1/v2 S1 默默升级为材料 v3 运行时。

## D01 R1 及跨组件判断

**R1：ADDRESSED。** 当前 addendum `:57`、`:128`、`:149`–`:165`、`:217` 的单一规范均为 B(D)∪U(D)，basis 不再依赖证据数量：

|域|准确 basis 与 usage 范围|
|---|---|
|ROWSET v2 / PINNED|所有准确纳入的已保存 input-set 要素 basis，加 set evidence/directory contexts；排除 body evidence/applied-exclusion usages|
|REVIEW_SOURCES|仅所核对要素 basis，加准确相交证据；body-only 核对无 element basis，不引入其他要素/目录/排除根|
|FINAL|全部准确纳入的冻结要素 basis，加既定 body/set evidence、directory、applied-exclusion contexts；不新造独立 body basis|

C1 basis / 全部 evidence C2 必须包含 C1+C2。`:163` 对每个 basis 要求完整 context、全部 selections、selected profiles、confirmation/material/catalog/parser/archive 及 stored-input/fence 验证和 §6 retention；无 usage 不能逃避。`:165` 按实际完整 PK、unsigned UTF8 排序去重，先拒绝 supplied duplicate keys，缺失/额外/无关行拒绝；basis 不制造 EVIDENCE、不增加 usage、不授予支持信用。

current raw sourceContext 和 exclusion.appliedContextId 留在已有顶层；草稿无自身 fingerprint/TaskSpec，继承 generated basis 必须是持久化图依赖祖先，非 UUID/时间推断。字典、ER、renderer 共享说明及 prerequisite 同一规则。完整 diff 未改变关系声明/DDL/ER 实体边、正式 SQL、迁移登记或运行时代码。新脚本属于离线验证/证据工具，没有引入生产框架。

作者的 pending-review 标题是精确保留的作者交付时态；当前 handoff/resume/adoption 已明确给出后续独立复审和设计采用，不据此误判仍未关闭，也不据设计采用声称已分配/应用迁移。

## 执行证据与计数的独立核对

本轮没有重跑任何单测、离线向量、DB/Docker/服务或旧大套，也没有新的 Java/DB 行为 probe。以下是读取封存 raw、XML、源码/命令 SHA 并独立核算的**记录执行结果**，不是本轮重新观察进程成功。

|封存运行|本轮独立核对|
|---|---|
|B07 01 graph RED|1 suite / 8 cases / 5 failures / 0 errors/skips；五项均 `org.opentest4j.AssertionFailedError`，期望安全 Failure 而实际未抛；分别是 outer compute、review、final、shared leaf 和 reused nonleaf|
|B07 02 / 03 / 09|02 旧 API 编译失败；03 contracts 4 pass 后 core test 编译失败、adapter 未执行；09 evidence audit AssertionError。均保留并正确排除产品 RED|
|B07 04 / 05|3 suites / 20 和 24 cases，均零 failures/errors/skips；是聚焦重跑，不能累加为不同测试|
|B07 06|13 suites / **112 unique cases**，零 failures/errors/skips；原 97 testcase identities 全部是子集，恰好 15 新项|
|B07 07|25 cases，全部是 112 子集；八 reactor 模块编译，无 IT 执行|
|root unit|26 份 XML / **179 unique cases**，零 failures/errors/skips，包含全部 112；多出的 67 是其他现有 unit cases，不能记作 291 项或 179 个新测例；八模块 SUCCESS，BUILD SUCCESS|
|D01 final vector / preservation|最终命令/source SHA 对应 82 / 184 assertions；old-recipe replay exit 1 为静态集合矛盾 assertion，最终 82/184 exit 0；未重复 DB cohort|

22 份 B07 XML 和全部 26 份 root XML 均读取原字节、验证清单 SHA、解析实际 testcase/failure/error/skipped 元素，并与 suite attributes 交叉计数；所有 testcase identity 的子集关系由本轮独立集合比较确认。12 份 B07 raw log 全字节读取并提取 build/compile/assertion 标记；root 完整 log 的 unit totals/8 模块/BUILD SUCCESS 与 XML 一致。

root summary 的 sourceCommit 为 `93b2270fb98482cb0d5470d5f80a2275100abd02`，16 个源码/checker SHA 与当前文件相同，且该 commit 到本次 HEAD 没有 backend/checker 改动。fresh reports 由规范运行器先移除旧 reports 再执行；root archive/capture 绑定这次结果。root log 中 `DeclaredErrorResponse.java` 既有 deprecated API 编译提示真实存在，文件未改，handoff/adoption 已记录，未被隐去或归因于新源码。

B07 RED source 两份保存快照与当时捕获 SHA 相符，后续按原 BASE+test prefix 重建的事实保持明确；不冒称在 RED 执行前已归档原快照。F3 没有 allocation-observer runtime RED，而是静态控制/数据流保证和小型容量/隔离行为证据；compile/API setup 不当 RED。

D01 保存 JSON 的 82 个命名 checks 唯一，184 个保全 checks 的数量与记录一致。独立从保存向量核算准确根/PK/完整选定 document-profile/缩减 digest：PINNED=C1..C5（3 evidence/4 usages/10 selections/10 profiles）；review E1=C1+C2（1/1/4/4）；FINAL=C1..C7（4/6/14/14）；E0 review=C3（0/0/2/2）；manual EM review=C2+C4；body review=C6。C1 无 usage 仍完整绑定，C8 不入任何域。旧 recipe 初始 traceback 194 与最终 replay 203 的差别未隐瞒，最终 runner SHA 对应 203 行 assertion。它证明静态配方冲突，不能称为实际 bad final、哈希碰撞或 SQL 丢失。

## 采用凭据、B04 与原字节保全

本轮直接比较 Git blobs/工作字节，没有只信任作者或 receipt 自报成功：

- B07 author `7e052a8..13b8f1d16f85787c12626faa41c7e511403f16f9` 与 root `fba12b6..93b2270` 的路径集同为 79；78 个非属性 payload 逐字节相同。D01 author `214a63d..212c794b497631b13b395c4d1436cbe306208fd4` 与 root `ba6306c..01ab538` 同为 43；42 个非属性 payload 相同。
- 两个 receipt 的全部 pathHashes 与对应 rootAfter blobs 相同；除根属性后续再追加外，当前载荷仍相同。`.gitattributes` 不仅检验 subsequence，还独立检验 **rootBefore bytes + author 相对 ownerBase 的精确追加 suffix = rootAfter bytes**。两个追加冲突确为有序 union，没有其他载荷改写或漏规则。
- B07 新 seal 77 payload、resume 本地 seal 38、B04 本地 seal 3 全部匹配；D01 manifest 41 个 covered files 匹配。历史 author seals 中 `.gitattributes` 正确按其 author HEAD blob 检验，未误把 root 合并后的属性声称成 author 原件。
- B07 original273 的全部 BASE size/SHA 匹配；当前 258 原字节不变，15 差异恰为授权既有 Java/属性。271 old payload 全部在 Git 保留，当前 256 相同。旧 `b07-pure-s1/` 120 文件、全部六个原 review/brief/probe custody sources/copies 保持原字节。
- D01 original65 的全部 BASE size/SHA 匹配；当前 59 原字节不变，六个例外恰为授权属性/当前规范/prerequisite/dictionary/renderer。35 个 immutable 文件匹配；旧 `b00-provenance-design/` 50 文件、六个原 review copies、B00-1.3 历史目录 5 文件及 checkpoint 40 文件与 BASE 原件相同。旧 93 whitelist vectors 保留，旧 draft 仍 6,871 decoded bytes / SHA `015a3a30f9970d41ef3ac45209794c7a4367e494b53a4f314f6cd0f0d3728112`。
- B04 receipt 全部 **227 paths / 3,608,418 bytes** 与当前文件及 exact owner HEAD `45dc8ed27449de191400c858b88413a518842f21` 相同；四报告/评审 hash 相符。三个作者提交均为 main 祖先。公开协调记录正确结合 full review 与 R1 scoped review；80 主要 IT 执行/44 distinct methods、local/S3 各 4 方法、16 次 GREEN downloads、67 unit 不累加的历史计数与原评审一致。RED-local 无 source preimage 的限制保留。
- B04-Q401 的首次 begin 201/401、trace `2b235500-5f04-43b7-a742-8fb19a63f7ac`、发生在 rollback hook/storage 前、缺精确 cookie/session/resolver 关联证据，与原评审相符；后续 GREEN 只未复现，没有假称定位/修复。
- 正式 **12 SQL**（through V013，含 V011_1）、两份用户计划及 checkpoint plans copies 与 BASE/既定 SHA 逐字节相同。总计划 SHA `a532dec6e94c53f0e81ae92f14b4f84b6a56cf9d68c37bd6e27d7903f911ffa9`，B 计划 SHA `bfa789f77b5ca90725b2c0f32977993c260ac79ab22c2fff76cd775cc104c862`。新/改 Markdown 的 43 个直接本地文件链接均存在。

当前入口没有把 2026-10-22 历史暂停快照改造成当时已通过；新的接续记录准确盖住旧状态。B06 预检的待证明锁序/真实来源/首次业务引用/Worker 原子提交缺口仍公开；B06 技术提案/RED 位于其他独立树，**不在本次固定代码范围**，本轮未读取/验证该树或声称其已完成。

## Cannot verify 与实际限制

1. 本轮只验证封存执行证据的内容、源码/命令/原件绑定和内部一致性，没有重新观察历史 JVM/Python 进程；不能独立证明每个未记录的历史操作或私有环境状态。已保存 source hashes/custody 与真实 assertion XML 支持已披露范围，不把重建快照当成现场前像。
2. F3 首次 clone 前预检是源码数据流结论；小型断言验证容量拒绝、同 holder、隔离/取消/累计边界，不是大内存或分配探针。初始 capture 期间外部并发改数组、阻塞 I/O deadline 仍属 acquisition 责任。
3. D01 82 向量是 synthetic IDs/hashes 和 sorted JSON 的缩减模型，184 是 render/preservation；均不能证明完整 v3 wire、真实 raw archive、ACL/parser、数据库应用/恢复或运行时 final 合格。正式迁移未分配/应用。
4. supplied-fact 验证不证明权威完整查询、持久档案索引/发布/恢复、历史权限撤销、事务/lease/CAS、模型/renderer 质量、长流/清理并发、真实企业接口、HA/fencing/断电/容量。B06/B07/B08/D01 后续运行时与公司/运维门禁仍待原计划交付。
5. 此审查没有操作 source/index/HEAD/branch、没有 test rerun/DB/Docker/S3/网络/服务/外部发送/push，也没有读取私有 env/PKI。唯一新写入是本报告；无需额外小 probe 才能回答的具体未决代码疑问为零。
