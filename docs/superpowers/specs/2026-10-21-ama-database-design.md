> 当前派生模型：B00-1.6-reconcile-scan，98 表、732 字段、189 个显式索引（含98个主键）。同步 Task6 typed 输入索引及 Task8 外部操作不可变扫描序号/唯一索引。下方原1.5正文原样保留为历史说明；完整未来审阅模型不等于已部署增量结构。实际013.3增量和保全证据见 [Task8 报告](../../../artifacts/phase-b/b-remaining/task-08/report.md)。

# AMA 数据库详细设计基线

当前设计版本：**B00-1.5-runtime-metadata**，98表/731列；在完整 B00-1.4-proposed 模型上仅新增两个运行时 sidecar。V013.1 的实际验证仅覆盖这两表及原部署模型不变性；未重跑完整未来模型验证。原 D01 三表与两列仍为 UNASSIGNED-D01，不由此启用。详见[运行时补充与证据](../../acceptance/b/database-runtime-metadata-task-02.md)。

父版本历史（保留）：当前设计版本：**B00-1.4-proposed**，96表/727列；仅设计提案与独立审阅库验证，尚未采用。新增3个规范化来源/解析选择关系及2个可空历史兼容字段。详见[来源补充规格](2026-10-22-ama-material-provenance-addendum.md)及[验证记录](../../acceptance/b/database-material-provenance-addendum-review.md)。正式SQL001..013（含011.1）不可变；未分配正式迁移号，运行时不变。B00-1.3四份原始文件及SHA保存在[历史清单](../../database/design-history/B00-1.3/manifest.json)，原93表实际metadata及raw evidence保持原路径原字节。以下1.3说明保留为历史范围，不授予新方案采用状态。

历史版本说明：**B00-1.3-review**，2026-10-22；状态：**生成结果关系持久化方案已获根任务技术 ACK，详细设计及独立评审待完成；不得据此声称 B07/正式迁移/生产通过**。已采用的 B00-1.2（85表/674列，含1.1 R1—R6）保持历史效力；本版新增8表，仅改变原表 sensitive_exclusion 的多标签主键和 CHECK。基线 `0d2079cd64e5b61bc5787ca5adfaa782e5cd4a40`，工作树 `/home/tanren/ama/.worktrees/phase-b-generation-design`；[本次记录](../../acceptance/b/database-generation-addendum-review.md)。

1.2 原始状态全文、设计/字典/DDL/声明源字节及SHA已保存于 [design-history/B00-1.2](../../database/design-history/B00-1.2/manifest.json)。原上传补充段标题曾写“待独立评审”，但同版本顶部及采用记录已明确通过；本版只更正当前标题并保留此前原字节，不重签或覆盖1.2评审。

既有采用历史：首版 `c73a765` 有六项 Important，未采用；`bd43ca3` 修正列表共同查询、个人名称去重、专题说明、同确认发布重试、转写处理快照与片段标记，经独立复审全部关闭，集成 `69f33a8`＋`bca9b89`，由 `6dd2cdd` 采用为 1.1。原始 BASE `e8c88d2aaed1a19b0d0202ddce9cb51ee01d84ee`、FIX_BASE `c73a7656fc1c5c2cb07d8b94ead68122059b549f` 与原工作树 `/home/tanren/ama/.worktrees/phase-b-database-design` 仅指该历史版；原评审及 metadata 文件保持原字节。

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
|minutes_template.current_revision_id → template_revision|B-07/D-04 验同template_id及owner，根name与当前修订name同事务一致|改名新增修订；历史名称不占当前命名空间；禁止悬空当前指针|
|external_operation.current_execution_no → external_execution(operation_id,execution_no)|B-05/B-08 锁subject/根/task/operation，CAS追加执行并验证同subject；不更换逻辑business_key|旧执行冻结保留，reconcile_epoch跨执行递增；UNKNOWN不得追加发送|
|transcription_input/source_revision 与输出revision/task|B-06/C-02/C-03/D-01 验同会议、同batch、source是已存在稿且不能为输出或其子孙；task typed input必须此快照|恢复/手工保存继承原processing_input；删除按反向生成依赖清理，不把历史输入改成当前设置|
|B阶段可空 material_snapshot、speaker、material locator|B-06/B-07 只允许尚未启用功能对应 NULL；D-01/C-03 开始非空写入前对应表/FK迁移已完成|审阅库建完整 FK；正式迁移按 registry 顺序，禁止 B-01 一次建全部未来表|

全局物理删除需要先解除 generation/exclusion/locator 等可能形成环的内容引用，再按拓扑逆序清理；FK 全为 RESTRICT，删除协调器显式规划同会议集，不能临时关闭 FOREIGN_KEY_CHECKS。跨会议共享模板、目录和治理数据不随会议级联删除。D-07 对 tombstone 的不可读、业务内容清除、文件删除、最小审计分别给证据。其他领域仍引用的受管文件不能当“删除成功已清除”，需移除会议关联并按批准范围证明没有会议正文副本留下；语料复用/法律保留没有企业规则时不开启。

## 统一事务与锁协议

使用 B-01 `TransactionRunner.required`、同一个 DataSource/Spring 绑定连接；推荐 READ COMMITTED 的短事务，迁移/应用必须显式确认隔离级别。CAS 同时检查 `expectedRevision` 与合法状态，0行即409/失效，不能继续执行效果。数据库死锁/锁超时只可重试整个纯数据库事务，有限次数＋抖动并复查权限；不可把已发外部请求的网络函数包进事务重试。

所有会触及多个领域的路径严格遵循以下锁等级，同级按稳定二进制主键排序，多根事务先按 subject_kind/id 排序锁齐。不得在 `advanceSubject` 之前先锁 meeting：它是事务入口的一部分。

1. 请求幂等作用域行（若有），再身份/授权身份行按ID；会话停用只锁身份及会话，不反向锁会议。业务写需要身份稳定时锁相应身份后再进入聚合。
2. `task_subject` fence；创建时 UUID 已确定，先插入 fence 再插入领域根。相同请求受幂等唯一键保护。
3. 领域聚合根（会议/治理根），再领域不可变版本、名单、ACL、发布根等按固定表名/主键顺序；需要修改的当前行预先锁齐，不在 callback 临时寻找另一个根。
4. 上传/录音会话与段（固定先 session 后 part），再 `file_object` 按ID，再 `file_reference`/upload_part/chunk/cleanup。引用新增和物理清理认领都走同一 file 锁；不得持 file 锁后再回头锁 meeting。清理使用先读候选ID、不加锁→按本顺序重读复验。
5. `durable_task`、`task_attempt`、`external_operation`、`external_execution`（按任务ID、操作ID、执行代际）。完成事务在第3级预先锁领域根；回调只能修改已锁同聚合数据，禁止外部I/O及引入更低等级锁。新插入不可变结果在已锁根下分配，无需反向锁旧域行。安全重试先非锁读出旧task ID，再按此顺序复验并锁全部旧/新task后推进operation，不得先锁operation再回锁旧task。
6. 审计 append，最后 `task_outbox_sequence(id=1)`、outbox INSERT，立即 COMMIT；分配序号之后不得再锁上述行，也不得调用网络。

`model_capacity`/reservation 准入使用**独立短事务**：先锁已提交的 external_operation及当前external_execution，复验operation token/execution_no/expiry/state，再锁model、reservation（FK父execution已锁）。不锁subject/meeting/task/outbox、不回调领域，任何路径不得model→operation/execution反序。完成/对账后的额度终结在本地业务事务提交后另事务依operation→execution→model→reservation顺序幂等执行；每次执行独立占额，遗漏会保守多占额，不会超发。外部执行历史只有reservation按批准策略解除后才可清理。`outbox_consumer`独立领取/续租；同库消费者要应用领域效果时先进入上述1—5级，再锁consumer，完成效果和cursor，最后outbox序列；不得持consumer后回调领域。跨领域消费建议按事件入幂等键并重新开启标准业务事务。

普通领取不能用一个锁 JOIN 先抢 task 再抢 subject。扫描 READY due 与 RUNNING expired 两个候选索引，非锁定得到小批ID；逐候选先 `SELECT task_subject ... FOR UPDATE SKIP LOCKED`，再根（若需判定领域状态），再 task `FOR UPDATE SKIP LOCKED`，重新验证状态/到期/版本/epoch，成功递增attempt并插attempt行。被锁候选跳过，不能阻塞后面全部工作。扫描分页有上限，每5秒执行，20秒租约、每5秒独立心跳；公平性按池配额＋队列等待时间提升排序，不能以 priority 永久饿死低优先级。具体吞吐和计划必须由 B-05/C-07 实测，不用本次建表证明30秒目标。

SKIP LOCKED 只处理行锁并返回不一致视图，因此只用于队列候选竞争，不用于名单确认、最终快照、权限判定等完整业务集合读取；DDL/元数据锁仍可能等待。[MySQL 8.4 locking reads](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html)

主要调用路径：创建=幂等→身份→新subject→meeting→审计→outbox；编辑/名单确认=幂等→身份→subject→meeting/roster/ACL→递增两处版本→审计→outbox；上传完成=subject→meeting→upload→file/refs→任务→审计→outbox；删除=subject→meeting(资格校验)→会话/文件→任务/操作状态→epoch及tombstone→审计→outbox；旧worker完成/发布查回=subject→meeting/领域快照→文件(若有)→task→operation→审计→outbox。外部查回不能反序先锁operation再锁meeting。

