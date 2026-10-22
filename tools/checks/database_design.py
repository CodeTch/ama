"""Review-only schema source and disposable MySQL metadata verifier. No Flyway writes.

python3 tools/checks/database_design.py generate
python3 tools/checks/database_design.py verify --container <NEW owned fixture>
"""
import argparse
import hashlib
import json
import pathlib
import re
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[2]
VERSION = 'B00-1.4-proposed'
PROVENANCE_ROOT_RULE = 'R1上下文根：所有纳入minutes_element.source_context_id无论证据数量均为basis根，与域内精确usage上下文取并集。ROWSET v2/PINNED用已保存输入要素集全部basis及set证据/目录，排除body/applied exclusion；REVIEW_SOURCES仅选定复核要素basis及精确相交证据（正文复核无要素basis）；FINAL用全部冻结纳入要素basis及既有body/set证据、目录、applied exclusion。各根完整context/selection/profile按实际完整PK去重及unsigned UTF8排序，缺失/额外行拒绝；无usage的basis同样验证并保留依赖。basis不伪造EVIDENCE、不增加usage数、不授予支持信用；草稿仍无自身fingerprint/TaskSpec，祖先按图依赖判断。'
TABLES = []
ID = 'char(36) CHARACTER SET ascii COLLATE ascii_bin'
KEY = 'varchar(255) COLLATE utf8mb4_0900_bin'
CODE = 'varchar(64) CHARACTER SET ascii COLLATE ascii_bin'
HASH = 'char(64) CHARACTER SET ascii COLLATE ascii_bin'

def col(name, typ, meaning, nullable=False, default=None):
    return dict(name=name, type=typ, meaning=meaning, nullable=nullable, default=default)
def ident(name='id', meaning='内部 UUID；服务器生成，永不复用', nullable=False):
    return col(name, ID, meaning, nullable)
def ref(name, meaning, nullable=False):
    return ident(name, meaning, nullable)
def text(name, meaning, nullable=False):
    return col(name, 'longtext', meaning, nullable)
def num(name, meaning, default=None, nullable=False):
    return col(name, 'bigint unsigned', meaning, nullable, default)
def ts(name, meaning, nullable=False):
    return col(name, 'datetime(6)', meaning, nullable)
def code(name, meaning, nullable=False, default=None):
    return col(name, CODE, meaning, nullable, default)
def fingerprint(name='input_fingerprint', meaning='规范化输入版本集合的 SHA-256'):
    return col(name, HASH, meaning)
def fk(cols, target, remote='id', note='同一事务校验同会议/同所有者；删除 RESTRICT，先显式解除子引用'):
    return dict(cols=cols.split(','), target=target, remote=remote.split(','), note=note)
def idx(name, cols, reason, unique=False):
    return dict(name=name, cols=cols.split(','), reason=reason, unique=unique)
def enum(name, vals):
    return name + ' IN (' + ','.join("'"+x+"'" for x in vals.split(',')) + ')'
def table(name, task, migration, purpose, cols, pk='id', indexes=(), fks=(), checks=(), policy='版本/快照 INSERT 后不可改写；可变状态仅由所属领域服务 CAS；历史无自动过期；会议删除按依赖逆序显式清理'):
    TABLES.append(dict(name=name, task=task, migration=migration, purpose=purpose, cols=cols, pk=pk.split(','), indexes=list(indexes), fks=list(fks), checks=list(checks), policy=policy))

table('audit_event','B-01','V006','最小事务审计（V001 的目标增量形态）',[
    col('id','bigint unsigned','审计自增编号；不能作提交顺序游标'), col('actor_id',KEY,'操作者稳定 ID 或受控系统主体'),code('action','动作码'),col('resource_id',KEY,'资源稳定标识'),num('revision','业务修订'),code('result','结果码'),col('trace_id',KEY,'安全关联标识'),ts('occurred_at','UTC 微秒事件时间')],indexes=[idx('audit_resource_time','resource_id,occurred_at,id','资源审计时间线'),idx('audit_trace','trace_id','安全追踪')],policy='append-only；无正文、令牌或密钥；逻辑主体关联刻意无 FK 以允许正文删除；保留策略由 INT-09/PR-13 决定，不自动清除')
table('department','B-02','V007','目录部门投影',[
    col('enterprise_id',KEY,'公司部门稳定 ID'),text('display_name','展示名'),col('parent_id',KEY,'父部门 ID；根为空',True),ts('verified_at','最后权威核验')],pk='enterprise_id',fks=[fk('parent_id','department','enterprise_id','目录适配器校验无环；删除 RESTRICT，停用代替物理删除')])
table('enterprise_identity','B-02','V007','企业身份（架构 user_identity）',[
    col('enterprise_id',KEY,'公司稳定 ID，大小写和尾空格精确比较'),text('display_name','展示名'),col('department_id',KEY,'目录部门',True),code('status','账户状态'),num('session_generation','撤销代际；停用/全退出时递增',0),ts('verified_at','权威状态核验')],pk='enterprise_id',fks=[fk('department_id','department','enterprise_id')],checks=[enum('status','ACTIVE,DISABLED')],policy='目录适配器维护；停用原子递增 session_generation；重新启用不复活旧会话；不从姓名推导 ID')
table('role_assignment','B-02','V007','独立平台角色；不隐含正文访问',[
    col('enterprise_id',KEY,'角色主体'),code('role','平台角色'),ts('assigned_at','分配时间')],pk='enterprise_id,role',fks=[fk('enterprise_id','enterprise_identity','enterprise_id')],checks=[enum('role','USER,PLATFORM_ADMIN')])
table('identity_session','B-02','V007','共享数据库会话与即时撤销',[
    col('token_hash',HASH,'256-bit opaque token 的 SHA-256；不存 token'),col('enterprise_id',KEY,'会话主体'),num('session_generation','签发时主体代际'),col('csrf_token','char(43) CHARACTER SET ascii COLLATE ascii_bin','独立随机会话 CSRF 值；不进入日志'),ts('issued_at','签发时间'),ts('expires_at','显式平台策略有效期'),ts('revoked_at','撤销时间',True)],pk='token_hash',indexes=[idx('session_identity','enterprise_id,expires_at','全会话撤销与过期诊断')],fks=[fk('enterprise_id','enterprise_identity','enterprise_id')],checks=['expires_at > issued_at'],policy='每请求共享 DB 查 ACTIVE、代际相同、未撤销、未过期；不缓存节点授权；无公司协议时禁止生产签发；过期会话清理另配策略')
table('meeting','B-03','V008','会议聚合根，唯一负责人和生命周期',[
    ident(),col('owner_id',KEY,'唯一企业负责人'),num('revision','全聚合 CAS 修订',0),num('cancel_epoch','删除/取消栅栏',0),num('access_revision','流读取撤权栅栏',0),code('stage','列表阶段'),code('lifecycle','独立删除生命周期',default="'ACTIVE'"),code('governance_state','独立治理状态',default="'ACTIVE'"),col('ever_published','tinyint unsigned','成功发布后不可清零',False,'0'),ref('info_revision_id','当前信息版本',True),ref('roster_revision_id','当前确认名单',True),ref('transcript_confirmation_id','当前确认转写',True),ref('minutes_revision_id','当前有效已保存正文',True),ref('final_confirmation_id','当前有效最终确认',True),col('actual_meeting_date','date','上海实际会议日；未知 NULL',True),ts('created_at','采集创建 UTC'),ts('updated_at','最后修改 UTC')],indexes=[idx('meeting_owner_date','owner_id,lifecycle,actual_meeting_date,id','负责会议日期分页'),idx('meeting_stage_date','lifecycle,stage,actual_meeting_date,id','授权后阶段统计/日期分页')],fks=[fk('owner_id','enterprise_identity','enterprise_id')],checks=[enum('stage','ENTRY,TRANSCRIPT_PENDING,TRANSCRIPT_FAILED,MINUTES_PENDING,MINUTES_FAILED,READY_TO_PUBLISH,PUBLISH_FAILED,PUBLISHED'),enum('lifecycle','ACTIVE,DELETING,DELETED'),enum('governance_state','ACTIVE,WITHDRAW_PENDING,WITHDRAWN,OBSOLETE_PENDING,OBSOLETE'), 'ever_published IN (0,1)'],policy='root 行受 subject fence 保护；当前指针由本领域在锁内校验目标同会议，因循环依赖使用应用完整性；物理根保留无正文 tombstone 防迟到复活')
table('meeting_info_revision','B-03','V008','不可变基础信息历史',[
    ident(),ref('meeting_id','会议'),num('revision','会议修订'),text('name','名称；不加产品字数限制',True),col('category_id',KEY,'公司分类稳定键；真实字典未到不伪造',True),text('summary','摘要',True),col('actual_meeting_date','date','可选真实上海会议日',True),col('created_by',KEY,'修改人'),ts('created_at','保存时间')],indexes=[idx('info_revision','meeting_id,revision','版本历史与唯一修订',True)],fks=[fk('meeting_id','meeting'),fk('created_by','enterprise_identity','enterprise_id')])
table('roster_revision','B-03','V008','名单草稿与已确认快照头；确认冻结该修订',[
    ident(),ref('meeting_id','会议'),num('revision','名单修订'),code('state','DRAFT 可编辑；CONFIRMED 永久冻结'),ts('confirmed_at','确认时间',True),col('confirmed_by',KEY,'确认者',True)],indexes=[idx('roster_revision','meeting_id,revision','名单历史',True)],fks=[fk('meeting_id','meeting'),fk('confirmed_by','enterprise_identity','enterprise_id')],checks=[enum('state','DRAFT,CONFIRMED'),"(state='DRAFT' AND confirmed_at IS NULL AND confirmed_by IS NULL) OR (state='CONFIRMED' AND confirmed_at IS NOT NULL AND confirmed_by IS NOT NULL)"])
table('roster_member','B-03','V008','名单内容；本表绝不被授权查询使用',[
    ref('roster_id','名单版本'),ident('member_id','名单项 ID'),col('enterprise_id',KEY,'已选企业身份；外部人物为空',True),text('display_name','该版本显示名称'),code('member_kind','ENTERPRISE 或 EXTERNAL')],pk='roster_id,member_id',indexes=[idx('roster_person','roster_id,enterprise_id','企业成员不重复；外部 NULL 可多条',True)],fks=[fk('roster_id','roster_revision'),fk('enterprise_id','enterprise_identity','enterprise_id')],checks=[enum('member_kind','ENTERPRISE,EXTERNAL'),"(member_kind='ENTERPRISE' AND enterprise_id IS NOT NULL) OR (member_kind='EXTERNAL' AND enterprise_id IS NULL)"])
table('meeting_access_grant','B-02/B-03','V008','独立有效 ACL；替代 V002 meeting_acl 读路径',[
    ref('meeting_id','会议'),col('enterprise_id',KEY,'已确认企业成员'),code('action','仅显式 READ；编辑/管理由负责人身份判定'),ref('roster_id','授权来自确认名单'),ts('confirmed_at','非空有效确认时间')],pk='meeting_id,enterprise_id,action',indexes=[idx('access_person_meeting','enterprise_id,action,meeting_id','先权限过滤再列表')],fks=[fk('meeting_id','meeting'),fk('enterprise_id','enterprise_identity','enterprise_id'),fk('roster_id','roster_revision')],checks=["action='READ'"],policy='确认名单事务差量重建；仅 CONFIRMED 同会议名单中 ACTIVE 企业成员可插入（服务检查）；撤权 DELETE 此有效 grant 同事务递增 access_revision 与审计；历史由 roster/audit 保留；旧 meeting_acl 禁止继续授权')
table('idempotency_record','B-03','V008','副作用请求重放/同键异请求冲突',[
    col('scope_hash',HASH,'SHA256(actor,resource,operation,key) 长度前缀编码作用域'),col('actor_id',KEY,'调用者'),col('resource_id',KEY,'资源或创建命名空间'),code('operation','操作类型'),col('key_hash',HASH,'请求幂等键摘要'),fingerprint('request_hash','规范化完整请求摘要'),code('state','事务完成结果'),ref('result_id','安全结果对象 ID',True),col('http_status','smallint unsigned','响应状态'),ts('created_at','受理时间')],pk='scope_hash',checks=[enum('state','SUCCEEDED,REJECTED')],policy='先插入唯一作用域行再锁资源；并发唯一冲突等待后比较 request_hash；同键异请求409；事务失败不留成功；外部异步返回任务ID；重放仍重验权限，不保存正文响应；无任意TTL清除')
table('info_suggestion','D-02','V018','AI 信息建议的独立候选',[
    ident(),ref('meeting_id','会议'),num('base_revision','提案基线'),fingerprint(),text('proposed_info','版本化 JSON 文本，由服务 schema 校验'),code('state','候选状态')],fks=[fk('meeting_id','meeting')],checks=[enum('state','PENDING,ADOPTED,REJECTED,STALE')])

table('storage_backend','B-04','V009','不可变后端路由注册；凭证在外部配置',[
    col('id',CODE,'稳定配置后端 ID'),code('storage_type','local/s3'),code('status','可写/历史只读/停用'),num('config_revision','配置引用版本')],checks=[enum('storage_type','local,s3'),enum('status','WRITABLE,READ_ONLY,DISABLED')],policy='ID 不复用且类型不可改变；禁用前核验历史/在途引用；不保存 Bucket 密钥、真实路径或临时 URL')
