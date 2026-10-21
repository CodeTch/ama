# B03 full-domain independent SPEC and quality review

Date: 2026-10-22. Reviewer: independent b03_domain_review agent; no authorship or child delegation.
Owner: /home/tanren/ama/.worktrees/phase-b-meeting-domain, codex/phase-b-meeting-domain.
Exact range: 0d2079cd64e5b61bc5787ca5adfaa782e5cd4a40..9f2da58e2bee76741e5b5aab2c22d83c7b46ab67.
Both commits aa543859e7b689a2fa11f0909d444048718ea754 and 9f2da58e2bee76741e5b5aab2c22d83c7b46ab67 are included.

## Verdicts

**SPEC: NOT PASS — one P2 API-contract mismatch requires correction.**
**Quality: CHANGES REQUESTED — the same finding blocks adoption of this exact delivery.**
No additional actionable finding was established in the owned implementation. This is a full-owned review, not approval based only on the author's report or green tests.

## Finding B03-R1 — [P2] Declare the error codes actually emitted by meeting routes

Primary location: contracts/openapi/meetings.yaml:270–287, especially the BusinessError references at lines 275, 281 and 287.
Related production locations: backend/api/src/main/java/ama/api/MeetingErrors.java:11–14; the meeting routes' 403 references to identity.yaml#/BusinessForbidden; contracts/openapi/identity.yaml:183–200; web/src/shared/api/generated.ts:233–241.

The new handlers return MEETING_FORBIDDEN, MEETING_CONFLICT, CATEGORY_DICTIONARY_UNAVAILABLE and REQUEST_TOO_LARGE. Every declared meeting error response ultimately uses identity.yaml#/BusinessError, whose closed code enum only permits UNAUTHENTICATED, FORBIDDEN, INVALID_ARGUMENT, IDENTITY_UNAVAILABLE and IDENTITY_PROVIDER_UNAVAILABLE. Thus normal meeting errors violate the published response schema. The generated TypeScript faithfully reproduces this incorrect enum: a consumer cannot type a comparison/branch against MEETING_CONFLICT (or the other three codes) without an assertion/widening, and runtime schema validation rejects these legitimate responses.

Reproduction from reviewed code (no new runtime execution performed): create an owned meeting, save expectedRevision=0, then send a second save with expectedRevision=0 and a different key. The existing staleRevisionPreservesCurrentValue case exercises the 409 path; MeetingErrors.conflict emits MEETING_CONFLICT, which is absent from the declared 409 response enum. Likewise the existing member-write denial, unavailable-category and transport-budget cases exercise the other three excluded codes. Their current assertions check status/state rather than error-schema conformance. The passing lint/bundle, generation byte comparison and web typecheck only establish that the static description is internally processable; they do not close this runtime mismatch.

Recommendation: provide meeting-specific error schemas/responses with the actual codes and preserve the shared authentication/CSRF/database failure alternatives, or deliberately extend the shared error contract under the relevant ownership agreement. Update the meeting 403 response as well as 409/413/503, regenerate the client, and add focused assertions/schema validation for the four real error bodies. Rerun the affected HTTP/contract/generated-client checks; no SQL, fixture reset, unrelated historical schema IT or production qualification is needed.

## Review basis and verified behavior

Read the full domain implementation report, activation/prepared/review briefs, original roles/basic-information requirements, approved product supplement, B03 requirements, selected database lock/ACL/idempotency/projection design, V008 SQL and all eight current dictionary sections, prerequisite/status records, and adopted B05 adoption/consumer handbook/mandatory fix1 addendum. The current root B00-1.3 dictionary's eight B03 table sections are byte-identical to the owner B00-1.2 sections; higher-schema work does not alter this scope.

Read every owned production source and both complete IT classes, complete OpenAPI files, generated TypeScript and integration handbook; inspected the complete changed-path inventory and source diff scope. The only later owner commit changes documentation chronology. Source/contracts/docs/generated git diff --check passed.

