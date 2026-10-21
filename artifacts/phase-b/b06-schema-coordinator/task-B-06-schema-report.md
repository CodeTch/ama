# B06 corrected schema group — final author handoff for independent review

Status: AUTHOR FUNCTIONAL GREEN, independent specification/quality review still
mandatory. Full B06 and production qualification remain OPEN.

- Worktree: `/home/tanren/ama/.worktrees/phase-b-transcript-schema`.
- Branch: `codex/phase-b-transcript-schema`.
- Exact full review BASE: `0d2079cd64e5b61bc5787ca5adfaa782e5cd4a40`.
- Final HEAD: `aef885da7a6f5c1245289943e5a5f0b83daaa835`.
- Review range: `0d2079cd64e5b61bc5787ca5adfaa782e5cd4a40..aef885da7a6f5c1245289943e5a5f0b83daaa835`.
  Includes preparation `0ed35e1`, reviewed-root merge `3e176eb`, implementation
  `d27b81f`, and raw-log attributes `aef885d`. Do not use HEAD~1 or merge HEAD as BASE.
- Whole range: 51 paths = seven source/record paths plus 44 evidence/manifest/attribute
  paths. The seven are the four original preparation paths, both SQL migrations,
  and `docs/acceptance/b/transcript-schema-implementation-report.md`.
- Source/record implementation report SHA-256:
  `1bd1348a9d9b3c1d5e4f814fce6f881b9820c21f2da7f5c1da63be00938d875d`.
- Evidence allowlist: `artifacts/phase-b/b06-schema-author/manifest.json`, SHA-256
  `4b37f1e9c5d9692b42472640f07b9c1438d0b4d037c4298921ef84a22b5ef0af`.
- Existing preparation report byte-exact archive:
  `task-B-06-schema-preparation-0ed35e1-report.md`, SHA-256
  `d862e0993f46a7348e9f26f58672192b7e763d059034f372e227e417ae045992`,
  with adjacent `task-B-06-schema-preparation-0ed35e1-manifest.json`. This final
  handoff supersedes the live report only; preparation facts and prior root
  preservation files were not rewritten.

Post-commit verification: tracked tree clean; complete-range `git diff --check`
succeeded; all eight lower SQL files, both POMs, migration register and shared
fixture helper unchanged versus BASE; 42 evidence payload and six source hashes
match the committed manifest. Initial staged diffcheck noticed normal Maven raw
log trailing spaces; those bytes were retained, and the final artifact-only
`.gitattributes` disables whitespace lint for `.log` while preserving raw bytes.
No source, SQL, test, or raw log bytes were changed for that formatting check.
No tests were rerun after the successful corrected suite because later changes
were documentation/evidence attributes only.

The only live fixture remains `ama-test-b01-59f108f79cad`, container ID beginning
`487a1cd59`, loopback45591, initial0/AIO0. It and all raw evidence remain present;
root must archive before any cleanup. No reset, repair, outOfOrder, downgrade,
other fixture mutation, push or main merge occurred.

The full implementation record follows. Remaining application guards and full
B06 gates are explicit at the end; author functional results are not independent
acceptance.

---

# B06 corrected schema group: V011 and registered V011.1

The author functional run is GREEN at historical target **011.1**: 11 selected
integration tests, zero failures, errors or skips. Independent specification and
quality review are still required. This is the schema prerequisite, not full B06
acceptance, parser activation, or production qualification.

## Source range and immutable migrations

Review the complete range from exact reviewed root
`0d2079cd64e5b61bc5787ca5adfaa782e5cd4a40` to the final delivery HEAD recorded in
the coordinator report. This includes the original four preparation paths from
`0ed35e1e5d543ee371c5e7488fc51a6d59e85b14`; do not use `HEAD~1` or the merge HEAD
as review BASE. The reviewed root was merged as
`3e176eb4e391858c2aa6ff0549e6cb3313012434` before formal work.

