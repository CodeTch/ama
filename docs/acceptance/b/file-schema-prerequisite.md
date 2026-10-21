# B04 V009 file-schema prerequisite

Date: 2026-10-22 (Asia/Shanghai). Scope: six-table structural prerequisite only; independent implementation review pending. **Full B04 remains OPEN.** This can structurally unblock V010 after root review/adoption; it does not enable business writes.

## Provenance and immutable migration

Original dispatch BASE `c1d2e28c274ea11baaeea24421217df8b247da71`; preparation-only checkpoint `45715b6d458088e5b3ec14ab8afb08f96412449c` was not reviewed or accepted as implementation. Exact reviewed root `2a569ebb411049363da96184b0507e6365f5f9fd` was mechanically merged at `dcc5302eb44a5c065b539b6a4ea5f7534a907787`. **Full owned implementation review BASE is `2a569eb..final HEAD`**, covering preparation tests/resources/record as well as final SQL and evidence. The mechanical merge is not a review baseline.

`V009__file_schema.sql` is 7,239 bytes, SHA256 **`79f048987282c82fd7a56d697a02e88c93fa45b3349bd4d43e840dca23ba437d`**, Flyway checksum **`-287948759`**. Root independently read the exact file, compared its six CREATE and nine FK blocks byte-for-byte against adopted B00-1.2, and explicitly ACKed first formal execution in coordinator register commit `b3c00ceebffdd7a15fd0ba3249a091c29f445c2b`. No byte changed after sharing/freezing or application. No review-only raw-DDL sandbox or alternate formal location was used. All earlier SQL remains byte-identical; no low-gap fill, outOfOrder, repair, or guessed version.

V009 creates only storage_backend, file_object, file_reference, upload_session (including display_name/purpose), upload_part and file_cleanup. It adds no permanent parent/future-domain table, credentials, production backend/configuration/purpose row, registry, API, or business service. No IF NOT EXISTS hides an unexpected conflict.

## Real RED and first formal GREEN

New helper-owned fixture `ama-test-b01-204cc11baabc`, `127.0.0.1:45041`, pinned MySQL **8.4.6**; image `mysql@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9`. The initial read-only capture confirms zero tables and no history. Fixture path/label/one loopback binding/JDBC URL are independently guarded. No old or other-owner fixture was touched; this positive fixture was never reset.

The canonical metadata method ran with `-Dama.file.schema.target=008` before V009 was written. Actual lower set 001→002→005→006→007→008 succeeded; the assertion then reported expected six file tables versus `[]`: **1 failure, 0 errors, 0 skips**. Logs/XML and the complete lower-set snapshot are retained.

After freeze ACK and a fresh source-hash/lower-history check, the canonical full FileSchemaMigrationIT ran with explicit target009. First application at **01:51:49.283–01:51:51.981 Asia/Shanghai** upgraded008→009. **8 tests, 0 failures, 0 errors, 0 skips**. Ordered seven-version history is entirely successful. The suite also checks exact lower history/DDL/data preservation, exactly six additions, every frozen SQL resource SHA, full history preservation on rerun, and rerun0.

Independent expectations are the six-table subset of adopted B00-1.2's committed actual MySQL metadata capture, with its source hash retained. Actual first-success metadata equals every saved expected row: **6 tables, 53 columns, 16 index definitions/36 index columns, 7 ENFORCED CHECKs, 9 FK constraints/9 columns**. Types/order/NULL/default/extra/collation, full index uniqueness/order/prefix/type, CHECK clauses and FK targets/actions all match.

Real negative DML checks assert specific MySQL codes: invalid enums/part0→3819; all nine orphan-parent columns→1452; parent delete/backend rename RESTRICT→1451; duplicate physical locations/parts/reference keys→1062; required name/purpose NULL→1048. Case-distinct object keys coexist independently in file/upload/cleanup ledgers; identical locations conflict. Long Unicode/display-name controls, purpose, internal session token and optional provider provenance round-trip without truncation. These are storage constraints, not application authorization or immutable-lifecycle proofs.

## Historical evidence and diagnoses

All evidence under `artifacts/phase-b/b04-schema/` is timestamped or explicitly historical; no capture is falsely presented as current after a later operation. Credentials/helper env files are excluded.

- The first collector attempt stopped at Python import because its root parent index was wrong; no DB access. The original failure log is retained; a focused ancestry check identified and corrected the path.
- The first attempted RED stopped before DB access because Maven's module cwd did not find the checkout-relative helper path. Its log/XML is retained as **configuration error, not RED**. Ancestor-based checkout lookup fixed the guard; the next run is the real missing-table RED above.
- Comparing the external pre-GREEN and first-success DDL initially exposed audit AUTO_INCREMENT2→3, because GREEN setup intentionally inserts one additional test audit row. Only that allocation counter differs; original rows/history and all structural DDL are preserved. The test's exact before/after migration DDL comparison occurs after this seed and passed. Both raw captures and explicit diagnostic are retained; they are not rewritten.
- No formal migration failed, no history was repaired/reset, and no checksum was changed. Preparation compile evidence remains historical and is not counted as behavior testing.

## Verification and remaining gates

Final regression results and evidence inventory are recorded in the implementation report. Existing local/native/ports/S3/API code was not modified. Stage-specific historical IdentityMigrationIT was not run at latest009; its original latest007 assertions were not relaxed. Existing Foundation V006 refusal parameter cases were not rerun or counted. Normal Foundation regression uses a separate new owned fixture so reset-based legacy harnesses cannot erase positive-schema or failed-history evidence.

The schema does not prove same-meeting/typed-owner integrity, safe enabled-purpose authorization, accepted-receipt immutability, restart reconstruction, FINALIZING freeze/replay/abort, orphan invisibility, blob-before-DB sequencing, reference/epoch cleanup, concurrent registration/deletion, transactional subject/outbox writes, full MeetingAccess, S3 common semantics, or deployment/HA/power-loss qualification. These remain the later full B04 and owning-domain deliveries after reviewed V010 and meeting authorization. Production backend initialization, credentials and company contracts remain outside DDL.

## Coordinator adoption

Independent scopedSPECcompliant/qualityApproved0findings. Full2a569eb..fd3c3d8includesprepared45715b6; integratedbothcommits,final1a00ca4,63pathsbyteidenticalbeforethisstatusappend,V009frozenSHAunchanged. See [independentreview](file-schema-independent-review.md). OnlystructuralV010dependencyunlocked,fullB04andbusinesswritesremainOPEN. Noextratest/DBoperationsformechanicaladoption.
