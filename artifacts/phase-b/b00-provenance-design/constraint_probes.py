"""Actual MySQL relational probes. Synthetic rows, owned committed seed plus rollback-only cases; not app acceptance."""
import hashlib, importlib.util, json, pathlib, re, subprocess, uuid
ROOT=pathlib.Path(__file__).resolve().parents[3]; OUT=ROOT/'artifacts/phase-b/b00-provenance-design'
proof=json.loads((OUT/'fixture-initial.json').read_text()); container=proof['container']
meta=json.loads(subprocess.check_output(['docker','inspect',container],text=True))[0]
assert meta['Id']==proof['id'] and meta['Mounts']==proof['mounts'] and meta['Config']['Image']==proof['image_reference']
def uid(key): return str(uuid.uuid5(uuid.NAMESPACE_URL,'ama-b00-design-synthetic:'+key))
def val(v):
    if v is None:return 'NULL'
    if isinstance(v,int):return str(v)
    return "CONVERT(0x"+v.encode().hex()+" USING utf8mb4)"
def insert(table, **cols): return 'INSERT INTO '+table+'('+','.join(cols)+') VALUES('+','.join(val(v) for v in cols.values())+');'
now='2026-10-22 00:00:00.000001'; sha='a'*64
seed=[]
def add(table,**cols):seed.append(insert(table,**cols))
add('enterprise_identity',enterprise_id='synthetic-owner',display_name='测试身份',status='ACTIVE',verified_at=now)
for suffix in ('a','b'):add('meeting',id=uid('meeting-'+suffix),owner_id='synthetic-owner',stage='MINUTES_PENDING',created_at=now,updated_at=now)
m=uid('meeting-a'); mb=uid('meeting-b')
add('storage_backend',id='synthetic-local',storage_type='local',status='WRITABLE',config_revision=1)
add('file_object',id=uid('file'),meeting_id=m,backend_id='synthetic-local',storage_type='local',namespace='synthetic',object_key='synthetic.txt',display_name='合成.txt',size_bytes=3,sha256=sha,state='AVAILABLE',created_at=now)
add('input_batch',id=uid('batch'),meeting_id=m,kind='TRANSCRIPT',input_fingerprint=sha,base_revision=0,created_at=now)
add('input_batch_file',batch_id=uid('batch'),ordinal=1,file_id=uid('file'),sha256=sha)
add('transcription_input',id=uid('processing'),meeting_id=m,batch_id=uid('batch'),kind='IMPORT',material_mode='EMPTY',processing_config_version='synthetic-v1',parser_version='synthetic-v1',manifest_schema_version=1,config_manifest='{}',input_fingerprint=sha,base_revision=0,cancel_epoch=0,created_at=now)
add('transcript_revision',id=uid('transcript'),meeting_id=m,revision=1,kind='WORKING',batch_id=uid('batch'),input_fingerprint=sha,created_by='synthetic-owner',created_at=now,processing_input_id=uid('processing'))
add('transcript_segment',revision_id=uid('transcript'),segment_id=uid('segment'),ordinal=1,body='合成测试，不代表真实会议。',source_file_id=uid('file'),source_paragraph=1)
add('transcript_confirmation',id=uid('transcript-confirmation'),meeting_id=m,revision_id=uid('transcript'),content_hash=sha,confirmed_by='synthetic-owner',confirmed_at=now)
add('source_locator',id=uid('locator'),meeting_id=m,kind='TRANSCRIPT',transcript_revision_id=uid('transcript'),segment_id=uid('segment'))
add('exclusion_revision',id=uid('exclusion'),meeting_id=m,revision=1,created_at=now)
add('minutes_template',id=uid('template'),name='合成模板',name_normalized='合成模板',name_hash=sha,current_revision_id=uid('template-revision'),revision=1,created_at=now)
add('template_revision',id=uid('template-revision'),template_id=uid('template'),revision=1,name='合成模板',themes='["合成"]',theme_count=1,created_at=now)
add('generation_input',id=uid('input'),meeting_id=m,transcript_confirmation_id=uid('transcript-confirmation'),template_revision_id=uid('template-revision'),exclusion_revision_id=uid('exclusion'),prompt_version='synthetic-v1',model_config_version='synthetic-v1',base_revision=0,input_fingerprint=sha,scope='FULL')
add('element_set',id=uid('set'),meeting_id=m,revision=1,created_at=now)
add('minutes_element',id=uid('element'),set_id=uid('set'),category='ACTION',ordinal=1,body='合成行动',support_state='PARTIAL')
add('minutes_element',id=uid('other-element'),set_id=uid('set'),category='DISCUSSION',ordinal=1,body='合成讨论',support_state='PARTIAL')
add('action_item',element_id=uid('element'))
add('minutes_revision',id=uid('minutes'),meeting_id=m,revision=1,title='合成标题',title_origin='HUMAN',body='合成正文',generation_input_id=uid('input'),created_by='synthetic-owner',created_at=now)
add('evidence_link',id=uid('evidence'),locator_id=uid('locator'),element_id=uid('element'),support_state='PARTIAL',target_anchor='synthetic-anchor-v1')
add('evidence_link',id=uid('other-evidence'),locator_id=uid('locator'),element_id=uid('other-element'),support_state='PARTIAL',target_anchor='synthetic-other-anchor-v1')
for cat,n in [('INTERNAL_BUSINESS_DATA',1),('POLITICAL_SENSITIVE',2)]:
    add('sensitive_detection',id=uid('detection'+str(n)),set_id=uid('set'),input_id=uid('input'),category=cat,ordinal=1)
    add('sensitive_detection_source',detection_id=uid('detection'+str(n)),locator_id=uid('locator'))
    add('sensitive_exclusion',revision_id=uid('exclusion'),locator_id=uid('locator'),category=cat)
