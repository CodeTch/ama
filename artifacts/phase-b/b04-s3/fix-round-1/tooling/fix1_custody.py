from pathlib import Path
import hashlib,json,subprocess
root=Path.cwd();art=root/'artifacts/phase-b/b04-s3';out=art/'fix-round-1'
def sha(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for block in iter(lambda:f.read(1024*1024),b''):h.update(block)
    return h.hexdigest()
old=json.loads((art/'review-candidate-evidence-manifest.json').read_text())
bad=[n for n,v in old.items() if sha(art/n)!=v['sha256'] or (art/n).stat().st_size!=v['size']]
classes=json.loads((art/'compiled-cohort-identities.json').read_text());count=0
for c in classes['cohorts'].values():
    for n,h in c['classes'].items():
        count+=1
        if sha(Path(c['directory'])/n)!=h:bad.append(str(Path(c['directory'])/n))
for n,h in classes['finalCanonical'].items():
    candidates=[root/'.tools/v3-canonical/backend'/m/'target'/k/'ama/storage/s3'/n for m in ['adapters','integration-tests']for k in ['classes','test-classes']]
    paths=[p for p in candidates if p.exists()]
    assert len(paths)==1
    count+=1
    if sha(paths[0])!=h:bad.append(str(paths[0]))
record={'originalSealSHA256':sha(art/'review-candidate-evidence-manifest.json'),'originalSealedFilesChecked':len(old),'originalClassesChecked':count,'mismatches':bad}
(out/'original-seal-unchanged.json').write_text(json.dumps(record,indent=2)+'\n');assert not bad;print(record)
cp=json.loads((art/'final-canonical-classpath.json').read_text())['entries']
jars={p:sha(Path(p)) for p in cp if '/software/amazon/awssdk/' in p and any('/'+x+'/' in p for x in ['sdk-core','retries','retries-spi'])}
(out/'sdk-retry-artifact-identities.json').write_text(json.dumps(jars,indent=2)+'\n')
fixture=json.loads((root/'.tools/s3-java-v3restart/fixture.json').read_text());state=json.loads(subprocess.check_output(['docker','inspect',fixture['id']]))[0]
safe={'id':state['Id'],'owner':state['Config']['Labels']['ama.owner'],'image':state['Config']['Image'],'limits':{k:state['HostConfig'][k]for k in ['NanoCpus','Memory','MemorySwap','PidsLimit','PortBindings']},'mounts':[{k:m[k]for k in ['Source','Destination']}for m in state['Mounts']],'operation':'read-only inspect; no restart/stop'}
(out/'owned-fixture-limits.json').write_text(json.dumps(safe,indent=2)+'\n')
