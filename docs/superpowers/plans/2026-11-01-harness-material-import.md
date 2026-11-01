# Harness material import repair

**Goal:** Import the documents selected in the harness management catalog through AMA, with matching content hashes, exact-version permissions and repeatable fixed-port startup.

**Architecture:** Add an explicitly configured local material simulator to the harness. It authenticates AMA delegations with the retained development identities, serves the harness catalog and reads existing controlled publication documents without rewriting them. All material reads and signed batch proofs use this same catalog. Other controlled knowledge/minutes traffic keeps its existing bridge.

**Execution:** Single agent, all commands from `/home/tanren/ama`, AMA main; preserve the sibling checkout's branch. No worktrees or automatic commits. The user approved the repair proposal in chat.

**Constraints:** HTTP8792/SMTP2525 remain fixed. Retain existing databases, uploaded files, signing identities and failed evidence. This is a development simulator, not company qualification.

- [x] Add failing cross-language canonical vectors and authenticated material read/proof tests, including controls, wrong version, missing document, revoked access, forged delegation and existing publication data.
- [x] Implement the harness material adapter and explicit configuration; persist startup settings and expose configured capability. Preserve ordinary management catalog APIs and controlled bridges.
- [x] Add typed AMA material errors and safe original-request recovery after a definitive version rejection. Keep unknown requests and normal hash validation intact.
- [x] Run harness and relevant AMA suites, deploy through the owned startup process, then run actual CAS/browser multi-document import, refresh and replay checks. Check fixed-port restart and original file hashes.
- [x] Self-review both diffs and record exact validation results here.

Review focus: literal backslash versus control escapes; impersonated provider IDs; permission revocation after listing; mixed old/new catalog batches; old pending keys after version changes.

## Execution record

- Canonical controls: RED observed, native material tests then full harness 53 PASS.
- Original receipt: actual MySQL RED (`MATERIAL_RECEIPT_UNAVAILABLE` instead of terminal rejection), then 11 receipt/authority IT PASS on a retained dedicated schema. Initial stale DB environment and file-service fixture failures are retained in material-repair-it-red*.log; no claim of the entire IT suite passing.
- Frontend: typed error/rejection/draft reset and stale list tests RED→GREEN; final full suite 484 PASS, typecheck/build/API compatibility PASS. Java production modules 854 PASS. Local startup settings 19 PASS.
- Actual CAS/browser: screenshot's three documents added, lost ACK recovered with identical key/body, hashes verified, save/reopen PASS. Full six-service/database/fixed-port harness restart, original receipt replay and three-document readback PASS. Genuine own-document version change → rejection → clear/reload/reselect → one READY readable document PASS.
- Ruling: use explicitly configured local development identities and grants for the native harness catalog, plus read-only access to old controlled publication data; do not rewrite old publication rows or infer permissions from request identity fields. This keeps uploaded files usable and preserves previous fixtures.
- Final review: single-agent self-review as required by AGENTS.md. Reviewed canonical escapes, signature/actor/purpose/resource bindings, exact-version grants, revoked/withdrawn sources, terminal receipt serialization, unknown ACK preservation, stale picker drafts and startup persistence. No outstanding required repair in this task.
- Final evidence: `.tools/local-app/material-repair-final-verification.json`; acceptance: `docs/acceptance/harness-material-import-2026-11-01.md`. Running/source signature 93f87e69130559840461fec2736e9b9ecdf1ea814d11c1f2cb51bc389000dd5b.
