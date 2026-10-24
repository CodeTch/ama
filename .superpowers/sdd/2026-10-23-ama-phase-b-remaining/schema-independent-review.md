# Independent Task 2 schema design and candidate DDL review

2026-10-23. Reviewer: b_runtime_schema_review, independently delegated by root. Review scope is the unassigned schema DESIGN and DDL only, before formal numbering, freeze, separate ACK or apply. Task 1 implementation changes are excluded.

**SPEC: PASS. SQL/design quality: APPROVED. Actionable findings: 0 (P0/P1/P2/P3: 0/0/0/0).**

This verdict approves the reviewed proposal as a design/SQL candidate. It allocates no migration number, grants no execution authority, and certifies no database execution, runtime implementation, test success, company integration or Phase B completion. Root must still fully read the final SQL, independently resolve the version/history gate, register/freeze complete final bytes and SHA in a commit, and separately ACK the exact SHA and named new fixture before first apply.

## Exact reviewed inputs and evidence boundary

The complete schema-design.md, UNASSIGNED-runtime-metadata.sql and design-r1-candidate/controlled-runtime.md were read. Independently recomputed SHA-256 values match the task's pinned inputs:

| Input | SHA-256 |
|---|---|
| schema-design.md | 731d4277a9ac23fb1f1a02aba35230404bfc750254caa7b10c2e0fc1449f2d7f |
| UNASSIGNED-runtime-metadata.sql | 1563f2806fcd9e6a804870a54f803852b722cfa29716613d6767af3f0cb51810 |
| design-r1-candidate/controlled-runtime.md | 0f57b2d7441ac3c7342be0df96739c33b1024f74738ee0fb74cba052d62de7d5 |

Declared source baseline is 34cb226aefe99c67d3681af68d748b0aeea4e073 and documentation baseline bd47aa0e1b24fa89fee5385860454f43dc0aecd7. Supporting review reads covered the original approved minutes UX decisions, relevant architecture requirements, migration-register ownership/order/freeze rules, dictionary conventions, actual complete V008/V011 SQL, relevant actual V012 generation/candidate/revision definitions, SchemaMigrator, JdbcMeetingAccess, meeting creation/replay/write checks in JdbcMeetingRepository, and GenerationFingerprint's actual fixed projection. Read-only SHA comparison confirms that all twelve migration files currently in the formal location match protected-baseline.json. No V014 or later SQL is present there. That file inventory is not evidence of any live target's Flyway history.

Only native PowerShell and rg read operations were used before writing this report. No DB, Docker, tests, private environment, network, Git mutation, source modification or subagent was used. This report is the only review-authored file.

## Independent findings and reasoning

### 1. Exact sidecar DDL: PASS

UNASSIGNED-runtime-metadata.sql:11-34 has exactly two CREATE TABLE statements and two ALTERs adding FKs on those new tables. There are four columns, two one-column primary indexes, two CHECKs and two one-column physical FKs. There is no extra index, old-table ALTER, seed, inference/backfill, trigger, IF NOT EXISTS or generation snapshot column. Both PKs also provide the corresponding child FK support index; both referenced parent IDs already have primary indexes.

The batch_id and meeting_id definitions exactly match the relevant actual parent UUID types: CHAR(36) CHARACTER SET ascii COLLATE ascii_bin (V011__transcript_schema.sql:14-23; V008__meeting_schema.sql meeting definition). InnoDB/table utf8mb4_0900_ai_ci and explicit RESTRICT/RESTRICT agree with ama-data-dictionary.md:5-7. Constraint names are distinct and within the identifier limit. CREATE-before-FK ordering has existing parents available. Static inspection finds no MySQL 8.4 syntax or CHECK-function restriction issue. Actual parser/enforcement proof is still a future acknowledged-fixture requirement, not a result of this review.

### 2. Exact batch profile and history: PASS

SQL:13-16 combines ASCII storage, ascii_bin equality and OCTET_LENGTH(profile)=18. The literal ama.input-batch.v1 contains exactly 18 ASCII bytes. Case changes fail binary collation equality. PAD SPACE equality alone could accept trailing spaces, but the retained VARCHAR byte-length check rejects that variant; leading/interior whitespace and other spellings fail equality. NOT NULL closes CHECK's nullable-expression escape. Strict SQL mode and application rejection of conversion/truncation remain explicitly required by schema-design.md:29-33 and :115; the column constraint is not presented as proof of original client bytes.

