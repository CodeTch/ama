#!/usr/bin/env python3
"""Start an isolated, persistent local AMA development application in Linux/WSL."""
import argparse
import fcntl
import hashlib
import json
import os
import re
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
import uuid
import sys

ROOT = Path(__file__).resolve().parents[2]
RUNTIME = ROOT / '.tools/local-app'
OWNER = hashlib.sha256(str(ROOT).encode()).hexdigest()[:12]
NAME = 'ama-local-dev-' + OWNER
IMAGE = 'mysql@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9'
ORIGIN = 'http://127.0.0.1:5173'
HTTP = build_opener(ProxyHandler({}))
CHILDREN = {}
SERVICE_ROLES = ('frontend', 'backend', 'worker', 'files', 'model', 'knowledge')


def configure_ports(state, web, api, management):
    """Retain this checkout's explicit listeners without taking another checkout's ports."""
    retained = state.get('ports', {})
    proposed = tuple(value if value is not None else retained.get(key, default)
                     for key, default, value in [('web', 5173, web), ('api', 8082, api), ('management', 8083, management)])
    if any(type(value) is not int or not 1024 <= value <= 65535 for value in proposed) or len(set(proposed)) != 3:
        raise ValueError('Three distinct local ports in 1024..65535 required')
    state['ports'] = dict(zip(('web', 'api', 'management'), proposed, strict=True))
    global ORIGIN
    ORIGIN = f'http://127.0.0.1:{proposed[0]}'
    return proposed


def source_signature():
    digest = hashlib.sha256()
    for directory in (ROOT / 'backend', ROOT / 'tools/dev', ROOT / 'tools/providers', ROOT / 'web'):
        for path in sorted(directory.rglob('*')):
            if path.is_file() and 'target' not in path.parts and 'node_modules' not in path.parts and path.suffix in {
                '.py', '.java', '.sql', '.yml', '.xml', '.json', '.ts', '.vue', '.css', '.html', '.mjs'
            }:
                digest.update(str(path.relative_to(ROOT)).encode())
                digest.update(path.read_bytes())
    digest.update((ROOT / 'pom.xml').read_bytes())
    return digest.hexdigest()


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
                     if p.is_file() and 'target' not in p.parts and (p.suffix in {'.java', '.sql', '.yml', '.xml'} or 'resources' in p.parts))
    digest = hashlib.sha256()
    for path in [ROOT / 'pom.xml', *sources]:
        digest.update(str(path.relative_to(ROOT)).encode())
        digest.update(path.read_bytes())
    fingerprint = digest.hexdigest()
    stamp = RUNTIME / 'backend-source.sha256'
    jar = ROOT / 'backend/api/target/ama-api-0.1.0-SNAPSHOT-exec.jar'
    if not (ROOT / 'backend/file-service/target/ama-file-service-0.1.0-SNAPSHOT-exec.jar').is_file() or not (ROOT / 'backend/worker/target/classes/ama/worker/TranscriptWorkerMain.class').is_file() or not jar.is_file() or not stamp.is_file() or stamp.read_text() != fingerprint:
        print('Building backend with the repository toolchain...', flush=True)
        run_logged([ROOT / 'mvnw', '-o', '-B', '-ntp', '-pl', 'backend/api,backend/file-service,backend/worker', '-am', '-DskipTests', 'package'], env, 'build.log')
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


def force_release_port(port):
    """Release a configured local listener so the desktop launcher is repeatable."""
    result = subprocess.run(['fuser', '-k', '-TERM', f'{port}/tcp'], capture_output=True, text=True)
    if result.returncode not in (0, 1):
        raise RuntimeError(f'Unable to release local port {port}: {result.stderr.strip()}')
    deadline = time.monotonic() + 5
    while time.monotonic() < deadline:
        with socket.socket() as probe:
            probe.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            try:
                probe.bind(('127.0.0.1', port))
                return
            except OSError:
                time.sleep(.1)
    subprocess.run(['fuser', '-k', '-KILL', f'{port}/tcp'], capture_output=True, text=True)
    time.sleep(.2)


def launch(command, env, log):
    with open(RUNTIME / log, 'a') as stream:
        process = subprocess.Popen([str(x) for x in command], cwd=ROOT, env=env, stdin=subprocess.DEVNULL,
                                   stdout=stream, stderr=subprocess.STDOUT, start_new_session=True)
    CHILDREN[process.pid] = process
    return process_record(process)


def status(state):
    print(json.dumps({**{role: alive(state.get(role)) for role in SERVICE_ROLES},
                      'url': ORIGIN + '/meetings', 'login': ORIGIN + '/auth/cas/login',
                      'cas': True, 'databaseContainer': NAME, 'passwordRequired': True}, ensure_ascii=False))


