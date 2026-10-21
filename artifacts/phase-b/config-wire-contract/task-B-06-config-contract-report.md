# B06/B07 immutable config contract design report
2026-10-22. Author /root/b06_config_contract_design; design-only delivery for root FULLREAD and fresh independent SPEC/technical-quality review. No implementation approval or successful application testing is claimed.
Workspace: /home/tanren/ama/.worktrees/phase-a. Required brief was read first. Brief root8da440d; coordinator subsequently reported V012 adoption at5eb00d0f00af51ef95b24e9af209395c64e1e85b with unchanged B00-1.3. I did not inspect that new SQL or unreviewed S1 Java.
Only two writes: SDD/task-B-06-config-contract-proposal.md and this report; SDD=.superpowers/sdd/2026-10-21-ama-phase-b. Proposal SHA256: **427451ba18bf1a72ce77da7eea87c72a6e4dcf885cb77617ab660b68a94e9832**. Proposal is109lines by the filesystem line count, below220. Final document integrity checks reread all five fenced literal vectors from the saved report; every stated byte length and SHA matches the saved UTF8 literal. No other file creation/edit, source/POM/SQL/migration/fixture/oracle changes, Git command/mutation, build/test/DB/Docker/service/model/company/network operation or child agent.

## Requested gap and complete proposed outcome
The adopted B07 fix1 requires a raw config_manifest to prove its owning confirmation profile. R5 and actual columns provide the semantic storage position but no exact wire schema. Passing an asserted typed profile with '{}' would hide that missing proof.
The proposal supplies a strict canonical-JSON manifest and immutable archive envelope, separate raw-byte SHA and adopted binary typed hash, exact IMPORT/EMPTY shape, complete archive graph schema/dependencies, known-definition validators and DB row/TaskSpec agreement. A caller cannot construct a successful binding from profile booleans; raw bytes and actual detached rows are mandatory. S1 owns only pure verification, not authenticity/ACL/resolver/network responsibility.
Its only new hash-domain label is TRANSCRIPTION_INPUT with profile ama.transcription-input.v1. This is a pure domain separator, not a database enum, profile kind or file-reference kind. Existing B07 confirmation projection/four-mark order, generation v2, review v2 and final v2 remain intact. The15 generation profile kinds remain exactly unchanged.
The processing manifest fully pins files/order/SHA/size, explicit EMPTY, parser/config/profile refs, budgets via immutable config SHA, base revision/business version/cancel epoch and one initial step with null model/prompt/source revision/parent/output. The parsed manifest must agree with the actual existing input/batch/file rows and producing task/revision chain before a hash is usable.
Generation roots are exact PROMPT_CONFIG and MODEL_CONFIG versions already stored by generation_input. They declare the same15-profile graph, including a transcript-only CATALOG and fixed DEFAULT template binding. Historical root raw SHA is recovered into the recomputed saved generation fingerprint, never selected by latest. Confirmation resolves its original processing archive; generation must agree with that exact older confirmation/codec Ref. Final resolves its saved body's generation input and recomputes its fingerprint before selecting FINAL_SOURCES.
Schema is fully specified even where runtime capability is unqualified: safe model semantic fields/prompt instructions/renderer identity are frozen config, no concrete company values invented. Hash consistency does not execute or qualify a model/renderer. Other provider parameters or advanced source capabilities require versioned reviewed schemas; absence of a company adapter does not block the pure readers/verifier/tests.
No batch-hash algorithm was inferred. Processing hashing includes the complete batch file projection directly; existing input_batch.input_fingerprint remains separately owned future B06 work before runtime writes. No complete B06/domain/runtime qualification is claimed.
ASR, ENHANCE, chained media, multi-AV and PINNED processing material stay explicit future gates. The proposal preserves R5's typed parent-input/prior-output/frozen-step obligations, rejects unrecognized v1 alternatives, and does not invent persisted origins or blob/file kinds. Existing D01 provenance restrictions remain binding.

