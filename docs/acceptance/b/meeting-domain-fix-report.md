# B03 domain review fix 1

B03-R1 (P2) is fixed on the original author's owned branch. The four actual meeting error codes now belong to the declared closed error union, and four real HTTP regression cases resolve the OpenAPI response references before checking the received envelope. No runtime behavior or SQL changed. Independent scoped re-review is pending; this report does not claim review approval or merge acceptance.

## Commit and scope

- Worktree: `/home/tanren/ama/.worktrees/phase-b-meeting-domain`.
- Branch: `codex/phase-b-meeting-domain`.
- Exact reviewed fix range: `9f2da58e2bee76741e5b5aab2c22d83c7b46ab67..68aa0957a3fcb69630fee96075055edb3a3cde42`.
- One fix commit: `68aa0957a3fcb69630fee96075055edb3a3cde42` (`fix(api): include meeting errors in declared response contract`).
- Input review: `task-B-03-domain-review.md`, SHA-256 `07267eebf253bd3bc7862231095e041a8279b6b1ef3b3f449ab19e12a0d1c97b`, read completely before the fix.
- No rebase/import of later root changes, runtime edits, production registrations, new table, SQL changes, company calls, other fixture mutation, reset/repair, push, merge, or child agents.

`contracts/openapi/identity.yaml` was narrowly edited under the explicit fix-round authorization. Its shared `BusinessError.error.code` keeps every existing alternative (`UNAUTHENTICATED`, `FORBIDDEN`, `INVALID_ARGUMENT`, `IDENTITY_UNAVAILABLE`, `IDENTITY_PROVIDER_UNAVAILABLE`) and adds `MEETING_FORBIDDEN`, `MEETING_CONFLICT`, `CATEGORY_DICTIONARY_UNAVAILABLE`, `REQUEST_TOO_LARGE`. The shared 403 response description now includes denied meeting access alongside listener/Origin/CSRF failures. This is a shared union, not a per-status discriminator; extending it also widens the shared generated type used by identity operations. No existing shared failure alternative was removed or renamed.

All meeting 403/409/413/503 response references already reach this shared envelope, so `meetings.yaml` remains byte-identical. The generated TypeScript code union and 403 description were regenerated from the source contract. Runtime `MeetingErrors` and shared identity/session error handlers remain unchanged.

## Meaningful RED and GREEN

The regression uses the existing actual MySQL + HTTP `MeetingRevisionIT` cases. The test helper begins at `contracts/openapi/api.yaml`, resolves the path item, exact method and actual HTTP status response, follows external/local `$ref` into the schema, and checks content type, object/string types, required properties, forbidden extra properties, the declared enum and canonical UUID trace ID. It imports no runtime error constants and contains no expected meeting-code list. The response codes come only from the actual HTTP bodies and are compared with the source schema's enum.

The helper is deliberately limited to the current error-envelope schema keywords; it is not a general JSON Schema validator. Unsupported types fail. This scope is sufficient for the unchanged closed object/string/enum/UUID envelope under review.

| Existing real HTTP case | Actual controlled response | RED before contract edit | GREEN after contract edit |
| --- | --- | --- | --- |
| `participantsCannotWriteAndPlatformAdminCannotRead` | 403 `MEETING_FORBIDDEN` | Enum rejection | Pass |
| `staleRevisionPreservesCurrentValue` | 409 `MEETING_CONFLICT` | Enum rejection | Pass |
| `configuredTransportBudgetRejectsWholeRequestWithoutTruncation` | 413 `REQUEST_TOO_LARGE` | Enum rejection | Pass |
| `unavailableCategoryDictionaryDoesNotBlockEmptyDraft` | 503 `CATEGORY_DICTIONARY_UNAVAILABLE` | Enum rejection | Pass |

Exact selector, identical in both runs:

```text
MeetingRevisionIT#staleRevisionPreservesCurrentValue+participantsCannotWriteAndPlatformAdminCannotRead+unavailableCategoryDictionaryDoesNotBlockEmptyDraft+configuredTransportBudgetRejectsWholeRequestWithoutTruncation
```

Exact Maven command (after sourcing pinned toolchain and the owned fixture environment privately):

```text
./mvnw -B -ntp -pl backend/integration-tests -am -Dtest=NoUnitTestsForContractOnlyFix -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=MeetingRevisionIT#staleRevisionPreservesCurrentValue+participantsCannotWriteAndPlatformAdminCannotRead+unavailableCategoryDictionaryDoesNotBlockEmptyDraft+configuredTransportBudgetRejectsWholeRequestWithoutTruncation -Dfailsafe.failIfNoSpecifiedTests=false verify
```

The explicit unmatched Surefire selector avoids running unrelated unit suites; Failsafe runs exactly the four selected integration cases. XML proves nonzero execution, so Maven's fail-if-none setting is not used as evidence of success.

- RED: 2026-10-21 20:48:22.872126–20:48:46.100299 UTC; Maven exit 1; **4 tests / 4 failures / 0 errors / 0 skipped**. Every failure names the actual code excluded by the old five-value declared enum. The captured HTTP bodies and stack traces are preserved in `red.log` / `red.xml`.
- GREEN: 2026-10-21 20:48:56.967687–20:49:15.319769 UTC; Maven exit 0; **4 tests / 0 failures / 0 errors / 0 skipped**. Actual response bodies are preserved in `green.log` / `green.xml`. The same test-source SHA values appear in both run records; only the source error contract changed between runs.
- `python3 tools/checks/run.py contract OpenApi`: exit 0, OpenAPI lint and external-reference bundle pass.
- `npm --prefix web run generate:api`: exit 0.
- `npm --prefix web run check:api`: exit 0, generated output byte-for-byte current.
- `npm --prefix web run typecheck`: exit 0.
- Source `git diff --check`: pass. All 28 staged blobs were verified equal to worktree bytes before commit; all 24 new evidence paths have `text: unset`.

