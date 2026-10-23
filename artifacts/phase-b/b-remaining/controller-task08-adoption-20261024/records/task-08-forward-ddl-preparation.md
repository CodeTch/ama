# Task 8 unversioned forward DDL preparation

Date: 2026-10-23. PREPARATION ONLY. Product baseline: `35b2325aee8e6c09aa1bdebc880c4f09bd37837b`. Worktree: `/home/tanren/ama/.worktrees/phase-b-completion`. The only authored paths are this report and adjacent `task-08-forward-ddl-proposal.sql`, under `.superpowers/sdd/2026-10-23-ama-phase-b-remaining` (W).

The root-adopted wrapper at adoption commit `1834ebb8` governs the exact R1 design and its independent reviews. Earlier R1/closure wording that adoption is pending is historical; it does not reopen the adopted ordinal choice, bounds, or accepted local backlog latency. FUP-B05-CANDIDATE-01 remains OPEN. This is an exact candidate for later independent DDL review, not that review's result. No numbered migration, formal header, version allocation, byte freeze, ACK, schema application, source activation, or runtime fairness closure is supplied. Tasks 5/6 have no new DDL; Task 8 activation follows their adoption and precedes Task 7 external activation.

## Exact SQL candidate

`W/task-08-forward-ddl-proposal.sql`: 164 bytes; SHA256 `023fe875efd2be0bbd9d59b2d22a9b6429ea88eb6fc7a7b30d8541a53c0d46ee`. Encoding is UTF-8/ASCII, no BOM, three LF-terminated lines, one final LF. The complete bytes are:

```sql
ALTER TABLE external_operation
    ADD COLUMN reconcile_scan_seq BIGINT NOT NULL AUTO_INCREMENT,
    ADD UNIQUE INDEX external_reconcile_scan (reconcile_scan_seq);
```

This is the adopted R1 three-line ALTER, copied exactly from its fenced SQL shape. It has one statement, no comments or migration identity, no transaction/session directives, no seed or UPDATE/backfill statement, no trigger, no new FK and **no new CHECK**. Omitting ALGORITHM/LOCK preserves the reviewed shape; this does not assert instant or online operation. Root must resolve actual pinned-engine behavior before freezing any formal candidate. Do not use IF NOT EXISTS to conceal partial or incompatible schema. Formal migration allocation/header/order/freeze and separate exact-byte ACK are later root-owned actions; this filename is outside all Flyway resources.

## Baseline and exact read identities

Read the prepared Task 8 brief first, then its plan extract/runtime custody supplement, adoption wrapper, exact R1, closure, original review, R1 review and original B05 follow-up. Read-only source inspection used `git show 35b2325aee8e6c09aa1bdebc880c4f09bd37837b:path`, so concurrent Task 5 uncommitted work is not used as a new Task 8 baseline. A failed lookup of the nonexistent `tools/checks/mysql_fixture.py` was corrected by tree inspection to `tools/dev/mysql_fixture.py`; neither helper was run. No repository test, generator command, Maven/Flyway process, database/Docker call, private environment access, resource creation, Git mutation or subagent dispatch occurred.

Adopted/current document identities (raw bytes):

| Input | Bytes | SHA256 |
|---|---:|---|
| `docs/superpowers/specs/2026-10-23-ama-phase-b-reconciliation-fairness.md` | 3365 | `8535567be287f8baed0a1bde8f47dbc1f4993f4581dbb4f88ea48bcbcbde0907` |
| `artifacts/phase-b/b-remaining/controller-fairness-design/task-08-fairness-design-r1.md` | 33231 | `4eefdd7da3abf58f13d0fe7e15fbe07c1740fa035f08bb5e60b0a708b9a08284` |
| `artifacts/phase-b/b-remaining/controller-fairness-design/task-08-fairness-design-r1-closure.md` | 4329 | `d5438fd0eb8b6fffc21997580a1ba4af723f17e479afa9968daae59f53a0a768` |
| `artifacts/phase-b/b-remaining/controller-fairness-design/task-08-fairness-design-r1-review.md` | 5954 | `3fcc74b1b3868860ea19d1f36ec3f609ccc083f7e6ae381f5e3b8143196b7f23` |
| `artifacts/phase-b/b-remaining/controller-fairness-design/task-08-fairness-design-review.md` | 18267 | `7c78bf8608ff0019bf45c21174badb6e40d83a547ba1f5d16d223f2616a99120` |

