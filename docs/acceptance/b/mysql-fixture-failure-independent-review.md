### Spec Compliance

- ❌ Issues found: log capture/read or log persistence failure still permits destructive removal, so the requested startup log evidence can be lost again. `tools/dev/mysql_fixture.py:80`, `:83`, `:89`, `:91`; regression test currently requires that behavior at `tools/checks/tests/test_mysql_fixture.py:145`. See Important I1.
- ✅ The remaining inspected implementation is narrowly scoped: selected-field capture, exact generated-name/label/pinned-reference/loopback ownership proof, private diagnostic files, both-password redaction, bounded new Docker commands, and original-exception preservation. `tools/dev/mysql_fixture.py:15`, `:26`, `:30`, `:37`, `:52`, `:72`, `:136`.
- ⚠️ The existing aggregate gate exercised a loopback HTTP stub despite the brief's no-network condition. This is disclosed in `docs/acceptance/b/mysql-fixture-failure-evidence.md:51` and the author report; the coordinator explicitly accepts it as a recorded process exception within authorized controlled local tests. It is not evidence of an external/company-service request and does not invalidate or add to the reported test counts.
- ⚠️ No live Docker/DB qualification was requested or performed by this reviewer. Docker's real selected-field Go-template behavior is not established by mocked subprocess tests. Original success-path behavior, explicit stop, CLI, pinned image and native-AIO options show no scoped change in the supplied diff; the successful launch-argument/output regression is at `tools/checks/tests/test_mysql_fixture.py:207`.

### Strengths

- Docker itself projects individual fields; Python then reconstructs the metadata/state/binding allowlist rather than persisting full inspect/Config.Env. All serialized diagnostic text and both captured log streams pass through exact generated-secret redaction. Unknown secret inputs omit inspect/log collection and refuse removal. `tools/dev/mysql_fixture.py:15`, `:37`, `:44`, `:49`, `:52`, `:58`, `:81`; tests at `tools/checks/tests/test_mysql_fixture.py:108`, `:192`.
- Cleanup requires the generated full name, expected label, pinned configured image reference and exactly one configured loopback MySQL binding. Invalid/missing proof fails closed, and a failed state-evidence write refuses removal. `tools/dev/mysql_fixture.py:60`, `:72`, `:86`; tests at `tools/checks/tests/test_mysql_fixture.py:148`, `:155`, `:172`, `:201`.
- New Docker subprocesses have a ten-second timeout and capture stderr; diagnostic files are created/chmodded 0600 before writing. Outer handling and best-effort note attachment preserve the original BaseException via bare re-raise. `tools/dev/mysql_fixture.py:26`, `:30`, `:136`; tests at `tools/checks/tests/test_mysql_fixture.py:120`, `:134`, `:183`, `:207`.
- Docker launch remains outside the cleanup handler, preventing launch-conflict failure from removing a pre-existing container. The exact launch arguments and successful public output are asserted. `tools/dev/mysql_fixture.py:112`, `:116`; `tools/checks/tests/test_mysql_fixture.py:212`, `:225`.

### Issues

#### Critical (Must Fix)

- None identified in the scoped diff.

#### Important (Should Fix)

- **I1 — Refuse removal when Docker logs were not preserved.** `tools/dev/mysql_fixture.py:80` reads logs and `:81` writes them, but the shared catch at `:83` only sets `logs = "failed"`. If the subsequent `failure.json` write succeeds, `owned` remains true and `:89`–`:91` remove the container. Thus a transient logs timeout, Docker log-read error, or failure to write `docker.log` can irreversibly destroy the original startup logs—the same evidence-loss class this task is intended to fix. Successful selected-state capture is insufficient: MySQL startup/AIO errors can reside in logs while Docker State.Error is empty. The brief requires state and redacted logs before startup-failure cleanup; inability to capture them should produce a safe refusal, preserving the original failure and container for diagnosis. Gate removal on successful safe log capture/persistence as well as ownership and persisted state. Update `tools/checks/tests/test_mysql_fixture.py:141`–`:145`, which currently expects `removed` after log-read failure. Add focused deterministic cases for log-read failure and specifically `docker.log` persistence failure with otherwise successful `failure.json` writes, asserting no `rm`, a fixed safe failure/refusal outcome, no leaked secrets, and the same original exception/traceback. The present write-failure test at `:172` fails every non-credential write, so it does not isolate this defect.

#### Minor (Nice to Have)

- No new code-quality issue identified beyond I1. Existing aggregate evidence contains deliberate negative-accounting `FAIL:` messages (`artifacts/checks/python.log:30`, `:33`, `:34`, `:37`, `:43`) inside outer tests that finish `ok`; directory summaries at `:47`–`:49` and `:104`–`:106` are both OK. This is pre-existing gate self-test noise, not a new fixture-test failure or new noise introduced by this task. Do not rewrite the raw evidence or expand this task into unrelated gate cleanup. The focused GREEN log is clean.

### Test and Evidence Checks

- Read the supplied three-file full diff once for BASE `0daecad85d348305c11982b5f0a7e2536a47434d` to HEAD `3d7aec112a6c04aff22708c6e1a78769c9432c84`; no Git commands, suites, Docker, DB or network checks were run. No checkout/index/HEAD changes were made. This report is the sole write.
- Named diff-truncation check: the unchanged readiness-loop body is omitted between the two diff hunks. Read only source lines 113–132 to verify the existing readiness command and its interaction with original errors; no broader source crawl. It uses environment references inside the container rather than literal generated secret values in Docker argv (`tools/dev/mysql_fixture.py:119`–`:121`).
- Read the eight existing allowlisted evidence files or their accounting contents and computed SHA-256 against the existing manifest: **8/8 match**. ALLOWLIST.txt matches the manifest path-for-path, with eight unique entries; the two metadata-file hashes also match the author report. Evidence root: `/home/tanren/ama/.worktrees/phase-b-fixture-evidence/artifacts/phase-b/ci-fixture-01`; accounting root: adjacent worktree `artifacts/checks`.
- Existing BASE replay source explicitly loads the stated BASE blob and runs the eight original deterministic regressions with mocked Docker (`artifacts/phase-b/ci-fixture-01/red_replay.py:10`). Saved RED: **8 methods, 9 assertion failures, 1 missing-feature-file error** (`red-safe.log:108`, `:110`), including ownership subcase failures and two compatibility passes. The zero-discovery setup failure is not counted as RED.
- Existing second RED: **12 methods, 1 failure**, specifically diagnostic-note attachment replacing the original error (`red-note.log:15`, `:23`, `:25`). Existing focused GREEN: **12 methods, zero failures/errors/skips** (`green.log:15`, `:17`).
- Existing aggregate evidence agrees across all three JSON reports and raw log: **86 executed = 34 checks + 52 probes, zero failures/skips** (`artifacts/checks/python.json:2`, `python-0.json:2`, `python-1.json:2`; `python.log:47`, `:104`). The focused 12 are included in the 34 and 86; counts are overlapping and are not added together.
- Author-reported py_compile, diff checks and clean worktree were not independently rerun, consistent with the read-only review contract. Hashes verify the supplied existing evidence bytes, not runtime provenance beyond what the evidence itself shows. No additional test result is claimed by this review.

### Assessment

**Task quality: Needs fixes.**

The security boundaries, ownership guard and original-exception handling are well structured and supported by deterministic regressions. I1 remains a task-blocking evidence-retention gap: the failure handler still destroys startup logs when their capture or persistence fails; fix that guard and its currently contrary test before adoption.
