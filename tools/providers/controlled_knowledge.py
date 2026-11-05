"""Explicit isolated controlled knowledge provider. Durable SQLite effects; no inferred no-effect."""
import resource
import argparse,base64,hashlib,hmac,http.server,json,os,pathlib,re,secrets,socket,ssl,sqlite3,threading,time,urllib.parse
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
 value=r['payload']
 extended=isinstance(value,dict) and value.get('schemaVersion')=='ama.controlled-knowledge-document.v2'
 exact(value,'schemaVersion title meeting minutes provenance sections'+(' publicationMetadata' if extended else ''))
 if not extended and value['schemaVersion']!='ama.controlled-knowledge-document.v1':raise Denied(400)
 if extended:
  metadata=exact(value['publicationMetadata'],'schema configurationVersionToken providerId libraryId folderId libraryName description visibility pathNames pathIds tagRules tags')
  if metadata['schema']!='ama.publication-configuration.v1' or any(metadata[k]!=r[k] for k in ('providerId','libraryId','folderId')) or not isinstance(metadata['configurationVersionToken'],str) or not re.fullmatch('[1-9][0-9]{0,18}',metadata['configurationVersionToken']) or int(metadata['configurationVersionToken'])>=2**63-1:raise Denied(400)
  rules=exact(metadata['tagRules'],'maxCount maxLength allowEmpty trim caseSensitive')
  if any(type(rules[k])!=int or not 1<=rules[k]<=256 for k in ('maxCount','maxLength')) or any(type(rules[k])!=bool for k in ('allowEmpty','trim','caseSensitive')):raise Denied(400)
  tags=metadata['tags']
  if not isinstance(tags,list) or len(tags)>rules['maxCount'] or not tags and not rules['allowEmpty']:raise Denied(400)
  identities=[]
  for tag in tags:
   if not isinstance(tag,str) or not tag.strip() or len(tag)>rules['maxLength'] or any(ord(c)<32 or 127<=ord(c)<=159 for c in tag) or rules['trim'] and tag!=tag.strip():raise Denied(400)
   identities.append(tag if rules['caseSensitive'] else tag.lower())
  if len(set(identities))!=len(identities):raise Denied(400)
  if not isinstance(metadata['pathNames'],list) or not isinstance(metadata['pathIds'],list) or len(metadata['pathNames'])!=len(metadata['pathIds'])+2 or not 2<=len(metadata['pathNames'])<=129:raise Denied(400)
  if any(not isinstance(x,str) or not x.strip() or len(x)>255 for x in metadata['pathNames']+metadata['pathIds']):raise Denied(400)
  if metadata['pathNames'][0]!=metadata['libraryName'] or metadata['pathIds'] and metadata['pathIds'][-1]!=r['folderId']:raise Denied(400)
  if not isinstance(metadata['description'],str) or len(metadata['description'])>2048 or not isinstance(metadata['visibility'],str) or not metadata['visibility'].strip() or len(metadata['visibility'])>1024:raise Denied(400)
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
  # Additive protocol migration on this explicitly owned controlled store. No document rewrite.
  self.db.executescript("""BEGIN IMMEDIATE;
CREATE TABLE IF NOT EXISTS document_governance(document TEXT PRIMARY KEY REFERENCES document(id),state TEXT NOT NULL CHECK(state IN ('ACTIVE','WITHDRAWN','OBSOLETE')),revision INTEGER NOT NULL CHECK(revision>=0),event_id TEXT NOT NULL UNIQUE,changed_at INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS governance_command(document TEXT NOT NULL REFERENCES document(id),command_key TEXT NOT NULL,actor TEXT NOT NULL,request_hash TEXT NOT NULL,receipt BLOB NOT NULL,PRIMARY KEY(document,command_key));
CREATE TABLE IF NOT EXISTS library_profile(library TEXT PRIMARY KEY REFERENCES library(id),description TEXT NOT NULL,visibility TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS governance_grant(actor TEXT NOT NULL,library TEXT NOT NULL REFERENCES library(id),can_manage INTEGER NOT NULL CHECK(can_manage IN (0,1)),PRIMARY KEY(actor,library));
COMMIT;""")
  self.tag_rules=config.get('publicationTagRules',dict(maxCount=20,maxLength=64,allowEmpty=True,trim=True,caseSensitive=True))
  exact(self.tag_rules,'maxCount maxLength allowEmpty trim caseSensitive')
  if any(type(self.tag_rules[k])!=int or not 1<=self.tag_rules[k]<=256 for k in ('maxCount','maxLength')) or any(type(self.tag_rules[k])!=bool for k in ('allowEmpty','trim','caseSensitive')):raise RuntimeError('Explicit publication tag rules required')
 def create_child_folder(self,folder):
  if not re.fullmatch(r'[a-z][a-z0-9-]{0,127}',folder) or folder=='controlled-root':raise RuntimeError('Bounded new child destination required')
  expected=(folder,'controlled-library','controlled-root',folder,0)
  with self.lock:
   self.db.execute('BEGIN IMMEDIATE')
   try:
    if not self.db.execute("SELECT 1 FROM folder WHERE id='controlled-root' AND library='controlled-library' AND parent IS NULL AND is_root=1").fetchone():raise RuntimeError('Original controlled root required')
    actual=self.db.execute('SELECT id,library,parent,name,is_root FROM folder WHERE id=?',(folder,)).fetchone()
    if actual is None:self.db.execute('INSERT INTO folder VALUES(?,?,?,?,?)',expected)
    elif tuple(actual)!=expected:raise RuntimeError('Existing destination differs')
    self.db.execute('COMMIT')
   except BaseException:
    self.db.execute('ROLLBACK');raise
 def administer(self,actor,library,folder,publish,read):
  with self.lock:
   if not self.db.execute('SELECT 1 FROM folder WHERE id=? AND library=?',(folder,library)).fetchone():raise RuntimeError('Actual destination required')
   self.db.execute('INSERT INTO grant_record VALUES(?,?,?,?,?,1) ON CONFLICT(actor,library,folder) DO UPDATE SET can_publish=excluded.can_publish,can_read=excluded.can_read,version=grant_record.version+1',(actor,library,folder,int(publish),int(read)))
 def manage(self,actor,library,allowed):
  with self.lock:
   self.db.execute('INSERT INTO governance_grant VALUES(?,?,?) ON CONFLICT(actor,library) DO UPDATE SET can_manage=excluded.can_manage',(actor,library,int(allowed)))
 def manager(self,actor,library):
  return bool(self.db.execute('SELECT 1 FROM governance_grant WHERE actor=? AND library=? AND can_manage=1',(actor,library)).fetchone())
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
  purposes={'/v1/knowledge/destination-authorizations':'DESTINATION_AUTHORIZE','/v1/knowledge/publications':'PUBLISH','/v1/knowledge/material-authorizations':'MATERIAL_READ'}
  purpose=purposes.get(path) if method=='POST' else 'MATERIAL_READ' if path.startswith(('/v1/knowledge/material-libraries','/v1/knowledge/material-documents/')) else 'DESTINATION_READ' if path.startswith('/v1/knowledge/libraries') else 'DOCUMENT_READ' if path.startswith('/v1/knowledge/documents/') else 'RECEIPT_LOOKUP'
  if re.fullmatch(r'/v1/knowledge/governance/documents/doc-[0-9a-f]{32}/(?:state|commands(?:/[A-Za-z0-9_.:-]{1,128})?)',path):purpose='GOVERNANCE_CHANGE' if method=='POST' else 'GOVERNANCE_OBSERVE'
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
  if method=='POST' and not isinstance(body,dict):raise Denied(400,'INVALID_PROVIDER_RECEIPT')
  if body is not None and body.get('actorId')!=actor:raise Denied()
  with self.lock:
   if method=='GET' and (path.startswith('/v1/knowledge/documents/index?') or re.fullmatch(r'/v1/knowledge/documents/doc-[0-9a-f]{32}\?view=permissions',path)):return self.publication_index(actor,path)
   if path.startswith('/v1/knowledge/governance/'):return self.governance(method,path,actor,body)
   if method=='POST' and path=='/v1/knowledge/material-authorizations':return self.material_authority(actor,body)
   if method=='POST' and path=='/v1/knowledge/destination-authorizations':
    exact(body,'actorId providerId libraryId folderId context')
    if body['providerId']!=self.ids['providerId']:raise Denied()
    grant=self.grant(actor,body['libraryId'],body['folderId']);now=int(time.time())
    assertion={'schema':'ama.controlled-kb-destination.v1',**self.ids,'actorId':actor,'libraryId':body['libraryId'],'folderId':body['folderId'],'context':body['context'],'authVersion':grant['version'],'issuedAt':now,'expiresAt':now+30}
    return {'providerId':self.ids['providerId'],'storeInstanceId':self.ids['storeInstanceId'],'assertion':self.sign(assertion)}
   if method=='GET' and path.startswith(('/v1/knowledge/material-libraries','/v1/knowledge/material-documents/')):return self.material_read(actor,path)
   if method=='GET' and path.startswith('/v1/knowledge/libraries'):
    if len(path)>2048 or re.search(r'%(?![0-9A-Fa-f]{2})',path):raise Denied(400)
    try:
     parsed=urllib.parse.urlsplit(path)
     if parsed.fragment or parsed.scheme or parsed.netloc:raise Denied(400)
     query=urllib.parse.parse_qs(parsed.query,strict_parsing=True,keep_blank_values=True,encoding='utf-8',errors='strict')
     if any(len(v)!=1 or not v[0] for v in query.values()):raise Denied(400)
     count=query.get('limit',['20'])[0]
     if not re.fullmatch(r'[1-9][0-9]?',count):raise Denied(400)
     limit=int(count)
     if limit>50:raise Denied(400)
     def identifier(value):
      if not 1<=len(value)<=255 or not value.strip() or value in ('.','..') or any(ord(c)<32 or 127<=ord(c)<=159 or c in '/\\' for c in value) or re.search(r'%(2f|5c)',value,re.I):raise Denied(400)
      value.encode('utf-8','strict');return value
     cursor=identifier(query['cursor'][0]) if 'cursor' in query else ''
     parts=parsed.path.split('/')
     if parsed.path=='/v1/knowledge/libraries/publication-rules':
      if query:raise Denied(400)
      return dict(providerId=self.ids['providerId'],storeInstanceId=self.ids['storeInstanceId'],tagRules=self.tag_rules)
     if len(parts)==6 and parts[1:4]==['v1','knowledge','libraries'] and parts[5]=='configuration':
      if set(query)!={'folderId'}:raise Denied(400)
      library=identifier(urllib.parse.unquote(parts[4],encoding='utf-8',errors='strict'));folder=identifier(query['folderId'][0]);self.grant(actor,library,folder)
      lib=self.library_metadata(actor,library)
      chain=[];seen=set();current=folder
      while current is not None:
       if len(chain)>=128 or current in seen:raise Denied(503,'INVALID_PROVIDER_RECEIPT')
       seen.add(current);node=self.db.execute('SELECT id,name,parent,is_root FROM folder WHERE id=? AND library=?',(current,library)).fetchone()
       if not node:raise Denied(409,'DESTINATION_UNAVAILABLE')
       chain.append(dict(node))
       if node['is_root']:
        if node['id']!=lib['rootFolderId'] or node['parent'] is not None:raise Denied(503,'INVALID_PROVIDER_RECEIPT')
        break
       current=node['parent']
      if not chain or not chain[-1]['is_root']:raise Denied(409,'DESTINATION_UNAVAILABLE')
      chain.reverse()
      return dict(providerId=self.ids['providerId'],storeInstanceId=self.ids['storeInstanceId'],libraryId=library,folderId=folder,libraryName=lib['name'],description=lib['description'],visibility=lib['visibility'],pathNames=[lib['name']]+[x['name'] for x in chain],pathIds=[x['id'] for x in chain[1:]],tagRules=self.tag_rules)
     if parsed.path=='/v1/knowledge/libraries':
      if set(query)-{'limit','cursor','search'}:raise Denied(400)
      search=query.get('search',[''])[0]
      if len(search)>255 or any(ord(c)<32 for c in search):raise Denied(400)
      rows=self.db.execute("SELECT DISTINCT l.id FROM library l JOIN grant_record g ON g.library=l.id LEFT JOIN library_profile p ON p.library=l.id WHERE g.actor=? AND (g.can_publish=1 OR g.can_read=1) AND l.id>? AND (instr(lower(l.name),lower(?))>0 OR instr(lower(COALESCE(p.description,'')),lower(?))>0) ORDER BY l.id LIMIT ?",(actor,cursor,search,search,limit+1)).fetchall()
      rows=[self.library_metadata(actor,row['id']) for row in rows]
     elif len(parts)==6 and parts[1:4]==['v1','knowledge','libraries'] and parts[5]=='folders':
      if set(query)-{'limit','cursor','parentFolderId'}:raise Denied(400)
      parent=identifier(query['parentFolderId'][0]) if 'parentFolderId' in query else None
      library=identifier(urllib.parse.unquote(parts[4],encoding='utf-8',errors='strict'))
      rows=self.db.execute('SELECT f.id,f.library libraryId,f.parent parentFolderId,f.is_root isRoot,f.name FROM folder f JOIN grant_record g ON g.library=f.library AND g.folder=f.id WHERE g.actor=? AND g.can_publish=1 AND f.library=? AND (? IS NULL AND f.is_root=1 OR f.parent=?) AND f.id>? ORDER BY f.id LIMIT ?',(actor,library,parent,parent,cursor,limit+1)).fetchall()
     else:raise Denied(404)
    except (ValueError,UnicodeError):raise Denied(400)
    more=len(rows)>limit;items=[dict(r) for r in rows[:limit]];return {'providerId':self.ids['providerId'],'storeInstanceId':self.ids['storeInstanceId'],'items':items,'cursor':items[-1]['id'] if more else None}
   if method=='POST' and path=='/v1/knowledge/publications':return self.publish(body,actor)
   if method=='GET' and re.fullmatch(r'/v1/knowledge/documents/doc-[0-9a-f]{32}\?view=location',path):
    document=path.split('/')[-1].split('?')[0]
    row=self.db.execute('SELECT id,publication,client,library,folder,payload_hash,result_id FROM document WHERE id=?',(document,)).fetchone()
    if not row:raise Denied(404)
    if row['client']!=self.ids['clientId']:raise Denied()
    self.require_document_active(row['id']);grant=self.grant(actor,row['library'],row['folder'],True)
    library=self.db.execute('SELECT id,name,root FROM library WHERE id=?',(row['library'],)).fetchone()
    if not library or not isinstance(library['name'],str) or not 1<=len(library['name'])<=1024:raise Denied(503,'INVALID_PROVIDER_RECEIPT')
    chain=[];seen=set();current=row['folder']
    while current is not None:
     if len(chain)>=32 or current in seen:raise Denied(503,'INVALID_PROVIDER_RECEIPT')
     seen.add(current);folder=self.db.execute('SELECT id,library,parent,name,is_root FROM folder WHERE id=? AND library=?',(current,row['library'])).fetchone()
     if not folder or not isinstance(folder['name'],str) or not 1<=len(folder['name'])<=1024:raise Denied(503,'INVALID_PROVIDER_RECEIPT')
     chain.append(folder['name'])
     if folder['is_root']:
      if folder['id']!=library['root'] or folder['parent'] is not None:raise Denied(503,'INVALID_PROVIDER_RECEIPT')
      break
     current=folder['parent']
     if current is None:raise Denied(503,'INVALID_PROVIDER_RECEIPT')
    if not chain or not 1<=grant['version']<2**63:raise Denied(503,'INVALID_PROVIDER_RECEIPT')
    result=dict(providerId=self.ids['providerId'],storeInstanceId=self.ids['storeInstanceId'],viewerId=actor,documentId=row['id'],resultId=row['result_id'],publicationId=row['publication'],payloadSha256=row['payload_hash'],libraryId=row['library'],libraryName=library['name'],libraryDescription='',folderId=row['folder'],folderPath=list(reversed(chain)),readGrantVersionToken=str(grant['version']))
    result['locationVersion']=digest(canonical(result));return result
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
    if document:self.require_document_active(row['id']);self.grant(actor,row['library'],row['folder'],True)
    elif row['actor']!=actor:raise Denied()
    result={'providerId':self.ids['providerId'],'storeInstanceId':self.ids['storeInstanceId'],'documentId':row['id'],'resultId':row['result_id'],'publicationId':row['publication'],'payloadSha256':row['payload_hash']}
    if document:result['payload']=strict(row['payload'],MAX_PAYLOAD)
    return result
   raise Denied(404)
 def library_metadata(self,actor,library):
  row=self.db.execute("SELECT l.id,l.name,l.root rootFolderId,COALESCE(p.description,'') description,COALESCE(p.visibility,'具有该知识库阅读权限的成员') visibility,MAX(g.can_publish) canPublish FROM library l JOIN grant_record g ON g.library=l.id LEFT JOIN library_profile p ON p.library=l.id WHERE l.id=? AND g.actor=? AND (g.can_publish=1 OR g.can_read=1) GROUP BY l.id",(library,actor)).fetchone()
  if not row:raise Denied()
  item=dict(row);item['canPublish']=bool(item['canPublish']);item['unavailableReason']='' if item['canPublish'] else '当前账号没有该知识库的发布权限';return item
 def require_document_active(self,document):
  row=self.db.execute('SELECT state FROM document_governance WHERE document=?',(document,)).fetchone()
  if row and row['state']!='ACTIVE':raise Denied(403,'DOCUMENT_NOT_ACTIVE')
 def publication_index(self,actor,path):
  from urllib.parse import urlsplit,parse_qs
  parts=urlsplit(path);query=parse_qs(parts.query,keep_blank_values=True)
  identity=dict(providerId=self.ids['providerId'],storeInstanceId=self.ids['storeInstanceId'])
  if parts.path!='/v1/knowledge/documents/index':
   document=parts.path.rsplit('/',1)[1];row=self.db.execute('SELECT library,folder,publication FROM document WHERE id=? AND client=?',(document,self.ids['clientId'])).fetchone()
   if not row:raise Denied(404)
   can_manage=self.manager(actor,row['library']);can_read=bool(self.db.execute('SELECT 1 FROM grant_record WHERE actor=? AND library=? AND folder=? AND can_read=1',(actor,row['library'],row['folder'])).fetchone())
   if not can_manage and not can_read:raise Denied()
   return dict(**identity,documentId=document,publicationId=row['publication'],libraryId=row['library'],canManage=can_manage)
  if set(query)-{'view','cursor','limit'} or any(len(v)!=1 for v in query.values()) or query.get('view',[''])[0] not in ('read','manage'):raise Denied(400)
  view=query['view'][0];cursor=query.get('cursor',[''])[0];limit=query.get('limit',['20'])[0]
  if not re.fullmatch('(?:[1-9]|[1-4][0-9]|50)',limit) or cursor and not re.fullmatch('doc-[0-9a-f]{32}',cursor):raise Denied(400)
  limit=int(limit)
  rows=self.db.execute("SELECT d.id,d.publication,d.library,d.folder,d.payload_hash,substr(json_extract(CAST(d.payload AS TEXT),'$.title'),1,512) title,substr(json_extract(CAST(d.payload AS TEXT),'$.meeting.name'),1,512) meeting_name,COALESCE(g.state,'ACTIVE') state,COALESCE(r.can_read,0) can_read,COALESCE(a.can_manage,0) can_manage FROM document d LEFT JOIN document_governance g ON g.document=d.id LEFT JOIN grant_record r ON r.actor=? AND r.library=d.library AND r.folder=d.folder LEFT JOIN governance_grant a ON a.actor=? AND a.library=d.library WHERE d.client=? AND d.id>? AND ((?='manage' AND a.can_manage=1) OR (?='read' AND r.can_read=1 AND COALESCE(g.state,'ACTIVE')='ACTIVE')) ORDER BY d.id LIMIT ?",(actor,actor,self.ids['clientId'],cursor,view,view,limit+1)).fetchall()
  items=[]
  for row in rows[:limit]:
   library=self.db.execute("SELECT l.name,COALESCE(p.description,'') description FROM library l LEFT JOIN library_profile p ON p.library=l.id WHERE l.id=?",(row['library'],)).fetchone();chain=[];seen=set();current=row['folder']
   while current is not None:
    if current in seen or len(chain)>=32:raise Denied(503,'INVALID_PROVIDER_RECEIPT')
    seen.add(current);folder=self.db.execute('SELECT name,parent FROM folder WHERE id=? AND library=?',(current,row['library'])).fetchone()
    if not folder:raise Denied(503,'INVALID_PROVIDER_RECEIPT')
    chain.append(folder['name']);current=folder['parent']
   if not library or not chain:raise Denied(503,'INVALID_PROVIDER_RECEIPT')
   items.append(dict(documentId=row['id'],publicationId=row['publication'],libraryId=row['library'],folderId=row['folder'],libraryName=library['name'],libraryDescription=library['description'],folderPath=list(reversed(chain)),title=row['title'],meetingName=row['meeting_name'],payloadSha256=row['payload_hash'],state=row['state'],canManage=bool(row['can_manage']),canRead=bool(row['can_read']) and row['state']=='ACTIVE'))
  return dict(**identity,items=items,cursor=items[-1]['documentId'] if len(rows)>limit else None,hasManagedLibraries=bool(self.db.execute('SELECT 1 FROM governance_grant WHERE actor=? AND can_manage=1 LIMIT 1',(actor,)).fetchone()))
 def governance(self,method,path,actor,body):
  match=re.fullmatch(r'/v1/knowledge/governance/documents/(doc-[0-9a-f]{32})/(state|commands(?:/[A-Za-z0-9_.:-]{1,128})?)',path)
  if not match:raise Denied(404)
  document,tail=match.groups();row=self.db.execute('SELECT id,publication,client,actor,library,folder FROM document WHERE id=?',(document,)).fetchone()
  if not row:raise Denied(404)
  if row['client']!=self.ids['clientId'] or row['actor']!=actor and not self.manager(actor,row['library']):raise Denied()
  def envelope(claims):return dict(providerId=self.ids['providerId'],storeInstanceId=self.ids['storeInstanceId'],assertion=self.sign(claims))
  if method=='GET' and tail.startswith('commands/'):
   saved=self.db.execute('SELECT actor,receipt FROM governance_command WHERE document=? AND command_key=?',(document,tail.split('/')[1])).fetchone()
   if not saved:raise Denied(404,'GOVERNANCE_NOT_FOUND')
   if saved['actor']!=actor:raise Denied()
   return strict(saved['receipt'],8192)
  if method=='GET' and tail=='state':
   now=int(time.time());self.db.execute("INSERT OR IGNORE INTO document_governance VALUES(?,'ACTIVE',0,?,?)",(document,secrets.token_hex(16),now));state=self.db.execute('SELECT * FROM document_governance WHERE document=?',(document,)).fetchone()
   return envelope(dict(schema='ama.controlled-kb-governance-state.v1',**self.ids,documentId=document,publicationId=row['publication'],actorId=actor,state=state['state'],revisionToken=str(state['revision']),eventId=state['event_id'],issuedAt=now,expiresAt=now+5))
  if method!='POST' or tail!='commands':raise Denied(404)
  exact(body,'protocol actorId documentId publicationId commandKey expectedRevisionToken action reason')
  key=body['commandKey'];version=body['expectedRevisionToken'];reason=body['reason']
  if body['protocol']!='ama.controlled-knowledge-governance.v1' or body['documentId']!=document or body['publicationId']!=row['publication'] or body['action'] not in ('WITHDRAW','OBSOLETE') or not isinstance(key,str) or not re.fullmatch('[A-Za-z0-9_.:-]{1,128}',key) or not isinstance(version,str) or not re.fullmatch('0|[1-9][0-9]{0,18}',version) or int(version)>=2**63-1 or not isinstance(reason,str) or not reason.strip() or len(reason)>2048 or len(reason.encode('utf-8'))>8192:raise Denied(400,'INVALID_GOVERNANCE_REQUEST')
  request_hash=digest(canonical(body));self.db.execute('BEGIN IMMEDIATE')
  try:
   old=self.db.execute('SELECT actor,request_hash,receipt FROM governance_command WHERE document=? AND command_key=?',(document,key)).fetchone()
   if old:
    if old['actor']!=actor or old['request_hash']!=request_hash:raise Denied(409,'GOVERNANCE_CONFLICT')
    result=strict(old['receipt'],8192);self.db.commit();return result
   if not self.manager(actor,row['library']):raise Denied(403,'SPACE_MANAGEMENT_REQUIRED')
   now=int(time.time());self.db.execute("INSERT OR IGNORE INTO document_governance VALUES(?,'ACTIVE',0,?,?)",(document,secrets.token_hex(16),now));current=self.db.execute('SELECT * FROM document_governance WHERE document=?',(document,)).fetchone()
   if current['state']!='ACTIVE' or current['revision']!=int(version):raise Denied(409,'GOVERNANCE_CONFLICT')
   state='WITHDRAWN' if body['action']=='WITHDRAW' else 'OBSOLETE';event=secrets.token_hex(16);revision=current['revision']+1
   claims=dict(schema='ama.controlled-kb-governance-receipt.v1',**self.ids,documentId=document,publicationId=row['publication'],actorId=actor,commandKey=key,requestSha256=request_hash,reasonSha256=digest(reason.encode('utf-8')),action=body['action'],state=state,revisionToken=str(revision),eventId=event,verifiedAt=now)
   result=envelope(claims);self.db.execute('UPDATE document_governance SET state=?,revision=?,event_id=?,changed_at=? WHERE document=?',(state,revision,event,now,document));self.db.execute('INSERT INTO governance_command VALUES(?,?,?,?,?)',(document,key,actor,request_hash,canonical(result)));self.db.commit();return result
  except BaseException:self.db.rollback();raise
 def material_authority(self,actor,body):
  exact(body,'protocol actorId items')
  if body['protocol']!='ama.controlled-material-authority.v1' or not isinstance(body['items'],list) or not 1<=len(body['items'])<=64:raise Denied(400)
  seen=set()
  for item in body['items']:
   if not isinstance(item,dict):raise Denied(400)
   exact(item,'libraryId documentId version')
   if any(not isinstance(item[k],str) or not re.fullmatch('[A-Za-z0-9_-]{1,128}',item[k]) for k in ('libraryId','documentId')) or not isinstance(item['version'],str) or not re.fullmatch('[0-9a-f]{64}',item['version']):raise Denied(400)
   identity=(item['libraryId'],item['documentId'],item['version'])
   if identity in seen:raise Denied(400)
   seen.add(identity)
  # Validate the whole exact-version batch under the provider's serialized store operation.
  # No content is returned, and no partial or stale authority survives any failed item.
  for library,document,version in seen:
   row=self.db.execute('SELECT * FROM document WHERE id=? AND library=? AND client=?',(document,library,self.ids['clientId'])).fetchone()
   if not row:raise Denied(404)
   self.require_document_active(document);self.grant(actor,library,row['folder'],True)
   if row['payload_hash']!=version:raise Denied(409,'VERSION_CONFLICT')
   if digest(bytes(row['payload']))!=version:raise Denied(503,'INVALID_PROVIDER_RECEIPT')
  now=int(time.time());claims=dict(schema='ama.controlled-material-authority.v1',**self.ids,actorId=actor,itemsSha256=digest(canonical(body['items'])),issuedAt=now,expiresAt=now+5)
  return dict(providerId=self.ids['providerId'],storeInstanceId=self.ids['storeInstanceId'],assertion=self.sign(claims))
 def material_read(self,actor,path):
  # Caller has already verified service bearer, exact signed delegation and store/key pins.
  if len(path)>2048 or re.search(r'%(?![0-9A-Fa-f]{2})',path):raise Denied(400)
  try:
   parsed=urllib.parse.urlsplit(path)
   query=urllib.parse.parse_qs(parsed.query,strict_parsing=True,keep_blank_values=True,encoding='utf-8',errors='strict')
   if parsed.fragment or parsed.scheme or parsed.netloc or any(len(v)!=1 or not v[0] for v in query.values()):raise Denied(400)
   def identifier(value):
    if not isinstance(value,str) or not re.fullmatch('[A-Za-z0-9_-]{1,128}',value):raise Denied(400)
    return value
   parts=parsed.path.split('/')
   if len(parts)==5 and parts[1:4]==['v1','knowledge','material-documents']:
    if set(query)!={'libraryId','version'}:raise Denied(400)
    document=identifier(parts[4]);library=identifier(query['libraryId'][0]);version=query['version'][0]
    if not re.fullmatch('[0-9a-f]{64}',version):raise Denied(400)
    row=self.db.execute('SELECT * FROM document WHERE id=? AND library=? AND client=?',(document,library,self.ids['clientId'])).fetchone()
    if not row:raise Denied(404)
    self.require_document_active(row['id']);self.grant(actor,library,row['folder'],True)
    if row['payload_hash']!=version:raise Denied(409,'VERSION_CONFLICT')
    payload=bytes(row['payload'])
    if len(payload)>MAX_PAYLOAD or digest(payload)!=version:raise Denied(503,'INVALID_PROVIDER_RECEIPT')
    return {'providerId':self.ids['providerId'],'storeInstanceId':self.ids['storeInstanceId'],'libraryId':library,'documentId':document,'version':version,'sha256':version,'payload':strict(payload,MAX_PAYLOAD)}
   count=query.get('limit',['20'])[0];search=query.get('search',[''])[0];after=query.get('cursor',[''])[0]
   if not re.fullmatch('[1-9][0-9]?',count) or int(count)>50 or len(search)>128 or any(ord(c)<32 or 127<=ord(c)<=159 for c in search):raise Denied(400)
   limit=int(count)
   if after:identifier(after)
   if parsed.path=='/v1/knowledge/material-libraries':
    if set(query)-{'limit','cursor','search'}:raise Denied(400)
    rows=self.db.execute('SELECT DISTINCT l.id,l.name FROM library l JOIN grant_record g ON g.library=l.id JOIN folder f ON f.id=g.folder AND f.library=l.id WHERE g.actor=? AND g.can_read=1 AND l.id>? AND instr(lower(l.name),lower(?))>0 ORDER BY l.id LIMIT ?',(actor,after,search,limit+1)).fetchall()
    items=[]
    for row in rows[:limit]:
     meta=self.db.execute("""SELECT count(*) documentCount,max(strftime('%Y-%m-%dT%H:%M:%SZ',json_extract(CAST(e.receipt AS TEXT),'$.verifiedAt'),'unixepoch')) updatedAt
      FROM document d LEFT JOIN execution e ON e.publication=d.publication AND json_extract(CAST(e.receipt AS TEXT),'$.documentId')=d.id
      WHERE d.library=? AND d.client=? AND EXISTS(SELECT 1 FROM grant_record g WHERE g.actor=? AND g.library=d.library AND g.folder=d.folder AND g.can_read=1)
      AND NOT EXISTS(SELECT 1 FROM document_governance dg WHERE dg.document=d.id AND dg.state<>'ACTIVE')""",(row['id'],self.ids['clientId'],actor)).fetchone()
     items.append({**dict(row),**dict(meta)})
   elif len(parts)==6 and parts[1:4]==['v1','knowledge','material-libraries'] and parts[5]=='documents':
    if set(query)-{'limit','cursor','search'}:raise Denied(400)
    library=identifier(parts[4])
    if not self.db.execute('SELECT 1 FROM grant_record g JOIN folder f ON f.id=g.folder AND f.library=g.library WHERE g.actor=? AND g.library=? AND g.can_read=1 LIMIT 1',(actor,library)).fetchone():raise Denied()
    rows=self.db.execute("SELECT d.id,d.library libraryId,json_extract(CAST(d.payload AS TEXT),'$.title') name,d.payload_hash version,length(d.payload) size,(SELECT strftime('%Y-%m-%dT%H:%M:%SZ',json_extract(CAST(e.receipt AS TEXT),'$.verifiedAt'),'unixepoch') FROM execution e WHERE e.publication=d.publication AND json_extract(CAST(e.receipt AS TEXT),'$.documentId')=d.id LIMIT 1) updatedAt FROM document d JOIN grant_record g ON g.actor=? AND g.library=d.library AND g.folder=d.folder AND g.can_read=1 WHERE NOT EXISTS(SELECT 1 FROM document_governance dg WHERE dg.document=d.id AND dg.state<>'ACTIVE') AND d.library=? AND d.client=? AND d.id>? AND instr(lower(json_extract(CAST(d.payload AS TEXT),'$.title')),lower(?))>0 ORDER BY d.id LIMIT ?",(actor,library,self.ids['clientId'],after,search,limit+1)).fetchall()
    items=[]
    for row in rows[:limit]:
     if not isinstance(row['name'],str) or len(row['name'])>1024 or not 0<row['size']<=MAX_PAYLOAD:raise Denied(413)
     items.append({'id':row['id'],'libraryId':row['libraryId'],'name':row['name'],'version':row['version'],'sha256':row['version'],'sizeBytesToken':str(row['size']),'updatedAt':row['updatedAt']})
   else:raise Denied(404)
   return {'providerId':self.ids['providerId'],'storeInstanceId':self.ids['storeInstanceId'],'items':items,'cursor':items[-1]['id'] if len(rows)>limit else None}
  except (ValueError,UnicodeError):raise Denied(400)
 def permanent_receipt(self,receipt):
  token=receipt.get('permanentRejection','')
  if not isinstance(token,str) or len(token)>8192 or token.count('.')!=1:raise Denied(409,'PROVIDER_CONFLICT')
  a,b=token.split('.');raw=unb64(a);signature=unb64(b)
  try:self.key.public_key().verify(signature,raw)
  except Exception:raise Denied(409,'PROVIDER_CONFLICT')
  proof=strict(raw,4096);expected={'schema':'ama.controlled-kb-permanent-rejection.v1',**self.ids,**{k:receipt[k] for k in ('publicationId','confirmationId','businessKey','logicalRequestHash','executionRequestHash','executionId','executionNo','actorId','libraryId','folderId','payloadSha256','receiptId','verifiedAt','slotGeneration','noEffectBasis','errorCode')}}
  if canonical(proof)!=raw or proof!=expected or proof['noEffectBasis']!='DEFINITIVE_REJECTION' or proof['errorCode']!='DESTINATION_DENIED':raise Denied(409,'PROVIDER_CONFLICT')
  return proof
 def transition_generation(self,r,slot,predecessors,maximum):
  # Caller has authenticated the immutable request and destination under BEGIN IMMEDIATE.
  # This pure validator additionally recomputes the exact new binding; gaps never invent receipts.
  validate_publication_binding(r)
  transition=r['bindingTransition']
  if slot['document'] is not None or transition is None or transition['expectedGeneration']!=slot['generation']:raise Denied(409,'PROVIDER_CONFLICT')
  if len(predecessors)>256 or len(predecessors)!=len(transition['priorProofs']):raise Denied(409,'PROVIDER_CONFLICT')
  for previous,expected in zip(predecessors,transition['priorProofs']):
   receipt=strict(previous['receipt'],65536);self.permanent_receipt(receipt)
   if receipt.get('state')!='FAILED' or receipt.get('publicationId')!=r['publicationId'] or receipt.get('noEffectBasis')!='DEFINITIVE_REJECTION' or expected!={'executionId':previous['id'],'executionRequestHash':previous['request_hash'],'receiptId':receipt.get('receiptId'),'proofDigest':digest(receipt.get('permanentRejection','').encode())}:raise Denied(409,'PROVIDER_CONFLICT')
  # Actual accepted rows plus UNIQUE(key,number) forbid number reuse. An authenticated app
  # may skip only unseen numbers; full real predecessor proof and slot CAS remain mandatory.
  if type(maximum)!=int or maximum<0 or r['executionNo']<=maximum or slot['generation']>=2**63-2:raise Denied(409,'PROVIDER_CONFLICT')
  return slot['generation']+1
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
    predecessors=self.db.execute('SELECT id,request_hash,receipt FROM execution WHERE publication=? ORDER BY id LIMIT 257',(r['publicationId'],)).fetchall()
    maximum=self.db.execute('SELECT COALESCE(MAX(number),0) FROM execution WHERE key=?',(r['businessKey'],)).fetchone()[0]
    generation=self.transition_generation(r,slot,predecessors,maximum)
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
def transport_context(config):
 if config.get('bind')!='127.0.0.1' or config.get('mode') not in ('ISOLATED_LOOPBACK_TEST','CONTROLLED_TLS'):raise RuntimeError('Explicit pinned loopback transport profile required')
 if config['mode']=='ISOLATED_LOOPBACK_TEST':return None
 # Dedicated operator-provided key pair; never system trust or an inferred endpoint.
 certificate=config['tlsCertificate'];key=config['tlsPrivateKey'];private(certificate,16384);private(key,16384)
 context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);context.minimum_version=ssl.TLSVersion.TLSv1_2;context.load_cert_chain(certificate,key);return context

