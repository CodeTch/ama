# Task13 gate custody preflight — 2026-10-23

PREPARATION ONLY. Read task-13-plan-extract.md and existing task-12-web-preflight.md. This supplements its already-stated new owned Playwright paths/ports, immutable prototype/baselines and real HTTPS dual-storage requirements; it does not propose another UI/workflow or change them. All source findings below use `git show 35b2325aee8e6c09aa1bdebc880c4f09bd37837b:path`; no concurrent Task5 source was inspected. Worktree `/home/tanren/ama/.worktrees/phase-b-completion`. Only this report is written; no runtime/tests/private-env/resource/Git/index/product mutation or subagents. No full gate PASS is claimed.

## Exact final affected gate map

Commands below describe baseline registration and future affected gate set, **not instructions to run the unsafe baseline now**. Task12 must finish the fresh-output support below, then root repins final reviewed source and owned resource custody before Task13 executes. Use the canonical runner for fail-closed counts.

| Invocation from repository root | Baseline scope and outputs/hazards |
|---|---|
| `python3 tools/checks/run.py all` | run.py147–173 runs every discovered Python test directory, all web unit files, root OpenAPI lint/bundle, all Java `*Test` units/helpers. It does NOT run IT/browser/visual/typecheck/build/check:api. Fixed Python outputs at116–138: artifacts/checks/python.json, python.log and python-N.json (plus transient same-stem .tmp from python_suite16–18); existing reports unlinked/log overwritten. Web unit XML artifacts/web/web.xml96–104; root bundle artifacts/openapi/api.yaml through contracts77–84/root package script. Java uses backend/*/target/surefire-reports/TEST-*.xml and execute13–16 deletes all matching existing reports before run, not just selected class. |
| `python3 tools/checks/run.py e2e` | All registered web/tests/e2e/*.spec.ts chosen87–104; XML artifacts/web/e2e.xml, passed to child as PLAYWRIGHT_JUNIT_OUTPUT_FILE. Playwright outputDir artifacts/web/test-results reused, fixed server127.0.0.2:4275 and no reuseExistingServer. Task12 real business suite must replace/extend actual contracts; baseline shell is not business qualification. |
| `python3 tools/checks/run.py visual` | All registered visual specs; XML artifacts/web/visual.xml, same shared Playwright outputDir. Snapshot lookup web/tests/visual/baselines/{projectName}/..., updateSnapshots:none, exact zero differing pixels. Existing shell screenshot covers y0..204 only; actual-full.png goes through info.outputPath into test-results. No --update-snapshots/recapture. |
| `npm --prefix web run typecheck` | web/package.json14 script `vue-tsc --noEmit`; source/type validation, no report path configured. Capture stdout/stderr/exit into fresh owned evidence. |
| `npm --prefix web run build -- --outDir <fresh-owned-build-dir>` | Script is vite build; web/vite.config.ts has no output override. Default build dir is web/dist; Vite's existing outDir CLI is the direct alternative for fresh build output. Resolve directory explicitly for web cwd; never choose repository/artifact ancestor as output directory. |
| `npm --prefix web run check:api` | Existing safe source-preserving alternative: check-api.mjs5–13 creates isolated OS temp generated.ts, compares exact bytes to tracked web/src/shared/api/generated.ts and removes only its own temp directory. No generated source overwrite. Capture log/exit separately. `generate:api` writes actual generated.ts and belongs to Task12 source author, not Task13 docs-only validation. |
| `python3 tools/checks/run.py it <ExactApprovedClass>` | Conditional affected nonreset runtime cohorts only, explicitly inventoried on final source. run.py52–75 needs approved DB URL, discovers `*IT`, expands class glob/#method, runs Failsafe verify and deletes existing backend/*/target/failsafe-reports/TEST-*.xml. It does not understand comma class names as a single glob. Never select `it` without approved exact class selection or invoke a broad Maven verify that adds old reset/schema tests. |

Do not separately rerun full `unit`, `web` or `contract OpenApi` after a successful final `all`: they are included. Full browser/visual are explicit distinct gates. Selector examples in old READMEs (`shell.spec.ts`, `client.test.ts`) are historical subset examples, not the final full affected set. `all` stops on first failed component; report only what actually executed. `load`, `ops` and non-OpenApi corporate contracts are unregistered run.py156–160, not substitute PASSes.

