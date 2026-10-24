from pathlib import Path
import shlex,re,json,subprocess,hashlib
P=Path(__file__).resolve().parents[4];A=Path(__file__).resolve().parent
# Private values exist only in this process. No values or individual value digests are emitted.
def envfile(path):
 values={}
 for raw in path.read_text().splitlines():
  line=raw.strip()
  if line.startswith('export '):line=line[7:]
  if not line or line.startswith('#') or '=' not in line:continue
  key,value=line.split('=',1);values[key.strip()]=shlex.split(value)[0]
 return values
old=envfile(P/'.tools/phase-b-remaining/env.sh')
s3=envfile(P/'.tools/task05-5237a2db-e8d5-4cbe-b357-b35df5cfcba5/client.env')
values={old['AMA_TEST_DATABASE_PASSWORD'].encode(),s3['AMA_S3_ACCESS'].encode(),s3['AMA_S3_SECRET'].encode(),(P/'.tools/task05-5237a2db-e8d5-4cbe-b357-b35df5cfcba5/pki/keystore-password').read_bytes().strip()}
for name in ['root.cnf','test.cnf']:values.add(envfile(P/'.tools/task08-848e93aace8b4f0fa34fc548267f3dc5/secrets'/name)['password'].encode())
assert len(values)==6,'Known-value input cardinality changed'
provider09=list((P/'.tools/task09-2d0e28ea775540f8b3525f9344edf5f0').rglob('provider.secret'))
provider10=list((P/'.tools/task10-1e48b41fc5c84a98af3a14b51b17884f').rglob('provider.secret'))
provider=provider09+provider10
for f in provider:
 assert f.is_file() and not f.is_symlink() and f.stat().st_mode&511==384
 values.add(f.read_bytes().strip())
assert all(len(v)>3 for v in values)
pattern=re.compile(b'-----BEGIN '+b'(?:[A-Z0-9 ]*PRIVATE KEY|OPENSSH PRIVATE KEY)-----')
raw=[p for p in A.rglob('*') if p.is_file() and p.name not in ['private-scan.json']]
staged=subprocess.check_output(['git','diff','--cached','--name-only','-z'],cwd=P).decode().split('\0');staged=[s for s in staged if s]
assert staged and all(not s.startswith('.tools/') for s in staged)
hits=0;pem=0;total=0
for f in raw:
 b=f.read_bytes();total+=len(b);hits+=sum(v in b for v in values);pem+=len(pattern.findall(b))
selected=set(staged);objects=set()
for entry in subprocess.check_output(['git','ls-files','--stage','-z'],cwd=P).split(b'\0'):
 if not entry:continue
 meta,name=entry.split(b'\t',1)
 if name.decode() in selected:
  mode,oid,stage=meta.split();assert stage==b'0';objects.add(oid)
with subprocess.Popen(['git','cat-file','--batch'],cwd=P,stdin=subprocess.PIPE,stdout=subprocess.PIPE) as git:
 for oid in sorted(objects):
  git.stdin.write(oid+b'\n');git.stdin.flush();header=git.stdout.readline().split();assert len(header)==3 and header[1]==b'blob';size=int(header[2]);b=git.stdout.read(size);assert git.stdout.read(1)==b'\n';total+=len(b);hits+=sum(v in b for v in values);pem+=len(pattern.findall(b))
 git.stdin.close();assert git.wait()==0
result={'scope':{'rawRoot':str(A.relative_to(P)),'rawFiles':len(raw),'exactStagedPaths':len(staged),'uniqueStagedBlobs':len(objects),'bytesScannedIncludingOverlap':total},'knownOriginalValueCount':6,'actualProviderSecretFiles':len(provider),'task9ProviderSecretFiles':len(provider09),'task10ProviderSecretFiles':len(provider10),'fixedTestBearerIsPublicFixtureConstant':True,'uniqueValues':len(values),'exactValueHits':hits,'privatePemHits':pem,'scriptSha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}
result['receiptScannedInMemory']=True
receipt=(json.dumps(result,indent=2)+'\n').encode();assert not any(v in receipt for v in values) and not pattern.search(receipt)
(A/'private-scan.json').write_bytes(receipt);print(json.dumps(result));assert hits==0 and pem==0,'Private scan failed; inspect safely without printing values'