## 权限、会话和幂等请求

会话每请求从共享DB验证 token_hash、expiry、revoked_at、identity ACTIVE、session_generation 完全相同，无节点本地授权缓存。停用/全退出同事务递增 generation 并审计；再启用不会恢复旧代际。单会话退出写 revoked_at。公司协议未落实前只允许生产resolver fail-closed，没有生产签发/回调/测试登录口。Cookie/CSRF/Origin 和WebSocket绑定由 B-02 按接口契约验证；寿命取明确配置，不从本设计发明企业时限。

负责人通过 `meeting.owner_id` 获得 READ/EDIT/MANAGE；有效名单企业成员仅 READ。角色不会覆盖对象权限。`roster_revision/roster_member` 全部禁止被授权查询读取；只有明确确认动作在同事务冻结名单、检查每个企业稳定ID、计算增删 `meeting_access_grant`、增加 access_revision、根revision/fence并审计。外部人员没有企业ID不授权；成员读取会前最新有效保存稿、发布后固定快照，绝不候选/未保存草稿。B02旧 `meeting_acl` 停止授权前须迁移确认来源；不能仅 `confirmed_at IS NOT NULL` 推断业务确认。

幂等作用域是 actor/resource/operation/Idempotency-Key 的长度前缀编码 SHA256，保存完整请求摘要。同scope不同摘要409；同摘要返回原安全对象ID/状态并再次鉴权；不得重执行外部效果。并发唯一键插入等待获胜事务提交后比摘要；业务回滚幂等行也回滚。对任务 enqueue 使用独立 producer_key，领域操作稳定键＋输入指纹生成，冲突比较完整spec。无默许TTL清除已完成key导致重复创建。普通保存使用CAS；两个窗口只有一个版本更新成功。

## 个人名称和专题说明（R2/R3）

个人模板及专题分别在当前用户个人范围去重；技术等价规则固定为 Unicode NFC 后剔除首尾 Unicode White_Space，内部空白保留、字母大小写保留，完整规范结果按utf8mb4_0900_bin/UTF-8字节比较。纯空白名称拒绝，不截断、不新增产品最大字数。展示名保存用户提交值；完整规范名为LONGTEXT，name_hash为规范名完整UTF-8 SHA256。摘要索引**非唯一**：哈希相同需逐项完整二进制比较，完整相同才重名，碰撞但完整不同允许；不得用ai_ci列比较或“哈希相同即冲突”。这与列表搜索的英文大小写不敏感是不同语义。

创建、改名以及释放当前名字的删除都先锁 `enterprise_identity(owner_id) FOR UPDATE`，作为该用户命名空间串行化锁（全局锁序第1级，早于subject/模板/专题根）。即使两个请求各创建不同随机UUID，仍竞争同一已存在owner行；不存在身份拒绝，不懒建独立锁。锁内计算规范名/摘要，按(owner_id,name_hash)读完整候选、排除当前根ID并比较；之后按全局顺序锁根并CAS，写名称、revision、audit并提交。模板和专题查询各自表，彼此不占名字；不同用户同名允许。批量多用户锁按二进制owner排序，禁止拿到根锁后才补owner锁。

`minutes_template` 是逻辑模板根，只登记当前名称与current_revision_id；`template_revision`保留每版名称/主题且不变。新建根与首版在同事务建立，当前指针由应用验证同template_id；改名追加修订并更新根，旧名称只保留历史、不阻止以后新模板使用。内置模板owner=NULL，由受控配置维护，个人不能修改；个人范围查重不借历史版本或他人名字阻止创建。B-07/V012建立逻辑根，D-04复用，不创建第二套模板版本。

`topic.description LONGTEXT NULL` 保存可选说明，未填写/空串规范为NULL，不加产品字数上限。创建保存name/description；修改同时携expectedRevision，名称冲突或CAS失败整笔回滚且保留原说明。说明单独修改也递增专题revision，重新读取与分组展示均取此持久值；不改变会议正文/确认/ACL。仅空专题可删除，删除走同owner锁释放当前名字。D-08/V023拥有持久化及并发验证。

## 工作稿、快照、候选与最终确认

转写 RAW/ENHANCED/WORKING/CANDIDATE 为不同不可变revision；保存产生新revision，稳定segment ID与实际源文件定位保留；纯转写可无时间、无说话人，不造音频。确认前校验已保存非空有效正文、零PENDING纠错、来源完整、当前revision及权限；插入 transcript_confirmation 并更新当前指针，可靠任务/outbox 同事务。B初始输入可无材料，指纹把NULL材料规范化为空集合；材料已启用后不得用NULL绕过未应用变更。

**R5：确认前的处理来源必须可恢复。** `input_batch/input_batch_file`只负责不可变主文件及顺序；新 `transcription_input`独立冻结batch、处理类型IMPORT/ASR/ENHANCE、重新增强所据已保存source_revision、明确应用material_snapshot、parser/model/prompt配置固定版本、处理配置完整安全manifest及schema版本、base_revision/cancel_epoch和inputFingerprint。不能用依赖已确认转写的generation_input代替它，也不能只留下哈希再读取meeting当前材料/“latest”配置。无模型的纯导入明确model/prompt=NULL且parser必填，未用提示词的模型步骤prompt=NULL，manifest标识确实未使用；ASR/ENHANCE的model配置必填，ENHANCE必须有已存在源稿。配置manifest保存可恢复的步骤/版本/来源引用、禁止密钥/正文副本，真实内容仍经受控文件引用取用。

B-06/V011中的材料以material_mode=EMPTY、material_snapshot_id=NULL代表规范空集合，不代表未知；指纹编码明确EMPTY及输入schema版本。D-01启用非空材料前，V017先建立material_snapshot及transcription_input→material_snapshot FK；PINNED必须有具体快照且同会议。材料应用后重新增强在subject/根锁下冻结新transcription_input并同事务入队，旧输入/人工稿/确认/纪要仍保留。task.input_kind=`TRANSCRIPTION_INPUT`、input_id=本快照ID，task.input_fingerprint必须相同；结果transcript_revision.processing_input_id/producer_task_id反向关联这次输入/任务，batch必须与输入batch相同。链式模型步骤在前一步完成事务内另建该步transcription_input：继承固定batch/材料/配置来源，将具体前一步输出ID、父输入ID及步骤配置写入新的config_manifest，计算本步fingerprint再入队；不得回填原快照或从当前根取变化中的值。若前一步为已保存转写，source_revision_id同时指向它；其他媒体中间结果用manifest的受控typed引用并验证其同会议和已保存状态。

人工WORKING保存或恢复历史创建新revision并继承被编辑/恢复稿的processing_input_id；其来源不能换成会议当前应用材料。输入指纹体现处理输入，确认content_hash另覆盖本次实际保存正文/来源/标记，二者不能混用。迟到结果只有subject版本/epoch、任务token和该inputFingerprint均匹配才可按候选链登记，不移动当前稿；新材料重增强期间人工编辑导致冲突保留历史候选。来源合法性按processing_input固定batch/源稿/材料清单验证，历史查看和恢复使用该链，不追随当前配置。

**R6：片段标记是持久输入。** `transcript_segment_mark(revision_id,segment_id,mark)`复合PK表示0到4类多选且单类不重复；只允许KEY=关键、CONCLUSION=结论、DISPUTE=争议、GENERATION_FOCUS=重点生成，FK指该版本片段。增选/取消均随显式保存创建新transcript_revision：复制未改片段/标记，再应用指定标记集，旧稿及确认快照不变。标记不改原文、不证明真实，重点生成只供参考。

确认content_hash按schema版本、稳定segment_id/顺序、正文、来源、说话人和每段按固定枚举序排序的**完整标记集合（含空集）**编码；generation_input指纹除确认ID还包含该content_hash，重新增强输入含source_revision完整内容/标记摘要。已经确认后改动任何这四类生成参考标记，保存事务必须清空当前transcript_confirmation_id和final_confirmation_id、递增meeting.revision/subject.business_version、标needs_generation=1；重新确认前禁止继续生成/最终确认。旧确认/纪要/标记仍可按旧版本查看；已发布只读不能改标记。B-06/V011建立表和hash规则，C-03复用编辑入口并验证并发保存/确认失效。

generation_input 固定确认转写、材料集合、模板、排除集、要素集、prompt/model版本、生成scope、基线revision；按schema版本、字段类型与稳定排序做规范化编码后 SHA256。空集合与缺失/未知不可混同；指纹包含源文件版本/哈希与来源清单，不以更新时间猜版本。模板空/内置和排除空集也有受控版本；模型/提示词配置B阶段允许受控文件版本，绝不固定写“latest”。

模型结果先存 generation_candidate 及候选正文/要素，当前有效指针不动。采用须当前根revision、inputFingerprint、目标区域版本都等于生成基线；并发人工编辑则409保留候选。全文采用生成新有效保存版本；局部采用只产生对应要素新集，正文保持旧稿并标为需要生成。`needs_generation` 与待处理候选查询是不同事实：生成成功可以清除前者，`PENDING`候选仍阻断最终确认；候选明确拒绝/过期处理不能冒充采用。

