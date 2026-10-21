from pathlib import Path
import subprocess,shutil,hashlib,json,os,sys
root=Path.cwd();work=root/'.tools/fix-round-1/canonical';art=root/'artifacts/phase-b/b04-s3/fix-round-1'
if sys.argv[1]=='copy':
    work.mkdir(parents=True,exist_ok=False)
    names=set(subprocess.check_output(['git','ls-files','-z'],text=True).split('\0'))
    names.update(str(p.relative_to(root)) for p in (root/'backend/integration-tests/src/test/java/ama/storage/s3').glob('*.java'))
    names.add('tools/dev/tests/test_s3_fixture.py')
    files={}
    for n in sorted(names):
        if not n or n.startswith('artifacts/'):continue
        p=root/n
        if not p.is_file():continue
        q=work/n;q.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,q)
        files[n]=hashlib.sha256(p.read_bytes()).hexdigest()
    (art/'canonical-source-manifest.json').write_text(json.dumps({'base':subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),'files':files},indent=2)+'\n')
    print('new canonical source copy',len(files));raise SystemExit(0)
name=sys.argv[1];selector=sys.argv[2];log=art/(name+'.log');assert not log.exists()
env=dict(os.environ,MAVEN_CONFIG='-DargLine=-Xmx384m',AMA_STORAGE_CONTRACT='s3',AMA_S3_EVIDENCE_GROUP='fix-round-1-'+name)
with log.open('w') as out:r=subprocess.run(['python3','tools/checks/run.py','it',selector],cwd=work,env=env,stdout=out,stderr=subprocess.STDOUT)
for kind in ['failsafe-reports','surefire-reports']:
    dest=art/(name+'-'+kind);dest.mkdir(exist_ok=False)
    for p in work.glob('backend/*/target/'+kind+'/TEST-*.xml'):shutil.copy2(p,dest/p.name)
for p in (work/'artifacts/phase-b/b04-s3/wire').rglob('*.json'):
    q=art/'wire'/p.relative_to(work/'artifacts/phase-b/b04-s3/wire');q.parent.mkdir(parents=True,exist_ok=True)
    if not q.exists():shutil.copy2(p,q)
print(name,'exit',r.returncode);raise SystemExit(r.returncode)
