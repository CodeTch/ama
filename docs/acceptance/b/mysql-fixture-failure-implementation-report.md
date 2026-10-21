# CI-FIXTURE-01 implementation report

Status: **DONE_WITH_CONCERNS** (verification-scope deviation recorded below).

BASE: `0daecad85d348305c11982b5f0a7e2536a47434d`.
HEAD: `3d7aec112a6c04aff22708c6e1a78769c9432c84`.
Branch: `codex/phase-b-fixture-evidence`.
Owned worktree: `/home/tanren/ama/.worktrees/phase-b-fixture-evidence`.

Committed owned files:

- `/home/tanren/ama/.worktrees/phase-b-fixture-evidence/tools/dev/mysql_fixture.py`
- `/home/tanren/ama/.worktrees/phase-b-fixture-evidence/tools/checks/tests/test_mysql_fixture.py`
- `/home/tanren/ama/.worktrees/phase-b-fixture-evidence/docs/acceptance/b/mysql-fixture-failure-evidence.md`

This report is the only requested write in the coordinator tree. No other source
tree was mutated, and there was no merge, push, stash, SQL/schema edit, or child
agent delegation. The implementation worktree is clean after the local commit.

## Defect, behavior, and scope

Verified directly in BASE: the startup `except BaseException` called
`docker rm -f` immediately, then re-raised. This lost container evidence before
capture and allowed removal failure to replace the original readiness error.
The first deterministic RED reproduced both defects and unguarded cleanup.

The new failure path retains both generated passwords in local variables for
redaction, selects individual Docker inspect fields with a Go template, rebuilds
an explicit Python allowlist, saves safe diagnostics, and only then attempts
guarded removal. The selected fields are name, configured image reference,
image ID, the expected fixture label, status/exit/OOM/error, and configured host
port bindings. It does not serialize an entire inspect result, Config.Env,
arbitrary state members, or any Docker argv.

Removal requires exact generated name, `ama.test-fixture=b01`, the existing pinned
image reference, and exactly one `3306/tcp` host binding to `127.0.0.1`. Configured
bindings remain available for an exited container whose active network ports
can disappear. Missing, malformed, or mismatching proof refuses removal and
records that safe outcome. Failure to persist diagnostic state also refuses
removal. A log capture failure records a fixed failure outcome but does not
replace the original error.

The generated directory retains its 0700 contract; diagnostic files
`failure.json` and `docker.log` are created/chmodded to 0600 before writes. Docker
logs are limited to the last 200 lines, capturing and redacting both stdout and
stderr. Exact generated application and root password values are removed from
saved text. Unknown redaction inputs omit both logs and inspect content rather
than writing unsafe bytes. Diagnostic outcome strings and exception notes are
fixed safe values; raw exception text is discarded. Every new diagnostic or
failure-cleanup subprocess has a 10-second timeout and captures stderr.

The original exception object and traceback survive capture, cleanup, and even
note-attachment errors through a bare re-raise. All secondary work is best effort.
The launch remains outside the failure-cleanup try block, so a name-conflict
launch failure cannot remove a pre-existing container. Success output, CLI,
existing launch arguments, pinned image, readiness probe, explicit stop code,
and default native-AIO behavior are unchanged. Native-AIO exhaustion and the
historical migration qualification are outside this fix.

## Workflow and command evidence

Applied the read systematic-debugging, test-driven-development (including
writing-good-tests), and verification-before-completion skill instructions.
No applicable AGENTS.md was found in the owned tree or its ancestors. Python
tests follow the existing unittest discovery and accounting conventions.

Commands were launched with Windows PowerShell from `C:\Users\LING` via
`wsl.exe -d Ubuntu-24.04 --cd /home/tanren/ama/.worktrees/phase-b-fixture-evidence --exec ...`.
The following commands are shown in their Linux form within that worktree:

1. Before production implementation:
   `python3 -m unittest discover -s tools/checks/tests -p test_mysql_fixture.py -v`.
   Eight methods ran: nine assertion failures (including five ownership/binding
   subcases), one missing diagnostic-file error, and two passing compatibility
   methods. This was meaningful RED: logs were not captured, cleanup was
   unconditional, the original exception was replaced, and failure.json was
   absent. The missing-file error is missing feature behavior, not setup failure.
2. A preliminary PowerShell UNC write failed before the test file existed and
   discovery ran zero tests. This was setup failure and was not counted as RED.
   The file write was corrected using apply_patch before the meaningful RED.
3. After the first implementation, the eight methods passed. Four additional
   hardening cases covered missing redaction inputs, malformed selected state,
   interruption, and note-attachment failure. The latter produced a second
   meaningful RED: 12 methods, one assertion failure. Best-effort note attachment
   then restored GREEN.
4. Assertion messages were hardened to avoid rendering unsafe replacement
   exceptions. `python3 artifacts/phase-b/ci-fixture-01/red_replay.py` loads BASE
   source directly from Git into a separate module and reruns the eight original
   regressions, with all Docker calls mocked. It reproduces eight methods,
   nine failures and one missing diagnostic-file error, exit 1, without mutating
   the implementation file. Raw evidence: `red-safe.log`.