最终确认锁根一次读取全部版本：名称/分类满足已定规则、确认名单、转写、已保存正文、已应用材料、要素、模板、排除、来源关系一致；没有未处理候选、待应用变更或敏感冲突。客户端未保存草稿不进入后端确认；API payload只能引用服务端保存版本。检查成功插 immutable final_confirmation，保存清单hash。确认后实质编辑清空当前final指针并递增revision，原confirmation仍保留历史。标题origin=HUMAN后会议改名/AI不得覆盖；恢复旧稿创建新版本且保留操作人时间，不回拨版本号。

来源定位按 TRANSCRIPT/AUDIO/PDF_PAGE/PPT_PAGE/WORD_PARAGRAPH/TEXT_PARAGRAPH/KNOWLEDGE_VERSION 分类型；对应字段必填且非适用字段由schema拒绝，页段真实存在，所有引用属于固定输入集合。证据只指向具体要素/正文版本；背景材料不能独自支持重要会议结论和行动，未证实为UNSUPPORTED/PARTIAL，不能造置信度。排除设置不删除源、核心要素或改变ACL，只有下一次生成应用，并在最终确认复验人工加回的冲突。

## B00-1.3 生成结果持久化增量（技术方案已 ACK，独立设计评审待完成）

根任务已完整阅读初稿 SHA `6abe12335303c176331089814e56aee32aae7b2c01a77ecc3cd9741350b044ff` 并裁定继续详细设计。初稿/报告原字节由根保存；此处落实裁定，不把初稿推荐项或待答用户问题变成产品规则。
新增七张目录/建议/复核表属于已有预留 V012；`final_result_review`属于已有 V013，与 `final_confirmation`同批创建并立即具有指向确认与复核的两个 physical FK。没有新增 deferred FK、空子表、迁移编号或 registry 修改。完整审阅 DDL 在设计库一次建立全部表和全部 FK，不替代分期正式迁移。

|原始需求/缺口|实体和保证|未由schema证明的部分|
|---|---|---|
|模块III 6.4 未选敏感检测及同位置两个标签|sensitive_detection + sensitive_detection_source；来源无序集、同类序号唯一；exclusion PK含category|检测完备性/隐含泄漏、人类核对、源权限|
|模块III 6.3/7 背景补充和来源|background_note + background_note_source；逐条保存input_id和正文|背景不能冒充会议决定；语义质量|
|模块III 6.6 原始负责人/日期建议未核实|action_suggestion与已接受action_item分离，继承精确input_id|企业人员选择、外部负责人待定；不授ACL|
|模块III 6.5/7、IV 5.3 精确证据复核|result_review + result_review_evidence；完整target/source hashes、服务器身份/时间|人类断言不等于真值；同会议/目标/集合/身份由事务核验|
|模块IV 5.4 最终历史不可变|final_result_review冻结精确成员，进入source_manifest_hash|最终业务检查和发布仍由B08实现|

七类主体要素枚举、已有 generation_input 及其固定版本图不变；新增表逐字段/PK/索引/CHECK/FK见完整字典。没有源文摘录副本、原始模型JSON或新文件元数据附件。来源alias/UTF8、anchor/hash编码与语义coverage仍依单独B07契约；这里规定必须记录哪些事实，不擅自采纳尚未评审的codec。
SOURCE_SUPPORT与ACTION_FIELDS严格分离。`minutes_element/evidence_link.support_state`保留创建时分类；当前人工支持必须查精确断言，最终历史必须查冻结成员。第一次自动有效保存与明确采用、逐项核对与合并确认两个交互问题仍待用户答复。字段允许明确adopt+review在同事务新建目标和新的断言，绝不自动复制历史review信用，也不强制额外点击。

### 集合、序号与去重

A detection is one model directory entry with one category and a nonempty set of sources; ordinal is one-based response order within that category. A note is one background entry in one-based order. Preserve distinct model entries even when their text/source sets happen to match; do not silently merge summaries. Response-local keys are validated then mapped to new UUIDs, not stored as database IDs.
Source relations are unordered sets: duplicate identical locator IDs reject during validation, PK protects persistence. Exact physical locator dedup is per meeting and full typed locator projection (including revision/file/material version); under root lock compare all nullable fields and exact section_path bytes. No lossy hash-only uniqueness or product length restriction. Equivalent location uses one server locator ID across both labels.
Exclusion is the set of (locator_id,category) within one exclusion_revision. Selecting a multi-source detection expands all its locators under its own category. Overlapping detections union these pairs. Clearing a row unselects only that category/location; other label survives. Display selected state derives from these pairs, not a mutable flag on detection. Partial overlap shows partial selection; directory counts count detection entries per label, not unique physical locations.
A new exclusion revision freezes the complete selection; empty set remains explicit existing empty revision. Exclusion behavior applies the union of selected physical source locations, but persistence/fingerprint/audit retain both labels. Copy-on-write never edits source text, existing body, old detections or old exclusion rows.
Review evidence is an unordered, duplicate-free set of existing evidence IDs. review_no is an append sequence allocated MAX(review_no)+1 under the meeting root lock, not a global AUTO_INCREMENT or wall-clock ordering. It is independent of content revisions. All content-bearing rows are immutable; only existing candidate decision state and meeting/subject pointers follow existing CAS rules.

### 精确复核断言与失效

SOURCE_SUPPORT reviews one exact element text span or one exact minutes body span. target_anchor uses the separately approved B07 anchor contract (unit, range, exact text hash); its encoding is not adopted by this schema proposal. Review cannot target a directory note as an important meeting decision. ACTION_FIELDS reviews the complete saved action tuple: exact element body/category plus owner_enterprise_id, owner_display and due_on including NULL.
`hash_profile` resolves an archived immutable nonsecret profile defining canonical typed encoding and domain separation. target_hash covers meeting, exact target row ID, its set/revision, exact full target content and anchor (or complete action tuple); source_set_hash covers every selected evidence ID, exact target/anchor, complete locator union, source role, immutable confirmation/file/material versions and content hashes. Canonical set sort is unsigned binary stable IDs. Do not hash display URLs or current/latest material/config.
The reviewed set must equal the complete persisted evidence set intersecting the reviewed target range, not a client-chosen supportive subset; for ACTION_FIELDS use all element evidence. Range membership/bounds, hash equality and same-meeting checks are recomputed from locked immutable rows. Empty/partial sources can be honestly reviewed UNSUPPORTED/PARTIAL. Background-only evidence cannot establish a meeting conclusion or action, including after a human click.
The schema records a human assertion, not proof of semantic truth. SOURCE_SUPPORT=SUPPORTED requires an explicit authorized source-support attestation with the target and complete source set shown and readable. No model output, valid source ID, generation/adoption click or generic final button alone generates this assertion.
Per-item attestation versus an explicitly worded combined final confirmation is **pending user answer**. Both write the same rows; a combined transaction may create several explicit assertions and final memberships atomically. This proposal adds no required extra UI click or invented corporate reviewer role. Meeting owner rights and independent source access are validated by existing ports; management role alone does not grant them.
reviewed_by is server-derived stable enterprise identity, reviewed_at is server UTC microsecond time. Client submits expected root revision, exact target/source hashes, claimed outcome and the content-specific review intent; server recomputes all of them except human judgment. Logs/audit hold IDs/outcomes only, never copied excerpts.
Existing minutes_element/evidence_link.support_state remains the immutable classification at that result version's creation. It cannot alone establish current reviewed support. Effective reviewed status is the latest review_no for the exact target ID/kind/anchor/hash/source-set combination; latest PARTIAL/UNSUPPORTED supersedes earlier SUPPORTED. Concurrent reviews use CAS; do not use “any historical SUPPORTED”. Full-element support requires approved coverage rules over all relevant statements, not one supported span. Those semantic rules remain a contract prerequisite.
A review write advances meeting.revision/subject.business_version and clears current final_confirmation_id; it does not rewrite body or the frozen classification. New content/source/anchor/version IDs invalidate prior reviews for current support even if rendered wording matches. Candidate adoption or restore never copies old reviewer identity/time into a new attestation. An explicit content-specific adopt+review intent may atomically create the new target and a fresh attestation with current actor/time/recomputed hashes; it does not require a second click merely because the new target ID is allocated in that transaction. Old review remains inspectable as history only.
At final confirmation, choose exact applicable review IDs under root lock, insert final_result_review rows, and include those IDs plus full review projections and source graph in existing final_confirmation.source_manifest_hash. Hash/profile must be reproducible from stored rows. Later reviews cannot change this membership or historical manifest. Do not resolve a past final snapshot with a newest-review query.
ACTION_FIELDS=CONFIRMED is independent of SOURCE_SUPPORT. It confirms accepted field choices, never evidence sufficiency or access. A human save creates a new element_set/element/action_item and review bound to that exact accepted tuple; missing owner/date may remain NULL under current rules. No raw suggestion is copied into accepted owner/date fields merely because structurally valid. External-owner resolution remains disabled/pending, not guessed.

### 候选、有效稿、历史与权限

