# B03 full meeting domain and remaining B02 MeetingAccess runtime — implementation report

Status: **implementation and local verification complete; ready for root's fresh full-owned spec and quality reviews**. This does not declare independent adoption, full Phase B completion, actual company integration or production deployment qualification.

## Exact ownership and review range

- Worktree: `/home/tanren/ama/.worktrees/phase-b-meeting-domain`
- Branch: `codex/phase-b-meeting-domain`
- Reviewed input BASE: `0d2079cd64e5b61bc5787ca5adfaa782e5cd4a40`
- Final HEAD: `9f2da58e2bee76741e5b5aab2c22d83c7b46ab67`
- Implementation/evidence commit: `aa543859e7b689a2fa11f0909d444048718ea754` — `feat: add revisioned meeting drafts and confirmed rosters`
- Documentation clarification commit: `9f2da58e2bee76741e5b5aab2c22d83c7b46ab67` — `docs: clarify meeting domain test chronology`
- Full review range is **BASE..final HEAD**, never HEAD~1. The second commit only corrects test-chronology wording; it changes no code or raw evidence.
- 79 owned paths: 19 source/contracts/generated-client/acceptance paths plus 60 evidence/archive files. The evidence manifest seals 59 entries, excluding itself. All 79 originally staged paths were byte-identical between the working files and Git index; every manifest entry was independently hashed against both. Final worktree status is clean.
- No child agents, parallel ACL writer, main merge, push, company request or other-owner fixture mutation occurred. This report is written only to the root-supplied SDD destination; it is outside the owned source branch.

The implementation read the task brief, adopted B05 adoption record, original consumer handbook and mandatory fix1 addendum before implementation. It uses the actual B05 interfaces, including explicit final flush and immediate production reference validation. No B05 TEST_* registration or fabricated task reference was copied into production.

## Owned source paths

Contracts/core:

- `backend/contracts/src/main/java/ama/contract/MeetingContracts.java`
- `backend/core/src/main/java/ama/access/MeetingAccess.java`
- `backend/core/src/main/java/ama/meeting/CategoryDictionary.java`
- `backend/core/src/main/java/ama/meeting/MeetingRepository.java`
- `backend/core/src/main/java/ama/meeting/MeetingService.java`
- `backend/core/src/main/java/ama/meeting/RosterService.java`

Adapters/API:

- `backend/adapters/src/main/java/ama/meeting/JdbcMeetingAccess.java`
- `backend/adapters/src/main/java/ama/meeting/JdbcMeetingRepository.java`
- `backend/adapters/src/main/java/ama/meeting/MeetingDigests.java`
- `backend/adapters/src/main/java/ama/meeting/MeetingEventReferences.java`
- `backend/api/src/main/java/ama/api/MeetingConfiguration.java`
- `backend/api/src/main/java/ama/api/MeetingController.java`
- `backend/api/src/main/java/ama/api/MeetingErrors.java`

Tests/contracts/handoff:

- `backend/integration-tests/src/test/java/ama/meeting/MeetingRevisionIT.java`
- `backend/integration-tests/src/test/java/ama/meeting/MeetingConcurrencyIT.java`
- `contracts/openapi/api.yaml`
- `contracts/openapi/meetings.yaml`
- `web/src/shared/api/generated.ts`
- `docs/acceptance/b/meeting-domain-integration.md`

The committed consumer handbook above contains the exact API shape, prelocked access usage, request encoding, production category prerequisite and outbox reference contracts.

## Implemented behavior and API decisions

