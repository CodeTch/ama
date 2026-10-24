from pathlib import Path
import os,subprocess,shlex,json,hashlib,time,sys,xml.etree.ElementTree as ET,shutil
ROOT=Path.cwd();OUT=ROOT/'artifacts/phase-b/b-remaining/task-08'
JAVA=Path('/home/tanren/ama/.worktrees/phase-a/.tools/zulu21.52.203-ca-jdk21.0.12.1-linux_x64')
def env(fresh=True):
 e=os.environ.copy()
 for line in (ROOT/('.tools/task08-848e93aace8b4f0fa34fc548267f3dc5/env.sh' if fresh else '.tools/phase-b-remaining/env.sh')).read_text().splitlines():
  if line.startswith('export '):line=line[7:]
  if '=' in line and not line.startswith('#'):
   k,v=line.split('=',1);a=shlex.split(v);e[k]=a[0] if a else ''
 e['JAVA_HOME']=str(JAVA);e['PATH']=str(JAVA/'bin')+':'+e['PATH'];e['JAVA_TOOL_OPTIONS']='-Xmx512m -XX:ActiveProcessorCount=2'
 return e
def classpath():
 p=next((ROOT/'backend/integration-tests/target/failsafe-reports').glob('TEST-*.xml'))
 cp=next(x.attrib['value'] for x in ET.parse(p).getroot().findall('properties/property') if x.attrib['name']=='java.class.path')
 return str(OUT/'classes')+':'+str(ROOT/'backend/adapters/src/main/resources')+':'+cp
if __name__=='__main__':
 mode=sys.argv[1]
 if mode=='compile':
  (OUT/'classes').mkdir(exist_ok=True)
  sys.exit(subprocess.run([str(JAVA/'bin/javac'),'-J-Xmx64m','-J-XX:ActiveProcessorCount=2','-cp',classpath(),'-d',str(OUT/'classes'),str(OUT/'Task08MigrationProbe.java')]).returncode)
 if mode in ['bootstrap','before','apply','rerun']:
  targets=sys.argv[2:] or ['behavior','fairheld','fairunregistered','overflow']
  for target in targets:
   e=env(target!='shared')
   if target!='shared':e['AMA_TEST_DATABASE_URL']=e['AMA_TASK08_'+target.upper()+'_DATABASE_URL']
   dest=OUT/'migration'/target;dest.mkdir(parents=True,exist_ok=True);log=dest/(mode+'.log');assert not log.exists()
   cmd=[str(JAVA/'bin/java'),'-Xmx64m','-XX:ActiveProcessorCount=2','-cp',classpath(),'Task08MigrationProbe',mode,str(dest)]
   with log.open('w') as f:r=subprocess.run(cmd,env=e,stdout=f,stderr=subprocess.STDOUT)
   print(target,mode,'exit',r.returncode,flush=True)
   if r.returncode:print(log.read_text()[-5000:]);sys.exit(r.returncode)
