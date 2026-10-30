# AMA Import CAS Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans (recommended). Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the AMA meeting import page start and complete a real local CAS/harness integration flow against the fixed-port `ama-integration-harness`, with regression coverage for configuration and cross-project contracts.

**Architecture:** Keep AMA's existing Vue/Spring/local launcher flow and send CAS/knowledge calls to the externally running harness. Merge the duplicate Spring CAS configuration into one mapping, then exercise the existing real create/upload/draft/import/material state machines through a Playwright integration script. Add harness changes only when a recorded AMA request proves that a simulator route or response is missing.

**Tech Stack:** Java 21/Spring Boot/SnakeYAML/JUnit, Vue 3/TypeScript/Playwright, FastAPI/SQLite/pytest, fixed harness HTTP port 8792 and SMTP port 2525.

**Spec:** `docs/superpowers/specs/2026-10-30-ama-import-cas-integration-design.md`

## Global Constraints

- The harness is a sibling simulator, not the authoritative production implementation.
- Harness HTTP/SMTP fixed ports remain `8792`/`2525`; do not change that design.
- Preserve harness uncommitted parallel-development changes and never reset or clean its worktree.
- Preserve AMA session, CSRF, idempotency, revision-token, owner/meeting scope, and exact file-byte checks.
- Do not claim browser integration success from static builds or unit tests alone.
- Keep scope to the meeting import page, CAS/session entry, and background material reads.

## Review Focus

- Duplicate top-level `cas` mappings: the application must load `application.yml` instead of failing before the server binds; covered by `applicationConfigurationHasNoDuplicateYamlKeys`.
- CAS callback origin/paths: ticket validation must use the exact AMA callback and harness allowlist; covered by the real login flow and harness request log.
- CAS identity mismatch: `dev-owner` must resolve to an AMA local identity after harness authentication; covered by session actor assertion.
- Import acceptance versus processing completion: the page must show the real 202/task acknowledgement and not claim transcription completion; covered by the import script's acceptance/task assertions.
- Material version/permission failures: the picker must preserve provider/version data and show a real failure when a document is unavailable; covered by material API assertions and the picker flow.

---

### Task 1: Remove the duplicate CAS configuration and lock startup with a regression test

**Files:**
- Modify: `backend/api/src/main/resources/application.yml`
- Create: `backend/api/src/test/java/ama/api/ApplicationConfigurationTest.java`

**Interfaces:**
- Produces one top-level `cas` mapping with `enabled`, `origin`, `service-url`, `logout-service-url`, `timeout-ms`, `identity-attribute`, and `session-lifetime-seconds`.

- [ ] **Step 1: Write the failing test**

Add `applicationConfigurationHasNoDuplicateYamlKeys` that loads the classpath `application.yml` with SnakeYAML `LoaderOptions#setAllowDuplicateKeys(false)` and asserts that loading succeeds and the `cas` map contains each required key.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -o -B -ntp -pl backend/api -am -Dtest=ApplicationConfigurationTest -DfailIfNoTests=false test`

Expected: FAIL with SnakeYAML's duplicate-key error for `cas`.

- [ ] **Step 3: Implement the minimal configuration fix**

Merge the two `cas` blocks into the single mapping at the top of `application.yml`. Keep the first block's timeout/default callback semantics, add the second block's `enabled` and identity properties, and use `/auth/cas/logout/callback` as the default logout callback expected by `CasClient`.

- [ ] **Step 4: Run the focused test and API compile**

Run: `./mvnw -o -B -ntp -pl backend/api -am -Dtest=ApplicationConfigurationTest -DfailIfNoTests=false test`

Expected: PASS with zero failures.

- [ ] **Step 5: Run `git diff --check` and inspect only the intended files**

Expected: no whitespace errors; only the YAML and regression test are changed in this task.

---

### Task 2: Add a repeatable real CAS/import/material integration check

**Files:**
- Create: `tools/dev/import_cas_integration.mjs`
- Modify: `web/package.json` only if a script alias is needed
- Modify: `docs/development/local-development.md` with the exact invocation and required fixed harness environment

**Interfaces:**
- Consumes: `AMA_LOCAL_URL` (default `http://127.0.0.1:5173`), `AMA_CAS_ORIGIN` (default `http://127.0.0.1:8792`), `AMA_CAS_USERNAME` (default `dev-owner`), `AMA_CAS_PASSWORD` (default `dev-password`).
- Produces: a JSON report under `.tools/local-app/integration-import-cas/` and a nonzero exit code on any contract or UI failure.

