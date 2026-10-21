**I1 — Refuse removal when Docker logs were not preserved — ADDRESSED.** In `tools/dev/mysql_fixture.py:90`, removal now requires both `owned` and `outcome["logs"] == "captured"`. That outcome is assigned only after the bounded log command returns, both streams are redacted, and `private_write(... / "docker.log", ...)` completes (`:80`–`:82`). A log-read or log-write exception sets the fixed `failed` outcome (`:83`–`:84`), leaving cleanup at its initial `refused` value (`:35`) and skipping `rm` (`:90`–`:92`). The preceding state save must also succeed, or the function returns before removal (`:85`–`:88`). Exact-name/label/pinned-image/loopback ownership proof remains required (`:60`–`:73`).

### Finding verification

- **Log-read failure retains the container and original error — ADDRESSED.** `tools/checks/tests/test_mysql_fixture.py:141`–`:151` injects a timeout containing both synthetic credentials; the assertions require original exception identity through `assert_original()` (`:93`–`:95`), the originating `command` traceback frame, `inspect=captured`, `logs=failed`, `cleanup=refused`, no `rm` event, and no credentials in diagnostics/stdout/notes. The former contrary removal expectation is removed.
- **Specifically docker.log-only persistence failure retains the container — ADDRESSED.** The new test at `tools/checks/tests/test_mysql_fixture.py:153`–`:167` intercepts only `Path.write_text` for `docker.log`, raises a synthetic exception containing both credentials, and delegates every other write to the original method. Consequently credential writes and both `failure.json` writes remain available. The resulting persisted state, no-`rm` event, original exception identity/originating traceback and secret-safety assertions isolate the original defect rather than failing all evidence writes.
- **Safely captured logs, persisted state, and ownership are all required before removal — ADDRESSED.** The new guard is a stricter conjunction after the existing successful state save; it does not bypass any ownership or evidence requirement. Unknown secrets leave logs omitted; missing/mismatched proof leaves `owned=False`; neither can now reach removal. No new subprocess, exception serialization, credential source, or persistence surface is introduced by this fix.
- **Original exception/traceback and fixed safe outcomes — ADDRESSED within this fix scope.** The production change only narrows entry to the existing cleanup block; existing outer error propagation is untouched. Both amended regressions exercise the actual startup entry point with mocked subprocess boundaries and check identical original exception and traceback origin. Their injected secondary exceptions are not serialized; saved RED/GREEN evidence contains neither synthetic credential.

### New Breakage in the Fix Diff

- **None.** The sole production behavior change is the additional successful-log-preservation condition. The two regression cases and acceptance-record amendments match that behavior. No new Critical, Important, or Minor issue was identified in the three-path fix diff.

### Out-of-Scope Observations

- **None.** The previously accepted/disclosed aggregate loopback-stub process exception is historical and was not reopened. No live Docker, DB, template/runtime, migration, native-AIO, or broader branch qualification is claimed.

### Checks Performed

- Read the brief, full initial review (I1), appended “Review fix round 1” report, and `superpowers/subagent-driven-development/re-review-prompt.md`. Read the supplied `task-CI-FIXTURE-01-fix-review.diff` once: FIX_BASE `3d7aec112a6c04aff22708c6e1a78769c9432c84` to HEAD `fdcdc92b11474dd6607cea0ee54695e451ded3b5`, three changed paths. Did not regenerate or reread the diff with Git.
- Read only directly relevant source context `tools/dev/mysql_fixture.py:26`–`:100` and test helpers `tools/checks/tests/test_mysql_fixture.py:1`–`:110` to confirm bounded subprocesses, successful-write-before-captured ordering, state-save-before-removal, ownership proof, and genuine original-error/no-secret assertions. No broad crawl.
- Read the two existing fix-round logs and counted method-result lines. `fix-round-1/red.log:38`–`:40`: **13 methods, 11 ok, 2 failures, zero errors/skips**. The failures are precisely log-only persistence (`:16`, `:21`) and log-read (`:27`, `:32`), both `removed != refused`; this is meaningful defect RED. `fix-round-1/green.log:16`–`:18`: **13 methods, all 13 ok, zero failures/errors/skips**. Historical counts overlap and are not added.
- Computed SHA-256 with PowerShell `Get-FileHash -Algorithm SHA256`: **2/2 new log hashes match** the new manifest and appended report. `red.log`: `9b8384fc849aa4b79c589108b5619b4be4050a0b949334c32e765d1dcb03ed50`; `green.log`: `973576800fafa5b7613fcaaa394268c3445353bd399da48ac729159335b93de3`.
- New `ALLOWLIST.txt` contains exactly those two log paths, matching the manifest path-for-path. Its SHA-256 is `5375531b8c4db75e9178c475105ffe6b0151dac2f4d9564f094087aa9a3e07a0`; new `SHA256SUMS` is `bc690ed4601d899aa0a2246b5fee8f01b0a84865a1796016fa11b705d93b41e9`. Both match the appended report.
- Computed hashes for all eight original allowlisted evidence files: **8/8 still match** the original manifest and initial report. The original allowlist and manifest themselves remain exactly the prior reported bytes: `151b5d0a9d477ea1ffe16a6a1be4e6bc7cbd36d93d012ccbedb5453da9db5f5d` and `74ed33fca1c70abdbe0d257bbef3b280a292e7890ffb30d6c557654662bd2ef8`, respectively. No evidence was rewritten.
- Checked saved fix-round logs for both exact synthetic credential strings: **zero matching lines in either log**. This supports the saved evidence's secret-safety claim, together with the amended assertions; it is not a live credential/runtime audit.
- Evidence root inspected: `/home/tanren/ama/.worktrees/phase-b-fixture-evidence/artifacts/phase-b/ci-fixture-01`; original accounting files remain under that worktree's `artifacts/checks`. Hashes establish supplied-byte integrity, not additional execution provenance.
- No suite/test, Docker, DB, network, or Git command was run by this re-reviewer. Author-reported exit codes, clean worktree, and diff checks were not independently rerun. The supplied logs substantiate their test counts and outcomes. This coordinator report is the reviewer's sole filesystem write; no checkout/index/HEAD/branch/source/evidence mutation or child-agent dispatch occurred.

### Verdict

**Scoped spec compliance: APPROVED.** I1's log-read and docker.log-only persistence failure paths retain the container; removal requires ownership proof plus safely persisted logs and state. Original-error/traceback and secret-safety coverage matches the requested fix.

**Scoped code quality: APPROVED.** The minimal production guard and focused regressions address the demonstrated defect without identified direct regression.

**Fix round: All findings addressed, no new Critical/Important breakage.** I1 is closed. Approval is limited to this fix round and saved mocked qualification; no live qualification is claimed.
