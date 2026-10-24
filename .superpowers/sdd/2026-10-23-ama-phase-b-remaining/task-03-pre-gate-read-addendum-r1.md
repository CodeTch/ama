# Task 3 pre-gate read addendum R1: authorization-first failure disclosure

2026-10-23. Supersedes only the failure routing and affected signature list in the original addendum SHA a45b8390d5ea27216de6407fb35ebc17eb0cfa365bbd6edc12c4ec16792daa57. Addresses PRD-01 from independent review SHA 44fd8346289d8b3bb1f5a85f6012cb6a5a7a5dcf779d452ac97b737b8733eb3c. Original bounded discovery, exact comparison, no hash under roots, data-only preparation and postcommit private opening remain binding. No implementation approval.

## Mandatory extension signatures

```java
interface ReadPreparation {} // private implementation, data only
ReadPreparation prepareRead(Actor actor, String meetingId, FileRegistry.ReadRequest request);
void authorizeRead(FileRegistry.LockedMeeting gate, String meetingId, FileRegistry.ReadRequest request);
Object prelockCurrent(FileRegistry.LockedMeeting gate, String meetingId,
    FileRegistry.Reference reference, String requestedFileId, ReadPreparation prepared);
ReadPlan prelockRead(FileRegistry.LockedMeeting gate, String meetingId,
    FileRegistry.ReadRequest request, ReadPreparation prepared);
```

All are abstract. authorizeRead is a registered-policy operation classifier and same-handle authorization check, independent of preparation success. It receives no preparation or detached graph. For recognized history/worker requests it requires owner EDIT using the same genuine B03 handle; for current it requires READ on that handle. It must not discover/acquire domain/source/task rows, compute hashes or parse. Unknown/malformed selector is invalid input and grants nothing. A caller cannot replace the configured policy or choose a flag to suppress required EDIT. Scope minting still rechecks exact private operation/request/actor/meeting/preparation lineage and complete actual graph. Existing legacy two-method policies are unchanged.

## Registry-owned bounded outcome

A private local outcome (never an API parameter or return value) has exactly three states: Prepared with the configured provider's private data preparation; Ineligible containing only a genuine FileContracts.NotFound from current-eligibility discovery; Fatal containing a safe closed internal exception/category for budget, profile, protocol, missing preparation support, unsupported complete-provider composition or other preparation error. Do not wrap arbitrary failures as NotFound. Do not expose graph IDs/raw DB strings, causes or original error details to clients. Preparation may not create a holder-bound scope.

Cancellation/interruption is not stored as an outcome: stop promptly with its existing cancellation semantics; no final authorization work, retry or reusable partial preparation follows. Invalid syntax/ambient transaction rejects immediately. Infrastructure failure remains closed and cannot be converted into successful authorization.

## Planned single-gate sequence

For syntactically valid recognized requests with a known target meeting, resolve the actual configured complete provider and run bounded prepareRead outside a transaction. Capture non-cancellation preparation failure as the private outcome. Enter exactly one final transaction and acquire registry READ gate. Invoke the configured authorizeRead on that same gate (owner EDIT for history/worker). Only after these checks may the registry decide the stored outcome. Authorization denial takes precedence over missing graph/profile/budget/protocol outcomes. Fatal fails safely; Ineligible becomes NotFound; neither enters prelockRead or any domain/source/task acquisition. Prepared proceeds with exact prelockRead and original complete protocol. No second gate/identity acquisition or automatic retry, and no hash/parse/missing-root discovery under the gate.

## Generic current sequence and alternatives

Keep the original minimal AVAILABLE-file-to-meeting lookup and its existing NotFound when no meeting is selectable. Legacy-only configured registry retains original download behavior and has no B06 preparation/budget prerequisite. For configured complete-provider paths, bounded complete reference/provider discovery uses explicit deployment limits (LIMIT remaining+1 and reject excess before unbounded allocation). Capture discovery/unsupported-composition errors as Fatal rather than returning them before READ. On one supported complete provider, call prepareRead(CurrentReadRequest) outside the transaction and capture its outcome. At most one configured complete provider instance is supported; no public selector resolves a different instance.

Acquire the one READ gate before observing Fatal/Ineligible. Fatal aborts after authorization even if a legacy reference would otherwise qualify. For Prepared, re-read the bounded full reference set and compare exact observations before domain acquisition; changed/extra references fail without choosing new provider or preparing again. For genuine current Ineligible, final authorization still precedes outcome routing; skip that complete provider and allow only an independently valid unchanged legacy reference through its original prelock/validate path. That legacy alternative must independently complete its authorization, original/reference checks and commit before opening. A failed complete preparation itself authorizes no domain/source/task acquisition, pin or open. If no valid alternative exists, return NotFound after READ. No catch-all fallback.

## Prospective closure tests

Compare outsider, revoked member, current member and owner against valid graph, no-current graph, unknown profile, reference overflow and multiple-provider composition: known-meeting READ denial remains identical and occurs before graph-specific observable failure. For history/worker, ordinary member EDIT denial precedes every stored prep failure. Verify genuine complete ineligibility plus valid unchanged legacy reference succeeds only after READ; fatal complete error plus that legacy alternative still fails with zero opens. Legacy-only construction performs no complete preparation. Assert cancellation halts immediately, no fallback/reuse; failed prep causes no domain/source/task locks; exact successful graph is rechecked; hash/prep precedes identities; one gate; finalizer/commit precede open. All original/R1 source-holder, full-set, lock-union and producer/live-lease distinctions remain required.
