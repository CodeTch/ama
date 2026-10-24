from pathlib import Path
import os,subprocess,sys,time,json,hashlib,shutil,xml.etree.ElementTree as ET,importlib.util,fnmatch
spec=importlib.util.spec_from_file_location('owned',Path(__file__).parent.parent/'run-owned.py');owned=importlib.util.module_from_spec(spec);spec.loader.exec_module(owned)
name,selector=sys.argv[1:3];kind=sys.argv[3] if len(sys.argv)>3 else 'it'
out=owned.OUT/'fix01'/name;out.mkdir(exist_ok=False);e=owned.env();e['TASK07_EVIDENCE_WINDOW']=str(out.resolve())
for helper in [Path(__file__),Path(__file__).parent.parent/'run-owned.py']:shutil.copyfile(helper,out/helper.name)
if kind=='it':e['MAVEN_CONFIG']='-Dtest=__Task07OnlyIntegration -Dsurefire.failIfNoSpecifiedTests=false'
cmd=['python3','tools/checks/run.py',kind,selector]
(out/'source.json').write_text(json.dumps({str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in Path('backend').rglob('*') if p.is_file() and '/target/' not in str(p) and p.suffix in ['.java','.sql']},indent=2)+'\n')

# Exact mutable source plus selected tests and transitively used controlled helpers.
changed=set(subprocess.run(['git','diff','--name-only','HEAD','--','backend'],capture_output=True,text=True,check=True).stdout.splitlines())
changed.update(subprocess.run(['git','ls-files','--others','--exclude-standard','backend'],capture_output=True,text=True,check=True).stdout.splitlines())
for p in Path('backend').rglob('*.java'):
 if '/target/' not in str(p) and (fnmatch.fnmatchcase(p.stem,selector.split('#')[0]) or p.name.startswith(('Task07','Task08Inherited','Task08Probe'))):changed.add(str(p))
for name in sorted(changed):
 p=Path(name)
 if p.is_file() and p.suffix in ['.java','.sql']:
  dest=out/'source'/p;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,dest)
start=time.time()
with (out/'run.log').open('w') as log:r=subprocess.run(cmd,env=e,stdout=log,stderr=subprocess.STDOUT)
reports=[];counts={'tests':0,'failures':0,'errors':0,'skipped':0}
for p in Path('backend').glob('*/target/'+('failsafe-reports' if kind=='it' else 'surefire-reports')+'/TEST-*.xml'):
 if p.stat().st_mtime>=start:
  dest=out/(p.parts[1]+'-'+p.name);shutil.copy2(p,dest);reports.append(str(dest));x=ET.parse(p).getroot()
  for k in counts:counts[k]+=int(x.attrib.get(k,0))
(out/'receipt.json').write_text(json.dumps({'command':cmd,'exit':r.returncode,'reports':reports,'elapsedSeconds':time.time()-start,'counts':counts,'mavenConfig':e.get('MAVEN_CONFIG')},indent=2)+'\n')
print(json.dumps(counts),'exit',r.returncode);print((out/'run.log').read_text()[-4000:]);sys.exit(r.returncode)
