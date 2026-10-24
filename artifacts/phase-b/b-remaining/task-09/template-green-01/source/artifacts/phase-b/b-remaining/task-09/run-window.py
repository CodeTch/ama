from pathlib import Path
import os,sys,json,hashlib,shlex,subprocess,shutil,datetime,xml.etree.ElementTree as ET
P=Path(__file__).resolve().parents[4]; A=Path(__file__).resolve().parent
W=P/'.superpowers/sdd/2026-10-23-ama-phase-b-remaining'
name,group,selector=sys.argv[1:]; out=A/name;out.mkdir()
h=lambda b:hashlib.sha256(b).hexdigest()
ack=json.loads((W/'task-09-root-fixture-application-ack-20261024.json').read_text())
assert ack['schema']=='ama_t09_2d0e28ea7755_behavior'
assert h((W/'task-09-root-fixture-application-ack-20261024.json').read_bytes())=='6f926c62b9710dab9c889eee104543b3250bfd5e33fc23d67fb655677e4c7e5d'
for x in ack['exactSqlPrefix']:assert h((P/x['path']).read_bytes())==x['sha256']
env=os.environ.copy();private=P/ack['privateEnv'];assert private.stat().st_mode&0o777==0o600
secret_values=[]
for line in private.read_text().splitlines():
 line=line.strip()
 if not line or line.startswith('#'):continue
 if line.startswith('export '):line=line[7:]
 key,value=line.split('=',1);parsed=shlex.split(value);assert len(parsed)==1;env[key]=parsed[0]
 if any(s in key for s in ['PASSWORD','SECRET','TOKEN']):secret_values.append(parsed[0].encode())
assert env['AMA_TEST_DATABASE_URL']=='jdbc:mysql://127.0.0.1:44727/'+ack['schema']
jdk='/home/tanren/ama/.worktrees/phase-a/.tools/zulu21.52.203-ca-jdk21.0.12.1-linux_x64'
env['JAVA_HOME']=jdk;env['PATH']=jdk+'/bin:'+env['PATH'];env['JAVA_TOOL_OPTIONS']='-Xmx512m -XX:ActiveProcessorCount=2';env['MAVEN_OPTS']='-Xmx512m -XX:ActiveProcessorCount=2'
if group=='it':env['MAVEN_ARGS']='-Dtest=Task09NoImplicitUnitSuite -Dsurefire.failIfNoSpecifiedTests=false'
source=[]
for f in sorted((P/'backend').rglob('*')):
 if f.is_file() and 'target' not in f.parts and f.suffix in ['.java','.sql','.xml','.yml','.properties']:
  b=f.read_bytes();rel=f.relative_to(P);dest=out/'source'/rel;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(b);source.append({'path':str(rel),'bytes':len(b),'sha256':h(b)})
for f in [P/'pom.xml',P/'tools/checks/run.py',Path(__file__)]:
 b=f.read_bytes();rel=f.relative_to(P);dest=out/'source'/rel;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(b);source.append({'path':str(rel),'bytes':len(b),'sha256':h(b)})
(out/'source-manifest.json').write_text(json.dumps(source,indent=2)+'\n')
command=['python3','tools/checks/run.py',group,selector]
(out/'command.json').write_text(json.dumps({'command':command,'cwd':str(P),'heapMiB':512,'processors':2,'mavenArgs':env.get('MAVEN_ARGS',''),'sourceSha256':h((out/'source-manifest.json').read_bytes()),'startedUtc':datetime.datetime.now(datetime.timezone.utc).isoformat()},indent=2)+'\n')
with (out/'output.log').open('wb') as log:r=subprocess.run(command,cwd=P,env=env,stdout=log,stderr=subprocess.STDOUT)
counts={'tests':0,'failures':0,'errors':0,'skipped':0};cases=[]
for f in sorted(P.glob('backend/*/target/'+('failsafe-reports' if group=='it' else 'surefire-reports')+'/TEST-*.xml')):
 b=f.read_bytes();dest=out/(f.parts[-4]+'-'+f.name);dest.write_bytes(b);root=ET.fromstring(b)
 for key in counts:counts[key]+=int(root.get(key,'0'))
 for c in root.findall('testcase'):cases.append({'class':c.get('classname'),'name':c.get('name'),'failure':c.find('failure') is not None,'error':c.find('error') is not None,'skipped':c.find('skipped') is not None})
for f in out.rglob('*'):
 if f.is_file():
  b=f.read_bytes();assert not any(len(v)>3 and v in b for v in secret_values),'private value found; do not print'
result={'exit':r.returncode,'counts':counts,'cases':cases};(out/'result.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({'window':name,'exit':r.returncode,'counts':counts}))
print((out/'output.log').read_text()[-2500:])
sys.exit(r.returncode)
