# Task11 exact design adoption preparation

2026-10-23. Document custody/adoption only. Prepared the exact previously reviewed full R1 replacement and its historical evidence for root adoption. No new design review, product implementation, source changes, test execution, migration generation/allocation/freeze/ACK/application or runtime/provider qualification was performed. Root owns later explicit-path commit and any plan/state updates; this report does not assert a commit occurred.

## Authority and verification

Read the Task11 pause section, W/current-state.json, the full original and R1 reviews, original root zero-effect ruling and fix brief, Task9/10 adoption wrappers and their raw manifest/byte-protection conventions. Root's prior full read of the actual R1 delta/closure/review remains the review basis; no broad re-review was opened. R1 review states both T11DR-01/02 ADDRESSED, scoped SPEC PASS/design APPROVED and zero new findings. The original ISSUES FOUND/NEEDS FIXES review remains preserved as historical evidence.

Fresh verification used ordinary WSL Python reading raw bytes and hashlib.sha256, with PowerShell literal here-string stdin. Exit 0. All 13 copied evidence files match the pause archive manifest's expected byte lengths and SHA256 and are byte-for-byte equal to their current W originals. All 7 core original/R1/closure/review/diff artifacts also match separately pinned hashes present in the original review, R1 review or pause Task11 section. All 14 raw manifest entries (13 evidence files plus nested .gitattributes) match actual artifact files; exact directory membership is 15 files including manifest.json. Nested .gitattributes is exactly 8 bytes `* -text` plus LF, SHA256 `705fd4d6451a31d36b3df7de96f83f30ac976c9b4a6d1e51671d8e2f33e2d0da`. Manifest self-hash is recorded here rather than recursively inside itself.

Artifact manifest SHA256: `151c7fee411613c3bf778432469f4905d40b085f4e456095fd160732712c53c2`.

Wrapper: `docs/superpowers/specs/2026-10-23-ama-phase-b-final-publication.md`, 7707 bytes, SHA256 `2bff74ecdf96accb22483418a358992b875481d131075fcf2f4088c89d63f6a6`.

The wrapper explicitly adopts the exact R1 while preserving originals and review outcomes. It retains the logical-only publication_submission proposal and the independent exact post-Task8 DDL gate, genuine Task7/private queue authority and marker/UNKNOWN boundaries, authenticated caller/delegation and dual-ACL official-document relay gates, all-mutator/final/readiness/rank/budget gates and future source/runtime evidence limits. No existing plan/spec/progress/current-state file was edited. No Git/index/HEAD, DB, Docker, private environment, network, runtime or resource operation was performed. All mutations were new files at the allowlist below plus this report; original evidence files were only read.

## Exact raw evidence

| Raw evidence | Bytes / lines | SHA256 |
|---|---:|---|
| `task-09-10-shared-rank-ruling.md` | 3478 / 13 | `3b10ab12f46f7b4a2f70a91871f20d4f4052662b1cfee2b08700b02188b6bc75` |
| `task-11-design-brief-prepared.md` | 4836 / 15 | `b098720328b80ddc29b5d6dc097d83767b4e63338f463cbb19cb2f47fca90d15` |
| `task-11-design-fix-01-brief.md` | 7653 / 34 | `7afd80d136ee77a065bc21b11a206e57c654eacda4c9a877af24935789dab85b` |
| `task-11-design-r1-review-diff.txt` | 97358 / 336 | `f4b03cf34487e9e357a39e50eea6d27608210b1f1ca478f8f7e1eaf39af0c9a0` |
| `task-11-design-review-diff.txt` | 69815 / 319 | `d9d41e1312097172c20ed8f3873a2d255d6750bba4ff810b0ba1c4d95c469c64` |
| `task-11-final-publication-design-r1-closure.md` | 15802 / 93 | `b906213c0ff8af494f54495b3dbd337cbf3283994dfabe2e6798255438808c51` |
| `task-11-final-publication-design-r1-review.md` | 11506 / 65 | `247c16e565986ebb61c8b60589240fd0fbd6bf82153826cb57bde72db42d3241` |
| `task-11-final-publication-design-r1.md` | 102624 / 405 | `f18ae4734603709284d91345b2909eca9687627c825444fd8cfa06dd49d36ffc` |
| `task-11-final-publication-design-review.md` | 12601 / 74 | `b1b6a4af59072f322fd71237636a608d9f0c2a530a98ce339ec57c56574a84f9` |
| `task-11-final-publication-design.md` | 69377 / 316 | `a8ec962bfee7ed1025a8d0dc113750447d9a8cc95b006b758cc544de900981e8` |
| `task-11-plan-extract.md` | 539 / 3 | `58ea9b87eb6908d1c363a9a1cb054965fbe16df1c832741b0d53cd2c0e5632dc` |
| `task-11-r1-root-delta.diff` | 60014 / 185 | `1dbebe5a89ffe5a7ea9b7437d90cfae9ea767a24420f2b7bad8d8e2c617794e8` |
| `task-11-zeroeffect-root-rulings.md` | 3115 / 11 | `9a4718ece70afbd631affe8464ccb75673d32af9e7b51820cdf2bae211709750` |

