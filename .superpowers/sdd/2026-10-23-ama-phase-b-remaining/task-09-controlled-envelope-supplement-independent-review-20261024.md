# Task9 controlled-envelope supplement — independent SPEC and quality review

## Spec Compliance

- **PASS — the narrow design supplement is compliant.** Reviewed the complete 38-line addition in `task-09-controlled-envelope-supplement-review-20261024.diff`, against adopted R1 sections 6.1–6.3 and the adoption wrapper. This is a design-only verdict on the new transport representation, not a Task9 implementation, runtime, source-closure, deployment or phase verdict.
- The identified contradiction is real: R1 `task-09-minutes-generation-design-r1.md:184–188` binds all reached generation/processing/review roots into the logical hash, while the exact POST structure at `:196` supplies no separate complete root union. The input-manifest projection in `backend/core/src/main/java/ama/minutes/contract/GenerationFingerprint.java:80–89` is not an inventory of that complete reached union. Requiring actual recomputation at R1 `:196` cannot be discharged by comparing two supplied digest strings.
- Supplement `task-09-controlled-envelope-supplement-20261024.md:9–11,23–29` supplies the missing logical data without changing the exact POST body, normalized payload, logical key, first execution or typed hash domains. Its adapter/provider division is appropriate: original archive completeness/authenticity stays with the authentic prepared adapter, while the provider independently verifies received canonical data and all stated cross-bindings. Hash equality is expressly not archive authentication.
- Supplement `:15–19` specifies a single canonical header encoding, bounded strict decoding and parsing, rejection of duplicates/unknown fields/noncanonical representations, and aggregate pre-handler bounds. It does not grant a second 64 KiB allowance. Actual numerical effective limits are explicitly installation-qualified, including overhead and actual HTTP client/server/parser limits; a complete envelope that cannot fit fails before marker/send.
- Supplement `:33–37` preserves R1 `:197–204` durable immutable mapping, restart validation, exact duplicate equivalence, lookup-only recovery and no generation resend/executionNo>1 semantics. Nothing in this supplement grants Task7 effect authority, converts generic HTTP failure into no-effect proof, or authorizes repairing accepted durable state from a caller request. This agrees with adopted `docs/superpowers/specs/2026-10-23-ama-phase-b-minutes-generation.md:11,17`.

## Strengths and concrete quality checks

- **Transport JSON and logical hashing remain distinct.** Supplement `:17,26` correctly uses `CanonicalJson.encode` for canonical JSON and `CanonicalValueCodec.sha256(ContractValues.envelope(...))` for the logical digest. Inspected `CanonicalJson.java:14–15,30–42`: scalars are validated, object keys are UTF8-ordered, arrays retain order, and JSON output is bounded. Inspected `CanonicalValueCodec.java:22–25,45–68,81–98`: hashing performs canonical-budget preflight and emits typed binary tags/lengths rather than JSON bytes. `ContractValues.java:29–30` defines exactly profile/kind/data. Thus the supplement does not silently redefine the existing hash as SHA256 of JSON or base64.
- **Input-fingerprint recomputation matches the supported implementation.** Supplement `:25` uses profile `ama.generation-input.v2`, kind `GENERATION_INPUT` and the exact input manifest. `GenerationFingerprint.java:15,99,134–140` identifies the profile and hashes that manifest. A single additional focused source read of `DomainHash.java:10–16` resolved the named risk that an unseen wrapper might alter the formula: it validates the profile artifact, then calls the same typed envelope hash. The provider's consistency check intentionally does not replace adapter archive/profile validation.
- **Boundedness addresses the early parser boundary.** Supplement `:19` includes header names, values and framing cumulatively, actual pre-handler limits, checked arithmetic, and decoded/tree/node/string/root intersections. It disallows truncation, root dropping and header chunking. This extends the inherited finite transport requirement at R1 `:208` without claiming any deployed server already satisfies it.
- **Durability covers the newly supplied information.** Supplement `:33–35` requires storing the exact envelope, validating it and its payload/input/request bindings on reload, and rejecting changed-root duplicates or corrupted stored metadata. Merely persisting the previous digest tuple would not meet this design. Required adversarial evidence at `:37` includes changed roots with unchanged supplied hashes and a fresh provider process, which directly target the original gap and the new persistence surface.

## Issues

### Critical (Must Fix)

None found within this narrow design supplement.

### Important (Should Fix)

None found within this narrow design supplement.

### Minor (Nice to Have)

None raised. Deployment-specific constants are deliberately future qualification evidence, not an omitted product quota to invent in this ruling.

## CannotVerify — required future evidence boundaries

