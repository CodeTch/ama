# B-00 independent task review

## Spec Compliance

- **❌ Issues found — 基线暂不可采用。** 6 个 Important，0 个 Critical，0 个 Minor。结构证据真实且一致，但存在已确认业务要求遗漏，以及发布重试协议与表约束冲突；须修复后增量复审。
- **Design/code quality: Needs fixes.** 本评审仅判断设计基线，不批准 B-01…B-08、阶段 B 或真实集成/性能/部署。
- 审查者：独立 B-00 reviewer；日期：2026-10-21。BASE `e8c88d2aaed1a19b0d0202ddce9cb51ee01d84ee`；HEAD `c73a7656fc1c5c2cb07d8b94ead68122059b549f`；设计版本 `B00-1.0-review`。
- 下列产品文件定位均相对只读工作树 `/home/tanren/ama/.worktrees/phase-b-database-design`。评审完整 package `task-B-00-review.diff`；机械元数据完整解析而不逐行打印；没有修改 checkout/index/HEAD、没有执行新 DDL/重置、没有子代理。

## Strengths

- `docs/superpowers/specs/2026-10-21-ama-database-design.md:36`、`:56`、`:99`、`:105`：明确关系完整性拥有者、统一锁序、真正提交后调用、独立 operation token；UNKNOWN/暂时 NOT_FOUND 不视为安全重发证明，避免把本地唯一键误称外部幂等。
- `docs/database/migration-register.md:16`：冻结旧集合先归集、所有环境先到 V001/V002/V005，再运行新版本；没有 outOfOrder/repair/旧脚本改写捷径。V003/V004 留空，后续任务占位版本有统一映射。
- `docs/superpowers/specs/2026-10-21-ama-database-design.md:73`、`:83`、`:113`、`:121`：草稿名单与有效 ACL 分离、会话代际撤销、候选与当前指针分离、顺序提交 outbox、文件引用和删除 epoch 等核心边界清楚；并发验证仍归领域任务，不以建表成功代替实现验收。
- `docs/acceptance/b/database-design-review.md:39`：DIFF-01…13 保留了真实实施差异及拥有者/迁移/回归，不将作者自审当成独立批准。

## Important — must fix before baseline adoption

### B00-R1 — 列表检索字段及共同筛选条件偏离已批准要求

**位置：** `docs/superpowers/specs/2026-10-21-ama-database-design.md:134`、`:142`。

查询设计目前写“名称/摘要/正文包含检索”，但绑定架构 `docs/superpowers/specs/2026-10-21-ama-architecture-design.md:280` 和需求矩阵 `docs/superpowers/specs/2026-10-21-ama-requirements-matrix.md:94` 明确要求“会议名称/编号/已确认人员姓名”，任一命中、英文不区分大小写。当前没有说明编号来源、确认名单姓名查询/投影，也未把个人专题、未归集专题、分类条件纳入列表/total/stageCounts/专题分组的同一查询约定。只对现有字段写 LIKE 转义不能满足这些业务条件。

**修正：** B-03/D-02/D-08 明确编号采用哪个持久字段、确认名单使用哪个有效版本；给出权限过滤后名称 OR 编号 OR 确认人员姓名的查询/投影和大小写语义，以及共同的分类/专题/未归集/日期/阶段过滤。若保留正文搜索，区分独立能力，不能替换列表搜索字段。补对应索引/迁移归属与同条件列表、计数、分组测试；保留现有 `%`、`_`、`=` 字面转义。

### B00-R2 — 个人模板及专题没有并发安全的名称去重方案

**位置：** `docs/database/ama-data-dictionary.md:810`、`:1584`；`docs/database/ama-schema-draft.sql:526`、`:1045`。

