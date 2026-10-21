# MySQL fixture startup failure evidence

CI-FIXTURE-01, 2026-10-22. BASE `0daecad85d348305c11982b5f0a7e2536a47434d`.

`tools/dev/mysql_fixture.py` previously removed a failed startup container before
preserving its diagnostics. A removal exception also replaced the original
readiness failure. This maintenance change preserves the original exception and
traceback, including when capture, cleanup, or diagnostic-note attachment fails.

After a successful `docker run`, a startup failure now writes `failure.json` and,
when safe, the last 200 Docker log lines to `docker.log` in that start's existing
`.tools/mysql-fixture/ama-test-b01-<generated>` directory. The generated directory
is private (0700); diagnostic files are created/chmodded to 0600 before writing.
Both generated passwords are retained only in memory for exact-value redaction.
Both stdout and stderr from Docker logs are redacted. Without both redaction
inputs, logs and inspect data are omitted. Outcomes use fixed safe strings;
capture and cleanup exceptions, their command arguments, and full inspect data
are never copied into diagnostics or exception notes.

The new bounded inspect command selects only name, image reference/ID, fixture
label, status/exit/OOM/error, and configured host port bindings. Python rebuilds
that allowlist before serialization. Cleanup requires the exact generated name,
label `ama.test-fixture=b01`, pinned image reference, and exactly one
`3306/tcp` binding to `127.0.0.1`. Configured bindings are checked because exited
containers may lack active network port mappings. Unavailable, malformed, or
mismatching proof refuses removal. Diagnostic state is persisted before removal;
failure to persist it also refuses removal. Each new Docker inspect/log/remove
subprocess has a 10-second timeout and captures stderr without printing it.

Successful startup, CLI, pinned image, launch arguments, readiness behavior,
default native AIO profile, and explicit stop behavior retain their existing
contracts. A launch failure remains outside the cleanup handler, avoiding
removal of a pre-existing conflicting name. This does not address the historical
host native-AIO exhaustion or change SQL/schema behavior.

## Verification

- First RED against the unchanged implementation: eight test methods, nine
  assertion failures (including five ownership/binding subcases), one missing
  `failure.json` error, and two passing existing-behavior cases. The missing-file
  error demonstrates absent evidence, rather than a discovery/setup failure.
- A baseline replay with credential-safe assertion rendering reproduced the
  same eight-method RED result (`red-safe.log`). The first exploratory RED log
  rendered synthetic test-only password strings in an exception comparison;
  assertions now use fixed messages and that exploratory log is excluded from
  the evidence adoption allowlist. No real generated credentials were used.
- A second RED caught note-attachment failure replacing the original exception:
  12 methods, one expected assertion failure (`red-note.log`).
- Focused GREEN: 12 methods, zero failures/errors/skips. Cases cover ordering,
  selected-field filtering, both passwords and both log streams, permissions,
  bounded subprocesses, original error/traceback preservation, refusal paths,
  malformed state, unknown redaction inputs, interruption, successful startup,
  and launch-conflict behavior. Docker/subprocess boundaries are mocked.
- Existing Python gate: 86 executed, zero failures, zero skips in two discovery
  directories (34 checks tests including the 12 focused cases; 52 probe tests).
  These counts overlap and must not be added together. The existing probe suite
  uses a local `127.0.0.1` HTTP stub. Running that gate exceeded this task brief's
  narrower no-network constraint; no external endpoint, real Docker, or DB was
  called. This verification deviation is recorded for independent review.
- `git diff --check` passes for the owned source/test/record.

Commands (from the isolated worktree):

```text
python3 artifacts/phase-b/ci-fixture-01/red_replay.py
python3 -m unittest discover -s tools/checks/tests -p test_mysql_fixture.py -v
python3 -c 'from tools.checks.run import python_tests; raise SystemExit(python_tests())'
git diff --check
```

Ignored raw evidence is explicitly allowlisted by
`artifacts/phase-b/ci-fixture-01/ALLOWLIST.txt` and hashed by the adjacent
`SHA256SUMS`. No credential directory is included. Independent review/adoption
and any live Docker validation remain separate from this mocked qualification.
