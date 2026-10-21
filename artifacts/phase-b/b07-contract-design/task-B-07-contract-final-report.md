# B07 pure-S1 final contract preparation report

2026-10-22. Status: revised proposal prepared for root full read and fresh independent contract review; not S1 implementation approval or B07 completion.

## Scope and exact baseline

- Root/source baseline observed before and after reads: `2581a001b0b08613e1ed7cfd5ba1c1af9ac56811`, `/home/tanren/ama/.worktrees/phase-a`.
- Adopted design: B00-1.3, author `7fb166b`, integration `8e44080`, coordinator adoption at current root. Registry adoption section + acceptance coordinator addendum + independent PASS/0 findings establish adoption; old “待评审” headings remain historical source wording, not a claim of current pending design. 93 tables / 709 columns are prior captured design evidence, not a new run here.
- New outputs only: `task-B-07-contract-final-proposal.md`, this `task-B-07-contract-final-report.md`.
- Original proposal SHA `80bde48809691a9883f76ff17affcc71c6003f749289f8d162d18a984ff148f9`; original report SHA `14ad5e6bea73247044d5e593d9b176ccb03ca76c48e5983d5df69cb41c39fae6`; both checked unchanged before final output.
- No code/POM/schema/SQL/migration/original-source edit; no implementation, compile, Maven, tests, DB, Docker, service, HTTP/company request or child agent. Read-only Git/file/hash inspection, local javap API inspection and SHA calculation of literal design vectors only. No unreviewed peer Java imported or consumed. Tracked status remained clean; new SDD files are ignored outputs, verified explicitly.

## Concrete changes from original proposal

1. R2 graph reconstruction is the adopted technical direction. Exact manifest fields, row projections, archived profile dependency kinds and compute-before-TaskSpec / verify-on-reload seams now specified. No blob/extra column fallback. Mutable material reference/parse state excluded from hash projection; runtime eligibility separately checks status. Config/archive/provider completeness remains prerequisite for S2, not a pure codec assumption of actual DB access.
2. R3 attachment/storage alternatives removed: five normalized directory/suggestion tables retain all sensitive/background fields and unverified action data; three review/membership tables retain exact assertions/history. Both labels share exact locator and exclusion PK includes category. Accepted action_item owner AND date remain NULL initially; original proposal's structurally valid suggested date in accepted due_on is superseded.
3. R4 creation classification remains conservative (valid refs PARTIAL, no sources UNSUPPORTED, background-only CONCLUSION/ACTION UNSUPPORTED). SOURCE_SUPPORT and ACTION_FIELDS are separate. Proposed review/full-source/target/final hash projections and exact-scope coverage avoid converting schema checks/partial-span support into whole-item proof. Review activation/intent/ACL are outside S1.
4. Common JDK-only CanonicalValueCodec is proposed, shareable with future B06 confirmation hash. Strict scalar UTF8, fixed four-mark ordering, null/EMPTY distinctions, numeric/set/key ordering, bounded allocation/cancellation, domain separation and four independent literal-byte golden vectors specified. No B06 parser UUID/hash behavior altered. B06 confirmation profile still needs root alignment before wiring.
5. Exact output JSON retains all seven kinds/sensitive/background fields; alias changes from fixed 8-digit width to `s` + positive decimal long. Canonical anchors use scalar ranges/full target text hash. SourceVersion projections now include source file/material/parser versions and hashes needed for reviews/final history.
6. JSON parsing is assigned to adapters using official Jackson 2 streaming APIs with strict duplicate/features/UTF8/EOF/limits and redacted failures; typed codec stays core. No handwritten JSON lexer, ObjectMapper permissive binding, AWS/Spring Web dependency in core/contracts. One future S1 owner may add direct versionless managed jackson-core in adapters and test-only managed JUnit where absent.
7. Both user UX questions remain pending without recommended/default activation. They no longer block isolated S1 technical implementation after root/fresh contract approval. Later integration retains human title protection, explicit relink, auto+explicit save, unsaved draft preservation and candidate/effective distinction.
8. Added root-requested final hash recovery: final→minutes_revision→generation_input→fixed config FINAL_SOURCES profile; per-review hash_profile stays independently archived. Manual save/restore inherits its original input/profile chain. Nullable/no-input historical or pure manual row fails PROFILE_UNAVAILABLE; no latest-profile reconstruction, schema addition or B08 implementation.

