# D01 provenance design author report — B00-1.4-proposed

2026-10-22. **Design delivered for fresh independent SPEC and quality review; not self-adopted.** The activated required technical gap has a concrete normalized proposal and review-only validation. No formal migration number, Flyway application, backend/runtime/POM/API/UI/provider enablement or company/model/KB/mail/fencing/production call occurred. No push/main merge. Required complex materials, full copy, partial regeneration, ordinary evidence rebinding, background prose editing and historical final reading remain in scope.

## Full owned change identity

- Worktree `/home/tanren/ama/.worktrees/phase-b-provenance-design`
- Branch `codex/phase-b-provenance-design`
- BASE `91774bd4eeca2c3f3233f0e6733b32c2abef28fc`
- HEAD `214a63d978e4c04c0b3aec1c99d8d0ce3c093d9d`
- Full owned range `91774bd4eeca2c3f3233f0e6733b32c2abef28fc..214a63d978e4c04c0b3aec1c99d8d0ce3c093d9d` includes every preparation commit; no peer tree code imported.
- Final tracked worktree clean. Full-range `git diff --check` exit0. 65 changed paths listed below. No backend path changed.

Commits (newest first):

```
214a63d978e4c04c0b3aec1c99d8d0ce3c093d9d docs: preserve raw fragment terminal whitespace attribute
c7950be0e758c99f188572da364764561dc9a92c docs: propose normalized material provenance and validate review schema
d611d75bd61c4729d4fc61e5c3debedcc03fdedf docs: preserve provenance proposal and baseline manifests
ccbad3e8e0fca86aa038b48989b9e6095bdeba2c docs: preserve B00-1.3 and provenance proposal evidence
```

## Concrete design and alternatives

Primary review target: `docs/superpowers/specs/2026-10-22-ama-material-provenance-addendum.md`; main B00 spec, complete ER, dictionary, review-only DDL and checker are synchronized at96tables/727columns.

Three normalized tables: source_context (explicit GENERATION_INPUT|MANUAL_SELECTION), source_context_material (one exact selected parsed_document per frozen material revision), parsed_document_profile (explicit format and exact immutable archived parser version/SHA). Two nullable legacy-compatible FKs: minutes_element.source_context_id and evidence_link.source_context_id. Each evidence origin is authoritative independently of element/body/ACTION suggestion. Existing directory input IDs resolve their unique generated context; background prose edits create new rows while retaining input/memberships as inherited source basis. Zero-evidence elements remain explicit context roots. Same physical locator may have distinct P1/P2 usage contexts without duplication of physical identity. Raw model JSON, text/candidate scans, latest-parser selection and generic weak polymorphic provenance were rejected.

New catalog bindings permit multiple versions of the same format in one context; exact document sidecar chooses the binding. Bindings sort format then version; dependencies use exact SHA-derived parser slots only under new recognized catalog/root schemas. Complete catalog enumerates all mapped parsed units, including unused valid units; missing/ambiguous selection/profile/mapping fails explicitly. Production format/size/duration/OCR qualification remains pending.

The clarified generation projection has no self fingerprint: current input hashes raw context/complete selections plus exclusion.appliedContextId, while inherited element-set graphs contain only already-persisted graph ancestors and exclude body/applied-exclusion usages. Saved review/final graphs may include independently verified generated digests. Ancestry never means time/UUID ordering. Old15 profile kinds/archive codec/raw bytes and old FullRow13 whitelists remain unchanged; new rowset.v2/generation-input.v3/result-review.v3/final-sources.v3 dispatch explicitly. Unsupported legacy origins stay unavailable; no fabricated READY/backfill/current fallback.

Proposal/raw authority was preserved before edits and sandbox DDL: original151lines SHA f8d6236c8d324630959e72a3f00cc312bf97273abba573fc9649164944753fc5; overriding57line delta SHA804eb24536f293427f5877b7358575ff0bca1c5faf23a99ba4156ac9d77c515c. Root FULLREAD and independently recomputed the6871byte nonempty-exclusion vector digest015a3a30f9970d41ef3ac45209794c7a4367e494b53a4f314f6cd0f0d3728112 before technical ACK. Raw originals/delta/vector/brief and ACK records reside under `artifacts/phase-b/b00-provenance-design/`.

## Actual sandbox and validation evidence