5. Final focused command:
   `python3 -m unittest discover -s tools/checks/tests -p test_mysql_fixture.py -v`.
   **12 executed, zero failures/errors/skips**, exit 0. Raw evidence: `green.log`.
6. Existing accounting gate:
   `python3 -c 'from tools.checks.run import python_tests; raise SystemExit(python_tests())'`.
   **86 executed, zero failures, zero skips**, exit 0, in two directories:
   34 `tools/checks/tests` methods and 52 `tools/probes/tests` methods. The focused
   12 are included in the 34 and 86; these counts must not be added together.
7. `python3 -m py_compile tools/dev/mysql_fixture.py tools/checks/tests/test_mysql_fixture.py`:
   exit 0. `git diff --check` and `git diff --cached --check`: exit 0 before
   commit; `git diff --check` and clean `git status --short` confirmed afterward.
8. `sha256sum --check artifacts/phase-b/ci-fixture-01/SHA256SUMS`:
   all eight explicitly allowlisted evidence files verify, exit 0.

No Java, web, schema, real Docker, or DB tests were run. There was no external
endpoint or company-service request. **The aggregate Python gate did start its
existing 127.0.0.1 HTTP test stub in tools/probes/tests/test_model_probe.py.**
This was discovered by source inspection after running the gate and reported
immediately to root. It exceeded the narrower no-network condition in this task
brief. Root acknowledged that this is a local-test scope deviation, distinct
from a real company/external call, and requested accurate reporting without
repeating successful tests solely to narrow the count.

## Explicit evidence allowlist and hashes

All raw evidence remains ignored. Adopt only the eight paths below, plus the
allowlist/hash manifest themselves; do not recursively copy artifact or .tools
directories. Paths are relative to the owned worktree.

```text
60182bb055d356c356ac055e7c9804e64b968c8f13653a408e4ce36993dba281  artifacts/phase-b/ci-fixture-01/red_replay.py
ae5abc6b084b4e56250a1839e182f1bf46749a80026ca9e94e0b013c5bd7105a  artifacts/phase-b/ci-fixture-01/red-safe.log
eed74c097953f7f4d7322f38fdfcb6438f5a982a2ae747f1280aebc35e516ec1  artifacts/phase-b/ci-fixture-01/red-note.log
a5563ac0b2c8faafc40e1ce8abc7630d61f8b87509636d9d43c7ac4aeac31842  artifacts/phase-b/ci-fixture-01/green.log
1a463eaca19876303e90bb3834c8225aeb050ca9d2c9ee96127292bf94e121d6  artifacts/checks/python.json
5496c375e273806368b7899ec016a70627a471cec61ed342ac6c99cce28e7d6b  artifacts/checks/python-0.json
26660680818fc640c565f63634b9dde6acdac0ad077f586815fd783760568d23  artifacts/checks/python-1.json
5d30098dd0a90c4ed8c50ac07b7f1344209e4e65be977840ea83d0ec4b5552ee  artifacts/checks/python.log
151b5d0a9d477ea1ffe16a6a1be4e6bc7cbd36d93d012ccbedb5453da9db5f5d  artifacts/phase-b/ci-fixture-01/ALLOWLIST.txt
74ed33fca1c70abdbe0d257bbef3b280a292e7890ffb30d6c557654662bd2ef8  artifacts/phase-b/ci-fixture-01/SHA256SUMS
```

Full evidence directory:
`/home/tanren/ama/.worktrees/phase-b-fixture-evidence/artifacts/phase-b/ci-fixture-01`.
Full accounting evidence directory:
`/home/tanren/ama/.worktrees/phase-b-fixture-evidence/artifacts/checks`.

## Security self-review and concerns

- Fixture tests replace both token generators with synthetic test-only values
  and patch subprocess boundaries; no real Docker-generated credentials exist
  in this evidence. Temporary credential files are cleaned by unittest fixtures.
- Full inspect/Config.Env is neither requested nor serialized on the new failure
  path. Both generated secrets are redacted before diagnostic persistence. Logs
  and unsafe inspect text are omitted if safe redaction cannot be established.
- No credential directory, env file, secret-bearing Docker argv, or raw exception
  is adopted or committed. A synthetic-only exploratory RED log originally
  printed a synthetic replacement exception; hardened assertions and a fresh
  unsanitized BASE replay replaced it in the adoption allowlist. The exploratory
  log remains ignored and is deliberately excluded rather than edited.
- Failure cleanup uses only the exact inspected generated name and requires all
  ownership/image/loopback proof. Failed or unavailable proof never issues rm.
  No enumeration, prune, service restart, sysctl, external endpoints, or other
  container actions were added.
- The aggregate gate's existing loopback HTTP stub is the explicit remaining
  process concern. There was no actual Docker/DB or external network call.
- No live Docker qualification was attempted, as required. The pinned image's
  Go-template projection and runtime capture are covered by deterministic
  subprocess-boundary tests rather than a live fixture. Independent root review
  and adoption are pending.

## Review fix round 1 — I1: require preserved logs before removal

