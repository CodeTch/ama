# B07 pure-S1 contract — independent SPEC and technical-quality review

2026-10-22. Fresh delegated reviewer; design only. No S1 implementation exists in this review.

## Verdict and scope

**SPEC: NEEDS REVISION. TECHNICAL QUALITY: NEEDS REVISION.** Findings: **Critical 0, Important 2, Minor 0**.
The isolated codec, strict output reader, immutable contracts and conservative classification direction are sound. The proposed review/final hash surface is not ready to freeze because its source-context identity and recovery rules do not cover histories explicitly allowed by the same proposal.
This does not reject adopted B00-1.3, authorize a schema change, or approve runtime behavior. Fix both Important findings before approving the complete proposed S1 surface; alternatively explicitly narrow the affected pure profiles with deterministic capability failures and leave unsupported histories outside their advertised domain.
First effective save and per-item/combined human review remain pending user decisions. Neither recommendation/default is activated. Neither finding requires a product UX decision.

Reviewed the complete 141-line final proposal and complete final report, review/finalization/original task briefs, adopted product supplement, relevant B00-1.3 spec/dictionary/DDL including all eight new tables, adoption records, TaskContracts, module POMs and local Jackson API metadata. Proposal SHA matches the brief. No source, Git, POM, SQL, test, database, service, network or child-agent actions; only this requested report is written. No compilation or test run is claimed.

## Important findings

### I1 — FINAL_SOURCES collapses different pinned source contexts onto one locator

Location: final proposal §5 line 97 and §6 lines 106,109,114; final report changes 5 and 8.

The source version is context-dependent, but FINAL_SOURCES defines one `{role,sourceVersion,sourceContentHash}` keyed by `locator.id`. That is not a total representation of the permitted mixed-origin directory graph. Locator dedup compares only the typed locator, while FILE SourceVersion includes materialRevisionId/parsedDocumentId/parserVersion/parsedTextSha256, none of which is part of a PDF_PAGE locator.

Minimal graph using only existing columns:

1. One immutable local file F has material revision M. Existing parsed_document permits D1(M,P1) and D2(M,P2); no uniqueness constraint forbids versions. Each immutable parser output contains PAGE 1. G1 and G2 pin their own archived parser/profile, each resolving one exact document; neither input has an ambiguous catalog.
2. N1 is background_note(input_id=G1), N2 is background_note(input_id=G2). Both reference typed PDF_PAGE(F,1), so same-meeting full-locator dedup correctly reuses L. The proposal explicitly permits copied directory rows with older input_id in a new set; the resulting set contains N1 and N2.
3. N1→G1→pinned D1 and N2→G2→pinned D2 are individually recoverable. The final helper must include both final directory joins, but its single sourceRows[L].sourceVersion cannot be both D1/P1/hash1 and D2/P2/hash2. Choosing one silently loses a version; keeping two rows violates the declared key; substituting current parser is forbidden. Merely hashing both note input IDs does not implement the promised complete source-version projection.

Evidence: dictionary source_locator lines 1035–1063, material/parsed tables lines 1439–1563, background_note/source lines 2105–2139; design B00-1.3 permits mixed original inputs and exact physical-locator reuse. The conflict exists with fully supplied immutable facts, before any database resolver or production material activation.

Required correction: define locator identity separately from its source-context occurrences. For example keep unique complete locator rows and a sorted duplicate-free context/usage projection binding each directory membership or evidence occurrence to its exact origin input and sourceVersion/hash. Specify complete keys, ordering, validation and domain bytes; preserve both occurrences in the example. Do not invent database columns. If the initial pure final profile intentionally cannot represent this case, explicitly reject it with a fixed capability code before hashing, specify its supported input domain, and remove the claim that it covers arbitrary mixed-origin supplied facts. Runtime material gating alone is not the pure helper contract.

Future pure verification: construct the two-note shared-L graph; permutation must preserve the digest, changing either pinned parser hash must affect it, neither occurrence may be dropped, and conflicting duplicate occurrence keys must reject. These are requested future tests, not executed tests.

Severity: Important. This is a technical profile/cardinality correction requiring root review before wire/hash freeze, not routine serializer implementation discretion and not a pending UX choice.

