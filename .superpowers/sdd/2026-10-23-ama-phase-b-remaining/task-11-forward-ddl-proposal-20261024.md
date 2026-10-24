# Task 11 publication_submission forward DDL proposal — 2026-10-24

**Status: unversioned prospective SQL for root review.** The companion SQL is a proposed single-table addition to the committed Task 8 schema at `de1611a67ba483afe21f3b29bb69c2d32028efef`. It has not been allocated a formal migration name/version, copied into resources, frozen, independently reviewed, ACKed, applied, or synchronized to the current model. No source, test, database, Git, or product change is part of this preparation. The earlier 235-file source-alignment preflight is reused as context, not rerun; its older source baseline is not substituted for this commit.

## Exact logical-to-physical mapping

The logical authority is [R1 §8.5](task-11-final-publication-design-r1.md), lines 265–322. All 19 fields are included. No meeting ID, payload text, credential, remote outcome, invented operation, or publication-attempt ID is added.

| R1 logical field | Proposed physical column | Parent or meaning |
|---|---|---|
| task_id | `char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL`, primary key | `durable_task(id)`; one immutable acceptance per actual task |
| publication_id | same UUID type, NOT NULL | `publication(id)`; membership prefix of unique key |
| submission_no | `bigint unsigned NOT NULL` | Positive, ≤9223372036854775806, unique per publication |
| confirmation_id | same UUID type, NOT NULL | `final_confirmation(id)`; accepted final |
| provider_id | `varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | Exact configured provider code |
| business_key | `varchar(255) COLLATE utf8mb4_0900_bin NOT NULL` | Exact accepted publicationId:confirmationId; deliberately not unique in this table |
| library_id | `varchar(255) COLLATE utf8mb4_0900_bin NOT NULL` | Accepted destination library |
| folder_id | same binary-collated text type, NOT NULL | Accepted destination folder |
| requester_id | same binary-collated text type, NOT NULL | `enterprise_identity(enterprise_id)`; original authenticated owner |
| logical_request_hash | `char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | Canonical lowercase SHA256, validated by writer |
| execution_request_hash | same hash type, NOT NULL | Canonical lowercase SHA256, validated by writer |
| request_manifest | `longtext NOT NULL` | Bounded, content-free original acceptance manifest; SQL UTF-8 byte cap 65536 |
| created_at | `datetime(6) NOT NULL` | Accepted DB time |
| local_state | `varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL` | `UNDECIDED` or `NEVER_MARKED`; no default |
| local_decided_at | `datetime(6) NULL` | Atomic local decision time |
| local_reason | `varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL` | Exact R1 three-value allowlist when decided |
| local_decided_by | `varchar(255) COLLATE utf8mb4_0900_bin NULL` | `enterprise_identity(enterprise_id)`; fresh authorizing owner |
| local_attempt_no | `bigint unsigned NULL` | Actual last attempt; zero is valid |
| local_execution_id | same UUID type, NULL | `external_execution(id)`; null only when genuinely no execution |

The table uses InnoDB and the existing `utf8mb4_0900_ai_ci` table default. Each UUID and ASCII enum/hash column declares `ascii_bin`; destination, business-key, and enterprise-ID columns declare `utf8mb4_0900_bin`. V007 converted the enterprise identity PK to that collation before V013. `request_manifest` follows the existing `external_execution.request_manifest` LONGTEXT storage; exact bytes and schema are application-verified, never compared with collation-sensitive SQL equality.

Primary key `task_id` and unique `(publication_id,submission_no)` enforce one binding per real task and one number per publication. The latter is also the publication FK backing index. The bounded undecided scan uses `(local_state,created_at,task_id)`; separate explicit indexes back confirmation, requester, nullable decider, and nullable execution FKs. Six FKs use `ON UPDATE RESTRICT ON DELETE RESTRICT`. SQL CHECKs enforce the supported submission number, local-state vocabulary, 65536-byte manifest ceiling, and full nullable decision tuple including the exact three reasons. UTF-8 byte length bounds UTF-16 code units for valid UTF-8; the writer still checks the R1 byte/unit limits and strict content-free manifest schema.

