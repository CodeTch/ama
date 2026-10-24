# Task6 typed-input task index supplement

Status: ROOT PROPOSAL, independent design/exact-DDL review pending. No migration number, freeze, application ACK or schema change. This supplements Task6 CV02 within currentfixround1, not a restart of Tasks1–5. Root makes this choice under the user's explicit instruction to resolve uncertainties autonomously and record decisions; no human approval of this unseen document is claimed.

## Intent and binding evidence

Preserve the adopted complete typed-input task union plus actual RAW producer, existing task budget64, one genuine gate per S/F, complete indexed discovery, canonical task PK locking, no transfer/hash/archive work under roots. R1 task6 saved design lines89/92/117 forbids unindexed input scanning and requires full relevant declared facts. Root read focused feasibility report969316e1 and actual author metadata/EXPLAIN b184d243. Frozen13 SQL has PK/task_producer/task_ready/task_expired/task_subject_history, no typed-input index. The actual global OR uses PRIMARY traversal; LIMIT65 bounds returned rows, not work/locks. Provenance04 actual extra-task RED and05 GREEN establish missing membership sensitivity, not safe indexing.

## Approaches and root choice

1. Keep global OR/PK traversal: preserves membership but violates indexed bounded discovery and can touch other roots. Rejected.
2. Scan complete current subject through task_subject_history, then filter: index-accessible but omits foreign-subject same-input anomalies and charges unrelated historical tasks against64. Rejected as changed semantics and capacity.
3. Add one nonunique typed-input lookup index and use bounded indexed membership IDs plus actual producer PK, then exact sorted PK metadata locks. Selected by root. Cost: controlled one-time index build/storage and a new independently reviewed forward migration; no new product quota/authority/business relation.

## Exact unversioned SQL / ownership

Proposal file task-06-input-index-proposal-20261024.sql contains exactly one LF-terminated ASCII statement:

```sql
CREATE INDEX task_input_lookup ON durable_task (input_kind, input_id, id);
```

It is nonunique BTREE metadata, not a uniqueness/business invariant. No column/default/check/FK/collation/PK/row rewrite, backfill/seed/authority/sequence change. Existing producer uniqueness stays. Explicit id trailing key makes deterministic indexed ID order available. Do not add IF NOT EXISTS, edit old migrations, repair/reset/outOfOrder, or silently fall back when absent. Root checks actual full13history, engine/driver/Flyway/order/indexabsence before choosing formal compatible patch;013.2 remains only an unassigned candidate. Task8's earlier013.2 was never assigned/frozen. Whole formal bytes (including any chosen header) need exact review/freeze and separate selected-target ACK before copy/application. Prefer preserve this already-reviewed fullbody unchanged, with no header addition.

One sole Task6author owns subsequent source/tests/new SQL afterACK. Root owns scope/design review, version/freeze/ACK and preservation. Same retained MySQL/S3 and existing privateenv/PKI only; no newcontainer/database/credential. Author must quiesce owned API/Worker/test writers and end controlled transactions before the immediate old-data capture/apply window. Historical182 active task rows are data to preserve, not evidence of running processes and not cleanup targets.

## Indexed reader protocol

After fresh genuine current meeting/subject gate and original input/RAW declared parents, enumerate complete typed-input IDs with explicit FORCE INDEX(task_input_lookup), equality on input_kind='TRANSCRIPTION_INPUT' and input_id=?, ORDER BY id, bounded maxTasks+1 sentinel. No subject/policy/state filter may hide an unexpected incoming task. Keep discovery non-authoritative/nonlocking if required for rank; it grants no write permission. Reserve bounded UUID/row/copy storage before driver DTO growth;64 remains complete relevant task union budget, not all meeting history. Union actual producer UUID, sort/deduplicate UUIDs before task locks, reject overflow or mismatched complete set. Acquire exact admitted task PK rows in canonical order and require actual input/producer/spec/subject/success/typed RAW output predicates. Do not lock unrelated subject history or scan global PK. Repeat the membership proof under the same held gate for S/F before success; no late lower-rank expansion. Complete relevant set must equal the originally declared set; changes fail, never clip.

