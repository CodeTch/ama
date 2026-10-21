"""Runner regressions: no selector and a failed subprocess must fail the gate."""
import importlib.util
import pathlib
import subprocess
import sys
import tempfile
import unittest

RUNNER = pathlib.Path(__file__).resolve().parents[1] / "run.py"

class RunnerTests(unittest.TestCase):
    def run_gate(self, *args):
        return subprocess.run([sys.executable, str(RUNNER), *args], text=True, capture_output=True)

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
            result = runner.execute([sys.executable, "-c", "raise SystemExit(17)"], pathlib.Path(directory), [report])
            self.assertEqual(17, result)

    def test_zero_executed_tests_is_failure(self):
        spec = importlib.util.spec_from_file_location("ama_runner", RUNNER)
        runner = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(runner)
        with tempfile.TemporaryDirectory() as directory:
            report = pathlib.Path(directory) / "TEST-empty.xml"
            result = runner.execute([sys.executable, "-c", "pass"], pathlib.Path(directory), [report])
            self.assertNotEqual(0, result)

    def test_all_skipped_tests_is_failure(self):
        spec = importlib.util.spec_from_file_location("ama_runner", RUNNER)
        runner = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(runner)
        with tempfile.TemporaryDirectory() as directory:
            report = pathlib.Path(directory) / "TEST-skipped.xml"
            code = 'from pathlib import Path; Path("TEST-skipped.xml").write_text(\'<testsuite tests="3" failures="0" errors="0" skipped="3"/>\')'
            result = runner.execute([sys.executable, "-c", code], pathlib.Path(directory), [report])
            self.assertNotEqual(0, result)

    def test_fresh_real_execution_is_success(self):
        spec = importlib.util.spec_from_file_location("ama_runner", RUNNER)
        runner = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(runner)
        with tempfile.TemporaryDirectory() as directory:
            report = pathlib.Path(directory) / "TEST-passed.xml"
            code = 'from pathlib import Path; Path("TEST-passed.xml").write_text(\'<testsuite tests="2" failures="0" errors="0" skipped="0"/>\')'
            self.assertEqual(0, runner.execute([sys.executable, "-c", code], pathlib.Path(directory), [report]))

    def test_skipped_required_case_is_failure_even_if_others_pass(self):
        spec = importlib.util.spec_from_file_location("ama_runner", RUNNER)
        runner = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(runner)
        with tempfile.TemporaryDirectory() as directory:
            report = pathlib.Path(directory) / "TEST-mixed.xml"
            code = 'from pathlib import Path; Path("TEST-mixed.xml").write_text(\'<testsuite tests="2" failures="0" errors="0" skipped="1"/>\')'
            self.assertNotEqual(0, runner.execute([sys.executable, "-c", code], pathlib.Path(directory), [report]))

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
            self.assertEqual(0, runner.java_tests("CoreTest"))

if __name__ == "__main__":
    unittest.main()
