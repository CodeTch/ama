"""Static-only coverage for the Task 9 fixture helper fix; never import it."""

import ast
import hashlib
from pathlib import Path


W = Path(__file__).resolve().parent
TREE = W.parents[2]
HELPER = W / "task-09-fixture-provisioning-prepared-20261024.py"
NOTE = W / "task-09-fixture-provisioning-prepared-20261024.md"
SNAP = W / "task-09-fixture-helper-before-fix01-20261024"
BASE = {
    HELPER.name: "bcbcea4a99a370077665d97f3fa3d21e5338eb124d1e0f1f041af0b6040a6208",
    NOTE.name: "6dbed8976efcca6bc10a70b9aa02a234b4694a3f81b9d880f719cc5a8b046b29",
    "task-09-fixture-provisioning-prepared-20261024-manifest.json":
        "1052dc075b2303eac942d5eb9f64b57b2f66066a2c1b1b51cbf56f5b4a39d72f",
}


def check(name, condition):
    if not condition:
        raise AssertionError(name)
    print("STATIC_OK", name)


for name, expected in BASE.items():
    check("snapshot:" + name,
          hashlib.sha256((SNAP / name).read_bytes()).hexdigest() == expected)

text = HELPER.read_text(encoding="utf-8")
module = ast.parse(text, filename=str(HELPER))
check("ast.parse", module is not None)
check("tree-level", TREE == Path("/home/tanren/ama/.worktrees/phase-b-completion"))
check("fixed-public-ack", 'task-07-consumer-activation-root-ack-20261024.json' in text
      and 'Path(args.task7_adoption_evidence) != ADOPTION_ACK' in text
      and 'public_file(ADOPTION_ACK)' in text)
for field in ("COMPLETE_ADOPTED", "sourceCommit", "adoptionCommit", "reviewSpec",
              "reviewQuality", "openCriticalImportant", "adoptionDocumentSha256"):
    check("ack-field:" + field, field in text)
check("public-doc-binding", 'public_file(TREE / ADOPTION_DOCUMENT)' in text
      and 'sha(document.read_bytes()) != ack["adoptionDocumentSha256"]' in text)
check("private-owner-path", 'def private_owned(' in text
      and 'current.is_symlink()' in text
      and 'current.resolve(strict=True).is_relative_to(root.resolve(strict=True))' in text)
check("private-inputs", all(s in text for s in (
    'private_owned(PARENT / "secrets", PARENT_OWNER, directory=True)',
    'private_owned(PARENT / "secrets" / name, PARENT_OWNER)',
    'private_owned(TASK7_ENV, TASK7_OWNER)',
    'private_owned(TASK5_ENV, TASK5_OWNER)',
    'private_owned(TASK5_PKI, TASK5_OWNER, directory=True)',
    'private_owned(TASK5_PKI / "keystore-password", TASK5_OWNER)')))
check("pinned-s3-pki", 'values["AMA_TASK07_S3_ENV"] != str(TASK5_ENV)' in text
      and 'values["AMA_TASK07_PKI_ROOT"] != str(TASK5_PKI)' in text)
check("complete-mounts", 'sorted(mounts, key=lambda m: m.get("Destination", ""))' in text
      and 'parent["mounts"]' in text
      and 'actual_mounts' not in text)
check("same-grant-session", 'grant_session_sql = ("SET SESSION sql_mode=' in text
      and '+ grant_sql + "\\nSELECT @@session.sql_mode;\\n")' in text
      and 'effective_grant_mode = mysql(grant_session_sql)' in text)
check("pre-sql-orphan-diagnostic", 'Pre-SQL owner/state creation failed; no SQL was issued.' in text
      and 'folder.mkdir(mode=0o700, exist_ok=False)' in text)
check("no-runtime-provisioning", 'if __name__ == "__main__":' in text
      and 'Config.Env' not in text and 'DROP SCHEMA' not in text
      and 'CREATE USER' not in text)
for schema in ("ama_t09_0123456789ab_behavior", "ama_t09_ffffffffffff_behavior"):
    pattern = schema.replace("_", "\\_")
    check("pure-grant-escape:" + schema,
          pattern.count("\\_") == schema.count("_") and "_" not in pattern.replace("\\_", ""))
print("STATIC_ONLY_DONE; helper was parsed, never imported or executed")
