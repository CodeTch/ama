"""Capture public per-run raw/XML/source evidence for precise batch unit selectors."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "artifacts/phase-b/b06-archives/batch"
SOURCES = [
    "backend/core/src/main/java/ama/transcript/BatchFingerprint.java",
    "backend/core/src/test/java/ama/transcript/BatchFingerprintTest.java",
    "backend/core/src/main/java/ama/canonical/CanonicalValue.java",
    "backend/core/src/main/java/ama/canonical/CanonicalValueCodec.java",
    "backend/core/src/test/java/ama/canonical/CanonicalValueCodecTest.java",
    "backend/contracts/src/main/java/ama/contract/MinutesContracts.java",
    "tools/checks/run.py", "tools/b06_archives_batch_vector.py", "tools/b06_archives_batch_run.py",
] + ["backend/core/src/test/resources/ama/transcript/" + name for name in
     [".gitattributes", "batch-v1.literal.json", "batch-v1.literal.hex", "batch-v1.literal.bytes", "batch-v1.literal.sha256"]]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("label")
    parser.add_argument("selector", choices=["BatchFingerprintTest", "CanonicalValueCodecTest", "MinutesContractsTest"])
    args = parser.parse_args()
    assert args.label.replace("-", "").isalnum()
    run = EVIDENCE / args.label
    run.mkdir(parents=True, exist_ok=False)
    (EVIDENCE / ".gitattributes").write_text("* -text\n")
    hashes = []
    for name in SOURCES:
        source = ROOT / name
        target = run / "source" / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
        hashes.append(hashlib.sha256(source.read_bytes()).hexdigest() + "  " + name)
    (run / "source-sha256.txt").write_text("\n".join(hashes) + "\n")
    command = ["python3", "tools/checks/run.py", "unit", args.selector]
    (run / "command.json").write_text(json.dumps(command) + "\n")
    with (run / "stdout.log").open("wb") as out, (run / "stderr.log").open("wb") as err:
        result = subprocess.run(command, cwd=ROOT, stdout=out, stderr=err, check=False)
    (run / "exit.txt").write_text(str(result.returncode) + "\n")
    for source in sorted(ROOT.glob("backend/*/target/surefire-reports/TEST-*.xml")):
        target = run / "xml" / source.relative_to(ROOT)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
    print(args.label + ": exit=" + str(result.returncode))
    print((run / "stdout.log").read_text()[-4000:])
    print((run / "stderr.log").read_text()[-2000:])
    return result.returncode


if __name__ == "__main__":
    raise SystemExit(main())
