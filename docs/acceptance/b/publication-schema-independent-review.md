# B08 / V013 schema prerequisite — fresh independent SPEC and technical quality review

Date: 2026-10-22. Reviewer: independent delegated reviewer, not the author; no child agents.

**SPEC: PASS. Technical quality: APPROVED for the bounded five-table schema prerequisite and its verification/evidence package. P0: 0; P1: 0; P2: 0; P3: 0. No actionable findings.** No source correction is required. These conclusions permit the coordinator to consider structure-only adoption; they do not approve complete B08, enable business writes, or qualify a company integration or production deployment. Applied/shared SQL remains immutable.

## Exact reviewed range and full coverage

Owner worktree: /home/tanren/ama/.worktrees/phase-b-publication-schema, branch codex/phase-b-publication-schema.

- BASE: 7f061e8a9d55f5b0db92762692edbe61123d400b.
- HEAD: d9428eeb0c696b6d21c6303ffd1e8ac115ff56ce.
- Both owned commits reviewed: preparation 1c8d3ef154933c93efb6dac427d5a6800d59da3f and final d9428eeb0c696b6d21c6303ffd1e8ac115ff56ce. This is the complete owned range, including preparation, rather than HEAD~1 or the external central freeze registration.
- All 95 changed paths, totaling 2,957,128 final bytes, independently match the delivery path map, actual HEAD Git blobs, worktree sizes and SHA-256 values. Source worktree status was clean before and after the read-only audit. No unrelated source, lower SQL, POM, runner, shared fixture helper/default, design or user-plan change appears.
- Consumed the complete 3,033,686-byte full diff, SHA fc73fcceba147a20b421db6c4c3fd4d19f7c65b2ff54d2dbd55b781932e55220. Reconstructed all 94 text target payloads from their hunks, preserving EOF/newline bytes; the one binary class target was checked against its full Git blob and actual bytes.
- Consumed the complete 98,790-byte six-source supplement, SHA 4aa9f7402cbc4ec9f3e4e45e86a5d506ef618f1e98d9d3b869472cef77acd770. Reconstructed all six complete targets; total source bytes are 95,218.
- Path map SHA: 28d4c4e576c577b61fdcd8b196bdb7073190929f9061708fdd0562d8f13edffc. Author handoff SHA: 60cc8d2d93c59a592188d5957cf15f4b5d266a11abb80b2296c9965f0cdd3426. The complete author handoff and binding schema/review briefs were read. The handoff's embedded implementation report is byte-equal to the complete 11,130-byte source report.

The entire six-source delivery was reviewed:

| Source path | Full coverage |
|---|---|
| backend/adapters/src/main/resources/db/migration/V013__publications.sql | 113 lines / 7,661 bytes; all CREATE blocks, explicit index and 18 FK statements |
| backend/integration-tests/src/test/java/ama/db/PublicationSchemaIT.java | 468 lines / 39,056 bytes; all ten tests, setup/history/ACK/fixture guards, seeds, rollback, snapshots and metadata queries |
| backend/integration-tests/src/test/resources/ama/db/publication-schema-b00-1.3.json | 1,052 lines / 26,790 bytes; every header and all row fields, independently derived and compared as complete bytes |
| docs/acceptance/b/publication-schema-preparation.py | 79 lines / 5,269 bytes; independent source guards, lower-parent discovery, oracle/draft generation and immutable-byte assertions |
| docs/acceptance/b/publication-schema-preparation.md | 19 long lines / 5,312 bytes; historical preparation status and first-apply hold |
| docs/acceptance/b/publication-schema-implementation-report.md | 79 lines / 11,130 bytes; complete results, claims, provenance and limits |

Every one of the 89 artifact/attribute/manifest paths was audited. The exact full path inventory is the above SHA-verified delivery map, with no unaccounted path:

| Artifact group | Paths | Audit |
|---|---:|---|
| Top-level attributes/checkpoint checks/checkpoint and final manifests | 4 | Complete bytes, JSON contents, entry coverage and original Git preservation |
| checkpoint-source | 5 | Four complete original source copies plus manifest; each equals the named original preparation-commit blob |
| compiled | 3 | SQL/oracle/class hashes and bytes equal existing actual target/classpath files |
| formal | 10 | Complete DDL/rows/five metadata groups/history/hash snapshots, fixture, ACK and boundary record |
| review | 43 | Original/corrected scripts and candidate, provenance, failures, all logs/XML/native histories/check records and prepared/RED Java |
| unit-xml | 12 | Eleven full RED-run unit XML suites and attributes |
| final-unit-xml | 12 | Eleven full GREEN-run unit XML suites and attributes |

All 31 JSON-named paths were consumed: 30 nonempty JSON documents parsed; the deliberately retained zero-byte initial oracle stdout is correctly treated as empty pre-SQL failure evidence. All 24 XML documents were fully parsed, including testcase/status nodes, properties and system-output content. Large snapshots were compared as complete structured values and DDL strings, rather than relying on truncated printed representations. Several broad exploratory displays were truncated; their missing portions are not credited as human reading. Required source coverage was completed in smaller displays and independent complete-byte/structured comparisons.

Consulted the original phase-B Task B-08, synchronized delivery-plan transitional rules, adopted B00-1.3 dictionary/DDL/checker and actual metadata, B00 generation design/adoption/independent-review records, complete B07 schema implementation and independent review patterns, and external central registration/freeze authority. Both relevant user plans are byte-equal between owner and coordinator trees. The old plan's V008 publication filename is superseded by the central registered V013 ownership, without renumbering old SQL.

## SPEC assessment

Independent original sources retain their adopted hashes:

- Actual full metadata: a6c758d488c490c97bb373f404bc25c5b50db001e7c0dcb82dfcf9a6668f7aa5; 93 tables / 709 columns.
- Full adopted review DDL: ebf1eae51b303d00b2ed35c4fbd5ba4c166813564032875a6ba0fb35ba743c93.

I independently selected the explicitly assigned five table names from the original full actual metadata, excluded only fk_final_confirmation_9, independently extracted the 57 lower CREATE names, and reconstructed the complete oracle including all provenance headers, counts, deferred map and metadata. It equals the delivered oracle byte for byte, SHA 0ae0d6550bee43740dd4633ab4eb05196b3fc04f9d3ea1bbe9cf81e776d557ff. The oracle is not generated from candidate SQL.

| Group | Independent expected = recorded actual |
|---|---:|
| Tables / engine / collation | 5 |
| Columns / order / type / nullability / default / collation / extra | 42 |
| Index-column rows / uniqueness / order / direction / prefix / type | 30 |
| Complete CHECK clauses / ENFORCED | 4 |
| FK-column rows / complete targets / order / update/delete actions | 18, all single-column constraints |

Every complete CREATE block is byte-exact to the adopted DDL. The exact 18 satisfiable ALTER/FK statements equal the adopted ordered subset; all five CREATEs precede every FK. Only final_confirmation.material_snapshot_id -> material_snapshot.id is deferred, named fk_final_confirmation_9, to V017. Its explicit same-name backing index remains at SQL line 77. Both final_result_review physical FKs are present at lines 111 and 113. No index, column or CHECK was removed from the oracle. Every nondeferred parent exists among the actual 57 lower tables or these five new tables.

The file consists of the five assigned CREATE blocks, one required explicit deferred-FK index and 18 FKs. No seed, IF NOT EXISTS, unrelated table, new design field, legacy READY guess, old migration rewrite or guessed forward version is present. Raw READY is only the adopted publication-state vocabulary; no existing business row is inferred or backfilled.

