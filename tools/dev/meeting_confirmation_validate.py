"""Restart and execute one real-browser confirmation suite under the existing lifecycle lock.

Use the normal local_app start implementation and retained settings/database. Holding
its existing lock through the browser run prevents another restart between health
verification and the first real user interaction. No alternate ports or database.
"""
import fcntl
import json
from pathlib import Path
import subprocess
import sys
import local_app

if __name__ == '__main__':
    if len(sys.argv) != 3:
        raise SystemExit('Expected a repository browser script and evidence directory')
    root = local_app.ROOT
    script = Path(sys.argv[1]).resolve()
    if script.parent != root / 'tools/dev' or not script.name.startswith('meeting_confirmation_') or script.suffix != '.mjs':
        raise SystemExit('Expected the checked-in confirmation browser suite')
    local_app.load_local_settings()
    with (local_app.RUNTIME / 'lifecycle.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        state = json.loads((local_app.RUNTIME / 'state.json').read_text())
        if state.get('owner') != local_app.OWNER:
            raise SystemExit('Local state belongs to another checkout')
        evidence = Path(sys.argv[2]).resolve()
        evidence.mkdir(parents=True, exist_ok=True)
        build_log = local_app.RUNTIME / 'build.log'
        build_offset = build_log.stat().st_size if build_log.exists() else 0
        try:
            local_app.start(state)
        finally:
            if build_log.exists():
                with build_log.open('rb') as source:
                    source.seek(build_offset)
                    (evidence / 'restart-build.log').write_bytes(source.read())
        result = subprocess.run(['node', str(script), str(Path(sys.argv[2]).resolve())], cwd=root)
        raise SystemExit(result.returncode)
