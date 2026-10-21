# B00-1.3 generation persistence — independent SPEC + quality review

Date: 2026-10-22. Reviewer: fresh independent delegated reviewer; no implementation ownership or child agents.

## Verdict

**SPEC: PASS for this bounded detailed-design increment. QUALITY: PASS for this bounded design/evidence package.** No actionable Critical/Important/Minor findings found. These conclusions do not adopt the increment on the coordinator's behalf, resolve either pending product question, or qualify any formal migration, application, company integration, production environment, performance or HA behavior.

Exact reviewed range: `0d2079cd64e5b61bc5787ca5adfaa782e5cd4a40` → `7fb166b01b6ff45213b973640ff7457c3cf36e56`.
Owner: `/home/tanren/ama/.worktrees/phase-b-generation-design`.
Complete review package: `task-B-00-generation-addendum-review.diff`, **1,067,332 bytes, 34 paths**; SHA-256 `42b53875c77739065842b01adff33499621d6f9fd29edeeafcbb6c88f6eacd25`.

Read first the binding brief and entire delivery report, including the appended final delivery. The original proposal/report are historical inputs; their deferred-FK suggestion and original BASE do not govern the final design. Root's final ruling and tracked root-ack.md govern: seven new tables V012, final_result_review V013 with its parent and both physical FKs, and only sensitive_exclusion PK/CHECK changes among original tables.

## Method and scope

Read the complete active design/checker/schema/dictionary delta, acceptance record, initial proposal, final delivery, source/evidence allowlists, fixture/probe/checker scripts and recorded outputs. Consulted original requirements III §§6.3–6.10,7–11 and IV §5, the adopted product supplement, and the B07 R3/R4 storage/review gaps. Inspected all new tables, fields, relationships, constraints, indexes and full captured metadata; audited preserved historical files by bytes/hash rather than dumping the old dictionary.

Only read-only file/JSON/diff parsing and hashing were performed. No Git command, SQL, Docker command, service operation, test, compile, source edit, migration edit, credential read, cleanup/reset or company call was executed. The sole write is this requested SDD report. Captured actual database evidence was inspected, not rerun; the report does not attest to the fixture's present live state.

## SPEC assessment

|Requirement/ruling|Reviewed evidence and assessment|
|---|---|
|Minimal normalized business persistence|Design lines 123–134; checker lines 351–400; dictionary lines 2068–2224. Five directory/suggestion tables preserve unselected detections, both labels, background body/source sets and raw owner/date suggestions. Three review/membership tables preserve assertions and historical selections. Each generated item keeps its originating input_id through copy/partial regeneration. No extra subject kind, manifest column, JSON blob/file workaround or copied source excerpt. Existing immutable graph/config references remain the input reconstruction strategy.|
|Only authorized existing-table delta|DDL sensitive_exclusion lines 636–644; dictionary lines 988–1009. PK now includes category, and the two-label CHECK rejects other classifications. No original column or FK changes. Independent byte/metadata comparison confirms the claimed restriction.|
|V012/V013 migration ownership|Design line 123; dictionary final_result_review lines 2210–2224; DDL lines 1855–1857. Final membership has both physical FKs and belongs to V013; no active deferred-FK alternative. Initial proposal text remains explicitly preserved history. Registry and existing migrations are untouched.|
|Selection and dedup semantics|Design lines 138–142. Ordered directory entries remain distinct; locator/source membership is set-valued, duplicate pairs reject, overlapping detections union locator/category pairs, clearing one category retains the other, and physical-source exclusion does not erase retained category history. Unselect is not source/detection deletion.|
|Classification versus human assertion|Design lines 134,146–155; dictionary lines 2158–2208. Existing support_state is immutable creation classification. SOURCE_SUPPORT and ACTION_FIELDS are separate, with exact targets, full content/action tuple, complete evidence set, source versions/hashes and archived hash profile. Latest exact-scope review supersedes earlier outcomes; no arbitrary historical SUPPORTED shortcut.|
|Reviewer and exact-target provenance|Design lines 147–153. Server identity/time, active identity/owner/source authorization, expected revision and complete-set/hash recomputation are explicit owning-transaction obligations. No identity FK is a deliberate event-time identity policy, not an authorization claim. New target/source/version invalidates old credit; content-specific adopt+review may create a fresh assertion atomically.|
|Final history|Design line 154; dictionary lines 2210–2224. Final membership freezes exact review IDs, complete projections and source graph into source_manifest_hash. Later reviews cannot silently rewrite past final evidence. Membership must match the final meeting/minutes/element set.|
|Candidate/effective/manual/history boundaries|Design lines 158–166. Candidate children commit together; pending candidates are not participant-effective versions. Adoption remaps to new effective target IDs, protects HUMAN title and unsaved UI text; element adoption leaves saved body intact. Manual edits/restores append versions and preserve origin input, never resurrect old review credit.|
|ACL and file boundaries|Design lines 165–175 and existing file section. Meeting access does not grant source access; each excerpt uses authorized immutable source. No invented roles, grants or name/history cap. Structured data remains MySQL; local/S3 managed-file lifecycle remains existing behavior. No new attachment/reference registry kinds.|
|Transactions, fences and deletion|Design lines 170–177. Authoritative-parent same-meeting/target/source checks, identity→subject→meeting→domain/file→task→audit→outbox lock order, lease/token/attempt/business-version/epoch checks, expectedRevision CAS, atomic candidate+children+task/audit/outbox, append-only reviews and explicit dependent deletion are specified. Tombstone/fence precedes cleanup; no FK disabling/cascade/automatic history expiry.|
|Pending product questions remain pending|Design lines 134,150,161 and unresolved-gates table. Neither first guarded auto-save versus explicit adoption nor per-item versus combined final review is adopted. Schema can support authorized future choices without automatically treating generation/adoption/final clicks as source attestation.|