| Version/file | Bytes | SHA-256 | Actual Flyway checksum |
|---|---:|---|---:|
| V011 / `V011__transcript_schema.sql` | 10941 | `a926c8b5d9806082d248f38bc7c7dd2bdbc46dd319d34b787e02f86132096ccb` | -286330771 |
| V011.1 / `V011_1__transcript_material_index.sql` | 397 | `9efc688e49ce4afc52f383929b91d70970905f04b4e7836bdb0533eac50a9713` | -570494170 |

Both files are under `backend/adapters/src/main/resources/db/migration/`. V011
contains the eight exact adopted CREATE statements followed by 21 deployed FKs.
The sole deferred FK is `fk_transcription_input_3`, material_snapshot_id to
material_snapshot.id, reserved for V017. No future material table is created.
All columns, CHECKs, collations and nullable version semantics remain adopted.

V011 omitted the automatic backing index for its deferred material FK. Its first
full run therefore failed metadata acceptance: expected 39 index-column rows,
actual 38. The original frozen SQL and independent oracle were preserved. Root
registered the narrow forward correction as V011.1 in central commit
`d7ab59db3dddeed066bb6c995ff7396276198a3b`; it contains exactly one CREATE INDEX
for the already-adopted index, with no IF NOT EXISTS, column or row changes.
V012–V027 reservations remain unchanged and the next integer remains V028. This
does not authorize guessing other fractional versions.

## Central ACK and actual application chronology

| Event | Central commit / actual UTC |
|---|---|
| V011 freeze ACK | `0ca7768988145cca7fe5826ac25d783fdc5ccdad`, 2026-10-21 20:08:21 UTC |
| V011 successful rank 9 | 2026-10-21 20:10:05 UTC, epoch 1792613405 |
| V011.1 freeze ACK | `8fff159b2fe85b9b91b7ccf82fe1b6618aad7629`, 2026-10-21 20:17:14 UTC |
| V011.1 successful rank 10 | 2026-10-21 20:18:11 UTC, epoch 1792613891 |

Both ACK commits preceded the respective first application. The original JDBC
snapshot displayed V011 installed_on as `2026-10-22 04:10:05.0` because the
capture used `java.sql.Timestamp.toString()` under JVM Asia/Shanghai. The initial
spoken report incorrectly called that server UTC. Original snapshots are
retained. Separate read-only DATE_FORMAT/epoch evidence establishes server
session `+00:00`, system `UTC`, and the UTC values above; no database value or
frozen SQL was changed to correct this label.

The actual pinned Flyway 12.4.0 `MigrationVersion` and `ResourceNameParser` proved
offline that `011 < 011.1 < 012`, and that the registered filename parses as
valid version `011.1`. No database connection was opened by that probe. The JAR
source and SHA, probe source and SHA, parser output and pinned JDK identity are
retained under the evidence `review/` directory.

## Fixture and verification

The only fixture created/used is `ama-test-b01-59f108f79cad`, exact ID and command
arguments in `review/fixture-identity.json`, bound to `127.0.0.1:45591`. Initial
table count was zero, independently captured by the helper wrapper and JDBC
setup. MySQL is 8.4.6. A process-local guarded wrapper appended only
`--innodb-use-native-aio=OFF`; actual args and server value zero were captured.
The shared helper, sysctl and defaults were unchanged. No fixture was reset,
repaired, downgraded, restarted or removed, and no other owner's fixture was
touched. This is a functional fixture, not storage performance/capacity/HA proof.

The test harness verifies the exact original fixture identity/args/loopback,
known successful history, all eight reviewed lower SQL SHAs/checksums and
Flyway validation before seed writes. It rejects unknown, failed or newer
histories before DML. The canonical test-only historical target is now 011.1;
production `SchemaMigrator` still migrates latest without a target switch.

| Run | Actual result |
|---|---|
| Preparation compile | Success; no tests executed |
| Initial formal setup attempt | Eight lower migrations succeeded; then absent target011 validation caused a setup error, not feature RED; retained separately |
| Corrected lower010 missing-table RED | 1 test, 1 assertion failure, 0 errors/skips: expected eight tables, actual empty list |
| First frozen V011 full suite | 11 tests, 1 metadata assertion failure, 0 errors/skips; unique missing material backing index; other 10 tests passed |
| V011.1 API/parser probe | Offline success, zero database connections |
| Corrected group compile | Success; no tests executed |
| Corrected group full suite | **11 tests, 0 failures/errors/skips**, Maven exit 0 |