table('file_object','B-04','V009','已持久化受管文件元数据',[
    ident(),ref('meeting_id','所属会议；治理文件为空',True),col('backend_id',CODE,'固定后端'),code('storage_type','冗余路由类型需与 backend 一致'),col('namespace',CODE,'逻辑命名空间'),col('object_key','varchar(512) CHARACTER SET ascii COLLATE ascii_bin','服务生成不可变键，不是用户路径'),text('provider_version_id','供应商版本；local 空',True),text('display_name','展示文件名'),num('size_bytes','实存字节'),fingerprint('sha256','完整内容 SHA256；不是 ETag'),num('location_revision','迁移位置 CAS',0),code('state','可用/待删/已删'),num('reference_epoch','引用增删/位置迁移栅栏',0),ts('created_at','落盘后登记时间')],indexes=[idx('file_location','backend_id,namespace,object_key','不可变逻辑位置唯一',True),idx('file_meeting','meeting_id,state,id','归属文件枚举'),idx('file_cleanup','state,created_at,id','可回收候选')],fks=[fk('meeting_id','meeting'),fk('backend_id','storage_backend')],checks=[enum('storage_type','local,s3'),enum('state','AVAILABLE,DELETE_PENDING,DELETED')],policy='文件及目录持久化先于 DB 登记和业务成功；引用注册、删除认领、位置更新均锁文件；不允许引用 DELETE_PENDING/DELETED；物理删除完成才 DELETED')
table('file_reference','B-04','V009','所有受管文件使用的统一引用账本',[
    ref('file_id','文件'),code('owner_kind','白名单领域类型'),ident('owner_id','具体引用行ID'),code('purpose','原始/解析/导出/采样等用途')],pk='file_id,owner_kind,owner_id,purpose',indexes=[idx('file_ref_owner','owner_kind,owner_id,file_id','删除所属业务引用')],fks=[fk('file_id','file_object')],policy='typed owner 由领域注册器校验存在及同会议，更新正文关联与本行必须同事务；禁止仅凭 refcount 判断；清理锁 file 后 NOT EXISTS 复验；历史版本引用也登记')
table('upload_session','B-04','V009','固定后端的断点续传会话',[
    ident(),ref('meeting_id','会议'),col('backend_id',CODE,'创建时固定'),col('namespace',CODE,'逻辑空间'),col('object_key','varchar(512) CHARACTER SET ascii COLLATE ascii_bin','服务生成目标键'),num('cancel_epoch','创建时会议取消代际'),num('expected_size','期望大小'),col('expected_sha256',HASH,'可选预声明校验',True),text('provider_upload_token','仅适配器可读供应商上传标识',True),code('state','会话状态'),ref('completed_file_id','完成文件；重复完成返回同 ID',True),ts('created_at','创建时间'),ts('updated_at','最近活动')],indexes=[idx('upload_meeting','meeting_id,state,id','删除与在途枚举'),idx('upload_target','backend_id,namespace,object_key','上传目标唯一',True)],fks=[fk('meeting_id','meeting'),fk('backend_id','storage_backend'),fk('completed_file_id','file_object')],checks=[enum('state','OPEN,FINALIZING,COMPLETED,ABORTED,FAILED')])
table('upload_part','B-04','V009','已落盘上传分片回执',[
    ref('upload_id','会话'),num('part_no','1起分片号'),num('size_bytes','分片大小'),fingerprint('sha256','分片哈希'),text('provider_token','供应商回执；非正文/密钥',True)],pk='upload_id,part_no',fks=[fk('upload_id','upload_session')],checks=['part_no > 0'])
table('file_cleanup','B-04/D-07','V009','可重试删除/孤儿清理登记',[
    ident(),ref('file_id','已登记文件；孤儿为空',True),col('backend_id',CODE,'待删位置固定后端'),col('namespace',CODE,'受控命名空间'),col('object_key','varchar(512) CHARACTER SET ascii COLLATE ascii_bin','受限枚举得到的逻辑键'),num('expected_location_revision','已登记文件的位置栅栏',nullable=True),num('expected_reference_epoch','引用栅栏',nullable=True),code('state','清理状态'),ts('not_before','配置恢复窗口后的可清理时间'),num('attempt','尝试次数',0),code('last_error','安全错误码',True)],indexes=[idx('cleanup_due','state,not_before,id','清理候选分页'),idx('cleanup_target','backend_id,namespace,object_key','位置仅一清理项',True)],fks=[fk('file_id','file_object'),fk('backend_id','storage_backend')],checks=[enum('state','PENDING,RUNNING,FAILED,COMPLETE')],policy='孤儿不允许业务读取；清理前检查 upload/recording 进行中以及所有 file_reference；未配置恢复窗口不清理技术孤儿；不能自动清理历史成果')

table('task_subject','B-05','V010','每领域聚合的版本/取消 fence（会议仅一个共享根）',[
    code('subject_kind','MEETING/治理根等白名单类型'),col('subject_id',KEY,'聚合稳定ID'),num('business_version','与根 revision 同事务前进'),num('cancel_epoch','取消/删除代际'),col('deleted','tinyint unsigned','不可复活 tombstone',False,'0')],pk='subject_kind,subject_id',checks=['deleted IN (0,1)'],policy='领域创建根时创建 fence；enqueue 不得懒建或降低栅栏；typed root 应用完整性；所有写路径先锁此行再锁根')
table('durable_task','B-05','V010','持久任务；外部未知结果从普通领取隔离',[
    ident(),code('task_type','处理类型'),code('pool','隔离限并发池'),code('subject_kind','聚合类型'),col('subject_id',KEY,'聚合ID'),col('producer_key',HASH,'稳定 enqueue 业务键摘要'),code('input_kind','输入快照类型'),ident('input_id','不可变输入快照ID'),fingerprint(),num('business_version','入队版本'),num('cancel_epoch','入队取消代际'),col('priority','int','有界队列优先级'),code('state','任务状态',default="'READY'"),num('attempt','每次领取递增',0),num('max_attempts','显式策略上限；含崩溃回收'),col('lease_owner',CODE,'当前领取者',True),ts('expires_at','DB租约截止',True),ts('available_at','下次允许领取'),ts('created_at','入队时间'),code('output_kind','输出类型',True),ident('output_id','输出ID',True),code('last_error','安全失败码',True)],indexes=[idx('task_producer','producer_key','同一业务入队重放唯一',True),idx('task_ready','pool,state,available_at,id','分开扫描 READY'),idx('task_expired','pool,state,expires_at,id','分开扫描过期 RUNNING'),idx('task_subject_history','subject_kind,subject_id,created_at,id','聚合任务历史')],fks=[fk('subject_kind,subject_id','task_subject','subject_kind,subject_id')],checks=[enum('state','READY,RUNNING,RECONCILING,SUCCEEDED,FAILED,CANCELLED,STALE'),'max_attempts > 0',"state <> 'RUNNING' OR (lease_owner IS NOT NULL AND expires_at IS NOT NULL)"],policy='input/output typed refs 由任务注册器校验具体领域存在且相同 subject/版本；producer_key 冲突需比较完整输入，否则冲突；失败/旧结果保留最小诊断不覆盖有效稿')
table('task_attempt','B-05','V010','每次 Worker 领取诊断历史',[
    ref('task_id','任务'),num('attempt','领取代际'),code('owner','Worker身份'),ts('started_at','开始'),ts('ended_at','结束',True),code('outcome','结束结果',True),code('error_code','安全错误码',True)],pk='task_id,attempt',fks=[fk('task_id','durable_task')])
table('external_operation','B-05','V010','任何可能产生外部效果的调用前 durable marker 及独立对账所有权',[
    ident(),ref('task_id','唯一拥有任务'),code('operation_kind','MODEL_GENERATE/PUBLISH/WITHDRAW/MAIL等'),col('business_key',KEY,'稳定外部幂等/查回键'),fingerprint('request_hash','不可变外部请求摘要'),num('origin_attempt','发起调用的任务attempt'),code('state','调用/对账状态'),ts('marked_at','提交marker时间；先于网络调用'),ts('next_check_at','下次对账'),num('reconcile_epoch','独立对账租约代际',0),code('reconcile_owner','对账者',True),ts('reconcile_expires_at','对账租约DB截止',True),text('external_result_id','外部结果定位',True),code('last_error','安全错误码',True)],indexes=[idx('external_task','task_id,operation_kind','每任务每效果类型单一操作',True),idx('external_key','operation_kind,business_key','同类型业务键唯一',True),idx('external_due','state,next_check_at,id','到期对账')],fks=[fk('task_id','durable_task')],checks=[enum('state','MARKED,UNKNOWN,SUCCEEDED,FAILED,MANUAL_REQUIRED')],policy='MARKED 与任务 RECONCILING 在调用前原子提交；不把 MARKED 当尚未调用；对账只查询/幂等协议重放；没有可靠协议转 MANUAL_REQUIRED 阻断；删除/版本失效仍可登记外部真实结果但禁止写回业务正文')
table('task_outbox_sequence','B-05','V010','提交序列锁，避免 AUTO_INCREMENT 先提交高号跳漏',[
    col('id','int','固定1'),num('next_id','下一事件号',1)],checks=['id = 1','next_id > 0'],policy='所有业务锁及审计后最后锁定 id=1；分配/插入事件后立即提交，禁止再锁其他领域行')
table('task_outbox','B-05','V010','持久引用事件（架构 outbox_event）',[
    num('sequence_id','由事务序列分配的提交顺序'),code('event_type','事件类型'),ref('task_id','可空任务引用',True),code('subject_kind','订阅/鉴权聚合类型'),col('subject_id',KEY,'聚合ID'),num('subject_revision','事件业务版本'),code('reference_kind','事件引用类型'),ident('reference_id','不含正文的引用ID'),ts('occurred_at','UTC事件时间')],pk='sequence_id',indexes=[idx('outbox_subject','subject_kind,subject_id,sequence_id','授权订阅增量')],fks=[fk('task_id','durable_task')],policy='typed subject/reference 注册器检查；事件不直接携正文；流读取重新鉴权；不按TTL删历史游标，归档后需要明确reset协议')
table('outbox_consumer','B-05','V010','持久消费游标及接管 fence',[
    code('consumer_id','稳定消费组'),num('cursor','已完成连续前缀',0),num('epoch','消费者领取代际',0),code('lease_owner','消费者',True),ts('expires_at','DB租约截止',True)],pk='consumer_id',policy='游标只能前进，重放可重复；本地效果与游标同事务；外部消费者以事件序号去重且成功ACK后才推进；拒绝越过失败事件')
table('model_capacity','B-05','V010','已审核模型在途容量；无默认公司额度',[
    code('model','模型配置稳定键'),col('capacity','int unsigned','明确容量'),num('config_revision','权威配置版本'),col('enabled','tinyint unsigned','允许新调用',False,'0'),code('cutoff_policy','平台有证据截止或必须人工终结')],pk='model',checks=['capacity > 0','enabled IN (0,1)',enum('cutoff_policy','PROVEN_DEADLINE,EXPLICIT_SETTLEMENT')])
table('model_reservation','B-05','V010','独立于短任务租约的模型额度保留',[
    ident(),code('model','模型'),ref('operation_id','外部操作唯一关联'),num('config_revision','准入配置版本'),ts('safe_deadline','平台证明截止；不支持时NULL',True),ts('created_at','准入时间'),ts('settled_at','明确终结才释放',True)],indexes=[idx('reservation_operation','operation_id','调用唯一占额',True),idx('model_deadline','model,settled_at,safe_deadline','计数活跃及到期回收')],fks=[fk('model','model_capacity','model'),fk('operation_id','external_operation')],policy='单独准入事务锁 model；持有许可后才调用。lease/HTTP超时不得释放；未知deadline允许无限占额且告警，默认未配置容量拒绝调用；缩容低于活跃数拒绝，禁用只阻断新准入')

table('input_batch','B-06/C-01','V011','处理主文件集合快照，禁止混合音视频与纯转写',[
    ident(),ref('meeting_id','会议'),code('kind','TRANSCRIPT 或 MEDIA'),fingerprint(),num('base_revision','启动时修订'),ts('created_at','冻结时间')],fks=[fk('meeting_id','meeting')],checks=[enum('kind','TRANSCRIPT,MEDIA')])
table('input_batch_file','B-06/C-01','V011','不可变有序源文件清单',[
    ref('batch_id','输入集合'),num('ordinal','从1顺序'),ref('file_id','已校验主文件'),fingerprint('sha256','冻结内容校验')],pk='batch_id,ordinal',indexes=[idx('batch_file_unique','batch_id,file_id','同集合文件不重复',True)],fks=[fk('batch_id','input_batch'),fk('file_id','file_object')],checks=['ordinal > 0'])
table('transcript_revision','B-06','V011','原始/增强/人工/候选转写不可变版本；确认另表',[
    ident(),ref('meeting_id','会议'),num('revision','单调转写版本'),code('kind','版本来源'),ref('batch_id','输入集合'),ref('parent_id','上个稿',True),fingerprint(),col('created_by',KEY,'创建者'),ts('created_at','保存时间')],indexes=[idx('transcript_history','meeting_id,revision','唯一版本与历史',True)],fks=[fk('meeting_id','meeting'),fk('batch_id','input_batch'),fk('parent_id','transcript_revision')],checks=[enum('kind','RAW,ENHANCED,WORKING,CANDIDATE')])
table('transcript_segment','B-06','V011','稳定片段ID跨修订保留；纯文档无虚构音频时间',[
    ref('revision_id','具体转写版本'),ident('segment_id','跨版本稳定段ID'),num('ordinal','版本内排序'),text('body','已保存片段正文'),ref('source_file_id','源文件'),num('source_paragraph','纯文本段序',nullable=True),num('start_ms','音频起点ms',nullable=True),num('end_ms','音频终点ms',nullable=True),ref('speaker_id','说话人业务身份；不授权',True)],pk='revision_id,segment_id',indexes=[idx('segment_order','revision_id,ordinal','完整稿顺序',True)],fks=[fk('revision_id','transcript_revision'),fk('source_file_id','file_object')],checks=['(start_ms IS NULL AND end_ms IS NULL) OR (start_ms IS NOT NULL AND end_ms IS NOT NULL AND end_ms >= start_ms)'])
table('transcript_confirmation','B-06','V011','确认转写快照；只指不可变保存稿',[
    ident(),ref('meeting_id','会议'),ref('revision_id','确认的不可变转写'),fingerprint('content_hash','完整内容与来源哈希'),col('confirmed_by',KEY,'人工确认者'),ts('confirmed_at','确认时间')],indexes=[idx('transcript_confirm_once','meeting_id,revision_id','同稿确认重放',True)],fks=[fk('meeting_id','meeting'),fk('revision_id','transcript_revision'),fk('confirmed_by','enterprise_identity','enterprise_id')])

