# B07/V012 schema prerequisite — fresh independent SPEC and quality review

Date: 2026-10-22. Reviewer: independent delegated reviewer, no authorship and no child agents.

**SPEC: PASS. QUALITY: APPROVED.** These verdicts cover only the nineteen-table V012 structure prerequisite and its delivered verification/evidence package. **P0: 0; P1: 0; P2: 0; P3: 0. No actionable findings.** No correction or reproduction/remedy entry is required. This does not accept full B07, application behavior, company integration or production qualification.

## Exact reviewed scope and coverage

Owner: `/home/tanren/ama/.worktrees/phase-b-generation-schema`, branch `codex/phase-b-generation-schema`.

- BASE `260b6f84a17c06605ff9f3f4eb811069f9ab099b` → HEAD `234e6e2be6cfa721d52c804e87e4b71a3b92e8b6`.
- All three owned commits reviewed: preparation `e5680284276cab5841424895103c89454ab15265`, checkpoint `b750f3fb1f44d9fe2aa8b421056469da42efbf16`, final `234e6e2be6cfa721d52c804e87e4b71a3b92e8b6`. This is the complete owned range, not HEAD~1.
- All **67 paths: six source/docs/SQL and 61 evidence/manifest/attribute paths** match the supplied path map, actual HEAD Git blobs, worktree sizes and SHA-256 values. Owner status was clean. No unrelated Java, POM, helper, runner, lower SQL or design change is present.
- Fully consumed and parsed the **2,572,121-byte** complete diff, SHA `27442e834c725d6ea6ae79207a444771f328d93795809142d02e06bba9cfb13a`. Reconstructed all 65 text targets, preserving raw EOF/newline bytes, and checked both binary targets against their Git blob hashes; all match actual files. The **160,550-byte** six-source supplement, SHA `5c30254867c2ef91f8a79117f32b141fc2873a4aba1ece043f7ae2fab9810fa1`, reconstructs every complete source byte. Six source files total 154,720 bytes.
- Path-map SHA `c1c72a3a38520e6bb098fa08b6a7f62c1dc07eb010abd0f0cd80511cbb5358af`. Binding schema brief and review brief read; complete author report read, SHA `d378ed7f539454b649db371b2b3eed80d28dbbcc720ac83538d1052a1b145e8b`. Its embedded final implementation record equals the entire source implementation report.

All source files were covered in full:

| Owned path | Coverage |
|---|---|
| `backend/adapters/src/main/resources/db/migration/V012__minutes_evidence.sql` | All 335 lines / 21,260 bytes; every CREATE, index and FK |
| `backend/integration-tests/src/test/java/ama/db/MinutesSchemaIT.java` | All 481 lines / 37,396 bytes; setup/history/fixture guards, all eleven tests, seeds, rollback and snapshot/query helpers |
| `backend/integration-tests/src/test/resources/ama/db/minutes-schema-b00-1.3.json` | All 74,006 bytes parsed; every header and every row/field in all five metadata groups compared independently, with compact complete row inspection |
| `docs/acceptance/b/generation-schema-preparation.py` | All 78 lines / 5,333 bytes; source guards, filtering, parent discovery, exact draft and index generation, immutable-byte checks |
| `docs/acceptance/b/generation-schema-preparation.md` | All 84 lines / 5,442 bytes; historical checkpoint and explicit first-apply hold |
| `docs/acceptance/b/generation-schema-implementation-report.md` | All 11,283 bytes, including limitations, first-apply provenance, actual counts and accepted SQL witnesses |

All 61 evidence paths were hashed and audited by type: top-level attributes/manifests 3; checkpoint-source copies/manifest 6; compiled class/SQL/oracle 3; formal snapshots/ACK/boundaries 10; review scripts/drafts/native histories/logs/XML/records 28; unit XML 11. Complete JSON snapshots were parsed and compared field by field; escaped DDL was compared as full byte strings, not printed as an oversized escaped dump. Complete RED/GREEN and both compile-only logs were read. All 13 XML files were parsed, including every testcase, failure/error/skipped node, properties and system-output schedule. No unseen truncated output is credited as review coverage.

Consulted the adopted B00-1.3 design/addendum and independent design review, relevant dictionary sections and service obligations, actual metadata and full DDL source, lower migration set, and the central committed register/freeze artifacts. Older root S1/B04 runtime work is outside this source range.

## SPEC assessment

