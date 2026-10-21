# B07 generation contract preparation report

Status: proposal prepared; root rulings and independent review required. This is not B07 implementation completion or adoption. Date: 2026-10-22.

## Output / scope

- Proposal: `task-B-07-generation-contract-proposal.md` (146 lines; cap 220). This report is the only other new output.
- Requested reviewed base: `d71a05bea0a194bb1a536998b4bc8dde65c5241f`, `/home/tanren/ama/.worktrees/phase-a`.
- Root advanced concurrently to `0812b6da0b143544de4075929b5dd01568547d14` before final read-only check. `git diff --name-only d71a05b -- <all ten tracked source paths listed below>` returned empty. Thus these original/spec/schema/parser documents retain reviewed-base bytes; no new B05 implementation was read or consumed. SDD briefs/reports below are exact hashed local coordination inputs, not Git adoption claims.
- No implementation, SQL, schema, original document, test runner or migration registry modified. No DB, Docker, HTTP, service, company request, test, compile, migration, commit or child agent executed. Only document reads, Git read-only status/diff, hashes and these two new SDD files.
- Tracked `git status --short` was empty before/after authoring; SDD outputs are ignored files, so clean status alone does not prove their absence. Explicit file existence, line count and output SHA were inspected.

## Findings / decisions for root

1. Seven kinds include DISAGREEMENT. Sensitive/background/related-material directories are separate. Original “其他六类” cannot override explicit seven-kind enum or enable exclusion controls in other directories.
2. Preferred immutable manifest is reconstructed from existing generation_input pointers, immutable row graphs, durable_task version/epoch and exact prompt/model configuration artifacts. No extra manifest file is required for bounded B-only scope if those invariants hold. Profile/schema versions can be fixed through immutable config references. Current provider support is a prerequisite, not an adopted fact. Missing immutable parse selection, custom prompt instruction or metadata input cannot be reconstructed merely from base_revision; freeze explicitly before enabling.
3. First-success-readable versus explicit adoption needs R1 ruling. Recommended minimal safe coordination is explicit first-save/adopt before participant-effective reading. A guarded automatic first-adoption alternative is described but changes original B07/B00 authority and is not adopted. No candidate is silently current; HUMAN title/unsaved input protection remains.
4. Existing source_locator/evidence_link support concrete typed provenance/anchors without new columns. Structural reference validity does not prove semantic support; PARTIAL-by-default plus exact-version authorized review is a proposed conservative policy. Root must approve support promotion/review persistence convention before claiming final readiness.
5. B00 does not contain a lossless destination for arbitrary unselected sensitive detections/generated background/owner-mention output. R3 alternatives are explicit capability-limited derived view, separately reviewed immutable file_reference result attachments, or later reviewed schema; none silently adopted. Multi-label sensitive locator handling is also a concrete reviewed choice, not a new column.
6. S1–S5 split provides disjoint contract/input/model/result/integration ownership, seams and meaningful tests. B02/B03/B04/B05/B06 dependencies, real company model protocol, production formats/budgets and D01/D03 semantic capabilities stay open. V012 is the existing reserved migration; original B07 V007 placeholder is not used.

## Exact source provenance

All paths relative to `/home/tanren/ama/.worktrees/phase-a`; SHA-256 covers complete file bytes, not just excerpts. Tracked sources below were checked unchanged against d71a05b. SDD local inputs are read-only and identified by hash.

|Input path|Version / role|SHA-256|
|---|---|---|
|`designDocs/智能会议助手一期需求说明文档.md`|original; modules II–IV, participant viewing|`e5b11f49373418e7b4ea9b74071fed035f320612a4783de075980c13223cf783`|
|`designDocs/线上模型服务接口文档.md`|original; especially §7 and client timeout limits|`be60b22cf6d05ddf534b83015a2993910cd1c395e480be908caadc71376bb8b2`|
|`docs/superpowers/specs/2026-10-21-ama-database-design.md`|adopted B00-1.2; §§6 onward and shared invariants|`d7a0fc3988fe0b41fbd84bb7c69b96d344f6ed8fca07be312d10fc31630541bd`|
|`docs/database/ama-data-dictionary.md`|B00-1.2; generation/transcript/evidence/file/material/task fields|`9e2f80080fc1757295b66e920d54152018f35fbe733458af8a6032a4b2ccc7d9`|
|`docs/database/ama-schema-draft.sql`|B00-1.2; read relevant DDL only, never executed|`402def655f133ab10caa44b94e89ed6692756623d32705e6ab63c8176d79c362`|
|`docs/database/migration-register.md`|B00-1.2; adopted registry / V012 reservation|`3c18d8f3e268e13d6dd2f13ab21cf212d4d283a1eb4ea171534edd64b42a250b`|
|`docs/superpowers/specs/2026-10-21-ama-product-supplement.md`|approved v0.1|`34a70d05e60b513faa8b7e98ba17e0d68008dcd05e260c210cbeb24bb181900c`|
|`docs/superpowers/specs/2026-10-21-ama-architecture-design.md`|v0.2; §§6/8/9/12/13|`f913ea4002759173bb2c9b53fe096e27b75bb387308a103d68215e8c19a0e4a2`|
|`docs/acceptance/b/database-design-review.md`|B00 review provenance / rule ownership|`feded94b4dfcfd380ae945da57b84b8f8ac869fb70eff34484a7b4596d16378b`|
|`docs/acceptance/b/transcript-parser-prerequisite.md`|ama.strict-utf8-lines.v1; adopted but unwired|`3ef135d1892f38b3e5ebe99dfc3389d7d3bc6393c0bfa51ef413d6b8718b2655`|
|`.superpowers/sdd/2026-10-21-ama-phase-b/task-B-07-generation-contract-brief.md`|current scoped requirements; local SDD|`1fd753aefc659be23f03750f3469d650eb78c97b23aa326f6d27eb28a02387b7`|
|`.superpowers/sdd/2026-10-21-ama-phase-b/task-B-07-brief.md`|original task; local SDD|`6936ab55fae017b2bcebe5aac37751bedc594834c80ef3b83208c2f56305a8a9`|
|`.superpowers/sdd/2026-10-21-ama-phase-b/product-supplement-approved.md`|approved v0.1 mirror; local SDD|`34a70d05e60b513faa8b7e98ba17e0d68008dcd05e260c210cbeb24bb181900c`|
|`.superpowers/sdd/2026-10-21-ama-phase-b/task-B-00-upload-addendum-review.md`|B00-1.2 incremental independent review; local SDD|`4b42c609b3e2ee2fec1e67e4d3eb8bdeea924a3eb79ffe3dfff0a282347e47a0`|
|`.superpowers/sdd/2026-10-21-ama-phase-b/task-B-06-parser-report.md`|parser prerequisite report; historical status qualified by adopted doc|`cbe8a999683432a27f08278f82414c4a8268b9d2f0382930aee2a6af4e3c6825`|
|`.superpowers/sdd/2026-10-21-ama-phase-b/task-B-06-schema-report.md`|preparation only; no applied/adopted V011|`d862e0993f46a7348e9f26f58672192b7e763d059034f372e227e417ae045992`|

## Verification limit

No tests executed. Document self-check only: required six contract areas present; proposal 146 lines; no adopted extra kind/column/auto-adoption; exact original LLM limitation retained. Prototype was not needed for seven names and no placeholder JavaScript was used. B05 observe fix remains unconsumed; concurrent root code integration is outside this report.

Proposal SHA-256: `80bde48809691a9883f76ff17affcc71c6003f749289f8d162d18a984ff148f9`.
