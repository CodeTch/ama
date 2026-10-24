"""Explicit isolated controlled knowledge provider. Durable SQLite effects; no inferred no-effect."""
import resource
import argparse,base64,hashlib,hmac,http.server,json,os,pathlib,re,secrets,socket,sqlite3,threading,time,urllib.parse
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey,Ed25519PublicKey
MAX_PAYLOAD=8*1024*1024
MAX_WIRE=MAX_PAYLOAD+65536
SCHEMA='ama.controlled-kb-delegation.v1'

def canonical(value):
 # The pinned Java CanonicalJson profile uses lowercase \u00xx for all controls.
 raw=json.dumps(value,ensure_ascii=False,sort_keys=True,separators=(',',':'),allow_nan=False)
 controls={r'\b':r'\u0008',r'\f':r'\u000c',r'\n':r'\u000a',r'\r':r'\u000d',r'\t':r'\u0009'}
 # Match complete escape tokens, so a literal backslash followed by n remains literal.
 return re.sub(r'\\(?:["\\/bfnrt]|u[0-9a-fA-F]{4})',lambda match:controls.get(match.group(),match.group()),raw).encode('utf-8')
def digest(value):return hashlib.sha256(value).hexdigest()
def b64(value):return base64.urlsafe_b64encode(value).decode().rstrip('=')
def unb64(value):
 if not isinstance(value,str) or not re.fullmatch('[A-Za-z0-9_-]+',value):raise Denied(403)
 raw=base64.urlsafe_b64decode(value+'='*((-len(value))%4))
 if b64(raw)!=value:raise Denied(403)
 return raw
class Denied(Exception):
 def __init__(self,status=403,code='DESTINATION_DENIED'):self.status=status;self.code=code

def strict(raw,limit):
 if len(raw)>limit:raise Denied(413,'INVALID_PROVIDER_RECEIPT')
 def pairs(entries):
  out={}
  for key,value in entries:
   if key in out:raise Denied(400,'INVALID_PROVIDER_RECEIPT')
   out[key]=value
  return out
 try:value=json.loads(raw.decode('utf-8','strict'),object_pairs_hook=pairs,parse_float=lambda _:(_ for _ in ()).throw(Denied(400)),parse_constant=lambda _:(_ for _ in ()).throw(Denied(400)))
 except (ValueError,UnicodeError):raise Denied(400,'INVALID_PROVIDER_RECEIPT')
 count=0
 def visit(v,depth):
  nonlocal count
  count+=1
  if depth>16 or count>200000:raise Denied(413,'INVALID_PROVIDER_RECEIPT')
  if isinstance(v,str):v.encode('utf-8','strict')
  elif isinstance(v,dict):
   for k,x in v.items():visit(k,depth+1);visit(x,depth+1)
  elif isinstance(v,list):
   for x in v:visit(x,depth+1)
  elif v is not None and not isinstance(v,(int,bool)):raise Denied(400)
 visit(value,0);return value

def exact(value,keys):
 if not isinstance(value,dict) or set(value)!=set(keys.split()):raise Denied(400,'INVALID_PROVIDER_RECEIPT')
 return value

