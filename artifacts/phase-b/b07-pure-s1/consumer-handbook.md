# B07 pure S1 consumer handbook

Implementation candidate for fresh independent review, not runtime activation. BASE: 8da440d39e1b8e8dcdfc34722908d29b5aa3aa8a. B07 adopted final proposal plus fix1 govern hashes/source occurrences; root config-wire adoption 7f061e8a9d55f5b0db92762692edbe61123d400b governs raw processing/archive JSON. No production quotas/defaults are selected. First-save and human-review UX remain pending.

## Authority and acquisition

Rows, ContentFile facts, tasks and raw bytes are caller-supplied. S1 verifies consistency of the supplied closed set and known wire schemas. It cannot prove that a DTO/list came from the authoritative immutable index, that a query returned every row, that bytes are durable or that access was authorized.

B06/S2 must provide a real archive source backed by an immutable (artifactType, exact version) index, enforce authorized complete reads, unique immutable publication/durability and full historical acquisition. A list or successful ValidatedOperation is not storage provenance. Hashes verify consistency with referenced bytes, not their origin. No filesystem/DB/network archive source is delivered.

Apply acquisition limits before constructing values. StoredProvenanceFacts requires maxFacts before copying its seven lists. CatalogFacts requires maxEntries; DocumentBounds requires maxUnits. GraphBudget and ArchiveReadBudget provide additional whole-operation bounds. RawArchive is an untrusted transport carrier and intentionally does not copy arrays; ConfigBindings checks each array length and cumulative remaining bytes before its first owned defensive copy. Do not mutate arrays concurrently during capture.

## Entry points

Contracts and core production packages use only JDK and internal types. StrictConfigArchiveReader, StrictProcessingManifestReader and StrictMinutesJsonReader use dedicated Jackson 2 streaming factories in adapters. No ObjectMapper, runtime beans or service wiring are delivered.

Construct ConfigBindings with StrictConfigArchiveReader and StrictProcessingManifestReader. Configure TranscriptConfirmationHash, GenerationFingerprint and ResultHashProfiles with (ConfigBindings, List<RawArchive>, ArchiveReadBudget). A no-argument business hash helper deliberately fails PROFILE_UNAVAILABLE; projection methods remain available.

ConfigBindings success-binding constructors are private. ArchivedProfileResolver is package-private with no caller success callback. Context.generation/artifact return provisional interpretations; a typed DTO is not a binding. Business compute/verify methods finish row/task/graph/digest/exact raw closure validation before success.

## Processing draft compute

ConfigBindings.computeProcessing(byte[] rawManifest, StoredProvenanceFacts prerequisites, List<RawArchive> archives, ArchiveReadBudget budget, BooleanSupplier cancelled) returns ValidatedProcessingBinding. ProcessingFingerprint provides the same configured compute/verify service.

Required shape:
1. Exact canonical raw manifest with explicit own input/meeting IDs and complete base fence. There is no own stored input fingerprint, TaskSpec or output revision yet.
2. Real input_batch plus every input_batch_file, complete saved ordinal/file ID/full SHA.
3. One ContentFile(id, sha256, sizeBytes, meetingId) per batch file. The three-argument projection-only overload is insufficient here.
4. Exactly the transitively reachable original raw processing/parser/codec/confirmation archives. Claimed identity must match the raw header, full raw SHA must match every reference, and dependency slots/order must match.

ProcessingFingerprint is the typed binary envelope of profile ama.transcription-input.v1, kind TRANSCRIPTION_INPUT, data M. It is not the raw JSON manifest SHA.

## Processing stored verify

ConfigBindings.verifyProcessing(FullRow storedInput, StoredProvenanceFacts facts, archives, budget, cancelled) additionally requires the exact TRANSCRIPTION_INPUT row present in facts, original raw config_manifest and actual stored fingerprint. Every immutable TaskSpec for that input must agree on meeting, subject businessVersion, cancelEpoch and fingerprint; at least one real matching task is required.

For every supplied owning transcript_revision, require matching meeting/batch/input fingerprint. A nonnull producer_task_id needs StoredTask(taskId, actual TaskSpec); its meeting/input/fingerprint/fence must agree. A flat TaskSpec suffices only where no producer identity needs resolution. No nullable wildcard, dummy hash or caller boolean repairs missing history.

Supported processing: IMPORT, one IMPORT_TXT step, source INPUT_BATCH, strict UTF-8 lines parser, null sourceRevisionId/parentInput/priorOutput/model/prompt, EMPTY materials. ASR/ENHANCE/PINNED materials/multiple steps fail CAPABILITY_UNAVAILABLE. Unknown schemas/kinds do not fall back. Future parent/prior-output history needs a separately adopted interpreter.

