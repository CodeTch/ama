import pathlib,subprocess,json,hashlib,os,re,xml.etree.ElementTree as E,shutil
root=pathlib.Path.cwd();e=root/'artifacts/phase-b/b-remaining/task-04';base='2ec38c0350f466fc4292d588051c5694cc099cb5'
def git(*a):return subprocess.check_output(['git',*a],text=True).strip()
assert git('branch','--show-current')=='codex/phase-b-completion-20261023'
assert git('diff','--cached','--name-only')==''
assert git('rev-parse','HEAD')=='875047cdbcd6ddd17f5c267f8998b7a5a4378b6c'
subprocess.run(['git','merge-base','--is-ancestor',base,'HEAD'],check=True)
rootdocs=git('diff','--name-only',base,'HEAD').splitlines();assert all(not p.startswith(('backend/','contracts/')) for p in rootdocs)
owned=(e/'owned-paths.txt').read_text().splitlines();assert len(owned)==32 and len(set(owned))==32
actual=set(git('diff','--name-only').splitlines()+git('ls-files','--others','--exclude-standard','--','backend','contracts').splitlines());assert actual==set(owned),actual^set(owned)
subprocess.run(['git','diff','--check'],check=True)
assert git('diff',base,'--','backend/adapters/src/main/resources/db')==''
assert git('diff',base,'--','backend/core/src/main/java/ama/storage/FileReferencePolicy.java')==''
assert 'meeting_minutes_editor_guard' in (root/'backend/adapters/src/main/java/ama/meeting/JdbcMeetingRepository.java').read_text()
sources={p:hashlib.sha256((root/p).read_bytes()).hexdigest() for p in owned};(e/'owned-source-manifest.json').write_text(json.dumps(sources,indent=2)+'\n')
summary={};bindings={}
for cmd in sorted(e.glob('*.command.json')):
 label=cmd.name.removesuffix('.command.json');rows=[]
 for p in sorted((e/(label+'-xml')).glob('*.xml')):
  x=E.parse(p).getroot();rows.append({'file':p.name,'suite':x.get('name'),**{k:int(x.get(k,'0')) for k in ['tests','failures','errors','skipped']}})
 summary[label]={'command':json.loads(cmd.read_text()),'suites':rows,'total':{k:sum(r[k] for r in rows) for k in ['tests','failures','errors','skipped']}}
 old=json.loads((e/(label+'.sources.json')).read_text());bindings[label]={p:{'test_sha256':old.get(p),'final_sha256':v} for p,v in sources.items() if p.endswith('.java') and old.get(p)!=v}
for label in ['final-regressions','all-unit-final-resumed','config-restart-final']:
 assert summary[label]['command']['exit_code']==0 and summary[label]['total']['tests']>0 and not any(summary[label]['total'][k] for k in ['failures','errors','skipped'])
 assert bindings[label]=={},(label,bindings[label])
for label in ['openapi-size-final-lint','openapi-size-final-bundle']:assert summary[label]['command']['exit_code']==0
(e/'verification-summary.json').write_text(json.dumps(summary,indent=2)+'\n');(e/'verification-source-deltas.json').write_text(json.dumps(bindings,indent=2)+'\n')
restart=pathlib.Path(summary['config-restart-final']['command']['AMA_B06_RESTART_EVIDENCE']);assert restart.parent==(root/'backend/integration-tests/target').resolve() and restart.name.startswith('task04-config-restart-')
assert (e/'config-restart-final-retained').exists(), 'Retained exact config restart copied by source audit'
# Guarded read-only SQL; no environment or credential output.
env=os.environ.copy()
for line in (root/'.tools/phase-b-remaining/env.sh').read_text().splitlines():
 import shlex
 key,value=line.removeprefix('export ').split('=',1);env[key]=shlex.split(value)[0]
