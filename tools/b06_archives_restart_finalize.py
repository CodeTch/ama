"""Seal only new Task4 public delivery; verify source association and prior fixture manifest preservation."""
import hashlib,json,pathlib,subprocess,sys,xml.etree.ElementTree as ET
from b06_archives_restart_run import ROOT,E,SOURCES,environment
BASE='5563f4cebd6f3f8d5c44d67c4e9b54f0a1d83d00'
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def save(name,value):(E/name).write_text(json.dumps(value,indent=2)+'\n')
def manifests(path):
 lines=path.read_text().splitlines();start=lines.index('id\tmeeting_id\tmanifest_sha\tinput_fingerprint\tbase_revision\tcancel_epoch')+1;end=next(n for n in range(start,len(lines)) if lines[n].startswith('id\ttask_type\t'))
 return {line.split('\t')[0]:line.split('\t')[2] for line in lines[start:end]}
def main():
 assert subprocess.check_output(['git','diff','--name-only',BASE],cwd=ROOT,text=True)=='' or sys.argv[1:] == ['seal'],'unexpected tracked baseline mutation before delivery commit'
 results=[]
 for p in sorted(E.glob('*/counts.json')):
  counts=json.loads(p.read_text());groups={}
  for kind,rows in counts.items():groups[kind]={key:sum(row[key] for row in rows.values()) for key in ('tests','failures','errors','skipped')}
  results.append({'run':p.parent.name,'exit':int((p.parent/'exit.txt').read_text()),'command':json.loads((p.parent/'command.json').read_text())['argv'],'counts':groups})
 save('runs.json',results)
 old=manifests(E/'custody-before/stdout.log');new=manifests(E/'custody-final/stdout.log');assert old and all(new[k]==v for k,v in old.items())
 save('prior-manifests-preserved.json',{'beforeCount':len(old),'afterCount':len(new),'priorManifestsUnchanged':True,'priorManifestSha256ByInput':old})
 dependency_paths=set(SOURCES)-{p for p in SOURCES if 'ArchiveRestart' in p or p.endswith('ConfigArchiveRestartIT.java') or p.startswith('tools/b06_archives_restart_')}
 dependency_paths.update([
 'backend/core/src/main/java/ama/canonical/CanonicalValue.java','backend/core/src/main/java/ama/canonical/CanonicalValueCodec.java',
 'backend/core/src/main/java/ama/config/CanonicalJson.java','backend/core/src/main/java/ama/config/ConfigSchema.java','backend/core/src/main/java/ama/config/ArchiveReadState.java',
 'backend/core/src/main/java/ama/access/MeetingAccess.java','backend/contracts/src/main/java/ama/contract/TaskContracts.java','backend/contracts/src/main/java/ama/contract/MinutesContracts.java','backend/contracts/src/main/java/ama/contract/ConfigArchiveContracts.java',
 'backend/adapters/src/main/java/ama/config/json/StrictConfigArchiveReader.java','backend/adapters/src/main/java/ama/config/json/StrictProcessingManifestReader.java',
 'backend/adapters/src/main/java/ama/config/json/StrictConfigIndexReader.java','backend/adapters/src/main/java/ama/config/file/ProtectedArchiveFiles.java',
 'backend/core/src/main/java/ama/config/ConfigArchiveReader.java','backend/core/src/main/java/ama/config/ProcessingManifestReader.java','backend/core/src/main/java/ama/config/WireValues.java',
 'backend/adapters/src/main/java/ama/db/MysqlDataSources.java',
 'backend/adapters/src/main/java/ama/task/JdbcSubjectFences.java','backend/adapters/src/main/java/ama/task/TaskTransactionContext.java','backend/integration-tests/src/test/java/ama/support/MysqlTestDatabase.java'])
 dependencies=[]
 for name in sorted(dependency_paths):
  actual=(ROOT/name).read_bytes();original=subprocess.check_output(['git','show',BASE+':'+name],cwd=ROOT);assert actual==original
  dependencies.append({'path':name,'sha256':hashlib.sha256(actual).hexdigest(),'baseline':BASE})
 save('current-source-dependencies.json',dependencies)
 r=E/'red-03/source';g=E/'green-01/source'
 for name in ['backend/integration-tests/src/test/java/ama/transcript/ConfigArchiveRestartIT.java','backend/integration-tests/src/test/java/ama/transcript/ArchiveRestartFixture.java']:assert (r/name).read_bytes()==(g/name).read_bytes()==(ROOT/name).read_bytes()
 probe='backend/integration-tests/src/test/java/ama/transcript/ArchiveRestartProbe.java';assert (g/probe).read_bytes()==(ROOT/probe).read_bytes()
 diff=subprocess.run(['diff','-u',str(r/probe),str(g/probe)],capture_output=True,text=True);assert diff.returncode==1;(E/'red-to-green-probe.diff').write_text(diff.stdout)
 for literal in (ROOT/'backend/integration-tests/src/test/resources/ama/transcript/restart').glob('*'):
  if literal.name=='.gitattributes':continue
  assert literal.read_bytes()==(ROOT/'backend/adapters/src/test/resources/ama/config/file/b06'/literal.name).read_bytes()
 public=[]
 for parent in [E,ROOT/'backend/integration-tests/src/test/resources/ama/transcript/restart']:
  public.extend(p for p in parent.rglob('*') if p.is_file() and p.name not in {'SHA256SUMS','public-allowlist.txt'})
 public.extend(ROOT/p for p in ['backend/integration-tests/src/test/java/ama/transcript/ArchiveRestartFixture.java','backend/integration-tests/src/test/java/ama/transcript/ArchiveRestartProbe.java','backend/integration-tests/src/test/java/ama/transcript/ConfigArchiveRestartIT.java','tools/b06_archives_restart_run.py','tools/b06_archives_restart_audit.py','tools/b06_archives_restart_finalize.py','docs/acceptance/b/task-B-06-archives-integration.md','docs/acceptance/b/task-B-06-archives-report.md'])
 assert all(p.is_file() for p in public)
 secrets=[v.encode() for k,v in environment().items() if 'PASSWORD' in k and len(v)>=6]
 for p in public:
  raw=p.read_bytes();assert not any(secret in raw for secret in secrets),'private fixture password in '+str(p.relative_to(ROOT))
 names=sorted({str(p.relative_to(ROOT)) for p in public});allow=E/'public-allowlist.txt';allow.write_text('\n'.join(names+['artifacts/phase-b/b06-archives/restart/public-allowlist.txt','artifacts/phase-b/b06-archives/restart/SHA256SUMS'])+'\n')
 public.append(allow);(E/'SHA256SUMS').write_text(''.join(digest(p)+'  '+str(p.relative_to(ROOT))+'\n' for p in sorted(set(public))))
 print('new public files sealed='+str(len(public))+'; prior manifest hashes preserved='+str(len(old))+'; baseline source dependency files='+str(len(dependencies)))
if __name__=='__main__':main()
