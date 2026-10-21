# B06 corrected schema prerequisite — independent SPEC and quality review

Review date: 2026-10-22 (Asia/Shanghai). Reviewer: fresh independent reviewer.

**SPEC verdict: PASS. Quality verdict: PASS. No actionable findings (P0–P3).**
The reviewed delivery is suitable for root adoption as the corrected B06 schema
prerequisite, V011 plus centrally registered V011.1. This verdict does not accept
full B06 business behavior or production qualification.

## Exact scope and completeness

- Owner: `/home/tanren/ama/.worktrees/phase-b-transcript-schema`.
- BASE: `0d2079cd64e5b61bc5787ca5adfaa782e5cd4a40`.
- HEAD: `aef885da7a6f5c1245289943e5a5f0b83daaa835`.
- Complete range includes preparation `0ed35e1`, reviewed-root merge `3e176eb`,
  implementation `d27b81f`, and evidence attributes `aef885d`; not HEAD~1.
- Read the exact schema brief first and the complete current coordinator report.
  The report's embedded implementation record is byte-exact to the owned report.
- Fully read all seven source/docs/SQL paths: both migrations,
  `TranscriptSchemaIT.java`, the complete independent JSON oracle,
  `transcript-schema-preparation.py`, preparation Markdown, and implementation
  Markdown. All 44 evidence/manifest/attribute paths were machine-audited from
  the actual worktree against both the complete diff and the explicit allowlist.
- Complete package: 2,413,702 bytes, SHA-256
  `027a1a8410e8c7f2d888bd065214547f449236ac77417193473ae599ef3337ac`.
  Its 3,832-byte range/commit/stat header precedes an exact current full-range
  `git diff` body. Three binary class files are independently audited as actual
  files, since the textual diff contains binary-file notices.
- Seven-path source supplement: 109,026 bytes, SHA-256
  `b0e52b90814dc0740931aa77fb0ad7baec250d7a9562acde57087dfa3a2e04f5`;
  exact to a freshly generated seven-path full-range diff.
- All 51 actual changed paths equal the delivery-path map, with every size and
  SHA matching. Evidence directory exactly equals 42 manifest payloads plus
  manifest and `.gitattributes`; no missing or extra artifact files.
- Manifest SHA-256:
  `4b37f1e9c5d9692b42472640f07b9c1438d0b4d037c4298921ef84a22b5ef0af`.
  All 42 payload sizes/SHA and six manifest source SHA match. All 42 payloads
  also equal their retained original `.tools` source bytes. The seventh source
  (implementation report) is covered by the complete delivery-path map.

## SPEC assessment

The eight V011 CREATE statements equal the adopted B00-1.2 full DDL byte for
byte. Its 21 deployed FK statements equal the adopted filtered statements in
order. All eight CREATEs precede every ALTER, including the cyclic
source-revision/processing-input references. Only material FK
`transcription_input.fk_transcription_input_3` is deferred to V017. There is no
future material table, seed, IF NOT EXISTS, business implementation or format
activation in this range. The two SET statements establish UTF-8/UTC.

The oracle SHA remains
`94e7b136824d1845462df644799db0fe4fbf8dce2ba28e961dd3df29bfe7b182`.
I independently filtered actual adopted full metadata source
`3de8041db8860f1547ff53a94b4ddd79470c4881340111fdafbc3ce5322399ad`
(85 tables/674 columns), excluding exactly the one material FK row. The result
is exactly the complete oracle, including the material backing index:

| Metadata group | Independent target | Corrected actual | Exact equality |
|---|---:|---:|---|
| Tables | 8 | 8 | yes |
| Columns | 63 | 63 | yes |
| Index columns | 39 | 39 | yes |
| CHECK constraints | 13 | 13 | yes |
| FK columns | 22 | 22 | yes; 21 constraints |

No index row is excluded. Current centrally adopted B00-1.3 has a different
full DDL SHA (`ebf1eae5…`), but an independent comparison also confirms these
eight CREATEs and 21 deployed FK statements are unchanged there. The historical
preparation source/oracle remains pinned to B00-1.2; the three preserved
preparation record/resource/script files equal their original preparation
commit bytes.