## Generation draft compute

ConfigBindings.computeGeneration(GenerationDraft draft, StoredProvenanceFacts prerequisites, Fence fence, List<RawArchive> archives, GraphBudget graphBudget, ArchiveReadBudget readBudget, BooleanSupplier cancelled) returns ValidatedGenerationBinding.

GenerationDraft positional fields: inputId, meetingId, transcriptConfirmationId, materialSnapshotId, templateRevisionId, exclusionRevisionId, elementSetId, promptVersion, modelVersion, Scope, targetElementId. Fence fields: meetingRevision, subjectBusinessVersion, cancelEpoch. Draft has no own generation_input row, fingerprint, TaskSpec or output revision.

Required closed prerequisite set:
* Exact confirmation, owning transcript_revision, all saved transcript_segment and transcript_segment_mark rows, owning processing input and original raw manifest, input batch/members/content metadata, actual historical processing task and producer facts.
* Exact template_revision and exclusion_revision, every sensitive_exclusion row and referenced source_locator.
* For PINNED elementSetId: element_set, all minutes_element rows, action_item/action_suggestion, sensitive_detection/join, background_note/join, element evidence_link/locator rows and EvidenceAnchor values. Empty tables remain explicit in rowset projection.
* Complete transitive facts for all actual reachable old origins: historical generation_input rows, GenerationConfiguration interpretations, confirmations/processing chains, all immutable TaskSpecs and required producer identities. Derive detached interpretations through bounded Context.generation, then let business validation verify them.
* Original prompt/model roots containing the same exact fifteen profile refs and their original transitive raw closure, including the original processing profiles. Extra raw nodes fail like missing nodes.

Supported generation: FULL scope, null target, confirmed transcript catalog, EMPTY materials, EMPTY or PINNED old element set, saved default/selected template, EMPTY or PINNED exclusions. Nonnull materialSnapshotId fails CAPABILITY_UNAVAILABLE. No production parser-to-locator material capability exists in this version.

## Generation stored verify

ConfigBindings.verifyGeneration(GenerationFacts(inputId, facts), TaskSpec task, archives, graphBudget, readBudget, cancelled) additionally needs the actual GENERATION_INPUT row, stored fingerprint, one corresponding GenerationConfiguration interpretation and all real immutable tasks referencing that input.

The row provides base_revision; historical TaskSpec/StoredTask facts supply businessVersion/cancelEpoch/fingerprint. All matching facts must agree, including meeting/subject. The passed task must identify that exact meeting and input and match every historical fence. Current subject/root state never fills missing history.

GenerationFingerprint.compute(draft, prerequisites, fence, graphBudget, cancelled) and verify(GenerationFacts, TaskSpec, Profiles, graphBudget, cancelled) enforce the same raw chain. Projection exposes inspection data plus a completed operation; a caller-created Projection alone cannot mint ValidatedGenerationBinding.

## Source graph matrix

StoredSourceGraph.derive(facts, GraphSelection(meetingId, minutesRevisionId, elementSetId, exclusionRevisionId, appliedInputId), graphBudget, cancelled) derives represented occurrences. verify(suppliedGraph, ...) requires exact usage-key bijection and unique physical locator rows. This validates row consistency only; business hashes also verify original raw profile/fingerprint chains.

| Saved use | Derived origin |
|---|---|
| BODY evidence | minutes_revision.generation_input_id |
| Detection/background membership | the directory row's own input_id |
| ACTION evidence with action_suggestion | suggestion.input_id; contradictory present suggestion never falls back |
| Ordinary copied element | intrinsic unique transcript confirmation only |
| Applied exclusion | final body's exact input; selected exclusion revision must equal that input's revision |

Local copied material with attractive historical candidates fails PROVENANCE_UNAVAILABLE. Per-evidence caller assertions cannot replace the actual chain. One physical locator used by two notes with different inputs/parser versions retains two complete occurrences. Both sensitivity categories stay distinct. Whole graph traversal checks active cycles, nodes, depth, cumulative facts and cancellation.

## Review and final shapes

ReviewFacts(meetingId, ReviewKind, elementId, minutesRevisionId, Anchor, selectedEvidenceIds, facts) selects exactly one target. SOURCE_SUPPORT requires exact scalar anchor and all intersecting evidence. ACTION_FIELDS requires ACTION and every target evidence. Supply target set/minutes rows, accepted action_item where applicable, evidence anchors and complete source history.

