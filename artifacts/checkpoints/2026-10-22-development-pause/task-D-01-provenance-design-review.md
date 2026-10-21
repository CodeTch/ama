# Independent D01 / B00-1.4-proposed SPEC and technical-quality review

Date: 2026-10-22. Reviewer: fresh independent agent `d01_provenance_review`. Design-only review; no child agents.

## Verdicts

**SPEC: CHANGES REQUIRED. Technical quality/design correctness: CHANGES REQUIRED.**

One P2 finding (R1) concerns the exact context-root rule for a saved element whose evidence has been rebound independently of its inherited basis. No P0/P1/P3 finding. The normalized schema, recorded metadata and byte-preservation checks otherwise passed. R1 is a narrowly scoped design correction, not a request to implement D01 runtime or reapply any database. Resolve it in the original author's design and focused offline evidence before adoption.

These verdicts do not assign a migration version, adopt B04/B07 peer implementations, approve real parsers/OCR/quotas/platform services, or close runtime D01/B07/B08/D03–D07.

## Exact reviewed range and custody

Author tree: `/home/tanren/ama/.worktrees/phase-b-provenance-design`.
BASE: `91774bd4eeca2c3f3233f0e6733b32c2abef28fc`.
HEAD: `214a63d978e4c04c0b3aec1c99d8d0ce3c093d9d`.
All four owned commits included: `ccbad3e8e0fca86aa038b48989b9e6095bdeba2c`, `d611d75bd61c4729d4fc61e5c3debedcc03fdedf`, `c7950be0e758c99f188572da364764561dc9a92c`, `214a63d978e4c04c0b3aec1c99d8d0ce3c093d9d`.

Independently verified all 65 paths / 2,530,605 bytes against the delivery inventory AND exact HEAD Git blobs; clean author checkout before/after review. Full-range `git diff --check` passed. The complete supplied full-index diff is the exact Git patch with commit/stat prefix; its SHA is `aef7586c2bd13b2a2c377393bda087ba72db927ee7ff3581f832108decd895a3`. The eight-file complete source supplement was reconstructed byte-for-byte and matched SHA `7962b8ec121f4d86808655512f403df54cdc3f7ce210c6a38ff3d9ecb1cc3d68`. The 65-path inventory SHA is `5fd3020b7e2866e9aa56f0c1c77ebcb1a83be06e6cba3bdb5bc8e41a7d42df70`.

Full author report read and hash-verified: `a8bd0d246e584f96b1e3d8df5a4aeb91cb9c6c1453396fbdfc7532766bfcdb90`. Original proposal (151 lines) `f8d6236c8d324630959e72a3f00cc312bf97273abba573fc9649164944753fc5`, overriding clarification (57 lines) `804eb24536f293427f5877b7358575ff0bca1c5faf23a99ba4156ac9d77c515c`, activated brief, review brief and both ACK records read completely. ACK only permitted the design sandbox and is not an independent PASS.

## R1 — P2: make element-basis roots independent of evidence count

Primary location: `docs/superpowers/specs/2026-10-22-ama-material-provenance-addendum.md:155`. Related locations: lines 55–57, 97, 126, 130, 207.

The schema deliberately gives an element an inherited basis context independently of each evidence row. The required rebind operation may therefore leave an element with `source_context_id=C1` while every evidence row on it uses `C2`. Line 126 says ROWSET v2 includes the exact reached contexts and their complete selections/profiles. However, line 155 defines the **exact** PINNED-element/final context closure as zero-evidence element roots union the context of each expected usage. For this permitted nonempty-evidence graph, that latter recipe produces only `{C2}`; the element's explicit `C1` basis is omitted. The review-specific sentence includes the reviewed element's basis even with evidence, and line 207 again singles out zero-evidence roots, so the broader rule cannot be applied consistently across domains by simple inheritance.

This is an ambiguity in canonical graph completeness, not merely a missing test: an implementer following the exact-union sentence rejects a graph containing C1 as extra, while one following the complete reached-row rule expects C1 and its selections/profiles. Rebinding all evidence is precisely where assuming that some usage carries the element's basis stops working. The existing mixed-P1/P2 and zero-evidence vectors do not exercise that shape.

