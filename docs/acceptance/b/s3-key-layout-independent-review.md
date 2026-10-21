# Independent narrow review: S3 key-layout v3

Date: 2026-10-22 Asia/Shanghai. Reviewer: fresh independent design reviewer.

SPEC verdict: **PASS for the stated ordinary-generated-ASCII application envelope and bounded generic representation.**

Design-quality verdict: **PASS; no Critical, Important or Minor design defect found in the reviewed proposal.** This is approval of the design at the exact reviewed bytes, not implementation approval, provider qualification or production readiness. Root authorization and the fresh implementation/code-quality/real-HTTP/restart gates remain required.

## Reviewed scope and immutable references

Read the complete `s3-key-layout-addendum.md`, SHA-256 `065fd8b7f4852c8d395352d83083a67ad800523bd9ca5fcf1c1fb5db8f8c702b`. Read original `s3-adapter-design-preflight.md` only for layout/list/routing context (especially lines 53, 57-76 and 201-207); independently checked its SHA-256 remains `3bb475ce826a739ae6c1f01246084e16fdec76dcf9038a387a79bcb7351904f5`.

Applied requesting-code-review's independent assessment and verification-before-completion's evidence-before-claims principles. This reviewer is already the dispatched independent seat; no further agent was dispatched. This is an uncommitted design proposal, so no Git review package was required or created.

No source edits, service/provider/network requests, containers, SQL/DB or Git operations occurred. The only file written by this review is this report. The pure-memory model below was passed to Python on stdin and created no repository test file.

## Findings and reasoning

Critical: none. Important: none. Minor: none.

- **F is uniquely reversible, ordered and prefix preserving** (addendum lines 36-49). On the complete UTF-16 unit domain the codeword set is injective and prefix-free. The two dot selectors place period/slash between unchanged `-` and `0`; raw values through `}` precede the fixed-width lowercase-hex high-unit escape. Comparing the first different unit therefore gives Java UTF-16 order, including surrogate pairs and isolated high/low units. A logical prefix ending between the two units of a surrogate pair remains exact. Validation of forbidden full StorageKeys remains a separate existing-contract step; mathematical checks on all units do not claim all such strings are legal keys.
- **P remains reversible and preserves ordering and both directions of prefix equivalence** (lines 53-69, 114, 120-124). All insertions are at common fixed encoded positions; no value-dependent escape adjustment is allowed. At a first payload difference, the corresponding physical characters compare directly. If one payload ends first, its physical string ends before any extra `/x`, including encoded length exactly 199. Conversely stripping the deterministic markers cannot create a logical-prefix match absent from F. Empty prefix maps only to the scope base. A key at encoded 199 and its extension must coexist and enumerate correctly in the actual provider; that is explicitly an unresolved qualification gate, not a filesystem assumption.
- **Exact budgets are correct** (lines 73-93). With k=ceil(L/199), the 73-byte base plus L+k+(k-1) yields 72+L+2k. L=942 gives 1024 bytes, five chunks and eight total segments; L=943 gives 1025 and must reject before mutations. Each accepted payload segment is 2..200 bytes and starts with x; no user slash, empty/dot/dotdot component or unbounded logical depth survives into the physical layout. Ordinary 512 one-byte generated units give 590 bytes/six segments. The 511-character alternating slash example gives L766, 846 bytes/seven segments.
- **The application envelope is stated honestly** (lines 83-91). The demonstrated grammar letters/digits/underscore/hyphen fits 512 without business segmentation. This is not a claim that every 512-character ASCII key fits: 512 periods encode to L1024 and require 1108 bytes/nine segments, so must reject. Some legal generic keys remain technically unrepresentable as allowed by the prior design. Backend/namespace generic Unicode and length remain in exact control metadata, rather than acquiring application DB ASCII64 limits. Actual server generator grammar has NOT been independently source-verified in this narrow review; line 89 correctly makes that an implementation evidence obligation. Neither VARCHAR(512) nor the model establishes that grammar.
- **Hash/control identity and lifecycle boundaries are adequate at design level** (lines 25-32, 116, 128). Versioned, domain-separated, length-prefixed exact code-unit inputs avoid tuple-boundary/UTF-8-replacement aliases; full immutable scope/key equality makes a forced digest collision fail closed. The stable physical namespace/layout identity must be provisioned and recovered consistently, with immutable routing; credentials/runtime revisions must not enter H or cause equality drift. Genuine physical reassignment requires new identity/backend routing retaining old routing. The proposal expressly forbids v2 object/token/cursor reinterpretation. No claim is made here that implementation enforces these requirements.
- **Enumeration is bounded and scope derived** (lines 120-128; original lines 201-207). One independently constructed mapped Prefix, no Delimiter, one provider page, MaxKeys<=limit, exact decoded prefix/scope/key bindings, monotonic validation and layout/config-bound cursor retain the common semantics. A missing scope is empty only on confirmed absence; permission/malformed/mismatch errors cannot broaden the query. Short/empty advancing pages remain permissible. No flat object hash, namespace scan or sorting fallback is introduced.