The 336-line actual R1 review diff SHA256 `f4b03cf34487e9e357a39e50eea6d27608210b1f1ca478f8f7e1eaf39af0c9a0` is preserved as its own exact file. The author's normalized document-diff recipe/hash in closure and the separately preserved root delta are different artifacts; neither substitutes for that actual review diff.

## Prepared root commit allowlist

16 new candidate tracked paths; root may commit only these explicit paths for this adoption. No old artifact/source path is included.

- `artifacts/phase-b/b-remaining/task-11-design/.gitattributes`
- `artifacts/phase-b/b-remaining/task-11-design/manifest.json`
- `artifacts/phase-b/b-remaining/task-11-design/task-09-10-shared-rank-ruling.md`
- `artifacts/phase-b/b-remaining/task-11-design/task-11-design-brief-prepared.md`
- `artifacts/phase-b/b-remaining/task-11-design/task-11-design-fix-01-brief.md`
- `artifacts/phase-b/b-remaining/task-11-design/task-11-design-r1-review-diff.txt`
- `artifacts/phase-b/b-remaining/task-11-design/task-11-design-review-diff.txt`
- `artifacts/phase-b/b-remaining/task-11-design/task-11-final-publication-design-r1-closure.md`
- `artifacts/phase-b/b-remaining/task-11-design/task-11-final-publication-design-r1-review.md`
- `artifacts/phase-b/b-remaining/task-11-design/task-11-final-publication-design-r1.md`
- `artifacts/phase-b/b-remaining/task-11-design/task-11-final-publication-design-review.md`
- `artifacts/phase-b/b-remaining/task-11-design/task-11-final-publication-design.md`
- `artifacts/phase-b/b-remaining/task-11-design/task-11-plan-extract.md`
- `artifacts/phase-b/b-remaining/task-11-design/task-11-r1-root-delta.diff`
- `artifacts/phase-b/b-remaining/task-11-design/task-11-zeroeffect-root-rulings.md`
- `docs/superpowers/specs/2026-10-23-ama-phase-b-final-publication.md`

Preparation report only (W custody output; root decides separately whether to track): `.superpowers/sdd/2026-10-23-ama-phase-b-remaining/task-11-adoption-preparation-20261023.md`.

## Inconsistencies and limits

No hash, length, manifest membership, evidence identity or adoption-boundary inconsistency was found. No independent tests were appropriate for raw document copying; document identity checks above are the evidence. One initial shell-only ancestor AGENTS read failed due to quoting and was corrected with literal Python stdin; it changed no file and did not contribute verification evidence. No AGENTS.md was found at the examined workspace ancestors or within docs/artifacts/.superpowers. No Git/index/HEAD inspection was used, as instructed; root must independently confirm staged explicit paths when committing.
