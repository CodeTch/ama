# D01 / B00-1.4-proposed fix1 — R1 context-root correction

Date: 2026-10-23 (Asia/Shanghai). **Design-only author correction; fresh independent scoped SPEC and quality review required. No adoption is claimed.** This is an explicitly assigned continuation of the original author role; original conversation identity was unavailable. No child agent was dispatched by this continuation.

## Custody and exact review range

Owned tree: `/home/tanren/ama/.worktrees/phase-b-provenance-design`.
Branch: `codex/phase-b-provenance-design`.
Exact starting BASE: `214a63d978e4c04c0b3aec1c99d8d0ce3c093d9d`; checkout and index were clean at resumption. No merge, rebase, main/peer import or migration allocation occurred.

The full fix review range is **`214a63d978e4c04c0b3aec1c99d8d0ce3c093d9d..HEAD`**, including this tracked report and the complete 43-path allowlist below. A tracked file cannot contain the SHA of the commit that contains its own final bytes. By coordinator agreement, the root SDD copy and final handoff receive the exact final HEAD/range after the local commit; the independent review package must freeze both full commit IDs. The symbolic HEAD here is not a shortened commit ID or a different implementation base.

Sole requirements: copied `original-review/task-D-01-provenance-design-fix1-brief.md`, SHA `b729049728ec174e312b8d712f4f9d5235ca813e3e14d41be9d918dbb87b6b90`. Fully read independent review SHA `0803c06ab1ca427e9d8b2d2007704c07cf85cf30d959da3649374cc1574b74b0` and original context-roots evidence SHA `6644ddf1cd0e70d334cc8d35511b309a9b4919ab1fbfe62889656c6d95dfc3c2`. Review runner/result and original65 inventory were copied unchanged into the new archive; the old archive was not overwritten.

## Corrected root recipe

The original exact zero-evidence-root union usage recipe omitted C1 when an included element retained basis C1 while all its evidence used C2. It conflicted with the complete reached-row rule. This is a static membership contradiction, not an executed bad final, digest collision or SQL loss.

For domain D, **context membership = B(D) ∪ U(D)**, where B is every exactly included element basis regardless of evidence count, and U is the exact existing expected usage contexts:

|Domain|B(D)|U(D) and exact scope|
|---|---|---|
|ROWSET v2 / PINNED generation inherited set|All included previously saved input-set element bases|Set evidence and directory memberships; excludes body evidence and applied exclusions|
|REVIEW_SOURCES|Only the selected reviewed element basis, even with nonempty or zero intersecting evidence; none for a body-only review|Only exact intersecting evidence for the saved review scope; no other element/directory/applied-exclusion roots|
|FINAL|All included frozen element bases|Existing exact frozen body/set evidence, directories and applied exclusions|
|Current generation raw sourceContext|Existing top-level current context/selections/profiles|Existing exclusion.appliedContextId remains top-level; no own SourceUsage/fingerprint/TaskSpec enters the draft|

Each reached basis owes the same complete FullRow14 context, all snapshot selections, selected parser profiles, confirmation/material/catalog/parser/archive closure, stored-input/fence verification and retention as a usage context. Context, selection and profile arrays use actual full PK deduplication and unsigned UTF8 PK ordering; duplicate supplied keys and missing/extra/unrelated rows reject. A basis creates no EVIDENCE, adds no usage cardinality or source-support credit. Generated inherited bases use persisted graph ancestry with cycle checks; UUID/time order does not establish ancestry.

Current addendum §§2/5/5a/9, the acceptance prerequisite, generated dictionary and complete ER explanation were synchronized. The generator has one shared R1 paragraph used by dictionary and ER rendering, plus the element policy clarification. This changes explanatory rules only: version, 96-table/727-column relation shapes, columns, keys, checks, FK/index semantics, complete ER entities/edges and review DDL remain identical to BASE.

## Focused offline evidence and actual results

