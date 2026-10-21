# B04 V009 schema prerequisite — independent SPEC and code-quality review

Date: 2026-10-22, Asia/Shanghai. Reviewer: /root/b04_schema_review.

## Reviewed scope and method

Fresh independent review of **2a569ebb411049363da96184b0507e6365f5f9fd..fd3c3d86e1c7998f95ded65704c3453579116d06** in /home/tanren/ama/.worktrees/phase-b-file-schema. This includes preparation commit 45715b6d458088e5b3ec14ab8afb08f96412449c; dcc5302 is the mechanical reviewed-root merge, not the review BASE. Author HEAD and clean working tree were checked directly.

Read the complete schema brief, actual preparation dispatch and coordinator regression rulings; complete author report; original B04 brief/storage preflight; relevant adopted B00-1.2 specification, six-table dictionary, migration register and both independent database reviews; existing local-infrastructure acceptance boundaries and the storage token/location contracts. Applied requesting-code-review/code-reviewer.md and verification-before-completion. No child agents, tests, builds, DB connections, container commands, fixture resets, or source/index/HEAD changes were made by this reviewer. The only written file is this report.

Read the complete six-path source/document supplement in focused passes, recovering sections lost to tool-output truncation. Independently read every one of the 63 owned paths as bytes and compared each with the requested HEAD. Programmatically parsed every evidence JSON/XML and inspected all log lines; this is a full artifact review, not a sample.

## Declined to judge

Each item below remains outside this structural checkpoint for the stated reason; none is implicitly accepted.

- Registry/upload service/API implementation and business-file orphan invisibility: deliberately deferred by the schema brief until meeting authorization and reviewed V010.
- Same-meeting/typed-owner integrity, enabled-purpose authorization, immutable backend bindings/accepted receipts, and legal lifecycle transitions: application/domain invariants; the nine existence FKs cannot establish them.
- Begin/restart/finalize/replay/abort, default-route recovery, blob-before-DB sequencing, concurrent reference/epoch cleanup, and subject/outbox atomicity: later full B04 behavior, not implemented or claimed here.
- Actual S3 common-contract qualification and full local+S3 equivalence: this delivery changes no provider implementation and runs only the existing local regression group.
- Old-checksum, failed-history, unknown-provenance or populated production installation recovery: explicitly excluded from the approved new-owned-empty-fixture path; no recovery authorization follows from this review.
- Historical IdentityMigrationIT at latest009 and the three destructive Foundation V006 refusal parameters: coordinator explicitly excluded them from this positive regression selector; they remain unrun, not passing.
- RHEL/XFS/DRBD, fencing, power loss and deployment/HA qualification: outside the exercised host and evidence boundary.
- Re-review of the entire 85-table design, previously accepted identity/native infrastructure, and unrelated web/Python/OpenAPI behavior: unchanged and outside this owned range.

## SPEC verdict

**Spec compliant — scoped V009 six-table structural prerequisite.**

The sole production change is [V009__file_schema.sql:1](/home/tanren/ama/.worktrees/phase-b-file-schema/backend/adapters/src/main/resources/db/migration/V009__file_schema.sql:1). Its six CREATE blocks and nine FK statements were independently compared byte-for-byte with their adopted B00-1.2 blocks. Only storage_backend, file_object, file_reference, upload_session, upload_part and file_cleanup are added. upload_session retains the adopted trailing display_name LONGTEXT NOT NULL and purpose ASCII CODE NOT NULL fields. No extra domain table/column/version, IF NOT EXISTS, production backend/purpose/configuration/credential seed, or earlier migration rewrite appears.

V009 is **7,239 bytes**, SHA256 **79f048987282c82fd7a56d697a02e88c93fa45b3349bd4d43e840dca23ba437d**. Independently calculated Flyway line CRC32 is **-287948759**, matching every successful captured V009 history row. All six lower SQL files are byte-identical to review BASE, and all seven archived SQL copies match the production resources.

The fixture guard in [FileSchemaMigrationIT.java:47](/home/tanren/ama/.worktrees/phase-b-file-schema/backend/integration-tests/src/test/java/ama/storage/FileSchemaMigrationIT.java:47) checks this checkout's helper file, container-name grammar, helper label, exactly one loopback binding and the exact JDBC URL before opening the test DataSource. First use requires zero tables; reuse rejects failed or nonexact known lower/full version history. Flyway.validate at line88 rejects checksum mismatch before the first seed at line90. The schema suite never calls reset/repair/clean. Guard refusal behavior was reviewed in source; no new live refusal experiment is claimed.

