# Task 6 independent SPEC and code-quality review

Date: 2026-10-24. Review only; no implementation edits, tests, runtime/network/DB/private-environment access, Git mutations, or delegated reviewers.

**SPEC: FAIL. Quality: Needs fixes.** Findings: **0 Critical, 2 Important, 1 Minor**. Four explicit CannotVerify items below limit the evidence assessment. This is the Task 6 review, not whole-Phase-B adoption. Functional qualification pins do not constitute independent source approval.

## Scope and inputs

Reviewed BASE `80c878f52c7b748efdcdb64e707d50cf410843b4` through author HEAD `aa490afc2690e9517a691cc8d1ae9487a025806d` using the prepared complete non-artifact diff, including all 45 source/test/contract/generated/author-document sections. Read in bounded passes; repetitive OpenAPI JSON was also compacted without changing its data. Recovered portions omitted by tool-output truncation rather than assuming they had been read. The 488-path stat and audit companion identify the larger evidence population; the root reviewer owns the exhaustive artifact/source/hash/private/resource custody audit.

Independently calculated:

- Review package SHA256: `1fe394463b934d0eb1a78dd97a02032f15e9c25245bfdbd73f5e3cdc52eac7ce`.
- Author report SHA256: `a4d3006e215f9393642f99d0cd78f16b5fa70c727bd20ec6d826fc17ccc3e345`.

Requirements read: task-06-brief.md, prepared brief, exact plan extract, runtime-author-common.md, binding review constraints, adopted saved-transcript wrapper and full saved-transcript R1, its closure/review context, and the controlled-runtime specification's full Normative R1 and following authority refinement. The bounded final Task5 seam repin and Task6 qualification candidate/additive candidate supplied interface and numeric context only. No whole implementation plan/history was reviewed. Old Task1–5 acceptance was not reopened.

All source paths below are relative to `/home/tanren/ama/.worktrees/phase-b-completion`. R1 refers to `artifacts/phase-b/b-remaining/controller-saved-transcript-design/task-06-saved-transcript-design-r1.md`.

## Findings

### T6SR-01 — Important: a successful version write can exceed its own ancestry admission ceiling

**Anchor:** `backend/adapters/src/main/java/ama/transcript/JdbcSavedTranscripts.java:109`; related `SavedTranscriptGraph.java:25` and `JdbcSavedTranscripts.java:73-76`.

Discovery admits the selected source only while its existing ancestry size is below `min(revisionDepth, revisionNodes)`. Save, marks and restoration then unconditionally append one WORKING row whose parent is that selected source. Neither planning nor final readback reserves/checks this extra ancestry node: readback validates only the newly inserted revision/segments/marks. Thus a valid source already at the effective ancestry ceiling can produce a committed 200 result that the same unchanged service immediately rejects during read/confirmation discovery. With the qualified depth128/node256 tuple, a small-content RAW plus 127 WORKING ancestors reaches depth128; another save creates depth129. The other checked budgets need not be exhausted by this small graph. A positive depth1 configuration makes the same defect visible on the first save, without requiring a large graph.

This violates the complete bounded saved-graph/new-row acceptance obligation: operational refusal must occur before committing a result that cannot satisfy the installed reader. The issue affects actual ordinary sequential saves, not only corrupt fixtures. It also permits advertising a write that creates an unreadable current pointer.

**Fix:** admit the complete prospective ancestry (selected chain plus the new immutable node) before insertion, recheck under F, and make the corresponding capability projection consistent. Preserve the full original ancestry rather than truncating/reparenting it to work around the limit. Add a boundary case proving limit-minus-one succeeds and its result is readable, while an additional append fails atomically with unchanged pointers/fences/receipts. The same check must cover SAVE, MARKS and RESTORE.

### T6SR-02 — Important: owner current reads hide an inconsistent non-null confirmation pointer

**Anchor:** `backend/adapters/src/main/java/ama/transcript/JdbcSavedTranscripts.java:169` and `:220`; related `SavedTranscriptGraph.java:37` and `:49`.

The graph acquires confirmations by the selected revision, rather than validating the non-null current confirmation pointer. The final relation check runs only for a non-owner. Projection then maps any selected confirmation that does not equal the root pointer to `confirmation:null`. Consequently an owner current GET, including `/transcript/confirmation`, can return 200/null with a non-null observation.currentConfirmationId when the pointer refers to another revision/meeting. A null saved pointer with a non-null confirmation pointer also takes the early empty-graph return and is reported as available. These states are not legitimate absence and are not prevented by the ordinary single-column foreign keys.

The adopted design requires complete current-pointer/actual-confirmation facts and closed failure on corrupt provenance (R1 sections3,6,8). The confirmation command already rejects a conflicting pointer, so the read routes currently disagree with that integrity rule. This finding is about the newly introduced current read; an owner inspecting a genuinely old historical revision may legitimately observe a different current confirmation.