All new evidence is in `artifacts/phase-b/b00-provenance-design-fix1/`. Commands ran through PowerShell calling `wsl.exe -d Ubuntu-24.04 --cd /home/tanren/ama/.worktrees/phase-b-provenance-design --exec bash -lc '<command>'`.

1. Before wording changes, `python3 -B artifacts/phase-b/b00-provenance-design-fix1/context_roots_vector.py old-recipe-red` exited **1** with the genuine assertion `expected C1+C2, old zero-evidence recipe produced ['C2']`. Initial command/stdout/stderr remain in `old-recipe-red.*`. After coverage additions, `old-recipe-replay.*` reproduces the same assertion with the final runner SHA. This is an executed pure set-model assertion, explicitly **not runtime RED**. The historical recipe remains solely as this counterexample.
2. `python3 -B tools/checks/database_design.py generate` exited **0**, reporting `B00-1.4-proposed: 96 tables, 727 columns generated`. The subsequent pure render audit checks exact generated bytes; it does not call the checker's container verifier.
3. `python3 -B artifacts/phase-b/b00-provenance-design-fix1/context_roots_vector.py verify` exited **0**: **82 assertions**. Final command/source SHA in `final-verification-commands.json`; final stdout/stderr in `vector-final.*`; reduced projections and named assertions in `context-roots-vector.json`. Initial 76-assertion run logs remain in `vector-verify.*`; the later additions cover selection sensitivity and body-review scope.
4. `python3 -B artifacts/phase-b/b00-provenance-design-fix1/verify_fix1.py` exited **0**: **184 assertions**, **59 original65 paths byte-identical**, **35 original immutable paths byte-identical**. Full evidence is `render-preservation-verification.json`; final command/source SHA in `final-verification-commands.json`. It imports only pure schema/render functions and uses local Git, never database/container operations. A prior audit before the final raw-EOF attributes is retained as `before-eofattr-*`.
5. `git diff --cached --check` final run exited **0**, with empty stdout/stderr in `diff-check-final.*`. The first run exited **2** only because the copied original reviewer Python file has its historical blank EOF line; `diff-check-run1.*` retains it. The exact copied file now has narrow `whitespace=cr-at-eol,-blank-at-eof`, and the original context JSON has narrow `whitespace=cr-at-eol`. All new archive files use `-text`. Their original CR/blank bytes and original SHAs were preserved, rather than reformatting independent evidence. This tooling whitespace result is not relabeled as product RED.

The reduced fixture includes nonempty E1 basis C1 with all E1 evidence at C2, generated zero-evidence E0 at C3, manually based EM at C4, set directory C5, body evidence C6, applied exclusion/current raw context C7, and unrelated C8. Each context has two explicit snapshot selections and both exact selected parser profiles. Literal independent expected memberships are:

|Domain/vector|Exact context IDs|
|---|---|
|PINNED|C1,C2,C3,C4,C5|
|Selected E1 REVIEW, exact intersecting EV1|C1,C2|
|Zero-evidence E0 REVIEW|C3|
|Manual-basis EM REVIEW|C2,C4|
|Body REVIEW|C6|
|FINAL|C1,C2,C3,C4,C5,C6,C7|

Every main domain checks complete membership, omission/extra/duplicate context, selection and profile rejection, no fake basis EVIDENCE, exact evidence/usage cardinality, basis-ID/catalog/profile/selection change sensitivity and input permutation stability. The draft includes complete raw current C7 selections/profiles and top-level appliedContextId, while inherited PINNED excludes body/applied usages; current/descendant nonusage bases and dependency cycles reject, missing saved generated basis facts reject, and timestamp ordering does not affect ancestor validity. The zero-evidence review retains its basis and has no evidence/support credit.

The model uses short synthetic IDs, synthetic hashes and sorted JSON digest sensitivity. It is **not** the full normative SourceGraph/profile wire encoding, an archive-valid raw fixture, real stored-fingerprint/ACL/parser verification, or runtime qualification. Its in-memory graph visit checks are a reduced witness of the existing acyclic obligation.

