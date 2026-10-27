import json,pathlib,sys,tempfile,time,unittest
from unittest.mock import patch
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
import bl5_prepare_flyway as m
class FlywayRepairTests(unittest.TestCase):
 def test_nonempty_or_already_created_schema_rejected_before_claim_and_process(self):
  for responses in [['1'],['0','ama_bl5_5d9729e1a748_local\nama_bl5_5d9729e1a748_local2\nama_bl5_5d9729e1a748_s3\n']]:
   with tempfile.TemporaryDirectory() as d:
    root=pathlib.Path(d);plan={'id':'fixture','mysql':{'schemaPrefix':'ama_bl5_5d9729e1a748'}}
    (root/'clock.json').write_text(json.dumps({'owner':'fixture','maximumSeconds':10800,'startedMonotonic':time.monotonic(),'bootId':pathlib.Path('/proc/sys/kernel/random/boot_id').read_text().strip()}));(root/'preparation-consumed.json').write_text('{}');(root/'resources.json').write_text(json.dumps({'mysql':{'id':'owned-mysql'},'s3':{'id':'owned-s3'}}));(root/'result.json').write_text(json.dumps({'runtimeArtifacts':{},'artifactDirectories':{},'exit':0,'stable':True,'source':{}}))
    with patch.object(m.r,'owned_root',return_value=root),patch.object(m.monitor,'admitted'),patch.object(m,'BUILD',root),patch.object(m.r,'verify_execution'),patch.object(m.r,'inspect'),patch.object(m.preparation,'sql',side_effect=responses),patch.object(m.r,'write_new') as write,patch.object(m.subprocess,'Popen') as process:
     with self.assertRaises(ValueError):m.prepare(plan)
     write.assert_not_called();process.assert_not_called()
if __name__=='__main__':unittest.main()
