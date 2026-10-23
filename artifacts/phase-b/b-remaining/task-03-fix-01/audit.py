import pathlib,subprocess,hashlib,json,os,re,xml.etree.ElementTree as ET
root=pathlib.Path.cwd();e=root/'artifacts/phase-b/b-remaining/task-03-fix-01';old=root/'artifacts/phase-b/b-remaining/task-03'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def git(*a):return subprocess.check_output(['git',*a],text=True).strip()
assert git('rev-parse','HEAD')=='5560381927d51001bd484642269b9432481e8c70'
assert not git('diff','--cached','--name-only')
base='aa0b5f08a19649091b0bcd60959bd741fb5c7850'
assert all(not p.startswith('backend/') for p in git('diff','--name-only',base,'HEAD').splitlines())
owned=['backend/adapters/src/main/java/ama/storage/FileBackendCatalog.java','backend/adapters/src/main/java/ama/storage/JdbcFileRegistry.java','backend/integration-tests/src/test/java/ama/storage/TranscriptAuthorityReviewIT.java']
assert set(git('diff','--name-only').splitlines()+git('ls-files','--others','--exclude-standard').splitlines())==set(owned)
subprocess.run(['git','diff','--check'],check=True)
assert sha(old/'evidence-manifest.json')=='56767a16ef4ecd1266bcde2f383bc5747a06cc94803abe811ebe4169b4f4e4d6'
old_manifest=json.loads((old/'evidence-manifest.json').read_text());assert len(old_manifest)==416
assert {str(p.relative_to(old)) for p in old.rglob('*') if p.is_file()}==set(old_manifest)|{'evidence-manifest.json'}
for name,item in old_manifest.items():assert sha(old/name)==item['sha256'] and (old/name).stat().st_size==item['bytes']
assert git('diff',base,'--','backend/adapters/src/main/resources/db','backend/adapters/src/main/java/ama/meeting/JdbcMeetingRepository.java')==''
assert sha(root/'.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-03-review.md')=='60f3e807d0d6e79fbdeafce015b063ce0acefbf910c0ec252dab25484952149c'
sources={p:sha(root/p) for p in owned};run=json.loads((e/'covering-final.sources.json').read_text());assert all(run[p]==h for p,h in sources.items())
(e/'owned-source-manifest.json').write_text(json.dumps(sources,indent=2)+'\n')
summary={}
for label in ['hash-red','hash-green','multiscope-proof','covering-final']:
 suites=[]
 for p in sorted((e/(label+'-xml')).glob('*.xml')):
  x=ET.parse(p).getroot();suites.append({'file':p.name,'suite':x.get('name'),**{k:int(x.get(k,'0')) for k in ['tests','errors','failures','skipped']}})
 summary[label]={'command':json.loads((e/(label+'.command.json')).read_text()),'suites':suites,'totals':{k:sum(x[k] for x in suites) for k in ['tests','errors','failures','skipped']}}
assert summary['covering-final']['totals']=={'tests':35,'errors':0,'failures':0,'skipped':0}
(e/'verification-summary.json').write_text(json.dumps(summary,indent=2)+'\n')
# Only exact UUIDs enumerated in fresh fix-run receipts are selected. No prefix mutation or cleanup.
ids=set()
for p in e.glob('*-custody/*.txt'):
 for match in re.finditer(r'synthetic-read-fixture task=([0-9a-f-]{36}) state=(\w+)',p.read_text()):
  assert match[2] in ['FAILED','SUCCEEDED'];ids.add(match[1])
assert ids
q="SELECT t.id,t.state,t.subject_id,t.input_id,m.owner_id,a.attempt,a.ended_at,a.outcome FROM durable_task t JOIN meeting m ON m.id=t.subject_id JOIN task_attempt a ON a.task_id=t.id WHERE t.id IN ("+','.join("'"+i+"'" for i in sorted(ids))+") ORDER BY t.id,a.attempt"
env=os.environ.copy();env['MYSQL_PWD']=env['AMA_TEST_DATABASE_PASSWORD'];r=subprocess.run(['docker','exec','-e','MYSQL_PWD','ama-test-b01-ad9f1af29eff','mysql','-u',env['AMA_TEST_DATABASE_USERNAME'],'--database=ama_b01_fixture','--batch','--raw','-e',q],env=env,capture_output=True,text=True);assert r.returncode==0
rows=r.stdout.splitlines()[1:];assert len(rows)==len(ids)
assert all(line.split('\t')[1] in ['FAILED','SUCCEEDED'] and line.split('\t')[6]!='NULL' for line in rows)
(e/'new-exact-task-custody.tsv').write_text(r.stdout)
secret=env['AMA_TEST_DATABASE_PASSWORD'].encode();hits=[str(p.relative_to(e)) for p in e.rglob('*') if p.is_file() and secret in p.read_bytes()];assert not hits
receipt={'fix_base':base,'precommit_head':git('rev-parse','HEAD'),'review_sha256':'60f3e807d0d6e79fbdeafce015b063ce0acefbf910c0ec252dab25484952149c','owned_source_paths':owned,'old_task03_files_verified_unchanged':417,'schema_and_creator_guard_unchanged':True,'source_seal_matches_covering_final':True,'index_empty':True,'credential_hits':len(hits),'new_exact_terminal_tasks':len(ids),'new_exact_ended_attempts':len(rows),'tests':summary['covering-final']['totals']}
(e/'self-review-checks.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps(receipt,indent=2))
