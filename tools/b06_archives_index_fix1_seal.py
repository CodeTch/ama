"""Seal fixed current delivery separately; never rewrite or reinterpret f58 historical seals."""
import hashlib
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
HIST = "f58bff56c0e823b1d542d623e8db9b21e011635f"
OLD = "artifacts/phase-b/b06-archives/index"
FIX = OLD + "/fix1"
OUT = ROOT / FIX
CHANGED_OLD = {
    "backend/adapters/src/main/java/ama/config/file/ControlledArchiveReleaseWriter.java",
    "backend/adapters/src/main/java/ama/config/file/ControlledConfigArchiveSource.java",
    "backend/adapters/src/main/java/ama/config/file/ProtectedArchiveFiles.java",
}
NEW_SOURCES = {
    "backend/adapters/src/test/java/ama/config/file/ControlledArchiveFix1Test.java",
    "backend/adapters/src/test/java/ama/config/file/ControlledArchiveFifoProbe.java",
    "tools/b06_archives_index_fix1_run.py",
    "tools/b06_archives_index_fix1_seal.py",
    "docs/acceptance/b/task-B-06-archives-index-fix1-report.md",
}


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def historical(name):
    return subprocess.check_output(["git", "show", HIST + ":" + name], cwd=ROOT)


old_paths = set(historical(OLD + "/ALLOWLIST.txt").decode().splitlines())
old_blobs = {name: historical(name) for name in old_paths}
historical_entries = historical(OLD + "/SHA256SUMS").decode().splitlines()
for line in historical_entries:
    digest, name = line.split("  ", 1)
    assert sha(old_blobs[name]) == digest, ("historical Git blob mismatch", name)
for name in old_paths - CHANGED_OLD:
    assert (ROOT / name).read_bytes() == old_blobs[name], ("protected history changed", name)
modified = set(subprocess.check_output(["git", "diff", HIST, "--name-only", "--diff-filter=MDRCTUXB"], cwd=ROOT, text=True).splitlines())
assert modified == CHANGED_OLD, ("unexpected existing path change", modified)
snapshot_count = 0
for manifest in sorted((ROOT / OLD).rglob("source-SHA256SUMS")):
    for line in manifest.read_text().splitlines():
        digest, name = line.split("  ", 1)
        assert sha((manifest.parent / "source" / name).read_bytes()) == digest, ("saved snapshot mismatch", manifest, name)
        snapshot_count += 1
final = OUT / "final-current-28/source-SHA256SUMS"
final_count = 0
for line in final.read_text().splitlines():
    digest, name = line.split("  ", 1)
    assert sha((ROOT / name).read_bytes()) == digest, ("final source association mismatch", name)
    final_count += 1
(OUT / "historical-and-source-verification.json").write_text(json.dumps(dict(
    historicalGitCommit=HIST,
    originalManifestGitBlobEntriesVerified=len(historical_entries),
    originalPaths= len(old_paths),
    unchangedHistoricalWorktreePaths=len(old_paths - CHANGED_OLD),
    modifiedOriginalAdapterPaths=sorted(CHANGED_OLD),
    originalManifestInterpretation="Historical source/report seal remains bound to f58 Git blobs, not current modified adapter source.",
    savedSnapshotEntriesVerified=snapshot_count,
    finalRunSourceEntriesMatchingCurrent=final_count,
), indent=2) + "\n")
summary = []
for run in sorted(p for p in OUT.iterdir() if p.is_dir()):
    command = run / "command.json"
    if not command.exists():
        continue
    suites = []
    for xml in sorted(run.glob("xml/*/TEST-*.xml")):
        suite = ET.parse(xml).getroot()
        suites.append(dict(name=suite.attrib["name"], **{key: int(suite.attrib.get(key, "0")) for key in ["tests", "failures", "errors", "skipped"]}))
    summary.append(dict(run=run.name, command=json.loads(command.read_text()), exit=int((run / "exit-code.txt").read_text()), suites=suites))
(OUT / "runs.json").write_text(json.dumps(summary, indent=2) + "\n")
current_sources = {line.split("  ", 1)[1] for line in historical(OLD + "/delivery-SHA256SUMS").decode().splitlines()} | NEW_SOURCES
(OUT / "CURRENT-SOURCE-SHA256SUMS").write_text("".join(f"{sha((ROOT / name).read_bytes())}  {name}\n" for name in sorted(current_sources)))
generated = {FIX + "/" + name for name in ["FIX1-ALLOWLIST.txt", "FULL-CURRENT-ALLOWLIST.txt", "FULL-CURRENT-SHA256SUMS"]}
full = old_paths | NEW_SOURCES | {str(p.relative_to(ROOT)) for p in OUT.rglob("*") if p.is_file()} | generated
assert ".gitattributes" not in full
changed = (full - old_paths) | CHANGED_OLD
(OUT / "FIX1-ALLOWLIST.txt").write_text("\n".join(sorted(changed)) + "\n")
(OUT / "FULL-CURRENT-ALLOWLIST.txt").write_text("\n".join(sorted(full)) + "\n")
manifest = FIX + "/FULL-CURRENT-SHA256SUMS"
(ROOT / manifest).write_text("".join(f"{sha((ROOT / name).read_bytes())}  {name}\n" for name in sorted(full) if name != manifest))
print("PASS historical Git seal", len(historical_entries), "preserved old paths", len(old_paths - CHANGED_OLD), "saved snapshots", snapshot_count, "final source associations", final_count)
print("SEALED fix1 changed paths", len(changed), "full-current public paths", len(full), "current source paths", len(current_sources))
