# Task 3 pre-gate read preparation addendum

2026-10-23. Proposed bounded technical closure of an implementation interface gap; no runtime acceptance. Original/R1 authority design remains binding. Canonical producer-key computation cannot occur under roots. Existing CompleteReadPolicy only exposes under-gate hooks, which is insufficient for generic current download.

## Exact proposed additive contract

Within CompleteReadPolicy, all new methods are abstract:

```java
interface ReadPreparation {} // data only, no holder/byte/write permission
ReadPreparation prepareRead(Actor actor, String meetingId, FileRegistry.ReadRequest request);
Object prelockCurrent(FileRegistry.LockedMeeting gate, String meetingId,
    FileRegistry.Reference reference, String requestedFileId, ReadPreparation prepared);
ReadPlan prelockRead(FileRegistry.LockedMeeting gate, String meetingId,
    FileRegistry.ReadRequest request, ReadPreparation prepared);
```

The last signature replaces the unimplemented new extension's three-argument prelockRead. Existing legacy FileReferencePolicy is unchanged. Add `FileRegistry.CurrentReadRequest(String referenceKind, String fileId) implements ReadRequest` as a public selector. History/worker selectors stay operation-specific. They remain claims only. The original contextual prelock for first-bind and complete members remains unchanged. INPUT_BATCH_FILE context-free prelock rejects absent preparation; generic registry dispatch explicitly uses prelockCurrent on its configured complete provider.

Private authority preparation contains its issuer, exact actor/meeting/operation selector and bounded immutable observations: current pointer (for current), explicit revision chain (for history), fixed input/batch/profile/member ordering, actual producer task/full spec, expected canonical producer key and original owner observation where required. It is not transaction authority. A private final scope is minted only after complete fresh B03 authorization and exact locked graph equality. Worker request lease remains an untrusted observation; live task/attempt/DB-expiry check stays after all sources/tasks.

## Registry sequences

Planned read rejects ambient transactions, resolves the configured exact CompleteReadPolicy, calls prepareRead outside transaction/identities, then begins the existing single final transaction. Pass the same preparation to prelockRead. Registry does not accept caller-supplied preparations. Under the gate, policy checks exact private issuer/request/actor/meeting and the complete newly locked graph; stale/foreign/mismatched observations fail without late root discovery. The existing source union, all-task prelock, once-only finalization, private local pin, commit and outside-transaction open follow unchanged.

Generic download first performs bounded nonlocking meeting/reference/provider discovery. It obtains all reference rows (LIMIT budget+1, reject excess; never first-N authorization) and rejects distinct complete-provider instances before source acquisition. Legacy-only operation retains old behavior. For the one complete-provider instance, registry calls prepareRead with CurrentReadRequest outside the final transaction. After acquiring the one READ gate it re-reads the complete reference list and rejects changes rather than preparing/hash-computing under roots. prelockCurrent consumes that exact configured preparation. Complete members share the same authentic private scope. Legacy alternatives still follow their two-method contract. No callback/global/ThreadLocal token cache is introduced.

## Acquisition and bounds

Preparation owns no DB locks and opens no storage/network/archive stream. Query only necessary fixed-width graph/task columns and exact bounded config observations. All collection growth is admitted against explicit configured row/member/edge/depth/UTF8/scalar/UTF16/aggregate limits; cancellation/interruption checked throughout. Any necessary LONGTEXT uses SQL length-gated projection and bounded character streaming (the projection itself must enforce bounds so a measure/read race cannot transfer oversized text). No display_name or provider-token transfer. Producer hashing occurs only after detached bounded discovery and before identities. Under roots, no canonical hash or strict JSON/archive work; exact immutable values/complete membership/parent edges/profile/full-spec are rechecked instead. Missing/changed/additional graph facts fail closed. Nothing about a valid preparation makes a pending/RAW source generic-readable.

## Compatibility and required evidence

Legacy providers and legacy constructor behavior remain intact; new methods cannot inherit bypass defaults. New complete assembly requires explicit finite budget and actual registered provider. Tests must show: hash/preparation outside DB transaction; initial identity starts afterwards; changed pointer/owner/parent/member/profile/spec between phases fails before open; foreign preparation/selector/actor rejects; generic effective reads retain producer grammar, pending/history do not; complete reference overflow rejects before allocating full result; multiple complete providers fail before sources; successful finalizer and commit still precede the only open; no second identity/gate acquisition. Existing R1 full-source/task ordering and rollback tests remain required.
