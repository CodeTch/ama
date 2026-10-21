# B07 pure S1 implementation report

Date: 2026-10-22. Author owns this isolated implementation; fresh independent SPEC and quality review is still required. This is not adoption, runtime activation or completion of all B07 business/UX requirements.

## Revision and scope

BASE: 8da440d39e1b8e8dcdfc34722908d29b5aa3aa8a
Verified source HEAD: 83f1db9a650ae087d3120fb28171b7a9cf1f5882
Tree: /home/tanren/ama/.worktrees/phase-b-minutes-pure
Branch: codex/phase-b-minutes-pure

Source/evidence commit contains 258 changed paths, 32 new production Java files, 13 owned test suites, complete independent vectors and raw verification archive. The documentation-only report commit follows it; delivery HEAD is recorded in the root SDD handoff and parent message. All source/tests are committed after actual verification. Full BASE..source-HEAD paths, plus this report, are listed below, not just HEAD~1.

Adopted normative inputs:
* B07 final proposal eaae0ee74d2330cb4bcd32525e445e3809c238e139574477306c761742b96f05.
* B07 fix1 delta e9a5a7cccb49e20640fee790fbcf866257e74befaa997bf89260f384dba08c00 takes precedence; root adoption 81ac17c, independent fix review 02618879.
* Config-wire adoption root 7f061e8a9d55f5b0db92762692edbe61123d400b; full proposal 427451ba18bf1a72ce77da7eea87c72a6e4dcf885cb77617ab660b68a94e9832 and independent review 39f99b7a20eaf336990edf3b27e4c98bad7c7c68bd54a97a474da62d06a83658 (PASS/PASS).
* Parent explicitly confirmed direct table-array expansion for directoryRows and exclusionRows and all historical TaskSpec fence agreement. No unreviewed peer Java/SQL/D01 draft was imported. Target source BASE stayed fixed.

## Delivered

Typed immutable contracts and finite FullRow schema projections, canonical binary codec with complete preflight and streaming SHA, strict Jackson 2 UTF-8 model/config/manifest readers, deterministic catalog/aliases/bounds, scalar anchors and safe failures, creation support, actual stored-origin graph derivation, processing/generation fingerprint compute/verify, confirmation/review/final hashes, saved-review coverage and private validated bindings.

Raw profile closure validates canonical JSON bytes, claimed header identity, full SHA, exact slot/ref/schema interpretation and exact reachable DAG. Missing/extra/duplicate/inconsistent archives fail. Raw arrays are length/remaining-budget checked before the first owned copy. Budgets accumulate across all referenced history, with cancellation and interrupt preservation.

Generation draft accepts explicit Fence and actual prerequisite rows without its own fingerprint/task/output; stored verify adds the exact immutable row/fingerprint and every actual task/fence. Producer identities are resolved through StoredTask rather than arbitrary asserted origins. Successful business hashes finish raw chain validation before return. Package-private resolver bridge cannot be supplied by callers; validated-binding constructors are private.

Consumer handbook: artifacts/phase-b/b07-pure-s1/consumer-handbook.md. It records exact APIs, draft/stored row-task shapes, all 32 FullRow descriptors, original profile requirements, source occurrence matrix, budgets/errors and the real archive-source obligation for B06/S2.

Only POM changes: versionless managed jackson-core in adapters (actual managed 2.21.5), versionless test-scope junit-jupiter in contracts/core. Existing databind/AWS remain unchanged. Contracts/core production imports are JDK/internal only. No parent POM/test-category/order changes; no TextTranscriptParser/stable UUID change.

## Executed verification

Final exact owned Maven selector command is in verification-commands.txt and raw/78-complete-owned-suite.log. It ran all 13 owned suites: 97 unique tests, failures=0, errors=0, skipped=0. Fresh XML was captured before the canonical runner removed stale report files.

Canonical command: python3 tools/checks/run.py unit RawHashChainTest. raw/79-canonical-runner.log reports PASS executed=13, failures=0; all eight reactor modules compiled. These 13 are a subset of the same 97 tests and are not double-counted. No DB/IT/legacy/S3/native tests were selected.

