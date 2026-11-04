"""Publication picker metadata is provider data, with independent publish/read grants."""
import time,uuid,unittest
from test_knowledge_location import Location,kb

class PublicationConfiguration(Location):
 def call(self,actor,method,path,body=None):
  if not path.startswith('/v1/knowledge/libraries'):return super().call(actor,method,path,body)
  raw=b'';now=int(time.time());claims=dict(schema=kb.SCHEMA,issuer=self.ids['clientId'],keyId=self.ids['delegationKeyId'],audience=self.ids['providerId'],storeInstanceId=self.ids['storeInstanceId'],actorId=actor,purpose='DESTINATION_READ',method=method,resource=path,issuedAt=now,expiresAt=now+30,nonce=uuid.uuid4().hex,requestDigest=kb.digest(kb.canonical(dict(method=method,resource=path))));encoded=kb.canonical(claims)
  return self.provider.serve(method,path,{'authorization':'Bearer '+self.bearer,'x-controlled-delegation':kb.b64(encoded)+'.'+kb.b64(self.app.sign(encoded))},raw)
 def test_picker_retains_read_only_libraries_and_denies_selection(self):
  page=self.call(self.viewer,'GET','/v1/knowledge/libraries?limit=20')
  self.assertEqual(1,len(page['items']))
  library=page['items'][0];self.assertFalse(library['canPublish']);self.assertTrue(library['unavailableReason']);self.assertTrue(library['visibility'])
  with self.assertRaises(kb.Denied):self.call(self.viewer,'GET','/v1/knowledge/libraries/library/configuration?folderId=folder')
 def test_exact_configuration_returns_validated_path_and_server_tag_rules(self):
  value=self.call(self.owner,'GET','/v1/knowledge/libraries/library/configuration?folderId=folder')
  self.assertEqual(['Actual knowledge library','Actual root'],value['pathNames'])
  self.assertEqual([],value['pathIds']);self.assertEqual('folder',value['folderId']);self.assertGreater(value['tagRules']['maxCount'],0)
  self.provider.administer(self.owner,'library','folder',False,True)
  with self.assertRaises(kb.Denied):self.call(self.owner,'GET','/v1/knowledge/libraries/library/configuration?folderId=folder')
 def test_search_filters_before_pagination_and_never_leaks_inaccessible_library(self):
  self.provider.db.execute('INSERT INTO library VALUES(?,?,?)',('aaa-hidden','SECRET','secret-root'))
  page=self.call(self.owner,'GET','/v1/knowledge/libraries?limit=1&search=Actual')
  self.assertEqual(['library'],[x['id'] for x in page['items']]);self.assertIsNone(page['cursor'])
  page=self.call(self.owner,'GET','/v1/knowledge/libraries?limit=1&search=SECRET');self.assertEqual([],page['items'])

if __name__=='__main__':unittest.main()
