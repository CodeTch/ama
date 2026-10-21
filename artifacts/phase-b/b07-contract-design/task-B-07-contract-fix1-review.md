# B07 contract fix1 — scoped independent re-review

2026-10-22. Same independent reviewer as the initial review; no proposal authorship or implementation ownership.

## Verdict

**SPEC: PASS for the corrected isolated pure-S1 contract. TECHNICAL QUALITY: PASS for that same bounded design scope.**
Initial I1: **ADDRESSED**. Initial I2: **ADDRESSED by explicit supported-domain restriction**, not universal provenance recovery.
Remaining/new findings: **Critical 0, Important 0, Minor 0**.

The reviewed contract is the complete old final proposal SHA `eaae0ee74d2330cb4bcd32525e445e3809c238e139574477306c761742b96f05` plus normative fix1 SHA `e9a5a7cccb49e20640fee790fbcf866257e74befaa997bf89260f384dba08c00`, with fix1 taking precedence. Neither the historical v1 projections nor the initial NEEDS REVISION verdict are silently rewritten. This scoped PASS closes the two design findings under the new explicit limits; root adoption is still required.

Read the entire 84-line fix1 proposal, entire 43-line fix1 report and required D01 provenance record. Revisited only affected old source/hash/profile sections and the relevant actual column definitions, including transcription_input. Verified the preserved inputs by SHA. No full initial-review repetition, source/POM/SQL edit, Git operation, build, test, database, service, network or child agent. This new report is the only write.

## Finding dispositions and impacted contracts

|Area|Independent conclusion|
|---|---|
|I1 physical locator versus usage identity|ADDRESSED. Fix1 §2 removes role/version/hash from unique locatorRows. EVIDENCE, DETECTION, BACKGROUND and EXCLUSION keys identify stored occurrences. Different note/detection inputs sharing L retain independent source versions and hashes.|
|I1 exact cardinality/order|The expected keys derive from complete evidence/directory/exclusion rows; exact bijection plus locator-ID union prevents missing/extra/duplicate occurrences. Key-internal locatorId must equal outer locatorId/FK. Rank then exact displayed tuple ordering is deterministic and independent of origin/hash/parser choice. Distinct keys remain distinct even if their values match.|
|I2 represented input paths|ADDRESSED for the expressly supported paths. Body, detection, background and ACTION suggestion use existing stored input links. No evidence.input_id or copied ordinary-element origin edge is invented. Supplied facts must contain the actual row/config chain, not an asserted evidence→input map.|
|I2 intrinsic transcript route|Exact revision/segment and unique same-meeting confirmation supply intrinsic transcript identity. The proposal explicitly stops claiming that this recovers the ordinary element's original generation input. Exact saved source/file/mark graph and owning confirmation profile are still required.|
|I2 unrepresented local histories|ADDRESSED by failure before digest. Ordinary non-ACTION local/parser origins and ACTION origins lacking the required chain cannot be repaired by candidate/text matching or current state. Ambiguous parser selection fails independently of iteration order. One unsupported occurrence fails the whole requested graph.|
|Generation v2 / PINNED set|The inherited set's SourceGraph now accompanies its physical-row contentHash. Thus prior parser contexts affect the new generation input digest instead of appearing only in later final/review hashes. Its membership is correctly limited to that set's evidence/directories; no body or exclusion occurrence is imported into the set graph. EMPTY remains explicit.|
|Review v2|The data shape contains full evidenceRows, unique locatorRows and all expected EVIDENCE usages. The old full overlapping-evidence rule remains; the caller cannot supply only favorable evidence. Target content semantics remain unchanged, while the incompatible profile identity changes consistently.|
|Final v2|The complete body/set evidence, directory joins and applied exclusion pairs generate occurrences exactly once. Review membership does not duplicate evidence usage. Frozen review/member rows remain included, each review hash recomputes under its own archived profile, and later reviews never alter the historical graph.|
|Applied exclusions|APPLIED_INPUT is an explicit context-of-application projection, not an invented original selection origin. The final body's input must pin the exact exclusion revision and contain the locator. Category stays in the occurrence key, preserving both labels. This meaning is compatible with the existing exclusion columns.|
|Profile identity/recovery|Generation/review/final move to v2; codec/ROWSET stay v1. No v1 digest is reinterpreted. Final profile recovery remains tied to the saved body's input/config chain. Confirmation profile recovery now uses the existing revision.processing_input_id and transcription_input processing_config_version/config_manifest/manifest_schema_version, with exact archived dependencies and no latest fallback.|

The I1 minimal graph now has a literal representable result: one locator L, BACKGROUND(N1,L) binding G1/D1/P1/hash1, and BACKGROUND(N2,L) binding G2/D2/P2/hash2. Both occurrence values are in the typed projection, so neither is lost to locator dedup. The expected future permutation and either-context-change vectors exercise the original defect directly.
The local document proof requires a uniquely selected material/parser graph within each input; it does not require P1 and P2 to be equal across inputs. Conversely, two eligible parser documents under one selection cannot be silently collapsed. This distinction closes I1 without reopening I2.

