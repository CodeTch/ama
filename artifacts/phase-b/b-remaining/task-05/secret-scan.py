from pathlib import Path
import os,json,re,hashlib,datetime
r=Path('/home/tanren/ama/.worktrees/phase-b-completion');e=r/'artifacts/phase-b/b-remaining/task-05';p=r/'.tools/task05-5237a2db-e8d5-4cbe-b357-b35df5cfcba5'
needles=[os.environ.get(k,'').encode() for k in ['AMA_TEST_DATABASE_PASSWORD','AMA_S3_ACCESS','AMA_S3_SECRET']]
needles.append((p/'pki/keystore-password').read_bytes().strip());needles=[v for v in needles if len(v)>=8]
assert len(needles)==4,'Missing private scan inputs'
paths=list(e.rglob('*'))+[r/f for f in json.loads((e/'explicit-source-paths.json').read_text())]
findings=[];count=0
for path in paths:
 if not path.is_file() or path.name in ['secret-scan.json','evidence-manifest.json']:continue
 data=path.read_bytes();count+=1
 if any(value in data for value in needles):findings.append({'path':str(path.relative_to(r)),'kind':'exact private value'})
 if re.search(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',data):findings.append({'path':str(path.relative_to(r)),'kind':'private key material'})
result={'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'filesScanned':count,'exactPrivateNeedleCount':len(needles),'findings':findings,'valuesPrinted':False,'scope':'Task5 public evidence and explicit touched backend paths; no private source/root is staged'}
(e/'secret-scan.json').write_text(json.dumps(result,indent=2)+'\n')
print('scanned',count,'findings',len(findings));raise SystemExit(bool(findings))
