"""Exact Task5 root-ACK resource plan. Exclusive owned writes; never prints credentials."""
from pathlib import Path
import os,subprocess,json,secrets,datetime
R=Path('/home/tanren/ama/.worktrees/phase-b-completion')
P=R/'.tools/task05-5237a2db-e8d5-4cbe-b357-b35df5cfcba5'
E=R/'artifacts/phase-b/b-remaining/task-05/run-5237a2db-e8d5-4cbe-b357-b35df5cfcba5'
NAME='ama-task05-s3-5237a2db-e8d5-4cbe-b357-b35df5cfcba5';OWNER='ama-task05-5237a2db-e8d5-4cbe-b357-b35df5cfcba5'
IMAGE='rustfs/rustfs@sha256:128220dc9382bac81940a1a9616ee8c274a3f3b8f647eedbeb50df5bb8b49922'
def guard(p):
 assert not p.exists() and not p.is_symlink() and p.resolve().is_relative_to(R)
 for a in p.parents:
  assert not a.is_symlink()
  if a==R:break
assert R.resolve(strict=True)==R
for p in (P,E):guard(p)
q=subprocess.run(['docker','container','inspect','--format','{{.Id}}',NAME],capture_output=True,text=True)
assert q.returncode==1 and not q.stdout.strip() and q.stderr.strip()=='Error response from daemon: No such container: '+NAME
fmt='{"id":{{json .Id}},"digests":{{json .RepoDigests}},"os":{{json .Os}},"arch":{{json .Architecture}}}'
i=json.loads(subprocess.run(['docker','image','inspect','--format',fmt,IMAGE],capture_output=True,text=True,check=True).stdout)
assert i['id']==IMAGE.split('@')[1] and IMAGE in i['digests'] and (i['os'],i['arch'])==('linux','amd64')
os.umask(0o077)
for p in (P,E):guard(p);p.mkdir(mode=0o700)
def mkdir(p):guard(p);p.mkdir(mode=0o700)
def write(p,text):
 guard(p)
 with os.fdopen(os.open(p,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600),'w') as f:f.write(text)
for name in ('s3','pki','native','runtime'):mkdir(P/name)
for name in ('data','logs'):
 target=P/'s3'/name;guard(target)
 subprocess.run(['sudo','-n','install','-d','-o','10001','-g','10001','-m','750',str(target)],check=True)
access=secrets.token_hex(12);secret=secrets.token_hex(24)
write(P/'s3/server.env','RUSTFS_ACCESS_KEY='+access+'\nRUSTFS_SECRET_KEY='+secret+'\nRUSTFS_CONSOLE_ENABLE=false\nRUSTFS_VOLUMES=/data\nRUSTFS_OBS_LOG_DIRECTORY=/logs\nRUST_LOG=warn\n')
cmd=['docker','run','-d','--name',NAME,'--platform','linux/amd64','--user','10001:10001','--cpus','2','--memory','2g','--memory-swap','2g','--pids-limit','256','--log-opt','max-size=10m','--log-opt','max-file=2','--label','ama.owner='+OWNER,'-p','127.0.0.1::9000','--env-file',str(P/'s3/server.env'),'--mount','type=bind,source='+str(P/'s3/data')+',target=/data','--mount','type=bind,source='+str(P/'s3/logs')+',target=/logs',IMAGE]
res=subprocess.run(cmd,text=True,capture_output=True)
write(E/'docker-create.json',json.dumps({'command':cmd,'exit':res.returncode,'stdout':res.stdout,'stderr':res.stderr},indent=2)+'\n')
assert res.returncode==0
cid=res.stdout.strip();assert len(cid)==64 and all(c in '0123456789abcdef' for c in cid)
write(P/'s3/fixture.json',json.dumps({'container':cid,'name':NAME,'owner':OWNER})+'\n')
fmt='{"id":{{json .Id}},"name":{{json .Name}},"image":{{json .Config.Image}},"owner":{{json (index .Config.Labels "ama.owner")}},"memory":{{json .HostConfig.Memory}},"memorySwap":{{json .HostConfig.MemorySwap}},"nanoCpus":{{json .HostConfig.NanoCpus}},"pids":{{json .HostConfig.PidsLimit}},"ports":{{json .NetworkSettings.Ports}},"mounts":{{json .Mounts}}}'
m=json.loads(subprocess.run(['docker','container','inspect','--format',fmt,cid],capture_output=True,text=True,check=True).stdout)
assert m['id']==cid and m['name']=='/'+NAME and m['owner']==OWNER and m['image']==IMAGE
assert m['memory']==m['memorySwap']==2147483648 and m['nanoCpus']==2000000000 and m['pids']==256
assert {(x['Source'],x['Destination']) for x in m['mounts']}=={(str(P/'s3/data'),'/data'),(str(P/'s3/logs'),'/logs')}
ports=m['ports']['9000/tcp'];assert len(ports)==1 and ports[0]['HostIp']=='127.0.0.1' and ports[0]['HostPort'].isdigit() and not m['ports'].get('9001/tcp')
write(E/'resource-receipt.json',json.dumps({'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'image':i,'container':m},indent=2)+'\n')
write(P/'client.env','export AMA_S3_ACCESS='+access+'\nexport AMA_S3_SECRET='+secret+'\nexport AMA_S3_ENDPOINT=http://127.0.0.1:'+ports[0]['HostPort']+'\nexport AMA_TASK05_ROOT='+str(P)+'\n')
pki=P/'pki';write(pki/'keystore-password',secrets.token_hex(24))
commands=[]
def run(argv):
 n=len(commands);log=pki/('generation-'+str(n)+'.log');guard(log)
 with log.open('x') as f:result=subprocess.run(argv,cwd=pki,stdout=f,stderr=subprocess.STDOUT,timeout=20)
 commands.append({'command':argv,'exit':result.returncode});write(E/('pki-command-'+str(n)+'.json'),json.dumps(commands[-1],indent=2)+'\n');assert result.returncode==0
run(['openssl','req','-x509','-newkey','rsa:2048','-noenc','-keyout','ca.key','-out','ca.crt','-days','2','-subj','/CN=AMA task05 local CA'])
for name in ('server','writer','reader','outsider'):
 run(['openssl','req','-newkey','rsa:2048','-noenc','-keyout',name+'.key','-out',name+'.csr','-subj','/CN='+name])
 write(pki/(name+'.ext'),'subjectAltName='+('DNS:localhost' if name=='server' else 'URI:spiffe://ama/'+name)+'\nextendedKeyUsage='+('serverAuth' if name=='server' else 'clientAuth')+'\nbasicConstraints=CA:FALSE\nkeyUsage=digitalSignature,keyEncipherment\n')
 run(['openssl','x509','-req','-in',name+'.csr','-CA','ca.crt','-CAkey','ca.key','-CAcreateserial','-out',name+'.crt','-days','2','-extfile',name+'.ext'])
 run(['openssl','pkcs12','-export','-inkey',name+'.key','-in',name+'.crt','-certfile','ca.crt','-out',name+'.p12','-passout','file:keystore-password'])
run(['/home/tanren/ama/.worktrees/phase-a/.tools/zulu21.52.203-ca-jdk21.0.12.1-linux_x64/bin/keytool','-importcert','-noprompt','-alias','ca','-file','ca.crt','-keystore','trust.p12','-storetype','PKCS12','-storepass:file','keystore-password'])
print('Created exact owned S3 '+cid+' loopback '+ports[0]['HostPort']+'; private fresh PKI complete')
