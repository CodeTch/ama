"""Final fresh S3 attempt within original four-schema/four-bucket batch caps."""
import json,pathlib,subprocess,time
import bl5_resources as r
import bl5_monitor as monitor
import bl5_prepare as preparation
from bl5_launch import BUILD,JAVA,process_record

def prepare(plan):
 root=r.owned_root(plan);monitor.admitted(root);clock=json.loads((root/'clock.json').read_text())
 if clock['owner']!=plan['id'] or clock['maximumSeconds']!=10800 or clock['bootId']!=pathlib.Path('/proc/sys/kernel/random/boot_id').read_text().strip() or not 0<=time.monotonic()-clock['startedMonotonic']<10400:raise ValueError('Original deadline')
 for name in ['s3-repair-consumed.json','s3-repair-schema.json','knowledge-s3-r2','service-s3-r2']:
  if (root/name).exists():raise ValueError('S3 repair already consumed')
 for mode in ['local','service-s3']:
  stopped=json.loads((root/mode/'harness-stopped.json').read_text())
  if stopped.get('errors')!=[] or stopped.get('dataRetained') is not True:raise ValueError('Previous mode not settled')
 process_records=r.registered_processes(plan)
 if len(process_records)>11:raise ValueError('Insufficient registry slots for bounded S3 retry')
 if any(r.process_matches(v) for v in process_records):raise ValueError('Existing workload still active')
 built=json.loads((BUILD/'result.json').read_text());r.verify_execution({'artifacts':built['runtimeArtifacts'],'artifactDirectories':built['artifactDirectories']})
 if built['exit']!=0 or not built['stable']:raise ValueError('Unverified repair build')
 for name,digest in built['source'].items():
  if r.sha(r.ROOT/name)!=digest:raise ValueError('Source drift')
 records=json.loads((root/'resources.json').read_text())
 for kind in ['mysql','s3']:r.inspect('docker',plan,kind,records[kind]['id'])
 prefix=plan['mysql']['schemaPrefix'];cid=records['mysql']['id']
 if prefix!='ama_bl5_5d9729e1a748':raise ValueError('Exact schema prefix required')
 names=[prefix+s for s in ['_local','_local2','_s3','_s3r2']]
 existing=preparation.sql('docker',cid,names[2],"SELECT schema_name FROM information_schema.schemata WHERE schema_name IN ('"+"','".join(names)+"') ORDER BY schema_name;").splitlines()
 if existing!=names[:3]:raise ValueError('Expected three previous schemas; fourth must be absent')
 r.write_new(root/'s3-repair-consumed.json',{'owner':plan['id'],'originalStartedMonotonic':clock['startedMonotonic'],'retainedSchemas':names[:3],'fourthSchema':names[3],'newBuckets':['bl5-5d9729e1-r2-data','bl5-5d9729e1-r2-control'],'maximumTotalSchemas':4,'maximumTotalBuckets':4},True)
 preparation.sql('docker',cid,names[2],'CREATE DATABASE `'+names[3]+'` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL ON `'+names[3]+'`.* TO ama_bl5;')
 r.write_new(root/'s3-repair-schema.json',{'mysqlPort':int(records['mysql']['ports']['3306/tcp'][0]['HostPort'])},True)
 env={'HOME':'/home/tanren','USER':'tanren','LANG':'C.UTF-8','PATH':str(JAVA/'bin')+':/usr/bin:/bin','JAVA_HOME':str(JAVA),'AMA_BL5_ROOT':str(root),'JAVA_TOOL_OPTIONS':'-XX:MaxMetaspaceSize=128m -XX:MaxDirectMemorySize=64m -XX:ReservedCodeCacheSize=64m -Xss512k'}
 with (root/'s3-repair-schema.log').open('x') as log:
  process=subprocess.Popen([str(JAVA/'bin/java'),'-Xmx256m','-XX:ActiveProcessorCount=2','-cp',(BUILD/'classpath.txt').read_text(),'ama.storage.Bl5S3RepairSchema'],cwd=BUILD/'project',env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
  try:
   process_record(root,'s3-repair-schema',process)
   if process.wait(timeout=120)!=0:raise RuntimeError('Final S3 migration failed; retain all evidence')
  finally:
   if process.poll() is None:
    process.terminate()
    try:process.wait(timeout=5)
    except subprocess.TimeoutExpired:process.kill();process.wait(timeout=5)
 monitor.admitted(root);preparation.knowledge(root,'s3-r2')
 r.write_new(pathlib.Path(plan['evidence'])/'s3-repair-prepared.json',json.loads((root/'s3-repair-schema-prepared.json').read_text()))
 print('Fourth and final schema migrated; fresh S3 retry identities prepared; prior resources retained.')
if __name__=='__main__':
 plan=json.loads(r.PLAN.read_text());r.gate(plan,json.loads(r.AUTH.read_text()),json.loads(r.REVIEW.read_text()),r.sha(r.PLAN));prepare(plan)
