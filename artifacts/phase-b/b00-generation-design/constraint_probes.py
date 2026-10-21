"""Actual MySQL relational probes. Synthetic rows, rollback-only; not app acceptance."""
import hashlib, importlib.util, json, pathlib, re, subprocess, uuid
ROOT=pathlib.Path(__file__).resolve().parents[3]; OUT=ROOT/'artifacts/phase-b/b00-generation-design'
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
cli=['docker','exec','-i',container,'sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h127.0.0.1 --user="$MYSQL_USER" "$MYSQL_DATABASE" --batch --raw --skip-column-names']
records=[];sql_records=[]
def run(name,statement,expected_error=None,expected_output=None,boundary=False):
    sql='SET NAMES utf8mb4; SET time_zone="+00:00"; START TRANSACTION;\n'+'\n'.join(seed)+'\n'+statement+'\nROLLBACK;\n'
    p=subprocess.run(cli,input=sql,text=True,capture_output=True)
    code=re.search(r'ERROR (\d+)',p.stderr)
    if expected_error:assert p.returncode!=0 and code and int(code[1])==expected_error,(name,p.returncode,p.stderr)
    else:
        assert p.returncode==0,(name,p.stderr)
        if expected_output is not None:assert p.stdout.strip()==expected_output,(name,p.stdout,expected_output)
    records.append({'probe':name,'database_boundary_witness':boundary,'exit_code':p.returncode,'mysql_error':int(code[1]) if code else None,'output':p.stdout.strip(),'expectation_met':True})
    sql_records.append('-- '+name+'\n'+statement)
run('multi-label-and-UTF8-lossless',"SELECT COUNT(*),COUNT(DISTINCT locator_id) FROM sensitive_exclusion; SELECT HEX(body) FROM background_note; SELECT HEX(owner_mention),due_on FROM action_suggestion; SELECT owner_enterprise_id IS NULL AND owner_display IS NULL AND due_on IS NULL FROM action_item;",expected_output='2\t1\n'+'背景：甲\n乙😀，仅背景补充。'.encode().hex().upper()+'\n'+'“王工”\n尚待映射😀'.encode().hex().upper()+'\t2026-11-01\n1')
run('unselect-one-label-retains-other',"DELETE FROM sensitive_exclusion WHERE category='INTERNAL_BUSINESS_DATA'; SELECT category FROM sensitive_exclusion; SELECT COUNT(*) FROM sensitive_detection;",expected_output='POLITICAL_SENSITIVE\n2')
run('duplicate-selected-label',insert('sensitive_exclusion',revision_id=uid('exclusion'),locator_id=uid('locator'),category='POLITICAL_SENSITIVE'),1062)
run('invalid-sensitive-category',insert('sensitive_detection',id=uid('bad-detection'),set_id=uid('set'),input_id=uid('input'),category='SECRET',ordinal=2),3819)
run('duplicate-directory-order',insert('background_note',id=uid('bad-note'),set_id=uid('set'),input_id=uid('input'),ordinal=1,body='different body'),1062)
run('invalid-zero-ordinal',insert('background_note',id=uid('bad-note'),set_id=uid('set'),input_id=uid('input'),ordinal=0,body='body'),3819)
run('duplicate-source-set-member',insert('sensitive_detection_source',detection_id=uid('detection1'),locator_id=uid('locator')),1062)
run('unknown-source-FK',insert('background_note_source',note_id=uid('note'),locator_id=uid('missing')),1452)
run('restrict-reviewed-evidence-delete','DELETE FROM evidence_link WHERE id='+val(uid('evidence'))+';',1451)
run('restrict-final-review-delete','DELETE FROM result_review WHERE id='+val(uid('review'))+';',1451)
run('final-confirmation-parent-FK',insert('final_result_review',confirmation_id=uid('missing'),review_id=uid('review')),1452)
run('final-review-parent-FK',insert('final_result_review',confirmation_id=uid('final'),review_id=uid('missing')),1452)
for name,fields in [('two-targets',{'minutes_revision_id':uid('minutes')}),('zero-targets',{'element_id':None}),('action-on-body',{'review_kind':'ACTION_FIELDS','element_id':None,'minutes_revision_id':uid('minutes'),'target_anchor':None,'outcome':'CONFIRMED'}),('invalid-outcome',{'outcome':'TRUE'}),('source-without-anchor',{'target_anchor':None}),('uppercase-hash',{'target_hash':'A'*64}),('nonhex-hash',{'source_set_hash':'z'*64})]:
    run(name,review(id=uid('probe-review'),review_no=2,**fields),3819)
run('unknown-reviewer-identity-not-an-FK',review(id=uid('probe-review'),review_no=2,reviewed_by='missing-human-identity')+' SELECT COUNT(*) FROM result_review r JOIN enterprise_identity i ON i.enterprise_id=r.reviewed_by WHERE r.id='+val(uid('probe-review'))+';',expected_output='0',boundary=True)
run('cross-meeting-review-not-FK-enforced',review(id=uid('probe-review'),review_no=2,meeting_id=mb)+' SELECT COUNT(*) FROM result_review r JOIN minutes_element e ON e.id=r.element_id JOIN element_set s ON s.id=e.set_id WHERE r.id='+val(uid('probe-review'))+' AND s.meeting_id=r.meeting_id;',expected_output='0',boundary=True)
run('other-target-evidence-not-FK-enforced',insert('result_review_evidence',review_id=uid('review'),evidence_id=uid('other-evidence'))+' SELECT COUNT(*) FROM result_review_evidence x JOIN result_review r ON r.id=x.review_id JOIN evidence_link e ON e.id=x.evidence_id WHERE x.evidence_id='+val(uid('other-evidence'))+' AND e.element_id=r.element_id;',expected_output='0',boundary=True)
run('content-hash-mismatch-not-CHECK-enforced',review(id=uid('probe-review'),review_no=2,target_hash='0'*64)+' SELECT target_hash=SHA2(\'合成行动\',256) FROM result_review WHERE id='+val(uid('probe-review'))+';',expected_output='0',boundary=True)
# Each success rolls back explicitly; error closes connection and MySQL rolls back.
p=subprocess.run(cli,input='SELECT (SELECT COUNT(*) FROM meeting)+(SELECT COUNT(*) FROM result_review)+(SELECT COUNT(*) FROM sensitive_detection)+(SELECT COUNT(*) FROM background_note);',text=True,capture_output=True)
assert p.returncode==0 and p.stdout.strip()=='0'
(OUT/'constraint-probes.sql.txt').write_text('-- Synthetic design-only seed; never a migration. Every case starts a new transaction and rolls back.\n'+'\n'.join(seed)+'\n\n'+'\n\n'.join(sql_records)+'\n')
(OUT/'constraint-probes.json').write_text(json.dumps({'container':container,'container_id':proof['id'],'scope':'actual relational checks and explicit limits; not application/ACL/semantic acceptance','probes':records,'all_probe_business_rows_after_rollback':0},ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'probes':len(records),'boundaries_observed':sum(x['database_boundary_witness'] for x in records),'residual_business_rows':0}))
