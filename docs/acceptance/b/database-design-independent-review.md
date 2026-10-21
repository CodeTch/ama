- **B00-R1 列表检索字段及共同筛选条件** — **ADDRESSED**。`docs/superpowers/specs/2026-10-21-ama-database-design.md:162`—`:183` 明确持久完整 meeting.id 为编号、当前保存名称 OR 编号 OR 当前 CONFIRMED 名单姓名、as_ci 英文大小写不敏感及 `%/_/=` 字面转义；ACL/governance/scope/q/stage/category/topic/UNASSIGNED/上海日期构成唯一 F，列表、total、stageCounts、topicGroups 使用同一视图与全部谓词。`docs/database/ama-schema-draft.sql:1258`、`:1272` 的投影字段、排序规则、复合名单 FK 与字典 `:1907`、`:1929` 一致；B-03/V008 与 D-02/D-08 的行为/性能验证归属明确。
- **B00-R2 个人模板及专题并发重名** — **ADDRESSED**。设计 `docs/superpowers/specs/2026-10-21-ama-database-design.md:84`—`:90` 明确完整 Unicode NFC＋首尾 White_Space 规范化、完整二进制相等、非唯一 SHA 桶及全文碰撞复核；创建/改名/释放名字先锁既有 owner identity 行，两个不同新 UUID 也串行；多 owner 固定顺序，根锁后不得反拿 owner 锁。`docs/database/ama-schema-draft.sql:1051`、`:1282` 的 LONGTEXT/0900_bin、非唯一 owner/hash 索引及 minutes_template 当前逻辑根支持此方案；历史 template_revision 名称不占当前空间，未加产品字数上限。当前修订指针采用已声明的应用完整性，避免首次根/版本循环 FK 插入。
- **B00-R3 专题说明持久化** — **ADDRESSED**。设计 `docs/superpowers/specs/2026-10-21-ama-database-design.md:92` 定义可选 LONGTEXT、未填写/空串归 NULL，说明与名称同事务，说明单改亦 expectedRevision CAS，冲突整笔回滚。`docs/database/ama-schema-draft.sql:1057` 与字典 `:1611` 定义一致；D-08/V023 与 round-trip/conflict 验证已登记。
- **B00-R4 同确认确定失败重试与新任务归属** — **ADDRESSED**。设计 `docs/superpowers/specs/2026-10-21-ama-database-design.md:120`—`:142` 将稳定逻辑 operation 与追加 execution 分开；`docs/database/ama-schema-draft.sql:366`、`:1297` 保留 `(operation_kind,business_key)` 唯一，移除单 task 所有权，新增执行唯一 `(operation_id,execution_no)` 和 unique(task_id)。确定零效果证明后锁 subject/根/相关 tasks/operation/旧 execution，CAS 新代际、追加新 task/PREPARED，真实 marker commit 后才发送。operation token 同时校验 execution_no、永久递增 reconcile_epoch、owner、expiry；并发双 retry、旧 task/token 迟到、UNKNOWN 不重发与矛盾成功转人工/权威查回均有规则。设计 `:140` 区分固定逻辑 hash 与每次目标/配置完整执行 hash、manifest、publication.revision CAS；同确认更正无效目录不假造新 confirmation/业务键，平台同键失败重试及同键变参能力是明确上线门槛。publication_attempt 与 model_reservation 均引用具体 execution，额度终结不以逻辑操作下一代状态释放旧执行。
- **B00-R5 转写材料及配置输入快照** — **ADDRESSED**。设计 `docs/superpowers/specs/2026-10-21-ama-database-design.md:98`—`:102` 与 `docs/database/ama-schema-draft.sql:1324` 冻结 batch、已保存 source_revision、材料模式/快照、parser/model/prompt/处理配置、版本化可恢复 manifest、revision/epoch/fingerprint；task typed 输入和输出 revision.processing_input_id/producer_task_id 明确对应。流水线每一步新建实际输入快照并继承固定 batch/材料/配置来源，引用具体前步输出及父输入，不能重读当前根或回填旧输入；人工保存/历史恢复继承原处理来源。EMPTY/NULL 是明确规范空集合，D-01/V017 建材料 FK 后才允许 PINNED；同会议/批次/已存在源稿及禁止循环的完整性拥有者已指定。
- **B00-R6 已保存片段多选标记** — **ADDRESSED**。设计 `docs/superpowers/specs/2026-10-21-ama-database-design.md:104`—`:106` 与 `docs/database/ama-schema-draft.sql:1355` 给出 `(revision_id,segment_id,mark)` PK、四值 CHECK 和复合片段 FK，支持每段 0..4 类且不重复。增删标记显式保存新 revision；含空集的完整稳定排序标记集合进入 confirmation.content_hash、generation 指纹和 re-enhancement 源摘要；改标记清当前转写/最终确认、提升 revision/fence、needs_generation，发布只读。V011/C-03 及确认失效/hash 用例已登记。