add('background_note',id=uid('note'),set_id=uid('set'),input_id=uid('input'),ordinal=1,body='背景：甲\n乙😀，仅背景补充。')
add('background_note_source',note_id=uid('note'),locator_id=uid('locator'))
add('action_suggestion',element_id=uid('element'),input_id=uid('input'),owner_mention='“王工”\n尚待映射😀',due_on='2026-11-01')
def review(**changes):
    row=dict(id=uid('review'),meeting_id=m,review_no=1,review_kind='SOURCE_SUPPORT',element_id=uid('element'),target_anchor='synthetic-anchor-v1',target_hash=sha,source_set_hash='b'*64,hash_profile='synthetic-probe-only',outcome='PARTIAL',reviewed_by='synthetic-owner',reviewed_at=now)
    row.update(changes);return insert('result_review',**row)
seed.append(review())
add('result_review_evidence',review_id=uid('review'),evidence_id=uid('evidence'))
add('meeting_info_revision',id=uid('info'),meeting_id=m,revision=1,created_by='synthetic-owner',created_at=now)
add('roster_revision',id=uid('roster'),meeting_id=m,revision=1,state='CONFIRMED',confirmed_at=now,confirmed_by='synthetic-owner')
add('final_confirmation',id=uid('final'),meeting_id=m,meeting_revision=1,info_revision_id=uid('info'),roster_revision_id=uid('roster'),transcript_confirmation_id=uid('transcript-confirmation'),minutes_revision_id=uid('minutes'),element_set_id=uid('set'),template_revision_id=uid('template-revision'),exclusion_revision_id=uid('exclusion'),source_manifest_hash=sha,input_fingerprint=sha,confirmed_by='synthetic-owner',confirmed_at=now)
add('final_result_review',confirmation_id=uid('final'),review_id=uid('review'))
# New seed augments synthetic B00 relations; all values are schema-only fixtures.
for n in (1,2):
    add('material_reference',id=uid('material'+str(n)),meeting_id=m,kind='LOCAL',file_id=uid('file'),state='ACTIVE')
    add('material_revision',id=uid('mr'+str(n)),reference_id=uid('material'+str(n)),revision=1,content_file_id=uid('file'),content_hash=sha,parse_state='READY',created_at=now)
