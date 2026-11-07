"""Library read, publication and space governance are separate provider grants."""
from test_knowledge_governance import Governance, kb

class PublicationLibraryAccess(Governance):
 def setUp(self):
  from test_knowledge_location import Location
  Location.setUp(self)
 def test_publisher_is_not_implicitly_a_space_administrator(self):
  with self.assertRaises(kb.Denied):self.apply()
 def test_space_manager_can_govern_without_meeting_ownership_or_publish_permission(self):
  self.provider.manage(self.viewer,'library',True)
  value=self.apply(self.command(actorId=self.viewer),self.viewer)
  self.assertEqual('WITHDRAWN',self.verify(value,'ama.controlled-kb-governance-receipt.v1')['state'])
  with self.assertRaises(kb.Denied):self.call(self.viewer,'GET','/v1/knowledge/documents/'+self.document)
  records=self.call(self.viewer,'GET','/v1/knowledge/documents/index?view=manage&limit=20')
  self.assertEqual('WITHDRAWN',records['items'][0]['state']);self.assertTrue(records['items'][0]['canManage']);self.assertNotIn('payload',records['items'][0])
 def test_library_filter_is_applied_before_pagination_and_keeps_read_authority(self):
  self.provider.db.execute('INSERT INTO library VALUES(?,?,?)',('unrelated','其他资料库','unrelated-root'))
  self.provider.db.execute('INSERT INTO folder VALUES(?,?,NULL,?,1)',('unrelated-root','unrelated','其他根目录'))
  self.provider.administer(self.viewer,'unrelated','unrelated-root',False,True)
  source=dict(self.provider.db.execute('SELECT * FROM document WHERE id=?',(self.document,)).fetchone())
  for n in range(25):
   row=dict(source,id='doc-'+format(n,'032x'),publication=str(__import__('uuid').uuid4()),library='unrelated',folder='unrelated-root',result_id='unrelated-'+str(n))
   self.provider.db.execute('INSERT INTO document('+','.join(row)+') VALUES('+','.join('?' for _ in row)+')',tuple(row.values()))
  rows=self.call(self.viewer,'GET','/v1/knowledge/documents/index?view=read&limit=1&libraryId=library')
  self.assertEqual([self.document],[r['documentId'] for r in rows['items']])
  self.assertIsNone(rows['cursor'])
  self.assertEqual([],self.call(self.viewer,'GET','/v1/knowledge/documents/index?view=read&limit=1&libraryId=other')['items'])
  for suffix in ('libraryId=library&libraryId=other','libraryId=','libraryId=..','libraryId=a%2Fb'):
   with self.assertRaises(kb.Denied):self.call(self.viewer,'GET','/v1/knowledge/documents/index?view=read&limit=20&'+suffix)
 def test_reader_index_and_permissions_do_not_reveal_other_spaces(self):
  path='/v1/knowledge/documents/'+self.document+'?view=permissions'
  self.assertFalse(self.call(self.owner,'GET',path)['canManage'])
  rows=self.call(self.viewer,'GET','/v1/knowledge/documents/index?view=read&limit=20')['items'];self.assertEqual([self.document],[r['documentId'] for r in rows])
  self.assertEqual([],self.call('stranger','GET','/v1/knowledge/documents/index?view=read&limit=20')['items'])
  self.provider.manage(self.viewer,'controlled-library',True)
  self.assertEqual([],self.call(self.viewer,'GET','/v1/knowledge/documents/index?view=manage&limit=20')['items'])
  with self.assertRaises(kb.Denied):self.apply(self.command(actorId=self.viewer),self.viewer)

# Only these role-boundary cases use the inherited signed fixture; legacy governance cases
# explicitly install their own manager grant in their fixture below.
for name in list(vars(Governance)):
 if name.startswith('test_'):setattr(PublicationLibraryAccess,name,None)
