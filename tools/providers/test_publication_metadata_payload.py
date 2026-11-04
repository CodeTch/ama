import unittest
from test_knowledge_binding import Binding,kb

class PublicationMetadata(Binding):
 def refresh_hashes(self):
  r=self.request;r['payloadSha256']=kb.digest(kb.canonical(r['payload']));logical={k:r[k] for k in ('providerId','publicationId','confirmationId','businessKey','libraryId','folderId','payloadSha256')};logical.update(schema='ama.knowledge-publish.v1',domain='KNOWLEDGE_PUBLISH',**{k:r['payload']['provenance'][k] for k in ('sourceManifestHash','inputFingerprint','renderArtifact')});r['logicalRequestHash']=kb.digest(kb.canonical(logical));r['executionRequestHash']=kb.digest(kb.canonical(dict(schema='ama.knowledge-publish-execution.v1',logicalRequestHash=r['logicalRequestHash'],executionNo=1,priorProofs=[])))
 def metadata(self):
  return dict(schema='ama.publication-configuration.v1',configurationVersionToken='2',providerId=self.request['providerId'],libraryId='library',folderId='folder',libraryName='知识库',description='项目纪要',visibility='项目成员可见',pathNames=['知识库','根目录'],pathIds=[],tags=['发布验收'],tagRules=dict(maxCount=3,maxLength=20,allowEmpty=True,trim=True,caseSensitive=True))
 def test_v2_metadata_is_part_of_immutable_document_and_request_hash(self):
  original=self.request['payload']['minutes']['body'];self.request['payload'].update(schemaVersion='ama.controlled-knowledge-document.v2',publicationMetadata=self.metadata());self.refresh_hashes()
  self.assertEqual(kb.canonical(self.request['payload']),kb.validate_publication_binding(self.request));self.assertEqual(original,self.request['payload']['minutes']['body'])
  self.request['payload']['publicationMetadata']['tags']=['篡改'];self.denied()
 def test_metadata_cannot_select_another_library_even_with_recomputed_hash(self):
  metadata=self.metadata();metadata['libraryId']='foreign';self.request['payload'].update(schemaVersion='ama.controlled-knowledge-document.v2',publicationMetadata=metadata);self.refresh_hashes();self.denied()

if __name__=='__main__':unittest.main()
