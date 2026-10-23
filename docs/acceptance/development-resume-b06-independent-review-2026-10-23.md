# B06.1 增量采用与接续文档独立集成复核

2026-10-23（Asia/Shanghai）。审查者 `/root/resume_checkpoint_review`，独立于 B06.1 作者及其独立源码/证据审查者。只读协调树 `/home/tanren/ama`；未派子代理。本报告不重复已完成的 `0676a213..05326e91` 优先项全审。

## 双结论及 findings

**SPEC：PASS。Technical quality：PASS。** 适用于下列固定整体增量的精确采用、与既有基线组合、公开记录/计数/保全和文档补充。已完成 B06.1 独立源码与机械审查的结论可以复用；本轮没有重新执行测试或数据库。

**本轮新增 actionable findings：P0=0、P1=0、P2=0、P3=0。Deferred findings：none。Parked findings：none。** 没有新增问题需要 file:line 修复。已明确公开登记的 **FUP-B05-CANDIDATE-01 仍 OPEN**，是未改候选选择逻辑的既有公平性资格缺口，详见下文；没有把它隐去或宣称已修复。

本结论支持当前 B06.1 精确采用及接续文档，不代表完整 B06、B06.2 激活、全局 reconciliation 公平性、外部 worker 生产激活、阶段 B 或 main 合并通过。

## 精确范围与包

协调分支：`codex/phase-b-resume-20261023`。

|范围|固定身份|
|---|---|
|整体 BASE|`8bd5e59931cd92f7fef8fcca86324addb2635e38`|
|主包 HEAD|`140d64e309b45e2ec8030a3a6982c0e5b40ac429`|
|docs supplement / 最终 HEAD|`7df598503363668ff629801f0b50e7f7e4a90184`|
|被采用作者 BASE..HEAD|`6b48b2f855bbf890c3ccad68325e7fd74d434489..4dcdc6b5102c7d99b043385349b4a87c98b52e80`|
|精确源码采用完成点|`0a85ef62007e2f8fd2d590002f3592e79f4767f1`|

主包 `.superpowers/sdd/2026-10-23-resume/resume-b06-integration-review-package.diff`：**10,993,137 bytes**，SHA256 **`b40a44a7f933280e814f37d23ce61cd4506b1cd5c0807d94b64aff7faa637883`**，1114 路径。补包 `resume-b06-docs-supplement.diff`：**36,724 bytes**，SHA256 **`0bfb6ebc409e603568bce48e91eef14361ebd7cc237f5390dd6eeca7750227e9`**，5 个 docs 路径。

完整并集为 **1114 + 5 − 3 个重叠文档 = 1116 个唯一路径**，最终文件合计 **10,093,867 bytes**；不是 1119。主包归属为 prerequisite archive 144、correction archive 932、adoption archive 5、backend Java 12、tools 11、docs 8、根 handoff 1、根属性 1；补包再新增两个文档，修改三个已有文档，没有源码/工具/属性/证据变化。

两个包的完整 patch 部分均与各自 exact BASE..HEAD 的 `git diff --no-ext-diff --full-index -U10` 相等。实际包采用无 `--binary` 格式；734-byte literal 的 Binary-files 标记不替代字节审计，其 Git blob/工作原件/size/SHA 已另行核对。全部 inventory 路径及 final size/SHA 与 exact Git blobs/当前文件匹配，整体 changed-path set 恰为两包并集。最终 `git diff --check BASE HEAD` 实际 exit 0，stdout/stderr 为空。

## 采用真实性与基线组合

不是只比较最终报告或 HEAD~1；三个作者提交逐一核对了完整 raw mode/blob delta：

|作者提交|协调采用提交|
|---|---|
|`26bf77d7d514475de874bdcee4962833a4bf8a2a`|`a1f6cbf3de4597ab50f410483f8f0f541ebbae61`|
|`553196cacbf27f2fd4dfc3b65d70c535f10f9958`|`7dced13c4642e30fa8596ca2888ce302a483b474`|
|`4dcdc6b5102c7d99b043385349b4a87c98b52e80`|`0a85ef62007e2f8fd2d590002f3592e79f4767f1`|