Completion validates the full output, maps typed sources, and inserts candidate minutes/set/elements/evidence plus all five directory/suggestion tables in one transaction. A failed child insert leaves no partial candidate. Initial action_item owner_enterprise_id/owner_display/due_on stay NULL; model values live only in action_suggestion. Initial support is conservative/unreviewed under separately approved policy, never SUPPORTED by structural validity.
Candidate directories belong to candidate.element_set_id. All display paths must check owner/version reachability; the existence of a minutes_revision/element_set row does not make it an effective participant-readable version. No candidate directory leaks through generic list/history endpoints.
Adoption checks unchanged root/base/input fingerprint and current confirmed settings; creates new effective minutes/set/element rows, remaps directory/suggestion/evidence rows, and retains original candidate history. Link exact old/new source locators; no copying review credit to new target IDs. HUMAN title is preserved; linked title changes require explicit relink; unsaved editor text remains protected outside worker state. ELEMENT adoption affects only intended element set; body stays saved old version and needs_generation is set.
First result guarded automatic save versus explicit adoption remains **pending user answer**. This schema works with either authorized policy; no enabling column/state implies one. Until resolved, implementation cannot silently expose first candidate as effective or treat adoption/final confirmation as automatic source attestation. A future guarded auto-first path must use the same atomic adoption/CAS/protection checks.
Manual element edits create new sets and retain unchanged directory/suggestion rows by copying to the new owner IDs; each copied detection/note/suggestion retains its own input_id, not a new model origin. Fresh generated rows have candidate.input_id; partial regeneration may carry unchanged rows with older pinned inputs, checked per row. Deleted elements omit their action_suggestion/action_item rows in the new set. Body-only edits create minutes revisions without rewriting core elements/directories. Changes to source evidence create a new target version, not repair history in place.
Restore previews exact old content/settings/source availability, then creates new revisions and remapped rows, clears final confirmation and rechecks exclusions/current source constraints. It never rewinds IDs/revisions or revives review credit. Historical candidates, detections, suggestions and reviews remain immutable; no TTL or product history limit.
Owner can inspect candidates/history through EDIT-authorized routes; confirmed participants see only permitted effective saved versions/history per existing policy, including sensitive core entries, and never pending candidates. Published reads bind the fixed final graph. Sensitive selection does not hide core detections or alter meeting ACL.
Every locator/excerpt read independently checks source permission/status/version. Render source excerpt from authorized immutable source only; no invented or copied fallback excerpt. Raw owner mentions are meeting content, not enterprise identity or authorization. Review record possession grants no meeting/source rights. Withdrawal/invalidation/tombstone blocks ordinary read/export/reuse as adopted.

### 完整性、事务与删除

On every write resolve meeting via authoritative parent rows. set→candidate/input, each locator→confirmed transcript segment or exact pinned batch/material snapshot, evidence→same target and meeting, review→same meeting/target/evidence, final membership→same final minutes/set are checked in owning transaction. UUID validity and individual FKs are insufficient. Source kinds/nonnull union, actual locator bounds and background-vs-meeting role are validated; nonempty materials still await D01, never silently resolved to latest.
All writes follow B00 global order: request idempotency→active identity/authorization rows→task_subject→meeting→fixed-order domain rows→file locks/refs if needed→durable_task/attempt/operation/execution→audit→outbox sequence. No network or late lower-order lock inside result/review callbacks. Reviewed-by identity is authenticated before subject/root locks; ACL is rechecked while locked.
Worker commit rechecks undeleted/nonpublished root, subject business_version/cancel_epoch, task attempt/owner/token/unexpired DB lease, immutable task input ID/fingerprint, candidate base revision, current confirmation and settings. Candidate+all children+task success/output ref+audit/outbox commit together. Unique candidate_task makes duplicate completion compare/reuse one full result, not duplicate directories. Fenced/expired/stale result cannot alter effective pointers or attach partial metadata.
Review/adoption/exclusion/manual save/finalization each use idempotency+expectedRevision CAS. Multi-item final review is all-or-nothing. A stale UI or revoked identity/source access causes rollback with safe codes and input preservation. Review and candidate completion cannot race unnoticed because both lock subject/root and advance/validate the same fence.
The proposed tables add no file-reference kinds. Reuse existing source locator/file ledger rules; a reused locator's underlying immutable source retains its existing managed references. Register any newly introduced real source reference through its owner/file ledger in the same transaction, never treat a source join as permission or unmanaged file retention.
Deletion first fences/tombstones meeting and cancels work under existing order; ordinary read is blocked before cleanup. Clear current pointers, freeze minimal diagnostic IDs, and use existing meeting deletion dependency plan. For this delta delete final_result_review before final/reviews; result_review_evidence before reviews/evidence; directory joins before detection/note/locators; action_suggestion before elements. Then remove existing candidate/input/exclusion/evidence graphs in dependency order, file references under file locks, and clean bytes only when no approved references remain.
No foreign-key disabling, recursive cascade, source deletion on unselect, shared-template deletion or automatic historical cleanup. Historical reviewer identity need not survive directory removal; review content/hash/source IDs are business data and removed with authorized meeting content, leaving only existing minimal audit policy. Retention/legal-hold ambiguity blocks cleanup, not permission bypass.


## 租约、外部效果与对账协议

普通领取只针对 READY 或已过期且未标记外部效果的 RUNNING。领取递增attempt，归属worker，expiry=DB now+20秒；续租须同attempt/owner、expiry严格大于DB now、版本/epoch匹配、未删除。过期不能续活。完成先按统一锁序锁根/task，核对完整不可变spec、fingerprint、business_version、cancel_epoch、attempt/owner与仍有效租约，然后同事务写领域结果、task、attempt、audit/outbox；检查失败不执行效果。`completeWithEffects` 优先；裸complete返回false后调用者不得继续写业务。

每任务 max_attempts 必须由受控任务策略明确赋值（开发初始5；是可配技术策略，不是公司承诺），包括崩溃回收；没有效果且确定可重试时退避5/10/20/40秒加有界抖动，第5次终结FAILED。用户重试产生新 task/producer_key 保留原输入；不是重置旧 attempt。参数/权限错误直接FAILED；旧业务版STALE，取消CANCELLED并保留最小诊断；不靠无限RUNNING回收耗尽平台额度。

任务粒度为一次固定输入的处理步骤/外部调用；同一模型的多个音频窗口/片段或分段生成分别入队，以各自稳定输入指纹和producer_key去重，不能在一个task内循环发出未登记的多个同类效果。流水线下一步在前一步完成事务中以typed输出快照入队；每个外部调用task只拥有一条external_execution，稳定external_operation可在确定零效果后拥有多代执行，不重写旧行隐藏调用历史。任务/操作进入未知后只由独立对账流收敛，不从普通自动重试路径重新发网络。

任何可能引起外部结果的调用先`beginExternal`：锁subject→根（冻结发布成果及拒绝编辑/删除）→task→operation→execution；验证task当前租约。首次建立稳定external_operation(subject、operation_kind、business_key、逻辑内容request_hash、current_execution_no=1)，并插execution(task_id、task_attempt、完整请求hash/manifest、MARKED、marked_at)；安全retry则只将已登记的当前PREPARED执行转MARKED。task转RECONCILING、operation转MARKED、记outbox，**实际提交后才允许发网络**。逻辑operation不再只有一个task外键；每次调用由execution精确归属新task/attempt。入口拒绝ambient事务或专用真实afterCommit调度；REQUIRED返回不等于提交。提交后发出前崩溃仍作为未知，不能假定未调用。

普通claimer完全排除RECONCILING。独立reconciler扫描MARKED/UNKNOWN且due的operation，非锁读当前execution/task ID，再按subject→根→task→operation→execution锁定并复验current_execution_no未变，CAS领取reconcile_epoch/owner/expiry（20秒、5秒心跳）后短事务提交。查询在锁外；结果写回须(operation_id,execution_no,reconcile_epoch,owner,未过期)全部匹配。旧worker不能凭旧task/operation token完成；可提交安全观察值供当前所有者权威复查，不能抢回所有权。确定成功记当前execution/operation及映射，在同业务版本/epoch且合法状态时完成领域；失效结果仅诊断/隔离候选。原task租约过期不丢弃外部事实，执行切换也不能静默忽略矛盾成功。

beginExternal初次提交令reconcile_epoch=1、owner=发起Worker、expires=DB now+20秒、next_check_at=该截止，返回含execution_no的独立token；之后每次安全retry/接管/标记只递增reconcile_epoch，**永不重置为1**。初始调用者也按独立token登记结果，原task token无完成权。接管只在operation租约失效且due后发生。调用前再验token不能消除“检查后暂停、醒来才发出”的外部竞态；非幂等平台安全重放还必须排除旧发送者在截止后重新发起（服务端强制绝对截止/fence或确认执行者隔离）。仅lease到期/等待不构成证明，无能力保持UNKNOWN/MANUAL_REQUIRED。

`NOT_FOUND`不是确定未执行：原请求可能仍在途、平台读可能最终一致。只有平台原生幂等创建（相同键相同效果已验证）或明确安全截止＋唯一可查回/草稿提交协议证明不会迟到创建，才允许按同键重放；否则继续UNKNOWN或MANUAL_REQUIRED，禁止第二次创建。UNKNOWN不当作FAILED，不允许编辑后另起发布；无幂等/可靠查回能力的知识库/邮件为真实集成阻塞。本地唯一键只限制本地记录，不能证明外部唯一。

**R4：同确认重试的可执行协议。** publication每会议一个根，业务键始终为`publicationId + confirmationId`无歧义编码；external_operation唯一(operation_kind,business_key)代表稳定逻辑效果，external_execution唯一(operation_id,execution_no)及unique(task_id)代表追加执行。请求已明确失败且**确定零外部效果**时，当前execution置FAILED并记录no_effect_basis（NOT_SENT/DEFINITIVE_REJECTION/PROVEN_CUTOFF_FENCED）、受控证据引用、verified_at和finished_at，operation及旧task终结FAILED。HTTP超时、普通NOT_FOUND、lease过期不能填该证明。

