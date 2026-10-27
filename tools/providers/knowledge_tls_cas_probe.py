"""Single released, newly owned TLS/provider-protocol scenario. No application business fixture."""
import base64,copy,datetime as dt,hashlib,http.client,ipaddress,json,os,pathlib,resource,secrets,signal,socket,sqlite3,ssl,subprocess,sys,threading,time,uuid
from cryptography import x509
from cryptography.hazmat.primitives import hashes,serialization
from cryptography.hazmat.primitives.asymmetric import ec,ed25519
from cryptography.x509.oid import NameOID,ExtendedKeyUsageOID
import controlled_knowledge as kb
P=pathlib.Path(__file__).resolve().parents[2];W=P/'.superpowers/sdd/2026-10-23-ama-phase-b-remaining'
OWNER='task11b6-aba9ef6fe1cb49038cc08ab48f5648eb';ACTOR='real-viewer'
def require(value):
 if not value:raise RuntimeError('Exact owned TLS protocol assertion failed')
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def write(path,value,private=False):
 raw=value if isinstance(value,bytes) else kb.canonical(value)
 fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600 if private else 0o644)
 with os.fdopen(fd,'wb') as out:out.write(raw)
class Probe:
 def __init__(self):
  plan_path=W/'task-11-b6-tls-cas-plan-20261027.json';require(sha(plan_path)==os.environ['AMA_TASK11_B6_PLAN_SHA256']);self.plan=json.loads(plan_path.read_text());require(self.plan['owner']==OWNER)
  self.root=P/'.tools'/OWNER;self.out=pathlib.Path(self.plan['output']);require(str(self.root)==self.plan['root'] and self.out==W/'task-11-b6-runtime-evidence-20261027/tls-cas-01');require((self.out/'consumed.json').is_file());require(not self.root.exists() and self.root.parent.resolve()==P/'.tools');require(sha(pathlib.Path(self.plan['payloadTemplate']))==self.plan['payloadSha256'])
  self.root.mkdir(mode=0o700);self.runtime=self.root/'runtime';self.runtime.mkdir(mode=0o700);self.process=None;self.possible_send=False;self.settled=False;self.starts=0;self.admin_calls=0;self.sqlite_reads=0;self.calls=[];self.lock=threading.Lock();self.pending=0
  self.env={'PATH':'/usr/bin:/bin','HOME':os.environ['HOME'],'LANG':'C.UTF-8','PYTHONDONTWRITEBYTECODE':'1'}
 def credentials(self):
  now=dt.datetime.now(dt.timezone.utc);ca_key=ec.generate_private_key(ec.SECP256R1());server_key=ec.generate_private_key(ec.SECP256R1());ca_name=x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,OWNER+' CA')]);server_name=x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,OWNER)])
  ca=x509.CertificateBuilder().subject_name(ca_name).issuer_name(ca_name).public_key(ca_key.public_key()).serial_number(x509.random_serial_number()).not_valid_before(now-dt.timedelta(minutes=1)).not_valid_after(now+dt.timedelta(days=2)).add_extension(x509.BasicConstraints(ca=True,path_length=0),critical=True).sign(ca_key,hashes.SHA256())
  leaf=x509.CertificateBuilder().subject_name(server_name).issuer_name(ca_name).public_key(server_key.public_key()).serial_number(x509.random_serial_number()).not_valid_before(now-dt.timedelta(minutes=1)).not_valid_after(now+dt.timedelta(days=2)).add_extension(x509.BasicConstraints(ca=False,path_length=None),critical=True).add_extension(x509.SubjectAlternativeName([x509.IPAddress(ipaddress.ip_address('127.0.0.1'))]),critical=False).add_extension(x509.ExtendedKeyUsage([ExtendedKeyUsageOID.SERVER_AUTH]),critical=False).sign(ca_key,hashes.SHA256())
  self.signing=ed25519.Ed25519PrivateKey.generate();self.assertion=ed25519.Ed25519PrivateKey.generate();self.bearer=base64.urlsafe_b64encode(secrets.token_bytes(32)).decode().rstrip('=');self.ids=dict(providerId='CONTROLLED_KNOWLEDGE_V1',storeInstanceId=str(uuid.uuid4()),clientId='b6-'+uuid.uuid4().hex,delegationKeyId='b6-'+uuid.uuid4().hex,assertionKeyId='b6-'+uuid.uuid4().hex)
  files={'ca.pem':ca.public_bytes(serialization.Encoding.PEM),'server.pem':leaf.public_bytes(serialization.Encoding.PEM)+ca.public_bytes(serialization.Encoding.PEM),'server.key':server_key.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()),'bearer':self.bearer.encode(),'bearer-verifier':kb.digest(self.bearer.encode()).encode(),'delegation.der':self.signing.private_bytes(serialization.Encoding.DER,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()),'delegation-public.der':self.signing.public_key().public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo),'assertion.der':self.assertion.private_bytes(serialization.Encoding.DER,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()),'assertion-public.der':self.assertion.public_key().public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)}
  for name,data in files.items():write(self.root/name,data,True)
  self.application=self.root/'application.json';write(self.application,dict(role='application',**self.ids,paths=dict(serviceBearer=str(self.root/'bearer'),delegationPrivateKey=str(self.root/'delegation.der'),providerAssertionPublicKey=str(self.root/'assertion-public.der'),providerTlsTrustCertificate=str(self.root/'ca.pem'))),True)
  with socket.socket() as reserve:reserve.bind(('127.0.0.1',0));self.port=reserve.getsockname()[1]
  self.config=dict(identities=self.ids,bearerVerifier=str(self.root/'bearer-verifier'),delegationPublic=str(self.root/'delegation-public.der'),assertionPrivate=str(self.root/'assertion.der'),store=str(self.runtime/'knowledge.sqlite'),port=self.port,bind='127.0.0.1',mode='CONTROLLED_TLS',tlsCertificate=str(self.root/'server.pem'),tlsPrivateKey=str(self.root/'server.key'))
  self.trust=ssl.create_default_context(cafile=str(self.root/'ca.pem'));self.trust.minimum_version=ssl.TLSVersion.TLSv1_2
  write(self.out/'resource.json',dict(owner=OWNER,root=str(self.root),port=self.port,identities=self.ids,application=str(self.application),applicationSha256=sha(self.application),caSha256=sha(self.root/'ca.pem'),store=self.config['store'],newStore=True,applicationBusinessFixture=False))
 def start(self,initialize):
  self.starts+=1;require(self.starts<=2);ready=self.runtime/('ready-'+str(self.starts)+'.json');self.config_path=self.runtime/('provider-'+str(self.starts)+'.json');write(self.config_path,{**self.config,'readyFile':str(ready)},True);self.log=self.runtime/('provider-'+str(self.starts)+'.log');fd=os.open(self.log,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
  command=['/usr/bin/python3',str(P/'tools/providers/controlled_knowledge.py'),'--config',str(self.config_path)]+(['--initialize'] if initialize else [])
  with os.fdopen(fd,'wb') as out:self.process=subprocess.Popen(command,env=self.env,stdout=out,stderr=subprocess.STDOUT,start_new_session=True)
  write(self.out/('provider-'+str(self.starts)+'-before-ready.json'),dict(pid=self.process.pid,port=self.port,initialize=initialize,configSha256=sha(self.config_path)))
  end=time.monotonic()+12
  while not ready.exists() and self.process.poll() is None and time.monotonic()<end:time.sleep(.025)
  require(self.process.poll() is None and ready.is_file());actual=json.loads(ready.read_text());require(actual['pid']==self.process.pid==actual['pgid'] and actual['port']==self.port and actual['identities']==self.ids and actual['store']==self.config['store'])
  write(self.out/('provider-'+str(self.starts)+'-started.json'),dict(actual=actual,initialize=initialize,configSha256=sha(self.config_path),mode='CONTROLLED_TLS'))
 def stop(self):
  if self.process is None:return
  process=self.process
  if process.poll() is None:
   process.terminate()
   try:process.wait(timeout=5)
   except subprocess.TimeoutExpired:process.kill();process.wait(timeout=5)
  write(self.out/('provider-'+str(self.starts)+'-closed.json'),dict(pid=process.pid,exit=process.returncode,alive=False,storeRetained=pathlib.Path(self.config['store']).is_file()))
  write(self.out/('provider-'+str(self.starts)+'.log'),self.log.read_bytes().replace(self.bearer.encode(),b'[REDACTED]'));self.process=None
 def admin(self,publish):
  self.admin_calls+=1;require(self.admin_calls<=3);log=self.runtime/('admin-'+str(self.admin_calls)+'.log');fd=os.open(log,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
  command=['/usr/bin/python3',str(P/'tools/providers/controlled_knowledge.py'),'--config',str(self.config_path),'--grant-actor',ACTOR,'--grant-read']+(['--grant-publish'] if publish else [])
  with os.fdopen(fd,'wb') as out:result=subprocess.run(command,env=self.env,stdout=out,stderr=subprocess.STDOUT,timeout=10)
  require(result.returncode==0);write(self.out/('admin-'+str(self.admin_calls)+'.json'),dict(actor=ACTOR,publish=publish,read=True,exit=0))
 def call(self,method,path,body=None,auth='both'):
  raw=b'' if body is None else kb.canonical(body);now=int(time.time());purpose='DESTINATION_AUTHORIZE' if path=='/v1/knowledge/destination-authorizations' else 'PUBLISH' if method=='POST' else 'DESTINATION_READ' if '/libraries' in path else 'DOCUMENT_READ' if '/documents/' in path else 'RECEIPT_LOOKUP'
  claims=dict(schema=kb.SCHEMA,issuer=self.ids['clientId'],keyId=self.ids['delegationKeyId'],audience=self.ids['providerId'],storeInstanceId=self.ids['storeInstanceId'],actorId=ACTOR,purpose=purpose,method=method,resource=path,issuedAt=now,expiresAt=now+30,nonce=secrets.token_hex(16),requestDigest=kb.digest(raw if method=='POST' else kb.canonical(dict(method=method,resource=path))))
  signed=kb.canonical(claims);headers={'Content-Type':'application/json','Content-Length':str(len(raw)),'Connection':'close'}
  if auth!='missing-bearer':headers['Authorization']='Bearer '+self.bearer
  if auth!='missing-delegation':headers['X-Controlled-Delegation']=kb.b64(signed)+'.'+kb.b64(self.signing.sign(signed))
  with self.lock:
   number=len(self.calls)+1;row=dict(number=number,method=method,purpose=purpose,path=path,requestSha256=kb.digest(raw),startedMonotonic=time.monotonic(),auth=auth);self.calls.append(row)
   if purpose=='PUBLISH':self.possible_send=True;self.pending+=1
  write(self.out/('request-'+str(number)+'-before.json'),row)
  connection=http.client.HTTPSConnection('127.0.0.1',self.port,context=self.trust,timeout=5)
  try:
   connection.request(method,path,body=raw,headers=headers);response=connection.getresponse();length=response.getheader('Content-Length');require(length is not None and length.isdigit() and int(length)<=kb.MAX_WIRE and response.getheader('Content-Encoding') is None and response.getheader('Transfer-Encoding') is None);data=response.read(int(length)+1);require(len(data)==int(length));value=kb.strict(data,kb.MAX_WIRE);row.update(status=response.status,responseSha256=kb.digest(data),endedMonotonic=time.monotonic());write(self.out/('request-'+str(number)+'-response.json'),row)
   with self.lock:
    if purpose=='PUBLISH':self.pending-=1
   return response.status,value
  finally:connection.close()
 def snapshot(self):
  self.sqlite_reads+=1;require(self.sqlite_reads<=3);store=pathlib.Path(self.config['store']);require(store.resolve()==self.runtime/'knowledge.sqlite' and not store.is_symlink());write(self.out/('sqlite-'+str(self.sqlite_reads)+'-before.json'),dict(store=str(store),mode='ro',queries=7,newOwnerOnly=True));tables={};execution={}
  def wire(value):return {'bytesSha256':kb.digest(value),'length':len(value)} if isinstance(value,bytes) else value
  db=sqlite3.connect(store.as_uri()+'?mode=ro',uri=True,timeout=2,isolation_level=None)
  try:
   for table in ('identity','library','folder','grant_record','publication_slot','execution','document'):
    rows=db.execute('SELECT * FROM '+table+' ORDER BY 1 LIMIT 33').fetchall();require(len(rows)<=32);safe=[[wire(x) for x in row] for row in rows];tables[table]=dict(count=len(rows),sha256=kb.digest(kb.canonical(safe)))
    if table=='execution':execution={row[0]:kb.digest(kb.canonical([wire(x) for x in row])) for row in rows}
  finally:db.close()
  result=dict(tables=tables,executionRows=execution);write(self.out/('sqlite-'+str(self.sqlite_reads)+'-result.json'),result);return result
 def rehash(self,r):
  r['payloadSha256']=kb.digest(kb.canonical(r['payload']));logical={k:r[k] for k in ('providerId','publicationId','confirmationId','businessKey','libraryId','folderId','payloadSha256')};logical.update(schema='ama.knowledge-publish.v1',domain='KNOWLEDGE_PUBLISH',**{k:r['payload']['provenance'][k] for k in ('sourceManifestHash','inputFingerprint','renderArtifact')});r['logicalRequestHash']=kb.digest(kb.canonical(logical));r['executionRequestHash']=kb.digest(kb.canonical(dict(schema='ama.knowledge-publish-execution.v1',logicalRequestHash=r['logicalRequestHash'],executionNo=r['executionNo'],priorProofs=[] if r['bindingTransition'] is None else r['bindingTransition']['priorProofs'])))
 def run(self):
  self.credentials();self.start(True);first_pid=self.process.pid;self.admin(True);write(self.out/'java-client-ready.json',dict(port=self.port,application=str(self.application),identities=self.ids,actor=ACTOR))
  end=time.monotonic()+35;ack=self.out/'java-client-ack.json'
  while not ack.exists() and time.monotonic()<end:require(self.process.poll() is None);time.sleep(.025)
  require(ack.is_file());java=json.loads(ack.read_text());require(java['get']==1 and java['post']==0 and java['actor']==ACTOR and java['actualTlsAndProviderAuth'])
  require(self.call('GET','/v1/knowledge/libraries?limit=20',auth='missing-bearer')[0]==401);require(self.call('GET','/v1/knowledge/libraries?limit=20',auth='missing-delegation')[0]==403)
  publication=str(uuid.uuid4());payload=json.loads(pathlib.Path(self.plan['payloadTemplate']).read_text())['value'];payload=copy.deepcopy(payload)
  for key in ('confirmationId','elementSetId','minutesRevisionId','transcriptConfirmationId'):payload['provenance'][key]=str(uuid.uuid4())
  payload['meeting']['name']='Constructed TLS provider protocol case';confirmation=payload['provenance']['confirmationId'];destination=dict(actorId=ACTOR,providerId=self.ids['providerId'],libraryId='controlled-library',folderId='controlled-root',context=publication)
  code,auth=self.call('POST','/v1/knowledge/destination-authorizations',destination);require(code==200);self.admin(False)
  original=dict(protocol='ama.controlled-knowledge-publish.v1',providerId=self.ids['providerId'],publicationId=publication,confirmationId=confirmation,businessKey=publication+':'+confirmation,executionId=str(uuid.uuid4()),executionNo=1,actorId=ACTOR,libraryId='controlled-library',folderId='controlled-root',destinationAuthorization=auth['assertion'],payload=payload,bindingTransition=None);self.rehash(original)
  code,rejected=self.call('POST','/v1/knowledge/publications',original);require(code==200 and rejected['state']=='FAILED' and rejected['noEffectBasis']=='DEFINITIVE_REJECTION');a,b=rejected['permanentRejection'].split('.');self.assertion.public_key().verify(kb.unb64(b),kb.unb64(a));require(kb.strict(kb.unb64(a),4096)['executionId']==original['executionId']);before=self.snapshot();require(before['tables']['document']['count']==0 and before['tables']['execution']['count']==1)
  self.admin(True);code,auth=self.call('POST','/v1/knowledge/destination-authorizations',destination);require(code==200);proof=dict(executionId=original['executionId'],executionRequestHash=original['executionRequestHash'],receiptId=rejected['receiptId'],proofDigest=kb.digest(rejected['permanentRejection'].encode()));nexts=[]
  for _ in range(2):
   value=copy.deepcopy(original);value.update(executionId=str(uuid.uuid4()),executionNo=2,destinationAuthorization=auth['assertion'],bindingTransition=dict(schema='ama.controlled-kb-binding-transition.v1',expectedGeneration=1,priorProofs=[proof],newBusinessKey=original['businessKey'],newLogicalRequestHash=original['logicalRequestHash']));self.rehash(value);nexts.append(value)
  barrier=threading.Barrier(3);answers=[None,None];errors=[]
  def compete(index):
   try:barrier.wait(timeout=3);answers[index]=self.call('POST','/v1/knowledge/publications',nexts[index])
   except BaseException as error:errors.append(type(error).__name__)
  threads=[threading.Thread(target=compete,args=(index,),daemon=True) for index in range(2)]
  for thread in threads:thread.start()
  barrier.wait(timeout=3)
  for thread in threads:thread.join(18);require(not thread.is_alive())
  require(not errors and sorted(answer[0] for answer in answers)==[200,409]);winner=next(i for i,a in enumerate(answers) if a[0]==200);succeeded=answers[winner][1];require(succeeded['state']=='SUCCESS');competition=[row for row in self.calls if row['purpose']=='PUBLISH'][1:3];require(max(x['startedMonotonic'] for x in competition)<min(x['endedMonotonic'] for x in competition))
  require(self.call('POST','/v1/knowledge/publications',nexts[winner])==(200,succeeded));require(self.call('POST','/v1/knowledge/publications',original)==(200,rejected));changed=copy.deepcopy(original);changed['payload']['meeting']['name']='Different valid immutable request';self.rehash(changed);require(self.call('POST','/v1/knowledge/publications',changed)[0]==409)
  def lookup(execution):return '/v1/knowledge/publications/'+publication+'/executions/'+execution
  require(self.call('GET',lookup(original['executionId']))==(200,rejected));require(self.call('GET',lookup(nexts[winner]['executionId']))==(200,succeeded));code,unseen=self.call('GET',lookup(nexts[1-winner]['executionId']));require(code==200 and unseen['state']=='UNKNOWN');document='/v1/knowledge/documents/'+succeeded['documentId'];code,view=self.call('GET',document);require(code==200 and view['payload']==payload)
  after=self.snapshot();require(after['tables']['document']['count']==1 and after['tables']['execution']['count']==2 and after['tables']['publication_slot']['count']==1 and after['executionRows'][original['executionId']]==before['executionRows'][original['executionId']]);require(self.pending==0);self.stop();self.start(False);require(self.process.pid!=first_pid)
  require(self.call('GET',lookup(original['executionId']))==(200,rejected));require(self.call('GET',lookup(nexts[winner]['executionId']))==(200,succeeded));require(self.call('GET',document)==(200,view));reopened=self.snapshot();require(reopened==after)
  counts=dict(GET=1+sum(row['method']=='GET' for row in self.calls),POST=sum(row['method']=='POST' for row in self.calls),publicationPOST=sum(row['purpose']=='PUBLISH' for row in self.calls),destinationPOST=sum(row['purpose']=='DESTINATION_AUTHORIZE' for row in self.calls),adminCalls=self.admin_calls,providerStarts=self.starts,sqliteReadOnlySessions=self.sqlite_reads,sqliteQueriesPerSession=7);require(counts==self.plan['requestBudget']);self.settled=True
  write(self.out/'protocol-result.json',dict(qualified=True,counts=counts,publication=publication,original=rejected,winner=succeeded,loserExecution=nexts[1-winner]['executionId'],competingIntervals=competition,restartPreservedAllSevenTables=True,immutableOriginalPreserved=True,documentCount=1,firstPid=first_pid,secondPid=self.process.pid,constructedProviderInputs=True,applicationBusinessMarker=False,javaDiscoveryActorSource='explicit DB boundary substitute'))
 def finish(self):
  if self.process is not None:
   if self.possible_send and not self.settled:write(self.out/'provider-retained.json',dict(pid=self.process.pid,alive=self.process.poll() is None,port=self.port,store=self.config['store'],pending=self.pending,reason='Protocol outcome not fully verified; no automatic cleanup/retry'))
   else:self.stop()
def main():
 os.sched_setaffinity(0,sorted(os.sched_getaffinity(0))[:2]);resource.setrlimit(resource.RLIMIT_AS,(268435456,268435456));probe=None
 def stop(signum,frame):raise InterruptedError('Owned driver termination')
 signal.signal(signal.SIGTERM,stop)
 try:probe=Probe();probe.run();return 0
 except BaseException as error:
  if probe is not None:write(probe.out/'driver-failure.json',dict(errorType=type(error).__name__,possiblePublicationSend=probe.possible_send,pending=probe.pending))
  print(json.dumps(dict(status='FAILED_NO_RETRY',errorType=type(error).__name__)));return 1
 finally:
  if probe is not None:probe.finish()
if __name__=='__main__':sys.exit(main())
