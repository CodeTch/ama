"""Offline guard checks: real validator, deterministic Docker inspect responses, no Docker I/O."""
import copy
import importlib.util
import json
import pathlib
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("s3_fixture", pathlib.Path(__file__).parents[1] / "s3_fixture.py")
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)


class OwnedFixtureGuardTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = pathlib.Path(self.temp.name)
        self.record = dict(id="a" * 64, port=44789, image=fixture.IMAGE,
                           owner=fixture.OWNER, workspace=str(fixture.ROOT))
        (self.directory / "fixture.json").write_text(json.dumps(self.record))
        self.state = {
            "Id": self.record["id"],
            "Config": {"Labels": {"ama.owner": fixture.OWNER}, "Image": fixture.IMAGE},
            "HostConfig": {"Memory": 2 * 1024**3, "MemorySwap": 2 * 1024**3,
                           "NanoCpus": 2_000_000_000, "PidsLimit": 256,
                           "PortBindings": {"9000/tcp": [{"HostIp": "127.0.0.1", "HostPort": "44789"}]}},
            "Mounts": [{"Source": str(self.directory / "data"), "Destination": "/data"},
                       {"Source": str(self.directory / "logs"), "Destination": "/logs"}],
        }

    def inspect(self, state):
        with patch.object(fixture, "HOME", self.directory), patch.object(
                fixture, "run", return_value=json.dumps([state])) as command:
            try:
                return fixture.inspect_owned()
            finally:
                command.assert_called_once_with("docker", "inspect", self.record["id"])

    def test_exact_owned_profile_is_accepted(self):
        self.assertEqual(self.record, self.inspect(self.state))

    def test_cpu_bound_drift_is_rejected(self):
        for value in [0, 1_000_000_000, 4_000_000_000, None]:
            with self.subTest(value=value):
                state = copy.deepcopy(self.state)
                state["HostConfig"]["NanoCpus"] = value
                with self.assertRaises(AssertionError): self.inspect(state)

    def test_swap_bound_drift_is_rejected(self):
        for value in [-1, 0, 4 * 1024**3, None]:
            with self.subTest(value=value):
                state = copy.deepcopy(self.state)
                state["HostConfig"]["MemorySwap"] = value
                with self.assertRaises(AssertionError): self.inspect(state)

    def test_pid_bound_drift_is_rejected(self):
        for value in [-1, 0, 512, None]:
            with self.subTest(value=value):
                state = copy.deepcopy(self.state)
                state["HostConfig"]["PidsLimit"] = value
                with self.assertRaises(AssertionError): self.inspect(state)

    def test_exact_id_must_match_inspected_response(self):
        self.state["Id"] = "b" * 64
        with self.assertRaises(AssertionError): self.inspect(self.state)

    def test_existing_owner_image_memory_ports_and_mount_guards_remain(self):
        for field in ["owner", "image", "memory", "port", "mount"]:
            with self.subTest(field=field):
                state = copy.deepcopy(self.state)
                if field == "owner": state["Config"]["Labels"]["ama.owner"] = "foreign"
                if field == "image": state["Config"]["Image"] = "foreign"
                if field == "memory": state["HostConfig"]["Memory"] = 0
                if field == "port": state["HostConfig"]["PortBindings"]["9000/tcp"][0]["HostIp"] = "0.0.0.0"
                if field == "mount": state["Mounts"][0]["Source"] = "/foreign"
                with self.assertRaises(AssertionError): self.inspect(state)


if __name__ == "__main__": unittest.main()
