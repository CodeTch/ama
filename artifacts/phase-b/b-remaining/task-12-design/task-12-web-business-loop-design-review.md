# Task12 prospective web business-loop design review

SPEC: NEEDS FIXES — one Important omission in the new Task12-owned backend read design.
Design quality: NEEDS FIXES — resolve T12DR-01 before design adoption/source dispatch.
Counts: Critical 0; Important 1; Minor 0.

## Reviewed object and limits

Reviewed the complete whole-addition package `task-12-design-review-diff.txt`, SHA256 `485e192201e8ac219986eaae4dddb16e3bf94c886b0774b9c22631437686f27c`, once in bounded sequential slices. All 175 added design lines were read; no diff slice was truncated. Target `task-12-web-business-loop-design.md` SHA256 independently matched `2ce7e7457f07314b7ce967f433c716764d64f97f890576755ee61f7208e9253a`. References below use target-file line numbers, not diff-file line numbers.

Applied the superpowers 7.0.1 task-reviewer template to a prospective DESIGN: independent SPEC and design-quality judgments, not implementation, browser or whole-branch qualification. Read the exact brief first, then plan extract and web preflight, b09 preflight, controlled-runtime including Normative R1, approved minutes contract/UX, authority refinement, prototype index and relevant final-render anchors. Focused producer-design checks covered Task6 protected transfer/stored inspection, Task10 history/intent/review/transfer contracts and Task11 R1 authentication, official-document relay and immutable acceptance/local recovery.

The controller supplied the newer Task4 commit `59c8b080` as still under independent source review, not adopted. The design's line 9 describes its earlier provisional snapshot; its source-dispatch gate at lines 17 and 173 must use the eventual adopted source. Likewise Task9/10 design adoption and Task11 R1 review status do not establish installed source. No implementation verdict is inferred from them.

## Strengths and SPEC checks

- `task-12-web-business-loop-design.md:17–43`: root OpenAPI and `/api/v1/meetings` ownership are explicit; missing list/workspace/category/task/command discovery is assigned to actual backend owners. Generated clients cannot mask missing schemas with handwritten successful shapes. Source adoption precedes activation.
- `task-12-web-business-loop-design.md:45–64`: concrete Vue/router/API/composable decomposition separates transport, command state, draft state, intent, task polling and business views; one author owns shared integration and no Pinia dependency is assumed.
- `task-12-web-business-loop-design.md:68–78`: real cookie/session/CSRF/Origin, no-store reads, token-only requests, normalization before the existing semantic digest, retained uncertain commands, historical ACK versus current observation, per-actor/tab journals and 409 draft retention are correctly separated. No JavaScript 2^53 product cap or frontend authorization substitute is introduced.
- `task-12-web-business-loop-design.md:82–96`: actual multipart upload/resume/order, RAW versus explicit WORKING, full marks, restoration/confirmation, first settings, server-produced generation disposition and candidate operations form a coherent prospective chain. Every minutes edit-capable entry waits for its own positive POST intent ACK; GET/another tab/task success never grants it. IME, paste, restoration, autosave and late responses preserve drafts.
- `task-12-web-business-loop-design.md:100–104`: independent body/set histories cannot fabricate historical pairs. Accepted nullable action tuples remain distinct from suggestions; exact WHOLE human review, complete evidence, latest matching coverage and frozen final membership follow the approved contract. Original-byte authorization is separate from stored-text inspection.
- `task-12-web-business-loop-design.md:106–120`: actual root-folder IDs, global write freeze, query-only UNKNOWN, permanent ever-published read-only, zero-effect recovery and remote/local manual outcomes are distinguished. Task11 service credentials, signed delegation, dual-ACL same-origin relay, immutable submissions and never-marked recovery remain explicit pending upstream gates, with no browser provider credentials or invented success.
- `task-12-web-business-loop-design.md:124–165`: immutable original/reference custody, strict shared-shell baseline, new original-derived references and all three viewports are specified. Both LOCAL and new owned S3 require full browser clicks through actual API/MySQL/native TLS/production Worker/durable controlled model and KB. Response-loss, two tabs, fresh JVM/provider stores, four identities, direct-write negatives and publication recovery are prospective evidence obligations; business route.fulfill and SQL-created positive results are forbidden.
- `task-12-web-business-loop-design.md:171–175`: unsupported audio/materials/company services and separate company/HA/capacity/native-I/O qualifications are honest. Task13 and primary integration remain later work.

