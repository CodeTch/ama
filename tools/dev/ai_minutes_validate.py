"""Restart AMA and validate a minutes browser scenario under one lifecycle lock."""
import fcntl, json, subprocess, sys
from pathlib import Path
import local_app

if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('Expected an ai_minutes browser scenario')
    root=local_app.ROOT
    script=Path(sys.argv[1]).resolve()
    if script.parent != root/'tools/dev' or not script.name.startswith('ai_minutes_') or script.suffix != '.mjs':
        raise SystemExit('Expected the project minutes browser scenario')
    if Path.cwd()!=root or subprocess.check_output(['git','branch','--show-current'],text=True).strip()!='main':
        raise SystemExit('AMA main in the project root is required')
    local_app.load_local_settings()
    with (local_app.RUNTIME/'lifecycle.lock').open('a') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX)
        state=json.loads((local_app.RUNTIME/'state.json').read_text())
        if state.get('owner')!=local_app.OWNER:
            raise SystemExit('Local state belongs to another checkout')
        local_app.start(state,bundled_frontend=True)
        raise SystemExit(subprocess.run(['node',str(script)],cwd=root).returncode)
