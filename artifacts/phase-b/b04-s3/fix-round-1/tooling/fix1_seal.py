from pathlib import Path
import subprocess,json,hashlib,shutil,tarfile,xml.etree.ElementTree as ET
root=Path.cwd();art=root/'artifacts/phase-b/b04-s3/fix-round-1';work=root/'.tools/fix-round-1/canonical'
def sha(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
    return h.hexdigest()
for p in (root/'artifacts/phase-b/b04-s3/wire').glob('fix-round-1-*/*.json'):
    q=art/'wire'/p.parent.name/p.name;q.parent.mkdir(parents=True,exist_ok=True)
    if not q.exists():shutil.copy2(p,q)
tooling=art/'tooling';tooling.mkdir(exist_ok=True)
for p in (root/'.tools').glob('fix1_*.py'):shutil.copy2(p,tooling/p.name)
groups={}
for p in art.rglob('TEST-*.xml'):
    t=ET.parse(p).getroot();group=str(p.parent.relative_to(art));g=groups.setdefault(group,{k:0 for k in ['tests','failures','errors','skipped']})
    for k in g:g[k]+=int(t.attrib.get(k,0))
(art/'test-counts.json').write_text(json.dumps(groups,indent=2)+'\n')
metrics={}
for p in (art/'wire').rglob('missing-length*.json'):
    values=json.loads(p.read_text());faults=[e for e in values if e['fault']]
    metrics[str(p.relative_to(art))]={'sha256':sha(p),'faultCount':len(faults),'faultBodyBytes':[e['upstreamBytes'] for e in faults],'faultDeliveredBytes':[e['downstreamBytes'] for e in faults],'finalGets':sum(e['method']=='GET' and '/data/v3/'in e['path']for e in values)}
(art/'missing-length-metrics.json').write_text(json.dumps(metrics,indent=2)+'\n')
xml=art/'canonical-shared-failsafe-reports/TEST-ama.storage.FileStorageContractIT.xml'
props=ET.parse(xml).getroot().find('properties');cp=next(p.attrib['value'] for p in props if p.attrib['name']=='java.class.path')
assert not any('/'+x+'/' in cp for x in ['apache-client','apache5-client','netty-nio-client'])
(art/'final-classpath.json').write_text(json.dumps({'sha256':hashlib.sha256(cp.encode()).hexdigest(),'entries':cp.split(':')},indent=2)+'\n')
classes={str(p.relative_to(work)):sha(p)for p in work.glob('backend/*/target/**/ama/storage/s3/*.class')}
(art/'final-compiled-manifest.json').write_text(json.dumps(classes,indent=2)+'\n')
names=set(subprocess.check_output(['git','diff','--name-only'],text=True).splitlines())
names.update(subprocess.check_output(['git','ls-files','--others','--exclude-standard'],text=True).splitlines())
names=sorted(names);assert len(names)==20,names
canonical=json.loads((art/'canonical-final-source-manifest.json').read_text())['files']
files={n:sha(root/n) for n in names};assert all(canonical[n]==h for n,h in files.items())
secrets=[]
for fixture in ['s3-java','s3-java-shallow','s3-java-v3restart']:
    for line in (root/'.tools'/fixture/'server.env').read_text().splitlines():
        if '=' in line:
            k,v=line.split('=',1)
            if any(s in k for s in ['ACCESS_KEY','SECRET_KEY']) and len(v)>=8:secrets.append(v.encode())
assert len(secrets)==6
scanned=[root/n for n in names]+[p for p in art.rglob('*')if p.is_file() and p.suffix in ['.log','.json','.txt','.xml','.py']]
hits=[]
for p in scanned:
    content=p.read_bytes()
    if any(s in content for s in secrets):hits.append(str(p))
assert not hits,hits
(art/'redaction-scan.json').write_text(json.dumps({'filesScanned':len(scanned),'actualCredentialValuesChecked':6,'hits':hits},indent=2)+'\n')
archive=art/'source.tar.gz';assert not archive.exists()
with tarfile.open(archive,'w:gz')as tar:
    for n in names:tar.add(root/n,arcname=n)
(art/'source-manifest.json').write_text(json.dumps({'fixBase':'9a8b79dfb4eb1ade5a18d7757699a40b3ed9f0fb','files':files,'archiveSHA256':sha(archive)},indent=2)+'\n')
(root/'.tools/fix1-owned-paths.txt').write_text('\n'.join(names)+'\n')
evidence={str(p.relative_to(art)):{'sha256':sha(p),'size':p.stat().st_size}for p in sorted(art.rglob('*'))if p.is_file() and p.name!='evidence-manifest.json'}
(art/'evidence-manifest.json').write_text(json.dumps(evidence,indent=2)+'\n')
print('sourcePaths',len(names),'sourceArchiveSHA256',sha(archive),'evidenceFiles',len(evidence),'evidenceManifestSHA256',sha(art/'evidence-manifest.json'),'credentialHits',len(hits))
print(json.dumps(groups))
for n,v in metrics.items():
    if 'framing-native-green' in n:print(n,v)