Correction: explicitly define roots for every domain. Recommended rule: all included `minutes_element.source_context_id` values are basis roots regardless of evidence count, union the expected usage contexts; REVIEW uses the selected element's basis; preserve the existing no-fake-EVIDENCE and complete selection/profile closure rules. Clarify any intentionally different domain scope rather than relying on “zero-evidence” as shorthand. Add a small offline vector with element C1, all evidence C2, and both full context selections/profiles; verify exact membership and that basis-context changes cannot be dropped from the domain that promises to bind it.

Evidence: `task-D-01-provenance-design-review-context-roots.json`, SHA `6644ddf1cd0e70d334cc8d35511b309a9b4919ab1fbfe62889656c6d95dfc3c2`. Three independent set-algebra assertions reproduced `zeroEvidenceRoots ∪ usageRoots={C2}`, `allReachedBasisRoots ∪ usageRoots={C1,C2}`, and missing basis `{C1}`.

Limit of finding: this is not an executed bad final, hash collision, or claim that SQL loses C1. The FK retains C1, generation rowset hashing separately binds its included rows, and regeneration/final gates may prevent particular finalization paths. Those protections do not resolve which exact SourceGraph row membership the new profile's validator accepts. No runtime work or additional table is requested to close R1.

## Review coverage and strengths

The handwritten addendum was read in full, including operation matrix and §4a background editing. Every checker change, all new schema declarations, dictionary/DDL/ER deltas, acceptance/registry/attribute changes, authored fixture/relational/reopen/oracle scripts and preserved initial schema/probe fragments were inspected. Repetitive full generated dictionary/DDL/ER, metadata, archived baseline and raw record bodies were machine-audited with exact semantic/byte accounting; they were not presented as a manual line-by-line reread of unchanged generated material. Every delivered path is accounted in the appendix and offline JSON.

Authority cross-checks included the complete synchronized overall/B plans, phase-D plan, adopted minutes-contract and config-wire adoption documents, complete B07 original/fix1 and config-wire normative proposals, current product-decisions/UX rules, original requirement material rules (§5.3/5.4) and module III generation/edit/source/copy/restore rules, and final prototype `AM_GROUPS`/`amEntries`/`amEditParagraph`. Prototype bg is included in editable content and paragraph editing preserves sources; addendum §4a correctly supports this. Original requirement/prototype/plan files were not changed. Current user-approved guarded first-save and owner source review supersede historical pending-UX wording without activating schema/runtime.

Positive conclusions:

- Three normalized relations plus two nullable FK columns are sufficient for explicit generated/manual selection and exact per-material parsed-document choice. Generated/manual union, nullable legacy state, unique generation input, composite selection PK, reverse indexes, format/hash checks and RESTRICT FKs are consistent across declaration/dictionary/ER/DDL/recorded metadata.
- Evidence has independent origin; ACTION suggestion is not evidence authority. Copied DISCUSSION and mixed historical candidate ambiguity do not require text/candidate scans. Manual origin does not invent a model task/fingerprint. Same physical locator retains distinct occurrence contexts.
- Full adoption/copy, partial replacement, manual text/action edits, evidence rebind, body-only save, restore, sensitivity dual-label selection, directory copy, background prose editing, related-material snapshot changes, historical final reading and clipboard semantics remain in scope. New target IDs invalidate review credit; unchanged physical/file/parser IDs and per-directory input/memberships are preserved.
- Catalog bindings support P1 and P2 for different PDF files in one context. Binding identity, conflicting SHA rejection, unsigned UTF8 order, exact per-document selection, SHA-derived dependency slots and finite unit mappings are explicit. Valid unused page5 remains enumerable; no reliance on preexisting locator rows, newest parser or fabricated source IDs. Unsupported/ambiguous/missing mapping fails the whole catalog.
- Old 15 ProfileKinds, archive envelope/codec/raw bytes and FullRow13 projection are preserved. New rowset-v2/generation-v3/review-v3/final-v3/catalog-v2/root-schema dispatch is explicit; old v1 slot vocabularies are not broadened. Old-chain v3 rebind cannot become final-eligible by reinterpretation: a compatible immutable v3 generate/adopt chain is required, while pending edits remain honestly readable.
- The draft current context/exclusion projection is acyclic: no own fingerprint or own TaskSpec enters the preimage; inherited graph scope excludes body/applied-exclusion usages and requires persisted dependency ancestors. Stored verification still checks real saved fingerprints/config/task/fence chains. Ancestorhood is graph dependency, not UUID/time order; cumulative budgets/cycle/cancellation obligations are inherited from the adopted wire contract.
- Historical retention includes candidates, copies, reviews/finals and zero-evidence roots, original source and parsed-text files, exact archive chains, guarded reads and revoked/withdrawn sources. Importantly, sidecar FK direction does not protect the sidecar itself: addendum:170 explicitly assigns immutable/cleanup protection to D01/D07. No FK-disable, TTL/history purge, source-ACL bypass or provider-original deletion is authorized.
- Lock ordering, bounded discovery then locked revalidation, CAS/fences, clear-final/review invalidation and D01/B07-S2/D03/D04/B08-D05/D06/B04/D07 ownership are explicit. Concurrent unreviewed peer implementation was not imported.

