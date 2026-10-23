# Runtime metadata and new-meeting creation — Task 2

2026-10-23. Source parent `a3b2933ef83035e858d2d3336decba4617f22a1f`; permanent SQL freeze `64908b1aef4c28f0806846128fcb44005f1b75e1`; separate exact ACK `15a124c607d4682b59993f4ff0945bf6dcbef57a`. This record covers only the two reviewed runtime sidecars and the narrow atomic creator insert. The full execution report is [task-02-report.md](../../../artifacts/phase-b/b-remaining/task-02/task-02-report.md).

## Current design and historical boundaries

`tools/checks/database_design.py` now declares **B00-1.5-runtime-metadata**, an actual generated inventory of **98 tables / 731 columns**. Its parent B00-1.4-proposed inventory is 96 / 727. Every prior model record, policy, CREATE and FK statement is identical in both comparison directions. The parent model's D01 three proposed tables and two nullable columns remain UNASSIGNED-D01; this work neither assigns nor installs their migration. Protected historical design files and all twelve original deployed migration scripts remain unchanged.

The current dictionary, review-only DDL and complete ER are generated consistently from this source. The physical evidence in this record targets **two** new tables and preserves the old deployed schema; it is not a fresh physical validation of the complete 98-table future model. Prior validation remains qualified by its original version and scope.

## Exact adopted sidecar contract

- `input_batch_fingerprint_profile`: `batch_id CHAR(36) ascii_bin NOT NULL` primary/FK to input_batch(id), and `profile VARCHAR(64) ascii_bin NOT NULL`, without defaults. Its enforced CHECK requires byte length 18 and exact `ama.input-batch.v1`. Missing sidecar remains legacy/unknown even when a stored digest matches a v1 recomputation; no backfill, inference or default is installed.
- `meeting_minutes_editor_guard`: `meeting_id CHAR(36) ascii_bin NOT NULL` primary/FK to meeting(id), and signed `intent_epoch BIGINT NOT NULL`, without defaults. Its enforced bound is 0 through 9223372036854775806. Only genuine new meeting creation initializes zero in its original transaction. Existing absence remains unknown; reads and creation replay never initialize it.
- Both tables use InnoDB / utf8mb4_0900_ai_ci. Both physical FKs are RESTRICT for delete and update. Their primary indexes already support the FK; no extra parent index is created. No old table, canonical FullRow, generation fingerprint, hash profile or archive format changes.

Positive epochs are sticky suppression of automatic first save; they are not edit authority, leases, close/timeout state or a resettable flag. No editor-intent API, auto-save, intake writer or retention behavior is activated here. Those future writers must enforce authentic profile issuance, monotonic increment/exhaustion and full current authority in their owning transactions.

## Frozen migration and physical evidence

`V013_1__b_runtime_metadata.sql` is exactly **1817 bytes**, SHA-256 `9280baea017014f3322af901373a7f8d69dfaa396fdd49f3c5d872535c43a520`, with exactly two CREATEs and two FK ALTERs. Root allocated, fully read, froze and separately acknowledged these bytes before formal placement and first apply. Twelve historical SQL hashes and successful ordered history checksums are preserved. Reserved integers 014..027 and next new integer 028 are unaffected.

The only target is this run's owned `ama-test-b01-ad9f1af29eff`, full ID `eefdd9a984ee00fe6ca886168b29a6ce4f3b405467b8d3204240d4e1a8dc7502`, 127.0.0.1:58062 / ama_b01_fixture. Exact MySQL 8.4.6 image SHA, selected label, 768MiB / 1 CPU / 256 PIDs and native AIO=0 are guarded. The new private environment path is `.tools/phase-b-remaining/env.sh`; credentials are absent from evidence.

The first actual JUnit schema assertion failed for the genuine absence of both required tables: one assertion failure, zero errors and skips. Both missing tables also returned MySQL 1146. Complete before/after snapshots were identical across 62 existing user tables / 1094 rows, SHOW CREATE, all five metadata groups and the twelve-row history. A separate actual missing-table creator run passed its whole-create rollback assertion before migration.

An explicitly identified metadata-only negative fixture adds nine new synthetic rows, each with fresh UUIDs. Adopted production BatchFingerprint computed the digest from its exact member/file facts, and independently recomputed the existing pinned literal golden. This creates no authentic upload or worker qualification. All original 1094 rows remain exact; the immediate migration snapshot contains 1103 rows. First apply added exactly one successful history row, checksum **-1898004857**, retaining its twelve-row prefix and every prior row/SHOW CREATE/metadata record. Both sidecars were empty immediately afterward.

RuntimeMetadataSchemaIT compares actual and expected metadata in both directions: exact 2 tables / 4 columns / 2 PK columns / 2 enforced CHECKs / 2 FK columns, including type, signedness, ordinal, null/default, collation, engine, index detail and referential actions. The first postapply oracle exposed MySQL's canonical rendering of OCTET_LENGTH as LENGTH. The original failure is retained. Server ASCII/supplementary probes prove byte lengths 1/4 and character lengths 1/1. A restricted CHECK AST accepts only this byte-function synonym and preserves literal case and AND/OR grouping; CHAR_LENGTH and changed literals/numbers fail. Frozen SQL and live DDL were never edited to address the representation difference.

Thirty physical constraint probes passed: four positives (epochs 0, 1, max; exact v1) and twenty-six expected rejections (bounds/overflow, duplicate PK, orphan, NULL, omitted required fields, case/unknown/empty/non-ASCII/whitespace profiles, and update/delete RESTRICT). Every probe used a separate rollback transaction; complete snapshots remained equal.

## Creator change and limits

The only application change is the required guard-zero INSERT in JdbcMeetingRepository.create's genuinely new branch, after its parent meeting insert and before info/history/projection work. It shares the existing subject/meeting/history/idempotency/audit/outbox transaction and final flush. The replay branch and reads are unchanged. A missing table fails the actual creation transaction; there is no DDL, fallback, upsert or lazy initialization.

Focused tests cover committed zero, positive-epoch replay preservation, actual legacy replay/read absence, replay reauthorization, and rollback after guard insertion at info, audit, flush and an actual owned JDBC connection abort before commit. Postapply missing-schema fault injection is explicitly labeled separately from the genuine preapply missing-table proof. The abort probe closes only the bound test transaction connection before the commit is sent, then the actual JDBC commit fails. Server rollback visibility and bounded cleanup are checked; this does not resolve uncertain commits or already committed response loss. An initial test-only callback setup was correctly rejected by the existing outbox synchronization policy; that failed receipt is retained and no production policy was weakened.

Migration-window equality ends before positive business tests, whose new audit/outbox events legitimately advance the shared outbox sequence and audit auto-increment state. Final reports distinguish those authorized test writes from migration preservation. No reset, DROP DATABASE/table, restart, repair, outOfOrder, old environment access or old artifact rewrite occurred. SQL remains permanently immutable after freeze; MySQL DDL partial failure would require preservation and a root forward ruling, not a rollback claim.

This is local schema/transaction evidence only. Company runtime, full future model, external services, capacity/HA, UI/editor intent, automatic saving and Phase B completion are outside this acceptance scope.