Schema-design.md:35-43 requires authentic server-selected v1 issuance in the same creating transaction as batch/members/input/file handoff/task/audit. Missing sidecar is unsupported even if the stored digest happens to match a recomputation. No history/date/kind/hash-width heuristic, retroactive attachment, lazy fill or migration backfill is allowed. The FK only guarantees a parent exists; the design correctly assigns authentic creation, same-meeting/source membership and immutable profile ownership to application capabilities. Diagnostic/history reads retain authorization and never rewrite old facts. Physical deletion is limited to coordinated parent cleanup after retained provenance is resolved.

### 3. Durable editor protocol and zero-only proof: PASS

Schema-design.md:11-15, :47-73 and :75-98 implement the frozen controlled-runtime.md:12, :33-35 ruling. A genuinely new meeting alone receives persisted zero, atomically with its original subject/meeting/projections/idempotency/audit/outbox creation. A historical absent row is UNKNOWN; only actual authorized intent may insert one. Reads, replay of old creation, generation and schema-unavailable paths cannot manufacture zero. Intent increases and never resets, expires, decrements or disappears during retained meeting lifetime.

Signed BIGINT plus the inclusive bound 0..9223372036854775806 matches Java long while leaving an arithmetic margin. SQL conditional increment requires the previous exact epoch and a value below the bound; one affected row is mandatory. At exhaustion the command rejects and cannot acknowledge/enable editing. Canonical decimal-string wire delivery avoids JavaScript precision loss without imposing a 2^53 storage cap.

The shared meeting root, rather than a nonexistent guard-row/gap lock, serializes intent and final first-save decisions. A final locked zero implies no prior committed positive intent because the only entry into zero is new creation and no return transition exists. Therefore an additional per-generation persisted editor epoch is unnecessary for this deliberately stronger zero-only predicate. Positive-equals-positive never qualifies. Unknown or positive at enqueue cannot later qualify through this state machine. This proof also depends on disabling incompatible writers at cutover, preserving UUID nonreuse, and retaining the guard through logical deletion; these requirements are explicit.

The UI waits for a committed intent ACK before typing, IME, paste, restored drafts or other editable changes. Auto-save winning the root race forces current-body/version reconciliation before enabling a stale editor. Intent winning makes the worker retain a candidate. Rollback before ACK keeps the editor disabled; lost committed ACK leaves durable positive suppression. No branch overwrites a typed draft based only on task success.

### 4. Genuine authority, replay and unchanged business fences: PASS

Schema-design.md:49 explicitly requires complete active identity -> subject -> meeting -> guard locks, the genuine issuer/DataSource/actual holder, owner EDIT, matching subject/root business and cancel fences, ACTIVE lifecycle/governance, ever_published=0 and non-PUBLISHED stage. The distinction is necessary: JdbcMeetingAccess.java:92-101 checks current identity/lifecycle/governance/ownership but does not itself reject published owner writes; JdbcMeetingRepository.java:154 adds that writer check. The proposal expressly closes this potential omission for the new command instead of assuming require(EDIT) is sufficient. No network/file/archive/hash work occurs under these locks.

Intent persists guard transition and semantic idempotency success with audit/outbox and final flush in one transaction. It changes neither meeting.revision nor subject.business_version/cancel_epoch, effective content, generation debt, pointers or review credit. Replay reauthorizes and returns the current committed positive projection without another increment; it does not pretend that result_id=meetingId stores the original epoch. A changed semantic body conflicts; a stale expected revision conflicts while preserving the draft. Existing mutation replay behavior is expressly left intact. V008 idempotency_record can represent the proposed operation without an extra epoch/snapshot table.

Generation adoption still checks original business/input/cancel fences, fresh authority, complete valid output and no effective body in the same atomic completion. Sticky intent alone does not change businessVersion and therefore yields a current-fence candidate. Explicit adoption may proceed with a positive guard only under fresh owner CAS/current eligibility and the exact original provenance. The proposal does not weaken ordinary B05 completion or authorize business-stale effects; the separately reviewed Task 7 retention policy remains required. No schema addition to the existing generation FullRow, canonical manifest or archive chain is proposed.

