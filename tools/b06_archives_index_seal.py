"""Explicit public-only delivery allowlist and SHA manifest for Task2."""
import hashlib
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "artifacts/phase-b/b06-archives/index"
BASE = "ad9960595ab5de6eef7760e2c9094d23666074cb"
PUBLIC = [
    "backend/core/src/main/java/ama/config/ConfigArchiveSource.java",
    "backend/core/src/main/java/ama/config/ConfigArchiveIndex.java",
    "backend/core/src/main/java/ama/config/ProcessingManifestRefs.java",
    "backend/adapters/src/main/java/ama/config/json/StrictConfigIndexReader.java",
    "backend/adapters/src/main/java/ama/config/file/ControlledConfigArchiveSource.java",
    "backend/adapters/src/main/java/ama/config/file/ControlledArchiveReleaseWriter.java",
    "backend/adapters/src/main/java/ama/config/file/ProtectedArchiveFiles.java",
    "backend/adapters/src/test/java/ama/config/file/ControlledConfigArchiveSourceTest.java",
    "docs/acceptance/b/task-B-06-archives-index-report.md",
    "tools/b06_archives_index_run.py",
    "tools/b06_archives_index_vectors.py",
    "tools/b06_archives_index_seal.py",
]
PUBLIC += [str(p.relative_to(ROOT)) for p in (ROOT / "backend/adapters/src/test/resources/ama/config/file/b06").glob("*") if p.is_file()]
EVIDENCE.mkdir(parents=True, exist_ok=True)
(EVIDENCE / ".gitattributes").write_text("* -text\n", encoding="ascii")
summary = []
for run in sorted(p for p in EVIDENCE.iterdir() if p.is_dir()):
    command = run / "command.json"
    if not command.exists():
        continue
    counts = []
    for path in sorted(run.glob("xml/*/TEST-*.xml")):
        suite = ET.parse(path).getroot()
        counts.append({"class": suite.attrib["name"], **{key: int(suite.attrib.get(key, "0")) for key in ["tests", "failures", "errors", "skipped"]}})
    summary.append(dict(run=run.name, command=json.loads(command.read_text()), exit=int((run / "exit-code.txt").read_text()), suites=counts))
(EVIDENCE / "runs.json").write_text(json.dumps(summary, indent=2) + "\n")
original = subprocess.check_output(["git", "diff", BASE, "--name-status", "--diff-filter=MDRCTUXB"], cwd=ROOT, text=True)
assert not original, "Existing tracked paths changed: " + original
(EVIDENCE / "preservation.json").write_text(json.dumps(dict(base=BASE, existing_tracked_changes=[], scope="All existing tracked files unchanged against BASE; new task files only. No DB/Docker operation was run."), indent=2) + "\n")
(EVIDENCE / "delivery-SHA256SUMS").write_text("".join(f"{hashlib.sha256((ROOT / name).read_bytes()).hexdigest()}  {name}\n" for name in sorted(PUBLIC)))
PUBLIC += [str(p.relative_to(ROOT)) for p in EVIDENCE.rglob("*") if p.is_file() and p.name not in {"ALLOWLIST.txt", "SHA256SUMS"}]
PUBLIC += [str((EVIDENCE / name).relative_to(ROOT)) for name in ["ALLOWLIST.txt", "SHA256SUMS"]]
PUBLIC = sorted(set(PUBLIC))
(EVIDENCE / "ALLOWLIST.txt").write_text("\n".join(PUBLIC) + "\n")
manifest = EVIDENCE / "SHA256SUMS"
manifest.write_text("".join(f"{hashlib.sha256((ROOT / name).read_bytes()).hexdigest()}  {name}\n" for name in PUBLIC if ROOT / name != manifest))
print("sealed", len(PUBLIC), "public paths; all existing tracked files preserved")
