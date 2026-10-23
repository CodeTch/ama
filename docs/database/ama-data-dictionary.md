# AMA 数据字典 — B00-1.5-runtime-metadata

生成源：`tools/checks/database_design.py`。与审阅 DDL 同步，实际 MySQL 元数据另行比对；生成本身不构成验证。

全表 InnoDB / utf8mb4_0900_ai_ci；稳定标识使用 utf8mb4_0900_bin（NO PAD），UUID/协议码/哈希用 ascii_bin。每字段的类型中明确覆盖排序规则。DATETIME(6) 一律 UTC；DATE 为 Asia/Shanghai 业务日。默认值 `无` 意味必须由应用明确赋值（可空字段默认 NULL）。主键唯一性的业务依据为稳定实体标识或明确复合业务键；UUID 不承载顺序。LONGTEXT 的物理容量不是产品字数限制。

全部 FK 均 ON UPDATE RESTRICT / ON DELETE RESTRICT，根与历史不级联；拥有任务负责在同事务验证同会议/同所有者、状态及快照成员关系。字典中无 FK 的 typed ID 和循环当前指针见设计文档“逻辑关系与删除”，不可按裸 ID 绕过鉴权。

R1上下文根：所有纳入minutes_element.source_context_id无论证据数量均为basis根，与域内精确usage上下文取并集。ROWSET v2/PINNED用已保存输入要素集全部basis及set证据/目录，排除body/applied exclusion；REVIEW_SOURCES仅选定复核要素basis及精确相交证据（正文复核无要素basis）；FINAL用全部冻结纳入要素basis及既有body/set证据、目录、applied exclusion。各根完整context/selection/profile按实际完整PK去重及unsigned UTF8排序，缺失/额外行拒绝；无usage的basis同样验证并保留依赖。basis不伪造EVIDENCE、不增加usage数、不授予支持信用；草稿仍无自身fingerprint/TaskSpec，祖先按图依赖判断。

## 表清单与分期

|表|任务|计划迁移|用途|
|---|---|---|---|
|audit_event|B-01|V006|最小事务审计（V001 的目标增量形态）|
|department|B-02|V007|目录部门投影|
|enterprise_identity|B-02|V007|企业身份（架构 user_identity）|
|role_assignment|B-02|V007|独立平台角色；不隐含正文访问|
|identity_session|B-02|V007|共享数据库会话与即时撤销|
|meeting|B-03|V008|会议聚合根，唯一负责人和生命周期|
|meeting_info_revision|B-03|V008|不可变基础信息历史|
|roster_revision|B-03|V008|名单草稿与已确认快照头；确认冻结该修订|
|roster_member|B-03|V008|名单内容；本表绝不被授权查询使用|
|meeting_access_grant|B-02/B-03|V008|独立有效 ACL；替代 V002 meeting_acl 读路径|
|idempotency_record|B-03|V008|副作用请求重放/同键异请求冲突|
|info_suggestion|D-02|V018|AI 信息建议的独立候选|
|storage_backend|B-04|V009|不可变后端路由注册；凭证在外部配置|
|file_object|B-04|V009|已持久化受管文件元数据|
|file_reference|B-04|V009|所有受管文件使用的统一引用账本|
|upload_session|B-04|V009|固定后端的断点续传会话|
|upload_part|B-04|V009|已落盘上传分片回执|
|file_cleanup|B-04/D-07|V009|可重试删除/孤儿清理登记|
|task_subject|B-05|V010|每领域聚合的版本/取消 fence（会议仅一个共享根）|
|durable_task|B-05|V010|持久任务；外部未知结果从普通领取隔离|
|task_attempt|B-05|V010|每次 Worker 领取诊断历史|
|external_operation|B-05|V010|任何可能产生外部效果的调用前 durable marker 及独立对账所有权|
|task_outbox_sequence|B-05|V010|提交序列锁，避免 AUTO_INCREMENT 先提交高号跳漏|
|task_outbox|B-05|V010|持久引用事件（架构 outbox_event）|
|outbox_consumer|B-05|V010|持久消费游标及接管 fence|
|model_capacity|B-05|V010|已审核模型在途容量；无默认公司额度|
|model_reservation|B-05|V010|独立于短任务租约的模型额度保留|
|input_batch|B-06/C-01|V011|处理主文件集合快照，禁止混合音视频与纯转写|
|input_batch_file|B-06/C-01|V011|不可变有序源文件清单|
|transcript_revision|B-06|V011|原始/增强/人工/候选转写不可变版本；确认另表|
|transcript_segment|B-06|V011|稳定片段ID跨修订保留；纯文档无虚构音频时间|
|transcript_confirmation|B-06|V011|确认转写快照；只指不可变保存稿|
|template_revision|B-07/D-04|V012|内置/个人模板不可变版本|
|generation_input|B-07|V012|纪要输入冻结集合；禁止未确认转写入队|
|minutes_revision|B-07|V012|已保存正文；候选与当前指针分离|
|element_set|B-07/D-03|V012|独立要素集合修订，不反向改写正文|
|minutes_element|B-07/D-03|V012|七类主体要素；敏感与背景不是额外主体类|
|action_item|B-07/D-03|V012|行动项结构信息；负责人不授权|
|exclusion_revision|B-07|V012|敏感排除集合版本；空集也有ID|
|sensitive_exclusion|B-07/D-03|V012|被明确排除的来源；不删除转写/要素/录音|
|generation_candidate|B-07|V012|生成结果候选；迟到结果不直接覆盖|
|source_locator|B-07/D-01|V012|有类型的不可变来源定位，不能以通用JSON抹去来源约束|
|evidence_link|B-07/D-03|V012|具体要素或正文版本的引用证据|
|final_confirmation|B-08|V013|冻结最终已保存版本集合及校验摘要|
|publication|B-08|V013|冻结发布操作；成功一次即永久只读|
|publication_attempt|B-08|V013|每次发布/查回的诊断与固定确认关联|
|external_document_mapping|B-08|V013|唯一成功外部文档映射|
|publication_governance|D-05|V020|不可逆撤回/废止请求和确认记录|
|external_event_receipt|D-05|V020|验签后外部事件去重/重放防护|
|media_source|C-01|V014|归一化音频采样数与多源映射|
|media_source_span|C-01|V014|跨段独立源时间轴到合并轴映射|
|speaker|B-06/C-03|V011|说话人业务标签；实名与授权隔离|
|correction|C-03|V015|与具体转写版本绑定的纠错建议|
|edit_operation|C-03|V015|可撤销编辑操作；撤销也创建新修订|
|recording_session|C-04|V016|唯一采集者与writerEpoch；固定后端|
|recording_part|C-04|V016|分段独立序号与durable连续前缀|
|recording_chunk|C-04|V016|落盘ACK事实来源，重复序号异哈希冲突|
|subtitle_revision|C-06|V016|临时/稳定字幕历史；不冒充正式转写|
|material_reference|D-01|V017|会议本地/知识库材料逻辑关联|
|material_revision|D-01|V017|固定源版本；外部引用永不跟随最新|
|material_snapshot|D-01|V017|已明确应用材料集合；待修订不能改有效集合|
|material_snapshot_item|D-01|V017|冻结材料修订清单|
|parsed_document|D-01|V017|解析版本及能力状态；不把空解析当成功|
|parsed_unit|D-01|V017|文本与页/段映射保留|
|export_job|D-06|V021|按已保存有效版本生成三格式导出|
|meeting_deletion|D-07|V022|删除请求与清理完成事实|
|topic|D-08|V023|单层个人专题，不改变内容版本/ACL|
|meeting_topic|D-08|V023|本人负责且已发布会议归集|
|corpus_feedback|E-01|V024|受控反馈候选/审核；不改原确认稿|
|feedback_segment|E-01|V024|反馈关联稳定源片段|
|corpus_revision|E-01|V024|批准后可用语料修订；全局使用另有安全门|
|voiceprint_consent|E-02|V025|明确用途授权及撤销记录|
|voiceprint_sample|E-02|V025|声纹样本与审核状态；不写ACL|
|evaluation_dataset|E-03|V026|受控评测数据版本|
|configuration_revision|E-03|V026|白名单配置候选；B期配置版本也可来自受控文件|
|evaluation_run|E-03|V026|固定数据/模型/提示词评测任务|
|configuration_release|E-03|V026|配置发布/回滚均新增记录|
|mail_job|E-04|V027|已保存版本受控链接邮件；不含正文附件/外部邮箱|
|mail_recipient|E-04|V027|固定企业收件人集合；发送前再验证有效名单和ACL|
|meeting_search_projection|B-03/D-02|V008|R1 列表当前名称/编号及筛选字段投影；与根修改同事务|
|meeting_person_search|B-03/D-02|V008|R1 当前已确认名单姓名投影；草稿和AI候选绝不进入|
|minutes_template|B-07/D-04|V012|R2 模板逻辑根与当前个人名称；历史名称不占用命名空间|
|external_execution|B-05/B-08|V010|R4 稳定逻辑操作下的追加执行尝试；支持同确认零效果失败重试|
|transcription_input|B-06/C-02/C-03/D-01|V011|R5 转写导入/识别/重新增强不可变输入快照，独立于纪要generation_input|
|transcript_segment_mark|B-06/C-03|V011|R6 片段多选标记随转写版本冻结；不是纪要要素|
|sensitive_detection|B-07/D-03|V012|未选择也保留的敏感检测目录；不属于七类主体要素|
|sensitive_detection_source|B-07/D-03|V012|敏感检测的完整无序来源集合|
|background_note|B-07/D-03|V012|生成背景补充正文；不冒充会议发言/决定/行动|
|background_note_source|B-07/D-03|V012|背景补充的完整来源集合|
|action_suggestion|B-07/D-03|V012|未核实的模型负责人原文和日期建议；与接受字段分离|
|result_review|B-07/D-03/B-08|V012|人类对精确目标内容和完整证据集合的不可变断言|
|result_review_evidence|B-07/D-03/B-08|V012|复核实际使用的完整证据集合，不只存支持项|
|final_result_review|B-08|V013|最终确认冻结精确复核成员，不读取以后最新复核|
|source_context|D-01/B-07|UNASSIGNED-D01|不可变完整来源选择；生成与人工选择显式分型|
|source_context_material|D-01/B-07|UNASSIGNED-D01|上下文内每个材料版本唯一明确选择解析文档|
|parsed_document_profile|D-01|UNASSIGNED-D01|解析文档的明确格式及不可变解析配置归档引用|
|input_batch_fingerprint_profile|B-06/runtime Task2|V013.1|明确不可变输入批次指纹算法；缺失为legacy/unknown|
|meeting_minutes_editor_guard|B-03/B-07/runtime Task2|V013.1|持久单调编辑意图；仅新建会议原事务可写零|

## audit_event

最小事务审计（V001 的目标增量形态）。所属 B-01，计划 V006。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`bigint unsigned`|否|无|审计自增编号；不能作提交顺序游标|
|actor_id|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|操作者稳定 ID 或受控系统主体|
|action|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|动作码|
|resource_id|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|资源稳定标识|
|revision|`bigint unsigned`|否|无|业务修订|
|result|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|结果码|
|trace_id|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|安全关联标识|
|occurred_at|`datetime(6)`|否|无|UTC 微秒事件时间|

主键：`id`。
- 索引 `audit_resource_time(resource_id,occurred_at,id)`：资源审计时间线。
- 索引 `audit_trace(trace_id)`：安全追踪。

更新/删除与完整性：append-only；无正文、令牌或密钥；逻辑主体关联刻意无 FK 以允许正文删除；保留策略由 INT-09/PR-13 决定，不自动清除。

## department

目录部门投影。所属 B-02，计划 V007。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|enterprise_id|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|公司部门稳定 ID|
|display_name|`longtext`|否|无|展示名|
|parent_id|`varchar(255) COLLATE utf8mb4_0900_bin`|是|无|父部门 ID；根为空|
|verified_at|`datetime(6)`|否|无|最后权威核验|

主键：`enterprise_id`。
- FK `fk_department_0`：`parent_id` → `department(enterprise_id)`。目录适配器校验无环；删除 RESTRICT，停用代替物理删除。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## enterprise_identity

企业身份（架构 user_identity）。所属 B-02，计划 V007。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|enterprise_id|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|公司稳定 ID，大小写和尾空格精确比较|
|display_name|`longtext`|否|无|展示名|
|department_id|`varchar(255) COLLATE utf8mb4_0900_bin`|是|无|目录部门|
|status|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|账户状态|
|session_generation|`bigint unsigned`|否|0|撤销代际；停用/全退出时递增|
|verified_at|`datetime(6)`|否|无|权威状态核验|