1. POST `/api/v1/meetings {}` derives ownership only from the existing authenticated shared session/current ACTIVE identity. It creates the MEETING subject before the root, then immutable empty info and empty roster draft. Response is `{id,revision:0,stage:"ENTRY"}`. Actual date is null; creation time is never substituted.
2. PUT `/api/v1/meetings/{id}/info` accepts `expectedRevision`, complete human `name/category/summary/occurredOn`, required exact enterprise `rosterDraftIds`, and optional `externalRosterNames` (default empty). Missing optional text/date means null, not a patch. Enterprise display names come from locked shared identity rows. Unknown fields, owner/role claims and duplicate JSON properties are rejected. No AI suggestion/voice/action-owner state is used.
3. A changed roster creates a new DRAFT only. Effective grants, current confirmed pointer and confirmed person search remain unchanged. A no-op roster value does not create a pending draft merely because information changed.
4. POST `/api/v1/meetings/{id}/roster-confirmations {expectedRevision}` independently confirms the latest saved pending draft; missing name/category/date does not prevent it. Empty roster is legal. It creates a new immutable CONFIRMED copy at the new aggregate revision, rechecks exact ACTIVE enterprise IDs, atomically replaces effective READ grants, advances access_revision/root/fence, and replaces confirmed names in the search projection.
5. Historical DRAFT rows are retained. Pending selection uses the latest roster across **all states**, so the old source DRAFT does not reappear after its new CONFIRMED copy. Later changed drafts retain prior current confirmed access until explicitly confirmed. Confirmed result_id identifies an immutable row with the exact original response revision, avoiding any schema addition.
6. GET `/api/v1/meetings/{id}` provides current valid saved info and confirmed roster. Only the owner gets a pending rosterDraft/rosterDraftId; members get null for those fields. No unsaved values, candidates or other speculative result fields are exposed. No list/search endpoint was added; D02 retains that scope.
7. Owners have object READ/EDIT/MANAGE, current effective enterprise participants READ only. Platform admin roles, historical grants, foreign-meeting roster pointers, forged nonmember grants and legacy meeting_acl cannot authorize. Shared ACTIVE identity is reread even for an old Actor object; roles/Actor.status are not current authority.
8. Ordinary reads require ACTIVE lifecycle and governance, matching the adopted database design's rule that withdrawal/obsolescence and pending states block ordinary recall/search/download. Mutations also reject published/ever-published roots. Historical versions remain intact; no restore/republication or governance administration is implemented.
9. Name and summary have no product character cap. The long-Unicode round-trip uses 80,000 code points per field, including emoji. A separately configurable `meeting.http.max-request-bytes` defaults to 8,388,608 bytes and rejects the whole request with 413; no truncation/key consumption. Jackson's independent string-length cap is disabled. This body budget is not a streaming-memory/capacity qualification.
10. Production non-null category values require `CategoryDictionary`; its default production adapter is unavailable until the actual approved dictionary is supplied. Empty draft/null category remains usable. Only tests provide a controlled local category entry. External names are display-only, never mapped to invented enterprise identities or grants.

## Transaction, capability and provenance proofs

Every production component uses the exact same DataSource instance and B01 TransactionRunner. Domain commands own independent transactions and reject ambient ones. Mutations follow idempotency → complete sorted identities → MEETING subject → meeting root → immutable versions/roster/ACL/projection → subject advance/idempotency completion → audit/event append → **outbox.flush last**. Creation never lazily initializes an existing fence/root. Failed audit or final flush rolls back all preceding effects.

Identity ordering is unsigned UTF-8 bytes, consistent with the shared NO PAD binary ID semantics; it is not Java natural UTF-16 order. The final real SQL-order case passes deliberately shuffled IDs including U+E000 and U+10000. It asserts the literal expected identity-lock order, separately checks actual MySQL `utf8mb4_0900_bin ORDER BY`, and verifies the subject/root/audit/outbox order and final sequence UPDATE. Its actual trace appears in `concurrency-boundary-final.log`.

Affected identities are discovered without locks before the transaction, then locked in sorted order before subject/root. Under the root, the complete current grant/latest-roster set must be covered by the prelocked set. Missing coverage returns 409 and rolls back; no lower-order identity is discovered/locked late. A real gated test reproduces a caller guessing expectedRevision=3 while another writer adds/confirms/removes-to-draft between discovery and locking. The previous implementation incorrectly accepted that future CAS; final code refuses it and retains the current grant/revision.

`MeetingAccess.require(actor,id,action)` is safe standalone and rejects ambient transactions. Composable callers acquire `MeetingAccess.Locked` via `access.lock(actor,id)` during their initial identity/subject/root prelock phase, then reuse `require(locked,action)` inside the same transaction. Handles are issuer/DataSource/actual Spring transaction-holder bound; fabricated, foreign-issuer and expired handles fail. A same-adapter attempt to begin identity prelocking after subject/root acquisition fails. Rechecking a handle reads the already-held root and validates current provenance, so a caller's own governance mutation cannot reuse cached authority. No extra transaction synchronization interferes with B05 final flush.

