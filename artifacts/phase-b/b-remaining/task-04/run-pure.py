import os,pathlib,subprocess,json,shlex,hashlib,sys,shutil,time,stat
root=pathlib.Path(__file__).resolve().parents[4]
e=root/'artifacts/phase-b/b-remaining/task-04'
name=sys.argv[1]; args=sys.argv[2:]
assert name in ('all-unit-final-resumed','openapi-final-lint','openapi-final-bundle','openapi-size-final-lint','openapi-size-final-bundle'), 'Pure commands only'
assert not (e/(name+'.command.json')).exists() and not (e/(name+'.sources.json')).exists(), 'Evidence label must be new'
expected=None
info={'qualification':'PURE UNIT/OPENAPI; database and storage are not used; runtime fixture guard intentionally not invoked after host interruption'}
env=os.environ.copy()
jdk='/home/tanren/ama/.worktrees/phase-a/.tools/zulu21.52.203-ca-jdk21.0.12.1-linux_x64'
env['AMA_STORAGE_CONTRACT']='local'
env['JAVA_HOME']=jdk;env['PATH']=jdk+'/bin:'+env['PATH'];env['MAVEN_OPTS']='-Xmx512m -XX:ActiveProcessorCount=2';env['JAVA_TOOL_OPTIONS']='-Xmx512m -XX:ActiveProcessorCount=2'
if name.startswith('config-restart-final'):
 env['JAVA_TOOL_OPTIONS']='-Xmx64m -XX:ActiveProcessorCount=2'
 import uuid
 restart=(root/'backend/integration-tests/target'/('task04-config-restart-'+str(uuid.uuid4()))).resolve()
 assert restart.parent==(root/'backend/integration-tests/target').resolve() and not restart.exists()
 restart.mkdir();env['AMA_B06_RESTART_EVIDENCE']=str(restart)
(e/(name+'.guard.json')).write_text(json.dumps(info,indent=2)+'\n')
files=sorted(p for p in (root/'backend').rglob('*') if p.is_file() and '/src/' in str(p))
(e/(name+'.sources.json')).write_text(json.dumps({str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in files},indent=2)+'\n')
parents=[root/'backend/integration-tests/target'/n for n in ['local-fixtures','registry-fixtures','registry-restart']]
before={p:{str(x) for x in p.iterdir()} if p.exists() else set() for p in parents}
children={}
proc=subprocess.Popen(args,cwd=root,env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
while True:
 try:
  stdout,stderr=proc.communicate(timeout=0.5);break
 except subprocess.TimeoutExpired:
  ppids={}
  for entry in pathlib.Path('/proc').iterdir():
   if not entry.name.isdigit():continue
   try:
    status=(entry/'status').read_text();ppid=int(next(line.split()[1] for line in status.splitlines() if line.startswith('PPid:')));ppids[int(entry.name)]=ppid
   except (OSError,StopIteration,ValueError):pass
  descendants={proc.pid}
  while True:
   extra={pid for pid,parent in ppids.items() if parent in descendants};old=len(descendants);descendants|=extra
   if len(descendants)==old:break
  for pid in descendants:
   if pid in children:continue
   try:
    argv=(pathlib.Path('/proc')/str(pid)/'cmdline').read_bytes().decode().strip('\0').split('\0')
    if any('surefirebooter' in a or 'maven.multiModule' in a or 'ArchiveRestartProbe' in a or 'RegistryApiProcess' in a or 'ama-file-service-0.1.0-SNAPSHOT-exec.jar' in a for a in argv):children[pid]={'pid':pid,'argv':argv,'JAVA_TOOL_OPTIONS':env.get('JAVA_TOOL_OPTIONS'),'owner_uid':(pathlib.Path('/proc')/str(pid)).stat().st_uid}
   except (OSError,UnicodeError):pass
r=subprocess.CompletedProcess(args,proc.returncode,stdout,stderr)
log=r.stdout+r.stderr
(e/(name+'.children.json')).write_text(json.dumps(list(children.values()),indent=2)+'\n')
created=[]
for parent in parents:
 if parent.exists():
  for child in parent.iterdir():
   if str(child) not in before[parent]:created.append({'path':str(child),'uid':child.stat().st_uid,'mode':oct(stat.S_IMODE(child.stat().st_mode))})
(e/(name+'.fixture-paths.json')).write_text(json.dumps(created,indent=2)+'\n')
custody=root/'backend/integration-tests/target/task04-custody'
if custody.exists():
 out=e/(name+'-custody');out.mkdir(exist_ok=True)
 for path in custody.iterdir():
  if path.is_file() and path.stat().st_mtime >= (e/(name+'.sources.json')).stat().st_mtime:shutil.copyfile(path,out/path.name)
for k in ['AMA_TEST_DATABASE_PASSWORD']:
 if env.get(k):log=log.replace(env[k],'[REDACTED]')
(e/(name+'.log')).write_text(log)
(e/(name+'.command.json')).write_text(json.dumps({'argv':args,'cwd':str(root),'exit_code':r.returncode,'JAVA_HOME':jdk,'private_env_path':None,'fixture_id':expected,'AMA_STORAGE_CONTRACT':env['AMA_STORAGE_CONTRACT'],'JAVA_TOOL_OPTIONS':env.get('JAVA_TOOL_OPTIONS'),'AMA_B06_RESTART_EVIDENCE':env.get('AMA_B06_RESTART_EVIDENCE')},indent=2)+'\n')
dest=e/(name+'-xml');dest.mkdir(exist_ok=True)
for p in (root/'backend').glob('*/target/*-reports/TEST-*.xml'):
 if p.stat().st_mtime >= (e/(name+'.sources.json')).stat().st_mtime:shutil.copyfile(p,dest/(p.parents[2].name+'-'+p.name))
print(log[-7000:]);sys.exit(r.returncode)

