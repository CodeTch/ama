# AMA 数据库详细设计基线

设计版本：**B00-1.0-review**，2026-10-21；状态：作者自审及真实结构验证，**待独立评审**。不是阶段 B 实现完成、平台联调、容量或上线批准。代码输入 BASE `e8c88d2aaed1a19b0d0202ddce9cb51ee01d84ee`，唯一设计工作树 `/home/tanren/ama/.worktrees/phase-b-database-design`。

绑定依据为架构 v0.2 第 6、8—10、12、14 节、需求对应清单 v0.2、A-02 决定台账与接口清单，以及用户已确认的 `2026-10-21-ama-product-supplement.md`。补充规格优先于旧台账中“建议/待确认”措辞：实际日期可选、转写显式保存、纪要显式及自动保存、人工标题保护、同批音视频可合并而不与纯转写混批、邮件仅受控链接和已确认且仍可读的企业参会人、治理候选/审核/生效、声纹用途授权可撤销、配置独立审批和新修订回滚。公司协议和运维证据仍 pending；测试 manifest 的空 fields/evidence 不构成认证。

配套：[`ama-data-dictionary.md`](../../database/ama-data-dictionary.md) 是逐字段定义；[`ama-schema-draft.sql`](../../database/ama-schema-draft.sql) 仅供一次性 MySQL 8.4 审阅库；[`migration-register.md`](../../database/migration-register.md) 管理实际及预留迁移；[`database-design-review.md`](../../acceptance/b/database-design-review.md) 记录覆盖、实现差异及验证。声明源 `tools/checks/database_design.py` 生成字典、DDL、本文完整 ER，实际数据库元数据另行核对，不以生成成功替代验证。

## 约束与领域归属

- “100 场同时在线录音并实时转写，每场一个采集端，合计 300 名在线用户”；300 包含 100 名录音用户。
- “两台 RHEL 8.10 x86_64 虚拟机，每台 16 核、32GB 内存”；不新增第三台项目节点。
- “业务数据仍使用 MySQL”；`database.mode=private-ha|enterprise-platform` 与 `storage.type=local|s3` 独立。
- “local 模式启动校验不得强制要求 S3 地址、Bucket 或凭证”；新上传/录音固定后端 ID，历史读取按原记录路由。
- “只有一个节点挂载普通 XFS 文件系统并启动数据服务”；隔离成功且副本可用后接管，隔离失败不强制提升。
- 现有模型、知识库、SSO/人员目录均采用适配器；不新建公司知识库，不用模拟结果冒充真实集成。
- 未确认转写不得生成；待采用候选不得通过最终确认；发布成功只读；撤回/废止不得恢复或再次发布。
- 负责人名单草稿、AI/声纹姓名、行动项负责人不产生访问授权；所有文件读取再次检查会议权限。
- 原文 P0/P1 均交付，历史成果不自动清理；不擅加产品名称字数上限；UTC 存储，实际会议日期按 Asia/Shanghai 筛选。
- Redis/MQ/Kubernetes/搜索集群不作为必需依赖；生产凭证不进仓库；原型/需求原件不修改。

物理表共用一个业务 schema 和 DataSource，领域服务拥有写权而不是各自重复一套授权/任务体系。B-01 拥有事务/审计；B-02 拥有目录、会话、角色及授权判定；B-03 拥有会议根/信息/名单/有效授权变更；B-04 拥有文件账本/引用/上传；B-05 拥有任务、外部操作 fence、outbox 和模型额度；B-06 拥有输入与转写快照；B-07 拥有纪要输入/候选/要素/来源；B-08 拥有最终快照与发布。C/D/E 扩展同一实体，不另建重复工作稿、发布或候选流程。字典逐表注明主任务和分期。

## 标识、时间、内容与状态

内部实体使用服务器生成 UUID 的 `CHAR(36) ascii_bin`；供应商稳定 ID 用 `VARCHAR(255) utf8mb4_0900_bin`（NO PAD），二进制精确相等，不裁剪、折叠大小写或用显示名匹配。255 是待平台 ID 契约验证的存储预算，不是姓名/名称字数约束；供应商超出时必须增量迁移而非截断。公司实体 ID 不冒充内部 UUID。所有产品名称、摘要、标题、正文、说明均 LONGTEXT；HTTP/解析/上传保护另由 A-02/INT-08 定义可观察错误和技术预算，不静默截断或新增名称上限。

DATETIME(6) 写 UTC；应用进入事务设置 `time_zone='+00:00'`，Java Instant 明确截断到微秒（不用默认时区/四舍五入），租约用 `UTC_TIMESTAMP(6)` 判定。`actual_meeting_date DATE NULL` 是负责人明确填写的上海日历日期，未知保持 NULL，筛选 `>=from AND <=to`；UTC 事件筛选上海日须在应用将边界转换成 UTC 半开区间，不用 `DATE(created_at)` 代替实际日期。

状态值由 CHECK 列举，合法转换由服务器状态机＋预期版本 CAS＋事务锁约束。CHECK 不验证跨行、是否本人、外部真实结果，也不能证明终态不可回退。`meeting.revision` 为全聚合业务 fence；每次会影响有效成果/权限/名单/输入的修改在同一事务递增根 revision 和 subject.business_version。上传分片进度、任务心跳、事件投递不无意义递增正文版本。各领域不可变版本另有局部 revision。`cancel_epoch` 单调递增，删除后根 tombstone 不复用 ID。`access_revision` 在名单授权/治理/删除变更时递增；长流约每5秒重验，已下载字节无法撤回。

`meeting.stage`、`lifecycle`、`governance_state` 独立。`ever_published=1` 永不清零。PUBLISHED 只读，不可再发布；撤回/废止及其 pending 状态阻断普通回看/检索/下载/AI引用，不允许恢复。平台管理员只有管理角色，不默认有会议正文访问权。

## 逻辑关系与删除完整性

完整 ER 覆盖字典全部物理表/字段/FK。所有实际 FK 都 RESTRICT，不做跨领域级联；先新增父再子、删除反序。单列 FK 只能证明存在：每一引用还必须在拥有领域事务中核验相同 meeting、owner、版本集成员和合法状态。错误关联整体回滚，不依赖客户端提供的 meetingId。下列未建 FK 的关系有明确拥有者和拒绝规则：

|逻辑关系|完整性拥有者与写规则|删除/更新规则|
|---|---|---|
|meeting 当前 info/roster/transcript/minutes/final 等指针|B-03/B-06/B-07/B-08，锁根后查目标同会议，roster 必 CONFIRMED；只指已保存有效版本|根先清指针再按依赖清理；发布冻结后不可移动|
|task_subject(kind,id) → 领域根|领域创建时同时插入，任务注册器只接受白名单 kind；enqueue 不创建根/fence|删除保留 fence tombstone；不可重新初始化同键|
|durable_task 输入/输出 typed ref|B-05 注册器调用领域验证，必须为具体不可变快照，同 subject/version/fingerprint|成功/失效后最小诊断可留，但正文删除时擦除/解除内容定位，不允许悬空引用被读取|
|task_outbox subject/reference typed ref|事件生产领域在同事务校验；消费者重新按权限解析|删除后只返回安全失效事件；事件不嵌入正文|
|file_reference owner_kind/owner_id|B-04 与所属领域共事务登记；每个业务文件列及历史文件引用都必须有账本行|先移除内容引用，锁 file 修改账本/epoch；无引用再可清理，引用禁止指向待删文件|
|audit actor/resource/trace、历史created_by|B-01 保存事件发生时标识，不强制外部主体永存；活动行为仍验身份|只保留最小审计；不能以审计保留正文；不级联删除安全记录|
|category、prompt/model version、external ID|配置/平台适配器校验白名单、存在性、固定版本、访问权；未提供真实选项不伪造|版本不可原地改含义，旧成果继续可追溯；撤销只阻断后续生效|
|generation target_element 与 source/evidence 的输入集合归属|B-07/D-03 验目标属于当前 element_set，源属于该确认转写或材料快照；页/段/时间范围真实存在|恢复/采用产生新版本与新引用；不改旧来源来“补证据”|
|B阶段可空 material_snapshot、speaker、material locator|B-06/B-07 只允许尚未启用功能对应 NULL；D-01/C-03 开始非空写入前对应表/FK迁移已完成|审阅库建完整 FK；正式迁移按 registry 顺序，禁止 B-01 一次建全部未来表|