主键：`enterprise_id`。
- CHECK `ck_enterprise_identity_0`：`status IN ('ACTIVE','DISABLED')`。
- FK `fk_enterprise_identity_0`：`department_id` → `department(enterprise_id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：目录适配器维护；停用原子递增 session_generation；重新启用不复活旧会话；不从姓名推导 ID。

## role_assignment

独立平台角色；不隐含正文访问。所属 B-02，计划 V007。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|enterprise_id|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|角色主体|
|role|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|平台角色|
|assigned_at|`datetime(6)`|否|无|分配时间|

主键：`enterprise_id,role`。
- CHECK `ck_role_assignment_0`：`role IN ('USER','PLATFORM_ADMIN')`。
- FK `fk_role_assignment_0`：`enterprise_id` → `enterprise_identity(enterprise_id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## identity_session

共享数据库会话与即时撤销。所属 B-02，计划 V007。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|token_hash|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|256-bit opaque token 的 SHA-256；不存 token|
|enterprise_id|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|会话主体|
|session_generation|`bigint unsigned`|否|无|签发时主体代际|
|csrf_token|`char(43) CHARACTER SET ascii COLLATE ascii_bin`|否|无|独立随机会话 CSRF 值；不进入日志|
|issued_at|`datetime(6)`|否|无|签发时间|
|expires_at|`datetime(6)`|否|无|显式平台策略有效期|
|revoked_at|`datetime(6)`|是|无|撤销时间|

主键：`token_hash`。
- 索引 `session_identity(enterprise_id,expires_at)`：全会话撤销与过期诊断。
- CHECK `ck_identity_session_0`：`expires_at > issued_at`。
- FK `fk_identity_session_0`：`enterprise_id` → `enterprise_identity(enterprise_id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：每请求共享 DB 查 ACTIVE、代际相同、未撤销、未过期；不缓存节点授权；无公司协议时禁止生产签发；过期会话清理另配策略。

## meeting

会议聚合根，唯一负责人和生命周期。所属 B-03，计划 V008。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|owner_id|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|唯一企业负责人|
|revision|`bigint unsigned`|否|0|全聚合 CAS 修订|
|cancel_epoch|`bigint unsigned`|否|0|删除/取消栅栏|
|access_revision|`bigint unsigned`|否|0|流读取撤权栅栏|
|stage|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|列表阶段|
|lifecycle|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|'ACTIVE'|独立删除生命周期|
|governance_state|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|'ACTIVE'|独立治理状态|
|ever_published|`tinyint unsigned`|否|0|成功发布后不可清零|
|info_revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|当前信息版本|
|roster_revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|当前确认名单|
|transcript_confirmation_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|当前确认转写|
|minutes_revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|当前有效已保存正文|
|final_confirmation_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|当前有效最终确认|
|actual_meeting_date|`date`|是|无|上海实际会议日；未知 NULL|
|created_at|`datetime(6)`|否|无|采集创建 UTC|
|updated_at|`datetime(6)`|否|无|最后修改 UTC|
|transcript_revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|当前已保存人工转写工作稿|
|material_snapshot_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|当前明确应用材料快照；B空集合NULL|
|template_revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|当前选择模板；不等于已应用正文|
|element_set_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|当前有效要素集|
|exclusion_revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|当前排除设置版本|
|generation_fingerprint|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|是|无|当前有效生成输入指纹|
|needs_generation|`tinyint unsigned`|否|0|正文是否需更新；独立于待采用候选|
|materials_pending|`tinyint unsigned`|否|0|材料修订有尚未明确应用变更|

主键：`id`。
- 索引 `meeting_owner_date(owner_id,lifecycle,actual_meeting_date,id)`：负责会议日期分页。
- 索引 `meeting_stage_date(lifecycle,stage,actual_meeting_date,id)`：授权后阶段统计/日期分页。
- 索引 `meeting_owner_updated(owner_id,lifecycle,updated_at,id)`：负责列表默认更新时间倒序。
- 索引 `meeting_stage_updated(lifecycle,stage,updated_at,id)`：阶段过滤后的默认稳定倒序。
- CHECK `ck_meeting_0`：`stage IN ('ENTRY','TRANSCRIPT_PENDING','TRANSCRIPT_FAILED','MINUTES_PENDING','MINUTES_FAILED','READY_TO_PUBLISH','PUBLISH_FAILED','PUBLISHED')`。
- CHECK `ck_meeting_1`：`lifecycle IN ('ACTIVE','DELETING','DELETED')`。
- CHECK `ck_meeting_2`：`governance_state IN ('ACTIVE','WITHDRAW_PENDING','WITHDRAWN','OBSOLETE_PENDING','OBSOLETE')`。
- CHECK `ck_meeting_3`：`ever_published IN (0,1)`。
- CHECK `ck_meeting_4`：`needs_generation IN (0,1)`。
- CHECK `ck_meeting_5`：`materials_pending IN (0,1)`。
- FK `fk_meeting_0`：`owner_id` → `enterprise_identity(enterprise_id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：root 行受 subject fence 保护；当前指针由本领域在锁内校验目标同会议，因循环依赖使用应用完整性；物理根保留无正文 tombstone 防迟到复活。

## meeting_info_revision

不可变基础信息历史。所属 B-03，计划 V008。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|会议|
|revision|`bigint unsigned`|否|无|会议修订|
|name|`longtext`|是|无|名称；不加产品字数限制|
|category_id|`varchar(255) COLLATE utf8mb4_0900_bin`|是|无|公司分类稳定键；真实字典未到不伪造|
|summary|`longtext`|是|无|摘要|
|actual_meeting_date|`date`|是|无|可选真实上海会议日|
|created_by|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|修改人|
|created_at|`datetime(6)`|否|无|保存时间|

主键：`id`。
- 唯一约束 `info_revision(meeting_id,revision)`：版本历史与唯一修订。
- FK `fk_meeting_info_revision_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_meeting_info_revision_1`：`created_by` → `enterprise_identity(enterprise_id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## roster_revision

名单草稿与已确认快照头；确认冻结该修订。所属 B-03，计划 V008。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|会议|
|revision|`bigint unsigned`|否|无|名单修订|
|state|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|DRAFT 可编辑；CONFIRMED 永久冻结|
|confirmed_at|`datetime(6)`|是|无|确认时间|
|confirmed_by|`varchar(255) COLLATE utf8mb4_0900_bin`|是|无|确认者|

主键：`id`。
- 唯一约束 `roster_revision(meeting_id,revision)`：名单历史。
- CHECK `ck_roster_revision_0`：`state IN ('DRAFT','CONFIRMED')`。
- CHECK `ck_roster_revision_1`：`(state='DRAFT' AND confirmed_at IS NULL AND confirmed_by IS NULL) OR (state='CONFIRMED' AND confirmed_at IS NOT NULL AND confirmed_by IS NOT NULL)`。
- FK `fk_roster_revision_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_roster_revision_1`：`confirmed_by` → `enterprise_identity(enterprise_id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## roster_member

名单内容；本表绝不被授权查询使用。所属 B-03，计划 V008。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|roster_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|名单版本|
|member_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|名单项 ID|
|enterprise_id|`varchar(255) COLLATE utf8mb4_0900_bin`|是|无|已选企业身份；外部人物为空|
|display_name|`longtext`|否|无|该版本显示名称|
|member_kind|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|ENTERPRISE 或 EXTERNAL|

主键：`roster_id,member_id`。
- 唯一约束 `roster_person(roster_id,enterprise_id)`：企业成员不重复；外部 NULL 可多条。
- CHECK `ck_roster_member_0`：`member_kind IN ('ENTERPRISE','EXTERNAL')`。
- CHECK `ck_roster_member_1`：`(member_kind='ENTERPRISE' AND enterprise_id IS NOT NULL) OR (member_kind='EXTERNAL' AND enterprise_id IS NULL)`。
- FK `fk_roster_member_0`：`roster_id` → `roster_revision(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_roster_member_1`：`enterprise_id` → `enterprise_identity(enterprise_id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## meeting_access_grant

独立有效 ACL；替代 V002 meeting_acl 读路径。所属 B-02/B-03，计划 V008。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|会议|
|enterprise_id|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|已确认企业成员|
|action|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|仅显式 READ；编辑/管理由负责人身份判定|
|roster_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|授权来自确认名单|
|confirmed_at|`datetime(6)`|否|无|非空有效确认时间|

主键：`meeting_id,enterprise_id,action`。
- 索引 `access_person_meeting(enterprise_id,action,meeting_id)`：先权限过滤再列表。
- CHECK `ck_meeting_access_grant_0`：`action='READ'`。
- FK `fk_meeting_access_grant_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_meeting_access_grant_1`：`enterprise_id` → `enterprise_identity(enterprise_id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_meeting_access_grant_2`：`roster_id` → `roster_revision(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：确认名单事务差量重建；仅 CONFIRMED 同会议名单中 ACTIVE 企业成员可插入（服务检查）；撤权 DELETE 此有效 grant 同事务递增 access_revision 与审计；历史由 roster/audit 保留；旧 meeting_acl 禁止继续授权。

## idempotency_record

副作用请求重放/同键异请求冲突。所属 B-03，计划 V008。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|scope_hash|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|SHA256(actor,resource,operation,key) 长度前缀编码作用域|
|actor_id|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|调用者|
|resource_id|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|资源或创建命名空间|
|operation|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|操作类型|
|key_hash|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|请求幂等键摘要|
|request_hash|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|规范化完整请求摘要|
|state|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|事务完成结果|
|result_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|安全结果对象 ID|
|http_status|`smallint unsigned`|否|无|响应状态|
|created_at|`datetime(6)`|否|无|受理时间|

主键：`scope_hash`。
- CHECK `ck_idempotency_record_0`：`state IN ('SUCCEEDED','REJECTED')`。

更新/删除与完整性：先插入唯一作用域行再锁资源；并发唯一冲突等待后比较 request_hash；同键异请求409；事务失败不留成功；外部异步返回任务ID；重放仍重验权限，不保存正文响应；无任意TTL清除。

## info_suggestion

AI 信息建议的独立候选。所属 D-02，计划 V018。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|会议|
|base_revision|`bigint unsigned`|否|无|提案基线|
|input_fingerprint|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|规范化输入版本集合的 SHA-256|
|proposed_info|`longtext`|否|无|版本化 JSON 文本，由服务 schema 校验|
|state|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|候选状态|

主键：`id`。
- CHECK `ck_info_suggestion_0`：`state IN ('PENDING','ADOPTED','REJECTED','STALE')`。
- FK `fk_info_suggestion_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## storage_backend

不可变后端路由注册；凭证在外部配置。所属 B-04，计划 V009。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|稳定配置后端 ID|
|storage_type|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|local/s3|
|status|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|可写/历史只读/停用|
|config_revision|`bigint unsigned`|否|无|配置引用版本|

主键：`id`。
- CHECK `ck_storage_backend_0`：`storage_type IN ('local','s3')`。
- CHECK `ck_storage_backend_1`：`status IN ('WRITABLE','READ_ONLY','DISABLED')`。

更新/删除与完整性：ID 不复用且类型不可改变；禁用前核验历史/在途引用；不保存 Bucket 密钥、真实路径或临时 URL。

## file_object

已持久化受管文件元数据。所属 B-04，计划 V009。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|所属会议；治理文件为空|
|backend_id|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|固定后端|
|storage_type|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|冗余路由类型需与 backend 一致|
|namespace|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|逻辑命名空间|
|object_key|`varchar(512) CHARACTER SET ascii COLLATE ascii_bin`|否|无|服务生成不可变键，不是用户路径|
|provider_version_id|`longtext`|是|无|供应商版本；local 空|
|display_name|`longtext`|否|无|展示文件名|
|size_bytes|`bigint unsigned`|否|无|实存字节|
|sha256|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|完整内容 SHA256；不是 ETag|
|location_revision|`bigint unsigned`|否|0|迁移位置 CAS|
|state|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|可用/待删/已删|
|reference_epoch|`bigint unsigned`|否|0|引用增删/位置迁移栅栏|
|created_at|`datetime(6)`|否|无|落盘后登记时间|

主键：`id`。
- 唯一约束 `file_location(backend_id,namespace,object_key)`：不可变逻辑位置唯一。
- 索引 `file_meeting(meeting_id,state,id)`：归属文件枚举。
- 索引 `file_cleanup(state,created_at,id)`：可回收候选。
- CHECK `ck_file_object_0`：`storage_type IN ('local','s3')`。
- CHECK `ck_file_object_1`：`state IN ('AVAILABLE','DELETE_PENDING','DELETED')`。
- FK `fk_file_object_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_file_object_1`：`backend_id` → `storage_backend(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：文件及目录持久化先于 DB 登记和业务成功；引用注册、删除认领、位置更新均锁文件；不允许引用 DELETE_PENDING/DELETED；物理删除完成才 DELETED。

## file_reference

所有受管文件使用的统一引用账本。所属 B-04，计划 V009。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|file_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|文件|
|owner_kind|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|白名单领域类型|
|owner_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|具体引用行ID|
|purpose|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|原始/解析/导出/采样等用途|

主键：`file_id,owner_kind,owner_id,purpose`。
- 索引 `file_ref_owner(owner_kind,owner_id,file_id)`：删除所属业务引用。
- FK `fk_file_reference_0`：`file_id` → `file_object(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：typed owner 由领域注册器校验存在及同会议，更新正文关联与本行必须同事务；禁止仅凭 refcount 判断；清理锁 file 后 NOT EXISTS 复验；历史版本引用也登记。

## upload_session

固定后端的断点续传会话。所属 B-04，计划 V009。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|会议|
|backend_id|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|创建时固定|
|namespace|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|逻辑空间|
|object_key|`varchar(512) CHARACTER SET ascii COLLATE ascii_bin`|否|无|服务生成目标键|
|cancel_epoch|`bigint unsigned`|否|无|创建时会议取消代际|
|expected_size|`bigint unsigned`|否|无|期望大小|
|expected_sha256|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|是|无|预声明完整SHA256；若初始NULL，在OPEN转FINALIZING事务填入完成请求哈希，之后不可变|
|provider_upload_token|`longtext`|是|无|内部存储 UploadToken.sessionId；与 backend_id 重建完整 token；已受理会话必须非空，仅服务端使用|
|state|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|会话状态|
|completed_file_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|完成文件；重复完成返回同 ID|
|created_at|`datetime(6)`|否|无|创建时间|
|updated_at|`datetime(6)`|否|无|最近活动|
|display_name|`longtext`|否|无|初始化 name 原值；仅展示数据，不作路径、不新增产品长度上限；创建后不可改|
|purpose|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|初始化时服务端校验的已启用且有权用途码；创建后不可改，不是公司字典声明|

主键：`id`。
- 索引 `upload_meeting(meeting_id,state,id)`：删除与在途枚举。
- 唯一约束 `upload_target(backend_id,namespace,object_key)`：上传目标唯一。
- CHECK `ck_upload_session_0`：`state IN ('OPEN','FINALIZING','COMPLETED','ABORTED','FAILED')`。
- FK `fk_upload_session_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_upload_session_1`：`backend_id` → `storage_backend(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_upload_session_2`：`completed_file_id` → `file_object(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：初始化成功前提交名称/用途/固定位置/存储token；OPEN下提交不可变分片；锁session冻结全部分片及完整hash后单向FINALIZING，不回OPEN；重启据行及分片恢复，完成在durable blob后同事务登记file/引用并写completed_file_id；名称复制到file_object，purpose保留在会话及引用账本；终态重放仍鉴权，删除按依赖显式清理。

## upload_part

已落盘上传分片回执。所属 B-04，计划 V009。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|upload_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|会话|
|part_no|`bigint unsigned`|否|无|1起分片号|
|size_bytes|`bigint unsigned`|否|无|分片大小|
|sha256|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|分片哈希|
|provider_token|`longtext`|是|无|供应商回执；非正文/密钥|

主键：`upload_id,part_no`。
- CHECK `ck_upload_part_0`：`part_no > 0`。
- FK `fk_upload_part_0`：`upload_id` → `upload_session(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：只登记实际durable且校验通过的服务端PartReceipt；锁session确认OPEN后INSERT，同号同size/hash重放原回执，异内容冲突；不UPDATE已接纳回执，不信任客户端provider token；FINALIZING后集合冻结且禁止增删改，迟到I/O不能登记；完成重放/恢复仍需保留完整清单，清理走显式生命周期。

## file_cleanup

可重试删除/孤儿清理登记。所属 B-04/D-07，计划 V009。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|file_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|已登记文件；孤儿为空|
|backend_id|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|待删位置固定后端|
|namespace|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|受控命名空间|
|object_key|`varchar(512) CHARACTER SET ascii COLLATE ascii_bin`|否|无|受限枚举得到的逻辑键|
|expected_location_revision|`bigint unsigned`|是|无|已登记文件的位置栅栏|
|expected_reference_epoch|`bigint unsigned`|是|无|引用栅栏|
|state|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|清理状态|
|not_before|`datetime(6)`|否|无|配置恢复窗口后的可清理时间|
|attempt|`bigint unsigned`|否|0|尝试次数|
|last_error|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|是|无|安全错误码|

主键：`id`。
- 索引 `cleanup_due(state,not_before,id)`：清理候选分页。
- 唯一约束 `cleanup_target(backend_id,namespace,object_key)`：位置仅一清理项。
- CHECK `ck_file_cleanup_0`：`state IN ('PENDING','RUNNING','FAILED','COMPLETE')`。
- FK `fk_file_cleanup_0`：`file_id` → `file_object(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_file_cleanup_1`：`backend_id` → `storage_backend(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：孤儿不允许业务读取；清理前检查 upload/recording 进行中以及所有 file_reference；未配置恢复窗口不清理技术孤儿；不能自动清理历史成果。

## task_subject

每领域聚合的版本/取消 fence（会议仅一个共享根）。所属 B-05，计划 V010。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|subject_kind|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|MEETING/治理根等白名单类型|
|subject_id|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|聚合稳定ID|
|business_version|`bigint unsigned`|否|无|与根 revision 同事务前进|
|cancel_epoch|`bigint unsigned`|否|无|取消/删除代际|
|deleted|`tinyint unsigned`|否|0|不可复活 tombstone|

主键：`subject_kind,subject_id`。
- CHECK `ck_task_subject_0`：`deleted IN (0,1)`。

更新/删除与完整性：领域创建根时创建 fence；enqueue 不得懒建或降低栅栏；typed root 应用完整性；所有写路径先锁此行再锁根。

## durable_task

持久任务；外部未知结果从普通领取隔离。所属 B-05，计划 V010。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|task_type|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|处理类型|
|pool|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|隔离限并发池|
|subject_kind|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|聚合类型|
|subject_id|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|聚合ID|
|producer_key|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|稳定 enqueue 业务键摘要|
|input_kind|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|输入快照类型|
|input_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|不可变输入快照ID|
|input_fingerprint|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|规范化输入版本集合的 SHA-256|
|business_version|`bigint unsigned`|否|无|入队版本|
|cancel_epoch|`bigint unsigned`|否|无|入队取消代际|
|priority|`int`|否|无|有界队列优先级|
|state|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|'READY'|任务状态|
|attempt|`bigint unsigned`|否|0|每次领取递增|
|max_attempts|`bigint unsigned`|否|无|显式策略上限；含崩溃回收|
|lease_owner|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|是|无|当前领取者|
|expires_at|`datetime(6)`|是|无|DB租约截止|
|available_at|`datetime(6)`|否|无|下次允许领取|
|created_at|`datetime(6)`|否|无|入队时间|
|output_kind|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|是|无|输出类型|
|output_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|输出ID|
|last_error|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|是|无|安全失败码|

主键：`id`。
- 唯一约束 `task_producer(producer_key)`：同一业务入队重放唯一。
- 索引 `task_ready(pool,state,available_at,id)`：分开扫描 READY。
- 索引 `task_expired(pool,state,expires_at,id)`：分开扫描过期 RUNNING。
- 索引 `task_subject_history(subject_kind,subject_id,created_at,id)`：聚合任务历史。
- CHECK `ck_durable_task_0`：`state IN ('READY','RUNNING','RECONCILING','SUCCEEDED','FAILED','CANCELLED','STALE')`。
- CHECK `ck_durable_task_1`：`max_attempts > 0`。
- CHECK `ck_durable_task_2`：`state <> 'RUNNING' OR (lease_owner IS NOT NULL AND expires_at IS NOT NULL)`。
- FK `fk_durable_task_0`：`subject_kind,subject_id` → `task_subject(subject_kind,subject_id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：input/output typed refs 由任务注册器校验具体领域存在且相同 subject/版本；producer_key 冲突需比较完整输入，否则冲突；失败/旧结果保留最小诊断不覆盖有效稿。

## task_attempt

每次 Worker 领取诊断历史。所属 B-05，计划 V010。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|task_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|任务|
|attempt|`bigint unsigned`|否|无|领取代际|
|owner|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|Worker身份|
|started_at|`datetime(6)`|否|无|开始|
|ended_at|`datetime(6)`|是|无|结束|
|outcome|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|是|无|结束结果|
|error_code|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|是|无|安全错误码|

主键：`task_id,attempt`。
- FK `fk_task_attempt_0`：`task_id` → `durable_task(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## external_operation

任何可能产生外部效果的调用前 durable marker 及独立对账所有权。所属 B-05，计划 V010。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|operation_kind|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|MODEL_GENERATE/PUBLISH/WITHDRAW/MAIL等|
|business_key|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|稳定外部幂等/查回键|
|request_hash|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|逻辑内容摘要：固定confirmation/body及业务操作；不替代每次完整请求摘要|
|state|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|调用/对账状态|
|marked_at|`datetime(6)`|否|无|首次before-external marker实际提交时间|
|next_check_at|`datetime(6)`|否|无|下次对账|
|reconcile_epoch|`bigint unsigned`|否|0|独立对账租约代际|
|reconcile_owner|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|是|无|对账者|
|reconcile_expires_at|`datetime(6)`|是|无|对账租约DB截止|
|external_result_id|`longtext`|是|无|外部结果定位|
|last_error|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|是|无|安全错误码|
|subject_kind|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|聚合类型|
|subject_id|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|聚合ID|
|current_execution_no|`bigint unsigned`|否|无|当前执行代际；初次1|

主键：`id`。
- 唯一约束 `external_key(operation_kind,business_key)`：同类型业务键唯一。
- 索引 `external_due(state,next_check_at,id)`：到期对账。
- CHECK `ck_external_operation_0`：`state IN ('MARKED','UNKNOWN','RETRY_QUEUED','SUCCEEDED','FAILED','MANUAL_REQUIRED')`。
- CHECK `ck_external_operation_1`：`current_execution_no > 0`。
- FK `fk_external_operation_0`：`subject_kind,subject_id` → `task_subject(subject_kind,subject_id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：operation_kind/business_key全历史唯一；不归单个task。current_execution_no由同聚合锁下CAS；独立reconcile_epoch跨执行永久递增、不清零。当前执行由(operation_id,no)应用校验。仅FAILED且当前execution有确定零效果证据可追加执行；UNKNOWN禁止重发。

## task_outbox_sequence

提交序列锁，避免 AUTO_INCREMENT 先提交高号跳漏。所属 B-05，计划 V010。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`int`|否|无|固定1|
|next_id|`bigint unsigned`|否|1|下一事件号|

主键：`id`。
- CHECK `ck_task_outbox_sequence_0`：`id = 1`。
- CHECK `ck_task_outbox_sequence_1`：`next_id > 0`。

更新/删除与完整性：所有业务锁及审计后最后锁定 id=1；分配/插入事件后立即提交，禁止再锁其他领域行。

## task_outbox

持久引用事件（架构 outbox_event）。所属 B-05，计划 V010。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|sequence_id|`bigint unsigned`|否|无|由事务序列分配的提交顺序|
|event_type|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|事件类型|
|task_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|可空任务引用|
|subject_kind|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|订阅/鉴权聚合类型|
|subject_id|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|聚合ID|
|subject_revision|`bigint unsigned`|否|无|事件业务版本|
|reference_kind|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|事件引用类型|
|reference_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|不含正文的引用ID|
|occurred_at|`datetime(6)`|否|无|UTC事件时间|

主键：`sequence_id`。
- 索引 `outbox_subject(subject_kind,subject_id,sequence_id)`：授权订阅增量。
- FK `fk_task_outbox_0`：`task_id` → `durable_task(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：typed subject/reference 注册器检查；事件不直接携正文；流读取重新鉴权；不按TTL删历史游标，归档后需要明确reset协议。

## outbox_consumer

持久消费游标及接管 fence。所属 B-05，计划 V010。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|consumer_id|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|稳定消费组|
|cursor|`bigint unsigned`|否|0|已完成连续前缀|
|epoch|`bigint unsigned`|否|0|消费者领取代际|
|lease_owner|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|是|无|消费者|
|expires_at|`datetime(6)`|是|无|DB租约截止|

主键：`consumer_id`。

更新/删除与完整性：游标只能前进，重放可重复；本地效果与游标同事务；外部消费者以事件序号去重且成功ACK后才推进；拒绝越过失败事件。

## model_capacity

已审核模型在途容量；无默认公司额度。所属 B-05，计划 V010。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|model|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|模型配置稳定键|
|capacity|`int unsigned`|否|无|明确容量|
|config_revision|`bigint unsigned`|否|无|权威配置版本|
|enabled|`tinyint unsigned`|否|0|允许新调用|
|cutoff_policy|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|平台有证据截止或必须人工终结|

主键：`model`。
- CHECK `ck_model_capacity_0`：`capacity > 0`。
- CHECK `ck_model_capacity_1`：`enabled IN (0,1)`。
- CHECK `ck_model_capacity_2`：`cutoff_policy IN ('PROVEN_DEADLINE','EXPLICIT_SETTLEMENT')`。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## model_reservation

独立于短任务租约的模型额度保留。所属 B-05，计划 V010。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|model|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|模型|
|execution_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|具体外部调用执行；独立占额|
|config_revision|`bigint unsigned`|否|无|准入配置版本|
|safe_deadline|`datetime(6)`|是|无|平台证明截止；不支持时NULL|
|created_at|`datetime(6)`|否|无|准入时间|
|settled_at|`datetime(6)`|是|无|明确终结才释放|

主键：`id`。
- 唯一约束 `reservation_execution(execution_id)`：每实际执行单独占额。
- 索引 `model_deadline(model,settled_at,safe_deadline)`：计数活跃及到期回收。
- FK `fk_model_reservation_0`：`model` → `model_capacity(model)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_model_reservation_1`：`execution_id` → `external_execution(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：独立准入事务按operation→当前execution→model→reservation锁序，复验独立token/执行号及配置；持有本执行许可后才调用。lease/HTTP超时不释放，未知deadline保守占额；execution明确终结后单独幂等结算，不以逻辑操作新执行状态释放旧执行额度。

## input_batch

处理主文件集合快照，禁止混合音视频与纯转写。所属 B-06/C-01，计划 V011。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|会议|
|kind|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|TRANSCRIPT 或 MEDIA|
|input_fingerprint|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|规范化输入版本集合的 SHA-256|
|base_revision|`bigint unsigned`|否|无|启动时修订|
|created_at|`datetime(6)`|否|无|冻结时间|

主键：`id`。
- CHECK `ck_input_batch_0`：`kind IN ('TRANSCRIPT','MEDIA')`。
- FK `fk_input_batch_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## input_batch_file

不可变有序源文件清单。所属 B-06/C-01，计划 V011。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|batch_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|输入集合|
|ordinal|`bigint unsigned`|否|无|从1顺序|
|file_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|已校验主文件|
|sha256|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|冻结内容校验|

主键：`batch_id,ordinal`。
- 唯一约束 `batch_file_unique(batch_id,file_id)`：同集合文件不重复。
- CHECK `ck_input_batch_file_0`：`ordinal > 0`。
- FK `fk_input_batch_file_0`：`batch_id` → `input_batch(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_input_batch_file_1`：`file_id` → `file_object(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## transcript_revision

原始/增强/人工/候选转写不可变版本；确认另表。所属 B-06，计划 V011。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|会议|
|revision|`bigint unsigned`|否|无|单调转写版本|
|kind|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|版本来源|
|batch_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|输入集合|
|parent_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|上个稿|
|input_fingerprint|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|规范化输入版本集合的 SHA-256|
|created_by|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|创建者|
|created_at|`datetime(6)`|否|无|保存时间|
|processing_input_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|固定处理/增强输入；手工修订继承父处理来源|
|producer_task_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|产生本结果任务；人工保存为空|

主键：`id`。
- 唯一约束 `transcript_history(meeting_id,revision)`：唯一版本与历史。
- CHECK `ck_transcript_revision_0`：`kind IN ('RAW','ENHANCED','WORKING','CANDIDATE')`。
- FK `fk_transcript_revision_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_transcript_revision_1`：`batch_id` → `input_batch(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_transcript_revision_2`：`parent_id` → `transcript_revision(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_transcript_revision_3`：`processing_input_id` → `transcription_input(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_transcript_revision_4`：`producer_task_id` → `durable_task(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：各稿不可变；生成结果input_fingerprint必须等于processing_input输入指纹，producer_task.spec.input_id必须同该输入。WORKING/恢复稿继承具体父稿处理来源，不能改用当前材料配置；确认content_hash另含已保存片段与多选标记。

## transcript_segment

稳定片段ID跨修订保留；纯文档无虚构音频时间。所属 B-06，计划 V011。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|具体转写版本|
|segment_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|跨版本稳定段ID|
|ordinal|`bigint unsigned`|否|无|版本内排序|
|body|`longtext`|否|无|已保存片段正文|
|source_file_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|源文件|
|source_paragraph|`bigint unsigned`|是|无|纯文本段序|
|start_ms|`bigint unsigned`|是|无|音频起点ms|
|end_ms|`bigint unsigned`|是|无|音频终点ms|
|speaker_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|说话人业务身份；不授权|

主键：`revision_id,segment_id`。
- 唯一约束 `segment_order(revision_id,ordinal)`：完整稿顺序。
- CHECK `ck_transcript_segment_0`：`(start_ms IS NULL AND end_ms IS NULL) OR (start_ms IS NOT NULL AND end_ms IS NOT NULL AND end_ms >= start_ms)`。
- FK `fk_transcript_segment_0`：`revision_id` → `transcript_revision(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_transcript_segment_1`：`source_file_id` → `file_object(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_transcript_segment_2`：`speaker_id` → `speaker(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## transcript_confirmation

确认转写快照；只指不可变保存稿。所属 B-06，计划 V011。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|会议|
|revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|确认的不可变转写|
|content_hash|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|完整内容与来源哈希|
|confirmed_by|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|人工确认者|
|confirmed_at|`datetime(6)`|否|无|确认时间|

主键：`id`。
- 唯一约束 `transcript_confirm_once(meeting_id,revision_id)`：同稿确认重放。
- FK `fk_transcript_confirmation_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_transcript_confirmation_1`：`revision_id` → `transcript_revision(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_transcript_confirmation_2`：`confirmed_by` → `enterprise_identity(enterprise_id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## template_revision

内置/个人模板不可变版本。所属 B-07/D-04，计划 V012。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|template_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|模板逻辑ID|
|revision|`bigint unsigned`|否|无|模板版本|
|owner_id|`varchar(255) COLLATE utf8mb4_0900_bin`|是|无|个人所有者；内置NULL|
|name|`longtext`|否|无|模板名|
|themes|`longtext`|否|无|有序主题 JSON 文本，schema含1..8项|
|theme_count|`bigint unsigned`|否|无|主题数|
|created_at|`datetime(6)`|否|无|保存时间|

主键：`id`。
- 唯一约束 `template_version(template_id,revision)`：版本唯一。
- 索引 `template_owner(owner_id,template_id,revision)`：本人模板列表。
- CHECK `ck_template_revision_0`：`theme_count BETWEEN 1 AND 8`。
- FK `fk_template_revision_0`：`owner_id` → `enterprise_identity(enterprise_id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_template_revision_1`：`template_id` → `minutes_template(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：不可变历史；owner_id必须与minutes_template一致，name是该修订的展示名；当前名称只由逻辑根登记；根当前指针由服务校验同template_id并同事务更新，避免循环FK。

## generation_input

纪要输入冻结集合；禁止未确认转写入队。所属 B-07，计划 V012。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|会议|
|transcript_confirmation_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|必需确认转写|
|material_snapshot_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|B允许NULL代表规范空集合|
|template_revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|固定模板|
|exclusion_revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|固定排除集|
|element_set_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|固定七类要素集合；首次可空|
|prompt_version|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|版本化提示词引用|
|model_config_version|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|审核模型配置引用|
|base_revision|`bigint unsigned`|否|无|采用时 CAS 基线|
|input_fingerprint|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|规范化输入版本集合的 SHA-256|
|scope|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|全文/单要素|
|target_element_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|单要素目标|

主键：`id`。
- CHECK `ck_generation_input_0`：`scope IN ('FULL','ELEMENT')`。
- CHECK `ck_generation_input_1`：`(scope='FULL' AND target_element_id IS NULL) OR (scope='ELEMENT' AND target_element_id IS NOT NULL)`。
- FK `fk_generation_input_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_generation_input_1`：`transcript_confirmation_id` → `transcript_confirmation(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_generation_input_2`：`template_revision_id` → `template_revision(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_generation_input_3`：`exclusion_revision_id` → `exclusion_revision(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_generation_input_4`：`element_set_id` → `element_set(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_generation_input_5`：`material_snapshot_id` → `material_snapshot(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：输入全部同会议/允许模板权限；材料/要素/排除 FK 在对应表创建后加；prompt/model 引用受版本配置端口校验，B期文件配置需稳定版本，绝不指当前默认配置。

## minutes_revision

已保存正文；候选与当前指针分离。所属 B-07，计划 V012。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|会议|
|revision|`bigint unsigned`|否|无|单调正文版本|
|parent_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|父版本|
|title|`longtext`|否|无|已保存标题|
|title_origin|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|HUMAN/LINKED/AI|
|body|`longtext`|否|无|已保存 Markdown|
|generation_input_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|生成输入；手写仍继承输入集|
|created_by|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|创建者|
|created_at|`datetime(6)`|否|无|保存时间|

主键：`id`。
- 唯一约束 `minutes_history(meeting_id,revision)`：正文历史。
- CHECK `ck_minutes_revision_0`：`title_origin IN ('HUMAN','LINKED','AI')`。
- FK `fk_minutes_revision_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_minutes_revision_1`：`parent_id` → `minutes_revision(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_minutes_revision_2`：`generation_input_id` → `generation_input(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## element_set

独立要素集合修订，不反向改写正文。所属 B-07/D-03，计划 V012。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|会议|
|revision|`bigint unsigned`|否|无|要素修订|
|created_at|`datetime(6)`|否|无|保存时间|

主键：`id`。
- 唯一约束 `element_set_revision(meeting_id,revision)`：要素历史。
- FK `fk_element_set_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## minutes_element

七类主体要素；敏感与背景不是额外主体类。所属 B-07/D-03，计划 V012。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|set_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|要素集合|
|category|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|七类之一|
|ordinal|`bigint unsigned`|否|无|同类顺序|
|body|`longtext`|否|无|内容|
|support_state|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|该版本创建时的冻结分类；不能单独证明当前人工复核充分性，参见result_review|
|source_context_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|不可变来源选择；NULL仅历史未知；v3新写必填由服务验证|

主键：`id`。
- 唯一约束 `element_order(set_id,category,ordinal)`：分类顺序。
- 索引 `minutes_element_context(source_context_id,id)`：历史来源选择反向依赖。
- CHECK `ck_minutes_element_0`：`category IN ('OVERVIEW','DISCUSSION','CONCLUSION','ACTION','RISK','OPEN_QUESTION','DISAGREEMENT')`。
- CHECK `ck_minutes_element_1`：`support_state IN ('SUPPORTED','PARTIAL','UNSUPPORTED','NOT_EXTRACTED')`。
- FK `fk_minutes_element_0`：`set_id` → `element_set(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_minutes_element_1`：`source_context_id` → `source_context(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理；B00-1.3当前人工支持由精确result_review决定，旧support_state不原地改写；新目标内容/来源/版本使旧断言失效；B00-1.4-proposed来源basis由自身source_context_id确定，人工改写不声称模型生成；evidence自身上下文优先，不能从要素/正文/ACTION建议推断。新v3写NULL由应用拒绝；旧NULL明确不可恢复；所有纳入要素basis无论证据数量均为域内根，完整选择/profile须验证与保留；根精确作用域见R1上下文根，不伪造证据或支持信用。

## action_item

行动项结构信息；负责人不授权。所属 B-07/D-03，计划 V012。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|element_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|ACTION类型要素|
|owner_enterprise_id|`varchar(255) COLLATE utf8mb4_0900_bin`|是|无|明确选择企业负责人|
|owner_display|`longtext`|是|无|无账号业务人员显示|
|due_on|`date`|是|无|可空截止日|

主键：`element_id`。
- FK `fk_action_item_0`：`element_id` → `minutes_element(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_action_item_1`：`owner_enterprise_id` → `enterprise_identity(enterprise_id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：仅人工明确保存后的接受字段；生成初始owner/date均NULL，模型提议在action_suggestion。人工字段保存建立新要素版本并写新ACTION_FIELDS复核，缺少截止不强补；外部负责人规则未定不启用；角色/名字/负责人不授权。

## exclusion_revision

敏感排除集合版本；空集也有ID。所属 B-07，计划 V012。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|会议|
|revision|`bigint unsigned`|否|无|排除修订|
|created_at|`datetime(6)`|否|无|保存时间|

主键：`id`。
- 唯一约束 `exclusion_version(meeting_id,revision)`：排除历史。
- FK `fk_exclusion_revision_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## sensitive_exclusion

被明确排除的来源；不删除转写/要素/录音。所属 B-07/D-03，计划 V012。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|排除集合|
|locator_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|具体源位置|
|category|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|原文两类敏感分类的稳定配置键|
|reason|`longtext`|是|无|排除说明|

主键：`revision_id,locator_id,category`。
- CHECK `ck_sensitive_exclusion_0`：`category IN ('INTERNAL_BUSINESS_DATA','POLITICAL_SENSITIVE')`。
- FK `fk_sensitive_exclusion_0`：`revision_id` → `exclusion_revision(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_sensitive_exclusion_1`：`locator_id` → `source_locator(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：不可变选择集；PK保留同一locator的两个标签，不复制物理locator绕过去重。勾选多来源检测展开同category全部locator；重叠项按(locator,category)并集，取消只影响对应标签/位置。下一次生成应用，不删除检测/原文/要素、不改ACL。

## generation_candidate

生成结果候选；迟到结果不直接覆盖。所属 B-07，计划 V012。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|会议|
|input_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|冻结输入|
|task_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|生成任务|
|minutes_revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|候选正文；局部候选可空|
|element_set_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|候选要素|
|base_revision|`bigint unsigned`|否|无|生成前聚合修订|
|input_fingerprint|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|规范化输入版本集合的 SHA-256|
|state|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|待采用/已处理/过期|
|created_at|`datetime(6)`|否|无|产生时间|
|decided_at|`datetime(6)`|是|无|处理时间|

主键：`id`。
- 索引 `candidate_pending(meeting_id,state,created_at,id)`：最终确认拦截待处理。
- 唯一约束 `candidate_task(task_id)`：每任务单一候选。
- CHECK `ck_generation_candidate_0`：`state IN ('PENDING','ADOPTED','REJECTED','STALE')`。
- FK `fk_generation_candidate_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_generation_candidate_1`：`input_id` → `generation_input(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_generation_candidate_2`：`task_id` → `durable_task(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_generation_candidate_3`：`minutes_revision_id` → `minutes_revision(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_generation_candidate_4`：`element_set_id` → `element_set(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## source_locator

有类型的不可变来源定位，不能以通用JSON抹去来源约束。所属 B-07/D-01，计划 V012。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|会议|
|kind|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|来源类型|
|transcript_revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|转写版本|
|segment_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|转写稳定段|
|file_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|媒体/本地文档文件|
|material_revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|知识库/材料固定版本|
|page_no|`bigint unsigned`|是|无|PDF/PPT 1起页|
|paragraph_no|`bigint unsigned`|是|无|Word/文本1起段|
|section_path|`longtext`|是|无|章节定位|
|start_ms|`bigint unsigned`|是|无|音频起|
|end_ms|`bigint unsigned`|是|无|音频止|

主键：`id`。
- 索引 `locator_meeting(meeting_id,id)`：批量来源校验。
- CHECK `ck_source_locator_0`：`kind IN ('TRANSCRIPT','AUDIO','PDF_PAGE','PPT_PAGE','WORD_PARAGRAPH','TEXT_PARAGRAPH','KNOWLEDGE_VERSION')`。
- CHECK `ck_source_locator_1`：`(kind='TRANSCRIPT' AND transcript_revision_id IS NOT NULL AND segment_id IS NOT NULL) OR (kind='AUDIO' AND file_id IS NOT NULL AND start_ms IS NOT NULL AND end_ms IS NOT NULL AND end_ms>=start_ms) OR (kind IN ('PDF_PAGE','PPT_PAGE') AND file_id IS NOT NULL AND page_no IS NOT NULL AND page_no>0) OR (kind IN ('WORD_PARAGRAPH','TEXT_PARAGRAPH') AND file_id IS NOT NULL AND paragraph_no IS NOT NULL AND paragraph_no>0) OR (kind='KNOWLEDGE_VERSION' AND material_revision_id IS NOT NULL)`。
- FK `fk_source_locator_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_source_locator_1`：`transcript_revision_id,segment_id` → `transcript_segment(revision_id,segment_id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_source_locator_2`：`file_id` → `file_object(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_source_locator_3`：`material_revision_id` → `material_revision(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：服务按kind拒绝非适用字段；PDF/PPT/Word等要核对解析页段存在；KNOWLEDGE_VERSION必须material_revision固定外部版本；所有来源同会议且属于输入快照，非FK可表达的跨集约束由领域事务验证。

## evidence_link

具体要素或正文版本的引用证据。所属 B-07/D-03，计划 V012。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|locator_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|源定位|
|element_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|目标要素；二选一|
|minutes_revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|目标正文版本；二选一|
|support_state|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|该版本创建时的冻结分类；不能单独证明当前人工复核充分性，参见result_review|
|target_anchor|`longtext`|否|无|正文段锚或要素局部锚|
|source_context_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|不可变来源选择；NULL仅历史未知；v3新写必填由服务验证|

主键：`id`。
- 索引 `evidence_element(element_id,locator_id)`：要素来源追踪。
- 索引 `evidence_minutes(minutes_revision_id,locator_id)`：正文引用。
- 索引 `evidence_link_context(source_context_id,id)`：历史来源选择反向依赖。
- CHECK `ck_evidence_link_0`：`(element_id IS NULL) <> (minutes_revision_id IS NULL)`。
- CHECK `ck_evidence_link_1`：`support_state IN ('SUPPORTED','PARTIAL','UNSUPPORTED')`。
- FK `fk_evidence_link_0`：`locator_id` → `source_locator(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_evidence_link_1`：`element_id` → `minutes_element(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_evidence_link_2`：`minutes_revision_id` → `minutes_revision(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_evidence_link_3`：`source_context_id` → `source_context(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理；B00-1.3当前人工支持由精确result_review决定，旧support_state不原地改写；新目标内容/来源/版本使旧断言失效；B00-1.4-proposed来源basis由自身source_context_id确定，人工改写不声称模型生成；evidence自身上下文优先，不能从要素/正文/ACTION建议推断。新v3写NULL由应用拒绝；旧NULL明确不可恢复。

## final_confirmation

冻结最终已保存版本集合及校验摘要。所属 B-08，计划 V013。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|会议|
|meeting_revision|`bigint unsigned`|否|无|确认时聚合修订|
|info_revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|基础信息|
|roster_revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|确认名单|
|transcript_confirmation_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|确认转写|
|minutes_revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|已保存正文|
|material_snapshot_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|空材料NULL|
|element_set_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|要素集合|
|template_revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|模板|
|exclusion_revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|排除设置|
|source_manifest_hash|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|冻结证据/来源及final_result_review精确成员与完整复核投影摘要；不得重查最新复核|
|input_fingerprint|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|规范化输入版本集合的 SHA-256|
|confirmed_by|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|确认负责人|
|confirmed_at|`datetime(6)`|否|无|确认时间|

主键：`id`。
- 唯一约束 `final_revision(meeting_id,meeting_revision)`：同版本确认唯一。
- FK `fk_final_confirmation_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_final_confirmation_1`：`info_revision_id` → `meeting_info_revision(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_final_confirmation_2`：`roster_revision_id` → `roster_revision(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_final_confirmation_3`：`transcript_confirmation_id` → `transcript_confirmation(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_final_confirmation_4`：`minutes_revision_id` → `minutes_revision(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_final_confirmation_5`：`element_set_id` → `element_set(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_final_confirmation_6`：`template_revision_id` → `template_revision(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_final_confirmation_7`：`exclusion_revision_id` → `exclusion_revision(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_final_confirmation_8`：`confirmed_by` → `enterprise_identity(enterprise_id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_final_confirmation_9`：`material_snapshot_id` → `material_snapshot(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理；B00-1.3同事务写final_result_review，历史读取该冻结成员，不随新review改变；全部业务/权限/敏感/来源检查仍由B08负责。

## publication

冻结发布操作；成功一次即永久只读。所属 B-08，计划 V013。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|会议|
|confirmation_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|最终快照|
|business_key|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|publicationId + confirmationId稳定编码|
|library_id|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|外部库ID|
|folder_id|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|外部目录ID|
|state|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|发布执行状态|
|revision|`bigint unsigned`|否|无|CAS|
|created_at|`datetime(6)`|否|无|登记时间|
|published_at|`datetime(6)`|是|无|确定成功时间|

主键：`id`。
- 唯一约束 `publication_meeting(meeting_id)`：每会议唯一发布根；确定零效果失败可原确认追加执行，真实内容修改才换确认。
- 唯一约束 `publication_key(business_key)`：外部业务键唯一。
- CHECK `ck_publication_0`：`state IN ('READY','RUNNING','RECONCILING','FAILED','SUCCEEDED')`。
- FK `fk_publication_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_publication_1`：`confirmation_id` → `final_confirmation(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：同confirmation确定零效果失败后可原确认重试：复用稳定publication/business_key/operation，追加external_execution及新task，不要求新确认；双重retry在subject/根/operation锁下只接受一次。更换confirmation仅真实修改重新确认时允许。SUCCEEDED永不重试，UNKNOWN不重发。

## publication_attempt

每次发布/查回的诊断与固定确认关联。所属 B-08，计划 V013。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|publication_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|发布根|
|confirmation_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|该次固定确认|
|operation_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|B05 durable外部操作|
|kind|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|PUBLISH/RECONCILE/WITHDRAW/OBSOLETE|
|outcome|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|结果状态|
|created_at|`datetime(6)`|否|无|开始时间|
|finished_at|`datetime(6)`|是|无|终结时间|
|execution_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|具体发布/查回操作对应执行|

主键：`id`。
- 索引 `publication_attempt_history(publication_id,created_at,id)`：治理历史。
- CHECK `ck_publication_attempt_0`：`kind IN ('PUBLISH','RECONCILE','WITHDRAW','OBSOLETE')`。
- CHECK `ck_publication_attempt_1`：`outcome IN ('PENDING','SUCCESS','FAILED','UNKNOWN')`。
- FK `fk_publication_attempt_0`：`publication_id` → `publication(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_publication_attempt_1`：`confirmation_id` → `final_confirmation(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_publication_attempt_2`：`operation_id` → `external_operation(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_publication_attempt_3`：`execution_id` → `external_execution(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## external_document_mapping

唯一成功外部文档映射。所属 B-08，计划 V013。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|publication_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|成功发布根|
|provider_id|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|知识库配置ID|
|external_document_id|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|外部稳定ID|
|document_url|`longtext`|否|无|经过允许列表校验的展示URL|
|authority_state|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|最新权威状态|
|verified_at|`datetime(6)`|否|无|状态权威核验时间|

主键：`publication_id`。
- 唯一约束 `external_document_unique(provider_id,external_document_id)`：一个外部文档仅映射一个成果。
- CHECK `ck_external_document_mapping_0`：`authority_state IN ('ACTIVE','WITHDRAWN','OBSOLETE','UNKNOWN')`。
- FK `fk_external_document_mapping_0`：`publication_id` → `publication(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## publication_governance

不可逆撤回/废止请求和确认记录。所属 D-05，计划 V020。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|publication_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|成果|
|action|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|撤回/废止|
|reason|`longtext`|否|无|治理原因|
|actor_id|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|有权治理主体|
|state|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|同步结果|
|operation_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|外部幂等操作|
|created_at|`datetime(6)`|否|无|本地立即阻断时间|

主键：`id`。
- CHECK `ck_publication_governance_0`：`action IN ('WITHDRAW','OBSOLETE')`。
- CHECK `ck_publication_governance_1`：`state IN ('PENDING','UNKNOWN','COMPLETE','FAILED')`。
- FK `fk_publication_governance_0`：`publication_id` → `publication(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_publication_governance_1`：`operation_id` → `external_operation(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## external_event_receipt

验签后外部事件去重/重放防护。所属 D-05，计划 V020。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|provider_id|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|供应商|
|event_id|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|供应商唯一事件|
|payload_hash|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|安全摘要，重复异内容拒绝|
|received_at|`datetime(6)`|否|无|接收时间|
|processed_at|`datetime(6)`|是|无|处理提交时间|

主键：`provider_id,event_id`。

更新/删除与完整性：协议签名/有效时间窗先校验；处理与收据同事务；乱序事件通过权威查回确定，不按到达顺序恢复终态。

## media_source

归一化音频采样数与多源映射。所属 C-01，计划 V014。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|batch_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|有序输入批次|
|normalized_file_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|归一化WAV|
|sample_rate|`bigint unsigned`|否|无|采样率|
|channels|`bigint unsigned`|否|无|通道|
|bits_per_sample|`bigint unsigned`|否|无|采样位宽|
|sample_count|`bigint unsigned`|否|无|有效采样总数|

主键：`id`。
- CHECK `ck_media_source_0`：`sample_rate>0 AND channels>0 AND bits_per_sample>0`。
- FK `fk_media_source_0`：`batch_id` → `input_batch(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_media_source_1`：`normalized_file_id` → `file_object(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## media_source_span

跨段独立源时间轴到合并轴映射。所属 C-01，计划 V014。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|media_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|归一化音频|
|ordinal|`bigint unsigned`|否|无|片段顺序|
|source_file_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|原源文件|
|source_start_sample|`bigint unsigned`|否|无|源起点|
|global_start_sample|`bigint unsigned`|否|无|合并有效采样起点|
|sample_count|`bigint unsigned`|否|无|长度|

主键：`media_id,ordinal`。
- CHECK `ck_media_source_span_0`：`ordinal>0 AND sample_count>0`。
- FK `fk_media_source_span_0`：`media_id` → `media_source(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_media_source_span_1`：`source_file_id` → `file_object(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## speaker

说话人业务标签；实名与授权隔离。所属 B-06/C-03，计划 V011。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|会议|
|display_name|`longtext`|否|无|匿名/人工标签|
|enterprise_id|`varchar(255) COLLATE utf8mb4_0900_bin`|是|无|人工确认实名可空|
|identity_source|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|ANONYMOUS/HUMAN/VOICE_CANDIDATE|
|merged_into_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|合并目标|
|revision|`bigint unsigned`|否|无|说话人修订|

主键：`id`。
- CHECK `ck_speaker_0`：`identity_source IN ('ANONYMOUS','HUMAN','VOICE_CANDIDATE')`。
- FK `fk_speaker_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_speaker_1`：`enterprise_id` → `enterprise_identity(enterprise_id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_speaker_2`：`merged_into_id` → `speaker(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## correction

与具体转写版本绑定的纠错建议。所属 C-03，计划 V015。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|会议|
|revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|源转写版本|
|segment_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|稳定段|
|proposed_text|`longtext`|否|无|建议文字|
|state|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|待处理/采纳/拒绝/失效|
|revision|`bigint unsigned`|否|无|建议CAS|

主键：`id`。
- 索引 `correction_pending(meeting_id,revision_id,state,id)`：确认前零待处理检查。
- CHECK `ck_correction_0`：`state IN ('PENDING','ACCEPTED','REJECTED','STALE')`。
- FK `fk_correction_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_correction_1`：`revision_id,segment_id` → `transcript_segment(revision_id,segment_id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## edit_operation

可撤销编辑操作；撤销也创建新修订。所属 C-03，计划 V015。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|会议|
|before_revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|之前转写|
|after_revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|之后转写|
|kind|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|纠错/替换/合并/撤销|
|reverts_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|撤销对象|
|actor_id|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|操作者|
|created_at|`datetime(6)`|否|无|操作时间|

主键：`id`。
- 索引 `edit_history(meeting_id,created_at,id)`：历史顺序。
- FK `fk_edit_operation_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_edit_operation_1`：`before_revision_id` → `transcript_revision(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_edit_operation_2`：`after_revision_id` → `transcript_revision(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_edit_operation_3`：`reverts_id` → `edit_operation(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## recording_session

唯一采集者与writerEpoch；固定后端。所属 C-04，计划 V016。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|会议|
|backend_id|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|创建固定后端|
|state|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|录音状态|
|writer_epoch|`bigint unsigned`|否|无|重连抢占代际|
|writer_id|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|当前采集端|
|writer_expires_at|`datetime(6)`|否|无|DB写租约|
|cancel_epoch|`bigint unsigned`|否|无|会议代际|
|archive_file_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|完整归档后文件|

主键：`id`。
- 索引 `recording_meeting(meeting_id,state,id)`：删除资格检查。
- CHECK `ck_recording_session_0`：`state IN ('RECORDING','PAUSED','FINISHING','ARCHIVING','AVAILABLE','FAILED','ABORTED')`。
- FK `fk_recording_session_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_recording_session_1`：`backend_id` → `storage_backend(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_recording_session_2`：`archive_file_id` → `file_object(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## recording_part

分段独立序号与durable连续前缀。所属 C-04，计划 V016。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|session_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|会话|
|ordinal|`bigint unsigned`|否|无|段序|
|highest_contiguous_seq|`bigint unsigned`|否|0|已落盘连续序号|
|final_seq|`bigint unsigned`|是|无|结束申报；未结束NULL|
|sample_count|`bigint unsigned`|否|0|校验后的有效总采样|

主键：`id`。
- 唯一约束 `recording_part_order(session_id,ordinal)`：段序唯一。
- CHECK `ck_recording_part_0`：`ordinal>0`。
- FK `fk_recording_part_0`：`session_id` → `recording_session(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## recording_chunk

落盘ACK事实来源，重复序号异哈希冲突。所属 C-04，计划 V016。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|part_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|录音段|
|seq|`bigint unsigned`|否|无|1起序号|
|writer_epoch|`bigint unsigned`|否|无|收到时已验证代际|
|sample_start|`bigint unsigned`|否|无|0起有效采样|
|sample_count|`bigint unsigned`|否|无|采样长度|
|sha256|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|音频校验|
|file_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|已落盘分片|
|persisted_at|`datetime(6)`|否|无|落盘登记时间|

主键：`part_id,seq`。
- CHECK `ck_recording_chunk_0`：`seq>0 AND sample_count>0`。
- FK `fk_recording_chunk_0`：`part_id` → `recording_part(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_recording_chunk_1`：`file_id` → `file_object(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## subtitle_revision

临时/稳定字幕历史；不冒充正式转写。所属 C-06，计划 V016。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|session_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|录音会话|
|segment_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|字幕段|
|revision|`bigint unsigned`|否|无|字幕版本|
|part_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|录音分段|
|state|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|平台真实语义|
|body|`longtext`|否|无|字幕|
|start_ms|`bigint unsigned`|否|无|段起|
|end_ms|`bigint unsigned`|否|无|段止|
|event_sequence|`bigint unsigned`|否|无|outbox提交游标|

主键：`session_id,segment_id,revision`。
- 索引 `subtitle_event(session_id,event_sequence)`：重连顺序。
- CHECK `ck_subtitle_revision_0`：`state IN ('TEMPORARY','STABLE')`。
- CHECK `ck_subtitle_revision_1`：`end_ms>=start_ms`。
- FK `fk_subtitle_revision_0`：`session_id` → `recording_session(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_subtitle_revision_1`：`part_id` → `recording_part(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## material_reference

会议本地/知识库材料逻辑关联。所属 D-01，计划 V017。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|会议|
|kind|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|LOCAL/KNOWLEDGE|
|provider_id|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|是|无|外部提供方|
|external_id|`varchar(255) COLLATE utf8mb4_0900_bin`|是|无|外部稳定文件ID|
|file_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|本地文件|
|state|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|关联状态|

主键：`id`。
- 索引 `material_meeting(meeting_id,state,id)`：材料列表。
- CHECK `ck_material_reference_0`：`kind IN ('LOCAL','KNOWLEDGE')`。
- CHECK `ck_material_reference_1`：`state IN ('ACTIVE','REMOVED')`。
- CHECK `ck_material_reference_2`：`(kind='LOCAL' AND file_id IS NOT NULL AND external_id IS NULL) OR (kind='KNOWLEDGE' AND provider_id IS NOT NULL AND external_id IS NOT NULL AND file_id IS NULL)`。
- FK `fk_material_reference_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_material_reference_1`：`file_id` → `file_object(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## material_revision

固定源版本；外部引用永不跟随最新。所属 D-01，计划 V017。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|reference_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|材料关联|
|revision|`bigint unsigned`|否|无|材料版本|
|external_version|`longtext`|是|无|稳定外部版本|
|content_file_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|受控获取的不可变本地内容|
|content_hash|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内容校验|
|parse_state|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|解析状态|
|created_at|`datetime(6)`|否|无|版本建立|

主键：`id`。
- 唯一约束 `material_version(reference_id,revision)`：唯一历史。
- CHECK `ck_material_revision_0`：`parse_state IN ('PENDING','READY','FAILED','UNSUPPORTED')`。
- FK `fk_material_revision_0`：`reference_id` → `material_reference(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_material_revision_1`：`content_file_id` → `file_object(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## material_snapshot

已明确应用材料集合；待修订不能改有效集合。所属 D-01，计划 V017。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|会议|
|revision|`bigint unsigned`|否|无|集合版本|
|input_fingerprint|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|规范化输入版本集合的 SHA-256|
|applied_at|`datetime(6)`|否|无|明确应用时间|

主键：`id`。
- 唯一约束 `material_snapshot_revision(meeting_id,revision)`：快照历史。
- FK `fk_material_snapshot_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## material_snapshot_item

冻结材料修订清单。所属 D-01，计划 V017。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|snapshot_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|快照|
|material_revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|固定材料版本|
|ordinal|`bigint unsigned`|否|无|显示顺序|

主键：`snapshot_id,material_revision_id`。
- 唯一约束 `snapshot_order(snapshot_id,ordinal)`：唯一顺序。
- FK `fk_material_snapshot_item_0`：`snapshot_id` → `material_snapshot(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_material_snapshot_item_1`：`material_revision_id` → `material_revision(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## parsed_document

解析版本及能力状态；不把空解析当成功。所属 D-01，计划 V017。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|material_revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|材料版本|
|parser_version|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|解析器配置版本|
|text_file_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|完整文本文件|
|state|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|解析结果状态|
|unit_count|`bigint unsigned`|否|无|页段数|
|error_code|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|是|无|明确不支持/失败原因|

主键：`id`。
- CHECK `ck_parsed_document_0`：`state IN ('READY','FAILED','UNSUPPORTED')`。
- FK `fk_parsed_document_0`：`material_revision_id` → `material_revision(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_parsed_document_1`：`text_file_id` → `file_object(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## parsed_unit

文本与页/段映射保留。所属 D-01，计划 V017。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|document_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|解析文档|
|ordinal|`bigint unsigned`|否|无|文本单元序号|
|kind|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|PAGE/PARAGRAPH/SECTION|
|page_no|`bigint unsigned`|是|无|页码|
|paragraph_no|`bigint unsigned`|是|无|段号|
|section_path|`longtext`|是|无|章节路径|
|body|`longtext`|否|无|抽取文字|

主键：`document_id,ordinal`。
- CHECK `ck_parsed_unit_0`：`kind IN ('PAGE','PARAGRAPH','SECTION')`。
- FK `fk_parsed_unit_0`：`document_id` → `parsed_document(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## export_job

按已保存有效版本生成三格式导出。所属 D-06，计划 V021。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|会议|
|minutes_revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|具体正文版本|
|confirmation_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|发布回看固定快照|
|format|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|md/pdf/docx|
|task_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|导出任务|
|output_file_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|输出文件|
|access_revision|`bigint unsigned`|否|无|受理时权限版本|
|state|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|任务状态|

主键：`id`。
- CHECK `ck_export_job_0`：`format IN ('md','pdf','docx')`。
- CHECK `ck_export_job_1`：`state IN ('PENDING','READY','FAILED','INVALIDATED')`。
- FK `fk_export_job_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_export_job_1`：`minutes_revision_id` → `minutes_revision(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_export_job_2`：`confirmation_id` → `final_confirmation(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_export_job_3`：`task_id` → `durable_task(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_export_job_4`：`output_file_id` → `file_object(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## meeting_deletion

删除请求与清理完成事实。所属 D-07，计划 V022。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|会议|
|cancel_epoch|`bigint unsigned`|否|无|删除时递增代际|
|state|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|清理结果|
|task_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|清理协调任务|
|requested_at|`datetime(6)`|否|无|访问立即阻断时间|
|completed_at|`datetime(6)`|是|无|物理文件及业务正文清理完成|

主键：`id`。
- 唯一约束 `deletion_meeting(meeting_id)`：单一删除根。
- CHECK `ck_meeting_deletion_0`：`state IN ('PENDING','FAILED','COMPLETE')`。
- FK `fk_meeting_deletion_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_meeting_deletion_1`：`task_id` → `durable_task(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## topic

单层个人专题，不改变内容版本/ACL。所属 D-08，计划 V023。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|owner_id|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|专题所有者|
|name|`longtext`|否|无|名称|
|revision|`bigint unsigned`|否|无|专题CAS|
|created_at|`datetime(6)`|否|无|创建时间|
|description|`longtext`|是|无|R3 可选专题说明；未填写/空串归一为NULL|
|name_normalized|`longtext COLLATE utf8mb4_0900_bin`|否|无|R2 NFC及首尾Unicode空白剔除后的完整名称|
|name_hash|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|完整name_normalized UTF-8字节SHA256，仅候选桶|

主键：`id`。
- 索引 `topic_owner(owner_id,id)`：个人专题。
- 索引 `topic_name_bucket(owner_id,name_hash,id)`：非唯一摘要桶；持owner身份行锁后完整二进制名称复核。
- FK `fk_topic_0`：`owner_id` → `enterprise_identity(enterprise_id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：个人名称不能为空；创建/改名先锁owner企业身份行，再按统一顺序锁专题并CAS revision；摘要相同逐项比较完整规范化名称，仅完整相同冲突；说明与名称同事务保存；空专题才能删除。

## meeting_topic

本人负责且已发布会议归集。所属 D-08，计划 V023。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|topic_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|专题|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|会议|
|added_at|`datetime(6)`|否|无|加入时间|

主键：`topic_id,meeting_id`。
- FK `fk_meeting_topic_0`：`topic_id` → `topic(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_meeting_topic_1`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：事务校验专题owner=会议owner且ever_published=1；不授予权限；空专题才能删，移除归集不删会议。

## corpus_feedback

受控反馈候选/审核；不改原确认稿。所属 E-01，计划 V024。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|来源会议|
|source_revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|来源转写版本|
|submitted_by|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|提交人|
|category|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|受控类别键|
|comment|`longtext`|否|无|反馈内容|
|state|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|候选审核状态|
|revision|`bigint unsigned`|否|无|CAS|
|reviewed_by|`varchar(255) COLLATE utf8mb4_0900_bin`|是|无|独立审核主体|
|review_reason|`longtext`|是|无|审核原因|
|created_at|`datetime(6)`|否|无|提交时间|
|reviewed_at|`datetime(6)`|是|无|审核时间|

主键：`id`。
- CHECK `ck_corpus_feedback_0`：`state IN ('PENDING','ACCEPTED','REJECTED','REVOKED')`。
- FK `fk_corpus_feedback_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_corpus_feedback_1`：`source_revision_id` → `transcript_revision(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_corpus_feedback_2`：`submitted_by` → `enterprise_identity(enterprise_id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_corpus_feedback_3`：`reviewed_by` → `enterprise_identity(enterprise_id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## feedback_segment

反馈关联稳定源片段。所属 E-01，计划 V024。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|feedback_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|反馈|
|revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|具体转写版本|
|segment_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|源段|

主键：`feedback_id,revision_id,segment_id`。
- FK `fk_feedback_segment_0`：`feedback_id` → `corpus_feedback(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_feedback_segment_1`：`revision_id,segment_id` → `transcript_segment(revision_id,segment_id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## corpus_revision

批准后可用语料修订；全局使用另有安全门。所属 E-01，计划 V024。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|feedback_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|已接受反馈|
|revision|`bigint unsigned`|否|无|语料版本|
|content_file_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|受控语料文件|
|state|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|APPROVED/REVOKED|
|purpose|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|允许用途配置键|
|approved_at|`datetime(6)`|否|无|批准时间|

主键：`id`。
- 唯一约束 `corpus_version(feedback_id,revision)`：语料版本。
- CHECK `ck_corpus_revision_0`：`state IN ('APPROVED','REVOKED')`。
- FK `fk_corpus_revision_0`：`feedback_id` → `corpus_feedback(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_corpus_revision_1`：`content_file_id` → `file_object(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## voiceprint_consent

明确用途授权及撤销记录。所属 E-02，计划 V025。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|subject_id|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|企业主体|
|purpose|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|已批准用途|
|consent_reference|`longtext`|否|无|受控授权凭据引用|
|state|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|GRANTED/REVOKED|
|revision|`bigint unsigned`|否|无|授权CAS|
|granted_at|`datetime(6)`|否|无|授权时间|
|revoked_at|`datetime(6)`|是|无|撤销时间|
|revoked_by|`varchar(255) COLLATE utf8mb4_0900_bin`|是|无|撤销操作者|
|revocation_reason|`longtext`|是|无|撤销原因；撤销时必填|

主键：`id`。
- CHECK `ck_voiceprint_consent_0`：`state IN ('GRANTED','REVOKED')`。
- CHECK `ck_voiceprint_consent_1`：`state <> 'REVOKED' OR (revoked_at IS NOT NULL AND revoked_by IS NOT NULL AND revocation_reason IS NOT NULL)`。
- FK `fk_voiceprint_consent_0`：`subject_id` → `enterprise_identity(enterprise_id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_voiceprint_consent_1`：`revoked_by` → `enterprise_identity(enterprise_id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## voiceprint_sample

声纹样本与审核状态；不写ACL。所属 E-02，计划 V025。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|consent_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|有效授权|
|sample_file_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|采样文件|
|model_version|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|特征模型版本|
|dimension|`bigint unsigned`|否|无|向量维数|
|encrypted_embedding_file_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|加密特征文件|
|key_reference|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|外部密钥引用，非密钥|
|state|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|候选/生效/拒绝/撤销|
|revision|`bigint unsigned`|否|无|CAS|
|reviewed_by|`varchar(255) COLLATE utf8mb4_0900_bin`|是|无|审核主体|
|reviewed_at|`datetime(6)`|是|无|审核时间|
|review_reason|`longtext`|是|无|批准/拒绝依据|
|revoked_at|`datetime(6)`|是|无|样本失效时间|

主键：`id`。
- 索引 `voice_consent(consent_id,state,id)`：撤销授权下全部样本。
- CHECK `ck_voiceprint_sample_0`：`dimension>0`。
- CHECK `ck_voiceprint_sample_1`：`state IN ('PENDING','ACTIVE','REJECTED','REVOKED')`。
- FK `fk_voiceprint_sample_0`：`consent_id` → `voiceprint_consent(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_voiceprint_sample_1`：`sample_file_id` → `file_object(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_voiceprint_sample_2`：`encrypted_embedding_file_id` → `file_object(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_voiceprint_sample_3`：`reviewed_by` → `enterprise_identity(enterprise_id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## evaluation_dataset

受控评测数据版本。所属 E-03，计划 V026。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|version_key|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|数据集唯一版本|
|manifest_file_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|样本及预期结果清单|
|purpose|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|允许用途|
|content_hash|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|数据集哈希|
|created_at|`datetime(6)`|否|无|创建时间|

主键：`id`。
- 唯一约束 `dataset_version(version_key)`：版本唯一。
- FK `fk_evaluation_dataset_0`：`manifest_file_id` → `file_object(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## configuration_revision

白名单配置候选；B期配置版本也可来自受控文件。所属 E-03，计划 V026。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|kind|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|MODEL/PROMPT/SYSTEM|
|revision|`bigint unsigned`|否|无|类别版本|
|configuration|`longtext`|否|无|schema校验JSON；无命令/任意URL/凭证|
|content_hash|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|配置哈希|
|state|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|候选审核状态|
|created_by|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|提出人|
|reviewed_by|`varchar(255) COLLATE utf8mb4_0900_bin`|是|无|独立审核人|
|created_at|`datetime(6)`|否|无|创建时间|
|reviewed_at|`datetime(6)`|是|无|审核时间|

主键：`id`。
- 唯一约束 `configuration_version(kind,revision)`：配置历史。
- CHECK `ck_configuration_revision_0`：`kind IN ('MODEL','PROMPT','SYSTEM')`。
- CHECK `ck_configuration_revision_1`：`state IN ('PENDING','APPROVED','REJECTED')`。
- CHECK `ck_configuration_revision_2`：`reviewed_by IS NULL OR reviewed_by <> created_by`。
- FK `fk_configuration_revision_0`：`created_by` → `enterprise_identity(enterprise_id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_configuration_revision_1`：`reviewed_by` → `enterprise_identity(enterprise_id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## evaluation_run

固定数据/模型/提示词评测任务。所属 E-03，计划 V026。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|dataset_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|固定数据集|
|model_config_version|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|模型配置固定版本|
|prompt_version|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|提示词固定版本|
|task_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|评测任务|
|state|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|执行状态|
|sample_count|`bigint unsigned`|否|无|实际样本数|
|metrics|`longtext`|是|无|指标JSON；仅真实测得值|
|errors_file_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|逐样本错误受控文件|
|created_at|`datetime(6)`|否|无|运行创建|

主键：`id`。
- CHECK `ck_evaluation_run_0`：`state IN ('PENDING','RUNNING','SUCCEEDED','FAILED')`。
- FK `fk_evaluation_run_0`：`dataset_id` → `evaluation_dataset(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_evaluation_run_1`：`task_id` → `durable_task(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_evaluation_run_2`：`errors_file_id` → `file_object(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## configuration_release

配置发布/回滚均新增记录。所属 E-03，计划 V026。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|kind|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|MODEL/PROMPT/SYSTEM|
|revision|`bigint unsigned`|否|无|发布CAS序列|
|configuration_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|已批准候选|
|evaluation_run_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|评测证据|
|rollback_of_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|若回滚指原发布|
|released_by|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|发布操作者|
|released_at|`datetime(6)`|否|无|发布时间|

主键：`id`。
- 唯一约束 `release_revision(kind,revision)`：发布单调唯一。
- CHECK `ck_configuration_release_0`：`kind IN ('MODEL','PROMPT','SYSTEM')`。
- FK `fk_configuration_release_0`：`configuration_id` → `configuration_revision(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_configuration_release_1`：`evaluation_run_id` → `evaluation_run(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_configuration_release_2`：`rollback_of_id` → `configuration_release(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_configuration_release_3`：`released_by` → `enterprise_identity(enterprise_id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## mail_job

已保存版本受控链接邮件；不含正文附件/外部邮箱。所属 E-04，计划 V027。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|会议|
|minutes_revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|已保存版本|
|confirmation_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|发布快照若有|
|sender_id|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|负责人|
|task_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|批次协调任务；每个收件人有独立发送操作|
|business_key|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|稳定消息查回键|
|state|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|发送/未知/失败|
|created_at|`datetime(6)`|否|无|受理时间|
|sent_at|`datetime(6)`|是|无|确定发送时间|

主键：`id`。
- 唯一约束 `mail_key(business_key)`：消息幂等。
- CHECK `ck_mail_job_0`：`state IN ('PENDING','SENT','FAILED','UNKNOWN','MANUAL_REQUIRED')`。
- FK `fk_mail_job_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_mail_job_1`：`minutes_revision_id` → `minutes_revision(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_mail_job_2`：`confirmation_id` → `final_confirmation(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_mail_job_3`：`sender_id` → `enterprise_identity(enterprise_id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_mail_job_4`：`task_id` → `durable_task(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理。

## mail_recipient

固定企业收件人集合；发送前再验证有效名单和ACL。所属 E-04，计划 V027。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|mail_job_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|邮件任务|
|enterprise_id|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|仍有查看权的确认企业参会人|
|state|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|该收件人的结果|
|provider_message_id|`longtext`|是|无|平台回执标识|
|operation_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|该收件人独立发送/查回操作|
|message_key|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|jobId + enterpriseId稳定消息键|

主键：`mail_job_id,enterprise_id`。
- 唯一约束 `mail_recipient_operation(operation_id)`：每外部发送只对应一个收件人。
- 唯一约束 `mail_recipient_key(message_key)`：单收件人消息幂等键。
- CHECK `ck_mail_recipient_0`：`state IN ('PENDING','SENT','FAILED','UNKNOWN','REVOKED')`。
- FK `fk_mail_recipient_0`：`mail_job_id` → `mail_job(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_mail_recipient_1`：`enterprise_id` → `enterprise_identity(enterprise_id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_mail_recipient_2`：`operation_id` → `external_operation(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：协调任务展开每收件人独立durable task/operation；发送前复验确认名单、有效READ和治理状态；部分成功只对未知收件人查回，已SENT永不再次发送；不是重新发送整批。

## meeting_search_projection

R1 列表当前名称/编号及筛选字段投影；与根修改同事务。所属 B-03/D-02，计划 V008。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|会议；公开复制/搜索编号就是完整meeting.id|
|info_revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|当前已保存基础信息；初始为空|
|roster_revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|当前确认名单；未确认为空|
|name_search|`longtext COLLATE utf8mb4_0900_as_ci`|是|无|当前名称；英文大小写不敏感，非正文|
|number_search|`varchar(36) COLLATE utf8mb4_0900_as_ci`|否|无|完整36字符meeting.id的搜索投影|
|category_id|`varchar(255) COLLATE utf8mb4_0900_bin`|是|无|当前分类；NULL映射显示其他|
|meeting_revision|`bigint unsigned`|否|无|投影对应根修订|

主键：`meeting_id`。
- 索引 `search_category(category_id,meeting_id)`：分类先筛再包含匹配。
- FK `fk_meeting_search_projection_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_meeting_search_projection_1`：`info_revision_id` → `meeting_info_revision(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_meeting_search_projection_2`：`roster_revision_id` → `roster_revision(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：服务端同事务维护；不是异步最终一致授权源。查询先根/ACL过滤，投影revision/指针须与根相符；正文/摘要不参加列表q匹配；根删除同步清理。

## meeting_person_search

R1 当前已确认名单姓名投影；草稿和AI候选绝不进入。所属 B-03/D-02，计划 V008。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|会议|
|roster_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|当前CONFIRMED名单|
|member_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|冻结名单项|
|name_search|`longtext COLLATE utf8mb4_0900_as_ci`|否|无|该已确认名单中的完整显示名|

主键：`meeting_id,member_id`。
- FK `fk_meeting_person_search_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_meeting_person_search_1`：`roster_id,member_id` → `roster_member(roster_id,member_id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：名单明确确认时与有效ACL同事务替换；包含确认的企业/外部业务人员显示名但不授予权限；目录改名不改冻结名单；查询按meeting_id EXISTS避免多人命中重复会议。

## minutes_template

R2 模板逻辑根与当前个人名称；历史名称不占用命名空间。所属 B-07/D-04，计划 V012。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|owner_id|`varchar(255) COLLATE utf8mb4_0900_bin`|是|无|个人所有者；内置模板NULL|
|name|`longtext`|否|无|当前完整展示名|
|name_normalized|`longtext COLLATE utf8mb4_0900_bin`|否|无|NFC及首尾Unicode空白剔除后的完整当前名称|
|name_hash|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|完整规范化名称SHA256；非唯一摘要桶|
|current_revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|当前template_revision；同模板应用完整性|
|revision|`bigint unsigned`|否|无|逻辑根CAS|
|created_at|`datetime(6)`|否|无|创建时间|

主键：`id`。
- 索引 `template_name_bucket(owner_id,name_hash,id)`：同个人当前名称候选；允许摘要碰撞但禁止完整同名。
- FK `fk_minutes_template_0`：`owner_id` → `enterprise_identity(enterprise_id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：个人创建/改名锁owner身份行串行；完整规范名二进制比较；内置由受控配置维护。改名产生新template_revision并CAS当前指针；旧revision名可读但不参与当前重名判断；不可跨所有者引用。

## external_execution

R4 稳定逻辑操作下的追加执行尝试；支持同确认零效果失败重试。所属 B-05/B-08，计划 V010。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|operation_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|稳定逻辑操作|
|execution_no|`bigint unsigned`|否|无|同操作单调1起代际|
|task_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|本次新任务唯一拥有者|
|task_attempt|`bigint unsigned`|是|无|before-call时实际领取attempt；排队时NULL|
|state|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|执行状态|
|request_hash|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|本次完整请求含目标位置的不可变SHA256|
|request_manifest|`longtext`|否|无|版本化安全请求清单：确认/目标ID/配置/固定输入引用；不含密钥|
|created_at|`datetime(6)`|否|无|入队/创建时间|
|marked_at|`datetime(6)`|是|无|本次实际before-call提交时间；PREPARED为NULL|
|finished_at|`datetime(6)`|是|无|确定终结时间|
|no_effect_basis|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|是|无|明确零效果证明类别；UNKNOWN/NOT_FOUND不可填写|
|no_effect_evidence|`longtext`|是|无|受控证明引用及必要事实；无凭证/正文|
|no_effect_verified_at|`datetime(6)`|是|无|证明核验时间|

主键：`id`。
- 唯一约束 `external_execution_number(operation_id,execution_no)`：一个操作每代一次。
- 唯一约束 `external_execution_task(task_id)`：一个外部调用任务只拥有一次执行。
- CHECK `ck_external_execution_0`：`execution_no > 0`。
- CHECK `ck_external_execution_1`：`state IN ('PREPARED','MARKED','UNKNOWN','SUCCEEDED','FAILED')`。
- CHECK `ck_external_execution_2`：`state <> 'MARKED' OR (task_attempt IS NOT NULL AND marked_at IS NOT NULL)`。
- CHECK `ck_external_execution_3`：`no_effect_basis IS NULL OR no_effect_basis IN ('NOT_SENT','DEFINITIVE_REJECTION','PROVEN_CUTOFF_FENCED')`。
- CHECK `ck_external_execution_4`：`(no_effect_basis IS NULL AND no_effect_evidence IS NULL AND no_effect_verified_at IS NULL) OR (state='FAILED' AND no_effect_basis IS NOT NULL AND no_effect_evidence IS NOT NULL AND no_effect_verified_at IS NOT NULL AND finished_at IS NOT NULL)`。
- FK `fk_external_execution_0`：`operation_id` → `external_operation(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_external_execution_1`：`task_id` → `durable_task(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：请求/任务/代际创建后不改；仅状态和证明由当前独立operation token推进，终结后冻结。新retry追加行，不删除旧行/改原task。跨执行旧token不能写当前结果；原task失败历史保留。确定零效果证明还必须排除旧发送者迟到重新发出。

## transcription_input

R5 转写导入/识别/重新增强不可变输入快照，独立于纪要generation_input。所属 B-06/C-02/C-03/D-01，计划 V011。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|会议|
|batch_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|原始有序主文件集合|
|kind|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|IMPORT/ASR/ENHANCE|
|source_revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|重新增强所据已保存转写；首次导入/ASR为空|
|material_snapshot_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|明确应用材料快照；B未启用或空集合为NULL|
|material_mode|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|EMPTY或PINNED，NULL不表示未知|
|processing_config_version|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|完整处理配置清单固定版本|
|parser_version|`varchar(255) COLLATE utf8mb4_0900_bin`|是|无|使用解析器时必填；纯ASR可空|
|model_config_version|`varchar(255) COLLATE utf8mb4_0900_bin`|是|无|模型调用固定配置；纯导入可空|
|prompt_version|`varchar(255) COLLATE utf8mb4_0900_bin`|是|无|使用提示词时固定版本；无提示词步骤可空|
|manifest_schema_version|`int unsigned`|否|无|可恢复输入清单schema版本|
|config_manifest|`longtext`|否|无|不可变规范化配置/步骤/来源引用清单；不含密钥或可变latest|
|input_fingerprint|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|规范化输入版本集合的 SHA-256|
|base_revision|`bigint unsigned`|否|无|受理时会议修订|
|cancel_epoch|`bigint unsigned`|否|无|受理取消代际|
|created_at|`datetime(6)`|否|无|与任务一同提交时间|

主键：`id`。
- 索引 `transcription_input_history(meeting_id,created_at,id)`：处理输入历史追溯。
- CHECK `ck_transcription_input_0`：`kind IN ('IMPORT','ASR','ENHANCE')`。
- CHECK `ck_transcription_input_1`：`material_mode IN ('EMPTY','PINNED')`。
- CHECK `ck_transcription_input_2`：`(material_mode='EMPTY' AND material_snapshot_id IS NULL) OR (material_mode='PINNED' AND material_snapshot_id IS NOT NULL)`。
- CHECK `ck_transcription_input_3`：`kind <> 'ENHANCE' OR source_revision_id IS NOT NULL`。
- CHECK `ck_transcription_input_4`：`kind <> 'IMPORT' OR parser_version IS NOT NULL`。
- CHECK `ck_transcription_input_5`：`kind='IMPORT' OR model_config_version IS NOT NULL`。
- CHECK `ck_transcription_input_6`：`manifest_schema_version > 0`。
- FK `fk_transcription_input_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_transcription_input_1`：`batch_id` → `input_batch(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_transcription_input_2`：`source_revision_id` → `transcript_revision(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_transcription_input_3`：`material_snapshot_id` → `material_snapshot(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：B只EMPTY/NULL且规范空集合入指纹；D01非空写入前V017加material FK，审阅库完整建FK。typed task input=TRANSCRIPTION_INPUT/id，输出revision反向引用本输入；手工保存/恢复继承原处理来源，不读meeting可变材料/配置。FK/source_revision同会议应用校验；历史删除先清源引用，禁止循环或子孙作为source。

## transcript_segment_mark

R6 片段多选标记随转写版本冻结；不是纪要要素。所属 B-06/C-03，计划 V011。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|已保存转写版本|
|segment_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|该版本稳定段|
|mark|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|KEY关键/CONCLUSION结论/DISPUTE争议/GENERATION_FOCUS重点生成|

主键：`revision_id,segment_id,mark`。
- CHECK `ck_transcript_segment_mark_0`：`mark IN ('KEY','CONCLUSION','DISPUTE','GENERATION_FOCUS')`。
- FK `fk_transcript_segment_mark_0`：`revision_id,segment_id` → `transcript_segment(revision_id,segment_id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：同段可0..4类，PK禁止重复；增删标记与片段保存产生新revision，旧标记不UPDATE/DELETE。规范排序的标记集进入确认content_hash/纪要inputFingerprint；确认后任何标记改动清当前转写/最终确认并要求重新确认，不证明内容真实；只作生成参考。

## sensitive_detection

未选择也保留的敏感检测目录；不属于七类主体要素。所属 B-07/D-03，计划 V012。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|set_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|所属不可变要素集|
|input_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|该检测原始生成输入；复制保留|
|category|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|两类敏感标签之一|
|ordinal|`bigint unsigned`|否|无|同类别1起展示顺序|

主键：`id`。
- 唯一约束 `detection_order(set_id,category,ordinal)`：同类条目顺序唯一。
- CHECK `ck_sensitive_detection_0`：`ordinal > 0`。
- CHECK `ck_sensitive_detection_1`：`category IN ('INTERNAL_BUSINESS_DATA','POLITICAL_SENSITIVE')`。
- FK `fk_sensitive_detection_0`：`set_id` → `element_set(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_sensitive_detection_1`：`input_id` → `generation_input(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：完整输出同事务插入；每项至少一个来源由服务校验。双标签同位置是不同检测；不同输出项不按相似文本/来源静默合并。摘录从有权不可变源读取，不保存模型伪摘录；历史/候选访问受拥有版本控制。

## sensitive_detection_source

敏感检测的完整无序来源集合。所属 B-07/D-03，计划 V012。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|detection_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|检测条目|
|locator_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|原始生成输入内精确来源|

主键：`detection_id,locator_id`。
- 索引 `detection_source_reverse(locator_id,detection_id)`：定位引用与删除检查。
- FK `fk_sensitive_detection_source_0`：`detection_id` → `sensitive_detection(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_sensitive_detection_source_1`：`locator_id` → `source_locator(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：同检测不重复来源；服务校验同会议且在检测input固定确认/材料集合中；与检测原子写入，零来源拒绝。先删此关系再清检测/来源；不授予源权限。

## background_note

生成背景补充正文；不冒充会议发言/决定/行动。所属 B-07/D-03，计划 V012。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|set_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|所属不可变要素集|
|input_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|本条原始生成输入；复制保留|
|ordinal|`bigint unsigned`|否|无|目录1起顺序|
|body|`longtext`|否|无|必要的生成背景正文；非原始模型JSON或源文副本|

主键：`id`。
- 唯一约束 `background_order(set_id,ordinal)`：背景目录顺序唯一。
- CHECK `ck_background_note_0`：`ordinal > 0`。
- FK `fk_background_note_0`：`set_id` → `element_set(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_background_note_1`：`input_id` → `generation_input(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：非空文本与至少一精确来源由验证器/事务保障；无新产品字数上限。新版本复制保留origin input；不得独自证明重要会议结论/行动，历史保留，删除先删来源关系。

## background_note_source

背景补充的完整来源集合。所属 B-07/D-03，计划 V012。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|note_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|背景条目|
|locator_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|该input内不可变来源|

主键：`note_id,locator_id`。
- 索引 `background_source_reverse(locator_id,note_id)`：来源引用与删除。
- FK `fk_background_note_source_0`：`note_id` → `background_note(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_background_note_source_1`：`locator_id` → `source_locator(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：PK为无序集合去重；同会议/固定输入成员校验，非空集合与正文原子提交。来源角色由固定图推导，不能因输出声称会议依据而改角色；读时独立源ACL。

## action_suggestion

未核实的模型负责人原文和日期建议；与接受字段分离。所属 B-07/D-03，计划 V012。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|element_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|确属ACTION的生成要素|
|input_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|这条建议原始生成输入；复制保留|
|owner_mention|`longtext`|是|无|原始负责人提议；不是企业ID/授权，非空值须非空白|
|due_on|`date`|是|无|结构有效但尚未核实的建议日期；未知NULL|

主键：`element_id`。
- FK `fk_action_suggestion_0`：`element_id` → `minutes_element(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_action_suggestion_1`：`input_id` → `generation_input(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：每个生成ACTION一行，可双NULL；源复用该element证据不伪造字段级依据。模型值不写入action_item接受字段；人工明确保存/复核形成新要素/action_item及新ACTION_FIELDS断言。外部负责人规则pending；人物选择不授予会议权限。

## result_review

人类对精确目标内容和完整证据集合的不可变断言。所属 B-07/D-03/B-08，计划 V012。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|所属会议|
|review_no|`bigint unsigned`|否|无|持meeting根锁分配的单调复核序号|
|review_kind|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|来源支持或行动字段确认|
|element_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|要素目标；与正文目标二选一|
|minutes_revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|正文目标；与要素二选一|
|target_anchor|`longtext`|是|无|SOURCE_SUPPORT精确范围的版本化规范anchor；ACTION_FIELDS为NULL|
|target_hash|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|含精确目标ID/版本/全文/anchor或完整行动元组的摘要|
|source_set_hash|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|完整证据/定位/来源角色与版本内容hash集合摘要；空集显式编码|
|hash_profile|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|可恢复不可变规范编码/域分离配置版本，不用latest|
|outcome|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|SUPPORTED/PARTIAL/UNSUPPORTED或CONFIRMED|
|reviewed_by|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|服务器验证的企业稳定身份；事件时值，不因目录删除丢历史|
|reviewed_at|`datetime(6)`|否|无|服务器UTC微秒复核时间|

主键：`id`。
- 唯一约束 `review_sequence(meeting_id,review_no)`：同会议复核顺序唯一。
- 索引 `review_element(element_id,review_kind,review_no)`：当前目标复核历史。
- 索引 `review_minutes(minutes_revision_id,review_kind,review_no)`：正文范围复核历史。
- CHECK `ck_result_review_0`：`review_no > 0`。
- CHECK `ck_result_review_1`：`(element_id IS NULL) <> (minutes_revision_id IS NULL)`。
- CHECK `ck_result_review_2`：`review_kind IN ('SOURCE_SUPPORT','ACTION_FIELDS')`。
- CHECK `ck_result_review_3`：`(review_kind='SOURCE_SUPPORT' AND target_anchor IS NOT NULL AND outcome IN ('SUPPORTED','PARTIAL','UNSUPPORTED')) OR (review_kind='ACTION_FIELDS' AND element_id IS NOT NULL AND target_anchor IS NULL AND outcome='CONFIRMED')`。
- CHECK `ck_result_review_4`：`REGEXP_LIKE(target_hash, '^[0-9a-f]{64}$', 'c')`。
- CHECK `ck_result_review_5`：`REGEXP_LIKE(source_set_hash, '^[0-9a-f]{64}$', 'c')`。
- FK `fk_result_review_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_result_review_1`：`element_id` → `minutes_element(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_result_review_2`：`minutes_revision_id` → `minutes_revision(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：仅明确内容级人工意图可写；active身份/owner及源ACL、完整来源/目标/hash、ACTION实际类型由拥有事务重验，非FK reviewed_by保留事件时身份。最新相同目标/范围/来源review_no决定断言，不能取任意历史SUPPORTED；根revision/fence前进、final指针清空。新target不继承旧review信用；内容级adopt+review可原子创建新actor/time/hash断言。最终确认只读冻结membership；不把人工断言当语义真值或权限。

## result_review_evidence

复核实际使用的完整证据集合，不只存支持项。所属 B-07/D-03/B-08，计划 V012。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|review_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|不可变复核|
|evidence_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|精确不可变目标的证据|

主键：`review_id,evidence_id`。
- 索引 `review_evidence_reverse(evidence_id,review_id)`：历史依赖与删除。
- FK `fk_result_review_evidence_0`：`review_id` → `result_review(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_result_review_evidence_1`：`evidence_id` → `evidence_link(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：集合必须等于目标范围相关全部证据；空集合显式hash但不得SOURCE_SUPPORT=SUPPORTED。FK仅证存在；同会议/同target/范围/来源版本/hash由事务重算。不可在复核提交后追加/删减；删除时先解除本关系。

## final_result_review

最终确认冻结精确复核成员，不读取以后最新复核。所属 B-08，计划 V013。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|confirmation_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|本次最终确认|
|review_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|本次选用的精确复核|

主键：`confirmation_id,review_id`。
- 索引 `final_review_reverse(review_id,confirmation_id)`：历史确认依赖。
- FK `fk_final_result_review_0`：`confirmation_id` → `final_confirmation(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_final_result_review_1`：`review_id` → `result_review(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：与V013 final_confirmation同时建表并立即有两个physical FK；不新增deferredFK。最终确认同事务验证同会议/精确minutes和element_set/当前适用复核，成员与完整复核投影进入source_manifest_hash；以后新断言不能改旧membership。先删除membership再清confirmation/review。

## source_context

不可变完整来源选择；生成与人工选择显式分型。所属 D-01/B-07，计划 UNASSIGNED-D01。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|内部 UUID；服务器生成，永不复用|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|所属会议|
|origin_kind|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|GENERATION_INPUT 或 MANUAL_SELECTION|
|generation_input_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|仅生成上下文的精确输入；人工为NULL|
|transcript_confirmation_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|冻结已确认转写|
|material_snapshot_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|是|无|冻结材料集合；NULL显式EMPTY|
|catalog_version|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|可恢复不可变CATALOG版本|
|catalog_sha256|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|归档CATALOG原始字节SHA256|
|created_at|`datetime(6)`|否|无|服务器UTC微秒创建时间|

主键：`id`。
- 唯一约束 `context_generation(generation_input_id)`：每个生成输入至多一个上下文；人工NULL可多行。
- 索引 `context_meeting(meeting_id,id)`：会议完整历史枚举。
- CHECK `ck_source_context_0`：`origin_kind IN ('GENERATION_INPUT','MANUAL_SELECTION')`。
- CHECK `ck_source_context_1`：`(origin_kind='GENERATION_INPUT' AND generation_input_id IS NOT NULL) OR (origin_kind='MANUAL_SELECTION' AND generation_input_id IS NULL)`。
- CHECK `ck_source_context_2`：`REGEXP_LIKE(catalog_sha256, '^[0-9a-f]{64}$', 'c')`。
- FK `fk_source_context_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_source_context_1`：`generation_input_id` → `generation_input(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_source_context_2`：`transcript_confirmation_id` → `transcript_confirmation(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_source_context_3`：`material_snapshot_id` → `material_snapshot(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：完整不可变来源快照；服务验证同会议/确认状态/完整材料/精确配置依赖和权限。生成input与context内容一致；人工不伪造生成。持根锁与使用者及文件账本原子提交；历史引用RESTRICT，无自动TTL。

## source_context_material

上下文内每个材料版本唯一明确选择解析文档。所属 D-01/B-07，计划 UNASSIGNED-D01。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|context_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|不可变来源上下文|
|material_revision_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|完整快照材料版本|
|parsed_document_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|该材料的精确解析输出；不取latest|

主键：`context_id,material_revision_id`。
- 索引 `selection_document(parsed_document_id,context_id)`：旧解析输出仅被复制证据引用仍受保护。
- 索引 `selection_material(material_revision_id,context_id)`：材料历史依赖。
- FK `fk_source_context_material_0`：`context_id` → `source_context(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_source_context_material_1`：`material_revision_id` → `material_revision(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。
- FK `fk_source_context_material_2`：`parsed_document_id` → `parsed_document(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：服务验证与快照items双射、document确属revision、READY/文本完整和唯一profile；FK只证存在。完整集合原子INSERT后不可增删；全图解除后才可清理。

## parsed_document_profile

解析文档的明确格式及不可变解析配置归档引用。所属 D-01，计划 UNASSIGNED-D01。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|document_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|精确解析文档|
|format|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|PDF/PPT/WORD/TXT；不能从unit种类或文件名猜测|
|parser_config_version|`varchar(255) COLLATE utf8mb4_0900_bin`|否|无|可恢复归档PARSER_CONFIG版本|
|parser_config_sha256|`char(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|解析配置归档原始字节SHA256|

主键：`document_id`。
- 索引 `profile_parser(parser_config_version,document_id)`：配置历史依赖枚举。
- CHECK `ck_parsed_document_profile_0`：`format IN ('PDF','PPT','WORD','TXT')`。
- CHECK `ck_parsed_document_profile_1`：`REGEXP_LIKE(parser_config_sha256, '^[0-9a-f]{64}$', 'c')`。
- FK `fk_parsed_document_profile_0`：`document_id` → `parsed_document(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：与实际解析输出原子创建；服务验证parser_version等于归档version、格式和映射匹配；缺少sidecar为legacy unavailable，禁止推测/补造READY。配置归档保留由领域负责，非跨库FK。

## input_batch_fingerprint_profile

明确不可变输入批次指纹算法；缺失为legacy/unknown。所属 B-06/runtime Task2，计划 V013.1。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|batch_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|原子新建批次的唯一父；禁止历史推断或补填|
|profile|`varchar(64) CHARACTER SET ascii COLLATE ascii_bin`|否|无|服务器真实选择的固定算法 ama.input-batch.v1；不可归一化|

主键：`batch_id`。
- CHECK `ck_input_batch_fingerprint_profile_0`：`OCTET_LENGTH(profile) = 18 AND profile = 'ama.input-batch.v1'`。
- FK `fk_input_batch_fingerprint_profile_0`：`batch_id` → `input_batch(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：与真实新批次及成员/输入/任务/审计原子创建；不可变，无默认与历史回填。缺行明确unsupported；无推断/懒填/自动TTL。授权永久父清理时先解除保留来源再删除子行；FK仅证存在，不证明算法或授权。

## meeting_minutes_editor_guard

持久单调编辑意图；仅新建会议原事务可写零。所属 B-03/B-07/runtime Task2，计划 V013.1。

|字段|MySQL 类型/排序规则|可空|默认|含义|
|---|---|---|---|---|
|meeting_id|`char(36) CHARACTER SET ascii COLLATE ascii_bin`|否|无|唯一会议父；历史缺行是UNKNOWN，重放/读/生成不得初始化|
|intent_epoch|`bigint`|否|无|服务器单调非负epoch；0仅证明新会未编辑，正数永久阻止首次自动保存|

主键：`meeting_id`。
- CHECK `ck_meeting_minutes_editor_guard_0`：`intent_epoch BETWEEN 0 AND 9223372036854775806`。
- FK `fk_meeting_minutes_editor_guard_0`：`meeting_id` → `meeting(id)`。同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用。

更新/删除与完整性：新建会议原事务插入0；真正授权编辑意图在根锁下由未知写1或严格递增，无减少/重置/超时/删除。上限前条件增量且一行，否则关闭失败；无默认。创建重放和旧行读取不补零；缺TABLE为schema unavailable。逻辑删除保留，只有永久删除父时协调清理；epoch不授予权限。