用户保持原confirmation重试时无需新确认：按幂等→身份→subject→publication根→相关task→operation→旧execution锁序，重新验证原确认仍有效、权限/目标、从未成功、operation=FAILED及完整零效果证明。CAS current_execution_no=N→N+1并递增reconcile_epoch以永久隔离旧token，创建新task（producer_key含operation.id及新execution_no）及PREPARED execution（完整请求hash/manifest、task_attempt/marked_at=NULL），operation=RETRY_QUEUED，publication=READY，与审计/outbox一并提交。旧task/execution/confirmation全保留、同business_key不变。PREPARED不代表已经调用；普通claimer只领取其新READY task，到beginExternal才按上一段真正提交marker并调用。RETRY_QUEUED不进入未知结果查回扫描；崩溃在marker前由B-05普通租约恢复，若无调用就达max_attempts，当前token流程以NOT_SENT证明终结FAILED。

同键双击重放同一次受理结果；不同请求键的并发retry在同subject/operation锁下只有第一笔从FAILED推进，第二笔看到RETRY_QUEUED/MARKED返回已有执行或明确冲突，不能追加第二条发送。旧执行迟到结果因execution_no/epoch不匹配不能提交当前效果；若出现与确定零效果证明矛盾的成功观察，记录安全诊断并进入MANUAL_REQUIRED/权威查回，阻断下一次发送，不能将其直接改为普通FAILED；平台若违反已验证截止/拒绝保证则集成资格失败。

成功一次将当前execution/operation/publication置SUCCEEDED、写唯一provider+externalDocumentId映射及ever_published=1，task/audit/outbox同事务，之后不允许再发。真实内容修改后才新增confirmation并产生新逻辑键，不能为重试假造确认。每execution完整manifest保留当次库/目录，明确零效果后可按产品规则修正目标；平台适配器必须证实稳定业务键下的失败重试/更正目标语义，若供应商缓存失败或拒绝同键变参而无安全协议则报平台前置，不能偷偷更换稳定业务键。撤回/废止仍先本地阻断、外部对账，不允许恢复终态。

operation.request_hash固定编码schema版本、operation_kind、business_key、confirmation_id及确认内容hash；execution.request_hash另编码上述逻辑hash与本次完整library_id/folder_id、渲染及发送配置固定版本、请求参数清单。目录/连接配置的纯投递更正与新执行受理在同事务保存到publication当前目标及新execution manifest，使用publication.revision CAS；它不修改已确认正文或meeting业务revision，不以投递配置改变强迫重新确认。若某配置实际改变确认内容，则走原内容修改/重新确认规则。B-08必须测试“失效目录明确拒绝且零效果→同confirmation更正目录→新execution完整hash改变、逻辑hash和业务键不变→成功”；适配器上线前用平台证据验证此请求参数/幂等键契约。平台不允许同键变参时，该分支保持明确失败并提示真实接口能力阻塞，不发新键绕过、不泛称平台已支持安全重试。

模型额度按operation→execution→model→reservation锁序复验当前独立token/执行号和enabled/config_revision，计数未settled reservation后按execution_id唯一插入，持有本执行reservation才调用。marker已提交而准入失败只能由有效token流程明确终结本execution“未调用”；接管者不能凭暂未查到reservation推断安全重发。timeout/cancel/lease失效不释放额度；有证明的safe_deadline微秒归一，无证据NULL保守占额直到明确终结。未知容量fail-closed、缩容不得低于在途量；execution确定终结提交后另事务幂等释放，崩溃遗漏由确定执行状态对账回收，不能凭逻辑operation的新一代状态释放旧执行未知额度。

邮件批次 `mail_job` 绑定协调task及保存版本，每个 `mail_recipient` 对应独立发送task/operation和 `jobId+enterpriseId` 消息键。发送前再验企业成员已确认、当前READ、负责人发件权限和发布/撤回状态，只发送受控版本链接。批次部分成功时，SENT收件人永不重发，只对UNKNOWN收件人按其键查回；无需假设供应商批量发送原子性。无单消息幂等/查回能力时保留UNKNOWN/MANUAL_REQUIRED，不能重新发整批。

## Outbox 提交顺序和游标

outbox不是普通AUTO_INCREMENT高水位。所有生产者最后锁 singleton sequence，分配连续号、插引用事件并立即提交；锁持续到COMMIT，因此另事务无法先提交更高号。回滚同时退回sequence和event；批量分配在一事务内，消费者只有已提交连续前缀。统一规则也涵盖录音字幕和非task领域事件（task_id可空）。事件只含安全引用，禁止正文/令牌。

consumer按sequence升序取有界页，成功完成连续前缀后推进cursor，失败不跳过；本地效果与cursor同事务，外部投递至少一次、对端以事件号幂等。消费lease/epoch防旧投递者推进游标。浏览器按授权subject分页，服务器内部游标可越过不授权事件但不得暴露内容/总事件详情；撤权/终态时结束订阅。事件清理/归档未定义前不自动删；将来提供明确定义的cursor过旧reset＋快照协议才允许收缩。全局序列可能瓶颈，B-05需延迟提交并发测试，C-07需含字幕/50 chunk ACK每秒负载验证，不能直接声称足够。

## 文件、上传、录音及删除

受管文件始终通过 file_id 路由 backend/type/namespace/object_key/provider_version/size/sha256；业务表不存物理根目录或公开签名URL。local无需S3配置，后端类型与ID不可改含义。put/complete先确保持久化文件与目录（或S3已验证durable响应），然后登记file/引用/业务状态，再返回成功。DB回滚的孤儿不在file表/无业务引用，不可读；枚举清理记录受限前缀及固定后端，保护仍在途upload/recording与恢复窗口，不能把本地文件＋DB当跨资源事务。

### B00-1.2 上传业务元数据补充（已采用；原待评审标题见保全历史）

具体缺口来自 B-04 的既定 HTTP 契约：初始化 `POST /meetings/{id}/uploads {purpose,name,size,sha256}` 返回 uploadId/partSize；完成请求只含 `{parts:[{part,sha256}],sha256}`，不重传 name/purpose。`StorageContracts.UploadSpec` 只有 backendId/namespace/objectKey/expectedSize；`LocalUploadJournal.begin` 将这一 spec 持久化，不能恢复业务名称或用途。`file_object.display_name` 只能在完整 blob durable 后登记；不得提前插 AVAILABLE 文件来保存初始化参数。因此唯一结构增量是 `upload_session.display_name LONGTEXT NOT NULL` 与 `purpose VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL`，追加到原字段清单后，不改变已采用列顺序或其他表结构。

名称完整保存初始化原值，重启、完成、重放均不另从浏览器、文件路径、对象键或临时内存推导；它是展示数据，不参与物理路径和 shell，输出按具体 HTML/HTTP 上下文安全编码，不在数据库做通用转义。LONGTEXT 沿用已有 `file_object.display_name` 的容量语义，不新增产品名称字数上限。purpose 沿用现有 CODE 与 `file_reference.purpose` 类型；服务端只接受配置中已启用且当前身份有权使用的安全 ASCII 码，并按精确大小写比较。配置码限定为非空 ASCII 字母/数字/下划线/连字符且能无截断放入既有 CODE，校验由 B-04 服务承担；64 是内部协议码的既有存储预算，不是用户文件名、文件大小或用途数量限制。本补充不创造产品用途清单、公司分类字典或新授权角色，未知/停用/无权用途拒绝。

|既定输入或恢复事实|持久事实及消费者|B-04 必验路径（本轮未执行应用测试）|
|---|---|---|
|begin.name|upload_session.display_name；完成后逐值复制到 file_object.display_name|含中文、引号、反斜杠、换行及长名称，API 进程重启后完成，完整值无损且不作路径|
|begin.purpose|upload_session.purpose；完成引用账本使用相同 purpose；后续领域绑定再次验证用途|begin 后重启且完成请求不含 purpose；不能借重放改变用途或绕过停用/权限|
|存储端 begin 返回 UploadToken|backend_id＋既有 provider_upload_token 保存其 sessionId，local 也必须保存；字段名称不变|API 重启后重建同 token；默认后端变化仍回原后端|
|complete 的决定|FINALIZING＋全部不可变 upload_part 行＋expected_size/expected_sha256＋固定位置|冻结后/API 重启/存储成功但 DB 失败，恢复同一清单与同一结果|

初始化先检查已提交的幂等结果，命中时重新鉴权并返回原 uploadId；未命中则验证身份、会议写权限、生命周期和启用用途，生成服务端位置，在 DB 事务外调用已固定后端的 beginUpload。返回存储 token 后，按统一锁序重新验证当前身份/会议/cancel_epoch/用途；把 name、purpose、固定位置、大小/hash、完整内部 token 与 OPEN 会话在一个事务提交，提交后才向客户端返回 uploadId。既有幂等作用域行及结果与会话同事务提交；相同请求重放返回同 uploadId，不再次受理会话；并发初始请求在最终幂等唯一键冲突后回读获胜结果，未被受理的存储 token 走技术暂存对账。存储 begin 成功但事务失败/进程崩溃产生未受理的技术暂存会话，不登记可用文件；按 B-04 对账和保护窗口处理。持久业务会话 ID 与存储 sessionId 不要求相同；provider_upload_token 是该内部不透明 sessionId 的保存槽，不能猜测两者相等。已受理会话必须非空；本轮保留既有可空 DDL，由领域不变量保证。

