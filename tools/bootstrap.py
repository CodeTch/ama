"""Provision the pinned Linux x86_64 development JDK/Node in ignored .tools only."""
import hashlib
import json
import pathlib
import platform
import shlex
import tarfile
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]

def main():
    if platform.system() != "Linux" or platform.machine() != "x86_64":
        raise SystemExit("This bootstrap supports Linux/WSL x86_64; use a compatible Java 21 toolchain on other platforms.")
    lock = json.loads((ROOT / "tools/toolchain-lock.json").read_text())
    destination = ROOT / ".tools"
    destination.mkdir(exist_ok=True)
    for tool in ("jdk", "node"):
        entry = lock[tool]
        target = destination / entry["directory"]
        if target.is_dir():
            print(f"Using existing {tool}: {target}")
            continue
        archive = destination / pathlib.PurePosixPath(entry["url"]).name
        if not archive.is_file():
            print(f"Downloading pinned {tool} {entry['version']}", flush=True)
            with urllib.request.urlopen(entry["url"], timeout=60) as response, archive.open("wb") as output:
                while block := response.read(1024 * 1024):
                    output.write(block)
        with archive.open("rb") as source:
            actual = hashlib.file_digest(source, "sha256").hexdigest()
        if actual != entry["sha256"]:
            archive.unlink()
            raise SystemExit(f"Checksum mismatch for {tool}; refused to extract")
        with tarfile.open(archive) as source:
            source.extractall(destination, filter="data")
        print(f"Verified {tool} SHA256 {actual}")
    java = destination / lock["jdk"]["directory"]
    node = destination / lock["node"]["directory"] / "bin"
    (destination / "env.sh").write_text(f"export JAVA_HOME={shlex.quote(str(java))}\nexport PATH={shlex.quote(str(java / 'bin'))}:{shlex.quote(str(node))}:\"$PATH\"\n")
    print("Run: . .tools/env.sh")

if __name__ == "__main__":
    main()
