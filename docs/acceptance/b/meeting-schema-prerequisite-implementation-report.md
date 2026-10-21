# B03 schema prerequisite — implementation delivery

Status: **AUTHOR IMPLEMENTATION / SELF-VERIFICATION COMPLETE; READY FOR INDEPENDENT REVIEW**. This closes only the requested first V008 schema delivery from the implementer. The root owns independent review and integration. Full B03 meeting domain/API and full B02 object authorization remain OPEN. No business-write enablement, company integration, performance, HA or production qualification is claimed.

- Date:2026-10-22, Asia/Shanghai.
- Sole worktree `/home/tanren/ama/.worktrees/phase-b-meeting-schema`, branch `codex/phase-b-meeting-schema`.
- Original dispatch BASE `df420cf240bf0dcb8087047758936a9b00d3a420`.
- Reviewed root dependency mechanically fast-forwarded to `62a8ec51092f449a25e5aff3222ad514e31a3384`, preserving all drafts and RED evidence; its existing34 paths/peer work are not this owned implementation diff. Reviewed V007 source39b447d, root integrationbec9393 and adoption62a8ec5 consumed without byte changes.
- Exact owned review range **`62a8ec51092f449a25e5aff3222ad514e31a3384..ff22197f5f6231aeb689e035f4435ddbbcbcf114`**; original BASE..HEAD additionally contains the already-reviewed root dependency integration and freeze records.
- Exact implementation commit **`ff22197f5f6231aeb689e035f4435ddbbcbcf114`**, `feat: add meeting schema prerequisite`.
-5 owned files,1983 insertions; post-commit status empty. No main checkout changes, push, external company calls or child agents.

## Owned files and scope

1. `backend/adapters/src/main/resources/db/migration/V008__meeting_schema.sql`: eight adopted permanent tables,15 FK constraints/16 FK-column rows and preflight rejection of any populated legacy ACL before permanent DDL.
2. `backend/integration-tests/src/test/java/ama/db/MeetingSchemaIT.java`:6 positive/frozen-byte schema tests.
3. `backend/integration-tests/src/test/java/ama/db/MeetingSchemaLegacyAclIT.java`:one-shot independent negative fixture harness; explicit draft/timestamped modes, no database reset, rejects a nonempty starting schema, retains failed history.
4. `backend/integration-tests/src/test/resources/ama/db/meeting-schema-b00-1.2.json`:eight-table subset of adopted independently captured B00-1.2 metadata, not generated from V008.
5. `docs/acceptance/b/meeting-schema-prerequisite.md`:scoped implementation/evidence/compatibility/remaining-gate record.

No API/contracts/domain implementation, fake task ID, subject/outbox write, new migration number, design regeneration or peer test edit. V008 CREATE blocks and all FK statements were independently compared byte-for-byte to the eight adopted B00 blocks. The only added preface is the coordinated legacy-ACL stop guard and deployment/freeze comments. Current pointers retain application-owned logical integrity; no future-domain FK was added.

## Immutable chronology and exact SQL hashes

Root9572b5a centrally froze and independently verified V008 **before any application**. Original preparation paused for V007; no placeholder/unreviewed migration or sandbox execution happened. After root supplied independently reviewed V007 and explicitly lifted the gate, all six actual SQL file hashes and the known owned RED fixture history were re-read before first execution. V008 bytes remained unchanged after sharing, application and commit.

|Version|Actual SHA256|Flyway checksum|
|---|---|---:|
|001|1726d9db3eb45d0fe86314dd41ad52d5f2332e3f16995cc246f3c3c532208335|1201435595|
|002|68b0d478504a554344cb12dfa8ef79c62da5c6e3455317791762fca25e70f7d0|1757300424|
|005|55cfdc4aabf169c82f30303ac4d03dce7f24b3c6af5b4cc3ebd0089ae7e7dda7|-1999355348|
|006|f36b7ab2e8d32cf5de6eadcd86524ab269dce98f7906b808eff1a0e39325178f|-1744360556|
|007|859970b83fc5623b18340c2de793ea35429cc3c0da5cbd1365b4763a5a64780d|157746768|
|008|fa44424aa1ff32e049929d45865fe7133c2548fc9ea451f3350cd8984c118ce3|-1747387036|

V003/V004 remain absent. The B01 first-applied-byte deviation was read and retained as a historical process issue, not repeated or relabeled. No checksum repair, migration overwrite, outOfOrder, old/unknown/other fixture reset or failed-fixture cleanup occurred.

## Actual schema and compatibility evidence

Target tables: meeting, meeting_info_revision, roster_revision, roster_member, meeting_access_grant, idempotency_record, meeting_search_projection, meeting_person_search. Actual full metadata equals the adopted independent subset: **8 tables /71 columns /24 index definitions /47 index-column rows /12 ENFORCED CHECKs /15 FK constraints /16 FK-column rows**.

