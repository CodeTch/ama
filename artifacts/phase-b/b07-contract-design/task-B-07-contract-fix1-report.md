# B07 contract fix1 report

2026-10-22. Design response only; no S1 implementation or independent approval claimed. Root baseline `260b6f84a17c06605ff9f3f4eb811069f9ab099b`; adopted B00-1.3 design unchanged. Output proposal is a precise normative replacement plus explicit unchanged-section references, 84 lines (under 220).

## Delivered and exact finding response

New proposal: `task-B-07-contract-fix1-proposal.md`, SHA-256 `e9a5a7cccb49e20640fee790fbcf866257e74befaa997bf89260f384dba08c00`.
New report: this file. These are the only files written in this fix round; neither prior proposal/report pair was changed. Both pending UX answers remain pending; no workflow or production activation.

|Finding / instruction|Normative response|Author status|
|---|---|---|
|I1: sourceRows collapses parser contexts|Proposal §2 separates unique FullRow locatorRows from complete sourceUsages; exact occurrence unions/keys, discriminators, ordering, FK checks and membership bijection. N1/G1/P1 and N2/G2/P2 sharing L retain two usages. §4 embeds both arrays in REVIEW_SOURCES/FINAL_SOURCES and includes inherited input-set graph.|Addressed in proposed contract; scoped independent review required.|
|I2: copied ordinary local evidence has no stored origin edge|Proposal §3 enumerates body/detection/note/ACTION/intrinsic transcript/applied exclusion paths using actual fields. No candidate/text/last-config inference. Missing non-ACTION/local origin fails PROVENANCE_UNAVAILABLE before hash; ambiguous selections and incomplete memberships have separate fixed failures.|Addressed by explicit supported-domain restriction, not universal provenance recovery.|
|Root: final profile recovery despite nullable body input|Proposal §5 retains final → minutes_revision.generation_input_id → archived config/profile dependency graph; manual/restore inherits original chain; absent chain PROFILE_UNAVAILABLE.|Retained and separated from source provenance recovery.|
|Root: confirmation lacks hash_profile column|Proposal §5 resolves transcript_revision.processing_input_id → transcription_input.processing_config_version/config_manifest/manifest_schema_version → fixed CONFIRMATION_HASH/codec. Manual/mark revisions inherit chain; future conversion requires new immutable processing input, never latest.|Explicit proposed binding; B06 alignment still required.|
|Root: minimum pure S1, later required D01 provenance|Proposal §6 permits only verifiable supplied facts on supported stored chains, keeps minimal TXT/codec/parser feasible, and qualifies complex production materials until separately reviewed D01 design. Future persistent edges require own B00 increment/registered version; 19-table V012 untouched.|Scope preserved; required feature remains outstanding, not permanently disabled.|

The old claim that every target's original generation graph is recoverable is explicitly superseded. Ordinary intrinsic transcript provenance identifies exact confirmation/revision/segment without falsely asserting an original generation input. Applied exclusion context is explicitly APPLIED_INPUT, not a purported original detection-selection edge; its fixed exclusion revision must agree with the final body input. These are concrete proposed semantics for independent review.
Version choices are explicit [P]: GENERATION_HASH becomes ama.generation-input.v2, REVIEW_HASH ama.result-review.v2, FINAL_SOURCES ama.final-sources.v2. Common codec and physical ROWSET profile stay v1. No new profile-kind/database column/file artifact is required. PINNED input elements include SourceGraph so source contexts inherited with a mixed set are actually encoded; no change to EMPTY input shape. Historical unapproved v1 schemas are not reinterpreted.

## Verification performed and limits

Read the complete independent review and verified its supplied SHA. Re-read the old final proposal's source/profile/hash sections and relevant exact dictionary fields, including transcript_revision and transcription_input. Cross-checked the existing columns proving I2: evidence_link/element_set/minutes_element lack origin input; body, normalized directories and action_suggestion have the specified paths. The complete review's table audit and existing B00-1.3 adoption records remain supporting recorded evidence, not a fresh database measurement.
Read-only local verification: SHA-256 checks for four preserved historical files, review and three adopted database-design artifacts; line count of new proposal; repository status observation. The SDD path is ignored by normal Git status, so a blank status is not claimed as proof that the new documents are tracked or reviewed. Scope proof is the explicit two new-file writes and unchanged hashes below. No Git mutation/commit occurred.
An initial PowerShell hash-list display command had a syntax error before execution; it wrote nothing and was corrected. Successful repeated hash reads produced the values below. No application test, build, compilation, JSON parsing execution, model call or database/service operation was run. Future tests in proposal §6 are required implementation work, not executed evidence.

|Preserved/reviewed file (relative to phase-a)|Verified SHA-256|
|---|---|
|SDD/task-B-07-generation-contract-proposal.md|80bde48809691a9883f76ff17affcc71c6003f749289f8d162d18a984ff148f9|
|SDD/task-B-07-generation-contract-report.md|14ad5e6bea73247044d5e593d9b176ccb03ca76c48e5983d5df69cb41c39fae6|
|SDD/task-B-07-contract-final-proposal.md|eaae0ee74d2330cb4bcd32525e445e3809c238e139574477306c761742b96f05|
|SDD/task-B-07-contract-final-report.md|b9fd896a5fce45d066b076ad2022cd9a358f53d961d5fa293dcad1a4c7c903ed|
|SDD/task-B-07-contract-review.md|6751a78f3747f06217955dfcf5e88875f04f3ad3b94965bdc5aa82e33b7961d7|
|docs/superpowers/specs/2026-10-21-ama-database-design.md|c4ae00740c7d72e2945d0f67a926631bba198c7ecde2a46cd66523293b21e8d4|
|docs/database/ama-data-dictionary.md|7d34063a86573c6812924a27fff6dc5f2b98cc849dabafb16b9487074fac1d8b|
|docs/database/ama-schema-draft.sql|ebf1eae51b303d00b2ed35c4fbd5ba4c166813564032875a6ba0fb35ba743c93|

SDD = `.superpowers/sdd/2026-10-21-ama-phase-b`. All four historical file hashes match their prior captured bytes. No source, POM, SQL, schema, migration register, acceptance record or existing SDD file was edited. No subagents were spawned.

## Remaining review and capability boundaries

Root/fresh scoped reviewer must assess I1 cardinality and exact domain bytes, I2 stored-proof matrix and negative cases, the v2 profile choices, APPLIED_INPUT exclusion meaning, and the explicit confirmation-profile chain. These are proposed technical decisions, not silent implementation discretion. Any later profile/graph expansion must get its own review.
S1 cannot authenticate caller facts or supply missing database edges; S2 later owns authoritative query completeness/ACL and immutable archive acquisition. Complex material parser selection and general copied ordinary-element provenance need D01 persistence design before production activation; no claim that the narrow profile completes those future requirements. Two product UX decisions, real model/protocol/quality evidence and all runtime/B08 gates remain unresolved as before.
