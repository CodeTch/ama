# B04 six-table schema preparation checkpoint

Historical checkpoint at 45715b6; retained to distinguish compilation-only preparation from the later [real schema prerequisite](file-schema-prerequisite.md).

Status: PREPARATION ONLY / NOT VALIDATED / dependency gate pending. Full B04 remains OPEN.

Worktree: /home/tanren/ama/.worktrees/phase-b-file-schema
Branch: codex/phase-b-file-schema
BASE: c1d2e28c274ea11baaeea24421217df8b247da71

This checkout currently contains reviewed formal V001/V002/V005/V006/V007 only. No unreviewed V008 was imported, inspected as implementation input, or executed. No V009 SQL was written/shared/applied; no candidate SHA or formal freeze ACK exists. No database fixture was started, connected, reset, repaired, or removed during preparation.

Prepared FileSchemaMigrationIT has eight real-MySQL test methods, all UNEXECUTED:
1. Exact six-table metadata against the independently reviewed B00-1.2 actual MySQL capture subset: engine/collation, ordered column type/null/default/extra/collation, complete indexes, ENFORCED CHECK clauses, all FK targets/actions.
2. Explicit 001/002/005/006/007/008/009 successful ordered history, unchanged lower history/DDL/data, rerun zero, frozen 001/002/005/006/007 resource hashes (008/009 hashes still to be added after dependency/freeze gates).
3. Invalid storage type/status, file type/state, upload state and cleanup state rejection with MySQL3819.
4. All nine FK columns reject unknown parents with MySQL1452.
5. Referenced backend/meeting/file/upload deletion and backend rename reject with MySQL1451.
6. Physical target uniqueness and case sensitivity independently exercised in file/upload/cleanup ledgers.
7. Part zero, duplicate part and duplicate reference rejection; distinct purpose reference allowed.
8. Long Unicode/name controls, purpose, internal session token and optional provider version round-trip plus required name/purpose NULL rejection.

The prepared fixture setup never calls MysqlTestDatabase.reset. It requires this worktree's helper env path, inspects the real Docker label/one loopback binding, matches the explicit JDBC URL to that binding, confirms empty first-use schema, and accepts only exact successful lower/full known history on reuse. It upgrades to explicit target008 before capturing lower state; -Dama.file.schema.target=008 selects RED, default009 selects the later frozen GREEN. Tests seed unmistakably test-only rows; per-test file rows roll back. Test-only lower identity/meeting/audit rows remain as preservation evidence.

Independent expectation resource is a filtered copy of docs/acceptance/b/database-upload-addendum-metadata-evidence.json, retaining source SHA and MySQL version. It is not generated from a candidate V009. Counts: six tables,53 columns,36 index columns,7 CHECKs,nine FK columns. Exact CHECK text intentionally compares same pinned MySQL8.4 capture; a runtime formatting mismatch must be investigated, not silently dropped.

Verification executed: pinned JDK21/Maven reactor test-compile with -DskipTests, exit0. No JUnit test executed and no RED/GREEN claim is made. Compile log: artifacts/phase-b/b04-schema-preparation/test-compile.log. Source/resource/document diff --check is clean; the preserved raw Maven log contains its original trailing spaces after [INFO]. Existing five formal SQL SHA values match frozen registry. Java/storage/native/S3/API implementation was not changed.

Resume only after root supplies exact independently reviewed V008 commit:
- Import only approved V008 SQL and required reviewed test metadata, retaining this task's scope. Add its exact SHA assertion.
- Start NEW helper-owned loopback fixture in this worktree and confirm empty/ownership before first migration.
- Run canonical metadata method against reviewed target008 with -Dama.file.schema.target=008. Preserve log/XML and actual successful lower history; expect assertion reporting zero of six tables. Compilation/startup/configuration error is not RED.
- Only after that real RED implement exact six-table V009, no other tables/versions or production seeds.
- Send root exact final V009 SHA; await explicit freeze ACK before first formal execution. Once shared/applied, bytes immutable.
- Run full prepared suite at explicit target009; investigate tests against actual MySQL, add reviewed hash assertions, preserve fresh history/metadata snapshots, all failed attempts and fixture history.
- Run relevant local/storage/Foundation NORMAL path and Java unit regressions. Do not run latest-stage IdentityMigrationIT or Foundation destructive negative parameter cases on the positive fixture; do not count unselected tests.
- Produce scoped schema implementation record and full BASE..HEAD package for root's independent spec/quality reviews. Schema checkpoint unlocks V010 structurally only; registry/API/ACL/orphan/lifecycle/concurrency/backend qualification remain later B04 work.

No tests are reported as passing. No whole-task or schema acceptance is claimed.