上传分片只信任实际存储成功、校验通过的服务端 PartReceipt。存储 I/O 不持长 DB 事务；回执落库时按统一锁序锁 session，重新验证 OPEN/身份/会议/epoch，INSERT `(upload_id,part_no)` 的 size/hash/provider_token。同号同内容重传返回原回执，不替换；同号异内容冲突。所有适配器必须保证同一逻辑分片的已接纳字节/回执不可被迟到写入覆盖。若 I/O 成功后 DB 提交失败可按相同内容重放补登记；若已退出 OPEN，不补登记或返回分片成功。不能仅锁数据库而让 provider 原地覆盖已冻结分片。

完成在短事务中先锁 session，再读取其全部已接纳分片，要求客户端 part/hash 清单恰好匹配全体服务端行，唯一、按 1 起连续递增且大小总和用防溢出运算等于 expected_size，拒绝缺漏、额外及另一会话回执。客户端不提交或决定 provider_token。完整 sha256 与非空预声明值一致；若 expected_sha256 初始为空，本事务填入完成请求的规范化完整 hash。只有上述检查通过才将 OPEN 条件更新为 FINALIZING，并实际提交后调用存储 complete。FINALIZING 永不回 OPEN，所有分片行、完整 hash、名称、用途、位置及 token 自此冻结；它们就是可恢复完整决定，不新增 JSON manifest、fingerprint 或 revision 列。每个改变分片集合的路径必须先锁同一 session，故冻结事务与落库竞争有唯一胜者。

重启或响应丢失后，FINALIZING 只从这些持久行重建相同 token/PartReceipt 清单/hash并安全重放 complete，或对固定目标进行实际 size/完整 SHA256 核验并由适配器重新确保持久化后对账；仅 stat/read 可见不等于 durable 证明，local 恢复须重新满足文件/目录同步保证。不能使用 ETag、客户端 receipt 或当前默认后端代替。未知存储结果保持 FINALIZING，不改 FAILED/OPEN 触发新内容；只有证明未发布且无仍可发布的在途操作才可终结 FAILED。ABORTED/FAILED 不复活；普通 abort 仅在 OPEN 下原子赢得 ABORTED 决定，再幂等中止存储，失败由恢复流程重试；FINALIZING 的 abort 返回冲突，COMPLETED 的 abort 为保留文件的幂等无操作。删除仍先设置会议栅栏，任何迟到完成在登记事务重新验证后失效，最多形成不可读孤儿。

文件完整 durable 且大小/完整 hash 验证一致后，登记事务按身份→subject→meeting→upload→file/refs 顺序复查身份/权限、会议生命周期、cancel_epoch、用途仍启用且有权、会话仍 FINALIZING 及固定决定。插入 AVAILABLE file，display_name 从 session 完整复制；同时写 completed_file_id、COMPLETED 及引用账本，随后才返回 fileId。具体上传结果引用由 B-04 注册器登记 `owner_kind=UPLOAD_SESSION, owner_id=upload_session.id, purpose=upload_session.purpose`，验证同会议及 completed_file_id 指向该文件；这是技术 owner 类型，不新增产品用途。保留此引用直到后续领域在同事务建立自己的引用并显式解除上传引用，或明确删除上传结果/会议，不能把已返回 fileId 的上传当无引用孤儿。用途仍以 session 与各具体业务引用为事实，不给 file_object 另添用途字段；一个文件可被多个合法引用使用，每次绑定由领域按原上传用途核验，不信任浏览器改用途。

同一 session 的双完成登记串行；后到者只在完整持久清单/hash相同且重新鉴权后返回已存 completed_file_id，不另建 file。blob 成功而登记事务失败时无 AVAILABLE/引用/COMPLETED 半提交，重试仍由同一 FINALIZING 决定恢复原名称/用途并完成一次登记。完成后的清单/hash/result 保留到明确的生命周期清理，以支持相同请求重放；不能清空 parts 再声称可比较旧完成请求。后续领域解除上传引用后 session.completed_file_id 仍是重放关联，文件清理须按已有业务列＋引用账本交叉核验并显式处理该关联；不存在以 TTL 自动删历史或绕过 RESTRICT 的路径。

本补充不新增 creator_id：现有会议 owner/MeetingAccess 决定每次操作权限，初始化操作者由既有审计/幂等作用域记录，不发明“仅最初上传人可续传”规则。不新增 part_size：HTTP 返回值是客户端分片建议，持久恢复依据实际已接纳 receipt/size，不按当前配置重切片或因配置改变拒绝旧合法逻辑分片。现有 FileStoragePort.writePart 接受通用逻辑分片；S3 适配器须以其受保护暂存/合并实现合法 transport parts，并持久化自身恢复清单，不能只对 S3 降低共同契约。具体 S3 暂存/合并、迟到 I/O 与 complete/abort 竞争仍为 B-04 独立实现/契约验证义务，不是本次数据库结构证据。如需变更共同协商约束或确有额外持久字段，先报告并单独评审，不从本补充推导已批准的新字段、固定传输大小或产品文件上限。

### 原有录音与删除边界（继续适用）

上传完成/重复完成核对相同size/hash/part清单和epoch；不同请求冲突；位置固定不受默认后端变化影响。删除任务先锁根校验从未成功发布、非录音/暂停、非发布进行/未知，设置DELETING及epoch/access_revision，阻断所有读取和新写；旧worker和已签发上传完成必须复查。清理按引用账本与业务列交叉校验，锁file无引用且位置/epoch匹配时标DELETE_PENDING；提交后删除物理对象/目录持久化，再标DELETED。禁止新引用待删文件。失败保留可重试状态；全部正文、版本、缓存和导出/文件清理完成才 deletion.COMPLETE/根DELETED。知识库引用只解除本会议关联，禁止删除公司知识库文件。

录音创建固定backend、writer_epoch；续租/重连必须同身份与当前epoch。每part的(seq,hash)唯一语义：重传同hash返回相同ACK，异hash冲突；旧writerEpoch拒绝。分片文件先durable、再chunk与连续前缀同事务，ACK只来自已提交事实。finish记录每段final_seq并检查缺口/采样连续；归档校验完整后才AVAILABLE，未归档分片仍有file_reference。归档后技术分片只有恢复窗口/无引用证据齐全才清理。多源映射保留源独立时间与全局累计有效采样。字幕独立修订，不能用定时器造STABLE，更不能当确认稿。

## 查询与容量验证计划

**R1 列表契约。** 会议编号采用持久且不可变的 `meeting.id` 完整36字符UUID，同时用于展示/复制/包含搜索；不取页内序号、不另造未存储短编号，也不把知道编号当授权。`meeting_search_projection`保存当前已保存名称、number_search、category及当前info/confirmed-roster指针；`meeting_person_search`只从 `meeting.roster_revision_id` 所指CONFIRMED名单的完整display_name生成，草稿、AI/声纹候选、行动项负责人不加入。外部业务人员只有在该名单明确确认后才可被已授权用户按名搜索，这不赋予其访问权。B-03同事务随信息保存/名单确认更新投影，根任何revision变化都同步投影版本；旧名随旧名单只保留在历史，不混入当前列表搜索。投影不替代授权事实。

公共基础关系F：先限制当前身份ACTIVE/有权scope（owned=owner；participating=有效confirmed READ grant），lifecycle=ACTIVE、governance=ACTIVE，再组合**同一组**q、stage、categoryId、topicId/topicMode、from/to。q仅为“当前会议名称 OR 完整会议编号 OR 当前已确认参会人员姓名”的包含匹配；搜索列统一utf8mb4_0900_as_ci，英文不区分大小写，不回写binary身份ID。绑定参数先把`=`转`==`、`%`转`=%`、`_`转`=_`，再加两端`%`并用`ESCAPE '='`。不把摘要/正文搜索当作这个列表q；其他正文检索能力另在有权版本中处理。

categoryId非空时精确匹配企业分类稳定ID；未填写分类仅在不限定分类时出现，显示“其他”不是一个伪造分类键。from/to分别可空，按上海实际DATE包含两端；任一日期条件存在即排除NULL日期，不用created_at。stage映射到已定服务端阶段条件。topicMode=ALL/ID/UNASSIGNED：ID要求topic.owner_id=当前用户并按topic.id精确EXISTS；UNASSIGNED要求不存在关联当前用户个人专题的meeting_topic；ALL不加该谓词。用户不能用他人的专题ID探测会议；我参与的视图不显示个人专题字段，若传他人专题ID仍拒绝。专题组织不能扩大F的ACL范围。

实现使用参数化、可组合谓词构建器产生**唯一的F定义**（同一请求只读一致性视图）；list=F按updated_at DESC,id DESC稳定分页，total=COUNT(F)，stageCounts=F按stage聚合，topicGroups=F按当前用户专题关联聚合并加UNASSIGNED组。多名命中用EXISTS而非JOIN展开，所以每会议在F仅一行；专题组内COUNT DISTINCT meeting_id并与组内条目使用同一F。不得在total/counts/group中忽略q、分类、所选stage、专题或日期，也不默认在counts偷偷移除当前stage条件。切换列表/分组沿用同一筛选，归集只改组织信息。数据库先利用权限/分类/专题/日期/更新时间索引缩小候选，再执行真实包含匹配；普通B-tree不能加速任意前导通配。

