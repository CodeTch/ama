"""Read-only verification of retained executed V012 evidence and actual classpath bytes."""
import datetime
import hashlib
import json
import pathlib
import re
import subprocess
import xml.etree.ElementTree as ET

root = pathlib.Path(__file__).resolve().parents[4]
review = root / '.tools/generation-schema-review'
formal = root / '.tools/generation-schema-formal'
read = lambda path: json.loads(path.read_text())
sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
candidate = '10742ca2075fa3430dedf692853e1c106bd4464eaed46d8c8c1aa07859878fb7'
oracle_sha = 'd051aaa95aeec4497f38e48190f4f396d7356ea69089196c8617119f66e9f987'
oracle_path = root / 'backend/integration-tests/src/test/resources/ama/db/minutes-schema-b00-1.3.json'
assert sha(oracle_path) == oracle_sha
expected = read(oracle_path)
assert expected['counts'] == dict(tables=19, columns=123, indexes=92, checks=21, foreign_keys=45)
before = read(formal / 'lower-before.json')
after = read(formal / 'after-upgrade.json')
for table in before['ddl']:
    assert after['ddl'][table] == before['ddl'][table], ('lower DDL', table)
    if table != 'flyway_schema_history':
        assert after['rows'][table] == before['rows'][table], ('lower rows', table)
assert after['rows']['flyway_schema_history'][:-1] == before['rows']['flyway_schema_history']
assert len(after['rows']['flyway_schema_history']) == 11
assert set(after['ddl']) == set(before['ddl']) | set(expected['tables'])
assert all(after['rows'][table] == [] for table in expected['tables'])
for group, rows in after['metadata'].items():
    assert [row for row in rows if row['table'] in expected['tables']] == expected['metadata'][group], group
    assert [row for row in rows if row['table'] not in expected['tables']] == before['metadata'][group], ('lower metadata', group)
assert after['lower_sql_sha256'] == before['lower_sql_sha256']
for filename, frozen in before['lower_sql_sha256'].items():
    path = root / 'backend/adapters/src/main/resources/db/migration' / filename
    assert sha(path) == frozen
    assert subprocess.check_output(['git', 'show', '260b6f84a17c06605ff9f3f4eb811069f9ab099b:' + str(path.relative_to(root))]) == path.read_bytes()
for filename in ['after-tests.json', 'private-target-rerun-zero.json', 'production-latest-rerun-zero.json']:
    assert (formal / filename).read_bytes() == (formal / 'after-upgrade.json').read_bytes()

ack = read(review / 'root-ack.json')
native_before = read(review / 'native-history-before-first012.json')
native_after = read(review / 'native-history-after-first012.json')
assert native_after['inspection'] == native_before['inspection']
assert native_after['history'][:-1] == native_before['history']
last = native_after['history'][-1]
assert (last['rank'], last['version'], last['checksum'], last['success']) == (11, '012', 2101765957, 1)
assert last['installed_epoch'] == 1792616106
assert last['installed_on_utc'] == '2026-10-21 20:55:06.000000'
assert datetime.datetime.fromisoformat(ack['ack_utc']).timestamp() < last['installed_epoch']
assert int(subprocess.check_output(['git', '-C', '/home/tanren/ama/.worktrees/phase-a', 'show', '-s', '--format=%ct', ack['register_commit']], text=True)) < last['installed_epoch']

active = root / 'backend/adapters/src/main/resources/db/migration/V012__minutes_evidence.sql'
classpath = root / 'backend/adapters/target/classes/db/migration/V012__minutes_evidence.sql'
assert sha(active) == sha(classpath) == sha(review / 'V012__minutes_evidence.sql.review-only') == sha(review / 'initial-V012__minutes_evidence.sql.review-only') == candidate
assert (root / 'backend/integration-tests/target/test-classes/ama/db/minutes-schema-b00-1.3.json').read_bytes() == oracle_path.read_bytes()
sql = active.read_text()
ddl = (root / 'docs/database/ama-schema-draft.sql').read_text()
creates = re.findall(r'^CREATE TABLE (\w+) \(.*?^\) ENGINE=.*?;', sql, re.M | re.S)
assert sorted(creates) == expected['tables'] and len(creates) == 19
for table in expected['tables']:
    pattern = r'^CREATE TABLE ' + table + r' \(.*?^\) ENGINE=.*?;'
    assert re.findall(pattern, sql, re.M | re.S) == re.findall(pattern, ddl, re.M | re.S)
assert sql.rfind('CREATE TABLE ') < sql.index('ALTER TABLE ')
for deferred in expected['excluded_foreign_keys']:
    assert 'ADD CONSTRAINT ' + deferred['name'] + ' ' not in sql
    assert 'CREATE INDEX ' + deferred['name'] + ' ON ' + deferred['table'] + ' (' + deferred['column'] + ');' in sql

suite = ET.parse(review / 'first-full.xml').getroot()
assert [int(suite.get(key)) for key in ('tests', 'failures', 'errors', 'skipped')] == [11, 0, 0, 0]
unit = []
for path in sorted(root.glob('backend/*/target/surefire-reports/TEST-*.xml')):
    result = ET.parse(path).getroot()
    assert all(int(result.get(key, 0)) == 0 for key in ('failures', 'errors', 'skipped'))
    unit.append(dict(path=str(path.relative_to(root)), tests=int(result.get('tests'))))
assert sum(item['tests'] for item in unit) == 62

result = dict(status='AUTHOR_EXECUTED_FUNCTIONAL_GREEN_NOT_INDEPENDENT_ACCEPTANCE',
              checked_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),
              integration=dict(tests=11, failures=0, errors=0, skipped=0), unit_tests=62,
              unit_suites=unit, metadata_counts=expected['counts'], lower_tables_including_history=len(before['ddl']),
              final_tables_including_history=len(after['ddl']), target_rows_after_rollback=0,
              lower_rows_ddl_metadata_history_and_sha_preserved=True,
              private_target_rerun_migrations=0, production_latest_rerun_migrations=0,
              exact_same_complete_snapshot_sha256=sha(formal / 'after-upgrade.json'),
              candidate_sha256=candidate, oracle_sha256=oracle_sha,
              native_first_apply=last, central_ack=ack,
              boundaries='Physical CHECK/FK/uniqueness and format only; accepted raw SQL identity/meeting/target/hash examples do not prove application authority, hash correctness, semantic truth, complete sources or immutable history.')
output = review / 'final-checks.json'
assert not output.exists(), 'preserve prior verification results'
output.write_text(json.dumps(result, indent=2) + '\n')
print('Author verification: 11 IT + 62 unit GREEN; exact 5 metadata groups; lower preservation; 2 zero-reruns; native UTC ACK-before-apply; frozen SQL/oracle/classpath bytes equal')