Selected unit tests: zero. No unselected tests are counted as passing. The
normal production migration rerun was checked inside the owned schema suite
without invoking reset-based Foundation cases. No historical B05 target010 test
was run on the newer fixture. No unrelated web/S3 cohorts were run.

The final command used pinned `phase-a/.tools/env.sh`, explicit WSL `--cd` and
`--exec` from host `C:\Users\LING`:

```sh
./mvnw -B -ntp -pl backend/integration-tests -am \
  -Dtest=NoUnitSelected -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test=TranscriptSchemaIT -Dfailsafe.failIfNoSpecifiedTests=false \
  -Dama.transcript.schema.frozen-sha=a926c8b5d9806082d248f38bc7c7dd2bdbc46dd319d34b787e02f86132096ccb \
  -Dama.transcript.schema.index-frozen-sha=9efc688e49ce4afc52f383929b91d70970905f04b4e7836bdb0533eac50a9713 verify
```

All five actual target metadata groups equal the independent B00-1.2 subset:
8 tables, 63 columns, 39 index-column rows, 13 CHECKs and 22 FK-column rows
(21 FK constraints). Only the reserved material FK is excluded, including no
index exclusions. The independent resource SHA remains
`94e7b136824d1845462df644799db0fe4fbf8dce2ba28e961dd3df29bfe7b182`;
the adopted full actual metadata source SHA remains
`3de8041db8860f1547ff53a94b4ddd79470c4881340111fdafbc3ce5322399ad`.

The actual suite physically exercises all vocabularies, material consistency,
required parser/model/source conditions and allowed nulls, positive manifest
version, nullable plaintext time/paragraph/speaker, invalid/partial time ranges,
all deployed FK paths and circular provenance, ordered/history/confirmation and
batch-file uniqueness, case-exact source IDs, NO PAD human IDs, long Unicode,
all four coexisting marks and composite duplicate rejection. Test rows roll
back. Real lower sentinels cover identity/session, meeting/info, source file and
storage backend, audit and task provenance. All lower rows/DDL/five metadata
groups/history/SQL SHAs survived V011 and V011.1. The correction adds exactly one
adopted index, preserves every existing row and history-prefix field, and leaves
every other table DDL unchanged. Both private-target and production-latest
reruns executed zero migrations with complete before/after snapshot equality.

## Evidence and remaining gates

The committed allowlist is `artifacts/phase-b/b06-schema-author/manifest.json`,
SHA-256 `4b37f1e9c5d9692b42472640f07b9c1438d0b4d037c4298921ef84a22b5ef0af`.
It identifies 42 payload files and source hashes: initial ownership/empty/AIO,
original review draft, correct RED plus distinct setup error, first failed full
suite, both frozen ACK records, original/corrected metadata and snapshots,
clock/epoch correction, version parser proof, corrected suite XML/log/class,
index delta, and historical/production rerun snapshots. Actual fixture root/user
password bytes were excluded from every payload; no mysql.env or fixture env.sh
was copied. Raw local source evidence and fixture remain intact for root to
archive before cleanup. Original preparation source/commit and root's six prior
preserved files were not rewritten; the coordinator preparation report was
separately byte-exact archived with its own manifest before final replacement.

Independent full-range specification/quality review is mandatory before root
adoption. Full B06 domain/API/worker/ACL/parser activation remains open. In
particular, the schema permits a consistent PINNED row while the material FK is
deferred: Application B must enforce EMPTY/NULL until V017. Same-meeting and
batch/source/snapshot membership, immutable history, saved confirmation
eligibility and content-hash rules also remain business obligations where these
individual FKs cannot prove them. No production configuration or business API
was enabled, no company call was made, and no push or main merge was performed.