Only one original producer may survive the existing exact-one semantics. An extra cross-subject same-input ID must fail just like same-subject extra, without reading body content or granting foreign authority. Detect surplus IDs before acquiring unnecessary foreign task content/locks where possible. The actual producer PK still exposes missing/wrong-input/wrong-subject producer corruption; malformed provenance is not ordinary preparation failure.

Root subject/input parents and task_subject FK plus mandatory registered-writer protocol protect compliant task creation under the held root. This proposal does not invent a generic FK from typed input to input table, assume gap locking or permit unsupported direct maintenance races. Verify complete membership and canonical locks under default and READ COMMITTED; structural probes are isolated controlled corruption with exact restoration. If a legitimate writer can create same-input cross-subject tasks concurrently, source proof must resolve it before activation rather than hiding it with a subject filter.

## Preservation/application acceptance

Before allocation/ACK: actual selected resource owner/image/mount/limits/port proof, all13 immutableSQL hashes and fullhistory fields, resolved engine8.4.6/currentdriver/Flyway hash/order, no existing conflicting index. Preserve exact current schema/column/index/check/FK shapes and complete deterministic old table data (all actual columns/rows/NULL/bytes/UTC timestamps, per-table streaming hashes/rowcounts; private raw if needed, public nonsecret hashes). Quiesce only current owned writers, never mutate historical fixtures. Explicitly bound metadata-lock wait and observe actual DDL duration/result; no online/HA/capacity claim.

After first Flyway migrate: exactly one new successful history row and identical13-row prefix; actual index nonunique visible BTREE with explicit keyparts input_kind,input_id,id, no prefix/expression and ascending order; all oldmetadata and businessdata hashes unchanged. Normal migrate rerun executes0 with fullhistory/schema/data stable. No table reseed/allocator side effect. On failure preserveactualstate/evidence for forward-only ruling, neveredit/repair/clean.

Actual EXPLAIN for typed-input ID discovery uses task_input_lookup selectively without filesort/globalPRIMARY scan; exact task transfers usePRIMARY and sorted IDs. Planner row estimates are not measured scan/lock bounds. Instrument real SQL/parameters/connection phases to prove returned set, no unrelated task locks where required, extra same-input tasks including foreignsubject rejected, >64 sentinel refusal without transfer, >64 unrelatedsameSubject tasks do not change valid selectedinput admission, actual producer mismatch missing/foreign fails. Preserve all failures.

CV03/04 may progress independently; final covering/inheritedonce/finalunits run after schema/source fixed. Same-task scopedreview covers original T6SR01/02/CV01–04 plus introduced index/source breakage. Qualificationpins/source snapshots remain historical; new affected-source/schema proof required before currentrecipe claim. No futureTask10/11/correction/browser/company qualification.

## Downstream model ownership

Task8 is next immediately after6. Extend its already-owned current generated model window by exactly this durable_task nonunique index in generator/SQLdraft/dictionary/ER index representation where supported, in addition to its own external_operation column/index. No need modify the current model during this Task6fix; add explicit pending model synchronization record linked to Task8, not a false claim that draft is deployed baseline. Preserve exact four existing model/generator blobs before Task8 writes as previously required. Current generator/version may remain B00-1.5 until8; Task8's selected next B00-1.6-reconcile-scan remains usable with both index additions documented. Expected full-review model totals98tables/732cols/189explicitindexes if baseline187 is reconfirmed, with existingchecks/FKs unchanged. Root must amend narrow Task8ownership beforeactivation (durable_task index region plus external_operation regions); do not overwrite oldprefix or historical outputs. Formal Task8Flyway version is allocated from actualpost6history, not its old unassigned candidate.
