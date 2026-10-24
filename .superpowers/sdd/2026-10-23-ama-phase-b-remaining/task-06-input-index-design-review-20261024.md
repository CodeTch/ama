# Task6 input-index supplement: independent design and exact-DDL review

Date: 2026-10-24. Reviewer: b06_input_index_design_review. Tree: `/home/tanren/ama/.worktrees/phase-b-completion`.

**Design SPEC: PASS. Quality: Approved. Exact unversioned DDL bytes: PASS. Findings: Critical 0 / Important 0 / Minor 0.**

This is the narrow CV02 prerequisite design/exact-body review, not a full Task6 source review, CV01-04 closure, runtime qualification, migration allocation/freeze, application ACK, or claim of human approval. SQL remains UNASSIGNED, unfrozen and unapplied by this review. No SQL copy/application is authorized by this report. Review IDs T6IDXDR01 onward are reserved for findings; none was identified, so no issue is manufactured to fill an ID.

## Exact reviewed proposals

| File in this report directory | Bytes | SHA256 |
|---|---:|---|
| task-06-input-index-supplement-design-20261024.md | 8822 | 12e6ee018b84ce6055e76f13181b830d59d3485af11f8c6a409abbb334ce735f |
| task-06-input-index-supplement-plan-20261024.md | 4815 | cf0ccc424e6c42ac466cb7a2fb5e566e26ab6220e442a98b0b8986474136a1d0 |
| task-06-input-index-proposal-20261024.sql | 75 | 11adae7d1d6b46bc7918692593abfbedf84c57a1d79dbe61f46a2c345d68878f |

All three hashes and sizes were verified before review and again immediately before report creation. The SQL is exactly the following ASCII statement plus one LF, without BOM, header or additional statement:

```sql
CREATE INDEX task_input_lookup ON durable_task (input_kind, input_id, id);
```

The focused feasibility note was read at SHA256 `969316e1f0b8f5ff07f4bf24fc8f61d441860b4f16e622eaae72b26093d35ea2`. Its supplied EXPLAIN account is evidence context, not a plan executed or independently verified in this review.

## Design and DDL assessment

1. **The index addresses the actual missing access path with minimal schema scope.** Design lines 11-13 and 29 retain the global typed-input set, including foreign-subject anomalies, without charging unrelated meeting history to task64. The 13 migration definitions contain no existing input-leading index. `backend/adapters/src/main/resources/db/migration/V010__reliable_execution.sql:72` defines ASCII/binary task IDs; lines 78-79 define nonnull ASCII/binary `input_kind` and `input_id`; lines 94-97 retain the producer/ready/expiry/subject indexes. The proposed full-width three-column key is compatible with these declared types. It is nonunique and introduces no additional business uniqueness, foreign-key relation, column or authority. The explicit ID suffix is acceptable even though InnoDB secondary indexes also carry the primary key: it makes the requested declared key shape and ID order explicit. No unnecessary schema redesign was found.

2. **The query shape is appropriate for bounded selective discovery.** Design line 29 and plan line 37 require a forced input index, equality on both typed-input components, ordered ID-only discovery, and the existing maxTasks+1 sentinel. A producer-PK lookup is kept separate from that predicate; the previous global OR/PRIMARY traversal is not retained. Sorting/deduplicating the union before exact-PK metadata locks preserves canonical task order. The producer still exposes a wrong/missing/foreign actual producer when it is absent from the indexed input set. Surplus same-input IDs fail rather than being filtered by subject, state or policy (design lines 29-31). Explicit accounting for ID/row/copy storage, the deduplicated producer union, and pre-transfer overflow is required by the proposal; these remain implementation obligations rather than established implementation facts.

3. **The compliant-writer serialization argument has concrete source support.** `backend/adapters/src/main/java/ama/task/TranscriptImportRuntime.java:33` installs the fixed production import registration; lines 48-53 delegate preparation/root locking/input validation to its domain policy. `backend/adapters/src/main/java/ama/task/JdbcTaskQueue.java:63` takes the subject fence and policy roots before task mutation; lines 79-90 validate input and producer identity before insertion. `backend/adapters/src/main/java/ama/transcript/ImportTaskDomainPolicy.java:89` acquires the prepared meeting root; line 102 retains the actual input; line 149 explicitly requires `transcription_input.meeting_id = spec.subject.id`. Its adopted creation path checks the authority-created transition (lines 30-32, 84-86); `backend/adapters/src/main/java/ama/transcript/JdbcProcessingIntake.java:106` through its enqueue at line 124 acquires the genuine meeting/subject and creates/retains the input before enqueue. Thus the inspected registered import path cannot legitimately attach the same input to another meeting while bypassing its real owner root. `backend/adapters/src/main/java/ama/task/JdbcSubjectFences.java:28` implements the actual fence as a retained PK `FOR UPDATE` read. The subject FK alone is not sufficient to prove global input ownership; the proposal correctly relies on the registered protocol as well.

