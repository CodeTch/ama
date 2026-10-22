#!/usr/bin/env python3
"""Read-only byte receipt audit; no services or private environments."""
import hashlib
import json
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[3]
checkpoint = root / 'artifacts/checkpoints/2026-10-22-development-pause'
receipt_path = checkpoint / 'task-B-04-registry-integration-byte-receipt.json'
receipt = json.loads(receipt_path.read_bytes())

def sha(data):
    return hashlib.sha256(data).hexdigest()

mismatches = []
for item in receipt['paths']:
    actual = (root / item['path']).read_bytes()
    if len(actual) != item['size'] or sha(actual) != item['sha256']:
        mismatches.append(item['path'])
reviews = {}
for name, expected in receipt['reviewHashes'].items():
    actual = sha((checkpoint / name).read_bytes())
    reviews[name] = {'sha256': actual, 'matchesReceipt': actual == expected}
plans = {
    'docs/superpowers/plans/2026-10-21-ama-delivery-plan.md': 'a532dec6e94c53f0e81ae92f14b4f84b6a56cf9d68c37bd6e27d7903f911ffa9',
    'docs/superpowers/plans/2026-10-21-ama-phase-b.md': 'bfa789f77b5ca90725b2c0f32977993c260ac79ab22c2fff76cd775cc104c862',
}
plan_checks = {p: sha((root / p).read_bytes()) == expected for p, expected in plans.items()}
sql_paths = sorted(root.glob('backend/adapters/src/main/resources/db/migration/*.sql'))
sql_changed = []
for path in sql_paths:
    rel = path.relative_to(root).as_posix()
    old = subprocess.check_output(['git', 'show', '0676a21311f2b2193f73a5e096f4a5605ee9b41f:' + rel], cwd=root)
    if old != path.read_bytes():
        sql_changed.append(rel)
result = {
    'mainBaseline': '0676a21311f2b2193f73a5e096f4a5605ee9b41f',
    'scope': 'B04 reviewed delivery custody on resumed main; no new runtime qualification',
    'receiptSha256': sha(receipt_path.read_bytes()),
    'pathsChecked': len(receipt['paths']),
    'bytesChecked': sum(x['size'] for x in receipt['paths']),
    'mismatches': mismatches,
    'reviewInputs': reviews,
    'userPlansUnchanged': plan_checks,
    'formalSqlCount': len(sql_paths),
    'formalSqlChangedFromResumeMain': sql_changed,
    'testsRerun': False,
    'q401': 'UNKNOWN; preserved incident is not closed by this custody audit',
}
print(json.dumps(result, ensure_ascii=False, indent=2))
assert not mismatches
assert all(x['matchesReceipt'] for x in reviews.values())
assert all(plan_checks.values())
assert len(sql_paths) == 12 and not sql_changed