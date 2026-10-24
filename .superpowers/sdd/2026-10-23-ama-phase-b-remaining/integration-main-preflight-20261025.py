from pathlib import Path
import subprocess,json,hashlib,datetime,shlex,re,base64
P=Path('/home/tanren/ama/.worktrees/phase-b-completion');W=P/'.superpowers/sdd/2026-10-23-ama-phase-b-remaining';A=W/'integration-main-20261025';A.mkdir(exist_ok=True)
def z(*args):return [x for x in subprocess.check_output(args,cwd=P).decode().split('\0') if x]
modified=z('git','diff','--name-only','-z');new=z('git','ls-files','--others','--exclude-standard','-z');extra=z('git','ls-files','--others','--ignored','--exclude-standard','-z','--',str(W.relative_to(P)),'artifacts/phase-b/b-remaining/task-11')
selected=set(modified+new);excluded=[]
for rel in extra:
 p=P/rel
 if not p.is_file():continue
 size=p.stat().st_size
 sensitive=any(x in rel.split('/') for x in ('.tools','secrets','private','node_modules','__pycache__')) or p.suffix.lower() in ('.pem','.key','.pk8','.p12','.jks','.sqlite','.sqlite3','.db','.class','.jar') or p.name.startswith('.env')
 if sensitive or (rel.startswith('.superpowers/') and size>=5*1024*1024):excluded.append({'path':rel,'bytes':size,'reason':'private/generated payload' if sensitive else 'large local-only review/evidence package','sha256':None if sensitive else hashlib.sha256(p.read_bytes()).hexdigest()})
 else:selected.add(rel)
# All secret comparisons remain in memory; no values or secret hashes leave this program.
secrets=[]
d=json.loads((W/'task-11-knowledge-credential-root-ack-20261025.json').read_text())
for x in d.get('privateScanInputs',[]):
 if any(t in Path(x).name for t in ('private','bearer')):
  p=Path(x)
  if p.exists():secrets.append(p.read_bytes().strip())
for env in [P/'.tools/task11-9714255c9e2c4861a2795cfaaec8e97f/env.sh',P/'.tools/task11fix1-49609b94161e4c90b6438ba683bfa3b1/env.sh',P/'.tools/task10-1e48b41fc5c84a98af3a14b51b17884f/mysql-recovery-20261024/env.sh',P/'.tools/phase-b-remaining/env.sh']:
 if not env.exists():continue
 for line in env.read_text().splitlines():
  try:a=shlex.split(line)
  except ValueError:continue
  if a and a[0]=='export':a=a[1:]
  if len(a)!=1 or '=' not in a[0]:continue
  k,v=a[0].split('=',1)
  if re.search(r'(PASSWORD|SECRET|TOKEN|BEARER)',k) and len(v)>=16:secrets.append(v.encode())
variants=set()
for b in secrets:
 for v in [b,b.hex().encode(),b.hex().upper().encode(),base64.b64encode(b),base64.urlsafe_b64encode(b).rstrip(b'=')]:
  if len(v)>=24:variants.add(v)
 if b.startswith(b'-----BEGIN'):
  flat=b''.join(x for x in b.splitlines() if not x.startswith(b'-----'));variants.add(flat)
patterns=[re.compile(rb'gh[pousr]_[A-Za-z0-9]{30,}'),re.compile(rb'github_pat_[A-Za-z0-9_]{40,}')]
hits=[]
for rel in sorted(selected):
 p=P/rel
 if not p.is_file():continue
 b=p.read_bytes()
 if any(v in b for v in variants):hits.append({'path':rel,'category':'actual private value encoding'})
 if any(r.search(b) for r in patterns):hits.append({'path':rel,'category':'credential token pattern'})
# Inspect all newly introduced historical blobs as well as the current delta.
rows=subprocess.check_output(['git','rev-list','--objects','origin/main..HEAD'],cwd=P).splitlines();inp=b'\n'.join(rows)+b'\n';cp=subprocess.run(['git','cat-file','--batch-check=%(objecttype) %(objectname) %(objectsize) %(rest)'],cwd=P,input=inp,capture_output=True,check=True);blobs=[];large=[]
for line in cp.stdout.decode().splitlines():
 a=line.split(' ',3)
 if len(a)>=3 and a[0]=='blob':
  blobs.append((a[1],int(a[2]),a[3] if len(a)>3 else ''))
  if int(a[2])>=100*1024*1024:large.append({'oid':a[1],'bytes':int(a[2]),'path':a[3] if len(a)>3 else ''})
proc=subprocess.Popen(['git','cat-file','--batch'],cwd=P,stdin=subprocess.PIPE,stdout=subprocess.PIPE)
for oid,n,path in blobs:
 proc.stdin.write((oid+'\n').encode());proc.stdin.flush();header=proc.stdout.readline();b=proc.stdout.read(n);assert proc.stdout.read(1)==b'\n'
 if any(v in b for v in variants):hits.append({'path':path,'oid':oid,'category':'actual private value in pending history'})
 if any(r.search(b) for r in patterns):hits.append({'path':path,'oid':oid,'category':'credential token pattern in pending history'})
proc.stdin.close();assert proc.wait()==0
report={'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'selectedFiles':len(selected),'selectedBytes':sum((P/x).stat().st_size for x in selected if (P/x).is_file()),'historyBlobsScanned':len(blobs),'actualPrivateInputs':len(secrets),'actualPrivateEncodings':len(variants),'privateValuesOrHashesPrinted':False,'sensitiveHits':hits,'oversizedHistoryBlobs':large,'excludedLocalOnlyFiles':excluded,'qualification':'Actual current credential encodings and token patterns; not a universal absence-of-secrets proof. Local private directories never selected; raw excluded packages retained locally.'}
(A/'preflight.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');(A/'candidate-paths.txt').write_text('\n'.join(sorted(selected))+'\n')
print(json.dumps({k:v for k,v in report.items() if k not in ['excludedLocalOnlyFiles']},ensure_ascii=False));print('EXCLUDED_LOCAL',len(excluded))
assert not hits and not large