## Actual offline checks and evidence limits

Reviewer evidence runner:
`task-D-01-provenance-design-review-offline.py`, SHA `ddfa0be3007c78b795b61455c3d48d7cfdd3a4224b70eb0ce0f02c8b41782956`.
Result:
`task-D-01-provenance-design-review-offline.json`, SHA `67a33ac618c8a753ac2b82b5408df63f816788538dee6955e0317e468fe95fef`.

Command: `python3 -B /home/tanren/ama/.worktrees/phase-a/.superpowers/sdd/2026-10-21-ama-phase-b/task-D-01-provenance-design-review-offline.py`, executed via WSL in author tree. **387 assertions passed**. Separately, the fully inspected authored pure oracle was executed in memory with all write_text calls redirected into a memory capture; its **250 assertions passed** and all would-be output bytes matched committed evidence. This is a replay of the author oracle, explicitly distinct from independent reviewer assertions.

The pure recorded-metadata comparison block was inspected then executed against captured JSON without its Docker/apply/probe code. Independently, every old metadata record was compared to the original 93-table metadata: no old record changed/vanished. Delta is exactly 3 tables, 18 columns, 19 index-column records, 5 CHECKs, 10 FK-column records. Totals: **96 tables / 727 columns / 302 index definitions / 460 index columns / 114 enforced CHECKs / 206 FKs / 213 FK columns**. Complete type/default/null/collation/engine/index/FK/CHECK-AST semantics match; old declaration semantics remain exact except the planned additive columns/index/FK and explanatory policy suffixes. Generated DDL/dictionary and complete ER have no drift.

Independently encoded all 93 static old-whitelist vectors. Independently encoded the nonempty-exclusion draft: **6,871 bytes**, SHA `015a3a30f9970d41ef3ac45209794c7a4367e494b53a4f314f6cd0f0d3728112`; exact hex matched, no own fingerprint field. This is an acyclicity/projection vector with synthetic content/config hashes, not an archive-valid generation or ACL/runtime proof. The reduced graph oracle sorts a reduced synthetic model and checks sensitivity/occurrences; it is not the full normative SourceGraph wire vector and does not close R1.

The four preserved B00-1.3 sources match BASE byte-for-byte. All 35 inventoried immutable files match BASE, including formal SQL and old metadata/evidence. The final manifest's 58 covered files match their recorded size/SHA; self/report exclusions and final attributes are separately covered by HEAD/inventory custody. Original DDL `ebf1eae51b303d00b2ed35c4fbd5ba4c166813564032875a6ba0fb35ba743c93` and metadata `a6c758d488c490c97bb373f404bc25c5b50db001e7c0dcb82dfcf9a6668f7aa5` remain intact.

