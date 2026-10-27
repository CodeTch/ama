"""Fresh-only B-L5 resources. Explicit human+review gates; never select retained fixtures."""
import bl5_monitor
import argparse, datetime, hashlib, json, os, pathlib, re, secrets, shutil, subprocess, time, sys, signal
ROOT=pathlib.Path(__file__).resolve().parents[1]
W=ROOT/'.superpowers/sdd/2026-10-23-ama-phase-b-remaining'
PLAN=W/'bl5-resource-plan-20261027.json'
AUTH=W/'bl5-human-authorization-20261027.json'
REVIEW=W/'bl5-harness-review-20261027/approval.json'
MYSQL='mysql@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9'
S3='rustfs/rustfs@sha256:128220dc9382bac81940a1a9616ee8c274a3f3b8f647eedbeb50df5bb8b49922'
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def write_new(path,value,private=False):
 fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600 if private else 0o644)
 with os.fdopen(fd,'w') as stream:stream.write(json.dumps(value,indent=2)+'\n')
def owned_root(plan):
 name=plan['id']
 if not re.fullmatch(r'bl5-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}',name):raise ValueError('Invalid owner')
 expected=ROOT/'.tools'/name
 if pathlib.Path(plan['root'])!=expected or expected.is_symlink() or expected.resolve()!=expected:raise ValueError('Foreign or linked root')
 return expected

def verify_execution(manifest):
 artifacts=manifest.get('artifacts');directories=manifest.get('artifactDirectories')
 if not artifacts or not directories:raise ValueError('Executable closure missing')
 for name in [*artifacts,*directories]:
  path=pathlib.Path(name)
  if not path.is_absolute() or path.resolve()!=path:raise ValueError('Executable ancestor drift')
 for name,digest in artifacts.items():
  path=pathlib.Path(name)
  if path.is_symlink() or not path.is_file() or sha(path)!=digest:raise ValueError('Executable drift: '+name)
 for name,expected in directories.items():
  directory=pathlib.Path(name)
  if directory.is_symlink() or not directory.is_dir():raise ValueError('Executable directory drift')
  actual=sorted(str(p.relative_to(directory)) for p in directory.rglob('*') if p.is_file() or p.is_symlink())
  if actual!=expected:raise ValueError('Executable inventory drift: '+name)

def gate(plan,auth,review,plan_hash):
 owned_root(plan)
 if plan['lifetimeHours']!=3 or plan['retainedAccess'] or plan['schemaResetDrop'] or plan['oldWindowRerun'] or plan['directProviderSqliteReads']!=0:raise ValueError('Plan boundary mismatch')
 if plan['mysql']['image']!=MYSQL or plan['s3']['image']!=S3 or plan['pull']!='never':raise ValueError('Image boundary mismatch')
 if auth.get('status')!='APPROVED_WITH_THREE_HOUR_CAP' or auth.get('planSha256')!=plan_hash or auth.get('owner')!=plan['id'] or auth.get('runtimeSecondsMaximum')!=10800:raise ValueError('Human authorization missing or stale')
 if review.get('status')!='SOURCE_HARNESS_APPROVED' or review.get('planSha256')!=plan_hash or not review.get('sourceManifest') or not review.get('reviewReport'):raise ValueError('Independent harness/source approval missing')
 manifest=pathlib.Path(review['sourceManifest']);report=pathlib.Path(review['reviewReport'])
 if not manifest.is_relative_to(W) or not report.is_relative_to(W) or sha(manifest)!=review.get('sourceManifestSha256') or sha(report)!=review.get('reviewReportSha256'):raise ValueError('Review artifact mismatch')
 closure=json.loads(manifest.read_text());verify_execution(closure);pins=closure['paths']
 if pins.get('tools/bl5_resources.py')!=sha(ROOT/'tools/bl5_resources.py'):raise ValueError('Resource runner not reviewed')
 for name,digest in {**pins,**plan['migrations']}.items():
  path=ROOT/name
  if not path.resolve().is_relative_to(ROOT) or sha(path)!=digest:raise ValueError('Reviewed source drift: '+name)
 if len(plan['migrations'])!=16:raise ValueError('Exact sixteen existing migrations required')