Owned fixture `ama-test-b01-28337bd74163`, full ID `2e5c579812838d46a89be4472eaccc1ca1fefd137048d0ea3018cd781588a11e`. Exact MySQL8.4.6 image digest `sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9`. Initial0 application tables; loopback127.0.0.1:44837; 2CPU/1GiB memory and memory+swap/256pids; private nativeAIO OFF=0. Original helper defaults/sysctl/old fixtures unchanged. Selected initial/final container identity, volume mount, labels/resources/ports/command and DB native UTC/epoch are retained. New bounded fixture remains retained with its committed synthetic seed, no reset/removal.

- Actual full metadata:96 tables,727 columns,302index definitions/460index columns,114enforced CHECKs,206FK constraints/213FK columns. Full column/default/nullability/collation/engine/index/FK/CHECK-AST comparison passed; metadata-only recheck passed without DDL. New DDL SHA4c6bd89301ca6316f4469e99db333bc5440fa8dd330a907cfc685d79ff656a33; metadata SHA52a7ae6fe8d91192d2ddfa0c9f5cdcdb5ab4b956633805ddb21b0fb4c79739d9.
-24 relational probe expectations passed, including6 intentional SQL accepted boundary witnesses, exact mixed P1/P2 evidence/notes, ACTION rebinding independent of suggestion, zero-evidence context roots, duplicate keys/check/FK failures, retention, explicit legacy NULL, unused page5 and provenance preservation under removed-source state.
-7 additional separate-process/new-connection checks passed: two historical candidate associations for the same set, copy/partial/restore exact origins, two different PDF files with P1/P2 in one context, editable background prose retaining P1, and rollback proof. This proves persisted relational recovery across process/connection reopen; it does not claim real API/worker or database-server restart testing.
-250 independent static/projection/catalog checks passed. Exact draft6871bytes/hex/digest reproduced. Reduced synthetic graph vectors show independent sensitivity to both contexts/old input digests, occurrence multiplicity, permutation stability and zero-evidence roots. Static old93table whitelist vectors unchanged after added columns. Complete catalog oracle includes unused valid units and rejects missing/wrong/ambiguous mappings. These are design oracles with synthetic hashes, not valid archive binding, production ACL/parser or B07 runtime acceptance.

Raw query SQL/stdout/stderr and all failed/successful attempts are preserved in `mysql-raw.jsonl`, `probes-raw.jsonl`, `reopen-catalog-raw.jsonl`, per-command stdout/stderr and JSON summaries. Initial synthetic seed failed3819 for author typo DECISION, rolled back automatically; correction to existing CONCLUSION passed without schema change/reset. First static verification had a path-map-versus-list reader error; corrected reader consumed the original unchanged manifest. Full cached diff initially noted an intentional terminal blank line in sealed raw schema-addition.py.txt; final commit adds a per-file whitespace attribute preserving those raw bytes, after which full BASE..HEAD diff-check passed. No warning/error evidence was discarded.

`final-manifest.json` SHA `42043c464544cd078aa47b116d58bcd38e029c50944a7075fbff6dd33f0e4e1a` seals58 source/evidence files and was reverified after commit; it excludes itself/root report to avoid a self-hash cycle. Final .gitattributes whitespace-preservation change is covered by HEAD and the path SHA inventory below. Private known-secret value scan over captured artifacts found0 credential/PKI matches; values never emitted.

## Preserved baseline and remaining gates

B00-1.3 four raw sources were copied and committed first in design-history/B00-1.3 with manifest. Original spec SHA c4ae00740c7d72e2945d0f67a926631bba198c7ecde2a46cd66523293b21e8d4; dictionary7d34063a86573c6812924a27fff6dc5f2b98cc849dabafb16b9487074fac1d8b; DDL ebf1eae51b303d00b2ed35c4fbd5ba4c166813564032875a6ba0fb35ba743c93; checker8a7db792bb23b13d0d79ed3142f8dcebe0762cc4f6aefe6e2b1656fa34f95ebc. Original93-table metadata SHA a6c758d488c490c97bb373f404bc25c5b50db001e7c0dcb82dfcf9a6668f7aa5 remains untouched. All35 inventoried immutable original formalSQL/old artifact/metadata files passed final hash comparison. Formal001..013 including011.1 unchanged.