全局物理删除需要先解除 generation/exclusion/locator 等可能形成环的内容引用，再按拓扑逆序清理；FK 全为 RESTRICT，删除协调器显式规划同会议集，不能临时关闭 FOREIGN_KEY_CHECKS。跨会议共享模板、目录和治理数据不随会议级联删除。D-07 对 tombstone 的不可读、业务内容清除、文件删除、最小审计分别给证据。其他领域仍引用的受管文件不能当“删除成功已清除”，需移除会议关联并按批准范围证明没有会议正文副本留下；语料复用/法律保留没有企业规则时不开启。

## 统一事务与锁协议

使用 B-01 `TransactionRunner.required`、同一个 DataSource/Spring 绑定连接；推荐 READ COMMITTED 的短事务，迁移/应用必须显式确认隔离级别。CAS 同时检查 `expectedRevision` 与合法状态，0行即409/失效，不能继续执行效果。数据库死锁/锁超时只可重试整个纯数据库事务，有限次数＋抖动并复查权限；不可把已发外部请求的网络函数包进事务重试。

所有会触及多个领域的路径严格遵循以下锁等级，同级按稳定二进制主键排序，多根事务先按 subject_kind/id 排序锁齐。不得在 `advanceSubject` 之前先锁 meeting：它是事务入口的一部分。

1. 请求幂等作用域行（若有），再身份/授权身份行按ID；会话停用只锁身份及会话，不反向锁会议。业务写需要身份稳定时锁相应身份后再进入聚合。
2. `task_subject` fence；创建时 UUID 已确定，先插入 fence 再插入领域根。相同请求受幂等唯一键保护。
3. 领域聚合根（会议/治理根），再领域不可变版本、名单、ACL、发布根等按固定表名/主键顺序；需要修改的当前行预先锁齐，不在 callback 临时寻找另一个根。
4. 上传/录音会话与段（固定先 session 后 part），再 `file_object` 按ID，再 `file_reference`/upload_part/chunk/cleanup。引用新增和物理清理认领都走同一 file 锁；不得持 file 锁后再回头锁 meeting。清理使用先读候选ID、不加锁→按本顺序重读复验。
5. `durable_task`、`task_attempt`、`external_operation`（按任务ID再操作ID）。完成事务在第3级预先锁领域根；回调只能修改已锁同聚合数据，禁止外部I/O及引入更低等级锁。新插入不可变结果在已锁根下分配，无需反向锁旧域行。
6. 审计 append，最后 `task_outbox_sequence(id=1)`、outbox INSERT，立即 COMMIT；分配序号之后不得再锁上述行，也不得调用网络。

`model_capacity`/reservation 准入使用**独立短事务**：先锁已提交的 external_operation，复验独立operation token/expiry/state，再锁 model、reservation（FK的父operation已锁）。不锁subject/meeting/task/outbox、不回调领域，任何路径不得 model→operation 反序。完成/对账后的额度终结在本地业务事务提交后另事务依 operation→model→reservation 顺序幂等执行；遗漏会保守多占额，不会超发。外部操作历史只有reservation已按批准策略解除后才可清理。`outbox_consumer` 独立领取/续租；同库消费者要应用领域效果时先进入上述1—5级，再锁 consumer，完成效果和cursor，最后outbox序列；不得持consumer后回调领域。跨领域消费建议按事件入幂等键并重新开启标准业务事务。

普通领取不能用一个锁 JOIN 先抢 task 再抢 subject。扫描 READY due 与 RUNNING expired 两个候选索引，非锁定得到小批ID；逐候选先 `SELECT task_subject ... FOR UPDATE SKIP LOCKED`，再根（若需判定领域状态），再 task `FOR UPDATE SKIP LOCKED`，重新验证状态/到期/版本/epoch，成功递增attempt并插attempt行。被锁候选跳过，不能阻塞后面全部工作。扫描分页有上限，每5秒执行，20秒租约、每5秒独立心跳；公平性按池配额＋队列等待时间提升排序，不能以 priority 永久饿死低优先级。具体吞吐和计划必须由 B-05/C-07 实测，不用本次建表证明30秒目标。

SKIP LOCKED 只处理行锁并返回不一致视图，因此只用于队列候选竞争，不用于名单确认、最终快照、权限判定等完整业务集合读取；DDL/元数据锁仍可能等待。[MySQL 8.4 locking reads](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html)

主要调用路径：创建=幂等→身份→新subject→meeting→审计→outbox；编辑/名单确认=幂等→身份→subject→meeting/roster/ACL→递增两处版本→审计→outbox；上传完成=subject→meeting→upload→file/refs→任务→审计→outbox；删除=subject→meeting(资格校验)→会话/文件→任务/操作状态→epoch及tombstone→审计→outbox；旧worker完成/发布查回=subject→meeting/领域快照→文件(若有)→task→operation→审计→outbox。外部查回不能反序先锁operation再锁meeting。

## 权限、会话和幂等请求

会话每请求从共享DB验证 token_hash、expiry、revoked_at、identity ACTIVE、session_generation 完全相同，无节点本地授权缓存。停用/全退出同事务递增 generation 并审计；再启用不会恢复旧代际。单会话退出写 revoked_at。公司协议未落实前只允许生产resolver fail-closed，没有生产签发/回调/测试登录口。Cookie/CSRF/Origin 和WebSocket绑定由 B-02 按接口契约验证；寿命取明确配置，不从本设计发明企业时限。

负责人通过 `meeting.owner_id` 获得 READ/EDIT/MANAGE；有效名单企业成员仅 READ。角色不会覆盖对象权限。`roster_revision/roster_member` 全部禁止被授权查询读取；只有明确确认动作在同事务冻结名单、检查每个企业稳定ID、计算增删 `meeting_access_grant`、增加 access_revision、根revision/fence并审计。外部人员没有企业ID不授权；成员读取会前最新有效保存稿、发布后固定快照，绝不候选/未保存草稿。B02旧 `meeting_acl` 停止授权前须迁移确认来源；不能仅 `confirmed_at IS NOT NULL` 推断业务确认。

幂等作用域是 actor/resource/operation/Idempotency-Key 的长度前缀编码 SHA256，保存完整请求摘要。同scope不同摘要409；同摘要返回原安全对象ID/状态并再次鉴权；不得重执行外部效果。并发唯一键插入等待获胜事务提交后比摘要；业务回滚幂等行也回滚。对任务 enqueue 使用独立 producer_key，领域操作稳定键＋输入指纹生成，冲突比较完整spec。无默许TTL清除已完成key导致重复创建。普通保存使用CAS；两个窗口只有一个版本更新成功。

## 工作稿、快照、候选与最终确认

转写 RAW/ENHANCED/WORKING/CANDIDATE 为不同不可变revision；保存产生新revision，稳定segment ID与实际源文件定位保留；纯转写可无时间、无说话人，不造音频。确认前校验已保存非空有效正文、零PENDING纠错、来源完整、当前revision及权限；插入 transcript_confirmation 并更新当前指针，可靠任务/outbox 同事务。B初始输入可无材料，指纹把NULL材料规范化为空集合；材料已启用后不得用NULL绕过未应用变更。

generation_input 固定确认转写、材料集合、模板、排除集、要素集、prompt/model版本、生成scope、基线revision；按schema版本、字段类型与稳定排序做规范化编码后 SHA256。空集合与缺失/未知不可混同；指纹包含源文件版本/哈希与来源清单，不以更新时间猜版本。模板空/内置和排除空集也有受控版本；模型/提示词配置B阶段允许受控文件版本，绝不固定写“latest”。

模型结果先存 generation_candidate 及候选正文/要素，当前有效指针不动。采用须当前根revision、inputFingerprint、目标区域版本都等于生成基线；并发人工编辑则409保留候选。全文采用生成新有效保存版本；局部采用只产生对应要素新集，正文保持旧稿并标为需要生成。`needs_generation` 与待处理候选查询是不同事实：生成成功可以清除前者，`PENDING`候选仍阻断最终确认；候选明确拒绝/过期处理不能冒充采用。