### 5. Migration gate, existing history and oracle: PASS

Schema-design.md:100-125 preserves all twelve frozen SQL files, V014..V027 owners and next new integer V028. This follows migration-register.md:23 and its root-only stage-patch ruling at :163-175. The conditional 013.1 route requires fresh proof using the actual pinned/resolved Flyway implementation, strict 13 < 13.1 < 14 ordering, complete successful twelve-script target prefix and no applied integer 14 or higher. This review neither supplies that proof nor grants the number. Unknown, failed, divergent or higher-version target history must stop; no repair, outOfOrder, reset or rewriting old SQL is permitted.

The proposed oracle is materially stronger than count-only verification: schema-design.md:114 compares both set directions for complete table/column/index-column/CHECK/FK-column details, collation/default/nullability/order, signed type, CHECK enforcement and referential actions. :115 covers valid and invalid profile/epoch/parent values. :116 requires legacy matching-hash and unknown-guard rows to remain absent through reads/restart. :117 preserves all prior user rows, SHOW CREATE and five metadata groups, checks parent indexes for unwanted changes, and requires the identical twelve-row history prefix plus exactly one acknowledged new successful row. :118 requires private-target/latest zero-apply reruns. :119-123 cover real transactional failure, races, rollback, replay, authority/lifecycle, hash compatibility and browser precision obligations. These are future evidence requirements, not tests executed here.

The design separates non-transactional multi-statement DDL failure from DML rollback (:125). A partial CREATE/FK failure must preserve schema/history and stop for centrally reviewed forward recovery; no test failure authorizes changing frozen SQL or weakening the oracle.

### 6. UTF-8/UTC convention assessment: no required change

The candidate contains no SET NAMES utf8mb4 or SET time_zone='+00:00'. SchemaMigrator.java:24-29 already applies both through Flyway initSql; actual V012 also omits a script-local SET preamble. All candidate string syntax/literals are ASCII, all relevant column/table character sets are explicit, and neither new table has a temporal column. Therefore this omission is not an actual DDL semantic defect or a migration-convention blocker for the documented executor. The actual acknowledged fixture must still record its session/server SQL mode and UTC/native timestamps as the design requires. If root chooses to add a preamble before freezing, those resulting bytes need their own final complete read/SHA and ACK; this report does not authorize silently substituting different SQL.

## Remaining gates

There are no requested design/DDL corrections. Root's final whole-SQL read, explicit dictionary/design adoption, pinned Flyway ordering/history proof, central registration and committed byte/SHA freeze, separate exact-SHA ACK, new-fixture physical oracle/constraint/history verification, and subsequent service/UI/retention reviews remain outstanding. No inference of apply permission follows from APPROVED.
## Coordinator clarification received during review

Root clarified that Task 2 should implement the narrow new-meeting create guard-zero insert after acknowledged schema apply, leaving other editor-intent operations to Task 10. This is coherent with schema-design.md:73 and the atomic creation requirement already reviewed; it does not introduce new schema or product behavior. The implementation must insert zero only in the genuinely new create branch, share the full original transaction and final flush, roll back all creation rows on guard/audit/flush/commit failure, and never fill a missing guard on creation replay or historical read. Missing table fails unavailable and cannot trigger DDL/default-zero handling. These are future implementation/test review obligations.

Root also reported a planned dictionary/generator/ER refresh containing the two sidecars while preserving the current proposed model's existing definitions and leaving separately proposed D01 tables/columns outside formal applied SQL. That packaging is consistent in principle; these forthcoming output bytes were not reviewed here and must not be covered by this verdict. Existing historical schema copies, deployed old-table definitions and SQL remain frozen.

Root reported an offline actual Flyway 12.4 version/ResourceNameParser ordering proof for 013 < 013.1 < 014 and the candidate filename. This reviewer did not execute or inspect that probe, so it remains separate root evidence. Target-history proof, allocation, final-byte freeze and separate ACK remain distinct gates. Any final renamed/commented/preamble-adjusted candidate must be fully read and hashed as those exact final bytes before ACK.