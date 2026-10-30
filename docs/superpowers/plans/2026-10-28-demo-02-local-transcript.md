# DEMO-02 Local TXT Slice Implementation Plan

> Use executing-plans inline. Continue the existing linked worktree; preserve inherited evidence and all adopted domain guards.

**Goal:** Run ordered TXT upload/import and saved transcript editing/confirmation from the normal local entry.
**Architecture:** Extend tools/dev/local_app.py with owned local mTLS service, immutable processing release and independent existing TranscriptWorkerMain. Use existing FileServiceMain, ConfiguredBackends and processing/transcript configuration; retain the one MySQL and persistent local root. No new storage or task implementation.
**Tech Stack:** Python launcher, Java 21, existing Maven modules, Vue/TypeScript, Playwright.
**Spec:** 2026-10-28-ama-demo-first-adjustment.md R2 sections 4.1–4.4.

- [x] Runtime: browser smoke must first fail on unavailable processing. Extend only local dev assembly and lifecycle. Prove ordered two-file upload/import, RAW, editable saved WORKING and confirmation via genuine APIs, Worker and filesystem. Bad UTF-8 must yield failed task without partial RAW.
- [ ] UI: compare original final rendered import/transcript states with actual screenshots, then minimally align modal/workspace presentation. Preserve per-instance drafts, recovery keys and IME guards.
- [x] Verify: local launcher ownership tests, frontend tests/typecheck/build/API check for changed UI; actual normal stop/restart keeps file and DB results. Record evidence and exact remaining prototype/reliability gaps. No Git publication in this batch.

Review focus: source changes while services are alive must trigger rebuild; backend IDs/PKI and local files survive restart; stop handles every owned process; no file access by outsiders or automatic generation; failed second-file parsing never publishes first-file output. DEMO-01 previously has functional browser evidence only; prototype comparison remains pending.
