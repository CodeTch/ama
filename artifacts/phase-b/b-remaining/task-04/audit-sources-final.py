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
print('FINAL_SOURCE_AND_XML_AUDIT_PASS',len(sources))
print('FINAL_COUNTS',json.dumps({k:summary[k]['total'] for k in ['final-regressions','config-restart-final','all-unit-final-resumed']}))
