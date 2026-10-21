from pathlib import Path
import json,hashlib,shutil,xml.etree.ElementTree as ET
root=Path.cwd();art=root/'artifacts/phase-b/b04-s3/fix-round-1';work=root/'.tools/fix-round-1/canonical'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
reports=list((art/'canonical-related-failsafe-reports').glob('TEST-*.xml'))
assert sum(int(ET.parse(p).getroot().attrib['tests']) for p in reports)==24
assert all(int(ET.parse(p).getroot().attrib.get(k,0))==0 for p in reports for k in ['failures','errors','skipped'])
classes={str(p.relative_to(work)):sha(p)for p in work.glob('backend/*/target/**/ama/storage/s3/*.class')}
(art/'canonical-related-compiled-manifest.json').write_text(json.dumps(classes,indent=2)+'\n')
old=json.loads((art/'canonical-source-manifest.json').read_text()); changed=[]
for n,h in old['files'].items():
    if sha(root/n)!=h:changed.append(n)
assert changed==['backend/integration-tests/src/test/java/ama/storage/s3/S3MissingLengthIT.java'],changed
for n in changed:shutil.copy2(root/n,work/n);old['files'][n]=sha(root/n)
(art/'canonical-final-source-manifest.json').write_text(json.dumps(old,indent=2)+'\n')
print('preserved related source/class manifest; copied only positive-control test extension')
