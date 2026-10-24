# Task12 design fix round 1 scoped independent review

T12DR-01 — ADDRESSED in prospective design.
SPEC: PASS for this fix scope.
Design quality: APPROVED for this fix scope.
New breakage: none. Counts: Critical 0; Important 0; Minor 0.

## Scope and custody

Applied superpowers 7.0.1 scoped re-review method. Read the exact fix brief first, then complete original review and R1 closure. Reviewed only the original finding and newly introduced breakage in the actual original-to-R1 context-3 diff. The initial combined tool output was truncated; subsequently read all 63 diff lines in bounded slices 1–24, 25–45 and 46–63 without truncation. No whole-design/source/runtime re-review was performed.

Independently computed raw SHA256/byte/line counts:

| Artifact | Bytes | Lines | SHA256 |
|---|---:|---:|---|
| Original design | 57259 | 175 | `2ce7e7457f07314b7ce967f433c716764d64f97f890576755ee61f7208e9253a` |
| R1 design | 69418 | 196 | `12602bdfaeda0672478c6bb08bf92fa844746990eda08fcd67b20b0ceb14ed9c` |
| Original review | 9799 | 50 | `b9bbedb4fa71cac0ccf3c65c62d00703b947c9e10c6031586702308260f43566` |
| R1 closure | 9409 | 82 | `eaed15994de0e03ecd1592b8ccbd393da481a510a26ff05553e7d780292a54b2` |
| Actual review diff | 26573 | 63 | `190f370987dbf2e607b8789299a3209fd0fb7b5495684816fed23c0f3be17ff8` |

A read-only Python `difflib.unified_diff(..., n=3)` byte comparison returned exit0 and `PASS: supplied context-3 diff exactly equals complete original-to-R1 diff`. SequenceMatcher independently confirmed only the six closure-reported edit intervals: original/R1 lines 1, 3, 9; insertion R1 42–62; original157→R1178; original173→R1194. Hash/diff checks are artifact checks, not application tests.

## Finding disposition and strengths

- **Concrete projection responsibility and genuine composition — ADDRESSED.** `task-12-web-business-loop-design-r1.md:46–50` assigns the proposed `ama.meeting.JdbcWebMeetingProjectionReader`, Task12 backend author, B03 owner and real `MeetingConfiguration` assembly seam. Existing full saved-info/roster acquisition cannot be hidden behind a smaller DTO. A protected producer primitive must actually meet caller-budget/holder/keyset guarantees before reuse. Same-holder adoption uses genuine prepared capabilities; nested standalone access/reader calls and a second gate are forbidden. Missing capability fails before content. The design does not assert that prospective producer facades are already installed.
- **Stable metadata before transfer — ADDRESSED.** R1 `:52–56` requires bounded metadata-only discovery, fresh authorization per candidate, retained identity/subject/root/grant protection, selected info/roster parent X locks, exact complete child sets including empty rosters, pointer/ownership/state checks, and protected text-length metadata. No content column is part of discovery or length capture. Existing parent FKs and root-writer discipline are explicit premises to verify in installed source/schema, not a reliance on default-isolation gap locks. R1 `:62` preserves canonical rank and complete producer keysets, with actual B03 domain placement reconciled before source dispatch.
- **Effective cumulative budget — ADDRESSED.** R1 `:56–62` accounts for names, summaries, roster/identity/category labels, fixed metadata, UTF8/scalars/UTF16, driver buffers, application/DTO copies, retained snapshots and worst-case escaped JSON. Bounds are the minimum of installed producer/archive/HTTP bounds. Checked arithmetic and one request ledger span rows, helpers, phases, retries and final reads. Exact protected columns/keys are selected only after reservation; post-fetch checks/streaming are secondary. Failed reservation/protection/cancellation cannot transfer the affected content. Successful values remain exact; no product name quota or silent oversized-row skip is introduced.
- **Category and repeated acquisitions — ADDRESSED.** R1 `:60–62` assigns the dictionary owner/version-bound metadata/read interface, keeps absent capability unavailable, requires initial protected acquisition for immutable/archive providers, and reserves subsequent copies. A global category read uses existing session/ACTIVE identity authority rather than an invented meeting ACL. S→A→F producer composition uses its genuine final gate and repeats protected metadata/reservation/transfer while charging both passes and retained data.
- **New-path evidence obligations — ADDRESSED.** R1 `:178` explicitly adds actual-MySQL instrumentation on the new list/workspace/info/roster/category paths. It covers oversized individual fields, aggregate/copy/escape exhaustion, metadata-to-fetch barriers, text/child/pointer/version races including empty sets, revocation/disable ordering, repeated/final acquisitions, exact gate/holder/keysets, no transfer after denied reservation, and no denied HTTP content. The category SQL case uses an owned adapter through the new contract and requires separate proof of the selected immutable/archive provider's initial acquisition. Exact bounded Unicode positives prevent accidental truncation from masquerading as safety. These are prospective assertions; none is represented as passed.

## Focused checks outside the diff

Concrete risk 1: the added design could invent a status-only issuer/holder adoption seam that the actual B03 API does not supply. Read `backend/adapters/src/main/java/ama/meeting/JdbcMeetingAccess.java:1–133` and the complete `backend/core/src/main/java/ama/access/MeetingAccess.java`. The actual code has status-only `lock`/`tryLockIdentities`, authentic issuer/current DataSource transaction checks, actor/meeting/identity-coverage validation in `requirePrepared`, and standalone access rejection inside ambient transactions. Its real member authorization also retains grant/confirmed-roster/member provenance. The design's explicit final domain-order reconciliation remains necessary; this check does not certify all eventual producer integration. One initially guessed `core/.../meeting/MeetingAccess.java` path was absent (exit1); the import identified the correct `ama/access` path, successfully read next. No broad crawl was used.

Concrete risk 2: stable complete-roster protection could rely on nonexistent parent FKs. Read only V008 lines52–105 and148–162. Actual schema confirms LONGTEXT name/summary/display columns, composite `(roster_id,member_id)` PK, info/roster→meeting FKs and roster_member→roster_revision FK. This supports the declared protection design; all concurrent writer coverage and actual-MySQL blocking behavior remain source/runtime acceptance gates.

## New breakage, observations and qualification boundary

New breakage in the fix diff: none.
Out-of-scope defect observations: none.

R1 lines9/194 describe root adoption of Task11 as imminent at authoring time. The controller now supplies adoption commit `f176382`; treat the R1 statement as a dated status record. The unchanged requirement to pin adopted wrapper/source and separately qualify DDL/runtime remains effective. This is not a new design blocker or proof of Task11 installation. Task4 source review/fix remains independently active.

Cannot verify from this design diff: installed held producer signatures, final domain rank/writer coverage, dictionary binding, qualified effective deployment budgets, actual statement/transaction/race/HTTP evidence and browser capability. These are explicitly retained activation gates, not missing design repair. Source and runtime capabilities remain unproven.

Fix-round verdict: all findings addressed; no new Critical/Important breakage. Original T12DR-01 may close at the design gate, subject to root adoption. No source correctness, runtime pass, resource activation or Phase B qualification is implied.

Review activity was read-only except this authorized report. No product, Git/index/HEAD, DB, Docker, dependency, browser, network, private environment or resource mutations; no tests rerun and no subagents.