## Executed evidence: pure-memory independent model

Python 3 process exited 0. Seed: 20261022. Model directly implemented the proposal's code-unit F, fixed-character P, canonical concatenation/decode and physical-shape rejection; it did not import or exercise adapter code.

- All 65,536 UTF-16 single-unit encodings: exact roundtrip, uniqueness, sorted unit order, prefix-free codewords. The abstract domain deliberately includes forbidden StorageKey units, so these are encoding facts only.
- 20,092 multi-unit samples: exact F/P roundtrips and identical logical/physical sort; 109,375 positive/negative/empty/proper-prefix comparisons. Cases include punctuation, tilde, BMP values, paired/isolated surrogates, encoded 198/199/200 and 397/398/399 neighborhoods.
- 72 split-escape cases: every internal offset of `.0`, `.1` and selected five-character escapes across encoded boundaries 199, 398, 597 and 796. Reassembly and prefix tests passed.
- Every L from 1 through 943: exact byte/depth formulas, segment<=200, no empty/dot/dotdot, and total<=1024 iff L<=942. Explicit ordinary512/slash511/period512 outputs were 590/846/1108 bytes respectively.
- 16 malformed marker/chunk/escape examples rejected, including empty/trailing/intermediate-short/overlong chunks, partial or noncanonical escapes. Separate length-prefixed code-unit examples distinguish isolated surrogate values and ambiguous-looking scope field concatenations.

The finite model checks support the proofs and provide fresh counterexample search. They are not a test of the real Java implementation, SDK serialization, provider key acceptance, cursor behavior, collision handling or runtime routing.

## Remaining gates; explicitly not verified

Actual template inventory and <=200/<=1024/<=8 validation for ALL control paths; full binding comparisons before reads/writes/lists; forced H/K collision handling; bounded exact metadata serialization preserving isolated units; stable provisioning across nodes; credential rotation and historical routing; source evidence for actual generated application keys; actual Java algorithm/property/canonical rejection tests remain implementation obligations.

Fresh isolated shallow backend/config identity, pinned RustFS and Java SDK, signed physical request evidence, real put/stat/HEAD/range/list/delete/native multipart, coexisting encoded-boundary prefix keys, bounded provider Prefix/MaxKeys and advancing cursors, acknowledged-object/control-binding restart recovery, large-part/common-contract/recovery checks and independent final code-quality review remain required. No provider capacity or total/segment/depth acceptance was inferred from this mathematical review.

The supplied original 255-byte200 / 256,512,1024-byte400 single-segment observations, raw deep-probe timing, two exit139 events and stack-overflow restart evidence remain preserved unresolved facts. This review did not inspect or re-run those fixtures, prove causal provider code, repair the old backend or authorize cleanup. No UI, terminal-decision, manifest, native-MPU or recovery redesign is requested by this verdict.