## Choices and self-review
Chosen over alternative1 (prevalidated boolean/profile facts): actual raw schema validation + dependency SHA/header equality + stored-row/fingerprint agreement is independently falsifiable by '{}' and byte perturbation.
Chosen over alternative2 (hash-only/latest roots): immutable type+version identity plus raw-byte digest and exact closure retains recoverable safe settings and detects version/SHA conflicts without digest inference.
Chosen over alternative3 (new DB config tables/binary business blobs): canonical JSON uses the existing LONGTEXT and existing version columns; the adopted canonical binary codec continues to separate typed business hashing from transport.
The JSON formatter has explicit key order/escaping/EOF rules; it is not a handwritten parser. Bootstrap read limits protect the manifest/archive before trusting any embedded budget. Pinned semantic budgets remain separate. Byte arrays are length-checked then defensively copied once, so SHA/parser/canonical comparison inspect the same bytes.
The graph includes only the closure of roots actually reached through stored processing/generation/review rows; no caller-chosen unrelated artifact can justify extras. All duplicates, missing/extra slots, unknown schemas, contradictory root dependencies, conflicting identity/SHA, cycles and cumulative resource overruns fail without a partial handle/digest.
Author self-review checked field/null mapping, no checksum self-reference, generation-root agreement, scalar/duplicate/EOF behavior, typed dependency ownership, default-template binding, archive/schema recognition before business hashing, and absence of any production enablement. This is author reasoning, not independent review or an executable test.
A broad read-only filename search briefly encountered permission-denied paths in other existing worktree tool/data directories. It read no protected contents, made no changes and was replaced by targeted source paths. One earlier combined read was truncated by tool output, so the normative final/fix1 sections needed by the proposal were reread in smaller slices. No failed tool launch was counted as evidence.

## Independently assembled literal raw-byte vectors
These are SYNTHETIC DESIGN VECTORS, not application test results or production defaults. Four archive JSON literals below are complete, not templates. M is the complete one-file IMPORT manifest. Field strings/integers/ASCII keys were assembled independently of any production codec or JSON adapter; each literal's exact UTF8 bytes were hashed by .NET SHA256 in PowerShell. No application code ran.
Each fenced literal is one line; the fence and its surrounding newlines are NOT bytes. The literals contain no BOM or terminal LF. The source file for M is exactly the single byte41hex (ASCII A), size1, SHA256559aead08264d5795d3909718cdd05abd49572e84fe55590eef31a88a08fdffd. UUIDs, version test.import.1, all budgets and fence7/9/2 are synthetic examples only.
Archive raw hashes are external Ref commitments. M's listed SHA is its raw JSON SHA for transport checking, NOT its domain-separated ProcessingFingerprint. S1 must additionally provide independent full canonical-domain bytes/digest and runtime reader rejection/positive tests before code acceptance; this report does not falsely label raw JSON SHA as the business fingerprint.
### C (codec)
UTF8 length=223; SHA256=2b553c99cecc917b12a466cd62859df2ac18533e518476aa1b843279f2eb6e19.
```json
{"archiveSchema":"ama.config-archive.v1","artifactType":"CANONICAL_CODEC","definition":{"contract":"ama.canonical-value.v1"},"definitionSchema":"ama.profile-contract.v1","dependencies":[],"version":"ama.canonical-value.v1"}
```

### P (parser)
UTF8 length=226; SHA256=7b9f57377ad401fe040287e133f3efa81d9956ea581a5e497ee6c0835051962a.
```json
{"archiveSchema":"ama.config-archive.v1","artifactType":"PARSER_CONFIG","definition":{"contract":"ama.strict-utf8-lines.v1"},"definitionSchema":"ama.txt-parser-config.v1","dependencies":[],"version":"ama.strict-utf8-lines.v1"}
```

