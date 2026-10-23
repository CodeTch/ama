import pathlib,subprocess,json,hashlib,os,xml.etree.ElementTree as E
root=pathlib.Path.cwd(); evidence=root/'artifacts/phase-b/b-remaining/task-03'
base='3e393dbc9485b187b89bfc03abd88821a0aabddb'
def git(*a):return subprocess.check_output(['git',*a],text=True).strip()
assert git('branch','--show-current')=='codex/phase-b-completion-20261023'
staged=git('diff','--cached','--name-only').splitlines();assert all(p.startswith('artifacts/phase-b/b-remaining/task-03/') or p in (evidence/'owned-paths.txt').read_text().splitlines() for p in staged)
subprocess.run(['git','merge-base','--is-ancestor',base,'HEAD'],check=True)
parent=git('rev-parse','HEAD')
roots=git('diff','--name-only',base,'HEAD').splitlines();assert all(not p.startswith('backend/') for p in roots)
owned=sorted((evidence/'owned-paths.txt').read_text().splitlines())
assert len(owned)==19 and all(p.startswith('backend/') and p.endswith('.java') for p in owned),owned
subprocess.run(['git','diff','--check'],check=True)
assert git('diff',base,'--','backend/adapters/src/main/resources/db')==''
repository=git('diff',base,'--','backend/adapters/src/main/java/ama/meeting/JdbcMeetingRepository.java')
assert 'meeting_minutes_editor_guard' in (root/'backend/adapters/src/main/java/ama/meeting/JdbcMeetingRepository.java').read_text()
assert '.people.keySet()' in repository and '.ids()' in repository
sources={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in owned}
(evidence/'owned-source-manifest.json').write_text(json.dumps(sources,indent=2)+'\n')
(evidence/'owned-paths.txt').write_text('\n'.join(owned)+'\n')
summary={}
for label in ['authority-regressions','upload-regression-retry','authority-final','authority-final-normalized','dangling-red','exact-authorized-teardown']:
 rows=[]
 for p in sorted((evidence/(label+'-xml')).glob('*.xml')):
  x=E.parse(p).getroot();rows.append({'file':p.name,'suite':x.get('name'),**{k:int(x.get(k,'0')) for k in ['tests','failures','errors','skipped']}})
 summary[label]={'command':json.loads((evidence/(label+'.command.json')).read_text()),'suites':rows,'total':{k:sum(x[k] for x in rows) for k in ['tests','failures','errors','skipped']}}
(evidence/'verification-summary.json').write_text(json.dumps(summary,indent=2)+'\n')
final=json.loads((evidence/'authority-final-normalized.sources.json').read_text())
assert all(final[p]==v for p,v in sources.items())
bindings={}
for label in ['authority-regressions','upload-regression-retry','authority-final','authority-final-normalized']:
 old=json.loads((evidence/(label+'.sources.json')).read_text());bindings[label]={p:{'test_sha256':old[p],'final_sha256':v} for p,v in sources.items() if old[p]!=v}
(evidence/'verification-source-deltas.json').write_text(json.dumps(bindings,indent=2)+'\n')
# Read-only exact result set; prefix selects only Task3 synthetic fixtures, never used for UPDATE.
q="SELECT t.id,t.state,t.subject_id,t.input_id,m.owner_id,a.attempt,a.ended_at,a.outcome FROM durable_task t JOIN meeting m ON m.id=t.subject_id LEFT JOIN task_attempt a ON a.task_id=t.id WHERE t.task_type='TRANSCRIPT_IMPORT_V1' AND t.pool='TRANSCRIPT_IMPORT' AND m.owner_id LIKE 'authority-%' ORDER BY t.id,a.attempt LIMIT 501"
env=os.environ.copy();env['MYSQL_PWD']=env['AMA_TEST_DATABASE_PASSWORD']
r=subprocess.run(['docker','exec','-e','MYSQL_PWD','ama-test-b01-ad9f1af29eff','mysql','-u',env['AMA_TEST_DATABASE_USERNAME'],'--database=ama_b01_fixture','--batch','--raw','-e',q],env=env,capture_output=True,text=True);assert r.returncode==0
rows=r.stdout.splitlines();assert len(rows)<502
assert all(line.split('\t')[1] in ['SUCCEEDED','FAILED','CANCELLED'] for line in rows[1:])
assert all(line.split('\t')[6]!='NULL' for line in rows[1:])
(evidence/'final-synthetic-task-custody.tsv').write_text(r.stdout)
# Exact credential check produces no credential bytes.
secret=env['AMA_TEST_DATABASE_PASSWORD'].encode();hits=[]
for p in evidence.rglob('*'):
 if p.is_file() and secret in p.read_bytes():hits.append(str(p.relative_to(evidence)))
assert not hits,{'credential_hit_paths':hits}
receipt={'base':base,'precommit_head':parent,'root_only_committed_paths':roots,'owned_source_count':len(owned),'schema_diff_empty':True,'task2_guard_preserved':True,'index_empty_before_initial_staging':True,'current_index_only_owned_paths':True,'source_seal_matches_authority_final_normalized':True,'credential_hit_count':len(hits),'retained_synthetic_task_attempt_rows':len(rows)-1,'all_synthetic_tasks_terminal_and_attempts_ended':True,'original_registry_interface_diff':git('diff',base,'--','backend/core/src/main/java/ama/storage/FileReferencePolicy.java')}
(evidence/'self-review-checks.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps(receipt,indent=2));print('test_totals',json.dumps({k:v['total'] for k,v in summary.items()}))

