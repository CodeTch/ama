# Task 5 fix01 — source cancellation, best-effort shutdown and CV1

Status: fix01 implementation and scoped verification delivered for root audit and original-reviewer rereview. This is the same Task5 round1/5; no adoption or production qualification is claimed.

FIX_BASE is `857102b917bbef77c38fae3211e3a65207a98c6c`. Author resumed at docs-only checkpoint `f98980543415ba17f0102a9c85f49452c5611527`, branch `codex/phase-b-completion-20261023`, worktree `/home/tanren/ama/.worktrees/phase-b-completion`. The delivery commit is the commit containing this report and the explicit source/evidence manifests. Root retained HEAD/index ownership freeze while this sole author implemented; root's unrelated resume document is excluded from staging. No new fix round, subagent, DDL, main mutation, push, container, credential or PKI was created.

## T5SR01

`TranscriptImportRuntime.prepareRaw` now establishes one bounded `SourceReadLifetime` before facts/archive/source preparation. It registers the actual Worker cancellation once and uses the absolute original batch deadline. The scope contains only abort registrations: no lease, source selector, plan, issuer, holder, capability or output permission. Its thread-local binding only finds the current transport lifetime. Carrier cancellation continues to depend on the original Worker signal and absolute deadline, so normal scope closure cannot invalidate a successfully prepared carrier or grant completion.

LOCAL registers a race-safe request controller before `sendAsync`, then attaches the real future and response body. Cancellation before assignment cancels/closes the subsequently assigned object; body ownership is released once. The existing five-minute request/body and ten-second connect settings remain. No body-reader thread was introduced. Header, error-body and returned-stream work all retain the registration until release.

S3's existing Apache5 builder is wrapped through `SdkHttpClient.Builder.buildWithDefaults`, preserving SDK-owned HTTP client closure. Each actual `ExecutableHttpRequest` is registered before `call`; cancellation invokes its real native `abort`. Registration remains through response-body closure, covering binding/control/HEAD/error work before the data stream returns. Existing admission, socket/API/connect limits, HTTPS production policy and retry count are unchanged. Both transports still execute only after the genuine source authorization transaction commits; no authority check was bypassed or weakened.

Meaningful RED: `red-local-batch-clean-fixture` and `red-local-renewal-clean-fixture` each failed the actual preparation-exit assertion while the authenticated partial503 route remained stalled. The former used a one-second batch against a five-minute transport; the latter expired the exact current persisted lease, so the actual queue renewal returned false. `red-s3-control-open` likewise failed while a genuine S3 control GET was stalled, after the common scope existed but before S3's transport was bound.

GREEN covers actual HTTP upload/completion/intake READY, the registered queue and existing Worker preparation, not a permissive policy. LOCAL's final three cases cover partial503 batch, actual renewal loss, and headers withheld entirely. Their observed total elapsed times were 1140/1212ms for the one-second batch cases and 5226ms for real renewal loss; fixture release remained untouched at each assertion. S3's three open cases cover control-body batch, actual renewal loss and SDK error-body, plus a fourth headers-withheld case. All preparations exited before proxy release; renewal-loss cases observed exactly one Worker abort callback. No RAW or typed output appeared. Owned Worker activity was drained before teardown.

## T5SR02

`WorkerLoop.close` now isolates each abort error, cancels every remaining execution, shuts down every scanner/heartbeat/worker executor, and joins them under one shared two-second deadline. It preserves the first failure with at most eight suppressed cleanup errors and restores interruption after cleanup. Execution callbacks remain detached under their execution monitor and invoked outside it. Repeated close is safe after failed cleanup.

The actual `TranscriptWorkerRuntimeProvider.Runtime.close` always attempts `definitions.close` after loop failure, preserves the first failure, and closes once. A checked backend error retains its cause.

