import contextlib,importlib.util,io,json,pathlib,subprocess,tempfile,unittest
from unittest.mock import patch

def runner():
 spec=importlib.util.spec_from_file_location("owned_runner",pathlib.Path(__file__).resolve().parents[1]/"run.py");m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m

class OwnedOutputTests(unittest.TestCase):
 def test_web_report_is_new_and_old_evidence_unchanged(self):
  r=runner()
  with tempfile.TemporaryDirectory() as d:
   r.ROOT=pathlib.Path(d);src=r.ROOT/"web/tests/unit/one.test.ts";src.parent.mkdir(parents=True);src.write_text("test");old=r.ROOT/"artifacts/web/web.xml";old.parent.mkdir(parents=True);old.write_text("historical")
   def run(command,cwd,reports):
    self.assertEqual([r.ROOT/"fresh/web.xml"],reports);self.assertIn("--test-reporter-destination="+str(reports[0]),command);reports[0].write_text("new");return 0
   with patch.object(r,"execute",side_effect=run):self.assertEqual(0,r.main(["web","one.test.ts","--output-root",str(r.ROOT/"fresh")]))
   self.assertEqual("historical",old.read_text());self.assertEqual("new",(r.ROOT/"fresh/web.xml").read_text())
 def test_existing_and_escaped_roots_fail_without_launch_or_mutation(self):
  r=runner()
  with tempfile.TemporaryDirectory() as d,tempfile.TemporaryDirectory() as outside:
   r.ROOT=pathlib.Path(d);existing=r.ROOT/"existing";existing.mkdir();(existing/"keep").write_text("old");(r.ROOT/"link").symlink_to(outside,target_is_directory=True)
   for target in (existing,pathlib.Path(outside)/"new",r.ROOT/"link/new"):
    with patch.object(r,"web_tests") as launch,contextlib.redirect_stderr(io.StringIO()):self.assertNotEqual(0,r.main(["web","*","--output-root",str(target)]));launch.assert_not_called()
   self.assertEqual("old",(existing/"keep").read_text());self.assertFalse((pathlib.Path(outside)/"new").exists())
 def test_contract_lint_and_bundle_use_owned_destination(self):
  r=runner()
  with tempfile.TemporaryDirectory() as d:
   r.ROOT=pathlib.Path(d);commands=[]
   def run(command,**kwargs):
    commands.append(command)
    if "bundle" in command:pathlib.Path(command[-1]).write_text("openapi: 3.1.0")
    return subprocess.CompletedProcess(command,0)
   with patch.object(r.subprocess,"run",side_effect=run):self.assertEqual(0,r.main(["contract","OpenApi","--output-root",str(r.ROOT/"fresh")]))
   self.assertEqual(2,len(commands));self.assertIn("lint",commands[0]);self.assertIn("bundle",commands[1]);self.assertEqual(str(r.ROOT/"fresh/api.yaml"),commands[1][-1]);self.assertNotIn("check:openapi",str(commands))
 def test_browser_command_routes_report_and_artifacts_to_owned_root(self):
  r=runner()
  with tempfile.TemporaryDirectory() as d:
   r.ROOT=pathlib.Path(d);src=r.ROOT/"web/tests/e2e/one.spec.ts";src.parent.mkdir(parents=True);src.write_text("test")
   def run(command,cwd,reports):self.assertIn("AMA_CHECK_OUTPUT_ROOT="+str(r.ROOT/"fresh"),command);self.assertIn("PLAYWRIGHT_JUNIT_OUTPUT_FILE="+str(r.ROOT/"fresh/e2e.xml"),command);return 0
   with patch.object(r,"execute",side_effect=run):self.assertEqual(0,r.main(["e2e","one.spec.ts","--output-root",str(r.ROOT/"fresh")]))
   marker=json.loads((r.ROOT/"fresh/owned-output.json").read_text());self.assertEqual(str(r.ROOT),marker["repository"]);self.assertEqual("e2e",marker["group"])
 def test_unsupported_groups_do_not_claim_isolation(self):
  r=runner()
  with tempfile.TemporaryDirectory() as d:
   r.ROOT=pathlib.Path(d)
   with contextlib.redirect_stderr(io.StringIO()):self.assertNotEqual(0,r.main(["unit","AnyTest","--output-root",str(r.ROOT/"fresh")]))
   self.assertFalse((r.ROOT/"fresh").exists())

 def test_successful_contract_process_without_bundle_is_not_a_pass(self):
  r=runner()
  with tempfile.TemporaryDirectory() as d:
   r.ROOT=pathlib.Path(d)
   with patch.object(r.subprocess,"run",return_value=subprocess.CompletedProcess([],0)),contextlib.redirect_stderr(io.StringIO()):self.assertNotEqual(0,r.main(["contract","OpenApi","--output-root",str(r.ROOT/"fresh")]))

 def test_explicit_empty_output_never_falls_back_to_historical_artifacts(self):
  r=runner()
  with tempfile.TemporaryDirectory() as d:
   r.ROOT=pathlib.Path(d);old=r.ROOT/"artifacts/web/web.xml";bundle=r.ROOT/"artifacts/openapi/api.yaml";old.parent.mkdir(parents=True);bundle.parent.mkdir(parents=True);old.write_text("old report");bundle.write_text("old bundle")
   for args in (["web","*"],["e2e","*"],["visual","*"],["contract","OpenApi"]):
    with patch.object(r,"web_tests",return_value=0) as web,patch.object(r,"contracts",return_value=0) as contracts,contextlib.redirect_stderr(io.StringIO()):
     self.assertNotEqual(0,r.main(args+["--output-root",""]));web.assert_not_called();contracts.assert_not_called()
   self.assertEqual("old report",old.read_text());self.assertEqual("old bundle",bundle.read_text())