查询骨架（省略同一谓词构建器内其余绑定项，不允许每个统计接口另写条件）：

```sql
SELECT m.id
FROM meeting m
JOIN meeting_search_projection p ON p.meeting_id=m.id
WHERE /* ACTIVE身份、owned/participating ACL、生命周期、治理、stage/date/category/topic共同谓词 */
      p.meeting_revision=m.revision
  AND (:q_absent OR p.name_search LIKE :pattern ESCAPE '='
       OR p.number_search LIKE :pattern ESCAPE '='
       OR EXISTS (SELECT 1 FROM meeting_person_search n
                  WHERE n.meeting_id=m.id AND n.roster_id=m.roster_revision_id
                    AND n.name_search LIKE :pattern ESCAPE '='))
ORDER BY m.updated_at DESC,m.id DESC;
```

|查询/边界|形态与索引顺序|容量假设及后续验证拥有者|
|---|---|---|
|负责/参与列表与共同筛选|meeting_owner_updated(owner,lifecycle,updated_at,id)、meeting_owner_date(owner,lifecycle,date,id)、meeting_stage_updated(lifecycle,stage,updated_at,id)、access_person_meeting(person,action,meeting)；search_category(category,meeting)；meeting_topic(topic,meeting)及其meeting FK索引支持EXISTS/NOT EXISTS|至少10万会议、1000人、多名单分布；B-03/D-02/D-08真实EXPLAIN ANALYZE与list/total/stageCounts/topicGroups共同F一致性|
|名称/编号/已确认人员姓名包含检索|projection PK meeting；person PK(meeting,member)，冻结roster/member FK；英文大小写不敏感as_ci、字面通配转义；任一字段命中|B-03/V008建投影，D-02维护/查询；B-tree不能优化前导通配，不拿FULLTEXT替代。含长中文/大小写/多人同名/旧名单/未确认候选/未知日期/分类/专题组合实测|
|任务领取|分开 task_ready(pool,state,available_at,id) 与 task_expired(pool,state,expires_at,id)；小批候选后按锁序重验|B-05 EXPLAIN、锁行跳过、5秒扫描、20秒租约，DB健康30秒重新可领目标；C-07加入实时/离线公平性|
|续租/提交|PK task.id，再精准attempt/owner/version/epoch；subject复合PK|B-05旧attempt、暂停handler、真实时间续租、DB恢复；不是本次结构验收|
|版本历史|各领域(meeting_id,revision)唯一B-tree，倒序keyset|至少100万transcript_segment与长文本；B-06/D-04验证恢复新版本及完整稿检索|
|来源追踪|segment(revision,id)，evidence_element(element,locator)，evidence_minutes(minutes,locator)|D-03/D-06页段/媒体定位与跨会议伪造拒绝；不以多表无界N+1加载|
|敏感/背景目录|detection_order(set,category,ordinal)、background_order(set,ordinal)，每页来源join以PK批量查；reverse(locator,id)支持删除依赖|B07/D03真实含双标签、共享locator、部分选中、长背景及混合origin input；页面批量而非无界N+1，无产品上限|
|复核/最终清单|review_element(element,kind,review_no)、review_minutes(minutes,kind,review_no)、review_sequence(meeting,no)；final PK(confirmation,review)|目标ID/完整anchor/hash在索引桶内逐项精确复核；禁止只选任意SUPPORTED，禁止最新复核替代历史membership；B08/D03实际EXPLAIN和并发CAS尚待实现|
|文件清理|file_meeting(meeting,state,id)，cleanup_due(state,not_before,id)，file_ref_owner(kind,id,file)；清理前锁file再NOT EXISTS全账本|B-04/D-07 orphan/并发引用/位置迁移/删除失败回归；不得用估算refcount替代证明|
|录音/事件|chunk(part,seq)，subtitle(session,event_seq)，outbox(subject_kind,subject_id,sequence_id)|100场每2秒约50分片/s、180000分片/小时；C-07两小时＋八小时稳定性，含全局序列争用|

列表/total/stageCounts/topicGroups必须共享上述全部F谓词及一致性视图；默认updated_at DESC,id DESC，日期仅筛选而不替换默认顺序。授权失败不要通过总数/人员命中泄露资料。吞吐目标100录音+200编辑/回看、20回听、10编辑、5离线任务；每节点API20/RT10/worker10初始连接配额需重算所有消费者。此处只是设计假设，不是EXPLAIN或性能报告。

## 分期迁移、兼容和回滚

V001/V002/V005冻结原字节；不得修改已应用/共享/暂停脚本或填V003/V004空位。登记册分配V006起增量，B-01不得代其他领域创建全套表。审阅DDL为了独立完整元数据核对在独有一次性设计库创建全部93表，绝不替代正式Flyway迁移。旧表 `meeting_acl` 仅过渡保留，目标有效权限表为 `meeting_access_grant`，不得无来源自动迁移旧“已确认”记录。1.1修正当时归V008/V010/V011/V012/V013/V017/V023预留设计；本版基线中V008/V010已采用，其正式SQL同V001—V010其他已存在脚本全部冻结，不能把历史“尚未实现”当成当前修改许可；首次正式迁移仍须执行登记册旧集合归集门。

正式扩展：新增列先nullable或显式安全默认，双读/双写窗口受版本标记控制；唯一/检查/FK前审计脏数据与二进制排序冲突，不能INSERT IGNORE吞损。把V001标识改为binary前验证既有长度/相等性；将V002身份投影/role/session_generation转移后，所有节点切到共享即时撤销与新ACL路径。V005缺字段回填完整spec、producer_key、max_attempts以及external marker，旧RUNNING必须排空/确认无外部未知，不能凭空升级成安全重试；维护期间停止领取再上线新worker。

应用回滚只允许回到兼容新增schema的版本。已产生新权限/任务/外部操作数据后，不可回滚到旧ACL或旧worker语义；停止写入并前滚修复，或使用受控恢复点且先对账所有外部效果。收缩旧列/表需要全部节点完成切换、历史/引用保留与备份恢复验证、独立新迁移号；无通用DROP回滚。本设计不提供未经批准的删除迁移。迁移由单一runner执行，Flyway历史校验防止并发自动迁移。

B00-1.3只改变仍未编写/共享的V012排除主键与新七表，V013新增最终复核成员表；部署前必须复核真实迁移状态。如果接收方V012/V013已共享或已应用，停止并报告，由协调者分配前向增量；本任务不消费V028，不覆盖历史。V011八表完整DDL/FK/index/列/CHECK保持1.2相同，无新增incoming FK。

## 未定范围、责任方和解锁条件

|待落实输入|已完整确定的本地结构/行为|责任方及门槛|
|---|---|---|
|生成首版有效保存/证据复核交互|候选和有效版本分离；内容级adopt+review可同事务，新身份/时间/目标hash，不复制旧信用|用户两项回答仍pending；不得默认自动首存或自动证实|
|anchor/hash/profile与语义coverage|result_review保存完整精确目标与来源集摘要、profile版本与身份时间|B07单独契约评审；未定义覆盖规则不得把局部SUPPORTED当整条充分|
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

