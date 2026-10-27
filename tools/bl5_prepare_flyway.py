"""One same-window repair: retain partial local schema; migrate fresh local2 and empty s3 via real Flyway."""
import json,pathlib,subprocess,time,struct
import bl5_resources as r
import bl5_monitor as monitor
import bl5_prepare as preparation
from bl5_launch import BUILD,JAVA,process_record

def prepare(plan):
 root=r.owned_root(plan);monitor.admitted(root);clock=json.loads((root/'clock.json').read_text())
 if clock['owner']!=plan['id'] or clock['maximumSeconds']!=10800 or clock['bootId']!=pathlib.Path('/proc/sys/kernel/random/boot_id').read_text().strip() or not 0<=time.monotonic()-clock['startedMonotonic']<10400:raise ValueError('Original preparation deadline')
 for name in ['pki','categories.bin','knowledge-local','knowledge-s3','schema-prepared.json','schema-preparation.json','flyway-preparation-consumed.json']:
  if (root/name).exists():raise ValueError('Preparation already consumed')
 if not (root/'preparation-consumed.json').is_file():raise ValueError('Prior preparation must be retained')
 built=json.loads((BUILD/'result.json').read_text());r.verify_execution({'artifacts':built['runtimeArtifacts'],'artifactDirectories':built['artifactDirectories']})
 if built['exit']!=0 or not built['stable']:raise ValueError('Unverified preparation build')
 for name,digest in built['source'].items():
  if r.sha(r.ROOT/name)!=digest:raise ValueError('Preparation source drift')
 records=json.loads((root/'resources.json').read_text())
 for kind in ['mysql','s3']:r.inspect('docker',plan,kind,records[kind]['id'])
 cid=records['mysql']['id'];prefix=plan['mysql']['schemaPrefix'];old=prefix+'_local';local=prefix+'_local2';s3=prefix+'_s3'
 if prefix!='ama_bl5_5d9729e1a748':raise ValueError('Exact owned schema names required')
 if preparation.sql('docker',cid,s3,'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE();').strip()!='0':raise ValueError('S3 schema is not empty')
 existing=preparation.sql('docker',cid,s3,"SELECT schema_name FROM information_schema.schemata WHERE schema_name IN ('"+old+"','"+local+"','"+s3+"') ORDER BY schema_name;").splitlines()
 if existing!=[old,s3]:raise ValueError('Expected exactly original two schemas before creating third')
 r.write_new(root/'flyway-preparation-consumed.json',{'owner':plan['id'],'originalStartedMonotonic':clock['startedMonotonic'],'retainedPartialSchema':old,'selectedSchemas':[local,s3]},True)
 preparation.sql('docker',cid,s3,'CREATE DATABASE `'+local+'` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL ON `'+local+'`.* TO ama_bl5;')
 r.write_new(root/'schema-preparation.json',{'mysqlPort':int(records['mysql']['ports']['3306/tcp'][0]['HostPort'])},True)
 env={'HOME':'/home/tanren','USER':'tanren','LANG':'C.UTF-8','PATH':str(JAVA/'bin')+':/usr/bin:/bin','JAVA_HOME':str(JAVA),'AMA_BL5_ROOT':str(root),'JAVA_TOOL_OPTIONS':'-XX:MaxMetaspaceSize=128m -XX:MaxDirectMemorySize=64m -XX:ReservedCodeCacheSize=64m -Xss512k'}
 with (root/'schema-preparation.log').open('x') as log:
  process=subprocess.Popen([str(JAVA/'bin/java'),'-Xmx256m','-XX:ActiveProcessorCount=2','-cp',(BUILD/'classpath.txt').read_text(),'ama.storage.Bl5SchemaPrepare'],cwd=BUILD/'project',env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
  try:
   process_record(root,'schema-flyway',process)
   if process.wait(timeout=120)!=0:raise RuntimeError('Flyway preparation failed; retain partial evidence, never rerun')
  finally:
   if process.poll() is None:
    process.terminate()
    try:process.wait(timeout=5)
    except subprocess.TimeoutExpired:process.kill();process.wait(timeout=5)
 monitor.admitted(root)
 history=json.loads((root/'schema-prepared.json').read_text());preparation.pki(root)
 data=struct.pack('>IBI',0x414d4143,1,1)
 for item in ['bl5-controlled','B-L5 synthetic meeting']:
  raw=item.encode();data+=struct.pack('>I',len(raw))+raw
 preparation.private(root/'categories.bin',data)
 for mode in ['local','s3']:preparation.knowledge(root,mode)
 r.write_new(pathlib.Path(plan['evidence'])/'prepared.json',{'owner':plan['id'],'schemas':[local,s3],'retainedPartialSchema':old,'migrationMethod':'ama.db.SchemaMigrator/Flyway','history':history,'newPrivatePki':True,'providerStoresStarted':False})
 print('Fresh local2 and s3 schemas migrated with Flyway; original partial schema retained.')

if __name__=='__main__':
 plan=json.loads(r.PLAN.read_text());r.gate(plan,json.loads(r.AUTH.read_text()),json.loads(r.REVIEW.read_text()),r.sha(r.PLAN));prepare(plan)