每对 `git diff --raw --no-abbrev parent commit` 完全相同，包含新增/修改及文件模式。完整作者范围和 root 采用范围路径集同为 **1104**；全部 1104 个 mode/type/blob ID 在 author HEAD、root adopted HEAD 和当前最终 HEAD 都相等。**1103 个非属性 payload 精确一致，属性本身也逐字节一致**，没有仅凭 subsequence 检验放行属性差异。原根属性仅追加一个说明/空行及两个新证据根的 `-text -whitespace` 规则；旧规则保留。

采用 receipt 的全部 1104 pathHashes 与真实 adopted blobs 相符；`adopted-seal-verification.json` 的 source/mode/manifest/review SHA 与本轮独立计算一致。完整作者 correction seal **1103 条**、prerequisite seal **143 条**，以及 adoption seal **4 条**全部匹配，且均无重复项。作者 full-delivery allowlist 与作者范围一致，manifest 自身的自排除没有伪造“全部载荷已自哈希”。adoption 的 seal 使用项目根相对路径，四个成员与清单相符。

作者 BASE `6b48b2f..` 与 root 采用前 BASE `8bd5e599..` 之间**仅三份协调文档**不同：`DEVELOPMENT_HANDOFF.md`、resume record、既有优先项独立审查公开副本。没有 runtime/schema/依赖差异。B07/config 的生产实现和 contracts、B04 storage/controller、D01 schema/dictionary/checker 均未被本次增量改变；B03 新接缝是已审查范围中的明确修改。因此组合没有绕过先前 B04/B07/D01 的采用基线。

后续 `140d64e..7df5985` 严格只有五份 docs：handoff、resume、B06.1 adoption，以及新 B06.2 prepared brief、FUP follow-up；无 Supplier、WorkerLoop/provider、新 SQL、生产任务策略、档案实现或真实文件引用被补入。

## 复用审查与本轮必要手工阅读

已完整读取并核对公开报告：

- `docs/acceptance/b/task-B-06-seams-independent-review.md`，SHA256 `46f1b2638e8f181c2aaa6a2dcdffa13d76499939211b57904620877002ffb496`。
- `docs/acceptance/b/task-B-06-seams-evidence-audit.md`，SHA256 `ddab0b9759281a1aae9b0bfc5052afa292338a2f94ebe1017d19fcdd93ccbafc`。

两报告明确覆盖完整 `6b48..4dcdc6b` 三提交，源码 SPEC/quality 双 PASS、0 finding；机械审计覆盖 1104 路径、全部 364 历史 TEST XML、13 correction source snapshots、旧保护集合与公开卫生边界。本轮复用该完整源码审查和 raw/XML 历史审计，没有重新人工读一千余份重复 raw/XML。

本轮手工读取当前 handoff/resume/adoption、消费者契约、作者报告、两份独立报告及五份 supplement 文档；读取 root receipt helper/结果、sealing/selected-provenance 工具的有关逻辑。必要组合源码阅读覆盖 `MeetingAccess`、`TaskDomainPolicy`、`TaskQueue`、`JdbcTaskPreparations`、完整 `JdbcMeetingAccess`/`JdbcTaskQueue`，以及 `JdbcExternalOperations` 的实际 queue/fact/retry 路径。对全仓 Java 的 preparation/policy 调用点作 targeted search，确认真实新 policy 注册仍为明确测试注册，没有启动 B06 生产域。

关键组合判断：

- `JdbcTaskPreparations.java:12`、`:29` 绑定私有发行者、精确 DataSource/实际 holder/full specs/purpose；队列消费独立校验自身 source/holder，不能只信 marker 或 policy boolean。`JdbcTaskQueue.java:44`、`:55` 在一份本地 DomainScope 中先身份准备、再 subject、一次完整 lockRoots，重复调用不回到身份/根发现阶段。
- `JdbcMeetingAccess.java:41`、`:48` 保留旧普通路径，新增 strict all-ACTIVE/skip 仅供明确 prepared API；`:85` 在实际 issuer/holder/actor/meeting/完整 coverage 上采用，再执行普通授权。B04 原 FileRegistry 获取/复用 capability 的旧调用没有被替换成晚锁身份。
- `JdbcExternalOperations.java:101` 先准备完整 old/current set；`:122`–`:145` 的 facts-only terminal 例外仍须精确当前执行和原权威验证，不能变成 domain callback/renew/send 权限；旧 owner/epoch/expiry 只在明确例外中放开。普通 valid()、未知/手工状态及矛盾隔离保持各自边界。
- `JdbcTaskQueue.java:121` 的旧三参数 completeWithEffects 继续先 validateOutput 再 effects。当前 public docs 正确把真正 Worker 输出原子提交留作后续任务。

