import pathlib,sys,tempfile,unittest
from unittest.mock import patch
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
import bl5_execution as m
class ExecutionScopeTests(unittest.TestCase):
 def test_empty_entries_never_become_current_directory(self):
  project=pathlib.Path('/owned/project');target=project/'backend/api/target/classes'
  self.assertEqual(m.classpath_entries(':'+str(target)+'::',project),[target])
 def test_all_paths_rejected_before_any_enumeration(self):
  project=pathlib.Path('/owned/project')
  for foreign in ['/home/tanren/ama/.worktrees/phase-b-completion','/old/private/keys','relative','/owned/project/backend/api/target/../../../old']:
   with patch.object(pathlib.Path,'rglob') as walk,patch.object(pathlib.Path,'read_bytes') as read:
    with self.assertRaises(ValueError):m.collect(str(project/'backend/api/target/classes')+':'+foreign,project)
    walk.assert_not_called();read.assert_not_called()
 def test_inventory_captures_only_explicit_new_output(self):
  with tempfile.TemporaryDirectory() as directory:
   project=pathlib.Path(directory);classes=project/'backend/api/target/classes';classes.mkdir(parents=True);(classes/'Example.class').write_bytes(b'class bytes')
   cp,artifacts,dirs=m.collect(':'+str(classes)+':',project);self.assertEqual(cp,str(classes));self.assertEqual(list(artifacts),[str(classes/'Example.class')]);self.assertEqual(dirs,{str(classes):['Example.class']})

 def test_ancestor_symlink_is_rejected_before_external_fixture_bytes_are_read(self):
  with tempfile.TemporaryDirectory() as directory:
   root=pathlib.Path(directory);project=root/'project';api=project/'backend/api';api.mkdir(parents=True);outside=root/'outside-fixture';classes=outside/'classes';classes.mkdir(parents=True);(classes/'Sentinel.class').write_bytes(b'not-in-approved-output');(api/'target').symlink_to(outside,target_is_directory=True)
   with patch.object(pathlib.Path,'read_bytes') as read:
    with self.assertRaisesRegex(ValueError,'ancestor'):m.collect(str(api/'target/classes'),project)
    read.assert_not_called()