The adopted source remains **93 tables / 709 columns**, full DDL SHA `ebf1eae51b303d00b2ed35c4fbd5ba4c166813564032875a6ba0fb35ba743c93`, actual-metadata SHA `a6c758d488c490c97bb373f404bc25c5b50db001e7c0dcb82dfcf9a6668f7aa5`. Independently filtered that actual metadata, using the nineteen assigned tables and excluding only the two named unavailable material FK constraints. Every resulting row equals the dedicated oracle, SHA `d051aaa95aeec4497f38e48190f4f396d7356ea69089196c8617119f66e9f987`:

| Group | Expected = recorded actual |
|---|---:|
| Tables / engine / collation | 19 |
| Columns / order / type / nullable / default / collation / extra | 123 |
| Index columns / uniqueness / sequence / direction / prefix / type | 92 |
| CHECK clauses / ENFORCED | 21 |
| FK columns / targets / order / RESTRICT actions | 45, belonging to 44 constraints |

All nineteen complete CREATE blocks are byte-exact to the adopted DDL; all precede FK additions. The deployed 44 ALTER/FK statements are exactly the adopted ordered satisfiable subset. Independently extracted all 38 lower parent tables from the ten actual lower SQL files. The only deferred constraints are:

- `fk_generation_input_5`: `generation_input.material_snapshot_id` → `material_snapshot.id`, V017. Its explicit backing index is retained at SQL line 245.
- `fk_source_locator_3`: `source_locator.material_revision_id` → `material_revision.id`, V017. Its explicit backing index is retained at SQL line 247.

No column, index or CHECK is removed from the oracle. No future material or `final_result_review` table, new business field, JSON/artifact-kind registry, seed, IF NOT EXISTS, renumbering or lower-SQL rewrite is introduced. Seven directory/suggestion/review tables and the exclusion three-part key match the adopted design. The preparation script guards the independent sources and is correctly documented as a historical ten-lower-file reproducer; its guard must not be weakened after activation.

The inherited locator wording discrepancy is accurately disclosed: general design line 118 says non-applicable fields are schema-rejected, while dictionary line 1063 assigns rejection to the service; adopted SQL line 227 permits extras. Preserving that approved CHECK is correct within this task. Neither report nor test claims an application rejection for these extras.

## Quality and physical-test assessment

`MinutesSchemaIT` lines 70–124 verify the owned fixture, lower classpath hashes, successful exact history and Flyway validation before seeds or V012. Unknown/failed/incomplete/newer histories stop. Before applying V012, the frozen SHA property, retained ACK and actual classpath SQL bytes must match. Lines 126–130 rollback each physical probe and the final connection; no reset/repair/clean path was added. The fixed exact-target harness is appropriately historical, not a generic production upgrader.

The eleven executed tests are meaningful for this scope. They cover exact five-group metadata, upgrade preservation and two zero-reruns, nineteen tables, seven subject/locator kinds, binary-case vocabularies, template 1..8, FULL/ELEMENT shape, numeric bounds, dual sensitivity labels and set uniqueness, Unicode/raw suggestion separation, review target XOR and SOURCE_SUPPORT/ACTION_FIELDS outcome/anchor/hash/required-field shapes. Lines 273–288 derive all **44** FK probes from the independent oracle and exercise populated seed rows, requiring native 1452 for missing parents; selected referenced parents require 1451 on delete. CHECK/duplicate/numeric/null probes check the specific MySQL error codes. Metadata separately verifies every FK's RESTRICT rules and every index-column order.

Four intentional raw SQL acceptances at lines 290–305 demonstrate unknown reviewer identity, cross-meeting target, different evidence target and syntactically valid unverified content hash. They are accurately labelled **accepted boundary witnesses**, not application rejection or semantic/hash correctness proof. Non-null future-material references are likewise physical boundary examples; Application B must forbid them until V017.

The wrapper, native-history capture and final-check scripts were fully inspected as evidence sources. The fixture wrapper narrowly adds native-AIO OFF to the new owned helper run and refuses removal, including failure cleanup. The explicitly allowlisted Python bytecode is compilation provenance: its source-size/header and in-memory code-object comparison match the retained 3,442-byte wrapper source; it is not an unexplained executable export. Retained compiled SQL/oracle/class bytes also match the existing classpath copies without rebuilding them.

## Raw results, provenance and chronology

Recorded fixture identity is consistent throughout: `ama-test-b01-feb4b5365dae`, full ID `b62b9b2b8f2b7bb47db21fa4561c90e3ba325138250975531f5585e16ede460e`, loopback `127.0.0.1:44915`, MySQL **8.4.6**, pinned image/image ID `869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9`, label b01, identical mount/args, initial zero tables and actual native AIO zero.

