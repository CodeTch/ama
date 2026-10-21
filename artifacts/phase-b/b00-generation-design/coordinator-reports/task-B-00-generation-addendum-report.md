# B00 generation-result addendum — initial proposal report

Status: STOPPED AT ROOT PROPOSAL READ/ACK GATE, 2026-10-22. No tracked B00 source edit or database authorization has occurred.

## Scope and exact state

- Owned worktree `/home/tanren/ama/.worktrees/phase-b-generation-design`; branch requested `codex/phase-b-generation-design`.
- Exact BASE = observed HEAD = `0812b6da0b143544de4075929b5dd01568547d14`; `git status --short` empty before and after. Root SDD outputs are intentionally outside this worktree and ignored coordination files.
- Only outputs: root SDD `task-B-00-generation-addendum-proposal.md` and this `task-B-00-generation-addendum-report.md`, under `/home/tanren/ama/.worktrees/phase-a/.superpowers/sdd/2026-10-21-ama-phase-b/`.
- Proposal is 89 lines (limit 220; no proposed executable DDL). SHA-256 `6abe12335303c176331089814e56aee32aae7b2c01a77ecc3cd9741350b044ff`.
- SQL NOT EXECUTED. No DB/Docker/service/company request, test, compile, formal SQL, migration/register edit, code edit, commit or child agent. Original requirements unchanged. Only reads, read-only Git/hash/line checks and the two requested SDD writes.
- One broad parent-directory AGENTS search encountered unrelated pre-existing probe data/log permission denials; no access/reset/repair attempted. No applicable repository AGENTS was found in ancestor paths or the owned tracked tree.

## Proposed correction / review focus

Eight normalized tables retain unselected sensitive entries, both labels, background text/source sets, raw action suggestions, exact target/source-bound reviews and final review membership. Only existing physical change is sensitive_exclusion multi-label PK plus category CHECK. Forecast 93 tables / 709 columns is a design arithmetic count, not an actual metadata result.
Directories/suggestions carry their originating generation_input ID so copied or mixed partial-generation sets do not relabel old suggestions as newly generated output. New action suggestions never automatically populate accepted action_item fields. No extra generation_input manifest/profile/epoch columns or managed metadata blob.
Important semantic change for root review: existing support_state stays the result's frozen classification and cannot prove reviewed support; applicable immutable result_review rows determine current reviewed support, and final_result_review freezes exactly the historical selection. Review does not assert semantic truth or grant source access. Source-support and action-field acceptance are separate review kinds.
The review UI policy remains open: per-item review versus an explicitly worded combined confirmation. First guarded automatic effective save versus explicit adoption also remains open. Schema enables both without choosing either. External-owner mapping, source access/enterprise role rules, support coverage semantics and separately reviewed anchor/hash codec remain prerequisites.
Cross-meeting/target membership, reviewer identity, source completeness and stale task/lease checks are owning-transaction invariants, not claimed as FK/CHECK guarantees. Meaningful implementation tests will be required later; this proposal does not claim them tested.
Migration detail requiring explicit ACK: all new tables are conceptually V012; final_result_review's final_confirmation FK waits for existing V013 parent creation (no writes before V013), matching existing phased-FK convention. No new migration number or registry change is made. If root prefers V013 table creation ownership instead, revise this proposal before tracked design changes.
V011 proof at this gate is whole-source identity: adopted 1.2 review DDL SHA remains `402def655f133ab10caa44b94e89ed6692756623d32705e6ab63c8176d79c362`; no tracked diff exists. Proposal names and leaves all eight V011 declarations/columns/indexes/CHECK/FKs unchanged, adding no new FK to them. Actual per-table equivalence checks belong to the post-ACK fixture phase, not an unexecuted claim now.
Historical V001..V009 present in this exact base were hashed below; V003/V004/V010 are absent from this owned base's migration directory. This does not authorize writing them or modifying another owner's shared V010; all applied/shared SQL remains immutable. No later root migration/code state was consumed.

## Input provenance

Tracked paths below are relative to the owned BASE worktree. Hashes cover complete file bytes; review used relevant original sections and current table definitions, not broad project history. Metadata is existing adopted evidence read from disk, never a new database query.

|Input|SHA-256|
|---|---|
|designDocs/智能会议助手一期需求说明文档.md|e5b11f49373418e7b4ea9b74071fed035f320612a4783de075980c13223cf783|
|docs/superpowers/specs/2026-10-21-ama-database-design.md|d7a0fc3988fe0b41fbd84bb7c69b96d344f6ed8fca07be312d10fc31630541bd|
|docs/database/ama-data-dictionary.md|9e2f80080fc1757295b66e920d54152018f35fbe733458af8a6032a4b2ccc7d9|
|docs/database/ama-schema-draft.sql|402def655f133ab10caa44b94e89ed6692756623d32705e6ab63c8176d79c362|
|docs/superpowers/specs/2026-10-21-ama-product-supplement.md|34a70d05e60b513faa8b7e98ba17e0d68008dcd05e260c210cbeb24bb181900c|
|docs/database/migration-register.md|3c18d8f3e268e13d6dd2f13ab21cf212d4d283a1eb4ea171534edd64b42a250b|
|docs/acceptance/b/database-upload-addendum-metadata-evidence.json|3de8041db8860f1547ff53a94b4ddd79470c4881340111fdafbc3ce5322399ad|
|tools/checks/database_design.py (declaration source)|41793c7f9b46dd0a88f8a24e037bd7b14348ccb1b7562c3e90ddf27dd6ec78c6|