| Suite | Unique tests |
|---|---:|
| ama.config.json.ArchiveBudgetTest | 15 |
| ama.config.json.ConfigBindingsTest | 6 |
| ama.config.json.ConfigWireReaderTest | 6 |
| ama.config.json.RawHashChainTest | 13 |
| ama.minutes.json.OutputBudgetBoundaryTest | 16 |
| ama.minutes.json.StrictMinutesJsonReaderTest | 7 |
| ama.contract.MinutesFactBudgetTest | 2 |
| ama.canonical.CanonicalValueCodecTest | 5 |
| ama.minutes.contract.FinalAndCoverageTest | 3 |
| ama.minutes.contract.GenerationFingerprintTest | 4 |
| ama.minutes.contract.HashProfilesTest | 6 |
| ama.minutes.contract.SourceAndSupportTest | 7 |
| ama.minutes.contract.StoredSourceGraphTest | 7 |

Raw evidence is preserved without stripping earlier failures. raw-run-index.json records actual status; a historical filename containing green is not a claim that its run passed.

* 48 distinct test methods have meaningful observed failing assertions in the selected RED rounds, listed in meaningful-tdd-methods.txt. Initial stub REDs and later boundary/security regressions precede fixes.
* Across every raw round, 51 distinct methods show observed assertion failures; the difference includes later fixture mistakes. Do not interpret all failing attempts as product defects.
* Nine raw rounds contain POM/compile setup errors: 01, 06, 11, 31, 32, 37, 38, 45, 66. They are preserved and excluded from meaningful assertion-RED claims.
* Round 68 used an incorrect BODY anchor and expected a weaker missing-suggestion error; fixtures corrected before round 70. Rounds 71/72 used ineffective fixture mutation strings; corrected against actual literal processing config before round 73. Round 75's independent manual fixture contained literal backslash+n while Java saved LF; corrected to explicit scalar LF before round 77. No production change was needed for those fixture corrections.
* The read-only source audit initially matched an ObjectMapper comment; corrected to actual code/import checks. raw/80 explains that tooling false positive; raw/81 and source-audit.json record the successful scope audit.
* Final git diff --cached --check passed. SHA256SUMS verified all 257 explicitly allowed payloads before source commit; payload-allowlist.txt is explicit. SHA256SUMS excludes itself. .gitattributes preserves exact raw/JUnit/vector bytes with -text and scopes raw log/JUnit whitespace suppression only to this new archive. Existing reports/raw archives were unchanged.

## Independent bytes and behavioral coverage

independent_vectors.py uses only Python standard-library literal schema data, struct big-endian encoding, strict UTF-8 and hashlib. It reads only adopted literal raw manifest bytes for the processing fixture, never production Java/codecs or Java-emitted expected hashes. Each domain emits complete hex bytes plus SHA, not a prefix. Domain fixtures and the separate synthetic raw archive closure are committed.

Complete domains include confirmation empty/four marks, generation FULL/EMPTY, review target/sources v2, final v2, full processing fingerprint (not manifest SHA), raw-supported generation EMPTY/PINNED/excluded snapshots, complete final occurrence matrix, selected-review final, saved manual child confirmation and projection-only dual-parser material occurrences.

