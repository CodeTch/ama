# B07/V012 schema prerequisite checkpoint — WAIT ROOT ACK

Status at 2026-10-21 20:51 UTC: actual lower011.1/missing19 RED obtained;
final complete candidate bytes delivered for root full read, central migration
register COMMIT and explicit SHA ACK. **No active V012 file; no V012 apply;
post-V012 tests NOT RUN.** This is not schema acceptance or full B07 completion.

## Exact owned range and paths

Owned worktree `/home/tanren/ama/.worktrees/phase-b-generation-schema`, branch
`codex/phase-b-generation-schema`.

BASE `260b6f84a17c06605ff9f3f4eb811069f9ab099b`.
Current HEAD `b750f3fb1f44d9fe2aa8b421056469da42efbf16`.
Complete owned range is BASE..HEAD, including preparation commits
`e5680284276cab5841424895103c89454ab15265` and
`b750f3fb1f44d9fe2aa8b421056469da42efbf16`, not HEAD~1.

Source/docs paths:

- `backend/integration-tests/src/test/java/ama/db/MinutesSchemaIT.java`
- `backend/integration-tests/src/test/resources/ama/db/minutes-schema-b00-1.3.json`
- `docs/acceptance/b/generation-schema-preparation.py`
- `docs/acceptance/b/generation-schema-preparation.md`
- `docs/acceptance/b/generation-schema-implementation-report.md`
- `artifacts/phase-b/b07-schema-author/` exact 18 allowlisted payloads plus
  checkpoint-manifest.json and .gitattributes; full payload path/size/SHA/source
  map is in the manifest. One generated fixture-wrapper pyc is explicitly retained
  and allowlisted as compilation provenance; no runtime credentials in it.

All ten lower SQL, production migrator, shared helper, runner, register, POM,
core/API/worker/contracts/parser and approved design files are unchanged.

## Complete final SQL delivery and hold

The complete final SQL bytes (review-only, outside Flyway) are:

`/home/tanren/ama/.worktrees/phase-b-generation-schema/artifacts/phase-b/b07-schema-author/review/V012__minutes_evidence.sql.review-only`

Bytes **21260**, SHA-256
**`10742ca2075fa3430dedf692853e1c106bd4464eaed46d8c8c1aa07859878fb7`**.
The initial draft `initial-V012__minutes_evidence.sql.review-only` and ignored
originals have the same complete bytes/SHA; initial provenance is preserved.
These delivered bytes are frozen. Any defect after sharing/application must be
reported for a centrally registered forward correction, never rewritten.

It contains exactly the assigned nineteen B00-1.3 CREATE blocks, each byte-exact
to adopted full DDL, before all FK additions. Then exactly two explicit adopted
backing indexes and 44 deployable exact FK statements. No final_result_review,
material table, seed, IF NOT EXISTS, new field/table or guessed version.
Exact parent set, comparison and mapping:
`artifacts/phase-b/b07-schema-author/review/candidate-comparison.json`.

| Deferred FK | Local column | Target / reserved version | Retained explicit backing index |
|---|---|---|---|
| fk_generation_input_5 | generation_input.material_snapshot_id | material_snapshot.id / V017 | fk_generation_input_5(material_snapshot_id) |
| fk_source_locator_3 | source_locator.material_revision_id | material_revision.id / V017 | fk_source_locator_3(material_revision_id) |

Deployed parent set extracted from the ten lower CREATE sets is the 38 tables:
audit_event, department, durable_task, enterprise_identity, external_execution,
external_operation, file_cleanup, file_object, file_reference,
idempotency_record, identity_session, input_batch, input_batch_file, meeting,
meeting_access_grant, meeting_acl, meeting_info_revision, meeting_person_search,
meeting_search_projection, model_capacity, model_reservation, outbox_consumer,
role_assignment, roster_member, roster_revision, speaker, storage_backend,
task_attempt, task_outbox, task_outbox_sequence, task_subject,
transcript_confirmation, transcript_revision, transcript_segment,
transcript_segment_mark, transcription_input, upload_part, upload_session.

Only root full read + register COMMIT + explicit ACK of the exact above SHA may
release the first012 application hold. The harness requires that SHA property
and a retained root-ack.json with the central commit and UTC before migration.
Current root-ack.json absent and formal post-V012 mode NOT RUN.

## Independent oracle

