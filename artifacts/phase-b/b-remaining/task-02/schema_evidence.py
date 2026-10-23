"""Task 2 guarded append-only schema evidence. Only this run fixture; no reset/DDL."""
import hashlib,json,pathlib,subprocess,sys,shlex
ROOT=pathlib.Path(__file__).resolve().parents[4]
OUT=pathlib.Path(__file__).resolve().parent
CONTAINER='ama-test-b01-ad9f1af29eff'
ID='eefdd9a984ee00fe6ca886168b29a6ce4f3b405467b8d3204240d4e1a8dc7502'
NEW={'input_batch_fingerprint_profile','meeting_minutes_editor_guard'}
def write(name,value):
    path=OUT/(name+'.json');assert not path.exists(),str(path)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
def query(sql,ok=True):
    r=subprocess.run(['docker','exec','-i',CONTAINER,'sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h127.0.0.1 --user="$MYSQL_USER" "$MYSQL_DATABASE" --batch --raw --skip-column-names'],input=sql,text=True,capture_output=True)
    with (OUT/'mysql-raw.jsonl').open('a') as f:f.write(json.dumps(dict(sql=sql,exit=r.returncode,stdout=r.stdout,stderr=r.stderr),ensure_ascii=False)+'\n')
    if ok:r.check_returncode()
    return r
QUERIES={
 'tables':"SELECT JSON_OBJECT('table',TABLE_NAME,'engine',ENGINE,'collation',TABLE_COLLATION) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME",
 'columns':"SELECT JSON_OBJECT('table',TABLE_NAME,'column',COLUMN_NAME,'ordinal',ORDINAL_POSITION,'type',COLUMN_TYPE,'nullable',IS_NULLABLE,'default',COLUMN_DEFAULT,'collation',COLLATION_NAME,'extra',EXTRA) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,ORDINAL_POSITION",
 'indexes':"SELECT JSON_OBJECT('table',TABLE_NAME,'name',INDEX_NAME,'unique',1-NON_UNIQUE,'seq',SEQ_IN_INDEX,'column',COLUMN_NAME,'direction',COLLATION,'prefix',SUB_PART,'type',INDEX_TYPE) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX",
 'checks':"SELECT JSON_OBJECT('table',t.TABLE_NAME,'name',t.CONSTRAINT_NAME,'clause',c.CHECK_CLAUSE,'enforced',t.ENFORCED) FROM information_schema.TABLE_CONSTRAINTS t JOIN information_schema.CHECK_CONSTRAINTS c ON c.CONSTRAINT_SCHEMA=t.CONSTRAINT_SCHEMA AND c.CONSTRAINT_NAME=t.CONSTRAINT_NAME WHERE t.CONSTRAINT_SCHEMA=DATABASE() AND t.CONSTRAINT_TYPE='CHECK' ORDER BY t.TABLE_NAME,t.CONSTRAINT_NAME",
 'foreign_keys':"SELECT JSON_OBJECT('table',k.TABLE_NAME,'name',k.CONSTRAINT_NAME,'column',k.COLUMN_NAME,'ordinal',k.ORDINAL_POSITION,'target',k.REFERENCED_TABLE_NAME,'remote',k.REFERENCED_COLUMN_NAME,'delete',r.DELETE_RULE,'update',r.UPDATE_RULE) FROM information_schema.KEY_COLUMN_USAGE k JOIN information_schema.REFERENTIAL_CONSTRAINTS r ON r.CONSTRAINT_SCHEMA=k.CONSTRAINT_SCHEMA AND r.CONSTRAINT_NAME=k.CONSTRAINT_NAME WHERE k.CONSTRAINT_SCHEMA=DATABASE() AND k.REFERENCED_TABLE_NAME IS NOT NULL ORDER BY k.TABLE_NAME,k.CONSTRAINT_NAME,k.ORDINAL_POSITION"}