V011 remains 10,941 bytes, SHA-256
`a926c8b5d9806082d248f38bc7c7dd2bdbc46dd319d34b787e02f86132096ccb`.
The original first full result was correctly preserved as 11 tests/1 failure,
with the single missing adopted material backing index (38 actual vs 39
expected index-column rows), no extra row, and the other four metadata groups
already exact. The independent oracle was not weakened.

Root centrally registered V011.1 in
`d7ab59db3dddeed066bb6c995ff7396276198a3b`, with the explicit bounded patch-number
policy, unchanged V012–V027 reservations, and next integer V028. The 397-byte
V011.1 file, SHA-256
`9efc688e49ce4afc52f383929b91d70970905f04b4e7836bdb0533eac50a9713`,
contains one CREATE INDEX on `transcription_input(material_snapshot_id)`.
Both migrations equal root's pre-application frozen copies. Offline CRC
recomputation equals actual Flyway checksums -286330771 and -570494170.

The central freeze commits and retained ACK records precede actual application:

| Event | Actual UTC | Proof |
|---|---|---|
| V011 freeze commit | 2026-10-21 20:08:21 | `0ca7768988145cca7fe5826ac25d783fdc5ccdad` |
| V011 preapply preservation commit | 2026-10-21 20:09:15 | `1549bf86682dae2902b6e141c8b53118e4a8f1f1` |
| V011 retained ACK | 2026-10-21 20:09:43.490688 | `review/root-ack.json` |
| V011 applied | 2026-10-21 20:10:05 | rank 9; epoch 1792613405 |
| V011.1 freeze commit | 2026-10-21 20:17:14 | `8fff159b2fe85b9b91b7ccf82fe1b6618aad7629` |
| V011.1 retained ACK | 2026-10-21 20:17:48.231149 | `review/index-root-ack.json` |
| V011.1 applied | 2026-10-21 20:18:11 | rank 10; epoch 1792613891 |

The preserved original snapshot still contains `2026-10-22 04:10:05.0`.
Original XML establishes JVM Asia/Shanghai; separate native UTC/epoch evidence
establishes session +00:00/system UTC and the corrected times above. Raw
snapshots were retained, rather than rewritten to correct that earlier label.
The actual pinned Flyway 12.4.0 API/parser probe source has no database load or
connection operation. Probe output validates `011 < 011.1 < 012`, filename
parsing to valid 011.1, and its source SHA; the actual local JAR hash independently
matches `c3f93df1f3e0c9d14652adb5ade97401f2ede503859ac693c2af5c06e2797799`.
Probe source/output also equal root's pre-apply captured bytes.

## Quality and evidence assessment

Reviewed `TranscriptSchemaIT.java` setup and fixture guards (lines 76–159,
392–433), five-group comparison (174–189), physical checks (217–357), snapshot
logic (448–492), and exact one-index delta assertion (502–533). Fixture identity,
original empty capture, pinned image, loopback URL/port, lower frozen SQL hashes,
known successful version/checksum history and Flyway validation precede seed
writes. Failed, unknown, incomplete and newer histories stop; no reset, repair,
outOfOrder or downgrade is used. Row probes use one non-autocommit connection
with rollback. Production `SchemaMigrator` is unchanged and migrates latest;
private historical test target is 011.1.

Fixture evidence consistently identifies only
`ama-test-b01-59f108f79cad`, container
`487a1cd59234e10967d6a8c5fa8d4fec7abd94fd038d976f00a1003783cdfd19`,
127.0.0.1:45591, MySQL 8.4.6, initial zero tables, actual native AIO zero. The
process-local wrapper only appends the native-AIO option to the reviewed helper.
The shared helper, both scoped POMs, central migration register in the owner,
and all eight lower SQL files are byte-identical to BASE.

Fresh parsing of committed original XML, with corresponding raw Maven log
inspection, yields:

| Preserved run | Tests | Failures | Errors | Skips | Assessment |
|---|---:|---:|---:|---:|---|
| Initial setup attempt | 1 | 0 | 1 | 0 | missing-target configuration error; not RED |
| Lower010 table-presence RED | 1 | 1 | 0 | 0 | actual eight-table assertion expected list vs empty list |
| First frozen V011 suite | 11 | 1 | 0 | 0 | sole metadata/index failure; preserved |
| Corrected V011.1 suite | 11 | 0 | 0 | 0 | author functional suite; Maven BUILD SUCCESS |

The selected test names match current source. XML confirms pinned JDK
21.0.12.1, the intended owner/classpath, Asia/Shanghai, the correct frozen SHA
properties on the corresponding runs, and NoUnitSelected. Corrected retained
class bytes equal the actual target test class; both actual Flyway classpath SQL
resources and the classpath oracle equal reviewed source bytes. The separate
compile logs report compilation only and are not counted as executed tests.
No unrelated or unselected test cohort is credited.

Physical cases cover all accepted vocabularies/case rejection, material-mode
consistency and explicit schema-level PINNED allowance, required/nullable
parser/model/prompt/source semantics, positive manifest version, nullable
plaintext coordinates/speaker, partial/reversed ranges, every deployed FK,
circular provenance, RESTRICT deletes, composite marks and duplicate rejection,
history/order/confirmation/batch-file uniqueness, exact-case source IDs,
NO PAD human identifiers, and long Unicode. These are substantive database
constraint probes, not constant/count substitutes for behavior.

Independent snapshot recomputation establishes all of the following:

- Lower-before equals after-RED byte-for-byte; stripping only the eight added
  tables and new history versions from first/corrected snapshots reconstructs
  the entire lower-before structure exactly: DDL, rows, all five metadata groups,
  original history fields and all eight lower SQL SHA values.
- Lower sentinels are nonempty for identity/session, meeting/info, source file,
  storage backend, audit, task subject and durable task provenance.
- First-after-upgrade equals first-after-tests. Correction-before and
  correction-after have identical table sets and all non-history rows; only
  `transcription_input` DDL changes, by the exact named index line.
- The complete old nine-row Flyway history prefix equals the corrected prefix
  field-for-field. Exactly rank 10/011.1 is appended, with the correct filename,
  success and checksum. Tables/columns/CHECK/FK metadata are unchanged; exactly
  the independently adopted material index row is added, none removed.
- Corrected after-upgrade, after-tests, index-after, historical rerun-zero and
  production-latest-rerun-zero snapshots are entirely equal. Raw logs record
  both reruns as up to date/no migration, consistent with the zero assertions.
- All earlier central source captures match committed author evidence. The
  preparation report archive remains 6,359 bytes, SHA-256
  `d862e0993f46a7348e9f26f58672192b7e763d059034f372e227e417ae045992`.
- Known fixture password bytes were scanned locally across all 44 artifact
  files with zero hits; no credential env file is packaged. Actual secret
  values were never emitted.
- `.gitattributes` is limited to this artifact directory (`* -text` and
  `*.log -whitespace`); the raw log bytes remain intact. Fresh complete-range
  `git diff --check` exits zero; owner HEAD matches the reviewed HEAD and owner
  tracked/untracked status is clean.

## Findings, limits and recommendation

**SPEC findings: none. Quality findings: none.** No severity/file-line finding
is emitted because no actionable defect remains in this scoped delivery.
The original omission is corrected by the registered immutable forward patch;
it is not treated as an unresolved finding against the corrected group.

Adopt the corrected schema group only. The material FK remains deferred to V017;
Application B must still enforce EMPTY/NULL. Same-meeting, batch/source/snapshot
membership, immutable business history, confirmation eligibility and saved-content
hash rules require later application evidence. Parser/API/worker/ACL/format
activation, full B06 acceptance, company integration, storage performance,
capacity, HA and production qualification remain open.

This review executed read-only file/Git inspection, independent parsing,
hashing and comparison. It did not execute tests/builds, SQL, Docker/service
commands, production/company calls, Git mutations or source changes; no child
agents were created. Only this requested review report was written. Runtime
results above are independently examined preserved author evidence, not a
fresh reviewer test execution. The live fixture and original evidence are left
for root archival before any cleanup.