- Identity/ACL: current enterprise_identity is locked and ACTIVE is checked independently of Actor.status/roles. Owner READ/EDIT/MANAGE and member READ are distinct. Grant/current roster/same-meeting CONFIRMED/member-kind/exact ID provenance is checked; legacy ACL, platform role, draft-only membership and forged nonmembers cannot grant access. Lifecycle/governance deny ordinary reads; repository writes additionally reject published/ever-published roots.
- Draft behavior: empty create stores null actual date, empty immutable information and pending roster. Save preserves current grants/confirmed pointer; all-state latest selection prevents the historical draft reappearing after confirmation. Explicit empty/add/remove confirmation creates an immutable confirmed copy and advances root/access/fence versions. Owner-only pending roster fields are withheld from member GET. Human name/summary are LONGTEXT, no product length cap; the separately configured byte budget rejects without truncation. Category availability fails closed in production; the dictionary entry exists only in test composition.
- Request identity: scope uses complete four-byte length-prefixed UTF-8 actor/resource/operation/key; request digest contains version marker, revision, every nullable text/date field and both complete lists/counts. Strict encoder rejects malformed surrogates. Same-key insertion serializes writers; mismatch follows current authorization. Replay restores original immutable revision/status without rerunning effects. Exceptions roll back the idempotency row.
- Locks: affected identities include actor, desired members, current grants and latest draft; unsigned UTF-8 ordering matches binary IDs. Lock-free discovery is followed by locked current-set coverage validation, so guessed future CAS cannot bypass omitted identities. No late identity lock or SKIP LOCKED is introduced. Subject is created before root; existing root version/epoch must match its fence.
- Capabilities: issuer, exact DataSource resource and actual Spring transaction holder bind the opaque handle. Fake, foreign and expired handles fail. The same adapter rejects identity prelocking after root acquisition. Reuse rereads the already-held root/current identity and member provenance instead of trusting cached governance.
- Atomicity: root/info/roster/grants/projections/subject/idempotency/audit/event share one DataSource transaction. Actual production event validators enforce subject kind, event meaning, null task and exact immutable reference/root/revision provenance. Outbox flush is last in create/save/confirm; no SQL follows it in those paths. Audit and final-flush fault tests exercise full rollback and same-key retry.
- Projection: full persistent UUID is retained; saved info name/category/pointer and aggregate revision update synchronously. Confirmed roster names replace prior effective person-search rows only on confirmation. Draft and AI/candidate values do not enter effective search. No competing D02 list implementation or production worker/provider is enabled.

## Evidence audit and chronology

Independently compared all 79 files against the delivery-path sizes/SHA-256 and HEAD Git blobs: exact set, zero mismatches. Independently hashed all 59 manifest payloads and compared the complete on-disk 60-file evidence set (including manifest): exact set, zero mismatches. All XML documents were parsed, all log files read for outcome/diagnostic inspection, and fixture/metadata/history/count/manifest/wrapper/small build records inspected with bounded output. Large repetitive JVM property blocks were not dumped.

Final XML method names exactly match both current IT classes: MeetingRevisionIT 30 + MeetingConcurrencyIT 5 = 35 distinct domain cases, zero failures/errors/skips. Eleven final Java unit XML suites total 62; web XML contains eight passing testcases. Final OpenAPI has two checks, generated byte comparison passes, and typecheck output is clean. Earlier runs overlap and are not additive. Four concurrency-suite methods use a real JDBC ProbeSource explicitly setting REPEATABLE_READ; the simultaneous-confirmation method uses the ordinary real MySQL DataSource. The latter is not an independently asserted isolation-setting probe. Latches coordinate actual statements/commit, not fabricated SQL results.

RED chronology verified: initial red.xml has three 401 setup failures; expanded/authenticated records each have ten missing-route failures before deeper assertions; early green=10 and expanded=21 overlap. edge-red has three guard failures plus one fixture connection/lock-wait setup error (raw exception is CommunicationsException/SocketTimeoutException), not four valid REDs. Corrected edge-red has four intended assertion failures. Concurrency RED has five tests with one future-CAS coverage failure; both later five-case logs pass, and boundary-final is the final source-corresponding one. revision-final-attempt1=25 overlaps final30.

Read all 141 raw full-first.log lines: it ends in API surefire IdentitySecurityTest with subprocess143, before integration-tests/failsafe/fixture setup. The separate interruption record retains PID710745, expanded four-class Meeting*IT selector and kill-TERM observation. The log alone does not record the kill command/PID; that detail is supported by the contemporaneous record. It is not counted as a domain run, migration/refusal/reset execution or test pass.