def guard():
    m=json.loads(subprocess.check_output(['docker','inspect',CONTAINER],text=True))[0]
    assert m['Id']==ID
    assert m['Config']['Labels']['ama.remaining.owner']=='remaining-20261023'
    assert m['Config']['Labels']['ama.test-fixture']=='b01'
    assert m['NetworkSettings']['Ports']['3306/tcp']==[{'HostIp':'127.0.0.1','HostPort':'58062'}]
    assert m['HostConfig']['Memory']==805306368 and m['HostConfig']['NanoCpus']==1000000000 and m['HostConfig']['PidsLimit']==256
    assert m['Image']=='sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9'
    assert m['Config']['Image']=='mysql@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9'
    assert m['Name']=='/'+CONTAINER and m['State']['Running']
    assert query('SELECT VERSION(),DATABASE(),@@innodb_use_native_aio').stdout.strip()=='8.4.6\tama_b01_fixture\t0'
    return {k:m[k] for k in ['Id','Image','Name']}|{'labels':m['Config']['Labels'],'imageReference':m['Config']['Image'],'ports':m['NetworkSettings']['Ports'],'resources':{k:m['HostConfig'][k] for k in ['Memory','NanoCpus','PidsLimit']}}
def snapshot():
    metadata={k:[json.loads(x) for x in query(q).stdout.splitlines()] for k,q in QUERIES.items()}
    names=[x['table'] for x in metadata['tables']]
    rows={n:sorted(query('SELECT * FROM `'+n+'`').stdout.splitlines()) for n in names if n!='flyway_schema_history'}
    creates={n:query('SHOW CREATE TABLE `'+n+'`').stdout for n in names if n!='flyway_schema_history'}
    history=query('SELECT installed_rank,version,description,type,script,checksum,installed_by,installed_on,execution_time,success FROM flyway_schema_history ORDER BY installed_rank').stdout.splitlines()
    return dict(metadata=metadata,rows=rows,creates=creates,history=history)
def old_equal(before,after):
    assert before['history']==after['history'][:len(before['history'])]
    for group in before['metadata']:
        assert before['metadata'][group]==[x for x in after['metadata'][group] if x['table'] not in NEW],group
    for key in ['rows','creates']:
        assert before[key]=={k:v for k,v in after[key].items() if k not in NEW},key
def seed_legacy():
    # Physical metadata fixture only: the committed literal establishes matching v1 bytes,
    # not authentic production intake or managed file persistence.
    literal=ROOT/'backend/core/src/test/resources/ama/transcript/batch-v1.literal.bytes'
    digest=hashlib.sha256(literal.read_bytes()).hexdigest()
    expected=(literal.with_suffix('.sha256')).read_text().strip()
    assert digest==expected=='c4a558e4dc8aff8289b4ed60193efdfbc7f64f73ae642e2eec6398c473458053'
    payload=json.loads(literal.with_suffix('.json').read_text())['data']
    import os,uuid
    meeting=str(uuid.uuid4());batch=str(uuid.uuid4())
    for f in payload['files']:f['fileId']=str(uuid.uuid4())
    payload['meetingId']=meeting;payload['batchId']=batch
    # Invoke the adopted production BatchFingerprint against exact new member/file facts.
    members=','.join('new BatchFingerprint.Member(%d,"%s","%s",%d)'%(f['ordinal'],f['fileId'],f['sha256'],f['sizeBytes']) for f in payload['files'])
    java='import ama.transcript.*; import ama.contract.MinutesContracts.*; import java.util.*;\n'
    java+='System.out.println("NEW_HASH:"+new BatchFingerprint().compute("%s","%s","TRANSCRIPT",9,List.of(%s),new CanonicalBudget(10000,16,1000,1000,100),()->false));\n'%(meeting,batch,members)
    golden=json.loads(literal.with_suffix('.json').read_text())['data']
    golden_members=','.join('new BatchFingerprint.Member(%d,"%s","%s",%d)'%(f['ordinal'],f['fileId'],f['sha256'],f['sizeBytes']) for f in golden['files'])
    java+='System.out.println("GOLDEN_HASH:"+new BatchFingerprint().compute("%s","%s","TRANSCRIPT",9,List.of(%s),new CanonicalBudget(10000,16,1000,1000,100),()->false));\n'%(golden['meetingId'],golden['batchId'],golden_members)
    cp=str(ROOT/'backend/core/target/classes')+':'+str(ROOT/'backend/contracts/target/classes')
    command=[os.environ['JAVA_HOME']+'/bin/jshell','--class-path',cp,'-']
    result=subprocess.run(command,input=java,text=True,capture_output=True)
    write('legacy-recomputation',dict(command=command,source=java,exit=result.returncode,stdout=result.stdout,stderr=result.stderr,literal_golden_sha256=digest,exact_new_facts=payload))
    assert result.returncode==0 and 'GOLDEN_HASH:'+digest in result.stdout,result.stderr
    digest=result.stdout.split('NEW_HASH:')[1].splitlines()[0].strip()
    assert len(digest)==64 and all(c in '0123456789abcdef' for c in digest)
    owner='runtime-legacy-'+str(uuid.uuid4());backend='runtime-legacy-'+str(uuid.uuid4())
    assert query("SELECT COUNT(*) FROM meeting WHERE id='"+meeting+"'").stdout.strip()=='0'
    before=snapshot();assert before==json.loads((OUT/'after-red.json').read_text());write('before-legacy-seed',before)
    statements=["START TRANSACTION",
      "INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES ('"+owner+"','legacy metadata fixture','ACTIVE',UTC_TIMESTAMP(6))",
      "INSERT INTO task_subject VALUES ('MEETING','"+meeting+"',9,0,0)",
      "INSERT INTO meeting(id,owner_id,revision,stage,created_at,updated_at) VALUES ('"+meeting+"','"+owner+"',9,'ENTRY',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
      "INSERT INTO storage_backend VALUES ('"+backend+"','local','READ_ONLY',1)",
      "INSERT INTO input_batch VALUES ('"+batch+"','"+meeting+"','TRANSCRIPT','"+digest+"',9,UTC_TIMESTAMP(6))"]
    for f in payload['files']:
        statements.append("INSERT INTO file_object(id,meeting_id,backend_id,storage_type,namespace,object_key,display_name,size_bytes,sha256,state,created_at) VALUES ('%s','%s','%s','local','task02','%s','physical metadata only',%d,'%s','AVAILABLE',UTC_TIMESTAMP(6))"%(f['fileId'],meeting,backend,f['fileId'],f['sizeBytes'],f['sha256']))
        statements.append("INSERT INTO input_batch_file VALUES ('%s',%d,'%s','%s')"%(batch,f['ordinal'],f['fileId'],f['sha256']))
    statements+=['COMMIT'];query(';\n'.join(statements)+';')
    after=snapshot();write('after-legacy-seed',after)
    assert before['metadata']==after['metadata'] and before['creates']==after['creates'] and before['history']==after['history']
    for name,rows in before['rows'].items(): assert set(rows)<=set(after['rows'][name]),name
    additions={n:sorted(set(v)-set(before['rows'][n])) for n,v in after['rows'].items() if v!=before['rows'][n]}
    write('legacy-seed',dict(scope='metadata-only schema fixture, not authentic intake/files',meeting=meeting,batch=batch,owner=owner,new_fingerprint=digest,exact_new_facts=payload,canonical_vector_sha256=expected,canonical_vector_bytes=len(literal.read_bytes()),additions=additions,all_prior_rows_preserved=True))
