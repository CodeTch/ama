# Task 8 independent exact-DDL review

Date: 2026-10-23. STATIC REVIEW ONLY, before allocation or activation. Product baseline: 35b2325aee8e6c09aa1bdebc880c4f09bd37837b. Worktree: /home/tanren/ama/.worktrees/phase-b-completion. W denotes .superpowers/sdd/2026-10-23-ama-phase-b-remaining.

## Verdict

- SPEC: PASS for the chosen additive schema and preparation gates against the adopted fairness wrapper at 1834ebb8 and its exact R1. The ordinal decision and algorithm were not reopened.
- Exact-DDL quality: PASS at static design review. No actionable SQL or preparation defect found. The reviewed statement has the required signed allocation column and one suitable unique index, with no contradictory PK/FK/CHECK/default or durable_task alteration.
- Physical execution, implementation, and fairness closure: NOT QUALIFIED. This verdict neither allocates a version nor freezes/ACKs formal bytes, selects resources, authorizes application, or closes FUP-B05-CANDIDATE-01.

Tasks 5 and 6 must finish before any Task 8 formal application. Root must supply the later activation baseline and independently resolve the selected engine/target/history, ordered version, full formal bytes, freeze, and separate exact-byte selected-target ACK.

## Exact identities and inspection

The prepared Task 8 brief was read first. The full preparation and the complete three-line SQL were read. The adopted wrapper and the R1 schema section were then compared without reopening the ordinal design. Baseline V010, relevant generator/model seams, and the existing marker INSERT/named mapper were inspected through read-only git show at the baseline; concurrent work was not adopted as a new source baseline.

| Input | Bytes | SHA256 |
|---|---:|---|
| W/task-08-forward-ddl-proposal.sql | 164 | 023fe875efd2be0bbd9d59b2d22a9b6429ea88eb6fc7a7b30d8541a53c0d46ee |
| W/task-08-forward-ddl-preparation.md | 30951 | ca66652163e281c298d80daa345e854a4cb1b85e2fa272473006ce0e42b67279 |
| backend/adapters/src/main/resources/db/migration/V010__reliable_execution.sql | 35136 | 03e89cc8bd58d5a6aa0d4ab031b8fc05a4f7d2450623df147cd84d05b68977db |
| tools/checks/database_design.py | 108070 | 91493cbb251c99c800bd2aec7861596d50ac76acbf9a159d14c59aaf5b71e6a0 |
| docs/database/ama-schema-draft.sql | 116623 | d3094336b4d55d9389b2fd1d852d627b93e0ac0394b9b1801e258a154b0cb445 |
| docs/database/ama-data-dictionary.md | 167091 | cd6a909abbcd4d20afaf00a3b98ce6329cc633b31e5e941166f16ddbd3177c74 |
| docs/superpowers/specs/2026-10-21-ama-database-design.md | 117367 | 05bfe0fdf1f2c05af67598ef8298c19a1ec138f51948026f44b9ee41c9793f04 |
| backend/adapters/src/main/java/ama/task/JdbcExternalOperations.java | 19114 | 63e84274395c74c8d5e5375fcfc898c5cc39f19447318a2093eebaff1496ad19 |

The proposal is byte-identical to the SQL in adopted exact R1 (R1 SHA256 4eefdd7da3abf58f13d0fe7e15fbe07c1740fa035f08bb5e60b0a708b9a08284): three LF-terminated lines, no CR or BOM, exactly one statement. No migration identity/header is included. SHA256 is a file digest, not a Flyway checksum.

Independent read-only byte comparison found the six pinned source/model inputs in the table unchanged in the current tree. All 13 current formal migration SQL files equal their baseline blobs; none of the baseline V011 through V013.1 SQL files ALTERs external_operation or durable_task. This is repository evidence, not an assertion of current database state.

## Findings and schema assessment

No actionable findings. The following concrete anchors explain the PASS and delimit what was reviewed:

| Anchor | Assessment |
|---|---|
| W/task-08-forward-ddl-proposal.sql:1 | Targets external_operation only. It adds no task authority, sidecar, trigger, FK, CHECK, session directive, seed, or semantic UPDATE/backfill. |
| W/task-08-forward-ddl-proposal.sql:2; V010__reliable_execution.sql:113 | Appends signed BIGINT NOT NULL AUTO_INCREMENT to the existing 15-column table, with no explicit default or existing allocation-column conflict. The expected new ordinal is 16; old types, defaults, collations and ordinals are untouched. Java long covers its positive domain through 9223372036854775807. |
| W/task-08-forward-ddl-proposal.sql:3; V010__reliable_execution.sql:129 | The single-column unique index supplies the allocation-leading index and total numeric seek order. Existing id PK, external_key and external_due remain. Including the existing FK support index gives five operation indexes and nine declared STATISTICS key-part rows after the delta. Actual visibility/BTREE/covering access still needs metadata and EXPLAIN. |
| V010__reliable_execution.sql:132; V010__reliable_execution.sql:215; V010__reliable_execution.sql:223 | The two original operation CHECKs, composite subject FK, and child operation-ID reference have no changed columns or definitions. The new numeric index cannot replace the subject-kind/subject-ID support prefix. No new relationship or parent identity is introduced. |
| V010__reliable_execution.sql:75; W/task-08-forward-ddl-preparation.md:83 | durable_task remains at its existing 22-column definition, indexes, three CHECKs and composite subject FK. No ordinary queue selector/index change belongs to this DDL. |
| JdbcExternalOperations.java:24; JdbcExternalOperations.java:39 | The inspected marker writer omits the new column using an explicit INSERT list, while its old-field mapper reads column names. The additive column therefore fits that pinned writer shape; later behavioral omission/dedup/retry/renew/observe preservation is still required. |

