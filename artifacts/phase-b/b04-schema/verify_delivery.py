"""Independent offline delivery accounting, including actual testcase elements."""
import hashlib
import json
import pathlib
import re
import shutil
import xml.etree.ElementTree as ET

root = pathlib.Path(__file__).resolve().parents[3]
folder = pathlib.Path(__file__).parent
results = {}
def count(name, path, expected):
    node = ET.parse(path).getroot()
    data = {k: int(node.get(k, '0')) for k in ('tests', 'failures', 'errors', 'skipped')}
    assert tuple(data.values()) == expected, (name, data, expected)
    cases = node.findall('testcase')
    assert len(cases) == data['tests'], (name, len(cases), data)
    assert sum(x.find('failure') is not None for x in cases) == data['failures']
    assert sum(x.find('error') is not None for x in cases) == data['errors']
    assert sum(x.find('skipped') is not None for x in cases) == data['skipped']
    data['testcase_names'] = [x.get('name') for x in cases]
    results[name] = data

# The setup assertion is a FAILURE in raw XML (blank testcase name), although
# Maven's console container summary labels the beforeAll problem an error.
count('configuration_error_not_red', folder/'pre-red-fixture-path-error.xml', (1,1,0,0))
count('real_red', folder/'red.xml', (1,1,0,0))
count('first_green_overlaps_final_cases', folder/'first-green.xml', (8,0,0,0))
count('final_file_schema', folder/'final-schema-it.xml', (8,0,0,0))
count('foundation_selected_normal', folder/'foundation-positive-it.xml', (13,0,0,0))
count('local_contract', folder/'local-storage-contract.xml', (7,0,0,0))
count('local_durability', folder/'local-durability.xml', (39,0,0,0))
unit_files = sorted((folder/'java-unit-xml').glob('*.xml'))
assert len(unit_files) == 5
unit_count = 0
for path in unit_files:
    node = ET.parse(path).getroot()
    n = int(node.get('tests'))
    count('unit:' + node.get('name'), path, (n,0,0,0))
    unit_count += n
assert unit_count == 28
wanted_file = root/'backend/integration-tests/src/test/resources/ama/storage/file-schema-b00-1.2-metadata.json'
wanted = json.loads(wanted_file.read_text())
assert hashlib.sha256((root/wanted['source']).read_bytes()).hexdigest() == wanted['source_sha256']
tables = {x['table'] for x in wanted['metadata']['tables']}
first = json.loads((folder/'first-success-snapshot.json').read_text())
final = json.loads((folder/'final-positive-snapshot.json').read_text())
foundation = json.loads((folder/'foundation-final-success-snapshot.json').read_text())
for capture in (first, final, foundation):
    assert capture['mysql_version'] == '8.4.6'
    history = capture['rows']['flyway_schema_history']
    assert [x['version'] for x in history] == ['001','002','005','006','007','008','009']
    assert all(x['success'] == 1 for x in history)
    assert history[-1]['checksum'] == -287948759
    subset = {key: [x for x in rows if x['table'] in tables] for key,rows in capture['metadata'].items()}
    assert subset == wanted['metadata'], capture['capture_label']
assert first['rows']['flyway_schema_history'] == final['rows']['flyway_schema_history']
sqlpath = root/'backend/adapters/src/main/resources/db/migration/V009__file_schema.sql'
assert sqlpath.stat().st_size == 7239
assert hashlib.sha256(sqlpath.read_bytes()).hexdigest() == '79f048987282c82fd7a56d697a02e88c93fa45b3349bd4d43e840dca23ba437d'
for path in (folder/'frozen-sql').glob('*.sql'):
    assert path.read_bytes() == (root/'backend/adapters/src/main/resources/db/migration'/path.name).read_bytes()
source = (root/'docs/database/ama-schema-draft.sql').read_text()
sql = sqlpath.read_text()
assert set(re.findall(r'CREATE TABLE (\w+)', sql)) == tables
for table in tables:
    pattern = r'CREATE TABLE '+table+r' \(.*?\n\) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;'
    assert re.findall(pattern, sql, re.S) == re.findall(pattern, source, re.S), table
actual_fks = [x for x in sql.splitlines() if x.startswith('ALTER TABLE ')]
wanted_fks = [x for x in source.splitlines() if any(x.startswith('ALTER TABLE '+t+' ADD CONSTRAINT ') for t in tables)]
assert len(actual_fks) == 9 and actual_fks == wanted_fks
for filename in ['first-green.log','final-schema-it.log','java-units.log','foundation-positive-it.log','local-storage-regression.log']:
    lines = (folder/filename).read_text().splitlines()
    assert not [x for x in lines if '[ERROR]' in x or '[WARNING]' in x or re.search(r'\s(?:WARN|ERROR)\s',x)], filename
    assert any('BUILD SUCCESS' in x for x in lines), filename
out = {
    'scope': 'Selected schema/backend/Foundation regressions only; not all integration tests or full B04 acceptance',
    'unique_selected_passing_cases': 8+13+7+39+28,
    'java_units_total': unit_count,
    'first_green_overlaps_final': True,
    'results': results,
    'final_metadata_matches_independent_b00': True,
    'first_and_final_positive_history_identical': True,
    'both_owned_fixture_histories_successful': True,
    'frozen_v009_sha256': hashlib.sha256(sqlpath.read_bytes()).hexdigest()
}
(folder/'verified-counts.json').write_text(json.dumps(out, ensure_ascii=False, indent=2)+'\n')
old = root.parent/'phase-a/.superpowers/sdd/2026-10-21-ama-phase-b/task-B-04-schema-report.md'
copy = folder/'preparation-coordinator-report.md'
if not copy.exists():
    shutil.copy2(old, copy)
print(json.dumps({k:v for k,v in out.items() if k!='results'},indent=2))