Assertions cover:
* Literal primitive binary tags/lengths, U+E000 vs U+10000 key order, controls/separators, list ordering/domain isolation, duplicate object entries, canonical limits/cancellation, each mark add/remove and duplicate/four-enum order.
* Actual Jackson streaming schema, seven categories/directories, two sensitivity labels, exact field/EOF/escaped-duplicate grammar, malformed UTF-8 suffix/BOM/UTF16/lone escaped surrogates, strict numeric/date/ID/range errors, no-close/sentinel/failed stream/interrupt/redaction.
* Exact model limit and one-over checks for wire/depth/tokens/name/string UTF16/UTF8/scalars/aggregate; all six element/detection/note/ref/evidence/membership containers; exact cumulative config budgets and every lower-by-one bound.
* Actual BODY/ACTION suggestion/detection/background/intrinsic transcript stored chains, exact one-locator six-use matrix and dual-parser projection; bijection/orphan/ambiguous/cross-version/cycle/node/depth/cancellation failures. Ordinary copied local material stays unsupported.
* PINNED set graph resolves the actual historical input's raw archives/fingerprint and every agreeing immutable fence. Contradictory saved suggestion cannot fall back; conflicting historical task cannot be ignored.
* New exclusion revision preserves old immutable input snapshot, independently hashes both labels, derives APPLIED_INPUT only from exact body input/revision, and stored generation verify recomputes it.
* Full review target content, intersecting-evidence subset rejection, partial span credit, latest contrary exact scope, invalidated hashes, frozen selected final review memberships, raw review profile resolution and original-chain reload.
* Raw known/future schema/capability boundaries, archive ref mismatch/missing/extra/duplicate, injected parsed DTO rejection, actual file SHA/size/meeting and real task fences. Saved manual revision retains original processing profile while hashing exact new body/marks. Unrelated current detached config and row/raw archive query order cannot change the historical final digest.

Fixture numeric budgets are explicitly synthetic and test-only. No company credential, endpoint, production profile installation or product quota is introduced.

## Limits and downstream obligations

Raw bytes and detached rows establish internal consistency only. They do not establish authoritative immutable index membership, durability/authentication, complete DB reads or ACL. B06/S2 needs a real immutable (artifactType,version) archive source and bounded complete historical fact acquisition. A DTO/list is not provenance.

Supported raw processing remains IMPORT/TXT/EMPTY, one IMPORT_TXT step without parents/prior outputs. ASR/ENHANCE/PINNED materials/additional/future history fail explicitly. Generation remains FULL/confirmed transcript/EMPTY materials, EMPTY or PINNED old set. Nonnull material snapshot fails CAPABILITY_UNAVAILABLE. Future profiles/schemas are rejected, never reinterpreted as current known versions.

Synthetic material graph/type tests do not claim complete raw material generation support. D01 must adopt durable material origins plus parser-to-locator-kind mapping/archive capabilities: PAGE/PARAGRAPH alone cannot distinguish PDF/PPT/DOCX/TXT/HTML; scanning existing locators cannot create a complete catalog. Copied ordinary local materials and ACTION sources outside a present saved suggestion's input remain unsupported. No D01 draft, root V013 schema or peer code was imported.

No DB/Docker/service/HTTP/network/model/Worker/API/OpenAPI/generated type/runtime bean/permission/adoption/UX tests or changes are claimed. No first-save or human-review default activation. Runtime still owns complete authoritative storage acquisition, deadlines, transaction/fence enforcement, identities, ACL, model calls and human adoption.

Fresh independent full-owned SPEC and quality review is required before root adoption. The author does not self-approve.

## All owned paths: full BASE..source HEAD plus report