4. **Default-isolation freshness is feasible without assumed gap locks.** Focused gate/transaction reads were made specifically to assess the proposed nonlocking ID lookup. `backend/adapters/src/main/java/ama/transcript/JdbcSavedTranscripts.java:37` starts S acquisition with the gate; read F does the same at line 168. Mutation F at line 55 claims its receipt before the gate, but `JdbcSavedTranscriptReceipts.java:32` reads that receipt with `FOR UPDATE`. `JdbcMeetingAccess.java:63`, line 90 and line 128 acquire identities, subject and meeting with locking reads; the ordinary identity reread at line 112 occurs only after the roots are held. On these inspected entry paths, moving the ID-only lookup into the held gate does not require using a read view established before that gate. The implementation must preserve this ordering: the independent outside-root discovery must not become an earlier consistent read in the same S/F transaction. With the compliant membership writers serialized by that root, a post-gate consistent view can safely enumerate membership under default isolation; READ COMMITTED must also retain that root through recheck and success. Repeating an old snapshot alone would not protect membership. The design's fresh gate, same-held-gate recheck and both-isolation evidence requirements are material conditions of this approval.

5. **Unexpected foreign rows are detected, not legitimized or silently omitted.** The proposal reads their IDs as part of the global typed-input membership and rejects surplus before unnecessary foreign content/locks where possible (design lines 29-33). Neither the task-subject FK nor this nonunique index prevents unsupported direct SQL from creating cross-subject references. Design line 33 explicitly excludes unsupported maintenance races and requires resolving any newly found legitimate cross-subject writer before activation. That is a proper support boundary, not a false promise of gap locking. Existing corruption must still be detected in quiescent structural probes; adding a subject filter would violate this approved design.

6. **Preservation and migration controls are adequate for this additive body.** Design lines 23, 25 and 37-39 require actual resource/history/index-absence checks, quiescence of owned live writers, all 13 immutable migration hashes and history-prefix preservation, complete deterministic old-data hashes, physical metadata comparison, bounded metadata-lock wait, one successful new history row, then normal migrate rerun with zero executions. Failed DDL or history recording is not treated as proof of rollback: state and evidence are retained for a forward-only ruling, without repair/reset/old-file edits. The plain CREATE INDEX is intentionally not directly idempotent; Flyway's successful history controls the normal rerun. The proposal does not claim a successful partially recorded migration can be blindly retried, online capacity, or absence of transient physical index-build work.

7. **Source/model ownership and proof scope are explicit.** Plan lines 31-42 keep a sole Task6 author and require focused source/behavior review after the schema change. The present global-OR candidate is not represented as already repaired or requalified. Design line 47 assigns pending model synchronization to immediate Task8, alongside its own column/index change, preserves the existing blobs, and requires the narrow ownership amendment before activation. Its model counts are conditional on reconfirming baseline counts. No generated draft is misrepresented as deployed baseline, and the old Task8 migration candidate is not treated as allocated. This is a proportionate split of work, provided that existing activation gate is enforced.

## Findings

Critical: none. Important: none. Minor: none.

Approval is for the reviewed design and exact 75-byte unversioned body. Any changed body/header requires exact-byte review/freeze; any widened writer model, weakened membership rule, changed task budget, or pre-gate transaction read-view arrangement requires reconsideration of the affected reasoning above.

## Cannot Verify

- Actual MySQL version/engine/index absence, resolved JDBC/Flyway hashes, target resource ownership/limits, actual full migration history, formal version ordering, or old-data/physical-schema preservation. No database, Docker, runtime, Java, tests, private environment, credentials, PKI or network was accessed.
- Actual new query plans, driver buffering/reservations, measured scans or locks, genuine writer blocking, missing-index failure, restoration of temporary corruption rows, first apply, zero-work rerun, failure handling, or either isolation-level test. The proposal's acceptance steps must supply these facts; estimates and this source reasoning do not substitute for them.
- Full application wiring outside the named production import installation, every possible future registered policy, or arbitrary direct maintenance. The positive writer argument is limited to the inspected current production import protocol. New legitimate same-input writers must obey the owner root/input contract before activation.
- Final Task6 source correctness, CV03/04, final qualification/adoption, or completed Task8 model synchronization. These were deliberately outside this narrow review.

## Custody

Only this new report was written. Proposal, product, migration, index, HEAD and historical artifacts were not modified. No subagent was created. The focused source reads were limited to the index schema, bounded metadata/candidate task query, registered import/queue writer ownership, and gate/transaction entry points needed to resolve the specific nonlocking-read freshness risk.

### Observed source-definition hashes

These identify the files read; they do not claim a broader source snapshot or current runtime deployment.