### I2 — Original generation provenance of copied ordinary-element evidence is not recoverable by the stated graph

Location: final proposal §5 lines 97,100–101 and §6 lines 109,111; final report changes 1,3,5.

REVIEW_SOURCES requires each source version/hash to bind its original target generation graph. The exact existing columns provide origin input_id for detection/note/action_suggestion and generation_input_id for a minutes body. They do not provide an origin input link for an ordinary minutes_element or evidence_link after the proposal creates new target/set/evidence IDs on adoption, manual set copy or restore.

Column/FK audit:

- evidence_link contains id, locator_id, element_id/minutes_revision_id, support_state, target_anchor; there is no input_id or prior evidence ID.
- minutes_element contains id,set_id,category,ordinal,body,support_state; element_set contains id,meeting_id,revision,created_at. Neither has an origin or parent link.
- generation_candidate.input_id identifies its own candidate.element_set_id. §6 line 109 creates a different effective set/element/evidence graph; no existing column maps that new element back to the old candidate element.
- generation_input.element_set_id is the frozen input set for a later operation, not a persisted per-output-element origin. minutes_revision.generation_input_id is a body chain, and body-only/element-only changes deliberately separate these lifecycles. action_suggestion covers ACTION only.

For a copied DISCUSSION element with a local PDF locator, the locator establishes F/page but not M/D/parser. Two prior inputs can legitimately contain that physical locator under different parser contexts. Matching identical text/anchor/locator against historical candidates is not a unique provenance key. review.hash_profile archives encoding rules, not the missing per-element origin; result_review_evidence only freezes evidence IDs. A correct supplied-facts hash cannot itself make this projection recoverable after reload.

Required correction: before freezing the ReviewFacts contract, specify an exact derivation from existing immutable columns for every supported target/origin path and state which input identity the pure helper must verify. If the current schema cannot represent an original parser context for a path, define a deterministic unsupported/provenance-unavailable result for that path and keep it out of the supported review/final profile until a separately reviewed design resolves it. Do not infer newest input/parser, use a coincidentally matching candidate, claim an evidence.input_id column, or attach raw JSON. Transcript-only intrinsic locators can be treated separately: their exact revision/segment plus unique confirmation is recoverable and does not need this missing local-document origin edge.

Future verification: supplied-fact positive case with a demonstrably unique stored chain; negative copied non-ACTION/local-parser case with two matching historical candidates; restart must not resolve by collection order or current config. Runtime query completeness/ACL still belongs to S2, but the pure contract must make its provenance prerequisite explicit rather than declare this missing edge recoverable.

Severity: Important. This is a technical reconstructability/supported-domain correction. A narrow fail-closed S1 scope can resolve it without schema edits or UX approval; unrestricted recovery requires separate explicit design.

## Reviewed areas without additional blocking findings

- Exactly seven subject kinds plus distinct sensitive/background directories; both sensitive labels survive shared locator reuse. Empty categories do not manufacture rows. Raw action suggestions remain separate from all-NULL initial accepted fields and never grant identity/ACL.
- Strict Reader-based UTF-8 decoding, escaped duplicate detection, exact key/type/EOF rules, scalar validation, Gregorian dates, integer lexeme bounds, redacted failures, caller stream ownership and cancellation limits are appropriate. Byte sentinel and explicit per-factory/application budgets correctly distinguish byte/UTF-16/scalar counts; implementation must honor the stated preflight/count checks.
- Canonical tags/lengths/null distinctions, unsigned UTF-8 object ordering, schema-defined sets, ordered lists, domain envelope and archived profile identity are deterministic. Defensive ownership and bounded encode/hash are appropriate. Literal independent vectors and future complete domain vectors are required; no implemented behavior is certified here.
- Full target-text scalar anchors, full overlapping evidence-set membership, maximum exact-scope review_no and no partial-span-to-whole credit are conservative and consistent with B00-1.3. Structural validity cannot return SUPPORTED or establish semantic entailment. Human review/identity/ACL remain outside S1.
- Generation fingerprint includes immutable snapshot identities, mark metadata, full row projections, TaskSpec businessVersion/cancelEpoch and pinned config dependencies. Compute-before-TaskSpec avoids a circular dummy digest. Omitted mutable material statuses are expressly identified; current UI text is not a hidden input.
- Final profile recovery through final→minutes_revision→generation_input→archived config is coherent for rows retaining that chain; no-input rows correctly fail PROFILE_UNAVAILABLE. This resolves encoding-profile recovery, not I1/I2 source-context recovery. Historical review membership remains exact and does not query later reviews.
- Local Spring Boot BOM manages Jackson 2.21.5 separately from Jackson 3. Local javap confirms the stated constraint Builder methods and strict/features APIs. Managed direct jackson-core in adapters, JDK-only core/contracts and one S1 owner avoid competing writers/dependency leakage.
- B06 confirmation profile alignment remains explicitly required before wiring. Nonempty materials, real model protocol/quality, permissions, transactions/fencing, formal V012/V013 and B08 are not approved. No production quota or enterprise dictionary is invented.

