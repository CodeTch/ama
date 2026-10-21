# B06/B07 immutable config wire contract — fresh independent review

2026-10-22. Reviewer /root/b06_config_contract_review; no proposal authorship or implementation ownership. Review-only workspace /home/tanren/ama/.worktrees/phase-a; observed HEAD 5eb00d0f00af51ef95b24e9af209395c64e1e85b.

## Verdict and scope

**SPEC: PASS for the proposed bounded immutable-config wire contract. TECHNICAL QUALITY: PASS for that design scope.**
Findings: Critical 0, Important 0, Minor 0. No concrete blocker found after comparing the complete 109-line proposal and complete 95-line report with the adopted B07 final+fix1, wrapper and independent fix review, relevant actual dictionary rows, R5/R6, TaskSpec/TaskQueue and the reviewed TXT parser contract/implementation.
This is not an implementation review, runtime acceptance or automatic adoption. Root adoption remains required before the single S1 author consumes the addendum. Existing pure source/API feasibility still requires implementation and independent meaningful tests; no unreviewed S1 Java was consumed.

## Independent technical assessment

|Area|Conclusion and concrete boundary checked|
|---|---|
|Raw schema before profile authority|PASS. An empty object cannot satisfy either exact manifest fields or archive envelope/known definition dispatch. ParsedArchive is derived data, not an authority token. Public untrusted claims/rows cannot construct successful private bindings. Expected artifact identity, definition schema, fixed contract/version, required slots and codec dependency must all validate before profile use. A caller supplying valid=true or a prebuilt profile map has no approved bypass.|
|Transport versus business hash|PASS. J specifies strict scalar UTF8, exact sorted keys, escapes, required nulls, ordered arrays and no whitespace/BOM/newline. Parsing consumes EOF and rejects decoded duplicate keys. Exact raw bytes must equal J; raw archive SHA is external and never stored inside the archive. ProcessingFingerprint uses the adopted typed binary domain envelope. M's raw JSON SHA is expressly not that fingerprint.|
|Exact archive graph|PASS. Identity is (type,version), with actual bytes/header/raw SHA correlated to each requested Ref. Wrong type with the same version, conflicting SHA, duplicate identity even with equal bytes, duplicate slot, missing/extra slot/node, cycle and unknown definition all fail. Shared dependencies are represented once. Whole-operation roots derive from actually reached processing inputs, generation pairs and selected review rows, not an arbitrary caller root list.|
|Generation roots|PASS. Both exact PROMPT_CONFIG/MODEL_CONFIG versions recover a common 15-profile dependency set; raw root SHA enters the recomputed saved generation fingerprint. A mutable current index cannot legitimately redefine an identity. Pure consistency cannot authenticate the index or protect against wholesale replacement of all purported stored facts; the proposal correctly leaves authoritative acquisition/publication to S2/ops.|
|Processing row correlation|PASS. Manifest schema/identity/IMPORT/sourceRevision/null model-prompt/material EMPTY/config versions/base revision/epoch must match transcription_input. Complete batch ordinal/file/SHA rows and file meeting/full SHA/size must agree. Task subject/input/fence/digest and revision owning input/batch/meeting/digest are checked, with producer_task_id resolving the actual producer spec when present. No batch fingerprint substitutes for the full file list.|
|Source content versus processing content|PASS. Source SHA covers complete actual file bytes; parser physical-line identity retains its existing four length-prefixed UTF8 fields and UUID-v8 bits. Confirmation independently hashes actual saved content/source/marks. Manual edits do not become claims that the original parser output was unchanged.|
|Fence semantics|PASS. meetingRevision and subjectBusinessVersion remain distinct frozen fields. Stored TaskSpecs for one generation input must all agree; missing historical fence fails. The synthetic 7/9/2 vector is not evidence that any live root transition is eligible. Current root/epoch substitution is expressly disallowed.|
|Private immutable result|PASS. Binding constructors are private to verification; successful values retain defensive copies and exact verified digest/refs. Raw arrays are bounded and copied before SHA/parse/J checks, all on the same isolated bytes. An archive-reader result or resolver callback cannot mint the final binding. Implementation must preserve these rules for nested lists/refs as well as byte arrays.|
|Whole-walk resource protection|PASS. External bootstrap limits cover manifest+closure+recursive historical inputs cumulatively, including fact edges/rows, tokens, dependencies and aggregate scalars. Per-document bounds remain additional. Checked arithmetic, pre-allocation/depth checks, cancellation/interruption and no partial success apply throughout. Pinned semantic budgets cannot be replaced by bootstrap defaults or clipping; stricter operational protection can only fail.|
|Finite profile dispatch|PASS. The table preserves all 15 existing kinds; the sole extra TRANSCRIPTION_INPUT label is a pure domain tag. Existing six hash domains/content projections retain their adopted semantics. VALIDATION_BUDGET retains exact adopted field sets. Unsupported model parameters require another schema rather than hidden defaults. Renderer identity and safe model syntax bind configuration without certifying execution quality/protocol.|
|Narrow catalog|PASS. ama.transcript-catalog.v1 provides confirmed transcript segments only, with EMPTY materials and no nontranscript source. Broader supplied-fact projection examples do not constitute successful full raw-profile material generation. No PDF/PPT/parser-selection mapping is invented. ASR/ENHANCE/chained media/PINNED processing remain future versioned grammar obligations.|
|Failures and ownership|PASS. Known schema/identity/profile failures, malformed input, digest mismatch, unsupported capability, cancellation and budget errors cannot return a partial handle or digest. Error text remains code-only. No DB/file/network/model/Spring implementation or runtime activation is smuggled into the pure seam; S1 ownership is singular.|