最终确认锁根一次读取全部版本：名称/分类满足已定规则、确认名单、转写、已保存正文、已应用材料、要素、模板、排除、来源关系一致；没有未处理候选、待应用变更或敏感冲突。客户端未保存草稿不进入后端确认；API payload只能引用服务端保存版本。检查成功插 immutable final_confirmation，保存清单hash。确认后实质编辑清空当前final指针并递增revision，原confirmation仍保留历史。标题origin=HUMAN后会议改名/AI不得覆盖；恢复旧稿创建新版本且保留操作人时间，不回拨版本号。

来源定位按 TRANSCRIPT/AUDIO/PDF_PAGE/PPT_PAGE/WORD_PARAGRAPH/TEXT_PARAGRAPH/KNOWLEDGE_VERSION 分类型；对应字段必填且非适用字段由schema拒绝，页段真实存在，所有引用属于固定输入集合。证据只指向具体要素/正文版本；背景材料不能独自支持重要会议结论和行动，未证实为UNSUPPORTED/PARTIAL，不能造置信度。排除设置不删除源、核心要素或改变ACL，只有下一次生成应用，并在最终确认复验人工加回的冲突。

## 租约、外部效果与对账协议

普通领取只针对 READY 或已过期且未标记外部效果的 RUNNING。领取递增attempt，归属worker，expiry=DB now+20秒；续租须同attempt/owner、expiry严格大于DB now、版本/epoch匹配、未删除。过期不能续活。完成先按统一锁序锁根/task，核对完整不可变spec、fingerprint、business_version、cancel_epoch、attempt/owner与仍有效租约，然后同事务写领域结果、task、attempt、audit/outbox；检查失败不执行效果。`completeWithEffects` 优先；裸complete返回false后调用者不得继续写业务。

每任务 max_attempts 必须由受控任务策略明确赋值（开发初始5；是可配技术策略，不是公司承诺），包括崩溃回收；没有效果且确定可重试时退避5/10/20/40秒加有界抖动，第5次终结FAILED。用户重试产生新 task/producer_key 保留原输入；不是重置旧 attempt。参数/权限错误直接FAILED；旧业务版STALE，取消CANCELLED并保留最小诊断；不靠无限RUNNING回收耗尽平台额度。

任务粒度为一次固定输入的处理步骤/外部调用；同一模型的多个音频窗口/片段或分段生成应分别入队，以各自稳定输入指纹和producer_key去重，不能在一个task内循环发出未登记的多个同类效果。流水线下一步在前一步完成事务中以typed输出快照入队；一个task每种operation_kind唯一，不重写旧operation来隐藏多次调用。任务/操作进入未知后只由独立对账流收敛，不从普通自动重试路径重新发网络。

任何可能引起外部结果的调用（模型生成、知识库创建/治理、邮件）先 `beginExternal`：锁subject→根（冻结发布成果及拒绝编辑/删除）→task→operation；验证当前租约，插 external_operation(MARKED, stable business_key, request_hash, origin_attempt)，task转RECONCILING、记outbox，**实际提交后才允许发网络**。入口必须拒绝已有 ambient transaction（或专用事务完成回调在真实afterCommit调度）；仅 REQUIRED 返回不等于提交，禁止在外层事务尚未commit时调用。若提交成功但发出前崩溃，MARKED也作为结果未知处理，不能假定未调用。

普通claimer完全排除RECONCILING。独立reconciler非锁扫描 due operation，再按subject→根→task→operation锁定，CAS领取 reconcile_epoch/owner/expiry（同20秒、5秒心跳策略）后短事务提交，外部查询在锁外。写回必须持有当前独立对账token；过期旧worker即便得到成功也不能以旧任务attempt完成，提交安全观察值供当前对账者重验/查询，不能抢回所有权。确定外部成功记operation/映射后，在同业务版本/未删且合法状态时完成域效果；版本失效只保存诊断或隔离候选，永不覆盖当前稿。对账信息不受原任务租约过期丢弃，外部已发生事实必须收敛。

beginExternal 初次提交同时令 reconcile_epoch=1、reconcile_owner=发起Worker、reconcile_expires_at=DB now+20秒、next_check_at=该截止，返回独立operation token；初始调用者也按该token续租和登记结果，原task token不再有完成权。接管只在operation租约失效且到next_check_at后发生。调用前再验token只保护本地决定，不能消除“检查后暂停、醒来才发出”的外部竞态；平台未提供幂等创建时，安全重放证明还必须排除旧发送者在截止后重新发起（如服务端强制请求绝对截止/调用fence，或已确认旧执行者被隔离）。仅本地lease过期或等待一段时间不构成该证明。无此能力就保持UNKNOWN/MANUAL_REQUIRED，不能自动第二次发出。

`NOT_FOUND`不是确定未执行：原请求可能仍在途、平台读可能最终一致。只有平台原生幂等创建（相同键相同效果已验证）或明确安全截止＋唯一可查回/草稿提交协议证明不会迟到创建，才允许按同键重放；否则继续UNKNOWN或MANUAL_REQUIRED，禁止第二次创建。UNKNOWN不当作FAILED，不允许编辑后另起发布；无幂等/可靠查回能力的知识库/邮件为真实集成阻塞。本地唯一键只限制本地记录，不能证明外部唯一。

publication 每会议一个根。稳定 business_key=`publicationId + confirmationId`无歧义编码，所有尝试保留当次confirmation/operation。确定失败且证明零效果才可同发布根绑定新confirmation并生成该确认的稳定业务键；SUCCEEDED后不允许换绑定/重试创建。成功映射 provider+externalDocumentId唯一、ever_published=1，与task/audit/outbox原子。撤回/废止先本地不可读并记治理请求，再网络同步；未知继续阻断。外部回调验签/去重/时效后查权威状态，不以乱序事件恢复终态；敏感读取权威状态无法确认则拒绝。

模型额度准入与短任务租约独立：按operation→model→reservation锁序复验当前独立token、显式容量、enabled/config_revision，计数未settled reservation后插入；持有reservation才调用。marker已提交而准入失败只能由仍持有效operation token的初始发送流程明确终结“本次未调用”，不得让通用worker自行回READY；接管者不能仅凭暂未查到reservation推断未发送或安全重试。timeout/cancel/lease失效不释放额度。safe_deadline只来自平台证据并截断到微秒；无安全截止使用NULL保守占额直到确定终结，未配置容量fail-closed。缩容不能低于在途量；禁用不删除活跃reservations。终结提交后再幂等释放额度；崩溃遗漏释放由operation确定状态对账回收。

邮件批次 `mail_job` 绑定协调task及保存版本，每个 `mail_recipient` 对应独立发送task/operation和 `jobId+enterpriseId` 消息键。发送前再验企业成员已确认、当前READ、负责人发件权限和发布/撤回状态，只发送受控版本链接。批次部分成功时，SENT收件人永不重发，只对UNKNOWN收件人按其键查回；无需假设供应商批量发送原子性。无单消息幂等/查回能力时保留UNKNOWN/MANUAL_REQUIRED，不能重新发整批。

## Outbox 提交顺序和游标

outbox不是普通AUTO_INCREMENT高水位。所有生产者最后锁 singleton sequence，分配连续号、插引用事件并立即提交；锁持续到COMMIT，因此另事务无法先提交更高号。回滚同时退回sequence和event；批量分配在一事务内，消费者只有已提交连续前缀。统一规则也涵盖录音字幕和非task领域事件（task_id可空）。事件只含安全引用，禁止正文/令牌。

consumer按sequence升序取有界页，成功完成连续前缀后推进cursor，失败不跳过；本地效果与cursor同事务，外部投递至少一次、对端以事件号幂等。消费lease/epoch防旧投递者推进游标。浏览器按授权subject分页，服务器内部游标可越过不授权事件但不得暴露内容/总事件详情；撤权/终态时结束订阅。事件清理/归档未定义前不自动删；将来提供明确定义的cursor过旧reset＋快照协议才允许收缩。全局序列可能瓶颈，B-05需延迟提交并发测试，C-07需含字幕/50 chunk ACK每秒负载验证，不能直接声称足够。

## 文件、上传、录音及删除