The eight tables add 35 columns with distinct necessary business ownership or membership roles; no redundant generation manifest is introduced. Related materials remain derived from fixed input snapshots. Background text cannot establish a meeting conclusion, model owner/date values remain outside accepted action_item values, and absent dates remain permitted. The restricted design is adequately specified for later implementation while explicitly retaining codec/coverage and product gates.

## Independent package and historical integrity audit

- Parsed every unified-diff hunk for all 34 paths and matched the target bytes against the owner tree. Independently computed Git blob SHA-1 values from file bytes (without invoking Git); all target index hashes match. Reconstructed all four changed original files from the reverse hunks and matched their original blob hash prefixes.
- All **18 source-allowlist entries + 13 evidence-allowlist entries = 31 SHA-256 checks match**. The remaining three package paths are the two allowlist files themselves and secret-exclusion-check.json; they are covered by the complete package blob/hunk verification. No unexpected source, application, migration, register or user-plan path appears.
- All four B00-1.2 preserved files match manifest SHA-256 values and independently reconstructed BASE bytes: design, dictionary, review DDL and checker. Thus preservation is stronger than trusting a second copied hash list.
- All **12 frozen-input hashes** match: V001/V002/V005/V006/V007/V008/V009/V010, shared fixture helper, migration register, old 1.1 metadata and old 1.2 metadata. None appears in the diff. V003/V004 are not invented or populated.
- Initial proposal/report/brief and ACK hashes match their allowlist entries. The initial report is not mistaken for the completed delivery, and initial V012 deferred-parent proposal does not override the root ruling.

## Actual metadata audit

Captured metadata file SHA-256: `a6c758d488c490c97bb373f404bc25c5b50db001e7c0dcb82dfcf9a6668f7aa5`.
Current review DDL SHA-256: `ebf1eae51b303d00b2ed35c4fbd5ba4c166813564032875a6ba0fb35ba743c93`.
Both match the allowlists; the latter also matches the captured metadata header and metadata-verify.log.

Independently counted the full capture: **93 tables, 709 columns, 290 index definitions / 441 index columns, 109 CHECKs, 196 FK constraints / 203 FK columns**.

An independent read-only SQL parser compared all 709 declared columns to captured metadata, including order, type, nullability, default, collation and extra. It reconstructed all explicit and implicit FK-supporting indexes and matched all 441 index rows including uniqueness, sequence, direction, prefix and type. It independently parsed all 203 FK column rows and matched referenced table/column/order and RESTRICT actions. All 109 CHECK identities and ENFORCED=YES match; all nine new-table CHECK expressions were inspected against the captured clauses, including operator grouping and case-sensitive REGEXP_LIKE arguments. Existing CHECK clauses match old metadata except the authorized added exclusion CHECK.

The dictionary's complete 709 column names/order/types/nullability/defaults match independently parsed DDL. Checker delta and generated ER relations match the new declarations: exactly one review target; kind/outcome/anchor combination; positive review/entry ordinals; lowercase 64-hex hashes; membership/reverse indexes; both final physical parents. The AST addition preserves function name and every argument, and fails on unsupported functions; it does not reduce a regexp to a generic token.

