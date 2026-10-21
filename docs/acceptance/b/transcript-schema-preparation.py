"""Preparation only: never activates or executes a Flyway migration."""
import hashlib
import json
import pathlib
import re

root = pathlib.Path(__file__).resolve().parents[3]
tables = sorted(("input_batch", "input_batch_file", "transcription_input", "transcript_revision",
                 "transcript_segment", "transcript_confirmation", "speaker", "transcript_segment_mark"))
source = root / "docs/acceptance/b/database-upload-addendum-metadata-evidence.json"
ddl = root / "docs/database/ama-schema-draft.sql"
source_sha = "3de8041db8860f1547ff53a94b4ddd79470c4881340111fdafbc3ce5322399ad"
ddl_sha = "402def655f133ab10caa44b94e89ed6692756623d32705e6ab63c8176d79c362"
assert hashlib.sha256(source.read_bytes()).hexdigest() == source_sha
assert hashlib.sha256(ddl.read_bytes()).hexdigest() == ddl_sha
adopted = json.loads(source.read_text())
assert adopted["counts"]["tables"] == 85 and adopted["counts"]["columns"] == 674
metadata = {}
for group in ("tables", "columns", "indexes", "checks", "foreign_keys"):
    metadata[group] = [row for row in adopted["metadata"][group] if row["table"] in tables
                       and not (group == "foreign_keys" and row["name"] == "fk_transcription_input_3")]
expected = {
    "status": "PREPARATION_ONLY_NOT_SCHEMA_ACCEPTANCE",
    "design_version": adopted["design_version"],
    "source_path": str(source.relative_to(root)),
    "source_sha256": source_sha,
    "source_ddl_sha256": ddl_sha,
    "source_mysql_version": adopted["mysql_version"],
    "tables": tables,
    "excluded_foreign_keys": [{"table": "transcription_input", "name": "fk_transcription_input_3",
                                "column": "material_snapshot_id", "target": "material_snapshot",
                                "remote": "id", "reserved_version": "V017"}],
    "counts": {group: len(rows) for group, rows in metadata.items()},
    "metadata": metadata,
}
target = root / "backend/integration-tests/src/test/resources/ama/db/transcript-schema-b00-1.2.json"
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(json.dumps(expected, ensure_ascii=False, indent=2) + "\n")

sql = ddl.read_text()
creates = []
for table in tables:
    matches = re.findall(r"^CREATE TABLE " + table + r" \(.*?^\) ENGINE=.*?;", sql, flags=re.M | re.S)
    assert len(matches) == 1, table
    creates.append(matches[0])
fks = [line for line in sql.splitlines() if any(line.startswith("ALTER TABLE " + table + " ") for table in tables)
       and "ADD CONSTRAINT fk_transcription_input_3 " not in line]
assert len(fks) == 21
candidate = root / ".tools/transcript-schema-review/V011__transcript_schema.sql.review-only"
candidate_bytes = ("-- REVIEW-ONLY PREPARATION. OUTSIDE FLYWAY. NOT FROZEN OR AUTHORIZED FOR APPLICATION.\n"
                     "-- Exact adopted eight CREATE statements, then 21 deployed FKs; material FK deferred to V017.\n"
                     "-- No database execution is permitted before root formal resumption and freeze SHA ACK.\n\n"
                     + "\n\n".join(creates + fks) + "\n").encode()
if candidate.exists():
    assert candidate.read_bytes() == candidate_bytes, "preserve changed review draft; never overwrite its provenance"
else:
    candidate.parent.mkdir(parents=True, exist_ok=True)
    candidate.write_bytes(candidate_bytes)
initial = candidate.with_name("initial-" + candidate.name)
if initial.exists():
    assert initial.read_bytes() == candidate_bytes, "preserve initial review draft bytes"
else:
    initial.write_bytes(candidate_bytes)
print(json.dumps({"expected_sha256": hashlib.sha256(target.read_bytes()).hexdigest(),
                  "candidate_sha256": hashlib.sha256(candidate.read_bytes()).hexdigest(),
                  "counts": expected["counts"], "candidate_create_count": len(creates),
                  "candidate_fk_count": len(fks)}, indent=2))
