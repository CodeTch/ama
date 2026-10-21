from pathlib import Path
import subprocess,os,sys,json,hashlib
root=Path.cwd();name=sys.argv[1];out=root/'.tools/fix-round-1'/name;out.mkdir(parents=True,exist_ok=False)
art=root/'artifacts/phase-b/b04-s3/fix-round-1';art.mkdir(parents=True,exist_ok=True)
entries=json.loads((root/'artifacts/phase-b/b04-s3/final-canonical-classpath.json').read_text())['entries']
cp=str(out)+':'+':'.join(entries)+':'+str(root/'.tools/junit-platform-console-standalone-6.0.3.jar')
sources=list((root/'backend/adapters/src/main/java/ama/storage/s3').glob('*.java'))+list((root/'backend/integration-tests/src/test/java/ama/storage/s3').glob('*.java'))
sources += [root/'backend/integration-tests/src/test/java/ama/storage'/n for n in ['ContractFixture.java','FileStorageContractIT.java','ContractFixtureTest.java']]
(art/(name+'-sources.json')).write_text(json.dumps({str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources},indent=2)+'\n')
java=Path(os.environ['JAVA_HOME'])/'bin'
subprocess.run([str(java/'javac'),'-cp',cp,'-d',str(out),*[str(p) for p in sources]],check=True)
env=dict(os.environ,AMA_S3_EVIDENCE_GROUP='fix-round-1-'+name)
cmd=[str(java/'java'),'-Xmx384m','-cp',cp,'org.junit.platform.console.ConsoleLauncher','execute','--fail-if-no-tests','--disable-ansi-colors','--reports-dir',str(art/name)]
for selector in sys.argv[2:]:cmd+=['--select-method' if '#' in selector else '--select-class',selector]
(art/(name+'-command.json')).write_text(json.dumps({'command':cmd,'classpathSHA256':hashlib.sha256(cp.encode()).hexdigest()},indent=2)+'\n')
with (art/(name+'.log')).open('w') as log:r=subprocess.run(cmd,cwd=root/'backend/integration-tests',env=env,stdout=log,stderr=subprocess.STDOUT)
print(name,r.returncode);raise SystemExit(r.returncode)