受管文件始终通过 file_id 路由 backend/type/namespace/object_key/provider_version/size/sha256；业务表不存物理根目录或公开签名URL。local无需S3配置，后端类型与ID不可改含义。put/complete先确保持久化文件与目录（或S3已验证durable响应），然后登记file/引用/业务状态，再返回成功。DB回滚的孤儿不在file表/无业务引用，不可读；枚举清理记录受限前缀及固定后端，保护仍在途upload/recording与恢复窗口，不能把本地文件＋DB当跨资源事务。

上传完成/重复完成核对相同size/hash/part清单和epoch；不同请求冲突；位置固定不受默认后端变化影响。删除任务先锁根校验从未成功发布、非录音/暂停、非发布进行/未知，设置DELETING及epoch/access_revision，阻断所有读取和新写；旧worker和已签发上传完成必须复查。清理按引用账本与业务列交叉校验，锁file无引用且位置/epoch匹配时标DELETE_PENDING；提交后删除物理对象/目录持久化，再标DELETED。禁止新引用待删文件。失败保留可重试状态；全部正文、版本、缓存和导出/文件清理完成才 deletion.COMPLETE/根DELETED。知识库引用只解除本会议关联，禁止删除公司知识库文件。

录音创建固定backend、writer_epoch；续租/重连必须同身份与当前epoch。每part的(seq,hash)唯一语义：重传同hash返回相同ACK，异hash冲突；旧writerEpoch拒绝。分片文件先durable、再chunk与连续前缀同事务，ACK只来自已提交事实。finish记录每段final_seq并检查缺口/采样连续；归档校验完整后才AVAILABLE，未归档分片仍有file_reference。归档后技术分片只有恢复窗口/无引用证据齐全才清理。多源映射保留源独立时间与全局累计有效采样。字幕独立修订，不能用定时器造STABLE，更不能当确认稿。

## 查询与容量验证计划

|查询/边界|形态与索引顺序|容量假设及后续验证拥有者|
|---|---|---|
|负责/参与列表|先 owner_id 或 access_grant.enterprise_id/action 过滤，再 lifecycle/governance、stage/date；meeting_owner_date(owner,lifecycle,date,id)，access_person_meeting(person,action,meeting)|至少10万会议、1000人、多名单分布；B-03/D-02真实EXPLAIN ANALYZE与分页/total/stageCounts一致性|
|名称/摘要/正文包含检索|授权范围内 `LIKE CONCAT('%', escaped_q, '%') ESCAPE '='`；必须将`%`,`_`,`=`按字面转义；正文只最新有效或发布快照|普通B-tree不能加速前导通配；不拿FULLTEXT近似替代。D-02用授权候选量/长中文数据实测，超预算调整投影或有界后台查询并增量评审，不静默截断结果|
|任务领取|分开 task_ready(pool,state,available_at,id) 与 task_expired(pool,state,expires_at,id)；小批候选后按锁序重验|B-05 EXPLAIN、锁行跳过、5秒扫描、20秒租约，DB健康30秒重新可领目标；C-07加入实时/离线公平性|
|续租/提交|PK task.id，再精准attempt/owner/version/epoch；subject复合PK|B-05旧attempt、暂停handler、真实时间续租、DB恢复；不是本次结构验收|
|版本历史|各领域(meeting_id,revision)唯一B-tree，倒序keyset|至少100万transcript_segment与长文本；B-06/D-04验证恢复新版本及完整稿检索|
|来源追踪|segment(revision,id)，evidence_element(element,locator)，evidence_minutes(minutes,locator)|D-03/D-06页段/媒体定位与跨会议伪造拒绝；不以多表无界N+1加载|
|文件清理|file_meeting(meeting,state,id)，cleanup_due(state,not_before,id)，file_ref_owner(kind,id,file)；清理前锁file再NOT EXISTS全账本|B-04/D-07 orphan/并发引用/位置迁移/删除失败回归；不得用估算refcount替代证明|
|录音/事件|chunk(part,seq)，subtitle(session,event_seq)，outbox(subject_kind,subject_id,sequence_id)|100场每2秒约50分片/s、180000分片/小时；C-07两小时＋八小时稳定性，含全局序列争用|

列表结果/total/stageCounts必须用相同权限及治理谓词，同一只读一致性视图或说明快照边界。无实际日期的NULL单独排序稳定并提供id tie-breaker。授权失败不要通过总数/全文命中泄露正文。吞吐目标100录音+200编辑/回看、20回听、10编辑、5离线任务；每节点API20/RT10/worker10初始连接配额需重算所有消费者。此处只是设计假设，不是EXPLAIN或性能报告。

## 分期迁移、兼容和回滚

V001/V002/V005冻结原字节；不得修改已应用/共享/暂停脚本或填V003/V004空位。登记册分配V006起增量，B-01不得代其他领域创建全套表。审阅DDL为了独立完整元数据核对在一次性库创建全部79表，绝不替代正式Flyway迁移。旧表 `meeting_acl` 仅过渡保留，目标有效权限表为 `meeting_access_grant`，不得无来源自动迁移旧“已确认”记录。

正式扩展：新增列先nullable或显式安全默认，双读/双写窗口受版本标记控制；唯一/检查/FK前审计脏数据与二进制排序冲突，不能INSERT IGNORE吞损。把V001标识改为binary前验证既有长度/相等性；将V002身份投影/role/session_generation转移后，所有节点切到共享即时撤销与新ACL路径。V005缺字段回填完整spec、producer_key、max_attempts以及external marker，旧RUNNING必须排空/确认无外部未知，不能凭空升级成安全重试；维护期间停止领取再上线新worker。

应用回滚只允许回到兼容新增schema的版本。已产生新权限/任务/外部操作数据后，不可回滚到旧ACL或旧worker语义；停止写入并前滚修复，或使用受控恢复点且先对账所有外部效果。收缩旧列/表需要全部节点完成切换、历史/引用保留与备份恢复验证、独立新迁移号；无通用DROP回滚。本设计不提供未经批准的删除迁移。迁移由单一runner执行，Flyway历史校验防止并发自动迁移。

## 未定范围、责任方和解锁条件

|待落实输入|已完整确定的本地结构/行为|责任方及门槛|
|---|---|---|
|SSO/目录协议、稳定ID长度、会话寿命、停用传播|身份/部门/角色/会话generation/有效ACL完整；生产签发fail-closed|身份/目录平台 INT-01/02；B-02真联调前提交版本化协议与撤销双节点证据|
|模型容量、安全调用截止、真实流式与稳定字幕语义|capacity/reservation/operation字段和无截止保守占额已定；不造配额|模型平台 INT-03/04；B-05/B-07/C实调用前证据，超过ID预算先迁移|
|知识库幂等/可靠查回、签名回调、权威ACL|publication/attempt/mapping/operation/receipt完整；UNKNOWN阻断|知识库平台 INT-05/06；B-08/D-05真实成功资格前竞态/迟到/查回验证|
|分类内容、格式/OCR、HTTP/解析资源预算|分类键引用、LONGTEXT、解析状态与typed locator已定；无OCR UNSUPPORTED|产品/运维 INT-08；B-06/D-01能力矩阵，不影响无依赖纯转写设计|
|治理角色映射、允许用途、反馈类别、评测指标/集、配置键白名单|候选/独立审核/批准版本/发布回滚、样本和metrics存储完整|企业安全/产品 INT-09、E-01；E真实功能启用前补具体字典/鉴权矩阵及增量评审；不能用审核权读全部会议|
|声纹授权凭据与保留删除规则、加密密钥管理|consent/sample/加密文件引用/撤销链完整；不写ACL|企业安全 INT-09；E-02真登记/全局复用禁用直到批准|
|邮件协议、授权发件人、消息查回、通知文案|仅已保存版本受控链接；确认企业成员且发送前重验；UNKNOWN不盲重发|邮件平台/产品 INT-07；E-04真实发送前；本轮不发邮件|
|审计/临时数据/备份保留与恢复删除保护|历史不自动删；软删除epoch与最小审计、引用安全清理已定|安全/产品/运维 D-17、PR-13；D-07/F正式清理验收前策略与恢复演练|
|公司MySQL/HA/落盘/集群隔离与容量|业务schema依赖MySQL8.4能力，local/S3独立路由|DB/存储/虚拟化平台PL-01、PR-*；F阶段真实环境资格，不拿fixture代替|

