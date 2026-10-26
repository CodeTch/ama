"""One exact B3 PREPARED-local window; source/release guard precedes environment and exclusive target."""
import base64,datetime,hashlib,json,os,pathlib,re,shlex,signal,stat,subprocess,sys,time,threading,xml.etree.ElementTree as ET
from task11_report_custody import exclusive_runner_lock,collect_reports
ROOT=pathlib.Path(__file__).resolve().parents[1]
W=ROOT/'.superpowers/sdd/2026-10-23-ama-phase-b-remaining'
OUT=ROOT/'artifacts/phase-b/b-remaining/task-11'
ACK='task-11-b3-prepared-local-r2-configuration-ack-20261026.json'
ACK_SHA='db267767a115d8a1262c10b31a85d06eb02bf3bb73820b8f53808bcb5bce4727'
TUPLE=('b3-prepared-local-01','it','Task11B3PreparedLocalIT','task11-b2-exact-isolated')
BUDGET=dict(servletRequests=22,parentContexts=2,childContexts=1,sessionInsertsMax=6,sessionDeletesMax=6,faultPoints=12,localDecisions=1,newSealedAcknowledgments=1,maintenanceScheduledPassesMax=3,freshJvmChildren=1,providerGetIncrements=0,destinationPostIncrements=0,publicationPostIncrements=0,sqliteObservations=0,grants=0,nativeStarts=0,claims=0,successors=0)
PINS={'readbackAdoption','readbackReview','readbackFacts','readbackCustody','sourceBase','allocation','overlay','credentials','retainedOrigin'}
def digest(raw):return hashlib.sha256(raw).hexdigest()
def public_bytes(path):
 assert path.is_absolute() and path.is_relative_to(ROOT) and '..' not in path.parts and not path.is_relative_to(ROOT/'.tools')
 current=ROOT
 for part in path.relative_to(ROOT).parts:current=current/part;assert current.exists() and not current.is_symlink()
 assert path.is_file() and path.resolve()==path and path.stat().st_size<=1048576
 return path.read_bytes()
def parse(raw):
 def unique(pairs):
  out={}
  for key,value in pairs:assert key not in out,'Duplicate JSON field';out[key]=value
  return out
 return json.loads(raw,object_pairs_hook=unique)
def pinned(path,sha):
 assert re.fullmatch('[0-9a-f]{64}',sha);raw=public_bytes(path);assert digest(raw)==sha;return parse(raw)
