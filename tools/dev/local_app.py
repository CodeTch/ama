#!/usr/bin/env python3
"""Start an isolated, persistent local AMA development application in Linux/WSL."""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import signal
import socket
import struct
import subprocess
import time
from urllib.error import HTTPError, URLError
from urllib.request import ProxyHandler, build_opener
import zipfile

ROOT = Path(__file__).resolve().parents[2]
RUNTIME = ROOT / '.tools/local-app'
OWNER = hashlib.sha256(str(ROOT).encode()).hexdigest()[:12]
NAME = 'ama-local-dev-' + OWNER
IMAGE = 'mysql@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9'
ORIGIN = 'http://127.0.0.1:5173'
CAS_ORIGIN = os.getenv('AMA_CAS_ORIGIN', 'http://127.0.0.1:8787').rstrip('/')
HTTP = build_opener(ProxyHandler({}))
CHILDREN = {}


def save(path, value):
    temporary = path.with_suffix('.writing')
    with open(temporary, 'w', encoding='utf-8') as stream:
        os.chmod(temporary, 0o600)
        json.dump(value, stream, ensure_ascii=False, indent=2)
    temporary.replace(path)


def docker(*args, check=True):
    result = subprocess.run(['docker', *args], capture_output=True, text=True, timeout=180)
    if check and result.returncode:
        raise RuntimeError('Docker operation failed: ' + args[0] + '; inspect the owned local container')
    return result


def owned_container():
    result = docker('inspect', NAME, check=False)
    if result.returncode:
        return None
    container = json.loads(result.stdout)[0]
    if container['Config']['Labels'].get('ama.local-dev') != OWNER:
        raise RuntimeError('Refusing a container without this local application ownership label')
    return container


def database(state):
    volume = NAME + '-mysql'
    existing = docker('volume', 'inspect', volume, check=False)
    if existing.returncode:
        docker('volume', 'create', '--label', 'ama.local-dev=' + OWNER, volume)
    elif json.loads(existing.stdout)[0]['Labels'].get('ama.local-dev') != OWNER:
        raise RuntimeError('Refusing a volume without this local application ownership label')
    container = owned_container()
    if container is None:
        envfile = RUNTIME / 'mysql.env'
        envfile.write_text('MYSQL_DATABASE=ama_local_dev\nMYSQL_USER=ama_local\nMYSQL_PASSWORD=' + state['databasePassword']
                           + '\nMYSQL_ROOT_PASSWORD=' + state['rootPassword'] + '\n')
        envfile.chmod(0o600)
        print('Creating dedicated local MySQL container and persistent volume...', flush=True)
        docker('run', '-d', '--name', NAME, '--label', 'ama.local-dev=' + OWNER,
               '--memory', '768m', '--cpus', '2', '--pids-limit', '256',
               '--publish', '127.0.0.1::3306', '--mount', 'type=volume,src=' + volume + ',dst=/var/lib/mysql',
               '--env-file', str(envfile), IMAGE)
    elif not container['State']['Running']:
        docker('start', NAME)
    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        result = docker('exec', NAME, 'sh', '-c',
                        'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h127.0.0.1 --user="$MYSQL_USER" "$MYSQL_DATABASE" --batch --skip-column-names -e "SELECT 1"', check=False)
        if result.returncode == 0:
            container = owned_container()
            return container['NetworkSettings']['Ports']['3306/tcp'][0]['HostPort']
        time.sleep(1)
    raise RuntimeError('Dedicated local MySQL did not become ready')