def validate_publication_binding(r):
 exact(r,'protocol providerId publicationId confirmationId businessKey logicalRequestHash executionRequestHash executionId executionNo actorId libraryId folderId destinationAuthorization payloadSha256 payload bindingTransition')
 if r['protocol']!='ama.controlled-knowledge-publish.v1' or r['businessKey']!=r['publicationId']+':'+r['confirmationId']:raise Denied(400)
 for key in ('publicationId','confirmationId','executionId'):
  if not isinstance(r[key],str) or not re.fullmatch('[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}',r[key]):raise Denied(400)
 for key in ('logicalRequestHash','executionRequestHash','payloadSha256'):
  if not isinstance(r[key],str) or not re.fullmatch('[0-9a-f]{64}',r[key]):raise Denied(400)
 for key in ('actorId','libraryId','folderId'):
  if not isinstance(r[key],str) or not 1<=len(r[key])<=255 or not r[key].strip():raise Denied(400)
 if type(r['executionNo'])!=int or not 1<=r['executionNo']<2**63-1:raise Denied(400)
 value=r['payload'];exact(value,'schemaVersion title meeting minutes provenance sections')
 if value['schemaVersion']!='ama.controlled-knowledge-document.v1':raise Denied(400)
 provenance=exact(value['provenance'],'confirmationId elementSetId inputFingerprint minutesRevisionId renderArtifact sourceManifestHash transcriptConfirmationId')
 if provenance['confirmationId']!=r['confirmationId']:raise Denied(400)
 exact(provenance['renderArtifact'],'version sha256')
 payload=canonical(value)
 if len(payload)>MAX_PAYLOAD or digest(payload)!=r['payloadSha256']:raise Denied(400)
 logical={k:r[k] for k in ('providerId','publicationId','confirmationId','businessKey','libraryId','folderId','payloadSha256')}
 logical.update(schema='ama.knowledge-publish.v1',domain='KNOWLEDGE_PUBLISH',**{k:provenance[k] for k in ('sourceManifestHash','inputFingerprint','renderArtifact')})
 if digest(canonical(logical))!=r['logicalRequestHash']:raise Denied(400)
 transition=r['bindingTransition'];proofs=[]
 if transition is not None:
  exact(transition,'schema expectedGeneration priorProofs newBusinessKey newLogicalRequestHash')
  if transition['schema']!='ama.controlled-kb-binding-transition.v1' or type(transition['expectedGeneration'])!=int or not 1<=transition['expectedGeneration']<2**63-1 or transition['newBusinessKey']!=r['businessKey'] or transition['newLogicalRequestHash']!=r['logicalRequestHash']:raise Denied(400)
  proofs=transition['priorProofs']
  if not isinstance(proofs,list) or not 1<=len(proofs)<=256:raise Denied(400)
  ids=[]
  for proof in proofs:
   exact(proof,'executionId executionRequestHash receiptId proofDigest');ids.append(proof['executionId'])
   if any(not isinstance(proof[k],str) or not re.fullmatch('[0-9a-f]{64}',proof[k]) for k in ('executionRequestHash','proofDigest')) or not isinstance(proof['receiptId'],str) or not re.fullmatch('receipt-[0-9a-f]{32}',proof['receiptId']):raise Denied(400)
  if ids!=sorted(set(ids)):raise Denied(400)
 elif r['executionNo']!=1:raise Denied(400)
 execution={'schema':'ama.knowledge-publish-execution.v1','logicalRequestHash':r['logicalRequestHash'],'executionNo':r['executionNo'],'priorProofs':proofs}
 if digest(canonical(execution))!=r['executionRequestHash']:raise Denied(400)
 return payload

def private(path,limit=8192):
 path=pathlib.Path(path)
 if path.is_symlink() or not path.is_file() or path.stat().st_mode&0o777!=0o600 or path.stat().st_size>limit:raise RuntimeError('Private controlled credential required')
 return path.read_bytes()