table('template_revision','B-07/D-04','V012','内置/个人模板不可变版本',[
    ident(),ident('template_id','模板逻辑ID'),num('revision','模板版本'),col('owner_id',KEY,'个人所有者；内置NULL',True),text('name','模板名'),text('themes','有序主题 JSON 文本，schema含1..8项'),num('theme_count','主题数'),ts('created_at','保存时间')],indexes=[idx('template_version','template_id,revision','版本唯一',True),idx('template_owner','owner_id,template_id,revision','本人模板列表')],fks=[fk('owner_id','enterprise_identity','enterprise_id')],checks=['theme_count BETWEEN 1 AND 8'])
table('generation_input','B-07','V012','纪要输入冻结集合；禁止未确认转写入队',[
    ident(),ref('meeting_id','会议'),ref('transcript_confirmation_id','必需确认转写'),ref('material_snapshot_id','B允许NULL代表规范空集合',True),ref('template_revision_id','固定模板'),ref('exclusion_revision_id','固定排除集'),ref('element_set_id','固定七类要素集合；首次可空',True),col('prompt_version',KEY,'版本化提示词引用'),col('model_config_version',KEY,'审核模型配置引用'),num('base_revision','采用时 CAS 基线'),fingerprint(),code('scope','全文/单要素'),ref('target_element_id','单要素目标',True)],fks=[fk('meeting_id','meeting'),fk('transcript_confirmation_id','transcript_confirmation'),fk('template_revision_id','template_revision')],checks=[enum('scope','FULL,ELEMENT'),"(scope='FULL' AND target_element_id IS NULL) OR (scope='ELEMENT' AND target_element_id IS NOT NULL)"],policy='输入全部同会议/允许模板权限；材料/要素/排除 FK 在对应表创建后加；prompt/model 引用受版本配置端口校验，B期文件配置需稳定版本，绝不指当前默认配置')
table('minutes_revision','B-07','V012','已保存正文；候选与当前指针分离',[
    ident(),ref('meeting_id','会议'),num('revision','单调正文版本'),ref('parent_id','父版本',True),text('title','已保存标题'),col('title_origin',CODE,'HUMAN/LINKED/AI'),text('body','已保存 Markdown'),ref('generation_input_id','生成输入；手写仍继承输入集',True),col('created_by',KEY,'创建者'),ts('created_at','保存时间')],indexes=[idx('minutes_history','meeting_id,revision','正文历史',True)],fks=[fk('meeting_id','meeting'),fk('parent_id','minutes_revision'),fk('generation_input_id','generation_input')],checks=[enum('title_origin','HUMAN,LINKED,AI')])
table('element_set','B-07/D-03','V012','独立要素集合修订，不反向改写正文',[
    ident(),ref('meeting_id','会议'),num('revision','要素修订'),ts('created_at','保存时间')],indexes=[idx('element_set_revision','meeting_id,revision','要素历史',True)],fks=[fk('meeting_id','meeting')])
table('minutes_element','B-07/D-03','V012','七类主体要素；敏感与背景不是额外主体类',[
    ident(),ref('set_id','要素集合'),code('category','七类之一'),num('ordinal','同类顺序'),text('body','内容'),code('support_state','有据/部分/无据/未提取')],indexes=[idx('element_order','set_id,category,ordinal','分类顺序',True)],fks=[fk('set_id','element_set')],checks=[enum('category','OVERVIEW,DISCUSSION,CONCLUSION,ACTION,RISK,OPEN_QUESTION,DISAGREEMENT'),enum('support_state','SUPPORTED,PARTIAL,UNSUPPORTED,NOT_EXTRACTED')])
table('action_item','B-07/D-03','V012','行动项结构信息；负责人不授权',[
    ref('element_id','ACTION类型要素'),col('owner_enterprise_id',KEY,'明确选择企业负责人',True),text('owner_display','无账号业务人员显示',True),col('due_on','date','可空截止日',True)],pk='element_id',fks=[fk('element_id','minutes_element'),fk('owner_enterprise_id','enterprise_identity','enterprise_id')])
table('exclusion_revision','B-07','V012','敏感排除集合版本；空集也有ID',[
    ident(),ref('meeting_id','会议'),num('revision','排除修订'),ts('created_at','保存时间')],indexes=[idx('exclusion_version','meeting_id,revision','排除历史',True)],fks=[fk('meeting_id','meeting')])
table('sensitive_exclusion','B-07/D-03','V012','被明确排除的来源；不删除转写/要素/录音',[
    ref('revision_id','排除集合'),ref('locator_id','具体源位置'),code('category','原文两类敏感分类的稳定配置键'),text('reason','排除说明',True)],pk='revision_id,locator_id',fks=[fk('revision_id','exclusion_revision')])
table('generation_candidate','B-07','V012','生成结果候选；迟到结果不直接覆盖',[
    ident(),ref('meeting_id','会议'),ref('input_id','冻结输入'),ref('task_id','生成任务'),ref('minutes_revision_id','候选正文；局部候选可空',True),ref('element_set_id','候选要素'),num('base_revision','生成前聚合修订'),fingerprint(),code('state','待采用/已处理/过期'),ts('created_at','产生时间'),ts('decided_at','处理时间',True)],indexes=[idx('candidate_pending','meeting_id,state,created_at,id','最终确认拦截待处理'),idx('candidate_task','task_id','每任务单一候选',True)],fks=[fk('meeting_id','meeting'),fk('input_id','generation_input'),fk('task_id','durable_task'),fk('minutes_revision_id','minutes_revision'),fk('element_set_id','element_set')],checks=[enum('state','PENDING,ADOPTED,REJECTED,STALE')])
table('source_locator','B-07/D-01','V012','有类型的不可变来源定位，不能以通用JSON抹去来源约束',[
    ident(),ref('meeting_id','会议'),code('kind','来源类型'),ref('transcript_revision_id','转写版本',True),ident('segment_id','转写稳定段',True),ref('file_id','媒体/本地文档文件',True),ref('material_revision_id','知识库/材料固定版本',True),num('page_no','PDF/PPT 1起页',nullable=True),num('paragraph_no','Word/文本1起段',nullable=True),text('section_path','章节定位',True),num('start_ms','音频起',nullable=True),num('end_ms','音频止',nullable=True)],indexes=[idx('locator_meeting','meeting_id,id','批量来源校验')],fks=[fk('meeting_id','meeting'),fk('transcript_revision_id,segment_id','transcript_segment','revision_id,segment_id'),fk('file_id','file_object')],checks=[enum('kind','TRANSCRIPT,AUDIO,PDF_PAGE,PPT_PAGE,WORD_PARAGRAPH,TEXT_PARAGRAPH,KNOWLEDGE_VERSION'),"(kind='TRANSCRIPT' AND transcript_revision_id IS NOT NULL AND segment_id IS NOT NULL) OR (kind='AUDIO' AND file_id IS NOT NULL AND start_ms IS NOT NULL AND end_ms IS NOT NULL AND end_ms>=start_ms) OR (kind IN ('PDF_PAGE','PPT_PAGE') AND file_id IS NOT NULL AND page_no>0) OR (kind IN ('WORD_PARAGRAPH','TEXT_PARAGRAPH') AND file_id IS NOT NULL AND paragraph_no>0) OR (kind='KNOWLEDGE_VERSION' AND material_revision_id IS NOT NULL)"],policy='服务按kind拒绝非适用字段；PDF/PPT/Word等要核对解析页段存在；KNOWLEDGE_VERSION必须material_revision固定外部版本；所有来源同会议且属于输入快照，非FK可表达的跨集约束由领域事务验证')
table('evidence_link','B-07/D-03','V012','具体要素或正文版本的引用证据',[
    ident(),ref('locator_id','源定位'),ref('element_id','目标要素；二选一',True),ref('minutes_revision_id','目标正文版本；二选一',True),code('support_state','支持程度'),text('target_anchor','正文段锚或要素局部锚')],indexes=[idx('evidence_element','element_id,locator_id','要素来源追踪'),idx('evidence_minutes','minutes_revision_id,locator_id','正文引用')],fks=[fk('locator_id','source_locator'),fk('element_id','minutes_element'),fk('minutes_revision_id','minutes_revision')],checks=['(element_id IS NULL) <> (minutes_revision_id IS NULL)',enum('support_state','SUPPORTED,PARTIAL,UNSUPPORTED')])

table('final_confirmation','B-08','V013','冻结最终已保存版本集合及校验摘要',[
    ident(),ref('meeting_id','会议'),num('meeting_revision','确认时聚合修订'),ref('info_revision_id','基础信息'),ref('roster_revision_id','确认名单'),ref('transcript_confirmation_id','确认转写'),ref('minutes_revision_id','已保存正文'),ref('material_snapshot_id','空材料NULL',True),ref('element_set_id','要素集合'),ref('template_revision_id','模板'),ref('exclusion_revision_id','排除设置'),fingerprint('source_manifest_hash','冻结 evidence_link/locator 集合摘要'),fingerprint(),col('confirmed_by',KEY,'确认负责人'),ts('confirmed_at','确认时间')],indexes=[idx('final_revision','meeting_id,meeting_revision','同版本确认唯一',True)],fks=[fk('meeting_id','meeting'),fk('info_revision_id','meeting_info_revision'),fk('roster_revision_id','roster_revision'),fk('transcript_confirmation_id','transcript_confirmation'),fk('minutes_revision_id','minutes_revision'),fk('element_set_id','element_set'),fk('template_revision_id','template_revision'),fk('exclusion_revision_id','exclusion_revision'),fk('confirmed_by','enterprise_identity','enterprise_id')])
table('publication','B-08','V013','冻结发布操作；成功一次即永久只读',[
    ident(),ref('meeting_id','会议'),ref('confirmation_id','最终快照'),col('business_key',KEY,'publicationId + confirmationId稳定编码'),col('library_id',KEY,'外部库ID'),col('folder_id',KEY,'外部目录ID'),code('state','发布执行状态'),num('revision','CAS'),ts('created_at','登记时间'),ts('published_at','确定成功时间',True)],indexes=[idx('publication_meeting','meeting_id','每会议唯一发布根；失败可同根换确认且先证明无外部成功',True),idx('publication_key','business_key','外部业务键唯一',True)],fks=[fk('meeting_id','meeting'),fk('confirmation_id','final_confirmation')],checks=[enum('state','READY,RUNNING,RECONCILING,FAILED,SUCCEEDED')],policy='失败且证明没有效果才允许新确认重新提交；每确认业务键固定，旧尝试保留；SUCCEEDED从不回退；RUNNING/RECONCILING阻止编辑/删除/第二发布')
table('publication_attempt','B-08','V013','每次发布/查回的诊断与固定确认关联',[
    ident(),ref('publication_id','发布根'),ref('confirmation_id','该次固定确认'),ref('operation_id','B05 durable外部操作'),code('kind','PUBLISH/RECONCILE/WITHDRAW/OBSOLETE'),code('outcome','结果状态'),ts('created_at','开始时间'),ts('finished_at','终结时间',True)],indexes=[idx('publication_attempt_history','publication_id,created_at,id','治理历史')],fks=[fk('publication_id','publication'),fk('confirmation_id','final_confirmation'),fk('operation_id','external_operation')],checks=[enum('kind','PUBLISH,RECONCILE,WITHDRAW,OBSOLETE'),enum('outcome','PENDING,SUCCESS,FAILED,UNKNOWN')])
table('external_document_mapping','B-08','V013','唯一成功外部文档映射',[
    ref('publication_id','成功发布根'),col('provider_id',CODE,'知识库配置ID'),col('external_document_id',KEY,'外部稳定ID'),text('document_url','经过允许列表校验的展示URL'),code('authority_state','最新权威状态'),ts('verified_at','状态权威核验时间')],pk='publication_id',indexes=[idx('external_document_unique','provider_id,external_document_id','一个外部文档仅映射一个成果',True)],fks=[fk('publication_id','publication')],checks=[enum('authority_state','ACTIVE,WITHDRAWN,OBSOLETE,UNKNOWN')])
table('publication_governance','D-05','V020','不可逆撤回/废止请求和确认记录',[
    ident(),ref('publication_id','成果'),code('action','撤回/废止'),text('reason','治理原因'),col('actor_id',KEY,'有权治理主体'),code('state','同步结果'),ref('operation_id','外部幂等操作'),ts('created_at','本地立即阻断时间')],fks=[fk('publication_id','publication'),fk('operation_id','external_operation')],checks=[enum('action','WITHDRAW,OBSOLETE'),enum('state','PENDING,UNKNOWN,COMPLETE,FAILED')])
table('external_event_receipt','D-05','V020','验签后外部事件去重/重放防护',[
    code('provider_id','供应商'),col('event_id',KEY,'供应商唯一事件'),fingerprint('payload_hash','安全摘要，重复异内容拒绝'),ts('received_at','接收时间'),ts('processed_at','处理提交时间',True)],pk='provider_id,event_id',policy='协议签名/有效时间窗先校验；处理与收据同事务；乱序事件通过权威查回确定，不按到达顺序恢复终态')

table('media_source','C-01','V014','归一化音频采样数与多源映射',[
    ident(),ref('batch_id','有序输入批次'),ref('normalized_file_id','归一化WAV'),num('sample_rate','采样率'),num('channels','通道'),num('bits_per_sample','采样位宽'),num('sample_count','有效采样总数')],fks=[fk('batch_id','input_batch'),fk('normalized_file_id','file_object')],checks=['sample_rate>0 AND channels>0 AND bits_per_sample>0'])
table('media_source_span','C-01','V014','跨段独立源时间轴到合并轴映射',[
    ref('media_id','归一化音频'),num('ordinal','片段顺序'),ref('source_file_id','原源文件'),num('source_start_sample','源起点'),num('global_start_sample','合并有效采样起点'),num('sample_count','长度')],pk='media_id,ordinal',fks=[fk('media_id','media_source'),fk('source_file_id','file_object')],checks=['ordinal>0 AND sample_count>0'])
