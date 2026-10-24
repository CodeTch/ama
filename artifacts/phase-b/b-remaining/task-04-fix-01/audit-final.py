from pathlib import Path
import os,subprocess,json,hashlib,xml.etree.ElementTree as ET,re
r=Path.cwd();e=r/'artifacts/phase-b/b-remaining/task-04-fix-01';base='f176382618924d9a00fd5fbabf93bbc30c78dd71'
def git(*args):return subprocess.check_output(['git',*args],cwd=r,text=True).strip()
owned=(e/'owned-paths.txt').read_text().splitlines();manifest=json.loads((e/'owned-source-manifest.json').read_text())
assert git('rev-parse','HEAD')==base and not git('diff','--cached','--name-only')
assert sorted(git('diff','--name-only').splitlines())==sorted(owned)
assert all(hashlib.sha256((r/p).read_bytes()).hexdigest()==h for p,h in manifest.items())
original=json.loads((e/'original-task04-proof.json').read_text());assert all(hashlib.sha256((r/p).read_bytes()).hexdigest()==h for p,h in original.items())
assert not git('diff',base,'--','backend/adapters/src/main/resources/db')
summary={}
for p in sorted(e.glob('*.command.json')):
 label=p.name.removesuffix('.command.json');command=json.loads(p.read_text());rows=[]
 for x in sorted((e/(label+'-xml')).glob('*.xml')):
  t=ET.parse(x).getroot();rows.append({'suite':t.attrib['name'],**{k:int(t.attrib[k]) for k in ['tests','failures','errors','skipped']},'file':str(x.relative_to(e)),'sha256':hashlib.sha256(x.read_bytes()).hexdigest()})
 summary[label]={'command':command,'junit':rows,'total':{k:sum(t[k] for t in rows) for k in ['tests','failures','errors','skipped']}}
final=summary['covering-final-3'];recovery=summary['covering-missing-2']
assert final['command']['exit_code']==1 and final['total']==dict(tests=26,failures=1,errors=1,skipped=0),final
assert recovery['command']['exit_code']==0 and recovery['total']==dict(tests=4,failures=0,errors=0,skipped=0),recovery
assert {t['suite']:t['tests'] for t in final['junit']}=={'ama.storage.ProcessingIntakeHttpIT':24,'ama.api.HttpRevisionTokensTest':1,'ama.transcript.TranscriptImportProducerTest':1}
passed=set();failed=[]
for label in ['covering-final-3','covering-missing-2']:
 for p in (e/(label+'-xml')).glob('*.xml'):
  t=ET.parse(p).getroot()
  for case in t.findall('testcase'):
   identity=t.attrib['name']+'#'+case.attrib['name']
   if case.find('failure') is None and case.find('error') is None:passed.add(identity)
   else:failed.append({'label':label,'case':identity})
assert len(passed)==26 and len(failed)==2,(passed,failed)
for label in ['covering-final-3','covering-missing-2']:
 snapshot=json.loads((e/(label+'.sources.json')).read_text());assert all(snapshot[p]==h for p,h in manifest.items())
for label in ['schema-green','http-schema-final','openapi-lint','openapi-bundle','generate-api','generated-api-check']:assert summary[label]['command']['exit_code']==0
snapshot=json.loads((e/'covering-final-3.sources.json').read_text());assert all(snapshot[p]==h for p,h in manifest.items())
contract='contracts/openapi/processing.yaml';generated='web/src/shared/api/generated.ts'
for label in ['schema-green','openapi-lint','openapi-bundle','generate-api','generated-api-check']:
 s=json.loads((e/(label+'.sources.json')).read_text());assert s[contract]==manifest[contract] and s[generated]==hashlib.sha256((r/generated).read_bytes()).hexdigest()
assert not git('diff','--',generated),'Generator should be byte-identical in this correction'
# Read-only final exact-owned-task custody; never issue prefix mutation.
q="SELECT t.id,t.state,t.subject_id,t.input_id,m.owner_id,a.attempt,a.ended_at,a.outcome FROM durable_task t JOIN meeting m ON m.id=t.subject_id LEFT JOIN task_attempt a ON a.task_id=t.id WHERE t.task_type='TRANSCRIPT_IMPORT_V1' AND t.pool='TRANSCRIPT_IMPORT' AND m.owner_id LIKE 'task04-%' ORDER BY t.id,a.attempt LIMIT 1001"
p=subprocess.run(['docker','exec','-e','MYSQL_PWD','ama-test-b01-ad9f1af29eff','mysql','-u',os.environ['AMA_TEST_DATABASE_USERNAME'],'--database=ama_b01_fixture','--batch','--raw','-e',q],capture_output=True,text=True);assert p.returncode==0;rows=p.stdout.splitlines();assert len(rows)<1002
assert all(line.split('\t')[1] in ['SUCCEEDED','FAILED','CANCELLED','STALE'] and (line.split('\t')[5]=='NULL' or line.split('\t')[6]!='NULL') for line in rows[1:])
(e/'final-task04-custody.tsv').write_text(p.stdout)
accepted=set()
for p in e.glob('*-custody/*-accepted-specs.json'):accepted.update(json.loads(p.read_text()).keys())
actual={line.split('\t')[0] for line in rows[1:]};assert accepted<=actual
# Exact known SQL/native TLS passwords never print.
text=(r/'backend/integration-tests/src/test/java/ama/storage/LocalFixture.java').read_text();secret=re.search(r'PASSWORD\s*=\s*"([^"\n]+)"',text).group(1).encode()
secrets={'owned_database':os.environ['AMA_TEST_DATABASE_PASSWORD'].encode(),'native_tls_fixture':secret}
scan=[p for p in e.rglob('*') if p.is_file()]+[r/p for p in owned]+[r/'.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-04-report.md']
hits={key:[str(p.relative_to(r)) for p in scan if value in p.read_bytes()] for key,value in secrets.items()};assert not any(hits.values()),hits
(e/'verification-summary.json').write_text(json.dumps(summary,indent=2)+'\n')
seal={'base':base,'branch':git('branch','--show-current'),'owned_sources':manifest,'covering_labels':['covering-final-3','covering-missing-2'],'covering_full_run_total':final['total'],'covering_recovery_run_total':recovery['total'],'distinct_passing_cases_on_final_source':len(passed),'retained_full_run_nonpassing_cases':failed,'one_clean_24case_invocation_claimed':False,'covering_all_source_seal_sha256':hashlib.sha256((e/'covering-final-3.sources.json').read_bytes()).hexdigest(),'all_original_task04_files_unchanged':len(original),'formal_sql_diff_empty':True,'index_empty_before_scoped_staging':True,'fresh_xml_counts_verified':True,'generated_api_regenerated_byte_identical':True,'observed_mysql_endpoint':46653,'owned_task_attempt_rows_terminal':len(rows)-1,'new_evidence_accepted_task_ids':len(accepted),'exact_password_hit_counts':{key:0 for key in secrets},'password_values_printed':False,'source_queries_on_historical_replay':0,'hashes_under_genuine_roots':0,'worker_business_qualification':'NOT_INSTALLED / businessReady=false'}
(e/'covering-exact-seal.json').write_text(json.dumps(seal,indent=2)+'\n');print(json.dumps(seal,indent=2))