Actual freeze commit b3c00ceebffdd7a15fd0ba3249a091c29f445c2b records this exact SHA at **01:50:30 +0800**, before the first-green log's sole V009 application at **01:51:49.283–01:51:51.981 +0800**. The coordinator dispatch/report provide the explicit execution ACK; the Git freeze record, archived ACK, source bytes and run chronology agree. Initial/lower-RED captures list only the six reviewed lower SQL resources; pre-frozen and later captures include the identical V009 hash.

The scoped completion records preserve full B04 OPEN and do not enable business writers. The FK/metadata evidence is not described as authorization, recovery, cleanup or provider qualification.

## Strengths and code quality

- The schema stays exactly within the adopted ownership and dependency boundaries. Binary/ascii location keys, complete unique target indexes, non-null upload business metadata, enforced enum/part checks and RESTRICT parent references all match the independently adopted structure.
- [FileSchemaMigrationIT.java:125](/home/tanren/ama/.worktrees/phase-b-file-schema/backend/integration-tests/src/test/java/ama/storage/FileSchemaMigrationIT.java:125) compares all five information_schema categories with a separately captured B00 source subset, rather than generating its expected schema from V009.
- [FileSchemaMigrationIT.java:149](/home/tanren/ama/.worktrees/phase-b-file-schema/backend/integration-tests/src/test/java/ama/storage/FileSchemaMigrationIT.java:149) checks exact ordered history, lower rows and SHOW CREATE definitions, exactly six additions, zero rerun, unchanged full history and all seven resource hashes.
- The real-DML cases at lines184,195,209,222,235 and246 verify MySQL error codes for invalid enum/part values, all nine missing-parent columns, deletion/rename restrictions, independent target uniqueness/case sensitivity, duplicate parts/references, required upload name/purpose and long Unicode/control-containing names. SQLException cause/code checking at line284 prevents unrelated failures from satisfying a negative assertion.
- Per-case writes use one explicit transaction and rollback at line111. Persistent lower test identity/meeting/audit seed rows are unmistakably test-only and intentionally form preservation evidence. Existing production storage/API code is unchanged.
- [file-schema-prerequisite.md:25](/home/tanren/ama/.worktrees/phase-b-file-schema/docs/acceptance/b/file-schema-prerequisite.md:25) and the implementation report retain failed attempts and historical capture timing rather than replacing them with successful summaries.

## Full artifact verification

The full package is **1,291,767 bytes**. After its explicit **199-byte explanatory header**, it exactly equals a fresh read-only git diff of the requested BASE..HEAD. Its 63 paths consist of six source/document paths and 57 artifact paths. The **79,168-byte** source supplement, SHA256 **3a7e25a23eae05c07c0b5f800b29c69cb4ba42c83477debf91fd40cf4c52e1d5**, exactly equals all non-artifacts diff blocks; no preparation paths or source changes are omitted.

The complete 57-path artifact inventory was read: 56 files in b04-schema (12 JSON including manifest, 12 XML, 20 logs, seven frozen SQL copies, three Python scripts and two Markdown records), plus the separate historical preparation compile log. SHA256SUMS.json has exactly 55 entries, matching every other file in its archive, with no missing/unlisted archive path. Every entry hash matches. Manifest SHA256 is **ae58d48bd07ec269afc041fc91f0276e09a4300e79f3931d6db71cac889e76aa**. Credential assignments/private-key blocks were not found in the reviewed archive.

The three supporting scripts were read, not executed: capture.py performs guarded live capture, and the two “offline” verifiers also write generated evidence. Reviewer checks instead used independent pure-memory logic, leaving those scripts and evidence untouched. All JSON was parsed, including all seven complete snapshots and their rows/DDL/history/source hashes; all XML attributes were checked against actual testcase/failure/error/skipped elements; all 21 logs including preparation were scanned in full.

### Independent metadata and history

The expected test resource's source SHA **3de8041db8860f1547ff53a94b4ddd79470c4881340111fdafbc3ce5322399ad** matches the actual unchanged adopted database-upload-addendum-metadata-evidence.json. Filtering that independent source to the six owned tables exactly reproduces the complete expected resource:

**6 tables / 53 columns / 16 index definitions / 36 index columns / 7 ENFORCED CHECKs / 9 FK constraints and 9 FK columns.**

Every table engine/collation; column ordinal/type/null/default/extra/collation; index uniqueness/order/direction/prefix/type; CHECK clause/enforcement; FK target/ordinal/update/delete action matches first-success, final-positive and Foundation-final actual captures. This includes automatically created supporting FK indexes. The metadata summary JSON agrees with the actual capture and independent source.