## Minimal source-owned output fixes before Task13

Task12 already owns new browser configuration/evidence under its preflight. Extend only the actual fixed sinks needed for canonical validation; source repair belongs to Task12/review, not docs-only Task13. Do not freeze invented option names in this note.

1. **Runner evidence root:** run.py96–104,116–138 needs caller-selected fresh owned artifact output for web/e2e/visual XML, Python aggregate/per-directory JSON and log (including matching .tmp), preserving discovery/accounting/exit/zero/skip rejection. It currently exposes only group+selector147–151; no fresh-output flag/env exists. Canonical web_tests sets PLAYWRIGHT_JUNIT_OUTPUT_FILE itself at102, so exporting that variable outside runner does not redirect its output. A single fresh root must reach both paths runner reads/counts and paths children write; changing only printed paths is insufficient. Independent e2e and visual outputs need distinct result directories so later Playwright cleanup cannot erase earlier failures.
2. **Contract bundle:** root package.json7 check:openapi hardcodes Redocly `--output artifacts/openapi/api.yaml`; run.py79 invokes that unchanged and81 prints fixed path. Make the selected canonical bundle output honor fresh owned output while retaining both lint and external-reference bundle and truthful evidence path. Existing safe direct command alternative is the same pinned Redocly lint followed by bundle `--output <fresh.yaml>` (the literal --output already appears in baseline package script); direct execution alone must not be relabeled canonical `all` evidence. No schema/contracts rewrite or generation during Task13.
3. **Playwright run outputs:** web/playwright.config.ts6–12 has an existing JUnit env override but hardcodes test-results, base URL/port/server. Task12's own config must supply unique fresh result directory and matching chosen loopback server/baseURL; retain immutable old snapshot lookup/zero-diff policy for inherited shell checks. Existing direct CLI supports `playwright test <explicit paths> --output <fresh>` with PLAYWRIGHT_JUNIT_OUTPUT_FILE=<fresh.xml>; canonical runner currently does not forward --output/config/port options, so Task12 must wire actual canonical execution to the prepared output/config. No second runner or test bypass is needed.

No source change is needed merely to make check:api temporary or build output fresh: existing check-api temp comparison and Vite --outDir suffice. Existing Java target reports are build outputs, not a reason to redirect Maven through an unreviewed property: before canonical execute unlinks them, preserve prior required raw XML/log/source seals in their already-owned immutable artifact locations; collect the new target reports immediately. Do not delete/rewrite historical artifacts or claim old target files as fresh. Task12 must add narrow regressions for output forwarding/no old artifact writes if it changes runner/package/config; existing runner tests use isolated temp ROOT and check canonical child dispatch/accounting (test_run143–232), so extending those checks need not touch runtime fixtures.

## Fixture and historical evidence exclusions

Final IT selection is a reviewed final-source inventory, not every IT class. Reuse Task7 source-alignment report's exact baseline hashes/ranges and safe cohort map rather than duplicate design: ExternalExecutionIT, ModelPermitIT, B06QueueSeamsIT, ReviewConcurrencyIT, TaskLeaseIT, OutboxFenceIT/ConsumerIT, ReconciliationWorkerIT and guarded WorkerProcessIT are nonreset baseline candidates; consumer Tasks4..12 add their actual reviewed scoped cohorts.