**Fix:** for CURRENT acquisition, establish and validate the exact non-null root confirmation relation under the retained root/declared confirmation locks in both S and F; require a current WORKING and the same meeting/revision/unique actual confirmation. Reject mismatches as unavailable instead of projecting null. Preserve the owner-history distinction. Add current owner/member negatives for wrong-revision/cross-meeting pointers and a null-saved/non-null-confirmation combination, with no content success.

### T6SR-03 — Minor: all four new generated mutation request types collapse to unknown

**Anchor:** `web/src/shared/api/generated.ts:1206`, `:1301`, `:1314`, `:1322`; originating schemas `contracts/openapi/transcripts.yaml:902`, `:972`, `:1007`, `:1042`.

SaveRequest, MarksRequest, ConfirmRequest and RestoreRequest are emitted as an object unioned with `unknown | unknown`. TypeScript reduces such a union to unknown, so consumers lose checks for required source/saved IDs, complete segment payloads and revision-token representations. The root-only `anyOf` branches containing `required` without properties cause this generator result. Mechanical regeneration and a passing project typecheck do not establish useful request types when no current UI consumes them. This does not weaken the server parser, which remains strict; it is a producer-contract quality defect for the downstream client.

**Fix:** express the alternate revision forms using a generator-compatible schema composition that retains the complete object shape, then regenerate mechanically. Verify the generated request types reject malformed payloads and retain token strings without editing generated.ts by hand.

## Strengths and SPEC observations

- **S/A/F structure:** archive/profile/producer/hash work is in `prepare()` after the snapshot transaction and before F. Private held helpers consume the already-acquired genuine gate. SavedScope verifies private issuer, actual DataSource holder, actor, meeting, purpose and exact gate; detached SavedTranscriptGraph is not authority. Ordinary failure recovery has a named allowlist, and nonordinary A errors precede final winner return after fresh authorization.
- **Protected acquisition:** every declared revision is row-locked before segment enumeration; all segment parents precede complete mark enumeration. Deferred segment/manifest transfers follow measurement and admission of the content-bearing sets. Transfers are exact PK selects. The same mechanism is used in F and new-row readback. Empty marks are established through actual queries under parent locks, not inferred by omitting a collection.
- **Cumulative limits:** the operation ledger is shared across discovery/S/A/F/planning/readback, checks UTF8/scalars/UTF16, owned-copy dimensions, response escaping, rows/edges and conservative canonical allocations, and uses checked remaining-capacity comparisons. Archived canonical minima are read from the selected original closure. The original parser source-byte limits are distinct from edited body content; no finding here demands reparsing human edits. The report's per-source parser interpretation is not treated as a new product quota.
- **Original provenance:** each WORKING hop retains fixed segment metadata and original input/batch/fingerprint; the RAW ancestor requires real SUCCEEDED typed output and the expected full producer TaskSpec computed outside roots. Original manifest/config/codec/parser/confirmation closure is verified, with no latest-default repair. hashDraft factors the same saved projection/algorithm and creates no fake confirmation identity/hash.
- **Atomic commands and replay:** source order/identity and complete marks are preserved, new human producer is null, pointers/fences/search/debt move in the transaction, and audit/outbox flush is last. Accepted receipts reference real immutable rows with separately labelled current observations. First confirmation, unchanged confirmation, explicit fresh reattachment and historical replay are distinct; old replay does not attach pointers. Replay remains available when the new runtime branch is unavailable.
- **Access and HTTP:** owner-history text and source-byte eligibility are separate. Current member output omits owner-only chain fields. Source endpoints delegate to the existing Task3/B04 protocol. Streaming request parsing rejects duplicate/unknown/trailing/scalar-invalid input and bounds unknown-length reads; tokens reuse the exact parser. Transcript errors and session-filter wrapping supply private/no-store paths. Recorded HTTP tests cover several real success/error/range cases.
- **Installation:** actual composition reads selected/declaration/version plus provider inventories; correction absence has a boot qualification and private component identity, with no invented pendingCount. Candidate/publication unknown/enabled/provider-present states deny new writes. Those future implementations remain absent and require later integration and activation review; the marker/held-fact seams are not claimed to implement disposition or publication freeze.

## Retained evidence checked

Read final receipts and independently parsed the existing XML (no tests rerun):

| Retained run | XML files | Distinct cases | Failures/errors/skips |
|---|---:|---:|---|
| final-allunits-01 | 41 | 254 | 0 / 0 / 0 |
| saved-final-it-01 | 9 | 16 | 0 / 0 / 0 |
| http-boundaries-02 | 1 | 1 | 0 / 0 / 0 |

The extra HTTP case is distinct; current integration total17 is supported. These are recorded author-run outcomes, not independent reruns. The unit receipt records `mavenConfig:null`; final integration receipts record the explicit integration-only selector. The final-source-check record states 380 Java sources with no changed/added paths; exhaustive source-window matching is the root review's separate responsibility.