模板只约束 `(template_id,revision)`，专题只有 `(owner_id,id)` 普通索引。设计也未定义名称规范化、同名判定或在创建/改名时锁定同一用户命名空间并完整比对的事务。不同随机 ID 的并发请求可以创建相同个人名称；根 CAS 不能保护“两个尚不存在的不同根”。原需求明确模板名称去重（`designDocs/智能会议助手一期需求说明文档.md:736`）及个人专题不可重名（`:1299`），架构 `:278` 还要求使用摘要时不能省略原文碰撞复核。

**修正：** B-07/D-04、D-08 明确个人范围的名称等价规则和串行化机制。可使用可执行的用户级锁＋完整规范化名称比较，或命名登记/摘要索引＋碰撞复核；不要为了唯一索引新增产品名称字数上限。说明模板历史版本与当前逻辑模板的重名关系，覆盖并发新建、改名冲突、不同用户同名及超长名称。

### B00-R3 — 专题说明没有持久字段

**位置：** `docs/database/ama-data-dictionary.md:1584`；`docs/database/ama-schema-draft.sql:1045`。

`topic` 仅有 id、owner_id、name、revision、created_at，没有说明字段，也没有承载说明的关联实体。原需求专题展示、新建及修改均包含“说明”（`designDocs/智能会议助手一期需求说明文档.md:1290`、`:1296`、`:1297`）。这是已确定的一期字段，不能留到 D-08 编迁移时再猜。

**修正：** D-08/V023 补充说明的 LONGTEXT 字段、空值/默认语义、创建及修改 CAS 规则，同步字典/DDL/ER/声明及元数据证据，并补持久化后重新读取的验收用例。

### B00-R4 — 同一确认版本确定失败后的发布重试无法按既定表约束落地

**位置：** `docs/database/ama-data-dictionary.md:577`；`docs/database/ama-schema-draft.sql:364`；`docs/superpowers/specs/2026-10-21-ama-database-design.md:97`、`:109`。

设计要求用户重试创建新 task/producer_key，发布业务键又固定为 `publicationId + confirmationId`。但 `external_operation` 唯一约束 `(operation_kind,business_key)` 且只有一个非空 owning `task_id`。第一次发布明确失败、已证明零外部效果后，用户不改正文而重试同一确认：新 task 的 beginExternal 无法再插入同键 marker；复用旧 operation 则仍归旧 task/attempt，现有所有权/对账协议没有合法接续步骤。换一个确认 ID 也不是通用解法，因为同一 meeting_revision 的最终确认唯一。设计目前只解释失败后换新 confirmation，没有解释正常的同确认重试。

**修正：** B-05/B-08 明确逻辑外部操作与执行尝试的区分：例如稳定 operation/business key 下追加可 fenced 的执行尝试并关联新 task，或给出保留单 task 的专用安全重试协议并消除现有冲突。不能删除历史操作、改变稳定发布键或对 UNKNOWN 盲重试。补“明确零效果失败→原确认重试→成功”“并发双重重试”“旧发送者/旧 token 迟到”验证，并同步约束及迁移登记。

### B00-R5 — 转写处理没有可追溯的材料与配置输入快照

**位置：** `docs/database/ama-data-dictionary.md:701`、`:739`；`docs/database/ama-schema-draft.sql:454`、`:479`；`docs/superpowers/specs/2026-10-21-ama-database-design.md:83`。

`input_batch` 保存主文件集合和 fingerprint，`transcript_revision` 只关联 batch/parent 与 fingerprint，没有关联该次处理的 material_snapshot、提示词/模型配置版本或其他可恢复输入清单。`generation_input` 是纪要输入且强制依赖已确认转写，不能承载确认之前的转写增强输入。B-06 契约已经接收 materialSnapshotId，C-03/D-01 的重新增强必须保留原始输入版本；只留哈希和可变 meeting 当前指针，之后无法知道旧增强稿用了哪个材料快照或配置，也无法证明其来源属于那次处理集合。

**修正：** B-06/C-02/C-03/D-01 为处理/增强建立不可变 typed 输入快照，或在现有输入实体补对应固定版本引用和有版本的清单；B 阶段 NULL 材料仍按规范空集合处理，D-01 解锁非空并补 FK。task 和结果 revision 明确引用该输入，补材料应用后重增强、旧结果迟到、恢复/历史查看仍使用原输入的断言。

