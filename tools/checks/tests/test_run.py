"""Runner regressions: no selector and a failed subprocess must fail the gate."""
import importlib.util
import contextlib
import io
import json
import pathlib
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

RUNNER = pathlib.Path(__file__).resolve().parents[1] / "run.py"

def load_runner():
    spec = importlib.util.spec_from_file_location("ama_runner", RUNNER)
    runner = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(runner)
    return runner

class AggregatePythonTests(unittest.TestCase):
    def aggregate_fixture(self, source, expected_exit, expected_counts):
        runner = load_runner()
        with tempfile.TemporaryDirectory() as directory:
            runner.ROOT = pathlib.Path(directory)
            discovery = runner.ROOT / "tools/checks/tests"
            discovery.mkdir(parents=True)
            if source:
                (discovery / "test_fixture.py").write_text("import unittest\n" + source)
            report = runner.ROOT / "artifacts/checks/python.json"
            report.parent.mkdir(parents=True)
            report.write_text('{"executed":99,"failed":0,"skipped":0}')
            output = io.StringIO()
            # Isolate unrelated Java/OpenAPI stages; Python discovery and result collection remain real.
            with patch.object(runner, "contracts", return_value=0), patch.object(runner, "java_tests", return_value=0), contextlib.redirect_stdout(output), contextlib.redirect_stderr(output):
                result = runner.main(["all"])
            if expected_exit:
                self.assertNotEqual(0, result, output.getvalue())
            else:
                self.assertEqual(0, result, output.getvalue())
            self.assertEqual(expected_counts, {key: json.loads(report.read_text())[key] for key in expected_counts})
            self.assertIn("evidence=", output.getvalue())
            self.assertIn("executed=" + str(expected_counts["executed"]), output.getvalue())
            self.assertTrue(report.with_suffix(".log").exists())

    def test_aggregate_rejects_empty_python_discovery(self):
        self.aggregate_fixture("", 1, {"executed":0, "failed":0, "skipped":0})

    def test_aggregate_rejects_required_skipped_python_case(self):
        self.aggregate_fixture('class Case(unittest.TestCase):\n @unittest.skip("required dependency unavailable")\n def test_required(self): pass\n', 1, {"executed":0, "failed":0, "skipped":1})

    def test_aggregate_rejects_partial_python_skip(self):
        self.aggregate_fixture('class Case(unittest.TestCase):\n def test_pass(self): self.assertTrue(True)\n @unittest.skip("required dependency unavailable")\n def test_required(self): pass\n', 1, {"executed":1, "failed":0, "skipped":1})

    def test_aggregate_records_actual_python_failure(self):
        self.aggregate_fixture('class Case(unittest.TestCase):\n def test_failure(self): self.assertEqual(1, 2)\n', 1, {"executed":1, "failed":1, "skipped":0})

    def test_aggregate_accepts_real_python_execution_with_fresh_counts(self):
        self.aggregate_fixture('class Case(unittest.TestCase):\n def test_pass(self): self.assertEqual(2, 2)\n', 0, {"executed":1, "failed":0, "skipped":0})

    def test_python_worker_failure_preserves_exit_and_rejects_stale_evidence(self):
        runner = load_runner()
        with tempfile.TemporaryDirectory() as directory:
            runner.ROOT = pathlib.Path(directory)
            runner.PYTHON_SUITE = runner.ROOT / "failed_worker.py"
            runner.PYTHON_SUITE.write_text("raise SystemExit(17)\n")
            report = runner.ROOT / "artifacts/checks/python.json"
            report.parent.mkdir(parents=True)
            report.write_text('{"executed":99,"failed":0,"skipped":0}')
            output = io.StringIO()
            with contextlib.redirect_stdout(output), contextlib.redirect_stderr(output):
                result = runner.python_tests()
            self.assertEqual(17, result)
            self.assertFalse(report.exists())
            self.assertIn("missing fresh Python evidence", output.getvalue())