- `backend/adapters/src/main/java/ama/transcript/SavedTranscriptRows.java`: `cf43ec766aeb9e86766106c88870e4ce08a56f595d9e3c5269e76309f2374877`
- `artifacts/phase-b/b-remaining/task-06/fix-01/qualification-task-union/SavedTranscriptGraph.java`: `3edd7124d86f0d41a12ce556e596ad2de26ef984c8ff036515d331527090e3ef`
- `backend/adapters/src/main/java/ama/transcript/ImportTaskDomainPolicy.java`: `f1f75c031c0129ee0f3b9d9e5d737d4dca5c7cf0d72e359befa409ab20bab342`
- `backend/adapters/src/main/java/ama/transcript/JdbcProcessingIntake.java`: `b5cf7dd3eb1941f2152ceaa8bd4c217369f3cdddf06250d98c87bfed3c15aa61`
- `backend/adapters/src/main/java/ama/task/JdbcTaskQueue.java`: `05d9965f0d448d37295680288aeb46c79d3908fc943edb6955c10d0fec94bc64`
- `backend/adapters/src/main/java/ama/task/JdbcSubjectFences.java`: `4622efcf0e6d76bf69e385fc442b4098fb6fe6f222b017349b92267008bb40dc`
- `backend/adapters/src/main/java/ama/task/TranscriptImportRuntime.java`: `5b9ffc7b767c01cdd51c1434318b8eb375fbf2e37a81b96107e8569eb714d39b`
- `backend/adapters/src/main/java/ama/transcript/JdbcSavedTranscripts.java`: `720a77843bf23829d5ff3da8aae9fbe2ee52855b9eb61fc962475d1024e8f994`
- `backend/adapters/src/main/java/ama/transcript/JdbcSavedTranscriptReceipts.java`: `64d03eccbe8c1394a1726473facd649ba9127bd05f767e7e3b0f3a0519514dba`
- `backend/adapters/src/main/java/ama/transcript/JdbcTranscriptAuthority.java`: `1b2a8518543e99aa01e8e50a70a7de87956e03eade69a991252fa87dc11c5941`
- `backend/adapters/src/main/java/ama/meeting/JdbcMeetingAccess.java`: `75917561fcd0e91cdf097997189a3c28d7998bbaf28db62c16a8162f6bdb64ec`
- `backend/adapters/src/main/java/ama/storage/JdbcFileRegistry.java`: `4131cb9f8b65228788f9eabf5237b0faf61fa917f3d80c697ee3618ed512dd0f`
- `backend/adapters/src/main/java/ama/db/TransactionRunner.java`: `63c0a3600d1529fdafca64d7413dc511b8b3ea9a2311d98ecce68a386def846b`
- `backend/adapters/src/main/resources/db/migration/V001__foundation.sql`: `1726d9db3eb45d0fe86314dd41ad52d5f2332e3f16995cc246f3c3c532208335`
- `backend/adapters/src/main/resources/db/migration/V002__identity_acl.sql`: `68b0d478504a554344cb12dfa8ef79c62da5c6e3455317791762fca25e70f7d0`
- `backend/adapters/src/main/resources/db/migration/V005__tasks_outbox.sql`: `55cfdc4aabf169c82f30303ac4d03dce7f24b3c6af5b4cc3ebd0089ae7e7dda7`
- `backend/adapters/src/main/resources/db/migration/V006__audit_metadata.sql`: `f36b7ab2e8d32cf5de6eadcd86524ab269dce98f7906b808eff1a0e39325178f`
- `backend/adapters/src/main/resources/db/migration/V007__identity_sessions.sql`: `859970b83fc5623b18340c2de793ea35429cc3c0da5cbd1365b4763a5a64780d`
- `backend/adapters/src/main/resources/db/migration/V008__meeting_schema.sql`: `fa44424aa1ff32e049929d45865fe7133c2548fc9ea451f3350cd8984c118ce3`
- `backend/adapters/src/main/resources/db/migration/V009__file_schema.sql`: `79f048987282c82fd7a56d697a02e88c93fa45b3349bd4d43e840dca23ba437d`
- `backend/adapters/src/main/resources/db/migration/V010__reliable_execution.sql`: `03e89cc8bd58d5a6aa0d4ab031b8fc05a4f7d2450623df147cd84d05b68977db`
- `backend/adapters/src/main/resources/db/migration/V011_1__transcript_material_index.sql`: `9efc688e49ce4afc52f383929b91d70970905f04b4e7836bdb0533eac50a9713`
- `backend/adapters/src/main/resources/db/migration/V011__transcript_schema.sql`: `a926c8b5d9806082d248f38bc7c7dd2bdbc46dd319d34b787e02f86132096ccb`
- `backend/adapters/src/main/resources/db/migration/V012__minutes_evidence.sql`: `10742ca2075fa3430dedf692853e1c106bd4464eaed46d8c8c1aa07859878fb7`
- `backend/adapters/src/main/resources/db/migration/V013_1__b_runtime_metadata.sql`: `9280baea017014f3322af901373a7f8d69dfaa396fdd49f3c5d872535c43a520`
- `backend/adapters/src/main/resources/db/migration/V013__publications.sql`: `1de37066869617723a11f05d7423584143a64e0c9ddf8879c7b4fd107de08e3c`