### B00-R6 — 已保存转写片段标记没有存储位置

**位置：** `docs/database/ama-data-dictionary.md:764`；`docs/database/ama-schema-draft.sql:496`。

全部 79 表/612 列中，转写片段仅保存正文、顺序、来源、时间、speaker，没有“关键、结论、争议、重点生成”多选标记字段/关联表。原需求 `designDocs/智能会议助手一期需求说明文档.md:484` 的片段标记要求保存并纳入快照，且确认后改变影响生成的标记必须重新确认。不能用纪要 `minutes_element.category` 替代输入片段标记，也不能只放浏览器状态。

**修正：** B-06/C-03 给出按 `(revision_id,segment_id)` 冻结的标记模型、允许值/多选唯一约束、保存产生新修订的规则，以及标记进入确认内容哈希/生成输入指纹的规则；同步分期迁移和确认失效回归。

## Verification and scope limits

- 完整读取 review brief、exact task brief、作者 report、冻结输入库存、B02/B05 pause handoff、B…E task-contracts；对照架构 6（含 6.3 全部十组实体）、8—10、12、14、已批准产品补充、A-02 决定台账及需求矩阵。台账旧 pending 措辞以已批准 supplement 为准，没有重新要求产品批准。
- 全部七文件从提供的 review package 分段审查。DDL、字典、ER 与声明清单全文程序比对；另独立解析每表字典列和 DDL 列定义，612 个唯一表/列定位完整。工具输出第一次受长度限制的中间声明段已补读；没有依靠摘要代替未读 hunk。
- 完整解析提交的 12,349 行 evidence JSON，核对 79 表、612 列、247 索引定义/363 索引列、85 ENFORCED CHECK、162 FK/166 FK 列。类型、精度、顺序、NULL/default/extra、collation、唯一性/列顺序/无前缀、CHECK AST、FK 目标与 RESTRICT 均与声明及实际证据一致。DDL SHA256 为 `a2bdcdaf65abc29f3a2c6e498c95220aeb3fe9b09698c529b831484712e999df`。
- 为证据真实性及尾空格语义疑点，只对作者专属 `ama-test-b01-b901c0503cf5` 做 docker inspect 和 SELECT：先验证本工作树 helper 所有权标记、fixture label、127.0.0.1 端口及镜像摘要，再读取 information_schema 五类完整结果；逐项与提交 JSON 完全一致。实际版本 8.4.6；`'ID' COLLATE utf8mb4_0900_bin = 'ID '` 和 `= 'id'` 均返回 0；COLLATIONS 确认 0900_bin 为 NO PAD。未调用会写 evidence/负向 INSERT 的 verify 命令，未新增任何 DDL/数据或重置 fixture。
- 对三个冻结 SQL 做只读 SHA256，分别与 register 的 V001/V002/V005 哈希完全一致。未跑 git 改变状态的命令、未重跑应用 suite、未连公司/生产、未调用其他任务的 MySQL。
- 明确风险的额外输入检查：查询/个人名称完整性回读原需求相关段；转写输入、历史与片段标记回读原需求模块二及 B06/C03/D01 契约。不存在“结构创建成功所以所有业务完整”的推论。
- ⚠️ 实现差异 DIFF-01…13 仍待各领域修复及回归；本次 metadata 证据不能验证域级权限/死锁/迟到响应、性能及真实外部协议。修复上述设计问题并获得独立采用结论后，也不得提前标记 B-01、B-02、B-05 或阶段 B 完成。

## Assessment

**Task quality: Needs fixes.**

元数据验证扎实，迁移保护、权限、外部未知结果及删除边界清楚；但六项已确认语义/结构缺口需要在设计采用前关闭。应定向修正并仅复核受影响的设计、约束和证据，避免重跑无关整套测试。