## Important — T12DR-01: assign a concrete pre-transfer content-budget protocol to the new backend readers

Location: `task-12-web-business-loop-design.md:35` (new meeting list), `:37–43` (new workspace/category readers and shared read rules), and `:157` (backend acceptance).

The design limits page size and response shape and forbids copying unbounded SELECT * helpers, but does not specify who protects content-bearing database reads before transfer, or how the new Task12 readers satisfy that requirement. A page of at most 50 rows is not a content bound: the existing schema stores meeting name and summary as LONGTEXT, and roster display names as LONGTEXT. The new list explicitly returns saved names. Existing saved-info access also transfers the entire info row, so composing workspace/info from that helper does not establish a bounded acquisition merely because the final DTO is smaller.

Focused source evidence: `backend/adapters/src/main/resources/db/migration/V008__meeting_schema.sql:67`, `:69`, `:94`; `backend/adapters/src/main/java/ama/meeting/JdbcMeetingRepository.java:130–136`. The latter is contextual existing source, not a Task12 implementation finding or permission to rewrite historical source in this design task.

The binding pre-transfer rule is explicit in `docs/superpowers/specs/2026-10-23-ama-phase-b-controlled-runtime.md:11`; Task6 R1 lines 148–158 and Task10 design lines 59–69 specify stable metadata protection and budget reservation before LONGTEXT reaches JDBC, including display labels, plus protected rereads. Task12's finite pagination/JSON response bounds and client draft/file memory limits do not replace that server obligation. An oversized saved name or concurrent change between length check and fetch can otherwise consume driver/application memory before the response limit or UI ever sees it.

Required design repair: name Task12's backend projection owner and the B03/producer integration seam for this responsibility. For new list/workspace/category and any composed info/roster content reads, specify metadata-only bounded ID discovery, fresh genuine authorization, retained authentic root/row/parent protection in existing rank, complete protected key sets, and cumulative per-field UTF8/scalar/UTF16/copy/escaped-response reservation before any text-bearing SELECT. Reuse the reviewed producer protected reader where it actually provides these guarantees; do not add a second ACL/gate or claim generic DTO/page bounds suffice. A reviewed equivalent server-side size gate is acceptable only if it proves oversized fields never reach the driver. Overflow, inability to establish stable protection and missing capability must fail before transfer without truncating successful content or inventing a product name-length cap.

Add focused prospective actual-MySQL instrumentation to the new reader acceptance: oversized saved name/display/category label and a length-check-versus-read race, including repeated/final read where applicable, prove no content-bearing SELECT occurs before protected reservation and no denied content is returned. Existing producer tests alone cannot establish this for newly introduced Task12 query paths.

## Source/runtime gates, not additional findings

- `task-12-web-business-loop-design.md:17`, `:154`, `:173`: before source dispatch, pin adopted Task4–11 source/facades, actual token normalization on every browser writer, complete shared PENDING/publication guards and all read/discovery DTOs. This review does not re-review Task4's source branch or approve Task11 source/DDL.
- `task-12-web-business-loop-design.md:128`, `:154`, `:164`: new original-derived reference captures, provenance and strict visual comparisons still must be produced; no new reference image or browser pass was inspected or claimed here.
- `task-12-web-business-loop-design.md:134–167`: new resource custody, qualified schema, retained stores, secure session bootstrap, runner output overrides and real dual-storage/restart evidence remain future activation gates. No fixture, command family, test count or hypothetical test name is represented as executed.

## Review activity

Read-only public documentation/source inspection and SHA256 checks only. Outside-diff checks addressed named risks: inherited metadata-before-transfer obligations and actual LONGTEXT/query shape; stale producer design authority versus current source; precise Task10 history/ACK/review semantics; Task11 provider/relay/local-recovery contracts; and unchanged final-render prototype authority. Broad searches whose output was truncated were followed by bounded relevant-section reads; the complete Task12 diff itself was read without truncation.

No tests, browser, dependency installation, DB, Docker, private environment, network, source, Git/index/HEAD, resource or subagent operations were performed. The sole write is this review report.
