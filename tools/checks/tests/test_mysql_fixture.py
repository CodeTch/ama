"""Startup evidence and cleanup safety. All Docker calls are mocked."""
import contextlib
import importlib.util
import io
import json
import pathlib
import stat
import subprocess
import tempfile
import traceback
import unittest
from unittest.mock import patch

SOURCE = pathlib.Path(__file__).resolve().parents[2] / "dev/mysql_fixture.py"
spec = importlib.util.spec_from_file_location("mysql_fixture_under_test", SOURCE)
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)


class FixtureFailureTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = pathlib.Path(self.temp.name)
        self.name = "ama-test-b01-0123456789ab"
        self.directory = self.root / ".tools/mysql-fixture" / self.name
        self.password = "synthetic-user-secret-abcdefghijklmnopqrstuvwxyz"
        self.root_password = "synthetic-root-secret-abcdefghijklmnopqrstuvwxyz"
        self.error = RuntimeError("synthetic readiness failure")
        self.events = []
        self.proof = {
            "name": "/" + self.name, "image_reference": fixture.IMAGE,
            "image_id": "sha256:synthetic-image-id", "label": "b01",
            "state": {"status": "exited", "exit_code": 1, "oom_killed": False,
                      "error": "error " + self.password + " " + self.root_password},
            "port_bindings": {"3306/tcp": [{"HostIp": "127.0.0.1", "HostPort": ""}]},
            "Config": {"Env": ["DO_NOT_PERSIST_UNSELECTED_ENV"]},
        }
        self.inspect_error = self.logs_error = self.cleanup_error = self.launch_error = None
        self.ready = False
        self.evidence_at_removal = None
        self.commands = []

    def command(self, argv, **kwargs):
        self.commands.append((argv, kwargs))
        action = argv[1]
        if action == "run" and self.launch_error:
            raise self.launch_error
        if action == "exec":
            self.events.append("readiness")
            if self.ready:
                return subprocess.CompletedProcess(argv, 0)
            raise self.error
        if action == "inspect":
            self.events.append("inspect")
            if self.inspect_error:
                raise self.inspect_error
            output = (json.dumps(self.proof) if "--format" in argv else
                      json.dumps([{"NetworkSettings": {"Ports": {"3306/tcp": [{"HostPort": "33060"}]}}}]))
        elif action == "logs":
            self.events.append("logs")
            if self.logs_error:
                raise self.logs_error
            output = "startup " + self.password + "\nroot " + self.root_password + "\n"
        elif action == "rm":
            self.events.append("rm")
            self.evidence_at_removal = self.diagnostics()
            if self.cleanup_error:
                raise self.cleanup_error
            output = self.name
        else:
            output = "synthetic-docker-result"
        if "capture_output" in kwargs:
            stderr = "stderr " + self.root_password if action == "logs" else ""
            return subprocess.CompletedProcess(argv, 0, stdout=output, stderr=stderr)
        return output

    def invoke(self):
        caught = None
        with patch.object(fixture, "ROOT", self.root), \
             patch.object(fixture.secrets, "token_hex", return_value="0123456789ab"), \
             patch.object(fixture.secrets, "token_urlsafe", side_effect=[self.password, self.root_password]), \
             patch.object(fixture.subprocess, "check_output", side_effect=self.command), \
             patch.object(fixture.subprocess, "run", side_effect=self.command), \
             patch.object(fixture.time, "sleep"), contextlib.redirect_stdout(io.StringIO()) as stdout:
            try:
                fixture.start()
            except BaseException as error:
                caught = error
        self.stdout = stdout.getvalue()
        return caught

    def assert_original(self):
        # Avoid rendering a replacement exception: it may contain credentials.
        self.assertTrue(self.invoke() is self.error, "original startup failure was replaced")

    def assert_no_secrets(self, text):
        self.assertFalse(self.password in text, "user credential was exposed")
        self.assertFalse(self.root_password in text, "root credential was exposed")

    def diagnostics(self):
        return {p.name: p.read_text() for p in self.directory.iterdir()
                if p.name not in {"mysql.env", "env.sh"}}

    def outcome(self):
        return json.loads((self.directory / "failure.json").read_text())

    def test_capture_precedes_removal_and_contains_only_safe_fields(self):
        self.assert_original()
        self.assertIn("logs", self.events)
        self.assertLess(self.events.index("logs"), self.events.index("rm"))
        self.assertIn("failure.json", self.evidence_at_removal)
        self.assertIn("docker.log", self.evidence_at_removal)
        self.assertIn("stderr [REDACTED]", self.diagnostics()["docker.log"])
        diagnostic = "\n".join(self.diagnostics().values()) + self.stdout + str(getattr(self.error, "__notes__", []))
        self.assert_no_secrets(diagnostic)
        self.assertNotIn("DO_NOT_PERSIST_UNSELECTED_ENV", diagnostic)
        self.assertNotIn("Config", diagnostic)
        state = self.outcome()["container"]
        self.assertEqual(state["name"], "/" + self.name)
        self.assertEqual(state["state"]["exit_code"], 1)
        self.assertEqual(self.outcome()["cleanup"], "removed")
        self.assertEqual(stat.S_IMODE(self.directory.stat().st_mode), 0o700)
        for filename in self.diagnostics():
            self.assertEqual(stat.S_IMODE((self.directory / filename).stat().st_mode), 0o600)
        for argv, kwargs in self.commands:
            if argv[1] in {"inspect", "logs", "rm"}:
                self.assertGreater(kwargs.get("timeout", 0), 0)
                self.assertLessEqual(kwargs["timeout"], 10)
            if argv[1] == "inspect":
                self.assertIn("--format", argv)
                self.assertNotIn(".Config.Env", " ".join(argv))

    def test_cleanup_failure_preserves_original_exception_and_traceback(self):
        self.cleanup_error = RuntimeError(self.password + self.root_password)
        self.assert_original()
        self.assertEqual(traceback.extract_tb(self.error.__traceback__)[-1].name, "command")
        self.assertEqual(self.outcome()["cleanup"], "failed")
        self.assert_no_secrets("\n".join(self.diagnostics().values()))

    def test_log_read_failure_preserves_container_and_original_failure(self):
        self.logs_error = subprocess.TimeoutExpired(["synthetic", self.password, self.root_password], 10)
        self.assert_original()
        self.assertEqual(traceback.extract_tb(self.error.__traceback__)[-1].name, "command")
        self.assertEqual(self.outcome()["inspect"], "captured")
        self.assertEqual(self.outcome()["logs"], "failed")
        self.assertEqual(self.outcome()["cleanup"], "refused")
        self.assertNotIn("rm", self.events)
        self.assert_no_secrets("\n".join(self.diagnostics().values()) + self.stdout +
                               str(getattr(self.error, "__notes__", [])))

    def test_log_persistence_failure_preserves_container_and_original_failure(self):
        original_write = pathlib.Path.write_text
        def write(path, *args, **kwargs):
            if path.name == "docker.log":
                raise OSError(self.password + self.root_password)
            return original_write(path, *args, **kwargs)
        with patch.object(pathlib.Path, "write_text", write):
            self.assert_original()
        self.assertEqual(traceback.extract_tb(self.error.__traceback__)[-1].name, "command")
        self.assertEqual(self.outcome()["inspect"], "captured")
        self.assertEqual(self.outcome()["logs"], "failed")
        self.assertEqual(self.outcome()["cleanup"], "refused")
        self.assertNotIn("rm", self.events)
        self.assert_no_secrets("\n".join(self.diagnostics().values()) + self.stdout +
                               str(getattr(self.error, "__notes__", [])))

    def test_inspect_failure_refuses_removal(self):
        self.inspect_error = RuntimeError(self.root_password)
        self.assert_original()
        self.assertNotIn("rm", self.events)
        self.assertEqual(self.outcome()["cleanup"], "refused")
        self.assert_no_secrets("\n".join(self.diagnostics().values()))

    def test_ownership_or_binding_mismatch_refuses_removal(self):
        # Each iteration gets a new generated directory, just as separate starts do.
        for key, value in [("name", "/preexisting"), ("label", "foreign"),
                           ("image_reference", "mysql:latest"),
                           ("port_bindings", {"3306/tcp": [{"HostIp": "0.0.0.0", "HostPort": "3306"}]}),
                           ("port_bindings", None)]:
            with self.subTest(field=key, value=value):
                original = self.proof[key]
                self.proof[key] = value
                self.root = self.root / "next"
                self.directory = self.root / ".tools/mysql-fixture" / self.name
                self.events.clear()
                self.assert_original()
                self.assertNotIn("rm", self.events)
                self.assertEqual(self.outcome()["cleanup"], "refused")
                self.proof[key] = original

    def test_diagnostic_write_failure_preserves_original_and_refuses_cleanup(self):
        original_write = pathlib.Path.write_text
        def write(path, *args, **kwargs):
            if path.name != "mysql.env":
                raise OSError("synthetic unsafe exception")
            return original_write(path, *args, **kwargs)
        with patch.object(pathlib.Path, "write_text", write):
            self.assert_original()
        self.assertNotIn("rm", self.events)
        self.assert_no_secrets(str(getattr(self.error, "__notes__", [])))

    def test_note_failure_cannot_replace_original_failure(self):
        class UnnotableError(RuntimeError):
            def add_note(self, note):
                raise RuntimeError("synthetic note failure")
        self.error = UnnotableError("original failure")
        self.assert_original()

    def test_unknown_secret_omits_logs_and_inspect_payload(self):
        self.directory.mkdir(parents=True, mode=0o700)
        with patch.object(fixture.subprocess, "run", side_effect=self.command), \
             patch.object(fixture.subprocess, "check_output", side_effect=self.command):
            fixture.startup_failure(self.directory, self.name, (None, self.root_password))
        self.assertEqual(self.events, [])
        self.assertEqual(self.outcome(), {"inspect": "failed", "logs": "omitted", "cleanup": "refused"})

    def test_malformed_selected_state_refuses_removal(self):
        self.proof["state"]["error"] = {"unexpected": self.password}
        self.assert_original()
        self.assertNotIn("rm", self.events)
        self.assert_no_secrets("\n".join(self.diagnostics().values()))

    def test_keyboard_interrupt_preserves_original_failure(self):
        self.error = KeyboardInterrupt("synthetic interruption")
        self.assert_original()
        self.assertEqual(self.outcome()["cleanup"], "removed")

    def test_success_retains_existing_public_output_and_launch_options(self):
        self.ready = True
        self.assertIsNone(self.invoke())
        self.assertEqual(self.diagnostics(), {})
        self.assertNotIn("logs", self.events)
        self.assertNotIn("rm", self.events)
        self.assertIn("loopback port 33060", self.stdout)
        self.assertTrue("AMA_TEST_DATABASE_PASSWORD=" + self.password in
                        (self.directory / "env.sh").read_text(), "private environment lacks user credential")
        launch = next(argv for argv, _ in self.commands if argv[1] == "run")
        self.assertEqual(launch, ["docker", "run", "-d", "--name", self.name,
            "--label", "ama.test-fixture=b01", "--publish", "127.0.0.1::3306",
            "--env-file", str(self.directory / "mysql.env"), fixture.IMAGE,
            "--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci",
            "--default-time-zone=+00:00"])

    def test_launch_failure_never_enters_cleanup_path(self):
        self.launch_error = RuntimeError("synthetic name conflict")
        self.assertIs(self.invoke(), self.launch_error)
        self.assertEqual(self.diagnostics(), {})
        self.assertNotIn("inspect", self.events)
        self.assertNotIn("rm", self.events)


if __name__ == "__main__":
    unittest.main()
