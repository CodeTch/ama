import copy,unittest
import test_knowledge_binding as binding
kb=binding.kb
class Transition(unittest.TestCase):
 def setUp(self):
  binding.Binding.setUp(self);self.provider=kb.Provider.__new__(kb.Provider);self.provider.ids={'providerId':'CONTROLLED_KNOWLEDGE_V1','storeInstanceId':'public-test-store','clientId':'public-test-client','delegationKeyId':'public-test-delegation','assertionKeyId':'public-test-assertion'}
  # Published RFC8032 vector, never a real fixture credential.
  self.provider.key=kb.Ed25519PrivateKey.from_private_bytes(bytes.fromhex('9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60'))
  receipt=self.provider.receipt(self.request,'FAILED',receiptId='receipt-'+'1'*32,verifiedAt=1,slotGeneration=1,noEffectBasis='DEFINITIVE_REJECTION',errorCode='DESTINATION_DENIED')
  claims={'schema':'ama.controlled-kb-permanent-rejection.v1',**self.provider.ids,**{k:receipt[k] for k in ('publicationId','confirmationId','businessKey','logicalRequestHash','executionRequestHash','executionId','executionNo','actorId','libraryId','folderId','payloadSha256','receiptId','verifiedAt','slotGeneration','noEffectBasis','errorCode')}};receipt['permanentRejection']=self.provider.sign(claims)
  self.predecessors=[{'id':self.request['executionId'],'request_hash':self.request['executionRequestHash'],'receipt':kb.canonical(receipt)}];self.slot={'generation':1,'document':None};self.next=copy.deepcopy(self.request);self.next['executionId']='00000000-0000-4000-8000-000000000042';self.next['executionNo']=3
  self.next['bindingTransition']={'schema':'ama.controlled-kb-binding-transition.v1','expectedGeneration':1,'priorProofs':[{'executionId':self.request['executionId'],'executionRequestHash':self.request['executionRequestHash'],'receiptId':receipt['receiptId'],'proofDigest':kb.digest(receipt['permanentRejection'].encode())}],'newBusinessKey':self.request['businessKey'],'newLogicalRequestHash':self.request['logicalRequestHash']};self.rehash()
 def rehash(self):self.next['executionRequestHash']=kb.digest(kb.canonical({'schema':'ama.knowledge-publish-execution.v1','logicalRequestHash':self.next['logicalRequestHash'],'executionNo':self.next['executionNo'],'priorProofs':self.next['bindingTransition']['priorProofs']}))
 def check(self):return self.provider.transition_generation(self.next,self.slot,self.predecessors,1)
 def test_gap_preserves_only_real_receipts(self):self.assertEqual(2,self.check());self.assertEqual(1,len(self.predecessors));self.assertEqual(1,len(self.next['bindingTransition']['priorProofs']))
 def test_used_number_cannot_rebind(self):self.next['executionNo']=1;self.rehash();self.reject_transition()
 def test_stale_generation(self):self.next['bindingTransition']['expectedGeneration']=2;self.reject_transition()
 def test_document_blocks(self):self.slot['document']='doc-existing';self.reject_transition()
 def test_missing_predecessor(self):self.next['bindingTransition']['priorProofs']=[];self.rehash();self.reject_transition()
 def test_invented_unseen_predecessor(self):self.next['bindingTransition']['priorProofs'].append({**self.next['bindingTransition']['priorProofs'][0],'executionId':'00000000-0000-4000-8000-000000000041'});self.rehash();self.reject_transition()
 def test_wrong_receipt_digest(self):self.next['bindingTransition']['priorProofs'][0]['proofDigest']='f'*64;self.rehash();self.reject_transition()
 def test_tampered_new_execution_binding(self):self.next['executionRequestHash']='f'*64;self.reject_transition()
 def test_wrong_new_key(self):self.next['bindingTransition']['newBusinessKey']='wrong';self.reject_transition()
 def test_permanent_signature_required(self):receipt=kb.strict(self.predecessors[0]['receipt'],65536);receipt['permanentRejection']=receipt['permanentRejection'][:-3]+'abc';self.predecessors[0]['receipt']=kb.canonical(receipt);self.reject_transition()
 def reject_transition(self):
  with self.assertRaises(kb.Denied):self.check()
if __name__=='__main__':unittest.main()
