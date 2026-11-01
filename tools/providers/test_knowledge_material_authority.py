"""Fresh exact-version read authority, real signed temporary provider; not company certification."""
import time,uuid
import test_knowledge_location as location
kb=location.kb

class MaterialAuthority(location.Location):
 def call(self,actor,method,path,body=None):
  if path!='/v1/knowledge/material-authorizations':return super().call(actor,method,path,body)
  raw=kb.canonical(body);now=int(time.time());claims=dict(schema=kb.SCHEMA,issuer=self.ids['clientId'],keyId=self.ids['delegationKeyId'],audience=self.ids['providerId'],storeInstanceId=self.ids['storeInstanceId'],actorId=actor,purpose='MATERIAL_READ',method=method,resource=path,issuedAt=now,expiresAt=now+30,nonce=uuid.uuid4().hex,requestDigest=kb.digest(raw))
  encoded=kb.canonical(claims);token=kb.b64(encoded)+'.'+kb.b64(self.app.sign(encoded))
  return self.provider.serve(method,path,{'authorization':'Bearer '+self.bearer,'x-controlled-delegation':token},raw)
 def item(self,**changes):return dict(libraryId='library',documentId=self.document,version=self.request['payloadSha256'],**changes)
 def proof(self,items=None,actor=None,**changes):
  actor=actor or self.viewer;body=dict(protocol='ama.controlled-material-authority.v1',actorId=actor,items=items if items is not None else [self.item()]);body.update(changes)
  return self.call(actor,'POST','/v1/knowledge/material-authorizations',body)
 def test_signed_batch_is_bound_to_exact_current_read_only_actor_and_versions(self):
  response=self.proof();self.assertEqual({'providerId','storeInstanceId','assertion'},set(response));raw,sig=response['assertion'].split('.');raw=kb.unb64(raw);self.provider.key.public_key().verify(kb.unb64(sig),raw);claims=kb.strict(raw,6144)
  self.assertEqual(dict(schema='ama.controlled-material-authority.v1',**self.ids,actorId=self.viewer,itemsSha256=kb.digest(kb.canonical([self.item()])),issuedAt=claims['issuedAt'],expiresAt=claims['expiresAt']),claims);self.assertEqual(5,claims['expiresAt']-claims['issuedAt']);self.assertGreater(claims['expiresAt'],time.time())
 def test_any_missing_wrong_version_or_revoked_item_denies_whole_batch(self):
  self.proof()
  for changed,status in [({'documentId':'missing'},404),({'version':'0'*64},409),({'libraryId':'missing'},404)]:
   bad={**self.item(),**changed}
   with self.assertRaises(kb.Denied) as error:self.proof([self.item(),bad])
   self.assertEqual(status,error.exception.status)
  self.provider.administer(self.viewer,'library','folder',False,False)
  with self.assertRaises(kb.Denied) as error:self.proof()
  self.assertEqual(403,error.exception.status)
 def test_terminal_document_never_gets_fresh_material_authority(self):
  self.proof();self.provider.db.execute("INSERT INTO document_governance VALUES(?,'WITHDRAWN',1,?,?)",(self.document,uuid.uuid4().hex,int(time.time())))
  with self.assertRaises(kb.Denied) as error:self.proof()
  self.assertEqual(403,error.exception.status)
 def test_bounded_exact_shape_duplicate_items_and_other_actor_fail_closed(self):
  for items in ([],[self.item()]*2,[self.item()]*65,[{**self.item(),'extra':'value'}],[None],[{**self.item(),'version':'BAD'}]):
   with self.assertRaises(kb.Denied) as error:self.proof(items)
   self.assertEqual(400,error.exception.status)
  for changed in ({'actorId':self.owner},{'protocol':'other'},{'extra':True}):
   with self.assertRaises(kb.Denied):self.proof(**changed)
