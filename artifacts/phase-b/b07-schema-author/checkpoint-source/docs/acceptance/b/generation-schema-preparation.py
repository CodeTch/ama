"""Reproduce independent B00-1.3 oracle; draft SQL only with --draft after actual RED.

Never executes SQL or activates a Flyway migration. Existing draft bytes are immutable.
"""
import hashlib
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[3]
TABLES = sorted('template_revision generation_input minutes_revision element_set minutes_element action_item exclusion_revision sensitive_exclusion generation_candidate source_locator evidence_link minutes_template sensitive_detection sensitive_detection_source background_note background_note_source action_suggestion result_review result_review_evidence'.split())
SOURCE = ROOT / 'docs/acceptance/b/database-generation-addendum-metadata-evidence.json'
DDL = ROOT / 'docs/database/ama-schema-draft.sql'
SOURCE_SHA = 'a6c758d488c490c97bb373f404bc25c5b50db001e7c0dcb82dfcf9a6668f7aa5'
DDL_SHA = 'ebf1eae51b303d00b2ed35c4fbd5ba4c166813564032875a6ba0fb35ba743c93'
assert hashlib.sha256(SOURCE.read_bytes()).hexdigest() == SOURCE_SHA
assert hashlib.sha256(DDL.read_bytes()).hexdigest() == DDL_SHA
adopted = json.loads(SOURCE.read_text())
assert adopted['counts']['tables'] == 93 and adopted['counts']['columns'] == 709
lower_files = sorted((ROOT / 'backend/adapters/src/main/resources/db/migration').glob('V*.sql'))
assert len(lower_files) == 10
parents = sorted(set(t for f in lower_files for t in re.findall(r'^CREATE TABLE (\w+) \(', f.read_text(), re.M)))
sql = DDL.read_text()
fk_pattern = r'^ALTER TABLE (\w+) ADD CONSTRAINT (\w+) FOREIGN KEY \(([^)]+)\) REFERENCES (\w+) \(([^)]+)\).*?;$'
fks = [m for m in re.finditer(fk_pattern, sql, re.M) if m[1] in TABLES]
deferred = [dict(table=m[1], name=m[2], column=m[3], target=m[4], remote=m[5], reserved_version='V017')
            for m in fks if m[4] not in parents + TABLES]
assert all(row['target'] in ('material_snapshot', 'material_revision') for row in deferred)
deferred_names = {row['name'] for row in deferred}
metadata = {group: [row for row in adopted['metadata'][group] if row['table'] in TABLES
                         and not (group == 'foreign_keys' and row['name'] in deferred_names)]
            for group in ('tables', 'columns', 'indexes', 'checks', 'foreign_keys')}
expected = dict(status='INDEPENDENT_ACTUAL_METADATA_ORACLE_NOT_SCHEMA_ACCEPTANCE',
                design_version=adopted['design_version'], source_path=str(SOURCE.relative_to(ROOT)),
                source_sha256=SOURCE_SHA, source_ddl_sha256=DDL_SHA,
                source_mysql_version=adopted['mysql_version'], tables=TABLES,
                deployed_lower_parents=parents, excluded_foreign_keys=deferred,
                counts={group: len(rows) for group, rows in metadata.items()}, metadata=metadata)
target = ROOT / 'backend/integration-tests/src/test/resources/ama/db/minutes-schema-b00-1.3.json'
target.parent.mkdir(parents=True, exist_ok=True)
oracle_bytes = (json.dumps(expected, ensure_ascii=False, indent=2) + '\n').encode()
if target.exists():
    assert target.read_bytes() == oracle_bytes, 'preserve independent oracle bytes'
else:
    target.write_bytes(oracle_bytes)
summary = dict(oracle_sha256=hashlib.sha256(oracle_bytes).hexdigest(), counts=expected['counts'],
               deployed_lower_parents=parents, excluded_foreign_keys=deferred)
if '--draft' in sys.argv:
    creates = [re.findall(r'^CREATE TABLE ' + table + r' \(.*?^\) ENGINE=.*?;', sql, re.M | re.S)[0] for table in TABLES]
    deployed = [m[0] for m in fks if m[2] not in deferred_names]
    # Deferred FK indexes exist in adopted actual metadata and must survive deferral.
    indexes = []
    for row in deferred:
        adopted_index = [i for i in metadata['indexes'] if i['table'] == row['table'] and i['name'] == row['name']]
        assert adopted_index, row
        cols = ','.join(i['column'] for i in sorted(adopted_index, key=lambda i: i['seq']))
        assert cols == row['column']
        indexes.append('CREATE INDEX ' + row['name'] + ' ON ' + row['table'] + ' (' + cols + ');')
    candidate = ('-- V012: adopted B00-1.3 minutes/generation schema prerequisite.\n'
                 '-- All 19 CREATE blocks precede FK additions. Material FKs remain reserved V017.\n'
                 '-- Explicit indexes preserve every deferred-FK backing index in the actual oracle.\n\n'
                 + '\n\n'.join(creates + indexes + deployed) + '\n').encode()
    directory = ROOT / '.tools/generation-schema-review'
    directory.mkdir(parents=True, exist_ok=True)
    for name in ('V012__minutes_evidence.sql.review-only', 'initial-V012__minutes_evidence.sql.review-only'):
        path = directory / name
        if path.exists():
            assert path.read_bytes() == candidate, 'never rewrite shared or initial bytes'
        else:
            path.write_bytes(candidate)
    comparison = dict(candidate_sha256=hashlib.sha256(candidate).hexdigest(), candidate_bytes=len(candidate),
                      create_count=len(creates), exact_create_blocks=True, deployed_fk_count=len(deployed),
                      deferred_fk_count=len(deferred), explicit_deferred_indexes=indexes,
                      all_create_before_fk=True, **summary)
    (directory / 'candidate-comparison.json').write_text(json.dumps(comparison, indent=2) + '\n')
    summary = comparison
print(json.dumps(summary, indent=2))
