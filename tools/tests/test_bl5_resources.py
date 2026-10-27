import sys
import importlib.util,json,pathlib,unittest
from unittest.mock import patch
import tempfile,time
ROOT=pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]));spec=importlib.util.spec_from_file_location('bl5_resources',ROOT/'tools/bl5_resources.py');m=importlib.util.module_from_spec(spec);sys.modules[spec.name]=m;spec.loader.exec_module(m)
class ResourceBoundaryTests(unittest.TestCase):
 def plan(self):return json.loads(m.PLAN.read_text())
 def test_current_plan_is_new_root_with_exact_three_hour_cap(self):
  p=self.plan();self.assertEqual(m.owned_root(p),ROOT/'.tools'/p['id']);self.assertEqual(p['lifetimeHours'],3)
 def test_foreign_or_parent_resource_roots_are_rejected(self):
  for bad in [str(ROOT),str(ROOT/'.tools'),'/tmp/old']:
   p=self.plan();p['root']=bad
   with self.assertRaises(ValueError):m.owned_root(p)
 def test_no_human_or_stale_plan_cannot_activate(self):
  p=self.plan()
  with self.assertRaisesRegex(ValueError,'Human authorization'):m.gate(p,{}, {},'hash')
  auth={'status':'APPROVED_WITH_THREE_HOUR_CAP','planSha256':'old','owner':p['id'],'runtimeSecondsMaximum':10800}
  with self.assertRaisesRegex(ValueError,'Human authorization'):m.gate(p,auth,{},'hash')
 def test_human_scope_does_not_bypass_independent_harness_gate(self):
  p=self.plan();auth={'status':'APPROVED_WITH_THREE_HOUR_CAP','planSha256':'hash','owner':p['id'],'runtimeSecondsMaximum':10800}
  with self.assertRaisesRegex(ValueError,'Independent'):m.gate(p,auth,{},'hash')
 def test_retained_or_expanded_limits_are_rejected(self):
  for key,value in [('retainedAccess',True),('schemaResetDrop',True),('lifetimeHours',6)]:
   p=self.plan();p[key]=value
   with self.assertRaisesRegex(ValueError,'boundary'):m.gate(p,{}, {},'hash')
 def test_manual_stop_drains_owned_processes_before_container_work(self):
  with tempfile.TemporaryDirectory() as directory,patch.object(m,'owned_root',return_value=pathlib.Path(directory)),patch.object(m,'drain_processes') as drain,patch.object(m,'inspect') as inspect:
   m.stop(self.plan(),'unused-docker')
   drain.assert_called_once();inspect.assert_not_called()
 def test_recycled_process_identity_is_never_signalled(self):
  with patch.object(m,'registered_processes',return_value=[{'pid':12345,'startTicks':'1'}]),patch.object(m,'process_matches',return_value=False),patch.object(m.os,'kill') as kill,patch.object(m.time,'sleep'):
   m.drain_processes(self.plan());kill.assert_not_called()
 def test_matching_process_gets_bounded_term_then_kill(self):
  with patch.object(m,'registered_processes',return_value=[{'pid':12345,'startTicks':'1'}]),patch.object(m,'process_matches',return_value=True),patch.object(m.os,'kill') as kill,patch.object(m.time,'sleep'):
   m.drain_processes(self.plan());self.assertEqual([c.args for c in kill.call_args_list],[(12345,m.signal.SIGTERM),(12345,m.signal.SIGKILL)])
 def test_changed_byte_or_added_runtime_resource_fails_execution_gate(self):
  with tempfile.TemporaryDirectory() as directory:
   root=pathlib.Path(directory);file=root/'Runner.class';file.write_bytes(b'reviewed');manifest={'artifacts':{str(file):m.sha(file)},'artifactDirectories':{str(root):['Runner.class']}}
   m.verify_execution(manifest);file.write_bytes(b'changed')
   with self.assertRaises(ValueError):m.verify_execution(manifest)
   file.write_bytes(b'reviewed');(root/'injected.properties').write_text('extra')
   with self.assertRaises(ValueError):m.verify_execution(manifest)
 def test_monitor_failure_revokes_admission_and_stops_all_owned_components(self):
  with tempfile.TemporaryDirectory() as directory:
   root=pathlib.Path(directory);plan=self.plan();clock={'owner':plan['id'],'maximumSeconds':10800,'startedMonotonic':time.monotonic(),'bootId':pathlib.Path('/proc/sys/kernel/random/boot_id').read_text().strip()};(root/'clock.json').write_text(json.dumps(clock))
   with patch.object(m,'owned_root',return_value=root),patch.object(m.bl5_monitor,'owned_usage',side_effect=ValueError('unavailable')),patch.object(m,'stop') as stop:
    m.watchdog(plan,'no-runtime');stop.assert_called_once_with(plan,'no-runtime')
   self.assertEqual(json.loads((root/'stopping.json').read_text())['reason'],'MONITOR_FAILURE:ValueError');self.assertTrue((root/'deadline-stop.json').exists())
 def test_execution_gate_rejects_parent_link_before_hashing(self):
  with tempfile.TemporaryDirectory() as directory:
   root=pathlib.Path(directory);outside=root/'outside-fixture';outside.mkdir();(outside/'Runner.class').write_bytes(b'fixture');(root/'target').symlink_to(outside,target_is_directory=True);file=root/'target/Runner.class'
   with patch.object(m,'sha') as sha:
    with self.assertRaisesRegex(ValueError,'ancestor'):m.verify_execution({'artifacts':{str(file):'unused'},'artifactDirectories':{str(root/'target'):['Runner.class']}})
    sha.assert_not_called()
