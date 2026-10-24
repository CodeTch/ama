"""Read-only owned delivery custody; never prints private values or their digests."""
from pathlib import Path
import hashlib,json,subprocess,shlex,re,os
ROOT=Path.cwd();OUT=Path('artifacts/phase-b/b-remaining/task-08');W=Path('.superpowers/sdd/2026-10-23-ama-phase-b-remaining')
BASE='12ca5436b2278e5cb08d60f4d8f41c1528a0e3a6'
MODELS=['docs/database/ama-data-dictionary.md','docs/database/ama-schema-draft.sql','docs/superpowers/specs/2026-10-21-ama-database-design.md','tools/checks/database_design.py']
PRODUCT=['backend/adapters/src/main/java/ama/task/JdbcExternalOperations.java','backend/adapters/src/main/java/ama/task/ReconciliationScanBudget.java','backend/adapters/src/main/resources/db/migration/V013_3__external_reconcile_scan.sql']
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def owned():
 paths=[Path(p) for p in MODELS+PRODUCT]+list(Path('backend/integration-tests/src/test/java/ama/task').glob('Task08*.java'))+[W/'task-08-report.md']
 for base in [OUT,Path('artifacts/phase-b/b-remaining/task-08-current-model-preservation')]:
  paths += [p for p in base.rglob('*') if p.is_file() and not any(x in p.parts for x in ('classes','__pycache__'))]
 return sorted(set(paths))
def config(p):
 result={}
 for line in Path(p).read_text().splitlines():
  line=line.removeprefix('export ')
  if '=' in line and not line.startswith('#'):
   k,v=line.split('=',1);a=shlex.split(v);result[k]=a[0] if a else ''
 return result
if __name__=='__main__':
 result={};snapshot=json.loads((W/'task-08-protected-adopted-inputs-20261024.json').read_text());exceptions=[]
 for e in snapshot['entries']:
  if not Path(e['path']).exists() or sha(e['path'])!=e['sha256']:exceptions.append(e['path'])
 assert sorted(exceptions)==sorted(MODELS),exceptions
 result['protected']={'entries':len(snapshot['entries']),'unchanged':len(snapshot['entries'])-len(exceptions),'authorizedCurrentModelExceptions':exceptions}
 dirty=subprocess.check_output(['git','diff','--name-only',BASE],text=True).splitlines();ownPaths={str(p) for p in owned()};allowed=ownPaths|{str(W/p) for p in ['current-state.json','progress.md','resume-20261024-active-context.md','observations.md']};unexpected=sorted(set(dirty)-allowed);assert not unexpected,unexpected
 result['trackedDiff']={'own':sorted(set(dirty)&ownPaths),'rootExcluded':sorted(set(dirty)-ownPaths),'unexpected':unexpected}
 prior=json.loads((OUT/'resource-guard-before.json').read_text());now=[]
 for i,expected in enumerate(prior):
  key='ama.remaining.owner' if i==0 else 'ama.test-fixture'
  fields={'id':'.Id','name':'.Name','running':'.State.Running','oom':'.State.OOMKilled','image':'.Config.Image','owner':'(index .Config.Labels "'+key+'")','memory':'.HostConfig.Memory','swap':'.HostConfig.MemorySwap','cpus':'.HostConfig.NanoCpus','pids':'.HostConfig.PidsLimit','ports':'.NetworkSettings.Ports','mounts':'.Mounts'}
  fmt='{'+','.join(json.dumps(k)+':{{json '+v+'}}' for k,v in fields.items())+'}'
  actual=json.loads(subprocess.check_output(['docker','inspect','--format',fmt,expected['id']],text=True));actual['ports']={k:v for k,v in actual['ports'].items() if v is not None};actual['mounts']=sorted(actual['mounts'],key=lambda x:x['Destination']);expected['mounts']=sorted(expected['mounts'],key=lambda x:x['Destination']);assert actual==expected,'Selected resource guard changed';now.append(actual)
 result['resourceGuards']=now
 secrets=[config('.tools/phase-b-remaining/env.sh')['AMA_TEST_DATABASE_PASSWORD']]
 s3=config('.tools/task05-5237a2db-e8d5-4cbe-b357-b35df5cfcba5/client.env');secrets += [s3['AMA_S3_ACCESS'],s3['AMA_S3_SECRET'],Path('.tools/task05-5237a2db-e8d5-4cbe-b357-b35df5cfcba5/pki/keystore-password').read_text().strip()]
 secrets += [config('.tools/task08-848e93aace8b4f0fa34fc548267f3dc5/env.sh')['AMA_TEST_DATABASE_PASSWORD'],config('.tools/task08-848e93aace8b4f0fa34fc548267f3dc5/secrets/root.cnf')['password']]
 assert len(set(secrets))==6 and all(secrets)
 files=[p for p in owned() if p.exists()];leaks=[]
 for p in files:
  data=p.read_bytes()
  if any(x.encode() in data for x in secrets) or re.search(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',data):leaks.append(str(p))
 diff=subprocess.check_output(['git','diff',BASE,'--']+MODELS+PRODUCT)
 assert not any(x.encode() in diff for x in secrets),'Private value in own diff'
 assert not leaks,leaks
 result['secretScan']={'distinctCurrentPrivateValues':6,'files':len(files),'matches':0,'privateKeyPatterns':0,'ownTrackedDiff':'PASS','valuesAndDigests':'not emitted'}
 pids={1229590}
 for path in OUT.glob('*/run.log'):
  for value in re.findall(r'\b(?:pid|PID)=(\d+)',path.read_text()):pids.add(int(value))
 result['recordedOwnedProcesses']=[{'pid':pid,'exists':Path('/proc/'+str(pid)).exists()} for pid in sorted(pids)]
 assert not any(x['exists'] for x in result['recordedOwnedProcesses']),'Recorded owned process remains'
 result['scope']='Only recorded owned PIDs and selected two MySQL resources; no global active0 or company-service claim.'
 (OUT/'delivery-custody.json').write_text(json.dumps(result,indent=2)+'\n')
 print(json.dumps({'protectedUnchanged':result['protected']['unchanged'],'modelExceptions':4,'secretValuesScanned':6,'matches':0,'selectedResources':2,'recordedPidsGone':len(pids)}))
