"""Task7 fix01 custody. Values/digests of credentials are never emitted."""
from pathlib import Path
import hashlib,json,subprocess,shlex,re,importlib.util,sys
O=Path('artifacts/phase-b/b-remaining/task-07');F=O/'fix01';W=Path('.superpowers/sdd/2026-10-23-ama-phase-b-remaining')
spec=importlib.util.spec_from_file_location('original',O/'verify-delivery.py');original=importlib.util.module_from_spec(spec);spec.loader.exec_module(original)
sha=original.sha;config=original.config
protected=json.loads((W/'task-07-protected-adopted-inputs-20261024.json').read_text())['files']
old=json.loads((W/'task-07-pre-fix01-delivery-protected-20261024.json').read_text())['entries']
for label,entries in [('adopted',protected),('initialDelivery',old)]:
 changed=[x['path'] for x in entries if not Path(x['path']).exists() or sha(x['path'])!=x['sha256']]
 assert not changed,(label,changed)
fmt='{{json .Id}} {{json .Name}} {{json .State.Status}} {{json .Config.Image}} {{json .HostConfig.Memory}} {{json .HostConfig.MemorySwap}} {{json .HostConfig.NanoCpus}} {{json .HostConfig.PidsLimit}} {{json .NetworkSettings.Ports}}'
guard=subprocess.check_output(['docker','inspect','--format',fmt,'11009a6b34fef0416e651100fbde05a07067dbcbd973f1ba731c08b2281140ad'],text=True);assert guard==(O/'resource-guard.txt').read_text()
secrets=[config('.tools/phase-b-remaining/env.sh')['AMA_TEST_DATABASE_PASSWORD']]
s3=config('.tools/task05-5237a2db-e8d5-4cbe-b357-b35df5cfcba5/client.env');secrets += [s3['AMA_S3_ACCESS'],s3['AMA_S3_SECRET'],Path('.tools/task05-5237a2db-e8d5-4cbe-b357-b35df5cfcba5/pki/keystore-password').read_text().strip()]
secrets += [config('.tools/task08-848e93aace8b4f0fa34fc548267f3dc5/env.sh')['AMA_TEST_DATABASE_PASSWORD'],config('.tools/task08-848e93aace8b4f0fa34fc548267f3dc5/secrets/root.cnf')['password']]
assert len(set(secrets))==6 and all(secrets)
files=sorted(set(original.own()+[W/'task-07-fix01-report-20261024.md']))
leaks=[]
for p in files:
 if not p.exists():continue
 data=p.read_bytes()
 if any(x.encode() in data for x in secrets) or re.search(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',data):leaks.append(str(p))
assert not leaks,leaks
for base in ['de1611a67ba483afe21f3b29bb69c2d32028efef','84b4b0bbf8212ec5606c3f8583a0989f10af681e']:
 diff=subprocess.check_output(['git','diff',base,'--','backend']);assert not any(x.encode() in diff for x in secrets)
pids=set()
for p in list(O.rglob('run.log'))+list(F.rglob('terminal-crash-child-status.txt')):
 pids.update(int(x) for x in re.findall(r'\b(?:pid|PID)=(\d+)',p.read_text()))
assert pids and not any(Path('/proc/'+str(p)).exists() for p in pids)
history=json.loads((F/'final-database.json').read_text());assert history['normalRerun']==0 and len(history['history'])==15 and history['history']==json.loads((O/'migration.json').read_text())['after']
result={'fixBase':'84b4b0bbf8212ec5606c3f8583a0989f10af681e','protectedAdoptedUnchanged':len(protected),'initialDeliveryImmutableUnchanged':len(old),'privateScan':{'distinctKnownValues':6,'matches':0,'pemMatches':0,'preimageDiffMatches':0,'valuesOrDigestsEmitted':False},'selectedResourceGuardUnchanged':True,'ownedChildPids':[{'pid':p,'gone':True} for p in sorted(pids)],'normalRerun':0,'all15FullHistoryFieldsUnchanged':True,'rootFilesExcluded':True,'scope':'Exact owned Task7 fixture only; no initial migration/reset/schema/model/provider changes; no global emptiness/company qualification claim.'}
(F/(sys.argv[1] if len(sys.argv)>1 else 'custody-result.json')).write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({k:v for k,v in result.items() if k not in ['ownedChildPids','scope']}))