class RunnerTests(unittest.TestCase):
    def run_gate(self, *args):
        return subprocess.run([sys.executable, str(RUNNER), *args], text=True, capture_output=True)

    def check_execute(self, runner, command, directory, reports, status, diagnostic):
        output = io.StringIO()
        with contextlib.redirect_stdout(output), contextlib.redirect_stderr(output):
            result = runner.execute(command, directory, reports)
        self.assertEqual(status, result)
        self.assertIn(diagnostic, output.getvalue())

    def test_unknown_group_is_failure(self):
        self.assertNotEqual(0, self.run_gate("unknown", "AnyTest").returncode)

    def test_zero_matches_is_failure(self):
        self.assertNotEqual(0, self.run_gate("unit", "NoSuchAmaTest").returncode)

    def test_subprocess_failure_cannot_pass_with_old_success_reports(self):
        spec = importlib.util.spec_from_file_location("ama_runner", RUNNER)
        runner = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(runner)
        with tempfile.TemporaryDirectory() as directory:
            report = pathlib.Path(directory) / "TEST-old.xml"
            report.write_text('<testsuite tests="7" failures="0" errors="0" skipped="0"/>')
            self.check_execute(runner, [sys.executable, "-c", "raise SystemExit(17)"], pathlib.Path(directory), [report], 17, "FAIL: subprocess exited 17")

    def test_zero_executed_tests_is_failure(self):
        spec = importlib.util.spec_from_file_location("ama_runner", RUNNER)
        runner = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(runner)
        with tempfile.TemporaryDirectory() as directory:
            report = pathlib.Path(directory) / "TEST-empty.xml"
            self.check_execute(runner, [sys.executable, "-c", "pass"], pathlib.Path(directory), [report], 1, "FAIL: executed=0")

    def test_all_skipped_tests_is_failure(self):
        spec = importlib.util.spec_from_file_location("ama_runner", RUNNER)
        runner = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(runner)
        with tempfile.TemporaryDirectory() as directory:
            report = pathlib.Path(directory) / "TEST-skipped.xml"
            code = 'from pathlib import Path; Path("TEST-skipped.xml").write_text(\'<testsuite tests="3" failures="0" errors="0" skipped="3"/>\')'
            self.check_execute(runner, [sys.executable, "-c", code], pathlib.Path(directory), [report], 1, "skipped=3")

    def test_fresh_real_execution_is_success(self):
        spec = importlib.util.spec_from_file_location("ama_runner", RUNNER)
        runner = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(runner)
        with tempfile.TemporaryDirectory() as directory:
            report = pathlib.Path(directory) / "TEST-passed.xml"
            code = 'from pathlib import Path; Path("TEST-passed.xml").write_text(\'<testsuite tests="2" failures="0" errors="0" skipped="0"/>\')'
            self.check_execute(runner, [sys.executable, "-c", code], pathlib.Path(directory), [report], 0, "PASS: executed=2")

    def test_skipped_required_case_is_failure_even_if_others_pass(self):
        spec = importlib.util.spec_from_file_location("ama_runner", RUNNER)
        runner = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(runner)
        with tempfile.TemporaryDirectory() as directory:
            report = pathlib.Path(directory) / "TEST-mixed.xml"
            code = 'from pathlib import Path; Path("TEST-mixed.xml").write_text(\'<testsuite tests="2" failures="0" errors="0" skipped="1"/>\')'
            self.check_execute(runner, [sys.executable, "-c", code], pathlib.Path(directory), [report], 1, "executed=1, failures=0, skipped=1")

    def test_canonical_future_modes_fail_explicitly_until_registered(self):
        for mode in ["it", "web", "e2e", "visual", "contract", "load", "ops"]:
            with self.subTest(mode=mode):
                self.assertNotEqual(0, self.run_gate(mode, "UnregisteredScenario").returncode)

    def test_unit_discovers_tests_in_core_module(self):
        spec = importlib.util.spec_from_file_location("ama_runner", RUNNER)
        runner = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(runner)
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            source = root / "backend/core/src/test/java/ama/CoreTest.java"
            source.parent.mkdir(parents=True)
            source.write_text("package ama; class CoreTest {}")
            (root / "backend/core/pom.xml").write_text("<project/>")
            # A build-process fixture emits fresh JUnit evidence; no Maven/network is needed to test dispatch.
            wrapper = root / "mvnw"
            wrapper.write_text('#!/usr/bin/env python3\nfrom pathlib import Path\np=Path("backend/core/target/surefire-reports/TEST-ama.CoreTest.xml")\np.parent.mkdir(parents=True,exist_ok=True)\np.write_text(\'<testsuite tests="1" failures="0" errors="0" skipped="0"/>\')\n')
            wrapper.chmod(0o755)
            runner.ROOT = root
            output = io.StringIO()
            with contextlib.redirect_stdout(output), contextlib.redirect_stderr(output):
                self.assertEqual(0, runner.java_tests("CoreTest"))
            self.assertIn("PASS: executed=1", output.getvalue())


