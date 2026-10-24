"""Task11 exact-source serial test windows. Only the explicitly ACKed fresh schema; no provisioning."""
import datetime,hashlib,json,os,pathlib,re,shlex,shutil,stat,subprocess,sys,time,xml.etree.ElementTree as ET
ROOT=pathlib.Path(__file__).resolve().parents[1]
W=ROOT/'.superpowers/sdd/2026-10-23-ama-phase-b-remaining'
OUT=ROOT/'artifacts/phase-b/b-remaining/task-11'
ACK='dcab31da53de0a4e065aa05914f5143a5afd68ddbd785a87617455db1aa1574d'
def digest(b):return hashlib.sha256(b).hexdigest()
def main():
 label,group,selector=sys.argv[1:]
 if not re.fullmatch(r'[a-z0-9-]+',label) or group not in ('it','unit'):raise SystemExit('Invalid bounded window')
 ackbytes=(W/'task-11-root-fixture-application-ack-20261025.json').read_bytes();assert digest(ackbytes)==ACK
 ack=json.loads(ackbytes);private=ROOT/ack['privateEnv'];assert stat.S_IMODE(private.stat().st_mode)==0o600 and stat.S_IMODE(private.parent.stat().st_mode)==0o700
 env=os.environ.copy()
 for line in private.read_text().splitlines():
  if not line.strip() or line.lstrip().startswith('#'):continue
  match=re.fullmatch(r'(?:export )?([A-Z][A-Z0-9_]*)=(.*)',line);assert match
  values=shlex.split(match.group(2));assert len(values)==1;env[match.group(1)]=values[0]
 assert env['AMA_TEST_DATABASE_URL']=='jdbc:mysql://127.0.0.1:46151/ama_t11_9714255c9e2c_behavior'
 target=OUT/label;target.mkdir(exist_ok=False)
 env.update(JAVA_TOOL_OPTIONS='-Xmx512m -XX:ActiveProcessorCount=2',MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2',MAVEN_CONFIG='-Dtest=Task11NoImplicitUnitSuite -Dsurefire.failIfNoSpecifiedTests=false',AMA_TASK11_EVIDENCE_WINDOW=str(target))
 if group=='unit':env.pop('MAVEN_CONFIG',None)
 candidates=subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard','--','backend','contracts/openapi','tools/checks/run.py','tools/checks/database_design.py','tools/task11_window.py'],cwd=ROOT,text=True).splitlines()
 manifest=[];head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
 changed=set(subprocess.check_output(['git','ls-files','--modified','--others','--exclude-standard','--','backend','contracts/openapi','tools'],cwd=ROOT,text=True).splitlines())
 for name in sorted(set(candidates)):
  path=ROOT/name
  if not path.is_file() or '/target/' in name:continue
  b=path.read_bytes();manifest.append({'path':name,'bytes':len(b),'sha256':digest(b)})
  if name in changed:
   saved=target/'source'/name;saved.parent.mkdir(parents=True,exist_ok=True);saved.write_bytes(b)
 (target/'source-manifest.json').write_text(json.dumps({'base':head,'files':manifest},indent=2)+'\n')
 def sample(where):
  cmd=['docker','exec',ack['cid'],'sh','-c','for f in memory.current memory.peak memory.max memory.events; do echo "$f"; cat "/sys/fs/cgroup/$f"; done']
  r=subprocess.run(cmd,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True);(target/('resource-'+where+'.txt')).write_text(r.stdout);assert r.returncode==0
 sample('before');started=time.time_ns();command=[sys.executable,'tools/checks/run.py',group,selector]
 (target/'command.json').write_text(json.dumps({'command':command,'cwd':str(ROOT),'startedNs':started,'startedUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'heapMiB':512,'processors':2,'schema':ack['schema'],'ackSha256':ACK,'sourceManifestSha256':digest((target/'source-manifest.json').read_bytes())},indent=2)+'\n')
 with (target/'run.log').open('w') as log:result=subprocess.run(command,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
 finished=time.time_ns();sample('after');counts=[]
 for report in ROOT.glob('backend/*/target/*-reports/TEST-*.xml'):
  if report.stat().st_mtime_ns<started:continue
  dest=target/'junit'/report.relative_to(ROOT);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(report,dest);tree=ET.parse(report).getroot();counts.append({'path':str(dest.relative_to(target)),'suite':tree.get('name'),**{k:int(tree.get(k,'0')) for k in ('tests','failures','errors','skipped')}})
 summary={'exitCode':result.returncode,'startedNs':started,'finishedNs':finished,'suites':counts,'totals':{k:sum(c[k] for c in counts) for k in ('tests','failures','errors','skipped')}}
 (target/'result.json').write_text(json.dumps(summary,indent=2)+'\n');print(json.dumps(summary,indent=2),flush=True)
 sys.exit(result.returncode)
if __name__=='__main__':main()
