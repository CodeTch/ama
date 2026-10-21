# B03 V008 schema prerequisite — independent review

Date: 2026-10-22 (Asia/Shanghai). Exact owned BASE `62a8ec51092f449a25e5aff3222ad514e31a3384` → HEAD `ff22197f5f6231aeb689e035f4435ddbbcbcf114` (`feat: add meeting schema prerequisite`). Review is limited to this one commit/five paths/1,983 additions. The already-reviewed root dependency integration in original dispatch `df420cf..HEAD` is not reviewed again.

## Spec compliance

**Verdict: Spec compliant — scoped V008 schema-only prerequisite.**

- Read the full task brief and author report, then the entire 2,027-line review package once in three contiguous segments. Independently verified its diff payload equals `git diff BASE..HEAD` exactly (77,043 bytes; the package additionally contains its header/stat). Source HEAD is the requested commit, status is clean, and read-only `git diff --check` reports no whitespace errors.
- Read the adopted B00-1.2 database design's applicable identity/time/state/integrity/transaction/ACL/idempotency and R1 search contracts, all eight applicable dictionary sections, the migration register, and the adopted independent addendum review. V008's eight CREATE blocks and all 15 FK statements match the adopted `docs/database/ama-schema-draft.sql` blocks exactly. No future-domain FK, extra permanent table, migration-number guess, API, domain service, outbox shortcut or fabricated task ID is introduced. See `backend/adapters/src/main/resources/db/migration/V008__meeting_schema.sql:24` and `:148`.
- V008 rejects **any** nonempty legacy `meeting_acl` using a temporary-table enforced CHECK before the first permanent DDL. It does not inspect `confirmed_at` to manufacture provenance, delete legacy records, create grants or alter old SQL. Lower-set prerequisites guarantee the legacy table exists. Stop-writer requirements are explicit. See `V008__meeting_schema.sql:1` and `:14`.
- The metadata assertion resource is an exact eight-table subset of the previously committed, independently captured adopted `docs/acceptance/b/database-upload-addendum-metadata-evidence.json`, rather than an expectation generated from V008. Independently parsed every tables/columns/indexes/checks/FK row and compared canonical full objects among that adopted capture, the committed expectation, and `final-positive-raw-metadata.json`: all equal. Counts independently obtained: 8 tables, 71 columns, 24 index definitions/47 index-column rows, 12 enforced CHECKs, 15 FK constraints/16 FK-column rows. See `backend/integration-tests/src/test/java/ama/db/MeetingSchemaIT.java:194` and `:211`, and `backend/integration-tests/src/test/resources/ama/db/meeting-schema-b00-1.2.json:1`.
- The schema-only scope is stated accurately in `docs/acceptance/b/meeting-schema-prerequisite.md:3` and `:54`. Current pointers and same-meeting/confirmed ACTIVE membership remain adopted application checks; this delivery does not claim that existence FKs alone implement authorization or immutable business history.

## Strengths and code quality

- `MeetingSchemaIT.java:53` verifies the real canonical 001→002→005→006→007→008 upgrade, exact earlier history preservation, audit and existing identity/session rows, legacy column metadata, exact table additions, complete adopted metadata and repeat deployment with zero migrations. `:35` separately verifies all six frozen SQL resources by SHA256.
- Negative DML assertions use actual MySQL error codes for CHECK, uniqueness, orphan reference and RESTRICT failures (`MeetingSchemaIT.java:99`, `:122`, `:182`). The composite-FK case creates an existing second roster before inserting a member belonging only to the first, so it exercises the two-column relationship rather than merely an absent parent. Long Unicode names, NULL actual dates and binary-ID versus as_ci search behavior have concrete storage assertions (`:99`, `:142`). Draft roster persistence checks no implicit grant or person-search insertion while avoiding an API authorization claim (`:86`).
- `MeetingSchemaLegacyAclIT.java:14` requires an explicit draft/timestamped mode and a fresh zero-table fixture, uses no reset/repair, exercises `sql_mode=''`, and verifies source ACL rows, every old SHOW CREATE definition, old table set and earlier history survive. It asserts one failed V008 history row. The narrow logger filter is bound to the owner thread, logger/level/exact expected message and parameter/throwable shape, then removed on close (`:52`); unrelated failures are not swallowed.
- Production DDL is small and auditable; tests separate structure, storage constraints, upgrade compatibility and legacy refusal. Documentation distinguishes first-success evidence from later positive-fixture snapshots, and records the collector escaping diagnosis and genuine historical failures without presenting them as passing results.

## Issues

### Critical

None found in the reviewed schema prerequisite.

### Important

None found in the reviewed schema prerequisite.

### Minor

None identified that warrants a change to this delivery. In particular, the known historical `IdentityMigrationIT` latest=007 expectation is a separately assigned owner correction, not a new B03 schema finding or a passing regression claim.

## Independent evidence checks

