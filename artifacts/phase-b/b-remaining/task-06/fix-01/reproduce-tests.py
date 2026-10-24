from pathlib import Path
import subprocess,sys,os,json,hashlib,shutil,time
root=Path.cwd(); name=sys.argv[1]; selector=sys.argv[2]; kind=sys.argv[3] if len(sys.argv)>3 else 'unit'
out=root/'artifacts/phase-b/b-remaining/task-06/fix-01'/name
out.mkdir(parents=True,exist_ok=False)
env=os.environ.copy();env['JAVA_HOME']='/home/tanren/ama/.worktrees/phase-a/.tools/zulu21.52.203-ca-jdk21.0.12.1-linux_x64';env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH'];env['JAVA_TOOL_OPTIONS']='-Xmx512m -XX:ActiveProcessorCount=2'
if kind=='it':
 env['MAVEN_CONFIG']='-Dtest=__Task06OnlyIntegration -Dsurefire.failIfNoSpecifiedTests=false'
 import shlex
 for config in [Path('.tools/phase-b-remaining/env.sh'),Path('.tools/task05-5237a2db-e8d5-4cbe-b357-b35df5cfcba5/client.env')]:
  for line in config.read_text().splitlines():
   if line.startswith('export '):line=line[7:]
   if '=' not in line or line.startswith('#'):continue
   key,value=line.split('=',1);words=shlex.split(value);env[key]=words[0] if words else ''
 env['AMA_TASK05_ROOT']=str(root/'.tools/task05-5237a2db-e8d5-4cbe-b357-b35df5cfcba5')
 assert env['AMA_TEST_DATABASE_URL']=='jdbc:mysql://127.0.0.1:46653/ama_b01_fixture'
 assert env['AMA_S3_ENDPOINT']=='http://127.0.0.1:45501'
cmd=['python3','tools/checks/run.py',kind,selector]
seal={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in Path('backend').rglob('*.java') if '/target/' not in str(p)}
(out/'source.json').write_text(json.dumps(seal,indent=2)+'\n')
start=time.time()
with (out/'run.log').open('w') as f: r=subprocess.run(cmd,env=env,stdout=f,stderr=subprocess.STDOUT)
reports=[]
for p in Path('backend').glob('*/target/'+('failsafe-reports' if kind=='it' else 'surefire-reports')+'/TEST-*.xml'):
 if p.stat().st_mtime>=start:
  dest=out/(p.parts[1]+'-'+p.name);shutil.copy2(p,dest);reports.append(str(dest))
(out/'receipt.json').write_text(json.dumps({'command':cmd,'exit':r.returncode,'reports':reports,'elapsedSeconds':time.time()-start,'mavenConfig':env.get('MAVEN_CONFIG')},indent=2)+'\n')
print((out/'run.log').read_text()[-5000:]);sys.exit(r.returncode)