ResultHashProfiles.target needs that review's original REVIEW_HASH archive and codec closure. sources additionally needs the original processing/generation source closures. targetData/sourcesData are inspection projections.

FinalFacts(confirmationId, facts) requires exact final_confirmation, minutes_revision with generation input, exact transcript/material/template/exclusion snapshot match, complete selected element set/directories/evidence, exclusion rows, frozen final_result_review, each selected result_review and every result_review_evidence, ReviewScopeFact(reviewId, Anchor) for selected SOURCE_SUPPORT reviews, and all reachable original input/tasks/config archives. Reviews recompute using their own saved hash_profile and raw archive. Later unselected reviews cannot change the final digest.

finalSources validates the original generation/fingerprint chain even without evidence. finalData is projection only. ReviewCoverageEvaluator evaluates supplied saved reviews: latest contrary exact scope wins, partial spans never aggregate to whole credit, changed target/hash invalidates credit. It creates no review or human intent.

directoryRows and exclusionRows are direct arrays of {name,rows}, sorted table name with explicit empty rows. directoryRows has exactly action_suggestion, background_note, background_note_source, sensitive_detection, sensitive_detection_source. exclusionRows has exactly exclusion_revision and sensitive_exclusion. Rows use complete PK order. evidenceRows/reviewRows/reviewEvidenceRows/finalReviewRows are direct FullRow arrays. ROWSET itself retains {tables:[...]}.

## Model output and support

StrictMinutesJsonReader.read(InputStream, ValidationContext, ValidationBudget, cancelled) validates exact schema and task/input fingerprint. Its ValidatedGenerationBinding overload pins the fingerprint/output budget. Runtime must derive ValidationContext from saved immutable task/input/catalog and supply a stream deadline. S1 cannot abort arbitrary blocking I/O.

The reader never closes caller input. It uses strict UTF-8 REPORT, no BOM/autodetect, at most maxWireBytes+1, escaped duplicate/unknown-field and trailing-root rejection, strict raw numeric/date/scalar grammar and cumulative token/string/container/membership limits. Anchors are Unicode scalar offsets and SHA of whole exact target text. No trimming/normalization/repair/retry.

Catalog aliases are s1, s2, ...: saved confirmation ordinal first, other synthetic supported locators by canonical locator bytes. Bounds include actual physical paragraph gaps. Pure page/audio/knowledge tests validate type projection only, not production parser activation.

CreationSupport gives valid refs PARTIAL, no evidence UNSUPPORTED and background-only meeting CONCLUSION/ACTION UNSUPPORTED. It never infers semantic SUPPORTED. Raw ACTION ownerMention/dueOn remain unverified; accepted enterprise owner/display/date all remain null.

## Profiles, bounds and errors

Known domains: ama.canonical-value.v1, ama.transcript-confirmation.v1, ama.transcription-input.v1, ama.generation-input.v2, ama.rowset.v1, ama.result-review.v2, ama.final-sources.v2. Versions are exact archived values, never latest. Unknown future schemas/profiles fail PROFILE_UNAVAILABLE.

Canonical codec tags are 00..06 with 8-byte big-endian lengths/counts/unsigned integers, strict UTF-8, unsigned UTF-8 object-key order and distinct null/empty/list order. Only typed CanonicalValue is accepted. Duplicate entries fail before map conversion. Complete preflight precedes success; SHA streams without whole encoded-output allocation. encode(byte[]) also enforces technical Integer.MAX_VALUE capacity.

CanonicalBudget: maxEncodedBytes/maxDepth/maxNodes/maxStringBytes/maxContainerEntries. ValidationBudget: maxWireBytes/maxDepth/maxTokens/maxNameUtf16/maxStringUtf16/maxStringUtf8Bytes/maxStringScalars/maxAggregateScalars/maxElements/maxDetections/maxNotes/maxRefs/maxEvidence/maxMemberships. GraphBudget adds maxOriginDepth/maxOriginNodes/maxAggregateFacts. ArchiveReadBudget positional fields: maxArtifactBytes/maxTotalBytes/maxArtifacts/maxDependencies/maxGraphDepth/maxTokens/maxJsonDepth/maxNameUtf16/maxStringUtf16/maxStringUtf8Bytes/maxAggregateScalars/maxManifestBytes/maxBatchFiles/maxFactRows/maxFactEdges. It bounds the entire operation's artifacts/raw bytes/decoded JSON/graph depth/edges/rows; referenced histories do not reset counters. All numeric fixture limits are synthetic, not production defaults.

