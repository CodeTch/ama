import importlib.util,pathlib,threading,unittest
path=pathlib.Path(__file__).resolve().parents[1]/"providers/controlled_knowledge.py"
spec=importlib.util.spec_from_file_location("b4_provider",path);kb=importlib.util.module_from_spec(spec);spec.loader.exec_module(kb)
class DB:
 def __init__(self):self.calls=[]
 def execute(self,sql,args):self.calls.append((sql,args));return self
 def fetchall(self):return []
class Discovery(unittest.TestCase):
 def provider(self):
  p=kb.Provider.__new__(kb.Provider);p.db=DB();p.lock=threading.Lock();p.ids={'providerId':'kb','storeInstanceId':'store'};p.authorize=lambda *args:{'actorId':'viewer'};return p
 def test_real_serve_decodes_library_once_before_bound_query(self):
  p=self.provider();p.serve('GET','/v1/knowledge/libraries/%E5%BA%93%25%E5%90%8D/folders?limit=1&parentFolderId=%E5%B1%82%20%26',{},b'')
  self.assertEqual(('viewer','库%名','层 &','层 &','',2),p.db.calls[0][1])
 def test_invalid_query_or_path_fails_before_any_sql(self):
  paths=['/v1/knowledge/libraries?limit=', '/v1/knowledge/libraries?limit=01','/v1/knowledge/libraries?cursor=', '/v1/knowledge/libraries?limit=1&limit=2','/v1/knowledge/libraries?actorId=other','/v1/knowledge/libraries?cursor=%FF','/v1/knowledge/libraries?cursor=%ZZ','/v1/knowledge/libraries/a%2Fb/folders','/v1/knowledge/libraries/a%252Fb/folders','/v1/knowledge/libraries/a/folders?parentFolderId=']
  for path in paths:
   with self.subTest(path=path):
    p=self.provider()
    with self.assertRaises(kb.Denied) as failure:p.serve('GET',path,{},b'')
    self.assertEqual(400,failure.exception.status);self.assertEqual([],p.db.calls)

 def test_library_route_prefix_is_exact(self):
  p=self.provider()
  with self.assertRaises(kb.Denied) as failure:p.serve('GET','/v1/knowledge/libraries-other/lib/folders',{},b'')
  self.assertEqual(404,failure.exception.status);self.assertEqual([],p.db.calls)
