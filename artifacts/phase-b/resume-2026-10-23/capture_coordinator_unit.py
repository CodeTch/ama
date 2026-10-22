#!/usr/bin/env python3
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[3]
archive = Path(__file__).resolve().parent
head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root).decode().strip()
reports = sorted(root.glob('backend/*/target/surefire-reports/TEST-*.xml'))
counts = dict(tests=0, failures=0, errors=0, skipped=0)
xmls = []
for source in reports:
    suite = ET.fromstring(source.read_bytes())
    for key in counts:
        counts[key] += int(suite.get(key, 0))
    assert len(suite.findall('testcase')) == int(suite.get('tests'))
    dest = archive / 'junit' / source.relative_to(root).parts[1] / source.name
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, dest)
    xmls.append({'path': dest.relative_to(archive).as_posix(), 'sha256': hashlib.sha256(dest.read_bytes()).hexdigest()})
assert counts == dict(tests=179, failures=0, errors=0, skipped=0), counts
paths = subprocess.check_output(['git', 'diff', '--name-only', '0676a21311f2b2193f73a5e096f4a5605ee9b41f', head], cwd=root).decode().splitlines()
sources = [{'path': p, 'sha256': hashlib.sha256((root / p).read_bytes()).hexdigest()} for p in paths if p.startswith('backend/') or p == 'tools/checks/database_design.py']
result = {'sourceCommit': head, 'command': 'env JAVA_HOME=/home/tanren/ama/.worktrees/phase-a/.tools/zulu21.52.203-ca-jdk21.0.12.1-linux_x64 python3 tools/checks/run.py unit', 'exitCode': 0, 'suites': len(reports), **counts, 'xml': xmls, 'changedSourceHashes': sources, 'overlap': 'Includes all 112 B07 owned cases; do not add counts as distinct tests', 'noise': 'Existing unchanged integration-tests DeclaredErrorResponse.java deprecated API compiler note; no new warning in changed source', 'scope': 'Fresh Java unit execution and compilation on adopted coordination branch; no IT/DB/S3/runtime service execution'}
(archive / 'coordinator-unit-summary.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
print(json.dumps({k: result[k] for k in ('sourceCommit', 'suites', 'tests', 'failures', 'errors', 'skipped', 'scope')}))