- MysqlTestDatabase.reset10–19 executes DROP DATABASE. IdentityAccessIT29–43 resets and deletes shared identities/audit; FoundationIT34, MeetingSchemaIT30, IdentityMigrationIT12 also reset. Exclude them from retained shared-fixture final gate selection.
- B05SchemaIT targets010/exact historical history; TranscriptSchemaIT target011.1, MinutesSchemaIT target012, PublicationSchemaIT target013; legacy ACL/identity suites target older history. RuntimeMetadataSchemaIT targets013.1 and must be repinned after Task8 SQL. They are historical-target tests, not generic forward-schema validators. Never repair/reset/outOfOrder/weaken their historical assertions.
- PreparedCompletionIT75–80 asserts exact history.size()==12 at BASE. Repin Task5's approved adaptation/successor before selecting. The current retained schema already includes13.1; Task8 will alter final shape again. Do not apply historical fixture creator guards to final schema or create an exact-target fixture without root's separate resource custody.
- DatabaseRecoveryIT6–22 Docker-restarts a DB; old fixture scripts create/remove containers/roots. Neither is an automatic integrated verification step. Existing Python mysql fixture tests explicitly mock Docker; runner Python discovery is not authorization to invoke fixture CLI.
- `tools/checks/database_design.py generate`668–672 rewrites current draft DDL/dictionary/ER. verify584–659 inspects a historical initial-fixture record, applies whole draft DDL unless --metadata-only, appends old artifacts/phase-b/b00-provenance-design/mysql-raw.jsonl509–513 and writes/compares old acceptance metadata655–659. Even metadata-only still appends historical raw evidence and executes behavioral negative INSERT checks648–651. Neither action is a safe generic final Task13 gate. Task8 owns reviewed current generator/schema/dictionary/ER source/evidence; Task13 reads that final evidence, routes required source corrections to author/review.
- `capture:prototype` is not a gate: capture-prototype.mjs8 requires explicit --refresh-reference when references exist, but then17–35 rewrites original screenshots/rendered-shell/baselines/provenance. Never use it or --update-snapshots to approve current implementation.

Direct probe CLIs are separate from canonical all: check_contracts.py181–199 reads explicit offline manifest and writes JSON to stdout (returns1 for pending,2 for failed); ha_preflight.py353–370 reads explicit inventory or reports blocked, stdout only; model_probe.py331–348 requires explicit private config and --output, may perform actual model work and overwrites chosen file. They are not extra final B capacity/company/HA gates. Existing offline stdout/fresh explicit output alternatives preserve records if separately in scope; no new permission to read configs/call endpoints follows. Their delivered Python tests are already discovered by all.

## Repin and single full affected-gate rule

Before Task13, root repins Task12's reviewed source/output controls plus all Tasks1..12 final commits/schema/test inventory and exact owned runtime resources. Check direct gate chain and output paths against THAT source; this BASE map cannot certify future code. Task13 remains docs-only: route code/setup fixes back to sole source author and independent review.

Run the relevant full affected set **once on that final integrated source**, preserving the exact invocation, BASE/HEAD, preexisting evidence and every failure/new output with hashes and fresh counts. Successful full components are not rerun just to improve wording/counts. A failure/source fix justifies bounded focused recheck and only affected full gates again after final change; preserve previous failed attempt. Archive outputs to distinct owned invocation paths before another command can clean/reuse them. No one command at BASE means “all B”: canonical all excludes IT/browser/visual, and passing local controlled checks does not certify company/HA/capacity. Independent whole review/records/adoption remain root-owned Task13 work.

## Baseline byte seal

Git blob is object SHA1; SHA256 is entire committed file. Source ranges above are one-based. Planning briefs are working preparation inputs, not runtime evidence.

