"""Business-level table catalog; validates field names against the data dictionary."""

from pathlib import Path
import re


GROUPS = [
    ("身份、会议与访问", "用户与会议的归属、名单、授权和检索", """
department|enterprise_id,parent_id,display_name|保存企业部门及上下级关系，支撑人员目录展示。
enterprise_identity|enterprise_id,department_id,display_name,status|保存企业账号投影及有效状态，作为负责人和参会人的身份依据。
role_assignment|enterprise_id,role|记录平台管理角色，管理职责与会议正文访问权分开。
identity_session|token_hash,enterprise_id,expires_at,revoked_at|管理登录会话、有效期与撤销状态。
meeting|id,owner_id,stage,lifecycle|保存会议主记录、唯一负责人和业务生命周期，关联各环节有效版本。
meeting_info_revision|id,meeting_id,name,actual_meeting_date|保留会议名称、分类、摘要和实际日期的历史版本。
roster_revision|id,meeting_id,revision,state|区分名单草稿与已确认名单，保存名单版本。
roster_member|roster_id,member_id,enterprise_id,display_name|保存某一名单版本的成员内容，名单本身不直接授权。
meeting_access_grant|meeting_id,enterprise_id,action,roster_id|保存会议实际生效的用户访问权限及其名单依据。
info_suggestion|id,meeting_id,proposed_info,state|保存AI提出的会议信息建议及采纳状态。
meeting_search_projection|meeting_id,name_search,number_search,category_id|提供会议列表的名称、编号和分类检索数据。
meeting_person_search|meeting_id,roster_id,member_id,name_search|提供已确认参会人姓名检索，排除草稿和AI候选。
"""),
    ("文件、采集与媒体", "主文件、上传、录音和时间轴", """
storage_backend|id,storage_type,status,config_revision|注册本地或S3存储后端及配置版本，凭证由外部配置管理。
file_object|id,meeting_id,backend_id,object_key,sha256|登记受管文件的位置、归属和完整性信息，文件内容保存在存储后端。
file_reference|file_id,owner_kind,owner_id,purpose|记录各业务对象对文件的使用关系，支撑安全清理。
upload_session|id,meeting_id,backend_id,state,completed_file_id|管理固定存储后端的上传会话、恢复与完成状态。
upload_part|upload_id,part_no,size_bytes,sha256|保存上传分片及校验结果，支撑断点续传。
file_cleanup|id,file_id,state,attempt|登记文件删除和无引用文件清理，保留失败重试状态。
input_batch|id,meeting_id,kind,input_fingerprint|冻结一次正式处理的主文件批次与输入摘要。
input_batch_file|batch_id,ordinal,file_id,sha256|保存批次内文件顺序及内容校验，关联批次与文件。
input_batch_fingerprint_profile|batch_id,profile|记录批次输入摘要使用的算法规则，保证历史输入可解释。
media_source|id,batch_id,normalized_file_id,sample_count|保存标准化音频及采样信息，作为媒体处理依据。
media_source_span|media_id,source_file_id,source_start_sample,global_start_sample|记录原录音与合并音频之间的时间轴对应关系。
recording_session|id,meeting_id,backend_id,state|管理在线录音会话、采集者和固定存储后端。
recording_part|id,session_id,ordinal,highest_contiguous_seq|管理录音分段顺序和已连续保存范围。
recording_chunk|part_id,seq,file_id,sha256|记录已保存录音分片及完整性，作为保存确认依据。
subtitle_revision|session_id,segment_id,revision,state|保留临时与稳定字幕版本，与正式确认转写分开。
"""),
    ("背景材料与来源", "材料引用、解析版本和证据定位", """
material_reference|id,meeting_id,kind,external_id,file_id|保存会议对本地或知识库材料的逻辑关联。
material_revision|id,reference_id,external_version,content_hash|固定材料的具体源版本与内容，避免随源文件更新而变化。
material_snapshot|id,meeting_id,revision,input_fingerprint|冻结本次明确应用的材料集合。
material_snapshot_item|snapshot_id,material_revision_id,ordinal|记录材料快照包含的具体材料版本及顺序。
parsed_document|id,material_revision_id,parser_version,state|记录材料解析版本、结果与失败状态。
parsed_unit|document_id,ordinal,page_no,paragraph_no,body|保存解析文本及页码、段落等定位信息。
parsed_document_profile|document_id,format,parser_config_version|固定解析格式和配置规则，支撑结果复现。
source_context|id,meeting_id,generation_input_id,material_snapshot_id|冻结一次生成或人工选源时的完整来源范围。
source_context_material|context_id,material_revision_id,parsed_document_id|明确来源上下文中每份材料使用的解析版本。
source_locator|id,kind,segment_id,material_revision_id,page_no|定位转写片段、录音范围或材料页段。
evidence_link|id,locator_id,element_id,minutes_revision_id|将具体要素或正文内容关联到可核对的来源证据。
"""),
    ("转写与人工编辑", "转写输入、内容版本与确认", """
transcription_input|id,meeting_id,batch_id,material_snapshot_id|冻结导入、识别或重新增强的输入与配置。
transcript_revision|id,meeting_id,kind,parent_id,processing_input_id|区分原始、增强、人工与候选转写，保留版本关系。
transcript_segment|revision_id,segment_id,body,speaker_id|保存转写片段正文、说话人和来源定位。
transcript_confirmation|id,meeting_id,revision_id,confirmed_by|冻结人工确认的已保存转写，作为纪要生成依据。
transcript_segment_mark|revision_id,segment_id,mark|保存与具体转写版本绑定的片段标记。
speaker|id,meeting_id,display_name,enterprise_id|管理说话人标签与身份对应，独立于会议访问授权。
correction|id,revision_id,segment_id,state|保存针对具体转写片段的纠错建议与处理状态。
edit_operation|id,meeting_id,before_revision_id,after_revision_id|记录编辑与撤销操作，保留修改前后版本关系。
"""),
    ("纪要、要素与复核", "生成输入、候选、正文及人工判断", """
minutes_template|id,owner_id,name,current_revision_id|管理模板逻辑身份、归属和当前版本。
template_revision|id,template_id,revision,themes|保存内置或个人模板的历史版本与生成主题。
generation_input|id,meeting_id,transcript_confirmation_id,material_snapshot_id|冻结纪要生成使用的确认转写、材料、模板和相关设置。
element_set|id,meeting_id,revision|管理七类会议要素集合的独立版本。
minutes_element|id,set_id,category,body|保存结构化会议要素及其类别、内容和来源上下文。
action_item|element_id,owner_enterprise_id,owner_display,due_on|保存已编辑的行动项负责人和截止日期。
action_suggestion|element_id,input_id,owner_mention,due_on|单独保存模型提出的负责人及日期建议，等待核实。
minutes_revision|id,meeting_id,title,body,generation_input_id|保存纪要标题和正文的历史版本及生成来源。
generation_candidate|id,input_id,minutes_revision_id,state|保存待采用生成结果，保护已有人工编辑。
exclusion_revision|id,meeting_id,revision|管理敏感排除设置的集合版本。
sensitive_exclusion|revision_id,locator_id,category,reason|记录明确排除的来源内容及原因，不删除原始会议资料。
sensitive_detection|id,set_id,input_id,category|保留敏感内容检测目录，供用户判断是否排除。
sensitive_detection_source|detection_id,locator_id|记录敏感检测项关联的完整来源集合。
background_note|id,set_id,input_id,body|保存辅助背景说明，并与会议发言和结论区分。
background_note_source|note_id,locator_id|记录背景说明对应的材料或转写来源。
result_review|id,meeting_id,review_kind,target_hash,outcome|记录人工对精确内容和来源集合的核对结论。
result_review_evidence|review_id,evidence_id|固定一次人工复核实际使用的证据集合。
meeting_minutes_editor_guard|meeting_id,intent_epoch|记录编辑意图的版本，防止生成结果覆盖正在进行的人工编辑。
"""),
    ("成果发布与资产管理", "确认、发布、导出、专题及分享", """
final_confirmation|id,meeting_id,minutes_revision_id,transcript_confirmation_id|冻结最终确认的正文、转写、材料和相关版本集合。
final_result_review|confirmation_id,review_id|固定最终确认采用的人工复核记录。
publication|id,meeting_id,confirmation_id,state|管理会议发布根、目标位置和发布状态。
publication_submission|task_id,publication_id,confirmation_id,submission_no|绑定一次发布受理任务、确认版本和目标，保留受理历史。
publication_attempt|id,publication_id,operation_id,outcome|记录发布及结果查回的每次执行与结果。
external_document_mapping|publication_id,external_document_id,authority_state|建立本地成果与外部知识库文档的唯一映射。
publication_governance|id,publication_id,action,state|记录成果撤回、废止及其确认状态。
external_event_receipt|provider_id,event_id,payload_hash,processed_at|对外部状态事件去重，记录接收与处理情况。
export_job|id,meeting_id,format,output_file_id,state|管理绑定有效成果版本的Markdown、PDF或Word导出。
meeting_deletion|id,meeting_id,state,completed_at|记录会议删除申请、清理过程和完成事实。
topic|id,owner_id,name,description|管理个人专题的名称、说明和归属。
meeting_topic|topic_id,meeting_id|关联个人专题与符合归集条件的会议。
mail_job|id,meeting_id,minutes_revision_id,state|管理已保存成果的受控链接邮件发送任务。
mail_recipient|mail_job_id,enterprise_id,state,message_key|保存企业收件人、发送结果和查回标识。
"""),
    ("运营治理与质量", "反馈、声纹授权、评测及配置", """
corpus_feedback|id,meeting_id,category,state|管理会议语料反馈的提交、审核和处理结论。
feedback_segment|feedback_id,revision_id,segment_id|将反馈关联到精确转写版本和片段。
corpus_revision|id,feedback_id,content_file_id,purpose|保存审核通过的语料版本和允许用途。
voiceprint_consent|id,subject_id,purpose,state|记录声纹使用的明确授权、用途和撤销状态。
voiceprint_sample|id,consent_id,sample_file_id,state|管理与授权关联的声纹样本、模型信息和审核状态。
evaluation_dataset|id,version_key,manifest_file_id,purpose|登记受控评测数据集及版本。
evaluation_run|id,dataset_id,model_config_version,metrics|记录固定数据、模型和提示词条件下的评测结果。
configuration_revision|id,kind,revision,state|管理允许配置项的候选版本和审核状态。
configuration_release|id,configuration_id,evaluation_run_id,rollback_of_id|记录配置发布、评测依据及回滚关系。
"""),
    ("任务、集成与审计", "可靠执行、外部结果核验和操作追踪", """
audit_event|id,actor_id,action,resource_id,result|记录关键操作主体、对象和结果，支撑追踪与审计。
idempotency_record|scope_hash,operation,key_hash,request_hash|识别重复业务请求并返回一致结果，阻止同键不同内容。
task_subject|subject_kind,subject_id,business_version,cancel_epoch|统一标记业务对象版本和取消状态，阻止旧任务回写。
durable_task|id,task_type,subject_id,state,attempt|保存后台任务、执行状态、重试和结果引用。
task_attempt|task_id,attempt,owner,outcome|记录每次Worker领取和执行的诊断历史。
external_operation|id,operation_kind,business_key,state|登记可能产生外部效果的业务操作及结果核验状态。
external_execution|id,operation_id,execution_no,state|保留同一外部业务操作下的追加执行记录。
task_outbox_sequence|id,next_id|管理持久事件的提交顺序，避免消费遗漏。
task_outbox|sequence_id,event_type,subject_id,reference_id|持久保存任务和业务变更事件的引用。
outbox_consumer|consumer_id,cursor,epoch,lease_owner|保存事件消费进度与接管状态。
model_capacity|model,capacity,config_revision,enabled|保存经配置确认的模型并发额度与启用状态。
model_reservation|id,model,execution_id,safe_deadline|记录在途模型调用占用，避免超时后额度重复释放。
"""),
]


