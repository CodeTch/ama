# D01 / B00-1.4-proposed fix1 — 接续独立限定复审

日期：2026-10-23（Asia/Shanghai）。审查者：`/root/d01_fix1_review`，**明确标记的接续独立审查者**，不是本次修正作者。原审查会话身份无法恢复；本次依据原审查文档接续其 R1 复核席位，不声称恢复了原 agent 身份。未派子代理。

## 双结论与问题

**SPEC：PASS（限定 R1 与本修正新增问题）。Technical quality / design correctness：PASS（同一限定范围）。R1：ADDRESSED。**

新问题：**无 P0/P1/P2/P3 finding**，因而无新增问题的 file:line 项。原 P2 R1 的两个不相容 exact-membership 配方已统一；此结论来自实际规范、脚本和完整固定 diff 的核对，不以作者报告自称 PASS 代替判断。

本结论可以用于关闭该 R1 设计修正的独立复审。它不自行采用整个 B00-1.4-proposed，不分配正式迁移版本，也不关闭 D01/B07/B08/D03–D07 等后续运行时验收。

## 范围与证据 custody

只读作者树：`/home/tanren/ama/.worktrees/phase-b-provenance-design`。

- BASE：`214a63d978e4c04c0b3aec1c99d8d0ce3c093d9d`。
- HEAD：`212c794b497631b13b395c4d1436cbe306208fd4`。
- 完整范围：`214a63d978e4c04c0b3aec1c99d8d0ce3c093d9d..212c794b497631b13b395c4d1436cbe306208fd4`。
- 固定 review package：`task-D-01-fix1-review-package.diff`，**386,542 bytes**，SHA256 `285e22667fb7ebf7e0648ef263bf0bbd2fe15e222ade406c16c40136e2d0085a`。

固定包完整读入一次并按 43 个路径拆为有界阅读段；当前规范与新增三个脚本逐项阅读，重复 JSON/历史复制材料按原始字节、结构及来源程序核算。完整补丁部分 375,184 bytes 与 `git diff --full-index --unified=10 BASE..HEAD` **字节完全一致**。包前置 final inventory 的 43 个 size/SHA 均与 exact HEAD Git blobs 相同；43 个工作区文件也均与 HEAD 相同。作者树审查前后均 clean，HEAD 未变；本审查的 `git diff BASE..HEAD --check` 退出 0，stdout/stderr 为空。

需求来源已核对：fix1 brief SHA `b729049728ec174e312b8d712f4f9d5235ca813e3e14d41be9d918dbb87b6b90`；原 review SHA `0803c06ab1ca427e9d8b2d2007704c07cf85cf30d959da3649374cc1574b74b0`；原 R1 context-roots 证据 SHA `6644ddf1cd0e70d334cc8d35511b309a9b4919ab1fbfe62889656c6d95dfc3c2`。阅读作者 root SDD 报告并核对其前缀恰为 tracked consumer report 的原字节；tracked report SHA `dbc666f9ee299cc231f4d31b8a9cb2426d80ee32373a52064652e1b5533bc5f7`。root SDD 的额外 postcommit seal 与固定 HEAD 相符。

## R1 核对结果

主要文件均为作者树下相对路径；行号为冻结 HEAD 的一基行号。

|绑定要求|实际证据与判断|
|---|---|
|所有 included element basis，无论证据数量均为根|`docs/superpowers/specs/2026-10-22-ama-material-provenance-addendum.md:57`、`:128`、`:157`、`:163`、`:217` 明确规定 B(D)∪U(D)，包括 element C1 / 全部 evidence C2 时必须保留 C1+C2。原 zero-evidence-only exact-union 句已删除。|
|PINNED / ROWSET v2 精确域|同文件 `:149`、`:159`：仅所有 included saved input-set bases 与 set evidence/directory contexts；排除 body evidence/applied-exclusion usages。current raw sourceContext 和 appliedContextId 保留在已有 top-level 投影。|
|REVIEW_SOURCES 精确域|同文件 `:134`、`:150`、`:160`：仅 selected reviewed element basis 与 exact intersecting evidence；body-only review 没有 element basis，不携入其他要素/目录/排除根。|
|FINAL 精确域|同文件 `:136`、`:151`、`:161`：所有 included frozen bases，加既定 body/set evidence、directory、applied exclusion usages；未新增独立 body basis 或 unrelated context。|
|完整 FullRow14 及依赖闭包|同文件 `:163` 要求每个 basis 与 usage context 同样具有完整 context、全部 snapshot selections、selected profiles、confirmation/material/catalog/parser/archive、stored-input/fence 验证及 §6 retention。无 usage 不能逃避。|
|全 PK 排序、去重和 exact membership|同文件 `:165`、`:217`：实际完整 PK、unsigned UTF8 排序；shared roots 不重复行；重复 supplied PK 在集合构造前拒绝；缺失/额外 context/selection/profile 拒绝。|
|不改变证据或支持语义|同文件 `:57`、`:165`：basis 不伪造 EVIDENCE、不改 usage cardinality、不授予 SOURCE_SUPPORT=SUPPORTED。独立 evidence origin 保持。|
|草稿无自身循环|同文件 `:148`、`:153`、`:155`、`:165`：无 own fingerprint/TaskSpec，raw current context 在顶层，generated inherited basis 也必须是持久化图依赖祖先；不按时间或 UUID 推断。|