### H (confirmation)
UTF8 length=419; SHA256=2f94e75e8d53ef37a152a1ccd63b54a61a6853e07508a5c927a4ba8e8209c0aa.
```json
{"archiveSchema":"ama.config-archive.v1","artifactType":"CONFIRMATION_HASH","definition":{"contract":"ama.transcript-confirmation.v1"},"definitionSchema":"ama.profile-contract.v1","dependencies":[{"ref":{"artifactType":"CANONICAL_CODEC","sha256":"2b553c99cecc917b12a466cd62859df2ac18533e518476aa1b843279f2eb6e19","version":"ama.canonical-value.v1"},"slot":"CANONICAL_CODEC"}],"version":"ama.transcript-confirmation.v1"}
```

### I (synthetic processing config)
UTF8 length=987; SHA256=e4e58cadff0045688e0cab8200018af7c4407a39a1ca97924f14b68c6d51765b.
```json
{"archiveSchema":"ama.config-archive.v1","artifactType":"PROCESSING_CONFIG","definition":{"canonicalBudget":{"maxContainerEntries":4096,"maxDepth":32,"maxEncodedBytes":65536,"maxNodes":4096,"maxStringBytes":4096},"contract":"ama.import-txt.v1","manifestSchemaVersion":1,"parseBudget":{"maxSegments":16,"maxSourceBytes":1024,"maxUtf16CodeUnits":1024}},"definitionSchema":"ama.import-txt-config.v1","dependencies":[{"ref":{"artifactType":"CANONICAL_CODEC","sha256":"2b553c99cecc917b12a466cd62859df2ac18533e518476aa1b843279f2eb6e19","version":"ama.canonical-value.v1"},"slot":"CANONICAL_CODEC"},{"ref":{"artifactType":"CONFIRMATION_HASH","sha256":"2f94e75e8d53ef37a152a1ccd63b54a61a6853e07508a5c927a4ba8e8209c0aa","version":"ama.transcript-confirmation.v1"},"slot":"CONFIRMATION_HASH"},{"ref":{"artifactType":"PARSER_CONFIG","sha256":"7b9f57377ad401fe040287e133f3efa81d9956ea581a5e497ee6c0835051962a","version":"ama.strict-utf8-lines.v1"},"slot":"PARSER_CONFIG"}],"version":"test.import.1"}
```

### M (synthetic manifest)
UTF8 length=1763; SHA256=7c2ec8c7fdc8e42df32ae0043748b64c4f1989007553a7fd69f2b183c1acbe0b.
```json
{"base":{"cancelEpoch":2,"meetingRevision":7,"subjectBusinessVersion":9},"batch":{"files":[{"fileId":"44444444-4444-4444-8444-444444444444","ordinal":1,"sha256":"559aead08264d5795d3909718cdd05abd49572e84fe55590eef31a88a08fdffd","sizeBytes":1}],"id":"33333333-3333-4333-8333-333333333333","kind":"TRANSCRIPT"},"configs":{"model":null,"parser":{"artifactType":"PARSER_CONFIG","sha256":"7b9f57377ad401fe040287e133f3efa81d9956ea581a5e497ee6c0835051962a","version":"ama.strict-utf8-lines.v1"},"processing":{"artifactType":"PROCESSING_CONFIG","sha256":"e4e58cadff0045688e0cab8200018af7c4407a39a1ca97924f14b68c6d51765b","version":"test.import.1"},"profiles":[{"kind":"CANONICAL_CODEC","ref":{"artifactType":"CANONICAL_CODEC","sha256":"2b553c99cecc917b12a466cd62859df2ac18533e518476aa1b843279f2eb6e19","version":"ama.canonical-value.v1"}},{"kind":"CONFIRMATION_HASH","ref":{"artifactType":"CONFIRMATION_HASH","sha256":"2f94e75e8d53ef37a152a1ccd63b54a61a6853e07508a5c927a4ba8e8209c0aa","version":"ama.transcript-confirmation.v1"}}],"prompt":null},"inputId":"22222222-2222-4222-8222-222222222222","kind":"IMPORT","materials":{"items":[],"mode":"EMPTY"},"meetingId":"11111111-1111-4111-8111-111111111111","profile":"ama.transcription-input.v1","schemaVersion":1,"sourceRevisionId":null,"steps":[{"config":{"artifactType":"PROCESSING_CONFIG","sha256":"e4e58cadff0045688e0cab8200018af7c4407a39a1ca97924f14b68c6d51765b","version":"test.import.1"},"kind":"IMPORT_TXT","model":null,"ordinal":1,"parentInput":null,"parser":{"artifactType":"PARSER_CONFIG","sha256":"7b9f57377ad401fe040287e133f3efa81d9956ea581a5e497ee6c0835051962a","version":"ama.strict-utf8-lines.v1"},"priorOutput":null,"prompt":null,"source":{"id":"33333333-3333-4333-8333-333333333333","kind":"INPUT_BATCH"}}]}
```