def before_apply():
    write('fresh-preapply-identity',guard());a=snapshot();write('immediate-before-apply',a)
    old=json.loads((OUT/'after-legacy-seed.json').read_text());assert old==a,'preapply attempts changed rows/schema/history'
    assert len(a['history'])==12 and all(x.endswith('\t1') for x in a['history'])
    prefix=[line.split('\t') for line in query('SELECT installed_rank,version,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank').stdout.splitlines()]
    required=[line.split('\t') for line in (ROOT/'artifacts/phase-b/b-remaining/task-01/fixture/history-after.log').read_text().splitlines()[1:13]]
    assert prefix==required
    write('fresh-prefix-proof',dict(exact_task1_prefix=prefix,success=True,no_applied_14_or_later=True,required_tables_absent=not NEW.intersection(x['table'] for x in a['metadata']['tables'])))
    print('READY: current guarded identity, exact 12-success prefix and all legacy/Task1 rows/schema/history snapshotted')
def after_apply():
    guard();before=json.loads((OUT/'immediate-before-apply.json').read_text());after=snapshot()
    old_equal(before,after);assert len(after['history'])==13 and after['history'][-1].split('\t')[1]=='013.1' and after['history'][-1].endswith('\t1')
    assert all(not after['rows'][n] for n in NEW),'migration must not populate historical sidecars'
    original=json.loads((OUT/'old-sql-sha256.json').read_text())
    assert original=={name:hashlib.sha256((ROOT/name).read_bytes()).hexdigest() for name in original}
    candidate=OUT/'V013_1__b_runtime_metadata.sql.txt';formal=ROOT/'backend/adapters/src/main/resources/db/migration/V013_1__b_runtime_metadata.sql'
    assert candidate.read_bytes()==formal.read_bytes() and hashlib.sha256(formal.read_bytes()).hexdigest()=='9280baea017014f3322af901373a7f8d69dfaa396fdd49f3c5d872535c43a520'
    write('after-apply',after)
    write('apply-preservation',dict(old_user_tables=len(before['rows']),old_rows=sum(map(len,before['rows'].values())),all_old_rows_show_create_five_groups_equal=True,history_prefix_equal=True,history_appended=after['history'][-1],new_tables_empty=True,old_source_hashes_unchanged=True,frozen_candidate_equals_formal=True))
    print('APPLY preservation PASS: exact 1103 old rows/all old schema/12historyprefix plus1successful013.1; newsidecars empty')
