import os,pathlib,subprocess,json,shlex,hashlib,sys,shutil,time,stat
root=pathlib.Path(__file__).resolve().parents[4]
e=root/'artifacts/phase-b/b-remaining/task-03'
name=sys.argv[1]; args=sys.argv[2:]
expected='eefdd9a984ee00fe6ca886168b29a6ce4f3b405467b8d3204240d4e1a8dc7502'
info=json.loads(subprocess.check_output(['docker','inspect','ama-test-b01-ad9f1af29eff']))[0]
assert info['Id']==expected and info['State']['Running'] and info['NetworkSettings']['Ports']['3306/tcp']==[{'HostIp':'127.0.0.1','HostPort':'58062'}]
env=os.environ.copy()
for line in (root/'.tools/phase-b-remaining/env.sh').read_text().splitlines():
 key,value=line.removeprefix('export ').split('=',1);env[key]=shlex.split(value)[0]
assert env['AMA_TEST_DATABASE_URL']=='jdbc:mysql://127.0.0.1:58062/ama_b01_fixture'
jdk='/home/tanren/ama/.worktrees/phase-a/.tools/zulu21.52.203-ca-jdk21.0.12.1-linux_x64'
env['AMA_STORAGE_CONTRACT']='local'
env['JAVA_HOME']=jdk;env['PATH']=jdk+'/bin:'+env['PATH'];env['MAVEN_OPTS']='-Xmx512m -XX:ActiveProcessorCount=2'
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
    if any('ArchiveRestartProbe' in a or 'RegistryApiProcess' in a or 'ama-file-service-0.1.0-SNAPSHOT-exec.jar' in a for a in argv):children[pid]={'pid':pid,'argv':argv,'JAVA_TOOL_OPTIONS':env.get('JAVA_TOOL_OPTIONS'),'owner_uid':(pathlib.Path('/proc')/str(pid)).stat().st_uid}
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
custody=root/'backend/integration-tests/target/task03-custody'
if custody.exists():
 out=e/(name+'-custody');out.mkdir(exist_ok=True)
 for path in custody.iterdir():
  if path.is_file() and path.stat().st_mtime >= (e/(name+'.sources.json')).stat().st_mtime:shutil.copyfile(path,out/path.name)
for k in ['AMA_TEST_DATABASE_PASSWORD']:log=log.replace(env[k],'[REDACTED]')
(e/(name+'.log')).write_text(log)
(e/(name+'.command.json')).write_text(json.dumps({'argv':args,'cwd':str(root),'exit_code':r.returncode,'JAVA_HOME':jdk,'private_env_path':'.tools/phase-b-remaining/env.sh','fixture_id':expected,'AMA_STORAGE_CONTRACT':env['AMA_STORAGE_CONTRACT'],'JAVA_TOOL_OPTIONS':env.get('JAVA_TOOL_OPTIONS'),'AMA_B06_RESTART_EVIDENCE':env.get('AMA_B06_RESTART_EVIDENCE')},indent=2)+'\n')
dest=e/(name+'-xml');dest.mkdir(exist_ok=True)
for p in (root/'backend').glob('*/target/*-reports/TEST-*.xml'):
 if p.stat().st_mtime >= (e/(name+'.sources.json')).stat().st_mtime:shutil.copyfile(p,dest/(p.parents[2].name+'-'+p.name))
print(log[-7000:]);sys.exit(r.returncode)
