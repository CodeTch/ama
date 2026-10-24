# Task9 design R1 closure — T9DR-01/P2

2026-10-23. Repair round 1/5. **DESIGN PROPOSAL ONLY; independent bounded re-review pending.** This closure records a document correction, not an adopted verdict, source seal, runtime availability, test pass or Phase B qualification.

## Inputs and full-read boundary

Fully read the independent review `task-09-minutes-generation-design-review.md`, raw SHA256 `3a2458bc60723256906103b260bdc20afab9e9cd2f83ad9d22da276b794d7e1a`, including its sole actionable T9DR-01/P2 and all prospective gate/risk dispositions. Fully read the coordinator's adopted cross-task ruling `task-09-10-shared-rank-ruling.md`, raw SHA256 `3b10ab12f46f7b4a2f70a91871f20d4f4052662b1cfee2b08700b02188b6bc75`. The original proposal remains byte-for-byte unchanged at raw SHA256 `cf174e2a54827da3c2e2152dbddf7cfc292680882e676f0291fd1c014835c56b`; the review and ruling hashes were also rechecked unchanged after writing R1. An initially truncated tool result was replaced by separate complete reads before repair.

The full replacement is `task-09-minutes-generation-design-r1.md`, raw SHA256 `b1b3343a1216b9df621c3db560412fdde908499890823c9e75820e620969add2`.

## Sole finding and exact correction

T9DR-01/P2 identified a mismatch between the proposed product route prefix and the existing versioned API, plus an ambiguous backend-relative OpenAPI output location. Focused read-only checks confirmed `backend/api/src/main/java/ama/api/MeetingController.java:30` uses `/api/v1/meetings`, and repository-root `contracts/openapi/api.yaml:22` declares that path with sibling-file refs. This is contract alignment, not an authentication bypass.

- All 12 Task9 product route occurrences, including examples and the nine-row endpoint table, now use `/api/v1/meetings/{meetingId}/minutes/...`.
- The files table explicitly qualifies `contracts/openapi/minutes.yaml` as repository-root, outside backend. It assigns the designated contract owner responsibility for adding Task9 paths and refs to the existing repository-root `contracts/openapi/api.yaml` before Task12 client generation. This is a prospective contract integration requirement; no YAML or source was created or modified by this repair.
- The controlled provider's three `/v1/minutes/...` protocol lines remain exactly unchanged.
- The opening scope sentence now accurately says that only this R1 replacement and its closure are written for this repair. No other design text changes.

The coordinator's shared rank ruling remains binding: established Task3/6 processing rank precedes the original Task9 minutes extension; all candidate locks occur at their declared final domain rank, before sessions/files/tasks/attempts/external rows. The original Task9 architecture text and the ruling's common qualified effective complete-domain budget requirement are retained without introducing independent draft capacity permissions. No lock order, numeric budget, settings producer, provenance proof, authority mechanism or integration gate was redesigned.

## Document verification and remaining gates

Read the complete `git diff --no-index` output between original and R1; its exit status 1 denotes the expected document differences. Independently reversed the four exact replacement operations in memory and confirmed case-sensitive equality to the complete original text, including original line endings. Confirmed 12 versioned product route occurrences, absence of old product prefixes/placeholders, and exact equality of all three provider protocol lines. Verified the existing repository-root API aggregate and controller paths. These are document/path checks, not runtime tests.

All original prospective evidence requirements, actual settings initialization/template provenance, complete graph/archive/input/authentication semantics, Task10/11 integration and API activation gates, capacity UNKNOWN and other qualification limits remain in force. Existing independent-review risk dispositions, including unavailable capacity after a committed marker and genuine final shared-facade reconciliation, remain prospective obligations. Correction of T9DR-01 does not claim that those obligations have been met.

Only the two new Markdown files were written. No source or fixture edits, runtime tests, DB/Docker/network/private-environment access, Git configuration/index/HEAD mutations, or helper agents. Status: the sole finding is addressed in the R1 proposal, awaiting independent bounded re-review and coordinator adoption; no additional implementation or runtime closure is asserted.