Comparing full old/current metadata independently:

- Original 85 table descriptors, all original columns and every original FK row are equal.
- No original index/check row was removed. Only added old index row is sensitive_exclusion PRIMARY sequence 3 category; only added old CHECK is its two-label membership condition.
- All 84 other original CREATE blocks and all original FK statements are byte-identical.
- For each of V011's eight tables, CREATE+FK bytes, every metadata array and incoming FK rows are unchanged from the complete adopted 1.2 design.

This last statement is explicitly **design-subset equivalence**. Root's separate finding about an omitted implicit material FK supporting index in formal V011 and registered 11.1 forward correction remains separate. This review neither asserts formal V011 equivalence to complete review DDL nor rewrites V011 nor adopts/qualifies 11.1.

## Fixture and probe evidence audit

Initial/final/probe/metadata records consistently identify `ama-test-b01-991f0386f26d`, full ID `a5bd8e25f6047c542e94af6aac5bec68e60532c183d460714586480b2e22add3`, MySQL 8.4.6, pinned image, owned worktree, same anonymous volume, localhost port 44649 and native AIO 0. Initial capture has zero schema tables; final capture lists all 93 tables with only task_outbox_sequence containing one DDL-created row. Recorded aio-max-nr is unchanged at 65536. The process-local wrapper narrows the extra AIO argument to the authorized helper shape and refuses failed-container deletion. No credentials were read or reproduced by this review.

All 23 SQL section names/order match the 23 captured probe records. Result distribution is consistent: six successful SQL cases (two positive and four boundary witnesses), three 1062 duplicate failures, nine 3819 CHECK failures, three 1452 FK failures and two 1451 RESTRICT failures. Source inspected at constraint_probes.py lines 57–90 establishes expected error/output checks and transaction rollback/connection-close rollback behavior; final_checks.py and fixture-final.json extend residual-row inspection to the whole schema.

Positive cases cover two labels on one locator, removal of one label with the other and both detections retained, multiline/emoji background/raw-owner bytes, suggested date and accepted NULL separation. Negative cases exercise duplicate label/order/source membership, invalid enum/zero order, target XOR, review kind/outcome/NULL-anchor combinations, uppercase/nonhex hashes, missing source/final parents and protected review/evidence deletion. The anchor probe is specifically NULL-anchor rejection; it is not validation of empty-string semantics, ranges, canonical anchor encoding or coverage.

Four cases deliberately succeed at raw SQL insertion and then return diagnostic 0: unknown reviewer identity, cross-meeting review, other-target evidence, and well-formed but mismatching content hash. They demonstrate database limits, as acceptance lines 45 and 53 say; they are not app rejection tests. The simple SHA2(body) mismatch witness does not claim to implement the eventual full target hash profile. The seed uses synthetic hashes/anchors and is relational test data, not a model-quality or semantically reviewed meeting.

Observed evidence and inspected scripts support the bounded DDL/metadata/constraint claims. No claim is made that the reviewer independently observed a live database run or authenticated the entire execution history beyond the supplied, internally consistent recorded evidence.

## Findings and remaining gates

**Actionable findings: none. Critical: 0; Important: 0; Minor: 0.** No mandatory correction is identified for this exact design increment.

The following are explicitly retained implementation gates, not newly discovered design findings:

1. User answers for first-result effective save and review interaction; no default activation.
2. Separately reviewed B07 anchor/Unicode/source alias/hash-profile/coverage contracts and reproducible immutable configuration/parser dependencies.
3. Application tests for active identity, owner/source ACL, same-meeting/target/full evidence membership, actual hash recomputation and explicit content-bound intent. A valid FK/hash format is insufficient.
4. Actual transaction/CAS/idempotency/concurrency/lease/epoch/token fencing, candidate atomic completion, append-only review/final membership, restore, withdrawal and deletion/file-reference behavior.
5. Formal V012/V013 migration status check before authoring; any already-shared/applied state needs coordinator allocation of a forward migration. Existing SQL and separate V011/11.1 work remain frozen/separate.
6. Company model/identity/material/source protocols, external-owner handling, production storage/DB/HA/capacity qualification remain outside this review.

Coordinator may use this PASS to make the bounded design adoption decision. It must not be used as application or migration completion evidence.
