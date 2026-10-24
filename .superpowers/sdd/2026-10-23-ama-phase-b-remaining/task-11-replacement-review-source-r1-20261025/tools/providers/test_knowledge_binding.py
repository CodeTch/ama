import copy,hashlib,importlib.util,json,pathlib,unittest
ROOT=pathlib.Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('kb',ROOT/'tools/providers/controlled_knowledge.py');kb=importlib.util.module_from_spec(spec);spec.loader.exec_module(kb)
class Binding(unittest.TestCase):
 def setUp(self):
  value=json.loads((ROOT/'artifacts/phase-b/b-remaining/task-11/knowledge-canonical-cross-01/renderer-client-java-actual.json').read_text())['value']
  self.request={'protocol':'ama.controlled-knowledge-publish.v1','providerId':'CONTROLLED_KNOWLEDGE_V1','publicationId':'00000000-0000-4000-8000-000000000030','confirmationId':value['provenance']['confirmationId'],'businessKey':'00000000-0000-4000-8000-000000000030:'+value['provenance']['confirmationId'],'executionId':'00000000-0000-4000-8000-000000000040','executionNo':1,'actorId':'public-test-actor','libraryId':'library','folderId':'folder','destinationAuthorization':'public-unit-placeholder','payloadSha256':kb.digest(kb.canonical(value)),'payload':value,'bindingTransition':None}
  r=self.request;logical={k:r[k] for k in ('providerId','publicationId','confirmationId','businessKey','libraryId','folderId','payloadSha256')};logical.update(schema='ama.knowledge-publish.v1',domain='KNOWLEDGE_PUBLISH',**{k:value['provenance'][k] for k in ('sourceManifestHash','inputFingerprint','renderArtifact')});r['logicalRequestHash']=kb.digest(kb.canonical(logical));r['executionRequestHash']=kb.digest(kb.canonical({'schema':'ama.knowledge-publish-execution.v1','logicalRequestHash':r['logicalRequestHash'],'executionNo':1,'priorProofs':[]}))
 def test_original_actual_java_payload(self):self.assertEqual(kb.canonical(self.request['payload']),kb.validate_publication_binding(self.request))
 def test_tampered_logical(self):self.request['logicalRequestHash']='f'*64;self.denied()
 def test_tampered_execution(self):self.request['executionRequestHash']='f'*64;self.denied()
 def test_tampered_payload(self):self.request['payload']['meeting']['name']='changed';self.denied()
 def test_cross_final(self):self.request['confirmationId']='00000000-0000-4000-8000-000000000099';self.denied()
 def test_wrong_key(self):self.request['businessKey']='other';self.denied()
 def test_unjustified_execution_number(self):self.request['executionNo']=2;self.denied()
 def test_unknown_field(self):self.request['extra']=True;self.denied()
 def denied(self):
  with self.assertRaises(kb.Denied):kb.validate_publication_binding(self.request)
if __name__=='__main__':unittest.main()