All raw records parsed/accounted: 20 mysql records, 26 relational records including the failed first seed, and 7 reopen/catalog records. Both complete metadata-query captures match the saved metadata exactly. Recorded probes show 24 expectations (6 SQL-accepted APP boundaries) and 7 separate-process/new-connection checks. Actual source transcripts show P1/P2 evidence/notes, manual rebind, copy/partial/restore, same-format different-file parser selection, background prose basis, unused page5 and rollback results. Synthetic configs and durable-task fingerprints intentionally are not valid business chains; neither SQL joins nor the tautological synthetic permission predicate are represented as ACL or stored-fingerprint verification.

Recorded fixture identity/resources are internally consistent: `ama-test-b01-28337bd74163`, full ID `2e5c579812838d46a89be4472eaccc1ca1fefd137048d0ea3018cd781588a11e`, pinned MySQL 8.4.6, initial0, native AIO0, loopback44837, 2CPU/1GiB/no extra swap/256 PIDs. This review did not inspect live Docker or contact the database and makes no new assertion about its current running state. Reopen evidence proves recorded process/connection persistence only, not a DB-server restart or API/worker acceptance.

Failures were retained honestly: DECISION seed error3819 followed by another initial-business-zero and corrected CONCLUSION seed; initial manifest-shape KeyError; preserved raw terminal blank line covered by narrow whitespace attribute. Reviewer-only tooling had two setup failures: first Windows UNC write attempt failed before creating a file; first audit compared abbreviated git index lines against a full-index package and was corrected to --full-index. Neither was a product/schema failure or caused source/evidence rewrite.

No Docker/DB/network/service/migration invocation, source/index/HEAD/branch mutation, live ACL/provider calls, child agent or broad ceremonial suite occurred. Only this new report and named reviewer evidence files were written under root SDD.

## Required disposition

Return R1 to the original author for one precise root-set rule and a focused independent-basis/rebound-evidence vector. Preserve all applied SQL and historical original proposal/evidence bytes. Fresh review of that correction is required before declaring this design adopted. Later runtime tasks still owe actual immutable archive retrieval, complete authorized facts, real parser selection/units, stored graph/task verification, copy/rebind/restore/final and cleanup concurrency tests; their absence is not a separate finding against this design-only delivery.

## Complete delivered-path accounting

All entries below passed exact HEAD/inventory size/SHA checks. “source” is the eight current source/design/checker package members; “artifact” accounts for the other 57 raw/history/metadata/attribute paths. Handwritten members were inspected as described above; repetitive generated/raw bodies were machine-audited.

