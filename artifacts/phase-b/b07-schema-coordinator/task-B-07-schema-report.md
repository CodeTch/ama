# B07/V012 final author delivery — independent review pending

**Author functional GREEN: 11 integration tests and 62 unit tests, zero failures,
errors or skips.** V012 was first applied only after the explicit central ACK,
on the same authorized owned fixture. Fresh independent SPEC and quality review
is still required before root adoption. Full B07 runtime, company integration,
production qualification and both UX decisions remain OPEN.

## Exact source range

Worktree `/home/tanren/ama/.worktrees/phase-b-generation-schema`, branch
`codex/phase-b-generation-schema`.

- BASE: `260b6f84a17c06605ff9f3f4eb811069f9ab099b`.
- Final HEAD: `234e6e2be6cfa721d52c804e87e4b71a3b92e8b6`.
- Complete owned range: BASE..HEAD, including preparation
  `e5680284276cab5841424895103c89454ab15265`, checkpoint allowlist
  `b750f3fb1f44d9fe2aa8b421056469da42efbf16`, and final delivery
  `234e6e2be6cfa721d52c804e87e4b71a3b92e8b6`. Review all three, not HEAD~1.
- Exactly 67 changed paths: six source/docs/SQL paths and 61 evidence, manifest
  and attribute paths. The complete path map appears below. Lower ten SQL,
  production migrator, shared helper/runner/POM and approved designs are unchanged.
  No application/core/API/worker/contracts/parser code or peer Java was imported.
- Central freeze commit `7b80ae282ac898af5cf0685267eb408ffde11ae5` is external
  register authority and was intentionally not merged into this owned branch.
  Source BASE remains unchanged.

The complete final migration is 21,260 bytes, SHA-256
**`10742ca2075fa3430dedf692853e1c106bd4464eaed46d8c8c1aa07859878fb7`**,
actual Flyway checksum **2101765957**. Final manifest SHA-256 is
**`d4a430376c0563a72b1da7c367b211a43b6b978d713c40748ac05867273912fb`**. Its 60 payload and six source entries match actual file
bytes and the exact evidence directory. Five checkpoint source/docs bytes are
separately retained with original SHA and checkpoint commit; the original
checkpoint manifest remains unchanged.

The prior root checkpoint report is byte-exact preserved at
`artifacts/phase-b/b07-schema-author/review/checkpoint-coordinator-report.md`,
SHA-256 `61c9c950bd937e500a030ef60e27e5e20b3bd0ad2b98ff7cc9e55e616cc3c198`. The initial shell redirection error file is a
transcription of the tool result, not original raw database/test stderr. The
complete database/test logs and XML are retained as raw bytes. No credential env
file was exported; both actual private password byte values were checked with
zero payload matches. The fixture and ignored originals remain for root archival.

## Exact owned fixture proof

The following initial selected-field record was rechecked before migration and
again natively after the suite. It captures the exact image, label, mount,
loopback binding, arguments, UTC and native AIO setting without credentials.

```json
{
  "name": "ama-test-b01-feb4b5365dae",
  "mysql_version": "8.4.6",
  "initial_table_count": 0,
  "innodb_use_native_aio": 0,
  "created_utc": "2026-10-21T20:45:39.206588+00:00",
  "server_utc": "2026-10-21 20:45:55.024593",
  "server_epoch": 1792615555,
  "session_time_zone": "+00:00",
  "inspection": {
    "id": "b62b9b2b8f2b7bb47db21fa4561c90e3ba325138250975531f5585e16ede460e",
    "name": "/ama-test-b01-feb4b5365dae",
    "image": "mysql@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9",
    "image_id": "sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9",
    "label": "b01",
    "args": [
      "--character-set-server=utf8mb4",
      "--collation-server=utf8mb4_0900_ai_ci",
      "--default-time-zone=+00:00",
      "--innodb-use-native-aio=OFF"
    ],
    "ports": {
      "3306/tcp": [
        {
          "HostIp": "127.0.0.1",
          "HostPort": "44915"
        }
      ]
    },
    "mounts": [
      {
        "Destination": "/var/lib/mysql",
        "Driver": "local",
        "Mode": "",
        "Name": "483a44f8cb8e92977279e7280ca61ccdfab37221326d79e1d44f87ec276069a1",
        "Propagation": "",
        "RW": true,
        "Source": "/var/lib/docker/volumes/483a44f8cb8e92977279e7280ca61ccdfab37221326d79e1d44f87ec276069a1/_data",
        "Type": "volume"
      }
    ],
    "auto_remove": false
  }
}
```

