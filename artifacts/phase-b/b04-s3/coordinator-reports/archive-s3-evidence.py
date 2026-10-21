from pathlib import Path
import json, hashlib, shutil, gzip
owner=Path('/home/tanren/ama/.worktrees/phase-b-storage-s3/artifacts/phase-b/b04-s3')
dest=Path('/home/tanren/ama/.worktrees/phase-a/artifacts/phase-b/b04-s3')
def sha(p):
 h=hashlib.sha256()
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1024*1024),b''): h.update(b)
 return h.hexdigest()
def copy(src,out,expected=None):
 if not src.is_file() or src.is_symlink(): raise RuntimeError('unsafe input '+str(src))
 digest=sha(src)
 if expected and (digest!=expected['sha256'] or src.stat().st_size!=expected['size']): raise RuntimeError('source mismatch '+str(src))
 out.parent.mkdir(parents=True,exist_ok=True)
 if out.exists():
  if sha(out)!=digest: raise RuntimeError('refuse overwrite '+str(out))
 else: shutil.copy2(src,out)
 if sha(out)!=digest: raise RuntimeError('copy mismatch')
 return {'sha256':digest,'size':out.stat().st_size}
records={}
for sub,seal,expected in [('', 'review-candidate-evidence-manifest.json','59536dde036a46a1d3df3d4866697fa7d4d776b2d292e73cb978cffa66571454'),('fix-round-1','evidence-manifest.json','635040bd8e2626007917bed2d154335411d0e3c6e84fe2f0c6303ae795c4b29a')]:
 base=owner/sub; target=dest/sub
 if sha(base/seal)!=expected: raise RuntimeError('manifest changed')
 manifest=json.loads((base/seal).read_text())
 for name,meta in manifest.items():
  rel=Path(name)
  if rel.is_absolute() or '..' in rel.parts or rel.name.startswith('.') or rel.name.lower().startswith(('env','credentials','password')): raise RuntimeError('unsafe allowlist '+name)
  if meta.get('pendingRun',False) or not meta.get('stableDuringRead',True): raise RuntimeError('unsealed evidence')
  out=target/rel
  record=copy(base/rel,out,meta)
  key=str(Path(sub)/rel)
  record['originalSource']=str(base/rel)
  if record['size']>8*1024*1024:
   zipped=Path(str(out)+'.gz')
   if not zipped.exists():
    with out.open('rb') as f, zipped.open('wb') as z, gzip.GzipFile(filename='',fileobj=z,mode='wb',mtime=0,compresslevel=6) as g: shutil.copyfileobj(f,g)
   h=hashlib.sha256()
   with gzip.open(zipped,'rb') as f:
    for b in iter(lambda:f.read(1024*1024),b''): h.update(b)
   if h.hexdigest()!=record['sha256']: raise RuntimeError('gzip roundtrip mismatch')
   record['gitArchive']=str(zipped.relative_to(dest));record['archiveSha256']=sha(zipped);record['archiveSize']=zipped.stat().st_size
  else: record['gitArchive']=key
  records[key]=record
 records[str(Path(sub)/seal)]=copy(base/seal,target/seal)
for sub,name in [('', 'commit-source-attestation.json'),('fix-round-1','postcommit-attestation.json')]:
 records[str(Path(sub)/name)]=copy(owner/sub/name,dest/sub/name)
result={'description':'Lossless coordinator copy. Original raw bytes retained at these paths; files above8MiB additionally archived as deterministic gzip for Git. Verify decompressed SHA against original seal. No env, credentials, private provider state or classes copied. Pending independent review is not adoption.','files':records}
out=dest/'coordinator-copy-manifest.json'
text=json.dumps(result,indent=2,sort_keys=True)+'\n'
if out.exists() and out.read_text()!=text: raise RuntimeError('refuse overwrite coordinator manifest')
out.write_text(text)
print(json.dumps({'copied':len(records),'rawBytes':sum(v['size'] for v in records.values()),'compressedFiles':sum('archiveSha256' in v for v in records.values()),'manifestSha256':sha(out)}))
