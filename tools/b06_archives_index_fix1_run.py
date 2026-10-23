"""Versioned fix1 exact-unit raw/XML/source evidence. Original runs and seals remain immutable."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
label, selector = sys.argv[1:]
out = ROOT / "artifacts/phase-b/b06-archives/index/fix1" / label
out.mkdir(parents=True, exist_ok=False)
sources = [
    "backend/core/src/main/java/ama/config/ConfigArchiveSource.java",
    "backend/core/src/main/java/ama/config/ConfigArchiveIndex.java",
    "backend/core/src/main/java/ama/config/ProcessingManifestRefs.java",
    "backend/adapters/src/main/java/ama/config/json/StrictConfigIndexReader.java",
    "backend/adapters/src/main/java/ama/config/file/ControlledConfigArchiveSource.java",
    "backend/adapters/src/main/java/ama/config/file/ControlledArchiveReleaseWriter.java",
    "backend/adapters/src/main/java/ama/config/file/ProtectedArchiveFiles.java",
    "backend/adapters/src/test/java/ama/config/file/ControlledConfigArchiveSourceTest.java",
    "backend/adapters/src/test/java/ama/config/file/ControlledArchiveFix1Test.java",
    "backend/adapters/src/test/java/ama/config/file/ControlledArchiveFifoProbe.java",
    "tools/b06_archives_index_fix1_run.py",
]
sources += [str(p.relative_to(ROOT)) for p in (ROOT / "backend/adapters/src/test/resources/ama/config/file/b06").glob("*")]
hashes = []
for name in sources:
    path = ROOT / name
    if not path.is_file():
        continue
    target = out / "source" / name
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(path, target)
    hashes.append(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {name}")
(out / "source-SHA256SUMS").write_text("\n".join(hashes) + "\n")
command = [sys.executable, "tools/checks/run.py", "unit", selector]
(out / "command.json").write_text(json.dumps(command) + "\n")
env = os.environ.copy()
env["JAVA_HOME"] = "/home/tanren/ama/.worktrees/phase-a/.tools/zulu21.52.203-ca-jdk21.0.12.1-linux_x64"
env["PATH"] = env["JAVA_HOME"] + "/bin:" + env["PATH"]
with (out / "stdout.log").open("wb") as stdout, (out / "stderr.log").open("wb") as stderr:
    result = subprocess.run(command, cwd=ROOT, env=env, stdout=stdout, stderr=stderr, check=False)
(out / "exit-code.txt").write_text(str(result.returncode) + "\n")
for path in ROOT.glob("backend/*/target/surefire-reports/TEST-*.xml"):
    target = out / "xml" / path.parent.parent.parent.name / path.name
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(path, target)
print((out / "stdout.log").read_text()[-11000:])
print((out / "stderr.log").read_text()[-3000:])
print("EXIT", result.returncode, "EVIDENCE", out.relative_to(ROOT))
raise SystemExit(result.returncode)