上述是针对集成的实际源码核对；不是声称本轮另做了完整 B06.1 所有源码/测试的第二次全审。

## 计数与最终源码绑定

本轮只读解析三个最终执行目录的 XML 和 source manifests，复用原机械审计的其余历史记录。未执行 Maven/Python 测试命令，也没有 DB/Docker/服务调用。

|最终作者运行|本轮从 XML 交叉核算|
|---|---|
|final-seams-18|1 IT suite，18 方法 / 18 调用，0 failures/errors/skips；另重复 26 unit suites / 179 cases|
|inherited-final|7 IT suites，26 distinct methods / 28 调用，0 failures/errors/skips；故意不执行 units|
|units-final|26 unit suites / 179 cases，0 failures/errors/skips，0 IT|

seam 与 inherited 的方法集合不相交，最终 IT 准确为 **44 个方法 / 46 次调用**。`heldRowDoesNotBlockOtherCandidate(String)[1..3]` 解释两次额外调用。最终 179 unit invocation identities 与此前优先项归档的 **原 179 项集合完全相同**，也与 final-seams-18 附带重跑集合相同；不是 179 新方法，不与先前 S1 112 或重复 RED/GREEN 相加。

三轮最终 source manifests 均恰为同 12 个 Java 路径；每个 SHA 与当前工作文件及 tested commit `553196cacbf27f2fd4dfc3b65d70c535f10f9958` blob 相同。所有采用后的源码又与 author HEAD 相同，故测试绑定没有在采用/补文档时被替换。

最初 3/2/0/0 RED、单独 output-order 控制，以及四个 correction assertion RED 的真实性/设置错误分类，复用已核对且精确封存的独立报告；本轮不把文档审计的格式/路径假设错误当产品 RED。当前文档准确说明 B03 helper 真正调用最新 migrator、验证 12 项后执行零迁移，没有声称完全没调用迁移器，也没有把所存 fixture 状态当本轮新观察的 live 状态。

## 公开接续与 B06.2 准备书

五份补充 docs 的字节、路径、大小/SHA 与补包相符，全部 105 行 prepared brief 已读。它明确 PREPARED/NOT ACTIVATED，没有测试或 DB/实现成功声明；新的接口、SecureDirectoryStream/目录 fsync/atomic rename、有界 JDBC/LONGTEXT 与跨 JVM 排程都是**待激活验证的建议**。本轮没有将它们按“已存在 API”验收，也没有展开未来实现设计。

计划中的 unit 入口已为真实现有语法 `python3 tools/checks/run.py unit BatchFingerprintTest`，没有遗留非法 `run.py java`。当前实现缺 durable index/reader、input_batch 无 profile/version、原上传绑定缺口、完整 B07 facts/生产策略/Worker/API 留待后续的边界均直述。启动必须另给精确 BASE、独有写树和新夹具；不得改写当前 B06.1 原树/seal。准备书中“root ignored 文件”描述原作者准备时态，当前 resume 明确说明其只读编写后被公开，不据此误称尚未保存或已经实施。

当前 handoff/resume/adoption 正确区分 B06.1 通过与完整 B06 未完成；B04-Q401 UNKNOWN、B01-IMM-01 永久 PARTIAL、D01 仅设计采用、main 阶段门禁及公司/运维资格保留。未来文件名/签名以 backtick 形式列作计划，不要求其现时已存在；实际 Markdown 文件链接在最终并集内共 **36 个**，全部目标文件存在（未重新验证所有文内 anchor）。

## 明确保留：既有 LIMIT 64 候选选择资格缺口

独立只读比较确认，owner BASE 的 `JdbcExternalOperations.java:67` 与当前 `:68` 完全保留同一条 `ORDER BY next_check_at,id LIMIT 64` 到期候选 SQL；没有翻页、游标推进或调整被 skip 候选。前 64 个持续 held/不可获取时后缀不可见属于 BASE 已有候选选择限制；本轮新增 unknown-domain skip 也只作用于这个已选窗口。