def database_pool_maximum():
    value = os.environ.get('AMA_LOCAL_DB_POOL_MAXIMUM', '20')
    if not re.fullmatch(r'[0-9]{1,2}', value) or not 1 <= int(value) <= 64:
        raise ValueError('AMA_LOCAL_DB_POOL_MAXIMUM must be an integer in 1..64')
    return int(value)


def file_worker_threads():
    value = os.environ.get('AMA_LOCAL_FILE_WORKERS', '16')
    if not re.fullmatch(r'[0-9]{1,2}', value) or not 1 <= int(value) <= 32:
        raise ValueError('AMA_LOCAL_FILE_WORKERS must be an integer in 1..32')
    return int(value)


def start(state):
    pool_maximum = database_pool_maximum()
    file_workers = file_worker_threads()
    configure_ports(state, None, None, None)
    signature = source_signature()
    if all(alive(state.get(role)) for role in SERVICE_ROLES) and state.get('sourceSignature') == signature and state.get('activePorts') == state.get('ports') and state.get('databasePoolMaximum', 20) == pool_maximum and state.get('fileWorkerThreads', 16) == file_workers:
        current_port = database(state)
        if state.get('databasePort') == current_port:
            status(state)
            return
    for role in SERVICE_ROLES:
        stop_process(state.get(role))
    for port in state['ports'].values():
        force_release_port(port)
    java, npm, env = toolchain()
    port = database(state)
    state['databasePort'] = port
    env.update(DATABASE_URL=f'jdbc:mysql://127.0.0.1:{port}/ama_local_dev?connectTimeout=1000&socketTimeout=10000',
               DATABASE_USERNAME='ama_local', DATABASE_PASSWORD=state['databasePassword'],
               AMA_LOCAL_DEV='1', AMA_LOCAL_DEV_INSTANCE=OWNER,
               PORT=str(state['ports']['api']), INTERNAL_PORT=str(state['ports']['management']), SERVER_ADDRESS='127.0.0.1',
               IDENTITY_ALLOWED_ORIGIN=ORIGIN, AMA_API_TARGET=f"http://127.0.0.1:{state['ports']['api']}", VITE_LOCAL_DEV_LOGIN='1')
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
    # Reuse the production processing budgets for local TXT intake and authority.
    names = ['artifact-bytes', 'archive-total-bytes', 'artifacts', 'dependencies', 'graph-depth',
             'json-tokens', 'json-depth', 'name-utf16', 'string-utf16', 'string-utf8', 'aggregate-scalars',
             'manifest-bytes', 'batch-files', 'fact-rows', 'fact-edges', 'canonical-bytes', 'canonical-depth',
             'canonical-nodes', 'canonical-string-utf8', 'canonical-scalars', 'index-bytes', 'index-entries',
             'release-bytes', 'index-generations', 'total-source-bytes']
    values = [65536, 1048576, 16, 32, 8, 200000, 32, 255, 65536, 262144, 1048576, 65536, 32,
              250000, 500000, 16777216, 32, 1000000, 2097152, 250000, 4194304, 4096, 67108864, 128, 1048576]
    options.append('--processing.enabled=true')
    options.extend(f'--processing.bounds.{key}={value}' for key, value in zip(names, values, strict=True))
    from local_services import prepare, properties, port as service_port
    if 'pkiPassword' not in state:
        state['pkiPassword'] = secrets.token_urlsafe(32)
    for key in ('filePort', 'workerHealthPort', 'modelPort', 'knowledgePort'):
        if key not in state:
            state[key] = service_port()
    for key in ('minutesTemplateId', 'minutesTemplateRevisionId'):
        if key not in state:
            state[key] = str(uuid.uuid4())
    save(RUNTIME / 'state.json', state)
    state['fileWorkerThreads'] = file_workers
    settings = prepare(RUNTIME, state, java, env, classpath)
    integration_origin = os.environ.get('AMA_INTEGRATION_KNOWLEDGE_ORIGIN')
    if integration_origin:
        if not re.fullmatch(r'http://127\.0\.0\.1:[1-9][0-9]{2,4}', integration_origin):
            raise RuntimeError('AMA_INTEGRATION_KNOWLEDGE_ORIGIN must be a pinned loopback HTTP origin')
        # Keep the local provider process for the normal service inventory,
        # while routing the application's controlled knowledge reads/writes
        # to the explicitly selected external integration harness.
        settings['publication.origin'] = integration_origin
        settings['publication.client-identities'] = RUNTIME / 'knowledge/application.json'
    from local_minutes import prepare as prepare_minutes
    minutes, model_ready = prepare_minutes(RUNTIME, state, java, env, classpath)
    settings.update({key.removeprefix('--').split('=', 1)[0]: key.split('=', 1)[1] for key in options})
    settings.update(minutes)
    settings['spring.datasource.hikari.maximum-pool-size'] = pool_maximum
    if integration_origin:
        settings['publication.origin'] = integration_origin
        settings['publication.client-identities'] = RUNTIME / 'knowledge/application.json'
    cas_origin = os.environ.get('AMA_CAS_ORIGIN', integration_origin or 'http://127.0.0.1:8792')
    if not re.fullmatch(r'http://127\.0\.0\.1:[1-9][0-9]{2,4}', cas_origin):
        raise RuntimeError('AMA_CAS_ORIGIN must be a pinned loopback HTTP origin')
    settings.update({
        'cas.enabled': 'true',
        'cas.origin': cas_origin,
        'cas.service-url': ORIGIN + '/auth/cas/callback',
        'cas.logout-service-url': ORIGIN + '/auth/cas/logout/callback',
        'cas.identity-attribute': os.environ.get('AMA_CAS_IDENTITY_ATTRIBUTE', 'user'),
        'cas.session-lifetime-seconds': os.environ.get('AMA_CAS_SESSION_LIFETIME_SECONDS', '28800'),
    })
    properties(RUNTIME / 'api.properties', settings)
    worker_settings = {**settings, 'database.url': env['DATABASE_URL'], 'database.user': env['DATABASE_USERNAME'],
        'database.password': env['DATABASE_PASSWORD'], 'worker.id': 'LOCAL_' + OWNER, 'worker.capacity': 2,
        'worker.lease-seconds': 20, 'worker.heartbeat-seconds': 5, 'worker.poll-seconds': 5,
        'worker.health.key-store': RUNTIME / 'pki/server.p12', 'worker.health.trust-store': RUNTIME / 'pki/trust.p12',
        'worker.health.key-store-password': state['pkiPassword'], 'worker.health.trust-store-password': state['pkiPassword'],
        'worker.health.port': state['workerHealthPort'], 'worker.health.peer': 'spiffe://ama/local-writer'}
    properties(RUNTIME / 'worker.properties', worker_settings)
    file_env = {**env, 'AMA_FILE_KEYSTORE_PASSWORD': state['pkiPassword'], 'AMA_FILE_TRUSTSTORE_PASSWORD': state['pkiPassword']}
    state['files'] = launch([java, '-Xmx192m' if file_workers > 16 else '-Xmx96m', '-XX:ActiveProcessorCount=2', '-jar', ROOT / 'backend/file-service/target/ama-file-service-0.1.0-SNAPSHOT-exec.jar', RUNTIME / 'file-service.properties'], file_env, 'files.log')
    save(RUNTIME / 'state.json', state)
    # Wait for this exact process to bind its retained local port before consumers start.
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        if not alive(state['files']):
            raise RuntimeError('Local file service exited; inspect files.log')
        try:
            with socket.create_connection(('127.0.0.1', state['filePort']), timeout=.5):
                break
        except OSError:
            time.sleep(.1)
    else:
        raise RuntimeError('Local file service did not become ready')
    state['model'] = launch([java, '-Xmx192m', '-XX:ActiveProcessorCount=2', '-cp', classpath,
        'ama.model.ControlledMinutesProvider', RUNTIME / 'minutes/runtime.properties'], env, 'model.log')
    save(RUNTIME / 'state.json', state)
    knowledge_ready = RUNTIME / 'knowledge/provider.ready'
    state['knowledge'] = launch([sys.executable, ROOT / 'tools/providers/controlled_knowledge.py',
        '--config', RUNTIME / 'knowledge/provider.json'], env, 'knowledge.log')
    save(RUNTIME / 'state.json', state)
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        if not all(alive(state[role]) for role in ('model', 'knowledge')):
            raise RuntimeError('Local controlled provider exited; inspect model.log / knowledge.log')
        if model_ready.is_file() and knowledge_ready.is_file():
            receipt = json.loads(knowledge_ready.read_text())
            if model_ready.read_text() == str(state['modelPort']) and receipt['pid'] == state['knowledge']['pid']:
                break
        time.sleep(.1)
    else:
        raise RuntimeError('Local controlled providers did not become ready')
    worker_classpath = str(ROOT / 'backend/worker/target/classes') + ':' + classpath
    state['worker'] = launch([java, '-Xmx256m', '-XX:ActiveProcessorCount=2', '-cp', worker_classpath,
        'ama.worker.TranscriptWorkerMain', RUNTIME / 'worker.properties'], env, 'worker.log')
    save(RUNTIME / 'state.json', state)
    state['backend'] = launch([java, '-Xmx512m', '-cp', classpath, 'ama.localdev.LocalApplication',
        '--spring.config.additional-location=file:' + str(RUNTIME / 'api.properties')], env, 'backend.log')
    save(RUNTIME / 'state.json', state)
    # A measured cold boot exceeded 60s after storage/DB validation; retain a finite startup bound.
    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        if CHILDREN[state['backend']['pid']].poll() is not None:
            raise RuntimeError('Local API exited; inspect ' + str(RUNTIME / 'backend.log'))
        if (RUNTIME / 'seeded').exists():
            try:
                HTTP.open(f"http://127.0.0.1:{state['ports']['api']}/api/v1/session", timeout=2)
            except HTTPError as response:
                if response.code == 401:
                    break
            except (URLError, TimeoutError):
                pass
        time.sleep(.25)
    else:
        raise RuntimeError('Local API did not become ready; inspect backend.log')
    if not alive(state['worker']):
        raise RuntimeError('Local TXT worker exited; inspect worker.log')
    state['activePorts'] = dict(state['ports'])
    state['sourceSignature'] = signature
    state['databasePoolMaximum'] = pool_maximum
    state['frontend'] = launch([npm, '--prefix', 'web', 'run', 'dev', '--', '--port', str(state['ports']['web']), '--strictPort'], env, 'frontend.log')
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