- RED raw log/XML: all ten lower versions `001,002,005,006,007,008,009,010,011,011.1` succeed; actual assertion at test line 134 is expected nineteen versus `[]`, **1 test / 1 failure / 0 errors / 0 skips**. Earlier shell redirection failure is separately retained as a tool-result transcription before fixture execution, not database stderr or feature RED.
- Both compile logs show successful compilation only. They are not counted as test success.
- Frozen exact full runner: **11 IT / 0 failures / 0 errors / 0 skips**, runner/Maven success. Eleven unit XML suites total **62 tests**, all zero failure/error/skip, and all carry the frozen SHA property. No wildcard/old destructive suite is counted. Raw schedules show one V012 application and the two intended zero-migration checks, with no post-ACK failure or unnecessary unchanged-suite rerun.

Active SQL, initial review-only draft, shared candidate, retained actual classpath resource and root frozen copy all have SHA `10742ca2075fa3430dedf692853e1c106bd4464eaed46d8c8c1aa07859878fb7`; size **21,260**, actual Flyway checksum **2101765957**. Root freeze manifest's ten entries match both actual files and central commit `7b80ae282ac898af5cf0685267eb408ffde11ae5`.

Precise UTC sequence: root static/full-read freeze capture **20:53:07.558007**; actual Git freeze commit **20:53:08**, epoch **1792615988**; explicit ACK **20:53:38.528017**; native before-first012 capture **20:54:09.148051**; rank11 successful V012 **20:55:06**, epoch **1792616106**. Thus committed central registration and ACK precede application. The author table's 20:53:07 freeze time refers to the retained freeze capture; it should not be interpreted as the Git commit second. Native final capture **20:59:24.223082** retains the same fixture and history. Original JDBC `04:55:06` strings and XML `user.timezone=Asia/Shanghai` are preserved and not mistaken for UTC.

## Preservation and manifest audit

Before→RED comparison preserves **every original key and complete value**; RED adds only the migration-source-SHA evidence key. All 39 lower objects' full DDL, rows, five metadata groups, ten-row successful history prefix and SQL hashes are preserved after upgrade. Existing synthetic identity/session/meeting/info/file/backend/audit/task/transcript/batch/segment/speaker/confirmation rows remain. All ten lower SQL files also equal BASE Git bytes; every actual checksum matches the frozen map.

Only nineteen new tables and the successful V012 history row persist. All nineteen table row sets are empty after rollback. Upgrade, after-tests, private-target012-zero and unchanged-production-latest-zero snapshots are byte-equal, SHA **`97e08a0988ab9f058f5bb0e1704ae3b2db1e80425efb18e0500ed8d011f5cde6`**. Native histories before/after/final retain the same entire lower prefix.

Final manifest SHA **`d4a430376c0563a72b1da7c367b211a43b6b978d713c40748ac05867273912fb`**: every one of its **60 payload + six source** size/SHA entries matches, with exact evidence-directory membership. Original **18 checkpoint payloads plus checkpoint manifest** equal checkpoint Git bytes; all five copied checkpoint source/docs match their original paths/commits. Checkpoint manifest SHA remains `b568905ca79e5c403b6618e6accc2a6ff7ca4be3bb4afd8d2ecc06ce1274551a`; old root report remains byte-exact at SHA `61c9c950bd937e500a030ef60e27e5e20b3bd0ad2b98ff7cc9e55e616cc3c198`. Raw `-text` attributes preserve original evidence bytes. No credential env/config file is exported; XML properties contain no sensitive-named credential property. The retained author scan records two actual private password values checked and zero matches; this reviewer did not read private credential files to repeat that check.

## Limits and remaining gates

This review executed only read-only file/Git inspection, parsing, hashing and in-memory analytics. It did not run tests, builds, SQL, Docker, services, company/network calls, Git mutations or cleanup. The sole write is this report. Recorded evidence is internally consistent and independently compared; this is not a new live observation of the fixture or authentication of unrecorded past commands. No claim is made about unseen operations outside the supplied evidence.

Structure-only approval leaves full B07 runtime, active identity/owner/source ACL, exact same-meeting/target/full-source membership, content/hash/profile/anchor/Unicode/coverage semantics, immutable history, adoption/CAS/idempotency/fencing/concurrency and both pending UX decisions OPEN. V013 final membership and V017 material parents remain future migrations. No real company-data, native I/O, capacity, HA or production-compatibility qualification follows from this fresh owned AIO-off fixture. Root adoption is a separate coordinator action; no runtime/business write is enabled by these verdicts.
