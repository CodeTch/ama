"""Seal the exact R1 package; no self-hash and no tracked-report/commit cycle."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
BASE = "214a63d978e4c04c0b3aec1c99d8d0ce3c093d9d"
REPORT = "docs/acceptance/b/material-provenance-design-fix1-report.md"
OVERRIDES = [".gitattributes", "docs/acceptance/b/material-provenance-design-prerequisite.md", "docs/database/ama-data-dictionary.md",
             "docs/superpowers/specs/2026-10-21-ama-database-design.md", "docs/superpowers/specs/2026-10-22-ama-material-provenance-addendum.md", "tools/checks/database_design.py"]
MANIFEST = str((HERE / "fix1-manifest.json").relative_to(ROOT))
ALLOWLIST = HERE / "fix1-allowlist.txt"
ALLOW_SHA = HERE / "fix1-allowlist.sha256"


def sha(data):
    return hashlib.sha256(data).hexdigest()


def build():
    paths = set(OVERRIDES + [REPORT, MANIFEST, str(ALLOWLIST.relative_to(ROOT)), str(ALLOW_SHA.relative_to(ROOT))])
    paths.update(str(p.relative_to(ROOT)) for p in HERE.rglob("*") if p.is_file())
    ordered = sorted(paths, key=lambda path: path.encode())
    ALLOWLIST.write_text("\n".join(ordered) + "\n")
    ALLOW_SHA.write_text(sha(ALLOWLIST.read_bytes()) + "  fix1-allowlist.txt\n")
    entries = []
    for path in ordered:
        if path in (MANIFEST, REPORT):
            continue
        raw = (ROOT / path).read_bytes()
        entries.append(dict(path=path, bytes=len(raw), sha256=sha(raw)))
    manifest = dict(scope="D01 R1 design-only fix1 exact package", base=BASE, head="HEAD (sealed externally after commit)",
                    allowlistSha256=sha(ALLOWLIST.read_bytes()), pathCount=len(ordered), coveredFiles=entries,
                    selfExcluded=MANIFEST, reportExcluded=REPORT,
                    exclusionReason="Manifest cannot hash itself; tracked consumer report cites manifest SHA and is covered by Git HEAD, avoiding a cycle.")
    (ROOT / MANIFEST).write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    return manifest


def verify():
    manifest = json.loads((ROOT / MANIFEST).read_text())
    paths = ALLOWLIST.read_text().splitlines()
    assert len(paths) == len(set(paths)) == manifest["pathCount"]
    assert paths == sorted(paths, key=lambda path: path.encode())
    assert sha(ALLOWLIST.read_bytes()) == manifest["allowlistSha256"] == ALLOW_SHA.read_text().split()[0]
    staged = subprocess.check_output(["git", "diff", "--cached", "--name-only", BASE], cwd=ROOT).decode().splitlines()
    assert set(staged) == set(paths), "staged exact path allowlist"
    assert {e["path"] for e in manifest["coveredFiles"]} == set(paths) - {MANIFEST, REPORT}
    for entry in manifest["coveredFiles"]:
        raw = (ROOT / entry["path"]).read_bytes()
        assert len(raw) == entry["bytes"] and sha(raw) == entry["sha256"], entry["path"]
        staged_raw = subprocess.check_output(["git", "show", ":" + entry["path"]], cwd=ROOT)
        assert raw == staged_raw, "staged bytes: " + entry["path"]
    assert (ROOT / REPORT).read_bytes() == subprocess.check_output(["git", "show", ":" + REPORT], cwd=ROOT)
    assert subprocess.run(["git", "diff", "--cached", "--check"], cwd=ROOT).returncode == 0
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=["build", "verify"])
    args = parser.parse_args()
    value = build() if args.mode == "build" else verify()
    print(json.dumps(dict(mode=args.mode, base=BASE, paths=value["pathCount"], coveredFiles=len(value["coveredFiles"]),
                          allowlistSha256=value["allowlistSha256"], manifestSha256=sha((ROOT / MANIFEST).read_bytes()))))