All eight SQL files (001/002/005/006/007/008/009/010) match both before/final hash records and BASE Git bytes. No SQL path changed. Both metadata/history snapshots have identical schema and first-install history; only final aggregate counts differ (122/232 to 127/248 meetings/events). Final Flyway timestamps UTC20:01:54–20:02:05 and checksums remain the initial successful creation sequence; raw first red migration logging is consistent with UTC+08 host time. Selected metadata contains the unchanged eight tables, 71 columns, indexes, 12 CHECK clauses and FKs. Final snapshot: 31 total tables, 127 meetings/subjects, zero tasks/task-bound events, 248 domain events.

Fixture records identify an owned empty MySQL8.4.6 container, loopback46085, exact image/volume/labels and process-only --innodb-use-native-aio=OFF; initial @@native_aio=0, timezone+00:00 and zero tables. Wrapper does not modify the shared helper and preserves failed containers. No Docker/DB/service action was executed during review. Archive .gitattributes is '* -text'. Known fixture application-password value was checked privately against every archive file: zero matches; no secret was printed. No full Docker environment or private env file is included in the 60-file archive.

## Composition and qualification limits

The access capability is a documented single-root, same-transaction composition mechanism, not an arbitrary-SQL lock-order monitor. A caller that already locked roots manually, acquires another root late, mutates invariants outside the APIs, or issues SQL/network operations after final flush is outside the supported contract. Member require performs grant/roster/member provenance locking; future consumers must execute their complete authorization/domain prelock before higher-order task/operation locks and reuse the held state appropriately. No future worker/domain callback integration is approved by this review.

The transport limit is checked after Spring materializes the body; it is an acceptance budget, not a streaming/allocation/load qualification. Real SSO/directory/disable propagation, category dictionary, external-person mapping, company security approval and production rollout remain prerequisites. Native-AIO-off evidence is local functional evidence only. No RHEL/HA/fencing/native I/O/capacity, full UI/e2e, historical destructive schema/refusal suite, or unrelated worker/storage integration rerun is claimed or required for this local fix.

Review used read-only file/Git inspection and analytical scripts; no tests, builds, DB/Docker/service operations, source edits, Git mutations or child agents. The sole write is this report. An initial oversized combined output was replaced by bounded complete source/relevant raw reads; no review conclusion relies on truncated unseen output.

## Complete owned path coverage (79)

Paths below are relative to the owner tree. All 19 source/contract/generated/acceptance files were read; all 60 evidence paths were audited as described above.

Source/contracts/acceptance (19):

- backend/adapters/src/main/java/ama/meeting/JdbcMeetingAccess.java
- backend/adapters/src/main/java/ama/meeting/JdbcMeetingRepository.java
- backend/adapters/src/main/java/ama/meeting/MeetingDigests.java
- backend/adapters/src/main/java/ama/meeting/MeetingEventReferences.java
- backend/api/src/main/java/ama/api/MeetingConfiguration.java
- backend/api/src/main/java/ama/api/MeetingController.java
- backend/api/src/main/java/ama/api/MeetingErrors.java
- backend/contracts/src/main/java/ama/contract/MeetingContracts.java
- backend/core/src/main/java/ama/access/MeetingAccess.java
- backend/core/src/main/java/ama/meeting/CategoryDictionary.java
- backend/core/src/main/java/ama/meeting/MeetingRepository.java
- backend/core/src/main/java/ama/meeting/MeetingService.java
- backend/core/src/main/java/ama/meeting/RosterService.java
- backend/integration-tests/src/test/java/ama/meeting/MeetingConcurrencyIT.java
- backend/integration-tests/src/test/java/ama/meeting/MeetingRevisionIT.java
- contracts/openapi/api.yaml
- contracts/openapi/meetings.yaml
- docs/acceptance/b/meeting-domain-integration.md
- web/src/shared/api/generated.ts

Evidence (60):

