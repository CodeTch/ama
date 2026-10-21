"""Read-only final evidence verification; no fixture mutation or credential output."""
import datetime,hashlib,json,pathlib,subprocess,xml.etree.ElementTree as E
root=pathlib.Path(__file__).resolve().parents[2]
d=root/'.tools/publication-schema-review'; f=root/'.tools/publication-schema-formal'
sha=lambda b:hashlib.sha256(b).hexdigest()
load=lambda p:json.loads(p.read_text())
source=load(root/'docs/acceptance/b/database-generation-addendum-metadata-evidence.json')
expected=load(root/'backend/integration-tests/src/test/resources/ama/db/publication-schema-b00-1.3.json')
tables=expected['tables']
independent={k:[x for x in source['metadata'][k] if x['table'] in tables and not(k=='foreign_keys' and x['name']=='fk_final_confirmation_9')] for k in ['tables','columns','indexes','checks','foreign_keys']}
assert independent==expected['metadata']
before=load(f/'lower-before.json'); upgraded=load(f/'after-upgrade.json'); final=load(f/'after-tests.json')
for name in ['after-upgrade.json','private-target-rerun-zero.json','production-latest-rerun-zero.json']:
    assert (f/name).read_bytes()==(f/'after-tests.json').read_bytes(),name
actual={k:[x for x in final['metadata'][k] if x['table'] in tables] for k in independent}
assert actual==independent
for table,ddl in before['ddl'].items(): assert final['ddl'][table]==ddl
for table,rows in before['rows'].items():
    assert (final['rows'][table][:-1] if table=='flyway_schema_history' else final['rows'][table])==rows,table
for group,rows in before['metadata'].items(): assert [x for x in final['metadata'][group] if x['table'] not in tables]==rows
assert all(final['rows'][t]==[] for t in tables)
assert final['lower_sql_sha256']==before['lower_sql_sha256']
assert len(before['ddl'])==58 and len(final['ddl'])==63
assert sha((d/'V013__publications.sql.review-only').read_bytes())=='1de37066869617723a11f05d7423584143a64e0c9ddf8879c7b4fd107de08e3c'
for p in ['backend/adapters/src/main/resources/db/migration/V013__publications.sql','backend/adapters/target/classes/db/migration/V013__publications.sql']:
    assert (root/p).read_bytes()==(d/'initial-V013__publications.sql.review-only').read_bytes()
for p in (root/'backend/adapters/src/main/resources/db/migration').glob('V*.sql'):
    if p.name=='V013__publications.sql': continue
    original=subprocess.check_output(['git','show','7f061e8a9d55f5b0db92762692edbe61123d400b:'+str(p.relative_to(root))],cwd=root)
    assert p.read_bytes()==original
red=E.parse(d/'red.xml').getroot(); green=E.parse(d/'first-full.xml').getroot()
for x,want in [(red,['1','1','0','0']),(green,['10','0','0','0'])]: assert [x.attrib[k] for k in ['tests','failures','errors','skipped']]==want
ack=load(d/'root-ack.json'); native=load(d/'native-history-final.json'); fixture=load(d/'fixture-identity.json')
assert fixture['inspection']==native['inspection']
assert native['history'][-1]['version']=='013' and native['history'][-1]['success']==1
commit_epoch=int(subprocess.check_output(['git','show','-s','--format=%ct',ack['register_commit']],cwd=root,text=True).strip())
ack_epoch=datetime.datetime.fromisoformat(ack['ack_utc']).timestamp()
assert commit_epoch<=ack_epoch<=native['history'][-1]['installed_epoch']
assert native['clock']['native_aio']==0 and native['clock']['session_time_zone']=='+00:00'
assert [x['version'] for x in native['history']]==['001','002','005','006','007','008','009','010','011','011.1','012','013']
result=dict(status='AUTHOR_FUNCTIONAL_GREEN_FRESH_INDEPENDENT_REVIEW_REQUIRED',captured_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),counts={k:len(v) for k,v in actual.items()},exact_five_group_equality=True,complete_lower_preservation=True,new_rows_all_empty_after_rollback=True,four_full_snapshots_byte_equal=True,final_snapshot_sha256=sha((f/'after-tests.json').read_bytes()),red=dict(tests=1,failures=1,errors=0,skipped=0),green=dict(tests=10,failures=0,errors=0,skipped=0),source_and_classpath_frozen_equal=True,central_register_commit=ack['register_commit'],central_register_commit_epoch=commit_epoch,ack_utc=ack['ack_utc'],native_first_application=native['history'][-1],fixture=fixture['name'])
output=d/'final-checks.json'; assert not output.exists(); output.write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