def run(docker,args,timeout=90):
 result=subprocess.run([docker,*args],capture_output=True,text=True,timeout=timeout)
 if result.returncode:raise RuntimeError('Owned docker operation failed: '+args[0]+' ('+str(result.returncode)+')')
 return result.stdout.strip()
SELECTED='{"id":{{json .Id}},"name":{{json .Name}},"owner":{{json (index .Config.Labels "ama.bl5.owner")}},"image":{{json .Config.Image}},"memory":{{json .HostConfig.Memory}},"memorySwap":{{json .HostConfig.MemorySwap}},"cpus":{{json .HostConfig.NanoCpus}},"pids":{{json .HostConfig.PidsLimit}},"ports":{{json .NetworkSettings.Ports}},"mounts":{{json .Mounts}},"running":{{json .State.Running}}}'
def inspect(docker,plan,kind,cid):
 v=json.loads(run(docker,['inspect','--format',SELECTED,cid],timeout=5));p=plan[kind]
 if v['id']!=cid or v['name']!='/'+p['name'] or v['owner']!=plan['id'] or v['image']!=p['image'] or v['memory']!=p['memoryMiB']*1024**2 or v['memorySwap']!=v['memory'] or v['cpus']!=p['cpus']*1000000000 or v['pids']!=p['pids']:raise ValueError('Owned container mismatch')
 expected={str(owned_root(plan)/kind/'data'):'/var/lib/mysql' if kind=='mysql' else '/data'}
 if kind=='s3':expected[str(owned_root(plan)/kind/'logs')]='/logs'
 actual={m['Source']:m['Destination'] for m in v['mounts']}
 if actual!=expected:raise ValueError('Unexpected mount')
 port='3306/tcp' if kind=='mysql' else '9000/tcp';bindings=v['ports'].get(port)
 if v['running'] and (not isinstance(bindings,list) or len(bindings)!=1 or bindings[0]['HostIp']!='127.0.0.1' or not re.fullmatch(r'[0-9]+',bindings[0]['HostPort'])):raise ValueError('Nonlocal binding')
 return v

def resume_empty_startup(plan):
 root=owned_root(plan);evidence=pathlib.Path(plan['evidence'])
 if os.geteuid()!=0:raise ValueError('Empty startup resume requires the selected WSL root executor')
 expected={'clock.json','credentials.json','watchdog.json','watchdog.log','stopping.json','deadline-stop.json'}
 if {p.name for p in root.iterdir()}!=expected:raise ValueError('Resume only an empty stopped startup')
 for name in expected:
  p=root/name
  if p.resolve()!=p or not p.is_file():raise ValueError('Linked empty startup evidence')
 if evidence.resolve()!=evidence or not evidence.is_relative_to(W) or {p.name for p in evidence.iterdir()}!={'clock.json'}:raise ValueError('Runtime evidence already consumed')
 if (evidence/'clock.json').resolve()!=evidence/'clock.json':raise ValueError('Linked evidence clock')
 clock=json.loads((root/'clock.json').read_text());elapsed=time.monotonic()-clock['startedMonotonic']
 if clock['owner']!=plan['id'] or clock['maximumSeconds']!=10800 or clock['bootId']!=pathlib.Path('/proc/sys/kernel/random/boot_id').read_text().strip() or not 0<=elapsed<10600 or json.loads((evidence/'clock.json').read_text())!=clock:raise ValueError('Original runtime deadline or clock mismatch')
 stopped=json.loads((root/'deadline-stop.json').read_text());marker=json.loads((root/'stopping.json').read_text());watch=json.loads((root/'watchdog.json').read_text())
 if stopped.get('owner')!=plan['id'] or stopped.get('failures')!=[] or stopped.get('retained') is not True or stopped.get('reason')!='MONITOR_FAILURE:ValueError' or marker!={'owner':plan['id'],'reason':'MONITOR_FAILURE:ValueError'}:raise ValueError('Startup failure was not settled')
 if watch.get('owner')!=plan['id'] or type(watch.get('pid'))!=int or watch['pid']<2 or pathlib.Path('/proc',str(watch['pid'])).exists():raise ValueError('Original watchdog not absent')
 write_new(root/'resume-empty-consumed.json',{'owner':plan['id'],'originalStartedMonotonic':clock['startedMonotonic'],'resumedMonotonic':time.monotonic(),'maximumSeconds':10800},True)
 archive=root/'startup-failure01';archive.mkdir(mode=0o700,exist_ok=False)
 for name in ['stopping.json','deadline-stop.json','watchdog.json','watchdog.log']:(root/name).rename(archive/name)