The adopted checker was evaluated in memory only, with no command entry point or verifier invoked. Its complete SQL and dictionary strings equal the existing adopted files byte for byte; all five declarations belong to B-08 / V013. The nullable material pointer and absence of hash-format CHECKs in final_confirmation are adopted design boundaries. Strengthening an already shared/applied migration is neither required nor permitted here.

The historical preparation script correctly insists on exactly eleven lower files. It is not a current-checkout acceptance command once V013 is present. Its guard was not weakened and it was not executed during review.

## Technical quality and physical tests

The harness is appropriately restricted to the owned historical fixture, not a general production migration runner. PublicationSchemaIT lines 71–128 guard the exact selected fixture identity, initial emptiness, pinned MySQL/native-AIO condition, known lower hashes and successful exact history before applying or seeding. Unknown, failed, incomplete and higher histories stop. The frozen source SHA, actual classpath SHA, retained ACK and authorized fixture/lower-version identity are checked before application. cleanDisabled(true) remains set. No reset, repair, outOfOrder, removal or downgrade path was added.

The lower synthetic seed is committed before the checkpoint; individual publication probes use one separate non-autocommit connection. Lines 131–135 roll back each probe and the final connection. Snapshot queries use the separate setup connection; final complete snapshots demonstrate that the temporary probe changes did not persist.

The ten actual GREEN cases test meaningful physical behavior:

- Full five-group metadata equality against the independent oracle, exact table set and complete upgrade/lower preservation.
- All 18 deployed FK paths require native missing-parent 1452, using populated rows; selected referenced parents require 1451. Complete metadata verifies all 18 RESTRICT update/delete rules.
- final_result_review missing parents, duplicate join, historical membership independence and both parent deletion restrictions. Lines 226–230 remove publication/attempt/mapping and result_review_evidence references first, isolating the two membership edges. Lines 236–239 remove membership and prove each parent DELETE succeeds. These assertions avoid attributing another child's rejection to the membership FKs.
- Frozen meeting-revision uniqueness, one publication root per meeting, exact business-key uniqueness, exact provider/document uniqueness, unsigned bounds and required nonnull fields.
- Exact publication, attempt-kind/outcome and mapping-authority vocabularies with lowercase/invalid-state rejection.
- Concrete external_execution parent and a matching operation/execution positive join; long Unicode URL round-trip, including emoji, supplementary characters and combining text.
- Nullable material and raw SQL acceptance boundaries are deliberately reported as physical capabilities, not application permission.

Specific MySQL error codes are checked, rather than accepting arbitrary exceptions. The RED-source and final test-source evolution was inspected: preparation added the other tests and exact ACK fixture guards; the final change isolates membership deletion probes and proves successful deletion after membership removal. The preserved prepared-method payload is an exact substring of checkpoint source. The original RED Java has the same missing-table assertion and retained setup/snapshot helpers, with its historical line numbers preserved.

The fixture wrapper, native-history script and final-check script were inspected in full, including their original corrected-path variants. The wrapper adds only the privately authorized resources/native-AIO argument to the new helper run and refuses removal, including failure cleanup. Its initial root-path error happens before helper import/start. The initial oracle wrong-output-path assertion preserves existing bytes and happens before SQL/draft work. Neither is feature RED.

The 47,969-byte compiled class is an explicitly allowlisted provenance artifact. It equals the existing actual test target class; its valid Java 21 class header, SourceFile and all ten source-test method names were independently checked. Retained compiled SQL/oracle equal actual classpath copies. No recompilation was performed; the review does not claim a new compiler reproduction.

## Raw results and immutable first-apply chronology

The complete RED, GREEN and both compile-only logs were consumed and all lines classified; their schedules and XML agree:

| Recorded execution | Actual evidence |
|---|---|
| Initial oracle output-target assertion | No SQL/fixture action; original empty stdout and explicitly labelled tool-result stderr transcription preserved; not RED |
| Initial fixture-wrapper FileNotFoundError | Before helper import/container creation; not RED |
| Lower sequence from initial zero tables | 001,002,005,006,007,008,009,010,011,011.1,012; eleven successes |
| Genuine missing-five RED | 1 test / 1 assertion failure / 0 errors / 0 skips; expected exact five names versus [] |
| Two compile-only stages | BUILD SUCCESS; no IT execution or IT credit |
| First complete exact PublicationSchemaIT | 10 tests / 0 failures / 0 errors / 0 skips; Maven/runner success |
| Reactor units on each required RED/GREEN runner invocation | Eleven suites, 62 tests; zero failure/error/skip |
| Intended historical013 and production-latest reruns | Zero migrations each; complete before/after snapshot equality |

The unit testcase identities are the same in both runs: 62 distinct unit tests, not 124 distinct tests. RED's existence assertion overlaps GREEN, so it is not counted as an eleventh distinct GREEN case. Both unit sets carry the correct RED or frozen-SHA JVM property respectively. XML records Asia/Shanghai and Java 21.0.12.1. No wildcard/destructive legacy IT, extra company/model/S3 integration cohort, post-ACK failure or unchanged-suite repetition is evidenced. The normal runner's existing S3 unit cases are correctly counted among reactor units.

The frozen SQL is 7,661 bytes, SHA 1de37066869617723a11f05d7423584143a64e0c9ddf8879c7b4fd107de08e3c. Original candidate, shared candidate, active source, retained/current actual classpath resource and central committed frozen copy all match. Independently recomputed line-normalized Flyway CRC32 checksums for all twelve SQL files; every one equals actual history, including V013 **-847352333** and V012 **2101765957**.

Central authority is the separate committed register 0759dc9fb5a20072fb982c91f8ddee70ec175219. Its 13 freeze-manifest entries independently match the actual immutable Git blobs; the corresponding complete SQL matches the source. Precise UTC order:

| Event | Recorded actual UTC |
|---|---|
| Root static/full-read capture | 2026-10-21 21:47:04.731880 |
| Actual central Git freeze commit | 21:48:00; epoch 1792619280 |
| Separate exact-fixture ACK receipt | 21:48:36.027422 |
| Native pre-application server time | 21:48:36.700457 |
| V013 native successful rank 12 | 21:49:39; epoch 1792619379 |
| Final native capture | 21:50:20.827610 |

I checked the strict timestamp ordering and the actual Git commit epoch. The native history rows retain the complete unchanged lower prefix, all success=1. Initial/after/final inspection objects have identical container ID, mount, args, image, loopback and resources. The original JDBC installed_on strings are preserved as Asia/Shanghai rendering (for example 05:49:39); they are not relabelled UTC.

Fixture: ama-test-b01-ee4d94105bb9, full ID 45e74f283bd96e71a876a303ffede564306d77da91b54fc7e73b2ec0a1bef36d, 127.0.0.1:46539; pinned MySQL 8.4.6 image/image ID 869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9; initial0/nativeAIO0; 2 CPU, 1 GiB RAM with memory-swap equal to RAM, 256 PIDs, AutoRemove=false. This is functional evidence only.

## Preservation, manifest and disclosure audit

Independently compared all complete lower-before values to after-RED: every original key/value is equal; only migration_sql_sha256 is added. Independently compared all lower DDL, every row value, all five metadata groups, lower source hashes and the eleven-row successful history prefix against the final snapshot. All 58 lower objects, including history, are preserved. The source CREATE-name set is exactly the actual 57 lower business tables.

The final set contains 63 objects, including history. The only persistent delta is the five table structures and one successful V013 history row. All five new rowsets are empty after rollback. Existing identity/session, meeting/info, storage/file, audit/task, transcript batch/input/revision/segment/speaker/confirmation and all nineteen V012 generation/evidence/review-table sentinels remain. All eleven lower source files equal both exact BASE bytes and coordinator-tree bytes.