SQL cannot prove complete snapshot bijection, document/material ownership, same meeting, profile archive authenticity, immutable profile deletion protection, permission, allowed saved version, graph completeness or semantic support. Sidecar deletion is deliberately an accepted SQL boundary: its FK points to document and does not prevent deleting the sidecar. D01/D07 must prohibit it while reachable. D01 owns parser/selection/authority validation; B07S2 versioned graph/config/fingerprint closure; D03 review/rebind; D04 exact copy/restore; B08/D05 final frozen memberships; D06 guarded reads/revocation; B04 all historical file reference/routing; D07 reverse cleanup. All compose with B03/B05 locks (identities before roots, complete nonlocking discovery then locked revalidation, files/tasks/audit/outbox final order), CAS/fences and no external network in transactional callbacks.

Root must independently FULLREAD the full range and dispatch fresh SPEC plus technical/quality review. Formal allocation remains centrally unassigned; no unreviewed peer B04/B07 implementation was imported. Later runtime acceptance must exercise every documented operation including mixed historical inputs, snapshot completeness, archive/config budgets/cycles, real permission revocation, file-retention concurrency, manual/edit/restore review invalidation and historical frozen final verification. This author report neither closes runtime D01 nor grants schema adoption.

## Every changed path and final SHA256 (full BASE..HEAD)