def close_socket(connection):
 try:connection.shutdown(socket.SHUT_RDWR)
 except OSError:pass
 connection.close()

class Server(http.server.ThreadingHTTPServer):
 daemon_threads=True;request_queue_size=4
 def __init__(self,bind,provider,tls=None):
  self.tls=tls;self.capacity=threading.BoundedSemaphore(2);self.connections=threading.Condition();self.active={};self.closing=False
  super().__init__(bind,Handler);self.provider=provider
 def active_connections(self):
  with self.connections:return tuple(self.active.values())
 def process_request(self,request,address):
  if not self.capacity.acquire(False):request.close();return
  with self.connections:
   if self.closing:request.close();self.capacity.release();return
   self.active[request]=request
  try:super().process_request(request,address)
  except BaseException:
   close_socket(request)
   with self.connections:self.active.pop(request,None);self.connections.notify_all()
   self.capacity.release();raise
 def process_request_thread(self,request,address):
  current=request;timer=None
  try:
   expired=threading.Event()
   def expire():expired.set();close_socket(current)
   timer=threading.Timer(15,expire);timer.daemon=True;timer.start()
   request.settimeout(5)
   if self.tls is not None:
    secured=self.tls.wrap_socket(request,server_side=True,do_handshake_on_connect=False);current=secured
    with self.connections:
     self.active[request]=secured
     if self.closing or expired.is_set():close_socket(secured);return
    secured.settimeout(5);secured.do_handshake()
   self.finish_request(current,address)
  except (ssl.SSLError,OSError,ValueError):pass
  except Exception:self.handle_error(current,address)
  finally:
   try:
    if timer is not None:timer.cancel()
   finally:
    try:close_socket(current)
    finally:
     with self.connections:self.active.pop(request,None);self.connections.notify_all()
     self.capacity.release()
 def server_close(self):
  with self.connections:self.closing=True;active=tuple(self.active.values())
  for connection in active:close_socket(connection)
  super().server_close()
  with self.connections:
   if not self.connections.wait_for(lambda:not self.active,timeout=5):raise TimeoutError('Controlled connection drain exceeded bound')

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--config',required=True);parser.add_argument('--initialize',action='store_true');parser.add_argument('--grant-actor');parser.add_argument('--grant-folder',default='controlled-root');parser.add_argument('--create-folder');parser.add_argument('--grant-read',action='store_true');parser.add_argument('--grant-publish',action='store_true');args=parser.parse_args()
 config=strict(private(args.config),8192)
 tls=transport_context(config)
 threading.stack_size(262144)
 os.sched_setaffinity(0,sorted(os.sched_getaffinity(0))[:2])
 resource.setrlimit(resource.RLIMIT_AS,(67108864,67108864))
 if args.create_folder and (args.initialize or args.grant_actor):raise RuntimeError('Separate exact destination administration required')
 provider=Provider(config,args.initialize)
 if args.create_folder:
  provider.create_child_folder(args.create_folder);provider.db.close();return
 if args.grant_actor:
  provider.administer(args.grant_actor,'controlled-library',args.grant_folder,args.grant_publish,args.grant_read);provider.db.close();return
 server=Server(('127.0.0.1',int(config['port'])),provider,tls=tls)
 ready=pathlib.Path(config['readyFile']);ready.write_bytes(canonical({'port':server.server_port,'identities':provider.ids,'store':str(provider.path),'pid':os.getpid(),'pgid':os.getpgid(0)}));os.chmod(ready,0o600)
 try:server.serve_forever(poll_interval=.2)
 finally:server.server_close();provider.db.close()
if __name__=='__main__':main()