The final native read at 2026-10-21 20:59:24.223082 UTC confirms the same fixture
and exact ten-row lower history prefix plus only successful rank11 V012. Native
DATE_FORMAT/epoch gives first application 2026-10-21 20:55:06 UTC, epoch1792616106.
Central freeze at 20:53:07 UTC and retained explicit ACK at
20:53:38.528017 UTC both precede it. Original JDBC Asia/Shanghai timestamp strings
are retained without relabelling them as server UTC.

## Full implementation record

This is the complete final implementation record from the owned source tree.
It details actual commands and counts, the exact deployed parent and deferred-FK
index handling, physical guarantees, accepted SQL boundary witnesses, and work
that remains unimplemented or unreviewed.

# B07 V012 schema prerequisite — author functional GREEN

Actual exact `MinutesSchemaIT` run: **11 tests, 0 failures, 0 errors, 0 skips**.
The existing runner also executed 62 reactor unit tests, all passing. This
completes author implementation of the bounded 19-table prerequisite; fresh
independent SPEC and quality review is still required before root adoption.
It does not accept full B07 or qualify production/native I/O/capacity/HA.

The exact owned review BASE remains
`260b6f84a17c06605ff9f3f4eb811069f9ab099b`. The complete range includes both
preparation commits `e5680284276cab5841424895103c89454ab15265` and
`b750f3fb1f44d9fe2aa8b421056469da42efbf16`, plus final delivery, whose HEAD is
recorded in the coordinator report. Do not use HEAD~1. Root's external central
register commit is authority, not a merged source dependency; no peer Java was
consumed. Initial preparation status and all original payload/manifest bytes
remain retained. All five checkpoint source/docs bytes are separately preserved
under `artifacts/phase-b/b07-schema-author/checkpoint-source/` with original SHA
and original checkpoint commit, so later final prose does not overwrite history.

## Frozen migration, central ACK and exact chronology

| Item | Actual value |
|---|---|
| Only new active migration | V012__minutes_evidence.sql |
| Bytes / SHA-256 | 21260 / `10742ca2075fa3430dedf692853e1c106bd4464eaed46d8c8c1aa07859878fb7` |
| Actual Flyway checksum | 2101765957 |
| Root full-read / central freeze commit | `7b80ae282ac898af5cf0685267eb408ffde11ae5` |
| Central freeze UTC | 2026-10-21 20:53:07 |
| Explicit root ACK retained UTC | 2026-10-21T20:53:38.528017+00:00 |
| Native successful rank11 application | 2026-10-21 20:55:06 UTC, epoch1792616106 |

Root fully read the complete review-only bytes, independently compared all
CREATE/FK/oracle/index subsets and committed the register before its explicit
ACK. Only afterward were these exact bytes copied into the Flyway path and
first applied to the authorized exact same fixture. Active SQL, initial draft,
shared candidate, actual Flyway classpath resource and central frozen copy are
the same SHA. No migration or oracle correction was needed. Frozen bytes remain
immutable; any future defect requires a centrally registered forward version.

The migration creates exactly 19 adopted B00-1.3 tables, each exact CREATE block
including its columns/engine/collations/index declarations/CHECKs, before every
FK addition. It explicitly preserves two adopted deferred-FK backing indexes,
then adds 44 exact currently satisfiable FK constraints / 45 FK-column rows.
No final_result_review/V013 or material/V017 table is created. No seed,
IF NOT EXISTS, new field/kind, JSON registry or design extension appears.
All ten lower migrations remain byte-exact to BASE and centrally adopted SHA.

| Deferred FK | Column | Target / reserved version | Explicit adopted backing index |
|---|---|---|---|
| fk_generation_input_5 | generation_input.material_snapshot_id | material_snapshot.id / V017 | fk_generation_input_5(material_snapshot_id) |
| fk_source_locator_3 | source_locator.material_revision_id | material_revision.id / V017 | fk_source_locator_3(material_revision_id) |

The complete 38-table deployed lower parent set is extracted from the actual
ten lower CREATE sets and recorded in the independent oracle and
review/candidate-comparison.json. Only these two unavailable material FK
constraints are excluded from metadata acceptance; no index, column or CHECK
is excluded. Application B must still reject non-null material pointers until
V017; schema deferral is not application permission to use future materials.

## Independent actual metadata and preservation