Primary MySQL 8.4 documentation confirms that an InnoDB allocation column can lead a secondary index; this unique sole-column key meets the documented requirement. Allocation does not establish commit order, and rollback gaps are permitted. See [AUTO_INCREMENT handling](https://dev.mysql.com/doc/refman/8.4/en/innodb-auto-increment-handling.html).

Omitting a positive-domain CHECK is correct: MySQL excludes AUTO_INCREMENT columns from CHECK expressions. Uniqueness and NOT NULL are database properties; positivity and immutability additionally depend on controlled writers/imports and validation. The preparation explicitly recognizes negative/zero injection and NULL allocation semantics rather than claiming complete database enforcement. See [CHECK constraints](https://dev.mysql.com/doc/refman/8.4/en/create-table-check-constraints.html).

## Current-model and oracle implications

W/task-08-forward-ddl-preparation.md:127-137 accurately identifies the required later ownership. tools/checks/database_design.py:30 makes num() unsigned; the ordinal must instead be a signed bigint column. Lines 457 and 627 currently special-case only audit_event.id for SQL AUTO_INCREMENT and expected EXTRA metadata. Both seams must represent the new attribute consistently while retaining audit_event behavior. Lines 473 and 479 also require the documented no-explicit-default/server-allocation exception so the dictionary does not misstate required application assignment. These are recognized later implementation gates, not current preparation defects.

The final operation column/index model and policy must be updated once, with the generated DDL, dictionary and ER appendix synchronized. No new ER relation or PK is justified. Independent text counting of the pinned generated DDL confirms 98 tables, 731 columns, 187 explicit indexes including PKs, 116 CHECKs and 208 FKs. This exact delta gives 98/732/188/116/208; these are review-model counts, excluding implicit FK indexes, not deployed-schema counts. Preserve the exact preceding generated outputs and all immutable SQL/history artifacts before later authorized regeneration. No generator or verifier was run.

## Gating concerns, not actual defects

The preparation's gates are adequate for a later controlled physical qualification, provided root enforces them as conditions rather than treating this static PASS as their evidence:

1. **Engine and rebuild:** W/task-08-forward-ddl-preparation.md:151,155,181,186 require actual selected image/version, SQL modes/allocation settings, target identity, metadata before/after, rebuild cost/disk/time and lock-wait assessment with writers stopped or quiesced. MySQL documents substantial reorganization, no concurrent DML, and at least shared locking for this operation. Omitting ALGORITHM/LOCK makes no online/instant promise; actual pinned-engine success must precede activation. See [online DDL column operations](https://dev.mysql.com/doc/refman/8.4/en/innodb-online-ddl-operations.html).
2. **Complete old-data preservation:** Lines 141 and 187 require every existing operation to receive a positive unique ordinal, with unspecified legacy enumeration order, while every old field/relation and all other table data compare exactly in a controlled immediate window. State/due/policy classes are covered. Full deterministic snapshots and row counts, including NULL/bytes/microseconds, cannot be replaced by sampling or later business-mutated snapshots.
3. **Constraint and compatibility evidence:** Lines 185-189 retain a genuine pre-forward negative schema assertion, exact physical metadata, old CHECK/unique/FK negatives with valid relational setup, explicit-column insertion, and stable ID-to-ordinal mapping across retry/dedup/claim/renew/observe. Missing schema/index fails visibly. Preserve the historical target-010 B05 oracle on a separately guarded target-010 fixture.
4. **Restores and exhaustion:** Lines 145-149 and 190 require full-row positivity/uniqueness verification before activation and after restore, exact surviving ID/ordinal mapping, allocation above restored MAX under actual settings, signed-long boundary handling, gaps/restart, and isolated actual exhaustion with unchanged old rows/no authority. No cursor endpoint arithmetic or shared-counter poisoning is proposed. MySQL's documentation includes both an out-of-type-range caveat and an exhaustion-error note; actual pinned-engine SQLState/error and failure atomicity must be captured, not inferred solely from the manual. See [AUTO_INCREMENT handling](https://dev.mysql.com/doc/refman/8.4/en/innodb-auto-increment-handling.html).
5. **Flyway and custody:** Lines 175-177 and 191 require actual ordered 13-row prefix identity, compatible root allocation with the resolved Flyway, immutable full formal bytes and separate ACK, initial one-migration success, full prefix/data preservation, then zero-execution normal rerun. The raw ALTER is intentionally not repeatable. Unknown/drifted/failed history blocks application; no edit/repair/reset/outOfOrder route is allowed.
6. **Source and fairness evidence:** Lines 192-195 and 203 leave query plans, bounded accounting, all-state scan coverage, adverse held/unregistered cases, late commits, cursor/error/recovery and regressions plus independent implementation/evidence adoption outstanding. This review does not substitute for those implementation gates or Task 7's later activation.

## Review limits

Only this review file was authored. No SQL execution, database/Docker command, environment/private-resource inspection, resource creation, test, generator invocation, formal version/header/freeze/ACK, source/model/old-plan/artifact change, staging/index/HEAD change, or subagent dispatch occurred. The review made only scoped read-only repository inspections and primary documentation reads. Any later formal byte change needs root's full-byte review/freeze and separate ACK; this unversioned static result grants no activation authority.