Source actual1.3 metadata SHA
`a6c758d488c490c97bb373f404bc25c5b50db001e7c0dcb82dfcf9a6668f7aa5`,
93 tables/709 columns. Full adopted DDL SHA
`ebf1eae51b303d00b2ed35c4fbd5ba4c166813564032875a6ba0fb35ba743c93`.
Fresh dedicated oracle SHA
`d051aaa95aeec4497f38e48190f4f396d7356ea69089196c8617119f66e9f987`.

Expected groups: 19 tables, 123 columns, 92 index-column rows, 21 CHECKs,
45 FK-column rows / 44 FK constraints. It filters only the two unavailable
material FK constraints, never columns/indexes/CHECKs. It is not generated from
candidate SQL. Reproduction script checks both adopted source SHAs first.

## Only owned fixture and genuine RED

New fixture `ama-test-b01-feb4b5365dae`, complete ID
`b62b9b2b8f2b7bb47db21fa4561c90e3ba325138250975531f5585e16ede460e`,
`127.0.0.1:44915`, pinned mysql image
`mysql@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9`.
Actual MySQL8.4.6, initial0 tables, serverUTC20:45:55.024593 / session+00:00,
nativeAIO0. Exact image ID/label/mount/args/loopback/UTC and initial0 retained
in fixture-identity.json. The process-local wrapper appends only
`--innodb-use-native-aio=OFF`, refuses cleanup even during startup failures;
shared helper/default/sysctl untouched. No nativeIO/performance/capacity/HA claim.

Exact lower001→002→005→006→007→008→009→010→011→011.1 succeeded, 10 migrations.
All ten frozen SQL SHAs and actual checksums are guarded before mutation;
unknown/failed/incomplete/newer history stops. Retained lower-before.json has
all full lower DDL, five metadata groups, every row, history and SQL SHA plus
real synthetic lower identity/session/meeting/info/storage/file/audit/task/
batch/transcription/revision/segment/speaker/confirmation sentinels.

Actual command, pinned phase-a env and explicit owned cwd:

```sh
JAVA_TOOL_OPTIONS=-Dama.minutes.schema.red=true \
python3 tools/checks/run.py it MinutesSchemaIT#allNineteenAssignedTablesExist
```

Fresh actual XML: **1 test / 1 assertion failure / 0 errors / 0 skips**,
expected nineteen assigned tables vs actual empty list. This happened before
drafting V012. Normal runner also ran 62 reactor unit tests, all0failure/error/
skip (retained log); these do not count as schema acceptance. An earlier shell
redirection error happened before the fixture command ran, retained separately
as initial-shell-redirection-error.log, not called RED. No failed SQL/setup
history was discarded. No fixture reset/repair/removal or other fixture touched.

Preparation script extraction succeeded; Python sources compile. Final full
integration-test source compilation `./mvnw -B -ntp -pl backend/integration-tests
-am -DskipTests test-compile` succeeded, all eight selected reactor modules.
This is compile-only. The prepared physical cases are NOT RUN pending ACK:
five-group actual comparison, full lower preservation, private012/latest rerun0,
seven subject/locator kinds, enums/scope/hash/required/numeric shapes, dual
sensitivity labels and join uniqueness, raw suggestion separation/Unicode,
all44 deployed FK paths and RESTRICT. Transactional row probes roll back.

## Evidence, concerns and open scope

Checkpoint manifest SHA
`b568905ca79e5c403b6618e6accc2a6ff7ca4be3bb4afd8d2ecc06ce1274551a`.
All raw logs/XML/SQL/snapshots have -text attributes and raw log whitespace
attributes. Both actual private fixture password bytes were checked without
printing; match count0; mysql.env/env.sh not exported. Ignored originals and
fixture remain intact for root archive before any cleanup.

An existing prose discrepancy is reported, not changed: general design text
says non-applicable locator fields are schema-rejected; exact adopted CHECK
allows extra non-applicable fields, while dictionary explicitly assigns rejection
to the service. Exact approved DDL remains governing; application must validate.
SOURCE_SUPPORT checks require nonnull anchor but do not prove anchor content;
reviewer existence/active authority, same meeting/target, source membership,
complete evidence, hash truth, history immutability are application obligations.
Prepared raw SQL accepted-boundary witnesses label these as acceptance at the
physical layer, never rejection proof. Until V017 Application B must reject both
non-null material pointers although raw SQL can accept them while FKs defer.

Full B07 runtime/domain/API/worker/ACL/CAS/fencing/adoption/profile/anchor/hash/
semantic support/source completeness/immutability/concurrency and both UX
questions remain OPEN. No business/provider/company calls, SSO/models/KB/mail
activation, main merge or push. No children. Final full-range fresh independent
SPEC and quality review still required before coordinator adoption.