## Create before persistence versus historical verification

I examined the apparent circularity risk in proposal §§3/6 rather than assuming an unspecified valid flag. Section 3 explicitly requires computing a new input before TaskSpec construction, with no dummy digest; section 6 names separate ProcessingFingerprint.compute/verify responsibilities. The addendum preserves the adopted B07 compute(facts,Fence,...) path, while stored verification compares actual input_fingerprint and historical task fences. The interface table is a responsibility sketch, not a requirement that a fresh draft already possess an actual stored row/task/revision.
The coherent required implementation is therefore a shared raw/schema/archive/fact validation stage with distinct creation and reload entry points: fresh computation validates raw configuration and draft fields plus real prerequisite batch/file/history facts and returns its newly computed digest; reload additionally compares the actual stored input digest and supplied task/revision links. A new generation input supplies explicit Fence; recursive historical inputs still require their actual saved digests and historical TaskSpecs. Creation neither accepts nor requires an own stored fingerprint or a not-yet-existing own TaskSpec; complete verified manifest/archive/prerequisite parent facts determine the digest first. No new own output revision is a prerequisite to hashing its draft. Once persisted rows/tasks exist, stored verification strictly compares their actual fingerprint and chain.
This follows the explicit create-before-task rule and retained B07 compute contract; it does not authorize nullable wildcards, empty-string expected digests or valid=true escape hatches on stored verification, caller-minted profile handles, or skipping historical checks. A later implementation that requires dummy own hashes, accepts arbitrary typed profiles, or treats stored recovery as optional would violate this reviewed design. I found no necessary self-reference in the specified bytes or graph and do not report a hypothetical API implementation mistake as a current design defect.

## Preserved B07 recovery and provenance

Confirmation recovery must traverse the actual confirmation revision to processing_input_id, parse the pinned raw manifest, match its row and archive graph, and recompute the unchanged confirmation projection. The generation root's confirmation and codec refs must equal that old chain including SHA. Full mark order remains KEY, CONCLUSION, DISPUTE, GENERATION_FOCUS, including explicit empty sets; actual saved body/source/speaker/time fields remain in the hash.
Final recovery stays tied to the saved body's generation input and verified saved generation fingerprint, then its exact FINAL_SOURCES/codec. Nullable legacy input fails rather than selecting current settings. Each selected review independently resolves its stored hash_profile and exact source context. The addendum does not replace fix1's unique locatorRows plus complete per-occurrence sourceUsages, exact evidence membership, full target content or exact-scope review coverage.
In particular, body/detection/note/ACTION suggestion paths still require their actual input links; intrinsic ordinary TRANSCRIPT evidence does not invent an original generation input. A contradictory present ACTION suggestion cannot fall back to a more attractive context. Ordinary copied local-material evidence and ambiguous/missing parser selection remain unsupported. One unsupported occurrence fails the whole requested graph, and selected review membership cannot remove inconvenient evidence. The new archive grammar supplies profile proof, not missing provenance edges.
Manual/mark/restore revisions preserve the concrete original processing or generation input. S2 still owns authoritative completeness and the actual edit/restore relation; a pure function does not fabricate a missing restored-from edge or prove storage authenticity.

## Independent literal-vector verification

Read the five one-line literals directly from the saved full report. A tiny independent Python read-only script used strict UTF8, duplicate-rejecting JSON decode, an independently written J formatter, hashlib SHA256 and explicit dependency/ref assertions. It invoked no application code, tests, build, database, service or network. All five lengths and hashes match; exact canonical form also matches. The script verified all four archive dependency refs, manifest config/profile refs, step config/parser equality, and SHA256 of the one source byte 0x41.

