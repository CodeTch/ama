from pathlib import Path
import json,subprocess,datetime,stat,hashlib
r=Path('/home/tanren/ama/.worktrees/phase-b-completion');e=r/'artifacts/phase-b/b-remaining/task-05';owned=r/'.tools/task05-5237a2db-e8d5-4cbe-b357-b35df5cfcba5'
mysql=json.loads((e/'mysql-guard.json').read_text());s3=json.loads((e/'run-5237a2db-e8d5-4cbe-b357-b35df5cfcba5/resource-receipt.json').read_text())['container'];records=[]
for expected,label in [(mysql,'ama.remaining.owner'),(s3,'ama.owner')]:
 c=json.loads(subprocess.check_output(['docker','inspect',expected['id']],text=True))[0]
 actual={'id':c['Id'],'name':c['Name'],'image':c['Config']['Image'],'owner':c['Config']['Labels'][label],'memory':c['HostConfig']['Memory'],'nanoCpus':c['HostConfig']['NanoCpus'],'pids':c['HostConfig']['PidsLimit'],'ports':c['NetworkSettings']['Ports']}
 for key,value in actual.items():assert value==expected[key],key
 assert c['State']['Status']=='running'
 if label=='ama.remaining.owner':assert c['Config']['Cmd']==expected['cmd']
 else:
  assert c['HostConfig']['MemorySwap']==expected['memorySwap'];assert c['Mounts']==expected['mounts']
  for name in ['data','logs']:
   p=owned/'s3'/name;assert not p.is_symlink();assert p.resolve().is_relative_to(owned.resolve());meta=p.stat();assert (meta.st_uid,meta.st_gid,stat.S_IMODE(meta.st_mode))==(10001,10001,0o750)
 actual['state']='running';records.append(actual)
processes=[]
for p in Path('/proc').iterdir():
 if not p.name.isdigit():continue
 try:args=(p/'cmdline').read_bytes().split(b'\0')
 except OSError:continue
 if b'ama.storage.Task05WorkerProcess' in args or b'ama.worker.TranscriptWorkerMain' in args or (any(str(owned).encode() in a for a in args) and any(b'ama-file-service-' in a for a in args)):processes.append({'pid':p.name,'arguments':[a.decode(errors='replace') for a in args if a]})
assert not processes, 'Owned process remains'
certificates={}
for name in ['ca','server','writer','reader','outsider']:
 p=owned/'pki'/(name+'.crt');certificates[name]=subprocess.check_output(['openssl','x509','-in',str(p),'-noout','-sha256','-fingerprint','-subject','-dates'],text=True)
result={'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'containers':records,'actualOwnedProcessIds':processes,'certificatePublicDetails':certificates,'historyReceipt':'final-projection/history.json','rootInstallReceipt':'root-install-invocation.txt'}
(e/'final-resource-guard.json').write_text(json.dumps(result,indent=2)+'\n')
print('exact containers and zero owned child processes verified')