After-upgrade, after-tests, private-target-rerun-zero and production-latest-rerun-zero are byte-equal, SHA 866f7bf59731563f5aeb0d52d9b464b0cd451cb1e91df27873a02cd10d65c841. This comparison includes the full rows/DDL/metadata/source-SHA payload, not just counts.

Final manifest SHA 29e361840a97e07d044f24aea65033e5126e983033dfc57f38a062b8033ce01c: all 94 entries have correct size/SHA and exactly cover the other 94 owned paths. Original checkpoint manifest SHA cf9756fb43d412dc68b4a3be04e850d27c0388fd068dd084eeb97b12d084a90f: all 43 entries and the manifest itself equal original preparation Git blobs. Four checkpoint source copies equal their original named Git source blobs. Raw -text applies throughout; separate nested log/XML -whitespace exemptions preserve payloads without silently changing their bytes.

Selected Docker records exclude Config.Env. No credential/env/private-PKI file is delivered; XML property names contain no password/secret/token/credential/private-key property. The retained author scans record both actual private password values checked with zero matches. I did not read private credentials or repeat that exact-value scan, and do not claim independent verification of undisclosed password values.

## Findings, boundaries and remaining gates

**Actionable findings: none (P0–P3 all zero).** No required correction, file:line defect, reproduction or remediation entry exists for the reviewed schema prerequisite. Strengths are the exact adopted DDL/independent-oracle match, explicit deferred index, isolated physical membership probes, immutable first-apply authority, complete lower preservation and honest evidence boundaries.

The following considered behaviors are expressly outside this activated schema-only plan and remain open; none is silently dropped:

- Full B08 service/contracts/API/worker behavior, version freezing and required-field/candidate/stale-confirmation rejection: the activation authorizes only the structural prerequisite.
- Active identity, confirmer/owner/reviewer authority, current directory/source ACL, same-meeting/target/complete review membership, source/hash/profile truth and semantic support: single-column FKs and raw accepted witnesses cannot establish them.
- Stable publicationId+confirmationId encoding, attempt/publication/confirmation/operation/execution consistency, real URL allowlisting and authoritative provider status: application/contract validation remains required. The recorded raw accepted SQL witnesses are not application rejection tests.
- Snapshot/review/membership immutability, safe state transitions, CAS/revisions, idempotency/concurrency/fencing, zero-effect retry proof, UNKNOWN reconciliation and SUCCEEDED read-only behavior: owning transactions and runtime tests remain required.
- Nonnull material pointers: physically possible under the deferred FK, but application B must forbid them until the separate V017 material deployment/validation.
- Both pending first-result-save/evidence-review UX decisions: the schema does not select or activate either interaction.
- Company SSO/model/knowledge/mail/material interfaces and true publish/find-by-business-key capabilities: no real company call or full platform acceptance is evidenced or approved.
- Production DB/storage/native I/O/capacity/HA/fencing/deployment qualification: a local bounded AIO-off MySQL functional fixture does not supply these qualifications.
- Coordinator adoption/integration and cleanup/archive: separate coordinator actions; this reviewer made no source/Git/fixture change and enabled no runtime flow.

Actual review operations were read-only Git/file reads, hashing, full diff reconstruction, JSON/XML/class-format parsing, offline CRC32 computation, in-memory checker string comparison and structured evidence analysis. No test/build/SQL/Docker/service/company/network/model/publish command was run, no historical preparation or evidence-mutating script was invoked, and no suite was rerun. The sole write is this requested report.

Recorded evidence is internally consistent and independently compared. This review is not a new live fixture observation or authentication of unrecorded historical operations. No contradiction to the claimed absence of reset/repair/outOfOrder/other-fixture mutation/company operations was found in the actual delivered code and evidence; unseen past operations cannot be certified from files. Any future SQL defect must go back to the original owner/coordinator for an ordered registered forward correction, never editing this frozen V013.
