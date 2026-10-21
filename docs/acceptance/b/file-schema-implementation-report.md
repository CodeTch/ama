# B04 V009 schema prerequisite — implementation report

Date: 2026-10-22, Asia/Shanghai. Status: **implementation and scoped verification ready for root's independent spec/quality review**. No independent implementation acceptance is claimed here; **full B04 remains OPEN**.

## Exact baseline and owned scope

- Worktree `/home/tanren/ama/.worktrees/phase-b-file-schema`, branch `codex/phase-b-file-schema`.
- Original dispatch BASE `c1d2e28c274ea11baaeea24421217df8b247da71`; unreviewed preparation checkpoint `45715b6d458088e5b3ec14ab8afb08f96412449c` remains fully included.
- Exact reviewed root `2a569ebb411049363da96184b0507e6365f5f9fd` mechanically merged at `dcc5302eb44a5c065b539b6a4ea5f7534a907787`. **Final full-owned review range is `2a569eb..final HEAD`**, never the merge HEAD. Final source commit supplied in coordinator report.
- Owned production change is exactly `backend/adapters/src/main/resources/db/migration/V009__file_schema.sql`; test Java/resource, scoped records and raw evidence are owned supporting paths. Existing Java production, native/local/S3 implementations, API, ports, migrations001/002/005/006/007/008 and B00 source/metadata are unchanged.

Read the exact schema brief/actual dispatch, original B04 brief/storage preflight, adopted B00-1.2 design/dictionary/register and independent design/addendum reviews, V008 adoption and independent implementation review, existing local-infrastructure checkpoint and storage contracts/port/router. Reviewed V008 only entered after root authorized exact2a569eb; no unreviewed peer source was consumed.

## Migration and freeze chronology

V009 is **7,239 bytes**; SHA256 **`79f048987282c82fd7a56d697a02e88c93fa45b3349bd4d43e840dca23ba437d`**; actual Flyway checksum **`-287948759`**. Six CREATE blocks and nine FK blocks are byte-for-byte the adopted B00-1.2 subset. All earlier hashes match frozen registry. No IF NOT EXISTS, runtime seed/configuration/credentials, old script rewrite, extra domain table, guessed version, outOfOrder or repair.

New helper-owned positive fixture `ama-test-b01-204cc11baabc` / `127.0.0.1:45041` / MySQL8.4.6 was confirmed zero-table before lower migrations. Actual reviewed lower set001→002→005→006→007→008 succeeded. Before V009 existed, canonical metadata method at explicit target008 failed for exactly missing six tables: raw XML **1test/1failure/0error/0skip**.

Root independently read exact V009 bytes, verified SHA and all six CREATE/nine FK blocks, recorded freeze at `b3c00ceebffdd7a15fd0ba3249a091c29f445c2b`, and explicitly ACKed first formal execution. Source SHA and six-version history were checked again immediately before first apply. At **01:51:49.283–01:51:51.981 Asia/Shanghai**, that positive fixture upgraded008→009; first suite8/0/0/0. SQL bytes never changed after sharing/freezing/applying. No review-only sandbox or alternate formal migration location was used.

The complete first-success and later final-positive snapshots are distinct historical captures. Both have27tables and the identical seven successful history rows; V009 checksum is-287948759. Positive fixture was never reset. Separate new Foundation fixture `ama-test-b01-2281b6270fc4` / `127.0.0.1:46305` was also confirmed empty; only approved NORMAL regression uses its existing reset-based harness. Final capture preserves successful001/002/005/006/007/008/009 there. No old, failed or other-owner fixture was touched.

## Independent metadata and meaningful tests

Actual six-table metadata exactly equals the independently reviewed B00-1.2 real-MySQL capture subset, not a candidate-generated expectation: **6tables /53columns /16index definitions /36index columns /7ENFORCED CHECK /9FK constraints and9FK columns**. Every engine/collation, ordered column type/null/default/extra/collation, index uniqueness/order/prefix/type, CHECK clause/enforcement and FK target/update/delete action is compared.

The eight FileSchemaMigrationIT methods exercise real MySQL:
1. Exact independently captured metadata for all six tables.
2. Exactly six additions; exact ordered seven-version history; unchanged lower history, rows and SHOW CREATE definitions; all seven source-resource hashes; full history unchanged on zero-migration rerun.
3. Invalid backend type/status, file type/state, upload and cleanup enums→3819.
4. Every nine FK columns rejects absent meeting/backend/file/upload parent→1452.
5. Referenced parent deletions and backend ID rename→1451.
6. Separate file/upload/cleanup target uniqueness tests reject duplicates→1062 while case-distinct keys coexist.
7. Part0→3819; duplicate part/reference→1062; different-purpose reference allowed.
8. Long Unicode/control-containing display names, purpose, internal session token and nullable provider provenance round-trip; required name/purpose NULL→1048.

