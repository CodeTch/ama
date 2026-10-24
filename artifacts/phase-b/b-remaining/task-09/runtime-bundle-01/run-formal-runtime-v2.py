from pathlib import Path
import sys,os,json,hashlib,shlex,subprocess,datetime,signal,time
B=Path(__file__).resolve().parent;P=B.parents[4];manifest=B/'manifest.json';digest=hashlib.sha256(manifest.read_bytes()).hexdigest()
ack=Path(sys.argv[1]);expected=sys.argv[2];assert hashlib.sha256(ack.read_bytes()).hexdigest()==expected and digest.encode() in ack.read_bytes(),'Exact runtime application ACK required'
m=json.loads(manifest.read_text())
for entry in m['files']:
 f=Path(entry['path']);assert f.is_file() and hashlib.sha256(f.read_bytes()).hexdigest()==entry['sha256'],'Frozen bundle changed'
out=P/'artifacts/phase-b/b-remaining/task-09/formal-runtime-start-01';out.mkdir()
env=os.environ.copy()
private=P/'.tools/task09-2d0e28ea775540f8b3525f9344edf5f0/env.sh';assert private.stat().st_mode&511==384
for line in private.read_text().splitlines():
 if line.startswith('export '):
  key,value=line[7:].split('=',1);env[key]=shlex.split(value)[0]
assert env['AMA_TEST_DATABASE_URL']=='jdbc:mysql://127.0.0.1:44727/ama_t09_2d0e28ea7755_behavior'
env['AMA_TASK09_EVIDENCE_WINDOW']=str(out);env['JAVA_TOOL_OPTIONS']='-Xmx64m -XX:ActiveProcessorCount=2'
command=m['command'];(out/'command.json').write_text(json.dumps({'command':command,'bundleSha256':digest,'ackSha256':expected,'startedUtc':datetime.datetime.now(datetime.timezone.utc).isoformat()},indent=2)+'\n')
process=None;timed_out=False;forced_group_cleanup=False
with (out/'output.log').open('wb') as log:
 try:
  process=subprocess.Popen(command,cwd=P,env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
  try:code=process.wait(timeout=90)
  except subprocess.TimeoutExpired:timed_out=True;code=124
 except BaseException:
  code=125
  raise
 finally:
  if process is not None:
   def group_exists():
    try:os.killpg(process.pid,0);return True
    except ProcessLookupError:return False
   if group_exists():
    forced_group_cleanup=True
    try:os.killpg(process.pid,signal.SIGTERM)
    except ProcessLookupError:pass
    until=time.monotonic()+5
    while group_exists() and time.monotonic()<until:time.sleep(.05)
    if group_exists():
     try:os.killpg(process.pid,signal.SIGKILL)
     except ProcessLookupError:pass
    try:process.wait(timeout=5)
    except subprocess.TimeoutExpired:code=126
   if code==0 and forced_group_cleanup:code=127
   (out/'process-lifecycle.json').write_text(json.dumps({'pid':process.pid,'pgid':process.pid,'exit':process.returncode,'timedOut':timed_out,'forcedGroupCleanup':forced_group_cleanup,'groupRemaining':group_exists()})+'\n')
(out/'exit.json').write_text(json.dumps({'exit':code,'endedUtc':datetime.datetime.now(datetime.timezone.utc).isoformat()})+'\n');print('formal-runtime exit='+str(code)+' evidence='+str(out));sys.exit(code)
