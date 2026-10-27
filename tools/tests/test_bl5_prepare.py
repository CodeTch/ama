import pathlib,sys,unittest
from unittest.mock import patch
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
import bl5_prepare as m
class PreparationBoundaryTests(unittest.TestCase):
 def test_version_order_keeps_parent_before_submigrations(self):
  names=['V013_4__d.sql','V013__base.sql','V011_1__index.sql','V011__base.sql']
  self.assertEqual(sorted(names,key=m.migration_order),['V011__base.sql','V011_1__index.sql','V013__base.sql','V013_4__d.sql'])
 def test_foreign_schema_is_rejected_before_any_client_runs(self):
  with patch.object(m.subprocess,'run') as run:
   for name in ['mysql','ama_task11','ama_bl5_abc_local;DROP DATABASE x','ama_bl5_abc_retained']:
    with self.assertRaises(ValueError):m.sql('docker','owned',name,'SELECT 1')
   run.assert_not_called()
