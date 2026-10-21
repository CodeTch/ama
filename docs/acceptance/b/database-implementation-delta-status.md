# B00 设计差异实施状态

2026-10-22；采用基线 B00-1.2-review。此表记录协调状态，原始 [设计差异](database-design-review.md) 和独立评审保留。设计通过、代码通过与真实公司/运维资格分别记录；未执行测试不计通过。总计划和B计划保持用户原字节。

|差异|当前状态|代码、证据和剩余门|
|---|---|---|
|DIFF-01 审计标识/索引/微秒|新受控空库范围已修正并独立评审|V006 f36…；[实施](audit-metadata-delta.md)及其独立初审/复审。B01-IMM-01历史应用后改字节偏差永久保留PARTIAL，不以本功能修正消除；旧checksum/失败/未知库先单独审阅恢复，不repair。|
|DIFF-02 草稿/有效ACL混合|OPEN|V007拒绝无来源旧数据；V008有效grant/roster结构已真实验证并独立采用（ff22197）；运行时owner/确认来源/撤权/admin正文拒绝与文件再鉴权待完整B02/B03/B04。|
|DIFF-03 会话代际/精确ID|受控身份前置已修正并独立评审|V007/JDBC每请求共享解析、generation、身份变更和双节点18测试；[实施](identity-prerequisite.md)、[独立评审](identity-prerequisite-independent-review.md)。实际SSO签发/停用传播及公司协议仍未资格验证。|
|DIFF-04 外部调用前持久marker|OPEN|B05仅准备接口/源稿，V010未执行；ambient拒绝、真正commit后调用、结果未知不重发和独立execution收口待真实并发/崩溃测试。|
|DIFF-05 统一锁序|OPEN|基线和消费者契约已明确；B05显式flush-last提案尚未MySQL验证，领域路径/领取/完成/删除并发待实现评审。|
|DIFF-06 producer/attempt/上限/typed输入|OPEN|V010和实际队列、crash cap/同键异体/旧尝试写回测试待实施；编译或Worker3单测不关闭此项。|
|DIFF-07 提交顺序/consumer前缀|OPEN|序列与显式最终flush/consumer协议已设计；延迟commit/rollback/前缀ACK/lease/争用待V010真实测试。|
|DIFF-08 额度deadline/配置/执行绑定|OPEN|NULL保守占额及配置授权已设计；旧草稿保全不复用。无真实平台cutoff保证，不释放未知占额或自动开启模型容量。|
|DIFF-09 文件/DB跨资源边界|OPEN|ports/native基础设施和[选定清单修复](local-upload-selection.md)已评审；JavaS3正在实现，V009结构准备。公共DB注册/回滚孤儿不可读/引用与epoch清理及两后端闭环未完成。|
|DIFF-10 快照/候选/根指针|OPEN|V011—013及领域服务未实施；版本/epoch/冻结输入/needs_generation/迟到结果保护待实际回归。|
|DIFF-11 迁移次序冲突|机制已落实，逐号持续验证|原001/002/005归集并逐字节冻结，003/004永久空；[统一登记](../../database/migration-register.md)006—027已分配，各新号必须root预执行SHAACK。当前已审到008，不提前声称后续迁移全部通过。|
|DIFF-12 旧队列草稿反序/懒建subject|OPEN|原稿/日志已归档，准备分支移出active source，不代表新替代实现已通过。领域显式fence、独立operation token及真实回归待B05及消费者。|
|DIFF-13 旧permit隐式配置/无证释放|OPEN|原稿保全；新接口failclosed，不存在已合入的新JDBC实现或真实quota/cutoff资格。待B05实现、排序配置锁和execution绑定测试。|

R1检索共同F、R2完整规范名/摘要碰撞、R3专题说明、R4逻辑operation/append-only execution、R5转写不可变输入、R6片段多标记的设计修正已独立关闭；其领域运行时实现仍由对应V008/010/011/012/023与任务评审逐项证明，不以DDL元数据替代行为。1.2的upload_session.display_name/purpose及公开FINALIZING注册协议待V009与完整B04；存储已选清单修复只是其中的必要边界。

B03结构完成、V009结构完成后分别追加实际采用提交及回归；B05和后续消费者交付时逐项更新。本表不代签独立评审、最终B阶段验收、真实公司联调、100录音/300在线容量或RHEL/fencing恢复资格。