`WorkerShutdownTest` runs two active real contextual executions. One registered abort throws, the other records cancellation; the actual provider Runtime is assembled through its private constructor using test reflection. The synthetic queue is lifecycle scheduling only. `red-shutdown` failed both tests: ordinary close observed abort1/2, backend close0/1 and unterminated executors; the interrupted case also failed backend/executor cleanup. `green-shutdown-compile-corrected` passed2/2. Assertions include no writer/quarantine, every executor terminated, backend once, first-error identity, repeated close and retained interruption. Finally rescue shut down the old RED executors so no test JVM was abandoned. Both cases also pass within final233 units.

## CV1

`Task05FixCarrierIT` passed2/2 on final product source. Two real HTTP accepted tasks have distinct private carriers and reversed plans `[Alpha,Beta]` and `[Beta,Alpha]`. Both cross-lease carrier substitutions are rejected before any RAW INSERT. An arbitrary public result supplier is also rejected. B then A genuinely completes with each original plan, meeting and source order.

For BODY, SOURCE and ORDER, the real JDBC probe intercepts the actual private validator's metadata SELECT after the RAW and both segments have been inserted. It changes same-length body `Alpha` to `Omega`, changes source to the other genuine file, or shifts persisted ordinals by10. The actual validator rejects each corrupted state. RAW/task output/current attempt/audit/outbox rollback is asserted before the same original carrier/lease genuinely completes. These are actual new-row mutations and validator executions, not a replacement policy, fake output validator or public mutation grant.

## Verification and source windows

The final actual command was `python3 tools/checks/run.py unit '*'`, invoked through the retained fix01 runner with a literal argument list. `final-all-units` executed **233 / 0 failures / 0 errors / 0 skips**, once. `final-source-seal.json` equals that run's complete source/resource/POM hash map. No backend product or test source changed afterward.

There are **16 distinct passing integration cases on the exact final product source**, counted without overlapping reruns:

| Evidence directory | Cases |
| --- | ---: |
| green-s3-open | 3 |
| s3-before-headers | 1 |
| cv1-genuine-carriers-and-validator | 2 |
| final-affected-regressions | 7 |
| final-local-lifetime-custody | 3 |

The seven affected regressions are genuine complete LOCAL/S3 HTTP→Worker→RAW2, LOCAL transport/authenticated health2, actual S3 streaming/control/error2 and the inherited TEST_INFO scheduler cancellation1. That synthetic scheduler policy proves lifecycle only. The standalone finite maintenance test1 is excluded from business counts. Focused shutdown2 is already included in233 and is not added again. No unaffected whole cohort was rerun.

`source-windows.json` lists exact product/test differences for every run. The final affected7 and final LOCAL3 have exact final entire backend source. Earlier CV1/S3/header runs have identical final product, with later additions/refinements to test-only files. The first LOCAL GREEN preceded only the separate S3 wrapper; it is superseded by final LOCAL3 for the nonoverlap count. `test-command-index.json` records actual command, exit, XML suites/cases and counts for every runner invocation. A hash seal was captured before every run. `red-source-reconstruction.json` supplies35 additive exact focused RED code files matched against those actual pre-run hashes; it explicitly identifies post-run derivation rather than falsely claiming standalone source files were captured at execution. Final standalone own sources are under `final-owned-source/`.

## Resources, maintenance and historical qualifications

Root ACK was based on `resume-20261024-root-selected-resources.json`, SHA256 `8612d0d0486c8c9cac11f7dabf51b15ac4b197d26c5a886083b6bf92e30eb690`. This author verified its hash before runtime work. Only the retained exact MySQL `eefdd9a984ee00fe6ca886168b29a6ce4f3b405467b8d3204240d4e1a8dc7502` / owner remaining-20261023 /127.0.0.1:46653 and RustFS `a91f5946d5f619335d5cfb6bf2295b1df46abbb4a75b2db8feccaf637bcb7d51` / owner ama-task05-5237a2db-e8d5-4cbe-b357-b35df5cfcba5 /127.0.0.1:45501 were reused. Private env and PKI remained under their approved this-run roots. New native/archive paths were exclusive UUID descendants; S3 tests used fresh UUID buckets on the retained fixture. Tests/Workers used512MiB/2CPU and native children64MiB/2CPU. No reset, migration, resource recreation or old fixture setup was invoked.