R1上下文根：所有纳入minutes_element.source_context_id无论证据数量均为basis根，与域内精确usage上下文取并集。ROWSET v2/PINNED用已保存输入要素集全部basis及set证据/目录，排除body/applied exclusion；REVIEW_SOURCES仅选定复核要素basis及精确相交证据（正文复核无要素basis）；FINAL用全部冻结纳入要素basis及既有body/set证据、目录、applied exclusion。各根完整context/selection/profile按实际完整PK去重及unsigned UTF8排序，缺失/额外行拒绝；无usage的basis同样验证并保留依赖。basis不伪造EVIDENCE、不增加usage数、不授予支持信用；草稿仍无自身fingerprint/TaskSpec，祖先按图依赖判断。

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
        longtext display_name
        varchar_64_ purpose
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
        varchar_64_ operation_kind
        varchar_255_ business_key
        char_64_ request_hash
        varchar_64_ state
        datetime_6_ marked_at
        datetime_6_ next_check_at
        bigint reconcile_epoch
        varchar_64_ reconcile_owner
        datetime_6_ reconcile_expires_at
        longtext external_result_id
        varchar_64_ last_error
        varchar_64_ subject_kind FK
        varchar_255_ subject_id FK
        bigint current_execution_no
        bigint reconcile_scan_seq
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
        char_36_ execution_id FK
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
        char_36_ processing_input_id FK
        char_36_ producer_task_id FK
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
        char_36_ template_id FK
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
        char_36_ source_context_id FK
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
        varchar_64_ category PK
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
        char_36_ source_context_id FK
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
        char_36_ execution_id FK
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
        longtext description
        longtext name_normalized
        char_64_ name_hash
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
    meeting_search_projection {
        char_36_ meeting_id PK,FK
        char_36_ info_revision_id FK
        char_36_ roster_revision_id FK
        longtext name_search
        varchar_36_ number_search
        varchar_255_ category_id
        bigint meeting_revision
    }
    meeting_person_search {
        char_36_ meeting_id PK,FK
        char_36_ roster_id FK
        char_36_ member_id PK,FK
        longtext name_search
    }
    minutes_template {
        char_36_ id PK
        varchar_255_ owner_id FK
        longtext name
        longtext name_normalized
        char_64_ name_hash
        char_36_ current_revision_id
        bigint revision
        datetime_6_ created_at
    }
    external_execution {
        char_36_ id PK
        char_36_ operation_id FK
        bigint execution_no
        char_36_ task_id FK
        bigint task_attempt
        varchar_64_ state
        char_64_ request_hash
        longtext request_manifest
        datetime_6_ created_at
        datetime_6_ marked_at
        datetime_6_ finished_at
        varchar_64_ no_effect_basis
        longtext no_effect_evidence
        datetime_6_ no_effect_verified_at
    }
    transcription_input {
        char_36_ id PK
        char_36_ meeting_id FK
        char_36_ batch_id FK
        varchar_64_ kind
        char_36_ source_revision_id FK
        char_36_ material_snapshot_id FK
        varchar_64_ material_mode
        varchar_255_ processing_config_version
        varchar_255_ parser_version
        varchar_255_ model_config_version
        varchar_255_ prompt_version
        int manifest_schema_version
        longtext config_manifest
        char_64_ input_fingerprint
        bigint base_revision
        bigint cancel_epoch
        datetime_6_ created_at
    }
    transcript_segment_mark {
        char_36_ revision_id PK,FK
        char_36_ segment_id PK,FK
        varchar_64_ mark PK
    }
    sensitive_detection {
        char_36_ id PK
        char_36_ set_id FK
        char_36_ input_id FK
        varchar_64_ category
        bigint ordinal
    }
    sensitive_detection_source {
        char_36_ detection_id PK,FK
        char_36_ locator_id PK,FK
    }
    background_note {
        char_36_ id PK
        char_36_ set_id FK
        char_36_ input_id FK
        bigint ordinal
        longtext body
    }
    background_note_source {
        char_36_ note_id PK,FK
        char_36_ locator_id PK,FK
    }
    action_suggestion {
        char_36_ element_id PK,FK
        char_36_ input_id FK
        longtext owner_mention
        date due_on
    }
    result_review {
        char_36_ id PK
        char_36_ meeting_id FK
        bigint review_no
        varchar_64_ review_kind
        char_36_ element_id FK
        char_36_ minutes_revision_id FK
        longtext target_anchor
        char_64_ target_hash
        char_64_ source_set_hash
        varchar_255_ hash_profile
        varchar_64_ outcome
        varchar_255_ reviewed_by
        datetime_6_ reviewed_at
    }
    result_review_evidence {
        char_36_ review_id PK,FK
        char_36_ evidence_id PK,FK
    }
    final_result_review {
        char_36_ confirmation_id PK,FK
        char_36_ review_id PK,FK
    }
    source_context {
        char_36_ id PK
        char_36_ meeting_id FK
        varchar_64_ origin_kind
        char_36_ generation_input_id FK
        char_36_ transcript_confirmation_id FK
        char_36_ material_snapshot_id FK
        varchar_255_ catalog_version
        char_64_ catalog_sha256
        datetime_6_ created_at
    }
    source_context_material {
        char_36_ context_id PK,FK
        char_36_ material_revision_id PK,FK
        char_36_ parsed_document_id FK
    }
    parsed_document_profile {
        char_36_ document_id PK,FK
        varchar_64_ format
        varchar_255_ parser_config_version
        char_64_ parser_config_sha256
    }
    input_batch_fingerprint_profile {
        char_36_ batch_id PK,FK
        varchar_64_ profile
    }
    meeting_minutes_editor_guard {
        char_36_ meeting_id PK,FK
        bigint intent_epoch
    }
    publication_submission {
        char_36_ task_id PK,FK
        char_36_ publication_id FK
        bigint submission_no
        char_36_ confirmation_id FK
        varchar_64_ provider_id
        varchar_255_ business_key
        varchar_255_ library_id
        varchar_255_ folder_id
        varchar_255_ requester_id FK
        char_64_ logical_request_hash
        char_64_ execution_request_hash
        longtext request_manifest
        datetime_6_ created_at
        varchar_32_ local_state
        datetime_6_ local_decided_at
        varchar_64_ local_reason
        varchar_255_ local_decided_by FK
        bigint local_attempt_no
        char_36_ local_execution_id FK
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
    task_subject ||--o{ external_operation : fk_external_operation_0
    durable_task o|--o{ task_outbox : fk_task_outbox_0
    model_capacity ||--o{ model_reservation : fk_model_reservation_0
    external_execution ||--o| model_reservation : fk_model_reservation_1
    meeting ||--o{ input_batch : fk_input_batch_0
    input_batch ||--o{ input_batch_file : fk_input_batch_file_0
    file_object ||--o{ input_batch_file : fk_input_batch_file_1
    meeting ||--o{ transcript_revision : fk_transcript_revision_0
    input_batch ||--o{ transcript_revision : fk_transcript_revision_1
    transcript_revision o|--o{ transcript_revision : fk_transcript_revision_2
    transcription_input ||--o{ transcript_revision : fk_transcript_revision_3
    durable_task o|--o{ transcript_revision : fk_transcript_revision_4
    transcript_revision ||--o{ transcript_segment : fk_transcript_segment_0
    file_object ||--o{ transcript_segment : fk_transcript_segment_1
    speaker o|--o{ transcript_segment : fk_transcript_segment_2
    meeting ||--o{ transcript_confirmation : fk_transcript_confirmation_0
    transcript_revision ||--o{ transcript_confirmation : fk_transcript_confirmation_1
    enterprise_identity ||--o{ transcript_confirmation : fk_transcript_confirmation_2
    enterprise_identity o|--o{ template_revision : fk_template_revision_0
    minutes_template ||--o{ template_revision : fk_template_revision_1
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
    source_context o|--o{ minutes_element : fk_minutes_element_1
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
    source_context o|--o{ evidence_link : fk_evidence_link_3
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
    external_execution ||--o{ publication_attempt : fk_publication_attempt_3
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
    meeting ||--o| meeting_search_projection : fk_meeting_search_projection_0
    meeting_info_revision o|--o{ meeting_search_projection : fk_meeting_search_projection_1
    roster_revision o|--o{ meeting_search_projection : fk_meeting_search_projection_2
    meeting ||--o{ meeting_person_search : fk_meeting_person_search_0
    roster_member ||--o{ meeting_person_search : fk_meeting_person_search_1
    enterprise_identity o|--o{ minutes_template : fk_minutes_template_0
    external_operation ||--o{ external_execution : fk_external_execution_0
    durable_task ||--o| external_execution : fk_external_execution_1
    meeting ||--o{ transcription_input : fk_transcription_input_0
    input_batch ||--o{ transcription_input : fk_transcription_input_1
    transcript_revision o|--o{ transcription_input : fk_transcription_input_2
    material_snapshot o|--o{ transcription_input : fk_transcription_input_3
    transcript_segment ||--o{ transcript_segment_mark : fk_transcript_segment_mark_0
    element_set ||--o{ sensitive_detection : fk_sensitive_detection_0
    generation_input ||--o{ sensitive_detection : fk_sensitive_detection_1
    sensitive_detection ||--o{ sensitive_detection_source : fk_sensitive_detection_source_0
    source_locator ||--o{ sensitive_detection_source : fk_sensitive_detection_source_1
    element_set ||--o{ background_note : fk_background_note_0
    generation_input ||--o{ background_note : fk_background_note_1
    background_note ||--o{ background_note_source : fk_background_note_source_0
    source_locator ||--o{ background_note_source : fk_background_note_source_1
    minutes_element ||--o| action_suggestion : fk_action_suggestion_0
    generation_input ||--o{ action_suggestion : fk_action_suggestion_1
    meeting ||--o{ result_review : fk_result_review_0
    minutes_element o|--o{ result_review : fk_result_review_1
    minutes_revision o|--o{ result_review : fk_result_review_2
    result_review ||--o{ result_review_evidence : fk_result_review_evidence_0
    evidence_link ||--o{ result_review_evidence : fk_result_review_evidence_1
    final_confirmation ||--o{ final_result_review : fk_final_result_review_0
    result_review ||--o{ final_result_review : fk_final_result_review_1
    meeting ||--o{ source_context : fk_source_context_0
    generation_input o|--o| source_context : fk_source_context_1
    transcript_confirmation ||--o{ source_context : fk_source_context_2
    material_snapshot o|--o{ source_context : fk_source_context_3
    source_context ||--o{ source_context_material : fk_source_context_material_0
    material_revision ||--o{ source_context_material : fk_source_context_material_1
    parsed_document ||--o{ source_context_material : fk_source_context_material_2
    parsed_document ||--o| parsed_document_profile : fk_parsed_document_profile_0
    input_batch ||--o| input_batch_fingerprint_profile : fk_input_batch_fingerprint_profile_0
    meeting ||--o| meeting_minutes_editor_guard : fk_meeting_minutes_editor_guard_0
    durable_task ||--o| publication_submission : fk_publication_submission_task
    publication ||--o{ publication_submission : fk_publication_submission_publication
    final_confirmation ||--o{ publication_submission : fk_publication_submission_confirmation
    enterprise_identity ||--o{ publication_submission : fk_publication_submission_requester
    enterprise_identity o|--o{ publication_submission : fk_publication_submission_decider
    external_execution o|--o{ publication_submission : fk_publication_submission_execution
```