def budget(value):assert value==BUDGET and all(type(value[k]) is int for k in BUDGET)
def validate_configuration(cfg):
 assert cfg['status']=='B3_PREPARED_LOCAL_CONFIGURATION_INERT' and tuple(cfg[k] for k in ('label','group','selector','fixtureName'))==TUPLE
 assert all(cfg[k] is False for k in ('runtimeAuthorized','grantAuthorized','nativeStartAuthorized','sendAuthorized'))
 assert cfg['owner']=='task11b2-d9e2fdcc611e4e21bb360e539ca46258' and cfg['schema']=='ama_t11b2_d9e2fdcc611e_behavior' and cfg['username']=='t11b2_d9e2fdcc611e' and cfg['cid']=='11009a6b34fef0416e651100fbde05a07067dbcbd973f1ba731c08b2281140ad' and cfg['port']==46151
 assert (cfg['meetingId'],cfg['publicationId'],cfg['taskId'],cfg['executionId'],cfg['operationId'],cfg['meetingRevision'],cfg['publicationRevision'])==('36db643e-5fbd-4f9a-9a85-a7e6b7d76e38','142a236c-bcd6-4608-8e93-2dd5446c5fa9','3588aaba-ecfa-4889-8954-46c1454447d4','65fa6b21-22da-4a2e-a886-341b16978106','896456c1-cc73-4a8c-803d-8b5bb058d4c7',13,6)
 budget(cfg['budget']);assert set(cfg['inputPins'])==PINS
 values={}
 for key,pin in cfg['inputPins'].items():
  assert pathlib.Path(pin['path']).name==pin['path'];raw=public_bytes(W/pin['path']);assert digest(raw)==pin['sha256']
  if key in ('readbackAdoption','readbackFacts','allocation','overlay','credentials','retainedOrigin'):values[key]=parse(raw)
 adoption=values['readbackAdoption'];facts=values['readbackFacts'];assert adoption['status']=='EXISTING_SUCCESSOR_READBACK_ADOPTED_FOR_B3' and adoption['factsSha256']==cfg['inputPins']['readbackFacts']['sha256'] and adoption['reviewSha256']==cfg['inputPins']['readbackReview']['sha256'] and adoption['sqliteBudgetRemainingForReadbackScope']==0
 assert facts['existingTaskId']==cfg['taskId'] and facts['existingExecutionId']==cfg['executionId'] and facts['taskState']=='READY' and facts['executionState']=='PREPARED' and facts['publicationRevision']==6 and facts['businessBeforeAfterIdentical'] is True
 assert cfg['baselineRows']=='artifacts/phase-b/b-remaining/task-11/b2-exact-retry-readback-01/accepted-rows-after.json' and len(pinned(ROOT/cfg['baselineRows'],cfg['baselineRowsSha256'])['rows'])==17
 assert cfg['runtimeSourceCheckpoint']=='task-11-b3-prepared-local-r2-source-checkpoint-20261026.json' and cfg['runtimeReleaseFile']=='task-11-b3-prepared-local-runtime-release-20261026.json'
 release=parse(public_bytes(W/cfg['runtimeReleaseFile']));assert release['status']=='B3_PREPARED_LOCAL_RUNTIME_RELEASED_ONCE' and tuple(release[k] for k in ('label','group','selector','fixtureName'))==TUPLE and release['configurationAckSha256']==ACK_SHA
 assert type(release['maxInvocations']) is int and release['maxInvocations']==1 and all(release[k] is False for k in ('oneGrantAuthorized','nativeStartAuthorized','sendAuthorized'));budget(release['budget'])
 issued=datetime.datetime.fromisoformat(release['issuedUtc'].replace('Z','+00:00'));deadline=datetime.datetime.fromisoformat(release['startBeforeUtc'].replace('Z','+00:00'));now=datetime.datetime.now(datetime.timezone.utc);assert issued.tzinfo is not None and deadline.tzinfo is not None and issued<=now<deadline
 cp=pinned(W/cfg['runtimeSourceCheckpoint'],release['sourceCheckpointSha256']);assert isinstance(cp['files'],list) and 0<len(cp['files'])<=2048
 for row in cp['files']:assert digest(public_bytes(ROOT/row['path']))==row['sha256']
 return values,cp

def private_bytes(path,owned,maximum=65536):
 assert path.is_absolute() and path.is_relative_to(owned) and '..' not in path.parts
 current=owned;assert current.resolve()==current and stat.S_IMODE(current.stat().st_mode)==0o700
 for part in path.relative_to(owned).parts:
  current=current/part;assert not current.is_symlink() and current.resolve()==current and stat.S_IMODE(current.stat().st_mode)==(0o600 if current==path else 0o700)
 assert path.is_file() and path.stat().st_size<=maximum
 return path.read_bytes()

GROUP_TERM_SECONDS=5
GROUP_KILL_SECONDS=5

def live_group_members(group):
 """Inspect only process identity/state, not cmdline; zombies confer no running capability."""
 assert type(group) is int and group>1
 members=[];entries=0
 for path in pathlib.Path('/proc').iterdir():
  if not path.name.isdecimal():continue
  entries+=1;assert entries<=16384,'Finite process identity inventory exceeded'
  try:
   raw=(path/'stat').read_text();fields=raw[raw.rfind(')')+2:].split()
  except (FileNotFoundError,ProcessLookupError):continue
  if int(fields[2])==group and fields[0] not in ('Z','X'):members.append(dict(pid=int(path.name),state=fields[0],startTicks=fields[19]))
 return sorted(members,key=lambda row:row['pid'])

