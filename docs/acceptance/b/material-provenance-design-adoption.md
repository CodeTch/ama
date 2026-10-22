# D01 材料来源补充：设计采用记录

日期：2026-10-23。采用 B00-1.4-proposed 材料来源技术设计及 R1 修正，作为后续增量迁移与运行时任务的设计依据。正式迁移仍通过 V013，新增三表/两列尚未分配或应用正式迁移；本记录不宣告 D01、B07/B08 或材料功能验收完成。

## 审查与采用

[原完整独立审查](../../../artifacts/checkpoints/2026-10-22-development-pause/task-D-01-provenance-design-review.md)覆盖 `91774bd4eeca2c3f3233f0e6733b32c2abef28fc..214a63d978e4c04c0b3aec1c99d8d0ce3c093d9d`，唯一 P2 R1 为相互矛盾的 exact context 根规则。[修复报告](material-provenance-design-fix1-report.md)对应 `214a63d978e4c04c0b3aec1c99d8d0ce3c093d9d..212c794b497631b13b395c4d1436cbe306208fd4`。[接续独立限定复审](material-provenance-design-fix1-independent-review.md)结论 SPEC/quality PASS、R1 ADDRESSED、0 新问题。协调采用以完整初审与此次限定复审的组合为依据；原会话身份无法恢复，接续作者和独立审查者均已明确标记。

修复提交已精确采用至接续分支 `codex/phase-b-resume-20261023` 的 `01ab5380f308233e4b7932096c7df2bae07e0d83`。43 个路径中 42 个内容与作者最终对象相同，另一个 `.gitattributes` 保留原集成规则和作者规则的顺序；正式 SQL 与用户计划未改字节。主分支阶段验收门禁继续有效。

## 已关闭的设计问题

每个域 D 的 context 成员为所有准确纳入要素的 basis roots B(D) 与原有准确 usage roots U(D) 的并集，不受要素证据数量影响。要素 basis C1、全部证据 C2 时必须包含 C1+C2。

- PINNED/ROWSET v2：已保存输入要素集的全部纳入 basis，加 set evidence/directory contexts；排除 body evidence/applied-exclusion usages。
- REVIEW_SOURCES：只包含所核对要素的 basis 和准确相交证据；body-only 核对没有 element basis。
- FINAL：所有冻结要素 basis，加既定 body/set evidence、directory 和 applied-exclusion contexts。

每个到达 basis 都承担完整 context、全部 selections、准确 parser profiles、archive/confirmation/stored-input/fence 验证与历史保留义务。成员按完整 PK 排序去重，缺失、重复、额外成员拒绝；basis 不制造 EVIDENCE、不增加 usage 数量、不授予来源支持。草稿没有自身 fingerprint/TaskSpec 循环。

## 证据与边界

新离线向量 82 个断言、纯渲染/保全 184 个断言通过；这些是原作者真实执行并由复审核对的记录，本次采用没有重复运行。旧集合规则断言失败证明静态矛盾，不能表述为已执行的坏 final、哈希碰撞或 SQL 丢失。短 synthetic IDs/合成 SHA/sorted JSON 的缩减模型不代表完整 wire、真实档案、ACL、解析器或运行时资格。

原 65 路径中 59 项保持原字节，六项为授权的当前规范/字典/渲染器/属性覆盖；35 项不可变文件、原提案/ACK、旧 metadata、93 个旧白名单向量和 6,871 字节旧草稿向量均保留。96 表/727 列的审阅 DDL 未变，本轮没有访问数据库或重跑原 relational cohort。

下一步仍需中央登记并独立 ACK 正式增量迁移，再做真实持久来源、精确解析选择、复制/部分替换/恢复/最终确认、权限撤销及历史清理并发验收。历史 B00-1.3、所有已应用 SQL 和失败证据继续保留；不得猜版本、repair、outOfOrder 或重置旧库。