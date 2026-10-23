# Setup history (never assertion RED)

- `red-01`: canonical `python3 tools/checks/run.py it ConfigArchiveRestartIT` reached integration test compilation, then failed `ArchiveRestartProbe.java:[50,163] illegal start of expression` due to an extra closing parenthesis. No IT/process ran. Raw runner logs, exit and exact source-before-run remain in this directory's `red-01/`. The probe expression was split into a named boolean.
- `red-02`: the same canonical selector failed integration compilation because `Role` matched both IdentityContracts.Role and MinutesContracts.Role. No IT/process ran. Raw logs/exit/source remain in `red-02/`. The actor now explicitly uses IdentityContracts.Role.USER.
- A read-only attempt to display the first real-process XML plus receipts with nested PowerShell/bash/xargs quoting was rejected by the host PowerShell parser (`Unrecognized token in source text`). No shell command or data mutation occurred. It was replaced with a simple read-only `rg` plus direct retained-file glob `cat`.

Both compilation attempts automatically executed dependency units before testCompile failed; those overlapping units are recorded separately in each counts.json. Their folder names reflect the intended next stage, not a claim of business assertion RED. The first runnable parent assertion RED is `red-03/`, with actual A commit ACK and fresh B typed failure.
