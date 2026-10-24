"""Task 11 fixture adaptation checks with in-memory boundaries only.

Never invoke the provisioning helper, SQL, Docker, private env, or files under .tools.
"""
import argparse
import ast
import copy
import hashlib
import json
import os
from pathlib import Path
import shlex
import subprocess
import types
import unittest
from unittest.mock import patch

W = Path(__file__).resolve().parent
HELPER = W / "task-11-fixture-provisioning-prepared-20261025.py"
PREVIOUS = W / "task-10-fixture-provisioning-prepared-20261024.py"
SOURCE = HELPER.read_text(encoding="utf-8")
OLD = PREVIOUS.read_text(encoding="utf-8")


def denied(*args, **kwargs):
    raise AssertionError("actual runtime/private boundary forbidden in offline check")


with patch.object(subprocess, "run", denied), patch.object(os, "open", denied):
    h = types.ModuleType("task11_offline_only")
    h.__file__ = str(HELPER)
    exec(compile(SOURCE, str(HELPER), "exec"), h.__dict__)

PUBLIC = {p: p.read_bytes() for p in
          (h.PARENT_GUARD, h.PARENT_ACK, h.RECOVERY_ACK, h.ADOPTION_ACK,
           h.TREE / h.ADOPTION_DOCUMENT)}


