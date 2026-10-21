# B00 设计差异实施状态

2026-10-22；采用基线 B00-1.3-review（原1.2及R1—R6保留）。此表记录协调状态，原始 [设计差异](database-design-review.md) 和独立评审保留。设计通过、代码通过与真实公司/运维资格分别记录；未执行测试不计通过。总计划和B计划保持用户原字节。

|差异|当前状态|代码、证据和剩余门|
|---|---|---|
|DIFF-01 审计标识/索引/微秒|新受控空库范围已修正并独立评审|V006 f36…；[实施](audit-metadata-delta.md)及其独立初审/复审。B01-IMM-01历史应用后改字节偏差永久保留PARTIAL，不以本功能修正消除；旧checksum/失败/未知库先单独审阅恢复，不repair。|
|DIFF-02 草稿/有效ACL混合|OPEN|V007拒绝无来源旧数据；V008有效grant/roster结构已真实验证并独立采用（ff22197）；B03/剩余B02 MeetingAccess完整运行时+fix1已独立采用，owner/有效确认来源/撤权/admin正文拒绝有35实际领域用例；文件再鉴权仍待B04，实际公司身份联调仍待提供。|
|DIFF-03 会话代际/精确ID|受控身份前置已修正并独立评审|V007/JDBC每请求共享解析、generation、身份变更和双节点18测试；[实施](identity-prerequisite.md)、[独立评审](identity-prerequisite-independent-review.md)。实际SSO签发/停用传播及公司协议仍未资格验证。|
|DIFF-04 外部调用前持久marker|OPEN|V010已按冻结03e89cc8…首次正式成功，完整实现及fix1 b126db2已独立复审采用；marker/commit-ACK/execution/进程故障及迟到事实隔离证据已核对，真实公司零效果/截止证明仍待资格验证。|
|DIFF-05 统一锁序|OPEN|B05显式flush-last、subject/root/task及独立model事务已有真实MySQL交付证据，独立复审采用，observe独立事务与整事务rediscovery契约明确；各领域消费者和删除路径仍未启用，不能据基础层测试关闭全局锁序。|
|DIFF-06 producer/attempt/上限/typed输入|OPEN|V010首次成功元数据与B00-1.2相等；实际队列/crash cap/同键异体/旧尝试写回已交47个不同IT的组合证据，完整实现及fix1已独立采用；领域typed输入消费者仍待各自评审，不以旧Worker3单测验收。|
|DIFF-07 提交顺序/consumer前缀|OPEN|序列与显式最终flush/consumer已有延迟commit、rollback、前缀ACK、lease及争用交付证据；基础实现已独立复审采用，真实远端投递/后续领域消费仍OPEN。|
|DIFF-08 额度deadline/配置/执行绑定|OPEN|新执行绑定/NULL保守占额/批量配置完整预锁与settlement实现已独立复审采用，旧草稿保全不复用；实际公司额度/cutoff仍未资格验证，不释放未知占额或自动开启模型容量。|
|DIFF-09 文件/DB跨资源边界|OPEN|ports/native基础设施和[选定清单修复](local-upload-selection.md)已评审；JavaS3完整交付及nullable长度资源释放等fix1已独立复审全部关闭并采用（43b33ba/a46d218）；V009六表结构已独立采用；公共DB注册/回滚孤儿不可读/引用与epoch清理及两后端闭环未完成。|
|DIFF-10 快照/候选/根指针|OPEN|V011/11.1结构前置已独立PASS采用（022f68a/71c7c5c/1d710f9）；8/63/39/13/22实际元数据一致，首轮索引失败和新版本修正证据保留。V012结构也已独立PASS采用（19/123/92/21/45、11IT实际通过），V013五表结构也已独立PASS采用（5/42/30/4/18、10IT），领域版本/epoch/冻结输入/needs_generation/迟到保护仍OPEN。|
|DIFF-11 迁移次序冲突|机制已落实，逐号持续验证|原001/002/005归集并逐字节冻结，003/004永久空；[统一登记](../../database/migration-register.md)006—027已分配，各新号必须root预执行SHAACK。当前结构独立采用到013（全部旧SQL不变，V011遗漏索引以中央11.1新号修正）；不提前声称后续迁移全部验收。|
|DIFF-12 旧队列草稿反序/懒建subject|OPEN|原稿/日志已归档保全；新替代实现及fix1 b126db2已完整独立复审采用。领域显式fence/独立operation token运行时和各消费者须分别评审。|
|DIFF-13 旧permit隐式配置/无证释放|OPEN|原稿保全；新JDBC配置/准入/执行绑定及排序全模型预锁/RR计数修复已独立复审采用；真实quota/cutoff资格仍OPEN。|

|DIFF-14 纪要目录/未选敏感项/建议/人类复核持久化|设计已补齐，运行时OPEN|1.3新增八表及排除双标签PK/CHECK，实际93表709列；[独立评审](database-generation-addendum-independent-review.md)0findings。V012七表已随十九表结构独立采用；V013最终membership及双physicalFK结构已独立PASS采用；业务权限/哈希/复核/最终确认未实施；两项交互选择已由用户确认，见[首版与核对规则](../../superpowers/specs/2026-10-22-ama-minutes-ux-decisions.md)，实现和验收仍OPEN。|

|DIFF-15 跨来源复制后的解析上下文/使用项追溯|发现技术缺口，D01设计及运行时OPEN|[纯S1修订契约](../../superpowers/specs/2026-10-22-ama-minutes-contract.md)只明确使用项投影和不支持路径失败，未新增持久来源边。[D01必做设计](material-provenance-design-prerequisite.md)须在复杂材料/一般来源重绑定启用前独立评审；不是删除需求或完整B07验收。|

R1检索共同F、R2完整规范名/摘要碰撞、R3专题说明、R4逻辑operation/append-only execution、R5转写不可变输入、R6片段多标记的设计修正已独立关闭；其领域运行时实现仍由对应V008/010/011/012/023与任务评审逐项证明，不以DDL元数据替代行为。1.2的upload_session.display_name/purpose已随V009结构独立采用，公开FINALIZING注册协议仍待完整B04；存储已选清单修复只是其中的必要边界。

B03结构完成、V009结构完成后分别追加实际采用提交及回归；B05和后续消费者交付时逐项更新。本表不代签独立评审、最终B阶段验收、真实公司联调、100录音/300在线容量或RHEL/fencing恢复资格。

当前协调补记：严格UTF8纯解析器前置已独立评审并接入（19项focused单测），仍未启用生产格式/大小能力；V011/11.1结构已独立采用；完整B06处理输入/确认/标记和业务指纹尚OPEN。该纯代码进展不改变本表各数据库/跨资源差异状态。