env['MYSQL_PWD']=env['AMA_TEST_DATABASE_PASSWORD']
fmt='{"Id":{{json .Id}},"Running":{{json .State.Running}},"Ports":{{json .NetworkSettings.Ports}},"Labels":{{json .Config.Labels}}}'
info=json.loads(subprocess.check_output(['docker','inspect','--format',fmt,'ama-test-b01-ad9f1af29eff']));assert info['Id']=='eefdd9a984ee00fe6ca886168b29a6ce4f3b405467b8d3204240d4e1a8dc7502' and info['Running'] and info['Labels']['ama.remaining.owner']=='remaining-20261023' and info['Ports']['3306/tcp']==[{'HostIp':'127.0.0.1','HostPort':'45157'}]
def sql(q):
 r=subprocess.run(['docker','exec','-e','MYSQL_PWD','ama-test-b01-ad9f1af29eff','mysql','-u',env['AMA_TEST_DATABASE_USERNAME'],'--database=ama_b01_fixture','--batch','--raw','-e',q],env=env,capture_output=True,text=True);assert r.returncode==0,'read-only SQL failed';return r.stdout
history=sql('SELECT installed_rank,version,description,type,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank LIMIT 14');assert len(history.splitlines())==14 and '-1898004857' in history
(e/'final-migration-history.tsv').write_text(history)
q="SELECT t.id,t.state,t.subject_id,t.input_id,m.owner_id,a.attempt,a.ended_at,a.outcome FROM durable_task t JOIN meeting m ON m.id=t.subject_id LEFT JOIN task_attempt a ON a.task_id=t.id WHERE t.task_type='TRANSCRIPT_IMPORT_V1' AND t.pool='TRANSCRIPT_IMPORT' AND m.owner_id LIKE 'task04-%' ORDER BY t.id,a.attempt LIMIT 1001"
rows=sql(q);assert len(rows.splitlines())<1002
(e/'final-task04-custody.tsv').write_text(rows)
nonterminal=[line for line in rows.splitlines()[1:] if line.split('\t')[1] not in ['SUCCEEDED','FAILED','CANCELLED','STALE'] or line.split('\t')[5]!='NULL' and line.split('\t')[6]=='NULL']
(e/'nonterminal-custody.json').write_text(json.dumps(nonterminal,indent=2)+'\n')
assert not nonterminal,'Own tasks require exact custody review; no prefix mutation authorized'
secret=env['AMA_TEST_DATABASE_PASSWORD'].encode();hit=[]
scan=[p for p in e.rglob('*') if p.is_file()]+[root/p for p in owned]+[root/'.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-04-report.md']
for p in scan:
 if p.exists() and secret in p.read_bytes():hit.append(str(p.relative_to(root)))
assert not hit,{'credential_hit_paths':hit}
checks={'base':base,'precommit_head':git('rev-parse','HEAD'),'branch':git('branch','--show-current'),'owned_source_count':len(owned),'root_only_intervening_committed_paths':rootdocs,'index_empty_before_staging':True,'actual_working_source_paths_exact':True,'schema_diff_empty':True,'formal_migration_history_rows':13,'current_restored_host_port':45157,'historical_test_host_port':58062,'operational_restore_is_not_database_restart_qualification':True,'original_reference_policy_unchanged':True,'task2_editor_guard_preserved':True,'final_gate_java_source_seals_match':True,'credential_hit_count':0,'retained_task04_task_attempt_rows':len(rows.splitlines())-1,'all_own_tasks_terminal_and_attempts_ended':True,'source_count_by_kind':{'java':28,'openapi':4},'config_restart_retained_path':str(restart)}
(e/'self-review-checks.json').write_text(json.dumps(checks,indent=2)+'\n')
print(json.dumps(checks,indent=2));print('FINAL_COUNTS',json.dumps({k:summary[k]['total'] for k in ['final-focused','lost-ack-custody-final','final-regressions','config-restart-final','all-unit-final-resumed']}))

