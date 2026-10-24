"""Focused offline contract tests. Only sanitized in-memory fixtures; no provisioning."""
import argparse
import ast
import copy
import hashlib
import inspect
import json
from pathlib import Path
import shlex
import types
import unittest
from unittest.mock import patch

W = Path(__file__).resolve().parent
CID = "1" * 64
NAME = "/ama-test-fake-parent"
SCHEMA = "ama_t09_0123456789ab_behavior"
OWNER = "task09-0123456789abcdef0123456789abcdef"
FAKE = Path("/fake-W-runtime-fix02")


def deny(*args, **kwargs):
    raise AssertionError("unexpected external operation or non-fixture read")


def load_helper(path):
    source = path.read_text(encoding="utf-8")
    tree = ast.parse(source)
    # Import is safe only if no module-level helper execution is present.
    for node in tree.body:
        if isinstance(node, ast.If):
            assert ast.dump(node.test) == ast.dump(ast.parse('__name__ == "__main__"', mode="eval").body)
            assert len(node.body) == 1 and isinstance(node.body[0], ast.Expr)
            assert ast.dump(node.body[0].value) == ast.dump(ast.parse('main()', mode="eval").body)
        else:
            assert isinstance(node, (ast.Import, ast.ImportFrom, ast.Assign, ast.FunctionDef, ast.Expr))
    module = types.ModuleType("offline_helper")
    module.__file__ = str(path)
    with patch("subprocess.run", deny):
        exec(compile(tree, str(path), "exec"), module.__dict__)
    return module, tree


