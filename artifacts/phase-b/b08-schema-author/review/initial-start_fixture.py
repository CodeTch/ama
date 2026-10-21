"""One new owned functional fixture; process-local AIO override; refuse removal."""
import datetime
import importlib.util
import json
import pathlib
import subprocess

root = pathlib.Path(__file__).resolve().parents[1]
directory = root / '.tools/publication-schema-review'
directory.mkdir(parents=True, exist_ok=True)
assert not (directory / 'fixture-identity.json').exists(), 'never create a second fixture for this task'
spec = importlib.util.spec_from_file_location('reviewed_fixture', root / 'tools/dev/mysql_fixture.py')
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)
original_docker = fixture.docker
original_failure = fixture.failure_command
record = {'created_utc': datetime.datetime.now(datetime.timezone.utc).isoformat()}

def local_docker(*args):
    if args and args[0] == 'run':
        assert args[-1] == '--default-time-zone=+00:00'
        assert fixture.IMAGE in args
        assert args[args.index('--label') + 1] == 'ama.test-fixture=b01'
        name = args[args.index('--name') + 1]
        assert name.startswith('ama-test-b01-')
        assert pathlib.Path(args[args.index('--env-file') + 1]).parent == root / '.tools/mysql-fixture' / name
        args = (*args[:1], '--cpus=2', '--memory=1g', '--memory-swap=1g', '--pids-limit=256', *args[1:], '--innodb-use-native-aio=OFF')
        record['run_arguments'] = list(args)
        record['name'] = name
        (directory / 'fixture-start-attempt.json').write_text(json.dumps(record, indent=2) + '\n')
    assert not args or args[0] != 'rm', 'all fixture cleanup deferred to root archive'
    return original_docker(*args)

def preserve_failure(*args):
    assert not args or args[0] != 'rm', 'all fixture cleanup deferred to root archive'
    return original_failure(*args)

fixture.docker = local_docker
fixture.failure_command = preserve_failure
assert not (directory / 'fixture-start-attempt.json').exists(), 'no second attempt without central review'
fixture.start()
name = record['name']
fmt = '{"id":{{json .Id}},"name":{{json .Name}},"image":{{json .Config.Image}},"image_id":{{json .Image}},"label":{{json (index .Config.Labels "ama.test-fixture")}},"args":{{json .Args}},"ports":{{json .NetworkSettings.Ports}},"mounts":{{json .Mounts}},"auto_remove":{{json .HostConfig.AutoRemove}},"nano_cpus":{{json .HostConfig.NanoCpus}},"memory":{{json .HostConfig.Memory}},"memory_swap":{{json .HostConfig.MemorySwap}},"pids_limit":{{json .HostConfig.PidsLimit}}}'
record['inspection'] = json.loads(original_docker('inspect', '--format', fmt, name))
sql = 'SELECT VERSION(),@@GLOBAL.innodb_use_native_aio,(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()),@@session.time_zone,UTC_TIMESTAMP(6),UNIX_TIMESTAMP()'
probe = subprocess.check_output(['docker', 'exec', name, 'sh', '-c',
    'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h127.0.0.1 --user="$MYSQL_USER" "$MYSQL_DATABASE" --batch --skip-column-names -e "' + sql + '"'], text=True).strip()
version, aio, count, zone, utc, epoch = probe.split('\t')
assert version == '8.4.6' and aio == '0' and count == '0'
assert zone == '+00:00'
ports = record['inspection']['ports']['3306/tcp']
assert len(ports) == 1 and ports[0]['HostIp'] == '127.0.0.1'
record.update(mysql_version=version, initial_table_count=int(count), innodb_use_native_aio=int(aio),
              server_utc=utc, server_epoch=int(epoch), session_time_zone=zone,
              credential_file_exported=False, purpose='functional V013 schema only; no native IO/capacity/HA qualification')
(directory / 'fixture-identity.json').write_text(json.dumps(record, indent=2) + '\n')
(directory / 'fixture-name').write_text(name + '\n')
print('Captured exact new fixture identity, image, mount, loopback, native AIO OFF, UTC and initial zero tables')