table('speaker','C-03','V015','说话人业务标签；实名与授权隔离',[
    ident(),ref('meeting_id','会议'),text('display_name','匿名/人工标签'),col('enterprise_id',KEY,'人工确认实名可空',True),code('identity_source','ANONYMOUS/HUMAN/VOICE_CANDIDATE'),ref('merged_into_id','合并目标',True),num('revision','说话人修订')],fks=[fk('meeting_id','meeting'),fk('enterprise_id','enterprise_identity','enterprise_id'),fk('merged_into_id','speaker')],checks=[enum('identity_source','ANONYMOUS,HUMAN,VOICE_CANDIDATE')])
table('correction','C-03','V015','与具体转写版本绑定的纠错建议',[
    ident(),ref('meeting_id','会议'),ref('revision_id','源转写版本'),ident('segment_id','稳定段'),text('proposed_text','建议文字'),code('state','待处理/采纳/拒绝/失效'),num('revision','建议CAS')],indexes=[idx('correction_pending','meeting_id,revision_id,state,id','确认前零待处理检查')],fks=[fk('meeting_id','meeting'),fk('revision_id,segment_id','transcript_segment','revision_id,segment_id')],checks=[enum('state','PENDING,ACCEPTED,REJECTED,STALE')])
table('edit_operation','C-03','V015','可撤销编辑操作；撤销也创建新修订',[
    ident(),ref('meeting_id','会议'),ref('before_revision_id','之前转写'),ref('after_revision_id','之后转写'),code('kind','纠错/替换/合并/撤销'),ref('reverts_id','撤销对象',True),col('actor_id',KEY,'操作者'),ts('created_at','操作时间')],indexes=[idx('edit_history','meeting_id,created_at,id','历史顺序')],fks=[fk('meeting_id','meeting'),fk('before_revision_id','transcript_revision'),fk('after_revision_id','transcript_revision'),fk('reverts_id','edit_operation')])
table('recording_session','C-04','V016','唯一采集者与writerEpoch；固定后端',[
    ident(),ref('meeting_id','会议'),col('backend_id',CODE,'创建固定后端'),code('state','录音状态'),num('writer_epoch','重连抢占代际'),code('writer_id','当前采集端'),ts('writer_expires_at','DB写租约'),num('cancel_epoch','会议代际'),ref('archive_file_id','完整归档后文件',True)],indexes=[idx('recording_meeting','meeting_id,state,id','删除资格检查')],fks=[fk('meeting_id','meeting'),fk('backend_id','storage_backend'),fk('archive_file_id','file_object')],checks=[enum('state','RECORDING,PAUSED,FINISHING,ARCHIVING,AVAILABLE,FAILED,ABORTED')])
table('recording_part','C-04','V016','分段独立序号与durable连续前缀',[
    ident(),ref('session_id','会话'),num('ordinal','段序'),num('highest_contiguous_seq','已落盘连续序号',0),num('final_seq','结束申报；未结束NULL',nullable=True),num('sample_count','校验后的有效总采样',0)],indexes=[idx('recording_part_order','session_id,ordinal','段序唯一',True)],fks=[fk('session_id','recording_session')],checks=['ordinal>0'])
table('recording_chunk','C-04','V016','落盘ACK事实来源，重复序号异哈希冲突',[
    ref('part_id','录音段'),num('seq','1起序号'),num('writer_epoch','收到时已验证代际'),num('sample_start','0起有效采样'),num('sample_count','采样长度'),fingerprint('sha256','音频校验'),ref('file_id','已落盘分片'),ts('persisted_at','落盘登记时间')],pk='part_id,seq',fks=[fk('part_id','recording_part'),fk('file_id','file_object')],checks=['seq>0 AND sample_count>0'])
table('subtitle_revision','C-06','V016','临时/稳定字幕历史；不冒充正式转写',[
    ref('session_id','录音会话'),ident('segment_id','字幕段'),num('revision','字幕版本'),ref('part_id','录音分段'),code('state','平台真实语义'),text('body','字幕'),num('start_ms','段起'),num('end_ms','段止'),num('event_sequence','outbox提交游标')],pk='session_id,segment_id,revision',indexes=[idx('subtitle_event','session_id,event_sequence','重连顺序')],fks=[fk('session_id','recording_session'),fk('part_id','recording_part')],checks=[enum('state','TEMPORARY,STABLE'),'end_ms>=start_ms'])

table('material_reference','D-01','V017','会议本地/知识库材料逻辑关联',[
    ident(),ref('meeting_id','会议'),code('kind','LOCAL/KNOWLEDGE'),col('provider_id',CODE,'外部提供方',True),col('external_id',KEY,'外部稳定文件ID',True),ref('file_id','本地文件',True),code('state','关联状态')],indexes=[idx('material_meeting','meeting_id,state,id','材料列表')],fks=[fk('meeting_id','meeting'),fk('file_id','file_object')],checks=[enum('kind','LOCAL,KNOWLEDGE'),enum('state','ACTIVE,REMOVED'),"(kind='LOCAL' AND file_id IS NOT NULL AND external_id IS NULL) OR (kind='KNOWLEDGE' AND provider_id IS NOT NULL AND external_id IS NOT NULL AND file_id IS NULL)"])
table('material_revision','D-01','V017','固定源版本；外部引用永不跟随最新',[
    ident(),ref('reference_id','材料关联'),num('revision','材料版本'),text('external_version','稳定外部版本',True),ref('content_file_id','受控获取的不可变本地内容',True),fingerprint('content_hash','内容校验'),code('parse_state','解析状态'),ts('created_at','版本建立')],indexes=[idx('material_version','reference_id,revision','唯一历史',True)],fks=[fk('reference_id','material_reference'),fk('content_file_id','file_object')],checks=[enum('parse_state','PENDING,READY,FAILED,UNSUPPORTED')])
table('material_snapshot','D-01','V017','已明确应用材料集合；待修订不能改有效集合',[
    ident(),ref('meeting_id','会议'),num('revision','集合版本'),fingerprint(),ts('applied_at','明确应用时间')],indexes=[idx('material_snapshot_revision','meeting_id,revision','快照历史',True)],fks=[fk('meeting_id','meeting')])
table('material_snapshot_item','D-01','V017','冻结材料修订清单',[
    ref('snapshot_id','快照'),ref('material_revision_id','固定材料版本'),num('ordinal','显示顺序')],pk='snapshot_id,material_revision_id',indexes=[idx('snapshot_order','snapshot_id,ordinal','唯一顺序',True)],fks=[fk('snapshot_id','material_snapshot'),fk('material_revision_id','material_revision')])
table('parsed_document','D-01','V017','解析版本及能力状态；不把空解析当成功',[
    ident(),ref('material_revision_id','材料版本'),col('parser_version',KEY,'解析器配置版本'),ref('text_file_id','完整文本文件',True),code('state','解析结果状态'),num('unit_count','页段数'),code('error_code','明确不支持/失败原因',True)],fks=[fk('material_revision_id','material_revision'),fk('text_file_id','file_object')],checks=[enum('state','READY,FAILED,UNSUPPORTED')])
table('parsed_unit','D-01','V017','文本与页/段映射保留',[
    ref('document_id','解析文档'),num('ordinal','文本单元序号'),code('kind','PAGE/PARAGRAPH/SECTION'),num('page_no','页码',nullable=True),num('paragraph_no','段号',nullable=True),text('section_path','章节路径',True),text('body','抽取文字')],pk='document_id,ordinal',fks=[fk('document_id','parsed_document')],checks=[enum('kind','PAGE,PARAGRAPH,SECTION')])
table('export_job','D-06','V021','按已保存有效版本生成三格式导出',[
    ident(),ref('meeting_id','会议'),ref('minutes_revision_id','具体正文版本'),ref('confirmation_id','发布回看固定快照',True),code('format','md/pdf/docx'),ref('task_id','导出任务'),ref('output_file_id','输出文件',True),num('access_revision','受理时权限版本'),code('state','任务状态')],fks=[fk('meeting_id','meeting'),fk('minutes_revision_id','minutes_revision'),fk('confirmation_id','final_confirmation'),fk('task_id','durable_task'),fk('output_file_id','file_object')],checks=[enum('format','md,pdf,docx'),enum('state','PENDING,READY,FAILED,INVALIDATED')])
table('meeting_deletion','D-07','V022','删除请求与清理完成事实',[
    ident(),ref('meeting_id','会议'),num('cancel_epoch','删除时递增代际'),code('state','清理结果'),ref('task_id','清理协调任务'),ts('requested_at','访问立即阻断时间'),ts('completed_at','物理文件及业务正文清理完成',True)],indexes=[idx('deletion_meeting','meeting_id','单一删除根',True)],fks=[fk('meeting_id','meeting'),fk('task_id','durable_task')],checks=[enum('state','PENDING,FAILED,COMPLETE')])
table('topic','D-08','V023','单层个人专题，不改变内容版本/ACL',[
    ident(),col('owner_id',KEY,'专题所有者'),text('name','名称'),num('revision','专题CAS'),ts('created_at','创建时间')],indexes=[idx('topic_owner','owner_id,id','个人专题')],fks=[fk('owner_id','enterprise_identity','enterprise_id')])
table('meeting_topic','D-08','V023','本人负责且已发布会议归集',[
    ref('topic_id','专题'),ref('meeting_id','会议'),ts('added_at','加入时间')],pk='topic_id,meeting_id',fks=[fk('topic_id','topic'),fk('meeting_id','meeting')],policy='事务校验专题owner=会议owner且ever_published=1；不授予权限；空专题才能删，移除归集不删会议')

table('corpus_feedback','E-01','V024','受控反馈候选/审核；不改原确认稿',[
    ident(),ref('meeting_id','来源会议'),ref('source_revision_id','来源转写版本'),col('submitted_by',KEY,'提交人'),code('category','受控类别键'),text('comment','反馈内容'),code('state','候选审核状态'),num('revision','CAS'),col('reviewed_by',KEY,'独立审核主体',True),text('review_reason','审核原因',True),ts('created_at','提交时间'),ts('reviewed_at','审核时间',True)],fks=[fk('meeting_id','meeting'),fk('source_revision_id','transcript_revision'),fk('submitted_by','enterprise_identity','enterprise_id'),fk('reviewed_by','enterprise_identity','enterprise_id')],checks=[enum('state','PENDING,ACCEPTED,REJECTED,REVOKED')])
table('feedback_segment','E-01','V024','反馈关联稳定源片段',[
    ref('feedback_id','反馈'),ref('revision_id','具体转写版本'),ident('segment_id','源段')],pk='feedback_id,revision_id,segment_id',fks=[fk('feedback_id','corpus_feedback'),fk('revision_id,segment_id','transcript_segment','revision_id,segment_id')])
table('corpus_revision','E-01','V024','批准后可用语料修订；全局使用另有安全门',[
    ident(),ref('feedback_id','已接受反馈'),num('revision','语料版本'),ref('content_file_id','受控语料文件'),code('state','APPROVED/REVOKED'),code('purpose','允许用途配置键'),ts('approved_at','批准时间')],indexes=[idx('corpus_version','feedback_id,revision','语料版本',True)],fks=[fk('feedback_id','corpus_feedback'),fk('content_file_id','file_object')],checks=[enum('state','APPROVED,REVOKED')])
table('voiceprint_consent','E-02','V025','明确用途授权及撤销记录',[
    ident(),col('subject_id',KEY,'企业主体'),code('purpose','已批准用途'),text('consent_reference','受控授权凭据引用'),code('state','GRANTED/REVOKED'),num('revision','授权CAS'),ts('granted_at','授权时间'),ts('revoked_at','撤销时间',True)],fks=[fk('subject_id','enterprise_identity','enterprise_id')],checks=[enum('state','GRANTED,REVOKED')])
table('voiceprint_sample','E-02','V025','声纹样本与审核状态；不写ACL',[
    ident(),ref('consent_id','有效授权'),ref('sample_file_id','采样文件'),col('model_version',KEY,'特征模型版本'),num('dimension','向量维数'),ref('encrypted_embedding_file_id','加密特征文件'),col('key_reference',KEY,'外部密钥引用，非密钥'),code('state','候选/生效/拒绝/撤销'),num('revision','CAS'),col('reviewed_by',KEY,'审核主体',True),ts('reviewed_at','审核时间',True)],indexes=[idx('voice_consent','consent_id,state,id','撤销授权下全部样本')],fks=[fk('consent_id','voiceprint_consent'),fk('sample_file_id','file_object'),fk('encrypted_embedding_file_id','file_object'),fk('reviewed_by','enterprise_identity','enterprise_id')],checks=['dimension>0',enum('state','PENDING,ACTIVE,REJECTED,REVOKED')])
table('evaluation_dataset','E-03','V026','受控评测数据版本',[
    ident(),col('version_key',KEY,'数据集唯一版本'),ref('manifest_file_id','样本及预期结果清单'),code('purpose','允许用途'),fingerprint('content_hash','数据集哈希'),ts('created_at','创建时间')],indexes=[idx('dataset_version','version_key','版本唯一',True)],fks=[fk('manifest_file_id','file_object')])
table('configuration_revision','E-03','V026','白名单配置候选；B期配置版本也可来自受控文件',[
    ident(),code('kind','MODEL/PROMPT/SYSTEM'),num('revision','类别版本'),text('configuration','schema校验JSON；无命令/任意URL/凭证'),fingerprint('content_hash','配置哈希'),code('state','候选审核状态'),col('created_by',KEY,'提出人'),col('reviewed_by',KEY,'独立审核人',True),ts('created_at','创建时间'),ts('reviewed_at','审核时间',True)],indexes=[idx('configuration_version','kind,revision','配置历史',True)],fks=[fk('created_by','enterprise_identity','enterprise_id'),fk('reviewed_by','enterprise_identity','enterprise_id')],checks=[enum('kind','MODEL,PROMPT,SYSTEM'),enum('state','PENDING,APPROVED,REJECTED'), 'reviewed_by IS NULL OR reviewed_by <> created_by'])
