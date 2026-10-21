# B08 V013 schema prerequisite — author functional GREEN

The exact `PublicationSchemaIT` first full run passed **10 tests, 0 failures, 0 errors, 0 skips**, runner/Maven exit0. The runner also executed62 reactor unit tests, all passing. This completes author delivery of the bounded five-table structure prerequisite; fresh independent full-range SPEC and quality review remains required before adoption. Full B08 workflow/API/runtime and company/production/UX acceptance remain OPEN.

Owned BASE is `7f061e8a9d55f5b0db92762692edbe61123d400b`, worktree `phase-b-publication-schema`, branch `codex/phase-b-publication-schema`. The full review range includes preparation commit `1c8d3ef154933c93efb6dac427d5a6800d59da3f` and the final delivery commit, whose exact HEAD and complete path map are recorded in the external root SDD report `task-B-08-schema-report.md`. Do not review only HEAD~1. Central registration is external authority, not an imported peer source dependency. No POM/helper/runner/main Java/lower SQL/design change occurred; no push, main merge or company call.

## Frozen bytes and first-apply chronology

Only new Flyway file: `V013__publications.sql`, 7,661 bytes, SHA256 `1de37066869617723a11f05d7423584143a64e0c9ddf8879c7b4fd107de08e3c`, actual Flyway checksum **-847352333**. All five complete CREATE blocks are byte-exact to adopted B00-1.3 and precede all18exact deployable FK statements. Both physical parents of `final_result_review` are created now. No seed, IF NOT EXISTS, extra table/column/CHECK, legacy READY guess, dropped oracle index or migration rewrite appears.

| Evidence | Actual UTC / authority |
|---|---|
| Root complete-read/static freeze capture | 2026-10-21 21:47:04.731880 |
| Central committed register | `0759dc9fb5a20072fb982c91f8ddee70ec175219`, 21:48:00, epoch1792619280 |
| Separate ACK receipt, persisted locally | 21:48:36.027422 |
| Native pre-apply lower history | 21:48:36.700457, exact eleven successful versions through012 |
| Native V013 rank12 success1 | 21:49:39, epoch1792619379 |

Root fully read the entire candidate, independently checked CREATE/FK/oracle/index equality and all lower sources/history/RED, then committed the freeze before a separate exact-fixture ACK. Only afterward were frozen candidate bytes copied into the active path and first applied. Active SQL, original draft, shared review candidate, root frozen copy and actual Flyway classpath resource have identical SHA. No SQL correction or post-ACK failure occurred. Sharing/application permanently freezes bytes; a future defect requires a centrally registered ordered forward correction, never reset/repair/outOfOrder or editing lower migrations.

Only `fk_final_confirmation_9(final_confirmation.material_snapshot_id -> material_snapshot.id)` is deferred until V017. Its explicit adopted index `fk_final_confirmation_9(material_snapshot_id)` remains. Every other target exists in the actual57deployed lower tables or the five V013 tables, including result_review. The complete lower source CREATE-name set equals actual deployed names, not merely a guessed stage list.

## Independent metadata and preservation

Oracle `publication-schema-b00-1.3.json` is independently filtered from the entire adopted actual93table/709column metadata, SHA `a6c758d488c490c97bb373f404bc25c5b50db001e7c0dcb82dfcf9a6668f7aa5`, full adopted DDL SHA `ebf1eae51b303d00b2ed35c4fbd5ba4c166813564032875a6ba0fb35ba743c93`. Oracle SHA `0ae0d6550bee43740dd4633ab4eb05196b3fc04f9d3ea1bbe9cf81e776d557ff`. It was never generated from the candidate. Only the named unavailable material FK is excluded; every column/index/CHECK and both review membership FKs remain.

| Group | Independent expected = actual |
|---|---:|
| Tables / engine / collation | 5 |
| Columns / order / type / nullability / defaults / collation / extra | 42 |
| Index-column rows / uniqueness / order / direction / prefix / type | 30 |
| ENFORCED CHECKs / full clauses | 4 |
| FK-column rows / full targets / order / RESTRICT rules | 18, all single-column constraints |

All58lower objects including history retain exact DDL, every row value, five metadata groups and eleven-row successful history prefix. All11lower SQL SHA/checksums match reviewed known-success history and BASE bytes, including V012 SHA10742ca2075fa3430dedf692853e1c106bd4464eaed46d8c8c1aa07859878fb7 / checksum2101765957. Real synthetic identity/session/meeting/info/storage/file/audit/task/transcript/batch/revision/segment/speaker/confirmation and nineteen V012 generation/evidence/review table sentinels persist. There are63final objects including history; all five new tables are empty after DML rollback. Only the five tables and successful V013 history row persist.

Private historical target013 rerun and unchanged production `SchemaMigrator` latest rerun each execute zero migrations. Four full snapshots—after-upgrade, after-tests, private013-zero and production-latest-zero—are byte-equal, SHA `866f7bf59731563f5aeb0d52d9b464b0cd451cb1e91df27873a02cd10d65c841`. Read-only final verifier independently filters actual source metadata again, checks all lower objects/rows/history and source/resource SHA, verifies exact fixture/native clock and central-commit < ACK < apply ordering. No unchanged suite was repeated after successful GREEN.

## Fixture and actually executed evidence

