# B06.2 Task3 fix1 independent scoped re-review

2026-10-23. Same independent reviewer as the original Task3 review. **SPEC: PASS. QUALITY: PASS. F1: CLOSED.** New findings: P0=0, P1=0, P2=0, P3=0. Deferred findings=0; parked findings=0.

This verdict covers the original F1 correction and introduced effects only. It completes the original review's sole requested correction, subject to root adoption. It does not constitute full B06, production, capacity/HA or FUP fairness qualification.

## Exact reviewed identity and coverage

- Author tree: `/home/tanren/ama/.worktrees/b06-processing-facts`.
- Scoped BASE: `298695761706419ee20c1b6b98f3ad73eff65cdd`.
- HEAD: `76a50c72006835a567ff200d231bf78420d67f3a`.
- Scoped range: `298695761706419ee20c1b6b98f3ad73eff65cdd..76a50c72006835a567ff200d231bf78420d67f3a`.
- Full original Task3 range `ad9960595ab5de6eef7760e2c9094d23666074cb..76a50c72006835a567ff200d231bf78420d67f3a` was used for delivery-set context, not an unnecessary broad re-review.
- Package: `/home/tanren/ama/.superpowers/sdd/task-B-06-archives-prepared-brief/task-3-fix1-review-package.diff`; 1,974,834 bytes; SHA256 `a37caf830f574bd00d156f5c8d8935b7ab2ceacdf0e9ee734fd2664ed21c5d4b`. Its complete body matches the immutable range's full-index, ten-context-line git diff.

The scoped delta contains exactly 131 paths: the explicitly reopened reader and reader IT, plus 129 additions. All current changed bytes and 100644 modes match HEAD; author git status was clean. All changed evidence files were machine-read, with public JSON/XML parsed. The full fix1 report, original review and common review brief were read completely; the two Java changes, new tools, tracked report, key raw logs, SQL metrics/history and source associations were inspected.

**No Java/tests were rerun, and no DB, Docker, private environment, old fixture, PKI or S3 was accessed.** This is independent static review and audit of the author's saved real execution evidence. No author implementation/evidence file was edited, and no subagent was used.

## F1 closure and introduced effects

At `backend/adapters/src/main/java/ama/transcript/JdbcProcessingFactsReader.java:69–70`, identity preflight now locks the same actual identity while separately selecting OCTET_LENGTH(display_name), CHAR_LENGTH(display_name), and `OCTET_LENGTH(CONVERT(display_name USING utf16)) DIV 2`. The shared name projection follows only after that gate. At lines 117–121, the locked manifest preflight uses the same three measurements before the manifest projection.

The dimensional helper at lines 223–227 checks UTF-8 bytes against maxStringUtf8Bytes, actual UTF-16 units against maxStringUtf16, and scalar count against remaining maxAggregateScalars. Charging remains scalar-based and uses subtraction before addition; units do not replace scalars or consume a second scalar reservation. Ordinary Java strings supply codePointCount and length separately. The exact UTF-16 measurement avoids a conservative factor-of-two rule that would reject valid boundaries.

ManifestSize retains all three dimensions. Initial StringBuilder capacity uses already-bounded units capped at 1024; bounded append checks remain. The post-stream check at line 168 compares actual Java length, code-point count and UTF-8 bytes with their respective measured values. It supplements the pre-transfer gate. No unbounded getString(config_manifest), early LONGTEXT body projection, budget-sized preallocation or weakened byte/aggregate check was introduced.

The production delta is limited to these dimension/gate/consistency changes. Public API, standalone rejection, same-DataSource authority, lock/query ordering, complete task/producer facts, historical fence values, LIMIT arithmetic, cancellation and post-commit checks are unchanged. The shared access adapter, core interface and fixture retain their previous bytes. No new business write or runtime wiring appears.

The new identity test reproduces 40 supplementary characters as 80 units, requires rejection before shared name projection, and accepts 32 characters at exactly 64 units. Its independent 32-scalar budget check requires acquisition to reach the domain query before later row scalars exhaust the budget, detecting incorrect unit-based charging. The manifest test independently observes 160 bytes/80 units for 40 supplementary characters, rejects before LONGTEXT projection, then returns the exact 32-character/64-unit value. The latter intentionally non-JSON text is correctly described as an acquisition boundary fixture, not a successful S1 binding. The probe delegates real JDBC calls/results and observes prepared SQL; it does not substitute lengths or DB values.