The seven captures describe two owned MySQL8.4.6 fixtures:

|Capture|Fixture/port|Tables|Successful history|
|---|---|---:|---|
|initial-empty|204cc11baabc /45041|0|none|
|lower-set-red|204cc11baabc /45041|21|001,002,005,006,007,008|
|pre-v009-frozen|204cc11baabc /45041|21|same six|
|first-success|204cc11baabc /45041|27|001,002,005,006,007,008,009|
|final-positive|204cc11baabc /45041|27|same seven|
|foundation-initial-empty|2281b6270fc4 /46305|0|none|
|foundation-final-success|2281b6270fc4 /46305|27|same seven|

All actual checksums are consistent: 001=1201435595, 002=1757300424, 005=-1999355348, 006=-1744360556, 007=157746768, 008=-1747387036, 009=-287948759. Every captured history entry is successful. Lower-RED and pre-frozen rows/metadata/DDL are identical. All original lower rows/history survive first application; all lower metadata is unchanged. First/final full metadata and all seven full history rows are identical; every first-success row remains in final-positive.

The sole outer pre-to-first lower SHOW CREATE difference is audit_event AUTO_INCREMENT=2→3. Independent comparison confirms that stripping only this counter leaves exact DDL equality and all original rows persist. This matches the extra GREEN setup audit seed and the retained diagnostic. The actual test snapshots DDL after its seed, before V009, and asserts exact equality including the counter. No migration-source change is attributable to this diagnostic.

### Historical executions and exact accounting

These are **saved author-run results independently audited offline**, not tests rerun by this reviewer.

|Selected final result|Actual testcase elements|Failures/errors/skips|
|---|---:|---|
|FileSchemaMigrationIT explicit009|8|0/0/0|
|Foundation approved normal selector|13|0/0/0|
|FileStorageContractIT local group|7|0/0/0|
|LocalDurabilityIT|39|0/0/0|
|Five backend Java unit XML files|28|0/0/0|

Exactly **95 distinct selected testcase identities** result; the first schema8 exactly overlaps final8 and is not added. Repeated upstream units in Maven verify logs are not added. The local wildcard was independently resolved to precisely FileStorageContractIT and LocalDurabilityIT in this HEAD. All five passing execution logs contain BUILD SUCCESS and no WARN/ERROR lines.

The real RED XML is **1 testcase /1 failure /0 errors /0 skips**, named sixTablesMatchIndependentAdoptedMysqlMetadata, with expected six tables versus an empty list; its log shows actual reviewed lower-set migrations to008 on45041. Earlier helper-path @BeforeAll failure is separately preserved: XML also records **1 failure /0 errors**, with blank testcase name, although the console container summary calls it an error. It is correctly excluded from RED.

The collector import failure and first offline-accounting mismatch are retained and accurately diagnosed. Preparation test-compile reports no tests; it is not added to the count. Historical IdentityMigrationIT and the three V006 refusal parameters have no selected testcase in these results. Java units are not portrayed as globally database-free.

Foundation logs use only46305. Their existing reset-based normal harness and two dynamic test-private009.1 reset probes are unchanged source infrastructure, deliberately isolated from45041; the final Foundation capture contains exactly seven production versions. They are neither an extra production migration allocation nor a failed-history reset. First-green has one V009 apply on45041; final-schema has no migrations to apply. The positive schema fixture's first/final history remains identical.

backend/docs git diff --check is clean. Raw Maven evidence retains upstream trailing spaces; those are original evidence formatting, not a source-quality finding.

## Issues

### Critical (must fix)

None found in the reviewed scope.

### Important (should fix)

None found in the reviewed scope.

### Minor (nice to have)

None identified that warrants a change for this structural checkpoint.

## Assessment and limits

**Code-quality verdict: Approved. Ready to integrate: Yes, for this exact frozen V009 schema-only prerequisite and its supporting tests/records/evidence.**

**SPEC verdict: Spec compliant for the scoped six-table prerequisite. Critical 0 / Important 0 / Minor 0.**

The schema matches the adopted target exactly, the real lower008→009 path and rerun evidence are coherent, and the tests meaningfully exercise real MySQL constraints while preserving the ownership/freeze rules. Coordinator adoption may structurally unlock V010. This report does not close full B04, meeting authorization, registry/API/common-S3 behavior, business writes or production qualification.

Verification limits: no live test/DB/container rerun or observation of later fixture state; snapshot timestamps remain historical. Runtime conclusions are limited to the retained raw author-run evidence. Unknown/old/failed installations still require a separate reviewed recovery plan. No integration, merge, commit or push was performed by this reviewer.

