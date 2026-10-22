# B07 纯 S1：协调采用记录

日期：2026-10-23。采用已完成完整初审和 F1/F2/F3 修复复审的纯 S1 实现，开放其原始配置、确认哈希、来源图与业务指纹接缝供 B06 使用。该采用是纯事实一致性组件门禁，不代表 B07 生成运行时、完整材料能力、UI 或阶段 B 已验收。

## 审查与精确集成

[原完整独立审查](../../../artifacts/checkpoints/2026-10-22-development-pause/task-B-07-pure-s1-review.md)覆盖 `8da440d39e1b8e8dcdfc34722908d29b5aa3aa8a..7e052a88fcd03d60523d013839701dd35a7f2b48`，发现 F1（P1）及 F2/F3（P2）。[修复报告](minutes-pure-s1-fix1-report.md)对应 `7e052a88fcd03d60523d013839701dd35a7f2b48..13b8f1d16f85787c12626faa41c7e511403f16f9`。[接续独立限定复审](minutes-pure-s1-fix1-independent-review.md)逐项确认 F1/F2/F3 ADDRESSED，限定 SPEC/quality PASS，0 新问题。协调采用基于完整初审与本轮限定复审的组合，不以单次测试通过替代审查。

作者和独立审查者均为明确标记的接续角色，没有冒称恢复原会话身份。修复提交已采用至 `codex/phase-b-resume-20261023` 的 `93b2270fb98482cb0d5470d5f80a2275100abd02`。集成只发生 `.gitattributes` 追加规则冲突；保留已有 D01 与原始规则后追加 S1 修复归档规则。79 路径集合与作者修复一致，78 个非属性载荷逐字节相同，两边属性规则顺序均保留。正式 SQL 和用户计划未改动。

## 已关闭的问题与消费者变化

- F1：一个操作共享事实索引、来源图状态和原始配置会话；保留所有递归输入图的 intrinsic confirmations，校验完整确认、processing chain、存储指纹与 task fence。直接存储验证、外层生成、review/final 对同一损坏历史一致拒绝。
- F2：缓存完整子树高度，在每条复用路径检查最长深度；保留迭代遍历、循环/取消检查和累计唯一节点预算，覆盖缓存非叶子与任意给定顺序。
- F3：Artifact 改用不可变、可先检查大小的 ArchiveBytes，移除裸 byte[] 构造/复制访问旁路；初次 capture 必须先检查实际 maximum/remaining，消费者检查大小后复用同一隔离载体。

[新的消费者补充](../../../artifacts/phase-b/minutes-pure-s1-fix1/consumer-handbook.md)给出准确 API 和预算语义。分离使用者必须在采集时传入实际操作界限，通常共享 ArchiveReadState；API 不会从事后较小预算推断之前的采集策略。原手册和原报告保持历史原字节，当前补充覆盖其相关 API 说明。

## 实际证据与限制

旧代码上的 8 个新图用例真实产生 5 个断言失败，覆盖外层 compute/review/final 的坏确认漏验及 shared leaf/reused nonleaf 深度旁路。最终原 13 套共 112 个唯一用例全部通过：原 97 项完整保留，加 15 项新增；规范入口 25 项是其子集，不累加，8 个 reactor 模块经过编译。原始日志/XML/源码哈希已经由独立审查核对。

F3 是静态数据流证明及小型边界/隔离行为验证，没有声称分配观察的 runtime RED，也未做巨大内存压力。02/03 编译设置失败和 09 审计脚本失败均保留，不算产品 RED。RED 测试源码后来按已保存哈希重建的事实已明确披露。聚焦、完整及规范入口执行的重叠均保留原计数。

原 273 路径历史对象及 271 个原 payload 均保持 Git 原件，当前 15 个原源码/属性路径为授权修正；所有旧 artifact、报告、手册、资源和独立审查证据未覆盖。新归档在 `artifacts/phase-b/minutes-pure-s1-fix1/`。

本轮不证明权威数据库事实完整性、存储/索引真实性、档案发布持久性、ACL、事务/租约/CAS、模型质量或真实格式能力。IMPORT/TXT/EMPTY/FULL 等已采用边界继续有效；D01 设计采用没有使其运行时自动可用。受控首版保存与负责人核对来源的 UX 仍由后续 B07/UI 实现。B06 必须另外交付真实配置来源、完整事实读取、锁序组合和 Worker 结果原子提交。
## 接续集成验证

在精确采用提交 `93b2270fb98482cb0d5470d5f80a2275100abd02` 上执行 `python3 tools/checks/run.py unit`（仅指定公开 Java21 路径，未加载私有夹具环境），26 套共 179 项通过，0 failures/errors/skips，8 个 reactor 模块成功。原始日志、26 份 fresh XML 与源码哈希见 `artifacts/phase-b/resume-2026-10-23/`；179 包含上述 112，不合并为独立新用例数。输出有未改动的 DeclaredErrorResponse.java 既有 deprecated API 编译提示；没有新改动源码警告。此运行没有执行 IT、数据库或 S3 服务。