add('material_snapshot',id=uid('snapshot'),meeting_id=m,revision=1,input_fingerprint=sha,applied_at=now)
add('material_snapshot_item',snapshot_id=uid('snapshot'),material_revision_id=uid('mr1'),ordinal=1)
for n in (1,2):
    add('parsed_document',id=uid('doc'+str(n)),material_revision_id=uid('mr1'),parser_version='P'+str(n),text_file_id=uid('file'),state='READY',unit_count=2)
    add('parsed_document_profile',document_id=uid('doc'+str(n)),format='PDF',parser_config_version='P'+str(n),parser_config_sha256=str(n)*64)
    for ordinal,page in ((1,1),(2,5)):
        add('parsed_unit',document_id=uid('doc'+str(n)),ordinal=ordinal,kind='PAGE',page_no=page,body='parser '+str(n)+' page '+str(page))
add('generation_input',id=uid('input2'),meeting_id=m,transcript_confirmation_id=uid('transcript-confirmation'),material_snapshot_id=uid('snapshot'),template_revision_id=uid('template-revision'),exclusion_revision_id=uid('exclusion'),prompt_version='synthetic-v2',model_config_version='synthetic-v2',base_revision=1,input_fingerprint='b'*64,scope='FULL')
seed.append('UPDATE generation_input SET material_snapshot_id='+val(uid('snapshot'))+' WHERE id='+val(uid('input'))+';')
def context(name,origin='GENERATION_INPUT',input_name='input',meeting=m):
    return insert('source_context',id=uid(name),meeting_id=meeting,origin_kind=origin,generation_input_id=uid(input_name) if input_name else None,transcript_confirmation_id=uid('transcript-confirmation'),material_snapshot_id=uid('snapshot'),catalog_version='synthetic-material-v2',catalog_sha256=sha,created_at=now)
for n,input_name in ((1,'input'),(2,'input2')):
    seed.append(context('context'+str(n),input_name=input_name))
    add('source_context_material',context_id=uid('context'+str(n)),material_revision_id=uid('mr1'),parsed_document_id=uid('doc'+str(n)))
seed.append(context('manual','MANUAL_SELECTION',None))
add('source_context_material',context_id=uid('manual'),material_revision_id=uid('mr1'),parsed_document_id=uid('doc2'))
add('source_locator',id=uid('pdf-locator'),meeting_id=m,kind='PDF_PAGE',file_id=uid('file'),page_no=1)
seed.append('UPDATE minutes_element SET source_context_id='+val(uid('context1'))+';')
seed.append('UPDATE evidence_link SET source_context_id='+val(uid('context1'))+',locator_id='+val(uid('pdf-locator'))+';')
add('evidence_link',id=uid('rebound'),locator_id=uid('pdf-locator'),element_id=uid('element'),support_state='PARTIAL',target_anchor='new ACTION basis',source_context_id=uid('manual'))
add('background_note',id=uid('note2'),set_id=uid('set'),input_id=uid('input2'),ordinal=2,body='Same text can have a different exact parser')
add('background_note_source',note_id=uid('note2'),locator_id=uid('pdf-locator'))
seed.append('UPDATE background_note_source SET locator_id='+val(uid('pdf-locator'))+' WHERE note_id='+val(uid('note'))+';')
# Identical historical candidate set/content associations never participate in origin resolution.
add('task_subject',subject_kind='MEETING',subject_id=m,business_version=1,cancel_epoch=0)
for n,inp in ((1,'input'),(2,'input2')):
    add('durable_task',id=uid('task'+str(n)),task_type='MINUTES',pool='MODEL',subject_kind='MEETING',subject_id=m,producer_key=str(n)*64,input_kind='GENERATION_INPUT',input_id=uid(inp),input_fingerprint=str(n)*64,business_version=1,cancel_epoch=0,priority=0,max_attempts=1,available_at=now,created_at=now)
    add('generation_candidate',id=uid('candidate'+str(n)),meeting_id=m,input_id=uid(inp),task_id=uid('task'+str(n)),element_set_id=uid('set'),base_revision=1,input_fingerprint=str(n)*64,state='STALE',created_at=now)