Failure is code-only: message equals code name; no cause, suppressed exception, payload/key/path excerpt or writable trace. Common codes: INVALID_JSON, INVALID_UTF8, INVALID_SCHEMA, UNKNOWN_FIELD, INPUT_INVALID, FINGERPRINT_MISMATCH, SOURCE_MEMBERSHIP_MISMATCH, SOURCE_NOT_IN_INPUT, SOURCE_CONTEXT_AMBIGUOUS, PROVENANCE_UNAVAILABLE, PROFILE_UNAVAILABLE, CAPABILITY_UNAVAILABLE, TARGET_INVALID, BUDGET_EXCEEDED, CANCELLED, READ_FAILED. Interrupted I/O restores the interrupt flag; final cancellation checks precede success. Do not log underlying Jackson messages/payloads.

## D01 and runtime obligations

D01 must adopt persisted material origins, parser -> locator-kind mapping and archive capabilities. PAGE/PARAGRAPH parsed_unit kinds cannot distinguish PDF/PPT/DOCX/TXT/HTML. Scanning existing source_locator registrations cannot establish the complete generation catalog. The material occurrence vector remains projection-only. New schema/profile versions require separate review/additive support; old profiles retain exact interpretation. Copied local materials, ACTION transcript outside its existing suggestion input and future processing histories fail closed today.

Runtime still owns authoritative complete/authorized reads, immutable archive index and durability, bounded acquisition/deadlines, transactions/fences, ACL, DB writes, model calls/retry/chunking/workers, identities, review/adoption and UX. No SQL/service/bean/model/network/DB/Worker/API activation is delivered.

## FullRow schema reference

Positional Cell values follow exactly this order; ? is nullable. UINT is nonnegative long. UUID/hash/date/timestamp use strict saved grammar. Generic caller-defined tables/columns are rejected. This schema does not prove query completeness/storage authenticity.