table('evaluation_run','E-03','V026','固定数据/模型/提示词评测任务',[
    ident(),ref('dataset_id','固定数据集'),col('model_config_version',KEY,'模型配置固定版本'),col('prompt_version',KEY,'提示词固定版本'),ref('task_id','评测任务'),code('state','执行状态'),num('sample_count','实际样本数'),text('metrics','指标JSON；仅真实测得值',True),ref('errors_file_id','逐样本错误受控文件',True),ts('created_at','运行创建')],fks=[fk('dataset_id','evaluation_dataset'),fk('task_id','durable_task'),fk('errors_file_id','file_object')],checks=[enum('state','PENDING,RUNNING,SUCCEEDED,FAILED')])
table('configuration_release','E-03','V026','配置发布/回滚均新增记录',[
    ident(),code('kind','MODEL/PROMPT/SYSTEM'),num('revision','发布CAS序列'),ref('configuration_id','已批准候选'),ref('evaluation_run_id','评测证据'),ref('rollback_of_id','若回滚指原发布',True),col('released_by',KEY,'发布操作者'),ts('released_at','发布时间')],indexes=[idx('release_revision','kind,revision','发布单调唯一',True)],fks=[fk('configuration_id','configuration_revision'),fk('evaluation_run_id','evaluation_run'),fk('rollback_of_id','configuration_release'),fk('released_by','enterprise_identity','enterprise_id')],checks=[enum('kind','MODEL,PROMPT,SYSTEM')])
table('mail_job','E-04','V027','已保存版本受控链接邮件；不含正文附件/外部邮箱',[
    ident(),ref('meeting_id','会议'),ref('minutes_revision_id','已保存版本'),ref('confirmation_id','发布快照若有',True),col('sender_id',KEY,'负责人'),ref('operation_id','durable外部消息操作'),col('business_key',KEY,'稳定消息查回键'),code('state','发送/未知/失败'),ts('created_at','受理时间'),ts('sent_at','确定发送时间',True)],indexes=[idx('mail_key','business_key','消息幂等',True)],fks=[fk('meeting_id','meeting'),fk('minutes_revision_id','minutes_revision'),fk('confirmation_id','final_confirmation'),fk('sender_id','enterprise_identity','enterprise_id'),fk('operation_id','external_operation')],checks=[enum('state','PENDING,SENT,FAILED,UNKNOWN,MANUAL_REQUIRED')])
table('mail_recipient','E-04','V027','固定企业收件人集合；发送前再验证有效名单和ACL',[
    ref('mail_job_id','邮件任务'),col('enterprise_id',KEY,'仍有查看权的确认企业参会人'),code('state','该收件人的结果'),text('provider_message_id','平台回执标识',True)],pk='mail_job_id,enterprise_id',fks=[fk('mail_job_id','mail_job'),fk('enterprise_id','enterprise_identity','enterprise_id')],checks=[enum('state','PENDING,SENT,FAILED,UNKNOWN,REVOKED')])

# Cross-domain relations are added AFTER all review tables; production adds each only
# when the named dependency is delivered. B empty material pointers are NULL until D-01.
EXTRA = [
 ('generation_input', fk('exclusion_revision_id','exclusion_revision')),
 ('generation_input', fk('element_set_id','element_set')),
 ('generation_input', fk('material_snapshot_id','material_snapshot')),
 ('final_confirmation', fk('material_snapshot_id','material_snapshot')),
 ('sensitive_exclusion', fk('locator_id','source_locator')),
 ('source_locator', fk('material_revision_id','material_revision')),
 ('transcript_segment', fk('speaker_id','speaker')),
]
for name, relation in EXTRA:
    next(t for t in TABLES if t['name']==name)['fks'].append(relation)

# Current working/input pointers are intentionally logical (same-meeting checks at
# the aggregate fence); they cannot point at unsaved browser state.
meeting = next(t for t in TABLES if t['name']=='meeting')
meeting['cols'].extend([
    ref('transcript_revision_id','当前已保存人工转写工作稿',True),
    ref('material_snapshot_id','当前明确应用材料快照；B空集合NULL',True),
    ref('template_revision_id','当前选择模板；不等于已应用正文',True),
    ref('element_set_id','当前有效要素集',True),
    ref('exclusion_revision_id','当前排除设置版本',True),
    col('generation_fingerprint',HASH,'当前有效生成输入指纹',True),
    col('needs_generation','tinyint unsigned','正文是否需更新；独立于待采用候选',False,'0'),
    col('materials_pending','tinyint unsigned','材料修订有尚未明确应用变更',False,'0')
])
meeting['checks'].extend(['needs_generation IN (0,1)','materials_pending IN (0,1)'])
speaker = next(t for t in TABLES if t['name']=='speaker')
speaker['task']='B-06/C-03'
speaker['migration']='V011'
locator = next(t for t in TABLES if t['name']=='source_locator')
locator['checks'][1]=locator['checks'][1].replace('AND page_no>0','AND page_no IS NOT NULL AND page_no>0').replace('AND paragraph_no>0','AND paragraph_no IS NOT NULL AND paragraph_no>0')
consent=next(t for t in TABLES if t['name']=='voiceprint_consent')
consent['cols'].extend([col('revoked_by',KEY,'撤销操作者',True),text('revocation_reason','撤销原因；撤销时必填',True)])
consent['fks'].append(fk('revoked_by','enterprise_identity','enterprise_id'))
consent['checks'].append("state <> 'REVOKED' OR (revoked_at IS NOT NULL AND revoked_by IS NOT NULL AND revocation_reason IS NOT NULL)")
sample=next(t for t in TABLES if t['name']=='voiceprint_sample')
sample['cols'].extend([text('review_reason','批准/拒绝依据',True),ts('revoked_at','样本失效时间',True)])
mail=next(t for t in TABLES if t['name']=='mail_job')
mail['cols']=[ref('task_id','批次协调任务；每个收件人有独立发送操作') if c['name']=='operation_id' else c for c in mail['cols']]
mail['fks']=[fk('task_id','durable_task') if f['cols']==['operation_id'] else f for f in mail['fks']]
recipient=next(t for t in TABLES if t['name']=='mail_recipient')
recipient['cols'].extend([ref('operation_id','该收件人独立发送/查回操作'),col('message_key',KEY,'jobId + enterpriseId稳定消息键')])
recipient['fks'].append(fk('operation_id','external_operation'))
recipient['indexes'].extend([idx('mail_recipient_operation','operation_id','每外部发送只对应一个收件人',True),idx('mail_recipient_key','message_key','单收件人消息幂等键',True)])
recipient['policy']='协调任务展开每收件人独立durable task/operation；发送前复验确认名单、有效READ和治理状态；部分成功只对未知收件人查回，已SENT永不再次发送；不是重新发送整批'

# Independent review round 1: R1-R6. These are review-target changes only; reserved
# domain migrations remain unimplemented and frozen V001/V002/V005 are untouched.
table('meeting_search_projection','B-03/D-02','V008','R1 列表当前名称/编号及筛选字段投影；与根修改同事务',[
    ref('meeting_id','会议；公开复制/搜索编号就是完整meeting.id'),ref('info_revision_id','当前已保存基础信息；初始为空',True),ref('roster_revision_id','当前确认名单；未确认为空',True),
    col('name_search','longtext COLLATE utf8mb4_0900_as_ci','当前名称；英文大小写不敏感，非正文',True),
    col('number_search','varchar(36) COLLATE utf8mb4_0900_as_ci','完整36字符meeting.id的搜索投影'),
    col('category_id',KEY,'当前分类；NULL映射显示其他',True),num('meeting_revision','投影对应根修订')],pk='meeting_id',
    indexes=[idx('search_category','category_id,meeting_id','分类先筛再包含匹配')],fks=[fk('meeting_id','meeting'),fk('info_revision_id','meeting_info_revision'),fk('roster_revision_id','roster_revision')],
    policy='服务端同事务维护；不是异步最终一致授权源。查询先根/ACL过滤，投影revision/指针须与根相符；正文/摘要不参加列表q匹配；根删除同步清理')
table('meeting_person_search','B-03/D-02','V008','R1 当前已确认名单姓名投影；草稿和AI候选绝不进入',[
    ref('meeting_id','会议'),ref('roster_id','当前CONFIRMED名单'),ident('member_id','冻结名单项'),col('name_search','longtext COLLATE utf8mb4_0900_as_ci','该已确认名单中的完整显示名')],
    pk='meeting_id,member_id',fks=[fk('meeting_id','meeting'),fk('roster_id,member_id','roster_member','roster_id,member_id')],
    policy='名单明确确认时与有效ACL同事务替换；包含确认的企业/外部业务人员显示名但不授予权限；目录改名不改冻结名单；查询按meeting_id EXISTS避免多人命中重复会议')
meeting['indexes'].extend([idx('meeting_owner_updated','owner_id,lifecycle,updated_at,id','负责列表默认更新时间倒序'),idx('meeting_stage_updated','lifecycle,stage,updated_at,id','阶段过滤后的默认稳定倒序')])
topic=next(t for t in TABLES if t['name']=='topic')
topic['cols'].extend([text('description','R3 可选专题说明；未填写/空串归一为NULL',True),text('name_normalized','R2 NFC及首尾Unicode空白剔除后的完整名称'),fingerprint('name_hash','完整name_normalized UTF-8字节SHA256，仅候选桶')])
topic['indexes'].append(idx('topic_name_bucket','owner_id,name_hash,id','非唯一摘要桶；持owner身份行锁后完整二进制名称复核'))
topic['policy']='个人名称不能为空；创建/改名先锁owner企业身份行，再按统一顺序锁专题并CAS revision；摘要相同逐项比较完整规范化名称，仅完整相同冲突；说明与名称同事务保存；空专题才能删除'
next(c for c in topic['cols'] if c['name']=='name_normalized')['type']='longtext COLLATE utf8mb4_0900_bin'
table('minutes_template','B-07/D-04','V012','R2 模板逻辑根与当前个人名称；历史名称不占用命名空间',[
    ident(),col('owner_id',KEY,'个人所有者；内置模板NULL',True),text('name','当前完整展示名'),text('name_normalized','NFC及首尾Unicode空白剔除后的完整当前名称'),fingerprint('name_hash','完整规范化名称SHA256；非唯一摘要桶'),ref('current_revision_id','当前template_revision；同模板应用完整性'),num('revision','逻辑根CAS'),ts('created_at','创建时间')],
    indexes=[idx('template_name_bucket','owner_id,name_hash,id','同个人当前名称候选；允许摘要碰撞但禁止完整同名')],fks=[fk('owner_id','enterprise_identity','enterprise_id')],
    policy='个人创建/改名锁owner身份行串行；完整规范名二进制比较；内置由受控配置维护。改名产生新template_revision并CAS当前指针；旧revision名可读但不参与当前重名判断；不可跨所有者引用')
template=next(t for t in TABLES if t['name']=='template_revision')
next(c for t in TABLES if t['name']=='minutes_template' for c in t['cols'] if c['name']=='name_normalized')['type']='longtext COLLATE utf8mb4_0900_bin'
template['fks'].append(fk('template_id','minutes_template'))
template['policy']='不可变历史；owner_id必须与minutes_template一致，name是该修订的展示名；当前名称只由逻辑根登记；根当前指针由服务校验同template_id并同事务更新，避免循环FK'

# R4: stable business operation survives failed executions. Each new task owns an
# append-only execution row, not a replacement logical business key.
operation=next(t for t in TABLES if t['name']=='external_operation')
operation['cols']=[c for c in operation['cols'] if c['name'] not in ('task_id','origin_attempt')]
operation['cols'].extend([code('subject_kind','聚合类型'),col('subject_id',KEY,'聚合ID'),num('current_execution_no','当前执行代际；初次1')])
for c in operation['cols']:
    if c['name']=='request_hash': c['meaning']='逻辑内容摘要：固定confirmation/body及业务操作；不替代每次完整请求摘要'
    if c['name']=='marked_at': c['meaning']='首次before-external marker实际提交时间'
operation['indexes']=[i for i in operation['indexes'] if i['name']!='external_task']
operation['fks']=[fk('subject_kind,subject_id','task_subject','subject_kind,subject_id')]
operation['checks']=[enum('state','MARKED,UNKNOWN,RETRY_QUEUED,SUCCEEDED,FAILED,MANUAL_REQUIRED'),'current_execution_no > 0']
operation['policy']='operation_kind/business_key全历史唯一；不归单个task。current_execution_no由同聚合锁下CAS；独立reconcile_epoch跨执行永久递增、不清零。当前执行由(operation_id,no)应用校验。仅FAILED且当前execution有确定零效果证据可追加执行；UNKNOWN禁止重发'
table('external_execution','B-05/B-08','V010','R4 稳定逻辑操作下的追加执行尝试；支持同确认零效果失败重试',[
    ident(),ref('operation_id','稳定逻辑操作'),num('execution_no','同操作单调1起代际'),ref('task_id','本次新任务唯一拥有者'),num('task_attempt','before-call时实际领取attempt；排队时NULL',nullable=True),
    code('state','执行状态'),fingerprint('request_hash','本次完整请求含目标位置的不可变SHA256'),text('request_manifest','版本化安全请求清单：确认/目标ID/配置/固定输入引用；不含密钥'),
    ts('created_at','入队/创建时间'),ts('marked_at','本次实际before-call提交时间；PREPARED为NULL',True),ts('finished_at','确定终结时间',True),
    code('no_effect_basis','明确零效果证明类别；UNKNOWN/NOT_FOUND不可填写',True),text('no_effect_evidence','受控证明引用及必要事实；无凭证/正文',True),ts('no_effect_verified_at','证明核验时间',True)],
    indexes=[idx('external_execution_number','operation_id,execution_no','一个操作每代一次',True),idx('external_execution_task','task_id','一个外部调用任务只拥有一次执行',True)],
    fks=[fk('operation_id','external_operation'),fk('task_id','durable_task')],
    checks=['execution_no > 0',enum('state','PREPARED,MARKED,UNKNOWN,SUCCEEDED,FAILED'),"state <> 'MARKED' OR (task_attempt IS NOT NULL AND marked_at IS NOT NULL)","no_effect_basis IS NULL OR no_effect_basis IN ('NOT_SENT','DEFINITIVE_REJECTION','PROVEN_CUTOFF_FENCED')","(no_effect_basis IS NULL AND no_effect_evidence IS NULL AND no_effect_verified_at IS NULL) OR (state='FAILED' AND no_effect_basis IS NOT NULL AND no_effect_evidence IS NOT NULL AND no_effect_verified_at IS NOT NULL AND finished_at IS NOT NULL)"],
    policy='请求/任务/代际创建后不改；仅状态和证明由当前独立operation token推进，终结后冻结。新retry追加行，不删除旧行/改原task。跨执行旧token不能写当前结果；原task失败历史保留。确定零效果证明还必须排除旧发送者迟到重新发出')