Pinned source/model identities at 35b2325aee8e6c09aa1bdebc880c4f09bd37837b:

| Input | Bytes | SHA256 |
|---|---:|---|
| `backend/adapters/src/main/resources/db/migration/V010__reliable_execution.sql` | 35136 | `03e89cc8bd58d5a6aa0d4ab031b8fc05a4f7d2450623df147cd84d05b68977db` |
| `tools/checks/database_design.py` | 108070 | `91493cbb251c99c800bd2aec7861596d50ac76acbf9a159d14c59aaf5b71e6a0` |
| `docs/database/ama-schema-draft.sql` | 116623 | `d3094336b4d55d9389b2fd1d852d627b93e0ac0394b9b1801e258a154b0cb445` |
| `docs/database/ama-data-dictionary.md` | 167091 | `cd6a909abbcd4d20afaf00a3b98ce6329cc633b31e5e941166f16ddbd3177c74` |
| `docs/superpowers/specs/2026-10-21-ama-database-design.md` | 117367 | `05bfe0fdf1f2c05af67598ef8298c19a1ec138f51948026f44b9ee41c9793f04` |
| `backend/adapters/src/main/java/ama/task/JdbcExternalOperations.java` | 19114 | `63e84274395c74c8d5e5375fcfc898c5cc39f19447318a2093eebaff1496ad19` |
| `backend/integration-tests/src/test/java/ama/task/B05SchemaIT.java` | 4451 | `2a5bfb7e4417f4108060553362382a47b6bca5dafb80518923d372c31b50ec04` |

V010 establishes the operation and task definitions below. No V011-through-V013.1 migration at this baseline ALTERs either table. V010 is 35,136 bytes and immutable. The current generated review model has the same relevant definitions; its future/D01 content is not deployed-schema authority.

### Existing external_operation

```sql
CREATE TABLE external_operation (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `operation_kind` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `business_key` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `marked_at` datetime(6) NOT NULL,
  `next_check_at` datetime(6) NOT NULL,
  `reconcile_epoch` bigint unsigned NOT NULL DEFAULT 0,
  `reconcile_owner` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `reconcile_expires_at` datetime(6) NULL,
  `external_result_id` longtext NULL,
  `last_error` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `subject_kind` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `subject_id` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `current_execution_no` bigint unsigned NOT NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX external_key (operation_kind,business_key),
  INDEX external_due (state,next_check_at,id),
  CONSTRAINT ck_external_operation_0 CHECK (state IN ('MARKED','UNKNOWN','RETRY_QUEUED','SUCCEEDED','FAILED','MANUAL_REQUIRED')),
  CONSTRAINT ck_external_operation_1 CHECK (current_execution_no > 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE external_operation ADD CONSTRAINT fk_external_operation_0 FOREIGN KEY (subject_kind,subject_id) REFERENCES task_subject (subject_kind,subject_id) ON UPDATE RESTRICT ON DELETE RESTRICT;
```

Its 15 columns remain in their existing order. The proposal appends `reconcile_scan_seq` as ordinal position 16; signed `bigint`, `IS_NULLABLE=NO`, `COLUMN_DEFAULT=NULL` (no explicit default), `EXTRA=auto_increment`, no character set/collation, precision 19 and scale 0. Positive persisted values fit Java `long`, maximum 9223372036854775807; zero is the in-memory cursor sentinel, not a valid owned operation ordinal. Numeric cursor comparison has no UUID/text collation dependency.

Existing `id` remains the clustered PK and immutable operation identity. `external_key(operation_kind,business_key)` remains unique; `external_due(state,next_check_at,id)` remains present. The existing FK to task_subject uses the InnoDB supporting index `fk_external_operation_0(subject_kind,subject_id)` because neither existing explicit index has this left prefix. Expected total operation index definitions become 5 from 4 and explicit information_schema.STATISTICS key-part rows become 9 from 8. The added visible BTREE is `external_reconcile_scan`, NON_UNIQUE=0, SEQ_IN_INDEX=1, COLUMN_NAME=reconcile_scan_seq, COLLATION=A, SUB_PART=NULL, no expression. InnoDB's secondary-index inclusion of the PK permits the ordinal/id projection without adding `id` to the declared unique key; actual EXPLAIN remains required.