def stop_owned_group(child):
 """Bounded exact-group cleanup defers interruptions across observation, signals and reap."""
 group=child.pid;custody=dict(processGroup=group,signals=[],quiescent=False,remainingLiveMembers=None);interrupt=None;failure=None;deferred=set();previous={}
 def remember(error):
  nonlocal interrupt
  interrupt=interrupt or error
 def probe(deadline):
  # Observation is side-effect-free and can safely resume after an interrupted /proc read.
  while True:
   try:return live_group_members(group)
   except (KeyboardInterrupt,InterruptedError) as error:
    remember(error)
    if time.monotonic()>=deadline:return None
 def defer(signum,frame):deferred.add(signum)
 if threading.current_thread() is threading.main_thread():
  for sig in (signal.SIGTERM,signal.SIGINT):previous[sig]=signal.signal(sig,defer)
 try:
  # Do not signal a live leader which no longer belongs to our start_new_session group.
  identity_deadline=time.monotonic()+5
  while True:
   try:
    if child.poll() is None:
     try:observed_group=os.getpgid(child.pid)
     except ProcessLookupError:
      if child.poll() is None:raise
     else:assert observed_group==group,'Owned producer changed process group'
    break
   except (KeyboardInterrupt,InterruptedError) as error:
    remember(error)
    if time.monotonic()>=identity_deadline:raise RuntimeError('Owned leader identity could not be confirmed within cleanup deadline')
  for sig,seconds in ((signal.SIGTERM,GROUP_TERM_SECONDS),(signal.SIGKILL,GROUP_KILL_SECONDS)):
   deadline=time.monotonic()+seconds;members=probe(deadline)
   if 'initialLiveMembers' not in custody:custody['initialLiveMembers']=members
   if members==[]:break
   try:os.killpg(group,sig);custody['signals'].append(sig.name)
   except ProcessLookupError:pass
   except (KeyboardInterrupt,InterruptedError) as error:
    remember(error)
    if sig==signal.SIGTERM:continue
   while True:
    members=probe(deadline)
    if members==[] or time.monotonic()>=deadline:break
    try:time.sleep(.02)
    except (KeyboardInterrupt,InterruptedError) as error:remember(error);break
  deadline=time.monotonic()+5
  while True:
   try:child.wait(timeout=max(.001,deadline-time.monotonic()));break
   except (KeyboardInterrupt,InterruptedError) as error:
    remember(error)
    if time.monotonic()>=deadline:break
  custody['remainingLiveMembers']=probe(time.monotonic()+5);custody['leaderExitCode']=child.poll();custody['quiescent']=custody['remainingLiveMembers']==[] and child.poll() is not None
  if not custody['quiescent']:raise RuntimeError('Owned producer group termination could not be confirmed')
 except BaseException as error:failure=error
 finally:
  custody['closedMonotonicNs']=time.monotonic_ns();custody['deferredSignals']=[signal.Signals(sig).name for sig in sorted(deferred)]
  for sig,handler in previous.items():signal.signal(sig,handler)
 if deferred and interrupt is None:interrupt=KeyboardInterrupt() if signal.SIGINT in deferred else InterruptedError('Owned group cleanup termination deferred')
 if interrupt is not None:
  if failure is not None:interrupt.add_note('Owned group cleanup failure: '+type(failure).__name__)
  interrupt.custody=custody;raise interrupt
 if failure is not None:failure.custody=custody;raise failure
 return custody

def run_window(lock=None):
 assert tuple(sys.argv[1:])==TUPLE,'Only exact fresh B3 window is supported'
 cfg=pinned(W/ACK,ACK_SHA);values,cp=validate_configuration(cfg)
 return execute_window(cfg,values,cp,lock,run_tuple=TUPLE,ack_sha=ACK_SHA,producer_timeout=240,root=ROOT,workspace=W,out=OUT)