class Contract(unittest.TestCase):
    def setUp(self):
        self.m, self.tree = load_helper(HELPER)
        m = self.m
        m.TREE = FAKE
        m.CID = CID
        m.PARENT_OWNER = "task08-fake-parent"
        m.IMAGE = "mysql@sha256:" + "2" * 64
        m.SERVER_UUID = "00000000-0000-4000-8000-000000000003"
        m.TASK7_OWNER = "task07-fake-source"
        m.TASK7_SCHEMA = "ama_t07_fake_behavior"
        m.TASK7_ENV = FAKE / "source-env.sh"
        m.TASK5_OWNER = "task05-fake-reference"
        m.TASK5_ENV = FAKE / "fake-client.env"
        m.TASK5_PKI = FAKE / "fake-pki"
        m.PARENT_GUARD = FAKE / "parent-guard.json"
        m.PARENT_ACK = FAKE / "parent-ack.json"
        self.selected = {
            "id": CID, "name": NAME, "image": m.IMAGE, "owner": m.PARENT_OWNER,
            "memory": 805306368, "swap": 805306368, "cpus": 1000000000,
            "pids": 256, "state": "running", "running": True, "oom": False,
            "ports": {"3306/tcp": [{"HostIp": "127.0.0.1", "HostPort": "44727"}]},
            "mounts": [
                {"Type": "bind", "Source": "/fake-W-runtime-fix02/secrets", "Destination": "/run/ama-task08", "Mode": "ro", "RW": False, "Propagation": "rprivate"},
                {"Type": "volume", "Name": "fake-data", "Source": "/fake-W-runtime-fix02/data", "Destination": "/var/lib/mysql", "Mode": "rw", "RW": True, "Propagation": ""},
            ],
        }
        self.guard = {"selected": copy.deepcopy(self.selected)}
        self.values = {
            "AMA_TEST_DATABASE_URL": "jdbc:mysql://127.0.0.1:44727/ama_t07_fake_behavior",
            "AMA_TEST_DATABASE_USERNAME": "ama_t08_848e93aace8b",
            "AMA_TEST_DATABASE_PASSWORD": "FAKE-OFFLINE-NOT-A-CREDENTIAL",
            "AMA_TEST_CONTAINER": NAME[1:],
            "AMA_TASK07_OWNER": m.TASK7_OWNER,
            "AMA_TASK07_RUN_ROOT": "/fake-W-runtime-fix02/source",
            "AMA_TASK07_S3_ENV": str(m.TASK5_ENV),
            "AMA_TASK07_PKI_ROOT": str(m.TASK5_PKI),
        }
        self.ack_override = {}

    def clone(self):
        m = self.m
        guard_raw = json.dumps(self.guard).encode()
        ack = {
            "base": "de1611a67ba483afe21f3b29bb69c2d32028efef",
            "creationGuardSha256": hashlib.sha256(guard_raw).hexdigest(),
            "cid": CID, "parentResourceOwner": m.PARENT_OWNER, "port": 44727,
            "ready": {"serverUuid": m.SERVER_UUID},
            "privateEnv": "source-env.sh",
        }
        ack.update(self.ack_override)
        source_raw = "".join("export " + key + "=" + shlex.quote(value) + "\n" for key, value in self.values.items()).encode()
        files = {m.PARENT_GUARD: guard_raw, m.PARENT_ACK: json.dumps(ack).encode(), m.TASK7_ENV: source_raw}
        before = dict(files)
        allowed_private = {
            (m.TASK7_ENV, m.TASK7_OWNER, False),
            (m.TASK5_ENV, m.TASK5_OWNER, False),
            (m.TASK5_PKI, m.TASK5_OWNER, True),
            (m.TASK5_PKI / "keystore-password", m.TASK5_OWNER, False),
        }
        def private(path, owner, *, directory=False):
            self.assertIn((path, owner, directory), allowed_private)
            return path
        def read_bytes(path):
            if path not in files:
                return deny()
            return files[path]
        def read_text(path, *args, **kwargs):
            return read_bytes(path).decode()
        def selected_transport(argv, **kwargs):
            self.assertEqual(argv[:4], ["docker", "container", "inspect", "--format"])
            self.assertEqual(argv[-1], CID)
            self.assertEqual(len(argv), 6)
            self.assertEqual(kwargs, {})
            return json.dumps(self.selected)
        with patch.object(Path, "read_bytes", read_bytes), patch.object(Path, "read_text", read_text), patch.object(Path, "write_text", deny), patch.object(Path, "write_bytes", deny), patch.object(m, "private_owned", private), patch.object(m, "run", selected_transport), patch.object(m, "mysql", deny), patch("subprocess.run", deny), patch("os.open", deny), patch("os.mkdir", deny):
            m.check_records()
            verified = m.selected()
            args = (SCHEMA, OWNER, FAKE / "new-owner")
            if len(inspect.signature(m.env_clone).parameters) == 4:
                output = m.env_clone(*args, verified)
            else:
                output = m.env_clone(*args)
        self.assertEqual(files, before, "source fixture must remain unchanged")
        return dict(line[7:].split("=", 1) for line in output.splitlines())

    def test_exact_guarded_name_accepted(self):
        try:
            result = self.clone()
        except RuntimeError as exc:
            self.fail("exact freshly guarded parent name was rejected: " + str(exc))
        self.assertEqual(result["AMA_TEST_CONTAINER"], CID)

    def test_full_cid_accepted(self):
        self.values["AMA_TEST_CONTAINER"] = CID
        self.assertEqual(self.clone()["AMA_TEST_CONTAINER"], CID)

    def test_untrusted_references_rejected(self):
        for value in ["other-name", "9" * 64, "ama-test-foreign-parent", CID[:12], NAME, "localhost", "ama-test-fake-parent.evil", "ama-test-fake-parent;touch /fake-marker", "$(echo ama-test-fake-parent)", "ama-test-fake-parent\nINJECTED=yes", " ama-test-fake-parent"]:
            with self.subTest(value=value):
                self.values["AMA_TEST_CONTAINER"] = value
                with self.assertRaises(RuntimeError):
                    self.clone()

    def test_fresh_selected_identity_binding_required(self):
        for field, value in [("id", "9" * 64), ("name", "/foreign-live-name"), ("owner", "foreign-owner"), ("image", "foreign-image")]:
            with self.subTest(field=field):
                saved = self.selected[field]
                self.selected[field] = value
                with self.assertRaisesRegex(RuntimeError, "identity/state/resource guard"):
                    self.clone()
                self.selected[field] = saved

    def test_fresh_selected_mount_binding_required(self):
        self.selected["mounts"][0]["RW"] = True
        with self.assertRaisesRegex(RuntimeError, "mount guard"):
            self.clone()

    def test_parent_ack_binding_required(self):
        for field, value in [("creationGuardSha256", "f" * 64), ("cid", "9" * 64), ("parentResourceOwner", "foreign-owner")]:
            with self.subTest(field=field):
                self.ack_override = {field: value}
                with self.assertRaisesRegex(RuntimeError, "parent guard/ACK mismatch"):
                    self.clone()

    def test_output_canonical_cid_and_task9_only_url(self):
        result = self.clone()
        self.assertEqual(result["AMA_TEST_CONTAINER"], CID)
        self.assertEqual(result["AMA_TEST_DATABASE_URL"], "jdbc:mysql://127.0.0.1:44727/ama_t09_0123456789ab_behavior")
        self.assertNotIn("ama_t07_fake_behavior", "\n".join(result.values()))
        self.assertEqual(set(result), {"AMA_TEST_DATABASE_URL", "AMA_TEST_DATABASE_USERNAME", "AMA_TEST_DATABASE_PASSWORD", "AMA_TEST_CONTAINER", "AMA_TASK09_OWNER", "AMA_TASK09_RUN_ROOT", "AMA_TASK09_S3_ENV", "AMA_TASK09_PKI_ROOT"})

    def test_provision_binds_clone_to_fresh_selected_after_records(self):
        # AST only: never call provision or main, even with fakes.
        fn = next(n for n in self.tree.body if isinstance(n, ast.FunctionDef) and n.name == "provision")
        assignments = {n.targets[0].id: n for n in fn.body if isinstance(n, ast.Assign) and len(n.targets) == 1 and isinstance(n.targets[0], ast.Name)}
        records, selected, server, cloned = [assignments[k] for k in ["records", "before_selected", "before_server", "cloned_env"]]
        self.assertEqual(records.value.func.id, "check_records")
        self.assertEqual(selected.value.func.id, "selected")
        self.assertEqual(server.value.func.id, "server")
        self.assertLess(records.lineno, selected.lineno)
        self.assertLess(selected.lineno, server.lineno)
        self.assertLess(server.lineno, cloned.lineno)
        self.assertEqual(cloned.value.func.id, "env_clone")
        self.assertEqual([n.id for n in cloned.value.args], ["schema", "owner", "folder", "before_selected"])


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--helper", choices=["old", "new"], required=True)
    parser.add_argument("--case", choices=["exact-name", "all"], default="all")
    options = parser.parse_args()
    HELPER = W / ("task-09-fixture-provisioning-prepared-20261024.py" if options.helper == "old" else "task-09-fixture-provisioning-runtime-fix02-20261024.py")
    suite = unittest.TestSuite([Contract("test_exact_guarded_name_accepted")]) if options.case == "exact-name" else unittest.defaultTestLoader.loadTestsFromTestCase(Contract)
    print("OFFLINE helper=" + options.helper + " case=" + options.case + "; fake inputs only; provision/main never called", flush=True)
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    raise SystemExit(0 if result.wasSuccessful() else 1)
