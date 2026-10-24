"""Task11 exact-source serial test windows. Only the explicitly ACKed fresh schema; no provisioning."""
import base64,datetime,hashlib,json,os,pathlib,re,shlex,shutil,stat,subprocess,sys,time,xml.etree.ElementTree as ET
ROOT=pathlib.Path(__file__).resolve().parents[1]
W=ROOT/'.superpowers/sdd/2026-10-23-ama-phase-b-remaining'
OUT=ROOT/'artifacts/phase-b/b-remaining/task-11'
ACK='dcab31da53de0a4e065aa05914f5143a5afd68ddbd785a87617455db1aa1574d'
def digest(b):return hashlib.sha256(b).hexdigest()
def main():
 label,group,selector=sys.argv[1:]
 if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_]*(?:#[A-Za-z][A-Za-z0-9_]*)?',selector) or not re.fullmatch(r'[a-z0-9-]+',label) or group not in ('it','unit'):raise SystemExit('Invalid bounded window')
 ackbytes=(W/'task-11-root-fixture-application-ack-20261025.json').read_bytes();assert digest(ackbytes)==ACK
 ack=json.loads(ackbytes);private=ROOT/ack['privateEnv'];assert stat.S_IMODE(private.stat().st_mode)==0o600 and stat.S_IMODE(private.parent.stat().st_mode)==0o700
 env=os.environ.copy()
 env['JAVA_HOME']='/home/tanren/ama/.worktrees/phase-a/.tools/zulu21.52.203-ca-jdk21.0.12.1-linux_x64'
 assert pathlib.Path(env['JAVA_HOME'],'bin/java').is_file()
 env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH']
 for line in private.read_text().splitlines():
  if not line.strip() or line.lstrip().startswith('#'):continue
  match=re.fullmatch(r'(?:export )?([A-Z][A-Z0-9_]*)=(.*)',line);assert match
  values=shlex.split(match.group(2));assert len(values)==1;env[match.group(1)]=values[0]
 assert env['AMA_TEST_DATABASE_URL']=='jdbc:mysql://127.0.0.1:46151/ama_t11_9714255c9e2c_behavior'
 target=OUT/label;target.mkdir(exist_ok=False)
 env.update(JAVA_TOOL_OPTIONS='-Xmx512m -XX:ActiveProcessorCount=2',MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2',MAVEN_CONFIG='-Dtest=Task11NoImplicitUnitSuite -Dsurefire.failIfNoSpecifiedTests=false',AMA_TASK11_EVIDENCE_WINDOW=str(target),AMA_TASK11_REPOSITORY_ROOT=str(ROOT))
 if group=='unit':env.pop('MAVEN_CONFIG',None)
 for suffix in ('OWNER','RUN_ROOT','S3_ENV','PKI_ROOT'):env['AMA_TASK09_'+suffix]=env['AMA_TASK11_'+suffix]
 env['AMA_TASK09_EVIDENCE_WINDOW']=str(target)
 modules=[ROOT/'backend/integration-tests'] if group=='it' else sorted(p.parent for p in ROOT.glob('backend/*/pom.xml'))
 matched=[p for m in modules for p in (m/'src/test/java').rglob(selector.split('#',1)[0]+'.java')]
 assert len(matched)==1,'Selector must identify exactly one discovered class'
 canonical=re.search(r'package\s+([^;]+);',matched[0].read_text()).group(1).strip()+'.'+matched[0].stem
 secret_values=[v.encode() for k,v in env.items() if k.startswith('AMA_') and any(x in k for x in ('PASSWORD','SECRET','TOKEN'))]
 credential_ack=json.loads((W/'task-11-knowledge-credential-root-ack-20261025.json').read_text())
 for name in credential_ack['privateScanInputs']:
  value=pathlib.Path(name).read_bytes();encoded=base64.b64encode(value);url=base64.urlsafe_b64encode(value)
  secret_values.extend([value,value.hex().encode(),encoded,encoded.rstrip(b'='),url,url.rstrip(b'=')])
  if name.endswith(('.pk8','.spki')):
   kind=b'PRIVATE KEY' if name.endswith('.pk8') else b'PUBLIC KEY'
   secret_values.append(b'-----BEGIN '+kind+b'-----\n'+b'\n'.join(encoded[i:i+64] for i in range(0,len(encoded),64))+b'\n-----END '+kind+b'-----')

 report_glob='backend/*/target/'+('failsafe-reports' if group=='it' else 'surefire-reports')+'/TEST-*.xml'
 previous={str(f):(f.stat().st_mtime_ns,digest(f.read_bytes())) for f in ROOT.glob(report_glob)}
 (target/'reports-before.json').write_text(json.dumps(previous,indent=2)+'\n')
 for f in ROOT.glob(report_glob):
  assert f.resolve().is_relative_to((ROOT/'backend').resolve()) and 'target' in f.parts
  f.unlink()
 assert not list(ROOT.glob(report_glob))
 (target/'reports-cleared.json').write_text(json.dumps({'paths':list(previous),'verifiedAbsent':True,'atNs':time.time_ns()},indent=2)+'\n')
 candidates=subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard','--','backend','contracts/openapi','tools/checks/run.py','tools/checks/database_design.py','tools/task11_window.py','tools/providers'],cwd=ROOT,text=True).splitlines()
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
 (target/'command.json').write_text(json.dumps({'command':command,'canonicalSelector':canonical,'reportsBeforeSha256':digest((target/'reports-before.json').read_bytes()),'reportsClearedSha256':digest((target/'reports-cleared.json').read_bytes()),'cwd':str(ROOT),'startedNs':started,'startedUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'heapMiB':512,'processors':2,'schema':ack['schema'],'ackSha256':ACK,'sourceManifestSha256':digest((target/'source-manifest.json').read_bytes())},indent=2)+'\n')
 with (target/'run.log').open('w') as log:result=subprocess.run(command,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
 finished=time.time_ns();sample('after');counts=[];cases=[];reports=[]
 log_text=(target/'run.log').read_text(errors='replace')
 for report in sorted(ROOT.glob(report_glob)):
  report_stat=report.stat();raw=report.read_bytes();sha=digest(raw);prior=previous.get(str(report));tree=ET.fromstring(raw);suite=tree.get('name','')
  qualified=started<=report_stat.st_mtime_ns<=finished and prior!=(report_stat.st_mtime_ns,sha) and ('Running '+suite) in log_text and suite==canonical
  reports.append({'path':str(report.relative_to(ROOT)),'mtimeNs':report_stat.st_mtime_ns,'sha256':sha,'previous':prior,'qualified':qualified,'suite':suite,'canonicalLogAssociation':('Running '+suite) in log_text})
  if not qualified:continue
  dest=target/'junit'/report.relative_to(ROOT);dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(raw)
  counts.append({'path':str(dest.relative_to(target)),'suite':suite,**{k:int(tree.get(k,'0')) for k in ('tests','failures','errors','skipped')}})
  for c in tree.findall('testcase'):cases.append({'suite':suite,'reportSha256':sha,'reportMtimeNs':report_stat.st_mtime_ns,'startedNs':started,'endedNs':finished,'class':c.get('classname'),'name':c.get('name'),'failure':c.find('failure') is not None,'error':c.find('error') is not None,'skipped':c.find('skipped') is not None})
 (target/'report-custody.json').write_text(json.dumps({'startedNs':started,'endedNs':finished,'canonicalSelector':canonical,'reports':reports,'logSha256':digest((target/'run.log').read_bytes())},indent=2)+'\n')
 for f in target.rglob('*'):
  if f.is_file():assert not any(len(v)>3 and v in f.read_bytes() for v in secret_values),'private value found; do not print'
 totals={k:sum(c[k] for c in counts) for k in ('tests','failures','errors','skipped')}
 exitcode=result.returncode or (1 if totals['tests']==0 or any(totals[k] for k in ('failures','errors','skipped')) or any(not r['qualified'] for r in reports) else 0)
 summary={'exitCode':exitcode,'processExitCode':result.returncode,'startedNs':started,'finishedNs':finished,'suites':counts,'totals':totals,'cases':cases}
 (target/'result.json').write_text(json.dumps(summary,indent=2)+'\n');print(json.dumps(summary,indent=2),flush=True)
 sys.exit(exitcode)
if __name__=='__main__':main()
