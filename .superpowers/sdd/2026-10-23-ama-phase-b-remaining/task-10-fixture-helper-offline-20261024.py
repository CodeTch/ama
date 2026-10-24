"""Small offline checks only; never calls provision/main or actual private/runtime boundaries."""
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
HELPER = W / "task-10-fixture-provisioning-prepared-20261024.py"
ORIGINAL = W / "task-09-fixture-provisioning-runtime-fix02-20261024.py"
SOURCE = HELPER.read_text(encoding="utf-8")
OLD = ORIGINAL.read_text(encoding="utf-8")
PUBLIC = {W / n: (W / n).read_bytes() for n in (
    "task-09-root-fixture-application-ack-20261024.json",
    "task-09-fixture-creation-guard-20261024.json")}


def denied(*args, **kwargs):
    raise AssertionError("actual runtime/private boundary forbidden in offline check")


# The reviewed module has only definitions/constants/imports at module level.
# Use a non-main name; prohibit process execution and writes throughout loading/tests.
with patch.object(subprocess, "run", denied), patch.object(os, "open", denied):
    h = types.ModuleType("task10_offline_only")
    h.__file__ = str(HELPER)
    exec(compile(SOURCE, str(HELPER), "exec"), h.__dict__)


class Offline(unittest.TestCase):
    def setUp(self):
        self.files = dict(PUBLIC)
        self.parent = json.loads(self.files[h.PARENT_GUARD])["selected"]
        self.doc = b"Synthetic public adoption document; not actual adoption.\n"
        self.ack = dict(task=9, status="COMPLETE_ADOPTED",
            base="cff858e163ca7dea774dcf05c707ef7551956156",
            sourceCommit="1" * 40, adoptionCommit="2" * 40,
            reviewSpec="PASS", reviewQuality="Approved", openCriticalImportant=0,
            adoptionDocument=h.ADOPTION_DOCUMENT,
            adoptionDocumentSha256=h.sha(self.doc))
        self.files[h.TREE / h.ADOPTION_DOCUMENT] = self.doc
        self.owner = "task10-0123456789abcdef0123456789abcdef"
        self.schema = "ama_t10_0123456789ab_behavior"
        self.folder = h.TOOLS / self.owner
        self.values = dict(AMA_TEST_DATABASE_URL="jdbc:mysql://127.0.0.1:44727/" + h.TASK9_SCHEMA,
            AMA_TEST_DATABASE_USERNAME="ama_t08_848e93aace8b",
            AMA_TEST_DATABASE_PASSWORD="fabricated-offline-token",
            AMA_TEST_CONTAINER=h.CID, AMA_TASK09_OWNER=h.TASK9_OWNER,
            AMA_TASK09_RUN_ROOT=str(h.TOOLS / h.TASK9_OWNER),
            AMA_TASK09_S3_ENV=str(h.TASK5_ENV), AMA_TASK09_PKI_ROOT=str(h.TASK5_PKI))
        def read_bytes(path):
            if path not in self.files:
                raise AssertionError("unexpected read outside explicit memory map")
            return self.files[path]
        def public(path):
            if path not in (h.ADOPTION_ACK, h.TREE / h.ADOPTION_DOCUMENT):
                raise AssertionError("unexpected public evidence path")
            return path
        def private(path, owner, directory=False):
            accepted = {(h.TASK9_ENV, h.TASK9_OWNER, False),
                (h.TASK5_ENV, h.TASK5_OWNER, False),
                (h.TASK5_PKI, h.TASK5_OWNER, True),
                (h.TASK5_PKI / "keystore-password", h.TASK5_OWNER, False)}
            if (path, owner, directory) not in accepted:
                raise AssertionError("unexpected private metadata request")
            return path
        patches = [patch.object(Path, "read_bytes", read_bytes),
            patch.object(Path, "read_text", lambda p, **kw: read_bytes(p).decode()),
            patch.object(Path, "open", denied), patch.object(Path, "stat", denied),
            patch.object(Path, "resolve", denied), patch.object(Path, "mkdir", denied),
            patch.object(Path, "write_text", denied), patch.object(Path, "write_bytes", denied),
            patch.object(subprocess, "run", denied), patch.object(os, "open", denied),
            patch.object(os, "chmod", denied), patch.object(os, "replace", denied),
            patch.object(h, "public_file", public), patch.object(h, "private_owned", private),
            patch.object(h, "mysql", denied), patch.object(h, "run", denied),
            patch.object(h, "provision", denied), patch.object(h, "main", denied)]
        for p in patches:
            p.start()
            self.addCleanup(p.stop)

    def adopt(self, ack=None, **overrides):
        self.files[h.ADOPTION_ACK] = json.dumps(self.ack if ack is None else ack).encode()
        args = argparse.Namespace(task9_adoption_evidence=str(h.ADOPTION_ACK),
            task9_adoption_sha256=h.sha(self.files[h.ADOPTION_ACK]))
        for key, value in overrides.items():
            setattr(args, key, value)
        return h.check_adoption(args)

    def clone(self, values=None):
        vals = self.values if values is None else values
        raw = "".join("export " + k + "=" + shlex.quote(v) + "\n" for k, v in vals.items()).encode()
        self.files[h.TASK9_ENV] = raw
        result = h.env_clone(self.schema, self.owner, self.folder, self.parent)
        self.assertEqual(self.files[h.TASK9_ENV], raw)
        return dict((line.split("=", 1)[0].removeprefix("export "),
            shlex.split(line.split("=", 1)[1])[0]) for line in result.splitlines())

    def test_future_adoption_gate(self):
        self.assertEqual(self.adopt()["sourceCommit"], "1" * 40)
        bad = dict(task=7, status="PREPARED", base="0" * 40,
            sourceCommit="bad", adoptionCommit="bad", reviewSpec="FAIL",
            reviewQuality="Pending", openCriticalImportant=1,
            adoptionDocument="other.md", adoptionDocumentSha256="0" * 64)
        for key, value in bad.items():
            with self.subTest(field=key), self.assertRaises(RuntimeError):
                self.adopt(dict(self.ack, **{key: value}))
        for path in (str(h.TASK9_ENV), str(h.W / "other.json")):
            with self.subTest(path=path), self.assertRaisesRegex(RuntimeError, "fixed public"):
                self.adopt(task9_adoption_evidence=path)
        with self.assertRaisesRegex(RuntimeError, "SHA-256 mismatch"):
            self.adopt(task9_adoption_sha256="0" * 64)

    def test_actual_public_parent_binding_and_fresh_selection(self):
        hashes = h.check_records()
        self.assertEqual(hashes["ackSha256"], "6f926c62b9710dab9c889eee104543b3250bfd5e33fc23d67fb655677e4c7e5d")
        with patch.object(h, "run", lambda argv: json.dumps(self.parent)):
            self.assertEqual(h.selected(), self.parent)
        for path in (h.PARENT_ACK, h.PARENT_GUARD):
            before = self.files[path]
            self.files[path] += b" "
            with self.assertRaisesRegex(RuntimeError, "guard/ACK mismatch"):
                h.check_records()
            self.files[path] = before
        altered = copy.deepcopy(self.parent)
        altered["mounts"][0]["RW"] = not altered["mounts"][0]["RW"]
        with patch.object(h, "run", lambda argv: json.dumps(altered)):
            with self.assertRaisesRegex(RuntimeError, "mount guard mismatch"):
                h.selected()

    def test_task9_to_task10_allowlist_and_canonical_cid(self):
        for ref in (h.CID, self.parent["name"].removeprefix("/")):
            output = self.clone(dict(self.values, AMA_TEST_CONTAINER=ref))
            self.assertEqual(set(output), {"AMA_TEST_DATABASE_URL", "AMA_TEST_DATABASE_USERNAME",
                "AMA_TEST_DATABASE_PASSWORD", "AMA_TEST_CONTAINER", "AMA_TASK10_OWNER",
                "AMA_TASK10_RUN_ROOT", "AMA_TASK10_S3_ENV", "AMA_TASK10_PKI_ROOT"})
            self.assertEqual(output["AMA_TEST_CONTAINER"], h.CID)
            self.assertEqual(output["AMA_TEST_DATABASE_URL"], "jdbc:mysql://127.0.0.1:44727/" + self.schema)
            self.assertEqual(output["AMA_TASK10_OWNER"], self.owner)
            self.assertEqual(output["AMA_TASK10_RUN_ROOT"], str(self.folder))
            for suffix in ("S3_ENV", "PKI_ROOT"):
                self.assertEqual(output["AMA_TASK10_" + suffix], self.values["AMA_TASK09_" + suffix])
            for suffix in ("USERNAME", "PASSWORD"):
                self.assertEqual(output["AMA_TEST_DATABASE_" + suffix], self.values["AMA_TEST_DATABASE_" + suffix])
        for ref in (h.CID[:12], "foreign-name", self.parent["name"], "127.0.0.1", h.CID + " "):
            with self.subTest(ref=ref), self.assertRaisesRegex(RuntimeError, "reference/credential"):
                self.clone(dict(self.values, AMA_TEST_CONTAINER=ref))
        bad = dict(self.values)
        bad["AMA_TASK10_OWNER"] = bad.pop("AMA_TASK09_OWNER")
        with self.assertRaisesRegex(RuntimeError, "allowlist"):
            self.clone(bad)
        with self.assertRaisesRegex(RuntimeError, "reference/credential"):
            self.clone(dict(self.values, AMA_TEST_DATABASE_URL="jdbc:mysql://127.0.0.1:44727/other"))

    def test_exact_narrow_adaptation_and_call_order(self):
        expected = OLD
        substitutions = [('Task 9','Task 10'),('task9','task10'),('task09-','task10-'),
            ('ama_t09_','ama_t10_'),('AMA_TASK09_','AMA_TASK10_'),('Task 7','Task 9'),
            ('TASK7','TASK9'),('task7','task9'),('AMA_TASK07_','AMA_TASK09_'),
            ('task07-0cea85a0e4724e78bd7aa6aabfc243bc','task09-2d0e28ea775540f8b3525f9344edf5f0'),
            ('ama_t07_0cea85a0e472_behavior','ama_t09_2d0e28ea7755_behavior'),('task-07-','task-09-'),
            ('task-B-remaining-07-prepared-external-adoption-20261024.md','task-B-remaining-09-minutes-generation-adoption-20261024.md'),
            ('de1611a67ba483afe21f3b29bb69c2d32028efef','cff858e163ca7dea774dcf05c707ef7551956156'),
            ('ack["task"] != 7','ack["task"] != 9')]
        for before, after in substitutions:
            expected = expected.replace(before, after)
        before = '    if (ack.get("base") != "cff858e163ca7dea774dcf05c707ef7551956156"'
        after = '    if (sha(guard_bytes) != "d9b8f75b2dccda19f4e77fa05dc03d512881fa3af9b14ee30bd9c22f5ea6d0f1"\n            or sha(PARENT_ACK.read_bytes()) != "6f926c62b9710dab9c889eee104543b3250bfd5e33fc23d67fb655677e4c7e5d"\n            or ack.get("base") != "cff858e163ca7dea774dcf05c707ef7551956156"'
        self.assertEqual(SOURCE, expected.replace(before, after))
        tree = ast.parse(SOURCE)
        provision = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == "provision")
        calls = [n.value for n in provision.body if isinstance(n, ast.Assign) and isinstance(n.value, ast.Call)]
        names = [n.func.id for n in calls if isinstance(n.func, ast.Name)]
        self.assertLess(names.index("check_records"), names.index("selected"))
        self.assertLess(names.index("selected"), names.index("server"))
        self.assertLess(names.index("server"), names.index("env_clone"))
        clone = next(n for n in calls if isinstance(n.func, ast.Name) and n.func.id == "env_clone")
        self.assertEqual(ast.unparse(clone.args[-1]), "before_selected")
        self.assertTrue(h.OWNER_RE.fullmatch(self.owner))
        self.assertTrue(h.SCHEMA_RE.fullmatch(self.schema))
        self.assertFalse(h.OWNER_RE.fullmatch(self.owner.replace("task10", "task09")))
        self.assertIn('--execute-reviewed-task10-provisioning', SOURCE)
        self.assertIn('--task9-adoption-evidence', SOURCE)


if __name__ == "__main__":
    unittest.main(verbosity=2)