* .gitattributes
* artifacts/phase-b/b07-pure-s1/SHA256SUMS
* artifacts/phase-b/b07-pure-s1/complete-owned-counts.json
* artifacts/phase-b/b07-pure-s1/consumer-handbook.md
* artifacts/phase-b/b07-pure-s1/independent_vectors.py
* artifacts/phase-b/b07-pure-s1/junit/canonical-runner/TEST-ama.config.json.RawHashChainTest.xml
* artifacts/phase-b/b07-pure-s1/junit/complete-owned/TEST-ama.canonical.CanonicalValueCodecTest.xml
* artifacts/phase-b/b07-pure-s1/junit/complete-owned/TEST-ama.config.json.ArchiveBudgetTest.xml
* artifacts/phase-b/b07-pure-s1/junit/complete-owned/TEST-ama.config.json.ConfigBindingsTest.xml
* artifacts/phase-b/b07-pure-s1/junit/complete-owned/TEST-ama.config.json.ConfigWireReaderTest.xml
* artifacts/phase-b/b07-pure-s1/junit/complete-owned/TEST-ama.config.json.RawHashChainTest.xml
* artifacts/phase-b/b07-pure-s1/junit/complete-owned/TEST-ama.contract.MinutesFactBudgetTest.xml
* artifacts/phase-b/b07-pure-s1/junit/complete-owned/TEST-ama.minutes.contract.FinalAndCoverageTest.xml
* artifacts/phase-b/b07-pure-s1/junit/complete-owned/TEST-ama.minutes.contract.GenerationFingerprintTest.xml
* artifacts/phase-b/b07-pure-s1/junit/complete-owned/TEST-ama.minutes.contract.HashProfilesTest.xml
* artifacts/phase-b/b07-pure-s1/junit/complete-owned/TEST-ama.minutes.contract.SourceAndSupportTest.xml
* artifacts/phase-b/b07-pure-s1/junit/complete-owned/TEST-ama.minutes.contract.StoredSourceGraphTest.xml
* artifacts/phase-b/b07-pure-s1/junit/complete-owned/TEST-ama.minutes.json.OutputBudgetBoundaryTest.xml
* artifacts/phase-b/b07-pure-s1/junit/complete-owned/TEST-ama.minutes.json.StrictMinutesJsonReaderTest.xml
* artifacts/phase-b/b07-pure-s1/meaningful-tdd-methods.txt
* artifacts/phase-b/b07-pure-s1/observed-assertion-failures.txt
* artifacts/phase-b/b07-pure-s1/payload-allowlist.txt
* artifacts/phase-b/b07-pure-s1/raw-run-index.json
* artifacts/phase-b/b07-pure-s1/raw/01-codec-red.log
* artifacts/phase-b/b07-pure-s1/raw/02-codec-red.log
* artifacts/phase-b/b07-pure-s1/raw/03-codec-green.log
* artifacts/phase-b/b07-pure-s1/raw/04-catalog-red.log
* artifacts/phase-b/b07-pure-s1/raw/05-catalog-assertion-red.log
* artifacts/phase-b/b07-pure-s1/raw/06-catalog-green.log
* artifacts/phase-b/b07-pure-s1/raw/07-catalog-green.log
* artifacts/phase-b/b07-pure-s1/raw/08-catalog-green.log
* artifacts/phase-b/b07-pure-s1/raw/09-json-red.log
* artifacts/phase-b/b07-pure-s1/raw/10-json-assertion-red.log
* artifacts/phase-b/b07-pure-s1/raw/11-json-green.log
* artifacts/phase-b/b07-pure-s1/raw/12-json-green.log
* artifacts/phase-b/b07-pure-s1/raw/13-graph-red.log
* artifacts/phase-b/b07-pure-s1/raw/14-graph-red.log
* artifacts/phase-b/b07-pure-s1/raw/15-graph-green.log
* artifacts/phase-b/b07-pure-s1/raw/16-graph-green.log
* artifacts/phase-b/b07-pure-s1/raw/17-independent-vectors.log
* artifacts/phase-b/b07-pure-s1/raw/18-profiles-red.log
* artifacts/phase-b/b07-pure-s1/raw/19-independent-vectors-fixed.log
* artifacts/phase-b/b07-pure-s1/raw/20-profiles-red.log
* artifacts/phase-b/b07-pure-s1/raw/21-profiles-green.log
* artifacts/phase-b/b07-pure-s1/raw/22-independent-generation.log
* artifacts/phase-b/b07-pure-s1/raw/23-generation-red.log
* artifacts/phase-b/b07-pure-s1/raw/24-generation-green.log
* artifacts/phase-b/b07-pure-s1/raw/25-independent-final.log
* artifacts/phase-b/b07-pure-s1/raw/26-final-red.log
* artifacts/phase-b/b07-pure-s1/raw/27-final-green.log
* artifacts/phase-b/b07-pure-s1/raw/28-material-profile-red.log
* artifacts/phase-b/b07-pure-s1/raw/29-material-profile-green.log
* artifacts/phase-b/b07-pure-s1/raw/30-config-reader-red.log
* artifacts/phase-b/b07-pure-s1/raw/31-config-reader-green.log
* artifacts/phase-b/b07-pure-s1/raw/32-config-reader-green.log
* artifacts/phase-b/b07-pure-s1/raw/33-config-reader-green.log
* artifacts/phase-b/b07-pure-s1/raw/34-independent-processing.log
* artifacts/phase-b/b07-pure-s1/raw/35-config-binding-red.log
* artifacts/phase-b/b07-pure-s1/raw/36-config-binding-green.log
* artifacts/phase-b/b07-pure-s1/raw/37-raw-integration-green.log
* artifacts/phase-b/b07-pure-s1/raw/38-raw-integration-green.log
* artifacts/phase-b/b07-pure-s1/raw/39-raw-integration-green.log
* artifacts/phase-b/b07-pure-s1/raw/40-independent-full-chain.log
* artifacts/phase-b/b07-pure-s1/raw/41-raw-generation-draft-red.log
* artifacts/phase-b/b07-pure-s1/raw/42-raw-generation-draft-green.log
* artifacts/phase-b/b07-pure-s1/raw/43-independent-reload-final.log
* artifacts/phase-b/b07-pure-s1/raw/44-raw-full-chain.log
* artifacts/phase-b/b07-pure-s1/raw/45-private-bindings-green.log
* artifacts/phase-b/b07-pure-s1/raw/46-private-bindings-green.log
* artifacts/phase-b/b07-pure-s1/raw/47-depth-audio-red.log
* artifacts/phase-b/b07-pure-s1/raw/48-audio-green-depth-red.log
* artifacts/phase-b/b07-pure-s1/raw/49-depth-budget-green.log
* artifacts/phase-b/b07-pure-s1/raw/50-shared-depth-green.log
* artifacts/phase-b/b07-pure-s1/raw/51-independent-occurrences.log
* artifacts/phase-b/b07-pure-s1/raw/52-orphan-membership-red.log
* artifacts/phase-b/b07-pure-s1/raw/53-orphan-membership-green.log
* artifacts/phase-b/b07-pure-s1/raw/54-pinned-rowset-budget-red.log
* artifacts/phase-b/b07-pure-s1/raw/55-pinned-rowset-budget-green.log
* artifacts/phase-b/b07-pure-s1/raw/56-task-identity-red-output-boundaries.log
* artifacts/phase-b/b07-pure-s1/raw/57-task-identity-green.log
* artifacts/phase-b/b07-pure-s1/raw/58-independent-control-json.log
* artifacts/phase-b/b07-pure-s1/raw/59-config-canonical-error-red.log
* artifacts/phase-b/b07-pure-s1/raw/60-config-canonical-error-green.log
* artifacts/phase-b/b07-pure-s1/raw/61-fact-copy-budget-red.log
* artifacts/phase-b/b07-pure-s1/raw/62-fact-copy-budget-green.log
* artifacts/phase-b/b07-pure-s1/raw/63-catalog-copy-budget-red.log
* artifacts/phase-b/b07-pure-s1/raw/64-nested-fact-caps-green.log
* artifacts/phase-b/b07-pure-s1/raw/65-independent-expanded-vectors.log
* artifacts/phase-b/b07-pure-s1/raw/66-actual-matrix-first-run.log
* artifacts/phase-b/b07-pure-s1/raw/67-expanded-vectors-corrected.log
* artifacts/phase-b/b07-pure-s1/raw/68-actual-matrix-test-run.log
* artifacts/phase-b/b07-pure-s1/raw/69-expanded-vectors-final.log
* artifacts/phase-b/b07-pure-s1/raw/70-actual-matrix-green.log
* artifacts/phase-b/b07-pure-s1/raw/71-final-limit-regressions-first-run.log
* artifacts/phase-b/b07-pure-s1/raw/72-final-limit-regressions-green.log
* artifacts/phase-b/b07-pure-s1/raw/73-final-limit-regressions-green.log
* artifacts/phase-b/b07-pure-s1/raw/74-manual-vector.log
* artifacts/phase-b/b07-pure-s1/raw/75-final-reload-future-checks.log
* artifacts/phase-b/b07-pure-s1/raw/76-manual-vector-exact-saved-newline.log
* artifacts/phase-b/b07-pure-s1/raw/77-final-reload-future-green.log
* artifacts/phase-b/b07-pure-s1/raw/78-complete-owned-suite.log
* artifacts/phase-b/b07-pure-s1/raw/79-canonical-runner.log
* artifacts/phase-b/b07-pure-s1/raw/80-source-audit-tool-error.log
* artifacts/phase-b/b07-pure-s1/raw/81-source-audit-green.log
* artifacts/phase-b/b07-pure-s1/source-audit.json
* artifacts/phase-b/b07-pure-s1/source-paths.txt
* artifacts/phase-b/b07-pure-s1/verification-commands.txt
* backend/adapters/pom.xml
* backend/adapters/src/main/java/ama/config/json/StrictConfigArchiveReader.java
* backend/adapters/src/main/java/ama/config/json/StrictConfigJson.java
* backend/adapters/src/main/java/ama/config/json/StrictProcessingManifestReader.java
* backend/adapters/src/main/java/ama/minutes/json/StrictMinutesJsonReader.java
* backend/adapters/src/test/java/ama/config/json/ArchiveBudgetTest.java
* backend/adapters/src/test/java/ama/config/json/ConfigBindingsTest.java
* backend/adapters/src/test/java/ama/config/json/ConfigWireReaderTest.java
* backend/adapters/src/test/java/ama/config/json/RawHashChainTest.java
* backend/adapters/src/test/java/ama/minutes/json/OutputBudgetBoundaryTest.java
* backend/adapters/src/test/java/ama/minutes/json/StrictMinutesJsonReaderTest.java
* backend/adapters/src/test/resources/ama/config/codec.json
* backend/adapters/src/test/resources/ama/config/confirmation.json
* backend/adapters/src/test/resources/ama/config/control-prompt.json
* backend/adapters/src/test/resources/ama/config/control-prompt.sha256
* backend/adapters/src/test/resources/ama/config/full-confirmation.hex
* backend/adapters/src/test/resources/ama/config/full-confirmation.sha256
* backend/adapters/src/test/resources/ama/config/full-final-matrix.hex
* backend/adapters/src/test/resources/ama/config/full-final-matrix.sha256
* backend/adapters/src/test/resources/ama/config/full-final-review.hex
* backend/adapters/src/test/resources/ama/config/full-final-review.sha256
* backend/adapters/src/test/resources/ama/config/full-final.hex
* backend/adapters/src/test/resources/ama/config/full-final.sha256
* backend/adapters/src/test/resources/ama/config/full-generation-excluded.hex
* backend/adapters/src/test/resources/ama/config/full-generation-excluded.sha256
* backend/adapters/src/test/resources/ama/config/full-generation-pinned.hex
* backend/adapters/src/test/resources/ama/config/full-generation-pinned.sha256
* backend/adapters/src/test/resources/ama/config/full-generation.hex
* backend/adapters/src/test/resources/ama/config/full-generation.sha256
* backend/adapters/src/test/resources/ama/config/full-manual-confirmation.hex
* backend/adapters/src/test/resources/ama/config/full-manual-confirmation.sha256
* backend/adapters/src/test/resources/ama/config/full-review-sources.hex
* backend/adapters/src/test/resources/ama/config/full-review-sources.sha256
* backend/adapters/src/test/resources/ama/config/full-review-target.hex
* backend/adapters/src/test/resources/ama/config/full-review-target.sha256
* backend/adapters/src/test/resources/ama/config/full/00.json
* backend/adapters/src/test/resources/ama/config/full/01.json
* backend/adapters/src/test/resources/ama/config/full/02.json
* backend/adapters/src/test/resources/ama/config/full/03.json
* backend/adapters/src/test/resources/ama/config/full/04.json
* backend/adapters/src/test/resources/ama/config/full/05.json
* backend/adapters/src/test/resources/ama/config/full/06.json
* backend/adapters/src/test/resources/ama/config/full/07.json
* backend/adapters/src/test/resources/ama/config/full/08.json
* backend/adapters/src/test/resources/ama/config/full/09.json
* backend/adapters/src/test/resources/ama/config/full/10.json
* backend/adapters/src/test/resources/ama/config/full/11.json
* backend/adapters/src/test/resources/ama/config/full/12.json
* backend/adapters/src/test/resources/ama/config/full/13.json
* backend/adapters/src/test/resources/ama/config/full/14.json
* backend/adapters/src/test/resources/ama/config/full/15.json
* backend/adapters/src/test/resources/ama/config/full/16.json
* backend/adapters/src/test/resources/ama/config/full/17.json
* backend/adapters/src/test/resources/ama/config/full/18.json
* backend/adapters/src/test/resources/ama/config/full/index.txt
* backend/adapters/src/test/resources/ama/config/manifest.json
* backend/adapters/src/test/resources/ama/config/parser.json
* backend/adapters/src/test/resources/ama/config/processing-domain.hex
* backend/adapters/src/test/resources/ama/config/processing-domain.sha256
* backend/adapters/src/test/resources/ama/config/processing.json
* backend/contracts/pom.xml
* backend/contracts/src/main/java/ama/contract/ConfigArchiveContracts.java
* backend/contracts/src/main/java/ama/contract/MinutesContracts.java
* backend/contracts/src/test/java/ama/contract/MinutesFactBudgetTest.java
* backend/core/pom.xml
* backend/core/src/main/java/ama/canonical/CanonicalValue.java
* backend/core/src/main/java/ama/canonical/CanonicalValueCodec.java
* backend/core/src/main/java/ama/config/ArchiveReadState.java
* backend/core/src/main/java/ama/config/CanonicalJson.java
* backend/core/src/main/java/ama/config/ConfigArchiveReader.java
* backend/core/src/main/java/ama/config/ConfigBindings.java
* backend/core/src/main/java/ama/config/ConfigSchema.java
* backend/core/src/main/java/ama/config/ProcessingManifestReader.java
* backend/core/src/main/java/ama/config/WireValues.java
* backend/core/src/main/java/ama/minutes/contract/ArchivedProfileResolver.java
* backend/core/src/main/java/ama/minutes/contract/ContractValues.java
* backend/core/src/main/java/ama/minutes/contract/CreationSupport.java
* backend/core/src/main/java/ama/minutes/contract/DomainHash.java
* backend/core/src/main/java/ama/minutes/contract/GenerationFingerprint.java
* backend/core/src/main/java/ama/minutes/contract/ModelOutputReader.java
* backend/core/src/main/java/ama/minutes/contract/OriginChainVerifier.java
* backend/core/src/main/java/ama/minutes/contract/OutputValidation.java
* backend/core/src/main/java/ama/minutes/contract/ResultHashProfiles.java
* backend/core/src/main/java/ama/minutes/contract/ReviewCoverageEvaluator.java
* backend/core/src/main/java/ama/minutes/contract/RowValues.java
* backend/core/src/main/java/ama/minutes/contract/RowsetProjection.java
* backend/core/src/main/java/ama/minutes/contract/SourceCatalog.java
* backend/core/src/main/java/ama/minutes/contract/StoredFactsIndex.java
* backend/core/src/main/java/ama/minutes/contract/StoredSourceGraph.java
* backend/core/src/main/java/ama/minutes/contract/TranscriptConfirmationHash.java
* backend/core/src/main/java/ama/transcript/ProcessingFingerprint.java
* backend/core/src/test/java/ama/canonical/CanonicalValueCodecTest.java
* backend/core/src/test/java/ama/minutes/contract/FinalAndCoverageTest.java
* backend/core/src/test/java/ama/minutes/contract/GenerationFingerprintTest.java
* backend/core/src/test/java/ama/minutes/contract/HashProfilesTest.java
* backend/core/src/test/java/ama/minutes/contract/SourceAndSupportTest.java
* backend/core/src/test/java/ama/minutes/contract/StoredSourceGraphTest.java
* backend/core/src/test/resources/ama/minutes/vectors/confirmation-marks.hex
* backend/core/src/test/resources/ama/minutes/vectors/confirmation-marks.json
* backend/core/src/test/resources/ama/minutes/vectors/confirmation-marks.sha256
* backend/core/src/test/resources/ama/minutes/vectors/confirmation.hex
* backend/core/src/test/resources/ama/minutes/vectors/confirmation.json
* backend/core/src/test/resources/ama/minutes/vectors/confirmation.sha256
* backend/core/src/test/resources/ama/minutes/vectors/final.hex
* backend/core/src/test/resources/ama/minutes/vectors/final.json
* backend/core/src/test/resources/ama/minutes/vectors/final.sha256
* backend/core/src/test/resources/ama/minutes/vectors/full-confirmation.hex
* backend/core/src/test/resources/ama/minutes/vectors/full-confirmation.json
* backend/core/src/test/resources/ama/minutes/vectors/full-confirmation.sha256
* backend/core/src/test/resources/ama/minutes/vectors/full-final-matrix.hex
* backend/core/src/test/resources/ama/minutes/vectors/full-final-matrix.json
* backend/core/src/test/resources/ama/minutes/vectors/full-final-matrix.sha256
* backend/core/src/test/resources/ama/minutes/vectors/full-final-review.hex
* backend/core/src/test/resources/ama/minutes/vectors/full-final-review.json
* backend/core/src/test/resources/ama/minutes/vectors/full-final-review.sha256
* backend/core/src/test/resources/ama/minutes/vectors/full-final.hex
* backend/core/src/test/resources/ama/minutes/vectors/full-final.json
* backend/core/src/test/resources/ama/minutes/vectors/full-final.sha256
* backend/core/src/test/resources/ama/minutes/vectors/full-generation-excluded.hex
* backend/core/src/test/resources/ama/minutes/vectors/full-generation-excluded.json
* backend/core/src/test/resources/ama/minutes/vectors/full-generation-excluded.sha256
* backend/core/src/test/resources/ama/minutes/vectors/full-generation-pinned.hex
* backend/core/src/test/resources/ama/minutes/vectors/full-generation-pinned.json
* backend/core/src/test/resources/ama/minutes/vectors/full-generation-pinned.sha256
* backend/core/src/test/resources/ama/minutes/vectors/full-generation.hex
* backend/core/src/test/resources/ama/minutes/vectors/full-generation.json
* backend/core/src/test/resources/ama/minutes/vectors/full-generation.sha256
* backend/core/src/test/resources/ama/minutes/vectors/full-manual-confirmation.hex
* backend/core/src/test/resources/ama/minutes/vectors/full-manual-confirmation.json
* backend/core/src/test/resources/ama/minutes/vectors/full-manual-confirmation.sha256
* backend/core/src/test/resources/ama/minutes/vectors/full-review-sources.hex
* backend/core/src/test/resources/ama/minutes/vectors/full-review-sources.json
* backend/core/src/test/resources/ama/minutes/vectors/full-review-sources.sha256
* backend/core/src/test/resources/ama/minutes/vectors/full-review-target.hex
* backend/core/src/test/resources/ama/minutes/vectors/full-review-target.json
* backend/core/src/test/resources/ama/minutes/vectors/full-review-target.sha256
* backend/core/src/test/resources/ama/minutes/vectors/generation.hex
* backend/core/src/test/resources/ama/minutes/vectors/generation.json
* backend/core/src/test/resources/ama/minutes/vectors/generation.sha256
* backend/core/src/test/resources/ama/minutes/vectors/occurrence-graph.hex
* backend/core/src/test/resources/ama/minutes/vectors/occurrence-graph.json
* backend/core/src/test/resources/ama/minutes/vectors/occurrence-graph.sha256
* backend/core/src/test/resources/ama/minutes/vectors/processing.hex
* backend/core/src/test/resources/ama/minutes/vectors/processing.json
* backend/core/src/test/resources/ama/minutes/vectors/processing.sha256
* backend/core/src/test/resources/ama/minutes/vectors/review-sources.hex
* backend/core/src/test/resources/ama/minutes/vectors/review-sources.json
* backend/core/src/test/resources/ama/minutes/vectors/review-sources.sha256
* backend/core/src/test/resources/ama/minutes/vectors/review-target.hex
* backend/core/src/test/resources/ama/minutes/vectors/review-target.json
* backend/core/src/test/resources/ama/minutes/vectors/review-target.sha256
* docs/acceptance/b/task-B-07-pure-s1-report.md