## Original preservation and package hashes

The original65 delivery inventory is unchanged SHA `5fd3020b7e2866e9aa56f0c1c77ebcb1a83be06e6cba3bdb5bc8e41a7d42df70`; the audit checks each inventory entry against its exact BASE Git blob. All **59** paths outside the six current normative/attribute overrides preserve original bytes. The six exceptions are precisely `.gitattributes`, current prerequisite, current dictionary, current database spec's rendered explanation, current provenance addendum, and current checker-renderer. Their prior bytes remain the exact BASE blobs and original inventory hashes; every before/after SHA/size is in `render-preservation-verification.json`.

Original proposal/clarification/ACK, reports, manifests, archived B00-1.3 sources, raw metadata/probe logs, original93 whitelist vectors and original6871-byte draft vector were not rewritten. The 35-path immutable map also covers formal SQL and earlier metadata/evidence. The original manifest remains a historical manifest for its original bytes; it is not rewritten to bless the six overrides. The separate fix1 manifest covers only this correction package.

Unchanged review DDL SHA: `4c6bd89301ca6316f4469e99db333bc5440fa8dd330a907cfc685d79ff656a33`.
Original93 whitelist-vector file SHA: `19dc15af6b5c4e836ef07042dcef248c38199da2ad05fc76502fa7e5eafebc0d`.
Original draft vector remains **6,871 decoded bytes**, SHA `015a3a30f9970d41ef3ac45209794c7a4367e494b53a4f314f6cd0f0d3728112`; its committed hex/JSON/summary files also remain exact original65 bytes.

|New package member|SHA256|
|---|---|
|`context_roots_vector.py`|`7846b63c73d8941bdd25091deca63ee2e1f3dbdfbbd0cf8fc5eac600f6e5fdce`|
|`context-roots-vector.json`|`f50eaeb82d256c4d8a08e6104cf6ff679bc0e0b98d6a205849a4b65f97ed1089`|
|`verify_fix1.py`|`0f1146ba9c83918be6ec7e837de74900333d59dc7d63fb288d7444a753abad55`|
|`render-preservation-verification.json`|`b1b4dccf5ac677cb4f18fbb62d164fc335d670aa18414083a77c9b902486fdcf`|
|`fix1-allowlist.txt`|`785e632f81facf379e8762e2fadc1e43422ae0285182a1772b0c090f87d6c93d`|
|`fix1-manifest.json`|`128158fc98774a3e504b4dad5814942fb48f130f10801e07d2886e9edf21bd3e`|

The exact allowlist has **43 paths**, SHA `785e632f81facf379e8762e2fadc1e43422ae0285182a1772b0c090f87d6c93d`; the fix1 manifest covers **41 files**. It excludes itself and this tracked report to avoid self/report hash cycles; Git HEAD seals both. `python3 -B artifacts/phase-b/b00-provenance-design-fix1/package_seal.py build` produced that manifest. Staged exact path equality, 41 current/index byte/SHA checks, this report's index bytes and staged whitespace checks are enforced by `package_seal.py verify` before commit. Postcommit full HEAD/range and final verification result are sealed in the root SDD handoff copy.

## Complete fix path allowlist