Read the final contracts receipt and logs: lint, generated consistency and vue-tsc commands exited0 in the retained evidence. generated.log says the generated API matches the OpenAPI byte-for-byte. The final lint.log contains an available-CLI-update notice and a successful validation, not a test failure. Schema-name/collision warning statements in the author report are not upgraded into a runtime defect; the concrete generated unknown unions are separately identified above.

Inspected the actual test sources and retained concurrency receipts. Both available retained-lock-writers files contain six blocked attempts, covering body growth, direct VALUES segment insertion, and insertion into an empty mark set across the two read acquisition phases. The SQL-event sample shows identity/status, subject/root acquisition and protected metadata/body queries. Task06CompositionIT's six low-dimensional assertions check zero body content SELECT after rejection; these are instrumented real JDBC tests, not separately persisted raw driver-transfer traces. Commit refusal and lost commit acknowledgment are meaningful real-JDBC faults; they are not a substitute for every separate failure site.

## CannotVerify / acceptance-evidence limits

**T6CV-01 — Full required HTTP transport/token matrix.** Task06Fixture sends requests to `http://127.0.0.1` with a manually supplied `__Host-AMA_SESSION` cookie (`Task06Fixture.java:102-107`), while the HTTPS transport exercised by the native fixture is the internal storage service. The Task6 evidence does not prove same-origin HTTPS/Secure-cookie handling for these public routes. The admin negative in Task06WireHttpIT invokes the service directly, not an admin HTTP session. The four specified large revision values/equal legacy-token-dual replay forms are not all exercised over Task6 HTTP; the new wire unit test covers 9007199254740993 and the shared token unit regression is present. Do not label those stronger HTTPS/four-session/large-token HTTP requirements passed. Browser work remains Task12 and is not required to exist here.

**T6CV-02 — Complete negative provenance matrix.** The 17-case current suite is meaningful but does not establish all adopted missing/extra/cross-meeting parent/segment/mark/member/file/task, wrong producer state/output, duplicate session, missing sidecar, published-history and disabled-route paired negatives. Pure archive/hash tests do not prove those database acquisition failures. Source inspection supports many predicates but cannot stand in for the mandatory actual read-completeness matrix. T6SR-02 is one concrete missing integrity check, not a claim that every untested predicate is broken.

**T6CV-03 — Full atomicity/phase/cancellation evidence.** Retained contention tests exercise readonly S/F, and commit-fault tests exercise SAVE. I cannot establish separate injected segment/readback/audit/flush failures across marks/restore/confirm, all authorization changes between S and F, command-F/readback transfer instrumentation, READ COMMITTED behavior, or all expired/foreign saved-handle and cancellation cases from these current tests. Source ordering and transactions support the intended guarantees, but the evidence must remain limited to the actual cases run. No live reruns were allowed in this review.

**T6CV-04 — Relevant inherited integration regressions.** The final all-unit run includes inherited hash/config/serialization/security/Worker tests, but final integration selection is Task06*IT. The retained final evidence does not establish the separately required relevant MeetingRevision/meeting concurrency and B04/B06 original-source integration regressions on Task6 source. Task6 exercises inherited code along its happy paths; that is not the same claim as executing those named regression suites. Resolve the applicable focused regression evidence without blindly running historical exact-schema suites against the shared forward schema.

Resource custody, all 7935 protected-input hashes, the full audit companion, private-value/PEM scanning and exhaustive historical-source-window custody were explicitly assigned to the root reviewer. They are not independently certified here. No production, company protocol, HA/capacity, B01IMM/B04Q401 or whole-B qualification follows.

## Focused unchanged-source checks and boundaries

Each expansion beyond the supplied changed hunks had a concrete cross-cutting reason:

1. `JdbcFileRegistry.java:626-679` and FileBackendCatalog.historicalStatus/requireHistoricalMetadata: verify that the new reader's repeated B04 source acquisition uses the same already-held source identities, measures before content, validates original sessions/type/current eligibility, and performs no storage I/O while roots are held. It repeats locks on retained identities; no new source authority was inferred from sourceBytes.
2. `JdbcMeetingAccess.java:50-133` and V008 meeting/identity-related shapes: check that new saved authorization uses status-only identity capture and that its root `SELECT *` does not transfer a LONGTEXT meeting field. This ruled out an apparent unbounded identity/name transfer concern. The existing shared gate obtains identity → subject → meeting and validates member grant/roster metadata; no finding reopens that adopted mechanism.
3. `MinutesContracts.check` lines26-28: establish that the false cancellation suppliers still honor thread interruption. The operation deadline is cooperative, not a claim of forced interruption of arbitrary I/O.
4. `JdbcTranscriptAuthority.java:176,254-255`: the supplied hunk introduced SavedScope but omitted the existing holder helper; this focused check verified the actual DataSource/active-transaction holder identity used by the new capability.
5. V008 idempotency state vocabulary: checked the exact stored-state context when assessing receipt restoration. No additional receipt finding is raised.

Only this new review report was written. The fixes above and applicable missing acceptance evidence need review before Task6 adoption. Future Task10/11/correction activation remains a separate later gate even after Task6 fixes.