def execute_window(cfg,values,cp,lock,*,run_tuple,ack_sha,producer_timeout,root,workspace,out):
 """Shared mechanics after each exact caller has validated its own pinned public release."""
 assert type(producer_timeout) is int and producer_timeout in (240,420)
 ROOT,W,OUT=root,workspace,out
 env=os.environ.copy();env={k:v for k,v in env.items() if not k.startswith('AMA_') and k not in ('JAVA_TOOL_OPTIONS','MAVEN_OPTS','MAVEN_CONFIG','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','JAVA_OPTS')}
 allocation,overlay,credentials=(values[k] for k in ('allocation','overlay','credentials'));owned=ROOT/'.tools'/cfg['owner'];assert allocation['privateRoot']==str(owned)
 private=ROOT/cfg['privateEnv'];assert overlay['privateEnv']==cfg['privateEnv'];seen=set()
 for line in private_bytes(private,owned).decode().splitlines():
  if not line.strip() or line.lstrip().startswith('#'):continue
  match=re.fullmatch(r'(?:export )?([A-Z][A-Z0-9_]*)=(.*)',line);assert match and match[1] in overlay['envKeys'] and match[1] not in seen
  tokens=shlex.split(match[2]);assert len(tokens)==1;env[match[1]]=tokens[0];seen.add(match[1])
 assert seen==set(overlay['envKeys']) and len(seen)==13
 assert env['AMA_TEST_DATABASE_URL']=='jdbc:mysql://127.0.0.1:46151/'+cfg['schema'] and env['AMA_TEST_DATABASE_USERNAME']==cfg['username'] and re.fullmatch('[A-Za-z0-9_-]{43}',env['AMA_TEST_DATABASE_PASSWORD']) and env['AMA_TEST_CONTAINER']==cfg['cid']
 expected={'AMA_TASK11_OWNER':cfg['owner'],'AMA_TASK11_RUN_ROOT':str(owned),'AMA_TASK11_PKI_ROOT':allocation['native']['pkiRoot'],'AMA_TASK11_PROVIDER_ROOT':allocation['knowledge']['root'],'AMA_TASK11_MODEL_ROOT':allocation['model']['root'],'AMA_TASK11_NATIVE_ROOT':allocation['native']['root'],'AMA_TASK11_ARCHIVE_ROOT':allocation['archive']['root'],'AMA_TASK11_MODEL_AUTHORIZATION_FILE':overlay['modelAuthorizationFile'],'AMA_TASK11_ALLOCATION':str(W/'task-11-b2-runtime-allocation-intent-20261026.json')}
 assert all(env[k]==v for k,v in expected.items())
 env['JAVA_HOME']='/home/tanren/ama/.worktrees/phase-a/.tools/zulu21.52.203-ca-jdk21.0.12.1-linux_x64';assert pathlib.Path(env['JAVA_HOME'],'bin/java').is_file();env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH']
 target=OUT/run_tuple[0]
 env.update(JAVA_TOOL_OPTIONS='-Xmx512m -XX:ActiveProcessorCount=2',MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2',MAVEN_CONFIG='-Dtest=Task11NoImplicitUnitSuite -Dsurefire.failIfNoSpecifiedTests=false',AMA_TASK11_FIXTURE=run_tuple[3],AMA_TASK11_REPOSITORY_ROOT=str(ROOT),AMA_TASK11_EVIDENCE_WINDOW=str(target))
 secrets=[env['AMA_TEST_DATABASE_PASSWORD'].encode()]
 for name in credentials['privateScanInputs']+overlay['privateScanInputsAdditional']:secrets.append(private_bytes(pathlib.Path(name),owned))
 secret_values=[]
 for value in secrets:
  for token in (value,value.hex().encode(),base64.b64encode(value),base64.urlsafe_b64encode(value)):secret_values.extend((token,token.rstrip(b'=')))
 target.mkdir(exist_ok=False)
 def write(name,value):
  with (target/name).open('x') as f:json.dump(value,f,indent=2);f.write('\n')
 report_glob='backend/*/target/failsafe-reports/TEST-*.xml';previous={str(p):{'mtimeNs':p.stat().st_mtime_ns,'sha256':digest(p.read_bytes())} for p in ROOT.glob(report_glob)};write('reports-before.json',previous)
 for p in ROOT.glob(report_glob):assert p.resolve().is_relative_to(ROOT/'backend') and 'target' in p.parts;p.unlink()
 write('reports-cleared.json',dict(paths=list(previous),verifiedAbsent=not list(ROOT.glob(report_glob)),atNs=time.time_ns(),atMonotonicNs=time.monotonic_ns(),runnerLock=lock))
 for row in cp['files']:
  data=public_bytes(ROOT/row['path']);assert digest(data)==row['sha256'];dest=target/'source'/row['path'];dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(data)
 write('source-manifest.json',dict(files=cp['files'],configurationAckSha256=ack_sha,sourceCheckpointSha256=digest(public_bytes(W/cfg['runtimeSourceCheckpoint']))))
 def sample(name):
  result=subprocess.run(['docker','exec',cfg['cid'],'sh','-c','for f in memory.current memory.peak memory.max memory.events; do echo "$f"; cat "/sys/fs/cgroup/$f"; done'],capture_output=True,text=True,timeout=10);assert result.returncode==0;(target/('resource-'+name+'.txt')).write_text(result.stdout)
 sample('before');started=time.time_ns();mono=time.monotonic_ns();command=[sys.executable,'tools/checks/run.py','it',run_tuple[2]];canonical='ama.storage.'+run_tuple[2]
 write('command.json',dict(command=command,canonicalSelector=canonical,startedNs=started,startedMonotonicNs=mono,startedUtc=datetime.datetime.now(datetime.timezone.utc).isoformat(),runnerLock=lock,configurationAckSha256=ack_sha,sourceCheckpointSha256=digest(public_bytes(W/cfg['runtimeSourceCheckpoint'])),budget=cfg['budget'],heapMiB=512,processors=2))
 child=None;primary=None
 try:
  with (target/'run.log').open('x') as log:
   child=subprocess.Popen(command,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True);write('producer.json',dict(pid=child.pid,processGroup=child.pid,runnerPid=os.getpid(),command=command,startedMonotonicNs=time.monotonic_ns()));child.wait(timeout=producer_timeout)
 except BaseException as failure:primary=failure
 finally:
  if child is not None:
   cleanup=None
   group_custody=None
   try:group_custody=stop_owned_group(child)
   except BaseException as failure:cleanup=failure;group_custody=getattr(failure,'custody',None)
   try:write('child-exit.json',dict(pid=child.pid,exitCode=child.poll(),alive=child.poll() is None,childExitNs=time.time_ns(),childExitMonotonicNs=time.monotonic_ns(),cleanupFailureType=type(cleanup).__name__ if cleanup else None,ownedGroup=group_custody))
   except BaseException as failure:
    if cleanup is not None:cleanup.add_note('Exit custody write failed: '+type(failure).__name__)
    else:cleanup=failure
   if cleanup is not None:
    if primary is not None:primary.add_note('Owned producer cleanup failed: '+type(cleanup).__name__)
    else:primary=cleanup
 if primary is not None:raise primary
 child_exit=parse((target/'child-exit.json').read_bytes());sample('after');source_matches=all(digest(public_bytes(ROOT/x['path']))==x['sha256'] for x in cp['files'])
 collected=collect_reports(lambda:list(ROOT.glob(report_glob)),target/'run.log',canonical,started,child_exit['childExitMonotonicNs']);reports=[];counts=[]
 for row in collected.pop('reports'):
  raw=row.pop('raw');path=pathlib.Path(row['path']);qualified=collected['qualified'] and source_matches and previous.get(str(path))!={'mtimeNs':row['mtimeNs'],'sha256':row['sha256']};entry=dict(row,path=str(path.relative_to(ROOT)),qualified=qualified);reports.append(entry)
  dest=target/'junit'/path.relative_to(ROOT);dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(raw);tree=ET.fromstring(raw);counts.append(dict(suite=tree.get('name'),path=str(dest.relative_to(target)),**{k:int(tree.get(k,'0')) for k in ('tests','failures','errors','skipped')}))
 write('report-custody.json',dict(runnerLock=lock,collection=collected,reports=reports,sourceManifestMatchesAtCollection=source_matches,childExit=child_exit,logSha256=digest((target/'run.log').read_bytes())))
 for path in target.rglob('*'):
  if path.is_file():assert not any(len(token)>3 and token in path.read_bytes() for token in secret_values),'Private value found; do not print'
 sealed=time.monotonic_ns();assert lock['acquiredMonotonicNs']<=mono<=child_exit['childExitMonotonicNs']<=collected['openedMonotonicNs']<=collected['closedMonotonicNs']<=sealed
 write('seal.json',dict(runnerLock=lock,sealedNs=time.time_ns(),sealedMonotonicNs=sealed,collectionCustodySha256=digest((target/'report-custody.json').read_bytes()),rawCopiesVerified=True))
 totals={k:sum(x[k] for x in counts) for k in ('tests','failures','errors','skipped')};code=child.returncode or (1 if totals['tests']!=1 or any(totals[k] for k in ('failures','errors','skipped')) or not reports or any(not r['qualified'] for r in reports) else 0);result=dict(exitCode=code,processExitCode=child.returncode,totals=totals,suites=counts);write('result.json',result);print(json.dumps(result,indent=2));return code

def stop_signal(signum,frame):raise InterruptedError('Owned runner termination requested')

def main():
 previous=signal.signal(signal.SIGTERM,stop_signal)
 try:
  with exclusive_runner_lock(OUT/'.task11-runner.lock',{'arguments':sys.argv[1:],'cwd':str(ROOT),'purpose':'exact-b3-prepared-local'}) as lock:code=run_window(lock)
 finally:signal.signal(signal.SIGTERM,previous)
 sys.exit(code)
if __name__=='__main__':main()