reservation=next(t for t in TABLES if t['name']=='model_reservation')
reservation['cols']=[ref('execution_id','具体外部调用执行；独立占额') if c['name']=='operation_id' else c for c in reservation['cols']]
reservation['indexes']=[idx('reservation_execution','execution_id','每实际执行单独占额',True) if i['name']=='reservation_operation' else i for i in reservation['indexes']]
reservation['fks']=[fk('execution_id','external_execution') if f['cols']==['operation_id'] else f for f in reservation['fks']]
reservation['policy']='独立准入事务按operation→当前execution→model→reservation锁序，复验独立token/执行号及配置；持有本执行许可后才调用。lease/HTTP超时不释放，未知deadline保守占额；execution明确终结后单独幂等结算，不以逻辑操作新执行状态释放旧执行额度'
publication_attempt=next(t for t in TABLES if t['name']=='publication_attempt')
publication_attempt['cols'].append(ref('execution_id','具体发布/查回操作对应执行'))
publication_attempt['fks'].append(fk('execution_id','external_execution'))
publication=next(t for t in TABLES if t['name']=='publication')
publication['policy']='同confirmation确定零效果失败后可原确认重试：复用稳定publication/business_key/operation，追加external_execution及新task，不要求新确认；双重retry在subject/根/operation锁下只接受一次。更换confirmation仅真实修改重新确认时允许。SUCCEEDED永不重试，UNKNOWN不重发'
next(i for i in publication['indexes'] if i['name']=='publication_meeting')['reason']='每会议唯一发布根；确定零效果失败可原确认追加执行，真实内容修改才换确认'

# R5/R6: pre-confirmation processing provenance and immutable segment annotations.
table('transcription_input','B-06/C-02/C-03/D-01','V011','R5 转写导入/识别/重新增强不可变输入快照，独立于纪要generation_input',[
    ident(),ref('meeting_id','会议'),ref('batch_id','原始有序主文件集合'),code('kind','IMPORT/ASR/ENHANCE'),ref('source_revision_id','重新增强所据已保存转写；首次导入/ASR为空',True),
    ref('material_snapshot_id','明确应用材料快照；B未启用或空集合为NULL',True),code('material_mode','EMPTY或PINNED，NULL不表示未知'),
    col('processing_config_version',KEY,'完整处理配置清单固定版本'),col('parser_version',KEY,'使用解析器时必填；纯ASR可空',True),col('model_config_version',KEY,'模型调用固定配置；纯导入可空',True),col('prompt_version',KEY,'使用提示词时固定版本；无提示词步骤可空',True),
    col('manifest_schema_version','int unsigned','可恢复输入清单schema版本'),text('config_manifest','不可变规范化配置/步骤/来源引用清单；不含密钥或可变latest'),fingerprint(),num('base_revision','受理时会议修订'),num('cancel_epoch','受理取消代际'),ts('created_at','与任务一同提交时间')],
    indexes=[idx('transcription_input_history','meeting_id,created_at,id','处理输入历史追溯')],fks=[fk('meeting_id','meeting'),fk('batch_id','input_batch'),fk('source_revision_id','transcript_revision'),fk('material_snapshot_id','material_snapshot')],
    checks=[enum('kind','IMPORT,ASR,ENHANCE'),enum('material_mode','EMPTY,PINNED'),"(material_mode='EMPTY' AND material_snapshot_id IS NULL) OR (material_mode='PINNED' AND material_snapshot_id IS NOT NULL)","kind <> 'ENHANCE' OR source_revision_id IS NOT NULL","kind <> 'IMPORT' OR parser_version IS NOT NULL","kind='IMPORT' OR model_config_version IS NOT NULL",'manifest_schema_version > 0'],
    policy='B只EMPTY/NULL且规范空集合入指纹；D01非空写入前V017加material FK，审阅库完整建FK。typed task input=TRANSCRIPTION_INPUT/id，输出revision反向引用本输入；手工保存/恢复继承原处理来源，不读meeting可变材料/配置。FK/source_revision同会议应用校验；历史删除先清源引用，禁止循环或子孙作为source')
transcript=next(t for t in TABLES if t['name']=='transcript_revision')
transcript['cols'].extend([ref('processing_input_id','固定处理/增强输入；手工修订继承父处理来源'),ref('producer_task_id','产生本结果任务；人工保存为空',True)])
transcript['fks'].extend([fk('processing_input_id','transcription_input'),fk('producer_task_id','durable_task')])
transcript['policy']='各稿不可变；生成结果input_fingerprint必须等于processing_input输入指纹，producer_task.spec.input_id必须同该输入。WORKING/恢复稿继承具体父稿处理来源，不能改用当前材料配置；确认content_hash另含已保存片段与多选标记'
table('transcript_segment_mark','B-06/C-03','V011','R6 片段多选标记随转写版本冻结；不是纪要要素',[
    ref('revision_id','已保存转写版本'),ident('segment_id','该版本稳定段'),code('mark','KEY关键/CONCLUSION结论/DISPUTE争议/GENERATION_FOCUS重点生成')],
    pk='revision_id,segment_id,mark',fks=[fk('revision_id,segment_id','transcript_segment','revision_id,segment_id')],checks=[enum('mark','KEY,CONCLUSION,DISPUTE,GENERATION_FOCUS')],
    policy='同段可0..4类，PK禁止重复；增删标记与片段保存产生新revision，旧标记不UPDATE/DELETE。规范排序的标记集进入确认content_hash/纪要inputFingerprint；确认后任何标记改动清当前转写/最终确认并要求重新确认，不证明内容真实；只作生成参考')

# Scoped upload addendum atop adopted 1.1; no other table/constraint changes.
upload=next(t for t in TABLES if t['name']=='upload_session')
upload['cols'].extend([
    text('display_name','初始化 name 原值；仅展示数据，不作路径、不新增产品长度上限；创建后不可改'),
    code('purpose','初始化时服务端校验的已启用且有权用途码；创建后不可改，不是公司字典声明')
])
next(c for c in upload['cols'] if c['name']=='provider_upload_token')['meaning']='内部存储 UploadToken.sessionId；与 backend_id 重建完整 token；已受理会话必须非空，仅服务端使用'
next(c for c in upload['cols'] if c['name']=='expected_sha256')['meaning']='预声明完整SHA256；若初始NULL，在OPEN转FINALIZING事务填入完成请求哈希，之后不可变'
upload['policy']='初始化成功前提交名称/用途/固定位置/存储token；OPEN下提交不可变分片；锁session冻结全部分片及完整hash后单向FINALIZING，不回OPEN；重启据行及分片恢复，完成在durable blob后同事务登记file/引用并写completed_file_id；名称复制到file_object，purpose保留在会话及引用账本；终态重放仍鉴权，删除按依赖显式清理'
part=next(t for t in TABLES if t['name']=='upload_part')
part['policy']='只登记实际durable且校验通过的服务端PartReceipt；锁session确认OPEN后INSERT，同号同size/hash重放原回执，异内容冲突；不UPDATE已接纳回执，不信任客户端provider token；FINALIZING后集合冻结且禁止增删改，迟到I/O不能登记；完成重放/恢复仍需保留完整清单，清理走显式生命周期'

# B00-1.3: normalized generated directories and exact human review provenance.
# Root proposal ACK 2026-10-22; only one of the original 85 physical tables changes.
exclusion=next(t for t in TABLES if t['name']=='sensitive_exclusion')
exclusion['pk']=['revision_id','locator_id','category']
exclusion['checks']=[enum('category','INTERNAL_BUSINESS_DATA,POLITICAL_SENSITIVE')]
exclusion['policy']='不可变选择集；PK保留同一locator的两个标签，不复制物理locator绕过去重。勾选多来源检测展开同category全部locator；重叠项按(locator,category)并集，取消只影响对应标签/位置。下一次生成应用，不删除检测/原文/要素、不改ACL'
table('sensitive_detection','B-07/D-03','V012','未选择也保留的敏感检测目录；不属于七类主体要素',[
    ident(),ref('set_id','所属不可变要素集'),ref('input_id','该检测原始生成输入；复制保留'),code('category','两类敏感标签之一'),num('ordinal','同类别1起展示顺序')],
    indexes=[idx('detection_order','set_id,category,ordinal','同类条目顺序唯一',True)],fks=[fk('set_id','element_set'),fk('input_id','generation_input')],
    checks=['ordinal > 0',enum('category','INTERNAL_BUSINESS_DATA,POLITICAL_SENSITIVE')],
    policy='完整输出同事务插入；每项至少一个来源由服务校验。双标签同位置是不同检测；不同输出项不按相似文本/来源静默合并。摘录从有权不可变源读取，不保存模型伪摘录；历史/候选访问受拥有版本控制')
table('sensitive_detection_source','B-07/D-03','V012','敏感检测的完整无序来源集合',[
    ref('detection_id','检测条目'),ref('locator_id','原始生成输入内精确来源')],pk='detection_id,locator_id',
    indexes=[idx('detection_source_reverse','locator_id,detection_id','定位引用与删除检查')],fks=[fk('detection_id','sensitive_detection'),fk('locator_id','source_locator')],
    policy='同检测不重复来源；服务校验同会议且在检测input固定确认/材料集合中；与检测原子写入，零来源拒绝。先删此关系再清检测/来源；不授予源权限')
table('background_note','B-07/D-03','V012','生成背景补充正文；不冒充会议发言/决定/行动',[
    ident(),ref('set_id','所属不可变要素集'),ref('input_id','本条原始生成输入；复制保留'),num('ordinal','目录1起顺序'),text('body','必要的生成背景正文；非原始模型JSON或源文副本')],
    indexes=[idx('background_order','set_id,ordinal','背景目录顺序唯一',True)],fks=[fk('set_id','element_set'),fk('input_id','generation_input')],checks=['ordinal > 0'],
    policy='非空文本与至少一精确来源由验证器/事务保障；无新产品字数上限。新版本复制保留origin input；不得独自证明重要会议结论/行动，历史保留，删除先删来源关系')
table('background_note_source','B-07/D-03','V012','背景补充的完整来源集合',[
    ref('note_id','背景条目'),ref('locator_id','该input内不可变来源')],pk='note_id,locator_id',
    indexes=[idx('background_source_reverse','locator_id,note_id','来源引用与删除')],fks=[fk('note_id','background_note'),fk('locator_id','source_locator')],
    policy='PK为无序集合去重；同会议/固定输入成员校验，非空集合与正文原子提交。来源角色由固定图推导，不能因输出声称会议依据而改角色；读时独立源ACL')
table('action_suggestion','B-07/D-03','V012','未核实的模型负责人原文和日期建议；与接受字段分离',[
    ref('element_id','确属ACTION的生成要素'),ref('input_id','这条建议原始生成输入；复制保留'),text('owner_mention','原始负责人提议；不是企业ID/授权，非空值须非空白',True),col('due_on','date','结构有效但尚未核实的建议日期；未知NULL',True)],
    pk='element_id',fks=[fk('element_id','minutes_element'),fk('input_id','generation_input')],
    policy='每个生成ACTION一行，可双NULL；源复用该element证据不伪造字段级依据。模型值不写入action_item接受字段；人工明确保存/复核形成新要素/action_item及新ACTION_FIELDS断言。外部负责人规则pending；人物选择不授予会议权限')
table('result_review','B-07/D-03/B-08','V012','人类对精确目标内容和完整证据集合的不可变断言',[
    ident(),ref('meeting_id','所属会议'),num('review_no','持meeting根锁分配的单调复核序号'),code('review_kind','来源支持或行动字段确认'),ref('element_id','要素目标；与正文目标二选一',True),ref('minutes_revision_id','正文目标；与要素二选一',True),text('target_anchor','SOURCE_SUPPORT精确范围的版本化规范anchor；ACTION_FIELDS为NULL',True),
    fingerprint('target_hash','含精确目标ID/版本/全文/anchor或完整行动元组的摘要'),fingerprint('source_set_hash','完整证据/定位/来源角色与版本内容hash集合摘要；空集显式编码'),col('hash_profile',KEY,'可恢复不可变规范编码/域分离配置版本，不用latest'),code('outcome','SUPPORTED/PARTIAL/UNSUPPORTED或CONFIRMED'),col('reviewed_by',KEY,'服务器验证的企业稳定身份；事件时值，不因目录删除丢历史'),ts('reviewed_at','服务器UTC微秒复核时间')],
    indexes=[idx('review_sequence','meeting_id,review_no','同会议复核顺序唯一',True),idx('review_element','element_id,review_kind,review_no','当前目标复核历史'),idx('review_minutes','minutes_revision_id,review_kind,review_no','正文范围复核历史')],
    fks=[fk('meeting_id','meeting'),fk('element_id','minutes_element'),fk('minutes_revision_id','minutes_revision')],
    checks=['review_no > 0','(element_id IS NULL) <> (minutes_revision_id IS NULL)',enum('review_kind','SOURCE_SUPPORT,ACTION_FIELDS'),"(review_kind='SOURCE_SUPPORT' AND target_anchor IS NOT NULL AND outcome IN ('SUPPORTED','PARTIAL','UNSUPPORTED')) OR (review_kind='ACTION_FIELDS' AND element_id IS NOT NULL AND target_anchor IS NULL AND outcome='CONFIRMED')","REGEXP_LIKE(target_hash, '^[0-9a-f]{64}$', 'c')","REGEXP_LIKE(source_set_hash, '^[0-9a-f]{64}$', 'c')"],
    policy='仅明确内容级人工意图可写；active身份/owner及源ACL、完整来源/目标/hash、ACTION实际类型由拥有事务重验，非FK reviewed_by保留事件时身份。最新相同目标/范围/来源review_no决定断言，不能取任意历史SUPPORTED；根revision/fence前进、final指针清空。新target不继承旧review信用；内容级adopt+review可原子创建新actor/time/hash断言。最终确认只读冻结membership；不把人工断言当语义真值或权限')