def capture_rerun(name):
    guard();a=snapshot();b=json.loads((OUT/'after-apply.json').read_text());assert a==b;write(name,a)
    print(name+': exact complete metadata/rows/history equal after-apply')
def capture_session():
    guard()
    sql="SET NAMES utf8mb4; SET time_zone='+00:00'; SELECT JSON_OBJECT('version',VERSION(),'global_sql_mode',@@global.sql_mode,'session_sql_mode',@@session.sql_mode,'session_timezone',@@session.time_zone,'system_timezone',@@system_time_zone,'now',NOW(6),'utc',UTC_TIMESTAMP(6),'native_epoch',UNIX_TIMESTAMP(UTC_TIMESTAMP(6)),'database',DATABASE(),'native_aio',@@innodb_use_native_aio);"
    value=json.loads(query(sql).stdout.strip());assert 'STRICT_TRANS_TABLES' in value['session_sql_mode'] and value['session_timezone']=='+00:00' and value['now']==value['utc'];write('session-utc-sqlmode',value)

def constraint_probes():
    import uuid
    before=snapshot();write('before-constraints',before);results=[]
    meeting=str(uuid.uuid4());batch=str(uuid.uuid4());owner='constraints-'+str(uuid.uuid4())
    pre="SET NAMES utf8mb4; SET time_zone='+00:00'; START TRANSACTION; INSERT INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES ('%s','constraint','ACTIVE',UTC_TIMESTAMP(6)); INSERT INTO meeting(id,owner_id,stage,created_at,updated_at) VALUES ('%s','%s','ENTRY',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)); INSERT INTO input_batch VALUES ('%s','%s','TRANSCRIPT',REPEAT('a',64),0,UTC_TIMESTAMP(6)); "%(owner,meeting,owner,batch,meeting)
    guard="INSERT INTO meeting_minutes_editor_guard VALUES ('%s',%%s);"%meeting
    profile="INSERT INTO input_batch_fingerprint_profile VALUES ('%s',%%s);"%batch
    def attempt(label,sql,code=None):
        result=query(pre+sql+' ROLLBACK;',False)
        assert (result.returncode==0 if code is None else result.returncode!=0 and any(str(c) in result.stderr for c in code)),(label,result.stderr)
        results.append(dict(label=label,exit=result.returncode,expected_mysql_errors=code,stdout=result.stdout,stderr=result.stderr))
    for n in ['0','1','9223372036854775806']:attempt('valid epoch '+n,guard%n+" SELECT intent_epoch FROM meeting_minutes_editor_guard WHERE meeting_id='"+meeting+"';")
    attempt('valid exact v1',profile%"'ama.input-batch.v1'")
    for n in ['-1','9223372036854775807']:attempt('reject epoch '+n,guard%n,[3819])
    attempt('reject bigint overflow',guard%'9223372036854775808',[1264])
    for value in ["''","'AMA.input-batch.v1'","'ama.input-batch.v2'","' ama.input-batch.v1'","'ama.input-batch.v1 '","'ama.input-batch.v1  '","'ama.input-batch.v1é'"]:
        attempt('reject profile '+value,profile%value,[3819,1366])
    for name,statement in [
      ('duplicate guard',guard%'0'+guard%'1'),('duplicate profile',profile%"'ama.input-batch.v1'"+profile%"'ama.input-batch.v1'")]:attempt(name,statement,[1062])
    for name,statement in [
      ('orphan guard',"INSERT INTO meeting_minutes_editor_guard VALUES ('00000000-0000-4000-8000-000000000001',0);"),
      ('orphan profile',"INSERT INTO input_batch_fingerprint_profile VALUES ('00000000-0000-4000-8000-000000000001','ama.input-batch.v1');")]:attempt(name,statement,[1452])
    for name,statement in [
      ('null meeting',"INSERT INTO meeting_minutes_editor_guard VALUES (NULL,0);"),('null epoch',guard%'NULL'),
      ('null batch',"INSERT INTO input_batch_fingerprint_profile VALUES (NULL,'ama.input-batch.v1');"),('null profile',profile%'NULL')]:attempt(name,statement,[1048])
    for name,statement in [
      ('missing epoch',"INSERT INTO meeting_minutes_editor_guard(meeting_id) VALUES ('%s');"%meeting),
      ('missing meeting',"INSERT INTO meeting_minutes_editor_guard(intent_epoch) VALUES (0);"),
      ('missing profile',"INSERT INTO input_batch_fingerprint_profile(batch_id) VALUES ('%s');"%batch),
      ('missing batch',"INSERT INTO input_batch_fingerprint_profile(profile) VALUES ('ama.input-batch.v1');")]:attempt(name,statement,[1364])
    for table,key,val,child in [('meeting','id',meeting,"DELETE FROM input_batch WHERE id='"+batch+"';"+guard%'0'),('input_batch','id',batch,profile%"'ama.input-batch.v1'")]:
        attempt('restrict delete '+table,child+" DELETE FROM %s WHERE %s='%s';"%(table,key,val),[1451])
        attempt('restrict update '+table,child+" UPDATE %s SET %s='%s' WHERE %s='%s';"%(table,key,str(uuid.uuid4()),key,val),[1451])
    after=snapshot();assert before==after;write('after-constraints',after);write('constraint-results',dict(positives=sum(x['exit']==0 for x in results),negatives=sum(x['exit']!=0 for x in results),probes=results,all_dml_rolled_back=True))
    print('Constraint probes PASS: '+str(len(results))+'; full before/after equality')