Both existing CHECKs remain enforced and unchanged. The one outbound composite FK and all inbound references (including external_execution.operation_id and any other actually deployed child) retain their columns, names, rules and parent identity. New FK count is zero. Table engine/default remain InnoDB/utf8mb4_0900_ai_ci; ascii/ascii_bin identifiers/codes/hashes and utf8mb4_0900_bin business/subject keys retain their explicit overrides; nullable external_result_id retains the default longtext collation. No column is renamed/retyped/reordered and no authority relation changes.

MySQL permits AUTO_INCREMENT on a suitable secondary index; the new sole-column unique index supplies the required leading allocation key. This does not make the ordinal a commit-order or authority token. See [MySQL 8.4 AUTO_INCREMENT handling](https://dev.mysql.com/doc/refman/8.4/en/innodb-auto-increment-handling.html).

### Existing durable_task, explicitly unchanged

The following current-model transcription matches the effective V010 ALTER, retaining all 22 fields, types/defaults/collations, PK, four secondary indexes, three CHECKs and composite subject FK. V010 transformed its empty legacy table; Task 8 neither repeats nor modifies that migration. No ordinary queue OFFSET selector or task claim index belongs to this proposal.

```sql
CREATE TABLE durable_task (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `task_type` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `pool` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `subject_kind` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `subject_id` varchar(255) COLLATE utf8mb4_0900_bin NOT NULL,
  `producer_key` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `input_kind` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `input_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `input_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `business_version` bigint unsigned NOT NULL,
  `cancel_epoch` bigint unsigned NOT NULL,
  `priority` int NOT NULL,
  `state` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'READY',
  `attempt` bigint unsigned NOT NULL DEFAULT 0,
  `max_attempts` bigint unsigned NOT NULL,
  `lease_owner` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `expires_at` datetime(6) NULL,
  `available_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `output_kind` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `output_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `last_error` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  PRIMARY KEY (id),
  UNIQUE INDEX task_producer (producer_key),
  INDEX task_ready (pool,state,available_at,id),
  INDEX task_expired (pool,state,expires_at,id),
  INDEX task_subject_history (subject_kind,subject_id,created_at,id),
  CONSTRAINT ck_durable_task_0 CHECK (state IN ('READY','RUNNING','RECONCILING','SUCCEEDED','FAILED','CANCELLED','STALE')),
  CONSTRAINT ck_durable_task_1 CHECK (max_attempts > 0),
  CONSTRAINT ck_durable_task_2 CHECK (state <> 'RUNNING' OR (lease_owner IS NOT NULL AND expires_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
ALTER TABLE durable_task ADD CONSTRAINT fk_durable_task_0 FOREIGN KEY (subject_kind,subject_id) REFERENCES task_subject (subject_kind,subject_id) ON UPDATE RESTRICT ON DELETE RESTRICT;
```

The approved fairness claim discovers the actual operation -> current external_execution -> durable_task full TaskSpec. It preserves identity -> subject -> complete domain/source graph -> all relevant tasks/attempts -> operation/execution ordering, genuine registered preparation, SKIP LOCKED and fresh locked due/expiry. None of that requires a durable_task column/index or FK change.

## Current-model ownership and exact prospective delta

At 35b2325aee8e6c09aa1bdebc880c4f09bd37837b `tools/checks/database_design.py` has VERSION `B00-1.5-runtime-metadata`; `TABLES` is the single model source. Its `generated()` owns `docs/database/ama-schema-draft.sql` and `docs/database/ama-data-dictionary.md`; `er()` owns only the generated ER appendix of `docs/superpowers/specs/2026-10-21-ama-database-design.md`. `generate` writes all three outputs, and `verify` can operate a review fixture: neither command was invoked here.

Read-only counting of the pinned generated DDL yields 98 CREATE TABLEs, 731 columns, 187 explicit index definitions including PKs, 116 CHECKs and 208 FK constraints. For this exact delta, those model counts become 98 / 732 / 188 / 116 / 208. These are review-model counts, not actual shared deployment counts; implicit FK support indexes are excluded from 187/188. No runtime table-count claim follows from the full future model.

At later activation the Task 8 sole source author owns the precise additive current-model update and source implementation under root review:

- Append a signed `bigint` column to the final operation column list, with the same position and no default; do not use `num()`, which produces `bigint unsigned`.
- Represent AUTO_INCREMENT consistently in SQL rendering and expected physical metadata. Currently `sql()` at line 457 and `verify()` expected-columns at line 627 special-case only `audit_event.id`; updating the column list alone would silently omit AUTO_INCREMENT or fail the oracle. Preserve audit_event behavior. Make the dictionary explain server allocation/positive-long domain/immutability and the no-explicit-default exception; no claim that the DB prevents arbitrary administrator reassignment.
- Add exactly `external_reconcile_scan(reconcile_scan_seq)` as a unique index and update operation policy/ER field. No new ER relation or PK change. The generator already emits numeric `bigint` ER fields and indexes from the shared model.
- Root assigns any model revision/migration provenance wording at activation; this preparation assigns neither. Preserve all unrelated definitions and D01 `UNASSIGNED-D01` entries. Do not deploy the full generated draft.
- Preserve the exact prior current model output before regeneration in a newly authorized artifact location. The Task 2 prior-draft artifact, all design-history snapshots, old goldens, the 13 immutable SQL files and the other 4727 protected historical paths remain untouched. This preparation writes no preservation artifact outside the two assigned outputs.

## Legacy rows, backfill, immutability and compatibility

The ALTER itself allocates the new enumeration metadata for **every existing operation**, including terminal, non-due, registered and unregistered policy rows. Legacy ordinal assignment order is unspecified; neither old marked_at/next_check_at order nor UUID order is reconstructed. There is no semantic backfill, application UPDATE, policy registration, task/execution creation, audit invention or promotion to due/claimable state. Only the new metadata differs during the controlled migration window; all 15 original column values, relations and all other table rows must compare exactly.

Repository INSERTs omit the new column. Pinned JdbcExternalOperations.beginExternal uses an explicit column list and reads old fields by name through SELECT *, so the old marker writer continues to receive a server-generated ordinal after the additive change. Existing retry updates the same logical operation; begin/retry/claim/renew/observe must never assign its ordinal. Old reconciliation binaries still have their old first-64 limitation and cannot qualify new fairness. A future scanner requires the new column/index and must fail visibly on missing schema, with no fallback to the old selector. New schema first, new scanner after successful migration/physical checks; consumer activation remains separately gated.

AUTO_INCREMENT is a generation facility, not complete positive-domain or anti-tamper enforcement. MySQL forbids CHECK expressions referencing AUTO_INCREMENT columns; the approved candidate intentionally adds no `CHECK(reconcile_scan_seq > 0)` or bounded CHECK. See [MySQL CHECK rules](https://dev.mysql.com/doc/refman/8.4/en/create-table-check-constraints.html). An explicit valid positive value may be used only by controlled restore/import; explicit negatives and zero under relevant SQL modes can violate the application's metadata invariant without a DB CHECK. Therefore qualification must distinguish DB uniqueness/NOT NULL from positive/immutable repository and restore invariants. NULL input to an AUTO_INCREMENT INSERT normally requests allocation, and zero depends on NO_AUTO_VALUE_ON_ZERO; do not write a false negative test expecting every NULL/zero INSERT to fail. Application reads/mapper/cursor validation reject invalid supplied metadata; pre-activation and restore verification checks the complete row set, since a `>0` scan cannot itself discover a hidden nonpositive row.

Controlled restore/import preserves the exact operation-ID-to-ordinal mapping for surviving rows, positive uniqueness and old row values, then verifies omitted-column allocation above the restored maximum under the actual increment/offset settings. Never resequence existing operations or reset a live cursor around arbitrary reseeding. Default allocation is not commit order: rolled-back gaps and late commits below a captured high-water are expected and revisited next pass. Crash/restart qualification concerns surviving persisted identities and safe rescan, not an unsupported promise that every uncommitted allocation is forever unreusable.

For exhaustion, retain the full signed-long endpoint; no `after+1` or `upper+1` arithmetic is needed. Exact MAX/near-MAX parsing/binding and server allocation failure must propagate without wrap, duplicate ordinal, ordinal rewrite, fallback, token or send authority. The MySQL documentation describes allocation exhaustion as an INSERT error; capture actual pinned-engine SQLState/error rather than inventing its exact driver mapping. Exercise this on a separately root-assigned disposable target/namespace, never poison the shared operation counter. No quota, rollover migration or reset is proposed.

Adding AUTO_INCREMENT requires substantial table reorganization and blocks concurrent DML, with at least the documented shared-lock behavior; it is not instant/no-lock deployment. See [MySQL 8.4 online DDL operations](https://dev.mysql.com/doc/refman/8.4/en/innodb-online-ddl-operations.html). One-time rebuild/index/disk/time cost and metadata-lock waits must be measured/bounded for the selected owned fixture. Stop or quiesce relevant writers during the preservation window according to root's later execution plan. This report neither selects an algorithm nor changes the reviewed statement.

## Engine/Flyway custody and preservation

Pinned tools/dev/mysql_fixture.py declares image `mysql@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9`; the model verifier requires MySQL 8.4.6. The baseline migration register records Flyway 12.4.0 and its prior ordered 013.1 allocation. Historical public Task 2 classpath evidence (`artifacts/phase-b/b-remaining/task-02/creator-assertion-red.xml`, SHA256 `31c368eaf4c4dd2c2297cd2477182e5a6536647c8ccbdb3c2d5579b96b660dcf`) identifies flyway-core/mysql 12.4.0 and Connector/J 9.7.0. These are pinned-source/historical facts, not current runtime inspection. Reconfirm actual selected engine/image/version, SQL modes, AUTO_INCREMENT settings, datasource and resolved Flyway/driver at later activation.

The exact baseline contains these 13 immutable formal files (tree lexical order below; installation order is taken from history, never lexical sorting):

| File under backend/adapters/src/main/resources/db/migration | Bytes | SHA256 |
|---|---:|---|
| `V001__foundation.sql` | 709 | `1726d9db3eb45d0fe86314dd41ad52d5f2332e3f16995cc246f3c3c532208335` |
| `V002__identity_acl.sql` | 1781 | `68b0d478504a554344cb12dfa8ef79c62da5c6e3455317791762fca25e70f7d0` |
| `V005__tasks_outbox.sql` | 2461 | `55cfdc4aabf169c82f30303ac4d03dce7f24b3c6af5b4cc3ebd0089ae7e7dda7` |
| `V006__audit_metadata.sql` | 1864 | `f36b7ab2e8d32cf5de6eadcd86524ab269dce98f7906b808eff1a0e39325178f` |
| `V007__identity_sessions.sql` | 3795 | `859970b83fc5623b18340c2de793ea35429cc3c0da5cbd1365b4763a5a64780d` |
| `V008__meeting_schema.sql` | 11205 | `fa44424aa1ff32e049929d45865fe7133c2548fc9ea451f3350cd8984c118ce3` |
| `V009__file_schema.sql` | 7239 | `79f048987282c82fd7a56d697a02e88c93fa45b3349bd4d43e840dca23ba437d` |
| `V010__reliable_execution.sql` | 35136 | `03e89cc8bd58d5a6aa0d4ab031b8fc05a4f7d2450623df147cd84d05b68977db` |
| `V011_1__transcript_material_index.sql` | 397 | `9efc688e49ce4afc52f383929b91d70970905f04b4e7836bdb0533eac50a9713` |
| `V011__transcript_schema.sql` | 10941 | `a926c8b5d9806082d248f38bc7c7dd2bdbc46dd319d34b787e02f86132096ccb` |
| `V012__minutes_evidence.sql` | 21260 | `10742ca2075fa3430dedf692853e1c106bd4464eaed46d8c8c1aa07859878fb7` |
| `V013_1__b_runtime_metadata.sql` | 1817 | `9280baea017014f3322af901373a7f8d69dfaa396fdd49f3c5d872535c43a520` |
| `V013__publications.sql` | 7661 | `1de37066869617723a11f05d7423584143a64e0c9ddf8879c7b4fd107de08e3c` |

Expected prior successful version sequence is 001,002,005,006,007,008,009,010,011,011.1,012,013,013.1. Preserve the actual installed_rank/version/description/type/script/checksum/installed_by/installed_on/execution_time/success values for all 13 rows, not merely the count. V013.1's adopted checksum is -1898004857. Root must verify exact then-current history and candidate version ordering with the resolved Flyway before allocating; reserved integers/empty numbers must not be guessed or reused. Unknown/failed/newer/drifted history blocks application and is reported, never repaired or reset.

No candidate SQL checksum is labelled a Flyway checksum here: the SHA above covers only the unversioned three-line file. Any later header/whitespace change produces different full formal bytes requiring review/freeze and separate ACK. Initial successful application should add exactly one root-selected history record after the verified prefix. A normal Flyway rerun must execute zero migrations and preserve the entire resulting history and data. Raw repetition of this ALTER is not idempotent and would encounter existing definitions; rerun safety comes from immutable Flyway history, not ignoring duplicate-column errors. Failed application keeps actual schema/history/raw failure evidence for root's forward-only ruling; no edit/repair/clean/reset/outOfOrder/drop/recreate route.

## Prospective evidence and acceptance matrix — none executed

All fixture allocation, targets, evidence paths, application and tests below require later root activation/custody, independent exact-DDL review and selected formal-byte ACK. The existing shared 13-migration schema is unchanged by this preparation. Before any new resource, root supplies its guard. Revalidate all assumptions after Tasks 5/6; no ordinary implementation author may allocate or self-ACK DDL.

| Gate | Exact future evidence |
|---|---|
| Baseline/negative schema assertion | On the root-selected pre-forward target, assert ordinal/index missing with a genuine assertion failure and preserve failure; new scanner missing-column/index failure must be visible. Record initial complete schema/history and untouched pre/post data. Setup errors are not behavioral RED. |
| Physical additive metadata | Capture information_schema TABLES/COLUMNS/STATISTICS/CHECK_CONSTRAINTS/TABLE_CONSTRAINTS/KEY_COLUMN_USAGE/REFERENTIAL_CONSTRAINTS plus SHOW CREATE before/after. Assert exact new column and unique visible index properties above, all prior columns/ordinals/defaults/collations/check ASTs/indexes/FKs unchanged, no extra objects. SHOW CREATE changes only by the expected column/index and engine-generated AUTO_INCREMENT state; compare it structurally, not byte-equal to the original whole CREATE. |
| Complete legacy preservation | Snapshot complete bounded owned fixture rows with deterministic PK order, full values, explicit NULL and lengths/bytes/UTC microseconds. Preserve every preexisting table; compare external_operation excluding only new column and compare all other data exactly. Include genuine markers spanning all existing operation states, future-due, terminal history and absent-policy registry; old UUID/request/execution/task/owner/epoch/times/outbox/relations identical. Prove each old row gets exactly one positive unique ordinal and total row counts match; no truncation/sample masquerading as complete evidence. |
| Omission/new insert and stable ordinal | Exercise the existing explicit-column writer after migration. New marker ordinal is unique and above prior max under the selected settings; retry reuses operation and ordinal, appends the appropriate execution only through the existing protocol. Claim/renew/observe and dedup replay retain the mapping. All-state scan coverage is independent of policy and due filters. |
| Actual constraint negatives | A duplicate explicit positive ordinal on a different otherwise valid owned operation must fail the unique key atomically. Existing invalid state, current_execution_no=0, duplicate operation business key and nonexistent subject must still fail their existing CHECK/unique/FK. Keep test setup relationally valid so the intended constraint is the cause; preserve actual error and rollback. Positive-domain mapper/restore rejection and immutable-writer tests are distinct from DB constraint rejection. |
| Restore/import and overflow | On separately approved isolated targets, restore exact valid ID/ordinal mapping, verify allocation above max, gaps and restart. Capture duplicate/invalid metadata rejection by the applicable layer, MAX signed-long parsing, exhausted generation failure with unchanged row count/old mapping, transaction rollback and no token/send. Do not assert NULL always fails or mutate the shared counter to MAX. |
| First migration/history/rerun | Record actual selected target identity and source seal, frozen full file hash, prehistory, initial migrationsExecuted=1, full prefix identity, new success/checksum/rank, complete immediate preservation window, then normal rerun migrationsExecuted=0 and unchanged history/schema/data. Preserve failures. Historical B05SchemaIT remains an exact target-010 oracle and runs only on a separately guarded fresh target-010 fixture, never on the forward shared schema. |
| Query-plan and accounting | EXPLAIN actual high-water/backward index and raw numeric range seeks on external_reconcile_scan; no filesort or filtered due-table sweep. Trace all explicit SQL, result/mutation rows, both candidate session SETs and separate driver controls. Include opening success P=B=1 (13+Q SQL, 11+R rows), four unregistered candidates P=2/B=4 (23 SQL/17 rows), active final-empty page (1 SQL/0 rows), and policy's actual finite closure. |
| Fairness adversaries | Separate >64-held-identities and >64-unregistered-policy genuine marker cases: 129 blockers + registered available tail, P=16/B=32, tail by 5 calls. Assert skipped business rows/outbox unchanged, no absent-policy authority, complete identity-first locks, partial-lock release and later revisit after availability. Mixed held root/task/operation/execution cases also preserve lock order. |
| Cursor/race/error/recovery | Multiple calls, arrivals above fixed high-water, a low reserved ordinal committing late behind cursor, rollback gaps/deletion, terminal/non-due prefix, exact/short/empty page boundaries, success consuming only through returned row and refetching tail, at most one pass/call, no same-call wrap. Fresh locked full spec/current execution/due+expiry; tryLock contention independent of renew/observe; separate instances and fresh JVM against retained owned state; discovery versus candidate exception progress and commit-ACK ambiguity with no token/resend. UNKNOWN remains lookup-only with one execution. |
| Regressions and independent adoption | Later source author runs inherited external/reconciliation/full-identity/task/outbox/permit regressions and canonical allunits once on final source, preserves fresh reports/command hashes and actual versus prospective qualifications, then root arranges independent implementation/evidence review. No current report closes the original B05 follow-up or substitutes for Task 7 permit recovery. |

The fixed high-water query is `SELECT reconcile_scan_seq FROM external_operation FORCE INDEX (external_reconcile_scan) ORDER BY reconcile_scan_seq DESC LIMIT 1`; a pass reads `SELECT reconcile_scan_seq,id FROM external_operation FORCE INDEX (external_reconcile_scan) WHERE reconcile_scan_seq > ? AND reconcile_scan_seq <= ? ORDER BY reconcile_scan_seq LIMIT ?`. JDBC binds signed longs and a positive bounded limit. Both are nonlocking discovery, no state/due/policy JOIN/WHERE filter. Advance through a validated consumed row before its independent candidate transaction; genuine authority is re-established under locks. The index adds no capability.

The adopted envelopes remain `1+ceil(B/P)+B*(11+Q_policy)` explicit SQL and `1+B+B*(9+R_policy)` result/mutation rows, with actual policy costs and separately measured driver controls. They are logical returned/affected-row bounds, not hard MVCC page/I/O bounds. The accepted local-only tradeoff remains 100,000 terminal prefix rows plus tail at B=64: 1,563 calls and about 7,810 seconds of existing poll spacing before query time. Persistent lock denial or indefinitely repeated restarts can prevent service. No production capacity, uninterrupted migration, availability, latency SLA or full business-loop qualification follows.

## Disposition

Preparation complete for independent exact-byte review. No source contradiction requiring an amendment to adopted R1 was found. The generator's AUTO_INCREMENT special case is a known implementation implication within the adopted ownership, not a new design choice. Actual engine/DDL/old-data behavior and all test rows above remain unexecuted gates. Root later supplies activation baseline after Tasks 5/6, separate exact-DDL review, allocation, full-byte freeze, selected-target ACK and eventual independent fairness review. This report's own byte count/SHA256 are returned separately to avoid a self-referential digest.

Final read-only verification: the proposal bytes equal the exact ALTER code block in adopted R1 (164 bytes, three LF lines, no CR/BOM); the report contains the matching SHA and no template placeholders. The current formal migration directory still contains 13 SQL files, each byte-identical to the pinned baseline. The current generator, generated DDL, dictionary and design/ER document also remain byte-identical to their pinned baseline blobs. This is file verification only, not database or test execution, and it makes no assertion about unrelated concurrent Task 5 source edits.