For an independent literal stored-row fixture, use M's meeting/input/batch/file IDs; input row kindIMPORT, source_revision_idNULL, material_modeEMPTY/material_snapshot_idNULL, processing_config_versiontest.import.1, parser_versionama.strict-utf8-lines.v1, model_config_versionNULL, prompt_versionNULL, manifest_schema_version1, base_revision7, cancel_epoch2; config_manifest is M exactly. The batch is TRANSCRIPT with the single ordinal1 row; file_object same meeting, size_bytes1 and the source SHA above. Task subjectMEETING/inputTRANSCRIPTION_INPUT, businessVersion9/cancelEpoch2; input row/task/producer revision use the independently computed ProcessingFingerprint, never M's raw SHA. Noncontent audit/time/task scheduling values belong to their ordinary owning fixture contracts.
The minimal archive closure is exactly I,C,H,P. I.dependencies agrees with M.configs and step refs; H→C. Changing any one byte of any archive without updating the ref fails raw SHA; changing all legitimate ancestor refs requires a new immutable config version and changes the processing fingerprint. A repeated current setting cannot rebind this old version.
Required future negative coverage is enumerated in proposal §7, including raw '{}', malformed/duplicate/trailing JSON, unknown definition/schema, wrong raw SHA, root profile conflict, cycles/missing parent, IMPORT nonnull model, field perturbations, reload/current-config change, budget/cancel boundaries and nullable legacy final chains. No test-only helper may mint a success binding bypassing raw validation.