The fresh `minutes-schema-b00-1.3.json` oracle is independently filtered from
adopted actual MySQL8.4.6 metadata, source SHA
`a6c758d488c490c97bb373f404bc25c5b50db001e7c0dcb82dfcf9a6668f7aa5`.
Full adopted DDL SHA is
`ebf1eae51b303d00b2ed35c4fbd5ba4c166813564032875a6ba0fb35ba743c93`.
The unchanged oracle SHA is
`d051aaa95aeec4497f38e48190f4f396d7356ea69089196c8617119f66e9f987`.
It is never generated from candidate SQL. Its source-SHA-guarded preparation
script remains a historical ten-lower-file reproducer at its original checkpoint;
do not modify its guard just to execute it after V012 has been activated.

| Metadata group | Independent expected | Actual | Equality |
|---|---:|---:|---|
| Tables | 19 | 19 | exact |
| Columns | 123 | 123 | exact |
| Index-column rows | 92 | 92 | exact, both deferred indexes present |
| ENFORCED CHECKs | 21 | 21 | exact |
| FK-column rows | 45 | 45 | exact, 44 constraints |

The fixture has 58 final tables including Flyway history, versus 39 lower tables
including history. Every lower DDL byte, row value, metadata group, source SQL
SHA and ten-row history prefix is unchanged. Real synthetic lower rows cover
identity/session, meeting/info, backend/file, audit/task and transcript batch/
input/revision/segment/speaker/confirmation. No old row was removed or repaired.
All 19 new tables contain zero rows after transactional physical probes roll
back. The only lasting data delta is the successful V012 history row.

Private historical target012 rerun executes zero migrations; unchanged
production `SchemaMigrator` latest rerun also executes zero. After-upgrade,
after-tests, private-rerun and production-rerun complete snapshots are byte-equal,
SHA `97e08a0988ab9f058f5bb0e1704ae3b2db1e80425efb18e0500ed8d011f5cde6`.

## Fixture and actually executed checks

Only the new, independently owned `ama-test-b01-feb4b5365dae` was used, full ID
`b62b9b2b8f2b7bb47db21fa4561c90e3ba325138250975531f5585e16ede460e`,
`127.0.0.1:44915`. Initial actual count0, MySQL8.4.6, exact pinned image ID,
label, mount, arguments, loopback binding and native AIO zero were captured and
rechecked before connection. Process-local wrapper appends only
`--innodb-use-native-aio=OFF`; shared helper/default/sysctl are unchanged, and
all fixture removal is refused until root archives it. No other fixture was
inspected or operated during fixture work. No reset/repair/downgrade/restart,
failed-history cleanup or removal occurred.

Known-success history and all ten lower SQL SHA/checksums are guarded before
seed writes; unknown/failed/incomplete/newer histories stop. Root ACK release
was scoped to this exact fixture and lower011.1, verified natively before first
V012 at 20:54:09 UTC. Native `DATE_FORMAT`/epoch captures prove UTC application
time and complete history prefix. Original JDBC snapshot timestamps are kept
unchanged; their `Timestamp.toString()` reflects JVM Asia/Shanghai and is not
mislabelled as native server UTC.

| Run | Actual result |
|---|---|
| Source-guarded independent oracle extraction | Success, no SQL execution |
| One initial shell redirection error | Before fixture command; not feature RED; tool-result transcription retained separately |
| Lower001→002→005→006→007→008→009→010→011→011.1 | All ten succeeded, initial0 fixture |
| Actual missing19 RED through existing runner | 1 test, 1 assertion failure, 0 errors/skips; expected19 vs empty list; 62 unit tests passed |
| Final preparation compile | Success; no integration tests executed by compile |
| First frozen V012 full exact runner | 11 integration tests and 62 reactor unit tests; 0 failures/errors/skips; Maven/runner exit0 |
| Read-only final evidence/classpath/native-time checks | Success; no migration or row write |

The full command used pinned `phase-a/.tools/env.sh`, own fixture env privately,
explicit WSL workdir from host `C:\Users\LING`, and process-local property:

```sh
JAVA_TOOL_OPTIONS=-Dama.minutes.schema.frozen-sha=10742ca2075fa3430dedf692853e1c106bd4464eaed46d8c8c1aa07859878fb7 \
python3 tools/checks/run.py it MinutesSchemaIT
```

No unselected test is counted passing. No reset-based Foundation/Meeting
wildcard, historical B06 test on the newer fixture or unrelated S3/web cohort
was run. No SQL/setup/test failure happened after the central V012 ACK, and no
unchanged suite was repeated after the successful full run.