同步点一致：`tools/checks/database_design.py:15` 的共享说明用于 dictionary 和完整 ER；`:429` 的 element policy 增补仅解释 basis。对应 `docs/database/ama-data-dictionary.md:9`、`docs/superpowers/specs/2026-10-21-ama-database-design.md:325`、`docs/acceptance/b/material-provenance-design-prerequisite.md:19` 均保持同一配方。对当前相关规范/checker/register/prerequisite 的 targeted search 未发现仍然生效的 zero-evidence-only 配方；历史报告中对原零证据测试的描述不被当作新规范。

正面质量判断：修正集中在一条明确集合规则；renderer 共享一个规范说明，减少字典/ER 漂移；完整 diff 中 schema declaration、DDL、ER 实体/边没有新增变更，未引入 runtime 或版本分配。新向量用 literal expected memberships 核对 root recipe，并保留旧 recipe 的反例，证据边界有明确标注。

## 82 向量与 184 保全记录的实际核对

本审查**未重跑**作者已完成的向量/渲染测试，未执行 DB/Docker、原 24 relational tests 或 broad cohort；也没有因代码阅读产生需要额外小离线行为 probe 的未覆盖疑问。以下是对已提交原始记录、对应源文件和 committed JSON 的审查与只读核算，不声称为 reviewer fresh test execution。

`artifacts/phase-b/b00-provenance-design-fix1/final-verification-commands.json` 记录：

|记录|实际日志/源的对应关系|
|---|---|
|old-recipe-replay，expected exit 1 / actual exit 1|stderr 指向最终 runner 第 203 行，确为 `expected C1+C2 ... produced ['C2']` 的 assertion；非 setup error。runner SHA 与最终源一致。|
|vector-final，exit 0|stdout 为 82 assertions，六个 domain/target 记录；stderr 0 bytes。runner SHA `7846b63c73d8941bdd25091deca63ee2e1f3dbdfbbd0cf8fc5eac600f6e5fdce` 与提交源一致。|
|render-preservation，exit 0|stdout 为 184 assertions / 59 original paths / 35 immutable paths；stderr 0 bytes。runner SHA `0f1146ba9c83918be6ec7e837de74900333d59dc7d63fb288d7444a753abad55` 与提交源一致。|

完整阅读 `context_roots_vector.py:35`–`:193`：fixture 为 nonempty E1 basis C1、所有 E1 evidence C2，generated zero-evidence E0/C3、manual EM/C4、directory/C5、body/C6、current/applied exclusion/C7、unrelated C8；每个 context 有两条显式 selection 与对应 profile。`scope` / `projection` 的域筛选、basis union、完整 PK 排序和 `validate` 的 missing/extra/duplicate/no-fake-usage 检查均与限定要求相符。其记录为 3×22 个主域断言 + 3×3 个 selected-target 断言 + 7 个 draft/ancestry 断言 = **82**，JSON check list 82 个且均唯一。

独立读取已提交 vector JSON，逐域核算 rows/usage、完整 PK 排序唯一性、selected document/profile 对应和其缩减 JSON digest；结果与 literal 期待一致：

|域/目标|context IDs|evidence 数 / usage 数|selection 数 / profile 数|
|---|---|---|---|
|PINNED|C1,C2,C3,C4,C5|3 / 4|10 / 10|
|REVIEW E1（仅 EV1 与 [0,10) 相交）|C1,C2|1 / 1|4 / 4|
|FINAL|C1,C2,C3,C4,C5,C6,C7|4 / 6|14 / 14|
|REVIEW E0，零证据|C3|0 / 0|2 / 2|
|REVIEW EM，人工 basis|C2,C4|1 / 1|4 / 4|
|REVIEW BODY|C6|1 / 1|2 / 2|

C1 在三个主域都无 usage，仍纳入完整 context/selection/profile；C8 从所有域排除。catalog SHA/profile SHA/精确 parser selection/basis ID 的敏感性、missing/extra/duplicate 各类 rows、permutation、no-fake-EVIDENCE、current/descendant/cycle/missing-saved-input 拒绝的命名断言均有源与记录对应。draft 的 top-level C7 raw context 具有两条 selections/profiles，继承图不含 C7，也没有 own fingerprint/TaskSpec。

完整阅读 `verify_fix1.py` 与 `package_seal.py`。184 的来源可静态核算为 14 个 schema/render/recipe 检查 + 2 个 inventory 检查 + 65 个 BASE 原始清单校验 + 59 个保留校验 + 35 个 immutable 校验 + 2 个旧向量校验 + 5 个原 review/brief 复制校验 + 2 个变更范围/属性校验。184 check entries 中共享说明的同名检查执行两次，分别针对 spec/dictionary，不是伪造额外覆盖。