`final-selected-resources.json` uses explicit selected Docker `--format` fields, never Config.Env or full inspect. Identity/image/owner/mounts/effective ports/memory+swap/CPU/PID/running/OOM match the root resume proof. Shared containers remain running. `final-known-processes.json` checks only30 exact recorded native/test PIDs; all were absent. No global process listing or broad kill was used. Per-native start/close receipts contain actual command/PID/port/exit. LOCAL server and S3 proxy executors terminate; the test JVM exits also bound all per-JVM transport/timer threads.

The22 new genuine intake tasks have7 genuine successful outputs and15 exact owned negative-fixture maintenance receipts. This count is from this run's finite acceptance/maintenance/positive evidence, not a new whole-database census and not the old75-task cohort. Maintenance locks/rechecks the full selected row/current attempt, exact acceptance actor/meeting/input/upload/file linkage, no RAW/output/external operation/execution/reservation; it ends only that attempt and marks only that task CANCELLED after the real behavior was observed and the Worker stopped. Maintenance is not business cancellation or a substitute for positive completion. The inherited TEST_INFO fixtures are separate.

All setup and failed runtime outputs remain. Explicit qualifications:

* First shutdown GREEN had a missing helper brace; first LOCAL attempt used the wrong TaskLease accessor. These compilation failures are not business RED.
* First two-case LOCAL run retained one real non-exit observation but teardown serialized `Instant` incorrectly, masking the primary assertion; the second case encountered that retained task. Two subsequent finite-maintenance setup attempts used nonexistent upload actor metadata and unsupported `LocalDateTime` serialization. No mutation occurred in those failed maintenance attempts. Corrected maintenance prelinked exactly those two new accepted IDs, preserved before/after and executed code, then terminated them. The standalone one-time test was archived and removed from final discovery.
* Earlier LOCAL helper executions stopped the temporary route before maintenance; those actual historical timings remain qualified. Final LOCAL3 executes Worker join/zero active→guarded maintenance→loop-close receipt→finally route stop. `final-local-custody-order.json` records actual file times, executed source order and subsequent exact closed-port observations. It does not invent an independent historical timestamp for server.stop.
* Three exited read-only javap interface inspections lacked explicit64MiB/2CPU JVM flags; two also suffered PowerShell `$Builder` expansion and returned only outer interfaces. They are inspection/setup qualifications, separate from bounded actual test/Worker/native evidence. No backdated bound is claimed.
* The first final-unit launcher expanded `*` before runner argument parsing and exited with ValueError before any test or evidence directory. The literal subprocess invocation then ran the sole actual final all-unit suite. First final resource guard compared configured dynamic blank HostPort to root's effective46653; its source and selected diagnostic are retained. Using NetworkSettings.Ports corrected that comparison; no resource identity changed.
* Expected Worker failure/interruption warnings in negative tests and existing compile warnings remain in logs. No failed run is overwritten or represented as a clean aggregate. Historical CV2 broader-inspect execution remains qualified in the original report/review; this round never called it or changed those records.

## Seals and handoff

`explicit-source-paths.json` lists20 own backend paths:7 product and13 test/helper files. No POM/schema change. `forward-copy-manifest.json` and exact diffs retain original inherited tests/helpers untouched. `old-evidence-verification.json` compares all552 protected old evidence files; none changed. The original W report's full byte prefix is preserved and this report is appended separately. Formal SQL/history/parser goldens are unchanged.

`evidence-manifest.json`, `final-source-seal.json`, `nonoverlap-counts.json`, `source-windows.json`, `test-command-index.json`, `final-selected-resources.json`, `final-known-processes.json`, `red-source-reconstruction.json` and `secret-scan.json` are the audit entry points. Exact private credential/PKI-password values and private-key PEM patterns are scanned over new public evidence, own source/scripts and both reports; only counts/paths are published. Root independently audits seals, history/custody and source, then dispatches the original reviewer for T5SR01/T5SR02/CV1 and introduced breakage. Task6/7/8 and publication/main/push remain unactivated.