|Literal|Exact UTF8 bytes|Independently verified SHA256|J equality|
|---|---:|---|---|
|C codec|223|2b553c99cecc917b12a466cd62859df2ac18533e518476aa1b843279f2eb6e19|PASS|
|P parser|226|7b9f57377ad401fe040287e133f3efa81d9956ea581a5e497ee6c0835051962a|PASS|
|H confirmation|419|2f94e75e8d53ef37a152a1ccd63b54a61a6853e07508a5c927a4ba8e8209c0aa|PASS|
|I synthetic processing config|987|e4e58cadff0045688e0cab8200018af7c4407a39a1ca97924f14b68c6d51765b|PASS|
|M synthetic manifest|1763|7c2ec8c7fdc8e42df32ae0043748b64c4f1989007553a7fd69f2b183c1acbe0b|PASS|

These checks establish the literal raw bytes only. They are not an executed production reader/graph/codec test, a processing-domain golden vector, real source-file parsing, or end-to-end generation/final recovery. Independent complete domain-byte/digest vectors and complete synthetic generation-root fixtures remain required S1 acceptance work, exactly as the proposal states. The separately deferred input_batch hash algorithm remains B06 work before runtime writes.

## Exact reviewed inputs and read limits

SDD is .superpowers/sdd/2026-10-21-ama-phase-b; all paths below are relative to the phase-a tree.

|Input|SHA256 observed in this review|
|---|---|
|SDD/task-B-06-config-contract-proposal.md (full 109 lines)|427451ba18bf1a72ce77da7eea87c72a6e4dcf885cb77617ab660b68a94e9832|
|SDD/task-B-06-config-contract-report.md (full 95 lines, all five literal vectors)|8ad71c053887afc9c6155a96e20af67636bde047a14b05aa92d212292d7ad586|
|SDD/task-B-06-config-contract-review-brief.md (read first)|38c7f0268cc0aba5a6f25e67c7dc997956d737b63612bfc67388106918dd8b09|
|docs/superpowers/specs/2026-10-22-ama-minutes-contract.md|d43386ffd2050bb80cb8924794bd27d9132a4117cfc279cb29253610083a4e24|
|artifacts/phase-b/b07-contract-design/task-B-07-contract-final-proposal.md|eaae0ee74d2330cb4bcd32525e445e3809c238e139574477306c761742b96f05|
|artifacts/phase-b/b07-contract-design/task-B-07-contract-fix1-proposal.md|e9a5a7cccb49e20640fee790fbcf866257e74befaa997bf89260f384dba08c00|
|artifacts/phase-b/b07-contract-design/task-B-07-contract-fix1-review.md|02618879c991061d1c03c907e8abd5ad0ff80db7ca79509b55a1ee68f2f00657|
|docs/database/ama-data-dictionary.md (relevant row/column sections)|7d34063a86573c6812924a27fff6dc5f2b98cc849dabafb16b9487074fac1d8b|
|docs/superpowers/specs/2026-10-21-ama-database-design.md (R5/R6 and related context)|c4ae00740c7d72e2945d0f67a926631bba198c7ecde2a46cd66523293b21e8d4|
|backend/contracts/src/main/java/ama/contract/TaskContracts.java|6641098514d21f4cba9b2ab82b7a6eaea9894f162a563447c69859e9dbdb9880|

Also read the full requested design brief, TaskQueue, TranscriptDocumentParser and TextTranscriptParser, and B05 fix1 consumer boundary report. An oversized combined B07 read was truncated; relevant missing spans were explicitly reread in smaller slices before forming conclusions. Source hashes identify whole file bytes but do not claim a whole unrelated database/plan review. No SQL/schema evidence was requalified, no whole unrelated plan or unreviewed S1 code was consumed.

## Limits and handoff

This report is the sole new write. Read-only Git HEAD inspection and file analytics were used; no Git mutation, source/POM/SQL edit, application test/build, DB/Docker/service action, company/network operation or child agent occurred.
The PASS permits root to adopt a technical design for isolated pure types/readers/hash verification. Actual archive publication/durability, DB authoritative row completeness, ACL, CAS/fencing, task persistence/retry, parser invocation, model/renderer protocol/quality/capacity/cancellation, D01 material provenance and B06/B07 runtime remain unqualified. It neither changes B00-1.3's 93-table/709-column design nor activates either pending UX choice. The version length bound is an existing technical storage limit, not a new user content quota; synthetic budgets/model/prompt fixtures are not production defaults.