class WebRunnerTests(unittest.TestCase):
    def runner(self, root):
        spec = importlib.util.spec_from_file_location("ama_web_runner", RUNNER)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        module.ROOT = root
        return module

    def test_registered_web_suites_execute_actual_tests_and_count_reports(self):
        # Catches missing dispatch, wrong cwd/report paths and treating discovery as execution.
        for group, relative in [("web", "unit/client.test.ts"), ("visual", "visual/shell.spec.ts"), ("e2e", "e2e/shell.spec.ts")]:
            with self.subTest(group=group), tempfile.TemporaryDirectory() as directory:
                root=pathlib.Path(directory)
                source=root / "web/tests" / relative
                source.parent.mkdir(parents=True)
                source.write_text("// discovery fixture")
                binary=root / "web/node_modules/.bin" / ("tsx" if group=="web" else "playwright")
                binary.parent.mkdir(parents=True)
                binary.write_text('#!/usr/bin/env python3\nfrom pathlib import Path\np=Path("../artifacts/web/'+group+'.xml")\np.parent.mkdir(parents=True,exist_ok=True)\np.write_text(\'<testsuite tests="2" failures="0" errors="0" skipped="0"/>\')\n')
                binary.chmod(0o755)
                self.assertEqual(0,self.runner(root).web_tests(group,source.name))

    def test_zero_web_matches_cannot_launch_a_false_success(self):
        with tempfile.TemporaryDirectory() as directory:
            self.assertNotEqual(0,self.runner(pathlib.Path(directory)).web_tests("web","missing.test.ts"))

    def test_python_discovery_counts_all_delivered_directories_and_rejects_skips(self):
        # Two real temporary Python suites; deleting a directory from discovery loses its count.
        with tempfile.TemporaryDirectory() as directory:
            root=pathlib.Path(directory)
            for folder,name in [("tools/checks/tests","one"),("tools/probes/tests","two")]:
                target=root / folder
                target.mkdir(parents=True)
                (target / ("test_"+name+".py")).write_text("import unittest\nclass Suite(unittest.TestCase):\n def test_real(self): self.assertEqual(4,2+2)\n")
            runner=self.runner(root)
            self.assertEqual(0,runner.python_tests())
            (root / "tools/probes/tests/test_two.py").write_text("import unittest\nclass Suite(unittest.TestCase):\n @unittest.skip('pending')\n def test_real(self): pass\n")
            self.assertNotEqual(0,runner.python_tests())

    def test_node_junit_direct_cases_are_counted_and_skips_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root=pathlib.Path(directory)
            runner=self.runner(root)
            report=root / "node.xml"
            for xml, want in [('<testsuites><testcase name="actual"/></testsuites>',0),('<testsuites><testcase name="pending"><skipped/></testcase><testcase name="pass"/></testsuites>',1),('<testsuites><testcase name="broken"><failure/></testcase></testsuites>',1)]:
                code="from pathlib import Path; Path('node.xml').write_text("+repr(xml)+")"
                self.assertEqual(want,runner.execute([sys.executable,"-c",code],root,[report]))

    def test_no_delivered_python_cases_is_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            self.assertNotEqual(0,self.runner(pathlib.Path(directory)).python_tests())

if __name__ == "__main__":
    unittest.main()