- `.gitattributes` — `22840b048a8b7968fb828f17863dd9f2903ec3a714489766fbd598f3745ff370`
- `artifacts/phase-b/b00-provenance-design/baseline-immutable-files.json` — `6ed05ad264938726225119f0d86113f037900e4785d6652652ed25f2fad47841`
- `artifacts/phase-b/b00-provenance-design/constraint-probes.json` — `f47f3a521d2d3fe9d536e14e798de16d1282c02e59cc68f8d5f62cf28fdae25f`
- `artifacts/phase-b/b00-provenance-design/constraint_probes.py` — `75110fdb68b077879237e5a14798a259fa01a71afd629e37fc389309962a4ca4`
- `artifacts/phase-b/b00-provenance-design/design-validation-final.stderr.txt` — `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`
- `artifacts/phase-b/b00-provenance-design/design-validation-final.stdout.txt` — `27d5b473141ec5cae3222d2ea858e272630bc78ed8f14e12ab9aff64f3bae3d3`
- `artifacts/phase-b/b00-provenance-design/design-validation-run1.stderr.txt` — `2d33f96a35c7b2501dbc7142f7bd37df0ed6b49c0628910bdd6244bf31e1094f`
- `artifacts/phase-b/b00-provenance-design/design-validation-run1.stdout.txt` — `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`
- `artifacts/phase-b/b00-provenance-design/design-validation-run2.stderr.txt` — `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`
- `artifacts/phase-b/b00-provenance-design/design-validation-run2.stdout.txt` — `27d5b473141ec5cae3222d2ea858e272630bc78ed8f14e12ab9aff64f3bae3d3`
- `artifacts/phase-b/b00-provenance-design/design-validation.json` — `9cc018523a84b690b2813922e7183b86617f51431fd64032b6ed96f921555ad6`
- `artifacts/phase-b/b00-provenance-design/design_validation.py` — `5bac615ba7f83ddc471ede99091a93886ecc1820b8ab34998c8f75863be0d350`
- `artifacts/phase-b/b00-provenance-design/diff-check.stderr.txt` — `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`
- `artifacts/phase-b/b00-provenance-design/diff-check.stdout.txt` — `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`
- `artifacts/phase-b/b00-provenance-design/evidence-safety-check.json` — `0abfb0826eeeb0e6ffabc48f9b7f59863afd40e0757a24750decd44c52cd7876`
- `artifacts/phase-b/b00-provenance-design/final-manifest.json` — `42043c464544cd078aa47b116d58bcd38e029c50944a7075fbff6dd33f0e4e1a`
- `artifacts/phase-b/b00-provenance-design/fixture-final-selected.json` — `8e8c0ae94c98d2a2a741891134079ebb1eb71063094072f030da4c3efae00d08`
- `artifacts/phase-b/b00-provenance-design/fixture-initial.json` — `3122e04fecf888628daa5c4338562936383701b5a9603003e318976d9164efe1`
- `artifacts/phase-b/b00-provenance-design/fixture-start.stderr.txt` — `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`
- `artifacts/phase-b/b00-provenance-design/fixture-start.stdout.txt` — `c3f87b50b10f0c3a8a6a4da3402ff33a10cdfcdf0a1d668c12096dc18553ef70`
- `artifacts/phase-b/b00-provenance-design/fixture-wrapper.json` — `e6621974375416ee9c25f59d9ce1bc21fea0093e0c93fa66315b5ab78c50b803`
- `artifacts/phase-b/b00-provenance-design/graph-projection-vector.hex` — `f6f4b582071560560580f5a0585407910bcbdf29029a1bdfac10c63263047b72`
- `artifacts/phase-b/b00-provenance-design/graph-projection-vector.json` — `50152ad3d08be219dbb4f8279d8f33b4fb1a44f6452b80f4ab929b59e49a28aa`
- `artifacts/phase-b/b00-provenance-design/historical-projection-vectors.json` — `19dc15af6b5c4e836ef07042dcef248c38199da2ad05fc76502fa7e5eafebc0d`
- `artifacts/phase-b/b00-provenance-design/metadata-recheck.stderr.txt` — `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`
- `artifacts/phase-b/b00-provenance-design/metadata-recheck.stdout.txt` — `b3b3dad90ea43d90c2a27c20ba5d83645c980636c0ce29a5de2777b57fe5b675`
- `artifacts/phase-b/b00-provenance-design/metadata-verify.stderr.txt` — `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`
- `artifacts/phase-b/b00-provenance-design/metadata-verify.stdout.txt` — `b3b3dad90ea43d90c2a27c20ba5d83645c980636c0ce29a5de2777b57fe5b675`
- `artifacts/phase-b/b00-provenance-design/mysql-raw.jsonl` — `4c9cb920279b4139263a124fb4fa9dc74909032a43b07047bfa87f2322ad5cf7`
- `artifacts/phase-b/b00-provenance-design/probes-addition.py.txt` — `fb51d4bb7e0d0788e1050fdea72b6eb0bbc8f15582b3fc4e5a18590aa1305d55`
- `artifacts/phase-b/b00-provenance-design/probes-raw.jsonl` — `dd5d2272bd291a0e578055bb8ab396b0d8d8c75e1f788e2b5ec6c3402490b980`
- `artifacts/phase-b/b00-provenance-design/probes-run1.stderr.txt` — `a87483397c9ce470ae2feab9fd09a48a9c4c56707e1a6e2fac04ada317fba941`
- `artifacts/phase-b/b00-provenance-design/probes-run1.stdout.txt` — `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`
- `artifacts/phase-b/b00-provenance-design/probes-run2.stderr.txt` — `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`
- `artifacts/phase-b/b00-provenance-design/probes-run2.stdout.txt` — `a2ac962a44748ac9c44efbd29267b805282018de14abd7ff843785f02d6072e0`
- `artifacts/phase-b/b00-provenance-design/reopen-catalog-probes.json` — `f375893e5ca2f150d8cac37ccc379f7176e1f727db81e5a4d1d2e5ada2634e75`
- `artifacts/phase-b/b00-provenance-design/reopen-catalog-raw.jsonl` — `a7b2d1aa19a69976379e1079f67a52cc3cd1b1eb7d36393ddd0032a7d4688905`
- `artifacts/phase-b/b00-provenance-design/reopen-catalog-run1.stderr.txt` — `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`
- `artifacts/phase-b/b00-provenance-design/reopen-catalog-run1.stdout.txt` — `15f9623aae67ff837b7d47fe1fc144f4464f90f69587596f6983563cd8b28e67`
- `artifacts/phase-b/b00-provenance-design/reopen_and_catalog_probe.py` — `6867b373ac9791bc811bca75e56708ceccbf77e4731c48512d5f007672cf4ce0`
- `artifacts/phase-b/b00-provenance-design/root-ack-clarification.md` — `284ee66b45dbe2b5f89e9d5d6aed4436db999b5baa6a6f1904ae72b74468d7df`
- `artifacts/phase-b/b00-provenance-design/root-ack.md` — `eab2e8a870bb6e8e5196da144f1e0ab34074706cd2e623ee6058143353763248`
- `artifacts/phase-b/b00-provenance-design/schema-addition.py.txt` — `8df21166b9f2e22e264c06e09ad3c0a4d0b727988ac2994d53d58f99604914e0`
- `artifacts/phase-b/b00-provenance-design/seed-committed.json` — `f3e3d63dcb40ead5e5c1fcf90b37422b1ce1f7393d1eb13af74f3ebc6410c7f7`
- `artifacts/phase-b/b00-provenance-design/start_fixture.py` — `96da659a642d167e80a18db945be4790111ef2d7d63652a2eda9d8d19bdef695`
- `artifacts/phase-b/b00-provenance-design/task-D-01-provenance-design-brief.md` — `a855d6970968677489260e220aa4f933bce94abf28745dd037f2ce29be92a03a`
- `artifacts/phase-b/b00-provenance-design/task-D-01-provenance-draft-exclusion-vector-summary.json` — `9dbd12c5f509545468698a71b49722b38c303e20e6abe076f1f8a9dc1bd97be3`
- `artifacts/phase-b/b00-provenance-design/task-D-01-provenance-draft-exclusion-vector.hex` — `cc90c587d0f30f27a84da6239ad660d3002e711859869dfe7a001eb57909ea73`
- `artifacts/phase-b/b00-provenance-design/task-D-01-provenance-draft-exclusion-vector.json` — `cf92b5e0066edf40845977684e80de58895f43d25b13ec5361549edf7b02ecde`
- `artifacts/phase-b/b00-provenance-design/task-D-01-provenance-proposal-clarification.md` — `804eb24536f293427f5877b7358575ff0bca1c5faf23a99ba4156ac9d77c515c`
- `artifacts/phase-b/b00-provenance-design/task-D-01-provenance-proposal.md` — `f8d6236c8d324630959e72a3f00cc312bf97273abba573fc9649164944753fc5`
- `docs/acceptance/b/database-material-provenance-addendum-metadata-evidence.json` — `52a7ae6fe8d91192d2ddfa0c9f5cdcdb5ab4b956633805ddb21b0fb4c79739d9`
- `docs/acceptance/b/database-material-provenance-addendum-review.md` — `7806c91eb524a436655c88fbfc47f21253ad9a751f60e803c76638e6db37be75`
- `docs/acceptance/b/material-provenance-design-prerequisite.md` — `655a560051f9ba0b596efe141596f9ef76e26f465be9f200d5483f612fd278d2`
- `docs/database/ama-data-dictionary.md` — `018203216e9c26a7e69a0a1d4bce142e161f6bdd5c167e7b855c332e131b8e30`
- `docs/database/ama-schema-draft.sql` — `4c6bd89301ca6316f4469e99db333bc5440fa8dd330a907cfc685d79ff656a33`
- `docs/database/design-history/B00-1.3/2026-10-21-ama-database-design.md` — `c4ae00740c7d72e2945d0f67a926631bba198c7ecde2a46cd66523293b21e8d4`
- `docs/database/design-history/B00-1.3/ama-data-dictionary.md` — `7d34063a86573c6812924a27fff6dc5f2b98cc849dabafb16b9487074fac1d8b`
- `docs/database/design-history/B00-1.3/ama-schema-draft.sql` — `ebf1eae51b303d00b2ed35c4fbd5ba4c166813564032875a6ba0fb35ba743c93`
- `docs/database/design-history/B00-1.3/database_design.py` — `8a7db792bb23b13d0d79ed3142f8dcebe0762cc4f6aefe6e2b1656fa34f95ebc`
- `docs/database/design-history/B00-1.3/manifest.json` — `86cbeaa90878bb84ab28e83ff7a943feed59ddd1173884f6219b3490d1cd0cfa`
- `docs/database/migration-register.md` — `63154e7c38482db834dae4ba0775d6a3b8b7c26154134784454a9f47e0c61726`
- `docs/superpowers/specs/2026-10-21-ama-database-design.md` — `3f4b6a9f8b0d3ba95c08567e5a6b6f4794a1b9302d565561e12374723435f2d3`
- `docs/superpowers/specs/2026-10-22-ama-material-provenance-addendum.md` — `95d4df5c1367ee6d9626f8b15c1a80a0e24f6510a49f522be0d685e9477c547a`
- `tools/checks/database_design.py` — `b2b1545a96a87921b2c3383de7f064fb42a31fda09d63041068e23287f084b59`