- [ ] **Step 1: Write the failing integration assertions**

Build the script around Playwright's real browser context. Assert, in order:
  1. anonymous `/api/v1/session` is 401;
  2. `/auth/cas/login` redirects to the fixed harness CAS origin with the exact callback service;
  3. the CAS form accepts `dev-owner/dev-password`, redirects through the ticket callback, and `/api/v1/session` returns actor `dev-owner`;
  4. the import page creates one meeting from the real button, opens the import dialog, and keeps the URL on `/meetings`;
  5. selecting a UTF-8 TXT file triggers the real upload API, enables save/start, and saves the draft;
  6. a fresh page load of the same meeting restores the saved file order without another upload;
  7. start processing yields the real import acknowledgement/task ID and routes to processing only after the acknowledgement;
  8. the background-material picker reads at least one library/document page from the configured harness origin and preserves the opaque document version;
  9. logout revokes the AMA session and no page errors occurred.

Use request/response observers and the harness request log to record method/path/status, without fulfilling product responses or fabricating task completion.

- [ ] **Step 2: Run the script before any implementation changes**

Run: `AMA_LOCAL_URL=http://127.0.0.1:5173 AMA_CAS_ORIGIN=http://127.0.0.1:8792 node tools/dev/import_cas_integration.mjs`

Expected: FAIL while the duplicate YAML prevents AMA startup; after Task 1 the remaining failure must identify the first real cross-project contract mismatch.

- [ ] **Step 3: Implement the smallest integration harness compatibility fix only if the failing request proves it is missing**

In `/home/tanren/ama-integration-harness`, add the exact endpoint/response and a focused pytest regression under `backend/tests/`. Preserve fixed ports, CAS allowlist behavior, opaque tokens, real error statuses, and all existing dirty files. Do not add broad fake success routes.

- [ ] **Step 4: Run the focused harness regression**

Run: `PYTHONPATH=.python_deps:backend python3 -m pytest -q backend/tests/<new-or-failing-test>.py`

Expected: PASS with zero failures; existing harness tests remain green.

- [ ] **Step 5: Rerun the real integration script**

Expected: PASS with all nine assertions, a report containing actual status codes and task/material IDs, and no page errors.

---

### Task 3: Full verification and evidence

**Files:**
- Modify: `docs/development/local-development.md` only with verified commands/results
- Create: `.tools/local-app/integration-import-cas/` runtime evidence (ignored local output, not source)

- [ ] **Step 1: Verify AMA frontend and API**

Run: `npm --prefix web run typecheck`, `npm --prefix web run build`, `npm --prefix web run test:unit`, and `./mvnw -o -B -ntp -pl backend/api -am test`.

Expected: each command exits 0; record exact test counts.

- [ ] **Step 2: Verify the harness**

Run: `PYTHONPATH=.python_deps:backend python3 -m pytest -q` and `bash -n scripts/start_latest.sh`.

Expected: all harness tests pass; fixed-port launcher syntax is valid.

- [ ] **Step 3: Verify the runtime prerequisites**

Start harness with its existing fixed-port launcher and AMA with `AMA_CAS_ORIGIN=http://127.0.0.1:8792` (and `AMA_INTEGRATION_KNOWLEDGE_ORIGIN=http://127.0.0.1:8792` for materials). Check both health endpoints and confirm the CAS callback service is the exact registered URL.

- [ ] **Step 4: Run the complete integration check again**

Run the command from Task 2 and inspect the report plus harness logs. Expected: fresh evidence for anonymous rejection, CAS ticket validation, session actor, meeting creation, real upload/save/reload, import acknowledgement, material page/version, logout revocation, and zero page errors.

- [ ] **Step 5: Review the requirement checklist**

Re-read the design and verify fixed ports, simulator boundary, honest acknowledgement semantics, no source resets, and no unrelated feature expansion. Report any unverified item explicitly rather than claiming completion.

