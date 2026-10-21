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

## 协调者结构采用记录（2026-10-22）

完整260b6f84..234e6e2（含两个准备提交）经fresh独立SPEC PASS/QUALITY APPROVED、0P0—P3，原件SHA0caf432a…；三提交归集为72e1d9e4、82f2e6a8、e6fe625c。67个最终路径在协调附注前与审查路径SHA/Git blob逐字节相同。作者60payload/finalmanifest和原checkpoint/source/报告完整保留；协调者另归档原始报告/评审/路径表/归集log和字节验证清单(-text)。[完整独立评审](generation-schema-independent-review.md)。没有为机械归集重复跑库或测试。

采用仅结构前置：V012完整SQL10742ca2…/21260bytes/checksum2101765957不变；实际目标19/123/92/21/45、11IT/0fail/0error/0skip，原lower10文件和DDL/行/元数据/history前缀保留，两个rerun各0。首次应用后未修SQL或oracle，两个延后材料FK索引均显式保留。确认时间区分：root读取/静态冻结capture20:53:07.558007UTC，Git中央冻结commit7b80ae28实际20:53:08UTC(epoch1792615988)，ACK20:53:38.528017，首次应用20:55:06(epoch1792616106)。原UTC/epoch与JDBCAsiaShanghai原件均保留。

generation-schema-preparation.py仍固定十个低版本的历史准备上下文；在原e568028/b750f3f checkout复现，不能削弱guard来强迫当前已含V012位置重跑。完整B00来源和元数据未变。不相关locator列的schema/prose差异由应用严格union验证承担，物理CHECK未更改。四个接受的rawSQL边界证据不能被当成权限/同会议/真实hash/语义拒绝测试。

V013可按已审1.3继续准备；完整B07领域/模型/档案恢复/来源验证/CAS/事务/候选/最终确认和两个交互仍OPEN，结构采用不打开最终B验收、业务Worker、复杂材料或真实公司/生产/HA/nativeI/O/容量资格。