## Local dependency/API evidence

- Parent Spring Boot 4.1.1 BOM imports Jackson **2** BOM 2.21.5 and separately Jackson **3** BOM 3.1.5. This contract chooses existing com.fasterxml.jackson.core 2.21.5 namespace; no upgrade or version pin proposed.
- adapters POM already directly declares jackson-databind; contracts has no dependencies, core depends only on contracts. Direct jackson-core managed declaration is a minimal future ownership clarification; no POM changed today.
- Local jar `javap` confirmed StreamReadConstraints.Builder: maxNestingDepth(int), maxDocumentLength(long), maxTokenCount(long), maxNumberLength(int), maxStringLength(int), maxNameLength(int), build(); TSFBuilder.streamReadConstraints; strict duplicate/source-location/source-close feature APIs; JsonReadFeature permissive switches; parser token/text/long methods. This is API inspection, not execution of validator or proof of its future behavior.
- One initial class-name shell expansion displayed outer StreamReadConstraints instead of nested Builder; retried with escaped dollar and inspected exact nested Builder successfully. No file/source effect. Literal-vector SHA preparation had one PowerShell syntax-only failed invocation; corrected invocation hashes fixed hand-derived bytes and is not a test/implementation run.
- Four vector hashes come from .NET SHA256 over explicit literal hex, independent of any proposed codec. Full domain vectors are mandatory future S1 test deliverables, not claimed complete test execution.

## Remaining approvals and limits

Fresh independent contract review should focus on strict schema/budget/API details, canonical/domain projections, B06 confirmation alignment, exact-scope coverage, immutable config/final-profile recovery and single-owner module split. Technical choices are fully specified proposals to accept or revise, not silently adopted implementation facts.
S2 authority/locking/restart/archive completeness; S3 actual company minutes protocol and quality; S4 normalized persistence/ACL; S5 runtime atomicity/CAS/fencing and B08 finalization remain separate. First effective save and review UX still require user decision before activation. Real production formats, model limits/credentials/semantics, source permissions, external owner mapping and enterprise categories/roles cannot be inferred from design fixtures.
Original LLM document specifies text correction transport, not minutes JSON-mode/quality/capacity or cancellation guarantees. Original approved product supplement remains in force, including controlled mail and conservative governance. V011.1 index correction is separate; this work changes no table/subset/migration.

## Exact input SHA-256

Paths below are relative to reviewed root unless prefixed `/home/tanren/.m2`. Whole-file byte hashes identify the exact input; excerpts inspected do not imply runtime adoption. Original requirement/architecture/product sources are retained from prior source review and hash checked; current database/acceptance/new-table/POM/local API inputs were reread. No files created outside the two requested outputs.