## Pure feasibility and dependency boundaries

The corrected surface remains implementable as detached immutable facts, pure graph validation and canonical hashing. No persistence resolver or newly stored graph blob is necessary for this scoped work. SourceGraph is a derived value, and proof of the completeness/authenticity of its supplied row universe remains an S2 responsibility.
There is no necessary digest self-reference: a new generation input's PINNED set refers to already saved members with prior input fingerprints or intrinsic confirmation identities. Its own newly computed digest is not an occurrence origin of that pre-existing input set. APPLIED_INPUT appears in later final projection, after that body input fingerprint exists; review/final hashes do not feed back into generation input. Fix1 expressly rejects cyclic origin dependencies and requires bounded traversal. Implementation must enforce those existing requirements across the entire dependency walk, with cumulative budget/cancellation checks, rather than assuming immutable-looking caller facts are automatically acyclic.
Immutable config dependencies identify profiles without storing a new hash_profile column in confirmation or final_confirmation. The confirmation manifest must actually provide the pinned dependency under its declared schema; the proposal does not authorize inventing it for an existing row. B06 alignment is still a prerequisite before wiring. A conflicting generation-side confirmation profile fails rather than redefining the already saved confirmation.
The existing strict JSON/Unicode/alias/anchor and conservative support rules are unchanged by the delta. This scoped review does not rerun or upgrade the initial design assessment into proof of implemented parser/codec behavior. The future independent domain vectors now need the exact v2 graph shapes.

## Material limits of this PASS

- It approves neither first automatic effective save nor per-item/combined review UX. Both remain pending without activated defaults.
- It does not qualify arbitrary manual edits or all TXT workflows merely because their locators are syntactically valid. For example, a retained ACTION suggestion from G1 does not authorize silently rebinding its input-linked evidence to G2; the strict matrix rejects a mismatch rather than switching to intrinsic provenance. A permitted future workflow requiring another binding needs the separately reviewed projection stated in fix1 §4. This restriction must not become a claim that the broader required feature has been delivered.
- Ordinary copied local-material provenance, general source rebindings, parser capability and durable origin design remain required future work. The separate D01 record explicitly includes generation/adoption/copy/partial regeneration/manual edits/restoration/reviews/finalization, persistent origin repair and independent review; it does not remove those requirements or grant indefinite deferral.
- No actual immutable archive, authoritative query completeness, ACL, source availability, model quality, concurrency/fencing, atomic persistence, runtime finalization, formal SQL or production capability was verified here. Supplied-fact tests and all runtime gates remain outstanding.
- No new B00 column, raw-JSON attachment, migration version, profile activation or schema application is approved by this report. Parallel formal V012 work is outside this review.

## Exact input SHA-256

SDD means `/home/tanren/ama/.worktrees/phase-a/.superpowers/sdd/2026-10-21-ama-phase-b`.

|Input|SHA-256|
|---|---|
|SDD/task-B-07-contract-fix1-proposal.md|e9a5a7cccb49e20640fee790fbcf866257e74befaa997bf89260f384dba08c00|
|SDD/task-B-07-contract-fix1-report.md|6774ea05ff2cfee4d53471790a7eeb9a8ffc68d3385c149fcc90bde0c074fac3|
|SDD/task-D-01-provenance-design-required.md|03da247eda21aa9b470c753b8b478584b44d377f851ccce23f979208dfe5897b|
|SDD/task-B-07-contract-review.md|6751a78f3747f06217955dfcf5e88875f04f3ad3b94965bdc5aa82e33b7961d7|
|SDD/task-B-07-contract-final-proposal.md|eaae0ee74d2330cb4bcd32525e445e3809c238e139574477306c761742b96f05|
|SDD/task-B-07-contract-final-report.md|b9fd896a5fce45d066b076ad2022cd9a358f53d961d5fa293dcad1a4c7c903ed|
|SDD/task-B-07-generation-contract-proposal.md|80bde48809691a9883f76ff17affcc71c6003f749289f8d162d18a984ff148f9|
|SDD/task-B-07-generation-contract-report.md|14ad5e6bea73247044d5e593d9b176ccb03ca76c48e5983d5df69cb41c39fae6|
|docs/superpowers/specs/2026-10-21-ama-database-design.md|c4ae00740c7d72e2945d0f67a926631bba198c7ecde2a46cd66523293b21e8d4|
|docs/database/ama-data-dictionary.md|7d34063a86573c6812924a27fff6dc5f2b98cc849dabafb16b9487074fac1d8b|
|docs/database/ama-schema-draft.sql|ebf1eae51b303d00b2ed35c4fbd5ba4c166813564032875a6ba0fb35ba743c93|

All four historical proposal/report files, the initial independent review and the three adopted database design files match the previously captured bytes. No test or implementation execution is represented by these integrity checks.
