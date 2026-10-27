"""Owned aggregate accounting and admission lease. No discovery of unrelated resources."""
import json,os,pathlib,re,subprocess,time,uuid
MIB=1024**2
# Reserve 2GiB memory, 3GiB owned disk and 1GiB evidence for admitted work/drain.
MEMORY_STOP=4096*MIB
DISK_STOP=5*1024**3
EVIDENCE_STOP=1024**3

def admitted(root):
 if (root/'stopping.json').exists():raise ValueError('B-L5 workload stopping')
 monitor=json.loads((root/'monitor.json').read_text());now=time.time()*1000
 if monitor.get('owner')!=root.name or monitor.get('status')!='ADMIT' or not monitor['observedUtcMillis']<=now<=monitor['expiresUtcMillis']:raise ValueError('Fresh aggregate admission required')

def reason(snapshot):
 for key,limit in [('chargedMemoryBytes',MEMORY_STOP),('ownedDiskBytes',DISK_STOP),('evidenceBytes',EVIDENCE_STOP)]:
  value=snapshot.get(key)
  if type(value)!=int or value<0:return 'UNAVAILABLE_'+key
  if value>=limit:return 'LIMIT_'+key
 return None

def proc(pid):
 try:
  raw=pathlib.Path('/proc',str(pid),'stat').read_text().rsplit(')',1)[1].split()
  if raw[0]=='Z':return None
  rss=int(pathlib.Path('/proc',str(pid),'statm').read_text().split()[1])*os.sysconf('SC_PAGE_SIZE')
  children=pathlib.Path('/proc',str(pid),'task',str(pid),'children').read_text().split()
  return {'ticks':raw[19],'rss':rss,'children':[int(c) for c in children]}
 except FileNotFoundError:return None

def parse_memory(value):
 part=value.split('/')[0].strip();match=re.fullmatch(r'([0-9]+(?:\.[0-9]+)?)\s*(B|KiB|MiB|GiB)',part)
 if not match:raise ValueError('Unavailable container memory units')
 return int(float(match[1])*{'B':1,'KiB':1024,'MiB':MIB,'GiB':1024**3}[match[2]])+MIB

def owned_usage(plan,docker,resources):
 root=resources.owned_root(plan);records=resources.registered_processes(plan);seen={};pending=[]
 for record in records:
  actual=proc(record['pid'])
  if actual and actual['ticks']==record['startTicks']:pending.append((record['pid'],actual))
 own=proc(os.getpid())
 if own:pending.append((os.getpid(),own))
 while pending:
  pid,actual=pending.pop()
  if pid in seen:continue
  if len(seen)>=64:raise ValueError('Owned descendant process bound')
  seen[pid]=actual
  for child in actual['children']:
   value=proc(child)
   if value:pending.append((child,value))
 containers=0
 for kind in ['mysql','s3']:
  marker=root/kind/'created.json'
  if not marker.exists():continue
  record=json.loads(marker.read_text());selected=resources.inspect(docker,plan,kind,record['id'])
  if selected['running']:
   value=resources.run(docker,['stats','--no-stream','--format','{{.MemUsage}}',record['id']],timeout=5)
   containers+=parse_memory(value)
 evidence=pathlib.Path(plan['evidence'])
 if root.resolve()!=root or evidence.resolve()!=evidence or not evidence.is_relative_to(resources.W):raise ValueError('Foreign aggregate paths')
 measured=subprocess.run(['sudo','-n','du','-sb','--one-file-system','--',str(root),str(evidence)],capture_output=True,text=True,timeout=5)
 if measured.returncode:raise ValueError('Owned disk measurement unavailable')
 sizes={}
 for line in measured.stdout.splitlines():
  count,name=line.split('\t',1);sizes[name]=int(count)
 if set(sizes)!={str(root),str(evidence)}:raise ValueError('Foreign disk measurement')
 host=sum(v['rss'] for v in seen.values())
 # Charge full approved container reservations even while they are only starting.
 reserve=sum(plan[k]['memoryMiB']*MIB for k in ['mysql','s3'])
 return {'hostRssBytes':host,'containerMemoryBytes':containers,'chargedMemoryBytes':host+max(reserve,containers),'ownedDiskBytes':sizes[str(root)]+sizes[str(evidence)],'evidenceBytes':sizes[str(evidence)],'ownedProcessCount':len(seen)}

def publish(root,snapshot):
 now=int(time.time()*1000);value={'owner':root.name,'status':'ADMIT','observedUtcMillis':now,'expiresUtcMillis':now+10000,**snapshot}
 temp=root/('monitor-'+str(uuid.uuid4())+'.tmp');fd=os.open(temp,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
 with os.fdopen(fd,'w') as f:json.dump(value,f)
 os.replace(temp,root/'monitor.json')
 return value