Test inputs are unmistakably test-only; file rows roll back after each case. The guard verifies this checkout's helper path, Docker label/one loopback binding and exact JDBC URL, empty first-use schema, no failed/unknown-version history, and validates frozen checksums before any seed on reuse. No reset/repair is called. Acceptance/replay/ownership lifecycle invariants remain application obligations.

## Actual verification accounting

Counts are parsed from both raw XML attributes and testcase elements by `artifacts/phase-b/b04-schema/verify_delivery.py`. All passing logs below contain BUILD SUCCESS and no WARN/ERROR lines.

|Verification|Tests|Failures|Errors|Skipped|Preserved evidence|
|---|---:|---:|---:|---:|---|
|Final FileSchemaMigrationIT explicit009|8|0|0|0|final-schema-it.log/xml|
|Foundation exact approved normal selector|13|0|0|0|foundation-positive-it.log/xml|
|FileStorageContractIT local group only|7|0|0|0|local-storage-regression.log /local-storage-contract.xml|
|LocalDurabilityIT|39|0|0|0|same log /local-durability.xml|
|All backend Java unit tests|28|0|0|0|java-units.log /java-unit-xml/*.xml|

This is95 distinct selected passing cases, not an all-integration-suite-green or full B04 claim. First schema GREEN8 overlaps final8 and is not added twice. Repeated upstream unit runs during Maven verify are likewise not added again. Preparation compilation is not a test pass. Java units include their own H2 readiness and intentionally unreachable loopback MySQL probe; no global no-DB-test claim is made.

The exact Foundation selector and every actual command are in `artifacts/phase-b/b04-schema/commands.md`. Historical IdentityMigrationIT's latest007/no-meeting assertion is not run at009 and was not weakened; its explicit historical-target fix remains its original owner's task. The destructive V006 refusal parameters are not rerun or counted; Foundation13 is not presented as all16. No unrelated web/Python/OpenAPI work or test result is claimed. Existing local durability/contract code is unchanged; actual local39+7 counts are taken from this run, not from older38/5 history.

## Preserved nonpassing attempts and self-review

- Collector's initial wrong root-parent index stopped at Python import before DB access; original log preserved and diagnosed by actual path ancestry.
- First attempted RED stopped in @BeforeAll because Maven's module cwd did not resolve the checkout-relative helper path. Raw XML records **1failure/0errors** with a blank testcase name; Maven console labels the container setup problem an error. It is explicitly **not valid RED**. Both outputs remain unchanged; ancestor lookup fixed the guard before real missing-table RED.
- External pre-GREEN versus first-success comparison initially saw audit AUTO_INCREMENT2→3, entirely from GREEN setup's extra test row. Raw snapshots/DDL diagnostic remain unchanged. Every original row/history entry persists; no structural DDL differs. The actual exact migration DDL assertion snapshots after this seed and passed, including the allocation counter.
- The first offline accounting script incorrectly expected the console's error classification; reading raw XML exposed the actual1failure/0errors. Its failed log is preserved, accounting corrected to the raw testcase, and final offline checks pass.
- Self-review strengthened the test guard to validate checksum before seeding reused fixtures. Final schema8 reran after that sole test change; frozen SQL did not change. No formal migration failed or failed history was erased.

Source/resource/document diff --check is clean. Raw tool logs retain original upstream trailing spaces; they are not edited to manufacture whitespace cleanliness. The historical preparation checkpoint/report is distinguished from behavioral evidence: final preparation compiler log is archived, and the first invocation's tail remains in conversation tool output as its original on-disk log path was reused before any migration or behavior evidence existed.

## Evidence inventory and limits

All raw logs/XML, seven historical captures (initialempty/lowerRED/pre-frozen/firstsuccess/finalpositive plus separate Foundation initial/final), source SQL copies, independent metadata subset comparison, diagnostics, commands and verifier scripts are retained under `artifacts/phase-b/b04-schema/`, with `SHA256SUMS.json`. No credential env, container environment dump, private key or production credential is included. Preparation compiler evidence remains under `artifacts/phase-b/b04-schema-preparation/`. Manifest count/hash is supplied by the coordinator report after final generation.

[Scoped schema record](file-schema-prerequisite.md) states the remaining gates. This delivery does not implement registry/control/API/upload service, MeetingAccess or subject/outbox writes; it cannot prove orphan invisibility, same-meeting/typed-owner authorization, immutable accepted receipts, restart/FINALIZING/replay protocol, blob-before-DB sequencing, concurrent reference/epoch cleanup, complete local+S3 semantics, HA/power loss or production upgrade of populated legacy deployments. Root's independent implementation review/adoption remains necessary before this structural prerequisite unlocks V010. Full B04 stays OPEN.
