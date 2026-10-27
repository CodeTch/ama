"""Start one reviewed B-L5 mode within the original batch clock. No infrastructure creation."""
import argparse,hashlib,json,os,pathlib,socket,subprocess,time
import bl5_resources as resources
import bl5_monitor
JAVA=pathlib.Path('/home/tanren/ama/.worktrees/phase-a/.tools/zulu21.52.203-ca-jdk21.0.12.1-linux_x64')
BUILD=resources.W/'bl5-harness-build12'
ASSETS=resources.W/'bl4-fixes-build01/dist'

def process_record(root,label,process):
 ticks=pathlib.Path('/proc',str(process.pid),'stat').read_text().rsplit(')',1)[1].split()[19]
 registry=root/'processes';registry.mkdir(mode=0o700,exist_ok=True)
 resources.write_new(registry/(label+'.json'),{'owner':root.name,'pid':process.pid,'startTicks':ticks},True)

def wait_ready(path,process,deadline):
 while not path.exists():
  if process.poll() is not None or time.monotonic()>=deadline:raise RuntimeError('Owned service did not reach readiness')
  time.sleep(.1)
 return json.loads(path.read_text())

def launch(plan,mode):
 root=resources.owned_root(plan);bl5_monitor.admitted(root);clock=json.loads((root/'clock.json').read_text())
 if clock['owner']!=plan['id'] or clock['maximumSeconds']!=10800 or clock['bootId']!=pathlib.Path('/proc/sys/kernel/random/boot_id').read_text().strip():raise ValueError('Owned clock mismatch')
 remaining=int(clock['startedMonotonic']+10600-time.monotonic())
 if remaining<60:raise ValueError('Insufficient batch runtime remaining')
 built=json.loads((BUILD/'result.json').read_text())
 if built['exit']!=0 or not built['stable']:raise ValueError('Compiled harness not verified')
 resources.verify_execution({'artifacts':built['runtimeArtifacts'],'artifactDirectories':built['artifactDirectories']})
 for name,digest in built['source'].items():
  if resources.sha(resources.ROOT/name)!=digest:raise ValueError('Compiled source drift: '+name)
 case=root/('service-s3-r2' if mode=='s3' else mode);case.mkdir(mode=0o700,exist_ok=False);evidence=pathlib.Path(plan['evidence'])/('s3-r2' if mode=='s3' else mode);evidence.mkdir(mode=0o700,exist_ok=False)
 records=json.loads((root/'resources.json').read_text());credentials=json.loads((root/'credentials.json').read_text())
 for kind in ['mysql','s3']:resources.inspect('docker',plan,kind,records[kind]['id'])
 with socket.socket() as chosen:chosen.bind(('127.0.0.1',0));port=chosen.getsockname()[1]
 config={'mode':mode,'browserOrigin':'https://127.0.0.1:'+str(port),'schema':plan['mysql']['schemaPrefix']+('_local2' if mode=='local' else '_s3r2'),'mysqlPort':int(records['mysql']['ports']['3306/tcp'][0]['HostPort']),'mysqlPassword':credentials['mysql'],'s3Endpoint':'http://127.0.0.1:'+records['s3']['ports']['9000/tcp'][0]['HostPort'],'s3Access':credentials['s3Access'],'s3Secret':credentials['s3Secret'],'categorySha256':resources.sha(root/'categories.bin'),'remainingSeconds':remaining}
 resources.write_new(case/'harness.json',config,True)
 env={'HOME':'/home/tanren','USER':'tanren','LANG':'C.UTF-8','PATH':str(JAVA/'bin')+':/usr/bin:/bin','JAVA_HOME':str(JAVA),'JAVA_TOOL_OPTIONS':'-XX:MaxMetaspaceSize=128m -XX:MaxDirectMemorySize=64m -XX:ReservedCodeCacheSize=64m -Xss512k','AMA_BL5_ROOT':str(root),'AMA_TASK09_RUN_ROOT':str(case),'AMA_TASK09_PKI_ROOT':str(root/'pki'),'AMA_TASK09_EVIDENCE_WINDOW':str(evidence),'AMA_TASK11_EVIDENCE_WINDOW':str(evidence),'AMA_TASK11_REPOSITORY_ROOT':str(resources.ROOT)}
 command=[str(JAVA/'bin/java'),'-Xmx512m','-XX:ActiveProcessorCount=2','-Djdk.httpclient.allowRestrictedHeaders=host','-cp',(BUILD/'classpath.txt').read_text(),'ama.storage.Bl5LiveHarness',str(case/'harness.json')]
 with (case/'harness.log').open('x') as log:process=subprocess.Popen(command,cwd=BUILD/'project',env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
 # S3 main registers itself before startup; avoid a second record for the same PID.
 if mode=='local':process_record(root,mode+'-launch',process)
 front=None
 try:
  ready=wait_ready(case/'ready.json',process,time.monotonic()+120)
  proxy={'ownedRoot':str(root),'certificate':str(root/'pki/server.pem'),'privateKey':str(root/'pki/server.key'),'readyFile':str(case/'https-ready.json'),'identitiesFile':str(case/'ready.json'),'assets':str(ASSETS),'upstreamPort':int(ready['apiPort']),'port':port}
  resources.write_new(case/'https.json',proxy,True)
  with (case/'https.log').open('x') as log:front=subprocess.Popen(['/usr/bin/python3',str(resources.ROOT/'tools/bl5_https_proxy.py'),'--config',str(case/'https.json')],stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
  process_record(root,('s3-r2' if mode=='s3' else mode)+'-https',front);wait_ready(case/'https-ready.json',front,time.monotonic()+10)
  resources.write_new(evidence/'entry.json',{'mode':mode,'origin':config['browserOrigin'],'bootstrapMeeting':ready['bootstrapMeeting'],'workerSameJvm':True,'knowledgeTls':True,'mockResponses':False})
  print(json.dumps({'mode':mode,'origin':config['browserOrigin'],'fixtureLogin':config['browserOrigin']+'/__bl5/login/owner','remainingSeconds':remaining}))
 except BaseException:
  if front is not None and front.poll() is None:
   front.terminate()
   try:front.wait(timeout=5)
   except subprocess.TimeoutExpired:front.kill();front.wait(timeout=5)
  if process.poll() is None:
   process.terminate()
   try:process.wait(timeout=15)
   except subprocess.TimeoutExpired:process.kill();process.wait(timeout=5)
  raise

if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('mode',choices=['local','s3']);args=p.parse_args();plan=json.loads(resources.PLAN.read_text());resources.gate(plan,json.loads(resources.AUTH.read_text()),json.loads(resources.REVIEW.read_text()),resources.sha(resources.PLAN));launch(plan,args.mode)
