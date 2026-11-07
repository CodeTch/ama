"""Restart and execute one real-browser publication suite under the existing lifecycle lock.

Use the normal local_app start implementation and retained settings/database. Holding
its existing lock through the browser run prevents another restart between health
verification and the first real user interaction. No alternate ports or database.
"""
import fcntl
import json
from pathlib import Path
import subprocess
import sys
import os
import time
import traceback
import local_app

if __name__ == '__main__':
    if len(sys.argv) != 3:
        raise SystemExit('Expected a repository browser script and evidence directory')
    root = local_app.ROOT
    script = Path(sys.argv[1]).resolve()
    if script.parent != root / 'tools/dev' or not script.name.startswith('knowledge_publish_') or script.suffix != '.mjs':
        raise SystemExit('Expected the checked-in publication browser suite')
    local_app.load_local_settings()
    with (local_app.RUNTIME / 'lifecycle.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        state = json.loads((local_app.RUNTIME / 'state.json').read_text())
        if state.get('owner') != local_app.OWNER:
            raise SystemExit('Local state belongs to another checkout')
        evidence = Path(sys.argv[2]).resolve()
        evidence.mkdir(parents=True, exist_ok=True)
        resume = evidence / 'resume-validation'
        restart = True
        while True:
            try:
                if restart:
                    state = json.loads((local_app.RUNTIME / 'state.json').read_text())
                    local_app.start(state, bundled_frontend=True)
                code = subprocess.run(['node', str(script), str(evidence)], cwd=root).returncode
            except Exception:
                traceback.print_exc()
                code = 1
            if code == 0 or os.environ.get('AMA_KP_KEEP_VALIDATION_LOCK') != '1':
                raise SystemExit(code)
            print('Validation failed; retained the lifecycle lock for repair. Create '
                  + str(resume) + ' containing retry, restart, or stop.', flush=True)
            deadline = time.monotonic() + 900
            while not resume.exists() and time.monotonic() < deadline:
                time.sleep(1)
            if not resume.exists():
                raise SystemExit(code)
            action = resume.read_text().strip()
            resume.unlink()
            if action == 'stop':
                raise SystemExit(code)
            if action == 'restart':
                restart = True
            elif action == 'retry':
                restart = False
            elif action != 'retry':
                raise SystemExit('Expected retry, restart, or stop')