- artifact | .gitattributes | 355 bytes | SHA256 22840b048a8b7968fb828f17863dd9f2903ec3a714489766fbd598f3745ff370
- artifact | artifacts/phase-b/b00-provenance-design/baseline-immutable-files.json | 6204 bytes | SHA256 6ed05ad264938726225119f0d86113f037900e4785d6652652ed25f2fad47841
- artifact | artifacts/phase-b/b00-provenance-design/constraint-probes.json | 4582 bytes | SHA256 f47f3a521d2d3fe9d536e14e798de16d1282c02e59cc68f8d5f62cf28fdae25f
- artifact | artifacts/phase-b/b00-provenance-design/constraint_probes.py | 17953 bytes | SHA256 75110fdb68b077879237e5a14798a259fa01a71afd629e37fc389309962a4ca4
- artifact | artifacts/phase-b/b00-provenance-design/design-validation-final.stderr.txt | 0 bytes | SHA256 e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
- artifact | artifacts/phase-b/b00-provenance-design/design-validation-final.stdout.txt | 184 bytes | SHA256 27d5b473141ec5cae3222d2ea858e272630bc78ed8f14e12ab9aff64f3bae3d3
- artifact | artifacts/phase-b/b00-provenance-design/design-validation-run1.stderr.txt | 306 bytes | SHA256 2d33f96a35c7b2501dbc7142f7bd37df0ed6b49c0628910bdd6244bf31e1094f
- artifact | artifacts/phase-b/b00-provenance-design/design-validation-run1.stdout.txt | 0 bytes | SHA256 e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
- artifact | artifacts/phase-b/b00-provenance-design/design-validation-run2.stderr.txt | 0 bytes | SHA256 e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
- artifact | artifacts/phase-b/b00-provenance-design/design-validation-run2.stdout.txt | 184 bytes | SHA256 27d5b473141ec5cae3222d2ea858e272630bc78ed8f14e12ab9aff64f3bae3d3
- artifact | artifacts/phase-b/b00-provenance-design/design-validation.json | 12860 bytes | SHA256 9cc018523a84b690b2813922e7183b86617f51431fd64032b6ed96f921555ad6
- artifact | artifacts/phase-b/b00-provenance-design/design_validation.py | 8498 bytes | SHA256 5bac615ba7f83ddc471ede99091a93886ecc1820b8ab34998c8f75863be0d350
- artifact | artifacts/phase-b/b00-provenance-design/diff-check.stderr.txt | 0 bytes | SHA256 e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
- artifact | artifacts/phase-b/b00-provenance-design/diff-check.stdout.txt | 0 bytes | SHA256 e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
- artifact | artifacts/phase-b/b00-provenance-design/evidence-safety-check.json | 173 bytes | SHA256 0abfb0826eeeb0e6ffabc48f9b7f59863afd40e0757a24750decd44c52cd7876
- artifact | artifacts/phase-b/b00-provenance-design/final-manifest.json | 11738 bytes | SHA256 42043c464544cd078aa47b116d58bcd38e029c50944a7075fbff6dd33f0e4e1a
- artifact | artifacts/phase-b/b00-provenance-design/fixture-final-selected.json | 1094 bytes | SHA256 8e8c0ae94c98d2a2a741891134079ebb1eb71063094072f030da4c3efae00d08
- artifact | artifacts/phase-b/b00-provenance-design/fixture-initial.json | 1682 bytes | SHA256 3122e04fecf888628daa5c4338562936383701b5a9603003e318976d9164efe1
- artifact | artifacts/phase-b/b00-provenance-design/fixture-start.stderr.txt | 0 bytes | SHA256 e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
- artifact | artifacts/phase-b/b00-provenance-design/fixture-start.stdout.txt | 1904 bytes | SHA256 c3f87b50b10f0c3a8a6a4da3402ff33a10cdfcdf0a1d668c12096dc18553ef70
- artifact | artifacts/phase-b/b00-provenance-design/fixture-wrapper.json | 707 bytes | SHA256 e6621974375416ee9c25f59d9ce1bc21fea0093e0c93fa66315b5ab78c50b803
- artifact | artifacts/phase-b/b00-provenance-design/graph-projection-vector.hex | 3537 bytes | SHA256 f6f4b582071560560580f5a0585407910bcbdf29029a1bdfac10c63263047b72
- artifact | artifacts/phase-b/b00-provenance-design/graph-projection-vector.json | 1670 bytes | SHA256 50152ad3d08be219dbb4f8279d8f33b4fb1a44f6452b80f4ab929b59e49a28aa
- artifact | artifacts/phase-b/b00-provenance-design/historical-projection-vectors.json | 27999 bytes | SHA256 19dc15af6b5c4e836ef07042dcef248c38199da2ad05fc76502fa7e5eafebc0d
- artifact | artifacts/phase-b/b00-provenance-design/metadata-recheck.stderr.txt | 0 bytes | SHA256 e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
- artifact | artifacts/phase-b/b00-provenance-design/metadata-recheck.stdout.txt | 763 bytes | SHA256 b3b3dad90ea43d90c2a27c20ba5d83645c980636c0ce29a5de2777b57fe5b675
- artifact | artifacts/phase-b/b00-provenance-design/metadata-verify.stderr.txt | 0 bytes | SHA256 e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
- artifact | artifacts/phase-b/b00-provenance-design/metadata-verify.stdout.txt | 763 bytes | SHA256 b3b3dad90ea43d90c2a27c20ba5d83645c980636c0ce29a5de2777b57fe5b675
- artifact | artifacts/phase-b/b00-provenance-design/mysql-raw.jsonl | 732467 bytes | SHA256 4c9cb920279b4139263a124fb4fa9dc74909032a43b07047bfa87f2322ad5cf7
- artifact | artifacts/phase-b/b00-provenance-design/probes-addition.py.txt | 11409 bytes | SHA256 fb51d4bb7e0d0788e1050fdea72b6eb0bbc8f15582b3fc4e5a18590aa1305d55
- artifact | artifacts/phase-b/b00-provenance-design/probes-raw.jsonl | 111389 bytes | SHA256 dd5d2272bd291a0e578055bb8ab396b0d8d8c75e1f788e2b5ec6c3402490b980
- artifact | artifacts/phase-b/b00-provenance-design/probes-run1.stderr.txt | 674 bytes | SHA256 a87483397c9ce470ae2feab9fd09a48a9c4c56707e1a6e2fac04ada317fba941
- artifact | artifacts/phase-b/b00-provenance-design/probes-run1.stdout.txt | 0 bytes | SHA256 e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
- artifact | artifacts/phase-b/b00-provenance-design/probes-run2.stderr.txt | 0 bytes | SHA256 e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
- artifact | artifacts/phase-b/b00-provenance-design/probes-run2.stdout.txt | 32 bytes | SHA256 a2ac962a44748ac9c44efbd29267b805282018de14abd7ff843785f02d6072e0
- artifact | artifacts/phase-b/b00-provenance-design/reopen-catalog-probes.json | 10255 bytes | SHA256 f375893e5ca2f150d8cac37ccc379f7176e1f727db81e5a4d1d2e5ada2634e75
- artifact | artifacts/phase-b/b00-provenance-design/reopen-catalog-raw.jsonl | 9776 bytes | SHA256 a7b2d1aa19a69976379e1079f67a52cc3cd1b1eb7d36393ddd0032a7d4688905
- artifact | artifacts/phase-b/b00-provenance-design/reopen-catalog-run1.stderr.txt | 0 bytes | SHA256 e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
- artifact | artifacts/phase-b/b00-provenance-design/reopen-catalog-run1.stdout.txt | 14 bytes | SHA256 15f9623aae67ff837b7d47fe1fc144f4464f90f69587596f6983563cd8b28e67
- artifact | artifacts/phase-b/b00-provenance-design/reopen_and_catalog_probe.py | 5536 bytes | SHA256 6867b373ac9791bc811bca75e56708ceccbf77e4731c48512d5f007672cf4ce0
- artifact | artifacts/phase-b/b00-provenance-design/root-ack-clarification.md | 1114 bytes | SHA256 284ee66b45dbe2b5f89e9d5d6aed4436db999b5baa6a6f1904ae72b74468d7df
- artifact | artifacts/phase-b/b00-provenance-design/root-ack.md | 434 bytes | SHA256 eab2e8a870bb6e8e5196da144f1e0ab34074706cd2e623ee6058143353763248
- artifact | artifacts/phase-b/b00-provenance-design/schema-addition.py.txt | 4031 bytes | SHA256 8df21166b9f2e22e264c06e09ad3c0a4d0b727988ac2994d53d58f99604914e0
- artifact | artifacts/phase-b/b00-provenance-design/seed-committed.json | 197 bytes | SHA256 f3e3d63dcb40ead5e5c1fcf90b37422b1ce1f7393d1eb13af74f3ebc6410c7f7
- artifact | artifacts/phase-b/b00-provenance-design/start_fixture.py | 3434 bytes | SHA256 96da659a642d167e80a18db945be4790111ef2d7d63652a2eda9d8d19bdef695
- artifact | artifacts/phase-b/b00-provenance-design/task-D-01-provenance-design-brief.md | 9413 bytes | SHA256 a855d6970968677489260e220aa4f933bce94abf28745dd037f2ce29be92a03a
- artifact | artifacts/phase-b/b00-provenance-design/task-D-01-provenance-draft-exclusion-vector-summary.json | 503 bytes | SHA256 9dbd12c5f509545468698a71b49722b38c303e20e6abe076f1f8a9dc1bd97be3
- artifact | artifacts/phase-b/b00-provenance-design/task-D-01-provenance-draft-exclusion-vector.hex | 13743 bytes | SHA256 cc90c587d0f30f27a84da6239ad660d3002e711859869dfe7a001eb57909ea73
- artifact | artifacts/phase-b/b00-provenance-design/task-D-01-provenance-draft-exclusion-vector.json | 6951 bytes | SHA256 cf92b5e0066edf40845977684e80de58895f43d25b13ec5361549edf7b02ecde
- artifact | artifacts/phase-b/b00-provenance-design/task-D-01-provenance-proposal-clarification.md | 11535 bytes | SHA256 804eb24536f293427f5877b7358575ff0bca1c5faf23a99ba4156ac9d77c515c
- artifact | artifacts/phase-b/b00-provenance-design/task-D-01-provenance-proposal.md | 32230 bytes | SHA256 f8d6236c8d324630959e72a3f00cc312bf97273abba573fc9649164944753fc5
- artifact | docs/acceptance/b/database-material-provenance-addendum-metadata-evidence.json | 380843 bytes | SHA256 52a7ae6fe8d91192d2ddfa0c9f5cdcdb5ab4b956633805ddb21b0fb4c79739d9
- source | docs/acceptance/b/database-material-provenance-addendum-review.md | 6549 bytes | SHA256 7806c91eb524a436655c88fbfc47f21253ad9a751f60e803c76638e6db37be75
- source | docs/acceptance/b/material-provenance-design-prerequisite.md | 3478 bytes | SHA256 655a560051f9ba0b596efe141596f9ef76e26f465be9f200d5483f612fd278d2
- source | docs/database/ama-data-dictionary.md | 163661 bytes | SHA256 018203216e9c26a7e69a0a1d4bce142e161f6bdd5c167e7b855c332e131b8e30
- source | docs/database/ama-schema-draft.sql | 115260 bytes | SHA256 4c6bd89301ca6316f4469e99db333bc5440fa8dd330a907cfc685d79ff656a33
- artifact | docs/database/design-history/B00-1.3/2026-10-21-ama-database-design.md | 113660 bytes | SHA256 c4ae00740c7d72e2945d0f67a926631bba198c7ecde2a46cd66523293b21e8d4
- artifact | docs/database/design-history/B00-1.3/ama-data-dictionary.md | 156475 bytes | SHA256 7d34063a86573c6812924a27fff6dc5f2b98cc849dabafb16b9487074fac1d8b
- artifact | docs/database/design-history/B00-1.3/ama-schema-draft.sql | 110504 bytes | SHA256 ebf1eae51b303d00b2ed35c4fbd5ba4c166813564032875a6ba0fb35ba743c93
- artifact | docs/database/design-history/B00-1.3/database_design.py | 100351 bytes | SHA256 8a7db792bb23b13d0d79ed3142f8dcebe0762cc4f6aefe6e2b1656fa34f95ebc
- artifact | docs/database/design-history/B00-1.3/manifest.json | 1164 bytes | SHA256 86cbeaa90878bb84ab28e83ff7a943feed59ddd1173884f6219b3490d1cd0cfa
- source | docs/database/migration-register.md | 42747 bytes | SHA256 63154e7c38482db834dae4ba0775d6a3b8b7c26154134784454a9f47e0c61726
- source | docs/superpowers/specs/2026-10-21-ama-database-design.md | 115821 bytes | SHA256 3f4b6a9f8b0d3ba95c08567e5a6b6f4794a1b9302d565561e12374723435f2d3
- source | docs/superpowers/specs/2026-10-22-ama-material-provenance-addendum.md | 46519 bytes | SHA256 95d4df5c1367ee6d9626f8b15c1a80a0e24f6510a49f522be0d685e9477c547a
- source | tools/checks/database_design.py | 105280 bytes | SHA256 b2b1545a96a87921b2c3383de7f064fb42a31fda09d63041068e23287f084b59
