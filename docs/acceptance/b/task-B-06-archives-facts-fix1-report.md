# B06.2 Task3 fix1 — exact pre-transfer UTF16 bounds

2026-10-23. Same-author correction for F1 only, submitted for the same independent reviewer. Fix BASE `298695761706419ee20c1b6b98f3ad73eff65cdd`; original Task3 BASE `ad9960595ab5de6eef7760e2c9094d23666074cb`. Tree `/home/tanren/ama/.worktrees/b06-processing-facts`, branch `codex/b06-processing-facts-20261023`. Exact final commit/ranges are added to the root ignored fix1 report after commit. No peer work/import/subagent, push/merge, or production activation occurred.

## Reviewed finding and correction

Root's complete independent review at `.superpowers/sdd/task-B-06-archives-prepared-brief/task-3-review.md`, SHA256 `68e25a1262b2b4883c4683a907a57c3088720989d81a6016898e9d0f327ea763`, was fully read. SPEC and quality requested changes for exactly one P2 F1: MySQL CHAR_LENGTH counts Unicode scalars while maxStringUtf16 counts Java code units. The previous identity preflight could accept40 supplementary characters under a64-unit budget, then shared access would materialize80 units. The manifest preflight also selected LONGTEXT before its later stream check rejected that mismatch. This finding is a resource-bound defect, not a claimed authorization bypass.

Only `JdbcProcessingFactsReader.java` and its newly introduced `ProcessingFactsReaderIT.java` were reopened from the original delivery. Both identity and manifest metadata queries now measure three independent dimensions under their retained row lock:

```sql
OCTET_LENGTH(value),
CHAR_LENGTH(value),
OCTET_LENGTH(CONVERT(value USING utf16)) DIV 2
```

The first is UTF8 bytes, the second Unicode scalars, and the third actual UTF16 code units. The real pinned MySQL8.4.6 tests independently observe40 emoji as160 bytes/40 scalars/80 units. A maxStringUtf16 check uses only the third dimension, before shared access's display_name projection or the manifest projection. maxAggregateScalars continues to charge only the second dimension using overflow-safe remaining-capacity subtraction. No guessed factor-of-two rejection of all scalars is used; exact legal supplementary boundaries remain available. The normal Java string path supplies its own exact length and codePointCount separately to the same dimensional helper.

ManifestSize retains all three measured values; the bounded stream later verifies exact UTF16 length as well as exact scalar and byte counts. That post-transfer consistency check is additional validation, never the pre-transfer gate. Its initial StringBuilder capacity uses the already-bounded actual units, capped at1024. No unbounded getString(config_manifest), early body projection or budget-sized container allocation is introduced. Shared JdbcMeetingAccess remains byte-identical; its getString(display_name) runs only after this reader has checked actual units under the same retained identity lock. Public signatures/standalone authorization/fact completeness/fence values/order/cancellation/limit arithmetic are unchanged.

## Real RED → GREEN and complete regression

New evidence is only `artifacts/phase-b/b06-archives/facts/fix1/`. Each run has its exact runner arguments/exit, raw stdout/stderr, source-before-run copies/SHA map and actual failsafe plus automatic surefire XML. All source records and XML testcase/declared totals were checked. No skip, no compile/setup failure and no simulated DB return qualifies a pass.

| New run | Exact command after `python3 tools/checks/run.py` | IT tests / failures / errors / skips | Exit/ruling |
|---|---|---|---|
| red-01 | `it ProcessingFactsReaderIT#supplementaryIdentityUtf16LimitPrecedesSharedAccessProjection+supplementaryManifestUtf16LimitPrecedesLongtextProjection` | 2 / 2 / 0 / 0 |1, genuine assertion RED against original reader |
| green-01-scoped | same exact two-method selector | 2 / 0 / 0 / 0 |0, precise F1 GREEN |
| green-02-final | `it ProcessingFactsReaderIT` | 12 / 0 / 0 / 0 |0, final whole reader regression |

The identity RED independently proved both failure to throw and actual preparation/use of shared LONGTEXT projection. The manifest RED proved that the later BUDGET_EXCEEDED exception happened after its actual LONGTEXT SELECT. The JDBC source delegates all real calls/results and records real prepared SQL; it never replaces returned values or fabricates lengths. Real SQL measurements in each test verify the actual80-unit counterexample.

Final identity regression rejects40 emoji before any shared name projection, accepts32 emoji at exactly64 units, then separately allows32 aggregate scalars to reach domain acquisition before subsequent row scalars exhaust that deliberately tiny aggregate budget. Thus units cannot incorrectly replace scalar accounting. Final manifest regression rejects40 emoji before selecting LONGTEXT, and returns the exact original32-emoji/64-unit value at the legal boundary. That intentionally non-JSON manifest is an acquisition-only negative fixture; it is not presented as a valid S1 processing manifest/binding. Existing ten cases continue verifying actual complete canonical processing, task/producer completeness, historical fences, authorization, byte/row/edge/member bounds, current reads, ambient/cancellation, Long.MAX budgets and post-commit cancellation.