def catalog():
    return [(name, purpose, [tuple(line.split("|")) for line in raw.strip().splitlines()])
            for name, purpose, raw in GROUPS]


def validate(root: Path):
    text = (root / "docs/database/ama-data-dictionary.md").read_text(encoding="utf-8")
    definitions = {}
    for match in re.finditer(r"^## ([a-z][a-z0-9_]*)\n(.*?)(?=^## |\Z)", text, re.M | re.S):
        name, body = match.groups()
        definitions[name] = set(re.findall(r"^\|([a-z][a-z0-9_]*)\|`", body, re.M))
    seen = []
    for _, _, rows in catalog():
        for table, fields, _ in rows:
            assert table in definitions, table
            assert set(fields.split(",")) <= definitions[table], (table, fields)
            seen.append(table)
    assert len(seen) == len(set(seen)) == len(definitions) == 99
    assert set(seen) == set(definitions)
    return len(seen)


def render_catalog():
    out = []
    for number, (name, _, rows) in enumerate(catalog(), 1):
        out += [f"<details>", f"<summary><strong>{number}. {name}（{len(rows)}张表）</strong></summary>", "",
                "| 表名 | 概要字段 | 业务作用 |", "| --- | --- | --- |"]
        for table, fields, purpose in rows:
            field_list = "、".join(f"`{field}`" for field in fields.split(","))
            out.append(f"| `{table}` | {field_list} | {purpose} |")
        out += ["", "</details>", ""]
    return "\n".join(out)


if __name__ == "__main__":
    root = Path(__file__).resolve().parents[4]
    count = validate(root)
    doc = root / "docs/reviews/2026-10-26-企业智能会议助手项目简要方案.md"
    text = doc.read_text(encoding="utf-8")
    start, end = "<!-- DB_CATALOG_START -->", "<!-- DB_CATALOG_END -->"
    assert text.count(start) == text.count(end) == 1
    before, rest = text.split(start)
    _, after = rest.split(end)
    doc.write_text(before + start + "\n\n" + render_catalog() + "\n" + end + after, encoding="utf-8")
    print(f"Validated and summarized {count} tables in 8 domains.")