# Copy/partial replace/restoration are explicit new IDs with per-row inherited context.
for suffix,revision in (('copy',2),('partial',3),('restore',4)):
    add('element_set',id=uid('set-'+suffix),meeting_id=m,revision=revision,created_at=now)
    for n in (1,2):
        basis='context2' if suffix=='partial' and n==2 else 'context1'
        add('minutes_element',id=uid(suffix+str(n)),set_id=uid('set-'+suffix),category='DISCUSSION',ordinal=n,body='same discussion',support_state='PARTIAL',source_context_id=uid(basis))
        add('evidence_link',id=uid('e-'+suffix+str(n)),locator_id=uid('pdf-locator'),element_id=uid(suffix+str(n)),support_state='PARTIAL',target_anchor='same anchor',source_context_id=uid(basis))
add('minutes_element',id=uid('zero-evidence'),set_id=uid('set-copy'),category='CONCLUSION',ordinal=1,body='explicit unsupported basis',support_state='UNSUPPORTED',source_context_id=uid('context2'))
cli=['docker','exec','-i',container,'sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h127.0.0.1 --user="$MYSQL_USER" "$MYSQL_DATABASE" --batch --raw --skip-column-names']
records=[]
def execute(name,sql,error=None,output=None,boundary=False):
    p=subprocess.run(cli,input=sql,text=True,capture_output=True)
    # Save before assertions: rejected, failed and successful attempts all survive.
    raw=dict(probe=name,sql=sql,exit_code=p.returncode,stdout=p.stdout,stderr=p.stderr,database_boundary_witness=boundary)
    with (OUT/'probes-raw.jsonl').open('a') as f:f.write(json.dumps(raw,ensure_ascii=False)+'\n')
    code=re.search(r'ERROR (\d+)',p.stderr)
    if error:assert p.returncode!=0 and code and int(code[1])==error,(name,p.stderr)
    else:
        assert p.returncode==0,(name,p.stderr)
        if output is not None:assert p.stdout.strip()==output,(name,p.stdout,output)
    records.append(dict(probe=name,expected_error=error,observed_error=int(code[1]) if code else None,boundary=boundary,expectation_met=True))
def run(name,sql,error=None,output=None,boundary=False):
    execute(name,'SET NAMES utf8mb4; SET time_zone="+00:00"; START TRANSACTION;\n'+sql+'\nROLLBACK;',error,output,boundary)