This supported composition contract does not intercept arbitrary caller SQL: a caller that manually locks a root before calling the initial access prelock is outside the contract, just as B05 cannot intercept arbitrary SQL after flush. Future queue/domain consumers must prelock in their policy and reuse the capability; they may not call standalone services, discover another root or perform network I/O in callbacks. No production worker or provider registration is enabled here.

Idempotency scope SHA uses full four-byte length-prefixed UTF-8 actor/resource/operation/key fields. The request digest is a versioned complete semantic encoding of every typed field, null marker and roster list/value. Malformed surrogate input is rejected instead of collapsing to replacement bytes. Unique insert-or-lock serializes same-key races. Digest conflicts occur only after current identity/object authorization. Rollback does not consume the key. Create/save receipts reference immutable info versions; confirmations reference their new immutable confirmed row. Replays recover original ID/revision and fixed operation status (201 create, 200 save/confirm) and never replay effects or restore old grants. No TTL is introduced.

Production reference validators register only MEETING_INFO/MEETING_ROSTER. They check subject kind, event meaning, null task ID and actual immutable row/root pointer/revision provenance; confirmed events require CONFIRMED state. No dummy task ID or fabricated domain reference is used. Final snapshot contains 127 meetings/127 MEETING subjects, zero durable tasks, zero task-bound events and 248 domain events, including prior successful/negative test runs; counts are not interpreted as production data or capacity evidence.

Search projection updates are in the same transaction: full UUID number_search, current saved name/category/info pointer, current confirmed roster pointer and root revision. Person search is populated only from the currently confirmed roster. Pending/old/external drafts never grant access or enter effective name search; confirmed external display names may enter authorized search without grants. Information saves clear final_confirmation_id without deleting historical confirmation/version rows.

## Final verification: distinct scope and exact commands

All Java commands source `/home/tanren/ama/.worktrees/phase-a/.tools/env.sh` privately. Domain commands additionally source the owned ignored fixture env at `.tools/mysql-fixture/ama-test-b01-936fd5c24ae5/env.sh`. Credentials are not included in reports, commands, committed env files or tool output.

| Final command/check | Executed | Failures/errors/skips | Evidence |
|---|---:|---|---|
| `python3 tools/checks/run.py it MeetingRevisionIT` | 30 | 0/0/0 | revision-final.log/xml |
| `python3 tools/checks/run.py it MeetingConcurrencyIT` | 5 | 0/0/0 | concurrency-boundary-final.log/xml |
| `python3 tools/checks/run.py unit` | 62 across 11 suites | 0/0/0 | unit-final.log and unit-xml/ |
| `python3 tools/checks/run.py web` | 8 | 0/0/0 | web-unit.log/xml |
| `python3 tools/checks/run.py contract OpenApi` | 2 checks | PASS | openapi-final.log |
| `npm --prefix web run check:api` | byte comparison | PASS | generated-api-check.log |
| `npm --prefix web run typecheck` | typecheck | PASS | web-typecheck-final.log |
| Full owned source/contracts/docs/generated diff whitespace check | scoped check | PASS | `git diff --cached --check -- backend contracts docs web` |
| Immutable SQL + raw evidence/index byte seal | 8 SQL / 59 manifest entries / 79 paths | PASS | manifests and final verification tool output |

These are **35 distinct final domain cases**, not a sum of repeated executions. The final concurrency suite was rerun after adding the meaningful BMP/non-BMP assertion; the 5-case earlier passing file is retained but not added again. Canonical integration commands also rerun existing unit tests during Maven verify; those repeated counts are not added to the separate 62-case unit total. The full final production source was unchanged after these passing executions; the final commit after aa54385 changes documentation wording only.

The 30-case suite includes actual HTTP conflict/CAS/draft privacy, empty/null date, long Unicode, production category unavailable, configured transport budget, explicit empty/add/remove confirmation, pending-draft projection stability, exact case/trailing IDs, owner/member/admin behavior, old Actor and replay reauthorization, foreign/malformed grants, legacy ACL exclusion, published/governed states, concurrent create/same-key/body/CAS, original replay revisions, real audit and final-flush rollback/retry, invalid payloads/Unicode, capability binding/phase and real production validator failures. The 5-case suite adds actual REPEATABLE READ/JDBC-gated disable-vs-confirm, revoke-vs-read, concurrent confirm, full SQL/UTF-8 lock order/final flush, and stale-discovery/future-CAS closure.

