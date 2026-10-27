"""Prepare only this authorized B-L5 batch after fresh resources and source gates."""
import datetime,hashlib,ipaddress,json,os,pathlib,secrets,subprocess,time,uuid
import bl5_resources as resources
from cryptography import x509
from cryptography.hazmat.primitives import hashes,serialization
from cryptography.hazmat.primitives.asymmetric import ec,ed25519
from cryptography.hazmat.primitives.serialization import pkcs12
from cryptography.x509.oid import NameOID,ExtendedKeyUsageOID
JAVA=pathlib.Path('/home/tanren/ama/.worktrees/phase-a/.tools/zulu21.52.203-ca-jdk21.0.12.1-linux_x64')

def private(path,raw):
 fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
 with os.fdopen(fd,'wb') as f:f.write(raw.encode() if isinstance(raw,str) else raw)

def pki(root):
 target=root/'pki';target.mkdir(mode=0o700,exist_ok=False)
 now=datetime.datetime.now(datetime.timezone.utc);ca_key=ec.generate_private_key(ec.SECP256R1());ca_name=x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,'B-L5 fresh isolated CA')])
 def builder(name,key):return x509.CertificateBuilder().subject_name(name).issuer_name(ca_name).public_key(key.public_key()).serial_number(x509.random_serial_number()).not_valid_before(now-datetime.timedelta(minutes=1)).not_valid_after(now+datetime.timedelta(days=1))
 ca=builder(ca_name,ca_key).add_extension(x509.BasicConstraints(ca=True,path_length=0),True).sign(ca_key,hashes.SHA256());private(target/'ca.pem',ca.public_bytes(serialization.Encoding.PEM));password=secrets.token_urlsafe(24);private(target/'keystore-password',password)
 for name in ['server','writer']:
  key=ec.generate_private_key(ec.SECP256R1());subject=x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,'B-L5 '+name)])
  san=[x509.DNSName('localhost'),x509.IPAddress(ipaddress.ip_address('127.0.0.1'))] if name=='server' else [x509.UniformResourceIdentifier('spiffe://ama/writer')]
  cert=builder(subject,key).add_extension(x509.BasicConstraints(ca=False,path_length=None),True).add_extension(x509.SubjectAlternativeName(san),False).add_extension(x509.ExtendedKeyUsage([ExtendedKeyUsageOID.SERVER_AUTH if name=='server' else ExtendedKeyUsageOID.CLIENT_AUTH]),False).sign(ca_key,hashes.SHA256())
  private(target/(name+'.p12'),pkcs12.serialize_key_and_certificates(name.encode(),key,cert,[ca],serialization.BestAvailableEncryption(password.encode())))
  if name=='server':private(target/'server.pem',cert.public_bytes(serialization.Encoding.PEM)+ca.public_bytes(serialization.Encoding.PEM));private(target/'server.key',key.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
 result=subprocess.run([str(JAVA/'bin/keytool'),'-importcert','-noprompt','-alias','bl5-ca','-file',str(target/'ca.pem'),'-keystore',str(target/'trust.p12'),'-storetype','PKCS12','-storepass:file',str(target/'keystore-password')],capture_output=True,timeout=10)
 if result.returncode:raise RuntimeError('Fresh private trust store generation failed')
 (target/'trust.p12').chmod(0o600)

def knowledge(root,mode):
 target=root/('knowledge-'+mode);target.mkdir(mode=0o700,exist_ok=False)
 for role in ['application','provider']:(target/role).mkdir(mode=0o700)
 app=target/'application';provider=target/'provider';delegation=ed25519.Ed25519PrivateKey.generate();assertion=ed25519.Ed25519PrivateKey.generate();bearer=secrets.token_urlsafe(32)
 private(app/'bearer',bearer);private(provider/'bearer-sha256',hashlib.sha256(bearer.encode()).hexdigest())
 private(app/'delegation.der',delegation.private_bytes(serialization.Encoding.DER,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()));private(provider/'delegation.der',delegation.public_key().public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo))
 private(provider/'assertion.der',assertion.private_bytes(serialization.Encoding.DER,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()));private(app/'assertion.der',assertion.public_key().public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo))
 ids={'providerId':'CONTROLLED_KNOWLEDGE_V1',**{name:str(uuid.uuid4()) for name in ['storeInstanceId','clientId','delegationKeyId','assertionKeyId']}}
 a={**ids,'role':'application','paths':{'serviceBearer':str(app/'bearer'),'delegationPrivateKey':str(app/'delegation.der'),'providerAssertionPublicKey':str(app/'assertion.der'),'providerTlsTrustCertificate':str(root/'pki/ca.pem')}}
 b={**ids,'role':'provider','paths':{'serviceBearerVerifier':str(provider/'bearer-sha256'),'delegationPublicKey':str(provider/'delegation.der'),'assertionPrivateKey':str(provider/'assertion.der')}}
 private(app/'publicidentities.json',json.dumps(a));private(provider/'publicidentities.json',json.dumps(b))