assert not (OUT/'seed-committed.json').exists(), 'Never reseed or reset an existing fixture'
execute('initial-business-zero','SELECT COUNT(*) FROM meeting;',output='0')
execute('persist-owned-synthetic-seed','SET NAMES utf8mb4; SET time_zone="+00:00"; START TRANSACTION;\n'+'\n'.join(seed)+'\nCOMMIT;')
(OUT/'seed-committed.json').write_text(json.dumps({'container':container,'seed_sha256':hashlib.sha256('\n'.join(seed).encode()).hexdigest(),'scope':'schema-only synthetic; configs deliberately not executable'})+'\n')
query="SELECT p.parser_config_version,COUNT(*) FROM evidence_link e JOIN source_context_material s ON s.context_id=e.source_context_id JOIN parsed_document_profile p ON p.document_id=s.parsed_document_id WHERE e.locator_id="+val(uid('pdf-locator'))+" GROUP BY p.parser_config_version ORDER BY p.parser_config_version;"
run('reopen-reconstruct-mixed-evidence-without-candidate-query',query,output='P1\t7\nP2\t2')
run('mixed-note-exact-input-context',"SELECT p.parser_config_version FROM background_note n JOIN source_context c ON c.generation_input_id=n.input_id JOIN source_context_material s ON s.context_id=c.id JOIN parsed_document_profile p ON p.document_id=s.parsed_document_id ORDER BY n.ordinal;",output='P1\nP2')
run('action-evidence-independent-of-suggestion',"SELECT p.parser_config_version,c.origin_kind,a.input_id=e.source_context_id FROM evidence_link e JOIN action_suggestion a ON a.element_id=e.element_id JOIN source_context c ON c.id=e.source_context_id JOIN source_context_material s ON s.context_id=c.id JOIN parsed_document_profile p ON p.document_id=s.parsed_document_id WHERE e.id="+val(uid('rebound'))+';',output='P2\tMANUAL_SELECTION\t0')
run('zero-evidence-context-root',"SELECT c.origin_kind,COUNT(e.id) FROM minutes_element m JOIN source_context c ON c.id=m.source_context_id LEFT JOIN evidence_link e ON e.element_id=m.id WHERE m.id="+val(uid('zero-evidence'))+' GROUP BY c.origin_kind;',output='GENERATION_INPUT\t0')
run('duplicate-generation-context',context('dup'),1062)
run('generated-context-missing-input',context('bad','GENERATION_INPUT',None),3819)
run('manual-context-with-input',context('bad','MANUAL_SELECTION','input'),3819)
run('unknown-origin-tag',context('bad','GENERATED',None),3819)
run('unknown-context-FK',insert('source_context_material',context_id=uid('missing'),material_revision_id=uid('mr1'),parsed_document_id=uid('doc1')),1452)
run('duplicate-material-selection',insert('source_context_material',context_id=uid('context1'),material_revision_id=uid('mr1'),parsed_document_id=uid('doc2')),1062)
run('restrict-context-delete','DELETE FROM source_context WHERE id='+val(uid('context1'))+';',1451)
run('restrict-selected-document-delete','DELETE FROM parsed_document WHERE id='+val(uid('doc1'))+';',1451)
run('profile-sidecar-deletion-is-domain-obligation','DELETE FROM parsed_document_profile WHERE document_id='+val(uid('doc1'))+'; SELECT COUNT(*) FROM parsed_document_profile WHERE document_id='+val(uid('doc1'))+';',output='0',boundary=True)
run('missing-selection-is-domain-obligation',context('incomplete','MANUAL_SELECTION',None)+' SELECT COUNT(*) FROM source_context_material WHERE context_id='+val(uid('incomplete'))+';',output='0',boundary=True)
run('wrong-document-material-is-domain-obligation',context('wrong','MANUAL_SELECTION',None)+insert('source_context_material',context_id=uid('wrong'),material_revision_id=uid('mr2'),parsed_document_id=uid('doc1'))+' SELECT s.material_revision_id=d.material_revision_id FROM source_context_material s JOIN parsed_document d ON d.id=s.parsed_document_id WHERE s.context_id='+val(uid('wrong'))+';',output='0',boundary=True)
run('cross-meeting-is-domain-obligation',context('cross','MANUAL_SELECTION',None,mb)+' SELECT c.meeting_id=t.meeting_id FROM source_context c JOIN transcript_confirmation t ON t.id=c.transcript_confirmation_id WHERE c.id='+val(uid('cross'))+';',output='0',boundary=True)
run('legacy-null-is-unavailable-not-current',insert('minutes_element',id=uid('legacy'),set_id=uid('set'),category='DISCUSSION',ordinal=10,body='legacy',support_state='PARTIAL')+' SELECT source_context_id IS NULL FROM minutes_element WHERE id='+val(uid('legacy'))+';',output='1',boundary=True)
run('unused-unit-retained-in-complete-document','SELECT page_no FROM parsed_unit WHERE document_id='+val(uid('doc1'))+' ORDER BY ordinal;',output='1\n5')
run('revocation-preserves-origin-not-access',"UPDATE material_reference SET state='REMOVED'; SELECT COUNT(*) FROM source_context_material; SELECT COUNT(*) FROM material_reference WHERE state='ACTIVE';",output='3\n0',boundary=True)
run('duplicate-occurrence-key-rejected',insert('background_note_source',note_id=uid('note2'),locator_id=uid('pdf-locator')),1062)
execute('final-reopen-unchanged-origin',query,output='P1\t7\nP2\t2')
execute('native-utc-epoch','SELECT UTC_TIMESTAMP(6),UNIX_TIMESTAMP(UTC_TIMESTAMP(6)),@@innodb_use_native_aio;')
(OUT/'constraint-probes.json').write_text(json.dumps(dict(container=container,container_id=proof['id'],scope='actual SQL constraints and explicit accepted boundary witnesses; connection reopen only, not database restart or runtime acceptance',probes=records),ensure_ascii=False,indent=2)+'\n')
print(json.dumps(dict(probes=len(records),boundaries=sum(x.get('boundary',False) for x in records))))
