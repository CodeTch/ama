from pathlib import Path
import subprocess,os,json,hashlib,shutil,xml.etree.ElementTree as ET,time,sys
r=Path('/home/tanren/ama'); head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=r,text=True).strip(); assert head==sys.argv[1]
assert not subprocess.check_output(['git','status','--porcelain'],cwd=r,text=True).strip()
out=r/'artifacts/phase-b/b06-archives/final-units'; out.mkdir(parents=True,exist_ok=False)
(out/'.gitattributes').write_text('* -text\n')
java=Path('/home/tanren/ama/.worktrees/phase-a/.tools/zulu21.52.203-ca-jdk21.0.12.1-linux_x64')
env=os.environ.copy(); env['JAVA_HOME']=str(java); env['PATH']=str(java/'bin')+os.pathsep+env.get('PATH','')
for key in list(env):
 if key.startswith('AMA_TEST_'): del env[key]
cmd=['python3','tools/checks/run.py','unit']
(out/'command.json').write_text(json.dumps({'head':head,'command':cmd,'cwd':str(r),'javaHome':str(java),'privateFixtureEnvironmentLoaded':False},indent=2)+'\n')
paths=subprocess.check_output(['git','diff','--name-only','ad9960595ab5de6eef7760e2c9094d23666074cb',head],cwd=r,text=True).splitlines()
sources=[]
for name in paths:
 if name.endswith('.java'):
  b=(r/name).read_bytes(); dest=out/'source'/name; dest.parent.mkdir(parents=True,exist_ok=True); dest.write_bytes(b); sources.append({'path':name,'sha256':hashlib.sha256(b).hexdigest()})
(out/'source-sha256.json').write_text(json.dumps(sources,indent=2)+'\n')
start=time.time()
with (out/'unit.log').open('wb') as f: result=subprocess.run(cmd,cwd=r,env=env,stdout=f,stderr=subprocess.STDOUT)
(out/'unit.exit').write_text(str(result.returncode)+'\n')
counts={k:0 for k in ['suites','tests','failures','errors','skipped']}; reports=[]
for src in sorted(r.glob('backend/*/target/surefire-reports/TEST-*.xml')):
 assert src.stat().st_mtime>=start-2,str(src)
 dest=out/'xml'/src.relative_to(r); dest.parent.mkdir(parents=True,exist_ok=True); shutil.copyfile(src,dest)
 suite=ET.parse(src).getroot(); counts['suites']+=1
 for key in ['tests','failures','errors','skipped']: counts[key]+=int(suite.get(key,0))
 reports.append(str(src.relative_to(r)))
summary={'head':head,'exit':result.returncode,**counts,'reports':reports,'newSourceFiles':len(sources)}
(out/'summary.json').write_text(json.dumps(summary,indent=2)+'\n'); print(json.dumps(summary),flush=True)
assert result.returncode==0 and counts['tests']>0 and all(counts[k]==0 for k in ['failures','errors','skipped'])