```text
.gitattributes
artifacts/phase-b/b00-provenance-design-fix1/before-eofattr-render-preservation-verification.json
artifacts/phase-b/b00-provenance-design-fix1/before-eofattr-render-preservation.stderr.txt
artifacts/phase-b/b00-provenance-design-fix1/before-eofattr-render-preservation.stdout.txt
artifacts/phase-b/b00-provenance-design-fix1/context-roots-vector.json
artifacts/phase-b/b00-provenance-design-fix1/context_roots_vector.py
artifacts/phase-b/b00-provenance-design-fix1/diff-check-final.command.json
artifacts/phase-b/b00-provenance-design-fix1/diff-check-final.stderr.txt
artifacts/phase-b/b00-provenance-design-fix1/diff-check-final.stdout.txt
artifacts/phase-b/b00-provenance-design-fix1/diff-check-run1.command.json
artifacts/phase-b/b00-provenance-design-fix1/diff-check-run1.stderr.txt
artifacts/phase-b/b00-provenance-design-fix1/diff-check-run1.stdout.txt
artifacts/phase-b/b00-provenance-design-fix1/final-verification-commands.json
artifacts/phase-b/b00-provenance-design-fix1/fix1-allowlist.sha256
artifacts/phase-b/b00-provenance-design-fix1/fix1-allowlist.txt
artifacts/phase-b/b00-provenance-design-fix1/fix1-manifest.json
artifacts/phase-b/b00-provenance-design-fix1/old-recipe-red.command.json
artifacts/phase-b/b00-provenance-design-fix1/old-recipe-red.stderr.txt
artifacts/phase-b/b00-provenance-design-fix1/old-recipe-red.stdout.txt
artifacts/phase-b/b00-provenance-design-fix1/old-recipe-replay.stderr.txt
artifacts/phase-b/b00-provenance-design-fix1/old-recipe-replay.stdout.txt
artifacts/phase-b/b00-provenance-design-fix1/original-review/task-D-01-provenance-design-delivery-paths.json
artifacts/phase-b/b00-provenance-design-fix1/original-review/task-D-01-provenance-design-fix1-brief.md
artifacts/phase-b/b00-provenance-design-fix1/original-review/task-D-01-provenance-design-review-context-roots.json
artifacts/phase-b/b00-provenance-design-fix1/original-review/task-D-01-provenance-design-review-offline.json
artifacts/phase-b/b00-provenance-design-fix1/original-review/task-D-01-provenance-design-review-offline.py
artifacts/phase-b/b00-provenance-design-fix1/original-review/task-D-01-provenance-design-review.md
artifacts/phase-b/b00-provenance-design-fix1/package_seal.py
artifacts/phase-b/b00-provenance-design-fix1/render-preservation-verification.json
artifacts/phase-b/b00-provenance-design-fix1/render-preservation.stderr.txt
artifacts/phase-b/b00-provenance-design-fix1/render-preservation.stdout.txt
artifacts/phase-b/b00-provenance-design-fix1/vector-final.stderr.txt
artifacts/phase-b/b00-provenance-design-fix1/vector-final.stdout.txt
artifacts/phase-b/b00-provenance-design-fix1/vector-verify.command.json
artifacts/phase-b/b00-provenance-design-fix1/vector-verify.stderr.txt
artifacts/phase-b/b00-provenance-design-fix1/vector-verify.stdout.txt
artifacts/phase-b/b00-provenance-design-fix1/verify_fix1.py
docs/acceptance/b/material-provenance-design-fix1-report.md
docs/acceptance/b/material-provenance-design-prerequisite.md
docs/database/ama-data-dictionary.md
docs/superpowers/specs/2026-10-21-ama-database-design.md
docs/superpowers/specs/2026-10-22-ama-material-provenance-addendum.md
tools/checks/database_design.py
```

## Remaining limits and review disposition

No DB, Docker, network, DDL application, original 24 relational tests, original broad cohort, Flyway, backend/POM/API/runtime changes or unreviewed peer imports occurred. No original requirement/prototype/main-plan files were edited. An initial read-only parent AGENTS search emitted permission-denied for protected peer fixture directories; it changed no file and was not treated as RED.

The original R1 review found one P2; this correction is ready for coordinator FULLREAD and the same independent reviewer to examine R1 and any new breakage. Author verification does not close that review or adopt B00-1.4-proposed. Formal ordered migration allocation and later D01/B07/D03–D07 runtime, ACL, parser, archive and retention/concurrency acceptance remain required.