Root SDD coordination input paths use the same root SDD directory as outputs:

|Input|SHA-256|
|---|---|
|task-B-00-generation-addendum-brief.md|eab35c92deafc04fc95a380e72ec62984dc9c9f73beda97242b3bc23e0d5e54f|
|task-B-07-generation-contract-brief.md|1fd753aefc659be23f03750f3469d650eb78c97b23aa326f6d27eb28a02387b7|
|task-B-07-generation-contract-proposal.md|80bde48809691a9883f76ff17affcc71c6003f749289f8d162d18a984ff148f9|
|task-B-07-generation-contract-report.md|14ad5e6bea73247044d5e593d9b176ccb03ca76c48e5983d5df69cb41c39fae6|

Current base migration hashes (all under backend/adapters/src/main/resources/db/migration):

|File|SHA-256|
|---|---|
|V001__foundation.sql|1726d9db3eb45d0fe86314dd41ad52d5f2332e3f16995cc246f3c3c532208335|
|V002__identity_acl.sql|68b0d478504a554344cb12dfa8ef79c62da5c6e3455317791762fca25e70f7d0|
|V005__tasks_outbox.sql|55cfdc4aabf169c82f30303ac4d03dce7f24b3c6af5b4cc3ebd0089ae7e7dda7|
|V006__audit_metadata.sql|f36b7ab2e8d32cf5de6eadcd86524ab269dce98f7906b808eff1a0e39325178f|
|V007__identity_sessions.sql|859970b83fc5623b18340c2de793ea35429cc3c0da5cbd1365b4763a5a64780d|
|V008__meeting_schema.sql|fa44424aa1ff32e049929d45865fe7133c2548fc9ea451f3350cd8984c118ce3|
|V009__file_schema.sql|79f048987282c82fd7a56d697a02e88c93fa45b3349bd4d43e840dca23ba437d|

Next action belongs to root: read and ACK exact proposal (or return concrete revisions). No tracked artifact edits or actual fixture work will proceed before that gate; fixture execution additionally requires its separate explicit authorization.


## Detailed-design delivery after explicit root ACK (appended; initial report preserved)

Root read/ACK and separate unique-fixture authorization received; original report above and initial proposal bytes remain preserved in root `*-initial-preserved.md` and tracked artifacts. Root superseded initial deferred-FK proposal: seven new tables V012; final_result_review V013 with both physical FKs immediately. Explicit content-level adopt+review may create fresh assertion atomically; neither pending user policy was inferred.

Final exact reviewed BASE `0d2079cd64e5b61bc5787ca5adfaa782e5cd4a40` → HEAD **`7fb166b01b6ff45213b973640ff7457c3cf36e56`**. Clean worktree `/home/tanren/ama/.worktrees/phase-b-generation-design`. Commit includes exact source/evidence allowlists (34 paths total); committed bytes checked against both hash lists after commit. Independent review remains pending; no adoption claim.

Primary acceptance: `docs/acceptance/b/database-generation-addendum-review.md`.
Full actual metadata: `docs/acceptance/b/database-generation-addendum-metadata-evidence.json`.
Baseline/V011 byte+metadata delta: `docs/acceptance/b/database-generation-addendum-delta-evidence.json`.
Preserved 1.2 design/dictionary/DDL/checker: `docs/database/design-history/B00-1.2/manifest.json`.
Fixture/probes/source/evidence: `artifacts/phase-b/b00-generation-design/`.

Actual own MySQL8.4.6 fixture `ama-test-b01-991f0386f26d`, ID `a5bd8e25f6047c542e94af6aac5bec68e60532c183d460714586480b2e22add3`, 127.0.0.1:44649, initial0 tables, nativeAIO0. Full93 tables/709 columns/290 index definitions/441 index columns/109 checks/196 FK constraints/203 FK columns matched. DDL SHA `ebf1eae51b303d00b2ed35c4fbd5ba4c166813564032875a6ba0fb35ba743c93`.
23 actual relational probes passed, including four explicit database-boundary witnesses for identity/crossmeeting/wrong-target/content-hash mismatch (raw SQL does not enforce these; future application checks required). All synthetic business rows rolled back; final93-table count leaves only the DDL outbox sequence row. Fixture retained for review, no cleanup/reset/repair.

Among original85 tables only sensitive_exclusion PK+two-labelCHECK changes; all original columns/FKs and other84 CREATE blocks identical. V011 eight CREATE+FK blocks and all metadata/incoming FKs equal. V001..V010 existing SQL, shared helper, registry, old1.1/1.2 metadata unchanged. Root pinned env sourced; process-local narrow nativeAIOOFF wrapper only, sysctl unchanged. No formal migration/application/company call/children.

One shell quoting attempt failed before SQL; concrete fixture-name command succeeded and first DDL+metadata execution exit0. No failed DDL or concealed reset. Actual DDL/metadata/constraints are design validation only; business transactions/identity/source ACL/semantics/fences/concurrency and pending product policy remain unimplemented/unaccepted. Await fresh independent design/spec+quality review of exact BASE..HEAD.
