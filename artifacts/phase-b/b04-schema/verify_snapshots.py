"""Offline checks of preserved B04 captures; never connects to a database."""
import hashlib
import json
import pathlib
import re
import shutil

root = pathlib.Path(__file__).resolve().parents[3]
folder = pathlib.Path(__file__).parent
wanted = json.loads((root / 'backend/integration-tests/src/test/resources/ama/storage/file-schema-b00-1.2-metadata.json').read_text())
snapshot = json.loads((folder / 'first-success-snapshot.json').read_text())
tables = {x['table'] for x in wanted['metadata']['tables']}
actual = {k: [x for x in rows if x['table'] in tables] for k, rows in snapshot['metadata'].items()}
assert actual == wanted['metadata']
before = json.loads((folder / 'pre-v009-frozen-snapshot.json').read_text())
assert snapshot['rows']['flyway_schema_history'][:-1] == before['rows']['flyway_schema_history']
for table, rows in before['rows'].items():
    if table != 'flyway_schema_history':
        assert all(row in snapshot['rows'][table] for row in rows), table
differences = {}
for table, ddl in before['show_create'].items():
    if table == 'flyway_schema_history':
        continue
    after = snapshot['show_create'][table]
    if after != ddl:
        differences[table] = {'before_auto_increment': re.findall(r'AUTO_INCREMENT=\d+', ddl),
                              'after_auto_increment': re.findall(r'AUTO_INCREMENT=\d+', after)}
        # The GREEN setup adds one test audit row before its exact migration
        # before/after comparison. The outer timestamped capture predates that
        # setup, so audit's allocation counter must differ by exactly this seed.
        assert table == 'audit_event'
        assert differences[table] == {'before_auto_increment': ['AUTO_INCREMENT=2'],
                                      'after_auto_increment': ['AUTO_INCREMENT=3']}
        assert re.sub(r'AUTO_INCREMENT=\d+', '', ddl) == re.sub(r'AUTO_INCREMENT=\d+', '', after)
assert all(snapshot['migration_sha256'][key] == value for key, value in before['migration_sha256'].items())
counts = dict(tables=len(actual['tables']), columns=len(actual['columns']),
              index_definitions=len({(x['table'],x['name']) for x in actual['indexes']}),
              index_columns=len(actual['indexes']), checks=len(actual['checks']),
              foreign_key_constraints=len({(x['table'],x['name']) for x in actual['foreign_keys']}),
              foreign_key_columns=len(actual['foreign_keys']))
out = {
    'scope': 'B04 six-table structure, not registry/API/ACL/transaction protocol/backend qualification',
    'captured_at_utc': snapshot['captured_at_utc'], 'fixture': snapshot['container'],
    'mysql_version': snapshot['mysql_version'],
    'independent_expectation_source': wanted['source'],
    'independent_expectation_source_sha256': wanted['source_sha256'],
    'counts': counts, 'metadata': actual,
    'v009_sha256': snapshot['migration_sha256']['V009__file_schema.sql'],
    'v009_flyway_checksum': snapshot['rows']['flyway_schema_history'][-1]['checksum'],
    'outer_capture_seed_counter_difference': differences,
    'exact_upgrade_ddl_comparison': 'The passing orderedUpgrade test captures DDL after its test seed and before V009; exact after-upgrade DDL equality includes AUTO_INCREMENT.'
}
(folder / 'first-success-file-metadata.json').write_text(json.dumps(out, ensure_ascii=False, indent=2) + '\n')
copydir = folder / 'frozen-sql'
copydir.mkdir(exist_ok=True)
for path in (root / 'backend/adapters/src/main/resources/db/migration').glob('*.sql'):
    dest = copydir / path.name
    if dest.exists():
        assert dest.read_bytes() == path.read_bytes(), path.name
    else:
        shutil.copy2(path, dest)
ack = {
    'coordinator_ack_commit': 'b3c00ceebffdd7a15fd0ba3249a091c29f445c2b',
    'sha256': '79f048987282c82fd7a56d697a02e88c93fa45b3349bd4d43e840dca23ba437d',
    'bytes': 7239, 'first_formal_fixture': 'ama-test-b01-204cc11baabc', 'loopback_port': 45041,
    'statement': 'Root independently read exact file, compared six CREATE + nine FK with adopted B00-1.2, registered and explicitly ACKed first formal execution. Shared/applied bytes permanently frozen; ACK is not implementation acceptance. Original coordinator message retained in conversation.'
}
(folder / 'coordinator-freeze-ack.json').write_text(json.dumps(ack, ensure_ascii=False, indent=2) + '\n')
print(json.dumps({'metadata_counts': counts, 'preserved_all_lower_history_and_rows': True,
                  'outer_capture_seed_counter_difference': differences}, indent=2))