| Table | Columns in order | Full PK | Enum restrictions |
|---|---|---|---|
| input_batch | id:UUID meeting_id:UUID kind:TEXT input_fingerprint:HASH base_revision:UINT created_at:TIMESTAMP | id | kind=TRANSCRIPT,MEDIA |
| input_batch_file | batch_id:UUID ordinal:UINT file_id:UUID sha256:HASH | batch_id,ordinal | — |
| transcript_revision | id:UUID meeting_id:UUID revision:UINT kind:TEXT batch_id:UUID parent_id:UUID? input_fingerprint:HASH created_by:TEXT created_at:TIMESTAMP processing_input_id:UUID producer_task_id:UUID? | id | kind=RAW,ENHANCED,WORKING,CANDIDATE |
| transcript_segment | revision_id:UUID segment_id:UUID ordinal:UINT body:TEXT source_file_id:UUID source_paragraph:UINT? start_ms:UINT? end_ms:UINT? speaker_id:UUID? | revision_id,segment_id | — |
| transcript_confirmation | id:UUID meeting_id:UUID revision_id:UUID content_hash:HASH confirmed_by:TEXT confirmed_at:TIMESTAMP | id | — |
| transcript_segment_mark | revision_id:UUID segment_id:UUID mark:TEXT | revision_id,segment_id,mark | mark=KEY,CONCLUSION,DISPUTE,GENERATION_FOCUS |
| transcription_input | id:UUID meeting_id:UUID batch_id:UUID kind:TEXT source_revision_id:UUID? material_snapshot_id:UUID? material_mode:TEXT processing_config_version:TEXT parser_version:TEXT? model_config_version:TEXT? prompt_version:TEXT? manifest_schema_version:UINT config_manifest:TEXT input_fingerprint:HASH base_revision:UINT cancel_epoch:UINT created_at:TIMESTAMP | id | kind=IMPORT,ASR,ENHANCE;material_mode=EMPTY,PINNED |
| template_revision | id:UUID template_id:UUID revision:UINT owner_id:TEXT? name:TEXT themes:TEXT theme_count:UINT created_at:TIMESTAMP | id | — |
| generation_input | id:UUID meeting_id:UUID transcript_confirmation_id:UUID material_snapshot_id:UUID? template_revision_id:UUID exclusion_revision_id:UUID element_set_id:UUID? prompt_version:TEXT model_config_version:TEXT base_revision:UINT input_fingerprint:HASH scope:TEXT target_element_id:UUID? | id | scope=FULL,ELEMENT |
| minutes_revision | id:UUID meeting_id:UUID revision:UINT parent_id:UUID? title:TEXT title_origin:TEXT body:TEXT generation_input_id:UUID? created_by:TEXT created_at:TIMESTAMP | id | title_origin=HUMAN,LINKED,AI |
| element_set | id:UUID meeting_id:UUID revision:UINT created_at:TIMESTAMP | id | — |
| minutes_element | id:UUID set_id:UUID category:TEXT ordinal:UINT body:TEXT support_state:TEXT | id | category=OVERVIEW,DISCUSSION,CONCLUSION,ACTION,RISK,OPEN_QUESTION,DISAGREEMENT;support_state=SUPPORTED,PARTIAL,UNSUPPORTED,NOT_EXTRACTED |
| action_item | element_id:UUID owner_enterprise_id:TEXT? owner_display:TEXT? due_on:DATE? | element_id | — |
| exclusion_revision | id:UUID meeting_id:UUID revision:UINT created_at:TIMESTAMP | id | — |
| sensitive_exclusion | revision_id:UUID locator_id:UUID category:TEXT reason:TEXT? | revision_id,locator_id,category | category=INTERNAL_BUSINESS_DATA,POLITICAL_SENSITIVE |
| source_locator | id:UUID meeting_id:UUID kind:TEXT transcript_revision_id:UUID? segment_id:UUID? file_id:UUID? material_revision_id:UUID? page_no:UINT? paragraph_no:UINT? section_path:TEXT? start_ms:UINT? end_ms:UINT? | id | kind=TRANSCRIPT,AUDIO,PDF_PAGE,PPT_PAGE,WORD_PARAGRAPH,TEXT_PARAGRAPH,KNOWLEDGE_VERSION |
| evidence_link | id:UUID locator_id:UUID element_id:UUID? minutes_revision_id:UUID? support_state:TEXT target_anchor:TEXT | id | support_state=SUPPORTED,PARTIAL,UNSUPPORTED |
| final_confirmation | id:UUID meeting_id:UUID meeting_revision:UINT info_revision_id:UUID roster_revision_id:UUID transcript_confirmation_id:UUID minutes_revision_id:UUID material_snapshot_id:UUID? element_set_id:UUID template_revision_id:UUID exclusion_revision_id:UUID source_manifest_hash:HASH input_fingerprint:HASH confirmed_by:TEXT confirmed_at:TIMESTAMP | id | — |
| material_reference | id:UUID meeting_id:UUID kind:TEXT provider_id:TEXT? external_id:TEXT? file_id:UUID? | id | kind=LOCAL,KNOWLEDGE |
| material_revision | id:UUID reference_id:UUID revision:UINT external_version:TEXT? content_file_id:UUID? content_hash:HASH created_at:TIMESTAMP | id | — |
| material_snapshot | id:UUID meeting_id:UUID revision:UINT input_fingerprint:HASH applied_at:TIMESTAMP | id | — |
| material_snapshot_item | snapshot_id:UUID material_revision_id:UUID ordinal:UINT | snapshot_id,material_revision_id | — |
| parsed_document | id:UUID material_revision_id:UUID parser_version:TEXT text_file_id:UUID? unit_count:UINT | id | — |
| parsed_unit | document_id:UUID ordinal:UINT kind:TEXT page_no:UINT? paragraph_no:UINT? section_path:TEXT? body:TEXT | document_id,ordinal | kind=PAGE,PARAGRAPH,SECTION |
| sensitive_detection | id:UUID set_id:UUID input_id:UUID category:TEXT ordinal:UINT | id | category=INTERNAL_BUSINESS_DATA,POLITICAL_SENSITIVE |
| sensitive_detection_source | detection_id:UUID locator_id:UUID | detection_id,locator_id | — |
| background_note | id:UUID set_id:UUID input_id:UUID ordinal:UINT body:TEXT | id | — |
| background_note_source | note_id:UUID locator_id:UUID | note_id,locator_id | — |
| action_suggestion | element_id:UUID input_id:UUID owner_mention:TEXT? due_on:DATE? | element_id | — |
| result_review | id:UUID meeting_id:UUID review_no:UINT review_kind:TEXT element_id:UUID? minutes_revision_id:UUID? target_anchor:TEXT? target_hash:HASH source_set_hash:HASH hash_profile:TEXT outcome:TEXT reviewed_by:TEXT reviewed_at:TIMESTAMP | id | review_kind=SOURCE_SUPPORT,ACTION_FIELDS |
| result_review_evidence | review_id:UUID evidence_id:UUID | review_id,evidence_id | — |
| final_result_review | confirmation_id:UUID review_id:UUID | confirmation_id,review_id | — |
