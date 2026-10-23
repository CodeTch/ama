from pathlib import Path
import os,subprocess,sys,shutil,json,hashlib,datetime
r=Path('/home/tanren/ama/.worktrees/phase-b-completion')
tag,group,selector=sys.argv[1:]
e=r/'artifacts/phase-b/b-remaining/task-05-fix-01'/tag
e.mkdir(exist_ok=False)
env=os.environ.copy();env['JAVA_HOME']='/home/tanren/ama/.worktrees/phase-a/.tools/zulu21.52.203-ca-jdk21.0.12.1-linux_x64';env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH'];env['MAVEN_OPTS']='-Xmx512m -XX:ActiveProcessorCount=2';env['JAVA_TOOL_OPTIONS']='-Xmx512m -XX:ActiveProcessorCount=2'
if group=='it': env['MAVEN_OPTS']+=' -Dtest=Task05FocusedIntegrationOnly -Dsurefire.failIfNoSpecifiedTests=false'
cmd=['python3','tools/checks/run.py',group,selector]
source={str(p.relative_to(r)):hashlib.sha256(p.read_bytes()).hexdigest() for p in r.glob('backend/**/*') if p.is_file() and ('src' in p.parts or p.name=='pom.xml') and 'target' not in p.parts}
(e/'source.json').write_text(json.dumps(source,indent=2)+'\n')
with (e/'output.log').open('w') as log: result=subprocess.run(cmd,cwd=r,env=env,stdout=log,stderr=subprocess.STDOUT)
reports=list(r.glob('backend/*/target/'+('failsafe-reports' if group=='it' else 'surefire-reports')+'/TEST-*.xml'))
for p in reports:
 if 'zero matching tests' not in (e/'output.log').read_text(): shutil.copyfile(p,e/(p.parents[2].name+'-'+p.name))
(e/'command.json').write_text(json.dumps({'command':cmd,'exit':result.returncode,'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'source_seal':'source.json','limits':'512MiB / 2 active processors'},indent=2)+'\n')
print('EXIT',result.returncode);print('\n'.join((e/'output.log').read_text().splitlines()[-18:]));sys.exit(result.returncode)
