# B07 V012 pre-application checkpoint

Exact owned base is `260b6f84a17c06605ff9f3f4eb811069f9ab099b`. This checkpoint
contains no active V012 migration and claims no GREEN or schema acceptance.
The central first-application hold is mandatory even on this private fixture.

The fresh oracle `minutes-schema-b00-1.3.json` is independently filtered from
adopted actual MySQL metadata SHA
`a6c758d488c490c97bb373f404bc25c5b50db001e7c0dcb82dfcf9a6668f7aa5`, full DDL SHA
`ebf1eae51b303d00b2ed35c4fbd5ba4c166813564032875a6ba0fb35ba743c93`.
Its SHA is `d051aaa95aeec4497f38e48190f4f396d7356ea69089196c8617119f66e9f987`.
Exactly 19 tables, 123 columns, 92 index-column rows, 21 CHECKs and 45 FK-column
rows (44 constraints) are expected. Only two material FK constraints are
excluded; every adopted backing index remains in the oracle.

| Deferred constraint | Column | Reserved target | Explicit retained index |
|---|---|---|---|
| fk_generation_input_5 | generation_input.material_snapshot_id | V017 material_snapshot.id | fk_generation_input_5(material_snapshot_id) |
| fk_source_locator_3 | source_locator.material_revision_id | V017 material_revision.id | fk_source_locator_3(material_revision_id) |

The deployed-parent set is extracted from all ten reviewed lower SQL files and
is fully recorded in the oracle and candidate-comparison.json. The candidate
contains all 19 exact adopted CREATE blocks before the two explicit deferred
indexes and all 44 exact deployable ALTER/FK statements. It contains no
material/final table, seed, IF NOT EXISTS or design extension. Full candidate
and initial bytes are retained outside Flyway in
`artifacts/phase-b/b07-schema-author/review/` and ignored `.tools/` originals.
Both are 21,260 bytes, SHA
`10742ca2075fa3430dedf692853e1c106bd4464eaed46d8c8c1aa07859878fb7`.
From this complete-byte checkpoint sharing these candidate bytes are frozen;
no later review or test failure authorizes changing them.

One new fixture only: `ama-test-b01-feb4b5365dae`, complete ID
`b62b9b2b8f2b7bb47db21fa4561c90e3ba325138250975531f5585e16ede460e`.
Its initial capture proves zero tables, pinned MySQL 8.4.6 image, exact mount,
loopback binding, UTC and actual native AIO zero. The local wrapper appends only
`--innodb-use-native-aio=OFF` and refuses all removal even during startup failure;
the shared helper, default and sysctl are unchanged. This is functional evidence,
with no native I/O, capacity, HA or production qualification.

The exact lower sequence 001,002,005,006,007,008,009,010,011,011.1 succeeded.
All ten SQL SHA/checksums and known-success history are guarded before seed
writes. Real synthetic lower identity/session, meeting/info, file/backend,
audit/task and transcript/batch/confirmation rows, full DDL and five metadata
groups are preserved in lower-before.json. No reset, repair, downgrade, fixture
removal or other fixture operation occurred.

Actual TDD RED was run through the existing runner with
`python3 tools/checks/run.py it MinutesSchemaIT#allNineteenAssignedTablesExist`
and process-local `-Dama.minutes.schema.red=true`. The retained fresh XML is
**1 test, 1 assertion failure, 0 errors, 0 skips**, expected nineteen tables vs
actual empty list. This preceded drafting SQL. The runner also executed the
normal selected reactor unit tests; their actual counts are retained in logs,
not counted as integration acceptance. One earlier shell redirection failed
before the fixture command could execute; it is retained separately and is
not RED. No setup error is claimed as feature RED.

The full physical harness is prepared and compiled, but all post-V012 cases are
NOT RUN pending root full read, central register COMMIT and explicit SHA ACK.
It checks all five metadata groups, complete lower preservation, private target
012 and unchanged production latest zero-rerun, seven subject and locator kinds,
scope/enum/hash/numeric/required-field checks, dual sensitivity labels and
membership uniqueness, raw action suggestions and Unicode, all 44 deployed FK
paths and RESTRICT. DML rolls back. Accepted SQL boundary witnesses explicitly
separate reviewer identity, same-meeting/target membership and hash truth from
physical constraints. Neither this harness nor this schema proves application
authority, semantic support, source completeness, content hash correctness or
immutability. Material non-null references remain forbidden by Application B
until V017 even though raw SQL can accept them with deferred FKs.

The older general design prose says non-applicable locator fields are rejected
by schema; the exact adopted CHECK permits such extras and the dictionary
explicitly assigns rejection to the service. The implementation preserves the
reviewed DDL and reports this as an application-boundary wording discrepancy;
it does not strengthen the adopted constraint silently.

Run the source-SHA-guarded preparation script without `--draft` to reproduce only
the oracle, or with it to compare the preserved draft bytes. It executes no SQL.
After the active V012 file exists, the historical preparation script belongs to
this original ten-lower-file checkpoint; do not weaken its lower-file guard to
make it a general latest-schema tool. Raw checkpoint payloads are allowlisted
with SHA, marked `-text`, and checked against both actual private password values
without printing or exporting credential files. Root must archive the fixture
and ignored originals before any cleanup.