The canonical upgrade test checks exact001→002→005→006→007→008 order and success, unchanged original Flyway rows/checksums, audit IDs/fields/time, existing V007 identity/session values and every original column, then repeat deployment0. Metadata comparison covers all types/precision/order/NULL/default/extra/collations; index uniqueness/column order/no truncation; CHECK clauses/enforcement; complete FK columns/targets/RESTRICT rules. Real MySQL negative DML exercises unique revision/member keys, invalid state/confirmation/member-kind combinations, orphan parents, an existing second roster with a member belonging only to the first (composite FK), parent deletion restrictions and idempotency uniqueness. Full Unicode names survive and unknown dates remainNULL. Exact stable-ID comparisons remain separate from case-insensitive/accent-sensitive search matching. Draft roster rows create neither grants nor search rows, without claiming API confirmation behavior.

Permanent legacy tables and rows are retained. Any legacy ACL row, including confirmed_at=NULL or a non-NULL timestamp, rejects at `chk_meeting_v008_legacy_acl_empty` before permanentDDL even with sql_mode=''. No timestamps are treated as provenance and no automatic grant is created. All old table SHOW CREATE definitions, ACL rows and lower history match across rejection. A populated real installation requires a separate reviewed mapping/recovery plan; this delivery does not invent one.

## Fixture custody and preserved state

All three containers were created by this worktree helper, independently confirmed empty before use, guarded as own b01-labeled loopback fixtures, pinned MySQL8.4.6 image. Credentials remain only in ignored local `.tools/mysql-fixture/.../env.sh`, never in report/log/manifest.

|Role|Owned container|Loopback port|Retained state|
|---|---|---:|---|
|Positive and regressions|ama-test-b01-7b3ec2548012|45527|21 tables;001/002/005/006/007/008 success1|
|Draft legacy ACL refusal|ama-test-b01-90171737c2cf|46377|13 tables;001/002/005/006/007 success1,008 success0;ACL confirmed_atNULL|
|Timestamped legacy ACL refusal|ama-test-b01-79de6fc2da7a|45069|13 tables;001/002/005/006/007 success1,008 success0;ACL confirmed_at non-NULL|

Before first formal V008 application,45527 history was the known RED001/002/005/006 allsuccess1, identity/session/ACL each0rows, matching root's explicit execution allowance. Its original audit probe/history snapshot was saved before canonical test reset. Only that owned successful-migration positive fixture was reset during ordinary positive/regression tests. Each negative used its own new0-table fixture once, is still preserved failed, and was subsequently only read for evidence. No fixture has been removed.

First formal exact lower→V008 test completed2026-10-22 01:22:17 Asia/Shanghai,1/0/0/0. First-success history/meta saved before later positive resets. Current final raw snapshot after final schema run again confirms the six-version successful history and V008 checksum-1747387036. First snapshot is not relabeled as current state.

## Exact verification and fresh JUnit counts

Pinned runtime: `source /home/tanren/ama/.worktrees/phase-a/.tools/env.sh`. All shell work launched from Windows-local cwd using explicit WSL --cd/--exec. Commands below run in the assigned task worktree; source only the corresponding owned fixture credentials for each IT.

|Command / evidence stem|Tests|Failures|Errors|Skipped|
|---|---:|---:|---:|---:|
|`python3 tools/checks/run.py it 'MeetingSchemaIT#lowerSetUpgradeAddsExactlyEightAdoptedTablesAndPreservesLegacyRowsAndHistory'` /first-v008-upgrade|1|0|0|0|
|`python3 tools/checks/run.py it MeetingSchemaIT` /final-meeting-schema-it|6|0|0|0|
|`AMA_TEST_LEGACY_ACL_MODE=draft python3 tools/checks/run.py it MeetingSchemaLegacyAclIT` /legacy-draft-refusal|1|0|0|0|
|`AMA_TEST_LEGACY_ACL_MODE=timestamped python3 tools/checks/run.py it MeetingSchemaLegacyAclIT` /legacy-timestamped-refusal|1|0|0|0|
|Explicit Foundation normal-path selector below /foundation-positive-it|13|0|0|0|
|`python3 tools/checks/run.py it IdentityAccessIT` /identity-access-it|18|0|0|0|
|`python3 tools/checks/run.py unit` /backend-unit|28|0|0|0|

Counts come from fresh preserved XML, independently parsed/asserted in `verified-counts.json`. First-upgrade1 overlaps canonical6 and is not another unique test. Complete backend unit accounting is28, from5 fresh suite XMLs. No WARN/ERROR lines in the final successful logs. Expected Flyway refusal logs are captured narrowly on the owning test thread, with exactly one emission asserted.

Foundation exact command:

```sh
python3 tools/checks/run.py it 'FoundationIT#migrationAppliesOnce+consecutiveResetsRemoveTablesFromLaterMigrations+independentMigratorsWaitForDatabaseLockThenApplyOnce+rollbackDoesNotLeaveSuccessAudit+connectionLossRollsBackAndFreshJdbcRecovers+checkedExceptionRollsBackBusinessAndAudit+auditCannotAutocommitWithoutCallerTransaction+nestedRequiredCannotCommitAfterInnerFailureIsCaught+successfulTransactionStoresUtf8mb4AndUtcMetadata+frozenBaselineUpgradesWithoutChangingRowsOrHistory+auditIdentifiersMatchCaseAndTrailingSpacesExactly+auditNanosecondsTruncateToUtcMicrosecondsRegardlessOfJvmTimezone+auditJoinsExternalSpringTransactionAndRollsBackWithIt'
```

The coordinator explicitly agreed to exclude repetition of the existing V006 refusal parameter cases because their reset-based harness destroys failed history. This report claims13 normal cases, not all16 Foundation cases. Original IdentityMigrationIT hard-codes latest=007/no meeting table, so it is obsolete for V008 and was not run/count as passing. Root will assign its original owner a separately reviewed explicit-target007 correction; no peer test was relaxed here. No unrelated web, company contract, production or full B03 API suite is claimed.

Final six schema tests were rerun after self-review strengthened existing identity/session preservation and cross-roster composite-FK coverage. Full backend units then reran with no further source changes. Staged diff-check passed and V008 SHA was rechecked immediately before the scoped commit.

## Evidence files and failure history

All raw local evidence is retained at `/home/tanren/ama/.worktrees/phase-b-meeting-schema/artifacts/phase-b/b03-schema/`, outside Git by established artifacts policy. No secrets or fixture credential files were copied.

- `before-first-v008.json`:ownership/loopback/history/source counts/all six SHA checks before first execution.
- `first-v008-upgrade.log/.xml`, `meeting-schema-it.log/.xml`, `final-meeting-schema-it.log/.xml`.
- `legacy-draft-refusal.log/.xml`, `legacy-timestamped-refusal.log/.xml`; `negative-empty-guards.json`.
- `foundation-positive-it.log/.xml`, `identity-access-it.log/.xml`, `backend-unit.log`, `backend-unit-xml/`.
- `final-positive-raw-metadata.json`, `positive-raw-metadata.json`, both `legacy-*-refusal-raw-metadata.json`:actual complete information_schema arrays, current history/ACL rows and SQL hashes.
- `actual-metadata-summary.json`, `verified-counts.json`, `sql/` exact six migration byte copies, `SHA256SUMS.json`.
- `preparation-checkpoint-report.md`:original waiting checkpoint retained, superseded by this delivery.

Historical failures are not concealed:

1. Initial requested007 before dependency supply:1 error/0failures/0skips, `No migration with a target version007 could be found`; `initial-missing-dependency.log/.xml`. Not valid RED.
2. Genuine pre-implementation RED against frozen001→002→005→006:1failure/0errors/0skips, expected eight-table list versus actual[]; `red.log/.xml`. Only afterward V008 was written.
3. Initial shell quoting attempt failed before SQL command body (`sh: -c: option requires an argument`); corrected with literal PowerShell here-string. Later fixture-creation shell ended with a trailing CR-only command returning1 after both fixtures had successfully been created; both were independently verified0-table before use. No migration/data failure is attributed to either shell issue.
4. Initial evidence collector lacked `mysql --raw`; batch escaping doubled CHECK-clause backslashes and a secondary Python summary comparison failed on checks. Reproduced batch-versus-raw on a read-only CHECK query, confirmed B00 collector uses--raw, fixed only the ignored collector, and exact raw metadata comparison passed. Original escaped captures and `capture-escaping-diagnosis.json` remain. SQL and the independent assertion resource never changed for this issue; JDBC schema tests remained passing.

No V008 permanentDDL failure occurred in the positive fixture. The two negative migration failures are intentional guard refusals, remain preserved and require no repair for this test delivery.

## Independent handoff and remaining scope

Review the exact owned62a8ec5..ff22197 diff, with originaldf420cf..HEAD mechanical root integration identifiable. Review request is schema-only: frozen migration, tests and scoped record. All owning implementation assertions above have actual evidence; independent sign-off is pending, not signed by the implementer.

Meeting business create/edit/confirmation must later create/advance subject fences and transactionally append/flush outbox after reviewedV009/V010; no dummy task IDs or omitted required events were used. Server-side same-meeting ACTIVE confirmed membership, current-pointer integrity, immutable history, projection/fence/outbox atomicity, replay reauthorization, HTTP409/CAS and concurrent API behavior remain full B03 obligations. MeetingAccess remains B02's separate later implementation gate. Actual company SSO/directory/protocol, purpose/classification dictionaries, platform and operational qualification remain pending. Keep legacy business writers and incompatible workers stopped; no authorization/body access switch is enabled by this schema delivery.