The earlier **35 domain tests (30 revision + 5 concurrency), 62 unit tests and 8 web tests** remain historical evidence from the original report, not new executions for this fix. No unrelated concurrency/unit/schema suite was rerun.

## Fixture and preservation

Only the approved owned fixture was used:

- Container: `ama-test-b01-936fd5c24ae5`.
- Exact ID: `5b5c1baba667ab43cca1ec76a2e5417ebb26bcbae780ad224cfdb49994f272c8`.
- Port: `127.0.0.1:46085`, database `ama_b01_fixture`.
- Image: `mysql@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9`.
- MySQL 8.4.6; native AIO OFF (the previously reviewed owned local startup condition, not company MySQL evidence).
- Before/after Flyway history is byte-identical: successful versions `001,002,005,006,007,008,009,010` with unchanged checksums. The ordinary test initialization invokes the existing migrator against this already-current schema; no new migration, reset, repair or schema alteration occurs.
- New synthetic identities/sessions/meetings are created by the four existing test cases in each run. Other owners' fixture state is untouched.

Preservation verified from a pre-edit SHA snapshot of the original 79 paths:

- Original report at `/home/tanren/ama/.worktrees/phase-a/.superpowers/sdd/2026-10-21-ama-phase-b/task-B-03-domain-report.md` is unchanged: SHA-256 `3c37268121fb46f2319b4df66ab674f5e4888d902b2c0657faf8e4a106cd622e`.
- All **60 original raw archive paths** remain byte-identical, including the original manifest. Original manifest SHA-256: `ad52ff4b8591a7c342bb15af4493fd937c440e58d858d8d78a67994f5fbee108`.
- Of the original 79 owned paths, 77 remain byte-identical. Only the explicitly necessary `MeetingRevisionIT.java` and `generated.ts` changed. `identity.yaml` is the additionally authorized shared contract edit; `DeclaredErrorResponse.java` is new test-only code.
- All eight migration files match the fix base bytes; individual SHA values are in `verification.json`. All production runtime source remains unchanged.
- New archive: `artifacts/phase-b/b03-domain-fix1/`, separate from the old archive. Its manifest contains 23 entries plus the manifest itself, protected by `* -text`.
- New manifest SHA-256: `84019ae61a68fe2c03fd7960a5f08669f46b9e6bfede6cdd82a30786b74531a5`.
- Known private fixture credential scan over all new archive files: PASS. No credential values are included in this report.

## Full owned path list for this fix

These are the exact 28 repository paths in the commit range above (relative to the owned worktree):

```text
artifacts/phase-b/b03-domain-fix1/.gitattributes
artifacts/phase-b/b03-domain-fix1/baseline.json
artifacts/phase-b/b03-domain-fix1/capture-fix1.py.txt
artifacts/phase-b/b03-domain-fix1/check-api.log
artifacts/phase-b/b03-domain-fix1/check-fix1.py.txt
artifacts/phase-b/b03-domain-fix1/contract-runs.json
artifacts/phase-b/b03-domain-fix1/fixture-ownership.json
artifacts/phase-b/b03-domain-fix1/generate-api.log
artifacts/phase-b/b03-domain-fix1/green-run.json
artifacts/phase-b/b03-domain-fix1/green.log
artifacts/phase-b/b03-domain-fix1/green.xml
artifacts/phase-b/b03-domain-fix1/history-after.txt
artifacts/phase-b/b03-domain-fix1/history-before.txt
artifacts/phase-b/b03-domain-fix1/identity-before.yaml
artifacts/phase-b/b03-domain-fix1/openapi-bundle.yaml
artifacts/phase-b/b03-domain-fix1/openapi.log
artifacts/phase-b/b03-domain-fix1/red-run.json
artifacts/phase-b/b03-domain-fix1/red.log
artifacts/phase-b/b03-domain-fix1/red.xml
artifacts/phase-b/b03-domain-fix1/run-fix1.py.txt
artifacts/phase-b/b03-domain-fix1/seal-fix1.py.txt
artifacts/phase-b/b03-domain-fix1/sha256-manifest.json
artifacts/phase-b/b03-domain-fix1/typecheck.log
artifacts/phase-b/b03-domain-fix1/verification.json
backend/integration-tests/src/test/java/ama/meeting/DeclaredErrorResponse.java
backend/integration-tests/src/test/java/ama/meeting/MeetingRevisionIT.java
contracts/openapi/identity.yaml
web/src/shared/api/generated.ts
```

This report is the additional authorized root SDD output at `/home/tanren/ama/.worktrees/phase-a/.superpowers/sdd/2026-10-21-ama-phase-b/task-B-03-domain-fix1-report.md`; it is not part of the owned feature-branch commit. Private `.tools` runners are ignored; exact runner bodies needed to reproduce the evidence are copied into the new archive.

## Remaining limits

The fix changes only the declared error union/descriptions, generated types and contract assertions. Original runtime/domain guarantees and original limitations are unchanged. Real company identity/sign-in/directory/category qualification, company MySQL qualification, and later phase integration remain prerequisites. The shared union permits multiple error codes across shared responses rather than encoding a strict status-to-code discriminated union, consistent with the existing contract design. Independent review should inspect the exact scoped range and fresh RED/GREEN evidence before acceptance.
