"""Serial precise selectors with retained raw logs/XML/source and private fixture values never serialized."""
import hashlib,json,os,pathlib,shlex,shutil,subprocess,sys,xml.etree.ElementTree as ET
ROOT=pathlib.Path(__file__).resolve().parents[1]
E=ROOT/'artifacts/phase-b/b06-archives/restart'
PRIVATE=pathlib.Path('/home/tanren/ama/.worktrees/b06-processing-facts/.tools/b06-archives/env.sh')
SOURCES=[
 'backend/integration-tests/src/test/java/ama/transcript/ArchiveRestartFixture.java',
 'backend/integration-tests/src/test/java/ama/transcript/ArchiveRestartProbe.java',
 'backend/integration-tests/src/test/java/ama/transcript/ConfigArchiveRestartIT.java',
 'backend/core/src/main/java/ama/transcript/BatchFingerprint.java',
 'backend/core/src/main/java/ama/transcript/ProcessingFactsReader.java',
 'backend/adapters/src/main/java/ama/transcript/JdbcProcessingFactsReader.java',
 'backend/core/src/main/java/ama/config/ConfigArchiveSource.java',
 'backend/core/src/main/java/ama/config/ConfigArchiveIndex.java',
 'backend/core/src/main/java/ama/config/ProcessingManifestRefs.java',
 'backend/core/src/main/java/ama/config/ConfigBindings.java',
 'backend/adapters/src/main/java/ama/config/file/ControlledConfigArchiveSource.java',
 'backend/adapters/src/main/java/ama/config/file/ControlledArchiveReleaseWriter.java',
 'backend/adapters/src/main/java/ama/task/JdbcTaskQueue.java',
 'backend/adapters/src/main/java/ama/task/JdbcTaskPreparations.java',
 'backend/adapters/src/main/java/ama/task/JdbcOutbox.java',
 'backend/adapters/src/main/java/ama/meeting/JdbcMeetingAccess.java',
 'backend/adapters/src/main/java/ama/db/TransactionRunner.java',
 'tools/checks/run.py','tools/b06_archives_restart_run.py']
def environment():
 env=os.environ.copy()
 for line in PRIVATE.read_text().splitlines():
  words=shlex.split(line)
  if not words:continue
  if words[0]!='export' or len(words)!=2:raise RuntimeError('unexpected private fixture assignment syntax')
  key,value=words[1].split('=',1);env[key]=value
 env['JAVA_HOME']='/home/tanren/ama/.worktrees/phase-a/.tools/zulu21.52.203-ca-jdk21.0.12.1-linux_x64'
 env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH']
 return env
def main():
 name,group,selector=sys.argv[1:];d=E/name;d.mkdir(parents=True,exist_ok=False)
 paths=SOURCES+[str(p.relative_to(ROOT)) for p in (ROOT/'backend/integration-tests/src/test/resources/ama/transcript/restart').glob('*')]
 hashes=[]
 for name in paths:
  p=ROOT/name;out=d/'source'/name;out.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,out);hashes.append({'path':name,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
 (d/'source-sha256.json').write_text(json.dumps(hashes,indent=2)+'\n')
 command=['python3','tools/checks/run.py',group,selector];env=environment();env['AMA_B06_RESTART_EVIDENCE']=str(d)
 (d/'command.json').write_text(json.dumps({'argv':command,'automaticDependencyUnits':group=='it','javaMajor':21},indent=2)+'\n')
 with (d/'stdout.log').open('w') as out,(d/'stderr.log').open('w') as err:result=subprocess.run(command,cwd=ROOT,env=env,stdout=out,stderr=err)
 (d/'exit.txt').write_text(str(result.returncode)+'\n');counts={'it':{},'unit':{}}
 for p in ROOT.glob('backend/*/target/*-reports/TEST-*.xml'):
  kind='it' if p.parent.name=='failsafe-reports' else 'unit';out=d/('xml' if kind=='it' else 'unit-xml')/p.parent.parent.parent.name/p.name;out.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,out)
  r=ET.parse(p).getroot();counts[kind][r.attrib['name']]={key:int(r.get(key,'0')) for key in ('tests','failures','errors','skipped')}
 (d/'counts.json').write_text(json.dumps(counts,indent=2)+'\n')
 print(f'{d.name}: exit={result.returncode}; evidence={d}')
 return result.returncode
if __name__=='__main__':sys.exit(main())
