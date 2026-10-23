from pathlib import Path
import json,subprocess,datetime,hashlib,socket,os
r=Path('/home/tanren/ama/.worktrees/phase-b-completion')
w=r/'.superpowers/sdd/2026-10-23-ama-phase-b-remaining';e=r/'artifacts/phase-b/b-remaining/task-05-fix-02'
stamp=lambda:datetime.datetime.now(datetime.timezone.utc).isoformat()
proof=json.loads((w/'task-05-fix-02-root-resource-ack-20261024.json').read_text())
fields={'id':'.Id','name':'.Name','status':'.State.Status','running':'.State.Running','oom':'.State.OOMKilled','image':'.Config.Image','memory':'.HostConfig.Memory','swap':'.HostConfig.MemorySwap','nano_cpus':'.HostConfig.NanoCpus','pids':'.HostConfig.PidsLimit','ports':'.NetworkSettings.Ports','mounts':'.Mounts'}
template='{'+','.join('"'+key+'":{{json '+field+'}}' for key,field in fields.items())+'}'
guard=[]
for original in proof['containers']:
 cid=original['id'];label='ama.remaining.owner' if cid.startswith('eefdd') else 'ama.owner'
 selected_template=template[:-1]+',"owner":{{json (index .Config.Labels "'+label+'")}}}'
 cmd=['docker','inspect','--format',selected_template,cid]
 result=subprocess.run(cmd,capture_output=True,text=True,check=True);actual=json.loads(result.stdout)
 expected={k:original[k] for k in fields};expected['owner']=original['labels'][label]
 changes={k:{'expected':expected[k],'actual':actual[k]} for k in expected if actual[k]!=expected[k]}
 guard.append({'exactCID':cid,'command':cmd,'selected':actual,'changes_from_resume':changes})
 assert not changes, 'selected resource identity changed'
(e/'final-selected-resources.json').write_text(json.dumps({'utc':stamp(),'method':'explicit selected --format, no Config.Env/full inspect','containers':guard},indent=2)+'\n')

owned={}
for p in e.glob('case-*-native-started.json'):
 data=json.loads(p.read_text());owned[data['pid']]={'kind':'native','configuration':data['command'][-1],'start':data.get('startInstant')}
 owned[data['parentPid']]={'kind':'test-jvm','tree':str(r)}
processes=[]
for pid,expected in sorted(owned.items()):
 path=Path('/proc')/str(pid)
 if not path.exists():processes.append({'pid':pid,'kind':expected['kind'],'present':False});continue
 command=(path/'cmdline').read_bytes().replace(b'\0',b' ').decode(errors='replace')
 same=expected.get('configuration','__NO_CONFIGURATION__') in command if expected['kind']=='native' else str(r) in os.readlink(path/'cwd')
 processes.append({'pid':pid,'kind':expected['kind'],'present':True,'matches_owned_command_or_cwd':same})
 assert not same, 'exact owned JVM remains active'
(e/'final-known-processes.json').write_text(json.dumps({'utc':stamp(),'method':'only exact recorded native/test PIDs; no global process enumeration','processes':processes},indent=2)+'\n')

print('final selected-only resources unchanged; all exact recorded native/test JVM PIDs absent')
