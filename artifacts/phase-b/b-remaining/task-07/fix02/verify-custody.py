"""Read-only exact fix02 custody; do not emit credentials or their digests."""
from pathlib import Path
import hashlib,json,subprocess,shlex,re,importlib.util,sys
O=Path('artifacts/phase-b/b-remaining/task-07');F=O/'fix02';W=Path('.superpowers/sdd/2026-10-23-ama-phase-b-remaining');A=W/'resume-fix02-before-records-20261024'
spec=importlib.util.spec_from_file_location('original',O/'verify-delivery.py');original=importlib.util.module_from_spec(spec);spec.loader.exec_module(original)
sha=original.sha;config=original.config;counts={}
for name,key in [('task-07-protected-adopted-inputs-20261024.json','files'),('task-07-pre-fix02-public-protected-20261024.json','entries'),('resume-fix02-before-records-20261024/manifest.json','entries')]:
 entries=json.loads((W/name).read_text())[key];changed=[x.get('archive',x['path']) for x in entries if not Path(x.get('archive',x['path'])).exists() or sha(x.get('archive',x['path']))!=x['sha256']];assert not changed,(name,changed);counts[name]=len(entries)
assert sha(W/'task-07-fix01-report-20261024.md')==sha(A/W/'task-07-fix01-report-20261024.md')
for resource in json.loads((W/'task-07-fix02-selected-resources-20261024.json').read_text())['resources']:
 s=resource['selected'];label=resource['ownerLabel'];fmt='{"id":{{json .Id}},"name":{{json .Name}},"image":{{json .Config.Image}},"owner":{{json (index .Config.Labels "'+label+'")}},"memory":{{json .HostConfig.Memory}},"swap":{{json .HostConfig.MemorySwap}},"cpus":{{json .HostConfig.NanoCpus}},"pids":{{json .HostConfig.PidsLimit}},"ports":{{json .NetworkSettings.Ports}},"mounts":{{json .Mounts}},"running":{{json .State.Running}},"oom":{{json .State.OOMKilled}}}'
 actual=json.loads(subprocess.check_output(['docker','inspect','--format',fmt,s['id']],text=True));actual['mounts']=sorted(actual['mounts'],key=lambda v:v['Destination']);assert actual==s,resource['role']
secrets=[config('.tools/phase-b-remaining/env.sh')['AMA_TEST_DATABASE_PASSWORD']]
s3=config('.tools/task05-5237a2db-e8d5-4cbe-b357-b35df5cfcba5/client.env');secrets += [s3['AMA_S3_ACCESS'],s3['AMA_S3_SECRET'],Path('.tools/task05-5237a2db-e8d5-4cbe-b357-b35df5cfcba5/pki/keystore-password').read_text().strip()]
secrets += [config('.tools/task08-848e93aace8b4f0fa34fc548267f3dc5/env.sh')['AMA_TEST_DATABASE_PASSWORD'],config('.tools/task08-848e93aace8b4f0fa34fc548267f3dc5/secrets/root.cnf')['password']]
assert len(set(secrets))==6 and all(secrets);assert config('.tools/task07-0cea85a0e4724e78bd7aa6aabfc243bc/env.sh')['AMA_TEST_DATABASE_PASSWORD'] in secrets
files=sorted(set(original.own()+[W/'task-07-fix02-report-20261024.md']));leaks=[]
for p in files:
 if not p.exists():continue
 data=p.read_bytes()
 if any(x.encode() in data for x in secrets) or re.search(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',data):leaks.append(str(p))
assert not leaks,leaks
for base in ['de1611a67ba483afe21f3b29bb69c2d32028efef','6cce4794dba473825d83fb9bccafc043cd611b5c']:
 diff=subprocess.check_output(['git','diff',base,'--','backend']);assert not any(x.encode() in diff for x in secrets)
pids=set()
for p in list(O.rglob('run.log'))+list(O.rglob('terminal-crash-child-status.txt')):
 pids.update(int(x) for x in re.findall(r'\b(?:pid|PID)=(\d+)',p.read_text()))
assert pids and not any(Path('/proc/'+str(p)).exists() for p in pids)
history=json.loads((F/'final-database.json').read_text());assert history['normalRerun']==0 and len(history['history'])==15 and history['history']==json.loads((O/'migration.json').read_text())['after']
result={'fixBase':'6cce4794dba473825d83fb9bccafc043cd611b5c','unchanged':counts,'fix01ReportImmutable':True,'privateScan':{'distinctKnownValues':6,'matches':0,'pemMatches':0,'preimageDiffMatches':0,'valuesOrDigestsEmitted':False},'selectedAll3ResourcesExact':True,'ownedChildPids':[{'pid':p,'gone':True} for p in sorted(pids)],'normalRerun':0,'all15FullHistoryFieldsUnchanged':True,'rootFilesExcluded':True,'scope':'Exact owned Task7 fixture only. No bootstrap/reset/schema/model/provider changes, no global process emptiness claim.'}
(F/(sys.argv[1] if len(sys.argv)>1 else 'custody-result.json')).write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({k:v for k,v in result.items() if k not in ['ownedChildPids','scope']}))
