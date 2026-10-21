# B03 meeting schema prerequisite

Status: **schema implementation and author verification complete; ready for independent review**. This is the first B03 prerequisite delivery. Full B03 meeting domain/API implementation remains open until reviewed V009/V010 and its later implementation/review. No production business-write enablement, enterprise integration, performance or whole-stage qualification is claimed.

## Scope and immutable input

`V008__meeting_schema.sql` adds exactly meeting, meeting_info_revision, roster_revision, roster_member, meeting_access_grant, idempotency_record, meeting_search_projection and meeting_person_search from adopted B00-1.2. It adds no subject/outbox tables, API or domain service and makes no effective authorization switch.

The coordinator centrally froze V008 in `9572b5a`, independently verified SHA256 **`fa44424aa1ff32e049929d45865fe7133c2548fc9ea451f3350cd8984c118ce3`**, then explicitly authorized execution after adopted reviewed V007 arrived in `62a8ec51092f449a25e5aff3222ad514e31a3384`. Mechanical integration preserved the task drafts and RED logs. V008 bytes never changed after sharing or application. V001/V002/V005/V006/V007 hashes match the central register; V003/V004 remain empty.

The migration preserves all legacy tables, columns and rows. A connection-local CHECK preflight rejects **any** populated legacy meeting_acl before permanent DDL, including non-strict SQL mode. A timestamp is not confirmation provenance. No legacy ACL row becomes a grant; a separately reviewed mapping/recovery prerequisite is required for populated ACL. Failed nontransactional Flyway execution requires operator assessment, never automatic repair or checksum replacement. Current revision pointers retain adopted application-owned integrity; future-domain FKs are not installed.

## Real schema verification

The test metadata resource is an eight-table subset of the independently captured adopted B00-1.2 evidence, never generated from V008. Actual information_schema tables, columns, indexes, CHECKs and FKs exactly match: **8 tables / 71 columns / 24 index definitions / 47 index-column rows / 12 enforced CHECKs / 15 FK constraints / 16 FK-column rows**. This checks complete type/order/NULL/default/collation/extra, index uniqueness/order/prefix, CHECK text/enforcement and FK columns/targets/RESTRICT actions.

`MeetingSchemaIT` verifies canonical lower-set→V008 upgrade; unchanged earlier Flyway rows, audit, existing V007 identity/session rows and legacy columns; repeat deployment; schema-enforced revision/member uniqueness, confirmation/member-kind states, full Unicode names and unknown dates, real orphan and cross-roster composite-FK rejection, parent-delete restrictions, idempotency scope uniqueness, state vocabularies, exact stable identifiers and as_ci search behavior. Draft roster insertion creates neither effective grants nor search rows. It does not claim API-level confirmation/authorization behavior.

|Verification|Executed|Failures/errors/skips|
|---|---:|---|
|First formal lower-set→V008 upgrade|1|0/0/0|
|Final canonical MeetingSchemaIT|6|0/0/0|
|Isolated legacy draft ACL refusal|1|0/0/0|
|Isolated timestamped legacy ACL refusal|1|0/0/0|
|Foundation normal migration/transaction/audit paths|13|0/0/0|
|IdentityAccessIT with two API nodes/shared real MySQL|18|0/0/0|
|Complete backend unit suite|28|0/0/0|

Counts are from preserved fresh JUnit XML. The first upgrade overlaps the canonical suite and is not added as another unique test. No WARN/ERROR lines occurred in these successful final logs; the expected Flyway rejection logger is captured narrowly and its single emission asserted.

Actual positive fixture: `ama-test-b01-7b3ec2548012`, `127.0.0.1:45527`, pinned MySQL **8.4.6**. It was newly created/confirmed empty for this task. Before first V008 execution its known RED history was re-read as 001/002/005/006 success1 and source identity/session/ACL counts were all0; all six SQL SHA256s were rechecked. Formal upgrade ran 001→002→005→006→007→008. Current final positive snapshot contains21 tables and the same six successful versions. V008 Flyway checksum is **`-1747387036`**. Ordinary positive test resets touched only this known owned successful fixture.

Negative fixtures were independently created and confirmed at0 tables: draft `ama-test-b01-90171737c2cf` / `127.0.0.1:46377`, timestamped `ama-test-b01-79de6fc2da7a` / `127.0.0.1:45069`. Each applied the canonical lower set, inserted one controlled legacy ACL row, then rejected V008 at `chk_meeting_v008_legacy_acl_empty` before permanent DDL. All prior SHOW CREATE TABLE definitions, ACL rows and lower history matched afterward. Each remains at13 tables, 001/002/005/006/007 success1 and008 success0/checksum-1747387036. Neither negative fixture was reset, repaired or removed.

## Commands, evidence and limits

Use the pinned runtime `/home/tanren/ama/.worktrees/phase-a/.tools/env.sh`; credentials remain in ignored local fixture env files. Canonical commands from the task worktree:

```sh
python3 tools/checks/run.py it MeetingSchemaIT
# Separate NEW empty owned fixture and credentials for each refusal command:
AMA_TEST_LEGACY_ACL_MODE=draft python3 tools/checks/run.py it MeetingSchemaLegacyAclIT
AMA_TEST_LEGACY_ACL_MODE=timestamped python3 tools/checks/run.py it MeetingSchemaLegacyAclIT
python3 tools/checks/run.py it IdentityAccessIT
python3 tools/checks/run.py unit
```

The Foundation run used13 explicitly selected normal methods; its exact selector is retained in the coordinator report and JUnit XML. Its existing V006 refusal parameter cases reset failed history, so the coordinator explicitly excluded their repetition; this is not a claim that all16 Foundation cases reran. Existing IdentityMigrationIT targets latest while asserting only007/no meeting table, so it is not a valid V008 regression and is not counted. The original owner must later make that historical test explicitly target007 under separate review. No peer tests were modified.

Ignored runtime evidence is under this worktree's `artifacts/phase-b/b03-schema/`: first/final upgrade XML/logs, two isolated refusal XML/logs, regression/unit XML/logs, before-first history/hash checks, complete final raw metadata/history snapshots, SQL-byte copies, count summary and SHA manifest. Fixture credentials are excluded. Initial CLI metadata capture omitted `--raw` and doubled CHECK escaping; those original captures are retained, diagnosis reproduced the boundary, and subsequent raw captures match the adopted metadata exactly. No schema, assertion baseline or frozen SQL was changed to accommodate it.

Historical TDD evidence is retained separately: initial unavailable-V007 setup error (1 error, not valid RED), then before writing V008 an intended missing-eight-tables failure against frozen001→002→005→006 (1 failure /0 errors/0 skips). No placeholder/unreviewed V007 or review-only relabeling was used.

Server-side same-meeting/confirmed ACTIVE membership, immutable saved history, correct current pointers, projection/fence/outbox atomicity, replay reauthorization, HTTP conflicts and API concurrent edits remain later domain-service obligations. Old business writers/worker semantics must stay stopped until compatible reviewed domain prerequisites and all instances are deployed. There was no production/company connection, external effect, push, main-checkout modification or child agent.