- artifacts/phase-b/b03-domain/.gitattributes
- artifacts/phase-b/b03-domain/concurrency-boundary-final.log
- artifacts/phase-b/b03-domain/concurrency-boundary-final.xml
- artifacts/phase-b/b03-domain/concurrency-final.log
- artifacts/phase-b/b03-domain/concurrency-final.xml
- artifacts/phase-b/b03-domain/concurrency-red.log
- artifacts/phase-b/b03-domain/concurrency-red.xml
- artifacts/phase-b/b03-domain/edge-red-corrected.log
- artifacts/phase-b/b03-domain/edge-red-corrected.xml
- artifacts/phase-b/b03-domain/edge-red.log
- artifacts/phase-b/b03-domain/edge-red.xml
- artifacts/phase-b/b03-domain/expanded.log
- artifacts/phase-b/b03-domain/expanded.xml
- artifacts/phase-b/b03-domain/final-metadata-history.txt
- artifacts/phase-b/b03-domain/fixture-ownership.json
- artifacts/phase-b/b03-domain/fixture-start.txt
- artifacts/phase-b/b03-domain/full-first.log
- artifacts/phase-b/b03-domain/generated-api-check.log
- artifacts/phase-b/b03-domain/generated-api-final.log
- artifacts/phase-b/b03-domain/generated-api.log
- artifacts/phase-b/b03-domain/green-first.log
- artifacts/phase-b/b03-domain/green-first.xml
- artifacts/phase-b/b03-domain/initial-empty.txt
- artifacts/phase-b/b03-domain/interrupted-selector-record.md
- artifacts/phase-b/b03-domain/migration-sha-before.json
- artifacts/phase-b/b03-domain/migration-sha-final.json
- artifacts/phase-b/b03-domain/npm-install.log
- artifacts/phase-b/b03-domain/openapi-final.log
- artifacts/phase-b/b03-domain/openapi.log
- artifacts/phase-b/b03-domain/owned-fixture-wrapper.py.txt
- artifacts/phase-b/b03-domain/red-authenticated.log
- artifacts/phase-b/b03-domain/red-authenticated.xml
- artifacts/phase-b/b03-domain/red-expanded.log
- artifacts/phase-b/b03-domain/red-expanded.xml
- artifacts/phase-b/b03-domain/red.log
- artifacts/phase-b/b03-domain/red.xml
- artifacts/phase-b/b03-domain/revision-final-attempt1.log
- artifacts/phase-b/b03-domain/revision-final-attempt1.xml
- artifacts/phase-b/b03-domain/revision-final.log
- artifacts/phase-b/b03-domain/revision-final.xml
- artifacts/phase-b/b03-domain/sealed-metadata-history.txt
- artifacts/phase-b/b03-domain/sha256-manifest.json
- artifacts/phase-b/b03-domain/test-counts.json
- artifacts/phase-b/b03-domain/unit-final.log
- artifacts/phase-b/b03-domain/unit-xml/TEST-ama.api.DeploymentConfigTest.xml
- artifacts/phase-b/b03-domain/unit-xml/TEST-ama.api.IdentityContractsTest.xml
- artifacts/phase-b/b03-domain/unit-xml/TEST-ama.api.IdentitySecurityTest.xml
- artifacts/phase-b/b03-domain/unit-xml/TEST-ama.storage.ContractFixtureTest.xml
- artifacts/phase-b/b03-domain/unit-xml/TEST-ama.storage.StorageRouterTest.xml
- artifacts/phase-b/b03-domain/unit-xml/TEST-ama.storage.s3.S3LayoutV3Test.xml
- artifacts/phase-b/b03-domain/unit-xml/TEST-ama.storage.s3.S3ProtocolTest.xml
- artifacts/phase-b/b03-domain/unit-xml/TEST-ama.support.ContractClientTest.xml
- artifacts/phase-b/b03-domain/unit-xml/TEST-ama.task.TaskMetadataTest.xml
- artifacts/phase-b/b03-domain/unit-xml/TEST-ama.transcript.TextTranscriptParserTest.xml
- artifacts/phase-b/b03-domain/unit-xml/TEST-ama.worker.WorkerLoopTest.xml
- artifacts/phase-b/b03-domain/web-npm-install.log
- artifacts/phase-b/b03-domain/web-typecheck-final.log
- artifacts/phase-b/b03-domain/web-typecheck.log
- artifacts/phase-b/b03-domain/web-unit.log
- artifacts/phase-b/b03-domain/web-unit.xml
