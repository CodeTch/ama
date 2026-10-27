import json,pathlib,sys,tempfile,time,unittest
from unittest.mock import patch
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
import bl5_prepare_s3_repair as m
class S3RepairBoundaryTests(unittest.TestCase):
 def test_active_workload_or_fourth_schema_blocks_before_claim(self):
  for live in [True,False]:
   with tempfile.TemporaryDirectory() as d:
    root=pathlib.Path(d);plan={'id':'fixture','mysql':{'schemaPrefix':'ama_bl5_5d9729e1a748'}}
    (root/'clock.json').write_text(json.dumps({'owner':'fixture','maximumSeconds':10800,'startedMonotonic':time.monotonic(),'bootId':pathlib.Path('/proc/sys/kernel/random/boot_id').read_text().strip()}))
    for mode in ['local','service-s3']:(root/mode).mkdir();(root/mode/'harness-stopped.json').write_text(json.dumps({'errors':[],'dataRetained':True}))
    (root/'resources.json').write_text(json.dumps({'mysql':{'id':'owned-mysql'},'s3':{'id':'owned-s3'}}));(root/'result.json').write_text(json.dumps({'runtimeArtifacts':{},'artifactDirectories':{},'exit':0,'stable':True,'source':{}}))
    names='\n'.join('ama_bl5_5d9729e1a748'+s for s in ['_local','_local2','_s3','_s3r2'])
    with patch.object(m.r,'owned_root',return_value=root),patch.object(m.monitor,'admitted'),patch.object(m.r,'registered_processes',return_value=[{'pid':123}] if live else []),patch.object(m.r,'process_matches',return_value=live),patch.object(m,'BUILD',root),patch.object(m.r,'verify_execution'),patch.object(m.r,'inspect'),patch.object(m.preparation,'sql',return_value=names),patch.object(m.r,'write_new') as write,patch.object(m.subprocess,'Popen') as process:
     with self.assertRaises(ValueError):m.prepare(plan)
     write.assert_not_called();process.assert_not_called()
if __name__=='__main__':unittest.main()
