# Retained execution windows

Counts are tests/failures/errors/skipped. Zero XML is setup/compilation, not a passing test. Windows are not additive coverage. Actual command, Maven flags, elapsed time and source hashes are in each receipt/source pair.

| Window | Exit | XML counts | Selector |
|---|---:|---|---|
| atomicity-01 | 1 | 3/1/0/0 | `Task06FixAtomicityIT` |
| atomicity-02 | 1 | 0/0/0/0 | `Task06FixAtomicityIT#freshAuthorizationLifecycleEpochAndCancellationWinAfterSnapshot` |
| atomicity-03 | 0 | 1/0/0/0 | `Task06FixAtomicityIT#freshAuthorizationLifecycleEpochAndCancellationWinAfterSnapshot` |
| atomicity-exact-holder-01 | 0 | 1/0/0/0 | `Task06FixAtomicityIT#operationFaultMatrixRollsBackRowsReceiptsAuditFencesAndOutbox` |
| atomicity-membership-01 | 0 | 1/0/0/0 | `Task06FixAtomicityIT#removedEffectiveMembershipBetweenSnapshotAndFinalCannotReturnContent` |
| final-integration-01 | 1 | 121/1/0/0 | `Task06*IT` |
| final-units-01 | 0 | 254/0/0/0 | `*` |
| handles-01 | 0 | 1/0/0/0 | `Task06FixHandlesIT` |
| https-01 | 1 | 0/0/0/0 | `Task06FixHttpsIT` |
| https-02 | 1 | 2/2/0/0 | `Task06FixHttpsIT` |
| https-03 | 1 | 2/2/0/0 | `Task06FixHttpsIT` |
| https-04 | 0 | 2/0/0/0 | `Task06FixHttpsIT` |
| indexed-provenance-green-01 | 0 | 1/0/0/0 | `Task06FixProvenanceIT` |
| indexed-reader-green-01 | 0 | 3/0/0/0 | `Task06IndexedTasksIT` |
| indexed-reader-green-02 | 0 | 3/0/0/0 | `Task06IndexedTasksIT` |
| indexed-reader-green-03 | 0 | 4/0/0/0 | `Task06IndexedTasksIT` |
| indexed-reader-red-01 | 1 | 1/1/0/0 | `Task06IndexedTasksIT` |
| inherited-concurrency-green-01 | 0 | 5/0/0/0 | `Task06InheritedMeetingConcurrencyIT` |
| integrity-green-01 | 0 | 2/0/0/0 | `Task06FixIntegrityIT` |
| integrity-red-01 | 1 | 2/2/0/0 | `Task06FixIntegrityIT` |
| locks-01 | 0 | 2/0/0/0 | `Task06FixLocksIT` |
| provenance-01 | 1 | 1/1/0/0 | `Task06FixProvenanceIT` |
| provenance-02 | 0 | 1/0/0/0 | `Task06FixProvenanceIT` |
| provenance-03 | 1 | 0/0/0/0 | `Task06FixProvenanceIT` |
| provenance-04 | 1 | 1/1/0/0 | `Task06FixProvenanceIT` |
| provenance-05 | 0 | 1/0/0/0 | `Task06FixProvenanceIT` |
| task-query-plans-01 | 0 | 1/0/0/0 | `Task06TaskQueryPlanIT` |
