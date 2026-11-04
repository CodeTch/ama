"""Actual signed controlled provider/store; local protocol evidence, not company certification."""
import pathlib,time,uuid
import test_knowledge_location as location
kb=location.kb

class Governance(location.Location):
 def setUp(self):
  super().setUp();self.provider.manage(self.owner,'library',True)
 def call(self,actor,method,path,body=None):
  material=path.startswith('/v1/knowledge/material-')
  if not material and not path.startswith('/v1/knowledge/governance/'):return super().call(actor,method,path,body)
  raw=kb.canonical(body) if body is not None else b'';now=int(time.time())
  claims=dict(schema=kb.SCHEMA,issuer=self.ids['clientId'],keyId=self.ids['delegationKeyId'],audience=self.ids['providerId'],storeInstanceId=self.ids['storeInstanceId'],actorId=actor,purpose='MATERIAL_READ' if material else 'GOVERNANCE_CHANGE' if method=='POST' else 'GOVERNANCE_OBSERVE',method=method,resource=path,issuedAt=now,expiresAt=now+30,nonce=uuid.uuid4().hex,requestDigest=kb.digest(raw if method=='POST' else kb.canonical(dict(method=method,resource=path))))
  raw_claims=kb.canonical(claims);token=kb.b64(raw_claims)+'.'+kb.b64(self.app.sign(raw_claims))
  return self.provider.serve(method,path,{'authorization':'Bearer '+self.bearer,'x-controlled-delegation':token},raw)
 def command(self,**changes):
  body=dict(protocol='ama.controlled-knowledge-governance.v1',actorId=self.owner,documentId=self.document,publicationId=self.request['publicationId'],commandKey='original-command',expectedRevisionToken='0',action='WITHDRAW',reason='更正已发布内容')
  body.update(changes);return body
 def path_for(self,tail):return '/v1/knowledge/governance/documents/'+self.document+'/'+tail
 def apply(self,body=None,actor=None):return self.call(actor or self.owner,'POST',self.path_for('commands'),body or self.command())
 def state(self):return self.call(self.owner,'GET',self.path_for('state'))
 def verify(self,response,schema):
  self.assertEqual({'providerId','storeInstanceId','assertion'},set(response));raw,signature=response['assertion'].split('.');raw=kb.unb64(raw);self.provider.key.public_key().verify(kb.unb64(signature),raw);claims=kb.strict(raw,8192);self.assertEqual(schema,claims['schema']);self.assertEqual(self.document,claims['documentId']);self.assertEqual(self.request['publicationId'],claims['publicationId']);return claims
 def test_authority_is_fresh_signed_and_bound_to_actual_document(self):
  value=self.verify(self.state(),'ama.controlled-kb-governance-state.v1');self.assertEqual('ACTIVE',value['state']);self.assertEqual('0',value['revisionToken']);self.assertLessEqual(value['expiresAt']-value['issuedAt'],5)
  with self.assertRaises(kb.Denied):self.call('stranger','GET',self.path_for('state'))
 def test_original_command_receipt_is_durable_and_changed_request_conflicts(self):
  first=self.apply();claims=self.verify(first,'ama.controlled-kb-governance-receipt.v1');self.assertEqual('WITHDRAWN',claims['state']);self.assertEqual('1',claims['revisionToken']);self.assertEqual(first,self.apply())
  lookup=self.call(self.owner,'GET',self.path_for('commands/original-command'));self.assertEqual(first,lookup)
  for change in ({'reason':'另一理由'},{'action':'OBSOLETE'},{'expectedRevisionToken':'1'}):
   with self.assertRaises(kb.Denied) as failure:self.apply(self.command(**change))
   self.assertEqual(409,failure.exception.status)
 def test_terminal_state_denies_content_and_location_and_cannot_be_restored(self):
  self.apply();self.assertEqual('WITHDRAWN',self.verify(self.state(),'ama.controlled-kb-governance-state.v1')['state'])
  for path in [self.path,'/v1/knowledge/documents/'+self.document]:
   with self.assertRaises(kb.Denied) as failure:self.call(self.owner,'GET',path)
   self.assertEqual(403,failure.exception.status)
  for action in ('ACTIVE','OBSOLETE','WITHDRAW'):
   with self.assertRaises(kb.Denied):self.apply(self.command(commandKey='other',expectedRevisionToken='1',action=action))
 def test_current_space_management_permission_and_exact_actor_are_required(self):
  with self.assertRaises(kb.Denied):self.apply(self.command(actorId=self.viewer),self.viewer)
  self.provider.manage(self.owner,'library',False)
  with self.assertRaises(kb.Denied):self.apply()
  self.assertEqual('ACTIVE',self.verify(self.state(),'ama.controlled-kb-governance-state.v1')['state'])
 def test_obsolete_survives_provider_reopen_and_old_command_stays_identical(self):
  body=self.command(action='OBSOLETE');original=self.apply(body);root=self.provider.path.parent;self.provider.db.close()
  config=dict(identities=self.ids,bearerVerifier=str(root/'bearer'),delegationPublic=str(root/'delegation'),assertionPrivate=str(root/'assertion'),store=str(root/'provider.sqlite'))
  self.provider=kb.Provider(config);self.addCleanup(self.provider.db.close);self.assertEqual(original,self.apply(body));self.assertEqual('OBSOLETE',self.verify(self.state(),'ama.controlled-kb-governance-state.v1')['state'])

 def test_withdrawn_document_disappears_from_material_search_and_cannot_feed_new_ai_input(self):
  listing='/v1/knowledge/material-libraries/library/documents?limit=20'
  content='/v1/knowledge/material-documents/'+self.document+'?libraryId=library&version='+self.request['payloadSha256']
  self.assertEqual(self.document,self.call(self.viewer,'GET',listing)['items'][0]['id']);self.assertIn('payload',self.call(self.viewer,'GET',content))
  self.apply();self.assertEqual([],self.call(self.viewer,'GET',listing)['items'])
  with self.assertRaises(kb.Denied) as failure:self.call(self.viewer,'GET',content)
  self.assertEqual(403,failure.exception.status)
 def test_bad_versions_extra_fields_and_empty_reasons_have_no_effect(self):
  for changes in ({'expectedRevisionToken':'01'},{'expectedRevisionToken':'1'},{'reason':' '},{'reason':'x'*2049},{'extra':'field'}):
   with self.assertRaises(kb.Denied):self.apply(self.command(**changes))
   self.assertEqual('ACTIVE',self.verify(self.state(),'ama.controlled-kb-governance-state.v1')['state'])

 def test_non_object_wire_body_is_rejected_without_an_internal_error(self):
  for body in ([],1,'text',None):
   with self.assertRaises(kb.Denied) as failure:self.call(self.owner,'POST',self.path_for('commands'),body)
   self.assertEqual(400,failure.exception.status)
