"""Temporary real signed provider/store fixtures; no live fixture or caller-auth stub."""
import copy,json,pathlib,tempfile,time,unittest,uuid
from cryptography.hazmat.primitives import serialization
import test_knowledge_binding as binding
kb=binding.kb

class Location(unittest.TestCase):
 def setUp(self):
  binding.Binding.setUp(self);self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);root=pathlib.Path(self.temp.name);root.chmod(0o700)
  self.app=kb.Ed25519PrivateKey.generate();provider=kb.Ed25519PrivateKey.generate()
  def private(name,raw):
   path=root/name;path.write_bytes(raw);path.chmod(0o600);return str(path)
  der=lambda key:key.private_bytes(serialization.Encoding.DER,serialization.PrivateFormat.PKCS8,serialization.NoEncryption())
  ids=dict(providerId=self.request['providerId'],storeInstanceId='location-store',clientId='location-client',delegationKeyId='location-delegation',assertionKeyId='location-assertion');self.ids=ids;self.bearer='A'*43
  config=dict(identities=ids,bearerVerifier=private('bearer',kb.digest(self.bearer.encode()).encode()),delegationPublic=private('delegation',self.app.public_key().public_bytes(serialization.Encoding.DER,serialization.PublicFormat.SubjectPublicKeyInfo)),assertionPrivate=private('assertion',der(provider)),store=str(root/'provider.sqlite'))
  self.provider=kb.Provider(config,initialize=True);self.addCleanup(self.provider.db.close);db=self.provider.db
  db.execute('INSERT INTO library VALUES(?,?,?)',('library','Actual knowledge library','folder'));db.execute('INSERT INTO folder VALUES(?,?,NULL,?,1)',('folder','library','Actual root'))
  self.owner=self.request['actorId'];self.viewer='actual-read-only-member';self.provider.administer(self.owner,'library','folder',True,True);self.provider.administer(self.viewer,'library','folder',False,True)
  grant=self.call(self.owner,'POST','/v1/knowledge/destination-authorizations',dict(actorId=self.owner,providerId=ids['providerId'],libraryId='library',folderId='folder',context=self.request['publicationId']))
  self.request['destinationAuthorization']=grant['assertion'];receipt=self.call(self.owner,'POST','/v1/knowledge/publications',self.request);self.document=receipt['documentId'];self.path='/v1/knowledge/documents/'+self.document+'?view=location'
 def call(self,actor,method,path,body=None):
  raw=kb.canonical(body) if body is not None else b'';now=int(time.time());claims=dict(schema=kb.SCHEMA,issuer=self.ids['clientId'],keyId=self.ids['delegationKeyId'],audience=self.ids['providerId'],storeInstanceId=self.ids['storeInstanceId'],actorId=actor,purpose='DOCUMENT_READ' if method=='GET' else 'PUBLISH' if path=='/v1/knowledge/publications' else 'DESTINATION_AUTHORIZE',method=method,resource=path,issuedAt=now,expiresAt=now+30,nonce=uuid.uuid4().hex,requestDigest=kb.digest(raw if method=='POST' else kb.canonical(dict(method=method,resource=path))));encoded=kb.canonical(claims);token=kb.b64(encoded)+'.'+kb.b64(self.app.sign(encoded))
  return self.provider.serve(method,path,{'authorization':'Bearer '+self.bearer,'x-controlled-delegation':token},raw)
 def read(self):return self.call(self.viewer,'GET',self.path)
 def test_read_only_member_gets_actual_location_and_canonical_version_without_publish_grant(self):
  value=self.read();self.assertEqual('Actual knowledge library',value['libraryName']);self.assertEqual(['Actual root'],value['folderPath']);self.assertEqual(self.viewer,value['viewerId']);self.assertEqual(self.document,value['documentId']);self.assertEqual(self.request['payloadSha256'],value['payloadSha256']);self.assertNotIn('payload',value);self.assertEqual(kb.digest(kb.canonical({k:v for k,v in value.items() if k!='locationVersion'})),value['locationVersion']);self.assertEqual('1',value['readGrantVersionToken'])
  with self.assertRaises(kb.Denied):self.provider.grant(self.viewer,'library','folder',False)
 def test_metadata_or_current_grant_change_changes_version_and_revocation_denies(self):
  original=self.read();self.provider.db.execute('UPDATE library SET name=? WHERE id=?',('Renamed actual library','library'));changed=self.read();self.assertNotEqual(original['locationVersion'],changed['locationVersion']);self.provider.administer(self.viewer,'library','folder',False,True);grant=self.read();self.assertNotEqual(changed['locationVersion'],grant['locationVersion']);self.assertEqual('2',grant['readGrantVersionToken']);self.provider.administer(self.viewer,'library','folder',False,False)
  with self.assertRaises(kb.Denied) as error:self.read()
  self.assertEqual(403,error.exception.status)
 def test_missing_document_and_unknown_or_duplicate_query_never_return_metadata(self):
  for path in ['/v1/knowledge/documents/doc-'+'0'*32+'?view=location',self.path+'&view=location',self.path+'&extra=1',self.path.replace('location','other')]:
   with self.assertRaises(kb.Denied):self.call(self.viewer,'GET',path)
 def test_corrupt_folder_chain_cannot_invent_a_path(self):
  self.provider.db.execute('UPDATE folder SET parent=id,is_root=0 WHERE id=?',('folder',))
  with self.assertRaises(kb.Denied):self.read()

if __name__=='__main__':unittest.main()