Only read-only Git/source/file inspection and independent in-memory Python comparisons were executed. No application test, database command, container operation, fixture reset/repair, index/HEAD/branch mutation or child agent was used. The sole written file is this requested coordinator review report.

- Independently rehashed all 45 manifest entries under author `artifacts/phase-b/b03-schema/`: zero mismatches. The directory has those 45 files plus `SHA256SUMS.json` itself; no unlisted evidence file. All six retained SQL copies equal current source bytes. V001/V002/V005/V006/V007 hashes equal registered inputs; V008 is `fa44424aa1ff32e049929d45865fe7133c2548fc9ea451f3350cd8984c118ce3`. V003/V004 remain absent from the owned change and formal version history. Freeze commit `9572b5a` records that exact V008 hash before the preserved first successful application log. This corroborates the supplied chronology; file hashes do not independently observe every historical editor action.
- Parsed the actual JUnit XML attributes **and testcase elements**, rather than trusting `verified-counts.json`: final MeetingSchemaIT 6/0/0/0; draft refusal 1/0/0/0; timestamped refusal 1/0/0/0; Foundation explicitly selected normal methods 13/0/0/0; IdentityAccessIT 18/0/0/0; backend units 28/0/0/0 across five XMLs (9+3+6+8+2). All equal the saved count summary. The first-upgrade 1/0/0/0 is an overlapping execution of the canonical upgrade method, not an additional unique case. Earlier six-case execution is likewise not counted again.
- Parsed historical `red.xml`: 1 test, 1 failure, 0 errors, 0 skips; the exact failure is expected eight tables versus `[]`. Its log applies only 001/002/005/006. Parsed `initial-missing-dependency.xml`: 1 test, 0 failures, 1 error, 0 skips, specifically absent target007. The latter is correctly excluded from valid RED.
- Successful preserved logs contain BUILD SUCCESS with matching selected suite results and zero WARN/ERROR lines. First V008 success is logged at 01:22:17.007 after five lower migrations. Draft/timestamped refusal logs each reach attempted V008 and finish with their one asserted negative case; expected migration rejection is captured by the narrowly scoped test filter.
- Independently inspected complete snapshot histories: first-success and final-positive captures each contain 001/002/005/006/007/008 success=1 with expected checksums; final-positive has 21 tables. Draft and timestamped captures each have 13 tables, the same five successful lower versions, V008 checksum `-1747387036`/success=0, and their respective NULL/non-NULL ACL timestamp. The new eight tables are absent in both refusal snapshots. Initial negative-empty guards record zero tables in two distinct owned loopback fixtures. These are retained captures, not live DB observations by this reviewer.
- CHECK raw versus batch diagnosis records the precise additional escaping boundary. Full raw metadata now equals the independently adopted expectation; the retained original captures remain separately named. The owned diff changes neither the B00 capture nor the collector nor any frozen earlier migration.

## Declined to judge / scope limits

- Meeting create/edit/confirmation APIs, HTTP409/CAS, same-key different payload, rollback/retry, concurrent edits, immutable saved business revisions and replay reauthorization: explicitly deferred full B03 delivery after V009/V010; no implementation exists in this diff.
- Same-meeting/current-pointer integrity, CONFIRMED and ACTIVE grant eligibility, atomic root/subject/projection/audit/outbox writes: adopted domain-service obligations; single-column existence FKs intentionally do not establish these facts.
- Full B02 MeetingAccess/body/file authorization switch: separate later implementation/review; this migration does not enable it.
- Historical `IdentityMigrationIT` calls latest migration while expecting only007 and no meeting tables (`backend/integration-tests/src/test/java/ama/identity/IdentityMigrationIT.java:12`); parent has explicitly assigned an owner fix to target007. It was not run or counted here. This approval is not an all-integration-suite-green claim.
- Existing V006 reset-based refusal parameter cases: excluded from repetition by coordinator to preserve failed history; only 13 selected normal Foundation cases are claimed.
- Safe migration of actual populated legacy identity/ACL installations: blocked by the explicit preflight and requires a separately reviewed mapping/recovery plan. No production upgrade or automatic grant mapping is approved.
- Company SSO/directory/classification/purpose protocols, production rollout, real writer/worker stop discipline, performance/capacity/HA and external effects: unavailable or outside this schema checkpoint. Retained fixture evidence does not prove these properties or current live container state.

## Assessment

**Spec-compliance verdict: Compliant (schema prerequisite only).**

**Code-quality verdict: Approved. Ready for coordinator integration: Yes, within that scope.**

The exact adopted eight-table schema, fail-closed legacy guard, complete independent metadata comparisons and preserved real-MySQL evidence support this prerequisite. No Critical/Important/Minor fix is requested. Coordinator integration should record the actual application/review evidence without overwriting the historical freeze entries, retain the frozen V008 bytes, and keep full B03/B02 authorization and production business-write gates OPEN. The previously assigned IdentityMigrationIT owner correction remains separate.