## Physical guarantees and explicit application boundaries

The actual rollback probes exercise all seven subject categories and support
states, title/candidate vocabularies with case rejection, template1..8 limits,
FULL/ELEMENT target shape, unsigned numeric bounds, and all seven locator kinds
with required fields and nonnegative/positive/time-order constraints. They prove
two sensitive labels coexist at one locator, duplicate tuples/source memberships
and per-category/per-note ordinals reject, and clearing one exclusion leaves the
other label and original detections/source joins intact. Background text and raw
owner suggestion retain long Unicode; raw suggestion owner/date stay separate
from accepted action_item NULL fields.

Review probes enforce target XOR, SOURCE_SUPPORT/ACTION_FIELDS distinctions,
per-kind outcome/anchor shapes, positive review sequence, required IDs/reviewer/
hash columns and lowercase64hex hash format. All 44 deployed FK paths physically
reject missing parents, and referenced review/evidence/locator/input/template/
set/transcript/identity parents reject deletion with RESTRICT. Metadata acceptance
also checks every actual FK rule and every index column in order.

Four intentional **accepted raw SQL boundary witnesses** show that individual
FKs and well-formed hashes permit unknown reviewer identity, cross-meeting target,
different evidence target and an unverified content hash. These are not rejection
tests. Schema cannot prove active identity/owner/source authority, same-meeting
membership, exact source/target completeness, content hash correctness, semantic
support, immutable history or source bounds existing in real content. Likewise
nonnull anchor CHECK does not validate anchor encoding or nonempty text. These
remain owning application transaction/contract obligations.

One inherited wording discrepancy is explicitly retained: general design prose
claims non-applicable locator fields are schema-rejected, but the exact adopted
CHECK permits extras; the dictionary correctly assigns their rejection to the
service. The implementation preserves the reviewed DDL instead of silently
strengthening it. No evidence is claimed for such an application rejection.

All raw evidence, original failed RED, initial draft, checkpoint manifests/source
and native-time proofs are retained with SHA allowlists and `-text` attributes;
log/XML whitespace is exempted from normalization checks. The final manifest is
`artifacts/phase-b/b07-schema-author/final-manifest.json`. Credential scans compare
both actual private password byte values without printing them or exporting env
files. The local fixture and ignored raw originals remain for root archival.

Full B07 business/API/worker/ACL/adoption/CAS/fencing, mixed-origin provenance,
profile/anchor/hash semantics, same-meeting/complete evidence, semantic support,
history immutability, concurrency and both pending UX questions remain OPEN.
No application runtime or provider/company/SSO/model/KB/mail behavior was enabled,
and no push/main merge occurred. Final fresh full-range independent SPEC and
quality review is required; these author checks do not self-sign that gate.


## Complete delivery path map

All 67 paths below are relative to the owned worktree. The final manifest supplies
payload sizes, SHA-256 values and original source locations; the path map covers
the complete BASE..HEAD range, including both preparation commits.

