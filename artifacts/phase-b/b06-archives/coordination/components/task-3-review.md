# B06.2 Task3 independent SPEC and quality review

2026-10-23. Reviewer: independent Task3 reviewer; no implementation edits or subagents.

**SPEC: REQUEST CHANGES. QUALITY: REQUEST CHANGES.** One actionable P2 finding; P0=0, P1=0, P2=1, P3=0. Deferred findings=0; parked findings=0. The saved tests and immutable evidence audit are sound within their stated scope, but they do not cover the supplementary-character acquisition counterexample below.

## Exact reviewed delivery

- Author tree: `/home/tanren/ama/.worktrees/b06-processing-facts`.
- BASE: `ad9960595ab5de6eef7760e2c9094d23666074cb`.
- HEAD: `298695761706419ee20c1b6b98f3ad73eff65cdd`.
- Complete range: `ad9960595ab5de6eef7760e2c9094d23666074cb..298695761706419ee20c1b6b98f3ad73eff65cdd`.
- Package: `/home/tanren/ama/.superpowers/sdd/task-B-06-archives-prepared-brief/task-3-review-package.diff`, 3,394,127 bytes, SHA256 `c15ebe3f670dfdbf06a008690c9748db8926dea5e245b824832a20759b1f94a2`. Its diff body independently matches the complete immutable range, rather than a root partial HEAD or HEAD~1.
- All 307 changes are additions, mode 100644. Every current file's bytes equal its HEAD blob; author tree status was clean. The public allowlist and SHA256SUMS cover exactly the same 306 unique paths; SHA256SUMS is the 307th path and excludes itself. Every public hash was independently recomputed. Allowlist SHA256 is `b3ecdd8ea0558ee1507a66409a1091293f21184820aafc18775fd75c34362df6`; SHA256SUMS file SHA256 is `16a31383f9e90618a15b6d60ccafd27736a44c34d81e1f483b820b6754b2607a`.

Read the full activated common/review/task briefs, author report, approved prepared brief, relevant proposal §§4–7, adopted original S1 handbook and fix1 addendum, config-wire adoption, historical runtime/preflight constraints, exact new source/test/tools/report, and relevant existing contracts/access/transaction/schema/validator code. All changed evidence bytes were read in the machine audit, all public JSON/XML parsed, and key raw logs, source snapshots, fixture records and SQL history inspected. This was an evidence audit and static review: **no Java/tests were rerun, and no DB, Docker, private environment, old fixture, PKI or S3 was accessed.**

## F1 — P2: Gate LONGTEXT by UTF-16 units before fetching it

**Location:** `/home/tanren/ama/.worktrees/b06-processing-facts/backend/adapters/src/main/java/ama/transcript/JdbcProcessingFactsReader.java:223` (`Acquisition.textSize`), called from the identity preflight at lines 69–71 and manifest preflight at lines 119–122.

The preflight compares MySQL `CHAR_LENGTH` directly with `maxStringUtf16`. For utf8mb4, CHAR_LENGTH counts characters; a supplementary character takes one character but two Java UTF-16 code units. Consequently the identity preflight can authorize the subsequent unbounded `getString(display_name)` in existing `JdbcMeetingAccess.java:55–56` even when the resulting Java string exceeds the caller's explicit UTF-16 budget. There is no later Java length check on that identity name.

Concrete small trigger: use an otherwise valid fixture with an ACTIVE owner's display_name equal to U+1F600 repeated 40 times, and invoke readBatch with the normal budget except `maxStringUtf16=64` (all returned UUID/hash/timestamp and ordinary owner-ID fields fit). SQL reports CHAR_LENGTH=40 and OCTET_LENGTH=160, so the current preflight accepts; shared access then materializes an 80-unit Java string and readBatch can return successfully despite the 64-unit limit. The same mismatch exists with the delivered controlled budget itself: 40,000 supplementary characters have 160,000 UTF-8 bytes and 80,000 UTF-16 units, passing its 262,144-byte/65,536-character preflight while exceeding 65,536 UTF-16 units. No giant allocation is needed to test the defect.

The manifest preflight uses the same helper. Its later stream append checks reject actual excess units, but that does not make its pre-transfer UTF-16 gate correct; do not depend on a driver's character stream avoiding complete intermediate decoding. The identity example alone proves the observable budget violation independently of that driver detail.

**Impact:** the promised pre-materialization LONGTEXT bound is not enforced for valid supplementary Unicode. This is a controlled acquisition correctness/resource-bound defect, not an authorization bypass.

**Required remedy:** measure/bound actual UTF-16 code units under the retained row lock before either LONGTEXT projection/shared access materializes the value. Keep scalar/code-point accounting separate and preserve exact legal boundary behavior. An exact SQL-derived UTF-16 length or another sound pre-transfer gate is suitable; a Java check after getString is too late. Apply the corrected dimension to identity and manifest preflights without modifying the adopted shared access adapter. Add a small real-DB supplementary identity regression and a manifest regression proving the UTF-16 limit is enforced before the LONGTEXT SELECT; retain genuine assertion RED then final GREEN and updated source/hash associations. Reviewer made no fixes or DB rerun.

## Other SPEC and quality checks

The public API matches Task3 and compiles independently of Task1/2 according to saved evidence. The adapter privately constructs TransactionRunner, JdbcSubjectFences and JdbcMeetingAccess from one supplied DataSource, rejects actual ambient transactions, and acquires identity → subject → meeting before requiring current EDIT. The current DB identity governs access; stale Actor status and roles do not grant access. No held capability escapes and no business writes, registration/provider/API or shared behavior change is present.