class Provider:
 def __init__(self,config,initialize=False):
  self.ids=exact(config['identities'],'providerId storeInstanceId clientId delegationKeyId assertionKeyId')
  self.verifier=private(config['bearerVerifier']).decode().strip()
  self.app_key=serialization.load_der_public_key(private(config['delegationPublic']))
  self.key=serialization.load_der_private_key(private(config['assertionPrivate']),password=None)
  if not isinstance(self.app_key,Ed25519PublicKey) or not isinstance(self.key,Ed25519PrivateKey):raise RuntimeError('Exact Ed25519 required')
  self.lock=threading.RLock();self.path=pathlib.Path(config['store']).resolve()
  if self.path.is_symlink() or self.path.parent.stat().st_mode&0o777!=0o700:raise RuntimeError('Owned durable store required')
  exists=self.path.exists()
  if initialize==exists:raise RuntimeError('Explicit one-time initialization or existing store required')
  self.db=sqlite3.connect(self.path,timeout=5,isolation_level=None,check_same_thread=False);self.db.row_factory=sqlite3.Row
  os.chmod(self.path,0o600);self.db.execute('PRAGMA journal_mode=WAL');self.db.execute('PRAGMA synchronous=FULL');self.db.execute('PRAGMA foreign_keys=ON');self.db.execute('PRAGMA busy_timeout=5000')
  public=self.key.public_key().public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)
  pins={**self.ids,'delegationPublicSha256':digest(self.app_key.public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)),'assertionPublicSha256':digest(public)}
  if initialize:
   self.db.executescript("""BEGIN IMMEDIATE;
CREATE TABLE identity(singleton INTEGER PRIMARY KEY CHECK(singleton=1),pins BLOB NOT NULL);
CREATE TABLE library(id TEXT PRIMARY KEY,name TEXT NOT NULL,root TEXT NOT NULL);
CREATE TABLE folder(id TEXT PRIMARY KEY,library TEXT NOT NULL,parent TEXT,name TEXT NOT NULL,is_root INTEGER NOT NULL,FOREIGN KEY(library) REFERENCES library(id));
CREATE TABLE grant_record(actor TEXT NOT NULL,library TEXT NOT NULL,folder TEXT NOT NULL,can_publish INTEGER NOT NULL,can_read INTEGER NOT NULL,version INTEGER NOT NULL,PRIMARY KEY(actor,library,folder));
CREATE TABLE publication_slot(publication TEXT PRIMARY KEY,generation INTEGER NOT NULL,key TEXT NOT NULL,logical_hash TEXT NOT NULL,document TEXT UNIQUE);
CREATE TABLE execution(id TEXT PRIMARY KEY,publication TEXT NOT NULL,key TEXT NOT NULL,number INTEGER NOT NULL,client TEXT NOT NULL,actor TEXT NOT NULL,logical_hash TEXT NOT NULL,request_hash TEXT NOT NULL,payload_hash TEXT NOT NULL,request BLOB NOT NULL,receipt BLOB NOT NULL,UNIQUE(key,number));
CREATE TABLE document(id TEXT PRIMARY KEY,publication TEXT NOT NULL UNIQUE,client TEXT NOT NULL,actor TEXT NOT NULL,library TEXT NOT NULL,folder TEXT NOT NULL,payload_hash TEXT NOT NULL,payload BLOB NOT NULL,result_id TEXT UNIQUE NOT NULL);
COMMIT;""")
   self.db.execute('INSERT INTO identity VALUES(1,?)',(canonical(pins),))
   self.db.execute('INSERT INTO library VALUES(?,?,?)',('controlled-library','Controlled library','controlled-root'))
   self.db.execute('INSERT INTO folder VALUES(?,?,NULL,?,1)',('controlled-root','controlled-library','Controlled root'))
  actual=self.db.execute('SELECT pins FROM identity WHERE singleton=1').fetchone()
  if not actual or bytes(actual[0])!=canonical(pins):raise RuntimeError('Pinned store/key identity mismatch')
 def administer(self,actor,library,folder,publish,read):
  with self.lock:
   if not self.db.execute('SELECT 1 FROM folder WHERE id=? AND library=?',(folder,library)).fetchone():raise RuntimeError('Actual destination required')
   self.db.execute('INSERT INTO grant_record VALUES(?,?,?,?,?,1) ON CONFLICT(actor,library,folder) DO UPDATE SET can_publish=excluded.can_publish,can_read=excluded.can_read,version=grant_record.version+1',(actor,library,folder,int(publish),int(read)))
 def sign(self,value):
  raw=canonical(value);return b64(raw)+'.'+b64(self.key.sign(raw))
 def verify(self,token,key,schema):
  if not isinstance(token,str) or len(token)>4096 or token.count('.')!=1:raise Denied()
  a,b=token.split('.');raw=unb64(a);signature=unb64(b)
  if len(signature)!=64:raise Denied()
  try:key.verify(signature,raw)
  except Exception:raise Denied()
  value=strict(raw,3072)
  if canonical(value)!=raw or value.get('schema')!=schema:raise Denied()
  now=int(time.time());issued=value.get('issuedAt');expires=value.get('expiresAt')
  if type(issued)!=int or type(expires)!=int or not issued<=expires<=issued+30 or issued>now+2 or expires<now:raise Denied()
  return value
 def authorize(self,headers,method,path,body):
  bearer=headers.get('authorization','')
  if not bearer.startswith('Bearer ') or not hmac.compare_digest(digest(bearer[7:].encode()),self.verifier):raise Denied(401)
  claims=self.verify(headers.get('x-controlled-delegation'),self.app_key,SCHEMA)
  exact(claims,'schema issuer keyId audience storeInstanceId actorId purpose method resource issuedAt expiresAt nonce requestDigest')
  purposes={'/v1/knowledge/destination-authorizations':'DESTINATION_AUTHORIZE','/v1/knowledge/publications':'PUBLISH'}
  purpose=purposes.get(path) if method=='POST' else 'DESTINATION_READ' if path.startswith('/v1/knowledge/libraries') else 'DOCUMENT_READ' if path.startswith('/v1/knowledge/documents/') else 'RECEIPT_LOOKUP'
  expected={**{'issuer':self.ids['clientId'],'keyId':self.ids['delegationKeyId'],'audience':self.ids['providerId'],'storeInstanceId':self.ids['storeInstanceId'],'method':method,'resource':path,'purpose':purpose,'requestDigest':digest(body if method=='POST' else canonical({'method':method,'resource':path}))}}
  if any(claims.get(k)!=v for k,v in expected.items()) or not isinstance(claims['actorId'],str) or not 1<=len(claims['actorId'])<=255 or not re.fullmatch('[A-Za-z0-9_-]{16,64}',claims['nonce']):raise Denied()
  return claims
 def grant(self,actor,library,folder,read=False):
  row=self.db.execute('SELECT g.* FROM grant_record g JOIN folder f ON f.id=g.folder AND f.library=g.library WHERE g.actor=? AND g.library=? AND g.folder=?',(actor,library,folder)).fetchone()
  if not row or not row['can_read' if read else 'can_publish']:raise Denied()
  return row
 def receipt(self,request,state,**extra):return {**{k:request[k] for k in ('providerId','publicationId','confirmationId','businessKey','logicalRequestHash','executionRequestHash','executionId','executionNo','actorId','libraryId','folderId','payloadSha256')},'protocol':'ama.controlled-knowledge-receipt.v1','storeInstanceId':self.ids['storeInstanceId'],'clientId':self.ids['clientId'],'state':state,**extra}
 def serve(self,method,path,headers,raw):
  call=self.authorize(headers,method,path,raw);actor=call['actorId'];body=strict(raw,MAX_WIRE) if method=='POST' else None
  if body is not None and body.get('actorId')!=actor:raise Denied()
  with self.lock:
   if method=='POST' and path=='/v1/knowledge/destination-authorizations':
    exact(body,'actorId providerId libraryId folderId context')
    if body['providerId']!=self.ids['providerId']:raise Denied()
    grant=self.grant(actor,body['libraryId'],body['folderId']);now=int(time.time())
    assertion={'schema':'ama.controlled-kb-destination.v1',**self.ids,'actorId':actor,'libraryId':body['libraryId'],'folderId':body['folderId'],'context':body['context'],'authVersion':grant['version'],'issuedAt':now,'expiresAt':now+30}
    return {'providerId':self.ids['providerId'],'storeInstanceId':self.ids['storeInstanceId'],'assertion':self.sign(assertion)}
   if method=='GET' and path.startswith('/v1/knowledge/libraries'):
    parsed=urllib.parse.urlsplit(path);query=urllib.parse.parse_qs(parsed.query,strict_parsing=True);limit=int(query.get('limit',['20'])[0]);cursor=query.get('cursor',[''])[0]
    if not 1<=limit<=50 or any(len(v)!=1 for v in query.values()):raise Denied(400)
    parts=parsed.path.split('/')
    if parsed.path=='/v1/knowledge/libraries':
     if set(query)-{'limit','cursor'}:raise Denied(400)
     rows=self.db.execute('SELECT DISTINCT l.id,l.name,l.root rootFolderId FROM library l JOIN grant_record g ON g.library=l.id WHERE g.actor=? AND g.can_publish=1 AND l.id>? ORDER BY l.id LIMIT ?',(actor,cursor,limit+1)).fetchall()
    elif len(parts)==6 and parts[5]=='folders':
     if set(query)-{'limit','cursor','parentFolderId'}:raise Denied(400)
     parent=query.get('parentFolderId',[None])[0];library=parts[4]
     rows=self.db.execute('SELECT f.id,f.library libraryId,f.parent parentFolderId,f.is_root isRoot,f.name FROM folder f JOIN grant_record g ON g.library=f.library AND g.folder=f.id WHERE g.actor=? AND g.can_publish=1 AND f.library=? AND (? IS NULL AND f.is_root=1 OR f.parent=?) AND f.id>? ORDER BY f.id LIMIT ?',(actor,library,parent,parent,cursor,limit+1)).fetchall()
    else:raise Denied(404)
    more=len(rows)>limit;items=[dict(r) for r in rows[:limit]];return {'providerId':self.ids['providerId'],'storeInstanceId':self.ids['storeInstanceId'],'items':items,'cursor':items[-1]['id'] if more else None}
   if method=='POST' and path=='/v1/knowledge/publications':return self.publish(body,actor)
   if method=='GET' and '/executions/' in path:
    parts=path.split('/')
    if len(parts)!=7 or parts[1:4]!=['v1','knowledge','publications'] or parts[5]!='executions':raise Denied(404)
    row=self.db.execute('SELECT * FROM execution WHERE publication=? AND id=?',(parts[4],parts[6])).fetchone()
    if not row:return {'protocol':'ama.controlled-knowledge-receipt.v1',**self.ids,'publicationId':parts[4],'executionId':parts[6],'state':'UNKNOWN'}
    if row['client']!=self.ids['clientId'] or row['actor']!=actor:raise Denied()
    return strict(row['receipt'],65536)
   if method=='GET' and path.startswith('/v1/knowledge/publications/by-business-key/'):
    key=path.split('/')[-1];row=self.db.execute('SELECT * FROM execution WHERE key=? ORDER BY number DESC LIMIT 1',(key,)).fetchone()
    if not row:return {'protocol':'ama.controlled-knowledge-receipt.v1',**self.ids,'businessKey':key,'state':'UNKNOWN'}
    if row['client']!=self.ids['clientId'] or row['actor']!=actor:raise Denied()
    return strict(row['receipt'],65536)
   if method=='GET' and (path.startswith('/v1/knowledge/results/') or path.startswith('/v1/knowledge/documents/')):
    document=path.startswith('/v1/knowledge/documents/');id=path.split('/')[-1];row=self.db.execute('SELECT * FROM document WHERE '+('id' if document else 'result_id')+'=?',(id,)).fetchone()
    if not row:raise Denied(404)
    if row['client']!=self.ids['clientId']:raise Denied()
    if document:self.grant(actor,row['library'],row['folder'],True)
    elif row['actor']!=actor:raise Denied()
    result={'providerId':self.ids['providerId'],'storeInstanceId':self.ids['storeInstanceId'],'documentId':row['id'],'resultId':row['result_id'],'publicationId':row['publication'],'payloadSha256':row['payload_hash']}
    if document:result['payload']=strict(row['payload'],MAX_PAYLOAD)
    return result
   raise Denied(404)
 def permanent_receipt(self,receipt):
  token=receipt.get('permanentRejection','')
  if not isinstance(token,str) or len(token)>8192 or token.count('.')!=1:raise Denied(409,'PROVIDER_CONFLICT')
  a,b=token.split('.');raw=unb64(a);signature=unb64(b)
  try:self.key.public_key().verify(signature,raw)
  except Exception:raise Denied(409,'PROVIDER_CONFLICT')
  proof=strict(raw,4096);expected={'schema':'ama.controlled-kb-permanent-rejection.v1',**self.ids,**{k:receipt[k] for k in ('publicationId','confirmationId','businessKey','logicalRequestHash','executionRequestHash','executionId','executionNo','actorId','libraryId','folderId','payloadSha256','receiptId','verifiedAt','slotGeneration','noEffectBasis','errorCode')}}
  if canonical(proof)!=raw or proof!=expected or proof['noEffectBasis']!='DEFINITIVE_REJECTION' or proof['errorCode']!='DESTINATION_DENIED':raise Denied(409,'PROVIDER_CONFLICT')
  return proof
 def publish(self,r,actor):
  payload=validate_publication_binding(r)
  if r['providerId']!=self.ids['providerId'] or r['actorId']!=actor:raise Denied(400)
  immutable={k:v for k,v in r.items() if k!='destinationAuthorization'};exact_request=canonical(immutable)
  self.db.execute('BEGIN IMMEDIATE')
  try:
   old=self.db.execute('SELECT * FROM execution WHERE id=?',(r['executionId'],)).fetchone()
   if old:
    if old['request']!=exact_request:raise Denied(409,'PROVIDER_CONFLICT')
    result=strict(old['receipt'],65536);self.db.execute('COMMIT');return result
   # Immutable replay above precedes destination expiry/revocation; it can never create another effect.
   assertion=self.verify(r['destinationAuthorization'],self.key.public_key(),'ama.controlled-kb-destination.v1')
   if any(assertion.get(k)!=v for k,v in self.ids.items()) or any(assertion.get(k)!=r[k] for k in ('actorId','libraryId','folderId')) or assertion.get('context')!=r['publicationId']:raise Denied()
   slot=self.db.execute('SELECT * FROM publication_slot WHERE publication=?',(r['publicationId'],)).fetchone()
   if slot:
    if slot['document'] is not None:raise Denied(409,'PROVIDER_CONFLICT')
    transition=r['bindingTransition']
    if transition is None or transition['expectedGeneration']!=slot['generation']:raise Denied(409,'PROVIDER_CONFLICT')
    predecessors=self.db.execute('SELECT id,request_hash,receipt FROM execution WHERE publication=? ORDER BY id LIMIT 257',(r['publicationId'],)).fetchall()
    if len(predecessors)>256 or len(predecessors)!=len(transition['priorProofs']):raise Denied(409,'PROVIDER_CONFLICT')
    for previous,expected in zip(predecessors,transition['priorProofs']):
     receipt=strict(previous['receipt'],65536)
     self.permanent_receipt(receipt)
     if receipt.get('state')!='FAILED' or receipt.get('noEffectBasis')!='DEFINITIVE_REJECTION' or expected!={'executionId':previous['id'],'executionRequestHash':previous['request_hash'],'receiptId':receipt.get('receiptId'),'proofDigest':digest(receipt.get('permanentRejection','').encode())}:raise Denied(409,'PROVIDER_CONFLICT')
    maximum=self.db.execute('SELECT COALESCE(MAX(number),0) FROM execution WHERE key=?',(r['businessKey'],)).fetchone()[0]
    if r['executionNo']!=maximum+1:raise Denied(409,'PROVIDER_CONFLICT')
    generation=slot['generation']+1
    if self.db.execute('UPDATE publication_slot SET generation=?,key=?,logical_hash=? WHERE publication=? AND generation=? AND document IS NULL',(generation,r['businessKey'],r['logicalRequestHash'],r['publicationId'],slot['generation'])).rowcount!=1:raise Denied(409,'PROVIDER_CONFLICT')
   else:
    if r['bindingTransition'] is not None or r['executionNo']!=1:raise Denied(409,'PROVIDER_CONFLICT')
    generation=1;self.db.execute('INSERT INTO publication_slot VALUES(?,1,?,?,NULL)',(r['publicationId'],r['businessKey'],r['logicalRequestHash']))
   try:
    grant=self.grant(actor,r['libraryId'],r['folderId'])
    if grant['version']!=assertion.get('authVersion'):raise Denied()
   except Denied:
    # A valid authenticated exact request is durably and permanently rejected before any document write.
    receipt=self.receipt(r,'FAILED',receiptId='receipt-'+secrets.token_hex(16),verifiedAt=int(time.time()),slotGeneration=generation,noEffectBasis='DEFINITIVE_REJECTION',errorCode='DESTINATION_DENIED')
    proof={'schema':'ama.controlled-kb-permanent-rejection.v1',**self.ids,**{k:receipt[k] for k in ('publicationId','confirmationId','businessKey','logicalRequestHash','executionRequestHash','executionId','executionNo','actorId','libraryId','folderId','payloadSha256','receiptId','verifiedAt','slotGeneration','noEffectBasis','errorCode')}}
    receipt['permanentRejection']=self.sign(proof)
    self.db.execute('INSERT INTO execution VALUES(?,?,?,?,?,?,?,?,?,?,?)',(r['executionId'],r['publicationId'],r['businessKey'],r['executionNo'],self.ids['clientId'],actor,r['logicalRequestHash'],r['executionRequestHash'],r['payloadSha256'],exact_request,canonical(receipt)))
    self.db.execute('COMMIT');return receipt
   document='doc-'+secrets.token_hex(16);result_id='result-'+secrets.token_hex(16)
   self.db.execute('INSERT INTO document VALUES(?,?,?,?,?,?,?,?,?)',(document,r['publicationId'],self.ids['clientId'],actor,r['libraryId'],r['folderId'],r['payloadSha256'],payload,result_id))
   self.db.execute('UPDATE publication_slot SET document=? WHERE publication=? AND document IS NULL',(document,r['publicationId']))
   receipt=self.receipt(r,'SUCCESS',documentId=document,resultId=result_id,receiptId='receipt-'+secrets.token_hex(16),verifiedAt=int(time.time()),slotGeneration=generation)
   self.db.execute('INSERT INTO execution VALUES(?,?,?,?,?,?,?,?,?,?,?)',(r['executionId'],r['publicationId'],r['businessKey'],r['executionNo'],self.ids['clientId'],actor,r['logicalRequestHash'],r['executionRequestHash'],r['payloadSha256'],exact_request,canonical(receipt)))
   self.db.execute('COMMIT');return receipt
  except BaseException:self.db.execute('ROLLBACK');raise

