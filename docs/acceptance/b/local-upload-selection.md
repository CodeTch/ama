# Local multipart completion selection correction

2026-10-22. Narrow DB-free correction atop `9572b5a`; author verification only, pending independent review. The [reviewed local infrastructure](local-file-infrastructure.md) remains the baseline. This record does not complete B-04 or qualify production storage.

`FileStoragePort.complete` selects exactly its supplied trusted immutable receipt manifest. The list remains nonempty, unique, ascending and contiguous from 1, session-bound, checked against actual selected bytes, and equal to the expected total and whole SHA-256. Unselected stored parts neither enter the result nor block completion. The public service must still match its client manifest to **all DB-committed receipts** and freeze those rows before storage completion; this change does not admit late business receipts.

The reproduced failure used expected size 3, selected part 1 `abc`, and an independently storage-accepted empty part 2 whose receipt was omitted from the caller's frozen selection. The local journal's additional directory scan rejected that valid selection. Removing only that scan also permits a nonempty unselected part 2 (`xyz`), without changing selected-byte verification or the persisted completion fingerprint. Different valid manifests conflict even if an added empty part leaves the final bytes unchanged. Bad full hashes while open remain retryable; writes after a completion decision reject; abort preserves a winning completion. Unselected artifacts remain protected technical data without a new cleanup policy.

Real loopback mTLS/native-filesystem regressions cover both extra-part forms, selected bytes/result, exact replay, changed selection, terminal writes, abort, and persistence across actual process kills before and after completion. Existing order, duplicate, foreign receipt, expected-size, missing selected part, checksum and durability checks remain exercised. These tests model the storage boundary, not a public API/database transaction.

The first full contract run also exposed an existing interrupted-stream test fixture defect: its `read()` threw only once. The pinned JDK's default bulk read can swallow that exception after a byte, after which the fixture generated unlimited bytes and legitimately reached `SIZE_MISMATCH`. Only the fixture now emits one byte and persistently throws on subsequent reads, including explicit bulk reads. Production error mappings are unchanged. The failed run is retained alongside final evidence in `artifacts/phase-b/b04-selection/`.

| Canonical execution | Tests / failures / errors / skips | Evidence stem |
|---|---|---|
| New selected HTTP cases before fix | 2 / 0 / 2 / 0; both `INVALID_ARGUMENT` at valid completion | `red-contract` |
| Selected process restart and bad-hash retry before fix | 3 / 1 / 2 / 0 | `red-durability` |
| First full contract, before stream fixture correction | 7 / 1 / 0 / 0; interrupted-stream fixture failed | `green-contract-attempt1` |
| Corrected interrupted-stream case | 1 / 0 / 0 / 0 | `green-stream-fixture` |
| Final full local contract | 7 / 0 / 0 / 0 | `green-contract` |
| Full native durability suite | 39 / 0 / 0 / 0 | `green-durability` |

Every row retains the exact log and JUnit XML. The canonical Maven reactor also ran 19 Java unit cases (8 router, 9 deployment configuration, 2 contract client), all passing with no skips; final unit XML is retained. The archive's `manifest.json` records every evidence file's SHA-256 and exact selectors. No S3 parameter group was added or counted as skipped; no unrelated full suite was run.

Evidence is WSL2/ext4 with the pinned Java 21 toolchain, task-owned loopback processes and generated PKI. The existing controlled MySQL fixture environment was sourced only for canonical runner admission; no connection to or SQL/reset/migration/other operation on that fixture, and no public business MySQL integration tests, occurred. The existing reactor unit group includes its own H2 TCP readiness/outage test and a deliberately unavailable loopback MySQL endpoint probe. No signatures, OpenAPI, schema, registry, S3, mount/fsync/mTLS/path rules or automatic cleanup were changed. RHEL/XFS/DRBD, power loss and fencing remain separate qualification gates.