The selected stored input is restricted to the authorized meeting. Reads expose the entire batch/member set, complete selected input, all owning revisions, immutable file id/meeting/hash/size, and one task union comprising every matching input task plus every nonnull revision producer ID. Cross-meeting files, wrong producer input/spec and contradictory tasks remain visible for S1 validation. There is no map overwrite, task state filter, current-pointer substitution or current-root overwrite of historical fences. FullRow columns follow adopted Table.columns with nullable cells, exact unsigned-to-long conversion and UTC six-digit DATETIME projections. Task IDs and every TaskSpec field are read from actual stored columns.

Domain current reads precede sorted file locks and the sorted task union. LIMIT remaining+1 (saturating at Long.MAX_VALUE), file-count checks and aggregate row/edge reservations fail on excess instead of truncating. Actual collection growth follows reservation, not budget-sized preallocation. Remaining-capacity arithmetic avoids overflow. Manifest byte/total-byte/scalar gates occur under the retained input-row lock before projection; the UTF-16 defect is F1. The stream loop is finite and checks cancellation before append. Cancellation/interruption is checked at acquisition, row, stream, final copy and post-commit boundaries; thread interruption is preserved. Five-second query timeout on owned SQL is explicit. The report correctly disclaims asynchronous cancellation of arbitrary driver blocking and a universal transaction deadline. Archive loading, JSON verification and S1 computation stay outside the reader's transaction.

The test probe delegates actual JDBC calls/results and observes SQL/real commit; it does not replace DB values. Controlled fixture file/task rows are explicitly synthetic metadata and direct inserts, not qualified uploads or a demonstrated atomic enqueue. The public reusable fixture does not imply actual Task4 lifecycle/commit-handshake proof.

## Independently audited saved evidence

| Run | Actual IT tests / failures / errors / skips | Classification |
| --- | --- | --- |
| red-01 | 0 / 0 / 0 / 0; exit 1 | Compile/setup: ambiguous Role import; not RED |
| red-02 | 3 / 0 / 3 / 0; exit 1 | BadSqlGrammarException, nonexistent updated_at; setup |
| red-03 | 3 / 0 / 3 / 0; exit 1 | Required verified_at omitted; setup |
| red-04 | 3 / 0 / 3 / 0; exit 1 | Real FK setup failure; setup |
| red-05 | 3 / 3 / 0 / 0; exit 1 | Genuine assertion RED against saved compile-only empty reader |
| red-06-expanded | 8 / 7 / 1 / 0; exit 1 | Seven assertion failures and one NoSuchElementException; not eight clean assertion REDs |
| green-01 | 8 / 0 / 0 / 0; exit 0 | Saved initial GREEN |
| red-07-commit-cancel | 1 / 1 / 0 / 0; exit 1 | Genuine assertion RED after actual commit callback |
| red-08-long-max | 1 / 1 / 0 / 0; exit 1 | Genuine assertion RED from wrongly rejected legal budget |
| green-02-final | 10 / 0 / 0 / 0; exit 0 | Saved final GREEN, does not cover F1 |

Each present XML's declared totals agree with actual testcase elements. Final GREEN contains one IT XML with ten independent methods plus 26 unit XML files totaling 179 executions, zero failures/errors/skips; raw final stdout reports the exact selector, ten executed ITs and BUILD SUCCESS. These are not additional unique units across repeated runs. Earlier missing unit XML is acknowledged, rather than inferred as fresh XML proof. Source-before-run SHA records match their saved files for every run. All eight final saved source/tool snapshots match HEAD, including the four delivered Java files, so the final test record is associated with the submitted source rather than a stale earlier version.

Saved fixture-create argv, selected metadata, published port and initial-zero output identify one owned loopback MySQL 8.4.6 process, exact container `363e6bef449436390d3e21041408b53002a48311668c86864befe2535404e45b`, owner archives-20261023, pinned image digest, 768 MiB memory/swap, one CPU, pids256 and process-local native AIO OFF. Actual initial table count is zero. The original empty HostConfig HostPort mistake and later exact-container port correction are reported honestly; the corrected creator is not represented as exact source-at-create proof.

All 12 formal SQL hashes independently match BASE and current HEAD and the before/post/final evidence lists. Saved post-migration and final raw history are identical: successful ordered 001,002,005,006,007,008,009,010,011,011.1,012,013, preserving 003/004 gaps. Selected final provenance is consistent with the scoped fixture. Four independent raw archive JSON files are canonical and match their refs/SHA/header. All 41 exported manifests match SQL-reported byte size/character count/SHA; each valid manifest has its actual input ID and the exact independent processing/parser/profile refs. These checks establish evidence consistency, not live database or durable archive authority.

The allowlist contains no .tools file or root .gitattributes. All preexisting tracked files, SQL, old tests/goldens, old artifacts and shared implementations remain unchanged in the reviewed range. The independent audit helper and captured stdout are in this same ignored review directory as `task-3-review-audit.py` and `task-3-review-audit.stdout.log`; they use only public files and git objects.

## Scope and disposition

Return F1 to the same author for a separate versioned correction and updated evidence. No important finding was deferred or parked. Existing exclusions remain explicit: no production upload/reference/download qualification, full segment/marks/confirmation/material graph, actual enqueue lifecycle, WorkerLoop/provider/API/HTTP, cross-JVM archive restart, production capacity/HA or FUP queue fairness qualification. Task4 retains its own actual producer/enqueue/restart obligations. A later scoped double PASS would adopt only Task3 processing acquisition, not full B06, stage B or company readiness.