1. **Actual transport limits and raw field visibility:** supplement `:15–19,37`. No concrete installed HTTP stack/settings or raw-wire results are part of this diff. The source/evidence review must check numerical aggregate/per-field/count limits, precise actual framing accounting, client-added/fixed header overhead, bounded pre-handler rejection, and whether the actual parser preserves or rejects repeated/case-duplicate/combined/folded/whitespace-bearing values before information is normalized away. Exercise supported maximum success and over-limit rejection on the actual client/server path. Application checks after unbounded parsing do not satisfy this requirement. No currently supported header capacity is certified here.
2. **Complete original preparation and immutable cross-bindings:** supplement `:11,23–29,37`. This design diff cannot establish that the eventual adapter derives every reached generation/processing/review root from the same authentic immutable prepared binding, or that it validates the entire bounded request before admission. Later source review must verify that path plus the actual provider recomputations and the unchanged payload/hash vectors. Include missing/duplicate/unsorted roots and changed-root/supplied-hash-equality attacks; neither agreement of caller-supplied digests nor a synthetic partial manifest proves closure.
3. **Persistence and restart correctness:** supplement `:33–37`. Later implementation/evidence must demonstrate durable exact envelope/payload storage and reload validation before accepted receipt/result or reserved-computation recovery, no accepted-state repair from POST, one result identity across a fresh provider process, and fail-closed corrupted or changed mappings. Inherited fsync/atomic rename/exclusive-lock/complete-prefix/torn-tail requirements remain binding. No store or process was touched for this review.
4. **Implementation quality and tests:** supplement `:37` expressly defers independent source review. This addition supplies required scenarios, not executable tests or passing results. No test was run, no implementation was reviewed as complete, and concurrent source reads below are observations only. Final implementation and raw RED/GREEN/failure/source evidence must receive their own gate; this design PASS cannot substitute for it.

## Assessment

**Task quality: Approved — narrow design supplement only.** The exact canonical-envelope header is a coherent, bounded way to provide the missing hash inputs while preserving the adopted body, payload, hash and Task7 semantics. The decision remains conditional on satisfying its explicit implementation and actual-runtime evidence requirements; this is not runtime qualification.

## Review scope, method and seal

- Date: 2026-10-24. Workspace: `/home/tanren/ama/.worktrees/phase-b-completion`.
- Process read: `/mnt/c/Users/LING/.codex/plugins/cache/openai-curated-remote/superpowers/7.0.1/skills/subagent-driven-development/task-reviewer-prompt.md` (9031 bytes; SHA256 `bfed55c8f19d0ff6ed57b0fe09127c92890e9311c6c73917129598826bf9ebe8`). Independent task-scoped SPEC plus quality review; no delegated reviewers.
- The supplement was read first among task documents, and its full addition diff was read once. The initial full R1 output was truncated, so all binding sections 6.1–6.3 were then explicitly read without truncation, together with relevant manifest context. The short adoption wrapper was read completely. No original design or historical review was changed.
- Focused outside-diff source checks were limited to the named canonical-format/hash-equivalence risks above: CanonicalJson, CanonicalValueCodec, ContractValues, GenerationFingerprint, and its directly called DomainHash wrapper. No broader source crawl, Git command, test, DB, Docker, private-environment, network or runtime action occurred. The only write is this requested review report.
- Source may change concurrently. These hashes seal the bytes observed during this review; they are not a fixed final implementation seal or an adoption of unrelated ongoing Task9 work.

| Reviewed input | Bytes | Observed SHA256 |
|---|---:|---|
| W/task-09-controlled-envelope-supplement-20261024.md | 9216 | `150f2a12df60f117edb35c1d3fdb265713b79b7116209d095287f155faff5c49` |
| W/task-09-controlled-envelope-supplement-review-20261024.diff | 9340 | `a7331b07c0142df6ca1969e32d72668b344662966c302c8a3229a6ba60e57f1a` |
| W/task-09-minutes-generation-design-r1.md | 70871 | `b1b3343a1216b9df621c3db560412fdde908499890823c9e75820e620969add2` |
| docs/superpowers/specs/2026-10-23-ama-phase-b-minutes-generation.md | 4449 | `99746a067ed0386f15817d06c09762feb87d2071387fc50d50d47b6195b109db` |
| backend/core/src/main/java/ama/config/CanonicalJson.java | 3153 | `ca70242b22f7878bb95a6de53e49fca1bac708cd380c2fd17ec111d8329ea327` |
| backend/core/src/main/java/ama/canonical/CanonicalValueCodec.java | 6649 | `3b8b080a5de3d66fbe67be204282d5b82bce02e386854047bfe5b5193e4b8d93` |
| backend/core/src/main/java/ama/minutes/contract/ContractValues.java | 3054 | `4ea8425b4e97b946f96de46be4a85e630fce5d346d30ba07e179b2dcb4d2b7ec` |
| backend/core/src/main/java/ama/minutes/contract/GenerationFingerprint.java | 16929 | `8400c26be2c2e064baa10fb14736814e93d3fdd2f744420496e686c1bce1cfa3` |
| backend/core/src/main/java/ama/minutes/contract/DomainHash.java | 978 | `2de639b0714ef2fdbaa9164e741fd65806ca3029c8e688f6b9718aa19c2d94d6` |

W denotes `.superpowers/sdd/2026-10-23-ama-phase-b-remaining` in the workspace above. All source citations refer to the observed byte versions in this table. Final report byte count and SHA256 are returned to the coordinator after writing; no self-referential report digest is embedded.