class Offline(unittest.TestCase):
    def setUp(self):
        self.files = dict(PUBLIC)
        self.historical = json.loads(self.files[h.PARENT_GUARD])["selectedAfter"]
        self.current = copy.deepcopy(self.historical)
        self.current["ports"] = {"3306/tcp": [{"HostIp": "127.0.0.1", "HostPort": "46151"}]}
        self.owner = "task11-0123456789abcdef0123456789abcdef"
        self.schema = "ama_t11_0123456789ab_behavior"
        self.folder = h.TOOLS / self.owner
        self.values = {
            "AMA_TEST_DATABASE_URL": "jdbc:mysql://127.0.0.1:46151/" + h.TASK10_SCHEMA,
            "AMA_TEST_DATABASE_USERNAME": "ama_t08_848e93aace8b",
            "AMA_TEST_DATABASE_PASSWORD": "fabricated-offline-token",
            "AMA_TEST_CONTAINER": h.CID,
            "AMA_TASK10_OWNER": h.TASK10_OWNER,
            "AMA_TASK10_RUN_ROOT": str(h.TOOLS / h.TASK10_OWNER),
            "AMA_TASK10_S3_ENV": str(h.TASK5_ENV),
            "AMA_TASK10_PKI_ROOT": str(h.TASK5_PKI),
        }

        def read_bytes(path):
            if path not in self.files:
                raise AssertionError("unexpected read outside explicit memory map")
            return self.files[path]

        def public(path):
            if path not in (h.PARENT_GUARD, h.PARENT_ACK, h.RECOVERY_ACK,
                            h.ADOPTION_ACK, h.TREE / h.ADOPTION_DOCUMENT):
                raise AssertionError("unexpected public evidence path")
            return path

        self.private_requests = []
        def private(path, owner, directory=False):
            self.private_requests.append((path, owner, directory))
            accepted = {
                (h.TASK10_ENV.parent, h.TASK10_OWNER, True),
                (h.TASK10_ENV, h.TASK10_OWNER, False),
                (h.TASK5_ENV, h.TASK5_OWNER, False),
                (h.TASK5_PKI, h.TASK5_OWNER, True),
                (h.TASK5_PKI / "keystore-password", h.TASK5_OWNER, False),
            }
            if (path, owner, directory) not in accepted:
                raise AssertionError("unexpected private metadata request")
            return path

        patches = [
            patch.object(Path, "read_bytes", read_bytes),
            patch.object(Path, "read_text", lambda p, **kw: read_bytes(p).decode()),
            patch.object(Path, "open", denied), patch.object(Path, "stat", denied),
            patch.object(Path, "resolve", denied), patch.object(Path, "mkdir", denied),
            patch.object(Path, "write_text", denied), patch.object(Path, "write_bytes", denied),
            patch.object(subprocess, "run", denied), patch.object(os, "open", denied),
            patch.object(os, "chmod", denied), patch.object(os, "replace", denied),
            patch.object(h, "public_file", public), patch.object(h, "private_owned", private),
            patch.object(h, "mysql", denied), patch.object(h, "run", denied),
            patch.object(h, "provision", denied), patch.object(h, "main", denied),
        ]
        for p in patches:
            p.start()
            self.addCleanup(p.stop)

    def adopt(self, *, evidence=None, digest=None):
        args = argparse.Namespace(
            task10_adoption_evidence=str(h.ADOPTION_ACK) if evidence is None else evidence,
            task10_adoption_sha256=h.sha(self.files[h.ADOPTION_ACK]) if digest is None else digest)
        return h.check_adoption(args)

    def clone(self, values=None):
        values = self.values if values is None else values
        raw = "".join("export " + key + "=" + shlex.quote(value) + "\n"
                      for key, value in values.items()).encode()
        self.files[h.TASK10_ENV] = raw
        output = h.env_clone(self.schema, self.owner, self.folder, self.current)
        self.assertIn((h.TASK10_ENV.parent, h.TASK10_OWNER, True), self.private_requests)
        self.assertEqual(self.files[h.TASK10_ENV], raw)
        return dict((line.split("=", 1)[0].removeprefix("export "),
                     shlex.split(line.split("=", 1)[1])[0])
                    for line in output.splitlines())

    def test_fixed_task10_adoption_and_public_document(self):
        actual = json.loads(self.files[h.ADOPTION_ACK])
        self.assertEqual(self.adopt()["sourceCommit"], "86777940c3f9500b1b89d884deaae2a48740d9a9")
        self.assertEqual(self.adopt()["adoptionCommit"], "ba48f35e7ef97c1e162a2d373453bd1e9689529c")
        for path in (str(h.TASK10_ENV), str(h.W / "other.json")):
            with self.subTest(path=path), self.assertRaisesRegex(RuntimeError, "fixed public"):
                self.adopt(evidence=path)
        with self.assertRaisesRegex(RuntimeError, "SHA-256 mismatch"):
            self.adopt(digest="0" * 64)
        before = self.files[h.ADOPTION_ACK]
        self.files[h.ADOPTION_ACK] += b" "
        with self.assertRaisesRegex(RuntimeError, "SHA-256 mismatch"):
            self.adopt()
        self.files[h.ADOPTION_ACK] = before
        before = self.files[h.TREE / h.ADOPTION_DOCUMENT]
        self.files[h.TREE / h.ADOPTION_DOCUMENT] += b" "
        with self.assertRaisesRegex(RuntimeError, "document SHA-256"):
            self.adopt()
        self.files[h.TREE / h.ADOPTION_DOCUMENT] = before
        self.assertEqual(actual["task"], 10)

    def test_historical_and_recovery_records_then_current_selection(self):
        hashes = h.check_records()
        self.assertEqual(hashes["guardSha256"], "8d999f4af9ccee70d9903755c0a0d4594e05a70e81310c9026a879991f902c62")
        self.assertEqual(hashes["ackSha256"], "808a552c4afbfdb77e9663979ba44f6068f11b6af7611adb633af4bbd2991df3")
        self.assertEqual(hashes["recoveryAckSha256"], "096cbdc38cb350b756c6958a0982d31277e4f0a4bc2f7a10e4f24fd1fc40b767")
        with patch.object(h, "run", lambda argv: json.dumps(self.current)):
            self.assertEqual(h.selected(), self.current)
        for path in (h.PARENT_GUARD, h.PARENT_ACK, h.RECOVERY_ACK):
            before = self.files[path]
            self.files[path] += b" "
            with self.subTest(path=path.name), self.assertRaisesRegex(RuntimeError, "guard/ACK mismatch"):
                h.check_records()
            self.files[path] = before
        old_port = copy.deepcopy(self.current)
        old_port["ports"]["3306/tcp"][0]["HostPort"] = "44727"
        with patch.object(h, "run", lambda argv: json.dumps(old_port)):
            with self.assertRaisesRegex(RuntimeError, "loopback port"):
                h.selected()
        altered = copy.deepcopy(self.current)
        altered["mounts"][0]["RW"] = not altered["mounts"][0]["RW"]
        with patch.object(h, "run", lambda argv: json.dumps(altered)):
            with self.assertRaisesRegex(RuntimeError, "mount guard"):
                h.selected()

    def test_task10_overlay_literal_allowlist_to_task11(self):
        for ref in (h.CID, self.current["name"].removeprefix("/")):
            output = self.clone(dict(self.values, AMA_TEST_CONTAINER=ref))
            self.assertEqual(set(output), {
                "AMA_TEST_DATABASE_URL", "AMA_TEST_DATABASE_USERNAME",
                "AMA_TEST_DATABASE_PASSWORD", "AMA_TEST_CONTAINER", "AMA_TASK11_OWNER",
                "AMA_TASK11_RUN_ROOT", "AMA_TASK11_S3_ENV", "AMA_TASK11_PKI_ROOT"})
            self.assertEqual(output["AMA_TEST_DATABASE_URL"], "jdbc:mysql://127.0.0.1:46151/" + self.schema)
            self.assertEqual(output["AMA_TEST_CONTAINER"], h.CID)
            self.assertEqual(output["AMA_TASK11_OWNER"], self.owner)
            self.assertEqual(output["AMA_TASK11_RUN_ROOT"], str(self.folder))
            for suffix in ("S3_ENV", "PKI_ROOT"):
                self.assertEqual(output["AMA_TASK11_" + suffix], self.values["AMA_TASK10_" + suffix])
            for suffix in ("USERNAME", "PASSWORD"):
                self.assertEqual(output["AMA_TEST_DATABASE_" + suffix],
                                 self.values["AMA_TEST_DATABASE_" + suffix])
        for ref in (h.CID[:12], "foreign-name", self.current["name"], "127.0.0.1"):
            with self.subTest(ref=ref), self.assertRaisesRegex(RuntimeError, "reference/credential"):
                self.clone(dict(self.values, AMA_TEST_CONTAINER=ref))
        for change in (
            {"AMA_TEST_DATABASE_URL": "jdbc:mysql://127.0.0.1:44727/" + h.TASK10_SCHEMA},
            {"AMA_TASK10_RUN_ROOT": str(h.TOOLS / "other-owner")},
            {"AMA_TASK10_OWNER": "other-owner"},
        ):
            with self.subTest(change=tuple(change)), self.assertRaisesRegex(RuntimeError, "reference/credential"):
                self.clone(dict(self.values, **change))
        bad = dict(self.values)
        bad["AMA_TASK09_OWNER"] = bad.pop("AMA_TASK10_OWNER")
        with self.assertRaisesRegex(RuntimeError, "allowlist"):
            self.clone(bad)

    def test_preserved_guard_functions_and_order(self):
        old = ast.parse(OLD)
        current = ast.parse(SOURCE)
        def function(tree, name):
            return next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == name)
        for name in ("inside_tools", "private_owned", "public_file", "run",
                     "server", "write_private", "save_state"):
            with self.subTest(function=name):
                self.assertEqual(ast.dump(function(old, name), include_attributes=False),
                                 ast.dump(function(current, name), include_attributes=False))
        provision = function(current, "provision")
        calls = [n.value for n in provision.body
                 if isinstance(n, ast.Assign) and isinstance(n.value, ast.Call)]
        names = [n.func.id for n in calls if isinstance(n.func, ast.Name)]
        self.assertLess(names.index("check_records"), names.index("selected"))
        self.assertLess(names.index("selected"), names.index("server"))
        self.assertLess(names.index("server"), names.index("env_clone"))
        self.assertLess(names.index("env_clone"), names.index("mysql"))
        clone = next(n for n in calls if isinstance(n.func, ast.Name) and n.func.id == "env_clone")
        self.assertEqual(ast.unparse(clone.args[-1]), "before_selected")
        self.assertTrue(h.OWNER_RE.fullmatch(self.owner))
        self.assertTrue(h.SCHEMA_RE.fullmatch(self.schema))
        self.assertFalse(h.OWNER_RE.fullmatch(self.owner.replace("task11", "task10")))
        self.assertIn("--execute-reviewed-task11-provisioning", SOURCE)
        self.assertIn("--task10-adoption-evidence", SOURCE)
        self.assertIn('schema.replace("_", "\\\\_")', SOURCE)
        self.assertIn("PARTIAL_OR_UNKNOWN_OUTCOME_ROOT_INSPECTION_REQUIRED", SOURCE)


if __name__ == "__main__":
    unittest.main(verbosity=2)