def create(plan,docker,resume=False):
 root=owned_root(plan);evidence=pathlib.Path(plan['evidence'])
 if not resume:
  root.mkdir(mode=0o700,exist_ok=False)
  if not evidence.is_relative_to(W) or evidence.exists():raise ValueError('Evidence output already exists or foreign')
  evidence.mkdir(mode=0o700)
  clock={'owner':plan['id'],'startedMonotonic':time.monotonic(),'maximumSeconds':10800,'startedUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'bootId':pathlib.Path('/proc/sys/kernel/random/boot_id').read_text().strip()}
  write_new(root/'clock.json',clock,True);write_new(evidence/'clock.json',clock)
 with (root/'watchdog.log').open('x') as log:
  child=subprocess.Popen([sys.executable,str(pathlib.Path(__file__).resolve()),'watchdog'],stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
 write_new(root/'watchdog.json',{'pid':child.pid,'owner':plan['id']},True)
 for kind in ['mysql','s3']:
  digests=json.loads(run(docker,['image','inspect','--format','{{json .RepoDigests}}',plan[kind]['image']]))
  if plan[kind]['image'] not in digests:raise ValueError('Pinned image unavailable')
 if resume:passwords=json.loads((root/'credentials.json').read_text())
 else:
  passwords={'mysql':secrets.token_urlsafe(32),'mysqlRoot':secrets.token_urlsafe(32),'s3Access':secrets.token_hex(12),'s3Secret':secrets.token_hex(24)}
  write_new(root/'credentials.json',passwords,True)
 records={}
 for kind in ['mysql','s3']:
  if (root/'stopping.json').exists():raise ValueError('Owned batch stopping during startup')
  p=plan[kind];base=root/kind;base.mkdir(mode=0o700);(base/'data').mkdir(mode=0o700)
  if kind=='s3':
   (base/'logs').mkdir(mode=0o700)
   for path in [base/'data',base/'logs']:
    if path.resolve().parent!=base.resolve():raise ValueError('Foreign permission target')
    path.chmod(0o750);subprocess.run(['sudo','-n','chown','10001:10001',str(path)],check=True,timeout=5)
  env=base/'server.env';fd=os.open(env,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
  schema=plan['mysql']['schemaPrefix']+'_local'
  lines={'MYSQL_ROOT_PASSWORD':passwords['mysqlRoot'],'MYSQL_DATABASE':schema,'MYSQL_USER':'ama_bl5','MYSQL_PASSWORD':passwords['mysql']} if kind=='mysql' else {'RUSTFS_ACCESS_KEY':passwords['s3Access'],'RUSTFS_SECRET_KEY':passwords['s3Secret'],'RUSTFS_CONSOLE_ENABLE':'false','RUSTFS_VOLUMES':'/data','RUSTFS_OBS_LOG_DIRECTORY':'/logs','RUST_LOG':'warn'}
  with os.fdopen(fd,'w') as f:f.write(''.join(k+'='+v+'\n' for k,v in lines.items()))
  args=['run','-d','--pull','never','--name',p['name'],'--platform','linux/amd64','--label','ama.bl5.owner='+plan['id'],'--memory',str(p['memoryMiB'])+'m','--memory-swap',str(p['memoryMiB'])+'m','--cpus',str(p['cpus']),'--pids-limit',str(p['pids']),'--log-opt','max-size=10m','--log-opt','max-file=2','--publish','127.0.0.1::'+('3306' if kind=='mysql' else '9000'),'--env-file',str(env),'--mount','type=bind,source='+str(base/'data')+',target='+('/var/lib/mysql' if kind=='mysql' else '/data')]
  if kind=='s3':args+=['--mount','type=bind,source='+str(base/'logs')+',target=/logs']
  args+=[p['image']]
  if kind=='mysql':args+=['--character-set-server=utf8mb4','--collation-server=utf8mb4_0900_ai_ci','--default-time-zone=+00:00','--innodb-use-native-aio=OFF']
  write_new(base/'create-intent.json',{'owner':plan['id'],'name':p['name'],'kind':kind},True)
  cid=run(docker,args)
  if not re.fullmatch('[0-9a-f]{64}',cid):raise ValueError('Unexpected container ID')
  write_new(base/'created.json',{'id':cid,'owner':plan['id'],'kind':kind},True)
  if (root/'stopping.json').exists():raise ValueError('Owned batch stopping after container creation')
  record=inspect(docker,plan,kind,cid);records[kind]=record;write_new(evidence/(kind+'-selected.json'),record)
 write_new(root/'resources.json',records,True)
 print(json.dumps({'owner':plan['id'],'status':'CREATED_NOT_SCHEMA_OR_BUSINESS_QUALIFIED','deadlineSeconds':10800}))

def registered_processes(plan):
 root=owned_root(plan);registry=root/'processes'
 if not registry.exists():return []
 if registry.is_symlink() or registry.resolve()!=registry or not registry.is_dir():raise ValueError('Foreign process registry')
 entries=sorted(registry.glob('*.json'))
 if len(entries)>16:raise ValueError('Process registry bound exceeded')
 records=[]
 for entry in entries:
  if entry.is_symlink() or entry.stat().st_size>4096:raise ValueError('Foreign process entry')
  v=json.loads(entry.read_text());pid=v.get('pid');ticks=v.get('startTicks')
  if v.get('owner')!=plan['id'] or type(pid)!=int or pid<2 or pid==os.getpid() or not isinstance(ticks,str) or not re.fullmatch('[0-9]+',ticks):raise ValueError('Foreign process marker')
  records.append(v)
 return records

def process_matches(v):
 try:return (pathlib.Path('/proc')/str(v['pid'])/'stat').read_text().rsplit(')',1)[1].split()[19]==v['startTicks']
 except FileNotFoundError:return False

def drain_processes(plan):
 records=registered_processes(plan);failures=[]
 for v in records:
  try:
   if process_matches(v):os.kill(v['pid'],signal.SIGTERM)
  except ProcessLookupError:pass
  except Exception as failure:failures.append(type(failure).__name__)
 if records:time.sleep(5)
 for v in records:
  try:
   if process_matches(v):os.kill(v['pid'],signal.SIGKILL)
  except ProcessLookupError:pass
  except Exception as failure:failures.append(type(failure).__name__)
 if failures:raise RuntimeError('Owned process draining failed: '+','.join(failures))

def stop(plan,docker):
 root=owned_root(plan);failures=[]
 if root.exists() and not (root/'stopping.json').exists():write_new(root/'stopping.json',{'owner':plan['id'],'reason':'MANUAL_OR_STARTUP_FAILURE'},True)
 try:drain_processes(plan)
 except Exception as failure:failures.append('processes:'+type(failure).__name__)
 for kind in ['s3','mysql']:
  try:
   marker=root/kind/'created.json'
   if not marker.exists():
    intent=root/kind/'create-intent.json'
    if not intent.exists():continue
    expected=json.loads(intent.read_text())
    if expected!={'owner':plan['id'],'name':plan[kind]['name'],'kind':kind}:raise ValueError('Foreign creation intent')
    recovered=json.loads(run(docker,['inspect','--format','{{json .Id}}',plan[kind]['name']],timeout=5))
    recorded={'owner':plan['id'],'kind':kind,'id':recovered}
   else:
    if marker.is_symlink() or marker.stat().st_size>4096:raise ValueError('Foreign marker path')
    recorded=json.loads(marker.read_text())
   if recorded.get('owner')!=plan['id'] or recorded.get('kind')!=kind or not re.fullmatch('[0-9a-f]{64}',recorded.get('id','')):raise ValueError('Foreign marker')
   current=inspect(docker,plan,kind,recorded['id'])
   if current['running']:run(docker,['stop','--time','20',recorded['id']],timeout=25)
  except Exception as failure:failures.append(kind+':'+type(failure).__name__)
 if failures:raise RuntimeError('Owned resource stop needs follow-up: '+','.join(failures))
 print('Owned processes and containers stopped; all data retained.')

def watchdog(plan,docker):
 root=owned_root(plan);clock=json.loads((root/'clock.json').read_text());deadline=clock['startedMonotonic']+10800
 if clock['owner']!=plan['id'] or clock['maximumSeconds']!=10800 or clock['bootId']!=pathlib.Path('/proc/sys/kernel/random/boot_id').read_text().strip():raise ValueError('Clock ownership mismatch')
 # Leave ninety seconds for draining, including a failed accounting attempt.
 stop_reason='DEADLINE'
 while time.monotonic()<deadline-90:
  if (root/'settled-stop.json').exists():return
  if (root/'stopping.json').exists():stop_reason=json.loads((root/'stopping.json').read_text()).get('reason','STOP_REQUEST');break
  try:
   snapshot=bl5_monitor.owned_usage(plan,docker,sys.modules[__name__]);stop_reason=bl5_monitor.reason(snapshot)
   if stop_reason:break
   bl5_monitor.publish(root,snapshot)
  except Exception as failure:
   stop_reason='MONITOR_FAILURE:'+type(failure).__name__;break
  time.sleep(min(1,max(0,deadline-90-time.monotonic())))
 if not (root/'stopping.json').exists():write_new(root/'stopping.json',{'owner':plan['id'],'reason':stop_reason or 'DEADLINE'},True)
 failures=[]
 try:stop(plan,docker)
 except Exception as failure:failures.append(str(failure))
 write_new(root/'deadline-stop.json',{'owner':plan['id'],'stoppedMonotonic':time.monotonic(),'failures':failures,'retained':True,'reason':stop_reason or 'DEADLINE'},True)
 if failures:raise RuntimeError('Some owned resources require verified stop follow-up')

def main():
 parser=argparse.ArgumentParser();parser.add_argument('action',choices=['create','resume-empty-startup','stop','watchdog']);args=parser.parse_args()
 plan=json.loads(PLAN.read_text());docker=shutil.which('docker')
 if not docker:raise RuntimeError('Linux docker client unavailable; no fallback resource selection')
 if args.action in ['create','resume-empty-startup']:
  gate(plan,json.loads(AUTH.read_text()),json.loads(REVIEW.read_text()),sha(PLAN))
  if args.action=='resume-empty-startup':resume_empty_startup(plan)
  try:create(plan,docker,resume=args.action=='resume-empty-startup')
  except BaseException:
   if owned_root(plan).exists():
    try:stop(plan,docker)
    except Exception:pass # Watchdog remains active; caller must inspect bounded failure evidence.
   raise
 elif args.action=='watchdog':watchdog(plan,docker)
 else:
  owned_root(plan);stop(plan,docker)
  if not (owned_root(plan)/'settled-stop.json').exists():write_new(owned_root(plan)/'settled-stop.json',{'owner':plan['id'],'monotonic':time.monotonic()},True)
if __name__=='__main__':main()
