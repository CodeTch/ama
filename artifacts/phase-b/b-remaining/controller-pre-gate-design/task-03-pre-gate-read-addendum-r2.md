# Task 3 pre-gate read addendum R2: provider-independent operation authorization

2026-10-23. Narrow residual closure for PRD-01 in R1 review SHA256 2b829d12b48ac59ca45695fb384722bdf098ecdfcc725c962cab814fb761e0d9. Original addendum and R1 remain immutable. This supersedes R1's assumption that successful provider resolution precedes outcome capture, and makes its universal authorization-precedence promise executable. All other adopted and original/R1 constraints remain binding. This is a design proposal, not implementation qualification.

## Closed selector classification before optional provider resolution

The registry recognizes exactly these final core selector record types for its new complete planned-read entry point:

```java
FileRegistry.CurrentReadRequest(String referenceKind, String fileId)
ProcessingSourceReader.HistoryRequest(String revisionId, String fileId)
ProcessingSourceReader.WorkerRequest(TaskLease lease, String inputId, String fileId)
```

The first is an additive final record implementing ReadRequest. The latter two already exist in the Task 3 source. They carry data claims only. The registry's fixed internal classification uses their concrete final Java types, never a caller-supplied action, boolean, enum value, overridden method, or configured provider result. CurrentReadRequest requires existing B03 READ. HistoryRequest and WorkerRequest additionally require existing B03 EDIT on the very same genuine handle. These two records have fixed INPUT_BATCH_FILE referenceKind implementations. Unknown custom ReadRequest implementations and structurally malformed record fields are rejected as syntax; a recognized record with valid fields but missing/legacy/ambiguous provider support remains a recognized request and is never relabelled malformed.

The mapping supplies a minimum permission requirement only. It grants no source, graph, task, scope, or byte authority. Choosing CurrentReadRequest always enters the strict current-WORKING operation; it cannot select history/worker behavior, even with IDs copied from those graphs. Configured policy authorizeRead remains abstract and may additionally restrict the accepted operation, using the existing same-handle B03 require. It cannot lower the registry's minimum. Successful prelockRead/prelockCurrent must still prove private actual provider/authority lineage, exact selector, actor, meeting, holder, preparation, and complete locked graph. No new ACL implementation or provider stand-in is introduced.

## Complete outcome boundary and exact gate sequence

For every syntactically valid recognized request with a known target meeting, capture the entire optional complete dispatch stage inside the registry-owned bounded outcome: configuration/provider selection, support checking, bounded nonlocking discovery, and prepareRead. Missing registration, legacy-only registration, missing preparation support, ambiguous complete composition, and discovery/preparation protocol/budget/profile failures are Fatal. Genuine current ineligibility alone is Ineligible. Success is Prepared. Cancellation/interruption still exits promptly and is never deferred. Outcome data and safe internal failure categories remain private, with no graph details disclosed before authorization.

Then, exactly once:

1. Enter the final transaction and acquire the registry READ gate through existing B03 identity/root authorization.
2. Apply the fixed concrete-selector minimum with existing access.require on that exact handle: EDIT for HistoryRequest/WorkerRequest; no second acquisition for CurrentReadRequest. This step is independent of whether a configured provider exists.
3. If one actual configured complete provider was resolved, invoke its authorizeRead on that same handle. If none exists, do not substitute or construct one; the fixed minimum is sufficient to order the safe Fatal result.
4. Decide the stored outcome. READ or EDIT denial takes precedence over any stored failure. Fatal returns a safe unavailable/protocol failure. Ineligible follows R1's authorized current-only routing. Neither enters complete prelockRead/prelockCurrent, source receipt acquisition, durable task/attempt acquisition, pin, or open.
5. Only Prepared proceeds through the exact prepared graph, complete source and task union, finalization, commit and private open. There is no late discovery, parse/hash under identities/roots, second ACL, second gate, retry, or provider replacement.

The trusted classification is deliberately available in JdbcFileRegistry, whose existing access collaborator already owns the genuine B03 gate. No additional public authority getter or action-bearing API is needed. Public request objects remain forgeable claims whose necessary permission can only increase; actual production operation semantics remain privately validated.

## Generic and legacy compatibility

The original minimal AVAILABLE-file-to-meeting lookup and NotFound behavior remain. Generic complete discovery is current-only and therefore uses the same READ minimum; provider resolution and discovery failures are captured before that one gate exactly as above. The complete path never tries another provider after acquiring roots. Genuine current Ineligible may use an independently valid unchanged legacy reference after authorization and original legacy checks; Fatal never permits that fallback, including ambiguous registrations.

The unchanged legacy constructor/download path has no complete-provider budget, selector, preparation or authorization-hook prerequisite. New planned complete reads issued to a legacy-only registry are recognized but unsupported: they still acquire the one READ gate and required same-handle EDIT before safe Fatal, with zero complete downstream locks/opens. This does not convert any legacy registration into B06 authority. Explicit complete assembly still rejects a legacy INPUT_BATCH_FILE override and missing/invalid configured budgets before immutable components escape. Duplicate/multiple complete composition in generic discovery is a deferred Fatal rather than a provider selection rule.

## Required prospective tests

For each of CurrentReadRequest, HistoryRequest and WorkerRequest, test valid known-meeting selectors against missing registration, legacy-only registration, and resolution/ambiguous-composition failure. Exercise outsider, revoked member, ordinary member and owner. Outsider/revoked READ denial must precede Fatal; for history/worker, ordinary-member EDIT denial must precede Fatal; eligible owner receives only safe Fatal. Current ordinary member can reach safe Fatal after READ. Assert one identity/root acquisition and zero complete domain/source/task/attempt acquisition and zero opens. Assert actual configured authorizeRead is never called on a substitute when absent, and cannot weaken the fixed requirement when present. Reject custom ReadRequest/action spoofing; prove CurrentReadRequest cannot enter historical/live-worker semantics. Retain all R1 genuine-ineligibility versus fatal legacy-fallback tests and successful exact-graph tests. Legacy-only download must remain green without configured complete limits or preparation.
