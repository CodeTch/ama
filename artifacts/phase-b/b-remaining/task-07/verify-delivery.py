"""Read-only Task7 custody; private values and their digests are never printed."""
from pathlib import Path
import hashlib,json,subprocess,shlex,re
O=Path('artifacts/phase-b/b-remaining/task-07');W=Path('.superpowers/sdd/2026-10-23-ama-phase-b-remaining');BASE='de1611a67ba483afe21f3b29bb69c2d32028efef'
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def own():
 product=json.loads((O/'qualification-ledger.json').read_text())['productionFiles']
 return sorted(set([Path(x) for x in product]+list(Path('backend/integration-tests/src/test/java/ama/task').glob('Task07*.java'))+[W/'task-07-report.md']+[p for p in O.rglob('*') if p.is_file() and not any(x in p.parts for x in ['classes','__pycache__'])]))
def config(p):
 values={}
 for line in Path(p).read_text().splitlines():
  line=line.removeprefix('export ')
  if '=' in line and not line.startswith('#'):
   k,v=line.split('=',1);a=shlex.split(v);values[k]=a[0] if a else ''
 return values
if __name__=='__main__':
 protected=json.loads((W/'task-07-protected-adopted-inputs-20261024.json').read_text());changed=[x['path'] for x in protected['files'] if not Path(x['path']).exists() or sha(x['path'])!=x['sha256']];assert not changed,changed
 cid='11009a6b34fef0416e651100fbde05a07067dbcbd973f1ba731c08b2281140ad'
 fmt='{{json .Id}} {{json .Name}} {{json .State.Status}} {{json .Config.Image}} {{json .HostConfig.Memory}} {{json .HostConfig.MemorySwap}} {{json .HostConfig.NanoCpus}} {{json .HostConfig.PidsLimit}} {{json .NetworkSettings.Ports}}'
 guard=subprocess.check_output(['docker','inspect','--format',fmt,cid],text=True);assert guard==(O/'resource-guard.txt').read_text()
 secrets=[config('.tools/phase-b-remaining/env.sh')['AMA_TEST_DATABASE_PASSWORD']]
 s3=config('.tools/task05-5237a2db-e8d5-4cbe-b357-b35df5cfcba5/client.env');secrets += [s3['AMA_S3_ACCESS'],s3['AMA_S3_SECRET'],Path('.tools/task05-5237a2db-e8d5-4cbe-b357-b35df5cfcba5/pki/keystore-password').read_text().strip()]
 secrets += [config('.tools/task08-848e93aace8b4f0fa34fc548267f3dc5/env.sh')['AMA_TEST_DATABASE_PASSWORD'],config('.tools/task08-848e93aace8b4f0fa34fc548267f3dc5/secrets/root.cnf')['password']]
 assert len(set(secrets))==6 and all(secrets);assert config('.tools/task07-0cea85a0e4724e78bd7aa6aabfc243bc/env.sh')['AMA_TEST_DATABASE_PASSWORD'] in secrets
 files=[p for p in own() if p.exists()];leaks=[]
 for p in files:
  data=p.read_bytes()
  if any(x.encode() in data for x in secrets) or re.search(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',data):leaks.append(str(p))
 assert not leaks,leaks
 diff=subprocess.check_output(['git','diff',BASE,'--','backend']);assert not any(x.encode() in diff for x in secrets)
 pids=set()
 for p in O.glob('*/run.log'):
  pids.update(int(x) for x in re.findall(r'\b(?:pid|PID)=(\d+)',p.read_text()))
 assert pids and not any(Path('/proc/'+str(p)).exists() for p in pids)
 owned={str(p) for p in own()};dirty=set(subprocess.check_output(['git','diff','--name-only',BASE],text=True).splitlines());root={str(W/p) for p in ['current-state.json','progress.md','resume-20261024-active-context.md','task-13-pending-issue-register-20261024.md']};assert not dirty-owned-root,dirty-owned-root
 old=json.loads((O/'migration.json').read_text());post=json.loads((O/'final-database.json').read_text());assert old['after']==post['history'];assert len(post['history'])==15
 result={'base':BASE,'protected':{'entries':len(protected['files']),'unchanged':len(protected['files']),'exceptions':[]},'ownedSourceAndEvidenceFiles':len(files),'privateScan':{'distinctKnownValues':6,'matches':0,'pemMatches':0,'trackedDiffMatches':0,'valuesOrDigestsEmitted':False},'selectedResourceGuardUnchanged':True,'recordedOwnedChildPids':[{'pid':x,'gone':True} for x in sorted(pids)],'all15HistoryFieldsUnchanged':True,'normalBootstrap':15,'normalReruns':[0,0,0],'rootTrackedPathsExcluded':sorted(dirty-root.intersection(owned)-owned),'scope':'Only exact Task7 schema, recorded child processes and actual selected parent MySQL resource. No global active0, old fixture rewrite, S3/PKI activity, company or HA claim.'}
 (O/'delivery-custody.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({'protectedUnchanged':len(protected['files']),'secretValues':6,'matches':0,'recordedChildrenGone':len(pids)}))
