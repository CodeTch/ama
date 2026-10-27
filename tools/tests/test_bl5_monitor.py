import pathlib,sys,tempfile,time,unittest
from unittest.mock import patch
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
import bl5_monitor as m
class AggregateBoundaryTests(unittest.TestCase):
 def test_every_aggregate_threshold_stops_with_headroom(self):
  good={'chargedMemoryBytes':1,'ownedDiskBytes':1,'evidenceBytes':1};self.assertIsNone(m.reason(good))
  for key,limit in [('chargedMemoryBytes',m.MEMORY_STOP),('ownedDiskBytes',m.DISK_STOP),('evidenceBytes',m.EVIDENCE_STOP)]:
   self.assertEqual(m.reason({**good,key:limit}),'LIMIT_'+key);self.assertEqual(m.reason({**good,key:None}),'UNAVAILABLE_'+key)
  self.assertLess(m.MEMORY_STOP,6*1024**3);self.assertLess(m.DISK_STOP,8*1024**3);self.assertLess(m.EVIDENCE_STOP,2*1024**3)
 def test_stale_missing_or_stopping_lease_blocks_new_admissions(self):
  with tempfile.TemporaryDirectory() as directory:
   root=pathlib.Path(directory)
   with self.assertRaises(FileNotFoundError):m.admitted(root)
   m.publish(root,{});m.admitted(root)
   with patch.object(m.time,'time',return_value=time.time()+11):
    with self.assertRaises(ValueError):m.admitted(root)
   (root/'stopping.json').write_text('{}')
   with self.assertRaises(ValueError):m.admitted(root)
 def test_container_units_are_bounded_and_unknown_units_fail(self):
  self.assertEqual(m.parse_memory('1.5GiB / 2GiB'),int(1.5*1024**3)+1024**2)
  with self.assertRaises(ValueError):m.parse_memory('unavailable')