def start_worker(state):
    # A component restart must use exactly the source/configuration already installed by normal start.
    if state.get('sourceSignature') != source_signature():
        raise RuntimeError('Local source changed; run normal start before a component restart')
    if not all(alive(state.get(role)) for role in SERVICE_ROLES if role != 'worker'):
        raise RuntimeError('Start the complete local application before restarting its Worker')
    if alive(state.get('worker')):
        status(state)
        return
    if str(database(state)) != str(state.get('databasePort')):
        raise RuntimeError('Local database port changed; run normal start to refresh all consumers')
    configuration=RUNTIME/'worker.properties'
    if configuration.is_symlink() or not configuration.is_file() or configuration.stat().st_mode&0o777 != 0o600:
        raise RuntimeError('Private existing Worker configuration required')
    with socket.socket() as probe:
        # Match the real server's restart binding: a closed connection in TIME_WAIT is not a listener.
        probe.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
        try:probe.bind(('127.0.0.1',state['workerHealthPort']))
        except OSError as error:raise RuntimeError('Worker health port occupied; no unrelated process was stopped') from error
    java,_,env=toolchain()
    try:
        from local_minutes import asr_environment
    except ModuleNotFoundError:
        # The launcher executes beside this module; unit tests import it as
        # tools.dev.local_app and therefore need the package-qualified path.
        import tools.dev.local_services as packaged_local_services
        sys.modules.setdefault('local_services', packaged_local_services)
        from tools.dev.local_minutes import asr_environment
    asr_environment(env)
    env.update(AMA_LOCAL_DEV='1',AMA_LOCAL_DEV_INSTANCE=OWNER)
    classpath=str(ROOT/'backend/worker/target/classes')+':'+application(java,env)
    state['worker']=launch([java,'-Xmx256m','-XX:ActiveProcessorCount=2','-cp',classpath,
        'ama.worker.TranscriptWorkerMain',configuration],env,'worker.log')
    save(RUNTIME/'state.json',state)
    deadline=time.monotonic()+30
    while time.monotonic()<deadline:
        if not alive(state['worker']):raise RuntimeError('Local Worker exited; inspect worker.log')
        try:
            with socket.create_connection(('127.0.0.1',state['workerHealthPort']),timeout=.5):
                status(state)
                return
        except OSError:time.sleep(.1)
    raise RuntimeError('Local Worker health endpoint did not become ready')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['start', 'stop', 'status', 'start-worker', 'stop-worker'])
    parser.add_argument('--web-port', type=int)
    parser.add_argument('--api-port', type=int)
    parser.add_argument('--management-port', type=int)
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
        configure_ports(state, args.web_port, args.api_port, args.management_port)
        save(statefile, state)
        if args.action == 'start':
            start(state)
        elif args.action == 'start-worker':
            start_worker(state)
        elif args.action == 'stop-worker':
            stop_process(state.get('worker'))
            print('Stopped only the exact owned Worker. Other services and all data retained.')
        elif args.action == 'stop':
            for role in SERVICE_ROLES:
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