初始 76-assertion logs、初始 old-recipe-red 的第 194 行 traceback 仍作为历史记录保存；最终 replay 的第 203 行与 source SHA 才绑定最终 runner。`before-eofattr-*` 和最终保全 JSON 的 old65 before/after 记录仅 `.gitattributes` current bytes 不同，与 narrow CR/blank-EOF 属性补充一致。最初 diff-check exit 2 明确是复制原 reviewer 源的历史 blank EOF；最终记录为 exit 0，未被误称为产品 RED。

## 历史字节与交付包保全

本审查直接从 exact BASE/HEAD Git blobs 及工作区字节核对，未依赖作者 audit 自称成功：

- original65 inventory SHA `5fd3020b7e2866e9aa56f0c1c77ebcb1a83be06e6cba3bdb5bc8e41a7d42df70`，65 个 BASE size/SHA 全部相符；**59 个原字节不变**。六个例外恰为 `.gitattributes`、当前 prerequisite、dictionary、当前 database spec 的说明、provenance addendum、checker-renderer，均属授权覆盖。audit 中每个 before/after size/SHA 与真实 blobs 一致。
- **35 个 immutable 文件**均与 immutable map、BASE 和 HEAD 同时相符。原 proposal/clarification/ACK/reports/manifests、raw metadata/probe 及 B00-1.3 四个历史源均保留原字节。完整 diff 中无 formal SQL/runtime/backend/POM/API/main-plan/原始需求/原型改动。
- 未改 review DDL：SHA `4c6bd89301ca6316f4469e99db333bc5440fa8dd330a907cfc685d79ff656a33`。old93 whitelist JSON 保持 93 项，文件 SHA `19dc15af6b5c4e836ef07042dcef248c38199da2ad05fc76502fa7e5eafebc0d`。旧 draft vector 解码仍为 **6,871 bytes** / SHA `015a3a30f9970d41ef3ac45209794c7a4367e494b53a4f314f6cd0f0d3728112`；原 hex/JSON/summary 也属于已保全 original65。
- 新 archive 的 6 个 original-review copies 与 checkpoint 原文件**逐字节相同**，含带原 CR/blank EOF 的文件；新 archive 以 `-text` 保存，whitespace 例外仅针对对应历史复制文件。
- fix1 allowlist：43 个无重复、unsigned UTF8 顺序路径，SHA `785e632f81facf379e8762e2fadc1e43422ae0285182a1772b0c090f87d6c93d`；与 exact Git range、包 final inventory 相同。manifest 41 个 covered file 的 size/SHA 全部匹配 exact HEAD；仅排除 manifest 自身与 tracked report，通过固定 HEAD/包 inventory 封存两者，未引入自哈希循环。manifest SHA `128158fc98774a3e504b4dad5814942fb48f130f10801e07d2886e9edf21bd3e`。

43 路径均有覆盖归属：6 个既有当前规范/属性文件逐项 diff 阅读；36 个新 archive 文件中 3 个新脚本逐项阅读、6 个历史复制文件作来源/字节核对、27 个 JSON/命令/日志/allowlist/seal 记录作结构和完整字节 accounting；另 1 个 tracked consumer report 与 root SDD 前缀/外部 seal 核对。未把重复 JSON/旧 metadata 的机器保全核算表述为重做原完整设计手工审查。

## 限制与 declined-to-judge

1. 82 向量是短 synthetic IDs、合成 hashes 和 sorted-JSON sensitivity 的**缩减纯模型**；不编码完整规范 SourceGraph/profile wire，不是 archive-valid raw fixture，也不验证真实 stored fingerprint/fence/ACL/parser。其访图检查只是既有 acyclic 义务的缩减 witness，support 相关断言仅证明 basis 未制造 usage，没有实现真实业务支持判定。184 是 pure render/preservation，不能变成 runtime/DB 成功。完整 wire/runtime 行为由此 diff 不能验证。
2. 最终命令/source SHA/exit/stdout/stderr 可核对的是已封存的作者执行记录；本审查未重新观察或重跑其进程。初始 76-run 与初始 RED 没有最终 source 的同一字节身份，不能声称最终源是它们当时的完整复原；最终 pinned replay/82 记录提供当前源的对应证据。
3. 原完整设计的未修改行为、旧 SQL/metadata 的真实数据库运行状态、原 24 relational probes、broad cohort、真实 archive retrieval/retention concurrency、API/worker/revocation 行为不在本轮判断范围；原因是本轮明确限定 R1/新增破坏，历史材料仅作 byte preservation。正式版本分配、adoption、peer integration 同样不由此复审决定。
4. 原审查会话身份本身无法由提交 diff 恢复；本报告已明确接续独立审查者身份，没有冒称原会话。

未静默排除其他在 R1/新改动范围内的具体可疑行为。没有待修复项、待重跑项或需要用户补充的证据缺口阻止本次限定双 PASS。只写本报告及作者树外临时阅读段；未改作者 source/index/HEAD/branch，也未派子代理或访问 DB/Docker/network。