```text
artifacts/phase-b/b07-schema-author/.gitattributes
artifacts/phase-b/b07-schema-author/checkpoint-manifest.json
artifacts/phase-b/b07-schema-author/checkpoint-source/backend/integration-tests/src/test/java/ama/db/MinutesSchemaIT.java
artifacts/phase-b/b07-schema-author/checkpoint-source/backend/integration-tests/src/test/resources/ama/db/minutes-schema-b00-1.3.json
artifacts/phase-b/b07-schema-author/checkpoint-source/docs/acceptance/b/generation-schema-implementation-report.md
artifacts/phase-b/b07-schema-author/checkpoint-source/docs/acceptance/b/generation-schema-preparation.md
artifacts/phase-b/b07-schema-author/checkpoint-source/docs/acceptance/b/generation-schema-preparation.py
artifacts/phase-b/b07-schema-author/checkpoint-source/manifest.json
artifacts/phase-b/b07-schema-author/compiled/MinutesSchemaIT.class
artifacts/phase-b/b07-schema-author/compiled/V012__minutes_evidence.sql
artifacts/phase-b/b07-schema-author/compiled/minutes-schema-b00-1.3.json
artifacts/phase-b/b07-schema-author/final-manifest.json
artifacts/phase-b/b07-schema-author/formal/after-red.json
artifacts/phase-b/b07-schema-author/formal/after-tests.json
artifacts/phase-b/b07-schema-author/formal/after-upgrade.json
artifacts/phase-b/b07-schema-author/formal/application-boundaries.json
artifacts/phase-b/b07-schema-author/formal/initial-empty.json
artifacts/phase-b/b07-schema-author/formal/lower-before.json
artifacts/phase-b/b07-schema-author/formal/owned-fixture.json
artifacts/phase-b/b07-schema-author/formal/private-target-rerun-zero.json
artifacts/phase-b/b07-schema-author/formal/production-latest-rerun-zero.json
artifacts/phase-b/b07-schema-author/formal/root-ack.json
artifacts/phase-b/b07-schema-author/review/V012__minutes_evidence.sql.review-only
artifacts/phase-b/b07-schema-author/review/__pycache__/start_fixture.cpython-312.pyc
artifacts/phase-b/b07-schema-author/review/candidate-comparison.json
artifacts/phase-b/b07-schema-author/review/capture_native_history.py
artifacts/phase-b/b07-schema-author/review/checkpoint-coordinator-report.md
artifacts/phase-b/b07-schema-author/review/final-checks.json
artifacts/phase-b/b07-schema-author/review/final-checks.log
artifacts/phase-b/b07-schema-author/review/final_checks.py
artifacts/phase-b/b07-schema-author/review/first-full.log
artifacts/phase-b/b07-schema-author/review/first-full.xml
artifacts/phase-b/b07-schema-author/review/fixture-identity.json
artifacts/phase-b/b07-schema-author/review/fixture-start-attempt.json
artifacts/phase-b/b07-schema-author/review/fixture-start.log
artifacts/phase-b/b07-schema-author/review/initial-V012__minutes_evidence.sql.review-only
artifacts/phase-b/b07-schema-author/review/initial-shell-redirection-error.log
artifacts/phase-b/b07-schema-author/review/native-after.log
artifacts/phase-b/b07-schema-author/review/native-before.log
artifacts/phase-b/b07-schema-author/review/native-final.log
artifacts/phase-b/b07-schema-author/review/native-history-after-first012.json
artifacts/phase-b/b07-schema-author/review/native-history-before-first012.json
artifacts/phase-b/b07-schema-author/review/native-history-final.json
artifacts/phase-b/b07-schema-author/review/preparation.json
artifacts/phase-b/b07-schema-author/review/red.log
artifacts/phase-b/b07-schema-author/review/red.xml
artifacts/phase-b/b07-schema-author/review/root-ack.json
artifacts/phase-b/b07-schema-author/review/start_fixture.py
artifacts/phase-b/b07-schema-author/review/test-compile-final-checkpoint.log
artifacts/phase-b/b07-schema-author/review/test-compile.log
artifacts/phase-b/b07-schema-author/unit-xml/adapters/TEST-ama.storage.StorageRouterTest.xml
artifacts/phase-b/b07-schema-author/unit-xml/adapters/TEST-ama.storage.s3.S3LayoutV3Test.xml
artifacts/phase-b/b07-schema-author/unit-xml/adapters/TEST-ama.storage.s3.S3ProtocolTest.xml
artifacts/phase-b/b07-schema-author/unit-xml/adapters/TEST-ama.task.TaskMetadataTest.xml
artifacts/phase-b/b07-schema-author/unit-xml/adapters/TEST-ama.transcript.TextTranscriptParserTest.xml
artifacts/phase-b/b07-schema-author/unit-xml/api/TEST-ama.api.DeploymentConfigTest.xml
artifacts/phase-b/b07-schema-author/unit-xml/api/TEST-ama.api.IdentityContractsTest.xml
artifacts/phase-b/b07-schema-author/unit-xml/api/TEST-ama.api.IdentitySecurityTest.xml
artifacts/phase-b/b07-schema-author/unit-xml/integration-tests/TEST-ama.storage.ContractFixtureTest.xml
artifacts/phase-b/b07-schema-author/unit-xml/integration-tests/TEST-ama.support.ContractClientTest.xml
artifacts/phase-b/b07-schema-author/unit-xml/worker/TEST-ama.worker.WorkerLoopTest.xml
backend/adapters/src/main/resources/db/migration/V012__minutes_evidence.sql
backend/integration-tests/src/test/java/ama/db/MinutesSchemaIT.java
backend/integration-tests/src/test/resources/ama/db/minutes-schema-b00-1.3.json
docs/acceptance/b/generation-schema-implementation-report.md
docs/acceptance/b/generation-schema-preparation.md
docs/acceptance/b/generation-schema-preparation.py
```