Current status: **DONE_WITH_CONCERNS**. I1 is fixed; the only process concern is
the previously disclosed/accepted historical aggregate loopback-stub deviation.
No new execution-scope deviation occurred in this fix round.

FIX_BASE: `3d7aec112a6c04aff22708c6e1a78769c9432c84`.
Current HEAD: `fdcdc92b11474dd6607cea0ee54695e451ded3b5`.
Branch/worktree ownership is unchanged. This current HEAD supersedes the initial
HEAD at the top of this historical report; prior evidence and results remain
intact rather than being rewritten.

Read the complete independent review at
`/home/tanren/ama/.worktrees/phase-a/.superpowers/sdd/2026-10-21-ama-phase-b/task-CI-FIXTURE-01-review.md`
and applied the receiving-code-review skill after checking I1 against the actual
control flow. The finding was valid: the shared log-read/log-write exception
handler set `logs=failed`, but cleanup was still gated only on `owned`. A successful
separate `failure.json` write therefore still permitted removal, potentially
destroying exact MySQL startup/AIO evidence. The initial contrary test also
required removal after log-read failure.

Minimal implementation change: the existing cleanup condition is now
`owned and outcome["logs"] == "captured"`. The `captured` outcome is assigned only
after log redaction and successful `docker.log` persistence. The preceding state
write must still succeed or return before reaching that guard. Thus removal now
requires ownership proof, safely captured/persisted logs, and persisted safe
state. Log-read and log-write failures leave `cleanup=refused`, retain the exact
container, and preserve the original failure/traceback with fixed safe outcomes.
No timeout, image, profile, success/launch, explicit stop, SQL, or other behavior
was changed.

The three updated committed paths are the same full owned paths listed above:

- `/home/tanren/ama/.worktrees/phase-b-fixture-evidence/tools/dev/mysql_fixture.py`
- `/home/tanren/ama/.worktrees/phase-b-fixture-evidence/tools/checks/tests/test_mysql_fixture.py`
- `/home/tanren/ama/.worktrees/phase-b-fixture-evidence/docs/acceptance/b/mysql-fixture-failure-evidence.md`

The former log-read test now expects refusal and checks the original traceback.
A separate deterministic `docker.log`-only persistence-failure test raises an
unsafe synthetic exception for that path while letting credential and all
`failure.json` writes succeed. Both tests confirm `inspect=captured`,
`logs=failed`, `cleanup=refused`, no `rm` event, unchanged original exception
identity and originating traceback function, and absence of both synthetic
secrets in diagnostic files, stdout, and exception notes.

### Fix-round verification and raw evidence

Only the focused mocked suite was run:

```text
python3 -m unittest discover -s tools/checks/tests -p test_mysql_fixture.py -v
```

- Before the guard change: **13 methods, two assertion failures, zero errors**,
  exit 1. Both failures are exactly `removed` versus the required `refused`, for
  log-read and specifically log-only write failure. This is meaningful RED,
  not setup/discovery failure. Other 11 methods passed.
- After the guard change: **13 methods passed, zero failures/errors/skips**,
  exit 0. This is the original focused 12 with one corrected case and one added
  case, rather than a separate additive 13 tests on top of historical counts.
- `git diff --cached --check` passed before the local commit. After committing,
  `git status --short` is empty and `git diff --check` passes.
- `sha256sum --check` verified both original eight-file evidence and new two-file
  fix-round evidence manifests. Original evidence bytes remain unchanged.
- No aggregate, Java, web, schema, DB, real Docker, or network execution was
  repeated or added in this fix round. Subprocess boundaries remain mocked.

New explicit allowlist directory:
`/home/tanren/ama/.worktrees/phase-b-fixture-evidence/artifacts/phase-b/ci-fixture-01/fix-round-1`.
Adopt only its two listed logs and two metadata files, alongside the separately
preserved original allowlist when needed. No recursive credential/artifact copy.

```text
9b8384fc849aa4b79c589108b5619b4be4050a0b949334c32e765d1dcb03ed50  artifacts/phase-b/ci-fixture-01/fix-round-1/red.log
973576800fafa5b7613fcaaa394268c3445353bd399da48ac729159335b93de3  artifacts/phase-b/ci-fixture-01/fix-round-1/green.log
5375531b8c4db75e9178c475105ffe6b0151dac2f4d9564f094087aa9a3e07a0  artifacts/phase-b/ci-fixture-01/fix-round-1/ALLOWLIST.txt
bc690ed4601d899aa0a2246b5fee8f01b0a84865a1796016fa11b705d93b41e9  artifacts/phase-b/ci-fixture-01/fix-round-1/SHA256SUMS
```

Security self-review: the fix introduces no new subprocess, serialized data,
exception text, permission change, or secret source. Log failure exceptions in
both new regressions include synthetic secrets, but fixed assertion rendering
and existing safe outcome handling keep those values out of raw evidence.
Original ownership, redaction, permissions, bounded work, and error-preservation
tests remain GREEN. No new implementation concern remains identified; root's
fresh scoped re-review/adoption is pending.