上述是外部内容/平台证据门槛，不是 B-01…08 必需字段占位。后续改变已定状态、关系、长度/精度、约束、删除规则必须先更新本设计/字典/DDL，补增量独立评审、元数据验证和登记新迁移，再在所属任务实现验收。

## 附录：完整 ER（由同一字段清单生成）

PK/FK 明确标在字段上；每条实体存在于字典和 DDL。`o|` 表示子行允许 NULL；`||` 表示子行必有父；父到子默认零到多（复合唯一索引的更紧基数见字典）。关系标签注明实际 FK，当前指针/typed 逻辑关系在正文另表。

```mermaid
erDiagram
    audit_event {
        bigint id PK
        varchar_255_ actor_id
        varchar_64_ action
        varchar_255_ resource_id
        bigint revision
        varchar_64_ result
        varchar_255_ trace_id
        datetime_6_ occurred_at
    }
    department {
        varchar_255_ enterprise_id PK
        longtext display_name
        varchar_255_ parent_id FK
        datetime_6_ verified_at
    }
    enterprise_identity {
        varchar_255_ enterprise_id PK
        longtext display_name
        varchar_255_ department_id FK
        varchar_64_ status
        bigint session_generation
        datetime_6_ verified_at
    }
    role_assignment {
        varchar_255_ enterprise_id PK,FK
        varchar_64_ role PK
        datetime_6_ assigned_at
    }
    identity_session {
        char_64_ token_hash PK
        varchar_255_ enterprise_id FK
        bigint session_generation
        char_43_ csrf_token
        datetime_6_ issued_at
        datetime_6_ expires_at
        datetime_6_ revoked_at
    }
    meeting {
        char_36_ id PK
        varchar_255_ owner_id FK
        bigint revision
        bigint cancel_epoch
        bigint access_revision
        varchar_64_ stage
        varchar_64_ lifecycle
        varchar_64_ governance_state
        tinyint ever_published
        char_36_ info_revision_id
        char_36_ roster_revision_id
        char_36_ transcript_confirmation_id
        char_36_ minutes_revision_id
        char_36_ final_confirmation_id
        date actual_meeting_date
        datetime_6_ created_at
        datetime_6_ updated_at
        char_36_ transcript_revision_id
        char_36_ material_snapshot_id
        char_36_ template_revision_id
        char_36_ element_set_id
        char_36_ exclusion_revision_id
        char_64_ generation_fingerprint
        tinyint needs_generation
        tinyint materials_pending
    }
    meeting_info_revision {
        char_36_ id PK
        char_36_ meeting_id FK
        bigint revision
        longtext name
        varchar_255_ category_id
        longtext summary
        date actual_meeting_date
        varchar_255_ created_by FK
        datetime_6_ created_at
    }
    roster_revision {
        char_36_ id PK
        char_36_ meeting_id FK
        bigint revision
        varchar_64_ state
        datetime_6_ confirmed_at
        varchar_255_ confirmed_by FK
    }
    roster_member {
        char_36_ roster_id PK,FK
        char_36_ member_id PK
        varchar_255_ enterprise_id FK
        longtext display_name
        varchar_64_ member_kind
    }
    meeting_access_grant {
        char_36_ meeting_id PK,FK
        varchar_255_ enterprise_id PK,FK
        varchar_64_ action PK
        char_36_ roster_id FK
        datetime_6_ confirmed_at
    }
    idempotency_record {
        char_64_ scope_hash PK
        varchar_255_ actor_id
        varchar_255_ resource_id
        varchar_64_ operation
        char_64_ key_hash
        char_64_ request_hash
        varchar_64_ state
        char_36_ result_id
        smallint http_status
        datetime_6_ created_at
    }
    info_suggestion {
        char_36_ id PK
        char_36_ meeting_id FK
        bigint base_revision
        char_64_ input_fingerprint
        longtext proposed_info
        varchar_64_ state
    }
    storage_backend {
        varchar_64_ id PK
        varchar_64_ storage_type
        varchar_64_ status
        bigint config_revision
    }
    file_object {
        char_36_ id PK
        char_36_ meeting_id FK
        varchar_64_ backend_id FK
        varchar_64_ storage_type
        varchar_64_ namespace
        varchar_512_ object_key
        longtext provider_version_id
        longtext display_name
        bigint size_bytes
        char_64_ sha256
        bigint location_revision
        varchar_64_ state
        bigint reference_epoch
        datetime_6_ created_at
    }
    file_reference {
        char_36_ file_id PK,FK
        varchar_64_ owner_kind PK
        char_36_ owner_id PK
        varchar_64_ purpose PK
    }
    upload_session {
        char_36_ id PK
        char_36_ meeting_id FK
        varchar_64_ backend_id FK
        varchar_64_ namespace
        varchar_512_ object_key
        bigint cancel_epoch
        bigint expected_size
        char_64_ expected_sha256
        longtext provider_upload_token
        varchar_64_ state
        char_36_ completed_file_id FK
        datetime_6_ created_at
        datetime_6_ updated_at
    }
    upload_part {
        char_36_ upload_id PK,FK
        bigint part_no PK
        bigint size_bytes
        char_64_ sha256
        longtext provider_token
    }
    file_cleanup {
        char_36_ id PK
        char_36_ file_id FK
        varchar_64_ backend_id FK
        varchar_64_ namespace
        varchar_512_ object_key
        bigint expected_location_revision
        bigint expected_reference_epoch
        varchar_64_ state
        datetime_6_ not_before
        bigint attempt
        varchar_64_ last_error
    }
    task_subject {
        varchar_64_ subject_kind PK
        varchar_255_ subject_id PK
        bigint business_version
        bigint cancel_epoch
        tinyint deleted
    }
    durable_task {
        char_36_ id PK
        varchar_64_ task_type
        varchar_64_ pool
        varchar_64_ subject_kind FK
        varchar_255_ subject_id FK
        char_64_ producer_key
        varchar_64_ input_kind
        char_36_ input_id
        char_64_ input_fingerprint
        bigint business_version
        bigint cancel_epoch
        int priority
        varchar_64_ state
        bigint attempt
        bigint max_attempts
        varchar_64_ lease_owner
        datetime_6_ expires_at
        datetime_6_ available_at
        datetime_6_ created_at
        varchar_64_ output_kind
        char_36_ output_id
        varchar_64_ last_error
    }
    task_attempt {
        char_36_ task_id PK,FK
        bigint attempt PK
        varchar_64_ owner
        datetime_6_ started_at
        datetime_6_ ended_at
        varchar_64_ outcome
        varchar_64_ error_code
    }
    external_operation {
        char_36_ id PK
        char_36_ task_id FK
        varchar_64_ operation_kind
        varchar_255_ business_key
        char_64_ request_hash
        bigint origin_attempt
        varchar_64_ state
        datetime_6_ marked_at
        datetime_6_ next_check_at
        bigint reconcile_epoch
        varchar_64_ reconcile_owner
        datetime_6_ reconcile_expires_at
        longtext external_result_id
        varchar_64_ last_error
    }
    task_outbox_sequence {
        int id PK
        bigint next_id
    }
    task_outbox {
        bigint sequence_id PK
        varchar_64_ event_type
        char_36_ task_id FK
        varchar_64_ subject_kind
        varchar_255_ subject_id
        bigint subject_revision
        varchar_64_ reference_kind
        char_36_ reference_id
        datetime_6_ occurred_at
    }
    outbox_consumer {
        varchar_64_ consumer_id PK
        bigint cursor
        bigint epoch
        varchar_64_ lease_owner
        datetime_6_ expires_at
    }
    model_capacity {
        varchar_64_ model PK
        int capacity
        bigint config_revision
        tinyint enabled
        varchar_64_ cutoff_policy
    }
    model_reservation {
        char_36_ id PK
        varchar_64_ model FK
        char_36_ operation_id FK
        bigint config_revision
        datetime_6_ safe_deadline
        datetime_6_ created_at
        datetime_6_ settled_at
    }
    input_batch {
        char_36_ id PK
        char_36_ meeting_id FK
        varchar_64_ kind
        char_64_ input_fingerprint
        bigint base_revision
        datetime_6_ created_at
    }
    input_batch_file {
        char_36_ batch_id PK,FK
        bigint ordinal PK
        char_36_ file_id FK
        char_64_ sha256
    }
    transcript_revision {
        char_36_ id PK
        char_36_ meeting_id FK
        bigint revision
        varchar_64_ kind
        char_36_ batch_id FK
        char_36_ parent_id FK
        char_64_ input_fingerprint
        varchar_255_ created_by
        datetime_6_ created_at
    }
    transcript_segment {
        char_36_ revision_id PK,FK
        char_36_ segment_id PK
        bigint ordinal
        longtext body
        char_36_ source_file_id FK
        bigint source_paragraph
        bigint start_ms
        bigint end_ms
        char_36_ speaker_id FK
    }
    transcript_confirmation {
        char_36_ id PK
        char_36_ meeting_id FK
        char_36_ revision_id FK
        char_64_ content_hash
        varchar_255_ confirmed_by FK
        datetime_6_ confirmed_at
    }
    template_revision {
        char_36_ id PK
        char_36_ template_id
        bigint revision
        varchar_255_ owner_id FK
        longtext name
        longtext themes
        bigint theme_count
        datetime_6_ created_at
    }
    generation_input {
        char_36_ id PK
        char_36_ meeting_id FK
        char_36_ transcript_confirmation_id FK
        char_36_ material_snapshot_id FK
        char_36_ template_revision_id FK
        char_36_ exclusion_revision_id FK
        char_36_ element_set_id FK
        varchar_255_ prompt_version
        varchar_255_ model_config_version
        bigint base_revision
        char_64_ input_fingerprint
        varchar_64_ scope
        char_36_ target_element_id
    }
    minutes_revision {
        char_36_ id PK
        char_36_ meeting_id FK
        bigint revision
        char_36_ parent_id FK
        longtext title
        varchar_64_ title_origin
        longtext body
        char_36_ generation_input_id FK
        varchar_255_ created_by
        datetime_6_ created_at
    }
    element_set {
        char_36_ id PK
        char_36_ meeting_id FK
        bigint revision
        datetime_6_ created_at
    }
    minutes_element {
        char_36_ id PK
        char_36_ set_id FK
        varchar_64_ category
        bigint ordinal
        longtext body
        varchar_64_ support_state
    }
    action_item {
        char_36_ element_id PK,FK
        varchar_255_ owner_enterprise_id FK
        longtext owner_display
        date due_on
    }
    exclusion_revision {
        char_36_ id PK
        char_36_ meeting_id FK
        bigint revision
        datetime_6_ created_at
    }
    sensitive_exclusion {
        char_36_ revision_id PK,FK
        char_36_ locator_id PK,FK
        varchar_64_ category
        longtext reason
    }
    generation_candidate {
        char_36_ id PK
        char_36_ meeting_id FK
        char_36_ input_id FK
        char_36_ task_id FK
        char_36_ minutes_revision_id FK
        char_36_ element_set_id FK
        bigint base_revision
        char_64_ input_fingerprint
        varchar_64_ state
        datetime_6_ created_at
        datetime_6_ decided_at
    }
    source_locator {
        char_36_ id PK
        char_36_ meeting_id FK
        varchar_64_ kind
        char_36_ transcript_revision_id FK
        char_36_ segment_id FK
        char_36_ file_id FK
        char_36_ material_revision_id FK
        bigint page_no
        bigint paragraph_no
        longtext section_path
        bigint start_ms
        bigint end_ms
    }
    evidence_link {
        char_36_ id PK
        char_36_ locator_id FK
        char_36_ element_id FK
        char_36_ minutes_revision_id FK
        varchar_64_ support_state
        longtext target_anchor
    }
    final_confirmation {
        char_36_ id PK
        char_36_ meeting_id FK
        bigint meeting_revision
        char_36_ info_revision_id FK
        char_36_ roster_revision_id FK
        char_36_ transcript_confirmation_id FK
        char_36_ minutes_revision_id FK
        char_36_ material_snapshot_id FK
        char_36_ element_set_id FK
        char_36_ template_revision_id FK
        char_36_ exclusion_revision_id FK
        char_64_ source_manifest_hash
        char_64_ input_fingerprint
        varchar_255_ confirmed_by FK
        datetime_6_ confirmed_at
    }
    publication {
        char_36_ id PK
        char_36_ meeting_id FK
        char_36_ confirmation_id FK
        varchar_255_ business_key
        varchar_255_ library_id
        varchar_255_ folder_id
        varchar_64_ state
        bigint revision
        datetime_6_ created_at
        datetime_6_ published_at
    }
    publication_attempt {
        char_36_ id PK
        char_36_ publication_id FK
        char_36_ confirmation_id FK
        char_36_ operation_id FK
        varchar_64_ kind
        varchar_64_ outcome
        datetime_6_ created_at
        datetime_6_ finished_at
    }
    external_document_mapping {
        char_36_ publication_id PK,FK
        varchar_64_ provider_id
        varchar_255_ external_document_id
        longtext document_url
        varchar_64_ authority_state
        datetime_6_ verified_at
    }
    publication_governance {
        char_36_ id PK
        char_36_ publication_id FK
        varchar_64_ action
        longtext reason
        varchar_255_ actor_id
        varchar_64_ state
        char_36_ operation_id FK
        datetime_6_ created_at
    }
    external_event_receipt {
        varchar_64_ provider_id PK
        varchar_255_ event_id PK
        char_64_ payload_hash
        datetime_6_ received_at
        datetime_6_ processed_at
    }
    media_source {
        char_36_ id PK
        char_36_ batch_id FK
        char_36_ normalized_file_id FK
        bigint sample_rate
        bigint channels
        bigint bits_per_sample
        bigint sample_count
    }
    media_source_span {
        char_36_ media_id PK,FK
        bigint ordinal PK
        char_36_ source_file_id FK
        bigint source_start_sample
        bigint global_start_sample
        bigint sample_count
    }
    speaker {
        char_36_ id PK
        char_36_ meeting_id FK
        longtext display_name
        varchar_255_ enterprise_id FK
        varchar_64_ identity_source
        char_36_ merged_into_id FK
        bigint revision
    }
    correction {
        char_36_ id PK
        char_36_ meeting_id FK
        char_36_ revision_id FK
        char_36_ segment_id FK
        longtext proposed_text
        varchar_64_ state
        bigint revision
    }
    edit_operation {
        char_36_ id PK
        char_36_ meeting_id FK
        char_36_ before_revision_id FK
        char_36_ after_revision_id FK
        varchar_64_ kind
        char_36_ reverts_id FK
        varchar_255_ actor_id
        datetime_6_ created_at
    }
    recording_session {
        char_36_ id PK
        char_36_ meeting_id FK
        varchar_64_ backend_id FK
        varchar_64_ state
        bigint writer_epoch
        varchar_64_ writer_id
        datetime_6_ writer_expires_at
        bigint cancel_epoch
        char_36_ archive_file_id FK
    }
    recording_part {
        char_36_ id PK
        char_36_ session_id FK
        bigint ordinal
        bigint highest_contiguous_seq
        bigint final_seq
        bigint sample_count
    }
    recording_chunk {
        char_36_ part_id PK,FK
        bigint seq PK
        bigint writer_epoch
        bigint sample_start
        bigint sample_count
        char_64_ sha256
        char_36_ file_id FK
        datetime_6_ persisted_at
    }
    subtitle_revision {
        char_36_ session_id PK,FK
        char_36_ segment_id PK
        bigint revision PK
        char_36_ part_id FK
        varchar_64_ state
        longtext body
        bigint start_ms
        bigint end_ms
        bigint event_sequence
    }
    material_reference {
        char_36_ id PK
        char_36_ meeting_id FK
        varchar_64_ kind
        varchar_64_ provider_id
        varchar_255_ external_id
        char_36_ file_id FK
        varchar_64_ state
    }
    material_revision {
        char_36_ id PK
        char_36_ reference_id FK
        bigint revision
        longtext external_version
        char_36_ content_file_id FK
        char_64_ content_hash
        varchar_64_ parse_state
        datetime_6_ created_at
    }
    material_snapshot {
        char_36_ id PK
        char_36_ meeting_id FK
        bigint revision
        char_64_ input_fingerprint
        datetime_6_ applied_at
    }
    material_snapshot_item {
        char_36_ snapshot_id PK,FK
        char_36_ material_revision_id PK,FK
        bigint ordinal
    }
    parsed_document {
        char_36_ id PK
        char_36_ material_revision_id FK
        varchar_255_ parser_version
        char_36_ text_file_id FK
        varchar_64_ state
        bigint unit_count
        varchar_64_ error_code
    }
    parsed_unit {
        char_36_ document_id PK,FK
        bigint ordinal PK
        varchar_64_ kind
        bigint page_no
        bigint paragraph_no
        longtext section_path
        longtext body
    }
    export_job {
        char_36_ id PK
        char_36_ meeting_id FK
        char_36_ minutes_revision_id FK
        char_36_ confirmation_id FK
        varchar_64_ format
        char_36_ task_id FK
        char_36_ output_file_id FK
        bigint access_revision
        varchar_64_ state
    }
    meeting_deletion {
        char_36_ id PK
        char_36_ meeting_id FK
        bigint cancel_epoch
        varchar_64_ state
        char_36_ task_id FK
        datetime_6_ requested_at
        datetime_6_ completed_at
    }
    topic {
        char_36_ id PK
        varchar_255_ owner_id FK
        longtext name
        bigint revision
        datetime_6_ created_at
    }
    meeting_topic {
        char_36_ topic_id PK,FK
        char_36_ meeting_id PK,FK
        datetime_6_ added_at
    }
    corpus_feedback {
        char_36_ id PK
        char_36_ meeting_id FK
        char_36_ source_revision_id FK
        varchar_255_ submitted_by FK
        varchar_64_ category
        longtext comment
        varchar_64_ state
        bigint revision
        varchar_255_ reviewed_by FK
        longtext review_reason
        datetime_6_ created_at
        datetime_6_ reviewed_at
    }
    feedback_segment {
        char_36_ feedback_id PK,FK
        char_36_ revision_id PK,FK
        char_36_ segment_id PK,FK
    }
    corpus_revision {
        char_36_ id PK
        char_36_ feedback_id FK
        bigint revision
        char_36_ content_file_id FK
        varchar_64_ state
        varchar_64_ purpose
        datetime_6_ approved_at
    }
    voiceprint_consent {
        char_36_ id PK
        varchar_255_ subject_id FK
        varchar_64_ purpose
        longtext consent_reference
        varchar_64_ state
        bigint revision
        datetime_6_ granted_at
        datetime_6_ revoked_at
        varchar_255_ revoked_by FK
        longtext revocation_reason
    }
    voiceprint_sample {
        char_36_ id PK
        char_36_ consent_id FK
        char_36_ sample_file_id FK
        varchar_255_ model_version
        bigint dimension
        char_36_ encrypted_embedding_file_id FK
        varchar_255_ key_reference
        varchar_64_ state
        bigint revision
        varchar_255_ reviewed_by FK
        datetime_6_ reviewed_at
        longtext review_reason
        datetime_6_ revoked_at
    }
    evaluation_dataset {
        char_36_ id PK
        varchar_255_ version_key
        char_36_ manifest_file_id FK
        varchar_64_ purpose
        char_64_ content_hash
        datetime_6_ created_at
    }
    configuration_revision {
        char_36_ id PK
        varchar_64_ kind
        bigint revision
        longtext configuration
        char_64_ content_hash
        varchar_64_ state
        varchar_255_ created_by FK
        varchar_255_ reviewed_by FK
        datetime_6_ created_at
        datetime_6_ reviewed_at
    }
    evaluation_run {
        char_36_ id PK
        char_36_ dataset_id FK
        varchar_255_ model_config_version
        varchar_255_ prompt_version
        char_36_ task_id FK
        varchar_64_ state
        bigint sample_count
        longtext metrics
        char_36_ errors_file_id FK
        datetime_6_ created_at
    }
    configuration_release {
        char_36_ id PK
        varchar_64_ kind
        bigint revision
        char_36_ configuration_id FK
        char_36_ evaluation_run_id FK
        char_36_ rollback_of_id FK
        varchar_255_ released_by FK
        datetime_6_ released_at
    }
    mail_job {
        char_36_ id PK
        char_36_ meeting_id FK
        char_36_ minutes_revision_id FK
        char_36_ confirmation_id FK
        varchar_255_ sender_id FK
        char_36_ task_id FK
        varchar_255_ business_key
        varchar_64_ state
        datetime_6_ created_at
        datetime_6_ sent_at
    }
    mail_recipient {
        char_36_ mail_job_id PK,FK
        varchar_255_ enterprise_id PK,FK
        varchar_64_ state
        longtext provider_message_id
        char_36_ operation_id FK
        varchar_255_ message_key
    }
    department o|--o{ department : fk_department_0
    department o|--o{ enterprise_identity : fk_enterprise_identity_0
    enterprise_identity ||--o{ role_assignment : fk_role_assignment_0
    enterprise_identity ||--o{ identity_session : fk_identity_session_0
    enterprise_identity ||--o{ meeting : fk_meeting_0
    meeting ||--o{ meeting_info_revision : fk_meeting_info_revision_0
    enterprise_identity ||--o{ meeting_info_revision : fk_meeting_info_revision_1
    meeting ||--o{ roster_revision : fk_roster_revision_0
    enterprise_identity o|--o{ roster_revision : fk_roster_revision_1
    roster_revision ||--o{ roster_member : fk_roster_member_0
    enterprise_identity o|--o{ roster_member : fk_roster_member_1
    meeting ||--o{ meeting_access_grant : fk_meeting_access_grant_0
    enterprise_identity ||--o{ meeting_access_grant : fk_meeting_access_grant_1
    roster_revision ||--o{ meeting_access_grant : fk_meeting_access_grant_2
    meeting ||--o{ info_suggestion : fk_info_suggestion_0
    meeting o|--o{ file_object : fk_file_object_0
    storage_backend ||--o{ file_object : fk_file_object_1
    file_object ||--o{ file_reference : fk_file_reference_0
    meeting ||--o{ upload_session : fk_upload_session_0
    storage_backend ||--o{ upload_session : fk_upload_session_1
    file_object o|--o{ upload_session : fk_upload_session_2
    upload_session ||--o{ upload_part : fk_upload_part_0
    file_object o|--o{ file_cleanup : fk_file_cleanup_0
    storage_backend ||--o{ file_cleanup : fk_file_cleanup_1
    task_subject ||--o{ durable_task : fk_durable_task_0
    durable_task ||--o{ task_attempt : fk_task_attempt_0
    durable_task ||--o{ external_operation : fk_external_operation_0
    durable_task o|--o{ task_outbox : fk_task_outbox_0
    model_capacity ||--o{ model_reservation : fk_model_reservation_0
    external_operation ||--o| model_reservation : fk_model_reservation_1
    meeting ||--o{ input_batch : fk_input_batch_0
    input_batch ||--o{ input_batch_file : fk_input_batch_file_0
    file_object ||--o{ input_batch_file : fk_input_batch_file_1
    meeting ||--o{ transcript_revision : fk_transcript_revision_0
    input_batch ||--o{ transcript_revision : fk_transcript_revision_1
    transcript_revision o|--o{ transcript_revision : fk_transcript_revision_2
    transcript_revision ||--o{ transcript_segment : fk_transcript_segment_0
    file_object ||--o{ transcript_segment : fk_transcript_segment_1
    speaker o|--o{ transcript_segment : fk_transcript_segment_2
    meeting ||--o{ transcript_confirmation : fk_transcript_confirmation_0
    transcript_revision ||--o{ transcript_confirmation : fk_transcript_confirmation_1
    enterprise_identity ||--o{ transcript_confirmation : fk_transcript_confirmation_2
    enterprise_identity o|--o{ template_revision : fk_template_revision_0
    meeting ||--o{ generation_input : fk_generation_input_0
    transcript_confirmation ||--o{ generation_input : fk_generation_input_1
    template_revision ||--o{ generation_input : fk_generation_input_2
    exclusion_revision ||--o{ generation_input : fk_generation_input_3
    element_set o|--o{ generation_input : fk_generation_input_4
    material_snapshot o|--o{ generation_input : fk_generation_input_5
    meeting ||--o{ minutes_revision : fk_minutes_revision_0
    minutes_revision o|--o{ minutes_revision : fk_minutes_revision_1
    generation_input o|--o{ minutes_revision : fk_minutes_revision_2
    meeting ||--o{ element_set : fk_element_set_0
    element_set ||--o{ minutes_element : fk_minutes_element_0
    minutes_element ||--o| action_item : fk_action_item_0
    enterprise_identity o|--o{ action_item : fk_action_item_1
    meeting ||--o{ exclusion_revision : fk_exclusion_revision_0
    exclusion_revision ||--o{ sensitive_exclusion : fk_sensitive_exclusion_0
    source_locator ||--o{ sensitive_exclusion : fk_sensitive_exclusion_1
    meeting ||--o{ generation_candidate : fk_generation_candidate_0
    generation_input ||--o{ generation_candidate : fk_generation_candidate_1
    durable_task ||--o| generation_candidate : fk_generation_candidate_2
    minutes_revision o|--o{ generation_candidate : fk_generation_candidate_3
    element_set ||--o{ generation_candidate : fk_generation_candidate_4
    meeting ||--o{ source_locator : fk_source_locator_0
    transcript_segment o|--o{ source_locator : fk_source_locator_1
    file_object o|--o{ source_locator : fk_source_locator_2
    material_revision o|--o{ source_locator : fk_source_locator_3
    source_locator ||--o{ evidence_link : fk_evidence_link_0
    minutes_element o|--o{ evidence_link : fk_evidence_link_1
    minutes_revision o|--o{ evidence_link : fk_evidence_link_2
    meeting ||--o{ final_confirmation : fk_final_confirmation_0
    meeting_info_revision ||--o{ final_confirmation : fk_final_confirmation_1
    roster_revision ||--o{ final_confirmation : fk_final_confirmation_2
    transcript_confirmation ||--o{ final_confirmation : fk_final_confirmation_3
    minutes_revision ||--o{ final_confirmation : fk_final_confirmation_4
    element_set ||--o{ final_confirmation : fk_final_confirmation_5
    template_revision ||--o{ final_confirmation : fk_final_confirmation_6
    exclusion_revision ||--o{ final_confirmation : fk_final_confirmation_7
    enterprise_identity ||--o{ final_confirmation : fk_final_confirmation_8
    material_snapshot o|--o{ final_confirmation : fk_final_confirmation_9
    meeting ||--o| publication : fk_publication_0
    final_confirmation ||--o{ publication : fk_publication_1
    publication ||--o{ publication_attempt : fk_publication_attempt_0
    final_confirmation ||--o{ publication_attempt : fk_publication_attempt_1
    external_operation ||--o{ publication_attempt : fk_publication_attempt_2
    publication ||--o| external_document_mapping : fk_external_document_mapping_0
    publication ||--o{ publication_governance : fk_publication_governance_0
    external_operation ||--o{ publication_governance : fk_publication_governance_1
    input_batch ||--o{ media_source : fk_media_source_0
    file_object ||--o{ media_source : fk_media_source_1
    media_source ||--o{ media_source_span : fk_media_source_span_0
    file_object ||--o{ media_source_span : fk_media_source_span_1
    meeting ||--o{ speaker : fk_speaker_0
    enterprise_identity o|--o{ speaker : fk_speaker_1
    speaker o|--o{ speaker : fk_speaker_2
    meeting ||--o{ correction : fk_correction_0
    transcript_segment ||--o{ correction : fk_correction_1
    meeting ||--o{ edit_operation : fk_edit_operation_0
    transcript_revision ||--o{ edit_operation : fk_edit_operation_1
    transcript_revision ||--o{ edit_operation : fk_edit_operation_2
    edit_operation o|--o{ edit_operation : fk_edit_operation_3
    meeting ||--o{ recording_session : fk_recording_session_0
    storage_backend ||--o{ recording_session : fk_recording_session_1
    file_object o|--o{ recording_session : fk_recording_session_2
    recording_session ||--o{ recording_part : fk_recording_part_0
    recording_part ||--o{ recording_chunk : fk_recording_chunk_0
    file_object ||--o{ recording_chunk : fk_recording_chunk_1
    recording_session ||--o{ subtitle_revision : fk_subtitle_revision_0
    recording_part ||--o{ subtitle_revision : fk_subtitle_revision_1
    meeting ||--o{ material_reference : fk_material_reference_0
    file_object o|--o{ material_reference : fk_material_reference_1
    material_reference ||--o{ material_revision : fk_material_revision_0
    file_object o|--o{ material_revision : fk_material_revision_1
    meeting ||--o{ material_snapshot : fk_material_snapshot_0
    material_snapshot ||--o{ material_snapshot_item : fk_material_snapshot_item_0
    material_revision ||--o{ material_snapshot_item : fk_material_snapshot_item_1
    material_revision ||--o{ parsed_document : fk_parsed_document_0
    file_object o|--o{ parsed_document : fk_parsed_document_1
    parsed_document ||--o{ parsed_unit : fk_parsed_unit_0
    meeting ||--o{ export_job : fk_export_job_0
    minutes_revision ||--o{ export_job : fk_export_job_1
    final_confirmation o|--o{ export_job : fk_export_job_2
    durable_task ||--o{ export_job : fk_export_job_3
    file_object o|--o{ export_job : fk_export_job_4
    meeting ||--o| meeting_deletion : fk_meeting_deletion_0
    durable_task ||--o{ meeting_deletion : fk_meeting_deletion_1
    enterprise_identity ||--o{ topic : fk_topic_0
    topic ||--o{ meeting_topic : fk_meeting_topic_0
    meeting ||--o{ meeting_topic : fk_meeting_topic_1
    meeting ||--o{ corpus_feedback : fk_corpus_feedback_0
    transcript_revision ||--o{ corpus_feedback : fk_corpus_feedback_1
    enterprise_identity ||--o{ corpus_feedback : fk_corpus_feedback_2
    enterprise_identity o|--o{ corpus_feedback : fk_corpus_feedback_3
    corpus_feedback ||--o{ feedback_segment : fk_feedback_segment_0
    transcript_segment ||--o{ feedback_segment : fk_feedback_segment_1
    corpus_feedback ||--o{ corpus_revision : fk_corpus_revision_0
    file_object ||--o{ corpus_revision : fk_corpus_revision_1
    enterprise_identity ||--o{ voiceprint_consent : fk_voiceprint_consent_0
    enterprise_identity o|--o{ voiceprint_consent : fk_voiceprint_consent_1
    voiceprint_consent ||--o{ voiceprint_sample : fk_voiceprint_sample_0
    file_object ||--o{ voiceprint_sample : fk_voiceprint_sample_1
    file_object ||--o{ voiceprint_sample : fk_voiceprint_sample_2
    enterprise_identity o|--o{ voiceprint_sample : fk_voiceprint_sample_3
    file_object ||--o{ evaluation_dataset : fk_evaluation_dataset_0
    enterprise_identity ||--o{ configuration_revision : fk_configuration_revision_0
    enterprise_identity o|--o{ configuration_revision : fk_configuration_revision_1
    evaluation_dataset ||--o{ evaluation_run : fk_evaluation_run_0
    durable_task ||--o{ evaluation_run : fk_evaluation_run_1
    file_object o|--o{ evaluation_run : fk_evaluation_run_2
    configuration_revision ||--o{ configuration_release : fk_configuration_release_0
    evaluation_run ||--o{ configuration_release : fk_configuration_release_1
    configuration_release o|--o{ configuration_release : fk_configuration_release_2
    enterprise_identity ||--o{ configuration_release : fk_configuration_release_3
    meeting ||--o{ mail_job : fk_mail_job_0
    minutes_revision ||--o{ mail_job : fk_mail_job_1
    final_confirmation o|--o{ mail_job : fk_mail_job_2
    enterprise_identity ||--o{ mail_job : fk_mail_job_3
    durable_task ||--o{ mail_job : fk_mail_job_4
    mail_job ||--o{ mail_recipient : fk_mail_recipient_0
    enterprise_identity ||--o{ mail_recipient : fk_mail_recipient_1
    external_operation ||--o| mail_recipient : fk_mail_recipient_2
```
