# Production Publication Preparation Renewal — Scoped Evidence

Formal B remains incomplete; M1-Demo now has separate controlled-local acceptance. This closes a specific production preparation defect; it does not reconstruct every cause of the earlier slow publication or close the remaining B-QUAL obligations.

## Trigger and causal reproduction

PublicationGraph compares the complete task union during production publication preparation. A successful ordinary queue renewal changes only the current RUNNING task's expires_at. With the original source, that legitimate change invalidated the exact discovery/capture comparison and preparation rejected before the marker. A new genuine daily-local submission was produced through the UI, with only the exact owned ordinary Worker stopped. No SQL business rows, fake output or historical B-L5 resource was used.

Fixture: meeting `91e1ab53-7bf9-410f-ad5d-745ffa9e1217`, publication `d5c2ed09-d559-4113-8a64-52353e9958cb`, task `f683db53-f7b8-4ce2-9933-a90de5fa9fb2`.

`tools/dev/java/ama/localdev/LocalPublicationRenewalCheck.java` builds the existing production authority/queue/service composition. A transparent DataSource pauses after discovery outside roots, then separately after the first actual capture COMMIT. The orchestrator performs a successful genuine queue renewal and proves state/owner/attempt unchanged while expiry advances. The old production service rejects both calls with PROVENANCE_UNAVAILABLE, with unchanged meeting/publication and zero external execution, publication attempt or document mapping (`publication-renewal-red.log`). The v1 helper is preserved as `publication-renewal-red-helper-v1.java` in the state evidence directory.

The initial fix still failed (`publication-renewal-green01.log`); it assumed the metadata reader always supplied an ISO String. A read-only probe confirmed actual Connector/J DATETIME values are LocalDateTime. The native-type test causally failed, then passed after support for native LocalDateTime and normalized ISO values. Do not describe Green01 as passing evidence.

## Change and limits

PublicationGraph now compares all four task-union tables before issuing an internal signal. Only the selected RUNNING task's strictly forward expiry change qualifies; owner, attempt, every other task column, every other task and all attempt/operation/execution metadata remain exact. Publication controls remain exact. An eligible signal rejects and unwinds the entire acquisition; the stale graph is never accepted.

JdbcPublicationService.prepare permits at most three total fresh captures, sharing the original cumulative budget ledger. Another capture may only be admitted within 15 seconds of preparation entry. This is an admission limit, not a 15-second completion timeout; each capture retains the existing finite resource/deadline limits. Every capture repeats roots, sources, current authorization, binding, manifest and fingerprint checks. Lookup preparation has a null lease and retains strict comparisons. Marker admission and one-shot send are unchanged. Worker warnings now include the typed Minutes failure enum, task ID and attempt, without exception messages or payloads.

## Genuine Worker GREEN

The separate `tools/dev/worker-java/ama/localdev/LocalPublicationWorkerRenewalCheck.java` runs actual WorkerLoop, ExternalExecutionSession and KnowledgePublicationHandler. On the original task's third and final permitted attempt, two preparation probes reproduce the same renewal interleavings and both return PREPARED without pre-marker business effects. The wrapper then delegates to the actual production handler and its genuine session for marker, transport and observation.

`publication-renewal-worker-green.log` confirms SUCCEEDED with one external execution and one document mapping. Independent review also verified one submission and one publication attempt; neither preparation-only attempt 1 nor 2 has an external execution. The third attempt is deliberate qualification history, not evidence that the fixed ordinary worker intrinsically needs three attempts.

Six focused tests cover exact current expiry eligibility, native JDBC representation and rejection of other task/owner/attempt/binding/inventory/union/time changes. Applicable contracts/core/adapters/API/Worker unit regression: **531 tests, zero failures/errors/skips**, in `publication-renewal-broad-java02.log`. The first broad run had two setup errors because an existing canonical-vector test requires an output directory; a fresh private local vector-output directory was supplied. No old operational evidence window or service was resumed.

One independent scoped review, `publication-renewal-source-review-20261029.md`, found no actionable P2-or-higher defect in this production diff. It distinguishes static boundary inspection from dynamic proof. The later genuine-MySQL bounded run (`publication-renewal-bounded-green02.log`) also passed: both positive barriers prepare without effects; three successive ordinary renewals exhaust the three-capture bound with PROVENANCE_UNAVAILABLE and zero effects; wrong owner, attempt and input binding likewise reject without effects. The exact ordinary Worker was then resumed and the same queued publication completed, including browser document/ACL checks (`bounded-publication02.log`). The first bounded run failed to obtain an immediately available claim; it is retained and is not counted as passing. A bounded 20-second claim poll handles the legitimate optional-empty result.

## Remaining boundaries

The historical first publication's 56.85-second creation-to-claim delay remains unexplained. This reproduction proves a real self-renewal preparation defect and its scoped fix, not that it was the sole historical cause. Broader maintenance/reconciliation coexistence, bounded saturation, exact budget/lock/transaction boundaries, authorization races, crash matrices and final branch review remain B-QUAL work. Prototype and browser evidence use separate records. All normal six daily services were restored after the genuine Worker qualification.