if __name__=='__main__':
    action=sys.argv[1]
    if action=='session': capture_session()
    elif action=='after-apply': after_apply()
    elif action in ['after-private-rerun','after-latest-rerun']: capture_rerun(action)
    elif action=='seed-legacy': seed_legacy()
    elif action=='before-apply': before_apply()
    elif action=='constraints': guard();constraint_probes()
    elif action=='stage-a':
        write('fixture-identity',guard())
        old=sorted((ROOT/'backend/adapters/src/main/resources/db/migration').glob('*.sql'))
        assert len(old)==12
        write('old-sql-sha256',{str(x.relative_to(ROOT)):hashlib.sha256(x.read_bytes()).hexdigest() for x in old})
        a=snapshot();assert len(a['history'])==12 and all(x.endswith('\t1') for x in a['history'])
        assert not NEW.intersection(x['table'] for x in a['metadata']['tables'])
        write('before-red',a)
        r=query('SELECT batch_id,profile FROM input_batch_fingerprint_profile; SELECT meeting_id,intent_epoch FROM meeting_minutes_editor_guard;',False)
        assert r.returncode!=0 and '1146' in r.stderr and 'input_batch_fingerprint_profile' in r.stderr
        r2=query('SELECT meeting_id,intent_epoch FROM meeting_minutes_editor_guard',False)
        assert r2.returncode!=0 and '1146' in r2.stderr and 'meeting_minutes_editor_guard' in r2.stderr
        b=snapshot();assert a==b;write('after-red',b)
        write('schema-red',dict(result='RED: both required tables missing (1146)',preservation='exact before/after snapshot equality',tables=len(a['rows']),rows=sum(map(len,a['rows'].values())),history=12))
        env={x.split('=',1)[0]:x.split('=',1)[1] for x in json.loads(subprocess.check_output(['docker','inspect',CONTAINER],text=True))[0]['Config']['Env']}
        private=ROOT/'.tools/phase-b-remaining/env.sh'
        if not private.exists():
            private.parent.mkdir(parents=True,exist_ok=True)
            private.write_text('\n'.join('export '+k+'='+shlex.quote(v) for k,v in dict(AMA_TEST_CONTAINER=CONTAINER,AMA_TEST_DATABASE_URL='jdbc:mysql://127.0.0.1:58062/ama_b01_fixture',AMA_TEST_DATABASE_USERNAME=env['MYSQL_USER'],AMA_TEST_DATABASE_PASSWORD=env['MYSQL_PASSWORD']).items())+'\n');private.chmod(0o600)
        print(json.dumps(json.loads((OUT/'schema-red.json').read_text())))