class Handler(http.server.BaseHTTPRequestHandler):
 protocol_version='HTTP/1.1';server_version='ControlledKnowledge';sys_version=''
 def log_message(self,*args):pass
 def do_POST(self):self.handle_controlled()
 def do_GET(self):self.handle_controlled()
 def handle_controlled(self):
  timer=threading.Timer(15,self.abort);timer.daemon=True;timer.start()
  try:
   if len(self.path)>2048 or len(self.headers)>32 or sum(len(k)+len(v)+4 for k,v in self.headers.items())>8192:raise Denied(431)
   headers={}
   for k,v in self.headers.items():
    k=k.lower()
    if k in headers or any(ord(c)<32 or ord(c)>126 for c in k+v):raise Denied(400)
    headers[k]=v
   if headers.get('host')!='127.0.0.1:'+str(self.server.server_port) or any(k in headers for k in ('transfer-encoding','content-encoding','expect')):raise Denied(400)
   length=headers.get('content-length','0')
   if not re.fullmatch('0|[1-9][0-9]{0,8}',length) or int(length)>MAX_WIRE or self.command=='GET' and length!='0':raise Denied(413)
   if self.command=='POST' and headers.get('content-type')!='application/json':raise Denied(415)
   self.connection.settimeout(5);body=self.rfile.read(int(length))
   if len(body)!=int(length):raise Denied(400)
   value=self.server.provider.serve(self.command,self.path,headers,body);raw=canonical(value)
   if len(raw)>(MAX_WIRE if '/documents/' in self.path else 262144 if '/libraries' in self.path else 65536):raise Denied(413)
   self.respond(200,raw)
  except Denied as denied:self.respond(denied.status,canonical({'error':denied.code}))
  except (BrokenPipeError,ConnectionResetError,TimeoutError,socket.timeout):pass
  except Exception:self.respond(503,canonical({'error':'PROVIDER_UNAVAILABLE'}))
  finally:timer.cancel();self.close_connection=True
 def abort(self):
  try:self.connection.shutdown(socket.SHUT_RDWR)
  except OSError:pass
  self.connection.close()
 def respond(self,status,body):
  self.send_response(status);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(body)));self.send_header('Cache-Control','no-store');self.send_header('Connection','close');self.end_headers();self.wfile.write(body)