class EmptyStartupResumeTests(unittest.TestCase):
 def fixture(self,root):
  plan={'id':'test-owner','evidence':str(root/'evidence')};base=root/'owned';base.mkdir();evidence=root/'evidence';evidence.mkdir()
  clock={'owner':plan['id'],'maximumSeconds':10800,'startedMonotonic':time.monotonic()-60,'bootId':pathlib.Path('/proc/sys/kernel/random/boot_id').read_text().strip()}
  values={'clock.json':clock,'credentials.json':{'mysql':'synthetic'},'watchdog.json':{'pid':999999999,'owner':plan['id']},'stopping.json':{'owner':plan['id'],'reason':'MONITOR_FAILURE:ValueError'},'deadline-stop.json':{'owner':plan['id'],'reason':'MONITOR_FAILURE:ValueError','failures':[],'retained':True}}
  for n,v in values.items():(base/n).write_text(json.dumps(v))
  (base/'watchdog.log').write_text('stopped');(evidence/'clock.json').write_text(json.dumps(clock));return plan,base
 def test_preserves_original_clock_credentials_and_all_failure_evidence(self):
  with tempfile.TemporaryDirectory() as d:
   plan,base=self.fixture(pathlib.Path(d));clock=(base/'clock.json').read_bytes();credentials=(base/'credentials.json').read_bytes()
   with patch.object(m,'owned_root',return_value=base),patch.object(m,'W',pathlib.Path(d)),patch.object(m.os,'geteuid',return_value=0):m.resume_empty_startup(plan)
   self.assertEqual(clock,(base/'clock.json').read_bytes());self.assertEqual(credentials,(base/'credentials.json').read_bytes());self.assertTrue((base/'startup-failure01/stopping.json').exists());self.assertFalse((base/'stopping.json').exists())
   with patch.object(m,'owned_root',return_value=base),patch.object(m,'W',pathlib.Path(d)),patch.object(m.os,'geteuid',return_value=0):
    with self.assertRaises(ValueError):m.resume_empty_startup(plan)
 def test_any_resource_intent_or_elapsed_deadline_rejects_before_mutation(self):
  for bad in ['intent','deadline']:
   with tempfile.TemporaryDirectory() as d:
    plan,base=self.fixture(pathlib.Path(d))
    if bad=='intent':(base/'mysql').mkdir();(base/'mysql/create-intent.json').write_text('{}')
    else:
     p=base/'clock.json';v=json.loads(p.read_text());v['startedMonotonic']-=10800;p.write_text(json.dumps(v))
    with patch.object(m,'owned_root',return_value=base),patch.object(m,'W',pathlib.Path(d)),patch.object(m.os,'geteuid',return_value=0):
     with self.assertRaises(ValueError):m.resume_empty_startup(plan)
    self.assertFalse((base/'resume-empty-consumed.json').exists());self.assertTrue((base/'stopping.json').exists())
class EmptyResumeEntrypointTests(unittest.TestCase):
 def test_rejected_resume_never_enters_cleanup(self):
  for uid in [0,1000]:
   with tempfile.TemporaryDirectory() as d:
    root=pathlib.Path(d);owned=root/'owned';owned.mkdir();(owned/'resources.json').write_text('{}');config=root/'config.json';config.write_text(json.dumps({'evidence':str(root/'evidence')}))
    with patch.object(m,'PLAN',config),patch.object(m,'AUTH',config),patch.object(m,'REVIEW',config),patch.object(m,'gate'),patch.object(m.shutil,'which',return_value='unused-docker'),patch.object(m,'owned_root',return_value=owned),patch.object(m.os,'geteuid',return_value=uid),patch.object(m,'stop') as stop,patch.object(sys,'argv',['bl5_resources.py','resume-empty-startup']):
     with self.assertRaises(ValueError):m.main()
     stop.assert_not_called()
    self.assertEqual({p.name for p in owned.iterdir()},{'resources.json'})
if __name__=='__main__':unittest.main()