## Exact sources and read scope
Full normative final proposal/fix1 plus adopted wrapper and scoped review were read; B00 R5/R6 and relevant tables (file_object, batch/files, input/revision/segment/confirmation, generation/minutes) were inspected, not the entire unrelated design. Plans/requirements/product/integration sources were read in relevant sections. B05 current TaskContracts/TaskQueue and fix1 consumer constraints were inspected. B06 parser interface and full implementation were read to preserve existing profile/SHA/UUID behavior. Company LLM document was read only for its text-correction protocol and capability limitation; no contained endpoint/model example is copied into the proposal.
The following SHA256 values identify complete source file bytes even where only relevant sections were read. They are integrity observations, not claims every line of each file was reread. No SQL file was consumed or rewritten for this task.
|Source relative to phase-a|SHA256|
|---|---|
|.superpowers/sdd/2026-10-21-ama-phase-b/task-B-06-config-contract-design-brief.md|481cccb3adea2479627b6348d2b739a11e54532c16861ed7be4a7c93e570263e|
|.superpowers/sdd/2026-10-21-ama-phase-b/task-B-06-brief.md|cfc6d58ca32cb70e54db8eb586fa96fd07dc5620b8c7dac1be539f130bd975d0|
|.superpowers/sdd/2026-10-21-ama-phase-b/task-B-06-parser-brief.md|fbfd7163ad7440cb99ce6d5a7adedf5c950c701b48118db8256a148e1620a444|
|docs/superpowers/specs/2026-10-22-ama-minutes-contract.md|d43386ffd2050bb80cb8924794bd27d9132a4117cfc279cb29253610083a4e24|
|artifacts/phase-b/b07-contract-design/task-B-07-contract-final-proposal.md|eaae0ee74d2330cb4bcd32525e445e3809c238e139574477306c761742b96f05|
|artifacts/phase-b/b07-contract-design/task-B-07-contract-fix1-proposal.md|e9a5a7cccb49e20640fee790fbcf866257e74befaa997bf89260f384dba08c00|
|artifacts/phase-b/b07-contract-design/task-B-07-contract-fix1-review.md|02618879c991061d1c03c907e8abd5ad0ff80db7ca79509b55a1ee68f2f00657|
|artifacts/phase-b/b07-contract-design/task-B-07-contract-fix1-report.md|6774ea05ff2cfee4d53471790a7eeb9a8ffc68d3385c149fcc90bde0c074fac3|
|artifacts/phase-b/b07-contract-design/task-B-07-contract-review.md|6751a78f3747f06217955dfcf5e88875f04f3ad3b94965bdc5aa82e33b7961d7|
|docs/superpowers/specs/2026-10-21-ama-database-design.md|c4ae00740c7d72e2945d0f67a926631bba198c7ecde2a46cd66523293b21e8d4|
|docs/database/ama-data-dictionary.md|7d34063a86573c6812924a27fff6dc5f2b98cc849dabafb16b9487074fac1d8b|
|docs/superpowers/plans/2026-10-21-ama-phase-b.md|bfa789f77b5ca90725b2c0f32977993c260ac79ab22c2fff76cd775cc104c862|
|docs/superpowers/specs/2026-10-21-ama-product-supplement.md|34a70d05e60b513faa8b7e98ba17e0d68008dcd05e260c210cbeb24bb181900c|
|docs/superpowers/specs/2026-10-21-ama-requirements-matrix.md|19af58ab580403b6ad615e856eb9bf40fba814cdb4d5832e5acb4cae69362d7c|
|docs/contracts/integration-catalog.md|cdf309d6759889125199624432370e9b1a7d282ffe832da473afac5234518f8b|
|docs/acceptance/b/task-B-05-fix1-report.md|db35d200702f23d598956f98d2d3e4a3f00ad806c6c98d6458154fc332fdf5e6|
|backend/contracts/src/main/java/ama/contract/TaskContracts.java|6641098514d21f4cba9b2ab82b7a6eaea9894f162a563447c69859e9dbdb9880|
|backend/core/src/main/java/ama/task/TaskQueue.java|4244597da8dad036ffe2d5e90ac4a8ccb8c5ff21bebe30c85d114b72bbc90a33|
|backend/core/src/main/java/ama/transcript/TranscriptDocumentParser.java|cfb4c1d8e31a86367f82c7e02148321ff8588576654937fe12aaf27a587b10f3|
|backend/adapters/src/main/java/ama/transcript/TextTranscriptParser.java|497c1bfbbab55554b3fc8dc26c4b4f0031cba0ecae35ecd9979a5bb7394ac04a|
|designDocs/线上模型服务接口文档.md|be60b22cf6d05ddf534b83015a2993910cd1c395e480be908caadc71376bb8b2|

The using-superpowers skill explicitly exempts dispatched subagents; brainstorming guidance was used for architecture alternatives and author self-review. The explicit task brief supersedes that skill's generic spec location/commit convention: only the two named SDD documents are allowed, no Git mutation or implementation here. No skill-derived extra approval question was sent.

## Handoff and remaining limits
Root must FULLREAD both outputs and commission a fresh independent technical review before the existing S1 single owner consumes this contract. The proposal explicitly reserves new immutable config contracts/readers/verifier/processing hash helper to that author, avoiding shared MinutesContracts/canonical/JSON parallel writers. No unreviewed peer code or source API was treated as adopted.
Actual immutable archive publication/index durability/acquisition, file/source facts/ACL completeness, persistence/restart behavior, B06 service/task/confirmation inheritance, candidate/adoption/final flows, renderer/model protocol/quality/capacity and operational production format/budget approval remain later work. No historical raw DDL fixture is rewritten as valid configuration. Source authenticity cannot be proven by a pure function; supplied facts must be acquired authoritatively by S2.
Both user UX decisions remain pending with no default activation. No production configuration, real company call, schema qualification, E03 configuration rollout or whole-B completion is claimed. This design deliberately unblocks pure code without representing synthetic data as enterprise qualification.
