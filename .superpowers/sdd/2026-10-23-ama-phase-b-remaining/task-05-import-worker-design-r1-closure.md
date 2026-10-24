# Task 5 R1 closure record

2026-10-23. DESIGN ONLY. Original and independent review preserved byte-for-byte. This record accompanies task-05-import-worker-design-r1.md; no source/test implementation or execution, DB/Docker/privateenv operations, Git mutation, subagents or runtime activation.

## Exact artifacts

| Artifact | Raw SHA256 |
|---|---|
| task-05-import-worker-design.md, unchanged original | 26a5b94befb57b5802b37b7dcd5e0679fc42add7f6d0e9a69602da871556d06f |
| task-05-import-worker-design-review.md, fully read independent findings | 90f4d86d43a0d0d87889d7a33c1e2e80696a389763853f298ffcdd5679feb6a7 |
| task-05-import-worker-design-r1.md | 7abe66e4fb2ae304df29ad62d374455c86a928d41fa4ba03f2d78fd268a284f4 |

## Finding closure

### T5DR-01 / Important P2

Review requirement: "freeze the exact private queue-owned after-task path" and bind "exact configured issuer/DataSource/actual holder, complete preparation/full spec, current task/attempt, prelocked graph and one-use completion phase."

R1 section1 retains genuine DomainScope in private ValidatedLease and mints queue-private AfterTask only after actual task/unfinished attempt validation. Private production carrier.get ALWAYS denies; it is intended rows only. Exact queue/policy identity is constructor-bound by package-internal pre-exposure factory with no public setter/second domain issuer. Registered private bindCompletion consumes same real PreparedDomain/issuer/holder/full scope and returns same-call private invocation. Queue alone advances BINDING/BOUND/WRITING/VALIDATING/SETTLED/CLOSED, enforces one-use, and passes exact wrapper/carrier/invocation/result identities. Writer and registered actual-output validation use this same confined invocation, no last-plan cache or reacquisition. Core envelope is explicitly data-only lifecycle admission, never authority. Existing unrelated Task1 trusted callbacks and Runnable order unchanged.

Prospective proofs in R1 section4: direct returned callback in arbitrary sameDS transaction; foreign queue/policy/issuer/source/holder; ended/suspended scope; wrong task/fullspec/attempt/phase; repeated invocation; public fake envelope. All fail before INSERT. Authentic path writes once, validator observes actual complete output and exact same plan; interleaved tasks cannot swap plans; validator/flush/update-count faults roll everything back.

Disposition: design closure proposed; independent re-review required, no implementation PASS.

### T5DR-02 / Important P2

Review requirement: "No renewal may hold the monitor while waiting for database locks held by a completion that needs that same monitor."

R1 section2 chooses one explicit phase-and-drain protocol. Tick selects under monitor, JDBC runs OUTSIDE monitor, exact tick generation publishes under monitor. Preparation -> DRAINING_RENEWAL disables queued/new ticks and waits for already-running tick WITHOUT DB locks and with condition wait releasing monitor. Only then DISPATCHED/completing selects one queue call. Callback admission briefly checks DISPATCHED/lost/closed under monitor after genuine DB checks; no renew is in flight or allowed to start, no monitor held across writer/DB. Close wakes drain promptly and suppresses pre-admission work. Unknown completion ACK never ordinary-completes twice. Fresh DB expiry after waiting stays mandatory. Lease20s/heartbeat5s/poll5s unchanged; no second timer. Task7 future MARKING uses same completed drain before ownership handoff; no external behavior activated.

Prospective deterministic barrier proofs in R1 section4: already-running renew prevents any completion DB entry; completion holding first root makes queued heartbeat skip without JDBC; close before callback obtains monitor and denies; genuine false renew before admission suppresses; fresh expiry after drain denies; admitted close is atomic commit/rollback; terminal success plus late tick does nothing; unknown ACK no second completion. No proof relies on DB timeout to break an invented cycle.

Disposition: design closure proposed; bounded re-review must also accept narrow Task7 ordinary-renew ordering refinement.

## Additional accepted review precision

R1 section3 requires actual unfinished task_attempt for renew/Runnable/Supplier and exact1 terminal update. Attempt0 requires no fabricated row. Expired reclaim checks/ends old attempt once; at maxAttempts task terminal helper must not end it a second time. Injected0 update rolls back output/task/attempt/events. This is internal integrity accounting, not a broad queue refactor.

R1 section5 explicitly retains Task3 pre-gate R2 permission floor, measured parser ALL decoded UTF16 and unchanged goldens, parseBudget propagation through complete(), exact original archive/config after defaults change, transport-boundary cancellation registered before success/error body read, actual atomic normal/watchdog close and S3 exactly-once permit release, real local+S3/Worker/MySQL/fresh-JVM evidence and fixture custody. Local header timeout or an unbounded blocking watchdog is still insufficient; no transport proof claimed.

## Status

Two P2 findings addressed in proposed design R1; pending independent bounded re-review. No product choice/newDDL/production activation. Original/review unchanged; only two new requested R1 artifacts written. B01 IMM PARTIAL/B04 Q401 UNKNOWN/company/HA/native-I/O/capacity boundaries retained.