## New Breakage in the Fix Diff

- **None — 0 Critical / 0 Important / 0 Minor。** 已检查新增 execution 的锁序及隐式 FK 父行要求：标准路径 tasks→operation→execution，安全 retry 预先获取旧 task ID 后按顺序锁齐；独立 quota 路径 operation→execution→model→reservation 不回拿领域锁。新增转写输入/结果表在表级形成双向关联，但 source 仅能指向已存在前步稿，初始输入 source 可空，实例链按反向生成依赖删除；不存在必须互相先插入的非空 FK 环。模板当前指针为应用完整性，历史实体仍 RESTRICT。片段标记/检索投影须随依赖先清理，会议软删及全部内容清除边界仍适用。

## Out-of-Scope Observations

- **None。** 未重新评审未改业务实现。既有 DIFF-01…13、真实接口/并发/性能/部署资格仍由对应领域任务关闭，不因本次结论减免。

## Checks and Evidence

- **范围检查：PASS。** 只读检查 FIX_BASE `c73a7656fc1c5c2cb07d8b94ead68122059b549f` → HEAD `bd43ca31d6b4c263c5f81da83944998492ef943e` 提供的 fix review package、六项原发现、exact brief、作者 fix1 报告与对应文件；未运行 git 改变 checkout/index/HEAD，未派子代理。唯一写入为本报告。
- **结构一致性：PASS。** 只读 Python 载入声明并逐字节核对 DDL/字典/ER；另独立解析字典和 DDL 的全部 **672** 个列定位，类型/NULL/default 一致。完整解析证据 JSON，对照 **85 表、672 列、270 索引定义/404 索引列、99 ENFORCED CHECK、179 FK/186 FK 列**；全部索引顺序/唯一性/无前缀、列属性/排序、CHECK AST、FK 目标及 RESTRICT 匹配，并另验 FK 全部指向显式 PK/unique。
- **完整 metadata diff 归集：PASS。** 从统一 diff 与当前 evidence 反向重建原 JSON，完整解析前后结构，仅 13 个预期受影响表族变化。tables +6；columns -14/+74（含改序字段）；index rows -3/+44；checks -1/+15；FK rows -2/+22，未见额外无关结构变化。DDL SHA256 为 `16d69102d5b775130efac720a31794fcedf0ff27f4f0002ef3fb9756a605d183`，与作者完整新库证据相同。
- **覆盖证据检查：PASS，限结构。** fix1 报告及 `docs/acceptance/b/database-design-review.md:99` 明确列出新空 fixture `ama-test-b01-01baa6c89b08` / 127.0.0.1:45575、MySQL 8.4.6、完整 CREATE＋metadata 命令/exit 0、3819/1452 负向结果；结构输出与提交证据匹配。本复审没有连接 fixture、没有 DDL/INSERT/重置，没有重复作者整套验证或应用 suite；独立执行的是上述离线结构/差异核对，不冒称再次实际建表或行为验收。
- **迁移边界：PASS。** `docs/database/migration-register.md:25`—`:53` 将修正落实到尚未实现的 V008/V010/V011/V012/V013/V017/V023；冻结 V001/V002/V005 与首次旧集合归集门保留，若已应用草案则另分配 V028+ 前向增量，不覆盖旧脚本。新增六项行为测试在 review `:47`—`:52` 已归属，仍待领域实施。

## Verdict

- **Fix round 1: All findings addressed, no new Critical/Important breakage。** R1—R6 全部关闭，new breakage 数量 **0**；B00-1.1 的本轮设计修复可采用。此结论仅关闭六项设计发现，不等于既有实现差异闭环、业务/阶段 B 完成或生产验收。
- **Report:** `/home/tanren/ama/.worktrees/phase-a/.superpowers/sdd/2026-10-21-ama-phase-b/task-B-00-rereview.md`。