def toolchain():
    bases = [ROOT / '.tools', ROOT.parent / 'phase-a/.tools', ROOT.parents[1] / '.tools']
    java = next((p for base in bases for p in base.glob('*/bin/java') if (p.parent / 'javac').is_file()), None)
    node = next((p for base in bases for p in base.glob('node-*/bin/node')), None)
    if java is None:
        location = shutil.which('javac')
        if location:
            java = Path(location).resolve().with_name('java')
    if java is None or node is None:
        raise RuntimeError('Java 21 / pinned Node toolchain unavailable; run tools/bootstrap.py first')
    # A local launch must not inherit another deployment's Spring/adapter
    # configuration or JVM system properties. Only our generated values are
    # reintroduced below, including the dedicated database and exact Origin.
    prefixes = ('SPRING_', 'DATABASE_', 'IDENTITY_', 'SERVER_', 'MANAGEMENT_', 'STORAGE_', 'S3_',
                'FILES_', 'PROCESSING_', 'TRANSCRIPT_', 'MINUTES_', 'PUBLICATION_', 'INTERNAL_', 'AMA_')
    excluded = {'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'CLASSPATH', 'MAVEN_OPTS'}
    env = {key: value for key, value in os.environ.items()
           if not key.upper().startswith(prefixes) and key.upper() not in excluded}
    env.update(JAVA_HOME=str(java.parent.parent), PATH=str(java.parent) + ':' + str(node.parent) + ':' + env['PATH'])
    return java, node.parent / 'npm', env


def run_logged(command, env, log):
    with open(RUNTIME / log, 'a') as stream:
        result = subprocess.run([str(x) for x in command], cwd=ROOT, env=env, stdout=stream, stderr=subprocess.STDOUT)
    if result.returncode:
        raise RuntimeError('Command failed; inspect ' + str(RUNTIME / log))


def application(java, env):
    sources = sorted(p for p in (ROOT / 'backend').rglob('*')
                     if p.is_file() and 'target' not in p.parts and p.suffix in {'.java', '.sql', '.yml', '.xml'})
    digest = hashlib.sha256()
    for path in [ROOT / 'pom.xml', *sources]:
        digest.update(str(path.relative_to(ROOT)).encode())
        digest.update(path.read_bytes())
    fingerprint = digest.hexdigest()
    stamp = RUNTIME / 'backend-source.sha256'
    jar = ROOT / 'backend/api/target/ama-api-0.1.0-SNAPSHOT-exec.jar'
    if not jar.is_file() or not stamp.is_file() or stamp.read_text() != fingerprint:
        print('Building backend with the repository toolchain...', flush=True)
        run_logged([ROOT / 'mvnw', '-o', '-B', '-ntp', '-pl', 'backend/api', '-am', '-DskipTests', 'package'], env, 'build.log')
        stamp.write_text(fingerprint)
    artifact = hashlib.sha256(jar.read_bytes()).hexdigest()
    exploded = RUNTIME / 'application' / artifact
    if not (exploded / '.complete').exists():
        exploded.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(jar) as archive:
            archive.extractall(exploded)
        (exploded / '.complete').touch()
    classes = RUNTIME / 'dev-classes'
    classes.mkdir(exist_ok=True)
    classpath = str(exploded / 'BOOT-INF/classes') + ':' + str(exploded / 'BOOT-INF/lib/*')
    development = sorted((ROOT / 'tools/dev/java').rglob('*.java'))
    run_logged([java.with_name('javac'), '--release', '21', '-parameters', '-encoding', 'UTF-8', '-cp', classpath,
                '-d', classes, *development], env, 'compile.log')
    return str(classes) + ':' + classpath


def process_record(process):
    fields = Path(f'/proc/{process.pid}/stat').read_text().rsplit(')', 1)[1].split()
    return {'pid': process.pid, 'started': fields[19]}


def alive(record):
    if not record:
        return False
    try:
        proc = Path('/proc') / str(record['pid'])
        fields = proc.joinpath('stat').read_text().rsplit(')', 1)[1].split()
        if fields[0] == 'Z':
            return False
        return fields[19] == record['started'] and (
            'AMA_LOCAL_DEV_INSTANCE=' + OWNER).encode() in proc.joinpath('environ').read_bytes().split(b'\0')
    except (FileNotFoundError, ProcessLookupError, PermissionError):
        return False


def stop_process(record):
    if not alive(record):
        return
    os.killpg(record['pid'], signal.SIGTERM)
    deadline = time.monotonic() + 15
    while alive(record) and time.monotonic() < deadline:
        time.sleep(.1)
    if alive(record):
        raise RuntimeError('Owned development process did not stop; inspect it before restarting')


def launch(command, env, log):
    with open(RUNTIME / log, 'a') as stream:
        process = subprocess.Popen([str(x) for x in command], cwd=ROOT, env=env, stdin=subprocess.DEVNULL,
                                   stdout=stream, stderr=subprocess.STDOUT, start_new_session=True)
    CHILDREN[process.pid] = process
    return process_record(process)


def status(state):
    print(json.dumps({'frontend': alive(state.get('frontend')), 'backend': alive(state.get('backend')),
                      'url': ORIGIN + '/meetings', 'login': ORIGIN + '/auth/cas/login',
                      'cas': CAS_ORIGIN + '/cas/login',
                      'databaseContainer': NAME, 'passwordRequired': True}, ensure_ascii=False))


def start(state):
    if alive(state.get('backend')) and alive(state.get('frontend')):
        current_port = database(state)
        if state.get('databasePort') == current_port:
            status(state)
            return
    for role in ('frontend', 'backend'):
        stop_process(state.get(role))
    for port in (5173, 8082, 8083):
        with socket.socket() as probe:
            probe.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            try:
                probe.bind(('127.0.0.1', port))
            except OSError as error:
                raise RuntimeError(f'Port {port} is occupied; no unrelated service was stopped') from error
    java, npm, env = toolchain()
    port = database(state)
    state['databasePort'] = port
    env.update(DATABASE_URL=f'jdbc:mysql://127.0.0.1:{port}/ama_local_dev?connectTimeout=1000&socketTimeout=10000',
               DATABASE_USERNAME='ama_local', DATABASE_PASSWORD=state['databasePassword'],
               AMA_LOCAL_DEV='1', AMA_LOCAL_DEV_INSTANCE=OWNER,
               PORT='8082', INTERNAL_PORT='8083', SERVER_ADDRESS='127.0.0.1',
               IDENTITY_ALLOWED_ORIGIN=ORIGIN, CAS_ORIGIN=CAS_ORIGIN,
               CAS_SERVICE_URL=ORIGIN + '/auth/cas/callback', CAS_LOGOUT_SERVICE_URL=ORIGIN + '/meetings',
               AMA_API_TARGET='http://127.0.0.1:8082', VITE_LOCAL_DEV_LOGIN='1')
    classpath = application(java, env)
    print('Applying schema migrations only to the dedicated local database...', flush=True)
    run_logged([java, '-cp', classpath, 'ama.db.MigrationMain'], env, 'migration.log')
    snapshot = b'AMAC' + b'\x01' + struct.pack('>I', 3)
    for key, label in [('project', '项目会议'), ('review', '需求评审'), ('workshop', '专题讨论')]:
        for field in (key, label):
            value = field.encode('utf-8')
            snapshot += struct.pack('>I', len(value)) + value
    category_file = RUNTIME / 'categories.bin'
    category_file.write_bytes(snapshot)
    options = [f'--local-dev.categories={category_file}',
               '--local-dev.categories-sha=' + hashlib.sha256(snapshot).hexdigest(),
               '--local-dev.seed-marker=' + str(RUNTIME / 'seeded'), '--local-dev.seed-date=' + state['seedDate']]
    # Meeting information writes require the real transcript/minutes transition
    # authority even when no model or Worker is installed. Install its bounded
    # DB machinery; provider-dependent operations remain unavailable.
    names = ['artifact-bytes', 'archive-total-bytes', 'artifacts', 'dependencies', 'graph-depth',
             'json-tokens', 'json-depth', 'name-utf16', 'string-utf16', 'string-utf8', 'aggregate-scalars',
             'manifest-bytes', 'batch-files', 'fact-rows', 'fact-edges', 'canonical-bytes', 'canonical-depth',
             'canonical-nodes', 'canonical-string-utf8', 'canonical-scalars', 'index-bytes', 'index-entries',
             'release-bytes', 'index-generations', 'total-source-bytes']
    values = [65536, 1048576, 16, 32, 8, 200000, 32, 255, 65536, 262144, 1048576, 65536, 32,
              250000, 500000, 16777216, 32, 1000000, 2097152, 250000, 4194304, 4096, 67108864, 128, 1048576]
    options.append('--processing.enabled=true')
    options.extend(f'--processing.bounds.{key}={value}' for key, value in zip(names, values, strict=True))
    state['backend'] = launch([java, '-Xmx512m', '-cp', classpath, 'ama.localdev.LocalApplication', *options], env, 'backend.log')
    save(RUNTIME / 'state.json', state)
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        if CHILDREN[state['backend']['pid']].poll() is not None:
            raise RuntimeError('Local API exited; inspect ' + str(RUNTIME / 'backend.log'))
        if (RUNTIME / 'seeded').exists():
            try:
                HTTP.open('http://127.0.0.1:8082/api/v1/session', timeout=2)
            except HTTPError as response:
                if response.code == 401:
                    break
            except (URLError, TimeoutError):
                pass
        time.sleep(.25)
    else:
        raise RuntimeError('Local API did not become ready; inspect backend.log')
    state['frontend'] = launch([npm, '--prefix', 'web', 'run', 'dev', '--', '--port', '5173', '--strictPort'], env, 'frontend.log')
    save(RUNTIME / 'state.json', state)
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        if CHILDREN[state['frontend']['pid']].poll() is not None:
            raise RuntimeError('Local frontend exited; inspect frontend.log')
        try:
            HTTP.open(ORIGIN + '/api/v1/session', timeout=2)
        except HTTPError as response:
            if response.code == 401:
                status(state)
                return
        except (URLError, TimeoutError):
            pass
        time.sleep(.25)
    raise RuntimeError('Frontend proxy did not become ready')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['start', 'stop', 'status'])
    args = parser.parse_args()
    RUNTIME.mkdir(parents=True, exist_ok=True, mode=0o700)
    RUNTIME.chmod(0o700)
    with open(RUNTIME / 'lifecycle.lock', 'a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        statefile = RUNTIME / 'state.json'
        state = json.loads(statefile.read_text()) if statefile.exists() else None
        if state is None:
            if args.action != 'start':
                print('Local development environment has not been created')
                return
            from datetime import datetime
            from zoneinfo import ZoneInfo
            state = {'owner': OWNER, 'databasePassword': secrets.token_urlsafe(32),
                     'rootPassword': secrets.token_urlsafe(32), 'seedDate': datetime.now(ZoneInfo('Asia/Shanghai')).date().isoformat()}
            save(statefile, state)
        if state.get('owner') != OWNER:
            raise RuntimeError('Local state belongs to another checkout')
        if args.action == 'start':
            start(state)
        elif args.action == 'stop':
            for role in ('frontend', 'backend'):
                stop_process(state.get(role))
            container = owned_container()
            if container and container['State']['Running']:
                docker('stop', NAME)
            print('Stopped owned local processes and database. All data retained.')
        else:
            status(state)


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, subprocess.TimeoutExpired) as error:
        raise SystemExit(str(error))