def migration_order(name):
 version=pathlib.Path(name).name.split('__',1)[0][1:]
 return tuple(int(n) for n in version.split('_'))

def sql(docker,cid,schema,statement):
 if not __import__('re').fullmatch('ama_bl5_[a-f0-9]+_(local|local2|s3|s3r2)',schema):raise ValueError('Foreign schema')
 command='MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --protocol=TCP -h127.0.0.1 --user=root --batch --skip-column-names --binary-mode'
 result=subprocess.run([docker,'exec','-i',cid,'sh','-c',command],input=('USE `'+schema+'`;'+statement).encode(),capture_output=True,timeout=30)
 if result.returncode:raise RuntimeError('Owned fresh schema operation failed')
 return result.stdout.decode()

def prepare(plan,docker):
 root=resources.owned_root(plan);clock=json.loads((root/'clock.json').read_text())
 if clock['owner']!=plan['id'] or clock['maximumSeconds']!=10800 or time.monotonic()-clock['startedMonotonic']>10600:raise ValueError('Owned runtime preparation deadline')
 resources.write_new(root/'preparation-consumed.json',{'owner':plan['id'],'started':time.monotonic()},True)
 records=json.loads((root/'resources.json').read_text());selected={kind:resources.inspect(docker,plan,kind,records[kind]['id']) for kind in ['mysql','s3']}
 cid=records['mysql']['id'];local=plan['mysql']['schemaPrefix']+'_local';s3=plan['mysql']['schemaPrefix']+'_s3'
 deadline=time.monotonic()+120
 while True:
  try:
   if sql(docker,cid,local,'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE();').strip()!='0':raise ValueError('Fresh schema must be empty')
   break
  except RuntimeError:
   if time.monotonic()>deadline:raise
   time.sleep(1)
 # Create only the second named fresh schema; no DROP, reset, IF NOT EXISTS, or prior fixture access.
 sql(docker,cid,local,'CREATE DATABASE `'+s3+'` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; GRANT ALL ON `'+s3+'`.* TO ama_bl5;')
 migration_files=sorted(plan['migrations'],key=migration_order)
 for schema in [local,s3]:
  if sql(docker,cid,schema,'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE();').strip()!='0':raise ValueError('Nonempty fresh schema')
  for name in migration_files:
   path=resources.ROOT/name
   if resources.sha(path)!=plan['migrations'][name]:raise ValueError('Migration source drift')
   sql(docker,cid,schema,path.read_text())
 pki(root)
 import struct
 rows=[('bl5-controlled','B-L5 synthetic meeting')]
 data=struct.pack('>IBI',0x414d4143,1,len(rows))
 for row in rows:
  for item in row:
   raw=item.encode();data+=struct.pack('>I',len(raw))+raw
 private(root/'categories.bin',data)
 for mode in ['local','s3']:knowledge(root,mode)
 resources.write_new(pathlib.Path(plan['evidence'])/'prepared.json',{'owner':plan['id'],'schemas':[local,s3],'migrations':migration_files,'newPrivatePki':True,'providerStoresStarted':False})

if __name__=='__main__':
 plan=json.loads(resources.PLAN.read_text());resources.gate(plan,json.loads(resources.AUTH.read_text()),json.loads(resources.REVIEW.read_text()),resources.sha(resources.PLAN));prepare(plan,__import__('shutil').which('docker'))