table('result_review_evidence','B-07/D-03/B-08','V012','复核实际使用的完整证据集合，不只存支持项',[
    ref('review_id','不可变复核'),ref('evidence_id','精确不可变目标的证据')],pk='review_id,evidence_id',
    indexes=[idx('review_evidence_reverse','evidence_id,review_id','历史依赖与删除')],fks=[fk('review_id','result_review'),fk('evidence_id','evidence_link')],
    policy='集合必须等于目标范围相关全部证据；空集合显式hash但不得SOURCE_SUPPORT=SUPPORTED。FK仅证存在；同会议/同target/范围/来源版本/hash由事务重算。不可在复核提交后追加/删减；删除时先解除本关系')
table('final_result_review','B-08','V013','最终确认冻结精确复核成员，不读取以后最新复核',[
    ref('confirmation_id','本次最终确认'),ref('review_id','本次选用的精确复核')],pk='confirmation_id,review_id',
    indexes=[idx('final_review_reverse','review_id,confirmation_id','历史确认依赖')],fks=[fk('confirmation_id','final_confirmation'),fk('review_id','result_review')],
    policy='与V013 final_confirmation同时建表并立即有两个physical FK；不新增deferredFK。最终确认同事务验证同会议/精确minutes和element_set/当前适用复核，成员与完整复核投影进入source_manifest_hash；以后新断言不能改旧membership。先删除membership再清confirmation/review')
for name in ('minutes_element','evidence_link'):
    t=next(t for t in TABLES if t['name']==name)
    next(c for c in t['cols'] if c['name']=='support_state')['meaning']='该版本创建时的冻结分类；不能单独证明当前人工复核充分性，参见result_review'
    t['policy'] += '；B00-1.3当前人工支持由精确result_review决定，旧support_state不原地改写；新目标内容/来源/版本使旧断言失效'
next(t for t in TABLES if t['name']=='action_item')['policy']='仅人工明确保存后的接受字段；生成初始owner/date均NULL，模型提议在action_suggestion。人工字段保存建立新要素版本并写新ACTION_FIELDS复核，缺少截止不强补；外部负责人规则未定不启用；角色/名字/负责人不授权'
final=next(t for t in TABLES if t['name']=='final_confirmation')
next(c for c in final['cols'] if c['name']=='source_manifest_hash')['meaning']='冻结证据/来源及final_result_review精确成员与完整复核投影摘要；不得重查最新复核'
final['policy'] += '；B00-1.3同事务写final_result_review，历史读取该冻结成员，不随新review改变；全部业务/权限/敏感/来源检查仍由B08负责'


# B00-1.4-proposed: unassigned correction; historical sources remain sealed.
table('source_context','D-01/B-07','UNASSIGNED-D01','不可变完整来源选择；生成与人工选择显式分型',[
    ident(),ref('meeting_id','所属会议'),code('origin_kind','GENERATION_INPUT 或 MANUAL_SELECTION'),ref('generation_input_id','仅生成上下文的精确输入；人工为NULL',True),ref('transcript_confirmation_id','冻结已确认转写'),ref('material_snapshot_id','冻结材料集合；NULL显式EMPTY',True),col('catalog_version',KEY,'可恢复不可变CATALOG版本'),fingerprint('catalog_sha256','归档CATALOG原始字节SHA256'),ts('created_at','服务器UTC微秒创建时间')],
    indexes=[idx('context_generation','generation_input_id','每个生成输入至多一个上下文；人工NULL可多行',True),idx('context_meeting','meeting_id,id','会议完整历史枚举')],
    fks=[fk('meeting_id','meeting'),fk('generation_input_id','generation_input'),fk('transcript_confirmation_id','transcript_confirmation'),fk('material_snapshot_id','material_snapshot')],
    checks=[enum('origin_kind','GENERATION_INPUT,MANUAL_SELECTION'),"(origin_kind='GENERATION_INPUT' AND generation_input_id IS NOT NULL) OR (origin_kind='MANUAL_SELECTION' AND generation_input_id IS NULL)","REGEXP_LIKE(catalog_sha256, '^[0-9a-f]{64}$', 'c')"],
    policy='完整不可变来源快照；服务验证同会议/确认状态/完整材料/精确配置依赖和权限。生成input与context内容一致；人工不伪造生成。持根锁与使用者及文件账本原子提交；历史引用RESTRICT，无自动TTL')
table('source_context_material','D-01/B-07','UNASSIGNED-D01','上下文内每个材料版本唯一明确选择解析文档',[
    ref('context_id','不可变来源上下文'),ref('material_revision_id','完整快照材料版本'),ref('parsed_document_id','该材料的精确解析输出；不取latest')],pk='context_id,material_revision_id',
    indexes=[idx('selection_document','parsed_document_id,context_id','旧解析输出仅被复制证据引用仍受保护'),idx('selection_material','material_revision_id,context_id','材料历史依赖')],
    fks=[fk('context_id','source_context'),fk('material_revision_id','material_revision'),fk('parsed_document_id','parsed_document')],
    policy='服务验证与快照items双射、document确属revision、READY/文本完整和唯一profile；FK只证存在。完整集合原子INSERT后不可增删；全图解除后才可清理')
table('parsed_document_profile','D-01','UNASSIGNED-D01','解析文档的明确格式及不可变解析配置归档引用',[
    ref('document_id','精确解析文档'),code('format','PDF/PPT/WORD/TXT；不能从unit种类或文件名猜测'),col('parser_config_version',KEY,'可恢复归档PARSER_CONFIG版本'),fingerprint('parser_config_sha256','解析配置归档原始字节SHA256')],pk='document_id',
    indexes=[idx('profile_parser','parser_config_version,document_id','配置历史依赖枚举')],fks=[fk('document_id','parsed_document')],
    checks=[enum('format','PDF,PPT,WORD,TXT'),"REGEXP_LIKE(parser_config_sha256, '^[0-9a-f]{64}$', 'c')"],
    policy='与实际解析输出原子创建；服务验证parser_version等于归档version、格式和映射匹配；缺少sidecar为legacy unavailable，禁止推测/补造READY。配置归档保留由领域负责，非跨库FK')
for name in ('minutes_element','evidence_link'):
    t=next(t for t in TABLES if t['name']==name)
    t['cols'].append(ref('source_context_id','不可变来源选择；NULL仅历史未知；v3新写必填由服务验证',True))
    t['indexes'].append(idx(name+'_context','source_context_id,id','历史来源选择反向依赖'))
    t['fks'].append(fk('source_context_id','source_context'))
    t['policy'] += '；B00-1.4-proposed来源basis由自身source_context_id确定，人工改写不声称模型生成；evidence自身上下文优先，不能从要素/正文/ACTION建议推断。新v3写NULL由应用拒绝；旧NULL明确不可恢复'
    if name == 'minutes_element':
        t['policy'] += '；所有纳入要素basis无论证据数量均为域内根，完整选择/profile须验证与保留；根精确作用域见R1上下文根，不伪造证据或支持信用'


def cname(t, n): return f"ck_{t['name']}_{n}"
def fname(t, n): return f"fk_{t['name']}_{n}"
def sql():
    result = [f'-- {VERSION}: REVIEW ONLY; never place this file in Flyway locations.',
              '-- All future tables exist ONLY in the disposable review database.',
              "SET NAMES utf8mb4;", "SET time_zone = '+00:00';"]
    for t in TABLES:
        lines=[]
        for c in t['cols']:
            s=f"  `{c['name']}` {c['type']} {'NULL' if c['nullable'] else 'NOT NULL'}"
            if c['default'] is not None: s+=' DEFAULT '+str(c['default'])
            if t['name']=='audit_event' and c['name']=='id': s+=' AUTO_INCREMENT'
            lines.append(s)
        lines.append('  PRIMARY KEY ('+','.join(t['pk'])+')')
        for i in t['indexes']:
            lines.append('  '+('UNIQUE ' if i['unique'] else '')+'INDEX '+i['name']+' ('+','.join(i['cols'])+')')
        for n,c in enumerate(t['checks']): lines.append(f'  CONSTRAINT {cname(t,n)} CHECK ({c})')
        result.append(f"-- Owner {t['task']}; planned {t['migration']}: {t['purpose']}")
        result.append('CREATE TABLE '+t['name']+' (\n'+',\n'.join(lines)+'\n) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;')
    for t in TABLES:
        for n,f in enumerate(t['fks']):
            result.append(f"ALTER TABLE {t['name']} ADD CONSTRAINT {fname(t,n)} FOREIGN KEY ({','.join(f['cols'])}) REFERENCES {f['target']} ({','.join(f['remote'])}) ON UPDATE RESTRICT ON DELETE RESTRICT;")
    result.append('INSERT INTO task_outbox_sequence(id,next_id) VALUES(1,1);')
    return '\n\n'.join(result)+'\n'

def dictionary():
    out=[f'# AMA 数据字典 — {VERSION}', '', '生成源：`tools/checks/database_design.py`。与审阅 DDL 同步，实际 MySQL 元数据另行比对；生成本身不构成验证。', '',
         '全表 InnoDB / utf8mb4_0900_ai_ci；稳定标识使用 utf8mb4_0900_bin（NO PAD），UUID/协议码/哈希用 ascii_bin。每字段的类型中明确覆盖排序规则。DATETIME(6) 一律 UTC；DATE 为 Asia/Shanghai 业务日。默认值 `无` 意味必须由应用明确赋值（可空字段默认 NULL）。主键唯一性的业务依据为稳定实体标识或明确复合业务键；UUID 不承载顺序。LONGTEXT 的物理容量不是产品字数限制。', '',
         '全部 FK 均 ON UPDATE RESTRICT / ON DELETE RESTRICT，根与历史不级联；拥有任务负责在同事务验证同会议/同所有者、状态及快照成员关系。字典中无 FK 的 typed ID 和循环当前指针见设计文档“逻辑关系与删除”，不可按裸 ID 绕过鉴权。', '',
         PROVENANCE_ROOT_RULE, '', '## 表清单与分期', '', '|表|任务|计划迁移|用途|','|---|---|---|---|']
    for t in TABLES: out.append(f"|{t['name']}|{t['task']}|{t['migration']}|{t['purpose']}|")
    for t in TABLES:
        out.extend(['',f"## {t['name']}",'',f"{t['purpose']}。所属 {t['task']}，计划 {t['migration']}。",'', '|字段|MySQL 类型/排序规则|可空|默认|含义|','|---|---|---|---|---|'])
        for c in t['cols']: out.append(f"|{c['name']}|`{c['type']}`|{'是' if c['nullable'] else '否'}|{c['default'] if c['default'] is not None else '无'}|{c['meaning']}|")
        out.extend(['', '主键：`'+','.join(t['pk'])+'`。'])
        for i in t['indexes']: out.append(f"- {'唯一约束' if i['unique'] else '索引'} `{i['name']}({','.join(i['cols'])})`：{i['reason']}。")
        for n,c in enumerate(t['checks']): out.append(f"- CHECK `{cname(t,n)}`：`{c}`。")
        for n,f in enumerate(t['fks']): out.append(f"- FK `{fname(t,n)}`：`{','.join(f['cols'])}` → `{f['target']}({','.join(f['remote'])})`。{f['note']}。")
        out.extend(['','更新/删除与完整性：'+t['policy']+'。'])
    return '\n'.join(out)+'\n'

def er():
    out=['## 附录：完整 ER（由同一字段清单生成）','', PROVENANCE_ROOT_RULE, '', 'PK/FK 明确标在字段上；每条实体存在于字典和 DDL。`o|` 表示子行允许 NULL；`||` 表示子行必有父；父到子默认零到多（复合唯一索引的更紧基数见字典）。关系标签注明实际 FK，当前指针/typed 逻辑关系在正文另表。', '', '```mermaid','erDiagram']
    for t in TABLES:
        fcols={c for f in t['fks'] for c in f['cols']}
        out.append('    '+t['name']+' {')
        for c in t['cols']:
            marks=[]
            if c['name'] in t['pk']: marks.append('PK')
            if c['name'] in fcols: marks.append('FK')
            out.append('        '+re.sub('[^a-zA-Z0-9_]','_',c['type'].split()[0])+' '+c['name']+(' '+','.join(marks) if marks else ''))
        out.append('    }')
    for t in TABLES:
        for n,f in enumerate(t['fks']):
            optional=any(c['nullable'] for c in t['cols'] if c['name'] in f['cols'])
            unique_child=any(set(i)==set(f['cols']) for i in [t['pk']]+[i['cols'] for i in t['indexes'] if i['unique']])
            cardinality='o|' if unique_child else 'o{'
            out.append(f"    {f['target']} {'o|' if optional else '||'}--{cardinality} {t['name']} : {fname(t,n)}")
    return '\n'.join(out+['```',''])

def generated():
    return {'docs/database/ama-schema-draft.sql':sql(),'docs/database/ama-data-dictionary.md':dictionary()}

def record_mysql(container, query, result):
    # Append-only raw query evidence; SQL is synthetic design/metadata only.
    out=ROOT/'artifacts/phase-b/b00-provenance-design/mysql-raw.jsonl'
    with out.open('a') as stream:
        stream.write(json.dumps(dict(container=container,query=query,exit_code=result.returncode,stdout=result.stdout,stderr=result.stderr),ensure_ascii=False)+'\n')