### RED and setup chronology (not inflated into final coverage)

- `red.xml`: 3 assertion failures caused by a test fixture token with 48 rather than required 43 characters; HTTP401. This is a **setup mistake**, not domain RED. It was corrected using a 32-byte random token encoded as 43 URL-safe characters.
- `red-expanded.xml` and repeated `red-authenticated.xml`: each 10 failures/0 errors/0 skips after that correction, because authenticated creation still had no implemented route (201 expected, error dispatch403). This establishes missing endpoint/domain behavior, not a claim that their deeper conflict assertions were already reached.
- `green-first.xml`: initial 10 cases pass; `expanded.xml`: 21 cases pass. These are overlapping earlier stages, not additional distinct final cases.
- `edge-red.xml`: 4 selected cases, 3 real guard failures plus 1 setup error. The latter mistakenly used a separate DataSource while the app transaction held its root, producing a real lock wait timeout. It was corrected to the same application DataSource and is not counted as a domain RED.
- `edge-red-corrected.xml`: 4 cases, 4 intended failures/0 errors/0 skips: stale cached governance, repeated identity prelock after root, invalid Unicode accepted with 200, and disabled old Actor mismatch returning conflict before access denial. Final guards close all four.
- `revision-final-attempt1.xml`: 25 cases pass before the final 5 coverage additions; final revision-final is 30 pass.
- `concurrency-red.xml`: 5 cases with 1 intended failure, where guessed future CAS plus stale discovery accepted an incomplete removed-identity prelock. Final concurrency files both show 5 pass; the later boundary file is the final submitted suite.
- Initial OpenAPI bundling warned that Unavailable duplicated a response name. The meeting response was renamed MeetingUnavailable; final lint/bundle is warning-free. The historical log remains unchanged.
- Raw Maven logs naturally contain trailing whitespace/blank lines. An unscoped Git diff whitespace check reports those raw archive bytes; they were deliberately preserved, not normalized. Source/contracts/docs/generated checks pass. Archive-local `.gitattributes` sets `-text` to preserve the sealed bytes.

### Interrupted selector, with exact preservation

One broad `Meeting*IT` attempt was noticed to include historical schema tests and stopped **before integration execution**, at API surefire IdentitySecurityTest. This is not counted as a run, a passing test, a domain RED or a DB operation. Complete process output is `full-first.log`; `interrupted-selector-record.md` preserves the observed command/PID/termination:

```text
PID 710745
-Dit.test=MeetingConcurrencyIT,MeetingRevisionIT,MeetingSchemaIT,MeetingSchemaLegacyAclIT
kill -TERM 710745
[INFO] Running ama.api.IdentitySecurityTest
FAIL: subprocess exited 143
```

The attempted follow-up XML copy explicitly reported no matching integration XML. No integration module/failsafe/MySQL fixture setup/reset/refusal was reached. Final Flyway installation timestamps still match the first red.log's initial migration (UTC20:01:54–20:02:05), not the later interrupted invocation. All later integration selectors were exact single class names. No schema reset/repair/drop/removal command was issued.

Unrun: historical schema/refusal/reset ITs, full unrelated B05 worker/recovery/native-I/O/capacity suites, storage protocols, full e2e/visual/UI flows, 100-recording load, RHEL/HA/fencing, real company integration. Existing 62 unit regressions and web8 are included; unrelated destructive/history-target tests were intentionally not run on this retained successful fixture. No claimed count includes those unrun scopes.

## Fixture ownership, migration immutability and retained evidence

Fresh owned fixture:

- Name: `ama-test-b01-936fd5c24ae5`
- Exact ID: `5b5c1baba667ab43cca1ec76a2e5417ebb26bcbae780ad224cfdb49994f272c8`
- Label: `ama.test-fixture=b01` (also Docker Desktop's Ubuntu-24.04 label)
- Image/reference ID: `mysql@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9`
- Binding: only `127.0.0.1:46085 -> 3306/tcp`
- Owned anonymous volume: `45a4b88483cec5cbc154d766c728e56057b40a42ef025e2d597425280d9e100a`, mounted read-write at `/var/lib/mysql`; exact source path is in fixture-ownership.json.
- Exact args: `--character-set-server=utf8mb4`, `--collation-server=utf8mb4_0900_ai_ci`, `--default-time-zone=+00:00`, `--innodb-use-native-aio=OFF`.
- Before migration: MySQL8.4.6, actual `@@innodb_use_native_aio=0`, timezone+00:00, **0 tables**. Captured in initial-empty.txt before first Flyway invocation.
- The authorized option is added only by the local owned wrapper, archived as owned-fixture-wrapper.py.txt. Shared helper/profile/sysctl is unchanged. Failed fixture cleanup is disabled in the wrapper; no removal happened. No other fixture was reset/repaired/removed/mutated.

All eight lower migrations were applied once in order 001→002→005→006→007→008→009→010, remain success1, and their final bytes match both the pre-run snapshot and BASE Git objects. Final schema has31 tables; complete selected information_schema columns/indexes/CHECKs/FKs and Flyway history are captured in sealed-metadata-history.txt (an earlier raw metadata snapshot is retained separately).

| Version | Flyway checksum | Unchanged SHA-256 |
|---|---:|---|
|001|1201435595|1726d9db3eb45d0fe86314dd41ad52d5f2332e3f16995cc246f3c3c532208335|
|002|1757300424|68b0d478504a554344cb12dfa8ef79c62da5c6e3455317791762fca25e70f7d0|
|005|-1999355348|55cfdc4aabf169c82f30303ac4d03dce7f24b3c6af5b4cc3ebd0089ae7e7dda7|
|006|-1744360556|f36b7ab2e8d32cf5de6eadcd86524ab269dce98f7906b808eff1a0e39325178f|
|007|157746768|859970b83fc5623b18340c2de793ea35429cc3c0da5cbd1365b4763a5a64780d|
|008|-1747387036|fa44424aa1ff32e049929d45865fe7133c2548fc9ea451f3350cd8984c118ce3|
|009|-287948759|79f048987282c82fd7a56d697a02e88c93fa45b3349bd4d43e840dca23ba437d|
|010|-1187397378|03e89cc8bd58d5a6aa0d4ab031b8fc05a4f7d2450623df147cd84d05b68977db|

No SQL/migration/version registration was needed. V008 is untouched. The final-flush rollback test temporarily sets the owned fixture's outbox sequence to Long.MAX_VALUE so the actual MySQL final sequence UPDATE fails its positive constraint after an event insert; the transaction rolls back, and the test restores the exact prior sequence value in finally. This is an owned functional fault injection, not a reset/history repair. Other negative cases intentionally retain synthetic malformed grants/pointers and legacy ACL entries; the fixture is not a clean company dataset or intended for unrelated migration tests.

Evidence is an explicit allowlist: owned container ID/image/labels/mount/loopback/args; initial emptiness/runtime variables; migration hashes/history/metadata; command logs; fresh JUnit XML; type/contract outputs; wrapper source; test-counts and SHA manifests. Full Docker Config.Env, credentials, sessions, real company data and private env files are excluded. The known fixture root/user passwords were scanned against every evidence file without printing them; result PASS. All raw historical/failed records remain preserved.

## Remaining gates and concerns

- Root's fresh independent **full-owned BASE..HEAD** spec and quality reviews must precede adoption. No known implementation blocker remains from this local verification, but adoption is not self-approved.
- Actual company SSO issuance/protocol, directory/role/disable propagation evidence, approved category dictionary, external-person identity mapping, production security approval and rollout remain pending. No production test-login or bypass was introduced.
- B02 runtime object authorization is implemented here; real company B02 qualification remains open. B03 domain implementation is complete for review; later result/publication/list owners retain B06–B09/D02 integration scope.
- Callback consumers must obey the documented initial prelock/capability and B05 final-flush order. The adapter cannot police arbitrary caller SQL or networking; no new production worker consumer is enabled.
- Native-AIO-off MySQL is functional evidence only, not native I/O/HA/capacity/fencing evidence. The configurable request budget does not qualify high-load memory behavior.
- No schema change, cleanup, deployment, production/company request, push or main merge is included.
