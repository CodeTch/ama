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


## 2026-10-29 R2 正式本地 B 的差异重核

上表保留2026-10-22原始判定，不用后来的成功改写当时的OPEN。以下依据已采用 remaining Tasks1–10、B-L1–4及当前日常链重核实际实现；**本节是作者映射，独立 final obligation/branch review 与尚未完成的限定证明仍是门槛**。目标环境仅单MySQL、本地mTLS文件、受控模型和loopback签名知识库；企业/双后端/双活要求按R2 PLATFORM-B或最后F保留。

|差异|当前本地实现/已得证据|当前剩余或保留边界|
|---|---|---|
|DIFF-01|已采用审计元数据新空库范围；日常信息/生成受理真实写后SQL异常、RAW/candidate/mapping native precommit halt显示相应root/图及审计/outbox原子边界；见[当前核心资格](local-core-qualification-2026-10-29.md)|B01-IMM-01历史应用后字节偏差永久PARTIAL，不repair或洗白旧库；当前新库成功不追认历史迁移|
|DIFF-02|已采用B03/Task3 authority、Task4/6/10消费者；当前owner/member/outsider/admin实际allow/deny、草稿不授权、S提交后owner正常API撤回到F拒绝已证|其他精确写入interleaving/锁集合按B-R03/06/12最终收口；公司身份来源PLATFORM-B|
|DIFF-03|共享DB会话解析、generation、停用原有18测试；当前跨六服务重启原会话保留、正式文档实际HTTP后身份停用F拒绝及恢复原字节|当前单机控制链不代替企业SSO协议与传播资格|
|DIFF-04|Task7准备/持久marker/UNKNOWN query路径、Task9生成与Task11发布已接；当前candidate和mapping真实INSERT后halt保留MARKED/RECONCILING且原task/execution恢复，providers独立重启保留原effect|共存时实际client GET/POST计数另补；企业零效果/cutoff证明PLATFORM-B|
|DIFF-05|Task3/6/7/9/10/11真实issuer/holder/阶段、identity→root及domain/task与flush-last；当前实际共享工厂/gates和root-free断言已有|本地完整集合/实际锁序追踪仍按B-R12；D阶段删除未启用，不提前认证|
|DIFF-06|typed IMPORT/EMPTY/GENERATION_INPUT/PUBLICATION已由正式消费者使用；当前三native切点原任务attempt恢复、零部分图、各唯一RAW/candidate/mapping|不扩展到未启用音视频/材料；最终affected regression/review pending|
|DIFF-07|已采用队列/显式outbox flush/消费者前缀原延迟commit+rollback+争用证据；当前领域受理/完成outbox跟随同事务|外部投递消费者C/E任务未启用，依拥有阶段验收；不是本地B缺实现|
|DIFF-08|真实ConfiguredMinutes/RuntimeProvider使用持久permit配置与执行绑定；旧NULL保守占额/批量全模型预锁证据保留；native MARKED恢复不释放未知占额|真实公司quota/cutoff仍PLATFORM-B，不能从controlled模型1次结果宣称|
|DIFF-09|Task4/5文件登记、真实本地mTLS双TXT上传、固定输入/引用；坏第二文件无部分RAW，RAW native恢复保留输入/文件/引用；普通完整重启同字节|intake后期受理写后回滚补证中；双后端/迁移/物理删除/灾备按R2 DEP条目F|
|DIFF-10|Task6/9/10不可变输入、候选及root、人类编辑保全已采用；当前真实首代自动adopt、再生成pending、单选adopt、human保存/审阅/冻结/发布链已证|晚到及精确S/A/F/原子性继续按当前B-R05剩余证明，不以UI成功替代|
|DIFF-11|原SQL字节保留，新号登记机制继续，当前日常装配可重复迁移/重开；没有更改已应用SQL或历史checksum|完整迁移目标矩阵/升级恢复按F。不得将设计模型全部表数当作当前已部署表数|
|DIFF-12|旧草稿归档不复用；真实领域fence/registry/queue/operation token通过正式composition，当前native恢复非手写lease|实际锁序/完整keyset剩余跟随DIFF-05/B-R12|
|DIFF-13|新permit配置/执行绑定/未知保守占额已由Task7/9/11消费者接入，native恢复原execution而非重建|公司真实quota/cutoff仍未验证，不以本地成功关闭企业资格|
|DIFF-14|Task9/10与B-L1–4已经实现目录/建议/审阅/候选/readiness/冻结，当前真实browser/API流程与真实action owner目录选择有证；见[负责人资格](action-owner-local-2026-10-29.md)|受控输出只证明流程。实际输入事实/相关性/语义质量及公司目录来源仍PLATFORM-B|
|DIFF-15|D01设计已独立采用；当前B仅既定TXT/受控来源，通用材料/跨来源重绑定能力继续明确不可用|复杂材料的一般来源边由D01及相应阶段拥有，F核验部署相关项；未把需求删除或写成已实现|

本地最终关闭还要求：适用intake原子性、维护/对账有界饱和、完整锁集合和全部源码最终回归/独立分支审查。此处不单独宣布正式B通过。

最终采用（2026-10-29）：独立L2/L3/L4/B00与原BASE→V2全分支APPROVE已形成；上列当前本地启用差异全部按[正式本地B验收](formal-local-b-2026-10-29.md)收口。旧20261022 OPEN和IMM PARTIAL保留为历史事实，PLATFORM/F及未启用D01范围不变；当前不再有本地B实现待办。