class Server(http.server.ThreadingHTTPServer):
 daemon_threads=True;request_queue_size=4
 def __init__(self,bind,provider):super().__init__(bind,Handler);self.provider=provider;self.capacity=threading.BoundedSemaphore(2)
 def process_request(self,request,address):
  if not self.capacity.acquire(False):request.close();return
  super().process_request(request,address)
 def process_request_thread(self,*args):
  try:super().process_request_thread(*args)
  finally:self.capacity.release()

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--config',required=True);parser.add_argument('--initialize',action='store_true');parser.add_argument('--grant-actor');parser.add_argument('--grant-read',action='store_true');parser.add_argument('--grant-publish',action='store_true');args=parser.parse_args()
 config=strict(private(args.config),8192)
 if config['mode']!='ISOLATED_LOOPBACK_TEST' or config['bind']!='127.0.0.1':raise RuntimeError('Explicit isolated loopback test profile required')
 threading.stack_size(262144)
 os.sched_setaffinity(0,sorted(os.sched_getaffinity(0))[:2])
 resource.setrlimit(resource.RLIMIT_AS,(67108864,67108864))
 provider=Provider(config,args.initialize)
 if args.grant_actor:
  provider.administer(args.grant_actor,'controlled-library','controlled-root',args.grant_publish,args.grant_read);provider.db.close();return
 server=Server(('127.0.0.1',int(config['port'])),provider)
 ready=pathlib.Path(config['readyFile']);ready.write_bytes(canonical({'port':server.server_port,'identities':provider.ids,'store':str(provider.path),'pid':os.getpid(),'pgid':os.getpgid(0)}));os.chmod(ready,0o600)
 try:server.serve_forever(poll_interval=.2)
 finally:server.server_close();provider.db.close()
if __name__=='__main__':main()
