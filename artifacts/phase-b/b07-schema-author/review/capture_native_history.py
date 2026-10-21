"""Read-only exact owned-fixture native UTC/epoch proof; never prints credentials."""
import argparse
import datetime
import json
import pathlib
import subprocess

root = pathlib.Path(__file__).resolve().parents[4]
parser = argparse.ArgumentParser()
parser.add_argument('stage', choices=['before-first012', 'after-first012', 'final'])
args = parser.parse_args()
review = root / '.tools/generation-schema-review'
initial = json.loads((review / 'fixture-identity.json').read_text())
ack = json.loads((review / 'root-ack.json').read_text())
name = initial['name']
assert name == ack['authorized_fixture_name'] == 'ama-test-b01-feb4b5365dae'
assert initial['inspection']['id'] == ack['authorized_fixture_id'] == 'b62b9b2b8f2b7bb47db21fa4561c90e3ba325138250975531f5585e16ede460e'
fmt = '{"id":{{json .Id}},"name":{{json .Name}},"image":{{json .Config.Image}},"image_id":{{json .Image}},"label":{{json (index .Config.Labels "ama.test-fixture")}},"args":{{json .Args}},"ports":{{json .NetworkSettings.Ports}},"mounts":{{json .Mounts}},"auto_remove":{{json .HostConfig.AutoRemove}}}'
current = json.loads(subprocess.check_output(['docker', 'inspect', '--format', fmt, name], text=True))
assert current == initial['inspection']
sql = "SELECT JSON_OBJECT('server_version',VERSION(),'session_time_zone',@@session.time_zone,'system_time_zone',@@system_time_zone,'native_aio',@@global.innodb_use_native_aio,'server_utc',DATE_FORMAT(UTC_TIMESTAMP(6),'%Y-%m-%d %H:%i:%s.%f'),'server_epoch',UNIX_TIMESTAMP()); SELECT JSON_OBJECT('rank',installed_rank,'version',version,'description',description,'type',type,'script',script,'checksum',checksum,'installed_by',installed_by,'installed_on_utc',DATE_FORMAT(installed_on,'%Y-%m-%d %H:%i:%s.%f'),'installed_epoch',UNIX_TIMESTAMP(installed_on),'execution_time',execution_time,'success',success) FROM flyway_schema_history ORDER BY installed_rank"
command = 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h127.0.0.1 --user="$MYSQL_USER" "$MYSQL_DATABASE" --batch --raw --skip-column-names -e "' + sql + '"'
rows = subprocess.check_output(['docker', 'exec', name, 'sh', '-c', command], text=True).splitlines()
clock = json.loads(rows[0])
history = [json.loads(row) for row in rows[1:]]
expected = ack['authorized_lower_versions'] + ([] if args.stage == 'before-first012' else ['012'])
assert [row['version'] for row in history] == expected
assert all(row['success'] == 1 for row in history)
assert clock['session_time_zone'] == '+00:00' and clock['native_aio'] == 0
record = dict(captured_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(), stage=args.stage,
              inspection=current, clock=clock, history=history, jdbc_timestamp_note='Original snapshots stringify JDBC timestamps in JVM Asia/Shanghai; only these native DATE_FORMAT/epoch fields are server UTC.')
output = review / ('native-history-' + args.stage + '.json')
assert not output.exists(), 'preserve every existing evidence file'
output.write_text(json.dumps(record, indent=2) + '\n')
print(args.stage, 'exact owned fixture history verified; versions=' + ','.join(expected), 'native server UTC=' + clock['server_utc'])