## Exact reviewed input SHA-256

Relative paths are rooted at `/home/tanren/ama/.worktrees/phase-a`; SDD means `.superpowers/sdd/2026-10-21-ama-phase-b`.

|Input|SHA-256|
|---|---|
|SDD/task-B-07-contract-final-proposal.md|eaae0ee74d2330cb4bcd32525e445e3809c238e139574477306c761742b96f05|
|SDD/task-B-07-contract-final-report.md|b9fd896a5fce45d066b076ad2022cd9a358f53d961d5fa293dcad1a4c7c903ed|
|SDD/task-B-07-contract-review-brief.md|c57ff91c3b47f7bf60764df7cc543b8634ab1ecb88849402741521ce2b250362|
|SDD/task-B-07-contract-finalization-brief.md|3c42b7ceb45b1b45b8bb3f1b334edef41fa9ff234f146d4dcead1fa203d8dbd3|
|SDD/task-B-07-brief.md|6936ab55fae017b2bcebe5aac37751bedc594834c80ef3b83208c2f56305a8a9|
|docs/superpowers/specs/2026-10-21-ama-product-supplement.md|34a70d05e60b513faa8b7e98ba17e0d68008dcd05e260c210cbeb24bb181900c|
|docs/superpowers/specs/2026-10-21-ama-database-design.md|c4ae00740c7d72e2945d0f67a926631bba198c7ecde2a46cd66523293b21e8d4|
|docs/database/ama-data-dictionary.md|7d34063a86573c6812924a27fff6dc5f2b98cc849dabafb16b9487074fac1d8b|
|docs/database/ama-schema-draft.sql|ebf1eae51b303d00b2ed35c4fbd5ba4c166813564032875a6ba0fb35ba743c93|
|docs/database/migration-register.md (current read)|97d97139b4c87c9052c4266b8cf00e482075d2b9c7da3c4745d422e3548f5dd4|
|docs/acceptance/b/database-generation-addendum-review.md|1b8199c9bda2efb6de33c162c59bd65630777fad9b178b1e536c877760977f54|
|docs/acceptance/b/database-generation-addendum-independent-review.md|242397c7d2d90689c44ccb814f0bde3d1e8a319fb593f57b843d7dc5788655a6|
|backend/contracts/src/main/java/ama/contract/TaskContracts.java|6641098514d21f4cba9b2ab82b7a6eaea9894f162a563447c69859e9dbdb9880|
|backend/adapters/pom.xml|642846d573a5e78959be183b19eb3cd7f86155c6dc87011baec9dedde4cc668d|
|backend/core/pom.xml|b3f8f975cdd5f8b5e7e0536dcdaff7008d94fff08a4008229c87d26182622981|
|backend/contracts/pom.xml|e4c80faa1124833874f99e8645d76652e8de41fb40ef85b7012d954b2e9b1717|
|/home/tanren/.m2/repository/com/fasterxml/jackson/core/jackson-core/2.21.5/jackson-core-2.21.5.jar|b64b5874162b503a0e58a8f7758266e8dd9f91bf49e3a59ae0b5f47589a231b7|

The current register has a later V011/V011.1 adoption append, so its SHA differs from the author's earlier captured register SHA. B00-1.3 adoption remains explicit; this is not evidence that the author's capture was incorrect. No Git baseline revalidation was performed under this review's no-Git scope. Prior 93-table/709-column database evidence is adopted recorded evidence, not a new measurement by this reviewer.