因此本轮不能证明全局公平性：前 64 个若持续不可获取/未注册，之后的已注册可获取候选仍可能延迟或饥饿。新行为修正窗口内 unknown domain 导致整次 claim 抛异常的问题，没有新增或修改候选窗口算法；我判断这**不是必须在本次 B06.1 精确采用中修源的增量缺陷**，同时不能从“skip unknown”推导无限积压下的可前进性。

root 的公开 ruling 已落在 `docs/acceptance/b/task-B-05-reconciliation-candidate-followup.md`，并同步 handoff/resume/adoption：ACK 原“历史不阻断注册域”按窗口内 skip 理解；原 ACK、旧源码/封存不重写。该后续任务 OPEN，明确无本轮运行时复现/生产事故证据；待独立 B05/B06 候选策略设计、有限精准反例、窄修正和独立审查，再满足外部 worker 激活资格。公开代价也明确：判断有误需额外修正/复审；修复前上述分布可能饿死后续对账，相应运行资格未通过。

这不是将一个本轮新 finding 静默 deferred/parked；是对未改历史限制的独立分类、公开跟踪及当前通过边界。该点没有被本报告的双 PASS 包含为“已修复”或“公平性通过”。本轮没有为它启动无界测试、扩源修复或新的实现任务。

## 原件、main/stash 与公开卫生

独立从 author BASE 的 Git tree 构造保护集合并比较当前 HEAD mode/type/blob 及磁盘内容：**1759/1759** 相同，包括 **1740 旧 artifact、7 plans、12 SQL**。两份用户计划还核对既定 SHA：总计划 `a532dec6e94c53f0e81ae92f14b4f84b6a56cf9d68c37bd6e27d7903f911ffa9`，B 计划 `bfa789f77b5ca90725b2c0f32977993c260ac79ab22c2fff76cd775cc104c862`。没有新增/重排正式迁移、改变旧证据或改写计划。

main 仍为 `0676a21311f2b2193f73a5e096f4a5605ee9b41f`。指定旧 stash commit `bc12e825246c60c12752129c4d512ef9c545758c` 仍存在且仍在 refs/stash reflog 中；没有 pop/reset/clean/rebase 操作。

对主包 **1102 个新增公共路径**作路径与公有内容扫描，未有私有 .tools/env/PKI 路径、私钥、credential-bearing URL、secret/password/token-named XML property。一个宽正则命中是 `tools/b06_owned_fixture.py` 把运行时随机生成的 password 变量拼入私有 env 的源码表达式；经语法/上下文核对，不是嵌入凭证。补充 docs 同样未发现这些公开泄漏形态。seal 工具的 exact-own-secret 扫描作为作者历史检查保留；本轮没有读取私有值，也不假称完成对未知私密值的独立比对。

本轮审计最初对 diff 的 `--binary` 格式和 adoption seal 根目录作了错误假设，均通过实际包格式/项目根相对清单定位并修正；无源文件或证据改写，也不算产品 RED。通过结论基于之后成功的完整字节/mode/清单核对。

## Cannot verify 与实际操作限制

- 没有重新观察作者历史 JVM/DB 进程；通过封存/源码/命令/XML 和原独立审查核对的是记录内容与一致性，不证明所有未记录历史动作，也不重新担保旧/新 fixture 当前 live 状态。
- 公共内容与 seal 检查不等于读取私有凭证后逐值扫描；本轮没有访问私有 env/Config.Env/PKI。
- 精确采用与有限 IT 不证明完整 B06、archive/index/restart、生产 INPUT_BATCH_FILE、实际导入/API/local/S3/Worker 原子结果或保存/编辑/marks/确认；prepared 文档不授这些资格。LIMIT64、公平性/容量及公司/HA/fencing/生产门禁继续开放。
- 本轮没有测试、数据库、Docker、服务、发布、外部发送或 source/index/HEAD/branch 修改，没有创建实现 worktree。唯一新增写入是本 ignored SDD 报告；最终 HEAD/main/干净状态另作只读确认。