## Saved RED/GREEN evidence audit

| Run | Actual IT tests / failures / errors / skips | Exit | Ruling |
| --- | --- | --- | --- |
| fix1/red-01 | 2 / 2 / 0 / 0 | 1 | Genuine assertion RED against exact original reader |
| fix1/green-01-scoped | 2 / 0 / 0 / 0 | 0 | Both F1 regression methods pass |
| fix1/green-02-final | 12 / 0 / 0 / 0 | 0 | Whole current reader IT selector passes |

The first two commands use exactly `python3 tools/checks/run.py it ProcessingFactsReaderIT#supplementaryIdentityUtf16LimitPrecedesSharedAccessProjection+supplementaryManifestUtf16LimitPrecedesLongtextProjection`; final command uses `python3 tools/checks/run.py it ProcessingFactsReaderIT`. Raw final stdout reports 12 executed ITs, zero failures/errors/skips, BUILD SUCCESS and runner PASS.

RED's identity method contains both the expected-failure-not-thrown assertion and the observed forbidden shared projection assertion. RED's manifest method contains the observed forbidden LONGTEXT projection assertion; its later budget exception did not falsely qualify the pre-transfer gate. These are actual assertion failures rather than setup errors. The saved RED reader exactly equals scoped BASE. Test source is unchanged across RED and both GREEN runs.

Each run retains one IT XML and 26 unit XML files. Declared counts match actual testcase counts. Every unit cohort has 179 executions and zero failures/errors/skips; repeated cohorts are not counted as 537 unique tests. All saved source hashes match snapshots. All five final snapshots match HEAD, including corrected reader SHA256 `9a0a4d3ccb2973c5ae28e726c4f1aea0e9793b7be1c844cd1e3abb3b8fc751ef` and IT SHA256 `0caaeab50bfa7caf10dce23a4b8fb93949d3ae3c946b4e1d564015a3a93d52f2`.

## Current seal and historical preservation

The current full allowlist and current SHA256SUMS contain exactly 435 unique paths with independently matching hashes; current SHA256SUMS is the 436th full-delivery path and excludes itself. This set exactly matches the full original Task3 range's changed paths. All allowlisted working bytes equal HEAD blobs. No private .tools path or root .gitattributes is included.

- Current allowlist SHA256: `0ff91e29464227c6d00aa4632d9f4e7ff2dee89285838e18c25ea200a397f33a`.
- Current seal file SHA256: `95c12d689c6d63e237f34d6df7f8b2ca4bdb175f4d415ccd506d9607a43ffede`.

All 305 original public paths other than the two explicitly reopened source files remain byte-identical to `2986957`. Every one of the 306 original hashes validates against its historical commit. The original report/raw records/seal are preserved as historical commit-bound evidence, with no silent rewrite to current source. Original allowlist/seal hashes remain `b3ecdd8ea0558ee1507a66409a1091293f21184820aafc18775fd75c34362df6` and `16a31383f9e90618a15b6d60ccafd27736a44c34d81e1f483b820b6754b2607a`.

All twelve formal SQL bytes/hashes match the original records and scoped BASE. Saved new history output exactly matches all thirteen old history lines, including header and twelve successful ordered migrations through V013 with 003/004 gaps preserved. Saved current manifest-hash query retains the exact SHA association of all 41 previously sealed manifests. All three new SQL audit command records have exit 0. New text metrics show the expected distinct scalar/unit dimensions on the same owned MySQL 8.4.6 fixture. These are consistency checks of preserved execution evidence, not a live DB audit or new initial-zero claim. New tools isolate fix1 evidence and do not execute the original fixture creator or old seal writer.

The independent public-file/git audit is preserved as `task-3-fix1-review-audit.py` and `task-3-fix1-review-audit.stdout.log` alongside this report. The reviewer initially tested package equality with the default git context width; the package uses ten context lines. Comparing with the actual full-index/-U10 format resolves that audit-script assumption. No source or evidence discrepancy was found.

F1 is closed with no new actionable issue, deferred finding or parked finding. Original Task3 limitations and Task4's separate real producer/enqueue/archive-restart obligations remain unchanged. Root owns final adoption and combined qualification.