| Path | Lines | Git blob | SHA256 |
|---|---:|---|---|
| `tools/checks/run.py` | 176 | `16a750cee47604dcff7452b225a3e255352b63f4` | `a3ece9cd0c1249fcbe51982fe9b2f30d330b4418406e4d00b8dc39a6cb8b3217` |
| `tools/checks/python_suite.py` | 22 | `1651eaa8bf2a0dc50ff2760ba526f06e8e14f8f0` | `9e41d750daba607d9dd7f8197b440888d2942680e33d76d3cc4d05bc75757f54` |
| `tools/checks/tests/test_run.py` | 265 | `6cd2c7544710ae82b4470e531db214d424aac0fd` | `06dd56c7592fb1687363b2896177e8c9082a5d5bb363c7a1e1763aec3cc18924` |
| `package.json` | 10 | `2053504daf902fd38822e70bcde375393d92ff41` | `fe42268cfb50065dea011d3bee7a21a306dfcf18a04ee6139f1c8a055e534757` |
| `web/package.json` | 38 | `2d746c212806e2c7dd1d26d1679e3a1245d8cb43` | `0c6e3334e70a699d95bbfc6c43ccb9b087341883588db994b3126cf9152c034d` |
| `web/playwright.config.ts` | 13 | `d1f78e0928a38ebad2538678a2868ad6bd10be4e` | `8ce8bb2fcffeb5856d61d123cf43a60b081148832dd673c3f20eec03e79eaaa0` |
| `web/vite.config.ts` | 3 | `d8506385bce6d59e3ad71dab2dee3b35b315e129` | `97133045dcb8a0a89896b48e2d6b3902573aa6ac879ee99dc8e0511e063f44e5` |
| `web/scripts/check-api.mjs` | 13 | `8bf9a57af5a7635bee3dc320d595846206930b96` | `7da55705dee56f2b0a56d0eba811ae7529696c6c727b9db9b03c13088368f72e` |
| `web/scripts/capture-prototype.mjs` | 36 | `80dad7c43381d63d2bc88cf1c14bfba607021983` | `03c1ced7a6b2b3634ffa36348ac63a64e121b39bb88e6b4c65573c7d08bbe6e6` |
| `web/tests/visual/shell.spec.ts` | 12 | `89fb93208dadc18047743ff3df0d683b26c32980` | `26adafd5152c0d6802bde8ccdbd294066dc85161fd81359b82a26a95173c9d9a` |
| `backend/README.md` | 43 | `5c7925590ee39656d6765b444b69823f1a5d6542` | `3fd70e24f2df6e22c8f6dbe76deb6796375307c83d1b4c4e5e03a11485269b06` |
| `web/README.md` | 39 | `9a23097bd575c9719dfd5bda3708ede5366ea2fd` | `e27ca25a3c70cb148a3c4072a120dda0b02eded0c99a28fb69905c54357fd7ca` |
| `tools/checks/database_design.py` | 676 | `f699d85317c530505789e289c843ba8ee364fbf1` | `91493cbb251c99c800bd2aec7861596d50ac76acbf9a159d14c59aaf5b71e6a0` |
| `tools/probes/check_contracts.py` | 216 | `ea77156739cebbc5fdd593697777cbbb3f01a3f0` | `98698fd4da65db67894bd7bb275a770c8a6bd3240343f8e86054ee5b4da06f9e` |
| `tools/probes/model_probe.py` | 352 | `c082b21afa4a5b441082516fc578bae3ad55fd52` | `7fb360120b29d3ef118d27207ce01e9aa99ff76ddbcc72cb36b1a875c2ccdcc6` |
| `tools/probes/ha_preflight.py` | 374 | `c7fbd5cba5de3938455a0187e558b5ec9fb77c90` | `0f1a7210ff31ebcc45ce683e402dbd50ae2689cdb7d7322e4091c2c71aa389ac` |
| `tools/checks/tests/test_mysql_fixture.py` | 254 | `1ac695504a3a9ae42a198a5398390942ded48a5b` | `15ba6dd0ceb0bf1a871a3ca2098cb66897395ad611770e9352857a0f73f6896a` |
| `backend/integration-tests/src/test/java/ama/support/MysqlTestDatabase.java` | 31 | `9bd21e7b164b7121554d45c75ea5e42943dc40fe` | `a734c9bc0c30e0e176d28fe1505c4d7d0a2a7dc188a09f31d4c38f79a96c878a` |
| `backend/integration-tests/src/test/java/ama/task/PreparedCompletionIT.java` | 239 | `32c75b585f96222973367510cf36f118ad211efe` | `d3296fd1bcbfbec7d97acfe77044a146535c0ea6384dc1531a42423d66f03c5b` |
| `backend/integration-tests/src/test/java/ama/identity/IdentityAccessIT.java` | 212 | `8d8eff8b4f7c9c80fa0c39cc91644d297dc42bf7` | `c11bb807711682d4cc5fb0d9ca61d45392acafbd48fff1d0dc5e3f9203610c45` |
| `backend/integration-tests/src/test/java/ama/task/DatabaseRecoveryIT.java` | 23 | `8737286140bee618497cc74695610ae89ad0c7ce` | `186829397c21df401ea72e97d039a41698754db7edd61d787fd6689096e0cafd` |

Preparation-input SHA256:

- `task-13-plan-extract.md`: `777bdc993fbff31490df3c291a90a34ea407c9c37a9cae8d2dace617a2efe599`
- `task-12-web-preflight.md`: `1f4c1b1b7bcedf7d18917980ba8552bad0556acd0d03c9737a05beb8cc0af717`