Each mandatory IT runner also automatically executes the same179 dependency units. All three runs retain179/0/0/0 unit XML; these are overlapping executions, not537 unique tests. No extra explicit whole-unit/old integration cohort was run. Root still owns final combined unit verification. The fix introduces two independent test methods; final unique reader methods are12. Only the2-method and whole ProcessingFactsReaderIT selectors were used.

The only setup issue was an initial read-only attempt at absent `/home/tanren/ama/task-3-review.md`. Root supplied the actual ignored briefing-directory path; it was fully read and SHA verified. This is recorded as setup, not test RED. No other setup/test failure occurred in fix1.

## Exact source association

The new tests were written and executed against the original reader before any implementation change. Their test-source SHA is the same in RED and both GREEN runs. Final source/tool snapshots match the current files byte-for-byte:

- Reader: `9a0a4d3ccb2973c5ae28e726c4f1aea0e9793b7be1c844cd1e3abb3b8fc751ef`.
- ProcessingFactsReaderIT: `0caaeab50bfa7caf10dce23a4b8fb93949d3ae3c946b4e1d564015a3a93d52f2`.
- Core interface and ArchiveProcessingFixture retain their original2986957 bytes and are included in every new run snapshot; no Task1/2 class is used.

The RED reader SHA remains the original `7648daf8ce226b05e339d7d67bc4cf37feeaf05dfdd504a3a8973d25d9ef1533`. `runs.json` records all new counts and exact commands. New auxiliary tools `b06_archives_facts_fix1_run.py` and `b06_archives_facts_fix1_finalize.py` isolate all writes to fix1 evidence/new report; neither old runner/audit/seal nor the fixture creator was executed or changed.

## Preserved historical seals and current full delivery seal

Original Task3 raw evidence/reports/seals/tools, including all prior failures and the original final10-test record, remain byte-identical to2986957. Its original allowlist SHA remains `b3ecdd8ea0558ee1507a66409a1091293f21184820aafc18775fd75c34362df6`; its original SHA256SUMS file SHA remains `16a31383f9e90618a15b6d60ccafd27736a44c34d81e1f483b820b6754b2607a`. Those original source claims are historical and commit-bound; the two explicitly reopened current source files supersede them for F1. The old manifest must be checked against its historical commit rather than silently rewritten to fit current source.

New `fix1/full-current-allowlist.json` and `fix1/SHA256SUMS` cover the entire current public Task3 delivery, including original immutable files/historical seal, current corrected source, new tests/tool/report and every new evidence file. The new SHA256SUMS excludes itself, includes no private .tools file and no root .gitattributes. Nested original evidence attributes remain unchanged and cover the new evidence. New seal generation checks every unchanged old public path against2986957 and scans explicit allowlisted bytes against only the same new fixture's private password values without printing either. Counts and allowlist SHA are recorded in the ignored final report to avoid a self-referential report hash.

## Same sole fixture and SQL/data preservation

No new container was created. Only the explicitly reassigned original B06.2 fixture was used serially: container ID `363e6bef449436390d3e21041408b53002a48311668c86864befe2535404e45b`, owner archives-20261023. Private env PATH remains `/home/tanren/ama/.worktrees/b06-processing-facts/.tools/b06-archives/env.sh`; no value is included. No old unrelated fixture/private environment/PKI/S3 was read, stopped, reset or repaired. All new test rows have fresh random fixture IDs; updates affect only those new rows. No previous Task3 fixture row was repaired into a passing state.

The new read-only final audit validates exact owned container ID/owner, captures selected text byte/scalar/unit lengths and manifest SHA provenance, and verifies the41 previously sealed manifest rows retain their original SHA. It compares the complete twelve-row Flyway history byte-for-byte to the old final history and verifies all12 formal migration bytes/SHA against original lower-sql-before.json and2986957. No migration bytes, checksum/history, permanent003/004 gap, schema or shared behavior changed; repeated fixture SchemaMigrator calls validate013 with zero new migrations. Original fixture resources/initial-zero/image proof remain the original historical evidence, not a new initial-zero claim. The fixture remains running/preserved and no additional Task3 DB call is planned after this audit.

## Scope and handoff

F1 is corrected with real small MySQL Unicode counterexamples and exact boundary evidence. No additional finding was parked or expanded into other domains. Original API/fact/fixture reuse summaries remain applicable, except that the pre-transfer UTF16 acquisition guarantee is now supplied by this correction. No qualified upload, current business input pointer, producer/Worker/API pipeline, durable archive source, cross-JVM restart, production capacity/HA or full B06 acceptance is claimed. No universal driver cancellation/deadline guarantee was added. Root will package2986957..fixHEAD for the same reviewer; no adoption is claimed before independent SPEC+quality PASS.