Only new owned `ama-test-b01-ee4d94105bb9`, full ID `45e74f283bd96e71a876a303ffede564306d77da91b54fc7e73b2ec0a1bef36d`, `127.0.0.1:46539` was used. Initial0, exact pinned MySQL8.4.6 image/image ID, label/mount/loopback and actual resource settings are retained. Process-local wrapper only bounds this new fixture to2CPU/1GiB memory/no swap/256PIDs and native AIO OFF, and refuses removal even on startup failure. Shared helper/default/sysctl unchanged. This proves functional SQL behavior, no native I/O/capacity/HA or production qualification. No old/other fixture operation, reset, repair, restart, removal or failed-history cleanup occurred.

Native captures use actual MySQL `DATE_FORMAT`, UTC_TIMESTAMP and UNIX_TIMESTAMP with session+00:00; they distinguish initial capture, lower checkpoint, pre-apply, after-first013 and final clocks/history. Original JDBC Timestamp.toString fields retain JVM Asia/Shanghai rendering and are not labelled UTC.

| Run | Actual result |
|---|---|
| Independent source-SHA-guarded oracle extraction | Success, no SQL |
| Initial inherited oracle output-target assertion | Before any SQL/container creation; original empty stdout and tool-result transcription retained; not RED |
| Initial wrapper root-path FileNotFoundError | Before container creation; original stderr retained; not RED |
| Initial0 lower001→002→005→006→007→008→009→010→011→011.1→012 | Eleven actual successes |
| Missing-five assertion RED | 1 test / 1 assertion failure / 0 errors / 0 skips, expected five vs[];62reactor units passed |
| Full harness compile and later membership-isolation compile | Both success; no IT counted |
| First complete frozen013 physical suite | 10 IT / 0 failures / 0 errors / 0 skips;62reactor units passed |
| Native/final read-only preservation checks | Success; no migration or row write |

The final suite contains10distinct IT cases. The RED case overlaps the final table-existence case; neither the repeated62reactor units nor RED plus GREEN is inflated into a larger distinct test count. No wildcard legacy reset/refusal/Foundation/Meeting/B06/S3/model/web cohort ran. No unselected test is counted passing. The inherited runner performs reactor units as part of the exact requested IT command.

Final command, with pinned phase-a runtime and privately sourced own env:

```sh
JAVA_TOOL_OPTIONS=-Dama.publication.schema.frozen-sha=1de37066869617723a11f05d7423584143a64e0c9ddf8879c7b4fd107de08e3c \
python3 tools/checks/run.py it PublicationSchemaIT
```

## Physical guarantees and application boundaries

Rollback probes cover all18new FK missing-parent errors1452, RESTRICT errors1451, exact publication/attempt/mapping vocabularies and case rejection, one publication root per meeting, exact business key/provider/document uniqueness, frozen meeting revision uniqueness, unsigned/null constraints, concrete attempt execution references and long Unicode URL preservation. `final_result_review` rejects each missing parent and duplicate join. Its two parent DELETE1451probes first remove other referencing child rows, isolating each membership FK; after removing membership both parent deletions succeed, all inside rollback. Adding a later review does not automatically change old membership. Full metadata separately verifies both FK deletion/update rules and all index column ordering.

Accepted raw SQL witnesses explicitly show that individual FKs do not enforce same-meeting parent consistency, actual confirmer owner authority, reviewer identity, source/manifest/input-hash truth, attempt confirmation matching the publication, operation matching an execution's parent, URL allowlisting or authoritative provider status. These are not application rejection tests. Nullable material is physically accepted without the deferred FK; Application B must forbid nonnull material pointers until V017. Hash columns in final_confirmation have no adopted hash-format CHECK, so the witness intentionally accepts unchecked strings; no format or semantic truth is inferred.

The schema likewise does not prove immutable snapshots/membership, stable business-key encoding, source completeness/currentness, exact applicable reviews, semantic support, state-transition/CAS legality, idempotent external effects, unknown-result reconciliation, publication-success read-only rules, permissions or company capabilities. These remain B08 application transaction/contract obligations; raw schema acceptance does not authorize publishing.

## Evidence retention and remaining gates

`artifacts/phase-b/b08-schema-author/` preserves the original checkpoint manifest, all original payloads/failures/SQL/native logs/XML/full snapshots, plus checkpoint source copies, ACK and final raw evidence. All selected files are SHA-allowlisted and `-text`; nested log/XML whitespace exemptions retain original bytes. The final manifest is `final-manifest.json`. Both real private password values were scanned for zero matches without printing or exporting credentials/env/Config.Env/private PKI. Ignored raw originals and the exact fixture remain until root archives; no cleanup occurred.

The preparation script intentionally belongs to the historical eleven-lower checkpoint. Its file-count guard refuses the current active013 checkout; do not weaken it for reruns. Preserve original candidate and checkpoint source/report copies rather than rewriting historical evidence.

Fresh independent full-range SPEC and quality review remains required. Full B08 workflow/API/worker/KB/reconcile/idempotency/CAS/fencing/authority/semantic/source/hash/URL validation, company contracts and both pending UX gates remain OPEN. No real publication/mail/model/SSO/KB/company call, production/HA/native-I/O/capacity validation, push or main merge is claimed.