def mysql(container, query):
    result=subprocess.run(['docker','exec','-i',container,'sh','-c',
        'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h127.0.0.1 --user="$MYSQL_USER" "$MYSQL_DATABASE" --batch --raw --skip-column-names'],input=query,text=True,capture_output=True)
    record_mysql(container,query,result)
    result.check_returncode()
    return result.stdout

def check_ast(expression):
    """Compare CHECK semantics while preserving AND/OR grouping and literal case.

    MySQL adds redundant parentheses, identifier quotes and character introducers,
    and returns backslash-escaped quotes in CHECK_CLAUSE. This restricted parser
    accepts only the grammar used by this review schema, and fails closed otherwise.
    """
    source=expression.replace('`','').replace("\\'", "'")
    source=re.sub(r'\b_(?:ascii|utf8mb4)\b','',source)
    tokens=re.findall(r"'(?:[^']|'')*'|<=|>=|<>|[(),=<>]|[A-Za-z_][A-Za-z_0-9]*|[0-9]+",source)
    assert ''.join(tokens)==re.sub(r'\s+','',source), 'Unsupported CHECK grammar'
    position=0
    def peek(): return tokens[position].lower() if position<len(tokens) else None
    def take(expected=None):
        nonlocal position
        value=tokens[position]; position+=1
        assert expected is None or value.lower()==expected, (value,expected)
        return value
    def atom():
        if peek()=='(':
            take('('); value=disjunction(); take(')'); return value
        value=take()
        if peek()=='(':
            assert value.lower()=='regexp_like', 'Unsupported CHECK function'
            take('('); args=[disjunction()]
            while peek()==',': take(); args.append(disjunction())
            take(')'); return ('call',value.lower(),tuple(args))
        return ('literal',value) if value.startswith("'") or value.isdigit() else ('identifier',value.lower())
    def comparison():
        left=atom(); op=peek()
        if op in ('=','<>','>','<','>=','<='):
            take(); return (op,left,atom())
        if op=='is':
            take(); negate=False
            if peek()=='not': take(); negate=True
            take('null'); return ('is_not_null' if negate else 'is_null',left)
        if op=='in':
            take(); take('('); values=[atom()]
            while peek()==',': take(); values.append(atom())
            take(')'); return ('in',left,tuple(values))
        if op=='between':
            take(); low=atom(); take('and'); high=atom(); return ('between',left,low,high)
        return left
    def conjunction():
        values=[comparison()]
        while peek()=='and': take(); values.append(comparison())
        # Associativity permits MySQL's extra parentheses, but never drops grouping
        # across different AND/OR operators.
        flat=[]
        for v in values: flat.extend(v[1:] if v[0]=='and' else [v])
        return tuple(['and']+flat) if len(flat)>1 else flat[0]
    def disjunction():
        values=[conjunction()]
        while peek()=='or': take(); values.append(conjunction())
        flat=[]
        for v in values: flat.extend(v[1:] if v[0]=='or' else [v])
        return tuple(['or']+flat) if len(flat)>1 else flat[0]
    result=disjunction()
    assert position==len(tokens), 'Unparsed CHECK expression'
    return result

def verify(container, metadata_only=False):
    assert check_ast('a=1 OR (b=2 AND c=3)') != check_ast('(a=1 OR b=2) AND c=3')
    assert check_ast("state='READY'") != check_ast("state='ready'")
    assert check_ast('((a=1)) AND b IS NOT NULL') == check_ast('a=1 AND (b IS NOT NULL)')
    local = ROOT/'.tools/mysql-fixture'/container/'env.sh'
    if not re.fullmatch(r'ama-test-b01-[a-f0-9]{12}',container) or not local.is_file():
        raise SystemExit('Refusing: fixture must be created in THIS worktree by mysql_fixture.py')
    meta=json.loads(subprocess.check_output(['docker','inspect',container],text=True))[0]
    initial=json.loads((ROOT/'artifacts/phase-b/b00-provenance-design/fixture-initial.json').read_text())
    assert initial['owned_worktree']==str(ROOT) and initial['container']==container
    assert meta['Id']==initial['id'] and meta['Image']==initial['image_id']
    assert meta['Config']['Image']==initial['image_reference'] and meta['Mounts']==initial['mounts']
    assert initial['initial_tables']==0 and initial['native_aio']==0
    assert initial['resources']=={k:meta['HostConfig'][k] for k in ('NanoCpus','Memory','MemorySwap','PidsLimit')}
    assert initial['resources']==dict(NanoCpus=2000000000,Memory=1073741824,MemorySwap=1073741824,PidsLimit=256)
    assert meta['HostConfig']['AutoRemove'] is False
    assert mysql(container,'SELECT @@innodb_use_native_aio;').strip()=='0'
    assert meta['Config']['Labels'].get('ama.test-fixture')=='b01'
    assert all(x['HostIp']=='127.0.0.1' for x in meta['NetworkSettings']['Ports']['3306/tcp'])
    for path, expected in generated().items(): assert (ROOT/path).read_text()==expected, path+' drift'
    design=(ROOT/'docs/superpowers/specs/2026-10-21-ama-database-design.md').read_text()
    assert design.endswith(er()), 'ER drift'
    # Never clear a database. Metadata-only rechecks THIS owned fixture without DDL.
    if not metadata_only:
        assert mysql(container,"SELECT count(*) FROM information_schema.tables WHERE table_schema=DATABASE();").strip()=='0', 'Fixture not empty: refusing to alter'
    version=mysql(container,'SELECT VERSION();').strip()
    assert version == '8.4.6', version
    if not metadata_only: mysql(container,sql())
    queries={
      'tables':"SELECT JSON_OBJECT('table',TABLE_NAME,'engine',ENGINE,'collation',TABLE_COLLATION) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME;",
      'columns':"SELECT JSON_OBJECT('table',TABLE_NAME,'column',COLUMN_NAME,'ordinal',ORDINAL_POSITION,'type',COLUMN_TYPE,'nullable',IS_NULLABLE,'default',COLUMN_DEFAULT,'collation',COLLATION_NAME,'extra',EXTRA) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,ORDINAL_POSITION;",
      'indexes':"SELECT JSON_OBJECT('table',TABLE_NAME,'name',INDEX_NAME,'unique',1-NON_UNIQUE,'seq',SEQ_IN_INDEX,'column',COLUMN_NAME,'direction',COLLATION,'prefix',SUB_PART,'type',INDEX_TYPE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX;",
      'checks':"SELECT JSON_OBJECT('table',t.TABLE_NAME,'name',t.CONSTRAINT_NAME,'clause',c.CHECK_CLAUSE,'enforced',t.ENFORCED) FROM information_schema.TABLE_CONSTRAINTS t JOIN information_schema.CHECK_CONSTRAINTS c ON c.CONSTRAINT_SCHEMA=t.CONSTRAINT_SCHEMA AND c.CONSTRAINT_NAME=t.CONSTRAINT_NAME WHERE t.CONSTRAINT_SCHEMA=DATABASE() AND t.CONSTRAINT_TYPE='CHECK' ORDER BY t.TABLE_NAME,t.CONSTRAINT_NAME;",
      'foreign_keys':"SELECT JSON_OBJECT('table',k.TABLE_NAME,'name',k.CONSTRAINT_NAME,'column',k.COLUMN_NAME,'ordinal',k.ORDINAL_POSITION,'target',k.REFERENCED_TABLE_NAME,'remote',k.REFERENCED_COLUMN_NAME,'delete',r.DELETE_RULE,'update',r.UPDATE_RULE) FROM information_schema.KEY_COLUMN_USAGE k JOIN information_schema.REFERENTIAL_CONSTRAINTS r ON r.CONSTRAINT_SCHEMA=k.CONSTRAINT_SCHEMA AND r.CONSTRAINT_NAME=k.CONSTRAINT_NAME WHERE k.CONSTRAINT_SCHEMA=DATABASE() AND k.REFERENCED_TABLE_NAME IS NOT NULL ORDER BY k.TABLE_NAME,k.CONSTRAINT_NAME,k.ORDINAL_POSITION;"}
    actual={k:[json.loads(x) for x in mysql(container,q).splitlines()] for k,q in queries.items()}
    assert {x['table'] for x in actual['tables']}=={t['name'] for t in TABLES}
    assert all(x['engine']=='InnoDB' and x['collation']=='utf8mb4_0900_ai_ci' for x in actual['tables'])
    expected_cols=[]
    for t in TABLES:
        for n,c in enumerate(t['cols'],1):
            typ=c['type'].split(' CHARACTER')[0].split(' COLLATE')[0]
            coll=None
            if re.match(r'(char|varchar|longtext)',typ): coll=(c['type'].split(' COLLATE ')[1] if ' COLLATE ' in c['type'] else 'utf8mb4_0900_ai_ci')
            expected_cols.append(dict(table=t['name'],column=c['name'],ordinal=n,type=typ,nullable='YES' if c['nullable'] else 'NO',default=None if c['default'] is None else str(c['default']).strip("'"),collation=coll,extra='auto_increment' if t['name']=='audit_event' and c['name']=='id' else ''))
    assert sorted(expected_cols,key=lambda x:(x['table'],x['ordinal']))==actual['columns'], 'Column metadata mismatch'
    known_indexes=[]
    for t in TABLES:
        defs=[dict(name='PRIMARY',cols=t['pk'],unique=True)]+t['indexes']
        # InnoDB synthesizes FK indexes when no existing left prefix supports it.
        for n,f in enumerate(t['fks']):
            if not any(i['cols'][:len(f['cols'])]==f['cols'] for i in defs): defs.append(dict(name=fname(t,n),cols=f['cols'],unique=False))
        for i in defs:
            for n,c in enumerate(i['cols'],1): known_indexes.append(dict(table=t['name'],name=i['name'],unique=int(i['unique']),seq=n,column=c,direction='A',prefix=None,type='BTREE'))
    assert sorted(known_indexes,key=lambda x:(x['table'],x['name'],x['seq']))==sorted(actual['indexes'],key=lambda x:(x['table'],x['name'],x['seq'])), 'Index metadata mismatch'
    expected_fk=[]
    for t in TABLES:
        for n,f in enumerate(t['fks']):
            for seq,(c,r) in enumerate(zip(f['cols'],f['remote']),1): expected_fk.append(dict(table=t['name'],name=fname(t,n),column=c,ordinal=seq,target=f['target'],remote=r,delete='RESTRICT',update='RESTRICT'))
    assert sorted(expected_fk,key=lambda x:(x['table'],x['name'],x['ordinal']))==actual['foreign_keys'], 'FK metadata mismatch'
    checks={(x['table'],x['name']):check_ast(x['clause']) for x in actual['checks']}
    expected_checks={(t['name'],cname(t,n)):check_ast(c) for t in TABLES for n,c in enumerate(t['checks'])}
    assert checks==expected_checks, 'CHECK metadata mismatch: '+repr({k:(v,checks.get(k)) for k,v in expected_checks.items() if checks.get(k)!=v})
    assert all(x['enforced']=='YES' for x in actual['checks'])
    # Behavioral negative checks on independent leaf roots, not a duplicate of generation.
    for statement in ["INSERT INTO storage_backend VALUES ('bad','invalid','WRITABLE',1);", "INSERT INTO identity_session(token_hash,enterprise_id,session_generation,csrf_token,issued_at,expires_at) VALUES (REPEAT('a',64),'missing',0,REPEAT('a',43),'2026-01-01','2026-01-02');"]:
        p=subprocess.run(['docker','exec','-i',container,'sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h127.0.0.1 --user="$MYSQL_USER" "$MYSQL_DATABASE" --batch'],input=statement,text=True,capture_output=True)
        record_mysql(container,statement,p)
        assert p.returncode!=0 and ('3819' in p.stderr or '1452' in p.stderr), 'Expected CHECK/FK rejection'
    counts=dict(tables=len(actual['tables']),columns=len(actual['columns']),index_definitions=len({(x['table'],x['name']) for x in actual['indexes']}),index_columns=len(actual['indexes']),checks=len(actual['checks']),foreign_key_constraints=len({(x['table'],x['name']) for x in actual['foreign_keys']}),foreign_key_columns=len(actual['foreign_keys']))
    evidence=dict(design_version=VERSION,mysql_version=version,container=container,image=meta['Config']['Image'],ddl_sha256=hashlib.sha256(sql().encode()).hexdigest(),counts=counts,metadata=actual,behavior=['invalid storage enum rejected (3819)','unknown identity FK rejected (1452)','CHECK comparer preserves operator grouping and literal case'],scope='DDL and all metadata only; not domain/concurrency/performance/integration acceptance')
    # Preserve all adopted 1.1/1.2 evidence bytes; this edition has its own capture.
    path=ROOT/'docs/acceptance/b/database-material-provenance-addendum-metadata-evidence.json'
    if path.exists():
        assert json.loads(path.read_text()) == evidence, 'Existing evidence differs; preserve before new checkpoint'
    else:
        path.write_text(json.dumps(evidence,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({k:v for k,v in evidence.items() if k!='metadata'},ensure_ascii=False,indent=2))

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action',choices=['generate','verify'])
    parser.add_argument('--container')
    parser.add_argument('--metadata-only',action='store_true',help='Recheck the same owned fixture without running DDL; never resets data')
    args=parser.parse_args()
    if args.action=='generate':
        for path,value in generated().items():
            p=ROOT/path; p.parent.mkdir(parents=True,exist_ok=True); p.write_text(value)
        p=ROOT/'docs/superpowers/specs/2026-10-21-ama-database-design.md'
        if p.exists(): p.write_text(p.read_text().split('## 附录：完整 ER')[0]+er())
        print(f'{VERSION}: {len(TABLES)} tables, {sum(len(t["cols"]) for t in TABLES)} columns generated')
    else:
        if not args.container: parser.error('--container required')
        verify(args.container,args.metadata_only)
