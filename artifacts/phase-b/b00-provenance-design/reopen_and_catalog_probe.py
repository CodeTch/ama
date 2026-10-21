"""Separate process reads persisted origins; added multi-file SQL stays rollback-only."""
import hashlib,json,pathlib,subprocess,uuid
ROOT=pathlib.Path(__file__).resolve().parents[3];OUT=ROOT/'artifacts/phase-b/b00-provenance-design'
proof=json.loads((OUT/'fixture-initial.json').read_text());name=proof['container']
actual=subprocess.check_output(['docker','inspect','--format','{{.Id}}',name],text=True).strip()
assert actual==proof['id'] and proof['owned_worktree']==str(ROOT)
def uid(k):return str(uuid.uuid5(uuid.NAMESPACE_URL,'ama-b00-design-synthetic:'+k))
def val(x):
    if x is None:return 'NULL'
    if type(x) is int:return str(x)
    return 'CONVERT(0x'+x.encode().hex()+' USING utf8mb4)'
def ins(t,**kw):return 'INSERT INTO '+t+'('+','.join(kw)+') VALUES('+','.join(val(v) for v in kw.values())+');'
cli=['docker','exec','-i',name,'sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h127.0.0.1 --user="$MYSQL_USER" "$MYSQL_DATABASE" --batch --raw --skip-column-names']
records=[]
def query(label,sql,expected=None):
    r=subprocess.run(cli,input=sql,text=True,capture_output=True)
    rec=dict(label=label,query=sql,stdout=r.stdout,stderr=r.stderr,exit_code=r.returncode)
    with (OUT/'reopen-catalog-raw.jsonl').open('a') as f:f.write(json.dumps(rec,ensure_ascii=False)+'\n')
    assert r.returncode==0,(label,r.stderr)
    if expected is not None:assert r.stdout.strip()==expected,(label,r.stdout)
    records.append(rec)
query('multiple-historical-candidates-same-set','SELECT COUNT(*),COUNT(DISTINCT input_id),COUNT(DISTINCT element_set_id) FROM generation_candidate;', '2\t2\t1')
for op in ('copy','partial','restore'):
    q='SELECT e.ordinal,p.parser_config_version FROM minutes_element e JOIN source_context_material s ON s.context_id=e.source_context_id JOIN parsed_document_profile p ON p.document_id=s.parsed_document_id WHERE e.set_id='+val(uid('set-'+op))+" AND e.category='DISCUSSION' ORDER BY e.ordinal;"
    query('separate-process-'+op,q,'1\tP1\n2\t'+('P2' if op=='partial' else 'P1'))
now='2026-10-22 00:00:00.000001';sha='a'*64;m=uid('meeting-a')
sql=["SET NAMES utf8mb4; SET time_zone='+00:00'; START TRANSACTION;"]
sql.append(ins('file_object',id=uid('file2'),meeting_id=m,backend_id='synthetic-local',storage_type='local',namespace='synthetic',object_key='two.pdf',display_name='two.pdf',size_bytes=3,sha256=sha,state='AVAILABLE',created_at=now))
sql.append(ins('material_reference',id=uid('material3'),meeting_id=m,kind='LOCAL',file_id=uid('file2'),state='ACTIVE'))
sql.append(ins('material_revision',id=uid('mr3'),reference_id=uid('material3'),revision=1,content_file_id=uid('file2'),content_hash=sha,parse_state='READY',created_at=now))
sql.append(ins('parsed_document',id=uid('doc3'),material_revision_id=uid('mr3'),parser_version='P2',text_file_id=uid('file2'),state='READY',unit_count=1))
sql.append(ins('parsed_document_profile',document_id=uid('doc3'),format='PDF',parser_config_version='P2',parser_config_sha256='2'*64))
sql.append(ins('parsed_unit',document_id=uid('doc3'),ordinal=1,kind='PAGE',page_no=1,body='second PDF same page but different file'))
sql.append(ins('material_snapshot',id=uid('snapshot-two'),meeting_id=m,revision=2,input_fingerprint=sha,applied_at=now))
for ordinal,mr in ((1,'mr1'),(2,'mr3')):sql.append(ins('material_snapshot_item',snapshot_id=uid('snapshot-two'),material_revision_id=uid(mr),ordinal=ordinal))
sql.append(ins('source_context',id=uid('context-two'),meeting_id=m,origin_kind='MANUAL_SELECTION',transcript_confirmation_id=uid('transcript-confirmation'),material_snapshot_id=uid('snapshot-two'),catalog_version='synthetic-material-v2',catalog_sha256=sha,created_at=now))
for mr,doc in (('mr1','doc1'),('mr3','doc3')):sql.append(ins('source_context_material',context_id=uid('context-two'),material_revision_id=uid(mr),parsed_document_id=uid(doc)))
sql.append('SELECT COUNT(*),COUNT(DISTINCT p.format),COUNT(DISTINCT p.parser_config_version),COUNT(DISTINCT r.content_file_id) FROM source_context_material s JOIN parsed_document_profile p ON p.document_id=s.parsed_document_id JOIN material_revision r ON r.id=s.material_revision_id WHERE s.context_id='+val(uid('context-two'))+';')
sql.append('SELECT COUNT(*) FROM source_context_material s JOIN parsed_unit u ON u.document_id=s.parsed_document_id WHERE s.context_id='+val(uid('context-two'))+'; ROLLBACK;')
query('two-PDF-files-two-parser-versions-in-one-context','\n'.join(sql),'2\t1\t2\t2\n3')
# Required background editing retains inherited source basis; new target gets a new identity.
sql='START TRANSACTION; '+ins('background_note',id=uid('edited-note'),set_id=uid('set-copy'),input_id=uid('input'),ordinal=1,body='human edited prose, inherited source basis')+ins('background_note_source',note_id=uid('edited-note'),locator_id=uid('pdf-locator'))+' SELECT p.parser_config_version FROM background_note n JOIN source_context c ON c.generation_input_id=n.input_id JOIN source_context_material s ON s.context_id=c.id JOIN parsed_document_profile p ON p.document_id=s.parsed_document_id WHERE n.id='+val(uid('edited-note'))+'; ROLLBACK;'
query('background-prose-edit-preserves-P1',sql,'P1')
query('extra-probes-rolled-back','SELECT COUNT(*) FROM material_snapshot; SELECT COUNT(*) FROM background_note;', '1\n2')
(OUT/'reopen-catalog-probes.json').write_text(json.dumps(dict(container=name,scope='separate Python process plus new MySQL connections; no API/worker/database-restart qualification',checks=len(records),results=records),indent=2)+'\n')
print(json.dumps(dict(checks=len(records))))
