"""Restart the actual main application and keep its lifecycle stable during browser acceptance."""
import fcntl
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import local_app

ROOT = local_app.ROOT

def main():
    if Path.cwd() != ROOT or subprocess.check_output(['git', 'branch', '--show-current'], text=True).strip() != 'main':
        raise RuntimeError('Project root and main required')
    out = Path(sys.argv[1]).resolve()
    if not out.is_relative_to(ROOT / '.tools/local-app/recording-strict-20261103') or out.exists():
        raise ValueError('A new owned acceptance output directory is required')
    out.mkdir(parents=True)
    local_app.load_local_settings()
    os.environ.update(AMA_CAS_ORIGIN='http://127.0.0.1:8792', AMA_INTEGRATION_KNOWLEDGE_ORIGIN='http://127.0.0.1:8792', AMA_INTEGRATION_MODEL_ORIGIN='http://127.0.0.1:8792')
    results = []
    report = {'pass': False, 'checks': results, 'startedAt': time.strftime('%Y-%m-%dT%H:%M:%S%z')}
    with open(local_app.RUNTIME / 'lifecycle.lock', 'a') as lock:
        print('Waiting for the shared application lifecycle lock...', flush=True)
        fcntl.flock(lock, fcntl.LOCK_EX)
        statefile = local_app.RUNTIME / 'state.json'
        state = json.loads(statefile.read_text())
        if state.get('owner') != local_app.OWNER:
            raise RuntimeError('Foreign local application state')
        local_app.configure_ports(state, 5174, 8182, 8183)
        local_app.save(statefile, state)
        try:
            report['previousPids'] = {role: state.get(role, {}).get('pid') for role in local_app.SERVICE_ROLES}
            for role in local_app.SERVICE_ROLES:
                local_app.stop_process(state.get(role))
            local_app.start(state)
            state = json.loads(statefile.read_text())
            report['runningPids'] = {role: state[role]['pid'] for role in local_app.SERVICE_ROLES}
            report['servicesRestarted'] = all(report['previousPids'][role] != report['runningPids'][role] for role in local_app.SERVICE_ROLES)
            if not report['servicesRestarted']:
                raise RuntimeError('Required service restart could not be verified')
            report['integrationOrigins'] = state['integrationOrigins']
            # Other lifecycle clients may read status after this run; startup/stop
            # waits until these actual browser interactions release the lock.
            for name, script in [('device', 'recording_device_recovery_integration.mjs'), ('processing', 'recording_strict_integration.mjs'), ('edges', 'recording_edge_integration.mjs')]:
                destination = out / name
                print('Running browser scenario:', name, flush=True)
                with (out / (name + '.log')).open('w') as log:
                    result = subprocess.run(['node', 'tools/dev/' + script, str(destination.relative_to(ROOT))], cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
                receipt = json.loads((destination / 'report.json').read_text()) if (destination / 'report.json').exists() else {'pass': False}
                results.append({'name': name, 'exitCode': result.returncode, 'pass': receipt['pass'], 'report': str(destination / 'report.json')})
                if result.returncode or not receipt['pass']:
                    raise RuntimeError('Browser acceptance failed: ' + name)
                print('PASS', name, flush=True)
            report['pass'] = True
        finally:
            report['finishedAt'] = time.strftime('%Y-%m-%dT%H:%M:%S%z')
            (out / 'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print('PASS browser acceptance:', out / 'report.json', flush=True)

if __name__ == '__main__':
    main()