## Deliberate limits and root decisions

* **Temporal immutability is not a CHECK property.** The later private writer must insert the entire binding once, never update `task_id` through `created_at`, and use one affected-row-count-1 `UNDECIDED → NEVER_MARKED` update filling all five decision fields. Exact sealed replay reads the original row without changing it. The SQL permits no decided row with incomplete fields, but by itself cannot prevent mutation, reversal, a different actor/reason/time, or a second decision. Root should decide whether to add DB triggers or rely on the reviewed confined writer and operation privileges; no trigger is invented here.
* **Cross-row truth needs the full locked protocol.** FKs cannot prove that task, publication, final, requester, execution, producer, r/e, original manifest, and meeting all belong to the same accepted submission. Nor can they prove no marker/send/UNKNOWN/other live round, genuine no-execution case A, genuine PREPARED B or local FAILED C, or prior-round safety. The R1 private issuer/holder, complete inventory, root/task marker barrier, actual attempt accounting, and atomic publication/audit/outbox transition remain mandatory.
* **The manifest is bounded metadata.** `OCTET_LENGTH` protects a persisted upper limit but does not enforce JSON shape, absence of content/secrets, canonical hashes, UUID syntax, or original acceptance equivalence. These are later producer/writer/replay checks. Existing rows without authentic binding are UNKNOWN/unavailable; no inferred backfill.
* **No old-row or history rewrite.** V013 mutable publication fields and operation-required publication_attempt remain as committed. The new table records the old accepted header even after current destination/final changes. Initial acceptance without a marker creates this row and task only; nullable `local_execution_id` is never populated with a fake execution. The old generic fixed-spec retry path stays intact.
* **Version/application decision is deferred.** This SQL deliberately has no migration basename, version, preflight, or Flyway history assertion. Root must recheck the actual prefix and Task 7/9/10 adopted source, allocate a version only after that review, freeze exact complete bytes, obtain independent static review and separate application ACK. The existing reserved 014–027 numbers and all historical migrations stay untouched. The current generator, dictionary, draft DDL, and ER appendix are future synchronized outputs, not edits in this preparation.

## Forward-compatibility and migration-preservation checklist

1. Pin the actual post-Task7/9/10 tree and all migration bytes; compare current physical parents/collations and any intervening migrations with this proposal. Do not assume a `013.4` slot.
2. Check every FK parent/index and MySQL CHECK/LOB behavior in an isolated schema after independently approved version allocation; assess partial DDL failure without repair, reset, out-of-order replay, or history rewrite.
3. Reconcile the shared lock rank: existing submission rows after final and before mapping/attempt; new row only after its real task and FK parents are held. Verify complete no-row/membership guards and S/A/F max+1/overflow checks.
4. Review the confined writer's immutable binding, single atomic local decision, exact replay, actor/session authority, and old task/attempt/execution association. Preserve accepted old bindings and decision rows across edits, reconfiguration, resubmissions, and restart.
5. Synchronize `tools/checks/database_design.py` and its current generated schema/dictionary plus ER appendix against the *then* actual schema; preserve historical generated exports and migration files.
6. Recheck source adoption gates: Task 7 marker/lease/attempt/terminal hook, Task 9 generated-input closure, Task 10 full PENDING/read budget, Task 8 reserved fairness behavior. This proposal is not runtime or Phase B qualification.

## Source pins

The companion manifest lists all **15** committed migration script paths, complete raw byte lengths/SHA256, and exact-byte focused line-range digests. Key selected ranges are V007:28–41 (identity collation); V008:113–126 (idempotency limit); V010:69–111, 115–133, 187–225 (task/attempt, marker, execution); V013:1–113 (publication/final/mapping and FKs); and V013_1/2/3 (actual post-Task8 prefix). R1 §8.5 and its closure/review/root ruling pins are recorded there too. The earlier source-alignment report describes the 235-file preflight at an older commit; it is cited only for observations and outstanding repin risks.