|Input|SHA-256|
|---|---|
|`.superpowers/sdd/2026-10-21-ama-phase-b/task-B-07-contract-finalization-brief.md`|`3c42b7ceb45b1b45b8bb3f1b334edef41fa9ff234f146d4dcead1fa203d8dbd3`|
|`.superpowers/sdd/2026-10-21-ama-phase-b/task-B-07-generation-contract-proposal.md`|`80bde48809691a9883f76ff17affcc71c6003f749289f8d162d18a984ff148f9`|
|`.superpowers/sdd/2026-10-21-ama-phase-b/task-B-07-generation-contract-report.md`|`14ad5e6bea73247044d5e593d9b176ccb03ca76c48e5983d5df69cb41c39fae6`|
|`.superpowers/sdd/2026-10-21-ama-phase-b/task-B-07-brief.md`|`6936ab55fae017b2bcebe5aac37751bedc594834c80ef3b83208c2f56305a8a9`|
|`designDocs/智能会议助手一期需求说明文档.md`|`e5b11f49373418e7b4ea9b74071fed035f320612a4783de075980c13223cf783`|
|`designDocs/线上模型服务接口文档.md`|`be60b22cf6d05ddf534b83015a2993910cd1c395e480be908caadc71376bb8b2`|
|`docs/superpowers/specs/2026-10-21-ama-product-supplement.md`|`34a70d05e60b513faa8b7e98ba17e0d68008dcd05e260c210cbeb24bb181900c`|
|`docs/superpowers/specs/2026-10-21-ama-architecture-design.md`|`f913ea4002759173bb2c9b53fe096e27b75bb387308a103d68215e8c19a0e4a2`|
|`docs/superpowers/specs/2026-10-21-ama-database-design.md`|`c4ae00740c7d72e2945d0f67a926631bba198c7ecde2a46cd66523293b21e8d4`|
|`docs/database/ama-data-dictionary.md`|`7d34063a86573c6812924a27fff6dc5f2b98cc849dabafb16b9487074fac1d8b`|
|`docs/database/ama-schema-draft.sql`|`ebf1eae51b303d00b2ed35c4fbd5ba4c166813564032875a6ba0fb35ba743c93`|
|`docs/database/migration-register.md`|`af0e478b8b98b10faaa60ca765afbb617b9eae3629f059ea64b12224be2a5be2`|
|`docs/acceptance/b/database-generation-addendum-review.md`|`1b8199c9bda2efb6de33c162c59bd65630777fad9b178b1e536c877760977f54`|
|`docs/acceptance/b/database-generation-addendum-independent-review.md`|`242397c7d2d90689c44ccb814f0bde3d1e8a319fb593f57b843d7dc5788655a6`|
|`docs/acceptance/b/transcript-parser-prerequisite.md`|`3ef135d1892f38b3e5ebe99dfc3389d7d3bc6393c0bfa51ef413d6b8718b2655`|
|`docs/acceptance/b/task-execution-fix-independent-review.md`|`385632a9e4f6a86461c9c881f74daf3d534a957bceb50deee6a8486a78f5a9a4`|
|`backend/contracts/src/main/java/ama/contract/TaskContracts.java`|`6641098514d21f4cba9b2ab82b7a6eaea9894f162a563447c69859e9dbdb9880`|
|`backend/core/src/main/java/ama/task/ExternalOperationPort.java`|`6230e84a6294c5252d149db66d8ba1ef2eca50bb2e26eff5b4a25851eecdee08`|
|`backend/core/src/main/java/ama/task/ObservationRetryRequiredException.java`|`63214b6807acdb7981ff1a6555e6d35925d081a06a5a896f77376ff2f7a5725b`|
|`pom.xml`|`5b3cb723fab6d1763f7862662c0d5bc5e47f29baafd7633f24636a409a767aa3`|
|`backend/contracts/pom.xml`|`e4c80faa1124833874f99e8645d76652e8de41fb40ef85b7012d954b2e9b1717`|
|`backend/core/pom.xml`|`b3f8f975cdd5f8b5e7e0536dcdaff7008d94fff08a4008229c87d26182622981`|
|`backend/adapters/pom.xml`|`642846d573a5e78959be183b19eb3cd7f86155c6dc87011baec9dedde4cc668d`|
|`/home/tanren/.m2/repository/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom`|`1c929eeccb48f8ee12225e9fec725177170b89575754bd903f184c185db77775`|
|`/home/tanren/.m2/repository/com/fasterxml/jackson/core/jackson-core/2.21.5/jackson-core-2.21.5.jar`|`b64b5874162b503a0e58a8f7758266e8dd9f91bf49e3a59ae0b5f47589a231b7`|
|`/home/tanren/.m2/repository/com/fasterxml/jackson/core/jackson-databind/2.21.5/jackson-databind-2.21.5.pom`|`72eeec36add46cd7d185fed89265cdb97a7fc05cf3413ce2a59440efe1823a12`|

## Output verification

Proposal line count: 141 (limit 220). Proposal SHA-256: `eaae0ee74d2330cb4bcd32525e445e3809c238e139574477306c761742b96f05`. Original proposal/report preservation hashes matched. No tests executed; local metadata/API and artifact integrity checks only. Root full read plus fresh independent contract review must precede S1 implementation.
